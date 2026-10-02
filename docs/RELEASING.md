# Releasing HearOn Link

A release is a GitHub Release with the signed APK attached twice: as `HearOnLink.apk` (the README's
download link points at `releases/latest/download/HearOnLink.apk`, so this name is fixed) and as
`HearOnLink-<version>.apk`, plus `SHA256SUMS`. The same build's App Bundle goes to Google Play as a
draft on the closed-testing track.

Pushing a tag `v<version>` does all of it on GitHub Actions. No particular machine is needed, and
nobody (person or agent) needs the key file.

**Every release must be signed with the HearOn Link release key** (alias `hearonlink`, certificate
SHA-256 `A1:12:57:4D:AA:A2:8B:09:C1:1E:B8:F3:40:B4:F8:30:D0:A3:29:ED:AB:B3:C8:5F:12:E0:2E:1F:26:33:47:B6`).
Android only installs an update over an existing app when both are signed with the same key; a
release signed with anything else makes everyone uninstall first (and lose their AirPods pairing
with the app, calibration and settings). Google Play's app signing key is this same key, so Play and
GitHub builds update each other.

## Steps

1. On `main`: bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`.
2. Add `docs/release-notes/<version>.md` (what's new, in plain words) and the same under a new
   heading in `CHANGELOG.md`.
3. Write Play's "What's new" in `store-submission/listing/en-US/release-notes.txt` (at most 500
   characters).
4. Commit and push, then tag that commit and push the tag:

```bash
git tag v<version> && git push origin v<version>
```

## What the tag does

`.github/workflows/release.yml` runs two jobs:

- **build** stops if the notes file is missing, if `versionName` isn't the tag's version, or if the
  Play text is over 500 characters. Then it runs the core tests, builds the signed APK and bundle,
  and stops unless both carry the HearOn Link certificate. It publishes the GitHub release
  `v<version>` with the notes and `HearOnLink.apk`, `HearOnLink-<version>.apk` and `SHA256SUMS`.
- **play** puts the bundle on Google Play as a **draft** on the closed-testing track, with the
  "What's new" text (`tools/play-upload.mjs`). What is live there stays live. If that version code
  is on Play already, it uploads nothing.

A draft isn't served and isn't reviewed. Someone still presses **Send for review** in the Play
Console: nothing goes to review automatically.

## A dry run

Press **Run workflow** on the Actions tab (Release, branch `main`), or run
`gh workflow run release.yml --ref main`. It does the same signed build, tests and certificate
checks, and checks that the Play key works. It publishes nothing and uploads nothing to Play. Do
one after changing the build or the workflow.

## Where the key lives

In the repo's GitHub environment `release` (secrets `SIGNING_KEYSTORE_B64` and
`SIGNING_KEYSTORE_PASS`); the Play key is the secret `PLAY_SERVICE_ACCOUNT_JSON` of the environment
`play`. Both environments accept only `main` and `v*` tags, so pull requests and forks never get
the secrets. The job that holds the signing key runs only GitHub's own actions, pinned to exact
commits. Anyone with write access to the repo can push a tag, so give write access only to people
you'd trust with a release.

The key is backed up privately, outside the repo. It is never committed (`.gitignore`
covers `*.jks`, `*.keystore`, `*.pass`).

## On a machine that has the key

The older route still works where the key is in `~/.config/hearonlink/` (`keystore.jks`,
`keystore.pass`):

```bash
tools/release.sh            # core tests, signed build, certificate check, checksums → executables/release-<version>/
tools/release.sh --publish  # … and push, then create the GitHub release v<version> with the notes
```

The same run writes `HearOnLink-<version>.aab` next to the APKs: the bundle Google Play wants
(see [store-submission/README.md](../store-submission/README.md)). It isn't attached to the GitHub
release, and this route doesn't upload it to Play.

`tools/release.sh` refuses to continue if the notes file is missing, the key isn't there, the APK
isn't signed with the HearOn Link certificate, or (with `--publish`) there are uncommitted changes.
`--publish` creates the tag `v<version>` too, and that starts the workflow: it leaves the release
you made as it is, and still builds the bundle and puts it on Play as a draft.

## Checking a release by hand

```bash
gh release download v<version> --dir check && cd check   # or cd executables/release-<version>
apksigner verify --print-certs HearOnLink.apk | grep SHA-256
sha256sum -c SHA256SUMS
```

If `apksigner` isn't on PATH, it's in `$ANDROID_HOME/build-tools/<version>/`.

Before tagging, try that commit on a Googlebook and check that it connects (`./hol debug state`
shows `status=CONNECTED`), that the tile and notification update, that the head-gesture demo still
says Yes and No, and that a press or gesture set to Show desktop or Open an app does it with
HearOn Link's window closed. After the release, install `HearOnLink.apk` over the previous release
to see that the update goes through.
