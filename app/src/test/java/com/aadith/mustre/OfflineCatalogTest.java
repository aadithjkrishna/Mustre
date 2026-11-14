package com.aadith.mustre;

import com.aadith.mustre.model.Song;
import com.aadith.mustre.network.ServerPrefs;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class OfflineCatalogTest {

    @Test
    public void testSongIdentityNormalisation() {
        Song song1 = new Song("  Midnight City ", " M83 ", "Album", "/path/1.mp3", null);
        Song song2 = new Song("midnight city", "m83", "Album B", "/path/2.mp3", null);

        String key1 = ServerPrefs.getSongIdentityKey(song1);
        String key2 = ServerPrefs.getSongIdentityKey(song2);

        assertEquals("midnight city::m83", key1);
        assertEquals(key1, key2);
    }

    @Test
    public void testDeduplicateSongsSameTitleArtistDifferentServerIDs() {
        List<Song> serverSongs = new ArrayList<>();
        Song songFromA = new Song("Resonance", "HOME", "Album 1", "http://serverA/song/1", "coverA");
        Song songFromB = new Song("Resonance", "HOME", "Album 2", "http://serverB/song/99", "coverB");

        serverSongs.add(songFromA);
        serverSongs.add(songFromB);

        List<Song> deduplicated = ServerPrefs.deduplicateSongs(serverSongs);

        assertEquals(1, deduplicated.size());
        assertEquals("Resonance", deduplicated.get(0).getTitle());
    }

    @Test
    public void testDuplicateIDsDifferentSongsNotMerged() {
        List<Song> songs = new ArrayList<>();
        Song songA = new Song("Track Alpha", "Artist A", "Album 1", "/audios/101.mp3", null);
        Song songB = new Song("Track Beta", "Artist B", "Album 2", "/audios/101.mp3", null);

        songs.add(songA);
        songs.add(songB);

        List<Song> deduplicated = ServerPrefs.deduplicateSongs(songs);

        assertEquals(2, deduplicated.size());
    }

    @Test
    public void testMultiServerCatalogMergingLogic() {
        // Server A has {A, B, C}. Only A and B are fully cached locally.
        Song songA = new Song("Song A", "Artist A", "Album", "/local/path/a.mp3", "coverA");
        Song songB = new Song("Song B", "Artist B", "Album", "/local/path/b.mp3", "coverB");
        Song songC_ServerA = new Song("Song C", "Artist C", "Album", "/serverA/c.mp3", "coverC");

        List<Song> fullyCachedLocal = new ArrayList<>();
        fullyCachedLocal.add(songA);
        fullyCachedLocal.add(songB);

        // Server B provides {B, C, D}
        Song songB_ServerB = new Song("Song B", "Artist B", "Album B", "/serverB/b.mp3", "coverB");
        Song songC_ServerB = new Song("Song C", "Artist C", "Album B", "/serverB/c.mp3", "coverC");
        Song songD_ServerB = new Song("Song D", "Artist D", "Album B", "/serverB/d.mp3", "coverD");

        List<Song> serverBCatalog = new ArrayList<>();
        serverBCatalog.add(songB_ServerB);
        serverBCatalog.add(songC_ServerB);
        serverBCatalog.add(songD_ServerB);

        // 1. When Connected to Server B:
        // Visible songs = Fully cached local songs ∪ Server B catalog
        // Uncached song C from Server A must NOT appear.
        // Fully cached Song A from Server A MUST remain available.
        java.util.Map<String, Song> merged = new java.util.LinkedHashMap<>();

        // Add Server B songs
        for (Song s : serverBCatalog) {
            String key = ServerPrefs.getSongIdentityKey(s);
            for (Song c : fullyCachedLocal) {
                if (key.equals(ServerPrefs.getSongIdentityKey(c))) {
                    s.setAudioPath(c.getAudioPath());
                    break;
                }
            }
            merged.put(key, s);
        }

        // Add Fully Cached Local songs
        for (Song c : fullyCachedLocal) {
            String key = ServerPrefs.getSongIdentityKey(c);
            if (!merged.containsKey(key)) {
                merged.put(key, c);
            }
        }

        List<Song> visible = new ArrayList<>(merged.values());

        // Expected visible when connected to B: A (cached), B (cached), C (server B stream), D (server B stream)
        assertEquals(4, visible.size());

        boolean hasA = false, hasB = false, hasC = false, hasD = false;
        for (Song s : visible) {
            if ("Song A".equals(s.getTitle())) hasA = true;
            if ("Song B".equals(s.getTitle())) hasB = true;
            if ("Song C".equals(s.getTitle())) {
                hasC = true;
                assertEquals("/serverB/c.mp3", s.getAudioPath()); // Server B's current stream URL
            }
            if ("Song D".equals(s.getTitle())) hasD = true;
        }

        assertTrue(hasA);
        assertTrue(hasB);
        assertTrue(hasC);
        assertTrue(hasD);

        // 2. When Offline:
        // Visible songs = Fully cached local songs (A and B)
        List<Song> offlineVisible = ServerPrefs.deduplicateSongs(fullyCachedLocal);
        assertEquals(2, offlineVisible.size());
        assertEquals("Song A", offlineVisible.get(0).getTitle());
        assertEquals("Song B", offlineVisible.get(1).getTitle());
    }
}
