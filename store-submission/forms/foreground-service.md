# Foreground service declaration (Play Console › Policy › App content › Foreground service permissions)

Play asks for this because the manifest declares `FOREGROUND_SERVICE_CONNECTED_DEVICE`. It is the
only foreground-service type HearOn Link uses (`LinkService`, `android:foregroundServiceType="connectedDevice"`).

What the form wants, per Play Console Help ("Understanding foreground service and full-screen
intent requirements", answer 13392821, read on 1 October 2026): a description of the functionality,
the user impact if the task is deferred or interrupted, a video link, and the use case.

## Type

**Connected device**

## Use case

Tick the preset for a device connected over Bluetooth. The Help page lists one preset for this type:
**"Continuous data transfer to an external device"** (its own examples: wearable, baby monitor,
headset, car). If the Console words it differently, pick the Bluetooth / external-device option; if
none fits, choose "Other" and paste the description below.

## Description of the functionality (paste)

> HearOn Link is a companion app for AirPods. While the user's AirPods are connected over Bluetooth,
> the app keeps their control channel (a Bluetooth L2CAP connection to the headset) open in a
> connected-device foreground service. Over that connection the AirPods continuously send battery
> levels, in-ear state, stem presses and the listening mode, and the app sends the user's commands
> back. The service drives features the user sees and uses while the app's window is closed: the
> ongoing notification with the battery of each AirPod and the case and one-tap listening modes, the
> Quick Settings tile, the home-screen widget, pausing playback when an AirPod is taken out of the
> ear, low-battery alerts, and the actions the user assigned to stem presses and head gestures. The
> service starts only when AirPods the user chose in the app connect, shows a notification the whole
> time, and stops by itself about 12 seconds after they disconnect. The user can also stop it at
> any time by disconnecting the AirPods or from Android's "Active apps" list.

## User impact if the task is deferred by the system (does not start immediately)

> Until the service runs, the app has no connection to the AirPods: the notification, tile and
> widget show no or outdated battery levels and listening mode, taking an AirPod out of the ear
> does not pause playback, and the stem presses and head gestures the user customised do nothing.
> The AirPods forward customised presses to the app instead of handling them, so a delayed start
> leaves those presses without any effect.

## User impact if the task is interrupted by the system (paused or restarted)

> The Bluetooth control channel closes. Battery levels and the listening mode in the notification,
> tile and widget stop updating, ear-detection pause and conversation-awareness volume stop working
> mid-session, and custom stem presses and head gestures stop responding until the connection is
> made again. Because the AirPods only report their state over this live connection, the app
> cannot catch up later with deferred or batched work.

## Video link

**https://youtu.be/lbD5PMXHcTI** (unlisted, 27 s, recorded on the HP Googlebook with AirPods Pro on 30 September 2026; storyboard in ../video/README.md)

Like the other apps' declaration videos: unlisted on the YouTube channel, with the MP4 kept in
a private folder. Play's Help page gives no format rules
for this video beyond "demonstrating each foreground service feature … the steps the user needs to
take in your app in order to trigger the feature".

## Why this is an allowed use (for an appeal, if the first review bounces)

The Device and Network Abuse policy asks that a foreground service is user-initiated or
user-perceptible, can be stopped by the user, and runs only as long as needed:

- **User-initiated:** it runs only for AirPods the user picked in the app's setup (Android's
  companion-device picker), and only while the user has them connected (in their ears or case open).
- **User-perceptible:** an ongoing notification the whole time, with live battery and mode buttons.
- **Stoppable:** disconnect the AirPods, or use Android's Active apps › Stop.
- **Only as long as needed:** it stops itself 12 seconds after the AirPods disconnect; it never
  runs without them.
- **Not just "keeping Bluetooth connected":** the connection carries user-visible features in real
  time (ear-detection pause, stem presses, mode switches, live battery), which WorkManager or
  companion-device presence events alone cannot deliver.
