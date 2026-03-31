package com.searchEngine.Kube.indexer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.tartarus.snowball.ext.EnglishStemmer;

@Service
public class SearchIndex {
    private static final String DB_URL = "jdbc:sqlite:searchengine.db";
    private static final Set<String> stopWords = Set.of("i", "me", "my", "myself", "we", "our", "ours", "ourselves", "you", "your", "yours", "yourself", "yourselves", "he", "him", "his", "himself", "she", "her", "hers", "herself", "it", "its", "itself", "they", "them", "their", "theirs", "themselves", "what", "which", "who", "whom", "this", "that", "these", "those", "am", "is", "are", "was", "were", "be", "been", "being", "have", "has", "had", "having", "do", "does", "did", "doing", "a", "an", "the", "and", "but", "if", "or", "because", "as", "until", "while", "of", "at", "by", "for", "with", "about", "against", "between", "into", "through", "during", "before", "after", "above", "below", "to", "from", "up", "down", "in", "out", "on", "off", "over", "under", "again", "further", "then", "once", "here", "there", "when", "where", "why", "how", "all", "any", "both", "each", "few", "more", "most", "other", "some", "such", "no", "nor", "not", "only", "own", "same", "so", "than", "too", "very", "s", "t", "can", "will", "just", "don", "should", "now");

    public static void buildIndex() {
        System.out.println("loading clean text from db...");
        String createTableSQL = "CREATE TABLE IF NOT EXISTS inverted_index (word TEXT, url TEXT, frequency INTEGER, PRIMARY KEY (word, url));";
        String selectPagesSQL = "SELECT url, clean_text FROM pages WHERE clean_text IS NOT NULL AND clean_text != '';";
        String upsertSQL = "INSERT INTO inverted_index (word, url, frequency) VALUES (?, ?, ?) ON CONFLICT(word, url) DO UPDATE SET frequency = frequency + excluded.frequency;";

        try (
                Connection con = DriverManager.getConnection("jdbc:sqlite:searchengine.db");
                Statement stmt = con.createStatement();
        ) {
            stmt.execute(createTableSQL);

            try (PreparedStatement pstmt = con.prepareStatement(upsertSQL)) {
                con.setAutoCommit(false);
                ResultSet rs = stmt.executeQuery(selectPagesSQL);
                int docCount = 0;

                while(rs.next()) {
                    String url = rs.getString("url");
                    String text = rs.getString("clean_text");
                    indexDocumentToDB(url, text, pstmt);
                    ++docCount;
                    if (docCount % 50 == 0) {
                        pstmt.executeBatch();
                        con.commit();
                        System.out.println("Indexed " + docCount + " documents");
                    }
                }

                pstmt.executeBatch();
                con.commit();
                System.out.println("index built successfully");
            }
        } catch (Exception e) {
            System.err.println("db error: " + e.getMessage());
        }

    }

    private static void indexDocumentToDB(String url, String text, PreparedStatement pstmt) throws Exception {
        String cleanText = text.toLowerCase().replaceAll("[^a-z0-9]", " ");
        String[] words = cleanText.split("\\s+");
        EnglishStemmer stemmer = new EnglishStemmer();
        Map<String, Integer> localWordCount = new HashMap();

        for(String word : words) {
            if (word.length() >= 2 && !stopWords.contains(word)) {
                stemmer.setCurrent(word);
                stemmer.stem();
                String stemmed = stemmer.getCurrent();
                localWordCount.put(stemmed, (Integer)localWordCount.getOrDefault(stemmed, 0) + 1);
            }
        }

        for(Map.Entry<String, Integer> entry : localWordCount.entrySet()) {
            pstmt.setString(1, (String)entry.getKey());
            pstmt.setString(2, url);
            pstmt.setInt(3, (Integer)entry.getValue());
            pstmt.addBatch();
        }

    }

    public Set<String> getStopWords() {
        return stopWords;
    }

    public static void main(String[] args) {
        System.out.println("test started");
        buildIndex();
        System.out.println("test complete!");
    }
}
