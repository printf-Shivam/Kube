package com.Crawler.Kube;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class CrawlerEngine{

    private  final int MAX_THREAD =20;
    private  final long HOST_DELAY = 1000;
    private  final int MAX_PAGES = 100;

    private  ExecutorService executor = Executors.newFixedThreadPool(MAX_THREAD);
    private  Set<String> visited = ConcurrentHashMap.newKeySet();

    private  ConcurrentHashMap<String,BlockingQueue<String>> hostQueue = new ConcurrentHashMap<>();
    private  Set<String> activeHosts = ConcurrentHashMap.newKeySet();

    private  ConcurrentHashMap<String, Long> hostLastAccess = new ConcurrentHashMap<>();
    private  ConcurrentHashMap<String, Integer> hostPageCount= new ConcurrentHashMap<>();

    private final RobotsParser robotsParser = new RobotsParser();
    private final DatabaseManager dbManager = new DatabaseManager();

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;

    private final java.util.concurrent.Semaphore browserLimiter = new java.util.concurrent.Semaphore(3);

    public CrawlerEngine(List<String> seedList) {

        initBrowser();
        System.out.println("Browser initialized successfully");
        //filling visited set
        Set<String> history = dbManager.getVisitedUrls();
        visited.addAll(history);

        //filling frontier
        List<String> oldFrontier = dbManager.loadFrontier();
        for(String url : oldFrontier)
            addURL(url);

        for(String seed: seedList)
            addURL(seed);

        //if process gets killed manually
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n manual stoppage ");

            saveFrontierState();
            dbManager.close();
            if (browser != null) browser.close();
            if (playwright != null) playwright.close();
        }));
    }

    public void start() {
        startDispatcher();

        while (!isCrawlComplete()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                break;
            }
        }

        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        saveFrontierState();
        dbManager.close();
        browser.close();
        playwright.close();
    }

    private boolean isCrawlComplete() {
        if (!activeHosts.isEmpty()) return false;

        for (BlockingQueue<String> queue : hostQueue.values()) {
            if (!queue.isEmpty()) return false;
        }

        return true;
    }

    private void startDispatcher() {
        Thread dispatcher = new Thread(()->{
            while (true) {
                boolean dispatched = false;

                for(String host : hostQueue.keySet()){
                    if(activeHosts.contains(host)) continue;

                    long currentTime = System.currentTimeMillis();
                    long lastAccess = hostLastAccess.getOrDefault(host, 0L);

                    if(currentTime - lastAccess < HOST_DELAY) continue;

                    if(!canCrawl(host)) continue;

                    BlockingQueue<String> queue = hostQueue.get(host);

                    if(queue == null || queue.isEmpty()) continue;

                    String url = queue.poll();
                    if(url == null) continue;

//                    if(!visited.add(url)) continue;

                    activeHosts.add(host);
                    hostLastAccess.put(host, System.currentTimeMillis());
                    executor.execute(()-> processUrl(host, url));
                    dispatched = true;
                }
                if(!dispatched){
                    try {
                        Thread.sleep(50);
                    } catch (Exception e) {
                        return;
                    }
                }
            }
        });
        dispatcher.setDaemon(true);
        dispatcher.start();
    }

    private void processUrl(String host, String url){
        try {
            String path = getPath(url);
            if (!robotsParser.isAllowed(host, path)) {
                System.out.println("crawling not allowed :" + url);
                return;
            }
            Document doc = request(url);
            if(doc == null) return;

            incrementHostCount(host);
            dbManager.savePage(url, doc.outerHtml());

            for(Element link : doc.select("a[href]")){
                addURL(link.absUrl("href"));
            }
        } catch (Exception e) {
            System.err.println("error in processing url: " + url);
        }
        finally{
            activeHosts.remove(host);
        }
    }

    private void addURL(String seed){
        String url = canonicalize(seed);
        if(url == null) return ;

        if (!visited.add(url)) return;

        String host = getHost(url);
        if(host == null) return;

        if(!canCrawl(host)) return;
        hostQueue.computeIfAbsent(host, h -> new LinkedBlockingQueue<>()).offer(url);
    }

    private String getPath(String url) {
        try {
            return new URL(url).getPath();
        }catch(Exception e) {
            return "/";
        }
    }

    private Document fetchWithJsoup(String url) {
        try {
            Connection con = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .timeout(10000)
                    .ignoreHttpErrors(true);
            Document doc = con.get();

            if(con.response().statusCode()==200) {
                System.out.println(Thread.currentThread().getName()+" visited: "+url);
            }
            return doc;
        }

        catch(IOException e) {
            System.err.println("error in request method");
            return null;
        }
    }

    private String canonicalize(String link){
        try {
            if(link == null || link.isEmpty()) return null;

            URL url = new URL(link);
            String protocol = url.getProtocol();
            if(!protocol.equals("https") && !protocol.equals("http"))
                return null;

            String host = url.getHost().toLowerCase();
            String path = url.getPath();

            if(path==null || path.isEmpty())
                path = "/";
            return protocol + "://" + host + path;

        } catch (Exception e) {
            System.err.println("error in canonicalization method");
            return null;
        }
    }

    private String getHost(String url){
        try {
            return new URL(url).getHost().toLowerCase();
        } catch (Exception e) {
            System.err.println("error in fetching host");
            return null;
        }
    }

    private boolean canCrawl(String host){
        return hostPageCount.getOrDefault(host, 0) < MAX_PAGES;
    }

    private void incrementHostCount(String host){
        hostPageCount.merge(host,1,Integer::sum);
    }

    private void saveFrontierState(){
        List<String> remaining = new ArrayList<>();

        for(BlockingQueue<String> queue : hostQueue.values()){
            remaining.addAll(queue);
        }
        dbManager.saveFrontier(remaining);
    }

    public void initBrowser() {
        playwright = Playwright.create();

        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(false)
        );

        context = browser.newContext(
                new Browser.NewContextOptions()
                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36")
                        .setViewportSize(1280, 800)
        );
    }

    //use browser
    private String fetchWithBrowser(String url) {
        Page page = null;

        try {
            browserLimiter.acquire(); //limit concurrency so that multiple tabs are not overwhelming resources

            page = context.newPage();

            page.navigate(url, new Page.NavigateOptions().setTimeout(15000));
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForSelector("body");

            page.waitForTimeout(6000);

            page.mouse().move(100, 100);
            page.mouse().wheel(0, 500);

            String html = page.content();

            if (html.contains("Just a moment") || html.contains("Enable JavaScript")) {
                System.out.println("Blocked page detected → skipping: " + url);
                return null;
            }

            return html;

        }
        catch (Exception e) {
            System.err.println("Browser fetch failed: " + url);
            return null;

        }
        finally{
            if(page != null){
                try{
                    page.close();
                }
                catch(Exception ignored){
                    System.err.println("error in browser");
                }
            }
            browserLimiter.release(); // release lock
        }
    }

    private Document request(String url) {
        try {
            Document doc = fetchWithJsoup(url);

            if (doc == null) return null;

            String html = doc.outerHtml();

            if(isBlockedOrEmpty(html)){
                System.out.println("switching to browser: " + url);

                String renderedHtml = fetchWithBrowser(url);
                if(renderedHtml != null){
                    doc = Jsoup.parse(renderedHtml);
                }
            }
            return doc;
        }
        catch(Exception e) {
            return null;
        }
    }
    private boolean isBlockedOrEmpty(String html) {
        return html.contains("Enable JavaScript")
                || html.contains("Just a moment")
                || html.length() < 2000;
    }
}

