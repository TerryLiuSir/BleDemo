package terry.bluesync.client.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;

import terry.bluesync.client.BluesyncGattAttributes;
import terry.bluesync.client.util.ByteUtil;
import terry.bluesync.client.util.LogUtil;

public class BleClient {
    private static final String TAG = BleClient.class.getSimpleName();
    private static final boolean DEBUG = true;

    private BluetoothAdapter mBluetoothAdapter;
    private Context mContext;
    private Handler mHandler;
    private BluetoothGatt mGatt;
    private Channel mChannel;

    private ChannelInitializer mChannelInitializer;

    public interface ChannelInitializer {
        /**
         * Set channel object if connecting successful, else channel is null;
         */
        void initChannel(Channel channel, boolean isSuccess, String errMsg);

        void initFail();
    }

    public BleClient(Context context) {
        mContext = context;
        mHandler = new Handler();
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
    }

    public boolean connect(final String address, ChannelInitializer initializer) {
        assert (initializer != null);

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled() || address == null ) {
            printLog("BluetoothAdapter not initialized or unspecified address.");
            return false;
        }
        mChannelInitializer = initializer;

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            printLog("Device not found.  Unable to connect.");
            return false;
        }

        mGatt = device.connectGatt(mContext, false, mGattCallback);
        return true;
    }

    public void disconnect() {
        printLog("disconnect");
        if (!mBluetoothAdapter.isEnabled()) {
            return;
        }

        if (mChannel != null) {
            mChannel.gatt().disconnect();
        }
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, int status, final int newState) {

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        printLog("Connected to GATT server, address=" + gatt.getDevice());

                        /* Call onServicesDiscovered function */
                        mGatt.discoverServices();
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        printLog("Disconnected from GATT server.");

                        if (mChannel != null) {
                            mChannel.gatt().close();
                            mChannel.inactive();
                            mChannel.destroy();
                            mChannel = null;
                        } else {
                            printLog("InitChannel fail");
                            mChannelInitializer.initFail();
                        }
                    }
                }
            });
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            printLog("onServicesDiscovered");
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        mChannel = initChannel(status);
                        mChannelInitializer.initChannel(mChannel, true, "success");

                        mChannel.active();

                        printLog("initChannel success");
                    } catch (BleConnectionException e) {
                        mChannel = null;
                        mGatt.close();
                        mGatt.close();

                        mChannelInitializer.initChannel(mChannel, false, e.getMessage());
                        LogUtil.e(TAG, "initChannel error, gatt close because " + e.getMessage());
                    }
                }
            });
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, int status) {
            printLog("onCharacteristicWrite, bytes=" + ByteUtil.byte2HexString(characteristic.getValue()));
            mChannel.writeNext();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            if (characteristic != mChannel.indicateCharacteristic()) {
                printLog("Indicate characteristic error");
                return;
            }

            byte[] value = characteristic.getValue();
            mChannel.read(value);

            printLog("onCharacteristicChanged, bytes=" + ByteUtil.byte2HexString(value));
        }
    };

    private Channel initChannel(int status) throws BleConnectionException {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            throw new BleConnectionException("gatt discover failure");
        }

        BluetoothGattService gattService = mGatt.getService(BluesyncGattAttributes.BLUESYNC_SERVICE);

        if (gattService == null) {
            throw new BleConnectionException("can not found bluesync service");
        }

        /** check indicate characteristic */
        BluetoothGattCharacteristic indicateCharacteristic = gattService.getCharacteristic(BluesyncGattAttributes.INDICATE_CHARACTERISTIC);
        if (indicateCharacteristic == null) {
            throw new BleConnectionException("can not found indicate characteristic");
        }

        BluetoothGattDescriptor descriptor = indicateCharacteristic.getDescriptor(BluesyncGattAttributes.CLIENT_CHARACTERISTIC_CONFIG);
        if (descriptor == null) {
            throw new BleConnectionException("can not found indicate descriptor ");
        }


        /** check read characteristic */
        BluetoothGattCharacteristic readCharacteristic = gattService.getCharacteristic(BluesyncGattAttributes.READ_CHARACTERISTIC);
        if (readCharacteristic == null) {
            throw new BleConnectionException("can not found read characteristic");
        }

        /** check write characteristic */
        BluetoothGattCharacteristic writeCharacteristic = gattService.getCharacteristic(BluesyncGattAttributes.WRITE_CHARACTERISTIC);
        if (writeCharacteristic == null) {
            throw new BleConnectionException("can not found write characteristic");
        }
        mChannel = new Channel(mGatt, indicateCharacteristic, readCharacteristic, writeCharacteristic);

        descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
        mGatt.writeDescriptor(descriptor);
        mGatt.setCharacteristicNotification(indicateCharacteristic, true);

        return mChannel;
    }

    private void printLog(String message) {
        if (DEBUG) {
            LogUtil.d(TAG, message);
        }
    }
}
