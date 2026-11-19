"""
Works out, on any Android TV, which installed packages are invasive (tracking, analytics, ads, ad rows) and which
are optional, without a hand-made list per device.

For every package it gathers:
  - names: the package name and the APK's file/folder name (often readable, e.g. "MiTVAnalytics.apk"),
  - permissions that matter for privacy (screen capture = ACR, app-usage stats, advertising ID, silent installs),
  - behaviour: starts at boot, uses the internet, has no icon (pure background), running right now,
and sorts it into a group. Groups 1-2 (tracking/analytics/ads and ad rows) are ticked automatically; everything
else is offered unticked. Anything the device needs (its keyboards, TV inputs, home screen, Google core services,
Android core) is detected on the device and never offered.
"""
import re

# ---- groups -----------------------------------------------------------------------------------------------------
# id, title, explanation, ticked automatically, open in the list
GROUPS = [
    ("tracking", "1. Tracking, analytics and ads",
     "Report what you watch or how you use the TV, show ads, or quietly install promoted apps. Ticked for you.", True, True),
    ("rows", "2. Ad and recommendation rows",
     "Sponsored rows, banners and recommendations on the home screen. Ticked for you.", True, True),
    ("voice", "Voice assistant", "Remove only if you never use the microphone button.", False, True),
    ("access", "Accessibility", "Keep if anyone in the house uses a screen reader.", False, True),
    ("cast", "Casting and screen mirroring", "Lets phones cast or mirror to the TV.", False, True),
    ("updates", "System updates", "Turning these off also stops security updates.", False, True),
    ("apps", "Apps with an icon", "Apps you can open from the home screen.", False, True),
    ("background", "Other background services",
     "Run without an icon. Most are harmless; only remove what you recognise.", False, False),
]

# ---- built-in knowledge (better names; the scan works without it) -------------------------------------------------
KNOWN = {
    "tv.alphonso.alphonso_eula": ("tracking", "Alphonso ad tracking (identifies what's on screen)", "uninstall"),
    "com.miui.tv.analytics": ("tracking", "Xiaomi usage analytics", "uninstall"),
    "com.google.android.feedback": ("tracking", "Google crash report sender", "uninstall"),
    "com.google.android.tv.bugreportsender": ("tracking", "Google TV bug report sender", "uninstall"),
    "com.google.android.partnersetup": ("tracking", "Google partner setup and usage pings", "uninstall"),
    "com.samsung.android.tvplus": ("rows", "Samsung TV Plus promotions", "disable"),
    "com.google.android.tvrecommendations": ("rows", "Recommendation rows", "uninstall"),
    "com.mitv.tvhome.michannel": ("rows", "Xiaomi content channel row", "uninstall"),
    "com.mitv.tvhome.atv": ("rows", "Xiaomi PatchWall banners", "uninstall"),
    "com.xm.webcontent": ("rows", "Xiaomi web content viewer", "uninstall"),
    "com.cvte.tv.ucservice": ("background", "CVTE service that can install and delete apps", "disable"),
    "com.google.android.katniss": ("voice", "Google Assistant voice search", "disable"),
    "com.google.android.speech.pumpkin": ("voice", "Assistant offline voice engine", "disable"),
    "com.google.android.voicesearch": ("voice", "Google voice search", "disable"),
    "com.google.android.marvin.talkback": ("access", "TalkBack screen reader", "disable"),
    "com.google.android.apps.mediashell": ("cast", "Chromecast built-in (casting from phones)", "disable"),
    "com.ecloud.eshare.server": ("cast", "EShare screen mirroring", "disable"),
    "com.ecloud.eairplay": ("cast", "AirPlay receiver for iPhone and Mac", "disable"),
    "com.xiaomi.mitv.updateservice": ("updates", "Xiaomi system update service", "disable"),
    "com.netflix.ninja": ("apps", "Netflix", "uninstall"),
    "com.amazon.amazonvideo.livingroom": ("apps", "Prime Video", "uninstall"),
    "com.google.android.youtube.tv": ("apps", "YouTube", "uninstall"),
    "com.google.android.youtube.tvmusic": ("apps", "YouTube Music", "uninstall"),
    "com.disney.disneyplus": ("apps", "Disney+", "uninstall"),
    "net.mbc.shahidTV": ("apps", "Shahid", "uninstall"),
    "com.spotify.tv.android": ("apps", "Spotify", "uninstall"),
    "com.google.android.play.games": ("apps", "Play Games", "disable"),
    "com.google.android.videos": ("apps", "Google TV / Play Movies", "disable"),
    "com.google.android.tv": ("apps", "Live Channels", "disable"),
}

