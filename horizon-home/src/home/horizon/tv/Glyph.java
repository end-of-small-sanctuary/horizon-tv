package home.horizon.tv;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Simple line icons drawn in code, so the app needs no image files. */
final class Glyph extends Drawable {
    static final int LIVE = 0, MOVIES = 1, SERIES = 2, APPS = 3, GEAR = 4, SLIDERS = 5, DIAMOND = 6, WIFI = 7, PLAY = 8;
    // Weather
    static final int SUN = 9, MOON = 10, PARTLY_DAY = 11, PARTLY_NIGHT = 12, CLOUD = 13, FOG = 14, RAIN = 15,
            SNOW = 16, THUNDER = 17;

    /** Weather icon for an Open-Meteo / WMO weather code. */
    static int forWeather(int code, boolean day) {
        if (code == 0) return day ? SUN : MOON;
        if (code == 1 || code == 2) return day ? PARTLY_DAY : PARTLY_NIGHT;
        if (code == 3) return CLOUD;
        if (code == 45 || code == 48) return FOG;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return SNOW;
        if (code >= 95) return THUNDER;
        if (code >= 51 && code <= 82) return RAIN;          // drizzle, rain, showers
        return CLOUD;
    }

    /** Cloud outline: three bumps on a flat base, centred at (cx, cy), width about 0.8 * size * k. */
    private static Path cloud(float s, float cx, float cy, float k) {
        Path p = new Path();
        p.addCircle(cx - s * .17f * k, cy + s * .03f * k, s * .15f * k, Path.Direction.CW);
        Path q = new Path();
        q.addCircle(cx + s * .02f * k, cy - s * .07f * k, s * .21f * k, Path.Direction.CW);
        p.op(q, Path.Op.UNION);
        q.reset();
        q.addCircle(cx + s * .21f * k, cy + s * .04f * k, s * .14f * k, Path.Direction.CW);
        p.op(q, Path.Op.UNION);
        q.reset();
        q.addRoundRect(new RectF(cx - s * .32f * k, cy + s * .02f * k, cx + s * .35f * k, cy + s * .18f * k),
                s * .08f * k, s * .08f * k, Path.Direction.CW);
        p.op(q, Path.Op.UNION);
        return p;
    }

