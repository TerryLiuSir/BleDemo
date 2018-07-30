package terry.bluesync.server;


import terry.bluesync.server.protocol.BluesyncMessage;

public interface BluesyncController {
    enum STATE {
        START,  /** server is running, wait for connecting */
        CONNECTED, /** already be connected */
        STOP,   /** server is stopped. */
    }

    interface Listener {
        void onSateChange(STATE state);
        void onReceiveSendData(BluesyncRequest request);
        void onReceivePushData(String data);
    }

     interface ResponseCallback {
        void onSuccess(String data);
        void onError(String message);
    }

    void start();

    void stop();

    void addListener(Listener listener);

    void removeListener(Listener listener);

    STATE getState();

    boolean isConnected();

    void pushData(String data) throws BluesyncException;

    void sendRequest(String data, ResponseCallback callback) throws BluesyncException;
    void sendRequest(String data, ResponseCallback callback, int timeout) throws BluesyncException;

    void sendResponse(int seqId, String data) throws BluesyncException;
}
