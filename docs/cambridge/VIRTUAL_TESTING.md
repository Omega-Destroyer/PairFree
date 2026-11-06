# Melomania: tested virtually

Date: 2026-11-06. This category means **the official vendor app connected to a simulated Bluetooth
device and its outgoing commands were observed**. It does not mean real earbuds accepted the commands,
that audio was measured, or that every PairFree feature has been tested on hardware.

| Virtual model | Vendor-app checks | Hardware tested |
|---|---|---|
| Melomania Touch | RFCOMM connection/startup reads; six EQ presets; all five gain bands including signed gains and limits; custom-slot save/recall; ten function-control bits and master switch; eight voice languages; four codec preferences; transparency switch and level 0/36/100 | No |
| Melomania 1+ | RFCOMM connection/startup reads; six EQ presets; all five gain bands; nine function-control bits and master switch; French language change; absence of Touch transparency on the home screen | No |

## Environment and provenance

- Original Melomania 2.8.12 (570), package `com.cambridgeaudio.melomania`, installed with its original
  arm64, English and xxhdpi splits. **No APK modification or re-signing** was needed: the app offers
  "Skip sign in". Wi-Fi and mobile data were disabled before installation and launch.
- Dedicated Android 37.0 Google Play x86_64 AVD, emulator 37.2.12.0, with ARM64 translation. The emulator's
  `hidden_api_policy` was configured to 1 for the app's reflective Bluetooth discovery calls. This is
  an emulator configuration, not a modification of the vendor's command builders.
- Bumble 0.0.235 with the Android/Netsim transport, RFCOMM SDP records for standard SPP and legacy GAIA,
  and an A2DP sink so Android reports an audio connection. The original research harness is outside
  the repository at `E:\Builds\research\melomania-virtual`.
- All battery values, firmware versions, masks, addresses and acknowledgements sent **to** the app
  were invented simulator fixtures. Packets sent **by** the running app are recorded separately from
  those replies. No physical Bluetooth controller or real earbuds were used.
- Distinct model instances need distinct Netsim chip addresses. Bumble's registration in this version
  omits the address; the harness supplies it in `Chip.address`. These addresses are synthetic.

## What the running app established

Startup traffic is `0294`, `0780`, `0783`. Preset and control reads repeat at roughly two seconds;
the composite state repeats at roughly five seconds. The responses were synthetic, so the app's
polling and parsing behavior is observed, while the response schema still relies on static analysis.

The six preset writes use `0214` with ids 0..5. Gain-band writes use `021A`, bands 1..5, parameter 2,
signed big-endian gain times 60 and final apply byte 1. The observed +2 dB band-1 packet is
`FF010005000A021A0612007801`; -2 dB band 2 is `FF010005000A021A0622FF8801`.

Editing a single band after Neutral selects custom mode 6 and then writes the changed band. Saving
and recalling the app's **phone-local** Custom 2 slot produced **Neutral 0, custom mode 6, then the
five nonzero gains**. It did not select device preset 7. PairFree's full-curve write now begins with
Neutral and custom mode before writing all five explicit values. Its UI no longer describes this
as replacing a device-side "Custom 1" saved slot. Numeric EQ frequencies remain unestablished; the
vendor UI labels the axes Bass, Mid-Range and Treble without a numeric frequency grid.

Control writes confirm bits 0..8 for play/pause, next, previous, volume up/down, answer/reject/end call
and assistant; Touch adds bit 9 for transparency. The master switch writes zero and restores the
phone's saved mask. PairFree currently exposes the individual function toggles. Rapid toggling in
the vendor UI can race its state polling, so captured action labels alone are not treated as proof
of the resulting on/off state; regression cases must retain their actual initial mask and packet.

The eight language writes are `020B` with indices 0..7. Codec writes are `07B8`, 0 SBC, 1 AAC, 2 aptX,
3 AAC+aptX. The vendor UI warns of an earphone restart after applying a codec preference; no separate
reset command was sent in these captures. Applying was possible with both simulated batteries present;
this runtime result takes precedence over the confusing decompiled conditional in `b3/e`.

Touch transparency writes `0798 01` on and `0798 02` off. Level writes `0799` were observed at 0, 36
and 100, including slider-release behavior. The phone-call permission prompt was denied; the local
transparency controls remained usable.

Firmware update and high-performance/low-power mode were not opened or exercised. The latter installs
a bundled firmware image. No firmware, account or location traffic was enabled. Captures here are
**vendor-app-on-emulator observations**, never hardware reports and never a reason to increment
"Tried on real earbuds" counts.

`data/cambridge/emulator_vectors.json` retains 78 selected outgoing command cases with explicit virtual
provenance and hashes of the installed APK splits. `CambridgeEmulatorTest` compares these packets with
PairFree's setting builders and compares a complete custom-curve restore sequence byte for byte.
Raw HCI captures, emulator state, pairing material, APKs and harness files remain outside the repository.

## PairFree UI check

The debug build from local commit `3317616` was installed on the same emulator and connected to the
virtual **1+**. Home displayed the simulated 80%/85% batteries and firmware 8.1.0. Sound showed five
gain rows; increasing band 1 twice sent Neutral, custom mode, then all five explicit gains on each
change, with band 1 reaching +1 and +2 dB. Controls showed nine function toggles; play/pause off/on
sent `0782 01FE` / `0782 01FF`. The Device tab showed the French language state and codec preference.
This is a limited UI/transport check of PairFree on the 1+ fixture. PairFree's Touch UI, every setting,
reconnection/failure behavior and physical earbuds were not exercised by that check.
