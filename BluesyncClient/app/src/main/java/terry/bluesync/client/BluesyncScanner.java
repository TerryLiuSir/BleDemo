package terry.bluesync.client;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class BluesyncScanner {
    private static final String TAG = BluesyncScanner.class.getSimpleName();

    private Context mContext;
    private Handler mHandler;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothScanner;
    private InnerScanCallback mInnerScanCallback;
    private ScanListener mScanListener;

    public interface ScanListener {
        void onScanResultChanged(List<BluesyncDevice> results);
    }

    public BluesyncScanner(Context context) {
        mContext = context;
        mHandler = new Handler();
        mInnerScanCallback = new InnerScanCallback();

        BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
    }

    public boolean startScan(ScanListener listener) {
        if (!mBluetoothAdapter.isEnabled()) {
            return false;
        }
        printLog("startScan");

        mBluetoothScanner = mBluetoothAdapter.getBluetoothLeScanner();

        mInnerScanCallback.clear();
        mScanListener = listener;

        mBluetoothScanner.startScan(createScanFilters(), createScanSettings(), mInnerScanCallback);
        return true;
    }

    private List<ScanFilter> createScanFilters() {
        ScanFilter.Builder builder = new ScanFilter.Builder();
        builder.setServiceUuid(new ParcelUuid(BluesyncGattAttributes.BLUESYNC_SERVICE));
        ScanFilter filter = builder.build();

        List<ScanFilter> filters = new ArrayList<>();
        filters.add(filter);

        return filters;
    }

    private ScanSettings createScanSettings() {
        ScanSettings.Builder builder = new ScanSettings.Builder();
//        builder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES);
//        builder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE);
//        builder.setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT);
        builder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);

        return builder.build();
    }

    public void stopScan() {
        if (!mBluetoothAdapter.isEnabled() || mBluetoothScanner == null) {
            return;
        }
        printLog("stopScan");

        mScanListener = null;
        mBluetoothScanner.stopScan(mInnerScanCallback);
    }

    private class InnerScanCallback extends ScanCallback {
        private List<BluetoothDevice> scanRecordList;
        private List<BluesyncDevice> scanResultTOList;

        public InnerScanCallback() {
            scanRecordList = new ArrayList<>();
            scanResultTOList = new ArrayList<>();
        }

        public void clear() {
            scanRecordList.clear();
            scanResultTOList.clear();
        }

        @Override
        public void onScanResult(int callbackType, android.bluetooth.le.ScanResult result) {
            if (isAlreadyExists(result.getDevice())) {
                return;
            }
            printLog(result.toString());

            byte[] manufacturerData = result.getScanRecord().getManufacturerSpecificData(1);
            String model = new String(manufacturerData);
            String address = result.getDevice().getAddress();

            byte[] manufacturerData2 = result.getScanRecord().getManufacturerSpecificData(2);
            String ip = null;
            String ssid = null;
            if (manufacturerData2 != null) {
                String extraData = new String(manufacturerData2);
                String[] dataArr = extraData.split(":");

                if (dataArr != null && dataArr.length == 2) {
                    ip = dataArr[0];
                    ssid = dataArr[1];
                }
            }

            BluesyncDevice resultTO = new BluesyncDevice(model, address, ip, ssid);
            scanResultTOList.add(resultTO);

            if (mScanListener != null) {
                mScanListener.onScanResultChanged(scanResultTOList);
            }
        }

        private synchronized boolean isAlreadyExists(BluetoothDevice device) {
            if (scanRecordList.contains(device)) {
                return true;
            }

            scanRecordList.add(device);
            return false;
        }

        @Override
        public void onScanFailed(int errorCode) {
            printLog("onScanFailed, errorCode=" + errorCode);
        }
    }

    private void printLog(String message) {
        Log.d(TAG, message);
    }
}
