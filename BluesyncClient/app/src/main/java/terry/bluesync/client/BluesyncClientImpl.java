package terry.bluesync.client;

import android.content.Context;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import terry.bluesync.client.ble.AbstractChannelHandlerContext;
import terry.bluesync.client.ble.BleClient;
import terry.bluesync.client.ble.BleClient.ChannelInitializer;
import terry.bluesync.client.ble.Channel;
import terry.bluesync.client.ble.ChannelHandlerAdapter;
import terry.bluesync.client.ble.ChannelPipeline;
import terry.bluesync.client.handler.BluesyncMessageCoder;
import terry.bluesync.client.handler.LengthFieldFrameDecoder;
import terry.bluesync.client.protocol.BluesyncMessage;
import terry.bluesync.client.protocol.BluesyncProto;
import terry.bluesync.client.protocol.BluesyncProtoUtil;

import static terry.bluesync.client.protocol.BluesyncProto.*;

public class BluesyncClientImpl extends BluesyncClient {
    private static final String TAG = BluesyncClient.class.getSimpleName();
    private static final boolean DEBUG = true;
    private static final int MAX_DATA_LENGTH = 4 * 1024;
    private static final int DEFAULT_TIMEOUT = 5 * 1000;

    private Context mContext;
    private STATE mState;
    private String mAddress;

    private BleClient mBleClient;
    private Channel mChannel;
    private List<Listener> mListeners;

    private ConnectionCallback mConnectionCallback;
    private DisconnectionCallback mDisconnectionCallback;
    private SparseArray<ResponseHolder> mResponseHolderMap;

    public BluesyncClientImpl(Context context) {
        mContext = context;
        mState = STATE.DISCONNECTED;

        mBleClient = new BleClient(mContext);
        mListeners = new LinkedList<>();
        mResponseHolderMap = new SparseArray<>();
    }

    @Override
    public void addListener(Listener listener) {
        assert (listener != null);
        printLog("addListener");

        mListeners.add(listener);
    }

    @Override
    public void removeListener(Listener listener) {
        printLog("removeListener");
        mListeners.remove(listener);
    }

    @Override
    public void connect(String address, ConnectionCallback callback) {
        if (callback == null) {
            mConnectionCallback = new DummyConnectionCallback();
        } else {
            mConnectionCallback = callback;
        }

        if (getState() == STATE.DISCONNECTED) {
            setState(STATE.CONNECTING);
            mAddress = address;

            boolean execute = mBleClient.connect(address, mChannelInitializer);
            if (!execute) {
                mConnectionCallback.onFailure("bluetooth isn't open");
                setState(STATE.DISCONNECTED);
            }
        } else {
            mConnectionCallback.onFailure("bluesync already connected, please reconnect after disconnected");
        }
    }

    @Override
    public void disconnect(DisconnectionCallback callback) {
        if (callback == null) {
            mDisconnectionCallback = new DummyDisconnectionCallback();
        } else {
            mDisconnectionCallback = callback;
        }

        if (getState() == STATE.DISCONNECTED) {
            mDisconnectionCallback.onFinish();
            return;
        }

        setState(STATE.DISCONNECTING);

        clearResponseHolder();
        mBleClient.disconnect();
    }

    @Override
    public void pushData(String data) throws BluesyncException {
        if (data == null) {
            throw new BluesyncException("push data fail, data can not be null");
        }

        if (getState() != STATE.CONNECTED) {
            throw new BluesyncException("send request fail, bluesync is already disconnected.");
        }

        BluesyncMessage dataPack = BluesyncProtoUtil.getRecvDataPush(data.getBytes());
        mChannel.write(dataPack);
    }

    @Override
    public void sendRequest(String data, ResponseCallback callback) throws BluesyncException {
        this.sendRequest(data, callback, DEFAULT_TIMEOUT);
    }

    @Override
    public void sendRequest(String data, ResponseCallback callback, int timeout) throws BluesyncException {
        if (callback == null ) {
            throw new BluesyncException("send request fail, callback can not be null");
        }

        if (data == null) {
            throw new BluesyncException("send request fail, data can not be null");
        }

        if (getState() != STATE.CONNECTED) {
            throw new BluesyncException("send request fail, bluesync is already disconnected.");
        }

        BluesyncMessage dataPack = BluesyncProtoUtil.getSendDataRequest(data.getBytes());
        addResponseHolder(dataPack.getSeqId(), callback, timeout);
        mChannel.write(dataPack);
    }

    @Override
    public void sendResponse(int seqId, String data) throws BluesyncException {
        if (data == null) {
            throw new BluesyncException("send response fail, data can not be null");
        }

        if (!isConnected()) {
            throw new BluesyncException("send response fail, bluesync has already disconnected.");
        }

        BluesyncMessage dataPack = BluesyncProtoUtil.getSendDataResponse(seqId, data.getBytes());
        mChannel.write(dataPack);
    }

    @Override
    public String getAddress() {
        return mAddress;
    }

    @Override
    public STATE getState() {
        return mState;
    }

    private void setState(STATE newState) {
        if (newState == mState) {
            return;
        }
        mState = newState;

        for(Listener listener: mListeners) {
            listener.onSateChange(newState);
        }

        printLog("state is " + mState);
    }

