---
layout: page
title: "Open-source AirPods app for Android (noise control and settings)"
description: "PairFree is a free, open-source Android and Windows app for the settings of AirPods and Beats on Apple's chips, built from the LibrePods project's work: no account, no internet access."
permalink: /open-source-airpods-beats-app/
---

PairFree is a free, open-source Android and Windows app for the settings of AirPods and Beats on Apple's chips, built from the LibrePods project's work: no account, no internet access.

## What you can control

Each model shows only the settings it has:

- Battery for each AirPod and the case.
- Listening modes: off, noise cancelling, transparency and adaptive, and Conversation Awareness.
- Press speed, press and hold duration, swipe to change volume, automatic ear detection and Personalized Volume, where the AirPods report them.

## On Windows too

Almost none of these brands makes a Windows app, so on a PC their earbuds are plain Bluetooth audio. PairFree for Windows is one small portable program (no installer) with the same settings as the Android app, in a window made for a mouse and with a tray icon for the battery and quick switches. It needs Windows 10 (version 1809) or 11, 64-bit, with the earbuds paired in Windows' Bluetooth settings. AirPods and the Beats on Apple's chips are not on Windows: Windows gives programs no way to open their settings channel.

<p><img src="https://omega-destroyer.github.io/PairFree/screenshots/android-home.png" width="250" alt="PairFree on Android: Home"> <img src="https://omega-destroyer.github.io/PairFree/screenshots/android-sound.png" width="250" alt="PairFree on Android: Sound"> <img src="https://omega-destroyer.github.io/PairFree/screenshots/windows-home.png" width="420" alt="PairFree on Windows: Home"></p>

## AirPods and Beats models

PairFree knows 30 AirPods and Beats models. Models that have been tried on real earbuds: AirPods Pro 3.

AirPods keep their settings on a Bluetooth channel that Android refused on most phones until Android 17. PairFree does not try it on a phone that is not known to open it, and it has not worked on a real pair yet: see the AirPods page for which phones.

