# HearOn Link: notes for working on the code

AirPods companion for Googlebooks (Googlebook OS = Android 17 desktop) and Android phones. Plain APK,
Kotlin + Jetpack Compose, Material 3 Expressive (material3 1.5.0-alpha29, pinned). MIT, clean-room:
never copy LibrePods (GPL-3.0) code, prose, fonts or images; byte layouts/opcodes are facts.

## Layout
- `core/` pure Kotlin, JUnit on the VM (`./hol test`):
  `aap/` Aap.kt (framing, builders, control ids, ListeningMode), AapEvent.kt (parser), PodState.kt
  (state + reducer), Models.kt (model numbers / BLE product ids → Family + Feature, incl. Beats),
  BatteryCache.kt (per-part last known level with time/source/live), Proto.kt (tiny protobuf for sensor
  messages); `gestures/HeadGestures.kt` (nod/shake detector, our own design), `gestures/Calibration.kt`
  (finds the nod/shake int16 offsets and scale from recorded frames);
  `ble/Proximity.kt` (proximity advert parse, AES decrypt, RPA resolve).
- `app/` package `io.github.kuscher.hearonlink`:
  `link/` AapConnection.kt (L2cap: hidden createL2capSocket via HiddenApiBypass; session), Link.kt
  (app-wide hub: StateFlow<LinkState> with pod + DeviceCache + Batteries, commands, sensor streams by owner),
  Controls.kt (stem-press forwarding 0x39 + actions, head gestures anytime, nod/shake for calls on phones),
  Actions.kt (Action list, performer, launchable apps for "Open an app"), Reactions.kt (ear pause/resume,
  ducking while talking, low battery; HeadMotion decode offsets), LinkService.kt (FGS connectedDevice +
  PresenceService (CDM) + BtReceiver), Nearby.kt (PendingIntent BLE scan + Companion association helpers).
  `system/` Notifications.kt (+ Actions receiver), ModeTile.kt (tile + Glance BatteryWidget).
  `ui/` Main.kt (MainActivity, AppScreen adaptive ≥840 dp two-pane, PanelActivity/PanelContent),
  Screens.kt (DevicePane, HomeSettings, PhoneNav, pages), Demo.kt, Onboarding.kt, AppSettings.kt,
  Components.kt (caption spacer/header, tooltips, PodsArt, BatteryTrio, ModeGroup, grouped rows),
  Shots.kt (offscreen renderer), theme/Theme.kt.
  `DebugReceiver.kt` adb hooks (DUMP-guarded).
- `probe/` throwaway instrumentation probe (raw AAP over adb). `docs/` plan, research, design canvas source.

## Dev loop
- `./hol build | install | test | debug … | render KIND W H DPI DARK OUT | head SECS | logs | idle`.
  Installing doesn't launch; never launch or steal focus while the user is active (`./hol idle`).
- Debug hooks: `session N [keep]` runs the link without the service (the FGS can't start from the
  background without a companion association), `selftest ID A B` writes a control and restores it,
  `action NAME[:package]` performs an action from the background like a press would (it takes focus),
  `state`, `send HEX`, `log on` (tx/rx to logcat; serials are masked), `render …` (Shots.kt).
- `./hol debug` clears logcat first; use a raw `am broadcast` when you need earlier lines. A goAsync
  receiver holds the app's broadcast queue: later debug broadcasts wait until it finishes.
- Two crashes in a row mark the app "bad" and background broadcasts stop starting it: uninstall +
  install clears it (no user data to lose during development; ask before doing it once the user uses it).
- `pm grant` needs `--user 10` on the HP. Fresh installs are in the stopped state: broadcasts use `-f 0x20`.
- Debug builds are signed with the release key (~/.config/hearonlink, alias hearonlink,
  cert SHA-256 A1:12:57:4D:…:26:33:47:B6) on a machine that has it, so they replace releases.

## Releasing (docs/RELEASING.md)
- No machine or key file needed: bump `versionCode`/`versionName`, add `docs/release-notes/<version>.md`
  (+ CHANGELOG.md) and Play's "What's new" (`store-submission/listing/en-US/release-notes.txt`, max 500
  characters), commit, push, then `git tag v<version> && git push origin v<version>`.
