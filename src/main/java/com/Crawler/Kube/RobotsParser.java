package com.Crawler.Kube;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

public class RobotsParser {

    //will store disallowed paths so that rules are not fetched every time
    private ConcurrentHashMap<String, List<String>> robotsMemory = new ConcurrentHashMap<>();

    //
    public boolean isAllowed(String host, String path){

        List<String> disallowed = robotsMemory.computeIfAbsent(host, this::fetchRules);

        for(String rule : disallowed){
            if(path.startsWith(rule)) return false;
        }
        return true;
    }

    //
    private List<String> fetchRules(String host){
        List<String> disallowed = new ArrayList<>();
        try {
            String robotsUrl = "https://" + host + "/robots.txt";
            String body = Jsoup.connect(robotsUrl)
                    .timeout(3000)
                    .ignoreHttpErrors(true)
                    .execute()
                    .body();
            boolean applicable = false;

            for(String line : body.split("\n")){
                line = line.trim();

                if(line.toLowerCase().startsWith("user-agent:")) {

                    String agent = line.substring(11).trim();
                    applicable = agent.equals("*");
                }

                if(applicable && line.toLowerCase().startsWith("disallow")) {

                    String path = line.substring(10).trim();
                    if(!path.isEmpty()) disallowed.add(path);
                }
            }
        } catch (Exception e) {
            // TODO: handle exception
        }
        return disallowed;
    }
}
