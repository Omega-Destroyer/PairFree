---
layout: page
title: "Open-source HeyMelody alternative for OnePlus and OPPO earbuds"
description: "PairFree is a free, open-source Android and Windows app that controls OnePlus and OPPO earbuds without HeyMelody: no account, no internet access."
permalink: /open-source-oneplus-app/
---

PairFree is a free, open-source Android and Windows app that controls OnePlus and OPPO earbuds without HeyMelody: no account, no internet access.

PairFree does not need HeyMelody to run, but only one app can hold the control connection to the earbuds at a time, so close HeyMelody first.

## What you can control

Each model shows only the settings it has:

- Battery for each earbud and the case.
- Noise control: off, noise cancelling and transparency, on the models that have them.
- The equalizer preset each model offers, and game mode.

## On Windows too

Almost none of these brands makes a Windows app, so on a PC their earbuds are plain Bluetooth audio. PairFree for Windows is one small portable program (no installer) with the same settings as the Android app, in a window made for a mouse and with a tray icon for the battery and quick switches. It needs Windows 10 (version 1809) or 11, 64-bit, with the earbuds paired in Windows' Bluetooth settings. OnePlus and OPPO is covered.

<p><img src="https://omega-destroyer.github.io/PairFree/screenshots/android-home.png" width="250" alt="PairFree on Android: Home"> <img src="https://omega-destroyer.github.io/PairFree/screenshots/android-sound.png" width="250" alt="PairFree on Android: Sound"> <img src="https://omega-destroyer.github.io/PairFree/screenshots/windows-home.png" width="420" alt="PairFree on Windows: Home"></p>

## OnePlus and OPPO models

PairFree knows 53 OnePlus and OPPO models. Models that have been tried on real earbuds: OnePlus Buds 4.

OnePlus and OPPO support is read from HeyMelody (117.6.6), which controls both brands, and was checked against the official app and against PairFree run on virtual earbuds. It has not been tried on real earbuds yet; more settings are not built yet.

