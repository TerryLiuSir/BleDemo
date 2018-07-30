package terry.bluesync.server.handler;


import android.os.Handler;

import terry.bluesync.server.ble.AbstractChannelHandlerContext;
import terry.bluesync.server.ble.ChannelHandlerAdapter;
import terry.bluesync.server.protocol.BluesyncMessage;
import terry.bluesync.server.protocol.BluesyncProto;
import terry.bluesync.server.util.ByteUtil;
import terry.bluesync.server.util.LogUtil;

public class LengthFieldFrameDecoder extends ChannelHandlerAdapter {
    private static final String TAG = LengthFieldFrameDecoder.class.getSimpleName();
    private static final boolean DEBUG = true;
    private static final int READ_TIMEOUT = 1 * 1000;

    protected byte[] mRecvBuf;
    protected int mRecvBufSize;
    protected int mRecvDataLen;
    protected int mRecvOffset;
    protected Handler mHandler;

    Runnable mTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            printLog("read timeout");
            resetRecv();
        }
    };

    public LengthFieldFrameDecoder(int size) {
        mRecvBuf = new byte[size];
        mRecvBufSize = size;
        mRecvDataLen = 0;
        mRecvOffset = 0;
        mHandler = new Handler();
    }

    @Override
    public void descriptorWrite(AbstractChannelHandlerContext ctx) throws Exception {
        resetRecv();
        ctx.fireDescriptorWrite();
    }

    @Override
    public void read(AbstractChannelHandlerContext ctx, Object msg) throws Exception {
        byte[] bArr = (byte[]) msg;
        int size = ((byte[]) msg).length;

        printLog("read bytes=" + ByteUtil.byteArray2HexString(bArr, size));

        try {
            System.arraycopy(bArr, 0, mRecvBuf, mRecvOffset, size);
            mRecvOffset += size;

            if (mRecvOffset > 8 && mRecvDataLen == 0) {
                mRecvDataLen = getFrameLength(mRecvBuf, size);
                printLog("read frame total length=" + mRecvDataLen);
            }

            if (isReadEnd()) {
                byte[] readBuf = new byte[mRecvDataLen];
                System.arraycopy(mRecvBuf, 0, readBuf, 0, mRecvDataLen);

                printLog("read end, bytes=" + ByteUtil.byte2HexString(readBuf));

                ctx.fireRead(readBuf);
                resetRecv();

                mHandler.removeCallbacks(mTimeoutRunnable);
            } else {
                mHandler.removeCallbacks(mTimeoutRunnable);
                mHandler.postDelayed(mTimeoutRunnable, READ_TIMEOUT);
            }
        } catch (ArrayIndexOutOfBoundsException outOfBoundsException) {
            handleException(ctx, "array out of bounds", outOfBoundsException);
        } catch (FrameDecodeException decodeException) {
            handleException(ctx, decodeException.getMessage(), decodeException);
        } catch (Exception exception) {
            handleException(ctx, "inner exception", exception);
        }
    }

    private int getFrameLength(byte[] buffer, int size) {
        StringBuilder stringBuilder = new StringBuilder(2);
        stringBuilder.append(String.format("%02X", new Object[]{Byte.valueOf(buffer[0])}));
        stringBuilder.append(String.format("%02X", new Object[]{Byte.valueOf(buffer[1])}));

        if (buffer[0] == (byte) -2 && buffer[1] == (byte) 1) {
            int length = ((buffer[2] & 255) << 8) + (buffer[3] & 255);
            if (length > mRecvBufSize) {
                throw new FrameDecodeException("receive data length exceed " + mRecvBufSize);
            }

            return length;
        }

        throw new FrameDecodeException("can not found airsync protocol frame header, bArr=" + ByteUtil.byteArray2HexString(buffer, size));
    }

    private boolean isReadEnd() {
        if (mRecvOffset != 0 && mRecvDataLen != 0 && mRecvOffset == mRecvDataLen) {
            return true;
        }

        if (mRecvOffset != 0 && mRecvDataLen != 0 && this.mRecvOffset > this.mRecvDataLen) {
            if (isFillZeroEndOfFrame()) {
                return true;
            }

            throw new FrameDecodeException("read frame length error, expect was " + mRecvDataLen +
                                        "but was " + mRecvOffset + " , bArr=" + ByteUtil.byte2HexString(mRecvBuf));
        }

        return false;
    }

    private boolean isFillZeroEndOfFrame() {
        int remainLen = this.mRecvOffset - this.mRecvDataLen;
        byte[] remainData = new byte[remainLen];
        System.arraycopy(this.mRecvBuf, this.mRecvDataLen, remainData, 0, remainLen);

        boolean isFillZero = true;
        for (int i = 0; i < remainLen; i++) {
            if (remainData[i] != (byte) 0) {
                isFillZero = false;
            }
        }

        return isFillZero;
    }

    private void resetRecv() {
        this.mRecvDataLen = 0;
        this.mRecvOffset = 0;
    }

    private void handleException(AbstractChannelHandlerContext ctx, String errMsg, Exception e) {
        LogUtil.e(TAG, errMsg + ", exception=" + e.toString());

        if (getSeqId() != 0) {
            BluesyncProto.BaseResponse.Builder builder = BluesyncProto.BaseResponse.newBuilder();
            builder.setErrCode(BluesyncProto.EmErrorCode.EEC_decode_VALUE);
            builder.setErrMsg(errMsg);
            BluesyncMessage errResponse = new BluesyncMessage(getSeqId(), BluesyncProto.EmCmdId.ECI_error, builder.build());
            ctx.fireWrite(errResponse);
        }

        resetRecv();
    }

    private int getSeqId() {
        if (mRecvOffset > 8) {
            int seq = ((mRecvBuf[6] & 0xFF) << 8) | (mRecvBuf[7] & 0xFF);
            return seq;
        }

        return 0;
    }

    private void printLog(String msg) {
        if (DEBUG) {
            LogUtil.d(TAG, msg);
        }
    }
}
