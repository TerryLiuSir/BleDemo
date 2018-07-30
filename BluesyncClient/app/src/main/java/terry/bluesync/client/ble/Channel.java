package terry.bluesync.client.ble;


import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

public class Channel {
    private BluetoothGatt gatt;
    private ChannelPipeline channelPipeline;
    private BluetoothGattCharacteristic indicateCharacteristic;
    private BluetoothGattCharacteristic readCharacteristic;
    private BluetoothGattCharacteristic writeCharacteristic;

    private boolean isActive = false;

    public Channel(BluetoothGatt gatt, BluetoothGattCharacteristic indicate,
                   BluetoothGattCharacteristic read, BluetoothGattCharacteristic write) {
        this.gatt = gatt;
        this.indicateCharacteristic = indicate;
        this.readCharacteristic = read;
        this.writeCharacteristic = write;

        channelPipeline = new ChannelPipeline(this);
    }

    public void destroy() {
        channelPipeline.destroy();
    }

    public ChannelPipeline channelPipeline() {
        return channelPipeline;
    }

    /**
     * Write success if channel is active.
     * But it can not give any reminder when write fail.
     * You'd better check channel weather active before call this function.
     * */
    public void write(Object msg) {
        channelPipeline().write(msg);
    }

    public boolean isActive() {
        return isActive;
    }

    public void exceptionCaught(Throwable throwable) {
        channelPipeline().exceptionCaught(throwable);
    }

    protected BluetoothGatt gatt() {
        return gatt;
    }

    protected BluetoothGattCharacteristic indicateCharacteristic() {
        return indicateCharacteristic;
    }

    protected BluetoothGattCharacteristic readCharacteristic() {
        return readCharacteristic;
    }

    protected BluetoothGattCharacteristic writeCharacteristic() {
        return writeCharacteristic;
    }

    protected void active() {
        channelPipeline().active();
        isActive = true;
    }

    protected void inactive() {
        channelPipeline().inactive();
        isActive = false;
    }

    protected void read(Object msg) {
        channelPipeline().read(msg);
    }

    protected void writeChannel(byte[] value) {
        writeCharacteristic().setValue(value);
        gatt().writeCharacteristic(writeCharacteristic());
    }

    protected void writeNext() {
        channelPipeline().writeNext();
    }
}
