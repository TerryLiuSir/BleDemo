package terry.bluesync.server;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import terry.bluesync.server.ble.AbstractChannelHandlerContext;
import terry.bluesync.server.ble.BleController;
import terry.bluesync.server.ble.Channel;
import terry.bluesync.server.ble.ChannelHandlerAdapter;
import terry.bluesync.server.ble.ChannelPipeline;
import terry.bluesync.server.handler.BluesyncMessageCoder;
import terry.bluesync.server.handler.LengthFieldFrameDecoder;
import terry.bluesync.server.protocol.BluesyncMessage;
import terry.bluesync.server.protocol.BluesyncProto;
import terry.bluesync.server.protocol.BluesyncProtoUtil;

import static terry.bluesync.server.protocol.BluesyncProto.*;

public class BluesyncControllerImpl implements BluesyncController {
    private static final String TAG = BluesyncController.class.getSimpleName();
    private static final boolean DEBUG = true;
    private static final int MAX_DATA_LENGTH = 4 * 1024;
    private static final int DEFAULT_TIMEOUT = 5 * 1000;

    private Context mContext;
    private STATE mState;

    private BleController mBleController;
    private Channel mActiveChannel;
    private List<Listener> mListeners;
    private SparseArray<ResponseHolder> mResponseHolderMap;

    public BluesyncControllerImpl(Context context) {
        mContext = context;
        mState = STATE.STOP;

        mBleController = new BleController(mContext);
        mBleController.registerChannelInitializer(mChannelInitializer);

        mListeners = new LinkedList<>();
    }

    @Override
    public void start() {
        if (getState() != STATE.STOP) {
            return;
        }
        boolean success = mBleController.start();
        printLog("BleController start success=" + success);

        setState(STATE.START);

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        mContext.registerReceiver(mBluetoothStateReceiver, filter);
    }

    @Override
    public void stop() {
        if (getState() == STATE.STOP) {
            return;
        }

        mActiveChannel = null;
        setState(STATE.STOP);

        mBleController.stop();

        clearResponseHolder();
        mContext.unregisterReceiver(mBluetoothStateReceiver);
    }

    @Override
    public void addListener(Listener listener) {
        assert (listener != null);
        mListeners.add(listener);
    }

    @Override
    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }

    private void setState(STATE state) {
        if (state == mState) {
            return;
        }
        mState = state;

        for (Listener listener : mListeners) {
            listener.onSateChange(state);
        }

        printLog("state is " + state);
    }

    @Override
    public STATE getState() {
        return mState;
    }

    @Override
    public boolean isConnected() {
        return getState() == STATE.CONNECTED;
    }

    @Override
    public void pushData(String data) throws BluesyncException {
        if (data == null) {
            throw new BluesyncException("push data fail, data can not be null");
        }

        if (!isConnected()) {
            throw new BluesyncException("push data fail, bluesync is already disconnected.");
        }

        BluesyncMessage request = BluesyncProtoUtil.getRecvDataPush(data.getBytes());
        mActiveChannel.write(request);
    }

    @Override
    public void sendRequest(String data, ResponseCallback callback) throws BluesyncException {
        sendRequest(data, callback, DEFAULT_TIMEOUT);
    }

    @Override
    public void sendRequest(String data, ResponseCallback callback, int timeout) throws BluesyncException {
        if (data == null) {
            throw new BluesyncException("send request fail, data can not be null");
        }

        if (!isConnected()) {
            throw new BluesyncException("send request fail, bluesync has already disconnected.");
        }

        BluesyncMessage request = BluesyncProtoUtil.getSendDataRequest(data.getBytes());
        addResponseHolder(request.getSeqId(), callback, timeout);
        mActiveChannel.write(request);
    }

    @Override
    public void sendResponse(int seqId, String data) throws BluesyncException {
        if (data == null) {
            throw new BluesyncException("send response fail, data can not be null");
        }

        if (!isConnected()) {
            throw new BluesyncException("send response fail, bluesync has already disconnected.");
        }

        BluesyncMessage request = BluesyncProtoUtil.getSendDataResponse(seqId, data.getBytes());
        mActiveChannel.write(request);
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
                callback.onSuccess(response.getData().toString());
            } else {
                callback.onError(protobufData.toString());
            }
        }

        public void discard() {
            timer.cancel();
            callback.onError("bluesync disconnected");
        }
    }

    private BleController.ChannelInitializer mChannelInitializer = new BleController.ChannelInitializer() {

        @Override
        public void initChannel(Channel ch) {
            ChannelPipeline pipeline = ch.channelPipeline();

            pipeline.addLast("frameDecoder", new LengthFieldFrameDecoder(MAX_DATA_LENGTH))
                    .addLast("messageCoder", new BluesyncMessageCoder(MAX_DATA_LENGTH, true, new LoginCallback()))
                    .addLast("messageHandler", new BluesycnMessageHandler());
        }
    };

    private class LoginCallback implements BluesyncMessageCoder.Callback {

        @Override
        public void onLoginBegin() {
            printLog("login start");

            setState(STATE.START);
        }

        @Override
        public void onLoginSuccess(InitResponse initResponse) {
            printLog("login success, ticket=" + initResponse);

            setState(STATE.CONNECTED);
        }

        @Override
        public void onLoginFail(String message) {
            printError("login fail, message=" + message);

            setState(STATE.START);
        }
    }

    private class BluesycnMessageHandler extends ChannelHandlerAdapter {
        @Override
        public void active(AbstractChannelHandlerContext ctx) throws Exception {
            Channel newChannel = ctx.channel();
            if (mActiveChannel != null && mActiveChannel != newChannel) {
                mActiveChannel.disconnect();
            }

            mActiveChannel = newChannel;
            setState(STATE.CONNECTED);
        }

        @Override
        public void inactive(AbstractChannelHandlerContext ctx) throws Exception {
            Channel newChannel = ctx.channel();
            if (mActiveChannel == newChannel) {
                mActiveChannel = null;
                setState(STATE.START);
            }
        }

        @Override
        public void read(AbstractChannelHandlerContext ctx, Object msg) throws Exception {
            BluesyncMessage protobufData = (BluesyncMessage) msg;

            switch (protobufData.getCmdId()) {
                case ECI_error:
                    BaseResponse errResponse = (BaseResponse) protobufData.getProtobufData();
                    printError("receive data error =" + errResponse.toString());

                    break;
                case ECI_req_sendData:
                    SendDataRequest sendRequest = (SendDataRequest) protobufData.getProtobufData();
                    BluesyncRequest bluesyncRequest = new BluesyncRequest(BluesyncControllerImpl.this, protobufData.getSeqId(), sendRequest.getData().toString());

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
                        listener.onReceivePushData(dataPush.getData().toString());
                    }
                    break;
            }
        }
    }

    private BroadcastReceiver mBluetoothStateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            switch (state) {
                case BluetoothAdapter.STATE_OFF:
                    printLog("Bluetooth turn off");

                    mBleController.stop();
                    mActiveChannel = null;
                    setState(STATE.START);
                    break;
                case BluetoothAdapter.STATE_ON:
                    printLog("Bluetooth turn on");

                    mBleController.start();
                    break;
            }
        }
    };

    private void printLog(String message) {
        if (DEBUG) {
            Log.d(TAG, message);
        }
    }

    private void printError(String message) {
        Log.e(TAG, message);
    }
}
