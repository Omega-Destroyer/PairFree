# OnePlus / OPPO protocol (HeyMelody)

Virtual follow-up, 2026-11-06: [model-specific scope and interventions](VIRTUAL_TESTING.md).
No physical hardware was tested.

Research notes, 2026-11-06. A local protocol-core draft is described in [IMPLEMENTATION.md](IMPLEMENTATION.md).
No OnePlus or OPPO model is enabled in the app, and no physical device was connected.
The bundled catalog contains 85 records (53 OPPO, 32 OnePlus), including hidden entries and duplicate
marketing names. [MODELS.md](MODELS.md) preserves their selected feature and control facts. These are
research leads, not supported-device claims. Realme needs its own app/model evidence even though public
projects describe a related protocol.

## 1. Sources and reproducibility

Primary source: HeyMelody 117.6.6 (`com.heytap.headset`, version code 117006006), the APKM in the reference
folder's `One Plus` directory. Bundle SHA-256:
`c646b6f6bf86947a5c637af4eae52861dc324b974e55075e118bd158fb0427b1`.
All splits were inventoried, including both ABI splits. Their native libraries are `libglide-webp.so`
and `libsqlcipher.so`; the Bluetooth protocol is in Java, not those libraries.

Decompiled source remains in `E:\Builds\research\decompiled\oneplus`. New split extraction and decoded
catalog remain in `E:\Builds\research\vendor-audit-2026-11-06\oneplus`. Important source locators:

- `F8/b` (TLVDataProcessor), `G8/b` (OPOv1Wrapper), `p224p8/a` (Packet), `p224p8/b` (packet factory).
- `com/oplus/melody/btsdk/protocol/commands/l` (PollCommandManager), `j` (notifications), `y` (set response
  handling), `z` (upgrade processing); actual writes are largely in `multidevice/HeadsetCoreService`.
- `p026b8/a` (Protocol), its 69 capability-bit entries and always-allowed command set.
- `api/data/KeyFunctionInfo`, `CurrentNoiseModeInfo`, `NoiseReductionInfo`, `EqInfo`, and the named feature
  data classes. Android Intent/message numbers in BluetoothService are not wire command numbers.
- `WhitelistConfigDTO.Function`, whitelist repository, and `common/util/U` (RepackagingDetector).

The old external findings dated 2026-11-03 misidentified the bundled-table encryption as the local cache
and mislabeled parts of the opening exchange. The source checks below supersede those statements.

