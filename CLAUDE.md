# SoundPeter: notes for coding agents

## What this is

An open replacement for the PeatsAudio app for SoundPEATS earbuds. Android first.
iOS and Windows later. The owner wants agents to do as much of the work as possible
and to ask before guessing.

## Where the truth lives

- `docs/PROTOCOL.md` is the protocol. Read it before writing any Bluetooth code.
- `data/commands.json` is the command table. Do not retype command numbers from
  memory. Load the file or generate code from it.
- `data/test_vectors.json` holds frames that every implementation must reproduce
  byte for byte. Any new codec, in any language, gets a test that runs these.
- `data/devices.json` is the model table from the official app. Newer models are
  missing from it and get added as people confirm them.
- `docs/DESIGN.md` is the app design: requirements, the Studio look and its colour
  values, every screen, and what is still open. Build the UI from it. The owner's
  requirements at the top of that file are fixed.

The spec came from static analysis of PeatsAudio 1.9.45 for Android. The decompiled
source is not in this repo and will not be. If the spec is silent on something,
say so and ask. Do not invent protocol details.

## What is confirmed and what is not

Confirmed by reading the app's code: transport, frame layout, every command number
in `commands.json`, EQ band encoding, the BLE service UUIDs, the update SDK each chip
family uses.

Confirmed on the H3 (firmware code `S5`, chip QCC3091 by its own report): which GETs
it answers, that ANC mode, scene, transparency level, LDAC and multipoint SETs reach it,
how it answers each, that GETs work over GAIA BLE, and the EQ layout and base curve
PeatsAudio writes to it. See `docs/PROTOCOL.md` section 10. Everything else is
unconfirmed, including all of the Air5 Pro+. Specific open questions:

- Which commands the Air5 Pro+ answers, and its firmware code. Deferred by the owner:
  assume it behaves like the H3 (same chip) and troubleshoot if it turns out not to.
  This is an assumption, not a finding; do not write it into `docs/PROTOCOL.md`.
- Whether `0x11` is the H3's Adaptive scene. The H3 lists levels `11` to `14`, not
  `15`, and does not answer the environment-adaptive GET `03 64`.
- The EQ layout of every model except the H3. The official app downloads a "classic"
  curve per model and adds the user's gains to it; a phone capture of its EQ writes
  (Android HCI snoop log) shows the layout, as it did for the H3.
- SETs and unasked state updates over the GAIA BLE service. iOS depends on it. GETs
  over it work on the H3, with the 4-byte header dropped as the spec says.
- How the remaining SETs answer on the H3. Known so far: ANC mode answers with an
  unasked GET reply about 2 s later; transparency level at once; LDAC and multipoint not
  at all, and the earbuds drop the channel about 3 to 4 s later.
- Why transparency level `02` does not sound different after `01` on the H3, in
  PeatsAudio too, and what the unasked `83 17` reply (about 6 s after connecting) means.

When a probe result settles one of these, update `docs/PROTOCOL.md` and save the raw
output under `data/reports/`.

## Test hardware

The owner has a SoundPEATS H3 and an Air5 Pro+. Both are sold as Qualcomm QCC3091
devices, so both should be in the `QUALCOMM` family. Everything else relies on
reports from other people.

## Hard rules

- Never commit decompiled code, vendor SDK code, firmware images, APKs, or the AES
  key the official app uses for its server API. `.gitignore` blocks the obvious ones.
- Never send factory reset (`0305`), reboot (`0316`) or any firmware-update traffic
  without an explicit confirmation from the user in that moment. The probe is
  read-only by default and must stay that way.
- Do not call SoundPEATS' server from code in this repo unless the owner says so.
- Before any firmware-update work: Qualcomm only, behind an opt-in flag, with a
  user-supplied image file. A bad flash can brick earbuds.

## Android notes

- The Android project is `android/`; open that folder in Android Studio. `:protocol` is
  plain Kotlin with no Android dependencies, `:app` is the Compose app. Command numbers
  and wire constants are generated from `data/commands.json` at build time
  (`generateTables` in `android/protocol/build.gradle.kts`); never type them by hand.
  The model table (`Models`) is generated from `data/devices.json` the same way.
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
  in `data/devices.json`); the app shows them disabled while LDAC is on. LDAC and
  multipoint are one either-or setting there (`ldac_or_multipoint`), and changing either
  drops the channel. Once a channel has been open, the app reopens it on its own after a
  drop, keeping the last state on screen.
- The EQ is write-only (the H3 acknowledges each band with `0F 81 01`). The app sends the
  model's base curve plus the user's gains, or one of the model's presets as it is
  (`EqLayout` from the `eq` entry in `data/devices.json`), only when the user changes it,
  never on connect, and remembers on the phone what it last sent.
- Debug builds log every frame to Logcat under the tag `SoundPeter`
  (`adb logcat -s SoundPeter`); release builds log nothing.
- Gradle 9 refuses to build from a folder Java reports as not writable, which happens
  on some network shares even when writing works. If the repo lives on such a share,
  build from a copy on a local disk.
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
   `data/reports/` and add the two models to `data/devices.json`. H3 done; Air5 Pro+
   deferred.
2. Try one harmless SET on each (ANC mode is audible) and record whether a reply comes back.
   H3 done; Air5 Pro+ deferred.
3. Scan for the GAIA BLE service on both. H3 done; Air5 Pro+ deferred.
4. Scaffold the Android app in Kotlin with Jetpack Compose (agreed with the owner).
   Put the protocol core in its own module with no Android dependencies and make it
   pass `data/test_vectors.json`. The app must not request the internet permission.
   Done: `android/`, with the protocol core in `android/protocol`.
5. Screens in this order: connection and battery, ANC, switches, gestures, EQ.
   Studio is the only look; the owner dropped Sketchbook. Keep colours, fonts and
   drawings in one theme rather than in the screens. All four tabs are built. Tried on
   the H3: connection, battery, ANC, transparency, LDAC and two devices. Not yet tried:
   the EQ, gestures, the Device tab's prompt settings.
6. Work out the Qualcomm EQ band layout on hardware. H3 done from a phone capture
   (`docs/PROTOCOL.md` section 10); Air5 Pro+ deferred.
7. Firmware update last, and only on the rules above.

## Checks

```
python -m unittest discover -s tools -v
cd android && ./gradlew :protocol:test        (gradlew.bat on Windows cmd/PowerShell)
```

Keep both passing. The Gradle tests run every case in `data/test_vectors.json` and check
the generated command table against `data/commands.json`. `tools/probe.py` carries its
own copy of the GET table so it can be handed to testers as a single file; a test fails
if it drifts from `data/commands.json`.
