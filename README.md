# BigFred Android Client

Natywna aplikacja Android pokazująca UI BigFred w WebView, z wyszukiwaniem huba w LAN.

## Wymagania

- JDK 17+
- Android SDK (API 35), minSdk 29
- Android Studio Ladybug+ albo wiersz poleceń Gradle

## Build

```bash
cd bigfred-android-client

# Opcjonalnie: lokalizacja SDK
# echo "sdk.dir=/path/to/Android/Sdk" > local.properties

make apk      # release APK → app/build/outputs/apk/release/app-release.apk
make test     # testy jednostkowe (JVM)
make debug    # debug APK
make help     # lista targetów
```

Podpis release (opcjonalnie; bez tego używany jest debug keystore):

```bash
export BIGFRED_STORE_FILE=/path/to/keystore.jks
export BIGFRED_STORE_PASSWORD=...
export BIGFRED_KEY_ALIAS=...
export BIGFRED_KEY_PASSWORD=...
make apk
```

W Android Studio: **Open** → katalog `bigfred-android-client` → Run na urządzeniu/emulatorze.

## CI / Release

Na push / PR workflow [`.github/workflows/ci.yml`](.github/workflows/ci.yml) uruchamia `make test` + `make apk` i publikuje artefakt `release-apk`.

Na push taga `v*` workflow [`.github/workflows/release.yml`](.github/workflows/release.yml) czeka na CI, tworzy GitHub Release (jeśli nie istnieje) i dokłada APK (`gh release upload --clobber`).

Opcjonalne secrets do podpisu release:

| Secret | Opis |
|--------|------|
| `BIGFRED_STORE_FILE_CONTENT` | Treść keystore `.jks` zakodowana base64 |
| `BIGFRED_STORE_PASSWORD` | Hasło keystore |
| `BIGFRED_KEY_ALIAS` | Alias klucza |
| `BIGFRED_KEY_PASSWORD` | Hasło klucza |

Bez `BIGFRED_STORE_FILE_CONTENT` CI podpisuje APK debug keystoreem (nadaje się do instalacji lokalnej / sideload).

## Zachowanie

1. **Cold start** — jeśli zapisany jest URL serwera i `GET /` zwraca sukces, od razu otwiera WebView.
2. **Discovery** — równolegle:
   - mDNS / `http://bigfred.local:8080/`
   - NSD `_http._tcp` (instancje BigFred)
   - fallback `{podsieć}.120:8080`
   - ręczne wpisanie hosta/portu
3. Wybrany adres trafia do DataStore; zmiana w menu bocznym → **Ustawienia serwera**.
4. WebView ładuje lokalną SPA BigFred (JS, DOM storage, cleartext HTTP).
5. Na czas ekranu WebView trzymany jest `WifiManager.WIFI_MODE_FULL_LOW_LATENCY`.

## Uprawnienia

`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `WAKE_LOCK`.
