# PairFree for developers

This is the technical side of the project. The [README](../README.md) is for people who want the app; this page is for people who want to read
how it works, run the tests or add to it. The protocol of each brand is in `docs/<brand>/PROTOCOL.md`, the app's design in
[DESIGN.md](DESIGN.md), the desktop apps' plan in [DESKTOP.md](DESKTOP.md) and the agents' working notes in `CLAUDE.md`.

## Building

- **Android:** open `android/` in Android Studio (JDK 17 or newer and the Android SDK). `:protocol` is plain Kotlin with no Android
  dependencies; `:app` is the Compose app. Command numbers, wire constants and model tables are generated from each brand's `data/<brand>/*.json`
  at build time; never type them by hand.
- **Windows:** `desktop/` is its own Gradle build over the same sources (Kotlin/Native): `gradlew.bat -p ..\desktop :app:linkReleaseExecutableMingwX64`.
  See [DESKTOP.md](DESKTOP.md).

## What is in the repo

Each brand has its own folders, named after the brand: `docs/<brand>/`, `data/<brand>/`,
`assets/devices/<brand>/` and `tools/device_art/<brand>/`.

| Path                      | What it is                                                        |
|---------------------------|-------------------------------------------------------------------|
| `docs/DESIGN.md`          | The app design: requirements, the looks, screens                  |
| `docs/LOOKS.md`           | The looks, and how to add one as a file in `data/looks/`          |
| `docs/UPSTREAM.md`        | Where the brands' own apps or recordings differ from other open-source projects |
| `docs/devices/`           | One page per brand: every model PairFree knows and what works on each |
| `docs/<brand>/PROTOCOL.md` | The brand's protocol: transport, frames, commands, EQ and more  |
| `data/<brand>/commands.json` | The brand's command table in machine-readable form. Apps load or mirror this. |
| `data/<brand>/devices.json` | Known models and their per-model details                        |
| `data/<brand>/test_vectors.json` | Frames every implementation must reproduce byte for byte   |
| `data/<brand>/reports/`   | Device reports and recordings from real earbuds, one file per model and firmware |
| `data/soundpeats/qcy_commands.json` | The separate protocol for the SOUNDPEATS Capsule3 Pro   |
| `data/soundcore/eq_presets.json`, `button_actions.json` | Soundcore EQ presets and touch actions, from OpenSCQ30 |
| `data/samsung/messages.json` | The Galaxy Buds message table, from GalaxyBudsClient          |
| `data/sony/functions.json`, `eq_presets.json` | What Sony earbuds list they have, and Sony's EQ presets, from Sony's app |
| `data/bose/commands.json` | Bose's BMAP function table, from Bose's app                      |
| `data/jbl/commands.json`  | The Harman commands PairFree uses with JBL models, from JBL's app |
| `data/apple/commands.json` | Apple's accessory protocol as PairFree uses it with AirPods, from LibrePods |
| `data/nothing/commands.json` | Nothing's command table, from Nothing X                      |
| `data/pixel/commands.json` | The Pixel Buds' transports, settings and EQ presets, from Google's Pixel Buds app |
| `data/qcy/commands.json`  | QCY's Bluetooth LE service and packet items, from QCY's app       |
| `data/1more/commands.json` | 1MORE's command table, from 1MORE's app                          |
| `data/earfun/commands.json`, `eq_presets.json` | EarFun's two command sets and its EQ presets, from EarFun's app |
| `tools/probe.py`          | Read-only probe for SOUNDPEATS earbuds. Asks them what they support. |
| `tools/import_eq_capture.py` | Turns the equalizer part of a recording from PeatsAudio into a model's EQ data. |
| `tools/import_openscq30.py` | Refreshes the Soundcore model list from a local copy of OpenSCQ30. |
| `tools/import_galaxybudsclient.py` | Refreshes the Galaxy Buds model list from a local copy of GalaxyBudsClient. |
| `tools/import_1more_list.py` | Rewrites the 1MORE model list from a saved copy of 1MORE's product list. It fetches nothing. |
| `tools/device_pages.py`   | Writes the tables on the device pages and the summary above, with `gen_models_table.py` (SOUNDPEATS), `gen_soundcore_table.py` (Soundcore), `gen_samsung_table.py` (Samsung), `gen_sony_table.py` (Sony), `gen_bose_table.py` (Bose), `gen_jbl_table.py` (JBL), `gen_apple_table.py` (AirPods and Beats), `gen_nothing_table.py` (Nothing and CMF), `gen_pixel_table.py` (Pixel Buds), `gen_qcy_table.py` (QCY) and `gen_1more_table.py` (1MORE), `gen_earfun_table.py` (EarFun). |
| `tools/fetch_server_data.py` | Owner-only research tool for SoundPEATS' server. The server now refuses it (authentication), so it gets nothing. |
| `tools/test_*.py`         | Tests for the tools and the data files. No hardware needed.       |
| `assets/devices/<brand>/` | Vector drawings of each model's earbud and case, per look         |
| `tools/gen_device_art.py` | Generates those drawings. Edit the shapes there, not the SVG files. |
| `tools/device_art/<brand>/` | Traced outlines for each model, read by `gen_device_art.py`     |
| `android/`                | The Android app. `protocol/` is the protocol core, `app/` the app. |

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

## Probe from a PC

The same information as the device report, for SOUNDPEATS earbuds, from a PC with Bluetooth
and Python 3.9 or newer.

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

## What each brand's device report holds

Quicker, and no official app needed, but it holds only what the earbuds report: no
equalizer curves, and nothing of how the official app changes a setting. Connect the earbuds in PairFree,
tap **Copy device report** on the Device tab, and paste it into a new issue. It lists every
setting PairFree can read and what the earbuds answered. On Soundcore earbuds it holds the
earbuds' whole state reply, with the serial number masked, which checks the model's row in
the [Soundcore table](devices/soundcore.md#all-models). On Galaxy Buds it holds their
whole state, as bytes and as PairFree read it, for the
[Samsung table](devices/samsung.md#all-models). On Sony earbuds and headphones it lists the
functions they report and the last message of each kind they sent, for the
[Sony table](devices/sony.md#all-models). On Bose headphones and earbuds it lists the
product, the firmware and each read's last answer, for the [Bose table](devices/bose.md#all-models),
and on JBL's the same for the [JBL table](devices/jbl.md#all-models). On AirPods and Beats it lists
the model number, firmware, battery and the settings they reported, for the
[AirPods and Beats table](devices/apple.md#all-models). On Nothing and CMF earbuds and headphones it
lists the product, the firmware and each read's last answer, for the
[Nothing and CMF table](devices/nothing.md#all-models), and on Pixel Buds the model, the firmware and
each setting's last answer, for the [Pixel Buds table](devices/pixel.md#all-models). On QCY earbuds it lists the model the
earbuds advertise, the firmware and the last value of each setting PairFree read, for the
[QCY table](devices/qcy.md#all-models). On 1MORE earbuds and headphones it lists the model, the
firmware and each read's last answer, for the [1MORE table](devices/1more.md#all-models), and on
EarFun's the same for the [EarFun table](devices/earfun.md#all-models).

## Ground rules

- No decompiled code, vendor SDKs, firmware images or server keys in this repo.
  Everything here is written from the protocol description.
- Commands that reset, reboot or flash the earbuds are never sent without an
  explicit confirmation from the person holding them.
