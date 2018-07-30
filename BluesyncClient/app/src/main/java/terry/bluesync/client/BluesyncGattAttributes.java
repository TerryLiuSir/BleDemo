package terry.bluesync.client;

import java.util.UUID;

public class BluesyncGattAttributes {
    public static final UUID BLUESYNC_SERVICE = UUID.fromString("0000ff88-0000-1000-8000-00805f9b34fb");
    public static final UUID WRITE_CHARACTERISTIC = UUID.fromString("0000fec7-0000-1000-8000-00805f9b34fb");
    public static final UUID INDICATE_CHARACTERISTIC = UUID.fromString("0000fec8-0000-1000-8000-00805f9b34fb");
    public static final UUID READ_CHARACTERISTIC = UUID.fromString("0000fec9-0000-1000-8000-00805f9b34fb");
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
}
