"""
Languages for Horizon TV Setup: Arabic (default, right to left) and English.
_() translates; static texts on labels, buttons, tabs, headings and dialogs are translated automatically by
install(), formatted sentences are wrapped with _() in the code.
"""
import json
import os

APP_EN = "Horizon TV Setup"
SETTINGS = os.path.join(os.path.expanduser("~"), "Documents", APP_EN, "settings.json")


def _load():
    try:
        with open(SETTINGS, encoding="utf-8") as f:
            return json.load(f).get("lang", "ar")
    except (OSError, ValueError):
        return "ar"


LANG = _load()
RTL = LANG == "ar"
# Layout helpers: "start"/"end" sides and anchors that flip for right-to-left.
L, R = ("right", "left") if RTL else ("left", "right")
W = "e" if RTL else "w"
J = "right" if RTL else "left"


def save_lang(lang):
    os.makedirs(os.path.dirname(SETTINGS), exist_ok=True)
    data = {}
    try:
        with open(SETTINGS, encoding="utf-8") as f:
            data = json.load(f)
    except (OSError, ValueError):
        pass
    data["lang"] = lang
    with open(SETTINGS, "w", encoding="utf-8") as f:
        json.dump(data, f)


def rtl_lines(text):
    """
    Mark each line as right-to-left (invisible RLE ... PDF marks). Tk lays text out left to right, so without
    this, numbers and punctuation at the start or end of an Arabic line ("1.", ":", ".", brackets) land on the
    wrong side. English parts inside (IP addresses, app names) still read left to right.
    """
    return "\n".join("‫" + line + "‬" if line.strip() else line for line in text.split("\n"))


def raw(text):
    """Translation without direction marks, for building a longer line that's marked once as a whole."""
    return AR.get(text, text) if LANG == "ar" and isinstance(text, str) else text


def _(text):
    if LANG != "ar" or not isinstance(text, str):
        return text
    if text not in AR:
        return text
    return rtl_lines(AR[text])


def reasons(text):
    """Translate the 'why it's listed' column, which is several short reasons joined with '; '."""
    if LANG != "ar" or not text:
        return text
    out = []
    for part in text.split("; "):
        if part.startswith("name: "):
            out.append(AR["name: "] + part[6:])
        else:
            out.append(AR.get(part, part))
    return rtl_lines("؛ ".join(out))


def install():
    """Translate static texts given to widgets and dialogs, so they don't each need wrapping."""
    from tkinter import ttk, messagebox, filedialog, simpledialog

    def wrap_init(cls):
        orig = cls.__init__

        def init(self, *a, **k):
            if isinstance(k.get("text"), str):
                k["text"] = _(k["text"])
            if RTL and cls is ttk.Label:
                k.setdefault("anchor", "e")
                k["justify"] = "right"    # every line of a multi-line note lines up on the right
            orig(self, *a, **k)
        cls.__init__ = init

    for cls in (ttk.Label, ttk.Button, ttk.Checkbutton):
        wrap_init(cls)

    nb_add = ttk.Notebook.add

    def add(self, child, **k):
        if "text" in k:
            k["text"] = _(k["text"])
        return nb_add(self, child, **k)
    ttk.Notebook.add = add

    heading = ttk.Treeview.heading

    def head(self, column, option=None, **k):
        if "text" in k:
            k["text"] = _(k["text"])
            if RTL:
                k.setdefault("anchor", "e")
        return heading(self, column, option, **k)
    ttk.Treeview.heading = head

    column = ttk.Treeview.column

    def col(self, cid, option=None, **k):
        if RTL and "width" in k and "anchor" not in k:
            k["anchor"] = "e"
        return column(self, cid, option, **k)
    ttk.Treeview.column = col

    for name in ("showinfo", "showerror", "showwarning", "askyesno", "askokcancel"):
        fn = getattr(messagebox, name)

        def make(fn):
            def wrapped(title=None, message=None, **k):
                return fn(_(title), _(message), **k)
            return wrapped
        setattr(messagebox, name, make(fn))

    for name in ("askopenfilename", "askopenfilenames"):
        fn = getattr(filedialog, name)

        def make2(fn):
            def wrapped(**k):
                if "title" in k:
                    k["title"] = _(k["title"])
                if "filetypes" in k:
                    k["filetypes"] = [(_(a), b) for a, b in k["filetypes"]]
                return fn(**k)
            return wrapped
        setattr(filedialog, name, make2(fn))

    ask = simpledialog.askstring

    def askstring(title, prompt, **k):
        return ask(_(title), _(prompt), **k)
    simpledialog.askstring = askstring


