package home.horizon.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Xtream Codes API client. Playlists (one per provider account) are stored only in this app's private storage.
 * One playlist is active at a time; each has its own Continue Watching and favourites (see History).
 */
final class Xtream {
    /** Playlist id ("p1" is the original single account), or null while signing in. */
    final String id;
    /** Your name for the playlist; empty means "use the server's host name". */
    final String name;
    final String server;
    final String user;
    final String pass;
    final String fmt;
    /** "xtream" (Xtream Codes sign-in) or "m3u" (a playlist link; server holds the link). */
    final String type;

    Xtream(String server, String user, String pass) { this(null, "", server, user, pass, "m3u8"); }

    Xtream(String id, String name, String server, String user, String pass, String fmt) {
        this(id, name, server, user, pass, fmt, "xtream");
    }

    Xtream(String id, String name, String server, String user, String pass, String fmt, String type) {
        this.type = "m3u".equals(type) ? "m3u" : "xtream";
        this.id = id;
        this.name = name == null ? "" : name.trim();
        this.server = normalize(server);
        this.user = user.trim();
        this.pass = pass;
        this.fmt = fmt == null || fmt.isEmpty() ? "m3u8" : fmt;
    }

    /** What the playlist is called on screen. */
    String label() {
        if (!name.isEmpty()) return name;
        try {
            return new URL(server).getHost();
        } catch (Exception e) {
            return server;
        }
    }

    // ---- Playlists -----------------------------------------------------------------------

    private static JSONObject toJson(Xtream x) throws Exception {
        return new JSONObject().put("id", x.id).put("name", x.name).put("server", x.server)
                .put("user", x.user).put("pass", x.pass).put("fmt", x.fmt).put("type", x.type);
    }

    private static Xtream fromJson(JSONObject o) {
        return new Xtream(o.optString("id"), o.optString("name"), o.optString("server"), o.optString("user"),
                o.optString("pass"), o.optString("fmt", "m3u8"), o.optString("type", "xtream"));
    }

    /** All saved playlists, oldest first. The original single sign-in becomes playlist "p1". */
    static java.util.List<Xtream> all(Context c) {
        SharedPreferences p = prefs(c);
        java.util.List<Xtream> out = new java.util.ArrayList<>();
        if (!p.contains("lists") && p.getString("server", null) != null) {
            Xtream legacy = new Xtream("p1", "", p.getString("server", ""), p.getString("user", ""),
                    p.getString("pass", ""), p.getString("fmt", "m3u8"));
            out.add(legacy);
            writeAll(c, out, "p1");
            p.edit().remove("server").remove("user").remove("pass").remove("fmt").apply();
            return out;
        }
        try {
            JSONArray a = new JSONArray(p.getString("lists", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) {
            // damaged list: treat as empty
        }
        return out;
    }

    private static void writeAll(Context c, java.util.List<Xtream> list, String active) {
        JSONArray a = new JSONArray();
        try {
            for (Xtream x : list) a.put(toJson(x));
        } catch (Exception ignored) {
            // cannot happen with plain strings
        }
        prefs(c).edit().putString("lists", a.toString()).putString("active", active).commit();
    }

    /** Id of the playlist in use (null when there are none). */
    static String activeId(Context c) {
        java.util.List<Xtream> list = all(c);
        if (list.isEmpty()) return null;
        String a = prefs(c).getString("active", null);
        for (Xtream x : list) if (x.id.equals(a)) return a;
        return list.get(0).id;
    }

    static void setActive(Context c, String id) { writeAll(c, all(c), id); }

    /** Adds a new playlist (id null) or replaces the one with the same id. Returns the saved playlist. */
    static Xtream put(Context c, Xtream x, String fmt) {
        java.util.List<Xtream> list = all(c);
        String id = x.id != null ? x.id : "p" + System.currentTimeMillis();
        Xtream saved = new Xtream(id, x.name, x.server, x.user, x.pass, fmt, x.type);
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) { list.set(i, saved); replaced = true; }
        }
        if (!replaced) list.add(saved);
        String active = activeId(c);
        writeAll(c, list, active == null ? id : active);
        return saved;
    }

