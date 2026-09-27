#!/usr/bin/env bash
# Mirrors the debcred modernization artifacts from the plugin workspace into this repository.
# The work is done with the code-modernization plugin in ../plugin (analysis/debcred, modernized/debcred,
# sp-banco-bolivariano); this repo is the published copy. Run from this folder, then commit and push.
set -euo pipefail
SRC="$(cd "$(dirname "$0")/../plugin" && pwd)"
rm -rf legacy analysis modernized
mkdir -p legacy analysis modernized
cp -r "$SRC/sp-banco-bolivariano" legacy/sp-banco-bolivariano
cp -r "$SRC/analysis/debcred" analysis/debcred
cp -r "$SRC/modernized/debcred" modernized/debcred
# build output, jqwik database and the plugin's own packets folder are not published
find modernized -type d -name target -prune -exec rm -rf {} +
rm -rf analysis/debcred/packets
find . -name '.jqwik-database' -prune -exec rm -rf {} +
echo "synced from $SRC"
