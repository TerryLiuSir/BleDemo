package terry.bluesync.client.util;

import android.util.Log;

public class LogUtil {
    private static final String MAIN_TAG = "BluesyncClient";
    private static final String ERROR_TAG = "Error";
    private static final String EVENT_TAG = "Event";

    public static void d(String tag, String msg) {
        Log.d(MAIN_TAG, tag + ":  " + msg);
    }

    public static void i(String tag, String msg) {
        Log.i(MAIN_TAG, tag + ":  " + msg);
    }

    public static void e(String tag, String msg) {
        Log.e(MAIN_TAG, tag + ":  " + msg);
    }



    public static void e(String subTag, String message, Throwable throwable) {
        Log.d(subTag, message + ", " + throwable.toString());

        Log.d(MAIN_TAG, ERROR_TAG + "/" + subTag + ": " + message + ", " + throwable.toString());
        StackTraceElement[] stackElements = throwable.getStackTrace();
        if (stackElements != null) {
            for (int i = 0; i < stackElements.length; i++) {
                Log.d(MAIN_TAG, ERROR_TAG + "/" + subTag + ": " + "          " + stackElements[i]);
            }
        }
    }

    public static void event(String subTag, String message) {
//        if (AirsyncApp.isEnableEventLog()) {
        Log.d(MAIN_TAG, EVENT_TAG + "/" + subTag + ": " + message);
//        }
    }
}
