package home.horizon.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One section (Live TV, Movies or Series): Search, Favourites, Recently watched and the provider's
 * categories on the left; channels / posters on the right. The red button toggles a favourite.
 */
public class BrowseActivity extends Activity {
    static final int MAX = 400;
    /** Fewer tiles for search results so they draw quickly while typing. */
    static final int SEARCH_MAX = 120;

    private static final int MODE_CATEGORY = 0, MODE_SEARCH = 1, MODE_FAVS = 2, MODE_RECENT = 3;

    private String kind;
    private Xtream x;
    private History history;
    private LinearLayout cats;
    private ListView list;
    private TileAdapter adapter;
    private TextView status;
    private EditText searchBox;
    private boolean destroyed;
    private int loadToken;
    private int mode = MODE_CATEGORY;

    /** Whole section for searching: one request, at most once a day, saved on the stick. */
    private JSONArray all;
    private boolean loadingAll;
    private String allError;
    private final Handler handler = new Handler();
    private final Map<View, TextView> badges = new HashMap<>();

    // ---- Sorting ----------------------------------------------------------------------------
    private static final int SORT_DEFAULT = 0, SORT_AZ = 1, SORT_ZA = 2, SORT_NEW = 3, SORT_RATING = 4;
    private static final String[] SORT_LABELS = {"Default", "A to Z", "Z to A", "Recently added", "Top rated"};
    private LinearLayout sortRow;
    private int sort = SORT_DEFAULT;
    /** What the grid is showing, unsorted, so a new sort can be applied without reloading. */
    private JSONArray currentArr;
    private String currentName;

    private final Runnable runSearch = new Runnable() {
        @Override public void run() { search(searchBox.getText().toString()); }
    };

