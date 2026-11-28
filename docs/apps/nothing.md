---
layout: page
title: "Open-source Nothing X app alternative (Nothing Ear and CMF Buds)"
description: "PairFree is a free, open-source Android and Windows app that controls Nothing and CMF earbuds and headphones without the Nothing X app: no account, no internet access."
permalink: /open-source-nothing-app/
---

PairFree is a free, open-source Android and Windows app that controls Nothing and CMF earbuds and headphones without the Nothing X app: no account, no internet access.

PairFree does not need the Nothing X app to run, but only one app can hold the control connection to the earbuds at a time, so close the Nothing X app first.

## What you can control

Each model shows only the settings it has:

- Battery, noise control (off, noise cancelling and transparency) and the noise cancelling strength.
- The equalizer: presets and a curve of your own; bass boost, spatial audio and head tracking on the headphones.
- What each tap, double tap, triple tap and hold does on each earbud, the case dial and the headphones' controls.
- Low lag mode, in-ear detection, and the codec the earbuds offer.

## On Windows too

Almost none of these brands makes a Windows app, so on a PC their earbuds are plain Bluetooth audio. PairFree for Windows is one small portable program (no installer) with the same settings as the Android app, in a window made for a mouse and with a tray icon for the battery and quick switches. It needs Windows 10 (version 1809) or 11, 64-bit, with the earbuds paired in Windows' Bluetooth settings. Nothing and CMF is covered.

<p><img src="https://omega-destroyer.github.io/PairFree/screenshots/android-home.png" width="250" alt="PairFree on Android: Home"> <img src="https://omega-destroyer.github.io/PairFree/screenshots/android-sound.png" width="250" alt="PairFree on Android: Sound"> <img src="https://omega-destroyer.github.io/PairFree/screenshots/windows-home.png" width="420" alt="PairFree on Windows: Home"></p>

## Nothing and CMF models

PairFree knows 19 Nothing and CMF models. Models that have been tried on real earbuds: Nothing Ear.

Nothing support is read from Nothing X and checked against two open-source projects tried on four Nothing and CMF models. It has not been tried with PairFree yet.

