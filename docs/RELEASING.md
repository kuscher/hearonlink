# Releasing HearOn Link

A release is a GitHub Release with the signed APK attached twice: as `HearOnLink.apk` (the README's
download link points at `releases/latest/download/HearOnLink.apk`, so this name is fixed) and as
`HearOnLink-<version>.apk`, plus `SHA256SUMS`.

**Every release must be signed with the HearOn Link release key** (alias `hearonlink`, certificate
SHA-256 `A1:12:57:4D:AA:A2:8B:09:C1:1E:B8:F3:40:B4:F8:30:D0:A3:29:ED:AB:B3:C8:5F:12:E0:2E:1F:26:33:47:B6`).
Android only installs an update over an existing app when both are signed with the same key; a
release signed with anything else makes everyone uninstall first (and lose their AirPods pairing
with the app, calibration and settings). The key lives with the maintainer in `~/.config/hearonlink/`
(`keystore.jks`, `keystore.pass`), backed up to private storage (a private folder, and a copy in a private folder); it is never committed. Google Play's app signing key is this same key, so Play and GitHub builds update each other
(`.gitignore` covers `*.jks`, `*.keystore`, `*.pass`).

## Steps

1. On `main`: bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`.
2. Add `docs/release-notes/<version>.md` (what's new, in plain words) and the same under a new
   heading in `CHANGELOG.md`.
3. Commit, then run:

```bash
tools/release.sh            # core tests, signed build, certificate check, checksums → executables/release-<version>/
tools/release.sh --publish  # … and push, then create the GitHub release v<version> with the notes
```

`tools/release.sh` refuses to continue if the notes file is missing, the key isn't there, the APK
isn't signed with the HearOn Link certificate, or (with `--publish`) there are uncommitted changes.

## Checking a release by hand

```bash
apksigner verify --print-certs executables/release-<version>/HearOnLink.apk | grep SHA-256
sha256sum -c executables/release-<version>/SHA256SUMS
```

Before publishing, install the release APK on a Googlebook over the previous release and check that
it connects (`./hol debug state` shows `status=CONNECTED`), that the tile and notification update,
and that the head-gesture demo still says Yes and No.
