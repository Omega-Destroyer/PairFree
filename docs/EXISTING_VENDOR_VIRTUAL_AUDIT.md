# Existing vendor emulator audit

Started **2026-11-07**, at the owner's request. **In progress.** This is the audit ledger, not a
claim that the existing device catalog has passed emulator testing. Results will be recorded by
exact model, firmware/capability fixture, app version and transport. Hardware-tested counts remain
unchanged. The earlier five-vendor work is indexed in [VIRTUAL_TESTING.md](VIRTUAL_TESTING.md).

## Scope

The existing JSON catalogs contain **619 device records across 12 brands**, including source-only
or partially covered records; JBL additionally lists 10 `not_covered` leads. Apple includes Beats.
These counts describe catalog entries, not unique hardware revisions or completed tests.

| Brand | Device records | Required distinctions | Current runtime status |
|---|---:|---|---|
| SoundPEATS | 88 | PeatsAudio/SOUNDPEATS versions; Qualcomm, Actions and other families; QCY-built BLE route | 2.5.3 H3 startup probes captured with local navigation intervention; settings pending |
| Soundcore | 66 | Current packed app versus 2.9.9; per-model layouts and feature sets | 2.9.9 Life Q30/A3028: all 22 presets, eight-band edits/recall and ambient modes captured; 6.4.0 startup investigation pending |
| Samsung | 16 | Legacy/standard framing, model-specific plugins and Wearable host, newer Buds4 plugin | Supplied plugin installed; host/entry investigation pending |
| Sony | 24 | Table sets 1/2 and both tables, advertised capabilities | WH-1000XM3/set-1 restricted fixture: EQ presets/full curve and noise settings captured; other functions/models pending |
| Bose | 22 | Model/app eligibility, BMAP commands and cloud-independent operators | QC Ultra 2 HP/Wolverine: three identification requests captured with connection hooks; settings pending |
| JBL | 112 | Direct Harman, protocol3/4, Airoha/LE Audio, BLE versus Classic | Tune 230NC: selected noise/gestures, Smart Switch and multipart EQ captured with a synthetic identity push; other families pending |
| Apple/Beats | 30 | Android Beats app versus AirPods/iOS-only vendor control; chip/model boundaries | Fit Pro: synthetic audio pairing only; app did not open control channel; no AirPods vendor-app result |
| Nothing/CMF | 19 | CRC/activation, simple/advanced/Dirac EQ, earbuds/case/headphones | Ear (2) presets/noise/selected controls and switches captured; custom curve through explicit original callback invocation |
| Pixel Buds | 6 | Maestro versus TLV; app/native-ABI and Android dependencies | Supplied APK needs Android 32+ but has ARM32 native split; environment investigation pending |
| QCY | 171 | QCY BLE, chip-specific layouts, JieLi/RCSP and custom tunnel | N70: BLE connection, EQ/gesture characteristic reads and two startup queries with local navigation and synthetic discovery row; settings pending |
| 1MORE | 39 | Per-model server-derived capability tables, EQ/controls and extra functions | Aero/ES903: 12 presets, all ten EQ bands, selected noise/playback/gesture settings captured with saved public configuration seeded in memory; other models pending |
| EarFun | 26 | Qualcomm/v3 opening, custom EF, model-specific EQ and gestures | Air Pro 4/TW600: 30 preset curves, all-band edits, selected gestures/noise/switches captured; other models pending |

## Method and limits

Compare the running vendor app and PairFree against the same fixture. Exercise normal presets,
EQ bands and profile recall, available gesture assignments, and additional settings. Vary capability,
firmware and state replies; check refusals, unanswered requests, readback, initialization ordering and
reconnection rather than returning success for every request. A simulator ACK establishes no physical
effect. Source-derived replies and packets captured from the vendor app are separate artifacts.

Do not enter firmware, factory reset or power-off flows. Do not use server credentials or account
authentication. Connectivity in vendor-app guests stays disabled. Any UI/discovery instrumentation is
recorded explicitly; modifying an encoder would invalidate its use as independent command evidence.
Missing app dependencies and unfinished fixtures remain research tasks, not protocol-impossibility claims.

## Environment and reproducibility

