package com.Crawler.Kube;

import java.sql.*;
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

                String createFrontierQuery = "CREATE TABLE IF NOT EXISTS frontier (url TEXT UNIQUE NOT NULL);";
                stmt.execute(createFrontierQuery);
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
                    System.err.println("DB writer error");
                }
            }
        });
        writer.setDaemon(true);
        writer.start();
    }
    public void close() {
        try {
            if (con == null || con.isClosed()) return;
            List<String[]> leftovers = new ArrayList<>();
            queue.drainTo(leftovers);

            if(!leftovers.isEmpty()){
                for(String[] data : leftovers){
                    pstmt.setString(1, data[0]);
                    pstmt.setString(2, data[1]);
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
                con.commit();
                System.out.println("saved " + leftovers.size() +" leftover pages");
            }

            try(Statement stmt = con.createStatement()){
                stmt.execute("PRAGMA wal_checkpoint(TRUNCATE);"); //after data is added to .db, delete wal file
            }

            if(con != null && !con.isClosed()){
                con.close();
                System.out.println("db closed");
            }
        }
        catch(Exception e){
            System.err.println("error in force stopping connection " +e.getMessage());
        }
    }

    public Set<String> getVisitedUrls(){
        Set<String> previousUrls = ConcurrentHashMap.newKeySet();
        String query = "SELECT url from pages;";

        try(Statement stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery(query)){

            while(rs.next()){
                previousUrls.add(rs.getString("url"));
            }
            con.commit();
        }
        catch(Exception e){
            System.err.println("error getting previous urls: " + e.getMessage());
        }

        return previousUrls;
    }

    public void saveFrontier(List<String> remainingUrls){
        if(remainingUrls.isEmpty()) return;
        try{
            if(con == null || con.isClosed()) return;

            String query = "INSERT OR IGNORE INTO frontier(url) VALUES(?)";

            try(PreparedStatement ps = con.prepareStatement(query)){
                for(String url : remainingUrls){
                    ps.setString(1,url);
                    ps.addBatch();
                }
                ps.executeBatch();
                con.commit();
                System.out.println("saved " + remainingUrls.size() + "urls from frontier for resumption");

            }
            catch (Exception e) {
                System.err.println("error saving frontier " + e.getMessage());
            }
        }
        catch (Exception e) {
            System.err.println("error saving frontier " + e.getMessage());
        }

    }

    public List<String> loadFrontier(){
        List<String> urls = new ArrayList<>();

        try(Statement s = con.createStatement();
            ResultSet rs = s.executeQuery("SELECT url FROM frontier")){

            while(rs.next())
                urls.add(rs.getString("url"));

            s.execute("DELETE FROM frontier");
            con.commit();
        }
        catch (Exception e){
            System.err.println("error in loading frontier from db");
        }

        return urls;
    }
}