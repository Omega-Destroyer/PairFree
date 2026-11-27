# HeyMelody: tested virtually

Date: 2026-11-06. **OnePlus Buds Pro 2**, catalog id `062014`, connected to the original HeyMelody
117.6.6 over Android's Bluetooth Classic/A2DP and RFCOMM path. No APK change, login or runtime hook was
needed. This covers one synthetic profile, not the other 84 catalog records, every firmware, or OPPO.
No physical device or PairFree UI was tested for this brand.

## Environment and fixtures

Dedicated Android 37.0 Google Play x86_64 emulator, ARM64 translation; original base, arm64, English
and xxhdpi splits. Wi-Fi/mobile data were disabled. Bumble 0.0.235 advertised the name and service
`0000079A-D102-11E1-9B23-00025B00A5A5` plus an A2DP sink. The public peer address was synthetic.

The simulator advertised capability bits **0, 1, 3, 4, 7, 8, 10**, with invented firmware/battery,
eight control records, switch states, preset and noise-state replies. Early runs deliberately left
several reads and writes unanswered; later runs supplied the selected read states and success ACKs.
Unsupported/unestablished commands were not given blanket success. These replies are fixtures, not
hardware recordings or evidence that the device really has those values.

The initial battery records were types 1/2/3 with 80/85/100. The UI displayed left 80, right **90** and
case 100: this model's rounding applies to the raw right-earbud value. Preserve raw percentage and UI
rounding separately.

## Captured commands

Startup began `0100`, `0102 FFFF`, `0200`, `0106`, `010B`, `0103`, `0101 0002`, then feature reads.
The vendor-id request used `FFFF` even on this selected OnePlus profile. Sequence values are per
connection; later reconnects restart them. Firmware reads and other unanswered reads were retried.

| UI/action | Command | Payload observed |
|---|---|---|
| Balanced (Default) | `0406` | `00` |
| Bold | `0406` | `03` |
| Clear Vocals | `0406` | `02` |
| Bass | `0406` | `01` |
| Noise cancellation | `0404` | `01 01 02` |
| Transparency | `0404` | `01 01 04` |
| Game mode on / off | `0403` | `06 01` / `06 00` |
| Left double-squeeze -> Next track | `0401` | `01 01 01 02 06` |
| Right double-squeeze -> Previous | `0401` | `01 02 01 02 05` |

The gesture payload starts with count 1, then device type, button, gesture and function. Each of
those writes was followed by `0108 02 03 01`; the running UI displayed the returned assignment.
Preset changes were followed by `010F`; game changes by the model-selected `010D` read.
The observed switch query was `0A 05 04 0B 0C 0F 11 18 06 1D 1C`.

The Off noise-mode button was present, but the attempted Off clicks did **not** produce a write in
the retained trace. Do not label an inferred Off packet as captured. The fixture did not reproduce
all asynchronous noise notifications. Also, an early unacknowledged game-mode click changed the UI
optimistically; screen state alone is insufficient evidence of acknowledgement or readback.

Custom EQ, every gesture/action, ANC levels, personalized noise, dual connection, fit/hearing tests,
codec selection, Zen Mode and other advanced features remain untested virtually. No firmware/debug,
provisioning or private operation was exercised. The selected capability fixture is intentionally
smaller than the catalog's feature list and must never become a production all-model capability table.

`data/oneplus/emulator_vectors.json` preserves **25 distinct actual outgoing command/payload
observations**, including startup and unanswered reads, with provenance and timestamps. It contains
no invented reply masquerading as a capture. Full traces and UI actions are outside the repo under
`E:\Builds\research\melomania-virtual` (`oneplus-traffic.jsonl`, `oneplus-actions.jsonl`).
These facts extend [PROTOCOL.md](PROTOCOL.md); they do not establish completed PairFree support.

## PairFree against a virtual Buds Pro 2 (2026-11-10)

Virtual testing, not hardware. PairFree's debug build (the `brand/oneplus-app` branch) ran on an Android 17 emulator against a Bumble peer that
offered the service `0000079A-...` and answered the reads from HeyMelody's parsers (invented replies: battery 80/85/100, the noise bitmap, preset
0, game mode off). PairFree listed it by name, connected over the serial service and sent `0100`, `0102 FFFF`, `0106`, `0103`, `0101 0002`, `010C
0101`, `010F` and `010D 0106`. It showed left 80%, case 100%, right 85% and noise cancelling, and wrote `0404 010104` for Transparency, `0406 03`
for the Bold preset and `0403 0601` / `0600` for game mode: the same payloads HeyMelody sent in the earlier run (the unit test
`OneplusSessionTest` compares them with `data/oneplus/emulator_vectors.json`). Off is written as `0404 010101`, which the earlier run did not capture.
Not covered: the noise strengths, custom EQ, touch controls and every model but the Buds Pro 2.
