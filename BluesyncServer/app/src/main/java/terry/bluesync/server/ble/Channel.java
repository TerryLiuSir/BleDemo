package terry.bluesync.server.ble;


import android.bluetooth.BluetoothDevice;

public class Channel {
    private BluetoothDevice mDevice;
    private UnSafe mUnsafe;
    private ChannelPipeline mChannelPipeline;

    public Channel(BluetoothDevice device, UnSafe unSafe) {
        mDevice = device;
        mUnsafe = unSafe;
        mChannelPipeline = new ChannelPipeline(this);
    }

    public void destroy() {
        mChannelPipeline.destroy();
    }

    public UnSafe unSafe() {
        return mUnsafe;
    }

    public BluetoothDevice device() {
        return mDevice;
    }

    public ChannelPipeline channelPipeline() {
        return mChannelPipeline;
    }

    public void active() {
        channelPipeline().active();
    }

    public void inactive() {
        channelPipeline().inactive();
    }

    public void descriptorWrite() {
        channelPipeline().descriptorWrite();
    }

    public void read(Object msg) {
        channelPipeline().read(msg);
    }

    public void write(Object msg) {
        channelPipeline().write(msg);
    }

    public void disconnect() { channelPipeline().disconnect(); }

    /**Notes: user should not call this function*/
    public void writeChannel() {
        channelPipeline().writeChannel();
    }

    public void exceptionCaught(Throwable throwable) {
        channelPipeline().exceptionCaught(throwable);
    }
}
