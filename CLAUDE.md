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

Not confirmed on hardware: all of it. As of the first commit nobody has run the
probe on real earbuds. Specific open questions:

- Which commands the H3 and Air5 Pro+ answer, and their firmware codes.
- How ANC level `0x11`, `0x15` and the environment-adaptive switch relate on the H3.
- The EQ band count and centre frequencies Qualcomm firmware expects. The official
  app downloads a "classic" curve per model and merges user points onto its bands.
- Whether these earbuds expose the GAIA BLE service. iOS depends on it.
- Whether SET commands get a reply frame. The official app does not look for one.

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

- Control runs over classic Bluetooth serial (RFCOMM) on the standard SPP UUID
  `00001101-0000-1000-8000-00805F9B34FB`. The earbuds must already be paired and
  connected for audio. There is no handshake.
- Android 12 and later need the `BLUETOOTH_CONNECT` runtime permission. No scan
  permission is needed to talk to a bonded device.
- Only one app can hold the channel. Close PeatsAudio before connecting.
- Reassemble incoming bytes by the frame's length byte. Do not assume one read is
  one frame.
- The official app leaves 50 ms between writes. Match that until testing says otherwise.
- Battery is read with the three battery GETs.

## Suggested order of work

1. Run `tools/probe.py` on the H3 and the Air5 Pro+. Save both outputs in
   `data/reports/` and add the two models to `data/devices.json`.
2. Try one harmless SET on each (ANC mode is audible) and record whether a reply comes back.
3. Scan for the GAIA BLE service on both.
4. Scaffold the Android app in Kotlin with Jetpack Compose (agreed with the owner).
   Put the protocol core in its own module with no Android dependencies and make it
   pass `data/test_vectors.json`. The app must not request the internet permission.
5. Screens in this order: connection and battery, ANC, switches, gestures, EQ.
   Studio is the only look for now. Keep colours, fonts and drawings behind a theme
   so another look can be added later as a skin, not a second set of screens.
6. Work out the Qualcomm EQ band layout on hardware.
7. Firmware update last, and only on the rules above.

## Checks

```
python -m unittest discover -s tools -v
```

Keep this passing. `tools/probe.py` carries its own copy of the GET table so it can
be handed to testers as a single file; a test fails if it drifts from
`data/commands.json`.
