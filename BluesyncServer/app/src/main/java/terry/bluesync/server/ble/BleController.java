package terry.bluesync.server.ble;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;


import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import terry.bluesync.server.util.ByteUtil;
import terry.bluesync.server.util.DeviceUtil;
import terry.bluesync.server.util.LogUtil;

public class BleController {
    private static final String TAG = BleController.class.getSimpleName();
    private static final boolean DEBUG = true;

    public static final UUID SERVICE_UUID = UUID.fromString("0000ff88-0000-1000-8000-00805f9b34fb");
    public static final UUID WRITE_CHARACTERISTIC = UUID.fromString("0000fec7-0000-1000-8000-00805f9b34fb");
    public static final UUID INDICATE_CHARACTERISTIC = UUID.fromString("0000fec8-0000-1000-8000-00805f9b34fb");
    public static final UUID READ_CHARACTERISTIC = UUID.fromString("0000fec9-0000-1000-8000-00805f9b34fb");
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final byte[] ENABLE_INDICATION_AND_NOTIFICATION = {0x03, 0x00};

    private Context mContext;
    private Handler mHandler;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;

    private BluetoothGattServer mGattServer;
    private BluetoothGattCharacteristic mWriteCharacteristic;
    private BluetoothGattCharacteristic mReadCharacteristic;
    private BluetoothGattCharacteristic mIndicateCharacteristic;

    private Map<BluetoothDevice, Channel> mChannelMap;
    private boolean mIsRunning = false;
    private ChannelInitializer mChannelInitializer;

    public interface ChannelInitializer {
        void initChannel(Channel ch);
    }

    public BleController(Context context) {
        mContext = context;

        mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        mChannelMap = new HashMap<>();
        mHandler = new Handler();
    }

    public void registerChannelInitializer(ChannelInitializer initializer) {
        mChannelInitializer = initializer;
    }

    public void unregisterChannelInitializer() {
        mChannelInitializer = null;
    }

    public boolean start() {
        if (mIsRunning) {
            return true;
        }

        if (!mBluetoothAdapter.isEnabled()) {
            return false;
        }

        initService();
        startAdvertise();
        mIsRunning = true;

        return true;
    }

    private void initService() {
        mGattServer = mBluetoothManager.openGattServer(mContext, new GattServerCallback());

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        mWriteCharacteristic = new BluetoothGattCharacteristic(WRITE_CHARACTERISTIC,
                BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE);
        mWriteCharacteristic.setValue("");
        service.addCharacteristic(mWriteCharacteristic);

        mIndicateCharacteristic = new BluetoothGattCharacteristic(INDICATE_CHARACTERISTIC,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_INDICATE,
                BluetoothGattCharacteristic.PERMISSION_READ);
        mIndicateCharacteristic.addDescriptor(getClientCharacteristicConfigurationDescriptor());
        mIndicateCharacteristic.setValue("");
        service.addCharacteristic(mIndicateCharacteristic);

        mReadCharacteristic = new BluetoothGattCharacteristic(READ_CHARACTERISTIC,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);
        mReadCharacteristic.setValue("");
        service.addCharacteristic(mReadCharacteristic);

        if (mGattServer != null && service != null) {
            mGattServer.addService(service);
        }
    }

