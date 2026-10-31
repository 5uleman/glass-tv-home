# Glass TV — Clean English Edition

> **Fork note.** This is a modified fork of [Gathin's glass-tv-home](https://github.com/aqmangge/glass-tv-home),
> used under its MIT licence — see [LICENSE](LICENSE). It is a hobby fork: there is no support promise, and it is
> not endorsed by or affiliated with the original author.

This is a modified version of Gathin's glass-tv-home, made by Suleman for English use, with some features
removed for a cleaner experience.

A light home screen for Android TV and Google TV in frosted glass, with a new set of places behind your apps
every week. This edition is the English-only, prayer-free build I use on my own TV.

## Download

The current build is **3.3.0** (`versionCode 18`), 628 KB, package id **`com.suleman.glasstv`**.
Download it from the [Releases page](https://github.com/5uleman/glass-tv-home/releases/latest) —
release binaries are attached there, not committed to this repository.

SHA-256 `54d52dc00785940f1eaca91c4b7c39177f88061426368c2cccf9c4087d5884fc`, tagged
`v3.3.0-clean-english`.

> The APKs still under `release/` (`glass-tv-home.apk`, `glass-tv-installer.apk`) are upstream's original
> builds, kept for reference. Do not install them as this edition — use only the Releases page.

```bash
adb install -r glasstv-3.3.0-signed.apk
```

The APK is signed with this fork's own key, so it will not install over the original author's build (different
package id, different signature) and does not install over a debug build of this fork. Uninstall the old one
first if the package ids collide.

Everything is reversible: the launcher is a normal app, and it never roots the TV or changes system firmware.

## What is different in this edition

- **English only.** Whatever language the TV is set to, the interface stays English.
- **Prayer and azan features are off.** No prayer timetable, no azan popup, no Islamic shelf, no Hijri or
  next-prayer rows in the nightstand clock.
- **Lahore is the default location** (station `PK01`, Asia/Karachi) for the weather pill. A location you set on
  the TV still wins over the default.
- **Restart TV and Turn Off TV open the system power dialog** instead of a launcher-made popup, so both rows do
  the same, predictable thing.
- **The self-updater is off.** This build never reads a release feed or shows an update prompt; new versions are
  built and installed by hand.
- **Four-column grid** and a hero title that uses the full width, so a long place name is not cut with an
  ellipsis.
- **Media polling is off**, so nothing polls the current track while the launcher is idle.

Everything else is upstream's work: the glass dock and menus, weekly places, the StandBy clock, the recent-apps
deck and RAM gauge, cache cleaning every 15 days, wallpaper themes and the phone QR upload.

## Setup on the TV

Install the APK, then three things have to be done by hand once per TV:

1. **Turn on the accessibility service**: Settings → Accessibility → Glass TV → On. The recent-apps deck, the
   force-stop it does and the power dialog need it. Press Home afterwards and check it is bound:
   `adb shell dumpsys accessibility | grep -i bound`. If it is not bound, firmware that blocks service
   auto-start is the usual reason, and the power rows will only show a hint pointing at that switch.

   **This has to be checked after every reboot, not just once.** On the TCL set this build was tested on, the TV
   resets the accessibility master switch at boot: `adb shell settings get secure accessibility_enabled` reads `0`,
   the service stays listed but is not bound, and Restart TV / Turn Off TV only offer to open the Accessibility
   screen. Turn Glass TV back on in Settings → Accessibility (the power rows open that screen for you), then check
   again with `adb shell dumpsys accessibility | grep -i 'bound services'` — it must list `Service[label=Home button…]`.

   Turning it back on over adb takes three commands, because setting the master switch alone does not rebind:
   ```bash
   adb shell settings put secure accessibility_enabled 1
   adb shell settings put secure enabled_accessibility_services ""
   adb shell settings put secure enabled_accessibility_services com.suleman.glasstv/com.gathin.tvhome.HomeKeyService
   ```
2. **Grant one permission for the cache cleaning**:
   ```bash
   adb shell pm grant com.suleman.glasstv android.permission.WRITE_SECURE_SETTINGS
   ```
3. **Disable the stock launcher, never uninstall it**, so the TV can be put back exactly as it was:
   ```bash
   adb shell pm disable-user --user 0 <stock launcher package>
   ```
   Find that package with `adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c
   android.intent.category.HOME`, or let `launcher/build-and-install.sh` do install, Home selection and the
   launcher swap with its own rollback if anything fails.

Debloating is optional and separate from this launcher. The playbook, tested lists and scripts for it are in
[docs/CLEANYOURTV-README.md](docs/CLEANYOURTV-README.md), with per-model notes in
[docs/DEVICES.md](docs/DEVICES.md) and [examples/](examples/).

## Screenshots

Taken from this build on a TCL Android TV (Android 11, 1080p).

| | |
|---|---|
| ![Home screen with the week's place and the glass dock](docs/screenshots/fork-home.jpg) | ![The dock with the first rows of the app grid](docs/screenshots/fork-dock.jpg) |
| *Home, with this week's place* | *The dock and the app grid behind it* |
| ![The app grid, four columns](docs/screenshots/fork-grid.jpg) | ![The quick settings menu](docs/screenshots/fork-settings.jpg) |
| *Every app, four columns* | *The quick settings menu* |
| ![The system power dialog](docs/screenshots/fork-power.jpg) | |
| *Restart TV and Turn Off TV open the system power dialog* | |

## Which TVs work

Android TV or Google TV, Android 8 or newer (`minSdk 26`), on the TV or on a box plugged into any TV.
[docs/DEVICES.md](docs/DEVICES.md) lists every model upstream has tested and how far each was tested.

LG (webOS) and Samsung (Tizen) TVs cannot run it: neither lets an app replace the home screen or the Home
button. A Google TV or Android TV stick or box in an HDMI port gives you the full launcher on those screens.

## Building it yourself

Needs JDK 17 (or newer) and Android SDK platform 34 with build-tools 34.0.0:

```bash
cd launcher
./gradlew testReleaseUnitTest assembleRelease
```

The release APK lands in `launcher/app/build/outputs/apk/release/app-release.apk`. CI runs the same two tasks
and fails if the APK grows past 700 KB.

Release signing is optional. Copy [launcher/keystore.properties.example](launcher/keystore.properties.example)
to `launcher/keystore.properties` (gitignored), point `storeFile` at a keystore outside the repository and fill
in the passwords; without that file the build falls back to the debug key, which is what CI uses.

## Credits and licence

- Built on [CleanYourTV](https://github.com/Gathin23/cleanyourtv) © 2026 Gathin, MIT licence — see
  [LICENSE](LICENSE). Its debloat lists, tools and launcher are the foundation of this project, and this fork
  keeps the full [upstream history](https://github.com/aqmangge/glass-tv-home).
- The Outfit typeface is under the SIL Open Font License (`launcher/FONT-LICENSE-OFL.txt`).
- The Inputs icon is from Google's Material Icons (Apache 2.0).
- Pictures of places and their captions are Bing's daily images (Microsoft). The launcher fetches them on your
  own TV; any promo or screenshot pictures in this repository show three of them.
- Gotham, used in upstream's promo pictures only, is a Hoefler&Co. typeface and is not part of this repository.
