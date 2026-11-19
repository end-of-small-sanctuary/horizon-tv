package home.horizon.tv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.util.Locale;

/**
 * Simplified single-colour icons for the home row.
 * Known apps are matched by package name, then by app name, and drawn as simple marks in the same style as
 * the built-in Live TV / Movies icons. Anything else gets an automatic silhouette made from its own icon.
 * Everything is drawn in white and tinted with the wallpaper colour by the tile.
 */
final class Brand {
    private Brand() { }

    // ---- Which mark ---------------------------------------------------------------------

    private static final int NONE = -1, YOUTUBE = 0, YOUTUBE_TV = 1, NETFLIX = 2, PRIME = 3, DISNEY = 4, MAX = 5,
            HULU = 6, APPLE_TV = 7, PARAMOUNT = 8, PEACOCK = 9, FUBO = 10, TUBI = 11, PLUTO = 12, CURIOSITY = 13,
            SPOTIFY = 14, PLEX = 15, JELLYFIN = 16, VLC = 17, FILES = 18, LOCALSEND = 19, BUTTONS = 20, BROWSER = 21,
            SHAHID = 22, STC = 23, OSN = 24, TOD = 25, STARZ = 26, VIU = 27, ANGHAMI = 28, CRUNCHYROLL = 29,
            TWITCH = 30;

    /** Package names (exact), checked first. */
    private static final Object[][] PACKAGES = {
            {YOUTUBE_TV, "com.google.android.youtube.tvunplugged", "com.google.android.apps.youtube.unplugged"},
            {YOUTUBE, "com.google.android.youtube.tv", "com.google.android.youtube", "com.google.android.youtube.googletv"},
            {NETFLIX, "com.netflix.ninja", "com.netflix.mediaclient"},
            {PRIME, "com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient", "com.amazon.avod"},
            {DISNEY, "com.disney.disneyplus"},
            {MAX, "com.wbd.stream", "com.hbo.hbonow", "com.hbo.max.android.tv", "com.hbo.go"},
            {HULU, "com.hulu.livingroomplus", "com.hulu.plus"},
            {APPLE_TV, "com.apple.atve.androidtv.appletv", "com.apple.atve.android.appletv", "com.apple.atve.amazon.appletv"},
            {PARAMOUNT, "com.cbs.ott", "com.cbs.app", "com.cbs.ca"},
            {PEACOCK, "com.peacocktv.peacockandroid", "com.peacock.peacocktv"},
            {FUBO, "com.fubo.firetv.screen", "com.fubotv.android", "tv.fubo.mobile"},
            {TUBI, "com.tubitv", "com.tubitv.ott"},
            {PLUTO, "tv.pluto.android"},
            {CURIOSITY, "com.curiositystream.curiositystream", "com.curiositystream.android"},
            {SPOTIFY, "com.spotify.tv.android", "com.spotify.music"},
            {PLEX, "com.plexapp.android", "com.plexapp.mediaserver.smb"},
            {JELLYFIN, "org.jellyfin.androidtv", "org.jellyfin.mobile"},
            {VLC, "org.videolan.vlc"},
            {FILES, "com.cxinventor.file.explorer"},
            {LOCALSEND, "org.localsend.localsend_app"},
            {BUTTONS, "flar2.homebutton"},
            {BROWSER, "com.phlox.tvwebbrowser"},
            {SHAHID, "net.mbc.shahidTV", "net.mbc.shahid", "net.mbc.shahid.tv"},
            {STC, "com.stc.tv", "com.mobily.stctv", "sa.com.stc.tv", "com.stc.stctv"},
            {OSN, "com.osn.go", "com.osn.osnplus", "com.osnplus.android", "com.osn.player"},
            {TOD, "com.beinsports.tod", "tv.tod.android", "com.tod.app"},
            {STARZ, "com.parsifal.starz", "com.starzplay.android", "com.starzplayarabia.android"},
            {VIU, "com.vuclip.viu", "com.viu.tv"},
            {ANGHAMI, "com.anghami", "com.anghami.tv"},
            {CRUNCHYROLL, "com.crunchyroll.crunchyroid", "com.crunchyroll.crunchyroid.tv"},
            {TWITCH, "tv.twitch.android.app", "tv.twitch.android.viewer"},
    };

