package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import util.Constants;
import util.Logger;

/**
 * Multithreaded dictionary server that handles concurrent client connections.
 * Uses a custom thread pool to manage client connections efficiently.
 */
public class DictionaryServer {
    private final int port;
    private final ThreadPool threadPool;
    private final Dictionary dictionary;
    private ServerSocket serverSocket;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<ClientHandler> activeHandlers = new CopyOnWriteArrayList<>();

    /**
     * Creates a new DictionaryServer with the specified port and dictionary file.
     *
     * @param port the port to listen on
     * @param dictionaryFile the file to load dictionary data from
     * @throws IOException if there's an error initializing the server
     */
    public DictionaryServer(int port, String dictionaryFile) throws IOException {
        this.port = port;
        this.threadPool = new ThreadPool(Constants.MAX_POOL_SIZE);
        this.dictionary = new Dictionary(dictionaryFile);
    }

    /**
     * Starts the server and begins accepting client connections.
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running.set(true);

        Logger.info("Dictionary server started on port " + port);
        Logger.info("Loaded dictionary with " + dictionary.size() + " words");

        // Main server loop - accept and handle client connections
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket, dictionary);
                activeHandlers.add(handler);

                // Submit handler to thread pool
                threadPool.execute(() -> {
                    try {
                        handler.run();
                    } finally {
                        activeHandlers.remove(handler);
                    }
                });
            } catch (SocketException e) {
                // Server socket closed, check if we're still running
                if (running.get()) {
                    Logger.error("Error accepting client connection", e);
                }
            } catch (IOException e) {
                if (running.get()) {
                    Logger.error("Error accepting client connection", e);
                }
            }
        }
    }

    /**
     * Stops the server and releases resources.
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return; // Already stopped
        }

        Logger.info("Stopping dictionary server...");

        Logger.info("Closing " + activeHandlers.size() + " active client connections");
        for (ClientHandler handler : activeHandlers) {
            handler.close();
        }
        activeHandlers.clear();

        // Give connections time to terminate gracefully
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Shutdown thread pool
        Logger.info("Shutting down thread pool");
        threadPool.shutdown();

        // Close server socket
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                Logger.info("Closing server socket");
                serverSocket.close();
            }
        } catch (IOException e) {
            Logger.error("Error closing server socket", e);
        }

        Logger.info("Dictionary server stopped");
    }

    public Dictionary getDictionary() {
        return dictionary;
    }

    public int getClientCount() {
        return activeHandlers.size();
    }


    public static void main(String[] args) {
        try {
            Logger.init("dictionary-server.log");
            Logger.setLevel(Logger.Level.INFO);

            // Parse command-line arguments
            int port = Constants.DEFAULT_PORT;
            String dictionaryFile = Constants.DEFAULT_DICTIONARY_FILE;

            if (args.length >= 1) {
                try {
                    port = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    Logger.error("Invalid port number: " + args[0]);
                    System.out.println("Usage: java -jar DictionaryServer.jar <port> <dictionary-file>");
                    return;
                }
            }

            if (args.length >= 2) {
                dictionaryFile = args[1];
            }

            // Start the server
            final DictionaryServer server = new DictionaryServer(port, dictionaryFile);

            // Add shutdown hook for clean termination
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

            server.start();
        } catch (IOException e) {
            Logger.error("Failed to start server", e);
            System.out.println("Failed to start server: " + e.getMessage());
        } finally {
            Logger.close();
        }
    }
}