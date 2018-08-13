package terry.bluesync.client;

public abstract class BluesyncClient {
    public enum STATE {
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED,
    }

    public interface Listener {
        void onSateChange(STATE state);
        void onReceiveSendData(BluesyncRequest request);
        void onReceivePushData(String data);
    }

    public interface ConnectionCallback {
        void onSuccess();
        void onFailure(String message);
    }

    public interface DisconnectionCallback {
        void onFinish();
    }

    public interface ResponseCallback {
        void onSuccess(String data);
        void onError(String message);
    }

    abstract public STATE getState();

    public boolean isConnected() {
        return getState() == STATE.CONNECTED;
    }

    abstract public String getAddress();

    abstract public void addListener(Listener listener);

    abstract public void removeListener(Listener listener);

    abstract public void connect(String address, ConnectionCallback callback);
    abstract public void disconnect(DisconnectionCallback callback);

    abstract public void pushData(String data) throws BluesyncException;

    abstract public void sendRequest(String data, ResponseCallback callback) throws BluesyncException;
    abstract public void sendRequest(String data, ResponseCallback callback, int timeout) throws BluesyncException;

    abstract void sendResponse(int seqId, String data) throws BluesyncException;
}
