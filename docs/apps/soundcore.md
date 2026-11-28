---
layout: page
title: "Open-source Soundcore app (for Anker Soundcore earbuds)"
description: "PairFree is a free, open-source Android and Windows app that controls Soundcore (Anker) earbuds and headphones without the Soundcore app: no account, no internet access."
permalink: /open-source-soundcore-app/
---

PairFree is a free, open-source Android and Windows app that controls Soundcore (Anker) earbuds and headphones without the Soundcore app: no account, no internet access.

PairFree does not need the Soundcore app to run, but only one app can hold the control connection to the earbuds at a time, so close the Soundcore app first.

## What you can control

Each model shows only the settings it has:

- Battery for each earbud and the case, and noise control (sound modes).
- The equalizer: Soundcore's presets and your own curves, on every model that has an equalizer.
- Touch controls and touch tone, game mode, LDAC and two devices at once, where the model has them.
- The Sleep A20's sleep timer.

## On Windows too

Almost none of these brands makes a Windows app, so on a PC their earbuds are plain Bluetooth audio. PairFree for Windows is one small portable program (no installer) with the same settings as the Android app, in a window made for a mouse and with a tray icon for the battery and quick switches. It needs Windows 10 (version 1809) or 11, 64-bit, with the earbuds paired in Windows' Bluetooth settings. Soundcore is covered.

<p><img src="https://omega-destroyer.github.io/PairFree/screenshots/android-home.png" width="250" alt="PairFree on Android: Home"> <img src="https://omega-destroyer.github.io/PairFree/screenshots/android-sound.png" width="250" alt="PairFree on Android: Sound"> <img src="https://omega-destroyer.github.io/PairFree/screenshots/windows-home.png" width="420" alt="PairFree on Windows: Home"></p>

## Soundcore models

PairFree knows 83 Soundcore models. Models that have been tried on real earbuds: AeroClip, P30i, R50i NC, Q20I, Sleep A20, Space A40.

Soundcore support is built on OpenSCQ30, the open-source Soundcore app by Kyle Scheuing (GPL-3.0-or-later), and checked against recordings from real earbuds.

