# PairFree

An alternative to the Android PeatsAudio app that ships with SoundPEATS earbuds.

No account. No cloud. Just the controls: battery, noise cancelling, EQ, gestures,
codec and multipoint switches.

Soundcore (Anker) earbuds are being added on the same screens: see
[Soundcore models](#soundcore-anker-models).

PairFree is an independent project. It is not affiliated with or endorsed by SoundPEATS or
Anker. It was called SoundPeter until version 0.1.3; the new name fits an app meant for earbuds
of every brand, and keeps clear of any vendor's name.

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
| Android app                             | All four tabs (Home, Sound, Controls, Device), the fit test and Record from PeatsAudio. Being tried on an H3 and an Air5 Pro+. |
| iOS and Windows apps                    | Planned, after Android                  |
| Firmware update                         | Planned for Qualcomm models, opt-in     |

## Tested earbuds

What PairFree can do depends on the model, because each one supports a different set
of features. These two have been tested on real earbuds. Their reports in
`data/soundpeats/reports/` hold the raw replies.

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
| Reports                           | [S5_0.4.9](data/soundpeats/reports/S5_0.4.9.txt), [S5_gaia_v3](data/soundpeats/reports/S5_gaia_v3.txt), [fit test](data/soundpeats/reports/S5_S2_fit_drain_leaudio.txt) | [S2_1.2](data/soundpeats/reports/S2_1.2.txt), [LDAC](data/soundpeats/reports/air5-pro-plus_ldac.txt), [drain, LE Audio](data/soundpeats/reports/S5_S2_fit_drain_leaudio.txt) |

✅ tried with PairFree on these earbuds. — not on this model.

1. Switching from voice to standard does not change the sound on the H3, in PeatsAudio
   too.
2. LDAC and two devices are one setting on both: turning one off turns the other on.
   The earbuds drop the connection for a few seconds after a change, and PairFree
   reconnects by itself.
3. Switching LE Audio restarts the earbuds. PairFree reconnects by itself.
4. Plays a series of sounds for about 30 seconds, then stops by itself. Take the earbuds
   out of your ears first.

Neither model answers these, so PairFree does not show them: in-ear detection, auto
power-off, dynamic EQ, dynamic bass, movie mode, spatial audio, panoramic sound, Dolby,
adaptive volume, personalized volume, light effect, privacy mode and fall detection.

### Equalizer bands

The sliders go from −6 to +6 dB in 1 dB steps, on top of the model's own tuning. 0 dB on
every band is the earbuds' stock sound (SOUNDPEATS Classic in PeatsAudio).

|          | SOUNDPEATS H3                                     | SOUNDPEATS Air5 Pro+                              |
|----------|---------------------------------------------------|---------------------------------------------------|
| Bands, Hz | 50, 160, 350, 800, 1.6k, 2.8k, 4.6k, 8k, 12k, 17k | 32, 65, 180, 500, 800, 1.8k, 3.6k, 5.5k, 8k, 12k |
| Presets  | SOUNDPEATS Classic, Bass Boost, Bass Reduce, Treble Boost, Electronic, Pop, Classical, Rock, Folk, Book Whisper, HIFI, ACG | The same twelve names, with this model's own curves |

Every band's frequency, Q and gain, and every preset, are in `data/soundpeats/devices.json`. The two
models have different curves, so one model's EQ is never used for another.

### All models

<details open>
<summary>Every model PairFree knows, and what has been confirmed on each</summary>

<!-- models-table:start (generated by tools/gen_models_table.py; do not edit) -->
45 models in the official app's table, 2 with a device report and 2 with their equalizer recorded. Some models have more than one firmware code: PairFree tells them apart by the code the earbuds report.

✅ works with PairFree on this model. — the model does not have it, or PairFree does not offer it there. A model without a device report can still be used: PairFree shows whatever the earbuds answer, but nobody has reported what that is yet.

<table>
<tr><th>Model</th><th>Firmware code</th><th>Chip</th><th>Battery</th><th>Noise control</th><th>Transparency levels</th><th>Game mode</th><th>Two devices</th><th>LDAC</th><th>LE Audio</th><th>Gestures</th><th>Touch off</th><th>In-ear detection</th><th>Voice prompts</th><th>Auto power-off</th><th>Find earbuds</th><th>Water drain</th><th>Fit test</th><th>Equalizer</th></tr>
<tr><td>A8, ECHO</td><td>ECHO7018</td><td>Jieli</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Air3 Deluxe HS</td><td>BS519W2D</td><td>WUQI</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Air3 Pro, Air3Pro</td><td>Air3Pro</td><td>Qualcomm</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Air3, Air3-Deluxe</td><td>BS390RB</td><td>Qualcomm</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Air4</td><td>AIR4-BS590R2E</td><td>Qualcomm</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Air4 Lite</td><td>WUQIAIRLITE</td><td>WUQI</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Air4 Pro</td><td>AIR4PRO-BS588R2E</td><td>Qualcomm</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Air5</td><td>QCC3091AIR5</td><td>Qualcomm</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Air5 Lite</td><td>ZKLXAIR5LITE</td><td>Bluetrum</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Air5 Pro+</td><td><a href="data/soundpeats/reports/S2_1.2.txt">S2</a></td><td>Qualcomm</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td></tr>
<tr><td>Air6 Pro</td><td>S27</td><td>Qualcomm</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Air6 Pro</td><td>S32</td><td>Qualcomm</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Breezy</td><td>ZKLXBREEZY</td><td>Bluetrum</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Capsule3 Pro</td><td>QCYTS04</td><td>QCY</td><td colspan="16">Not supported: a different protocol (docs/soundpeats/PROTOCOL.md section 10)</td></tr>
<tr><td>Capsule3 Pro</td><td>SPTS04</td><td>QCY</td><td colspan="16">Not supported: a different protocol (docs/soundpeats/PROTOCOL.md section 10)</td></tr>
<tr><td>Chasers</td><td>ZKLXCHASERS</td><td>Bluetrum</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Clear</td><td>JL6973Clear</td><td>Jieli</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>CyberGear</td><td>ATS3019DK12</td><td>Actions</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Engine 4</td><td>WuQiEngine4</td><td>WUQI</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>GoFree</td><td>3007AGOFREE</td><td>WUQI</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>GoFree 2</td><td>NEW-WQ7034MXGoFree2</td><td>WUQI</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>GoFree 2</td><td>WQ7034MXGoFree2</td><td>WUQI</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>H1</td><td>BT5107</td><td>Qualcomm</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>H3</td><td><a href="data/soundpeats/reports/S5_0.4.9.txt">S5</a></td><td>Qualcomm</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>—</td><td>✅</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>Hi Listening</td><td>HILISTENING</td><td>Qualcomm</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Life</td><td>T57</td><td>Smartlink</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Life3</td><td>JIELILIFE3</td><td>Jieli</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Mini HS</td><td>WUQIMINIHS</td><td>WUQI</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Mini Pro</td><td>QCC3040</td><td>Qualcomm</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Mini Pro HS</td><td>WQT7033</td><td>WUQI</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Opera03</td><td>OPERA03</td><td>WUQI</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Opera05</td><td>OPERA05</td><td>WUQI</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>PearlClip Pro</td><td>BT893XPro</td><td>Bluetrum</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>PearlClip Pro</td><td>S13</td><td>Bluetrum</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>RunFree</td><td>JULIRUNFREE</td><td>Actions</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>RunFree HS</td><td>RUNFREEHS</td><td>WUQI</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>RunFree Lite</td><td>FREELITE</td><td>WUQI</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>RunFree Lite2</td><td>JULI3025Lite2</td><td>Actions</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Sonic</td><td>T28</td><td>Qualcomm</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Sonic Pro</td><td>T28Pro</td><td>Qualcomm</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Space</td><td>R1292A7018F</td><td>Jieli</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Space Pro</td><td>ZKLXSPACEPRO</td><td>Bluetrum</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>TrueAir2</td><td>BS343RB</td><td>Qualcomm</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>TRUEFREE C1, TRUEFREE soft</td><td>JL6973D8C1</td><td>Jieli</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>TRUEFREE F2</td><td>WuQi7034MXF2</td><td>WUQI</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Truengine 3 SE, Truengine 3SE</td><td>BT5106-3040</td><td>Qualcomm</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
<tr><td>Wings2</td><td>TS03WINGS2</td><td>Jieli</td><td colspan="15"><a href="#help-add-your-model">Needs a device report</a></td><td><a href="#record-from-peatsaudio">Needs a recording</a></td></tr>
</table>
<!-- models-table:end -->

</details>

### Other models

Almost every model the official app supports uses the same protocol, so PairFree
should connect to the other models in `data/soundpeats/devices.json` (all but the Capsule3 Pro)
and to newer ones, and show whatever they answer. None of them has been tested yet. The equalizer appears only once
someone has recorded the model's curves.

If you have one, see [Help add your model](#help-add-your-model): one recording from
your phone adds its controls and its equalizer to the table.

The Capsule3 Pro is the exception. It uses a different protocol over Bluetooth LE,
documented in `docs/soundpeats/PROTOCOL.md` section 10, and PairFree does not support it yet.

## Soundcore (Anker) models

PairFree is growing into one app for earbuds of several brands, with the same screens for
each. Soundcore support is in progress. PairFree lists every model below among your paired
earbuds and shows its battery, noise control, game mode, LDAC and two devices at once, where
the model has them. It also sets the equalizer, presets and custom curves, on the 12 models
whose EQ is Tested or Checked below. Only the Space A40 has been tried so far; touch controls
and the other settings come next.

Soundcore support is built on [OpenSCQ30](https://github.com/Oppzippy/OpenSCQ30) by Kyle
Scheuing, the open-source Soundcore app, licensed GPL-3.0-or-later like PairFree. The model
list, each model's features, the packet format, where each model puts each setting in its
replies, and the EQ correction come from OpenSCQ30's source, and its tests supply the replies
the layouts are checked against. Recordings from a real Space A40 (`data/soundcore/reports/`)
confirmed them, and showed that its case reports its battery in percent.
`docs/soundcore/PROTOCOL.md` describes the protocol.

<details open>
<summary>Every Soundcore model and what it has</summary>

<!-- soundcore-table:start (generated by tools/gen_soundcore_table.py; do not edit) -->
39 models. What each model has, and where each setting sits in its replies, comes from <a href="https://github.com/Oppzippy/OpenSCQ30">OpenSCQ30</a> (commit <code>bc77a29</code>, 2026-09-26), the open-source Soundcore app by Kyle Scheuing, plus what PairFree confirmed on the earbuds.

<b>Tested</b> (1): tried on the earbuds with PairFree. <b>Checked</b> (21): PairFree reads every reply from the model that OpenSCQ30's tests hold, most of them from its users' bug reports. <b>Not tested</b>: read from OpenSCQ30's code alone. A device report from one of these helps (Help add your model, below).

✅ the model has it. — it does not, as far as OpenSCQ30 knows. PairFree shows battery, noise control, two devices, LDAC, game mode, touch controls and touch tone on every model that has them, and the equalizer where its column says Tested (recorded from the Soundcore app) or Checked (OpenSCQ30's tests hold the exact packet). Touch controls say the same of their writes. The other columns are not in PairFree yet.

<table>
<tr><th>Model</th><th>Number</th><th>Status</th><th>Battery</th><th>Noise control</th><th>Equalizer</th><th>HearID</th><th>Touch controls</th><th>Two devices</th><th>LDAC</th><th>Game mode</th><th>3D / spatial</th><th>Wear detection</th><th>Touch tone</th><th>Auto power-off</th></tr>
<tr><td>A20i</td><td>A3948</td><td>Checked</td><td>Each earbud</td><td>—</td><td>✅ Not tested</td><td>—</td><td>✅ Checked</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>—</td></tr>
<tr><td>AeroClip</td><td>A3388</td><td>Checked</td><td>Each earbud, case</td><td>—</td><td>✅ Checked</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>—</td></tr>
<tr><td>C30i</td><td>A3330</td><td>Checked</td><td>Each earbud, case</td><td>—</td><td>✅ Not tested</td><td>—</td><td>✅ Checked</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>—</td></tr>
<tr><td>C50i</td><td>D1101</td><td>Checked</td><td>Each earbud</td><td>—</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td></tr>
<tr><td>Liberty 2 Pro</td><td>A3909</td><td>Checked</td><td>Each earbud</td><td>—</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>Liberty 2 Pro+</td><td>A3930</td><td>Not tested</td><td>Each earbud</td><td>✅</td><td>✅ Not tested</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>Liberty 3 Pro</td><td>A3952</td><td>Checked</td><td>Each earbud, case</td><td>✅</td><td>✅ Not tested</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅</td><td>—</td><td>—</td><td>✅</td><td>✅</td><td>✅</td></tr>
<tr><td>Liberty 4 NC</td><td>A3947</td><td>Checked</td><td>Each earbud, case</td><td>✅</td><td>✅ Checked</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>Liberty 4 Pro</td><td>A3954</td><td>Checked</td><td>Each earbud, case</td><td>✅</td><td>✅ Checked</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>—</td><td>✅</td></tr>
<tr><td>Liberty 5</td><td>A3957</td><td>Checked</td><td>Each earbud, case</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>✅</td></tr>
<tr><td>Liberty Air 2 Pro</td><td>A3951</td><td>Not tested</td><td>Each earbud</td><td>✅</td><td>✅ Not tested</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅</td><td>—</td><td>—</td><td>✅</td><td>✅</td><td>—</td></tr>
<tr><td>Life 2 Neo</td><td>A3033</td><td>Not tested</td><td>One</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>—</td></tr>
<tr><td>Life A2 NC</td><td>A3935</td><td>Not tested</td><td>Each earbud</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>Life Dot 2 NC</td><td>A3931</td><td>Not tested</td><td>Each earbud</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>Life Dot 2S</td><td>A3926</td><td>Not tested</td><td>Each earbud</td><td>✅</td><td>✅ Not tested</td><td>✅</td><td>✅ Checked</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>Life Note 3</td><td>A3933</td><td>Not tested</td><td>Each earbud, case</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>—</td></tr>
<tr><td>Life Note 3S</td><td>A3945</td><td>Not tested</td><td>Each earbud, case</td><td>—</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>—</td></tr>
<tr><td>Life P2 Mini</td><td>A3944</td><td>Not tested</td><td>Each earbud</td><td>—</td><td>✅ Checked</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>—</td></tr>
<tr><td>Life P3</td><td>A3939</td><td>Not tested</td><td>Each earbud, case</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>—</td></tr>
<tr><td>Life Q35</td><td>A3027</td><td>Not tested</td><td>One</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>—</td></tr>
<tr><td>Life Tune</td><td>A3029</td><td>Not tested</td><td>One</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td></tr>
<tr><td>Life Tune Pro</td><td>A3030</td><td>Not tested</td><td>One</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>—</td></tr>
<tr><td>Motion+</td><td>A3116</td><td>Checked</td><td>One</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td></tr>
<tr><td>P20i, P25i, R50i</td><td>A3949</td><td>Checked</td><td>Each earbud</td><td>—</td><td>✅ Checked</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>—</td><td>✅</td><td>—</td></tr>
<tr><td>P30i, R50i NC</td><td>A3959</td><td>Checked</td><td>Each earbud</td><td>✅</td><td>✅ Checked</td><td>—</td><td>✅ Checked</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>P31i</td><td>D1202</td><td>Not tested</td><td>Each earbud, case</td><td>✅</td><td>✅ Checked</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>P40i</td><td>A3955</td><td>Checked</td><td>Each earbud, case</td><td>✅</td><td>✅ Checked</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>Q11i</td><td>A3005</td><td>Checked</td><td>One</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td></tr>
<tr><td>Q20I</td><td>A3004</td><td>Not tested</td><td>One</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>Q30, Life Q30</td><td>A3028</td><td>Not tested</td><td>One</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td></tr>
<tr><td>R60i NC</td><td>D1202C</td><td>Checked</td><td>Each earbud, case</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>Sleep A30</td><td>D1301</td><td>Checked</td><td>Each earbud</td><td>✅</td><td>—</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>Space A40</td><td><a href="data/soundcore/reports/A3936_06.43.txt">A3936</a></td><td>Tested</td><td>Each earbud, case</td><td>✅</td><td>✅ Tested</td><td>✅</td><td>✅ Tested</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>Space One</td><td>A3035</td><td>Checked</td><td>One</td><td>✅</td><td>✅ Checked</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td></tr>
<tr><td>Space One Pro</td><td>A3062</td><td>Checked</td><td>One</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>—</td><td>—</td><td>✅</td></tr>
<tr><td>Space Q45</td><td>A3040</td><td>Checked</td><td>One</td><td>✅</td><td>✅ Checked</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td></tr>
<tr><td>Sport X20</td><td>A3968</td><td>Checked</td><td>Each earbud, case</td><td>✅</td><td>✅ Not tested</td><td>✅</td><td>✅ Not tested</td><td>✅</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>V20i</td><td>A3876</td><td>Checked</td><td>Each earbud</td><td>—</td><td>✅ Checked</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>—</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>✅</td></tr>
<tr><td>Vortex</td><td>A3031</td><td>Not tested</td><td>Each earbud</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>✅</td></tr>
</table>
<!-- soundcore-table:end -->

</details>

## Known issue: Air5 Pro+ LDAC does not work

On a Samsung Galaxy S24 FE (Android 16, One UI 8), the **Air5 Pro+** advertises LDAC but
the phone refuses to use it: audio stays on AAC, and forcing LDAC in Developer options is
rejected. Turning LDAC on also removes aptX, so it *lowers* quality. The **H3**, with the
same Qualcomm QCC3091 chip, runs LDAC at 96 kHz / 32-bit on the same phone, so this looks
like an Air5 Pro+ firmware bug. It has been reported to SoundPEATS. Until it is fixed,
leave LDAC off on the Air5 Pro+ and you get aptX.

Details and the log evidence: `docs/soundpeats/PROTOCOL.md` section 12 and
`data/soundpeats/reports/air5-pro-plus_ldac.txt`.

**Have an Air5 Pro+ and a different phone?** Please help find out whether this is
Samsung-only:

1. Turn LDAC on (in PeatsAudio or PairFree), connect, and play music.
2. Enable Developer options on the phone, open **Bluetooth Audio Codec**, and note which
   codec is active and which are listed.
3. Select **LDAC**. Does the codec change (a brief audio pause) or stay on AAC?
4. Open an issue with your phone model, Android version, the Air5 Pro+ firmware version
   (from PeatsAudio; the one tested here is S2 1.2) and what you saw.

## What is in the repo

| Path                      | What it is                                                        |
|---------------------------|-------------------------------------------------------------------|
| `docs/soundpeats/PROTOCOL.md`        | The protocol: transport, frames, commands, EQ, chip families, firmware update |
| `docs/DESIGN.md`          | The app design: requirements, the Studio look, screens            |
| `data/soundpeats/commands.json`      | The command table in machine-readable form. Apps load or mirror this. |
| `data/soundpeats/test_vectors.json`  | Frames every implementation must reproduce byte for byte          |
| `data/soundpeats/devices.json`       | Known models, their firmware codes, chip families and per-model details |
| `data/soundpeats/qcy_commands.json`  | The separate protocol for the Capsule3 Pro                        |
| `data/soundpeats/reports/`           | Device reports and recordings from real earbuds, one file per model and firmware |
| `tools/fetch_server_data.py` | Owner-only research tool for SoundPEATS' server. The server now refuses it (authentication), so it gets nothing. |
| `tools/probe.py`          | Read-only probe. Asks the earbuds what they support.              |
| `tools/import_eq_capture.py` | Turns the equalizer part of a recording from the app into a model's EQ data. |
| `tools/gen_models_table.py` | Writes the All models table in this README from `data/soundpeats/devices.json` and the reports. |
| `tools/test_probe.py`     | Tests for the probe and the data files. No hardware needed.       |
| `assets/devices/soundpeats/`         | Vector drawings of each model's earbud and case, per look         |
| `tools/gen_device_art.py` | Generates those drawings. Edit the shapes there, not the SVG files. |
| `tools/device_art/soundpeats/`       | Traced outlines for each model, read by `gen_device_art.py`       |
| `android/`                | The Android app. `protocol/` is the protocol core, `app/` the app. |

## Help add your model

One report from someone who owns a model adds it to the [All models](#all-models) table
for everyone. There are three ways to make one. The first gives the most.

### Record from PeatsAudio

This gathers everything in one go: every setting the official PeatsAudio app reads from
your earbuds (which shows what SoundPEATS supports on your model), the equalizer curves
and presets it sends, and PairFree's own device report. It takes about ten minutes on
your phone, with no computer.

In PairFree, open the Device tab and tap **Record from PeatsAudio** (or the button on the
Sound tab). It walks you through it:

1. Turn on Developer options and set **Bluetooth HCI snoop log** to **Enabled**.
2. In PeatsAudio, open each screen once without changing anything, run its fit test if it
   has one, then tap SOUNDPEATS Classic and every other EQ preset once.
3. Take a bug report from Developer options (**Take bug report**, then **Full report**).
   **It takes several minutes, and Android shows nothing while it works: wait for the
   notification that says it is ready.** Then tap it and share it to PairFree.
4. Name each EQ curve PairFree found, and tap **Send on GitHub**.

PairFree reads the bug report only on your phone and keeps nothing but the messages
exchanged with your SoundPEATS earbuds. The report you send has no names or addresses.
Afterwards, turn the snoop log off and delete the bug report.

### Copy device report

Quicker, and no PeatsAudio needed, but no equalizer. Connect the earbuds in PairFree,
tap **Copy device report** on the Device tab, and paste it into a new issue. It lists every
setting PairFree can read and what the earbuds answered. On Soundcore earbuds it holds the
earbuds' whole state reply, with the serial number masked, which checks the model's row in
the Soundcore table.

### Probe from a PC

The same information as Copy device report, from a PC with Bluetooth and Python 3.9 or
newer.

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

## Credits

- [OpenSCQ30](https://github.com/Oppzippy/OpenSCQ30) by Kyle Scheuing (GPL-3.0-or-later): the
  Soundcore protocol, model list and per-model features that PairFree's Soundcore support is
  ported from. Code ported from it says so where it is.
- [IBM Plex](https://github.com/IBM/plex) fonts (SIL Open Font License 1.1).

## License

GNU General Public License, version 3 or (at your option) any later version
(GPL-3.0-or-later). See `LICENSE`.
