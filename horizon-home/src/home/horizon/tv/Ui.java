package home.horizon.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared look and helpers. All sizes are given for a 1920-wide screen and scaled. */
final class Ui {
    static final int BG = 0xFF07090D;
    static final int GLASS = 0x99202630;
    static final int GLASS_FOCUS = 0xF0394252;
    static final int TEXT = 0xFFF4F6FA;
    static final int MUTED = 0xFFA9B1BE;
    static final int TRACK = 0x55FFFFFF;

    /** Background work: network, images, database. */
    static final ExecutorService POOL = Executors.newFixedThreadPool(3);

    private Ui() { }

    static float scale(Context c) { return c.getResources().getDisplayMetrics().widthPixels / 1920f; }

    static int px(Context c, float v) { return Math.round(v * scale(c)); }

    static TextView text(Context c, float size, int color, String family) {
        TextView t = new TextView(c);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(c, size));
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        // Lines follow the system language's direction: right-aligned in Arabic and Urdu, left-aligned
        // otherwise, whatever language a channel or film name happens to be in.
        t.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        return t;
    }

    static GradientDrawable round(int fill, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radiusPx);
        return g;
    }

    /** Frosted-glass tile background with a white outline when focused. */
    static StateListDrawable glass(Context c, float radius) {
        GradientDrawable focus = round(Theme.glassFocus(), px(c, radius));
        focus.setStroke(px(c, 3), Theme.text());
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[] {android.R.attr.state_focused}, focus);
        d.addState(new int[] {android.R.attr.state_pressed}, focus);
        d.addState(new int[0], round(Theme.glass(), px(c, radius)));
        return d;
    }

    static void focusable(View v) {
        v.setFocusable(true);
        v.setClickable(true);
    }

    /** Short focus lift: the tile grows a little and rises. */
    static void lift(final View v, final float amount, final View.OnFocusChangeListener also) {
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View x, boolean has) {
                // No enlargement: the coloured outline is the highlight, so tiles are never clipped.
                if (also != null) also.onFocusChange(x, has);
            }
        });
    }

    static void toast(Context c, String s) { Toast.makeText(c, s, Toast.LENGTH_SHORT).show(); }

    /** "Options" key: the menu button, or red on remotes that have colour keys. Holding OK does the same. */
    static boolean isOptionsKey(int keyCode) {
        return keyCode == android.view.KeyEvent.KEYCODE_MENU || keyCode == android.view.KeyEvent.KEYCODE_PROG_RED;
    }

    /** Pill-shaped action button in the wallpaper's colours. */
    static TextView button(Context c, String label) {
        TextView b = text(c, 26, Theme.text(), "sans-serif-medium");
        b.setText(label);
        b.setGravity(android.view.Gravity.CENTER);
        b.setPaddingRelative(px(c, 36), px(c, 20), px(c, 36), px(c, 20));
        b.setBackground(glass(c, 40));
        focusable(b);
        return b;
    }

    static void open(Activity a, Intent i) {
        try {
            a.startActivity(new Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            a.overridePendingTransition(0, 0);
        } catch (RuntimeException e) {
            toast(a, T.t("Could not open that"));
        }
    }

    /**
     * Opens Android TV Settings inside the caller's task (no NEW_TASK). Settings is a translucent side panel,
     * so the calling screen stays visible, dimmed, behind it - instead of the system wallpaper.
     */
    static void openSettings(Activity a) {
        try {
            a.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
            a.overridePendingTransition(0, 0);
            App.settingsOver = a.getClass();
        } catch (RuntimeException e) {
            toast(a, T.t("Could not open Settings"));
        }
    }

    static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, sec)
                     : String.format(java.util.Locale.US, "%d:%02d", m, sec);
    }

    static String left(long pos, long dur) {
        long min = Math.max(1, (dur - pos) / 60000);
        return min >= 60 ? T.f("%1$d hr %2$d min left", min / 60, min % 60) : T.f("%d min left", min);
    }

}
