package com.searchEngine.Kube.crawler;

import java.net.URL;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;

import com.searchEngine.Kube.database.DatabaseManager;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class CrawlerEngine {

    private final int MAX_THREAD = 200;
    private final int MAX_PAGES_PER_HOST = 100;

    private static final long HOST_DELAY_MS = 50;

    private static class ScheduledHost implements Delayed {
        final String host;
        final long readyAt;

        ScheduledHost(String host, long readyAt) {
            this.host = host;
            this.readyAt = readyAt;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(readyAt - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(readyAt, ((ScheduledHost) o).readyAt);
        }
    }

    private final DelayQueue<ScheduledHost> readyHosts = new DelayQueue<>();

    private static final int SIZE = 2_000_000;
    private final AtomicLongArray visited = new AtomicLongArray(SIZE);


    // RAM staging buffer
    private static final int RAM_BUFFER_CAPACITY = 500; // X
    private static final int RAM_FLUSH_THRESHOLD = 250;  // Y

//    private final ArrayDeque<PageData> ramBuffer = new ArrayDeque<>(RAM_BUFFER_CAPACITY);
//    private final Object bufferLock = new Object();

//    ram based buffer
    private final PageData[] buffer = new PageData[RAM_BUFFER_CAPACITY];

    private int head = 0;
    private int tail = 0;
    private int size = 0;
    private final Object bufferLock = new Object();


    //thread pool
    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_THREAD);

    private final ConcurrentHashMap<String, BlockingQueue<String>> hostQueue = new ConcurrentHashMap<>();
    private final Set<String> activeHosts = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Long> hostLastAccess = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> hostPageCount = new ConcurrentHashMap<>();

    //how many threads are allowed to crawl one host parallely
    private final ConcurrentHashMap<String, Semaphore> hostSemaphores = new ConcurrentHashMap<>();
    private static final int MAX_PER_HOST = 3;

    private final ArrayBlockingQueue<PageData> dbQueue = new ArrayBlockingQueue<>(2000);

    // writer accumulates pages and committs changes in one transaction
    private static final int DB_BATCH_SIZE = 200;
    //if our crawler is slow, it will never fill batch so we make commit in 2s
    private static final long DB_BATCH_TIMEOUT_MS = 2000L;


    private final RobotsParser robotsParser = new RobotsParser();
    private final DatabaseManager dbManager = new DatabaseManager();

    private long startTime;
    private final AtomicInteger totalPages = new AtomicInteger(0);

    private static final ThreadLocal<List<String>> linkBuffer =
            ThreadLocal.withInitial(() -> new ArrayList<>(64));



    public CrawlerEngine(List<String> seedList) {
        dbManager.initJsQueueTable();

        for (String seed : seedList) {
            this.addURL(seed);
        }

        // accumulate pages till max size -> flush in DB
        // we dont want pages to stay in RAM for too long it eats a lot of RAM
        Thread dbWorker = new Thread(() -> {
            List<PageData> batch = new ArrayList<>(DB_BATCH_SIZE);
            long lastFlush = System.currentTimeMillis();

            while (true) {
                try {
                    PageData data = dbQueue.poll(200, TimeUnit.MILLISECONDS);
                    if (data != null) {
                        batch.add(data);
                    }

                    boolean batchFull = batch.size() >= DB_BATCH_SIZE;
                    boolean timedOut = System.currentTimeMillis() - lastFlush >= DB_BATCH_TIMEOUT_MS;

                    if (!batch.isEmpty() && (batchFull || timedOut)) {
                        dbManager.savePageBatch(batch);  //single transaction for all pages in batch

                        totalPages.addAndGet(batch.size());

                        batch.clear();
                        lastFlush = System.currentTimeMillis();
                    }
                } catch (InterruptedException e) {
                    // flush remaining pages before exiting
                    if (!batch.isEmpty()) {
                        dbManager.savePageBatch(batch);
                    }
                    totalPages.addAndGet(batch.size());
                    return;

//                catch (InterruptedException e) {
//                    // Thread interrupted during shutdown hook sequence
//                    if (!batch.isEmpty()) {
//                        try {
//                            int batchSize = batch.size();
//                            dbManager.savePageBatch(batch);
//                            totalPages.addAndGet(batchSize);
//                        } catch (Exception dbEx) {
//                            System.err.println("[ERROR] Final emergency flush failed");
//                            dbEx.printStackTrace();
//                        }
//                    }
//                    return; // Exit safely
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        dbWorker.setDaemon(true);
        dbWorker.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n===== MANUAL STOP =====");
            printStats();
            saveFrontierState();
            dbManager.close();
        }));

//        Thread bufferFlusher = new Thread(() -> {
//            while (true) {
//                try {
//                    Thread.sleep(500); // periodic flush
//
//                    synchronized (bufferLock) {
//                        if (!ramBuffer.isEmpty()) {
//                            flushBufferChunk(ramBuffer.size()); // flush all
//                        }
//                    }
//                } catch (InterruptedException e) {
//                    return;
//                }
//            }
//        });
//        bufferFlusher.setDaemon(true);
//        bufferFlusher.start();

//        Thread bufferFlusher = new Thread(() -> {
//            while (true) {
//                try {
//                    Thread.sleep(500);
//
//                    synchronized (bufferLock) {
//                        if (size > 0) {
//                            flushBuffer(size);
//                        }
//                    }
//                } catch (InterruptedException e) {
//                    return;
//                }
//            }
//        });
//        bufferFlusher.setDaemon(true);
//        bufferFlusher.start();
    }

    public void start() {
        startTime = System.currentTimeMillis();
        startDispatcher();

        while (!isCrawlComplete()) {
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                break;
            }
        }

        executor.shutdown();
        try {
            executor.awaitTermination(1L, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        printStats();
        saveFrontierState();
        dbManager.close();
    }

    private void startDispatcher() {
        Thread dispatcher = new Thread(() -> {
            while (true) {
                try {
                    // blocks until a host is ready
                    ScheduledHost scheduled = readyHosts.take();
                    String host = scheduled.host;

                    Semaphore sem = hostSemaphores.computeIfAbsent(host, h -> new Semaphore(MAX_PER_HOST));

                    if (!sem.tryAcquire() || !canCrawl(host)) {
                        BlockingQueue<String> q = hostQueue.get(host);
                        if (q != null && !q.isEmpty()) {
                            readyHosts.offer(new ScheduledHost(host,
                                    System.currentTimeMillis() + HOST_DELAY_MS));
                        }
                        continue;
                    }

                    BlockingQueue<String> queue = hostQueue.get(host);
                    if (queue == null || queue.isEmpty()) continue;

                    String url = queue.poll();
                    if (url == null) continue;

                    hostLastAccess.put(host, System.currentTimeMillis());

                    executor.execute(() -> processUrl(host, url));

                } catch (InterruptedException e) {
                    return; // dispatcher thread shutting down
                }
            }
        });
        dispatcher.setDaemon(true);
        dispatcher.start();
    }

    private void processUrl(String host, String url) {
        try {
            String path = getPath(url);
            if (!robotsParser.isAllowed(host, path)) return;

            Semaphore sem = hostSemaphores.get(host); //max no of thread that will crawl one particular domain at a time

            try {
                String html = fetchHtml(url);
                if (html != null) {
                    handleFetchedPage(host, url, html);
                }
            }
            finally {
                if (sem != null) sem.release();
            }

        } catch (Exception e) {
            System.err.println("Error scheduling fetch: " + url);
            e.printStackTrace();
            activeHosts.remove(host);
        }
    }

    //setting up okhtml client
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();

    private String fetchHtml(String url) {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build();

        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful()) return null;

            String body = response.body().string();

            // limit size
            if (body.length() > 500 * 1024) {
                body = body.substring(0, 500 * 1024);
            }

            System.out.println(Thread.currentThread().getName() + " visited: " + url);

            return body;

        } catch (Exception e) {
            return null;
        }
    }

    private void handleFetchedPage(String host, String url, String html) {
        try {
            if (needsJS(html)) {
                dbManager.saveJsUrl(url);
                return;
            }

            incrementHostCount(host);
            totalPages.incrementAndGet();

            List<String> links = extractLinks(html);

            Document doc = Jsoup.parse(html);
            String text = doc.text();
            String title = doc.title();

            PageData pageData = new PageData(url, title, text);

            // help GC
            html = null;
            doc = null;

            dbQueue.offer(pageData);

            for (String link : links) {
                String abs = resolveUrl(url, link);
                if (abs != null) addURL(abs);
            }

        } catch (Exception e) {
            System.err.println("Error processing: " + url);
            e.printStackTrace();
        } finally {
            BlockingQueue<String> q = hostQueue.get(host);
            if (q != null && !q.isEmpty()) {
                readyHosts.offer(new ScheduledHost(
                        host,
                        System.currentTimeMillis() + HOST_DELAY_MS
                ));
            }
        }
    }


    private final int MAX_CONCURRENT_HOSTS = 1000;

    private void addURL(String seed) {
        String url = canonicalize(seed);
        if (url == null) return;

        long hash = hashUrl(url);
        if (!addVisited(hash)) return;

        String host = getHost(url);
        if (host == null || !canCrawl(host)) return;

        if (!hostQueue.containsKey(host) && hostQueue.size() >= MAX_CONCURRENT_HOSTS) {
            return;
        }

        BlockingQueue<String> queue = hostQueue.computeIfAbsent(host, h -> new LinkedBlockingQueue<>(50));

        if (queue.offer(url)) {
            if (!activeHosts.contains(host)) {
                readyHosts.offer(new ScheduledHost(host, System.currentTimeMillis()));
            }
        }
    }


    private boolean addVisited(long hash) {
        if (hash == 0) hash = 1;
        int index = getIndex(hash);
        int startIndex = index;

        while (true) {
            long current = visited.get(index);

            if (current == 0) {
                if (visited.compareAndSet(index, 0, hash)) return true;
                continue;
            }

            if (current == hash) return false; // duplicate

            // linear probing in case of  collision
            index = (index + 1) % SIZE;
            if (index == startIndex) return false; // table full
        }
    }

    private long hashUrl(String url) {
        long hash = 1469598103934665603L;
        for (int i = 0; i < url.length(); i++) {
            hash ^= url.charAt(i);
            hash *= 1099511628211L;
        }
        return hash;
    }

    private int getIndex(long hash) {
        return (int) (Math.abs(hash) % SIZE);
    }


    //completely GPT generated, no human brain was involved here
    private List<String> extractLinks(String html) {
        List<String> links = linkBuffer.get();
        links.clear(); // reuse

        int i = 0;
        int n = html.length();

        while (i < n) {
            if (html.regionMatches(true, i, "<a", 0, 2)) {
                char nextChar = (i + 2 < n) ? html.charAt(i + 2) : ' ';
                if (!Character.isWhitespace(nextChar) && nextChar != '>') {
                    i++;
                    continue;
                }

                int tagEnd = html.indexOf('>', i);
                if (tagEnd == -1) break;

                int hrefIndex = -1;
                for (int j = i + 2; j < tagEnd - 4; j++) {
                    if (html.regionMatches(true, j, "href=", 0, 5)) {
                        hrefIndex = j;
                        break;
                    }
                }

                if (hrefIndex != -1) {
                    int start = hrefIndex + 5;
                    while (start < tagEnd && (html.charAt(start) == ' '
                            || html.charAt(start) == '"'
                            || html.charAt(start) == '\'')) {
                        start++;
                    }
                    int end = start;
                    while (end < tagEnd
                            && html.charAt(end) != '"'
                            && html.charAt(end) != '\''
                            && html.charAt(end) != ' '
                            && html.charAt(end) != '>') {
                        end++;
                    }
                    if (end > start) {
                        links.add(html.substring(start, end));
                    }
                }

                i = tagEnd;
            }
            i++;
        }

        return links;
    }


    private boolean isCrawlComplete() {
        if (!activeHosts.isEmpty()) return false;
        if (!readyHosts.isEmpty()) return false;
        for (BlockingQueue<String> q : hostQueue.values()) {
            if (!q.isEmpty()) return false;
        }
        return true;
    }


    private String canonicalize(String link) {
        try {
            if (link == null || link.isEmpty()) return null;
            URL url = new URL(link);
            String protocol = url.getProtocol();
            if (!protocol.equals("https") && !protocol.equals("http")) return null;
            String host = url.getHost().toLowerCase();
            String path = url.getPath();
            if (path == null || path.isEmpty()) path = "/";
            return protocol + "://" + host + path;
        } catch (Exception e) {
            return null;
        }
    }

    private String getHost(String url) {
        try {
            return new URL(url).getHost().toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }

    private String getPath(String url) {
        try {
            return new URL(url).getPath();
        } catch (Exception e) {
            return "/";
        }
    }

    private boolean canCrawl(String host) {
        return hostPageCount.getOrDefault(host, 0) < MAX_PAGES_PER_HOST;
    }

    private void incrementHostCount(String host) {
        int count = hostPageCount.merge(host, 1, Integer::sum);

        if (count >= MAX_PAGES_PER_HOST) {
            hostQueue.remove(host);
            hostLastAccess.remove(host);
            System.out.println("[INFO] Host capped and evicted: " + host);
        }
    }

    private String resolveUrl(String base, String link) {
        try {
            return new URL(new URL(base), link).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean needsJS(String html) {
        return html.length() < 2000
                || html.contains("Enable JavaScript")
                || html.contains("Just a moment");
    }

    private void saveFrontierState() {
        List<String> remaining = new ArrayList<>();
        for (BlockingQueue<String> queue : hostQueue.values()) {
            remaining.addAll(queue);
        }
        dbManager.saveFrontier(remaining);
    }

    //to keep track of stats (gpt generated)
    private void printStats() {
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        int pages = totalPages.get();

        System.out.println("\n===== CRAWLER PERFORMANCE =====");
        System.out.println("Total Pages Crawled : " + pages);
        System.out.println("Total Time (ms)     : " + totalTime);
        if (pages > 0) {
            System.out.printf("Pages per second    : %.2f%n", pages * 1000.0 / totalTime);
            System.out.printf("Avg time/page (ms)  : %.2f%n", totalTime * 1.0 / pages);
        }
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        System.out.println("Memory Used (MB)    : " + (used / (1024 * 1024)));
        System.out.println("Allocated (MB)      : " + (rt.totalMemory() / (1024 * 1024)));
        System.out.println("DB Reads            : " + dbManager.getDbReads());
        System.out.println("DB Writes           : " + dbManager.getDbWrites());
        System.out.println("================================\n");
    }

//    private void addToBuffer(PageData pageData) { //for arrayDequq
//        synchronized (bufferLock) {
//            ramBuffer.add(pageData);
//
//            // trigger flush when threshold reached
//            if (ramBuffer.size() >= RAM_FLUSH_THRESHOLD) {
//                flushBufferChunk(RAM_FLUSH_THRESHOLD);
//            }
//        }
//    }

//    private void addToBuffer(PageData pageData) { //for array
//        synchronized (bufferLock) {
//
//            // if full → force flush
//            if (size == RAM_BUFFER_CAPACITY) {
//                flushBuffer(RAM_FLUSH_THRESHOLD);
//            }
//
//            buffer[tail] = pageData;
//            tail = (tail + 1) % RAM_BUFFER_CAPACITY;
//            size++;
//
//            if (size >= RAM_FLUSH_THRESHOLD) {
//                flushBuffer(RAM_FLUSH_THRESHOLD);
//            }
//        }
//    }

//    private void flushBuffer(int count) { // for array method
//        int actual = Math.min(count, size);
//
//        for (int i = 0; i < actual; i++) {
//            PageData data = buffer[head];
//            buffer[head] = null; // help GC
//
//            head = (head + 1) % RAM_BUFFER_CAPACITY;
//            size--;
//
//            try {
//                if (!dbQueue.offer(data, 1, TimeUnit.SECONDS)) {
//                    System.err.println("[WARN] DB queue full, dropping page");
//                }
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//            }
//        }
//    }


    public static void main(String[] args) {
        List<String> seedList = new ArrayList<>();

        //seed list by gemini
        // Tech & CS
        seedList.add("https://en.wikipedia.org/wiki/Computer_science");
        seedList.add("https://www.geeksforgeeks.org/");
        seedList.add("https://stackoverflow.com/questions");
        seedList.add("https://news.ycombinator.com/");
        seedList.add("https://dev.to/");
        seedList.add("https://www.infoq.com/");
        seedList.add("https://thenewstack.io/");
        seedList.add("https://www.theregister.com/");

        // News & general
        seedList.add("https://www.bbc.com/news");
        seedList.add("https://www.reuters.com/");
        seedList.add("https://apnews.com/");
        seedList.add("https://www.theguardian.com/");

        // Science & knowledge
        seedList.add("https://www.scientificamerican.com/");
        seedList.add("https://phys.org/");
        seedList.add("https://arxiv.org/list/cs/recent");
        seedList.add("https://www.nature.com/news");

        // Reference
        seedList.add("https://en.wikipedia.org/wiki/Artificial_intelligence");
        seedList.add("https://en.wikipedia.org/wiki/Software_engineering");
        seedList.add("https://en.wikipedia.org/wiki/Data_structure");
        seedList.add("https://en.wikipedia.org/wiki/Operating_system");

        System.out.println("--- Launching CrawlerEngine with " + seedList.size() + " seeds ---");
        CrawlerEngine crawler = new CrawlerEngine(seedList);
        System.out.println("Starting the crawl process...");
        crawler.start();
        System.out.println("Crawl sequence finished.");
    }
}