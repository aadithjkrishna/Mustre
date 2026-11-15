package com.aadith.mustre.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import com.aadith.mustre.R;
import com.aadith.mustre.model.Song;
import com.aadith.mustre.network.ServerPrefs;
import com.aadith.mustre.service.MusicService;
import com.aadith.mustre.util.HapticHelper;

@OptIn(markerClass = UnstableApi.class)
public class PanelAnimationController {

    public interface OnSwipeActionListener {
        void onFloatingPlayerDrop(int dropX, int dropY);
    }

    public interface MusicServiceSupplier {
        MusicService getMusicService();
    }

    private final View activityRoot;
    private final View cardCover;
    private final View cardCoverIncoming;
    private final ImageView coverImageIncoming;
    private final View portraitSongListPanel;
    private final View portraitLyricsPanel;
    private final TextView tvTitleView;
    private final TextView tvArtistView;
    private final View headerVisualizer;

    private final View searchBarContainer;
    private final View searchDivider;
    private final View rvSearchResults;
    private final View btnSearchToggle;
    private final View btnSettings;
    private final View btnSearchClose;

    private final ImageButton btnSongListToggle;
    private final ImageButton btnLyrics;

    private boolean isPortraitSongListOpen = false;
    private boolean isPortraitLyricsOpen = false;
    private float lastSwipeFraction = 0f;

    public PanelAnimationController(View activityRoot) {
        this.activityRoot = activityRoot;
        this.cardCover = activityRoot.findViewById(R.id.card_cover);
        this.cardCoverIncoming = activityRoot.findViewById(R.id.card_cover_incoming);
        this.coverImageIncoming = activityRoot.findViewById(R.id.cover_image_incoming);
        this.portraitSongListPanel = activityRoot.findViewById(R.id.portrait_song_list_panel);
        this.portraitLyricsPanel = activityRoot.findViewById(R.id.portrait_lyrics_panel);
        this.tvTitleView = activityRoot.findViewById(R.id.tv_title);
        this.tvArtistView = activityRoot.findViewById(R.id.tv_artist);
        this.headerVisualizer = activityRoot.findViewById(R.id.header_visualizer);

        this.searchBarContainer = activityRoot.findViewById(R.id.search_bar_container);
        this.searchDivider = activityRoot.findViewById(R.id.search_divider);
        this.rvSearchResults = activityRoot.findViewById(R.id.rv_search_results);
        this.btnSearchToggle = activityRoot.findViewById(R.id.btn_search_toggle);
        this.btnSettings = activityRoot.findViewById(R.id.btn_settings);
        this.btnSearchClose = activityRoot.findViewById(R.id.btn_search_close);

        this.btnSongListToggle = activityRoot.findViewById(R.id.btn_song_list_toggle);
        this.btnLyrics = activityRoot.findViewById(R.id.btn_lyrics);

        if (this.cardCover != null) {
            this.cardCover.bringToFront();
        }
    }

    public boolean isPortraitSongListOpen() {
        return isPortraitSongListOpen;
    }

    public boolean isPortraitLyricsOpen() {
        return isPortraitLyricsOpen;
    }

    private ValueAnimator oledCollapseAnimator;
    private boolean isHeaderCollapsedForOled = false;

    public boolean isHeaderCollapsedForOled() {
        return isHeaderCollapsedForOled;
    }

    public boolean isAnyPortraitPanelOpen() {
        return isPortraitSongListOpen || isPortraitLyricsOpen;
    }

    public void applySwipeFraction(float fraction) {
        float clampedFraction = Math.min(1.0f, Math.max(0.0f, fraction));
        lastSwipeFraction = clampedFraction;

        applyHeaderCollapseFraction(clampedFraction);

        float density = activityRoot.getResources().getDisplayMetrics().density;
        View activePanel = isPortraitLyricsOpen ? portraitLyricsPanel : portraitSongListPanel;
        View inactivePanel = isPortraitLyricsOpen ? portraitSongListPanel : portraitLyricsPanel;

        if (inactivePanel != null) {
            inactivePanel.setVisibility(View.GONE);
        }

        if (activePanel != null) {
            activePanel.setVisibility(View.VISIBLE);
            activePanel.setTranslationY((320f * density) * (1.0f - clampedFraction));
            activePanel.setAlpha(clampedFraction);
        }
    }

