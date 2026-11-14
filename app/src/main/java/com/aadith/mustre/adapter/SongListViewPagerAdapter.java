package com.aadith.mustre.adapter;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class SongListViewPagerAdapter extends RecyclerView.Adapter<SongListViewPagerAdapter.PageViewHolder> {

    private final SongAdapter songAdapter;
    private final SongAdapter queueAdapter;
    private final ItemTouchHelper queueItemTouchHelper;

    public SongListViewPagerAdapter(SongAdapter songAdapter, SongAdapter queueAdapter, ItemTouchHelper queueItemTouchHelper) {
        this.songAdapter = songAdapter;
        this.queueAdapter = queueAdapter;
        this.queueItemTouchHelper = queueItemTouchHelper;
    }

    @Override
    public int getItemCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        RecyclerView recyclerView = new RecyclerView(parent.getContext());
        recyclerView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        recyclerView.setLayoutManager(new LinearLayoutManager(parent.getContext()));
        recyclerView.setClipToPadding(false);
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        switch (viewType) {
            case 0:
                recyclerView.setAdapter(songAdapter);
                break;
            case 1:
            default:
                recyclerView.setAdapter(queueAdapter);
                if (queueItemTouchHelper != null) {
                    queueItemTouchHelper.attachToRecyclerView(recyclerView);
                }
                break;
        }
        return new PageViewHolder(recyclerView);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {}

    public static class PageViewHolder extends RecyclerView.ViewHolder {
        public PageViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
