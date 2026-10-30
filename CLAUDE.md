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
that is not ready for every model can stay hidden on the others through the brand's data, so it
does not have to wait on a branch. Do not keep a
long-lived branch per brand, and do not mix brands on one branch. Push only when the owner asks.

An open replacement for the official apps of earbud brands, starting with PeatsAudio for
SoundPEATS earbuds. Android first. iOS and Windows later. The long-term aim is one app for
every brand whose protocol can be worked out (`docs/DESIGN.md`, Requirements); Soundcore
(Anker), Samsung Galaxy Buds, Sony, Bose, JBL, AirPods and Nothing (with CMF) are being added. The owner wants agents to do as much of the
work as possible and to ask before guessing.

## Versions

Releases are numbered x.y.z (owner, 2026-10-29). x is the major version; 1.0 has not been released.
y goes up by one for each new vendor a release adds (a release adding two goes up by two), and z resets
to 0 then. z goes up for everything else: more models of a vendor PairFree has, fixes, settings newly
offered, device reports added. The schema started at 0.3.0 (Nothing and CMF); earlier releases were
numbered by hand. `versionCode` is x * 1,000,000 + y * 1,000 + z, written out as a number so F-Droid's
update check can read it; `tools/test_version.py` fails when the two disagree.

## Adding a brand: make every model as complete as the sources allow

A brand is not done when it connects and shows the battery and noise control. Before it is merged, go
through everything the vendor's own app offers for each model (its feature tables, per-model cards or
panels, and every screen) and every open-source project for that brand, and build every setting their
combined information makes workable: equalizer, touch controls, timers, prompts, lights, codecs and
the rest. Where the vendor app and an open-source project differ, the vendor app wins unless a
recording says otherwise. Leave a setting out only when no source gives its layout or its values, or
when sending it could do harm (factory reset, firmware, powering off), and then say why in the brand's
protocol doc. The 2026-10-29 audit found the earlier brands missing settings that their sources
already described (Sony's touch keys and auto power-off, Bose's button and timer, Nothing's headphone
controls, the Soundcore models its older app reads); the owner does not want that again. The same
applies to adding models to a brand.

## Where the truth lives

Each brand has its own folders, named after the brand as people know it rather than the
company: `docs/<brand>/`, `data/<brand>/` (with `reports/`), `assets/devices/<brand>/` and
`tools/device_art/<brand>/`. App-wide documents stay at the top level. Each brand also has a page
for users, `docs/devices/<brand>.md`, listing its models; its table and the README's one-row-per-brand
summary are generated (`tools/device_pages.py`), so keep model lists out of the README. Never copy numbers
from one brand to another. So far: `soundpeats`, `soundcore`, `samsung`, `sony`, `bose`, `jbl`, `apple` (AirPods and the
Beats on Apple's chips) and `nothing` (Nothing and CMF).

- `docs/soundpeats/PROTOCOL.md` is the SoundPEATS protocol. Read it before writing any
  Bluetooth code.
- `docs/soundcore/PROTOCOL.md` is what is known of the Soundcore protocol so far. All of it
  comes from OpenSCQ30's source. What the owner's Space A40 confirmed is marked there, with its
  recordings in `data/soundcore/reports/`; 21 more models' layouts are checked against the
  replies in OpenSCQ30's tests, and 23 against the parsers of the Soundcore app 2.9.9, the last version
  that is not ijiami-packed (`soundcore_app` in `devices.json`, section 10 of the doc; decompiled
  outside the repo on 2026-10-29). Treat the rest as a lead, and check it against a user's device
  report before building more on it.
- `data/soundcore/commands.json` is the Soundcore command table and `data/soundcore/devices.json`
  its model table; `generateSoundcoreTables` turns them into `SoundcoreCommand`, `SoundcoreWire`,
  `SoundcoreModels` and each model's `SoundcoreLayout` in the `protocol.soundcore` package. The
  app lists every model there among the paired earbuds (`SoundcoreModels.identify`: vendor service,
  else Bluetooth name) and shows it on the same screens through `SoundcoreSession`, which reads the
  state with the model's layout and maps it to `EarbudState`. It sends a setting only when the
  last state showed it. The equalizer (`SoundcoreEqualizer`, `eq` in `devices.json`, presets in
  `eq_presets.json`) goes through the same Sound tab via `SoundcoreEqTarget`, for every model with an
  `eq` entry (owner, 2026-10-29); `eq.checked` says whether a recording of the Soundcore app or an
  OpenSCQ30 test pins the write, and the Sound tab says when neither does.
  Touch controls (`SoundcoreButtons`, `SoundcoreTouch`, `buttons` in `devices.json`, action sets in
  `button_actions.json`) show on the Controls tab for every model that has them, tested or not
  (owner, 2026-10-14); `buttons.checked` only says how far the writes are confirmed. Where the A40's
  recordings and OpenSCQ30 differ, the recordings win (`docs/soundcore/PROTOCOL.md` sections 8 and 9).
  Settings no shared screen holds are brand rows from `SoundcoreSession.groups` (so far the Sleep A20's
  sleep timer). A user's recording can add what OpenSCQ30 lacks (`checked: "recording"`, the Sleep A20,
  section 11); its writes go into `test_vectors.json` and the Kotlin tests rebuild them.
