package com.aadith.mustre.network;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.aadith.mustre.model.Song;

import java.util.List;

public class CatalogRepository {

    private static final String TAG = "CatalogRepository";

    public interface CatalogCallback {
        void onCatalogLoaded(List<Song> songs, boolean isOffline, String message);
        void onRequestLocalNetworkPermission();
    }

    private int activeServerSessionId = 0;
    private boolean hasShownOfflineToast = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public List<Song> loadCachedSongsLocally(Context context) {
        return ServerPrefs.getMergedVisibleSongs(context, null, true);
    }

    public void fetchMusicData(Context context, String baseUrl, CatalogCallback callback) {
        final int currentSession = ++activeServerSessionId;

        if (ServerPrefs.isOffline(context)) {
            List<Song> fullSongList = loadCachedSongsLocally(context);
            if (!hasShownOfflineToast) {
                hasShownOfflineToast = true;
                if (callback != null) {
                    callback.onCatalogLoaded(fullSongList, true, "Offline Mode: Playing downloaded songs (" + fullSongList.size() + ")");
                }
            } else if (callback != null) {
                callback.onCatalogLoaded(fullSongList, true, null);
            }
            return;
        }

        hasShownOfflineToast = false;

        if (Build.VERSION.SDK_INT >= 37
                && context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
            if (callback != null) {
                callback.onRequestLocalNetworkPermission();
            }
            List<Song> fullSongList = loadCachedSongsLocally(context);
            if (callback != null) {
                callback.onCatalogLoaded(fullSongList, true, "Local network permission required. Showing downloaded songs (" + fullSongList.size() + ")");
            }
            return;
        }

        HttpHandler httpHandler = new HttpHandler();

        String songsUrl = baseUrl + "/songs";
        fetchSongsFromEndpoint(context, httpHandler, baseUrl, songsUrl, currentSession, callback, () -> {
            String apiSongsUrl = baseUrl + "/api/songs";
            fetchSongsFromEndpoint(context, httpHandler, baseUrl, apiSongsUrl, currentSession, callback, () -> 
                fetchSongsFromEndpoint(context, httpHandler, baseUrl, baseUrl, currentSession, callback, () -> {
                    mainHandler.post(() -> {
                        if (currentSession != activeServerSessionId) return;
                        List<Song> fullSongList = loadCachedSongsLocally(context);
                        if (!hasShownOfflineToast) {
                            hasShownOfflineToast = true;
                            if (callback != null) {
                                callback.onCatalogLoaded(fullSongList, true, "Server unreachable. Showing downloaded songs (" + fullSongList.size() + ")");
                            }
                        } else if (callback != null) {
                            callback.onCatalogLoaded(fullSongList, true, null);
                        }
                    });
                })
            );
        });
    }

    private void fetchSongsFromEndpoint(Context context, HttpHandler httpHandler, String baseUrl, String url, int sessionId, CatalogCallback callback, Runnable onFailed) {
        httpHandler.makeHttpGetRequest(url, new HttpHandler.MusicDataCallback() {
            @Override
            public void onSuccess(String result) {
                if (sessionId != activeServerSessionId || !url.startsWith(baseUrl)) {
                    Log.d(TAG, "Ignoring stale response from " + url);
                    return;
                }
                try {
                    List<Song> serverSongs = ServerPrefs.parseSongsResponse(result);
                    if (serverSongs != null && !serverSongs.isEmpty()) {
                        ServerPrefs.saveCachedSongsJson(context, result);
                        List<Song> merged = ServerPrefs.getMergedVisibleSongs(context, serverSongs, false);
                        mainHandler.post(() -> {
                            if (sessionId != activeServerSessionId) return;
                            if (callback != null) {
                                callback.onCatalogLoaded(merged, false, "Connected! Loaded " + serverSongs.size() + " songs");
                            }
                        });
                    } else if (onFailed != null) {
                        onFailed.run();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing JSON from " + url, e);
                    if (onFailed != null) {
                        onFailed.run();
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                if (sessionId != activeServerSessionId) return;
                Log.w(TAG, "Endpoint " + url + " failed: " + e.getMessage());
                if (onFailed != null) {
                    onFailed.run();
                }
            }
        });
    }
}
