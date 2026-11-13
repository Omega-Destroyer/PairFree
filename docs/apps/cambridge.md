---
layout: page
title: "Open-source Cambridge Audio Melomania app alternative"
description: "PairFree is a free, open-source Android app that controls Cambridge Audio's Melomania 1+ and Melomania Touch earbuds without the Melomania app: no account, no internet access."
permalink: /open-source-cambridge-app/
---

PairFree is a free, open-source Android app that controls Cambridge Audio's Melomania 1+ and Melomania Touch earbuds without the Melomania app: no account, no internet access.

PairFree does not need the Melomania app to run, but only one app can hold the control connection to the earbuds at a time, so close the Melomania app first.

## What you can control

Each model shows only the settings it has:

- Battery for each earbud, and on the Touch ambient sound and its level.
- The equalizer: six presets and five gain bands from -6 to +6 dB.
- Which touch functions are on, the voice guidance language, and the codec the earbuds offer (SBC, AAC, aptX).

## Cambridge Audio models

PairFree knows 8 Cambridge Audio models. No model of this brand has been tried on real earbuds with PairFree yet; support comes from the brand's own app and reports.

Cambridge Audio support is read from the Melomania app (2.8.12) and its commands were checked against the official app run on a virtual earbud. It has not been tried with PairFree on real earbuds yet. The newer Melomania Connect models (A100, M100, P100) are not covered.

The [full model list for Cambridge Audio on GitHub](https://github.com/Omega-Destroyer/PairFree/blob/main/docs/devices/cambridge.md) shows what works on each one. If your model is not marked as tried, a device report from you adds it: see [how to help](https://github.com/Omega-Destroyer/PairFree#help-add-your-model).

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
- [Samsung Galaxy Buds](https://omega-destroyer.github.io/PairFree/open-source-galaxy-buds-app/)
- [AirPods and Beats](https://omega-destroyer.github.io/PairFree/open-source-airpods-beats-app/)
- [Nothing and CMF](https://omega-destroyer.github.io/PairFree/open-source-nothing-app/)
- [Google Pixel Buds](https://omega-destroyer.github.io/PairFree/open-source-pixel-buds-app/)
- [QCY](https://omega-destroyer.github.io/PairFree/open-source-qcy-app/)
- [1MORE](https://omega-destroyer.github.io/PairFree/open-source-1more-app/)
- [EarFun](https://omega-destroyer.github.io/PairFree/open-source-earfun-app/)
- [OnePlus and OPPO](https://omega-destroyer.github.io/PairFree/open-source-oneplus-app/)

---

PairFree is an independent project. It is not affiliated with or endorsed by SOUNDPEATS, Anker, Samsung, Sony, Bose, Harman (JBL), Apple, Nothing, Google, QCY, 1MORE, EarFun, Cambridge Audio, OnePlus or OPPO; the names are used to say which earbuds it works with.
