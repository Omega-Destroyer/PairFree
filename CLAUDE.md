# PairFree: notes for coding agents

## What this is

The app was called SoundPeter until 0.1.3 (renamed 2026-10-14). Some internal names keep the old
one on purpose: the signing properties `soundpeter.*` in the owner's `~/.gradle/gradle.properties`,
the key file, and the raw recordings in `data/<brand>/reports/`. The app ID, the Kotlin package and
the settings file are `pairfree` (the settings file was renamed after 0.1.4, which reset the
settings of the few copies installed then; owner's choice). EQ profile files from SoundPeter still
import.

## Branches

`main` is what gets released: F-Droid builds the `vX.Y.Z` tags on it. New work goes on a short
branch from `main`, one per brand (`brand/<name>`, for example `brand/jlab`) or per larger feature,
and is merged back with a merge commit as soon as it works for one model, then deleted. A feature
that is not ready for every model can stay hidden on the others through the brand's data (as
`eq.checked` does for the Soundcore EQ), so it does not have to wait on a branch. Do not keep a
long-lived branch per brand, and do not mix brands on one branch. Push only when the owner asks.

An open replacement for the official apps of earbud brands, starting with PeatsAudio for
SoundPEATS earbuds. Android first. iOS and Windows later. The long-term aim is one app for
every brand whose protocol can be worked out (`docs/DESIGN.md`, Requirements); Soundcore
(Anker) and Samsung Galaxy Buds are being added. The owner wants agents to do as much of the
work as possible and to ask before guessing.

## Where the truth lives

Each brand has its own folders, named after the brand as people know it rather than the
company: `docs/<brand>/`, `data/<brand>/` (with `reports/`), `assets/devices/<brand>/` and
`tools/device_art/<brand>/`. App-wide documents stay at the top level. Each brand also has a page
for users, `docs/devices/<brand>.md`, listing its models; its table and the README's one-row-per-brand
summary are generated (`tools/device_pages.py`), so keep model lists out of the README. Never copy numbers
from one brand to another. So far: `soundpeats`, `soundcore` and `samsung`.

- `docs/soundpeats/PROTOCOL.md` is the SoundPEATS protocol. Read it before writing any
  Bluetooth code.
- `docs/soundcore/PROTOCOL.md` is what is known of the Soundcore protocol so far. All of it
  comes from OpenSCQ30's source. What the owner's Space A40 confirmed is marked there, with its
  recordings in `data/soundcore/reports/`; 21 more models' layouts are checked against the
  replies in OpenSCQ30's tests. Treat the rest as a lead, and check it against a user's device
  report before building more on it.
- `data/soundcore/commands.json` is the Soundcore command table and `data/soundcore/devices.json`
  its model table; `generateSoundcoreTables` turns them into `SoundcoreCommand`, `SoundcoreWire`,
  `SoundcoreModels` and each model's `SoundcoreLayout` in the `protocol.soundcore` package. The
  app lists every model there among the paired earbuds (`SoundcoreModels.identify`: vendor service,
  else Bluetooth name) and shows it on the same screens through `SoundcoreSession`, which reads the
  state with the model's layout and maps it to `EarbudState`. It sends a setting only when the
  last state showed it. The equalizer (`SoundcoreEqualizer`, `eq` in `devices.json`, presets in
  `eq_presets.json`) goes through the same Sound tab via `SoundcoreEqTarget`, and only for models
  whose `eq.checked` is set: a recording of the Soundcore app or an OpenSCQ30 test pins the write.
  Touch controls (`SoundcoreButtons`, `SoundcoreTouch`, `buttons` in `devices.json`, action sets in
  `button_actions.json`) show on the Controls tab for every model that has them, tested or not
  (owner, 2026-10-14); `buttons.checked` only says how far the writes are confirmed. Where the A40's
  recordings and OpenSCQ30 differ, the recordings win (`docs/soundcore/PROTOCOL.md` sections 8 and 9).
