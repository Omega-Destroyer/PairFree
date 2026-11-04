# PairFree: notes for coding agents

## What this is

An open replacement for the official apps of earbud brands. Android first; iOS and Windows later. It
started as a replacement for PeatsAudio (SoundPEATS) and now covers SoundPEATS, Soundcore (Anker), Samsung
Galaxy Buds, Sony, Bose, JBL, AirPods and Apple-chip Beats, Nothing (with CMF), Google's Pixel Buds, QCY,
1MORE and EarFun. The long-term aim is one app for every brand whose protocol can be worked out (`docs/DESIGN.md`,
Requirements). The owner wants agents to do as much of the work as possible and to ask before guessing.

The app was called SoundPeter until 0.1.3 (renamed 2026-10-14). Some internal names keep the old one on
purpose: the signing properties `soundpeter.*` in the owner's `~/.gradle/gradle.properties`, the key file,
and the raw recordings in `data/<brand>/reports/`. The app ID, the Kotlin package and the settings file are
`pairfree` (the settings file was renamed after 0.1.4, which reset the settings of the few copies installed
then; owner's choice). EQ profile files from SoundPeter still import.

## Sources: the vendor's app first

Owner, 2026-10-30 and 2026-11-03. For every brand:

1. **The vendor's own app is the source.** Decompile it (outside the repo) and build from what its code does:
   its command tables, its per-model feature lists, its parsers and writes. Relying on users' reports for
   each model is not efficient; read everything the app holds before asking users for anything.
2. **Recordings of the vendor's app with real earbuds confirm it.** A user's recording or device report, or
   the owner's own captures, settles a question the code leaves open, and wins where it disagrees with the
   code (a firmware the app's code does not describe, for example).
3. **Open-source projects are a check, and fill what the app cannot give** (a packed app, a server-side
   table, a model the app no longer lists). Their licence decides whether their code may be used or only
   their facts; credit them wherever either is used.
4. **Where the vendor's app or a recording differs from an open-source project, record it in
   `docs/UPSTREAM.md`** in the same change, with the evidence. The owner may report it to the project as an
   issue or a pull request; PairFree's findings can help those projects too.

If the sources are silent on something, say so and ask. Do not invent protocol details.

Tools for reading apps, all outside the repo (`E:\Builds\research`): jadx for Java/Kotlin; for React Native
apps compiled to Hermes bytecode, hermes-dec (owner, 2026-11-03). Data an app bundles encrypted with a key the
app holds may be decrypted there for its facts (owner, 2026-11-03, HeyMelody's model list); the key and the
decrypted file stay out of the repo, and no server is contacted. An app's server credentials are never used
(Hard rules).

## Branches

`main` is what gets released: F-Droid builds the `vX.Y.Z` tags on it. New work goes on a short branch from
`main`, one per brand (`brand/<name>`, for example `brand/jlab`) or per larger feature, and is merged back with a
merge commit as soon as it works for one model, then deleted. A feature that is not ready for every model can
stay hidden on the others through the brand's data, so it does not have to wait on a branch. Do not keep a
long-lived branch per brand, and do not mix brands on one branch: QCY and 1MORE sat on branches for two weeks
(2026-10-18 to 11-03) and needed a hand merge. Push only when the owner asks.

## Versions

Releases are numbered x.y.z (owner, 2026-10-29). x is the major version; 1.0 has not been released. y goes up
by one for each new vendor a release adds (a release adding two goes up by two), and z resets to 0 then. z goes
up for everything else: more models of a vendor PairFree has, fixes, settings newly offered, device reports
added. The schema started at 0.3.0 (Nothing and CMF); earlier releases were numbered by hand. `versionCode` is
x * 1,000,000 + y * 1,000 + z, written out as a number so F-Droid's update check can read it;
`tools/test_version.py` fails when the two disagree.

## Adding a brand: make every model as complete as the sources allow

A brand is not done when it connects and shows the battery and noise control. Before it is merged, go through
everything the vendor's own app offers for each model (its feature tables, per-model cards or panels, and every
screen) and every open-source project for that brand, and build every setting their combined information makes
workable: equalizer, touch controls, timers, prompts, lights, codecs and the rest. Leave a setting out only when
no source gives its layout or its values, or when sending it could do harm (factory reset, firmware, powering
off), and then say why in the brand's protocol doc. The 2026-10-29 audit found the earlier brands missing
settings that their sources already described (Sony's touch keys and auto power-off, Bose's button and timer,
Nothing's headphone controls, the Soundcore models its older app reads); the owner does not want that again. The
same applies to adding models to a brand.

A new brand also needs: its words and services in `Identify` and `data/identify.json`; a device report and the
connection report (below) that work for it; its page under `docs/devices/` with a generator in
`tools/device_pages.py`; its official app's name in `officialApp` (`ui/PairFreeApp.kt`).

## Where the truth lives

Each brand has its own folders, named after the brand as people know it rather than the company:
`docs/<brand>/`, `data/<brand>/` (with `reports/`), `assets/devices/<brand>/` and `tools/device_art/<brand>/`.
App-wide documents stay at the top level. Each brand also has a page for users, `docs/devices/<brand>.md`,
listing its models; its table and the README's one-row-per-brand summary are generated
(`tools/device_pages.py`), so keep model lists out of the README. Never copy numbers from one brand to another.
So far: `soundpeats`, `soundcore`, `samsung`, `sony`, `bose`, `jbl`, `apple` (AirPods and the Beats on Apple's
chips), `nothing` (Nothing and CMF), `pixel` (Google's Pixel Buds), `qcy`, `1more` (the Kotlin package is
`onemore`) and `earfun`.

Speakers are held as future work (owner, 2026-10-30): reports from Bose and Sony speakers are saved under
`data/<brand>/reports/`, and each brand's protocol doc says what adding them would take.

- `docs/soundpeats/PROTOCOL.md` is the SoundPEATS protocol, read from PeatsAudio 1.9.45 and SOUNDPEATS 2.5.3
  (`com.thirtydays.headset`, PeatsAudio's successor; what it added is marked "2.5.3"). Read it before writing
  any Bluetooth code. Both apps take each model's features and EQ curves from SoundPEATS' server, so those come
  from the owner's captures and users' recordings (the H3, Air5 Pro+, C30 and TRUEFREE Clip C10 so far).
  `data/soundpeats/commands.json` is the command table: do not retype command numbers from memory, load the
  file or generate code from it. `data/soundpeats/test_vectors.json` holds frames that every implementation
  must reproduce byte for byte; any new codec, in any language, gets a test that runs these.
  `data/soundpeats/devices.json` is the model table from the official app, with each model's chip family, EQ
  command and merge rule, and the EQ of the models recorded; look a model up by the firmware code in the
  `03 09` reply (the earbuds do not report their chip family). `data/soundpeats/qcy_commands.json` is the
  separate BLE protocol of the QCY-built Capsule3 Pro (section 10).
- `docs/soundcore/PROTOCOL.md` is the Soundcore protocol. The current Soundcore app (6.4.0) is ijiami-packed,
  so only its resources are readable; the code comes from the Soundcore app 2.9.9, the last version that is
  not packed (decompiled outside the repo on 2026-10-29, `soundcore_app` in `devices.json`, section 10), and
  from OpenSCQ30 (github.com/Oppzippy/OpenSCQ30, Kyle Scheuing, GPL-3.0-or-later), whose code PairFree's
  layouts were ported from and whose tests check 21 models' replies. Recordings win over both: the owner's
  Space A40 (sections 6, 8, 9) and users' recordings of the Sleep A20 (section 11) and Q20i (section 12); a
  user's AeroClip report confirms its state. Where they differ from OpenSCQ30, `docs/UPSTREAM.md` lists it.
  Models no source describes in full are leads: check them against a user's report before building more on
  them.
- `data/soundcore/commands.json` is the Soundcore command table and `data/soundcore/devices.json` its model
  table; `generateSoundcoreTables` turns them into `SoundcoreCommand`, `SoundcoreWire`, `SoundcoreModels` and
  each model's `SoundcoreLayout` in the `protocol.soundcore` package. The app lists every model there among the
  paired earbuds (`SoundcoreModels.identify`: vendor service, else Bluetooth name) and shows it through
  `SoundcoreSession`, which reads the state with the model's layout and maps it to `EarbudState`. It sends a
  setting only when the last state showed it. The equalizer (`SoundcoreEqualizer`, `eq` in `devices.json`,
  presets in `eq_presets.json`) goes through the Sound tab via `SoundcoreEqTarget`, for every model with an
  `eq` entry (owner, 2026-10-29); `eq.checked` says what pins the write (hardware, a recording, or an OpenSCQ30
  test), and the Sound tab says when nothing does. Touch controls (`SoundcoreButtons`, `SoundcoreTouch`,
  `buttons` in `devices.json`, action sets in `button_actions.json`) show on the Controls tab for every model
  that has them, tested or not (owner, 2026-10-14); `buttons.checked` only says how far the writes are
  confirmed. Settings no shared screen holds are brand rows from `SoundcoreSession.groups` (so far the Sleep
  A20's sleep timer). A user's recording's writes go into `test_vectors.json` and the Kotlin tests rebuild them.
- The Soundcore model list comes from OpenSCQ30, with the Sleep A20 (A6611), which its library does not
  support, from its device faker's captured state and a user's recording (kept by the importer). Earbuds and
  headphones only the Soundcore app's strings name (6.4.0) are `report_only`: PairFree finds them, asks for
  their state and offers a device report (serial-like text masked), and shows no settings until their layout is
  known; a vendor service naming an unknown number is treated the same. Eight of them are in Soundcore 2.9.9,
  which reads them with another model's code, so they have that model's layout (`soundcore_app.same_as`; the
  A3910 has its own). Where 2.9.9 shows a model lacks something OpenSCQ30 lists, `soundcore_app.lacks` leaves it
  out. The app's other names for a model (its European names and the like) are among `names`.
  `python tools/import_openscq30.py PATH` refreshes the model list and features in `data/soundcore/devices.json`
  from a local checkout and records the commit; everything else in an entry (state, battery, checksum,
  service_uuid, commands, min_firmware, kind, eq, buttons, reports, ldac_excludes, extra_features, notes,
  report_only, soundcore_app, vendor_ids) is kept. After a refresh to a newer commit, compare its parsers, EQ and
  button settings with the layouts. Then run `python tools/gen_soundcore_table.py`; a test fails while the page
  is out of date. Credit OpenSCQ30 wherever its code or facts are used, as the code, the README and that page
  do. `data/soundcore/test_vectors.json` holds the A40's recorded frames, its state, its EQ and touch writes, the
  users' recordings, and the state replies, EQ and touch tests from OpenSCQ30's tests (serials zeroed); the
  Kotlin tests rebuild or read every one. Never send `SoundcoreCommand.NEVER_SEND`.
- `docs/samsung/PROTOCOL.md` is the Galaxy Buds protocol. Samsung's own Galaxy Buds plugin (budsunitemgr
  9.0.10, decompiled outside the repo on 2026-10-14) is the source for the Buds4 family and three models
  GalaxyBudsClient lacks (Buds On, H1, T1): `samsung_app` in `data/samsung/devices.json`, which wins over
  GalaxyBudsClient field by field, and `SamsungModel.buds4Layout` switches to its state layout and writes
  (section 12). The older models come from GalaxyBudsClient (github.com/timschneeb/GalaxyBudsClient, Tim
  Schneeberger, GPLv3 without "or later"); the plugin only covers the Buds4 era, so reading Samsung's older
  plugin for them is still open. No Galaxy Buds have been tried; the owner has none. 7 models' state layouts
  are checked against the replies in GalaxyBudsClient's tests (`data/samsung/test_vectors.json`).
- `data/samsung/messages.json` is the message table and `data/samsung/devices.json` the model table;
  `generateSamsungTables` turns them into `SamsungMessage` and the model list in the `protocol.samsung`
  package. `SamsungModels.identify` picks the model by the model id a Buds2 or later publishes in its service
  list, else by Bluetooth name where the device has the model's service (section 4), and `SamsungSession`
  shows it: the battery, noise mode and fit test go to `EarbudState`, and every other setting to brand-neutral
  rows (`SettingGroup` in `protocol/Settings.kt`, drawn by `ui/SettingRows.kt` on the tab each group names). A
  new brand's settings that fit no shared screen go the same way. The EQ goes through the Sound tab via
  `SamsungEqTarget`: presets the earbuds apply themselves (their curves are unknown, so none is drawn) and, on
  the Buds3 and later, a nine-band custom curve resent once per connection. `python
  tools/import_galaxybudsclient.py PATH` refreshes `devices.json` from a local checkout, keeping `names`,
  `reports`, `notes` and `kind`; then run `python tools/gen_samsung_table.py`. The state layout
  (`SamsungStatus.kt`) is a line-by-line port of GalaxyBudsClient's decoder: after a refresh to a newer commit,
  compare the decoder with it. Credit GalaxyBudsClient wherever its code or facts are used. Never send
  `SamsungMessage.NEVER_SEND`; there is no codec message, so no codec switch can be offered.
- `docs/sony/PROTOCOL.md` is the Sony protocol, read from Sony's own app (Sony | Sound Connect 13.3.0,
  decompiled outside the repo on 2026-10-14) and checked against Gadgetbridge's Sony support
  (codeberg.org/Freeyourgadget/Gadgetbridge, AGPL-3.0: facts only, never its code), which has been used with
  real headphones. No Sony earbuds or headphones have been tried; users' reports from two ULT FIELD speakers
  are saved (section 9). Sony's app has two table sets, chosen by the RFCOMM service (`SonyWire.SET1_SERVICE`,
  `SET2_SERVICE`) and confirmed by the length of the earbuds' first reply; each message waits for its
  acknowledgement (`SonyLink`), so Sony traffic goes through `EarbudsConnection.serveSony`'s queue, never
  straight to the socket. `data/sony/commands.json` (all four tables), `functions.json` (what the earbuds list
  they have), `devices.json` (names only: the earbuds report their own features) and `eq_presets.json` become
  `SonyCommand`, `SonyFunction`, `SonyWire` and the model and preset lists through `generateSonyTables`.
  `SonySession` reads only the functions the earbuds list; noise control (`SonyNoise`) writes back in the layout
  of the earbuds' own last reply; the EQ goes through the Sound tab via `SonyEqTarget`, whose "frames" are
  message payloads for that queue. Automatic power-off and the keys' functions (`SonyAutoOff`, `SonyKeys`) offer
  only what the earbuds' capability lists (the keys only presets that need no assistant or app set up); set 2's
  voice guidance comes from its table 2 function list, which set 2 sends on its own. Set 2 writes on as 0 and off
  as 1 for its switches. After a change to `devices.json` run `python tools/gen_sony_table.py`. Never send
  `SonyCommand.*.NEVER_SEND` (firmware update, test, logs); power off only from the confirmed action.
- `docs/bose/PROTOCOL.md` is Bose's BMAP, read from Bose's own app (13.3.4, decompiled outside the repo on
  2026-10-14) and checked against bosectl (github.com/aaronsb/bosectl, aaronsb, MIT), which was tried on seven
  Bose models; where they differ, Bose's app wins (multipoint's bits, the button's masks: `docs/UPSTREAM.md`).
  No Bose headphones or earbuds have been tried with PairFree; a user's SoundLink Flex (a speaker) report is
  saved. BMAP packets have no start byte or checksum, so the reader splits by the length byte, and
  `EarbudsConnection.serveBose` sends one request at a time, waiting for its answer. Bose's cloud sign-in guards
  plain SET; PairFree writes only with SETGET (as Bose's app does) and START, never signs in and never calls
  Bose's servers, and reads a setting again when the device refuses a write. `data/bose/commands.json`
  (functions, operators, errors) and `devices.json` (by product id, which the device reports) become
  `BoseFunction`, `BoseOperator` and `BoseModels` through `generateBoseTables`; `BoseSession` maps replies to the
  screens. The device report keeps only PairFree's own reads: unasked messages can hold other devices'
  addresses. After a change to `devices.json` run `python tools/gen_bose_table.py`. Never send
  `BoseFunction.NEVER_SEND` (firmware update, factory default, authentication); power off only from the confirmed action. The standby timer's
  choices come from based-connect (GPL-3.0, facts only).
- `docs/jbl/PROTOCOL.md` is JBL's Harman command set, read from JBL's own app (JBL Headphones 5.37.10,
  decompiled outside the repo on 2026-10-15) and checked against JblTray (github.com/emperorbug46/JblTray, MIT),
  which recorded a Tune Flex 2, and FajkPes's JBL-Headphones-App (no licence: facts only), which recorded a Tune
  720BT. Of JBL's models a user's Live Buds 3 (Airoha, AB1577S) has been tried with PairFree, and worked (0.3.2,
  issue #4; its report in `data/jbl/reports/`); the same user saw noise control not change on 0.3.2, not yet
  explained. Most models are controlled over Bluetooth LE GATT (`GattLink`), the Quantum 360 family over the
  serial channel. PairFree connects over LE to the classic address first and scans only when that fails
  (`JblFinder`); the scan needs `BLUETOOTH_SCAN` (`neverForLocation`), which the owner allowed for JBL on
  2026-10-15, asked only when the user opens a JBL model (`docs/DESIGN.md`, Permissions). `data/jbl/commands.json`
  and `devices.json` (102 models from JBL's product list and dashboard configs, with each model's family, noise
  commands and auto power-off encoding, section 5) become `JblCommand`, `JblStatus`, `JblNoise`, `JblAmbient`,
  `JblFeature` and `JblModels` through `generateJblTables`; `JblSession` maps replies to the screens and reads
  each change back 3 s later. Each model's `settings` (the dashboard cards its control class sends, section 4.8)
  go through `JblExtras`. `JblReader` splits both Harman's `AA` frames and protocol 4's `00 DD` frames
  (`JblP4Packet`), or on the Airoha models RACE frames alone. `EarbudsConnection.serveJbl` sends one command at a
  time, waiting up to 1 s, as JBL's app does. Protocol 4 (section 6) is covered, including the models with a BLE
  security mode 1 to 3, which add a transport step `GattLink` runs itself (section 7): no key is needed. JBL's
  genuine-product check needs a key from a native library, so it is left out (hard rules); the BES LE Audio
  models and the EQ are not covered. The Airoha models (family `airoha`, section 10, including the LE Audio Tour
  Pro 3, Tune 670NC and 770NC, opened over Airoha's LE service when paired as an LE-only device) speak Airoha's
  RACE commands (`RacePacket`, `RaceParser`, `JblRace` from `commands.json`'s `airoha`) through the same
  `JblSession` and `JblExtras`; the session reads the chip name first, and noise control, auto power-off and
  pause when taken off are written the way that chip takes them (`RaceChip`). Their touch controls and noise
  level are not covered. After a change to `devices.json` run `python tools/gen_jbl_table.py`. Never send
  `JblCommand.NEVER_SEND` (factory reset, power off, firmware update, analytics) or `JblRace.NEVER_SEND`, and on
  RACE no key event but Smart Audio & Video's (`JblRace.KEY_EVENTS`) and no firmware update command
  (`JblSession.allowed` checks every frame).
- `docs/apple/PROTOCOL.md` is Apple's accessory protocol for AirPods and Apple-chip Beats. Apple's only Android
  app, Beats 2.12, is native code and has not been read, so this one is ported from LibrePods
  (github.com/kavishdevar/librepods, GPL-3.0-or-later) and checked against MagicPodsCore
  (github.com/steam3d/MagicPodsCore, GPL-3.0: facts only, the Beats and AirPods Max models). No AirPods have
  worked with PairFree yet: a user's AirPods Pro 3 on a Pixel 8 with Android 17 failed to open the channel (issue
  #3; the snoop log shows the stack refused it before any request went out), and the connection report should
  tell why. The channel is classic L2CAP PSM 0x1001, opened through `BluetoothSocket`'s hidden constructors
  (`AppleLink`, LibrePods' list); Android's stack refuses it before Android 17 except on LibrePods' list
  (`AppleSupport`: Pixel on 16 QPR3, OnePlus/OPPO/realme on 16). The owner chose (2026-10-15) to say Android 17
  is needed on the other phones instead of trying: no root, no Xposed, no posing as an Apple device.
  `data/apple/commands.json` and `devices.json` become `AppleWire`, `AppleOpcode`, `AppleControl` and
  `AppleModels` through `generateAppleTables`; `AppleSession` shows each setting once the AirPods report it. The
  information packet holds serial numbers: keep it out of logs, reports and test vectors (zeroed there). After a
  change to `devices.json` run `python tools/gen_apple_table.py`. Never send `AppleOpcode.NEVER_SEND` or
  `AppleControl.NEVER_SEND` (smart routing, keys, renaming, connection ownership and pairing).
- `docs/nothing/PROTOCOL.md` is Nothing's protocol for Nothing and CMF earbuds and headphones, read from Nothing
  X 3.4.15 (decompiled outside the repo on 2026-10-18: the protocol is plain Kotlin behind a Flutter UI) and
  checked against nada (github.com/chukfinley/nada) and nadamas (github.com/ezvk/nadamas), both MIT, which were
  tried on a CMF Buds 2 Plus, an Ear (2), an Ear (3a) and a CMF Buds Pro 2; where they and Nothing X differ,
  Nothing X wins until a report settles it (the EQ preset numbers, section 5, `docs/UPSTREAM.md`). No Nothing
  model has been tried with PairFree. Frames are `55`, control, command, length, sequence number, payload and a
  CRC-16/MODBUS (none on the Ear (1)) over the serial service `AEAC4A03-…`; a reply is the command with bit 15
  cleared, and `EarbudsConnection.serveNothing` sends one command at a time, waiting up to 1 s.
  `data/nothing/commands.json` (Nothing X's ProtocolConstant) and `devices.json` (19 models by product id, which
  the earbuds report on read `C01C`, with each firmware range's features from the table Nothing X bundles)
  become `NothingCommand`, `NothingWire` and the model list through `generateNothingTables`; `NothingSession`
  shows a setting only where the model has it and the earbuds answered its read. Two fields of that table are
  decoded by inference (the noise mode bits and the codecs, section 6): say so wherever they matter. After a
  change to `devices.json` run `python tools/gen_nothing_table.py`. Never send `NothingCommand.NEVER_SEND`
  (factory reset, firmware update, calibration, debug) or `NothingCommand.PRIVATE` (reads that return the serial
  number or other devices' addresses).
- `docs/pixel/PROTOCOL.md` is the Pixel Buds' protocol, read from Google's Pixel Buds app 1.0.990706425
  (decompiled outside the repo on 2026-10-30; obfuscated protobuf-lite, whose schemas were read from each
  class's message info with `E:\Builds\research\pixel-tools\rawinfo.py`) and checked against pbpctrl
  (github.com/qzed/pbpctrl, MIT or Apache-2.0, used with a Pixel Buds Pro; PairFree's HDLC framing follows its
  code) and OpenControl for Pixel Buds Pro 2 (AGPL-3.0: facts only, from its recordings). A user's Pixel Buds Pro
  answered every read in 0.4.0 (issue #4, `data/pixel/reports/`); the other models rely on device reports. Two
  transports carry the same protobuf messages: Maestro (Pigweed RPC in HDLC frames, serial service
  `25e97ff7-…`) on the Pro, Pro 2 and 2a, and plain group/code/length frames (`3a046f6d-…`) on the Pixel Buds
  (2020) and A-Series, which no other project describes and no recording shows. `data/pixel/commands.json`
  (transports, each family's settings fields, values, Google's EQ presets) and `devices.json` (6 device types, 5
  models, with the per-model feature list the app bundles) become `PixelWire`, `PixelValues`, the settings table
  and `PixelModels` through `generatePixelTables`; `PixelSession` reads each setting the family has and shows it
  once the earbuds answer, `EarbudsConnection.servePixel` sends one message at a time, and `PixelEqTarget` puts
  the five-band EQ on the Sound tab (whole-dB steps; Google's are half steps). Touch controls offer what Google's
  app offers for press and hold (noise control, digital assistant, Live Translate), Google apps or not; the 2020
  model's two touch slots, which the app no longer sets, are left alone. Spatial audio and head tracking are
  Android's settings, not the earbuds'. After a change to `devices.json` run `python tools/gen_pixel_table.py`.
  Never send the plain transport's groups 2, 5, 130 and 136 (firmware update, crash reports, update debug,
  statistics), firmware update on Maestro, or writes of the fields marked `never_send` (automatic updates, setup,
  diagnostics, the seal and automatic tests); `PixelSession.allowed` checks every message. The hardware info holds
  serial numbers: keep them out of logs, reports and test vectors.
- `docs/qcy/PROTOCOL.md` is QCY's own protocol, read from QCY's app (4.0.7, decompiled outside the repo on
  2026-10-18) and checked against OpenQCY-Desktop (github.com/GiorgioRafael/OpenQCY-Desktop, MIT), which recorded
  a MeloBuds N70. No QCY model has been tried with PairFree. QCY's app takes each model's control panel from
  QCY's server, so the model list is QCY's own product list as Quicky (github.com/hui1601/Quicky, no licence:
  facts only) dumped it, keeping the 171 earphones (owner, 2026-10-18); refresh `data/qcy/devices.json` from a
  newer Quicky only with the owner's OK, and never fetch QCY's server from this repo. QCY's earbuds are
  controlled over Bluetooth LE: PairFree scans for their advert (company 0x521C), which names the model (vendor
  id) and the LE side to connect to, with `BLUETOOTH_SCAN`, which the owner allowed for QCY on 2026-10-18
  (`docs/DESIGN.md`, Permissions). The service spreads over several characteristics, so `EnvelopeGattLink`
  carries `GattEnvelope`s (operation, short UUID, value). `data/qcy/commands.json` and `devices.json` become
  `QcyWire`, `QcyItem`, `QcyKeys` and the model list through `generateQcyTables`; `QcySession` asks for each item
  the model's control panel lists, the timed power off (`power_off_timer`) among them, and the EQ goes through the
  Sound tab via `QcyEqTarget` (section 7: a curve of the user's own; no presets). About half of QCY's models are
  on JieLi's chips and take the same items through JieLi's RCSP (service A002, section 6): `QcyJieliSession`
  wraps `QcySession` and turns its messages into RCSP frames (`RcspFrame`, the `jieli` part of `commands.json` as
  `JieliWire`), QCY's items in RCSP's custom command and a few settings as JieLi's own attributes, as QCY's app
  sends each; `QcyControl.of` picks the session by the model's `protocol`. Its settings need no authentication:
  JieLi's native auth is only in its OTA and watch SDKs. After a change to `devices.json` run `python
  tools/gen_qcy_table.py`. Never send `QcyItem.NEVER_SEND` (resets, pairing, test, rename, restart, clock and
  alarm) or `JieliWire.NEVER_SEND` (reboot, system operations and writes).
- `docs/1more/PROTOCOL.md` is 1MORE's protocol, read from 1MORE's app (2.0.0, decompiled outside the repo on
  2026-10-18). No open-source project for it was found, and no 1MORE model has been tried with PairFree. Frames
  are `11 01` (from the phone) or `01 00`, the command, the length and a sequence number (two bytes each, high
  first), the XOR of those eight bytes, and the payload, over the standard serial port; reads and writes are
  separate commands, and a write is answered with one result byte. 1MORE's app takes each model's features from
  1MORE's product list (`system/headphone/list`), which the owner let PairFree fetch once by hand on 2026-10-18
  (raw reply in `server_dump/1more/`, git-ignored); `data/1more/devices.json` keeps only what the owner approved:
  the Bluetooth name, the product model, the control flags, the EQ's presets and bands, and the pictures' paths
  (no burn-in code, Sound ID, OTA data or ids). Fetch the list again only with the owner's OK, by hand, never from
  code in this repo; `python tools/import_1more_list.py` then rewrites `devices.json` from the saved reply.
  `data/1more/commands.json` and `devices.json` become `OneMoreWire`, `OneMoreCommand` and the model list through
  `generateOneMoreTables`; `OneMoreSession` reads only what the model's entry lists and shows each setting once
  its read answered, the NANO's light among them, and the EQ goes through the Sound tab via `OneMoreEqTarget`
  (preset sounds and the custom curve). Models match by the whole Bluetooth name, as in 1MORE's app; the S52 and
  S22Ultra are Tuya devices (`tuya`) and are not listed. `data/1more/test_vectors.json` holds the frames 1MORE's
  app writes out in full. After a change to `devices.json` run `python tools/gen_1more_table.py`. Never send
  `OneMoreCommand.NEVER_SEND` (handshake, AI licence, music delete and upload).
- `docs/earfun/PROTOCOL.md` is EarFun's protocol, read from EarFun's app (EarFun Audio 20.1.61, decompiled outside
  the repo on 2026-11-03) and checked against Gadgetbridge's EarFun support (AGPL-3.0: facts only; Air S, Air Pro 4,
  Free Pro 3), whose EQ master gain and gain steps differ from EarFun's app (`docs/UPSTREAM.md`). No EarFun model has
  been tried with PairFree. Everything runs over the standard serial port, in two protocols by chip: the Qualcomm
  models speak GAIA with vendor 000A (`03` and the command, answered `83`), as SOUNDPEATS' do, with GAIA v3's opening
  on vendor 001D; the others EarFun's own `EF` frame. `data/earfun/commands.json` (both command sets and every
  setting's values), `devices.json` (26 entries by Bluetooth name for EarFun's 33 earbuds and headphones, with what
  EarFun's app offers each; 8 on other chips, `covered` false, are not listed among the paired earbuds) and
  `eq_presets.json` become `EarfunQcc`, `EarfunCommand`, `EarfunValues` and the model list through
  `generateEarfunTables`. `EarfunSession` reads only what the model has, shows a setting once its read answered, and
  speaks either protocol; `EarbudsConnection.serveEarfun` sends one command at a time, a read waiting up to 1 s. The
  EQ cannot be read: `EarfunEqTarget` writes EarFun's band frames (per-model master gain, 21 slider positions) and,
  as EarFun's app does, does not send it again on connecting (`EqTarget.restoresOnConnect`). Sound quality follows
  the rules of EarFun's quality screen (the earbuds restart after a change), the voice prompt language is offered
  where the earbuds have it built in, and the call gestures and headphone buttons are covered; restart and power off
  are actions the user confirms (owner, 2026-11-03). Not covered yet: the Qualcomm models' voice packs and the other
  chips' protocols (`docs/earfun/PROTOCOL.md` sections 8 and 9). After a change to `devices.json` run
  `python tools/gen_earfun_table.py`. Never send `EarfunQcc.NEVER_SEND` or `PRIVATE`, `EarfunCommand.NEVER_SEND` or
  `PRIVATE` (factory reset, renaming, device management, the paired phones' list, the serial number); `CONFIRM`
  (restart, power off) only from the confirmed action; `EarfunSession.allowed` checks every frame.
- `docs/UPSTREAM.md` lists where the vendor apps or recordings differ from the open-source projects (Sources).
- `docs/DESIGN.md` is the app design: requirements, the Studio look and its colour values, every screen, and
  what is still open. Build the UI from it. The owner's requirements at the top of that file are fixed.

## Test hardware

The owner has a SoundPEATS H3 and an Air5 Pro+, both Qualcomm QCC3091 (`QUALCOMM` family), and a Soundcore
Space A40. Everything else relies on reports and recordings from users (each brand's page, Tried on real earbuds).

## Hard rules

- Never commit decompiled code, vendor SDK code, firmware images or APKs, from any brand, or any key or
  credential an official app uses (for its server API, such as PeatsAudio's AES key, or for data it bundles).
  `.gitignore` blocks the obvious ones. Decompile official apps outside the repo. An app's `secrets` files are
  not read.
- Never send factory reset, reboot, power off or any firmware-update traffic, on any brand, without an explicit
  confirmation from the user in that moment; each brand's `NEVER_SEND` list holds them. Restart and power off are
  benign enough to offer as actions the user confirms on screen (owner, 2026-11-03; EarFun's `CONFIRM`); factory
  reset and firmware stay as they are. The probe is read-only
  by default and must stay that way.
- No code in this repo calls any brand's servers, and the app never requests the internet permission. The owner
  has allowed two research exceptions, both run by hand: `tools/fetch_server_data.py` (SoundPEATS: GET requests
  only, at most one per 30 seconds, no account or device identifiers; since 2026-10-10 the server refuses every
  request without the official app's authentication, and the tool stops at the first refusal) and the one-time
  1MORE product list (2026-10-18). Do not make either authenticate: no app keys, certificates or signing, in or
  outside the repo.
- Raw server replies go in `server_dump/`, which git ignores. They are vendor data. Do not commit them. Derived
  facts (feature codes, chip families, EQ numbers) go into `data/` only after the owner has agreed to what is
  included.
- Before any firmware-update work: Qualcomm only, behind an opt-in flag, with a user-supplied image file. A bad
  flash can brick earbuds.

## Android notes

- The Android project is `android/`; open that folder in Android Studio. `:protocol` is plain Kotlin with no
  Android dependencies, `:app` is the Compose app. Command numbers, wire constants and model tables are
  generated from each brand's `data/<brand>/*.json` at build time (the `generate*Tables` tasks in
  `android/protocol/build.gradle.kts`); never type them by hand.
- Keep `android/protocol/src/main` free of `java.*` imports so it can become a Kotlin Multiplatform module for
  iOS.
- Finding the earbuds: `Identify` picks the brand first (a brand's own service, else a brand word in the name,
  else an exact model name; `data/identify.json`), then that brand's code picks the model and service. A model
  PairFree mistook, missed or took for another brand gets an entry in `names` in `data/identify.json` before the
  fix; never a special case in one brand's matcher.
- The channel lives in the app process (`Hub`): the screens hold it while they are on screen, and the status
  notification (`status/`) while it shows, for earbuds whose choice is PairFree's (docs/DESIGN.md,
  Notification). It closes when neither needs it. Only one app can hold the channel, so while the notification
  holds it the brand's own app cannot connect; the user turns the notification off to use both (owner's answer
  in issue #13, 2026-11-02). No app can hide Google's Fast Pair notification; PairFree only links to its
  settings.
- A setting from the app shows at once, then the earbuds' answer or a read-back confirms it. Once a channel has
  been open, the app reopens it on its own after a drop, keeping the last state on screen.
- Reports for public issues, every brand: Copy device report (Device tab), Record from the official app
  (`docs/DESIGN.md`), and Copy connection report (the connect screen's failure message, issue #3), which says
  what PairFree tried and how each try ended, the only record release builds keep. They mask the earbuds'
  address (first three bytes kept) and serial-like text, the owner's part of the Bluetooth name
  (`ReportPrivacy`: "XXX's Pixel Buds Pro"), leave out messages marked private (Soundcore's and SOUNDPEATS'
  `commands.json`, Nothing's `PRIVATE`) and firmware-update messages, and show at most four bytes of a message
  PairFree does not know. Keep it that way.
- A report received goes under `data/<brand>/reports/`, listed in its model's `reports` in
  `data/<brand>/devices.json` (add the model first if the table lacks it), then the brand's `gen_*_table.py`.
- Debug builds log every frame to Logcat under the tag `PairFree` (`adb logcat -s PairFree`); release builds log
  nothing.
- The repo lives on the owner's NAS (`X:\Coding\PairFree\PairFree`), from which Gradle 9 refuses to build. Edit
  there; build in the mirror `E:\Builds\PairFree` after `powershell -NoProfile -ExecutionPolicy Bypass -File
  .vscode\mirror-build.ps1`, which stops if the mirror holds files newer than the NAS (keep build logs out of the
  mirror's root).
- Releases are reproducible. F-Droid builds each `vX.Y.Z` tag itself and publishes the APK from the GitHub release
  only if the two are identical apart from the signature. Bump `versionCode` and `versionName` (Versions, above),
  tag, build `:app:assembleRelease` from a fresh clone of the tag (never from a copy with local edits), and upload
  it to the release as `PairFree-X.Y.Z.apk`. Files under `android/app/src/main/assets/` go into the APK byte for
  byte; `.gitattributes` keeps them as committed, so a Windows checkout matches F-Droid's.
- Android 12 and later need the `BLUETOOTH_CONNECT` runtime permission. No scan permission is needed to talk to
  a bonded device, except JBL's LE models when their LE side does not answer on the classic address, and QCY's
  models (`BLUETOOTH_SCAN`, `neverForLocation`, asked only when the user opens one).

## SOUNDPEATS specifics

- Control runs over classic Bluetooth serial (RFCOMM) on the standard SPP UUID
  `00001101-0000-1000-8000-00805F9B34FB`. There is no handshake. Reassemble incoming bytes by the frame's length
  byte; do not assume one read is one frame. The official app leaves 50 ms between writes.
- On connect the app sends `Requests.ON_CONNECT`, the probe's read-only GET list (a test keeps the two in step),
  then reads the battery every 30 s. `EarbudState` takes every reply, asked for or not; a feature counts as
  supported once its GET has answered, except commands marked `per_model` in `data/soundpeats/commands.json` (LE
  Audio): those also need the model's `confirmed` list in `data/soundpeats/devices.json` (`EarbudState.offers`).
  The H3 answers the LE Audio read, yet PeatsAudio does not offer LE Audio on it. A GET the firmware does not
  support gets no reply at all. Battery is read with the three battery GETs; `FF` means unknown.
- Treat every GET reply as a state update, whenever it arrives: the earbuds send them unasked after a SET and
  after a change made by touch. After a touch control the H3 goes silent for up to about 15 s, then answers
  everything at once; do not treat that as a disconnect, and keep writes off the main thread.
- A SET shows at once (`EarbudState.expect`), then the app reads the value back 3 s later. Factory reset goes
  only through `Requests.factoryResetConfirmedByUser`, called from the confirmation dialog.
- On the H3, PeatsAudio offers game mode and dual device only with LDAC off (`ldac_excludes`); the Air5 Pro+ is
  the same, and LDAC and multipoint are one either-or setting there (`ldac_or_multipoint`); changing either drops
  the channel. The `ldac` switch only changes which codecs the earbuds advertise; the phone picks the codec, and
  no app can, nor read which one it plays (`docs/soundpeats/PROTOCOL.md` section 12). Samsung phones can also need
  LDAC turned on for the earbuds in their own Bluetooth settings; the app reminds the user (`LdacPhoneDialog`).
- The EQ is write-only (the H3 acknowledges each band with `0F 81 01`; Qualcomm's own EQ reads do not follow
  SoundPEATS' writes). The app sends the model's base curve plus the user's gains, or one of the model's presets
  as it is (`EqLayout` from the `eq` entry; on `append_after_classic` models the classic bands are `fixed_bands`,
  sent first), and remembers on the phone what it last sent; it sends that again 2.5 s after every channel opens,
  as PeatsAudio does (owner, 2026-10-11). For earbuds this phone has never set, it sends nothing and shows the EQ
  as not known. A new model's EQ: `python tools/import_eq_capture.py report.txt`, check the output, then add its
  `eq` entry and its test vectors. Never copy one model's EQ to another.
- The official app's other settings (dynamic EQ and bass, movie mode, spatial audio, Dolby, adaptive and personal
  volume, panoramic sound, volume balance and limit, adaptive noise cancelling, privacy mode, fall detection and
  the light) show as brand rows (`SoundpeatsSettings`, keys `sp_<id>`) once their GET answers.
- On QUALCOMM models the app also reads the GAIA feature list (`Requests.gaiaFeatures`, vendor `00 1D`) and offers
  the fit test when feature `09` is there. Never send GAIA frames to other families; feature `06` is firmware
  update, never probe it.
- A SOUNDPEATS report goes in as `data/soundpeats/reports/<code>_<version>.txt`; `tools/gen_models_table.py`
  updates the All models table.
- Open questions on the owner's earbuds (`docs/soundpeats/PROTOCOL.md` section 11 has the details): whether the
  Air5 Pro+ fit test reads good in a well-fitted ear; whether every LDAC earbud needs the Samsung phone's own LDAC
  switch; whether `0x11` is the H3's Adaptive scene; SETs over the GAIA BLE service (iOS depends on it); how the
  remaining SETs answer; why transparency `02` sounds like `01` on the H3, and what its unasked `83 17` means;
  whether an EQ read exists behind GAIA feature `07` (owner, 2026-11-03: parked). When a probe settles one,
  update the protocol doc and save the raw output under `data/soundpeats/reports/`.

## Where the work stands

PairFree started from the H3 and Air5 Pro+ (probe, SETs, GAIA, the Android app, the Qualcomm EQ; released as
0.1.0 on 2026-10-12, every feature tried on both) and has since added a brand at a time. Each brand's page under
`docs/devices/` says what it shows, what was tried and what is not covered yet; firmware update comes last, and
only on the rules above.

## Before every push to GitHub

Go through this list each time, not only for releases; the owner found several pushes that left the README or a
device page behind the code or the reports (2026-10-30).

1. **Checks** (below) pass: the Python tests, and in the E: mirror `:protocol:test :app:assembleDebug
   :app:lintDebug`.
2. **Detection:** a model PairFree mistook, missed or took for another brand gets an entry in `names` in
   `data/identify.json` (docs/DESIGN.md, Finding the earbuds) before the fix. A new brand gets its words in
   `brand_words` and its services in `Identify`.
3. **Device reports received** since the last push: each saved and listed (Android notes), so the brand's page
   and the README count it as tried.
4. **Generated pages:** run every `tools/gen_*_table.py` that touches a changed brand (each rewrites all device
   pages and the README summary), and `tools/gen_device_art.py` when models or their codes changed; commit
   `README.md`, `docs/devices/*.md` and `DeviceArtTable.kt` with the change.
5. **Hand-written text the generators do not touch**, checked against what changed:
   - `README.md`: the status table (the released version, "Tried on real earbuds"), the brand list.
   - `docs/devices/<brand>.md` above the generated table: what PairFree shows, which models were tried, the "Not
     yet" and not-covered lists, Help wanted.
   - `docs/<brand>/PROTOCOL.md`: the sections the change touches, its open questions and never-sent list.
   - `docs/UPSTREAM.md`: any difference from an open-source project the change found.
   - This file's paragraph for the brand: model counts, sources, what was tried, what is and is not covered, what
     is never sent.
   - The `_about` text in each changed `data/<brand>/*.json`.
   - The F-Droid description (`fastlane/metadata/android/en-US/full_description.txt`) and the GitHub repository's
     description, when brands, tried models or coverage change.
6. **Issues** the change settles: reply naming the exact version it ships in ("in PairFree 0.3.3"), never "the
   next version"; after a release, follow up on any reply that named a version still to come. The owner posts
   the replies from drafts.

For a release, also: bump `versionCode` and `versionName` (Versions, above) and the README's "Released
(vX.Y.Z)"; tag; build from a fresh clone of the tag; check the signature and an upgrade over the previous release;
write the notes ("Changes since", issue numbers, the tried models as the README lists them, the APK's SHA-256);
upload `PairFree-X.Y.Z.apk`.

## Checks

```
python -m unittest discover -s tools -v
cd android && ./gradlew :protocol:test        (gradlew.bat on Windows cmd/PowerShell)
```

Keep both passing. The Gradle tests run every brand's test vectors and check each generated table against its
JSON. `tools/probe.py` carries its own copy of the SOUNDPEATS GET table so it can be handed to testers as a single
file; a test fails if it drifts from `data/soundpeats/commands.json`.
