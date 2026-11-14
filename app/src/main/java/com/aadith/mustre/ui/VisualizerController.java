package com.aadith.mustre.ui;

import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import com.aadith.mustre.service.MusicService;

@OptIn(markerClass = UnstableApi.class)
public class VisualizerController {

    private final View bar1;
    private final View bar2;
    private final View bar3;
    private final View bar4;

    private final Handler visualizerHandler = new Handler(Looper.getMainLooper());
    private Runnable visualizerRunnable;

    public VisualizerController(View bar1, View bar2, View bar3, View bar4) {
        this.bar1 = bar1;
        this.bar2 = bar2;
        this.bar3 = bar3;
        this.bar4 = bar4;
    }

    public void updateVisualizerState(boolean isHeaderPanelOpen, boolean isPortraitMode, MusicService musicService) {
        boolean shouldPlay = isHeaderPanelOpen 
                && isPortraitMode 
                && musicService != null 
                && musicService.isPlaying();

        if (shouldPlay) {
            startHeaderVisualizerAnimation(isHeaderPanelOpen, isPortraitMode, musicService);
        } else {
            stopHeaderVisualizerAnimation();
        }
    }

    public void startHeaderVisualizerAnimation(boolean isHeaderPanelOpen, boolean isPortraitMode, MusicService musicService) {
        if (bar1 == null || bar2 == null || bar3 == null || bar4 == null) return;

        float density = bar1.getContext().getResources().getDisplayMetrics().density;
        float barHeight = 18f * density;
        bar1.setPivotY(bar1.getHeight() > 0 ? bar1.getHeight() : barHeight);
        bar2.setPivotY(bar2.getHeight() > 0 ? bar2.getHeight() : barHeight);
        bar3.setPivotY(bar3.getHeight() > 0 ? bar3.getHeight() : barHeight);
        bar4.setPivotY(bar4.getHeight() > 0 ? bar4.getHeight() : barHeight);

        if (visualizerRunnable != null) return;

        visualizerRunnable = new Runnable() {
            private float c1 = 0.5f, c2 = 0.5f, c3 = 0.5f, c4 = 0.5f;
            private float t1 = 0.5f, t2 = 0.5f, t3 = 0.5f, t4 = 0.5f;
            private int step = 0;

            @Override
            public void run() {
                if (isHeaderPanelOpen && isPortraitMode && musicService != null && musicService.isPlaying()) {
                    if (step++ % 3 == 0) {
                        t1 = 0.25f + (float) Math.random() * 0.75f;
                        t2 = 0.25f + (float) Math.random() * 0.75f;
                        t3 = 0.25f + (float) Math.random() * 0.75f;
                        t4 = 0.25f + (float) Math.random() * 0.75f;
                    }
                    c1 += (t1 - c1) * 0.45f;
                    c2 += (t2 - c2) * 0.45f;
                    c3 += (t3 - c3) * 0.45f;
                    c4 += (t4 - c4) * 0.45f;

                    bar1.setScaleY(c1);
                    bar2.setScaleY(c2);
                    bar3.setScaleY(c3);
                    bar4.setScaleY(c4);

                    visualizerHandler.postDelayed(this, 30);
                } else {
                    stopHeaderVisualizerAnimation();
                }
            }
        };
        visualizerHandler.post(visualizerRunnable);
    }

    public void stopHeaderVisualizerAnimation() {
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
}
