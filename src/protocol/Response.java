/**
 * NAME: KAMAL KUMAR KHATRI
 * STUDENT_ID: 1534816
 */
package protocol;

import java.io.Serial;
import java.io.Serializable;

/**
 * Represents a response from the server to the client.
 */
public class Response implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    // Status codes
    public static enum StatusCode {
        SUCCESS,            // Operation completed successfully
        WORD_NOT_FOUND,     // Word not found in dictionary
        DUPLICATE_WORD,     // Word already exists (for ADD operation)
        MEANING_EXISTS,     // Meaning already exists (for ADD_MEANING)
        MEANING_NOT_FOUND,  // Meaning not found (for UPDATE_MEANING)
        ERROR               // General error
    }

    private final StatusCode status;
    private final String word;
    private final String[] meanings;
    private final String message; // Additional error message

    /**
     * Constructor for responses with meanings (like SEARCH results).
     */
    public Response(StatusCode status, String word, String[] meanings) {
        this.status = status;
        this.word = word;
        this.meanings = meanings;
        this.message = null;
    }

    /**
     * Constructor for responses with a message.
     */
    public Response(StatusCode status, String word, String message) {
        this.status = status;
        this.word = word;
        this.meanings = null;
        this.message = message;
    }

    public StatusCode getStatus() {
        return status;
    }

    public String[] getMeanings() {
        return meanings;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Response [status=").append(status)
                .append(", word=").append(word);

        if (meanings != null && meanings.length > 0) {
            sb.append(", meanings=[");
            for (int i = 0; i < meanings.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(meanings[i]);
            }
            sb.append("]");
        }

        if (message != null) {
            sb.append(", message=").append(message);
        }

        sb.append("]");
        return sb.toString();
    }
}