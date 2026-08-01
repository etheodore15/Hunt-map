# Brief: on-device bring-up of Hunt Map on the Hema HX-1

You are a Claude Code session running on a computer with a **Hema HX-1**
(Android 4.4.2, API 19, 1 GB RAM, built-in GPS, no Google services) attached
by USB. Your job is to install, test and iterate the legacy build of this
repo's app on that device until it works in the field.

## What this repo is

A hunting map app for the Morisset NSW area: Leaflet web app (single
`index.html` at repo root) wrapped two ways:

- `android/` — Capacitor app for modern phones (NOT your concern here).
- `hx1-app/` — a standalone plain-WebView wrapper for the HX-1
  (minSdk 19, pinned Gradle 7.6.4 + AGP 7.4.2, needs JDK 17).
  **This is the build you are working on.**

## How the HX-1 build works (read before changing anything)

- `scripts/prepare-hx1.sh` assembles `hx1-app/src/main/assets/www/` from the
  root web app: swaps the Capacitor runtime for Promise/fetch polyfills
  (`vendor/legacy/`), and substitutes a half-resolution topo overlay
  (`morisset_topo_hx1.webp`) because the full one would OOM a 1 GB device.
  **Always run it before building** — the assets dir is gitignored.
- `hx1-app/src/main/java/.../MainActivity.java` exposes `window.HXBridge`
  (sync JSON strings). In `index.html`, the bridge is shimmed to look like
  the Capacitor `TileStore` plugin (`IS_HX` flag), so trips/downloads/deletes
  reuse the same JS paths as the phone app.
- Tile requests are **intercepted natively** (`shouldInterceptRequest`) and
  served from `files/tiles/<md5(canonicalKey)>` or fetched with a TLS1.2-
  enabled socket factory (KitKat disables TLS1.2 by default — never let the
  WebView fetch tile hosts itself). Canonical keys: `topo|sat/z/y/x`,
  `lots/z/x/y` — must stay identical between `TileNet.java` and
  `canonKey()` in `index.html`.
- Trips are recorded by `TrackService` (foreground, CSV file) because the
  WebView JS is suspended when unfocused.
- The web app shows script errors in a red on-screen box (bottom) — the
  device-side console. `chrome://inspect` does NOT work on a 4.4 WebView;
  use `adb logcat` and that box.

## Build & install

```bash
./scripts/prepare-hx1.sh
cd hx1-app && ./gradlew assembleDebug     # needs JAVA_HOME -> JDK 17
adb install -r build/outputs/apk/debug/*-debug.apk
adb shell am start -n au.com.huntmap.hx1/.MainActivity
adb logcat -s "chromium:*" "WebViewConsole:*" "AndroidRuntime:E" "hx1:*"
```

CI also builds this APK on every push (`hunt-map-hx1-v*.apk` on the GitHub
release), so pushing to the branch is an alternative delivery path.

## Bring-up checklist (in order)

1. App launches, map renders (Satellite + NSW Topo base switch works).
   Watch `adb logcat` for WebView JS errors; the red box shows them too.
2. Panel opens/closes; sections expand. (Engine is Chromium 30 — if
   something renders broken, suspect modern CSS; fallbacks exist for env().)
3. GPS: tap 🎯 outdoors — WebView geolocation is granted natively by the
   wrapper. First fix can take minutes on a cold unit.
4. Trip logging: ⏺ start, walk around, ⏹ stop — track should save (points
   come from TrackService via HXBridge).
5. Offline areas: draw a small area, download (native downloader, progress
   via `__hxProgress`). Then enable airplane mode and confirm tiles render
   inside the area (native intercept serves them).
6. Memory: pan/zoom hard with the Morisset overlay on; if the WebView
   crashes/reloads, the overlay may still be too big — shrink
   `morisset_topo_hx1.webp` further in `scripts` (regen from
   `morisset_topo.webp` with PIL) or default `topoToggle` off for HX.
7. TLS: if tiles won't download, test which host fails:
   `adb logcat` + curl equivalents; fix belongs in `TileNet.httpGet`
   (worst case: bundle CA certs into a custom TrustManager there).

## Rules

- Don't touch `android/` (the phone app) unless a shared file in the repo
  root forces it; test web regressions if you edit `index.html`
  (it is shared by web, phone and HX-1 builds).
- Commit to the branch `claude/forest-boundaries-satellite-overlay-f0loyz`
  with clear messages; CI publishes releases automatically.
- Known accepted losses on HX-1: no Blend view (no mix-blend-mode), no
  Street View habit (old browser), no update banner (no GitHub API TLS
  guarantees) — the app must degrade gracefully, not crash.