# ---- name words -------------------------------------------------------------------------------------------------
TRACK_WORDS = {"analytics", "analytic", "tracking", "tracker", "telemetry", "acr", "alphonso", "samba", "sambatv",
               "inscape", "tvsonar", "admob", "ads", "adservice", "adservices", "adsdk", "advert", "advertising",
               "adpush", "tvpush", "feedback", "bugreport", "bugreportsender", "crashreport", "crashlytics",
               "statistics", "logcollector", "logupload", "partnersetup", "autoinstall", "autoinstalls",
               "datacollection", "usagereport", "eula"}
ROW_WORDS = {"recommendation", "recommendations", "recommend", "tvrecommendations", "webcontent", "michannel",
             "patchwall", "promo", "promotion", "promotions", "banner", "featured", "sponsored", "tvplus"}
VOICE_WORDS = {"katniss", "voice", "voicesearch", "assistant", "speech", "pumpkin", "hotword", "alexa", "vui"}
ACCESS_WORDS = {"talkback", "accessibility", "marvin", "screenreader", "braille", "a11y"}
CAST_WORDS = {"mediashell", "cast", "chromecast", "miracast", "airplay", "eairplay", "eshare", "dlna", "mirror",
              "mirroring", "screenmirror", "airscreen", "wfd", "wifidisplay", "projection"}
UPDATE_WORDS = {"update", "updater", "updateservice", "ota", "upgrade", "systemupgrade", "fota", "otaupdate"}
# Words that mean "the TV needs this" - never offered.
ESSENTIAL_WORDS = {"input", "tvinput", "hdmi", "cec", "remote", "bluetooth", "wifi", "keyboard", "ime", "inputmethod",
                   "setupwizard", "provision", "packageinstaller", "permissioncontroller", "systemui", "settings",
                   "tvsettings", "launcher", "webview", "framework", "overlay", "certinstaller", "keychain",
                   "externalstorage", "documentsui", "mtp", "shell", "networkstack", "captiveportal", "audio",
                   "picture", "pq", "display", "tvservice", "tvserver", "factory", "fac", "systemcontrol", "hal",
                   "hardware", "tvplayer", "atv", "dtv", "tuner", "epg", "channels", "power", "standby", "tvsetup",
                   "setup", "oobe", "gms", "gsf", "playstore"}
# Same idea for names written as one word, e.g. a "TvService" or "SystemControl" folder.
ESSENTIAL_SUBSTRINGS = ("tvservice", "tvserver", "systemcontrol", "tvinput", "hdmi", "remote", "bluetooth",
                        "keyboard", "inputmethod", "launcher", "settings", "setup", "systemui", "webview", "provision")
# Packages / prefixes always kept.
CORE = {"android", "com.android.vending", "com.google.android.gms", "com.google.android.gsf",
        "com.android.location.fused", "com.google.android.tv.remote.service", "com.google.android.webview",
        "com.android.webview", "com.google.android.ext.services", "com.google.android.ext.shared",
        "com.google.android.packageinstaller", "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
        "com.google.android.backdrop", "home.horizon.tv"}
CORE_PREFIXES = ("com.android.", "android.ext.", "com.google.android.gms", "com.google.android.gsf",
                 "com.google.android.tungsten", "com.google.android.configupdater", "com.qualcomm.", "com.mediatek.",
                 "com.droidlogic", "com.amlogic", "com.realtek", "vendor.", "org.codeaurora")
