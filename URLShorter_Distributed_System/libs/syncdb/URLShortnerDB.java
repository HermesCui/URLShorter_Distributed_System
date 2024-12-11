package libs.syncdb;

import java.math.BigInteger;
import java.sql.*;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import libs.config.ConfigState;
import libs.data.StorageDataUtil;
import libs.db.LRUCacheStore;
import libs.msg.ClusterSyncDBMessage;
import libs.com.*;

public class URLShortnerDB {

    private static URLShortnerDB instance = null;
    private static final ReentrantLock instanceLock = new ReentrantLock();
    private static final ConfigState state = new ConfigState();
    private final LRUCacheStore cache = new LRUCacheStore(100000);
    private Connection writeConn;
    private final ReentrantLock writeLock = new ReentrantLock();
    private final BlockingQueue<Connection> readConnPool;
    private final int POOL_SIZE = 10; 

    private String url = "jdbc:sqlite:" + ConfigState.kvStoreDbPath;;
    // 4 readers for client
    // 4 readers for p2p
    // 1 writer for all.


    // Private constructor to prevent instantiation
    private URLShortnerDB() {
        this.writeConn = connect(url);
        this.readConnPool = new ArrayBlockingQueue<>(POOL_SIZE);
        initializeReadConnections(url);

    }

    // Public method to provide access to the singleton instance
    public static URLShortnerDB getInstance() {
        if (instance == null) {
            instanceLock.lock();
            try {
                if (instance == null) {
                    instance = new URLShortnerDB();
                }
            } finally {
                instanceLock.unlock();
            }
        }
        return instance;
    }

    // Method to establish a database connection
    private static Connection connect(String url) {
        try {
            StorageDataUtil.validateDirectory(ConfigState.kvStoreDbPathDir);
            Connection conn = DriverManager.getConnection(url);
            Statement stmt = conn.createStatement();
            stmt.executeUpdate("PRAGMA synchronous = NORMAL;");
            stmt.executeUpdate("PRAGMA journal_mode = WAL;");
            return conn;
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("URLShortnerDB: Connection error: " + e.getMessage());
            return null;
        }
    }

    // Initialize the read connection pool
    private void initializeReadConnections(String url) {
        for (int i = 0; i < POOL_SIZE; i++) {
            Connection conn = connect(url);
            if (conn != null) {
                try {
                    readConnPool.put(conn);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("URLShortnerDB: Failed to initialize read connection pool.");
                }
            } else {
                System.err.println("URLShortnerDB: Failed to create read connection.");
            }
        }
    }

    // Acquire a read connection from the pool
    private Connection acquireReadConnection() {
        try {
            return readConnPool.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("URLShortnerDB: Interrupted while acquiring read connection.");
            return null;
        }
    }
    
    // Release a read connection back to the pool [investigate later bug on conn reuse.]
    private void releaseReadConnection(Connection conn) {
        if (conn != null) {
            try {
                readConnPool.put(conn);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("URLShortnerDB: Interrupted while releasing read connection.");
            }
        }
    }

