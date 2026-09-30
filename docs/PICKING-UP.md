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

Next steps:
1. User runs first-run on the HP (permission → companion picker → tile → notifications).
2. Record `./hol head 10` while the user nods and shakes; fix HeadMotion offsets + detector defaults.
3. Verify service auto-start on connect, tile, notification actions, ear pause, nearby alert.
4. CI workflow (core tests + debug build), README, licences, release v0.1.