    public void applyHeaderCollapseFraction(float clampedFraction) {
        if (cardCover == null) return;

        cardCover.bringToFront();
        cardCover.setPivotX(cardCover.getWidth() / 2f);
        cardCover.setPivotY(cardCover.getHeight() / 2f);
        cardCover.setRotation(0f);

        float density = activityRoot.getResources().getDisplayMetrics().density;
        float screenWidthPx = activityRoot.getResources().getDisplayMetrics().widthPixels;

        float coverTargetX = getCoverTargetX(density, screenWidthPx);
        float coverTargetY = getCoverTargetY(density);

        float titleTargetX = getTitleTargetX(density, screenWidthPx);
        float titleTargetY = getTitleTargetY(density);

        float artistTargetX = getArtistTargetX(density, screenWidthPx);
        float artistTargetY = getArtistTargetY(density);

        cardCover.setTranslationX(coverTargetX * clampedFraction);
        cardCover.setTranslationY(coverTargetY * clampedFraction);
        float coverScale = 1.0f - ((1.0f - 0.148f) * clampedFraction);
        cardCover.setScaleX(coverScale);
        cardCover.setScaleY(coverScale);

        float textScale = 1.0f - ((1.0f - 0.68f) * clampedFraction);

        if (tvTitleView != null) {
            tvTitleView.setTranslationX(titleTargetX * clampedFraction);
            tvTitleView.setTranslationY(titleTargetY * clampedFraction);
            tvTitleView.setScaleX(textScale);
            tvTitleView.setScaleY(textScale);

            int targetWidth = (clampedFraction > 0.35f) ? (int) (screenWidthPx - (144f * density) - (115f * density)) : 0;
            if (tvTitleView.getLayoutParams().width != targetWidth) {
                ViewGroup.LayoutParams lp = tvTitleView.getLayoutParams();
                lp.width = targetWidth;
                tvTitleView.setLayoutParams(lp);
            }
        }

        if (tvArtistView != null) {
            tvArtistView.setTranslationX(artistTargetX * clampedFraction);
            tvArtistView.setTranslationY(artistTargetY * clampedFraction);
            tvArtistView.setScaleX(textScale);
            tvArtistView.setScaleY(textScale);

            int targetWidth = (clampedFraction > 0.35f) ? (int) (screenWidthPx - (144f * density) - (115f * density)) : 0;
            if (tvArtistView.getLayoutParams().width != targetWidth) {
                ViewGroup.LayoutParams lp = tvArtistView.getLayoutParams();
                lp.width = targetWidth;
                tvArtistView.setLayoutParams(lp);
            }
        }

        if (headerVisualizer != null) {
            if (clampedFraction > 0.2f) {
                headerVisualizer.setVisibility(View.VISIBLE);
                headerVisualizer.setAlpha((clampedFraction - 0.2f) / 0.8f);
            } else {
                headerVisualizer.setVisibility(View.GONE);
            }
        }
    }

    public void collapseHeaderForOled(boolean collapse, boolean animate) {
        if (isAnyPortraitPanelOpen()) {
            return;
        }
        if (isHeaderCollapsedForOled == collapse) {
            return;
        }
        isHeaderCollapsedForOled = collapse;

        if (oledCollapseAnimator != null) {
            oledCollapseAnimator.cancel();
        }

        float start = collapse ? 0.0f : 1.0f;
        float end = collapse ? 1.0f : 0.0f;

        if (!animate) {
            applyHeaderCollapseFraction(end);
            return;
        }

        oledCollapseAnimator = ValueAnimator.ofFloat(start, end);
        oledCollapseAnimator.setDuration(380);
        oledCollapseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        oledCollapseAnimator.addUpdateListener(anim -> {
            float frac = (float) anim.getAnimatedValue();
            applyHeaderCollapseFraction(frac);
        });
        oledCollapseAnimator.start();
    }

    public void animateToState(boolean targetOpen, VisualizerController visualizerController, MusicService musicService) {
        boolean currentlyOpen = isAnyPortraitPanelOpen();
        if (currentlyOpen != targetOpen) {
            if (targetOpen) {
                if (!isPortraitLyricsOpen) {
                    togglePortraitSongList(visualizerController, musicService);
                } else {
                    togglePortraitLyrics(visualizerController, musicService);
                }
            } else {
                closeAllPortraitPanels(visualizerController, musicService);
            }
        } else {
            float finalFraction = targetOpen ? 1.0f : 0.0f;

            ValueAnimator animator = ValueAnimator.ofFloat(lastSwipeFraction, finalFraction);
            animator.setDuration(220);
            animator.setInterpolator(new DecelerateInterpolator(1.8f));
            animator.addUpdateListener(animation -> {
                float val = (float) animation.getAnimatedValue();
                applySwipeFraction(val);
            });
            animator.start();
        }
    }

