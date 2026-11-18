---
layout: page
title: "Open-source Pixel Buds app (for Android phones that are not Pixels)"
description: "PairFree is a free, open-source Android and Windows app for Google Pixel Buds: no account, no internet access, and it does not need a Pixel phone."
permalink: /open-source-pixel-buds-app/
---

PairFree is a free, open-source Android and Windows app for Google Pixel Buds: no account, no internet access, and it does not need a Pixel phone.

PairFree does not need the Pixel Buds app to run, but only one app can hold the control connection to the earbuds at a time, so close the Pixel Buds app first.

## What you can control

Each model shows only the settings it has:

- Battery for each earbud and the case.
- Noise control on the Pixel Buds Pro, Pro 2 and 2a, and adaptive noise control on the Pro 2.
- The equalizer on the Pro, Pro 2 and 2a: Google's presets and a curve of your own over five bands.
- Press and hold actions, conversation detection, volume EQ and balance, where the model has them.

## On Windows too

Almost none of these brands makes a Windows app, so on a PC their earbuds are plain Bluetooth audio. PairFree for Windows is one small portable program (no installer) with the same settings as the Android app, in a window made for a mouse and with a tray icon for the battery and quick switches. It needs Windows 10 (version 1809) or 11, 64-bit, with the earbuds paired in Windows' Bluetooth settings. Google Pixel Buds is covered.

<p><img src="https://omega-destroyer.github.io/PairFree/screenshots/android-home.png" width="250" alt="PairFree on Android: Home"> <img src="https://omega-destroyer.github.io/PairFree/screenshots/android-sound.png" width="250" alt="PairFree on Android: Sound"> <img src="https://omega-destroyer.github.io/PairFree/screenshots/windows-home.png" width="420" alt="PairFree on Windows: Home"></p>

## Google Pixel Buds models

PairFree knows 5 Google Pixel Buds models. Models that have been tried on real earbuds: Pixel Buds Pro.

A user's Pixel Buds Pro answered every setting PairFree reads. The other models have not been tried.

If your model is not marked as tried, a device report from you adds it: see [how to help](https://github.com/Omega-Destroyer/PairFree#help-add-your-model).

## What works on each Google Pixel Buds model

<div style="overflow-x:auto">
5 models, by the device type they report. What each has comes from Google's Pixel Buds app; the earbuds answer only for the settings they have, so a row lists the most a model can show.

<b>Tested</b> (1): tried with PairFree. <b>Not tested</b>: from Google's app alone. A device report from any model helps (<a href="https://github.com/Omega-Destroyer/PairFree/blob/main/README.md#help-add-your-model">Help add your model</a>).

<table>
<tr><th>Model</th><th>Device type</th><th>Connection</th><th>Noise control</th><th>Also</th><th>Status</th></tr>
<tr><td>Pixel Buds</td><td><code>1</code></td><td>plain frames</td><td>—</td><td>bass boost, Adaptive Sound, attention alerts, in-ear detection, sharing detection, touch controls on or off</td><td>Not tested</td></tr>
<tr><td>Pixel Buds A-Series</td><td><code>2</code></td><td>plain frames</td><td>—</td><td>bass boost and its level, loudness compensation, Adaptive Sound, in-ear detection, touch controls on or off</td><td>Not tested</td></tr>
<tr><td><a href="https://github.com/Omega-Destroyer/PairFree/blob/main/data/pixel/reports/allegro_release_5.9.txt">Pixel Buds Pro</a></td><td><code>4, 5</code></td><td>Maestro</td><td>ANC, transparency</td><td>5-band EQ and Google&#x27;s presets, balance, mono, volume EQ, conversation detection, press and hold per earbud, the modes it cycles, in-ear detection, multipoint, case sounds</td><td>Tested</td></tr>
<tr><td>Pixel Buds Pro 2</td><td><code>6</code></td><td>Maestro</td><td>ANC, adaptive, transparency</td><td>5-band EQ and Google&#x27;s presets, balance, mono, volume EQ, conversation detection, press and hold per earbud, the modes it cycles, in-ear detection, multipoint, case sounds</td><td>Not tested</td></tr>
<tr><td>Pixel Buds 2a</td><td><code>9</code></td><td>Maestro</td><td>ANC, transparency</td><td>5-band EQ and Google&#x27;s presets, balance, mono, volume EQ, conversation detection, press and hold per earbud, the modes it cycles, in-ear detection, multipoint, case sounds</td><td>Not tested</td></tr>
</table>
</div>

The same list is on [GitHub](https://github.com/Omega-Destroyer/PairFree/blob/main/docs/devices/pixel.md).

## Why use PairFree

- **No account and no internet.** The app has no internet permission, so it cannot go online.
- **One app for several brands.** The same screens for every brand, instead of one app per pair of earbuds.
- **Open source.** GPL-3.0; the protocol is documented in the repository with tests, so anyone can check what the app sends to the earbuds.
- **Small and quick:** about 6 MB on Android and 16 MB on Windows, and reproducible: the published APK can be rebuilt from the source.
- **On Windows as well as Android,** where most of these brands have no app at all.

## Get PairFree

On Android, PairFree is on [F-Droid](https://f-droid.org/packages/io.github.omegadestroyer.pairfree/) and on the [GitHub releases page](https://github.com/Omega-Destroyer/PairFree/releases/latest); it needs Android 12 or later. On Windows 10 or 11, download `PairFree-X.Y.Z.exe` from the same releases page and run it.

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
- [QCY](https://omega-destroyer.github.io/PairFree/open-source-qcy-app/)
- [1MORE](https://omega-destroyer.github.io/PairFree/open-source-1more-app/)
- [EarFun](https://omega-destroyer.github.io/PairFree/open-source-earfun-app/)
- [Cambridge Audio](https://omega-destroyer.github.io/PairFree/open-source-cambridge-app/)
- [Sennheiser](https://omega-destroyer.github.io/PairFree/open-source-sennheiser-app/)
- [Jabra](https://omega-destroyer.github.io/PairFree/open-source-jabra-app/)
- [OnePlus and OPPO](https://omega-destroyer.github.io/PairFree/open-source-oneplus-app/)

---

PairFree is an independent project. It is not affiliated with or endorsed by SOUNDPEATS, Anker, Samsung, Sony, Bose, Harman (JBL), Apple, Nothing, Google, QCY, 1MORE, EarFun, Cambridge Audio, Sennheiser, Jabra, OnePlus or OPPO; the names are used to say which earbuds it works with.
