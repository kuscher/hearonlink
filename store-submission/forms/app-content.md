# App content declarations (Play Console › Policy › App content)

| Declaration | Answer |
|---|---|
| Privacy policy | https://googlebook.studio/privacy/hearonlink |
| Ads | No, the app contains no ads |
| App access | All functionality is available without special access (no login). It needs AirPods (or Beats with an Apple chip) to connect to; reviewer note below |
| Content rating | See `content-rating.md` |
| Target audience | 13 and over (13–15, 16–17, 18+). Not designed for children, so the Families policy doesn't apply |
| Data safety | See `data-safety.md` |
| News app | No |
| Government app | No |
| Financial features | My app doesn't provide any financial features |
| Health apps | Heart rate readout from AirPods Pro 3 only (on the device, no medical claims); see the form for the closest category ("Activity and fitness": Google's example there is "tracking body vitals related to fitness (like heart rate)"). The health policy asks for the not-a-medical-device disclaimer in the description: it's the "Not a medical device" paragraph |
| Advertising ID | Not used (no ad or analytics SDKs) |
| Permissions | Nearby devices (Bluetooth connect, scan with `neverForLocation`), notifications, companion device (presence, start foreground services and run in background), phone (`READ_PHONE_STATE`, `ANSWER_PHONE_CALLS`: optional, phones only, answering a call with a nod; neither is in Play's restricted call-log/SMS group), a `<queries>` entry for apps with a launcher icon (the "Open an app" picker; not `QUERY_ALL_PACKAGES`, so no declaration), and a `connectedDevice` foreground service (declaration below). `WAKE_LOCK`, `ACCESS_NETWORK_STATE` and `RECEIVE_BOOT_COMPLETED` come with WorkManager, which the widget library uses. **No accessibility service since 0.2.0 (2)** |

## Declarations that need more than a tick

- **Accessibility API: not needed any more.** 0.1.0 (1) had an optional accessibility service for system actions
  (Overview, Back, Screenshot…). 0.2.0 (2) removed it: the manifest has no `BIND_ACCESSIBILITY_SERVICE` service. Once
  the 0.2.0 bundle replaces the 0.1.0 draft on the closed-testing track, the Console stops asking for this declaration
  (if it was saved for the old draft, clear it). Show desktop and Open an app are ordinary activity starts that Android
  allows from the background for the companion app of a device the user picked in the system's device picker.
- **Foreground service `connectedDevice`.** The answers, word for word, are in
  [foreground-service.md](foreground-service.md). Needs a **video**: [../video/README.md](../video/README.md).

## Instructions for reviewers (App access)

> HearOn Link is a companion app for Apple AirPods and needs a pair to do anything: pair AirPods (2nd generation or
> later, AirPods Pro or AirPods Max) in Android's Bluetooth settings on a device running Android 17 (or Android 16 QPR3
> on a Pixel), open HearOn Link, tap Allow for Nearby devices and pick the AirPods in the system's device list. No
> account or login exists. Without AirPods the app shows its "No AirPods yet" state and the setup. The video linked in
> the foreground-service declaration shows the app working with AirPods Pro.

## Things a reviewer or the pre-launch report may raise

- **Non-SDK interface.** Android has no public call for a classic Bluetooth L2CAP socket, so the app calls the hidden
  `BluetoothDevice.createL2capSocket(int)` (through AndroidHiddenApiBypass, exempting only `BluetoothDevice` and
  `BluetoothSocket`). No Play policy names non-SDK interfaces; the pre-launch report lists them as a warning. The call
  still needs the `BLUETOOTH_CONNECT` permission the user granted, so it doesn't get around the permission model. The
  README's "How it works" says the same in public.
- **Heart rate (simpler option).** The Health page is untested (no AirPods Pro 3 at hand). Removing the readout would
  let the Health declaration say "no health features" and drop the disclaimer paragraph.
- **Pre-launch devices** have no AirPods, so the report only exercises the setup screens.
