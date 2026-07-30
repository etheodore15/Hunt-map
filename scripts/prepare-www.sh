#!/usr/bin/env bash
# Copy the web app into www/ for Capacitor (the app itself lives at repo root
# so the GitHub-served URL keeps working).
set -e
cd "$(dirname "$0")/.."
rm -rf www
mkdir -p www
cp index.html morisset_topo.webp legend.webp listings.json properties.json www/
cp -r vendor www/
