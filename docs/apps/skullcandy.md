---
layout: page
title: "Open-source Skullcandy app alternative"
description: "PairFree is a free, open-source Android and Windows app that controls Skullcandy earbuds and headphones without the Skullcandy app: no account, no internet access."
permalink: /open-source-skullcandy-app/
---

PairFree is a free, open-source Android and Windows app that controls Skullcandy earbuds and headphones without the Skullcandy app: no account, no internet access.

PairFree does not need the Skullcandy app to run, but only one app can hold the control connection to the earbuds at a time, so close the Skullcandy app first.

## What you can control

Each model shows only the settings it has:

- Battery for each earbud and the case, and noise control (off, noise cancelling, transparency) with its strength where the model has it.
- The equalizer: the earbuds' own presets and your own curve, on the models that have one, shown as the earbuds hold it.
- Gaming mode, multipoint, wear detection and the touch and button controls, where the model has them.
- The Crusher 1080 ANC's Auracast receiver, and a link to Tile on the models that carry Tile.

## On Windows too

Almost none of these brands makes a Windows app, so on a PC their earbuds are plain Bluetooth audio. PairFree for Windows is one small portable program (no installer) with the same settings as the Android app, in a window made for a mouse and with a tray icon for the battery and quick switches. It needs Windows 10 (version 1809) or 11, 64-bit, with the earbuds paired in Windows' Bluetooth settings. Skullcandy is covered.

<p><img src="https://omega-destroyer.github.io/PairFree/screenshots/android-home.png" width="250" alt="PairFree on Android: Home"> <img src="https://omega-destroyer.github.io/PairFree/screenshots/android-sound.png" width="250" alt="PairFree on Android: Sound"> <img src="https://omega-destroyer.github.io/PairFree/screenshots/windows-home.png" width="420" alt="PairFree on Windows: Home"></p>

## Skullcandy models

PairFree knows 56 Skullcandy models. No model of this brand has been tried on real earbuds with PairFree yet; support comes from the brand's own app and reports.

Skullcandy support is read from the Skullcandy app 5.0.6 and its SDK. It has not been tried with PairFree on any product yet.

