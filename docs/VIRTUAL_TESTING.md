# Virtual device testing

The [existing-vendor audit started 2026-11-07](EXISTING_VENDOR_VIRTUAL_AUDIT.md) is in progress and
tracks the previously implemented catalogs separately from the five-vendor results below.

Research run: **2026-11-06**. Virtual testing is a separate evidence category from source analysis,
unit tests and **testing on real earbuds**. A simulator supplies invented replies; observing the
vendor app's outgoing bytes verifies what that app sends under those conditions. It does not establish
that physical firmware accepts the command or that an acoustic effect occurs.

## Tested virtually: vendor commands captured

| Vendor | Model/profile | Captured scope | Hardware tested |
|---|---|---|---|
| Cambridge Audio | Melomania Touch | Startup, six presets, five gain bands, custom save/recall, ten control bits, eight languages, four codec preferences, transparency switch/level | No |
| Cambridge Audio | Melomania 1+ | Startup, six presets, five gain bands, nine control bits, French language; limited PairFree UI/transport check | No |
| OnePlus | Buds Pro 2, catalog `062014` | Startup/reads, four EQ presets, ANC/transparency writes, game mode on/off, two gesture reassignments with readback | No |
| Nothing | Ear (2), B155, firmware fixture 1.0.1.74 | Four presets and Custom selection, six noise modes, left double-pinch assignments, low lag and in-ear switches; custom curve captured separately through an explicitly invoked original callback | No |
| Sony | WH-1000XM3, table set 1, restricted capability fixture | Preset IDs, cumulative six-value EQ, limits/readback/editor reopening, NC/ambient level and voice passthrough; offline privacy navigation intervention | No |
| EarFun | Air Pro 4 / TW600, Audio 20.1.61 | All 30 preset curves, all-band edits, local profile recall, selected gestures/noise/switches | Synthetic firmware/state; seeded gesture readback; [scope](earfun/VIRTUAL_TESTING.md) |
| Soundcore | Life Q30 / A3028, app 2.9.9 | All 22 preset IDs/curves, eight-band custom EQ, local recall and ambient modes | Static synthetic state/ACKs; current app 6.4.0 has no command result; [scope](soundcore/VIRTUAL_TESTING.md) |
| JBL | Tune 230NC / BES, Headphones 5.37.10 | Selected noise and gestures, Smart Switch, two EQ presets and a custom edit/recall | Synthetic identity notification and read replies; [scope](jbl/VIRTUAL_TESTING.md) |
| 1MORE | Aero / ES903, app 2.0.0 | Twelve presets, ten EQ bands, selected noise, playback, voice and gestures | Saved public model configuration seeded into app memory; synthetic replies; [scope](1more/VIRTUAL_TESTING.md) |

