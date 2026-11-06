# Audio-Technica Connect protocol research

Research only, 2026-11-06. No implementation or supported-device entry was added; no Audio-Technica
hardware was used. [MODELS.md](MODELS.md) identifies 28 model definitions, including two speakers and
one hearing-assist product. [COMMANDS.md](COMMANDS.md) records the distinct command-number namespaces.

## 1. Sources and extraction

Primary source: Audio-Technica | Connect 2.8.0 (`jp.co.audiotechnica.connect`), reference XAPK SHA-256
`0c12e95c43111a3253112581521e7df77994470861108dcf07985d776b83685e`.
The base and every split were inventoried; the supplied native split is **armeabi-v7a**, not arm64.
Java sources are in `E:\Builds\research\decompiled\audiotechnica`. The app's logic is React Native,
compiled to Hermes bytecode v96 in `assets/index.android.bundle`.

The bundle was successfully decompiled with [hermes-dec](https://github.com/P1sec/hermes-dec), revision
`2e796663e1fedb93dca51060797ebf3327b3fedb`. Output is outside this repo at
`E:\Builds\research\vendor-audit-2026-11-06\audiotechnica\bundle.js`. The earlier external note saying
command bytes were inaccessible without a bytecode tool is superseded. Decompiler output is pseudocode;
its registers/closure slots and control flow need to be followed, not executed as ordinary JavaScript.
No vendor source, bytecode, native binary, or decompiler output is redistributed here.

The app supplies the evidence directly. A public-project search did not establish an independent,
hardware-validated implementation covering all four Audio-Technica families. No source code from another
headphone project was copied, and reuse of a generic chip protocol is not model confirmation.

## 2. Four families, selected by model

| ProtocolType | Models/examples | Vendor entry points |
|---|---|---|
| `GAIA_V2` | M50xBT, SPORT7TW, CKR7TW, ANC900BT, CKS5TW, CKR700BT, ANC400BT, ANC300TW, CKR70TW, SR50BT, CC500BT | `gaiav2/GaiaV2Module`, `CommandJavaModule`; JS `GaiaV2`, `buildGet...`, `parseGet...` |
| `GAIA_V3` | CKS50TW, TWX9, TWX9MK2 | `gaiav3/RNGaiaV3Module`, `lib_gaiav3_at/vendors/AtVendor`; JS `GaiaV3` |
| `AIROHA` | CKS50TW2, CC500BT2 | `airoha/RNAirohaModule`, `RNAirohaEqModule`; JS `Airoha`, `AirohaCommand` |
| `RTK` | M50xBT2, HL7BT, WB2022, TWX7, CKS30TW/+, SQ1TW2/NC, AL5NC | `rtk/RNRtkModule`, `RNRtkEqModule`, `RNRtkHsModule`; JS Realtek adapter |

Do not select a protocol from a product-name substring or Bluetooth generation. CKS50TW and CKS50TW2,
and M50xBT and M50xBT2, use different families. The model objects also carry `twMirroring`,
`needSlaveForSetting`, `multipoint`, `rejectSecondaryApp`, `supportFeatures`, `deviceHomeContents` and
firmware/build predicates. A future catalog must preserve those predicates. An interface method that
throws a not-implemented error is not proof that the product supports that setting.

## 3. Connections and wire envelopes

### GAIA v2

The app uses RFCOMM GAIA service `00001107-D102-11E1-9B23-00025B00A5A5` or standard SPP
`00001101-0000-1000-8000-00805F9B34FB`, selected from SDP; a BLE packet implementation is also present.
`CommandJavaModule` selects GaiaPacketBLE or GaiaPacketBREDR. These are different transport envelopes:
BLE has the vendor/command header; RFCOMM adds GAIA stream framing and optional checksum. Use the actual
packet class, not a BLE payload copied directly to a serial socket.

`GaiaV2Module.sendCommand` fixes the vendor to `000A`. AT command builders put a leading `00` and AT
subcommand in the payload (battery is subcommand `28`), with command-specific status/result decoding.
The legacy battery parser reads primary level/low-state then peer level/low-state. Consult
`buildGetBatteryInfo` and `parseGetBatteryInfo`; a percentage is not a voltage and primary is not always
left. The decoded outer command enum fixes AT_COMMAND to `0800` (acknowledged as `8800`); standard GAIA
EQ and information commands are separate. For example a legacy AT battery read has payload `00 28`
under vendor `000A`, command `0800`. This is an analytical builder result, not a recording.

### GAIA v3

The app registers `AtVendor`, vendor id **1560 = `0618`**. Features are basic `70`, ANC `71`, EQ `72`,
connection `73`, calls `74`, alongside Qualcomm features. Vendor ids `000A` and `0618` are not
interchangeable. `AtVendor` uses a 5000 ms command timeout by default. The Java RN bridge passes
feature id, command id and payload to `SendCommandRequest`; reply/error/notification type is separate
(`COMMAND=0`, `NOTIFICATION=1`, `RESPONSE=2`, `ERROR=3`).

| Setting | Feature | Read / write command (hex) |
|---|---|---|
| Battery | `70` | `05` |
| Codec preference | `70` | `07` / `08`; newer preference `2B` / `2C` |
| Volume balance | `70` | `0F` / `10` |
| Low latency | `70` | `11` / `12` |
| Wear detection | `70` | `16` / `17` |
| Auto play/pause | `70` | `18` / `19` |
| Key assignment settings | `70` | `2D` / `2E` |
| Ambience mode | `71` | `01` / `02` |
| Quick hear-through | `71` | `0A` / `0B`; streaming mode `0C` / `0D` |
| EQ enable, bank, parameters | `72` | `01`/`02`, `03`/`04`, `05`/`06`; save `07` |
| Sidetone | `74` | `03` / `04` |

The full table also distinguishes ANC optimization, personalization, mode cycling, EQ nickname,
assistant selection, gesture timing, codec v2 and connection-management operations. They do not all
belong in a generic read-only probe.

### Airoha

Audio-Technica uses its own service `DC7783C4-2950-4142-AD46-07A9889584D9` (`RNAirohaModule`), not JBL's
RACE service merely because both use Airoha. Its vendor command wrapper is:

```text
05 5A | (payload size + 4) u16 LE | 00 00 | feature u8 | command u8 | payload
```

The separate `sendAirohaCommand` is ordinary RACE:

```text
05 5A | (payload size + 2) u16 LE | race id u16 LE | payload
```

The wrapper sizes include the bytes after the length field. The bridge's ordinary RACE read for the
BR/EDR address is `05 5A 03 00 D5 0C 00` (private; recorded as a wire-format fact, not a recommended probe).
The custom and ordinary command paths have different response handling. The Airoha custom basic
namespace differs from GAIA v3: balance is `0B/0C`, low latency `0D/0E`, key settings `22/23`, multipoint
`2A/2B`, LE mode `3D/3E`. ANC ambience uses `00/01`. See COMMANDS.md; do not reuse a Qualcomm command id.

### Realtek

`RNRtkModule` exposes both native BBPRO requests and AT custom commands. AT custom requests are `0800`,
responses `8800`; their subcommand namespace differs again. The RN bridge dispatches other BBPRO events
separately. RNRtkModule.sendCommand builds opcode bytes `00 08` (0800 little endian), then the AT
subcommand as **two big-endian bytes**, then parameters. Its 8800 response parser reads the first two
parameter bytes as that same big-endian subcommand and passes the rest to the AT parser. This differs
from the one-byte legacy GAIA AT subcommand and the Airoha feature/command pair. `TransportLayerPacket`, `Command`, `AckPacket` and `SppTransportLayer` under
`com/realsil/sdk/bbpro/core/transportlayer` are the exact framing/ack/retry sources; do not treat an AT
subcommand byte as a whole Realtek packet.

TransportLayerPacket.encode/parse establishes the BBPRO envelope as `AA | sequence u8 | length u16 LE |
opcode u16 LE | parameters`, where length includes the opcode and total size is length + 4. Opcode zero
is ACK; this SDK exempts opcode `0301` from ACK requirements. This packet builder appends no checksum.
Preserve the ACK/status and event paths instead of assuming the `8800` event is every command's ACK.

The bundled Realtek connection layer has both standard SPP and vendor SPP
`6A24EEAB-4B65-4693-986B-3C26C352264F`. Trace the selected `ConnectionParameters`/channel type per model,
rather than assuming that the SDK's default is the app's selected service. BLE and LE Audio paths need
a separate Android-permission review before implementation.

## 4. Sound, controls and other features

EQ is not one format across these products. The app has EQ v1/v2, selectable banks, parameters,
master gain, gain-only updates and nicknames. Qualcomm v3 exposes a vendor EQ feature; Airoha's EQ
module uses the chip's PEQ/NV paths; Realtek's EqModelClient exposes entry counts, state, bank and
parameter records, with native coefficient conversion. A future implementation must follow the model's
EQ library selector and bands/ranges; slider positions and stored filter coefficients are different data.

The bytecode contains the complete model/UI route to codec preferences, volume steps and balance,
low latency, noise/ambient mode and level, talk-through, quick hear-through, auto play/pause, wear
sensitivity, ear-detection power-off timers, automatic power-off, key assignment, multi-click/force
intervals, touch/force sensitivity, sidetone, voice prompts/language/level, multipoint, LE Audio,
attachment sensing and private timers. It also contains hearing-assist scenes, leakage limiting,
call EQ and soundscape/BGM behavior on the models that offer them. Keep the per-model feature predicates;
particularly, a secondary earbud can be required for writes even when a read works on one earbud.

Concrete examples: TWX9 uses GAIA v3 and makes codec v2 conditional on build number greater than 2327.
CKS50TW2 uses Airoha, lists LE Audio among peer-sensitive settings, and has model-variant-sensitive
home contents. SQ1TW2NC is Realtek; its peer-required list includes codec, EQ, balance, keys,
talk-through, voice-pack updates and rename. Voice-pack updates remain outside an initial safe
setting implementation even when the UI exposes them.

Codec preference changes which codecs are allowed; the phone still negotiates the active codec.
Paired-device management and current media metadata have privacy/ownership implications beyond a
multipoint on/off toggle. Renaming and Fast Pair provisioning are also separate operations.

## 5. Native libraries actually opened

The armeabi-v7a split includes `libDspConfig.so`, `libnative-peq.so`, ANC libraries, hearing-aid libraries,
Hermes and React Native. The EQ libraries were extracted, exported symbols enumerated, and their JNI
coefficient entry points disassembled with capstone/pyelftools outside the repo:

| Library | SHA-256 | Entry opened |
|---|---|---|
| `libDspConfig.so` | `606a64408d54af424080178a13f86cf5dd764afb23e36d330b3effe19d12c539` | `DspConfig_calculateEq`, Thumb address `9D79` |
| `libnative-peq.so` | `bdaa9282e239de27edde7a14c912121a27d4f08859844b9301c60f87ffb1ab63` | `NativePeq_getCofeParam`, Thumb address `DA21` |

Realtek exports calculate/parse EQ, gain correction and fixed-point/filter routines. Airoha exports
PEQ coefficient and filter routines. Realtek also exposes local authentication/ECC/AES functions;
their presence alone does not establish that a given model requires them for control. These are
readable native code, not an impossibility claim. Complete the call-chain/parameter audit and compare
outputs before deciding whether PairFree's existing Airoha/Realtek math can be reused; a matching symbol
name does not prove a matching algorithm or serialization. No port or runtime test was written here.

## 6. Safety, validation and remaining work

Exclude factory/device reset, boot/shipping/battery-isolation modes, firmware and voice-pack transfer,
register writes, DSP-image writes, Fast Pair public/private-key and model-id provisioning, paired-device
removal and calibration/debug/test flows from initial control support. Avoid serial/address, peer-phone
and media metadata in public reports. A method named GET_RESET is still a reset operation.

Checked: all supplied splits; Java bridges and packet/vendor wrappers; Hermes model definitions,
command constants, representative payload builders/parsers, peer and firmware restrictions; native
exports and selected entry-point disassembly; public-project search. No vendor server was requested,
no secrets file was read, and no Bluetooth traffic was transmitted.

Further analysis is still needed for the complete per-model feature/payload/enum matrix, Realtek
transport/auth call chains, notification subscriptions, native coefficient behavior, and queued
request timing across all four families. The material is accessible locally; these are research
tasks, not blockers. Validate future codecs against the actual builders and then hardware/recordings.
The successful Hermes decompilation removes the earlier tool-access uncertainty but is not a claim
that all 28 models have been fully decoded or tested.
