---
layout: page
title: "Open-source Sony headphones app (a Sound Connect alternative)"
description: "PairFree is a free, open-source Android and Windows app that controls Sony earbuds and headphones without Sony | Sound Connect: no account, no internet access."
permalink: /open-source-sony-app/
---

PairFree is a free, open-source Android and Windows app that controls Sony earbuds and headphones without Sony | Sound Connect: no account, no internet access.

PairFree does not need the Sony | Sound Connect app to run, but only one app can hold the control connection to the earbuds at a time, so close the Sony | Sound Connect app first.

## What you can control

Each model shows only the settings it has:

- Battery, and noise control: noise cancelling and ambient sound, with the ambient level, voice focus and speak-to-chat.
- The equalizer, with the model's presets and, where it allows one, a curve of your own; Clear Bass and DSEE upscaling.
- What each touch sensor or button does, from the choices the model lists.
- The connection mode, automatic power-off, pause when taken off, voice guidance and two devices at once.

## On Windows too

Almost none of these brands makes a Windows app, so on a PC their earbuds are plain Bluetooth audio. PairFree for Windows is one small portable program (no installer) with the same settings as the Android app, in a window made for a mouse and with a tray icon for the battery and quick switches. It needs Windows 10 (version 1809) or 11, 64-bit, with the earbuds paired in Windows' Bluetooth settings. Sony is covered.

<p><img src="https://omega-destroyer.github.io/PairFree/screenshots/android-home.png" width="250" alt="PairFree on Android: Home"> <img src="https://omega-destroyer.github.io/PairFree/screenshots/android-sound.png" width="250" alt="PairFree on Android: Sound"> <img src="https://omega-destroyer.github.io/PairFree/screenshots/windows-home.png" width="420" alt="PairFree on Windows: Home"></p>

## Sony models

PairFree knows 24 Sony models. Models that have been tried on real earbuds: WF-1000XM5, WF-1000XM6.

Sony support is read from Sony's own app and checked against Gadgetbridge's Sony support. Each model says itself what it has when PairFree connects. It has not been tried on Sony earbuds or headphones yet; two ULT Field speakers have been read.

If your model is not marked as tried, a device report from you adds it: see [how to help](https://github.com/Omega-Destroyer/PairFree#help-add-your-model).

## What works on each Sony model

<div style="overflow-x:auto">
24 models. Sony's earbuds and headphones say themselves what they have when PairFree connects, so every model gets what it lists, and a model missing here works the same once the phone shows Sony's service for it. <b>Table set</b> is the one the model answered with in <a href="https://codeberg.org/Freeyourgadget/Gadgetbridge">Gadgetbridge</a>'s notes (commit <code>53e478e</code>); PairFree reads it from the earbuds either way.

<b>Tested</b> (2): tried with PairFree. <b>Not tested</b>: from Sony's app and Gadgetbridge alone. A device report from any model helps (<a href="https://github.com/Omega-Destroyer/PairFree/blob/main/README.md#help-add-your-model">Help add your model</a>).

<table>
<tr><th>Model</th><th>Type</th><th>Table set</th><th>Status</th><th>Name from</th></tr>
<tr><td>WH-1000XM2</td><td>Headphones</td><td>—</td><td>Not tested</td><td>Gadgetbridge</td></tr>
<tr><td>WH-1000XM3</td><td>Headphones</td><td>1</td><td>Not tested</td><td>Gadgetbridge</td></tr>
<tr><td>WH-1000XM4</td><td>Headphones</td><td>—</td><td>Not tested</td><td>Gadgetbridge</td></tr>
<tr><td>WH-1000XM5</td><td>Headphones</td><td>2</td><td>Not tested</td><td>Gadgetbridge</td></tr>
<tr><td>WH-1000XM6</td><td>Headphones</td><td>—</td><td>Not tested</td><td>Gadgetbridge</td></tr>
<tr><td>WH-CH720N</td><td>Headphones</td><td>—</td><td>Not tested</td><td>Gadgetbridge</td></tr>
<tr><td>ULT WEAR</td><td>Headphones</td><td>—</td><td>Not tested</td><td>Gadgetbridge</td></tr>
<tr><td>WF-1000XM3</td><td>Earbuds</td><td>—</td><td>Not tested</td><td>Gadgetbridge</td></tr>
<tr><td>WF-1000XM4</td><td>Earbuds</td><td>2</td><td>Not tested</td><td>Gadgetbridge</td></tr>
<tr><td><a href="https://github.com/Omega-Destroyer/PairFree/blob/main/data/sony/reports/WF-1000XM5_6.1.0.txt">WF-1000XM5</a></td><td>Earbuds</td><td>2</td><td>Tested</td><td>Gadgetbridge</td></tr>
<tr><td><a href="https://github.com/Omega-Destroyer/PairFree/blob/main/data/sony/reports/WF-1000XM6_0.11.2.txt">WF-1000XM6</a></td><td>Earbuds</td><td>—</td><td>Tested</td><td>Sony's app</td></tr>
<tr><td>WF-SP800N</td><td>Earbuds</td><td>1</td><td>Not tested</td><td>Gadgetbridge</td></tr>
<tr><td>WF-C500</td><td>Earbuds</td><td>—</td><td>Not tested</td><td>Gadgetbridge</td></tr>
<tr><td>WF-C510</td><td>Earbuds</td><td>—</td><td>Not tested</td><td>Gadgetbridge</td></tr>
<tr><td>WF-C700N</td><td>Earbuds</td><td>—</td><td>Not tested</td><td>Gadgetbridge</td></tr>
<tr><td>WF-C710N</td><td>Earbuds</td><td>—</td><td>Not tested</td><td>Gadgetbridge</td></tr>
<tr><td>LinkBuds</td><td>Earbuds</td><td>2</td><td>Not tested</td><td>Gadgetbridge</td></tr>
<tr><td>LinkBuds S</td><td>Earbuds</td><td>2</td><td>Not tested</td><td>Gadgetbridge</td></tr>
<tr><td>LinkBuds UC</td><td>Earbuds</td><td>—</td><td>Not tested</td><td>Sony's app</td></tr>
<tr><td>LinkBuds Open</td><td>Earbuds</td><td>—</td><td>Not tested</td><td>Sony's app</td></tr>
<tr><td>LinkBuds Fit</td><td>Earbuds</td><td>—</td><td>Not tested</td><td>Sony's app</td></tr>
<tr><td>LinkBuds Clip</td><td>Earbuds</td><td>—</td><td>Not tested</td><td>Sony's app</td></tr>
<tr><td>WI-C100</td><td>Neckband</td><td>—</td><td>Not tested</td><td>Gadgetbridge</td></tr>
<tr><td>WI-SP600N</td><td>Neckband</td><td>1</td><td>Not tested</td><td>Gadgetbridge</td></tr>
</table>
</div>

The same list is on [GitHub](https://github.com/Omega-Destroyer/PairFree/blob/main/docs/devices/sony.md).

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
- [Huawei and Honor](https://omega-destroyer.github.io/PairFree/open-source-huawei-app/)
- [OnePlus and OPPO](https://omega-destroyer.github.io/PairFree/open-source-oneplus-app/)

---

PairFree is an independent project. It is not affiliated with or endorsed by SOUNDPEATS, Anker, Samsung, Sony, Bose, Harman (JBL), Apple, Nothing, Google, QCY, 1MORE, EarFun, Cambridge Audio, Sennheiser, Jabra, Skullcandy, Huawei, Honor, OnePlus or OPPO; the names are used to say which earbuds it works with.
