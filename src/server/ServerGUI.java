package server;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Timer;

import util.Constants;
import util.Logger;

/**
 * Graphical user interface for monitoring the dictionary server.
 * This is an optional component for the creativity element.
 */
public class ServerGUI extends JFrame {
    @Serial
    private static final long serialVersionUID = 1L;

    private DictionaryServer server;
    private JTextArea logArea;
    private JButton startButton;
    private JButton stopButton;
    private JLabel statusLabel;
    private JLabel statusIconLabel;
    private JLabel wordCountLabel;
    private JLabel clientCountLabel;
    private JComboBox<String> logLevelCombo;
    private JCheckBox consoleOutputCheckbox;
    private JProgressBar memoryUsageBar;
    private JLabel memoryUsageLabel;

    private final int port;
    private final String dictionaryFile;
    private boolean serverRunning = false;
    private Timer statsUpdateTimer;

    // Cache icons to avoid reloading them
    private ImageIcon runningIcon;
    private ImageIcon stoppedIcon;

    /**
     * Creates a new ServerGUI with the specified port and dictionary file.
     *
     * @param port the port to listen on
     * @param dictionaryFile the file to load dictionary data from
     */
    public ServerGUI(int port, String dictionaryFile) {
        this.port = port;
        this.dictionaryFile = dictionaryFile;

        // Preload icons
        this.runningIcon = createIcon("start.png", 20, 20);
        this.stoppedIcon = createIcon("stop.png", 20, 20);

        initializeUI();

        setTitle("Dictionary Server Monitor");
        setSize(900, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Custom logger handler to display logs in the GUI
        Logger.setLogHandler(this::appendLog);

        // Add window listener to handle close events properly
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (serverRunning) {
                    stopServer();
                }
                if (statsUpdateTimer != null) {
                    statsUpdateTimer.cancel();
                }
            }
        });
    }

    /**
     * Initializes the user interface components.
     */
    private void initializeUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            Logger.warning("Could not set system look and feel");
        }

        // Main layout
        JPanel mainPanel = new JPanel(new BorderLayout(0, 10));
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
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new CompoundBorder(
                new EtchedBorder(),
                new EmptyBorder(10, 10, 10, 10)
        ));

        // Server info panel
        JPanel serverInfoPanel = new JPanel(new GridLayout(2, 1, 0, 5));

        // Status indicators - using Box layout to eliminate unwanted gaps
        Box statusIndicatorsBox = Box.createHorizontalBox();
        statusIndicatorsBox.setBorder(new EmptyBorder(0, 0, 0, 0));

        // Status with icon - using BoxLayout to control spacing precisely
        Box statusBox = Box.createHorizontalBox();
        statusIconLabel = new JLabel(stoppedIcon);
        statusLabel = new JLabel("Server not running");
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        statusLabel.setForeground(Color.RED);

        statusBox.add(statusIconLabel);
        statusBox.add(Box.createHorizontalStrut(5)); // Fixed 5px gap
        statusBox.add(statusLabel);

        // Word and client counts
        wordCountLabel = new JLabel("Words: 0");
        wordCountLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        clientCountLabel = new JLabel("Clients: 0");
        clientCountLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));

        statusIndicatorsBox.add(statusBox);
        statusIndicatorsBox.add(Box.createHorizontalStrut(15)); // Fixed gap
        statusIndicatorsBox.add(wordCountLabel);
        statusIndicatorsBox.add(Box.createHorizontalStrut(15)); // Fixed gap
        statusIndicatorsBox.add(clientCountLabel);
        statusIndicatorsBox.add(Box.createHorizontalGlue()); // Push everything to the left

        // Memory usage panel
        JPanel memoryPanel = new JPanel(new BorderLayout(5, 0));
        memoryUsageBar = new JProgressBar(0, 100);
        memoryUsageBar.setStringPainted(true);
        memoryUsageLabel = new JLabel("Memory Usage: ");

        memoryPanel.add(memoryUsageLabel, BorderLayout.WEST);
        memoryPanel.add(memoryUsageBar, BorderLayout.CENTER);

        serverInfoPanel.add(statusIndicatorsBox);
        serverInfoPanel.add(memoryPanel);

        // Right side - log controls
        JPanel logControlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JLabel logLevelLabel = new JLabel("Log Level:");
        logLevelCombo = new JComboBox<>(Arrays.stream(Logger.Level.values()).map(Enum::name).toArray(String[]::new));
        logLevelCombo.setSelectedItem(Logger.Level.INFO.toString());

        // Using ItemListener to respond only when selection actually changes
        logLevelCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                String level = (String) e.getItem();
                setLogLevel(Logger.Level.valueOf(level));
            }
        });

        consoleOutputCheckbox = new JCheckBox("Console Output", true);
        consoleOutputCheckbox.addActionListener(e -> Logger.setConsoleOutput(consoleOutputCheckbox.isSelected()));

        logControlsPanel.add(logLevelLabel);
        logControlsPanel.add(logLevelCombo);
        logControlsPanel.add(consoleOutputCheckbox);

        panel.add(serverInfoPanel, BorderLayout.CENTER);
        panel.add(logControlsPanel, BorderLayout.EAST);

        return panel;
    }

    /**
     * Sets the log level for filtering log messages.
     *
     * @param level the log level
     */
    private void setLogLevel(Logger.Level level) {
        Logger.setLevel(level);
        logArea.append("<--------------- Log level changed to " + level + " --------------->\n");
    }

    /**
     * Creates the log panel.
     *
     * @return the log panel
     */
    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setBorder(new TitledBorder(BorderFactory.createEtchedBorder(), "Server Log",
                TitledBorder.LEFT, TitledBorder.TOP, new Font(Font.SANS_SERIF, Font.BOLD, 12)));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBackground(new Color(250, 250, 250));

        logArea.getDocument().putProperty("maxLength", 50000);

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = getButtonPanel();
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel getButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        // Add export log button
        JButton exportButton = new JButton("Export Log");
        exportButton.setToolTipText("Save log contents to a file");
        exportButton.addActionListener(e -> exportLog());

        // Add clear button
        JButton clearButton = new JButton("Clear Log");
        clearButton.setToolTipText("Clear the log display");
        clearButton.addActionListener(e -> logArea.setText(""));

        buttonPanel.add(exportButton);
        buttonPanel.add(clearButton);
        return buttonPanel;
    }

    /**
     * Exports the log content to a file.
     */
    private void exportLog() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Log File");
        fileChooser.setSelectedFile(new java.io.File("server_log_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".txt"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                java.io.File file = fileChooser.getSelectedFile();
                java.io.FileWriter writer = new java.io.FileWriter(file);
                writer.write(logArea.getText());
                writer.close();
                JOptionPane.showMessageDialog(this,
                        "Log saved successfully to:\n" + file.getAbsolutePath(),
                        "Log Saved", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                Logger.error("Failed to save log file", e);
                JOptionPane.showMessageDialog(this,
                        "Failed to save log file: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Creates the control panel.
     *
     * @return the control panel
     */
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(5, 0, 0, 0));

        // Server configuration panel
        JPanel configPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        configPanel.add(new JLabel("Port: " + port));
        configPanel.add(new JLabel("Dictionary: " + dictionaryFile));

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        startButton = new JButton("Start Server");
        startButton.setIcon(createIcon("start.png", 16, 16));
        startButton.setBackground(new Color(230, 255, 230));

        stopButton = new JButton("Stop Server");
        stopButton.setIcon(createIcon("stop.png", 16, 16));
        stopButton.setBackground(new Color(255, 230, 230));
        stopButton.setEnabled(false);

        startButton.addActionListener(e -> startServer());
        stopButton.addActionListener(e -> stopServer());

        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);

        panel.add(configPanel, BorderLayout.WEST);
        panel.add(buttonPanel, BorderLayout.EAST);

        return panel;
    }

    /**
     * Creates an icon from resources or returns null if not found.
     */
    private ImageIcon createIcon(String name, int width, int height) {
        try {
            File resourceFile = new File("resources/icons/" + name);
            if (resourceFile.exists()) {
                ImageIcon icon = new ImageIcon(resourceFile.getAbsolutePath());
                return new ImageIcon(icon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH));
            }
            // No Icon Scenarios
            return createColorIcon(name.contains("start") ? Color.GREEN : Color.RED, width, height);
        } catch (Exception e) {
            // Default Coloured Icon
            return createColorIcon(name.contains("start") ? Color.GREEN : Color.RED, width, height);
        }
    }

    /**
     * Creates a simple colored icon as fallback when image resources aren't available
     */
    private ImageIcon createColorIcon(Color color, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(color);
        g2d.fillOval(0, 0, width, height);
        g2d.dispose();
        return new ImageIcon(image);
    }

    /**
     * Starts the server in a background thread.
     */
    private void startServer() {
        startButton.setEnabled(false);
        logArea.append("Starting server on port " + port + "...\n");

        new Thread(() -> {
            try {
                server = new DictionaryServer(port, dictionaryFile);
                serverRunning = true;

                updateStatus();
                startStatsUpdater();

                SwingUtilities.invokeLater(() -> {
                    stopButton.setEnabled(true);
                });

                server.start();
            } catch (IOException e) {
                Logger.error("Failed to start server", e);

                SwingUtilities.invokeLater(() -> {
                    startButton.setEnabled(true);
                    JOptionPane.showMessageDialog(
                            ServerGUI.this,
                            "Failed to start server: " + e.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                });
            }
        }).start();
    }

    /**
     * Stops the server.
     */
    private void stopServer() {
        stopButton.setEnabled(false);
        logArea.append("Stopping server...\n");

        new Thread(() -> {
            if (server != null) {
                server.stop();
                server = null;
            }

            serverRunning = false;
            stopStatsUpdater();
            updateStatus();

            SwingUtilities.invokeLater(() -> {
                startButton.setEnabled(true);
            });
        }).start();
    }

    /**
     * Starts a timer to update server statistics periodically.
     */
    private void startStatsUpdater() {
        if (statsUpdateTimer != null) {
            statsUpdateTimer.cancel();
        }

        statsUpdateTimer = new Timer("StatsUpdater", true);
        statsUpdateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateClientCount();
                updateMemoryUsage();
            }
        }, 0, 2000); // Update every 2 seconds
    }

    /**
     * Stops the statistics update timer.
     */
    private void stopStatsUpdater() {
        if (statsUpdateTimer != null) {
            statsUpdateTimer.cancel();
            statsUpdateTimer = null;
        }
    }

    /**
     * Updates the client count display by getting the count directly from the server.
     */
    private void updateClientCount() {
        if (server != null && serverRunning) {
            final int count = server.getClientCount();
            SwingUtilities.invokeLater(() -> {
                clientCountLabel.setText("Clients: " + count);
                // Change color based on load (optional)
                if (count > 10) {
                    clientCountLabel.setForeground(Color.RED);
                } else if (count > 5) {
                    clientCountLabel.setForeground(new Color(255, 140, 0)); // Orange
                } else {
                    clientCountLabel.setForeground(Color.BLACK);
                }
            });
        }
    }

    /**
     * Updates the memory usage display.
     */
    private void updateMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        int percentage = (int) ((usedMemory * 100) / totalMemory);
        final String memText = "Memory: " + formatSize(usedMemory) + " / " + formatSize(totalMemory);

        SwingUtilities.invokeLater(() -> {
            memoryUsageBar.setValue(percentage);
            memoryUsageLabel.setText(memText);

            // Set color based on usage
            if (percentage > 80) {
                memoryUsageBar.setForeground(Color.RED);
            } else if (percentage > 60) {
                memoryUsageBar.setForeground(Color.ORANGE);
            } else {
                memoryUsageBar.setForeground(new Color(0, 150, 0));
            }
        });
    }

    /**
     * Formats memory size to a readable format.
     */
    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        int z = (63 - Long.numberOfLeadingZeros(size)) / 10;
        return String.format("%.1f %sB", (double)size / (1L << (z*10)), " KMGTPE".charAt(z));
    }

    /**
     * Updates the server status display.
     */
    private void updateStatus() {
        SwingUtilities.invokeLater(() -> {
            if (serverRunning) {
                statusLabel.setText("Server running on port " + port);
                statusLabel.setForeground(new Color(0, 128, 0));
                // Update the icon to show running state
                statusIconLabel.setIcon(runningIcon);

                // Update word count (if available)
                if (server != null) {
                    try {
                        Dictionary dictionary = server.getDictionary();
                        int wordCount = dictionary.size();
                        wordCountLabel.setText("Words: " + wordCount);
                    } catch (Exception e) {
                        Logger.error("Error accessing dictionary", e);
                    }
                }
            } else {
                statusLabel.setText("Server not running");
                statusLabel.setForeground(Color.RED);
                // Update the icon to show stopped state
                statusIconLabel.setIcon(stoppedIcon);
                wordCountLabel.setText("Words: 0");
                clientCountLabel.setText("Clients: 0");
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
        SwingUtilities.invokeLater(() -> {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String timestamp = dateFormat.format(new Date());

            Color color = switch (level) {
                case WARNING -> Color.ORANGE.darker();
                case ERROR -> Color.RED;
                case DEBUG -> Color.GRAY;
                default -> Color.BLACK;
            };

            String logEntry = String.format("[%s] %s: %s\n", timestamp, level, message);

            // Check if we need to trim the log (to prevent memory issues)
            int docLength = logArea.getDocument().getLength();
            Integer maxLength = (Integer) logArea.getDocument().getProperty("maxLength");
            if (maxLength != null && docLength > maxLength) {
                try {
                    // Remove first 20% of content when limit is reached
                    int cutLength = docLength / 5;
                    logArea.getDocument().remove(0, cutLength);
                    logArea.append("\n... [Log trimmed to prevent memory issues] ...\n\n");
                } catch (Exception e) {
                    // In case of error, just clear the log
                    logArea.setText("");
                    logArea.append("[Log cleared due to size limits]\n");
                }
            }

            logArea.append(logEntry);

            // Scroll to the bottom
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    /**
     * Main method to start the server monitor GUI.
     *
     * @param args command-line arguments (port, dictionary-file)
     */
    public static void main(String[] args) {
        try {
            // Set system look and feel
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.out.println("Could not set system look and feel: " + e.getMessage());
        }

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

            SwingUtilities.invokeLater(() -> {
                ServerGUI gui = new ServerGUI(finalPort, finalDictionaryFile);
                gui.setVisible(true);
            });
        } catch (Exception e) {
            System.out.println("Failed to start server GUI: " + e.getMessage());
            e.printStackTrace();
        }
    }
}