- Every Soundcore model comes from OpenSCQ30 (github.com/Oppzippy/OpenSCQ30, Kyle Scheuing,
  GPL-3.0-or-later). The Sleep A20 (A6611), which its library does not support, comes from its device
  faker's captured state (`tools/soundcore-device-faker/devices/a6611.toml`): `module` null, read-only
  (battery and firmware) until a report confirms more, and kept by the importer. Earbuds and headphones only the
  Soundcore app's strings name (6.4.0, which is ijiami-packed: only its resources are readable) are `report_only`:
  PairFree finds them, asks for their state and offers a device report (serial-like text masked), and shows no
  settings until their layout is known; a vendor service naming an unknown number is treated the same. Eight of
  them are in Soundcore 2.9.9, which reads them with another model's code, so they have that model's layout
  (`soundcore_app.same_as`; the A3910 has its own). Where 2.9.9 shows a model lacks something OpenSCQ30 lists,
  `soundcore_app.lacks` leaves it out. The
  app's other names for a model (its European names and the like) are among `names`. `python tools/import_openscq30.py PATH` refreshes the model list and features
  in `data/soundcore/devices.json` from a local checkout and records the commit; everything else
  in an entry (state, battery, checksum, service_uuid, commands, min_firmware, kind, eq, buttons,
  reports, ldac_excludes, extra_features, notes, report_only, soundcore_app, vendor_ids) is kept. The state layouts, EQs and touch controls
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
  (`data/samsung/test_vectors.json`); the Buds Core and Buds3 models are its code alone. For the
  Buds4 family and three models GalaxyBudsClient lacks (Buds On, H1, T1), `samsung_app` in
  `data/samsung/devices.json` holds what Samsung's own Galaxy Buds plugin says (budsunitemgr
  9.0.10, decompiled outside the repo on 2026-10-14): it wins over GalaxyBudsClient field by field,
  and `SamsungModel.buds4Layout` switches to its state layout and writes
  (`docs/samsung/PROTOCOL.md` section 12).
