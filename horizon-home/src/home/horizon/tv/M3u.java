package home.horizon.tv;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * M3U / M3U8 playlist support (the other common IPTV format, besides Xtream Codes).
 *
 * The playlist is downloaded as a stream and saved on the stick (refreshed every 12 hours; the saved copy is used
 * if the provider can't be reached). Entries are sorted into Live TV, Movies and Series by their address
 * (Xtream-style /movie/ and /series/ links), file type (.mp4, .mkv ...) and names like "Show S01E02", and grouped
 * by the playlist's own group-title. Each item's ID is its own link, so playing, resuming, Continue Watching and
 * favourites work exactly as for Xtream, with nothing to look up.
 */
final class M3u {
    private M3u() { }

    private static final long TTL = 12 * 3600 * 1000L;
    private static final Pattern ATTR = Pattern.compile("([\\w-]+)=\"([^\"]*)\"");
    private static final Pattern EPISODE = Pattern.compile("^(.*?)[\\s._-]*[Ss](\\d{1,2})[\\s._-]*[Ee](\\d{1,4})\\b.*$");
    private static final String[] VIDEO_EXT = {"mp4", "mkv", "avi", "mov", "m4v", "wmv", "webm", "mpg", "mpeg"};

    /** Parsed playlist. */
    static final class Index {
        final Map<String, JSONArray> items = new HashMap<>();             // kind -> items
        final Map<String, LinkedHashMap<String, Integer>> groups = new HashMap<>();   // kind -> group -> count
        final Map<String, JSONObject> seriesInfo = new HashMap<>();       // series_id -> info + episodes
        int count;
    }

    private static final Map<String, Index> LOADED = new HashMap<>();

    /** The parsed playlist; downloads it when there's no fresh saved copy (or when force is set). */
    static synchronized Index index(Xtream x, boolean force) throws IOException {
        String url = x.server;
        Index cached = LOADED.get(url);
        File file = DiskCache.apiFile("m3u|" + url);
        boolean fresh = file != null && file.exists() && System.currentTimeMillis() - file.lastModified() < TTL;
        if (cached != null && fresh && !force) return cached;
        if (force || !fresh) {
            try {
                download(url, file);
            } catch (IOException e) {
                if (file == null || !file.exists()) throw e;   // offline: keep using the saved copy
            }
        }
        if (file == null || !file.exists()) throw new IOException("No playlist");
        Index idx = parse(new FileInputStream(file));
        LOADED.put(url, idx);
        return idx;
    }

    private static void download(String address, File out) throws IOException {
        if (out == null) throw new IOException("No cache folder");
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", Players.USER_AGENT);
        File tmp = new File(out.getPath() + ".part");
        try {
            int code = c.getResponseCode();
            if (code >= 400) throw new IOException("Server answered " + code);
            InputStream in = c.getInputStream();
            FileOutputStream fo = new FileOutputStream(tmp);
            try {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
            } finally {
                fo.close();
                in.close();
            }
            if (!tmp.renameTo(out)) {
                out.delete();
                if (!tmp.renameTo(out)) throw new IOException("Could not save the playlist");
            }
        } finally {
            tmp.delete();
            c.disconnect();
        }
    }

    private static Index parse(InputStream raw) throws IOException {
        Index idx = new Index();
        for (String k : new String[] {"live", "vod", "series"}) {
            idx.items.put(k, new JSONArray());
            idx.groups.put(k, new LinkedHashMap<String, Integer>());
        }
        Map<String, Map<String, JSONArray>> seasons = new HashMap<>();   // series_id -> season -> episodes
        BufferedReader r = new BufferedReader(new InputStreamReader(raw, "UTF-8"), 65536);
        try {
            String line, name = null, logo = "", group = "", extGroup = "";
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#EXTINF")) {
                    Map<String, String> a = new HashMap<>();
                    Matcher m = ATTR.matcher(line);
                    while (m.find()) a.put(m.group(1).toLowerCase(Locale.ROOT), m.group(2));
                    int comma = commaAfterAttributes(line);
                    name = comma >= 0 ? line.substring(comma + 1).trim() : "";
                    if (name.isEmpty()) name = a.containsKey("tvg-name") ? a.get("tvg-name") : "?";
                    logo = a.containsKey("tvg-logo") ? a.get("tvg-logo") : "";
                    group = a.containsKey("group-title") ? a.get("group-title") : "";
                    continue;
                }
                if (line.startsWith("#EXTGRP:")) {
                    extGroup = line.substring(8).trim();
                    continue;
                }
                if (line.startsWith("#")) continue;
                if (name == null) continue;                       // a link without #EXTINF: skip
                String url = line;
                String grp = !group.isEmpty() ? group : !extGroup.isEmpty() ? extGroup : "Other";
                add(idx, seasons, name, logo, grp, url);
                idx.count++;
                name = null;
                logo = "";
                group = "";
            }
        } finally {
            r.close();
        }
        // Series: put the seasons into the info objects the series page expects.
        for (Map.Entry<String, Map<String, JSONArray>> e : seasons.entrySet()) {
            JSONObject info = idx.seriesInfo.get(e.getKey());
            JSONObject eps = new JSONObject();
            try {
                for (Map.Entry<String, JSONArray> s : e.getValue().entrySet()) eps.put(s.getKey(), s.getValue());
                info.put("episodes", eps);
            } catch (Exception ignored) {
                // cannot happen with plain values
            }
        }
        return idx;
    }

    /** The display name follows the first comma that isn't inside a quoted attribute. */
    private static int commaAfterAttributes(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') quoted = !quoted;
            else if (ch == ',' && !quoted) return i;
        }
        return -1;
    }

    private static String extension(String url) {
        String path = url;
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        return dot > slash ? path.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static void add(Index idx, Map<String, Map<String, JSONArray>> seasons, String name, String logo,
                            String group, String url) {
        String ext = extension(url);
        boolean video = false;
        for (String v : VIDEO_EXT) if (v.equals(ext)) video = true;
        Matcher ep = EPISODE.matcher(name);
        String kind = url.contains("/series/") ? "series"
                : url.contains("/movie/") ? "vod"
                : video && ep.matches() ? "series"
                : video ? "vod" : "live";
        try {
            if ("series".equals(kind)) {
                String show = ep.matches() && !ep.group(1).trim().isEmpty() ? ep.group(1).trim() : name;
                String season = ep.matches() ? String.valueOf(Integer.parseInt(ep.group(2))) : "1";
                String number = ep.matches() ? String.valueOf(Integer.parseInt(ep.group(3))) : "";
                String id = "m3u-series|" + group + "|" + show;
                if (!idx.seriesInfo.containsKey(id)) {
                    idx.seriesInfo.put(id, new JSONObject().put("info", new JSONObject().put("name", show).put("cover", logo)));
                    idx.items.get("series").put(new JSONObject().put("series_id", id).put("name", show)
                            .put("cover", logo).put("category_id", group));
                    count(idx, "series", group);
                }
                Map<String, JSONArray> bySeason = seasons.get(id);
                if (bySeason == null) {
                    bySeason = new java.util.TreeMap<>(new java.util.Comparator<String>() {
                        @Override public int compare(String a, String b) {
                            return Integer.valueOf(a).compareTo(Integer.valueOf(b));
                        }
                    });
                    seasons.put(id, bySeason);
                }
                JSONArray list = bySeason.get(season);
                if (list == null) {
                    list = new JSONArray();
                    bySeason.put(season, list);
                }
                list.put(new JSONObject().put("id", url).put("episode_num", number.isEmpty() ? String.valueOf(list.length() + 1) : number)
                        .put("title", name).put("container_extension", ext).put("info", new JSONObject()));
            } else if ("vod".equals(kind)) {
                idx.items.get("vod").put(new JSONObject().put("stream_id", url).put("name", name)
                        .put("stream_icon", logo).put("category_id", group).put("container_extension", ext));
                count(idx, "vod", group);
            } else {
                idx.items.get("live").put(new JSONObject().put("stream_id", url).put("name", name)
                        .put("stream_icon", logo).put("category_id", group));
                count(idx, "live", group);
            }
        } catch (Exception ignored) {
            // skip a malformed entry
        }
    }

    private static void count(Index idx, String kind, String group) {
        Map<String, Integer> g = idx.groups.get(kind);
        Integer n = g.get(group);
        g.put(group, n == null ? 1 : n + 1);
    }

    // ---- The same calls Xtream offers -------------------------------------------------------

    static JSONArray categories(Xtream x, String kind) throws Exception {
        JSONArray out = new JSONArray();
        for (String g : index(x, false).groups.get(kind).keySet()) {
            out.put(new JSONObject().put("category_id", g).put("category_name", g));
        }
        return out;
    }

    static JSONArray items(Xtream x, String kind, String categoryId) throws Exception {
        JSONArray all = index(x, false).items.get(kind);
        if (categoryId == null) return all;
        JSONArray out = new JSONArray();
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i);
            if (o != null && categoryId.equals(o.optString("category_id"))) out.put(o);
        }
        return out;
    }

    static JSONObject seriesInfo(Xtream x, String seriesId) throws Exception {
        JSONObject info = index(x, false).seriesInfo.get(seriesId);
        return info != null ? info : new JSONObject();
    }

    /** Checks a new playlist: downloads it now and reports how many entries it has. */
    static JSONObject check(Xtream x) throws Exception {
        Index idx = index(x, true);
        return new JSONObject().put("count", idx.count)
                .put("live", idx.items.get("live").length())
                .put("vod", idx.items.get("vod").length())
                .put("series", idx.items.get("series").length());
    }

    static List<String> kinds() {
        List<String> k = new ArrayList<>();
        k.add("live");
        k.add("vod");
        k.add("series");
        return k;
    }
}
