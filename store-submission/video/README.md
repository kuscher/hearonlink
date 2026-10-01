# The foreground-service video

Play's foreground-service declaration needs a link to a video "demonstrating each foreground
service feature" and "the steps the user needs to take in your app in order to trigger the
feature". HearOn Link has one such feature: the connected-device service that runs while AirPods
are connected. `tools/play-video.sh` records it on the Googlebook and builds the final file.

Status (30 September 2026): recorded on the HP Googlebook with AirPods Pro and composed (27 s). The
take was driven from the Mac by screenshot detection (`uiautomator dump` can't get an idle state
on the Googlebook desktop), so the notification row, the app window and the tile's panel were
found on screen and only they are sharp. The AirPods stayed connected, so the stop scene (6 below)
is not in this video; the declaration doesn't require it.

## Storyboard as recorded (27 seconds)

| # | On screen | Caption burned into the video |
|---|---|---|
| 1 | HearOn Link's ongoing notification, app window closed | AirPods connected, HearOn Link's window closed: its connected-device service runs and shows this notification (battery, listening mode). |
| 2 | The notification expanded; a tap on Noise Cancellation in it | Expanding the notification shows the listening modes: switching to Noise Cancellation from it, without opening the app. |
| 3 | HearOn Link's window; a tap on Transparency | The app shows the same state, kept by the service: Noise Cancellation. Switching back to Transparency in the app. |
| 4 | The Quick Settings tile's small panel, window closed again | With the window closed again, HearOn Link's Quick Settings tile opens this small panel: the same battery and listening mode, kept current by the service. |

The notification and the panel are shown larger than on screen (the composer zooms a small
rectangle up to 2×), so they stay readable on YouTube.

## Original storyboard (about 45 seconds, `tools/play-video.sh`)

| # | On screen | Caption burned into the video |
|---|---|---|
| 1 | The notification panel with HearOn Link's ongoing notification | AirPods connected: HearOn Link starts its connected-device service and shows this notification (battery, listening mode). |
| 2 | HearOn Link's window; a tap on Noise Cancellation | The app: battery for each AirPod and the case, and the listening mode. Switching to Noise Cancellation. |
| 3 | Window closed, notification panel open; a tap on Transparency in the notification | HearOn Link's window is closed. The service keeps running, so the notification still works: switching to Transparency from it. |
| 4 | Quick Settings with the HearOn Link tile | The Quick Settings tile shows the listening mode and battery, kept current by the service. |
| 5 | The tile's small panel | A tap on the tile opens HearOn Link's small panel. |
| 6 | The notification panel without the notification | AirPods back in their case: the service has stopped by itself and its notification is gone. |

Scenes 1 to 5 are scripted (real taps through adb on the real UI). Scene 6 needs you: put both
AirPods in the case and close the lid when the small panel has closed.

## Recording

1. Wear your AirPods; HearOn Link must show Connected (`./hol debug state`). Any build works:
   the script reads the app's state through its shell-only test hook, which releases have too.
2. Close anything you'd rather not have on screen for a minute. The script shows the desktop and
   brings your front window back at the end.
3. Run `tools/play-video.sh` and keep your hands off the Googlebook until your windows are back.
   About 35 seconds in, put the AirPods in the case.

The result is `store-submission/video/out/hearonlink-foreground-service.mp4` (1920 × 1350, H.264): a
title card, then the scenes with a numbered caption band, in the style of the other apps'
declaration videos (kuscher/googlebook-tech `scripts/play/videos`), with touches shown as dots.
`tools/play-video.sh process` rebuilds it from the same take, for example after editing a caption
or a rectangle in `out/scenes.tsv` (`tools/play_video_compose.py` does the composing).

**Privacy.** Everything outside HearOn Link's window and the system panels is blurred in the final
video: wallpaper, widgets, desktop icons, the taskbar and the status bar. Watch it once before
uploading anyway. The raw take (`out/raw.mp4`) is not blurred; `out/` is in `.gitignore`, and
neither file belongs in the public repository.

## Uploading

Like the other apps' declaration videos: upload the final file as an **unlisted YouTube video**, keep
the MP4 in a private folder, and paste the YouTube
link into the declaration (`../forms/foreground-service.md`, "Video link") and the Console.
