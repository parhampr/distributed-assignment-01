package client;

import java.awt.*;
import java.awt.event.*;
import java.io.Serial;
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

    // Connection status
    private JLabel statusLabel;
    private JButton connectButton;

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

    /**
     * Creates a new ClientGUI with the specified server address and port.
     *
     * @param serverAddress the server address
     * @param serverPort the server port
     */
    public ClientGUI(String serverAddress, int serverPort) {
        connectionManager = new ConnectionManager(serverAddress, serverPort);
        connectionManager.setConnectionListener(this);

        initializeUI();

        setTitle(Constants.APP_TITLE);
        setSize(Constants.WINDOW_WIDTH, Constants.WINDOW_HEIGHT);
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
        // Main layout
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Status panel (top)
        JPanel statusPanel = createStatusPanel();
        mainPanel.add(statusPanel, BorderLayout.NORTH);

        // Tabbed pane (center)
        tabbedPane = new JTabbedPane();

        // Create tabs
        searchPanel = createSearchPanel();
        addRemovePanel = createAddRemovePanel();
        updatePanel = createUpdatePanel();

        tabbedPane.addTab("Search", searchPanel);
        tabbedPane.addTab("Add/Remove", addRemovePanel);
        tabbedPane.addTab("Update", updatePanel);

        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // Add main panel to frame
        setContentPane(mainPanel);
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
                new EmptyBorder(5, 5, 5, 5)
        ));

        statusLabel = new JLabel("Not connected");
        statusLabel.setForeground(Color.RED);

        connectButton = new JButton("Connect");
        connectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (connectionManager.isConnected()) {
                    connectionManager.disconnect();
                } else {
                    new Thread(() -> {
                        connectButton.setEnabled(false);
                        connectionManager.connect();
                        connectButton.setEnabled(true);
                    }).start();
                }
            }
        });

        panel.add(statusLabel, BorderLayout.WEST);
        panel.add(connectButton, BorderLayout.EAST);

        return panel;
    }

    /**
     * Creates the search panel.
     *
     * @return the search panel
     */
    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Input panel
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));

        JLabel wordLabel = new JLabel("Word:");
        searchWordField = new JTextField(20);
        searchButton = new JButton("Search");

        inputPanel.add(wordLabel, BorderLayout.WEST);
        inputPanel.add(searchWordField, BorderLayout.CENTER);
        inputPanel.add(searchButton, BorderLayout.EAST);

        // Result panel
        JPanel resultPanel = new JPanel(new BorderLayout());
        resultPanel.setBorder(new TitledBorder("Meanings"));

        searchResultArea = new JTextArea();
        searchResultArea.setEditable(false);
        searchResultArea.setLineWrap(true);
        searchResultArea.setWrapStyleWord(true);
        // Add scroll pane for result area
        JScrollPane scrollPane = new JScrollPane(searchResultArea);
        resultPanel.add(scrollPane, BorderLayout.CENTER);

        // Add action listener for search button
        searchButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performSearch();
            }
        });

        // Add action listener for Enter key in search field
        searchWordField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performSearch();
            }
        });

        panel.add(inputPanel, BorderLayout.NORTH);
        panel.add(resultPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Creates the add/remove panel.
     *
     * @return the add/remove panel
     */
    private JPanel createAddRemovePanel() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 0, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Add word panel
        JPanel addPanel = new JPanel(new BorderLayout(0, 5));
        addPanel.setBorder(new TitledBorder("Add Word"));

        JPanel addWordPanel = new JPanel(new BorderLayout(5, 0));
        JLabel addWordLabel = new JLabel("Word:");
        addWordField = new JTextField(20);

        addWordPanel.add(addWordLabel, BorderLayout.WEST);
        addWordPanel.add(addWordField, BorderLayout.CENTER);

        JPanel addMeaningsPanel = new JPanel(new BorderLayout(0, 5));
        JLabel meaningsLabel = new JLabel("Meanings (one per line):");
        addMeaningsArea = new JTextArea(5, 20);
        addMeaningsArea.setLineWrap(true);
        JScrollPane meaningsScrollPane = new JScrollPane(addMeaningsArea);

        addMeaningsPanel.add(meaningsLabel, BorderLayout.NORTH);
        addMeaningsPanel.add(meaningsScrollPane, BorderLayout.CENTER);

        addButton = new JButton("Add Word");
        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addWord();
            }
        });

        addPanel.add(addWordPanel, BorderLayout.NORTH);
        addPanel.add(addMeaningsPanel, BorderLayout.CENTER);
        addPanel.add(addButton, BorderLayout.SOUTH);

        // Remove word panel
        JPanel removePanel = new JPanel(new BorderLayout(0, 5));
        removePanel.setBorder(new TitledBorder("Remove Word"));

        JPanel removeWordPanel = new JPanel(new BorderLayout(5, 0));
        JLabel removeWordLabel = new JLabel("Word:");
        removeWordField = new JTextField(20);

        removeWordPanel.add(removeWordLabel, BorderLayout.WEST);
        removeWordPanel.add(removeWordField, BorderLayout.CENTER);

        removeButton = new JButton("Remove Word");
        removeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                removeWord();
            }
        });

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
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Word selection panel
        JPanel wordPanel = new JPanel(new BorderLayout(5, 0));
        JLabel wordLabel = new JLabel("Word:");
        updateWordField = new JTextField(20);
        getMeaningsButton = new JButton("Get Meanings");

        wordPanel.add(wordLabel, BorderLayout.WEST);
        wordPanel.add(updateWordField, BorderLayout.CENTER);
        wordPanel.add(getMeaningsButton, BorderLayout.EAST);

        // Meaning selection panel
        JPanel meaningPanel = new JPanel(new BorderLayout(0, 5));
        meaningPanel.setBorder(new TitledBorder("Update Meaning"));

        JPanel existingMeaningPanel = new JPanel(new BorderLayout(5, 0));
        JLabel existingLabel = new JLabel("Existing Meaning:");
        existingMeaningsCombo = new JComboBox<>();

        existingMeaningPanel.add(existingLabel, BorderLayout.WEST);
        existingMeaningPanel.add(existingMeaningsCombo, BorderLayout.CENTER);

        JPanel newMeaningPanel = new JPanel(new BorderLayout(5, 0));
        JLabel newLabel = new JLabel("New Meaning:");
        newMeaningField = new JTextField(20);

        newMeaningPanel.add(newLabel, BorderLayout.WEST);
        newMeaningPanel.add(newMeaningField, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        updateMeaningButton = new JButton("Update Meaning");
        addMeaningButton = new JButton("Add New Meaning");

        buttonPanel.add(updateMeaningButton);
        buttonPanel.add(addMeaningButton);

        meaningPanel.add(existingMeaningPanel, BorderLayout.NORTH);
        meaningPanel.add(newMeaningPanel, BorderLayout.CENTER);
        meaningPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Add action listeners
        getMeaningsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                getMeanings();
            }
        });

        updateMeaningButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateMeaning();
            }
        });

        addMeaningButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addMeaning();
            }
        });

        panel.add(wordPanel, BorderLayout.NORTH);
        panel.add(meaningPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Performs a search operation.
     */
    private void performSearch() {
        String word = searchWordField.getText().trim();
        if (word.isEmpty()) {
            showMessage("Please enter a word to search");
            return;
        }

        Request request = new Request(Request.OperationType.SEARCH, word);
        Response response = connectionManager.sendRequest(request);

        if (response == null) {
            showError("Failed to get response from server");
            return;
        }

        if (response.getStatus() == Response.StatusCode.SUCCESS) {
            String[] meanings = response.getMeanings();
            StringBuilder result = new StringBuilder();

            for (int i = 0; i < meanings.length; i++) {
                result.append(i + 1).append(". ").append(meanings[i]).append("\n");
            }

            searchResultArea.setText(result.toString());
        } else {
            searchResultArea.setText("No meanings found for \"" + word + "\"");
        }
    }

    /**
     * Adds a new word to the dictionary.
     */
    private void addWord() {
        String word = addWordField.getText().trim();
        if (word.isEmpty()) {
            showMessage("Please enter a word to add");
            return;
        }

        String meaningsText = addMeaningsArea.getText().trim();
        if (meaningsText.isEmpty()) {
            showMessage("Please enter at least one meaning");
            return;
        }

        String[] meanings = meaningsText.split("\n");
        Request request = new Request(Request.OperationType.ADD, word, meanings);
        Response response = connectionManager.sendRequest(request);

        if (response == null) {
            showError("Failed to get response from server");
            return;
        }

        if (response.getStatus() == Response.StatusCode.SUCCESS) {
            showMessage("Word \"" + word + "\" added successfully");
            addWordField.setText("");
            addMeaningsArea.setText("");
        } else {
            showError(response.getMessage());
        }
    }

    /**
     * Removes a word from the dictionary.
     */
    private void removeWord() {
        String word = removeWordField.getText().trim();
        if (word.isEmpty()) {
            showMessage("Please enter a word to remove");
            return;
        }

        Request request = new Request(Request.OperationType.REMOVE, word);
        Response response = connectionManager.sendRequest(request);

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
    }

    /**
     * Gets meanings for a word.
     */
    private void getMeanings() {
        String word = updateWordField.getText().trim();
        if (word.isEmpty()) {
            showMessage("Please enter a word");
            return;
        }

        Request request = new Request(Request.OperationType.SEARCH, word);
        Response response = connectionManager.sendRequest(request);

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
            } else {
                updateMeaningButton.setEnabled(false);
            }
        } else {
            showError("Word not found: " + word);
            updateMeaningButton.setEnabled(false);
        }
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

        Request request = new Request(Request.OperationType.UPDATE_MEANING, word, oldMeaning, newMeaning);
        Response response = connectionManager.sendRequest(request);

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

        Request request = new Request(Request.OperationType.ADD_MEANING, word, newMeaning);
        Response response = connectionManager.sendRequest(request);

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

        if (connected) {
            statusLabel.setText("Connected to server");
            statusLabel.setForeground(new Color(0, 128, 0)); // Dark green
            connectButton.setText("Disconnect");
        } else {
            statusLabel.setText("Not connected");
            statusLabel.setForeground(Color.RED);
            connectButton.setText("Connect");
        }

        searchButton.setEnabled(connected);
        addButton.setEnabled(connected);
        removeButton.setEnabled(connected);
        getMeaningsButton.setEnabled(connected);
        addMeaningButton.setEnabled(connected);
        updateMeaningButton.setEnabled(connected && existingMeaningsCombo.getItemCount() > 0);
    }

    @Override
    public void onConnected() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                updateConnectionStatus();
            }
        });
    }

    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                updateConnectionStatus();
            }
        });
    }

    @Override
    public void onReconnecting() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                statusLabel.setText("Reconnecting...");
                statusLabel.setForeground(Color.ORANGE);
                connectButton.setEnabled(false);
            }
        });
    }

    @Override
    public void onReconnectFailed() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                statusLabel.setText("Reconnection failed");
                statusLabel.setForeground(Color.RED);
                connectButton.setEnabled(true);
                connectButton.setText("Connect");
            }
        });
    }
}