If your model is not marked as tried, a device report from you adds it: see [how to help](https://github.com/Omega-Destroyer/PairFree#help-add-your-model).

## What works on each Nothing and CMF model

<div style="overflow-x:auto">
19 models, by the product ids they report. Noise control and the rest come from the feature table Nothing X bundles, for the newest firmware it lists; a model that reports a product id missing here gets whatever it answers for.

<b>Tested</b> (1): tried with PairFree. <b>nada</b>, <b>nadamas</b> (4): <a href="https://github.com/chukfinley/nada">nada</a> and <a href="https://github.com/ezvk/nadamas">nadamas</a> were used with one, sending the messages PairFree sends. <b>Not tested</b>: from Nothing X alone. A device report from any model helps (<a href="https://github.com/Omega-Destroyer/PairFree/blob/main/README.md#help-add-your-model">Help add your model</a>).

<table>
<tr><th>Model</th><th>Product</th><th>Type</th><th>Noise control</th><th>Also</th><th>Status</th></tr>
<tr><td>Nothing Ear (2)</td><td><code>B155</code></td><td>Earbuds</td><td>ANC (adaptive, high, mid, low), transparency</td><td>EQ presets, custom EQ, LHDC, in-ear detection, low lag, touch controls</td><td>nadamas</td></tr>
<tr><td>Nothing Ear (stick)</td><td><code>B157</code></td><td>Earbuds</td><td>ANC</td><td>EQ presets, custom EQ, in-ear detection, low lag, touch controls</td><td>Not tested</td></tr>
<tr><td>Nothing Ear (a)</td><td><code>B162</code></td><td>Earbuds</td><td>ANC (adaptive, high, mid, low), transparency</td><td>EQ presets, custom EQ, bass boost, LDAC, in-ear detection, low lag, touch controls</td><td>Not tested</td></tr>
<tr><td><a href="https://github.com/Omega-Destroyer/PairFree/blob/main/data/nothing/reports/B171_1.0.1.56.txt">Nothing Ear</a></td><td><code>B171</code></td><td>Earbuds</td><td>ANC (adaptive, high, mid, low), transparency</td><td>EQ presets, custom EQ, bass boost, LHDC and LDAC, in-ear detection, low lag, touch controls</td><td>Tested</td></tr>
<tr><td>Nothing Ear (3)</td><td><code>B173</code></td><td>Earbuds</td><td>ANC (adaptive, high, mid, low), transparency</td><td>EQ presets, custom EQ, bass boost, spatial audio, LDAC, in-ear detection, low lag, touch controls</td><td>Not tested</td></tr>
<tr><td>Nothing Ear (open)</td><td><code>B174</code></td><td>Earbuds</td><td>—</td><td>EQ presets, custom EQ, low lag, touch controls</td><td>Not tested</td></tr>
<tr><td>Nothing Ear (1)</td><td><code>B181</code></td><td>Earbuds</td><td>ANC (high, low), transparency</td><td>EQ presets, in-ear detection, low lag, touch controls</td><td>Not tested</td></tr>
<tr><td>Nothing (project 24272)</td><td><code>B183</code></td><td>Earbuds</td><td>ANC (adaptive, high, mid, low), transparency</td><td>EQ presets, custom EQ, bass boost, LDAC, in-ear detection, low lag, touch controls</td><td>Not tested</td></tr>
<tr><td>Nothing Ear (3a)</td><td><code>B190</code></td><td>Earbuds</td><td>ANC (adaptive, high, mid, low), transparency</td><td>EQ presets, bass boost, spatial audio, LDAC, in-ear detection, low lag</td><td>nadamas</td></tr>
<tr><td>CMF Buds Pro</td><td><code>B163</code></td><td>Earbuds</td><td>ANC (high, mid, low), transparency</td><td>EQ presets, custom EQ, in-ear detection, low lag, touch controls</td><td>Not tested</td></tr>
<tr><td>CMF Buds</td><td><code>B168</code></td><td>Earbuds</td><td>ANC, transparency</td><td>custom EQ, bass boost, in-ear detection, low lag, touch controls</td><td>Not tested</td></tr>
<tr><td>CMF Buds Pro 2</td><td><code>B172</code></td><td>Earbuds</td><td>ANC (adaptive, high, mid, low), transparency</td><td>custom EQ, bass boost, spatial audio, LDAC, in-ear detection, low lag, touch controls</td><td>Not tested</td></tr>
<tr><td>CMF Buds 2</td><td><code>B179</code></td><td>Earbuds</td><td>ANC (adaptive, high, mid, low), transparency</td><td>EQ presets, custom EQ, bass boost, spatial audio, in-ear detection, low lag, touch controls</td><td>Not tested</td></tr>
<tr><td>CMF Buds 2 Plus</td><td><code>B184</code></td><td>Earbuds</td><td>ANC (adaptive, high, mid, low), transparency</td><td>EQ presets, custom EQ, bass boost, spatial audio, LDAC, in-ear detection, low lag, touch controls</td><td>nada</td></tr>
<tr><td>CMF Buds 2a</td><td><code>B185</code></td><td>Earbuds</td><td>ANC, transparency</td><td>custom EQ, bass boost, low lag, touch controls</td><td>Not tested</td></tr>
<tr><td>CMF Buds Pro 2</td><td><code>B187</code></td><td>Earbuds</td><td>ANC (adaptive, high, mid, low), transparency</td><td>custom EQ, bass boost, spatial audio, LDAC, in-ear detection, low lag, touch controls</td><td>nadamas</td></tr>
<tr><td>Nothing Headphone (1)</td><td><code>B170</code></td><td>Headphones</td><td>ANC (adaptive, high, mid, low), transparency</td><td>EQ presets, custom EQ, bass boost, spatial audio, LDAC, in-ear detection, low lag, touch controls</td><td>Not tested</td></tr>
<tr><td>CMF Headphone Pro</td><td><code>B175</code></td><td>Headphones</td><td>ANC (adaptive, high, mid, low), transparency</td><td>EQ presets, custom EQ, spatial audio, LDAC, low lag, touch controls</td><td>Not tested</td></tr>
<tr><td>CMF Neckband Pro</td><td><code>B164</code></td><td>Neckband</td><td>ANC (adaptive, high, mid, low), transparency</td><td>EQ presets, custom EQ, bass boost, spatial audio, low lag, touch controls</td><td>Not tested</td></tr>
</table>
</div>

The same list is on [GitHub](https://github.com/Omega-Destroyer/PairFree/blob/main/docs/devices/nothing.md).

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
