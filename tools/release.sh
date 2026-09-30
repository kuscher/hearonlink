#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# Builds a signed release and (with --publish) creates the GitHub release.
#   tools/release.sh            build + verify into executables/release-<version>/
#   tools/release.sh --publish  … and publish it as v<version> with docs/release-notes/<version>.md
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."
VERSION=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)
NOTES=docs/release-notes/$VERSION.md
[ -f "$NOTES" ] || { echo "missing $NOTES"; exit 1; }
[ -f ~/.config/hearonlink/keystore.jks ] || { echo "no release key in ~/.config/hearonlink"; exit 1; }
./gradlew :core:test :app:assembleRelease --console=plain -q
OUT=executables/release-$VERSION
mkdir -p "$OUT"
cp app/build/outputs/apk/release/app-release.apk "$OUT/HearOnLink.apk"
cp "$OUT/HearOnLink.apk" "$OUT/HearOnLink-$VERSION.apk"
BT=$(ls -d "${ANDROID_HOME:-$HOME/Android/Sdk}"/build-tools/*/ | sort -V | tail -1)
CERT=$("$BT/apksigner" verify --print-certs "$OUT/HearOnLink.apk" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1)
EXPECT=a112574daaa28b09c11eb8f340b4f830d0a329edabb3c85f12e02e1f263347b6
[ "$CERT" = "$EXPECT" ] || { echo "wrong signing certificate: $CERT"; exit 1; }
(cd "$OUT" && sha256sum HearOnLink.apk "HearOnLink-$VERSION.apk" > SHA256SUMS)
ls -la "$OUT"; cat "$OUT/SHA256SUMS"
if [ "${1:-}" = --publish ]; then
  git diff --quiet && git diff --cached --quiet || { echo "commit first"; exit 1; }
  git push -q origin HEAD
  gh release create "v$VERSION" "$OUT/HearOnLink.apk" "$OUT/HearOnLink-$VERSION.apk" "$OUT/SHA256SUMS" \
    --title "HearOn Link $VERSION" --notes-file "$NOTES" --target "$(git rev-parse HEAD)"
fi
