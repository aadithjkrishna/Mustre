package com.aadith.mustre.lyrics;

import com.aadith.mustre.model.LyricLine;
import com.aadith.mustre.model.LyricWord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LyricsfileParser {

    public static List<LyricLine> parse(String content) {
        if (content == null || content.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<LyricLine> lines = new ArrayList<>();
        LyricLine currentLine = null;
        List<LyricWord> currentWords = null;
        LyricWord currentWord = null;
        boolean inLinesSection = false;
        boolean inWordsSection = false;
        int lineIndent = 0;

        String[] rawLines = content.split("\r?\n");

        for (String raw : rawLines) {
            String stripped = raw.trim();
            if (stripped.isEmpty() || stripped.startsWith("#")) continue;

            if (stripped.equals("lines:")) {
                inLinesSection = true;
                inWordsSection = false;
                continue;
            }

            if (!inLinesSection) continue;

            int indent = 0;
            while (indent < raw.length() && raw.charAt(indent) == ' ') {
                indent++;
            }

            if (stripped.startsWith("- text:")) {
                String val = extractQuotedValue(stripped.substring("- text:".length()).trim());

                if (inWordsSection && indent > lineIndent) {
                    // New Word definition under the current line
                    currentWord = new LyricWord(val, 0, 0);
                    if (currentWords != null) {
                        currentWords.add(currentWord);
                    }
                } else {
                    // New Line definition
                    lineIndent = indent;
                    inWordsSection = false;
                    currentWord = null;
                    currentWords = new ArrayList<>();
                    currentLine = new LyricLine(0, 0, val, currentWords);
                    lines.add(currentLine);
                }
            } else if (stripped.startsWith("words:")) {
                inWordsSection = true;
                currentWord = null;
            } else if (stripped.startsWith("start_ms:")) {
                long ms = parseLong(stripped.substring("start_ms:".length()).trim());
                if (inWordsSection && currentWord != null) {
                    currentWord = new LyricWord(currentWord.getText(), ms, currentWord.getEndMs());
                    if (currentWords != null && !currentWords.isEmpty()) {
                        currentWords.set(currentWords.size() - 1, currentWord);
                    }
                } else if (currentLine != null) {
                    currentLine.setTimeMs(ms);
                }
            } else if (stripped.startsWith("end_ms:")) {
                long ms = parseLong(stripped.substring("end_ms:".length()).trim());
                if (inWordsSection && currentWord != null) {
                    currentWord = new LyricWord(currentWord.getText(), currentWord.getStartMs(), ms);
                    if (currentWords != null && !currentWords.isEmpty()) {
                        currentWords.set(currentWords.size() - 1, currentWord);
                    }
                } else if (currentLine != null) {
                    currentLine.setEndTimeMs(ms);
                }
            }
        }

        // Post-process lines: calculate charStart, charEnd, and endMs for each word
        for (LyricLine line : lines) {
            List<LyricWord> words = line.getWords();
            if (words != null && !words.isEmpty()) {
                StringBuilder reconstructed = new StringBuilder();
                for (int i = 0; i < words.size(); i++) {
                    LyricWord w = words.get(i);
                    int start = reconstructed.length();
                    reconstructed.append(w.getText());
                    int end = reconstructed.length();
                    w.setCharStart(start);
                    w.setCharEnd(end);

                    if (w.getEndMs() <= w.getStartMs()) {
                        if (i + 1 < words.size() && words.get(i + 1).getStartMs() > w.getStartMs()) {
                            w.setEndMs(words.get(i + 1).getStartMs());
                        } else if (line.getEndTimeMs() > w.getStartMs()) {
                            w.setEndMs(line.getEndTimeMs());
                        } else {
                            w.setEndMs(w.getStartMs() + 350);
                        }
                    }
                }
                if (line.getTimeMs() == 0 && words.get(0).getStartMs() > 0) {
                    line.setTimeMs(words.get(0).getStartMs());
                }
                String recStr = reconstructed.toString();
                if (!recStr.trim().isEmpty()) {
                    line.setText(recStr);
                    int lineLen = line.getText().length();
                    for (LyricWord w : words) {
                        if (w.getCharStart() > lineLen) w.setCharStart(lineLen);
                        if (w.getCharEnd() > lineLen) w.setCharEnd(lineLen);
                    }
                }
            }
        }

        Collections.sort(lines);
        return lines;
    }

    private static String extractQuotedValue(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            if (s.length() >= 2) {
                s = s.substring(1, s.length() - 1);
            }
        }
        return s.replace("\\'", "'").replace("\\\"", "\"");
    }

    private static long parseLong(String val) {
        try {
            return Long.parseLong(val);
        } catch (Exception e) {
            return 0;
        }
    }
}
