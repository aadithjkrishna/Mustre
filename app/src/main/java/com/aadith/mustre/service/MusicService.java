package com.aadith.mustre.service;

import com.aadith.mustre.network.SongDownloader;
import com.aadith.mustre.network.ServerPrefs;
import com.aadith.mustre.network.MediaCacheManager;
import com.aadith.mustre.model.Song;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.extractor.DefaultExtractorsFactory;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import com.aadith.mustre.MainActivity;
import com.aadith.mustre.R;
import com.aadith.mustre.model.Song;
import com.aadith.mustre.network.MediaCacheManager;
import com.aadith.mustre.network.ServerPrefs;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@UnstableApi
public class MusicService extends Service {

    private static final String TAG = "MusicService";
    public static final String CHANNEL_ID = "mustre_music_channel";
    public static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_PLAY_PAUSE = "com.aadith.mustre.ACTION_PLAY_PAUSE";
    public static final String ACTION_PREV = "com.aadith.mustre.ACTION_PREV";
    public static final String ACTION_NEXT = "com.aadith.mustre.ACTION_NEXT";

    private String getBaseUrl() {
        return ServerPrefs.getServerUrl(this);
    }

    private final IBinder binder = new MusicBinder();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private MediaSessionCompat mediaSession;
    private ExoPlayer player;

    private List<Song> originalList;
    private List<Song> songList; // This acts as the queue
    private int currentSongIndex = 0;
    private int repeatMode = Player.REPEAT_MODE_OFF;
    private final java.util.ArrayDeque<Song> playbackHistory = new java.util.ArrayDeque<>();
    private Bitmap currentCoverBitmap;
    private Format currentAudioFormat;

    private void addToHistory(Song song) {
        if (song == null) return;
        if (!playbackHistory.isEmpty() && playbackHistory.peek() == song) return;
        playbackHistory.push(song);
        while (playbackHistory.size() > 50) {
            playbackHistory.removeLast();
        }
    }

    private int findSongIndexInOriginalList(Song song) {
        if (song == null || originalList == null || originalList.isEmpty()) return -1;
        String path = song.getAudioPath();
        String title = song.getTitle();
        String artist = song.getArtist();
        for (int i = 0; i < originalList.size(); i++) {
            Song s = originalList.get(i);
            if (s == null) continue;
            if (path != null && s.getAudioPath() != null && path.equalsIgnoreCase(s.getAudioPath())) {
                return i;
            }
            if (title != null && s.getTitle() != null && title.equalsIgnoreCase(s.getTitle())
                    && artist != null && s.getArtist() != null && artist.equalsIgnoreCase(s.getArtist())) {
                return i;
            }
        }
        return -1;
    }

    public interface MusicChangeListener {
        void onSongChanged(Song song, int index);
        void onPlaybackStateChanged(boolean isPlaying);
        void onCoverLoaded(Bitmap bitmap);
        void onPlayerError(String errorMessage);
        default void onQualityDetected(String quality) {}
        default void onQueueChanged(List<Song> queue) {}
    }

    private final CopyOnWriteArrayList<MusicChangeListener> changeListeners = new CopyOnWriteArrayList<>();

