package com.aadith.mustre.lyrics;

import com.aadith.mustre.model.LyricLine;
import com.aadith.mustre.model.LyricWord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LrcParser {

    private static final Pattern TIME_TAG_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?\\]");
    private static final Pattern WORD_TAG_PATTERN = Pattern.compile("<(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?>");

    public static List<LyricLine> parse(String lrcContent) {
        if (lrcContent == null || lrcContent.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<LyricLine> result = new ArrayList<>();
        String[] lines = lrcContent.split("\r?\n");

        for (String rawLine : lines) {
            if (rawLine == null || rawLine.trim().isEmpty()) continue;

            Matcher matcher = TIME_TAG_PATTERN.matcher(rawLine);
            List<Long> timestamps = new ArrayList<>();
            int lastMatchEnd = 0;

            while (matcher.find()) {
                long totalMs = parseTimestamp(matcher.group(1), matcher.group(2), matcher.group(3));
                timestamps.add(totalMs);
                lastMatchEnd = matcher.end();
            }

            if (!timestamps.isEmpty()) {
                String content = rawLine.substring(lastMatchEnd);
                List<LyricWord> words = parseEnhancedWords(content);
                String cleanText = words != null && !words.isEmpty()
                        ? extractCleanText(words)
                        : content.trim();

                for (Long timeMs : timestamps) {
                    LyricLine line = new LyricLine(timeMs, cleanText);
                    if (words != null && !words.isEmpty()) {
                        line.setWords(cloneWords(words));
                    }
                    result.add(line);
                }
            }
        }

        Collections.sort(result);

        // Filter out redundant multiple consecutive empty lines while preserving deliberate breaks
        List<LyricLine> cleaned = new ArrayList<>(result.size());
        boolean prevWasEmpty = false;
        for (LyricLine line : result) {
            boolean isEmpty = line.isEmpty();
            if (isEmpty && prevWasEmpty) {
                continue;
            }
            cleaned.add(line);
            prevWasEmpty = isEmpty;
        }

        // Ensure every non-empty line has word-level timing (synthesizing proportional words if enhanced tags are absent)
        for (int i = 0; i < cleaned.size(); i++) {
            LyricLine line = cleaned.get(i);
            if (!line.hasWords() && !line.isEmpty()) {
                long lineStart = line.getTimeMs();
                long lineEnd;
                if (i + 1 < cleaned.size()) {
                    long nextStart = cleaned.get(i + 1).getTimeMs();
                    lineEnd = Math.min(lineStart + 6000L, Math.max(lineStart + 1200L, nextStart));
                } else {
                    lineEnd = lineStart + 4000L;
                }
                line.setWords(synthesizeWordsForLine(line.getText(), lineStart, lineEnd));
            }
        }

        return cleaned;
    }

    private static List<LyricWord> synthesizeWordsForLine(String text, long lineStart, long lineEnd) {
        if (text == null || text.trim().isEmpty()) return null;
        String trimmed = text.trim();
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length == 0) return null;

        List<LyricWord> words = new ArrayList<>(tokens.length);
        long totalDuration = Math.max(800L, lineEnd - lineStart);
        int totalChars = 0;
        for (String t : tokens) {
            totalChars += Math.max(1, t.length());
        }

        long currentStart = lineStart;
        int searchIndex = 0;

        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            int charStart = text.indexOf(token, searchIndex);
            if (charStart < 0) charStart = searchIndex;
            int charEnd = charStart + token.length();
            searchIndex = charEnd;

            long wordDuration;
            if (i == tokens.length - 1) {
                wordDuration = Math.max(200L, (lineStart + totalDuration) - currentStart);
            } else {
                float fraction = (float) Math.max(1, token.length()) / totalChars;
                wordDuration = Math.max(200L, (long) (fraction * totalDuration));
            }

            long currentEnd = currentStart + wordDuration;
            words.add(new LyricWord(token, currentStart, currentEnd, charStart, charEnd));
            currentStart = currentEnd;
        }

        return words;
    }

    private static long parseTimestamp(String minStr, String secStr, String fracStr) {
        try {
            int minutes = Integer.parseInt(minStr);
            int seconds = Integer.parseInt(secStr);
            long ms = 0;
            if (fracStr != null && !fracStr.isEmpty()) {
                if (fracStr.length() == 1) {
                    ms = Integer.parseInt(fracStr) * 100L;
                } else if (fracStr.length() == 2) {
                    ms = Integer.parseInt(fracStr) * 10L;
                } else {
                    ms = Long.parseLong(fracStr.substring(0, Math.min(3, fracStr.length())));
                }
            }
            return (minutes * 60L + seconds) * 1000L + ms;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static List<LyricWord> parseEnhancedWords(String content) {
        if (content == null || !content.contains("<") || !content.contains(">")) {
            return null;
        }

        Matcher matcher = WORD_TAG_PATTERN.matcher(content);
        List<MatcherMatch> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(new MatcherMatch(matcher.start(), matcher.end(), matcher.group(1), matcher.group(2), matcher.group(3)));
        }

        if (matches.isEmpty()) {
            return null;
        }

        List<LyricWord> words = new ArrayList<>();
        StringBuilder cleanBuilder = new StringBuilder();

        for (int i = 0; i < matches.size(); i++) {
            MatcherMatch match = matches.get(i);
            int wordStart = match.end;
            int wordEnd = (i + 1 < matches.size()) ? matches.get(i + 1).start : content.length();
            String wordText = content.substring(wordStart, wordEnd);

            long startMs = parseTimestamp(match.minStr, match.secStr, match.fracStr);
            int charStart = cleanBuilder.length();
            cleanBuilder.append(wordText);
            int charEnd = cleanBuilder.length();

            LyricWord word = new LyricWord(wordText, startMs, 0, charStart, charEnd);
            words.add(word);
        }

        // Compute endMs for each word
        for (int i = 0; i < words.size(); i++) {
            LyricWord w = words.get(i);
            if (i + 1 < words.size()) {
                w.setEndMs(words.get(i + 1).getStartMs());
            } else {
                w.setEndMs(w.getStartMs() + 350);
            }
        }

        return words;
    }

    private static String extractCleanText(List<LyricWord> words) {
        StringBuilder sb = new StringBuilder();
        for (LyricWord w : words) {
            sb.append(w.getText());
        }
        return sb.toString().trim();
    }

    private static List<LyricWord> cloneWords(List<LyricWord> original) {
        List<LyricWord> copy = new ArrayList<>(original.size());
        for (LyricWord w : original) {
            copy.add(new LyricWord(w.getText(), w.getStartMs(), w.getEndMs(), w.getCharStart(), w.getCharEnd()));
        }
        return copy;
    }

    private static class MatcherMatch {
        final int start;
        final int end;
        final String minStr;
        final String secStr;
        final String fracStr;

        MatcherMatch(int start, int end, String minStr, String secStr, String fracStr) {
            this.start = start;
            this.end = end;
            this.minStr = minStr;
            this.secStr = secStr;
            this.fracStr = fracStr;
        }
    }
}