- Every Soundcore model comes from OpenSCQ30 (github.com/Oppzippy/OpenSCQ30, Kyle Scheuing,
  GPL-3.0-or-later). `python tools/import_openscq30.py PATH` refreshes the model list and features
  in `data/soundcore/devices.json` from a local checkout and records the commit; everything else
  in an entry (state, battery, checksum, service_uuid, commands, min_firmware, kind, eq, buttons,
  reports, ldac_excludes, extra_features, notes) is kept. The state layouts, EQs and touch controls
  were read from OpenSCQ30's code; after a refresh to a newer commit, compare its parsers, EQ and
  button settings with them. Then run
  `python tools/gen_soundcore_table.py` for the Soundcore table in `docs/devices/soundcore.md`; a test
  fails while it is out of date. Credit OpenSCQ30 wherever its code or facts are used, as the code,
  the README and that page do.
  `data/soundcore/test_vectors.json` holds the A40's recorded frames, its state, its EQ and touch
  writes, and the state replies, EQ and touch tests from OpenSCQ30's tests (serials zeroed); the
  Kotlin tests rebuild or read every one. Never send `SoundcoreCommand.NEVER_SEND`.
- `docs/samsung/PROTOCOL.md` is the Galaxy Buds protocol, all of it from GalaxyBudsClient's source
  (github.com/timschneeb/GalaxyBudsClient, Tim Schneeberger, GPLv3 without "or later"). No Galaxy
  Buds have been tried; the owner has none, so every model relies on users' device reports. 7
  models' state layouts are checked against the replies in GalaxyBudsClient's tests
  (`data/samsung/test_vectors.json`); the Buds Core, Buds3 and later are its code alone.
- `data/samsung/messages.json` is the message table and `data/samsung/devices.json` the model table;
  `generateSamsungTables` turns them into `SamsungMessage` and the model list in the
  `protocol.samsung` package. The app finds Galaxy Buds by Bluetooth name
  (`SamsungModels.fromName`) and shows them through `SamsungSession`: the battery, noise mode and
  fit test go to `EarbudState`, and every other setting to brand-neutral rows (`SettingGroup` in
  `protocol/Settings.kt`, drawn by `ui/SettingRows.kt` on the tab each group names). A new brand's
  settings that fit no shared screen go the same way. The EQ goes through the Sound tab via
  `SamsungEqTarget`: presets the earbuds apply themselves (their curves are unknown, so none is
  drawn) and, on the Buds3 and later, a nine-band custom curve resent once per connection.
  `python tools/import_galaxybudsclient.py PATH` refreshes `devices.json` from a local checkout,
  keeping `names`, `reports`, `notes` and `kind`; then run `python tools/gen_samsung_table.py`.
  The state layout (`SamsungStatus.kt`) is a line-by-line port of GalaxyBudsClient's decoder:
  after a refresh to a newer commit, compare the decoder with it. Credit GalaxyBudsClient wherever
  its code or facts are used. Never send `SamsungMessage.NEVER_SEND`; there is no codec message,
  so no codec switch can be offered.
- `data/soundpeats/commands.json` is the command table. Do not retype command numbers from
  memory. Load the file or generate code from it.
- `data/soundpeats/test_vectors.json` holds frames that every implementation must reproduce
  byte for byte. Any new codec, in any language, gets a test that runs these.
- `data/soundpeats/devices.json` is the model table from the official app, with each model's
  chip family, EQ command and EQ merge rule. Newer models are missing from it and
  get added as people confirm them. Look a model up by the firmware code in the
  `03 09` reply. The earbuds do not report their chip family.
- `data/soundpeats/qcy_commands.json` is the separate protocol for the QCY-built Capsule3 Pro.
  It is BLE only. `docs/soundpeats/PROTOCOL.md` section 10 explains it.
- `docs/DESIGN.md` is the app design: requirements, the Studio look and its colour
  values, every screen, and what is still open. Build the UI from it. The owner's
  requirements at the top of that file are fixed.

The spec came from static analysis of PeatsAudio 1.9.45 for Android. The decompiled
source is not in this repo and will not be. SOUNDPEATS 2.5.3 (`com.thirtydays.headset`),
PeatsAudio's successor from the same company, was read later: 72 of its 87 commands match
`commands.json`, and what it added is marked "2.5.3" in the protocol doc and in the
data. It also downloads per-model features and EQ curves from a server. If the spec is
silent on something, say so and ask. Do not invent protocol details.

## What is confirmed and what is not

