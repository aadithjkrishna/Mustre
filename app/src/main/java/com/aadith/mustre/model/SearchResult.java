package com.aadith.mustre.model;

import com.google.gson.annotations.SerializedName;

public class SearchResult {

    @SerializedName(value = "id", alternate = {"video_id", "videoId", "song_id"})
    private String id;

    @SerializedName(value = "title", alternate = {"name"})
    private String title;

    @SerializedName(value = "artist", alternate = {"channel", "channelTitle", "uploader", "author"})
    private String artist;

    @SerializedName(value = "album")
    private String album;

    @SerializedName(value = "img_url", alternate = {"thumbnail", "thumbnail_url", "cover_path", "cover_url", "imgUrl"})
    private String imgUrl;

    @SerializedName(value = "source")
    private String source;

    @SerializedName(value = "downloaded")
    private boolean downloaded;

    @SerializedName(value = "url", alternate = {"stream_url", "audio_path", "audio_url"})
    private String url;

    @SerializedName(value = "duration")
    private long duration;

    @SerializedName(value = "quality", alternate = {"audio_quality", "audioQuality", "bitrate", "format", "codec", "audio_format"})
    private String quality;

    @SerializedName(value = "size", alternate = {"file_size", "fileSize", "filesize", "size_bytes", "bytes", "length"})
    private com.google.gson.JsonElement sizeElement;

    @SerializedName(value = "size_str", alternate = {"size_formatted", "formatted_size", "human_size", "sizeText", "size_text"})
    private String sizeStr;

    private transient long size;

    @SerializedName(value = "file_name", alternate = {"fileName"})
    private String fileName;

    private transient boolean isLoading;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getImgUrl() {
        return imgUrl;
    }

    public void setImgUrl(String imgUrl) {
        this.imgUrl = imgUrl;
    }

    public String getThumbnail() {
        return imgUrl;
    }

    public void setThumbnail(String thumbnail) {
        this.imgUrl = thumbnail;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean isDownloaded() {
        return downloaded;
    }

    public void setDownloaded(boolean downloaded) {
        this.downloaded = downloaded;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getQuality() {
        if (quality != null && !quality.trim().isEmpty()) {
            return quality;
        }
        String fn = fileName != null ? fileName.toLowerCase() : "";
        String t = title != null ? title.toLowerCase() : "";
        String u = url != null ? url.toLowerCase() : "";
        if (fn.endsWith(".flac") || t.contains("flac") || u.endsWith(".flac") || u.contains(".flac")) {
            return "FLAC (Lossless)";
        }
        if (fn.endsWith(".opus") || t.contains("opus") || u.endsWith(".opus")) {
            return "Opus (High Quality)";
        }
        if (fn.endsWith(".mp3") || u.endsWith(".mp3")) {
            return "MP3 (320 kbps)";
        }
        if ("telegram".equalsIgnoreCase(source)) {
            return "FLAC (Lossless)";
        }
        if ("youtube".equalsIgnoreCase(source)) {
            return "AAC • 256 kbps";
        }
        return quality;
    }

    public void setQuality(String quality) {
        this.quality = quality;
    }

    public long getSize() {
        if (sizeElement != null && !sizeElement.isJsonNull() && sizeElement.isJsonPrimitive()) {
            try {
                return sizeElement.getAsLong();
            } catch (Exception ignored) {}
        }
        return size;
    }

    public void setSize(long size) {
        this.size = size;
        this.sizeElement = new com.google.gson.JsonPrimitive(size);
    }

    public String getSizeStr() {
        return getFormattedSize();
    }

    public void setSizeStr(String sizeStr) {
        this.sizeStr = sizeStr;
    }

    public void setFormattedSize(String formattedSize) {
        this.sizeStr = formattedSize;
    }

    public String getFormattedSize() {
        if (sizeStr != null && !sizeStr.trim().isEmpty()) {
            return sizeStr.trim();
        }
        if (sizeElement != null && !sizeElement.isJsonNull()) {
            try {
                if (sizeElement.isJsonPrimitive()) {
                    com.google.gson.JsonPrimitive primitive = sizeElement.getAsJsonPrimitive();
                    if (primitive.isNumber()) {
                        long bytes = primitive.getAsLong();
                        return formatBytes(bytes);
                    } else if (primitive.isString()) {
                        String str = primitive.getAsString().trim();
                        if (str.matches("^\\d+$")) {
                            return formatBytes(Long.parseLong(str));
                        }
                        return str;
                    }
                }
            } catch (Exception ignored) {}
        }
        if (size > 0) {
            return formatBytes(size);
        }
        return null;
    }

    public static String formatBytes(long bytes) {
        if (bytes <= 0) return null;
        if (bytes >= 1024L * 1024L * 1024L) {
            return String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        } else if (bytes >= 1024L * 1024L) {
            return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        } else if (bytes >= 1024L) {
            return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        } else {
            return bytes + " B";
        }
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public boolean isLoading() {
        return isLoading;
    }

    public void setLoading(boolean loading) {
        isLoading = loading;
    }
}