If your model is not marked as tried, a device report from you adds it: see [how to help](https://github.com/Omega-Destroyer/PairFree#help-add-your-model).

## What works on each Soundcore model

<div style="overflow-x:auto">
83 models. What each model has, and where each setting sits in its replies, comes from <a href="https://github.com/Oppzippy/OpenSCQ30">OpenSCQ30</a> (commit <code>bc77a29</code>, 2026-09-26), the open-source Soundcore app by Kyle Scheuing, checked against the Soundcore app's own code (version 2.9.9, 3.3.0, the last one that can be read) where it has the model, plus what PairFree confirmed on the earbuds.

<b>Tested</b> (5): tried on the earbuds with PairFree. <b>Checked</b> (43): PairFree reads every reply from the model that OpenSCQ30's tests hold, most of them from its users' bug reports, or reads the reply as the Soundcore app's own code does. <b>Not tested</b>: read from OpenSCQ30's code alone. <b>Report only</b>: the Soundcore app lists it, but nothing gives its layout yet; PairFree finds it and offers a device report, and shows no settings. A device report from any of these helps (<a href="https://github.com/Omega-Destroyer/PairFree/blob/main/README.md#help-add-your-model">Help add your model</a>).

✅ the model has it. — it does not, as far as OpenSCQ30 knows. PairFree shows battery, noise control, two devices, LDAC, game mode, touch controls and touch tone on every model that has them, and the equalizer: its column says Tested (recorded from the Soundcore app on the owner's earbuds), Recorded (recorded from the Soundcore app on a user's earbuds), Checked (OpenSCQ30's tests hold the exact packet) or Not tested (OpenSCQ30's code alone). Touch controls say the same of their writes. The other columns are not in PairFree yet.

<table>
<tr><th>Model</th><th>Number</th><th>Status</th><th>Battery</th><th>Noise control</th><th>Equalizer</th><th>HearID</th><th>Touch controls</th><th>Two devices</th><th>LDAC</th><th>Game mode</th><th>3D / spatial</th><th>Wear detection</th><th>Touch tone</th><th>Auto power-off</th></tr>
<tr><td>Anker AeroClip 2</td><td>D1105</td><td>Not tested</td><td>Each earbud, case</td><td>—</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>Anker Sleep Earbuds 4</td><td>D1303</td><td>Not tested</td><td>Each earbud, case</td><td>—</td><td>—</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>—</td></tr>
<tr><td>Anker Sleep Earbuds 4 Pro</td><td>D1302</td><td>Not tested</td><td>Each earbud, case</td><td>—</td><td>—</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>—</td></tr>
<tr><td>Anker soundcore Q31i, soundcore Q31i</td><td>D1404</td><td>Not tested</td><td>One</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>Anker Space 2 Pro</td><td>D1406</td><td>Not tested</td><td>One</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>Anker Space 2 Pro Max</td><td>D1407</td><td>Not tested</td><td>One</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>Liberty Buds</td><td>D1200</td><td>Not tested</td><td>Each earbud, case</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>✅</td></tr>
<tr><td>Liberty Buds 2</td><td>D1206</td><td>Not tested</td><td>Each earbud, case</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>—</td></tr>
<tr><td>A20i, soundcore A25i</td><td>A3948</td><td>Checked</td><td>Each earbud</td><td>—</td><td>✅ Not tested</td><td>—</td><td>✅ Checked</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>—</td></tr>
<tr><td>soundcore A30i</td><td>A3958</td><td>Not tested</td><td>Each earbud</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>AeroClip</td><td><a href="https://github.com/Omega-Destroyer/PairFree/blob/main/data/soundcore/reports/A3388_02.49.txt">A3388</a></td><td>Tested</td><td>Each earbud, case</td><td>—</td><td>✅ Checked</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>—</td></tr>
<tr><td>soundcore AeroClip 2 Pro</td><td>D1102</td><td>Not tested</td><td>Each earbud, case</td><td>—</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>—</td></tr>
<tr><td>soundcore AeroFit</td><td>A3872</td><td>Not tested</td><td>Each earbud, case</td><td>—</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>soundcore AeroFit 2, AeroFit 2 AI Assistant</td><td>A3874</td><td>Not tested</td><td>Each earbud, case</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>—</td></tr>
<tr><td>soundcore AeroFit 2 Pro</td><td>A3875</td><td>Not tested</td><td>Each earbud, case</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>soundcore AeroFit Pro</td><td>A3871</td><td>Not tested</td><td>Each earbud, case</td><td>—</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>C30i</td><td>A3330</td><td>Checked</td><td>Each earbud, case</td><td>—</td><td>✅ Not tested</td><td>—</td><td>✅ Checked</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>—</td></tr>
<tr><td>soundcore C40i</td><td>A3331</td><td>Not tested</td><td>Each earbud, case</td><td>—</td><td>✅ Not tested</td><td>—</td><td>✅ Checked</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>—</td></tr>
<tr><td>C50i</td><td>D1101</td><td>Checked</td><td>Each earbud</td><td>—</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td></tr>
<tr><td>Frames</td><td>A3600</td><td>Not tested</td><td>Each earbud</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>soundcore H30i</td><td>A3012</td><td>Not tested</td><td>One</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>soundcore K20i</td><td>A3994</td><td>Not tested</td><td>Each earbud</td><td>—</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>Liberty 2</td><td>A3913</td><td>Checked</td><td>Each earbud</td><td>—</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>Liberty 2 Pro</td><td>A3909</td><td>Checked</td><td>Each earbud</td><td>—</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>Liberty 2 Pro+</td><td>A3930</td><td>Checked</td><td>Each earbud</td><td>✅</td><td>✅ Not tested</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>Liberty 3 Pro</td><td>A3952</td><td>Checked</td><td>Each earbud, case</td><td>✅</td><td>✅ Not tested</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅</td><td>—</td><td>—</td><td>✅</td><td>✅</td><td>✅</td></tr>
<tr><td>Liberty 4</td><td>A3953</td><td>Checked</td><td>Each earbud, case</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>—</td><td>✅</td></tr>
<tr><td>Liberty 4 NC</td><td>A3947</td><td>Checked</td><td>Each earbud, case</td><td>✅</td><td>✅ Checked</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>Liberty 4 Pro</td><td>A3954</td><td>Checked</td><td>Each earbud, case</td><td>✅</td><td>✅ Checked</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>—</td><td>✅</td></tr>
<tr><td>Liberty 5</td><td>A3957</td><td>Checked</td><td>Each earbud, case</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>✅</td></tr>
<tr><td>soundcore Liberty 5 Pro</td><td>D1203</td><td>Not tested</td><td>Each earbud, case</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>—</td></tr>
<tr><td>soundcore Liberty 5 Pro Max</td><td>D1204</td><td>Not tested</td><td>Each earbud, case</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>—</td></tr>
<tr><td>Liberty Air 2 Pro</td><td>A3951</td><td>Checked</td><td>Each earbud</td><td>✅</td><td>✅ Not tested</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅</td><td>—</td><td>—</td><td>✅</td><td>✅</td><td>—</td></tr>
<tr><td>Liberty Air2, Liberty Air 2</td><td>A3910</td><td>Checked</td><td>Each earbud</td><td>—</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>Life 2 Neo, Q10i</td><td>A3033</td><td>Checked</td><td>One</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>—</td></tr>
<tr><td>Life A1, A1</td><td>A3927</td><td>Checked</td><td>Each earbud</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>Life A2 NC, Life A2 NC+</td><td>A3935</td><td>Checked</td><td>Each earbud</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>Life A3i, A3i</td><td>A3992</td><td>Checked</td><td>Each earbud, case</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>—</td></tr>
<tr><td>Life Dot 2 NC, Life Dot 2 XR</td><td>A3931</td><td>Checked</td><td>Each earbud</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>Life Dot 2S, Liberty Neo 2</td><td>A3926</td><td>Checked</td><td>Each earbud</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>✅ Checked</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>Life Dot 3i, Dot 3i</td><td>A3982</td><td>Checked</td><td>Each earbud, case</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>—</td></tr>
<tr><td>Life NC</td><td>A3201</td><td>Not tested</td><td>One</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>Life Note 3</td><td>A3933</td><td>Checked</td><td>Each earbud, case</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>—</td></tr>
<tr><td>Life Note 3i, Note 3i</td><td>A3983</td><td>Checked</td><td>Each earbud, case</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>—</td></tr>
<tr><td>Life Note 3S</td><td>A3945</td><td>Checked</td><td>Each earbud, case</td><td>—</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>—</td></tr>
<tr><td>soundcore Life Note C</td><td>A3943</td><td>Not tested</td><td>Each earbud</td><td>—</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>—</td></tr>
<tr><td>Life P2 Mini, soundcore P2 Mini</td><td>A3944</td><td>Not tested</td><td>Each earbud</td><td>—</td><td>✅ Checked</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>—</td></tr>
<tr><td>Life P3, P3</td><td>A3939</td><td>Checked</td><td>Each earbud, case</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>—</td></tr>
<tr><td>Life P3i, P3i</td><td>A3993</td><td>Checked</td><td>Each earbud, case</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>—</td></tr>
<tr><td>soundcore Life Q20, soundcore Q20</td><td>A3025</td><td>Checked</td><td>One</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td></tr>
<tr><td>Life Q20+, soundcore Life Q20+ SE</td><td>A3045</td><td>Not tested</td><td>One</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td></tr>
<tr><td>Life Q35, Q35</td><td>A3027</td><td>Checked</td><td>One</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>—</td></tr>
<tr><td>Life Tune, Tune, Life Tune XR</td><td>A3029</td><td>Checked</td><td>One</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td></tr>
<tr><td>Life Tune Pro</td><td>A3030</td><td>Checked</td><td>One</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>—</td></tr>
<tr><td>Motion+</td><td>A3116</td><td>Checked</td><td>One</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td></tr>
<tr><td>P20i, P25i, R50i</td><td>A3949</td><td>Checked</td><td>Each earbud</td><td>—</td><td>✅ Checked</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>—</td><td>✅</td><td>—</td></tr>
<tr><td>P30i, R50i NC</td><td><a href="https://github.com/Omega-Destroyer/PairFree/blob/main/data/soundcore/reports/A3959_01.65.txt">A3959</a></td><td>Tested</td><td>Each earbud</td><td>✅</td><td>✅ Checked</td><td>—</td><td>✅ Checked</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>P31i</td><td>D1202</td><td>Not tested</td><td>Each earbud, case</td><td>✅</td><td>✅ Checked</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>P40i</td><td>A3955</td><td>Checked</td><td>Each earbud, case</td><td>✅</td><td>✅ Checked</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>soundcore P41i</td><td>A3937</td><td>Not tested</td><td>Each earbud, case</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>soundcore P42i</td><td>D1205</td><td>Not tested</td><td>Each earbud, case</td><td>—</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>Q11i</td><td>A3005</td><td>Checked</td><td>One</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td></tr>
<tr><td>Q20I</td><td><a href="https://github.com/Omega-Destroyer/PairFree/blob/main/data/soundcore/reports/A3004_05.08.txt">A3004</a></td><td>Tested</td><td>One</td><td>✅</td><td>✅ Recorded</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>soundcore Q20i Plus</td><td>A3004C</td><td>Not tested</td><td>One</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>soundcore Q21i NC</td><td>A300B</td><td>Not tested</td><td>One</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>Q30, Life Q30</td><td>A3028</td><td>Checked</td><td>One</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td></tr>
<tr><td>soundcore R50i VI</td><td>A3969</td><td>Not tested</td><td>Each earbud</td><td>—</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>—</td><td>✅</td><td>—</td></tr>
<tr><td>R60i NC</td><td>D1202C</td><td>Checked</td><td>Each earbud, case</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>Sleep A10</td><td>A6610</td><td>Checked</td><td>Each earbud</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>Sleep A20</td><td><a href="https://github.com/Omega-Destroyer/PairFree/blob/main/data/soundcore/reports/A6611_03.22.txt">A6611</a></td><td>Tested</td><td>Each earbud, case</td><td>—</td><td>✅ Recorded</td><td>—</td><td>✅ Recorded</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>Sleep A30, Sleep A30 Special</td><td>D1301</td><td>Checked</td><td>Each earbud</td><td>✅</td><td>—</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>Space A40</td><td><a href="https://github.com/Omega-Destroyer/PairFree/blob/main/data/soundcore/reports/A3936_06.43.txt">A3936</a></td><td>Tested</td><td>Each earbud, case</td><td>✅</td><td>✅ Tested</td><td>✅</td><td>✅ Tested</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>Space One, soundcore CS 30Q</td><td>A3035</td><td>Checked</td><td>One</td><td>✅</td><td>✅ Checked</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td></tr>
<tr><td>Space One Pro</td><td>A3062</td><td>Checked</td><td>One</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>—</td><td>—</td><td>✅</td></tr>
<tr><td>Space Q45</td><td>A3040</td><td>Checked</td><td>One</td><td>✅</td><td>✅ Checked</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td></tr>
<tr><td>Sport X10</td><td>A3961</td><td>Not tested</td><td>Each earbud, case</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>—</td></tr>
<tr><td>Sport X20</td><td>A3968</td><td>Checked</td><td>Each earbud, case</td><td>✅</td><td>✅ Not tested</td><td>✅</td><td>✅ Not tested</td><td>✅</td><td>—</td><td>—</td><td>✅</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>V20i</td><td>A3876</td><td>Checked</td><td>Each earbud</td><td>—</td><td>✅ Checked</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>—</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>✅</td></tr>
<tr><td>soundcore V30i</td><td>A3873</td><td>Not tested</td><td>Each earbud, case</td><td>—</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>soundcore V40i</td><td>A3878</td><td>Not tested</td><td>Each earbud</td><td>—</td><td>✅ Not tested</td><td>—</td><td>✅ Not tested</td><td>✅</td><td>—</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>✅</td></tr>
<tr><td>Vortex</td><td>A3031</td><td>Not tested</td><td>Each earbud</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>✅</td></tr>
<tr><td>VR P10</td><td>A3850</td><td>Checked</td><td>Each earbud</td><td>—</td><td>✅ Not tested</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>Space 2</td><td>D1402</td><td>Not tested</td><td>One</td><td>✅</td><td>✅ Not tested</td><td>—</td><td>—</td><td>✅</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td></tr>
</table>
</div>

The same list is on [GitHub](https://github.com/Omega-Destroyer/PairFree/blob/main/docs/devices/soundcore.md).

## Why use PairFree

- **No account and no internet.** The app has no internet permission, so it cannot go online.
- **One app for several brands.** The same screens for every brand, instead of one app per pair of earbuds.
- **Open source.** GPL-3.0; the protocol is documented in the repository with tests, so anyone can check what the app sends to the earbuds.
- **Small and quick:** about 6 MB on Android and 16 MB on Windows, and reproducible: the published APK can be rebuilt from the source.
- **On Windows as well as Android,** where most of these brands have no app at all.

## Get PairFree

On Android, PairFree is on [F-Droid](https://f-droid.org/packages/io.github.omegadestroyer.pairfree/) and on the [GitHub releases page](https://github.com/Omega-Destroyer/PairFree/releases/latest); it needs Android 12 or later. On Windows 10 or 11, download `PairFree.exe` from the same releases page and run it.

## Other brands

PairFree controls earbuds of other brands too:

- [SOUNDPEATS](https://omega-destroyer.github.io/PairFree/open-source-soundpeats-app/)
- [Bose](https://omega-destroyer.github.io/PairFree/open-source-bose-app/)
- [JBL](https://omega-destroyer.github.io/PairFree/open-source-jbl-app/)
- [Sony](https://omega-destroyer.github.io/PairFree/open-source-sony-app/)
- [Samsung Galaxy Buds](https://omega-destroyer.github.io/PairFree/open-source-galaxy-buds-app/)
- [AirPods and Beats](https://omega-destroyer.github.io/PairFree/open-source-airpods-beats-app/)
- [Nothing and CMF](https://omega-destroyer.github.io/PairFree/open-source-nothing-app/)
- [Google Pixel Buds](https://omega-destroyer.github.io/PairFree/open-source-pixel-buds-app/)
- [QCY](https://omega-destroyer.github.io/PairFree/open-source-qcy-app/)
- [1MORE](https://omega-destroyer.github.io/PairFree/open-source-1more-app/)
- [EarFun](https://omega-destroyer.github.io/PairFree/open-source-earfun-app/)
- [Cambridge Audio](https://omega-destroyer.github.io/PairFree/open-source-cambridge-app/)
- [Sennheiser](https://omega-destroyer.github.io/PairFree/open-source-sennheiser-app/)
- [Jabra](https://omega-destroyer.github.io/PairFree/open-source-jabra-app/)
- [Skullcandy](https://omega-destroyer.github.io/PairFree/open-source-skullcandy-app/)
- [OnePlus and OPPO](https://omega-destroyer.github.io/PairFree/open-source-oneplus-app/)

---

PairFree is an independent project. It is not affiliated with or endorsed by SOUNDPEATS, Anker, Samsung, Sony, Bose, Harman (JBL), Apple, Nothing, Google, QCY, 1MORE, EarFun, Cambridge Audio, Sennheiser, Jabra, Skullcandy, OnePlus or OPPO; the names are used to say which earbuds it works with.
