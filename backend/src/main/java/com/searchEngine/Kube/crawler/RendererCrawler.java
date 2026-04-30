package com.searchEngine.Kube.crawler;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.searchEngine.Kube.database.DatabaseManager;

import java.util.List;
import java.util.concurrent.Semaphore;

public class RendererCrawler {

    private final DatabaseManager dbManager = new DatabaseManager();
    private final Semaphore browserLimiter = new Semaphore(3);

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;

    public void start() {
        initBrowser();

        List<PageData> batch = new java.util.ArrayList<>(10);

        while (true) {
            try {
                String url = dbManager.getNextJsUrl();

                if (url == null) {
                    flushBatch(batch);
                    Thread.sleep(1000);
                    continue;
                }

                String html = fetchWithBrowser(url);

                if (html != null) {
                    // 🔥 convert to lightweight data
                    org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);

                    String title = doc.title();
                    String text = doc.text();

                    batch.add(new PageData(url, title, text));

                    dbManager.markJsDone(url);
                } else {
                    dbManager.markJsFailed(url);
                }

                // 🔥 batch flush
                if (batch.size() >= 10) {
                    flushBatch(batch);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String fetchWithBrowser(String url) {
        Page page = null;

        try {
            browserLimiter.acquire();

            page = context.newPage();

            page.navigate(url, new Page.NavigateOptions().setTimeout(15000));
            page.waitForLoadState(LoadState.NETWORKIDLE);

            page.waitForTimeout(2000);

            String html = page.content();

            if (html.length() > 500_000) return null;

            if (html.contains("Just a moment") || html.contains("Enable JavaScript")) {
                System.out.println("Blocked page → " + url);
                return null;
            }

            return html;

        } catch (Exception e) {
            System.err.println("Browser fetch failed: " + url);
            return null;
        } finally {
            if (page != null) {
                try { page.close(); } catch (Exception ignored) {}
            }
            browserLimiter.release();
        }
    }

    public void initBrowser() {
        this.playwright = Playwright.create();
        this.browser = this.playwright.chromium().launch((new BrowserType.LaunchOptions()).setHeadless(false));
        this.context = this.browser.newContext((new Browser.NewContextOptions()).setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36").setViewportSize(1280, 800));
    }

    private void flushBatch(List<PageData> batch) {
        if (!batch.isEmpty()) {
            dbManager.savePageBatch(batch);
            batch.clear();
        }
    }
}