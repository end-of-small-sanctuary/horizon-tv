package home.horizon.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Full-screen player built on ExoPlayer (hardware decoding through MediaCodec).
 * ExoPlayer keeps its buffer inside this app with a fixed size cap, unlike the stick's built-in
 * Amlogic player, whose memory kept growing until the stick ran out of RAM and stuttered.
 * Live: Up/Down change channel. Movies and episodes: Left/Right skip, OK pauses, position is saved for resuming.
 */
public class PlayerActivity extends Activity {

    // ---- Ways to open the player --------------------------------------------------------

    static Intent live(Context c, JSONArray channels, int index) {
        // Up/Down zaps through the category. Very large categories pass a window of channels around the one
        // picked (Android limits how much a screen can be handed at once).
        int total = channels.length();
        int n = Math.min(total, BrowseActivity.MAX);
        int start = Math.max(0, Math.min(index - n / 2, total - n));
        index -= start;
        String[] ids = new String[n];
        String[] names = new String[n];
        String[] icons = new String[n];
        for (int i = 0; i < n; i++) {
            JSONObject o = channels.optJSONObject(start + i);
            ids[i] = o == null ? "" : o.optString("stream_id");
            names[i] = o == null ? "" : o.optString("name");
            icons[i] = o == null ? "" : o.optString("stream_icon");
        }
        return new Intent(c, PlayerActivity.class).putExtra("kind", "live")
                .putExtra("ids", ids).putExtra("names", names).putExtra("icons", icons).putExtra("index", index);
    }

    static Intent movie(Context c, Xtream x, JSONObject o) {
        String id = o.optString("stream_id");
        String ext = o.optString("container_extension", "mp4");
        return new Intent(c, PlayerActivity.class).putExtra("kind", "movie").putExtra("key", "movie:" + id)
                .putExtra("title", o.optString("name")).putExtra("sub", "Movie")
                .putExtra("image", o.optString("stream_icon")).putExtra("url", x.movieUrl(id, ext.length() == 0 ? "mp4" : ext));
    }

    static Intent episode(Context c, Xtream x, String show, String season, JSONObject ep, String image) {
        String id = ep.optString("id");
        String ext = ep.optString("container_extension", "mp4");
        return new Intent(c, PlayerActivity.class).putExtra("kind", "episode").putExtra("key", "ep:" + id)
                .putExtra("title", show).putExtra("sub", "S" + season + " E" + ep.optString("episode_num"))
                .putExtra("image", image).putExtra("url", x.episodeUrl(id, ext.length() == 0 ? "mp4" : ext));
    }

    static Intent resume(Context c, History.Item i) {
        Intent intent = new Intent(c, PlayerActivity.class).putExtra("kind", i.kind).putExtra("key", i.key)
                .putExtra("title", i.title).putExtra("sub", i.sub).putExtra("image", i.image)
                .putExtra("url", i.url).putExtra("alt", i.alt);
        if ("live".equals(i.kind)) {
            intent.putExtra("ids", new String[] {i.key.substring(5)}).putExtra("names", new String[] {i.title})
                    .putExtra("icons", new String[] {i.image}).putExtra("index", 0);
        }
        return intent;
    }

    // ---- Player -------------------------------------------------------------------------

    /** Same identity the old built-in player sent, so the provider sees no change. */
    private static final String USER_AGENT = "stagefright/1.2 (Linux;Android 9)";

    private String kind;
    private String key;
    private String title;
    private String sub;
    private String image;
    private String url;
    private String alt;
    private String[] ids;
    private String[] names;
    private String[] icons;
    private int index;

    private Xtream x;
    private History history;
    private ExoPlayer player;
    private FrameLayout root;
    private SurfaceView surface;
    private View overlay;
    private TextView titleView;
    private TextView subView;
    private TextView timeView;
    private TextView statusView;
    private View barFill;
    private View barTrack;
    private final Handler handler = new Handler();
    private boolean prepared;
    private boolean triedAlt;
    private String playing;
    private long resumeAt;
    private int ticks;
    private float aspect;

    // ---- Brightness: -50 (darker) .. +50 (brighter), remembered for all films and channels ----
    private int brightness;
    private View shade;
    private View picturePanel;
    private TextView pictureValue;
    private View pictureFill;
    private View pictureTrack;
    private boolean okLongPressed;

