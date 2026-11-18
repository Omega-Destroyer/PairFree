---
layout: page
title: "Open-source EarFun app alternative (EarFun Audio)"
description: "PairFree is a free, open-source Android and Windows app that controls EarFun earbuds and headphones without the EarFun Audio app: no account, no internet access."
permalink: /open-source-earfun-app/
---

PairFree is a free, open-source Android and Windows app that controls EarFun earbuds and headphones without the EarFun Audio app: no account, no internet access.

PairFree does not need the EarFun Audio app to run, but only one app can hold the control connection to the earbuds at a time, so close the EarFun Audio app first.

## What you can control

Each model shows only the settings it has:

- Battery for each earbud and the case, and noise control: the kind of noise cancelling and of ambient sound.
- The equalizer: EarFun's presets and a curve of your own.
- What each tap and hold does, game mode, two devices at once, sound quality (LDAC, aptX), and voice prompts.
- Auto power-off, pausing when you take the earbuds out, and finding a lost earbud.

## On Windows too

Almost none of these brands makes a Windows app, so on a PC their earbuds are plain Bluetooth audio. PairFree for Windows is one small portable program (no installer) with the same settings as the Android app, in a window made for a mouse and with a tray icon for the battery and quick switches. It needs Windows 10 (version 1809) or 11, 64-bit, with the earbuds paired in Windows' Bluetooth settings. EarFun is covered.

<p><img src="https://omega-destroyer.github.io/PairFree/screenshots/android-home.png" width="250" alt="PairFree on Android: Home"> <img src="https://omega-destroyer.github.io/PairFree/screenshots/android-sound.png" width="250" alt="PairFree on Android: Sound"> <img src="https://omega-destroyer.github.io/PairFree/screenshots/windows-home.png" width="420" alt="PairFree on Windows: Home"></p>

## EarFun models

PairFree knows 26 EarFun models. No model of this brand has been tried on real earbuds with PairFree yet; support comes from the brand's own app and reports.

EarFun support is read from EarFun's own app and checked against Gadgetbridge's EarFun support. It has not been tried with PairFree yet.