    public void togglePortraitSongList(VisualizerController visualizerController, MusicService musicService) {
        if (cardCover == null || portraitSongListPanel == null) return;
        HapticHelper.segmentTick(activityRoot);

        float density = activityRoot.getResources().getDisplayMetrics().density;
        float screenWidthPx = activityRoot.getResources().getDisplayMetrics().widthPixels;
        boolean isPortraitMode = activityRoot.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT;

        if (isPortraitSongListOpen) {
            closeAllPortraitPanels(visualizerController, musicService);
            return;
        }

        if (isPortraitLyricsOpen) {
            // Lyrics was open, switch smoothly to Song List without moving header
            isPortraitLyricsOpen = false;
            isPortraitSongListOpen = true;

            if (btnLyrics != null) btnLyrics.setColorFilter(0xB3FFFFFF);
            if (btnSongListToggle != null) btnSongListToggle.setColorFilter(0xFF00FFCC);

            if (portraitLyricsPanel != null) {
                portraitLyricsPanel.animate().cancel();
                portraitLyricsPanel.animate()
                        .alpha(0f)
                        .translationY(40f * density)
                        .setDuration(180)
                        .withEndAction(() -> {
                            if (portraitLyricsPanel != null) portraitLyricsPanel.setVisibility(View.GONE);
                        })
                        .start();
            }

            portraitSongListPanel.setVisibility(View.VISIBLE);
            portraitSongListPanel.setTranslationY(40f * density);
            portraitSongListPanel.setAlpha(0f);
            portraitSongListPanel.animate().cancel();
            portraitSongListPanel.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(240)
                    .setInterpolator(new DecelerateInterpolator(1.6f))
                    .start();

            if (visualizerController != null) {
                visualizerController.updateVisualizerState(true, isPortraitMode, musicService);
            }
            return;
        }

        // Neither was open: Full animation from player to Header + Song List
        isPortraitSongListOpen = true;
        if (btnSongListToggle != null) btnSongListToggle.setColorFilter(0xFF00FFCC);
        if (btnLyrics != null) btnLyrics.setColorFilter(0xB3FFFFFF);

        animateHeaderToTop(density, screenWidthPx);

        if (headerVisualizer != null) {
            headerVisualizer.setVisibility(View.VISIBLE);
            headerVisualizer.setAlpha(0f);
            headerVisualizer.animate().cancel();
            headerVisualizer.animate().alpha(1f).setDuration(340).start();
        }
        if (visualizerController != null) {
            visualizerController.updateVisualizerState(true, isPortraitMode, musicService);
        }

        if (portraitLyricsPanel != null) portraitLyricsPanel.setVisibility(View.GONE);

        portraitSongListPanel.setVisibility(View.VISIBLE);
        portraitSongListPanel.setTranslationY(320f * density);
        portraitSongListPanel.setAlpha(0f);
        portraitSongListPanel.animate().cancel();
        portraitSongListPanel.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(340)
                .setInterpolator(new DecelerateInterpolator(1.8f))
                .start();
    }

    public void togglePortraitLyrics(VisualizerController visualizerController, MusicService musicService) {
        if (cardCover == null || portraitLyricsPanel == null) return;
        HapticHelper.segmentTick(activityRoot);

        float density = activityRoot.getResources().getDisplayMetrics().density;
        float screenWidthPx = activityRoot.getResources().getDisplayMetrics().widthPixels;
        boolean isPortraitMode = activityRoot.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT;

        if (isPortraitLyricsOpen) {
            closeAllPortraitPanels(visualizerController, musicService);
            return;
        }

        if (isPortraitSongListOpen) {
            // Song list was open, switch smoothly to Lyrics without moving header
            isPortraitSongListOpen = false;
            isPortraitLyricsOpen = true;

            if (btnSongListToggle != null) btnSongListToggle.setColorFilter(0xB3FFFFFF);
            if (btnLyrics != null) btnLyrics.setColorFilter(0xFF00FFCC);

            if (portraitSongListPanel != null) {
                portraitSongListPanel.animate().cancel();
                portraitSongListPanel.animate()
                        .alpha(0f)
                        .translationY(40f * density)
                        .setDuration(180)
                        .withEndAction(() -> {
                            if (portraitSongListPanel != null) portraitSongListPanel.setVisibility(View.GONE);
                        })
                        .start();
            }

            portraitLyricsPanel.setVisibility(View.VISIBLE);
            portraitLyricsPanel.setTranslationY(40f * density);
            portraitLyricsPanel.setAlpha(0f);
            portraitLyricsPanel.animate().cancel();
            portraitLyricsPanel.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(240)
                    .setInterpolator(new DecelerateInterpolator(1.6f))
                    .start();

            if (visualizerController != null) {
                visualizerController.updateVisualizerState(true, isPortraitMode, musicService);
            }
            return;
        }

        // Neither was open: Full animation from player to Header + Lyrics Panel
        isPortraitLyricsOpen = true;
        if (btnLyrics != null) btnLyrics.setColorFilter(0xFF00FFCC);
        if (btnSongListToggle != null) btnSongListToggle.setColorFilter(0xB3FFFFFF);

        animateHeaderToTop(density, screenWidthPx);

        if (headerVisualizer != null) {
            headerVisualizer.setVisibility(View.VISIBLE);
            headerVisualizer.setAlpha(0f);
            headerVisualizer.animate().cancel();
            headerVisualizer.animate().alpha(1f).setDuration(340).start();
        }
        if (visualizerController != null) {
            visualizerController.updateVisualizerState(true, isPortraitMode, musicService);
        }

        if (portraitSongListPanel != null) portraitSongListPanel.setVisibility(View.GONE);

        portraitLyricsPanel.setVisibility(View.VISIBLE);
        portraitLyricsPanel.setTranslationY(320f * density);
        portraitLyricsPanel.setAlpha(0f);
        portraitLyricsPanel.animate().cancel();
        portraitLyricsPanel.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(340)
                .setInterpolator(new DecelerateInterpolator(1.8f))
                .start();
    }

