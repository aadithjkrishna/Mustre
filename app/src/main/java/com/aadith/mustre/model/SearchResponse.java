package com.aadith.mustre.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class SearchResponse {
    @SerializedName("results")
    private List<SearchResult> results;

    @SerializedName("songs")
    private List<SearchResult> songs;

    public List<SearchResult> getResults() {
        if (results != null) return results;
        return songs;
    }
}
