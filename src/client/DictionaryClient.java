package client;

import java.io.IOException;
import javax.swing.SwingUtilities;
import util.Constants;
import util.Logger;

/**
 * Client application for the dictionary server.
 */
public class DictionaryClient {
    /**
     * Main method to start the client application.
     *
     * @param args command-line arguments (server-address, server-port)
     */
    public static void main(String[] args) {
        try {
            // Setup logging
            Logger.init("dictionary-client.log");
            Logger.setLevel(Logger.Level.INFO);

            // Parse command-line arguments
            String serverAddress = Constants.DEFAULT_SERVER_ADDRESS;
            int serverPort = Constants.DEFAULT_PORT;

            if (args.length >= 1) {
                serverAddress = args[0];
            }

            if (args.length >= 2) {
                try {
                    serverPort = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    Logger.error("Invalid port number: " + args[1]);
                    System.out.println("Usage: java -jar DictionaryClient.jar <server-address> <server-port>");
                    return;
                }
            }

            // Start the client GUI
            final String finalServerAddress = serverAddress;
            final int finalServerPort = serverPort;

            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    ClientGUI gui = new ClientGUI(finalServerAddress, finalServerPort);
                    gui.setVisible(true);

                    Logger.info("Dictionary client started");
                    Logger.info("Server address: " + finalServerAddress);
                    Logger.info("Server port: " + finalServerPort);
                }
            });
        } catch (IOException e) {
            Logger.error("Failed to start client", e);
            System.out.println("Failed to start client: " + e.getMessage());
        }
    }
}