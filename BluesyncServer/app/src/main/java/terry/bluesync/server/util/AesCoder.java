package terry.bluesync.server.util;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;
import java.util.UUID;
import java.util.zip.CRC32;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;


public class AesCoder {
    /**
     * Secret key algorithm
     */
    private static final String KEY_ALGORITHM = "AES";

    /**
     * Secret key size
     */
    private static final int KEY_SIZE = 128;

    /**
     * Cipher Algorithm Name / Cipher Algorithm Mode / Cipher Algorithm Padding
     */
    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS7Padding";

    /**
     * Random value length
     */
    private static final int RAN_LEN = 4;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private static final byte[] KEY = { 0x35, 0x32, 0x30, 0x32, 0x30, 0x22, 0x33, 0x14, 0x30, 0x35, 0x20, 0x16, 0x30,
            0x37, 0x30, 0x38 };

    private static final byte[] IV = KEY;


    public static byte[] genKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance(KEY_ALGORITHM);
        keyGen.init(KEY_SIZE);

        SecretKey secretKey = keyGen.generateKey();
        return secretKey.getEncoded();
    }

    public static byte[] encrypt(byte[] key, byte[] iv, byte[] data) throws AesCoderException {
        Key secretKey = toKey(key);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM, "BC");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);

            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new AesCoderException(e);
        }
    }


    public static byte[] decrypt(byte[] key, byte[] iv,byte[] data) throws AesCoderException {
        Key secretKey = toKey(key);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM, "BC");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);

            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new AesCoderException(e);
        }
    }

    private static Key toKey(byte[] key) {
        SecretKey secretKey = new SecretKeySpec(key, KEY_ALGORITHM);
        return secretKey;
    }

    public static byte[] encodeAesSign(byte[] sn) {
        byte[] aesSign = null;

        try {
            byte[] ran = getRan();
            byte[] crc32Value = getCrc32(sn, ran);
            byte[] sign = genSign(sn , ran, crc32Value);

            aesSign = encrypt(KEY, IV, sign);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return aesSign;
    }

    private static byte[] getRan() {
        SecureRandom random = new SecureRandom();
        byte bytes[] = new byte[RAN_LEN];
        random.nextBytes(bytes);

        return bytes;
    }

    private static byte[] getCrc32(byte[] sn, byte[] ran) {
        int len = sn.length + ran.length;
        byte[] data = new byte[len];

        System.arraycopy(sn, 0, data, 0, sn.length);
        System.arraycopy(ran, 0, data, sn.length, ran.length);

        CRC32 crc32 = new CRC32();
        crc32.update(data);
        int crcValue = (int) crc32.getValue();

        return  ByteUtil.int2ByteArray(crcValue);
    }

    private static byte[] genSign(byte[] sn, byte[] ran, byte[] crc32Value) {
        int len = sn.length + ran.length + crc32Value.length;
        byte[] data = new byte[len];

        System.arraycopy(sn, 0, data, 0, sn.length);
        System.arraycopy(ran, 0, data, sn.length, ran.length);
        System.arraycopy(crc32Value, 0, data, sn.length + ran.length, crc32Value.length);
        return data;
    }

    public static boolean decodeAesSign(byte[] aesSign, byte[] sn) {
        try {
            byte[] decryptSign = decrypt(KEY, IV, aesSign);

            byte[] decryptSn = new byte[sn.length];
            byte[] decryptRan = new byte[RAN_LEN];
            byte[] decryptCrc32Value = new byte[4];

            System.arraycopy(decryptSign, 0, decryptSn, 0, decryptSn.length);
            System.arraycopy(decryptSign, decryptSn.length, decryptRan, 0, decryptRan.length);
            System.arraycopy(decryptSign, decryptSn.length + decryptRan.length, decryptCrc32Value, 0, decryptCrc32Value.length);

            byte[] crc32Value = getCrc32(decryptSn, decryptRan);

            return Arrays.equals(crc32Value, decryptCrc32Value);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public static byte[] genSessionKey() {
        try {
            return genKey();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static byte[] encodeAesSessionKey(byte[] data) throws AesCoderException {
        return encrypt(KEY, KEY, data);
    }

    public static byte[] decodeAesSessionKey(byte[] data) throws AesCoderException {
        return decrypt(KEY, KEY, data);
    }

    public static String genTicket() {
        return UUID.randomUUID().toString();
    }
}
