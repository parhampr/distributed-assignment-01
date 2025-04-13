package protocol;

import java.io.Serial;
import java.io.Serializable;

/**
 * Represents a request from the client to the server.
 */
public class Request implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public enum OperationType {
        SEARCH,         // Look up meanings of a word
        ADD,            // Add a new word with meanings
        REMOVE,         // Remove a word
        ADD_MEANING,    // Add a meaning to an existing word
        UPDATE_MEANING, // Update a meaning of an existing word
        HEARTBEAT       // Connection keep-alive check
    }

    private final OperationType operation;
    private final String word;
    private final String[] meanings;
    private final String oldMeaning;

    /**
     * Constructor for SEARCH, ADD, REMOVE, and ADD_MEANING operations.
     */
    public Request(OperationType operation, String word, String... meanings) {
        this.operation = operation;
        this.word = word;
        this.meanings = meanings;
        this.oldMeaning = null;
    }

    /**
     * Constructor for UPDATE_MEANING operation.
     */
    public Request(OperationType operation, String word, String oldMeaning, String newMeaning) {
        this.operation = operation;
        this.word = word;
        this.meanings = new String[]{newMeaning};
        this.oldMeaning = oldMeaning;
    }

    public OperationType getOperation() {
        return operation;
    }

    public String getWord() {
        return word.toLowerCase();
    }

    public String[] getMeanings() {
        return meanings;
    }

    public String getOldMeaning() {
        return oldMeaning;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Request [operation=").append(operation)
                .append(", word=").append(getWord());

        if (meanings != null && meanings.length > 0) {
            sb.append(", meanings=[");
            for (int i = 0; i < meanings.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(meanings[i]);
            }
            sb.append("]");
        }

        if (oldMeaning != null) {
            sb.append(", oldMeaning=").append(oldMeaning);
        }

        sb.append("]");
        return sb.toString();
    }
}