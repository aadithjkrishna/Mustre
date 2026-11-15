package com.aadith.mustre;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.aadith.mustre.adapter.SearchResultAdapter;
import com.aadith.mustre.adapter.SongAdapter;
import com.aadith.mustre.adapter.SongListViewPagerAdapter;
import com.aadith.mustre.lyrics.LyricsAdapter;
import com.aadith.mustre.lyrics.LyricsRepository;
import com.aadith.mustre.model.LyricLine;
import com.aadith.mustre.model.Lyrics;
import com.aadith.mustre.model.SearchResult;
import com.aadith.mustre.model.Song;
import com.aadith.mustre.network.CatalogRepository;
import com.aadith.mustre.network.HttpHandler;
import com.aadith.mustre.network.ServerPrefs;
import com.aadith.mustre.player.TrackPlaybackManager;
import com.aadith.mustre.search.SearchManager;
import com.aadith.mustre.service.FloatingPlayerService;
import com.aadith.mustre.service.MusicService;
import com.aadith.mustre.ui.OledModeController;
import com.aadith.mustre.ui.PanelAnimationController;
import com.aadith.mustre.ui.QualityMetadataHelper;
import com.aadith.mustre.ui.SongInfoDialogHelper;
import com.aadith.mustre.ui.VisualizerController;
import com.aadith.mustre.util.HapticHelper;
import com.aadith.mustre.util.ImageUtils;
import com.aadith.mustre.util.TimeUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

@UnstableApi
public class MainActivity extends AppCompatActivity implements MusicService.MusicChangeListener {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_LOCAL_NETWORK = 1001;

    private String getBaseUrl() {
        return ServerPrefs.getServerUrl(this);
    }

    private boolean isPortraitMode() {
        return getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT;
    }

    private ImageView coverImageView;
    private ImageView backgroundImageView;
    private TextView tvTitle;
    private TextView tvArtist;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private View badgeAudioQuality;
    private TextView tvQualityText;
    private SeekBar seekbarAudio;
    private ImageButton btnPlayPause;
    private ImageButton btnLyrics;

    private FrameLayout portraitLyricsPanel;
    private RecyclerView rvSyncedLyrics;
    private NestedScrollView scrollPlainLyrics;
    private TextView tvPlainLyrics;
    private View lyricsLoadingContainer;
    private View lyricsInstrumentalContainer;
    private View lyricsEmptyContainer;
    private TextView tvLyricsEmptyTitle;
    private TextView tvLyricsEmptySubtitle;
    private View btnLyricsRetry;

    private LyricsAdapter lyricsAdapter;
    private final LyricsRepository lyricsRepository = new LyricsRepository();
    private Lyrics currentLyrics = null;
    private Song currentLyricsSong = null;
    private boolean isUserScrollingLyrics = false;
    private final Handler lyricsScrollResumeHandler = new Handler(Looper.getMainLooper());
    private Runnable lyricsScrollResumeRunnable;

    private RecyclerView rvSearchResults;
    private SongAdapter songAdapter;
    private SongAdapter queueAdapter;
    private boolean isShowingQueue = false;
    private SearchResultAdapter searchResultAdapter;
    private List<Song> fullSongList = new ArrayList<>();

    private ImageButton btnSearchToggle;
    private ImageButton btnSettings;
    private EditText etSearch;
    private ImageButton btnSearchClose;
    private View searchDivider;

    private final SearchManager searchManager = new SearchManager();
    private final CatalogRepository catalogRepository = new CatalogRepository();
    private final TrackPlaybackManager trackPlaybackManager = new TrackPlaybackManager();
    private VisualizerController visualizerController;
    private PanelAnimationController panelAnimationController;
    private OledModeController oledModeController;

    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private Runnable updateSeekBarRunnable;
    private boolean isUserSeeking = false;
    private int lastSeekbarNotchProgress = -1;

    private MusicService musicService;
    private boolean isServiceBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            isServiceBound = true;
            musicService.addMusicChangeListener(MainActivity.this);