    private void sun(Canvas c, float s, float cx, float cy, float r) {
        p.setStyle(Paint.Style.STROKE);
        c.drawCircle(cx, cy, r, p);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            c.drawLine(cx + cos * r * 1.55f, cy + sin * r * 1.55f, cx + cos * r * 2.1f, cy + sin * r * 2.1f, p);
        }
    }

    private void moon(Canvas c, float cx, float cy, float r) {
        Path m = new Path();
        m.addCircle(cx, cy, r, Path.Direction.CW);
        Path cut = new Path();
        cut.addCircle(cx + r * .55f, cy - r * .35f, r * .85f, Path.Direction.CW);
        m.op(cut, Path.Op.DIFFERENCE);
        p.setStyle(Paint.Style.FILL);
        c.drawPath(m, p);
    }

    private final int kind;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private int level = 3;

    Glyph(int kind, int color) {
        this.kind = kind;
        p.setColor(color);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    void setColor(int color) { p.setColor(color); invalidateSelf(); }

    /** Wi-Fi strength 0..3, or -1 for offline. */
    void setWifiLevel(int l) { level = l; invalidateSelf(); }

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height());
        c.save();
        c.translate(b.centerX() - s / 2f, b.centerY() - s / 2f);
        p.setStrokeWidth(s * 0.075f);
        p.setStyle(Paint.Style.STROKE);
        switch (kind) {
            case LIVE:
                r.set(s * .1f, s * .2f, s * .9f, s * .72f);
                c.drawRoundRect(r, s * .06f, s * .06f, p);
                c.drawLine(s * .34f, s * .86f, s * .66f, s * .86f, p);
                break;
            case MOVIES:
                r.set(s * .12f, s * .24f, s * .88f, s * .82f);
                c.drawRoundRect(r, s * .05f, s * .05f, p);
                c.drawLine(s * .12f, s * .40f, s * .88f, s * .40f, p);
                for (int i = 0; i < 3; i++) {
                    float x = s * (.30f + i * .2f);
                    c.drawLine(x - s * .06f, s * .26f, x + s * .02f, s * .38f, p);
                }
                break;
            case SERIES:
                r.set(s * .26f, s * .14f, s * .9f, s * .6f);
                c.drawRoundRect(r, s * .05f, s * .05f, p);
                r.set(s * .1f, s * .32f, s * .74f, s * .84f);
                p.setStyle(Paint.Style.FILL);
                p.setAlpha(255);
                c.drawRoundRect(r, s * .05f, s * .05f, p);
                break;
            case APPS:
                p.setStyle(Paint.Style.FILL);
                float g = s * .12f, q = (s - 3 * g) / 2f;
                for (int i = 0; i < 2; i++) {
                    for (int j = 0; j < 2; j++) {
                        r.set(g + i * (q + g), g + j * (q + g), g + i * (q + g) + q, g + j * (q + g) + q);
                        c.drawRoundRect(r, s * .05f, s * .05f, p);
                    }
                }
                break;
            case GEAR:
                float cx = s / 2f;
                c.drawCircle(cx, cx, s * .25f, p);
                c.drawCircle(cx, cx, s * .08f, p);
                for (int i = 0; i < 8; i++) {
                    c.save();
                    c.rotate(45 * i, cx, cx);
                    c.drawLine(cx, s * .08f, cx, s * .2f, p);
                    c.restore();
                }
                break;
            case SLIDERS:
                float[] knobs = {.32f, .66f, .44f};
                for (int i = 0; i < 3; i++) {
                    float y = s * (.24f + i * .26f);
                    p.setStyle(Paint.Style.STROKE);
                    c.drawLine(s * .1f, y, s * .9f, y, p);
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(s * knobs[i], y, s * .08f, p);
                }
                break;
            case DIAMOND:
                p.setStyle(Paint.Style.FILL);
                Path d = new Path();
                d.moveTo(s / 2f, s * .08f);
                d.lineTo(s * .92f, s / 2f);
                d.lineTo(s / 2f, s * .92f);
                d.lineTo(s * .08f, s / 2f);
                d.close();
                c.drawPath(d, p);
                break;
            case WIFI:
                float wx = s / 2f, wy = s * .82f;
                int full = p.getAlpha();
                for (int i = 1; i <= 3; i++) {
                    float rad = s * .22f * i;
                    p.setAlpha(level >= i ? full : full / 3);
                    r.set(wx - rad, wy - rad, wx + rad, wy + rad);
                    c.drawArc(r, 225, 90, false, p);
                }
                p.setStyle(Paint.Style.FILL);
                p.setAlpha(level >= 0 ? full : full / 3);
                c.drawCircle(wx, wy, s * .07f, p);
                p.setAlpha(full);
                break;
            case PLAY:
                p.setStyle(Paint.Style.FILL);
                Path t = new Path();
                t.moveTo(s * .28f, s * .18f);
                t.lineTo(s * .84f, s * .5f);
                t.lineTo(s * .28f, s * .82f);
                t.close();
                c.drawPath(t, p);
                break;
            case SUN:
                sun(c, s, s * .5f, s * .5f, s * .17f);
                break;
            case MOON:
                moon(c, s * .5f, s * .5f, s * .32f);
                break;
            case PARTLY_DAY:
            case PARTLY_NIGHT: {
                // Sun or moon behind a cloud: whatever the cloud covers isn't drawn.
                Path front = cloud(s, s * .56f, s * .6f, 1f);
                c.save();
                c.clipPath(front, android.graphics.Region.Op.DIFFERENCE);
                if (kind == PARTLY_DAY) sun(c, s, s * .36f, s * .36f, s * .12f);
                else moon(c, s * .38f, s * .36f, s * .2f);
                c.restore();
                p.setStyle(Paint.Style.STROKE);
                c.drawPath(front, p);
                break;
            }
            case CLOUD:
                p.setStyle(Paint.Style.STROKE);
                c.drawPath(cloud(s, s * .5f, s * .5f, 1.15f), p);
                break;
            case FOG:
                p.setStyle(Paint.Style.STROKE);
                c.drawPath(cloud(s, s * .5f, s * .36f, .9f), p);
                c.drawLine(s * .14f, s * .7f, s * .86f, s * .7f, p);
                c.drawLine(s * .24f, s * .84f, s * .76f, s * .84f, p);
                break;
            case RAIN:
            case SNOW:
            case THUNDER: {
                p.setStyle(Paint.Style.STROKE);
                c.drawPath(cloud(s, s * .5f, s * .34f, 1f), p);
                if (kind == RAIN) {
                    for (int i = 0; i < 3; i++) {
                        float x = s * (.32f + i * .18f);
                        c.drawLine(x, s * .66f, x - s * .06f, s * .84f, p);
                    }
                } else if (kind == SNOW) {
                    p.setStyle(Paint.Style.FILL);
                    for (int i = 0; i < 3; i++) {
                        c.drawCircle(s * (.32f + i * .18f), s * (i == 1 ? .82f : .72f), s * .045f, p);
                    }
                } else {
                    p.setStyle(Paint.Style.FILL);
                    Path bolt = new Path();
                    bolt.moveTo(s * .54f, s * .56f);
                    bolt.lineTo(s * .38f, s * .76f);
                    bolt.lineTo(s * .5f, s * .76f);
                    bolt.lineTo(s * .42f, s * .94f);
                    bolt.lineTo(s * .64f, s * .7f);
                    bolt.lineTo(s * .52f, s * .7f);
                    bolt.lineTo(s * .6f, s * .56f);
                    bolt.close();
                    c.drawPath(bolt, p);
                }
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
