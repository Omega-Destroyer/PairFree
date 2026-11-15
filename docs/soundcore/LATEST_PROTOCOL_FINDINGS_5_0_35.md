# Soundcore 5.0.35-435: recovered protocol findings

**2026-11-15 expansion:** [Per-model route evidence](MODEL_PROTOCOL_ROUTES_5_0_35.md), [control mappings](CONTROL_MAPPING_5_0_35.md) and [additional legacy layouts](LEGACY_PROTOCOL_DETAILS_5_0_35.md) now use the complete restored instruction store. In particular, legacy A3909 04/81 carries one function byte, and its HearID time is big-endian; the common-writer descriptions below must not be generalized to those legacy methods.

This document records findings from the latest recovered vendor Java, separately from 3.3/OpenSCQ30 evidence. [Recovery and virtual check limits](APK_RECOVERY_5_0_35.md) apply throughout. Source identifiers refer to the external `live-jadx/sources` tree.

## Packet construction

The checksummed path in `BaseBtDeviceCommand` takes a command prefix, appends a two-byte little-endian total frame length, payload, then an additive modulo-256 checksum over all preceding bytes. For the common seven-byte prefix, payload begins at byte 9 and total length is `payload_length + 10`. The unchecksummed path also appends a little-endian length but does not append a checksum; a null payload takes a separate path and must not be conflated with an empty array. Eight observed serialization cases are recorded in the recovery document.

`Cmm2CmdData.c` constructs a prefix beginning `08 ee 00 00 00`, followed by command set and ID. Its sibling `a` instead has `10` in prefix byte 4. Model-specific code selects between them, including D1303 branches. These are different variants; do not replace both with one hardcoded prefix.

`SendCmdDataBean` holds a prefix, a single-byte payload, an array payload, or a command prefix used without an extra payload. Its discriminator is routing metadata for the manager, not itself an extra byte transmitted to the earbuds. A seven-byte constant in the catalogue is a prefix, not an already length/checksum-complete packet.

## Newer EQ formats

The common `CmmBtCmdService.j5` selects several serialization paths through model-specific boolean overrides. The old fixed-band form is not sufficient for all current products.

For the `c6()` path, `m6` constructs command **03/8A** (prefix `08 ee 00 00 00 03 8a`) with this payload:

| Payload offset | Field | Encoding |
| --- | --- | --- |
| 0 | EQ index | u16 little-endian |
| 2 | HearID enabled | u8 boolean |
| 3 | HearID state | u8 |
| 4 | HearID time | 32-bit little-endian; confirmed with `0x12345678` in the virtual writer check |
| 8 | Filter count | u8 |
| 9 + 13*n | Filter type | u8 |
| 10 + 13*n | Gain | IEEE-754 f32 little-endian |
| 14 + 13*n | Center frequency | IEEE-754 f32 little-endian |
| 18 + 13*n | Q | IEEE-754 f32 little-endian |

Payload size is `9 + 13*filter_count`. In the custom-EQ branch, index `0xFEFE` uses up to eight points at 100, 200, 400, 800, 1600, 3200, 6400 and 12800 Hz, type 1 and Q 0.7. Named presets instead obtain gain, type, frequency and Q from distinct tables and respect the frequency vector's valid count. Treating every vector as an eight-band gain curve loses the actual filter definition.

Direct `c6() == true` overrides occur in A3956, D1105, D1205, D1206, D1404 and D1406 command services. Resolve each model's actual class route and inheritance before assigning that format. In particular D1204 uses `A3956ProductModel`, and D1203 uses `A3956XProductModel`.

A3956 tables are firmware dependent. `A3956VersionUtils.h()` selects the newer tables for parsed firmware ranges [348,399), [448,499), [548,599), [648,699). This is not a single `>=348` gate. The gain/type/frequency/Q table sets have differing valid lengths. The captured table CSV preserves field, index, values and valid count so those distinctions remain inspectable.

The common legacy `s6` path uses an EQ index followed by ten quantized position bytes for each ear, 22 payload bytes total. `CustomEqProgressHelpUtil.j` scales the first nine values by ten and uses `Math.round`; its tenth value uses an integer cast after scaling. Do not silently substitute a uniform conversion for all positions or confuse the bean's stored positions with displayed dB values.

## Buttons and cases

D1206's overridden `p4` builder uses **04/8C**, with four payload bytes: side (left=0, right=1), gesture ID, TWS function, non-TWS function. The public method accepts the non-TWS value before the TWS value, so preserving argument order on the wire would swap the two functions. Both sides were checked against the recovered method in the emulator. Gesture/action enumerations and per-model allowed choices still require caller and parser tracing.

Cases have separate registrations and profiles, such as D1203-Box and D1204-Box, including their own SPP/GATT routes. Do not inherit an earbud's transport or button map for its case. The SDK's `BleChannelProfile` constructor orders its UUID arguments as **service, read, write**. This differs from assumptions based on the numeric `7777`/`8888` names.

## Sleep and firmware-dependent commands

`D1301CmdService.s2` uses command set **15** hex, subcommand **04** when the parsed firmware is at least 160, otherwise **01**. `x5` constructs **07/8B**. These were recovered from the latest code; the firmware-dependent request was not exercised against a simulated or physical bud. It must not be generalized to other Sleep products.

## How to use the catalogues

- [Registered models](REGISTERED_MODELS_5_0_35.md): every compiled app registration, observed SPP getter and explicit routing evidence.
- [SDK profiles](SDK_PROFILES_5_0_35.md): all 14 compiled product/component profiles, channel UUIDs and explicit module composition.
- `PROTOCOL_CONSTANTS_5_0_35.csv`: runtime field names and actual constant bytes. Symbols are source references, not user-facing setting names.
- `COMMAND_BUILDERS_5_0_35.csv`: 467 declared builder methods across 51 classes; 179 have directly resolved constant prefixes. Empty prefix cells include computed prefixes, inherited work and helpers; they do not mean that a command is absent. Primitive types are types seen in a method, not an ordered or complete payload schema. No semantic meaning is assigned from obfuscated method names.
- `BUILDER_FLAGS_5_0_35.csv`: 106 constant boolean overrides. These require interpretation in the selecting caller; a bare true does not prove a particular feature.
- `EQ_TABLES_5_0_35.csv`: 408 vectors from 44 runtime static tables. Preserve gains, type, frequency and Q as separate roles, and resolve preset index and firmware selection before using them.

The catalogues cover recovered evidence, not a finished implementation specification for all settings. Next work is per-model feature/caller mapping, ordered reply schemas and version gates, full preset selection, button action enumerations, and virtual serializer/parser cases. The online Things/OMC registry remains a separate completeness requirement.

## Additional virtual checks

The [expanded virtual checks](VIRTUAL_PROTOCOL_CHECKS_5_0_35.md) cover the shared button writer across 64 concrete classes, parametric EQ serialization, D1206/D1406 float EQ decoding, ANC-selection bits and short-buffer behavior. These are synthetic serializer/parser checks, not hardware acceptance.
