package com.aadith.mustre.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aadith.mustre.R;
import com.aadith.mustre.model.Song;
import com.aadith.mustre.network.ServerPrefs;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder> {

    public interface OnSongClickListener {
        void onSongClick(Song song, int position);
        void onSongPlayClick(Song song, int position);
        default void onItemsSwapped(List<Song> newOrder) {}
    }

    public interface OnStartDragListener {
        void onStartDrag(RecyclerView.ViewHolder viewHolder);
    }

    private List<Song> songList = new ArrayList<>();
    private final OnSongClickListener listener;
    private OnStartDragListener dragListener;
    private boolean isQueueMode = false;
    private int selectedPosition = -1;
    private String selectedAudioPath = null;

    public SongAdapter(OnSongClickListener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setQueueMode(boolean isQueueMode) {
        this.isQueueMode = isQueueMode;
        notifyDataSetChanged();
    }

    public void setOnStartDragListener(OnStartDragListener dragListener) {
        this.dragListener = dragListener;
    }

    public List<Song> getSongs() {
        return new ArrayList<>(songList);
    }

    public void setSongs(List<Song> songs) {
        this.songList = songs != null ? new ArrayList<>(songs) : new ArrayList<>();
        updateSelectedPosition();
        notifyDataSetChanged();
    }

    public void removeItem(int position) {
        if (position >= 0 && position < songList.size()) {
            songList.remove(position);
            notifyItemRemoved(position);
            updateSelectedPosition();
        }
    }

    public void swapItems(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                java.util.Collections.swap(songList, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                java.util.Collections.swap(songList, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
        if (listener != null) {
            listener.onItemsSwapped(songList);
        }
    }

    public void setSelectedSong(Song song) {
        if (song == null) {
            this.selectedAudioPath = null;
            setSelectedPosition(-1);
            return;
        }
        this.selectedAudioPath = song.getAudioPath();
        updateSelectedPosition();
    }

    public void setSelectedPosition(int position) {
        int previousSelected = this.selectedPosition;
        this.selectedPosition = position;

        if (previousSelected >= 0 && previousSelected < getItemCount()) {
            notifyItemChanged(previousSelected);
        }
        if (selectedPosition >= 0 && selectedPosition < getItemCount()) {
            notifyItemChanged(selectedPosition);
        }
    }

    private void updateSelectedPosition() {
        if (selectedAudioPath == null) return;
        for (int i = 0; i < songList.size(); i++) {
            Song s = songList.get(i);
            if (s != null && s.getAudioPath() != null && s.getAudioPath().equals(selectedAudioPath)) {
                int oldPos = selectedPosition;
                selectedPosition = i;
                if (oldPos != -1 && oldPos < getItemCount()) {
                    notifyItemChanged(oldPos);
                }
                notifyItemChanged(selectedPosition);
                return;
            }
        }
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_song, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        Song song = songList.get(position);
        boolean isSelected = (position == selectedPosition) ||
                (song != null && song.getAudioPath() != null && song.getAudioPath().equals(selectedAudioPath));
        holder.bind(song, listener, dragListener, position, isSelected, isQueueMode);
    }

    @Override
    public long getItemId(int position) {
        if (position >= 0 && position < songList.size()) {
            Song song = songList.get(position);
            if (song != null && song.getAudioPath() != null) {
                return song.getAudioPath().hashCode();
            }
        }
        return position;
    }

    @Override
    public int getItemCount() {
        return songList.size();
    }

    static class SongViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivSongIcon;
        private final TextView tvTitle;
        private final TextView tvArtist;
        private final TextView tvHq;
        private final ImageView ivPlayIcon;

        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            ivSongIcon = itemView.findViewById(R.id.iv_song_icon);
            tvTitle = itemView.findViewById(R.id.tv_item_title);
            tvArtist = itemView.findViewById(R.id.tv_item_artist);
            tvHq = itemView.findViewById(R.id.tv_item_hq);
            ivPlayIcon = itemView.findViewById(R.id.iv_song_play_icon);
        }

        public void bind(Song song, OnSongClickListener listener, OnStartDragListener dragListener, int position, boolean isSelected, boolean isQueueMode) {
            String title = song.getTitle() != null && !song.getTitle().isEmpty() ? song.getTitle() : "Unknown Title";
            String artist = song.getArtist() != null && !song.getArtist().isEmpty() ? song.getArtist() : "Unknown Artist";

            tvTitle.setText(title);
            tvArtist.setText(artist);

            // Highlight currently playing/selected item
            if (isSelected) {
                itemView.setBackgroundResource(R.drawable.bg_item_selected);
            } else {
                itemView.setBackgroundResource(R.drawable.bg_item_ripple);
            }

            // Load Cover Image thumbnail with disk caching strategy for offline display
            String baseUrl = ServerPrefs.getServerUrl(itemView.getContext());
            String coverUrl = ServerPrefs.formatCoverUrl(baseUrl, song.getCoverPath());

            boolean isOffline = ServerPrefs.isOffline(itemView.getContext());
            if (isOffline && (coverUrl != null && (coverUrl.startsWith("http://") || coverUrl.startsWith("https://")))) {
                Glide.with(itemView.getContext())
                        .load(coverUrl)
                        .onlyRetrieveFromCache(true)
                        .placeholder(R.drawable.mustre)
                        .error(R.drawable.mustre)
                        .centerCrop()
                        .into(ivSongIcon);
            } else {
                Glide.with(itemView.getContext())
                        .load(coverUrl)
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                        .placeholder(R.drawable.mustre)
                        .error(R.drawable.mustre)
                        .centerCrop()
                        .into(ivSongIcon);
            }

            String quality = song != null ? song.getQuality() : null;
            if (quality == null || quality.trim().isEmpty()) {
                quality = ServerPrefs.getCachedQualityText(itemView.getContext(), song != null ? song.getAudioPath() : null);
            }
            if (quality != null && !quality.trim().isEmpty()) {
                if (quality.contains("FLAC") || quality.contains("Lossless")) {
                    tvHq.setText("FLAC");
                } else if (quality.contains("Opus") || quality.contains("High Quality")) {
                    tvHq.setText("HQ");
                } else {
                    tvHq.setText("AAC");
                }
                tvHq.setVisibility(View.VISIBLE);
            } else {
                String path = (song != null && song.getAudioPath() != null) ? song.getAudioPath().toLowerCase() : "";
                if (path.endsWith(".flac") || path.endsWith(".alac") || path.endsWith(".wav") || path.contains("telegram")) {
                    tvHq.setText("FLAC");
                    tvHq.setVisibility(View.VISIBLE);
                } else if (path.endsWith(".opus")) {
                    tvHq.setText("HQ");
                    tvHq.setVisibility(View.VISIBLE);
                } else if (path.endsWith(".mp3") || path.endsWith(".aac") || path.endsWith(".m4a")) {
                    tvHq.setText("AAC");
                    tvHq.setVisibility(View.VISIBLE);
                } else {
                    tvHq.setVisibility(View.GONE);
                }
            }

            if (ivPlayIcon != null) {
                if (isQueueMode) {
                    ivPlayIcon.setImageResource(R.drawable.ic_drag_handle);
                    ivPlayIcon.setOnClickListener(null);
                    ivPlayIcon.setOnTouchListener((v, event) -> {
                        if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                            if (dragListener != null) {
                                dragListener.onStartDrag(SongViewHolder.this);
                            }
                        }
                        return false;
                    });
                } else {
                    ivPlayIcon.setImageResource(R.drawable.ic_play);
                    ivPlayIcon.setOnTouchListener(null);
                    ivPlayIcon.setOnClickListener(v -> {
                        int adapterPos = getAdapterPosition();
                        if (listener != null && adapterPos != RecyclerView.NO_POSITION) {
                            listener.onSongPlayClick(song, adapterPos);
                        }
                    });
                }
            }

            itemView.setOnClickListener(v -> {
                int adapterPos = getAdapterPosition();
                if (listener != null && adapterPos != RecyclerView.NO_POSITION) {
                    listener.onSongClick(song, adapterPos);
                }
            });
        }
    }
}