Confirmed by reading the app's code: transport, frame layout, every command number
in `commands.json`, EQ band encoding, the BLE service UUIDs, the update SDK each chip
family uses. Also confirmed there: every model except the Capsule3 Pro uses the same
frames and command numbers. Models differ in which features they have, in the EQ
command and merge rule, and in a few quirks (`docs/soundpeats/PROTOCOL.md` sections 5.1 and 9).
Which features a model has is not in the APK. It comes from SoundPEATS' server, so
it has to be probed.

Confirmed on the H3 (firmware code `S5`, chip QCC3091 by its own report): which GETs
it answers, that ANC mode, scene, transparency level, LDAC and multipoint SETs reach it,
how it answers each, that GETs work over GAIA BLE, and the EQ layout and base curve
PeatsAudio writes to it. Also that vendor `00 1D` answers Qualcomm GAIA v3 reads, and
that Qualcomm's own EQ reads there do not follow SoundPEATS' EQ writes, so the EQ cannot be
read back. Also the fit test, captured from PeatsAudio: GAIA feature `09`, `12 00 01` to
start, result `12 80` with one byte per earbud, left first (`01` good, `02` poor). See `docs/soundpeats/PROTOCOL.md` section 11. Feature `06` there is
firmware update: do not probe it.

Confirmed on the Air5 Pro+ (firmware code `S2`, version 1.2), from phone captures of
PeatsAudio: its firmware code, its EQ layout, base curve and presets, the replies to the
reads PeatsAudio sends on connecting (the same as the H3's), drainage (about 30 s, then it
reports itself off) and LE Audio (the earbuds restart and reconnect). Also every read
PairFree sends on connect (the probe's list; `data/soundpeats/reports/S2_1.2.txt`), and on
2026-10-12 the owner tried every feature PairFree offers on it, and all worked. The
Tested earbuds table in `docs/devices/soundpeats.md` is the summary. Specific open questions:

- Whether the Air5 Pro+ fit test gives a good result in a well-fitted ear: the only pair
  available has someone else's ear tips. Its LDAC does not work (`docs/soundpeats/PROTOCOL.md`
  section 12).
- Whether the Air5 Pro+ LDAC bug is Samsung-specific. On a Galaxy S24 FE (One UI 8) the
  phone disables the Air5's advertised LDAC and falls back to AAC, while the H3's LDAC
  works on the same phone. Needs reports from other phones. A bug report went to SoundPEATS.
- Whether `0x11` is the H3's Adaptive scene. The H3 lists levels `11` to `14`, not
  `15`, and does not answer the environment-adaptive GET `03 64`.
- The EQ layout of every model except the H3 and Air5 Pro+. The official app downloads a
  "classic" curve and presets per model and adds the user's gains to the curve; a phone
  capture of its EQ writes (Android HCI snoop log) shows them, as it did for those two.
  The two have different layouts and presets, so never copy one model's EQ to another.
- SETs and unasked state updates over the GAIA BLE service. iOS depends on it. GETs
  over it work on the H3, with the 4-byte header dropped as the spec says.
- How the remaining SETs answer on the H3. Known so far: ANC mode answers with an
  unasked GET reply about 2 s later; transparency level at once; LDAC and multipoint not
  at all, and the earbuds drop the channel about 3 to 4 s later.
- Why transparency level `02` does not sound different after `01` on the H3, in
  PeatsAudio too, and what the unasked `83 17` reply (about 6 s after connecting) means.

When a probe result settles one of these, update `docs/soundpeats/PROTOCOL.md` and save the raw
output under `data/soundpeats/reports/`.

## Test hardware

The owner has a SoundPEATS H3 and an Air5 Pro+. Both are sold as Qualcomm QCC3091
devices, so both should be in the `QUALCOMM` family. Everything else relies on
reports from other people.

## Hard rules