If your model is not marked as tried, a device report from you adds it: see [how to help](https://github.com/Omega-Destroyer/PairFree#help-add-your-model).

## What works on each AirPods and Beats model

<div style="overflow-x:auto">
30 models: the AirPods LibrePods knows, with the model numbers they report, and the Beats and AirPods Max MagicPodsCore lists. Each one reports its settings when PairFree connects, and PairFree shows only those.

<b>Tested</b> (1): tried with PairFree. <b>Not tested</b>: from LibrePods and MagicPodsCore. A device report from any model helps (<a href="https://github.com/Omega-Destroyer/PairFree/blob/main/README.md#help-add-your-model">Help add your model</a>).

<table>
<tr><th>Model</th><th>Model numbers</th><th>Type</th><th>Adaptive Audio</th><th>Status</th></tr>
<tr><td>AirPods (1st generation)</td><td><code>A1523</code> <code>A1722</code></td><td>Earbuds</td><td>—</td><td>Not tested</td></tr>
<tr><td>AirPods (2nd generation)</td><td><code>A2032</code> <code>A2031</code></td><td>Earbuds</td><td>—</td><td>Not tested</td></tr>
<tr><td>AirPods (3rd generation)</td><td><code>A2565</code> <code>A2564</code></td><td>Earbuds</td><td>—</td><td>Not tested</td></tr>
<tr><td>AirPods 4</td><td><code>A3053</code> <code>A3050</code> <code>A3054</code></td><td>Earbuds</td><td>—</td><td>Not tested</td></tr>
<tr><td>AirPods 4 with Active Noise Cancellation</td><td><code>A3056</code> <code>A3055</code> <code>A3057</code></td><td>Earbuds</td><td>Yes</td><td>Not tested</td></tr>
<tr><td>AirPods 5</td><td><code>A3531</code> <code>A3532</code> <code>A3533</code></td><td>Earbuds</td><td>Yes</td><td>Not tested</td></tr>
<tr><td>AirPods 5 with Wireless Charging Case</td><td><code>A3439</code> <code>A3440</code> <code>A3441</code></td><td>Earbuds</td><td>Yes</td><td>Not tested</td></tr>
<tr><td>AirPods Pro</td><td><code>A2084</code> <code>A2083</code></td><td>Earbuds</td><td>—</td><td>Not tested</td></tr>
<tr><td>AirPods Pro 2 (Lightning)</td><td><code>A2931</code> <code>A2699</code> <code>A2698</code></td><td>Earbuds</td><td>Yes</td><td>Not tested</td></tr>
<tr><td>AirPods Pro 2 (USB-C)</td><td><code>A3047</code> <code>A3048</code> <code>A3049</code></td><td>Earbuds</td><td>Yes</td><td>Not tested</td></tr>
<tr><td><a href="https://github.com/Omega-Destroyer/PairFree/blob/main/data/apple/reports/AirPodsPro3_A3064_90.3431.txt">AirPods Pro 3</a></td><td><code>A3063</code> <code>A3064</code> <code>A3065</code></td><td>Earbuds</td><td>Yes</td><td>Tested</td></tr>
<tr><td>AirPods Max</td><td>—</td><td>Headphones</td><td>—</td><td>Not tested</td></tr>
<tr><td>AirPods Max (USB-C)</td><td>—</td><td>Headphones</td><td>—</td><td>Not tested</td></tr>
<tr><td>AirPods Max 2</td><td>—</td><td>Headphones</td><td>Yes</td><td>Not tested</td></tr>
<tr><td>Powerbeats3</td><td>—</td><td>Earbuds</td><td>—</td><td>Not tested</td></tr>
<tr><td>Powerbeats4</td><td>—</td><td>Earbuds</td><td>—</td><td>Not tested</td></tr>
<tr><td>Powerbeats Pro</td><td>—</td><td>Earbuds</td><td>—</td><td>Not tested</td></tr>
<tr><td>Powerbeats Pro 2</td><td>—</td><td>Earbuds</td><td>—</td><td>Not tested</td></tr>
<tr><td>Powerbeats Fit</td><td>—</td><td>Earbuds</td><td>—</td><td>Not tested</td></tr>
<tr><td>BeatsX</td><td>—</td><td>Earbuds</td><td>—</td><td>Not tested</td></tr>
<tr><td>Beats Flex</td><td>—</td><td>Earbuds</td><td>—</td><td>Not tested</td></tr>
<tr><td>Beats Fit Pro</td><td>—</td><td>Earbuds</td><td>—</td><td>Not tested</td></tr>
<tr><td>Beats Studio Buds</td><td>—</td><td>Earbuds</td><td>—</td><td>In doubt: MagicPodsCore lists it, but Beats&#x27; Android app speaks its own protocol to it</td></tr>
<tr><td>Beats Studio Buds +</td><td>—</td><td>Earbuds</td><td>—</td><td>In doubt: MagicPodsCore lists it, but Beats&#x27; Android app speaks its own protocol to it</td></tr>
<tr><td>Beats Solo Buds</td><td>—</td><td>Earbuds</td><td>—</td><td>In doubt: MagicPodsCore lists it, but Beats&#x27; Android app speaks its own protocol to it</td></tr>
<tr><td>Beats Solo3</td><td>—</td><td>Headphones</td><td>—</td><td>Not tested</td></tr>
<tr><td>Beats Solo Pro</td><td>—</td><td>Headphones</td><td>—</td><td>Not tested</td></tr>
<tr><td>Beats Solo 4</td><td>—</td><td>Headphones</td><td>—</td><td>Not tested</td></tr>
<tr><td>Beats Studio3</td><td>—</td><td>Headphones</td><td>—</td><td>Not tested</td></tr>
<tr><td>Beats Studio Pro</td><td>—</td><td>Headphones</td><td>—</td><td>Not tested</td></tr>
</table>

<b>Why some of these need a particular Android version.</b> AirPods and AirPods Max take their settings over a classic Bluetooth L2CAP channel (PSM 0x1001). Android has no public call for a classic L2CAP channel (its public L2CAP calls are for Bluetooth LE, and the newest one accepts channel numbers 128 to 255 only), so an app can reach it only through <code>BluetoothSocket</code>'s hidden constructors, and only where the phone's Bluetooth stack accepts the channel. Until Android 17 the stack refused it except on Pixels with Android 16 QPR3 and on OnePlus, OPPO and realme phones with Android 16. Google fixed the stack in Android 17, but Android 17 builds can also hide those constructors from apps. Where they are hidden, PairFree asks Android's runtime, for its own process and for that one class only, to show them, as LibrePods does (no root, no Xposed); it does so only for AirPods and only after the constructors came back hidden, and the connection report says whether it was tried. This is a workaround for a restriction, not a public API: a user's AirPods Pro 3 on a Pixel 8 with Android 17 connected through it (issue #3, PairFree 0.9.1), and a later Android version may close it. Apple's own Beats app on Android does not use that channel: it talks to the Beats on Apple's chips over an ordinary Bluetooth serial connection (RFCOMM), which any Android version allows. PairFree now speaks that protocol (B2P) to the Beats that Apple's app controls that way (Powerbeats Pro and Pro 2, Powerbeats Fit, Fit Pro, Studio Buds and Buds +, Solo Buds, Solo Pro, Solo 4, Studio Pro): they need no particular Android version. It shows the battery, the listening mode, one-bud noise cancelling, ear detection, the microphone and the in-case tones, for the models Apple's app lists each for, written from Apple's app and not yet tried on any Beats. The older Beats (Powerbeats3, Solo3, BeatsX, Flex, Studio3) use another protocol, BCD, over the same serial channel: PairFree shows their noise cancelling switch (and the Flex's auto answer), also untried; the Beats Pill+ speaker is not covered. See docs/apple/B2P.md.
</div>

The same list is on [GitHub](https://github.com/Omega-Destroyer/PairFree/blob/main/docs/devices/apple.md).

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
