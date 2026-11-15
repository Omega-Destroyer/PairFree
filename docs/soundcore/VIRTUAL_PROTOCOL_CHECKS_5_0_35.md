# Soundcore 5.0.35-435: virtual protocol checks

**Additional evidence, 2026-11-15:** [Six legacy serializer/framer checks](LEGACY_PROTOCOL_DETAILS_5_0_35.md) use bounded interpretation of restored DEX with synthetic beans and a local sink. This is a distinct check method from the Android instrumentation below. Five verify A3909 payloads; one verifies complete Flare framing. [Control-factory interpretation](CONTROL_MAPPING_5_0_35.md) checks dispatch/action IDs, not transmitted packets or device acceptance.

These are offline calls to recovered vendor serializers and parsers, using synthetic objects and buffers. No earbud was connected and no transport send/connect method was called. The [machine-readable cases](VIRTUAL_PROTOCOL_CASES_5_0_35.json) include input parameters, observed bytes/values and the APKM SHA-256.

## Button writer coverage

The sweep follows the full measured `CmmBtCmdService` inheritance tree, including classes that declare only capability/preset overrides and inherit their actual builders. It covers **64 concrete classes, two sides each: 128 cases**. The dynamic `ThingsCmdService` is excluded because its encoder depends on downloaded product scripts. Each observed format was independently checked against the recovered constant `d6()` override in the DEX/class hierarchy and an independently constructed expected payload. A second fresh instrumentation run reproduced the results.

Input in every case: gesture ID 2, non-TWS function 7, TWS function 5. Those are synthetic encoding inputs, not claims that all these values are selectable or supported on every product.

| Format | Command set / ID | Payload | Classes in sweep |
| --- | --- | --- | --- |
| Legacy packed functions | `04/81` | side u8, gesture u8, packed functions u8; non-TWS in high nibble, TWS in low nibble | 60 |
| Separate function bytes | `04/8C` | side u8, gesture u8, TWS function u8, non-TWS function u8 | 4 |

Side is left=0, right=1. For the test values, legacy payloads are `00 02 75` / `01 02 75`; separate-byte payloads are `00 02 05 07` / `01 02 05 07`. The separate-byte classes are:

- `com.oceanwing.devicecmd.manager.product.a3956.A3956CmdService`
- `com.oceanwing.devicecmd.manager.product.d1105.D1105CmdService`
- `com.oceanwing.devicecmd.manager.product.d1206.D1206CmdService`
- `com.oceanwing.devicecmd.manager.product.d1406.D1406CmdService`

A first draft of the sweep selected only classes with a declared builder return type and omitted inherited-only classes. The final 64-class selection corrects that omission. No runtime-vs-source protocol conflict was established. These checks cover this one builder path; they do not prove that every class/model exposes configurable buttons. Trace its UI feature gate and action list separately.

## Parametric EQ writer

Three D1206 `m6` cases were checked byte-for-byte, with zero, one and two filter points. The model state was a synthetic in-memory holder, with no manager constructor or channel initialization. The cases confirm:

- Command prefix `08 ee 00 00 00 03 8a` (`03/8A`).
- EQ index `0xFEFE` as u16 little-endian.
- HearID enable and state as separate u8 fields.
- HearID time as a 32-bit little-endian bit pattern, tested with `0x12345678` → `78 56 34 12`.
- A u8 point count and 13 bytes per point: type u8, gain/frequency/Q as f32 little-endian.
- Payload size `9 + 13 * count`.

Example with one point (type 1, gain 1.25, 100 Hz, Q 0.7), enabled HearID, state 1 and that time value:

`fe fe 01 01 78 56 34 12 01 01 00 00 a0 3f 00 00 c8 42 33 33 33 3f`

The zero-point case documents what the builder emits; it does not show that firmware accepts zero points. Preset selection and firmware-dependent table selection were not exercised by these calls.

## EQ reply blocks

Both D1206 `V0` and D1406 `A3` read eight f32 values in little-endian order. Four cases used a known eight-value vector at offsets 0 and 3. The first eight decoded positions matched exactly. D1206 additionally sets its ninth exposed position to 12; D1406 leaves it at zero. Preserve the distinction between stored bean positions and displayed dB values.

| Case | Observed result | Implication |
| --- | --- | --- |
| D1206, 31-byte block | Default zero-valued bean returned | Validate the input length before treating a returned bean as a valid EQ reply. |
| D1406, 31-byte block | `BufferUnderflowException` | Its helper has no sufficient block-length guard; add one in an independent implementation. |
| D1406, NaN / +Infinity / +101 / -101 / +100 / -100 / +0.5 / -0.5 | First four values become zero; remaining values are preserved | Invalid/non-finite values and magnitudes above 100 are reset to zero, not clipped to ±100. |

## ANC-selection flags and short replies

A direct D1206 `b0` call with mask `0xA5` at full-frame byte 9 decoded these bits, from least significant to most significant:

| Bit | Meaning | Observed for `0xA5` |
| --- | --- | --- |
| 0 | TWS ANC selected | true |
| 1 | TWS transparency selected | false |
| 2 | TWS normal selected | true |
| 3 | TWS adaptive selected | false |
| 4 | Non-TWS ANC selected | false |
| 5 | Non-TWS transparency selected | true |
| 6 | Non-TWS normal selected | false |
| 7 | Non-TWS adaptive selected | true |

Two direct D1206 `v0` calls checked truncated input. A 15-byte input returns null; a 16-byte input passes its initial guard and throws `BufferUnderflowException`. The recovered code starts at byte 9 and performs eight byte reads, so 16 bytes are insufficient for those reads. This is an observed vendor-parser boundary error, not a requirement to reproduce it. Validate the actual payload span and framing/checksum separately. These test buffers intentionally use synthetic header/checksum placeholders and are not valid on-wire status replies.

Together the EQ/ANC cases and parametric writer comprise **13 new checks**, in addition to the button sweep and the eight earlier wrapper/button cases in [recovery evidence](APK_RECOVERY_5_0_35.md). The earlier two D1206 button inputs overlap with the broad sweep; do not sum them as distinct unique cases.

## Limits

Class/method recovery, parser behavior and packet construction remain separate from a real device accepting a command. Firmware-dependent capability and preset branches, action enumerations, connection sequencing and full state layouts are still being mapped per model. The full vendor-supported list also includes the unresolved online Things/OMC path.
