package com.aadith.mustre.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import com.aadith.mustre.model.DownloadResponse;
import com.aadith.mustre.model.SearchResult;
import com.aadith.mustre.model.Song;
import com.aadith.mustre.model.SongsResponse;
import com.aadith.mustre.network.HttpHandler;
import com.aadith.mustre.network.ServerPrefs;
import com.aadith.mustre.network.SongDownloader;
import com.aadith.mustre.service.MusicService;
import com.aadith.mustre.util.TimeUtils;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.gson.Gson;

import java.util.List;
import java.util.Objects;

@OptIn(markerClass = UnstableApi.class)
public class TrackPlaybackManager {

    private static final String TAG = "TrackPlaybackManager";

    public interface TrackPlaybackCallback {
        void onSongDownloadingStateChanged(boolean isDownloading);
        void onTrackInfoPrepared(String title, String artist, String currentTime, String totalTime, String initialQuality, String imgUrl);
        void onCoverLoaded(Bitmap bitmap);
        void onSongReadyToPlay(Song streamedSong, int existingIndex);
        void onPlaybackError(String errorMessage);
        void onCollapseSearchRequested();
    }

    private ValueAnimator seekBarLoadingAnimator;
    private boolean isSongDownloading = false;
    private int downloadRequestId = 0;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public boolean isSongDownloading() {
        return isSongDownloading;
    }

    public void setSongDownloading(boolean downloading) {
        this.isSongDownloading = downloading;
    }

    public void startSeekBarLoadingAnimation(SeekBar seekbarAudio) {
        stopSeekBarLoadingAnimation(seekbarAudio);

        if (seekbarAudio != null) {
            seekbarAudio.setEnabled(false);
            seekbarAudio.setMax(1000);

            seekBarLoadingAnimator = ValueAnimator.ofFloat(0f, 1f);
            seekBarLoadingAnimator.setDuration(1200);
            seekBarLoadingAnimator.setRepeatCount(ValueAnimator.INFINITE);
            seekBarLoadingAnimator.setRepeatMode(ValueAnimator.REVERSE);
            seekBarLoadingAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            seekBarLoadingAnimator.addUpdateListener(anim -> {
                float fraction = (float) anim.getAnimatedValue();
                int progress = (int) (fraction * 1000);
                seekbarAudio.setProgress(progress);
                seekbarAudio.setSecondaryProgress(Math.min(1000, progress + 200));
            });
            seekBarLoadingAnimator.start();
        }
    }

    public void stopSeekBarLoadingAnimation(SeekBar seekbarAudio) {
        if (seekBarLoadingAnimator != null) {
            seekBarLoadingAnimator.cancel();
            seekBarLoadingAnimator = null;
        }
        if (seekbarAudio != null) {
            seekbarAudio.setEnabled(true);
        }
    }

