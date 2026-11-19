package home.horizon.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Home screen: clock, quick buttons, app row and Continue Watching from the built-in IPTV player. */
public class HomeActivity extends Activity {
    /** Weather for the city of the device's own time zone (set during TV setup); nothing is hard-coded. */
    private static final String WEATHER = "api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s&current_weather=true";
    private static final String GEOCODE = "geocoding-api.open-meteo.com/v1/search?count=1&format=json&language=%s&name=%s";

    private TextView time;
    private TextView date;
    private ImageView backdrop;
    private TextView continueLabel;
    private Glyph wifiGlyph;
    /** Icons that follow the wallpaper's accent colour (unfocused state). */
    private final List<Glyph> themed = new ArrayList<>();
    private final List<Glyph> focusedButtons = new ArrayList<>();
    /** Top-right buttons and their icons; their colours follow the wallpaper's top brightness. */
    private final List<ImageView> topButtons = new ArrayList<>();
    private final List<Glyph> topGlyphs = new ArrayList<>();
    private final List<TextView> mainText = new ArrayList<>();
    private final List<TextView> mutedText = new ArrayList<>();
    private final List<ImageView> tintedIcons = new ArrayList<>();

    /** Text whose colour follows the wallpaper. */
    private TextView tText(float size, boolean muted, String family) {
        TextView t = Ui.text(this, size, muted ? Theme.muted() : Theme.text(), family);
        (muted ? mutedText : mainText).add(t);
        return t;
    }
    private LinearLayout appRow;
    private LinearLayout continueRow;
    private TextView continueEmpty;
    /** History of the playlist in use (changes when you switch playlists). */
    private History history() { return History.get(this); }

    /** Last-seen state of the custom icons folder (names + sizes + times of its files). */
    private String iconsSeen = null;

    /**
     * Icons added or removed by the Windows setup app show up when Home comes back, or when Home is pressed
     * while already on the home screen. Compares the folder's contents, since folder times aren't reliable on /sdcard.
     */
    private void checkIcons() {
        java.io.File dir = getExternalFilesDir("icons");
        StringBuilder sb = new StringBuilder();
        java.io.File[] files = dir == null ? null : dir.listFiles();
        if (files != null) {
            java.util.Arrays.sort(files);
            for (java.io.File f : files) sb.append(f.getName()).append(f.length()).append(f.lastModified()).append('|');
        }
        String now = sb.toString();
        if (iconsSeen != null && !now.equals(iconsSeen)) appsDirty = true;
        iconsSeen = now;
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        checkIcons();
        if (appsDirty) refreshApps();
    }

    private String weather = "";
    private int weatherIcon = Glyph.SUN;
    private long weatherOkAt;
    private long weatherTriedAt;
    private boolean fetchingWeather;
    private boolean resumed;
    private boolean destroyed;
    private boolean appsDirty = true;
    private String focusKey;
    private final Map<String, Apps.App> favByTag = new HashMap<>();