    /** App names (lower case, spaces removed, contains), checked in this order when the package isn't known. */
    private static final Object[][] NAMES = {
            {YOUTUBE_TV, "youtubetv"}, {YOUTUBE, "youtube"}, {NETFLIX, "netflix"}, {PRIME, "primevideo"},
            {DISNEY, "disney"}, {MAX, "hbomax"}, {MAX, "hbo"}, {HULU, "hulu"}, {APPLE_TV, "appletv"},
            {PARAMOUNT, "paramount"}, {PEACOCK, "peacock"}, {FUBO, "fubo"}, {TUBI, "tubi"}, {PLUTO, "plutotv"},
            {CURIOSITY, "curiosity"}, {SPOTIFY, "spotify"}, {PLEX, "plex"}, {JELLYFIN, "jellyfin"}, {VLC, "vlc"},
            {FILES, "cxfile"}, {LOCALSEND, "localsend"}, {BUTTONS, "buttonmapper"}, {BROWSER, "tvbro"},
            {SHAHID, "shahid"}, {SHAHID, "شاهد"}, {STC, "stctv"}, {STC, "jawwy"}, {OSN, "osn"}, {TOD, "tod"},
            {STARZ, "starz"}, {VIU, "viu"}, {ANGHAMI, "anghami"}, {ANGHAMI, "أنغامي"}, {CRUNCHYROLL, "crunchyroll"},
            {TWITCH, "twitch"}, {MAX, "max"},
    };

    private static int lookup(String pkg, String label) {
        for (Object[] row : PACKAGES) {
            for (int i = 1; i < row.length; i++) if (row[i].equals(pkg)) return (Integer) row[0];
        }
        String name = label == null ? "" : label.toLowerCase(Locale.ROOT).replace(" ", "").replace("+", "");
        for (Object[] row : NAMES) {
            String key = (String) row[1];
            // Short names ("max", "tod", "viu") must be the whole name, so "Maxthon" or "Today" don't match.
            boolean whole = key.length() <= 3;
            if (whole ? name.equals(key) : name.contains(key)) return (Integer) row[0];
        }
        return NONE;
    }

    /** Marks that come from a logo image in res/drawable-nodpi (dark logo on a light background). */
    private static final Object[][] IMAGES = {
            {DISNEY, "brand_disney"},
    };

    /** A white icon for the home row: a simple mark for known apps, else a silhouette of the app's own icon. */
    static Drawable icon(android.content.Context c, Apps.App app) {
        // 1. A custom icon added with the Windows setup app: files/icons/<package>.png (white shape on transparent).
        java.io.File dir = c.getExternalFilesDir("icons");
        java.io.File custom = dir == null ? null : new java.io.File(dir, app.pkg + ".png");
        if (custom != null && custom.isFile()) {
            Bitmap b = android.graphics.BitmapFactory.decodeFile(custom.getPath());
            if (b != null) return new BitmapDrawable(c.getResources(), b);
        }
        int mark = lookup(app.pkg, app.label);
        for (Object[] row : IMAGES) {
            if ((Integer) row[0] == mark) {
                Drawable d = logo(c, (String) row[1]);
                if (d != null) return d;
            }
        }
        if (mark != NONE) return new Mark(mark);
        Drawable d = silhouette(app.icon);
        return d != null ? d : app.icon;
    }

