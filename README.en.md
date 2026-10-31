# HyperOS後台卡片隱藏 (CardHide)

[繁體中文](README.md) | **English**

An Android LSPosed module that hides selected apps from the recent-apps list without actively removing their tasks or stopping the apps.

**Version: 1.0 build 7** · Package: `tw.local.cardhide` · Android versionCode: `7`

Build 7 removes automatic Root reboot, shows a non-cancelable manual-reboot notice on first enable, adds an active Shizuku round-trip test, and removes “/ Personal Space” from the header.

## Activation and compatibility

Light/dark dialogs use opaque surfaces and a dimmed backdrop. First enabling LSPosed shows a non-cancelable notice requiring a manual reboot through the device power menu.

**Root / LSPosed** retains the device-tested backend. **Shizuku without root (experimental)** uses wireless debugging with shell UID 2000. Start Shizuku, select this backend, authorize/connect, then enable hiding. No activation reboot is required.

The ROM must expose `WindowContainerTransaction.setTaskForceExcludedFromRecents` and grant shell `MANAGE_ACTIVITY_TASKS`. A HyperOS version number alone does not establish compatibility. The app checks actual interfaces and permissions and reports unsupported ROMs. It never substitutes task removal or force-stop.

The daemon checks running tasks about once per second. After selecting and opening a target app, **Check feature status** briefly restores and hides it again, verifying that the system recent-task list changes both ways. If the test reports system-level success while the HyperOS launcher still shows a card, the launcher is using a cache or private OEM list; use Root / LSPosed. After reboot, Shizuku still has to be started first.

Before changing backends or uninstalling, disable hiding and wait for the restored status. If the daemon dies, existing flags may remain until tasks are recreated or the device reboots. Original forced-exclusion flags are not exposed for reading; do not combine this experimental backend with another task-exclusion tool.

## Features

- Select apps by name or package name, with search.
- Two-page management: available apps on the first page, selected apps on the second; swipe or tap to switch.
- Master switch, individual restore and restore-all.
- HyperOS installed-app-list permission guidance, settings shortcut and refresh on return.
- Light, dark and system-following themes.
- Optional glass-inspired surfaces with transparency, local refraction and edge highlights.
- Full-window wallpaper with pan, pinch-to-zoom, screen-ratio and free-ratio cropping.
- Local settings and images; no Internet permission.

> The glass effect is a custom Android implementation inspired by Liquid Glass, not Apple's native material. The app UI is currently Traditional Chinese; this repository provides Chinese and English documentation.

## LSPosed requirements

- Android 12 / API 31 or newer.
- Root and a working LSPosed installation for your ROM.
- Legacy Xposed module support and the newer XSharedPreferences mechanism, API 93 or above.
- A compatible system implementation of `ActivityTaskManagerService.getRecentTasks`.

Development targets HyperOS. Compatibility with every Android release or custom launcher is not guaranteed.

## Download and install

Download and install [CardHide-1.0-build7.apk](downloads/CardHide-1.0-build7.apk). The following steps apply to LSPosed mode:

1. Install the APK.
2. Enable CardHide (後台卡片隱藏) in LSPosed.
3. Select **System Framework** (`android`) as the scope. Do not select each target app.
4. Reboot, then open CardHide.
5. Follow the app-list permission guidance, enable the master switch and select apps.
6. Wait about one second, leave Recents and open it again.

**Upgrading from 0.x:** install over the existing app to keep settings; do not uninstall first. Reboot if LSPosed requests it. Distributed releases retain the same signing certificate. A self-built APK signed with another key cannot replace the distributed APK in place.

## Installed-app-list permission

On first use, including the first upgrade to a permission-guided version, tap **前往權限設定** (Open permission settings). Find **取得應用程式清單 / 獲取應用列表** (Get installed apps) and choose **Allow / Always allow**, then return to CardHide.

If the ROM does not support the direct shortcut, CardHide opens its app-info screen instead. Continue to **Permissions / Other permissions**, or navigate manually:

**Settings → Apps → CardHide → Permissions → Get installed apps**

The main screen keeps a permission entry and a reload option. Denying access does not erase existing selections. If the list is empty or incomplete, check this additional HyperOS permission.

## Wallpaper and cropping

Open **外觀** (Appearance), then **選擇圖片並裁切** (Choose and crop image):

