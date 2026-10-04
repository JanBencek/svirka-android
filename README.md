# Svirka for Android

Native Android player for a [Navidrome](https://www.navidrome.org) library — the app
companion to the [Svirka](https://music.bencit.com) web player. Same look (neutral
dark "midnight" palette, sparkles mark), native where it counts:

- downloads & offline playback, background playback, Android Auto
- **Import from link**: Account → Import from link, or share a YouTube / Spotify /
  SoundCloud link to Svirka — it lands in the library (needs the MusicBox importer
  at `<server>/musicbox/`)

**Install:** https://github.com/JanBencek/svirka-android/releases/latest/download/Svirka.apk
(rolling debug build, rebuilt on every push to `master`).

App ID `com.bencit.svirka` — installs alongside Navic.

## Building

No local toolchain needed: GitHub Actions builds the APK on every push
(`.github/workflows/build-apk.yml`). Locally: JDK 21 + Android SDK, then
`./gradlew :androidApp:assembleDebug`.

## Credits & license

Svirka for Android is a fork of [Navic](https://github.com/ssalggnikool/Navic) by
paige and contributors (via [JanBencek/Navic](https://github.com/JanBencek/Navic)).
Kotlin packages are kept as `paige.navic.*` so upstream fixes merge cleanly:
`git fetch upstream && git merge upstream/master`.

Licensed under the GNU GPL v3.0, like Navic — see [LICENSE](LICENSE).
