package com.aadith.mustre.network;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheWriter;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.datasource.okhttp.OkHttpDataSource;

import java.io.File;
import java.net.ConnectException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;

@UnstableApi
public class MediaCacheManager {

    private static final String TAG = "MediaCacheManager";
    private static final long MAX_CACHE_SIZE_BYTES = 1024L * 1024L * 1024L; // 1 GB Cache Limit

    private static SimpleCache simpleCache;
    private static DataSource.Factory cacheDataSourceFactory;
    private static final ExecutorService preloadExecutor = Executors.newSingleThreadExecutor();

    public static synchronized SimpleCache getSimpleCache(Context context) {
        if (simpleCache == null) {
            File cacheDir = new File(context.getApplicationContext().getCacheDir(), "exoplayer_media_cache");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            LeastRecentlyUsedCacheEvictor evictor = new LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES);
            StandaloneDatabaseProvider databaseProvider = new StandaloneDatabaseProvider(context.getApplicationContext());
            simpleCache = new SimpleCache(cacheDir, evictor, databaseProvider);
        }
        return simpleCache;
    }

    public static synchronized DataSource.Factory getCacheDataSourceFactory(Context context) {
        if (cacheDataSourceFactory == null) {
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .connectionPool(new okhttp3.ConnectionPool(5, 15, TimeUnit.SECONDS))
                    .retryOnConnectionFailure(true)
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .addInterceptor(chain -> {
                        Request original = chain.request();
                        HttpUrl url = original.url();
                        String host = url.host();

                        try {
                            return chain.proceed(original);
                        } catch (java.io.IOException ioe) {
                            if (HttpHandler.isRunningOnEmulator() && !"10.0.2.2".equals(host)) {
                                Log.d(TAG, "ExoPlayer stream connection to " + host + " failed (" + ioe.getClass().getSimpleName()
                                        + ": " + ioe.getMessage() + "), attempting emulator 10.0.2.2 fallback");
                                try {
                                    HttpUrl fallback = url.newBuilder().host("10.0.2.2").build();
                                    return chain.proceed(original.newBuilder().url(fallback).build());
                                } catch (java.io.IOException fallbackEx) {
                                    Log.w(TAG, "Fallback to 10.0.2.2 failed: " + fallbackEx.getMessage());
                                }
                            }

                            // Retry once for transient socket reset or IO drop
                            try {
                                Thread.sleep(150);
                                return chain.proceed(original);
                            } catch (Exception ignored) {}

                            throw ioe;
                        }
                    })
                    .build();

            OkHttpDataSource.Factory httpDataSourceFactory = new OkHttpDataSource.Factory(okHttpClient)
                    .setUserAgent("Mustre-AndroidApp/1.0");

            DefaultDataSource.Factory defaultDataSourceFactory =
                    new DefaultDataSource.Factory(context.getApplicationContext(), httpDataSourceFactory);

            try {
                SimpleCache cache = getSimpleCache(context);
                androidx.media3.datasource.cache.CacheKeyFactory cacheKeyFactory = dataSpec -> {
                    if (dataSpec.key != null) return dataSpec.key;
                    String uriStr = dataSpec.uri.toString();
                    int q = uriStr.indexOf('?');
                    String clean = q >= 0 ? uriStr.substring(0, q) : uriStr;
                    int h = clean.indexOf('#');
                    return h >= 0 ? clean.substring(0, h) : clean;
                };

                cacheDataSourceFactory = new CacheDataSource.Factory()
                        .setCache(cache)
                        .setUpstreamDataSourceFactory(defaultDataSourceFactory)
                        .setCacheKeyFactory(cacheKeyFactory)
                        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
            } catch (Exception e) {
                Log.e(TAG, "SimpleCache init failed, falling back to default data source: " + e.getMessage(), e);
                cacheDataSourceFactory = defaultDataSourceFactory;
            }
        }
        return cacheDataSourceFactory;
    }

    public static synchronized void clearCache(Context context) {
        try {
            if (simpleCache != null) {
                simpleCache.release();
                simpleCache = null;
            }
            cacheDataSourceFactory = null;
            File cacheDir = new File(context.getApplicationContext().getCacheDir(), "exoplayer_media_cache");
            deleteDir(cacheDir);
        } catch (Exception e) {
            Log.w(TAG, "Failed clearing media cache: " + e.getMessage());
        }
    }

    private static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    deleteDir(new File(dir, child));
                }
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        }
        return false;
    }

    private static CacheWriter currentPreloadWriter;

    public static void cancelPreload() {
        if (currentPreloadWriter != null) {
            try {
                currentPreloadWriter.cancel();
            } catch (Exception ignored) {}
            currentPreloadWriter = null;
        }
    }

    /**
     * Pre-loads the next song audios in the background using available bandwidth.
     */
    public static void preloadNextSongs(Context context, List<String> audioUrls) {
        if (audioUrls == null || audioUrls.isEmpty()) {
            cancelPreload();
            return;
        }

        if (context != null && ServerPrefs.isOffline(context)) {
            cancelPreload();
            return;
        }

        // Cancel any active background preloading immediately on the calling thread
        cancelPreload();

        preloadExecutor.execute(() -> {
            for (String audioUrl : audioUrls) {
                if (audioUrl == null || audioUrl.trim().isEmpty()) continue;

                try {
                    Uri uri = Uri.parse(audioUrl);
                    DataSpec dataSpec = new DataSpec(uri);
                    DataSource dataSource = getCacheDataSourceFactory(context).createDataSource();

                    if (dataSource instanceof CacheDataSource) {
                        CacheWriter cacheWriter = new CacheWriter(
                                (CacheDataSource) dataSource,
                                dataSpec,
                                null,
                                (requestLength, bytesCached, newBytesCached) -> {}
                        );
                        currentPreloadWriter = cacheWriter;

                        Log.d(TAG, "Pre-buffering song in background: " + audioUrl);
                        cacheWriter.cache();
                        Log.d(TAG, "Song pre-buffered successfully: " + audioUrl);
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Pre-buffering stopped or completed for " + audioUrl);
                    break; // Interrupted or cancelled, stop preloading remaining URLs in this batch
                } finally {
                    currentPreloadWriter = null;
                }
            }
        });
    }

    public static long getCachedBytesForUrl(Context context, String url) {
        if (url == null || url.trim().isEmpty()) return 0;
        try {
            SimpleCache cache = getSimpleCache(context);
            if (cache != null) {
                long directBytes = cache.getCachedBytes(url, 0, androidx.media3.common.C.LENGTH_UNSET);
                if (directBytes > 0) return directBytes;

                String targetFilename = getFilename(url);
                if (!targetFilename.isEmpty()) {
                    for (String key : cache.getKeys()) {
                        if (key != null && (key.equals(url) || key.endsWith(url) || getFilename(key).equalsIgnoreCase(targetFilename))) {
                            long b = cache.getCachedBytes(key, 0, androidx.media3.common.C.LENGTH_UNSET);
                            if (b > 0) return b;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static String getFilename(String path) {
        if (path == null) return "";
        try {
            int q = path.indexOf('?');
            if (q >= 0) path = path.substring(0, q);
            int h = path.indexOf('#');
            if (h >= 0) path = path.substring(0, h);
            int slash = path.lastIndexOf('/');
            if (slash >= 0 && slash < path.length() - 1) {
                return path.substring(slash + 1);
            }
        } catch (Exception ignored) {}
        return path;
    }
}
