package terry.bluesync.server;

public class BluesyncRequest {

    private BluesyncController controller;
    private int seqId;
    private String data;

    public BluesyncRequest(BluesyncController controller, int seqId, String data) {
        this.controller = controller;
        this.seqId = seqId;
        this.data = data;
    }

    public int getSeqId() {
        return seqId;
    }

    public String getData() {
        return data;
    }

    public void sendResponse(String reponseData) throws BluesyncException {
        controller.sendResponse(this.seqId, reponseData);
    }

    @Override
    public String toString() {
        return "seqId=" + seqId + ", data=" + data;
    }
}
