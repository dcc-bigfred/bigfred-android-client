# BigFred Android Client

Native Android app that displays the BigFred UI in a WebView, with LAN hub discovery.

## Requirements

- JDK 17+
- Android SDK (API 35), minSdk 29
- Android Studio Ladybug+ or Gradle from the command line

## Build

```bash
cd bigfred-android-client

# Optional: SDK location
# echo "sdk.dir=/path/to/Android/Sdk" > local.properties

make apk      # release APK → app/build/outputs/apk/release/app-release.apk
make test     # unit tests (JVM)
make debug    # debug APK
make help     # list targets
```

Release signing (optional; without it the debug keystore is used):

```bash
export BIGFRED_STORE_FILE=/path/to/keystore.jks
export BIGFRED_STORE_PASSWORD=...
export BIGFRED_KEY_ALIAS=...
export BIGFRED_KEY_PASSWORD=...
make apk
```

In Android Studio: **Open** → `bigfred-android-client` directory → Run on a device/emulator.

## CI / Release

On push / PR, [`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs `make test` + `make apk` and publishes the `release-apk` artifact.

On push of a `v*` tag, [`.github/workflows/release.yml`](.github/workflows/release.yml) waits for CI, creates a GitHub Release (if missing), and attaches the APK (`gh release upload --clobber`).

Optional secrets for release signing:

| Secret | Description |
|--------|-------------|
| `BIGFRED_STORE_FILE_CONTENT` | Keystore `.jks` contents encoded as base64 |
| `BIGFRED_STORE_PASSWORD` | Keystore password |
| `BIGFRED_KEY_ALIAS` | Key alias |
| `BIGFRED_KEY_PASSWORD` | Key password |

Without `BIGFRED_STORE_FILE_CONTENT`, CI signs the APK with the debug keystore (suitable for local install / sideload).

## Behavior

1. **Cold start** — if a server URL is saved and `GET /` succeeds, the app opens WebView immediately.
2. **Discovery** — in parallel:
   - mDNS / `http://bigfred.local:8080/`
   - NSD `_http._tcp` (BigFred instances)
   - fallback `{subnet}.120:8080`
   - manual host/port entry
3. The selected address is stored in DataStore; change it from the side menu → **Server settings**.
4. WebView loads the local BigFred SPA (JS, DOM storage, cleartext HTTP).
5. While the WebView screen is shown, `WifiManager.WIFI_MODE_FULL_LOW_LATENCY` is held.

## Permissions

`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `WAKE_LOCK`.
