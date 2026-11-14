package com.aadith.mustre.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AudioCacheManager {

    private static final String TAG = "AudioCacheManager";
    private static final long MAX_CACHE_SIZE_BYTES = 1024L * 1024L * 1024L; // 1 GB Cache Limit
    private static AudioCacheManager instance;

    private final File cacheDir;
    private final OkHttpClient client;
    private final ExecutorService executor;

    public interface CacheCallback {
        void onAudioReady(String audioPathOrUrl);
    }

    private AudioCacheManager(Context context) {
        cacheDir = new File(context.getCacheDir(), "audio_cache");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        client = new OkHttpClient.Builder()
                .proxy(java.net.Proxy.NO_PROXY)
                .build();
        executor = Executors.newSingleThreadExecutor();
    }

    public static synchronized AudioCacheManager getInstance(Context context) {
        if (instance == null) {
            instance = new AudioCacheManager(context.getApplicationContext());
        }
        return instance;
    }

    public boolean isAudioCached(String audioUrl) {
        if (audioUrl == null || audioUrl.trim().isEmpty()) return false;
        if (audioUrl.startsWith("/") || audioUrl.startsWith("file://") || audioUrl.startsWith("content://")) {
            String clean = audioUrl.startsWith("file://") ? audioUrl.substring(7) : audioUrl;
            File f = new File(clean);
            return f.exists() && f.length() > 0;
        }

        String fileName1 = md5(audioUrl) + getExtension(audioUrl);
        File cachedFile1 = new File(cacheDir, fileName1);
        if (cachedFile1.exists() && cachedFile1.length() > 0) return true;

        String pathOnly = getPathOnly(audioUrl);
        String fileName2 = md5(pathOnly) + getExtension(pathOnly);
        File cachedFile2 = new File(cacheDir, fileName2);
        return cachedFile2.exists() && cachedFile2.length() > 0;
    }

    public File getCachedFile(String audioUrl) {
        if (audioUrl == null || audioUrl.trim().isEmpty()) return null;
        if (audioUrl.startsWith("/") || audioUrl.startsWith("file://")) {
            String clean = audioUrl.startsWith("file://") ? audioUrl.substring(7) : audioUrl;
            File f = new File(clean);
            if (f.exists() && f.length() > 0) return f;
        }

        String fileName1 = md5(audioUrl) + getExtension(audioUrl);
        File cachedFile1 = new File(cacheDir, fileName1);
        if (cachedFile1.exists() && cachedFile1.length() > 0) return cachedFile1;

        String pathOnly = getPathOnly(audioUrl);
        String fileName2 = md5(pathOnly) + getExtension(pathOnly);
        File cachedFile2 = new File(cacheDir, fileName2);
        if (cachedFile2.exists() && cachedFile2.length() > 0) return cachedFile2;

        return null;
    }

    private String getPathOnly(String url) {
        if (url == null) return "";
        try {
            int q = url.indexOf('?');
            if (q >= 0) url = url.substring(0, q);
            int h = url.indexOf('#');
            if (h >= 0) url = url.substring(0, h);
            int schemeIndex = url.indexOf("://");
            if (schemeIndex >= 0) {
                int nextSlash = url.indexOf('/', schemeIndex + 3);
                if (nextSlash >= 0) {
                    return url.substring(nextSlash);
                }
            }
        } catch (Exception ignored) {}
        return url;
    }

    /**
     * Returns local cached file path if available, or streams & caches in background.
     */
    public void getAudioPath(String audioUrl, CacheCallback callback) {
        if (audioUrl == null || audioUrl.trim().isEmpty()) {
            callback.onAudioReady(audioUrl);
            return;
        }

        String fileName = md5(audioUrl) + getExtension(audioUrl);
        File cachedFile = new File(cacheDir, fileName);

        if (cachedFile.exists() && cachedFile.length() > 0) {
            Log.d(TAG, "Playing from 1GB local audio cache: " + cachedFile.getAbsolutePath());
            callback.onAudioReady(cachedFile.getAbsolutePath());
            return;
        }

        // Return network URL immediately for streaming while downloading in background
        callback.onAudioReady(audioUrl);

        // Download and cache for future offline playback
        executor.execute(() -> {
            try {
                Request request = new Request.Builder()
                        .url(audioUrl)
                        .header("User-Agent", "Mustre-AndroidApp/1.0")
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        File tempFile = new File(cacheDir, fileName + ".tmp");
                        try (InputStream is = response.body().byteStream();
                             FileOutputStream fos = new FileOutputStream(tempFile)) {
                            byte[] buffer = new byte[8192];
                            int read;
                            while ((read = is.read(buffer)) != -1) {
                                fos.write(buffer, 0, read);
                            }
                        }

                        if (cachedFile.exists()) {
                            //noinspection ResultOfMethodCallIgnored
                            cachedFile.delete();
                        }
                        if (tempFile.renameTo(cachedFile)) {
                            Log.d(TAG, "Audio cached successfully: " + cachedFile.getName());
                            enforceMaxCacheSize();
                        } else if (tempFile.exists()) {
                            //noinspection ResultOfMethodCallIgnored
                            tempFile.delete();
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Background audio caching failed for " + audioUrl, e);
                File tempFile = new File(cacheDir, fileName + ".tmp");
                if (tempFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    tempFile.delete();
                }
            }
        });
    }

    /**
     * Enforces the 1GB maximum cache size limit by deleting the oldest accessed audio files.
     */
    private void enforceMaxCacheSize() {
        File[] files = cacheDir.listFiles();
        if (files == null) return;

        long currentSize = 0;
        for (File file : files) {
            currentSize += file.length();
        }

        if (currentSize > MAX_CACHE_SIZE_BYTES) {
            Log.d(TAG, "Cache size (" + currentSize + " bytes) exceeds 1GB limit. Pruning oldest files...");
            Arrays.sort(files, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));

            for (File file : files) {
                if (currentSize <= MAX_CACHE_SIZE_BYTES * 0.8) {
                    break;
                }
                long fileSize = file.length();
                if (file.delete()) {
                    currentSize -= fileSize;
                    Log.d(TAG, "Deleted old audio cache file: " + file.getName());
                }
            }
        }
    }

    private String getExtension(String url) {
        if (url == null) return ".audio";
        try {
            int queryParam = url.indexOf('?');
            String cleanUrl = queryParam >= 0 ? url.substring(0, queryParam) : url;
            int hashParam = cleanUrl.indexOf('#');
            if (hashParam >= 0) cleanUrl = cleanUrl.substring(0, hashParam);
            int lastSlash = cleanUrl.lastIndexOf('/');
            String filename = lastSlash >= 0 ? cleanUrl.substring(lastSlash + 1) : cleanUrl;
            int dot = filename.lastIndexOf('.');
            if (dot >= 0 && dot < filename.length() - 1) {
                String ext = filename.substring(dot);
                if (ext.length() <= 6 && !ext.contains(":") && !ext.contains("/")) {
                    return ext;
                }
            }
        } catch (Exception ignored) {}
        return ".audio";
    }

    private String md5(String s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(s.getBytes());
            byte[] messageDigest = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }
}
