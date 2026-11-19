package home.horizon.tv;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Small file cache in the app's cache folder: images (shrunk to TV size) and provider lists.
 * Kept under a size limit; least recently used files are removed first.
 */
final class DiskCache {
    static final long IMAGE_LIMIT = 150L * 1024 * 1024;
    static final long API_LIMIT = 40L * 1024 * 1024;

    private static File images;
    private static File api;
    private static boolean trimmed;

    private DiskCache() { }

    static synchronized void init(Context c) {
        if (images != null) return;
        images = new File(c.getApplicationContext().getCacheDir(), "img");
        api = new File(c.getApplicationContext().getCacheDir(), "api");
        images.mkdirs();
        api.mkdirs();
        if (!trimmed) {
            trimmed = true;
            Ui.POOL.execute(new Runnable() {
                @Override public void run() {
                    trim(images, IMAGE_LIMIT);
                    trim(api, API_LIMIT);
                }
            });
        }
    }

    static File imageFile(String url) { return images == null ? null : new File(images, hash(url) + ".webp"); }

    static File apiFile(String url) { return api == null ? null : new File(api, hash(url) + ".json"); }

    static byte[] read(File f) {
        if (f == null || !f.exists()) return null;
        try {
            RandomAccessFile r = new RandomAccessFile(f, "r");
            byte[] data = new byte[(int) r.length()];
            r.readFully(data);
            r.close();
            f.setLastModified(System.currentTimeMillis());
            return data;
        } catch (IOException e) {
            return null;
        }
    }

    static void write(File f, byte[] data) {
        if (f == null) return;
        try {
            File tmp = new File(f.getPath() + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(data);
            out.close();
            if (!tmp.renameTo(f)) tmp.delete();
        } catch (IOException ignored) {
            // cache is optional
        }
    }

    /** Total size of the cache in bytes (both folders). */
    static long size() { return folderSize(images) + folderSize(api); }

    static void clear() {
        for (File d : new File[] {images, api}) {
            File[] files = d == null ? null : d.listFiles();
            if (files != null) for (File f : files) f.delete();
        }
    }

    private static long folderSize(File d) {
        File[] files = d == null ? null : d.listFiles();
        long total = 0;
        if (files != null) for (File f : files) total += f.length();
        return total;
    }

    private static void trim(File dir, long limit) {
        File[] files = dir.listFiles();
        if (files == null) return;
        long total = 0;
        for (File f : files) total += f.length();
        if (total <= limit) return;
        Arrays.sort(files, new Comparator<File>() {
            @Override public int compare(File a, File b) { return Long.compare(a.lastModified(), b.lastModified()); }
        });
        for (File f : files) {
            if (total <= limit * 0.8) break;
            total -= f.length();
            f.delete();
        }
    }

    private static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("MD5").digest(s.getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
