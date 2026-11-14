package com.aadith.mustre;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.aadith.mustre.network.HttpHandler;
import com.aadith.mustre.network.ServerPrefs;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQUEST_LOCAL_NETWORK = 1002;

    private EditText etServerUrl;
    private TextView tvConnectionStatus;
    private SwitchMaterial switchOfflineMode;
    private SwitchMaterial switchContrastProtection;
    private SwitchMaterial switchPopupPlayer;
    private SwitchMaterial switchOledMode;
    private LinearLayout containerSavedUrls;

    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        ImageButton btnBack = findViewById(R.id.btn_settings_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        etServerUrl = findViewById(R.id.et_settings_server_url);
        tvConnectionStatus = findViewById(R.id.tv_connection_status);
        switchOfflineMode = findViewById(R.id.switch_offline_mode);
        switchContrastProtection = findViewById(R.id.switch_contrast_protection);
        switchPopupPlayer = findViewById(R.id.switch_popup_player);
        switchOledMode = findViewById(R.id.switch_oled_mode);
        containerSavedUrls = findViewById(R.id.container_saved_server_urls);

        MaterialButton btnAddServerUrl = findViewById(R.id.btn_add_server_url);
        if (btnAddServerUrl != null) {
            btnAddServerUrl.setOnClickListener(v -> {
                if (etServerUrl != null) {
                    String input = etServerUrl.getText().toString().trim();
                    if (!input.isEmpty()) {
                        ServerPrefs.addSavedServerUrl(SettingsActivity.this, input);
                        ServerPrefs.setServerUrl(SettingsActivity.this, input);
                        etServerUrl.setText("");
                        renderSavedServerUrlsList();
                        testServerConnection();
                        Toast.makeText(this, "Address added and selected!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Enter a valid URL or IP", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        MaterialButton btnTestConnection = findViewById(R.id.btn_test_connection);
        MaterialButton btnClearCache = findViewById(R.id.btn_clear_cache);

        // Load saved preferences
        String currentUrl = ServerPrefs.getServerUrl(this);
        if (etServerUrl != null) {
            etServerUrl.setText(currentUrl);
        }

        renderSavedServerUrlsList();

        if (switchOfflineMode != null) {
            switchOfflineMode.setChecked(ServerPrefs.isOfflineModeEnabled(this));
            switchOfflineMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                ServerPrefs.setOfflineModeEnabled(this, isChecked);
                Toast.makeText(this, isChecked ? "Offline Mode Enabled" : "Offline Mode Disabled", Toast.LENGTH_SHORT).show();
            });
        }

        if (switchContrastProtection != null) {
            switchContrastProtection.setChecked(ServerPrefs.isContrastProtectionEnabled(this));
            switchContrastProtection.setOnCheckedChangeListener((buttonView, isChecked) -> {
                ServerPrefs.setContrastProtectionEnabled(this, isChecked);
                Toast.makeText(this, isChecked ? "Contrast Protection Enabled" : "Contrast Protection Disabled", Toast.LENGTH_SHORT).show();
            });
        }

        if (switchPopupPlayer != null) {
            switchPopupPlayer.setChecked(ServerPrefs.isPopupPlayerEnabled(this));
            switchPopupPlayer.setOnCheckedChangeListener((buttonView, isChecked) -> {
                ServerPrefs.setPopupPlayerEnabled(this, isChecked);
                Toast.makeText(this, isChecked ? "Floating Pop Up Player Enabled" : "Floating Pop Up Player Disabled", Toast.LENGTH_SHORT).show();

                if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!Settings.canDrawOverlays(this)) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("Pop Up Permission Required")
                                .setMessage("To allow floating pop up player over apps, please grant 'Display over other apps' permission.")
                                .setPositiveButton("Grant Permission", (dialog, which) -> {
                                    Intent intent = new Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:" + getPackageName())
                                    );
                                    startActivity(intent);
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                }
            });
        }

        if (switchOledMode != null) {
            switchOledMode.setChecked(ServerPrefs.isOledModeEnabled(this));
            switchOledMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                ServerPrefs.setOledModeEnabled(this, isChecked);
                Toast.makeText(this, isChecked ? "OLED Mode Enabled (10s idle)" : "OLED Mode Disabled", Toast.LENGTH_SHORT).show();
            });
        }

        Slider sliderPrefetchCount = findViewById(R.id.slider_prefetch_count);
        TextView tvPrefetchCountVal = findViewById(R.id.tv_prefetch_count_val);

        if (sliderPrefetchCount != null) {
            int currentPrefetch = ServerPrefs.getPrefetchCount(this);
            sliderPrefetchCount.setValue((float) currentPrefetch);
            if (tvPrefetchCountVal != null) {
                tvPrefetchCountVal.setText(currentPrefetch + (currentPrefetch == 1 ? " song" : " songs"));
            }

            sliderPrefetchCount.addOnChangeListener((slider, value, fromUser) -> {
                int count = (int) value;
                ServerPrefs.setPrefetchCount(SettingsActivity.this, count);
                if (tvPrefetchCountVal != null) {
                    tvPrefetchCountVal.setText(count + (count == 1 ? " song" : " songs"));
                }
            });
        }

        if (btnTestConnection != null) {
            btnTestConnection.setOnClickListener(v -> testServerConnection());
        }

        if (btnClearCache != null) {
            btnClearCache.setOnClickListener(v -> {
                ServerPrefs.clearAllCache(this);
                com.aadith.mustre.network.MediaCacheManager.clearCache(this);
                new Thread(() -> {
                    try {
                        com.bumptech.glide.Glide.get(getApplicationContext()).clearDiskCache();
                    } catch (Exception ignored) {}
                }).start();
                try {
                    com.bumptech.glide.Glide.get(getApplicationContext()).clearMemory();
                } catch (Exception ignored) {}
                Toast.makeText(this, "Local cache & metadata cleared!", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void renderSavedServerUrlsList() {
        if (containerSavedUrls == null) return;
        containerSavedUrls.removeAllViews();

        String activeUrl = ServerPrefs.getServerUrl(this);
        List<String> savedUrls = ServerPrefs.getSavedServerUrls(this);

        for (String url : savedUrls) {
            View itemView = getLayoutInflater().inflate(R.layout.item_saved_server_url, containerSavedUrls, false);
            ImageView ivIndicator = itemView.findViewById(R.id.iv_url_active_indicator);
            TextView tvUrl = itemView.findViewById(R.id.tv_saved_url);
            ImageView btnDelete = itemView.findViewById(R.id.btn_delete_saved_url);

            tvUrl.setText(url);
            boolean isActive = url.equalsIgnoreCase(activeUrl);

            if (isActive) {
                ivIndicator.setImageResource(R.drawable.ic_circle_checked);
                ivIndicator.setColorFilter(0xFF00FFCC);
                tvUrl.setTextColor(0xFF00FFCC);
                tvUrl.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                ivIndicator.setImageResource(R.drawable.ic_circle_outline);
                ivIndicator.setColorFilter(0x80FFFFFF);
                tvUrl.setTextColor(0xFFFFFFFF);
                tvUrl.setTypeface(null, android.graphics.Typeface.NORMAL);
            }

            itemView.setOnClickListener(v -> {
                ServerPrefs.setServerUrl(SettingsActivity.this, url);
                if (etServerUrl != null) {
                    etServerUrl.setText(url);
                }
                renderSavedServerUrlsList();
                testServerConnection();
            });

            btnDelete.setOnClickListener(v -> {
                ServerPrefs.removeSavedServerUrl(SettingsActivity.this, url);
                renderSavedServerUrlsList();
            });

            containerSavedUrls.addView(itemView);
        }
    }

    private void testServerConnection() {
        if (Build.VERSION.SDK_INT >= 37
                && checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_LOCAL_NETWORK}, REQUEST_LOCAL_NETWORK);
            return;
        }

        if (etServerUrl == null) return;
        String rawUrl = etServerUrl.getText().toString().trim();
        if (rawUrl.isEmpty()) {
            Toast.makeText(this, "Please enter a valid URL (e.g. 192.168.1.104:3000)", Toast.LENGTH_SHORT).show();
            return;
        }

        ServerPrefs.setServerUrl(this, rawUrl);
        String baseUrl = ServerPrefs.getServerUrl(this);
        if (!rawUrl.equals(baseUrl)) {
            etServerUrl.setText(baseUrl);
            etServerUrl.setSelection(baseUrl.length());
        }

        if (tvConnectionStatus != null) {
            tvConnectionStatus.setText("Testing...");
            tvConnectionStatus.setTextColor(0xB3FFFFFF);
        }

        HttpHandler httpHandler = new HttpHandler();
        String testSongsUrl = baseUrl + "/songs";

        httpHandler.makeHttpGetRequest(testSongsUrl, new HttpHandler.MusicDataCallback() {
            @Override
            public void onSuccess(String result) {
                runOnUiThread(() -> {
                    renderSavedServerUrlsList();
                    java.util.List<com.aadith.mustre.model.Song> songs = ServerPrefs.parseSongsResponse(result);
                    int count = songs != null ? songs.size() : 0;
                    if (tvConnectionStatus != null) {
                        tvConnectionStatus.setText("Connected (" + count + " songs) ✓");
                        tvConnectionStatus.setTextColor(0xFF4CAF50);
                    }
                    Toast.makeText(SettingsActivity.this, "Server connected! Found " + count + " songs.", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(Exception songEx) {
                // If /songs fails, try /api/songs
                String apiSongsUrl = baseUrl + "/api/songs";
                httpHandler.makeHttpGetRequest(apiSongsUrl, new HttpHandler.MusicDataCallback() {
                    @Override
                    public void onSuccess(String result) {
                        runOnUiThread(() -> {
                            java.util.List<com.aadith.mustre.model.Song> songs = ServerPrefs.parseSongsResponse(result);
                            int count = songs != null ? songs.size() : 0;
                            if (tvConnectionStatus != null) {
                                tvConnectionStatus.setText("Connected (/api/songs) ✓");
                                tvConnectionStatus.setTextColor(0xFF4CAF50);
                            }
                            Toast.makeText(SettingsActivity.this, "Connected via /api/songs (" + count + " songs)!", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(Exception apiEx) {
                        // If /api/songs also fails, test root /
                        httpHandler.makeHttpGetRequest(baseUrl, new HttpHandler.MusicDataCallback() {
                            @Override
                            public void onSuccess(String result) {
                                runOnUiThread(() -> {
                                    if (tvConnectionStatus != null) {
                                        tvConnectionStatus.setText("Server Online ✓ (No /songs)");
                                        tvConnectionStatus.setTextColor(0xFFFFB300);
                                    }
                                    Toast.makeText(SettingsActivity.this, "Server is online, but no songs endpoint found (/songs, /api/songs)", Toast.LENGTH_LONG).show();
                                });
                            }

                            @Override
                            public void onError(Exception baseError) {
                                runOnUiThread(() -> {
                                    String msg = baseError.getMessage() != null ? baseError.getMessage() : "Connection failed";
                                    if (tvConnectionStatus != null) {
                                        String shortMsg = msg.length() > 20 ? msg.substring(0, 17) + "..." : msg;
                                        tvConnectionStatus.setText("Failed (" + shortMsg + ") • Unreachable");
                                        tvConnectionStatus.setTextColor(0xFFFF5252);
                                    }
                                    Toast.makeText(SettingsActivity.this, "Cannot connect to server: " + msg, Toast.LENGTH_LONG).show();
                                });
                            }
                        });
                    }
                });
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_LOCAL_NETWORK) return;

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            testServerConnection();
        } else {
            Toast.makeText(this, "Allow Local network access to test the server.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
