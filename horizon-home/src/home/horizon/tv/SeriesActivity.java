package home.horizon.tv;

import android.app.Activity;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/** One series: seasons on the left, episodes (with how far you got) on the right. */
public class SeriesActivity extends Activity {
    private Xtream x;
    private History history;
    private String name;
    private String cover;
    private LinearLayout seasons;
    private LinearLayout episodes;
    private TextView status;
    private JSONObject episodesBySeason;
    private boolean destroyed;
    private String openSeason;

    private int px(float v) { return Ui.px(this, v); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        x = Xtream.load(this);
        history = History.get(this);
        name = getIntent().getStringExtra("name");
        cover = getIntent().getStringExtra("cover");
        final String id = getIntent().getStringExtra("id");

        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setBackgroundColor(0xFF0D1117);
        left.setPaddingRelative(px(48), px(56), px(48), px(40));
        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(Ui.round(0xFF1A1F27, px(16)));
        poster.setClipToOutline(true);
        Images.load(poster, cover, px(260));
        left.addView(poster, new LinearLayout.LayoutParams(px(240), px(360)));
        TextView title = Ui.text(this, 36, Ui.TEXT, "sans-serif-light");
        title.setText(name);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = px(24);
        tlp.bottomMargin = px(18);
        left.addView(title, tlp);

        // Favourite button for remotes without colour keys.
        final String seriesJson = getIntent().getStringExtra("json");
        final TextView favButton = Ui.button(this, "");
        favButton.setVisibility(seriesJson == null ? View.GONE : View.VISIBLE);
        favButton.setText(history.isFavourite("series", id) ? "★  " + T.t("In Favourites") : "☆  " + T.t("Add to Favourites"));
        favButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean now = history.toggleFavourite("series", id, seriesJson);
                favButton.setText(now ? "★  " + T.t("In Favourites") : "☆  " + T.t("Add to Favourites"));
                Ui.toast(SeriesActivity.this, T.t(now ? "Added to Favourites" : "Removed from Favourites"));
            }
        });
        LinearLayout.LayoutParams fblp = new LinearLayout.LayoutParams(-1, -2);
        fblp.bottomMargin = px(20);
        left.addView(favButton, fblp);

        ScrollView seasonScroll = new ScrollView(this);
        seasonScroll.setVerticalScrollBarEnabled(false);
        seasons = new LinearLayout(this);
        seasons.setOrientation(LinearLayout.VERTICAL);
        seasonScroll.addView(seasons);
        left.addView(seasonScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(left, new LinearLayout.LayoutParams(px(400), -1));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPaddingRelative(px(56), px(56), px(56), 0);
        status = Ui.text(this, 24, Ui.MUTED, "sans-serif");
        status.setText(T.t("Loading episodes..."));
        right.addView(status);
        ScrollView epScroll = new ScrollView(this);
        epScroll.setVerticalScrollBarEnabled(false);
        epScroll.setClipToPadding(false);
        epScroll.setPaddingRelative(px(12), px(20), px(12), px(60));
        episodes = new LinearLayout(this);
        episodes.setOrientation(LinearLayout.VERTICAL);
        episodes.setClipChildren(false);
        epScroll.addView(episodes);
        right.addView(epScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(right, new LinearLayout.LayoutParams(0, -1, 1f));
        setContentView(root);

        Ui.POOL.execute(new Runnable() {
            @Override public void run() {
                JSONObject info = null;
                String error = null;
                try {
                    info = x.seriesInfo(id);
                } catch (Exception e) {
                    error = e.getMessage();
                }
                final JSONObject result = info;
                final String err = error;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (destroyed) return;
                        if (result == null || result.optJSONObject("episodes") == null) {
                            status.setText(T.t("Could not load this series") + (err == null ? "." : ": " + err));
                            return;
                        }
                        episodesBySeason = result.optJSONObject("episodes");
                        showSeasons();
                    }
                });
            }
        });
    }

    @Override protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        if (openSeason != null) showEpisodes(openSeason, false);
    }

    private void showSeasons() {
        List<String> keys = new ArrayList<>();
        Iterator<String> it = episodesBySeason.keys();
        while (it.hasNext()) keys.add(it.next());
        Collections.sort(keys, new Comparator<String>() {
            @Override public int compare(String a, String b) { return num(a) - num(b); }
        });
        seasons.removeAllViews();
        for (final String key : keys) {
            TextView t = Ui.text(this, 26, Ui.TEXT, "sans-serif");
            t.setText(T.f("Season %s", key));
            t.setPaddingRelative(px(22), px(16), px(22), px(16));
            StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[] {android.R.attr.state_focused}, Ui.round(0xFF2E3644, px(14)));
            bg.addState(new int[0], Ui.round(0x00000000, px(14)));
            t.setBackground(bg);
            Ui.focusable(t);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showEpisodes(key, true); }
            });
            seasons.addView(t, new LinearLayout.LayoutParams(-1, -2));
        }
        if (!keys.isEmpty()) {
            seasons.getChildAt(0).requestFocus();
            showEpisodes(keys.get(0), false);
        }
    }

    private void showEpisodes(final String season, boolean focusFirst) {
        openSeason = season;
        episodes.removeAllViews();
        JSONArray list = episodesBySeason.optJSONArray(season);
        if (list == null) return;
        status.setText(name + " - " + T.f("Season %s", season) + " - " + T.f("%d episodes", list.length()));
        for (int i = 0; i < list.length(); i++) {
            final JSONObject ep = list.optJSONObject(i);
            if (ep == null) continue;
            episodes.addView(episodeRow(season, ep));
        }
        if (focusFirst && episodes.getChildCount() > 0) episodes.getChildAt(0).requestFocus();
    }

    private View episodeRow(final String season, final JSONObject ep) {
        JSONObject info = ep.optJSONObject("info");
        String thumb = info == null ? "" : info.optString("movie_image", "");
        if (thumb.length() == 0) thumb = cover;
        History.Item seen = history.get("ep:" + ep.optString("id"));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPaddingRelative(px(16), px(16), px(24), px(16));
        row.setBackground(Ui.glass(this, 18));
        Ui.focusable(row);
        Ui.lift(row, 1.02f, null);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(Ui.round(0xFF1A1F27, px(10)));
        frame.setClipToOutline(true);
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Images.load(img, thumb, px(260));
        frame.addView(img, new FrameLayout.LayoutParams(-1, -1));
        row.addView(frame, new LinearLayout.LayoutParams(px(256), px(144)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(this, 28, Ui.TEXT, "sans-serif");
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setText("E" + ep.optString("episode_num") + "   " + ep.optString("title"));
        text.addView(t);
        TextView sub = Ui.text(this, 22, Ui.MUTED, "sans-serif");
        long secs = info == null ? 0 : info.optLong("duration_secs", 0);
        String line = secs > 0 ? T.f("%d min", secs / 60) : "";
        if (seen != null && seen.dur > 0) {
            line = History.finished(seen) ? T.t("Watched") : Ui.left(seen.pos, seen.dur);
        }
        sub.setText(line);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = px(10);
        text.addView(sub, slp);
        if (seen != null && seen.dur > 0 && !History.finished(seen)) {
            LinearLayout bar = new LinearLayout(this);
            bar.setBackground(Ui.round(Ui.TRACK, px(3)));
            View fill = new View(this);
            fill.setBackground(Ui.round(0xFFFFFFFF, px(3)));
            float done = Math.max(0.03f, seen.pos / (float) seen.dur);
            bar.addView(fill, new LinearLayout.LayoutParams(0, -1, done));
            bar.addView(new View(this), new LinearLayout.LayoutParams(0, -1, 1f - done));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(px(360), px(5));
            blp.topMargin = px(12);
            text.addView(bar, blp);
        }
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.setMarginStart(px(28));
        row.addView(text, tlp);

        final String image = thumb;
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Ui.open(SeriesActivity.this, PlayerActivity.episode(SeriesActivity.this, x, name, season, ep, image));
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = px(18);
        row.setLayoutParams(lp);
        return row;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 9999;
        }
    }
}
