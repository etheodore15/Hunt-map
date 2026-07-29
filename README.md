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
| **See-through paper** | Multiply blend — the white paper disappears so boundaries, contours and tracks draw directly on the satellite photo |
| **Show topo overlay** | Quick on/off toggle |

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
