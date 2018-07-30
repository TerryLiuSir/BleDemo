package terry.bluesync.client.util;

import android.util.Log;

import com.google.protobuf.ByteString;

import java.nio.ByteBuffer;
import java.util.Locale;


public class ByteUtil {
    public static final long MILLSECONDS_OF_SECOND = 1000;
    private static final String TAG = "Util";

    public static final class STATE_CHANGED {
        public static final byte ADD_RECORD = (byte) 49;
        public static final byte ADD_USER = (byte) 65;
        public static final byte DEL_RECORD = (byte) 50;
        public static final byte DEL_USER = (byte) 66;
        public static final byte EDIT_RECORD = (byte) 51;
        public static final byte EDIT_USER = (byte) 67;
        public static final byte END_MEASURE = (byte) 34;
        public static final byte LOW_POWER = (byte) 2;
        public static final byte SCALE_NAME_CHANGE = (byte) 1;
        public static final byte START_MEASURE = (byte) 33;
    }

    public static String ByteString2HexString(ByteString byteString) {
        byte[] ByteString2byteArray = ByteString2byteArray(byteString);
        return ByteString2byteArray == null ? null : byteArray2HexString(ByteString2byteArray, ByteString2byteArray.length);
    }

    public static byte[] ByteString2byteArray(ByteString byteString) {
        int size = byteString.size();
        if (size == 0) {
            return null;
        }
        byte[] bArr = new byte[size];
        byteString.copyTo(bArr, 0, 0, size);
        return bArr;
    }

    public static String byte2HexString(byte[] bArr) {
        if (bArr == null || bArr.length == 0) {
            Log.e(TAG, "Byte Array is null or empty");
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder(bArr.length);
        for (int i = 0; i < bArr.length; i++) {
            stringBuilder.append(byte2HexString(bArr[i]));
        }
        return stringBuilder.toString();
    }

    public static String byte2HexString(byte b) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(String.format("0x%02X ", new Object[]{Byte.valueOf(b)}));
        return stringBuilder.toString();
    }

