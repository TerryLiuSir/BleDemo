package terry.bluesync.server.util;

public class DataSplitUtil {
    private byte[] mData = null;
    private int mFrontPosition = 0;
    private int mRearPosition = 0;
    private int mSplitChunkSize;

    public DataSplitUtil(int size) {
        mSplitChunkSize = size;
    }

    public void setData(byte[] bArr) {
        if (bArr == null) {
            mData = null;
            mRearPosition = 0;
            mFrontPosition = 0;
            return;
        }

        mData = new byte[bArr.length];
        System.arraycopy(bArr, 0, mData, 0, bArr.length);
        mRearPosition = bArr.length;
        mFrontPosition = 0;
    }

    public byte[] getDataChunk() {
        int remainSize = mRearPosition - mFrontPosition;
        if (remainSize == 0) {
            return null;
        }

        if (remainSize >= mSplitChunkSize) {
            remainSize = mSplitChunkSize;
        }

        byte[] retByte = new byte[remainSize];
        System.arraycopy(mData, mFrontPosition, retByte, 0, remainSize);
        mFrontPosition += remainSize;

        return retByte;
    }
}