    public void closeAllPortraitPanels(VisualizerController visualizerController, MusicService musicService) {
        if (cardCover == null) return;
        HapticHelper.gestureEnd(activityRoot);

        float density = activityRoot.getResources().getDisplayMetrics().density;
        boolean isPortraitMode = activityRoot.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT;

        isPortraitSongListOpen = false;
        isPortraitLyricsOpen = false;

        if (btnSongListToggle != null) btnSongListToggle.setColorFilter(0xB3FFFFFF);
        if (btnLyrics != null) btnLyrics.setColorFilter(0xB3FFFFFF);

        animateHeaderToCenter();

        if (headerVisualizer != null) {
            headerVisualizer.animate().cancel();
            headerVisualizer.animate().alpha(0f).setDuration(220).withEndAction(() -> headerVisualizer.setVisibility(View.GONE)).start();
        }
        if (visualizerController != null) {
            visualizerController.updateVisualizerState(false, isPortraitMode, musicService);
        }

        if (portraitSongListPanel != null) {
            portraitSongListPanel.animate().cancel();
            portraitSongListPanel.animate()
                    .translationY(320f * density)
                    .alpha(0f)
                    .setDuration(250)
                    .withEndAction(() -> portraitSongListPanel.setVisibility(View.GONE))
                    .start();
        }

        if (portraitLyricsPanel != null) {
            portraitLyricsPanel.animate().cancel();
            portraitLyricsPanel.animate()
                    .translationY(320f * density)
                    .alpha(0f)
                    .setDuration(250)
                    .withEndAction(() -> portraitLyricsPanel.setVisibility(View.GONE))
                    .start();
        }
    }

    private void animateHeaderToTop(float density, float screenWidthPx) {
        cardCover.bringToFront();
        cardCover.setPivotX(cardCover.getWidth() / 2f);
        cardCover.setPivotY(cardCover.getHeight() / 2f);
        cardCover.setRotation(0f);

        float coverTargetX = getCoverTargetX(density, screenWidthPx);
        float coverTargetY = getCoverTargetY(density);

        float titleTargetX = getTitleTargetX(density, screenWidthPx);
        float titleTargetY = getTitleTargetY(density);

        float artistTargetX = getArtistTargetX(density, screenWidthPx);
        float artistTargetY = getArtistTargetY(density);

        int maxTextWidth = (int) (screenWidthPx - (144f * density) - (115f * density));
        if (maxTextWidth > (80f * density)) {
            if (tvTitleView != null) {
                ViewGroup.LayoutParams lp = tvTitleView.getLayoutParams();
                lp.width = maxTextWidth;
                tvTitleView.setLayoutParams(lp);
            }
            if (tvArtistView != null) {
                ViewGroup.LayoutParams lp = tvArtistView.getLayoutParams();
                lp.width = maxTextWidth;
                tvArtistView.setLayoutParams(lp);
            }
        }

        cardCover.animate().cancel();
        cardCover.animate()
                .scaleX(0.148f)
                .scaleY(0.148f)
                .translationX(coverTargetX)
                .translationY(coverTargetY)
                .setDuration(340)
                .setInterpolator(new DecelerateInterpolator(1.8f))
                .start();

        if (tvTitleView != null) {
            tvTitleView.animate().cancel();
            tvTitleView.animate()
                    .translationX(titleTargetX)
                    .translationY(titleTargetY)
                    .scaleX(0.68f)
                    .scaleY(0.68f)
                    .setDuration(340)
                    .setInterpolator(new DecelerateInterpolator(1.8f))
                    .start();
        }

        if (tvArtistView != null) {
            tvArtistView.animate().cancel();
            tvArtistView.animate()
                    .translationX(artistTargetX)
                    .translationY(artistTargetY)
                    .scaleX(0.68f)
                    .scaleY(0.68f)
                    .setDuration(340)
                    .setInterpolator(new DecelerateInterpolator(1.8f))
                    .start();
        }
    }

    private void animateHeaderToCenter() {
        if (tvTitleView != null) {
            ViewGroup.LayoutParams lp = tvTitleView.getLayoutParams();
            lp.width = 0;
            tvTitleView.setLayoutParams(lp);
        }
        if (tvArtistView != null) {
            ViewGroup.LayoutParams lp = tvArtistView.getLayoutParams();
            lp.width = 0;
            tvArtistView.setLayoutParams(lp);
        }

        cardCover.animate().cancel();
        cardCover.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator(1.8f))
                .start();