            if (fullSongList != null && !fullSongList.isEmpty()) {
                musicService.setOriginalList(fullSongList);
            }
            syncUiWithService();
            fetchMusicData();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            musicService = null;
        }
    };

    private final SearchManager.SearchCallback searchCallback = new SearchManager.SearchCallback() {
        @Override
        public void onSearchResultsUpdated(List<SearchResult> results, boolean isPending, boolean isOffline) {
            if (etSearch != null && etSearch.getText() != null && !etSearch.getText().toString().trim().isEmpty()) {
                expandSearchRectangle();
                if (searchDivider != null) searchDivider.setVisibility(View.VISIBLE);
                if (rvSearchResults != null) rvSearchResults.setVisibility(View.VISIBLE);
            }

            if (searchResultAdapter != null) {
                searchResultAdapter.setResults(results);
            }

            if (isOffline) {
                expandSearchRectangle();
                if (searchDivider != null) searchDivider.setVisibility(View.VISIBLE);
                if (rvSearchResults != null) rvSearchResults.setVisibility(View.VISIBLE);
                Toast.makeText(MainActivity.this, "Offline Mode: Showing local songs only", Toast.LENGTH_SHORT).show();
            } else if (!isPending && results.isEmpty() && etSearch != null && etSearch.getText() != null && !etSearch.getText().toString().trim().isEmpty()) {
                Toast.makeText(MainActivity.this, "No search results found", Toast.LENGTH_SHORT).show();
            }
        }
    };

    private final CatalogRepository.CatalogCallback catalogCallback = new CatalogRepository.CatalogCallback() {
        @Override
        public void onCatalogLoaded(List<Song> songs, boolean isOffline, String message) {
            fullSongList = songs != null ? songs : new ArrayList<>();
            if (songAdapter != null) {
                songAdapter.setSongs(fullSongList);
            }
            if (musicService != null) {
                musicService.setOriginalList(fullSongList);
            }
            if (message != null && !message.isEmpty()) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onRequestLocalNetworkPermission() {
            if (Build.VERSION.SDK_INT >= 37) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_LOCAL_NETWORK}, REQUEST_LOCAL_NETWORK);
            }
        }
    };

    private final TrackPlaybackManager.TrackPlaybackCallback trackPlaybackCallback = new TrackPlaybackManager.TrackPlaybackCallback() {
        @Override
        public void onSongDownloadingStateChanged(boolean isDownloading) {}

        @Override
        public void onTrackInfoPrepared(String title, String artist, String currentTime, String totalTime, String initialQuality, String imgUrl) {
            if (tvTitle != null) tvTitle.setText(title);
            if (tvArtist != null) tvArtist.setText(artist);
            if (tvCurrentTime != null) tvCurrentTime.setText(currentTime);
            if (tvTotalTime != null) tvTotalTime.setText(totalTime);

            if (initialQuality != null && !initialQuality.trim().isEmpty()) {
                if (badgeAudioQuality != null) badgeAudioQuality.setVisibility(View.VISIBLE);
                if (tvQualityText != null) tvQualityText.setText(initialQuality);
            } else {
                if (badgeAudioQuality != null) badgeAudioQuality.setVisibility(View.GONE);
            }

            if (btnPlayPause != null) btnPlayPause.setImageResource(R.drawable.ic_pause);
        }

        @Override
        public void onCoverLoaded(Bitmap bitmap) {
            MainActivity.this.onCoverLoaded(bitmap);
        }

        @Override
        public void onSongReadyToPlay(Song streamedSong, int existingIndex) {
            if (songAdapter != null) {
                songAdapter.setSongs(fullSongList);
            }
            fetchMusicData();
        }

        @Override
        public void onPlaybackError(String errorMessage) {
            if (btnPlayPause != null) btnPlayPause.setImageResource(R.drawable.ic_play);
            Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
            fetchMusicData();
        }

        @Override
        public void onCollapseSearchRequested() {
            collapseSearchRectangle();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setWindowDecor();
        setContentView(R.layout.activity_main);

        View mainRoot = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(mainRoot, (v, insets) -> {
            v.setPadding(0, 0, 0, 0);
            return insets;
        });

        initViews(mainRoot);
        setupAdaptersAndRecyclerViews();
        setupControlListeners();
        setupSearchOverlay();
        setupLyricsUI(mainRoot);
        setupGestureListeners(mainRoot);

        loadCachedSongsLocally();

        Intent serviceIntent = new Intent(this, MusicService.class);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @SuppressWarnings("deprecation")
    private void setWindowDecor() {
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
    }

    private void initViews(View mainRoot) {
        coverImageView = findViewById(R.id.cover_image);
        backgroundImageView = findViewById(R.id.background_image);
        tvTitle = findViewById(R.id.tv_title);
        tvArtist = findViewById(R.id.tv_artist);
        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvTotalTime = findViewById(R.id.tv_total_time);
        badgeAudioQuality = findViewById(R.id.badge_audio_quality);
        tvQualityText = findViewById(R.id.tv_quality_text);

        if (badgeAudioQuality != null) {
            badgeAudioQuality.setClickable(true);
            badgeAudioQuality.setFocusable(true);
            badgeAudioQuality.setOnClickListener(v -> {
                HapticHelper.lightTick(v);
                showSongInfoDialog();
            });
        }
        if (tvQualityText != null) {
            tvQualityText.setClickable(true);
            tvQualityText.setFocusable(true);
            tvQualityText.setOnClickListener(v -> {
                HapticHelper.lightTick(v);
                showSongInfoDialog();
            });
        }

        seekbarAudio = findViewById(R.id.seekbar_audio);
        btnPlayPause = findViewById(R.id.btn_play_pause);

        btnSearchToggle = findViewById(R.id.btn_search_toggle);
        btnSettings = findViewById(R.id.btn_settings);
        etSearch = findViewById(R.id.et_search);
        btnSearchClose = findViewById(R.id.btn_search_close);
        searchDivider = findViewById(R.id.search_divider);
        rvSearchResults = findViewById(R.id.rv_search_results);

        visualizerController = new VisualizerController(
                findViewById(R.id.bar1),
                findViewById(R.id.bar2),
                findViewById(R.id.bar3),
                findViewById(R.id.bar4)
        );

        panelAnimationController = new PanelAnimationController(mainRoot);
        oledModeController = new OledModeController(this, panelAnimationController);

        backgroundImageView.setRenderEffect(RenderEffect.createBlurEffect(80f, 80f, Shader.TileMode.CLAMP));
    }

    private void setupAdaptersAndRecyclerViews() {
        songAdapter = new SongAdapter(new SongAdapter.OnSongClickListener() {
            @Override
            public void onSongClick(Song song, int position) {
                if (musicService != null) {
                    musicService.addToQueue(song);
                    Toast.makeText(MainActivity.this, "Added to queue", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onSongPlayClick(Song song, int position) {
                collapseSearchRectangle();
                boolean isOfflineOrDisconnected = ServerPrefs.isOffline(MainActivity.this) || !ServerPrefs.isNetworkAvailable(MainActivity.this);
                if (isOfflineOrDisconnected && !ServerPrefs.isSongCachedLocally(MainActivity.this, song)) {
                    Toast.makeText(MainActivity.this, "Song is not cached offline", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (musicService != null) {
                    musicService.playImmediate(song);
                }
            }
        });

        queueAdapter = new SongAdapter(new SongAdapter.OnSongClickListener() {
            @Override
            public void onSongClick(Song song, int position) {
                boolean isOfflineOrDisconnected = ServerPrefs.isOffline(MainActivity.this) || !ServerPrefs.isNetworkAvailable(MainActivity.this);
                if (isOfflineOrDisconnected && !ServerPrefs.isSongCachedLocally(MainActivity.this, song)) {
                    Toast.makeText(MainActivity.this, "Song is not cached offline", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (musicService != null) {
                    musicService.playSong(position, true);
                }
            }

            @Override
            public void onSongPlayClick(Song song, int position) {
                boolean isOfflineOrDisconnected = ServerPrefs.isOffline(MainActivity.this) || !ServerPrefs.isNetworkAvailable(MainActivity.this);
                if (isOfflineOrDisconnected && !ServerPrefs.isSongCachedLocally(MainActivity.this, song)) {
                    Toast.makeText(MainActivity.this, "Song is not cached offline", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (musicService != null) {
                    musicService.playSong(position, true);
                }
            }

            @Override
            public void onItemsSwapped(List<Song> newOrder) {
                if (musicService != null) {
                    musicService.updateQueueOrder(newOrder);
                }
            }
        });
        queueAdapter.setQueueMode(true);

        setupQueueTabsAndTouchHelper();

        searchResultAdapter = new SearchResultAdapter(new SearchResultAdapter.OnSearchResultClickListener() {
            @Override
            public void onSearchResultClick(SearchResult result, int position) {
                if (result == null) return;
                Song song = new Song(
                        result.getTitle(),
                        result.getArtist(),
                        result.getAlbum(),
                        result.getUrl() != null ? result.getUrl() : "/song/" + result.getId(),
                        result.getImgUrl()
                );
                if (musicService != null) {
                    musicService.addToQueue(song);
                }
                String downloadUrl = getBaseUrl() + "/song/" + result.getId();
                if (result.getSource() != null && !result.getSource().trim().isEmpty()) {
                    downloadUrl += "?source=" + result.getSource().trim();
                }
                new HttpHandler().makeHttpGetRequest(downloadUrl, new HttpHandler.MusicDataCallback() {
                    @Override
                    public void onSuccess(String jsonResult) {
                        try {
                            Song downloadedSong = new Gson().fromJson(jsonResult, Song.class);
                            if (downloadedSong != null && downloadedSong.getAudioPath() != null) {
                                song.setAudioPath(downloadedSong.getAudioPath());
                                if (downloadedSong.getCoverPath() != null) {
                                    song.setCoverPath(downloadedSong.getCoverPath());
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    @Override
                    public void onError(Exception e) {}
                });
                Toast.makeText(MainActivity.this, "Added to queue", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSearchResultPlayClick(SearchResult result, int position) {
                trackPlaybackManager.onSearchResultSelected(MainActivity.this, result, fullSongList, musicService, getBaseUrl(), seekbarAudio, trackPlaybackCallback);
            }
        });

        if (rvSearchResults != null) {
            rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
            rvSearchResults.setAdapter(searchResultAdapter);
            rvSearchResults.addOnScrollListener(new HapticHelper.WeightedScrollListener(this, 56));
        }
    }

    private void setupQueueTabsAndTouchHelper() {
        TextView tabSongsPortrait = findViewById(R.id.tab_songs_portrait);
        TextView tabQueuePortrait = findViewById(R.id.tab_queue_portrait);
        TextView tabSongsLandscape = findViewById(R.id.tab_songs_landscape);
        TextView tabQueueLandscape = findViewById(R.id.tab_queue_landscape);
        ImageView btnClearQueuePortrait = findViewById(R.id.btn_clear_queue_portrait);
        ImageView btnClearQueueLandscape = findViewById(R.id.btn_clear_queue_landscape);

        ViewPager2 viewpagerPortrait = findViewById(R.id.viewpager_portrait);
        ViewPager2 viewpagerLandscape = findViewById(R.id.viewpager_landscape);

        View.OnClickListener clearQueueListener = v -> {
            HapticHelper.dismiss(v);
            if (musicService != null) {
                musicService.clearQueue();
                Toast.makeText(MainActivity.this, "Queue cleared", Toast.LENGTH_SHORT).show();
            }
        };

        if (btnClearQueuePortrait != null) btnClearQueuePortrait.setOnClickListener(clearQueueListener);
        if (btnClearQueueLandscape != null) btnClearQueueLandscape.setOnClickListener(clearQueueListener);

        ItemTouchHelper.SimpleCallback itemTouchCallback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                   @NonNull RecyclerView.ViewHolder viewHolder,
                                   @NonNull RecyclerView.ViewHolder target) {
                if (isShowingQueue && queueAdapter != null) {
                    int fromPos = viewHolder.getAdapterPosition();
                    int toPos = target.getAdapterPosition();
                    if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION) {
                        queueAdapter.swapItems(fromPos, toPos);
                        if (musicService != null) {
                            musicService.updateQueueOrder(queueAdapter.getSongs());
                        }
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                if (isShowingQueue && queueAdapter != null) {
                    int pos = viewHolder.getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        queueAdapter.removeItem(pos);
                        if (musicService != null) {
                            musicService.removeFromQueue(pos);
                        }
                        Toast.makeText(MainActivity.this, "Removed from queue", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                if (!isShowingQueue) return 0;
                return super.getSwipeDirs(recyclerView, viewHolder);
            }

            @Override
            public int getDragDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                if (!isShowingQueue) return 0;
                return super.getDragDirs(recyclerView, viewHolder);
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(itemTouchCallback);
        queueAdapter.setOnStartDragListener(itemTouchHelper::startDrag);

        SongListViewPagerAdapter pagerAdapter = new SongListViewPagerAdapter(songAdapter, queueAdapter, itemTouchHelper);

        if (viewpagerPortrait != null) {
            viewpagerPortrait.setAdapter(pagerAdapter);
        }
        if (viewpagerLandscape != null) {
            viewpagerLandscape.setAdapter(pagerAdapter);
        }

        ViewPager2.OnPageChangeCallback pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                isShowingQueue = (position == 1);
                if (isShowingQueue) {
                    updateTabUI(tabQueuePortrait, tabSongsPortrait);
                    updateTabUI(tabQueueLandscape, tabSongsLandscape);
                    if (btnClearQueuePortrait != null) btnClearQueuePortrait.setVisibility(View.VISIBLE);
                    if (btnClearQueueLandscape != null) btnClearQueueLandscape.setVisibility(View.VISIBLE);
                    if (musicService != null && queueAdapter != null) {
                        queueAdapter.setSongs(musicService.getSongList());
                    }
                } else {
                    updateTabUI(tabSongsPortrait, tabQueuePortrait);
                    updateTabUI(tabSongsLandscape, tabQueueLandscape);
                    if (btnClearQueuePortrait != null) btnClearQueuePortrait.setVisibility(View.GONE);
                    if (btnClearQueueLandscape != null) btnClearQueueLandscape.setVisibility(View.GONE);
                }
            }
        };

        if (viewpagerPortrait != null) {
            viewpagerPortrait.registerOnPageChangeCallback(pageChangeCallback);
        }
        if (viewpagerLandscape != null) {
            viewpagerLandscape.registerOnPageChangeCallback(pageChangeCallback);
        }

        View.OnClickListener songsTabListener = v -> {
            HapticHelper.segmentTick(v);
            if (viewpagerPortrait != null) viewpagerPortrait.setCurrentItem(0, true);
            if (viewpagerLandscape != null) viewpagerLandscape.setCurrentItem(0, true);
        };

        View.OnClickListener queueTabListener = v -> {
            HapticHelper.segmentTick(v);
            if (viewpagerPortrait != null) viewpagerPortrait.setCurrentItem(1, true);
            if (viewpagerLandscape != null) viewpagerLandscape.setCurrentItem(1, true);
        };

        if (tabSongsPortrait != null) tabSongsPortrait.setOnClickListener(songsTabListener);
        if (tabQueuePortrait != null) tabQueuePortrait.setOnClickListener(queueTabListener);
        if (tabSongsLandscape != null) tabSongsLandscape.setOnClickListener(songsTabListener);
        if (tabQueueLandscape != null) tabQueueLandscape.setOnClickListener(queueTabListener);
    }

    private void setupControlListeners() {
        ImageButton btnPrevious = findViewById(R.id.btn_previous);
        ImageButton btnNext = findViewById(R.id.btn_next);

        btnPlayPause.setOnClickListener(v -> {
            HapticHelper.playPauseClick(v);
            if (trackPlaybackManager.isSongDownloading()) {
                Toast.makeText(this, "Downloading song from server...", Toast.LENGTH_SHORT).show();
                return;
            }
            if (musicService != null) musicService.togglePlayPause();
        });

        btnNext.setOnClickListener(v -> {
            HapticHelper.lightTick(v);
            if (trackPlaybackManager.isSongDownloading()) {
                trackPlaybackManager.setSongDownloading(false);
                trackPlaybackManager.stopSeekBarLoadingAnimation(seekbarAudio);
            }
            if (musicService != null) musicService.playNextSong();
        });

        btnPrevious.setOnClickListener(v -> {
            HapticHelper.lightTick(v);
            if (trackPlaybackManager.isSongDownloading()) {
                trackPlaybackManager.setSongDownloading(false);
                trackPlaybackManager.stopSeekBarLoadingAnimation(seekbarAudio);
            }
            if (musicService != null) musicService.playPreviousSong();
        });

        btnLyrics = findViewById(R.id.btn_lyrics);
        if (btnLyrics != null) {
            btnLyrics.setOnClickListener(v -> {
                HapticHelper.lightTick(v);
                togglePortraitLyrics();
            });
        }

        ImageButton btnSongListToggle = findViewById(R.id.btn_song_list_toggle);
        if (btnSongListToggle != null) {
            btnSongListToggle.setOnClickListener(v -> {
                HapticHelper.lightTick(v);
                togglePortraitSongList();
            });
        }

        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                HapticHelper.lightTick(v);
                startActivity(new Intent(this, SettingsActivity.class));
            });
        }

        seekbarAudio.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    tvCurrentTime.setText(TimeUtils.formatTime(progress));
                    int max = seekBar.getMax();
                    int notchStepMs = Math.max(800, max / 70);
                    if (lastSeekbarNotchProgress < 0 || Math.abs(progress - lastSeekbarNotchProgress) >= notchStepMs) {
                        HapticHelper.performRotaryKnobTick(seekBar);
                        lastSeekbarNotchProgress = progress;
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
                lastSeekbarNotchProgress = seekBar.getProgress();
                HapticHelper.dragStart(seekBar);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                lastSeekbarNotchProgress = -1;
                HapticHelper.gestureEnd(seekBar);
                if (musicService != null) {
                    musicService.seekTo(seekBar.getProgress());
                }
                isUserSeeking = false;
                if (panelAnimationController != null && panelAnimationController.isPortraitLyricsOpen()) {
                    updateActiveLyricsLine(seekBar.getProgress());
                }
                startSeekBarUpdater();
            }
        });
    }

    private void setupSearchOverlay() {
        if (btnSearchToggle != null) {
            btnSearchToggle.setOnClickListener(v -> {
                HapticHelper.lightTick(v);
                expandSearchRectangle();
                if (etSearch != null) {
                    etSearch.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(etSearch, 0);
                    }
                }
            });
        }

        if (btnSearchClose != null) {
            btnSearchClose.setOnClickListener(v -> {
                HapticHelper.lightTick(v);
                collapseSearchRectangle();
            });
        }

        ImageView ivSearchIcon = findViewById(R.id.iv_search_icon);
        if (ivSearchIcon != null) {
            ivSearchIcon.setOnClickListener(v -> {
                if (etSearch != null) {
                    String query = etSearch.getText().toString();
                    searchManager.filterSongs(fullSongList, query, searchCallback);
                    searchManager.performSearch(MainActivity.this, fullSongList, query, getBaseUrl(), etSearch.getText().toString(), searchCallback);
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
                    }
                }
            });
        }

        if (etSearch != null) {
            etSearch.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            etSearch.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) expandSearchRectangle();
            });
            etSearch.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    String query = etSearch.getText().toString();
                    searchManager.filterSongs(fullSongList, query, searchCallback);
                    searchManager.performSearch(MainActivity.this, fullSongList, query, getBaseUrl(), etSearch.getText().toString(), searchCallback);
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
                    }
                    return true;
                }
                return false;
            });
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (s.length() > 0) {
                        View searchBarContainer = findViewById(R.id.search_bar_container);
                        if (searchBarContainer != null && searchBarContainer.getVisibility() != View.VISIBLE) {
                            expandSearchRectangle();
                        }
                    } else {
                        if (searchResultAdapter != null) {
                            searchResultAdapter.setResults(null);
                        }
                    }
                    searchManager.filterSongs(fullSongList, s.toString(), searchCallback);
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void setupGestureListeners(View mainRoot) {
        View.OnTouchListener swipeTouchListener = panelAnimationController.createSwipeTouchListener(visualizerController, () -> musicService, (dropX, dropY) -> {
            checkAndStartFloatingPlayer(dropX, dropY);
            moveTaskToBack(true);
        });

        if (mainRoot != null) {
            mainRoot.setOnTouchListener((v, event) -> {
                if (!isPortraitMode()) return false;
                if (event.getY() < (getResources().getDisplayMetrics().heightPixels * 0.70f)) {
                    return swipeTouchListener.onTouch(v, event);
                }
                return false;
            });
        }

        View cardCover = findViewById(R.id.card_cover);
        if (cardCover != null) {
            cardCover.bringToFront();
            cardCover.setOnTouchListener(swipeTouchListener);
        }

        View tvTitleView = findViewById(R.id.tv_title);
        if (tvTitleView != null) tvTitleView.setOnTouchListener(swipeTouchListener);

        View tvArtistView = findViewById(R.id.tv_artist);
        if (tvArtistView != null) tvArtistView.setOnTouchListener(swipeTouchListener);

        View headerVisualizer = findViewById(R.id.header_visualizer);
        if (headerVisualizer != null) headerVisualizer.setOnTouchListener(swipeTouchListener);
    }

    public void updateVisualizerState() {
        if (visualizerController != null && panelAnimationController != null) {
            boolean isHeaderOpen = panelAnimationController.isAnyPortraitPanelOpen()
                    || (oledModeController != null && oledModeController.isOledActive());
            visualizerController.updateVisualizerState(isHeaderOpen, isPortraitMode(), musicService);
        }
    }

    private void togglePortraitSongList() {
        if (panelAnimationController != null) {
            panelAnimationController.togglePortraitSongList(visualizerController, musicService);
        }
    }

    private void togglePortraitLyrics() {
        if (panelAnimationController != null) {
            panelAnimationController.togglePortraitLyrics(visualizerController, musicService);
            if (panelAnimationController.isPortraitLyricsOpen()) {
                if (currentLyrics == null && musicService != null && musicService.getCurrentSong() != null) {
                    loadLyricsForCurrentSong(false);
                } else if (musicService != null) {
                    updateActiveLyricsLine(musicService.getCurrentPosition());
                }
                startSeekBarUpdater();
            }
        }
    }

    private void expandSearchRectangle() {
        if (panelAnimationController != null) {
            panelAnimationController.expandSearchRectangle(isPortraitMode());
        }
    }

    private void collapseSearchRectangle() {
        if (panelAnimationController != null) {
            panelAnimationController.collapseSearchRectangle(etSearch, isPortraitMode());
        }
    }

    private void updateTabUI(TextView activeTab, TextView inactiveTab) {
        if (activeTab != null) {
            activeTab.setTextColor(0xFF00FFCC);
            activeTab.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        if (inactiveTab != null) {
            inactiveTab.setTextColor(0x80FFFFFF);
            inactiveTab.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }

    private void checkAndStartFloatingPlayer(int dropX, int dropY) {
        if (!Settings.canDrawOverlays(this)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Pop Up Player Permission")
                    .setMessage("To display the pop up player over other apps, please grant 'Display over other apps' permission.")
                    .setPositiveButton("Grant Permission", (dialog, which) -> {
                        Intent intent = new Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())
                        );
                        startActivity(intent);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        Intent intent = new Intent(this, FloatingPlayerService.class);
        intent.putExtra("DROP_X", dropX);
        intent.putExtra("DROP_Y", dropY);
        startService(intent);
    }

    private void syncUiWithService() {
        if (musicService == null) return;

        if (queueAdapter != null) {
            queueAdapter.setSongs(musicService.getSongList());
        }


        Song song = musicService.getCurrentSong();
        if (song != null) {
            if (songAdapter != null) songAdapter.setSelectedSong(song);
            if (queueAdapter != null) queueAdapter.setSelectedSong(song);
            onSongChanged(song, musicService.getCurrentSongIndex());
        }

        Bitmap cover = musicService.getCurrentCoverBitmap();
        if (cover != null) {
            onCoverLoaded(cover);
        }

        onPlaybackStateChanged(musicService.isPlaying());
        startSeekBarUpdater();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (oledModeController != null) {
            oledModeController.onResume();
        }
        updateVisualizerState();
        try {
            stopService(new Intent(this, FloatingPlayerService.class));
        } catch (Exception ignored) {}

        if (ServerPrefs.isOffline(this)) {
            loadCachedSongsLocally();
        } else {
            fetchMusicData();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (oledModeController != null) {
            oledModeController.onPause();
        }
        if (visualizerController != null) {
            visualizerController.stopHeaderVisualizerAnimation();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (oledModeController != null) {
            oledModeController.handleDispatchTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onSongChanged(Song song, int index) {
        if (song == null) return;

        tvTitle.setText(song.getTitle() != null && !song.getTitle().isEmpty() ? song.getTitle() : "Unknown Title");
        tvArtist.setText(song.getArtist() != null && !song.getArtist().isEmpty() ? song.getArtist() : "Unknown Artist");

        if (songAdapter != null) songAdapter.setSelectedSong(song);
        if (queueAdapter != null) queueAdapter.setSelectedSong(song);

        String songQuality = song.getQuality();
        if (songQuality != null && !songQuality.trim().isEmpty()) {
            if (oledModeController == null || !oledModeController.isOledActive()) {
                badgeAudioQuality.setVisibility(View.VISIBLE);
            }
            tvQualityText.setText(songQuality);
        }

        QualityMetadataHelper.updateQualityBadge(this, badgeAudioQuality, tvQualityText, song.getAudioPath(), ServerPrefs.formatFullUrl(getBaseUrl(), song.getAudioPath()), quality -> {
            if (songAdapter != null) Log.d(TAG, "Song quality detected: " + quality);
        });

        if (oledModeController != null && oledModeController.isOledActive()) {
            if (badgeAudioQuality != null) {
                badgeAudioQuality.setVisibility(View.INVISIBLE);
                badgeAudioQuality.setAlpha(0f);
            }
        }
        updateVisualizerState();
        loadLyricsForCurrentSong(false);

        // Pre-fetch next track's lyrics for instantaneous transitions
        Song nextSong = musicService != null ? musicService.getNextSong() : null;
        if (nextSong != null) {
            lyricsRepository.prefetchLyrics(this, nextSong, 0);
        }
    }

    @Override
    public void onQueueChanged(List<Song> queue) {
        runOnUiThread(() -> {
            if (queueAdapter != null) queueAdapter.setSongs(queue);
        });
    }

    @Override
    public void onQualityDetected(String quality) {
        runOnUiThread(() -> {
            if (quality != null && !quality.trim().isEmpty()) {
                badgeAudioQuality.setVisibility(View.VISIBLE);
                tvQualityText.setText(quality);
            }
        });
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        if (isPlaying) {
            if (trackPlaybackManager.isSongDownloading()) {
                trackPlaybackManager.setSongDownloading(false);
                trackPlaybackManager.stopSeekBarLoadingAnimation(seekbarAudio);
            }
            btnPlayPause.setImageResource(R.drawable.ic_pause);
            startSeekBarUpdater();
        } else {
            if (!trackPlaybackManager.isSongDownloading()) {
                btnPlayPause.setImageResource(R.drawable.ic_play);
            }
        }
        updateVisualizerState();
    }

    @Override
    public void onCoverLoaded(Bitmap bitmap) {
        if (bitmap != null) {
            coverImageView.setImageBitmap(bitmap);
            backgroundImageView.setImageBitmap(bitmap);

            View backgroundScrim = findViewById(R.id.background_scrim);
            if (backgroundScrim != null) {
                if (ServerPrefs.isContrastProtectionEnabled(this)) {
                    boolean isLight = ImageUtils.isBitmapLight(bitmap);
                    backgroundScrim.setAlpha(isLight ? 0.85f : 0.50f);
                } else {
                    backgroundScrim.setAlpha(0.25f);
                }
            }
        }
    }

    @Override
    public void onPlayerError(String errorMessage) {
        runOnUiThread(() -> {
            trackPlaybackManager.setSongDownloading(false);
            trackPlaybackManager.stopSeekBarLoadingAnimation(seekbarAudio);
            if (btnPlayPause != null) btnPlayPause.setImageResource(R.drawable.ic_play);
            String msg = errorMessage != null ? errorMessage : "Playback failed";
            Toast.makeText(MainActivity.this, "Playback error: " + msg, Toast.LENGTH_LONG).show();
            updateVisualizerState();
        });
    }

    private void loadCachedSongsLocally() {
        fullSongList = catalogRepository.loadCachedSongsLocally(this);
        if (songAdapter != null) songAdapter.setSongs(fullSongList);
        if (musicService != null) {
            musicService.setOriginalList(fullSongList);
        }
    }

    private void fetchMusicData() {
        catalogRepository.fetchMusicData(this, getBaseUrl(), catalogCallback);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_LOCAL_NETWORK) return;

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchMusicData();
        } else {
            Toast.makeText(this, "Local network access is required to connect to your server.", Toast.LENGTH_LONG).show();
        }
    }

    private void showSongInfoDialog() {
        SongInfoDialogHelper.showSongInfoDialog(
                this,
                musicService,
                fullSongList,
                getBaseUrl(),
                tvTotalTime != null ? tvTotalTime.getText().toString() : "--:--"
        );
    }

    private void startSeekBarUpdater() {
        if (trackPlaybackManager.isSongDownloading()) return;
        if (updateSeekBarRunnable != null) {
            updateHandler.removeCallbacks(updateSeekBarRunnable);
        }

        updateSeekBarRunnable = new Runnable() {
            @Override
            public void run() {
                if (trackPlaybackManager.isSongDownloading()) return;
                if (musicService != null && !isUserSeeking) {
                    long currentPos = musicService.getCurrentPosition();
                    long duration = musicService.getDuration();
                    long bufferedPos = musicService.getBufferedPosition();

                    if (duration > 0) {
                        seekbarAudio.setMax((int) duration);
                        tvTotalTime.setText(TimeUtils.formatTime((int) duration));
                    }

                    seekbarAudio.setProgress((int) currentPos);
                    seekbarAudio.setSecondaryProgress((int) bufferedPos);
                    tvCurrentTime.setText(TimeUtils.formatTime((int) currentPos));

                    if (panelAnimationController != null && panelAnimationController.isPortraitLyricsOpen()) {
                        updateActiveLyricsLine(currentPos);
                    }

                    if (musicService.isPlaying()) {
                        boolean wordSyncActive = panelAnimationController != null
                                && panelAnimationController.isPortraitLyricsOpen()
                                && lyricsAdapter != null
                                && lyricsAdapter.hasActiveWordSync();
                        int delay = wordSyncActive ? 50 : 500;
                        updateHandler.postDelayed(this, delay);
                    }
                }
            }
        };
        updateHandler.post(updateSeekBarRunnable);
    }

    private void setupLyricsUI(View mainRoot) {
        portraitLyricsPanel = mainRoot.findViewById(R.id.portrait_lyrics_panel);
        rvSyncedLyrics = mainRoot.findViewById(R.id.rv_synced_lyrics);
        scrollPlainLyrics = mainRoot.findViewById(R.id.scroll_plain_lyrics);
        tvPlainLyrics = mainRoot.findViewById(R.id.tv_plain_lyrics);
        lyricsLoadingContainer = mainRoot.findViewById(R.id.lyrics_loading_container);
        lyricsInstrumentalContainer = mainRoot.findViewById(R.id.lyrics_instrumental_container);
        lyricsEmptyContainer = mainRoot.findViewById(R.id.lyrics_empty_container);
        tvLyricsEmptyTitle = mainRoot.findViewById(R.id.tv_lyrics_empty_title);
        tvLyricsEmptySubtitle = mainRoot.findViewById(R.id.tv_lyrics_empty_subtitle);
        btnLyricsRetry = mainRoot.findViewById(R.id.btn_lyrics_retry);

        lyricsScrollResumeRunnable = () -> isUserScrollingLyrics = false;

        lyricsAdapter = new LyricsAdapter((line, position, targetTimeMs) -> {
            if (musicService != null && line != null && !line.isEmpty()) {
                musicService.seekTo(targetTimeMs);
                isUserScrollingLyrics = false;
                lyricsAdapter.updatePlaybackPosition(position, targetTimeMs);
                scrollToLyricPosition(position);
            }
        });

        if (rvSyncedLyrics != null) {
            rvSyncedLyrics.setLayoutManager(new LinearLayoutManager(this));
            rvSyncedLyrics.setAdapter(lyricsAdapter);
            rvSyncedLyrics.setItemAnimator(null);
            rvSyncedLyrics.addOnScrollListener(new HapticHelper.WeightedScrollListener(this, 50));
            rvSyncedLyrics.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        isUserScrollingLyrics = true;
                        lyricsScrollResumeHandler.removeCallbacks(lyricsScrollResumeRunnable);
                    } else if (newState == RecyclerView.SCROLL_STATE_IDLE && isUserScrollingLyrics) {
                        lyricsScrollResumeHandler.removeCallbacks(lyricsScrollResumeRunnable);
                        lyricsScrollResumeHandler.postDelayed(lyricsScrollResumeRunnable, 3500);
                    }
                }
            });
        }

        if (scrollPlainLyrics != null) {
            HapticHelper.attachWeightedScrollListener(scrollPlainLyrics, 48);
        }

        if (btnLyricsRetry != null) {
            btnLyricsRetry.setOnClickListener(v -> {
                HapticHelper.lightTick(v);
                loadLyricsForCurrentSong(true);
            });
        }
    }

    private void loadLyricsForCurrentSong(boolean forceRefresh) {
        Song song = musicService != null ? musicService.getCurrentSong() : null;
        if (song == null) {
            showLyricsEmpty("No song playing", "");
            return;
        }

        currentLyricsSong = song;
        currentLyrics = null;
        if (lyricsAdapter != null) {
            lyricsAdapter.setLines(null);
        }
        showLyricsLoading();

        long durationMs = musicService != null ? musicService.getDuration() : 0;
        lyricsRepository.fetchLyrics(this, song, durationMs, forceRefresh, new LyricsRepository.LyricsCallback() {
            @Override
            public void onLyricsLoaded(Lyrics lyrics) {
                if (currentLyricsSong != song) return;
                currentLyrics = lyrics;
                displayLyrics(lyrics);
            }

            @Override
            public void onLyricsError(String errorMessage) {
                if (currentLyricsSong != song) return;
                showLyricsEmpty("No lyrics found", "Couldn't find lyrics on LRCLIB for this track");
            }
        });
    }

    private void displayLyrics(Lyrics lyrics) {
        if (lyrics == null) {
            showLyricsEmpty("No lyrics found", "");
            return;
        }
        if (lyrics.isInstrumental()) {
            showLyricsInstrumental();
            return;
        }
        if (lyrics.hasSyncedLyrics()) {
            showSyncedLyrics(lyrics.getParsedLines());
            if (musicService != null) {
                updateActiveLyricsLine(musicService.getCurrentPosition());
            }
            startSeekBarUpdater();
        } else if (lyrics.getPlainLyrics() != null && !lyrics.getPlainLyrics().trim().isEmpty()) {
            showPlainLyrics(lyrics.getPlainLyrics());
        } else {
            showLyricsEmpty("No lyrics found", "No lyrics available for this song");
        }
    }

    private void showLyricsLoading() {
        if (lyricsLoadingContainer != null) lyricsLoadingContainer.setVisibility(View.VISIBLE);
        if (rvSyncedLyrics != null) rvSyncedLyrics.setVisibility(View.GONE);
        if (scrollPlainLyrics != null) scrollPlainLyrics.setVisibility(View.GONE);
        if (lyricsInstrumentalContainer != null) lyricsInstrumentalContainer.setVisibility(View.GONE);
        if (lyricsEmptyContainer != null) lyricsEmptyContainer.setVisibility(View.GONE);
    }

    private void showSyncedLyrics(List<LyricLine> lines) {
        if (lyricsLoadingContainer != null) lyricsLoadingContainer.setVisibility(View.GONE);
        if (scrollPlainLyrics != null) scrollPlainLyrics.setVisibility(View.GONE);
        if (lyricsInstrumentalContainer != null) lyricsInstrumentalContainer.setVisibility(View.GONE);
        if (lyricsEmptyContainer != null) lyricsEmptyContainer.setVisibility(View.GONE);
        if (rvSyncedLyrics != null) {
            rvSyncedLyrics.setVisibility(View.VISIBLE);
            if (lyricsAdapter != null) {
                lyricsAdapter.setLines(lines);
            }
        }
    }

    private void showPlainLyrics(String plainText) {
        if (lyricsLoadingContainer != null) lyricsLoadingContainer.setVisibility(View.GONE);
        if (rvSyncedLyrics != null) rvSyncedLyrics.setVisibility(View.GONE);
        if (lyricsInstrumentalContainer != null) lyricsInstrumentalContainer.setVisibility(View.GONE);
        if (lyricsEmptyContainer != null) lyricsEmptyContainer.setVisibility(View.GONE);
        if (scrollPlainLyrics != null) {
            scrollPlainLyrics.setVisibility(View.VISIBLE);
            if (tvPlainLyrics != null) {
                tvPlainLyrics.setText(plainText);
            }
        }
    }

    private void showLyricsInstrumental() {
        if (lyricsLoadingContainer != null) lyricsLoadingContainer.setVisibility(View.GONE);
        if (rvSyncedLyrics != null) rvSyncedLyrics.setVisibility(View.GONE);
        if (scrollPlainLyrics != null) scrollPlainLyrics.setVisibility(View.GONE);
        if (lyricsEmptyContainer != null) lyricsEmptyContainer.setVisibility(View.GONE);
        if (lyricsInstrumentalContainer != null) lyricsInstrumentalContainer.setVisibility(View.VISIBLE);
    }

    private void showLyricsEmpty(String title, String subtitle) {
        if (lyricsLoadingContainer != null) lyricsLoadingContainer.setVisibility(View.GONE);
        if (rvSyncedLyrics != null) rvSyncedLyrics.setVisibility(View.GONE);
        if (scrollPlainLyrics != null) scrollPlainLyrics.setVisibility(View.GONE);
        if (lyricsInstrumentalContainer != null) lyricsInstrumentalContainer.setVisibility(View.GONE);
        if (lyricsEmptyContainer != null) {
            lyricsEmptyContainer.setVisibility(View.VISIBLE);
            if (tvLyricsEmptyTitle != null) tvLyricsEmptyTitle.setText(title);
            if (tvLyricsEmptySubtitle != null) tvLyricsEmptySubtitle.setText(subtitle);
        }
    }

    private void updateActiveLyricsLine(long positionMs) {
        if (currentLyrics == null || !currentLyrics.hasSyncedLyrics() || lyricsAdapter == null) return;
        List<LyricLine> lines = currentLyrics.getParsedLines();
        if (lines == null || lines.isEmpty()) return;

        int targetIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).getTimeMs() <= positionMs) {
                targetIndex = i;
            } else {
                break;
            }
        }

        boolean lineChanged = (targetIndex != lyricsAdapter.getActivePosition());
        lyricsAdapter.updatePlaybackPosition(targetIndex, positionMs);

        if (lineChanged && !isUserScrollingLyrics && targetIndex >= 0 && rvSyncedLyrics != null) {
            scrollToLyricPosition(targetIndex);
        }
    }

    private static final DecelerateInterpolator LYRIC_SCROLL_INTERPOLATOR = new DecelerateInterpolator(2.2f);

    private void scrollToLyricPosition(int position) {
        if (rvSyncedLyrics == null || rvSyncedLyrics.getLayoutManager() == null) return;
        LinearLayoutManager lm = (LinearLayoutManager) rvSyncedLyrics.getLayoutManager();
        LinearSmoothScroller scroller = new LinearSmoothScroller(this) {
            @Override
            public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd, int snapPreference) {
                return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2);
            }

            @Override
            protected float calculateSpeedPerPixel(android.util.DisplayMetrics displayMetrics) {
                return 180f / displayMetrics.densityDpi;
            }

            @Override
            protected int calculateTimeForDeceleration(int dx) {
                return Math.max(super.calculateTimeForDeceleration(dx), 450);
            }

            @Override
            protected void onTargetFound(View targetView, RecyclerView.State state, Action action) {
                int dx = calculateDxToMakeVisible(targetView, getHorizontalSnapPreference());
                int dy = calculateDyToMakeVisible(targetView, getVerticalSnapPreference());
                final int distance = (int) Math.sqrt(dx * dx + dy * dy);
                int time = Math.max(calculateTimeForDeceleration(distance), 450);
                if (time > 0) {
                    action.update(-dx, -dy, time, LYRIC_SCROLL_INTERPOLATOR);
                }
            }
        };
        scroller.setTargetPosition(position);
        lm.startSmoothScroll(scroller);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        trackPlaybackManager.stopSeekBarLoadingAnimation(seekbarAudio);
        if (lyricsScrollResumeHandler != null) {
            lyricsScrollResumeHandler.removeCallbacksAndMessages(null);
        }
        if (visualizerController != null) {
            visualizerController.stopHeaderVisualizerAnimation();
        }
        if (isServiceBound) {
            if (musicService != null) {
                musicService.removeMusicChangeListener(MainActivity.this);
            }
            unbindService(serviceConnection);
            isServiceBound = false;
        }
        if (updateHandler != null) {
            updateHandler.removeCallbacksAndMessages(null);
        }
    }
}
