package com.aadith.mustre.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Lyrics {

    @SerializedName("id")
    private long id;

    @SerializedName(value = "trackName", alternate = {"name"})
    private String trackName;

    @SerializedName("artistName")
    private String artistName;

    @SerializedName("albumName")
    private String albumName;

    @SerializedName("duration")
    private double duration;

    @SerializedName("instrumental")
    private boolean instrumental;

    @SerializedName("plainLyrics")
    private String plainLyrics;

    @SerializedName("syncedLyrics")
    private String syncedLyrics;

    @SerializedName("hasWordSync")
    private boolean hasWordSync;

    @SerializedName("lyricsfile")
    private String lyricsfile;

    private transient List<LyricLine> parsedLines;

    public Lyrics() {}

    public Lyrics(String plainLyrics, String syncedLyrics, boolean instrumental) {
        this.plainLyrics = plainLyrics;
        this.syncedLyrics = syncedLyrics;
        this.instrumental = instrumental;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTrackName() {
        return trackName;
    }

    public void setTrackName(String trackName) {
        this.trackName = trackName;
    }

    public String getArtistName() {
        return artistName;
    }

    public void setArtistName(String artistName) {
        this.artistName = artistName;
    }

    public String getAlbumName() {
        return albumName;
    }

    public void setAlbumName(String albumName) {
        this.albumName = albumName;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public boolean isInstrumental() {
        return instrumental;
    }

    public void setInstrumental(boolean instrumental) {
        this.instrumental = instrumental;
    }

    public String getPlainLyrics() {
        return plainLyrics;
    }

    public void setPlainLyrics(String plainLyrics) {
        this.plainLyrics = plainLyrics;
    }

    public String getSyncedLyrics() {
        return syncedLyrics;
    }

    public void setSyncedLyrics(String syncedLyrics) {
        this.syncedLyrics = syncedLyrics;
    }

    public boolean hasWordSync() {
        return hasWordSync;
    }

    public void setHasWordSync(boolean hasWordSync) {
        this.hasWordSync = hasWordSync;
    }

    public String getLyricsfile() {
        return lyricsfile;
    }

    public void setLyricsfile(String lyricsfile) {
        this.lyricsfile = lyricsfile;
    }

    public List<LyricLine> getParsedLines() {
        if (parsedLines == null) {
            boolean hasWordsInFile = lyricsfile != null && lyricsfile.contains("words:");
            if ((hasWordSync || hasWordsInFile) && lyricsfile != null && !lyricsfile.trim().isEmpty()) {
                parsedLines = com.aadith.mustre.lyrics.LyricsfileParser.parse(lyricsfile);
            }
            if ((parsedLines == null || parsedLines.isEmpty()) && syncedLyrics != null && !syncedLyrics.trim().isEmpty()) {
                parsedLines = com.aadith.mustre.lyrics.LrcParser.parse(syncedLyrics);
            }
            if ((parsedLines == null || parsedLines.isEmpty()) && lyricsfile != null && !lyricsfile.trim().isEmpty()) {
                parsedLines = com.aadith.mustre.lyrics.LyricsfileParser.parse(lyricsfile);
            }
        }
        return parsedLines;
    }

    public void setParsedLines(List<LyricLine> parsedLines) {
        this.parsedLines = parsedLines;
    }

    public boolean hasSyncedLyrics() {
        List<LyricLine> lines = getParsedLines();
        return lines != null && !lines.isEmpty();
    }
}