        if (tvTitleView != null) {
            tvTitleView.animate().cancel();
            tvTitleView.animate()
                    .translationX(0f)
                    .translationY(0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(300)
                    .setInterpolator(new DecelerateInterpolator(1.8f))
                    .start();
        }
        if (tvArtistView != null) {
            tvArtistView.animate().cancel();
            tvArtistView.animate()
                    .translationX(0f)
                    .translationY(0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(300)
                    .setInterpolator(new DecelerateInterpolator(1.8f))
                    .start();
        }
    }

    public void expandSearchRectangle(boolean isPortraitMode) {
        HapticHelper.lightTick(searchBarContainer);
        if (isPortraitMode) {
            if (searchBarContainer != null && searchBarContainer.getVisibility() != View.VISIBLE) {
                searchBarContainer.animate().cancel();
                searchBarContainer.setPivotX(0f);
                searchBarContainer.setPivotY(0f);
                searchBarContainer.setScaleX(0.1f);
                searchBarContainer.setScaleY(0.1f);
                searchBarContainer.setAlpha(0f);
                searchBarContainer.setVisibility(View.VISIBLE);

                searchBarContainer.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .alpha(1.0f)
                        .setDuration(280)
                        .setInterpolator(new DecelerateInterpolator(1.5f))
                        .start();
            }

            if (searchDivider != null) searchDivider.setVisibility(View.VISIBLE);
            if (rvSearchResults != null) rvSearchResults.setVisibility(View.VISIBLE);
            if (btnSearchClose != null) btnSearchClose.setVisibility(View.VISIBLE);

            if (btnSearchToggle != null && btnSearchToggle.getVisibility() == View.VISIBLE) {
                btnSearchToggle.animate().cancel();
                btnSearchToggle.animate().alpha(0f).setDuration(180).withEndAction(() -> btnSearchToggle.setVisibility(View.GONE)).start();
            }
            if (btnSettings != null && btnSettings.getVisibility() == View.VISIBLE) {
                btnSettings.animate().cancel();
                btnSettings.animate().alpha(0f).setDuration(180).withEndAction(() -> btnSettings.setVisibility(View.GONE)).start();
            }
        } else {
            if (activityRoot instanceof ViewGroup) {
                TransitionManager.beginDelayedTransition((ViewGroup) activityRoot, new AutoTransition().setDuration(220));
            }

            if (searchBarContainer != null) {
                searchBarContainer.setVisibility(View.VISIBLE);
                searchBarContainer.setBackgroundResource(R.drawable.bg_search_container_dark);
            }
            if (searchDivider != null) searchDivider.setVisibility(View.VISIBLE);
            if (rvSearchResults != null) rvSearchResults.setVisibility(View.VISIBLE);
            if (btnSearchClose != null) btnSearchClose.setVisibility(View.VISIBLE);
        }
    }

    public void collapseSearchRectangle(EditText etSearch, boolean isPortraitMode) {
        HapticHelper.lightTick(searchBarContainer);
        if (isPortraitMode) {
            if (searchBarContainer != null && searchBarContainer.getVisibility() == View.VISIBLE) {
                searchBarContainer.animate().cancel();
                searchBarContainer.setPivotX(0f);
                searchBarContainer.setPivotY(0f);

                searchBarContainer.animate()
                        .scaleX(0.1f)
                        .scaleY(0.1f)
                        .alpha(0f)
                        .setDuration(220)
                        .setInterpolator(new AccelerateInterpolator(1.5f))
                        .withEndAction(() -> {
                            if (searchBarContainer != null) {
                                searchBarContainer.setVisibility(View.GONE);
                                searchBarContainer.setScaleX(1.0f);
                                searchBarContainer.setScaleY(1.0f);
                                searchBarContainer.setAlpha(1.0f);
                            }
                        })
                        .start();
            }

            if (btnSearchToggle != null) {
                btnSearchToggle.animate().cancel();
                btnSearchToggle.setVisibility(View.VISIBLE);
                btnSearchToggle.setAlpha(0f);
                btnSearchToggle.animate().alpha(1.0f).setDuration(220).start();
            }
            if (btnSettings != null) {
                btnSettings.animate().cancel();
                btnSettings.setVisibility(View.VISIBLE);
                btnSettings.setAlpha(0f);
                btnSettings.animate().alpha(1.0f).setDuration(220).start();
            }
        } else {
            if (activityRoot instanceof ViewGroup) {
                TransitionManager.beginDelayedTransition((ViewGroup) activityRoot, new AutoTransition().setDuration(200));
            }

            if (searchDivider != null) searchDivider.setVisibility(View.GONE);
            if (rvSearchResults != null) rvSearchResults.setVisibility(View.GONE);
            if (searchBarContainer != null) {
                searchBarContainer.setVisibility(View.VISIBLE);
                searchBarContainer.setBackgroundResource(R.drawable.bg_glassmorphic_card);
            }
            if (btnSearchClose != null) btnSearchClose.setVisibility(View.GONE);
        }

        if (etSearch != null) {
            etSearch.setText("");
            etSearch.clearFocus();
            InputMethodManager imm = (InputMethodManager) activityRoot.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
            }
        }
    }

    public float getCoverTargetX(float density, float screenWidthPx) {
        if (cardCover == null) return 0f;
        float coverCurrentCenterX = cardCover.getLeft() > 0 ? cardCover.getLeft() + (cardCover.getWidth() / 2f) : screenWidthPx / 2f;
        return (114f * density) - coverCurrentCenterX;
    }

    public float getCoverTargetY(float density) {
        if (cardCover == null) return 0f;
        float targetYLevelPx = 80f * density;
        float coverCurrentCenterYPx = cardCover.getTop() > 0 ? cardCover.getTop() + (cardCover.getHeight() / 2f) : 270.5f * density;
        return targetYLevelPx - coverCurrentCenterYPx;
    }

    public float getTitleTargetX(float density, float screenWidthPx) {
        if (tvTitleView == null) return 0f;
        float titleCurrentCenterX = tvTitleView.getLeft() > 0 ? tvTitleView.getLeft() + (tvTitleView.getWidth() / 2f) : screenWidthPx / 2f;
        return (144f * density) - titleCurrentCenterX + (70f * density);
    }

    public float getTitleTargetY(float density) {
        if (tvTitleView == null) return 0f;
        float targetYLevelPx = 80f * density;
        float titleCurrentCenterY = tvTitleView.getTop() > 0 ? tvTitleView.getTop() + (tvTitleView.getHeight() / 2f) : 580f * density;
        return (targetYLevelPx - (6f * density)) - titleCurrentCenterY;
    }

    public float getArtistTargetX(float density, float screenWidthPx) {
        if (tvArtistView == null) return 0f;
        float artistCurrentCenterX = tvArtistView.getLeft() > 0 ? tvArtistView.getLeft() + (tvArtistView.getWidth() / 2f) : screenWidthPx / 2f;
        return (144f * density) - artistCurrentCenterX + (70f * density);
    }

