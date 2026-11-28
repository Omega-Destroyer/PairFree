---
layout: page
title: "Open-source Sennheiser Smart Control alternative"
description: "PairFree is a free, open-source Android and Windows app that controls Sennheiser's Momentum, Accentum and CX earbuds and headphones that speak GAIA v3 without Smart Control: no account, no internet access."
permalink: /open-source-sennheiser-app/
---

PairFree is a free, open-source Android and Windows app that controls Sennheiser's Momentum, Accentum and CX earbuds and headphones that speak GAIA v3 without Smart Control: no account, no internet access.

PairFree does not need Sennheiser Smart Control to run, but only one app can hold the control connection to the earbuds at a time, so close Sennheiser Smart Control first.

## What you can control

Each model shows only the settings it has:

- Battery for each earbud and the case.
- Noise control (off, noise cancelling, transparency), anti-wind and adaptive noise cancelling.
- Answering calls automatically, comfort call, on-head detection, smart pause, low latency mode, and locking the touch controls.

## On Windows too

Almost none of these brands makes a Windows app, so on a PC their earbuds are plain Bluetooth audio. PairFree for Windows is one small portable program (no installer) with the same settings as the Android app, in a window made for a mouse and with a tray icon for the battery and quick switches. It needs Windows 10 (version 1809) or 11, 64-bit, with the earbuds paired in Windows' Bluetooth settings. Sennheiser is covered.

<p><img src="https://omega-destroyer.github.io/PairFree/screenshots/android-home.png" width="250" alt="PairFree on Android: Home"> <img src="https://omega-destroyer.github.io/PairFree/screenshots/android-sound.png" width="250" alt="PairFree on Android: Sound"> <img src="https://omega-destroyer.github.io/PairFree/screenshots/windows-home.png" width="420" alt="PairFree on Windows: Home"></p>

## Sennheiser models

PairFree knows 28 Sennheiser models. Models that have been tried on real earbuds: Momentum 5 Wireless.

Sennheiser support is read from Sennheiser Smart Control 4.9.4 and Smart Control Plus 1.6.3. It has not been tried with PairFree on any product yet. The older models on other protocols (Momentum 3, PXC 550, HD 450 and others) are not covered.

