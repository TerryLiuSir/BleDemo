package terry.bluesync.server.protocol;


import com.google.protobuf.ByteString;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import terry.bluesync.server.protocol.BluesyncProto.BaseRequest;
import terry.bluesync.server.protocol.BluesyncProto.EmCmdId;
import terry.bluesync.server.protocol.BluesyncProto.EmErrorCode;

import static terry.bluesync.server.protocol.BluesyncProto.*;

public class BluesyncProtoUtil {
    private static final int MIN_SEQ_NUM = Short.MAX_VALUE;
    private static final int MAX_SEQ_NUM = Short.MAX_VALUE * 2;
    private static AtomicInteger mSeqId = new AtomicInteger(MIN_SEQ_NUM);

    public static int genSeqId() {
        int nextId = mSeqId.getAndIncrement();
        if (nextId > MAX_SEQ_NUM) {
            int value;
            do {
                value = mSeqId.get();
            } while (value > MAX_SEQ_NUM && mSeqId.compareAndSet(value, MIN_SEQ_NUM));

            nextId = MIN_SEQ_NUM + (nextId - MAX_SEQ_NUM);
        }

        return nextId;
    }

    public static BluesyncMessage getSendDataRequest(byte[] data) {
        SendDataRequest.Builder builder = SendDataRequest.newBuilder();
        builder.setBaseRequest(BaseRequest.newBuilder().build())
            .setData(ByteString.copyFrom(data));

        return  new BluesyncMessage(genSeqId(), EmCmdId.ECI_req_sendData, builder.build());
    }

    public static BluesyncMessage getSendDataResponse(int seqId, byte[] data) {
        SendDataRequest.Builder builder = SendDataRequest.newBuilder();
        builder.setBaseRequest(BaseRequest.newBuilder().build());
        if (data != null) {
            builder.setData(ByteString.copyFrom(data));
        }

        return  new BluesyncMessage(seqId, EmCmdId.ECI_resp_sendData, builder.build());
    }

    public static BluesyncMessage getRecvDataPush(byte[] data) {
        RecvDataPush.Builder builder = RecvDataPush.newBuilder();
        builder.setBasePush(BasePush.newBuilder().build())
                .setData(ByteString.copyFrom(data));

        return new BluesyncMessage(0, EmCmdId.ECI_push_recvData, BaseRequest.newBuilder().build());
    }

    public static BaseResponse getBaseResponseMessage(int error, String message) {
        BaseResponse.Builder builder = BaseResponse.newBuilder();
        builder.setErrCode(error);
        if (message != null) {
            builder.setErrMsg(message);
        }

        BaseResponse response = builder.build();
        return response;
    }
}
