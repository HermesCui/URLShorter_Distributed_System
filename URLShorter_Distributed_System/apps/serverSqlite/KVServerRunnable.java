import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import libs.config.ConfigState;
import libs.db.DBVerifier;
import libs.syncdb.URLShortnerDB;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.Executors;
import libs.syncdb.*;

public class KVServerRunnable implements Runnable {

    private static final File WEB_ROOT = new File(".");
    private static final String DEFAULT_FILE = "index.html";
    private static final String FILE_NOT_FOUND = "404.html";
    private static final String REDIRECT_RECORDED = "redirect_recorded.html";
    private static final String REDIRECT = "redirect.html";
    private static final boolean VERBOSE = false;
    private static final Pattern PUT_PATTERN = Pattern.compile("short=(\\S+)&long=(\\S+)");

    private final URLShortnerDB database;
    private final DBVerifier dbVerifierRef = DBVerifier.getInstance(); 

    public KVServerRunnable() {
        dbVerifierRef.recreateDatabase();
        this.database = URLShortnerDB.getInstance();
    }

    @Override
    public void run() {
        try {
            int port = ConfigState.kvStorePublicFacingPort;
            int threadPoolSize = ConfigState.kvStoreWebWorkerThreadCount;
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new MyHandler());
            server.setExecutor(Executors.newFixedThreadPool(threadPoolSize));
            server.start();
            System.out.println("KVServerRunnable: HTTP Server started successfully on port " + port);
        } catch (IOException e) {
            System.err.println("KVServerRunnable: Failed to start HTTP Server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                String method = exchange.getRequestMethod();
                String query = exchange.getRequestURI().getQuery();
                String path = exchange.getRequestURI().getPath();

                if (VERBOSE) {
                    System.out.println("KVServerRunnable: Request method: " + method);
                    System.out.println("KVServerRunnable: Request path: " + path);
                    System.out.println("KVServerRunnable: Request query: " + query);
                }

                if ("PUT".equalsIgnoreCase(method)) {
                    handlePutRequest(exchange, query);
                } else if ("GET".equalsIgnoreCase(method)) {
                    handleGetRequest(exchange, path);
                } else {
                    exchange.sendResponseHeaders(405, -1); // Method Not Allowed
                }
            } catch (Exception e) {
                System.err.println("KVServerRunnable: Error handling request: " + e.getMessage());
                e.printStackTrace();
                try {
                    exchange.sendResponseHeaders(500, -1);
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            } finally {
                exchange.close(); // Ensure the exchange is closed
            }
        }

        private void handlePutRequest(HttpExchange exchange, String query) throws IOException {
            if (query == null) {
                sendResponse(exchange, 400, new File(WEB_ROOT, FILE_NOT_FOUND));
                return;
            }

            Matcher matcher = PUT_PATTERN.matcher(query);
            if (matcher.find()) {
                String shortResource = matcher.group(1);
                String longResource = matcher.group(2);
                database.save(shortResource, longResource);
                sendResponse(exchange, 200, new File(WEB_ROOT, REDIRECT_RECORDED));
            } else {
                System.out.println("KVServerRunnable: Invalid PUT request");
                sendResponse(exchange, 400, new File(WEB_ROOT, FILE_NOT_FOUND));
            }
        }

        private void handleGetRequest(HttpExchange exchange, String path) throws IOException {
            if (path == null || path.equals("/")) {
                sendResponse(exchange, 200, new File(WEB_ROOT, DEFAULT_FILE));
                return;
            }

            String shortResource = path.substring(1); // Remove the leading "/"
            String longResource = database.find(shortResource);

            if (longResource != null) {
                exchange.getResponseHeaders().set("Location", longResource);
                sendResponse(exchange, 307, new File(WEB_ROOT, REDIRECT));
            } else {
                sendResponse(exchange, 404, new File(WEB_ROOT, FILE_NOT_FOUND));
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, File file) throws IOException {
            if (!file.exists() || file.isDirectory()) {
                System.err.println("KVServerRunnable: File not found: " + file.getAbsolutePath());
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            byte[] response = readFileData(file);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, response.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }

        private byte[] readFileData(File file) throws IOException {
            try (FileInputStream fis = new FileInputStream(file)) {
                return fis.readAllBytes();
            }
        }
    }
}