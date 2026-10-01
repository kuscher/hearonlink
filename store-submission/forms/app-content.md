# App content declarations (Play Console › Policy › App content)

| Declaration | Answer |
|---|---|
| Privacy policy | https://googlebook.studio/privacy/hearonlink |
| Ads | No, the app contains no ads |
| App access | All functionality is available without special access (no login). It needs AirPods (or Beats with an Apple chip) to connect to |
| Content rating | See `content-rating.md` |
| Target audience | 13 and over (13–15, 16–17, 18+). Not designed for children, so the Families policy doesn't apply |
| Data safety | See `data-safety.md` |
| News app | No |
| Government app | No |
| Financial features | My app doesn't provide any financial features |
| Health apps | Heart rate readout from AirPods Pro 3 only (on the device, no medical claims); see the form for the closest category |
| Advertising ID | Not used (no ad or analytics SDKs) |
| Permissions | Nearby devices (Bluetooth connect, scan with `neverForLocation`), notifications, companion device (presence, start foreground services and run in background), phone (`READ_PHONE_STATE`, `ANSWER_PHONE_CALLS`: optional, phones only, answering a call with a nod; neither is in Play's restricted call-log/SMS group), accessibility service and a `connectedDevice` foreground service (declarations below) |

## Declarations that need more than a tick

- **Accessibility API.** HearOn Link is not an accessibility tool (`isAccessibilityTool` false). Its optional
  system-actions service only performs global actions the user picks for a stem press or head gesture (Home, Overview,
  Back, Notifications, Quick Settings, Screenshot, Lock). It has no event types, `canRetrieveWindowContent` false, no
  key or motion filtering, and collects nothing. The in-app disclosure is the "Turn on system actions" card, shown when
  a picked action needs the service, before it opens Accessibility settings. Needs a **video**: the card, the consent
  in Accessibility settings, and a stem press doing Overview or Show desktop.
- **Foreground service `connectedDevice`.** `LinkService` keeps the control channel to the user's AirPods open while
  they're connected (battery, listening mode, stem presses, ear detection), started by Android's companion-device
  presence when the AirPods connect, with a notification showing the battery. Needs a **video**: the AirPods
  connecting, the notification, and a setting changed from the notification or the tile while the app is closed.
