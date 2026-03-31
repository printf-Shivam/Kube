package com.searchEngine.Kube.textParser;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class TextParser {
    private static final String DBurl = "jdbc:sqlite:searchengine.db";

    public void processAndStoreText() {
        try (
                Connection con = DriverManager.getConnection("jdbc:sqlite:searchengine.db");
                Statement stmt = con.createStatement();
        ) {
            try {
                stmt.execute("ALTER TABLE pages ADD COLUMN clean_text TEXT;");
                System.out.println("clean text column added to db");
            } catch (Exception var14) {
                System.out.println("column already exists");
            }

            System.out.println("text cleaning started");
            String query = "SELECT url, html_content FROM pages WHERE clean_text IS NULL;";
            ResultSet rs = stmt.executeQuery(query);
            String updateQuery = "UPDATE pages SET clean_text = ? WHERE url = ?";
            PreparedStatement updateStmt = con.prepareStatement(updateQuery);
            int count = 0;

            while(rs.next()) {
                String url = rs.getString("url");
                String raw = rs.getString("html_content");
                Document doc = Jsoup.parse(raw);
                String clean = this.extractCleanText(doc);
                updateStmt.setString(1, clean);
                updateStmt.setString(2, url);
                updateStmt.executeUpdate();
                ++count;
                if (count % 50 == 0) {
                    System.out.println("processed and stored " + count + " pages to db");
                }
            }

            System.out.println("finished processing and cleaning");
        } catch (Exception e) {
            System.err.println("db error" + e.getMessage());
        }

    }

    private String extractCleanText(Document doc) {
        if (doc == null) {
            return "";
        } else {
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
            return text.length() < 200 ? "" : text;
        }
    }

    public static void main(String[] args) {

        (new TextParser()).processAndStoreText();
    }
}
