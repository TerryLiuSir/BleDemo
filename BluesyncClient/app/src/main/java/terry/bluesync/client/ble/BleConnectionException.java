package terry.bluesync.client.ble;

public class BleConnectionException extends Exception {
    private static final long serialVersionUID = 6926716840699621852L;

    /**
     * Creates a new instance.
     */
    public BleConnectionException() {
    }

    /**
     * Creates a new instance.
     */
    public BleConnectionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new instance.
     */
    public BleConnectionException(String message) {
        super(message);
    }

    /**
     * Creates a new instance.
     */
    public BleConnectionException(Throwable cause) {
        super(cause);
    }
}
