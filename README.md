# Horizon TV

[العربية](#العربية) · [English](#english)

<div dir="rtl">

## العربية

شاشة رئيسية نظيفة وسريعة لأجهزة Android TV مع مشغّل IPTV مدمج، وبرنامج لنظام Windows يثبّتها ويزيل التتبع والتطبيقات غير الضرورية من التلفزيون، دون كتابة أي أوامر.

| | |
|---|---|
| **Horizon Home** (`horizon-home/`) | شاشة رئيسية لأجهزة Android TV: مشغّل IPTV (‏Xtream Codes وروابط M3U)، والمفضلة، ومتابعة المشاهدة، والخلفيات وأيقونات التطبيقات، بـ 9 لغات. |
| **Horizon TV Setup** (`setup-app/`) | برنامج لنظام Windows يتصل بالتلفزيون عبر Wi-Fi، ويكتشف الخدمات المتطفلة تلقائيًا ويزيلها، ويثبّت Horizon Home، ويدير الخلفيات والأيقونات. يمكن التراجع عن كل تغيير. |

### التنزيل
حمّل الملفات الجاهزة من مجلد [`release/`](release):
- **`Horizon TV Setup.exe`:** شغّله على أي كمبيوتر بنظام Windows. لا يحتاج إلى تثبيت، وكل ما يلزمه (adb وHorizon Home) موجود بداخله.
- **`HorizonHome.apk`:** الشاشة الرئيسية وحدها، إذا أردت تثبيتها بنفسك (`adb install HorizonHome.apk`).

### Horizon Home
- **الشاشة الرئيسية**
  - ساعة مع حالة الطقس المحلي وأيقونتها (صافٍ، غائم، ممطر...). تُحدَّد المدينة تلقائيًا من المنطقة الزمنية للتلفزيون، ويمكنك اختيار مدينة أخرى أو إيقاف الطقس من الخيارات ← مدينة الطقس.
  - اختصارات إلى البث المباشر والأفلام والمسلسلات وتطبيقاتك المفضلة.
  - متابعة المشاهدة، مع زر لإزالة أي عنصر.
- **IPTV**
  - **Xtream Codes** (الخادم واسم المستخدم وكلمة المرور) أو **رابط M3U**. يمكنك إضافة أكثر من قائمة والتبديل بينها، ولكل قائمة سجلها ومفضلتها.
  - **البث المباشر:** قائمة قنوات مع نافذة معاينة. اضغط موافق مرة للمعاينة، ومرة ثانية لملء الشاشة.
  - **الأفلام والمسلسلات:** الملصقات، والاستئناف، و"تخطي المقدمة" (تُحفظ مدتها لكل مسلسل)، والترتيب (أ إلى ي، المضاف حديثًا، الأعلى تقييمًا).
  - **البحث** يتم في قائمة محفوظة على التلفزيون، فلا يُرسل أي شيء أثناء الكتابة.
- **المشغّل:** ‏ExoPlayer مع فك الترميز عبر العتاد ومخزن مؤقت صغير ثابت، فتبقى الذاكرة مستقرة حتى على الأجهزة ذات 1 جيجابايت. وفيه أيضًا ضبط للسطوع أثناء التشغيل (زر الإعدادات في الريموت، أو الضغط مطولًا على موافق).
- **المظهر:** الألوان تتبع الخلفية، مع أيقونات بسيطة بلون واحد للتطبيقات الشائعة وأيقونات تلقائية لبقية التطبيقات.
- **اللغات:** العربية، والإنجليزية، والإسبانية، والبرتغالية، والإندونيسية، والصينية (الماندرين)، والروسية، والتركية، والأردية. تتبع لغة النظام، والعربية والأردية تُعرضان من اليمين إلى اليسار.
- **صغيرة وخفيفة:** حجمها نحو 1.3 ميجابايت، وصفحات الأقسام لا تنشئ إلا الصفوف الظاهرة على الشاشة.

### Horizon TV Setup (لنظام Windows)
1. **الاتصال:** فعّل تصحيح الأخطاء على التلفزيون (الخطوات معروضة في البرنامج)، واكتب عنوان IP الخاص به، ثم اضغط اتصال.
2. **التنظيف:** يُفحص التلفزيون تلقائيًا.
   - **محددة لك:** التتبع والتحليلات والإعلانات، وصفوف الإعلانات والتوصيات.
   - **باختيارك:** المساعد الصوتي، والبث، والتحديثات، والتطبيقات، وخدمات الخلفية. يوضّح البرنامج *سبب* إدراج كل عنصر.
   - **لا تُعرض أبدًا:** الأساسيات (الريموت، لوحة المفاتيح، متجر Play، خدمات Google، مداخل التلفزيون، الشاشة الرئيسية)، إذ تُكتشف من التلفزيون نفسه.
3. **الشاشة الرئيسية:** ثبّت Horizon Home، أو عُد إلى الشاشة الأصلية.
4. **الخلفيات:** تُقص الصور وتُضبط على مقاس الشاشة بجودة عالية.
5. **أيقونات التطبيقات:** حوّل أي صورة شعار إلى أيقونة في الصف الرئيسي.
6. **التراجع:** كل تغيير يُسجَّل ويمكن عكسه. التطبيقات التي ثبّتّها بنفسك تُنسخ احتياطيًا إلى الكمبيوتر قبل إزالتها.

يفتح البرنامج باللغة العربية افتراضيًا، والإنجليزية على بُعد نقرة واحدة.

### البناء من المصدر
تفاصيل البناء أدناه في القسم الإنجليزي ([Building from source](#building-from-source)).

### الخصوصية
- **بيانات الدخول:** بيانات دخول IPTV تبقى على التلفزيون، في التخزين الخاص بالتطبيق.
- **البحث:** لا يتصل بالمزوّد أثناء الكتابة.
- **الطقس:** يستخدم Open-Meteo (بلا حساب). الشيء الوحيد المُرسل هو اسم المدينة (من المنطقة الزمنية للتلفزيون، أو المدينة التي تختارها)، ويمكن إيقاف الطقس تمامًا.
- **مساعد زر الريموت:** خدمة إمكانية الوصول تستمع إلى زر واحد في الريموت فقط، ولا تقرأ الشاشة أبدًا.

أسماء التطبيقات وشعاراتها ملك لأصحابها. هذا المشروع غير تابع لأي خدمة بث أو شركة مصنّعة للتلفزيونات.

</div>

---

## English

A clean, fast home screen for Android TV, with a built-in IPTV player, plus a Windows app that sets it up and removes tracking and bloat from the TV, with no commands to type.

| | |
|---|---|
| **Horizon Home** (`horizon-home/`) | An Android TV launcher: home screen, IPTV player (Xtream Codes and M3U), favourites, Continue Watching, wallpapers and app icons, in 9 languages. |
| **Horizon TV Setup** (`setup-app/`) | A Windows app that connects to the TV over Wi-Fi, finds invasive services automatically and removes them, installs Horizon Home, and manages wallpapers and icons. Every change can be undone. |

### Download
Grab the ready-made files from the [`release/`](release) folder:
- **`Horizon TV Setup.exe`:** run it on any Windows PC. It needs no install, and everything it needs (adb and Horizon Home) is inside.
- **`HorizonHome.apk`:** the launcher on its own, if you'd rather install it yourself (`adb install HorizonHome.apk`).

### Horizon Home
- **Home screen**
  - A clock with the local weather and an icon (clear, cloudy, rain and so on). The city comes from the TV's time zone by default; pick another city or turn weather off in Options → Weather city.
  - Shortcuts to Live TV, Movies, Series and your favourite apps.
  - Continue Watching, with a button to remove items.
- **IPTV**
  - **Xtream Codes** (server, username, password) or an **M3U link**. You can add several playlists and switch between them, and each keeps its own history and favourites.
  - **Live TV:** a channel list with a preview window. Press OK once to preview a channel, and again for full screen.
  - **Movies and Series:** posters, resume, "Skip intro" (the length is learned per show), and sorting (A to Z, recently added, top rated).
  - **Search** works on a list saved on the TV, so nothing is sent while you type.
- **Player:** ExoPlayer with hardware decoding and a fixed small buffer, so memory stays flat even on 1 GB devices. It also has a brightness control during playback (the remote's settings button, or hold OK).
- **Looks:** colours follow your wallpaper, with simple single-colour icons for popular apps and automatic silhouettes for the rest.
- **Languages:** English, Arabic, Spanish, Portuguese, Indonesian, Mandarin, Russian, Turkish and Urdu. It follows the system language, and Arabic and Urdu lay out right to left.
- **Small and light:** about 1.3 MB. Category pages only build the rows on screen.

### Horizon TV Setup (Windows)
1. **Connect:** turn on debugging on the TV (the steps are shown in the app), type its IP address and press Connect.
2. **Clean up:** the TV is scanned automatically.
   - **Ticked for you:** tracking, analytics and ads, and ad or recommendation rows.
   - **Your choice:** voice, casting, updates, apps and background services. Each one shows *why* it's listed.
   - **Never listed:** essentials (remote, keyboard, Play Store, Google services, TV inputs, home screen) are detected on the TV itself.
3. **Home screen:** install Horizon Home, or go back to the original.
4. **Wallpapers:** pictures are cropped and resized to the screen, in high quality.
5. **App icons:** turn any logo picture into a home-row icon.
6. **Undo:** every change is recorded and can be reversed. Apps you installed yourself are backed up to the PC before removal.

The app opens in Arabic by default, and English is one click away.

### Building from source
**Horizon Home** builds without Gradle (`horizon-home/build.ps1`). It needs:
- JDK 17 in `%USERPROFILE%\android-build\jdk`
- Android SDK build-tools 33.0.2 and platform 28 in `%USERPROFILE%\android-build\sdk`
- The ExoPlayer (AndroidX Media3 1.4.1) jars in `%USERPROFILE%\android-build\libs\jars`: exoplayer, common, extractor, datasource, decoder, container, database and exoplayer-hls, plus androidx core 1.8.0, collection 1.2.0, annotation 1.3.0, exifinterface 1.3.6, guava 33.0.0-android and failureaccess 1.0.2.

R8 trims unused library code, while the whole player is kept (see `proguard-rules.pro`). The first build creates a local signing key, `local-signing.keystore`. Keep it private: updates only install over an existing copy when they're signed with the same key.

**Horizon TV Setup** needs Python 3 with Pillow and PyInstaller, and adb in `%USERPROFILE%\platform-tools`. Build Horizon Home first, then:

```bash
powershell -ExecutionPolicy Bypass -File setup-app\build_exe.ps1
```

The build leaves out unused Python modules and runs a self-test inside the packaged app. See [`setup-app/README.md`](setup-app/README.md).

### Privacy
- **Logins:** IPTV logins stay on the TV, in the app's private storage.
- **Search:** it never contacts the provider while you type.
- **Weather:** it uses Open-Meteo (no account). The only thing sent is a city name (from the TV's time zone, or the one you chose), and weather can be turned off entirely.
- **Remote button helper:** the accessibility service listens for one remote button only and never reads the screen.

### Credits
- [ExoPlayer / AndroidX Media3](https://github.com/androidx/media) (Apache 2.0)
- [Pillow](https://python-pillow.org)
- [PyInstaller](https://pyinstaller.org)
- [Open-Meteo](https://open-meteo.com) for weather
- `adb` from the Android SDK Platform-Tools, which the Windows app bundles

App names and logos belong to their owners. This project isn't affiliated with any streaming service or TV maker.
