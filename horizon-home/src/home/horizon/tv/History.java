package home.horizon.tv;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** What you watched, where you stopped. Feeds the Continue Watching row. */
final class History extends SQLiteOpenHelper {

    static final class Item {
        String key;      // live:<id>, movie:<id>, ep:<id>
        String kind;     // live, movie, episode
        String title;
        String sub;
        String image;
        String url;
        String alt;      // fallback stream address (live)
        long pos;
        long dur;
        long at;
    }

    // ---- One database per IPTV playlist ------------------------------------------------------

    private static final java.util.Map<String, History> OPEN = new java.util.HashMap<>();

    /** The first playlist keeps the original file, so nothing watched before playlists existed is lost. */
    private static String fileFor(String playlistId) {
        return playlistId == null || "p1".equals(playlistId) ? "history.db" : "history-" + playlistId + ".db";
    }

    /** History of the playlist in use. */
    static synchronized History get(Context c) {
        String file = fileFor(Xtream.activeId(c));
        History h = OPEN.get(file);
        if (h == null) {
            h = new History(c, file);
            OPEN.put(file, h);
        }
        return h;
    }

    /** Deletes a removed playlist's Continue Watching and favourites. */
    static synchronized void forget(Context c, String playlistId) {
        String file = fileFor(playlistId);
        History h = OPEN.remove(file);
        if (h != null) h.close();
        c.getApplicationContext().deleteDatabase(file);
    }

    /** Kept for older callers: same as get(). */
    History(Context c) { this(c, fileFor(Xtream.activeId(c))); }

    private History(Context c, String file) { super(c.getApplicationContext(), file, null, 3); }

    // ---- "Finished" rule ------------------------------------------------------------------

    /** Movies count as watched with 10 minutes or less left (end credits); episodes at 95%. */
    static boolean finished(String kind, long pos, long dur) {
        if (dur <= 0) return false;
        if ("movie".equals(kind)) return dur - pos <= 10 * 60000L;
        return pos >= dur * 0.95;
    }

    static boolean finished(Item i) { return i != null && !"live".equals(i.kind) && finished(i.kind, i.pos, i.dur); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE h (key TEXT PRIMARY KEY, kind TEXT, title TEXT, sub TEXT, image TEXT, "
                + "url TEXT, alt TEXT, pos INTEGER, dur INTEGER, at INTEGER, hidden INTEGER DEFAULT 0)");
        createFavourites(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int from, int to) {
        if (from < 2) createFavourites(db);
        if (from < 3) db.execSQL("ALTER TABLE h ADD COLUMN hidden INTEGER DEFAULT 0");
    }

    /** Takes something off Continue Watching; its resume point is kept. Playing it again brings it back. */
    void hide(String key) {
        ContentValues v = new ContentValues();
        v.put("hidden", 1);
        getWritableDatabase().update("h", v, "key=?", new String[] {key});
    }

    /** Favourites per section (live / vod / series); data is the provider's JSON for the item. */
    private static void createFavourites(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS f (kind TEXT, id TEXT, data TEXT, at INTEGER, PRIMARY KEY (kind, id))");
    }

    boolean isFavourite(String kind, String id) {
        Cursor c = getReadableDatabase().query("f", new String[] {"id"}, "kind=? AND id=?",
                new String[] {kind, id}, null, null, null);
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    /** Adds or removes; returns true when the item is now a favourite. */
    boolean toggleFavourite(String kind, String id, String json) {
        if (isFavourite(kind, id)) {
            getWritableDatabase().delete("f", "kind=? AND id=?", new String[] {kind, id});
            return false;
        }
        ContentValues v = new ContentValues();
        v.put("kind", kind);
        v.put("id", id);
        v.put("data", json);
        v.put("at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("f", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        return true;
    }

    List<String> favourites(String kind) {
        List<String> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("f", new String[] {"data"}, "kind=?", new String[] {kind},
                null, null, "at DESC");
        try {
            while (c.moveToNext()) out.add(c.getString(0));
        } finally {
            c.close();
        }
        return out;
    }

    /** Everything recently played of one kind (live, movie, episode), newest first, finished ones included. */
    List<Item> recentOfKind(String kind, int limit) {
        List<Item> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("h", null, "kind=?", new String[] {kind}, null, null, "at DESC",
                String.valueOf(limit));
        try {
            while (c.moveToNext()) out.add(read(c));
        } finally {
            c.close();
        }
        return out;
    }

    void save(Item i) {
        ContentValues v = new ContentValues();
        v.put("key", i.key);
        v.put("kind", i.kind);
        v.put("title", i.title);
        v.put("sub", i.sub);
        v.put("image", i.image);
        v.put("url", i.url);
        v.put("alt", i.alt);
        v.put("pos", i.pos);
        v.put("dur", i.dur);
        v.put("at", System.currentTimeMillis());
        v.put("hidden", 0);   // watching it again puts it back on Continue Watching
        getWritableDatabase().insertWithOnConflict("h", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    Item get(String key) {
        Cursor c = getReadableDatabase().query("h", null, "key=?", new String[] {key}, null, null, null);
        try {
            return c.moveToFirst() ? read(c) : null;
        } finally {
            c.close();
        }
    }

    /** Most recent first; finished movies and episodes, and anything you removed, are left out. */
    List<Item> recent(int limit) {
        List<Item> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("h", null, "hidden IS NULL OR hidden=0", null, null, null, "at DESC", "40");
        try {
            while (c.moveToNext() && out.size() < limit) {
                Item i = read(c);
                if (!finished(i)) out.add(i);
            }
        } finally {
            c.close();
        }
        return out;
    }

    void clear() { getWritableDatabase().delete("h", null, null); }

    private static Item read(Cursor c) {
        Item i = new Item();
        i.key = c.getString(c.getColumnIndex("key"));
        i.kind = c.getString(c.getColumnIndex("kind"));
        i.title = c.getString(c.getColumnIndex("title"));
        i.sub = c.getString(c.getColumnIndex("sub"));
        i.image = c.getString(c.getColumnIndex("image"));
        i.url = c.getString(c.getColumnIndex("url"));
        i.alt = c.getString(c.getColumnIndex("alt"));
        i.pos = c.getLong(c.getColumnIndex("pos"));
        i.dur = c.getLong(c.getColumnIndex("dur"));
        i.at = c.getLong(c.getColumnIndex("at"));
        return i;
    }
}
