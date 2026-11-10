# Skullcandy: virtual setup attempted, no control capture

Date: 2026-11-06. Profile: **Crusher 1080 ANC**, bundled product id `34`, model `S6CNW`.
Original Skullcandy 5.0.6, with arm64/English/hdpi splits, ran offline on the dedicated Android 37
guest. Android paired and connected A2DP to the simulated headphone. The vendor app recognized its
name and displayed **Internet Required** for first-time setup. **No RFCOMM control channel, Bragi RPC,
EQ, gesture or other setting write was captured.** This model is not marked as virtually tested
settings or as supported hardware.

## What the app actually showed

Onboarding was usable without signing in: welcome, terms/privacy, region and Bluetooth permission.
Notifications were denied. Initially the app detected another connected virtual brand and showed
an unsupported-product card. Disconnecting the other virtual accessories and relaunching let it
select Crusher 1080 ANC. This is a reason to test with only the intended audio accessory connected.

For Crusher 1080 ANC, first-time setup explicitly requested an internet connection. **Not now** is
the offline exit; the network was not enabled and the app was stopped. This observation establishes
a setup prerequisite in this installation, not whether the eventual configuration request is public
or authenticated, and not whether every Skullcandy model needs online setup.

The draft observation peer offered an audio sink and the reference Bragi data-service UUID, plus
legacy SPP/GAIA services to observe selection. An advertised service is a harness choice, not proof
that this product uses it. In this run the app selected none of those RFCOMM control services.

## Next work and limits

Trace the Flutter setup/provisioning path, transport configuration and offline cache, using the
bundled manifest, Bragi SDK and configuration strategy classes already indexed in [PROTOCOL.md](PROTOCOL.md).
If a configuration request is public, inspect/fetch it using an external unauthenticated research
script as allowed by CLAUDE.md. If it needs server authentication, record that actual requirement;
do not invent configuration or use app credentials. The observed dialog alone does not settle it.
Local secure-channel requirements also remain to be exercised; no device trust comparison or
authenticated-access exchange took place.

The other 55 catalog records are **not attempted virtually**. This includes both Crusher Evo
transport variants and every other GAIA, RACE, JieLi, legacy BLE and Bragi route; a common brand or
marketing name does not transfer a result. No hardware or PairFree UI was tested, no APK was patched,
and no firmware/reset/power-off or account service was exercised. The app's `secrets` asset was not read.

External artifacts: `E:\Builds\research\melomania-virtual\skullcandy-traffic.jsonl` contains readiness,
Bluetooth and A2DP events but **no `app_tx` packet**. `skull-only-connected.png` records the first-time
setup dialog. APKs, screenshots, emulator state and pairing material remain outside the repo. No
Skullcandy emulator command vectors have been added.