External artifacts: `E:\Builds\research\existing-vendor-virtual-2026-11-07`. This directory holds
reference/split hashes, installation results, original APK extractions, UI captures, synthetic profiles,
outgoing packet traces and research tools. The NAS reference APKs are unchanged. No vendor source,
binary, firmware, raw server data or pairing material is committed.

The initial Android 37 guest uses the established Netsim/Bumble lab. A small original research helper
exposes Flutter accessibility descriptions to UIAutomator and can inspect/connect a named synthetic
Bluetooth device through Android's own APIs. It does not alter vendor code or packet builders.
Only the isolated research guests receive these tools/configuration changes; the owner's AVD is untouched.

## Findings so far

- The supplied **1MORE 2.0.0** manifest identifies **`com.onemore.connect`**, not the previously documented
  `com.onemore.music`. Code namespace and installed application id must be distinguished.
- Required ABI/density splits matter: incomplete selection initially rejected PeatsAudio, SOUNDPEATS
  and EarFun. Installing the supplied matching density splits resolved those installation failures.
- The supplied Pixel Buds build has minimum Android 32 and an **armeabi-v7a-only** native split. It
  cannot be treated as successfully installed on the current x86_64/ARM64-only Android 37 guest.
- Nothing X opened RFCOMM to the virtual **Ear (2)** and queried color `C00C` and model `C01C` during
  local enumeration. The draft fixture initially omitted color; that missing synthetic response is
  not a device/protocol failure. Selected settings are retained below; complete model/firmware coverage is still pending.

No finding here yet changes a production command implementation or establishes model-wide virtual
coverage. This ledger will be updated as the runtime investigations finish.

### First retained command results

- [Nothing X / Ear (2)](nothing/VIRTUAL_TESTING.md): 22 selected distinct setting packets,
  including an explicitly assisted custom-EQ callback. Native Ear (2) high-shelf Q .7 and the
  Flutter low-lag screen's one-byte writes differ from the previously described generic paths.
  Preset bass/voice numbering agrees with PairFree's vendor-derived table.
- [SOUNDPEATS 2.5.3 / H3](soundpeats/VIRTUAL_TESTING.md): 32 selected startup/probe packets;
  recognized model and synthetic battery UI. Local navigation bypassed offline onboarding;
  feature configuration remains to be hydrated. No EQ/gesture coverage claim.
- [Sony Sound Connect / WH-1000XM3](sony/VIRTUAL_TESTING.md): 43 distinct setting payloads,
  including preset IDs, cumulative six-value EQ writes and NC/ambient/voice control. Only
  battery/EQ/noise capabilities were advertised. Invalid `FF` active-preset readback crashed
  the vendor app; the corrected simulator uses advertised Manual for readback.

- [Bose / QC Ultra 2 HP](bose/VIRTUAL_TESTING.md): three original identification requests
  captured through RFCOMM with disclosed connection/audio-profile hooks. Local onboarding
  returns to discovery; no settings result.

- [EarFun Audio / Air Pro 4](earfun/VIRTUAL_TESTING.md): 131 distinct setting frames,
  all 30 preset batches matching the existing curve table, local saved-profile recall,
  selected gestures/noise/switches. Runtime metadata for all 35 definitions is a separate category.

- [Soundcore 2.9.9 / Life Q30](soundcore/VIRTUAL_TESTING.md): 36 distinct setting
  frames, all 22 factory IDs/curves matching the existing table, eight-band edits and
  local profile recall, ANC subtypes/transparency/normal. Current packed app still needs startup tracing.

- [JBL / Tune 230NC](jbl/VIRTUAL_TESTING.md): selected noise/gesture/Smart Switch writes
  and six multipart EQ transfers after a synthetic identity push. Its BES Smart Switch
  `81`/`82`/`83` path differs from PairFree's current general direct implementation.

- [1MORE / Aero](1more/VIRTUAL_TESTING.md): 12 presets, ten custom EQ bands and selected controls captured after seeding the saved public model configuration. Original app sends handshake `004D`, contrary to the previous source-only note.

- [QCY / MeloBuds N70](qcy/VIRTUAL_TESTING.md): BLE connection, characteristic reads and two startup queries after local navigation and synthetic discovery metadata. No settings writes captured.

- [Beats / Fit Pro](apple/VIRTUAL_TESTING.md): synthetic audio pairing only; the app did not open an accessory control channel, so no protocol commands were captured.

