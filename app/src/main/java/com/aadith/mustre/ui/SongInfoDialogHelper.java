package com.aadith.mustre.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;

import com.aadith.mustre.R;
import com.aadith.mustre.model.SearchResult;
import com.aadith.mustre.model.Song;
import com.aadith.mustre.network.MediaCacheManager;
import com.aadith.mustre.network.ServerPrefs;
import com.aadith.mustre.service.MusicService;
import com.aadith.mustre.util.TimeUtils;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;
import java.util.Locale;

@OptIn(markerClass = UnstableApi.class)
public class SongInfoDialogHelper {

    public static void showSongInfoDialog(Context context, MusicService musicService, List<Song> fullSongList, String baseUrl, String currentTotalTimeText) {
        Song targetSong = musicService != null ? musicService.getCurrentSong() : null;
        if (targetSong == null && fullSongList != null && !fullSongList.isEmpty()) {
            int currentIdx = musicService != null ? musicService.getCurrentSongIndex() : 0;
            if (currentIdx >= 0 && currentIdx < fullSongList.size()) {
                targetSong = fullSongList.get(currentIdx);
            }
        }

        if (targetSong == null) {
            Toast.makeText(context, "No song currently loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.TranslucentBottomSheetDialog);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_song_info, null);
        dialog.setContentView(view);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.55f);
            dialog.getWindow().setElevation(0f);
        }

        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog d = (BottomSheetDialog) dialogInterface;
            FrameLayout bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackground(null);
                bottomSheet.setBackgroundColor(Color.TRANSPARENT);
                bottomSheet.setElevation(0f);
                bottomSheet.setOutlineProvider(null);
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
            View coordinator = d.findViewById(com.google.android.material.R.id.coordinator);
            if (coordinator != null) {
                coordinator.setBackground(null);
                coordinator.setBackgroundColor(Color.TRANSPARENT);
                coordinator.setElevation(0f);
                coordinator.setOutlineProvider(null);
            }
        });

        // 1. Cover Art
        ImageView ivCover = view.findViewById(R.id.iv_info_cover);
        if (ivCover != null) {
            String coverUrl = ServerPrefs.formatCoverUrl(baseUrl, targetSong.getCoverPath());
            boolean isOffline = ServerPrefs.isOffline(context);
            com.bumptech.glide.RequestBuilder<Drawable> glideBuilder = Glide.with(context)
                    .load(coverUrl)
                    .placeholder(R.drawable.mustre)
                    .error(R.drawable.mustre);
            if (isOffline && (coverUrl.startsWith("http://") || coverUrl.startsWith("https://"))) {
                glideBuilder = glideBuilder.onlyRetrieveFromCache(true);
            }
            glideBuilder.into(ivCover);
        }

        // 2. Title, Artist & Album
        TextView tvTitle = view.findViewById(R.id.tv_info_title);
        if (tvTitle != null) {
            tvTitle.setText(targetSong.getTitle() != null && !targetSong.getTitle().trim().isEmpty() ? targetSong.getTitle() : "Unknown Title");
        }

        TextView tvArtist = view.findViewById(R.id.tv_info_artist);
        if (tvArtist != null) {
            tvArtist.setText(targetSong.getArtist() != null && !targetSong.getArtist().trim().isEmpty() ? targetSong.getArtist() : "Unknown Artist");
        }

        TextView tvAlbum = view.findViewById(R.id.tv_info_album);
        if (tvAlbum != null) {
            if (targetSong.getAlbum() != null && !targetSong.getAlbum().trim().isEmpty()) {
                tvAlbum.setText(targetSong.getAlbum().trim());
                tvAlbum.setVisibility(View.VISIBLE);
            } else {
                tvAlbum.setVisibility(View.GONE);
            }
        }

        // 3. Audio Quality Header Badge
        TextView tvQualityHeader = view.findViewById(R.id.tv_info_quality_header);
        String qText = targetSong.getQuality();
        if (qText == null || qText.trim().isEmpty()) {
            qText = ServerPrefs.getCachedQualityText(context, targetSong.getAudioPath());
        }
        if (qText == null || qText.trim().isEmpty()) {
            String path = targetSong.getAudioPath() != null ? targetSong.getAudioPath().toLowerCase() : "";
            if (path.endsWith(".flac") || path.contains("telegram")) {
                qText = "FLAC (Lossless)";
            } else if (path.endsWith(".opus")) {
                qText = "High Quality • Opus";
            } else {
                qText = "Standard Audio";
            }
        }
        if (tvQualityHeader != null) {
            tvQualityHeader.setText(qText);
        }

        ImageView ivQualityIcon = view.findViewById(R.id.iv_info_quality_icon);
        if (ivQualityIcon != null) {
            if (qText != null && (qText.contains("FLAC") || qText.contains("Lossless"))) {
                ivQualityIcon.setImageResource(R.drawable.ic_lossless);
            } else {
                ivQualityIcon.setImageResource(R.drawable.ic_music_note);
            }
        }

        // 4. Audio Specs
        TextView tvCodec = view.findViewById(R.id.tv_info_codec);
        TextView tvSampleRate = view.findViewById(R.id.tv_info_sample_rate);
        TextView tvBitDepth = view.findViewById(R.id.tv_info_bit_depth);
        TextView tvBitrate = view.findViewById(R.id.tv_info_bitrate);
        TextView tvChannels = view.findViewById(R.id.tv_info_channels);

        Format format = musicService != null ? musicService.getCurrentAudioFormat() : null;
        String pathLower = targetSong.getAudioPath() != null ? targetSong.getAudioPath().toLowerCase() : "";

        // Codec
        String codecStr = "Unknown";
        if (format != null && format.sampleMimeType != null) {
            String mime = format.sampleMimeType.toLowerCase();
            if (mime.contains("flac")) codecStr = "FLAC (Lossless)";
            else if (mime.contains("opus")) codecStr = "Opus (Interactive Audio)";
            else if (mime.contains("mp4a") || mime.contains("aac")) codecStr = "AAC (Advanced Audio Coding)";
            else if (mime.contains("mpeg") || mime.contains("mp3")) codecStr = "MP3 (MPEG-1 Audio Layer III)";
            else codecStr = format.sampleMimeType;
        } else {
            if (pathLower.endsWith(".flac") || pathLower.contains("telegram")) codecStr = "FLAC (Free Lossless Audio Codec)";
            else if (pathLower.endsWith(".opus")) codecStr = "Opus (High Quality)";
            else if (pathLower.endsWith(".mp3")) codecStr = "MP3";
            else if (pathLower.endsWith(".aac") || pathLower.endsWith(".m4a")) codecStr = "AAC";
        }
        if (tvCodec != null) tvCodec.setText(codecStr);

        // Sample rate
        String sampleRateStr = "44.1 kHz (CD Quality)";
        if (format != null && format.sampleRate > 0) {
            if (format.sampleRate % 1000 == 0) {
                sampleRateStr = String.format(Locale.US, "%,d Hz (%d kHz)", format.sampleRate, format.sampleRate / 1000);
            } else {
                sampleRateStr = String.format(Locale.US, "%,d Hz (%.1f kHz)", format.sampleRate, format.sampleRate / 1000.0);
            }
        } else if (qText.contains("kHz")) {
            sampleRateStr = "44.1 kHz (44,100 Hz)";
        }
        if (tvSampleRate != null) tvSampleRate.setText(sampleRateStr);

        // Bit depth
        String bitDepthStr = "16-bit";
        if (format != null && format.pcmEncoding != Format.NO_VALUE) {
            if (format.pcmEncoding == C.ENCODING_PCM_24BIT) {
                bitDepthStr = "24-bit (Studio Master)";
            } else if (format.pcmEncoding == C.ENCODING_PCM_32BIT || format.pcmEncoding == C.ENCODING_PCM_FLOAT) {
                bitDepthStr = "32-bit Float";
            } else if (format.pcmEncoding == C.ENCODING_PCM_16BIT) {
                bitDepthStr = "16-bit (CD Quality)";
            }
        } else if (pathLower.endsWith(".flac") || pathLower.contains("telegram") || qText.contains("24-bit")) {
            bitDepthStr = "24-bit (Studio Master)";
        }
        if (tvBitDepth != null) tvBitDepth.setText(bitDepthStr);

        // Bitrate
        String bitrateStr = "--";
        if (format != null && format.bitrate > 0) {
            bitrateStr = String.format(Locale.US, "%,d kbps", format.bitrate / 1000);
        } else if (pathLower.endsWith(".flac") || pathLower.contains("telegram")) {
            bitrateStr = "~1,690 kbps (Lossless Master)";
        } else if (pathLower.endsWith(".opus")) {
            bitrateStr = "~320 kbps (VBR)";
        } else if (pathLower.endsWith(".mp3") || pathLower.endsWith(".aac")) {
            bitrateStr = "256 - 320 kbps";
        }
        if (tvBitrate != null) tvBitrate.setText(bitrateStr);

        // Channels
        String channelsStr = "Stereo (2 Channels)";
        if (format != null && format.channelCount > 0) {
            channelsStr = format.channelCount == 1 ? "Mono (1 Channel)" : (format.channelCount == 2 ? "Stereo (2 Channels)" : format.channelCount + " Channels");
        }
        if (tvChannels != null) tvChannels.setText(channelsStr);

        // 5. File & Playback Details
        TextView tvSource = view.findViewById(R.id.tv_info_source);
        if (tvSource != null) {
            if (pathLower.contains("telegram")) {
                tvSource.setText("Telegram Audio");
            } else if (pathLower.contains("youtube") || pathLower.contains("googlevideo")) {
                tvSource.setText("YouTube Audio");
            } else {
                tvSource.setText("MusicServer LAN Library");
            }
        }

        TextView tvDuration = view.findViewById(R.id.tv_info_duration);
        if (tvDuration != null) {
            long dur = musicService != null ? musicService.getDuration() : 0;
            if (dur > 0) {
                tvDuration.setText(TimeUtils.formatTime((int) dur));
            } else {
                tvDuration.setText(currentTotalTimeText != null ? currentTotalTimeText : "--:--");
            }
        }

        TextView tvSize = view.findViewById(R.id.tv_info_size);
        if (tvSize != null) {
            String size = targetSong.getFormattedSize();
            if (size == null || size.trim().isEmpty()) {
                size = ServerPrefs.getCachedSizeText(context, targetSong.getAudioPath());
            }
            if (size == null || size.trim().isEmpty()) {
                String fullUrl = ServerPrefs.formatFullUrl(baseUrl, targetSong.getAudioPath());
                long cachedBytes = MediaCacheManager.getCachedBytesForUrl(context, fullUrl);
                if (cachedBytes > 0) {
                    size = SearchResult.formatBytes(cachedBytes);
                }
            }
            tvSize.setText(size != null && !size.trim().isEmpty() ? size : "Direct Stream");
        }

        TextView tvStorage = view.findViewById(R.id.tv_info_storage);
        if (tvStorage != null) {
            String fullUrl = ServerPrefs.formatFullUrl(baseUrl, targetSong.getAudioPath());
            long cachedBytes = MediaCacheManager.getCachedBytesForUrl(context, fullUrl);
            if (cachedBytes > 0) {
                tvStorage.setText("Offline Cached (" + SearchResult.formatBytes(cachedBytes) + ")");
            } else {
                tvStorage.setText("Direct LAN Stream");
            }
        }

        TextView tvPath = view.findViewById(R.id.tv_info_path);
        if (tvPath != null) {
            String pathVal = targetSong.getAudioPath() != null ? targetSong.getAudioPath() : "--";
            tvPath.setText(pathVal);
            tvPath.setOnClickListener(v -> {
                if (!"--".equals(pathVal)) {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        android.content.ClipData clip = android.content.ClipData.newPlainText("Audio Path", pathVal);
                        cm.setPrimaryClip(clip);
                        Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        // Close Buttons (Top circular & bottom Done pill)
        View btnCloseTop = view.findViewById(R.id.btn_info_close_top);
        if (btnCloseTop != null) {
            btnCloseTop.setOnClickListener(v -> dialog.dismiss());
        }

        View btnClose = view.findViewById(R.id.btn_info_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }
}
