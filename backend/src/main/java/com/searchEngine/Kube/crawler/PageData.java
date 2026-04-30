package com.searchEngine.Kube.crawler;

public class PageData {
    public String url;
    public String title;
    public String text;

    public PageData(String u, String title, String text){
        url = u;
        this.title = title ;
        this.text = text;
    }
}