If your model is not marked as tried, a device report from you adds it: see [how to help](https://github.com/Omega-Destroyer/PairFree#help-add-your-model).

## What works on each OnePlus and OPPO model

<div style="overflow-x:auto">
<table>
<tr><th>Model</th><th>Product id</th><th>Implemented</th><th>Status</th></tr>
<tr><td>OPPO Enco M31</td><td><code>050410</code></td><td>battery</td><td>Not tested</td></tr>
<tr><td>OnePlus BulletsWireless Z2 ANC</td><td><code>050C14</code></td><td>battery; noise control: off, noise cancelling, transparency; 4 equalizer presets</td><td>Not tested</td></tr>
<tr><td>OnePlus Bullets Wireless Z3</td><td><code>051014</code></td><td>battery; 4 equalizer presets</td><td>Not tested</td></tr>
<tr><td>OPPO Enco Free</td><td><code>060410</code></td><td>battery</td><td>Not tested</td></tr>
<tr><td>OnePlus Buds</td><td><code>060414</code></td><td>battery</td><td>Not tested</td></tr>
<tr><td>OPPO Enco W31</td><td><code>060810</code></td><td>battery</td><td>Not tested</td></tr>
<tr><td>OnePlus Buds Z</td><td><code>060814</code></td><td>battery</td><td>Not tested</td></tr>
<tr><td>OPPO Enco W51</td><td><code>060C10</code></td><td>battery</td><td>Not tested</td></tr>
<tr><td>OnePlus Buds Pro</td><td><code>060C14</code></td><td>battery; noise control: off, transparency; 4 equalizer presets</td><td>Not tested</td></tr>
<tr><td>OnePlus Buds Z2</td><td><code>061014</code></td><td>battery; noise control: off, transparency; 4 equalizer presets</td><td>Not tested</td></tr>
<tr><td>OPPO Enco X</td><td><code>061410</code></td><td>battery; noise control: off, transparency; 3 equalizer presets</td><td>Not tested</td></tr>
<tr><td>OnePlus Nord Buds</td><td><code>061414</code></td><td>battery; 4 equalizer presets</td><td>Not tested</td></tr>
<tr><td>OPPO Enco Air</td><td><code>061810</code></td><td>battery</td><td>Not tested</td></tr>
<tr><td>OPPO Enco Play</td><td><code>061C10</code></td><td>battery</td><td>Not tested</td></tr>
<tr><td>OnePlus Nord Buds CE</td><td><code>061C14</code></td><td>battery; 4 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OPPO Enco Free2</td><td><code>062010</code></td><td>battery; noise control: off, noise cancelling, transparency; 4 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OnePlus Buds Pro 2</td><td><code>062014</code></td><td>battery; noise control: off, noise cancelling, transparency; 4 equalizer presets; game mode</td><td><a href="https://github.com/Omega-Destroyer/PairFree/blob/main/data/oneplus/emulator_vectors.json">Vendor app and PairFree tested virtually</a>; no hardware test</td></tr>
<tr><td>OPPO Enco Buds</td><td><code>062410</code></td><td>battery; game mode</td><td>Not tested</td></tr>
<tr><td>OnePlus Nord Buds 2</td><td><code>062414</code></td><td>battery; noise control: off, noise cancelling, transparency; 4 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OPPO Enco Air Lite</td><td><code>062810</code></td><td>battery; game mode</td><td>Not tested</td></tr>
<tr><td>OnePlus Nord Buds 2r</td><td><code>062C14</code></td><td>battery; 3 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OPPO Enco R</td><td><code>063010</code></td><td>battery; 4 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OPPO Enco Air2</td><td><code>063410</code></td><td>battery; 3 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OnePlus Buds Pro 2R</td><td><code>063414</code></td><td>battery; noise control: off, noise cancelling, transparency; 4 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OPPO Enco Air2 Pro</td><td><code>063810</code></td><td>battery; noise control: off, noise cancelling, transparency; 3 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OPPO Enco X2</td><td><code>063C10</code></td><td>battery; noise control: off, noise cancelling, transparency; 3 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OnePlus Buds 3</td><td><code>063C14</code></td><td>battery; noise control: off, noise cancelling, transparency; 3 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OPPO Enco Free2i</td><td><code>064010</code></td><td>battery; noise control: off, noise cancelling, transparency; 4 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OnePlus Buds Pro 3</td><td><code>064014</code></td><td>battery; noise control: off, noise cancelling, transparency; 5 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OnePlus Nord Buds 3 Pro</td><td><code>064414</code></td><td>battery; noise control: off, noise cancelling, transparency; 3 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OPPO Enco Buds2</td><td><code>064810</code></td><td>battery; 3 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OPPO Enco Air3</td><td><code>064C10</code></td><td>battery; 3 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OPPO Enco R Pro</td><td><code>065010</code></td><td>battery; noise control: off, noise cancelling, transparency; 3 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OnePlus Nord Buds 3</td><td><code>065014</code></td><td>battery; noise control: off, noise cancelling, transparency; 3 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OnePlus Buds 4</td><td><code>065414</code></td><td>battery; noise control: off, noise cancelling, transparency; 3 equalizer presets; game mode</td><td><a href="https://github.com/Omega-Destroyer/PairFree/blob/main/data/oneplus/reports/OnePlusBuds4_065414_0.11.1.txt">Tested</a> by a user report</td></tr>
<tr><td>OnePlus Nord Buds 3r</td><td><code>065854</code></td><td>battery; 3 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OPPO Enco Air3 Pro</td><td><code>065C10</code></td><td>battery; noise control: off, noise cancelling, transparency; 3 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OPPO Enco X3i</td><td><code>066810</code></td><td>battery; noise control: off, noise cancelling, transparency; 3 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OPPO Enco Air3i</td><td><code>067010</code></td><td>battery; 3 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OPPO Enco X3</td><td><code>067410</code></td><td>battery; noise control: off, noise cancelling, transparency; 5 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OPPO Enco Air3s</td><td><code>067810</code></td><td>battery; 3 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OPPO Enco Air4 Pro</td><td><code>067C10</code></td><td>battery; noise control: off, noise cancelling, transparency; 3 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OPPO Enco Buds2 Pro</td><td><code>068010</code></td><td>battery; 3 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OnePlus Buds</td><td><code>068414</code></td><td>battery</td><td>Not tested</td></tr>
<tr><td>OnePlus Buds Z</td><td><code>068814</code></td><td>battery</td><td>Not tested</td></tr>
<tr><td>OPPO Enco Air4</td><td><code>069010</code></td><td>battery; noise control: off, noise cancelling, transparency; 3 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OPPO Enco Buds3</td><td><code>06A450</code></td><td>battery; 3 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OPPO Enco Buds3 Pro</td><td><code>06A850</code></td><td>battery; 3 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OPPO Enco Clip</td><td><code>06B810</code></td><td>battery; 3 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OPPO Enco X3s</td><td><code>06BC10</code></td><td>battery; noise control: off, noise cancelling, transparency; 4 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OPPO Enco Air5s</td><td><code>06C810</code></td><td>battery; noise control: off, noise cancelling, transparency; 3 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OPPO Enco Air5</td><td><code>06D810</code></td><td>battery; noise control: off, noise cancelling, transparency; 3 equalizer presets; game mode</td><td>Not tested</td></tr>
<tr><td>OPPO Enco Clip2</td><td><code>06E010</code></td><td>battery; 4 equalizer presets; game mode</td><td>Not tested</td></tr>
</table>
</div>

The same list is on [GitHub](https://github.com/Omega-Destroyer/PairFree/blob/main/docs/devices/oneplus.md).

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
- [Google Pixel Buds](https://omega-destroyer.github.io/PairFree/open-source-pixel-buds-app/)
- [QCY](https://omega-destroyer.github.io/PairFree/open-source-qcy-app/)
- [1MORE](https://omega-destroyer.github.io/PairFree/open-source-1more-app/)
- [EarFun](https://omega-destroyer.github.io/PairFree/open-source-earfun-app/)
- [Cambridge Audio](https://omega-destroyer.github.io/PairFree/open-source-cambridge-app/)
- [Sennheiser](https://omega-destroyer.github.io/PairFree/open-source-sennheiser-app/)
- [Jabra](https://omega-destroyer.github.io/PairFree/open-source-jabra-app/)

---

PairFree is an independent project. It is not affiliated with or endorsed by SOUNDPEATS, Anker, Samsung, Sony, Bose, Harman (JBL), Apple, Nothing, Google, QCY, 1MORE, EarFun, Cambridge Audio, Sennheiser, Jabra, OnePlus or OPPO; the names are used to say which earbuds it works with.