## PairFree compared with the captured frames (2026-11-10)

Each captured frame set was replayed against PairFree's own code by a protocol test (the test named below reads the vector
file, builds PairFree's frame for the same setting and compares bytes). This is a comparison of PairFree with **what the vendor
app sent a synthetic peer**: it does not show that real firmware accepts any of it. What differed, and what was changed:

| Brand / model | Compared | Result | Change |
|---|---|---|---|
| JBL Tune 230NC | Smart Audio & Video read and write, left Volume and right None gestures | **Differed.** JBL's app has two status classes with the same methods and bytes except this; the BES class (base of `BesBasicControl` and `BesSdkControl`, 39 models) uses `82`/`81`/`83`, the protocol 3 class `80`. Gesture frames equal. | The 39 models use the BES commands (`smart_switch: bes`); others keep `80` (`JblTest.theBesModelsSendSmartAudioAndVideoOverTheirOwnCommands`). The multipart `41` EQ transfers (the BASS preset and the My EQ curve, 179 bytes in three parts) are **equal** (`JblEqTest.theTune230NcEqTransfersJblsAppSentToAVirtualPeer`); the other presets and the noise-mode frames are not compared yet. |
| 1MORE Aero | Handshake, 12 presets, custom open and the 10-band curve, noise, reset | **Differed.** The app does send `004D 01` first (the earlier source note was wrong: five call sites) and `0069 0C` when Custom is chosen. Presets, noise bytes and the flat curve equal. | PairFree sends the handshake first and `0069 0C` before a custom curve on models with presets (`OneMoreTest.theFramesTheAppSentToAVirtualAero`). The `0062` smart-playback, `0082` spatial, `0080` voice-control and `0084` tap frames are **equal**; the single-slider `0053` writes (one record per band) are not compared yet. |
| Nothing Ear (2) | Presets, noise, left double pinch, low lag, in-ear, custom curve (callback) | **Differed** in two places: low lag is one byte (`F040 01/02`), and the Ear (2)'s own view model gives the high shelf Q 0.7 (table: 1.0). The other products' own view models differ from the table too. | One-byte low lag; Ear (2) Q 0.7; the others recorded as open (`docs/nothing/PROTOCOL.md` section 5; `NothingTest.theFramesNothingXSentToAVirtualEar2`). The noise (`F00F`) and left double-pinch (`F003`) frames are **equal**; the personalised-ANC and dual-connection screens are not compared. |
| EarFun Air Pro 4 | All 30 preset batches, single-band slider frames, noise sub-modes, ambient/ANC icons, game mode, five gestures | **Equal.** | None (`EarfunTest.theFramesEarFunsAppSentToAVirtualAirPro4`). Voice volume, wear detection, disable controls and the firmware 32 natural mode are not compared yet. |
| Soundcore Life Q30 (2.9.9) | All 22 preset frames, 9 custom-curve and recall frames | **Equal.** | None (`SoundcoreVirtualQ30Test`). The `0681` ambient-sound frames are not compared yet. |
| Sony WH-1000XM3, set 1 | 35 EQ messages (preset ids, six-value curves) | **Equal.** | None (`SonySessionTest.theEqualizerMessagesSonysAppSentToAVirtualWh1000xm3`). The 8 noise-control messages and their state machine are not compared yet. |
| SOUNDPEATS H3 | The 32 startup/probe packets | PairFree's connect list contains every read the app sent except `03 62 00` (Dolby support hint, which PeatsAudio 1.9.45 sent only to the Clip1; 2.5.3 sends it to the H3). | Recorded in `docs/soundpeats/PROTOCOL.md`; not sent by PairFree. No settings captured. |
| Bose QC Ultra 2 HP | The 3 identification requests | The app sends version, all function blocks (`00.02`), product ids (`00.03`). PairFree sent the version, the ids and the firmware, not the block list. | PairFree now reads `00.02` second (`BoseTest.startsWithTheReadsBosectlSends`). |
| QCY MeloBuds N70 | Two startup queries | Not compared in code (PairFree's QCY BLE layer is not in the protocol module's unit tests for queries). | Open. |
| Beats Fit Pro, Samsung, Pixel Buds | No control capture exists | Nothing to compare. | Open (Samsung and Pixel Buds need their host or emulator dependencies investigated). |

