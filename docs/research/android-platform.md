# PodLink: Android platform APIs, design system and store status

Research date: 2026-09-30. Scope: a Kotlin/Compose, Material 3 Expressive AirPods controller for
Android 17 (API 37) Googlebooks (desktop-mode Android, x86_64 + arm64) and phones, minSdk ~34,
shipped as a normal APK (no root, no adb grants) via GitHub, later Google Play and googlebook.studio.

How this was verified:
- The local SDK `~/Android/Sdk/platforms/android-37.0` (Platform 17, rev 2, extension level 22) plus
  `android-35`. There is no `~/Android/Sdk/sources`, so I used `android-stubs-src.jar` and
  `data/api-versions.xml` from the platform.
- AOSP sources on android.googlesource.com: `android17-release` branch and the
  `android-17.0.0_r1` tag, plus a blobless clone of `platform/packages/modules/Bluetooth` for `git log`.
- Google's published Android 16 hidden-API flags file.
- Read-only `adb` queries on the HP Googlebook 14: build `(build)`,
  SDK 37 (`sdk_full` 37.1), arm64-v8a.

Web-only claims are linked, and anything unverified is marked **(unverified)**.

---

## 1. Classic (BR/EDR) L2CAP to PSM 0x1001

### Verdict: no public API. A normal app cannot open a BR/EDR L2CAP socket to a fixed PSM on API 37.

There is no public API through API 37, and the Android 16 flags file puts every hidden route on the
non-SDK blocklist. What did change is the Bluetooth *stack*: since Android 17 (and Pixel Android 16
QPR3), it accepts the AirPods L2CAP channel. The only remaining blocker for a non-root app is
therefore the Java non-SDK restriction on creating the socket.

### Public socket APIs as of API 37

Checked against `api-versions.xml` and the stubs.

| API | Since | What it does |
|---|---|---|
| `BluetoothDevice.createL2capChannel(int psm)` / `createInsecureL2capChannel(int psm)` | 29 | Javadoc: "The supported Bluetooth transport is **LE only**." These are LE CoC sockets. |
| `BluetoothSocketSettings` + `BluetoothSocketSettings.Builder` | **36** | Builder methods: `setSocketType(int)`, `setL2capPsm(@IntRange(from=128,to=255) int)`, `setRfcommUuid(UUID)`, `setRfcommServiceName(String)`, `setEncryptionRequired(boolean)`, `setAuthenticationRequired(boolean)`, `build()`. |
| `BluetoothDevice.createUsingSocketSettings(BluetoothSocketSettings)` | **36** | Client side |
| `BluetoothAdapter.listenUsingSocketSettings(BluetoothSocketSettings)` | **36** | Server side |
| `BluetoothSocket.TYPE_LE = 4` | **36** | New constant |
| `BluetoothSocket.TYPE_L2CAP = 3` | old | Public, but only as a return value of `getConnectionType()`. LE CoC sockets also report `TYPE_L2CAP`. |

The builder rejects BR/EDR L2CAP in `android17-release`, in
[BluetoothSocketSettings.java](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/android17-release/framework/java/android/bluetooth/BluetoothSocketSettings.java):

```java
public Builder setSocketType(@SocketType int socketType) {
    if (socketType != BluetoothSocket.TYPE_RFCOMM
            && socketType != BluetoothSocket.TYPE_LE) {
        throw new IllegalArgumentException("invalid socketType - " + socketType);
    }
```