    // ---- Skip intro (episodes): the provider has no intro times, so the length is learned per show ----
    private static final long DEFAULT_INTRO_MS = 85000;
    private static final long INTRO_WINDOW_MS = 10 * 60000;   // offered only in the first 10 minutes
    private TextView skipPill;
    private boolean skipUsed;
    private long skipFrom = -1;
    private long skipAdjustUntil;

    private final Runnable hideSkip = new Runnable() {
        @Override public void run() { skipPill.setVisibility(View.GONE); }
    };

    private final Runnable hidePicture = new Runnable() {
        @Override public void run() { picturePanel.setVisibility(View.GONE); }
    };

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateOverlay();
            if (++ticks % 10 == 0) save();
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable hide = new Runnable() {
        @Override public void run() { if (prepared && player != null && player.isPlaying()) overlay.setVisibility(View.GONE); }
    };

    private int px(float v) { return Ui.px(this, v); }

    private boolean isLive() { return "live".equals(kind); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        x = Xtream.load(this);
        history = History.get(this);
        Intent in = getIntent();
        kind = in.getStringExtra("kind");
        key = in.getStringExtra("key");
        title = in.getStringExtra("title");
        sub = in.getStringExtra("sub");
        image = in.getStringExtra("image");
        url = in.getStringExtra("url");
        alt = in.getStringExtra("alt");
        ids = in.getStringArrayExtra("ids");
        names = in.getStringArrayExtra("names");
        icons = in.getStringArrayExtra("icons");
        index = in.getIntExtra("index", 0);

        setContentView(buildUi());
        player = buildPlayer();

        if (isLive()) {
            if (x == null || ids == null || ids.length == 0) {
                Ui.toast(this, T.t("Add your IPTV playlist first"));
                finish();
                return;
            }
            tuneChannel(index);
        } else {
            History.Item seen = key == null ? null : history.get(key);
            boolean fromStart = in.getBooleanExtra("fromStart", false);
            if (!fromStart && seen != null && seen.dur > 0 && !History.finished(seen)) resumeAt = seen.pos;
            play();
        }
        handler.post(tick);
    }

    private ExoPlayer buildPlayer() {
        ExoPlayer p = Players.create(this, false);
        p.setVideoSurfaceView(surface);
        p.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int s) {
                if (s == Player.STATE_BUFFERING) {
                    statusView.setText(T.t(prepared ? "Buffering..." : "Loading..."));
                    if (prepared) showOverlay();
                } else if (s == Player.STATE_READY) {
                    statusView.setText("");
                    if (!prepared) {
                        prepared = true;
                        save();
                        showOverlay();
                    }
                } else if (s == Player.STATE_ENDED) {
                    if (isLive()) {
                        restart();          // the live feed dropped: reconnect
                    } else {
                        saveFinished();
                        finish();
                    }
                }
            }

            @Override public void onPlayerError(PlaybackException e) {
                if (isLive() && e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    restart();
                    return;
                }
                if (!triedAlt && alt != null && alt.length() > 0) {
                    triedAlt = true;
                    statusView.setText(T.t("Trying the other stream format..."));
                    start(alt, 0);
                } else {
                    statusView.setText(T.f("This stream won't play on this device (%s).", e.getErrorCodeName()));
                    showOverlay();
                }
            }

