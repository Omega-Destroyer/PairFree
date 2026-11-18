---
layout: page
title: "Open-source Galaxy Buds app (without Galaxy Wearable)"
description: "PairFree is a free, open-source Android and Windows app that controls Samsung Galaxy Buds without Galaxy Wearable, and on any Android phone: no account, no internet access."
permalink: /open-source-galaxy-buds-app/
---

PairFree is a free, open-source Android and Windows app that controls Samsung Galaxy Buds without Galaxy Wearable, and on any Android phone: no account, no internet access.

PairFree does not need Galaxy Wearable to run, but only one app can hold the control connection to the earbuds at a time, so close Galaxy Wearable first.

## What you can control

Each model shows only the settings it has:

- Battery and noise control (noise cancelling and ambient sound).
- The equalizer: Samsung's presets on every model, and a nine-band custom curve on the Buds3 and later.
- Touch controls: what touch and hold does.
- Ambient sound level, extra loud, voice focus, noise cancelling level, voice detect, the fit test and find my earbuds.

## On Windows too

Almost none of these brands makes a Windows app, so on a PC their earbuds are plain Bluetooth audio. PairFree for Windows is one small portable program (no installer) with the same settings as the Android app, in a window made for a mouse and with a tray icon for the battery and quick switches. It needs Windows 10 (version 1809) or 11, 64-bit, with the earbuds paired in Windows' Bluetooth settings. Samsung Galaxy Buds is covered.

<p><img src="https://omega-destroyer.github.io/PairFree/screenshots/android-home.png" width="250" alt="PairFree on Android: Home"> <img src="https://omega-destroyer.github.io/PairFree/screenshots/android-sound.png" width="250" alt="PairFree on Android: Sound"> <img src="https://omega-destroyer.github.io/PairFree/screenshots/windows-home.png" width="420" alt="PairFree on Windows: Home"></p>

## Samsung Galaxy Buds models

PairFree knows 16 Samsung Galaxy Buds models. No model of this brand has been tried on real earbuds with PairFree yet; support comes from the brand's own app and reports.

Galaxy Buds support is built on GalaxyBudsClient (GPLv3) and Samsung's own plugin for the Buds4 family. It has not been tried on any Galaxy Buds yet: nobody on the project owns a pair.

