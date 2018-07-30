package terry.bluesync.client;

import android.content.Context;

public class BluesyncAdapter {
    private BluesyncScanner mBluesyncScanner;
    private BluesyncClient mBluesyncClient;

    public BluesyncAdapter(Context context) {
        mBluesyncClient = new BluesyncClientImpl(context);
        mBluesyncScanner = new BluesyncScanner(context);
    }

    public BluesyncScanner getBluesyncScanner() {
        return mBluesyncScanner;
    }

    public BluesyncClient getBluesyncClient() {
        return mBluesyncClient;
    }
}