    // Read operation using a connection from the pool
    public String find(String shortURL) {
        if (cache.containsKey(shortURL)) {
            return cache.get(shortURL);
        }

        Connection conn = acquireReadConnection();
        if (conn == null) {
            return null; 
        }

        String sql = "SELECT longurl FROM bitly WHERE shorturl = ?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, shortURL);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String longURL = rs.getString("longurl");
                    cache.put(shortURL, longURL);
                    return longURL;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("URLShortnerDB: Read error: " + e.getMessage());
        } finally {

            releaseReadConnection(conn);
        }
        return null;
    }

    public boolean save(String shortURL, String longURL) {
        writeLock.lock();
        try {
            String sql = "INSERT INTO bitly(shorturl, longurl) VALUES(?, ?) " +
                         "ON CONFLICT(shorturl) DO UPDATE SET longurl = excluded.longurl;";
            try (PreparedStatement ps = writeConn.prepareStatement(sql)) {
                ps.setString(1, shortURL);
                ps.setString(2, longURL);
                ps.executeUpdate();
                cache.put(shortURL, longURL);
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("URLShortnerDB: Write error: " + e.getMessage());
            return false;
        } finally {
            writeLock.unlock();
        }
    }

    public boolean saveBatch(ArrayList<String> rows) {
        System.out.println("Starting batch write stage on row data:\n");
        writeLock.lock();
        try {
            String sql = "INSERT INTO bitly(shorturl, longurl, opTime) VALUES(?, ?, ?) " +
                         "ON CONFLICT(shorturl) DO UPDATE SET " +
                         "longurl = excluded.longurl, " +
                         "opTime = excluded.opTime " +
                         "WHERE excluded.opTime > bitly.opTime;";

            writeConn.setAutoCommit(false);
            try (PreparedStatement ps = writeConn.prepareStatement(sql)) {
                int validRows = 0;
                for (String row : rows) {
                    String[] columns = row.split(",", 3); // Limit to 3 parts to handle URLs with commas
                    if (columns.length < 3) {
                        System.err.println("URLShortnerDB: Invalid row format: " + row);
                        continue;
                    }

                    String shortURL = columns[0].trim();
                    String longURL = columns[1].trim();
                    String opTimeStr = columns[2].trim();

                    Timestamp opTime;
                    try {
                        if (opTimeStr.endsWith(".0")) {
                            opTimeStr = opTimeStr.substring(0, opTimeStr.length() - 2);
                        }
                        opTime = Timestamp.valueOf(opTimeStr);
                    } catch (IllegalArgumentException e) {
                        System.err.println("URLShortnerDB: Invalid opTime format in row: " + row);
                        continue;
                    }

                    ps.setString(1, shortURL);
                    ps.setString(2, longURL);
                    ps.setTimestamp(3, opTime);

                    System.out.println("Prepared to insert/update row: shortURL=" + shortURL + ", longURL=" + longURL + ", opTime=" + opTime);
                    ps.addBatch();

                    cache.put(shortURL, longURL);
                    validRows++;
                }

                if (validRows == 0) {
                    System.out.println("No valid rows to insert/update.");
                    writeConn.setAutoCommit(true);
                    return true;
                }

                // Execute the batch and log the results
                int[] batchResults = ps.executeBatch();
                for (int i = 0; i < batchResults.length; i++) {
                    System.out.println("Batch operation " + (i + 1) + ": " + (batchResults[i] == PreparedStatement.EXECUTE_FAILED ? "Failed" : "Success"));
                }

                writeConn.commit();
                System.out.println("URLShortnerDB: Successfully saved batch of " + validRows + " rows.");
                return true;
            } catch (SQLException e) {
                writeConn.rollback();
                System.err.println("URLShortnerDB: Batch write error: " + e.getMessage());
                e.printStackTrace();
                return false;
            } finally {
                writeConn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("URLShortnerDB: Transaction error: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            writeLock.unlock();
        }
    }

    public int getRowCount() {
        String sql = "SELECT COUNT(*) AS total FROM bitly";
        int rowCount = -1;

        Connection conn = acquireReadConnection();
        if (conn == null) {
            System.err.println("Failed to acquire a database connection.");
            return -1;
        }

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                rowCount = rs.getInt("total");
                System.out.println("Total rows in table '" + "bitly" + "': " + rowCount);
            }

        } catch (SQLException e) {
            // Log the exception message
            System.err.println("Error fetching row count from table '" + "bitly" + "': " + e.getMessage());
        } finally {
            // Release the database connection
            releaseReadConnection(conn);
        }

        return rowCount;
    }



    public ArrayList<String> fetchRequestedRows(ClusterSyncDBMessage msg) {

        System.out.println("Querying the database for the required rows:");
        System.out.println("Requested row data: epoch=" + msg.epocDateAsOf);
        System.out.println("Requested row data: window=" + msg.window);
        System.out.println("Requested row data: current=" + msg.curr);
        System.out.println("Host start: " + msg.hostStart + " Host end: " + msg.hostEnd);
        System.out.println("Requested row data: start=" + msg.hostStart + ", end=" + msg.hostEnd);
    
        //System.out.println("Total Canidates for fetching data:" + getRowCount());
        int window = msg.window;
        int curr = msg.curr;
        int offset = window * curr;

        ConsistentHashing consistentHashing = ConsistentHashing.getInstance();
        ArrayList<String> fetchedRows = new ArrayList<>();
        String sql = "SELECT shorturl, longurl, opTime FROM bitly ORDER BY opTime ASC LIMIT ? OFFSET ?;";
        Connection conn = acquireReadConnection();
        if (conn == null) {
            System.err.println("Failed to acquire a database connection.");
            return null;
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) { 
            ps.setInt(1, window);  
            ps.setInt(2, offset);   
            try (ResultSet rs = ps.executeQuery()) {
                
                int totalRows = 0;
                int rowsInRange = 0;
                
                while (rs.next()) {
                    totalRows += 1;
                    String shortURL = rs.getString("shorturl");
                    String longURL = rs.getString("longurl");
                    Timestamp opTimeTimestamp = rs.getTimestamp("opTime");
                    String opTime = opTimeTimestamp != null ? opTimeTimestamp.toString() : "null";
                    
                    System.out.println("Processing row " + totalRows + ":");
                    System.out.println("shortURL = " + shortURL + ", longURL = " + longURL + ", opTime = " + opTime);
                    
                    boolean inRange = consistentHashing.withinRange(shortURL, msg.hostStart, msg.hostEnd);
                    System.out.println("shortURL Hash within range [" + msg.hostStart + ", " + msg.hostEnd + "]: " + inRange);
                    if (inRange) {
                        fetchedRows.add(shortURL + "," + longURL + "," + opTime);
                        rowsInRange += 1;
                    }
                }
    
                System.out.println("Total rows fetched from DB: " + totalRows);
                System.out.println("Rows within range: " + rowsInRange);
            }
        } catch (SQLException e) {
            System.err.println("URLShortnerDB: Error fetching requested rows: " + e.getMessage());
            return null;
        } finally {
            releaseReadConnection(conn);
        }
        System.out.println("Number of rows added to fetchedRows: " + fetchedRows.size());
        return fetchedRows;
    }
    

    // Shutdown method to close all connections
    public void shutdown() {
        try {
            writeConn.close();
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("URLShortnerDB: Error closing write connection: " + e.getMessage());
        }

        for (Connection conn : readConnPool) {
            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
                System.err.println("URLShortnerDB: Error closing read connection: " + e.getMessage());
            }
        }
    }
}
