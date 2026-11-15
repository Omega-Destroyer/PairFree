# Soundcore 5.0.35: additional legacy wire details

These findings come from the fully restored DEX and its callers. Six bounded checks interpreted the actual serializer instructions with synthetic values. No Android app, Bluetooth connection or transport write was executed. [Exact cases](LEGACY_PAYLOAD_CASES_5_0_35.json) distinguish five payload checks from one complete-frame check.

## A3909 manager family

The compiled A3909, A3910 and A3913 model registrations select `A3909DeviceManager`. That is routing evidence, not proof that every model exposes all these functions. Their UI/model and firmware conditions still apply.

### Button assignment: 04/81

`A3909DeviceManager.K0(boolean left, int gesture, int function)` supplies `A3909CmdConstants.K` and three payload bytes:

| Offset | Value |
| --- | --- |
| 0 | left = 0, right = 1 |
| 1 | gesture argument, reduced to u8 |
| 2 | one function argument, reduced to u8 |

`A3909CustomButtonFragment` obtains the last argument from `A3909CustomButtonVM.getCommonCustomizeBtnCmdValue`, which delegates to `A3909CustomButtonM`. Direct bytecode evaluation confirms explicit volume up=0, volume down=1, previous=2, next=3, ambient=4, assistant=5 and play/pause=6 mappings for the corresponding common protocol identifiers. Its default is 0; that default is not a supported encoding for unknown actions.

This legacy writer does **not** pack separate TWS/non-TWS function nibbles. The newer common `CmmBtCmdService.p4` also uses 04/81 but packs two function values. The command number alone cannot select the payload semantics.

Synthetic checks: left/gesture 2/function 6 produced `00 02 06`; right/gesture 1/function 5 produced `01 01 05`. These gesture inputs test serialization and do not assert that those gestures are offered on every family member.

`L0(left, gesture, enabled)` uses **04/83**, with side, gesture and enabled (0/1) bytes. The enabled left/gesture 2 case produced `00 02 01`.

### EQ: 02/81

`O0(index, leftBean, rightBean)` supplies `A3909CmdConstants.E` and **18 payload bytes**:

| Offset | Field |
| --- | --- |
| 0–1 | index, u16 little-endian |
| 2–9 | left bean positions 0 through 7, reduced to u8 |
| 10–17 | right bean positions 0 through 7, reduced to u8 |

Positions 8 and 9 are not serialized by this method. The bean's stored positions must not be equated with displayed dB without tracing their construction. Input index `0x1234`, left positions 0…7 and right positions 8…15 produced `34 12 00 01 02 03 04 05 06 07 08 09 0a 0b 0c 0d 0e 0f`.

### HearID: 03/82

`S0(gender, ageRange, leftBean, rightBean, testTime)` supplies `A3909CmdConstants.H` and **22 payload bytes**. Argument meanings are established by `A3909EqVM.setHearIdDetail` and `A3909HearIdDataVM.setCmdHearIdDetails`.

| Offset | Field |
| --- | --- |
| 0 | gender, u8 |
| 1 | age range, u8 |
| 2–9 | left positions 0 through 7 |
| 10–17 | right positions 0 through 7 |
| 18–21 | testTime, 32-bit **big-endian** |

`CustomEqProgressHelpUtil.h` casts each floating bean position to an integer; it does not multiply by ten or round. `BytesUtil.R` emits the four time bytes most significant first. The caller casts the model's test time to int; no timestamp unit is inferred here. This differs from the newer parametric 03/8A writer's little-endian time field.

The interpreted test uses fractional and negative bean values to distinguish integer truncation from rounding, plus time `0x12345678` to establish byte order. The final four observed bytes were `12 34 56 78`.

## Flare / Flare+ direct SPP framing

`A3161SppLink.c(header, body)` allocates `header.length + 2 + body.length`, writes the total length little-endian after the header, then copies the body. It adds **no checksum**. Its direct request constants can already contain a nine-byte header with length bytes; retain the distinction between those complete constants and the seven-byte headers used by `c`.

The original DEX framer, given header `08 ee 00 00 00 01 87` and body `2a`, returned the complete ten-byte frame `08 ee 00 00 00 01 87 0a 00 2a`. No extra checksum byte was appended. `A3161MainActivity.initSpplink` is the common entry for A3161 and A3162, even though their model manager getters return null.

## Check implementation and limits

External tools are `per-model-analysis/LegacyPayloadProbe.java`, `ControlFactoryProbe.java`, `DexProtocolIndex.java` and `legacy-payload-cases.json` under the research root. The interpreter runs the restored method instructions, including integer operations, byte-array writes and branches. Synthetic beans replace Android/device state. `BytesUtil.R` and `CustomEqProgressHelpUtil.h` run through their restored instructions; `BytesUtil.Q` is modeled as its verified low-byte cast. Array copying uses the JDK implementation.

For the five A3909 cases, the packet wrapper is intercepted to capture prefix and payload before sending; those checks do not verify complete frame bytes, checksums, acknowledgements or device acceptance. The Flare case runs its complete pure framer but does not call its SPP sink. The interpreters are bounded subsets that stop on unsupported operations, not general Android emulators.
