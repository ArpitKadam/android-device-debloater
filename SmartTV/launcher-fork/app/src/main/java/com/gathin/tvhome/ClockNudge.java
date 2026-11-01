package com.gathin.tvhome;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextClock;

/**
 * Fork: this TV's firmware stops Android's once-a-minute TIME_TICK alarm (2 ticks in 38 minutes), and a TextClock
 * with no seconds in its format listens to nothing else, so every clock freezes until its activity is recreated.
 * While an activity is showing, this re-applies each TextClock's format (which re-reads the time) every 10 s.
 */
final class ClockNudge implements Runnable {

    private static final long EVERY_MS = 10_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final View root;
    private final Runnable extra;

    /** extra runs on every refresh too (Home's greeting follows the hour); may be null. */
    ClockNudge(View root, Runnable extra) {
        this.root = root;
        this.extra = extra;
    }

    void start() {
        handler.removeCallbacks(this);
        run();
    }

    void stop() {
        handler.removeCallbacks(this);
    }

    @Override
    public void run() {
        nudge(root);
        if (extra != null) extra.run();
        handler.postDelayed(this, EVERY_MS);
    }

    static void nudge(View view) {
        if (view instanceof TextClock) {
            TextClock clock = (TextClock) view;
            clock.setFormat12Hour(clock.getFormat12Hour());
            clock.setFormat24Hour(clock.getFormat24Hour());
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) nudge(group.getChildAt(i));
        }
    }
}
