package com.smarthome.app;

import android.os.Handler;
import android.os.Looper;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SmartHomeApi {

    public interface StatusCallback {
        void onSuccess(DeviceStatus status);
        void onError(String message);
    }

    public interface ActionCallback {
        void onSuccess(String response);
        void onError(String message);
    }

    private final OkHttpClient client;
    private final Gson gson;
    private final Handler mainHandler;
    private String baseUrl;

    public SmartHomeApi(String initialHost) {
        this.baseUrl = normalizeUrl(initialHost);
        this.client = new OkHttpClient.Builder()
                .connectTimeout(2500, TimeUnit.MILLISECONDS)
                .readTimeout(2500, TimeUnit.MILLISECONDS)
                .writeTimeout(2500, TimeUnit.MILLISECONDS)
                .build();
        this.gson = new Gson();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void setBaseUrl(String hostOrIp) {
        this.baseUrl = normalizeUrl(hostOrIp);
    }

    public String getBaseUrl() {
        return this.baseUrl;
    }

    private String normalizeUrl(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "http://smarthome.local";
        }
        String s = input.trim();
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            s = "http://" + s;
        }
        if (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    public void fetchStatus(StatusCallback callback) {
        String url = baseUrl + "/api/status";
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // If smarthome.local failed, try Hotspot fallback 192.168.4.1
                if (baseUrl.contains("smarthome.local")) {
                    String fallbackUrl = "http://192.168.4.1/api/status";
                    Request fbReq = new Request.Builder().url(fallbackUrl).build();
                    client.newCall(fbReq).enqueue(new Callback() {
                        @Override
                        public void onFailure(Call c2, IOException e2) {
                            mainHandler.post(() -> callback.onError("ডিভাইস পাওয়া যায়নি: " + e.getMessage()));
                        }

                        @Override
                        public void onResponse(Call c2, Response r2) throws IOException {
                            if (r2.isSuccessful() && r2.body() != null) {
                                String body = r2.body().string();
                                baseUrl = "http://192.168.4.1"; // Switch to working IP
                                try {
                                    DeviceStatus status = gson.fromJson(body, DeviceStatus.class);
                                    mainHandler.post(() -> callback.onSuccess(status));
                                } catch (Exception parseEx) {
                                    mainHandler.post(() -> callback.onError("JSON Parse Error: " + parseEx.getMessage()));
                                }
                            } else {
                                mainHandler.post(() -> callback.onError("HTTP Error: " + r2.code()));
                            }
                        }
                    });
                } else {
                    mainHandler.post(() -> callback.onError("সংযোগ ব্যর্থ: " + e.getMessage()));
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    try {
                        DeviceStatus status = gson.fromJson(json, DeviceStatus.class);
                        mainHandler.post(() -> callback.onSuccess(status));
                    } catch (Exception parseEx) {
                        mainHandler.post(() -> callback.onError("JSON Parse Error: " + parseEx.getMessage()));
                    }
                } else {
                    mainHandler.post(() -> callback.onError("HTTP Error " + response.code()));
                }
            }
        });
    }

    public void setRelay(int relayId, boolean state, ActionCallback callback) {
        String url = baseUrl + "/api/relay?id=" + relayId + "&state=" + (state ? 1 : 0);
        executeGet(url, callback);
    }

    public void setServoSpeed(int speed, ActionCallback callback) {
        String url = baseUrl + "/api/servo?speed=" + speed;
        executeGet(url, callback);
    }

    public void setAutoMode(int state, ActionCallback callback) {
        String url = baseUrl + "/api/auto_mode?state=" + state;
        executeGet(url, callback);
    }

    public void setAutoTimer(int seconds, ActionCallback callback) {
        String url = baseUrl + "/api/auto_timer?sec=" + seconds;
        executeGet(url, callback);
    }

    public void cancelAutoTimer(ActionCallback callback) {
        String url = baseUrl + "/api/auto_timer?cancel=1";
        executeGet(url, callback);
    }

    public void setRelayTimer(int relayId, int totalSeconds, int targetState, ActionCallback callback) {
        String url = baseUrl + "/api/timer?id=" + relayId + "&sec=" + totalSeconds + "&target=" + targetState;
        executeGet(url, callback);
    }

    public void cancelRelayTimer(int relayId, ActionCallback callback) {
        String url = baseUrl + "/api/timer?id=" + relayId + "&cancel=1";
        executeGet(url, callback);
    }

    public void saveClimateConfig(int targetTemp, int maxTemp, int calib, ActionCallback callback) {
        String url = baseUrl + "/api/climate_config?target=" + targetTemp + "&max=" + maxTemp + "&calib=" + calib;
        executeGet(url, callback);
    }

    private void executeGet(String url, ActionCallback callback) {
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(e.getMessage()));
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (callback != null) {
                    String resStr = response.isSuccessful() ? "OK" : "Error " + response.code();
                    mainHandler.post(() -> callback.onSuccess(resStr));
                }
            }
        });
    }
}
