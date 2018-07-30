package com.ragentek.projector;

import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.List;

import terry.bluesync.client.BluesyncAdapter;
import terry.bluesync.client.BluesyncDevice;
import terry.bluesync.client.BluesyncScanner;
import terry.bluesync.client.BluesyncService;
import terry.bluesync.client.util.AesCoder;
import terry.bluesync.client.util.ByteUtil;
import terry.bluesync.client.util.LogUtil;

public class ScannerTestActivity extends ListActivity {
    private static final String TAG = ScannerTestActivity.class.getSimpleName();

    private BluesyncScanner mBluesyncScanner;
    private ArrayAdapter mArrayAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mArrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1);
        setListAdapter(mArrayAdapter);
        boundService();

        byte[] sn = "12323123".getBytes();
        byte[] aesSign = AesCoder.encodeAesSign(sn);
        boolean ret = AesCoder.decodeAesSign(aesSign, sn);

        LogUtil.d("Test", "sessionKey=" + ByteUtil.byte2HexString(AesCoder.genSessionKey()));
        LogUtil.d("Test", "ret =" + ret);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mConnection);
    }

    public void boundService() {
        Intent serviceIntent = new Intent(this, BluesyncService.class);
        serviceIntent.setPackage(getPackageName());
        startService(serviceIntent);

        bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE);
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            BluesyncService.LocalBinder binder = (BluesyncService.LocalBinder) service;
            BluesyncAdapter adapter = binder.getBluesyncAdapter();
            mBluesyncScanner = adapter.getBluesyncScanner();
            startScan();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (mBluesyncScanner != null) {
                mBluesyncScanner.stopScan();
                mBluesyncScanner = null;
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();

        if (mBluesyncScanner != null) {
            startScan();
        } else {
            boundService();
        }
    }

    private void startScan() {
        mBluesyncScanner.stopScan();
        mBluesyncScanner.startScan(mScanListener);
    }

    private BluesyncScanner.ScanListener mScanListener = new BluesyncScanner.ScanListener() {

        @Override
        public void onScanResultChanged(List<BluesyncDevice> results) {
            mArrayAdapter.clear();
            for (BluesyncDevice resultTO: results) {
                mArrayAdapter.add(resultTO);
            }
        }
    };

    @Override
    protected void onPause() {
        super.onPause();

        mBluesyncScanner.stopScan();
        mArrayAdapter.clear();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        BluesyncDevice scanResult = (BluesyncDevice) mArrayAdapter.getItem(position);
        Intent intent = new Intent(this, BluesyncProtocolTestActivity.class);
        intent.putExtra(BluesyncProtocolTestActivity.EXTRA_BLUE_ADDRESS, scanResult.getAddress());
        startActivity(intent);
    }
}
