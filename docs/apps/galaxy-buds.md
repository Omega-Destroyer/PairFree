---
layout: page
title: "Open-source Galaxy Buds app (without Galaxy Wearable)"
description: "PairFree is a free, open-source Android app that controls Samsung Galaxy Buds without Galaxy Wearable, and on any Android phone: no account, no internet access."
permalink: /open-source-galaxy-buds-app/
---

PairFree is a free, open-source Android app that controls Samsung Galaxy Buds without Galaxy Wearable, and on any Android phone: no account, no internet access.

PairFree does not need Galaxy Wearable to run, but only one app can hold the control connection to the earbuds at a time, so close Galaxy Wearable first.

## What you can control

Each model shows only the settings it has:

- Battery and noise control (noise cancelling and ambient sound).
- The equalizer: Samsung's presets on every model, and a nine-band custom curve on the Buds3 and later.
- Touch controls: what touch and hold does.
- Ambient sound level, extra loud, voice focus, noise cancelling level, voice detect, the fit test and find my earbuds.

## Samsung Galaxy Buds models

PairFree knows 16 Samsung Galaxy Buds models. No model of this brand has been tried on real earbuds with PairFree yet; support comes from the brand's own app and reports.

Galaxy Buds support is built on GalaxyBudsClient (GPLv3) and Samsung's own plugin for the Buds4 family. It has not been tried on any Galaxy Buds yet: nobody on the project owns a pair.

The [full model list for Samsung Galaxy Buds on GitHub](https://github.com/Omega-Destroyer/PairFree/blob/main/docs/devices/samsung.md) shows what works on each one. If your model is not marked as tried, a device report from you adds it: see [how to help](https://github.com/Omega-Destroyer/PairFree#help-add-your-model).

## Why use PairFree

- **No account and no internet.** The app has no internet permission, so it cannot go online.
- **One app for several brands.** The same screens for every brand, instead of one app per pair of earbuds.
- **Open source.** GPL-3.0; the protocol is documented in the repository with tests, so anyone can check what the app sends to the earbuds.
- **Small and quick,** about 3 MB, and reproducible: the published APK can be rebuilt from the source.

## Get PairFree

PairFree is on [F-Droid](https://f-droid.org/packages/io.github.omegadestroyer.pairfree/) and on the [GitHub releases page](https://github.com/Omega-Destroyer/PairFree/releases/latest). It needs Android 12 or later.

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

---

PairFree is an independent project. It is not affiliated with or endorsed by SOUNDPEATS, Anker, Samsung, Sony, Bose, Harman (JBL), Apple, Nothing, Google, QCY, 1MORE or EarFun; the names are used to say which earbuds it works with.