If your model is not marked as tried, a device report from you adds it: see [how to help](https://github.com/Omega-Destroyer/PairFree#help-add-your-model).

## What works on each Skullcandy model

<div style="overflow-x:auto">
<table>
<tr><th>Model</th><th>Kind</th><th>Product id</th><th>Shown</th><th>Status</th></tr>
<tr><td>Crusher ANC</td><td>Headphones</td><td>0x01</td><td>battery (both earbuds), Find with Tile (link)</td><td>Not tested</td></tr>
<tr><td>Crusher Evo</td><td>Headphones</td><td>0x02</td><td>battery (both earbuds), equalizer (3 presets), Find with Tile (link)</td><td>Not tested</td></tr>
<tr><td>Crusher Evo</td><td>Headphones</td><td>0x30</td><td>battery (both earbuds and the case), multipoint, equalizer (custom + 3 presets), personal sound on or off and intensity</td><td>Not tested</td></tr>
<tr><td>Indy ANC</td><td>Earbuds</td><td>0x07</td><td>battery (both earbuds and the case), noise mode, ambient mode (as transparency), Find with Tile (link)</td><td>Not tested</td></tr>
<tr><td>Indy Fuel</td><td>Earbuds</td><td>0x04</td><td>battery (both earbuds), ambient mode, equalizer (3 presets), Find with Tile (link)</td><td>Not tested</td></tr>
<tr><td>Indy Evo</td><td>Earbuds</td><td>0x03</td><td>battery (both earbuds), ambient mode, equalizer (3 presets), Find with Tile (link)</td><td>Not tested</td></tr>
<tr><td>Push Ultra</td><td>Earbuds</td><td>0x05</td><td>battery (both earbuds), equalizer (3 presets), Find with Tile (link)</td><td>Not tested</td></tr>
<tr><td>Push Active</td><td>Earbuds</td><td>0x08</td><td>battery, noise mode, transparency level, equalizer (custom + 3 presets), Find with Tile (link)</td><td>Not tested</td></tr>
<tr><td>Grind</td><td>Earbuds</td><td>0x09</td><td>battery, noise mode, transparency level, equalizer (custom + 3 presets), Find with Tile (link)</td><td>Not tested</td></tr>
<tr><td>Grind Fuel</td><td>Earbuds</td><td>0x63</td><td>battery, noise mode, transparency level, equalizer (custom + 3 presets), Find with Tile (link)</td><td>Not tested</td></tr>
<tr><td>Crusher ANC 2</td><td>Headphones</td><td>0x16</td><td>battery, noise mode, strength (RACE), multipoint (RACE), equalizer (custom + 3 presets), Find with Tile (link)</td><td>Not tested</td></tr>
<tr><td>Rail</td><td>Earbuds</td><td>0x21</td><td>battery (both earbuds and the case), transparency + strength, multipoint, equalizer (custom + 3 presets), Find with Tile (link)</td><td>Not tested</td></tr>
<tr><td>Rail ANC</td><td>Earbuds</td><td>0x15</td><td>battery (both earbuds and the case), noise mode + strength, multipoint, equalizer (custom + 3 presets), Find with Tile (link)</td><td>Not tested</td></tr>
<tr><td>Method 360 ANC</td><td>Earbuds</td><td>0x1D</td><td>battery, noise mode + strength + profile, gaming mode, voice prompts, prompt language, multipoint (RACE), button actions, equalizer (RACE: custom + 3 presets)</td><td>Not tested</td></tr>
<tr><td>Method 540 ANC</td><td>Earbuds</td><td>0x2D</td><td>battery, noise mode + strength + profile, gaming mode, voice prompts, prompt language, multipoint (RACE), button actions, equalizer (RACE: custom + 3 presets)</td><td>Not tested</td></tr>
<tr><td>Jib True XT2</td><td>Earbuds</td><td>0x0D</td><td>battery (both earbuds and the case), Find with Tile (link)</td><td>Not tested</td></tr>
<tr><td>Indy Evo</td><td>Earbuds</td><td>0x0A</td><td>battery (both earbuds and the case), noise mode, Find with Tile (link)</td><td>Not tested</td></tr>
<tr><td>Indy Fuel</td><td>Earbuds</td><td>0x0B</td><td>battery (both earbuds and the case), noise mode, Find with Tile (link)</td><td>Not tested</td></tr>
<tr><td>Dime 2</td><td>Earbuds</td><td>0x0F</td><td>battery (both earbuds and the case), Find with Tile (link)</td><td>Not tested</td></tr>
<tr><td>Dime XT2</td><td>Earbuds</td><td>0x19</td><td>battery (both earbuds and the case), personal sound on or off and intensity, Find with Tile (link)</td><td>Not tested</td></tr>
<tr><td>Sesh ANC</td><td>Earbuds</td><td>0x0C</td><td>battery (both earbuds and the case), noise mode, noise cancelling and transparency strength, gaming mode, equalizer (custom + 3 presets), Find with Tile (link)</td><td>Not tested</td></tr>
<tr><td>Mod</td><td>Earbuds</td><td>0x12</td><td>battery (both earbuds and the case), noise mode, multipoint, equalizer (custom + 3 presets), button actions (Airoha&#x27;s gesture API), personal sound on or off and intensity, Find with Tile (link)</td><td>Not tested</td></tr>
<tr><td>Riff Wireless 2</td><td>Headphones</td><td>0x14</td><td>battery (both earbuds and the case), gaming mode, multipoint, equalizer (custom + 3 presets), Find with Tile (link)</td><td>Not tested</td></tr>
<tr><td>PLYR</td><td>Headphones</td><td>0x11</td><td>battery (both earbuds and the case), equalizer (custom + 7 presets), sidetone (state + level), game and chat mix, Find with Tile (link)</td><td>Not tested</td></tr>
<tr><td>SLYR Pro Wireless</td><td>Headphones</td><td>0x23</td><td>battery (both earbuds and the case), equalizer (custom + 7 presets), voice prompts, sidetone (state + level), game and chat mix, microphone volume</td><td>Not tested</td></tr>
<tr><td>SLYR Pro Wireless</td><td>Headphones</td><td>0x24</td><td>battery (both earbuds and the case), equalizer (custom + 7 presets), voice prompts, sidetone (state + level), game and chat mix, microphone volume</td><td>Not tested</td></tr>
<tr><td>Push ANC Active</td><td>Earbuds</td><td>0x26</td><td>battery (both earbuds and the case), noise mode, noise cancelling and transparency strength, gaming mode, multipoint, equalizer (custom + 3 presets), button actions (Airoha&#x27;s gesture API), personal sound on or off and intensity</td><td>Not tested</td></tr>
<tr><td>Sesh ANC Active</td><td>Earbuds</td><td>0x25</td><td>battery (both earbuds and the case), noise mode, noise cancelling and transparency strength, gaming mode, multipoint, equalizer (custom + 3 presets), button actions (Airoha&#x27;s gesture API), personal sound on or off and intensity</td><td>Not tested</td></tr>
<tr><td>Icon ANC</td><td>Headphones</td><td>0x1A</td><td>battery (both earbuds and the case), noise mode, noise cancelling and transparency strength, gaming mode, multipoint, equalizer (custom + 3 presets), button actions (the app&#x27;s own key map), personal sound on or off and intensity</td><td>Not tested</td></tr>
<tr><td>Dime Evo</td><td>Earbuds</td><td>0x1B</td><td>battery (both earbuds and the case), noise mode, noise cancelling and transparency strength, gaming mode, multipoint, equalizer (custom + 3 presets), button actions (Airoha&#x27;s gesture API)</td><td>Not tested</td></tr>
<tr><td>AVIATOR 900 ANC</td><td>Headphones</td><td>0x06</td><td>battery (both earbuds and the case), noise mode, noise cancelling and transparency strength, gaming mode, multipoint, wear detection, equalizer (custom + 4 presets), button actions (the app&#x27;s own key map), sidetone (state + level), adaptive noise cancelling, personal sound on or off and intensity, THX spatial audio + intensity + head tracking</td><td>Not tested</td></tr>
<tr><td>Hesh 360</td><td>Headphones</td><td>0x1E</td><td>battery (both earbuds and the case), noise mode, noise cancelling and transparency strength, gaming mode, multipoint, equalizer (custom + 3 presets)</td><td>Not tested</td></tr>
<tr><td>Hesh 540 ANC</td><td>Headphones</td><td>0x1F</td><td>battery (both earbuds and the case), noise mode, noise cancelling and transparency strength, gaming mode, multipoint, wear detection, equalizer (custom + 3 presets), button actions (the app&#x27;s own key map), sidetone (state + level), personal sound on or off and intensity, THX spatial audio + intensity</td><td>Not tested</td></tr>
<tr><td>Crusher PLYR 720</td><td>Headphones</td><td>0x29</td><td>battery (both earbuds and the case), multipoint, bass boost, sidetone (state + level), game and chat mix, microphone volume, Crusher intensity, LED mode, brightness and colour, THX spatial audio + intensity + head tracking</td><td>Not tested</td></tr>
<tr><td>Crusher PLYR 720 XBOX</td><td>Headphones</td><td>0x2A</td><td>battery (both earbuds and the case), multipoint, bass boost, sidetone (state + level), game and chat mix, microphone volume, Crusher intensity, LED mode, brightness and colour, THX spatial audio + intensity + head tracking</td><td>Not tested</td></tr>
<tr><td>Crusher 540 Active</td><td>Headphones</td><td>0x2C</td><td>battery (both earbuds), equalizer (3 presets), Find with Tile (link)</td><td>Not tested</td></tr>
<tr><td>Crusher 540 Active</td><td>Headphones</td><td>0x31</td><td>battery (both earbuds and the case), multipoint, equalizer (custom + 3 presets), personal sound on or off and intensity</td><td>Not tested</td></tr>
<tr><td>Push 720 Open</td><td>Earbuds</td><td>0x2F</td><td>battery (both earbuds and the case), gaming mode, multipoint, equalizer (custom + 3 presets), button actions (Airoha&#x27;s gesture API), personal sound on or off and intensity</td><td>Not tested</td></tr>
<tr><td>Icon 180</td><td>Headphones</td><td>0x2E</td><td>battery, equalizer (custom + 3 presets), multipoint, gaming mode</td><td>Not tested</td></tr>
<tr><td>Push 540 Open</td><td>Earbuds</td><td>0x32</td><td>battery (both earbuds and the case), gaming mode, multipoint, equalizer (custom + 3 presets), button actions (Airoha&#x27;s gesture API), touch controls</td><td>Not tested</td></tr>
<tr><td>Terrain Mini</td><td>Speaker</td><td>0x736</td><td></td><td>Found only (speakers are held back)</td></tr>
<tr><td>Terrain</td><td>Speaker</td><td>0x737</td><td></td><td>Found only (speakers are held back)</td></tr>
<tr><td>Terrain XL</td><td>Speaker</td><td>0x738</td><td></td><td>Found only (speakers are held back)</td></tr>
<tr><td>Dime 360</td><td>Earbuds</td><td>0x33</td><td>battery (both earbuds and the case), noise mode, noise cancelling and transparency strength, gaming mode, multipoint, equalizer (custom + 3 presets), button actions (Airoha&#x27;s gesture API)</td><td>Not tested</td></tr>
<tr><td>Crusher 720</td><td>Headphones</td><td>0x36</td><td>battery (both earbuds and the case), noise mode, noise cancelling and transparency strength, gaming mode, multipoint, wear detection, equalizer (custom + 3 presets), button actions (the app&#x27;s own key map), sidetone (state + level), personal sound on or off and intensity, THX spatial audio + intensity</td><td>Not tested</td></tr>
<tr><td>Crusher 1080 ANC</td><td>Headphones</td><td>0x34</td><td>battery, noise mode + strength + profile, sidetone, gaming mode, wear detection (RPC), Crusher intensity and mode, spatial audio, voice prompts, prompt language, multipoint (RACE), button actions, equalizer (the earbuds&#x27; own: presets and custom), Auracast receiver (RACE)</td><td>Not tested</td></tr>
<tr><td>Session 360</td><td>Speaker</td><td>0x37</td><td></td><td>Found only (speakers are held back)</td></tr>
<tr><td>Session 540</td><td>Speaker</td><td>0x38</td><td></td><td>Found only (speakers are held back)</td></tr>
<tr><td>Session 720</td><td>Speaker</td><td>0x39</td><td></td><td>Found only (speakers are held back)</td></tr>
<tr><td>Session 900</td><td>Speaker</td><td>0x3A</td><td></td><td>Found only (speakers are held back)</td></tr>
<tr><td>Smokin&#x27; Buds 360</td><td>Earbuds</td><td>0x35</td><td></td><td>Found only: no settings yet</td></tr>
<tr><td>DIME 540 ANC</td><td>Earbuds</td><td>0x45</td><td>battery, noise mode + level, equalizer (custom + 3 presets), multipoint, gaming mode, touch controls, button actions</td><td>Not tested</td></tr>
<tr><td>Smokin&#x27; Buds 360H</td><td>Earbuds</td><td>0x42</td><td>battery, noise mode, equalizer (custom + 3 presets), multipoint, sidetone, button actions</td><td>Not tested</td></tr>
<tr><td>Smokin&#x27; Buds 360S</td><td>Earbuds</td><td>0x41</td><td>battery, noise mode, equalizer (custom + 3 presets), multipoint, sidetone, button actions</td><td>Not tested</td></tr>
<tr><td>Crusher 900 ANC</td><td>Headphones</td><td>0x44</td><td>battery (both earbuds and the case), noise mode, noise cancelling and transparency strength, gaming mode, multipoint, wear detection, equalizer (custom), button actions (Airoha&#x27;s gesture API), sidetone (state + level), personal sound on or off and intensity, THX spatial audio + intensity</td><td>Not tested</td></tr>
<tr><td>Push 180 Open</td><td>Earbuds</td><td>0x43</td><td>battery, equalizer (custom)</td><td>Not tested</td></tr>
</table>
</div>

The same list is on [GitHub](https://github.com/Omega-Destroyer/PairFree/blob/main/docs/devices/skullcandy.md).

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
- [Sennheiser](https://omega-destroyer.github.io/PairFree/open-source-sennheiser-app/)
- [Jabra](https://omega-destroyer.github.io/PairFree/open-source-jabra-app/)
- [Huawei and Honor](https://omega-destroyer.github.io/PairFree/open-source-huawei-app/)
- [OnePlus and OPPO](https://omega-destroyer.github.io/PairFree/open-source-oneplus-app/)

---

PairFree is an independent project. It is not affiliated with or endorsed by SOUNDPEATS, Anker, Samsung, Sony, Bose, Harman (JBL), Apple, Nothing, Google, QCY, 1MORE, EarFun, Cambridge Audio, Sennheiser, Jabra, Skullcandy, Huawei, Honor, OnePlus or OPPO; the names are used to say which earbuds it works with.