    private ChannelInitializer mChannelInitializer = new ChannelInitializer() {

        @Override
        public void initChannel(Channel channel, boolean isSuccess, String errMsg) {
            if (!isSuccess) {
                setState(STATE.DISCONNECTED);
                mConnectionCallback.onFailure(errMsg);
                printError("connect fail, errMsg=" + errMsg);
                return;
            }

            mChannel = channel;
            ChannelPipeline pipeline = mChannel.channelPipeline();
            pipeline.addLast("frameDecoder", new LengthFieldFrameDecoder(MAX_DATA_LENGTH))
                    .addLast("messageCoder", new BluesyncMessageCoder(MAX_DATA_LENGTH,false, new LoginCallback()))
                    .addLast("messageHandler", new BluesycnMessageHandler());
        }

        @Override
        public void initFail() {
            setState(STATE.DISCONNECTED);
            mConnectionCallback.onFailure("channel init failure");
        }
    };

    private class LoginCallback implements BluesyncMessageCoder.Callback {

        @Override
        public void onLoginSuccess() {
            printLog("login success");
            setState(STATE.CONNECTED);

            mConnectionCallback.onSuccess();
        }

        @Override
        public void onLoginFail(String message) {
            printError("login fail, message=" + message);
            mBleClient.disconnect();
        }
    }

    private class BluesycnMessageHandler extends ChannelHandlerAdapter {
        @Override
        public void inactive(AbstractChannelHandlerContext ctx) throws Exception {
            STATE oldState = getState();
            setState(STATE.DISCONNECTED);

            if (oldState == STATE.CONNECTING) {
                mConnectionCallback.onFailure("login fail");
            }

            if (oldState == STATE.DISCONNECTING) {
                mDisconnectionCallback.onFinish();
            }
        }

        @Override
        public void read(AbstractChannelHandlerContext ctx, Object msg) throws Exception {
            BluesyncMessage protobufData = (BluesyncMessage) msg;
            String dataStr;

            switch (protobufData.getCmdId()) {
                case ECI_error:
                    BaseResponse errResponse = (BaseResponse) protobufData.getProtobufData();
                    printError("receive data error =" + errResponse.toString());

                    break;
                case ECI_req_sendData:
                    SendDataRequest sendRequest = (SendDataRequest) protobufData.getProtobufData();
                    dataStr = new String(sendRequest.getData().toByteArray());
                    BluesyncRequest bluesyncRequest = new BluesyncRequest(BluesyncClientImpl.this, protobufData.getSeqId(), dataStr);

                    for (Listener listener : mListeners) {
                        listener.onReceiveSendData(bluesyncRequest);
                    }
                    break;
                case ECI_resp_sendData:
                    consumeResponseHolder(protobufData);
                    break;
                case ECI_push_recvData:
                    RecvDataPush dataPush = (RecvDataPush) protobufData.getProtobufData();
                    for (Listener listener : mListeners) {
                        dataStr = new String(dataPush.getData().toByteArray());
                        listener.onReceivePushData(dataStr);
                    }
                    break;
            }
        }
    }

    private void printLog(String message) {
        if (DEBUG) {
            Log.d(TAG, message);
        }
    }

    private void printError(String message) {
        Log.e(TAG, message);
    }

    private class DummyConnectionCallback implements ConnectionCallback {
        @Override
        public void onSuccess() {}

        @Override
        public void onFailure(String msg) {}
    }

    private class DummyDisconnectionCallback implements DisconnectionCallback {

        @Override
        public void onFinish() {

        }
    }

    private synchronized void addResponseHolder(int seqId, ResponseCallback callback, int timeout) {
        ResponseHolder holder = new ResponseHolder(seqId, callback, timeout);
        mResponseHolderMap.put(seqId, holder);
    }

    private synchronized void clearResponseHolder() {
        List<ResponseHolder> holderList = new ArrayList();
        for(int i = 0; i < mResponseHolderMap.size(); i++){
            int key = mResponseHolderMap.keyAt(i);
            ResponseHolder holder = mResponseHolderMap.get(key);
            holderList.add(holder);
        }
        mResponseHolderMap.clear();

        for (ResponseHolder response: holderList) {
            response.discard();
        }
    }

    private synchronized void removeResponseCallback(int seqId) {
        mResponseHolderMap.delete(seqId);
    }

    private synchronized boolean consumeResponseHolder(BluesyncMessage protobufData) {
        int seqId = protobufData.getSeqId();
        ResponseHolder holder = mResponseHolderMap.get(seqId);
        if (holder != null) {
            mResponseHolderMap.delete(seqId);
            holder.handleResponse(protobufData);
            return true;
        } else {
            return false;
        }
    }

    private class ResponseHolder {
        private int seqId;
        private ResponseCallback callback;
        private Timer timer;

        ResponseHolder(int seqId, ResponseCallback callback, int timeout) {
            this.seqId = seqId;
            this.callback = callback;

            timer = new Timer();
            timer.schedule(new TimeoutTask(), timeout);
        }

        private class TimeoutTask extends TimerTask {

            @Override
            public void run() {
                printLog("response timeout, seqId=" + seqId);
                removeResponseCallback(seqId);

                callback.onError("response timeout");
            }
        }

        public void handleResponse(BluesyncMessage protobufData) {
            timer.cancel();

            SendDataResponse response = (SendDataResponse) protobufData.getProtobufData();
            EmCmdId cmdId = protobufData.getCmdId();

            if (cmdId != EmCmdId.ECI_error) {
                String dataStr = new String(response.getData().toByteArray());
                callback.onSuccess(dataStr);
            } else {
                callback.onError(protobufData.toString());
            }
        }

        public void discard() {
            timer.cancel();
            callback.onError("bluesync disconnected");
        }
    }

}
