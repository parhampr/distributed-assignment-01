package server;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import util.Constants;
import util.Logger;

/**
 * Graphical user interface for monitoring the dictionary server.
 * This is an optional component for the creativity element.
 */
public class ServerGUI extends JFrame {
    private static final long serialVersionUID = 1L;

    private DictionaryServer server;
    private JTextArea logArea;
    private JButton startButton;
    private JButton stopButton;
    private JLabel statusLabel;
    private JLabel wordCountLabel;
    private JLabel clientCountLabel;

    private int port;
    private String dictionaryFile;
    private boolean serverRunning = false;
    private int clientCount = 0;

    /**
     * Creates a new ServerGUI with the specified port and dictionary file.
     *
     * @param port the port to listen on
     * @param dictionaryFile the file to load dictionary data from
     */
    public ServerGUI(int port, String dictionaryFile) {
        this.port = port;
        this.dictionaryFile = dictionaryFile;

        initializeUI();

        setTitle("Dictionary Server Monitor");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Custom logger handler to display logs in the GUI
        Logger.setLogHandler(new Logger.LogHandler() {
            @Override
            public void handleLog(Logger.Level level, String message) {
                appendLog(level, message);

                // Update client count if client connected or disconnected
                if (message.contains("Client connected")) {
                    clientCount++;
                    updateClientCount();
                } else if (message.contains("Client disconnected")) {
                    clientCount--;
                    if (clientCount < 0) clientCount = 0;
                    updateClientCount();
                }
            }
        });
    }

    /**
     * Updates the client count display.
     */
    private void updateClientCount() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                clientCountLabel.setText("Clients: " + clientCount);
            }
        });
    }

    /**
     * Initializes the user interface components.
     */
    private void initializeUI() {
        // Main layout
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Status panel (top)
        JPanel statusPanel = createStatusPanel();
        mainPanel.add(statusPanel, BorderLayout.NORTH);

        // Log panel (center)
        JPanel logPanel = createLogPanel();
        mainPanel.add(logPanel, BorderLayout.CENTER);

        // Control panel (bottom)
        JPanel controlPanel = createControlPanel();
        mainPanel.add(controlPanel, BorderLayout.SOUTH);

        // Add main panel to frame
        setContentPane(mainPanel);
    }

    /**
     * Creates the status panel.
     *
     * @return the status panel
     */
    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 3, 10, 0));
        panel.setBorder(new CompoundBorder(
                new EtchedBorder(),
                new EmptyBorder(5, 5, 5, 5)
        ));

        statusLabel = new JLabel("Server not running");
        statusLabel.setForeground(Color.RED);

        wordCountLabel = new JLabel("Words: 0");
        clientCountLabel = new JLabel("Clients: 0");

        panel.add(statusLabel);
        panel.add(wordCountLabel);
        panel.add(clientCountLabel);

        return panel;
    }

    /**
     * Creates the log panel.
     *
     * @return the log panel
     */
    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Server Log"));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(logArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Creates the control panel.
     *
     * @return the control panel
     */
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        startButton = new JButton("Start Server");
        stopButton = new JButton("Stop Server");
        stopButton.setEnabled(false);

        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startServer();
            }
        });

        stopButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                stopServer();
            }
        });

        panel.add(startButton);
        panel.add(stopButton);

        return panel;
    }

    /**
     * Starts the server in a background thread.
     */
    private void startServer() {
        startButton.setEnabled(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    server = new DictionaryServer(port, dictionaryFile);
                    serverRunning = true;

                    updateStatus();

                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            stopButton.setEnabled(true);
                        }
                    });

                    server.start();
                } catch (IOException e) {
                    Logger.error("Failed to start server", e);

                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            startButton.setEnabled(true);
                            JOptionPane.showMessageDialog(
                                    ServerGUI.this,
                                    "Failed to start server: " + e.getMessage(),
                                    "Error",
                                    JOptionPane.ERROR_MESSAGE
                            );
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * Stops the server.
     */
    private void stopServer() {
        stopButton.setEnabled(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (server != null) {
                    server.stop();
                    server = null;
                }

                serverRunning = false;
                clientCount = 0;
                updateStatus();

                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        startButton.setEnabled(true);
                    }
                });
            }
        }).start();
    }

    /**
     * Updates the server status display.
     */
    private void updateStatus() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (serverRunning) {
                    statusLabel.setText("Server running on port " + port);
                    statusLabel.setForeground(new Color(0, 128, 0)); // Dark green

                    // Update word count (if available)
                    if (server != null) {
                        try {
                            // Access dictionary through reflection since we don't have a direct getter
                            java.lang.reflect.Field dictionaryField = DictionaryServer.class.getDeclaredField("dictionary");
                            dictionaryField.setAccessible(true);
                            Dictionary dictionary = (Dictionary) dictionaryField.get(server);
                            int wordCount = dictionary.size();
                            wordCountLabel.setText("Words: " + wordCount);
                        } catch (Exception e) {
                            Logger.error("Error accessing dictionary", e);
                        }
                    }
                } else {
                    statusLabel.setText("Server not running");
                    statusLabel.setForeground(Color.RED);
                    wordCountLabel.setText("Words: 0");
                    clientCountLabel.setText("Clients: 0");
                }
            }
        });
    }

    /**
     * Appends a log message to the log area.
     *
     * @param level the log level
     * @param message the log message
     */
    private void appendLog(final Logger.Level level, final String message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String timestamp = dateFormat.format(new Date());

                Color color;
                switch (level) {
                    case INFO:
                        color = Color.BLACK;
                        break;
                    case WARNING:
                        color = Color.ORANGE.darker();
                        break;
                    case ERROR:
                        color = Color.RED;
                        break;
                    case DEBUG:
                        color = Color.GRAY;
                        break;
                    default:
                        color = Color.BLACK;
                        break;
                }

                String logEntry = String.format("[%s] %s: %s\n", timestamp, level, message);
                logArea.append(logEntry);

                // Scroll to the bottom
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }
        });
    }

    /**
     * Main method to start the server monitor GUI.
     *
     * @param args command-line arguments (port, dictionary-file)
     */
    public static void main(String[] args) {
        try {
            // Parse command-line arguments
            int port = Constants.DEFAULT_PORT;
            String dictionaryFile = Constants.DEFAULT_DICTIONARY_FILE;

            if (args.length >= 1) {
                try {
                    port = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid port number: " + args[0]);
                    System.out.println("Usage: java ServerGUI <port> <dictionary-file>");
                    return;
                }
            }

            if (args.length >= 2) {
                dictionaryFile = args[1];
            }

            // Start the GUI
            final int finalPort = port;
            final String finalDictionaryFile = dictionaryFile;

            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    ServerGUI gui = new ServerGUI(finalPort, finalDictionaryFile);
                    gui.setVisible(true);
                }
            });
        } catch (Exception e) {
            System.out.println("Failed to start server GUI: " + e.getMessage());
            e.printStackTrace();
        }
    }
}