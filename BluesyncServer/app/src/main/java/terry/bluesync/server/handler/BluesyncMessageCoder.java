package terry.bluesync.server.handler;

import android.os.Handler;

import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessageV3;

import terry.bluesync.server.ble.AbstractChannelHandlerContext;
import terry.bluesync.server.ble.ChannelHandlerAdapter;
import terry.bluesync.server.protocol.BluesyncMessage;
import terry.bluesync.server.protocol.BluesyncProto;
import terry.bluesync.server.protocol.BluesyncProtoUtil;
import terry.bluesync.server.util.AesCoder;
import terry.bluesync.server.util.AesCoderException;
import terry.bluesync.server.util.ByteUtil;
import terry.bluesync.server.util.DeviceUtil;
import terry.bluesync.server.util.LogUtil;
import terry.bluesync.server.util.SessionInfo;


public class BluesyncMessageCoder extends ChannelHandlerAdapter {
    private static final String TAG = BluesyncMessageCoder.class.getSimpleName();
    private static final boolean DEBUG = true;
    private static final int FIXED_HEAD_LEN = 8;
    private static final int AUTH_REQ_DELAY = 500;
    private static final int AUTH_TIME_OUT = 4 * 1000;

    private int mSendDataLen;
    private boolean mIsEncrypt;
    private byte[] mSessionKey = null;
    private Handler mHandler;

    private AbstractChannelHandlerContext mCtx;

    enum STEP {
        AUTH,
        INIT,
        READY,
    }
    private STEP mStep;

    public interface Callback {
        void onLoginBegin();
        void onLoginSuccess(BluesyncProto.InitResponse initResponse);
        void onLoginFail(String message);
    }
    private Callback mCallback;

    public BluesyncMessageCoder(int size, boolean isEncrypt, Callback callback) {
        assert (callback != null);

        mHandler = new Handler();
        mSendDataLen = size;
        mIsEncrypt = isEncrypt;

        mCallback = callback;
        setStep(STEP.AUTH);
    }