    public void onSearchResultSelected(Context context, SearchResult result, List<Song> fullSongList, MusicService musicService, String baseUrl, SeekBar seekbarAudio, TrackPlaybackCallback callback) {
        if (result == null) return;

        if (ServerPrefs.isOffline(context) && !"local".equalsIgnoreCase(result.getSource())) {
            if (callback != null) callback.onPlaybackError("Song is not cached");
            return;
        }

        if ("local".equalsIgnoreCase(result.getSource())) {
            if (callback != null) callback.onCollapseSearchRequested();

            Song localSong = new Song(
                    result.getTitle(),
                    result.getArtist(),
                    result.getAlbum(),
                    result.getUrl(),
                    result.getImgUrl(),
                    result.getQuality()
            );

            int foundIndex = -1;
            String resultKey = ServerPrefs.getSongIdentityKey(localSong);

            for (int i = 0; i < fullSongList.size(); i++) {
                Song s = fullSongList.get(i);
                if (s == null) continue;
                if (Objects.equals(s.getAudioPath(), result.getUrl()) ||
                        resultKey.equals(ServerPrefs.getSongIdentityKey(s))) {
                    foundIndex = i;
                    break;
                }
            }

            if (foundIndex != -1 && musicService != null) {
                musicService.playSong(foundIndex, true);
            } else if (musicService != null) {
                musicService.playImmediate(localSong);
            }
            return;
        }

        final int currentToken = ++downloadRequestId;
        isSongDownloading = true;
        if (callback != null) {
            callback.onSongDownloadingStateChanged(true);
            callback.onCollapseSearchRequested();
        }

        if (musicService != null) {
            musicService.pause();
        }

        String title = result.getTitle() != null && !result.getTitle().isEmpty() ? result.getTitle() : "Unknown Title";
        String artist = result.getArtist();
        if (artist == null || artist.trim().isEmpty()) {
            artist = "telegram".equalsIgnoreCase(result.getSource()) ? "Telegram" : "YouTube";
        }

        String currentTime = "00:00";
        String totalTime = result.getDuration() > 0 ? TimeUtils.formatTime((int) (result.getDuration() * 1000)) : "--:--";

        String initialQuality = result.getQuality();
        if (initialQuality == null || initialQuality.trim().isEmpty()) {
            if ("telegram".equalsIgnoreCase(result.getSource())) {
                initialQuality = "FLAC (Lossless)";
            } else if ("youtube".equalsIgnoreCase(result.getSource())) {
                initialQuality = "AAC • 256 kbps";
            }
        }

        if (initialQuality != null && !initialQuality.trim().isEmpty()) {
            if (result.getUrl() != null && !result.getUrl().trim().isEmpty()) {
                ServerPrefs.saveCachedQualityText(context, result.getUrl(), initialQuality);
            }
            if (result.getFileName() != null && !result.getFileName().trim().isEmpty()) {
                ServerPrefs.saveCachedQualityText(context, result.getFileName(), initialQuality);
                ServerPrefs.saveCachedQualityText(context, "/audios/" + result.getFileName(), initialQuality);
            }
        }

        String initialSize = result.getFormattedSize();
        if (initialSize != null && !initialSize.trim().isEmpty()) {
            if (result.getUrl() != null && !result.getUrl().trim().isEmpty()) {
                ServerPrefs.saveCachedSizeText(context, result.getUrl(), initialSize);
            }
            if (result.getFileName() != null && !result.getFileName().trim().isEmpty()) {
                ServerPrefs.saveCachedSizeText(context, result.getFileName(), initialSize);
                ServerPrefs.saveCachedSizeText(context, "/audios/" + result.getFileName(), initialSize);
            }
        }

        if (callback != null) {
            callback.onTrackInfoPrepared(title, artist, currentTime, totalTime, initialQuality, result.getImgUrl());
        }

        String imgUrl = result.getImgUrl();
        if (imgUrl != null && !imgUrl.trim().isEmpty()) {
            String fullCoverUrl = ServerPrefs.formatCoverUrl(baseUrl, imgUrl);
            boolean isOffline = ServerPrefs.isOffline(context);
            com.bumptech.glide.RequestBuilder<Bitmap> glideBuilder = Glide.with(context)
                    .asBitmap()
                    .load(fullCoverUrl);
            if (isOffline && (fullCoverUrl.startsWith("http://") || fullCoverUrl.startsWith("https://"))) {
                glideBuilder = glideBuilder.onlyRetrieveFromCache(true);
            }
            glideBuilder.into(new CustomTarget<Bitmap>() {
                @Override
                public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                    if (currentToken != downloadRequestId) return;
                    if (callback != null) callback.onCoverLoaded(resource);
                }

                @Override
                public void onLoadCleared(@Nullable Drawable placeholder) {}
            });
        }

        startSeekBarLoadingAnimation(seekbarAudio);

        String songUrl = baseUrl + "/song/" + result.getId();
        if (result.getSource() != null && !result.getSource().trim().isEmpty()) {
            songUrl += "?source=" + result.getSource().trim();
        }

