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

    public void setConnectionListener(ConnectionListener listener) {
        this.listener = listener;
    }

    public void setAutoConnect(boolean enable) {
        autoConnect.set(enable);

        // Try to connect if auto-connect enabled and currently disconnected
        if (enable && !connected.get() && !reconnecting.get()) {
            startReconnectThread();
        }
    }

    public boolean isAutoConnectEnabled() {
        return autoConnect.get();
    }

    /**
     * Connects to the server and sets up communication streams.
     */
    public boolean connect() {
        if (connected.get()) {
            return true;
        }

        Logger.info("Connecting to server: " + serverAddress + ":" + serverPort);

        try {
            socket = new Socket(serverAddress, serverPort);
            socket.setSoTimeout(Constants.CONNECTION_TIMEOUT);

            // Create streams - output first, then input
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush(); // Flush header information

            in = new ObjectInputStream(socket.getInputStream());

            connected.set(true);

            if (listener != null) {
                listener.onConnected();
            }

            // Start heartbeat to keep connection alive
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
     * Sends periodic heartbeats to verify server connection.
     */
    private void startHeartbeatThread() {
        // Stop existing heartbeat thread if running
        if (heartbeatThread != null && heartbeatThread.isAlive()) {
            heartbeatThread.interrupt();
        }

        heartbeatThread = new Thread(() -> {
            try {
                while (connected.get() && !Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(10000); // 10-second interval

                        if (!connected.get()) {
                            break;
                        }

                        if (socket == null || socket.isClosed()) {
                            notifyDisconnection();
                            break;
                        }

                        // Send ping and wait for response
                        Request pingRequest = new Request(Request.OperationType.HEARTBEAT, "_heartbeat_");

                        synchronized (this) {
                            if (out != null) {
                                try {
                                    socket.setSoTimeout(1000); // Short timeout for heartbeat
                                    out.writeObject(pingRequest);
                                    out.flush();
                                    out.reset();

                                    // Read response
                                    in.readObject();
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
     * Attempts to reconnect to server with backoff strategy.
     */
    private void startReconnectThread() {
        if (reconnecting.get()) {
            return; // Already trying to reconnect
        }

        reconnecting.set(true);

        // Stop existing reconnect thread if running
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

                    // Increasing delay with a maximum cap
                    int delay = Math.min(3000 * Math.min(attemptCount, 10), Constants.MAX_DELAY_MS);

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
     */
    public synchronized Response sendRequest(Request request) {
        if (!connected.get()) {
            return null;
        }

        try {
            Logger.debug("Sending request: " + request);

            out.writeObject(request);
            out.flush();
            out.reset();

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
     * Shuts down the connection manager and releases resources.
     */
    public void shutdown() {
        disconnect();
        Logger.info("Connection manager shut down");
    }

    /**
     * Interface for connection state change notifications.
     */
    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
        void onReconnecting();
        void onReconnectFailed();
    }
}