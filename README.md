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
| Confirmed on hardware                   | H3: reads, ANC mode change, reads over BLE. Air5 Pro+ later. |
| App design                              | Written. Two looks, five screens.       |
| Android app                             | Scaffolded. Protocol module passes the test vectors. No screens yet. |
| iOS and Windows apps                    | Planned, after Android                  |
| Firmware update                         | Planned for Qualcomm models, opt-in     |

## What is in the repo

| Path                      | What it is                                                        |
|---------------------------|-------------------------------------------------------------------|
| `docs/PROTOCOL.md`        | The protocol: transport, frames, commands, EQ, chip families, firmware update |
| `docs/DESIGN.md`          | The app design: requirements, looks, screens                      |
| `data/commands.json`      | The command table in machine-readable form. Apps load or mirror this. |
| `data/test_vectors.json`  | Frames every implementation must reproduce byte for byte          |
| `data/devices.json`       | Known models, their firmware codes and chip families              |
| `data/reports/`           | Probe output from real earbuds, one file per model and firmware   |
| `tools/probe.py`          | Read-only probe. Asks the earbuds what they support.              |
| `tools/test_probe.py`     | Tests for the probe and the data files. No hardware needed.       |
| `assets/devices/`         | Vector drawings of each model's earbud and case, per look         |
| `tools/gen_device_art.py` | Generates those drawings. Edit the shapes there, not the SVG files. |
| `android/`                | The Android app. `protocol/` is the protocol core, `app/` the app. |

## Which earbuds

One protocol covers almost every model the official app supports, whatever chip is
inside. The Capsule3 Pro is the exception and is not covered yet. See
`docs/PROTOCOL.md` section 5 and `data/devices.json`.

A model counts as supported once someone has run the probe on it and filed the output.

## Help by probing your earbuds

You need a PC with Bluetooth and Python 3.9 or newer.

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
