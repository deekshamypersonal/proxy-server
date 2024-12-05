package proxyserver;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ProxyServer {

    // Configuration constants
    private static final int DEFAULT_PORT = 8080;
    private static final int MAX_CLIENTS = 400;
    private static final int CACHE_CAPACITY = 100; // Adjust as needed

    // LRU Cache instance
    private static final LRUCache cache = new LRUCache(CACHE_CAPACITY);

    private int portNumber;
    private ServerSocket proxyServerSocket;
    private ExecutorService threadPool;

    private static final Set<String> blockedHosts = ConcurrentHashMap.newKeySet();
    private volatile boolean isRunning = true;

    /**
     * Constructs a ProxyServer with the specified port number.
     */
    public ProxyServer(int portNumber) {
        this.portNumber = portNumber;
        this.threadPool = Executors.newFixedThreadPool(MAX_CLIENTS);
    }

    /**
     * The main method to start the proxy server.
     */
    public static void main(String[] args) {
        int portNumber = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                portNumber = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number provided. Using default port " + DEFAULT_PORT);
            }
        }
        ProxyServer proxyServer = new ProxyServer(portNumber);
        proxyServer.start();
    }

    /**
     * Starts the proxy server.
     */
    public void start() {
        System.out.println("Starting Proxy Server on port: " + portNumber);

        // Load blocked hosts from file
        loadBlockedHosts();

        // Start the administrative thread
        startAdminThread();

        try {
            proxyServerSocket = new ServerSocket(portNumber);
            System.out.println("Proxy server is listening on port: " + portNumber);

            while (isRunning) {
                try {
                    Socket clientSocket = proxyServerSocket.accept();
                    System.out.println("Accepted connection from client: " + clientSocket.getRemoteSocketAddress());
                    threadPool.execute(new ClientHandler(clientSocket, cache, blockedHosts));
                } catch (IOException e) {
                    if (isRunning) {
                        e.printStackTrace();
                    } else {
                        // Server socket closed during shutdown
                        break;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }

    /**
     * Shuts down the proxy server gracefully.
     */
    private void shutdown() {
        // Properly shut down the thread pool
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(60, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        // Close the server socket
        if (proxyServerSocket != null && !proxyServerSocket.isClosed()) {
            try {
                proxyServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Proxy server has been shut down.");
    }

    /**
     * Loads the list of blocked hosts from a file.
     */
    private void loadBlockedHosts() {
        URL resource = ProxyServer.class.getClassLoader().getResource("blocked_urls.txt");
        if (resource != null) {
            try (InputStream inputStream = resource.openStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String host = extractHost(line.trim());
                    if (host != null) {
                        blockedHosts.add(host);
                    }
                }
            } catch (IOException e) {
                System.err.println("Could not read blocked URLs file.");
                e.printStackTrace();
            }
        } else {
            System.err.println("blocked_urls.txt not found.");
        }
    }

    /**
     * Starts the administrative thread to handle user input.
     */
    private void startAdminThread() {
        new Thread(() -> {
            try (BufferedReader adminReader = new BufferedReader(new InputStreamReader(System.in))) {
                String input;
                while (isRunning) {
                    System.out.println("\nPROXY SERVER MENU");
                    System.out.print("Enter new site to block (hostname or URL) (or type 'exit' to quit): ");
                    input = adminReader.readLine();
                    if (input == null) continue;
                    if (input.equalsIgnoreCase("exit")) {
                        System.out.println("Shutting down proxy server...");
                        isRunning = false;

                        // Close the server socket to stop accepting new connections
                        try {
                            proxyServerSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        // Initiate shutdown of the thread pool
                        threadPool.shutdown();
                        try {
                            if (!threadPool.awaitTermination(60, TimeUnit.SECONDS)) {
                                threadPool.shutdownNow();
                            }
                        } catch (InterruptedException e) {
                            threadPool.shutdownNow();
                            Thread.currentThread().interrupt();
                        }
                        System.out.println("Proxy server has been shut down.");
                        break; // Exit the admin thread loop
                    } else if (input.trim().isEmpty()) {
                        System.out.println("No input entered.");
                    } else {
                        String host = extractHost(input.trim());
                        if (host != null) {
                            blockedHosts.add(host);
                            System.out.println("Blocked Host: " + host);
                        } else {
                            System.out.println("Invalid hostname or URL.");
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Extracts the host from a given input string, which can be a URL or hostname.
     */
    private static String extractHost(String input) {
        try {
            String host;
            if (!input.contains("://")) {
                host = input.toLowerCase();
            } else {
                URL url = new URL(input);
                host = url.getHost().toLowerCase();
            }
            // Remove "www." prefix if present
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            return host;
        } catch (Exception e) {
            // Invalid URL
            return null;
        }
    }
}
