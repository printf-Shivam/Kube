package com.searchEngine.Kube.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:searchengine.db";
    private Connection con;
    private PreparedStatement pstmt;
    private final BlockingQueue<String[]> queue = new LinkedBlockingQueue();

    public DatabaseManager() {
        this.initDatabase();
    }

    private void initDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            this.con = DriverManager.getConnection("jdbc:sqlite:searchengine.db");

            try (Statement stmt = this.con.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA synchronous=NORMAL");
                this.con.setAutoCommit(false);
                String createTableSQL = "CREATE TABLE IF NOT EXISTS pages (id INTEGER PRIMARY KEY AUTOINCREMENT,url TEXT UNIQUE NOT NULL,html_content TEXT NOT NULL);";
                stmt.execute(createTableSQL);
                String createFrontierQuery = "CREATE TABLE IF NOT EXISTS frontier (url TEXT UNIQUE NOT NULL);";
                stmt.execute(createFrontierQuery);
            }

            String insertSQL = "INSERT OR IGNORE INTO pages(url, html_content) VALUES(?, ?)";
            this.pstmt = this.con.prepareStatement(insertSQL);
            this.con.commit();
        } catch (ClassNotFoundException var6) {
            System.err.println("jdbc error");
        } catch (Exception e) {
            System.err.println("db init failed: " + e.getMessage());
        }

    }

    public void savePage(String url, String html) {
        this.queue.offer(new String[]{url, html});
    }

    private void startWriter() {
        Thread writer = new Thread(() -> {
            while(true) {
                try {
                    List<String[]> batch = new ArrayList();
                    batch.add((String[])this.queue.take());
                    this.queue.drainTo(batch, 49);

                    for(String[] data : batch) {
                        this.pstmt.setString(1, data[0]);
                        this.pstmt.setString(2, data[1]);
                        this.pstmt.addBatch();
                    }

                    this.pstmt.executeBatch();
                    this.con.commit();
                    this.pstmt.clearBatch();
                } catch (Exception var4) {
                    System.err.println("DB writer error");
                }
            }
        });
        writer.setDaemon(true);
        writer.start();
    }

    public void close() {
        try {
            if (this.con == null || this.con.isClosed()) {
                return;
            }

            List<String[]> leftovers = new ArrayList();
            this.queue.drainTo(leftovers);
            if (!leftovers.isEmpty()) {
                for(String[] data : leftovers) {
                    this.pstmt.setString(1, data[0]);
                    this.pstmt.setString(2, data[1]);
                    this.pstmt.addBatch();
                }

                this.pstmt.executeBatch();
                this.con.commit();
                System.out.println("saved " + leftovers.size() + " leftover pages");
            }

            try (Statement stmt = this.con.createStatement()) {
                stmt.execute("PRAGMA wal_checkpoint(TRUNCATE);");
            }

            if (this.con != null && !this.con.isClosed()) {
                this.con.close();
                System.out.println("db closed");
            }
        } catch (Exception e) {
            System.err.println("error in force stopping connection " + e.getMessage());
        }

    }

    public Set<String> getVisitedUrls() {
        Set<String> previousUrls = ConcurrentHashMap.newKeySet();
        String query = "SELECT url from pages;";

        try (
                Statement stmt = this.con.createStatement();
                ResultSet rs = stmt.executeQuery(query);
        ) {
            while(rs.next()) {
                previousUrls.add(rs.getString("url"));
            }

            this.con.commit();
        } catch (Exception e) {
            System.err.println("error getting previous urls: " + e.getMessage());
        }

        return previousUrls;
    }

    public void saveFrontier(List<String> remainingUrls) {
        if (!remainingUrls.isEmpty()) {
            try {
                if (this.con == null || this.con.isClosed()) {
                    return;
                }

                String query = "INSERT OR IGNORE INTO frontier(url) VALUES(?)";

                try (PreparedStatement ps = this.con.prepareStatement(query)) {
                    for(String url : remainingUrls) {
                        ps.setString(1, url);
                        ps.addBatch();
                    }

                    ps.executeBatch();
                    this.con.commit();
                    System.out.println("saved " + remainingUrls.size() + "urls from frontier for resumption");
                } catch (Exception e) {
                    System.err.println("error saving frontier " + e.getMessage());
                }
            } catch (Exception e) {
                System.err.println("error saving frontier " + e.getMessage());
            }

        }
    }

    public List<String> loadFrontier() {
        List<String> urls = new ArrayList();

        try (
                Statement s = this.con.createStatement();
                ResultSet rs = s.executeQuery("SELECT url FROM frontier");
        ) {
            while(rs.next()) {
                urls.add(rs.getString("url"));
            }

            s.execute("DELETE FROM frontier");
            this.con.commit();
        } catch (Exception var10) {
            System.err.println("error in loading frontier from db");
        }

        return urls;
    }

    public int getTotalDocumentCount() {
        String query = "SELECT COUNT(*) AS total FROM pages WHERE clean_text IS NOT NULL AND clean_text != '';";

        try {
            try (
                    Connection con = DriverManager.getConnection("jdbc:sqlite:searchengine.db");
                    Statement stmt = con.createStatement();
            ) {
                try (ResultSet rs = stmt.executeQuery(query)) {
                    if (rs.next()) {
                        int var5 = rs.getInt("total");
                        return var5;
                    } else {
                        return 0;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("error counting docs: " + e.getMessage());
            return 0;
        }
    }

    public static void main(String[] args) {
        DatabaseManager dbm = new DatabaseManager();
        System.out.println("total doc count" + dbm.getTotalDocumentCount());
    }
}
