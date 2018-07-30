package terry.bluesync.server.protocol;

import com.google.protobuf.GeneratedMessageV3;

public class BluesyncMessage {
    private int seqId;
    private BluesyncProto.EmCmdId cmdId;
    private GeneratedMessageV3 protobufData; // protobuf base object class

    public BluesyncMessage(int seqId, BluesyncProto.EmCmdId cmdId, GeneratedMessageV3 protobufData) {
        this.seqId = seqId;
        this.cmdId = cmdId;
        this.protobufData = protobufData;
    }

    public BluesyncProto.EmCmdId getCmdId() {
        return cmdId;
    }

    public int getSeqId () {
        return seqId;
    }

    public GeneratedMessageV3 getProtobufData() {
        return protobufData;
    }

    @Override
    public String toString() {
        return "[" + seqId + "]" + cmdId + ": " + protobufData;
    }
}