# com.android.* packages that ARE invasive (exceptions to the prefix above).
CORE_EXCEPTIONS = {"com.android.partnerbrowsercustomizations.tmobile"}

PRIVACY_PERMS = {
    "android.permission.CAPTURE_VIDEO_OUTPUT": "can capture what's on screen",
    "android.permission.CAPTURE_SECURE_VIDEO_OUTPUT": "can capture what's on screen",
    "android.permission.READ_FRAME_BUFFER": "can capture what's on screen",
    "android.permission.CAPTURE_AUDIO_OUTPUT": "can record the TV's sound",
    "android.permission.PACKAGE_USAGE_STATS": "reads which apps you use",
    "com.google.android.gms.permission.AD_ID": "uses the advertising ID",
    "android.permission.INSTALL_PACKAGES": "can install apps silently",
    "android.permission.DELETE_PACKAGES": "can delete apps",
}


def words(text):
    """'MiTVAnalytics.apk' / 'com.miui.tv.analytics' -> {'mi','tv','analytics','miui',...} (lower case)."""
    text = re.sub(r"([a-z])([A-Z])", r"\1 \2", text)          # camelCase
    text = re.sub(r"([A-Z]+)([A-Z][a-z])", r"\1 \2", text)    # TVAnalytics
    parts = re.split(r"[^A-Za-z0-9]+", text)
    return {p.lower() for p in parts if p}


def nice_name(pkg, apk_path):
    """Readable name from the APK folder ('MiTVAnalytics' -> 'Mi TV Analytics'), else from the package."""
    folder = ""
    m = re.search(r"/(?:priv-app|app)/([^/]+)/", apk_path or "")
    if m and not m.group(1).startswith(pkg):
        folder = m.group(1)
    if folder:
        s = re.sub(r"([a-z])([A-Z])", r"\1 \2", folder)
        s = re.sub(r"([A-Z]+)([A-Z][a-z])", r"\1 \2", s)
        return s.replace("_", " ").strip()
    last = pkg.split(".")[-1]
    return last[:1].upper() + last[1:]


