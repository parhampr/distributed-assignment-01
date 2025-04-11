package protocol;

import java.io.Serial;
import java.io.Serializable;

/**
 * Base class for all messages exchanged between client and server.
 */
public abstract class Message implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public static enum MessageType {
        REQUEST,
        RESPONSE
    }

    private final MessageType type;

    public Message(MessageType type) {
        this.type = type;
    }

    public MessageType getType() {
        return type;
    }
}