If your model is not marked as tried, a device report from you adds it: see [how to help](https://github.com/Omega-Destroyer/PairFree#help-add-your-model).

## What works on each Samsung Galaxy Buds model

<div style="overflow-x:auto">
16 models. What each model has, and where each setting sits in its state, comes from <a href="https://github.com/timschneeb/GalaxyBudsClient">GalaxyBudsClient</a> (commit <code>754b6fd</code>, 2026-10-01), the open-source Galaxy Buds app by Tim Schneeberger. For the Buds4 family and three models GalaxyBudsClient does not have, it comes from Samsung's own Galaxy Buds plugin (budsunitemgr 9.0.10), which corrects GalaxyBudsClient where they differ.

<b>Tested</b> (0): tried on the earbuds with PairFree. <b>Checked</b> (7): PairFree reads the state replies from the model that GalaxyBudsClient's tests hold. <b>Samsung's app</b>: read from Samsung's own plugin, with no reply from real earbuds yet. <b>Not tested</b>: read from GalaxyBudsClient's code alone. A device report from any model helps (<a href="https://github.com/Omega-Destroyer/PairFree/blob/main/README.md#help-add-your-model">Help add your model</a>).

✅ the model has it. — it does not, as far as GalaxyBudsClient knows. <i>rev. N+</i>: only with firmware whose state has revision N or later; the device report shows the revision. PairFree shows every column but the last two on the models that have them. <i>Hold switches between</i> says <i>Not yet</i> where PairFree shows the touch-and-hold action but not which modes it switches between.

<table>
<tr><th>Model</th><th>Number</th><th>Status</th><th>Battery</th><th>Noise control</th><th>Equalizer</th><th>Touch controls</th><th>Hold switches between</th><th>Fit test</th><th>Ambient per earbud</th><th>Voice detect</th><th>Balance</th><th>Seamless connection</th><th>Game mode</th><th>Spatial audio</th></tr>
<tr><td>Buds</td><td>SM-R170</td><td>Checked</td><td>Each earbud</td><td>Ambient</td><td>Presets</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅ rev. 3+</td><td>—</td><td>—</td></tr>
<tr><td>Buds+</td><td>SM-R175</td><td>Checked</td><td>Each earbud, case</td><td>Ambient</td><td>Presets</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅ rev. 11+</td><td>✅</td><td>—</td></tr>
<tr><td>Buds Live</td><td>SM-R180</td><td>Checked</td><td>Each earbud, case</td><td>ANC</td><td>Presets</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅ rev. 7+</td><td>✅ rev. 3+</td><td>✅</td><td>✅ rev. 9+</td></tr>
<tr><td>Buds Pro</td><td>SM-R190</td><td>Checked</td><td>Each earbud, case</td><td>ANC, ambient</td><td>Presets</td><td>✅</td><td>✅</td><td>—</td><td>✅ rev. 8+</td><td>✅</td><td>✅ rev. 5+</td><td>✅</td><td>✅</td><td>✅</td></tr>
<tr><td>Buds2</td><td>SM-R177</td><td>Checked</td><td>Each earbud, case</td><td>ANC, ambient</td><td>Presets</td><td>✅</td><td>✅</td><td>✅</td><td>✅ rev. 5+</td><td>—</td><td>✅</td><td>✅</td><td>✅</td><td>✅ rev. 10+</td></tr>
<tr><td>Buds2 Pro</td><td>SM-R510</td><td>Checked</td><td>Each earbud, case</td><td>ANC, ambient</td><td>Presets</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td></tr>
<tr><td>Buds FE</td><td>SM-R400N</td><td>Checked</td><td>Each earbud, case</td><td>ANC, ambient</td><td>Presets</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>✅</td><td>—</td></tr>
<tr><td>Buds Core</td><td>SM-R410</td><td>Not tested</td><td>Each earbud, case</td><td>ANC, ambient</td><td>Presets</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>✅</td><td>—</td></tr>
<tr><td>Buds3</td><td>SM-R530</td><td>Not tested</td><td>Each earbud, case</td><td>ANC, ambient</td><td>Presets, custom</td><td>✅</td><td>Not yet</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td></tr>
<tr><td>Buds3 Pro</td><td>SM-R630</td><td>Not tested</td><td>Each earbud, case</td><td>ANC, ambient</td><td>Presets, custom</td><td>✅</td><td>Not yet</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td></tr>
<tr><td>Buds3 FE</td><td>SM-R420</td><td>Not tested</td><td>Each earbud, case</td><td>ANC, ambient</td><td>Presets, custom</td><td>✅</td><td>Not yet</td><td>✅</td><td>✅</td><td>—</td><td>✅</td><td>✅</td><td>✅</td><td>—</td></tr>
<tr><td>Buds4</td><td>SM-R540</td><td>Samsung's app</td><td>Each earbud, case</td><td>ANC, ambient</td><td>Presets, custom</td><td>✅</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>✅</td><td>—</td><td>—</td></tr>
<tr><td>Buds4 Pro</td><td>SM-R640</td><td>Samsung's app</td><td>Each earbud, case</td><td>ANC, ambient</td><td>Presets, custom</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>—</td><td>—</td></tr>
<tr><td>Buds On</td><td>SM-U600</td><td>Samsung's app</td><td>Each earbud</td><td>—</td><td>Presets</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>
<tr><td>H1</td><td>SM-U700</td><td>Samsung's app</td><td>One</td><td>ANC, ambient</td><td>Presets, custom</td><td>✅</td><td>—</td><td>—</td><td>—</td><td>—</td><td>✅</td><td>✅</td><td>—</td><td>—</td></tr>
<tr><td>T1</td><td>SM-R545</td><td>Samsung's app</td><td>Each earbud, case</td><td>ANC, ambient</td><td>Presets, custom</td><td>✅</td><td>—</td><td>✅</td><td>—</td><td>—</td><td>✅</td><td>✅</td><td>—</td><td>—</td></tr>
</table>
</div>

The same list is on [GitHub](https://github.com/Omega-Destroyer/PairFree/blob/main/docs/devices/samsung.md).

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
- [AirPods and Beats](https://omega-destroyer.github.io/PairFree/open-source-airpods-beats-app/)
- [Nothing and CMF](https://omega-destroyer.github.io/PairFree/open-source-nothing-app/)
- [Google Pixel Buds](https://omega-destroyer.github.io/PairFree/open-source-pixel-buds-app/)
- [QCY](https://omega-destroyer.github.io/PairFree/open-source-qcy-app/)
- [1MORE](https://omega-destroyer.github.io/PairFree/open-source-1more-app/)
- [EarFun](https://omega-destroyer.github.io/PairFree/open-source-earfun-app/)
- [Cambridge Audio](https://omega-destroyer.github.io/PairFree/open-source-cambridge-app/)
- [Sennheiser](https://omega-destroyer.github.io/PairFree/open-source-sennheiser-app/)
- [Jabra](https://omega-destroyer.github.io/PairFree/open-source-jabra-app/)
- [OnePlus and OPPO](https://omega-destroyer.github.io/PairFree/open-source-oneplus-app/)

---

PairFree is an independent project. It is not affiliated with or endorsed by SOUNDPEATS, Anker, Samsung, Sony, Bose, Harman (JBL), Apple, Nothing, Google, QCY, 1MORE, EarFun, Cambridge Audio, Sennheiser, Jabra, OnePlus or OPPO; the names are used to say which earbuds it works with.
