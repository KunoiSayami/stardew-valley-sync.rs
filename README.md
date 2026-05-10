# Stardew Valley Save Sync

Sync Stardew Valley saves between your PC and Android device over your local Wi-Fi network — no cloud required.

## How it works

- The **PC server** (Rust) watches your save folder and exposes a small HTTP API.
- The **Android app** (native Kotlin + Jetpack Compose) auto-discovers the server via mDNS (same mechanism as LocalSend) and lets you push or pull any save slot.
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

### Federation (multi-server sync)

Multiple server instances can form a federation so that a save uploaded to any one server is automatically replicated to all others. This is useful for keeping multiple PCs in sync without involving the Android app.

Add the following to `config.toml`:

```toml
pin = "123456"

# Shared secret used between servers — different from the client PIN.
federation_token = "some-long-random-secret"

# List every other server in the federation.
[[peers]]
url = "http://192.168.1.20:24742"

[[peers]]
url = "http://192.168.1.30:24742"
```

**How it works:**
- When a client uploads a save, the receiving server immediately pushes the ZIP to all configured peers via `POST /api/v1/federation/push/{slot_id}`.
- Each peer re-replicates onward to *its own* peer list, excluding the sender, so the update fans out across the full mesh.
- Servers on the same LAN are also discovered automatically via mDNS — no static config needed for local peers.
- Replication is fire-and-forget: failures are logged but never surfaced to the uploading client.
- Federation is **opt-in**: omitting `federation_token` from the config disables it entirely.

### Windows system tray

On Windows the server runs as a system tray icon. Right-clicking it reveals:

| Menu item | Action |
|-----------|--------|
| *StardewSync — port XXXX* | Status (read-only) |
| **Start on Boot** | Toggle Windows autostart |
| **Add Federated Server…** | Open a dialog to add a peer at runtime |
| **Exit** | Shut the server down |

**Add Federated Server…** opens a dialog with two fields:

- **Server address** — the URL of the peer (e.g. `http://192.168.1.20:24742`).
- **Password (optional)** — the federation token for that peer. Defaults to this server's own `federation_token` when one is configured.

Peers added this way take effect immediately (no restart needed) but are not written back to `config.toml`. Add them to the config file if you want them to survive a restart.

### Security

This tool is designed for **LAN use only**. There is no TLS. The PIN prevents random devices on your network from accessing your saves. Do not expose the port to the internet.

---

## Android App

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (or any IDE with Android SDK)
- JDK 17+
- Android device running Android 11 or newer (API 30+)

### Build & install

- **Android Studio**: Open `android/` as the project root, let Gradle sync finish, then Run on a connected device.
- **CLI**:
  ```sh
  cd android
  ./gradlew :app:assembleRelease
  # APK: app/build/outputs/apk/release/app-release-unsigned.apk
  ```

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

[![](https://www.gnu.org/graphics/agplv3-155x51.png "AGPL v3 logo")](https://www.gnu.org/licenses/agpl-3.0.txt)

Copyright (C) 2026 KunoiSayami

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.