- Drag the image to position it and pinch to zoom.
- Drag the four corner handles to resize the crop frame.
- Screen ratio is the default for the current orientation; free ratio allows arbitrary proportions.
- Tap **套用** (Apply) to save, or cancel to keep the previous background.
- Free-ratio images and orientation changes may require additional center cropping to fill the window.

Images are decoded at a maximum long edge of 3072 pixels and saved at a maximum long edge of 2560 pixels. The system document picker grants access only to the selected image; broad photo-library permission is not needed.

## How it works and limitations

After the system framework's `ActivityTaskManagerService.getRecentTasks` returns, the module creates a filtered copy of its result. It **does not call removeTask, force-stop or killProcess**, and does not alter the actual stored tasks.

- This is not a permanent keep-alive tool. Battery management, memory pressure and app behavior still apply.
- Notifications, launcher icons and internal system tasks remain visible. This is not a security boundary.
- Selections apply to the Android user that saved them, not across app clones, work profiles or secondary spaces.
- Launchers may cache the recent-task list. Reopen Recents, or reboot if needed.
- ROM changes to internal methods or return types can break compatibility. A filtering failure leaves the original list untouched and logs the error.

## Compatibility and validation

A user reported that the **0.1.0 hiding core** worked on:

| Component | Reported environment |
| --- | --- |
| Device | Xiaomi Pad 8 Pro |
| ROM | HyperOS 4.0.0.44 |
| Root | KernelSU 32680-5 |
| Framework | LSPosed 2.21 |

Version 1.0 retains that hiding core. The version change does not imply that every newer UI feature has been validated on this device.

Completed checks: Java / DEX build, APK signature and alignment, eight selection tests, crop boundary cases and 10,000 randomized crop-range checks.

Still requires device testing: the updated permission-settings shortcut, complete app enumeration, glass rendering, crop gestures and other ROMs.

## Disable and troubleshoot

Turn off the master switch or deselect apps to restore their cards. To disable completely, disable the module in LSPosed and reboot.

Search LSPosed logs for `CardHide`:

- `getRecentTasks hooks=1` or higher: a hook target was found; verify actual behavior separately.
- `hooks=0` / `unsupported framework`: incompatible framework.
- `filtering failed; returning original list`: unexpected return format; the original list was retained.

For bug reports, include the device, full OS / Android / LSPosed versions, reproduction steps and relevant logs. Remove personal information before sharing logs.

## Build from source (Windows)

Install Python 3, JDK 21+ and Android SDK packages `platforms;android-36` and `build-tools;36.0.0`. Put the JDK `bin` directory on PATH.

The project uses SDK tools directly; it does not include a Gradle project or wrapper.

```powershell
New-Item -ItemType Directory -Force app/libs | Out-Null
Invoke-WebRequest 'https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar' -OutFile app/libs/xposed-api-82.jar
$env:CARDHIDE_KEY_PASSWORD = 'replace-with-your-signing-password'
python build.py --sdk 'D:\Android\Sdk' --keystore 'D:\Keys\cardhide.p12'
```

Output: `build/CardHide-1.0.apk`. The script creates a signing key at the specified location if none exists. Preserve the key for future upgrades. The build also runs the selection and crop tests.

The Xposed API JAR is compile-only and is not bundled in the APK; LSPosed supplies it at runtime. Release signing keys are not included in this repository.

## Project layout

```text
app/src/main/AndroidManifest.xml
app/src/main/java/tw/local/cardhide/
  CardHideApp.java    # Restore after Shizuku restarts
  MainActivity.java   # Management UI, permissions and appearance
  RecentHook.java     # LSPosed system-framework hook
  ShellClient.java    # Shizuku connection and status
  ShellService.java   # Shell-identity task exclusion
  Selection.java      # Package and Android-user filtering
  GlassSurface.java   # Glass-inspired rendering
  CropActivity.java   # Interactive image crop UI
  CropMath.java       # Crop geometry
app/src/main/assets/xposed_init
app/src/main/res/
tests/
build.py
```

## References

- [Xiaomi installed-app-list permission](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1619)
- [Xiaomi permission-settings shortcut](https://dev.mi.com/docs/appsmarket/technical_docs/adaptation_FAQ/)
- [LSPosed XSharedPreferences](https://github.com/LSPosed/LSPosed/wiki/New-XSharedPreferences)
- [Apple material design](https://developer.apple.com/design/human-interface-guidelines/materials)

This is an independent implementation. The original reference app's APK, code and assets are not included.
