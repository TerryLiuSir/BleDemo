package terry.bluesync.server.ble;

import android.bluetooth.BluetoothDevice;

public interface UnSafe {
    void doWrite(BluetoothDevice device, byte[] value);
    void doDisconnect(BluetoothDevice device);
}
