import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import libs.msg.ClusterState;
import libs.config.ConfigState;
import libs.data.ClusterStateLoader;
import libs.com.HostResolver;
import libs.msg.ApplicationState;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.net.*;
import java.io.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.Executors;
import libs.cluster.ClusterGossip;


public class ManagementPlaneServer {

    static final ConfigState state = new ConfigState();
    static final File WEB_ROOT = new File(".");
    static final String DEFAULT_FILE = "index.html";

    public static void main(String[] args) throws IOException {
        // Create an HTTP server
        ConfigState.parseEnviromentVariables(args);
        ClusterStateLoader.loadClusterData();
        HttpServer server = HttpServer.create(new InetSocketAddress(ConfigState.monitorSystemPublicFacingPort), 0);
        System.out.println("Server started");

        // Set the handlers for specific endpoints
        server.createContext("/", new ServerMainHandler());
        server.createContext("/serverList", new ServerListHandler());

        // Start the server with a fixed thread pool
        server.setExecutor(Executors.newFixedThreadPool(5)); // Use a thread pool of 5
        server.start();
    }

    static class ServerMainHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, new File(WEB_ROOT, DEFAULT_FILE));
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, File file) throws IOException {
            byte[] response = readFileData(file);
            exchange.sendResponseHeaders(statusCode, response.length);
            OutputStream os = exchange.getResponseBody();
            os.write(response);
            os.close();
        }

        private byte[] readFileData(File file) throws IOException {
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();
            return data;
        }
    }

    // Handler for serving the list of servers
    static class ServerListHandler implements HttpHandler {
        // public static ClusterState createTestClusterOne(){
        //     ClusterState clusterOne = new ClusterState();
        //     ClusterGossip.setOrUpdateHostHeartBeat(clusterOne.kvStoreHostHeartBeat, "A", -1);
        //     ClusterGossip.setOrUpdateHostHeartBeat(clusterOne.kvStoreHostHeartBeat, "B", 4);
        //     ClusterGossip.setOrUpdateHostHeartBeat(clusterOne.kvStoreHostHeartBeat, "C", 5);
        //     ClusterGossip.setOrUpdateHostHeartBeat(clusterOne.kvStoreHostHeartBeat, "D", 432423);
        //     ClusterGossip.setOrUpdateHostHeartBeat(clusterOne.kvStoreHostHeartBeat, "E", 5);
        //     ClusterGossip.setOrUpdateHostHeartBeat(clusterOne.kvStoreHostHeartBeat, "A", 6);
        //     return clusterOne;
        // }

        // public static ClusterState createTestClusterOne() {
        //     ClusterState clusterOne = new ClusterState();

        //     // Set heartbeats for hosts
        //     clusterOne.kvStoreHostHeartBeat.put("A", -1);
        //     clusterOne.kvStoreHostHeartBeat.put("B", 4);
        //     clusterOne.kvStoreHostHeartBeat.put("C", 5);
        //     clusterOne.kvStoreHostHeartBeat.put("D", 432423);
        //     clusterOne.kvStoreHostHeartBeat.put("E", 5);

        //     // Mock ApplicationStates for each host
        //     clusterOne.updateApplicationState("A", createTestAppState("A", "192.168.1.1", "KVStore", true));
        //     clusterOne.updateApplicationState("B", createTestAppState("B", "192.168.1.2", "KVStore", true));
        //     clusterOne.updateApplicationState("C", createTestAppState("C", "192.168.1.3", "Monitor", true));
        //     clusterOne.updateApplicationState("D", createTestAppState("D", "192.168.1.4", "LoadBalancer", true));
        //     clusterOne.updateApplicationState("E", createTestAppState("E", "192.168.1.5", "KVStore", true));

        //     return clusterOne;
        // }

        // // Helper method to create ApplicationState for a host
        // public static ApplicationState createTestAppState(String hostName, String hostIp, String applet, boolean ready) {
        //     ApplicationState appState = new ApplicationState(hostName, hostIp);  // Use the constructor
        //     appState.setApplets(applet);
        //     appState.setReadyState(ready);
        //     return appState;
        // }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                // Get data from ClusterState instance
                ClusterState clusterState = ClusterState.getInstance();

                // Prepare the response string
                StringBuilder responseBuilder = new StringBuilder();

                // Iterate through the hostnames in the cluster and get ApplicationState for each
                for (String hostName : clusterState.kvStoreHostHeartBeat.keySet()) {
                    // Get the ApplicationState for the host
                    ApplicationState appState = clusterState.getApplicationState(hostName);

                    // Determine the status based on heartbeat
                    int heartbeat = clusterState.kvStoreHostHeartBeat.get(hostName);
                    String status = (heartbeat > 0 && heartbeat <= ConfigState.processHealthyCheckTimeOut) ? "Active" : "Inactive";

                    // Construct the response string: hostname, hostip, applet, state, status
                    responseBuilder.append(appState.toString())
                                .append(" ")
                                .append(status)
                                .append("\n");  // Separate each host's data by newline
                }

                // Convert StringBuilder to a string and send it as the response
                String response = responseBuilder.toString();
                exchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }

        public static String[] getApplicationStatesAsStrings(ApplicationState[] appStates) {
            String[] result = new String[appStates.length];
            for (int i = 0; i < appStates.length; i++) {
                result[i] = appStates[i].toString();
            }
            return result;
        }
    }
}
