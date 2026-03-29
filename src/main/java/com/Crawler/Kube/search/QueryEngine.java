package com.Crawler.Kube.search;

import com.Crawler.Kube.indexer.SearchIndex;
import org.tartarus.snowball.ext.EnglishStemmer;

import java.util.*;

public class QueryEngine {

    Map<String,Map<String,Integer>> invertedIndex;
    Set<String> stopWords;

    public QueryEngine(Map<String,Map<String,Integer>> invertedIndex, Set<String> stopWords){
        this.invertedIndex = invertedIndex;
        this.stopWords = stopWords;
    }

    public void search(String serachQuery){

        String cleanQuery = serachQuery.toLowerCase().replaceAll("[^a-z0-9]", " ");
        String [] words = cleanQuery.split("\\s+");

        EnglishStemmer stemmer = new EnglishStemmer();
        List<String> targetWords = new ArrayList<>();

        for(String word : words){
            if(word.length()<2 || stopWords.contains(word)) continue;

            stemmer.setCurrent(word);
            stemmer.stem();
            targetWords.add(stemmer.getCurrent());
        }

        if(targetWords.isEmpty()){
            System.out.println("no words to search");
            return;
        }

        System.out.println("stemmed word : " + targetWords);

        // suppose we have two words, word1 nd word2.
        //for us, most relevant document is that which contains both the word
        // for this we find intersection of all target words

        HashMap<String, Integer> combined = new HashMap<>();
        boolean isFirstWord = true;

        for(String target : targetWords){
            Map<String, Integer> urlFreq = invertedIndex.get(target);

            if(urlFreq==null) return ;

            if(isFirstWord){
                combined.putAll(urlFreq);
                isFirstWord= false;
            }
            else{
                combined.keySet().retainAll(urlFreq.keySet());

                //scoring (basic ranking, simply adding words frequency to score)
                for (String url : combined.keySet()) {
                    int combinedScore = combined.get(url) + urlFreq.get(url);
                    combined.put(url, combinedScore);
                }
            }
        }
        if (combined.isEmpty()) {
            System.out.println("no results found for '" + serachQuery + "'\n--------------------------------------------------");
        } else {
            System.out.println("Found in " + combined.size() + " pages. Top results:");
            combined.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(10)
                    .forEach(entry ->
                            System.out.println("-> " + entry.getKey() + " (combined mentions: " + entry.getValue() + ")")
                    );
            System.out.println("--------------------------------------------------");
        }
    }
    public static void main(String[] args) {

        SearchIndex builder = new SearchIndex();
        SearchIndex.buildIndex();
        QueryEngine engine = new QueryEngine(builder.getInvertedIndex(), builder.getStopWords());

        engine.search("stack overflow");
    }
}