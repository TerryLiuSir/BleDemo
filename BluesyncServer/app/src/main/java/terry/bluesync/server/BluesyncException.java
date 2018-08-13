package terry.bluesync.server;

public class BluesyncException extends Exception {

    /**
     * Creates a new exception.
     */
    public BluesyncException() {
    }

    public BluesyncException(String message) {
        super(message);
    }

    /**
     * Creates a new exception.
     */
    public BluesyncException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new exception.
     */
    public BluesyncException(Throwable cause) {
        super(cause);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + ": " + getMessage();
    }
}