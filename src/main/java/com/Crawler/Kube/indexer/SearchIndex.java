package com.Crawler.Kube.indexer;

import org.tartarus.snowball.ext.EnglishStemmer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

public class SearchIndex {
    private static final String DB_URL = "jdbc:sqlite:searchengine.db";

    // word -> (url->freq count). storing in RAM for now(in-memory)
    private final Map<String, Map<String, Integer>> invertedIndex = new HashMap<>();

    // stop words removal, using NLTK's list
    private final Set<String> stopWords = Set.of(
            "i", "me", "my", "myself", "we", "our", "ours", "ourselves",
            "you", "your", "yours", "yourself", "yourselves",
            "he", "him", "his", "himself",
            "she", "her", "hers", "herself",
            "it", "its", "itself",
            "they", "them", "their", "theirs", "themselves",
            "what", "which", "who", "whom",
            "this", "that", "these", "those",
            "am", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "having",
            "do", "does", "did", "doing",
            "a", "an", "the",
            "and", "but", "if", "or", "because", "as", "until", "while",
            "of", "at", "by", "for", "with", "about", "against", "between",
            "into", "through", "during", "before", "after", "above", "below",
            "to", "from", "up", "down", "in", "out", "on", "off", "over", "under",
            "again", "further", "then", "once",
            "here", "there", "when", "where", "why", "how",
            "all", "any", "both", "each", "few", "more", "most", "other", "some", "such",
            "no", "nor", "not", "only", "own", "same", "so", "than", "too", "very",
            "s", "t", "can", "will", "just", "don", "should", "now"
    );

    public void buildIndex() {
        System.out.println("loading clean text from db...");
        int docCount = 0;

        //selecting only rows for which we have clean text for
        String query = "SELECT url, clean_text FROM pages WHERE clean_text IS NOT NULL AND clean_text != '';";

        try (Connection con = DriverManager.getConnection(DB_URL);
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                String url = rs.getString("url");
                String text = rs.getString("clean_text");

                indexDocument(url, text);
                docCount++;
            }
            System.out.println("index built successfully, indexed " + docCount + " docs.\n");
        }
        catch(Exception e) {
            System.err.println("db error: " + e.getMessage());
        }
    }

    private void indexDocument(String url, String text) {
        //normalizing text(basic )
        String cleanText = text.toLowerCase().replaceAll("[^a-z0-9]", " ");

        //tokenize (break long texts into words)
        String[] words = cleanText.split("\\s+");

        EnglishStemmer stemmer = new EnglishStemmer();

        //count freq of token for specific url
        for(String word : words) {
            if (word.length() < 2 || stopWords.contains(word)) continue;

            stemmer.setCurrent(word);
            stemmer.stem();
            String stemmed = stemmer.getCurrent();

            invertedIndex.putIfAbsent(stemmed, new HashMap<>());
            Map<String, Integer> urlFreq = invertedIndex.get(stemmed);

            //incr the freq of this word for this url
            urlFreq.put(url, urlFreq.getOrDefault(url, 0) + 1);
        }
    }

    public void searchTest(String query) {
        EnglishStemmer stemmer = new EnglishStemmer();

        stemmer.setCurrent(query.toLowerCase().trim());
        stemmer.stem();
        String targetWord = stemmer.getCurrent();
        System.out.println("searching for: '" + query + "' (Stemmed to: '" + targetWord + "')");
        if(invertedIndex.containsKey(targetWord)) {
            Map<String, Integer> results = invertedIndex.get(targetWord);
            System.out.println("Found in " + results.size() + " pages. top results:-");

            //relevant pages = high freq, so sort by highest freq
            results.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(10)
                    .forEach(entry ->
                            System.out.println("-> " + entry.getKey() + " (total mentions: "+ entry.getValue() +")")
                    );
        }
        else
            System.out.println("no results found for '" + targetWord );
    }

    public static void main(String[] args) {
        SearchIndex engine = new SearchIndex();

        //read from db and build index
        engine.buildIndex();

        //testing words to search
        engine.searchTest(".");
        engine.searchTest("vulnerability");
        engine.searchTest("java");
    }
}