package com.aadith.mustre.model;

public class LyricWord {

    private String text;
    private long startMs;
    private long endMs;
    private int charStart;
    private int charEnd;

    public LyricWord(String text, long startMs, long endMs) {
        this(text, startMs, endMs, 0, 0);
    }

    public LyricWord(String text, long startMs, long endMs, int charStart, int charEnd) {
        this.text = text != null ? text : "";
        this.startMs = startMs;
        this.endMs = endMs;
        this.charStart = charStart;
        this.charEnd = charEnd;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text != null ? text : "";
    }

    public long getStartMs() {
        return startMs;
    }

    public void setStartMs(long startMs) {
        this.startMs = startMs;
    }

    public long getEndMs() {
        return endMs;
    }

    public void setEndMs(long endMs) {
        this.endMs = endMs;
    }

    public int getCharStart() {
        return charStart;
    }

    public void setCharStart(int charStart) {
        this.charStart = charStart;
    }

    public int getCharEnd() {
        return charEnd;
    }

    public void setCharEnd(int charEnd) {
        this.charEnd = charEnd;
    }

    @Override
    public String toString() {
        return text + " [" + startMs + "-" + endMs + "ms]";
    }
}
