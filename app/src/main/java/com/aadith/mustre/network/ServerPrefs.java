package com.aadith.mustre.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import com.aadith.mustre.BuildConfig;
import com.aadith.mustre.model.Song;
import com.aadith.mustre.model.SongsResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class ServerPrefs {

    private static final String PREF_NAME = "mustre_server_prefs";
    private static final String KEY_SERVER_URL = "server_url";

    private static final String PREF_QUALITY_CACHE = "mustre_quality_cache";
    private static final String KEY_CACHED_SONGS_JSON = "cached_songs_json";
    private static final String KEY_FULLY_CACHED_SONGS_JSON = "fully_cached_songs_json";
    private static final String KEY_OFFLINE_MODE_ENABLED = "offline_mode_enabled";
    private static final String KEY_CONTRAST_PROTECTION_ENABLED = "contrast_protection_enabled";
    private static final String KEY_POPUP_PLAYER_ENABLED = "popup_player_enabled";
    private static final String KEY_OLED_MODE_ENABLED = "oled_mode_enabled";
    private static final String KEY_PREFETCH_COUNT = "prefetch_count";
    private static final String KEY_REPEAT_MODE = "repeat_mode";
    private static final String KEY_SAVED_SERVER_URLS = "saved_server_urls";

    public static String getServerUrl(Context context) {
        if (context == null) return BuildConfig.BASE_URL;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String url = prefs.getString(KEY_SERVER_URL, BuildConfig.BASE_URL);
        if (url != null && url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public static void setServerUrl(Context context, String url) {
        if (context == null) return;
        if (url != null && url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SERVER_URL, url).apply();
    }

    public static synchronized List<String> getSavedServerUrls(Context context) {
        if (context == null) return new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_SAVED_SERVER_URLS, null);
        List<String> list = new ArrayList<>();
        if (json != null && !json.trim().isEmpty()) {
            try {
                Type type = new TypeToken<List<String>>() {}.getType();
                List<String> parsed = new Gson().fromJson(json, type);
                if (parsed != null) list.addAll(parsed);
            } catch (Exception ignored) {}
        }
        String active = getServerUrl(context);
        if (active != null && !active.trim().isEmpty() && !list.contains(active)) {
            list.add(0, active);
        }
        return list;
    }

    public static synchronized void addSavedServerUrl(Context context, String url) {
        if (context == null || url == null || url.trim().isEmpty()) return;
        String cleanUrl = url.trim();
        if (cleanUrl.endsWith("/")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
        }
        List<String> list = getSavedServerUrls(context);
        if (!list.contains(cleanUrl)) {
            list.add(0, cleanUrl);
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_SAVED_SERVER_URLS, new Gson().toJson(list)).apply();
        }
    }

    public static synchronized void removeSavedServerUrl(Context context, String url) {
        if (context == null || url == null) return;
        List<String> list = getSavedServerUrls(context);
        if (list.remove(url) || list.remove(url.trim())) {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_SAVED_SERVER_URLS, new Gson().toJson(list)).apply();
        }
    }

    public static String getCachedSongsJson(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_CACHED_SONGS_JSON, null);
    }

    public static void saveCachedSongsJson(Context context, String json) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_CACHED_SONGS_JSON, json).apply();
    }

    public static synchronized List<Song> getFullyCachedSongs(Context context) {
        if (context == null) return new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_FULLY_CACHED_SONGS_JSON, null);
        if (json != null && !json.trim().isEmpty()) {
            List<Song> songs = parseSongsResponse(json);
            if (songs != null) return songs;
        }
        return new ArrayList<>();
    }

    public static synchronized void addFullyCachedSong(Context context, Song song) {
        if (context == null || song == null) return;
        List<Song> cached = getFullyCachedSongs(context);
        boolean exists = false;
        for (Song s : cached) {
            boolean samePath = s.getAudioPath() != null && song.getAudioPath() != null && s.getAudioPath().equalsIgnoreCase(song.getAudioPath());
            boolean sameTitle = s.getTitle() != null && song.getTitle() != null && s.getTitle().equalsIgnoreCase(song.getTitle())
                    && s.getArtist() != null && song.getArtist() != null && s.getArtist().equalsIgnoreCase(song.getArtist());
            if (samePath || sameTitle) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            cached.add(song);
            Gson gson = new Gson();
            SongsResponse resp = new SongsResponse();
            resp.setSongs(cached);
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_FULLY_CACHED_SONGS_JSON, gson.toJson(resp)).apply();
        }
    }

    public static boolean isOfflineModeEnabled(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_OFFLINE_MODE_ENABLED, false);
    }

    public static void setOfflineModeEnabled(Context context, boolean enabled) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_OFFLINE_MODE_ENABLED, enabled).apply();
    }

    public static boolean isNetworkAvailable(Context context) {
        if (context == null) return false;
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        android.net.Network network = cm.getActiveNetwork();
        if (network == null) return false;
        android.net.NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        return capabilities != null && (
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    public static boolean isOffline(Context context) {
        return isOfflineModeEnabled(context) || !isNetworkAvailable(context);
    }

    public static String getSongIdentityKey(Song song) {
        if (song == null) return "unknown::unknown";
        String title = song.getTitle() != null ? song.getTitle().trim().toLowerCase() : "";
        String artist = song.getArtist() != null ? song.getArtist().trim().toLowerCase() : "";
        if (!title.isEmpty() || !artist.isEmpty()) {
            return title + "::" + artist;
        }
        String path = song.getAudioPath() != null ? song.getAudioPath().trim().toLowerCase() : "";
        return path.isEmpty() ? "unknown::unknown" : path;
    }

    public static synchronized File resolveLocalAudioFile(Context context, Song song) {
        if (context == null || song == null) return null;
        String path = song.getAudioPath();

        // 1. Direct local path check
        if (path != null && !path.trim().isEmpty()) {
            String cleanPath = path.startsWith("file://") ? path.substring(7) : path;
            if (cleanPath.startsWith("/") || cleanPath.startsWith("content://")) {
                File f = new File(cleanPath);
                if (f.exists() && f.length() > 0) {
                    return f;
                }
            }
        }

        // 2. Check full_downloads directory (SongDownloader)
        try {
            File downloadDir = new File(context.getFilesDir(), "full_downloads");
            if (downloadDir.exists()) {
                String identityKey = getSongIdentityKey(song);
                String extension = getExtensionFromPath(path);
                String filename = md5String(identityKey) + extension;
                File targetFile = new File(downloadDir, filename);
                if (targetFile.exists() && targetFile.length() > 0) {
                    return targetFile;
                }

                if (path != null && !path.trim().isEmpty()) {
                    String pathFilename = md5String(path.trim()) + extension;
                    File pathFile = new File(downloadDir, pathFilename);
                    if (pathFile.exists() && pathFile.length() > 0) {
                        return pathFile;
                    }
                }
            }
        } catch (Exception ignored) {}

        // 3. Check audio_cache directory (AudioCacheManager)
        try {
            String baseUrl = getServerUrl(context);
            String fullUrl = formatFullUrl(baseUrl, path);
            File acFile = AudioCacheManager.getInstance(context).getCachedFile(fullUrl);
            if (acFile == null && path != null) {
                acFile = AudioCacheManager.getInstance(context).getCachedFile(path);
            }
            if (acFile != null && acFile.exists() && acFile.length() > 0) {
                return acFile;
            }
        } catch (Exception ignored) {}

        // 4. Check fully cached songs list in SharedPreferences
        try {
            List<Song> fullyCachedList = getFullyCachedSongs(context);
            for (Song s : fullyCachedList) {
                if (s == null) continue;
                boolean samePath = s.getAudioPath() != null && path != null && s.getAudioPath().equalsIgnoreCase(path);
                boolean sameTitle = s.getTitle() != null && song.getTitle() != null && s.getTitle().equalsIgnoreCase(song.getTitle())
                        && s.getArtist() != null && song.getArtist() != null && s.getArtist().equalsIgnoreCase(song.getArtist());
                if (samePath || sameTitle) {
                    String cachedPath = s.getAudioPath();
                    if (cachedPath != null && !cachedPath.trim().isEmpty()) {
                        String cleanPath = cachedPath.startsWith("file://") ? cachedPath.substring(7) : cachedPath;
                        File cf = new File(cleanPath);
                        if (cf.exists() && cf.length() > 0) {
                            return cf;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    private static String md5String(String s) {
        if (s == null) return "";
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

    private static String getExtensionFromPath(String url) {
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

    public static synchronized void reconcileLocalCatalog(Context context) {
        if (context == null) return;
        List<Song> cached = getFullyCachedSongs(context);
        List<Song> validList = new ArrayList<>();
        boolean changed = false;

        for (Song song : cached) {
            if (song == null) continue;
            File localFile = resolveLocalAudioFile(context, song);
            if (localFile != null && localFile.exists() && localFile.length() > 0) {
                song.setAudioPath(localFile.getAbsolutePath());
                validList.add(song);
            } else {
                changed = true;
            }
        }

        try {
            File dlDir = new File(context.getFilesDir(), "full_downloads");
            if (dlDir.exists()) {
                File[] files = dlDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.getName().endsWith(".tmp")) {
                            file.delete();
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        if (changed) {
            Gson gson = new Gson();
            SongsResponse resp = new SongsResponse();
            resp.setSongs(validList);
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_FULLY_CACHED_SONGS_JSON, gson.toJson(resp)).apply();
        }
    }

    public static synchronized List<Song> deduplicateSongs(List<Song> songs) {
        if (songs == null) return new ArrayList<>();
        java.util.Map<String, Song> map = new java.util.LinkedHashMap<>();
        for (Song s : songs) {
            if (s == null) continue;
            String key = getSongIdentityKey(s);
            if (!map.containsKey(key)) {
                map.put(key, s);
            }
        }
        return new ArrayList<>(map.values());
    }

    public static synchronized List<Song> getMergedVisibleSongs(Context context, List<Song> activeServerSongs, boolean isOffline) {
        reconcileLocalCatalog(context);
        List<Song> fullyCached = getFullyCachedSongs(context);

        if (isOffline) {
            List<Song> candidates = new ArrayList<>(fullyCached);

            String cachedJson = getCachedSongsJson(context);
            if (cachedJson != null && !cachedJson.trim().isEmpty()) {
                List<Song> lastServerSongs = parseSongsResponse(cachedJson);
                if (lastServerSongs != null) {
                    for (Song s : lastServerSongs) {
                        if (s == null) continue;
                        String key = getSongIdentityKey(s);
                        boolean exists = false;
                        for (Song c : candidates) {
                            if (key.equals(getSongIdentityKey(c))) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            candidates.add(s);
                        }
                    }
                }
            }

            List<Song> verifiedOffline = new ArrayList<>();
            for (Song s : candidates) {
                if (s == null) continue;
                if (isSongFullyCached(context, s)) {
                    File localFile = resolveLocalAudioFile(context, s);
                    if (localFile != null) {
                        s.setAudioPath(localFile.getAbsolutePath());
                    }
                    addFullyCachedSong(context, s);
                    verifiedOffline.add(s);
                }
            }
            return deduplicateSongs(verifiedOffline);
        }

        java.util.Map<String, Song> merged = new java.util.LinkedHashMap<>();

        if (activeServerSongs != null) {
            for (Song serverSong : activeServerSongs) {
                if (serverSong == null) continue;
                String key = getSongIdentityKey(serverSong);
                File localFile = resolveLocalAudioFile(context, serverSong);
                if (localFile != null) {
                    serverSong.setAudioPath(localFile.getAbsolutePath());
                }
                merged.put(key, serverSong);
            }
        }

        for (Song cached : fullyCached) {
            if (cached == null || !isSongFullyCached(context, cached)) continue;
            String key = getSongIdentityKey(cached);
            File localFile = resolveLocalAudioFile(context, cached);
            if (localFile != null) {
                cached.setAudioPath(localFile.getAbsolutePath());
            }
            if (!merged.containsKey(key)) {
                merged.put(key, cached);
            } else {
                Song existing = merged.get(key);
                if (existing != null && localFile != null) {
                    existing.setAudioPath(localFile.getAbsolutePath());
                }
            }
        }

        return new ArrayList<>(merged.values());
    }

    @OptIn(markerClass = UnstableApi.class)
    public static boolean isSongFullyCached(Context context, Song song) {
        if (song == null || context == null) return false;

        // 1. Physical file resolution
        File localFile = resolveLocalAudioFile(context, song);
        if (localFile != null && localFile.exists() && localFile.length() > 0) {
            return true;
        }

        // 2. ExoPlayer SimpleCache check
        String path = song.getAudioPath();
        if (path != null && !path.trim().isEmpty()) {
            String baseUrl = getServerUrl(context);
            String fullUrl = formatFullUrl(baseUrl, path);
            long cachedBytes = MediaCacheManager.getCachedBytesForUrl(context, fullUrl);
            long expectedSize = song.getSize();

            if (cachedBytes > 0) {
                if (expectedSize > 0) {
                    return cachedBytes >= (long) (expectedSize * 0.85);
                } else {
                    return cachedBytes >= 256 * 1024;
                }
            }
        }

        return false;
    }

    @OptIn(markerClass = UnstableApi.class)
    public static boolean isSongCachedLocally(Context context, Song song) {
        return isSongFullyCached(context, song);
    }

    public static boolean isContrastProtectionEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_CONTRAST_PROTECTION_ENABLED, true);
    }

    public static void setContrastProtectionEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_CONTRAST_PROTECTION_ENABLED, enabled).apply();
    }

    public static boolean isPopupPlayerEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_POPUP_PLAYER_ENABLED, false);
    }

    public static void setPopupPlayerEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_POPUP_PLAYER_ENABLED, enabled).apply();
    }

    public static boolean isOledModeEnabled(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_OLED_MODE_ENABLED, false);
    }

    public static void setOledModeEnabled(Context context, boolean enabled) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_OLED_MODE_ENABLED, enabled).apply();
    }

    public static int getSavedRepeatMode(Context context) {
        if (context == null) return androidx.media3.common.Player.REPEAT_MODE_OFF;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_REPEAT_MODE, androidx.media3.common.Player.REPEAT_MODE_OFF);
    }

    public static void saveRepeatMode(Context context, int mode) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_REPEAT_MODE, mode).apply();
    }

    public static int getPrefetchCount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_PREFETCH_COUNT, 2);
    }

    public static void setPrefetchCount(Context context, int count) {
        if (count < 1) count = 1;
        if (count > 10) count = 10;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_PREFETCH_COUNT, count).apply();
    }

    public static void clearAllCache(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_CACHED_SONGS_JSON).remove(KEY_FULLY_CACHED_SONGS_JSON).apply();

        SharedPreferences qPrefs = context.getSharedPreferences(PREF_QUALITY_CACHE, Context.MODE_PRIVATE);
        qPrefs.edit().clear().apply();

        try {
            File dlDir = new File(context.getFilesDir(), "full_downloads");
            if (dlDir.exists()) {
                File[] files = dlDir.listFiles();
                if (files != null) {
                    for (File f : files) f.delete();
                }
            }
        } catch (Exception ignored) {}
    }

    public static String getCachedQualityText(Context context, String audioPath) {
        if (audioPath == null || audioPath.trim().isEmpty()) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREF_QUALITY_CACHE, Context.MODE_PRIVATE);
        return prefs.getString(audioPath, null);
    }

    public static void saveCachedQualityText(Context context, String audioPath, String qualityText) {
        if (audioPath == null || audioPath.trim().isEmpty() || qualityText == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_QUALITY_CACHE, Context.MODE_PRIVATE);
        prefs.edit().putString(audioPath, qualityText).apply();
    }

    public static String getCachedSizeText(Context context, String audioPath) {
        if (audioPath == null || audioPath.trim().isEmpty()) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREF_QUALITY_CACHE, Context.MODE_PRIVATE);
        return prefs.getString(audioPath + "_size", null);
    }

    public static void saveCachedSizeText(Context context, String audioPath, String sizeText) {
        if (audioPath == null || audioPath.trim().isEmpty() || sizeText == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_QUALITY_CACHE, Context.MODE_PRIVATE);
        prefs.edit().putString(audioPath + "_size", sizeText).apply();
    }

    private static boolean isPrivateOrLocalHost(String host) {
        if (host == null) return false;
        if (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("10.0.2.2")) return true;
        return host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.");
    }

    public static List<Song> parseSongsResponse(String json) {
        if (json == null) return null;
        String trimmed = json.trim();
        if (trimmed.isEmpty()) return null;

        Gson gson = new Gson();
        try {
            if (trimmed.startsWith("[")) {
                Type listType = new TypeToken<List<Song>>() {}.getType();
                return gson.fromJson(trimmed, listType);
            }

            if (trimmed.startsWith("{")) {
                try {
                    SongsResponse response = gson.fromJson(trimmed, SongsResponse.class);
                    if (response != null && response.getSongs() != null && !response.getSongs().isEmpty()) {
                        return response.getSongs();
                    }
                } catch (Exception ignored) {}

                JsonObject obj = JsonParser.parseString(trimmed).getAsJsonObject();
                for (String key : new String[]{"songs", "data", "music", "tracks", "results", "items", "playlist"}) {
                    if (obj.has(key) && obj.get(key).isJsonArray()) {
                        Type listType = new TypeToken<List<Song>>() {}.getType();
                        return gson.fromJson(obj.get(key), listType);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("ServerPrefs", "Error parsing songs response: " + e.getMessage(), e);
        }
        return null;
    }

    public static String formatFullUrl(String baseUrl, String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }

        String trimmedPath = path.trim();

        if (trimmedPath.startsWith("file://") || trimmedPath.startsWith("content://")) {
            return trimmedPath;
        }

        if (trimmedPath.startsWith("/")) {
            String cleanPath = trimmedPath;
            if (cleanPath.contains("?")) cleanPath = cleanPath.substring(0, cleanPath.indexOf("?"));
            if (cleanPath.contains("#")) cleanPath = cleanPath.substring(0, cleanPath.indexOf("#"));
            File localFile = new File(cleanPath);
            if (localFile.exists() || cleanPath.startsWith("/data/") || cleanPath.startsWith("/storage/") || cleanPath.startsWith("/sdcard/")) {
                return trimmedPath;
            }
        }

        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            baseUrl = BuildConfig.BASE_URL;
        }

        trimmedPath = trimmedPath.replace("\\", "/");

        if (trimmedPath.startsWith("http://") || trimmedPath.startsWith("https://")) {
            try {
                android.net.Uri pathUri = android.net.Uri.parse(trimmedPath);
                String pathHost = pathUri.getHost();
                if (isPrivateOrLocalHost(pathHost)) {
                    android.net.Uri baseUri = android.net.Uri.parse(baseUrl);
                    String baseHost = baseUri.getHost();
                    if (baseHost != null && !baseHost.equalsIgnoreCase(pathHost)) {
                        int port = pathUri.getPort();
                        String portStr = port != -1 ? ":" + port : (baseUri.getPort() != -1 ? ":" + baseUri.getPort() : "");
                        String scheme = pathUri.getScheme() != null ? pathUri.getScheme() : "http";
                        String query = pathUri.getEncodedQuery() != null ? "?" + pathUri.getEncodedQuery() : "";
                        String fragment = pathUri.getEncodedFragment() != null ? "#" + pathUri.getEncodedFragment() : "";
                        String pathOnly = pathUri.getEncodedPath() != null ? pathUri.getEncodedPath() : "";
                        return scheme + "://" + baseHost + portStr + pathOnly + query + fragment;
                    }
                }
            } catch (Exception ignored) {}
            return trimmedPath;
        }

        if (trimmedPath.contains(" ")) {
            trimmedPath = trimmedPath.replace(" ", "%20");
        }

        if (trimmedPath.startsWith("/")) {
            return baseUrl + trimmedPath;
        }
        return baseUrl + "/" + trimmedPath;
    }

    public static String formatCoverUrl(String baseUrl, String path) {
        String fullUrl = formatFullUrl(baseUrl, path);
        if (fullUrl == null || fullUrl.trim().isEmpty()) {
            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                baseUrl = BuildConfig.BASE_URL;
            }
            return baseUrl + "/assets/images/spiderman1.jpg";
        }
        return fullUrl;
    }
}