- `data/samsung/messages.json` is the message table and `data/samsung/devices.json` the model table;
  `generateSamsungTables` turns them into `SamsungMessage` and the model list in the
  `protocol.samsung` package. The app finds Galaxy Buds by the model id a Buds2 or later publishes in
  its service list, else by Bluetooth name where the device has the model's service
  (`SamsungModels.identify`, `docs/samsung/PROTOCOL.md` section 4), and shows them through
  `SamsungSession`: the battery, noise mode and
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
- `docs/sony/PROTOCOL.md` is the Sony protocol, read from Sony's own app (Sony | Sound Connect
  13.3.0, decompiled outside the repo on 2026-10-14) and checked against Gadgetbridge's Sony support
  (codeberg.org/Freeyourgadget/Gadgetbridge, AGPL-3.0: facts only, never its code), which has been
  used with real headphones. No Sony model has been tried; the owner has none. Sony's app has two
  table sets, chosen by the RFCOMM service (`SonyWire.SET1_SERVICE`, `SET2_SERVICE`) and confirmed
  by the length of the earbuds' first reply; each message waits for its acknowledgement
  (`SonyLink`), so Sony traffic goes through `EarbudsConnection.serveSony`'s queue, never straight
  to the socket. `data/sony/commands.json` (all four tables), `functions.json` (what the earbuds
  list they have), `devices.json` (names only: the earbuds report their own features) and
  `eq_presets.json` become `SonyCommand`, `SonyFunction`, `SonyWire` and the model and preset lists
  through `generateSonyTables`. `SonySession` reads only the functions the earbuds list; noise
  control (`SonyNoise`) writes back in the layout of the earbuds' own last reply; the EQ goes
  through the Sound tab via `SonyEqTarget`, whose "frames" are message payloads for that queue.
  Automatic power-off and the keys' functions (`SonyAutoOff`, `SonyKeys`) offer only what the earbuds'
  capability lists (the keys only presets that need no assistant or app set up); set 2's voice guidance
  comes from its table 2 function list, which set 2 sends on its own.
  Set 2 writes on as 0 and off as 1 for its switches. After a change to `devices.json` run
  `python tools/gen_sony_table.py`. Never send `SonyCommand.*.NEVER_SEND` (firmware update, test,
  logs).
- `docs/bose/PROTOCOL.md` is Bose's BMAP, read from Bose's own app (13.3.4, decompiled outside the
  repo on 2026-10-14) and from bosectl (github.com/aaronsb/bosectl, aaronsb, MIT), which was tried
  on seven Bose models; where they differ, Bose's app wins (multipoint's bits). No Bose model has
  been tried with PairFree. BMAP packets have no start byte or checksum, so the reader splits by
  the length byte, and `EarbudsConnection.serveBose` sends one request at a time, waiting for its
  answer. Bose's cloud sign-in guards plain SET; PairFree writes only with SETGET (as Bose's app
  does) and START, never signs in and never calls Bose's servers, and reads a setting again when
  the device refuses a write. `data/bose/commands.json` (functions, operators, errors) and
  `devices.json` (by product id, which the device reports) become `BoseFunction`, `BoseOperator`
  and `BoseModels` through `generateBoseTables`; `BoseSession` maps replies to the screens. The
  device report keeps only PairFree's own reads: unasked messages can hold other devices'
  addresses. After a change to `devices.json` run `python tools/gen_bose_table.py`. Never send
  `BoseFunction.NEVER_SEND` (firmware update, factory default, power, authentication). The button's masks
  are read as Bose's app reads them (bit 0 in the last byte), not as bosectl does; the standby timer's
  choices come from based-connect (GPL-3.0, facts only).
- `docs/jbl/PROTOCOL.md` is JBL's Harman command set, read from JBL's own app (JBL Headphones
  5.37.10, decompiled outside the repo on 2026-10-15) and checked against JblTray
  (github.com/emperorbug46/JblTray, MIT), which recorded a Tune Flex 2, and FajkPes's
  JBL-Headphones-App (no licence: facts only), which recorded a Tune 720BT. No JBL model has
  been tried with PairFree. Most models are controlled over Bluetooth LE GATT (`GattLink`), the
  Quantum 360 family over the serial channel. PairFree connects over LE to the classic address
  first and scans only when that fails (`JblFinder`); the scan needs `BLUETOOTH_SCAN`
  (`neverForLocation`), which the owner allowed for JBL alone on 2026-10-15, asked only when the
  user opens a JBL model (`docs/DESIGN.md`, Permissions). `data/jbl/commands.json` and
  `devices.json` (83 models from JBL's product list and dashboard configs, with each model's
  family, noise commands and auto power-off encoding, section 5) become `JblCommand`,
  `JblStatus`, `JblNoise`, `JblAmbient`, `JblFeature` and `JblModels` through
  `generateJblTables`; `JblSession` maps replies to the screens and reads each change back 3 s
  later. Each model's `settings` (the dashboard cards its control class sends: Smart Audio & Video,
  balance, VoiceAware, SafeSound, 3D spatial, Find My Buds and more, section 4.8) go through
  `JblExtras`; the list was read from JBL's app 5.37.10 outside the repo. `JblReader` splits both Harman's `AA` frames and protocol 4's `00 DD` frames (`JblP4Packet`). `EarbudsConnection.serveJbl` sends one command at a time, waiting up to 1 s, as JBL's
  app does. Protocol 4 (section 6) is covered, including the models with a BLE security mode 1 to 3,
  which add a transport step `GattLink` runs itself (the raw characteristic, the host-name checksum,
  section 7): no key is needed. JBL's genuine-product check needs a key from a native library, so it
  is left out (hard rules); Airoha and LE Audio models and the EQ are not covered. After a
  change to `devices.json` run `python tools/gen_jbl_table.py`. Never send
  `JblCommand.NEVER_SEND` (factory reset, power off, firmware update, analytics).
