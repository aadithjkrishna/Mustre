package com.aadith.mustre.lyrics;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;

import com.aadith.mustre.model.Lyrics;
import com.aadith.mustre.model.Song;
import com.aadith.mustre.network.ServerPrefs;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class LyricsRepository {

    private static final String TAG = "LyricsRepository";
    private static final String BASE_URL = "https://lrclib.net";
    private static final String USER_AGENT = "Mustre/1.0 (https://github.com/aadith/Mustre)";
    private static final String LYRICS_DIR_NAME = "lyrics";
    private static final String LEGACY_CACHE_DIR_NAME = "lyrics_cache";

    private static volatile LyricsRepository instance;

    public static LyricsRepository getInstance() {
        if (instance == null) {
            synchronized (LyricsRepository.class) {
                if (instance == null) {
                    instance = new LyricsRepository();
                }
            }
        }
        return instance;
    }

    public interface LyricsCallback {
        void onLyricsLoaded(Lyrics lyrics);
        void onLyricsError(String errorMessage);
    }

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectionPool(new ConnectionPool(5, 30, TimeUnit.SECONDS))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    // High-capacity in-memory cache
    private final LruCache<String, Lyrics> memoryCache = new LruCache<>(100);

    public boolean isLyricsCached(Context context, Song song) {
        if (song == null || context == null) return false;
        List<String> keys = generateCacheKeys(song);
        for (String key : keys) {
            if (memoryCache.get(key) != null) return true;
            File diskFile = getPersistentLyricsFile(context, key);
            if (diskFile != null && diskFile.exists() && diskFile.length() > 0) return true;
            File legacyFile = getLegacyCacheFile(context, key);
            if (legacyFile != null && legacyFile.exists() && legacyFile.length() > 0) return true;
        }
        return false;
    }

    public void prefetchLyrics(Context context, Song song, long durationMs) {
        if (song == null || context == null) return;
        if (isLyricsCached(context, song)) return;

        fetchLyrics(context, song, durationMs, false, new LyricsCallback() {
            @Override
            public void onLyricsLoaded(Lyrics lyrics) {
                Log.d(TAG, "Prefetched lyrics successfully for: " + song.getTitle());
            }

            @Override
            public void onLyricsError(String errorMessage) {
                Log.d(TAG, "Prefetch failed for " + song.getTitle() + ": " + errorMessage);
            }
        });
    }

    public void fetchLyrics(Context context, Song song, long durationMs, boolean forceRefresh, @NonNull LyricsCallback callback) {
        if (song == null) {
            callback.onLyricsError("Song is null");
            return;
        }

        List<String> cacheKeys = generateCacheKeys(song);
        String primaryKey = cacheKeys.get(0);

        // 1. Check in-memory cache
        if (!forceRefresh) {
            for (String key : cacheKeys) {
                Lyrics cached = memoryCache.get(key);
                if (cached != null) {
                    callback.onLyricsLoaded(cached);
                    return;
                }
            }
        }

        executorService.execute(() -> {
            // 2. Check persistent disk storage (and migrate from legacy cache if present)
            if (!forceRefresh && context != null) {
                for (String key : cacheKeys) {
                    Lyrics diskLyrics = loadFromDisk(context, key);
                    if (diskLyrics != null) {
                        for (String k : cacheKeys) {
                            memoryCache.put(k, diskLyrics);
                        }
                        mainHandler.post(() -> callback.onLyricsLoaded(diskLyrics));
                        return;
                    }
                }
            }

            // If offline and not in cache, avoid hanging on network
            if (context != null && ServerPrefs.isOffline(context)) {
                mainHandler.post(() -> callback.onLyricsError("Offline: Lyrics not cached for this song"));
                return;
            }

            // 3. Fetch from LRCLIB using cascading search strategies
            String rawTitle = song.getTitle() != null ? song.getTitle() : "";
            String rawArtist = song.getArtist() != null ? song.getArtist() : "";
            String rawAlbum = song.getAlbum() != null ? song.getAlbum() : "";

            String cleanTitle = cleanTitle(rawTitle);
            String cleanArtist = cleanArtist(rawArtist);
            int durationSec = durationMs > 0 ? (int) Math.round(durationMs / 1000.0) : 0;

            Lyrics result = null;

            // Strategy 1: Exact /api/get with all metadata
            if (!cleanTitle.isEmpty() && !cleanArtist.isEmpty()) {
                result = tryApiGet(cleanTitle, cleanArtist, rawAlbum, durationSec);
            }

            // Strategy 2: Exact /api/get without duration/album
            if (result == null && !cleanTitle.isEmpty() && !cleanArtist.isEmpty()) {
                result = tryApiGet(cleanTitle, cleanArtist, null, 0);
            }

            // Strategy 3: /api/search with track_name and artist_name
            if (result == null && !cleanTitle.isEmpty() && !cleanArtist.isEmpty()) {
                result = tryApiSearchFields(cleanTitle, cleanArtist, durationSec);
            }

            // Strategy 4: /api/search with query q = "Artist Title"
            if (result == null) {
                String query = (!cleanArtist.isEmpty() ? cleanArtist + " " : "") + cleanTitle;
                if (!query.trim().isEmpty()) {
                    result = tryApiSearchQuery(query.trim(), durationSec);
                }
            }

            // Strategy 5: Raw title query fallback
            if (result == null && !rawTitle.isEmpty() && !rawTitle.equals(cleanTitle)) {
                result = tryApiSearchQuery(rawTitle.trim(), durationSec);
            }

            if (result != null) {
                result.getParsedLines();

                // Cache in memory for all matching keys
                for (String k : cacheKeys) {
                    memoryCache.put(k, result);
                }

                // Persist atomically to disk
                if (context != null) {
                    saveToPersistentDisk(context, primaryKey, result);
                }

                final Lyrics finalResult = result;
                mainHandler.post(() -> callback.onLyricsLoaded(finalResult));
            } else {
                mainHandler.post(() -> callback.onLyricsError("No lyrics found on LRCLIB"));
            }
        });
    }

    private Lyrics loadFromDisk(Context context, String key) {
        // Try persistent filesDir first
        File persistentFile = getPersistentLyricsFile(context, key);
        if (persistentFile != null && persistentFile.exists() && persistentFile.length() > 0) {
            try (FileReader reader = new FileReader(persistentFile)) {
                Lyrics diskLyrics = gson.fromJson(reader, Lyrics.class);
                if (diskLyrics != null) {
                    diskLyrics.getParsedLines();
                    return diskLyrics;
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed reading persistent lyrics file: " + e.getMessage());
            }
        }

        // Check legacy cacheDir and migrate if found
        File legacyFile = getLegacyCacheFile(context, key);
        if (legacyFile != null && legacyFile.exists() && legacyFile.length() > 0) {
            try (FileReader reader = new FileReader(legacyFile)) {
                Lyrics diskLyrics = gson.fromJson(reader, Lyrics.class);
                if (diskLyrics != null) {
                    diskLyrics.getParsedLines();
                    // Migrate to persistent storage
                    saveToPersistentDisk(context, key, diskLyrics);
                    //noinspection ResultOfMethodCallIgnored
                    legacyFile.delete();
                    return diskLyrics;
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed reading legacy lyrics file: " + e.getMessage());
            }
        }

        return null;
    }

    private Lyrics tryApiGet(String trackName, String artistName, String albumName, int durationSec) {
        try {
            HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_URL + "/api/get").newBuilder();
            urlBuilder.addQueryParameter("track_name", trackName);
            urlBuilder.addQueryParameter("artist_name", artistName);
            if (albumName != null && !albumName.trim().isEmpty()) {
                urlBuilder.addQueryParameter("album_name", albumName.trim());
            }
            if (durationSec > 0) {
                urlBuilder.addQueryParameter("duration", String.valueOf(durationSec));
            }

            Request request = new Request.Builder()
                    .url(urlBuilder.build())
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    Lyrics lyrics = gson.fromJson(json, Lyrics.class);
                    if (isValidLyrics(lyrics)) {
                        return lyrics;
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "tryApiGet error: " + e.getMessage());
        }
        return null;
    }

    private Lyrics tryApiSearchFields(String trackName, String artistName, int targetDurationSec) {
        try {
            HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_URL + "/api/search").newBuilder();
            urlBuilder.addQueryParameter("track_name", trackName);
            urlBuilder.addQueryParameter("artist_name", artistName);

            return executeSearchAndPickBest(urlBuilder.build(), targetDurationSec);
        } catch (Exception e) {
            Log.d(TAG, "tryApiSearchFields error: " + e.getMessage());
        }
        return null;
    }

    private Lyrics tryApiSearchQuery(String query, int targetDurationSec) {
        try {
            HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_URL + "/api/search").newBuilder();
            urlBuilder.addQueryParameter("q", query);

            return executeSearchAndPickBest(urlBuilder.build(), targetDurationSec);
        } catch (Exception e) {
            Log.d(TAG, "tryApiSearchQuery error: " + e.getMessage());
        }
        return null;
    }

    private Lyrics executeSearchAndPickBest(HttpUrl url, int targetDurationSec) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String json = response.body().string();
                Type listType = new TypeToken<List<Lyrics>>() {}.getType();
                List<Lyrics> results = gson.fromJson(json, listType);

                if (results != null && !results.isEmpty()) {
                    Lyrics best = null;
                    double bestScore = Double.MAX_VALUE;

                    for (Lyrics l : results) {
                        if (!isValidLyrics(l)) continue;

                        boolean hasSynced = l.getSyncedLyrics() != null && !l.getSyncedLyrics().trim().isEmpty();
                        boolean hasWords = l.hasWordSync() || (l.getLyricsfile() != null && l.getLyricsfile().contains("words:"));
                        double durationDiff = targetDurationSec > 0 && l.getDuration() > 0
                                ? Math.abs(l.getDuration() - targetDurationSec)
                                : 0.0;

                        // Lower score is better. Prioritize synced lyrics, with extra boost for word sync
                        double score = durationDiff;
                        if (!hasSynced) {
                            score += 5000.0;
                        } else if (hasWords) {
                            score -= 1000.0;
                        }

                        if (best == null || score < bestScore) {
                            best = l;
                            bestScore = score;
                        }
                    }

                    return best != null ? best : results.get(0);
                }
            }
        }
        return null;
    }

    private boolean isValidLyrics(Lyrics lyrics) {
        if (lyrics == null) return false;
        if (lyrics.isInstrumental()) return true;
        boolean hasSynced = lyrics.getSyncedLyrics() != null && !lyrics.getSyncedLyrics().trim().isEmpty();
        boolean hasPlain = lyrics.getPlainLyrics() != null && !lyrics.getPlainLyrics().trim().isEmpty();
        return hasSynced || hasPlain;
    }

    public static String cleanTitle(String rawTitle) {
        if (rawTitle == null) return "";
        String s = rawTitle.trim();
        // Remove common audio extensions
        s = s.replaceAll("(?i)\\.(mp3|flac|wav|opus|m4a|aac|ogg|alac)$", "");
        // Remove leading track numbers like "01. ", "01 - ", "1. "
        s = s.replaceAll("^\\d{1,3}\\s*[-.]\\s*", "");
        // If "Artist - Title", extract title
        if (s.contains(" - ")) {
            String[] parts = s.split(" - ", 2);
            if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                s = parts[1].trim();
            }
        }
        // Remove parentheses / brackets with metadata
        s = s.replaceAll("(?i)\\s*\\([^)]*(?:official|video|audio|lyric|lyrics|remaster|remastered|live|edit|bonus|feat|ft\\.)[^)]*\\)", "");
        s = s.replaceAll("(?i)\\s*\\[[^\\]]*(?:official|video|audio|lyric|lyrics|remaster|remastered|live|edit|bonus|feat|ft\\.)[^\\]]*\\]", "");
        // Remove trailing feat / ft
        s = s.replaceAll("(?i)\\s*(?:feat|ft)\\.?\\s+.*$", "");
        return s.trim();
    }

    public static String cleanArtist(String rawArtist) {
        if (rawArtist == null) return "";
        String a = rawArtist.trim();
        // Remove feat / ft
        a = a.replaceAll("(?i)\\s*(?:feat|ft)\\.?\\s+.*$", "");
        return a.trim();
    }

    public static List<String> generateCacheKeys(Song song) {
        List<String> keys = new ArrayList<>();
        if (song == null) return keys;

        // Key 1: Cleaned normalized key (primary)
        String cleanT = cleanTitle(song.getTitle()).toLowerCase();
        String cleanA = cleanArtist(song.getArtist()).toLowerCase();
        String primaryIdentity = cleanT + "::" + cleanA;
        keys.add(md5(primaryIdentity));

        // Key 2: Exact ServerPrefs identity key
        String serverIdentity = ServerPrefs.getSongIdentityKey(song);
        String serverKey = md5(serverIdentity);
        if (!keys.contains(serverKey)) {
            keys.add(serverKey);
        }

        // Key 3: Raw Title and Artist
        String rawT = song.getTitle() != null ? song.getTitle().trim().toLowerCase() : "";
        String rawA = song.getArtist() != null ? song.getArtist().trim().toLowerCase() : "";
        String rawKey = md5(rawA + "_" + rawT);
        if (!keys.contains(rawKey)) {
            keys.add(rawKey);
        }

        return keys;
    }

    private File getPersistentLyricsFile(Context context, String key) {
        if (context == null || key == null) return null;
        File dir = new File(context.getFilesDir(), LYRICS_DIR_NAME);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return new File(dir, key + ".json");
    }

    private File getLegacyCacheFile(Context context, String key) {
        if (context == null || key == null) return null;
        File dir = new File(context.getCacheDir(), LEGACY_CACHE_DIR_NAME);
        if (!dir.exists()) {
            return null;
        }
        return new File(dir, key + ".json");
    }

    private void saveToPersistentDisk(Context context, String key, Lyrics lyrics) {
        if (context == null || key == null || lyrics == null) return;
        File targetFile = getPersistentLyricsFile(context, key);
        if (targetFile == null) return;

        File tempFile = new File(targetFile.getParentFile(), targetFile.getName() + ".tmp");
        try (FileWriter writer = new FileWriter(tempFile)) {
            gson.toJson(lyrics, writer);
            writer.flush();
            if (!tempFile.renameTo(targetFile)) {
                if (targetFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    targetFile.delete();
                }
                //noinspection ResultOfMethodCallIgnored
                tempFile.renameTo(targetFile);
            }
        } catch (Exception e) {
            Log.w(TAG, "saveToPersistentDisk failed: " + e.getMessage());
            if (tempFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            }
        }
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
