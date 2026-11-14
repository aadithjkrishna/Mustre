package com.aadith.mustre.model;

import com.google.gson.annotations.SerializedName;

public class Song {

    @SerializedName(value = "title", alternate = {"name"})
    private String title;

    @SerializedName(value = "artist", alternate = {"channel", "channelTitle", "uploader", "author"})
    private String artist;

    private String album;

    @SerializedName(value = "audio_path", alternate = {"audioPath", "url", "stream_url", "streamUrl", "audio_url", "audioUrl", "path", "file", "src", "stream"})
    private String audioPath;

    @SerializedName(value = "cover_path", alternate = {"coverPath", "img_url", "imgUrl", "thumbnail", "thumbnail_url", "thumbnailUrl", "cover", "cover_url", "image"})
    private String coverPath;

    @SerializedName(value = "quality", alternate = {"audio_quality", "audioQuality", "bitrate", "format", "codec", "audio_format"})
    private String quality;

    @SerializedName(value = "size", alternate = {"file_size", "fileSize", "filesize", "size_bytes", "bytes", "length"})
    private com.google.gson.JsonElement sizeElement;

    @SerializedName(value = "size_str", alternate = {"size_formatted", "formatted_size", "human_size", "sizeText", "size_text"})
    private String sizeStr;

    private transient long size;

    public Song() {}

    public Song(String title, String artist, String album, String audioPath, String coverPath) {
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.audioPath = audioPath;
        this.coverPath = coverPath;
    }

    public Song(String title, String artist, String album, String audioPath, String coverPath, String quality) {
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.audioPath = audioPath;
        this.coverPath = coverPath;
        this.quality = quality;
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

    public String getAudioPath() {
        return audioPath;
    }

    public void setAudioPath(String audioPath) {
        this.audioPath = audioPath;
    }

    public String getCoverPath() {
        return coverPath;
    }

    public void setCoverPath(String coverPath) {
        this.coverPath = coverPath;
    }

    public String getQuality() {
        if (quality != null && !quality.trim().isEmpty()) {
            return quality;
        }
        String path = audioPath != null ? audioPath.toLowerCase() : "";
        if (path.endsWith(".flac") || path.endsWith(".alac") || path.endsWith(".wav") || path.contains("telegram")) {
            return "FLAC (Lossless)";
        }
        if (path.endsWith(".opus")) {
            return "Opus (High Quality)";
        }
        if (path.endsWith(".mp3") || path.endsWith(".aac") || path.endsWith(".m4a")) {
            return "AAC Audio";
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
                        return SearchResult.formatBytes(bytes);
                    } else if (primitive.isString()) {
                        String str = primitive.getAsString().trim();
                        if (str.matches("^\\d+$")) {
                            return SearchResult.formatBytes(Long.parseLong(str));
                        }
                        return str;
                    }
                }
            } catch (Exception ignored) {}
        }
        if (size > 0) {
            return SearchResult.formatBytes(size);
        }
        return null;
    }

    @Override
    public String toString() {
        return "Song{" +
                "title='" + title + '\'' +
                ", artist='" + artist + '\'' +
                ", album='" + album + '\'' +
                ", audioPath='" + audioPath + '\'' +
                ", coverPath='" + coverPath + '\'' +
                ", quality='" + quality + '\'' +
                '}';
    }
}