    public float getArtistTargetY(float density) {
        if (tvArtistView == null) return 0f;
        float targetYLevelPx = 80f * density;
        float artistCurrentCenterY = tvArtistView.getTop() > 0 ? tvArtistView.getTop() + (tvArtistView.getHeight() / 2f) : 610f * density;
        return (targetYLevelPx + (10f * density)) - artistCurrentCenterY;
    }

    public void setMiniHeaderOffset(float offsetPx, float alpha) {
        float density = activityRoot.getResources().getDisplayMetrics().density;
        float screenWidthPx = activityRoot.getResources().getDisplayMetrics().widthPixels;

        float coverBaseX = getCoverTargetX(density, screenWidthPx);
        float coverBaseY = getCoverTargetY(density);
        float titleBaseX = getTitleTargetX(density, screenWidthPx);
        float titleBaseY = getTitleTargetY(density);
        float artistBaseX = getArtistTargetX(density, screenWidthPx);
        float artistBaseY = getArtistTargetY(density);

        if (cardCover != null) {
            cardCover.setTranslationX(coverBaseX + offsetPx);
            cardCover.setTranslationY(coverBaseY);
            cardCover.setScaleX(0.148f);
            cardCover.setScaleY(0.148f);
            cardCover.setAlpha(alpha);
        }
        if (tvTitleView != null) {
            tvTitleView.setTranslationX(titleBaseX + offsetPx);
            tvTitleView.setTranslationY(titleBaseY);
            tvTitleView.setScaleX(0.68f);
            tvTitleView.setScaleY(0.68f);
            tvTitleView.setAlpha(alpha);
        }
        if (tvArtistView != null) {
            tvArtistView.setTranslationX(artistBaseX + offsetPx);
            tvArtistView.setTranslationY(artistBaseY);
            tvArtistView.setScaleX(0.68f);
            tvArtistView.setScaleY(0.68f);
            tvArtistView.setAlpha(alpha * 0.85f);
        }
        if (headerVisualizer != null) {
            headerVisualizer.setTranslationX(offsetPx);
            headerVisualizer.setTranslationY(0f);
            headerVisualizer.setAlpha(alpha);
        }
    }

    public void animateMiniHeaderTrackSwitch(boolean isNext, MusicService musicService) {
        float density = activityRoot.getResources().getDisplayMetrics().density;
        float screenWidthPx = activityRoot.getResources().getDisplayMetrics().widthPixels;

        float coverBaseX = getCoverTargetX(density, screenWidthPx);
        float titleBaseX = getTitleTargetX(density, screenWidthPx);
        float artistBaseX = getArtistTargetX(density, screenWidthPx);

        float exitOffset = isNext ? (-screenWidthPx * 0.6f) : (screenWidthPx * 0.6f);
        float enterOffset = -exitOffset;

        View anchorView = cardCover != null ? cardCover : (tvTitleView != null ? tvTitleView : activityRoot);

        if (cardCover != null) {
            cardCover.animate().cancel();
            cardCover.animate().translationX(coverBaseX + exitOffset).alpha(0f).setDuration(160).setInterpolator(new AccelerateInterpolator(1.4f)).start();
        }
        if (tvTitleView != null) {
            tvTitleView.animate().cancel();
            tvTitleView.animate().translationX(titleBaseX + exitOffset).alpha(0f).setDuration(160).setInterpolator(new AccelerateInterpolator(1.4f)).start();
        }
        if (tvArtistView != null) {
            tvArtistView.animate().cancel();
            tvArtistView.animate().translationX(artistBaseX + exitOffset).alpha(0f).setDuration(160).setInterpolator(new AccelerateInterpolator(1.4f)).start();
        }
        if (headerVisualizer != null) {
            headerVisualizer.animate().cancel();
            headerVisualizer.animate().translationX(exitOffset).alpha(0f).setDuration(160).setInterpolator(new AccelerateInterpolator(1.4f)).start();
        }

        anchorView.animate().cancel();
        anchorView.animate()
                .setDuration(160)
                .withEndAction(() -> {
                    if (musicService != null) {
                        if (isNext) {
                            musicService.playNextSong();
                        } else {
                            musicService.playPreviousSong();
                        }
                    }

                    setMiniHeaderOffset(enterOffset, 0f);

                    if (cardCover != null) {
                        cardCover.animate().cancel();
                        cardCover.animate().translationX(coverBaseX).alpha(1.0f).setDuration(240).setInterpolator(new DecelerateInterpolator(1.6f)).start();
                    }
                    if (tvTitleView != null) {
                        tvTitleView.animate().cancel();
                        tvTitleView.animate().translationX(titleBaseX).alpha(1.0f).setDuration(240).setInterpolator(new DecelerateInterpolator(1.6f)).start();
                    }
                    if (tvArtistView != null) {
                        tvArtistView.animate().cancel();
                        tvArtistView.animate().translationX(artistBaseX).alpha(0.85f).setDuration(240).setInterpolator(new DecelerateInterpolator(1.6f)).start();
                    }
                    if (headerVisualizer != null) {
                        headerVisualizer.animate().cancel();
                        headerVisualizer.animate().translationX(0f).alpha(1.0f).setDuration(240).setInterpolator(new DecelerateInterpolator(1.6f)).start();
                    }
                })
                .start();
    }