AR = {
    # ---- window, tabs ----
    "Horizon TV Setup": "إعداد Horizon TV",
    "  1. Connect  ": "  1. الاتصال  ",
    "  2. Clean up  ": "  2. التنظيف  ",
    "  3. Home screen  ": "  3. الشاشة الرئيسية  ",
    "  4. Wallpapers  ": "  4. الخلفيات  ",
    "  5. App icons  ": "  5. أيقونات التطبيقات  ",
    "  6. Undo  ": "  6. التراجع  ",
    "Language:": "اللغة:",
    "Please wait for the current step to finish.": "يرجى الانتظار حتى تنتهي الخطوة الحالية.",
    "Error: %s": "خطأ: %s",
    "timed out": "انتهت المهلة",
    "adb not found": "لم يتم العثور على adb",
    "The language changes when the program restarts. Restart now?":
        "تتغير اللغة عند إعادة تشغيل البرنامج. هل تريد إعادة التشغيل الآن؟",

    # ---- 1. Connect ----
    "Connect to your TV or stick": "الاتصال بالتلفزيون أو الجهاز",
    "On the TV, one time only:\n"
    "  1. Settings > Device Preferences > About: press  Build  7 times (\"You are now a developer\").\n"
    "  2. Settings > Device Preferences > Developer options: turn on  USB debugging  "
    "(and  Network debugging  if it's listed).\n"
    "  3. Find the TV's IP address in Settings > Network & Internet > your Wi-Fi.\n"
    "Then type the IP address below and press Connect. Accept the prompt that appears on the TV\n"
    "(tick \"Always allow from this computer\").":
        "على التلفزيون، مرة واحدة فقط:\n"
        "  1. الإعدادات > تفضيلات الجهاز > حول: اضغط على  رقم الإصدار (Build)  7 مرات (\"أصبحت الآن مطورًا\").\n"
        "  2. الإعدادات > تفضيلات الجهاز > خيارات المطور: فعّل  تصحيح أخطاء USB  "
        "(و تصحيح الأخطاء عبر الشبكة  إن وُجد).\n"
        "  3. اعثر على عنوان IP للتلفزيون في الإعدادات > الشبكة والإنترنت > شبكة Wi-Fi.\n"
        "ثم اكتب عنوان IP بالأسفل واضغط اتصال. وافق على الرسالة التي تظهر على التلفزيون\n"
        "(حدّد \"السماح دائمًا من هذا الكمبيوتر\").",
    "TV's IP address:": "عنوان IP للتلفزيون:",
    "Connect": "اتصال",
    "Already connected (Wi-Fi or USB):": "متصل بالفعل (Wi-Fi أو USB):",
    "Use this one": "استخدم هذا",
    "Refresh": "تحديث",
    "Not connected.": "غير متصل.",
    "Type the TV's IP address first, for example 192.168.1.50.": "اكتب عنوان IP للتلفزيون أولًا، مثل 192.168.1.50.",
    "Connecting to %s ...": "جارٍ الاتصال بـ %s ...",
    "The TV is asking for permission. Accept the prompt on the TV screen (tick \"Always allow\"), then press Connect again.":
        "التلفزيون يطلب الإذن. وافق على الرسالة الظاهرة على شاشة التلفزيون (حدّد \"السماح دائمًا\")، ثم اضغط اتصال مرة أخرى.",
    "Couldn't reach %s.\n\nCheck that the TV is on, on the same Wi-Fi as this PC, and that USB/Network debugging is turned on.":
        "تعذّر الوصول إلى %s.\n\nتأكد أن التلفزيون يعمل، وأنه على نفس شبكة Wi-Fi مع هذا الكمبيوتر، وأن تصحيح أخطاء USB/الشبكة مفعّل.",
    "Connected:  %s %s  -  Android %s  -  screen %dx%d\nChanges and backups for this device are kept in:  %s":
        "متصل:  %s %s  -  أندرويد %s  -  الشاشة %dx%d\nتُحفظ التغييرات والنسخ الاحتياطية لهذا الجهاز في:  %s",
    "Connected to %s %s (Android %s).": "تم الاتصال بـ %s %s (أندرويد %s).",

    # ---- 2. Clean up ----
    "Choose what to switch off or remove": "اختر ما تريد إيقافه أو إزالته",
    "Scan again": "فحص مرة أخرى",
    "The TV was scanned automatically: groups 1 and 2 (tracking, ads) are ticked for you; tick anything else "
    "you want gone. Click the Action cell to switch between Disable and Uninstall.\nEssentials (remote, "
    "keyboard, Play Store, Google services, Wi-Fi, TV inputs, home screen) are detected and never listed.":
        "تم فحص التلفزيون تلقائيًا: المجموعتان 1 و2 (التتبع والإعلانات) محددتان لك؛ حدّد أي شيء آخر تريد إزالته.\n"
        "اضغط على خانة الإجراء للتبديل بين تعطيل وإلغاء التثبيت.\n"
        "الأساسيات لا تُعرض أبدًا، إذ تُكتشف تلقائيًا:\n"
        "الريموت، لوحة المفاتيح، متجر Play، خدمات Google، الواي فاي، مداخل التلفزيون، الشاشة الرئيسية.",
    "App": "التطبيق",
    "Action": "الإجراء",
    "Status now": "الحالة الآن",
    "Type": "النوع",
    "Why it's listed": "سبب إدراجه",
    "Package": "الحزمة",
    "Back up apps I installed before uninstalling them": "انسخ احتياطيًا التطبيقات التي ثبّتُّها قبل إزالتها",
    "   Animations:": "   الحركات:",
    "Leave as they are": "اتركها كما هي",
    "Faster (half)": "أسرع (النصف)",
    "Off (fastest)": "إيقاف (الأسرع)",
    "Normal": "عادية",
    "Apply ticked changes": "طبّق التغييرات المحددة",
    "Disable": "تعطيل",
    "Uninstall": "إلغاء التثبيت",
    "Preinstalled": "مثبّت مسبقًا",
    "Yours": "خاص بك",
    "Scanning the device (apps, permissions, what starts at boot, what's running) ...":
        "جارٍ فحص الجهاز (التطبيقات، الأذونات، ما يبدأ مع التشغيل، ما يعمل الآن) ...",
    "Scan finished: %d apps can be removed or switched off; %d invasive ones are ticked for you.":
        "اكتمل الفحص: %d تطبيقًا يمكن إزالته أو إيقافه؛ تم تحديد %d من التطبيقات المتطفلة لك.",
    "Nothing is ticked.": "لم يتم تحديد أي شيء.",
    "Switch off %d app(s) and remove %d app(s).": "إيقاف %d تطبيق وإزالة %d تطبيق.",
    "\n\n%d of the removed apps were installed by you; %s": "\n\n%d من التطبيقات المحذوفة ثبّتّها أنت؛ %s",
    "they'll be backed up to this PC first.": "سيتم نسخها احتياطيًا إلى هذا الكمبيوتر أولًا.",
    "NO backup will be kept, so they can only come back from where you got them.":
        "لن يتم الاحتفاظ بنسخة احتياطية، ولن تعود إلا من مصدرها الأصلي.",
    "\n\nAnimations: ": "\n\nالحركات: ",
    "\n\nEverything can be undone later on the Undo tab. Continue?":
        "\n\nيمكن التراجع عن كل شيء لاحقًا من تبويب التراجع. هل تريد المتابعة؟",
    "Done. Check the TV: remote, Wi-Fi and inputs should all work as before.":
        "تم. تحقق من التلفزيون: يجب أن يعمل الريموت وWi-Fi والمداخل كما كانت.",
    "Saved the device's current state to ": "تم حفظ حالة الجهاز الحالية في ",
    "Switched off": "تم إيقاف",
    "Couldn't switch off": "تعذّر إيقاف",
    "Removed": "تمت إزالة",
    "Couldn't remove": "تعذّرت إزالة",
    "Skipped %s: the backup didn't work, so it was NOT removed.": "تم تخطي %s: فشل النسخ الاحتياطي، لذلك لم تتم إزالته.",
    "Backed up %s (%d file(s)) to %s": "تم نسخ %s احتياطيًا (%d ملف) إلى %s",
    "Animation speed set to %s.": "تم ضبط سرعة الحركات على %s.",
    "Animation speed set to ": "تم ضبط سرعة الحركات على ",

    # status values (shown in the list)
    "status:Running": "يعمل",
    "status:Installed": "مثبّت",
    "status:Disabled": "معطّل",
    "status:Removed": "محذوف",

    # ---- groups and reasons (from analyse.py) ----
    "1. Tracking, analytics and ads": "1. التتبع والتحليلات والإعلانات",
    "Report what you watch or how you use the TV, show ads, or quietly install promoted apps. Ticked for you.":
        "تُبلغ عمّا تشاهده أو كيف تستخدم التلفزيون، أو تعرض إعلانات، أو تثبّت تطبيقات مروّجة بصمت. محددة لك.",
    "2. Ad and recommendation rows": "2. صفوف الإعلانات والتوصيات",
    "Sponsored rows, banners and recommendations on the home screen. Ticked for you.":
        "صفوف ممولة ولافتات وتوصيات على الشاشة الرئيسية. محددة لك.",
    "Voice assistant": "المساعد الصوتي",
    "Remove only if you never use the microphone button.": "أزلها فقط إذا لم تستخدم زر الميكروفون أبدًا.",
    "Accessibility": "إمكانية الوصول",
    "Keep if anyone in the house uses a screen reader.": "أبقها إذا كان أحد في المنزل يستخدم قارئ الشاشة.",
    "Casting and screen mirroring": "البث وعكس الشاشة",
    "Lets phones cast or mirror to the TV.": "تتيح للهواتف البث أو عكس الشاشة على التلفزيون.",
    "System updates": "تحديثات النظام",
    "Turning these off also stops security updates.": "إيقافها يوقف أيضًا التحديثات الأمنية.",
    "Apps with an icon": "تطبيقات لها أيقونة",
    "Apps you can open from the home screen.": "تطبيقات يمكنك فتحها من الشاشة الرئيسية.",
    "Other background services": "خدمات خلفية أخرى",
    "Run without an icon. Most are harmless; only remove what you recognise.":
        "تعمل بدون أيقونة. معظمها غير ضار؛ أزل فقط ما تعرفه.",
    "can capture what's on screen": "يمكنه التقاط ما على الشاشة",
    "can record the TV's sound": "يمكنه تسجيل صوت التلفزيون",
    "reads which apps you use": "يقرأ التطبيقات التي تستخدمها",
    "uses the advertising ID": "يستخدم المعرّف الإعلاني",
    "can install apps silently": "يمكنه تثبيت تطبيقات بصمت",
    "can delete apps": "يمكنه حذف تطبيقات",
    "starts with the TV, uses the internet": "يبدأ مع التلفزيون ويستخدم الإنترنت",
    "captures the screen in the background (ACR)": "يلتقط الشاشة في الخلفية (ACR)",
    "auto-installer": "مثبّت تلقائي",
    "known": "معروف",
    "name: ": "الاسم: ",

    # known app descriptions
    "Alphonso ad tracking (identifies what's on screen)": "تتبع إعلانات Alphonso (يتعرف على ما يُعرض)",
    "Xiaomi usage analytics": "تحليلات استخدام Xiaomi",
    "Google crash report sender": "مرسل تقارير الأعطال من Google",
    "Google TV bug report sender": "مرسل تقارير أخطاء Google TV",
    "Google partner setup and usage pings": "إعداد شركاء Google وإرسال بيانات الاستخدام",
    "Samsung TV Plus promotions": "عروض Samsung TV Plus",
    "Recommendation rows": "صفوف التوصيات",
    "Xiaomi content channel row": "صف قنوات محتوى Xiaomi",
    "Xiaomi PatchWall banners": "لافتات Xiaomi PatchWall",
    "Xiaomi web content viewer": "عارض محتوى الويب من Xiaomi",
    "CVTE service that can install and delete apps": "خدمة CVTE التي يمكنها تثبيت التطبيقات وحذفها",
    "Google Assistant voice search": "البحث الصوتي في مساعد Google",
    "Assistant offline voice engine": "محرك الصوت دون اتصال للمساعد",
    "Google voice search": "البحث الصوتي من Google",
    "TalkBack screen reader": "قارئ الشاشة TalkBack",
    "Chromecast built-in (casting from phones)": "Chromecast المدمج (البث من الهواتف)",
    "EShare screen mirroring": "عكس الشاشة EShare",
    "AirPlay receiver for iPhone and Mac": "مستقبل AirPlay لأجهزة iPhone وMac",
    "Xiaomi system update service": "خدمة تحديث النظام من Xiaomi",
    "Live Channels": "القنوات المباشرة",
    "Xiaomi screensaver": "شاشة التوقف من Xiaomi",

    # ---- 3. Home screen ----
    "A clean, fast home screen with a built-in IPTV player (Xtream Codes), favourites, Continue Watching,\n"
    "wallpapers and app icons, in 9 languages. Installing it here also:\n"
    "  - makes it the home screen,\n"
    "  - switches the stock home screen off (it comes back with the button below),\n"
    "  - turns on its helper so the remote's Settings/Apps button works.\n"
    "Sign in to your IPTV on the TV itself: Options (sliders button) > Add an IPTV playlist.":
        "شاشة رئيسية نظيفة وسريعة مع مشغّل IPTV مدمج (Xtream Codes)، والمفضلة، ومتابعة المشاهدة،\n"
        "والخلفيات وأيقونات التطبيقات، بـ 9 لغات. التثبيت من هنا أيضًا:\n"
        "  - يجعلها الشاشة الرئيسية،\n"
        "  - يوقف الشاشة الرئيسية الأصلية (تعود بالزر أدناه)،\n"
        "  - يفعّل المساعد الذي يجعل زر الإعدادات/التطبيقات في الريموت يعمل.\n"
        "سجّل الدخول إلى IPTV من التلفزيون نفسه: الخيارات (زر أشرطة التمرير) > إضافة قائمة IPTV.",
    "Install or update Horizon Home": "تثبيت Horizon Home أو تحديثه",
    "Go back to the original home screen": "العودة إلى الشاشة الرئيسية الأصلية",
    "Check": "فحص",
    "Horizon Home: %s\nCurrent home screen: %s\nRemote button helper: %s":
        "حالة Horizon Home: %s\nالشاشة الرئيسية الحالية: %s\nمساعد زر الريموت: %s",
    "home:installed": "مثبّتة",
    "home:not installed": "غير مثبّتة",
    "home:on": "مفعّل",
    "home:off": "متوقف",
    "HorizonHome.apk is missing from this program.": "ملف HorizonHome.apk مفقود من هذا البرنامج.",
    "Install Horizon Home, make it the home screen and switch the stock home screen off?\n\nYou can go back at "
    "any time with \"Go back to the original home screen\".":
        "تثبيت Horizon Home وجعلها الشاشة الرئيسية وإيقاف الشاشة الرئيسية الأصلية؟\n\n"
        "يمكنك العودة في أي وقت بزر \"العودة إلى الشاشة الرئيسية الأصلية\".",
    "Installing Horizon Home ...": "جارٍ تثبيت Horizon Home ...",
    "Install failed, nothing else was changed.": "فشل التثبيت، ولم يتم تغيير أي شيء آخر.",
    "Stock home screen %s: %s": "الشاشة الرئيسية الأصلية %s: %s",
    "Stock home screen (%s)": "الشاشة الرئيسية الأصلية (%s)",
    "Horizon Home set as the home screen": "تعيين Horizon Home كشاشة رئيسية",
    "Horizon Home is now the home screen.": "أصبحت Horizon Home الشاشة الرئيسية الآن.",
    "Switch the original home screen back on and turn Horizon Home off?":
        "إعادة تشغيل الشاشة الرئيسية الأصلية وإيقاف Horizon Home؟",
    "Original home screen restored (Horizon Home is switched off; installing it again turns it on).":
        "تمت استعادة الشاشة الرئيسية الأصلية (تم إيقاف Horizon Home؛ تثبيتها مجددًا يعيد تشغيلها).",

    # ---- 4. Wallpapers ----
    "Wallpapers": "الخلفيات",
    "Pictures are cropped to the screen's shape from the centre and resized, so they load fast. "
    "Pick one on the TV: Options > Wallpaper.":
        "تُقص الصور على شكل الشاشة من المنتصف ويُعاد تحجيمها لتُحمّل بسرعة. اختر واحدة على التلفزيون: الخيارات > الخلفية.",
    "Size:": "الحجم:",
    "Auto (from the device)": "تلقائي (من الجهاز)",
    "Add pictures...": "إضافة صور...",
    "Rename": "إعادة تسمية",
    "Remove": "إزالة",
    "Choose pictures": "اختر الصور",
    "Pictures": "الصور",
    "All files": "كل الملفات",
    "Skipped %s: not a picture I can read (%s).": "تم تخطي %s: ليست صورة يمكن قراءتها (%s).",
    "Added %s (%dx%d, %d KB).": "تمت إضافة %s (%dx%d، %d كيلوبايت).",
    "Pick a wallpaper in the list first.": "اختر خلفية من القائمة أولًا.",
    "New name for %s:": "الاسم الجديد لـ %s:",
    "Renamed to ": "أُعيدت التسمية إلى ",
    "Remove %s from the TV?": "إزالة %s من التلفزيون؟",
    "Removed ": "تمت إزالة ",

    # ---- 5. App icons ----
    "App icons on the home row": "أيقونات التطبيقات في الصف الرئيسي",
    "Horizon Home already draws simple icons for popular apps. To use your own, pick the app, then a logo "
    "picture (any colours; the background is removed and the logo is tinted to match the wallpaper).\n"
    "The new icon appears the next time you press Home on the remote.":
        "ترسم Horizon Home بالفعل أيقونات بسيطة للتطبيقات الشائعة. لاستخدام أيقونتك، اختر التطبيق ثم صورة الشعار\n"
        "(بأي ألوان؛ تُزال الخلفية ويُلوَّن الشعار ليناسب الخلفية).\n"
        "تظهر الأيقونة الجديدة عند الضغط على زر الصفحة الرئيسية في الريموت.",
    "App (package)": "التطبيق (الحزمة)",
    "Your icon": "أيقونتك",
    "Choose a logo picture...": "اختر صورة الشعار...",
    "Remove my icon": "إزالة أيقونتي",
    "Preview:": "معاينة:",
    "yes": "نعم",
    "Pick an app in the list first.": "اختر تطبيقًا من القائمة أولًا.",
    "Choose a logo for ": "اختر شعارًا لـ ",
    "Icon for %s sent to the TV. Press Home on the remote to see it.":
        "تم إرسال أيقونة %s إلى التلفزيون. اضغط زر الصفحة الرئيسية في الريموت لرؤيتها.",
    "Removed your icon for %s; the built-in one is back.": "تمت إزالة أيقونتك لـ %s؛ عادت الأيقونة المدمجة.",
    "Couldn't find a logo in that picture (it looks like one flat colour).":
        "تعذّر العثور على شعار في هذه الصورة (تبدو بلون واحد).",

    # ---- 6. Undo ----
    "Changes made with this program on this device": "التغييرات التي أُجريت بهذا البرنامج على هذا الجهاز",
    "Select one or more (Ctrl/Shift-click) and press Undo selected.":
        "حدّد عنصرًا أو أكثر (Ctrl/Shift مع النقر) ثم اضغط تراجع عن المحدد.",
    "When": "الوقت",
    "Change": "التغيير",
    "State": "الحالة",
    "state:undone": "تم التراجع",
    "state:active": "سارٍ",
    "Undo selected": "تراجع عن المحدد",
    "Open backups folder": "فتح مجلد النسخ الاحتياطية",
    "Removed (preinstalled)": "محذوف (مثبّت مسبقًا)",
    "Removed (yours%s)": "محذوف (خاص بك%s)",
    ", backed up": "، مع نسخة احتياطية",
    ", no backup": "، بدون نسخة احتياطية",
    "Select something to undo first.": "حدّد شيئًا للتراجع عنه أولًا.",
    "Undo %d change(s)?": "التراجع عن %d تغيير؟",
    "No backup for %s: reinstall it from where you got it.": "لا توجد نسخة احتياطية لـ %s: أعد تثبيته من مصدره.",
    "Undid": "تم التراجع عن",
    "Couldn't undo": "تعذّر التراجع عن",
}
