import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;

import libs.cluster.ClusterGossip;
import libs.com.*;
import libs.config.AppletType;
import libs.config.ConfigState;
import libs.msg.*;
import libs.data.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class TestLib {
	static char [] ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
		static Random rand;

		public static String randomString(int length){
			StringBuilder r = new StringBuilder();
			for(int i=0;i<length;i++){
				int index = rand.nextInt(ALPHANUMERIC.length);
				r.append(ALPHANUMERIC[index]);
			}
			return r.toString();
		}
		
	private static Connection connect(String url) {
		Connection conn = null;
		try {
			StorageDataUtil.validateDirectory(ConfigState.kvStoreDbPathDir);
			conn = DriverManager.getConnection(url);
			/**
			 * pragma locking_mode=EXCLUSIVE;
			 * pragma temp_store = memory;
			 * pragma mmap_size = 30000000000;
			 **/
			String sql = """
			pragma synchronous = normal;
			pragma journal_mode = WAL;
			""";
			Statement stmt  = conn.createStatement();
			stmt.executeUpdate(sql);

		} catch (SQLException e) {
			e.printStackTrace();
			System.out.println(e.getMessage());
        	}
		return conn;
	}
	
	public static void main(String[] args) {
		try {
			// Set the applet type for this application instance
			AppletType appletType = AppletType.KVSTORE;
			// Initialize the ConfigState with applet type
			ConfigState.parseEnviromentVariables(args, appletType);
			StorageDataUtil.validateDirectory(ConfigState.appletLogBasePath);
			StorageDataUtil.validateDirectory(ConfigState.kvStoreDbPathDir);
			long seed = 5000;
			rand = new Random(seed);
		} catch (UnknownHostException e) {
			System.err.println("URLShortner: Error occurred during environment variable parsing.");
			e.printStackTrace();
			return;
		}
		overloadDb();
		System.out.println("Done!");
	}

	public static String generateRandomHost(){
		Random random = new Random();
		int hostnum = random.nextInt(10);
		return "HOST" + hostnum;
	}
	public static void overloadDb(){
		Connection conn = connect("jdbc:sqlite:" + ConfigState.kvStoreDbPath);
		try {
			String insertSQL = "INSERT INTO bitly(shorturl,longurl,hostOrigin) VALUES(?,?,?) ON CONFLICT(shorturl) DO UPDATE SET longurl=?;";
			PreparedStatement ps = conn.prepareStatement(insertSQL);

			for(int i=0;i<5000;i++){
				String gh = generateRandomHost();
				String longURL = "http://"+randomString(100);
				String shortURL = randomString(20);

				System.out.println("Inserting: " + longURL + ", " + shortURL + ": " + gh);
				
				ps.setString(1, shortURL);
				ps.setString(2, longURL);
				ps.setString(3, gh);
				
				ps.execute();
			}
			
		} catch (SQLException e) {
			e.printStackTrace();
			System.out.println(e.getMessage());
		}
	}

}