Details and limits: [Melomania](cambridge/VIRTUAL_TESTING.md), [HeyMelody](oneplus/VIRTUAL_TESTING.md).
The 2026-11-07 existing-vendor pass adds [Nothing's exact scope and interventions](nothing/VIRTUAL_TESTING.md).
It also adds [Sony's restricted profile and negative fixture results](sony/VIRTUAL_TESTING.md).
Cambridge retains 78 selected outgoing command cases; HeyMelody retains 25 distinct command/payload
observations. Those counts are packets/cases, not devices or feature-completeness percentages.

## Instrumented virtual connection: startup only

| Vendor | Model/profile | Evidence | Remaining work |
|---|---|---|---|
| Audio-Technica | ATH-M50xBT, GAIA v2 | RFCOMM opened; API-version query observed at the app output and in HCI traffic, with A2DP discovery supplied by a hook | Complete identification/replies and reach settings; other three AT families have no virtual result |
| SoundPEATS | H3 / S5, SOUNDPEATS 2.5.3 | Model/battery UI and startup/probe packets after research-only local route selection | Hydrate feature configuration and exercise settings; PeatsAudio 1.9.45 has no device-command result |
| Bose | QC Ultra 2 HP / Wolverine, Bose 13.3.4 | Three original BMAP identification requests over RFCOMM with connection/audio-profile hooks | Trace local onboarding return and reach settings; [limits](bose/VIRTUAL_TESTING.md) |
| QCY | MeloBuds N70, app 4.0.7 | BLE connection, EQ/gesture characteristic reads and two item queries, with local navigation and a synthetic discovery row | Supply verified control-panel configuration and exercise settings; [limits](qcy/VIRTUAL_TESTING.md) |

See [Audio-Technica's environment, interventions and exact limit](audiotechnica/VIRTUAL_TESTING.md).
See also [SoundPEATS' startup evidence and limits](soundpeats/VIRTUAL_TESTING.md).
This is deliberately distinct from an uninstrumented vendor-app settings test.

## Attempted: no headphone protocol capture

| Vendor | Model/profile | Furthest observed result | Next investigation |
|---|---|---|---|
| Huawei | FreeBuds 3 | Original app reaches offline catalog; instrumented local network check reaches configuration guide and scan; scan finds no eligible device | Reproduce its discovery/identity data and enter the embedded Smart Audio plugin |
| Skullcandy | Crusher 1080 ANC, product `34` | Android pairs/connects virtual audio; vendor app recognizes model and shows first-time Internet Required dialog; no RFCOMM control channel | Trace provisioning/configuration selection and its offline cache before a control-session test |
| Beats | Fit Pro | Android pairs/connects synthetic audio; app remains at welcome and opens no accessory channel | Investigate model discovery/identity and transport; [scope](apple/VIRTUAL_TESTING.md) |

Details: [Huawei](huawei/VIRTUAL_TESTING.md), [Skullcandy](skullcandy/VIRTUAL_TESTING.md). These models
are **not** counted as virtually tested settings. Neither setup difficulty is a claim that the protocol
cannot be implemented. No server account/authentication was used, and no authenticated provisioning
was bypassed.

## Catalog coverage and reporting

The [85 HeyMelody records](oneplus/MODELS.md), [28 AT definitions](audiotechnica/MODELS.md) and
[56 Skullcandy records](skullcandy/MODELS.md) each have a virtual-testing status in their index table.
All rows other than the particular profiles above remain **Not attempted**. Duplicate names, hidden
records, different firmware feature lists, different transports and different chip families do not
inherit a test. Huawei's [older PBI and newer reference-model tables](huawei/PROTOCOL.md) likewise keep
source coverage separate from this single FreeBuds 3 setup attempt. Cambridge's modern A100/M100/P100
family was not virtually tested; its separate Melomania Connect APK was not present in this audit.

For future results, record the exact app/splits and hashes, selected model/id/firmware fixture, transport,
UI action, outgoing packet, synthetic response and any hooks. An installation, pairing dialog, unit test,
source-derived frame or successful simulator ACK alone does not justify a vendor-command test result.
Keep hardware reports and hardware-tested counts unchanged.

## Reproduction and local review

Original APKs, simulator code, UI dumps/screenshots, HCI logs and pairing state remain outside the repo:
`E:\Builds\research\melomania-virtual`. The supplied reference APKs were not modified or re-signed.
The AT native-library installation workaround and the AT/Huawei runtime hooks are disclosed in their
individual notes. Internet connectivity in the Android guests stayed disabled throughout vendor-app
work. No firmware installation, factory reset, power-off command or account service was exercised.

The lab used emulator 37.2.12.0 and Bumble 0.0.235. Android 37.0 provided Netsim/ARM64 translation for
Melomania, HeyMelody and Skullcandy. Android 30 provided 32-bit ARM translation and a separate legacy
RootCanal HCI transport for AT; Huawei was also examined there with Frida 17.26.1. Android 33 was booted
as a compatibility check, but its x86_64-only guest could not run the supplied AT armeabi-v7a split.
See [Android's network-simulator documentation](https://developer.android.com/studio/run/emulator-networking-advanced),
[64-bit emulator-image guidance](https://developer.android.com/google/play/requirements/64-bit) and
[Frida's Android setup documentation](https://frida.re/docs/android/).

This research branch contains documentation and selected outgoing protocol facts. PairFree implementation
changes remain on the individual local `brand/*` branches. Cambridge's virtual-curve fix/regression cases
are in `3317616`; its additional UI scope is documented in `ed29a1a`. No merge, release, push or PR is
part of this virtual audit.

## Local review commits

| Local branch | Commit | Virtual-audit change |
|---|---|---|
| `research/virtual-device-audit` | `cfa56ee` | Cross-vendor index, full research catalogs and selected captures |
| `brand/cambridge` | `3317616`, `ed29a1a` | Captured command regression cases, custom-curve fix and limited PairFree UI result |
| `brand/oneplus` | `4f768da` | Vendor-specific scope, evidence and limits; app integration remains a draft |
| `brand/audiotechnica` | `c10384a` | Vendor-specific scope, evidence and limits; app integration remains a draft |
| `brand/huawei` | `99f3598` | Vendor-specific scope, evidence and limits; app integration remains a draft |
| `brand/skullcandy` | `7f48144` | Vendor-specific scope, evidence and limits; app integration remains a draft |

All are local. The research-only follow-up passed 133 existing Python checks plus catalog/table,
local-link and selected-packet validation. Cambridge's code change also passed its protocol tests,
debug APK build and lint before the vendor-app and PairFree emulator checks were documented.
