package terry.bluesync.server.handler;

public class FrameDecodeException extends RuntimeException {

    private static final long serialVersionUID = 2908618315971075004L;

    /**
     * Creates a new exception.
     */
    public FrameDecodeException() {
    }

    /**
     * Creates a new exception.
     */
    public FrameDecodeException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new exception.
     */
    public FrameDecodeException(String message) {
        super(message);
    }

    /**
     * Creates a new exception.
     */
    public FrameDecodeException(Throwable cause) {
        super(cause);
    }
}
