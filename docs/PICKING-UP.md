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

Next steps:
1. User runs first-run on the HP (permission → companion picker → tile → notifications).
2. Record `./hol head 10` while the user nods and shakes; fix HeadMotion offsets + detector defaults.
3. Verify service auto-start on connect, tile, notification actions, ear pause, nearby alert.
4. CI workflow (core tests + debug build), README, licences, release v0.1.