Public checks: [Gadgetbridge's OPPO documentation](https://gadgetbridge.org/gadgets/headphones/oppo/)
and [TouchLock](https://github.com/sarvesh-official/TouchLock) describe OPOv1 and real device use.
Gadgetbridge is AGPL-3.0: use protocol facts only. No code from either project was incorporated.
TouchLock's fixed RFCOMM channel is a report about its devices/platform; do not replace SDP with that
number for every model. No vendor-app-versus-public-project disagreement was established in this pass.

## 2. Transport and opening

The bundled entries all set `supportSpp` true and specify RFCOMM service
`0000079A-D102-11E1-9B23-00025B00A5A5`. The SDK also has a GATT path using that service, write
`0100079A-D102-11E1-9B23-00025B00A5A5` and notify `0200079A-D102-11E1-9B23-00025B00A5A5`, plus an extended
079C path. Keep transport selection separate from model identification. BLE scanning is a future
permission decision; this research does not grant PairFree a scan permission for this brand.

PollCommandManager sends these negotiation operations; the exact scheduling is in HeadsetCoreService:

| Command | Meaning | Request payload |
|---|---|---|
| `0100` | Remote capabilities | empty |
| `0101` | Remote MTU exchange | `00 02` (512, little endian) |
| `0102` | Vendor id exchange | `9A 07` (1946); the official app uses `FF FF` on its OPPO path |
| `0103` | Product id | empty |
| `0104` | Remote protocol version | empty |
| `0105` | Remote firmware/version list | empty |
| `0106` | Battery | empty |
| `0107` | Upgrade capabilities (excluded from initial probe) | empty |

There is no key/challenge in this negotiation. Do not confuse the vendor id exchange with server
credentials or Fast Pair account keys. The product-id parser takes three little-endian bytes after
a success byte and applies specific legacy-id remappings before matching the catalog.

## 3. Frames, replies, and capabilities

The inner packet is:

```text
command u16 LE | sequence u8 | payload length u16 LE | payload
```

The response-command comparison masks bit 15; the request key combines masked command and sequence.
A normal response begins its payload with a status byte (zero success); individual parsers then check
lengths and unpack the result. Preserve sequence matching rather than treating every notification as
the answer to the currently pending request.

OPOv1 wraps it as:

```text
AA | remaining length (7-bit little-endian varint) | control | reserved | inner packet
```

For an unfragmented packet the control's low two bits are zero; the parser skips both control and
reserved bytes. A fragmented packet has an extra fragment byte; low control bits distinguish fragments
and `3` ends the logical packet. The SDK separately reassembles MTU chunks and logical fragments. Do not
assume one RFCOMM read or GATT notification is a packet. No checksum is appended by this wrapper.

Analytical example only, not a hardware capture: a capability read with sequence zero and empty payload
has inner `00 01 00 00 00`, outer `AA 07 00 00 00 01 00 00 00`.

[CAPABILITIES.md](CAPABILITIES.md) preserves all 69 bit-to-command expansions as protocol facts.
The capability response has a success byte followed by least-significant-bit-first feature bits; at most
69 are decoded in this build. A set bit expands into the commands in `p026b8/a.f14745a`; one bit can permit
several reads and writes. The unconditional set is `0100 0101 0102 0103 0104 0106 010B 010D 0F00 0F03 0F04`.
**Unconditional means SDK dispatch permits it, not that it is safe to send**: the `0Fxx` commands are debug.
The catalog determines which features the UI offers; the remote capability set determines which commands
the SDK permits. A future implementation needs both, plus a successful read of state before changing it.

The battery response is status, count, then count two-byte records: device type and packed level.
Level is the low seven bits; bit 7 means charging (`BatteryInfo`, `CommandUtil.d`). The app applies
optional per-model rounding (`batteryRadix`), distinct from the raw wire value; do not apply that
rounding to case/sentinel values without following its conditions.

## 4. Controls and feature payloads

| Feature | Read / write evidence | Encoding notes |
|---|---|---|
| Key assignments | read `0108`, write `0401` or model-selected alternate | count, then four-byte records: device/ear type, button, gesture, function; write is followed by `0108` |
| Typed noise queries | `010C` | request pairs include `01 01`, `02 01`, `02 03`, `02 04`, `03 01`, `04 01`; retain their parser distinctions |
| Noise configuration | `0404` | action/type/value or CurrentNoiseModeInfo layout, not one universal ANC byte |
| Shared switches | read `010D`, write `0403` | read: count then requested feature ids; write: feature id then 0/1 |
| EQ preset | write `0406` | one model-specific preset/mode byte |
| Custom EQ | capability bit 34: read `0122`, write `0418` | structured EQ record described below |
| Personalized ANC | `011A` / `0412` | write one action byte; do not apply it to a model with no feature |
| Spatial type | `012A` / `0422` | write one type byte; model lists allowed spatial types |
| LE Audio action | `0424` | two bytes; `0433` is a separate LE/BR switch, one boolean byte |
| Head motion | `0134` / `0431` | type enum; do not assume it is a boolean |
| Find | `0400` | one boolean byte; audible operation, never part of an automatic probe |

`CurrentNoiseModeInfo` uses type 1 or 5 for a variable-width mode bitmap, type 2 for a level. Its write
begins with action `01`, then type and either bitmap bytes or the level. `NoiseReductionInfo` is action,
type, then a minimally sized 1..4-byte little-endian value. Preserve model-specific nested
`noiseReductionMode.protocolIndex` and `childrenMode` values: newer Enco and Buds Pro models have a
hierarchy; older Free2/Air2 families have different indices. A label such as off or adaptive is not a
brand-wide number.

For shared switches, PollCommandManager selects feature ids from catalog flags, including hearing
enhancement, personalized noise, game/zen-related features, multipoint, recording, high sound quality and
sleep detection. Do not poll every numerical id merely because `010D` permits a list.

Touch `control` and `callControl` entries in MODELS.md retain ear, action, show-ear choice and support
mask. These masks enumerate possible functions; they are not bytes that can be written directly.
KeyFunctionInfo supplies the wire record; UI enum/mask translation must also be followed.

## 5. Sound and remaining app features

Custom EQ `0418` is operation, signed minimum, signed maximum, EQ id, UTF-8 name-byte count and name,
band count, then each frequency as u16 LE and signed gain byte. HeadsetCoreService rejects missing bands
or unequal frequency/gain counts. Follow `EqInfo` and the UI's gain scaling before implementing; a byte
should not be assumed to mean one whole dB. Preset selection is a separate operation. Some newer model
entries explicitly list ten frequencies `[31,62,125,250,500,1000,2000,4000,8000,16000]`; other entries do
not, so do not copy that curve to every model.

The app also contains bass engine, gaming sound, voice prompt volume, automatic volume, pinch/tap
sensitivity, wear detection, fit testing, hearing enhancement/ear scan, multi-device control, zen/white
noise, spatial audio, head motion, AI/assistant controls, and model-specific codec choices. The selected
catalog facts are in MODELS.md; wire entry points are in HeadsetCoreService and their `api/data` classes.
Features involving host camera actions, assistants, health analytics or downloads need phone-side work
as well as an earbud setting; their existence does not justify contacting a vendor server.

## 6. Bundled catalog, native code, and servers

The bundled whitelist is AES-GCM (12-byte nonce, 128-bit tag) around gzip-compressed JSON. Its AES key is
derived by the app from its signing certificate hash (`common/util/U`), whereas `whitelistSecretKey` is
randomly generated for a separate on-phone cache. The bundled file was authenticated and decoded locally;
no account or server was needed. The decoded file and all decryption material remain outside this repo.
Its `compatWhiteList` holds 85 records; `whiteList` starts empty and the app's compatibility conversion
fills the current representation. Preserve parent/child variants and firmware predicates when later
making runtime data; the selected documentation does not replace that conversion.

The native splits contain image/database dependencies, not a hidden Bluetooth encoder. Server/cloud
code exists, but the model/settings evidence used here is local. No vendor endpoint was requested.

## 7. Exclusions, validation, and work remaining

Never include debug/diagnostic `0Fxx` operations, upgrade/file-transfer flows, resets, account-key writes,
serial-number reads, or control of other paired phones in the initial setting allowlist. In particular,
HeadsetCoreService has `setAccountKey`; it is not required by the negotiation above. Identity and paired
phone replies must not enter public reports. Find, hearing tests and sound playback require deliberate
user actions. No operation was sent during this research.

Checked: every split's library inventory; bundled model data with authenticated decryption; both frame
layers; capability table; noise, touch and EQ data classes and write builders; public protocol checks.
The original jadx output skips PollCommandManager's large response dispatcher. Simple mode still
reported inference errors, so it was recovered successfully in fallback instruction mode at
`oneplus/PollCommandManager-fallback.txt/l.java` under the external audit folder (1659 dispatcher lines).
The recovered switch pins replies `8100` capabilities, `8101` MTU, `8102` vendor id, `8103` product id,
`8104` protocol version, `8105` versions, `8106` battery, `8107` upgrade capability, `8108` keys, `8109`
in-ear status, `810B` color id, `810C` typed noise, and `810D` shared switches. This avoids guessing from
method names. The complete remaining feature branches still need semantic review.

Before implementing, finish reviewing each response branch and offered feature's enum/predicate mapping,
review the preserved 69-bit table against each feature parser, derive analytical vectors from the actual builders, and then
confirm behavior with recordings/hardware. The catalog's hidden entries are not release promises. No
PairFree hardware validation, runtime model entries, scan authorization, or supported-device pages were
added by this research.

## 12. Hearing enhancement test (Golden Sound), read and built 2026-11-11

From HeyMelody (`model/repository/hearingenhance`, `p189mc/k0.java` the detecting screen, `p189mc/J.java` the result screen, `HeadsetCoreService`). Models with
feature 11 (`hearingEnhancement`) among their switches offer it on the Sound tab (`OneplusSession.hearingTest`, `OneplusHearingTest`). **The earbuds play the tones.**

| Command | Payload |
|---|---|
| `040D` (1037) | `2, 1` detection on; `2, 0` detection off |
| `040E` (1038) | a tone: `3, 1, device, type, dB` (device 1 left or 2 right, type 1 to 6 low to high, dB a signed byte); pause: `4`; audition on: `1, count, points, id (int, big-endian), name`; audition off: `2`; save: `3, count, points (3 bytes each), id, name` |
| `0403` | `11, 1` the enhancement switch (HeyMelody's generic switch) |
| `0411` (1041) | restore data: a count, then `type, length (2 bytes), data`; type 1 holds a byte for which of 20 wordings the app showed; PairFree does not send it |
| `0415` (1045) | the ear scan: mode, then for modes 1 and 3 a little-endian length, the scan data and an int; not built |
| `0116` (278) | request of the filter data: count, points, id; the reply is the curves the app draws; not built |

The test: 12 points, the left earbud's six types then the right one's. The slider has 12 positions, `-55, -49, -41, -35, -25, -19, -11, -5, 3, 7, 13, 17` dB, starting at position 5
(-19 dB); each move sends that point's tone; Next pauses (`040E 4`) and goes on; when all are done the app sends the audition or save form with a random id and the
date as the name, and the switch. The earbuds work out the enhancement from the points; the app only draws the curves it asks for with `0116`. The 5-byte tone form and the
save form share mode 3 and differ in length, as HeyMelody builds them. The ear scan that comes first on models with `earScan` (`0415`) is optional in the app (it
saves the data only when it has it) and is left out. Unit tests (`OneplusHearingTestTest`) and invented replies only; no OnePlus earbuds, and the earbuds' replies are
not read.
