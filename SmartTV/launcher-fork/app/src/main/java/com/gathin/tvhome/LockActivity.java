package com.gathin.tvhome;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.Random;

/**
 * Fork: the lock screen shown on boot and on every wake from standby. A new wallpaper from the theme's
 * collection with the clock and date dead centre, over Home. Any key rises it like a shutter from the bottom
 * edge and Home is underneath. Translucent, so Home stays drawn behind it; no animation but one translation.
 */
public class LockActivity extends Activity {

    /** The key press that woke the TV can still be arriving; it must not open the lock it just raised. */
    private static final long GRACE_MS = 800;
    private static final long REVEAL_MS = 520;

    private final Random random = new Random();
    private long shownAt;
    private boolean opening;
    private ClockNudge clockNudge;
    private NowPlaying nowPlaying;
    private LinearLayout nowCard;
    private ImageView nowArt;
    private TextView nowTitle;
    private TextView nowArtist;
    private TextView nowHint;
    /** True from onCreate to onDestroy: HomeKeyService raises the lock at screen-off and must not stack a second. */
    static volatile boolean alive;
    /** A wake-up key arrives after the screen turns on, not after the lock was raised at screen-off. */
    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            shownAt = SystemClock.uptimeMillis();
            if (clockNudge != null) clockNudge.start();
        }
    };

    /** Keys the system keeps for itself while the lock is up: volume and the firmware's own keys never open it. */
    static boolean opensOn(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_SLEEP:
            case KeyEvent.KEYCODE_WAKEUP:
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_APP_SWITCH:
                return false;
            default:
                return true;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        alive = true;
        registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
        setContentView(R.layout.dream_clock);
        LinearLayout content = findViewById(R.id.dreamContent);
        FrameLayout.LayoutParams place = (FrameLayout.LayoutParams) content.getLayoutParams();
        place.gravity = Gravity.CENTER;
        content.setLayoutParams(place);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        showWallpaper(findViewById(R.id.dreamAmbient));
        shownAt = SystemClock.uptimeMillis();
        clockNudge = new ClockNudge(findViewById(android.R.id.content), null);
        buildNowCard(content);
        nowPlaying = new NowPlaying(this, this::bindNowCard);
    }

    /** The song that is playing, under the date: art, title, artist. Text only, no buttons: any key lifts the lock. */
    private void buildNowCard(LinearLayout content) {
        float d = getResources().getDisplayMetrics().density;
        nowCard = new LinearLayout(this);
        nowCard.setOrientation(LinearLayout.HORIZONTAL);
        nowCard.setGravity(Gravity.CENTER_VERTICAL);
        int cardPad = Math.round(14 * d);
        nowCard.setPadding(cardPad, cardPad, cardPad, cardPad);
        GradientDrawable panel = new GradientDrawable();
        panel.setColor(0x40000000);
        panel.setStroke(Math.max(1, Math.round(d)), 0x40FFFFFF);
        panel.setCornerRadius(20 * d);
        nowCard.setBackground(panel);
        nowArt = new ImageView(this);
        nowArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        nowCard.addView(nowArt, new LinearLayout.LayoutParams(Math.round(84 * d), Math.round(84 * d)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        nowTitle = new TextView(this);
        nowTitle.setTextSize(21);
        nowTitle.setTextColor(0xFFFFFFFF);
        nowTitle.setTypeface(nowTitle.getTypeface(), android.graphics.Typeface.BOLD);
        nowTitle.setSingleLine(true);
        nowTitle.setEllipsize(TextUtils.TruncateAt.END);
        nowArtist = new TextView(this);
        nowArtist.setTextSize(15);
        nowArtist.setTextColor(0xD9FFFFFF);
        nowArtist.setSingleLine(true);
        nowArtist.setEllipsize(TextUtils.TruncateAt.END);
        nowHint = new TextView(this);
        nowHint.setTextSize(12);
        nowHint.setTextColor(0xB3FFFFFF);
        nowHint.setSingleLine(true);
        nowHint.setPadding(0, Math.round(8 * d), 0, 0);
        texts.addView(nowTitle);
        texts.addView(nowArtist);
        texts.addView(nowHint);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(Math.round(330 * d),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        textLp.setMarginStart(Math.round(16 * d));
        nowCard.addView(texts, textLp);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = Math.round(28 * d);
        content.addView(nowCard, cardLp);
        nowCard.setVisibility(View.GONE);
    }

    /** Shown when a song is playing, and kept while it is paused from here, so OK can resume it. */
    private void bindNowCard(NowPlaying.State state) {
        boolean wasShowing = nowCard.getVisibility() == View.VISIBLE;
        if (state == null || (!state.playing && !wasShowing)) {
            nowCard.setVisibility(View.GONE);
            return;
        }
        nowTitle.setText(state.title);
        nowArtist.setText(state.artist);
        if (state.art != null) {
            nowArt.setPadding(0, 0, 0, 0);
            nowArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
            nowArt.setImageBitmap(state.art);
        } else {
            int inset = Math.round(20 * getResources().getDisplayMetrics().density);
            nowArt.setPadding(inset, inset, inset, inset);
            nowArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
            nowArt.setImageResource(R.drawable.ic_music_note);
        }
        nowHint.setText(state.playing ? "◀ prev   OK pause   next ▶   ▲ unlock" : "◀ prev   OK play   next ▶   ▲ unlock");
        nowCard.setVisibility(View.VISIBLE);
    }

    /** Left, Right and OK work the song while its card is up; true when the key was one of those. */
    private boolean songKey(KeyEvent event) {
        if (nowCard.getVisibility() != View.VISIBLE) return false;
        int key = event.getKeyCode();
        boolean ours = key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT
                || key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER;
        if (ours && event.getAction() == KeyEvent.ACTION_UP) {
            if (key == KeyEvent.KEYCODE_DPAD_LEFT) nowPlaying.previous();
            else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) nowPlaying.next();
            else nowPlaying.playPause();
        }
        return ours;
    }

    @Override
    protected void onResume() {
        super.onResume();
        clockNudge.start();
        nowPlaying.start();
    }

    @Override
    protected void onPause() {
        clockNudge.stop();
        nowPlaying.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        alive = false;
        unregisterReceiver(screenOn);
        super.onDestroy();
    }

    /** The next picture, written to Home's own preference so Home shows the same one when the shutter lifts. */
    private void showWallpaper(ImageView ambient) {
        Config cfg;
        try {
            cfg = Config.load(this);
        } catch (RuntimeException e) {
            return;
        }
        if (cfg.wallpapers.isEmpty()) return;
        SharedPreferences prefs = getSharedPreferences("wallpaper_rotation", MODE_PRIVATE);
        File next = WallpaperRotation.pick(cfg.wallpapers, prefs.getString("image", ""), cfg.wallpaperOrder, random);
        if (next == null) return;
        prefs.edit().putString("image", next.getName()).putLong("changed", System.currentTimeMillis()).apply();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        new Thread(() -> {
            Bitmap bmp = ClockScene.decode(next, dm.widthPixels, dm.heightPixels);
            if (bmp == null) return;
            ambient.post(() -> {
                if (isDestroyed()) {
                    bmp.recycle();
                    return;
                }
                ambient.setImageDrawable(new LayerDrawable(new Drawable[]{
                        new BitmapDrawable(getResources(), bmp), new ColorDrawable(0x66000000)}));
            });
        }, "lock-wallpaper").start();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!opensOn(event.getKeyCode())) return super.dispatchKeyEvent(event);
        if (songKey(event)) return true;
        if (event.getAction() == KeyEvent.ACTION_UP) open();
        return true;
    }

    private void open() {
        if (opening || SystemClock.uptimeMillis() - shownAt < GRACE_MS) return;
        opening = true;
        View screen = findViewById(android.R.id.content);
        screen.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        screen.animate().translationY(-screen.getHeight()).setDuration(REVEAL_MS)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    finish();
                    overridePendingTransition(0, 0);
                }).start();
    }
}