    public void animateMiniHeaderReset() {
        float density = activityRoot.getResources().getDisplayMetrics().density;
        float screenWidthPx = activityRoot.getResources().getDisplayMetrics().widthPixels;

        float coverBaseX = getCoverTargetX(density, screenWidthPx);
        float titleBaseX = getTitleTargetX(density, screenWidthPx);
        float artistBaseX = getArtistTargetX(density, screenWidthPx);

        if (cardCover != null) {
            cardCover.animate().cancel();
            cardCover.animate().translationX(coverBaseX).alpha(1.0f).setDuration(200).setInterpolator(new DecelerateInterpolator(1.5f)).start();
        }
        if (tvTitleView != null) {
            tvTitleView.animate().cancel();
            tvTitleView.animate().translationX(titleBaseX).alpha(1.0f).setDuration(200).setInterpolator(new DecelerateInterpolator(1.5f)).start();
        }
        if (tvArtistView != null) {
            tvArtistView.animate().cancel();
            tvArtistView.animate().translationX(artistBaseX).alpha(0.85f).setDuration(200).setInterpolator(new DecelerateInterpolator(1.5f)).start();
        }
        if (headerVisualizer != null) {
            headerVisualizer.animate().cancel();
            headerVisualizer.animate().translationX(0f).alpha(1.0f).setDuration(200).setInterpolator(new DecelerateInterpolator(1.5f)).start();
        }
    }

