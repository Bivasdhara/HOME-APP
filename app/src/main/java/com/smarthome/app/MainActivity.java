package com.smarthome.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "SmartHomePrefs";
    private static final String KEY_HOST = "device_host";
    private static final long POLL_INTERVAL_MS = 1500;

    // Networking
    private SmartHomeApi api;
    private SharedPreferences prefs;
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private boolean isPolling = false;
    private boolean isUserSeekingFan = false;
    private boolean isUpdatingRelayUI = false;

    // UI - Header & Status
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvDeviceIp;
    private TextView tvStatusBadge;
    private ImageButton btnSettings;

    // UI - Climate
    private TextView tvWeatherSummary;
    private TextView tvRealTemp;
    private TextView tvFeelsLike;
    private TextView tvHumidity;

    // UI - Servo / Smart AC Fan
    private MaterialButtonToggleGroup toggleModeGroup;
    private TextView tvFanAngle;
    private TextView tvModeStatusText;
    private TextView tvFanSpeedPercent;
    private SeekBar seekBarFan;
    private Button btnStepMinus;
    private Button btnStepPlus;
    private TextView tvAutoTimerTxt;
    private Button btnAutoTimer15;
    private Button btnAutoTimer30;
    private Button btnAutoTimer60;
    private Button btnCancelAutoTimer;

    // UI - 4 Relays
    private final LinearLayout[] cardRelays = new LinearLayout[4];
    private final TextView[] tvRelayTimers = new TextView[4];
    private final ImageButton[] btnRelayTimerDialogs = new ImageButton[4];
    private final SwitchMaterial[] swRelays = new SwitchMaterial[4];

    // Current State Cache
    private DeviceStatus currentStatus;
    private Vibrator vibrator;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPolling) {
                fetchDeviceStatus(false);
                pollHandler.postDelayed(this, POLL_INTERVAL_MS);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String host = prefs.getString(KEY_HOST, "smarthome.local");
        api = new SmartHomeApi(host);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        initViews();
        setupListeners();
    }

    private void initViews() {
        swipeRefresh = findViewById(R.id.swipeRefresh);
        tvDeviceIp = findViewById(R.id.tvDeviceIp);
        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        btnSettings = findViewById(R.id.btnSettings);

        tvWeatherSummary = findViewById(R.id.tvWeatherSummary);
        tvRealTemp = findViewById(R.id.tvRealTemp);
        tvFeelsLike = findViewById(R.id.tvFeelsLike);
        tvHumidity = findViewById(R.id.tvHumidity);

        toggleModeGroup = findViewById(R.id.toggleModeGroup);
        tvFanAngle = findViewById(R.id.tvFanAngle);
        tvModeStatusText = findViewById(R.id.tvModeStatusText);
        tvFanSpeedPercent = findViewById(R.id.tvFanSpeedPercent);
        seekBarFan = findViewById(R.id.seekBarFan);
        btnStepMinus = findViewById(R.id.btnStepMinus);
        btnStepPlus = findViewById(R.id.btnStepPlus);

        tvAutoTimerTxt = findViewById(R.id.tvAutoTimerTxt);
        btnAutoTimer15 = findViewById(R.id.btnAutoTimer15);
        btnAutoTimer30 = findViewById(R.id.btnAutoTimer30);
        btnAutoTimer60 = findViewById(R.id.btnAutoTimer60);
        btnCancelAutoTimer = findViewById(R.id.btnCancelAutoTimer);

        // Relays
        cardRelays[0] = findViewById(R.id.cardRelay0);
        cardRelays[1] = findViewById(R.id.cardRelay1);
        cardRelays[2] = findViewById(R.id.cardRelay2);
        cardRelays[3] = findViewById(R.id.cardRelay3);

        tvRelayTimers[0] = findViewById(R.id.tvTimerR0);
        tvRelayTimers[1] = findViewById(R.id.tvTimerR1);
        tvRelayTimers[2] = findViewById(R.id.tvTimerR2);
        tvRelayTimers[3] = findViewById(R.id.tvTimerR3);

        btnRelayTimerDialogs[0] = findViewById(R.id.btnTimerDialog0);
        btnRelayTimerDialogs[1] = findViewById(R.id.btnTimerDialog1);
        btnRelayTimerDialogs[2] = findViewById(R.id.btnTimerDialog2);
        btnRelayTimerDialogs[3] = findViewById(R.id.btnTimerDialog3);

        swRelays[0] = findViewById(R.id.swRelay0);
        swRelays[1] = findViewById(R.id.swRelay1);
        swRelays[2] = findViewById(R.id.swRelay2);
        swRelays[3] = findViewById(R.id.swRelay3);

        tvDeviceIp.setText(api.getBaseUrl());
    }

    private void setupListeners() {
        swipeRefresh.setOnRefreshListener(() -> fetchDeviceStatus(true));

        btnSettings.setOnClickListener(v -> {
            vibrateTap();
            showSettingsDialog();
        });

        // Mode Toggle
        toggleModeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            vibrateTap();
            int newMode = (checkedId == R.id.btnModeAuto) ? 1 : 0;
            api.setAutoMode(newMode, new SmartHomeApi.ActionCallback() {
                @Override
                public void onSuccess(String response) {
                    fetchDeviceStatus(false);
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(MainActivity.this, "মোড পরিবর্তনে সমস্যা: " + message, Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Servo Slider
        seekBarFan.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    tvFanSpeedPercent.setText(progress + "%");
                    int angle = Math.round((progress * 270.0f) / 100.0f);
                    tvFanAngle.setText("সার্ভো এঙ্গেল: " + angle + "° (সর্বোচ্চ ০°-২৭০° রেঞ্জ)");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeekingFan = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeekingFan = false;
                vibrateTap();
                int progress = seekBar.getProgress();
                api.setServoSpeed(progress, new SmartHomeApi.ActionCallback() {
                    @Override
                    public void onSuccess(String response) {
                        fetchDeviceStatus(false);
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(MainActivity.this, "গতি পরিবর্তনে ত্রুটি: " + message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        // Stepper Buttons (+ / - 5%)
        btnStepMinus.setOnClickListener(v -> {
            vibrateTap();
            int current = seekBarFan.getProgress();
            int next = Math.max(0, current - 5);
            seekBarFan.setProgress(next);
            tvFanSpeedPercent.setText(next + "%");
            api.setServoSpeed(next, null);
        });

        btnStepPlus.setOnClickListener(v -> {
            vibrateTap();
            int current = seekBarFan.getProgress();
            int next = Math.min(100, current + 5);
            seekBarFan.setProgress(next);
            tvFanSpeedPercent.setText(next + "%");
            api.setServoSpeed(next, null);
        });

        // Auto Timer Quick Buttons
        btnAutoTimer15.setOnClickListener(v -> setAutoTimerMinutes(15));
        btnAutoTimer30.setOnClickListener(v -> setAutoTimerMinutes(30));
        btnAutoTimer60.setOnClickListener(v -> setAutoTimerMinutes(60));

        btnCancelAutoTimer.setOnClickListener(v -> {
            vibrateTap();
            api.cancelAutoTimer(new SmartHomeApi.ActionCallback() {
                @Override
                public void onSuccess(String response) {
                    Toast.makeText(MainActivity.this, "অটো টাইমার বাতিল করা হয়েছে", Toast.LENGTH_SHORT).show();
                    fetchDeviceStatus(false);
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(MainActivity.this, "টাইমার বাতিলে সমস্যা", Toast.LENGTH_SHORT).show();
                }
            });
        });

        // 4 Relays Switched & Timers
        for (int i = 0; i < 4; i++) {
            final int relayId = i;
            swRelays[i].setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isUpdatingRelayUI) return;
                vibrateTap();
                api.setRelay(relayId, isChecked, new SmartHomeApi.ActionCallback() {
                    @Override
                    public void onSuccess(String response) {
                        fetchDeviceStatus(false);
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(MainActivity.this, "রিলে " + (relayId + 1) + " নিয়ন্ত্রণে ত্রুটি", Toast.LENGTH_SHORT).show();
                        // Revert visually on error
                        fetchDeviceStatus(false);
                    }
                });
            });

            btnRelayTimerDialogs[i].setOnClickListener(v -> {
                vibrateTap();
                showRelayTimerDialog(relayId);
            });
        }
    }

    private void setAutoTimerMinutes(int minutes) {
        vibrateTap();
        int seconds = minutes * 60;
        api.setAutoTimer(seconds, new SmartHomeApi.ActionCallback() {
            @Override
            public void onSuccess(String response) {
                Toast.makeText(MainActivity.this, minutes + " মিনিট পর অটো মোড চালু হবে", Toast.LENGTH_SHORT).show();
                fetchDeviceStatus(false);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(MainActivity.this, "টাইমার সেট ব্যর্থ", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchDeviceStatus(boolean fromSwipe) {
        api.fetchStatus(new SmartHomeApi.StatusCallback() {
            @Override
            public void onSuccess(DeviceStatus status) {
                swipeRefresh.setRefreshing(false);
                updateUiWithStatus(status);
            }

            @Override
            public void onError(String message) {
                swipeRefresh.setRefreshing(false);
                tvStatusBadge.setText("অফলাইন ⚠️");
                tvStatusBadge.setTextColor(getColor(R.color.accent_coral));
            }
        });
    }

    private void updateUiWithStatus(DeviceStatus status) {
        this.currentStatus = status;

        // Connection Badge
        tvStatusBadge.setText("অনলাইন 🟢");
        tvStatusBadge.setTextColor(getColor(R.color.accent_green));
        tvDeviceIp.setText(api.getBaseUrl().replace("http://", ""));

        // Climate Section
        if (status.dhtOk == 1) {
            tvRealTemp.setText(String.format(Locale.getDefault(), "%.1f°C", status.temp));
            tvFeelsLike.setText(String.format(Locale.getDefault(), "%.1f°C", status.feelsLike));
            tvHumidity.setText(String.format(Locale.getDefault(), "%.0f%%", status.hum));

            // Google Weather style feels-like description
            float fl = status.feelsLike;
            if (fl < 25.0f) {
                tvWeatherSummary.setText("আরামদায়ক আবহাওয়া 🌿");
                tvWeatherSummary.setTextColor(getColor(R.color.accent_green));
            } else if (fl < 30.0f) {
                tvWeatherSummary.setText("স্বাভাবিক আবহাওয়া ☀️");
                tvWeatherSummary.setTextColor(getColor(R.color.primary_glow));
            } else if (fl < 35.0f) {
                tvWeatherSummary.setText("উষ্ণ ও আর্দ্র অনুভূত 🔥");
                tvWeatherSummary.setTextColor(getColor(R.color.accent_amber));
            } else {
                tvWeatherSummary.setText("চরম তাপদাহ সতর্কতা ⚠️");
                tvWeatherSummary.setTextColor(getColor(R.color.accent_coral));
            }
        } else {
            tvRealTemp.setText("--");
            tvFeelsLike.setText("--");
            tvHumidity.setText("--");
            tvWeatherSummary.setText("সেন্সর রিডিং অমিল");
            tvWeatherSummary.setTextColor(getColor(R.color.text_muted));
        }

        // Fan / Servo Mode
        if (status.autoMode == 1) {
            toggleModeGroup.check(R.id.btnModeAuto);
            tvModeStatusText.setText("❄️ অটো মোড সক্রিয় (স্মার্ট এসি অ্যালগরিদম)");
            seekBarFan.setEnabled(false);
            btnStepMinus.setEnabled(false);
            btnStepPlus.setEnabled(false);
        } else {
            toggleModeGroup.check(R.id.btnModeManual);
            tvModeStatusText.setText("🖐️ ম্যানুয়াল গতি নিয়ন্ত্রণ");
            seekBarFan.setEnabled(true);
            btnStepMinus.setEnabled(true);
            btnStepPlus.setEnabled(true);
        }

        if (!isUserSeekingFan) {
            seekBarFan.setProgress(status.fanSpeed);
            tvFanSpeedPercent.setText(status.fanSpeed + "%");
            tvFanAngle.setText("সার্ভো এঙ্গেল: " + status.fanAngle + "° (সর্বোচ্চ ০°-২৭০° রেঞ্জ)");
        }

        // Auto Switch Timer display
        if (status.autoTimerSec > 0) {
            long min = status.autoTimerSec / 60;
            long sec = status.autoTimerSec % 60;
            tvAutoTimerTxt.setText(String.format(Locale.getDefault(), "⏱️ অটোতে ফিরবে: %02d:%02d", min, sec));
            btnCancelAutoTimer.setVisibility(View.VISIBLE);
        } else {
            tvAutoTimerTxt.setText("⏱️ অটোতে ফেরার টাইমার:");
            btnCancelAutoTimer.setVisibility(View.GONE);
        }

        // Relays
        if (status.relays != null && status.relays.size() >= 4) {
            isUpdatingRelayUI = true;
            for (int i = 0; i < 4; i++) {
                DeviceStatus.RelayStatus r = status.relays.get(i);
                boolean isOn = (r.state == 1);
                swRelays[i].setChecked(isOn);
                cardRelays[i].setBackgroundResource(isOn ? R.drawable.card_bg_active : R.drawable.card_bg);

                // Timer text
                if (r.timerSec > 0) {
                    long m = r.timerSec / 60;
                    long s = r.timerSec % 60;
                    String targetWord = (r.timerTarget == 1) ? "চালু" : "বন্ধ";
                    tvRelayTimers[i].setText(String.format(Locale.getDefault(), "⏳ %02d:%02d পর %s", m, s, targetWord));
                    tvRelayTimers[i].setTextColor(getColor(R.color.accent_amber));
                } else {
                    tvRelayTimers[i].setText(isOn ? "ডিভাইস চালু" : "ডিভাইস বন্ধ");
                    tvRelayTimers[i].setTextColor(getColor(R.color.text_muted));
                }
            }
            isUpdatingRelayUI = false;
        }
    }

    private void showSettingsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, null);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(40, 20, 40, 20);

        final EditText etHost = new EditText(this);
        etHost.setHint("যেমন: smarthome.local অথবা 192.168.4.1");
        etHost.setText(api.getBaseUrl().replace("http://", ""));
        container.addView(etHost);

        new MaterialAlertDialogBuilder(this)
                .setTitle("ডিভাইস সংযোগ কনফিগারেশন")
                .setMessage("স্মার্ট হোমের আইপি (IP) বা ডোমেন অ্যাড্রেস লিখুন:")
                .setView(container)
                .setPositiveButton("সেভ করুন", (dialog, which) -> {
                    String newHost = etHost.getText().toString().trim();
                    if (!newHost.isEmpty()) {
                        prefs.edit().putString(KEY_HOST, newHost).apply();
                        api.setBaseUrl(newHost);
                        tvDeviceIp.setText(newHost);
                        fetchDeviceStatus(true);
                    }
                })
                .setNeutralButton("ক্যালিব্রেশন মেনু", (dialog, which) -> showClimateCalibrationDialog())
                .setNegativeButton("বাতিল", null)
                .show();
    }

    private void showClimateCalibrationDialog() {
        if (currentStatus == null) {
            Toast.makeText(this, "আগে ডিভাইসের সাথে সংযোগ স্থাপন করুন", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(40, 20, 40, 20);

        TextView lbl1 = new TextView(this);
        lbl1.setText("টার্গেট তাপমাত্রা (°C):");
        container.addView(lbl1);
        final EditText etTarget = new EditText(this);
        etTarget.setText(String.valueOf(currentStatus.targetTemp));
        container.addView(etTarget);

        TextView lbl2 = new TextView(this);
        lbl2.setText("সর্বোচ্চ তাপমাত্রা (°C):");
        container.addView(lbl2);
        final EditText etMax = new EditText(this);
        etMax.setText(String.valueOf(currentStatus.maxTemp));
        container.addView(etMax);

        TextView lbl3 = new TextView(this);
        lbl3.setText("ক্যালিব্রেশন অফসেট (°C, যেমন 0, -2, 2):");
        container.addView(lbl3);
        final EditText etCalib = new EditText(this);
        etCalib.setText(String.valueOf(currentStatus.tempCalibration));
        container.addView(etCalib);

        new MaterialAlertDialogBuilder(this)
                .setTitle("🌡️ ক্লাইমেট ও সেন্সর ক্যালিব্রেশন")
                .setView(container)
                .setPositiveButton("সংরক্ষণ", (dialog, which) -> {
                    try {
                        int target = Integer.parseInt(etTarget.getText().toString().trim());
                        int max = Integer.parseInt(etMax.getText().toString().trim());
                        int calib = Integer.parseInt(etCalib.getText().toString().trim());

                        api.saveClimateConfig(target, max, calib, new SmartHomeApi.ActionCallback() {
                            @Override
                            public void onSuccess(String response) {
                                Toast.makeText(MainActivity.this, "ক্যালিব্রেশন সফলভাবে সেভ হয়েছে", Toast.LENGTH_SHORT).show();
                                fetchDeviceStatus(false);
                            }

                            @Override
                            public void onError(String message) {
                                Toast.makeText(MainActivity.this, "সেভ করতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } catch (Exception ex) {
                        Toast.makeText(MainActivity.this, "সঠিক পূর্ণসংখ্যা লিখুন", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("বাতিল", null)
                .show();
    }

    private void showRelayTimerDialog(int relayId) {
        String[] relayNames = {"💡 লাইট", "⚡ ফ্যান পাওয়ার", "🔌 সকেট", "🌙 নাইট ল্যাম্প"};
        String title = relayNames[relayId] + " - টাইমার কাউন্টডাউন";

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(40, 20, 40, 20);

        TextView lblTime = new TextView(this);
        lblTime.setText("কাউন্টডাউন সময় (মিনিটে লিখুন):");
        container.addView(lblTime);

        final EditText etMinutes = new EditText(this);
        etMinutes.setHint("যেমন: 15, 30, 60");
        etMinutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        container.addView(etMinutes);

        TextView lblAction = new TextView(this);
        lblAction.setText("টাইমার শেষে কী হবে:");
        lblAction.setPadding(0, 16, 0, 8);
        container.addView(lblAction);

        final RadioGroup radioGroup = new RadioGroup(this);
        radioGroup.setOrientation(RadioGroup.HORIZONTAL);
        android.widget.RadioButton rbOff = new android.widget.RadioButton(this);
        rbOff.setId(View.generateViewId());
        rbOff.setText("অফ (Turn OFF)");
        rbOff.setChecked(true);
        radioGroup.addView(rbOff);

        android.widget.RadioButton rbOn = new android.widget.RadioButton(this);
        rbOn.setId(View.generateViewId());
        rbOn.setText("অন (Turn ON)");
        radioGroup.addView(rbOn);
        container.addView(radioGroup);

        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(container)
                .setPositiveButton("টাইমার শুরু", (dialog, which) -> {
                    String minStr = etMinutes.getText().toString().trim();
                    if (!minStr.isEmpty()) {
                        int mins = Integer.parseInt(minStr);
                        int targetState = rbOn.isChecked() ? 1 : 0;
                        api.setRelayTimer(relayId, mins * 60, targetState, new SmartHomeApi.ActionCallback() {
                            @Override
                            public void onSuccess(String response) {
                                Toast.makeText(MainActivity.this, mins + " মিনিটের টাইমার সক্রিয় হয়েছে", Toast.LENGTH_SHORT).show();
                                fetchDeviceStatus(false);
                            }

                            @Override
                            public void onError(String message) {
                                Toast.makeText(MainActivity.this, "টাইমার চালুর ব্যর্থতা", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                })
                .setNeutralButton("টাইমার বন্ধ", (dialog, which) -> {
                    api.cancelRelayTimer(relayId, new SmartHomeApi.ActionCallback() {
                        @Override
                        public void onSuccess(String response) {
                            Toast.makeText(MainActivity.this, "টাইমার বাতিল করা হয়েছে", Toast.LENGTH_SHORT).show();
                            fetchDeviceStatus(false);
                        }

                        @Override
                        public void onError(String message) {
                            Toast.makeText(MainActivity.this, "টাইমার বাতিলে সমস্যা", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("ফিরে যান", null)
                .show();
    }

    private void vibrateTap() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(25);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isPolling = true;
        pollHandler.post(pollRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isPolling = false;
        pollHandler.removeCallbacks(pollRunnable);
    }
}