        HttpHandler httpHandler = new HttpHandler();
        httpHandler.makeHttpGetRequest(songUrl, new HttpHandler.MusicDataCallback() {
            @Override
            public void onSuccess(String jsonResult) {
                mainHandler.post(() -> {
                    if (currentToken != downloadRequestId) return;
                    handleSongReadyForPlayback(context, result, jsonResult, fullSongList, musicService, seekbarAudio, callback);
                });
            }

            @Override
            public void onError(Exception e) {
                mainHandler.post(() -> {
                    if (currentToken != downloadRequestId) return;
                    Log.w(TAG, "Song endpoint request error: " + e.getMessage() + ", checking direct stream URL fallback");

                    if (result.getUrl() != null && !result.getUrl().trim().isEmpty() && !result.getUrl().contains("youtube.com")) {
                        handleSongReadyForPlayback(context, result, null, fullSongList, musicService, seekbarAudio, callback);
                    } else {
                        isSongDownloading = false;
                        if (callback != null) callback.onSongDownloadingStateChanged(false);
                        stopSeekBarLoadingAnimation(seekbarAudio);
                        if (seekbarAudio != null) {
                            seekbarAudio.setProgress(0);
                            seekbarAudio.setSecondaryProgress(0);
                        }
                        String msg = e.getMessage() != null ? e.getMessage() : "Playback failed";
                        if (callback != null) callback.onPlaybackError("Download error: " + msg);
                    }
                });
            }
        });
    }

    private void handleSongReadyForPlayback(Context context, SearchResult result, String jsonResult, List<Song> fullSongList, MusicService musicService, SeekBar seekbarAudio, TrackPlaybackCallback callback) {
        try {
            Song streamedSong = null;
            if (jsonResult != null && !jsonResult.trim().isEmpty()) {
                Gson gson = new Gson();
                try {
                    DownloadResponse response = gson.fromJson(jsonResult, DownloadResponse.class);
                    if (response != null && response.getSong() != null) {
                        streamedSong = response.getSong();
                    } else {
                        streamedSong = gson.fromJson(jsonResult, Song.class);
                    }
                } catch (Exception ex) {
                    Log.w(TAG, "Failed parsing JSON response with Gson: " + jsonResult, ex);
                }
            }

            if (streamedSong == null || streamedSong.getAudioPath() == null || streamedSong.getAudioPath().trim().isEmpty()) {
                if (result.getUrl() != null && !result.getUrl().trim().isEmpty() && !result.getUrl().contains("youtube.com")) {
                    streamedSong = new Song();
                    streamedSong.setAudioPath(result.getUrl());
                }
            }

            if (streamedSong != null && streamedSong.getAudioPath() != null && !streamedSong.getAudioPath().trim().isEmpty()) {
                if (streamedSong.getTitle() == null || streamedSong.getTitle().trim().isEmpty()) {
                    streamedSong.setTitle(result.getTitle());
                }
                if (streamedSong.getArtist() == null || streamedSong.getArtist().trim().isEmpty()) {
                    streamedSong.setArtist(result.getArtist() != null ? result.getArtist() : ("telegram".equalsIgnoreCase(result.getSource()) ? "Telegram" : "YouTube"));
                }
                if (streamedSong.getCoverPath() == null || streamedSong.getCoverPath().trim().isEmpty()) {
                    streamedSong.setCoverPath(result.getImgUrl());
                }
                if (streamedSong.getAlbum() == null || streamedSong.getAlbum().trim().isEmpty()) {
                    streamedSong.setAlbum(result.getAlbum());
                }

                String quality = streamedSong.getQuality();
                if (quality == null || quality.trim().isEmpty()) {
                    quality = result.getQuality();
                }
                if (quality == null || quality.trim().isEmpty()) {
                    if (streamedSong.getAudioPath().toLowerCase().endsWith(".flac") || "telegram".equalsIgnoreCase(result.getSource())) {
                        quality = "FLAC (Lossless)";
                    } else if ("youtube".equalsIgnoreCase(result.getSource())) {
                        quality = "AAC • 256 kbps";
                    }
                }
                streamedSong.setQuality(quality);

                if (quality != null && !quality.trim().isEmpty()) {
                    ServerPrefs.saveCachedQualityText(context, streamedSong.getAudioPath(), quality);
                }

                String size = streamedSong.getFormattedSize();
                if (size == null || size.trim().isEmpty()) {
                    size = result.getFormattedSize();
                }
                if (size != null && !size.trim().isEmpty()) {
                    streamedSong.setFormattedSize(size);
                    ServerPrefs.saveCachedSizeText(context, streamedSong.getAudioPath(), size);
                }
                if (result.getSize() > 0) {
                    streamedSong.setSize(result.getSize());
                }

                boolean exists = false;
                int existingIndex = -1;
                for (int i = 0; i < fullSongList.size(); i++) {
                    Song existing = fullSongList.get(i);
                    if (Objects.equals(streamedSong.getAudioPath(), existing.getAudioPath())) {
                        exists = true;
                        existingIndex = i;
                        if (existing.getQuality() == null || existing.getQuality().trim().isEmpty()) {
                            existing.setQuality(quality);
                        }
                        if (size != null && !size.trim().isEmpty() && (existing.getFormattedSize() == null || existing.getFormattedSize().trim().isEmpty())) {
                            existing.setFormattedSize(size);
                        }
                        break;
                    }
                }

                if (!exists) {
                    fullSongList.add(streamedSong);
                    existingIndex = fullSongList.size() - 1;
                }

                SongDownloader.getInstance(context).downloadSong(streamedSong, null);

                try {
                    Gson cacheGson = new Gson();
                    SongsResponse cachedResponse = new SongsResponse();
                    cachedResponse.setSongs(fullSongList);
                    ServerPrefs.saveCachedSongsJson(context, cacheGson.toJson(cachedResponse));
                } catch (Exception ignored) {}

                if (musicService != null) {
                    musicService.setSongs(fullSongList, existingIndex, true);
                }
                isSongDownloading = false;
                if (callback != null) {
                    callback.onSongDownloadingStateChanged(false);
                    callback.onSongReadyToPlay(streamedSong, existingIndex);
                }
                stopSeekBarLoadingAnimation(seekbarAudio);
            } else {
                isSongDownloading = false;
                if (callback != null) callback.onSongDownloadingStateChanged(false);
                stopSeekBarLoadingAnimation(seekbarAudio);
                if (seekbarAudio != null) {
                    seekbarAudio.setProgress(0);
                    seekbarAudio.setSecondaryProgress(0);
                }
                if (callback != null) callback.onPlaybackError("Unable to stream audio url from server");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error finalizing song playback", e);
            isSongDownloading = false;
            if (callback != null) callback.onSongDownloadingStateChanged(false);
            stopSeekBarLoadingAnimation(seekbarAudio);
            if (seekbarAudio != null) {
                seekbarAudio.setProgress(0);
                seekbarAudio.setSecondaryProgress(0);
            }
            if (callback != null) callback.onPlaybackError("Error finalizing song playback");
        }
    }
}
