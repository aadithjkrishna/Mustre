package com.aadith.mustre.model;

import androidx.annotation.NonNull;

import java.util.List;

public class LyricLine implements Comparable<LyricLine> {

    private long timeMs;
    private long endTimeMs;
    private String text;
    private List<LyricWord> words;

    public LyricLine(long timeMs, String text) {
        this(timeMs, 0, text, null);
    }

    public LyricLine(long timeMs, long endTimeMs, String text, List<LyricWord> words) {
        this.timeMs = timeMs;
        this.endTimeMs = endTimeMs;
        this.text = text != null ? text.trim() : "";
        this.words = words;
    }

    public long getTimeMs() {
        return timeMs;
    }

    public void setTimeMs(long timeMs) {
        this.timeMs = timeMs;
    }

    public long getEndTimeMs() {
        return endTimeMs;
    }

    public void setEndTimeMs(long endTimeMs) {
        this.endTimeMs = endTimeMs;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text != null ? text.trim() : "";
    }

    public List<LyricWord> getWords() {
        return words;
    }

    public void setWords(List<LyricWord> words) {
        this.words = words;
    }

    public boolean hasWords() {
        return words != null && !words.isEmpty();
    }

    public boolean isEmpty() {
        return text == null || text.trim().isEmpty();
    }

    @Override
    public int compareTo(@NonNull LyricLine o) {
        return Long.compare(this.timeMs, o.timeMs);
    }

    @NonNull
    @Override
    public String toString() {
        return "[" + timeMs + "ms] " + text;
    }
}
