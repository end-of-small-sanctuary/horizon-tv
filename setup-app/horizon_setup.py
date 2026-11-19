#!/usr/bin/env python3
"""
Horizon TV Setup - clean up an Android TV device and set up Horizon Home, from Windows, without typing commands.

Tabs:
  1. Connect      - find the TV/stick over Wi-Fi (ADB) and show what it is.
  2. Clean up     - scan known bloat groups, see what's installed and running, tick what to disable/uninstall, apply.
  3. Home screen  - install Horizon Home, make it the home screen, switch the stock launcher off (or back on).
  4. Wallpapers   - add, rename and remove wallpapers (resized to the screen automatically).
  5. App icons    - give any app on the home row your own single-colour icon.
  6. Undo         - every change is recorded per device and can be reversed here.

Nothing is ever changed without a confirmation, and every change is reversible: system apps are only disabled
or removed for the current user (they come back with one click), and apps the user installed are backed up to
the PC before they are uninstalled (unless that box is unticked).
"""
import json
import os
import queue
import re
import subprocess
import sys
import tempfile
import threading
from datetime import datetime

import tkinter as tk
from tkinter import ttk, filedialog, messagebox, simpledialog

from PIL import Image, ImageChops, ImageOps, ImageTk

import analyse
import i18n
from i18n import _, L, R, W, J

i18n.install()   # translate static texts on widgets and dialogs

APP = "Horizon TV Setup"
HOME_PKG = "home.horizon.tv"
HOME_ACTIVITY = "home.horizon.tv/.HomeActivity"
KEY_SERVICE = "home.horizon.tv/home.horizon.tv.KeyService"
APP_FILES = "/sdcard/Android/data/home.horizon.tv/files"
WALL_DIR = APP_FILES + "/wallpapers"
ICON_DIR = APP_FILES + "/icons"
DATA = os.path.join(os.path.expanduser("~"), "Documents", APP)
NO_WINDOW = 0x08000000 if os.name == "nt" else 0


def asset(*parts):
    """Files bundled with the program (adb, Horizon Home). Works both as a script and inside the .exe."""
    base = getattr(sys, "_MEIPASS", os.path.dirname(os.path.abspath(__file__)))
    return os.path.join(base, "assets", *parts)


# Friendly names for the App icons list (packages not listed here are shown as-is).
NAMES = {pkg: v[1] for pkg, v in analyse.KNOWN.items()}
NAMES.update({
    "com.android.vending": "Play Store", "com.mitv.dream": "Xiaomi screensaver", "org.videolan.vlc": "VLC",
    "com.plexapp.android": "Plex", "org.jellyfin.androidtv": "Jellyfin", "com.cxinventor.file.explorer": "Cx File Explorer",
    "org.localsend.localsend_app": "LocalSend", "flar2.homebutton": "Button Mapper", "com.phlox.tvwebbrowser": "TV Bro",
    "com.crunchyroll.crunchyroid": "Crunchyroll", "tv.twitch.android.app": "Twitch", "com.vuclip.viu": "Viu",
})

# Stock home screens, switched off only after Horizon Home is the home screen (and back on to undo).
STOCK_LAUNCHERS = [
    "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx", "com.cvte.tv.launcher.fall",
    "com.mitv.tvhome", "com.amazon.tv.launcher", "com.android.launcher3",
]

RESOLUTIONS = [(_("Auto (from the device)"), None), ("720p (1280x720)", (1280, 720)),
               ("1080p (1920x1080)", (1920, 1080)), ("1440p (2560x1440)", (2560, 1440)),
               ("4K (3840x2160)", (3840, 2160))]


# ---------------------------------------------------------------------------------------------------------------
# ADB
# ---------------------------------------------------------------------------------------------------------------
class Adb:
    def __init__(self):
        self.exe = self._find()
        self.serial = None

    @staticmethod
    def _find():
        bundled = asset("adb")
        if os.path.isfile(os.path.join(bundled, "adb.exe")):
            # Run adb from a permanent folder, not the .exe's temporary unpack folder: adb leaves a background
            # server running, which would otherwise lock that folder and stop Windows from cleaning it up.
            home = os.path.join(os.environ.get("LOCALAPPDATA", os.path.expanduser("~")), APP, "adb")
            try:
                os.makedirs(home, exist_ok=True)
                for name in os.listdir(bundled):
                    src, dst = os.path.join(bundled, name), os.path.join(home, name)
                    if not os.path.isfile(dst) or os.path.getsize(dst) != os.path.getsize(src):
                        import shutil
                        shutil.copy2(src, dst)
                return os.path.join(home, "adb.exe")
            except OSError:
                return os.path.join(bundled, "adb.exe")   # in use (e.g. adb already running): use it as is
        p = os.path.join(os.path.expanduser("~"), "platform-tools", "adb.exe")
        return p if os.path.isfile(p) else "adb"

    def run(self, *args, timeout=90, device=True):
        cmd = [self.exe] + (["-s", self.serial] if device and self.serial else []) + list(args)
        try:
            r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace",
                               timeout=timeout, creationflags=NO_WINDOW)
            return r.returncode, (r.stdout + r.stderr).strip()
        except subprocess.TimeoutExpired:
            return 1, _("timed out")
        except FileNotFoundError:
            return 1, _("adb not found")

    def shell(self, command, timeout=90):
        return self.run("shell", command, timeout=timeout)[1]

    def devices(self):
        _rc, out = self.run("devices", device=False)
        found = []
        for line in out.splitlines()[1:]:
            parts = line.split()
            if len(parts) >= 2:
                found.append((parts[0], parts[1]))
        return found


# ---------------------------------------------------------------------------------------------------------------
# Images
# ---------------------------------------------------------------------------------------------------------------
def fit_wallpaper(src, dst, size):
    """Crop to the screen's shape from the centre and resize; photos smaller than the screen are never enlarged."""
    w, h = size
    im = ImageOps.exif_transpose(Image.open(src)).convert("RGB")
    scale = max(w / im.width, h / im.height)
    if scale > 1:  # smaller than the screen: keep its own pixels, cropped to the screen's shape
        cw = min(im.width, round(im.height * w / h))
        w, h = cw, round(cw * size[1] / size[0])
    im = ImageOps.fit(im, (w, h), Image.LANCZOS, centering=(0.5, 0.5))
    # Highest practical quality: JPEG 95 with full colour detail (no 4:2:0 chroma subsampling).
    im.save(dst, "JPEG", quality=95, subsampling=0, optimize=True)
    return im.size


