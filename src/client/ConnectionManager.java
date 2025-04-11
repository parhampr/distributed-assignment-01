package client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import protocol.Request;
import protocol.Response;
import util.Constants;
import util.Logger;

/**
 * Manages the connection to the dictionary server.
 */
public class ConnectionManager {
    private String serverAddress;
    private int serverPort;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private ConnectionListener listener;

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
        if (connected.get()) {
            return true;
        }

        try {
            socket = new Socket(serverAddress, serverPort);
            socket.setSoTimeout(Constants.CONNECTION_TIMEOUT);

            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            connected.set(true);

            if (listener != null) {
                listener.onConnected();
            }

            Logger.info("Connected to server: " + serverAddress + ":" + serverPort);
            return true;
        } catch (IOException e) {
            Logger.error("Failed to connect to server", e);
            disconnect();
            return false;
        }
    }

    /**
     * Disconnects from the server.
     */
    public void disconnect() {
        if (!connected.get()) {
            return;
        }

        try {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            Logger.error("Error closing connection", e);
        } finally {
            connected.set(false);

            if (listener != null) {
                listener.onDisconnected();
            }

            Logger.info("Disconnected from server");
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

        try {
            out.writeObject(request);
            out.flush();

            Object obj = in.readObject();
            if (obj instanceof Response) {
                return (Response) obj;
            } else {
                Logger.error("Received invalid response type: " + obj.getClass().getName());
                return null;
            }
        } catch (IOException | ClassNotFoundException e) {
            Logger.error("Error sending request", e);
            disconnect();
            return null;
        }
    }

    /**
     * Attempts to reconnect to the server.
     *
     * @return true if reconnected successfully, false otherwise
     */
    private boolean reconnect() {
        if (listener != null) {
            listener.onReconnecting();
        }

        for (int attempt = 1; attempt <= Constants.MAX_RECONNECT_ATTEMPTS; attempt++) {
            Logger.info("Reconnect attempt " + attempt + " of " + Constants.MAX_RECONNECT_ATTEMPTS);

            if (connect()) {
                return true;
            }

            try {
                Thread.sleep(Constants.RECONNECT_DELAY);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        if (listener != null) {
            listener.onReconnectFailed();
        }

        return false;
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