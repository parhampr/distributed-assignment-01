package client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import protocol.Request;
import protocol.Response;
import util.Constants;
import util.Logger;

/**
 * Manages the connection to the dictionary server.
 */
public class ConnectionManager {
    private final String serverAddress;
    private final int serverPort;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean autoConnect = new AtomicBoolean(true);
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);

    private ConnectionListener listener;
    private Thread heartbeatThread;
    private Thread reconnectThread;

    /**
     * Creates a new ConnectionManager with the specified server address and port.
     *
     * @param serverAddress the server address
     * @param serverPort the server port
     */
    public ConnectionManager(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }


    public String getServerAddress() {
        return serverAddress;
    }

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
     * Sets the auto-connect flag.
     *
     * @param enable true to enable auto-connect, false to disable
     */
    public void setAutoConnect(boolean enable) {
        autoConnect.set(enable);

        // If we're enabling auto-connect and we're currently disconnected, attempt to connect
        if (enable && !connected.get() && !reconnecting.get()) {
            startReconnectThread();
        }
    }

    /**
     * Gets the current auto-connect status.
     *
     * @return true if auto-connect is enabled, false otherwise
     */
    public boolean isAutoConnectEnabled() {
        return autoConnect.get();
    }

    /**
     * Connects to the server.
     *
     * @return true if connected successfully, false otherwise
     */
    public boolean connect() {
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

            // Start heartbeat thread
            startHeartbeatThread();

            Logger.info("Connected to server: " + serverAddress + ":" + serverPort);
            return true;
        } catch (IOException e) {
            Logger.error("Failed to connect to server: " + e.getMessage());
            cleanupConnection();
            return false;
        }
    }

    /**
     * Starts a thread to send periodic heartbeats to the server.
     */
    private void startHeartbeatThread() {
        // Stop any existing heartbeat thread
        if (heartbeatThread != null && heartbeatThread.isAlive()) {
            heartbeatThread.interrupt();
        }

        heartbeatThread = new Thread(() -> {
            try {
                while (connected.get() && !Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(10000); // Send heartbeat every 10 seconds

                        if (!connected.get()) {
                            break;
                        }

                        // Check if socket is still valid
                        if (socket == null || socket.isClosed()) {
                            notifyDisconnection();
                            break;
                        }

                        // Send heartbeat request
                        Request pingRequest = new Request(Request.OperationType.HEARTBEAT, "_heartbeat_");

                        synchronized (this) {
                            if (out != null) {
                                try {
                                    socket.setSoTimeout(2000); // Short timeout for heartbeat
                                    out.writeObject(pingRequest);
                                    out.flush();
                                    out.reset();

                                    // Read response (we don't care about the result, just that it worked)
                                    in.readObject();

                                    // Reset timeout
                                    socket.setSoTimeout(Constants.CONNECTION_TIMEOUT);
                                } catch (Exception e) {
                                    Logger.debug("Heartbeat failed: " + e.getMessage());
                                    notifyDisconnection();
                                    break;
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        Logger.debug("Error in heartbeat thread: " + e.getMessage());
                        notifyDisconnection();
                        break;
                    }
                }
            } catch (Exception e) {
                Logger.error("Heartbeat thread error: " + e.getMessage());
            }
            Logger.debug("Heartbeat thread exiting");
        });

        heartbeatThread.setDaemon(true);
        heartbeatThread.setName("Heartbeat-Thread");
        heartbeatThread.start();
    }

    /**
     * Starts a thread to attempt reconnection.
     */
    private void startReconnectThread() {
        if (reconnecting.get()) {
            return; // Already reconnecting
        }

        reconnecting.set(true);

        // Stop any existing reconnect thread
        if (reconnectThread != null && reconnectThread.isAlive()) {
            reconnectThread.interrupt();
        }

        reconnectThread = new Thread(() -> {
            try {
                int attemptCount = 0;

                if (listener != null) {
                    listener.onReconnecting();
                }

                while (autoConnect.get() && !connected.get() && !Thread.currentThread().isInterrupted()) {
                    attemptCount++;
                    Logger.info("Reconnect attempt " + attemptCount);

                    if (connect()) {
                        Logger.info("Reconnection successful");
                        break;
                    }

                    // Notify after every few attempts
                    if (attemptCount % 3 == 0 && listener != null) {
                        listener.onReconnectFailed();
                    }

                    // Wait before next attempt with increasing delay
                    int delay = Math.min(3000 * Math.min(attemptCount, 10), 30000);

                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                if (!connected.get() && listener != null) {
                    listener.onReconnectFailed();
                }
            } finally {
                reconnecting.set(false);
            }
        });

        reconnectThread.setDaemon(true);
        reconnectThread.setName("Reconnect-Thread");
        reconnectThread.start();
    }

    /**
     * Notifies about disconnection and cleans up resources.
     */
    private void notifyDisconnection() {
        if (connected.compareAndSet(true, false)) {
            cleanupConnection();

            if (listener != null) {
                listener.onDisconnected();
            }

            // Try to reconnect if auto-connect is enabled
            if (autoConnect.get() && !reconnecting.get()) {
                startReconnectThread();
            }
        }
    }

    /**
     * Cleans up connection resources.
     */
    private synchronized void cleanupConnection() {
        try {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // Ignore errors during close
                }
                in = null;
            }

            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // Ignore errors during close
                }
                out = null;
            }

            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    // Ignore errors during close
                }
                socket = null;
            }
        } catch (Exception e) {
            Logger.debug("Error during connection cleanup: " + e.getMessage());
        }
    }

    /**
     * Disconnects from the server.
     */
    public synchronized void disconnect() {
        boolean wasConnected = connected.getAndSet(false);

        // Stop heartbeat thread
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
        }

        // Stop reconnect thread
        if (reconnectThread != null) {
            reconnectThread.interrupt();
        }

        cleanupConnection();

        if (wasConnected && listener != null) {
            listener.onDisconnected();
        }

        Logger.info("Disconnected from server");
    }

    /**
     * Sends a request to the server and waits for a response.
     *
     * @param request the request to send
     * @return the server's response, or null if there was an error
     */
    public synchronized Response sendRequest(Request request) {
        if (!connected.get()) {
            return null;
        }

        try {
            Logger.debug("Sending request: " + request);

            out.writeObject(request);
            out.flush();
            out.reset(); // Reset object cache

            Object obj = in.readObject();
            if (obj instanceof Response) {
                Logger.debug("Received response: " + obj);
                return (Response) obj;
            } else {
                Logger.error("Received invalid response type");
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
    }

    public boolean isConnected() {
        return connected.get();
    }

    /**
     * Shutdown the connection manager and release resources.
     */
    public void shutdown() {
        disconnect();
        Logger.info("Connection manager shut down");
    }

    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
        void onReconnecting();
        void onReconnectFailed();
    }
}