    @Override
    public void descriptorWrite(final AbstractChannelHandlerContext ctx) throws Exception {

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mCallback.onLoginBegin();
                sendAuthRequest(ctx);
                setStep(STEP.AUTH);

                startAuthTimeout(ctx);
            }
        }, AUTH_REQ_DELAY);
    }

    private void startAuthTimeout(AbstractChannelHandlerContext ctx) {
        mCtx = ctx;
        mHandler.removeCallbacks(mAuthTimeout);
        mHandler.postDelayed(mAuthTimeout, AUTH_TIME_OUT);
    }

    private Runnable mAuthTimeout = new Runnable() {
        @Override
        public void run() {
            if (getStep() != STEP.READY) {
                try {
                    printLog("auth time out");
                    disconnect(mCtx);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private void sendAuthRequest(AbstractChannelHandlerContext ctx) {
        String model = DeviceUtil.getModel();
        String sn = DeviceUtil.getSerialNo();
        ByteString mac = ByteString.copyFromUtf8(DeviceUtil.getBtMacAddressString());
        ByteString aesSign = ByteString.copyFrom(AesCoder.encodeAesSign(DeviceUtil.getSerialNo().getBytes()));

        BluesyncProto.AuthRequest.Builder builder = BluesyncProto.AuthRequest.newBuilder();
        builder.setBaseRequest(BluesyncProto.BaseRequest.newBuilder().build())
                .setModle(model)
                .setSerialNo(sn)
                .setMacAddress(mac)
                .setIsEncrypt(mIsEncrypt)
                .setAesSign(aesSign);

        BluesyncMessage request = new BluesyncMessage(BluesyncProtoUtil.genSeqId(), BluesyncProto.EmCmdId.ECI_req_auth, builder.build());
        printLog("write data=" + request);

        try {
            byte[] bArr = packagePlainData(request);
            ctx.fireWrite(bArr);
        } catch (BluesyncMessageCoderException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void read(AbstractChannelHandlerContext ctx, Object msg) {
        switch (getStep()) {
            case AUTH:
                handleAuthenResponse(ctx, (byte[]) msg);
                break;
            case INIT:
                handleInitResponse(ctx, (byte[]) msg);
                break;
            case READY:
                handleMessage(ctx, (byte[]) msg);
                break;
        }
    }

    private void handleAuthenResponse(AbstractChannelHandlerContext ctx, byte[] bArr) {
        try {
            BluesyncMessage message = parsePlainData(bArr);

            if (!isAuthenCmdId(message.getCmdId())) {
                throw new BluesyncMessageCoderException(message.getSeqId(), BluesyncProto.EmErrorCode.EEC_needAuth_VALUE, "need authen");
            }

            BluesyncProto.AuthResponse authResponse = (BluesyncProto.AuthResponse) message.getProtobufData();
            printLog("handleAuhen, data=" + authResponse.toString());

            if (mIsEncrypt) {
                /** save session key */
                byte[] aesSessionKey = authResponse.getAesSessionKey().toByteArray();
                mSessionKey = AesCoder.decodeAesSessionKey(aesSessionKey);
                if (mSessionKey == null) {
                    throw new BluesyncMessageCoderException(message.getSeqId(), BluesyncProto.EmErrorCode.EEC_authFail_VALUE, "sessionKey error");
                }
            }

            /** generate ticket */
            sendInitRequest(ctx);

            setStep(STEP.INIT);
        } catch (BluesyncMessageCoderException e) {
            LogUtil.e(TAG, e.toString());
            mCallback.onLoginFail(e.toString());
        } catch (AesCoderException e) {
            LogUtil.e(TAG, "");
            mCallback.onLoginFail(e.toString());
        }
    }

    private boolean isAuthenCmdId(BluesyncProto.EmCmdId cmdId) {
        return cmdId == BluesyncProto.EmCmdId.ECI_resp_auth;
    }

    private void sendInitRequest(AbstractChannelHandlerContext ctx) {
        try {
            BluesyncProto.InitRequest.Builder builder = BluesyncProto.InitRequest.newBuilder();
            builder.setBaseRequest(BluesyncProto.BaseRequest.newBuilder().build());

            BluesyncMessage request = new BluesyncMessage(BluesyncProtoUtil.genSeqId(), BluesyncProto.EmCmdId.ECI_req_init, builder.build());
            printLog("write data=" + request);

            byte[] bArr = packageData(request);
            ctx.fireWrite(bArr);
        } catch (Exception e) {
            e.printStackTrace();
            LogUtil.e(TAG, "sendInitRequest error, " + e.toString());
        }
    }

    private void handleInitResponse(AbstractChannelHandlerContext ctx, byte[] bArr) {
        try {
            BluesyncMessage message = parseData(bArr);
            if (!isInitCmdId(message.getCmdId())) {
                throw new BluesyncMessageCoderException(message.getSeqId(), BluesyncProto.EmErrorCode.EEC_needAuth_VALUE, "need authen");
            }

            BluesyncProto.InitResponse initResponse = (BluesyncProto.InitResponse) message.getProtobufData();
            if(initResponse.getBaseResponse().getErrCode() != BluesyncProto.EmErrorCode.EEC_success_VALUE) {
                throw new BluesyncMessageCoderException(message.getSeqId(), BluesyncProto.EmErrorCode.EEC_needAuth_VALUE, initResponse.toString());
            }

            mCallback.onLoginSuccess(initResponse);
            mHandler.removeCallbacks(mAuthTimeout);
            setStep(STEP.READY);
        } catch (BluesyncMessageCoderException e) {
            LogUtil.e(TAG, "handleInitResponse fail, " + e.toString());
            mCallback.onLoginFail(e.toString());
            setStep(STEP.AUTH);
        }
    }

    private boolean isInitCmdId(BluesyncProto.EmCmdId cmdId) {
        return cmdId == BluesyncProto.EmCmdId.ECI_resp_init;
    }

    private void handleMessage(AbstractChannelHandlerContext ctx, byte[] bArr) {
        try {
            BluesyncMessage message = parseData(bArr);
            printLog("receive data=" + message);

            ctx.fireRead(message);
        } catch (BluesyncMessageCoderException exception) {
            handleException(ctx, exception);
        }
    }

    private BluesyncMessage parseData(byte[] bArr) throws BluesyncMessageCoderException {
        if (mIsEncrypt) {
            return parseEncryptedData(bArr);
        } else {
            return parsePlainData(bArr);
        }
    }

    private BluesyncMessage parsePlainData(byte[] bArr) throws BluesyncMessageCoderException {
        int seqId = getSeqId(bArr);
        BluesyncProto.EmCmdId cmdId = getCmdId(seqId, bArr);
        byte[] protobuf = getProtobufByteArray(bArr);

        GeneratedMessageV3 protobufObject = getProtobufObject(seqId, cmdId, protobuf);
        return new BluesyncMessage(seqId, cmdId, protobufObject);
    }

    private BluesyncMessage parseEncryptedData(byte[] bArr) throws BluesyncMessageCoderException {
        int seqId = getSeqId(bArr);
        try {
            BluesyncProto.EmCmdId cmdId = getCmdId(seqId, bArr);

            byte[] encryptedProtobuf = getProtobufByteArray(bArr);
            byte[] protobufData = AesCoder.decrypt(mSessionKey, mSessionKey, encryptedProtobuf);

            GeneratedMessageV3 protobufObject = getProtobufObject(seqId, cmdId, protobufData);
            return new BluesyncMessage(seqId, cmdId, protobufObject);
        } catch (AesCoderException e) {
            e.printStackTrace();
            throw new BluesyncMessageCoderException(seqId, BluesyncProto.EmErrorCode.EEC_decode_VALUE, "aes decode error");
        }
    }

    private BluesyncProto.EmCmdId getCmdId(int seqId, byte[] bArr) throws BluesyncMessageCoderException {
        try {
            int cmdInt = ((bArr[4] & 0xFF) << 8) | (bArr[5] & 0xFF);
            BluesyncProto.EmCmdId cmdId = BluesyncProto.EmCmdId.forNumber(cmdInt);
            cmdId.toString();
            return cmdId;
        } catch (NullPointerException exception) {
            throw new BluesyncMessageCoderException(seqId, BluesyncProto.EmErrorCode.EEC_decode_VALUE, "parse cmdId fail, cmdId=" + ByteUtil.byte2HexString(bArr));
        } catch (IndexOutOfBoundsException exception) {
            throw new BluesyncMessageCoderException(seqId, BluesyncProto.EmErrorCode.EEC_decode_VALUE, "parse cmdId fail, cmdId=" + ByteUtil.byte2HexString(bArr));
        }
    }

    private int getSeqId(byte[] bArr) throws BluesyncMessageCoderException {
        try {
            int seq = ((bArr[6] & 0xFF) << 8) | (bArr[7] & 0xFF);
            return seq;
        } catch (NullPointerException exception) {
            throw new BluesyncMessageCoderException(0, BluesyncProto.EmErrorCode.EEC_decode_VALUE, "parse seq fail, cmdId=" + ByteUtil.byte2HexString(bArr));
        } catch (IndexOutOfBoundsException exception) {
            throw new BluesyncMessageCoderException(0, BluesyncProto.EmErrorCode.EEC_decode_VALUE, "parse seq fail, cmdId=" + ByteUtil.byte2HexString(bArr));
        }
    }

    private byte[] getProtobufByteArray(byte[] bArr) {
        int length = bArr.length - FIXED_HEAD_LEN;
        if (length == 0) {
            return null;
        }

        byte[] protobuf = new byte[length];
        System.arraycopy(bArr, FIXED_HEAD_LEN, protobuf, 0, length);

        return protobuf;
    }

    private GeneratedMessageV3 getProtobufObject(int seqId, BluesyncProto.EmCmdId cmdId, byte[] protobuf) throws BluesyncMessageCoderException {
        try {
            GeneratedMessageV3 retObject = null;
            switch (cmdId) {
                case ECI_error:
                    retObject = BluesyncProto.BaseResponse.parseFrom(protobuf);
                    break;
                case ECI_req_auth:
                    retObject = BluesyncProto.AuthRequest.parseFrom(protobuf);
                    break;
                case ECI_resp_auth:
                    retObject = BluesyncProto.AuthResponse.parseFrom(protobuf);
                    break;
                case ECI_req_init:
                    retObject = BluesyncProto.InitRequest.parseFrom(protobuf);
                    break;
                case ECI_resp_init:
                    retObject = BluesyncProto.InitResponse.parseFrom(protobuf);
                    break;
                case ECI_req_sendData:
                    retObject = BluesyncProto.SendDataRequest.parseFrom(protobuf);
                    break;
                case ECI_resp_sendData:
                    retObject = BluesyncProto.SendDataResponse.parseFrom(protobuf);
                    break;
                case ECI_push_recvData:
                    retObject = BluesyncProto.RecvDataPush.parseFrom(protobuf);
                    break;
                default:
                    printError("parse protobuf fail for unexpected command id.");
                    throw new Exception();
            }

            return retObject;
        } catch (Exception e) {
            throw new BluesyncMessageCoderException(seqId, BluesyncProto.EmErrorCode.EEC_decode_VALUE, "parse protobuf fail.");
        }
    }

    private void handleException(AbstractChannelHandlerContext ctx, BluesyncMessageCoderException exception) {
        BluesyncProto.BaseResponse.Builder builder = BluesyncProto.BaseResponse.newBuilder();
        builder.setErrCode(exception.getErrCode());
        builder.setErrMsg(exception.getMessage());

        BluesyncMessage errResponse = new BluesyncMessage(exception.getSeqId(), BluesyncProto.EmCmdId.ECI_error, builder.build());
        ctx.fireWrite(errResponse);

        LogUtil.e(TAG, exception.toString());
    }

    @Override
    public void write(AbstractChannelHandlerContext ctx, Object msg) throws Exception {
        if (getStep() != STEP.READY) {
            LogUtil.e(TAG, "you want write data must after authentication success");
            return;
        }

        try {
            printLog("write data=" + msg);

            byte[] bArr = packageData((BluesyncMessage) msg);
            ctx.fireWrite(bArr);
        } catch (Exception e) {
            LogUtil.e(TAG, e.toString());
        }
    }

    private byte[] packageData(BluesyncMessage bluesyncMessage) throws BluesyncMessageCoderException {
        if (mIsEncrypt) {
            return packageEncryptData(bluesyncMessage);
        } else {
            return packagePlainData(bluesyncMessage);
        }
    }

    private byte[] packageEncryptData(BluesyncMessage bluesyncMessage) throws BluesyncMessageCoderException {
        int seqId = bluesyncMessage.getSeqId();
        try {
            BluesyncProto.EmCmdId cmdId = bluesyncMessage.getCmdId();

            byte[] protoData = bluesyncMessage.getProtobufData().toByteArray();
            byte[] encryptedProtoData = AesCoder.encrypt(mSessionKey, mSessionKey, protoData);

            byte[] packData = addFixedHeadBuf(seqId, cmdId.getNumber(), encryptedProtoData);
            if (packData.length > mSendDataLen) {
                throw new BluesyncMessageCoderException(seqId, 0, "send data length exceed " + mSendDataLen);
            }
            return packData;
        } catch (Exception e) {
            e.printStackTrace();
            throw new BluesyncMessageCoderException(seqId, 0, "encryptAndPackageData error" + e);
        }
    }

    private byte[] packagePlainData(BluesyncMessage bluesyncMessage) throws BluesyncMessageCoderException {
        BluesyncProto.EmCmdId cmdId = bluesyncMessage.getCmdId();
        int seqId = bluesyncMessage.getSeqId();
        byte[] protoData = bluesyncMessage.getProtobufData().toByteArray();

        byte[] packData = addFixedHeadBuf(seqId, cmdId.getNumber(), protoData);
        if (packData.length > mSendDataLen) {
            throw new BluesyncMessageCoderException(seqId, 0, "send data length exceed " + mSendDataLen);
        }

        return packData;
    }

    private byte[] addFixedHeadBuf(int seqId,int cmdId, byte[] protobuf) {
        int totalLength = FIXED_HEAD_LEN;
        if (protobuf != null && protobuf.length > 0) {
            totalLength = protobuf.length + FIXED_HEAD_LEN;
        }
        byte[] bArr = new byte[totalLength];

        bArr[0] = (byte) -2;
        bArr[1] = (byte) 1;
        bArr[2] = (byte) ((totalLength >> 8) & 255);
        bArr[3] = (byte) (totalLength & 255);
        bArr[4] = (byte) ((cmdId >> 8) & 255);
        bArr[5] = (byte) (cmdId & 255);
        bArr[6] = (byte) ((seqId >> 8) & 255);
        bArr[7] = (byte) (seqId & 255);

        if (totalLength > FIXED_HEAD_LEN) {
            System.arraycopy(protobuf, 0, bArr, 8, protobuf.length);
        }

        return bArr;
    }

    private STEP getStep() {
        return mStep;
    }

    private void setStep(STEP step) {
        mStep = step;
    }

    private void printLog(String message) {
        if (DEBUG) {
            LogUtil.d(TAG, message);
        }
    }

    private void printError(String message) {
        LogUtil.e(TAG, message);
    }
}
