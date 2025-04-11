package client;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import util.Constants;
import util.Logger;

/**
 * Client application for the dictionary server.
 */
public class DictionaryClient {
    private static ConnectionManager connectionManager;

    /**
     * Main method to start the client application.
     *
     * @param args command-line arguments (server-address, server-port)
     */
    public static void main(String[] args) {
        try {
            // Set system look and feel
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // Continue with default look and feel
            }

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

            // Create connection manager
            final String finalServerAddress = serverAddress;
            final int finalServerPort = serverPort;
            connectionManager = new ConnectionManager(finalServerAddress, finalServerPort);

            // Auto-connect on startup
            boolean autoConnect = args.length <= 0; // Auto-connect if no args provided

            SwingUtilities.invokeLater(() -> {
                ClientGUI gui = new ClientGUI(connectionManager);
                gui.setVisible(true);

                // Add shutdown hook for proper cleanup
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    if (connectionManager != null) {
                        connectionManager.shutdown();
                    }
                    Logger.close();
                }));

                // Also add window listener to handle manual closing
                gui.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        if (connectionManager != null) {
                            connectionManager.shutdown();
                        }
                        Logger.close();
                    }
                });

                Logger.info("Dictionary client started");
                Logger.info("Server address: " + finalServerAddress);
                Logger.info("Server port: " + finalServerPort);

                // Auto-connect if specified
                if (autoConnect) {
                    new Thread(() -> connectionManager.connect()).start();
                }
            });
        } catch (IOException e) {
            Logger.error("Failed to start client", e);
            System.out.println("Failed to start client: " + e.getMessage());
        }
    }
}