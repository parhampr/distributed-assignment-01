package client;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.Serial;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import javax.swing.*;
import javax.swing.border.*;

import protocol.Request;
import protocol.Response;
import util.Constants;

/**
 * Graphical user interface for the dictionary client.
 */
public class ClientGUI extends JFrame implements ConnectionManager.ConnectionListener {
    @Serial
    private static final long serialVersionUID = 1L;

    private final ConnectionManager connectionManager;
    private long lastConnectionToastTime = 0;

    // Connection status
    private JLabel statusLabel;
    private JLabel statusIconLabel;
    private JButton connectButton;
    private ImageIcon connectedIcon;
    private ImageIcon disconnectedIcon;
    private ImageIcon connectingIcon;

    // Tabs
    private JTabbedPane tabbedPane;
    private JPanel searchPanel;
    private JPanel addRemovePanel;
    private JPanel updatePanel;

    // Search panel components
    private JTextField searchWordField;
    private JTextArea searchResultArea;
    private JButton searchButton;

    // Add/Remove panel components
    private JTextField addWordField;
    private JTextArea addMeaningsArea;
    private JButton addButton;
    private JTextField removeWordField;
    private JButton removeButton;

    // Update panel components
    private JTextField updateWordField;
    private JComboBox<String> existingMeaningsCombo;
    private JTextField newMeaningField;
    private JButton getMeaningsButton;
    private JButton updateMeaningButton;
    private JButton addMeaningButton;

    // Server info
    private final String serverAddress;
    private final int serverPort;

    private JCheckBox autoConnectCheckbox;

    /**
     * Creates a new ClientGUI with the provided connection manager.
     *
     * @param connectionManager the connection manager to use
     */
    public ClientGUI(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.serverAddress = connectionManager.getServerAddress();
        this.serverPort = connectionManager.getServerPort();
        connectionManager.setConnectionListener(this);

        // Load icons
        this.connectedIcon = createIcon("connected.png", 16, 16, Color.GREEN);
        this.disconnectedIcon = createIcon("disconnected.png", 16, 16, Color.RED);
        this.connectingIcon = createIcon("connecting.png", 16, 16, Color.ORANGE);

        initializeUI();

        setTitle(Constants.APP_TITLE);
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                connectionManager.disconnect();
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
            // Continue with default look and feel
        }

        // Main layout
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Status panel (top)
        JPanel statusPanel = createStatusPanel();
        mainPanel.add(statusPanel, BorderLayout.NORTH);

