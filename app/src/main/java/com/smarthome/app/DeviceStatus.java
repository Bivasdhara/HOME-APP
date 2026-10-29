package com.smarthome.app;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class DeviceStatus {
    @SerializedName("wifi_mode")
    public int wifiMode;

    @SerializedName("ssid")
    public String ssid;

    @SerializedName("rssi")
    public int rssi;

    @SerializedName("ap_ip")
    public String apIp;

    @SerializedName("sta_ip")
    public String staIp;

    @SerializedName("uptime")
    public String uptime;

    @SerializedName("fan_speed")
    public int fanSpeed;

    @SerializedName("fan_angle")
    public int fanAngle;

    @SerializedName("temp")
    public float temp;

    @SerializedName("feels_like")
    public float feelsLike;

    @SerializedName("hum")
    public float hum;

    @SerializedName("dht_ok")
    public int dhtOk;

    @SerializedName("auto_mode")
    public int autoMode;

    @SerializedName("target_temp")
    public int targetTemp;

    @SerializedName("max_temp")
    public int maxTemp;

    @SerializedName("temp_calibration")
    public int tempCalibration;

    @SerializedName("auto_timer_sec")
    public long autoTimerSec;

    @SerializedName("relays")
    public List<RelayStatus> relays;

    public static class RelayStatus {
        @SerializedName("state")
        public int state; // 1 = ON, 0 = OFF

        @SerializedName("timer_sec")
        public long timerSec;

        @SerializedName("timer_target")
        public int timerTarget;
    }
}
