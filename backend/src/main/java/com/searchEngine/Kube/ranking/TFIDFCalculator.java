package com.searchEngine.Kube.ranking;

import java.util.HashMap;
import java.util.Map;

public class TFIDFCalculator {
    public Map<String, Double> calculateWordScores(Map<String, Integer> urlFreq, int totalDocs) {
        Map<String, Double> wordScores = new HashMap();
        int df = urlFreq.size();
        double idf = Math.log10((double)totalDocs / (double)df);

        for(Map.Entry<String, Integer> entry : urlFreq.entrySet()) {
            String url = (String)entry.getKey();
            int tf = (Integer)entry.getValue();
            double tfIdfScore = (double)tf * idf;
            wordScores.put(url, tfIdfScore);
        }

        return wordScores;
    }
}