    public class MusicBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        mediaSession = new MediaSessionCompat(this, "MustreMediaSession");
        mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );
        mediaSession.setActive(true);

        setupMediaSessionCallbacks();
        initializeExoPlayer();
    }

    @OptIn(markerClass = UnstableApi.class)
    private void initializeExoPlayer() {
        // Buffer entire audio file to 100% full length so playback never drops during internet disconnects
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        60_000,           // minBufferMs (60s)
                        24 * 3600 * 1000, // maxBufferMs (24h - unconstrained to buffer full audio length)
                        1000,             // bufferForPlaybackMs (1s initial play delay)
                        2000              // bufferForPlaybackAfterRebufferMs (2s rebuffer delay)
                )
                .setTargetBufferBytes(C.LENGTH_UNSET) // Do not cap buffering by MB size limits
                .setPrioritizeTimeOverSizeThresholds(false) // Prioritize full duration loading
                .setBackBuffer(3600_000, true) // Retain 1 hour of back-buffer for seamless backwards seeking
                .build();

        DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true);

        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(
                MediaCacheManager.getCacheDataSourceFactory(this),
                extractorsFactory
        );

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(audioAttributes, true)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .build();

        repeatMode = ServerPrefs.getSavedRepeatMode(this);
        player.setRepeatMode(Player.REPEAT_MODE_OFF);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    onTrackEnded();
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updateNotification();
                for (MusicChangeListener listener : changeListeners) {
                    listener.onPlaybackStateChanged(isPlaying);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Throwable cause = error.getCause();
                String detailedMsg;
                boolean isNetworkError = false;

                if (cause instanceof androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                    int code = ((androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) cause).responseCode;
                    detailedMsg = "HTTP " + code + " error loading audio stream";
                    isNetworkError = true;
                } else if (cause instanceof androidx.media3.datasource.HttpDataSource.HttpDataSourceException
                        || cause instanceof java.net.UnknownHostException
                        || cause instanceof java.net.ConnectException
                        || cause instanceof java.net.SocketTimeoutException) {
                    detailedMsg = "Network error connecting to server";
                    isNetworkError = true;
                } else if (cause != null && cause.getMessage() != null && !cause.getMessage().trim().isEmpty()) {
                    detailedMsg = cause.getMessage();
                } else if (error.getMessage() != null) {
                    detailedMsg = error.getMessage();
                } else {
                    detailedMsg = "Playback failed";
                }

                if (isNetworkError) {
                    detailedMsg = "Connection failed. Please check network or server.";
                }

                Log.e(TAG, "ExoPlayer playback error (" + error.errorCode + "): " + detailedMsg, error);
                updateNotification();
                for (MusicChangeListener listener : changeListeners) {
                    listener.onPlayerError(detailedMsg);
                }
            }

            @Override
            public void onTracksChanged(@NonNull Tracks tracks) {
                for (Tracks.Group group : tracks.getGroups()) {
                    if (group.getType() == C.TRACK_TYPE_AUDIO && group.isSelected()) {
                        for (int i = 0; i < group.length; i++) {
                            if (group.isTrackSelected(i)) {
                                Format format = group.getTrackFormat(i);
                                handleAudioFormatDetected(format);
                                return;
                            }
                        }
                    }
                }
            }
        });
    }

    private void handleAudioFormatDetected(Format format) {
        if (format == null) return;
        this.currentAudioFormat = format;

        String mimeType = format.sampleMimeType != null ? format.sampleMimeType.toLowerCase() : "";
        int sampleRate = format.sampleRate;
        int pcmEncoding = format.pcmEncoding;
        int bitrate = format.bitrate;

        String sampleRateText = "";
        if (sampleRate > 0) {
            if (sampleRate % 1000 == 0) {
                sampleRateText = (sampleRate / 1000) + " kHz";
            } else {
                sampleRateText = String.format(java.util.Locale.US, "%.1f kHz", sampleRate / 1000.0);
            }
        }

        Song current = getCurrentSong();
        String path = (current != null && current.getAudioPath() != null) ? current.getAudioPath().toLowerCase() : "";

        String qualityText;
        if (mimeType.contains("flac") || path.endsWith(".flac") || path.endsWith(".alac") || path.endsWith(".wav") || path.contains("telegram")) {
            String bitDepth = (pcmEncoding == C.ENCODING_PCM_24BIT) ? "24-bit" :
                    ((pcmEncoding == C.ENCODING_PCM_32BIT || pcmEncoding == C.ENCODING_PCM_FLOAT) ? "32-bit" : "24-bit");
            String spec = !sampleRateText.isEmpty() ? bitDepth + " / " + sampleRateText : bitDepth + " / 44.1 kHz";
            qualityText = "Lossless • " + spec + " FLAC";
        } else if (mimeType.contains("opus") || path.endsWith(".opus")) {
            int kbps = bitrate > 0 ? bitrate / 1000 : 320;
            qualityText = "High Quality • " + kbps + " kbps Opus";
        } else if (mimeType.contains("mp4a") || mimeType.contains("aac") || path.endsWith(".m4a") || path.endsWith(".aac")) {
            int kbps = bitrate > 0 ? bitrate / 1000 : 256;
            qualityText = "AAC • " + kbps + " kbps";
        } else if (mimeType.contains("mpeg") || mimeType.contains("mp3") || path.endsWith(".mp3")) {
            int kbps = bitrate > 0 ? bitrate / 1000 : 320;
            qualityText = "MP3 • " + kbps + " kbps";
        } else {
            if (current != null && current.getQuality() != null && !current.getQuality().trim().isEmpty()) {
                qualityText = current.getQuality();
            } else {
                qualityText = "High Quality Audio";
            }
        }

        if (current != null) {
            current.setQuality(qualityText);
            ServerPrefs.saveCachedQualityText(this, current.getAudioPath(), qualityText);
        }

        for (MusicChangeListener listener : changeListeners) {
            listener.onQualityDetected(qualityText);
        }
    }

    private void setupMediaSessionCallbacks() {
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() { togglePlayPause(); }
            @Override
            public void onPause() { togglePlayPause(); }
            @Override
            public void onSkipToNext() { playNextSong(); }
            @Override
            public void onSkipToPrevious() { playPreviousSong(); }
            @Override
            public void onSeekTo(long pos) { seekTo(pos); }
        });
    }

    public void addMusicChangeListener(MusicChangeListener listener) {
        if (listener != null && !changeListeners.contains(listener)) {
            changeListeners.add(listener);
        }
    }

    public void removeMusicChangeListener(MusicChangeListener listener) {
        if (listener != null) {
            changeListeners.remove(listener);
        }
    }

    public void setMusicChangeListener(MusicChangeListener listener) {
        changeListeners.clear();
        if (listener != null) {
            changeListeners.add(listener);
        }
    }

    public boolean hasSongs() {
        return songList != null && !songList.isEmpty();
    }

    public synchronized void setOriginalList(List<Song> songs) {
        if (songs != null && !songs.isEmpty()) {
            this.originalList = new java.util.ArrayList<>(songs);
        }
    }

    private synchronized boolean checkAndReplenishQueue() {
        if (songList == null) {
            songList = new java.util.ArrayList<>();
        }
        if (songList.isEmpty()) {
            return false;
        }
        if (originalList == null || originalList.isEmpty()) {
            String cachedJson = ServerPrefs.getCachedSongsJson(this);
            if (cachedJson != null && !cachedJson.trim().isEmpty()) {
                try {
                    com.aadith.mustre.model.SongsResponse resp = new com.google.gson.Gson().fromJson(cachedJson, com.aadith.mustre.model.SongsResponse.class);
                    if (resp != null && resp.getSongs() != null && !resp.getSongs().isEmpty()) {
                        this.originalList = new java.util.ArrayList<>(resp.getSongs());
                    }
                } catch (Exception ignored) {}
            }
        }
        if (originalList == null || originalList.isEmpty()) return false;

        boolean added = false;
        while (songList.size() <= 1) {
            Song currentOrLast = !songList.isEmpty() ? songList.get(songList.size() - 1) : null;
            List<Song> candidates = new java.util.ArrayList<>(originalList);

            if (currentOrLast != null && candidates.size() > 1) {
                candidates.removeIf(s -> s.getAudioPath() != null &&
                        s.getAudioPath().equalsIgnoreCase(currentOrLast.getAudioPath()));
            }

            if (candidates.size() > songList.size()) {
                java.util.Set<String> existingPaths = new java.util.HashSet<>();
                for (Song s : songList) {
                    if (s.getAudioPath() != null) {
                        existingPaths.add(s.getAudioPath().toLowerCase());
                    }
                }
                candidates.removeIf(s -> s.getAudioPath() != null &&
                        existingPaths.contains(s.getAudioPath().toLowerCase()));
            }

            if (!candidates.isEmpty()) {
                int randomIndex = new java.util.Random().nextInt(candidates.size());
                Song randomSong = candidates.get(randomIndex);
                songList.add(randomSong);
                added = true;
            } else if (!originalList.isEmpty()) {
                Song randomSong = originalList.get(new java.util.Random().nextInt(originalList.size()));
                songList.add(randomSong);
                added = true;
            } else {
                break;
            }
        }

        if (added) {
            notifyQueueChanged();
        }
        return added;
    }

    public synchronized void setSongs(List<Song> songs, int initialIndex, boolean autoPlay) {
        if (songs == null || songs.isEmpty()) return;
        this.originalList = new java.util.ArrayList<>(songs);
        
        // Create a randomized queue starting with the selected song
        this.songList = new java.util.ArrayList<>();
        Song firstSong = this.originalList.get(initialIndex >= 0 && initialIndex < this.originalList.size() ? initialIndex : 0);
        this.songList.add(firstSong);
        
        List<Song> remaining = new java.util.ArrayList<>(this.originalList);
        remaining.remove(firstSong);
        java.util.Collections.shuffle(remaining);
        this.songList.addAll(remaining);
        
        this.currentSongIndex = 0;
        checkAndReplenishQueue();
        
        notifyQueueChanged();
        playSong(0, autoPlay);
    }

    public synchronized void addToQueue(Song song) {
        if (songList == null) {
            songList = new java.util.ArrayList<>();
        }
        songList.add(song);
        if (songList.size() == 1) {
            currentSongIndex = 0;
            checkAndReplenishQueue();
            notifyQueueChanged();
            playSong(0, true);
        } else {
            notifyQueueChanged();
            triggerPrefetch();
        }
    }

    public synchronized void removeFromQueue(int index) {
        if (songList != null && index >= 0 && index < songList.size()) {
            Song removed = songList.remove(index);
            if (index == 0) {
                addToHistory(removed);
                checkAndReplenishQueue();
                if (!songList.isEmpty()) {
                    currentSongIndex = 0;
                    playSong(0, true);
                } else {
                    currentSongIndex = -1;
                    if (player != null) player.stop();
                }
            }
            checkAndReplenishQueue();
            notifyQueueChanged();
            triggerPrefetch();
        }
    }

    public synchronized void clearQueue() {
        if (songList != null) {
            songList.clear();
            currentSongIndex = -1;
            playbackHistory.clear();
            notifyQueueChanged();
            triggerPrefetch();
        }
    }

    public synchronized void playImmediate(Song song) {
        MediaCacheManager.cancelPreload();
        if (song == null) return;
        Song current = getCurrentSong();
        if (current != null && !current.equals(song)) {
            addToHistory(current);
        }
        if (songList == null) {
            songList = new java.util.ArrayList<>();
        }
        if (songList.isEmpty()) {
            songList.add(song);
        } else {
            songList.add(0, song);
        }
        currentSongIndex = 0;
        checkAndReplenishQueue();
        notifyQueueChanged();
        playSong(0, true);
    }

    public synchronized void updateQueueOrder(List<Song> newOrder) {
        if (newOrder != null) {
            this.songList = new java.util.ArrayList<>(newOrder);
            checkAndReplenishQueue();
            notifyQueueChanged();
            triggerPrefetch();
        }
    }

    public synchronized void triggerPrefetch() {
        List<Song> snapshot = songList != null ? new java.util.ArrayList<>(songList) : null;
        if (snapshot != null && snapshot.size() > 1 && currentSongIndex >= 0 && currentSongIndex < snapshot.size()) {
            java.util.List<String> nextAudioUrls = new java.util.ArrayList<>();
            int prefetchCount = ServerPrefs.getPrefetchCount(this);

            for (int i = 1; i <= prefetchCount; i++) {
                int nextIndex = (currentSongIndex + i) % snapshot.size();
                if (nextIndex == currentSongIndex) break;

                Song nextSong = snapshot.get(nextIndex);
                if (nextSong != null && nextSong.getAudioPath() != null) {
                    String url = ServerPrefs.formatFullUrl(getBaseUrl(), nextSong.getAudioPath());
                    if (url != null && !nextAudioUrls.contains(url)) {
                        nextAudioUrls.add(url);
                    }
                }
            }

            MediaCacheManager.preloadNextSongs(this, nextAudioUrls);
        }
    }

    private void notifyQueueChanged() {
        List<Song> snapshot = songList != null ? new java.util.ArrayList<>(songList) : new java.util.ArrayList<>();
        for (MusicChangeListener listener : changeListeners) {
            listener.onQueueChanged(snapshot);
        }
    }

    public synchronized void playSong(int index, boolean autoPlay) {
        if (songList == null || songList.isEmpty()) return;

        // Remove all songs before the requested index (already played / skipped)
        while (index > 0 && !songList.isEmpty()) {
            Song removed = songList.remove(0);
            addToHistory(removed);
            index--;
        }
        currentSongIndex = 0;
        checkAndReplenishQueue();

        if (songList.isEmpty()) return;

        currentAudioFormat = null;
        Song song = songList.get(0);

        if (ServerPrefs.isOffline(this) && !ServerPrefs.isSongFullyCached(this, song)) {
            Log.w(TAG, "Offline mode active and song not cached: " + song.getTitle());

            // Look forward in queue for the next available completely cached song
            int nextCachedIndex = -1;
            for (int i = 1; i < songList.size(); i++) {
                if (ServerPrefs.isSongFullyCached(this, songList.get(i))) {
                    nextCachedIndex = i;
                    break;
                }
            }

            if (nextCachedIndex != -1) {
                final String skippedTitle = song.getTitle() != null ? song.getTitle() : "Track";
                mainHandler.post(() -> android.widget.Toast.makeText(this, "Skipped " + skippedTitle + " (not cached offline)", android.widget.Toast.LENGTH_SHORT).show());

                // Keep skipped uncached songs in queue by moving them to the end of the queue
                for (int k = 0; k < nextCachedIndex; k++) {
                    Song skipped = songList.remove(0);
                    songList.add(skipped);
                }

                notifyQueueChanged();
                playSong(0, autoPlay);
                return;
            } else {
                if (player != null) {
                    player.pause();
                }
                mainHandler.post(() -> android.widget.Toast.makeText(this, "Song is not cached", android.widget.Toast.LENGTH_SHORT).show());
                for (MusicChangeListener listener : changeListeners) {
                    listener.onPlayerError("Song is not cached");
                }
                return;
            }
        }

        File localAudioFile = ServerPrefs.resolveLocalAudioFile(this, song);
        if (localAudioFile != null && localAudioFile.exists() && localAudioFile.length() > 0) {
            song.setAudioPath(localAudioFile.getAbsolutePath());
            Log.d(TAG, "Playing song from verified local file: " + localAudioFile.getAbsolutePath());
        } else if (!ServerPrefs.isOffline(this)) {
            SongDownloader.getInstance(this).downloadSong(song, null);
        }

        String rawAudioUrl = ServerPrefs.formatFullUrl(getBaseUrl(), song.getAudioPath());
        if (rawAudioUrl == null || rawAudioUrl.trim().isEmpty()) {
            Log.e(TAG, "Audio URL is empty for song: " + song.getTitle());
            for (MusicChangeListener listener : changeListeners) {
                listener.onPlayerError("Audio path unavailable");
            }
            return;
        }

        mainHandler.post(() -> {
            if (player == null) return;
            try {
                Uri mediaUri;
                String currentPath = song.getAudioPath();
                if (currentPath != null && (currentPath.startsWith("/") || currentPath.startsWith("file://") || currentPath.startsWith("content://"))) {
                    String cleanPath = currentPath.startsWith("file://") ? currentPath.substring(7) : currentPath;
                    File localFile = new File(cleanPath);
                    if (localFile.exists()) {
                        mediaUri = Uri.fromFile(localFile);
                    } else {
                        mediaUri = Uri.parse(rawAudioUrl);
                    }
                } else {
                    mediaUri = Uri.parse(rawAudioUrl);
                }

                MediaItem mediaItem = MediaItem.fromUri(mediaUri);
                player.setMediaItem(mediaItem);
                player.prepare();

                if (autoPlay) {
                    player.play();
                } else {
                    player.pause();
                }
            } catch (Exception e) {
                Log.e(TAG, "ExoPlayer playback setup error: " + e.getMessage(), e);
            }
        });

        notifyQueueChanged();
        triggerPrefetch();

        loadCoverArt(song);

        for (MusicChangeListener listener : changeListeners) {
            listener.onSongChanged(song, 0);
        }
    }

    private void loadCoverArt(Song song) {
        String baseUrl = getBaseUrl();
        String coverUrl = ServerPrefs.formatCoverUrl(baseUrl, song.getCoverPath());
        String fallbackUrl = ServerPrefs.formatCoverUrl(baseUrl, null);

        boolean isOffline = ServerPrefs.isOffline(this);

        if (isOffline && (coverUrl.startsWith("http://") || coverUrl.startsWith("https://"))) {
            Glide.with(this)
                    .asBitmap()
                    .load(coverUrl)
                    .onlyRetrieveFromCache(true)
                    .error(Glide.with(this).asBitmap().load(fallbackUrl).onlyRetrieveFromCache(true))
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            currentCoverBitmap = resource;
                            updateNotification();
                            for (MusicChangeListener listener : changeListeners) {
                                listener.onCoverLoaded(resource);
                            }
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {}
                    });
        } else {
            Glide.with(this)
                    .asBitmap()
                    .load(coverUrl)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .error(Glide.with(this).asBitmap().load(fallbackUrl).diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL))
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            currentCoverBitmap = resource;
                            updateNotification();
                            for (MusicChangeListener listener : changeListeners) {
                                listener.onCoverLoaded(resource);
                            }
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {}
                    });
        }
    }

    public void togglePlayPause() {
        mainHandler.post(() -> {
            if (player == null) return;
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.play();
            }
        });
    }

    public void pause() {
        mainHandler.post(() -> {
            if (player != null && player.isPlaying()) {
                player.pause();
            }
        });
    }

    public void play() {
        mainHandler.post(() -> {
            if (player != null && !player.isPlaying()) {
                player.play();
            }
        });
    }

    public synchronized void playNextSong() {
        if (!hasSongs()) return;

        int mode = getRepeatMode();

        if (songList != null && songList.size() > 1) {
            Song finished = songList.remove(0);
            addToHistory(finished);
            if (mode == Player.REPEAT_MODE_ALL) {
                songList.add(finished);
            }
            notifyQueueChanged();
            playSong(0, true);
        } else if (originalList != null && !originalList.isEmpty()) {
            Song current = getCurrentSong();
            addToHistory(current);
            int currentIdx = findSongIndexInOriginalList(current);
            if (currentIdx < 0) currentIdx = 0;
            int nextIdx = (currentIdx + 1) % originalList.size();
            Song nextSong = originalList.get(nextIdx);
            playImmediate(nextSong);
        } else {
            currentSongIndex = -1;
            if (player != null) player.stop();
            notifyQueueChanged();
        }
    }

    public synchronized void playPreviousSong() {
        if (!hasSongs()) return;

        if (player != null && player.getCurrentPosition() > 3000) {
            player.seekTo(0);
            return;
        }

        if (!playbackHistory.isEmpty()) {
            Song prevSong = playbackHistory.pop();
            if (songList == null) {
                songList = new java.util.ArrayList<>();
            }
            songList.add(0, prevSong);
            currentSongIndex = 0;
            checkAndReplenishQueue();
            notifyQueueChanged();
            playSong(0, true);
        } else if (originalList != null && !originalList.isEmpty()) {
            int currentIdx = findSongIndexInOriginalList(getCurrentSong());
            if (currentIdx < 0) currentIdx = 0;
            int prevIdx = (currentIdx - 1 + originalList.size()) % originalList.size();
            Song prevSong = originalList.get(prevIdx);
            if (songList == null) {
                songList = new java.util.ArrayList<>();
            }
            songList.add(0, prevSong);
            currentSongIndex = 0;
            checkAndReplenishQueue();
            notifyQueueChanged();
            playSong(0, true);
        } else if (player != null) {
            player.seekTo(0);
        }
    }

    public synchronized Song getNextSong() {
        if (!hasSongs()) return null;
        if (songList != null && songList.size() > 1) {
            return songList.get(1);
        }
        if (originalList != null && !originalList.isEmpty()) {
            int currentIdx = findSongIndexInOriginalList(getCurrentSong());
            if (currentIdx < 0) currentIdx = 0;
            int idx = (currentIdx + 1) % originalList.size();
            return originalList.get(idx);
        }
        return getCurrentSong();
    }

    public synchronized Song getPreviousSong() {
        if (!hasSongs()) return null;
        if (!playbackHistory.isEmpty()) {
            return playbackHistory.peek();
        }
        if (originalList != null && !originalList.isEmpty()) {
            int currentIdx = findSongIndexInOriginalList(getCurrentSong());
            if (currentIdx < 0) currentIdx = 0;
            int idx = (currentIdx - 1 + originalList.size()) % originalList.size();
            return originalList.get(idx);
        }
        return getCurrentSong();
    }

    public synchronized void toggleRepeatMode() {
        int currentMode = getRepeatMode();
        if (currentMode == Player.REPEAT_MODE_OFF) {
            repeatMode = Player.REPEAT_MODE_ALL;
        } else if (currentMode == Player.REPEAT_MODE_ALL) {
            repeatMode = Player.REPEAT_MODE_ONE;
        } else {
            repeatMode = Player.REPEAT_MODE_OFF;
        }

        if (player != null) {
            player.setRepeatMode(Player.REPEAT_MODE_OFF);
        }

        ServerPrefs.saveRepeatMode(this, repeatMode);

        for (MusicChangeListener listener : changeListeners) {
            listener.onQueueChanged(getSongList());
        }
    }

    public synchronized int getRepeatMode() {
        return repeatMode;
    }

    private synchronized void onTrackEnded() {
        int mode = getRepeatMode();

        if (mode == Player.REPEAT_MODE_ONE) {
            if (hasSongs()) {
                playSong(0, true);
            }
            return;
        }

        if (mode == Player.REPEAT_MODE_ALL) {
            if (songList != null && !songList.isEmpty()) {
                if (songList.size() > 1) {
                    Song finished = songList.remove(0);
                    songList.add(finished);
                } else if (originalList != null && !originalList.isEmpty()) {
                    this.songList = new java.util.ArrayList<>(originalList);
                    this.currentSongIndex = 0;
                }
                notifyQueueChanged();
                playSong(0, true);
            }
            return;
        }

        if (songList != null && !songList.isEmpty()) {
            songList.remove(0);
            notifyQueueChanged();
            if (!songList.isEmpty()) {
                playSong(0, true);
            } else {
                currentSongIndex = -1;
                if (player != null) {
                    player.pause();
                    player.seekTo(0);
                }
                for (MusicChangeListener listener : changeListeners) {
                    listener.onPlaybackStateChanged(false);
                }
            }
        }
    }

    public void seekTo(long positionMs) {
        if (player != null) {
            player.seekTo(positionMs);
        }
    }

    public Song getCurrentSong() {
        if (hasSongs() && currentSongIndex >= 0 && currentSongIndex < songList.size()) {
            return songList.get(currentSongIndex);
        }
        return null;
    }

    public synchronized List<Song> getSongList() { 
        return songList != null ? new java.util.ArrayList<>(songList) : new java.util.ArrayList<>(); 
    }
    public int getCurrentSongIndex() { return currentSongIndex; }
    public boolean isPlaying() { return player != null && player.isPlaying(); }
    public long getCurrentPosition() { return player != null ? player.getCurrentPosition() : 0; }
    public long getDuration() { return player != null && player.getDuration() > 0 ? player.getDuration() : 0; }
    public long getBufferedPosition() { return player != null ? player.getBufferedPosition() : 0; }
    public Bitmap getCurrentCoverBitmap() { return currentCoverBitmap; }
    public Format getCurrentAudioFormat() { return currentAudioFormat; }

    public void updateNotification() {
        if (mediaSession == null || !hasSongs()) return;

        Song song = getCurrentSong();
        if (song == null) return;

        String title = song.getTitle() != null ? song.getTitle() : "Unknown Title";
        String artist = song.getArtist() != null ? song.getArtist() : "Unknown Artist";
        boolean playing = isPlaying();
        long pos = getCurrentPosition();
        long duration = getDuration();

        // Update Media Metadata
        MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);

        if (currentCoverBitmap != null) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentCoverBitmap);
        }

        mediaSession.setMetadata(metadataBuilder.build());

        // Update Playback State
        int state = playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(state, pos, 1.0f);

        mediaSession.setPlaybackState(stateBuilder.build());

        // Create Notification
        Intent openAppIntent = new Intent(this, MainActivity.class);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Intent prevIntent = new Intent(this, MusicService.class).setAction(ACTION_PREV);
        PendingIntent prevPendingIntent = PendingIntent.getService(
                this, 1, prevIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Intent playPauseIntent = new Intent(this, MusicService.class).setAction(ACTION_PLAY_PAUSE);
        PendingIntent playPausePendingIntent = PendingIntent.getService(
                this, 2, playPauseIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Intent nextIntent = new Intent(this, MusicService.class).setAction(ACTION_NEXT);
        PendingIntent nextPendingIntent = PendingIntent.getService(
                this, 3, nextIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        int playPauseIcon = playing ? R.drawable.ic_pause : R.drawable.ic_play;
        String playPauseTitle = playing ? "Pause" : "Play";

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.mustre)
                .setContentTitle(title)
                .setContentText(artist)
                .setLargeIcon(currentCoverBitmap)
                .setOngoing(playing)
                .setContentIntent(contentPendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(R.drawable.ic_previous, "Previous", prevPendingIntent)
                .addAction(playPauseIcon, playPauseTitle, playPausePendingIntent)
                .addAction(R.drawable.ic_next, "Next", nextPendingIntent)
                .setStyle(new MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .build();

        if (playing) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH);
            } else {
                stopForeground(false);
            }
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(NOTIFICATION_ID, notification);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACTION_PLAY_PAUSE.equals(action)) {
                togglePlayPause();
            } else if (ACTION_PREV.equals(action)) {
                playPreviousSong();
            } else if (ACTION_NEXT.equals(action)) {
                playNextSong();
            }
        }
        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Mustre Music Playback",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows notification and lockscreen playback controls");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);

        if (player != null) {
            try {
                player.stop();
                player.release();
            } catch (Exception ignored) {}
            player = null;
        }

        if (mediaSession != null) {
            try {
                mediaSession.setActive(false);
                mediaSession.release();
            } catch (Exception ignored) {}
            mediaSession = null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(NOTIFICATION_ID);
            nm.cancelAll();
        }

        try {
            stopService(new Intent(this, FloatingPlayerService.class));
        } catch (Exception ignored) {}

        stopSelf();
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        changeListeners.clear();
        if (player != null) {
            player.release();
            player = null;
        }
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        super.onDestroy();
    }
}
