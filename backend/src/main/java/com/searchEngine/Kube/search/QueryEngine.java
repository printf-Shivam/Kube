package com.searchEngine.Kube.search;

import com.searchEngine.Kube.database.DatabaseManager;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import com.searchEngine.Kube.indexer.SearchIndex;
import com.searchEngine.Kube.ranking.TFIDFCalculator;
import org.springframework.stereotype.Service;
import org.tartarus.snowball.ext.EnglishStemmer;

@Service
public class QueryEngine {
    private static final String DBurl = "jdbc:sqlite:searchengine.db";
    Set<String> stopWords;
    DatabaseManager databaseManager = new DatabaseManager();
    TFIDFCalculator tfidfCalculator = new TFIDFCalculator();

    public QueryEngine(Set<String> stopWords) {
        this.stopWords = stopWords;
    }

    private Map<String, Integer> fetchWordDataFromDB(String targetWord) {
        Map<String, Integer> urlFreq = new HashMap();
        String query = "SELECT url, frequency FROM inverted_index WHERE word = ?";

        try (
                Connection con = DriverManager.getConnection("jdbc:sqlite:searchengine.db");
                PreparedStatement pstmt = con.prepareStatement(query);
        ) {
            pstmt.setString(1, targetWord);
            ResultSet rs = pstmt.executeQuery();

            while(rs.next()) {
                urlFreq.put(rs.getString("url"), rs.getInt("frequency"));
            }
        } catch (Exception var12) {
            System.err.println("DB search error");
        }

        return urlFreq.isEmpty() ? null : urlFreq;
    }

    public Map<String, Double> search(String serachQuery) {
        int totalDocs = this.databaseManager.getTotalDocumentCount();
        if (totalDocs == 0) {
            System.out.println("db empty");
            return Collections.emptyMap();
        } else {
            String cleanQuery = serachQuery.toLowerCase().replaceAll("[^a-z0-9]", " ");
            String[] words = cleanQuery.split("\\s+");
            EnglishStemmer stemmer = new EnglishStemmer();
            List<String> targetWords = new ArrayList();

            for(String word : words) {
                if (word.length() >= 2 && !this.stopWords.contains(word)) {
                    stemmer.setCurrent(word);
                    stemmer.stem();
                    targetWords.add(stemmer.getCurrent());
                }
            }

            if (targetWords.isEmpty()) {
                System.out.println("no words to search");
                return Collections.emptyMap();
            } else {
                System.out.println("stemmed word : " + String.valueOf(targetWords));
                Map<String, Double> combined = new HashMap();
                boolean isFirstWord = true;

                for(String target : targetWords) {
                    Map<String, Integer> rawUrlFreq = this.fetchWordDataFromDB(target);
                    if (rawUrlFreq == null) {
                        System.out.println("no result found");
                        return Collections.emptyMap();
                    }

                    Map<String, Double> tf_idf_scores = this.tfidfCalculator.calculateWordScores(rawUrlFreq, totalDocs);
                    if (isFirstWord) {
                        combined.putAll(tf_idf_scores);
                        isFirstWord = false;
                    } else {
                        combined.keySet().retainAll(tf_idf_scores.keySet());

                        for(String url : combined.keySet()) {
                            Double combinedScore = (Double)combined.get(url) + (Double)tf_idf_scores.get(url);
                            combined.put(url, combinedScore);
                        }
                    }
                }

                Map<String, Double> topResults = new LinkedHashMap<>();

                combined.entrySet().stream()
                        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                        .limit(10)
                        .forEach(entry -> topResults.put(entry.getKey(), entry.getValue()));

                return topResults;
            }
        }
    }

    public static void main(String[] args) {
        SearchIndex builder = new SearchIndex();
        QueryEngine engine = new QueryEngine(builder.getStopWords());
        engine.search("java security");
        engine.search("vulnerability");
    }
}
