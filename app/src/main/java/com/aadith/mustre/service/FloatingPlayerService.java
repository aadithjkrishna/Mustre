package com.aadith.mustre.service;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import com.aadith.mustre.MainActivity;
import com.aadith.mustre.R;
import com.aadith.mustre.model.Song;

@OptIn(markerClass = UnstableApi.class)
public class FloatingPlayerService extends Service implements MusicService.MusicChangeListener {

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;

    private ImageView ivCover;
    private TextView tvTitle;
    private TextView tvArtist;
    private ImageButton btnPlayPause;
    private View bar1, bar2, bar3, bar4;

    private MusicService musicService;
    private boolean isBound = false;

    // Adjustable width modes (Compact 175dp, Medium 220dp, Expanded 280dp)
    private final int[] widthPresetsDp = new int[]{175, 220, 280};
    private int currentPresetIndex = 1; // Default medium (220dp)

    private final Handler visualizerHandler = new Handler(Looper.getMainLooper());
    private Runnable visualizerRunnable;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            isBound = true;

            musicService.addMusicChangeListener(FloatingPlayerService.this);
            syncWithMusicService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            musicService = null;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        ContextThemeWrapper themeContext = new ContextThemeWrapper(this, R.style.Theme_Mustre);
        floatingView = LayoutInflater.from(themeContext).inflate(R.layout.layout_floating_popup_player, null);

        ivCover = floatingView.findViewById(R.id.iv_floating_cover);
        tvTitle = floatingView.findViewById(R.id.tv_floating_title);
        tvArtist = floatingView.findViewById(R.id.tv_floating_artist);
        btnPlayPause = floatingView.findViewById(R.id.btn_floating_play_pause);
        ImageButton btnClose = floatingView.findViewById(R.id.btn_floating_close);

        bar1 = floatingView.findViewById(R.id.floating_bar1);
        bar2 = floatingView.findViewById(R.id.floating_bar2);
        bar3 = floatingView.findViewById(R.id.floating_bar3);
        bar4 = floatingView.findViewById(R.id.floating_bar4);

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int initialWidthPx = (int) (widthPresetsDp[currentPresetIndex] * metrics.density);
        int initialHeightPx = (int) (58 * metrics.density);

