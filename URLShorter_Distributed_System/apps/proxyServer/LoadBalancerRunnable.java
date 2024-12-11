import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import libs.com.ConsistentHashing;
import libs.config.ConfigState;
import libs.msg.ClusterState;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class LoadBalancerRunnable implements Runnable {

    private final AtomicReference<ClusterState> clusterStateRef;
    private final ConsistentHashing consistentHashing;

    public LoadBalancerRunnable(AtomicReference<ClusterState> clusterStateRef) {
        this.clusterStateRef = clusterStateRef;
        this.consistentHashing = ConsistentHashing.getInstance();
    }

    @Override
    public void run() {
        try {
            int port = ConfigState.loadBalancerPublicFacingPort;
            int threadPoolSize = ConfigState.loadBalancerWebWorkerThreadCount;

            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new MyHandler());
            server.setExecutor(Executors.newFixedThreadPool(threadPoolSize));
            server.start();
            System.out.println("LoadBalancerRunnable: HTTP Server started successfully on port " + port);
        } catch (IOException e) {
            System.err.println("LoadBalancerRunnable: Failed to start HTTP Server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                String method = exchange.getRequestMethod();
                URI uri = exchange.getRequestURI();
                String path = uri.getPath();
                String query = uri.getQuery();

                System.out.println("LoadBalancerRunnable: Received request: " + method + " " + uri);

                // Extract the short URL from the request
                String shortUrl = null;
                boolean isValidRequest = false;

                if ("PUT".equalsIgnoreCase(method)) {
                    // For PUT requests, extract 'short' from query parameters
                    if (query != null) {
                        Map<String, String> queryParams = parseQueryParams(query);
                        if (queryParams.containsKey("short")) {
                            shortUrl = queryParams.get("short");
                            isValidRequest = true;
                        }
                    }
                } else if ("GET".equalsIgnoreCase(method)) {
                    // For GET requests, extract 'shortUrl' from the path
                    if (path != null && !path.equals("/")) {
                        shortUrl = path.substring(1); // Remove leading '/'
                        isValidRequest = true;
                    }
                }

                Iterator<String> kvStoreHosts;
                if (isValidRequest && shortUrl != null) {
                    // Get the iterator of nodes responsible for the key
                    kvStoreHosts = consistentHashing.getSuccessorNodes(shortUrl);
                } else {
                    // For invalid requests, get all nodes and iterate over them
                    Set<String> nodesSet = consistentHashing.getNodes();
                    if (nodesSet.isEmpty()) {
                        sendResponse(exchange, 503, "Service Unavailable: No KVStore nodes available");
                        return;
                    }
                    kvStoreHosts = nodesSet.iterator();
                }

                boolean success = false;
                Exception lastException = null;

                while (kvStoreHosts.hasNext()) {
                    String kvStoreHost = kvStoreHosts.next();
                    try {
                        forwardRequest(exchange, kvStoreHost);
                        success = true;
                        break;
                    } catch (IOException e) {
                        lastException = e;
                        System.err.println("LoadBalancerRunnable: Failed to forward to " + kvStoreHost + ": " + e.getMessage());
                        // Try the next node
                    }
                }

                if (!success) {
                    if (lastException != null) {
                        sendResponse(exchange, 502, "Bad Gateway: Unable to forward request");
                    } else {
                        sendResponse(exchange, 503, "Service Unavailable: No KVStore nodes available");
                    }
                }

            } catch (Exception e) {
                System.err.println("LoadBalancerRunnable: Error handling request: " + e.getMessage());
                e.printStackTrace();
                try {
                    exchange.sendResponseHeaders(500, -1);
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            } finally {
                exchange.close();
            }
        }

        private void forwardRequest(HttpExchange exchange, String kvStoreHost) throws IOException {
            String method = exchange.getRequestMethod();
            URI uri = exchange.getRequestURI();
            int kvStorePort = ConfigState.kvStorePublicFacingPort;
            String urlString = "http://" + kvStoreHost + ":" + kvStorePort + uri.toString();

            System.out.println("LoadBalancerRunnable: Forwarding request to " + urlString);

            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);

            // Copy request headers
            exchange.getRequestHeaders().forEach((key, values) -> {
                for (String value : values) {
                    connection.addRequestProperty(key, value);
                }
            });

            // Forward request body if present
            if ("PUT".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method)) {
                connection.setDoOutput(true);
                try (OutputStream os = connection.getOutputStream();
                     InputStream is = exchange.getRequestBody()) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                }
            }

            // Get response from KVStore
            int responseCode = connection.getResponseCode();
            InputStream is = responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
            if (is != null) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    responseBody.write(buffer, 0, bytesRead);
                }
            }

            // Copy response headers
            exchange.getResponseHeaders().clear();
            connection.getHeaderFields().forEach((key, values) -> {
                if (key != null && values != null) {
                    exchange.getResponseHeaders().put(key, values);
                }
            });

            // Send response back to client
            byte[] responseBytes = responseBody.toByteArray();
            exchange.sendResponseHeaders(responseCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
            System.out.println("LoadBalancerRunnable: Successfully forwarded request to " + urlString);
        }

        private Map<String, String> parseQueryParams(String query) throws UnsupportedEncodingException {
            Map<String, String> queryParams = new HashMap<>();
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx > 0 && idx < pair.length() - 1) {
                    String key = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                    String value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                    queryParams.put(key, value);
                }
            }
            return queryParams;
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
            byte[] responseBytes = message.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }
}