    private int px(float v) { return Ui.px(this, v); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        kind = getIntent().getStringExtra("kind");
        x = Xtream.load(this);
        history = History.get(this);
        if (x == null) {
            Ui.open(this, new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(Ui.BG);

        ScrollView catScroll = new ScrollView(this);
        catScroll.setVerticalScrollBarEnabled(false);
        catScroll.setBackgroundColor(0xFF0D1117);
        catScroll.setPaddingRelative(px(36), px(56), px(36), px(56));
        catScroll.setClipToPadding(false);
        cats = new LinearLayout(this);
        cats.setOrientation(LinearLayout.VERTICAL);
        catScroll.addView(cats);
        root.addView(catScroll, new LinearLayout.LayoutParams(px(460), -1));

        // A recycling list: only the rows on screen exist, and they're reused while scrolling, so even huge
        // categories open instantly and memory stays flat. Heading, status, search box and sort bar are its
        // header, so they scroll up out of view together with the tiles.
        list = new ListView(this);
        list.setVerticalScrollBarEnabled(false);
        list.setClipToPadding(false);         // tiles run to the screen edges while scrolling
        list.setPaddingRelative(px(60), px(56), px(48), px(80));
        list.setItemsCanFocus(true);          // the tiles inside each row take focus, not the row
        list.setDivider(null);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        list.setCacheColorHint(0);
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        TextView heading = Ui.text(this, 52, Theme.text(), "sans-serif-light");
        heading.setText(sectionName());
        right.addView(heading);
        status = Ui.text(this, 24, Theme.muted(), "sans-serif");
        // Arabic category names would otherwise flip the whole line right-to-left.
        status.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = px(10);
        slp.bottomMargin = px(16);
        right.addView(status, slp);

        searchBox = new EditText(this);
        searchBox.setHint(T.f("Type to search %s", sectionNoun()));
        searchBox.setHintTextColor(0xFF6F7886);
        searchBox.setTextColor(Ui.TEXT);
        searchBox.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(28));
        searchBox.setSingleLine(true);
        searchBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBox.setPaddingRelative(px(24), 0, px(24), 0);
        StateListDrawable sbg = new StateListDrawable();
        GradientDrawable on = Ui.round(0xFF232A35, px(14));
        on.setStroke(px(3), Theme.text());
        sbg.addState(new int[] {android.R.attr.state_focused}, on);
        sbg.addState(new int[0], Ui.round(0xFF1C222B, px(14)));
        searchBox.setBackground(sbg);
        searchBox.setVisibility(View.GONE);
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                handler.removeCallbacks(runSearch);
                handler.postDelayed(runSearch, 500);
            }
        });
        // TV keyboards often hand over the full text only on Search/Done/Enter or when they close.
        searchBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                handler.removeCallbacks(runSearch);
                search(searchBox.getText().toString());
                return false;
            }
        });
        LinearLayout.LayoutParams sblp = new LinearLayout.LayoutParams(-1, px(80));
        sblp.bottomMargin = px(16);
        sblp.setMarginEnd(px(60));
        right.addView(searchBox, sblp);

        sortRow = new LinearLayout(this);
        sortRow.setOrientation(LinearLayout.HORIZONTAL);
        sortRow.setGravity(Gravity.CENTER_VERTICAL);
        sortRow.setVisibility(View.GONE);
        LinearLayout.LayoutParams srlp = new LinearLayout.LayoutParams(-2, -2);
        srlp.bottomMargin = px(26);
        right.addView(sortRow, srlp);
        sort = getSharedPreferences("browse", MODE_PRIVATE).getInt("sort_" + kind, SORT_DEFAULT);        buildSortRow();

        list.addHeaderView(right, null, false);
        adapter = new TileAdapter();
        list.setAdapter(adapter);
        if ("live".equals(kind)) {
            // Live TV: channel list on the left, a fixed preview window on the right.
            list.setPaddingRelative(px(48), px(56), px(24), px(80));
            LinearLayout content = new LinearLayout(this);
            content.addView(list, new LinearLayout.LayoutParams(0, -1, 1f));
            content.addView(buildPreview(), new LinearLayout.LayoutParams(px(780), -1));
            root.addView(content, new LinearLayout.LayoutParams(0, -1, 1f));
        } else {
            root.addView(list, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        setContentView(root);

        // Back on the first row of tiles (or the sort bar / search box): scroll all the way up so the heading
        // and sort bar show again.
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(new android.view.ViewTreeObserver.OnGlobalFocusChangeListener() {
            @Override public void onGlobalFocusChanged(View oldFocus, View newFocus) {
                if (newFocus == null || !isInside(newFocus, list)) return;
                int pos;
                try {
                    pos = list.getPositionForView(newFocus);
                } catch (RuntimeException e) {
                    return;
                }
                if (pos != ListView.INVALID_POSITION && pos <= list.getHeaderViewsCount()) {
                    list.post(new Runnable() {
                        @Override public void run() { list.smoothScrollToPositionFromTop(0, 0, 120); }
                    });
                }
            }
        });

        addSpecial(T.t("Search"), new Runnable() { @Override public void run() { openSearch(); } });
        addSpecial(T.t("Favourites"), new Runnable() { @Override public void run() { showFavourites(); } });
        addSpecial(T.t("Recently watched"), new Runnable() { @Override public void run() { showRecent(); } });
        TextView catHead = Ui.text(this, 20, Theme.muted(), "sans-serif-medium");
        catHead.setText(T.t("CATEGORIES"));
        catHead.setPaddingRelative(px(22), px(28), px(22), px(10));
        cats.addView(catHead);
        loadCategories();
        // Start the once-a-day search list quietly, so Search is usually ready when you get there.
        loadSearchList();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        releasePreview();
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        if (mode == MODE_RECENT) showRecent();
        else if (mode == MODE_FAVS) showFavourites();
        // Back from full screen (or from Home): carry on previewing the same channel.
        if (previewId != null) playPreview();
    }

    /** Stop the preview before anything else opens: most providers allow only one stream at a time. */
    @Override protected void onPause() {
        releasePreview();
        super.onPause();
    }

    // ---- Live TV preview ----------------------------------------------------------------------

    private androidx.media3.exoplayer.ExoPlayer preview;
    private android.view.SurfaceView previewSurface;
    private TextView previewHint;
    private TextView previewStatus;
    private TextView previewName;
    private TextView previewSub;
    private String previewId;
    private String previewUrl;
    private String previewAlt;
    private boolean previewTriedAlt;

    private View buildPreview() {
        LinearLayout pane = new LinearLayout(this);
        pane.setOrientation(LinearLayout.VERTICAL);
        pane.setPaddingRelative(0, px(150), px(60), 0);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(Ui.round(0xFF000000, px(18)));
        frame.setClipToOutline(true);
        previewSurface = new android.view.SurfaceView(this);
        frame.addView(previewSurface, new FrameLayout.LayoutParams(-1, -1));
        previewHint = Ui.text(this, 24, Ui.MUTED, "sans-serif");
        previewHint.setGravity(Gravity.CENTER);
        previewHint.setText(T.t("Press OK on a channel to preview it"));
        frame.addView(previewHint, new FrameLayout.LayoutParams(-1, -1));
        previewStatus = Ui.text(this, 22, 0xFFFFD27A, "sans-serif");
        previewStatus.setPaddingRelative(px(20), px(12), px(20), px(12));
        frame.addView(previewStatus, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START));
        pane.addView(frame, new LinearLayout.LayoutParams(px(720), px(405)));

        previewName = Ui.text(this, 36, Theme.text(), "sans-serif");
        previewName.setSingleLine(true);
        previewName.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(px(720), -2);
        nlp.topMargin = px(26);
        pane.addView(previewName, nlp);
        previewSub = Ui.text(this, 22, Theme.muted(), "sans-serif");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = px(10);
        pane.addView(previewSub, slp);
        return pane;
    }

    private void onChannelClick(JSONArray arr, JSONObject o, int index) {
        String id = o.optString("stream_id");
        if (id.equals(previewId) && preview != null) {
            Ui.open(this, PlayerActivity.live(this, arr, index));   // second OK: full screen
            return;
        }
        previewId = id;
        String fmt = Xtream.liveFormat(this);
        previewUrl = x.liveUrl(id, fmt);
        previewAlt = x.liveUrl(id, "m3u8".equals(fmt) ? "ts" : "m3u8");
        previewName.setText(o.optString("name"));
        previewSub.setText(T.t("Press OK again for full screen"));
        markPreviewRow();
        playPreview();
    }

    private void playPreview() {
        if (previewUrl == null) return;
        if (preview == null) {
            preview = Players.create(this, true);
            preview.setVideoSurfaceView(previewSurface);
            preview.addListener(new androidx.media3.common.Player.Listener() {
                @Override public void onPlaybackStateChanged(int s) {
                    if (s == androidx.media3.common.Player.STATE_BUFFERING) previewStatus.setText(T.t("Loading..."));
                    else if (s == androidx.media3.common.Player.STATE_READY) {
                        previewStatus.setText("");
                        previewHint.setVisibility(View.GONE);
                    } else if (s == androidx.media3.common.Player.STATE_ENDED) {
                        startPreviewStream(previewUrl);   // live feed dropped: reconnect
                    }
                }

                @Override public void onPlayerError(androidx.media3.common.PlaybackException e) {
                    if (!previewTriedAlt && previewAlt != null) {
                        previewTriedAlt = true;
                        startPreviewStream(previewAlt);
                    } else {
                        previewStatus.setText(T.t("This channel won't play right now."));
                    }
                }
            });
        }
        previewTriedAlt = false;
        startPreviewStream(previewUrl);
    }

    private void startPreviewStream(String url) {
        if (preview == null) return;
        previewStatus.setText(T.t("Loading..."));
        preview.stop();
        preview.setMediaItem(androidx.media3.common.MediaItem.fromUri(url));
        preview.prepare();
        preview.setPlayWhenReady(true);
    }

    private void releasePreview() {
        if (preview != null) {
            preview.release();
            preview = null;
        }
    }

    /** The channel being previewed is shown in the accent colour in the list (only rows on screen exist). */
    private void markPreviewRow() {
        for (int r = 0; r < list.getChildCount(); r++) {
            View row = list.getChildAt(r);
            if (!(row instanceof LinearLayout) || !(row.getTag() instanceof Integer)) continue;
            LinearLayout cells = (LinearLayout) row;
            for (int i = 0; i < cells.getChildCount(); i++) {
                Cell c = (Cell) cells.getChildAt(i).getTag();
                if (c != null && c.type == TYPE_CHANNEL && c.object != null) {
                    c.name.setTextColor(c.object.optString("stream_id").equals(previewId) ? Theme.accent : Ui.TEXT);
                }
            }
        }
    }

    /** The full-screen TV keyboard just closed: search with whatever text it handed over. */
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && mode == MODE_SEARCH) {
            handler.removeCallbacks(runSearch);
            handler.post(runSearch);
        }
    }

    private String lastQuery = "";

    private String sectionName() {
        return T.t("live".equals(kind) ? "Live TV" : "vod".equals(kind) ? "Movies" : "Series");
    }

    /** "channels", "movies" or "series" in the current language (for sentences like "Search all ..."). */
    private String sectionNoun() {
        return T.t("live".equals(kind) ? "channels" : "vod".equals(kind) ? "movies" : "series");
    }

    private String historyKind() {
        return "live".equals(kind) ? "live" : "vod".equals(kind) ? "movie" : "episode";
    }

    private String idOf(JSONObject o) {
        return "series".equals(kind) ? o.optString("series_id") : o.optString("stream_id");
    }

    // ---- Left column ----------------------------------------------------------------------

    private TextView listButton(String label) {
        TextView t = Ui.text(this, 26, Ui.TEXT, "sans-serif");
        t.setText(label);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPaddingRelative(px(22), px(18), px(22), px(18));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, Ui.round(Theme.glassFocus(), px(14)));
        bg.addState(new int[0], Ui.round(0x00000000, px(14)));
        t.setBackground(bg);
        Ui.focusable(t);
        cats.addView(t, new LinearLayout.LayoutParams(-1, -2));
        return t;
    }

    private void addSpecial(String label, final Runnable action) {
        TextView t = listButton(label);
        t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
    }

    private void loadCategories() {
        status.setText(T.t("Loading categories..."));
        Ui.POOL.execute(new Runnable() {
            @Override public void run() {
                JSONArray arr = null;
                String error = null;
                try {
                    arr = x.categories(kind);
                } catch (Exception e) {
                    error = e.getMessage();
                }
                final JSONArray result = arr;
                final String err = error;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (destroyed) return;
                        if (result == null) {
                            status.setText(T.f("Could not load categories: %s", err));
                            return;
                        }
                        showCategories(result);
                    }
                });
            }
        });
    }

    private void showCategories(JSONArray arr) {
        View first = null;
        JSONObject firstCat = null;
        for (int i = 0; i < arr.length(); i++) {
            final JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            final String id = o.optString("category_id");
            final String name = o.optString("category_name", "Category");
            TextView t = listButton(name);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { loadItems(id, name); }
            });
            if (first == null) {
                first = t;
                firstCat = o;
            }
        }
        if (first == null) {
            status.setText(T.t("Your provider has no categories here."));
            return;
        }
        first.requestFocus();
        loadItems(firstCat.optString("category_id"), firstCat.optString("category_name", ""));
    }

    // ---- Category / search / favourites / recent ------------------------------------------

    private void loadItems(final String categoryId, final String name) {
        mode = MODE_CATEGORY;
        searchBox.setVisibility(View.GONE);
        final int token = ++loadToken;
        status.setText(T.f("%s - loading...", name));
        Ui.POOL.execute(new Runnable() {
            @Override public void run() {
                JSONArray arr = null;
                String error = null;
                try {
                    arr = x.items(kind, categoryId);
                } catch (Exception e) {
                    error = e.getMessage();
                }
                final JSONArray result = arr;
                final String err = error;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (destroyed || token != loadToken) return;
                        if (result == null) {
                            status.setText(T.f("Could not load %1$s: %2$s", name, err));
                            return;
                        }
                        showItems(result, name);
                    }
                });
            }
        });
    }

    private void openSearch() {
        mode = MODE_SEARCH;
        ++loadToken;
        searchBox.setVisibility(View.VISIBLE);
        searchBox.requestFocus();
        adapter.clear();
        sortRow.setVisibility(View.GONE);
        currentArr = null;
        status.setText(searchStatus());
        if (all == null && !loadingAll) loadSearchList();
    }

    private String searchStatus() {
        String what = sectionNoun();
        if (all != null) return T.f("%1$d %2$s ready to search.", all.length(), what);
        if (loadingAll) {
            return T.f("Getting today's list of %1$s (once a day): %2$.1f MB so far - you can type now.", what, Xtream.progressBytes / (1024f * 1024f));
        }
        if (allError != null) return T.f("Could not get the list for searching: %s", allError);
        return T.f("Search all %s by name.", what);
    }

    /** One request for the whole section (at most once a day); started in the background when the section opens. */
    /** While the list downloads, refresh the progress line once a second (only while Search is open). */
    private final Runnable progress = new Runnable() {
        @Override public void run() {
            if (destroyed || !loadingAll) return;
            if (mode == MODE_SEARCH && searchBox.getText().toString().trim().length() < 2) status.setText(searchStatus());
            handler.postDelayed(this, 1000);
        }
    };

    private void loadSearchList() {
        loadingAll = true;
        allError = null;
        handler.post(progress);
        Ui.POOL.execute(new Runnable() {
            @Override public void run() {
                JSONArray arr = null;
                String error = null;
                try {
                    arr = x.searchList(kind);
                } catch (Exception e) {
                    error = e.getMessage();
                } catch (OutOfMemoryError e) {
                    error = T.t("the list is too large for this device");
                }
                final JSONArray result = arr;
                final String err = error;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        loadingAll = false;
                        if (destroyed) return;
                        all = result;
                        allError = err;
                        if (mode != MODE_SEARCH) return;
                        if (searchBox.getText().toString().trim().length() >= 2) search(searchBox.getText().toString());
                        else status.setText(searchStatus());
                    }
                });
            }
        });
    }

    /** Filters the saved list on the stick: typing never sends anything to the provider. */
    private void search(final String query) {
        if (mode != MODE_SEARCH) return;
        final String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.length() < 2 || all == null) {
            adapter.clear();
            sortRow.setVisibility(View.GONE);
            currentArr = null;
            lastQuery = "";
            status.setText(searchStatus() + (q.length() < 2 ? "  " + T.t("Type at least 2 letters.") : ""));
            return;
        }
        if (q.equals(lastQuery) && adapter.getCount() > 0) return;  // same text: results already showing
        lastQuery = q;
        final int token = ++loadToken;
        final JSONArray source = all;
        Ui.POOL.execute(new Runnable() {
            @Override public void run() {
                final JSONArray found = new JSONArray();
                for (int i = 0; i < source.length() && found.length() < SEARCH_MAX; i++) {
                    JSONObject o = source.optJSONObject(i);
                    if (o != null && o.optString("name").toLowerCase(Locale.ROOT).contains(q)) found.put(o);
                }
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (destroyed || token != loadToken || mode != MODE_SEARCH) return;
                        showItems(found, T.f("Results for \"%s\"", query.trim()));
                    }
                });
            }
        });
    }

    private void showFavourites() {
        mode = MODE_FAVS;
        ++loadToken;
        searchBox.setVisibility(View.GONE);
        JSONArray arr = new JSONArray();
        for (String json : history.favourites(kind)) {
            try {
                arr.put(new JSONObject(json));
            } catch (Exception ignored) {
                // skip a damaged entry
            }
        }
        if (arr.length() == 0) {
            adapter.clear();
            sortRow.setVisibility(View.GONE);
            currentArr = null;
            status.setText(T.t("No favourites yet. Hold OK on anything (or use the Favourite button on a movie or series page)."));
            return;
        }
        showItems(arr, T.t("Favourites"));
    }

    private void showRecent() {
        mode = MODE_RECENT;
        ++loadToken;
        searchBox.setVisibility(View.GONE);
        List<History.Item> items = history.recentOfKind(historyKind(), 60);
        if ("live".equals(kind) && !items.isEmpty()) {
            // Channels: same list-and-preview layout as the categories, newest first.
            JSONArray arr = new JSONArray();
            for (History.Item it : items) {
                if (it.key == null || !it.key.startsWith("live:")) continue;
                try {
                    arr.put(new JSONObject().put("stream_id", it.key.substring(5))
                            .put("name", it.title).put("stream_icon", it.image));
                } catch (Exception ignored) {
                    // skip a damaged entry
                }
            }
            int keep = sort;
            sort = SORT_DEFAULT;
            showItems(arr, T.t("Recently watched"));
            sort = keep;
            sortRow.setVisibility(View.GONE);
            currentArr = null;
            return;
        }
        sortRow.setVisibility(View.GONE);   // always newest first
        currentArr = null;
        if (items.isEmpty()) {
            adapter.clear();
            status.setText(T.t("Nothing watched here yet."));
            return;
        }
        status.setText(T.t("Recently watched") + " - " + items.size());
        adapter.showRecent(items);
        list.setSelection(0);
    }

    // ---- Sort bar -----------------------------------------------------------------------

    private static boolean isInside(View v, View parent) {
        for (View p = v; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) {
            if (p == parent) return true;
        }
        return false;
    }

    private void buildSortRow() {
        sortRow.removeAllViews();
        TextView label = Ui.text(this, 22, Theme.muted(), "sans-serif-medium");
        label.setText(T.t("SORT"));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
        llp.setMarginEnd(px(18));
        sortRow.addView(label, llp);
        for (int i = 0; i < SORT_LABELS.length; i++) {
            if (i == SORT_RATING && "live".equals(kind)) continue;   // channels have no rating
            final int option = i;
            TextView chip = Ui.text(this, 22, option == sort ? 0xFF11151B : Theme.text(), "sans-serif-medium");
            // Live TV's column is narrower: shorter label so the chips fit on one line.
            chip.setText(T.t(i == SORT_NEW && "live".equals(kind) ? "Newest" : SORT_LABELS[i]));
            chip.setSingleLine(true);
            chip.setPaddingRelative(px(26), px(12), px(26), px(12));
            int fill = option == sort ? Theme.accent : Theme.glass();
            GradientDrawable on = Ui.round(option == sort ? Theme.accent : Theme.glassFocus(), px(30));
            on.setStroke(px(3), Theme.text());
            StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[] {android.R.attr.state_focused}, on);
            bg.addState(new int[0], Ui.round(fill, px(30)));
            chip.setBackground(bg);
            Ui.focusable(chip);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { setSort(option); }
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
            clp.setMarginEnd(px(14));
            sortRow.addView(chip, clp);
        }
    }

    private void setSort(int option) {
        if (option == sort) return;
        sort = option;
        getSharedPreferences("browse", MODE_PRIVATE).edit().putInt("sort_" + kind, sort).apply();
        int focused = sortRow.indexOfChild(getCurrentFocus());
        buildSortRow();
        if (focused >= 0 && focused < sortRow.getChildCount()) sortRow.getChildAt(focused).requestFocus();
        if (currentArr != null) render(currentArr, currentName);
    }

    private static long number(JSONObject o, String... keys) {
        for (String k : keys) {
            String v = o.optString(k, "");
            if (v.length() == 0) continue;
            try {
                return (long) (Double.parseDouble(v) * 100);
            } catch (NumberFormatException ignored) {
                // try the next key
            }
        }
        return 0;
    }

    /** A sorted copy; the provider's own order is kept for "Default" and for ties. */
    private JSONArray sorted(JSONArray arr) {
        if (sort == SORT_DEFAULT) return arr;
        java.util.List<JSONObject> list = new java.util.ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) list.add(o);
        }
        final int s = sort;
        java.util.Collections.sort(list, new java.util.Comparator<JSONObject>() {
            @Override public int compare(JSONObject a, JSONObject b) {
                switch (s) {
                    case SORT_AZ: return a.optString("name").trim().compareToIgnoreCase(b.optString("name").trim());
                    case SORT_ZA: return b.optString("name").trim().compareToIgnoreCase(a.optString("name").trim());
                    case SORT_NEW: return Long.compare(number(b, "added", "last_modified"), number(a, "added", "last_modified"));
                    default: return Long.compare(number(b, "rating_5based", "rating"), number(a, "rating_5based", "rating"));
                }
            }
        });
        JSONArray out = new JSONArray();
        for (JSONObject o : list) out.put(o);
        return out;
    }

    // ---- Tiles --------------------------------------------------------------------------

    private void showItems(final JSONArray arr, String name) {
        currentArr = arr;
        currentName = name;
        sortRow.setVisibility(View.VISIBLE);
        render(arr, name);
    }

    private void render(JSONArray unsorted, String name) {
        final JSONArray arr = sorted(unsorted);
        // FSI/PDI isolate the (often Arabic) name so it can't reorder the English around it.
        status.setText("⁨" + name + "⁩ - " + arr.length() + "   |   " + T.t("hold OK: favourite"));
        adapter.showItems(arr, "live".equals(kind));
        list.setSelection(0);
    }

    // ---- Recycling list ---------------------------------------------------------------------
    // Each list row holds several tiles (6 posters, 4 recently-watched cards, or 1 channel row). Rows and their
    // tiles are created once and only re-filled ("bound") with new data as you scroll.

    private static final int TYPE_POSTER = 0, TYPE_CHANNEL = 1, TYPE_RECENT = 2;

    /** The views inside one tile, kept so a reused tile can be re-filled quickly. */
    private static final class Cell {
        int type;
        int index = -1;
        JSONObject object;
        History.Item item;
        View holder;
        View tile;
        ImageView image;
        TextView name;
        TextView sub;
        TextView number;
        TextView badge;
        LinearLayout bar;
        View fill;
        View rest;
    }

    private final class TileAdapter extends android.widget.BaseAdapter {
        JSONArray items = new JSONArray();
        List<History.Item> recent;
        boolean live;
        final java.util.Set<String> favs = new java.util.HashSet<>();

        void clear() {
            items = new JSONArray();
            recent = null;
            notifyDataSetChanged();
        }

        void showItems(JSONArray arr, boolean isLive) {
            items = arr;
            recent = null;
            live = isLive;
            favs.clear();
            for (String json : history.favourites(kind)) {
                try {
                    favs.add(idOf(new JSONObject(json)));
                } catch (Exception ignored) {
                    // skip a damaged entry
                }
            }
            notifyDataSetChanged();
        }

        void showRecent(List<History.Item> r) {
            recent = r;
            items = new JSONArray();
            live = false;
            notifyDataSetChanged();
        }

        int type() { return recent != null ? TYPE_RECENT : live ? TYPE_CHANNEL : TYPE_POSTER; }

        int cols(int type) { return type == TYPE_POSTER ? 6 : type == TYPE_RECENT ? 4 : 1; }

        int size() { return recent != null ? recent.size() : items.length(); }

        @Override public int getCount() {
            int c = cols(type());
            return (size() + c - 1) / c;
        }

        @Override public Object getItem(int position) { return null; }

        @Override public long getItemId(int position) { return position; }

        @Override public int getViewTypeCount() { return 3; }

        @Override public int getItemViewType(int position) { return type(); }

        @Override public View getView(int position, View convert, android.view.ViewGroup parent) {
            int type = type();
            LinearLayout row = convert instanceof LinearLayout ? (LinearLayout) convert : makeRow(type);
            row.setTag(position);
            int cols = cols(type);
            for (int i = 0; i < cols; i++) bind((Cell) row.getChildAt(i).getTag(), position * cols + i);
            return row;
        }

        private LinearLayout makeRow(int type) {
            LinearLayout row = new LinearLayout(BrowseActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPaddingRelative(0, 0, 0, type == TYPE_CHANNEL ? px(10) : px(28));
            int cols = cols(type);
            for (int i = 0; i < cols; i++) {
                Cell c = makeCell(type);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                if (i < cols - 1) lp.setMarginEnd(px(24));
                row.addView(c.holder, lp);
            }
            return row;
        }

        private Cell makeCell(final int type) {
            final Cell c = new Cell();
            c.type = type;
            FrameLayout holder = new FrameLayout(BrowseActivity.this);
            holder.setClipChildren(false);
            c.holder = holder;
            holder.setTag(c);
            if (type == TYPE_CHANNEL) {
                LinearLayout row = new LinearLayout(BrowseActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPaddingRelative(px(18), px(12), px(64), px(12));   // room on the right for the ★ badge
                row.setBackground(Ui.glass(BrowseActivity.this, 16));
                c.number = Ui.text(BrowseActivity.this, 22, Theme.muted(), "sans-serif");
                c.number.setGravity(Gravity.CENTER);
                row.addView(c.number, new LinearLayout.LayoutParams(px(64), -2));
                c.image = new ImageView(BrowseActivity.this);
                c.image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(px(88), px(50));
                llp.setMarginEnd(px(20));
                row.addView(c.image, llp);
                c.name = Ui.text(BrowseActivity.this, 25, Ui.TEXT, "sans-serif");
                c.name.setSingleLine(true);
                c.name.setEllipsize(TextUtils.TruncateAt.END);
                c.name.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                c.name.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                row.addView(c.name, new LinearLayout.LayoutParams(0, -2, 1f));
                c.tile = row;
                holder.addView(row, new FrameLayout.LayoutParams(px(600), px(78)));
            } else {
                boolean recentType = type == TYPE_RECENT;
                int w = recentType ? px(300) : px(196);
                LinearLayout t = new LinearLayout(BrowseActivity.this);
                t.setOrientation(LinearLayout.VERTICAL);
                FrameLayout frame = framed();
                c.image = new ImageView(BrowseActivity.this);
                frame.addView(c.image, new FrameLayout.LayoutParams(-1, -1));
                t.addView(frame, new LinearLayout.LayoutParams(w, recentType ? px(170) : px(294)));
                if (recentType) {
                    c.bar = new LinearLayout(BrowseActivity.this);
                    c.bar.setBackground(Ui.round(Ui.TRACK, px(3)));
                    c.fill = new View(BrowseActivity.this);
                    c.fill.setBackground(Ui.round(Theme.text(), px(3)));
                    c.rest = new View(BrowseActivity.this);
                    c.bar.addView(c.fill, new LinearLayout.LayoutParams(0, -1, 0.5f));
                    c.bar.addView(c.rest, new LinearLayout.LayoutParams(0, -1, 0.5f));
                    LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(w, px(5));
                    barLp.topMargin = px(10);
                    t.addView(c.bar, barLp);
                }
                c.name = Ui.text(BrowseActivity.this, recentType ? 22 : 20, Ui.TEXT, "sans-serif");
                if (recentType) c.name.setSingleLine(true); else c.name.setMaxLines(2);
                c.name.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(w, -2);
                nlp.topMargin = px(10);
                t.addView(c.name, nlp);
                if (recentType) {
                    c.sub = Ui.text(BrowseActivity.this, 18, Ui.MUTED, "sans-serif");
                    c.sub.setSingleLine(true);
                    LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(w, -2);
                    slp.topMargin = px(4);
                    t.addView(c.sub, slp);
                }
                c.tile = t;
                holder.addView(t, new FrameLayout.LayoutParams(-2, -2));
            }
            Ui.focusable(c.tile);
            if (type != TYPE_RECENT) {
                c.badge = Ui.text(BrowseActivity.this, 18, 0xFF11151B, "sans-serif-medium");
                c.badge.setText("★");
                c.badge.setGravity(Gravity.CENTER);
                c.badge.setBackground(Ui.round(Theme.accent, px(16)));
                FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(px(32), px(32),
                        type == TYPE_CHANNEL ? Gravity.CENTER_VERTICAL | Gravity.END : Gravity.TOP | Gravity.END);
                blp.topMargin = type == TYPE_CHANNEL ? 0 : px(8);
                blp.setMarginEnd(type == TYPE_CHANNEL ? px(18) : px(8));
                holder.addView(c.badge, blp);
                badges.put(c.tile, c.badge);
            }
            c.tile.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (c.index < 0) return;
                    if (c.type == TYPE_RECENT) {
                        Ui.open(BrowseActivity.this, PlayerActivity.resume(BrowseActivity.this, c.item));
                    } else if (c.type == TYPE_CHANNEL) {
                        onChannelClick(items, c.object, c.index);
                    } else {
                        open(items, c.object, c.index);
                    }
                }
            });
            if (type != TYPE_RECENT) {
                c.tile.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View v) { toggleFavourite(v); return true; }
                });
            }
            return c;
        }

        private void bind(Cell c, int index) {
            if (index >= size()) {                 // empty slot at the end of the last row
                c.index = -1;
                c.object = null;
                c.item = null;
                c.tile.setTag(null);
                c.holder.setVisibility(View.INVISIBLE);
                c.tile.setFocusable(false);
                return;
            }
            c.index = index;
            c.holder.setVisibility(View.VISIBLE);
            c.tile.setFocusable(true);
            if (c.type == TYPE_RECENT) {
                History.Item it = recent.get(index);
                c.item = it;
                c.object = null;
                boolean liveItem = "live".equals(it.kind);
                c.image.setScaleType(liveItem ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
                c.image.setPaddingRelative(liveItem ? px(50) : 0, liveItem ? px(30) : 0, liveItem ? px(50) : 0, liveItem ? px(30) : 0);
                Images.load(c.image, it.image, px(300));
                if (!liveItem && it.dur > 0) {
                    float done = Math.min(1f, Math.max(0.03f, it.pos / (float) it.dur));
                    ((LinearLayout.LayoutParams) c.fill.getLayoutParams()).weight = done;
                    ((LinearLayout.LayoutParams) c.rest.getLayoutParams()).weight = 1f - done;
                    c.bar.setVisibility(View.VISIBLE);
                    c.bar.requestLayout();
                } else {
                    c.bar.setVisibility(View.GONE);
                }
                c.name.setText(it.title);
                String extra = liveItem ? T.t("Live") : History.finished(it) ? T.t("Watched")
                        : it.dur > 0 ? Ui.left(it.pos, it.dur) : "";
                c.sub.setText(it.sub == null || it.sub.isEmpty() || liveItem ? extra : it.sub + " - " + extra);
                return;
            }
            JSONObject o = items.optJSONObject(index);
            c.object = o;
            c.item = null;
            c.tile.setTag(o);                      // used by the favourite (menu / red) button
            if (o == null) return;
            c.badge.setVisibility(favs.contains(idOf(o)) ? View.VISIBLE : View.GONE);
            c.name.setText(o.optString("name"));
            if (c.type == TYPE_CHANNEL) {
                c.number.setText(String.valueOf(index + 1));
                Images.load(c.image, o.optString("stream_icon"), px(120));
                c.name.setTextColor(o.optString("stream_id").equals(previewId) ? Theme.accent : Ui.TEXT);
            } else {
                c.image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Images.load(c.image, "series".equals(kind) ? o.optString("cover") : o.optString("stream_icon"), px(200));
            }
        }
    }

    private FrameLayout framed() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(Ui.round(Theme.placeholder(), px(14)));
        frame.setClipToOutline(true);
        GradientDrawable ring = Ui.round(0x00000000, px(14));
        ring.setStroke(px(4), Theme.text());
        StateListDrawable fg = new StateListDrawable();
        fg.addState(new int[] {android.R.attr.state_focused}, ring);
        fg.addState(new int[0], Ui.round(0x00000000, px(14)));
        frame.setForeground(fg);
        frame.setDuplicateParentStateEnabled(true);
        return frame;
    }

    private void open(JSONArray arr, JSONObject o, int index) {
        if ("live".equals(kind)) {
            Ui.open(this, PlayerActivity.live(this, arr, index));
        } else if ("vod".equals(kind)) {
            Ui.open(this, new Intent(this, MovieActivity.class).putExtra("json", o.toString()));
        } else {
            Ui.open(this, new Intent(this, SeriesActivity.class)
                    .putExtra("id", o.optString("series_id"))
                    .putExtra("name", o.optString("name"))
                    .putExtra("cover", o.optString("cover"))
                    .putExtra("json", o.toString()));
        }
    }

    // ---- Red button: favourite -------------------------------------------------------------

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (Ui.isOptionsKey(e.getKeyCode())) {
            View f = getCurrentFocus();
            if (f == null || !(f.getTag() instanceof JSONObject)) return super.dispatchKeyEvent(e);
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) toggleFavourite(f);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    /** Hold OK (or the menu button) on a tile to add or remove it from Favourites. */
    private void toggleFavourite(View f) {
        if (!(f.getTag() instanceof JSONObject)) return;
        JSONObject o = (JSONObject) f.getTag();
        boolean now = history.toggleFavourite(kind, idOf(o), o.toString());
        if (now) adapter.favs.add(idOf(o)); else adapter.favs.remove(idOf(o));
        TextView badge = badges.get(f);
        if (badge != null) badge.setVisibility(now ? View.VISIBLE : View.GONE);
        Ui.toast(this, now ? T.f("%s added to Favourites", o.optString("name"))
                : T.f("%s removed from Favourites", o.optString("name")));
        if (!now && mode == MODE_FAVS) showFavourites();
    }
}
