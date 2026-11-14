package com.aadith.mustre;

import com.aadith.mustre.lyrics.LrcParser;
import com.aadith.mustre.lyrics.LyricsRepository;
import com.aadith.mustre.lyrics.LyricsfileParser;
import com.aadith.mustre.model.LyricLine;
import com.aadith.mustre.model.LyricWord;
import com.aadith.mustre.model.Lyrics;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class LyricsTest {

    @Test
    public void testLrcParserBasicAndTimestamps() {
        String lrc = "[ti:Test Track]\n" +
                "[ar:Test Artist]\n" +
                "[00:05.50] First line\n" +
                "[00:10.250] Second line with 3 digits\n" +
                "[00:15.5] Third line with 1 digit\n" +
                "[00:20.00][00:30.00] Repeated chorus\n";

        List<LyricLine> lines = LrcParser.parse(lrc);

        assertEquals(5, lines.size());
        assertEquals(5500, lines.get(0).getTimeMs());
        assertEquals("First line", lines.get(0).getText());

        assertEquals(10250, lines.get(1).getTimeMs());
        assertEquals("Second line with 3 digits", lines.get(1).getText());

        assertEquals(15500, lines.get(2).getTimeMs());
        assertEquals("Third line with 1 digit", lines.get(2).getText());

        assertEquals(20000, lines.get(3).getTimeMs());
        assertEquals("Repeated chorus", lines.get(3).getText());

        assertEquals(30000, lines.get(4).getTimeMs());
        assertEquals("Repeated chorus", lines.get(4).getText());
    }

    @Test
    public void testLrcParserEmptyContent() {
        List<LyricLine> lines = LrcParser.parse(null);
        assertNotNull(lines);
        assertTrue(lines.isEmpty());

        lines = LrcParser.parse("   ");
        assertNotNull(lines);
        assertTrue(lines.isEmpty());
    }

    @Test
    public void testCleanTitleAndArtist() {
        assertEquals("Yellow", LyricsRepository.cleanTitle("Yellow.flac"));
        assertEquals("Yellow", LyricsRepository.cleanTitle("01. Yellow"));
        assertEquals("Yellow", LyricsRepository.cleanTitle("01 - Yellow"));
        assertEquals("Yellow", LyricsRepository.cleanTitle("Coldplay - Yellow"));
        assertEquals("Yellow", LyricsRepository.cleanTitle("Yellow (Official Video)"));
        assertEquals("Yellow", LyricsRepository.cleanTitle("Yellow [Remastered 2021]"));
        assertEquals("Yellow", LyricsRepository.cleanTitle("Yellow (feat. Rihanna)"));
        assertEquals("Yellow", LyricsRepository.cleanTitle("Yellow ft. Rihanna"));

        assertEquals("Coldplay", LyricsRepository.cleanArtist("Coldplay feat. Rihanna"));
        assertEquals("Coldplay", LyricsRepository.cleanArtist("Coldplay ft. Someone"));
    }

    @Test
    public void testCacheKeysConsistency() {
        com.aadith.mustre.model.Song rawSong = new com.aadith.mustre.model.Song(
                "01. Yellow (Official Music Video)",
                "Coldplay feat. Someone",
                "Parachutes",
                "/storage/audios/yellow.mp3",
                null
        );

        com.aadith.mustre.model.Song cleanSong = new com.aadith.mustre.model.Song(
                "Yellow",
                "Coldplay",
                "Parachutes",
                "http://server/song/123",
                null
        );

        List<String> rawKeys = LyricsRepository.generateCacheKeys(rawSong);
        List<String> cleanKeys = LyricsRepository.generateCacheKeys(cleanSong);

        assertFalse(rawKeys.isEmpty());
        assertFalse(cleanKeys.isEmpty());

        // The primary key for both should match because normalized title/artist are identical
        assertEquals(cleanKeys.get(0), rawKeys.get(0));
    }

    @Test
    public void testLyricsfileParserWordSync() {
        String yaml = "lines:\n" +
                "  - text: 'Look at the stars'\n" +
                "    start_ms: 12000\n" +
                "    end_ms: 15000\n" +
                "    words:\n" +
                "      - text: 'Look '\n" +
                "        start_ms: 12000\n" +
                "        end_ms: 12500\n" +
                "      - text: 'at '\n" +
                "        start_ms: 12500\n" +
                "        end_ms: 13000\n" +
                "      - text: 'the '\n" +
                "        start_ms: 13000\n" +
                "        end_ms: 13500\n" +
                "      - text: 'stars'\n" +
                "        start_ms: 13500\n" +
                "        end_ms: 15000\n" +
                "  - text: 'Look how they shine for you'\n" +
                "    start_ms: 16000\n" +
                "    end_ms: 20000\n" +
                "    words:\n" +
                "      - text: 'Look '\n" +
                "        start_ms: 16000\n" +
                "        end_ms: 16500\n" +
                "      - text: 'how '\n" +
                "        start_ms: 16500\n" +
                "        end_ms: 17000\n" +
                "      - text: 'they '\n" +
                "        start_ms: 17000\n" +
                "        end_ms: 17500\n" +
                "      - text: 'shine '\n" +
                "        start_ms: 17500\n" +
                "        end_ms: 18500\n" +
                "      - text: 'for '\n" +
                "        start_ms: 18500\n" +
                "        end_ms: 19000\n" +
                "      - text: 'you'\n" +
                "        start_ms: 19000\n" +
                "        end_ms: 20000\n";

        List<LyricLine> lines = LyricsfileParser.parse(yaml);
        assertNotNull(lines);
        assertEquals(2, lines.size());

        LyricLine line1 = lines.get(0);
        assertEquals(12000, line1.getTimeMs());
        assertEquals(15000, line1.getEndTimeMs());
        assertEquals("Look at the stars", line1.getText());
        assertTrue(line1.hasWords());
        assertEquals(4, line1.getWords().size());

        LyricWord w1 = line1.getWords().get(0);
        assertEquals("Look ", w1.getText());
        assertEquals(12000, w1.getStartMs());
        assertEquals(12500, w1.getEndMs());
        assertEquals(0, w1.getCharStart());
        assertEquals(5, w1.getCharEnd());

        LyricWord w4 = line1.getWords().get(3);
        assertEquals("stars", w4.getText());
        assertEquals(13500, w4.getStartMs());
        assertEquals(15000, w4.getEndMs());
        assertEquals(12, w4.getCharStart());
        assertEquals(17, w4.getCharEnd());

        LyricLine line2 = lines.get(1);
        assertEquals(16000, line2.getTimeMs());
        assertEquals(20000, line2.getEndTimeMs());
        assertEquals("Look how they shine for you", line2.getText());
        assertTrue(line2.hasWords());
        assertEquals(6, line2.getWords().size());
    }

    @Test
    public void testLyricsPrioritizesLyricsfileWhenWordSyncActive() {
        Lyrics lyrics = new Lyrics();
        lyrics.setHasWordSync(true);
        lyrics.setSyncedLyrics("[00:12.00] Fallback line");
        lyrics.setLyricsfile("lines:\n" +
                "  - text: 'Word synced line'\n" +
                "    start_ms: 12000\n" +
                "    words:\n" +
                "      - text: 'Word '\n" +
                "        start_ms: 12000\n" +
                "        end_ms: 12400\n" +
                "      - text: 'synced '\n" +
                "        start_ms: 12400\n" +
                "        end_ms: 12700\n" +
                "      - text: 'line'\n" +
                "        start_ms: 12700\n" +
                "        end_ms: 13000\n");

        List<LyricLine> lines = lyrics.getParsedLines();
        assertNotNull(lines);
        assertEquals(1, lines.size());
        assertEquals("Word synced line", lines.get(0).getText());
        assertTrue(lines.get(0).hasWords());
    }

    @Test
    public void testEnhancedLrcWordSync() {
        String enhancedLrc = "[00:12.34] <00:12.34> Look <00:12.56> at <00:13.00> the <00:13.50> stars\n";
        List<LyricLine> lines = LrcParser.parse(enhancedLrc);

        assertNotNull(lines);
        assertEquals(1, lines.size());

        LyricLine line = lines.get(0);
        assertEquals(12340, line.getTimeMs());
        assertEquals("Look  at  the  stars", line.getText());
        assertTrue(line.hasWords());
        assertEquals(4, line.getWords().size());

        LyricWord w0 = line.getWords().get(0);
        assertEquals(" Look ", w0.getText());
        assertEquals(12340, w0.getStartMs());
        assertEquals(12560, w0.getEndMs());

        LyricWord w1 = line.getWords().get(1);
        assertEquals(" at ", w1.getText());
        assertEquals(12560, w1.getStartMs());
        assertEquals(13000, w1.getEndMs());

        LyricWord w3 = line.getWords().get(3);
        assertEquals(" stars", w3.getText());
        assertEquals(13500, w3.getStartMs());
        assertTrue(w3.getEndMs() > 13500);
    }

    @Test
    public void testLyricsfileWordEndMsInterpolation() {
        // Words without explicit end_ms (standard in LRCLIB YAML)
        String yaml = "lines:\n" +
                "  - text: 'Look at the stars'\n" +
                "    start_ms: 33370\n" +
                "    end_ms: 34667\n" +
                "    words:\n" +
                "      - text: 'Look '\n" +
                "        start_ms: 33370\n" +
                "      - text: 'at '\n" +
                "        start_ms: 33803\n" +
                "      - text: 'the '\n" +
                "        start_ms: 33944\n" +
                "      - text: 'stars'\n" +
                "        start_ms: 34152\n";

        List<LyricLine> lines = LyricsfileParser.parse(yaml);
        assertNotNull(lines);
        assertEquals(1, lines.size());

        LyricLine line = lines.get(0);
        List<LyricWord> words = line.getWords();
        assertNotNull(words);
        assertEquals(4, words.size());

        // Word 0 end should be Word 1 start
        assertEquals(33370, words.get(0).getStartMs());
        assertEquals(33803, words.get(0).getEndMs());

        // Word 1 end should be Word 2 start
        assertEquals(33803, words.get(1).getStartMs());
        assertEquals(33944, words.get(1).getEndMs());

        // Last word end should be line endTimeMs
        assertEquals(34152, words.get(3).getStartMs());
        assertEquals(34667, words.get(3).getEndMs());
    }

    @Test
    public void testWordSyncSpansFlow() {
        java.util.List<LyricWord> words = new java.util.ArrayList<>();
        words.add(new LyricWord("Look ", 1000, 1500, 0, 5));
        words.add(new LyricWord("at ", 1500, 2000, 5, 8));
        words.add(new LyricWord("stars", 2000, 3000, 8, 13));
        LyricLine line = new LyricLine(1000, 3000, "Look at stars", words);

        // 1. Before word 0 starts (t = 500ms): not active yet, all normal color
        com.aadith.mustre.lyrics.LyricsAdapter.WordSyncState stateBefore = com.aadith.mustre.lyrics.LyricsAdapter.calculateWordSyncState(line, 500);
        assertFalse(stateBefore.hasActiveWord);
        assertFalse(stateBefore.allFinished);

        // 2. During Word 0 (t = 1200ms): Word 0 glows, upcoming is normal color
        com.aadith.mustre.lyrics.LyricsAdapter.WordSyncState stateWord0 = com.aadith.mustre.lyrics.LyricsAdapter.calculateWordSyncState(line, 1200);
        assertTrue(stateWord0.hasActiveWord);
        assertEquals(0, stateWord0.currentStart);
        assertEquals(4, stateWord0.currentEnd); // "Look" (trimmed space)
        assertEquals(0, stateWord0.pastEnd); // no past words
        assertEquals(5, stateWord0.upcomingStart); // upcoming starts after word 0

        // 3. During Word 1 (t = 1700ms): Word 0 is solid white, Word 1 glows, Word 2 is normal color
        com.aadith.mustre.lyrics.LyricsAdapter.WordSyncState stateWord1 = com.aadith.mustre.lyrics.LyricsAdapter.calculateWordSyncState(line, 1700);
        assertTrue(stateWord1.hasActiveWord);
        assertEquals(5, stateWord1.pastEnd); // Word 0 (0..5) is solid white past
        assertEquals(5, stateWord1.currentStart);
        assertEquals(7, stateWord1.currentEnd); // "at" (trimmed space)
        assertEquals(8, stateWord1.upcomingStart); // Word 2 starts at 8

        // 4. During Word 2 (t = 2500ms): Word 0 & 1 solid white, Word 2 glows
        com.aadith.mustre.lyrics.LyricsAdapter.WordSyncState stateWord2 = com.aadith.mustre.lyrics.LyricsAdapter.calculateWordSyncState(line, 2500);
        assertTrue(stateWord2.hasActiveWord);
        assertEquals(8, stateWord2.pastEnd); // Word 0 & 1 (0..8) are solid white past
        assertEquals(8, stateWord2.currentStart);
        assertEquals(13, stateWord2.currentEnd); // "stars"
        assertEquals(13, stateWord2.upcomingStart); // no upcoming words

        // 5. After all words finished (t = 3500ms): all solid white, no glow
        com.aadith.mustre.lyrics.LyricsAdapter.WordSyncState stateAfter = com.aadith.mustre.lyrics.LyricsAdapter.calculateWordSyncState(line, 3500);
        assertFalse(stateAfter.hasActiveWord);
        assertTrue(stateAfter.allFinished);
        assertEquals(13, stateAfter.pastEnd);
    }

    @Test
    public void testLyricsColorsDifferentiation() {
        // Reached lyrics should be crisp solid white
        assertEquals(0xFFFFFFFF, com.aadith.mustre.lyrics.LyricsAdapter.COLOR_REACHED);
        // Active lyrics / word should be pure white
        assertEquals(0xFFFFFFFF, com.aadith.mustre.lyrics.LyricsAdapter.COLOR_ACTIVE);
        // Unreached lyrics should be subtle light gray (#FFB2B2B2)
        assertEquals(0xFFB2B2B2, com.aadith.mustre.lyrics.LyricsAdapter.COLOR_UNREACHED);
        // Reached and unreached must be strictly different colors
        assertNotEquals(com.aadith.mustre.lyrics.LyricsAdapter.COLOR_REACHED, com.aadith.mustre.lyrics.LyricsAdapter.COLOR_UNREACHED);
    }

    @Test
    public void testFindWordAtCharOffsetForSeeking() {
        java.util.List<LyricWord> words = new java.util.ArrayList<>();
        words.add(new LyricWord("Look ", 12000, 12500, 0, 5));
        words.add(new LyricWord("at ", 12500, 13000, 5, 8));
        words.add(new LyricWord("the ", 13000, 13500, 8, 12));
        words.add(new LyricWord("stars", 13500, 15000, 12, 17));
        LyricLine line = new LyricLine(12000, 15000, "Look at the stars", words);

        // Click on "Look" (char offset 2)
        LyricWord w0 = com.aadith.mustre.lyrics.LyricsAdapter.findWordAtCharOffset(line, 2);
        assertNotNull(w0);
        assertEquals("Look ", w0.getText());
        assertEquals(12000, w0.getStartMs());

        // Click on "at" (char offset 6)
        LyricWord w1 = com.aadith.mustre.lyrics.LyricsAdapter.findWordAtCharOffset(line, 6);
        assertNotNull(w1);
        assertEquals("at ", w1.getText());
        assertEquals(12500, w1.getStartMs());

        // Click on "stars" (char offset 15)
        LyricWord w3 = com.aadith.mustre.lyrics.LyricsAdapter.findWordAtCharOffset(line, 15);
        assertNotNull(w3);
        assertEquals("stars", w3.getText());
        assertEquals(13500, w3.getStartMs());

        // Click beyond end of line (e.g. 20) -> snaps to closest word ("stars")
        LyricWord wEnd = com.aadith.mustre.lyrics.LyricsAdapter.findWordAtCharOffset(line, 20);
        assertNotNull(wEnd);
        assertEquals("stars", wEnd.getText());
        assertEquals(13500, wEnd.getStartMs());

        // Null line or line without words returns null
        assertNull(com.aadith.mustre.lyrics.LyricsAdapter.findWordAtCharOffset(null, 5));
        LyricLine plainLine = new LyricLine(5000, "Plain line without word sync");
        assertNull(com.aadith.mustre.lyrics.LyricsAdapter.findWordAtCharOffset(plainLine, 5));
    }

    @Test
    public void testCentralThreeLinesSizes() {
        int activePos = 5;

        // 1. Central line playing (active): largest (28sp)
        assertEquals(28f, com.aadith.mustre.lyrics.LyricsAdapter.getTextSizeForPosition(5, activePos), 0.01f);

        // 2. Just played (active - 1): central neighbor (24sp)
        assertEquals(24f, com.aadith.mustre.lyrics.LyricsAdapter.getTextSizeForPosition(4, activePos), 0.01f);

        // 3. About to play (active + 1): central neighbor (24sp)
        assertEquals(24f, com.aadith.mustre.lyrics.LyricsAdapter.getTextSizeForPosition(6, activePos), 0.01f);

        // 4. Distant lines: smaller (18sp)
        assertEquals(18f, com.aadith.mustre.lyrics.LyricsAdapter.getTextSizeForPosition(3, activePos), 0.01f);
        assertEquals(18f, com.aadith.mustre.lyrics.LyricsAdapter.getTextSizeForPosition(2, activePos), 0.01f);
        assertEquals(18f, com.aadith.mustre.lyrics.LyricsAdapter.getTextSizeForPosition(0, activePos), 0.01f);
        assertEquals(18f, com.aadith.mustre.lyrics.LyricsAdapter.getTextSizeForPosition(7, activePos), 0.01f);
        assertEquals(18f, com.aadith.mustre.lyrics.LyricsAdapter.getTextSizeForPosition(10, activePos), 0.01f);

        // 5. Initial state before playback begins (activePosition == -1): line 0 is about to play (24sp)
        assertEquals(24f, com.aadith.mustre.lyrics.LyricsAdapter.getTextSizeForPosition(0, -1), 0.01f);
        assertEquals(18f, com.aadith.mustre.lyrics.LyricsAdapter.getTextSizeForPosition(1, -1), 0.01f);
        assertEquals(18f, com.aadith.mustre.lyrics.LyricsAdapter.getTextSizeForPosition(2, -1), 0.01f);
    }
}