            @Override public void onVideoSizeChanged(VideoSize v) {
                if (v.width > 0 && v.height > 0) {
                    aspect = v.width * v.pixelWidthHeightRatio / v.height;
                    fitVideo();
                }
            }
        });
        return p;
    }

    private View buildUi() {
        root = new FrameLayout(this) {
            @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
                super.onSizeChanged(w, h, ow, oh);
                post(new Runnable() { @Override public void run() { fitVideo(); } });
            }
        };
        root.setBackgroundColor(0xFF000000);
        surface = new SurfaceView(this);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        // A see-through layer over the picture only: black darkens it, white lifts dark scenes
        // (like a TV's Brightness setting). It sits over the video, not the black bars.
        shade = new View(this);
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPaddingRelative(px(96), px(140), px(96), px(64));
        panel.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x00000000, 0xCC000000, 0xF0000000}));
        titleView = Ui.text(this, 44, Ui.TEXT, "sans-serif");
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        panel.addView(titleView);
        subView = Ui.text(this, 26, Ui.MUTED, "sans-serif");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = px(10);
        panel.addView(subView, slp);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout track = new FrameLayout(this);
        track.setBackground(Ui.round(Ui.TRACK, px(3)));
        barFill = new View(this);
        barFill.setBackground(Ui.round(0xFFFFFFFF, px(3)));
        track.addView(barFill, new FrameLayout.LayoutParams(0, -1));
        barTrack = track;
        row.addView(track, new LinearLayout.LayoutParams(0, px(6), 1f));
        timeView = Ui.text(this, 24, Ui.TEXT, "sans-serif");
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.setMarginStart(px(28));
        row.addView(timeView, tlp);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = px(26);
        panel.addView(row, rlp);

        statusView = Ui.text(this, 24, 0xFFFFD27A, "sans-serif");
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(-2, -2);
        stlp.topMargin = px(16);
        panel.addView(statusView, stlp);
        overlay = panel;
        root.addView(panel, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        skipPill = Ui.text(this, 28, 0xFF11151B, "sans-serif-medium");
        skipPill.setText(T.t("Skip intro") + "  ›");
        skipPill.setPaddingRelative(px(40), px(20), px(40), px(20));
        skipPill.setBackground(Ui.round(0xF2FFFFFF, px(40)));
        skipPill.setVisibility(View.GONE);
        FrameLayout.LayoutParams sk = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        sk.setMarginEnd(px(96));
        sk.bottomMargin = px(250);
        root.addView(skipPill, sk);

        root.addView(buildPicturePanel(), new FrameLayout.LayoutParams(px(720), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        brightness = getSharedPreferences("player", MODE_PRIVATE).getInt("brightness", 0);
        applyBrightness();
        return root;
    }

    private View buildPicturePanel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPaddingRelative(px(44), px(30), px(44), px(34));
        p.setBackground(Ui.round(0xE0161B22, px(28)));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = Ui.text(this, 30, Ui.TEXT, "sans-serif-medium");
        label.setText(T.t("Brightness"));
        top.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        pictureValue = Ui.text(this, 30, Ui.TEXT, "sans-serif");
        top.addView(pictureValue);
        p.addView(top);

        FrameLayout track = new FrameLayout(this);
        track.setBackground(Ui.round(Ui.TRACK, px(4)));
        View mid = new View(this);
        mid.setBackgroundColor(0xAAFFFFFF);
        track.addView(mid, new FrameLayout.LayoutParams(px(3), -1, Gravity.CENTER_HORIZONTAL));
        pictureFill = new View(this);
        pictureFill.setBackground(Ui.round(0xFFFFFFFF, px(4)));
        track.addView(pictureFill, new FrameLayout.LayoutParams(0, -1));
        pictureTrack = track;
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, px(10));
        tlp.topMargin = px(22);
        p.addView(track, tlp);

        TextView hint = Ui.text(this, 22, Ui.MUTED, "sans-serif");
        hint.setText(T.t("Left / Right to change   |   OK or Back to close"));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-2, -2);
        hlp.topMargin = px(20);
        p.addView(hint, hlp);

        picturePanel = p;
        p.setVisibility(View.GONE);
        p.setTranslationY(px(60));
        return p;
    }

    private void applyBrightness() {
        int b = brightness;
        if (b < 0) shade.setBackgroundColor(Math.round(-b / 50f * 0.60f * 255) << 24);            // up to 60% black
        else shade.setBackgroundColor((Math.round(b / 50f * 0.22f * 255) << 24) | 0xFFFFFF);    // up to 22% white
        pictureValue.setText(b > 0 ? "+" + b : String.valueOf(b));
        pictureTrack.post(new Runnable() {
            @Override public void run() {
                int w = pictureTrack.getWidth();
                int half = w / 2;
                int len = Math.round(half * Math.abs(brightness) / 50f);
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pictureFill.getLayoutParams();
                lp.width = len;
                lp.setMarginStart(brightness >= 0 ? half : half - len);
                pictureFill.setLayoutParams(lp);
            }
        });
    }

    private void changeBrightness(int step) {
        int b = Math.max(-50, Math.min(50, brightness + step));
        showPicture();
        if (b == brightness) return;
        brightness = b;
        applyBrightness();
        getSharedPreferences("player", MODE_PRIVATE).edit().putInt("brightness", b).apply();
    }

    private void showPicture() {
        picturePanel.setVisibility(View.VISIBLE);
        overlay.setVisibility(View.GONE);
        handler.removeCallbacks(hidePicture);
        handler.postDelayed(hidePicture, 6000);
    }

    private boolean pictureOpen() { return picturePanel.getVisibility() == View.VISIBLE; }

    /** The remote's settings button (via KeyService) opens or closes the brightness panel during playback. */
    void togglePicture() {
        if (pictureOpen()) picturePanel.setVisibility(View.GONE);
        else showPicture();
    }

    /** Letterbox: the picture keeps its shape and is centred, with black bars where needed. */
    private void fitVideo() {
        int w = root.getWidth(), h = root.getHeight();
        if (aspect <= 0 || w == 0 || h == 0) return;
        int vw = w, vh = Math.round(w / aspect);
        if (vh > h) { vh = h; vw = Math.round(h * aspect); }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) surface.getLayoutParams();
        if (lp.width == vw && lp.height == vh) return;
        lp.width = vw;
        lp.height = vh;
        lp.gravity = Gravity.CENTER;
        surface.setLayoutParams(lp);
        shade.setLayoutParams(new FrameLayout.LayoutParams(vw, vh, Gravity.CENTER));
    }

    private void tuneChannel(int i) {
        index = (i + ids.length) % ids.length;
        String id = ids[index];
        String fmt = Xtream.liveFormat(this);
        key = "live:" + id;
        title = names[index];
        image = icons[index];
        sub = ids.length > 1 ? T.f("Channel %1$d of %2$d", index + 1, ids.length) : T.t("Live");
        url = x.liveUrl(id, fmt);
        alt = x.liveUrl(id, "m3u8".equals(fmt) ? "ts" : "m3u8");
        play();
    }

    private void play() {
        prepared = false;
        triedAlt = false;
        titleView.setText(title);
        subView.setText(sub == null ? "" : sub);
        statusView.setText("Loading...");
        showOverlay();
        start(url, resumeAt);
        resumeAt = 0;
    }

    private void start(String address, long positionMs) {
        playing = address;
        prepared = false;
        player.stop();
        if (positionMs > 0) player.setMediaItem(MediaItem.fromUri(address), positionMs);
        else player.setMediaItem(MediaItem.fromUri(address));
        player.prepare();
        player.setPlayWhenReady(true);
    }

    /** Live only: reconnect to the same stream. */
    private void restart() {
        if (playing == null) return;
        statusView.setText(T.t("Reconnecting..."));
        showOverlay();
        start(playing, 0);
    }

    private String introKey() { return "intro_" + (title == null ? "" : title.trim().toLowerCase(java.util.Locale.ROOT)); }

    private long introLength() {
        return getSharedPreferences("player", MODE_PRIVATE).getLong(introKey(), DEFAULT_INTRO_MS);
    }

    private boolean skipShowing() { return skipPill.getVisibility() == View.VISIBLE; }

    /** Episodes only, in the first 10 minutes, until it's been used once in this viewing. Shown for 12 s. */
    private void maybeShowSkip() {
        if (!"episode".equals(kind) || skipUsed || !prepared || player == null) return;
        if (player.getCurrentPosition() > INTRO_WINDOW_MS) return;
        skipPill.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideSkip);
        handler.postDelayed(hideSkip, 12000);
    }

    private void skipIntro() {
        long from = player.getCurrentPosition();
        long target = from + introLength();
        long dur = duration();
        player.seekTo(dur > 0 ? Math.min(dur - 1000, target) : target);
        skipUsed = true;
        skipPill.setVisibility(View.GONE);
        skipFrom = from;
        skipAdjustUntil = android.os.SystemClock.uptimeMillis() + 20000;
        Ui.toast(this, T.f("Skipped %s. Left/Right now fine-tunes it for this show.", Ui.clock(introLength())));
        showOverlay();
    }

    /** Left/Right just after a skip: remember the corrected intro length for the next episodes. */
    private void learnSkip(long newPosition) {
        if (skipFrom < 0 || android.os.SystemClock.uptimeMillis() > skipAdjustUntil) return;
        long len = newPosition - skipFrom;
        if (len < 10000 || len > 5 * 60000) return;
        getSharedPreferences("player", MODE_PRIVATE).edit().putLong(introKey(), len).apply();
        skipAdjustUntil = android.os.SystemClock.uptimeMillis() + 20000;
    }

    private void showOverlay() {
        maybeShowSkip();
        overlay.setVisibility(View.VISIBLE);
        updateOverlay();
        handler.removeCallbacks(hide);
        handler.postDelayed(hide, 4000);
    }

    private long duration() {
        long d = player == null ? C.TIME_UNSET : player.getDuration();
        return d == C.TIME_UNSET ? 0 : d;
    }

    private void updateOverlay() {
        if (overlay.getVisibility() != View.VISIBLE || player == null) return;
        if (isLive() || !prepared) {
            timeView.setText(isLive() ? "LIVE" : "");
            barFill.getLayoutParams().width = isLive() ? barTrack.getWidth() : 0;
            barFill.requestLayout();
            return;
        }
        long pos = player.getCurrentPosition();
        long dur = duration();
        timeView.setText(Ui.clock(pos) + " / " + Ui.clock(dur));
        barFill.getLayoutParams().width = dur > 0 ? (int) (barTrack.getWidth() * (pos / (float) dur)) : 0;
        barFill.requestLayout();
    }

    private void save() {
        if (!prepared || key == null || player == null) return;
        final History.Item i = item();
        if (!isLive()) {
            i.pos = player.getCurrentPosition();
            i.dur = duration();
        }
        Ui.POOL.execute(new Runnable() {
            @Override public void run() { history.save(i); }
        });
    }

    private void saveFinished() {
        if (key == null) return;
        final History.Item i = item();
        i.dur = Math.max(1, duration());
        i.pos = i.dur;
        Ui.POOL.execute(new Runnable() {
            @Override public void run() { history.save(i); }
        });
    }

    private History.Item item() {
        History.Item i = new History.Item();
        i.key = key;
        i.kind = kind;
        i.title = title;
        i.sub = sub;
        i.image = image;
        i.url = url;
        i.alt = alt;
        return i;
    }

    private void playPause() {
        if (!isLive() && prepared) {
            if (player.getPlayWhenReady()) player.pause(); else player.play();
        }
        showOverlay();
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (player == null) return super.onKeyDown(code, e);
        // Arabic and Urdu: the progress and brightness bars run right to left, so Left and Right swap roles
        // (Left goes forward / brighter), matching the direction the bar moves on screen.
        if (getWindow().getDecorView().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) code = KeyEvent.KEYCODE_DPAD_RIGHT;
            else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) code = KeyEvent.KEYCODE_DPAD_LEFT;
        }
        if (pictureOpen()) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:  changeBrightness(-5); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT: changeBrightness(5);  return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:  showPicture(); return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_BACK:
                    if (e.getRepeatCount() == 0) {
                        picturePanel.setVisibility(View.GONE);
                        okLongPressed = true;    // swallow the matching key-up
                    }
                    return true;
                default: break;
            }
        }
        if (Ui.isOptionsKey(code)) {
            if (e.getRepeatCount() == 0) togglePicture();
            return true;
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                // Tap OK: pause/play (on release). Hold OK: brightness.
                if (e.getRepeatCount() == 0) {
                    okLongPressed = false;
                    e.startTracking();
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                playPause();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (!isLive() && prepared) {
                    long back = Math.max(0, player.getCurrentPosition() - 10000);
                    player.seekTo(back);
                    learnSkip(back);
                }
                showOverlay();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (!isLive() && prepared) {
                    long target = player.getCurrentPosition() + 30000;
                    long dur = duration();
                    long to = dur > 0 ? Math.min(dur - 1000, target) : target;
                    player.seekTo(to);
                    learnSkip(to);
                }
                showOverlay();
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (skipShowing()) {           // Back just dismisses the button
                    skipPill.setVisibility(View.GONE);
                    return true;
                }
                return super.onKeyDown(code, e);
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                if (isLive() && ids.length > 1) { save(); tuneChannel(index + 1); } else showOverlay();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                if (isLive() && ids.length > 1) { save(); tuneChannel(index - 1); } else showOverlay();
                return true;
            default:
                return super.onKeyDown(code, e);
        }
    }

    @Override public boolean onKeyLongPress(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            okLongPressed = true;
            togglePicture();
            return true;
        }
        return super.onKeyLongPress(code, e);
    }

    @Override public boolean onKeyUp(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            if (!okLongPressed && player != null && !e.isCanceled()) {
                if (skipShowing()) skipIntro(); else playPause();   // OK presses the button while it shows
            }
            okLongPressed = false;
            return true;
        }
        return super.onKeyUp(code, e);
    }

    @Override protected void onPause() {
        save();
        if (player != null) player.pause();
        super.onPause();
    }

    @Override protected void onStop() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
        super.onStop();
        finish();
    }
}
