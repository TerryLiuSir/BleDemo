package terry.bluesync.client;

import com.alibaba.fastjson.JSON;

public class BluesyncDevice {
    private String model;
    private String address;
    private String ip;
    private String ssid;

    public BluesyncDevice() {}

    public BluesyncDevice(String model, String address) {
        this(model, address, null, null);
    }

    public BluesyncDevice(String model, String address, String ip, String ssid) {
        this.model = model;
        this.address = address;
        this.ip = ip;
        this.ssid = ssid;
    }

    public String getModel() {
        return model;
    }

    public String getAddress() {
        return address;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getIp() {
        return ip;
    }

    public void setSsid(String ssid) {
        this.ssid = ssid;
    }

    public String getSsid() {
        return ssid;
    }

    public boolean isNetworkEnable() {
        return ip != null;
    }

    public String toJson() {
        return JSON.toJSONString(this);
    }

    public static BluesyncDevice parseFromJson(String json) {
        return JSON.parseObject(json, BluesyncDevice.class);
    }

    @Override
    public boolean equals(Object o) {
        BluesyncDevice other = (BluesyncDevice) o;

        if (other == null) {
            return false;
        }

        if (this.getAddress().equals(other.getAddress())
                && this.getModel().equals(other.getModel())) {
            return true;
        }

        return false;
    }

    public String getId() {
        String[] segment = getAddress().split(":");
        int len = segment.length;
        if (len < 2) {
            return getModel();
        }

        return getModel() + "_" + segment[len-2] + segment[len-1];
    }

    @Override
    public String toString() {
        return model + "_" + address + ", ip=" + ip + ", ssid=" + ssid;
    }
}
