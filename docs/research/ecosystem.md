# AirPods companion-app ecosystem (research for PodLink)

Researched 2026-09-30. LibrePods is covered in depth in [librepods.md](librepods.md) and only appears
here for comparison. Protocol facts checked on hardware are in [device-findings.md](device-findings.md)
(HP Googlebook 14, Android 17, AirPods Pro 2 USB-C). Sources are linked inline and listed at the end.

Method: shallow clones of CAPod, OpenPods, PodsCompanion, AirPodsDesktop, MagicPodsCore and AirStatus
(code read directly), GitHub issues and PRs via `gh`, Google Play listing pages fetched with curl
(ratings, installs, descriptions, review snippets as of 2026-09-30), Apple's trademark pages, Play policy
pages, two PETS papers and the MagicPairing paper, plus a read-only `dumpsys` on the HP Googlebook.

---

## 0. Key takeaways

1. **Non-root noise control works on Android 17, including Googlebooks.** Google fixed the AOSP
   Bluetooth-stack bug ([issuetracker 371713238](https://issuetracker.google.com/issues/371713238)) in
   the March 2026 AOSP release. It shipped in Pixel Android 16 QPR3 / April 2026, in the Android 17 betas,
   and in ColorOS/OxygenOS 16. Since then CAPod 5.x, LibrePods, PodsLink, AirPro, "AirPods on Android"
   (DenoSoft) and PodWing all switch ANC/Transparency/Adaptive **without root**. They do it by opening a
   classic (BR/EDR) L2CAP channel to PSM `0x1001` (Apple's AAP) through the **hidden**
   `BluetoothDevice.createInsecureL2capSocket(int)` / `createL2capSocket(int)`, reached with
   LSPosed's HiddenApiBypass technique. The public `BluetoothSocketSettings` API still rejects
   `TYPE_L2CAP` on API 37. CAPod confirms the hidden path on a Pixel 8 running API 37, and
   device-findings.md confirms it on the Googlebook.
2. **Only one app can hold the AAP channel at a time.** CAPod and LibrePods block each other
   ([capod#538](https://github.com/d4rken-org/capod/issues/538)), so PodLink has to detect this and explain it.
3. **Nearly everything open source here is GPL-3.0** (CAPod, OpenPods, PodsCompanion, LibrePods,
   MagicPodsCore, AirPodsDesktop, AirStatus, kAirPods, LinuxPods). If PodLink wants a permissive license,
   it must re-implement from protocol facts (clean room) and not copy code. MIT references exist
   (winpods, omarchy-pods), and HiddenApiBypass is Apache-2.0.
4. **Name conflict: "PodsLink – AirPods Battery"** (`net.podslink`, 1M+ installs, 4.4★, developer name
   "PodsLink") is one letter away from "PodLink", in the same category, and already advertises ANC and
   head gestures. "Podlink" is also an established podcast-link service (pod.link). Apple sent
   cease-and-desist letters over "pod" names in the iPod era. **Recommendation: pick a different name, or
   at least run a trademark and Play search before committing.**
5. **No existing app has a real large-screen or desktop layout.** CAPod has no window-size-class code.
   LibrePods has open "Tablet UI" issues, and its popup scales with screen width, so it fills a third of a
   tablet screen. That gap is PodLink's to fill on Googlebooks.
6. **Many older apps are invisible on Googlebooks.** Play hides apps targeting API 34 or lower from new
   users on newer Android. That likely rules out Assistant Trigger, PodAir, AirBuds Popup and Wunderfind.
   AirBattery (7.2M downloads) left Play in May 2025.

---

## 1. App landscape

### 1.1 Android apps at a glance

Play numbers are from the listing pages on 2026-09-30 (US English). "ANC" means the app can switch
listening modes without root.

| App (package) | License / source | Last activity | Stars | Play installs · rating (reviews) | Root | ANC | Notable features |
|---|---|---|---|---|---|---|---|
| **CAPod** (`eu.darken.capod`) | GPL-3.0 code; icons, logos, animations, store texts excluded ([README](https://github.com/d4rken-org/capod)) | push 2026-09-28, v5.4.0-rc0 2026-09-24 | 1,147 | 500K+ · 3.8 (1.56K) · no ads, IAP/sub "Pro"; also F-Droid + IzzyOnDroid | No | **Yes** since v5.0 (Apr 2026), on ROMs with the fix | BLE battery (10%), AAP battery (1%), IRK/ENC key auto-retrieval, case-open + connection popups (overlay), ear-detection play/pause, auto-connect, per-device reactions, ANC QS tile, battery + ANC Glance widgets, press controls, conversation awareness, sleep detection, charge-complete notification, battery-life/health estimates, debug-log recorder |
| **OpenPods** (`com.dosse.airpods`) | GPL-3.0 ([repo](https://github.com/adolfintel/OpenPods)) | v1.11 2025-09-26 | 1,259 | **Not on Play by design**; F-Droid + direct APK | No | No | Battery notification only. Deliberately breaks Play builds (anti-reupload) |
| **MaterialPods** (`com.pryshedko.materialpods`) | Proprietary | updated 2026-09-23 | – | 1M+ · 4.2 (22.2K) · ads + IAP | No | No | Popup animation, notification battery, dark/light widgets, play/pause on ear detection, assistant trigger, "ring" to find buds |
| **AndroPods** (`pro.vitalii.andropods`) | Proprietary | updated 2026-08-22 | – | 10M+ · 4.1 (71.7K) · IAP (Pro) | No | No | iOS-style popup animation, battery % drawn into the status-bar notification icon, ear detection [PRO], assistant on 4 taps [PRO]. Asks for location + overlay |
| **PodsCompanion** (`io.github.domi04151309.podscompanion`) | GPL-3.0, OpenPods fork ([repo](https://github.com/Domi04151309/PodsCompanion)) | **archived**, last push 2020-11-07 | 64 | Play listing gone (404) | No | No | Notification, popup, widget |
| **AirBattery** (`friedrich.georg.airbattery`) | Proprietary | last update 2023-04-09 | – | **Removed from Play 2025-05-09**; had 7.2M downloads, 2.56★ ([AppBrain](https://www.appbrain.com/app/airbattery/friedrich.georg.airbattery)) | No | No | Self-updating notification, lowest-pod status icon, in-ear detection |
| **Assistant Trigger** (`com.dotarrow.assistantTrigger`) | Proprietary | updated 2024-10-01 | – | 1M+ · 3.8 (17K) · ads + IAP | No | No | Squeeze/double-tap starts the voice assistant, case-open popup, notification battery (pro), ear detection (pro), caller/notification announcer |
| **Wunderfind** (`com.hf.findlostdevice`) | Proprietary | updated 2022-08-16 | – | 1M+ · 3.8 (79.7K) | No | No | BLE RSSI "distance score" radar for any BT device; play-sound is Pro |
| **PodsLink** (`net.podslink`) | Proprietary ([help site repo](https://github.com/wbshm/podslink-help)) | updated 2026-09-20 | – | 1M+ · 4.4 (23K) · IAP | No | **Yes** ("requires Android 16 QPR3 or later") | Popups with custom wallpapers/animations, "dynamic island", gestures, voice broadcast, offline tracking, head gestures, conversation awareness. Full trademark notice |
| **PodsBattery** (`com.yugongkeji.podstool`) | Proprietary | updated 2026-09-22 | – | 1M+ · 4.5 (46.3K) · ads + IAP | No | ? (touch-control settings listed) | Popup (free), notification, status bar, widgets, QS, "Find my headphones", "Dynamic Island", in-ear detection |
| **AirPods on Android** (`com.sumyapplications.bluetooth.earphone`, DenoSoft) | Proprietary | updated 2026-09-25 | – | 1M+ · 4.4 (11.3K) · ads + IAP | No | **Yes** | Popup "island", noise control, TWS support. Good disclaimer wording |
| **AirPro: AirPod Tracker & Find** (`com.evotap.airpod`, Ashrise) | Proprietary | updated 2026-09-07 | – | 1M+ · 4.4 (11.8K) · ads + subscription | No | **Yes** ("Full ANC Control on Android!") | Finder, widgets, popup; reviews call the paywall and trial a scam |
| **PodWing** (`dk.nickn.podwing`) | Proprietary, free | updated 2026-09-17 | – | 100+ · 4.7 · no ads | No | **Yes** (app + QS tile) | **Per-app noise automations**, 10-band EQ, low-battery alert at any %, gestures, find tone, sleep timer, media card, renaming. Clean disclaimer |
| AirBuds Popup (`kr.pe.designerj.airbudspopup`) | Proprietary, paid | updated 2022-03-02 | – | 10K+ · 3.8 | No | No | Popup, 1-min lockscreen notification, wearing detection, "assistive listen" (mic passthrough) |
| PodAir (`com.bickster.podair`) | Proprietary | updated 2024-10-30 | – | 1M+ · 4.1 · ads | No | No | Battery level |
| WonderPods (`com.l2p.android.wonderpods`) | Proprietary | updated 2025-08-24 | – | 10K+ · 4.8 | No | No | 3D popup themes, last-paired location |
| BudWave (`com.insideinc.budwave`) | Proprietary | updated 2026-07-21 | – | 10K+ · 4.6 · no ads | No | ? | "Earbuds battery & EQ" |
| AFind (`com.dreamteammobile.find.my.airpods`) | Proprietary | updated 2026-09-25 | – | 5K+ · 4.2 · ads | No | No | Finder |
| **Beats** (`com.apple.bnd`, by Apple) | Proprietary (official) | updated 2026-09-16 | – | 10M+ · 3.9 (25.5K) | No | Beats only | Apple's own Android app, **Beats products only** (no AirPods): one-touch pairing, battery, widgets, locate on map, firmware updates, opt-in analytics |
| *LibrePods* (`me.kavishdevar.librepods`) | GPL-3.0 | updated 2026-08-27 | ~30K | 10K+ · 4.9 (1.09K) · IAP | No on A16 QPR3+/A17; root on older | **Yes** | See librepods.md |

Not on Play, but worth knowing: apps literally named "Apple Airpods for Android" (`com.airpods.android`,
100K+, 2.6★) and "Apple Airpods Pro 2" (`com.apple_airpods_forandroid…`, 50K+, 3.1★) are still listed.
That shows how loosely Play enforces trademarks, not that such names are safe (see §4).

### 1.2 Notes per app

**CAPod** (d4rken-org, Germany). This is the reference implementation for PodLink's scope.
- Architecture: Kotlin, Hilt, Compose, Navigation3, Glance widgets. Two data sources merge into one
  `PodDevice`: BLE snapshots (`pods/core/apple/ble/*`) and an AAP session (`pods/core/apple/aap/*`,
  added in v5.0.0-beta0 on 2026-04-04, [PR #464](https://github.com/d4rken-org/capod/pull/464)). Each model
  is a small class with a `DEVICE_CODE`. Capabilities come from `PodModel.Features` flags (see §3.7).
- AAP transport (`common/bluetooth/l2cap/L2capSocketFactory.kt`) first tries the public
  `BluetoothSocketSettings` with `TYPE_L2CAP`. That throws `IllegalArgumentException` on API 37, so it
  falls back to hidden `createInsecureL2capSocket(psm)` after `HiddenApiBypass.setExemptions("Landroid/bluetooth/")`
  (a vendored copy of LSPosed AndroidHiddenApiBypass, Apache-2.0).
  [PR #706](https://github.com/d4rken-org/capod/pull/706): "On a Pixel 8 (API 37) with AirPods Pro 2
  the obfuscated build took the hidden-API L2CAP path and completed handshake, settings decode and
  ANC/stem commands."
- After connecting it requests the IRK and ENC keys (AAP opcode 0x30) and stores them in the device
  profile. From then on it matches BLE adverts by resolving the RPA and decrypts the 1% battery (§2.7).
- Permissions: `BLUETOOTH_SCAN` (neverForLocation), `BLUETOOTH_CONNECT`, `POST_NOTIFICATIONS`,
  `SYSTEM_ALERT_WINDOW` (popup), `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `RECEIVE_BOOT_COMPLETED`,
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, plus location only up to API 30. It does **not** use
  CompanionDeviceManager.
- Scanner: hardware `ScanFilter` on manufacturer data `004C` with `[07, 19]` masked, and three scan
  modes (low power 2 s batch, balanced 1 s, low latency 0.5 s). Compatibility toggles let users disable
  offloaded filtering, disable batching, or use "indirect delivery" (PendingIntent scans), because
  some ROMs drop or batch adverts.
- Monetisation: free core, "CAPod Pro" IAP or yearly subscription for themes, auto play/pause, popups,
  widgets, advanced settings and device controls. The FOSS build unlocks through GitHub Sponsors.
  Since 5.0 the free tier shows one device on the dashboard.
- Pain points, from issues and reviews: duplicate or ghost cards (for example an odd non-status `0x07`
  frame sent on connect, [#603](https://github.com/d4rken-org/capod/issues/603)); ear detection
  unreliable on Pro 2 ([#38](https://github.com/d4rken-org/capod/issues/38)); Max always "in ear"
  ([#106](https://github.com/d4rken-org/capod/issues/106), [#548](https://github.com/d4rken-org/capod/issues/548));
  "advanced settings unavailable" on ROMs without the fix ([#538](https://github.com/d4rken-org/capod/issues/538));
  a Play reviewer with AirPods Pro 3 on a Samsung S23 Ultra paid for Pro and got no ANC (most likely
  a ROM without the L2CAP fix). The whole
  developer account was once wrongly terminated as "malware" (Aug 2023, restored after 19 days,
  [#155](https://github.com/d4rken-org/capod/issues/155)).
- Praise: open source, no ads, frequent releases, and a responsive developer who works from debug logs.

**OpenPods** (Federico Dossena). It was the original FOSS decoder, and its approach survives in AirStatus,
PodsCompanion and early CAPod.
- It matches by strongest RSSI: it keeps beacons from the last 10 s, picks the strongest, and ignores
  anything weaker than −60 dBm (`PodsStatusScanCallback.java`). It reads the advert as a hex string (§2).
- **Stays off Play on purpose.** The author found paid or ad-laden reuploads, some even under the
  OpenPods package name, and sent DMCA notices
  ([#24](https://github.com/adolfintel/OpenPods/issues/24), [#81](https://github.com/adolfintel/OpenPods/issues/81),
  [#138](https://github.com/adolfintel/OpenPods/issues/138)). The app "intentionally violates Google Play
  policies on disabling battery optimization" and sabotages itself when installed from Play.
- Complaints: scanning drains the battery ([#27](https://github.com/adolfintel/OpenPods/issues/27)),
  location must be on (pre-Android 12), and replicas report wrong battery levels.

**MaterialPods / AndroPods / PodsBattery / PodsLink / DenoSoft / AirPro** are the closed-source mass
market. They compete on iOS-look popups ("dynamic island", 3D animations, custom wallpapers), widgets and
finder features, and monetise with ads plus IAP. Recurring complaints across their reviews:
- battery values frozen or wrong ("100% for both… 65% for the case 24/7"; "empty gray box");
- audio briefly pausing or dropping while the service runs;
- popups that keep appearing after being turned off;
- paywalls on basic battery display, aggressive ads, subscription traps (AirPro);
- ear detection only half-working.

Praise goes to apps that "just work" after choosing the right model (PodsBattery), have a generous free
tier (MaterialPods) and look nice. PodsLink's FAQ still says 1% battery only works in pairing mode, which
predates its AAP support (unverified).

**PodWing** is small (100+ installs) but has the most interesting new ideas: **per-app noise automations**
(Transparency for Maps, ANC for music), a battery alert at any threshold, and a strictly offline, free
posture.

**Assistant Trigger / AirBuds Popup / AirBattery** are the 2018–2022 generation. They pioneered popups,
announcing callers and assistant triggers from media-button squeezes. They are unmaintained now, and
their target API levels probably keep them off Android 17 devices (§4.5).

**Wunderfind** is a generic BLE RSSI "warmer/colder" finder. PodLink can do the same with IRK-matched
adverts (RSSI radar), plus a "play tone" over A2DP when the buds are connected (MaterialPods, PodWing).
Avoid the phrase "Find My" (§4).

### 1.3 Desktop and other platforms

| Project | Platform | License | Activity / stars | What it does | Notes |
|---|---|---|---|---|---|
| **MagicPods** ([site](https://magicpods.app), [issue/readme repo](https://github.com/steam3d/MagicPods-Windows)) | Windows (Microsoft Store 9P6SKKFKSHKM) | Proprietary app (repo has no license); free, "no ads/subscriptions" | readme repo 872★, push 2026-09-22 | Battery in tray, Win11 widget, Win10 live tile; ear detection; **auto-switch audio to PC speakers when both buds are out**; iOS-style case popup; **hotkeys** for ANC, output and BT; VoiceOver notifications; low-battery alerts | ANC, conversation awareness and button remap need the separate **MagicAAP driver**, because Windows has no user-mode BR/EDR L2CAP client. Also supports Galaxy Buds, Sony and Airoha fakes |
| **MagicPodsCore** ([repo](https://github.com/steam3d/MagicPodsCore)) | Linux / SteamOS backend (WebSocket API) | GPL-3.0 | 108★, push 2026-09-29 | AAP battery, ANC, conversation awareness, press settings; BLE popup trigger with IRK + ENC decryption | Frontends: [MagicPodsDecky](https://github.com/steam3d/MagicPodsDecky) (Steam Deck, GPL-3.0, 94★), MagicPodsPlasmoid, [MagicPodsLinux](https://github.com/steam3d/MagicPodsLinux) (GPL-3.0). Its `AapModelIds.h` is the cleanest model table |
| **AirPodsDesktop** ([repo](https://github.com/SpriteOvO/AirPodsDesktop)) | Windows 10/11 x64 (Linux WIP) | GPL-3.0 | 3,472★, v0.6.1 beta 2026-09-19 (added AirPods 5) | Tray battery, ear detection with media control, low-latency mode, **animated popups (light/dark)** | Ships per-model `.avi` popup animations (`Source/Resource/Video/*`). Its packed `AppleCP.h` struct is a compact spec of the advert |
| **AirStatus** ([repo](https://github.com/delphiki/AirStatus)) | Linux (Python + bleak) | GPL-3.0 | 143★, push 2024-05 | JSON battery output for bars | OpenPods logic (−60 dBm, 10 s strongest) |
| **LinuxPods** ([repo](https://github.com/mstroecker/LinuxPods)) | Linux GTK (Rust) | GPL-3.0 | 4★, push 2026-09-13 | BLE + AAP | Useful [BLE doc](https://github.com/mstroecker/LinuxPods/blob/main/docs/ble-proximity-pairing.md). Its model table has errors (§2.3). It drops BLE while AAP is connected |
| **kAirPods** ([repo](https://github.com/can1357/kAirPods)) | KDE Plasma 6 | GPL-3.0 | 216★, push 2026-01 | Battery, noise control, panel widget | |
| **omarchy-pods** ([repo](https://github.com/thisisgm/omarchy-pods)) | Omarchy (Hyprland) bar | **MIT** | 219★, push 2026-08-26 | Per-pod and case battery, listening modes, adaptive level, CA, one-bud ANC, ear-detection policy | UI built from **capability keys**. Documents a BlueZ issue: continuous LE discovery blocks reconnection of other bonded LE devices ([#64](https://github.com/thisisgm/omarchy-pods/issues/64)), and discovery during a live AAP link causes crackle |
| **winpods** ([repo](https://github.com/sinanovicanes/winpods)) | Windows | **MIT** | 39★, push 2026-08-23 | Movable widget, tray, low-battery notifications | "not affiliated with Apple Inc." note |
| Gnome extensions ([maniacx](https://github.com/maniacx/Airpod-Battery-Monitor), [delphiki](https://github.com/delphiki/gnome-airpods-battery-status)) | GNOME Shell | GPL-3.0 / none | 54★ / 47★ | Top-bar battery | |
| macOS: NoiseBuddy (BSD-2-Clause, 730★, 2024), OnlySwitch (MIT), AirBuddy (commercial) | macOS | – | – | Touch Bar/menu-bar listening-mode switching, iOS-like popups on Mac | Native Apple APIs; mostly UX inspiration |

**Wear OS:** no AirPods companion for Wear OS turned up. CAPod has an old issue about a Galaxy Watch 5
([#154](https://github.com/d4rken-org/capod/issues/154)). This is a gap, but a low priority one.

### 1.4 What users praise and complain about (all sources)

**Praise:** seeing all three battery levels at a glance; the case-open popup ("like the iPhone");
reliable auto-pause; ANC switching from a tile or widget "without reaching for the stem"; no ads;
open source; a developer who answers.

**Complaints**, roughly by frequency:
1. Battery **inaccurate, stale or frozen** (10% steps, adverts arrive slowly, wrong model chosen).
2. **Background death**: popups and notifications stop because the OEM killed the service; apps push
   battery-optimisation whitelisting (PodsLink ships per-OEM guides).
3. **Audio side effects**: brief pauses or dropouts while the helper runs (AndroPods, Assistant Trigger
   reviews). On Linux, LE discovery during a live link crackles (omarchy-pods).
4. **Duplicates / other people's AirPods** showing up (RSSI heuristics), ghost cards.
5. **Paywalls and ads** on basic battery display, subscription traps.
6. **ANC "doesn't work"** on ROMs without the L2CAP fix. Users can't tell app bugs from platform gaps
   (CAPod's wording fix: [PR #513](https://github.com/d4rken-org/capod/pull/513)).
7. LibrePods specifically: taking over the AirPods can disturb setup done on Apple devices (hearing-aid
   settings), and car handoff can wedge the buds (Play reviews).

### 1.5 Which non-root apps switch ANC and Transparency, and how

**How:** they use AAP (Apple Accessory Protocol / "AACP") over **classic L2CAP PSM 0x1001** to the
bonded AirPods (SDP service `74ec2172-0bad-4d01-8f77-997b2be0722a`). The sequence (MagicPodsCore
`sdk/aap/setters/*`, CAPod `AapConnection.kt`; full detail in librepods.md and device-findings.md):
- handshake `00 00 04 00 01 00 02 00 00 00 00 00 00 00 00 00`
- enable notifications `04 00 04 00 0F 00 FF FF <mode> FF`
- InitExt for H2 devices `04 00 04 00 4D 00 0E 00 00 00 00 00 00 00`
- key request `04 00 04 00 30 00 05 00`
- set listening mode `04 00 04 00 09 00 0D <m> 00 00 00`, where m is 1 = Off, 2 = ANC,
  3 = Transparency, 4 = Adaptive

**Why it works now:** the Android BT stack rejected Apple's L2CAP channel configuration ("Peer does not
support our desired channel types", `l2c_fcr.cc`; [capod#215](https://github.com/d4rken-org/capod/issues/215)).
Before 2026 only rooted devices with a patched `libbluetooth` could do this (LibrePods/ALN). Google fixed
it in AOSP in March 2026. Apps still need the **hidden** socket constructor. The public
`BluetoothSocketSettings.Builder.setSocketType` is documented as "Must be one of: TYPE_RFCOMM, TYPE_LE",
and `setL2capPsm` accepts only 128–255 for LE
([docs, updated 2026-08-03](https://developer.android.com/reference/android/bluetooth/BluetoothSocketSettings.Builder)).

**ROM compatibility** (community tracker [capod#538](https://github.com/d4rken-org/capod/issues/538),
as of 2026-09-29):

| Works | Doesn't |
|---|---|
| Any Android 17 / AOSP-17-based build (Pixel A17, Samsung One UI 9 beta on S23+/S25U/S26U/Z Flip 6, **HP Googlebook 14** per device-findings.md) | Samsung One UI 8.5 (Android 16: S24+, S24U, S25U) |
| Pixel Android 16 from the April 2026 update (one Pixel 10a on the same build reported failing) | GrapheneOS builds based on Android 16 |
| OnePlus 12/15 OxygenOS 16, Oppo ColorOS 16 | Nothing Phone 4a Pro, Fairphone 6 (A16), Poco F7 Pro HyperOS 3.0.303 |
| Poco X7 Pro HyperOS 3.1 after disabling `com.xiaomi.bluetooth` | |

Also:
- OnePlus, Oppo and realme ship a *native* AirPods integration that only does battery and ear detection.
  It is unrelated to the fix.
- **Only one AAP client at a time**: a phone with LibrePods installed made CAPod fail until LibrePods
  was removed.

**Apps with non-root ANC on Play (Sept 2026):** CAPod (Pro feature), LibrePods, PodsLink ("Android 16
QPR3 or later"), AirPro, AirPods on Android (DenoSoft), PodWing (free, plus QS tile). The closed-source
ones almost certainly use the same AAP path; PodsLink's own wording confirms the platform dependency.

**Other routes:** Beats headphones expose an RFCOMM control channel that AirPods don't (steam3d in
capod#215). That is why Apple's official Beats app can control Beats on any Android. Windows needs a
kernel driver (MagicAAP). Linux/BlueZ allows raw L2CAP sockets.

---

## 2. The Apple "Proximity Pairing" BLE advertisement

### 2.1 Framing

- AD type `0xFF` manufacturer data, company ID **`0x004C`** (little-endian `4C 00` on air). Android's
  `ScanRecord.getManufacturerSpecificData(0x004C)` returns the bytes after the company ID.
- Continuity TLVs follow: `[type][len][value…]`. Proximity Pairing is **type `0x07`**; genuine AirPods and
  Beats use **len `0x19` (25)**, so the manufacturer data is 27 bytes. Clones often use len `0x13` (19)
  (CAPod `FakeAirPods*`), so a strict `[07 19]` filter excludes them.
- The first value byte is `0x01` for every plaintext status frame, pairing mode included. AirPods also
  emit `0x07` frames with other prefixes, for example `0x07` from the **identity (public) address on
  connect**. Those are not status frames and **must be dropped**, or they create ghost devices
  ([capod#603](https://github.com/d4rken-org/capod/issues/603), `ProximityPairing.kt`).

### 2.2 Byte layout (offsets relative to the value, i.e. after `07 19`)

| Off | Field | Notes |
|---|---|---|
| 0 | Prefix `0x01` | Drop other values |
| 1–2 | **Model** | Bytes on air e.g. `0E 20`. Read little-endian for Apple's product ID (`0x200E`, as MagicPods and AirPodsDesktop do); OpenPods and CAPod compare big-endian (`0x0E20`). §2.3 |
| 3 | **Status** ("UTP") | Bitfield, §2.4 |
| 4 | **Pod batteries** | Two nibbles, 0–10 = ×10%, 15 = unknown/disconnected, >10 seen (clamp). Which nibble is left depends on the flip rule, §2.5 |
| 5 | **Charging flags (high nibble) + case battery (low nibble)** | High nibble bit0/bit1 = the two pods (swapped by flip), bit2 = case charging, bit3 unknown. Low nibble = case battery 0–10 / 15 |
| 6 | **Lid** | bit3 = 1 closed / 0 open; bits 0–2 a lid open/close event counter (AirPodsDesktop `switchCount`; Celosia calls it the Lid Open Count). Upper nibble observed `0x5` with pods in the case, `0x1` out (LinuxPods). Only trustworthy from a pod broadcasting from inside the case, §2.6 |
| 7 | **Color** | §2.6 |
| 8 | **Connection state** | `0x00` disconnected, `0x04` idle, `0x05` music, `0x06` call, `0x07` ringing, `0x09` hanging up (CAPod `HasStateDetectionAirPods`, LinuxPods PR #7). furiousMAC lists it as "suffix 0x00" (older firmware) |
| 9–24 | **Encrypted payload**, 16 bytes | AES-128, §2.7 |

Sources: [furiousMAC continuity](https://github.com/furiousMAC/continuity/blob/master/messages/proximity_pairing.md) (GPL-2.0),
Celosia & Cunche, [*Discontinued Privacy*, PETS 2020](https://petsymposium.org/popets/2020/popets-2020-0003.pdf),
CAPod `ApplePods.kt`/`DualApplePods.kt`, AirPodsDesktop `AppleCP.h`, OpenPods `PodsStatus.java`,
MagicPodsCore `AppAnimationCapability.cpp`.

### 2.3 Model IDs

Product ID is the little-endian u16 (the same ID that AAP and Apple's DI records use). The on-air bytes
at offsets 1–2 are the reverse, e.g. `0x2014` appears as `14 20`. The model numbers are what AAP's
device-info packet reports (CAPod `PodModel.modelNumbers`). IDs agree across CAPod, MagicPodsCore
(`AapModelIds.h`) and AirPodsDesktop unless noted.

| Model | PID | Earbud/headset model numbers |
|---|---|---|
| AirPods (1st gen) | `0x2002` | A1523, A1722 |
| AirPods (2nd gen) | `0x200F` | A2031, A2032 |
| AirPods (3rd gen) | `0x2013` | A2564, A2565 |
| AirPods 4 | `0x2019` | A3050, A3053, A3054 |
| AirPods 4 (ANC) | `0x201B` | A3055, A3056, A3057 |
| AirPods 5 | `0x2036` | – (added to all three projects Sept 2026) |
| AirPods 5, wireless-charging case | `0x2030` | – |
| AirPods Pro (1st gen) | `0x200E` | A2083, A2084 |
| AirPods Pro 2 (Lightning) | `0x2014` | A2698, A2699, A2931 |
| AirPods Pro 2 (USB-C) | `0x2024` | A3047, **A3048** (the Googlebook test pair), A3049 |
| AirPods Pro 3 | `0x2027` | A3063, A3064, A3065 |
| AirPods Max (Lightning) | `0x200A` | A2096 |
| AirPods Max (USB-C, 2024) | `0x201F` | A3184 |
| AirPods Max 2 | `0x202D` | A3454 |
| Powerbeats3 | `0x2003` | A1747 |
| BeatsX | `0x2005` | A1763 |
| Beats Solo3 | `0x2006` | A1796 |
| Beats Studio3 | `0x2009` | A1914 (CAPod matches only the first byte, `0x09`) |
| Powerbeats Pro | `0x200B` | A2047, A2048, A2453, A2454 (OpenPods matches first byte `0x0B`) |
| Beats Solo Pro | `0x200C` | A1881 |
| Powerbeats4 | `0x200D` | A2015 |
| Beats Flex | `0x2010` | A2295 |
| Beats Studio Buds | `0x2011` | A2512–A2514 |
| Beats Fit Pro | `0x2012` | A2576–A2578 |
| Beats Studio Buds+ | `0x2016` | A2871, A2872, A2952 |
| Beats Studio Pro | `0x2017` | A2924 |
| Powerbeats Pro 2 | `0x201D` | A3157–A3159 |
| Beats Solo 4 | `0x2025` | A3140 |
| Beats Solo Buds | `0x2026` | A3150, A3151, A3153 |
| Powerbeats Fit | `0x202F` | – (MagicPodsCore only) |
| Beats 360 | `0x2038` | A3577 (CAPod 5.4 only) |

Caveats:
- LinuxPods' doc labels `0x2420` as "AirPods Pro" and `0x0220` as "AirPods (2nd gen)". Both are wrong;
  they are Pro 2 USB-C and AirPods 1.
- Clones reuse genuine IDs with a shorter length, so key detection on the length byte as well as the ID.
- Prefer the AAP model number over the BLE ID when both are known. CAPod auto-corrects the profile model
  when AAP disagrees ([PR #465](https://github.com/d4rken-org/capod/pull/465)).

### 2.4 Status byte (offset 3)

| Bit | Meaning (consensus; "this" = the pod sending the advert) |
|---|---|
| 0 | Unknown. MagicPods calls it "two AirPods active". Set in the "both in case" example `0x55` |
| 1 | In ear (one pod, see the XOR rule) |
| 2 | **Both pods in case** |
| 3 | In ear (other pod) |
| 4 | **One pod in case** |
| 5 | **Primary is left** (1 = left is primary and mic pod, 0 = right). Clear means values are "flipped" |
| 6 | **This (broadcasting) pod is in the case** |
| 7 | Unknown |

The in-ear and mic rule (CAPod `DualApplePods`):
```
flipped   = !bit5
thisInCase = bit6
if (flipped XOR thisInCase): leftInEar = bit3, rightInEar = bit1
else:                        leftInEar = bit1, rightInEar = bit3
leftIsMic  = bit5 XOR thisInCase
rightIsMic = !bit5 XOR thisInCase
```
- AirPodsDesktop also ignores in-ear bits while a pod is charging; they glitch then.
- CAPod's settings text says that on some models ear detection is only reliable for the primary pod over
  BLE. AAP's ear-detection opcode (`0x0006`) is authoritative when connected.
- Single-battery devices (Max, Beats headphones) use the **left-pod fields**, and the flip bit signals
  "worn" (d4rken, [capod#287](https://github.com/d4rken-org/capod/issues/287)). Max has been reported as
  "always in ear" over BLE.

### 2.5 Battery nibbles, the flip rule, charging bits

- Offset 4: when **not flipped** (bit5 = 1), **left = low nibble, right = high nibble**. When flipped,
  swap them. furiousMAC describes the unflipped case: "4 MSBits are the right battery and the 4 LSbits
  are the left".
- Offset 5 high nibble: not flipped → bit0 = left charging, bit1 = right charging; flipped → swapped.
  bit2 = case charging.
- Offset 5 low nibble: case battery. The case level is only broadcast while the lid is open, i.e. a pod
  is in the case: "the battery level of the case is only exposed when the lid is open" (Celosia). Apple's
  own widget likewise shows the case "only when at least one of your AirPods is in the case"
  ([MacRumors](https://www.macrumors.com/how-to/check-the-battery-life-of-your-airpods/)).
- Values: 0–10 → 0–100% in 10% steps; 15 → unknown or absent. Values above 10 have been seen; clamp to
  100%.
- Resolution: the public data is deciles. Show a "≈" or "~10%" affordance, or switch to 1% when
  decrypted or AAP data exists. CAPod tracks `batteryCaseResolution` for this.

### 2.6 Lid, color, and what the bytes mean in each state

- **Lid (offset 6):** bit3 is 0 = open, 1 = closed. It is only meaningful when this pod is in the case
  (status bit6) or both are (bit2). A frame from the *out-of-case* pod carries a stale lid byte that
  decodes as a phantom OPEN, verified on Pro 1 and Pro 3 (CAPod `LidState.fromRaw`). CAPod recovers the
  lid state from an in-case broadcast within the last 2 s and treats a case-open popup as stale after
  4 s without a fresh OPEN. The counter bits increase with rapid open/close and reset later; Celosia
  notes they make pairs trackable.
- **Color (offset 7):** `00` white, `01` black, `02` red, `03` blue, `04` pink, `05` gray, `06` silver,
  `07` gold, `08` rose gold, `09` space gray, `0A` dark blue, `0B` light blue, `0C` yellow (Celosia
  Table 7, CAPod, AirPodsDesktop). Mostly relevant for AirPods Max and Beats colourways, for tinting
  PodLink's own illustrations.

### 2.7 The encrypted 16 bytes and the keys

- **Cipher:** AES-128 in **ECB**, decrypt the last 16 bytes of the value with the **encryption key**
  ("ENC", macOS keychain `MagicAccEncKey`). MagicPairing (Heinze et al.,
  [WiSec 2020](https://arxiv.org/pdf/2005.07255)) calls this the "MagicPairing EncryptionKey"; it is set
  up during pairing.
- **Decrypted layout** (CAPod `asBatteryState`, MagicPodsCore `ParseBle`, kavishdevar in capod#287):

  | Byte | Meaning |
  |---|---|
  | 0 | `0x04` or `0x14` (a type or flag; MagicPods accepts only these) |
  | 1 | Pod A battery: bit7 = charging, bits0–6 = **percent 0–100**, `0xFF` = disconnected, `0x00` = n/a |
  | 2 | Pod B battery, same encoding |
  | 3 | Case battery, same encoding |
  | 4–15 | Unknown (zeros in the middle when the case is absent) |

  Pod A is **left when not flipped** (CAPod reads left from pos `flipped ? 2 : 1`; MagicPods uses status
  bit5). A wrong key yields garbage rather than an error, so validate the byte-0 marker and the value
  ranges before trusting it.
- **Getting the keys without a Mac.** Over AAP, send `04 00 04 00 30 00 05 00` (opcode 0x30). The reply
  is opcode **0x31**: `data[4]=0x31`, `data[6]=count`, then per entry `type` (0x01 = IRK, 0x04 = ENC),
  `data[start+2]=len`, value at `start+4` (MagicPodsCore `AapPrivateKeysWatcher.cpp`). CAPod does this
  automatically on every AAP connect and stores both keys in the profile. Per the
  [CAPod wiki](https://github.com/d4rken-org/capod/wiki/AirPod-Keys), the IRK and ENC are the same no
  matter which host the AirPods are paired with. The manual route is the macOS keychain item
  `MobileBluetooth` → `MagicAccIRK` / `MagicAccEncKey`.
- **Why it matters for PodLink:** once AAP has run once, PodLink can go back to passive BLE (cheap, no
  L2CAP session) and still get **1% battery and unambiguous identity**, even when the AirPods are playing
  from another device.

### 2.8 Address rotation and matching adverts to *your* AirPods

- Adverts use a **resolvable private address (RPA)** that rotates. Apple devices generally rotate about
  every 15 minutes, the spec's recommended value ([Martin et al., PETS 2019](https://petsymposium.org/popets/2019/popets-2019-0057.pdf);
  Celosia notes battery values change more slowly than the address). On the Googlebook the AirPods are
  bonded **only over BR/EDR**: dumpsys shows `le_linkkey_known:F` and `in_resolving_list: F`. Android
  therefore can't resolve the RPA or link it to the bonded device, and the app has to.
- **Heuristic (no keys):** OpenPods and AirStatus take the strongest beacon in the last 10 s with RSSI of
  at least −60 dBm. CAPod matches profiles by model plus a per-profile "minimum signal quality" and has a
  "Hide unmatched devices" setting. These heuristics break in offices and on buses, when two identical
  models are nearby, and when the buds are across the room.
- **IRK (exact):** the standard BLE `ah()` check: `hash == AES-128(IRK, prand‖0¹³)[0..2]` over the RPA,
  where the top two bits of the address are `01`. CAPod `RPAChecker` also tries the reversed octet order
  to be safe. Google's [bumble `ble_rpa_tool.py`](https://github.com/google/bumble/blob/main/apps/ble_rpa_tool.py)
  is a reference. MagicPods runs `Aes::VerifyRPA` before decrypting. CAPod shows an "IRK-matched" badge
  on the card.

### 2.9 Advertising cadence and what's visible when

No authoritative interval figure is published. Evidence:

- Celosia: Proximity Pairing messages "are constantly transmitted by active Apple audio devices", but the
  status fields are "only broadcasted when the AirPods are outside the case", and the case battery only
  with the lid open.
- LinuxPods: "Advertisements update infrequently. Updates mainly take place when something happens."
  Content changes on events, even though frames repeat.
- CAPod's timeouts imply at least several frames per second during a case-open event (4 s staleness), and
  a device is dropped after 20 s without adverts. Hardware batching on some ROMs (Xiaomi) can suppress
  delivery until a state change ([#603](https://github.com/d4rken-org/capod/issues/603)).
- **Action: measure it on the Googlebook** with the probe: frames per second per state, RSSI, and the
  address rotation period.

| State | Visible over BLE |
|---|---|
| Both pods in the closed case | Essentially nothing after the lid closes. A few frames around the close event |
| Lid opened | Burst of frames from the in-case pod(s): lid bit = open, case battery valid, status bits 2/4/6 set. This triggers iOS's card and should trigger PodLink's |
| Worn, connected to *this* Android | Continues advertising: batteries, in-ear bits, connection state `0x04`/`0x05`/`0x06`. Case battery 15 (unknown) unless a pod is in the case. On connect, one odd non-`0x01` frame from the identity address |
| Worn, connected to *another* host (iPhone/Mac) | Same adverts. The connection-state byte reflects that host's activity. With the IRK, PodLink can say "in use on another device" and still show 1% battery |
| One pod in case, one worn | Both pods may advertise, and the frames alternate (worn pod: NOT_IN_CASE / stale lid; in-case pod: real lid + case). Merge across frames within about 2 s |
| Pairing mode (case button) | Still prefix `0x01`. The card on iOS shows "Connect". PodsLink claims 1% battery works only here (unverified) |
| AAP session active | LinuxPods and omarchy-pods stop BLE discovery while the control link is up (crackle on Linux). CAPod keeps both sources and merges them |

### 2.10 Scanning on Android (practical)

- Filter in hardware: `ScanFilter.setManufacturerData(0x004C, [0x07, 0x19, 0…], [0xFF, 0xFF, 0…])`.
  Offer a compatibility fallback that filters in software for ROMs that break offloaded filters.
- For background work, prefer **PendingIntent-based scans** (`BluetoothLeScanner.startScan(filters,
  settings, PendingIntent)`). They survive process death and need no continuous FGS. CAPod calls this
  "indirect delivery". Use low-power mode with a report delay when idle, and low-latency only while
  the popup or dashboard is visible.
- Stop or duty-cycle scans while AAP is connected; AAP already gives battery, ear and ANC state. This
  avoids audio side effects (dropouts on some phones, crackle on Linux) and saves battery. "Scanning
  too much" is one of OpenPods' most-discussed issues.
- Android 12+: `BLUETOOTH_SCAN` with `neverForLocation` needs no location permission and no location
  toggle.

---

## 3. UX patterns worth borrowing

### 3.1 The iOS connection card, adapted
- iOS: when the case opens near an iPhone signed into the same Apple ID, a bottom card rises with an
  animated product render, the name, and battery rings for the buds (combined, or split if they differ)
  and the case. It auto-dismisses. For unpaired buds it shows a "Connect" button.
- Android equivalents: Google Fast Pair's half-sheet and battery notification, and the Pixel Buds battery
  row with L / R / case icons. PodLink should follow **that** Material grammar rather than clone iOS.
- Implementation lessons from CAPod `PopUpReaction`:
  - trigger only on a **fresh OPEN from an in-case pod** that is **IRK-matched**, or one that passes the
    profile heuristic;
  - 10 s cooldown; hide after 4 s without a fresh OPEN; don't show while the app is in front;
  - a separate "connection popup" on the first connect;
  - it's an overlay (`SYSTEM_ALERT_WINDOW`).
- **Googlebook:** the status bar doesn't show per-app notification icons. A **Live Update chip**
  (`POST_PROMOTED_NOTIFICATIONS`, one visible, about 7 characters, hidden while the app is on top)
  is the only persistent in-bar surface. For the case-open moment, use a fixed-width card near the top
  right, **not** width-proportional. LibrePods' island filled a third of a tablet screen
  ([#425](https://github.com/librepods-org/librepods/issues/425), [#657](https://github.com/librepods-org/librepods/issues/657)).
  Popups must never be hover-triggered (user preference).

### 3.2 Showing three battery levels
- Show a **combined "Buds" value** when both are out and within about 10%; split **L / R** when they
  differ or one is in the case. This is what iOS does: "If you take out an AirPod, you'll see individual
  percentages".
- Show **Case** only when a pod is in it or the lid was recently open. Otherwise show the last known
  value with a "last seen 12 min ago" timestamp.
- Mark the charging state (bolt), the source (BLE ~10% / encrypted 1% / AAP 1%) and staleness. CAPod
  greys cached values and hides the charging indicator when out of range
  ([PR #625](https://github.com/d4rken-org/capod/pull/625)).
- CAPod 4.0 moved to **circular gauges** per battery and progress bars in the expanded notification.
  AndroPods draws the % into the notification small icon; that's not useful on Googlebook, where icons
  aren't shown.
- Estimates (CAPod 5.2): remaining listening time, time to full and **battery health as % of rated
  listening time**, seeded from Apple's published hours per model (`BatterySpec`: Pro 3 8 h ANC on,
  Pro 2 6 h, AirPods 4 5 h, 4 ANC 4 h, Max 20 h).

### 3.3 Reactions: CAPod's per-device reaction settings are the best checklist
- **Auto pause** on removal.
- **Auto play only if auto-pause stopped it.** Manual, stem and sleep pauses stay paused.
- **Start music on wear**, even after a manual pause.
- **One-pod mode**: react to a single pod.
- **Auto connect** with conditions: *when seen* / *case open* / *in ear*. The Android 12+ restriction
  notice appears only when relevant.
- Case-open popup, connection popup.
- **Charged notification** with target level and scope (pods / case / both).
- **Conversation awareness action**: nothing / lower volume by X / pause.
- **Sleep detection**: pause and notify.
- **Press controls** (stem long-press actions; note that long-press actions disable ANC cycling).
- Keep the notification after disconnect; an optional second notification only while connected.
- Reactions moved from global to **per device** in 5.1 ([PR #503](https://github.com/d4rken-org/capod/pull/503)).

Also worth borrowing:
- **Per-app noise automations** (PodWing).
- **Auto-switch audio to the laptop speakers when both buds come out** (MagicPods). That fits a laptop
  well, but check whether Android 17 lets apps route this.
- Low-battery alert at a chosen threshold.
- Hotkeys (MagicPods). On Googlebook, prefer in-app shortcuts, the QS tile and the chip over any
  accessibility-service key capture; the user wants input access least-privilege.

### 3.4 Quick Settings tile and widgets
- The CAPod `AncTileService` **cycles** visible modes on tap, with **1 s debounce** and an **optimistic
  "pending" state**, and uses subtitle states: "Permission required", "No device", "No noise control",
  "Bluetooth off".
- Widgets: a battery widget and a separate **ANC widget** (Glance), with presets (Material You / dark /
  light / colours), a transparency slider and a "show device name" option. A LibrePods fork prototypes
  responsive 2×1 / 2×2 / 3×2 layouts ([#772](https://github.com/librepods-org/librepods/issues/772)).
  Check whether Googlebook OS offers home-screen widgets on the desktop before investing heavily.

### 3.5 Model illustrations without Apple's renders
- **CAPod and OpenPods ship Apple-marketing-style photo renders** (`device_airpods_pro2_case.png` and
  `podpro_case.png` show the open Pro case with its green LED). CAPod explicitly **excludes icons and
  images from its GPL**. LibrePods separates Apple images into a `res-apple` source set.
  AirPodsDesktop and MagicPods ship per-model iOS-style popup **videos**. All of this carries copyright
  risk (§4).
- CAPod's lesson ([#62](https://github.com/d4rken-org/capod/issues/62)): realistic images don't tint
  well under dynamic colour. Complex SVG traces are slow ("Very long vector path" warnings).
  **Monochrome, simple vectors** work best in notifications and widgets.
- **For PodLink:** draw original line art per **form factor**, not per SKU: stem buds (AirPods 1–5),
  short-stem with ear tip (Pro), stemless buds (Beats buds), ear-hook (Powerbeats), over-ear (Max, Beats
  headphones), and case variants. Tint by the colour byte. Material Symbols (Apache-2.0) already covers a
  lot: `earbuds`, `earbuds_2`, `earbud_left`, `earbud_right`, `earbud_case`, `earbuds_battery`,
  `headphones`, `headphones_battery`, `noise_control_on`, `noise_control_off`, `noise_aware`,
  `ear_sound`, `spatial_audio`, `spatial_tracking`, `hearing`.

### 3.6 Onboarding and permissions
- Minimal set on Android 12+:
  - `BLUETOOTH_SCAN` (neverForLocation) and `BLUETOOTH_CONNECT`, with no location needed;
  - `POST_NOTIFICATIONS`;
  - `POST_PROMOTED_NOTIFICATIONS` for the chip (a normal permission);
  - overlay only if the user turns on the case popup.
  Older apps still onboard with "location permission is needed for BLE" (AndroPods, AirBuds Popup),
  which users distrust.
- Use a **CompanionDeviceManager association** as the "pick your AirPods" step. It gives:
  - `REQUEST_COMPANION_RUN_IN_BACKGROUND`, `…_USE_DATA_IN_BACKGROUND` and
    `…_START_FOREGROUND_SERVICES_FROM_BACKGROUND`;
  - on Android 16+, `startObservingDevicePresence(ObservingDevicePresenceRequest)`, which **binds your
    `CompanionDeviceService` while the device is in BLE range or connected over Bluetooth**
    ([docs](https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing)).

  This replaces the battery-optimisation-exemption request that CAPod and OpenPods rely on (§4.4). No
  surveyed app uses CDM, so it's a differentiator.
- A **capability check** screen:
  - does the AAP socket open? If not, say it's a platform or OS-version limitation, not the user's
    hardware (CAPod PR #513 wording);
  - link a compatibility note;
  - detect **conflicting AAP apps** (LibrePods, CAPod, PodsLink…) and explain that only one can connect;
  - OEM tips, e.g. Xiaomi `com.xiaomi.bluetooth`.
- A troubleshooter and a **debug-log recorder** with redaction (CAPod). Most AirPods bugs are ROM or
  firmware specific and can't be fixed without logs.

### 3.7 Feature gating per model
Drive the UI from **capabilities**, not model names. omarchy-pods builds its panel from capability keys
the daemon publishes. AAP sends a capability list (opcode `0x0002`) plus the current value of every
setting (`0x0009`) on connect (device-findings.md). CAPod's static fallback (`PodModel.Features`):

| Capability | Models (CAPod flags) |
|---|---|
| Listening-mode control | Pro, Pro 2 (both), Pro 3, AirPods 4 ANC, AirPods 5 (both), Max (all), Beats Solo Pro, Studio3, Studio Buds, Studio Buds+, Studio Pro, Beats 360, Powerbeats Pro 2, Fit Pro |
| Adaptive + conversation awareness + adaptive noise level | Pro 2 (both), Pro 3, AirPods 4 ANC, AirPods 5, Max 2 |
| "Off" mode | Hidden unless **Allow Off** is enabled on Pro 2/Pro 3 and others (AAP setting `0x34`). Apple support: Off "only after you enable it" |
| ANC with one pod | Pro, Pro 2, Pro 3, AirPods 4 ANC |
| Volume swipe | Pro 2 (both), Pro 3, AirPods 5 wireless-case variant |
| Sleep detection | AirPods 4 / 4 ANC, Pro 2 (both), Pro 3, Powerbeats Pro 2 |
| Optimized charge limit (`0x3B`) | Pro 3 |
| Ear detection | All AirPods incl. Max, Powerbeats Pro 1/2, Fit Pro |
| Case / dual pods | All buds. Beats Solo Buds' case has **no battery** |

Other gating notes:
- Tell users "Changes take effect when AirPods are in ear" for settings that need wear.
- Grey out the adaptive noise slider outside Adaptive.

---

## 4. Trademark and store risks

### 4.1 Apple's rules ([Guidelines for Using Apple Trademarks](https://www.apple.com/legal/intellectual-property/guidelinesfor3rdparties.html))
- **Compatibility wording is allowed:** you may use an Apple word mark "in a referential phrase such as
  'runs on,' 'for use with,' 'for,' or 'compatible with'" if:
  - (a) the mark "is not part of the product name";
  - (c) it "appears less prominent than the product name";
  - (d) the product really is compatible;
  - (e) there is no "sense of endorsement, sponsorship, or false association".
- **Not allowed:**
  - using an Apple trademark "in whole or in part … as or as part of a company name, trade name,
    product name";
  - any "variation, phonetic equivalent, … takeoff, or abbreviation of an Apple trademark" (examples:
    "Appletree", "iPodMart");
  - the Apple logo or "any other Apple-owned graphic symbol, logo, or icon";
  - imitating Apple's "packaging, web site design, logos, or typefaces".
- **Images:** product images may be shown only if "an actual photograph of the genuine Apple product and
  not an artist's rendering", and Apple-owned photos need "express written permission". **Don't ship
  Apple renders or iOS animations.**
- **Credit line:** "AirPods is a trademark of Apple Inc., registered in the U.S. and other countries and
  regions." Outside the US, drop the ™/® symbols.
- The [Apple trademark list](https://www.apple.com/legal/intellectual-property/trademark/appletmlist.html)
  includes AirPods®, AirPods Pro®, AirPods Max®, EarPods®, **Find My®**, **Live Listen®**, Siri®, and
  Beats marks (referred to Beats Electronics, LLC). Avoid "Find My" and "Live Listen" as feature names;
  use "Locate" / "Play a sound" / "Listen through mic".
- **"Pod" names:** Apple sent cease-and-desists over "Podium"/"Flypod" (2009) and "Podcast Ready" (2006)
  in the iPod era ([AppleInsider](https://appleinsider.com/articles/09/03/18/apple_issues_cease_and_desist_over_podium_flypod_marks.html),
  [MacRumors](https://www.macrumors.com/2006/09/25/podcast-trademark-controversy/)). Many current
  companion apps use "Pods" without visible action (CAPod, OpenPods, MaterialPods, AndroPods, LibrePods,
  PodWing, PodsLink), so the risk is real but has not been enforced lately.

### 4.2 How existing listings word it

| App | Title | Disclaimer |
|---|---|---|
| CAPod | "CAPod - Companion for AirPods" | None in the Play text ("Most popular AirPods and Beats devices are supported") |
| PodsLink | "PodsLink - AirPods Battery" | "AirPods, AirPods Pro, and AirPods Max are trademarks of Apple Inc. / Beats, BeatsX, and Powerbeats Pro are trademarks of Beats Electronics, LLC." |
| AirPods on Android (DenoSoft) | "AirPods on Android" (mark *in* the name; risky) | "independent third-party application and is not provided, endorsed, sponsored, or affiliated with any device manufacturer" plus TM lines |
| PodWing | "PodWing - AirPods companion" | "independent app and is not affiliated with, endorsed by, or sponsored by Apple. AirPods and Beats are trademarks of Apple Inc." |
| Wunderfind | "Wunderfind: Find Lost Device" | "We are not affiliated with the mentioned companies… AirPods is a trademark of Apple Inc., registered in the U.S. and other countries." |
| PodsCompanion (GitHub) | "Pods Companion for Android™" | "AirPods is a trademark of Apple Inc." |
| winpods (GitHub) | – | "winpods is not affiliated with Apple Inc. AirPods is a trademark of Apple Inc." |

Suggested pattern for PodLink (or its final name):
- Title: `<Name> – earbuds companion`. Put "for AirPods & Beats" in the **short description**.
- Full description ends with: "<Name> is an independent app. It is not affiliated with, endorsed by or
  sponsored by Apple Inc. AirPods, AirPods Pro, AirPods Max and Beats are trademarks of Apple Inc.,
  registered in the U.S. and other countries and regions."
- Icon: no Apple glyphs and no white-stem-earbud silhouette that mimics Apple's product art.
- The same line goes in the in-app About screen. This also matches the user's passion-project README
  conventions.

### 4.3 Play policies that bite
- **Impersonation** ([policy](https://support.google.com/googleplay/android-developer/answer/9888374)):
  "App titles and icons that are so similar to those of existing products or services that users may be
  misled" count as violations. **"PodLink" vs "PodsLink" (same category, 1M+) is exactly this risk**, and
  it invites reviews confusing the two.
- **Intellectual property** ([policy](https://support.google.com/googleplay/android-developer/answer/9888072)):
  no "improper or unauthorized use of an identical or similar trademark in a way that is likely to
  cause confusion". All content must be "your own original work or … licensed". This covers product
  renders and iOS animations in screenshots.
- **Battery-optimisation exemption:** "Google Play policies prohibit apps from requesting direct
  exemption … unless the core function of the app is adversely affected". The acceptable-use table marks
  apps that "only need to connect to devices, such as wireless headphones, connected via standard
  Bluetooth profiles" as **Not Acceptable**
  ([docs](https://developer.android.com/training/monitoring-device-state/doze-standby#exemption-cases)).
  OpenPods deliberately violates this; CAPod declares the permission anyway. **PodLink: use CDM plus
  PendingIntent scans instead.**
- **Foreground service type** `connectedDevice` needs the Play Console FGS declaration. Keep the FGS
  short-lived: only while AAP is connected or a popup is pending.
- **Hidden (non-SDK) API use:** there is no explicit Play rule against it, and CAPod, LibrePods,
  PodsLink and others ship it on Play today. The runtime can block it in any update, so treat the
  hidden path as fragile. Keep BLE-only features fully functional, and watch for a public BR/EDR L2CAP
  API.
- **App optimisation:** Play Console now warns when obfuscation is 0% ("fix by Feb 2027";
  [CAPod PR #706](https://github.com/d4rken-org/capod/pull/706)). Enable R8 obfuscation and keep the
  reflection targets.
- **Account risk:** d4rken's whole account was terminated in 2023 on a false malware flag and restored
  after 19 days. Keep a non-Play release channel (GitHub releases / F-Droid), as the user's other
  projects do.

### 4.4 Removals and takedowns found
- **AirBattery** (Elisabeth Friedrich): 7.2M downloads, 2.56★, last update 2023-04-09, **removed
  2025-05-09**. No public reason. The timing fits abandonment and target-API enforcement, not a
  trademark action.
- "AirBattery | An AirPod Battery App" (Dozzby) was removed 2021-08-13, and "AirBattery - AirPods Pro
  Battery Level" (Satpda) 2022-08-15, per AppBrain. No reasons given.
- **OpenPods reuploads** were removed after the author's DMCA notices (GPL violations, 2019–2022).
- The **PodsCompanion** listing is gone (404). The repo was archived in 2020.
- **No evidence was found of Apple-initiated takedowns** of AirPods companion apps on Play. Even titles
  like "Apple Airpods for Android" remain listed. That's weak enforcement, not permission.

### 4.5 Target API filter (affects what Googlebook users see)
Play: "Existing apps must target Android 15 (API level 35) or higher to remain available to new users on
devices running Android OS higher than your app's target API level". Updates must target API 36 from
2026-08-31 ([policy](https://support.google.com/googleplay/android-developer/answer/11926878)). Apps last
updated in 2022–2024 therefore very likely don't appear for new users on Android 17 Googlebooks
(Assistant Trigger, PodAir, AirBuds Popup, Wunderfind, AirPod Battery). That leaves CAPod, PodsLink,
MaterialPods, AndroPods, PodsBattery, DenoSoft, AirPro, PodWing, LibrePods and BudWave.

### 4.6 License implication for PodLink
The code worth studying is GPL-3.0: CAPod, OpenPods, PodsCompanion, LibrePods, MagicPodsCore,
AirPodsDesktop, AirStatus, kAirPods, LinuxPods and the GNOME extension. furiousMAC's continuity docs are
GPL-2.0.

For a permissively licensed PodLink:
- **Take facts, not code.** Byte layouts, opcodes and model IDs are not copyrightable, but don't copy or
  translate source files, tests or resources.
- Permissive references: omarchy-pods (MIT), winpods (MIT), LSPosed HiddenApiBypass (Apache-2.0),
  Google bumble (Apache-2.0) for RPA math, Material Symbols (Apache-2.0).

---

## 5. AirPods on ChromeOS, desktop Android and Googlebooks

- **ChromeOS:** it shows battery for some BT devices in the tray, but not per bud and with no case card.
  Users sideload CAPod into ARC ([Chrome Unboxed](https://chromeunboxed.com/chromebook-chrome-os-bluetooth-battery-level-how-to/)).
  ARC's Bluetooth sits behind the ChromeOS BlueZ stack, so AAP was never an option there.
- **Googlebook (Android 17 native)** is better placed, per the read-only checks on 2026-09-30 and
  device-findings.md:
  - HP Googlebook 14: arm64, SDK 37, BT mainline module
    `com.google.android.bt` 371899999;
  - AirPods Pro (A3048) bonded BR/EDR only and active for A2DP and HFP;
  - Settings shows a **single HFP battery value**, with no L/R/case split and no Fast-Pair-style
    metadata for AirPods;
  - **the AAP socket opens without root.** A Googlebook gets full features out of the box, which most
    phones in 2026 still don't (Samsung A16 etc.).
- **Desktop UI gap:**
  - CAPod has no adaptive layouts.
  - LibrePods' popup scales with window width, and tablet users complain
    ([#425](https://github.com/librepods-org/librepods/issues/425), [#657](https://github.com/librepods-org/librepods/issues/657)).
    A fork prototypes list-detail and foldable layouts ([#772](https://github.com/librepods-org/librepods/issues/772)).
  - No Android app handles resizable windows, the desktop status bar or keyboard use.

  PodLink should:
  - use window size classes / list-detail (a device list plus a detail pane), fixed-width popups (an
    overlay `WRAP_CONTENT` window is first measured at 580 dp on Googlebook), and a header row under the
    system caption;
  - use a **Live Update chip** for in-bar battery (≤ 7 chars, e.g. `82·79%`) and keep a QS tile for ANC;
  - provide keyboard shortcuts inside the app;
  - not rely on the notification small-icon number trick.
- **Desktop-only ideas from Windows and Linux peers:**
  - **Auto-switch output to speakers when both buds are out** (MagicPods);
  - low-latency mode (AirPodsDesktop);
  - a "Deliberately absent" list, i.e. don't duplicate the system audio and Bluetooth panels
    (omarchy-pods);
  - never hold continuous discovery when the buds are away (omarchy-pods #64: it blocked other bonded LE
    devices such as mice from reconnecting on Linux). On a laptop full of BT peripherals the analogous
    Android risk is radio contention. Use low duty cycles.
- **Performance on x86_64 and arm64:** nothing native is needed. All of this is Kotlin plus framework
  BLE/L2CAP.

---

## Sources

Code and issues:
- CAPod: https://github.com/d4rken-org/capod (README, `PodModel.kt`, `ble/protocol/*`,
  `ble/devices/*`, `aap/engine/AapConnection.kt`, `common/bluetooth/l2cap/L2capSocketFactory.kt`,
  `reaction/core/popup/PopUpReaction.kt`, `main/ui/tile/AncTileService.kt`, `res/values/strings.xml`);
  issues [#62](https://github.com/d4rken-org/capod/issues/62), [#155](https://github.com/d4rken-org/capod/issues/155),
  [#215](https://github.com/d4rken-org/capod/issues/215), [#287](https://github.com/d4rken-org/capod/issues/287),
  [#538](https://github.com/d4rken-org/capod/issues/538), [#603](https://github.com/d4rken-org/capod/issues/603);
  PRs [#464](https://github.com/d4rken-org/capod/pull/464), [#513](https://github.com/d4rken-org/capod/pull/513),
  [#706](https://github.com/d4rken-org/capod/pull/706); releases v4.0.0–v5.4.0; wiki
  [AirPod Keys](https://github.com/d4rken-org/capod/wiki/AirPod-Keys)
- OpenPods: https://github.com/adolfintel/OpenPods (`PodsStatus.java`, `PodsStatusScanCallback.java`),
  issues #24, #27, #81, #138
- PodsCompanion: https://github.com/Domi04151309/PodsCompanion
- AirPodsDesktop: https://github.com/SpriteOvO/AirPodsDesktop (`Source/Core/AppleCP.{h,cpp}`)
- MagicPodsCore: https://github.com/steam3d/MagicPodsCore (`sdk/aap/*`,
  `device/capabilities/aap/AppAnimationCapability.cpp`); MagicPods: https://github.com/steam3d/MagicPods-Windows,
  https://magicpods.app
- AirStatus: https://github.com/delphiki/AirStatus · LinuxPods: https://github.com/mstroecker/LinuxPods
  (PR #7, docs/ble-proximity-pairing.md) · omarchy-pods: https://github.com/thisisgm/omarchy-pods (#64) ·
  kAirPods: https://github.com/can1357/kAirPods · winpods: https://github.com/sinanovicanes/winpods
- LibrePods issues #425, #657, #758, #772: https://github.com/librepods-org/librepods
- PodsLink help site: https://github.com/wbshm/podslink-help
- furiousMAC continuity: https://github.com/furiousMAC/continuity/blob/master/messages/proximity_pairing.md
- Google bumble RPA tool: https://github.com/google/bumble/blob/main/apps/ble_rpa_tool.py

Papers:
- Celosia & Cunche, *Discontinued Privacy*, PETS 2020: https://petsymposium.org/popets/2020/popets-2020-0003.pdf
- Martin et al., *Handoff All Your Privacy*, PETS 2019: https://petsymposium.org/popets/2019/popets-2019-0057.pdf
- Heinze et al., *MagicPairing*, WiSec 2020: https://arxiv.org/pdf/2005.07255

Store listings (fetched 2026-09-30):
- CAPod https://play.google.com/store/apps/details?id=eu.darken.capod
- AndroPods https://play.google.com/store/apps/details?id=pro.vitalii.andropods
- MaterialPods https://play.google.com/store/apps/details?id=com.pryshedko.materialpods
- PodsLink https://play.google.com/store/apps/details?id=net.podslink
- PodsBattery https://play.google.com/store/apps/details?id=com.yugongkeji.podstool
- AirPods on Android https://play.google.com/store/apps/details?id=com.sumyapplications.bluetooth.earphone
- AirPro https://play.google.com/store/apps/details?id=com.evotap.airpod
- PodWing https://play.google.com/store/apps/details?id=dk.nickn.podwing
- Assistant Trigger https://play.google.com/store/apps/details?id=com.dotarrow.assistantTrigger
- AirBuds Popup https://play.google.com/store/apps/details?id=kr.pe.designerj.airbudspopup
- Wunderfind https://play.google.com/store/apps/details?id=com.hf.findlostdevice
- LibrePods https://play.google.com/store/apps/details?id=me.kavishdevar.librepods
- Beats (Apple) https://play.google.com/store/apps/details?id=com.apple.bnd
- AirBattery (removed) https://www.appbrain.com/app/airbattery/friedrich.georg.airbattery

Policy, legal and docs:
- Apple trademark guidelines https://www.apple.com/legal/intellectual-property/guidelinesfor3rdparties.html
  and list https://www.apple.com/legal/intellectual-property/trademark/appletmlist.html
- Play Impersonation https://support.google.com/googleplay/android-developer/answer/9888374 ·
  IP https://support.google.com/googleplay/android-developer/answer/9888072 ·
  Target API https://support.google.com/googleplay/android-developer/answer/11926878
- Doze exemptions https://developer.android.com/training/monitoring-device-state/doze-standby#exemption-cases
- Companion device pairing https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing
- BluetoothSocketSettings.Builder https://developer.android.com/reference/android/bluetooth/BluetoothSocketSettings.Builder
- Android Authority on LibrePods going root-free: https://www.androidauthority.com/librepods-using-airpods-with-android-unlock-3661340/
- Apple "pod" C&Ds: https://appleinsider.com/articles/09/03/18/apple_issues_cease_and_desist_over_podium_flypod_marks.html ·
  https://www.macrumors.com/2006/09/25/podcast-trademark-controversy/
- Podlink (podcast links): https://podnews.net/update/podlink-gathright
- iOS battery display: https://www.macrumors.com/how-to/check-the-battery-life-of-your-airpods/
- Apple listening modes (Off needs enabling): https://support.apple.com/guide/airpods/switch-between-noise-control-modes-dev9812f5cc3/web