    public static String byte2String(byte[] bArr) {
        if (bArr == null || bArr.length == 0) {
            Log.e(TAG, "Byte Array is null or empty");
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder(bArr.length);
        for (byte b : bArr) {
            stringBuilder.append((char) b);
        }
        return stringBuilder.toString();
    }

    public static int byte2ToInt(byte[] bArr) {
        return (bArr[1] & 255) | ((bArr[0] & 255) << 8);
    }

    public static int byte3ToInt(byte[] bArr) {
        return ((bArr[2] & 255) | ((bArr[1] & 255) << 8)) | ((bArr[0] & 255) << 16);
    }

    public static int byte4ToInt(byte[] bArr) {
        return (((bArr[3] & 255) | ((bArr[2] & 255) << 8)) | ((bArr[1] & 255) << 16)) | ((bArr[0] & 255) << 24);
    }

    public static String byteArray2HexString(byte[] bArr, int i) {
        StringBuilder stringBuilder = new StringBuilder(i);
        if (bArr.length < i) {
            Log.w(TAG, "data length is shorter then print command length");
            i = bArr.length;
        }
        for (int i2 = 0; i2 < i; i2++) {
            stringBuilder.append(String.format("%02X ", new Object[]{Byte.valueOf(bArr[i2])}));
        }
        return stringBuilder.toString();
    }

    public static String byteArray2String(byte[] bArr, int i) {
        StringBuilder stringBuilder = new StringBuilder(i);
        if (bArr.length < i) {
            Log.w(TAG, "data length is shorter then print command length");
            i = bArr.length;
        }
        for (int i2 = 0; i2 < i; i2++) {
            stringBuilder.append(String.format("%02x", new Object[]{Byte.valueOf(bArr[i2])}));
        }
        return stringBuilder.toString();
    }

    public static byte[] hexString2Byte(String str) {
        byte[] bArr;
        if (str == null || str.length() == 0) {
            Log.e(TAG, "String is null or nil");
            bArr = null;
        } else {
            String[] split = str.toUpperCase(Locale.US).split(" ");
            bArr = new byte[split.length];
            int length = split.length;
            int i = 0;
            int i2 = 0;
            while (i < length) {
                String str2 = split[i];
                if (str2.length() != 2) {
                    return null;
                }
                if (((str2.charAt(0) < '0' || str2.charAt(0) > '9') && (str2.charAt(0) < 'A' || str2.charAt(0) > 'F')) || ((str2.charAt(1) < '0' || str2.charAt(1) > '9') && (str2.charAt(1) < 'A' || str2.charAt(1) > 'F'))) {
                    Log.d(TAG, "invalid hex string");
                    return null;
                }
                bArr[i2] = (byte) ((((byte) str2.charAt(0)) >= STATE_CHANGED.ADD_USER ? ((str2.charAt(0) - 65) + 10) << 4 : (str2.charAt(0) - 48) << 4) | (((byte) str2.charAt(1)) >= STATE_CHANGED.ADD_USER ? (str2.charAt(1) - 65) + 10 : str2.charAt(1) - 48));
                i++;
                i2++;
            }
        }
        return bArr;
    }

    public static long hexString2Long(String str) {
        if (str == null || str.length() == 0) {
            Log.e(TAG, "Hex string is null or nil");
            return 0;
        }
        String[] split = str.toUpperCase(Locale.US).split(" ");
        Byte[] bArr = new Byte[split.length];
        int length = split.length;
        int i = 0;
        int i2 = 0;
        while (i < length) {
            String str2 = split[i];
            bArr[i2] = Byte.valueOf((byte) ((((byte) str2.charAt(0)) >= STATE_CHANGED.ADD_USER ? ((str2.charAt(0) - 65) + 10) << 4 : (str2.charAt(0) - 48) << 4) | (((byte) str2.charAt(1)) >= STATE_CHANGED.ADD_USER ? (str2.charAt(1) - 65) + 10 : str2.charAt(1) - 48)));
            i++;
            i2++;
        }
        long j = 0;
        int i3 = 0;
        int length2 = split.length - 1;
        while (i3 < bArr.length) {
            long longValue = ((255 & bArr[i3].longValue()) << (length2 << 3)) | j;
            i3++;
            length2--;
            j = longValue;
        }
        return j;
    }

    public static String long2MacString(long j) {
        int i = 0;
        byte[] bArr = new byte[6];
        for (int i2 = 0; i2 < 6; i2++) {
            bArr[i2] = (byte) ((int) (j >> (40 - (i2 << 3))));
        }
        StringBuilder stringBuilder = new StringBuilder();
        while (i < 6) {
            if (i != 0) {
                stringBuilder.append(":");
            }
            int i3 = bArr[i] & 255;
            if (i3 < 16) {
                stringBuilder.append("0");
            }
            stringBuilder.append(Integer.toHexString(i3));
            i++;
        }
        return stringBuilder.toString().toUpperCase(Locale.US);
    }

    public static long macString2Long(String str) {
        if (str == null || str.length() == 0) {
            Log.e(TAG, "mac string is null or nil");
            return 0;
        }
        String[] split = str.toUpperCase(Locale.US).split(":");
        Byte[] bArr = new Byte[split.length];
        int length = split.length;
        int i = 0;
        int i2 = 0;
        while (i < length) {
            String str2 = split[i];
            bArr[i2] = Byte.valueOf((byte) ((((byte) str2.charAt(0)) >= STATE_CHANGED.ADD_USER ? ((str2.charAt(0) - 65) + 10) << 4 : (str2.charAt(0) - 48) << 4) | (((byte) str2.charAt(1)) >= STATE_CHANGED.ADD_USER ? (str2.charAt(1) - 65) + 10 : str2.charAt(1) - 48)));
            i++;
            i2++;
        }
        long j = 0;
        int i3 = 0;
        int length2 = split.length - 1;
        while (i3 < bArr.length) {
            long longValue = ((255 & bArr[i3].longValue()) << (length2 << 3)) | j;
            i3++;
            length2--;
            j = longValue;
        }
        return j;
    }

    public static byte[] long2ByteArray(long value) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(value);
        return buffer.array();
    }

    public static byte[] int2ByteArray(int value) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(value);
        return buffer.array();
    }
}

