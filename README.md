# SoundPeter

An alternative to the Android PeatsAudio app that ships with SoundPEATS earbuds.

No account. No cloud. Just the controls: battery, noise cancelling, EQ, gestures,
codec and multipoint switches.

SoundPeter is an independent project. It is not affiliated with or endorsed by SoundPEATS.

## Status

Early. The control protocol is documented from the official Android app and has been
partly confirmed on an H3. Nothing here is ready for daily use.

| Piece                                   | State                                   |
|-----------------------------------------|-----------------------------------------|
| Protocol spec                           | Written, from static analysis           |
| Command table and test vectors          | Written, checked against the app's code |
| Probe script (PC)                       | Written, run on an H3                   |
| Confirmed on hardware                   | H3: reads, ANC and other SETs, EQ layout, LDAC, reads over BLE. Air5 Pro+: firmware, EQ layout and presets; LDAC bug found (below). |
| App design                              | Written. One look (Studio), five screens. |
| Android app                             | All four tabs: Home, Sound (EQ), Controls, Device. Being tried on an H3. |
| iOS and Windows apps                    | Planned, after Android                  |
| Firmware update                         | Planned for Qualcomm models, opt-in     |

## Known issue: Air5 Pro+ LDAC does not work

On a Samsung Galaxy S24 FE (Android 16, One UI 8), the **Air5 Pro+** advertises LDAC but
the phone refuses to use it: audio stays on AAC, and forcing LDAC in Developer options is
rejected. Turning LDAC on also removes aptX, so it *lowers* quality. The **H3**, with the
same Qualcomm QCC3091 chip, runs LDAC at 96 kHz / 32-bit on the same phone, so this looks
like an Air5 Pro+ firmware bug. It has been reported to SoundPEATS. Until it is fixed,
leave LDAC off on the Air5 Pro+ and you get aptX.

Details and the log evidence: `docs/PROTOCOL.md` section 12 and
`data/reports/air5-pro-plus_ldac.txt`.

**Have an Air5 Pro+ and a different phone?** Please help find out whether this is
Samsung-only:

1. Turn LDAC on (in PeatsAudio or SoundPeter), connect, and play music.
2. Enable Developer options on the phone, open **Bluetooth Audio Codec**, and note which
   codec is active and which are listed.
3. Select **LDAC**. Does the codec change (a brief audio pause) or stay on AAC?
4. Open an issue with your phone model, Android version, the Air5 Pro+ firmware version
   (from PeatsAudio; the one tested here is S2 1.2) and what you saw.

## What is in the repo

| Path                      | What it is                                                        |
|---------------------------|-------------------------------------------------------------------|
| `docs/PROTOCOL.md`        | The protocol: transport, frames, commands, EQ, chip families, firmware update |
| `docs/DESIGN.md`          | The app design: requirements, the Studio look, screens            |
| `data/commands.json`      | The command table in machine-readable form. Apps load or mirror this. |
| `data/test_vectors.json`  | Frames every implementation must reproduce byte for byte          |
| `data/devices.json`       | Known models, their firmware codes, chip families and per-model details |
| `data/qcy_commands.json`  | The separate protocol for the Capsule3 Pro                        |
| `data/reports/`           | Probe output from real earbuds, one file per model and firmware   |
| `tools/fetch_server_data.py` | Owner-only research tool for SoundPEATS' server. The server now refuses it (authentication), so it gets nothing. |
| `tools/probe.py`          | Read-only probe. Asks the earbuds what they support.              |
| `tools/import_eq_capture.py` | Turns an EQ capture report from the app into a model's EQ data. |
| `tools/test_probe.py`     | Tests for the probe and the data files. No hardware needed.       |
| `assets/devices/`         | Vector drawings of each model's earbud and case, per look         |
| `tools/gen_device_art.py` | Generates those drawings. Edit the shapes there, not the SVG files. |
| `tools/device_art/`       | Traced outlines for each model, read by `gen_device_art.py`       |
| `android/`                | The Android app. `protocol/` is the protocol core, `app/` the app. |

## Which earbuds

One protocol covers almost every model the official app supports, whatever chip is
inside. Models differ in which features they have and in a few details recorded per
model in `data/devices.json`. See `docs/PROTOCOL.md` section 5.

The Capsule3 Pro is the exception. It uses a different protocol over Bluetooth LE,
documented in `docs/PROTOCOL.md` section 10. The probe does not support it yet.

A model counts as supported once someone has run the probe on it and filed the output.

## Help add your model's equalizer

SoundPeter offers an equalizer only for models whose curves it knows: so far the H3 and
the Air5 Pro+. The earbuds cannot report their curves, but the official PeatsAudio app
sends them, and you can record that on your phone in about five minutes. One recording
per model is enough, and no computer is needed.

In SoundPeter, open the Device tab and tap **Record the EQ from PeatsAudio** (or the
button on the Sound tab). It walks you through it:

1. Turn on Developer options and set **Bluetooth HCI snoop log** to **Enabled**.
2. In PeatsAudio, tap SOUNDPEATS Classic and then every preset once.
3. Take a bug report from Developer options and share it to SoundPeter.
4. Name each curve SoundPeter found, and tap **Send on GitHub**.

SoundPeter reads the bug report only on your phone and keeps nothing but the EQ sent to
your earbuds. The report you send has no names or addresses. Afterwards, turn the snoop
log off and delete the bug report.

## Help by probing your earbuds

The app's Device tab has **Copy device report**, which gives the same information as the
probe without a computer. To use the probe instead, you need a PC with Bluetooth and
Python 3.9 or newer.

1. Pair the earbuds with the PC.
2. Close PeatsAudio on your phone. Only one client can hold the connection.
3. Run:

   ```
   python tools/probe.py --mac AA:BB:CC:DD:EE:FF
   ```

   On Windows, if the earbuds show up as a COM port:

   ```
   pip install pyserial
   python tools/probe.py --port COM5
   ```

4. Open a device report issue and paste the output.

The probe only sends read commands. It does not change any setting.

## Running the tests

```
python -m unittest discover -s tools -v
```

The Android protocol module runs the same test vectors. It needs JDK 17 or newer and
the Android SDK; Android Studio brings both.

```
cd android
./gradlew :protocol:test
```

## Ground rules

- No decompiled code, vendor SDKs, firmware images or server keys in this repo.
  Everything here is written from the protocol description.
- Commands that reset, reboot or flash the earbuds are never sent without an
  explicit confirmation from the person holding them.

## License

GNU General Public License v3.0. See `LICENSE`.
