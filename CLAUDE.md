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
  Actions.kt (Action list, performer, SystemActions accessibility service), Reactions.kt (ear pause/resume,
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
  `state`, `send HEX`, `log on` (tx/rx to logcat; serials are masked), `render …` (Shots.kt).
- `./hol debug` clears logcat first; use a raw `am broadcast` when you need earlier lines. A goAsync
  receiver holds the app's broadcast queue: later debug broadcasts wait until it finishes.
- Two crashes in a row mark the app "bad" and background broadcasts stop starting it: uninstall +
  install clears it (no user data to lose during development; ask before doing it once the user uses it).
- `pm grant` needs `--user 10` on the HP. Fresh installs are in the stopped state: broadcasts use `-f 0x20`.
- Debug builds are signed with the release key (~/.config/hearonlink, alias hearonlink,
  cert SHA-256 A1:12:57:4D:…:26:33:47:B6), so they replace releases.

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
- Googlebook ≥840 dp: device pane left (452 dp, chrome colour), settings right (max 680 dp).
- Only show what the AirPods support (Family features + reported controls). Root-only features never show.
- No INTERNET permission, no overlays. The accessibility service (SystemActions) only performs global
  actions (Home, Overview, Back, Notifications, QS, Screenshot, Lock): no event types, no window content,
  and it's off until the user turns it on for a system action. Peek itself is a WM key gesture
  (TOGGLE_DESKTOP_HOME_SCREEN_PEEK) apps can't send; "Show desktop (peek)" uses GLOBAL_ACTION_HOME.
- Head motion streams only for owners: the demo, a ringing call (phones), or "gestures anytime" (opt-in,
  only while a bud is in an ear). The demo pauses anytime actions (Controls.demoOpen).
- Battery shows the cache: live parts normal, others faded with their age. Never wipe a level on a
  "disconnected" report.
