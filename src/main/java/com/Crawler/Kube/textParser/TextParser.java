package com.Crawler.Kube.textParser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.sql.*;

public class TextParser {
    private static final String DBurl = "jdbc:sqlite:searchengine.db";

    public void processAndStoreText(){
        try (Connection con = DriverManager.getConnection(DBurl);
             Statement stmt = con.createStatement()){
            try{
                stmt.execute("ALTER TABLE pages ADD COLUMN clean_text TEXT;");
                System.out.println("clean text column added to db");
            }
            catch (Exception e){
                System.out.println("column already exists");
            }

            System.out.println("text cleaning started");

            //getting pages to clean
            String query = "SELECT url, html_content FROM pages WHERE clean_text IS NULL;";
            ResultSet rs = stmt.executeQuery(query);

            //preparing to update
            String updateQuery = "UPDATE pages SET clean_text = ? WHERE url = ?";
            PreparedStatement updateStmt = con.prepareStatement(updateQuery);

            int count=0;
            while(rs.next()){
                String url = rs.getString("url");
                String raw = rs.getString("html_content");

                Document doc = Jsoup.parse(raw);
                String clean = extractCleanText(doc);

                updateStmt.setString(1,clean);
                updateStmt.setString(2,url);
                updateStmt.executeUpdate();

                count++;
                if(count%50 == 0)
                    System.out.println("processed and stored " + count + " pages to db");
            }
            System.out.println("finished processing and cleaning");
        }
        catch(Exception e){
            System.err.println("db error" + e.getMessage());
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
        new TextParser().processAndStoreText();
    }
}