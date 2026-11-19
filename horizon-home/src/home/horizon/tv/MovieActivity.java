package home.horizon.tv;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

/** Movie page: poster, details, Play / Resume, Start over and a Favourite button (no colour keys needed). */
public class MovieActivity extends Activity {
    private JSONObject movie;
    private Xtream x;
    private History history;
    private String id;
    private TextView play;
    private TextView startOver;
    private TextView fav;
    private TextView meta;
    private TextView plot;
    private boolean destroyed;

    private int px(float v) { return Ui.px(this, v); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        x = Xtream.load(this);
        history = History.get(this);
        try {
            movie = new JSONObject(getIntent().getStringExtra("json"));
        } catch (Exception e) {
            finish();
            return;
        }
        id = movie.optString("stream_id");

        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(Ui.BG);
        root.setPaddingRelative(px(96), px(80), px(96), px(60));

        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(Ui.round(Theme.placeholder(), px(20)));
        poster.setClipToOutline(true);
        Images.load(poster, movie.optString("stream_icon"), px(420));
        root.addView(poster, new LinearLayout.LayoutParams(px(420), px(630)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, -1, 1f);
        ilp.setMarginStart(px(72));
        root.addView(info, ilp);

        TextView title = Ui.text(this, 56, Theme.text(), "sans-serif-light");
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setText(movie.optString("name"));
        info.addView(title);

        meta = Ui.text(this, 26, Theme.muted(), "sans-serif");
        String rating = movie.optString("rating", "");
        meta.setText(rating.length() > 0 && !"0".equals(rating) ? T.f("Rating %s", rating) : "");
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
        mlp.topMargin = px(16);
        info.addView(meta, mlp);

        plot = Ui.text(this, 26, Theme.text(), "sans-serif");
        plot.setMaxLines(7);
        plot.setEllipsize(TextUtils.TruncateAt.END);
        plot.setLineSpacing(px(8), 1f);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2);
        plp.topMargin = px(28);
        info.addView(plot, plp);

        info.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setClipChildren(false);
        play = Ui.button(this, T.t("Play"));
        play.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Ui.open(MovieActivity.this, PlayerActivity.movie(MovieActivity.this, x, movie)); }
        });
        buttons.addView(play);
        startOver = Ui.button(this, T.t("Start over"));
        startOver.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Ui.open(MovieActivity.this, PlayerActivity.movie(MovieActivity.this, x, movie).putExtra("fromStart", true));
            }
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.setMarginStart(px(20));
        buttons.addView(startOver, sl);
        fav = Ui.button(this, "");
        fav.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean now = history.toggleFavourite("vod", id, movie.toString());
                Ui.toast(MovieActivity.this, T.t(now ? "Added to Favourites" : "Removed from Favourites"));
                refresh();
            }
        });
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-2, -2);
        fl.setMarginStart(px(20));
        buttons.addView(fav, fl);
        info.addView(buttons, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        play.requestFocus();
        loadDetails();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }

    private void refresh() {
        History.Item seen = history.get("movie:" + id);
        boolean resumable = seen != null && seen.dur > 0 && seen.pos > 0 && !History.finished(seen);
        play.setText(resumable ? T.f("Resume (%s)", Ui.left(seen.pos, seen.dur)) : T.t("Play"));
        startOver.setVisibility(resumable ? View.VISIBLE : View.GONE);
        fav.setText(history.isFavourite("vod", id) ? "★  " + T.t("In Favourites") : "☆  " + T.t("Add to Favourites"));
    }

    /** Plot, genre and year, fetched when you open the page. */
    private void loadDetails() {
        if (x == null) return;
        Ui.POOL.execute(new Runnable() {
            @Override public void run() {
                JSONObject info = null;
                try {
                    JSONObject r = x.vodInfo(id);
                    info = r.optJSONObject("info");
                } catch (Exception ignored) {
                    // the page still works without details
                }
                final JSONObject result = info;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (destroyed || result == null) return;
                        StringBuilder m = new StringBuilder();
                        append(m, result.optString("releasedate", result.optString("release_date", "")));
                        append(m, result.optString("genre", ""));
                        append(m, result.optString("duration", ""));
                        String r = result.optString("rating", "");
                        if (r.length() > 0 && !"0".equals(r)) append(m, T.f("Rating %s", r));
                        if (m.length() > 0) meta.setText(m.toString());
                        String p = result.optString("plot", result.optString("description", ""));
                        if (p.length() > 0) plot.setText(p);
                    }
                });
            }
        });
    }

    private static void append(StringBuilder b, String s) {
        if (s == null || s.length() == 0 || "null".equals(s)) return;
        if (b.length() > 0) b.append("   ·   ");
        b.append(s);
    }
}
