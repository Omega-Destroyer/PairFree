# SoundPEATS virtual testing

Research date: **2026-11-07**. This is **not hardware testing**. Both supplied original APKs were
installed offline on the isolated Android 37 Google APIs userdebug emulator with ARM64 translation.
No vendor packet builder was changed.

## H3 / S5: identification and startup commands

**SOUNDPEATS 2.5.3-global**, `com.thirtydays.headset`, connected by real Android RFCOMM to a
synthetic H3 over SPP. The peer's firmware is `S5_20250605_V0.4.9`; selected state replies were
seeded from the existing H3 recording facts, with synthetic batteries 85/80/100. Replies remain
fixtures even when their values originate in a hardware report.

The offline welcome screen offered Log In/Register. Source inspection found a guest path whose
availability and guest identifier come through `NetworkSkipLoginRepository`. Instead of using
credentials or contacting that service, a research Frida script selected the existing local
`AppBackStack` DevicesRoute, then DeviceScanRoute with the synthetic name/address. This is an
**instrumented navigation test**, not an untouched onboarding result. Android pairing/A2DP used
Android's own APIs. Encoders and transport writes were unmodified.

The original app recognized **H3**, displayed all three supplied battery values and sent the
startup/probe commands retained in [`emulator_vectors.json`](../../data/soundpeats/emulator_vectors.json).
The trace includes unsupported/unanswered reads as well as answered reads; no blanket success
reply was supplied. `0367` reset-state queries occurred but are excluded from selected fixtures.
No reset write, firmware channel or power-off operation was invoked.

One version-specific finding: **2.5.3 sent `0362 00` to this H3/S5 profile**. The older 1.9.45
description restricts the Dolby support hint to S7; that restriction must not be attributed to
both app generations. This is app output under a fixture, not proof that H3 firmware uses it.

The offline product page showed User Guide, Device Info, Firmware Upgrade and Reset Device,
with no normal settings controls. The latter two flows were not opened. Per-model feature/EQ
configuration hydration still needs to be supplied or traced before claiming EQ, presets,
gestures or additional settings were tested. Identification and battery traffic alone do not
validate those features or the remaining 87 catalog entries/protocol families.

## PeatsAudio 1.9.45

`com.xingkeqi.peats` reached its login screen after terms and the introduction. No account was
entered and no device command was captured from this version in this pass. The newer app's H3
traffic must not be attributed to PeatsAudio.

Raw traces, UI captures, fixture replies and research tools remain outside the repository at
`E:\Builds\research\existing-vendor-virtual-2026-11-07`.
