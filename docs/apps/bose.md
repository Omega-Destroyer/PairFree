---
layout: page
title: "Open-source Bose app (a Bose Music alternative)"
description: "PairFree is a free, open-source Android and Windows app that controls Bose headphones and earbuds without the Bose Music app. It never signs in to Bose and never contacts Bose's servers."
permalink: /open-source-bose-app/
---

PairFree is a free, open-source Android and Windows app that controls Bose headphones and earbuds without the Bose Music app. It never signs in to Bose and never contacts Bose's servers.

PairFree does not need the Bose Music app to run, but only one app can hold the control connection to the earbuds at a time, so close the Bose Music app first.

## What you can control

Each model shows only the settings it has:

- Battery for each earbud and the case, or for the headphones.
- Listening modes (Quiet, Aware and the modes you set up in Bose's app) and the noise cancelling level.
- Tone control (bass, mid and treble) and immersive audio.
- What the shortcut button does, the auto-off timer, two devices at once, and pause when taken off.

## On Windows too

Almost none of these brands makes a Windows app, so on a PC their earbuds are plain Bluetooth audio. PairFree for Windows is one small portable program (no installer) with the same settings as the Android app, in a window made for a mouse and with a tray icon for the battery and quick switches. It needs Windows 10 (version 1809) or 11, 64-bit, with the earbuds paired in Windows' Bluetooth settings. Bose is covered.

<p><img src="https://omega-destroyer.github.io/PairFree/screenshots/android-home.png" width="250" alt="PairFree on Android: Home"> <img src="https://omega-destroyer.github.io/PairFree/screenshots/android-sound.png" width="250" alt="PairFree on Android: Sound"> <img src="https://omega-destroyer.github.io/PairFree/screenshots/windows-home.png" width="420" alt="PairFree on Windows: Home"></p>

## Bose models

PairFree knows 22 Bose models. No model of this brand has been tried on real earbuds with PairFree yet; support comes from the brand's own app and reports.

Bose support is built from Bose's own app and checked against an open-source project that was used with seven Bose models. It has not been tried on Bose headphones with PairFree yet.

If your model is not marked as tried, a device report from you adds it: see [how to help](https://github.com/Omega-Destroyer/PairFree#help-add-your-model).

## What works on each Bose model

<div style="overflow-x:auto">
22 models, by the names and product ids Bose's own app gives them. Each device reports its product id when PairFree connects and answers only for the settings it has, so a Bose model missing here works the same way.

<b>Tested</b> (0): tried with PairFree. <b>bosectl</b> (8): <a href="https://github.com/aaronsb/bosectl">bosectl</a> (commit <code>70570b2</code>) was tried on it, with the messages PairFree sends; its notes do not say which QC35 it was, so both are marked. <b>Not tested</b>: from Bose's app alone. A device report from any model helps (<a href="https://github.com/Omega-Destroyer/PairFree/blob/main/README.md#help-add-your-model">Help add your model</a>).

<table>
<tr><th>Model</th><th>Product</th><th>Type</th><th>Status</th></tr>
<tr><td>QuietComfort 35</td><td><code>400C</code></td><td>Headphones</td><td>bosectl</td></tr>
<tr><td>QuietComfort 35 Series 2</td><td><code>4020</code></td><td>Headphones</td><td>bosectl</td></tr>
<tr><td>NC 700 Headphones</td><td><code>4024</code></td><td>Headphones</td><td>Not tested</td></tr>
<tr><td>QC 45</td><td><code>4039</code></td><td>Headphones</td><td>bosectl</td></tr>
<tr><td>QuietComfort Headphones</td><td><code>4075</code></td><td>Headphones</td><td>bosectl</td></tr>
<tr><td>QC Ultra Headphones</td><td><code>4066</code></td><td>Headphones</td><td>Not tested</td></tr>
<tr><td>QC Ultra 2 HP</td><td><code>4082</code></td><td>Headphones</td><td>bosectl</td></tr>
<tr><td>QC 2 HP</td><td><code>4083</code></td><td>Headphones</td><td>Not tested</td></tr>
<tr><td>SoundSport Free</td><td><code>4018</code></td><td>Earbuds</td><td>Not tested</td></tr>
<tr><td>Sport Earbuds</td><td><code>402D</code></td><td>Earbuds</td><td>Not tested</td></tr>
<tr><td>QuietComfort Earbuds</td><td><code>402F</code></td><td>Earbuds</td><td>bosectl</td></tr>
<tr><td>Sport Open Earbuds</td><td><code>403A</code></td><td>Earbuds</td><td>Not tested</td></tr>
<tr><td>QC Earbuds II</td><td><code>4064</code></td><td>Earbuds</td><td>Not tested</td></tr>
<tr><td>Ultra Open Earbuds</td><td><code>4068</code></td><td>Earbuds</td><td>bosectl</td></tr>
<tr><td>QC Ultra Earbuds</td><td><code>4072</code></td><td>Earbuds</td><td>Not tested</td></tr>
<tr><td>QC Ultra 2 Earbuds</td><td><code>4062</code></td><td>Earbuds</td><td>bosectl</td></tr>
<tr><td>Ultra Open 2 Earbuds</td><td><code>4089</code></td><td>Earbuds</td><td>Not tested</td></tr>
<tr><td>Sport Open Earbuds</td><td><code>4097</code></td><td>Earbuds</td><td>Not tested</td></tr>
<tr><td>QuietComfort Earbuds</td><td><code>0023</code></td><td>Earbuds</td><td>Not tested</td></tr>
<tr><td>SoundSport</td><td><code>4012</code></td><td>Neckband</td><td>Not tested</td></tr>
<tr><td>SoundSport Pulse</td><td><code>4013</code></td><td>Neckband</td><td>Not tested</td></tr>
<tr><td>QuietControl 30</td><td><code>4014</code></td><td>Neckband</td><td>Not tested</td></tr>
</table>
</div>

The same list is on [GitHub](https://github.com/Omega-Destroyer/PairFree/blob/main/docs/devices/bose.md).

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
- [OnePlus and OPPO](https://omega-destroyer.github.io/PairFree/open-source-oneplus-app/)

---

PairFree is an independent project. It is not affiliated with or endorsed by SOUNDPEATS, Anker, Samsung, Sony, Bose, Harman (JBL), Apple, Nothing, Google, QCY, 1MORE, EarFun, Cambridge Audio, Sennheiser, Jabra, OnePlus or OPPO; the names are used to say which earbuds it works with.