- `docs/apple/PROTOCOL.md` is Apple's accessory protocol for AirPods and Apple-chip Beats, ported from
  LibrePods (github.com/kavishdevar/librepods, GPL-3.0-or-later) and checked against MagicPodsCore
  (github.com/steam3d/MagicPodsCore, GPL-3.0: facts only, the Beats and AirPods Max models). No
  AirPods have been tried with PairFree. The channel is classic L2CAP PSM 0x1001, opened through
  `BluetoothSocket`'s hidden constructors (`AppleLink`, LibrePods' list); Android's stack refuses it
  before Android 17 except on LibrePods' list (`AppleSupport`: Pixel on 16 QPR3, OnePlus/OPPO/realme
  on 16). The owner chose (2026-10-15) to say Android 17 is needed on the other phones instead of
  trying: no root, no Xposed, no posing as an Apple device.
  `data/apple/commands.json` and `devices.json` become `AppleWire`, `AppleOpcode`, `AppleControl`
  and `AppleModels` through `generateAppleTables`; `AppleSession` shows each setting once the AirPods
  report it. The information packet holds serial numbers: keep it out of logs, reports and test
  vectors (zeroed there). After a change to `devices.json` run `python tools/gen_apple_table.py`.
  Never send `AppleOpcode.NEVER_SEND` or `AppleControl.NEVER_SEND` (smart routing, keys, renaming,
  connection ownership and pairing).