If your model is not marked as tried, a device report from you adds it: see [how to help](https://github.com/Omega-Destroyer/PairFree#help-add-your-model).

## What works on each Sennheiser model

<div style="overflow-x:auto">
<table>
<tr><th>Model</th><th>Bluetooth names</th><th>Shown</th><th>Status</th></tr>
<tr><td>Accentum</td><td>AC1, AC1 BLACK, AC1 WHITE, ACAEBT…</td><td>battery, codec in use, noise cancelling, anti-wind, adaptive noise cancelling, transparency, pause in full transparency, on-head detection, answer calls automatically, comfort call, low latency mode, lock the touch controls, voice prompts, voice prompt language, wear state, sample rate, automatic power off, sidetone, equalizer, podcast mode, paired devices (connect, disconnect)</td><td>Not tested</td></tr>
<tr><td>Accentum Plus</td><td>ACCENTUM Plus, ACP1, ACP1 BLACK, ACP1 WHITE…</td><td>battery, codec in use, noise cancelling, anti-wind, adaptive noise cancelling, transparency, pause in full transparency, on-head detection, smart pause, answer calls automatically, comfort call, low latency mode, lock the touch controls, voice prompts, voice prompt language, wear state, sample rate, automatic power off, sidetone, transparency level, equalizer, personalised sound, podcast mode, paired devices (connect, disconnect)</td><td>Not tested</td></tr>
<tr><td>Accentum True Wireless</td><td>ACCENTUM TW, ATW1, ATW1 Black, ATW1 Blue…</td><td>battery, codec in use, noise cancelling, anti-wind, adaptive noise cancelling, transparency, pause in full transparency, smart pause, answer calls automatically, low latency mode, lock the touch controls, eco mode, battery protection, voice prompts, Bluetooth compatibility mode, voice prompt language, wear state, sample rate, automatic power off, sidetone, transparency level, equalizer, touch and button functions, find device, podcast mode, Auracast, restart, paired devices (connect, disconnect), fit test</td><td>Not tested</td></tr>
<tr><td>CX 200 True Wireless</td><td>CX 200BT TW, CX True Wireless, CX200TW1, CX200TW1 Black…</td><td>battery, codec in use, lock the touch controls, voice prompts, voice prompt language, wear state, automatic power off, sidetone, equalizer, touch and button functions, podcast mode, paired devices (connect, disconnect)</td><td>Not tested. Bluetooth LE only</td></tr>
<tr><td>CX Sport True Wireless</td><td>CX200TW1 S, CX200TW1 S Black, Sport True Wireless</td><td>battery, codec in use, lock the touch controls, voice prompts, voice prompt language, open ear tip support, wear state, automatic power off, sidetone, equalizer, touch and button functions, podcast mode, paired devices (connect, disconnect)</td><td>Not tested. Bluetooth LE only</td></tr>
<tr><td>CX Plus True Wireless</td><td>CX 400BT TW2, CX 400iiBT TW, CX Plus SE True Wireless, CX Plus True Wireless…</td><td>battery, codec in use, noise cancelling, anti-wind, adaptive noise cancelling, transparency, pause in full transparency, smart pause, answer calls automatically, lock the touch controls, voice prompts, voice prompt language, wear state, automatic power off, sidetone, equalizer, touch and button functions, podcast mode, paired devices (connect, disconnect)</td><td>Not tested. Bluetooth LE only</td></tr>
<tr><td>CX 500BT True Wireless</td><td>CX 500BT, CX500BT1, CX500BT1 Grey</td><td>battery, codec in use, noise cancelling, anti-wind, adaptive noise cancelling, transparency, pause in full transparency, smart pause, answer calls automatically, lock the touch controls, voice prompts, voice prompt language, wear state, automatic power off, sidetone, equalizer, touch and button functions, podcast mode, paired devices (connect, disconnect)</td><td>Not tested. Bluetooth LE only</td></tr>
<tr><td>Momentum 4 Wireless</td><td>M4AEBT, M4AEBT Black, M4AEBT Denim, M4AEBT Graphite…</td><td>battery, codec in use, noise cancelling, anti-wind, adaptive noise cancelling, transparency, pause in full transparency, on-head detection, smart pause, answer calls automatically, comfort call, low latency mode, lock the touch controls, voice prompts, Bluetooth compatibility mode, voice prompt language, wear state, automatic power off, sidetone, transparency level, equalizer, personalised sound, podcast mode, paired devices (connect, disconnect)</td><td>Not tested</td></tr>
<tr><td>Momentum Sport</td><td>MOMENTUM SPORT, MOMENTUM SPORT1, MOMENTUM Sport, MSPORT1…</td><td>battery, codec in use, noise cancelling, anti-wind, adaptive noise cancelling, transparency, pause in full transparency, smart pause, answer calls automatically, low latency mode, lock the touch controls, eco mode, battery protection, voice prompts, Bluetooth compatibility mode, voice prompt language, tap sensitivity, wear state, sample rate, touch area, automatic power off, sidetone, transparency level, equalizer, touch and button functions, find device, podcast mode, restart, paired devices (connect, disconnect), fit test, heart rate and body temperature</td><td>Not tested</td></tr>
<tr><td>Momentum True Wireless 3</td><td>MOMENTUM TW 3, MTW3, MTW3 Black, MTW3 Graphite…</td><td>battery, codec in use, noise cancelling, anti-wind, adaptive noise cancelling, transparency, pause in full transparency, smart pause, answer calls automatically, low latency mode, lock the touch controls, voice prompts, Bluetooth compatibility mode, voice prompt language, wear state, automatic power off, transparency level, equalizer, touch and button functions, personalised sound, podcast mode, paired devices (connect, disconnect), fit test</td><td>Not tested</td></tr>
<tr><td>Momentum True Wireless 4</td><td>MOMENTUM TW 4, MTW4, MTW4 Black, MTW4 Black Copper…</td><td>battery, codec in use, noise cancelling, anti-wind, adaptive noise cancelling, transparency, pause in full transparency, smart pause, answer calls automatically, low latency mode, lock the touch controls, eco mode, battery protection, voice prompts, Bluetooth compatibility mode, voice prompt language, aptX Lossless, wear state, sample rate, automatic power off, sidetone, transparency level, equalizer, touch and button functions, find device, personalised sound, podcast mode, Auracast, restart, paired devices (connect, disconnect), fit test</td><td>Not tested</td></tr>
<tr><td>Momentum 5 Wireless</td><td>M5AEBT, MOMENTUM 5</td><td>everything the product answers (no product file in either app)</td><td>Tried: device report</td></tr>
<tr><td>Momentum True Wireless 5</td><td>MOMENTUM TW 5, MTW5, PHOBOS, Phobos…</td><td>everything the product answers (no product file in either app)</td><td>Not tested</td></tr>
<tr><td>HDB 630</td><td>HDB 630, HDBT</td><td>everything the product answers (no product file in either app)</td><td>Not tested</td></tr>
<tr><td>HDR 275 / 255</td><td>HDR 275, HDR275, HDR275 Black, Venus TVL…</td><td>everything the product answers (no product file in either app)</td><td>Not tested</td></tr>
<tr><td>CX 350BT (GAIA v2)</td><td>CX 350BT</td><td>battery, podcast mode, equalizer (bass, mid, treble)</td><td>Not tested</td></tr>
<tr><td>HD 250BT (GAIA v2)</td><td>HD 250BT</td><td>battery, podcast mode, equalizer (bass, mid, treble)</td><td>Not tested</td></tr>
<tr><td>HD 350BT (GAIA v2)</td><td>HD 350BT</td><td>battery, podcast mode, equalizer (bass, mid, treble), sidetone</td><td>Not tested</td></tr>
<tr><td>HD 450BT (GAIA v2)</td><td>HD 450BT</td><td>battery, podcast mode, equalizer (bass, mid, treble), sidetone</td><td>Not tested</td></tr>
<tr><td>HD 450SE (GAIA v2)</td><td>HD 450SE</td><td>battery, podcast mode, equalizer (bass, mid, treble), sidetone</td><td>Not tested</td></tr>
<tr><td>HD 458BT (GAIA v2)</td><td>HD 458BT</td><td>battery, podcast mode, equalizer (bass, mid, treble), sidetone</td><td>Not tested</td></tr>
<tr><td>IE 80S BT (GAIA v2)</td><td>IE 80S BT</td><td>voice prompts, answer calls automatically, smart pause, codec in use, automatic power off, multi-function button, voice prompt language, equalizer (five bands)</td><td>Not tested</td></tr>
<tr><td>CX 400BT True Wireless (GAIA v2)</td><td>CX TW, CX 400BT TW</td><td>battery, equalizer (bass, mid, treble), sidetone, voice prompts, answer calls automatically, smart pause, voice prompt language, lock the touch controls, transparent hearing, noise cancelling</td><td>Not tested. Bluetooth LE only</td></tr>
<tr><td>Momentum True Wireless (GAIA v2)</td><td>MOMENTUM TW</td><td>battery, equalizer (bass, mid, treble), voice prompts, answer calls automatically, smart pause, voice prompt language, transparent hearing</td><td>Not tested. Bluetooth LE only</td></tr>
<tr><td>Momentum True Wireless 2 (GAIA v2)</td><td>MOMENTUM TW 2</td><td>battery, equalizer (bass, mid, treble), sidetone, voice prompts, answer calls automatically, smart pause, voice prompt language, lock the touch controls, transparent hearing, noise cancelling</td><td>Not tested. Bluetooth LE only</td></tr>
<tr><td>Momentum 3 Wireless (Nautilus)</td><td>MOMENTUM 3</td><td>battery, noise cancelling modes, pause music in transparency, transparent hearing, smart pause, voice prompts, voice prompt language, codec in use, equalizer (bass, mid, treble), Amazon Alexa</td><td>Not tested</td></tr>
<tr><td>PXC 550 (Nautilus)</td><td>PXC 550</td><td>battery, noise cancelling modes, smart pause, voice prompts, voice prompt language, noise cancelling, call enhancement, sound presets, adaptive strength, director settings (boost, reverb, speaker distance, dynamic loudness)</td><td>Not tested</td></tr>
<tr><td>PXC 550-II (Nautilus)</td><td>PXC 550-II</td><td>battery, noise cancelling modes, smart pause, voice prompts, voice prompt language, noise cancelling, call enhancement, middle position of the switch, sound presets, adaptive strength, Amazon Alexa, director settings (boost, reverb, speaker distance, dynamic loudness)</td><td>Not tested</td></tr>
</table>
</div>

The same list is on [GitHub](https://github.com/Omega-Destroyer/PairFree/blob/main/docs/devices/sennheiser.md).

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
- [Cambridge Audio](https://omega-destroyer.github.io/PairFree/open-source-cambridge-app/)
- [Jabra](https://omega-destroyer.github.io/PairFree/open-source-jabra-app/)
- [Skullcandy](https://omega-destroyer.github.io/PairFree/open-source-skullcandy-app/)
- [Huawei and Honor](https://omega-destroyer.github.io/PairFree/open-source-huawei-app/)
- [OnePlus and OPPO](https://omega-destroyer.github.io/PairFree/open-source-oneplus-app/)

---

PairFree is an independent project. It is not affiliated with or endorsed by SOUNDPEATS, Anker, Samsung, Sony, Bose, Harman (JBL), Apple, Nothing, Google, QCY, 1MORE, EarFun, Cambridge Audio, Sennheiser, Jabra, Skullcandy, Huawei, Honor, OnePlus or OPPO; the names are used to say which earbuds it works with.
