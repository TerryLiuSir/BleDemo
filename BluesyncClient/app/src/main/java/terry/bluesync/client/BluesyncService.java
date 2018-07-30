package terry.bluesync.client;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;


public class BluesyncService extends Service {

    private LocalBinder mBinder;
    private BluesyncAdapter mBluesyncAdapter;

    @Override
    public void onCreate() {
        mBinder = new BluesyncService.LocalBinder();
        mBluesyncAdapter = new BluesyncAdapter(this);
    }

    public class LocalBinder extends Binder {

        public BluesyncAdapter getBluesyncAdapter() {
            return mBluesyncAdapter;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
    }

}
