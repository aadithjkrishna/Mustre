package com.aadith.mustre.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.aadith.mustre.model.Song;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SongDownloader {

    private static final String TAG = "SongDownloader";
    private static SongDownloader instance;

    private final Context context;
    private final File downloadDir;
    private final OkHttpClient client;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final ConcurrentHashMap<String, Boolean> activeDownloads = new ConcurrentHashMap<>();

    public interface DownloadCallback {
        void onSuccess(Song downloadedSong);
        void onError(Exception e);
    }

    private SongDownloader(Context context) {
        this.context = context.getApplicationContext();
        this.downloadDir = new File(this.context.getFilesDir(), "full_downloads");
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }
        this.client = new OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
        this.executor = Executors.newCachedThreadPool();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized SongDownloader getInstance(Context context) {
        if (instance == null) {
            instance = new SongDownloader(context);
        }
        return instance;
    }

    public void downloadSong(Song song, DownloadCallback callback) {
        if (song == null || song.getAudioPath() == null || song.getAudioPath().trim().isEmpty()) {
            if (callback != null) callback.onError(new IllegalArgumentException("Invalid song audio path"));
            return;
        }

        String baseUrl = ServerPrefs.getServerUrl(context);
        String fullUrl = ServerPrefs.formatFullUrl(baseUrl, song.getAudioPath());
        if (fullUrl == null) {
            if (callback != null) callback.onError(new IllegalArgumentException("Invalid full URL"));
            return;
        }

        String downloadKey = ServerPrefs.getSongIdentityKey(song);
        if (Boolean.TRUE.equals(activeDownloads.get(downloadKey))) {
            Log.d(TAG, "Download already in progress for: " + song.getTitle());
            return;
        }

        // Check if already fully cached
        if (ServerPrefs.isSongFullyCached(context, song)) {
            Log.d(TAG, "Song already fully cached: " + song.getTitle());
            if (callback != null) mainHandler.post(() -> callback.onSuccess(song));
            return;
        }

        activeDownloads.put(downloadKey, true);

        executor.execute(() -> {
            File tempFile = null;
            try {
                String extension = getExtension(fullUrl);
                String filename = md5(downloadKey) + extension;
                File finalFile = new File(downloadDir, filename);
                tempFile = new File(downloadDir, filename + ".tmp");

                if (tempFile.exists()) {
                    tempFile.delete();
                }

                Request request = new Request.Builder()
                        .url(fullUrl)
                        .header("User-Agent", "Mustre-AndroidApp/1.0")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        throw new IOException("HTTP download failed code: " + response.code());
                    }

                    long contentLength = response.body().contentLength();
                    long expectedSize = song.getSize() > 0 ? song.getSize() : contentLength;

                    long bytesWritten = 0;
                    try (InputStream is = response.body().byteStream();
                         FileOutputStream fos = new FileOutputStream(tempFile)) {
                        byte[] buffer = new byte[16384];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                            bytesWritten += read;
                        }
                        fos.flush();
                    }

                    // Validation step
                    if (bytesWritten <= 0) {
                        throw new IOException("Downloaded file is empty (0 bytes)");
                    }

                    if (contentLength > 0 && bytesWritten != contentLength) {
                        throw new IOException("Incomplete download: wrote " + bytesWritten + " of expected " + contentLength + " bytes");
                    }

                    if (expectedSize > 0 && bytesWritten != expectedSize && contentLength > 0) {
                        throw new IOException("File size mismatch: wrote " + bytesWritten + " expected " + expectedSize);
                    }

                    if (!tempFile.canRead()) {
                        throw new IOException("Downloaded temp file is unreadable");
                    }

                    // Atomic promote
                    if (!tempFile.renameTo(finalFile)) {
                        // Fallback copy if rename fails across file systems
                        if (!copyFile(tempFile, finalFile) || !finalFile.exists()) {
                            throw new IOException("Failed to move temp download file to final location");
                        }
                        tempFile.delete();
                    }

                    Song downloadedSong = new Song(
                            song.getTitle(),
                            song.getArtist(),
                            song.getAlbum(),
                            finalFile.getAbsolutePath(),
                            song.getCoverPath(),
                            song.getQuality()
                    );
                    downloadedSong.setSize(bytesWritten);
                    downloadedSong.setSizeStr(song.getFormattedSize());

                    ServerPrefs.addFullyCachedSong(context, downloadedSong);

                    // Pre-cache song lyrics so they are permanently available offline
                    com.aadith.mustre.lyrics.LyricsRepository.getInstance().prefetchLyrics(context, downloadedSong, 0);

                    Log.d(TAG, "Song download verified & completed: " + downloadedSong.getTitle() + " (" + bytesWritten + " bytes)");
                    activeDownloads.remove(downloadKey);

                    if (callback != null) {
                        mainHandler.post(() -> callback.onSuccess(downloadedSong));
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Download failed for " + song.getTitle() + ": " + e.getMessage());
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
                activeDownloads.remove(downloadKey);
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(e));
                }
            }
        });
    }

    private boolean copyFile(File src, File dst) {
        try (InputStream in = new java.io.FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.flush();
            return dst.exists() && dst.length() == src.length();
        } catch (Exception e) {
            return false;
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
