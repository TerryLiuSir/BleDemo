package terry.bluesync.client.handler;

public class BluesyncMessageCoderException extends Exception {
    private int seqId;
    private int errCode;

    /**
     * Creates a new exception.
     */
    public BluesyncMessageCoderException(int seqId, int errCode, String message) {
        super(message);
        this.seqId = seqId;
        this.errCode = errCode;
    }

    public int getSeqId() { return seqId; }

    public int getErrCode() {
        return errCode;
    }
}
