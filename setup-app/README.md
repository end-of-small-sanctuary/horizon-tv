# Horizon TV Setup

A Windows program that cleans up an Android TV, Google TV box or stick and sets up Horizon Home, with no commands to type. It's one file, **`dist\Horizon TV Setup.exe`**. Copy that file to any Windows PC and run it; nothing needs installing.

## What it does
| Tab | What you can do |
|---|---|
| **1. Connect** | Type the TV's IP address and press Connect. The app shows the device's make, model, Android version and screen size. |
| **2. Clean up** | Scans the device and lists the known clutter groups found on it. Each app shows whether it's running, installed, disabled or removed. Tick what to remove, and pick Disable or Uninstall for each app, then apply. You can also speed up or turn off animations. |
| **3. Home screen** | Install or update Horizon Home. This makes it the home screen, switches the stock home screen off and turns on the helper for the remote's settings button. Another button goes back to the original home screen. |
| **4. Wallpapers** | Add pictures, which are cropped and resized to the screen automatically. You can also rename, remove and preview them. |
| **5. App icons** | Pick an app, then any logo picture. The app removes its background and makes it a single-colour home-row icon. You can also remove it again. |
| **6. Undo** | Every change made on this device, which you can undo one at a time or several at once. |

## Safety
- **Nothing changes without a confirmation.**
- **Essentials are never listed:** the remote, keyboard, Play Store, Google services, Wi-Fi, Bluetooth and TV inputs.
- **Preinstalled apps are only disabled or removed for the current user,** so Undo brings them back.
- **Apps the user installed are backed up to the PC before they're uninstalled** (the box under the list, ticked by default).
- **A snapshot of the device's state is saved before each set of changes.**
- **Where things are kept:** each device's history, backups and log are in `Documents\Horizon TV Setup\devices\<serial number>\`.

## IPTV playlists
Horizon Home supports both common IPTV formats. Add them on the TV under **Options > IPTV playlists > Add a playlist**:
- **Xtream Codes:** a server address, username and password.
- **M3U link:** one link to an `.m3u` or `.m3u8` playlist. Live channels, films and series are sorted out automatically and grouped by the playlist's own groups.

## Building the .exe
Needs Python 3 with Pillow and PyInstaller, and adb in `%USERPROFILE%\platform-tools`.

```bash
powershell -ExecutionPolicy Bypass -File setup-app\build_exe.ps1
```

The build bundles adb and the current `horizon-home\out\HorizonHome.apk`. Rebuild Horizon Home first (`horizon-home\build.ps1`) if you changed it.

Unused Python libraries are left out of the build (numpy, SSL, Pillow's AVIF plugin and others; see `$exclude` in `build_exe.ps1`), which brings the `.exe` from 37 MB down to about 16 MB. After every build, the script runs a self-test inside the packaged app (`--selftest`), covering wallpapers, icons, image formats, languages and adb. The build fails if anything is missing.

## How the clean-up list is built (no manual list needed)
`analyse.py` scans the connected TV and decides for itself. For every package it checks:
- **Names:** the package name and the APK's file or folder name, for words like analytics, tracking, telemetry, ACR, ads, feedback, bug report, auto-install and recommendations.
- **Permissions:** whether it can capture what's on screen (the way ACR ad tracking works), read which apps you use, use the advertising ID, or install and delete apps silently.
- **Behaviour:** whether it starts with the TV, uses the internet, runs with no icon, or is running right now.

Groups **1 (tracking, analytics and ads)** and **2 (ad and recommendation rows)** are ticked automatically. Voice, accessibility, casting, updates, apps with icons and other background services are listed for the user to choose. Each row shows **why** it was listed.

**Never offered:** the device's own keyboards, TV inputs and current home screen (all detected on the device itself), plus Google core services, Android core and vendor hardware services.

`KNOWN` in `analyse.py` holds friendlier names for common packages. It's optional, since the scan works without it.

## Languages
The app opens in **Arabic (right to left)** by default. The selector at the top switches to English, and the choice is remembered. All texts are in `i18n.py`.
