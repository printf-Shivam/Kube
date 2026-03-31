package com.searchEngine.Kube.crawler;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.searchEngine.Kube.database.DatabaseManager;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class CrawlerEngine {
    private final int MAX_THREAD = 20;
    private final long HOST_DELAY = 1000L;
    private final int MAX_PAGES = 100;
    private ExecutorService executor = Executors.newFixedThreadPool(20);
    private Set<String> visited = ConcurrentHashMap.newKeySet();
    private ConcurrentHashMap<String, BlockingQueue<String>> hostQueue = new ConcurrentHashMap();
    private Set<String> activeHosts = ConcurrentHashMap.newKeySet();
    private ConcurrentHashMap<String, Long> hostLastAccess = new ConcurrentHashMap();
    private ConcurrentHashMap<String, Integer> hostPageCount = new ConcurrentHashMap();
    private final RobotsParser robotsParser = new RobotsParser();
    private final DatabaseManager dbManager = new DatabaseManager();
    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private final Semaphore browserLimiter = new Semaphore(3);

    public CrawlerEngine(List<String> seedList) {
        this.initBrowser();
        System.out.println("Browser initialized successfully");
        Set<String> history = this.dbManager.getVisitedUrls();
        this.visited.addAll(history);

        for(String url : this.dbManager.loadFrontier()) {
            this.addURL(url);
        }

        for(String seed : seedList) {
            this.addURL(seed);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n manual stoppage ");
            this.saveFrontierState();
            this.dbManager.close();
            if (this.browser != null) {
                this.browser.close();
            }

            if (this.playwright != null) {
                this.playwright.close();
            }

        }));
    }

    public void start() {
        this.startDispatcher();

        while(!this.isCrawlComplete()) {
            try {
                Thread.sleep(500L);
            } catch (InterruptedException var3) {
                break;
            }
        }

        this.executor.shutdown();

        try {
            this.executor.awaitTermination(1L, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        this.saveFrontierState();
        this.dbManager.close();
        this.browser.close();
        this.playwright.close();
    }

    private boolean isCrawlComplete() {
        if (!this.activeHosts.isEmpty()) {
            return false;
        } else {
            for(BlockingQueue<String> queue : this.hostQueue.values()) {
                if (!queue.isEmpty()) {
                    return false;
                }
            }

            return true;
        }
    }

    private void startDispatcher() {
        Thread dispatcher = new Thread(() -> {
            while(true) {
                boolean dispatched = false;

                for(String host : this.hostQueue.keySet()) {
                    if (!this.activeHosts.contains(host)) {
                        long currentTime = System.currentTimeMillis();
                        long lastAccess = (Long)this.hostLastAccess.getOrDefault(host, 0L);
                        if (currentTime - lastAccess >= 1000L && this.canCrawl(host)) {
                            BlockingQueue<String> queue = (BlockingQueue)this.hostQueue.get(host);
                            if (queue != null && !queue.isEmpty()) {
                                String url = (String)queue.poll();
                                if (url != null) {
                                    this.activeHosts.add(host);
                                    this.hostLastAccess.put(host, System.currentTimeMillis());
                                    this.executor.execute(() -> this.processUrl(host, url));
                                    dispatched = true;
                                }
                            }
                        }
                    }
                }

                if (!dispatched) {
                    try {
                        Thread.sleep(50L);
                    } catch (Exception var10) {
                        return;
                    }
                }
            }
        });
        dispatcher.setDaemon(true);
        dispatcher.start();
    }

    private void processUrl(String host, String url) {
        try {
            String path = this.getPath(url);
            if (this.robotsParser.isAllowed(host, path)) {
                Document doc = this.request(url);
                if (doc == null) {
                    return;
                }

                this.incrementHostCount(host);
                this.dbManager.savePage(url, doc.outerHtml());

                for(Element link : doc.select("a[href]")) {
                    this.addURL(link.absUrl("href"));
                }

                return;
            }

            System.out.println("crawling not allowed :" + url);
        } catch (Exception var10) {
            System.err.println("error in processing url: " + url);
            return;
        } finally {
            this.activeHosts.remove(host);
        }

    }

    private void addURL(String seed) {
        String url = this.canonicalize(seed);
        if (url != null) {
            if (this.visited.add(url)) {
                String host = this.getHost(url);
                if (host != null) {
                    if (this.canCrawl(host)) {
                        ((BlockingQueue)this.hostQueue.computeIfAbsent(host, (h) -> new LinkedBlockingQueue())).offer(url);
                    }
                }
            }
        }
    }

    private String getPath(String url) {
        try {
            return (new URL(url)).getPath();
        } catch (Exception var3) {
            return "/";
        }
    }

    private Document fetchWithJsoup(String url) {
        try {
            Connection con = Jsoup.connect(url).userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36").header("Accept-Language", "en-US,en;q=0.9").timeout(10000).ignoreHttpErrors(true);
            Document doc = con.get();
            if (con.response().statusCode() == 200) {
                PrintStream var10000 = System.out;
                String var10001 = Thread.currentThread().getName();
                var10000.println(var10001 + " visited: " + url);
            }

            return doc;
        } catch (IOException var4) {
            System.err.println("error in request method");
            return null;
        }
    }

    private String canonicalize(String link) {
        try {
            if (link != null && !link.isEmpty()) {
                URL url = new URL(link);
                String protocol = url.getProtocol();
                if (!protocol.equals("https") && !protocol.equals("http")) {
                    return null;
                } else {
                    String host = url.getHost().toLowerCase();
                    String path = url.getPath();
                    if (path == null || path.isEmpty()) {
                        path = "/";
                    }

                    return protocol + "://" + host + path;
                }
            } else {
                return null;
            }
        } catch (Exception var6) {
            System.err.println("error in canonicalization method");
            return null;
        }
    }

    private String getHost(String url) {
        try {
            return (new URL(url)).getHost().toLowerCase();
        } catch (Exception var3) {
            System.err.println("error in fetching host");
            return null;
        }
    }

    private boolean canCrawl(String host) {
        return (Integer)this.hostPageCount.getOrDefault(host, 0) < 100;
    }

    private void incrementHostCount(String host) {
        this.hostPageCount.merge(host, 1, Integer::sum);
    }

    private void saveFrontierState() {
        List<String> remaining = new ArrayList();

        for(BlockingQueue<String> queue : this.hostQueue.values()) {
            remaining.addAll(queue);
        }

        this.dbManager.saveFrontier(remaining);
    }

    public void initBrowser() {
        this.playwright = Playwright.create();
        this.browser = this.playwright.chromium().launch((new BrowserType.LaunchOptions()).setHeadless(false));
        this.context = this.browser.newContext((new Browser.NewContextOptions()).setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36").setViewportSize(1280, 800));
    }

    private String fetchWithBrowser(String url) {
        Page page = null;

        String var17;
        try {
            this.browserLimiter.acquire();
            page = this.context.newPage();
            page.navigate(url, (new Page.NavigateOptions()).setTimeout((double)15000.0F));
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForSelector("body");
            page.waitForTimeout((double)6000.0F);
            page.mouse().move((double)100.0F, (double)100.0F);
            page.mouse().wheel((double)0.0F, (double)500.0F);
            String html = page.content();
            if (!html.contains("Just a moment") && !html.contains("Enable JavaScript")) {
                var17 = html;
                return var17;
            }

            System.out.println("Blocked page detected → skipping: " + url);
            var17 = null;
        } catch (Exception var15) {
            System.err.println("Browser fetch failed: " + url);
            var17 = null;
            return var17;
        } finally {
            if (page != null) {
                try {
                    page.close();
                } catch (Exception var14) {
                    System.err.println("error in browser");
                }
            }

            this.browserLimiter.release();
        }

        return var17;
    }

    private Document request(String url) {
        try {
            Document doc = this.fetchWithJsoup(url);
            if (doc == null) {
                return null;
            } else {
                String html = doc.outerHtml();
                if (this.isBlockedOrEmpty(html)) {
                    System.out.println("switching to browser: " + url);
                    String renderedHtml = this.fetchWithBrowser(url);
                    if (renderedHtml != null) {
                        doc = Jsoup.parse(renderedHtml);
                    }
                }

                return doc;
            }
        } catch (Exception var5) {
            return null;
        }
    }

    private boolean isBlockedOrEmpty(String html) {
        return html.contains("Enable JavaScript") || html.contains("Just a moment") || html.length() < 2000;
    }

    public static void main(String[] args) {
        // 1. Prepare your initial seed URLs
        List<String> seedList = new ArrayList<>();
        seedList.add("https://en.wikipedia.org/wiki/Computer_science");
        seedList.add("https://www.geeksforgeeks.org/");
        seedList.add("https://www.bbc.com/news");

        System.out.println("--- Launching CrawlerEngine ---");

        CrawlerEngine crawler = new CrawlerEngine(seedList);

        System.out.println("Starting the crawl process...");
        crawler.start();

        System.out.println("Crawl sequence finished.");
    }
}
