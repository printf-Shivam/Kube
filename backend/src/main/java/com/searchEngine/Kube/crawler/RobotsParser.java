package com.searchEngine.Kube.crawler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

public class RobotsParser {
    private ConcurrentHashMap<String, List<String>> robotsMemory = new ConcurrentHashMap();

    public boolean isAllowed(String host, String path) {
        for (String rule : (List<String>) this.robotsMemory.computeIfAbsent(host, this::fetchRules)) {
            if (path.startsWith(rule)) {
                return false;
            }
        }
        return true;
    }

    private List<String> fetchRules(String host) {
        List<String> disallowed = new ArrayList();

        try {
            String robotsUrl = "https://" + host + "/robots.txt";
            Connection.Response response = Jsoup.connect(robotsUrl).userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36").timeout(5000).ignoreHttpErrors(true).ignoreContentType(true).execute();
            if (response.statusCode() == 404) {
                return disallowed;
            }

            String body = response.body();
            boolean applicable = false;

            for(String line : body.split("\n")) {
                line = line.trim();
                if (line.toLowerCase().startsWith("user-agent:")) {
                    String agent = line.substring(line.indexOf(58) + 1).trim();
                    applicable = agent.equals("*");
                }

                if (applicable && line.toLowerCase().startsWith("disallow:")) {
                    String path = line.substring(line.indexOf(58) + 1).trim();
                    if (!path.isEmpty()) {
                        disallowed.add(path);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("error fetching rules for " + host + " " + e.getMessage());
        }

        return disallowed;
    }
}
