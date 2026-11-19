package home.horizon.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Wallpaper and the colour taken from it. Photos live in this app's own folder on the stick:
 * /sdcard/Android/data/home.horizon.tv/files/wallpapers (copied there over ADB, no storage permission needed).
 */
final class Theme {
    /** Soft accent used for icons; follows the wallpaper. */
    static int accent = 0xFFE6EAF0;

    /** True when the top band of the wallpaper (behind the clock and buttons) is light. */
    static boolean topBright;

    /** Clock, date and top-button colour: dark on a light sky, light otherwise. */
    static int topText() { return topBright ? blend(0xFF0E1114, accent, 0.30f) : text(); }

    /** Colour of a focused top button's disc, and of its icon. */
    static int discFill() { return topBright ? blend(0xFF0E1114, accent, 0.25f) : 0xFFF4F6FA; }
    static int discIcon() { return topBright ? 0xFFF4F6FA : 0xFF11151B; }

    /** Average brightness (0..1) of the top quarter of the image. */
    static float topLuminance(Bitmap src) {
        Bitmap s = Bitmap.createScaledBitmap(src, 64, 36, true);
        float sum = 0;
        int n = 0;
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < s.getWidth(); x++) {
                int c = s.getPixel(x, y);
                sum += (0.2126f * Color.red(c) + 0.7152f * Color.green(c) + 0.0722f * Color.blue(c)) / 255f;
                n++;
            }
        }
        if (s != src) s.recycle();
        return n == 0 ? 0 : sum / n;
    }

    private Theme() { }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("home", Context.MODE_PRIVATE); }

    static File folder(Context c) {
        File f = c.getExternalFilesDir("wallpapers");
        if (f != null && !f.exists()) f.mkdirs();
        return f;
    }

    static List<String> wallpapers(Context c) {
        List<String> out = new ArrayList<>();
        File dir = folder(c);
        String[] names = dir == null ? null : dir.list();
        if (names == null) return out;
        Arrays.sort(names, String.CASE_INSENSITIVE_ORDER);
        for (String n : names) {
            String l = n.toLowerCase();
            if (l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp")) out.add(n);
        }
        return out;
    }

    /** Saved choice, or the first photo; "" means no wallpaper. */
    static String selected(Context c) {
        String s = prefs(c).getString("wallpaper", null);
        List<String> all = wallpapers(c);
        if (s != null && (s.isEmpty() || all.contains(s))) return s;
        return all.isEmpty() ? "" : all.get(0);
    }

    static void select(Context c, String name) { prefs(c).edit().putString("wallpaper", name).apply(); }

    /** Loads the selected photo at about screen size and updates the accent colour. Run off the main thread. */
    static Bitmap load(Context c) {
        String name = selected(c);
        if (name.isEmpty()) {
            accent = 0xFFE6EAF0;
            topBright = false;
            syncSystemWallpaper(c, "", null);
            return null;
        }
        File f = new File(folder(c), name);
        int screenW = c.getResources().getDisplayMetrics().widthPixels;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), bounds);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= screenW) sample *= 2;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
            if (b != null) {
                accent = soften(dominant(b));
                topBright = topLuminance(b) > 0.58f;
                syncSystemWallpaper(c, name, b);
            }
            return b;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    /**
     * Android TV's Settings panel shows the SYSTEM wallpaper behind it (black by default), not the home screen.
     * Mirror the chosen photo there, once per change.
     */
    private static void syncSystemWallpaper(Context c, String name, Bitmap b) {
        SharedPreferences p = prefs(c);
        if (name.equals(p.getString("systemWallpaper", null))) return;
        try {
            android.app.WallpaperManager wm = android.app.WallpaperManager.getInstance(c);
            if (b == null) wm.clear(); else wm.setBitmap(b);
            p.edit().putString("systemWallpaper", name).apply();
        } catch (Exception ignored) {
            // the home screen still shows the photo; only the Settings background stays as it was
        }
    }

    /** Most common colourful hue (36 hue buckets, weighted by saturation and brightness). */
    static int dominant(Bitmap src) {
        Bitmap s = Bitmap.createScaledBitmap(src, 64, 36, true);
        float[] weight = new float[36];
        float[] sumS = new float[36];
        float[] sumV = new float[36];
        float[] sumX = new float[36];
        float[] sumY = new float[36];
        float[] hsv = new float[3];
        long r = 0, g = 0, b = 0;
        int n = 0;
        for (int y = 0; y < s.getHeight(); y++) {
            for (int x = 0; x < s.getWidth(); x++) {
                int c = s.getPixel(x, y);
                r += Color.red(c);
                g += Color.green(c);
                b += Color.blue(c);
                n++;
                Color.colorToHSV(c, hsv);
                if (hsv[2] < 0.18f || hsv[1] < 0.12f) continue;
                int k = Math.min(35, (int) (hsv[0] / 10f));
                float w = hsv[1] * hsv[2];
                weight[k] += w;
                sumS[k] += hsv[1] * w;
                sumV[k] += hsv[2] * w;
                double a = Math.toRadians(hsv[0]);
                sumX[k] += (float) Math.cos(a) * w;
                sumY[k] += (float) Math.sin(a) * w;
            }
        }
        if (s != src) s.recycle();
        int best = -1;
        for (int k = 0; k < 36; k++) if (best < 0 || weight[k] > weight[best]) best = k;
        if (best < 0 || weight[best] < 0.5f) {
            return n == 0 ? 0xFFE6EAF0 : Color.rgb((int) (r / n), (int) (g / n), (int) (b / n));
        }
        float hue = (float) Math.toDegrees(Math.atan2(sumY[best], sumX[best]));
        if (hue < 0) hue += 360f;
        return Color.HSVToColor(new float[] {hue, sumS[best] / weight[best], sumV[best] / weight[best]});
    }

    /** Tile glass, tinted toward the wallpaper colour. */
    static int glass() { return (0x99 << 24) | (blend(0xFF14171B, accent, 0.12f) & 0xFFFFFF); }

    /** Selected tile: brighter, warmer glass in the wallpaper's colour. */
    static int glassFocus() { return (0xF0 << 24) | (blend(0xFF2B2F35, accent, 0.38f) & 0xFFFFFF); }

    /** Empty image placeholder behind thumbnails. */
    static int placeholder() { return blend(0xFF181B20, accent, 0.10f); }

    /** Main text: near-white, tinted toward the accent. */
    static int text() { return blend(0xFFFFFFFF, accent, 0.30f); }

    /** Secondary text: dimmer and more tinted. */
    static int muted() { return blend(0xFF8E949C, accent, 0.55f); }

    static int blend(int a, int b, float t) {
        return Color.rgb(
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    /** Softer, lighter version so icons stay readable on the darkened photo. */
    static int soften(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[1] = Math.min(0.42f, hsv[1] * 0.55f);
        hsv[2] = 0.94f;
        return Color.HSVToColor(hsv);
    }
}
