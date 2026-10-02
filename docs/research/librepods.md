# LibrePods: research notes for PodLink

Researched 2026-09-30. Sources are the LibrePods GitHub repo (all four branches, cloned at the commits
below), its issues and PRs, AOSP Bluetooth sources, and web reporting (section 4.5). "main" means
`librepods-org/librepods@d2dfa2f` unless noted. Paths are relative to the repo root.

| Branch | Head | Date | What it is |
|---|---|---|---|
| `main` | [`d2dfa2f`](https://github.com/librepods-org/librepods/tree/d2dfa2fb344eef0fa7e72eaf8110000643cf0318) | 2026-09-30 | Released Android app (v1.0.0-rc1/rc2 line), old Qt/C++ Linux app, protocol docs |
| `android/rewrite` | [`c1c8d67`](https://github.com/librepods-org/librepods/tree/c1c8d6791fdc9396bde404dd8eca19696db7efed) | 2026-09-23 | Android rewrite: typed packet classes, multi-device, protobuf head tracking, heart rate, mic recording |
| `linux/rust` | [`672e65a`](https://github.com/librepods-org/librepods/tree/672e65ad36eebf21ff1c1a508066f9197ee56d17) | 2026-05-15 | Rust (BlueZ) Linux rewrite (also has basic Nothing earbuds support over ATT, `src/devices/nothing.rs`); still carries the old `head-tracking/` Python tools |
| `windows/hearing-aid` | `76206e5` | 2025-12-08 | One Python (Google Bumble) hearing-aid script |

---

## Key takeaways for PodLink

1. **Root is not needed on our target.** The Android bug LibrePods works around (the L2CAP channel-mode
   check `l2c_fcr_chk_chan_modes` in the Bluetooth stack) is fixed in Android 17 and in the Pixel
   Android 16 QPR3 Bluetooth module. LibrePods itself treats every SDK ≥ 37 device as supported
   ([`RootlessSupport.kt`](https://github.com/librepods-org/librepods/blob/d2dfa2f/android/app/src/main/java/me/kavishdevar/librepods/utils/RootlessSupport.kt#L25-L39)).
   Our own probe on the HP Googlebook 14 (Android 17, CL3B build) opened PSM 0x1001 without root
   ([device-findings.md](device-findings.md)). What still needs root: the **vendor-ID (DID) spoof**
   behind multipoint/auto-switching, transparency customization, hearing aid and loud-sound reduction.
2. **The one non-public call** is creating a classic L2CAP `BluetoothSocket`. LibrePods reflects on the
   hidden `BluetoothSocket` constructors after exempting them from hidden-API checks via
   `VMRuntime.setHiddenApiExemptions` in JNI. The public API-36 `BluetoothSocketSettings` path rejects
   `TYPE_L2CAP` (classic) with `invalid socketType - 3` (our probe).
3. **The license is GPL-3.0-or-later, for everything.** Copying any LibrePods Kotlin/C++ makes PodLink
   GPL-3.0-or-later. The permissive-license preference means **clean-room reimplementation from the
   protocol facts** below (byte layouts and opcodes are facts, not expression). Do not copy their
   code, their prose docs verbatim, their assets (SF Pro font, AirPods PNG renders, MP4 popups) or
   use the LibrePods name/logo (trademark notice).
4. **Protocol is simple and well mapped** (section 3). Head tracking in the new rewrite goes through
   an "RTBuddy" protobuf envelope on opcode 0x17. Parse it that way, not at fixed byte offsets.
5. **Head gestures** use a hand-tuned peak/trough + rhythm + axis-isolation scorer
   (section 5, ~400 lines, which the README says is AI-generated). It is only used to answer or decline
   *phone* calls. Googlebooks have no telephony, so PodLink would need its own use for it (VoIP/Meet
   answer, media, or a "confirm" gesture).
6. **Beware a naming collision.** "PodsLink" is an existing AirPods-on-Android app with its own AAP
   implementation (cited in LibrePods [#713](https://github.com/librepods-org/librepods/issues/713)).
   [`zondaxxx/podlink`](https://github.com/zondaxxx/podlink) ("Podlink", package `dev.podlink`, Sept 2026)
   is also an AirPods companion.

---

## 1. Org inventory

The `librepods-org` org was created 2026-06-24 and has **one public repository**.

| Repo | License (SPDX) | Primary language | Last push | Stars | Forks | Open issues+PRs |
|---|---|---|---|---|---|---|
| [librepods-org/librepods](https://github.com/librepods-org/librepods) | **GPL-3.0-or-later** (GitHub detects `GPL-3.0`) | Kotlin | 2026-09-30 15:11 UTC | 30,100 | 1,772 | 317 (218 issues) |

- **Old names redirect.** `gh api repos/kavishdevar/librepods` and `repos/kavishdevar/aln` both resolve to
  `librepods-org/librepods`: the repo was transferred and GitHub redirects. The README badges and some
  links still point at `kavishdevar/librepods`. The project was created 2024-09-26 as "ALN / AirPods Like
  Normal".
- **Languages** (bytes): Kotlin 1,207,714 · C++ 166,057 · C 117,164 (vendored xz) · Python 28,990 · QML 24,523 · CMake 3,843.
- **Activity.** 647 commits on `main`. Contributors: kavishdevar 486, tim-gromeyer 73, devnoname120 14, then a
  long tail. There are 40+ open PRs from the community as of 2026-09-30, including many Android fixes.
- **Releases.** `v1.0.0-rc1` (2026-06-20, latest non-prerelease; ~35k APK downloads), nightlies
  (last `nightly-53679cc`, 2026-08-31), `linux-v0.1.0` (2025-11-10). Also on Google Play as
  `me.kavishdevar.librepods`. The Play flavor has `minSdk 36`, the FOSS flavor `minSdk 33`.

### License details

- `LICENSE` is the verbatim GPLv3 text. The README "License" section and the per-file headers say
  *"either version 3 of the License, or any later version"*, so the effective SPDX is **GPL-3.0-or-later**.
  Header coverage: 77 of 108 `.kt` files on `main`, 65 of 203 on `android/rewrite`. Files without a
  header fall under the repo LICENSE.
- There are no SPDX tags in first-party files. Third-party code bundled inside:
  - `android/app/src/main/cpp/xz/*`: xz-embedded, `SPDX-License-Identifier: 0BSD`.
  - `linux/thirdparty/QR-Code-generator`: Project Nayuki, MIT.
  - Some Compose UI components are "borrowed from Kyant0's AndroidLiquidGlass catalog (Apache-2.0)",
    per the README.
  - `android/app/src/main/res-apple/font/sf_pro.otf` is the **Apple SF Pro font (proprietary)**. The
    README says it will be removed.
  - `res-apple/drawable/airpods_pro_2*.png` (AirPods renders) and `res/raw/{connected,island}.mp4`
    (popup animations) have unclear provenance. Do not reuse them.
- **Trademark notice.** "The GPL does not grant any rights to use the LibrePods name, logo, or branding."
- **Monetization.** The Play build paywalls *conversational awareness, head gestures and the Apple-style
  UI*. [#648](https://github.com/librepods-org/librepods/issues/648) and
  [#556](https://github.com/librepods-org/librepods/issues/556) show the maintainer saying the app disables
  CA and head gestures when not upgraded. The FOSS build's "purchase" opens GitHub Sponsors and unlocks
  after 5 s (`billing/FOSSBillingProvider.kt`).

### Related projects (for licensing context)

| Project | License | Note |
|---|---|---|
| [tyalie/AAP-Protocol-Defintion](https://github.com/tyalie/AAP-Protocol-Defintion) | none (all rights reserved) | First AAP write-up (2021) |
| [pabloaul/apple-wireshark](https://github.com/pabloaul/apple-wireshark) | GPL-3.0 | Wireshark dissectors for AAP and friends; README points here for the protocol |
| [d4rken-org/capod](https://github.com/d4rken-org/capod) | GPL-3.0 (icons/docs/translations excluded) | AirPods companion: BLE, plus rootless AAP over L2CAP since 2026-03-30; LibrePods' README: "Use this if you're using Android version 16 QPR3 or below and are not rooted" |
| [LSPosed/AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass) | Apache-2.0 | What our probe uses; safe to depend on |

---

## 2. Feature matrix

Legend (from the README): ✅ works · ⚪ needs VendorID (DID) spoofing, which on Android means root/Xposed ·
🟡 experimental (`android/rewrite`) · 🔴 planned · ⛔ won't do · ❓ unknown.
Column "Mechanism" gives the AAP opcode or control-command ID (section 3).

| Feature | Android | Linux | Mechanism | Models (per `data/AirPods.kt`) |
|---|---|---|---|---|
| Battery L/R/case (+ charging state) | ✅ | ✅ | opcode 0x04 push; BLE 0x07 advert as fallback (section 6) | all. **Max broken on `main`**: parser needs exactly 3 entries ([#783](https://github.com/librepods-org/librepods/issues/783); fix in PR [#767](https://github.com/librepods-org/librepods/pull/767)) |
| Listening mode Off/ANC/Transparency/Adaptive | ✅ | ✅ | control 0x0D = 1/2/3/4 | Pro 1 (no Adaptive), Pro 2, Pro 3, 4 ANC, Max (Off/ANC/T) |
| "Off" option | ✅ | ✅ | control 0x34 ALLOW_OFF_OPTION = 1 must be set before 0x0D=1 works on recent firmware | same |
| Long-press listening-mode cycle | ✅ | ❓ | control 0x1A bitmask Off=0x01, ANC=0x02, T=0x04, Adaptive=0x08 (app pref `long_press_byte`, default 0b0111) | Pro 1/2/3, 4 ANC |
| Press-and-hold action (modes vs assistant) | ✅ | 🔴 | App intercepts presses (control 0x39 RAW_GESTURES bitmask → opcode 0x19 events) and runs actions itself. Siri-on-hold on the bud is 0x16=0x05 (documented, unused) | stem models |
| Custom single/double/triple/long press actions | ✅ | 🔴 | 0x39 bitmask single 0x01, double 0x02, triple 0x04, long 0x08; app maps to play/pause/next/prev/assistant/cycle ANC | stem models |
| Conversational awareness toggle + ducking | ✅ (paywalled on Play) | ✅ | toggle control 0x28 (1 on / 2 off); speaking events opcode 0x4B; app lowers STREAM_MUSIC itself | Pro 2, Pro 3, 4 ANC, Max 2 |
| Adaptive audio level ("more/less noise") | ✅ | ✅ | control 0x2E, 0–100 | Pro 2/3, 4 ANC |
| Personalized (adaptive) volume | ✅ | 🔴 | control 0x26 (1/2) | Pro 2/3, 4, 4 ANC |
| Ear detection + auto pause/play | ✅ | ✅ | opcode 0x06 push; config toggle 0x0A; app sends media keys | all buds |
| Rename | ✅ (re-pair to see it) | ✅ | opcode 0x1A | all |
| Head gestures (nod = answer, shake = decline) | ✅ (paywalled on Play) | ⛔ | opcode 0x17 sensor stream + app-side detector (section 5) | AirPods 3, 4, 4 ANC, Pro 2, Pro 3 |
| Hearing aid (audiogram, enable) | ⚪ | 🔴 (Python script only) | control 0x2C (enrolled, enabled) and 0x33; audiogram via **ATT** handle 0x2A | Pro 2, Pro 3 |
| Transparency customization (amplification, balance, tone, conversation boost, ambient noise reduction, EQ) | ⚪ | 🔴 | **ATT** handle 0x18 (little-endian floats) + opcode 0x53 | Pro 2, Pro 3 |
| Loud sound reduction | ⚪ | 🔴 | **ATT** handle 0x1B | Pro 2, Pro 3, Max 2 |
| Hearing protection (PPE) | ✅ | 🔴 | control 0x37 toggle, 0x38 cap level | Pro 3 |
| Multi-device / automatic switching ("Move to iPhone" banners) | ⚪ | ⚪ | control 0x06 OWNS_CONNECTION, opcodes 0x0E audio source, 0x2E connected devices, 0x10/0x11 smart routing | all with AAP |
| Accessibility: press speed, hold duration, one-bud ANC, volume swipe, swipe speed | ✅ | 🔴 | 0x17, 0x18, 0x1B, 0x25, 0x23 | Pro 2/3 (swipe), stem models |
| Call controls (press once/twice to answer/mute) | ✅ | 🔴 | control 0x24, 2 bytes (default `00 03`) | stem models |
| Microphone side (auto/L/R) | ✅ | 🔴 | control 0x01: 0 auto, 1 right, 2 left | all |
| Sleep detection (pause when falling asleep) | ✅ (needs recent firmware) | 🔴 | control 0x35 | Pro 2/3, 4, 4 ANC |
| Optimized ("dynamic end of") charging | ✅ | 🔴 | control 0x3B; battery status 5 = optimized | recent firmware |
| Tone (chime) volume | ✅ | 🔴 | control 0x1F, 0–100 (app default 75) | all |
| Case sounds | 🔴 on `main`; rootless toggle in PR [#764](https://github.com/librepods-org/librepods/pull/764) | 🔴 | control 0x31 IN_CASE_TONE_CONFIG (1/2); 0x40 in-case tone volume; docs also show an ATT write `12 3A 00 01 00 08 xx` | Pro 2/3, 4 ANC (speaker cases) |
| Custom EQ (bass/mid/treble, iOS 27 style) | ✅ | ❓ | opcode 0x63 | recent firmware |
| Headphone accommodation EQ | ✅ | 🔴 | opcode 0x53 | Pro 2/3 |
| Firmware/serials/model info | ✅ | ✅ | opcode 0x1D push on connect | all |
| Auto-connect to AirPods | ✅ | ✅ | ACL/UUID broadcasts + A2DP/HFP connect via hidden APIs | all |
| Head-tracked spatial audio | ❓ (data available, not fed to Android) | ❓ | Would need a `TYPE_HEAD_TRACKER` HID bridge (root) or an in-app renderer ([#719](https://github.com/librepods-org/librepods/issues/719)) | IMU models |
| Heart rate | 🟡 (`android/rewrite`, PR [#702](https://github.com/librepods-org/librepods/pull/702)) | 🔴 | RTBuddy sensor service HEARTRATE(19) or HEARTRATE_COMMAND(84), 1 s | Pro 3 |
| HQ two-way audio (mic over AACP while A2DP stays) | 🟡 (in-app recorder only) | 🟡 PR #655 | opcode 0x58 MICROPHONE_STREAM | recent |
| Find My (network, play sound, left-behind) | ❓ | ❓ | not reverse-engineered; the "offline-finding seed" is not the IRK/ENC keys | - |
| Battery in system Settings/status bar, AirPods icon in Settings | root-module only | n/a | privileged `BluetoothDevice.setMetadata` + HFP `+IPHONEACCEV` broadcast; needs `BLUETOOTH_PRIVILEGED`/`INTERACT_ACROSS_USERS` (priv-app) | all |
| Beats | ✗ no Beats model IDs anywhere in the code | ✗ | - | - |

**Model table** (`main` `android/app/src/main/java/me/kavishdevar/librepods/data/AirPods.kt`; `android/rewrite` `devices/AirPods.kt` adds Max):

| Model | Model numbers | Extra capabilities |
|---|---|---|
| AirPods 1 / 2 | A1523, A1722 / A2032, A2031 | none (battery, ear detection, rename, info) |
| AirPods 3 | A2565, A2564 | head gestures |
| AirPods 4 | A3053, A3050, A3054 | head gestures, sleep detection, adaptive volume |
| AirPods 4 ANC | A3056, A3055, A3057 | + listening modes, CA, adaptive audio, stem config |
| AirPods Pro 1 | A2084, A2083 | listening modes |
| AirPods Pro 2 (Lightning / USB-C) | A2931, A2699, A2698 / A3047, A3048, A3049 | listening modes, CA, stem config, LSR, sleep, hearing aid, adaptive audio, adaptive volume, volume swipe, head gestures |
| AirPods Pro 3 | A3063, A3064, A3065 | Pro 2 set + PPE (hearing protection) + heart rate |
| AirPods Max / Max USB-C (rewrite only) | A2096 / A3184 | listening modes |
| AirPods Max 2 (rewrite only) | A3454 | listening modes, CA, LSR, adaptive audio, adaptive volume |
| AirPods 5 (PR [#788](https://github.com/librepods-org/librepods/pull/788)) | A3531–A3533, A3439–A3441 (wireless case) | AirPods 4-like |

On `main`, unknown model numbers (for example any Max) fall back to Pro 2 USB-C `A3049` (explained in PR #767).
In the rewrite the per-model table is only the static default. The AirPods also send a **capability list
(opcode 0x02)**, which `android/rewrite` parses (`types/Capability.kt`): CASE_SOUND 0x12,
HIDE_OFF_LISTENING_MODE 0x13, EAR_TIP_FIT_TEST 0x17, AUTO_ANC 0x18, SLEEP_DETECTION 0x21,
HEARING_AID 0x22, CAMERA_CONTROL 0x23, HEART_RATE_MONITOR 0x26, HEARING_PROTECTION 0x30 and more.
**PodLink should gate UI on this list rather than on a model table.**

### Why some features need the DID/vendor-ID spoof

The AirPods read the host's **SDP Device ID (PnP/DI) record**. When it says vendor `0x004C` (Apple) with
vendor-ID source `0x0001` (Bluetooth SIG), they treat the host as an Apple device and unlock:

- the **ATT server over BR/EDR** (L2CAP PSM 0x001F): transparency customization (handle 0x18),
  loud sound reduction (0x1B) and hearing aid (0x2A). LibrePods only opens the ATT socket when the hook
  is on (`AirPodsService.kt` L2659-2666);
- **smart routing / "TiPi" multipoint ownership** (control 0x06, opcodes 0x10/0x11/0x2E/0x0E).
  `takeOver()` bails out with *"not taking over, vendorid is probably not set to apple"* when the hook is off
  (`AirPodsService.kt` L2479-2481).

On Linux: `DeviceID = bluetooth:004C:0000:0000` in `/etc/bluetooth/main.conf` (README). On Android the local
DI record is built in `btif_core.cc` from the read-only sysprops `bluetooth.device_id.vendor_id` /
`vendor_id_source` / `product_id` / `version`. The default is Google `LMP_COMPID_GOOGLE` with source BT SIG
([AOSP `sysprop/device_id.sysprop`](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/main/sysprop/device_id.sysprop),
`system/btif/src/btif_core.cc`, `tSDP_DI_RECORD record = {.vendor = DeviceIDProperties::vendor_id().value_or(LMP_COMPID_GOOGLE) …}; BTA_DmSetLocalDiRecord(&record)`).
An unrooted app cannot change it. LibrePods hooks `BTA_DmSetLocalDiRecord` instead (section 4.3).
*Untested idea:* on a rooted device, `resetprop bluetooth.device_id.vendor_id 76` before Bluetooth starts
should give the same spoof without Xposed.

**Current caveat.** With AirPods firmware **9A348 (build 9442752, ~2026-09-15)** the "act as Apple" mode makes the
pods drop the link about every 40 s. The pods apparently expect the full new macOS session protocol
(0x4F attribute queries and more), which LibrePods doesn't speak
([#782](https://github.com/librepods-org/librepods/issues/782), confirmed by a second user). Also
[#700](https://github.com/librepods-org/librepods/issues/700): the hook causes A2DP/HFP flapping on Android 17.
**PodLink should not plan on the Apple-DID features.**

---

## 3. The AAP / AACP protocol

LibrePods calls it AACP ("Apple Accessory Communication Protocol").

Protocol docs in the repo:
- [`docs/AAP Definitions.md`](https://github.com/librepods-org/librepods/blob/main/docs/AAP%20Definitions.md)
  (written against AirPods Pro 2 USB-C firmware 7A305). It has two errata: the battery example's L/R labels
  are swapped ([#771](https://github.com/librepods-org/librepods/issues/771)), and `FF FF FE FF` gives no
  battery on A2698 ([#770](https://github.com/librepods-org/librepods/issues/770)).
- [`docs/control_commands.md`](https://github.com/librepods-org/librepods/blob/main/docs/control_commands.md)
  (IDs from the iOS 19.1 beta Bluetooth stack).
- [`docs/opcodes.md`](https://github.com/librepods-org/librepods/blob/main/docs/opcodes.md) and
  [`docs/device-info.md`](https://github.com/librepods-org/librepods/blob/main/docs/device-info.md).
- `Proximity Pairing Message.md` (BLE), on the `linux/rust` branch.
- Open PR [#797](https://github.com/librepods-org/librepods/pull/797) adds `docs/host-capabilities.md` (connect pacing).

The most complete opcode list is in code: `android/rewrite` `bluetooth/aacp/types/Opcodes.kt`,
`ControlCommand.kt`, `Capability.kt` and `proto/rtbuddy.proto`.

### 3.1 Transport

- Classic BR/EDR **L2CAP, PSM 0x1001 (4097)**, basic mode, on the already-bonded (encrypted) ACL.
  The SDP service UUID is `74ec2172-0bad-4d01-8f77-997b2be0722a`, which is also how LibrePods recognises
  AirPods. Our probe saw MTU rx 8087 / tx 1691.
- Each L2CAP SDU is one message; LibrePods reads into a 1024-byte buffer. **The rewrite reads 1024 too.
  Use at least the MTU**, because some AAP messages are larger.
- ATT (only with the DID spoof): L2CAP **PSM 0x001F** on the same ACL, plain ATT PDUs (section 3.9).

### 3.2 Framing

All integers are little-endian.

```
[type u16][service u16][opcode u16][payload…]
type:    0x0000 CONNECT, 0x0001 CONNECT_RESPONSE, 0x0002 DISCONNECT, 0x0003 DISCONNECT_RESPONSE, 0x0004 MESSAGE
service: 0x0004 for everything seen
```

So every normal message starts `04 00 04 00 <op lo> <op hi>`. Source: `android/rewrite`
`packet/AACPPacket.kt` (`rawPacket = [type,0,service,0,opcode,0] + payload`).

### 3.3 Connect sequence (as LibrePods does it)

`AirPodsService.connectToSocket`, `main` L2637-2840; the rewrite is `AACPManager.connect`/`sendDelayedInitPackets`.

1. **Handshake (CONNECT):** `00 00 04 00 01 00 02 00 00 00 00 00 00 00 00 00`. The reply is a
   CONNECT_RESPONSE starting `01 00 04 00` (`linux/airpods_packets.h` `HANDSHAKE_ACK`). The rewrite sends
   `…01 00 03 00…` ("connectService4").
2. **Host capabilities / "set feature flags"** (op 0x4D, `SOURCE_FEATURE_CAPABILITIES`):
   `04 00 04 00 4D 00 D7 00 00 00 00 00 00 00`. The docs use `FF` as the first byte; either one enables
   CA-while-playing and Adaptive (without it, a request for Adaptive answers with ANC).
3. **Notification register** (op 0x0F): `04 00 04 00 0F 00 FF FF FF FF`. The third byte is `FD` if ear
   detection is disabled. Prefer `FF FF FF FF` (#770).
4. Optional op 0x29 ("country code" in the rewrite, "enables setting EQ" in `main`):
   `04 00 04 00 29 00 00 FF FF FF FF FF FF FF`.
5. **Proximity/magic keys request** (op 0x30): `04 00 04 00 30 00 05 00` (bitmask IRK 0x01 | ENC 0x04).
6. The accessory then pushes 0x1D info, 0x02 capabilities, all 0x09 control states, 0x04 battery,
   0x06 ear state, 0x08 bud role, 0x2E/0x0E/0x0C device lists, 0x17 sensor descriptors, and so on (matches
   our probe).

LibrePods repeats the whole sequence at +200 ms and again at +5 s (the rewrite repeats after 3 s). PR #797
recommends pacing of about 100-350 ms between steps and **never draining the RX queue** after connecting,
because 0x1D can arrive before the notification register is acknowledged.

### 3.4 Opcodes

Union of `main` `AACPManager.Opcodes`, rewrite `MessageOpcode` and `docs/opcodes.md`.
Direction: H = accessory→host, A = host→accessory.

| Op | Name | Dir | Notes |
|---|---|---|---|
| 0x01 | CAPABILITIES_REQUEST | A | (#782's "new handshake `04 00 04 00 01 00 08`" is this message type) |
| 0x02 | CAPABILITIES | H | `[count]` then `[id][value(size per id)]` |
| 0x03/0x04 | BATTERY_INFO req/push | A/H | 3.5 |
| 0x05/0x06 | EAR_DETECTION req/push | A/H | 3.5 |
| 0x07/0x08 | BUD_ROLE req/push | A/H | `payload[0]==1` means left bud is primary |
| 0x09 | CONTROL_COMMAND | both | 3.6 |
| 0x0B | DEVICE_LIST | H | |
| 0x0C | MAC_ADDRESS | H | |
| 0x0D/0x0E | AUDIO_SOURCE req/resp | A/H | resp: `[mac 6B reversed][type 0 none,1 call,2 media]` |
| 0x0F | REQUEST_NOTIFICATIONS | A | `FF FF FF FF` |
| 0x10/0x11 | SMART_ROUTING / response | A/H | target MAC (reversed) + length + OPACK-like key/value blob (`playingApp`, `btName`, `SetOwnershipToFalse`, `ShowNearbyUI`, `Hijackv2`…); multipoint only |
| 0x12/0x13 | EASY_PAIR | | |
| 0x14 | CONNECT_PRIORITY_LIST / "send connected MAC" | A | |
| 0x15/0x16 | TRIANGLE_LINK_STATUS | | |
| 0x17 | BUDDY_COMMAND (RTBuddy) | both | head tracking, heart rate, SPL; 3.7 |
| 0x19 | STEM_PRESS | H | `[type 05 single/06 double/07 triple/08 long][bud 01 L/02 R]` |
| 0x1A | RENAME | A | `01 [len] 00 [utf-8]` (`docs/opcodes.md` says 0x1E, but code uses 0x1A) |
| 0x1B | TIMESTAMP | A | macOS sends an ISO-8601 time sync (#782) |
| 0x1D | INFORMATION | H | NUL-separated strings: name, model (A####), manufacturer, serial, fw1, fw2, hw rev, updater id, left serial, right serial, version3, then encrypted blobs; push-only |
| 0x1E | EXTERNAL_ACCESSORY_SESSION | | |
| 0x20 | REMOTE_FIRMWARE_AUTH | | |
| 0x22/0x23 | CASE_INFO req/push | | |
| 0x24 | DEVICE_INFO | | |
| 0x26/0x27 | CERTIFICATES | | |
| 0x28 | GYRO_INFO | | |
| 0x29 | SET_COUNTRY_CODE | A | see 3.3 |
| 0x2B | STREAM_STATE_INFO | H | |
| 0x2C | GAPA_CHALLENGE | | |
| 0x2D/0x2E | CONNECTED_DEVICES req/resp | A/H | resp: `data[8]`=count, then 8-byte entries `[mac 6][info1][info2]` |
| 0x30/0x31/0x32 | MAGIC_KEYS req/resp/2 | A/H | 3.8 |
| 0x44–0x4A | smart-routing v2, bud swap… | | |
| 0x4B | CONVERSATION_AWARENESS | H | `02 00 01 [level]`: 1-2 user started speaking, 3 stopped, intermediate = ramp, 8-9 normal. App ducks on 1/2, restores on 6/8/9 |
| 0x4C | ADAPTIVE_VOLUME | | |
| 0x4D | SOURCE_FEATURE_CAPABILITIES ("set feature flags") | A | 3.3 |
| 0x4E | FEATURE_PROXCARD_STATUS | H | |
| 0x4F | UARP_DATA / "information req/res" | both | macOS does ~100 exchanges on new firmware (#782) |
| 0x52 | SOURCE_CONTEXT | | |
| 0x53 | HEADPHONE_ACCOMMODATION | both | `84 00 02 02 [phone 1/2] [media 1/2]` + 4 × 8 LE floats (140 bytes total) |
| 0x54 | SET_BAND_EDGES | | |
| 0x56 | USB_SPATIAL_SENSOR_DATA_REQUEST | | |
| 0x57 | SLEEP_DETECTION_UPDATE | H | |
| 0x58 | MICROPHONE_STREAM | both | rewrite's recorder |
| 0x59 | DYNAMIC_END_OF_CHARGE | | |
| 0x60 | PERSONAL_TRANSLATION | | |
| 0x62 | SET_FEATURE_FLAGS (rewrite naming) | | |
| 0x63 | CUSTOM_EQ | both | payload `[len u16=5][?][state 1 off/2 on][low][mid][high]` (0-100) |
| 0x64 | APPLECARE | | |

**Opcodes our probe saw but didn't decode** (device-findings.md): 0x0008 = BUD_ROLE, 0x002b = STREAM_STATE_INFO,
0x004e = FEATURE_PROXCARD_STATUS, 0x0053 = HEADPHONE_ACCOMMODATION, 0x0055 = unknown (also unknown in the rewrite),
0x000c = MAC_ADDRESS, 0x000e = AUDIO_SOURCE, 0x002e = CONNECTED_DEVICES, 0x0002 = CAPABILITIES. The
0x0017 "HID descriptors incl. devmotion6" are RTBuddy `SensorDescriptor` messages.

### 3.5 Push packets

- **Battery (0x04):** `04 00 04 00 04 00 [n]` then n × `[component][01][level 0-100][status][01]`.
  - component: 0x01 single (Max), 0x02 **right**, 0x04 **left**, 0x08 case.
  - status: 0 unknown, 1 charging, 2 discharging, 4 disconnected, 5 optimized charging.
  - Example with 3 entries: `…03 02 01 64 02 01 04 01 63 01 01 08 01 11 02 01` = right 100 % discharging,
    left 99 % charging, case 17 % discharging.
  - Parse by count, not fixed 22 bytes (the Max bug). A level of 127 has been seen for a missing bud
    ([#737](https://github.com/librepods-org/librepods/issues/737)), so treat it as unknown.
  - The case reports only while a bud is in it; iOS gets the rest via Find My (maintainer in [#250](https://github.com/librepods-org/librepods/issues/250)).
- **Ear detection (0x06):** `04 00 04 00 06 00 [primary][secondary]`: 0 in ear, 1 out of ear, 2 in case.
  Map primary/secondary to L/R with BUD_ROLE (0x08). `main` just assumes an order. It is re-sent when the
  primary swaps (mic handover).
- **Control command echo (0x09):** see 3.6. Every state is pushed after 0x0F.
- **CA (0x4B), stem press (0x19), info (0x1D):** see the table above.

### 3.6 Control commands (op 0x09)

`04 00 04 00 09 00 [id] [v1] [v2] [v3] [v4]`: always 11 bytes, unused bytes 0. Booleans are **1 = on, 2 = off**.
The accessory echoes the new value.

| ID | Name | Value encoding |
|---|---|---|
| 0x01 | Mic mode | 0 auto, 1 right, 2 left |
| 0x05 | Button send mode | ? |
| 0x06 | Owns connection | 1 own / 0 not (multipoint) |
| 0x0A | Ear detection enabled | 1/2 |
| 0x0D | **Listening mode** | 1 Off, 2 ANC, 3 Transparency, 4 Adaptive |
| 0x0E/0x0F | Heart-rate monitor 1/2 | |
| 0x12 | "Hey Siri" voice trigger | 1/2 |
| 0x14/0x15 | Single/double click mode | ? |
| 0x16 | Click-hold mode | 2 bytes `[right][left]`: 1 noise control, 5 Siri |
| 0x17 | Double-click interval (press speed) | 0 default, 1 slower, 2 slowest |
| 0x18 | Click-hold interval (press-and-hold duration) | 0 default, 1 slower, 2 slowest |
| 0x1A | **Listening-mode cycle config** | bitmask Off 0x01, ANC 0x02, Transparency 0x04, Adaptive 0x08 |
| 0x1B | One-bud ANC | 1/2 |
| 0x1C | Crown rotation direction (Max) | 1 reversed, 2 default |
| 0x1E | Auto-answer mode | ? |
| 0x1F | Chime (tone) volume | 0-100 |
| 0x20 | Connect automatically / smart-routing mode | 1/2 |
| 0x22 | HFP uplink mode | |
| 0x23 | Volume-swipe interval | 0 default, 1 longer, 2 longest |
| 0x24 | Call management config | 2 bytes; app default `00 03`; `[1]==02` flips press-once/twice |
| 0x25 | Volume swipe | 1/2 |
| 0x26 | Adaptive (personalized) volume | 1/2 |
| 0x27 | Software mute | ? |
| 0x28 | Conversation detect (CA) | 1/2 |
| 0x29 | SSL | ? |
| 0x2C | Hearing aid | 2 bytes `[enrolled][enabled]`, 1/2 |
| 0x2E | Adaptive audio ("AutoANC") strength | 0-100 |
| 0x2F | HPS gain swipe | |
| 0x30 | HRM state | |
| 0x31 | In-case tone | 1/2 |
| 0x32 | Siri multitone | |
| 0x33 | Hearing assist | 1/2 |
| 0x34 | Allow Off listening mode | 1/2 |
| 0x35 | Sleep detection | 1/2 |
| 0x36 | Allow auto connect | 1/2 |
| 0x37 / 0x38 | PPE (hearing protection) toggle / cap level | |
| 0x39 | Raw gestures (forward presses to host) | bitmask single 0x01, double 0x02, triple 0x04, long 0x08 |
| 0x3A | Temporary pairing | 1 temporary, 2 permanent |
| 0x3B | Dynamic end of charge | |
| 0x3C | System Siri mode | |
| 0x3D | Hearing aid generic | |
| 0x3E/0x3F | Uplink EQ bud/source | |
| 0x40 | In-case tone volume | 0-100 |
| 0x41 | Disable button input | |
| 0x42 | Extended hold and release | |

The rewrite also names 0x02 SCAN, 0x03 RESET, 0x04 BASIC_DOUBLE_TAP_MODE, 0x07 TAP_INTERVAL, 0x08 BUD_ROLE,
0x09 DEBUG_GET_DATA, 0x0B JITTER_BUFFER, 0x0C DOUBLE_TAP_MODE, 0x11 SWITCH_CONTROL, 0x13 DOAP_MODE.

**Listening-mode cycle (next mode).** From `getNextMode()` (`AirPodsService.kt` L3203): walk the enabled
modes in the order Off (only if 0x34 is on) → Transparency → Adaptive → ANC, then wrap. LibrePods uses it
for the QS tile and "cycle" stem action. Setting 0x1A changes what a **physical** long press cycles through.
Recent firmware requires at least two modes.

### 3.7 Head tracking and other sensors (op 0x17, "RTBuddy")

This is the envelope inside opcode 0x17, from `android/rewrite` `packet/RTBuddyPacket.kt` and `proto/rtbuddy.proto`:

```
04 00 04 00 17 00 | descriptor u32 LE (0x00100000 = SENSOR_DATA_WX) | length u16 LE | protobuf SensorDataWX
message SensorDataWX { int32 seq=1; int32 log_type=2; bytes another_sensor_stream=3; RequestAllDescriptors=4;
  SensorDescriptor sensor_descriptor=5; SensorCommand command=7; SensorServiceSetting service_settings=8;
  SensorTypeAck start_ack=9; SensorTypeAck command_ack=12; }
SensorServiceSetting { SensorServiceType service=1; int32 setting=2; bytes configuration=3; }
SensorCommand { SensorServiceType service=1; bytes payload=3; }
SensorServiceType: ACCEL 11, GYRO 12, PDR 13, ACTIVITY 14, CMA 15, DEVMOTION6 16, SPL0 17,
                   HEARTRATE 19, HEARTRATEv2 20, HEARTRATE_COMMAND 84, …
```

- **Start**: `service_settings{service, setting=2, configuration = 01 ‖ interval_µs u32 LE}`. **Stop**: the same
  with interval 0.
  - `main`'s start packet `04 00 04 00 17 00 00 00 10 00 10 00 08 A1 02 42 0B 08 0E 10 02 1A 05 01 40 9C 00 00`
    decodes to seq=289, service=14 (ACTIVITY), interval **0x9C40 = 40,000 µs → 25 Hz**.
  - The "alternate" packet (default on since #315) is `…0F 00 08 73 42 0B 08 10 10 02 1A 05 01 40 9C 00 00`,
    which is service **16 (DEVMOTION6)** at 25 Hz.
  - The rewrite picks DEVMOTION6 when the firmware `version3` starts with a digit ≥ 8, otherwise ACTIVITY
    (`devices/AppleDevice.kt` L263-287). The default interval is 40 ms (`AppleSettings.headTrackingInterval`),
    adjustable in a debug slider.
- **Data**: `SensorDataWX.command{service=14|16, payload = 58 bytes}`. Offsets are **inside that payload**:
  - int16 LE "orientation" o1/o2/o3 at [20]/[22]/[24];
  - two int16 LE values called "horizontal/vertical acceleration" at [28]/[30] (alternate horizontal offset 26).
    The maintainer believes these are really **rotation rates** ([#133](https://github.com/librepods-org/librepods/issues/133)).
    They are what the gesture detector uses.
  - `main` reads fixed absolute offsets instead: o1..o3 at packet bytes 43/45/47, h/v at 51/53, and needs
    packet ≥ 70 bytes (`AACPManager.receivePacket`, `HeadOrientation.kt`, `AirPodsService.processHeadTrackingData`
    L2198). These shift by one whenever the protobuf `seq` varint changes length.
    [#719](https://github.com/librepods-org/librepods/issues/719) recommends protobuf-relative parsing.
  - Example frame from #133: `04 00 04 00 17 00 | 00 00 10 00 | 44 00 | 08 0f 10 01 3a 3e 08 0e 1a 3a | <58-byte payload>`.
  - **Confirmed on our unrooted HP Googlebook** ([device-findings.md](device-findings.md)): the DEVMOTION6 start
    gets a `start_ack` (field 9, service 16) after ~30 ms. It then streams 80-byte frames at ≈ 25 Hz
    (181 in 7.1 s), which stop on the interval-0 packet.
- **Orientation math** (`main` `utils/HeadOrientation.kt`, ported from `head-tracking/head_orientation.py`):
  calibrate on the first 10 samples (neutral = mean of (o + 5500)), then
  `pitch = (o2n + o3n)/2/32000*180` and `yaw = (o2n − o3n)/2/32000*180`. This is empirical, not real Euler angles.
- The same stream carries heart rate (HEARTRATE 19 / HEARTRATE_COMMAND 84 when firmware version3 ≥ 9, 1 s interval,
  18-byte payload, bpm at `payload[1]`) and sound pressure level (SPL0 17).

### 3.8 Proximity ("magic") keys

- Request: `04 00 04 00 30 00 [bitmask] 00`, where bitmask 0x01 = IRK, 0x04 = ENC_KEY, 0x05 = both.
- Response 0x31: `04 00 04 00 31 00 [count]` then per key `[type][00][len][00][key bytes]` (16-byte keys).
- The **IRK** resolves the AirPods' BLE random address. The **ENC_KEY** decrypts the last 16 bytes of the
  proximity-pairing advert (section 6). LibrePods stores both Base64 in SharedPreferences (and logs them;
  PR [#810](https://github.com/librepods-org/librepods/pull/810) removes the logging). Treat them as secrets.

### 3.9 ATT over BR/EDR (DID-spoof only)

L2CAP PSM 0x1F, standard ATT PDUs: Read Req `0A [handle u16]` → `0B value`; Write Req `12 [handle u16] data`
→ `13`; notifications `1B`. Handles are 0x18 transparency (CCCD 0x19), 0x1B loud sound reduction, 0x2A
hearing aid (CCCD 0x2B).

The transparency value is little-endian float32s: `enabled`, then left `[EQ×8, amplification, tone,
conversation boost, ambient-noise reduction]`, then the same for the right bud, then an optional own-voice
amplification. After writing it, send the 0x53 accommodation packet (`data/Transparency.kt`, `bluetooth/ATTManager.kt`).

### 3.10 Firmware 9A348 (Sept 2026) changes to watch

[#782](https://github.com/librepods-org/librepods/issues/782) has packet-level analysis against a macOS 26.6.2 trace:

- The pods now send `AT+IPHONEACCEV=1,1,83` **without the trailing CR**. AOSP's HFP AT parser waits for
  the CR, so HFP reconnects every ~20 s on stock Android even without LibrePods. The workaround is an Xposed
  hook answering `+XAPL=iPhone,0`. **PodLink users on Android may see HFP flaps that aren't our fault.**
- With the Apple DID, the pods drop hosts that don't complete the new session: `0x4F` attribute queries,
  `0x1B` time sync, a new `0x29` payload `05 ff 05 ff 03 03 03 03`, and opcodes 0x0D/0x22/0x24/0x2D/0x44/0x54.
  macOS never sends 0x4D and doesn't re-request proximity keys. **Without the DID spoof, basic AAP keeps working.**

---

## 4. The root question

### 4.1 Short answer

**LibrePods needs root/Xposed only because of an Android Bluetooth-stack bug, plus (separately and
always) for the Apple vendor-ID spoof.**

- **The bug.** Every app-created classic L2CAP socket asks for **ERTM** mode. The stack asks each peer for
  its L2CAP Extended Features, and AirPods don't answer until they get the AAP handshake. After a 3 s
  timeout, `l2c_fcr_chk_chan_modes()` sees no ERTM support and returns `false`, so the channel is torn
  down before or right after the Connection Request.
- **The Xposed module** hooks that function in `libbluetooth_jni.so` (or `libbluetooth_qti.so`) inside the
  Bluetooth process to always return 1. A second hook on `BTA_DmSetLocalDiRecord` rewrites the local SDP
  Device ID vendor to Apple (0x004C).
- **The fix.** Google fixed it internally (commit `51b17545d`, "Update Interop fix to be custom UUID based",
  merged 2025-12-12, internal Bug 371797042, no aconfig flag). The stack now skips the ERTM requirement
  and the extended-features query **for any bonded device whose SDP UUIDs include AAP's
  `74ec2172-0bad-4d01-8f77-997b2be0722a`**.
  - It is in `android17-release` / `android-17.0.0_r1`, and so in every Android 17 build that tracks AOSP.
  - It is also in the Pixel Android 16 QPR3 (`CP1A`) Bluetooth module from March 2026, and in OEM
    backports: OxygenOS/ColorOS 16, One UI 9 (A17).
- **Our target.** The HP Googlebook 14 on Android 17 (module `com.google.android.bt`
  371899999) opens PSM 0x1001 from a normal app with no root (probe, 2026-09-30). The remaining
  non-public piece is the hidden-API socket constructor, not root.

### 4.2 Root cause in the Bluetooth stack

AOSP `packages/modules/Bluetooth`. The legacy `system/stack` L2CAP code is still used under the GD
("Gabeldorsche") shim in current releases, so the check is the same in every build before the fix.

1. **Sockets ask for ERTM.** `btif_sock_l2cap.cc` (A16 QPR2 L846; A17 L819-1033):
   `const tL2CAP_ERTM_INFO obex_l2c_etm_opt = {L2CAP_FCR_ERTM_MODE, /* Mandatory for OBEX over l2cap */};` …
   `if (!sock->is_le_coc) ertm_info.reset(new tL2CAP_ERTM_INFO(obex_l2c_etm_opt));`
   There's no way for an app to request Basic mode.
2. **The stack queries the peer's features.** On ACL up it sends an Extended Features Information Request
   (`l2cu_send_peer_info_req(p_lcb, L2CAP_EXTENDED_FEATURES_INFO_TYPE)` in `l2c_link.cc`). AirPods "will not
   respond with anything until a specific handshake packet is sent" ([issue 371713238](https://issuetracker.google.com/issues/371713238),
   filed 2024-10-06, "L2CAP extended flow control packet blocks socket").
   `l2c_info_resp_timer_timeout` fires after 3 s with `peer_ext_fea` lacking `L2CAP_EXTFEA_ENH_RETRANS`.
3. **The check fails** (`system/stack/l2cap/l2c_fcr.cc`, unchanged android14 through 16-qpr2;
   [A16 QPR2 L1595](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/android16-qpr2-release/system/stack/l2cap/l2c_fcr.cc)):
   ```cpp
   if (!(p_ccb->p_lcb->peer_ext_fea & L2CAP_EXTFEA_ENH_RETRANS) &&
       p_ccb->p_rcb->ertm_info.preferred_mode == L2CAP_FCR_ERTM_MODE) {
     log::warn("L2CAP - Peer does not support our desired channel types");
     p_ccb->p_rcb->ertm_info.preferred_mode = 0;
     return false;
   }
   return true;
   ```
   `l2c_csm.cc` then releases the CCB and reports `L2CAP_CONN_OTHER_ERROR`. The disassembly of an Infinix
   A16 `libbluetooth_jni.so` shows the same branch (`tbnz w8,#3` on `peer_ext_fea`, `cmp preferred_mode,#3`), per
   [zondaxxx/podlink docs/l2cap-airpods-bug.md](https://github.com/zondaxxx/podlink/blob/main/docs/l2cap-airpods-bug.md).
4. **Some OEM stacks** (seen on a Xiaomi 14 Pro in PR #785) get past this check with the hook, but
   `allowed_modes` is already 0. `l2c_fcr_process_peer_cfg_req()` then disconnects on the peer's
   ConfigReq ("L2C CFG: mode is ERTM, but peer does not support; Try BASIC … allowed:0 … incompatible
   configurations disconnect").

Linux/BlueZ never hit this because BlueZ opens the channel in Basic mode. Google's first internal attempt
(`ab862ce1a`) states it directly: *"Airpods require default L2CAP channel as BASIC … Some devices do not
support ERTM from peer and donot send response for INFO response during connection."*

### 4.3 What the Xposed module hooks

Module entry points: `android/app/src/main/resources/META-INF/xposed/{java_init.list,native_init.list,scope.list,module.prop}`.
It uses the **libxposed API 101** (LSPosed/Vector). The module scope is `com.android.bluetooth`,
`com.google.android.bluetooth`, `com.android.settings` and `com.google.android.settings`.

- **Java side** (`utils/KotlinModule.kt`, `onPackageLoaded`): in the Bluetooth process it `System.load`s
  `libl2c_fcr_hook.so` from the module APK (`!/lib/<abi>/`). It then reads the remote pref `vendor_id_hook`
  and calls `NativeBridge.setSdpHook(value)`. In Settings it hooks
  `AdvancedBluetoothDetailsHeaderController.updateIcon(ImageView, String)` so that `android.resource://me.kavishdevar.librepods/…`
  icon URIs from `setMetadata` can render (a cosmetic Settings-header fix).
- **Native side** (`android/app/src/main/cpp/l2c_fcr_hook.cpp`, `rewrite` renames it `fluoride_hooks.cpp`):
  - `native_init()` receives the framework's `hook_func`. On load of **`libbluetooth_jni.so`** (and
    **`libbluetooth_qti.so`** on Qualcomm stacks) it reads the library file from `/proc/self/maps`.
  - It XZ-decompresses the **`.gnu_debugdata`** (MiniDebugInfo) section, looks symbols up in its `.symtab`,
    falls back to `.dynsym`, adds the load base, and inline-hooks:
    1. `l2c_fcr_chk_chan_modes(tL2C_CCB*)` → **`fake_l2c_fcr_chk_chan_modes` always returns 1**
       (after calling the original), so the stack no longer rejects the AirPods' channel for lacking ERTM support.
    2. `BTA_DmSetLocalDiRecord(tSDP_DI_RECORD*, uint32_t*)` → **rewrites `vendor = 0x004C`,
       `vendor_id_source = 0x0001`** before calling the original: the Apple DID spoof. As written the gating
       looks buggy:
       - the replacement *always* overwrites the vendor and registers the spoofed record (it calls the
         original twice, once inside a log statement);
       - `vendor_id_hook == true` only adds an extra call with the *unmodified* record first
         (`l2c_fcr_hook.cpp` L58-79).
       #700 reports the systemized install applies the hook regardless of the preference.
  - The ELF parsing is Elf64-only, so 32-bit stacks aren't handled (PR [#734](https://github.com/librepods-org/librepods/pull/734)).
- PR [#785](https://github.com/librepods-org/librepods/pull/785) (open) adds a third hook,
  **`l2c_fcr_process_peer_cfg_req`**, turning its `DISCONNECT` verdict into `OK`. Its HCI trace shows the
  real sequence:
  ```
  TX ConnReq PSM=0x1001 → RX ConnRsp success → TX ConfigReq RFC[mode=Basic] → RX ConfigReq MTU=2582
  → TX DisconnReq   ("L2C CFG: mode is ERTM, but peer does not support; Try BASIC …
     l2c_fcr_process_peer_cfg_req() … preferred: 3 allowed:0 … incompatible configurations disconnect")
  ```
  The app then reports `read failed, socket might closed or timeout, read ret: -1`.
- An older root path, the "root module" (`root-module-manual/`), installs the APK as a priv-app with
  `BLUETOOTH_PRIVILEGED`, `MODIFY_PHONE_STATE`, `INTERACT_ACROSS_USERS` and `LOCAL_MAC_ADDRESS`. It is only
  for system battery/icon metadata and speaker fallback, not for the L2CAP fix. The earlier radare2-based
  offset finder (`utils/RadareOffsetFinder.kt`) is dead code now.

### 4.4 Upstream fix (AOSP)

- **Not on public Gerrit.** Searching `android-review.googlesource.com` for `bug:371713238`,
  `message:371713238` and `l2c_fcr_chk_chan_modes` returns nothing. The commits appear on googlesource only
  after merge and reference internal **Bug 371797042**.
  - [`ab862ce1a`](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/ab862ce1a844da5c220e52ae779b239e7bc04f2f),
    "Add Interop fix to avoid querying L2CAP INFO request & related checks" (bhaktha@google.com, authored
    2025-11-23, merged 2025-12-10). It matched by device name ("AirPods", "AirPods Pro") through a new
    `INTEROP_L2CAP_DISABLE_ERTM` entry.
  - `a2ee778a3` (2025-12-12) reverted it: "Removing this and adding better iop change based on uuid".
  - **[`51b17545d`](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/51b17545d46b212a98b5b6f0bb58f99760ddde1a),
    "Update Interop fix to be custom UUID based"** (merged 2025-12-12). This is the one that shipped:
    ```cpp
    // l2c_fcr_chk_chan_modes(): instead of `return false;`
    if (l2c_should_skip_ertm(p_ccb->p_lcb->remote_bd_addr)) { log::info("candidate device for skip ertm"); return true; }
    // l2c_link.cc: don't send the Extended Features info request to such devices
    // l2c_utils.cc:
    bool l2c_should_skip_ertm(const RawAddress& bd_addr) {
      const Uuid RMT_CUSTOM_UUID = Uuid::FromString("74ec2172-0bad-4d01-8f77-997b2be0722a");
      std::vector<bluetooth::Uuid> remote_uuids = btif_storage_get_services(bd_addr);
      return std::find(remote_uuids.begin(), remote_uuids.end(), RMT_CUSTOM_UUID) != remote_uuids.end();
    }
    ```
    "Flag: EXEMPT": there is no aconfig flag, so it's unconditional. It only applies once the AirPods' SDP
    record (with the AAP UUID) is cached, which normal pairing does.
- **Where it is.**
  - In [`android17-release` l2c_fcr.cc L1609](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/android17-release/system/stack/l2cap/l2c_fcr.cc)
    and [l2c_utils.cc L3739](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/android17-release/system/stack/l2cap/l2c_utils.cc),
    tag `android-17.0.0_r1`.
  - Not in `android16-qpr2-release`. There's no public `android16-qpr3-release` source.
  - Module: the Bluetooth mainline APEX, renamed **`com.android.bt`** in A17 (formerly `com.android.btservices`).
    Google-signed builds ship as `com.google.android.bt`, as on our Googlebook.
  - Google on the issue (comment #899, quoted in [CAPod #215](https://github.com/d4rken-org/capod/issues/215),
    2026-03-24): *"The change is available as part of Android open source Bluetooth stack starting March
    2026. Mobile phone manufacturers will determine when this will be updated on their respective devices."*
  - The issue-tracker JSON shows a timestamp of 2026-04-24 (probably resolution) and last modified 2026-06-06.
- **Unverified.** Exactly which Google Play system update / mainline train delivered it to A16 QPR3 Pixels.
  LibrePods says "latest Play system update"; the CAPod tracker says "April 2026 security update". There are
  no public `aml_bt` release notes. A Galaxy S25 Ultra on the April 2026 Play system update was still broken,
  so on non-Pixels the Play update alone doesn't bring it; the OEM must ship the new module/OS.

### 4.5 Which builds work without root (status 2026-09-30)

LibrePods' own gate (`utils/RootlessSupport.kt` L25-39):
`if (SDK_INT >= 37) return true` (commit `bffb5c8`, 2026-06-10: *"consider all A17 devices supported … the app
does work on OneUI 9"*). Otherwise it allows Pixel on SDK 36 with `Build.ID` starting `CP1A` (A16 QPR3), and
OnePlus/Oppo/realme on SDK ≥ 36, with a manual bypass. The in-app list (`strings.xml` `check_the_repository_for_more_info`)
names Pixel on the A16 March update + latest Play system update, Pixel on 17 Beta 3+, OxygenOS 16+ and ColorOS 16+.

Community reports, mainly [LibrePods #487](https://github.com/librepods-org/librepods/issues/487) (2026-03-25 → 06-23)
and the [CAPod tracker #538](https://github.com/d4rken-org/capod/issues/538) (04-23 → 09-29):

| Works without root | Evidence (report date) |
|---|---|
| Pixel, A16 QPR3 (CP1A) | Pixel 6/8/10 Pro on CP1A.260305.018 (03-30/31); Pixel 10 Pro XL CP1A.260405.005 (05-09); needs the latest Play system update; some mixed reports |
| Pixel, A17 Beta 3+ and A17 stable (CP2A) | several, from 03-27 |
| GrapheneOS A17 | CP2A.260605.012 (06-23) |
| OnePlus, OxygenOS 16 | OnePlus 12 16.0.5 (05-18); OnePlus 15 16.0.10.500 (09-12); OnePlus 12 16.0.10 in #784 (09-19) |
| Oppo/OnePlus, ColorOS/OxygenOS 16 | per README; Find X9 Ultra ColorOS 16.0.8 in #648 |
| Samsung, One UI 9 (A17) | S26U beta (05-13); S23+ beta (09-03); Z Flip6 beta 2 (09-25); S25U stable (09-29). Stable rollout from 2026-09-16 ([9to5Google](https://9to5google.com/2026/09/24/samsung-galaxy-s26-one-ui-9-android-17-rollout/)) |
| Custom A17 ROMs (Evolution X) | works without the hook ([#700](https://github.com/librepods-org/librepods/issues/700)) |
| Xiaomi Poco X7 Pro, HyperOS 3.1 | only after disabling `com.xiaomi.bluetooth` (05-07); see PR #760 |
| **HP Googlebook 14, A17 (CL3B)** | our probe, 2026-09-30 |

| Still broken | Evidence |
|---|---|
| Samsung One UI 8/8.5 (A16) | S24+, S25U, S26U through 09-24; maintainer: "Samsung devices need OneUI 9" |
| GrapheneOS A16 | builds 2026050701 and 2026061601 |
| Pixel 10a on CP1A.260405.005 / .260505.005 | conflicts with the Pixel 10 Pro XL report above |
| Fairphone 6, A16 | 09-29 |
| Moto g stylus 2025, A16 | [#745](https://github.com/librepods-org/librepods/issues/745) (08-24) |
| Nothing OS | [#672](https://github.com/librepods-org/librepods/issues/672) (07-17); Phone 4a Pro (09-08) |
| Poco F7 Pro, HyperOS 3.0.303 | 09-21 |
| Oppo Find X9, ColorOS 16.0.9, handshake never completes ([#726](https://github.com/librepods-org/librepods/issues/726)) | 08-14 |
| realme UI 7 | no reports either way |

Press: [Android Authority](https://www.androidauthority.com/librepods-using-airpods-with-android-unlock-3661340/) (2026-04-28),
[heise](https://www.heise.de/en/news/Use-AirPods-with-Android-devices-LibrePods-app-lands-in-the-Play-Store-11275308.html) (04-28),
[How-To Geek](https://www.howtogeek.com/google-quietly-fixed-airpods-compatibility-with-android-and-this-app-is-all-you-need/) (04-29).

**Coexistence caveats for PodLink:**
- Only one app can hold the PSM 0x1001 channel. CAPod and LibrePods conflict with each other
  (`GAP_ConnOpen: Failure registering PSM 0x1001` in #487 went away when CAPod was removed).
- OEM AirPods integrations (OnePlus/Oppo [#587](https://github.com/librepods-org/librepods/issues/587),
  Xiaomi `com.xiaomi.bluetooth`) can grab the channel too.

**PodLink should detect these apps and explain the conflict.**

### 4.6 How the app creates the L2CAP socket

- **`main`** (`bluetooth/BluetoothConnectionManager.kt`) calls `createBluetoothSocket(adapter, device, uuid, psm)`,
  which tries hidden `BluetoothSocket` constructors by reflection in this order:
  `(BluetoothAdapter, BluetoothDevice, int type=3 L2CAP, boolean auth=true, boolean encrypt=true, int port=psm, ParcelUuid)`
  (the Android 16 QPR3+/17 signature), then `(device, 3, true, true, psm, uuid)`, then `(device, 3, fd=1, true, true, psm, uuid)`,
  and then two older orders. PR #764 notes that `fd=1` is wrong and should be `-1`.
- **Hidden-API exemption**: `libbluetooth_socket.so` (`cpp/bluetooth_socket.cpp`, loaded in `AirPodsService`'s
  companion `init`). Its `JNI_OnLoad` spawns a native thread that calls
  `VMRuntime.getRuntime().setHiddenApiExemptions(["Landroid/bluetooth/BluetoothSocket;", "Landroid/bluetooth/BluetoothDevice;"])`,
  with strings XOR-obfuscated with 0x47. The rewrite's `cpp/hiddenapi.cpp` exempts `"L"` (everything).
  This is the same trick as LSPosed's HiddenApiBypass. Apache-2.0 HiddenApiBypass is the clean choice for us.
- It does **not** use `BluetoothDevice.createUsingSocketSettings(BluetoothSocketSettings)` (API 36). On our
  Googlebook that API rejects classic L2CAP (`IllegalArgumentException: invalid socketType - 3`).
  `createInsecureL2capChannel`/`createL2capChannel` are LE CoC only.
- The ATT socket is the same constructor with PSM 31 and a null UUID.
- `connect()` is wrapped in a 5 s `withTimeout`. There's a blocking read loop on a coroutine; the loop ends
  on `-1` or an exception, which broadcasts `AIRPODS_DISCONNECTED`.

### 4.7 What fails without root on an unfixed stack

- **Stack log on unfixed builds** (CAPod #215): `l2c_fcr_chk_chan_modes: L2CAP - Peer does not support our desired channel types`
  → `send_app_err_code: … reason code:1` → app side `BluetoothSocketException: Connection failed for unknown reason`.
  Before the fix `connect()` could also block for seconds, and even cause ANRs, per the issue description.
- **LibrePods UI** (`AirPodsService.kt`):
  - the high-priority notification "Unable to connect to AirPods over L2CAP" / "…LibrePods couldn't connect to
    AirPods using L2CAP. Error: …" (L1812; currently short-circuited by an early `return`);
  - toasts "Couldn't connect to socket: <msg>" and "Couldn't connect to socket: timeout." (L2720-2737);
  - "Error: Socket created, but not connected." ([#229](https://github.com/librepods-org/librepods/issues/229)).
- **Underlying IOException users paste:** `read failed, socket might closed or timeout, read ret: -1`
  ([#188](https://github.com/librepods-org/librepods/issues/188), [#637](https://github.com/librepods-org/librepods/issues/637), #745, PR #785).
- **Reflection breakage:** `NoSuchMethodException: Cannot find matching constructor` (the constructor gained a
  leading `BluetoothAdapter` in A16 QPR3/A17) and `BluetoothSocketException: No PSM available`.
- **Hidden-API denial if not exempted:** `Accessing hidden method Landroid/bluetooth/BluetoothDevice;->createInsecureL2capSocket(I)… (max-target-o, reflection, denied)`.
  The LE-only public path gives `L2CA_RegisterLECoc: Invalid BLE PSM value, PSM: 0x1001`, and
  `BluetoothSocketSettings` gives `IllegalArgumentException: invalid socketType - 3`.
- **Onboarding "Not supported" page** (`strings.xml` L247-256): *"Many devices are not supported due to limitations
  in the Android Bluetooth stack. On these devices, root access with an Xposed framework is required for full
  functionality. This limitation has been addressed in newer Android versions…"*, followed by the device list above.

### 4.8 Implications for PodLink on Googlebooks

- Our target is Android 17 with Google's Bluetooth module, so the fix is present and **no root is needed** (verified).
  Keep a runtime check anyway, for OEM builds: connect with a short timeout, and if it fails with the
  signatures above, show "this build's Bluetooth stack lacks the AirPods fix" instead of a generic error.
- **Socket creation** needs a hidden API: `BluetoothDevice.createInsecureL2capSocket/createL2capSocket(int)`
  or the `BluetoothSocket` constructor. Use **LSPosed AndroidHiddenApiBypass (Apache-2.0)**, as our probe
  and CAPod do.
- **Risk.** A17 ART has a flag `com.android.art.flags.hiddenapi_jni_api_callers` (bug 403305904). For apps
  targeting SDK > 36 it checks the *native caller* on JNI hidden-API access
  ([art hidden_api.cc](https://android.googlesource.com/platform/art/+/refs/heads/android17-release/runtime/hidden_api.cc)).
  - It would break LibrePods' pthread `setHiddenApiExemptions` trick.
  - It's off in AOSP's `cp2a` config and on shipping builds so far.
  - The Java-side Unsafe/ArtMethod approach of HiddenApiBypass is a different path.
  - Keep a fallback ladder: `createInsecureL2capSocket` → `createL2capSocket` → reflected constructor.
    Watch every ART mainline update.
- **Don't ship an Xposed module.** The DID-spoof features are root-only and currently unstable with
  firmware 9A348 (section 2).

---

## 5. Head gestures

Files (`main`): `utils/GestureDetector.kt` (416 lines), `utils/GestureFeedback.kt` (179, SoundPool beeps),
`utils/HeadOrientation.kt` (101, orientation for the UI), `AirPodsService.{handleIncomingCall, processHeadTrackingData,
startHeadTracking, stopHeadTracking, answerCall, rejectCall}`, and the UI in `presentation/screens/HeadTrackingScreen.kt`.
The algorithm was ported from `head-tracking/gestures.py` (on `linux/rust`). The README says the head-gesture
code and UI are **entirely AI-generated**.

**Input.** The two int16 "horizontal" and "vertical" values (bytes 51/53, see 3.7) at **25 Hz** (40 ms). Pitch/yaw
from o1..o3 is only drawn in the UI and not used for detection.

**Algorithm** (`GestureDetector.processHeadOrientation` + `detectGestures`):

1. **Reject junk:** drop samples with |h| or |v| > 6000 ("likely calibration data").
2. **Immediate feedback:** if the sample-to-sample delta exceeds 600 on one axis (and dominates the other),
   play a panned blip (left/right for horizontal, centre for vertical), debounced 150 ms per axis and 200 ms
   per direction. This is audible feedback only.
3. **Smoothing:** 3-sample moving average per axis, pushed into 100-sample ring buffers (~4 s).
4. **Peak/trough detection** per axis, on each new sample:
   - dynamic threshold = `max(50, min(150, variance(last 4 samples)/3))`;
   - a direction reversal larger than that threshold is recorded as a peak or trough, but only if |value| > **400**;
   - the time between successive extremes feeds `peakIntervals` (last 5) and `movementSpeedIntervals`.
5. **Required extremes:** 3, or 4 if the average interval between extremes is < 300 ms (fast wobble).
6. **Confidence** for an axis with ≥ N extremes, over the last N extremes:
   `0.4·min(1, meanAmp/600) + 0.2·rhythm + 0.2·alternation + 0.2·isolation`, where
   - `rhythm = 1 − min(1, mean((Δt/meanΔt − 1)²)/0.5)`;
   - `alternation` = 1 if the signs strictly alternate, else 0.5;
   - `isolation = min(1, 1.2·meanAmp_axis / (mean|other axis| over the last 2N samples + 0.1))`.
7. **Decision:** a coroutine polls every 50 ms. **Vertical is checked first**: confidence ≥ 0.7 means
   **"yes" (nod)**. Otherwise horizontal ≥ 0.7 means **"no" (shake)**. The detector plays a confirm sound
   (`R.raw.confirm_yes/no`), invokes the callback and stops.

**Calibration.** None for detection. Orientation calibration is 10 samples for the UI only. The rewrite adds
per-user **byte-offset** settings (vertical 30, horizontal 28 or 26) and an interval slider, both debug-only.

**False positives.** Handled only by the amplitude floor (400), the reversal threshold, the alternation/rhythm/
isolation scoring and the 0.7 cutoff. There is no timeout, no "must be still first" gate and no minimum
duration. Detection runs from ringing until a gesture, the call going `IDLE`, or stop.
[#555](https://github.com/librepods-org/librepods/issues/555) reports shakes detected as "yes" (Pixel 7,
Pro 2, not reproduced by the maintainer); [#556](https://github.com/librepods-org/librepods/issues/556)
reports occasional unintended hang-ups. The vertical-first order biases ambiguous motion toward "yes".

**When it runs.**
- `TelephonyCallback` `CALL_STATE_RINGING` → `handleIncomingCall()` → `startHeadTracking()` + `startDetection`.
- On a match it calls `TelecomManager.acceptRingingCall()` / `endCall()`, which need `ANSWER_PHONE_CALLS`
  and are deprecated. The code has a TODO to move to an InCallService with a CDM association.
- The test button runs detection without stopping tracking.
- Tracking also starts for 5 s right after every AACP connect, then stops (`AirPodsService` L2759-2766).
  The purpose is undocumented.

**Head tracking start/stop, battery, audio.**
- `startHeadTracking()` (`AirPodsService` L3149) first checks control 0x06 OWNS_CONNECTION. If this phone
  doesn't own the AirPods it calls `takeOver("call", startHeadTrackingAgain = true)`; otherwise it logs
  "might not work". This suggests the stream only flows to the active/owning host. Nothing in the code
  requires audio to be playing.
- Stopping sends interval 0. There are no battery measurements in the repo or issues. Streaming only runs
  while a call rings or the head-tracking screen is open, which bounds the cost.
- The rewrite shows state `WAITING` until the first sensor frame arrives, then `ACTIVE`.

**Relevance to Googlebooks.** There is no telephony. The PodLink options are a self-managed ConnectionService/VoIP
"answer" (if we host calls), mapping nod/shake to accept/dismiss our own prompts (for example the connection
popup), or media controls. Re-tune the thresholds: they are unitless raw values tuned on AirPods Pro 2.

---

## 6. BLE proximity-pairing adverts

Yes, LibrePods parses them (`bluetooth/BLEManager.kt`, 498 lines). The Linux equivalents are `linux/ble/*` and
`linux-rust/src/bluetooth/le.rs`; the doc is `Proximity Pairing Message.md` on `linux/rust`.

- **Scan:** a `ScanFilter` on manufacturer ID 76 (0x004C) with data `07 19` (type 0x07, length 25), mask
  `FF FF`, using `SCAN_MODE_LOW_LATENCY`, `MATCH_MODE_AGGRESSIVE` and `reportDelay 500 ms`. It is always on while
  the service runs. That power cost matters on a laptop; prefer `SCAN_MODE_LOW_POWER` or PendingIntent scans.
- **Address filter:** adverts are **only accepted from addresses that resolve with the stored IRK**
  (`BluetoothCryptography.verifyRPA`, standard `ah()` = AES-128(IRK, prand‖0¹³)[0..2] with byte-reversed
  key and data). **So LibrePods' BLE path only works after at least one AAP session delivered the IRK.**
- **Layout** (manufacturer data after the company ID):

| Byte | Field |
|---|---|
| 0 | 0x07 proximity pairing |
| 1 | length (0x19 = 25 on current models) |
| 2 | 0x01 paired / 0x00 pairing mode |
| 3-4 | model id, read big-endian as `[3]<<8|[4]` (0x0E20 Pro, 0x1420 Pro 2, 0x2420 Pro 2 USB-C, 0x0220 AirPods 1, 0x0F20 AirPods 2, 0x1320 AirPods 3, 0x1920 AirPods 4, 0x1B20 AirPods 4 ANC, 0x0A20 Max, 0x1F20 Max USB-C) |
| 5 | status bits: 0x02/0x08 in-ear (L/R swapped by `primaryLeft XOR thisInCase`), 0x04 both in case, 0x10 one in case, 0x20 primary is left, 0x40 this pod in case |
| 6 | pod battery nibbles: 0-9 ×10 %, 0xA-0xE = 100 %, 0xF = n/a. Left = low nibble and right = high nibble when the left bud is primary (status bit 0x20); swapped otherwise |
| 7 | **lower nibble = case battery** (same encoding), **upper nibble = charging flags**: bit0 left, bit1 right (swapped when the right bud is primary), bit2 case. The Markdown doc and the C++ comment say the opposite, but both the Kotlin (`BLEManager.kt` L457-462) and C++ (`linux/ble/blemanager.cpp` L177-184) code read case = low and flags = high |
| 8 | lid: bit3 0 = open; bits 0-2 = open counter |
| 9 | colour (0 white, 1 black, 2 red …) |
| 10 | connection state: 0 disconnected, 4 idle, 5 music, 6 call, 7 ringing, 9 hanging up |
| 11-26 | 16-byte encrypted payload |

- **Encrypted payload:** the last 16 bytes are decrypted with the **ENC_KEY from opcode 0x31**, as a single
  AES-128-ECB block (`Cipher("AES/ECB/NoPadding")`, key used as-is, not reversed). Decrypted bytes [1]/[2] are
  left/right battery (swapped when the right bud is primary) and [3] the case; bit 7 = charging, low 7 bits
  = 0-100 %; 0xFF (or 127 while charging) means unknown. This gives **1 % battery granularity** instead of
  10 % steps.
- Lid open → iOS-style popup overlay. Lid state is forced closed after 2.5 s without adverts; devices go stale after 15 s.
- The BLE connection state is used to decide auto takeover (take over only if the AirPods are Idle/Disconnected, per preferences).

---

## 7. Android app architecture (`main`)

- **Build:** one Gradle module `:app`. AGP 9.2.1, Kotlin 2.3.21, compileSdk/targetSdk **37**, minSdk **33**
  (Play flavor 36), NDK r30, CMake (two native libs). All ABIs are built (no `abiFilters`), so x86_64 is
  included; the Xposed ELF parser is 64-bit only.
- **UI:** Compose BOM 2026.05.00, **material3 1.5.0-alpha21 with `ExperimentalMaterial3ExpressiveApi`** (an
  "M3E" theme is the default since June 2026, alongside an Apple-style theme). Also Navigation 3 1.1.2, Haze
  (blur), Kyant backdrop (liquid glass), AboutLibraries, Play Billing 8.3 and Play Review.
- **Rewrite** (`android/rewrite`): Room 3, protobuf-kotlin 4.36, StateFlows instead of broadcasts, multi-device
  `Device<State,Settings,Metadata>` model, material3 1.5.0-alpha27.
- **Manifest permissions:** `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` (neverForLocation), `BLUETOOTH_ADVERTISE`,
  legacy `BLUETOOTH`/`BLUETOOTH_ADMIN`, `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_CONNECTED_DEVICE`,
  `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `SYSTEM_ALERT_WINDOW`, `READ_PHONE_STATE`,
  `ANSWER_PHONE_CALLS`, `MODIFY_AUDIO_SETTINGS`, billing. There are also privileged ones that only work as a
  priv-app: `BLUETOOTH_PRIVILEGED`, `MODIFY_PHONE_STATE`, `LOCAL_MAC_ADDRESS`, `INTERACT_ACROSS_USERS`.
  `uses-feature telephony required=false`.
- **Main classes:**

| Class | Lines | Role |
|---|---|---|
| `services/AirPodsService.kt` | 3,218 | The god-object foreground service (`foregroundServiceType="connectedDevice"`, START_STICKY). Detection, connect, packet callbacks, ear-detection media logic, takeover/multipoint, notifications, widgets updates, head tracking, call handling, metadata |
| `bluetooth/AACPManager.kt` | 1,350 | Packet builders and parser, control-command state cache and listeners, smart-routing packets |
| `bluetooth/BLEManager.kt` | 498 | Proximity-advert scanner/parser |
| `bluetooth/ATTManager.kt` | 211 | ATT client over the PSM-31 socket |
| `bluetooth/BluetoothConnectionManager.kt` | 76 | Global socket holders + reflective socket factory |
| `utils/MediaController.kt` | 363 | `AudioManager.dispatchMediaKeyEvent` play/pause/next/prev, `registerAudioPlaybackCallback` to know who started playback, CA volume ducking via `setStreamVolume` ramps |
| `services/AirPodsQSService.kt` | 279 | One QS tile "ANC Mode" (tap cycles modes; dialog variant `QuickSettingsDialogActivity`) |
| `presentation/widgets/{BatteryWidget,NoiseControlWidget}.kt` | 37/99 | RemoteViews widgets (XML layouts, not Glance) |
| `presentation/overlays/PopupWindow.kt`, `IslandWindow.kt` | 290/758 | `TYPE_APPLICATION_OVERLAY` windows (need `SYSTEM_ALERT_WINDOW`): iOS-style bottom-sheet popup on lid open (VideoView animation), and a "Dynamic Island" pill on connect/takeover |
| `presentation/viewmodel/AirPodsViewModel.kt` | 784 | Binds to the service |
| `presentation/screens/*` | | ~20 screens (settings, accessibility, transparency, hearing aid, EQ, head tracking, troubleshooter, onboarding) |
| `receivers/BootReceiver.kt` | 45 | Starts the service on boot/update |
| `utils/KotlinModule.kt` + `cpp/*` | | Xposed module + native hooks |

- **Detection:** no CompanionDeviceManager.
  - At service start it loops over `bondedDevices`, calls `fetchUuidsWithSdp()`, and for devices whose
    `uuids` contain `74ec2172-…722a` checks the A2DP proxy's connected devices.
  - A receiver on `ACL_CONNECTED` / `ACTION_UUID` (plus A2DP/HFP state and others) matches the UUID or a saved MAC.
  - PR [#794](https://github.com/librepods-org/librepods/pull/794) stops the SDP query of every bonded device.
  - PR [#791](https://github.com/librepods-org/librepods/pull/791) only opens AACP when an ACL exists.
- **Reconnection:** there is no retry loop. It reconnects on the next ACL/UUID broadcast, from the manual
  "Reconnect to last device" button (`reconnectFromSavedMac`), or via BLE "Disconnected" state.
  [#784](https://github.com/librepods-org/librepods/issues/784) shows the BLE path grabbing the AirPods from
  other hosts without checking preferences (fix in PR [#804](https://github.com/librepods-org/librepods/pull/804)).
- **Audio routing:** `connectAudio`/`disconnectAudio` call the hidden A2DP/HEADSET proxy methods
  `setConnectionPolicy`/`connect`/`disconnect` by reflection. These are privileged, so they are effective only
  as a priv-app. The app also disconnects audio when both buds are charging.
- **Ear detection → media** (`processEarDetectionChange`, L1233):
  - both out → `sendPause(force)`, and optionally disconnect audio;
  - first bud in → connect A2DP, then play once A2DP connects;
  - one-of-two removed → pause.
  - `iPausedTheMedia`/`userPlayedTheMedia` flags avoid resuming media the user paused.
- **Local MAC:** needed for smart routing. It comes from `LOCAL_MAC_ADDRESS` (priv-app) or
  `su -c settings get secure bluetooth_address`.
- **Notifications:** an "Background Service Running" FGS notification, a battery notification with custom
  RemoteViews, and a socket-failure channel (currently disabled with an early `return`).
- **Large screen / desktop:** nothing. There is no WindowSizeClass, list-detail or resizable handling. Tablet
  and foldable users complain about stretched UI and a huge island overlay
  ([#657](https://github.com/librepods-org/librepods/issues/657), [#772](https://github.com/librepods-org/librepods/issues/772),
  which prototypes an adaptive layout in a fork). The overlays assume a phone status-bar notch. **PodLink can
  differentiate here.**

### Most reusable pieces (if GPL were acceptable) and their Android coupling

| File (branch) | Lines | What | Android-only deps |
|---|---|---|---|
| `bluetooth/aacp/types/Opcodes.kt` (rewrite) | 91 | opcode enum | none |
| `bluetooth/aacp/types/ControlCommand.kt` (rewrite) | 115 | control-command IDs + (de)serialise | none |
| `bluetooth/aacp/types/Capability.kt` (rewrite) | 70 | capability IDs and value sizes | kotlinx.serialization |
| `bluetooth/aacp/packet/*.kt` (rewrite, 12 files) | ~720 | typed parsers/builders (battery, ear, control, info, keys, RTBuddy, stem, rename, EQ, audio source, connected devices) | `android.util.Log` only |
| `proto/rtbuddy.proto` + `RTBuddyManager.kt` (rewrite) | 60 + 98 | sensor-stream envelope, start/stop at an interval | protobuf-lite/kotlin |
| `bluetooth/AACPManager.kt` (main) | 1,350 | everything, incl. smart-routing blobs | `Log`, `BluetoothSocket` in `sendPacket` |
| `utils/GestureDetector.kt` (main/rewrite) | 416/413 | nod/shake detector | `Log`, `AirPodsService`, `GestureFeedback` (SoundPool); core math is pure Kotlin |
| `utils/HeadOrientation.kt` (main) | 101 | pitch/yaw + calibration | none (coroutines Flow) |
| `utils/BluetoothCryptography.kt` (main) | 76 | RPA resolve (`ah`, `e`) | none (javax.crypto) |
| `bluetooth/BLEManager.kt` (main) | 498 | advert parse + decrypt | scanner, SharedPreferences; parse functions pure |
| `data/Transparency.kt`, `data/HearingAid.kt` (main) | 181/192 | ATT payload codecs | none |
| `data/AirPods.kt` (main) / `devices/AirPods.kt` (rewrite) | 277/414 | model numbers → capabilities | `R.drawable` refs |
| `bluetooth/BluetoothConnectionManager.kt` (main) | 76 | reflective socket creation | Android BT |

The protocol layer is almost free of Android code. A clean-room PodLink version should live in a pure-Kotlin
(JVM/KMP) module with only the socket and scanner in the Android layer. That module would be unit-testable
on the VM, which fits the "no local emulators" constraint.

---

## 8. Known limitations, open bugs, Android 17, desktop

- **Firmware 9A348 (mid-Sept 2026):**
  - HFP flapping from the missing CR in `AT+IPHONEACCEV`, on all AOSP devices;
  - "act as Apple" drops the link every ~40 s ([#782](https://github.com/librepods-org/librepods/issues/782));
  - disconnect reports: [#789](https://github.com/librepods-org/librepods/issues/789), [#653](https://github.com/librepods-org/librepods/issues/653).
- **Android 17 + hook:** A2DP/HFP disconnect cycling with the forced `l2c_fcr_chk_chan_modes` hook on a device that
  already has the fix ([#700](https://github.com/librepods-org/librepods/issues/700)). A HiddenApiBypass
  constructor mismatch was also reported there (from a different app, `com.android.bluetooth.bthelper`).
- **Takeover grabbing AirPods** from a desktop/iPad on app start ([#784](https://github.com/librepods-org/librepods/issues/784), PR #804).
- **Model bugs:** Max treated as Pro 2 with no battery (#783, PR #767); Pro 1 shows Adaptive (#769); 127 % battery
  (#737); case battery 0 % when both buds are out (by design; #250).
- **Other open bugs:**
  - head gestures: shake read as nod (#555); auto-disable on the Play build (#648);
  - widgets and status-bar battery (#758, #673, #679);
  - QS tile behavior (#755);
  - automatic mic mode causes reconnects (#730);
  - media not restored after reconnect (#708);
  - OxygenOS/Oppo Find X9 handshake issues (#726);
  - 32-bit devices (#734).
- **Linux:** a cluster of PipeWire/WirePlumber profile issues (#795, #798, #799) and a stale release (#725).
- **Desktop/large screen:** no support today (#657, #772). Nothing in the repo or issues mentions ChromeOS,
  Googlebook or desktop-mode Android. The app's assumptions don't fit a laptop:
  - telephony-driven head gestures;
  - `SYSTEM_ALERT_WINDOW` phone-style overlays;
  - an always-on LOW_LATENCY BLE scan;
  - `su` fallbacks.
- **Security/privacy:** proximity keys are logged (PR #810), and an "APK signing certificate fingerprint"
  request is open (#802).
- **Android 17 status.** The L2CAP fix is in AOSP A17 (section 4.4). The known A17-specific problems are
  the hook-induced disconnect cycling (#700) and the firmware-induced HFP flap and Apple-DID drops (#782).
  LibrePods targets and compiles against SDK 37.
- **x86_64.** The v1.0.0-rc1 FOSS APK ships x86 and x86_64 builds of both native libs. Nothing in LibrePods'
  protocol code is ABI-specific. For PodLink the protocol layer is pure Kotlin anyway.
- **Other apps for context:**
  - [CAPod](https://github.com/d4rken-org/capod) (GPL-3.0; icons/docs/translations excluded) added **rootless
    AAP over L2CAP on 2026-03-30** alongside its BLE features. Its `L2capSocketFactory.kt` tries
    `BluetoothSocketSettings`, then a HiddenApiBypass-style `setHiddenApiExemptions("Landroid/bluetooth/")`
    + `createInsecureL2capSocket(psm)`. Its maintainer calls root "out of scope". Latest v5.4.0-rc0 (2026-09-24).
  - MagicPods: Windows (paid, closed) and Linux/Steam Deck (GPL-3.0 core); no Android version.
