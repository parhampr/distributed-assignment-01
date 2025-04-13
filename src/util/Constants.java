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
    public static final int MAX_POOL_SIZE = 100;

    // Connection settings
    public static final int CONNECTION_TIMEOUT = 3000; // milliseconds
    public  static final int TOAST_COOLDOWN_MS = 5000; //milliseconds
    public static final int MAX_DELAY_MS = 10000; // milliseconds
    // Dictionary file format
    public static final String MEANING_DELIMITER = "    ";  // Pipe character in regex

    // GUI constants
    public static final String APP_TITLE = "Dictionary Application";

    // Error messages
    public static final String ERR_SERVER_CONNECTION = "Failed to connect to server";
    public static final String ERR_DICTIONARY_FILE = "Failed to load dictionary file";
    public static final String ERR_INVALID_INPUT = "Invalid input parameters";

    private Constants() {
        // Private constructor to prevent instantiation
    }
}