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
| **Topo map opacity** | Fades the topo sheet in/out over the imagery |
| **Satellite opacity** | Dims the imagery (map background is dark) |
| **Topo on top / Satellite on top** | Swaps the stacking order of the two layers |
| **Satellite / Streets (OSM)** | Switches the basemap between Esri satellite imagery and OpenStreetMap (roads, road names — handy for navigation); the opacity slider applies to whichever is active |
| **🎯 My location** | Live GPS tracking: blue dot + accuracy ring following your device position (browser location permission required; works in the field on the https:// page). The map follows you until you pan away — tap again to re-centre, tap once more to stop |
| **🧭 Trip logs** | Start/stop GPS track recording at any time (auto-starts GPS if needed). Stopping saves the trip automatically — no prompt. Each saved trip can be renamed, annotated, recoloured, hidden/shown or deleted from the Trip logs list; tap its name to zoom to it. Tracks persist in the browser and survive page reloads (an interrupted recording is recovered on next load) |
| **See-through paper** | Multiply blend — the white paper disappears so boundaries, contours and tracks draw directly on the satellite photo |
| **Show topo overlay** | Quick on/off toggle |
| **✕ / ☰** | Hides the whole control panel behind floating buttons (☰ controls, 📍 add note, 🗺 map key) for a full-screen map |
| **Live boundaries** | Current NSW Spatial Services layers drawn on top: property lots / cadastre (orange), state forest (green), national parks & reserves (magenta), and FCNSW **indicative permanent hunting exclusion zones** (red dashed — permanent exclusions only; temporary harvesting closures come with your DPIRD written permission / Hunt NSW app) |
| **🗺 Map key** | Shows the sheet's full legend (extracted from the GeoPDF) in a scrollable panel |
| **🚶 Street View** | Arm it, then tap anywhere on the map to open Google Street View at that spot (new tab, keyless deep link). Every property and note popup also carries a Street View link |
| **📍 Add note** | Arm it, then tap the map to drop a pin — pins are draggable, editable and deletable, persist in your browser (localStorage), and **Export** downloads them as GeoJSON for GPS apps |
| **Property finder** | Two layers driven by shared filters — **minimum size in acres**, a **bordering forest/reserve name** dropdown (e.g. only parcels touching Olney State Forest), and **must border: state forest and/or NPWS park/reserve/SCA**. Gold = for-sale listings (amber = within 250 m, grey = neither). Teal = *all* cadastral properties: every parcel bordering a state forest or NPWS reserve within ~30 km of the sheet (from 1 acre), plus all parcels ≥~50 acres region-wide, precomputed from the NSW cadastre with ground-corrected areas |

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
