package com.aadith.mustre.model;

import com.google.gson.annotations.SerializedName;

public class DownloadResponse {
    private boolean success;

    @SerializedName("song")
    private Song song;

    public boolean isSuccess() {
        return success;
    }

    public Song getSong() {
        return song;
    }
}