- `docs/nothing/PROTOCOL.md` is Nothing's protocol for Nothing and CMF earbuds and headphones, read from
  Nothing X 3.4.15 (decompiled outside the repo on 2026-10-18: the protocol is plain Kotlin behind a Flutter
  UI) and checked against nada (github.com/chukfinley/nada) and nadamas (github.com/ezvk/nadamas), both MIT,
  which were tried on a CMF Buds 2 Plus, an Ear (2), an Ear (3a) and a CMF Buds Pro 2; where they and
  Nothing X differ, Nothing X wins until a report settles it (the EQ preset numbers, section 5). No Nothing
  model has been tried with PairFree. Frames are `55`, control, command, length, sequence number, payload
  and a CRC-16/MODBUS (none on the Ear (1)) over the serial service `AEAC4A03-…`; a reply is the command
  with bit 15 cleared, and `EarbudsConnection.serveNothing` sends one command at a time, waiting up to
  1 s. `data/nothing/commands.json` (Nothing X's ProtocolConstant) and `devices.json` (19 models by
  product id, which the earbuds report on read `C01C`, with each firmware range's features from the table
  Nothing X bundles) become `NothingCommand`, `NothingWire` and the model list through
  `generateNothingTables`; `NothingSession` shows a setting only where the model has it and the earbuds
  answered its read. Two fields of that table are decoded by inference (the noise mode bits and the
  codecs, section 6): say so wherever they matter. After a change to `devices.json` run
  `python tools/gen_nothing_table.py`. Never send `NothingCommand.NEVER_SEND` (factory reset, firmware
  update, calibration, debug) or `NothingCommand.PRIVATE` (reads that return the serial number or other
  devices' addresses).
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
  available has someone else's ear tips.
- Whether every LDAC earbud needs the Samsung phone's own LDAC switch, or only some. It was on
  for the H3 and off for the Air5 Pro+ on the same phone. Needs reports from others.
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
  The channel lives in the app process (`Hub`): the screens hold it while they are on
  screen, and the status notification (`status/`) while it shows, for earbuds whose
  choice is PairFree's (docs/DESIGN.md, Notification). It closes when neither needs it.
  No app can hide Google's Fast Pair notification; PairFree only links to its settings.
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
  codec, and no app can, nor read which one it plays (`docs/soundpeats/PROTOCOL.md` section 12).
  Samsung phones can also need LDAC turned on for the earbuds in their own Bluetooth settings (the
  device's gear, Codecs); the owner found on 2026-10-19 that this, not the earbuds, kept the Air5
  Pro+ on AAC. The app reminds the user of it when LDAC is turned on (`LdacPhoneDialog`).
- The EQ is write-only (the H3 acknowledges each band with `0F 81 01`). The app sends the
  model's base curve plus the user's gains, or one of the model's presets as it is
  (`EqLayout` from the `eq` entry in `data/soundpeats/devices.json`), and remembers on the phone what
  it last sent. Since nothing can read the EQ back and PeatsAudio may have changed it, the
  app sends that again 2.5 s after every channel opens, as PeatsAudio does (owner's choice,
  2026-10-11). For earbuds this phone has never set, it sends nothing and shows the EQ as
  not known. Any profile whose bytes match what is on the earbuds shows as selected.
- The official app's other settings (dynamic EQ and bass, movie mode, spatial audio, Dolby, adaptive and
  personal volume, panoramic sound, volume balance and limit, adaptive noise cancelling, privacy mode, fall
  detection and the light) show as brand rows (`SoundpeatsSettings`, keys `sp_<id>`) once their GET answers;
  the H3 and Air5 Pro+ answer none of them.
- A feature counts as supported once its GET answers, except commands marked `per_model`
  in `data/soundpeats/commands.json` (LE Audio): those also need the model's `confirmed` list in
  `data/soundpeats/devices.json` (`EarbudState.offers`). The H3 answers the LE Audio read, yet
  PeatsAudio does not offer LE Audio on it.
- On QUALCOMM models the app also reads the GAIA feature list (`Requests.gaiaFeatures`,
  vendor `00 1D`) after the connect reads, and offers the fit test when feature `09` is
  there. Never send GAIA frames to other families.
- New models come from users. The best report is the app's Record from the official app
  (`docs/DESIGN.md`): from a bug report shared to it, on the phone, it lists what
  PeatsAudio read from the earbuds, PairFree's own device report and the EQ curves
  PeatsAudio sent. Copy device report and `tools/probe.py` give the settings alone. On
  Soundcore earbuds and Galaxy Buds the same screen gives a timeline of what the brand's app
  and the earbuds sent each other (`VendorRecording`), with the user's list of what they
  changed: the way to confirm a write before building on it. A report goes into a public
  issue, so it masks the earbuds' address and serial-like text, leaves out bodies marked
  `private` in `data/soundcore/commands.json` and firmware-update messages, and shows at most
  four bytes of a message PairFree does not know. Keep it that way.
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
  `versionCode` and `versionName` (Versions, above), tag, build `:app:assembleRelease` from a fresh clone of
  the tag (never from a copy with local edits), and upload it to the release as
  `PairFree-X.Y.Z.apk`. Files under `android/app/src/main/assets/` go into the APK byte
  for byte; `.gitattributes` keeps them as committed, so a Windows checkout matches F-Droid's.
- Control runs over classic Bluetooth serial (RFCOMM) on the standard SPP UUID
  `00001101-0000-1000-8000-00805F9B34FB`. The earbuds must already be paired and
  connected for audio. There is no handshake.
- Android 12 and later need the `BLUETOOTH_CONNECT` runtime permission. No scan
  permission is needed to talk to a bonded device, except JBL's LE models when their LE side
  does not answer on the classic address (`BLUETOOTH_SCAN`, JBL only, above).
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