        // Tabbed pane (center)
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));

        // Create tabs
        searchPanel = createSearchPanel();
        addRemovePanel = createAddRemovePanel();
        updatePanel = createUpdatePanel();

        tabbedPane.addTab("Search", new ImageIcon(), searchPanel);
        tabbedPane.addTab("Add/Remove", new ImageIcon(), addRemovePanel);
        tabbedPane.addTab("Update", new ImageIcon(), updatePanel);

        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // Footer panel with server info
        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        footerPanel.setBorder(new CompoundBorder(
                new MatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                new EmptyBorder(5, 5, 5, 5)
        ));
        footerPanel.add(new JLabel("Server: " + serverAddress + ":" + serverPort));

        mainPanel.add(footerPanel, BorderLayout.SOUTH);

        // Add main panel to frame
        setContentPane(mainPanel);

        // Update UI state based on current connection status
        updateConnectionStatus();
    }

    /**
     * Creates the status panel.
     *
     * @return the status panel
     */
    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new CompoundBorder(
                new EtchedBorder(),
                new EmptyBorder(8, 10, 8, 10)
        ));

        // Create status box with icon
        Box statusBox = Box.createHorizontalBox();
        statusIconLabel = new JLabel(disconnectedIcon);
        statusLabel = new JLabel("Not connected");
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        statusLabel.setForeground(Color.RED);

        statusBox.add(statusIconLabel);
        statusBox.add(Box.createHorizontalStrut(5));
        statusBox.add(statusLabel);
        statusBox.add(Box.createHorizontalGlue());

        // Add auto-connect checkbox
        autoConnectCheckbox = new JCheckBox("Auto-Connect", connectionManager.isAutoConnectEnabled());
        autoConnectCheckbox.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        autoConnectCheckbox.addActionListener(e -> {
            boolean autoConnect = autoConnectCheckbox.isSelected();
            connectionManager.setAutoConnect(autoConnect);
            updateConnectionStatus();

            if (autoConnect) {
                showToastNotification("Auto-Connect Enabled",
                        "Will automatically attempt to reconnect if connection is lost",
                        JOptionPane.INFORMATION_MESSAGE);
            } else {
                showToastNotification("Auto-Connect Disabled",
                        "Manual connection required if disconnected",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });

        statusBox.add(autoConnectCheckbox);
        statusBox.add(Box.createHorizontalStrut(10));

        connectButton = new JButton("Connect");
        connectButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        connectButton.setFocusPainted(false);
        connectButton.addActionListener(e -> toggleConnection());

        panel.add(statusBox, BorderLayout.CENTER);
        panel.add(connectButton, BorderLayout.EAST);

        return panel;
    }

    /**
     * Toggle connection state when connect/disconnect button is clicked.
     */
    private void toggleConnection() {
        if (connectionManager.isConnected()) {
            // Force disable auto-connect when manually disconnecting
            if (connectionManager.isAutoConnectEnabled()) {
                autoConnectCheckbox.setSelected(false);
                connectionManager.setAutoConnect(false);
                showToastNotification("Auto-Connect Disabled",
                        "Auto-connect disabled due to manual disconnection",
                        JOptionPane.INFORMATION_MESSAGE);
            }
            connectionManager.disconnect();
        } else {
            new Thread(() -> {
                SwingUtilities.invokeLater(() -> {
                    connectButton.setEnabled(false);
                    statusLabel.setText("Connecting...");
                    statusLabel.setForeground(Color.ORANGE);
                    statusIconLabel.setIcon(connectingIcon);
                });

                boolean success = connectionManager.connect();

                SwingUtilities.invokeLater(() -> {
                    connectButton.setEnabled(true);
                    if (!success && !connectionManager.isAutoConnectEnabled()) {
                        // Only show a toast if the manual connection failed and we're not auto-reconnecting
                        showToastNotification("Connection Failed", "Failed to connect to server: " + serverAddress + ":" + serverPort, JOptionPane.ERROR_MESSAGE);
                    }
                });
            }).start();
        }
    }

    /**
     * Creates the search panel.
     *
     * @return the search panel
     */
    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Input panel
        JPanel inputPanel = new JPanel(new BorderLayout(10, 0));
        inputPanel.setBorder(new EmptyBorder(0, 0, 10, 0));

        JLabel wordLabel = new JLabel("Word:");
        wordLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));

        searchWordField = new JTextField(20);
        searchWordField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        searchButton = new JButton("Search");
        setSearchButtonAttributes(inputPanel, wordLabel, searchButton, searchWordField);

        // Result panel
        JPanel resultPanel = new JPanel(new BorderLayout());
        resultPanel.setBorder(new TitledBorder(BorderFactory.createEtchedBorder(),
                "Meanings", TitledBorder.LEFT, TitledBorder.TOP,
                new Font(Font.SANS_SERIF, Font.BOLD, 13)));

        searchResultArea = new JTextArea();
        searchResultArea.setEditable(false);
        searchResultArea.setLineWrap(true);
        searchResultArea.setWrapStyleWord(true);
        searchResultArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        searchResultArea.setBackground(new Color(250, 250, 250));

        // Add scroll pane for result area
        JScrollPane scrollPane = new JScrollPane(searchResultArea);
        resultPanel.add(scrollPane, BorderLayout.CENTER);

        // Add action listener for search button
        searchButton.addActionListener(e -> performSearch());

        // Add action listener for Enter key in search field
        searchWordField.addActionListener(e -> performSearch());

        panel.add(inputPanel, BorderLayout.NORTH);
        panel.add(resultPanel, BorderLayout.CENTER);

        return panel;
    }

    private void setSearchButtonAttributes(JPanel inputPanel, JLabel wordLabel, JButton searchButton, JTextField searchWordField) {
        searchButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        searchButton.setIcon(createIcon("search.png", 16, 16, Color.DARK_GRAY));
        searchButton.setEnabled(false);

        inputPanel.add(wordLabel, BorderLayout.WEST);
        inputPanel.add(searchWordField, BorderLayout.CENTER);
        inputPanel.add(searchButton, BorderLayout.EAST);
    }

    /**
     * Creates the add/remove panel.
     *
     * @return the add/remove panel
     */
    private JPanel createAddRemovePanel() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 0, 15));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Add word panel
        JPanel addPanel = new JPanel(new BorderLayout(0, 10));
        addPanel.setBorder(new TitledBorder(BorderFactory.createEtchedBorder(),
                "Add Word", TitledBorder.LEFT, TitledBorder.TOP,
                new Font(Font.SANS_SERIF, Font.BOLD, 13)));

        JPanel addWordPanel = new JPanel(new BorderLayout(10, 0));
        JLabel addWordLabel = new JLabel("Word:");
        addWordLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));

        addWordField = new JTextField(20);
        addWordField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));

        addWordPanel.add(addWordLabel, BorderLayout.WEST);
        addWordPanel.add(addWordField, BorderLayout.CENTER);

        JPanel addMeaningsPanel = new JPanel(new BorderLayout(0, 5));
        JLabel meaningsLabel = new JLabel("Meanings (one per line):");
        meaningsLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));

        addMeaningsArea = new JTextArea(5, 20);
        addMeaningsArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        addMeaningsArea.setLineWrap(true);
        addMeaningsArea.setBackground(new Color(250, 250, 250));
        JScrollPane meaningsScrollPane = new JScrollPane(addMeaningsArea);

        addMeaningsPanel.add(meaningsLabel, BorderLayout.NORTH);
        addMeaningsPanel.add(meaningsScrollPane, BorderLayout.CENTER);

        addButton = new JButton("Add Word");
        addButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        addButton.setIcon(createIcon("add.png", 16, 16, new Color(0, 120, 0)));
        addButton.setEnabled(false);
        addButton.addActionListener(e -> addWord());

        addPanel.add(addWordPanel, BorderLayout.NORTH);
        addPanel.add(addMeaningsPanel, BorderLayout.CENTER);
        addPanel.add(addButton, BorderLayout.SOUTH);

        // Remove word panel
        JPanel removePanel = new JPanel(new BorderLayout(0, 10));
        removePanel.setBorder(new TitledBorder(BorderFactory.createEtchedBorder(),
                "Remove Word", TitledBorder.LEFT, TitledBorder.TOP,
                new Font(Font.SANS_SERIF, Font.BOLD, 13)));

        JPanel removeWordPanel = new JPanel(new BorderLayout(10, 0));
        JLabel removeWordLabel = new JLabel("Word:");
        removeWordLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));

        removeWordField = new JTextField(20);
        removeWordField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));

        removeWordPanel.add(removeWordLabel, BorderLayout.WEST);
        removeWordPanel.add(removeWordField, BorderLayout.CENTER);

        removeButton = new JButton("Remove Word");
        removeButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        removeButton.setIcon(createIcon("remove.png", 16, 16, Color.RED));
        removeButton.setEnabled(false);
        removeButton.addActionListener(e -> removeWord());

        removePanel.add(removeWordPanel, BorderLayout.NORTH);
        removePanel.add(removeButton, BorderLayout.SOUTH);

        panel.add(addPanel);
        panel.add(removePanel);

        return panel;
    }

    /**
     * Creates the update panel.
     *
     * @return the update panel
     */
    private JPanel createUpdatePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 15));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Word selection panel
        JPanel wordPanel = new JPanel(new BorderLayout(10, 0));
        JLabel wordLabel = new JLabel("Word:");
        wordLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));

        updateWordField = new JTextField(20);
        updateWordField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));

        getMeaningsButton = new JButton("Get Meanings");
        setSearchButtonAttributes(wordPanel, wordLabel, getMeaningsButton, updateWordField);

        // Meaning selection panel
        JPanel meaningPanel = new JPanel(new BorderLayout(0, 10));
        meaningPanel.setBorder(new TitledBorder(BorderFactory.createEtchedBorder(),
                "Update Meaning", TitledBorder.LEFT, TitledBorder.TOP,
                new Font(Font.SANS_SERIF, Font.BOLD, 13)));

        JPanel existingMeaningPanel = new JPanel(new BorderLayout(10, 0));
        JLabel existingLabel = new JLabel("Existing Meaning:");
        existingLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));

        existingMeaningsCombo = new JComboBox<>();
        existingMeaningsCombo.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));

        existingMeaningPanel.add(existingLabel, BorderLayout.WEST);
        existingMeaningPanel.add(existingMeaningsCombo, BorderLayout.CENTER);

        JPanel newMeaningPanel = new JPanel(new BorderLayout(10, 0));
        newMeaningPanel.setBorder(new EmptyBorder(10, 0, 10, 0));
        JLabel newLabel = new JLabel("New Meaning:");
        newLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));

        newMeaningField = new JTextField(20);
        newMeaningField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));

        newMeaningPanel.add(newLabel, BorderLayout.WEST);
        newMeaningPanel.add(newMeaningField, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        updateMeaningButton = new JButton("Update Meaning");
        updateMeaningButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        updateMeaningButton.setIcon(createIcon("update.png", 16, 16, new Color(0, 0, 160)));
        updateMeaningButton.setEnabled(false);

        addMeaningButton = new JButton("Add New Meaning");
        addMeaningButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        addMeaningButton.setIcon(createIcon("add.png", 16, 16, new Color(0, 120, 0)));
        addMeaningButton.setEnabled(false);

        buttonPanel.add(updateMeaningButton);
        buttonPanel.add(addMeaningButton);

        meaningPanel.add(existingMeaningPanel, BorderLayout.NORTH);
        meaningPanel.add(newMeaningPanel, BorderLayout.CENTER);
        meaningPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Add action listeners
        getMeaningsButton.addActionListener(e -> getMeanings());
        updateMeaningButton.addActionListener(e -> updateMeaning());
        addMeaningButton.addActionListener(e -> addMeaning());

        panel.add(wordPanel, BorderLayout.NORTH);
        panel.add(meaningPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Creates an icon from resources or returns a fallback icon.
     */
    private ImageIcon createIcon(String name, int width, int height, Color fallbackColor) {
        try {
            // Try multiple paths to find the icon
            File resourceFile = new File("resources/icons/" + name);
            if (resourceFile.exists()) {
                ImageIcon icon = new ImageIcon(resourceFile.getAbsolutePath());
                return new ImageIcon(icon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH));
            }

            // Create fallback icon
            return createColorIcon(fallbackColor, width, height);
        } catch (Exception e) {
            return createColorIcon(fallbackColor, width, height);
        }
    }

    /**
     * Creates a simple colored icon as fallback.
     */
    private ImageIcon createColorIcon(Color color, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(color);
        g2d.fillOval(2, 2, width - 4, height - 4);
        g2d.setColor(color.darker());
        g2d.drawOval(2, 2, width - 4, height - 4);
        g2d.dispose();
        return new ImageIcon(image);
    }

    /**
     * Performs a dictionary search operation.
     * Validates input, sends request to server, and displays results.
     */
    private void performSearch() {
        final String word = searchWordField.getText().trim().toLowerCase();
        if (word.isEmpty()) {
            showToastNotification("Word Search", "Please enter a word to search", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Visual feedback for search in progress
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        searchButton.setEnabled(false);

        new Thread(() -> {
            try {
                Request request = new Request(Request.OperationType.SEARCH, word);
                Response response = connectionManager.sendRequest(request);

                SwingUtilities.invokeLater(() -> {
                    // Reset UI state
                    setCursor(Cursor.getDefaultCursor());
                    searchButton.setEnabled(connectionManager.isConnected());

                    if (response == null) {
                        showToastNotification("Search Error",
                                "Failed to receive response from server for word: \"" + word + "\"",
                                JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    displaySearchResults(word, response);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    setCursor(Cursor.getDefaultCursor());
                    searchButton.setEnabled(connectionManager.isConnected());
                    showError("Search Error" + "An error occurred while searching for \"" + word + "\": " + e.getMessage());
                });
            }
        }).start();
    }

    /**
     * Displays search results in the UI.
     *
     * @param word the searched word
     * @param response the server response
     */
    private void displaySearchResults(String word, Response response) {
        if (response.getStatus() == Response.StatusCode.SUCCESS) {
            String[] meanings = response.getMeanings();

            if (meanings == null || meanings.length == 0) {
                searchResultArea.setText("No meanings found for \"" + word + "\"");
                return;
            }

            StringBuilder result = new StringBuilder();
            for (int i = 0; i < meanings.length; i++) {
                result.append(i + 1).append(". ").append(meanings[i]).append("\n");
            }

            searchResultArea.setText(result.toString());
            searchResultArea.setCaretPosition(0);
        } else {
            showToastNotification("Search Result",
                    "Problem finding \"" + word + "\": " + response.getMessage(),
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * Adds a new word to the dictionary.
     * Validates input, sends request to server, and handles the response.
     */
    private void addWord() {
        final String word = addWordField.getText().trim().toLowerCase();
        if (word.isEmpty()) {
            showToastNotification("Add Word", "Please enter a word to add", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String meaningsText = addMeaningsArea.getText().trim();
        if (meaningsText.isEmpty()) {
            showMessage("Please enter at least one meaning");
            return;
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        addButton.setEnabled(false);

        new Thread(() -> {
            try {
                // Filter out empty lines and trim each meaning
                String[] meanings = Arrays.stream(meaningsText.split("\n"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toArray(String[]::new);

                Request request = new Request(Request.OperationType.ADD, word, meanings);
                Response response = connectionManager.sendRequest(request);

                SwingUtilities.invokeLater(() -> {
                    setCursor(Cursor.getDefaultCursor());
                    addButton.setEnabled(connectionManager.isConnected());

                    if (response == null) {
                        showError("Failed to get response from server while adding \"" + word + "\"");
                        return;
                    }

                    if (response.getStatus() == Response.StatusCode.SUCCESS) {
                        showToastNotification("New Word Added: "+word, "Successfully added new word with " + meanings.length + " meaning(s)", JOptionPane.INFORMATION_MESSAGE);
                        addWordField.setText("");
                        addMeaningsArea.setText("");
                    } else {
                        showError("Failed to add \"" + word + "\": " + response.getMessage());
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    setCursor(Cursor.getDefaultCursor());
                    addButton.setEnabled(connectionManager.isConnected());
                    showError("An error occurred while adding word \"" + word + "\": " + e.getMessage());
                });
            }
        }).start();
    }

    /**
     * Removes a word from the dictionary.
     */
    private void removeWord() {
        String word = removeWordField.getText().trim().toLowerCase();
        if (word.isEmpty()) {
            showToastNotification("Add Word", "Please enter a word to remove", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Ask for confirmation
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to remove the word \"" + word + "\"?",
                "Confirm Removal",
                JOptionPane.YES_NO_OPTION
        );

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        removeButton.setEnabled(false);

        new Thread(() -> {
            Request request = new Request(Request.OperationType.REMOVE, word);
            Response response = connectionManager.sendRequest(request);

            SwingUtilities.invokeLater(() -> {
                setCursor(Cursor.getDefaultCursor());
                removeButton.setEnabled(connectionManager.isConnected());

                if (response == null) {
                    showError("Failed to get response from server");
                    return;
                }

                if (response.getStatus() == Response.StatusCode.SUCCESS) {
                    showMessage("Word \"" + word + "\" removed successfully");
                    removeWordField.setText("");
                } else {
                    showError(response.getMessage());
                }
            });
        }).start();
    }

    /**
     * Gets meanings for a word.
     */
    private void getMeanings() {
        String word = updateWordField.getText().trim().toLowerCase();
        if (word.isEmpty()) {
            showToastNotification("Add Word", "Please enter a word to update", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        getMeaningsButton.setEnabled(false);

        new Thread(() -> {
            Request request = new Request(Request.OperationType.SEARCH, word);
            Response response = connectionManager.sendRequest(request);

            SwingUtilities.invokeLater(() -> {
                setCursor(Cursor.getDefaultCursor());
                getMeaningsButton.setEnabled(connectionManager.isConnected());

                if (response == null) {
                    showError("Failed to get response from server");
                    return;
                }

                existingMeaningsCombo.removeAllItems();

                if (response.getStatus() == Response.StatusCode.SUCCESS) {
                    String[] meanings = response.getMeanings();
                    for (String meaning : meanings) {
                        existingMeaningsCombo.addItem(meaning);
                    }

                    if (meanings.length > 0) {
                        existingMeaningsCombo.setSelectedIndex(0);
                        updateMeaningButton.setEnabled(true);
                        addMeaningButton.setEnabled(true);
                    } else {
                        showMessage("Word found but it has no meanings. You can add a meaning.");
                        updateMeaningButton.setEnabled(false);
                        addMeaningButton.setEnabled(true);
                    }
                } else {
                    showError("Word not found: " + word);
                    updateMeaningButton.setEnabled(false);
                    addMeaningButton.setEnabled(true); // Allow adding to new words
                }
            });
        }).start();
    }

    /**
     * Updates a meaning for a word.
     */
    private void updateMeaning() {
        String word = updateWordField.getText().trim();
        if (word.isEmpty()) {
            showMessage("Please enter a word");
            return;
        }

        String oldMeaning = (String) existingMeaningsCombo.getSelectedItem();
        if (oldMeaning == null) {
            showMessage("Please select an existing meaning");
            return;
        }

        String newMeaning = newMeaningField.getText().trim();
        if (newMeaning.isEmpty()) {
            showMessage("Please enter a new meaning");
            return;
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        updateMeaningButton.setEnabled(false);

        new Thread(() -> {
            Request request = new Request(Request.OperationType.UPDATE_MEANING, word, oldMeaning, newMeaning);
            Response response = connectionManager.sendRequest(request);

            SwingUtilities.invokeLater(() -> {
                setCursor(Cursor.getDefaultCursor());
                updateMeaningButton.setEnabled(connectionManager.isConnected() && existingMeaningsCombo.getItemCount() > 0);

                if (response == null) {
                    showError("Failed to get response from server");
                    return;
                }

                if (response.getStatus() == Response.StatusCode.SUCCESS) {
                    showMessage("Meaning updated successfully");
                    newMeaningField.setText("");
                    getMeanings(); // Refresh meanings
                } else {
                    showError(response.getMessage());
                }
            });
        }).start();
    }

    /**
     * Adds a new meaning to a word.
     */
    private void addMeaning() {
        String word = updateWordField.getText().trim();
        if (word.isEmpty()) {
            showMessage("Please enter a word");
            return;
        }

        String newMeaning = newMeaningField.getText().trim();
        if (newMeaning.isEmpty()) {
            showMessage("Please enter a meaning to add");
            return;
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        addMeaningButton.setEnabled(false);

        new Thread(() -> {
            Request request = new Request(Request.OperationType.ADD_MEANING, word, newMeaning);
            Response response = connectionManager.sendRequest(request);

            SwingUtilities.invokeLater(() -> {
                setCursor(Cursor.getDefaultCursor());
                addMeaningButton.setEnabled(connectionManager.isConnected());

                if (response == null) {
                    showError("Failed to get response from server");
                    return;
                }

                if (response.getStatus() == Response.StatusCode.SUCCESS) {
                    showMessage("Meaning added successfully");
                    newMeaningField.setText("");
                    getMeanings(); // Refresh meanings
                } else {
                    showError(response.getMessage());
                }
            });
        }).start();
    }

    /**
     * Shows an information message.
     *
     * @param message the message to show
     */
    private void showMessage(String message) {
        JOptionPane.showMessageDialog(this, message, "Information", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Shows an error message.
     *
     * @param message the message to show
     */
    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Updates the UI when connection status changes.
     */
    private void updateConnectionStatus() {
        boolean connected = connectionManager.isConnected();
        boolean autoConnect = connectionManager.isAutoConnectEnabled();

        if (connected) {
            statusLabel.setText("Connected to server");
            statusLabel.setForeground(new Color(0, 128, 0)); // Dark green
            statusIconLabel.setIcon(connectedIcon);
            connectButton.setText("Disconnect");
            connectButton.setEnabled(true);
        } else {
            if (autoConnect) {
                statusLabel.setText("Not connected (auto-connect enabled)");
            } else {
                statusLabel.setText("Not connected");
            }
            statusLabel.setForeground(Color.RED);
            statusIconLabel.setIcon(disconnectedIcon);
            connectButton.setText("Force Connect");
            connectButton.setEnabled(true);
        }

        // Enable/disable operation buttons based on connection status
        searchButton.setEnabled(connected);
        addButton.setEnabled(connected);
        removeButton.setEnabled(connected);
        getMeaningsButton.setEnabled(connected);
        addMeaningButton.setEnabled(connected);
        updateMeaningButton.setEnabled(connected && existingMeaningsCombo.getItemCount() > 0);

        // Ensure checkbox reflects current state
        autoConnectCheckbox.setSelected(autoConnect);
    }

    /**
     * Shows a toast-style notification in the bottom right corner.
     * @param title The notification title
     * @param message The notification message
     * @param messageType JOptionPane message type constant (ERROR_MESSAGE, WARNING_MESSAGE, etc.)
     */
    private void showToastNotification(String title, String message, int messageType) {
        // Use a separate thread to avoid blocking UI
        new Thread(() -> {
            SwingUtilities.invokeLater(() -> {
                // Create undecorated toast panel
                JDialog toast = new JDialog(this);
                toast.setUndecorated(true);
                toast.setSize(400, 80);

                // Create a rounded panel with shadow effect for the toast
                JPanel toastPanel = new JPanel(new BorderLayout()) {
                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                        // Shadow effect
                        g2.setColor(new Color(0, 0, 0, 50));
                        g2.fillRoundRect(3, 3, getWidth() - 6, getHeight() - 6, 15, 15);

                        // Background color based on message type
                        Color bgColor;
                        if (messageType == JOptionPane.ERROR_MESSAGE) {
                            bgColor = new Color(255, 220, 220);
                        } else if (messageType == JOptionPane.WARNING_MESSAGE) {
                            bgColor = new Color(255, 243, 200);
                        } else {
                            bgColor = new Color(220, 237, 255);
                        }

                        g2.setColor(bgColor);
                        g2.fillRoundRect(0, 0, getWidth() - 3, getHeight() - 3, 15, 15);
                        g2.dispose();
                    }
                };
                toastPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

                // Add icon based on message type
                JLabel iconLabel = new JLabel();
                if (messageType == JOptionPane.ERROR_MESSAGE) {
                    iconLabel.setIcon(UIManager.getIcon("OptionPane.errorIcon"));
                } else if (messageType == JOptionPane.WARNING_MESSAGE) {
                    iconLabel.setIcon(UIManager.getIcon("OptionPane.warningIcon"));
                } else {
                    iconLabel.setIcon(UIManager.getIcon("OptionPane.informationIcon"));
                }

                // Title and message
                JPanel textPanel = new JPanel(new BorderLayout(5, 5));
                textPanel.setOpaque(false);

                JLabel titleLabel = new JLabel(title);
                titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));

                JLabel messageLabel = new JLabel("<html><body width='200'>" + message + "</body></html>");
                messageLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

                textPanel.add(titleLabel, BorderLayout.NORTH);
                textPanel.add(messageLabel, BorderLayout.CENTER);

                // Add components to toast
                toastPanel.add(iconLabel, BorderLayout.WEST);
                toastPanel.add(textPanel, BorderLayout.CENTER);
                toast.add(toastPanel);

                // Position at bottom right
                positionToastBottomRight(toast);

                // Fade-in animation
                final Timer fadeInTimer = new Timer(20, null);
                final float[] opacity = {0.0f};

                fadeInTimer.addActionListener(e -> {
                    opacity[0] += 0.1f;
                    if (opacity[0] > 1.0f) {
                        opacity[0] = 1.0f;
                        fadeInTimer.stop();

                        // Schedule fade-out after showing for 3 seconds
                        Timer dismissTimer = new Timer(3000, e2 -> {
                            Timer fadeOutTimer = new Timer(20, null);
                            fadeOutTimer.addActionListener(e3 -> {
                                opacity[0] -= 0.1f;
                                if (opacity[0] < 0.0f) {
                                    opacity[0] = 0.0f;
                                    fadeOutTimer.stop();
                                    toast.dispose();
                                } else {
                                    toast.setOpacity(opacity[0]);
                                }
                            });
                            fadeOutTimer.start();
                        });
                        dismissTimer.setRepeats(false);
                        dismissTimer.start();
                    }
                    toast.setOpacity(opacity[0]);
                });

                // Show the toast
                toast.setVisible(true);
                fadeInTimer.start();
            });
        }).start();
    }

    /**
     * Positions a toast notification in the bottom right corner of the screen
     */
    private void positionToastBottomRight(JDialog toast) {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(toast.getGraphicsConfiguration());

        // Calculate position - 20px margin from right and bottom edges
        int x = screenSize.width - toast.getWidth() - 20 - screenInsets.right;
        int y = screenInsets.bottom + 20;

        toast.setLocation(x, y);
    }

    @Override
    public void onConnected() {
        SwingUtilities.invokeLater(() -> {
            updateConnectionStatus();
            showToastNotification("Connected",
                    "Connected to " + serverAddress + ":" + serverPort,
                    JOptionPane.INFORMATION_MESSAGE);
        });
    }

    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            updateConnectionStatus();
            if (connectionManager.isAutoConnectEnabled()) {
                showToastNotification("Disconnected",
                        "Connection to server has been lost. Attempting to reconnect automatically...",
                        JOptionPane.WARNING_MESSAGE);
            } else {
                showToastNotification("Disconnected",
                        "Connection to server has been lost. Click 'Force Connect' to reconnect.",
                        JOptionPane.WARNING_MESSAGE);
            }
        });
    }

    @Override
    public void onReconnecting() {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Reconnecting...");
            statusLabel.setForeground(Color.ORANGE);
            statusIconLabel.setIcon(connectingIcon);
            connectButton.setEnabled(false);

            // Disable all operation buttons
            searchButton.setEnabled(false);
            addButton.setEnabled(false);
            removeButton.setEnabled(false);
            getMeaningsButton.setEnabled(false);
            addMeaningButton.setEnabled(false);
            updateMeaningButton.setEnabled(false);

            // We'll show a toast only when reconnection starts, not for each attempt
            showToastNotification("Reconnecting", "Attempting to reconnect to server...", JOptionPane.WARNING_MESSAGE);
        });
    }

    @Override
    public void onReconnectFailed() {
        SwingUtilities.invokeLater(() -> {
            // Update the status to show we're still trying if auto-connect is on
            if (connectionManager.isAutoConnectEnabled()) {
                statusLabel.setText("Reconnection failed (retrying...)");
            } else {
                statusLabel.setText("Reconnection failed");
            }
            statusLabel.setForeground(Color.RED);

            // Make connect button available if auto-connect is disabled
            if (!connectionManager.isAutoConnectEnabled()) {
                statusIconLabel.setIcon(disconnectedIcon);
                connectButton.setEnabled(true);
                connectButton.setText("Force Connect");
            }

            // Keep all operation buttons disabled
            searchButton.setEnabled(false);
            addButton.setEnabled(false);
            removeButton.setEnabled(false);
            getMeaningsButton.setEnabled(false);
            addMeaningButton.setEnabled(false);
            updateMeaningButton.setEnabled(false);

            // Show a toast notification but not too frequently
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastConnectionToastTime > Constants.TOAST_COOLDOWN_MS) {
                lastConnectionToastTime = currentTime;
                if (connectionManager.isAutoConnectEnabled()) {
                    showToastNotification("Still Trying", "Reconnection failed. Will continue retrying...", JOptionPane.WARNING_MESSAGE);
                } else {
                    showToastNotification("Connection Failed", "Cannot connect to server. Click 'Force Connect' to try again.", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }
}