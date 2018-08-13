package terry.bluesync.server;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;

import terry.bluesync.server.BluesyncController.ResponseCallback;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 200;
    private static final int REQUEST_ENABLE_BT = 300;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluesyncController mBluesyncController;

    private ListView mListView;
    private TextView mLogText;
    private ScrollView mScrollView;

    private Handler mHandler = new Handler();
    private final CaseItemGroup mRootCaseGroup = new CaseItemGroup();

    private enum CaseType {
        REQUEST,
        PUSH
    }

    private class CaseItem {
        private CaseType type;
        private String title;
        private String message;

        public CaseItem(CaseType type, String title, String message) {
            this.type = type;
            this.title = title;
            this.message = message;
        }

        public void test() {
            try {
                switch (type) {
                    case REQUEST:
                        mBluesyncController.sendRequest(message, new CaseResponseCallback());
                        printLog("Request, data=" + message);
                        break;
                    case PUSH:
                        mBluesyncController.pushData(message);
                        printLog("Push, data=" + message);
                        break;
                }
            } catch (BluesyncException e) {
                printLog(e.toString());
            }
        }

        class CaseResponseCallback implements ResponseCallback {

            @Override
            public void onSuccess(String data) {
                printLog("Response, data=" + data);
            }

            @Override
            public void onError(String message) {
                printLog("Response, error=" + message);
            }
        }
    }

    private class CaseItemGroup {
        ArrayList<CaseItem> caseList = new ArrayList();

        public void add(CaseItem testCase) {
            caseList.add(testCase);
        }

        public String[] getTitleList() {
            String[] titleList = new String[caseList.size()];
            for (int i = 0; i < caseList.size(); i++) {
                titleList[i] = caseList.get(i).title;
            }
            return titleList;
        }

        public int size() {
            return caseList.size();
        }

        public CaseItem get(int index) {
            return caseList.get(index);
        }

        public void clear() {
            caseList.clear();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPermissions();
        initBluesync();

        initView();
        initCases();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mBluesyncController != null) {
            mBluesyncController.addListener(mListener);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mBluesyncController != null) {
            mBluesyncController.removeListener(mListener);
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            } else {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage("Permission Denied")
                            .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    finish();
                                }
                            });
                    builder.create().show();
                }
                break;
        }
    }

    private void validateBluetooth() {
        mBluetoothManager = (BluetoothManager) this.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        if ((mBluetoothAdapter == null) || (!mBluetoothAdapter.isEnabled())) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (!mBluetoothAdapter.isEnabled()) {
                finish();
                return;
            }
        }
    }

    public void initBluesync() {
        Intent serviceIntent = new Intent(this, BluesyncServerService.class);
        serviceIntent.setPackage(getPackageName());
        startService(serviceIntent);

        bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE);
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            BluesyncServerService.LocalBinder binder = (BluesyncServerService.LocalBinder) service;
            mBluesyncController = binder.getBluesyncController();
            mBluesyncController.addListener(mListener);

            printLog("onbind service");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (mBluesyncController != null) {
                mBluesyncController.removeListener(mListener);
                mBluesyncController = null;
            }
        }
    };

    private BluesyncController.Listener mListener = new BluesyncController.Listener() {

        @Override
        public void onSateChange(BluesyncController.STATE state) {
            printLog("State=" + state.toString());
        }

        @Override
        public void onReceiveSendData(BluesyncRequest request) {
            printLog("ReceiveSendData, request=" + request);

            try {
                request.sendResponse("{response:" + request.getData() + "}");
            } catch (BluesyncException e) {
                printLog("Send response error=" + e.toString());
            }
        }

        @Override
        public void onReceivePushData(String data) {
            printLog("ReceivePushData, push=" + data);
        }
    };

    private void initView() {
        mListView = (ListView) findViewById(R.id.list);
        mListView.setOnItemClickListener(mItemListener);

        mScrollView = (ScrollView) findViewById(R.id.scroll);
        mLogText = (TextView) findViewById(R.id.log_text);
    }

    private void initCases() {
        mRootCaseGroup.add(createRequestCase());
        mRootCaseGroup.add(createPushCase());

        mListView.setAdapter(new ArrayAdapter(this,
                android.R.layout.simple_list_item_1, mRootCaseGroup.getTitleList()));
    }

    private AdapterView.OnItemClickListener mItemListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (position >= 0 && position < mRootCaseGroup.size()) {
                CaseItem item = mRootCaseGroup.get(position);
                item.test();
            }
        }
    };

    private CaseItem createRequestCase() {
        return new CaseItem(CaseType.REQUEST,"Request String", "This is request data");
    }

    private CaseItem createPushCase() {
        return new CaseItem(CaseType.PUSH,"Push String", "This is push data");
    }

    public void printLog(final String message) {
        Log.d(TAG, message);

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLogText.append("\n");
                mLogText.append(message);
                scrollToEnd();
            }
        });
    }

    private void scrollToEnd() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mScrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }
        }, 100);
    }

}