    /** Removes a playlist together with its Continue Watching and favourites. */
    static void remove(Context c, String id) {
        java.util.List<Xtream> list = all(c);
        String active = activeId(c);
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id.equals(id)) list.remove(i);
        if (id.equals(active)) active = list.isEmpty() ? null : list.get(0).id;
        writeAll(c, list, active);
        History.forget(c, id);
    }

    static Xtream byId(Context c, String id) {
        for (Xtream x : all(c)) if (x.id.equals(id)) return x;
        return null;
    }

    static String normalize(String s) {
        s = s.trim();
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://" + s;
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("xtream", Context.MODE_PRIVATE); }

    /** The playlist in use, or null when none has been added. */
    static Xtream load(Context c) {
        DiskCache.init(c);
        String id = activeId(c);
        return id == null ? null : byId(c, id);
    }

    /** Preferred live format of the playlist in use: "m3u8" when the provider allows it, else "ts". */
    static String liveFormat(Context c) {
        Xtream x = load(c);
        return x == null ? "m3u8" : x.fmt;
    }

    private String api(String action, String extra) {
        String u = server + "/player_api.php?username=" + enc(user) + "&password=" + enc(pass);
        if (action != null) u += "&action=" + action;
        if (extra != null) u += extra;
        return u;
    }

    boolean isM3u() { return "m3u".equals(type); }

    JSONObject login() throws Exception { return new JSONObject(get(api(null, null))); }

    private static final long LIST_TTL = 6 * 3600 * 1000L;

    /** kind: "live", "vod" or "series". */
    JSONArray categories(String kind) throws Exception {
        if (isM3u()) return M3u.categories(this, kind);
        return array(cached(api("get_" + kind + "_categories", null)));
    }

    JSONArray items(String kind, String categoryId) throws Exception {
        if (isM3u()) return M3u.items(this, kind, categoryId);
        String action = "series".equals(kind) ? "get_series" : "get_" + kind + "_streams";
        return array(cached(api(action, categoryId == null ? null : "&category_id=" + enc(categoryId))));
    }

    JSONObject vodInfo(String vodId) throws Exception {
        if (isM3u()) return new JSONObject().put("info", new JSONObject());   // M3U has no film details
        return new JSONObject(cached(api("get_vod_info", "&vod_id=" + enc(vodId))));
    }

    JSONObject seriesInfo(String seriesId) throws Exception {
        if (isM3u()) return M3u.seriesInfo(this, seriesId);
        return new JSONObject(cached(api("get_series_info", "&series_id=" + enc(seriesId))));
    }

    private static final long SEARCH_TTL = 24 * 3600 * 1000L;

    /** Fields kept per item in the search list (everything else is dropped while downloading). */
    private static final java.util.Set<String> KEEP = new java.util.HashSet<>(java.util.Arrays.asList(
            "stream_id", "series_id", "name", "stream_icon", "cover", "container_extension", "rating"));

    /**
     * Whole section as a compact list for searching: ONE request, at most once a day.
     * Streamed straight to storage and trimmed on the way, so a huge list never sits in memory.
     */
    JSONArray searchList(String kind) throws Exception {
        if (isM3u()) return M3u.items(this, kind, null);
        String action = "series".equals(kind) ? "get_series" : "get_" + kind + "_streams";
        String url = api(action, null);
        java.io.File f = DiskCache.apiFile("search|" + url);
        boolean fresh = f != null && f.exists() && System.currentTimeMillis() - f.lastModified() < SEARCH_TTL;
        if (!fresh) {
            try {
                downloadCompact(url, f);
            } catch (IOException e) {
                if (f == null || !f.exists()) throw e;
                // keep using yesterday's copy
            }
        }
        byte[] data = DiskCache.read(f);
        if (data == null) throw new IOException("No saved list");
        return new JSONArray(new String(data, "UTF-8"));
    }

    /** Bytes received so far by the running search-list download (for the on-screen progress). */
    static volatile long progressBytes;

    private static void downloadCompact(String address, java.io.File out) throws IOException {
        if (out == null) throw new IOException("No cache folder");
        final long started = System.currentTimeMillis();
        progressBytes = 0;
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9) HorizonHome/1.0");
        java.io.File tmp = new java.io.File(out.getPath() + ".part");
        android.util.JsonReader r = null;
        android.util.JsonWriter w = null;
        try {
            int code = c.getResponseCode();
            final long firstByteMs = System.currentTimeMillis() - started;
            android.util.Log.i("Horizon", "search list: server answered " + code + " after " + firstByteMs + " ms, encoding="
                    + c.getContentEncoding() + ", length=" + c.getContentLength());
            if (code >= 400) throw new IOException("Server answered " + code);
            java.io.InputStream counting = new java.io.FilterInputStream(c.getInputStream()) {
                @Override public int read() throws IOException {
                    int b = super.read();
                    if (b >= 0) progressBytes++;
                    return b;
                }
                @Override public int read(byte[] buf, int off, int len) throws IOException {
                    int n = super.read(buf, off, len);
                    if (n > 0) progressBytes += n;
                    return n;
                }
            };
            r = new android.util.JsonReader(new java.io.InputStreamReader(
                    new java.io.BufferedInputStream(counting, 65536), "UTF-8"));
            w = new android.util.JsonWriter(new java.io.OutputStreamWriter(
                    new java.io.BufferedOutputStream(new java.io.FileOutputStream(tmp), 65536), "UTF-8"));
            if (r.peek() != android.util.JsonToken.BEGIN_ARRAY) throw new IOException("Unexpected answer");
            r.beginArray();
            w.beginArray();
            while (r.hasNext()) {
                if (r.peek() != android.util.JsonToken.BEGIN_OBJECT) {
                    r.skipValue();
                    continue;
                }
                r.beginObject();
                w.beginObject();
                while (r.hasNext()) {
                    String name = r.nextName();
                    android.util.JsonToken t = r.peek();
                    if (KEEP.contains(name) && (t == android.util.JsonToken.STRING || t == android.util.JsonToken.NUMBER)) {
                        w.name(name).value(r.nextString());
                    } else {
                        r.skipValue();
                    }
                }
                r.endObject();
                w.endObject();
            }
            r.endArray();
            w.endArray();
            w.close();
            w = null;
            long ms = System.currentTimeMillis() - started;
            android.util.Log.i("Horizon", "search list: " + progressBytes + " bytes in " + ms + " ms ("
                    + (ms > 0 ? progressBytes * 1000 / ms / 1024 : 0) + " KB/s), saved " + tmp.length() + " bytes");
            if (!tmp.renameTo(out)) throw new IOException("Could not save the list");
        } finally {
            try { if (r != null) r.close(); } catch (IOException ignored) { }
            try { if (w != null) w.close(); } catch (IOException ignored) { }
            tmp.delete();
            c.disconnect();
        }
    }

    /**
     * Lists are kept on the stick for 6 hours. If the provider can't be reached, the last saved copy is used.
     */
    private static String cached(String url) throws IOException {
        java.io.File f = DiskCache.apiFile(url);
        boolean fresh = f != null && f.exists() && System.currentTimeMillis() - f.lastModified() < LIST_TTL;
        if (fresh) {
            byte[] data = DiskCache.read(f);
            if (data != null) return new String(data, "UTF-8");
        }
        try {
            long t0 = System.currentTimeMillis();
            byte[] data = bytes(url, 20 * 1024 * 1024);
            android.util.Log.i("Horizon", "list request: " + data.length + " bytes in "
                    + (System.currentTimeMillis() - t0) + " ms (" + url.replaceAll("password=[^&]*", "password=***") .replaceAll("username=[^&]*", "username=***") + ")");
            String body = new String(data, "UTF-8").trim();
            if (body.startsWith("[") || body.startsWith("{")) DiskCache.write(f, data);
            return body;
        } catch (IOException offline) {
            byte[] stale = DiskCache.read(f);
            if (stale != null) return new String(stale, "UTF-8");
            throw offline;
        }
    }

    // For M3U playlists the ID is the stream's own link.
    String liveUrl(String id, String ext) { if (isM3u()) return id; return server + "/live/" + user + "/" + pass + "/" + id + "." + ext; }
    String movieUrl(String id, String ext) { if (isM3u()) return id; return server + "/movie/" + user + "/" + pass + "/" + id + "." + ext; }
    String episodeUrl(String id, String ext) { if (isM3u()) return id; return server + "/series/" + user + "/" + pass + "/" + id + "." + ext; }

    private static JSONArray array(String body) throws Exception {
        String b = body.trim();
        return b.startsWith("[") ? new JSONArray(b) : new JSONArray();
    }

    static String get(String address) throws IOException {
        byte[] data = bytes(address, 20 * 1024 * 1024);
        return new String(data, "UTF-8");
    }

    static byte[] bytes(String address, int limit) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9) HorizonHome/1.0");
        try {
            int code = c.getResponseCode();
            if (code >= 400) throw new IOException("Server answered " + code);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > limit) throw new IOException("Response too large");
            }
            in.close();
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }
}
