#!/usr/bin/env bash
# Assemble the legacy (Android 4.4 / Hema HX-1) web assets:
# - polyfills instead of the Capacitor runtime
# - half-resolution topo overlay (1 GB RAM device)
set -e
cd "$(dirname "$0")/.."
DEST=hx1-app/src/main/assets/www
rm -rf "$DEST"
mkdir -p "$DEST"
cp index.html legend.webp listings.json properties.json "$DEST/"
cp morisset_topo_hx1.webp "$DEST/morisset_topo.webp"
cp -r vendor "$DEST/"
# swap the Capacitor runtime for the legacy polyfills
python3 - "$DEST/index.html" << 'PY'
import sys
p = sys.argv[1]
s = open(p).read()
s = s.replace('<script src="vendor/capacitor.js"></script>',
  '<script src="vendor/legacy/promise-polyfill.js"></script>\n'
  '<script src="vendor/legacy/fetch-polyfill.js"></script>\n'
  '<script src="vendor/legacy/hx-fetch-fix.js"></script>')
open(p, 'w').write(s)
PY
rm -f "$DEST/vendor/capacitor.js"
echo "hx1 www ready: $(du -sh "$DEST" | cut -f1)"
