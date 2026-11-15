package com.aadith.mustre.lyrics;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.aadith.mustre.R;
import com.aadith.mustre.model.LyricLine;
import com.aadith.mustre.model.LyricWord;
import com.aadith.mustre.util.HapticHelper;
import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public class LyricsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // Lyric Color Constants
    public static final int COLOR_ACTIVE = 0xFFFFFFFF;        // Pure bright white
    public static final int COLOR_REACHED = 0xFFFFFFFF;       // Crisp white (reached / already sung)
    public static final int COLOR_UNREACHED = 0xFFB2B2B2;     // Subtle light gray (not reached yet)

    // Lyric Text Sizes (SP) for Focus Hierarchy
    public static final float TEXT_SIZE_ACTIVE_SP = 28f;      // Current playing line (central)
    public static final float TEXT_SIZE_NEIGHBOR_SP = 24f;    // Just played and about to play (central 3)
    public static final float TEXT_SIZE_DISTANT_SP = 18f;     // Distant lines (outer context)

    private static final int VIEW_TYPE_LINE = 0;
    private static final int VIEW_TYPE_FOOTER = 1;
    public static final String PAYLOAD_WORD_HIGHLIGHT = "payload_word_highlight";
    public static final String PAYLOAD_STATE_CHANGE = "payload_state_change";
    private static final DecelerateInterpolator FLUID_INTERPOLATOR = new DecelerateInterpolator(2.0f);

    public interface OnLyricLineClickListener {
        void onLyricLineClick(LyricLine line, int position, long targetTimeMs);
    }

    private final List<LyricLine> lines = new ArrayList<>();
    private final OnLyricLineClickListener listener;
    private int activePosition = -1;
    private long currentPositionMs = 0;
    private int lastHapticLineIndex = -1;
    private int lastHapticWordIndex = -1;
    private Context context;

    public LyricsAdapter(OnLyricLineClickListener listener) {
        this.listener = listener;
    }

    public void setLines(List<LyricLine> newLines) {
        this.lines.clear();
        if (newLines != null) {
            this.lines.addAll(newLines);
        }
        this.activePosition = -1;
        this.currentPositionMs = 0;
        this.lastHapticLineIndex = -1;
        this.lastHapticWordIndex = -1;
        notifyDataSetChanged();
    }

    public int getActivePosition() {
        return activePosition;
    }

    public void setActivePosition(int newActivePosition) {
        if (this.activePosition != newActivePosition) {
            int old = this.activePosition;
            this.activePosition = newActivePosition;
            notifyLinesChanged(old, newActivePosition);
        }
    }

    public void setCurrentPositionMs(long positionMs) {
        this.currentPositionMs = positionMs;
        if (activePosition >= 0 && activePosition < lines.size()) {
            LyricLine activeLine = lines.get(activePosition);
            if (activeLine != null && activeLine.hasWords()) {
                notifyItemChanged(activePosition, PAYLOAD_WORD_HIGHLIGHT);
            }
        }
    }

    public void updatePlaybackPosition(int newActivePosition, long positionMs) {
        this.currentPositionMs = positionMs;
        if (this.activePosition != newActivePosition) {
            int old = this.activePosition;
            this.activePosition = newActivePosition;
            notifyLinesChanged(old, newActivePosition);

            if (newActivePosition >= 0 && newActivePosition < lines.size()) {
                LyricLine newLine = lines.get(newActivePosition);
                lastHapticWordIndex = -1;
                if (newActivePosition != lastHapticLineIndex) {
                    lastHapticLineIndex = newActivePosition;
                    if (context != null) {
                        HapticHelper.performLyricLineHaptic(context);
                        if (newLine != null && newLine.hasWords()) {
                            checkWordHaptic(newLine, positionMs);
                        }
                    }
                }
            }
        } else if (activePosition >= 0 && activePosition < lines.size()) {
            LyricLine activeLine = lines.get(activePosition);
            if (activeLine != null && activeLine.hasWords()) {
                checkWordHaptic(activeLine, positionMs);
                notifyItemChanged(activePosition, PAYLOAD_WORD_HIGHLIGHT);
            }
        }
    }

    private void checkWordHaptic(LyricLine activeLine, long positionMs) {
        if (activeLine == null || !activeLine.hasWords() || context == null) return;
        List<LyricWord> words = activeLine.getWords();
        if (words == null || words.isEmpty()) return;

        int activeWordIdx = -1;
        for (int i = 0; i < words.size(); i++) {
            LyricWord w = words.get(i);
            if (positionMs >= w.getStartMs() && positionMs < w.getEndMs()) {
                activeWordIdx = i;
                break;
            }
        }

        if (activeWordIdx != -1 && activeWordIdx != lastHapticWordIndex) {
            lastHapticWordIndex = activeWordIdx;
            HapticHelper.performLyricWordHaptic(context);
        }
    }

    public static float getTextSizeForPosition(int position, int activePosition) {
        if (position == activePosition) {
            return TEXT_SIZE_ACTIVE_SP;
        } else if ((activePosition >= 0 && (position == activePosition - 1 || position == activePosition + 1))
                || (activePosition < 0 && position == 0)) {
            return TEXT_SIZE_NEIGHBOR_SP;
        } else {
            return TEXT_SIZE_DISTANT_SP;
        }
    }

    public static LyricWord findWordAtCharOffset(LyricLine line, int charOffset) {
        if (line == null || !line.hasWords() || charOffset < 0) return null;
        List<LyricWord> words = line.getWords();
        if (words == null || words.isEmpty()) return null;

        for (LyricWord w : words) {
            if (charOffset >= w.getCharStart() && charOffset <= w.getCharEnd()) {
                return w;
            }
        }

        LyricWord closest = null;
        int minDistance = Integer.MAX_VALUE;
        for (LyricWord w : words) {
            int dist;
            if (charOffset < w.getCharStart()) {
                dist = w.getCharStart() - charOffset;
            } else if (charOffset > w.getCharEnd()) {
                dist = charOffset - w.getCharEnd();
            } else {
                dist = 0;
            }
            if (dist < minDistance) {
                minDistance = dist;
                closest = w;
            }
        }
        return closest;
    }

    private void notifyLinesChanged(int oldPos, int newPos) {
        if (oldPos == newPos) return;
        if (lines.isEmpty()) return;

        if (oldPos < 0) {
            if (newPos >= 0 && newPos < lines.size()) {
                int end = Math.min(lines.size(), newPos + 2);
                notifyItemRangeChanged(0, end, PAYLOAD_STATE_CHANGE);
            }
            return;
        }
        if (newPos < 0) {
            int end = Math.min(lines.size(), oldPos + 2);
            notifyItemRangeChanged(0, end, PAYLOAD_STATE_CHANGE);
            return;
        }
        int min = Math.max(0, Math.min(oldPos, newPos) - 1);
        int max = Math.min(lines.size() - 1, Math.max(oldPos, newPos) + 1);
        int count = max - min + 1;
        notifyItemRangeChanged(min, count, PAYLOAD_STATE_CHANGE);
    }

    public boolean hasActiveWordSync() {
        if (activePosition >= 0 && activePosition < lines.size()) {
            LyricLine activeLine = lines.get(activePosition);
            return activeLine != null && activeLine.hasWords();
        }
        return false;
    }

    public LyricLine getLine(int position) {
        if (position >= 0 && position < lines.size()) {
            return lines.get(position);
        }
        return null;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == lines.size()) {
            return VIEW_TYPE_FOOTER;
        }
        return VIEW_TYPE_LINE;
    }

    @Override
    public int getItemCount() {
        return lines.isEmpty() ? 0 : lines.size() + 1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (context == null) {
            context = parent.getContext();
        }
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_FOOTER) {
            View view = inflater.inflate(R.layout.item_lyrics_footer, parent, false);
            return new FooterViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_lyrics_line, parent, false);
            return new LineViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && holder instanceof LineViewHolder) {
            LineViewHolder lineHolder = (LineViewHolder) holder;
            if (payloads.contains(PAYLOAD_WORD_HIGHLIGHT)) {
                if (position == activePosition && position < lines.size()) {
                    LyricLine line = lines.get(position);
                    if (line != null && line.hasWords()) {
                        applyWordHighlight(lineHolder.tvLyricLine, line, currentPositionMs);
                        return;
                    }
                }
            } else if (payloads.contains(PAYLOAD_STATE_CHANGE)) {
                bindLineHolder(lineHolder, position, true);
                return;
            }
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof LineViewHolder) {
            bindLineHolder((LineViewHolder) holder, position, false);
        }
    }

    private void bindLineHolder(LineViewHolder lineHolder, int position, boolean animate) {
        LyricLine line = lines.get(position);
        boolean isActive = (position == activePosition);
        boolean isReached = (activePosition >= 0 && position < activePosition);
        boolean isJustPlayed = (activePosition >= 0 && position == activePosition - 1);
        boolean isAboutToPlay = (activePosition >= 0 ? position == activePosition + 1 : position == 0);
        boolean isCentralNeighbor = isJustPlayed || isAboutToPlay;

        lineHolder.itemView.setOnClickListener(v -> {
            HapticHelper.lightTick(v);
            if (listener != null) {
                long targetTimeMs = line.getTimeMs();
                if (line.hasWords() && lineHolder.lastTouchX >= 0 && lineHolder.lastTouchY >= 0) {
                    int charOffset = lineHolder.tvLyricLine.getOffsetForPosition(lineHolder.lastTouchX, lineHolder.lastTouchY);
                    if (charOffset >= 0) {
                        LyricWord clickedWord = findWordAtCharOffset(line, charOffset);
                        if (clickedWord != null && clickedWord.getStartMs() > 0) {
                            targetTimeMs = clickedWord.getStartMs();
                        }
                    }
                }
                listener.onLyricLineClick(line, position, targetTimeMs);
            }
        });

        // Cancel previous animator if running
        lineHolder.tvLyricLine.animate().cancel();
        lineHolder.tvLyricLine.setPivotX(0f);
        lineHolder.tvLyricLine.setPivotY(lineHolder.tvLyricLine.getHeight() > 0 ? (lineHolder.tvLyricLine.getHeight() / 2f) : 0f);

        if (line.isEmpty()) {
            lineHolder.tvLyricLine.setText("♪  ♪  ♪");
            lineHolder.tvLyricLine.setTextColor(isActive ? COLOR_ACTIVE : (isReached ? COLOR_REACHED : COLOR_UNREACHED));
            float textSize = isActive ? 24f : (isCentralNeighbor ? 20f : 16f);
            lineHolder.tvLyricLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
            lineHolder.tvLyricLine.setTypeface(null, Typeface.NORMAL);
            float targetAlpha = isActive ? 0.95f : (isCentralNeighbor ? (isReached ? 0.70f : 0.60f) : (isReached ? 0.45f : 0.35f));

            if (isActive) {
                lineHolder.tvLyricLine.setShadowLayer(10f, 0f, 0f, 0x80FFFFFF);
            } else {
                lineHolder.tvLyricLine.setShadowLayer(0f, 0f, 0f, 0);
            }

            if (animate && lineHolder.itemView.isAttachedToWindow()) {
                lineHolder.tvLyricLine.animate()
                        .alpha(targetAlpha)
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(400)
                        .setInterpolator(FLUID_INTERPOLATOR)
                        .start();
            } else {
                lineHolder.tvLyricLine.setAlpha(targetAlpha);
                lineHolder.tvLyricLine.setScaleX(1.0f);
                lineHolder.tvLyricLine.setScaleY(1.0f);
            }
            return;
        }

        float targetTextSize = getTextSizeForPosition(position, activePosition);
        float currentTextSizePx = lineHolder.tvLyricLine.getTextSize();
        float scaledDensity = lineHolder.tvLyricLine.getContext().getResources().getDisplayMetrics().scaledDensity;
        float currentSizeSp = scaledDensity > 0 ? (currentTextSizePx / scaledDensity) : targetTextSize;
        boolean sizeChanged = Math.abs(currentSizeSp - targetTextSize) > 0.5f;

        float targetAlpha;
        if (isActive) {
            targetAlpha = 1.0f;
            lineHolder.tvLyricLine.setTypeface(null, Typeface.BOLD);
            if (line.hasWords()) {
                lineHolder.tvLyricLine.setShadowLayer(0f, 0f, 0f, 0);
                applyWordHighlight(lineHolder.tvLyricLine, line, currentPositionMs);
            } else {
                lineHolder.tvLyricLine.setText(line.getText());
                lineHolder.tvLyricLine.setTextColor(COLOR_ACTIVE);
                lineHolder.tvLyricLine.setShadowLayer(14f, 0f, 0f, 0xB3FFFFFF);
            }
        } else {
            lineHolder.tvLyricLine.setText(line.getText());
            lineHolder.tvLyricLine.setTextColor(isReached ? COLOR_REACHED : COLOR_UNREACHED);
            lineHolder.tvLyricLine.setTypeface(null, Typeface.NORMAL);
            lineHolder.tvLyricLine.setShadowLayer(0f, 0f, 0f, 0);
            targetAlpha = isCentralNeighbor ? (isReached ? 0.85f : 0.80f) : (isReached ? 0.55f : 0.50f);
        }

        lineHolder.tvLyricLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, targetTextSize);

        if (animate && lineHolder.itemView.isAttachedToWindow()) {
            if (sizeChanged) {
                float initialScale = (targetTextSize > currentSizeSp) ? 0.92f : 1.06f;
                lineHolder.tvLyricLine.setScaleX(initialScale);
                lineHolder.tvLyricLine.setScaleY(initialScale);
            }
            lineHolder.tvLyricLine.animate()
                    .alpha(targetAlpha)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(420)
                    .setInterpolator(FLUID_INTERPOLATOR)
                    .start();
        } else {
            lineHolder.tvLyricLine.setAlpha(targetAlpha);
            lineHolder.tvLyricLine.setScaleX(1.0f);
            lineHolder.tvLyricLine.setScaleY(1.0f);
        }
    }

    public static class WordSyncState {
        public final int pastEnd;
        public final int currentStart;
        public final int currentEnd;
        public final int upcomingStart;
        public final boolean hasActiveWord;
        public final boolean allFinished;

        public WordSyncState(int pastEnd, int currentStart, int currentEnd, int upcomingStart, boolean hasActiveWord, boolean allFinished) {
            this.pastEnd = pastEnd;
            this.currentStart = currentStart;
            this.currentEnd = currentEnd;
            this.upcomingStart = upcomingStart;
            this.hasActiveWord = hasActiveWord;
            this.allFinished = allFinished;
        }
    }

    public static WordSyncState calculateWordSyncState(LyricLine line, long currentPositionMs) {
        if (line == null) {
            return new WordSyncState(0, -1, -1, 0, false, false);
        }
        String fullText = line.getText();
        int fullLen = fullText != null ? fullText.length() : 0;
        List<LyricWord> words = line.getWords();
        if (words == null || words.isEmpty()) {
            return new WordSyncState(fullLen, -1, -1, fullLen, false, true);
        }

        int currentWordIndex = -1;
        int lastSungWordIndex = -1;

        for (int i = 0; i < words.size(); i++) {
            LyricWord w = words.get(i);
            if (currentPositionMs >= w.getStartMs() && currentPositionMs < w.getEndMs()) {
                currentWordIndex = i;
                break;
            } else if (currentPositionMs >= w.getEndMs()) {
                lastSungWordIndex = i;
            }
        }

        if (currentWordIndex != -1) {
            LyricWord currentWord = words.get(currentWordIndex);
            int wordStart = Math.min(Math.max(0, currentWord.getCharStart()), fullLen);
            int wordEnd = Math.min(Math.max(wordStart, currentWord.getCharEnd()), fullLen);

            int visibleWordEnd = wordEnd;
            if (fullText != null) {
                while (visibleWordEnd > wordStart && Character.isWhitespace(fullText.charAt(visibleWordEnd - 1))) {
                    visibleWordEnd--;
                }
            }

            return new WordSyncState(wordStart, wordStart, visibleWordEnd, wordEnd, true, false);
        } else if (lastSungWordIndex == words.size() - 1) {
            return new WordSyncState(fullLen, -1, -1, fullLen, false, true);
        } else {
            return new WordSyncState(0, -1, -1, 0, false, false);
        }
    }

    public static SpannableStringBuilder buildWordSyncSpannable(LyricLine line, long currentPositionMs) {
        if (line == null) return new SpannableStringBuilder("");
        String fullText = line.getText();
        if (fullText == null || fullText.isEmpty()) {
            return new SpannableStringBuilder("");
        }

        SpannableStringBuilder ssb = new SpannableStringBuilder(fullText);
        WordSyncState state = calculateWordSyncState(line, currentPositionMs);

        if (state.hasActiveWord) {
            // 1. Words read before: crisp white without glow
            if (state.pastEnd > 0) {
                ssb.setSpan(new ForegroundColorSpan(COLOR_REACHED), 0, state.pastEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            // 2. Current word: solid white WITH the glow effect
            if (state.currentEnd > state.currentStart) {
                ssb.setSpan(new GlowingWordSpan(14f, 0xB3FFFFFF, COLOR_ACTIVE), state.currentStart, state.currentEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            // Space between current word and next word: unreached color, no glow
            if (state.upcomingStart > state.currentEnd) {
                ssb.setSpan(new ForegroundColorSpan(COLOR_UNREACHED), state.currentEnd, state.upcomingStart, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            // 3. Upcoming words: unreached color (matching unreached lyrics COLOR_UNREACHED), crisp, no glow, no blur
            if (state.upcomingStart < fullText.length()) {
                ssb.setSpan(new ForegroundColorSpan(COLOR_UNREACHED), state.upcomingStart, fullText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } else if (state.allFinished) {
            // All words on this line were read before: all become crisp white without glow
            ssb.setSpan(new ForegroundColorSpan(COLOR_REACHED), 0, fullText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            // Words not yet started on this active line: unreached color, no glow
            ssb.setSpan(new ForegroundColorSpan(COLOR_UNREACHED), 0, fullText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return ssb;
    }

    private void applyWordHighlight(TextView textView, LyricLine line, long currentPositionMs) {
        textView.setShadowLayer(0f, 0f, 0f, 0);
        if (line == null) return;
        SpannableStringBuilder ssb = buildWordSyncSpannable(line, currentPositionMs);
        textView.setText(ssb);
    }

    public static class GlowingWordSpan extends ReplacementSpan {
        private final float shadowRadius;
        private final int shadowColor;
        private final int textColor;

        public GlowingWordSpan(float shadowRadius, int shadowColor, int textColor) {
            this.shadowRadius = shadowRadius;
            this.shadowColor = shadowColor;
            this.textColor = textColor;
        }

        public float getShadowRadius() {
            return shadowRadius;
        }

        public int getShadowColor() {
            return shadowColor;
        }

        public int getTextColor() {
            return textColor;
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
            if (fm != null) {
                Paint.FontMetricsInt pFm = paint.getFontMetricsInt();
                fm.ascent = pFm.ascent;
                fm.descent = pFm.descent;
                fm.top = pFm.top;
                fm.bottom = pFm.bottom;
                fm.leading = pFm.leading;
            }
            return (int) Math.ceil(paint.measureText(text, start, end));
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
            if (start >= end) return;
            int oldColor = paint.getColor();
            paint.setColor(textColor);
            paint.setShadowLayer(shadowRadius, 0f, 0f, shadowColor);
            canvas.drawText(text, start, end, x, y, paint);
            paint.clearShadowLayer();
            paint.setColor(oldColor);
        }
    }

    static class LineViewHolder extends RecyclerView.ViewHolder {
        final TextView tvLyricLine;
        float lastTouchX = -1f;
        float lastTouchY = -1f;

        LineViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLyricLine = itemView.findViewById(R.id.tv_lyric_line);
            itemView.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_UP) {
                    if (tvLyricLine != null) {
                        lastTouchX = event.getX() - tvLyricLine.getLeft();
                        lastTouchY = event.getY() - tvLyricLine.getTop();
                    }
                }
                return false;
            });
            if (tvLyricLine != null) {
                tvLyricLine.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_UP) {
                        lastTouchX = event.getX();
                        lastTouchY = event.getY();
                    }
                    return false;
                });
            }
        }
    }

    static class FooterViewHolder extends RecyclerView.ViewHolder {
        FooterViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
