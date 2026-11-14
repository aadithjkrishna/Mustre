package com.aadith.mustre.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aadith.mustre.R;
import com.aadith.mustre.model.SearchResult;
import com.aadith.mustre.network.ServerPrefs;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.ArrayList;
import java.util.List;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.SearchResultViewHolder> {

    public interface OnSearchResultClickListener {
        void onSearchResultClick(SearchResult result, int position);
        void onSearchResultPlayClick(SearchResult result, int position);
    }

    private List<SearchResult> results = new ArrayList<>();
    private final OnSearchResultClickListener resultClickListener;

    public SearchResultAdapter(OnSearchResultClickListener listener) {
        this.resultClickListener = listener;
        setHasStableIds(true);
    }

    public void setResults(List<SearchResult> results) {
        this.results = results != null ? new ArrayList<>(results) : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SearchResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_result, parent, false);
        return new SearchResultViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SearchResultViewHolder holder, int position) {
        SearchResult result = results.get(position);
        holder.bind(result, resultClickListener);
    }

    @Override
    public long getItemId(int position) {
        if (position >= 0 && position < results.size()) {
            SearchResult r = results.get(position);
            if (r != null) {
                String idStr = r.getUrl() != null ? r.getUrl() : r.getTitle();
                if (idStr != null) return idStr.hashCode();
            }
        }
        return position;
    }

    @Override
    public int getItemCount() {
        return results.size();
    }

    static class SearchResultViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivThumbnail;
        private final TextView tvTitle;
        private final TextView tvArtist;
        private final TextView tvSize;
        private final ProgressBar pbLoading;
        private final ImageView ivPlayIcon;

        public SearchResultViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.iv_search_thumbnail);
            tvTitle = itemView.findViewById(R.id.tv_search_title);
            tvArtist = itemView.findViewById(R.id.tv_search_artist);
            tvSize = itemView.findViewById(R.id.tv_search_size);
            pbLoading = itemView.findViewById(R.id.pb_search_loading);
            ivPlayIcon = itemView.findViewById(R.id.iv_search_play_icon);
        }

        public void bind(SearchResult result, OnSearchResultClickListener listener) {
            if (result == null) return;

            String title = result.getTitle() != null && !result.getTitle().isEmpty() ? result.getTitle() : "Track";
            tvTitle.setText(title);

            String formattedSize = result.getFormattedSize();
            if (tvSize != null) {
                if (formattedSize != null && !formattedSize.trim().isEmpty()) {
                    tvSize.setText(formattedSize.trim());
                    tvSize.setVisibility(View.VISIBLE);
                } else {
                    tvSize.setVisibility(View.GONE);
                }
            }

            StringBuilder subtitle = new StringBuilder();
            if (result.getArtist() != null && !result.getArtist().trim().isEmpty()) {
                subtitle.append(result.getArtist().trim());
            }

            if (result.getQuality() != null && !result.getQuality().trim().isEmpty()) {
                if (subtitle.length() > 0) subtitle.append(" • ");
                subtitle.append(result.getQuality().trim());
            }

            if (formattedSize != null && !formattedSize.trim().isEmpty()) {
                if (subtitle.length() > 0) subtitle.append(" • ");
                subtitle.append(formattedSize.trim());
            }

            String source = result.getSource();
            if (source != null && !source.trim().isEmpty()) {
                if (subtitle.length() > 0) subtitle.append(" • ");
                if ("telegram".equalsIgnoreCase(source)) {
                    subtitle.append("Telegram");
                } else if ("youtube".equalsIgnoreCase(source)) {
                    subtitle.append("YouTube");
                } else {
                    subtitle.append(source);
                }
            } else {
                if (subtitle.length() == 0) {
                    subtitle.append("Track");
                }
            }

            if (result.isDownloaded()) {
                subtitle.append(" • Saved");
            }

            tvArtist.setText(subtitle.toString());

            String imgUrl = result.getImgUrl();
            if (imgUrl != null && !imgUrl.trim().isEmpty()) {
                String fullUrl = ServerPrefs.formatCoverUrl(ServerPrefs.getServerUrl(itemView.getContext()), imgUrl.trim());

                boolean isOffline = ServerPrefs.isOffline(itemView.getContext());
                if (isOffline && (fullUrl != null && (fullUrl.startsWith("http://") || fullUrl.startsWith("https://")))) {
                    Glide.with(itemView.getContext())
                            .load(fullUrl)
                            .onlyRetrieveFromCache(true)
                            .placeholder(R.drawable.mustre)
                            .error(R.drawable.mustre)
                            .into(ivThumbnail);
                } else {
                    Glide.with(itemView.getContext())
                            .load(fullUrl)
                            .placeholder(R.drawable.mustre)
                            .error(R.drawable.mustre)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(ivThumbnail);
                }
            } else {
                ivThumbnail.setImageResource(R.drawable.mustre);
            }

            if (result.isLoading()) {
                pbLoading.setVisibility(View.VISIBLE);
                ivPlayIcon.setVisibility(View.GONE);
            } else {
                pbLoading.setVisibility(View.GONE);
                ivPlayIcon.setVisibility(View.VISIBLE);
            }

            if (ivPlayIcon != null) {
                ivPlayIcon.setOnClickListener(v -> {
                    int adapterPos = getAdapterPosition();
                    if (listener != null && adapterPos != RecyclerView.NO_POSITION) {
                        listener.onSearchResultPlayClick(result, adapterPos);
                    }
                });
            }

            itemView.setOnClickListener(v -> {
                int adapterPos = getAdapterPosition();
                if (listener != null && adapterPos != RecyclerView.NO_POSITION) {
                    listener.onSearchResultClick(result, adapterPos);
                }
            });
        }
    }
}
