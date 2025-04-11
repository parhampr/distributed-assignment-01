package util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Simple logging utility for the application.
 */
public class Logger {
    // Log levels
    public static enum Level {
        INFO,
        WARNING,
        ERROR,
        DEBUG
    }

    /**
     * Interface for log message handlers.
     */
    public interface LogHandler {
        /**
         * Handles a log message.
         *
         * @param level the log level
         * @param message the log message
         */
        void handleLog(Level level, String message);
    }

    private static final String LOG_FORMAT = "[%s] %s: %s%n";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static PrintWriter logWriter;
    private static Level currentLevel = Level.INFO;
    private static boolean consoleOutput = true;
    private static LogHandler logHandler = null;

    /**
     * Initialize the logger with a file to write logs to.
     *
     * @param logFile the file to write logs to
     * @throws IOException if the file cannot be opened for writing
     */
    public static void init(String logFile) throws IOException {
        if (logWriter != null) {
            logWriter.close();
        }

        File file = new File(logFile);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        if (!file.exists()) {
            file.createNewFile();
        }

        logWriter = new PrintWriter(new FileWriter(file, true), true);
    }

    /**
     * Set the current log level.
     *
     * @param level the log level to set
     */
    public static void setLevel(Level level) {
        currentLevel = level;
    }

    /**
     * Set whether to output logs to the console.
     *
     * @param enable true to enable console output, false to disable
     */
    public static void setConsoleOutput(boolean enable) {
        consoleOutput = enable;
    }

    /**
     * Set a log handler to receive log messages.
     *
     * @param handler the log handler to set
     */
    public static void setLogHandler(LogHandler handler) {
        logHandler = handler;
    }

    /**
     * Log a message at the INFO level.
     *
     * @param message the message to log
     */
    public static void info(String message) {
        log(Level.INFO, message);
    }

    /**
     * Log a message at the WARNING level.
     *
     * @param message the message to log
     */
    public static void warning(String message) {
        log(Level.WARNING, message);
    }

    /**
     * Log a message at the ERROR level.
     *
     * @param message the message to log
     */
    public static void error(String message) {
        log(Level.ERROR, message);
    }

    /**
     * Log a message at the ERROR level with an exception.
     *
     * @param message the message to log
     * @param e the exception to log
     */
    public static void error(String message, Exception e) {
        log(Level.ERROR, message + ": " + e.getMessage());
        if (currentLevel == Level.DEBUG) {
            e.printStackTrace();
        }
    }

    /**
     * Log a message at the DEBUG level.
     *
     * @param message the message to log
     */
    public static void debug(String message) {
        log(Level.DEBUG, message);
    }

    /**
     * Log a message at the specified level.
     *
     * @param level the log level
     * @param message the message to log
     */
    private static void log(Level level, String message) {
        if (level.ordinal() < currentLevel.ordinal()) {
            return;
        }

        String formattedMessage = String.format(LOG_FORMAT,
                DATE_FORMAT.format(new Date()), level, message);

        if (consoleOutput) {
            if (level == Level.ERROR) {
                System.err.print(formattedMessage);
            } else {
                System.out.print(formattedMessage);
            }
        }

        if (logWriter != null) {
            logWriter.print(formattedMessage);
            logWriter.flush();
        }

        if (logHandler != null) {
            logHandler.handleLog(level, message);
        }
    }

    /**
     * Close the logger and release resources.
     */
    public static void close() {
        if (logWriter != null) {
            logWriter.close();
            logWriter = null;
        }
    }
}