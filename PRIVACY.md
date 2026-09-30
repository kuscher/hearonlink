# HearOn Link privacy policy

Last updated: 30 September 2026

HearOn Link is an AirPods companion app made by Alexander Kuscher as a personal hobby project.
This policy covers the HearOn Link app for Android.

## The short version

HearOn Link doesn't collect, store or share any information about you. It has no internet
permission at all: it can't send anything anywhere. There are no accounts, ads, analytics or trackers.

## What stays on your device

- **Your AirPods' state and settings** (name, model, firmware, battery levels and when they were
  last seen, the settings you chose) are kept in HearOn Link's private app storage so the app can
  show them while the AirPods are away.
- **Two Bluetooth keys your AirPods hand out** (an identity key and an advert key) are kept in the
  same private storage. They let HearOn Link recognise your AirPods' Bluetooth adverts and read the
  case battery. They are excluded from Android backup.
- **Head-gesture calibration**: where nods and shakes show up in your AirPods' motion data. The raw
  motion data of the last calibration is kept in the app's cache so the developer can help tune the
  detector on request; nothing leaves the device unless you copy it off yourself.
- Your app settings may be included in your device backup if Android backup is on; that backup is
  managed by Android and your Google account, and the developer can't see it.

Uninstalling HearOn Link deletes all of it.

## Permissions

- **Nearby devices** (Bluetooth connect and scan): to talk to your AirPods and to read their case's
  Bluetooth adverts. Never used for location.
- **Notifications**: battery while connected, low-battery and case-nearby alerts.
- **Companion device**: Android's own picker links HearOn Link to your AirPods, so the app can start
  when they connect.
- **Phone calls** (phones only, and only if you turn on answering calls with your head): to know
  when a call rings and to accept or decline it.
- **System actions** (optional accessibility service): performs Home, Overview, Back,
  Notifications, Quick Settings, Screenshot or Lock when you press a stem or move your head, if you
  pick those actions. It receives no accessibility events, can't read the screen and doesn't see
  what you type or tap.

## Head motion

Your AirPods' motion sensor data is read only while the head-gesture demo or calibration is open,
while a call rings (if you turned on answering calls with your head), or while an AirPod is in your
ear with "Use head gestures anytime" on. It's processed on the device and isn't stored, apart from
the calibration described above.

## Contact

Questions: open an issue at https://github.com/kuscher/hearonlink.
