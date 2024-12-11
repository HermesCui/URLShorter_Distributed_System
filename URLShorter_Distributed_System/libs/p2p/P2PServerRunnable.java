package libs.p2p;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import libs.cluster.ClusterGossip;
import libs.config.AppletType;
import libs.config.ConfigState;
import libs.msg.ClusterState;
import libs.msg.MessageState;
import libs.syncdb.ClusterDBReadHandler;
import libs.syncdb.ClusterDBWriterHandler;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class P2PServerRunnable implements Runnable {

    private final AtomicReference<ClusterState> clusterStateRef;
    private final int port;
    private final int workerThreadCount;
    private final AppletType appletType;



    public P2PServerRunnable(int port, int workerThreadCount, AtomicReference<ClusterState> clusterStateRef, AppletType appletType) {
        this.port = port;
        this.workerThreadCount = workerThreadCount;
        this.clusterStateRef = clusterStateRef;
        this.appletType = appletType;
    }

    @Override
    public void run() {
        System.out.println("[P2PServerRunnable] Starting bootstrap procedures.");
        bootstrap();
    }

    public void bootstrap() {
        entry();
    }

    public void entry() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new DefaultRouteHandler());
            server.createContext("/gossip", new GossipClusterState());

            if(ConfigState.appletType == AppletType.KVSTORE){
                server.createContext("/db_write", new ClusterDBWriterHandler());
                server.createContext("/db_read", new ClusterDBReadHandler());
            }


            server.setExecutor(Executors.newFixedThreadPool(this.workerThreadCount));  // Worker threads for handling requests
            server.start();
            System.out.println("P2P HTTP Server started successfully on port " + port);
        } catch (IOException e) {
            System.err.println("Failed to start HTTP Server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    class DefaultRouteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                String query = exchange.getRequestURI().getQuery();
                String path = exchange.getRequestURI().getPath();

                String response = "Hello from " + appletType + " applet!";
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    class GossipClusterState implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();

            if ("PUT".equalsIgnoreCase(method)) {
                handlePutRequest(exchange);
            } else if ("GET".equalsIgnoreCase(method)) {
                handleGetRequest(exchange);
            } else {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            }
        }

        private void handlePutRequest(HttpExchange exchange) throws IOException {
            try (InputStream requestBody = exchange.getRequestBody();
                 ObjectInputStream objectInputStream = new ObjectInputStream(requestBody)) {

                MessageState receivedMessage = (MessageState) objectInputStream.readObject();

                if (receivedMessage.payloadData instanceof ClusterState) {
                    ClusterState peerClusterState = (ClusterState) receivedMessage.payloadData;

                    // Merge the received ClusterState with the local ClusterState
                    ClusterGossip.mergeClusterGossip(clusterStateRef, peerClusterState);
                    
                    // Insert to the applet fields if its a new host.
                    // Todo: inspect application health before updating heartbeat. 
                    // Special: Technically we should have a two way response but assume one way..
                    ClusterGossip.updateHeartbeat(clusterStateRef, receivedMessage.srcHost, peerClusterState.currentHostAppletType);


                    // Prepare response with the updated local ClusterState
                    String destHost = exchange.getRemoteAddress().getAddress().getHostAddress();
                    String srcHost = ConfigState.hostName;
                    MessageState responseMsg = new MessageState(
                            MessageState.MsgCtx.CLUSTER_GOSSIP_RESPONSE,
                            srcHost,
                            destHost,
                            clusterStateRef.get()
                    );
                    
                    sendResponse(exchange, 200, responseMsg);
                } else {
                    System.err.println("Invalid payload data type in received message");
                    exchange.sendResponseHeaders(400, -1);
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                exchange.sendResponseHeaders(400, -1);
            } finally {
                exchange.close(); 
            }
        }

        private void handleGetRequest(HttpExchange exchange) throws IOException {
            String destHost = exchange.getRemoteAddress().getAddress().getHostAddress();
            String srcHost = ConfigState.hostName;
            MessageState responseMsg = new MessageState(
                    MessageState.MsgCtx.CLUSTER_GOSSIP_RESPONSE,
                    srcHost,
                    destHost,
                    clusterStateRef.get()
            );
            sendResponse(exchange, 200, responseMsg);
        }

        private void sendResponse(HttpExchange exchange, int statusCode, MessageState msg) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            byte[] responseBytes = serializeObject(msg);
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(responseBytes);
            } catch (IOException e) {
                e.printStackTrace();
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.close();
            }
        }

        private byte[] serializeObject(Object obj) throws IOException {
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                 ObjectOutputStream out = new ObjectOutputStream(bos)) {
                out.writeObject(obj);
                return bos.toByteArray();
            }
        }
    }
}
