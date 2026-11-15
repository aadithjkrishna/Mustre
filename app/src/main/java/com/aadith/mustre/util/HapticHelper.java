package com.aadith.mustre.util;

import android.content.Context;
import android.os.Build;
import android.os.CombinedVibration;
import android.os.SystemClock;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

import com.aadith.mustre.network.ServerPrefs;

/**
 * Utility for delivering tactile, mechanical haptic feedback across Mustre.
 * Optimized for Android 14+ (API 34+) rich haptic primitives with variable strength
 * and dedicated line/word lyrics sync haptics.
 */
public final class HapticHelper {

    private static long lastScrubTickTime = 0L;
    private static final long MIN_SCRUB_INTERVAL_MS = 45L;

    private static long lastRotaryKnobTickTime = 0L;
    private static final long MIN_ROTARY_INTERVAL_MS = 20L;

    private static long lastScrollTickTime = 0L;
    private static final long MIN_SCROLL_INTERVAL_MS = 22L;

    private static long lastWordHapticTime = 0L;
    private static final long MIN_WORD_INTERVAL_MS = 60L;

    private HapticHelper() {}

    /**
     * Relative scale factor based on user's selected haptic strength (Light=0.40, Medium=0.75, Strong=1.00).
     */
    public static float getStrengthScale(@NonNull Context context) {
        int strength = ServerPrefs.getHapticStrength(context);
        switch (strength) {
            case ServerPrefs.HAPTIC_STRENGTH_LIGHT:
                return 0.40f;
            case ServerPrefs.HAPTIC_STRENGTH_STRONG:
                return 1.00f;
            case ServerPrefs.HAPTIC_STRENGTH_MEDIUM:
            default:
                return 0.75f;
        }
    }

    /**
     * Relative scale factor for line-based lyrics haptics (Light=0.45, Medium=0.75, Strong=1.00).
     */
    public static float getLineLyricsStrengthScale(@NonNull Context context) {
        int strength = ServerPrefs.getLineLyricsHapticStrength(context);
        switch (strength) {
            case ServerPrefs.HAPTIC_STRENGTH_LIGHT:
                return 0.45f;
            case ServerPrefs.HAPTIC_STRENGTH_STRONG:
                return 1.00f;
            case ServerPrefs.HAPTIC_STRENGTH_MEDIUM:
            default:
                return 0.75f;
        }
    }

    /**
     * Relative scale factor for word-based lyrics haptics (Light=0.45, Medium=0.75, Strong=1.00).
     */
    public static float getWordLyricsStrengthScale(@NonNull Context context) {
        int strength = ServerPrefs.getWordLyricsHapticStrength(context);
        switch (strength) {
            case ServerPrefs.HAPTIC_STRENGTH_LIGHT:
                return 0.45f;
            case ServerPrefs.HAPTIC_STRENGTH_STRONG:
                return 1.00f;
            case ServerPrefs.HAPTIC_STRENGTH_MEDIUM:
            default:
                return 0.75f;
        }
    }

    /**
     * Relative amplitude (1..255) based on user's selected haptic strength.
     */
    public static int getStrengthAmplitude(@NonNull Context context) {
        int strength = ServerPrefs.getHapticStrength(context);
        switch (strength) {
            case ServerPrefs.HAPTIC_STRENGTH_LIGHT:
                return 80;
            case ServerPrefs.HAPTIC_STRENGTH_STRONG:
                return 255;
            case ServerPrefs.HAPTIC_STRENGTH_MEDIUM:
            default:
                return 165;
        }
    }

    /**
     * Crisp confirmation click for primary playback (Play / Pause).
     */
    public static void playPauseClick(@Nullable View view) {
        if (!isEnabled(view)) return;
        performFeedback(view, HapticFeedbackConstants.CONFIRM, VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f, 20, 190);
    }

    /**
     * Snappy, tactile response for Next / Previous track buttons and catalog items.
     */
    public static void lightTick(@Nullable View view) {
        if (!isEnabled(view)) return;
        performFeedback(view, HapticFeedbackConstants.KEYBOARD_TAP, VibrationEffect.Composition.PRIMITIVE_TICK, 0.7f, 12, 140);
    }

