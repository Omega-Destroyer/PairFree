"""Writes PairFree's web site, the GitHub Pages site built from docs/ (docs/_config.yml): a home page and one
landing page per brand, so that searches such as "open source Soundcore app" find a real page with a title and a
description, not a file in the repository.

usage: python tools/gen_site.py          (rewrites docs/index.md, docs/apps/*.md and the README's list of the brand pages)

The text for each brand is written here from what the brand's page under docs/devices/ says. Model counts and which
models have been tried come from the README's generated brand table, so they follow the device pages. A test
(tools/test_site.py) fails while the pages are out of date.
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SITE = "https://omega-destroyer.github.io/PairFree"
REPO = "https://github.com/Omega-Destroyer/PairFree"

# slug: the page's address is /open-source-<slug>-app/. name: what people call it. app: the official app it stands in for.
BRANDS = [
    dict(
        slug="soundpeats", page="soundpeats", name="SOUNDPEATS", app="PeatsAudio (the SOUNDPEATS app)",
        title="Open-source SOUNDPEATS app (a PeatsAudio alternative)",
        lead="PairFree is a free, open-source Android app for SOUNDPEATS earbuds. It does what PeatsAudio, the official app, does, without an account or internet access.",
        does=[
            "Battery for each earbud and the case.",
            "Noise control: off, noise cancelling and transparency, with their scenes and levels where the model has them.",
            "The equalizer: the model's own presets and your own saved curves, and a hearing test that builds a curve for you.",
            "Game mode, LDAC and LE Audio, two devices at once, the ear tip fit test, find my earbuds and water drain.",
            "Touch controls and the settings only some models have, such as spatial audio, Dolby and dynamic bass.",
        ],
        notes="SOUNDPEATS earbuds are the project's origin: the owner's H3 and Air5 Pro+ are tested in every feature, and users' reports cover the C30 and the TRUEFREE Clip C10.",
    ),
    dict(
        slug="soundcore", page="soundcore", name="Soundcore", app="the Soundcore app",
        title="Open-source Soundcore app (for Anker Soundcore earbuds)",
        lead="PairFree is a free, open-source Android app that controls Soundcore (Anker) earbuds and headphones without the Soundcore app: no account, no internet access.",
        does=[
            "Battery for each earbud and the case, and noise control (sound modes).",
            "The equalizer: Soundcore's presets and your own curves, on every model that has an equalizer.",
            "Touch controls and touch tone, game mode, LDAC and two devices at once, where the model has them.",
            "The Sleep A20's sleep timer.",
        ],
        notes="Soundcore support is built on OpenSCQ30, the open-source Soundcore app by Kyle Scheuing (GPL-3.0-or-later), and checked against recordings from real earbuds.",
    ),
    dict(
        slug="bose", page="bose", name="Bose", app="the Bose Music app",
        title="Open-source Bose app (a Bose Music alternative)",
        lead="PairFree is a free, open-source Android app that controls Bose headphones and earbuds without the Bose Music app. It never signs in to Bose and never contacts Bose's servers.",
        does=[
            "Battery for each earbud and the case, or for the headphones.",
            "Listening modes (Quiet, Aware and the modes you set up in Bose's app) and the noise cancelling level.",
            "Tone control (bass, mid and treble) and immersive audio.",
            "What the shortcut button does, the auto-off timer, two devices at once, and pause when taken off.",
        ],
        notes="Bose support is built from Bose's own app and checked against an open-source project that was used with seven Bose models. It has not been tried on Bose headphones with PairFree yet.",
    ),
    dict(
        slug="jbl", page="jbl", name="JBL", app="the JBL Headphones app",
        title="Open-source JBL Headphones app alternative",
        lead="PairFree is a free, open-source Android app that controls JBL earbuds and headphones without the JBL Headphones app: no account, no internet access.",
        does=[
            "Battery, and noise control: noise cancelling, Ambient Aware and TalkThru, with adaptive noise cancelling where the model has it.",
            "The equalizer for every model, with JBL's presets and a curve of your own on ten bands.",
            "Smart Audio & Video, 3D spatial sound, SafeSound, balance and Hi-Res audio, where the model has them.",
            "Touch controls on many models, including the Live Buds 3 and other Airoha-chip earbuds.",
        ],
        notes="JBL support is read from JBL's own app. A user's Live Buds 3 has been tried with PairFree.",
    ),
    dict(
        slug="sony", page="sony", name="Sony", app="the Sony | Sound Connect app",
        title="Open-source Sony headphones app (a Sound Connect alternative)",
        lead="PairFree is a free, open-source Android app that controls Sony earbuds and headphones without Sony | Sound Connect: no account, no internet access.",
        does=[
            "Battery, and noise control: noise cancelling and ambient sound, with the ambient level, voice focus and speak-to-chat.",
            "The equalizer, with the model's presets and, where it allows one, a curve of your own; Clear Bass and DSEE upscaling.",
            "What each touch sensor or button does, from the choices the model lists.",
            "The connection mode, automatic power-off, pause when taken off, voice guidance and two devices at once.",
        ],
        notes="Sony support is read from Sony's own app and checked against Gadgetbridge's Sony support. Each model says itself what it has when PairFree connects. It has not been tried on Sony earbuds or headphones yet; two ULT Field speakers have been read.",
    ),
    dict(
        slug="galaxy-buds", page="samsung", name="Samsung Galaxy Buds", app="Galaxy Wearable",
        title="Open-source Galaxy Buds app (without Galaxy Wearable)",
        lead="PairFree is a free, open-source Android app that controls Samsung Galaxy Buds without Galaxy Wearable, and on any Android phone: no account, no internet access.",
        does=[
            "Battery and noise control (noise cancelling and ambient sound).",
            "The equalizer: Samsung's presets on every model, and a nine-band custom curve on the Buds3 and later.",
            "Touch controls: what touch and hold does.",
            "Ambient sound level, extra loud, voice focus, noise cancelling level, voice detect, the fit test and find my earbuds.",
        ],
        notes="Galaxy Buds support is built on GalaxyBudsClient (GPLv3) and Samsung's own plugin for the Buds4 family. It has not been tried on any Galaxy Buds yet: nobody on the project owns a pair.",
    ),
    dict(
        slug="airpods-beats", page="apple", name="AirPods and Beats", app="", close=False,
        title="Open-source AirPods app for Android (noise control and settings)",
        lead="PairFree is a free, open-source Android app for the settings of AirPods and Beats on Apple's chips, built from the LibrePods project's work: no account, no internet access.",
        does=[
            "Battery for each AirPod and the case.",
            "Listening modes: off, noise cancelling, transparency and adaptive, and Conversation Awareness.",
            "Press speed, press and hold duration, swipe to change volume, automatic ear detection and Personalized Volume, where the AirPods report them.",
        ],
        notes="AirPods keep their settings on a Bluetooth channel that Android refused on most phones until Android 17. PairFree does not try it on a phone that is not known to open it, and it has not worked on a real pair yet: see the AirPods page for which phones.",
    ),
    dict(
        slug="nothing", page="nothing", name="Nothing and CMF", app="the Nothing X app",
        title="Open-source Nothing X app alternative (Nothing Ear and CMF Buds)",
        lead="PairFree is a free, open-source Android app that controls Nothing and CMF earbuds and headphones without the Nothing X app: no account, no internet access.",
        does=[
            "Battery, noise control (off, noise cancelling and transparency) and the noise cancelling strength.",
            "The equalizer: presets and a curve of your own; bass boost, spatial audio and head tracking on the headphones.",
            "What each tap, double tap, triple tap and hold does on each earbud, the case dial and the headphones' controls.",
            "Low lag mode, in-ear detection, and the codec the earbuds offer.",
        ],
        notes="Nothing support is read from Nothing X and checked against two open-source projects tried on four Nothing and CMF models. It has not been tried with PairFree yet.",
    ),
    dict(
        slug="pixel-buds", page="pixel", name="Google Pixel Buds", app="the Pixel Buds app",
        title="Open-source Pixel Buds app (for Android phones that are not Pixels)",
        lead="PairFree is a free, open-source Android app for Google Pixel Buds: no account, no internet access, and it does not need a Pixel phone.",
        does=[
            "Battery for each earbud and the case.",
            "Noise control on the Pixel Buds Pro, Pro 2 and 2a, and adaptive noise control on the Pro 2.",
            "The equalizer on the Pro, Pro 2 and 2a: Google's presets and a curve of your own over five bands.",
            "Press and hold actions, conversation detection, volume EQ and balance, where the model has them.",
        ],
        notes="A user's Pixel Buds Pro answered every setting PairFree reads. The other models have not been tried.",
    ),
    dict(
        slug="qcy", page="qcy", name="QCY", app="the QCY app",
        title="Open-source QCY app alternative",
        lead="PairFree is a free, open-source Android app that controls QCY earbuds and headphones without the QCY app: no account, no internet access.",
        does=[
            "Battery for each earbud and the case, and noise control with its scenes and strength.",
            "The equalizer, as a curve of your own over the bands the earbuds report.",
            "What each tap, double tap, triple tap and hold does, game mode, in-ear detection, LDAC, two devices at once and the timed power-off, where the model has them.",
            "Both of QCY's protocols: QCY's own, and the one used on JieLi chips.",
        ],
        notes="QCY support is read from QCY's own app. It has not been tried with PairFree yet.",
    ),
    dict(
        slug="1more", page="1more", name="1MORE", app="1MORE's own app",
        title="Open-source 1MORE app alternative",
        lead="PairFree is a free, open-source Android app that controls 1MORE earbuds and headphones without 1MORE's app: no account, no internet access.",
        does=[
            "Battery for each earbud and the case, and noise control: the kind of noise cancelling and of transparency.",
            "The equalizer: the model's preset sounds and, on models with one, a curve of your own; spatial audio.",
            "What a double and a triple tap do, two devices at once, and the connection mode.",
            "The NANO's light, and finding a lost earbud.",
        ],
        notes="1MORE support is read from 1MORE's own app. It has not been tried with PairFree yet.",
    ),
    dict(
        slug="earfun", page="earfun", name="EarFun", app="the EarFun Audio app",
        title="Open-source EarFun app alternative (EarFun Audio)",
        lead="PairFree is a free, open-source Android app that controls EarFun earbuds and headphones without the EarFun Audio app: no account, no internet access.",
        does=[
            "Battery for each earbud and the case, and noise control: the kind of noise cancelling and of ambient sound.",
            "The equalizer: EarFun's presets and a curve of your own.",
            "What each tap and hold does, game mode, two devices at once, sound quality (LDAC, aptX), and voice prompts.",
            "Auto power-off, pausing when you take the earbuds out, and finding a lost earbud.",
        ],
        notes="EarFun support is read from EarFun's own app and checked against Gadgetbridge's EarFun support. It has not been tried with PairFree yet.",
    ),
]

QUESTIONS = [
    (
        "Is there an open-source app for my earbuds?",
        "Yes, for the brands above. PairFree is one open-source app (GPL-3.0) that talks directly to the earbuds over Bluetooth, "
        "so one app can replace the Soundcore, Bose Music, JBL Headphones, Sony Sound Connect, Galaxy Wearable, Nothing X, PeatsAudio, "
        "QCY, 1MORE and EarFun apps.",
    ),
    (
        "Does it work without an account or internet?",
        "Yes. The app does not have the internet permission, so it cannot go online, and there is nothing to sign up for. "
        "Nothing leaves your phone unless you share a device report or an equalizer profile yourself.",
    ),
    (
        "Does it work with every model of a brand?",
        "It lists every model its sources describe, and each model shows only the settings it has. Most models have not been tried "
        "on real earbuds yet: their support comes from the brands' own apps and from other open-source projects. Each brand's page "
        "says which models have been tried, and a device report from your own earbuds helps the next person.",
    ),
    (
        "Can I use PairFree and the brand's own app together?",
        "Not at the same time: only one app can hold the control connection to a pair of earbuds. Close the brand's own app first.",
    ),
    (
        "Which phones does it need?",
        "Android 12 or later. AirPods need Android 17, or one of the few phones on Android 16 that already open their settings channel.",
    ),
    (
        "Where do I get it?",
        "From F-Droid or from the GitHub releases page. Both are the same APK, signed with the same key.",
    ),
]

NOT_AFFILIATED = (
    "PairFree is an independent project. It is not affiliated with or endorsed by SOUNDPEATS, Anker, Samsung, Sony, Bose, "
    "Harman (JBL), Apple, Nothing, Google, QCY, 1MORE or EarFun; the names are used to say which earbuds it works with."
)


def summary():
    """{page: (models, tried)} from the README's generated brand table."""
    text = (ROOT / "README.md").read_text(encoding="utf-8")
    rows = {}
    for m in re.finditer(r"^\| \[[^\]]+\]\(docs/devices/([\w]+)\.md\) \| (\d+) \| (.+?) \|$", text, re.M):
        rows[m.group(1)] = (int(m.group(2)), m.group(3).strip())
    return rows


