package terry.bluesync.client;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;

import terry.bluesync.client.BluesyncClient.Listener;
import terry.bluesync.client.BluesyncClient.ResponseCallback;
import terry.bluesync.client.R;
import terry.bluesync.client.protocol.BluesyncMessage;
import terry.bluesync.client.protocol.BluesyncProtoUtil;

public class TestCaseActivity extends Activity {
    private static final String TAG = TestCaseActivity.class.getSimpleName();
    public static final String EXTRA_BLUE_ADDRESS = "blue_address";
    private static final String LARGE_DATA = "The Spring Framework provides a comprehensive programming and configuration model for modern Java-based enterprise applications - on any kind of deployment platform. A key element of Spring is infrastructural support at the application level: Spring focuses on the \"plumbing\" of enterprise applications so that teams can focus on application-level business logic, without unnecessary ties to specific deployment environments.";

    private BluesyncClient mBluesyncClient;
    private String mAddress;

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
                        mBluesyncClient.sendRequest(message, new CaseResponseCallback());
                        printLog("Request, data=" + message);
                        break;
                    case PUSH:
                        mBluesyncClient.pushData(message);
                        printLog("Push, data=" + message);
                        break;
                }
            } catch (BluesyncException e) {
                printLog(e.toString());
            }
        };

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
        setContentView(R.layout.activity_test_list);

        initBluesync();
        initView();
        initCases();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mBluesyncClient != null) {
            mBluesyncClient.connect(mAddress, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mBluesyncClient != null) {
            mBluesyncClient.disconnect(null);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mConnection);
    }

    private void initBluesync() {
        mAddress = getIntent().getStringExtra(EXTRA_BLUE_ADDRESS);
        boundService();
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
            mBluesyncClient = adapter.getBluesyncClient();
            mBluesyncClient.addListener(mListener);

            mBluesyncClient.connect(mAddress, null);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (mBluesyncClient != null) {
                mBluesyncClient.removeListener(mListener);
                mBluesyncClient = null;
            }
        }
    };

    private Listener mListener = new BluesyncClient.Listener() {

        @Override
        public void onSateChange(BluesyncClient.STATE state) {
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
        mListView = (ListView) findViewById(R.id.wifiList);
        mListView.setOnItemClickListener(mItemListener);

        mScrollView = (ScrollView) findViewById(R.id.scroll);
        mLogText = (TextView) findViewById(R.id.log_text);
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

    private void initCases() {
        mRootCaseGroup.add(createRequestCase());
        mRootCaseGroup.add(createPushCase());

        mListView.setAdapter(new ArrayAdapter(this,
                android.R.layout.simple_list_item_1, mRootCaseGroup.getTitleList()));
    }

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
