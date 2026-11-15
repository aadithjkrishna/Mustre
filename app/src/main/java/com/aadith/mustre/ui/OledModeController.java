package com.aadith.mustre.ui;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import com.aadith.mustre.MainActivity;
import com.aadith.mustre.R;
import com.aadith.mustre.network.ServerPrefs;

/**
 * Controller managing OLED Mode: transitions UI into a pure black, power-saving state
 * after 10 seconds of user inactivity, and smoothly wakes up upon any touch interaction.
 *
 * Includes anti-burn-in measures:
 * 1. Automatic header collapse for Now Playing cover.
 * 2. 60-second periodic micro-pixel shifting (subtle 2dp jitter) on static playback controls.
 * 3. 85% reduced peak luminance on static controls during OLED mode to reduce diode thermal stress.
 */
public class OledModeController {

    private static final long INACTIVITY_DELAY_MS = 10_000L; // 10 seconds
    private static final long PIXEL_SHIFT_INTERVAL_MS = 60_000L; // 60 seconds
    private static final long ANIM_DURATION_MS = 380L;

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PanelAnimationController panelAnimationController;

    private View oledBlackScrim;
    private View btnSearchToggle;
    private View btnSettings;
    private View badgeAudioQuality;
    private View btnLyrics;
    private View btnSongListToggle;

    private View btnPlayPause;
    private View btnNext;
    private View btnPrevious;
    private View seekbarAudio;
    private View seekbarContainer;
    private View controlsContainer;

    private boolean isOledEnabled = false;
    private boolean isOledActive = false;
    private boolean isInteractingWithBasicControls = false;
    private int pixelShiftStep = 0;

