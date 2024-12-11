package libs.com;

import libs.msg.MessageState;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public class SystemMessageSender {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    public static MessageState issueRequest(String hostName, int port, MessageState data,
                                            String routerKey, String method) {
        String httpRequestUrl = "http://" + hostName + ":" + port + "/" + routerKey;
        try {
            byte[] requestBody = serializeObject(data);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(httpRequestUrl))
                    .timeout(Duration.ofSeconds(30))
                    .method(method, HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .header("Content-Type", "application/octet-stream")
                    .build();

            HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                return deserializeObject(response.body());
            } else {
                System.err.println("Request failed with status code: " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Error during request to " + httpRequestUrl);
            e.printStackTrace();
            //Thread.currentThread().interrupt();
        }
        return null;
    }

    private static byte[] serializeObject(Object obj) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(obj);
            return bos.toByteArray();
        }
    }

    private static MessageState deserializeObject(byte[] data) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ObjectInputStream in = new ObjectInputStream(bis)) {
            return (MessageState) in.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("Class not found during deserialization", e);
        }
    }
}