package client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.ConnectException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import protocol.Request;
import protocol.Response;
import util.Constants;
import util.Logger;

/**
 * Manages the connection to the dictionary server with improved reliability.
 */
public class ConnectionManager {
    private final String serverAddress;
    private final int serverPort;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private ConnectionListener listener;
    private ScheduledExecutorService heartbeatExecutor;

    // Added for thread safety
    private final Lock connectionLock = new ReentrantLock();
    private final AtomicBoolean isReconnecting = new AtomicBoolean(false);

    /**
     * Creates a new ConnectionManager with the specified server address and port.
     *
     * @param serverAddress the server address
     * @param serverPort the server port
     */
    public ConnectionManager(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Heartbeat-Thread");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Gets the server address.
     *
     * @return the server address
     */
    public String getServerAddress() {
        return serverAddress;
    }

    /**
     * Gets the server port.
     *
     * @return the server port
     */
    public int getServerPort() {
        return serverPort;
    }

    /**
     * Sets the connection listener.
     *
     * @param listener the listener to set
     */
    public void setConnectionListener(ConnectionListener listener) {
        this.listener = listener;
    }

    /**
     * Connects to the server.
     *
     * @return true if connected successfully, false otherwise
     */
    public boolean connect() {
        connectionLock.lock();
        try {
            if (connected.get()) {
                return true;
            }

            Logger.info("Connecting to server: " + serverAddress + ":" + serverPort);

            try {
                socket = new Socket(serverAddress, serverPort);
                socket.setSoTimeout(Constants.CONNECTION_TIMEOUT);

                // Important: Create output stream first, then input stream
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush(); // Flush header information

                in = new ObjectInputStream(socket.getInputStream());

                connected.set(true);

                if (listener != null) {
                    listener.onConnected();
                }

                Logger.info("Connected to server: " + serverAddress + ":" + serverPort);

                // Start heartbeat to detect server disconnection
                startHeartbeat();

                return true;
            } catch (ConnectException e) {
                Logger.error("Connection refused: " + e.getMessage());
                disconnect();
                if (listener != null) {
                    listener.onReconnectFailed();
                }
                return false;
            } catch (IOException e) {
                Logger.error("Failed to connect to server: " + e.getMessage());
                disconnect();
                if (listener != null) {
                    listener.onReconnectFailed();
                }
                return false;
            }
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Starts the heartbeat mechanism to detect server disconnections.
     */
    private void startHeartbeat() {
        if (heartbeatExecutor.isShutdown()) {
            heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Heartbeat-Thread");
                t.setDaemon(true);
                return t;
            });
        }

        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (connected.get()) {
                try {
                    // First check basic socket state
                    if (socket == null || socket.isClosed() || !socket.isConnected() ||
                            socket.isInputShutdown() || socket.isOutputShutdown()) {
                        Logger.debug("Socket state check failed in heartbeat");
                        notifyDisconnection();
                        return;
                    }

                    // Then perform an actual protocol request to verify the server is responding
                    // Just do a simple search for a unlikely word - lightweight but protocol-compliant
                    Request pingRequest = new Request(Request.OperationType.SEARCH, "_heartbeat_");
                    Response response = sendHeartbeatRequest(pingRequest);

                    if (response == null) {
                        Logger.debug("Heartbeat request failed - server not responding");
                        notifyDisconnection();
                    }
                } catch (Exception e) {
                    Logger.debug("Heartbeat detected disconnection: " + e.getMessage());
                    notifyDisconnection();
                }
            }
        }, 10, 10, TimeUnit.SECONDS); // Less frequent to reduce load
    }

    /**
     * Sends a heartbeat request to check if server is alive.
     * This is separate from regular sendRequest to avoid recursion and notification issues.
     */
    private Response sendHeartbeatRequest(Request request) {
        connectionLock.lock();
        try {
            if (!connected.get() || socket == null || out == null || in == null) {
                return null;
            }

            try {
                socket.setSoTimeout(2000); // Short timeout for heartbeat
                out.writeObject(request);
                out.flush();
                out.reset();

                Object obj = in.readObject();
                socket.setSoTimeout(Constants.CONNECTION_TIMEOUT); // Reset timeout

                if (obj instanceof Response) {
                    return (Response) obj;
                } else {
                    return null;
                }
            } catch (Exception e) {
                Logger.debug("Heartbeat request exception: " + e.getMessage());
                return null;
            }
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Notifies about disconnection and cleans up resources.
     */
    private void notifyDisconnection() {
        if (connected.compareAndSet(true, false)) {
            disconnect();

            if (listener != null) {
                listener.onDisconnected();
            }
        }
    }

    /**
     * Disconnects from the server.
     */
    public void disconnect() {
        connectionLock.lock();
        try {
            if (!connected.get() && socket == null) {
                return;
            }

            Logger.info("Disconnecting from server");

            try {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        Logger.debug("Error closing input stream: " + e.getMessage());
                    }
                    in = null;
                }

                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                        Logger.debug("Error closing output stream: " + e.getMessage());
                    }
                    out = null;
                }

                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        Logger.debug("Error closing socket: " + e.getMessage());
                    }
                    socket = null;
                }
            } finally {
                connected.set(false);

                if (listener != null) {
                    listener.onDisconnected();
                }

                Logger.info("Disconnected from server");
            }
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Sends a request to the server and waits for a response.
     *
     * @param request the request to send
     * @return the server's response, or null if there was an error
     */
    public Response sendRequest(Request request) {
        if (!connected.get()) {
            if (!reconnect()) {
                return null;
            }
        }

        connectionLock.lock();
        try {
            if (!connected.get()) {
                return null;
            }

            try {
                // Log request (for debugging)
                Logger.debug("Sending request: " + request.toString());

                socket.setSoTimeout(Constants.CONNECTION_TIMEOUT);
                out.writeObject(request);
                out.flush();
                out.reset(); // Important: Reset object cache to avoid stale data

                Object obj = in.readObject();
                if (obj instanceof Response response) {
                    // Log response (for debugging)
                    Logger.debug("Received response: " + response.toString());
                    return response;
                } else {
                    Logger.error("Received invalid response type: " +
                            (obj != null ? obj.getClass().getName() : "null"));
                    return null;
                }
            } catch (SocketException | SocketTimeoutException e) {
                Logger.error("Socket error during request: " + e.getMessage());
                notifyDisconnection();
                return null;
            } catch (IOException | ClassNotFoundException e) {
                Logger.error("Error sending request: " + e.getMessage());
                notifyDisconnection();
                return null;
            }
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Attempts to reconnect to the server with improved retry logic.
     *
     * @return true if reconnected successfully, false otherwise
     */
    private boolean reconnect() {
        // Avoid multiple threads trying to reconnect simultaneously
        if (!isReconnecting.compareAndSet(false, true)) {
            return false;
        }

        try {
            if (listener != null) {
                listener.onReconnecting();
            }

            Logger.info("Attempting to reconnect to server");

            // Clean up any existing connection resources
            disconnect();

            for (int attempt = 1; attempt <= Constants.MAX_RECONNECT_ATTEMPTS; attempt++) {
                Logger.info("Reconnect attempt " + attempt + " of " + Constants.MAX_RECONNECT_ATTEMPTS);

                if (connect()) {
                    Logger.info("Reconnection successful");
                    return true;
                }

                // If this is not the last attempt, wait before retrying
                if (attempt < Constants.MAX_RECONNECT_ATTEMPTS) {
                    try {
                        Thread.sleep(Constants.RECONNECT_DELAY);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }

            Logger.error("Failed to reconnect after " + Constants.MAX_RECONNECT_ATTEMPTS + " attempts");

            if (listener != null) {
                listener.onReconnectFailed();
            }

            return false;
        } finally {
            isReconnecting.set(false);
        }
    }

    /**
     * Checks if the connection is established.
     *
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * Shutdown the connection manager and release resources.
     */
    public void shutdown() {
        disconnect();

        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor = null;
        }

        Logger.info("Connection manager shut down");
    }

    /**
     * Interface for connection status listeners.
     */
    public interface ConnectionListener {
        /**
         * Called when a connection is established.
         */
        void onConnected();

        /**
         * Called when the connection is lost.
         */
        void onDisconnected();

        /**
         * Called when attempting to reconnect.
         */
        void onReconnecting();

        /**
         * Called when reconnection fails.
         */
        void onReconnectFailed();
    }
}