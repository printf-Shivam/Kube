package com.Crawler.Kube;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:searchengine.db";
    private Connection con;
    private PreparedStatement pstmt;
    private final BlockingQueue<String[]> queue = new LinkedBlockingQueue<>();

    public DatabaseManager() {
        initDatabase();
    }

    private void initDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            con = DriverManager.getConnection(DB_URL);

            try (Statement stmt = con.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA synchronous=NORMAL");

                con.setAutoCommit(false);

                String createTableSQL = "CREATE TABLE IF NOT EXISTS pages ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "url TEXT UNIQUE NOT NULL,"
                        + "html_content TEXT NOT NULL"
                        + ");";
                stmt.execute(createTableSQL);
            }

            String insertSQL = "INSERT OR IGNORE INTO pages(url, html_content) VALUES(?, ?)";
            pstmt = con.prepareStatement(insertSQL);
            con.commit();

        } catch (ClassNotFoundException e) {
            System.err.println("jdbc error");
        } catch (Exception e) {
            System.err.println("db init failed: " + e.getMessage());
        }
    }

    public void savePage(String url, String html) {
        queue.offer(new String[]{url, html});
//        if (pstmt == null) return;
//
//        try {
//            pstmt.setString(1, url);
//            pstmt.setString(2, html);
//            pstmt.executeUpdate();
//            pstmt.clearParameters();
//        } catch (Exception e) {
//            System.err.println("failed to save page " + url + ": " + e.getMessage());
//        }
    }
    private void startWriter(){
        Thread writer = new Thread(()->{
            while(true){
                try{
                    List<String[]> batch = new ArrayList<>();
                    batch.add(queue.take());
                    queue.drainTo(batch,49);

                    for(String[] data: batch ){
                        pstmt.setString(1, data[0]);
                        pstmt.setString(2, data[1]);
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                    con.commit();
                    pstmt.clearBatch();
                }
                catch (Exception e){
                    System.err.println("DB writer Error");
                }
            }
        });
        writer.setDaemon(true);
        writer.start();
    }
}