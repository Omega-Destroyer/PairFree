---
layout: page
title: "Open-source Cambridge Audio Melomania app alternative"
description: "PairFree is a free, open-source Android and Windows app that controls Cambridge Audio's Melomania 1+ and Melomania Touch earbuds without the Melomania app: no account, no internet access."
permalink: /open-source-cambridge-app/
---

PairFree is a free, open-source Android and Windows app that controls Cambridge Audio's Melomania 1+ and Melomania Touch earbuds without the Melomania app: no account, no internet access.

PairFree does not need the Melomania app to run, but only one app can hold the control connection to the earbuds at a time, so close the Melomania app first.

## What you can control

Each model shows only the settings it has:

- Battery for each earbud, and on the Touch ambient sound and its level.
- The equalizer: six presets and five gain bands from -6 to +6 dB.
- Which touch functions are on, the voice guidance language, and the codec the earbuds offer (SBC, AAC, aptX).

## On Windows too

Almost none of these brands makes a Windows app, so on a PC their earbuds are plain Bluetooth audio. PairFree for Windows is one small portable program (no installer) with the same settings as the Android app, in a window made for a mouse and with a tray icon for the battery and quick switches. It needs Windows 10 (version 1809) or 11, 64-bit, with the earbuds paired in Windows' Bluetooth settings. Cambridge Audio is covered.

<p><img src="https://omega-destroyer.github.io/PairFree/screenshots/android-home.png" width="250" alt="PairFree on Android: Home"> <img src="https://omega-destroyer.github.io/PairFree/screenshots/android-sound.png" width="250" alt="PairFree on Android: Sound"> <img src="https://omega-destroyer.github.io/PairFree/screenshots/windows-home.png" width="420" alt="PairFree on Windows: Home"></p>

## Cambridge Audio models

PairFree knows 8 Cambridge Audio models. No model of this brand has been tried on real earbuds with PairFree yet; support comes from the brand's own app and reports.

Cambridge Audio support is read from the Melomania app (2.8.12) and its commands were checked against the official app run on a virtual earbud. It has not been tried with PairFree on real earbuds yet. The newer Melomania Connect models (A100, M100, P100) are not covered.

If your model is not marked as tried, a device report from you adds it: see [how to help](https://github.com/Omega-Destroyer/PairFree#help-add-your-model).

## What works on each Cambridge Audio model

<div style="overflow-x:auto">
<table>
<tr><th>Model</th><th>Implemented</th><th>Status</th></tr>
<tr><td>Melomania 1+</td><td>Battery, presets and five gain bands, control toggles, language and codec preference</td><td><a href="https://github.com/Omega-Destroyer/PairFree/blob/main/docs/cambridge/VIRTUAL_TESTING.md">Vendor app tested virtually</a>; no hardware test</td></tr>
<tr><td>Melomania Touch</td><td>Battery, presets and five gain bands, control toggles, language and codec preference, ambient sound and level</td><td><a href="https://github.com/Omega-Destroyer/PairFree/blob/main/docs/cambridge/VIRTUAL_TESTING.md">Vendor app tested virtually</a>; no hardware test</td></tr>
<tr><td>Melomania A100</td><td>Battery, noise control and its button cycle, equalizer with presets, left/right balance, gaming, mono, sleep, loudness, side tone and spatial audio (each where the model has it), wear detection, find alert, touch gestures and their actions, paired devices, voice language, automatic power off, codec choice</td><td>Not tested</td></tr>
<tr><td>Melomania A100 SE</td><td>Battery, noise control and its button cycle, equalizer with presets, left/right balance, gaming, mono, sleep, loudness, side tone and spatial audio (each where the model has it), wear detection, find alert, touch gestures and their actions, paired devices, voice language, automatic power off, codec choice</td><td>Not tested</td></tr>
<tr><td>Melomania M100</td><td>Battery, noise control and its button cycle, equalizer with presets, left/right balance, gaming, mono, sleep, loudness, side tone and spatial audio (each where the model has it), wear detection, find alert, touch gestures and their actions, paired devices, voice language, automatic power off, codec choice</td><td>Not tested</td></tr>
<tr><td>Melomania P100</td><td>Battery, noise control and its button cycle, equalizer with presets, left/right balance, gaming, mono, sleep, loudness, side tone and spatial audio (each where the model has it), wear detection, find alert, touch gestures and their actions, paired devices, voice language, automatic power off, codec choice</td><td>Not tested</td></tr>
<tr><td>Melomania P100 SE</td><td>Battery, noise control and its button cycle, equalizer with presets, left/right balance, gaming, mono, sleep, loudness, side tone and spatial audio (each where the model has it), wear detection, find alert, touch gestures and their actions, paired devices, voice language, automatic power off, codec choice</td><td>Not tested</td></tr>
<tr><td>Melomania X100</td><td>Battery, noise control and its button cycle, equalizer with presets, left/right balance, gaming, mono, sleep, loudness, side tone and spatial audio (each where the model has it), wear detection, find alert, touch gestures and their actions, paired devices, voice language, automatic power off, codec choice</td><td>Not tested</td></tr>
</table>
</div>

The same list is on [GitHub](https://github.com/Omega-Destroyer/PairFree/blob/main/docs/devices/cambridge.md).

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
- [Soundcore](https://omega-destroyer.github.io/PairFree/open-source-soundcore-app/)
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
- [Sennheiser](https://omega-destroyer.github.io/PairFree/open-source-sennheiser-app/)
- [Jabra](https://omega-destroyer.github.io/PairFree/open-source-jabra-app/)
- [Skullcandy](https://omega-destroyer.github.io/PairFree/open-source-skullcandy-app/)
- [OnePlus and OPPO](https://omega-destroyer.github.io/PairFree/open-source-oneplus-app/)

---

PairFree is an independent project. It is not affiliated with or endorsed by SOUNDPEATS, Anker, Samsung, Sony, Bose, Harman (JBL), Apple, Nothing, Google, QCY, 1MORE, EarFun, Cambridge Audio, Sennheiser, Jabra, Skullcandy, OnePlus or OPPO; the names are used to say which earbuds it works with.
