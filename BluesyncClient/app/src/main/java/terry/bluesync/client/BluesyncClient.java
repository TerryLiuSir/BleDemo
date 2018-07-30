package terry.bluesync.client;


import terry.bluesync.client.protocol.BluesyncMessage;

public abstract class BluesyncClient {
    public enum STATE {
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED,
    }

    public interface Listener {
        void onSateChange(STATE state);
        void onReceive(BluesyncMessage message);
    }

    public interface ConnectionCallback {
        void onSuccess();
        void onFailure(String msg);
    }

    public interface DisconnectionCallback {
        void onFinish();
    }

    public interface ResponseCallback {
        void onSuccess(BluesyncMessage protobufData);
        void onError(String message);
    }

    public boolean isConnected() {
        return getState() == STATE.CONNECTED;
    }

    abstract public void addListener(Listener listener);

    abstract public void removeListener(Listener listener);

    abstract public void connect(String address, ConnectionCallback callback);

    abstract public void disconnect(DisconnectionCallback callback);

    abstract public void pushData(BluesyncMessage message) throws BluesyncException;

    abstract public void sendRequest(BluesyncMessage message, ResponseCallback callback) throws BluesyncException;
    abstract public void sendRequest(BluesyncMessage message, ResponseCallback callback, int timeout) throws BluesyncException;

    abstract public String getAddress();

    abstract public STATE getState();
}