    /** A dark-on-light logo image turned into a white shape on transparent (darker = more solid). */
    private static Drawable logo(android.content.Context c, String name) {
        int id = c.getResources().getIdentifier(name, "drawable", c.getPackageName());
        if (id == 0) return null;
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inScaled = false;
        Bitmap src = android.graphics.BitmapFactory.decodeResource(c.getResources(), id, o);
        if (src == null) return null;
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int lum = (Color.red(p) * 30 + Color.green(p) * 59 + Color.blue(p) * 11) / 100;
            px[i] = Color.argb((255 - lum) * Color.alpha(p) / 255, 255, 255, 255);
        }
        src.recycle();
        return new BitmapDrawable(c.getResources(), Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888));
    }

    // ---- Automatic silhouette ---------------------------------------------------------------

    /**
     * Turns a colourful icon into a one-colour shape: the background colour (taken from the icon's edges)
     * becomes transparent and the logo on it becomes solid. Icons that already have a transparent background
     * keep their own outline.
     */
    static Drawable silhouette(Drawable icon) {
        if (icon == null) return null;
        final int n = 96;
        Bitmap b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        icon.setBounds(0, 0, n, n);
        icon.draw(c);
        int[] px = new int[n * n];
        b.getPixels(px, 0, n, 0, 0, n, n);

        // Background = average colour of the opaque pixels a few pixels in from the edge.
        long r = 0, g = 0, bl = 0, count = 0, opaqueEdge = 0, edge = 0;
        int inset = n / 10;
        for (int i = inset; i < n - inset; i++) {
            int[] samples = {px[inset * n + i], px[(n - 1 - inset) * n + i], px[i * n + inset], px[i * n + n - 1 - inset]};
            for (int s : samples) {
                edge++;
                if (Color.alpha(s) > 200) {
                    opaqueEdge++;
                    r += Color.red(s); g += Color.green(s); bl += Color.blue(s); count++;
                }
            }
        }
        int[] out = new int[n * n];
        if (opaqueEdge < edge * 0.6) {
            // Transparent background: the shape is already the icon's outline.
            for (int i = 0; i < px.length; i++) out[i] = Color.argb(Color.alpha(px[i]), 255, 255, 255);
        } else {
            int br = (int) (r / count), bgc = (int) (g / count), bb = (int) (bl / count);
            int kept = 0;
            for (int i = 0; i < px.length; i++) {
                int p = px[i];
                int d = Math.abs(Color.red(p) - br) + Math.abs(Color.green(p) - bgc) + Math.abs(Color.blue(p) - bb);
                // Soft edge between 60 and 140 colour-distance, so curves stay smooth.
                int a = d <= 60 ? 0 : d >= 140 ? 255 : (d - 60) * 255 / 80;
                a = a * Color.alpha(p) / 255;
                if (a > 128) kept++;
                out[i] = Color.argb(a, 255, 255, 255);
            }
            // Nothing (or almost everything) stood out from the background: keep the plain outline instead.
            if (kept < px.length / 50 || kept > px.length * 0.8) {
                for (int i = 0; i < px.length; i++) out[i] = Color.argb(Color.alpha(px[i]), 255, 255, 255);
            }
        }
        Bitmap res = Bitmap.createBitmap(out, n, n, Bitmap.Config.ARGB_8888);
        b.recycle();
        return new BitmapDrawable(null, res);
    }

    // ---- Hand-drawn marks -------------------------------------------------------------------

    private static final class Mark extends Drawable {
        private final int kind;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Path path = new Path();

        Mark(int kind) {
            this.kind = kind;
            p.setColor(Color.WHITE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        /** Bold wordmark fitted into the icon box. */
        private void word(Canvas c, float s, String text, boolean bold, float maxH) {
            p.setStyle(Paint.Style.FILL);
            p.setTypeface(Typeface.create(bold ? "sans-serif-black" : "sans-serif-medium", Typeface.BOLD));
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(s * maxH);
            float w = p.measureText(text);
            if (w > s * .94f) p.setTextSize(s * maxH * (s * .94f) / w);
            Paint.FontMetrics fm = p.getFontMetrics();
            c.drawText(text, s / 2f, s / 2f - (fm.ascent + fm.descent) / 2f, p);
        }

        private void fill() { p.setStyle(Paint.Style.FILL); }
        private void stroke(float s, float w) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(s * w); }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            float s = Math.min(b.width(), b.height());
            c.save();
            c.translate(b.centerX() - s / 2f, b.centerY() - s / 2f);
            path.reset();
            switch (kind) {
                case YOUTUBE: {                       // rounded screen with the play triangle cut out
                    fill();
                    path.setFillType(Path.FillType.EVEN_ODD);
                    r.set(s * .06f, s * .2f, s * .94f, s * .8f);
                    path.addRoundRect(r, s * .17f, s * .17f, Path.Direction.CW);
                    path.moveTo(s * .41f, s * .36f);
                    path.lineTo(s * .65f, s * .5f);
                    path.lineTo(s * .41f, s * .64f);
                    path.close();
                    c.drawPath(path, p);
                    break;
                }
                case YOUTUBE_TV: {                    // play screen outline above a small "TV"
                    stroke(s, .06f);
                    r.set(s * .14f, s * .1f, s * .86f, s * .56f);
                    c.drawRoundRect(r, s * .13f, s * .13f, p);
                    fill();
                    path.moveTo(s * .43f, s * .22f);
                    path.lineTo(s * .62f, s * .33f);
                    path.lineTo(s * .43f, s * .44f);
                    path.close();
                    c.drawPath(path, p);
                    c.save();
                    c.translate(0, s * .3f);
                    word(c, s, "TV", true, .3f);
                    c.restore();
                    break;
                }
                case NETFLIX: {                       // the "N" ribbon
                    fill();
                    r.set(s * .26f, s * .1f, s * .41f, s * .9f);
                    c.drawRect(r, p);
                    r.set(s * .59f, s * .1f, s * .74f, s * .9f);
                    c.drawRect(r, p);
                    path.moveTo(s * .26f, s * .1f);
                    path.lineTo(s * .41f, s * .1f);
                    path.lineTo(s * .74f, s * .9f);
                    path.lineTo(s * .59f, s * .9f);
                    path.close();
                    c.drawPath(path, p);
                    break;
                }
                case PRIME: {                         // "prime" with the smile arrow
                    c.save();
                    c.translate(0, -s * .08f);
                    word(c, s, "prime", true, .34f);
                    c.restore();
                    stroke(s, .05f);
                    r.set(s * .2f, s * .3f, s * .8f, s * .76f);
                    c.drawArc(r, 30, 120, false, p);
                    fill();
                    path.moveTo(s * .74f, s * .6f);
                    path.lineTo(s * .86f, s * .6f);
                    path.lineTo(s * .8f, s * .73f);
                    path.close();
                    c.drawPath(path, p);
                    break;
                }
                case DISNEY: {                        // thin sweeping arc over "disney+"
                    stroke(s, .028f);
                    r.set(-s * .04f, s * .24f, s * 1.0f, s * .9f);
                    c.drawArc(r, 172, 190, false, p);
                    c.save();
                    c.translate(-s * .02f, s * .1f);
                    word(c, s, "disney+", true, .3f);
                    c.restore();
                    break;
                }
                case MAX:       word(c, s, "max", true, .46f); break;
                case HULU:      word(c, s, "hulu", true, .46f); break;
                case FUBO:      word(c, s, "fubo", true, .46f); break;
                case TUBI:      word(c, s, "tubi", true, .5f); break;
                case STC:       word(c, s, "stc", true, .5f); break;
                case OSN:       word(c, s, "OSN+", true, .4f); break;
                case TOD:       word(c, s, "TOD", true, .46f); break;
                case STARZ:     word(c, s, "STARZ", true, .36f); break;
                case VIU:       word(c, s, "viu", true, .52f); break;
                case SHAHID:    word(c, s, "شاهد", true, .5f); break;   // شاهد
                case APPLE_TV: {                      // small leaf + "tv"
                    fill();
                    c.save();
                    c.translate(0, s * .08f);
                    word(c, s, "tv", true, .5f);
                    c.restore();
                    // Leaf: a pointed almond shape leaning right, above the letters.
                    path.moveTo(s * .46f, s * .3f);
                    path.quadTo(s * .44f, s * .08f, s * .66f, s * .04f);
                    path.quadTo(s * .68f, s * .26f, s * .46f, s * .3f);
                    path.close();
                    c.drawPath(path, p);
                    break;
                }
                case PARAMOUNT: {                     // mountain under an arc of stars
                    fill();
                    path.moveTo(s * .12f, s * .86f);
                    path.lineTo(s * .44f, s * .4f);
                    path.lineTo(s * .54f, s * .52f);
                    path.lineTo(s * .62f, s * .44f);
                    path.lineTo(s * .88f, s * .86f);
                    path.close();
                    c.drawPath(path, p);
                    for (int i = 0; i < 7; i++) {
                        double a = Math.toRadians(200 + i * 140 / 6.0);
                        c.drawCircle((float) (s * .5f + Math.cos(a) * s * .38f), (float) (s * .56f + Math.sin(a) * s * .38f), s * .04f, p);
                    }
                    break;
                }
                case PEACOCK: {                       // six dots, as in the logo
                    fill();
                    for (int i = 0; i < 6; i++) {
                        float x = s * (.32f + (i % 2) * .36f), y = s * (.2f + (i / 2) * .3f);
                        c.drawCircle(x, y, s * .11f, p);
                    }
                    break;
                }
                case PLUTO: {                         // planet with a ring inside a screen
                    stroke(s, .06f);
                    r.set(s * .08f, s * .16f, s * .92f, s * .78f);
                    c.drawRoundRect(r, s * .1f, s * .1f, p);
                    fill();
                    c.drawCircle(s * .5f, s * .47f, s * .13f, p);
                    stroke(s, .04f);
                    c.save();
                    c.rotate(-20, s * .5f, s * .47f);
                    r.set(s * .26f, s * .4f, s * .74f, s * .54f);
                    c.drawOval(r, p);
                    c.restore();
                    stroke(s, .06f);
                    c.drawLine(s * .36f, s * .9f, s * .64f, s * .9f, p);
                    break;
                }
                case CURIOSITY: {                     // bold open "C"
                    stroke(s, .14f);
                    p.setStrokeCap(Paint.Cap.BUTT);
                    r.set(s * .2f, s * .2f, s * .8f, s * .8f);
                    c.drawArc(r, 45, 270, false, p);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    break;
                }
                case SPOTIFY: {                       // solid circle with three curved bars cut out
                    fill();
                    Path circle = new Path();
                    circle.addCircle(s * .5f, s * .5f, s * .44f, Path.Direction.CW);
                    // Bars: widest on top, each bowing upward and dipping slightly to the right.
                    float[][] bars = {
                            {.24f, .40f, .50f, .25f, .78f, .45f, .095f},
                            {.28f, .555f, .51f, .44f, .73f, .59f, .078f},
                            {.31f, .69f, .51f, .60f, .68f, .72f, .062f},
                    };
                    Paint cut = new Paint(Paint.ANTI_ALIAS_FLAG);
                    cut.setStyle(Paint.Style.STROKE);
                    cut.setStrokeCap(Paint.Cap.ROUND);
                    for (float[] bar : bars) {
                        Path line = new Path();
                        line.moveTo(s * bar[0], s * bar[1]);
                        line.quadTo(s * bar[2], s * bar[3], s * bar[4], s * bar[5]);
                        cut.setStrokeWidth(s * bar[6]);
                        Path solid = new Path();
                        cut.getFillPath(line, solid);
                        circle.op(solid, Path.Op.DIFFERENCE);
                    }
                    c.drawPath(circle, p);
                    break;
                }
                case PLEX: {                          // the chevron
                    fill();
                    path.moveTo(s * .26f, s * .1f);
                    path.lineTo(s * .5f, s * .1f);
                    path.lineTo(s * .78f, s * .5f);
                    path.lineTo(s * .5f, s * .9f);
                    path.lineTo(s * .26f, s * .9f);
                    path.lineTo(s * .54f, s * .5f);
                    path.close();
                    c.drawPath(path, p);
                    break;
                }
                case JELLYFIN: {                      // triangle within a triangle
                    stroke(s, .07f);
                    path.moveTo(s * .5f, s * .12f);
                    path.lineTo(s * .9f, s * .84f);
                    path.lineTo(s * .1f, s * .84f);
                    path.close();
                    c.drawPath(path, p);
                    fill();
                    path.reset();
                    path.moveTo(s * .5f, s * .42f);
                    path.lineTo(s * .66f, s * .7f);
                    path.lineTo(s * .34f, s * .7f);
                    path.close();
                    c.drawPath(path, p);
                    break;
                }
                case VLC: {                           // traffic cone
                    fill();
                    path.setFillType(Path.FillType.EVEN_ODD);
                    path.moveTo(s * .42f, s * .08f);
                    path.lineTo(s * .58f, s * .08f);
                    path.lineTo(s * .78f, s * .8f);
                    path.lineTo(s * .22f, s * .8f);
                    path.close();
                    path.addRect(s * .33f, s * .34f, s * .67f, s * .42f, Path.Direction.CW);
                    path.addRect(s * .28f, s * .56f, s * .72f, s * .64f, Path.Direction.CW);
                    c.drawPath(path, p);
                    r.set(s * .12f, s * .8f, s * .88f, s * .9f);
                    c.drawRoundRect(r, s * .04f, s * .04f, p);
                    break;
                }
                case FILES: {                         // folder
                    stroke(s, .065f);
                    path.moveTo(s * .1f, s * .24f);
                    path.lineTo(s * .4f, s * .24f);
                    path.lineTo(s * .48f, s * .34f);
                    path.lineTo(s * .9f, s * .34f);
                    path.lineTo(s * .9f, s * .8f);
                    path.lineTo(s * .1f, s * .8f);
                    path.close();
                    c.drawPath(path, p);
                    c.drawLine(s * .1f, s * .44f, s * .9f, s * .44f, p);
                    break;
                }
                case LOCALSEND: {                     // arrow sending out of a tray
                    stroke(s, .07f);
                    c.drawLine(s * .5f, s * .12f, s * .5f, s * .6f, p);
                    c.drawLine(s * .32f, s * .3f, s * .5f, s * .12f, p);
                    c.drawLine(s * .68f, s * .3f, s * .5f, s * .12f, p);
                    path.moveTo(s * .16f, s * .56f);
                    path.lineTo(s * .16f, s * .86f);
                    path.lineTo(s * .84f, s * .86f);
                    path.lineTo(s * .84f, s * .56f);
                    c.drawPath(path, p);
                    break;
                }
                case BUTTONS: {                       // remote with a big round button
                    stroke(s, .06f);
                    r.set(s * .3f, s * .06f, s * .7f, s * .94f);
                    c.drawRoundRect(r, s * .18f, s * .18f, p);
                    c.drawCircle(s * .5f, s * .32f, s * .1f, p);
                    fill();
                    c.drawCircle(s * .5f, s * .58f, s * .045f, p);
                    c.drawCircle(s * .5f, s * .74f, s * .045f, p);
                    break;
                }
                case BROWSER: {                       // globe
                    stroke(s, .055f);
                    c.drawCircle(s * .5f, s * .5f, s * .4f, p);
                    r.set(s * .32f, s * .1f, s * .68f, s * .9f);
                    c.drawOval(r, p);
                    c.drawLine(s * .1f, s * .5f, s * .9f, s * .5f, p);
                    c.drawLine(s * .17f, s * .3f, s * .83f, s * .3f, p);
                    c.drawLine(s * .17f, s * .7f, s * .83f, s * .7f, p);
                    break;
                }
                case ANGHAMI: {                       // music note
                    fill();
                    c.save();
                    c.rotate(-20, s * .34f, s * .74f);
                    r.set(s * .2f, s * .64f, s * .48f, s * .84f);
                    c.drawOval(r, p);
                    c.restore();
                    stroke(s, .07f);
                    p.setStrokeCap(Paint.Cap.BUTT);
                    c.drawLine(s * .46f, s * .72f, s * .46f, s * .14f, p);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    path.moveTo(s * .46f, s * .14f);
                    path.cubicTo(s * .58f, s * .2f, s * .8f, s * .24f, s * .74f, s * .5f);
                    c.drawPath(path, p);
                    break;
                }
                case CRUNCHYROLL: {                   // ring with an inner circle off to one side
                    stroke(s, .08f);
                    c.drawCircle(s * .5f, s * .5f, s * .38f, p);
                    fill();
                    c.drawCircle(s * .58f, s * .44f, s * .19f, p);
                    break;
                }
                case TWITCH: {                        // speech bubble with two bars
                    stroke(s, .065f);
                    path.moveTo(s * .18f, s * .12f);
                    path.lineTo(s * .84f, s * .12f);
                    path.lineTo(s * .84f, s * .58f);
                    path.lineTo(s * .66f, s * .74f);
                    path.lineTo(s * .48f, s * .74f);
                    path.lineTo(s * .34f, s * .88f);
                    path.lineTo(s * .34f, s * .74f);
                    path.lineTo(s * .18f, s * .74f);
                    path.close();
                    c.drawPath(path, p);
                    c.drawLine(s * .46f, s * .3f, s * .46f, s * .5f, p);
                    c.drawLine(s * .64f, s * .3f, s * .64f, s * .5f, p);
                    break;
                }
                default:
                    break;
            }
            c.restore();
        }

        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
