/**
 * NAME: KAMAL KUMAR KHATRI
 * STUDENT_ID: 1534816
 */
package server;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.UUID;

import protocol.Request;
import protocol.Response;
import util.Logger;

/**
 * Handles communication with a connected client.
 * Each client gets its own handler running in a separate thread.
 */
public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final Dictionary dictionary;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private volatile boolean running = true;

    /**
     * Creates a handler for a client connection.
     */
    public ClientHandler(Socket clientSocket, Dictionary dictionary) {
        this.clientSocket = clientSocket;
        this.dictionary = dictionary;
    }

    @Override
    public void run() {
        try {
            // Initialize streams - output, then input
            out = new ObjectOutputStream(clientSocket.getOutputStream());
            in = new ObjectInputStream(clientSocket.getInputStream());

            String connectionId = UUID.randomUUID().toString();
            String clientAddress = clientSocket.getInetAddress().getHostAddress();
            int clientPort = clientSocket.getPort();

            Logger.info(String.format("Client connected: %s:%d (ID=%s)", clientAddress, clientPort, connectionId));

            while (running && !Thread.currentThread().isInterrupted() && !clientSocket.isClosed()) {
                try {
                    if (!(in.readObject() instanceof Request request)) {
                        continue;
                    }

                    // Log detailed info for non-heartbeat request
                    if (request.getOperation() == Request.OperationType.HEARTBEAT) {
                        Logger.debug(String.format("Heartbeat from %s:%d (ID=%s)", clientAddress, clientPort, connectionId));
                    } else {
                        Logger.info(String.format("Request from %s:%d - %s - %s", clientAddress, clientPort, request.getOperation(), request));
                    }

                    Response response = processRequest(request);
                    out.writeObject(response);
                    out.flush();

                    if (request.getOperation() != Request.OperationType.HEARTBEAT) {
                        Logger.info(String.format("Response to %s:%d - %s - %s", clientAddress, clientPort, response.getStatus(), response));
                    }

                } catch (ClassNotFoundException e) {
                    Logger.error(String.format("Invalid message: address=%s, connectionId=%s, error=%s", clientAddress, connectionId, e.getMessage()), e);
                } catch (EOFException e) {
                    Logger.info(String.format("Connection closed by client: address=%s, connectionId=%s", clientAddress, connectionId));
                    break;
                } catch (SocketException e) {
                    Logger.debug(String.format("Socket error: address=%s, connectionId=%s, error=%s", clientAddress, connectionId, e.getMessage()));
                    break;
                } catch (IOException e) {
                    Logger.error(String.format("IO error: address=%s, connectionId=%s, error=%s", clientAddress, connectionId, e.getMessage()), e);
                    break;
                }
            }
        } catch (IOException e) {
            if (running) {
                Logger.error(String.format("Client handler error: socket=%s, error=%s", clientSocket, e.getMessage()), e);
            }
        } finally {
            close();
        }
    }

    /**
     * Processes client requests and generates appropriate responses.
     */
    private Response processRequest(Request request) {
        try {
            return switch (request.getOperation()) {
                case SEARCH -> processSearch(request);
                case ADD -> processAdd(request);
                case REMOVE -> processRemove(request);
                case ADD_MEANING -> processAddMeaning(request);
                case UPDATE_MEANING -> processUpdateMeaning(request);
                case HEARTBEAT -> new Response(Response.StatusCode.SUCCESS, "_heartbeat_", "Server alive");
                default ->
                        new Response(Response.StatusCode.ERROR, request.getWord(), "Unknown operation: " + request.getOperation());
            };
        } catch (Exception e) {
            Logger.error("Error processing request", e);
            return new Response(Response.StatusCode.ERROR, request.getWord(), "Server error: " + e.getMessage());
        }
    }

    private Response processSearch(Request request) {
        String word = request.getWord();

        List<String> meanings = dictionary.search(word);
        if (meanings == null || meanings.isEmpty()) {
            return new Response(Response.StatusCode.WORD_NOT_FOUND, word, "Word not found in dictionary");
        }

        return new Response(Response.StatusCode.SUCCESS, word, meanings.toArray(new String[0]));
    }

    private Response processAdd(Request request) throws IOException {
        String word = request.getWord();
        String[] meanings = request.getMeanings();

        if (meanings == null || meanings.length == 0) {
            return new Response(Response.StatusCode.ERROR, word, "Word must have at least one meaning");
        }

        boolean added = dictionary.addWord(word, meanings);
        if (!added) {
            return new Response(Response.StatusCode.DUPLICATE_WORD, word, "Word already exists in dictionary");
        }

        return new Response(Response.StatusCode.SUCCESS, word, "Word added successfully");
    }

    private Response processRemove(Request request) throws IOException {
        String word = request.getWord();
        boolean removed = dictionary.removeWord(word);

        if (!removed) {
            return new Response(Response.StatusCode.WORD_NOT_FOUND, word, "Word not found in dictionary");
        }

        return new Response(Response.StatusCode.SUCCESS, word, "Word removed successfully");
    }

    private Response processAddMeaning(Request request) throws IOException {
        String word = request.getWord();
        String[] meanings = request.getMeanings();

        if (meanings == null || meanings.length == 0) {
            return new Response(Response.StatusCode.ERROR, word, "No meaning provided");
        }

        boolean added = dictionary.addMeaning(word, meanings[0]);
        if (!added) {
            List<String> existingMeanings = dictionary.search(word);
            if (existingMeanings == null) {
                return new Response(Response.StatusCode.WORD_NOT_FOUND, word, "Word not found in dictionary");
            } else {
                return new Response(Response.StatusCode.MEANING_EXISTS, word, "Meaning already exists for this word");
            }
        }

        return new Response(Response.StatusCode.SUCCESS, word, "Meaning added successfully");
    }

    private Response processUpdateMeaning(Request request) throws IOException {
        String word = request.getWord();
        String oldMeaning = request.getOldMeaning();
        String[] meanings = request.getMeanings();

        if (oldMeaning == null || meanings == null || meanings.length == 0) {
            return new Response(Response.StatusCode.ERROR, word, "Missing old or new meaning");
        }

        boolean updated = dictionary.updateMeaning(word, oldMeaning, meanings[0]);
        if (!updated) {
            List<String> existingMeanings = dictionary.search(word);
            if (existingMeanings == null) {
                return new Response(Response.StatusCode.WORD_NOT_FOUND, word, "Word not found in dictionary");
            } else {
                return new Response(Response.StatusCode.MEANING_NOT_FOUND, word, "Old meaning not found for this word");
            }
        }

        return new Response(Response.StatusCode.SUCCESS, word, "Meaning updated successfully");
    }

    /**
     * Closes the client connection and releases resources.
     */
    public void close() {
        running = false;

        try {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // Ignore during shutdown
                }
                in = null;
            }

            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // Ignore during shutdown
                }
                out = null;
            }

            if (clientSocket != null && !clientSocket.isClosed()) {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    // Ignore during shutdown
                }

                // Only log if not already shutting down
                if (running) {
                    Logger.info("Client disconnected: " + clientSocket.getInetAddress());
                }
            }
        } catch (Exception e) {
            // Catch any exceptions during close to avoid disrupting shutdown
            Logger.debug("Error during client handler close: " + e.getMessage());
        }
    }
}