- The tag runs `.github/workflows/release.yml`: signed APK as the GitHub release (`HearOnLink.apk`,
  `HearOnLink-<version>.apk`, `SHA256SUMS`), the bundle as a DRAFT on Play's closed-testing track.
  Sending it for review stays a button in the Play Console; never automate that.
- The key is in the GitHub environment `release` (the Play key in `play`); never read, copy or print
  key material. "Run workflow" on the Actions tab is a dry run (nothing published). `tools/release.sh`
  still works where `~/.config/hearonlink` has the key.

## Protocol gotchas (see docs/research/device-findings.md)
- Control writes are applied but NOT echoed: update state optimistically.
- Listening-mode writes seem ignored while both buds are out of ear.
- Head tracking: DEVMOTION6 (service 16) when firmware build starts with ≥8, else ACTIVITY (14); 25 Hz.
  Offsets default to 30 up/down, 28 sideways (public notes). The calibration wizard (ui/Calibrate.kt:
  still → nod → shake → check) stores offsets + a swing scale PER AXIS, per primary bud ("L"/"R") in
  DeviceCache.head; the detector divides each axis by its scale. Raw frames of the last calibration
  are in the app cache (`./hol pull calibration-last.txt`). On the HP's Pro 2 at rest: 44/46/48 look
  like gravity (≈1024 total), 26/28/30 like rotation rates, 0/2/10/12/50 are counters.
- Custom stem presses: control 0x39 = mask of forwarded presses (only customised ones), events op 0x19.
  Unverified: whether the mask survives when the AirPods move to another device (keep defaults = none).
- Custom EQ (op 0x63) shows only after the AirPods report it; the flags byte is echoed back unchanged.

## Design rules (canvas https://claude.ai/artifact/TPTz5wiH4FfdQvC5mhV1bu, approved direction)
- Nothing floats; header row below the system caption, caption painted the same colour.
- Desktop window (manifest): `resizeableActivity`, `<layout>` min 360 × 480 dp, and `configChanges` for
  size/keyboard changes, so MainActivity is NOT recreated on resize: everything size-dependent must
  read Compose state (`windowWidthDp()`, insets), never values captured in onCreate. Verified on the
  HP with `am task resize ID l t r b` (same Window, layout switches at 840 dp); that shell command
  ignores the minimum size, so the minimum is only declared, not verified by dragging.
- Googlebook ≥840 dp: device pane left (452 dp, chrome colour), settings right (max 680 dp), on every
  page but the full-window demo. The header keeps the AirPods' name; Back and the page title sit at
  the top of the right pane (never Back in the window's top-left corner beside the device pane).
- The UI reads `LinkState.view`, not `pod`: live values filled in with the remembered model and
  settings until the AirPods report them, and all from memory while away. Away = same controls,
  switched off (the user read hidden controls as lost features).
- Only show what the AirPods support (Family features + reported controls). Root-only features never show.
- No INTERNET permission, no overlays, and NO accessibility service (removed in 0.2.0 at the user's
  request: it makes a Play release hard; don't bring it back). "Show desktop" is the Home intent
  (ACTION_MAIN + CATEGORY_HOME) and "Open an app" a launch intent; both start an activity from the
  background, which Android allows only for a companion app (CDM association). Being connected is
  not an association: picking such an action asks for it right away (`rememberCompanionLinker`), and
  a reminder card shows only while it's missing. The association request must name the paired device
  (`BluetoothDeviceFilter.setAddress` + `setSingleDevice(true)`): only then does Android look among
  bonded devices; a UUID-only request scans for devices in pairing mode and its list stays empty.
  Stored action values:
  the Action name, plus ":package" for OPEN_APP; unknown names (0.1's Overview, Back, Notifications,
  Quick Settings, Screenshot, Lock) fall back to AirPods default (presses) or Nothing (gestures).
  Peek itself is a WM key gesture (TOGGLE_DESKTOP_HOME_SCREEN_PEEK) apps can't send.
- Head motion streams only for owners: the demo, a ringing call (phones), or "gestures anytime" (opt-in,
  only while a bud is in an ear). The demo pauses anytime actions (Controls.demoOpen).
- Battery shows the cache: live parts normal, others faded with their age. Never wipe a level on a
  "disconnected" report.
