package terry.bluesync.server;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;

import terry.bluesync.server.util.LogUtil;

public class BluesyncServerService extends Service {
    private static final String TAG = "BluesyncService";

    private LocalBinder mBinder;
    private BluesyncController mBluesyncController;


    @Override
    public void onCreate() {
        LogUtil.d(TAG,"onCreate");
        mBluesyncController = new BluesyncControllerImpl(this);
        mBinder = new LocalBinder();

        mBluesyncController.start();

        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mWifiStateReceive, filter);
    }

    private BroadcastReceiver mWifiStateReceive = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                if (mBluesyncController.getState() != BluesyncController.STATE.CONNECTED) {
//                    mBluesyncController.updateAdvertiseData();
                }
            }
        }
    };

    public class LocalBinder extends Binder {
        public BluesyncController getBluesyncController() {
            return mBluesyncController;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogUtil.d(TAG, "onStartCommand, intent=" + intent);
        return Service.START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mBluesyncController.stop();
        unregisterReceiver(mWifiStateReceive);
    }

}