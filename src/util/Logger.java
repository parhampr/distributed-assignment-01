/**
 * NAME: KAMAL KUMAR KHATRI
 * STUDENT_ID: 1534816
 */
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
    public static enum Level {
        ERROR,
        WARNING,
        INFO,
        DEBUG
    }

    /**
     * Interface for log message handlers.
     */
    public interface LogHandler {
        void handleLog(Level level, String message);
    }

    private static final String LOG_FORMAT = "[%s] %s: %s%n";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static PrintWriter logWriter;
    private static Level currentLevel = Level.INFO;
    private static boolean consoleOutput = true;
    private static LogHandler logHandler = null;

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

    public static void setLevel(Level level) {
        currentLevel = level;
    }

    public static void setConsoleOutput(boolean enable) {
        consoleOutput = enable;
    }

    public static void setLogHandler(LogHandler handler) {
        logHandler = handler;
    }

    public static void info(String message) {
        log(Level.INFO, message);
    }

    public static void warning(String message) {
        log(Level.WARNING, message);
    }

    public static void error(String message) {
        log(Level.ERROR, message);
    }

    public static void error(String message, Exception e) {
        log(Level.ERROR, message + ": " + e.getMessage());
        if (currentLevel == Level.DEBUG) {
            e.printStackTrace();
        }
    }

    public static void debug(String message) {
        log(Level.DEBUG, message);
    }

    private static void log(Level level, String message) {
        if (level.ordinal() > currentLevel.ordinal()) {
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

    public static void close() {
        if (logWriter != null) {
            logWriter.close();
            logWriter = null;
        }
    }
}