def make_icon(src, dst):
    """
    Any logo picture -> white shape on transparent, as Horizon Home expects.
    Transparent logos keep their outline; otherwise the background colour (taken from the edges) is removed,
    which works for dark-on-light and light-on-dark logos alike. Trimmed to the logo and capped at 256 px tall.
    """
    im = ImageOps.exif_transpose(Image.open(src)).convert("RGBA")
    # Work at most 1024 px on the long side: far more than a 256-px icon needs, and instant for any photo.
    im.thumbnail((1024, 1024), Image.LANCZOS)
    W, H = im.size
    alpha = im.getchannel("A")
    # Background colour from a 1-px frame around the picture.
    frame = [im.crop((0, 0, W, 1)), im.crop((0, H - 1, W, H)), im.crop((0, 0, 1, H)), im.crop((W - 1, 0, W, H))]
    edge = [p for f in frame for p in f.getdata()]
    opaque = [p for p in edge if p[3] > 200]
    if len(opaque) < len(edge) * 0.6:
        mask = alpha                                       # transparent logo: its own outline is the shape
    else:
        bgc = tuple(sum(p[i] for p in opaque) // len(opaque) for i in range(3))
        diff = ImageChops.difference(im.convert("RGB"), Image.new("RGB", im.size, bgc))
        r, g, b = diff.split()
        distance = ImageChops.add(ImageChops.add(r, g), b)   # colour distance (capped at 255; only 60-140 matters)
        # Soft edge between 60 and 140, so curves stay smooth; then keep the picture's own transparency.
        mask = distance.point(lambda d: 0 if d <= 60 else 255 if d >= 140 else (d - 60) * 255 // 80)
        mask = ImageChops.multiply(mask, alpha)
    box = mask.getbbox()
    if not box:
        raise ValueError(_("Couldn't find a logo in that picture (it looks like one flat colour)."))
    mask = mask.crop(box)
    if mask.height > 256:
        mask = mask.resize((max(1, round(mask.width * 256 / mask.height)), 256), Image.LANCZOS)
    out = Image.new("RGBA", mask.size, (255, 255, 255, 0))
    out.putalpha(mask)
    out.save(dst, "PNG")
    return out


# ---------------------------------------------------------------------------------------------------------------
# The window
# ---------------------------------------------------------------------------------------------------------------
class App(tk.Tk):
    CHECK, BLANK = "☑", "☐"

    def __init__(self):
        super().__init__()
        self.title(_(APP))
        # Sizes below are for a normal-DPI screen; s() scales them for high-resolution displays.
        self.k = max(1.0, self.winfo_fpixels("1i") / 96.0)
        w = min(self.s(1120), int(self.winfo_screenwidth() * 0.9))
        h = min(self.s(780), int(self.winfo_screenheight() * 0.82))
        self.geometry("%dx%d+%d+%d" % (w, h, (self.winfo_screenwidth() - w) // 2, max(0, (self.winfo_screenheight() - h) // 3)))
        self.minsize(self.s(900), self.s(620))
        self.adb = Adb()
        self.events = queue.Queue()
        self.device_id = None          # stable id (serial number) used for the per-device folder
        self.screen = (1920, 1080)
        self.busy = False
        self.rows = {}                 # Clean-up tree item -> info
        self.thumb = None

        style = ttk.Style(self)
        try:
            style.theme_use("vista")
        except tk.TclError:
            pass
        style.configure("Treeview", rowheight=self.s(26))
        style.configure("Title.TLabel", font=("Segoe UI", 15, "bold"))
        style.configure("Note.TLabel", foreground="#555")
        style.configure("TLabel", font=("Segoe UI", 10))
        style.configure("TButton", font=("Segoe UI", 10))
        style.configure("Treeview", font=("Segoe UI", 10))
        style.configure("Treeview.Heading", font=("Segoe UI", 10, "bold"))

        # Language switch (Arabic is the default; the window reopens in the chosen language).
        top = ttk.Frame(self)
        top.pack(fill="x", padx=10, pady=(8, 0))
        self.lang_pick = ttk.Combobox(top, state="readonly", width=10, values=["العربية", "English"])
        self.lang_pick.current(0 if i18n.LANG == "ar" else 1)
        self.lang_pick.pack(side=R)
        ttk.Label(top, text="Language:").pack(side=R, padx=6)
        self.lang_pick.bind("<<ComboboxSelected>>", self._switch_language)

        self.tabs = ttk.Notebook(self)
        self.tabs.pack(fill="both", expand=True, padx=10, pady=(4, 4))
        self._build_connect()
        self._build_cleanup()
        self._build_home()
        self._build_wallpapers()
        self._build_icons()
        self._build_undo()

        bottom = ttk.Frame(self)
        bottom.pack(fill="x", padx=10, pady=(0, 10))
        self.progress = ttk.Progressbar(bottom, mode="determinate")
        self.progress.pack(fill="x")
        self.log = tk.Text(bottom, height=7, wrap="word", state="disabled", font=("Segoe UI", 9))
        self.log.tag_configure("line", justify=J)
        self.log.pack(fill="x", pady=(4, 0))

        self.after(100, self._pump)
        self._set_device_tabs(False)
        self.after(300, self.refresh_devices)
        self.after(600, self._reattach)

    def _reattach(self):
        """After a language switch, reconnect to the TV that was in use."""
        p = os.path.join(DATA, "last_serial.txt")
        try:
            with open(p, encoding="utf-8") as fh:
                serial = fh.read().strip()
            os.remove(p)
        except OSError:
            return
        if dict(self.adb.devices()).get(serial) == "device":
            self._attach(serial)

    def s(self, v):
        return int(v * self.k)

    def _switch_language(self, _e=None):
        lang = "ar" if self.lang_pick.current() == 0 else "en"
        if lang == i18n.LANG:
            return
        i18n.save_lang(lang)
        if self.busy:
            return
        if messagebox.askyesno(APP, "The language changes when the program restarts. Restart now?"):
            if self.adb.serial:     # reconnect to the same TV after the restart
                with open(os.path.join(DATA, "last_serial.txt"), "w", encoding="utf-8") as fh:
                    fh.write(self.adb.serial)
            args = [sys.executable] if getattr(sys, "frozen", False) else [sys.executable, os.path.abspath(__file__)]
            # A fresh copy that unpacks itself: don't let it inherit this copy's temporary-folder settings,
            # since this copy deletes that folder as it closes.
            env = {k: v for k, v in os.environ.items() if not k.startswith(("_MEI", "_PYI"))}
            env["PYINSTALLER_RESET_ENVIRONMENT"] = "1"
            subprocess.Popen(args, env=env, close_fds=True,
                             creationflags=0x00000008 | 0x00000200 if os.name == "nt" else 0)  # detached, new group
            self.destroy()

    # ---- plumbing ------------------------------------------------------------------------------------------
    def say(self, text):
        self.events.put(("log", text))

    def _pump(self):
        try:
            while True:
                kind, value = self.events.get_nowait()
                if kind == "log":
                    self.log.configure(state="normal")
                    self.log.insert("end", value + "\n", "line")
                    self.log.see("end")
                    self.log.configure(state="disabled")
                    if self.device_id:
                        try:
                            with open(os.path.join(self.device_dir(), "log.txt"), "a", encoding="utf-8") as f:
                                plain = re.sub("[‪-‮⁦-⁩]", "", value)   # no direction marks on disk
                                f.write(datetime.now().strftime("%Y-%m-%d %H:%M:%S  ") + plain + "\n")
                        except OSError:
                            pass
                elif kind == "progress":
                    self.progress.configure(maximum=value[1], value=value[0])
                elif kind == "call":
                    value()
        except queue.Empty:
            pass
        self.after(100, self._pump)

    def ui(self, fn):
        """Run fn on the window's thread."""
        self.events.put(("call", fn))

    def task(self, fn, done=None):
        """Run fn in the background so the window stays responsive."""
        if self.busy:
            messagebox.showinfo(APP, "Please wait for the current step to finish.")
            return
        self.busy = True
        self.config(cursor="watch")

        def work():
            try:
                fn()
            except Exception as e:  # report, never crash the window
                self.say(_("Error: %s") % e)
            finally:
                def finish():
                    self.busy = False
                    self.config(cursor="")
                    self.progress.configure(value=0)
                    if done:
                        done()
                self.ui(finish)
        threading.Thread(target=work, daemon=True).start()

    def device_dir(self, *parts):
        d = os.path.join(DATA, "devices", self.device_id or "unknown", *parts)
        os.makedirs(d, exist_ok=True)
        return d

    def changes(self):
        p = os.path.join(self.device_dir(), "changes.json")
        try:
            with open(p, encoding="utf-8") as f:
                return json.load(f)
        except (OSError, ValueError):
            return []

    def record(self, entry):
        items = self.changes()
        entry.setdefault("time", datetime.now().strftime("%Y-%m-%d %H:%M"))
        entry.setdefault("undone", False)
        items.append(entry)
        with open(os.path.join(self.device_dir(), "changes.json"), "w", encoding="utf-8") as f:
            json.dump(items, f, indent=1)

    def save_changes(self, items):
        with open(os.path.join(self.device_dir(), "changes.json"), "w", encoding="utf-8") as f:
            json.dump(items, f, indent=1)

    def _set_device_tabs(self, on):
        for i in range(1, 6):
            self.tabs.tab(i, state="normal" if on else "disabled")

    # ---- 1. Connect ----------------------------------------------------------------------------------------
    def _build_connect(self):
        f = ttk.Frame(self.tabs, padding=18)
        self.tabs.add(f, text="  1. Connect  ")
        ttk.Label(f, text="Connect to your TV or stick", style="Title.TLabel").pack(anchor=W)
        steps = (
            "On the TV, one time only:\n"
            "  1. Settings > Device Preferences > About: press  Build  7 times (\"You are now a developer\").\n"
            "  2. Settings > Device Preferences > Developer options: turn on  USB debugging  "
            "(and  Network debugging  if it's listed).\n"
            "  3. Find the TV's IP address in Settings > Network & Internet > your Wi-Fi.\n"
            "Then type the IP address below and press Connect. Accept the prompt that appears on the TV\n"
            "(tick \"Always allow from this computer\")."
        )
        ttk.Label(f, text=steps, justify=J).pack(anchor=W, pady=(8, 14))
        row = ttk.Frame(f)
        row.pack(anchor=W)
        ttk.Label(row, text="TV's IP address:").pack(side=L)
        self.ip = tk.StringVar(value=self._last_ip())
        e = ttk.Entry(row, textvariable=self.ip, width=22)
        e.pack(side=L, padx=8)
        e.bind("<Return>", lambda _e: self.connect())
        ttk.Button(row, text="Connect", command=self.connect).pack(side=L)
        ttk.Label(f, text="Already connected (Wi-Fi or USB):", style="Note.TLabel").pack(anchor=W, pady=(16, 2))
        row2 = ttk.Frame(f)
        row2.pack(anchor=W)
        self.device_pick = ttk.Combobox(row2, state="readonly", width=40)
        self.device_pick.pack(side=L)
        ttk.Button(row2, text="Use this one", command=self.use_picked).pack(side=L, padx=8)
        ttk.Button(row2, text="Refresh", command=self.refresh_devices).pack(side=L)
        self.info = ttk.Label(f, text="Not connected.", justify=J, font=("Segoe UI", 10))
        self.info.pack(anchor=W, pady=(20, 0))

    def _last_ip(self):
        try:
            with open(os.path.join(DATA, "last_ip.txt"), encoding="utf-8") as fh:
                return fh.read().strip()
        except OSError:
            return ""

    def refresh_devices(self):
        devs = self.adb.devices()
        self.device_pick["values"] = ["%s  (%s)" % d for d in devs]
        if devs and not self.device_pick.get():
            self.device_pick.current(0)

    def use_picked(self):
        v = self.device_pick.get()
        if v:
            self._attach(v.split()[0])

    def connect(self):
        ip = self.ip.get().strip()
        if not ip:
            messagebox.showinfo(APP, "Type the TV's IP address first, for example 192.168.1.50.")
            return
        target = ip if ":" in ip else ip + ":5555"

        def work():
            self.say(_("Connecting to %s ...") % target)
            _rc, out = self.adb.run("connect", target, device=False, timeout=20)
            self.say(out)
            os.makedirs(DATA, exist_ok=True)
            with open(os.path.join(DATA, "last_ip.txt"), "w", encoding="utf-8") as fh:
                fh.write(ip)
        # Attach once this step has finished, so the automatic scan that follows isn't refused as "busy".
        self.task(work, done=lambda: self._attach(target))

    def _attach(self, serial):
        state = dict(self.adb.devices()).get(serial)
        if state == "unauthorized":
            messagebox.showinfo(APP, "The TV is asking for permission. Accept the prompt on the TV screen "
                                     "(tick \"Always allow\"), then press Connect again.")
            return
        if state != "device":
            messagebox.showerror(APP, _("Couldn't reach %s.\n\nCheck that the TV is on, on the same Wi-Fi as this "
                                        "PC, and that USB/Network debugging is turned on.") % serial)
            return
        self.adb.serial = serial
        get = lambda p: self.adb.shell("getprop " + p).strip()
        maker, model, android = get("ro.product.manufacturer"), get("ro.product.model"), get("ro.build.version.release")
        self.device_id = re.sub(r"[^A-Za-z0-9_.-]", "_", get("ro.serialno") or serial)
        m = re.search(r"(\d+)x(\d+)", self.adb.shell("wm size"))
        if m:
            a, b = int(m.group(1)), int(m.group(2))
            self.screen = (max(a, b), min(a, b))
        self.info.configure(text=_("Connected:  %s %s  -  Android %s  -  screen %dx%d\nChanges and backups for this "
                                   "device are kept in:  %s") % (maker, model, android, self.screen[0], self.screen[1],
                                                                 self.device_dir()))
        self.say(_("Connected to %s %s (Android %s).") % (maker, model, android))
        self._set_device_tabs(True)
        self.refresh_devices()
        self.tabs.select(1)
        self.scan()

    # ---- 2. Clean up -----------------------------------------------------------------------------------------
    def _build_cleanup(self):
        f = ttk.Frame(self.tabs, padding=12)
        self.tabs.add(f, text="  2. Clean up  ")
        top = ttk.Frame(f)
        top.pack(fill="x")
        ttk.Label(top, text="Choose what to switch off or remove", style="Title.TLabel").pack(side=L)
        ttk.Button(top, text="Scan again", command=self.scan).pack(side=R)
        ttk.Label(f, style="Note.TLabel", text=(
            "The TV was scanned automatically: groups 1 and 2 (tracking, ads) are ticked for you; tick anything else "
            "you want gone. Click the Action cell to switch between Disable and Uninstall.\nEssentials (remote, "
            "keyboard, Play Store, Google services, Wi-Fi, TV inputs, home screen) are detected and never listed."
        )).pack(anchor=W, pady=(4, 6))

        # The app name is a normal column (not the tree's own first column, which Tk always draws on the left),
        # so the whole column order can mirror for Arabic. The tree column only holds the group expand arrow.
        cols = ("name", "action", "status", "kind", "why", "package")
        wrap = ttk.Frame(f)
        wrap.pack(fill="both", expand=True)
        self.tree = ttk.Treeview(wrap, columns=cols, selectmode="browse")
        self.tree["displaycolumns"] = tuple(reversed(cols)) if i18n.RTL else cols
        self.tree.heading("#0", text="")
        self.tree.heading("name", text="App")
        self.tree.heading("action", text="Action")
        self.tree.heading("status", text="Status now")
        self.tree.heading("kind", text="Type")
        self.tree.heading("why", text="Why it's listed")
        self.tree.heading("package", text="Package")
        self.tree.column("#0", width=self.s(34), stretch=False)
        # Fixed widths for the small columns; the name column takes whatever room is left, so nothing is cut off.
        self.tree.column("name", width=self.s(300), minwidth=self.s(200), stretch=True)
        self.tree.column("action", width=self.s(100), anchor="center", stretch=False)
        self.tree.column("status", width=self.s(80), stretch=False)
        self.tree.column("kind", width=self.s(95), stretch=False)
        self.tree.column("why", width=self.s(250), stretch=False)
        self.tree.column("package", width=self.s(210), stretch=False)
        sb = ttk.Scrollbar(wrap, orient="vertical", command=self.tree.yview)
        self.tree.configure(yscrollcommand=sb.set)
        self.tree.tag_configure("group", font=("Segoe UI", 10, "bold"))
        self.tree.tag_configure("done", foreground="#888")
        self.tree.tag_configure("running", foreground="#0a6b2b")
        self.tree.pack(side=L, fill="both", expand=True)
        sb.pack(side=R, fill="y")
        self.tree.bind("<Button-1>", self._tree_click)

        bar = ttk.Frame(f)
        bar.pack(fill="x", pady=(8, 0))
        self.backup_apks = tk.BooleanVar(value=True)
        ttk.Checkbutton(bar, text="Back up apps I installed before uninstalling them", variable=self.backup_apks
                        ).pack(side=L)
        ttk.Label(bar, text="   Animations:").pack(side=L)
        self.anim = ttk.Combobox(bar, state="readonly", width=16,
                                 values=[_("Leave as they are"), _("Faster (half)"), _("Off (fastest)"), _("Normal")])
        self.anim.current(0)
        self.anim.pack(side=L, padx=4)
        ttk.Button(bar, text="Apply ticked changes", command=self.apply_cleanup).pack(side=R)

    def _installed(self):
        """package -> {'system': bool, 'enabled': bool, 'present': bool} for every package, incl. removed ones."""
        everything = set(re.findall(r"package:(\S+)", self.adb.shell("pm list packages -u")))
        present = set(re.findall(r"package:(\S+)", self.adb.shell("pm list packages")))
        disabled = set(re.findall(r"package:(\S+)", self.adb.shell("pm list packages -d")))
        user = set(re.findall(r"package:(\S+)", self.adb.shell("pm list packages -3")))
        return {p: {"system": p not in user, "present": p in present, "enabled": p not in disabled}
                for p in everything}

    def _running(self):
        out = self.adb.shell("ps -A -o NAME 2>/dev/null || ps -A")
        names = set()
        for line in out.splitlines():
            parts = line.split()
            if parts:
                names.add(parts[-1].split(":")[0])
        return names

    def scan(self):
        def work():
            self.say(_("Scanning the device (apps, permissions, what starts at boot, what's running) ..."))
            info = analyse.collect(self.adb)
            rows = analyse.classify(info)
            self.ui(lambda: self._fill_tree(rows))
        self.task(work)

    def _fill_tree(self, rows):
        self.tree.delete(*self.tree.get_children())
        self.rows.clear()
        for gid, title, note, auto, opened in analyse.GROUPS:
            members = [r for r in rows if r["group"] == gid]
            if not members:
                continue
            members.sort(key=lambda r: (r["done"], r["name"].lower()))
            ticked = any(r["ticked"] for r in members)
            todo = sum(1 for r in members if not r["done"])
            g = self.tree.insert("", "end", text="",
                                 values=(self._group_cell(ticked, i18n.raw(title), todo), "", "", "", _(note), ""),
                                 open=opened, tags=("group",))
            self.rows[g] = {"is_group": True}
            for r in members:
                tags = ("done",) if r["done"] else (("running",) if r["status"] == "Running" else ())
                item = self.tree.insert(g, "end", text="",
                                        values=(self._boxed(r["ticked"], i18n.raw(r["name"])),
                                                _(r["action"].capitalize()), _("status:" + r["status"]).replace("status:", ""),
                                                _("Preinstalled" if r["system"] else "Yours"),
                                                i18n.reasons(r["reasons"]), r["pkg"]),
                                        tags=tags)
                self.rows[item] = dict(r)
        picked = sum(1 for r in rows if r["ticked"])
        self.say(_("Scan finished: %d apps can be removed or switched off; %d invasive ones are ticked for you.") % (
            sum(1 for r in rows if not r["done"]), picked))

    def _tree_click(self, ev):
        item = self.tree.identify_row(ev.y)
        if not item or item not in self.rows:
            return
        # Which column was clicked, by name (the visible order differs between Arabic and English).
        cid = self.tree.identify_column(ev.x)
        col = None
        if cid != "#0":
            shown = self.tree["displaycolumns"]
            col = shown[int(cid[1:]) - 1]
        row = self.rows[item]
        if row.get("is_group"):
            if col == "name":
                on = self.BLANK in self.tree.set(item, "name")
                self._set_tick(item, on)
                for child in self.tree.get_children(item):
                    if not self.rows[child]["done"]:
                        self._set_tick(child, on)
            return
        if row["done"]:
            return
        if col == "action":
            row["action"] = "uninstall" if row["action"] == "disable" else "disable"
            self.tree.set(item, "action", _(row["action"].capitalize()))
        elif col == "name":
            self._set_tick(item, not row["ticked"])

    def _boxed(self, on, text):
        """Tick box before the text, or after it in Arabic so it sits on the right."""
        box = self.CHECK if on else self.BLANK
        # Table cells ignore direction marks, so in Arabic the box goes after the text, which puts it on the right.
        return "%s  %s" % (text, box) if i18n.RTL else "%s  %s" % (box, text)

    def _group_cell(self, on, title, count):
        """Group heading. Table cells always lay out left to right, so for Arabic it's written in visual order:
        count, title, number, box (read right to left: box, "1.", title, count)."""
        if not i18n.RTL:
            return self._boxed(on, "%s  (%d)" % (title, count))
        box = self.CHECK if on else self.BLANK
        title = re.sub(r"^\d+\.\s*", "", title)   # a leading "1." can't be placed correctly in a table cell
        return "(%d)   %s  %s" % (count, title, box)

    def _set_tick(self, item, on):
        cell = self.tree.set(item, "name")
        cell = cell.replace(self.CHECK, self.BLANK if not on else self.CHECK).replace(
            self.BLANK, self.CHECK if on else self.BLANK)
        self.tree.set(item, "name", cell)
        if not self.rows[item].get("is_group"):
            self.rows[item]["ticked"] = on

    def apply_cleanup(self):
        picked = [r for r in self.rows.values() if not r.get("is_group") and r["ticked"] and not r["done"]]
        anim = self.anim.get()
        if not picked and anim == _("Leave as they are"):
            messagebox.showinfo(APP, "Nothing is ticked.")
            return
        dis = [r for r in picked if r["action"] == "disable"]
        uni = [r for r in picked if r["action"] == "uninstall"]
        mine = [r for r in uni if not r["system"]]
        msg = _("Switch off %d app(s) and remove %d app(s).") % (len(dis), len(uni))
        if mine:
            msg += _("\n\n%d of the removed apps were installed by you; %s") % (
                len(mine), _("they'll be backed up to this PC first.") if self.backup_apks.get()
                else _("NO backup will be kept, so they can only come back from where you got them."))
        if anim != _("Leave as they are"):
            msg += _("\n\nAnimations: ") + anim + "."
        msg += _("\n\nEverything can be undone later on the Undo tab. Continue?")
        if not messagebox.askyesno(APP, msg):
            return
        backup = self.backup_apks.get()

        def work():
            self._save_state()
            n = len(picked)
            for i, r in enumerate(picked, 1):
                self.events.put(("progress", (i - 1, n)))
                self._apply_one(r, backup)
            if anim != _("Leave as they are"):
                self._set_animations({_("Faster (half)"): "0.5", _("Off (fastest)"): "0", _("Normal"): "1"}[anim])
            self.events.put(("progress", (n, max(n, 1))))
            self.say(_("Done. Check the TV: remote, Wi-Fi and inputs should all work as before."))
        self.task(work, done=self.scan)

    def _save_state(self):
        d = self.device_dir("backup-" + datetime.now().strftime("%Y%m%d-%H%M%S"))
        for name, cmd in (("packages.txt", "pm list packages -f -u"), ("disabled.txt", "pm list packages -d"),
                          ("settings-global.txt", "settings list global"), ("settings-secure.txt", "settings list secure"),
                          ("settings-system.txt", "settings list system")):
            with open(os.path.join(d, name), "w", encoding="utf-8") as fh:
                fh.write(self.adb.shell(cmd))
        self.say(_("Saved the device's current state to ") + d)

    def _apply_one(self, r, backup):
        pkg = r["pkg"]
        if r["action"] == "disable":
            out = self.adb.shell("pm disable-user --user 0 " + pkg)
            ok = "disabled" in out.lower()
            self.say("%s %s: %s" % (_("Switched off") if ok else _("Couldn't switch off"), r["name"], out))
            if ok:
                self.record({"type": "disable", "pkg": pkg, "name": r["name"]})
            return
        if r["system"]:
            out = self.adb.shell("pm uninstall --user 0 " + pkg)
            ok = "success" in out.lower()
            self.say("%s %s: %s" % (_("Removed") if ok else _("Couldn't remove"), r["name"], out))
            if ok:
                self.record({"type": "uninstall-system", "pkg": pkg, "name": r["name"]})
            return
        saved = None
        if backup:
            saved = self._backup_apk(pkg)
            if not saved:
                self.say(_("Skipped %s: the backup didn't work, so it was NOT removed.") % r["name"])
                return
        out = self.adb.shell("pm uninstall " + pkg)
        ok = "success" in out.lower()
        self.say("%s %s: %s" % (_("Removed") if ok else _("Couldn't remove"), r["name"], out))
        if ok:
            self.record({"type": "uninstall-user", "pkg": pkg, "name": r["name"], "backup": saved})

    def _backup_apk(self, pkg):
        paths = re.findall(r"package:(\S+)", self.adb.shell("pm path " + pkg))
        if not paths:
            return None
        d = self.device_dir("apk-backup", pkg)
        for p in paths:
            local = os.path.join(d, os.path.basename(p))
            self.adb.run("pull", p, local, timeout=600)
            if not os.path.isfile(local) or os.path.getsize(local) == 0:
                return None
        self.say(_("Backed up %s (%d file(s)) to %s") % (pkg, len(paths), d))
        return d

    def _set_animations(self, value):
        before = {k: self.adb.shell("settings get global " + k).strip() for k in
                  ("window_animation_scale", "transition_animation_scale", "animator_duration_scale")}
        for k in before:
            self.adb.shell("settings put global %s %s" % (k, value))
        self.record({"type": "animations", "pkg": "", "name": _("Animation speed set to ") + value, "before": before})
        self.say(_("Animation speed set to %s.") % value)

    # ---- 3. Home screen ------------------------------------------------------------------------------------
    def _build_home(self):
        f = ttk.Frame(self.tabs, padding=18)
        self.tabs.add(f, text="  3. Home screen  ")
        ttk.Label(f, text="Horizon Home", style="Title.TLabel").pack(anchor=W)
        ttk.Label(f, justify=J, text=(
            "A clean, fast home screen with a built-in IPTV player (Xtream Codes), favourites, Continue Watching,\n"
            "wallpapers and app icons, in 9 languages. Installing it here also:\n"
            "  - makes it the home screen,\n"
            "  - switches the stock home screen off (it comes back with the button below),\n"
            "  - turns on its helper so the remote's Settings/Apps button works.\n"
            "Sign in to your IPTV on the TV itself: Options (sliders button) > Add an IPTV playlist.")
                  ).pack(anchor=W, pady=(6, 14))
        self.home_status = ttk.Label(f, text="", font=("Segoe UI", 10))
        self.home_status.pack(anchor=W, pady=(0, 12))
        row = ttk.Frame(f)
        row.pack(anchor=W)
        ttk.Button(row, text="Install or update Horizon Home", command=self.install_home).pack(side=L)
        ttk.Button(row, text="Go back to the original home screen", command=self.restore_home).pack(side=L, padx=10)
        ttk.Button(row, text="Check", command=self.check_home).pack(side=L)
        self.tabs.bind("<<NotebookTabChanged>>", self._tab_changed)

    def _tab_changed(self, _e):
        index = self.tabs.index(self.tabs.select())    # by position, so it works in every language
        if not self.adb.serial or self.busy:
            return
        if index == 2:
            self.check_home()
        elif index == 3:
            self.load_wallpapers()
        elif index == 4:
            self.load_apps()
        elif index == 5:
            self.load_undo()

    def _current_home(self):
        out = self.adb.shell("cmd package resolve-activity --brief -a android.intent.action.MAIN "
                             "-c android.intent.category.HOME")
        lines = [l.strip() for l in out.splitlines() if "/" in l]
        return lines[-1] if lines else "unknown"

    def check_home(self):
        def work():
            installed = HOME_PKG in self.adb.shell("pm list packages " + HOME_PKG)
            home = self._current_home()
            helper = KEY_SERVICE in self.adb.shell("settings get secure enabled_accessibility_services")
            if i18n.RTL:
                home = "‪" + home + "‬"    # package name: keep its dots and slash left to right
            text = _("Horizon Home: %s\nCurrent home screen: %s\nRemote button helper: %s") % (
                i18n.raw("home:installed" if installed else "home:not installed").replace("home:", ""), home,
                i18n.raw("home:on" if helper else "home:off").replace("home:", ""))
            self.ui(lambda: self.home_status.configure(text=text))
        self.task(work)

    def install_home(self):
        apk = asset("HorizonHome.apk")
        if not os.path.isfile(apk):
            messagebox.showerror(APP, "HorizonHome.apk is missing from this program.")
            return
        if not messagebox.askyesno(APP, "Install Horizon Home, make it the home screen and switch the stock home "
                                        "screen off?\n\nYou can go back at any time with \"Go back to the original "
                                        "home screen\"."):
            return

        def work():
            self.say(_("Installing Horizon Home ..."))
            _rc, out = self.adb.run("install", "-r", apk, timeout=300)
            self.say(out)
            if "Success" not in out:
                self.say(_("Install failed, nothing else was changed."))
                return
            self.adb.shell("cmd package set-home-activity " + HOME_ACTIVITY)
            self.adb.shell("mkdir -p '%s' '%s'" % (WALL_DIR, ICON_DIR))
            self._helper_on()
            pkgs = self._installed()
            for p in STOCK_LAUNCHERS:
                if p in pkgs and pkgs[p]["present"] and pkgs[p]["enabled"]:
                    out = self.adb.shell("pm disable-user --user 0 " + p)
                    self.say(_("Stock home screen %s: %s") % (p, out))
                    if "disabled" in out.lower():
                        self.record({"type": "launcher", "pkg": p, "name": _("Stock home screen (%s)") % p})
            self.record({"type": "home", "pkg": HOME_PKG, "name": _("Horizon Home set as the home screen")})
            self.adb.shell("input keyevent KEYCODE_HOME")
            self.say(_("Horizon Home is now the home screen."))
        self.task(work, done=self.check_home)

    def _helper_on(self):
        current = self.adb.shell("settings get secure enabled_accessibility_services").strip()
        if KEY_SERVICE not in current:
            value = KEY_SERVICE if current in ("", "null") else current + ":" + KEY_SERVICE
            self.adb.shell("settings put secure enabled_accessibility_services " + value)
        self.adb.shell("settings put secure accessibility_enabled 1")

    def restore_home(self):
        if not messagebox.askyesno(APP, "Switch the original home screen back on and turn Horizon Home off?"):
            return

        def work():
            launchers = [c for c in self.changes() if c["type"] == "launcher" and not c["undone"]]
            for c in launchers:
                self.say("%s: %s" % (c["pkg"], self.adb.shell("pm enable " + c["pkg"])))
            items = self.changes()
            for c in items:
                if c["type"] in ("launcher", "home"):
                    c["undone"] = True
            self.save_changes(items)
            self.adb.shell("pm disable-user --user 0 " + HOME_PKG)
            self.adb.shell("input keyevent KEYCODE_HOME")
            self.say(_("Original home screen restored (Horizon Home is switched off; installing it again turns it on)."))
        self.task(work, done=self.check_home)

    # ---- 4. Wallpapers ---------------------------------------------------------------------------------------
    def _build_wallpapers(self):
        f = ttk.Frame(self.tabs, padding=12)
        self.tabs.add(f, text="  4. Wallpapers  ")
        ttk.Label(f, text="Wallpapers", style="Title.TLabel").pack(anchor=W)
        ttk.Label(f, style="Note.TLabel", text=(
            "Pictures are cropped to the screen's shape from the centre and resized, so they load fast. "
            "Pick one on the TV: Options > Wallpaper.")).pack(anchor=W, pady=(4, 8))
        row = ttk.Frame(f)
        row.pack(fill="x")
        ttk.Label(row, text="Size:").pack(side=L)
        self.wall_res = ttk.Combobox(row, state="readonly", width=24, values=[r[0] for r in RESOLUTIONS])
        self.wall_res.current(0)
        self.wall_res.pack(side=L, padx=6)
        ttk.Button(row, text="Add pictures...", command=self.add_wallpapers).pack(side=L, padx=(16, 4))
        ttk.Button(row, text="Rename", command=self.rename_wallpaper).pack(side=L, padx=4)
        ttk.Button(row, text="Remove", command=self.remove_wallpaper).pack(side=L, padx=4)
        body = ttk.Frame(f)
        body.pack(fill="both", expand=True, pady=(10, 0))
        self.walls = tk.Listbox(body, font=("Segoe UI", 10), activestyle="none")
        self.walls.pack(side=L, fill="both", expand=True)
        self.walls.bind("<<ListboxSelect>>", lambda _e: self.preview_wallpaper())
        self.wall_preview = ttk.Label(body)
        self.wall_preview.pack(side=L, padx=12, anchor="n")

    def _wall_size(self):
        chosen = RESOLUTIONS[self.wall_res.current()][1]
        return chosen or self.screen

    def _list_dir(self, path):
        out = self.adb.shell("ls -1 '%s' 2>/dev/null" % path)
        return [l.strip() for l in out.splitlines() if l.strip() and "No such file" not in l]

    def load_wallpapers(self):
        def work():
            names = [n for n in self._list_dir(WALL_DIR) if n.lower().endswith((".jpg", ".jpeg", ".png", ".webp"))]
            def show():
                self.walls.delete(0, "end")
                for n in names:
                    self.walls.insert("end", n)
                self.wall_preview.configure(image="")
            self.ui(show)
        self.task(work)

    def add_wallpapers(self):
        files = filedialog.askopenfilenames(title="Choose pictures", filetypes=[
            ("Pictures", "*.jpg *.jpeg *.png *.webp *.bmp *.heic"), ("All files", "*.*")])
        if not files:
            return
        size = self._wall_size()

        def work():
            self.adb.shell("mkdir -p '%s'" % WALL_DIR)
            tmp = tempfile.mkdtemp()
            for i, src in enumerate(files, 1):
                self.events.put(("progress", (i - 1, len(files))))
                name = os.path.splitext(os.path.basename(src))[0] + ".jpg"
                dst = os.path.join(tmp, name)
                try:
                    got = fit_wallpaper(src, dst, size)
                except Exception as e:
                    self.say(_("Skipped %s: not a picture I can read (%s).") % (os.path.basename(src), e))
                    continue
                _rc, out = self.adb.run("push", dst, "%s/%s" % (WALL_DIR, name), timeout=300)
                self.say(_("Added %s (%dx%d, %d KB).") % (name, got[0], got[1], os.path.getsize(dst) // 1024))
            self.events.put(("progress", (1, 1)))
        self.task(work, done=self.load_wallpapers)

    def _picked_wall(self):
        sel = self.walls.curselection()
        return self.walls.get(sel[0]) if sel else None

    def preview_wallpaper(self):
        name = self._picked_wall()
        if not name or self.busy:
            return

        def work():
            tmp = os.path.join(tempfile.gettempdir(), "horizon-preview.jpg")
            self.adb.run("pull", "%s/%s" % (WALL_DIR, name), tmp, timeout=120)
            def show():
                try:
                    im = Image.open(tmp)
                    im.thumbnail((420, 240))
                    self.thumb = ImageTk.PhotoImage(im)
                    self.wall_preview.configure(image=self.thumb)
                except Exception:
                    pass
            self.ui(show)
        self.task(work)

    def rename_wallpaper(self):
        name = self._picked_wall()
        if not name:
            messagebox.showinfo(APP, "Pick a wallpaper in the list first.")
            return
        base, ext = os.path.splitext(name)
        new = simpledialog.askstring(APP, _("New name for %s:") % name, initialvalue=base, parent=self)
        if not new or new.strip() == base:
            return
        new = re.sub(r"[\\/:*?\"<>|']", "", new.strip()) + ext
        self.task(lambda: self.say(self.adb.shell("mv '%s/%s' '%s/%s'" % (WALL_DIR, name, WALL_DIR, new)) or
                                   _("Renamed to ") + new), done=self.load_wallpapers)

    def remove_wallpaper(self):
        name = self._picked_wall()
        if not name:
            messagebox.showinfo(APP, "Pick a wallpaper in the list first.")
            return
        if messagebox.askyesno(APP, _("Remove %s from the TV?") % name):
            self.task(lambda: (self.adb.shell("rm -f '%s/%s'" % (WALL_DIR, name)), self.say(_("Removed ") + name)),
                      done=self.load_wallpapers)

    # ---- 5. App icons ----------------------------------------------------------------------------------------
    def _build_icons(self):
        f = ttk.Frame(self.tabs, padding=12)
        self.tabs.add(f, text="  5. App icons  ")
        ttk.Label(f, text="App icons on the home row", style="Title.TLabel").pack(anchor=W)
        ttk.Label(f, style="Note.TLabel", text=(
            "Horizon Home already draws simple icons for popular apps. To use your own, pick the app, then a logo "
            "picture (any colours; the background is removed and the logo is tinted to match the wallpaper).\n"
            "The new icon appears the next time you press Home on the remote.")).pack(anchor=W, pady=(4, 8))
        body = ttk.Frame(f)
        body.pack(fill="both", expand=True)
        self.app_list = ttk.Treeview(body, columns=("name", "custom"), show="headings", selectmode="browse")
        self.app_list["displaycolumns"] = ("custom", "name") if i18n.RTL else ("name", "custom")
        self.app_list.heading("name", text="App (package)")
        self.app_list.heading("custom", text="Your icon")
        self.app_list.column("name", width=self.s(420), stretch=True)
        self.app_list.column("custom", width=self.s(90), anchor="center", stretch=False)
        self.app_list.pack(side=L, fill="both", expand=True)
        side = ttk.Frame(body, padding=(14, 0))
        side.pack(side=L, fill="y")
        ttk.Button(side, text="Choose a logo picture...", command=self.set_icon).pack(fill="x")
        ttk.Button(side, text="Remove my icon", command=self.remove_icon).pack(fill="x", pady=6)
        ttk.Label(side, text="Preview:").pack(anchor=W, pady=(16, 4))
        box = tk.Frame(side, bg="#20262f", width=self.s(220), height=self.s(140))
        box.pack_propagate(False)      # fixed pixel size, whatever is (or isn't) shown inside
        box.pack()
        self.icon_preview = tk.Label(box, bg="#20262f")
        self.icon_preview.pack(fill="both", expand=True)
        self.icon_img = None

    def load_apps(self):
        def work():
            pkgs = set()
            for cat in ("android.intent.category.LEANBACK_LAUNCHER", "android.intent.category.LAUNCHER"):
                out = self.adb.shell("cmd package query-activities -a android.intent.action.MAIN -c " + cat)
                pkgs.update(re.findall(r"packageName=(\S+)", out))
            pkgs.discard(HOME_PKG)
            custom = {n[:-4] for n in self._list_dir(ICON_DIR) if n.endswith(".png")}
            def show():
                self.app_list.delete(*self.app_list.get_children())
                for p in sorted(pkgs, key=lambda q: NAMES.get(q, q).lower()):
                    label = "%s   (%s)" % (NAMES[p], p) if p in NAMES else p
                    self.app_list.insert("", "end", iid=p, values=(label, _("yes") if p in custom else ""))
            self.ui(show)
        self.task(work)

    def _picked_app(self):
        sel = self.app_list.selection()
        if not sel:
            messagebox.showinfo(APP, "Pick an app in the list first.")
            return None
        return sel[0]

    def set_icon(self):
        pkg = self._picked_app()
        if not pkg:
            return
        src = filedialog.askopenfilename(title=_("Choose a logo for ") + pkg, filetypes=[
            ("Pictures", "*.png *.jpg *.jpeg *.webp *.bmp"), ("All files", "*.*")])
        if not src:
            return
        dst = os.path.join(self.device_dir("icons"), pkg + ".png")
        try:
            icon = make_icon(src, dst)
        except Exception as e:
            messagebox.showerror(APP, str(e))
            return
        # Preview tinted like on the TV, on a dark background.
        tinted = Image.new("RGBA", icon.size, (232, 217, 192, 255))
        tinted.putalpha(icon.getchannel("A"))
        tinted.thumbnail((200, 120))
        bg = Image.new("RGBA", (220, 140), (32, 38, 47, 255))
        bg.alpha_composite(tinted, ((220 - tinted.width) // 2, (140 - tinted.height) // 2))
        self.icon_img = ImageTk.PhotoImage(bg)
        self.icon_preview.configure(image=self.icon_img)

        def work():
            self.adb.shell("mkdir -p '%s'; rm -f '%s/%s.png'" % (ICON_DIR, ICON_DIR, pkg))
            _rc, out = self.adb.run("push", dst, "%s/%s.png" % (ICON_DIR, pkg), timeout=120)
            self.say(_("Icon for %s sent to the TV. Press Home on the remote to see it.") % pkg)
        self.task(work, done=self.load_apps)

    def remove_icon(self):
        pkg = self._picked_app()
        if pkg:
            self.task(lambda: (self.adb.shell("rm -f '%s/%s.png'; touch '%s'" % (ICON_DIR, pkg, ICON_DIR)),
                               self.say(_("Removed your icon for %s; the built-in one is back.") % pkg)),
                      done=self.load_apps)

    # ---- 6. Undo ---------------------------------------------------------------------------------------------
    def _build_undo(self):
        f = ttk.Frame(self.tabs, padding=12)
        self.tabs.add(f, text="  6. Undo  ")
        ttk.Label(f, text="Changes made with this program on this device", style="Title.TLabel").pack(anchor=W)
        ttk.Label(f, style="Note.TLabel", text="Select one or more (Ctrl/Shift-click) and press Undo selected."
                  ).pack(anchor=W, pady=(4, 8))
        self.undo_list = ttk.Treeview(f, columns=("when", "what", "state"), show="headings", selectmode="extended")
        self.undo_list["displaycolumns"] = ("state", "what", "when") if i18n.RTL else ("when", "what", "state")
        for c, t, w in (("when", "When", 140), ("what", "Change", 560), ("state", "State", 120)):
            self.undo_list.heading(c, text=t)
            self.undo_list.column(c, width=self.s(w))
        self.undo_list.pack(fill="both", expand=True)
        row = ttk.Frame(f)
        row.pack(fill="x", pady=(8, 0))
        ttk.Button(row, text="Undo selected", command=self.undo_selected).pack(side=R)
        ttk.Button(row, text="Open backups folder", command=lambda: os.startfile(self.device_dir())).pack(side=L)

    def _describe(self, c):
        kind = {"disable": _("Switched off"), "uninstall-system": _("Removed (preinstalled)"),
                "uninstall-user": _("Removed (yours%s)") % (_(", backed up") if c.get("backup") else _(", no backup")),
                "launcher": _("Switched off"), "home": "", "animations": ""}.get(c["type"], c["type"])
        return ("%s: %s" % (kind, c["name"])).lstrip(": ")

    def load_undo(self):
        items = self.changes()
        self.undo_list.delete(*self.undo_list.get_children())
        for i, c in enumerate(items):
            if c["type"] == "home":
                continue
            self.undo_list.insert("", "end", iid=str(i), values=(c["time"], self._describe(c),
                                                                 _("state:undone" if c["undone"] else "state:active").replace("state:", "")))

    def undo_selected(self):
        sel = [int(i) for i in self.undo_list.selection()]
        if not sel:
            messagebox.showinfo(APP, "Select something to undo first.")
            return
        if not messagebox.askyesno(APP, _("Undo %d change(s)?") % len(sel)):
            return

        def work():
            items = self.changes()
            for i in sel:
                c = items[i]
                if c["undone"]:
                    continue
                t, pkg = c["type"], c["pkg"]
                if t in ("disable", "launcher"):
                    out = self.adb.shell("pm enable " + pkg)
                    ok = "enabled" in out.lower()
                elif t == "uninstall-system":
                    out = self.adb.shell("cmd package install-existing " + pkg)
                    ok = "installed" in out.lower()
                elif t == "uninstall-user":
                    d = c.get("backup")
                    apks = [os.path.join(d, n) for n in os.listdir(d)] if d and os.path.isdir(d) else []
                    if not apks:
                        self.say(_("No backup for %s: reinstall it from where you got it.") % c["name"])
                        continue
                    _rc, out = self.adb.run(*(["install-multiple", "-r"] + apks if len(apks) > 1 else
                                            ["install", "-r"] + apks), timeout=600)
                    ok = "Success" in out
                elif t == "animations":
                    for k, v in c.get("before", {}).items():
                        self.adb.shell("settings delete global %s" % k if v in ("", "null")
                                       else "settings put global %s %s" % (k, v))
                    out, ok = "restored", True
                else:
                    continue
                self.say("%s %s: %s" % (_("Undid") if ok else _("Couldn't undo"), c["name"], out))
                if ok:
                    c["undone"] = True
            self.save_changes(items)
        self.task(work, done=self.load_undo)


def selftest(report):
    """Checks the packaged app has everything it needs (run by build_exe.ps1 after each build)."""
    lines = []
    tmp = tempfile.mkdtemp()
    try:
        jpg, png = os.path.join(tmp, "photo.jpg"), os.path.join(tmp, "logo.png")
        img = Image.new("RGB", (3000, 2000), (40, 90, 160))
        img.save(jpg, "JPEG", quality=90)
        logo = Image.new("RGBA", (400, 200), (255, 255, 255, 255))
        logo.paste((200, 0, 0, 255), (100, 50, 300, 150))
        logo.save(png)
        lines.append("wallpaper jpg %s" % (fit_wallpaper(jpg, os.path.join(tmp, "w.jpg"), (1920, 1080)),))
        lines.append("wallpaper png %s" % (fit_wallpaper(png, os.path.join(tmp, "w2.jpg"), (3840, 2160)),))
        lines.append("icon from png %s" % (make_icon(png, os.path.join(tmp, "i.png")).size,))
        img2 = Image.new("RGB", (800, 600), (250, 250, 250))
        img2.paste((10, 10, 10), (200, 200, 600, 400))
        img2.save(jpg, "JPEG", quality=90)
        lines.append("icon from jpg %s" % (make_icon(jpg, os.path.join(tmp, "i3.png")).size,))
        lines.append("webp %s" % (Image.open(io_webp()).size,))
        lines.append("analyse groups %d" % len(analyse.GROUPS))
        lines.append("arabic %s" % (i18n.AR.get("Connect") is not None))
        lines.append("adb %s" % Adb().exe)
        lines.append("OK")
    except Exception as e:
        import traceback
        lines.append("FAILED: %r" % e)
        lines.append(traceback.format_exc())
    with open(report, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


def io_webp():
    import io
    b = io.BytesIO()
    Image.new("RGB", (64, 32), (1, 2, 3)).save(b, "WEBP")
    b.seek(0)
    return b


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "--selftest":
        selftest(sys.argv[2])
        sys.exit(0)
    if os.name == "nt":
        try:  # sharp text on high-resolution screens
            import ctypes
            ctypes.windll.shcore.SetProcessDpiAwareness(1)
        except Exception:
            pass
    os.makedirs(DATA, exist_ok=True)
    App().mainloop()