    class GattServerCallback extends BluetoothGattServerCallback {
        public void onServiceAdded(int status, BluetoothGattService service) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                printLog("onServiceAdded status=GATT_SUCCESS service=" + service.getUuid().toString());
            } else {
                printLog("onServiceAdded status!=GATT_SUCCESS");
            }
        }

        public void onConnectionStateChange(final BluetoothDevice device, int status,
                                            final int newState) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        printLog("[" + device + "]" + " connect");

                        Channel channel = getChannel(device);
                        if (mChannelInitializer != null) {
                            mChannelInitializer.initChannel(channel);
                        }

                        channel.active();
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED){
                        printLog("[" + device + "]" + " disconnect");

                        getChannel(device).inactive();
                        removeChannel(device);
                    }
                }
            });
        }

        public void onCharacteristicReadRequest(BluetoothDevice device,
                                                int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);

            printLog("Device tried to read characteristic: " + characteristic.getUuid());
            printLog("Value: " + Arrays.toString(characteristic.getValue()));

            if (offset != 0) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset,
            /* value (optional) */ null);
                return;
            }
            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                    offset, characteristic.getValue());
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device,
                                                 int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite,
                                                 boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite,
                    responseNeeded, offset, value);
            printLog("Characteristic Write request: " + Arrays.toString(value));

            if (responseNeeded) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
            /* No need to respond with an offset */ 0,
            /* No need to respond with a value */ null);
            }

            getChannel(device).read(value);
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded,
                    offset, value);
            int status;
            if (descriptor.getUuid() == CLIENT_CHARACTERISTIC_CONFIG) {
                BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
                boolean supportsNotifications = (characteristic.getProperties() &
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
                boolean supportsIndications = (characteristic.getProperties() &
                        BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0;

                if (!(supportsNotifications || supportsIndications)) {
                    status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
                } else if (value.length != 2) {
                    status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
                } else if (Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                    status = BluetoothGatt.GATT_SUCCESS;
                    descriptor.setValue(value);
                } else if (supportsNotifications &&
                        Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    status = BluetoothGatt.GATT_SUCCESS;
                    descriptor.setValue(value);
                } else if (supportsIndications &&
                        Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
                    status = BluetoothGatt.GATT_SUCCESS;
                    descriptor.setValue(value);
                } else if (supportsIndications && supportsIndications
                        && Arrays.equals(value, ENABLE_INDICATION_AND_NOTIFICATION)) {
                    status = BluetoothGatt.GATT_SUCCESS;
                    descriptor.setValue(value);
                } else {
                    status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
                }
            } else {
                status = BluetoothGatt.GATT_SUCCESS;
                descriptor.setValue(value);
            }

            printLog("Descriptor Write Request " + descriptor.getUuid() + " " + Arrays.toString(value) + ", status=" + status);

            if (responseNeeded) {
                mGattServer.sendResponse(device, requestId, status,
            /* No need to respond with offset */ 0,
            /* No need to respond with a value */ null);
            }

            getChannel(device).descriptorWrite();
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            printLog("onExecuteWrite + int requestId=" + requestId + "execute=" + execute);
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
            printLog("Notification sent. Status: " + status);

            getChannel(device).writeChannel();
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            printLog("onMtuChanged, mtu=" + mtu);
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            printLog("Device tried to read descriptor: " + descriptor.getUuid());
            printLog("Value: " + Arrays.toString(descriptor.getValue()));

            if (offset != 0) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset,
            /* value (optional) */ null);
                return;
            }

            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset,
                    descriptor.getValue());
        }
    }

    private void removeChannel(BluetoothDevice device) {
        mChannelMap.remove(device);
    }

    private Channel getChannel(BluetoothDevice device) {
        Channel channel = mChannelMap.get(device);
        if (channel == null) {
            channel = new Channel(device, mUnSafe);
            mChannelMap.put(device, channel);
        }

        return channel;
    }

    private UnSafe mUnSafe = new UnSafe() {
        @Override
        public synchronized void doWrite(BluetoothDevice device, byte[] value) {
            if (!mIsRunning) {
                printLog("Write fail because BleController is inactive.");
                return;
            }

            try {
                mIndicateCharacteristic.setValue(value);
                boolean ret = mGattServer.notifyCharacteristicChanged(device, mIndicateCharacteristic, true);
                printLog("[" + device + "] doWrite success=" + ret + ", value=" + ByteUtil.byte2HexString(value));
            } catch (Exception exception) {
                printLog("[" + device + "] doWrite failure, e=" + exception.toString());
            }
        }

        @Override
        public synchronized void doDisconnect(BluetoothDevice device) {
            if (!mIsRunning) {
                printLog("Write fail because BleController is inactive.");
                return;
            }

            mGattServer.cancelConnection(device);
            printLog("[" + device + "] doDisconnect");
        }
    };

    private BluetoothGattDescriptor getClientCharacteristicConfigurationDescriptor() {
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(CLIENT_CHARACTERISTIC_CONFIG,
                (BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
        descriptor.setValue(new byte[]{0, 0});
        return descriptor;
    }

    public void startAdvertise() {
        mBluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        mBluetoothLeAdvertiser.startAdvertising(createAdvSettings(true, 0), createFMPAdvertiseData(), createScanResponseData(), mAdvCallback);
        printLog("startAdvertise");
    }

    public void stopAdvertise() {
        mBluetoothLeAdvertiser.stopAdvertising(mAdvCallback);
        printLog("stopAdvertise");
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public AdvertiseSettings createAdvSettings(boolean connectable, int timeoutMillis) {
        AdvertiseSettings.Builder builder = new AdvertiseSettings.Builder();
        builder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);
        builder.setConnectable(connectable);
        builder.setTimeout(timeoutMillis);
        builder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);

        return builder.build();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public AdvertiseData createFMPAdvertiseData() {
        AdvertiseData.Builder builder = new AdvertiseData.Builder();
        builder.addServiceUuid(new ParcelUuid(SERVICE_UUID));
        builder.setIncludeDeviceName(false);
        builder.addManufacturerData(1, DeviceUtil.getModel().getBytes());
        AdvertiseData adv = builder.build();

        return adv;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public AdvertiseData createScanResponseData() {
        AdvertiseData.Builder builder = new AdvertiseData.Builder();
        byte[] byteData = "null".getBytes();

        // TODO:
        String ip = "192.168.12.1";
        if (ip != null) {
            String ssid = "ssid";
            String data = ip + ":" + ssid;

            int maxlen = 26;
            if (data.length() > maxlen) {
                data = data.substring(0, maxlen);
            }
            byteData = data.getBytes();
        }

        builder.addManufacturerData(2, byteData);
        AdvertiseData adv = builder.build();

        return adv;
    }

    private AdvertiseCallback mAdvCallback = new AdvertiseCallback() {
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            if (settingsInEffect != null) {
                printLog("onStartSuccess TxPowerLv=" + settingsInEffect.getTxPowerLevel() + " mode=" + settingsInEffect.getMode() + " timeout=" + settingsInEffect.getTimeout());
            } else {
                printLog("onStartSuccess, settingInEffect is null");
            }
        }

        public void onStartFailure(int errorCode) {
            printLog("onStartFailure errorCode=" + errorCode);
        }
    };

    public void stop() {
        if (!mIsRunning) {
            return;
        }

        if (mBluetoothLeAdvertiser != null) {
            stopAdvertise();
            mBluetoothLeAdvertiser = null;
        }

        if (mGattServer != null) {
            mGattServer.clearServices();
            mGattServer.close();
            mGattServer = null;
        }


        for(Channel channel: mChannelMap.values()) {
            channel.destroy();
        }
        mChannelMap.clear();
        mIsRunning = false;
    }

    private void printLog(String msg) {
        if (DEBUG) {
            LogUtil.d(TAG, msg);
        }
    }

    public void dump() {
        LogUtil.event(TAG, "isRunning=" + mIsRunning);
        dumpChannel();
        if (mBluetoothManager != null) {
            LogUtil.event(TAG, "Connected  devices=" + mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT_SERVER));
        }
    }

    public void dumpChannel() {
        for (BluetoothDevice device: mChannelMap.keySet()) {
            LogUtil.event(TAG, "device=" + device.getName() + ", " + device.getAddress());
        }
    }
}
