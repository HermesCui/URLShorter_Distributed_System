package libs.syncdb;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import libs.com.ConsistentHashing;
import libs.config.ConfigState;
import libs.msg.ClusterSyncDBMessage;
import libs.msg.MessageState;

import java.io.*;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.Executors;

/**
 * This route exposes the set of utilities for peers to 
 * reach out and obtain information stored in the database.

 */
public class ClusterDBWriterHandler implements HttpHandler {

    private final URLShortnerDB database;


    public ClusterDBWriterHandler() {
        this.database = URLShortnerDB.getInstance();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1); 
            exchange.close();
            return;
        }

        try (InputStream is = exchange.getRequestBody(); ObjectInputStream ois = new ObjectInputStream(is)) {
            MessageState receivedMessage = (MessageState) ois.readObject();
            ClusterSyncDBMessage rowRequest = (ClusterSyncDBMessage) receivedMessage.payloadData;
            Boolean write_success = database.saveBatch(rowRequest.rowData);
            if(!write_success){
                exchange.sendResponseHeaders(400, -1); 
                return;
            }
            MessageState responseMsg = new MessageState(
                    MessageState.MsgCtx.CLUSTER_SYNC_WRITE_SUCCESS,
                    ConfigState.hostName, 
                    receivedMessage.srcHost,
                    null
            );
            sendResponse(exchange, 200, responseMsg);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            exchange.sendResponseHeaders(400, -1); 
        } finally {
            exchange.close();
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, MessageState msg) throws IOException {
        if (msg != null) {
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            byte[] responseBytes = serializeObject(msg);
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        } else {
            exchange.sendResponseHeaders(statusCode, -1);
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