- Never commit decompiled code, vendor SDK code, firmware images or APKs, from any
  brand, or any key or credential an official app uses for its server API (such as
  PeatsAudio's AES key). `.gitignore` blocks the obvious ones. Decompile official apps
  outside the repo.
- Never send factory reset (`0305`), reboot (`0316`) or any firmware-update traffic
  without an explicit confirmation from the user in that moment. The probe is
  read-only by default and must stay that way.
- Do not call SoundPEATS' server from code in this repo unless the owner says so.
  The owner has allowed one exception: `tools/fetch_server_data.py`, a research
  tool he runs by hand. It only sends GET requests, at most one per 30 seconds,
  with no account or device identifiers. Keep it that way. The app itself never
  talks to the server and never requests the internet permission. Since 2026-10-10
  the server refuses every request without the official app's authentication, and
  the tool stops at the first refusal. Do not make it authenticate: no app keys,
  certificates or signing, in or outside the repo. No code in this repo calls any
  other brand's servers.
- Raw server replies go in `server_dump/`, which git ignores. They are vendor
  data. Do not commit them. Derived facts (feature codes, chip families, EQ
  numbers) go into `data/` only after the owner has agreed to what is included.
- Before any firmware-update work: Qualcomm only, behind an opt-in flag, with a
  user-supplied image file. A bad flash can brick earbuds.

## Android notes

- The Android project is `android/`; open that folder in Android Studio. `:protocol` is
  plain Kotlin with no Android dependencies, `:app` is the Compose app. Command numbers
  and wire constants are generated from `data/soundpeats/commands.json` at build time
  (`generateTables` in `android/protocol/build.gradle.kts`); never type them by hand.
  The model table (`Models`) is generated from `data/soundpeats/devices.json` the same way.
- Keep `android/protocol/src/main` free of `java.*` imports so it can become a Kotlin
  Multiplatform module for iOS.
- On connect the app sends `Requests.ON_CONNECT`, the probe's read-only GET list (a test
  keeps the two in step), then reads the battery every 30 s. `EarbudState` takes every
  reply, asked for or not; a feature counts as supported once its GET has answered.
  The app holds the channel only while it is on screen.
- A SET from the app shows at once (`EarbudState.expect`), then the app reads the value
  back 3 s later; the earbuds' reply replaces the guess. Factory reset goes only through
  `Requests.factoryResetConfirmedByUser`, called from the confirmation dialog.
- On the H3, PeatsAudio offers game mode and dual device only with LDAC off (`ldac_excludes`
  in `data/soundpeats/devices.json`); the app shows them disabled while LDAC is on. The Air5 Pro+ is
  the same, LDAC and multipoint included (owner, 2026-10-12). LDAC and
  multipoint are one either-or setting there (`ldac_or_multipoint`), and changing either
  drops the channel. Once a channel has been open, the app reopens it on its own after a
  drop, keeping the last state on screen.
- The `ldac` switch only changes which codecs the earbuds advertise; the phone picks the
  codec, and no app can (`docs/soundpeats/PROTOCOL.md` section 12). On the Air5 Pro+ turning LDAC on
  removes aptX and the phone then refuses LDAC, so it lands on AAC: a known firmware bug,
  not an app fault.
- The EQ is write-only (the H3 acknowledges each band with `0F 81 01`). The app sends the
  model's base curve plus the user's gains, or one of the model's presets as it is
  (`EqLayout` from the `eq` entry in `data/soundpeats/devices.json`), and remembers on the phone what
  it last sent. Since nothing can read the EQ back and PeatsAudio may have changed it, the
  app sends that again 2.5 s after every channel opens, as PeatsAudio does (owner's choice,
  2026-10-11). For earbuds this phone has never set, it sends nothing and shows the EQ as
  not known. Any profile whose bytes match what is on the earbuds shows as selected.
- A feature counts as supported once its GET answers, except commands marked `per_model`
  in `data/soundpeats/commands.json` (LE Audio): those also need the model's `confirmed` list in
  `data/soundpeats/devices.json` (`EarbudState.offers`). The H3 answers the LE Audio read, yet
  PeatsAudio does not offer LE Audio on it.
- On QUALCOMM models the app also reads the GAIA feature list (`Requests.gaiaFeatures`,
  vendor `00 1D`) after the connect reads, and offers the fit test when feature `09` is
  there. Never send GAIA frames to other families.
- New models come from users. The best report is the app's Record from PeatsAudio
  (`docs/DESIGN.md`): from a bug report shared to it, on the phone, it lists what
  PeatsAudio read from the earbuds, PairFree's own device report and the EQ curves
  PeatsAudio sent. Copy device report and `tools/probe.py` give the settings alone.
