package com.searchEngine.Kube.controller;


import java.util.Map;

import com.searchEngine.Kube.indexer.SearchIndex;
import com.searchEngine.Kube.search.QueryEngine;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin
public class SearchController {
    private final QueryEngine queryEngine;

    public SearchController() {
        System.out.println("booting search api");
        SearchIndex indexer = new SearchIndex();
        this.queryEngine = new QueryEngine(indexer.getStopWords());
    }

    @GetMapping({"/api/search"})
    public Map<String, Double> search(@RequestParam(name = "q") String userQuery) {
        System.out.println("user query :" + userQuery);
        return this.queryEngine.search(userQuery);
    }
}
