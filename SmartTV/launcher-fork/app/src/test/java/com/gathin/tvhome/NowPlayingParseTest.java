package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class NowPlayingParseTest {

    private static List<String> dump(String state, String metadata) {
        return Arrays.asList(
                "  Sessions Stack - have 2 sessions:",
                "    androidx.media3.session.id. com.spotify.tv.android/androidx.media3.session.id. (userId=0)",
                "      package=com.spotify.tv.android",
                "      launchIntent=PendingIntent{99f7f62}",
                "      state=PlaybackState {state=" + state + ", position=151779, buffered position=0, speed=1.0}",
                "      metadata:" + metadata,
                "      queueTitle=null, size=1",
                "    other.session com.example/x (userId=0)",
                "      package=com.example",
                "      metadata:size=0, description=null");
    }

    @Test
    public void playingSongSplitsIntoTitleAndArtist() {
        NowPlaying.State s = NowPlaying.parse(dump("3",
                "size=22, description=Tha Waddup (with GHILDIYAL), Naam Sujal, GHILDIYAL, ISHH, null"));
        assertEquals("Tha Waddup (with GHILDIYAL)", s.title);
        assertEquals("Naam Sujal, GHILDIYAL, ISHH", s.artist);
        assertTrue(s.playing);
    }

    @Test
    public void pausedSongIsStillShownButNotPlaying() {
        NowPlaying.State s = NowPlaying.parse(dump("2", "size=22, description=Song, Artist, null"));
        assertEquals("Song", s.title);
        assertFalse(s.playing);
    }

    @Test
    public void stoppedNoSessionOrNoMetadataShowsNothing() {
        assertNull(NowPlaying.parse(dump("1", "size=22, description=Song, Artist, null")));
        assertNull(NowPlaying.parse(dump("3", "size=0, description=null")));
        assertNull(NowPlaying.parse(Arrays.asList("package=com.example", "state=PlaybackState {state=3}")));
    }
}