    /**
     * Mechanical toggle feedback for on/off switches and toggle buttons.
     */
    public static void toggle(@Nullable View view, boolean state) {
        if (!isEnabled(view)) return;
        int feedback = state ? HapticFeedbackConstants.TOGGLE_ON : HapticFeedbackConstants.TOGGLE_OFF;
        performFeedback(view, feedback, VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f, 15, 160);
    }

    /**
     * Detent tick for discrete steps (e.g. slider intervals, queue item swaps, threshold crossed).
     */
    public static void segmentTick(@Nullable View view) {
        if (!isEnabled(view)) return;
        performFeedback(view, HapticFeedbackConstants.SEGMENT_TICK, VibrationEffect.Composition.PRIMITIVE_TICK, 0.75f, 12, 150);
    }

    /**
     * Tactile rotary knob tick modeled after physical mechanical dial notches
     * (identical to Samsung Clock rotary dial and WearOS rotating crown).
     */
    public static void performRotaryKnobTick(@Nullable View view) {
        if (!isEnabled(view)) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastRotaryKnobTickTime < MIN_ROTARY_INTERVAL_MS) {
            return;
        }
        lastRotaryKnobTickTime = now;

        Context context = view.getContext();
        float strengthScale = getStrengthScale(context);
        float scale = Math.min(1.0f, Math.max(0.1f, 0.70f * strengthScale));
        int amplitude = Math.min(255, Math.max(1, (int) (160 * strengthScale)));

        boolean performed = false;
        try {
            performed = view.performHapticFeedback(
                    HapticFeedbackConstants.CLOCK_TICK,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            );
        } catch (Exception ignored) {}

        if (!performed) {
            try {
                performed = view.performHapticFeedback(
                        HapticFeedbackConstants.SEGMENT_TICK,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                );
            } catch (Exception ignored) {}
        }

