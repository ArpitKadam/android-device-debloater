package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Fork: the song a music app is playing, and its controls.
 *
 * Android gives an app no door to another app's media session on this TV: notification listeners are refused on
 * low_ram devices and Spotify refuses media-browser clients. What it does allow is the DUMP permission, which adb can
 * grant (pm grant com.gathin.tvhome android.permission.DUMP). With it the launcher runs dumpsys media_session
 * itself, every few seconds while Home or the lock screen is up, and reads the title, artist and play state from
 * the app's session. The controls are ordinary media keys, which Android routes to that session. No album art:
 * the dump has none.
 */
final class NowPlaying {

    static final class State {
        final String title;
        final String artist;
        final Bitmap art;
        final boolean playing;

        State(String title, String artist, Bitmap art, boolean playing) {
            this.title = title;
            this.artist = artist;
            this.art = art;
            this.playing = playing;
        }

        boolean sameAs(State o) {
            return o != null && playing == o.playing && title.equals(o.title) && artist.equals(o.artist);
        }
    }

    interface Listener {
        /** state is null when there is nothing to show. */
        void onNowPlaying(State state);
    }

    private static final String TAG = "NowPlaying";
    // ponytail: Spotify only. Make this a list to follow other music apps.
    private static final String PACKAGE = "com.spotify.tv.android";
    private static final long POLL_MS = 4000;
    // ponytail: fixed waits, no check that Spotify is really in front. Poll the resumed activity if one proves too short.
    private static final long SHOW_DELAY_MS = 1500;
    private static final long RETURN_DELAY_MS = 1000;
    private static final ExecutorService READER = Executors.newSingleThreadExecutor();

    private final Context context;
    private final Listener out;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::read;
    private boolean started;
    private State shown;

    NowPlaying(Context context, Listener out) {
        this.context = context;
        this.out = out;
    }

    void start() {
        if (started) return;
        started = true;
        read();
    }

    void stop() {
        started = false;
        main.removeCallbacks(poll);
    }

    void previous() {
        key(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    }

    void next() {
        key(KeyEvent.KEYCODE_MEDIA_NEXT);
    }

    void playPause() {
        if (shown != null && shown.playing) pauseOnScreen();
        else key(KeyEvent.KEYCODE_MEDIA_PLAY);
    }

    /**
     * Spotify restarts by itself about 31 s after a pause that reaches it while it is in the background (measured:
     * the same key paused it for good with its screen up, and in-app pause also stuck). So bring it up for a moment,
     * pause it there, and return to what was on screen: Home, or a fresh lock screen (LockActivity is noHistory
     * and is gone once another app covers it).
     */
    private void pauseOnScreen() {
        Context app = context.getApplicationContext();
        Intent open = app.getPackageManager().getLeanbackLaunchIntentForPackage(PACKAGE);
        if (open == null) {
            key(KeyEvent.KEYCODE_MEDIA_PAUSE);
            return;
        }
        Intent back = new Intent(app, context.getClass())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        app.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        main.postDelayed(() -> {
            key(KeyEvent.KEYCODE_MEDIA_PAUSE);
            main.postDelayed(() -> app.startActivity(back), RETURN_DELAY_MS);
        }, SHOW_DELAY_MS);
    }

    private void key(int code) {
        AudioManager audio = context.getSystemService(AudioManager.class);
        if (audio != null) {
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
        }
        main.removeCallbacks(poll);   // look again soon, so the card follows what the key did
        if (started) main.postDelayed(poll, 700);
    }

    private void read() {
        main.removeCallbacks(poll);
        if (!started) return;
        READER.execute(() -> {
            State s = parse(dump());
            main.post(() -> {
                if (!started) return;
                if (s == null ? shown != null : !s.sameAs(shown)) {
                    shown = s;
                    out.onNowPlaying(s);
                }
                main.postDelayed(poll, POLL_MS);
            });
        });
    }

    private static List<String> dump() {
        List<String> lines = new ArrayList<>();
        Process p = null;
        try {
            p = new ProcessBuilder("dumpsys", "media_session").redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) lines.add(line);
            }
            p.waitFor(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.w(TAG, "dumpsys failed: " + e.getMessage());
        } finally {
            if (p != null) p.destroy();
        }
        return lines;
    }

    /**
     * The music app's session in a dumpsys media_session dump: its play state and description
     * ("title, subtitle, description"). Null when it has no session, or is stopped, or has no title.
     * A title that itself contains ", " splits at its first comma: the dump gives no better handle.
     */
    static State parse(List<String> lines) {
        int at = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equals("package=" + PACKAGE)) {
                at = i;
                break;
            }
        }
        if (at < 0) return null;
        int state = -1;
        String description = null;
        for (int i = at + 1; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.startsWith("package=")) break;
            if (state < 0 && t.startsWith("state=PlaybackState {state=")) {
                int from = "state=PlaybackState {state=".length();
                int to = from;
                while (to < t.length() && Character.isDigit(t.charAt(to))) to++;
                if (to > from) state = Integer.parseInt(t.substring(from, to));
            }
            if (description == null && t.startsWith("metadata:")) {
                int d = t.indexOf("description=");
                if (d >= 0) description = t.substring(d + "description=".length());
            }
        }
        boolean playing = state == 3 || state == 6;   // playing, buffering
        if (!playing && state != 2) return null;       // stopped, none or an error: nothing to show
        if (description == null || description.equals("null")) return null;
        while (description.endsWith(", null")) description = description.substring(0, description.length() - 6);
        int comma = description.indexOf(", ");
        String title = comma < 0 ? description : description.substring(0, comma);
        String artist = comma < 0 ? "" : description.substring(comma + 2);
        if (title.isEmpty()) return null;
        return new State(title, artist, null, playing);
    }
}
