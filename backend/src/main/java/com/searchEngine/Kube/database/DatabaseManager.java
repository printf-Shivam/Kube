package com.searchEngine.Kube.database;

import com.searchEngine.Kube.crawler.PageData;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseManager {
    private static final String URL = "jdbc:postgresql://localhost:5432/search_engine";
    private static final String USER = "postgres";
    private static final String PASSWORD = "1234";

    private Connection con;
    private PreparedStatement pstmt;

    private java.util.concurrent.atomic.AtomicInteger dbReads = new java.util.concurrent.atomic.AtomicInteger(0);
    private java.util.concurrent.atomic.AtomicInteger dbWrites = new java.util.concurrent.atomic.AtomicInteger(0);

    public DatabaseManager() {
        this.initDatabase();
    }

    public int getDbReads() {
        return dbReads.get();
    }

    public int getDbWrites() {
        return dbWrites.get();
    }

    private void initDatabase() {
        try {
            Class.forName("org.postgresql.Driver");

            this.con = DriverManager.getConnection(URL, USER, PASSWORD);
            this.con.setAutoCommit(false);

            try (Statement stmt = con.createStatement()) {

                String createTableSQL = """
                CREATE TABLE IF NOT EXISTS pages (
                    id SERIAL PRIMARY KEY,
                    url TEXT UNIQUE NOT NULL,
                    title TEXT,
                    clean_text TEXT
                );
            """;

                stmt.execute(createTableSQL);

                String frontierSQL = """
                CREATE TABLE IF NOT EXISTS frontier (
                    url TEXT UNIQUE NOT NULL
                );
            """;

                stmt.execute(frontierSQL);

                String jsQueueSQL = """
                CREATE TABLE IF NOT EXISTS js_render_queue (
                    url TEXT PRIMARY KEY,
                    status TEXT DEFAULT 'pending',
                    retry_count INT DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """;

                stmt.execute(jsQueueSQL);

                con.commit();
            }

        } catch (Exception e) {
            System.err.println("Postgres init failed: " + e.getMessage());
        }
    }


    public void close() {
        try {
            if (this.con == null || this.con.isClosed()) return;

            this.con.close();
            System.out.println("db closed");

        } catch (Exception e) {
            System.err.println("error closing DB: " + e.getMessage());
        }
    }

//    public Set<String> getVisitedUrls() {
//        Set<String> previousUrls = ConcurrentHashMap.newKeySet();
//        String query = "SELECT url from pages;";
//
//        try (
//                Statement stmt = this.con.createStatement();
//                ResultSet rs = stmt.executeQuery(query);
//        ) {
//            while(rs.next()) {
//                previousUrls.add(rs.getString("url"));
//            }
//
//            this.con.commit();
//        } catch (Exception e) {
//            System.err.println("error getting previous urls: " + e.getMessage());
//        }
//
//        return previousUrls;
//    }

    public void saveFrontier(List<String> remainingUrls) {
        if (!remainingUrls.isEmpty()) {
            try {
                if (this.con == null || this.con.isClosed()) {
                    return;
                }

                String query = "INSERT INTO frontier(url) VALUES(?) ON CONFLICT DO NOTHING";

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

//    public List<String> loadFrontier() {
//        List<String> urls = new ArrayList();
//
//        try (
//                Statement s = this.con.createStatement();
//                ResultSet rs = s.executeQuery("SELECT url FROM frontier");
//        ) {
//            while(rs.next()) {
//                urls.add(rs.getString("url"));
//            }
//
//            s.execute("DELETE FROM frontier");
//            this.con.commit();
//        } catch (Exception var10) {
//            System.err.println("error in loading frontier from db");
//        }
//
//        return urls;
//    }

    public int getTotalDocumentCount() {
        String query = "SELECT COUNT(*) AS total FROM pages WHERE clean_text IS NOT NULL AND clean_text != '';";

        try {
            try (
                    Connection con = DriverManager.getConnection(URL, USER, PASSWORD);
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



    public void saveJsUrl(String url) {
        String sql = "INSERT INTO js_render_queue (url, status) VALUES (?, 'pending') ON CONFLICT DO NOTHING";

        try (PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, url);
            stmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getNextJsUrl() {
        String selectSql = "SELECT url FROM js_render_queue WHERE status = 'pending' LIMIT 1";
        String updateSql = "UPDATE js_render_queue SET status = 'processing' WHERE url = ?";

        try {
            con.setAutoCommit(false);

            String url = null;

            try (PreparedStatement selectStmt = con.prepareStatement(selectSql);
                 ResultSet rs = selectStmt.executeQuery()) {

                if (rs.next()) {
                    url = rs.getString("url");
                }
            }

            if (url != null) {
                try (PreparedStatement updateStmt = con.prepareStatement(updateSql)) {
                    updateStmt.setString(1, url);
                    updateStmt.executeUpdate();
                }
            }

            con.commit();
            con.setAutoCommit(false);
            con.setAutoCommit(true);

            return url;

        } catch (Exception e) {
            e.printStackTrace();
            try { con.rollback(); } catch (Exception ex) {}
        }

        return null;
    }

    public void markJsDone(String url) {
        String sql = "UPDATE js_render_queue SET status = 'done' WHERE url = ?";

        try (PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, url);
            stmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void markJsFailed(String url) {
        String sql = "UPDATE js_render_queue SET status = 'pending', retry_count = retry_count + 1 WHERE url = ? AND retry_count < 3";

        try (PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1, url);
            stmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void initJsQueueTable() {
        String sql = """
        CREATE TABLE IF NOT EXISTS js_render_queue (
            url TEXT PRIMARY KEY,
            status TEXT DEFAULT 'pending',
            retry_count INTEGER DEFAULT 0,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    """;

        try (Statement stmt = con.createStatement()) {
            stmt.execute(sql);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        DatabaseManager dbm = new DatabaseManager();
        System.out.println("total doc count" + dbm.getTotalDocumentCount());
    }

    public void savePageBatch(List<PageData> batch) {
        if (batch == null || batch.isEmpty()) return;

        String sql = "INSERT INTO pages(url, title, clean_text) VALUES (?, ?, ?) ON CONFLICT DO NOTHING";

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (PageData data : batch) {
                ps.setString(1, data.url);
                ps.setString(2, data.title);
                ps.setString(3, data.text);
                ps.addBatch();
            }

            ps.executeBatch();
            dbWrites.incrementAndGet();

        } catch (Exception e) {
            System.err.println("Batch insert failed: " + e.getMessage());
        }
    }
}