def url(slug):
    return f"{SITE}/open-source-{slug}-app/"


def brand_page(b, models, tried):
    status = (
        f"Models that have been tried on real earbuds: {tried}." if tried not in ("—", "") else
        "No model of this brand has been tried on real earbuds with PairFree yet; support comes from the brand's own app and reports."
    )
    bullets = "\n".join(f"- {x}" for x in b["does"])
    close = (
        f"PairFree does not need {b['app']} to run, but only one app can hold the control connection to the earbuds at a time, "
        f"so close {b['app']} first.\n\n" if b.get("close", True) else ""
    )
    other = "\n".join(f"- [{o['name']}]({url(o['slug'])})" for o in BRANDS if o is not b)
    return f"""---
layout: page
title: "{b['title']}"
description: "{b['lead']}"
permalink: /open-source-{b['slug']}-app/
---

{b['lead']}

{close}## What you can control

Each model shows only the settings it has:

{bullets}

## {b['name']} models

PairFree knows {models} {b['name']} models. {status}

{b['notes']}

The [full model list for {b['name']} on GitHub]({REPO}/blob/main/docs/devices/{b['page']}.md) shows what works on each one. If your model is not marked as tried, a device report from you adds it: see [how to help]({REPO}#help-add-your-model).

## Why use PairFree

- **No account and no internet.** The app has no internet permission, so it cannot go online.
- **One app for several brands.** The same screens for every brand, instead of one app per pair of earbuds.
- **Open source.** GPL-3.0; the protocol is documented in the repository with tests, so anyone can check what the app sends to the earbuds.
- **Small and quick,** about 3 MB, and reproducible: the published APK can be rebuilt from the source.

## Get PairFree

PairFree is on [F-Droid](https://f-droid.org/packages/io.github.omegadestroyer.pairfree/) and on the [GitHub releases page]({REPO}/releases/latest). It needs Android 12 or later.

## Other brands

PairFree controls earbuds of other brands too:

{other}

---

{NOT_AFFILIATED}
"""


