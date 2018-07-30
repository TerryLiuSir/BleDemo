package terry.bluesync.server.util;

import android.bluetooth.BluetoothAdapter;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class DeviceUtil {
    private static final String TAG = DeviceUtil.class.getSimpleName();

    public static String getBtMacAddressString() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            LogUtil.e(TAG, "couldn't find bluetoothManagerService");
            return null;
        }

        return bluetoothAdapter.getAddress();
    }

    public static byte[] getBtMacAddressBytes() {
        byte[] result = {00, 00, 00, 00, 00, 00};

        String btAddress = getBtMacAddressString();
        if (btAddress == null || btAddress.isEmpty()) {
            return result;
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            String[] macBytes = btAddress.split(":");

            for (String mac : macBytes) {
                output.write(Integer.parseInt(mac, 16));
            }

            result = output.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            LogUtil.e(TAG, "Get bluetooth address error, btAddress=" + btAddress);
        } finally {
            try {
                output.close();
            } catch (IOException e) {
            }
        }

        return result;
    }

    private static String getWifiApIpString(){
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && !inetAddress.isLinkLocalAddress()) {
                        return inetAddress.getHostAddress().toString();
                    }
                }
            }
        } catch (SocketException ex) {
            LogUtil.e(TAG, ex.toString());
            ex.printStackTrace();
        }
        return null;
    }

    public static String getSerialNo() {
        return Build.SERIAL;
    }

    public static String getModel() {
        return "VOGA LP2";
    }


    public static String getHardwareVersion() {
        return "";
    }

    public static String getId() {
        String[] segment = getBtMacAddressString().split(":");
        int len = segment.length;
        if (len < 2) {
            return getModel();
        }

        return getModel() + "_" + segment[len-2] + segment[len-1];
    }

    public static String getAppUrl() {
        return "https://voga.com/projector2/app/download";
    }
}
