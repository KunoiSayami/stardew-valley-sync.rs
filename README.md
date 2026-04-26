# Stardew Valley Save Sync

Sync Stardew Valley saves between your PC and Android device over your local Wi-Fi network — no cloud required.

## How it works

- The **PC server** (Rust) watches your save folder and exposes a small HTTP API.
- The **Android app** (Flutter) auto-discovers the server via mDNS (same mechanism as LocalSend) and lets you push or pull any save slot.
- Both sides create timestamped backups before overwriting anything.
- If the target save is newer than the source, you are prompted before proceeding.

---

## PC Server

### Save file locations

| Platform | Default path |
|----------|-------------|
| Windows  | `%APPDATA%\StardewValley\Saves` |
| Linux    | `~/.config/StardewValley/Saves` |

The path is auto-detected. Override with `--saves-dir`.

### Build

```sh
cargo build --release -p server
# binary: target/release/stardew-sync-server  (Linux)
#         target/release/stardew-sync-server.exe  (Windows)
```

### Run

```sh
# Minimal — auto-detects saves dir, uses default port 24742
stardew-sync-server --pin 123456

# All options
stardew-sync-server --pin 123456 --port 24742 --saves-dir "C:\custom\path\Saves"
```

| Flag | Default | Description |
|------|---------|-------------|
| `--pin` | *(required)* | 4–8 digit PIN shared with the Android app |
| `--port` | `24742` | TCP port to listen on |
| `--saves-dir` | auto-detected | Path to the `Saves` folder |

The server advertises itself on the LAN via mDNS (`_stardewsync._tcp.local.`) so the Android app can find it automatically.

### Security

This tool is designed for **LAN use only**. There is no TLS. The PIN prevents random devices on your network from accessing your saves. Do not expose the port to the internet.

---

## Android App

### Prerequisites

- [Flutter SDK](https://flutter.dev/docs/get-started/install) installed
- [Android Studio](https://developer.android.com/studio) with the Flutter plugin
- Android device running Android 11 or newer (API 30+)

### Build & install

1. Open the `android/` folder in Android Studio.
2. Let Gradle and Flutter sync finish.
3. Run on a connected device or build an APK:
   - **Android Studio**: Run → Run 'main.dart'
   - **CLI**: `flutter build apk --release` (from `android/`)
4. Sideload the APK from `android/build/app/outputs/flutter-apk/app-release.apk` if not installing directly.

### First launch

1. Enter the server's IP and PIN (or wait for auto-discovery to find it).
2. When prompted, grant the app access to the Stardew Valley saves folder:  
   `Android/data/com.chucklefish.stardewvalley/files/Saves`  
   This uses Android's Storage Access Framework — no root required.
3. Push or pull any save slot. The app warns you if the destination is newer.

### Backup location

- **Server-side**: `<saves-dir>/<slot>.bak.<timestamp>/`
- **Android-side**: same folder as the save, named `<slot>.bak.<timestamp>/`

---

## License

AGPL-3.0 — see [LICENSE](LICENSE).
