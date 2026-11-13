# IPTV Hub Android TV

A lightweight Android TV / Google TV shell for the existing hosted IPTV Hub PWA. The website remains the source of truth; this app opens `https://garrettstorts-cmyk.github.io/iptv-hub/` in a full-screen Android WebView and does not bundle or rewrite the web application.

## Requirements

- Windows 10 or later
- Android Studio stable, including Android SDK command-line tools and Platform Tools
- JDK 17 or newer (Android Gradle Plugin 9.5.1 requires JDK 17)
- Android SDK Platform 37 and Android SDK Build Tools 36.0.0
- An internet connection for the Gradle wrapper and Android dependencies on the first build

The project uses Android Gradle Plugin 9.5.1, whose published compatibility table supports API 37 and requires Gradle 9.6.0 / JDK 17. The Gradle wrapper downloads the matching Gradle distribution automatically.

### Install prerequisites on Windows

1. Install the latest stable Android Studio from [developer.android.com/studio](https://developer.android.com/studio). The Android Studio Setup Wizard can install the SDK and Platform Tools.
2. In Android Studio, open **Tools → SDK Manager**. Install **Android SDK Platform 37**, **Android SDK Build-Tools 36.0.0**, **Android SDK Platform-Tools**, and **Android SDK Command-line Tools (latest)**.
3. Use a JDK 17 installation. Android Studio's bundled runtime is also suitable if it reports Java 17 or newer. In Android Studio, the Gradle JDK is under **Settings → Build, Execution, Deployment → Build Tools → Gradle**.
4. Open a new PowerShell terminal and verify:

   ```powershell
   java -version
   adb version
   ```

### Set `ANDROID_HOME` / SDK variables if needed

The normal Windows SDK location is `%LOCALAPPDATA%\Android\Sdk`. Set the user environment variables from PowerShell (change the path if you selected a different SDK location):

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
[Environment]::SetEnvironmentVariable("ANDROID_HOME", $sdk, "User")
[Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", $sdk, "User")
```

Set `JAVA_HOME` to the root of your installed JDK 17 if it is not already configured, then add the SDK platform tools to your user `Path` if needed:

```powershell
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Path\To\jdk-17", "User")
$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
$platformTools = "$sdk\platform-tools"
if ($userPath -notlike "*$platformTools*") {
    [Environment]::SetEnvironmentVariable("Path", "$userPath;$platformTools", "User")
}
```

Close and reopen VS Code after changing environment variables. You can install SDK components from PowerShell with:

```powershell
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" "platform-tools" "platforms;android-37" "build-tools;36.0.0"
```

## Build a debug APK

From this directory in the VS Code PowerShell terminal:

```powershell
.\gradlew.bat clean assembleDebug
```

The first build downloads Gradle and required Android build artifacts. The installable debug APK is:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Install on Google TV with ADB

1. On the TV, open **Settings → System → About** and select **Android TV OS build** (or **Build**) seven times to enable Developer Options. Menu labels vary by manufacturer.
2. Open **Settings → System → Developer options** and enable **USB debugging** or **Wireless debugging** if your TV provides it. Accept the debugging authorization prompt on the TV.
3. For modern Wireless debugging, use the IP addresses and ports displayed on the TV. Pair first using the pairing port and pairing code:

   ```powershell
   adb pair TV_IP:PAIRING_PORT
   ```

   After pairing, connect using the separate connection port shown by the TV:

   ```powershell
   adb connect TV_IP:ADB_PORT
   adb devices
   ```

   For older network debugging that does not offer pairing, use the TV's displayed IP and ADB port with `adb connect TV_IP:ADB_PORT`, then verify with `adb devices`. For a USB connection, `adb devices` should show the TV before installing. Do not assume an IP address or port; wireless debugging ports can change.
4. Once the debug APK has been built, install or update it with:

   ```powershell
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

   To remove the app manually if needed (this deletes its local app data):

   ```powershell
   adb uninstall com.garrettstorts.iptvhub
   ```

   Do not uninstall if you want to preserve favorites, recent channels, and settings.
5. Open **IPTV Hub** from the TV launcher.

### USB-drive or APK-transfer alternative

Copy `app-debug.apk` to a USB drive or transfer it with a trusted APK-transfer app. On the TV, open the file with a file manager or transfer app and allow that app to install unknown apps when Android prompts. The exact menus vary across Google TV devices. Do not enable unknown-app installation for apps you do not trust.

## Release APK later

Run:

```powershell
.\gradlew.bat assembleRelease
```

Without a configured release signing key, the output is normally `app\build\outputs\apk\release\app-release-unsigned.apk` and cannot be installed as an update to the debug-signed APK. Configure a private release keystore and a Gradle signing configuration before distributing a signed release. Do not commit the keystore or its passwords.

## TV behavior and testing notes

- The only web content kept in the WebView is HTTPS content within the hosted IPTV Hub path. Ordinary external HTTP/HTTPS links open in the device browser; unsupported schemes are blocked.
- JavaScript, DOM storage, browser caching, and user-initiated media playback are enabled. File/content access, cleartext WebView traffic, and arbitrary JavaScript bridges are not enabled.
- D-pad and Enter/Center events are not intercepted by the native Activity, so Android WebView can deliver them as keyboard events to the PWA's existing focus/navigation handling.
- Back dispatches an Escape key event to the focused page element first. The PWA can close its player, dialogs, or secondary screen; at Home the wrapper exits; otherwise actual WebView history is used when available.
- HTML fullscreen custom views cover the WebView, hide system chrome, and keep the display awake. Native Media3/ExoPlayer could be added later as a playback fallback only if real-device WebView codec/HLS testing shows it is needed.
- If the hosted page fails to load, the shell displays **Unable to load IPTV Hub** with **Retry** and **Exit** actions. Detailed failures are logged in Logcat.
- Verify D-pad focus, Escape/Back transitions, external browser handoff, retry behavior, and HLS/audio/video playback on the actual Google TV model. Codec and HLS support varies by device/WebView version.

### Google TV test checklist

Record results on the actual TV; these checks have not been run until a device is connected.

- [ ] HOME: App appears in Apps launcher; IPTV Hub banner/icon appears; launches without browser chrome.
- [ ] NAVIGATION: D-pad Up/Down/Left/Right; OK/Center; Back; search focus; Browse by Category.
- [ ] SEARCH: Channel-name search; channel-number search; current-program search; future movie/show search.
- [ ] GUIDE: Open Guide; move across programs; open program details; return to the prior screen.
- [ ] PLAYBACK: Open a known working channel; fullscreen; Back exits fullscreen; previous/next channel; Arrow Up/Down surfing.
- [ ] PERSISTENCE: Favorites, recent channels, and settings survive an app restart.
- [ ] NETWORK: App loads after closing/reopening; behavior is sensible while offline.
- [ ] STREAM RESULTS: Record the channel/stream and device/WebView version for each result below.

| Channel / stream | Result | Notes |
|---|---|---|
|  | Plays successfully |  |
|  | Unsupported format |  |
|  | Network error |  |
|  | Fails only in WebView; worked elsewhere |  |

No Play Store developer account is required for debug builds or sideloaded testing.