If your model is not marked as tried, a device report from you adds it: see [how to help](https://github.com/Omega-Destroyer/PairFree#help-add-your-model).

## What works on each EarFun model

<div style="overflow-x:auto">
26 models by their Bluetooth name, from EarFun's own app. 8 of them are on chips whose own protocol PairFree does not cover yet; PairFree does not list those among the paired earbuds.

<b>Tested</b>: tried with PairFree. <b>Not tested</b>: from EarFun's app alone. A device report from any model helps (<a href="https://github.com/Omega-Destroyer/PairFree/blob/main/README.md#help-add-your-model">Help add your model</a>).

<table>
<tr><th>Model</th><th>EarFun's code</th><th>Type</th><th>Noise control</th><th>Also</th><th>Status</th></tr>
<tr><td>ATVEL Tune Pro Q7</td><td><code>ATVEL_HP200</code></td><td>Headphones</td><td>—</td><td>—</td><td>Not yet (BES)</td></tr>
<tr><td>ATVEL Tune S5 Pro</td><td><code>ATVEL_TW300</code></td><td>Earbuds</td><td>—</td><td>—</td><td>Not yet (WUQI)</td></tr>
<tr><td>ATVEL Tune S5 ProX</td><td><code>ATVEL_TW201</code></td><td>Earbuds</td><td>ANC, ambient</td><td>game mode, 6-band EQ with presets, touch controls, restart</td><td>Not tested</td></tr>
<tr><td>EarFun Air</td><td><code>TW200S</code></td><td>Earbuds</td><td>—</td><td>—</td><td>Not yet (Airoha)</td></tr>
<tr><td>EarFun Air 2</td><td><code>TW210</code></td><td>Earbuds</td><td>—</td><td>—</td><td>Not yet (WUQI)</td></tr>
<tr><td>EarFun Air 2 NC</td><td><code>TW220</code>, <code>TW220_VDL</code></td><td>Earbuds</td><td>ANC (standard, adaptive, wind), ambient</td><td>game mode, two devices, spatial audio, voice prompt volume, find the earbuds, auto power-off, 10-band EQ with presets, touch controls (can be turned off), sound quality (LDAC), voice prompt language</td><td>Not tested</td></tr>
<tr><td>EarFun Air Life</td><td><code>TW205</code></td><td>Earbuds</td><td>—</td><td>game mode, find the earbuds, auto power-off, 10-band EQ with presets, touch controls</td><td>Not tested</td></tr>
<tr><td>EarFun Air Pro 2</td><td><code>TW300</code></td><td>Earbuds</td><td>—</td><td>—</td><td>Not yet (WUQI)</td></tr>
<tr><td>EarFun Air Pro 3</td><td><code>TW500</code>, <code>TW500_3072</code></td><td>Earbuds</td><td>ANC (standard, wind), ambient</td><td>game mode, two devices, microphone, voice prompt volume, find the earbuds, 10-band EQ with presets, touch controls (can be turned off), sound quality (aptX, aptX Adaptive), restart</td><td>Not tested</td></tr>
<tr><td>EarFun Air Pro 4</td><td><code>TW600</code></td><td>Earbuds</td><td>ANC (standard, balanced, adaptive, adapts to your ears, wind), ambient (full)</td><td>game mode, two devices, microphone, voice prompt volume, find the earbuds, auto power-off, 10-band EQ with presets, touch controls (can be turned off), sound quality (LDAC, aptX, aptX Adaptive, aptX Lossless), restart</td><td>Not tested</td></tr>
<tr><td>EarFun Air Pro 4+</td><td><code>TW700</code>, <code>TW700AI</code></td><td>Earbuds</td><td>ANC (adapts to your ears, adaptive, levels 1 to 4, wind), ambient (full)</td><td>game mode, two devices, pause and answer in the ear, microphone, voice prompt volume, volume limit, find the earbuds, auto power-off, 10-band EQ with presets, touch controls with call gestures (can be turned off), sound quality (LDAC, aptX, aptX Adaptive, aptX Lossless), restart and power off</td><td>Not tested</td></tr>
<tr><td>EarFun Air Pro 4i</td><td><code>TW230</code></td><td>Earbuds</td><td>ANC (standard, balanced, adaptive, wind), ambient (natural)</td><td>game mode, two devices, voice prompt volume, volume limit, find the earbuds, auto power-off, 10-band EQ with presets, touch controls (can be turned off), sound quality (LDAC), power off</td><td>Not tested</td></tr>
<tr><td>EarFun Air Pro 5</td><td><code>TW800</code></td><td>Earbuds</td><td>ANC (adapts to your ears, adaptive, levels 1 to 4, wind), ambient (full)</td><td>game mode, two devices, pause and answer in the ear, microphone, voice prompt volume, volume limit, find the earbuds, auto power-off, 10-band EQ with presets, touch controls with call gestures (can be turned off), sound quality (LDAC, aptX, aptX Adaptive, aptX Lossless), restart and power off</td><td>Not tested</td></tr>
<tr><td>EarFun Air Pro SV</td><td><code>TW306</code></td><td>Earbuds</td><td>—</td><td>—</td><td>Not yet (Airoha (its own commands))</td></tr>
<tr><td>EarFun Air S</td><td><code>TWA01</code>, <code>TW201</code></td><td>Earbuds</td><td>ANC, ambient</td><td>game mode, 6-band EQ with presets, touch controls, restart</td><td>Not tested</td></tr>
<tr><td>EarFun Clip</td><td><code>CW100</code></td><td>Earbuds</td><td>—</td><td>game mode, two devices, voice prompt volume, volume limit, find the earbuds, auto power-off, 10-band EQ with presets, touch controls (can be turned off), sound quality (LDAC), voice prompt language, power off</td><td>Not tested</td></tr>
<tr><td>EarFun Clip 2</td><td><code>CW200</code></td><td>Earbuds</td><td>—</td><td>game mode, two devices, voice prompt volume, volume limit, find the earbuds, auto power-off, 10-band EQ with presets, touch controls with call gestures (can be turned off), sound quality (LDAC), voice prompt language, power off</td><td>Not tested</td></tr>
<tr><td>EarFun Free 1S</td><td><code>TW100</code></td><td>Earbuds</td><td>—</td><td>—</td><td>Not yet (ZhongKe)</td></tr>
<tr><td>EarFun Free 2S</td><td><code>TW101S</code></td><td>Earbuds</td><td>—</td><td>game mode, 6-band EQ with presets, touch controls, restart</td><td>Not tested</td></tr>
<tr><td>EarFun Free Pro 3</td><td><code>TW400</code></td><td>Earbuds</td><td>ANC (standard, wind), ambient (full)</td><td>game mode, two devices, microphone, voice prompt volume, find the earbuds, 10-band EQ with presets, touch controls (can be turned off), sound quality (aptX, aptX Adaptive), restart</td><td>Not tested</td></tr>
<tr><td>EarFun OpenJump</td><td><code>OW100</code>, <code>OW100_NEW</code></td><td>Earbuds</td><td>—</td><td>game mode, two devices, spatial audio, voice prompt volume, volume limit, find the earbuds, auto power-off, 10-band EQ with presets, touch controls (can be turned off), sound quality (LDAC), voice prompt language, power off</td><td>Not tested</td></tr>
<tr><td>EarFun Tune Pro</td><td><code>HP220</code></td><td>Headphones</td><td>ANC (ANC 1, ANC 2, wind), ambient</td><td>game mode, two devices, spatial audio, voice prompt volume, auto power-off, 10-band EQ with presets, button controls, voice prompt language</td><td>Not tested</td></tr>
<tr><td>EarFun Wave Life</td><td><code>HP201</code></td><td>Headphones</td><td>ANC (ANC 2, wind), ambient</td><td>game mode, two devices, voice prompt volume, auto power-off, 10-band EQ with presets, button controls</td><td>Not tested</td></tr>
<tr><td>EarFun Wave Pro</td><td><code>HP200</code>, <code>HP200_BT8931H</code></td><td>Headphones</td><td>—</td><td>—</td><td>Not yet (BES (or BT8931H))</td></tr>
<tr><td>EarFun Wave Pro 2</td><td><code>HP300</code></td><td>Headphones</td><td>ANC (ANC 1, ANC 2, wind), ambient</td><td>game mode, two devices, spatial audio, voice prompt volume, auto power-off, 10-band EQ with presets, button controls, voice prompt language</td><td>Not tested</td></tr>
<tr><td>EarFun Wave Pro X</td><td><code>HP1000</code></td><td>Headphones</td><td>ANC (adapts to your ears, adaptive, levels 1 to 4, wind), ambient (full)</td><td>game mode, two devices, voice prompt volume, volume limit, auto power-off, 10-band EQ with presets, button controls, sound quality (LDAC, aptX, aptX Adaptive, aptX Lossless), restart and power off</td><td>Not tested</td></tr>
</table>
</div>

The same list is on [GitHub](https://github.com/Omega-Destroyer/PairFree/blob/main/docs/devices/earfun.md).

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
- [Cambridge Audio](https://omega-destroyer.github.io/PairFree/open-source-cambridge-app/)
- [Sennheiser](https://omega-destroyer.github.io/PairFree/open-source-sennheiser-app/)
- [Jabra](https://omega-destroyer.github.io/PairFree/open-source-jabra-app/)
- [OnePlus and OPPO](https://omega-destroyer.github.io/PairFree/open-source-oneplus-app/)

---

PairFree is an independent project. It is not affiliated with or endorsed by SOUNDPEATS, Anker, Samsung, Sony, Bose, Harman (JBL), Apple, Nothing, Google, QCY, 1MORE, EarFun, Cambridge Audio, Sennheiser, Jabra, OnePlus or OPPO; the names are used to say which earbuds it works with.