def home(rows):
    brands = "\n".join(
        f"- [{b['title']}]({url(b['slug'])}): {rows.get(b['page'], (0, ''))[0]} {b['name']} models" for b in BRANDS
    )
    total = sum(v[0] for v in rows.values())
    faq = "\n\n".join(f"### {q}\n\n{a}" for q, a in QUESTIONS)
    return f"""---
layout: page
title: "PairFree: open-source app for earbuds and headphones (Android)"
description: "A free, open-source Android app that controls earbuds and headphones of many brands from one app: Soundcore, Bose, JBL, Sony, Galaxy Buds, AirPods, Nothing, Pixel Buds, SOUNDPEATS, QCY, 1MORE, EarFun. No account, no internet."
permalink: /
---

PairFree is a free, open-source Android app that replaces the brand-specific apps for wireless earbuds and headphones with one light, fast one. It talks to your earbuds directly over Bluetooth to set noise cancelling, the equalizer, touch controls and more. It has no account, no internet access and no ads.

It covers {total} models from 12 brands. Pick yours:

{brands}

## What it controls

Each model shows only what it has: the battery of each earbud and the case, noise control (off, noise cancelling, transparency, adaptive) with its scenes and levels, the equalizer with the brand's presets and your own curves, touch controls, game mode, LDAC and LE Audio, two devices at once, the ear tip fit test, find my earbuds, a home screen widget and a status notification, and the settings only one brand has.

## Why PairFree

- **No account, no internet.** The app does not have the internet permission, so it cannot go online.
- **One app to replace a drawer full of them.** Every brand gets the same screens.
- **Open source** (GPL-3.0), with every protocol documented in the repository and tested.
- **Reproducible builds:** the published APK can be rebuilt from the source and checked byte for byte.
- **Grows with its users:** a device report from your earbuds adds your model for everyone ([how to help]({REPO}#help-add-your-model); the people who have are thanked in [CONTRIBUTORS.md]({REPO}/blob/main/CONTRIBUTORS.md)).

## Get PairFree

PairFree is on [F-Droid](https://f-droid.org/packages/io.github.omegadestroyer.pairfree/) and on the [GitHub releases page]({REPO}/releases/latest). The source is at [github.com/Omega-Destroyer/PairFree]({REPO}).

## Questions

{faq}

---

{NOT_AFFILIATED}
"""


