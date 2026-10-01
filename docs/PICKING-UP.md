# Picking up HearOn Link

Status 2026-09-30 (end of day 1):
- Phase 0 feasibility done (unrooted AAP, state, head tracking, writes).
- Phase 1 foundations done (core + tests, link, service, ./hol). CI not yet added.
- Phase 2 app mostly done: home (desktop two-pane + phone), pages, settings, onboarding with the
  companion picker, theme, icon. Rendered and checked with `./hol render`.
- Phase 3 written, not yet verified on device: companion presence start, tile + panel, notifications
  (MetricStyle), widget, ear pause, ducking, BLE nearby alert.
- Phase 4: demo screen done; detector needs a recorded trace (user wearing the AirPods) to confirm
  the sensor offsets and tune thresholds; call answering on phones not written yet.
- Phase 5 not started: README (passion-project style), licences screen/NOTICES, PRIVACY.md, release
  workflow, backup of the key, googlebook.studio listing (repo kuscher/googlebook-tech-listings).

Day 1, later (user request "implement the possible but not yet built"):
- Stem press actions incl. system actions (Show desktop via Home, Overview…), head gestures anytime with
  nod/shake actions (default shake = Show desktop), nod/shake for calls on phones, EQ, case sounds,
  sleep detection, AirPods ear-detection switch, optimized charging, call controls, microphone side,
  Digital Crown direction, heart rate + hearing protection (Pro 3, untested), Beats product ids.
- Battery cache (fixes wiped levels): per part with time and source, fed by the channel and BLE adverts.
- Needs the user: enable the system-actions service, try custom presses, calibrate in the demo,
  check what "Show desktop" (Home) does on the Googlebook.

Release 0.1.0 (2026-09-30): new headphones icon (tools/logo.py), README/licences/privacy/changelog,
tools/release.sh + docs/RELEASING.md, CI; GitHub release v0.1.0 published (HearOnLink.apk, SHA256SUMS);
release key backed up privately (a private folder). googlebook.studio listing text in
store-submission/googlebook-studio.md; repo made PUBLIC and listing filed as kuscher/googlebook-tech-listings#1.

Next steps:
1. User runs first-run on the HP (permission → companion picker → tile → notifications).
2. Record `./hol head 10` while the user nods and shakes; fix HeadMotion offsets + detector defaults.
3. Verify service auto-start on connect, tile, notification actions, ear pause, nearby alert.
4. CI workflow (core tests + debug build), README, licences, release v0.1.

Google Play (2026-09-30, from the Mac): app "HearOn Link: Earbuds Companion" (Play app id 4972990156565698422,
developer Fika Labs). Play App Signing uses this repo's release key (A1:12:57:4D…, uploaded with PEPK), so Play and
GitHub APKs update each other. Listing, graphics, store settings (Tools) and all App content declarations are filed
from store-submission/; the privacy policy is https://googlebook.studio/privacy/hearonlink. Version code 1 (0.1.0) is a
draft on closed testing (alpha, track 4700420611186455222) with the Google Group googlebook-studio-testers@googlegroups.com
and 178 countries. Left before Send for review: the Accessibility API and connectedDevice foreground-service
declarations, each needing a video of the real flow with AirPods (disclosure card → consent → a stem press doing
Overview; AirPods connecting → notification → a mode change while the app is closed). Record on the HP.

0.2.0 (2026-10-01, on the HP; built and installed there, NOT yet published): the accessibility service is gone
(the user: "It makes play releasing hard"; "Remove the a11y permission everywhere"), so Play needs only the
connectedDevice declaration now. Show desktop = Home intent, new "Open an app" action with a picker; both are
background activity starts, which need the companion association (verified on the HP with a temporary
`cmd companiondevice associate 10 PKG MAC`: BAL_ALLOW_ALLOWLISTED_COMPONENT, all windows minimise; without it the log
says "Background activity launch blocked" and startActivity still returns normally). The user's own install has NO
association (the setup's picker was skipped), so its Presses and Gestures pages show "Choose your AirPods first".
The old actions stored there (Overview, Screenshot, Quick Settings) now read as AirPods default / Nothing.
Kit changes are listed in store-submission/README.md ("0.2.0 (2)"). tools/play-video.sh records the declaration video
on the HP in the house style of kuscher/googlebook-tech scripts/play/videos (title card, caption band, touches shown),
blurring everything but HearOn Link and the system panels; its capture and compose steps are tested, the on-screen
flow has NOT run yet (it needs the AirPods connected and the screen for a minute).
Done 2026-09-30 (from the Mac): the declaration video was recorded on the HP with the AirPods Pro (4 scenes, 27 s,
storyboard in store-submission/video/README.md; driven by screenshot detection, so the stop scene is not in it), is
unlisted on YouTube https://youtu.be/lbD5PMXHcTI and in a private folder,
and is filed in the Console's foreground-service declaration ("Continuous data transfer to an external device").
v0.2.0 is published on GitHub; HearOnLink-0.2.0.aab (code 2) replaced 0.1.0 as the closed-testing release, the listing
and screenshots were re-uploaded from the kit, the privacy page lost its accessibility section (googlebook-tech
f758d7f), and the release was SENT FOR REVIEW (the user: "release the HearOn Link app"). Play's pre-review check
flagged org.lsposed.hiddenapibypass ("unsupported APIs", a technical-quality warning about ART updates); it was
ignored as "requires extensive changes". Follow-up: Android 16+ has a public BR/EDR socket API,
BluetoothDevice.createUsingSocketSettings(BluetoothSocketSettings.Builder().setSocketType(BluetoothSocket.TYPE_L2CAP)
.setL2capPsm(…)), worth trying on the HP so HiddenApiBypass is only needed below API 36 (or not at all).
Still open: (1) the user picks the AirPods in the app once and tries Show desktop / Open an app; (2) edit
kuscher/googlebook-tech-listings#1's description.

0.2.1 (2026-10-01, on the HP): the user's report "we lost a bunch of features" was the not-connected state (the
AirPods had left for ~2.5 min; my first diagnostic `./hol debug` wiped the app's log, so a late-reporting session
can't be ruled out). Fixes: away keeps the controls, disabled; `LinkState.view` falls back to the cache; the CDM
request names the bonded device (the old UUID-only picker stayed empty for paired AirPods); picking Show desktop /
Open an app asks for the confirmation at once; desktop Back moved into the right pane; Settings is two-pane too.
NOT verified: the real CDM single-device dialog (needs the user's tap); the log says "companion: dialog ready /
linked (id) / <error>". Unrelated find that night: system volume was muted by the another app's "another app: Filming"
Do Not Disturb rule (it disallows media).

