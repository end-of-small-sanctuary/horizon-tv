package home.horizon.tv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

/** Small image loader: downloads off the main thread, shrinks to the needed width, keeps a memory cache. */
final class Images {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(10 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap b) { return b.getByteCount(); }
    };

    private Images() { }

    static void load(final ImageView v, final String url, final int maxWidth) {
        DiskCache.init(v.getContext());
        v.setTag(url);
        if (url == null || url.length() == 0) {
            v.setImageDrawable(null);
            return;
        }
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            v.setImageBitmap(cached);
            return;
        }
        v.setImageDrawable(null);
        Ui.POOL.execute(new Runnable() {
            @Override public void run() {
                final Bitmap b = fetch(url, maxWidth);
                if (b != null) CACHE.put(url, b);
                v.post(new Runnable() {
                    @Override public void run() {
                        if (b != null && url.equals(v.getTag())) v.setImageBitmap(b);
                    }
                });
            }
        });
    }

    private static Bitmap fetch(String url, int maxWidth) {
        try {
            // 1. Saved copy on the stick (already shrunk to TV size).
            java.io.File file = DiskCache.imageFile(url);
            byte[] saved = DiskCache.read(file);
            if (saved != null) {
                Bitmap b = BitmapFactory.decodeByteArray(saved, 0, saved.length);
                if (b != null) return b;
            }
            // 2. Download, shrink, and save a small WebP copy for next time.
            byte[] data = Xtream.bytes(url, 4 * 1024 * 1024);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (b != null && file != null) {
                if (b.getWidth() > maxWidth * 1.25f) {
                    int h = Math.round(b.getHeight() * (maxWidth / (float) b.getWidth()));
                    Bitmap scaled = Bitmap.createScaledBitmap(b, maxWidth, Math.max(1, h), true);
                    if (scaled != b) b.recycle();
                    b = scaled;
                }
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                b.compress(Bitmap.CompressFormat.WEBP, 80, out);
                DiskCache.write(file, out.toByteArray());
            }
            return b;
        } catch (Exception e) {
            return null;
        } catch (OutOfMemoryError e) {
            CACHE.evictAll();
            return null;
        }
    }
}
