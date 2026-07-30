# Hunt Map — Morisset 9131-1N Topo / Satellite Overlay

Interactive viewer that overlays the NSW Spatial Services **1:25,000 Morisset (9131-1N, 2017)**
topographic map on satellite imagery, with independent transparency control for each layer —
built to identify **state forest boundaries** (green linework on the topo sheet) against
real-world terrain.

## Use it

Open **`index.html`** — either:

- Live via GitHub Pages (enable Pages for this branch in repo *Settings → Pages*), or
- Live via githack: <https://raw.githack.com/etheodore15/Hunt-map/claude/forest-boundaries-satellite-overlay-f0loyz/index.html>, or
- Locally: clone the repo and open `index.html` in a browser (no build step, no server needed).

### Controls

| Control | What it does |
|---|---|
| **In the field** | The top section: 🎯 My location (live GPS with follow/re-centre/stop states), ⏺ Start trip log (auto-saves on stop; per-trip rename/note/recolour/show-hide/delete; live fix-quality readout), 📍 Add note |
| **Map layers** | One view switch — **Topo / Blend / Satellite** — plus a Topo strength slider and a background picker: Satellite, Streets (OSM), or **NSW Topo** (the statewide NSW Spatial Services topographic tile service — detailed topo far beyond the bundled Morisset sheet). "Advanced layering" holds the order swap and background brightness. 🗺 Map key shows the app symbols and the full topo legend |
| **Boundaries** | Live NSW layers: hunting exclusion zones (permanent only, with a safety callout), state forest, national parks & reserves, lot boundaries (with a zoom-in prompt) |
| **Offline areas** | Draw a rectangle (clamped to 20×20 km) and download its tile pyramid for offline use — pick any of NSW topographic, satellite imagery, and cadastral lot lines. Multiple areas can be saved, renamed, outlined on the map and deleted (tiles are removed unless another area still needs them). Tiles live in the device's IndexedDB store; the map reads that store first and only then the network |
| **Property finder** (collapsed by default) | For-sale listings and matching parcels driven by: minimum acres, a **must-border** choice (SF / park / either / anything), and a subordinate forest-name picker. Zero-result guidance, result counts, zoom-to-results, Street View |
| **Notes & data** | Export / Import — one GeoJSON bundling notes + trips, portable between web and the app |
| **✕ / floating buttons** | Hiding the panel leaves thumb-reach buttons bottom-right (☰ menu, 🗺 key, 📍 note, ⏺/⏹ trip, 🎯 location) with a live GPS-accuracy badge while tracking. Armed modes show a visible banner that cancels on tap or after 30 s |

The live boundary layers are fetched on demand from
`portal.spatial.nsw.gov.au` (NSW_Land_Parcel_Property_Theme and
NSW_Administrative_Boundaries_Theme, rendered outline-only), so unlike the 2017
topo sheet they reflect current data. Property lot lines only render once
zoomed in past ~1:100,000 (a limit of the NSW service).

## How it was made

The source PDF (`source/9131-1N-MORISSET.pdf`) is a GeoPDF exported from ArcMap. Its
`TOPO_BASEMAP` viewport carries a Measure dictionary with four ground control points
(GDA94 / MGA Zone 56). The map frame was:

1. Rasterised at ~3× (8678 px wide) with pdfium,
2. Cropped to the map viewport,
3. Perspective-warped from the projected (Transverse Mercator) frame into an
   axis-aligned **Web Mercator** image using the four GeoPDF control points,
4. Saved as `morisset_topo.webp` (alpha corners where the rotated frame doesn't cover).

Because the image is Mercator-aligned, a plain Leaflet `imageOverlay` at bounds
`[-33.13084, 151.23591] → [-32.99409, 151.51389]` registers exactly with the satellite
tile grid — no manual nudging needed.

## Coverage

Morisset / western Lake Macquarie area, including Olney State Forest, Awaba State Forest,
Watagans National Park and Jilliby State Conservation Area.

> **Note:** map data is the 2017 edition — verify current land tenure and access rules with
> NSW authorities before relying on it in the field.

Topo map © NSW Spatial Services. Satellite imagery © Esri World Imagery.

## Native Android app

The repo doubles as a Capacitor project that wraps the same app in a native
Android shell, adding what a browser can't do:

- **Background GPS trip logging** — a foreground service (persistent
  notification) keeps fixes flowing with the screen off or the app in the
  background, via `@capacitor-community/background-geolocation`.
- **Persistent state** — settings, active layers, filters, notes and trip
  logs live in the WebView's storage and survive app restarts (the web
  version persists them too, via localStorage).

### Get the APK / updates

Every push to this branch builds the APK and publishes it as a **GitHub
Release** (Releases page → latest → attached `hunt-map-v1.0.N.apk`); the
same file is also an Actions artifact. Install it once from the Releases
page — after that **the app checks for new releases on launch and shows an
"Update available — Download" banner** when the repo has moved on. Builds
are signed with a persistent CI key so updates install over the top
(if the key cache is ever lost you'll get a one-time "app not installed"
and need to uninstall/reinstall — export your notes/trips first).

### Moving your data between web and app

**Export** (Notes section) downloads one GeoJSON containing all notes *and*
trip logs; **Import** reads that file back and merges it, skipping
duplicates. Use it to carry your web markers into the Android app (or
between phones): Export on the web page → share the file to your phone →
Import inside the app.

### Build locally

```bash
npm install
npm run sync          # copies web assets into www/ and syncs the android project
npx cap open android  # opens Android Studio; or: cd android && ./gradlew assembleDebug
```

On first GPS use the app asks for location permission — choose
**"While using the app"** and allow **precise** location. The trip logger
then keeps recording with the screen locked; a notification shows while the
service is active.