        if (!performed) {
            executePrimitiveOrWaveform(context, VibrationEffect.Composition.PRIMITIVE_TICK, scale, 12, amplitude);
        }
    }

    /**
     * Subtle, weighted mechanical tick when scrolling through lists and lyrics.
     * Emulates physical wheel inertia and weighted item detents.
     */
    public static void performScrollTick(@Nullable View view) {
        if (!isEnabled(view)) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastScrollTickTime < MIN_SCROLL_INTERVAL_MS) {
            return;
        }
        lastScrollTickTime = now;

        Context context = view.getContext();
        float strengthScale = getStrengthScale(context);
        float scale = Math.min(1.0f, Math.max(0.08f, 0.55f * strengthScale));
        int amplitude = Math.min(255, Math.max(1, (int) (125 * strengthScale)));

        boolean performed = false;
        try {
            performed = view.performHapticFeedback(
                    HapticFeedbackConstants.CLOCK_TICK,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            );
        } catch (Exception ignored) {}

        if (!performed) {
            try {
                performed = view.performHapticFeedback(
                        HapticFeedbackConstants.SEGMENT_TICK,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                );
            } catch (Exception ignored) {}
        }

        if (!performed) {
            executePrimitiveOrWaveform(context, VibrationEffect.Composition.PRIMITIVE_LOW_TICK, scale, 12, amplitude);
        }
    }

    /**
     * RecyclerView OnScrollListener that produces a tactile, weighted detent sensation
     * as items scroll past, simulating physical inertia and mass.
     */
    public static class WeightedScrollListener extends RecyclerView.OnScrollListener {
        private int accumulatedDy = 0;
        private final int stepPx;
        private boolean isUserDragging = false;

        public WeightedScrollListener(Context context, int itemHeightDp) {
            float density = context.getResources().getDisplayMetrics().density;
            this.stepPx = Math.max(1, (int) (itemHeightDp * density));
        }

        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                isUserDragging = true;
                accumulatedDy = 0;
            } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                isUserDragging = false;
                accumulatedDy = 0;
            }
        }

        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            if (!isUserDragging) {
                return;
            }
            accumulatedDy += dy;
            if (Math.abs(accumulatedDy) >= stepPx) {
                accumulatedDy %= stepPx;
                performScrollTick(recyclerView);
            }
        }
    }

    /**
     * Attaches weighted inertia scroll haptics to a NestedScrollView (e.g. plain text lyrics).
     */
    public static void attachWeightedScrollListener(@Nullable NestedScrollView scrollView, int stepDp) {
        if (scrollView == null) return;
        float density = scrollView.getContext().getResources().getDisplayMetrics().density;
        final int stepPx = Math.max(1, (int) (stepDp * density));
        final int[] accumulatedDy = new int[]{0};

        scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            int dy = scrollY - oldScrollY;
            if (dy == 0) return;
            accumulatedDy[0] += dy;
            if (Math.abs(accumulatedDy[0]) >= stepPx) {
                accumulatedDy[0] %= stepPx;
                performScrollTick(v);
            }
        });
    }

    /**
     * Tactile pick-up feedback when dragging starts (e.g. seekbar touch down, queue drag handle).
     */
    public static void dragStart(@Nullable View view) {
        if (!isEnabled(view)) return;
        performFeedback(view, HapticFeedbackConstants.DRAG_START, VibrationEffect.Composition.PRIMITIVE_THUD, 0.9f, 22, 210);
    }

    /**
     * Latch/docking feedback when gestures finish or seek is released.
     */
    public static void gestureEnd(@Nullable View view) {
        if (!isEnabled(view)) return;
        performFeedback(view, HapticFeedbackConstants.GESTURE_END, VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f, 16, 170);
    }

    /**
     * Rejection/dismiss feedback (e.g. swiping item out of queue, deleting saved server).
     */
    public static void dismiss(@Nullable View view) {
        if (!isEnabled(view)) return;
        performFeedback(view, HapticFeedbackConstants.REJECT, VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.85f, 18, 180);
    }

    /**
     * Haptic pulse for line-based lyrics transitions.
     * Deep, punchy, and rhythmic bass-like thud marking the arrival of each line.
     */
    public static void performLyricLineHaptic(@Nullable Context context) {
        if (context == null) return;
        if (!ServerPrefs.isLineLyricsHapticsEnabled(context)) return;
        float strengthScale = getLineLyricsStrengthScale(context);
        float scale = Math.min(1.0f, Math.max(0.3f, 0.95f * strengthScale));
        int amplitude = Math.min(255, Math.max(1, (int) (220 * strengthScale)));
        executePrimitiveOrWaveform(context, VibrationEffect.Composition.PRIMITIVE_THUD, scale, 26, amplitude);
    }

    /**
     * Haptic micro-tick for word-based lyrics sync.
     * Crisp, light, and distinct syllable tap that matches the rhythm of each sung word.
     */
    public static void performLyricWordHaptic(@Nullable Context context) {
        if (context == null) return;
        if (!ServerPrefs.isWordLyricsHapticsEnabled(context)) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastWordHapticTime < MIN_WORD_INTERVAL_MS) {
            return;
        }
        lastWordHapticTime = now;

        float strengthScale = getWordLyricsStrengthScale(context);
        float scale = Math.min(1.0f, Math.max(0.2f, 0.60f * strengthScale));
        int amplitude = Math.min(255, Math.max(1, (int) (140 * strengthScale)));
        executePrimitiveOrWaveform(context, VibrationEffect.Composition.PRIMITIVE_CLICK, scale, 15, amplitude);
    }

    /**
     * Demonstrates the selected Line Lyrics haptic strength level on settings slider change.
     */
    public static void testLineLyricsStrengthHaptic(@Nullable View view, int strength) {
        if (view == null) return;
        Context context = view.getContext();
        float strengthScale;
        switch (strength) {
            case ServerPrefs.HAPTIC_STRENGTH_LIGHT:
                strengthScale = 0.45f;
                break;
            case ServerPrefs.HAPTIC_STRENGTH_STRONG:
                strengthScale = 1.00f;
                break;
            case ServerPrefs.HAPTIC_STRENGTH_MEDIUM:
            default:
                strengthScale = 0.75f;
                break;
        }
        float scale = Math.min(1.0f, Math.max(0.3f, 0.95f * strengthScale));
        int amplitude = Math.min(255, Math.max(1, (int) (220 * strengthScale)));
        executePrimitiveOrWaveform(context, VibrationEffect.Composition.PRIMITIVE_THUD, scale, 26, amplitude);
    }

    /**
     * Demonstrates the selected Word Lyrics haptic strength level on settings slider change.
     */
    public static void testWordLyricsStrengthHaptic(@Nullable View view, int strength) {
        if (view == null) return;
        Context context = view.getContext();
        float strengthScale;
        switch (strength) {
            case ServerPrefs.HAPTIC_STRENGTH_LIGHT:
                strengthScale = 0.45f;
                break;
            case ServerPrefs.HAPTIC_STRENGTH_STRONG:
                strengthScale = 1.00f;
                break;
            case ServerPrefs.HAPTIC_STRENGTH_MEDIUM:
            default:
                strengthScale = 0.75f;
                break;
        }
        float scale = Math.min(1.0f, Math.max(0.2f, 0.60f * strengthScale));
        int amplitude = Math.min(255, Math.max(1, (int) (140 * strengthScale)));
        executePrimitiveOrWaveform(context, VibrationEffect.Composition.PRIMITIVE_CLICK, scale, 15, amplitude);
    }

    /**
     * Demonstrates the selected haptic strength level on settings slider change.
     */
    public static void testStrengthHaptic(@Nullable View view, int strength) {
        if (view == null) return;
        Context context = view.getContext();
        if (!ServerPrefs.isHapticFeedbackEnabled(context)) return;

        float scale;
        int amplitude;
        int duration;
        switch (strength) {
            case ServerPrefs.HAPTIC_STRENGTH_LIGHT:
                scale = 0.40f;
                amplitude = 80;
                duration = 10;
                break;
            case ServerPrefs.HAPTIC_STRENGTH_STRONG:
                scale = 1.00f;
                amplitude = 255;
                duration = 24;
                break;
            case ServerPrefs.HAPTIC_STRENGTH_MEDIUM:
            default:
                scale = 0.75f;
                amplitude = 165;
                duration = 16;
                break;
        }

        executePrimitiveOrWaveform(context, VibrationEffect.Composition.PRIMITIVE_CLICK, scale, duration, amplitude);
    }

    private static boolean isEnabled(@Nullable View view) {
        if (view == null) return false;
        Context context = view.getContext();
        return ServerPrefs.isHapticFeedbackEnabled(context);
    }

    private static void performFeedback(@Nullable View view, int feedbackConstant, int primitiveId, float baseScale, int durationMs, int baseAmplitude) {
        if (view == null) return;
        Context context = view.getContext();
        float strengthScale = getStrengthScale(context);
        float scale = Math.min(1.0f, Math.max(0.05f, baseScale * strengthScale));
        int amplitude = Math.min(255, Math.max(1, (int) (baseAmplitude * strengthScale)));

        // If user selected Strong or Light, prefer calibrated scaled primitives so the strength difference is physically tangible
        int strength = ServerPrefs.getHapticStrength(context);
        if (strength != ServerPrefs.HAPTIC_STRENGTH_MEDIUM) {
            boolean executed = executePrimitiveOrWaveform(context, primitiveId, scale, durationMs, amplitude);
            if (executed) return;
        }

        boolean performed = false;
        try {
            performed = view.performHapticFeedback(
                    feedbackConstant,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            );
        } catch (Exception ignored) {}

        if (!performed) {
            executePrimitiveOrWaveform(context, primitiveId, scale, durationMs, amplitude);
        }
    }

    private static boolean executePrimitiveOrWaveform(@NonNull Context context, int primitiveId, float scale, int durationMs, int amplitude) {
        try {
            Vibrator vibrator = getVibrator(context);
            if (vibrator == null || !vibrator.hasVibrator()) return false;

            VibrationAttributes attrs = new VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_TOUCH)
                    .build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                int targetPrimitive = primitiveId;
                if (!vibrator.areAllPrimitivesSupported(targetPrimitive) && targetPrimitive == VibrationEffect.Composition.PRIMITIVE_LOW_TICK) {
                    if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
                        targetPrimitive = VibrationEffect.Composition.PRIMITIVE_TICK;
                    }
                }
                if (vibrator.areAllPrimitivesSupported(targetPrimitive)) {
                    VibrationEffect effect = VibrationEffect.startComposition()
                            .addPrimitive(targetPrimitive, scale)
                            .compose();
                    vibrator.vibrate(effect, attrs);
                    return true;
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator.hasAmplitudeControl()) {
                VibrationEffect effect = VibrationEffect.createOneShot(durationMs, amplitude);
                vibrator.vibrate(effect, attrs);
                return true;
            }

            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Nullable
    private static Vibrator getVibrator(@NonNull Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    return vm.getDefaultVibrator();
                }
            }
            return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        } catch (Exception ignored) {
            return null;
        }
    }
}