    private final Runnable inactivityRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isOledEnabled || activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            // If user is actively inside the song list / queue picker, postpone entering OLED
            if (panelAnimationController != null && panelAnimationController.isPortraitSongListOpen()) {
                resetInactivityTimer();
                return;
            }
            // If user is actively searching songs, postpone entering OLED
            View searchBarContainer = activity.findViewById(R.id.search_bar_container);
            if (searchBarContainer != null && searchBarContainer.getVisibility() == View.VISIBLE && searchBarContainer.getAlpha() > 0.5f) {
                resetInactivityTimer();
                return;
            }
            enterOledMode(true);
        }
    };

    private final Runnable pixelShiftRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isOledActive || activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            applyPixelShift();
            handler.postDelayed(this, PIXEL_SHIFT_INTERVAL_MS);
        }
    };

    public OledModeController(Activity activity, PanelAnimationController panelAnimationController) {
        this.activity = activity;
        this.panelAnimationController = panelAnimationController;
        initViews();
    }

    public void setPanelAnimationController(PanelAnimationController controller) {
        this.panelAnimationController = controller;
    }

    public void initViews() {
        oledBlackScrim = activity.findViewById(R.id.oled_black_scrim);
        btnSearchToggle = activity.findViewById(R.id.btn_search_toggle);
        btnSettings = activity.findViewById(R.id.btn_settings);
        badgeAudioQuality = activity.findViewById(R.id.badge_audio_quality);
        btnLyrics = activity.findViewById(R.id.btn_lyrics);
        btnSongListToggle = activity.findViewById(R.id.btn_song_list_toggle);

        btnPlayPause = activity.findViewById(R.id.btn_play_pause);
        btnNext = activity.findViewById(R.id.btn_next);
        btnPrevious = activity.findViewById(R.id.btn_previous);
        seekbarAudio = activity.findViewById(R.id.seekbar_audio);
        seekbarContainer = activity.findViewById(R.id.seekbar_container);
        controlsContainer = activity.findViewById(R.id.controls_container);
    }

    public void onResume() {
        isOledEnabled = ServerPrefs.isOledModeEnabled(activity);
        if (isOledEnabled) {
            resetInactivityTimer();
        } else {
            cancelInactivityTimer();
            if (isOledActive) {
                exitOledMode(false);
            }
        }
    }

    public void onPause() {
        cancelInactivityTimer();
        handler.removeCallbacks(pixelShiftRunnable);
    }

    /**
     * Handles touch dispatch during OLED mode.
     * Basic controls (play/pause, next, previous, seekbar) can be operated while keeping OLED mode active.
     * Touching anywhere else wakes up the full normal UI.
     */
    public void handleDispatchTouchEvent(MotionEvent ev) {
        if (ev == null) return;

        if (!isOledEnabled) {
            cancelInactivityTimer();
            if (isOledActive) {
                exitOledMode(false);
            }
            return;
        }

        if (!isOledActive) {
            resetInactivityTimer();
            return;
        }

        int action = ev.getActionMasked();
        float rawX = ev.getRawX();
        float rawY = ev.getRawY();

        if (action == MotionEvent.ACTION_DOWN) {
            if (isTouchInsideBasicControls(rawX, rawY)) {
                isInteractingWithBasicControls = true;
                resetInactivityTimer();
            } else {
                isInteractingWithBasicControls = false;
                exitOledMode(true);
                resetInactivityTimer();
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (isInteractingWithBasicControls) {
                isInteractingWithBasicControls = false;
                resetInactivityTimer();
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (isInteractingWithBasicControls) {
                resetInactivityTimer();
            }
        }
    }

    private boolean isTouchInsideBasicControls(float rawX, float rawY) {
        if (isTouchInsideView(btnPlayPause, rawX, rawY, 14)) return true;
        if (isTouchInsideView(btnNext, rawX, rawY, 14)) return true;
        if (isTouchInsideView(btnPrevious, rawX, rawY, 14)) return true;
        if (isTouchInsideView(seekbarAudio, rawX, rawY, 16)) return true;
        if (isTouchInsideView(seekbarContainer, rawX, rawY, 10)) return true;
        return false;
    }

    private boolean isTouchInsideView(View view, float rawX, float rawY, int paddingDp) {
        if (view == null || view.getVisibility() != View.VISIBLE) return false;
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        float density = view.getResources().getDisplayMetrics().density;
        int padPx = (int) (paddingDp * density);

        int left = location[0] - padPx;
        int top = location[1] - padPx;
        int right = location[0] + view.getWidth() + padPx;
        int bottom = location[1] + view.getHeight() + padPx;

        return rawX >= left && rawX <= right && rawY >= top && rawY <= bottom;
    }

    public void onUserInteraction() {
        if (!isOledEnabled) {
            cancelInactivityTimer();
            if (isOledActive) {
                exitOledMode(false);
            }
            return;
        }

        // If currently blacked out in OLED mode, smoothly wake up UI
        if (isOledActive) {
            exitOledMode(true);
        }

        // Reset the 10-second idle countdown
        resetInactivityTimer();
    }

    public void resetInactivityTimer() {
        cancelInactivityTimer();
        if (isOledEnabled) {
            handler.postDelayed(inactivityRunnable, INACTIVITY_DELAY_MS);
        }
    }

    public void cancelInactivityTimer() {
        handler.removeCallbacks(inactivityRunnable);
    }

    public boolean isOledActive() {
        return isOledActive;
    }

    public boolean isOledEnabled() {
        return isOledEnabled;
    }

    public void enterOledMode(boolean animate) {
        if (isOledActive) return;
        isOledActive = true;

        // 1. Black scrim overlay
        if (oledBlackScrim != null) {
            oledBlackScrim.setVisibility(View.VISIBLE);
            if (animate) {
                oledBlackScrim.animate().cancel();
                oledBlackScrim.animate()
                        .alpha(1.0f)
                        .setDuration(ANIM_DURATION_MS)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start();
            } else {
                oledBlackScrim.setAlpha(1.0f);
            }
        }

        // 2. Collapse cover and song title/artist into top header if in normal Now Playing cover view
        if (panelAnimationController != null && !panelAnimationController.isAnyPortraitPanelOpen()) {
            panelAnimationController.collapseHeaderForOled(true, animate);
        }

        // 3. Fade out non-essential chrome buttons
        fadeViewOut(btnSearchToggle, animate);
        fadeViewOut(btnSettings, animate);
        fadeViewOut(badgeAudioQuality, animate);
        fadeViewOut(btnLyrics, animate);
        fadeViewOut(btnSongListToggle, animate);

        // 4. Tone down luminance on static controls to 85% to protect OLED diodes from burn-in
        if (controlsContainer != null) {
            controlsContainer.animate().cancel();
            controlsContainer.animate().alpha(0.85f).setDuration(ANIM_DURATION_MS).start();
        }
        if (seekbarContainer != null) {
            seekbarContainer.animate().cancel();
            seekbarContainer.animate().alpha(0.85f).setDuration(ANIM_DURATION_MS).start();
        }

        // 5. Start periodic 60-second micro pixel shift
        pixelShiftStep = 0;
        handler.removeCallbacks(pixelShiftRunnable);
        handler.postDelayed(pixelShiftRunnable, PIXEL_SHIFT_INTERVAL_MS);

        // 6. Update visualizer state
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).updateVisualizerState();
        }
    }

    public void exitOledMode(boolean animate) {
        if (!isOledActive) return;
        isOledActive = false;
        isInteractingWithBasicControls = false;

        // 1. Remove black scrim
        if (oledBlackScrim != null) {
            if (animate) {
                oledBlackScrim.animate().cancel();
                oledBlackScrim.animate()
                        .alpha(0.0f)
                        .setDuration(ANIM_DURATION_MS)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .withEndAction(() -> {
                            if (!isOledActive) {
                                oledBlackScrim.setVisibility(View.GONE);
                            }
                        })
                        .start();
            } else {
                oledBlackScrim.setAlpha(0.0f);
                oledBlackScrim.setVisibility(View.GONE);
            }
        }

        // 2. Expand header back down to standard Now Playing size if it was collapsed for OLED
        if (panelAnimationController != null && panelAnimationController.isHeaderCollapsedForOled()) {
            panelAnimationController.collapseHeaderForOled(false, animate);
        }

        // 3. Restore all chrome buttons
        fadeViewIn(btnSearchToggle, animate);
        fadeViewIn(btnSettings, animate);
        fadeViewIn(badgeAudioQuality, animate);
        fadeViewIn(btnLyrics, animate);
        fadeViewIn(btnSongListToggle, animate);

        // 4. Stop pixel shifting and reset controls translation & 100% luminance
        handler.removeCallbacks(pixelShiftRunnable);
        View controlsContainer = activity.findViewById(R.id.controls_container);
        View seekbarContainer = activity.findViewById(R.id.seekbar_container);
        if (controlsContainer != null) {
            controlsContainer.animate().cancel();
            controlsContainer.animate().translationX(0f).translationY(0f).alpha(1.0f).setDuration(250).start();
        }
        if (seekbarContainer != null) {
            seekbarContainer.animate().cancel();
            seekbarContainer.animate().translationX(0f).translationY(0f).alpha(1.0f).setDuration(250).start();
        }

        // 5. Update visualizer state
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).updateVisualizerState();
        }
    }

    private void applyPixelShift() {
        pixelShiftStep = (pixelShiftStep + 1) % 4;
        float density = activity.getResources().getDisplayMetrics().density;
        float shiftPx = 2f * density;
        float dx = (pixelShiftStep == 1) ? shiftPx : (pixelShiftStep == 3) ? -shiftPx : 0f;
        float dy = (pixelShiftStep == 0) ? -shiftPx : (pixelShiftStep == 2) ? shiftPx : 0f;

        View controlsContainer = activity.findViewById(R.id.controls_container);
        View seekbarContainer = activity.findViewById(R.id.seekbar_container);

        if (controlsContainer != null) {
            controlsContainer.animate().translationX(dx).translationY(dy).setDuration(600).start();
        }
        if (seekbarContainer != null) {
            seekbarContainer.animate().translationX(dx).translationY(dy).setDuration(600).start();
        }
    }

    private void fadeViewOut(View view, boolean animate) {
        if (view == null) return;
        if (animate) {
            view.animate().cancel();
            view.animate()
                    .alpha(0.0f)
                    .setDuration(ANIM_DURATION_MS)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .withEndAction(() -> {
                        if (isOledActive) {
                            view.setVisibility(View.INVISIBLE);
                        }
                    })
                    .start();
        } else {
            view.setAlpha(0.0f);
            view.setVisibility(View.INVISIBLE);
        }
    }

    private void fadeViewIn(View view, boolean animate) {
        if (view == null) return;
        view.setVisibility(View.VISIBLE);
        if (animate) {
            view.animate().cancel();
            view.animate()
                    .alpha(1.0f)
                    .setDuration(ANIM_DURATION_MS)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        } else {
            view.setAlpha(1.0f);
        }
    }
}
