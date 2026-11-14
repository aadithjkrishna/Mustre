package com.aadith.mustre.ui;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.aadith.mustre.network.ServerPrefs;

import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QualityMetadataHelper {

    private static final String TAG = "QualityMetadataHelper";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface QualityCallback {
        void onQualityUpdated(String qualityText);
    }

    public static void updateQualityBadge(Context context, View badgeAudioQuality, TextView tvQualityText, String audioPath, String fullAudioUrl, QualityCallback callback) {
        if (audioPath == null || audioPath.trim().isEmpty()) {
            if (badgeAudioQuality != null) badgeAudioQuality.setVisibility(View.GONE);
            return;
        }

        String cachedQuality = ServerPrefs.getCachedQualityText(context, audioPath);
        if (cachedQuality != null && !cachedQuality.trim().isEmpty()) {
            if (badgeAudioQuality != null) badgeAudioQuality.setVisibility(View.VISIBLE);
            if (tvQualityText != null) tvQualityText.setText(cachedQuality);
            if (callback != null) callback.onQualityUpdated(cachedQuality);
            return;
        }

        String pathLower = audioPath.toLowerCase();
        String initialQuality;

        if (pathLower.endsWith(".flac") || pathLower.endsWith(".alac") || pathLower.endsWith(".wav") || pathLower.contains("telegram")) {
            initialQuality = "FLAC (Lossless)";
        } else if (pathLower.endsWith(".opus")) {
            initialQuality = "High Quality • Opus";
        } else if (pathLower.endsWith(".mp3") || pathLower.endsWith(".aac") || pathLower.endsWith(".m4a")) {
            initialQuality = "AAC Audio";
        } else {
            if (badgeAudioQuality != null) badgeAudioQuality.setVisibility(View.GONE);
            return;
        }

        if (badgeAudioQuality != null) badgeAudioQuality.setVisibility(View.VISIBLE);
        if (tvQualityText != null) tvQualityText.setText(initialQuality);

        Handler mainHandler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
                retriever.setDataSource(fullAudioUrl, new HashMap<>());

                String bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
                String sampleRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE);

                int parsedBitrateKbps = 0;
                if (bitrateStr != null && !bitrateStr.isEmpty()) {
                    try {
                        parsedBitrateKbps = Integer.parseInt(bitrateStr) / 1000;
                    } catch (NumberFormatException ignored) {}
                }

                String sampleRateText = "";
                if (sampleRateStr != null && !sampleRateStr.isEmpty()) {
                    try {
                        int sampleRateHz = Integer.parseInt(sampleRateStr);
                        if (sampleRateHz % 1000 == 0) {
                            sampleRateText = (sampleRateHz / 1000) + " kHz";
                        } else {
                            sampleRateText = String.format(Locale.US, "%.1f kHz", sampleRateHz / 1000.0);
                        }
                    } catch (NumberFormatException ignored) {}
                }

                String finalQualityText;
                if (pathLower.endsWith(".flac") || pathLower.endsWith(".alac") || pathLower.endsWith(".wav")) {
                    String spec = !sampleRateText.isEmpty() ? "24-bit / " + sampleRateText : "24-bit / 48 kHz";
                    finalQualityText = "Lossless • " + spec + " FLAC";
                } else if (pathLower.endsWith(".opus")) {
                    String spec = parsedBitrateKbps > 0 ? parsedBitrateKbps + " kbps" : "320 kbps";
                    finalQualityText = "High Quality • " + spec + " Opus";
                } else {
                    String spec = parsedBitrateKbps > 0 ? parsedBitrateKbps + " kbps" : "256 kbps";
                    finalQualityText = "AAC • " + spec;
                }

                ServerPrefs.saveCachedQualityText(context, audioPath, finalQualityText);

                mainHandler.post(() -> {
                    if (badgeAudioQuality != null) badgeAudioQuality.setVisibility(View.VISIBLE);
                    if (tvQualityText != null) tvQualityText.setText(finalQualityText);
                    if (callback != null) callback.onQualityUpdated(finalQualityText);
                });

            } catch (Exception e) {
                Log.d(TAG, "Could not extract metadata from " + fullAudioUrl, e);
            }
        });
    }
}
