package terry.bluesync.client;

public class BluesyncRequest {

    private BluesyncClient client;
    private int seqId;
    private String data;

    public BluesyncRequest(BluesyncClient client, int seqId, String data) {
        this.client = client;
        this.seqId = seqId;
        this.data = data;
    }

    public int getSeqId() {
        return seqId;
    }

    public String getData() {
        return data;
    }

    public void sendResponse(String data) throws BluesyncException {
        client.sendResponse(this.seqId, data);
    }

    @Override
    public String toString() {
        return "seqId=" + seqId + ", data=" + data;
    }
}
