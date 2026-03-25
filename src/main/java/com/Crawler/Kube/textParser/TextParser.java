package com.Crawler.Kube.textParser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class TextParser {
    private static final String DBurl = "jdbc:sqlite:searchengine.db";

    public void testParser(){
        String query = "SELECT url, html_content FROM pages LIMIT 3;";

        try(Connection con = DriverManager.getConnection(DBurl);
            Statement stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery(query)){

            System.out.println("HTML parsing test");

            while(rs.next()){
                String url = rs.getString("url");
                String rawHtml = rs.getString("html_content");

                Document doc = Jsoup.parse(rawHtml);

                String clean = extractCleanText(doc);

                System.out.println("source url : " + url);
                System.out.println("original html length : " + rawHtml.length());
                System.out.println("cleaned text length : " + clean.length());

                if(clean.length() > 0){
                    System.out.println("snippet: " + clean.substring(0, Math.min(clean.length(),200)));
                } else {
                    System.out.println("snippet: [EMPTY / LOW QUALITY]");
                }

                System.out.println("--------------------------------------------------------\n");
            }

        } catch (Exception e) {
            System.err.println("db read error");
        }
    }


    private String extractCleanText(Document doc) {
        if (doc == null) return "";

        doc.select("script, style, nav, footer, header, aside, noscript").remove();
        doc.select(".nav, .menu, .footer, .header, .sidebar, .ads, .banner").remove();

        Element main = doc.selectFirst("main, article, .content, .post, .entry-content");

        String text;
        if (main != null) {
            text = main.text();
        } else {
            text = doc.body().text();
        }

        text = text.replaceAll("(?i)enable javascript.*", "");
        text = text.replaceAll("(?i)just a moment.*", "");
        text = text.replaceAll("(?i)please enable javascript.*", "");

        text = text.replaceAll("\\s+", " ").trim();

        if (text.length() < 200) {
            return "";
        }

        return text;
    }

    public static void main(String[] args) {
        new TextParser().testParser();
    }
}