    public View.OnTouchListener createSwipeTouchListener(VisualizerController visualizerController, MusicServiceSupplier musicServiceSupplier, OnSwipeActionListener listener) {
        return new View.OnTouchListener() {
            private float startY = 0f;
            private float startX = 0f;
            private int dragDirection = 0; // 0 = undecided, 1 = vertical, 2 = horizontal
            private Song loadedIncomingSong = null;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                boolean isPortraitMode = activityRoot.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT;
                if (!isPortraitMode) return false;

                float density = activityRoot.getResources().getDisplayMetrics().density;
                float totalDragPx = 200f * density;
                boolean isCardCoverTouch = (cardCover != null && v == cardCover);
                MusicService musicService = musicServiceSupplier != null ? musicServiceSupplier.getMusicService() : null;

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = event.getRawY();
                        startX = event.getRawX();
                        dragDirection = 0;
                        loadedIncomingSong = null;
                        if (cardCover != null) {
                            cardCover.bringToFront();
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaY = event.getRawY() - startY;
                        float deltaX = event.getRawX() - startX;

                        if (dragDirection == 0) {
                            if (Math.abs(deltaY) > (8f * density) && Math.abs(deltaY) > Math.abs(deltaX)) {
                                dragDirection = 1; // Vertical (panel/popup player)
                            } else if (Math.abs(deltaX) > (8f * density) && Math.abs(deltaX) > Math.abs(deltaY)) {
                                if (!isAnyPortraitPanelOpen()) {
                                    if (isCardCoverTouch) {
                                        dragDirection = 2; // Horizontal (album cover circular arc)
                                    }
                                } else {
                                    float rawY = event.getRawY();
                                    boolean isHeaderTouch = (v == cardCover || v == tvTitleView || v == tvArtistView || v == headerVisualizer || rawY < (120f * density));
                                    if (isHeaderTouch) {
                                        dragDirection = 3; // Horizontal (mini song details swipe next/prev)
                                    }
                                }
                            }
                        }

                        if (dragDirection == 1) {
                            float currentFraction;
                            if (!isAnyPortraitPanelOpen()) {
                                currentFraction = -deltaY / totalDragPx;
                            } else {
                                currentFraction = 1.0f - (deltaY / totalDragPx);
                            }
                            applySwipeFraction(currentFraction);
                            return true;
                        } else if (dragDirection == 2 && cardCover != null) {
                            float screenWidth = activityRoot.getResources().getDisplayMetrics().widthPixels;
                            float screenHeight = activityRoot.getResources().getDisplayMetrics().heightPixels;

                            float pivotX = cardCover.getWidth() / 2f;
                            float pivotY = screenHeight - cardCover.getTop();

                            cardCover.setPivotX(pivotX);
                            cardCover.setPivotY(pivotY);

                            float rotationCurrent = (deltaX / screenWidth) * 55f;
                            cardCover.setRotation(rotationCurrent);

                            if (musicService != null && cardCoverIncoming != null && coverImageIncoming != null) {
                                boolean isNext = deltaX < 0;
                                Song targetSong = isNext ? musicService.getNextSong() : musicService.getPreviousSong();

                                if (targetSong != null && targetSong != loadedIncomingSong) {
                                    loadedIncomingSong = targetSong;
                                    String baseUrl = ServerPrefs.getServerUrl(activityRoot.getContext());
                                    String coverUrl = ServerPrefs.formatCoverUrl(baseUrl, targetSong.getCoverPath());

                                    boolean isOffline = ServerPrefs.isOffline(activityRoot.getContext());
                                    boolean cacheOnly = isOffline && (coverUrl.startsWith("http://") || coverUrl.startsWith("https://"));
                                    com.bumptech.glide.Glide.with(activityRoot.getContext())
                                            .load(coverUrl)
                                            .onlyRetrieveFromCache(cacheOnly)
                                            .placeholder(R.drawable.mustre)
                                            .error(R.drawable.mustre)
                                            .into(coverImageIncoming);
                                }

                                cardCoverIncoming.setPivotX(pivotX);
                                cardCoverIncoming.setPivotY(pivotY);
                                cardCoverIncoming.setVisibility(View.VISIBLE);

                                float rotationIncoming = (isNext ? 60f : -60f) + rotationCurrent;
                                cardCoverIncoming.setRotation(rotationIncoming);
                            }
                            return true;
                        } else if (dragDirection == 3) {
                            float clampedDeltaX = deltaX * 0.7f;
                            float progress = Math.min(1.0f, Math.abs(deltaX) / (60f * density));
                            float alpha = 1.0f - (progress * 0.3f);
                            setMiniHeaderOffset(clampedDeltaX, alpha);
                            return true;
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (dragDirection == 1) {
                            dragDirection = 0;
                            float deltaYUp = event.getRawY() - startY;
                            if (!isAnyPortraitPanelOpen()) {
                                float currentFraction = -deltaYUp / totalDragPx;
                                if (currentFraction > 0.25f || deltaYUp < -60f) {
                                    HapticHelper.gestureEnd(activityRoot);
                                    animateToState(true, visualizerController, musicService);
                                } else {
                                    animateToState(false, visualizerController, musicService);
                                    if (deltaYUp > 60f && ServerPrefs.isPopupPlayerEnabled(activityRoot.getContext()) && musicService != null && musicService.hasSongs() && listener != null) {
                                        HapticHelper.gestureEnd(activityRoot);
                                        listener.onFloatingPlayerDrop((int) event.getRawX(), (int) event.getRawY());
                                    }
                                }
                            } else {
                                if (deltaYUp > 60f) {
                                    HapticHelper.gestureEnd(activityRoot);
                                    closeAllPortraitPanels(visualizerController, musicService);
                                } else {
                                    animateToState(true, visualizerController, musicService);
                                }
                            }
                            return true;
                        } else if (dragDirection == 2 && cardCover != null) {
                            dragDirection = 0;
                            float deltaXUp = event.getRawX() - startX;
                            boolean isNext = deltaXUp < -(40f * density);
                            boolean isPrevious = deltaXUp > (40f * density);

                            if ((isNext || isPrevious) && cardCoverIncoming != null) {
                                HapticHelper.segmentTick(cardCover);
                                float exitRotation = isNext ? -65f : 65f;

                                cardCover.animate().cancel();
                                cardCover.animate()
                                        .rotation(exitRotation)
                                        .setDuration(220)
                                        .setInterpolator(new AccelerateInterpolator(1.4f))
                                        .start();

                                cardCoverIncoming.animate().cancel();
                                cardCoverIncoming.animate()
                                        .rotation(0f)
                                        .setDuration(240)
                                        .setInterpolator(new DecelerateInterpolator(1.6f))
                                        .withEndAction(() -> {
                                            if (musicService != null) {
                                                if (isNext) {
                                                    musicService.playNextSong();
                                                } else {
                                                    musicService.playPreviousSong();
                                                }
                                            }
                                            cardCover.setPivotX(cardCover.getWidth() / 2f);
                                            cardCover.setPivotY(cardCover.getHeight() / 2f);
                                            cardCover.setRotation(0f);
                                            cardCover.setTranslationX(0f);
                                            cardCover.setTranslationY(0f);
                                            if (cardCoverIncoming != null) {
                                                cardCoverIncoming.setVisibility(View.GONE);
                                                cardCoverIncoming.setRotation(0f);
                                            }
                                        })
                                        .start();
                            } else {
                                cardCover.animate()
                                        .rotation(0f)
                                        .setDuration(200)
                                        .setInterpolator(new DecelerateInterpolator(1.5f))
                                        .withEndAction(() -> {
                                            cardCover.setPivotX(cardCover.getWidth() / 2f);
                                            cardCover.setPivotY(cardCover.getHeight() / 2f);
                                            cardCover.setTranslationX(0f);
                                            cardCover.setTranslationY(0f);
                                        })
                                        .start();

                                if (cardCoverIncoming != null) {
                                    cardCoverIncoming.animate()
                                            .rotation(deltaXUp < 0 ? 65f : -65f)
                                            .setDuration(200)
                                            .withEndAction(() -> cardCoverIncoming.setVisibility(View.GONE))
                                            .start();
                                }
                            }
                            return true;
                        } else if (dragDirection == 3) {
                            dragDirection = 0;
                            float deltaXUp = event.getRawX() - startX;
                            float threshold = 35f * density;
                            if (deltaXUp > threshold) {
                                // Swipe right -> Previous song
                                HapticHelper.segmentTick(activityRoot);
                                animateMiniHeaderTrackSwitch(false, musicService);
                            } else if (deltaXUp < -threshold) {
                                // Swipe left -> Next song
                                HapticHelper.segmentTick(activityRoot);
                                animateMiniHeaderTrackSwitch(true, musicService);
                            } else {
                                animateMiniHeaderReset();
                            }
                            return true;
                        } else if (event.getAction() == MotionEvent.ACTION_UP) {
                            v.performClick();
                            return true;
                        }
                        break;
                }
                return false;
            }
        };
    }
}
