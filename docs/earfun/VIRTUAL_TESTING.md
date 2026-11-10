# EarFun virtual testing

Research date: **2026-11-07**. Original EarFun Audio **20.1.61**, `com.corelink.earfun`,
offline on the isolated Android 37 Google APIs userdebug emulator with ARM64 translation.
The APK and protocol encoders were unchanged. **No physical EarFun device was tested.**

## Air Pro 4 / TW600 settings capture

The stock app's Skip option permits guest use. Android paired and connected the synthetic
audio peer; the app recognized its name-only LE advertisement and opened the real serial
RFCOMM service. No login, navigation or encoder hook was needed. Read-only Frida inspection
of model metadata was separate from the UI actions and changed no methods or model selection.

The peer supplies made-up battery values (85/80/100), color, codec/state and gesture replies.
Its initial firmware string `0.3.2` parsed as empty for this model: `TW600Config.getFwVersion`
extracts field 3 from an underscore-separated string. Changing the **synthetic reply** to
`TW600_VIRTUAL_v0.3.2` displayed the later feature set. This is not a discovered firmware
identifier or a claim about which firmware a real product currently runs.

### Original UI writes retained

[emulator_vectors.json](../../data/earfun/emulator_vectors.json) contains **131 distinct
setting packets**, plus **30 complete preset batches** and a saved-profile recall batch.
Each case preserves its wire vendor ID, command and full outgoing frame. APK hashes are included.

- All 29 named factory curves and Default emit ten GAIA vendor `001D`, command `0E01`
  packets, indices `A1` through `AA`. Independently decoding every captured batch gives
  exactly the curves in [eq_presets.json](../../data/earfun/eq_presets.json). This verifies
  the app's transmitted values, not acoustic response or real firmware acceptance.
- Custom edits exercised all ten bands, positive and negative values, and the full
  -6/+6 dB wire range. The screen's +/-10 positions are nonlinear gain-table indices.
  The master is `FE98` (-6 dB), frequencies are multiplied by 3 (31.5 Hz truncates to 94),
  gains by 60 and Q .7 to `0B33`. Initial negative drags reached position -8 (-4 dB),
  rather than the intended minimum; later captures explicitly reached -10 (-6 dB).
- Saving on the phone and selecting a saved custom profile emits the complete ten-band
  curve again. EQ replies were deliberately not implemented by the peer, so this establishes
  local profile recall and outgoing bytes, **not device readback or persistence**.
- Noise mode `0314`: Normal `00`, ANC `01`, Ambient `02`. Cancelling subtype `033A`:
  Strong `02`, Balance `03`, AI Environment Adaptive `04`, AI Ear Adaptive `00`, Wind `01`.
  Ambient subtype `033C`: Default `00`, Natural `01`. AI Environment Adaptive initially
  produced an upgrade notice and no write with the malformed firmware fixture; the corrected
  firmware reply permitted its original UI write. No firmware update was opened.
- Game mode `0312`: on `01`, off `00`. In-ear detection `0349`: off `01`, on `00`.
- Disable controls `0334`: both `01`, left `02`, right `03`, enabled again `00`.
- Gesture writes `030A` retain four bytes: left `[gesture, action, 0, 0]`, right
  `[0, 0, gesture, action]`. Single/double/triple/hold were exercised on both sides;
  hold uses gesture `11`. Captured examples include left single volume-down `01030000`,
  left double next `02050000`, left triple volume-up `03020000`, left hold assistant
  `11060000`; right-side volume-down assignments use `00000103`, `00000203`,
  `00000303`, `00001103`. The peer ACKs these writes but returns a seeded full gesture
  table; assignment persistence/readback is **not** verified.
- Prompt volume `0338`: 100/75/50/25/0 percent emit `00/01/02/03/04` respectively.

Ordinary status replies are synthetic, with simple write/read fixture state where implemented.
Unknown requests receive no invented application success. Startup includes `0371`, which the
current source names **GET_VOICE_WIZARD**; it is not an unidentified reset command. The TW600
parser's `initCommand()` is null, and this run does not validate the Free Pro 3's v3 opening.

### Limits and unfinished work

The microphone screen was inspected but not saved: its default `isChangeMicNeedReset()`
is true. Codec, multipoint, LE Audio/Fast Pair, factory reset, power-off, firmware and voice-pack
installation were not applied. Find tones, timers, partner/personalized curves, all gesture action
choices, call gestures, adverse reply combinations and reconnection still need investigation.
PairFree has not yet been compared against this fixture. Other chip families and model/firmware
variants inherit no settings coverage.

## Runtime model table

[runtime_models.json](../../data/earfun/runtime_models.json) records read-only enum getters
from all **35** original running model definitions: names, name-matching flag and parser class.
All catalog codes were present. This includes two speaker definitions for inventory only.
It is **metadata inspection**, not 35 virtual Bluetooth tests. The name-matching flag differs
by model; the earlier blanket description of LE product-ID matching was too broad.

Original APKs, scripts, UI captures, synthetic replies, raw traffic and pairing state remain
outside the repository in `E:\Builds\research\existing-vendor-virtual-2026-11-07`.

## Catalog coverage

Every row below has runtime metadata inspection. Only TW600 has the specific settings result
above; combined catalog entries do not transfer that result to another code or chip variant.

| Catalog name | Model codes | Bluetooth virtual result |
|---|---|---|
| EarFun Free Pro 3 | TW400 | Not attempted |
| EarFun Air Pro 3 | TW500, TW500_3072 | Not attempted |
| EarFun Air Pro 4 | TW600 | TW600 settings captured; stated fixture only |
| EarFun Air Pro 4+ | TW700, TW700AI | Not attempted |
| EarFun Air Pro 5 | TW800 | Not attempted |
| EarFun Air S | TWA01, TW201 | Not attempted |
| ATVEL Tune S5 ProX | ATVEL_TW201 | Not attempted |
| EarFun Free 2S | TW101S | Not attempted |
| EarFun Wave Pro X | HP1000 | Not attempted |
| EarFun Air 2 NC | TW220, TW220_VDL | Not attempted |
| EarFun Air Life | TW205 | Not attempted |
| EarFun OpenJump | OW100, OW100_NEW | Not attempted |
| EarFun Clip | CW100 | Not attempted |
| EarFun Clip 2 | CW200 | Not attempted |
| EarFun Air Pro 4i | TW230 | Not attempted |
| EarFun Wave Life | HP201 | Not attempted |
| EarFun Tune Pro | HP220 | Not attempted |
| EarFun Wave Pro 2 | HP300 | Not attempted |
| EarFun Air | TW200S | Not attempted |
| EarFun Air 2 | TW210 | Not attempted |
| EarFun Air Pro 2 | TW300 | Not attempted |
| ATVEL Tune S5 Pro | ATVEL_TW300 | Not attempted |
| EarFun Air Pro SV | TW306 | Not attempted |
| EarFun Free 1S | TW100 | Not attempted |
| EarFun Wave Pro | HP200, HP200_BT8931H | Not attempted |
| ATVEL Tune Pro Q7 | ATVEL_HP200 | Not attempted |
