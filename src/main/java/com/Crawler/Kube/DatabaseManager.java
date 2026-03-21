package com.Crawler.Kube;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:searchengine.db";
    private Connection con;
    private PreparedStatement pstmt;

    public DatabaseManager() {
        initDatabase();
    }

    private void initDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            con = DriverManager.getConnection(DB_URL);

            try (Statement stmt = con.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                String createTableSQL = "CREATE TABLE IF NOT EXISTS pages ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "url TEXT UNIQUE NOT NULL,"
                        + "html_content TEXT NOT NULL"
                        + ");";
                stmt.execute(createTableSQL);
            }

            String insertSQL = "INSERT OR IGNORE INTO pages(url, html_content) VALUES(?, ?)";
            pstmt = con.prepareStatement(insertSQL);

        } catch (ClassNotFoundException e) {
            System.err.println("jdbc error");
        } catch (Exception e) {
            System.err.println("db init failed: " + e.getMessage());
        }
    }

    public synchronized void savePage(String url, String html) {
        if (pstmt == null) return;

        try {
            pstmt.setString(1, url);
            pstmt.setString(2, html);
            pstmt.executeUpdate();
            pstmt.clearParameters();
        } catch (Exception e) {
            System.err.println("failed to save page " + url + ": " + e.getMessage());
        }
    }
}