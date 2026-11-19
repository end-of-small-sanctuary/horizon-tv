package home.horizon.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Installed apps and the user's favourites. */
final class Apps {
    static final class App {
        String pkg;
        String label;
        Drawable icon;
        Drawable banner;
        Intent intent;
    }

    private static final List<String> DEFAULT_FAVS = Arrays.asList("com.android.vending");

    private Apps() { }

    static List<App> load(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, ResolveInfo> found = new LinkedHashMap<>();
        Map<String, String> category = new LinkedHashMap<>();
        for (String cat : new String[] {Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER}) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(c.getPackageName())) continue;
                found.put(pkg, ri);
                category.put(pkg, cat);
            }
        }
        List<App> apps = new ArrayList<>();
        for (Map.Entry<String, ResolveInfo> e : found.entrySet()) {
            ResolveInfo ri = e.getValue();
            App a = new App();
            a.pkg = e.getKey();
            a.label = String.valueOf(ri.loadLabel(pm));
            a.icon = ri.loadIcon(pm);
            a.banner = ri.activityInfo.loadBanner(pm);
            if (a.banner == null) a.banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            a.intent = new Intent(Intent.ACTION_MAIN).addCategory(category.get(a.pkg))
                    .setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name))
                    .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            apps.add(a);
        }
        Collections.sort(apps, new Comparator<App>() {
            @Override public int compare(App x, App y) { return x.label.compareToIgnoreCase(y.label); }
        });
        return apps;
    }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("home", Context.MODE_PRIVATE); }

    static List<String> favs(Context c) {
        String s = prefs(c).getString("favorites", null);
        if (s == null) return new ArrayList<>(DEFAULT_FAVS);
        List<String> out = new ArrayList<>();
        for (String p : s.split(",")) if (p.length() > 0) out.add(p);
        return out;
    }

    static void saveFavs(Context c, List<String> favs) {
        prefs(c).edit().putString("favorites", TextUtils.join(",", favs)).apply();
    }
}