def collect(adb):
    """Everything the classification needs, from about ten adb calls."""
    sh = adb.shell
    paths = {}
    for line in sh("pm list packages -f -u").splitlines():
        m = re.match(r"package:(.*)=([^=\s]+)$", line.strip())
        if m:
            paths[m.group(2)] = m.group(1)
    present = set(re.findall(r"package:(\S+)", sh("pm list packages")))
    disabled = set(re.findall(r"package:(\S+)", sh("pm list packages -d")))
    user = set(re.findall(r"package:(\S+)", sh("pm list packages -3")))
    running = set()
    for line in sh("ps -A -o NAME 2>/dev/null || ps -A").splitlines():
        parts = line.split()
        if parts:
            running.add(parts[-1].split(":")[0])
    launchable = set()
    for cat in ("android.intent.category.LEANBACK_LAUNCHER", "android.intent.category.LAUNCHER"):
        launchable |= set(re.findall(r"packageName=(\S+)",
                                     sh("cmd package query-activities -a android.intent.action.MAIN -c " + cat)))
    boot = set(re.findall(r"packageName=(\S+)",
                          sh("cmd package query-receivers -a android.intent.action.BOOT_COMPLETED")))
    essential = set(re.findall(r"^([\w.]+)/", sh("ime list -a -s"), re.M))
    essential |= set(re.findall(r"packageName=(\S+)", sh("cmd package query-services -a android.media.tv.TvInputService")))
    home = sh("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
    essential |= {l.split("/")[0].strip() for l in home.splitlines() if "/" in l}
    # Permissions: one dump of every package.
    perms = {}
    internet = set()
    current = None
    for line in sh("dumpsys package packages", timeout=180).splitlines():
        m = re.match(r"\s*Package \[([^\]]+)\]", line)
        if m:
            current = m.group(1)
            continue
        if current:
            for perm in PRIVACY_PERMS:
                if perm in line and "granted=false" not in line:
                    perms.setdefault(current, set()).add(perm)
            if "android.permission.INTERNET" in line:
                internet.add(current)
    return {"paths": paths, "present": present, "disabled": disabled, "user": user, "running": running,
            "launchable": launchable, "boot": boot, "essential": essential, "perms": perms, "internet": internet}


def is_core(pkg, w, info):
    if pkg in CORE or pkg in info["essential"]:
        return True
    if pkg.startswith(CORE_PREFIXES) and pkg not in CORE_EXCEPTIONS:
        return True
    if "/overlay/" in info["paths"].get(pkg, "") or pkg.endswith(".overlay") or "overlay" in w:
        return True
    return False


def classify(info):
    """List of rows: pkg, name, group, ticked, action, reasons, system, status, done."""
    rows = []
    for pkg, path in sorted(info["paths"].items()):
        system = pkg not in info["user"]
        w = words(pkg) | words(path.split("/")[-2] if path.count("/") > 2 else "")
        known = KNOWN.get(pkg)
        group, reasons = None, []
        # Privacy-relevant permissions (screen capture = how ACR ad tracking works).
        perm_notes = sorted({PRIVACY_PERMS[p] for p in info["perms"].get(pkg, ())})
        background = pkg not in info["launchable"]
        tracks = bool(w & TRACK_WORDS) or pkg.startswith("android.autoinstalls.config")
        rowsish = bool(w & ROW_WORDS)
        if known:
            group, name, action = known
            reasons.append("known")
        else:
            name, action = nice_name(pkg, path), "disable" if system else "uninstall"
            if tracks:
                group = "tracking"
                reasons.append("name: " + ", ".join(sorted(w & TRACK_WORDS)) if w & TRACK_WORDS else "auto-installer")
                action = "uninstall"
            elif rowsish:
                group = "rows"
                reasons.append("name: " + ", ".join(sorted(w & ROW_WORDS)))
                action = "uninstall"
        # Core/essential packages are never offered, unless they're clearly trackers by name or known.
        if not known and not tracks and is_core(pkg, w, info):
            continue
        if not known and not tracks and (w & ESSENTIAL_WORDS) and system:
            continue
        # Joined words too ("TvService", "SystemControl"), for system apps.
        joined = (pkg + "/" + (path.split("/")[-2] if path.count("/") > 2 else "")).lower()
        if not known and not tracks and system and any(s in joined for s in ESSENTIAL_SUBSTRINGS):
            continue
        if group is None:
            if "can capture what's on screen" in perm_notes and background and not (w & CAST_WORDS):
                group, action = "tracking", "disable"
                reasons.append("captures the screen in the background (ACR)")
            elif w & VOICE_WORDS:
                group = "voice"
            elif w & ACCESS_WORDS:
                group = "access"
            elif w & CAST_WORDS:
                group = "cast"
            elif w & UPDATE_WORDS:
                group = "updates"
            elif not background:
                group = "apps"
            elif system and pkg.startswith("com.google.android.") and not perm_notes:
                continue        # Google's own plumbing (backup, sync, setup helpers): not worth the risk
            else:
                group = "background"
        reasons += perm_notes
        if pkg in info["boot"] and pkg in info["internet"] and background:
            reasons.append("starts with the TV, uses the internet")
        present = pkg in info["present"]
        enabled = pkg not in info["disabled"]
        status = "Removed" if not present else "Disabled" if not enabled else (
            "Running" if pkg in info["running"] else "Installed")
        done = status in ("Removed", "Disabled")
        auto = next(g[3] for g in GROUPS if g[0] == group)
        rows.append({"pkg": pkg, "name": name, "group": group, "action": action, "system": system,
                     "status": status, "done": done, "ticked": auto and not done,
                     "reasons": "; ".join(r for r in reasons if r != "known") or ("known" if known else "")})
    return rows
