package util;

/**
 * Contains constants used throughout the application.
 */
public class Constants {
    // Default values
    public static final int DEFAULT_PORT = 1234;
    public static final String DEFAULT_SERVER_ADDRESS = "localhost";
    public static final String DEFAULT_DICTIONARY_FILE = "dictionary.txt";

    // Thread pool settings
    public static final int CORE_POOL_SIZE = 5;
    public static final int MAX_POOL_SIZE = 100;
    public static final int KEEP_ALIVE_TIME = 60; // seconds

    // Connection settings
    public static final int CONNECTION_TIMEOUT = 5000; // milliseconds
    public static final int MAX_RECONNECT_ATTEMPTS = 5;
    public static final int RECONNECT_DELAY = 2000; // milliseconds
    public  static final int TOAST_COOLDOWN_MS = 5000; //milliseconds
    // Dictionary file format
    public static final String WORD_DELIMITER = "\\|";  // Pipe character in regex
    public static final String WORD_SEPARATOR = "|";    // Pipe character

    // GUI constants
    public static final int WINDOW_WIDTH = 800;
    public static final int WINDOW_HEIGHT = 600;
    public static final String APP_TITLE = "Dictionary Application";

    // Error messages
    public static final String ERR_SERVER_CONNECTION = "Failed to connect to server";
    public static final String ERR_DICTIONARY_FILE = "Failed to load dictionary file";
    public static final String ERR_INVALID_INPUT = "Invalid input parameters";

    private Constants() {
        // Private constructor to prevent instantiation
    }
}