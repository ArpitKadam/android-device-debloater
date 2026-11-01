package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Fork: the now-playing card on Home, a square glass tile under the top bar: the song's art (a note when there is
 * none), title, artist and three buttons, previous, play or pause, next. Shown and hidden by the top bar's note
 * button. D-pad: Down from a top-bar button enters it, Left/Right go round the buttons (they wrap), Up returns to
 * the top bar, Down goes to the dock.
 */
final class NowPlayingTray extends LinearLayout {

    private final ImageView art;
    private final TextView title;
    private final TextView artist;
    private final LinearLayout controls;
    private final ImageButton[] buttons;
    private final ImageButton play;
    private final float density;
    private Runnable down = () -> { };
    private Runnable up = () -> { };

    NowPlayingTray(Context context, NowPlaying np) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        float d = density;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Math.round(10 * d);
        setPadding(pad, pad, pad, pad);
        GradientDrawable glass = new GradientDrawable();
        glass.setColor(0x4D000000);
        glass.setStroke(Math.max(1, Math.round(d)), 0x33FFFFFF);
        glass.setCornerRadius(18 * d);
        setBackground(glass);

        art = new ImageView(context);
        art.setBackgroundColor(0x26FFFFFF);
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 10 * d);
            }
        });
        art.setClipToOutline(true);
        addView(art, new LayoutParams(Math.round(54 * d), Math.round(54 * d)));

        title = text(context, 13, 0xFFFFFFFF, true);
        artist = text(context, 11, 0xB3FFFFFF, false);
        LayoutParams titleLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = Math.round(6 * d);
        addView(title, titleLp);
        addView(artist, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        controls = new LinearLayout(context);
        controls.setOrientation(HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        ImageButton previous = button(context, R.drawable.ic_media_prev, d);
        play = button(context, R.drawable.ic_media_play, d);
        ImageButton next = button(context, R.drawable.ic_media_next, d);
        buttons = new ImageButton[]{previous, play, next};
        previous.setOnClickListener(v -> np.previous());
        play.setOnClickListener(v -> np.playPause());
        next.setOnClickListener(v -> np.next());
        for (int i = 0; i < buttons.length; i++) {
            ImageButton b = buttons[i];
            int at = i;
            controls.addView(b, new LayoutParams(Math.round(38 * d), Math.round(38 * d)));
            b.setOnKeyListener((v, keyCode, event) -> onButtonKey(at, keyCode, event));
        }
        LayoutParams controlsLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        controlsLp.topMargin = Math.round(6 * d);
        addView(controls, controlsLp);
        bind(null);
    }

    /** What leaving the card does; focus moving onto it hides the dock's highlight and the top shelf. */
    void setNavigation(Runnable down, Runnable up, Runnable onFocused) {
        this.down = down;
        this.up = up;
        for (ImageButton b : buttons) {
            b.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) onFocused.run();
            });
        }
    }

    View playButton() {
        return play;
    }

    /** state is null when nothing is playing: the card says so and shows no buttons. */
    void bind(NowPlaying.State state) {
        if (state == null) {
            title.setText("Nothing playing");
            artist.setText("");
            controls.setVisibility(INVISIBLE);
            showNote();
            return;
        }
        title.setText(state.title);
        artist.setText(state.artist);
        play.setImageResource(state.playing ? R.drawable.ic_media_pause : R.drawable.ic_media_play);
        controls.setVisibility(VISIBLE);
        if (state.art != null) {
            art.setPadding(0, 0, 0, 0);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setImageBitmap(state.art);
        } else {
            showNote();
        }
    }

    private void showNote() {
        int inset = Math.round(12 * density);
        art.setPadding(inset, inset, inset, inset);
        art.setScaleType(ImageView.ScaleType.FIT_CENTER);
        art.setImageResource(R.drawable.ic_music_note);
    }

    private boolean onButtonKey(int at, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            // swallow the matching key-up of the keys handled below, so nothing else reacts to it
            return keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                    || keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                buttons[(at + buttons.length - 1) % buttons.length].requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                buttons[(at + 1) % buttons.length].requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                up.run();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                down.run();
                return true;
            default:
                return false;
        }
    }

    private static TextView text(Context context, int sp, int color, boolean bold) {
        TextView t = new TextView(context);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setEllipsize(TextUtils.TruncateAt.END);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private static ImageButton button(Context context, int icon, float d) {
        ImageButton b = new ImageButton(context);
        b.setImageResource(icon);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = Math.round(9 * d);
        b.setPadding(pad, pad, pad, pad);
        GradientDrawable focused = new GradientDrawable();
        focused.setShape(GradientDrawable.OVAL);
        focused.setColor(0x66FFFFFF);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused}, focused);
        background.addState(new int[]{}, new ColorDrawable(0));
        b.setBackground(background);
        b.setFocusable(true);
        return b;
    }
}
