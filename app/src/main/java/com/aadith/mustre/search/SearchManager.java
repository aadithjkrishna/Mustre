package com.aadith.mustre.search;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.aadith.mustre.model.SearchResult;
import com.aadith.mustre.model.Song;
import com.aadith.mustre.network.HttpHandler;
import com.aadith.mustre.network.ServerPrefs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SearchManager {

    private static final String TAG = "SearchManager";

    public interface SearchCallback {
        void onSearchResultsUpdated(List<SearchResult> results, boolean isPending, boolean isOffline);
    }

    private int searchGeneration = 0;
    private boolean isTelegramSearchPending = false;
    private boolean isYouTubeSearchPending = false;
    private boolean isUnifiedSearchPending = false;

    private final List<SearchResult> currentLocalResults = new ArrayList<>();
    private final List<SearchResult> currentTelegramResults = new ArrayList<>();
    private final List<SearchResult> currentYouTubeResults = new ArrayList<>();
    private final List<SearchResult> currentUnifiedResults = new ArrayList<>();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void filterSongs(List<Song> fullSongList, String query, SearchCallback callback) {
        currentLocalResults.clear();
        if (fullSongList == null) return;

        if (query == null || query.trim().isEmpty()) {
            notifyCombinedSearchResults(callback, false);
            return;
        }

        String lowerQuery = query.toLowerCase().trim();
        for (Song song : fullSongList) {
            String title = song.getTitle() != null ? song.getTitle().toLowerCase() : "";
            String artist = song.getArtist() != null ? song.getArtist().toLowerCase() : "";
            if (title.contains(lowerQuery) || artist.contains(lowerQuery)) {
                SearchResult sr = createLocalSearchResult(song);
                currentLocalResults.add(sr);
            }
        }
        notifyCombinedSearchResults(callback, isSearchPending());
    }

    private SearchResult createLocalSearchResult(Song song) {
        SearchResult sr = new SearchResult();
        sr.setId(song.getAudioPath() != null ? song.getAudioPath() : song.getTitle());
        sr.setTitle(song.getTitle());
        sr.setArtist(song.getArtist());
        sr.setAlbum(song.getAlbum());
        sr.setImgUrl(song.getCoverPath());
        sr.setUrl(song.getAudioPath());
        sr.setQuality(song.getQuality());
        sr.setFormattedSize(song.getFormattedSize());
        sr.setSource("Local");
        return sr;
    }

    public void clearResults(SearchCallback callback) {
        currentLocalResults.clear();
        currentTelegramResults.clear();
        currentYouTubeResults.clear();
        currentUnifiedResults.clear();
        isTelegramSearchPending = false;
        isYouTubeSearchPending = false;
        isUnifiedSearchPending = false;
        notifyCombinedSearchResults(callback, false);
    }

    public boolean isSearchPending() {
        return isTelegramSearchPending || isYouTubeSearchPending || isUnifiedSearchPending;
    }

    public void performSearch(Context context, List<Song> fullSongList, String query, String baseUrl, String currentInputText, SearchCallback callback) {
        if (query == null || query.trim().isEmpty()) {
            clearResults(callback);
            return;
        }

        final String activeQuery = query.trim();
        final int currentGen = ++searchGeneration;

        // 1. Immediately filter and display local library results
        currentLocalResults.clear();
        if (fullSongList != null) {
            String lowerQ = activeQuery.toLowerCase();
            for (Song s : fullSongList) {
                String t = s.getTitle() != null ? s.getTitle().toLowerCase() : "";
                String a = s.getArtist() != null ? s.getArtist().toLowerCase() : "";
                if (t.contains(lowerQ) || a.contains(lowerQ)) {
                    SearchResult sr = createLocalSearchResult(s);
                    currentLocalResults.add(sr);
                }
            }
        }

        if (ServerPrefs.isOffline(context)) {
            currentTelegramResults.clear();
            currentYouTubeResults.clear();
            currentUnifiedResults.clear();
            isTelegramSearchPending = false;
            isYouTubeSearchPending = false;
            isUnifiedSearchPending = false;
            notifyCombinedSearchResults(callback, false, true);
            return;
        }

        currentTelegramResults.clear();
        currentYouTubeResults.clear();
        currentUnifiedResults.clear();
        isTelegramSearchPending = true;
        isYouTubeSearchPending = true;
        isUnifiedSearchPending = true;

        notifyCombinedSearchResults(callback, true);

        HttpHandler httpHandler = new HttpHandler();

        // 1. Telegram Source
        Map<String, String> telegramParams = new HashMap<>();
        telegramParams.put("q", activeQuery);
        telegramParams.put("query", activeQuery);
        telegramParams.put("source", "telegram");

        executeSearchWithFallback(httpHandler, baseUrl, telegramParams, new HttpHandler.MusicDataCallback() {
            @Override
            public void onSuccess(String result) {
                if (currentGen != searchGeneration && !activeQuery.equalsIgnoreCase(currentInputText)) return;
                Log.d(TAG, "Telegram search returned: " + result);
                List<SearchResult> parsed = parseSearchResults(result, "telegram");
                mainHandler.post(() -> {
                    if (currentGen != searchGeneration && !activeQuery.equalsIgnoreCase(currentInputText)) return;
                    isTelegramSearchPending = false;
                    currentTelegramResults.clear();
                    if (parsed != null) currentTelegramResults.addAll(parsed);
                    notifyCombinedSearchResults(callback, isSearchPending());
                });
            }

            @Override
            public void onError(Exception e) {
                Log.w(TAG, "Telegram search request failed: " + e.getMessage());
                mainHandler.post(() -> {
                    isTelegramSearchPending = false;
                    notifyCombinedSearchResults(callback, isSearchPending());
                });
            }
        });

        // 2. YouTube Source
        Map<String, String> youtubeParams = new HashMap<>();
        youtubeParams.put("q", activeQuery);
        youtubeParams.put("query", activeQuery);
        youtubeParams.put("source", "youtube");

        executeSearchWithFallback(httpHandler, baseUrl, youtubeParams, new HttpHandler.MusicDataCallback() {
            @Override
            public void onSuccess(String result) {
                if (currentGen != searchGeneration && !activeQuery.equalsIgnoreCase(currentInputText)) return;
                Log.d(TAG, "YouTube search returned: " + result);
                List<SearchResult> parsed = parseSearchResults(result, "youtube");
                mainHandler.post(() -> {
                    if (currentGen != searchGeneration && !activeQuery.equalsIgnoreCase(currentInputText)) return;
                    isYouTubeSearchPending = false;
                    currentYouTubeResults.clear();
                    if (parsed != null) currentYouTubeResults.addAll(parsed);
                    notifyCombinedSearchResults(callback, isSearchPending());
                });
            }

            @Override
            public void onError(Exception e) {
                Log.w(TAG, "YouTube search request failed: " + e.getMessage());
                mainHandler.post(() -> {
                    isYouTubeSearchPending = false;
                    notifyCombinedSearchResults(callback, isSearchPending());
                });
            }
        });

        // 3. Unified Source
        Map<String, String> unifiedParams = new HashMap<>();
        unifiedParams.put("q", activeQuery);
        unifiedParams.put("query", activeQuery);

        executeSearchWithFallback(httpHandler, baseUrl, unifiedParams, new HttpHandler.MusicDataCallback() {
            @Override
            public void onSuccess(String result) {
                if (currentGen != searchGeneration && !activeQuery.equalsIgnoreCase(currentInputText)) return;
                Log.d(TAG, "Unified search returned: " + result);
                List<SearchResult> parsed = parseSearchResults(result, "Server");
                mainHandler.post(() -> {
                    if (currentGen != searchGeneration && !activeQuery.equalsIgnoreCase(currentInputText)) return;
                    isUnifiedSearchPending = false;
                    currentUnifiedResults.clear();
                    if (parsed != null) currentUnifiedResults.addAll(parsed);
                    notifyCombinedSearchResults(callback, isSearchPending());
                });
            }

            @Override
            public void onError(Exception e) {
                Log.d(TAG, "Unified search request failed or unsupported: " + e.getMessage());
                mainHandler.post(() -> {
                    isUnifiedSearchPending = false;
                    notifyCombinedSearchResults(callback, isSearchPending());
                });
            }
        });
    }

    private void notifyCombinedSearchResults(SearchCallback callback, boolean isPending) {
        notifyCombinedSearchResults(callback, isPending, false);
    }

    private void notifyCombinedSearchResults(SearchCallback callback, boolean isPending, boolean isOffline) {
        if (callback == null) return;
        List<SearchResult> combined = getCombinedResults();
        callback.onSearchResultsUpdated(combined, isPending, isOffline);
    }

    public List<SearchResult> getCombinedResults() {
        List<SearchResult> combined = new ArrayList<>();
        Set<String> seenIdentifiers = new HashSet<>();

        // 1. Add Local library matches first
        for (SearchResult sr : currentLocalResults) {
            String key = getDeduplicationKey(sr);
            if (!seenIdentifiers.contains(key)) {
                seenIdentifiers.add(key);
                combined.add(sr);
            }
        }

        // 2. Add Telegram results
        for (SearchResult sr : currentTelegramResults) {
            String key = getDeduplicationKey(sr);
            if (!seenIdentifiers.contains(key)) {
                seenIdentifiers.add(key);
                combined.add(sr);
            }
        }

        // 3. Add YouTube results
        for (SearchResult sr : currentYouTubeResults) {
            String key = getDeduplicationKey(sr);
            if (!seenIdentifiers.contains(key)) {
                seenIdentifiers.add(key);
                combined.add(sr);
            }
        }

        // 4. Add Unified server results
        for (SearchResult sr : currentUnifiedResults) {
            String key = getDeduplicationKey(sr);
            if (!seenIdentifiers.contains(key)) {
                seenIdentifiers.add(key);
                combined.add(sr);
            }
        }

        return combined;
    }

    private String getDeduplicationKey(SearchResult sr) {
        if (sr == null) return "";
        if (sr.getId() != null && !sr.getId().trim().isEmpty()) {
            return (sr.getSource() != null ? sr.getSource() : "") + ":" + sr.getId().trim().toLowerCase();
        }
        if (sr.getUrl() != null && !sr.getUrl().trim().isEmpty()) {
            return sr.getUrl().trim().toLowerCase();
        }
        String title = sr.getTitle() != null ? sr.getTitle().trim().toLowerCase() : "";
        String artist = sr.getArtist() != null ? sr.getArtist().trim().toLowerCase() : "";
        return title + "::" + artist;
    }

    private void executeSearchWithFallback(HttpHandler httpHandler, String baseUrl, Map<String, String> params, HttpHandler.MusicDataCallback callback) {
        String primaryUrl = baseUrl + "/search";
        httpHandler.makeHttpGetRequest(primaryUrl, params, new HttpHandler.MusicDataCallback() {
            @Override
            public void onSuccess(String result) {
                callback.onSuccess(result);
            }

            @Override
            public void onError(Exception e) {
                String fallbackUrl = baseUrl + "/api/search";
                httpHandler.makeHttpGetRequest(fallbackUrl, params, new HttpHandler.MusicDataCallback() {
                    @Override
                    public void onSuccess(String result) {
                        callback.onSuccess(result);
                    }

                    @Override
                    public void onError(Exception fallbackEx) {
                        callback.onError(fallbackEx);
                    }
                });
            }
        });
    }

    public List<SearchResult> parseSearchResults(String result, String defaultSource) {
        List<SearchResult> searchResults = new ArrayList<>();
        if (result == null || result.trim().isEmpty()) {
            return searchResults;
        }

        try {
            String jsonText = result.trim();
            if (jsonText.startsWith("\uFEFF")) {
                jsonText = jsonText.substring(1).trim();
            }

            JsonElement rootElement = JsonParser.parseString(jsonText);
            JsonArray array = extractJsonArray(rootElement);

            if (array != null) {
                for (JsonElement el : array) {
                    if (!el.isJsonObject()) continue;
                    JsonObject obj = el.getAsJsonObject();

                    SearchResult item = new SearchResult();

                    // ID
                    if (obj.has("id") && !obj.get("id").isJsonNull()) {
                        JsonElement idEl = obj.get("id");
                        if (idEl.isJsonPrimitive()) {
                            item.setId(idEl.getAsString());
                        } else if (idEl.isJsonObject()) {
                            JsonObject idObj = idEl.getAsJsonObject();
                            if (idObj.has("videoId") && !idObj.get("videoId").isJsonNull()) {
                                item.setId(idObj.get("videoId").getAsString());
                            } else if (idObj.has("id") && !idObj.get("id").isJsonNull()) {
                                item.setId(idObj.get("id").getAsString());
                            }
                        }
                    } else if (obj.has("videoId") && !obj.get("videoId").isJsonNull()) {
                        item.setId(obj.get("videoId").getAsString());
                    } else if (obj.has("video_id") && !obj.get("video_id").isJsonNull()) {
                        item.setId(obj.get("video_id").getAsString());
                    } else if (obj.has("song_id") && !obj.get("song_id").isJsonNull()) {
                        item.setId(obj.get("song_id").getAsString());
                    } else if (obj.has("track_id") && !obj.get("track_id").isJsonNull()) {
                        item.setId(obj.get("track_id").getAsString());
                    } else if (obj.has("file_id") && !obj.get("file_id").isJsonNull()) {
                        item.setId(obj.get("file_id").getAsString());
                    }

                    // Snippet
                    JsonObject snippet = obj.has("snippet") && obj.get("snippet").isJsonObject() ? obj.getAsJsonObject("snippet") : null;

                    // Title
                    if (obj.has("title") && !obj.get("title").isJsonNull()) {
                        item.setTitle(obj.get("title").getAsString());
                    } else if (snippet != null && snippet.has("title") && !snippet.get("title").isJsonNull()) {
                        item.setTitle(snippet.get("title").getAsString());
                    } else if (obj.has("name") && !obj.get("name").isJsonNull()) {
                        item.setTitle(obj.get("name").getAsString());
                    } else if (obj.has("song_name") && !obj.get("song_name").isJsonNull()) {
                        item.setTitle(obj.get("song_name").getAsString());
                    } else if (obj.has("track_name") && !obj.get("track_name").isJsonNull()) {
                        item.setTitle(obj.get("track_name").getAsString());
                    } else if (obj.has("file_name") && !obj.get("file_name").isJsonNull()) {
                        item.setTitle(obj.get("file_name").getAsString());
                    }

                    // Artist
                    if (obj.has("artist") && !obj.get("artist").isJsonNull()) {
                        item.setArtist(obj.get("artist").getAsString());
                    } else if (obj.has("channelTitle") && !obj.get("channelTitle").isJsonNull()) {
                        item.setArtist(obj.get("channelTitle").getAsString());
                    } else if (snippet != null && snippet.has("channelTitle") && !snippet.get("channelTitle").isJsonNull()) {
                        item.setArtist(snippet.get("channelTitle").getAsString());
                    } else if (obj.has("uploader") && !obj.get("uploader").isJsonNull()) {
                        item.setArtist(obj.get("uploader").getAsString());
                    } else if (obj.has("author") && !obj.get("author").isJsonNull()) {
                        item.setArtist(obj.get("author").getAsString());
                    } else {
                        item.setArtist(defaultSource);
                    }

                    // Album
                    if (obj.has("album") && !obj.get("album").isJsonNull()) {
                        item.setAlbum(obj.get("album").getAsString());
                    }

                    // URL / Audio Path
                    if (obj.has("url") && !obj.get("url").isJsonNull()) {
                        item.setUrl(obj.get("url").getAsString());
                    } else if (obj.has("stream_url") && !obj.get("stream_url").isJsonNull()) {
                        item.setUrl(obj.get("stream_url").getAsString());
                    } else if (obj.has("audio_path") && !obj.get("audio_path").isJsonNull()) {
                        item.setUrl(obj.get("audio_path").getAsString());
                    } else if (obj.has("audio_url") && !obj.get("audio_url").isJsonNull()) {
                        item.setUrl(obj.get("audio_url").getAsString());
                    }

                    // Fallback ID
                    if (item.getId() == null || item.getId().trim().isEmpty()) {
                        if (item.getUrl() != null && !item.getUrl().trim().isEmpty()) {
                            item.setId(item.getUrl());
                        } else if (item.getTitle() != null) {
                            item.setId(item.getTitle());
                        }
                    }

                    // Thumbnail / Cover image
                    if (obj.has("img_url") && !obj.get("img_url").isJsonNull()) {
                        item.setImgUrl(obj.get("img_url").getAsString());
                    } else if (obj.has("thumbnail") && !obj.get("thumbnail").isJsonNull()) {
                        item.setImgUrl(obj.get("thumbnail").getAsString());
                    } else if (obj.has("cover_path") && !obj.get("cover_path").isJsonNull()) {
                        item.setImgUrl(obj.get("cover_path").getAsString());
                    } else if (snippet != null && snippet.has("thumbnails") && snippet.get("thumbnails").isJsonObject()) {
                        JsonObject thumbs = snippet.getAsJsonObject("thumbnails");
                        if (thumbs.has("high") && thumbs.getAsJsonObject("high").has("url")) {
                            item.setImgUrl(thumbs.getAsJsonObject("high").get("url").getAsString());
                        } else if (thumbs.has("medium") && thumbs.getAsJsonObject("medium").has("url")) {
                            item.setImgUrl(thumbs.getAsJsonObject("medium").get("url").getAsString());
                        } else if (thumbs.has("default") && thumbs.getAsJsonObject("default").has("url")) {
                            item.setImgUrl(thumbs.getAsJsonObject("default").get("url").getAsString());
                        }
                    }

                    // Source
                    if (obj.has("source") && !obj.get("source").isJsonNull()) {
                        item.setSource(obj.get("source").getAsString());
                    } else {
                        item.setSource(defaultSource);
                    }

                    // Quality
                    if (obj.has("quality") && !obj.get("quality").isJsonNull()) {
                        item.setQuality(obj.get("quality").getAsString());
                    } else if (obj.has("audio_quality") && !obj.get("audio_quality").isJsonNull()) {
                        item.setQuality(obj.get("audio_quality").getAsString());
                    }

                    // Size
                    if (obj.has("size") && !obj.get("size").isJsonNull()) {
                        JsonElement sEl = obj.get("size");
                        if (sEl.isJsonPrimitive()) {
                            try {
                                item.setSize(sEl.getAsLong());
                            } catch (Exception ignored) {}
                        }
                    }
                    if (obj.has("size_str") && !obj.get("size_str").isJsonNull()) {
                        item.setFormattedSize(obj.get("size_str").getAsString());
                    }

                    // File name
                    if (obj.has("file_name") && !obj.get("file_name").isJsonNull()) {
                        item.setFileName(obj.get("file_name").getAsString());
                    }

                    if (item.getTitle() != null && !item.getTitle().trim().isEmpty()) {
                        searchResults.add(item);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing " + defaultSource + " search response: " + result, e);
        }
        return searchResults;
    }

    private JsonArray extractJsonArray(JsonElement element) {
        if (element == null) return null;
        if (element.isJsonArray()) return element.getAsJsonArray();
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            String[] commonKeys = {"results", "items", "songs", "tracks", "data", "result", "list", "search_results", "searchResults"};
            for (String key : commonKeys) {
                if (obj.has(key)) {
                    JsonElement sub = obj.get(key);
                    if (sub.isJsonArray()) return sub.getAsJsonArray();
                    if (sub.isJsonObject()) {
                        JsonArray nested = extractJsonArray(sub);
                        if (nested != null) return nested;
                    }
                }
            }
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                if (entry.getValue().isJsonArray()) {
                    return entry.getValue().getAsJsonArray();
                } else if (entry.getValue().isJsonObject()) {
                    JsonArray nested = extractJsonArray(entry.getValue());
                    if (nested != null) return nested;
                }
            }
        }
        return null;
    }
}
