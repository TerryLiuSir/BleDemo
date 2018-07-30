package com.ragentek.projector;

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

import terry.bluesync.client.BluesyncAdapter;
import terry.bluesync.client.BluesyncClient;
import terry.bluesync.client.BluesyncClient.Listener;
import terry.bluesync.client.BluesyncClient.ResponseCallback;
import terry.bluesync.client.BluesyncException;
import terry.bluesync.client.BluesyncService;
import terry.bluesync.client.R;
import terry.bluesync.client.protocol.BluesyncMessage;
import terry.bluesync.client.protocol.BluesyncProtoUtil;

public class BluesyncProtocolTestActivity extends Activity {
    private static final String TAG = BluesyncProtocolTestActivity.class.getSimpleName();
    public static final String EXTRA_BLUE_ADDRESS = "blue_address";
    private static final String LARGE_DATA = "The Spring Framework provides a comprehensive programming and configuration model for modern Java-based enterprise applications - on any kind of deployment platform. A key element of Spring is infrastructural support at the application level: Spring focuses on the \"plumbing\" of enterprise applications so that teams can focus on application-level business logic, without unnecessary ties to specific deployment environments.";

    private BluesyncClient mBluesyncClient;
    private String mAddress;

    private ListView mListView;
    private TextView mLogText;
    private ScrollView mScrollView;

    private Handler mHandler = new Handler();
    private final CaseItemGroup mRootCaseGroup = new CaseItemGroup();

    private abstract class CaseItem {
        private String title;

        public CaseItem(String title) {
            this.title = title;
        }

        public void test() {
            try {
                BluesyncMessage message = getMessage();
                mBluesyncClient.sendRequest(message, new MyResponseCallback());

                printLog("=======Send request ========");
                printLog(message.toString());
            } catch (BluesyncException e) {
                printLog(e.toString());
            }
        }

        abstract public BluesyncMessage getMessage();

        class MyResponseCallback implements ResponseCallback {
            @Override
            public void onSuccess(BluesyncMessage protobufData) {
                printLog("=======Response message ========");
                printLog(protobufData.toString());
            }

            private void printSimple(BluesyncMessage protobufData) {
                printLog("[" + protobufData.getSeqId() + "], cmdId=" + protobufData.getCmdId());
            }

            @Override
            public void onError(String message) {
                printLog("=======Response error ========");
                printLog(message);
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

    private Listener mListener = new Listener() {

        @Override
        public void onSateChange(BluesyncClient.STATE state) {
            printLog("state=" + state.toString());
        }

        @Override
        public void onReceive(BluesyncMessage message) {
            printLog("=======Receive message ========");
            printLog(message.toString());
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
        mRootCaseGroup.add(createConnection());
        mRootCaseGroup.add(createDisconnection());
        mRootCaseGroup.add(createConnection100Times());
        mRootCaseGroup.add(createSendData());
        mRootCaseGroup.add(createPushData());

        mListView.setAdapter(new ArrayAdapter(this,
                android.R.layout.simple_list_item_1, mRootCaseGroup.getTitleList()));
    }

    private CaseItem createConnection() {
        return new CaseItem("connect test") {
            @Override
            public void test() {
                mConnectionTimes = 0;
                mBluesyncClient.connect(mAddress, null);
            }

            @Override
            public BluesyncMessage getMessage() {
                return null;
            }
        };
    }

    private CaseItem createDisconnection() {
        return new CaseItem("disconnect test") {
            @Override
            public void test() {
                mBluesyncClient.disconnect(null);
            }

            @Override
            public BluesyncMessage getMessage() {
                return null;
            }
        };
    }

    private final static int MAX_CONNECTION_TIME = 100;
    private int mConnectionTimes = 0;

    private CaseItem createConnection100Times() {
        return new CaseItem("connect stress test(100 times)") {
            @Override
            public void test() {
                mConnectionTimes = 0;
                mHandler.post(mDisconnectionRunnable);
            }

            @Override
            public BluesyncMessage getMessage() {
                return null;
            }
        };
    }

    private Runnable mConnectionRunnable = new Runnable() {
        @Override
        public void run() {
            if (mConnectionTimes++ > MAX_CONNECTION_TIME) {
                printLog("connection " + MAX_CONNECTION_TIME + " times success");
                return;
            }

            mBluesyncClient.connect(mAddress, new BluesyncClient.ConnectionCallback() {

                @Override
                public void onSuccess() {
                    mHandler.post(mDisconnectionRunnable);
                }

                @Override
                public void onFailure(String msg) {
                    printLog("connection " + mConnectionTimes + " times failure");
                }
            });
        }
    };

    private Runnable mDisconnectionRunnable = new Runnable() {
        @Override
        public void run() {
            mBluesyncClient.disconnect(new BluesyncClient.DisconnectionCallback() {
                @Override
                public void onFinish() {
                    mHandler.postDelayed(mConnectionRunnable, 1000);
                }
            });
        }
    };

    private CaseItem createSendData() {
        return new CaseItem("send data") {
            @Override
            public BluesyncMessage getMessage() {
                return BluesyncProtoUtil.getSendDataRequest("client send data request".getBytes());
            }
        };
    }

    private CaseItem createPushData() {
        return new CaseItem("push data") {
            @Override
            public BluesyncMessage getMessage() {
                return BluesyncProtoUtil.getRecvDataPush("client push data request".getBytes());
            }
        };
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