    private final BroadcastReceiver status = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateStatus(); }
    };

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            appsDirty = true;
            if (resumed) refreshApps();
        }
    };

    private int px(float v) { return Ui.px(this, v); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);

        setContentView(buildRoot());
        loadWallpaper();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        unregisterReceiver(packages);
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        f.addAction(WifiManager.RSSI_CHANGED_ACTION);
        registerReceiver(status, f);
        updateStatus();
        checkIcons();
        if (appsDirty) refreshApps();
        refreshContinue();
    }

    @Override protected void onPause() {
        resumed = false;
        unregisterReceiver(status);
        super.onPause();
    }

    @Override public void onBackPressed() { }

    // ---- Wallpaper and accent colour ----------------------------------------------------

    private void loadWallpaper() {
        Ui.POOL.execute(new Runnable() {
            @Override public void run() {
                final android.graphics.Bitmap b = Theme.load(HomeActivity.this);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (destroyed) return;
                        backdrop.setImageBitmap(b);
                        applyAccent();
                    }
                });
            }
        });
    }

    private void applyAccent() {
        for (Glyph g : themed) g.setColor(Theme.accent);
        for (Glyph g : topGlyphs) g.setColor(focusedButtons.contains(g) ? Theme.discIcon() : Theme.topText());
        for (ImageView b : topButtons) b.setBackground(discBackground());
        for (TextView t : mainText) t.setTextColor(Theme.text());
        for (TextView t : mutedText) t.setTextColor(Theme.muted());
        for (ImageView i : tintedIcons) i.setColorFilter(Theme.accent, android.graphics.PorterDuff.Mode.SRC_IN);
        // Tile glass and highlight are built from the accent, so rebuild both rows with the new colours.
        time.setTextColor(Theme.topText());
        date.setTextColor(Theme.topText());
        mainText.clear();
        mutedText.clear();
        tintedIcons.clear();
        mainText.add(continueLabel);
        mutedText.add(continueEmpty);
        refreshApps();
        refreshContinue();
    }

    private Glyph topGlyph(int kind) {
        Glyph g = new Glyph(kind, Theme.topText());
        topGlyphs.add(g);
        return g;
    }

    /** Round button background: a disc when focused, nothing otherwise. */
    private StateListDrawable discBackground() {
        StateListDrawable bg = new StateListDrawable();
        GradientDrawable on = new GradientDrawable();
        on.setShape(GradientDrawable.OVAL);
        on.setColor(Theme.discFill());
        GradientDrawable off = new GradientDrawable();
        off.setShape(GradientDrawable.OVAL);
        off.setColor(0x00000000);
        bg.addState(new int[] {android.R.attr.state_focused}, on);
        bg.addState(new int[0], off);
        return bg;
    }

    private Glyph themedGlyph(int kind) {
        Glyph g = new Glyph(kind, Theme.accent);
        themed.add(g);
        return g;
    }

    private void chooseWallpaper() {
        final List<String> names = Theme.wallpapers(this);
        final List<String> labels = new ArrayList<>();
        for (String n : names) labels.add(n.replaceAll("\\.[^.]+$", ""));
        labels.add(T.t("No wallpaper (plain dark)"));
        String current = Theme.selected(this);
        int checked = current.isEmpty() ? names.size() : Math.max(0, names.indexOf(current));
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(T.t(names.isEmpty() ? "No photos found on the stick" : "Wallpaper"))
                .setSingleChoiceItems(labels.toArray(new String[0]), checked, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        Theme.select(HomeActivity.this, which < names.size() ? names.get(which) : "");
                        d.dismiss();
                        loadWallpaper();
                    }
                }).show();
    }

    // ---- Layout -------------------------------------------------------------------------

    private View buildRoot() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        View fade = new View(this);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x0007090D, 0xB307090D, 0xF207090D}));
        root.addView(fade, new FrameLayout.LayoutParams(-1, px(760), Gravity.BOTTOM));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPaddingRelative(px(80), px(60), px(80), px(40));
        col.setClipChildren(false);
        col.setClipToPadding(false);
        root.setClipChildren(false);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        col.addView(buildTop(), new LinearLayout.LayoutParams(-1, -2));
        col.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1f));

        HorizontalScrollView appScroll = hScroll();
        appRow = new LinearLayout(this);
        appRow.setOrientation(LinearLayout.HORIZONTAL);
        appRow.setPaddingRelative(px(16), px(18), px(16), px(18));
        appRow.setClipChildren(false);
        appScroll.addView(appRow);
        col.addView(appScroll, new LinearLayout.LayoutParams(-1, -2));

        TextView label = tText(30, false, "sans-serif");
        continueLabel = label;
        label.setText(T.t("Continue Watching"));
        label.setPaddingRelative(px(16), 0, 0, 0);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-2, -2);
        labelLp.topMargin = px(34);
        labelLp.bottomMargin = px(4);
        col.addView(label, labelLp);

        HorizontalScrollView contScroll = hScroll();
        FrameLayout contHolder = new FrameLayout(this);
        continueRow = new LinearLayout(this);
        continueRow.setOrientation(LinearLayout.HORIZONTAL);
        continueRow.setPaddingRelative(px(16), px(16), px(16), px(8));
        continueRow.setClipChildren(false);
        contHolder.addView(continueRow);
        contScroll.addView(contHolder);
        // Tall enough for the cards plus the Remove button that appears under the selected one.
        col.addView(contScroll, new LinearLayout.LayoutParams(-1, px(456)));

        continueEmpty = tText(26, true, "sans-serif");
        continueEmpty.setVisibility(View.GONE);
        contHolder.addView(continueEmpty, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL));
        return root;
    }

    private HorizontalScrollView hScroll() {
        HorizontalScrollView s = new HorizontalScrollView(this);
        s.setHorizontalScrollBarEnabled(false);
        s.setOverScrollMode(View.OVER_SCROLL_NEVER);
        s.setClipToPadding(false);
        s.setClipChildren(false);
        s.setSmoothScrollingEnabled(true);
        return s;
    }

    private View buildTop() {
        FrameLayout top = new FrameLayout(this);
        top.setPaddingRelative(px(16), 0, px(16), 0);

        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        time = Ui.text(this, 92, Theme.topText(), "sans-serif-light");
        // Full-width lines: the time, date and weather can never be cut short when their text changes.
        time.setSingleLine(true);
        date = Ui.text(this, 30, Theme.topText(), "sans-serif");
        date.setSingleLine(true);
        clock.addView(time, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
        dlp.topMargin = px(12);
        clock.addView(date, dlp);
        top.addView(clock, new FrameLayout.LayoutParams(-1, -2, Gravity.START | Gravity.TOP));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        wifiGlyph = topGlyph(Glyph.WIFI);
        buttons.addView(circle(wifiGlyph, new Runnable() {
            @Override public void run() { Ui.open(HomeActivity.this, new Intent(Settings.ACTION_WIFI_SETTINGS)); }
        }));
        buttons.addView(circle(topGlyph(Glyph.APPS), new Runnable() {
            @Override public void run() { Ui.open(HomeActivity.this, new Intent(HomeActivity.this, AppsActivity.class)); }
        }));
        buttons.addView(circle(topGlyph(Glyph.GEAR), new Runnable() {
            @Override public void run() { Ui.openSettings(HomeActivity.this); }
        }));
        buttons.addView(circle(topGlyph(Glyph.SLIDERS), new Runnable() {
            @Override public void run() { showOptions(); }
        }));
        top.addView(buttons, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.TOP));
        return top;
    }

    /** Round top-right button: white disc with a dark icon when focused. */
    private View circle(final Glyph glyph, final Runnable action) {
        ImageView b = new ImageView(this);
        b.setImageDrawable(glyph);
        b.setPaddingRelative(px(22), px(22), px(22), px(22));
        b.setBackground(discBackground());
        topButtons.add(b);
        Ui.focusable(b);
        // No scaling: the white disc is the highlight, so the button can't be clipped.
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                if (has) focusedButtons.add(glyph); else focusedButtons.remove(glyph);
                glyph.setColor(has ? Theme.discIcon() : Theme.topText());
            }
        });
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(84), px(84));
        lp.setMarginStart(px(14));
        b.setLayoutParams(lp);
        return b;
    }

    // ---- App row ------------------------------------------------------------------------

    private void refreshApps() {
        appsDirty = false;
        appRow.removeAllViews();
        favByTag.clear();
        addSystemTile("sys:live", Glyph.LIVE, T.t("Live TV"), "live");
        addSystemTile("sys:vod", Glyph.MOVIES, T.t("Movies"), "vod");
        addSystemTile("sys:series", Glyph.SERIES, T.t("Series"), "series");

        Map<String, Apps.App> byPkg = new HashMap<>();
        for (Apps.App a : Apps.load(this)) byPkg.put(a.pkg, a);
        for (String pkg : Apps.favs(this)) {
            Apps.App a = byPkg.get(pkg);
            if (a != null) addAppTile(a);
        }
        View target = focusKey == null ? null : appRow.findViewWithTag(focusKey);
        if (target == null && focusKey == null) target = appRow.getChildAt(0);
        if (target != null) target.requestFocus();
    }

    private FrameLayout baseTile(String tag) {
        FrameLayout t = new FrameLayout(this);
        t.setTag(tag);
        t.setBackground(Ui.glass(this, 20));
        Ui.focusable(t);
        // No scaling in the app row: the white outline is the highlight, so nothing can be clipped.
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) { if (has) focusKey = (String) v.getTag(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(196), px(124));
        lp.setMarginEnd(px(22));
        t.setLayoutParams(lp);
        appRow.addView(t);
        return t;
    }

    private void addSystemTile(String tag, int glyph, String label, final String kind) {
        FrameLayout t = baseTile(tag);
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(themedGlyph(glyph));
        inner.addView(icon, new LinearLayout.LayoutParams(px(52), px(52)));
        TextView name = tText(20, false, "sans-serif-medium");
        name.setText(label);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.topMargin = px(8);
        inner.addView(name, nlp);
        t.addView(inner, new FrameLayout.LayoutParams(-1, -1));
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openIptv(kind); }
        });
    }

    private void addAppTile(final Apps.App app) {
        FrameLayout t = baseTile("fav:" + app.pkg);
        favByTag.put("fav:" + app.pkg, app);
        // Single-colour app icon in the wallpaper's accent, matching the built-in tiles.
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER);
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Drawable mark = Brand.icon(this, app);       // simple mark for known apps, else a silhouette of its icon
        img.setImageDrawable(mark);
        img.setColorFilter(Theme.accent, android.graphics.PorterDuff.Mode.SRC_IN);
        tintedIcons.add(img);
        // Wide logos (like Disney+) get more of the tile's width so they aren't tiny.
        boolean wide = mark.getIntrinsicWidth() > 0 && mark.getIntrinsicWidth() > mark.getIntrinsicHeight() * 1.3f;
        inner.addView(img, new LinearLayout.LayoutParams(wide ? px(124) : px(52), px(52)));
        TextView name = tText(20, false, "sans-serif-medium");
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        name.setText(app.label);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(px(176), -2);
        nlp.topMargin = px(8);
        inner.addView(name, nlp);
        t.addView(inner, new FrameLayout.LayoutParams(-1, -1));
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Ui.open(HomeActivity.this, app.intent); }
        });
        t.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { favMenu(app); return true; }
        });
    }

    private void openIptv(String kind) {
        if (Xtream.load(this) == null) {
            Ui.open(this, new Intent(this, LoginActivity.class));
            return;
        }
        Ui.open(this, new Intent(this, BrowseActivity.class).putExtra("kind", kind));
    }

    // ---- Continue Watching ---------------------------------------------------------------

    private void refreshContinue() {
        Ui.POOL.execute(new Runnable() {
            @Override public void run() {
                final List<History.Item> items = history().recent(10);
                runOnUiThread(new Runnable() {
                    @Override public void run() { if (!destroyed) showContinue(items); }
                });
            }
        });
    }

    private void showContinue(List<History.Item> items) {
        // With more than one playlist, say which one this row belongs to.
        List<Xtream> lists = Xtream.all(this);
        Xtream active = Xtream.load(this);
        continueLabel.setText(lists.size() > 1 && active != null ? T.t("Continue Watching") + "  ·  " + active.label() : T.t("Continue Watching"));
        continueRow.removeAllViews();
        if (items.isEmpty()) {
            continueEmpty.setText(Xtream.load(this) == null
                    ? T.t("Add your IPTV playlist from Options, the sliders button at the top.")
                    : T.t("Nothing yet. Start something from Live TV, Movies or Series."));
            continueEmpty.setVisibility(View.VISIBLE);
            return;
        }
        continueEmpty.setVisibility(View.GONE);
        for (History.Item i : items) continueRow.addView(card(i));
    }

    private View card(final History.Item item) {
        boolean live = "live".equals(item.kind);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setTag("cw:" + item.key);
        Ui.focusable(card);
        // No scaling: the outline around the thumbnail is the highlight, so the card can't be clipped.

        FrameLayout thumb = new FrameLayout(this);
        thumb.setBackground(Ui.round(Theme.placeholder(), px(14)));
        thumb.setClipToOutline(true);
        GradientDrawable ring = Ui.round(0x00000000, px(14));
        ring.setStroke(px(4), Theme.text());
        StateListDrawable fg = new StateListDrawable();
        fg.addState(new int[] {android.R.attr.state_focused}, ring);
        fg.addState(new int[0], Ui.round(0x00000000, px(14)));
        thumb.setForeground(fg);
        thumb.setDuplicateParentStateEnabled(true);
        ImageView img = new ImageView(this);
        img.setScaleType(live ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
        if (live) img.setPaddingRelative(px(60), px(40), px(60), px(40));
        thumb.addView(img, new FrameLayout.LayoutParams(-1, -1));
        Images.load(img, item.image, px(404));
        if (live) {
            TextView badge = Ui.text(this, 18, 0xFFFFFFFF, "sans-serif-medium");
            badge.setText(T.t("LIVE"));
            badge.setBackground(Ui.round(0xFFD93A3A, px(6)));
            badge.setPaddingRelative(px(10), px(5), px(10), px(5));
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
            blp.setMarginStart(px(14));
            blp.topMargin = px(14);
            thumb.addView(badge, blp);
        }
        card.addView(thumb, new LinearLayout.LayoutParams(px(404), px(228)));

        if (!live && item.dur > 0) {
            LinearLayout bar = new LinearLayout(this);
            bar.setBackground(Ui.round(Ui.TRACK, px(3)));
            View fill = new View(this);
            fill.setBackground(Ui.round(0xFFFFFFFF, px(3)));
            float done = Math.min(1f, Math.max(0.03f, item.pos / (float) item.dur));
            bar.addView(fill, new LinearLayout.LayoutParams(0, -1, done));
            bar.addView(new View(this), new LinearLayout.LayoutParams(0, -1, 1f - done));
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(px(404), px(5));
            barLp.topMargin = px(12);
            card.addView(bar, barLp);
        }

        TextView title = tText(28, false, "sans-serif");
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setText(item.title);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(px(404), -2);
        tlp.topMargin = px(12);
        card.addView(title, tlp);

        LinearLayout subRow = new LinearLayout(this);
        subRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView dot = new ImageView(this);
        dot.setImageDrawable(themedGlyph(Glyph.DIAMOND));
        subRow.addView(dot, new LinearLayout.LayoutParams(px(18), px(18)));
        TextView sub = tText(22, true, "sans-serif");
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        String extra = live ? T.t("Live channel") : (item.dur > 0 ? Ui.left(item.pos, item.dur) : T.t("Resume"));
        sub.setText(item.sub == null || item.sub.length() == 0 ? extra : item.sub + " - " + extra);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(px(374), -2);
        slp.setMarginStart(px(10));
        subRow.addView(sub, slp);
        LinearLayout.LayoutParams srl = new LinearLayout.LayoutParams(-2, -2);
        srl.topMargin = px(8);
        card.addView(subRow, srl);

        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Ui.open(HomeActivity.this, PlayerActivity.resume(HomeActivity.this, item)); }
        });
        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { forgetMenu("cw:" + item.key); return true; }
        });

        // "Remove" button under each card: press Down from the card to reach it. It shows only while
        // the card or the button itself is selected, so the row stays clean.
        final TextView remove = Ui.text(this, 20, Theme.text(), "sans-serif-medium");
        remove.setText("✕  " + T.t("Remove"));
        remove.setGravity(Gravity.CENTER);
        remove.setPaddingRelative(px(22), px(10), px(22), px(10));
        GradientDrawable rOn = Ui.round(Theme.glassFocus(), px(24));
        rOn.setStroke(px(3), Theme.text());
        StateListDrawable rBg = new StateListDrawable();
        rBg.addState(new int[] {android.R.attr.state_focused}, rOn);
        rBg.addState(new int[0], Ui.round(Theme.glass(), px(24)));
        remove.setBackground(rBg);
        Ui.focusable(remove);
        remove.setAlpha(0f);
        remove.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Ui.POOL.execute(new Runnable() {
                    @Override public void run() {
                        history().hide(item.key);
                        refreshContinue();
                    }
                });
                Ui.toast(HomeActivity.this, T.f("%s removed from Continue Watching", item.title));
            }
        });
        View.OnFocusChangeListener show = new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                remove.post(new Runnable() {
                    @Override public void run() { remove.setAlpha(card.hasFocus() || remove.hasFocus() ? 1f : 0f); }
                });
            }
        };
        card.setOnFocusChangeListener(show);
        remove.setOnFocusChangeListener(show);

        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.addView(card, new LinearLayout.LayoutParams(px(404), -2));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
        rlp.topMargin = px(14);
        holder.addView(remove, rlp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(404), -2);
        lp.setMarginEnd(px(40));
        holder.setLayoutParams(lp);
        return holder;
    }

    // ---- Favourites and options --------------------------------------------------------

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        // The remote's Settings key used to be handled by the (now disabled) Google launcher.
        if (e.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) {
            if (e.getAction() == KeyEvent.ACTION_UP) Ui.openSettings(this);
            return true;
        }
        if (Ui.isOptionsKey(e.getKeyCode())) {
            View f = getCurrentFocus();
            Object tag = f == null ? null : f.getTag();
            boolean handled = tag instanceof String
                    && (((String) tag).startsWith("fav:") || ((String) tag).startsWith("cw:"));
            if (!handled) {
                if (e.getKeyCode() == KeyEvent.KEYCODE_MENU) {
                    if (e.getAction() == KeyEvent.ACTION_UP) Ui.openSettings(this);
                    return true;
                }
                return super.dispatchKeyEvent(e);
            }
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                if (((String) tag).startsWith("fav:")) favMenu(favByTag.get(tag));
                else forgetMenu((String) tag);
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private void favMenu(final Apps.App app) {
        if (app == null) return;
        final List<String> favs = Apps.favs(this);
        final int index = favs.indexOf(app.pkg);
        final List<String> labels = new ArrayList<>();
        final List<Integer> acts = new ArrayList<>();
        if (index > 0) { labels.add(T.t(T.rtl() ? "Move right" : "Move left")); acts.add(1); }   // earlier in the row
        if (index >= 0 && index < favs.size() - 1) { labels.add(T.t(T.rtl() ? "Move left" : "Move right")); acts.add(2); }
        labels.add(T.t("Remove from the row"));
        acts.add(3);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(app.label)
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        int a = acts.get(which);
                        if (a == 1) Collections.swap(favs, index, index - 1);
                        else if (a == 2) Collections.swap(favs, index, index + 1);
                        else { favs.remove(app.pkg); focusKey = null; }
                        Apps.saveFavs(HomeActivity.this, favs);
                        refreshApps();
                    }
                }).show();
    }

    private void forgetMenu(final String tag) {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setItems(new String[] {T.t("Remove from Continue Watching")}, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        final String key = tag.substring(3);
                        Ui.POOL.execute(new Runnable() {
                            @Override public void run() {
                                history().hide(key);
                                refreshContinue();
                            }
                        });
                    }
                }).show();
    }

    private void showOptions() {
        final boolean signedIn = Xtream.load(this) != null;
        DiskCache.init(this);
        final String cacheLabel = T.f("Clear cache (%.0f MB of saved posters and lists)",
                DiskCache.size() / (1024f * 1024f));
        Xtream active = Xtream.load(this);
        final String playlistsLabel = signedIn ? T.f("IPTV playlists (using: %s)", active.label()) : T.t("Add an IPTV playlist");
        final String wallpaperLabel = T.t("Wallpaper");
        final String clearLabel = T.t("Clear Continue Watching");
        final String appRowLabel = T.t("Edit app row (opens All apps)");
        final String weatherLabel = T.f("Weather city (%s)", weatherSettingName());
        final String[] items = signedIn
                ? new String[] {wallpaperLabel, weatherLabel, playlistsLabel, clearLabel,
                        appRowLabel, cacheLabel}
                : new String[] {wallpaperLabel, weatherLabel, playlistsLabel, appRowLabel, cacheLabel};
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(T.t("Options"))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String pick = items[which];
                        if (pick.equals(wallpaperLabel)) chooseWallpaper();
                        else if (pick.equals(playlistsLabel)) {
                            if (signedIn) showPlaylists();
                            else Ui.open(HomeActivity.this, new Intent(HomeActivity.this, LoginActivity.class));
                        } else if (pick.equals(clearLabel)) {
                            Ui.POOL.execute(new Runnable() {
                                @Override public void run() { history().clear(); refreshContinue(); }
                            });
                        } else if (pick.equals(weatherLabel)) {
                            weatherMenu();
                        } else if (pick.equals(cacheLabel)) {
                            Ui.POOL.execute(new Runnable() {
                                @Override public void run() { DiskCache.clear(); }
                            });
                            Ui.toast(HomeActivity.this, T.t("Cache cleared"));
                        } else {
                            Ui.open(HomeActivity.this, new Intent(HomeActivity.this, AppsActivity.class));
                        }
                    }
                }).show();
    }

    // ---- IPTV playlists ------------------------------------------------------------------

    /** Every playlist (the one in use is ticked) plus "Add a playlist". */
    private void showPlaylists() {
        final List<Xtream> all = Xtream.all(this);
        final String active = Xtream.activeId(this);
        final String[] labels = new String[all.size() + 1];
        for (int i = 0; i < all.size(); i++) {
            Xtream x = all.get(i);
            labels[i] = (x.id.equals(active) ? "✓  " : "      ") + x.label();
        }
        labels[all.size()] = "+  " + T.t("Add a playlist");
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(T.t("IPTV playlists"))
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == all.size()) {
                            Ui.open(HomeActivity.this, new Intent(HomeActivity.this, LoginActivity.class));
                        } else {
                            playlistMenu(all.get(which), all.get(which).id.equals(active));
                        }
                    }
                }).show();
    }

    private void playlistMenu(final Xtream x, boolean inUse) {
        final String[] items = inUse
                ? new String[] {T.t("Edit name or sign-in"), T.t("Remove this playlist")}
                : new String[] {T.t("Use this playlist"), T.t("Edit name or sign-in"), T.t("Remove this playlist")};
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(inUse ? T.f("%s (in use)", x.label()) : x.label())
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String pick = items[which];
                        if (pick.equals(T.t("Use this playlist"))) {
                            Xtream.setActive(HomeActivity.this, x.id);
                            Ui.toast(HomeActivity.this, T.f("Now using %s", x.label()));
                            refreshContinue();
                        } else if (pick.equals(T.t("Edit name or sign-in"))) {
                            Ui.open(HomeActivity.this, new Intent(HomeActivity.this, LoginActivity.class).putExtra("id", x.id));
                        } else {
                            confirmRemove(x);
                        }
                    }
                }).show();
    }

    private void confirmRemove(final Xtream x) {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(T.f("Remove %s?", x.label()))
                .setMessage(T.t("Its sign-in, Continue Watching and favourites are deleted from this stick. Your account with the provider isn't affected."))
                .setPositiveButton(T.t("Remove"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        Xtream.remove(HomeActivity.this, x.id);
                        Ui.toast(HomeActivity.this, T.f("%s removed", x.label()));
                        refreshContinue();
                    }
                })
                .setNegativeButton(T.t("Cancel"), null)
                .show();
    }

    // ---- Clock, Wi-Fi, weather -----------------------------------------------------------

    private void updateStatus() {
        Date now = new Date();
        time.setText(DateFormat.format(DateFormat.is24HourFormat(this) ? "H:mm" : "h:mm", now));
        String d = DateFormat.format("EEE d MMM", now).toString();
        if (weather.length() == 0) {
            date.setText(d);
        } else {
            // Date, then a small weather icon (same colour and height as the text), then city and temperature.
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(d + "     " + weather);
            Glyph icon = new Glyph(weatherIcon, date.getCurrentTextColor());
            int size = Math.round(date.getTextSize() * 1.25f);
            icon.setBounds(0, 0, size, size);
            int at = d.length() + 3;
            sb.setSpan(new android.text.style.ImageSpan(icon, android.text.style.ImageSpan.ALIGN_BOTTOM),
                    at, at + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            date.setText(sb);
        }

        int level = -1;
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo net = cm.getActiveNetworkInfo();
        boolean online = net != null && net.isConnected();
        if (online) {
            level = 3;
            if (net.getType() == ConnectivityManager.TYPE_WIFI) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                level = WifiManager.calculateSignalLevel(wm.getConnectionInfo().getRssi(), 4);
            }
        }
        wifiGlyph.setWifiLevel(level);
        if (online) maybeFetchWeather();
    }

    // ---- Weather city setting ----------------------------------------------------------------
    // "auto": the city of the TV's time zone (default). "city": one the user picked. "off": no weather.

    private String weatherSettingName() {
        android.content.SharedPreferences w = getSharedPreferences("weather", MODE_PRIVATE);
        String mode = w.getString("mode", "auto");
        if ("off".equals(mode)) return T.t("Off");
        if ("city".equals(mode)) return w.getString("cityName", "");
        return T.t("Automatic");
    }

    private void weatherMenu() {
        final String[] choices = {T.t("Automatic (from the time zone)"), T.t("Choose a city..."), T.t("Turn weather off")};
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(T.t("Weather city"))
                .setItems(choices, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == 0) setWeatherMode("auto");
                        else if (which == 2) setWeatherMode("off");
                        else askCity();
                    }
                }).show();
    }

    private void setWeatherMode(String mode) {
        getSharedPreferences("weather", MODE_PRIVATE).edit().putString("mode", mode).apply();
        refreshWeatherNow();
    }

    /** Drops the current reading and fetches again with the new setting. */
    private void refreshWeatherNow() {
        weather = "";
        weatherOkAt = 0;
        weatherTriedAt = 0;
        updateStatus();
    }

    private void askCity() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(T.t("Type a city name"));
        input.setSingleLine(true);
        input.setTextDirection(T.rtl() ? View.TEXT_DIRECTION_FIRST_STRONG_RTL : View.TEXT_DIRECTION_FIRST_STRONG_LTR);
        FrameLayout box = new FrameLayout(this);
        box.setPaddingRelative(px(48), px(16), px(48), 0);
        box.addView(input);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(T.t("Weather city"))
                .setView(box)
                .setPositiveButton(T.t("Search"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String q = input.getText().toString().trim();
                        if (q.length() > 0) searchCity(q);
                    }
                })
                .setNegativeButton(T.t("Cancel"), null)
                .show();
        input.requestFocus();
    }

    /** Looks the name up with Open-Meteo (up to 8 matches, in the system language) and lets you pick one. */
    private void searchCity(final String query) {
        Ui.toast(this, T.t("Searching..."));
        Ui.POOL.execute(new Runnable() {
            @Override public void run() {
                org.json.JSONArray found = null;
                try {
                    String lang = Locale.getDefault().getLanguage();
                    if ("in".equals(lang)) lang = "id";
                    String url = String.format(Locale.US, GEOCODE, lang, Xtream.enc(query)).replace("count=1", "count=8");
                    found = new JSONObject(fetch(url)).optJSONArray("results");
                } catch (Exception ignored) {
                    // shown as "not found" below
                }
                final org.json.JSONArray results = found;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (destroyed) return;
                        if (results == null || results.length() == 0) {
                            Ui.toast(HomeActivity.this, T.t("No city found with that name."));
                            return;
                        }
                        pickCity(results);
                    }
                });
            }
        });
    }

    private void pickCity(final org.json.JSONArray results) {
        final String[] labels = new String[results.length()];
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.optJSONObject(i);
            StringBuilder s = new StringBuilder(r.optString("name"));
            // Region and country, so places with the same name can be told apart.
            for (String k : new String[] {"admin1", "country"}) {
                String v = r.optString(k, "");
                if (v.length() > 0 && !v.equals(r.optString("name"))) s.append(", ").append(v);
            }
            labels[i] = s.toString();
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(T.t("Weather city"))
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        JSONObject r = results.optJSONObject(which);
                        getSharedPreferences("weather", MODE_PRIVATE).edit()
                                .putString("mode", "city")
                                .putString("cityName", r.optString("name"))
                                .putString("cityLat", String.valueOf(r.optDouble("latitude")))
                                .putString("cityLon", String.valueOf(r.optDouble("longitude")))
                                .apply();
                        refreshWeatherNow();
                    }
                }).show();
    }

    /** HTTPS first, plain HTTP if this device's TLS is too old for the server. */
    private static String fetch(String address) throws java.io.IOException {
        try {
            return Xtream.get("https://" + address);
        } catch (java.io.IOException tls) {
            return Xtream.get("http://" + address);
        }
    }

    /**
     * {name in the system language, latitude, longitude} for the city of the device's time zone
     * (e.g. Europe/Paris -> Paris), looked up once with Open-Meteo's free place-name search and saved.
     * Returns null for zones that aren't a city (UTC, GMT...): then no weather is shown.
     */
    private String[] weatherPlace() throws Exception {
        android.content.SharedPreferences w = getSharedPreferences("weather", MODE_PRIVATE);
        String mode = w.getString("mode", "auto");
        if ("off".equals(mode)) return null;
        if ("city".equals(mode)) {
            return new String[] {w.getString("cityName", ""), w.getString("cityLat", ""), w.getString("cityLon", "")};
        }
        String zone = java.util.TimeZone.getDefault().getID();
        String lang = Locale.getDefault().getLanguage();
        if ("in".equals(lang)) lang = "id";
        String key = zone + "|" + lang;
        android.content.SharedPreferences p = getSharedPreferences("weather", MODE_PRIVATE);
        if (key.equals(p.getString("key", null))) {
            return p.contains("lat") ? new String[] {p.getString("name", ""), p.getString("lat", ""), p.getString("lon", "")} : null;
        }
        int slash = zone.lastIndexOf('/');
        if (slash < 0) {
            p.edit().putString("key", key).remove("lat").apply();
            return null;
        }
        String city = zone.substring(slash + 1).replace('_', ' ');
        JSONObject g = new JSONObject(fetch(String.format(Locale.US, GEOCODE, lang, Xtream.enc(city))));
        org.json.JSONArray results = g.optJSONArray("results");
        JSONObject r = results == null ? null : results.optJSONObject(0);
        if (r == null) {
            p.edit().putString("key", key).remove("lat").apply();
            return null;
        }
        String[] place = {r.optString("name", city), String.valueOf(r.getDouble("latitude")), String.valueOf(r.getDouble("longitude"))};
        p.edit().putString("key", key).putString("name", place[0]).putString("lat", place[1]).putString("lon", place[2]).apply();
        return place;
    }

    private void maybeFetchWeather() {
        long now = System.currentTimeMillis();
        if (fetchingWeather || now - weatherOkAt < 3600000L || now - weatherTriedAt < 300000L) return;
        fetchingWeather = true;
        weatherTriedAt = now;
        Ui.POOL.execute(new Runnable() {
            @Override public void run() {
                String line = null;
                int icon = Glyph.SUN;
                try {
                    String[] place = weatherPlace();
                    if (place == null) throw new java.io.IOException("no city for this time zone");
                    String body = fetch(String.format(Locale.US, WEATHER, place[1], place[2]));
                    JSONObject cw = new JSONObject(body).getJSONObject("current_weather");
                    line = place[0] + String.format(Locale.getDefault(), " %d°C", Math.round(cw.getDouble("temperature")));
                    // Weather code (WMO) and day/night come in the same answer: no extra request.
                    icon = Glyph.forWeather(cw.optInt("weathercode", 0), cw.optInt("is_day", 1) == 1);
                } catch (Exception ignored) {
                    // keep the previous value
                }
                final String result = line;
                final int resultIcon = icon;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        fetchingWeather = false;
                        if (result == null || destroyed) return;
                        weather = result;
                        weatherIcon = resultIcon;
                        weatherOkAt = System.currentTimeMillis();
                        updateStatus();
                    }
                });
            }
        });
    }

    @SuppressWarnings("unused")
    private static Drawable none() { return null; }
}
