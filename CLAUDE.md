# Hunt Map

Hunting map app for Morisset NSW: a single-file Leaflet web app
(`index.html`) shared by three targets:

- **Web** — served as-is (see README for links).
- **Phone (Android)** — Capacitor wrapper in `android/`; plugins in
  `android/app/src/main/java/au/com/huntmap/morisset/`. CI builds and
  publishes `hunt-map-v*.apk` on every push to the working branch.
- **Hema HX-1 (Android 4.4)** — plain-WebView wrapper in `hx1-app/` with
  its own pinned toolchain. **Read `HX1-DEBUG.md` before touching it** —
  it explains the bridge shim, native tile interception, and the
  on-device bring-up checklist.

Key invariants:
- `index.html` must stay ES5 (no arrows/let/const/template literals) —
  the HX-1's Chromium 30 engine parses it directly.
- Canonical tile keys (`topo|sat/z/y/x`, `lots/z/x/y`) must match across
  `index.html` (`canonKey`), `TileFiles/MapScreen` (phone), and
  `TileNet.java` (HX-1).
- `scripts/prepare-www.sh` (phone/web assets) and `scripts/prepare-hx1.sh`
  (legacy assets) must be run before the respective Gradle builds.

Branch: `claude/forest-boundaries-satellite-overlay-f0loyz`. CI:
`.github/workflows/android-apk.yml` builds both APKs and attaches them to
a GitHub release per push.
