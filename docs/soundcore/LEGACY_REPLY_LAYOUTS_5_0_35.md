# Soundcore 5.0.35: legacy reply layouts and parser checks

Thirty checks interpret restored parser DEX with synthetic buffers and in-memory beans. The [exact cases](LEGACY_REPLY_CASES_5_0_35.json) include null/short buffers, optional fields, EQ markers, button nibbles and two normalization paths. These are parser checks, not captures from earbuds. The outer packet assembler, checksum validation, dispatcher and Android callbacks are not executed.

## A3909 manager: state reply 01/01

`A3909DeviceManager.a` validates assembled packets with `CheckSumUtil.d`, reads group and command at packet offsets 5/6, then dispatches group 1 to `W`. For command 1, `W` calls `w0`. That method chooses **`u0` for model code A3910**, otherwise **`y0`**. A3909/A3910/A3913 sharing the same manager therefore does not mean they share this reply layout.

Offsets below are absolute packet offsets, including the existing nine-byte header. The direct parser entries do not repeat the outer checksum check.

| Offset | Common field / interpretation |
| --- | --- |
| 9 | host-device byte, unsigned |
| 10 | TWS state, true only for byte 1 |
| 11, 12 | left/right battery bytes, unsigned |
| 13, 14 | left/right charging, true only for byte 1 |
| 15–16 | little-endian EQ word with mode markers described below |
| 17 | gender byte |
| 18 | age-range byte; 0xFF makes `hearIdHasData` false |
| 19 | HearID switch, true only for byte 1 |
| 20–27 | left HearID positions 0–7 |
| 28–35 | right HearID positions 0–7 |
| 36–39 | HearID time, decoded by `BytesUtil.J(..., true)` in big-endian order |

The position helper `t0` first treats each input as unsigned and clamps it to 0…24. It does not divide by ten. These are bean positions, not established displayed dB values.

### `y0`: minimum 46 packet bytes

| Offset | Field |
| --- | --- |
| 40 | left double-tap function |
| 41 | left long-press function |
| 42 | right double-tap function |
| 43 | right long-press function |
| 44 | transparency switch, true only for byte 1 |

The tested direct entry returns false for null or fewer than 46 bytes. It accepts the tested longer buffers without adding the A3910-only optional fields below.

### `u0`: A3910, minimum 50 packet bytes

| Offset | Field |
| --- | --- |
| 40, 41 | left double-tap enable and function |
| 42, 43 | left long-press enable and function |
| 44, 45 | right double-tap enable and function |
| 46, 47 | right long-press enable and function |
| 48 | transparency switch |
| 49 | auto-ear/wear detection, read only when packet length >=51 |
| 50, 51 | left single-tap enable and function, read only when length >=55 |
| 52, 53 | right single-tap enable and function, read only when length >=55 |

The tested direct entry returns false for null or fewer than 50 bytes. At length 50 it marks wear detection unavailable and false. At 51 it exposes wear detection. At 54 it still marks single-tap support unavailable; at 55 it reads the four single-tap bytes. Preserve the packet-length gates, rather than merely checking whether individual array indices exist.

The parser populates `EQInfoWithCustomValues.customValues` using local helper `P0`, rather than reconstructing it from the listed packet bytes. That dependency is modeled in the interpreter and deliberately omitted from the published decoded-packet observations. The parent `w0` reset and later callbacks are also outside the direct-entry checks.

### EQ-word markers and range handling

The high EQ byte selects a vendor `eqType`: 0xEE gives type 2 and subtracts 0xEE00 from the little-endian word; 0xFE gives type 1 and retains the word; other values give type 3. Ordinary indices outside 0…22 are reset to 0, with exceptions:

- Both parsers preserve 0xFEFE.
- `y0` also preserves index 238 (0x00EE), while `u0` resets that value to 0.

The checks cover words 0, 22, 23, 238, 0xEE05 and 0xFEFE for both variants. The meaning of index 238 is not assigned from this acceptance test. Invalid-index fallback does not change the already selected `eqType`.

## A3951 manager: packed button block

`G1(buffer, offset)` reads twelve bytes relative to its offset argument:

| Relative offset | Field |
| --- | --- |
| 0 | left double-tap enable |
| 1 | left double-tap functions: high nibble non-TWS, low nibble TWS |
| 2 | left long-press enable |
| 3 | left long-press functions, same nibble order |
| 4 | right double-tap enable |
| 5 | right double-tap functions, same nibble order |
| 6 | right long-press enable |
| 7 | right long-press functions, same nibble order |
| 8, 9 | left single-tap enable and unpacked function byte |
| 10, 11 | right single-tap enable and unpacked function byte |

Enable bytes compare exactly to 1. A synthetic 0x75 packed byte decoded to non-TWS function 7 and TWS function 5. The direct helper throws `ArrayIndexOutOfBoundsException` when given eleven bytes at offset zero. This establishes the helper's input requirement; it is not a conclusion about validation at its callers.

## A3951 manager: model-dependent position decoding

`I1` reads eight position bytes through `I0`. For A3027, A3028, A3028CC, A3029, A3029XR, A3030, A3033, A3033EU, A3045, A3926, A3930 and A3951, `I0` clamps the unsigned byte to 60…180 before dividing by ten. Other model-code inputs skip that clamp and still divide by ten.

The checks use A3028 and a synthetic unlisted A3954 string to distinguish the two helper branches. This does **not** assign A3954 to the A3951 protocol route. Input bytes `00 3b 3c 78 b4 b5 fa ff` produced positions `6, 6, 6, 12, 18, 18, 18, 18` in the clamped branch, versus `0, 5.9, 6, 12, 18, 18.1, 25, 25.5` without it. This differs from the A3909 helper's unsigned 0…24 clamp.

The separate `K1`/`N0` normalization path depends on additional model bounds and remains to be traced. These findings must not become one universal EQ decoder.

## Reproduction and limits

External `LegacyReplyProbe.java` extends the earlier `LegacyPayloadProbe` interpreter. Arithmetic, byte reads, branches and selected `BytesUtil` routines run from restored DEX; model setters store their arguments in synthetic beans. `P0`, logging and support-flag setters are modeled locally, with no transport or account access. All six earlier serializer/framer checks still pass after the interpreter extension.

Compile both probes with `ControlFactoryProbe.java` and `DexProtocolIndex.java` on the JADX JAR classpath. Run `LegacyReplyProbe` with restored DEX directory, `static-prefixes.json`, output `legacy-payload-cases.json`, and output `legacy-reply-cases.json`. This checks the listed paths and buffers, not every malformed packet or firmware combination.
