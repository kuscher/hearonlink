# Google Play submission kit

Everything the Play Console asks for when publishing HearOn Link, ready to copy or upload (made on 30 September 2026,
following the kits of the other Googlebook apps). Developer account Fika Labs (7424304467248438473).

## What's here

| Play Console field | File | Limit / spec |
|---|---|---|
| App name | [listing/en-US/title.txt](listing/en-US/title.txt) | 30 characters |
| Short description | [listing/en-US/short-description.txt](listing/en-US/short-description.txt) | 80 characters |
| Full description | [listing/en-US/full-description.txt](listing/en-US/full-description.txt) | 4,000 characters |
| Release notes ("What's new") | [listing/en-US/release-notes.txt](listing/en-US/release-notes.txt) | 500 characters |
| App icon | [graphics/icon-512.png](graphics/icon-512.png) | 512 × 512 PNG, full square (Play rounds the corners), from docs/images/icon.svg |
| Feature graphic | [graphics/feature-graphic.png](graphics/feature-graphic.png) | 1024 × 500, 24-bit PNG |
| Screenshots | [graphics/large-screen/](graphics/large-screen) (5) | 1920 × 1080 (16:9), 24-bit PNG. Used for phone, 7-inch, 10-inch and Chromebook |
| Store settings, contact, category | [forms/store-settings.md](forms/store-settings.md) | |
| Privacy policy | https://googlebook.studio/privacy/hearonlink | public, outside googlebook.studio's invite gate |
| Data safety | [forms/data-safety.md](forms/data-safety.md) | "No data collected" |
| Content rating (IARC) | [forms/content-rating.md](forms/content-rating.md) | expected: Everyone / PEGI 3 |
| Other App content declarations | [forms/app-content.md](forms/app-content.md) | reviewer note, permissions, what reviewers may raise |
| Foreground service declaration | [forms/foreground-service.md](forms/foreground-service.md) | texts ready to paste; needs the video link |
| The video for that declaration | [video/README.md](video/README.md), `tools/play-video.sh` | recorded on the HP with AirPods in |
| Phone screenshots (optional, not uploaded) | [graphics/phone/](graphics/phone) (8) | 1080 × 1920 (9:16), the real phone layout, straight from `./hol render` |

The title leaves Apple's trademarks out; the description names AirPods only to say what it works with, and says
HearOn Link isn't made or endorsed by Apple. The screenshots are HearOn Link's own README images with a caption, made
with `scripts/play/graphics.mjs` in kuscher/googlebook-tech.

## Steps

1. **App signing:** *Use existing app signing key*, uploaded with Google's PEPK tool from `~/.config/hearonlink/keystore.jks`
   (alias `hearonlink`, SHA-256 `A1:12:57:4D:…:26:33:47:B6`), so the Play build and the APKs on GitHub have the same
   signature and people can move between them without uninstalling. The same key is the upload key.
2. **Build the bundle:** `tools/release.sh` → `executables/release-<version>/HearOnLink-<version>.aab` (and the APKs),
   signed with that key. Each upload needs a higher `versionCode` than the last (1 for 0.1.0, 2 for 0.2.0).
3. **Closed test first** (personal developer account: 12 testers opted in for 14 days before production), with the
   Google Group googlebook-studio-testers@googlegroups.com.
4. **Release:** add the bundle to the closed testing track, paste `release-notes.txt`, file the foreground-service
   declaration with its video, send for review.

## 0.2.0 (2): no accessibility service

0.1.0 (1), the first closed-testing draft, had an optional accessibility service, which needs its own declaration,
video and review. 0.2.0 removes it (the user: "It makes play releasing hard"). For Play that means:

- Replace the draft: upload `HearOnLink-0.2.0.aab` (version code 2) to the closed-testing track in place of 0.1.0 (1).
- Paste the updated `listing/en-US/full-description.txt` (the accessibility paragraph is gone; "While your AirPods are
  connected" and "Not a medical device" are new) and `release-notes.txt`.
- Replace screenshots 01, 02 and 04 in `graphics/large-screen/` (they showed Overview and the old accessibility card).
- Only one declaration is left: the connected-device foreground service, with one video.
- The privacy page https://googlebook.studio/privacy/hearonlink has to lose its accessibility bullet when 0.2.0 goes out
  (kuscher/googlebook-tech, `lib/privacy/policies.ts`); the app links to it from Settings › About HearOn Link.
