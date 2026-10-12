# SoundPeter

An alternative to the Android PeatsAudio app that ships with SoundPEATS earbuds.

No account. No cloud. Just the controls: battery, noise cancelling, EQ, gestures,
codec and multipoint switches.

SoundPeter is an independent project. It is not affiliated with or endorsed by SoundPEATS.

<p>
  <img src="docs/screenshots/home.png" width="250" alt="Home: battery for each earbud and the case, noise control and scenes, the sound profile, and quick switches">
  <img src="docs/screenshots/sound.png" width="250" alt="Sound: the 10-band equalizer with a saved profile">
  <img src="docs/screenshots/sound-preset.png" width="250" alt="Sound: one of the official presets">
  <img src="docs/screenshots/controls.png" width="250" alt="Controls: what each tap and hold does, per earbud">
  <img src="docs/screenshots/device.png" width="250" alt="Device: connection, the ear tip fit test, voice prompts">
  <img src="docs/screenshots/fit-test.png" width="250" alt="Fit test: a good fit on both earbuds">
</p>

Screenshots with a SOUNDPEATS H3, using replies recorded from real earbuds.

## Status

Early. The control protocol is documented from the official Android app and has been
partly confirmed on an H3. Nothing here is ready for daily use.

| Piece                                   | State                                   |
|-----------------------------------------|-----------------------------------------|
| Protocol spec                           | Written, from static analysis           |
| Command table and test vectors          | Written, checked against the app's code |
| Probe script (PC)                       | Written, run on an H3                   |
| Confirmed on hardware                   | H3 and Air5 Pro+: see [Tested earbuds](#tested-earbuds) |
| App design                              | Written. One look (Studio), five screens. |
| Android app                             | All four tabs (Home, Sound, Controls, Device), the fit test and the EQ recorder. Being tried on an H3 and an Air5 Pro+. |
| iOS and Windows apps                    | Planned, after Android                  |
| Firmware update                         | Planned for Qualcomm models, opt-in     |

## Tested earbuds

What SoundPeter can do depends on the model, because each one supports a different set
of features. These two have been tested on real earbuds. Their reports in
`data/reports/` hold the raw replies.

|                                   | SOUNDPEATS H3              | SOUNDPEATS Air5 Pro+       |
|-----------------------------------|----------------------------|----------------------------|
| Firmware tested                   | S5, version 0.4.9          | S2, version 1.2            |
| Chip                              | Qualcomm QCC3091           | Qualcomm QCC3091 (as sold) |
| Battery: left, right, case        | ✅                         | ✅                         |
| Noise control: off, ANC, transparency | ✅                     | ✅                         |
| ANC scenes (4)                    | ✅                         | ✅                         |
| Transparency: voice or standard   | ✅ ¹                       | ✅                         |
| Game mode                         | ✅ only with LDAC off      | ✅ only with LDAC off      |
| Two devices at once               | ✅ either this or LDAC ²   | ✅ either this or LDAC ²   |
| LDAC                              | ✅ 96 kHz / 32-bit ²       | ⚠️ the phone refuses it (below) |
| LE Audio                          | —                          | ✅ ³                       |
| Turn touch controls off           | ✅                         | ✅                         |
| Gestures (tap, double, triple, hold) | ✅                      | ✅                         |
| Voice prompts: volume, language   | ✅                         | ✅                         |
| Find my earbuds (beep)            | ✅                         | ✅                         |
| Equalizer, 10 bands               | ✅                         | ✅                         |
| EQ presets                        | ✅ all 12                  | ✅ all 12                  |
| Ear tip fit test                  | ✅                         | ✅                         |
| Water drain                       | —                          | ✅ ⁴                       |
| Reports                           | [S5_0.4.9](data/reports/S5_0.4.9.txt), [S5_gaia_v3](data/reports/S5_gaia_v3.txt), [fit test](data/reports/S5_S2_fit_drain_leaudio.txt) | [S2_1.2](data/reports/S2_1.2.txt), [LDAC](data/reports/air5-pro-plus_ldac.txt), [drain, LE Audio](data/reports/S5_S2_fit_drain_leaudio.txt) |

✅ tried with SoundPeter on these earbuds. — not on this model.

1. Switching from voice to standard does not change the sound on the H3, in PeatsAudio
   too.
2. LDAC and two devices are one setting on both: turning one off turns the other on.
   The earbuds drop the connection for a few seconds after a change, and SoundPeter
   reconnects by itself.
3. Switching LE Audio restarts the earbuds. SoundPeter reconnects by itself.
4. Plays a series of sounds for about 30 seconds, then stops by itself. Take the earbuds
   out of your ears first.

Neither model answers these, so SoundPeter does not show them: in-ear detection, auto
power-off, dynamic EQ, dynamic bass, movie mode, spatial audio, panoramic sound, Dolby,
adaptive volume, personalized volume, light effect, privacy mode and fall detection.

### Equalizer bands

The sliders go from −6 to +6 dB in 1 dB steps, on top of the model's own tuning. 0 dB on
every band is the earbuds' stock sound (SOUNDPEATS Classic in PeatsAudio).

|          | SOUNDPEATS H3                                     | SOUNDPEATS Air5 Pro+                              |
|----------|---------------------------------------------------|---------------------------------------------------|
| Bands, Hz | 50, 160, 350, 800, 1.6k, 2.8k, 4.6k, 8k, 12k, 17k | 32, 65, 180, 500, 800, 1.8k, 3.6k, 5.5k, 8k, 12k |
| Presets  | SOUNDPEATS Classic, Bass Boost, Bass Reduce, Treble Boost, Electronic, Pop, Classical, Rock, Folk, Book Whisper, HIFI, ACG | The same twelve names, with this model's own curves |

Every band's frequency, Q and gain, and every preset, are in `data/devices.json`. The two
models have different curves, so one model's EQ is never used for another.

### All models

<details open>
<summary>Every model SoundPeter knows, and what has been confirmed on each</summary>

<!-- models-table:start (generated by tools/gen_models_table.py; do not edit) -->
45 models in the official app's table, 2 with a device report and 2 with their equalizer recorded. Some models have more than one firmware code: SoundPeter tells them apart by the code the earbuds report.

✅ works with SoundPeter on this model. — the model does not have it, or SoundPeter does not offer it there. A model without a device report can still be used: SoundPeter shows whatever the earbuds answer, but nobody has reported what that is yet.

<table>
<tr><th>Model</th><th>Firmware code</th><th>Chip</th><th>Battery</th><th>Noise control</th><th>Transparency levels</th><th>Game mode</th><th>Two devices</th><th>LDAC</th><th>LE Audio</th><th>Gestures</th><th>Touch off</th><th>In-ear detection</th><th>Voice prompts</th><th>Auto power-off</th><th>Find earbuds</th><th>Water drain</th><th>Fit test</th><th>Equalizer</th></tr>
<tr><td>A8, ECHO</td><td>ECHO7018</td><td>Jieli</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Air3 Deluxe HS</td><td>BS519W2D</td><td>WUQI</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Air3 Pro, Air3Pro</td><td>Air3Pro</td><td>Qualcomm</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Air3, Air3-Deluxe</td><td>BS390RB</td><td>Qualcomm</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Air4</td><td>AIR4-BS590R2E</td><td>Qualcomm</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Air4 Lite</td><td>WUQIAIRLITE</td><td>WUQI</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Air4 Pro</td><td>AIR4PRO-BS588R2E</td><td>Qualcomm</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Air5</td><td>QCC3091AIR5</td><td>Qualcomm</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Air5 Lite</td><td>ZKLXAIR5LITE</td><td>Bluetrum</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Air5 Pro+</td><td><a href="data/reports/S2_1.2.txt">S2</a></td><td>Qualcomm</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td></tr>
<tr><td>Air6 Pro</td><td>S27</td><td>Qualcomm</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Air6 Pro</td><td>S32</td><td>Qualcomm</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Breezy</td><td>ZKLXBREEZY</td><td>Bluetrum</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Capsule3 Pro</td><td>QCYTS04</td><td>QCY</td><td colspan="16">Not supported: a different protocol (docs/PROTOCOL.md section 10)</td></tr>
<tr><td>Capsule3 Pro</td><td>SPTS04</td><td>QCY</td><td colspan="16">Not supported: a different protocol (docs/PROTOCOL.md section 10)</td></tr>
<tr><td>Chasers</td><td>ZKLXCHASERS</td><td>Bluetrum</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Clear</td><td>JL6973Clear</td><td>Jieli</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>CyberGear</td><td>ATS3019DK12</td><td>Actions</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Engine 4</td><td>WuQiEngine4</td><td>WUQI</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>GoFree</td><td>3007AGOFREE</td><td>WUQI</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>GoFree 2</td><td>NEW-WQ7034MXGoFree2</td><td>WUQI</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>GoFree 2</td><td>WQ7034MXGoFree2</td><td>WUQI</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>H1</td><td>BT5107</td><td>Qualcomm</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>H3</td><td><a href="data/reports/S5_0.4.9.txt">S5</a></td><td>Qualcomm</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>—</td><td>✅</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>Hi Listening</td><td>HILISTENING</td><td>Qualcomm</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Life</td><td>T57</td><td>Smartlink</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Life3</td><td>JIELILIFE3</td><td>Jieli</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Mini HS</td><td>WUQIMINIHS</td><td>WUQI</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Mini Pro</td><td>QCC3040</td><td>Qualcomm</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Mini Pro HS</td><td>WQT7033</td><td>WUQI</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Opera03</td><td>OPERA03</td><td>WUQI</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Opera05</td><td>OPERA05</td><td>WUQI</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>PearlClip Pro</td><td>BT893XPro</td><td>Bluetrum</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>PearlClip Pro</td><td>S13</td><td>Bluetrum</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>RunFree</td><td>JULIRUNFREE</td><td>Actions</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>RunFree HS</td><td>RUNFREEHS</td><td>WUQI</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>RunFree Lite</td><td>FREELITE</td><td>WUQI</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>RunFree Lite2</td><td>JULI3025Lite2</td><td>Actions</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Sonic</td><td>T28</td><td>Qualcomm</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Sonic Pro</td><td>T28Pro</td><td>Qualcomm</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Space</td><td>R1292A7018F</td><td>Jieli</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Space Pro</td><td>ZKLXSPACEPRO</td><td>Bluetrum</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>TrueAir2</td><td>BS343RB</td><td>Qualcomm</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>TRUEFREE C1, TRUEFREE soft</td><td>JL6973D8C1</td><td>Jieli</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>TRUEFREE F2</td><td>WuQi7034MXF2</td><td>WUQI</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Truengine 3 SE, Truengine 3SE</td><td>BT5106-3040</td><td>Qualcomm</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
<tr><td>Wings2</td><td>TS03WINGS2</td><td>Jieli</td><td colspan="15"><a href="#help-by-probing-your-earbuds">Needs a device report</a></td><td><a href="#help-add-your-models-equalizer">Needs a recording</a></td></tr>
</table>
<!-- models-table:end -->

</details>

### Other models

Almost every model the official app supports uses the same protocol, so SoundPeter
should connect to the other models in `data/devices.json` (all but the Capsule3 Pro)
and to newer ones, and show whatever they answer. None of them has been tested yet. The equalizer appears only once
someone has recorded the model's curves.

If you have one, two things from SoundPeter add it to the table:

- **Copy device report** on the Device tab: what the earbuds answer, with no computer
  needed. Paste it into a new issue.
- **Record the EQ from PeatsAudio**, for the equalizer (see
  [Help add your model's equalizer](#help-add-your-models-equalizer)).

The Capsule3 Pro is the exception. It uses a different protocol over Bluetooth LE,
documented in `docs/PROTOCOL.md` section 10, and SoundPeter does not support it yet.

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
| `tools/gen_models_table.py` | Writes the All models table in this README from `data/devices.json` and the reports. |
| `tools/test_probe.py`     | Tests for the probe and the data files. No hardware needed.       |
| `assets/devices/`         | Vector drawings of each model's earbud and case, per look         |
| `tools/gen_device_art.py` | Generates those drawings. Edit the shapes there, not the SVG files. |
| `tools/device_art/`       | Traced outlines for each model, read by `gen_device_art.py`       |
| `android/`                | The Android app. `protocol/` is the protocol core, `app/` the app. |

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
