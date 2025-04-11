package server;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;

import protocol.Request;
import protocol.Response;
import util.Logger;

/**
 * Handles communication with a client.
 */
public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final Dictionary dictionary;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private volatile boolean running = true;

    /**
     * Creates a new ClientHandler for the specified client socket.
     *
     * @param clientSocket the client socket
     * @param dictionary the dictionary to use
     */
    public ClientHandler(Socket clientSocket, Dictionary dictionary) {
        this.clientSocket = clientSocket;
        this.dictionary = dictionary;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(clientSocket.getOutputStream());
            in = new ObjectInputStream(clientSocket.getInputStream());

            Logger.info("Client connected: " + clientSocket.getInetAddress());

            // Process client requests
            while (running && !Thread.currentThread().isInterrupted() && !clientSocket.isClosed()) {
                try {
                    // Read request from client
                    Object obj = in.readObject();
                    if (!(obj instanceof Request request)) {
                        continue;
                    }

                    Logger.debug("Received request: " + request);

                    // Process request and send response
                    Response response = processRequest(request);
                    out.writeObject(response);
                    out.flush();

                    Logger.debug("Sent response: " + response);
                } catch (ClassNotFoundException e) {
                    Logger.error("Invalid message received", e);
                } catch (EOFException e) {
                    // Client closed connection normally
                    Logger.debug("Client closed connection");
                    break;
                } catch (SocketException e) {
                    // Socket error (client disconnected abruptly)
                    Logger.debug("Socket error: " + e.getMessage());
                    break;
                } catch (IOException e) {
                    // Other IO error
                    Logger.error("IO error handling client", e);
                    break;
                }
            }
        } catch (IOException e) {
            if (running) {
                Logger.error("Error handling client", e);
            }
        } finally {
            close();
        }
    }
    /**
     * Processes a client request and generates a response.
     *
     * @param request the client request
     * @return the response to send to the client
     */
    private Response processRequest(Request request) {
        try {
            return switch (request.getOperation()) {
                case SEARCH -> processSearch(request);
                case ADD -> processAdd(request);
                case REMOVE -> processRemove(request);
                case ADD_MEANING -> processAddMeaning(request);
                case UPDATE_MEANING -> processUpdateMeaning(request);
                default -> new Response(Response.StatusCode.ERROR, request.getWord(),
                        "Unknown operation: " + request.getOperation());
            };
        } catch (Exception e) {
            Logger.error("Error processing request", e);
            return new Response(Response.StatusCode.ERROR, request.getWord(),
                    "Server error: " + e.getMessage());
        }
    }

    private Response processSearch(Request request) {
        String word = request.getWord();
        List<String> meanings = dictionary.search(word);

        if (meanings == null || meanings.isEmpty()) {
            return new Response(Response.StatusCode.WORD_NOT_FOUND, word,
                    "Word not found in dictionary");
        }

        return new Response(Response.StatusCode.SUCCESS, word,
                meanings.toArray(new String[0]));
    }

    private Response processAdd(Request request) throws IOException {
        String word = request.getWord();
        String[] meanings = request.getMeanings();

        if (meanings == null || meanings.length == 0) {
            return new Response(Response.StatusCode.ERROR, word,
                    "Word must have at least one meaning");
        }

        boolean added = dictionary.addWord(word, meanings);
        if (!added) {
            return new Response(Response.StatusCode.DUPLICATE_WORD, word,
                    "Word already exists in dictionary");
        }

        return new Response(Response.StatusCode.SUCCESS, word,
                "Word added successfully");
    }

    private Response processRemove(Request request) throws IOException {
        String word = request.getWord();
        boolean removed = dictionary.removeWord(word);

        if (!removed) {
            return new Response(Response.StatusCode.WORD_NOT_FOUND, word,
                    "Word not found in dictionary");
        }

        return new Response(Response.StatusCode.SUCCESS, word,
                "Word removed successfully");
    }

    private Response processAddMeaning(Request request) throws IOException {
        String word = request.getWord();
        String[] meanings = request.getMeanings();

        if (meanings == null || meanings.length == 0) {
            return new Response(Response.StatusCode.ERROR, word,
                    "No meaning provided");
        }

        boolean added = dictionary.addMeaning(word, meanings[0]);
        if (!added) {
            List<String> existingMeanings = dictionary.search(word);
            if (existingMeanings == null) {
                return new Response(Response.StatusCode.WORD_NOT_FOUND, word,
                        "Word not found in dictionary");
            } else {
                return new Response(Response.StatusCode.MEANING_EXISTS, word,
                        "Meaning already exists for this word");
            }
        }

        return new Response(Response.StatusCode.SUCCESS, word,
                "Meaning added successfully");
    }

    private Response processUpdateMeaning(Request request) throws IOException {
        String word = request.getWord();
        String oldMeaning = request.getOldMeaning();
        String[] meanings = request.getMeanings();

        if (oldMeaning == null || meanings == null || meanings.length == 0) {
            return new Response(Response.StatusCode.ERROR, word,
                    "Missing old or new meaning");
        }

        boolean updated = dictionary.updateMeaning(word, oldMeaning, meanings[0]);
        if (!updated) {
            List<String> existingMeanings = dictionary.search(word);
            if (existingMeanings == null) {
                return new Response(Response.StatusCode.WORD_NOT_FOUND, word,
                        "Word not found in dictionary");
            } else {
                return new Response(Response.StatusCode.MEANING_NOT_FOUND, word,
                        "Old meaning not found for this word");
            }
        }

        return new Response(Response.StatusCode.SUCCESS, word,
                "Meaning updated successfully");
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

                // Only log if we're not in the process of shutting down the server
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