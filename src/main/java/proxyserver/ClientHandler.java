package proxyserver;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Handles individual client connections and requests.
 */
public class ClientHandler implements Runnable {

    private static final int MAX_BYTES = 4096;
    private Socket clientSocket;
    private LRUCache cache;
    private Set<String> blockedHosts;

    public ClientHandler(Socket socket, LRUCache cache, Set<String> blockedHosts) {
        this.clientSocket = socket;
        this.cache = cache;
        this.blockedHosts = blockedHosts;
    }

    @Override
    public void run() {
        try {
            handleClientRequest();
        } finally {
            closeSocket(clientSocket);
        }
    }

    /**
     * Handles the client's request by determining the type and processing it accordingly.
     */
    private void handleClientRequest() {
        try {
            InputStream clientInput = clientSocket.getInputStream();

            byte[] buffer = new byte[MAX_BYTES];
            int bytesRead = clientInput.read(buffer);

            if (bytesRead == -1) {
                System.out.println("Client disconnected!");
                return;
            }

            String requestString = new String(buffer, 0, bytesRead, StandardCharsets.US_ASCII);
            System.out.println("Request String: " + requestString);

            if (requestString.startsWith("CONNECT")) {
                handleHTTPSRequest(requestString);
            } else {
                handleHTTPRequest(requestString);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles standard HTTP requests.
     *
     * @param requestString The HTTP request as a string.
     */
    private void handleHTTPRequest(String requestString) {
        Socket serverSocket = null;
        try {
            BufferedReader reader = new BufferedReader(new StringReader(requestString));

            // Read the request line
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }

            String[] tokens = requestLine.split(" ");
            if (tokens.length < 3) {
                return;
            }
            String method = tokens[0];
            String fullURL = tokens[1];
            String httpVersion = tokens[2];

            // Only handle GET requests
            if (!method.equalsIgnoreCase("GET")) {
                System.err.println("Unsupported HTTP method: " + method);
                return;
            }

            // Extract host from the URL
            URL url = new URL(fullURL);
            String host = url.getHost().toLowerCase();

            // Check if the host is blocked
            if (isBlocked(host)) {
                sendBlockedResponse(host);
                return;
            }

            // Use the full URL as the cache key
            String cacheKey = fullURL;

            // Check if the response is in the cache
            byte[] cachedResponse = cache.get(cacheKey);
            if (cachedResponse != null) {
                System.out.println("Cache hit for URL: " + fullURL);
                OutputStream clientOutput = clientSocket.getOutputStream();
                clientOutput.write(cachedResponse);
                clientOutput.flush();
                return;
            }

            System.out.println("Cache miss for URL: " + fullURL);

            // Parse the full URL to extract host, port, and path
            String path = url.getFile(); // Includes path and query string
            if (path.isEmpty()) {
                path = "/";
            }
            int port = (url.getPort() != -1) ? url.getPort() : 80;

            // Read headers from requestString
            List<String> headers = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (!line.toLowerCase().startsWith("proxy-connection")) {
                    headers.add(line);
                }
            }

            // Establish connection to the server
            serverSocket = new Socket(host, port);
            OutputStream serverOutput = serverSocket.getOutputStream();
            BufferedWriter serverWriter = new BufferedWriter(new OutputStreamWriter(serverOutput));

            // Modify the request line to use the relative path
            String proxyRequestLine = method + " " + path + " " + httpVersion + "\r\n";
            serverWriter.write(proxyRequestLine);

            // Forward headers to server
            for (String header : headers) {
                serverWriter.write(header + "\r\n");
            }
            serverWriter.write("\r\n");
            serverWriter.flush();

            // Read the response from the server
            InputStream serverInput = serverSocket.getInputStream();
            ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream();

            // Relay data and store it in the response buffer
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = serverInput.read(buffer)) != -1) {
                responseBuffer.write(buffer, 0, bytesRead);
            }

            byte[] responseBytes = responseBuffer.toByteArray();

            // Store the response in the cache
            cache.put(cacheKey, responseBytes);

            // Send the response back to the client
            OutputStream clientOutput = clientSocket.getOutputStream();
            clientOutput.write(responseBytes);
            clientOutput.flush();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeSocket(serverSocket);
        }
    }

    /**
     * Handles HTTPS requests by establishing a tunnel between the client and server.
     *
     * @param requestString The HTTPS CONNECT request as a string.
     */
    private void handleHTTPSRequest(String requestString) {
        Socket serverSocket = null;
        try {
            BufferedReader reader = new BufferedReader(new StringReader(requestString));

            // Read the CONNECT request line
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }

            String[] tokens = requestLine.split(" ");
            if (tokens.length < 2) {
                return;
            }
            String method = tokens[0];
            String hostPort = tokens[1];

            if (!method.equalsIgnoreCase("CONNECT")) {
                System.err.println("Invalid HTTPS request.");
                return;
            }

            String[] hostPortTokens = hostPort.split(":");
            String host = hostPortTokens[0].toLowerCase();
            int port = (hostPortTokens.length > 1) ? Integer.parseInt(hostPortTokens[1]) : 443;

            // Check if the host is blocked
            if (isBlocked(host)) {
                sendBlockedResponse(host);
                return;
            }

            // Discard remaining headers
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                // Optionally log or process headers
            }

            // Attempt to establish connection to the server
            try {
                serverSocket = new Socket(host, port);
            } catch (IOException e) {
                // Send error response to client
                sendErrorResponse("502 Bad Gateway", "Failed to connect to " + host + ":" + port);
                return;
            }

            // Send 200 Connection Established to client
            OutputStream clientOutput = clientSocket.getOutputStream();
            BufferedWriter clientWriter = new BufferedWriter(new OutputStreamWriter(clientOutput));
            clientWriter.write("HTTP/1.1 200 Connection Established\r\n");
            clientWriter.write("\r\n");
            clientWriter.flush();

            // Relay data between client and server
            relayData(clientSocket, serverSocket);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeSocket(serverSocket);
        }
    }

    /**
     * Relays data between the client and server sockets for HTTPS connections.
     *
     * @param clientSocket The client socket.
     * @param serverSocket The server socket.
     */
    private void relayData(Socket clientSocket, Socket serverSocket) {
        try {
            InputStream clientInput = clientSocket.getInputStream();
            OutputStream clientOutput = clientSocket.getOutputStream();
            InputStream serverInput = serverSocket.getInputStream();
            OutputStream serverOutput = serverSocket.getOutputStream();

            // Create threads to handle data relay
            Thread clientToServer = new Thread(() -> forwardData(clientInput, serverOutput));
            Thread serverToClient = new Thread(() -> forwardData(serverInput, clientOutput));

            clientToServer.start();
            serverToClient.start();

            // Wait for both threads to finish
            clientToServer.join();
            serverToClient.join();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Forwards data from an input stream to an output stream.
     *
     * @param input  The input stream.
     * @param output The output stream.
     */
    private void forwardData(InputStream input, OutputStream output) {
        byte[] buffer = new byte[8192];
        int bytesRead;
        try {
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
                output.flush();
            }
        } catch (IOException e) {
            // Connection may be closed; handle as needed
        }
    }

    /**
     * Checks if a host is blocked.
     *
     * @param host The hostname to check.
     * @return True if the host is blocked, false otherwise.
     */
    private boolean isBlocked(String host) {
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }
        return blockedHosts.contains(host);
    }

    /**
     * Sends a 403 Forbidden response to the client for blocked hosts.
     *
     * @param host The blocked host.
     */
    private void sendBlockedResponse(String host) {
        try {
            OutputStream clientOutput = clientSocket.getOutputStream();
            String response = "HTTP/1.1 403 Forbidden\r\n" +
                    "Content-Type: text/html\r\n" +
                    "Connection: close\r\n" +
                    "\r\n" +
                    "<html><body><h1>403 Forbidden</h1><p>Access to the host '" + host + "' is blocked.</p></body></html>";
            clientOutput.write(response.getBytes(StandardCharsets.UTF_8));
            clientOutput.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends an error response to the client.
     *
     * @param status  The HTTP status code and reason phrase.
     * @param message The error message to include in the response body.
     */
    private void sendErrorResponse(String status, String message) {
        try {
            OutputStream clientOutput = clientSocket.getOutputStream();
            String response = "HTTP/1.1 " + status + "\r\n" +
                    "Content-Type: text/html\r\n" +
                    "Connection: close\r\n" +
                    "\r\n" +
                    "<html><body><h1>" + status + "</h1><p>" + message + "</p></body></html>";
            clientOutput.write(response.getBytes(StandardCharsets.UTF_8));
            clientOutput.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Closes a socket if it's open.
     *
     * @param socket The socket to close.
     */
    private void closeSocket(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