- For the controls: save the report as `data/soundpeats/reports/<code>_<version>.txt`, list it under
  the model's `reports` in `data/soundpeats/devices.json`, then run `python tools/gen_models_table.py`
  to update the All models table in `docs/devices/soundpeats.md`; a test fails while it is out of date. On
  Qualcomm models the app's reports include a `gaia_features` line, which fills the Fit
  test column.
- For the EQ: `python tools/import_eq_capture.py report.txt`,
  check the output (the firmware code, the names, that SOUNDPEATS Classic is there), then
  add its `eq` entry to the model in `data/soundpeats/devices.json` and its cases to
  `data/soundpeats/test_vectors.json`. Never copy one model's EQ to another.
- Debug builds log every frame to Logcat under the tag `PairFree`
  (`adb logcat -s PairFree`); release builds log nothing.
- Gradle 9 refuses to build from a folder Java reports as not writable, which happens
  on some network shares even when writing works. If the repo lives on such a share,
  build from a copy on a local disk.
- Releases are reproducible. F-Droid builds each `vX.Y.Z` tag itself and publishes the APK
  from the GitHub release only if the two are identical apart from the signature. Bump
  `versionCode` and `versionName`, tag, build `:app:assembleRelease` from a fresh clone of
  the tag (never from a copy with local edits), and upload it to the release as
  `PairFree-X.Y.Z.apk`. Files under `android/app/src/main/assets/` go into the APK byte
  for byte; `.gitattributes` keeps them as committed, so a Windows checkout matches F-Droid's.
- Control runs over classic Bluetooth serial (RFCOMM) on the standard SPP UUID
  `00001101-0000-1000-8000-00805F9B34FB`. The earbuds must already be paired and
  connected for audio. There is no handshake.
- Android 12 and later need the `BLUETOOTH_CONNECT` runtime permission. No scan
  permission is needed to talk to a bonded device.
- Only one app can hold the channel. Close PeatsAudio before connecting.
- Reassemble incoming bytes by the frame's length byte. Do not assume one read is
  one frame.
- The official app leaves 50 ms between writes. Match that until testing says otherwise.
- Battery is read with the three battery GETs. A value of `FF` means unknown.
- Treat every GET reply as a state update, whenever it arrives. The earbuds send them
  unasked after a SET and after a change made by touch. A GET the firmware does not
  support gets no reply at all.
- After a touch control the H3 goes silent for up to about 15 s, then answers
  everything at once. Do not treat that as a disconnect. Writes can block during the
  pause, so keep them off the main thread.

## Suggested order of work

1. Run `tools/probe.py` on the H3 and the Air5 Pro+. Save both outputs in
   `data/soundpeats/reports/` and add the two models to `data/soundpeats/devices.json`. Done for both; the
   Air5 Pro+'s reads came from PairFree's connect, which sends the probe's list.
2. Try one harmless SET on each (ANC mode is audible) and record whether a reply comes back.
   Done for both.
3. Scan for the GAIA BLE service on both. H3 done; Air5 Pro+ deferred.
4. Scaffold the Android app in Kotlin with Jetpack Compose (agreed with the owner).
   Put the protocol core in its own module with no Android dependencies and make it
   pass `data/soundpeats/test_vectors.json`. The app must not request the internet permission.
   Done: `android/`, with the protocol core in `android/protocol`.
5. Screens in this order: connection and battery, ANC, switches, gestures, EQ.
   Studio is the only look for now. Keep colours, fonts and drawings behind a theme
   so another look can be added later as a skin, not a second set of screens. All four
   tabs are built. Every feature PairFree offers has been tried on both the H3 and the
   Air5 Pro+ (owner, 2026-10-12). Released as v0.1.0 on 2026-10-12.
6. Work out the Qualcomm EQ band layout on hardware. H3 and Air5 Pro+ done from phone
   captures (`docs/soundpeats/PROTOCOL.md` section 11).
7. Firmware update last, and only on the rules above.

## Checks

```
python -m unittest discover -s tools -v
cd android && ./gradlew :protocol:test        (gradlew.bat on Windows cmd/PowerShell)
```

Keep both passing. The Gradle tests run every case in `data/soundpeats/test_vectors.json` and check
the generated command table against `data/soundpeats/commands.json`. `tools/probe.py` carries its
own copy of the GET table so it can be handed to testers as a single file; a test fails
if it drifts from `data/soundpeats/commands.json`.