`setL2capPsm()` also throws outside 128..255. The other builder options (`setDataPath`,
`setSocketName`, `setHubId`, `setEndpointId`, `setRequestedMaximumPacketSize`) are all `@SystemApi`,
used for socket offload, and need `BLUETOOTH_PRIVILEGED`. So `createUsingSocketSettings` is
**RFCOMM or LE CoC only**. Reference:
[BluetoothSocketSettings.Builder](https://developer.android.com/reference/android/bluetooth/BluetoothSocketSettings.Builder).

**New in API 37 and useful to PodLink** (`BluetoothDevice`):
- `connect()` / `disconnect()` connect or disconnect all profiles. They need `BLUETOOTH_CONNECT` plus either `BLUETOOTH_PRIVILEGED` **or a CompanionDeviceManager association**. In the Android 16 flags file they were `system-api`.
- `fetchUuids(int transport)`
- `createBond(int transport)`
- `cancelBondProcess()`
- `getBondStatus(int)`
- `connectGatt(BluetoothGattConnectionSettings, Executor, BluetoothGattCallback)`

### Hidden routes and their non-SDK status

Source: Google's
[Android 16 hiddenapi-flags.csv](https://dl.google.com/developers/android/baklava/non-sdk/hiddenapi-flags.csv)
(SHA-256 `9102af02…8074`, matches the
[non-SDK restrictions page](https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces)).
No Android 17 CSV is published yet: `/cinnamonbun/`, `/android17/` and similar paths return 404.

```
Landroid/bluetooth/BluetoothSocket;-><init>(Landroid/bluetooth/BluetoothDevice;IZZILandroid/os/ParcelUuid;)V,blocked
Landroid/bluetooth/BluetoothSocket;-><init>(... all 6 constructors ...),blocked
Landroid/bluetooth/BluetoothDevice;->createL2capSocket(I)Landroid/bluetooth/BluetoothSocket;,lo-prio,max-target-o
Landroid/bluetooth/BluetoothDevice;->createInsecureL2capSocket(I)Landroid/bluetooth/BluetoothSocket;,lo-prio,max-target-o
Landroid/bluetooth/BluetoothSocket;->TYPE_L2CAP_BREDR:I,lo-prio,max-target-o
Landroid/bluetooth/BluetoothDevice;->setMetadata(I[B)Z,sdk,system-api,test-api
Ldalvik/system/VMRuntime;->setHiddenApiExemptions([Ljava/lang/String;)V,blocked,core-platform-api
```

- **Android 17 source:** the hidden helpers are still `@Hide`, in [BluetoothDevice.java @ android17-release](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/android17-release/framework/java/android/bluetooth/BluetoothDevice.java):
  - `public BluetoothSocket createL2capSocket(int channel)` calls `new BluetoothSocket(mAdapter, this, TYPE_L2CAP, true, true, channel, null)`. It no longer declares `IOException`.
  - The package-private constructor changed shape to `BluetoothSocket(BluetoothAdapter, BluetoothDevice, int type, boolean auth, boolean encrypt, int port, ParcelUuid uuid)`. On Android 16 it was `(BluetoothDevice, int, boolean, boolean, int, ParcelUuid)`.
  - LibrePods tries five constructor shapes because of this churn. **If you use a bypass, call `createL2capSocket(0x1001)` by reflection rather than the constructor**, because its signature has been stable.
- **Service side has no PSM or type gate.** `BluetoothSocketManagerBinder.connectSocket()` ([source](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/android17-release/android/app/src/com/android/bluetooth/btservice/BluetoothSocketManagerBinder.java)) only runs `enforceActiveUser()` and `Util.enforceConnectPermissionForPreflight` (BLUETOOTH_CONNECT). Once a `BluetoothSocket` object exists, `connect()` works for a normal app holding `BLUETOOTH_CONNECT`. `BLUETOOTH_PRIVILEGED` is only required for offload data paths.
- **Permissions needed:** `BLUETOOTH_CONNECT` only. `BLUETOOTH_PRIVILEGED` is `signature|privileged` and can't be granted to a Play or sideloaded app.

**Hidden-API bypass options**

| Option | How | Status on 36/37 |
|---|---|---|
| [LSPosed HiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass) (`org.lsposed.hiddenapibypass:hiddenapibypass`) | Uses `Unsafe` and `VMRuntime.setHiddenApiExemptions` | Maven Central latest is 6.1 (2025-02). The README badge claims Android 1.0–17 and the repo had commits in June 2026. The [Android 16 release notes](https://developer.android.com/about/versions/16/release-notes) (Beta 1 through 4.1) warn that apps using it "might experience intermittent crashes … because these libraries rely on internal ART structures". Its README also says: "Google Play doesn't allow apps to use hidden APIs, reporting library usage will cause your app to fail app review, you need to disable dependencies info reporting". |
| LSPass (same repo) | `Property.of()` trick | README: "can be blocked as easily as meta-reflection". |
| LibrePods' native trick ([bluetooth_socket.cpp](https://github.com/librepods-org/librepods/blob/main/android/app/src/main/cpp/bluetooth_socket.cpp)) | `JNI_OnLoad` spawns a pthread, `AttachCurrentThread`s it, and calls `VMRuntime.getRuntime().setHiddenApiExemptions(["Landroid/bluetooth/BluetoothSocket;","Landroid/bluetooth/BluetoothDevice;"])`. A thread with no Java caller frame is treated as trusted. **The class and method names are XOR-obfuscated** (key 0x47), presumably to dodge static scanners. | LibrePods targets and compiles against API 37. Its Play flavour sets `minSdk 36`. |
| Target API ≤ 27 | `createL2capSocket` is `max-target-o`, so an app targeting 26/27 could call it by plain reflection with no bypass | Not usable on Play (new apps must target API 36 since 2026-08-31). Legacy-target compatibility behaviour makes it a poor sideload option too. |

**Play precedent** (from the store sub-research; details in section 5):
- LibrePods (GPL-3.0, on Play since 2026-04-28, 10K+ installs) ships the native bypass.
- CAPod (`eu.darken.capod`, 500K+) reportedly ships a HiddenApiBypass-derived `HiddenApiBypass.kt` **(unverified by me)**.
- The Play policy text doesn't mention non-SDK APIs explicitly. The practical risk is **breakage on OS updates plus pre-launch-report errors** for blocked APIs, not a named policy.

### The AOSP stack fix that made non-root AirPods L2CAP possible

**The bug.** AirPods reject Android's L2CAP ERTM negotiation on PSM 0x1001. `l2c_fcr_chk_chan_modes()` then returned false, so the stack dropped the channel. Root tools such as LibrePods' Xposed module hooked exactly this function (`l2c_fcr_hook.cpp`).

**The fix.** Commit [`51b17545d4` "Update Interop fix to be custom UUID based"](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/51b17545d46b212a98b5b6f0bb58f99760ddde1a%5E%21/):
- Author Bhakthavatsala Raghavendra, 2025-12-12, Bug 371797042, Change-Id `Ia74a6ff634f3b2fff385d6c5e6f1840bb88a0ea6`.
- Commit message: "Update interop fix for airpods to skip ERTM related checks based on custom UUID".
- Files changed:
  - `system/stack/l2cap/l2c_fcr.cc`: `l2c_fcr_chk_chan_modes()` now returns true when `l2c_should_skip_ertm(remote_bd_addr)`.
  - `system/stack/l2cap/l2c_link.cc`: the stack no longer sends the L2CAP Extended Features info request to such devices.
  - `system/stack/l2cap/l2c_utils.cc`: new `bool l2c_should_skip_ertm(const RawAddress&)` returns true when the peer's **stored SDP UUIDs** (`btif_storage_get_services`) contain **`74ec2172-0bad-4d01-8f77-997b2be0722a`**. This is the AirPods AAP service UUID, the same one LibrePods passes to its socket.
- It replaced [`ab862ce1a8`](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/ab862ce1a844da5c220e52ae779b239e7bc04f2f), an interop-database version from 2025-12-10 that was reverted in [`a2ee778a3a`](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/a2ee778a3a08a02467e56319313c09c8efe4a71d).
- The public tracker bug LibrePods links is [issuetracker 371713238](https://issuetracker.google.com/issues/371713238). A Google comment there, quoted on [HN](https://news.ycombinator.com/item?id=47527757), says "available as part of Android open source Bluetooth stack starting March 2026".

**Where it has shipped**

| Build | Has the fix? | How verified |
|---|---|---|
| `android17-release` branch and **`android-17.0.0_r1`** tag | yes | `git merge-base` and fetching `l2c_utils.cc` at the tag |
| `android16-qpr2-release` (head 2025-10-30) | **no** | same |
| Pixel Android 16 QPR3 (March 2026 Pixel drop, "with latest Google Play system update"), ColorOS/OxygenOS 16, realme UI 7.0 | yes | [LibrePods android/README](https://github.com/librepods-org/librepods/blob/main/android/README.md), [How-To Geek](https://www.howtogeek.com/google-quietly-fixed-airpods-compatibility-with-android-and-this-app-is-all-you-need/) |
| **HP Googlebook 14 (this device)** | yes | Its Bluetooth APEX is `com.google.android.bt`, versionCode **371899999**, and `/apex/com.android.bt/lib64/libbluetooth_jni.so` contains the log string `candidate device for skip ertm`. |

**Mainline status.** No Bluetooth mainline train number is published for this fix, and I couldn't confirm a backport to Android ≤16 QPR2 through Play system updates **(unverified)**. Plan on "Android 17+, Pixel 16 QPR3+, and some OEM 16 builds".

**Design implications for PodLink**
1. The fix keys on the **cached SDP UUID list**. After pairing, call `fetchUuidsWithSdp()` or the new `fetchUuids(BluetoothDevice.TRANSPORT_BREDR)` if `getUuids()` lacks `74ec2172-…`.
2. With minSdk 34, older or unpatched devices will fail the L2CAP connect. Ship a **degraded BLE-advertisement mode** (battery and lid state from Apple proximity adverts, as CAPod does) and detect it at runtime.
3. The socket still needs a hidden-API bypass. Isolate it behind one interface with a feature flag, so a Play build can drop it if review objects.

---

## 2. Device ID (DID) vendor ID: a normal app can't change it

The local DI SDP record is built once at stack start in
[`system/btif/src/btif_core.cc`](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/android17-release/system/btif/src/btif_core.cc)
(around line 168):

```cpp
.vendor = uint16_t(android::sysprop::bluetooth::DeviceIDProperties::vendor_id().value_or(LMP_COMPID_GOOGLE)),
.vendor_id_source = ...vendor_id_source().value_or(DI_VENDOR_ID_SOURCE_BTSIG),
.product = ...product_id().value_or(0), .version = ...version().value_or(0)
```

**The sysprops.** [`sysprop/device_id.sysprop`](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/android17-release/sysprop/device_id.sysprop) defines them, all `scope: Internal` and `access: Readonly`:
- `bluetooth.device_id.vendor_id`
- `bluetooth.device_id.vendor_id_source`
- `bluetooth.device_id.product_id`
- `bluetooth.device_id.version`

**Who can set them.** Under the sepolicy `private/property_contexts`, `bluetooth.*` maps to `u:object_r:bluetooth_prop:s0`. `private/property.te` has `neverallow { domain -coredomain -bluetooth -hal_bluetooth_server } bluetooth_prop:property_service set`. In practice only the Bluetooth process, system_server or the OEM build (vendor/product `build.prop`) sets them, and root can too.
- **Not** untrusted apps.
- **Not** `adb shell` either: the shell domain has no `set_prop` for `bluetooth_prop`.

**On this device:**
- `getprop -Z bluetooth.device_id.vendor_id` returns `bluetooth_prop`.
- The property is unset, so the default Google `0x00E0` with SIG source applies.

**Other options**
- No public or `@SystemApi` setter exists.
- `persist.bluetooth.*` is also `bluetooth_prop`, so it doesn't help.

**Effect.** Features LibrePods marks "needs VendorID spoofing" stay root-only:
- Loud Sound Reduction
- Hearing Aid
- Transparency customisation
- multipoint

LibrePods does this with an Xposed hook (`vendor_id_hook`, via `NativeBridge.setSdpHook`) and says root is required "regardless of your device/OS" ([README](https://github.com/librepods-org/librepods#vendorid-spoofing)). On Linux the equivalent is `DeviceID = bluetooth:004C:0000:0000` in BlueZ.

---

## 3. OS integration hooks for a non-privileged app (API 36/37)

### (a) Quick Settings tile

Verified on the Googlebook: `sysui_qs_tiles` already contains third-party `custom(...)` tiles, such as the GMS Nearby Share tile.

**Tile APIs**
- `TileService` with the `android.service.quicksettings.action.QS_TILE` intent filter.
- Active mode: `META_DATA_ACTIVE_TILE = "android.service.quicksettings.ACTIVE_TILE"` plus the static `TileService.requestListeningState(Context, ComponentName)`, which pushes battery or mode updates only when they change. This is best for PodLink.
- `META_DATA_TOGGLEABLE_TILE` for switch semantics in accessibility.
- `Tile.setSubtitle(CharSequence)` (29), e.g. "L 80 · R 75 · Case 40", and `setStateDescription(CharSequence)` (30).
- `Tile.STATE_ACTIVE`, `STATE_INACTIVE`, `STATE_UNAVAILABLE`.

**Add, tap and long-press**
- Add-tile prompt: `StatusBarManager.requestAddTileService(ComponentName, CharSequence, Icon, Executor, Consumer<Integer>)` (33). Result codes are `TILE_ADD_REQUEST_RESULT_TILE_ADDED`, `_ALREADY_ADDED` and `_NOT_ADDED`.
- Tap:
  - `onClick()` in the service, for example to cycle the noise mode.
  - `TileService.showDialog(Dialog)`
  - `startActivityAndCollapse(PendingIntent)` (34). The `Intent` overload throws `UnsupportedOperationException` on 34+.
  - `Tile.setActivityLaunchForClick(PendingIntent)` (35) makes the system launch the activity without binding the service.
- Long-press: an activity with intent-filter action `android.service.quicksettings.action.QS_TILE_PREFERENCES`. The docs recommend protecting it with `BIND_QUICK_SETTINGS_TILE`.

**Categories (36.1).** Add `<meta-data android:name="android.service.quicksettings.TILE_CATEGORY" android:value="android.service.quicksettings.CATEGORY_CONNECTIVITY"/>` (`TileService.META_DATA_TILE_CATEGORY`, `CATEGORY_CONNECTIVITY`). This sorts the tile in the QS edit page ([source.android.com](https://source.android.com/docs/core/display/quick-settings-tile)).

**No dual-target or detail panel for third-party tiles.** I found no public API in API 37 for split tap targets or a detail panel; only SystemUI's own Internet/Bluetooth tiles have that. Use tap for the quick action and long-press for preferences.

References: [TileService](https://developer.android.com/reference/android/service/quicksettings/TileService), [Tile](https://developer.android.com/reference/android/service/quicksettings/Tile).

### (b) CompanionDeviceManager (CDM): the best hook

Googlebook reports `feature:android.software.companion_device_setup`.

**Association**
- `CompanionDeviceManager.associate(AssociationRequest, Executor, Callback)`.
- `BluetoothDeviceFilter.Builder()` with `.setAddress(bondedAirPods.address)`, `.addServiceUuid(ParcelUuid("74ec2172-0bad-4d01-8f77-997b2be0722a"), null)` and/or `.setNamePattern(...)`.
- `AssociationRequest.Builder().addDeviceFilter(f).setSingleDevice(true)`. With a MAC filter and `setSingleDevice(true)`, "bonded devices are also searched among" (AOSP comment), so already-paired AirPods show immediately.
- **No device profile is needed.** There is no headphones profile; the watch, glasses and computer profiles don't fit and would be a policy smell.

**New builder options**
- 36.1: `AssociationRequest.Builder.setDeviceIcon(Icon)`.
- **37: `setExtraPermissions(Set.of(AssociationRequest.PERMISSION_GROUP_NEARBY))`.** The javadoc says "These permissions will be granted to the companion app upon a successful association", so one dialog covers pairing and the Nearby devices grant.

**What an association grants** (verified in AOSP `ActiveServices` and `BackgroundActivityStartController` @ android17-release and in [the CDM guide](https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing)):

| Grant | Details |
|---|---|
| Start FGS from background | `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND` (normal). `ActiveServices` returns `REASON_COMPANION_DEVICE_MANAGER`. |
| Run in background | `REQUEST_COMPANION_RUN_IN_BACKGROUND` (normal) puts the app on the power-save allowlist. You don't need `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, which the [Doze table](https://developer.android.com/training/monitoring-device-state/doze-standby) calls "Not acceptable" for headphone companions. |
| Background data | `REQUEST_COMPANION_USE_DATA_IN_BACKGROUND` |
| Background activity starts | Allowed: `BalVerdict(BAL_ALLOW_ALLOWLISTED_COMPONENT, "Companion App")` |
| Device control | `BluetoothDevice.setAlias()` (31) requires the association. So do **`BluetoothDevice.connect()`/`disconnect()` (37)** and `CompanionDeviceManager.removeBond(int)` (36). |
| Scan results | `ScanController` delivers results for associated device addresses even without scan permission. |

It does **not** grant FGS while-in-use (WIU) capability; see (f) and (g).

**Presence observing**
- `startObservingDevicePresence(ObservingDevicePresenceRequest)` (36), built with `ObservingDevicePresenceRequest.Builder().setAssociationId(id)`.
  - It needs the `REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE` permission (normal).
  - It replaces the deprecated `startObservingDevicePresence(String)`.
  - `setUuid(ParcelUuid)` needs `REQUEST_OBSERVE_DEVICE_UUID_PRESENCE` and the automotive-projection profile, so it isn't for PodLink.
- `CompanionDeviceService.onDevicePresenceEvent(DevicePresenceEvent)` (36). The system binds the service on `EVENT_BT_CONNECTED` for classic devices (or `EVENT_BLE_APPEARED`) and unbinds on disconnect. Binding "elevates the priority of the process".
- Service declaration: `android:permission="android.permission.BIND_COMPANION_DEVICE_SERVICE"` with action `android.companion.CompanionDeviceService`.
- This is the right trigger for "AirPods connected → open the AAP socket and start the connectedDevice FGS".

**Settings surface.** Bluetooth device details → "companion apps" row ([`BluetoothDetailsCompanionAppsController`](https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/android17-release/src/com/android/settings/bluetooth/BluetoothDetailsCompanionAppsController.java)) lets the user launch the app or "Disconnect app?", which disassociates it.

**Competitors.** LibrePods doesn't use CDM at all, so this is a clear differentiator.

### (c) Live Updates / promoted notifications: don't promote a battery notification

**The APIs** (checked in API 37):
- `Notification.FLAG_PROMOTED_ONGOING` (36)
- `Notification.Builder.setRequestPromotedOngoing(boolean)` and `EXTRA_REQUEST_PROMOTED_ONGOING` (36.1)
- `Manifest.permission.POST_PROMOTED_NOTIFICATIONS` (36.1, `normal|appop`)
- `NotificationManager.canPostPromotedNotifications()` (36)
- `Notification.ProgressStyle` (36)
- `Notification.MetricStyle` with `Notification.Metric` and `Metric.FixedInt(int, CharSequence unit)` (37). Also new in 37: `Notification.Action` style/emphasis hints and `SEMANTIC_STYLE_*`.

**The policy.** The [Live Updates guide](https://developer.android.com/develop/ui/views/notifications/live-update) lists **"Device status/battery/connected accessories"** under *inappropriate* uses. Promotion also requires ongoing, user-initiated, time-bound activities.

**What to do instead:**
- Use a normal (non-promoted) ongoing FGS notification.
- `MetricStyle` "shows up to 3 metrics when expanded", which fits Left / Right / Case exactly. Example: `MetricStyle().addMetric(Metric(Metric.FixedInt(82, "%"), "Left"))`.
- Gate it with `Build.VERSION.SDK_INT >= 37`.
- Add actions for the noise-control modes.

### (d) Showing battery in system Settings: not possible for a normal app

**The metadata route is privileged.**
- `BluetoothDevice.setMetadata(int, byte[])` and the `METADATA_UNTETHERED_LEFT_BATTERY`, `_RIGHT_BATTERY`, `_CASE_BATTERY`, `_*_CHARGING`, `_*_ICON`, `METADATA_IS_UNTETHERED_HEADSET`, `METADATA_MAIN_ICON`, `METADATA_ENHANCED_SETTINGS_UI_URI` and `METADATA_COMPANION_APP` keys are all `@Hide @SystemApi`.
- `setMetadata` is `@RequiresPermission(allOf = {BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED})` in android17-release.
- A hidden-API bypass doesn't help, because the service enforces `BLUETOOTH_PRIVILEGED` (`signature|privileged`).
- LibrePods needs its root "system app" module to show battery in Settings.
- `BluetoothDevice.getBatteryLevel()` is also `system-api`.

**The only public battery signal is HFP.**
- AirPods send Apple `AT+IPHONEACCEV` over HFP. The stack uses it for the single battery level Settings already shows.
- Apps can listen via `BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT` with category `BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_COMPANY_ID_CATEGORY + ".76"` (Apple). The extras are `EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD` and `_ARGS`.
- It needs a runtime-registered receiver plus `BLUETOOTH_CONNECT`.
- It's a useful coarse fallback only while HFP is connected.

**Where PodLink can show L/R/case:** the app, the QS tile subtitle, the notification and a widget.

### (e) Widgets on Googlebook

**Verified on the HP Googlebook:**
- `feature:android.software.app_widgets` is present.
- The home is `com.google.android.apps.nexuslauncher/.NexusLauncherActivity`.
- `dumpsys appwidget` shows NexusLauncher hosts with 3 bound widgets, so the desktop launcher hosts AppWidgets.

**APIs**
- Glance: `androidx.glance:glance-appwidget` **1.2.0** stable, 1.3.0-alpha02 (2026-08-26), and `glance-material3` at the same versions ([releases](https://developer.android.com/jetpack/androidx/releases/glance)). Glance doesn't have the M3 Expressive components, so style it by hand.
- Platform additions:
  - `AppWidgetProviderInfo.WIDGET_CATEGORY_NOT_KEYGUARD` (36)
  - `AppWidgetManager.queryAppWidgetEvents` and `AppWidgetEvent` engagement data (36.1)
  - `AppWidgetManager.OPTION_APPWIDGET_DISPLAY_ID` (37)
- **Android 17 target change:** a strict RemoteViews bitmap/icon memory cap of `1.5 × screen w × h × 4`. Exceeding it throws `IllegalArgumentException` ([behavior changes 17](https://developer.android.com/about/versions/17/behavior-changes-17)).

### (f) Background: BLE scanning, FGS and broadcasts

**BLE scanning**
- `BluetoothLeScanner.startScan(List<ScanFilter>, ScanSettings, PendingIntent)` (26) survives process death.
- Filter with `ScanFilter.Builder().setManufacturerData(0x004C, byteArrayOf(0x07, 0x19), byteArrayOf(0xFF, 0xFF))` for Apple proximity-pairing ads.
- Declare `BLUETOOTH_SCAN` with `android:usesPermissionFlags="neverForLocation"`. The "beacon filtering" that comes with `neverForLocation` is the DeviceConfig `location_denylist_advertising_data` in `AdapterService`. Its default is `"⊈0016AAFE40/00FFFFFFF0,⊆0016AAFE/00FFFFFF,⊆00FF4C0002/00FFFFFFFF"`, which covers Eddystone and **iBeacon (Apple type 0x02) only**. AirPods proximity ads (type **0x07**) are not filtered, so no location permission is needed.
- 36.1 added `ScanSettings.Builder.setRssiThreshold(int)` and `setScanType(int)`.

**Foreground service**
- Declare `android:foregroundServiceType="connectedDevice"` and the `FOREGROUND_SERVICE_CONNECTED_DEVICE` permission (normal).
- At runtime you need a granted `BLUETOOTH_CONNECT`, `_SCAN` or `_ADVERTISE` ([service types](https://developer.android.com/develop/background-work/services/fgs/service-types)). The docs suggest CDM plus presence as the alternative for long-running device connections.

**Starting the FGS from the background**
- A background start is allowed through the **CDM exemption** ([restrictions-bg-start](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)).
- `ACTION_ACL_CONNECTED` and `BluetoothA2dp`/`BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED` are on the [implicit-broadcast exception list](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions), so a manifest receiver works. They are **not** FGS-start exemptions by themselves; pair them with the CDM association. Better still, use `onDevicePresenceEvent`.

**Android 15–17 changes**
- **15:**
  - `BOOT_COMPLETED` can't start dataSync, mediaPlayback, mediaProjection or phoneCall FGS. connectedDevice is fine.
  - The `SYSTEM_ALERT_WINDOW` exemption needs a visible overlay.
- **17, all apps:** background audio hardening; see (g).
- **17, apps targeting 37:**
  - BAL hardening: `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` is replaced by `…_ALLOW_IF_VISIBLE`.
  - Safer native DCL.
  - Static final fields can't be changed by reflection or JNI.

### (g) Media control for ear detection

- `AudioManager.dispatchMediaKeyEvent(KeyEvent)` is public and needs no permission. It routes to the active media session. Also use `AudioManager.isMusicActive()` and `registerAudioPlaybackCallback(...)` / `getActivePlaybackConfigurations()` to track whether PodLink caused the pause. This is exactly what LibrePods' `MediaController.kt` does, with no notification listener.
- `MediaSessionManager.getActiveSessions(ComponentName)` needs an enabled NotificationListenerService or `MEDIA_CONTENT_CONTROL` (`signature|privileged`). Avoid it unless you need track metadata.
- **Android 17 [background audio hardening](https://developer.android.com/about/versions/17/changes/bg-audio):**
  - For all apps, background `setStreamVolume`, `adjustStreamVolume`, `adjustVolume`, `setStreamMute`, `setRingerMode`, playback and focus are silently ignored or fail unless there's a visible activity or a non-shortService FGS.
  - For targetSdk 37, the FGS also needs **while-in-use** capability.
  - The WIU list in `ActiveServices` doesn't include CDM, so a connectedDevice FGS started in the background via CDM **probably can't change volume**.
  - Media key dispatch isn't on the restricted list.
  - Test any "volume from AirPods gesture" feature on device.

### (h) Calls: head-gesture answer and decline (phones only)

**Detecting a ringing call:** `TelephonyManager.registerTelephonyCallback(...)` with `TelephonyCallback.CallStateListener` (`READ_PHONE_STATE`), or `TelecomManager.isInCall()`.

**Answering and ending**
- `TelecomManager.acceptRingingCall()` and `endCall()` need `ANSWER_PHONE_CALLS` (`dangerous|runtime`). Both are deprecated since API 29 but still present in 37.
- For self-managed or transactional (VoIP) calls they only work for privileged callers or System UI.
- The recommended `InCallService` companion path needs `MANAGE_ONGOING_CALLS` (`signature|appop`, granted through the CDM watch profile), so it's not for earbuds. `CALL_COMPANION_APP` is `normal`, but binding still needs that role or appop.
- Play treats `READ_PHONE_STATE` and `ANSWER_PHONE_CALLS` as ordinary runtime permissions, not the restricted Call Log group.

**On Googlebook:**
- `feature:android.software.telecom` is present, but there's no telephony feature.
- Calls there are VoIP (self-managed), so answer/decline gestures won't work. Hide the feature there.

---

## 4. Material 3 Expressive and Adaptive in Compose (versions as of 2026-09-30)

Versions come from `dl.google.com` maven-metadata. The sub-research checked opt-in status in the
actual source jars.

| Artifact | Stable | Pre-release |
|---|---|---|
| `androidx.compose.material3:material3` | **1.4.0** (2025-09-24). No 1.5 beta or RC. | **1.5.0-alpha29** (2026-09-23) |
| `androidx.compose:compose-bom` | **2026.09.00** (2026-09-09): material3 1.4.0, ui/foundation **1.12.1**, adaptive 1.3.0 | `compose-bom-alpha` **2026.09.01**: material3 1.5.0-alpha29, ui 1.13.0-alpha03 |
| `material3-adaptive-navigation-suite` | 1.4.0 | 1.5.0-alpha29 |
| `androidx.compose.material3.adaptive:*` (adaptive, -layout, -navigation, -navigation3) | **1.3.0** (2026-08-12) | 1.4.0-alpha02 |
| `androidx.window` | **1.5.1** | 1.6.0-alpha05 |
| `androidx.glance:glance-appwidget` / `glance-material3` | 1.2.0 | 1.3.0-alpha02 |

**Toolchain**
- Compose 1.12 compiles against API 37. The AAR metadata requires `minCompileSdk 37` and AGP ≥ 9.1; the release note says 9.2.
- material3 alpha29 pulls in foundation/ui 1.13.0-alpha01 and Kotlin stdlib 2.2.20.
- ui 1.13 alphas need compileSdk **37.1**.

**Key fact:** 1.4.0-beta01 removed every public `@ExperimentalMaterial3ExpressiveApi` API ("please switch to 1.5.0-alpha"). **Stable 1.4.0 has essentially no Expressive components**, so M3E means the 1.5.0 alphas. No official date for 1.5.0 stable (**unverified** reports say late 2026 or early 2027).

| Component (1.5.0-alpha29) | Opt-in needed? | Graduated in |
|---|---|---|
| `ButtonGroup`, connected groups (`ButtonGroupDefaults.ConnectedSpaceBetween`, `connectedLeading/Middle/TrailingButtonShapes()`) | no | alpha22 |
| `SplitButtonLayout` + `SplitButtonDefaults` | no | alpha20 |
| `HorizontalFloatingToolbar` / `VerticalFloatingToolbar` | no | alpha22 |
| `LinearWavyProgressIndicator` / `CircularWavyProgressIndicator` | no | alpha18 |
| `MotionScheme.expressive()` / `standard()` | no | alpha15 |
| `MaterialExpressiveTheme`, `expressiveLightColorScheme()` | no | alpha18 |
| `ToggleButton`, `ElevatedToggleButton`, `FilledTonalToggleButton`, `OutlinedToggleButton`, new `IconToggleButton`s | no | alpha19 |
| Emphasized typography (`displayLargeEmphasized`, …) | no | alpha16 |
| `FloatingActionButtonMenu`, `ToggleFloatingActionButton` | no | alpha19 |
| Button sizes XS–XL (`ButtonDefaults.ExtraSmall…ExtraLarge*`) | no (`contentPaddingFor` is still experimental) | alpha19 |
| **`SegmentedListItem`, `ListItemDefaults.segmentedShapes(index, count)`, `SegmentedGap`** | no | alpha23 |
| Stateful `Slider` / `VerticalSlider` | no (the stateless overload is deprecated in alpha28 and alpha29 has a breaking `onValueChange` change) | alpha16 |
| `FlexibleBottomAppBar`, Medium/Large flexible top app bars | no | alpha23 |
| `WideNavigationRail`, `ModalWideNavigationRail`, `ShortNavigationBar` | no, **stable since 1.4.0** | 1.4.0 |
| Carousel | no | alpha11 |
| **`LoadingIndicator` / `ContainedLoadingIndicator`** | **yes**, `@ExperimentalMaterial3ExpressiveApi` | reverted in alpha19 |
| **`MaterialShapes`, `RoundedPolygon.toShape()`, `Morph.toPath()`** (shape morphing) | **yes**, Expressive-experimental | reverted in alpha19 |

**Adaptive**
- `NavigationSuiteScaffold` is stable with types `ShortNavigationBarCompact`, `ShortNavigationBarMedium`, `WideNavigationRailCollapsed`, `WideNavigationRailExpanded`, `NavigationRail` and others. The default picker never chooses Expanded, so pick it yourself for Large/XL widths.
- `ListDetailPaneScaffold`, `SupportingPaneScaffold` and `NavigableListDetailPaneScaffold` still need `@ExperimentalMaterial3AdaptiveApi`.
- Window size classes: window 1.5 `WindowSizeClass.BREAKPOINTS_V2` gives width breakpoints 600 / 840 / **1200 (Large)** / **1600 (Extra-large)** dp.
- `currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)` has been **deprecated** since adaptive 1.3.0-alpha10. Use **`currentWindowAdaptiveInfoV2()`**.

**Guidance for settings screens**
- The [Settings pattern](https://developer.android.com/design/ui/mobile/guides/patterns/settings) says to group settings with containment and headings, use subscreens at 15+ items, use list-detail on large screens, and cap width rather than stretching.
- The Compose equivalent of the Android 16 Settings "grouped cards" look is `SegmentedListItem` + `ListItemDefaults.segmentedShapes(index, count)` + `Arrangement.spacedBy(ListItemDefaults.SegmentedGap)`, restarting index and count per group. The Views equivalent is MDC [List.md](https://github.com/material-components/material-components-android/blob/master/docs/components/List.md) ("expressive list variants; standard and segmented").
- SettingsLib's expressive theme is platform-internal, not a public artifact.

**Guidance for desktop**
- [Support desktop windowing](https://developer.android.com/develop/adaptive-apps/guides/support-desktop-windowing):
  - Draw your own header under the caption with `APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND` and `WindowInsets.captionBar`, avoiding `WindowInsets#getBoundingRects()`.
  - This fits the user's "native caption bar" preference.
- [Desktop quality guidelines](https://developer.android.com/docs/quality-guidelines/adaptive-app-quality/experiences/desktop): keyboard focus, shortcuts, context menus, scrollbars and cursor. They suggest hover fly-outs, which conflicts with the user's no-hover-reveal rule, so use tooltips only.

**Android 17 notes for targetSdk 37**
- On sw ≥ 600dp the app can't lock orientation, resizability or aspect ratio, and the opt-out is gone.
- RFCOMM `read()` returns −1 on close. PodLink uses L2CAP, but check for −1 anyway.
- New `ACCESS_LOCAL_NETWORK` runtime permission (not needed here).
- Edge-to-edge and predictive back have been mandatory since 36.

---

## 5. Google Play policy risks

**Trademarks**
- The [IP](https://support.google.com/googleplay/android-developer/answer/9888072), [Impersonation](https://support.google.com/googleplay/android-developer/answer/9888374) and [Metadata](https://support.google.com/googleplay/android-developer/answer/9898842) policies forbid confusing use of others' marks or logos and implying endorsement. The title limit is 30 characters.
- The accepted pattern on Play today is own brand + "for AirPods" / "Companion for AirPods", with a disclaimer and trademark attribution in the description:
  - "CAPod – Companion for AirPods"
  - "MaterialPods: AirPods battery"
  - "AndroPods – Airpods on Android"
  - "Assistant Trigger: for AirPods"
  - "PodsLink – AirPods Battery"
- [Apple's third-party trademark guidelines](https://www.apple.com/legal/intellectual-property/guidelinesfor3rdparties.html):
  - Only referential use ("for", "compatible with").
  - Never as part of your product name, and less prominent than your own name.
  - No Apple logos or Apple product imagery.
  - Suggested attribution: "AirPods is a trademark of Apple Inc., registered in the U.S. and other countries and regions."
  - The MFi badge is off-limits.
  - Draw a generic earbud icon.
- No confirmed trademark-based takedown of an AirPods companion app was found.

**Hidden APIs**
- The [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/9888379) doesn't name non-SDK use. The closest wording is "circumvent security protections".
- Technically, the pre-launch report flags blocked APIs as errors and unsupported ones as warnings.
- HiddenApiBypass' README claims library reporting "will cause your app to fail app review".
- Precedent: LibrePods and CAPod are live on Play with bypasses.
- Rated **medium risk**, mostly breakage. Keep the bypass in one module you can switch off.

**Nearby devices and location**
- There is no Play Console declaration for `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT`, but the Data safety form still applies.
- Avoid location permissions: `ACCESS_BACKGROUND_LOCATION` triggers a declaration and review.

**Foreground service declaration** ([FGS requirements](https://support.google.com/googleplay/android-developer/answer/13392821))
- For connectedDevice you give a description, the impact if deferred, and a **video** of the user-initiated flow.
- Avoid `specialUse`.

**Target API.** New apps and updates must target **API 36 since 2026-08-31**, with an extension possible to 2026-11-01 ([target API](https://support.google.com/googleplay/android-developer/answer/11926878)).

**Other permissions**

| Permission | Play status |
|---|---|
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | "Not acceptable" for this category. Use CDM instead. |
| `QUERY_ALL_PACKAGES` | Restricted. Use `<queries>`. |
| AccessibilityService | Declaration plus in-app disclosure. Avoid it, in line with the user's minimal-input-monitoring rule. |
| `READ_PHONE_STATE` / `ANSWER_PHONE_CALLS` | Ordinary runtime permissions |
| Call Log | Needs default-handler status |

**GPL-3.0 on Play**
- Fine in practice: LibrePods (GPL-3.0), CAPod (GPL-3.0), Signal (AGPL) and VLC are all on Play.
- If PodLink derives from LibrePods it **must stay GPL-3.0** and publish the source for every Play build.
- GPLv3 §6 "Installation Information" targets hardware "User Products". That's my reading, not legal advice.

**Developer verification** ([ADC help](https://support.google.com/android-developer-console/answer/16561738))
- Enforcement for sideloaded apps starts **2026-09-30 in BR, ID, SG and TH**, and goes global in 2027.
- For GitHub APKs, register the package name and signing key in the Android Developer Console ([package registration](https://support.google.com/android-developer-console/answer/16640821)). Otherwise users need the advanced flow or adb.
- Play apps are registered automatically.

## 6. googlebook.studio and the "PodLink" name

**googlebook.studio is the user's own unofficial site.** Verified by fetching it:
- Vercel, `x-robots-tag: noindex, nofollow`.
- Invite-only: "A small showcase of community-built apps for Googlebooks".
- Footer: "A personal passion project. Not affiliated with or endorsed by Google."
- Links to googlebook.google, Google Play, and `https://github.com/kuscher/googlebook-tech-listings/issues/new` ("Get listed").
- It is not an official Google store, and listing there has no policy implications beyond the user's own rules.
- Googlebook itself: the Chromebook successor, "Googlebook OS" on Android 16/17 ([developer.android.com/googlebook](https://developer.android.com/googlebook), [9to5Google](https://9to5google.com/2026/09/21/googlebook-launch/)).

**"PodLink": high clash risk**
- **"PodsLink – AirPods Battery"** (`net.podslink`, 1M+ installs, podslink.net) is on Play in the *same category*. It advertises noise control, ear detection and head gestures, and shows up when searching "podlink".
- GitHub **`zondaxxx/podlink`** (created 2026-09-05) is "AirPods companion for Android without root…".
- **Podlink / pod.link** is a podcast link service. Spotify returned it to its founder in 2025 ([Podnews](https://podnews.net/update/podlink-gathright)).
- Old US PODLINK trademarks are both cancelled (75108518, 85186295).

**Rename candidates** (no Play or GitHub clash found in quick checks; not a formal trademark search):
- **Earlet**
- **Caseside**
- **Caselid**
- **Lidlift**
- **Stemtap**. An unrelated Australian STEM-education company uses the name.

## 7. Recommended integration stack

- **Onboarding:** one CDM `associate()` with `BluetoothDeviceFilter(address + AAP UUID)`, `setSingleDevice(true)`, `setDeviceIcon` (36.1+) and `setExtraPermissions(NEARBY)` (37+). Fall back to a runtime `BLUETOOTH_CONNECT`/`BLUETOOTH_SCAN` request on 34–36.
- **Lifecycle:** `startObservingDevicePresence(setAssociationId)`, then `CompanionDeviceService.onDevicePresenceEvent(EVENT_BT_CONNECTED)`, then start the `connectedDevice` FGS (CDM exemption) and open the AAP socket to PSM 0x1001. Tear down on `EVENT_BT_DISCONNECTED`.
- **Transport:**
  - Hidden `createL2capSocket(0x1001)` behind a bypass module, only when `Build.VERSION.SDK_INT >= 37` or the stack is known-fixed.
  - Probe once, then cache "L2CAP OK / not OK".
  - Before connecting, make sure SDP UUIDs are cached (`fetchUuids(TRANSPORT_BREDR)`).
  - Fallback everywhere: BLE proximity adverts (`0x004C`/`0x07`) for battery and lid state, plus HFP `+IPHONEACCEV` (company ID 76) for coarse battery.
- **Surfaces:**
  - Active QS tile with subtitle, `CATEGORY_CONNECTIVITY` and a preferences long-press.
  - Normal ongoing FGS notification using `MetricStyle` on 37 (not promoted).
  - Glance widget, which the Googlebook launcher hosts.
  - In-app M3E UI on material3 1.5.0-alpha (BOM-alpha).
- **Ear detection:** `dispatchMediaKeyEvent` + `isMusicActive`/`AudioPlaybackCallback`. No notification listener.
- **Calls (phones only):** `ANSWER_PHONE_CALLS` + `acceptRingingCall()`/`endCall()`, SIM calls only. Hide on Googlebook.
- **Not possible without root:** DID vendor-ID spoofing, and L/R/case battery in system Settings (`setMetadata`).
