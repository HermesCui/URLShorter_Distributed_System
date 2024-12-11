package libs.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import libs.data.StorageDataUtil;

import libs.config.ConfigState;

//[0] 1 The application responds after a database file gets corrupt
//[0] 1 The application responds after a database file gets deleted

public class DBVerifier {
    
    private String dbUrl = "jdbc:sqlite:database.db";  // Database URL
    private String dbFilePath = "database.db";  // Path to the database file
    private String dbFileDir = null;

    private static DBVerifier instance;


    public DBVerifier() {
        this.dbUrl = "jdbc:sqlite:" + ConfigState.kvStoreDbPath;
        this.dbFilePath = ConfigState.kvStoreDbPath;
        this.dbFileDir = ConfigState.kvStoreDbPathDir;
    }

    public static synchronized DBVerifier getInstance() {
        if (instance == null) {
            instance = new DBVerifier();
        }
        return instance;
    }

    // 1. Check if the database file exists
    public boolean checkDatabaseExists() {
        File dbFile = new File(dbFilePath);
        return dbFile.exists();
    }

    // 2. Run integrity check to detect corruption
    public boolean runIntegrityCheck() {
        String integrityCheckSql = "PRAGMA integrity_check;";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {
            
            var result = stmt.executeQuery(integrityCheckSql);
            if (result.next() && "ok".equalsIgnoreCase(result.getString(1))) {
                System.out.println("Database integrity check passed.");
                return true;
            } else {
                System.err.println("Database integrity check failed: " + result.getString(1));
                return false;
            }
        } catch (SQLException e) {
            System.err.println("Error running integrity check: " + e.getMessage());
            return false;
        }
    }

    public void handleMissingDatabase() {
        if (!checkDatabaseExists()) {
            System.out.println("Database file is missing.");
            System.out.println("Recreating the database...");
            recreateDatabase();
        }
    }

    public void handleCorruption() {
        if (!runIntegrityCheck()) {
            System.err.println("Database is corrupt.");
            System.out.println("No backup available.");
            recreateDatabase();
        }
    }

    public void recreateDatabase() {
        StorageDataUtil.validateDirectory(dbFileDir);
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {
            // SQL to recreate the bitly table
            String createTableSQL = """
                CREATE TABLE IF NOT EXISTS bitly (
                    shorturl TEXT PRIMARY KEY,
                    longurl TEXT NOT NULL,
                    opTime TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
                );
            """;
            stmt.executeUpdate(createTableSQL);
            System.out.println("Database recreated successfully.");
        } catch (SQLException e) {
            System.err.println("Error recreating the database: " + e.getMessage());
        }
    }
}
