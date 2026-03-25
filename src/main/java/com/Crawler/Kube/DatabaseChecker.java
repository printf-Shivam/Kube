package com.Crawler.Kube;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class DatabaseChecker {
    //generated using gemini
    private static final String DB_URL = "jdbc:sqlite:searchengine.db";

    public static void main(String[] args) {
        try (Connection con = DriverManager.getConnection(DB_URL);
             Statement stmt = con.createStatement()) {

            System.out.println("--- Database Status ---");

            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS total FROM pages;");
            System.out.println("Total pages saved: " + rs.getInt("total"));

            rs = stmt.executeQuery("SELECT COUNT(*) AS total FROM frontier;");
            System.out.println("URLs in frontier: " + rs.getInt("total"));

            System.out.println("\nSample of crawled URLs:");
            rs = stmt.executeQuery("SELECT url FROM pages LIMIT 5;");
            while (rs.next()) {
                System.out.println(" - " + rs.getString("url"));
            }

        } catch (Exception e) {
            System.err.println("Error reading database: " + e.getMessage());
        }
    }
}