SITE_LINKS = re.compile(r"(<!-- site-links:start[^\n]*-->\n)(.*?)(<!-- site-links:end -->)", re.S)


def readme(rows):
    """The README with the list of the brands' web pages filled in between its markers (other text is left as it is)."""
    text = (ROOT / "README.md").read_text(encoding="utf-8").replace("\r\n", "\n")
    links = "\n".join(f"- [{b['title']}]({url(b['slug'])})" for b in BRANDS)
    block = f"Each brand also has a page on the [web site]({SITE}/):\n\n{links}\n"
    return SITE_LINKS.sub(lambda m: m.group(1) + block + m.group(3), text, count=1)


def build():
    rows = summary()
    pages = {"docs/index.md": home(rows), "README.md": readme(rows)}
    for b in BRANDS:
        models, tried = rows[b["page"]]
        pages[f"docs/apps/{b['slug']}.md"] = brand_page(b, models, tried)
    return pages


if __name__ == "__main__":
    for rel, text in build().items():
        path = ROOT / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        # The README keeps the line endings it has.
        newline = "\r\n" if rel == "README.md" and b"\r\n" in path.read_bytes() else "\n"
        path.write_text(text, encoding="utf-8", newline=newline)
    print(f"{len(build())} files written (docs/ and the README's brand links)")