        params = new WindowManager.LayoutParams(
                initialWidthPx,
                initialHeightPx,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = (metrics.widthPixels - initialWidthPx) / 2;
        params.y = (int) (120 * metrics.density);

        setupTouchAndDragListener();

        btnPlayPause.setOnClickListener(v -> {
            if (musicService != null) {
                musicService.togglePlayPause();
            }
        });

        btnClose.setOnClickListener(v -> stopSelf());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        try {
            windowManager.addView(floatingView, params);
        } catch (Exception ignored) {
            stopSelf();
            return;
        }

        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("DROP_X") && intent.hasExtra("DROP_Y")) {
            int dropX = intent.getIntExtra("DROP_X", params.x);
            int dropY = intent.getIntExtra("DROP_Y", params.y);

            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int cardWidth = params.width > 0 ? params.width : (int) (220 * metrics.density);
            int cardHeight = params.height > 0 ? params.height : (int) (58 * metrics.density);

            params.x = Math.max(0, Math.min(metrics.widthPixels - cardWidth, dropX - (cardWidth / 2)));
            params.y = Math.max(0, Math.min(metrics.heightPixels - cardHeight, dropY - (cardHeight / 2)));

            if (floatingView != null && windowManager != null && floatingView.isAttachedToWindow()) {
                try {
                    windowManager.updateViewLayout(floatingView, params);
                } catch (Exception ignored) {}
            }
        }
        return START_NOT_STICKY;
    }

    private void setupTouchAndDragListener() {
        View root = floatingView.findViewById(R.id.floating_root);
        if (root == null) return;

        root.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private long lastTapTime = 0;
            private final Handler tapHandler = new Handler(Looper.getMainLooper());
            private Runnable singleTapRunnable;

            // Pinch gesture parameters
            private float initialPinchDistance = 0f;
            private int initialWidth = 0;
            private boolean isPinching = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                DisplayMetrics metrics = getResources().getDisplayMetrics();
                int minWidthPx = (int) (160 * metrics.density);
                int maxWidthPx = (int) (340 * metrics.density);

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isPinching = false;
                        return true;

                    case MotionEvent.ACTION_POINTER_DOWN:
                        if (event.getPointerCount() == 2) {
                            isPinching = true;
                            initialPinchDistance = getFingerDistance(event);
                            initialWidth = params.width;
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (isPinching && event.getPointerCount() == 2) {
                            float currentDistance = getFingerDistance(event);
                            if (initialPinchDistance > 10f) {
                                float scale = currentDistance / initialPinchDistance;
                                int newWidth = (int) (initialWidth * scale);
                                params.width = Math.max(minWidthPx, Math.min(maxWidthPx, newWidth));
                                try {
                                    windowManager.updateViewLayout(floatingView, params);
                                } catch (Exception ignored) {}
                            }
                            return true;
                        }

                        if (!isPinching) {
                            float deltaX = event.getRawX() - initialTouchX;
                            float deltaY = event.getRawY() - initialTouchY;

                            params.x = initialX + (int) deltaX;
                            params.y = initialY + (int) deltaY;

                            try {
                                windowManager.updateViewLayout(floatingView, params);
                            } catch (Exception ignored) {}
                            return true;
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_POINTER_UP:
                        if (!isPinching) {
                            float deltaX = Math.abs(event.getRawX() - initialTouchX);
                            float deltaY = Math.abs(event.getRawY() - initialTouchY);

                            if (deltaX < 8 && deltaY < 8) {
                                long currentTime = System.currentTimeMillis();
                                if (currentTime - lastTapTime < 300) {
                                    // Cancel single tap action
                                    if (singleTapRunnable != null) {
                                        tapHandler.removeCallbacks(singleTapRunnable);
                                        singleTapRunnable = null;
                                    }
                                    lastTapTime = 0;
                                    // Double tap toggles preset width (175dp -> 220dp -> 280dp)
                                    currentPresetIndex = (currentPresetIndex + 1) % widthPresetsDp.length;
                                    int targetWidthDp = widthPresetsDp[currentPresetIndex];
                                    params.width = (int) (targetWidthDp * metrics.density);
                                    try {
                                        windowManager.updateViewLayout(floatingView, params);
                                    } catch (Exception ignored) {}
                                    Toast.makeText(FloatingPlayerService.this, "Pop Up Width: " + targetWidthDp + "dp", Toast.LENGTH_SHORT).show();
                                } else {
                                    lastTapTime = currentTime;
                                    // Single tap brings main app to front after confirming it's not a double tap
                                    singleTapRunnable = () -> {
                                        Intent openAppIntent = new Intent(FloatingPlayerService.this, MainActivity.class);
                                        openAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                        startActivity(openAppIntent);
                                    };
                                    tapHandler.postDelayed(singleTapRunnable, 300);
                                }
                            }
                        }
                        isPinching = false;
                        return true;
                }
                return false;
            }

            private float getFingerDistance(MotionEvent event) {
                if (event.getPointerCount() < 2) return 0f;
                float x = event.getX(0) - event.getX(1);
                float y = event.getY(0) - event.getY(1);
                return (float) Math.hypot(x, y);
            }
        });
    }

    private void syncWithMusicService() {
        if (musicService == null) return;

        Song song = musicService.getCurrentSong();
        if (song != null) {
            onSongChanged(song, musicService.getCurrentSongIndex());
        }

        Bitmap cover = musicService.getCurrentCoverBitmap();
        if (cover != null) {
            onCoverLoaded(cover);
        }

        onPlaybackStateChanged(musicService.isPlaying());
    }

    @Override
    public void onSongChanged(Song song, int index) {
        if (song == null) return;
        if (tvTitle != null) {
            tvTitle.setText(song.getTitle() != null && !song.getTitle().isEmpty() ? song.getTitle() : "Unknown Title");
        }
        if (tvArtist != null) {
            tvArtist.setText(song.getArtist() != null && !song.getArtist().isEmpty() ? song.getArtist() : "Unknown Artist");
        }
        updateVisualizerState();
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        if (btnPlayPause != null) {
            btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        }
        updateVisualizerState();
    }

    @Override
    public void onCoverLoaded(Bitmap bitmap) {
        if (bitmap != null) {
            if (ivCover != null) ivCover.setImageBitmap(bitmap);
        }
    }

    @Override
    public void onPlayerError(String errorMessage) {
        if (btnPlayPause != null) {
            btnPlayPause.setImageResource(R.drawable.ic_play);
        }
        updateVisualizerState();
    }

    private void updateVisualizerState() {
        boolean shouldPlay = musicService != null && musicService.isPlaying();
        if (shouldPlay) {
            startVisualizerAnimation();
        } else {
            stopVisualizerAnimation();
        }
    }

    private void startVisualizerAnimation() {
        if (bar1 == null || bar2 == null || bar3 == null || bar4 == null) return;

        float barHeight = 15f * getResources().getDisplayMetrics().density;
        bar1.setPivotY(bar1.getHeight() > 0 ? bar1.getHeight() : barHeight);
        bar2.setPivotY(bar2.getHeight() > 0 ? bar2.getHeight() : barHeight);
        bar3.setPivotY(bar3.getHeight() > 0 ? bar3.getHeight() : barHeight);
        bar4.setPivotY(bar4.getHeight() > 0 ? bar4.getHeight() : barHeight);

        if (visualizerRunnable != null) return;

        visualizerRunnable = new Runnable() {
            @Override
            public void run() {
                if (musicService != null && musicService.isPlaying()) {
                    float s1 = (float) (0.25 + Math.random() * 0.75);
                    float s2 = (float) (0.25 + Math.random() * 0.75);
                    float s3 = (float) (0.25 + Math.random() * 0.75);
                    float s4 = (float) (0.25 + Math.random() * 0.75);

                    bar1.animate().scaleY(s1).setDuration(60).start();
                    bar2.animate().scaleY(s2).setDuration(60).start();
                    bar3.animate().scaleY(s3).setDuration(60).start();
                    bar4.animate().scaleY(s4).setDuration(60).start();

                    visualizerHandler.postDelayed(this, 35);
                } else {
                    stopVisualizerAnimation();
                }
            }
        };
        visualizerHandler.post(visualizerRunnable);
    }

    private void stopVisualizerAnimation() {
        if (visualizerRunnable != null) {
            visualizerHandler.removeCallbacks(visualizerRunnable);
            visualizerRunnable = null;
        }

        if (bar1 != null && bar2 != null && bar3 != null && bar4 != null) {
            bar1.animate().cancel();
            bar2.animate().cancel();
            bar3.animate().cancel();
            bar4.animate().cancel();

            bar1.animate().scaleY(0.18f).setDuration(180).start();
            bar2.animate().scaleY(0.18f).setDuration(180).start();
            bar3.animate().scaleY(0.18f).setDuration(180).start();
            bar4.animate().scaleY(0.18f).setDuration(180).start();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopVisualizerAnimation();

        if (isBound) {
            if (musicService != null) {
                musicService.removeMusicChangeListener(FloatingPlayerService.this);
            }
            unbindService(serviceConnection);
            isBound = false;
        }

        if (windowManager != null && floatingView != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception ignored) {}
        }

        super.onDestroy();
    }
}
