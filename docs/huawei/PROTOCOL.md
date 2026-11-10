# Huawei AI Life / FreeBuds protocol research

Research only, 2026-11-06. No Huawei support was implemented and no hardware was connected.
The supplied AI Life app contains an older but readable Smart Audio plugin. Its protocol evidence and
newer OpenFreebuds research are kept distinct below; neither proves every FreeBuds model is supported.

## 1. Primary source, nested plugin, and public checks

Primary source: AI Life 11.0.2.305 (`com.huaweioverseas.smarthome`), reference APK SHA-256
`f09948106562ea930055f657a72391b58e139b3b6c1d659b326d28b772e9f5f4`.
This replaces the unrelated HUAWEI CONNECT conference app examined in the old external notes.
It is a monolithic APK with both arm64-v8a and armeabi native libraries.

Decompilation is outside the repo at
`E:\Builds\research\vendor-audit-2026-11-06\huawei\jadx`. The important discovery is
`assets/plugins/com.huawei.smartaudioplugin.jar`: it is an embedded Android plugin, not an ordinary
Java source jar. Decompiling it separately produced 3021 classes, with no jadx errors, at sibling
`plugin-jadx`. Reading only the outer app misses the headphone protocol.

Primary source locators relative to `plugin-jadx/sources`:

- `com/huawei/accessory/datatransmanager/spp/BluetoothThread` and `spp/a` (SPP data channel).
- `accessory/protocol/mbb/MbbLinkerProtocol`, `MbbProtocol`, `Command`, `CommandConfigEnum`.
- `BatteryPercent`, `BTClickInformation`, `ANCInformation`, `LanguageInformation`, `LongPressSearchResult`.
- `accessory/datatype/ModelPbiMap`, `accessory/ui/main/AccessoryDetailActivity`, and device UI packages
  `cm530`, `nile`, `mermaid`, `roc`, `walrus`.

Public check: [OpenFreebuds](https://github.com/melianmiko/OpenFreebuds), MelianMiko, revision
`0b4f14c0b500d491f0fb4ea38f2984ec334e3649`, checked locally outside the repo. Its repository carries
GPL-3.0; these notes use facts only, not its code. The main reference paths are
`openfreebuds/driver/huawei/package.py`, `constants/spp_commands.py`, `handler/`, `driver/per_model/`,
and `driver/generic/spp.py`. Some reference model handlers are explicitly tentative; their source
comments must not be turned into hardware-validation claims.

[FreeBuddies](https://github.com/IAL32/freebuddies) is a further protocol/capture reference for FreeBuds 4;
its README identifies LGPLv3 and links its SPP documentation. It was found as a lead, not used as proof
for a model-specific byte layout in this pass. Gadgetbridge Huawei is another follow-up cross-check.

## 2. Connection and model boundaries

The vendor plugin opens a secure RFCOMM socket using standard SPP
`00001101-0000-1000-8000-00805F9B34FB` (`BluetoothThread`, socket creation around line 501).
No server sign-in or app-held server key occurs in this local frame builder/control path. That finding
is limited to these local commands; it does not authorize AI Life account or cloud operations.

OpenFreebuds independently uses model-specific RFCOMM ports: its base default is 16; several newer
models override to 1. These are public reference facts, not a reason to hardcode one port for Huawei.
A future Android connection should preserve service discovery and produce a connection report, with
any channel fallback backed by a model's evidence.

The old plugin's ModelPbiMap is:

| PBI | Internal model string |
|---|---|
| `004O` | `CM-H-SHARK` |
| `ZB02` | `Walrus-AT020` |
| `ZA01` | `CM530` |
| `ZA06` | `M0001` |
| `ZA07` | `MermaidTWS` |
| `ZA04` | `Nile-CN020` |

These are vendor identifiers, not guessed marketing-name aliases. Follow the corresponding UI/device
manager before assigning a product name. The main detail activity also explicitly defaults to
FreeBuds 3. This older plugin cannot provide a modern all-model feature table merely because the
outer application is AI Life.

Reference coverage to consult for newer devices:

| OpenFreebuds model class | RFCOMM port | Useful caveat |
|---|---|---|
| FreeBuds 3 | 1 | legacy model-specific handler |
| FreeBuds 4i, 5i | 16 inherited | separate model feature lists |
| FreeBuds 6i | 1 | preset curves can be app-side custom EQ data |
| FreeBuds Pro, Pro 2, Pro 3, Pro 5 | 1 | preserve each model's ANC/EQ/gesture flags |
| FreeBuds SE | 16 inherited | no inference of ANC or EQ from family name |
| FreeBuds SE 2, SE 4 ANC | 1 | separate feature sets |
| FreeBuds Studio | 1 | over-ear behavior and battery shape differ |
| FreeLace Pro | 16 inherited | neckband-specific controls |
| FreeLace Pro 2 | 1 | separate model class |
| FreeClip 2 | 1 | open-ear; source lists untested/unsupported handlers explicitly |

Do not infer that a reference project validated every handler just because it registers one.

## 3. Frame and TLV encoding

MbbLinkerProtocol builds:

```text
5A | length u16 BE | control 00 | service u8 | command u8 | TLVs | CRC16 u16 BE
```

Length counts the control byte, service/command and TLVs; total frame size is length + 5. CRC is
CRC-16/XMODEM: polynomial `1021`, initial zero, non-reflected, no final XOR, over every preceding frame
byte including `5A` and the length. A TLV is type u8, length u8, then that many bytes. A read usually
requests a type with zero length; a write supplies its value. Multiple requested types are concatenated.
There is no extra global success byte that can be stripped from all commands; decode the relevant TLVs.

The legacy parser rejects a nonzero control byte and checks CRC. Its hex-string stream splitter keeps
an incomplete suffix for two seconds; the decompiled implementation also has suspicious string
concatenation. A future parser should implement bounded byte-stream reassembly from the documented
length and checksum, without reproducing that UI/helper behavior. Fragmented control values are not
established by this plugin's parser and must not be invented.

OpenFreebuds' framing, CRC and TLV construction agree with the vendor plugin. Its reader supports
several requested battery tags whereas the old vendor battery request only asks for tag 1. That is a
request-coverage difference, not evidence that tags 2 and 3 have another wire meaning.

## 4. Commands from the vendor plugin

All command numbers here are hex and are paired with their service byte.

| Purpose | Read / write | Payload facts |
|---|---|---|
| Version/info | `01 07` | vendor requests zero-length tags 1 through 21; reply includes private identity fields |
| Battery | `01 08`; notification `01 27` | read tag 1; response tag 1 global/minimum level, tag 2 component levels, tag 3 charging states |
| Double tap | `01 20` / `01 1F` | tag 1 left function, tag 2 right function; omit a side rather than writing a negative sentinel |
| Triple tap | `01 26` / `01 25` | same side TLVs, separate command pair |
| Voice-recognition switch | `2B 02` / `2B 01` | tag 1, scalar value on write |
| Legacy ANC switch | `2B 05` / `2B 04` | tag 1, one scalar byte in this builder |
| Legacy ANC mode/index | `2B 07` / `2B 06`; index write `2B 08` | tag 1; keep switch, mode and index distinct |
| Voice ANC | `2B 13` / `2B 12` | tag 1 |
| Long press | `2B 17` / `2B 16` | model-dependent side/action structure; follow packageLongPressSettingCmd |
| Long-press noise cycle | `2B 19` / `2B 18` | separate from choosing the current ANC mode |
| Touch enable | `01 2D` / `01 2C` | follow getTouchState / setTouchBoardState |
| Language | service `0C`, commands `02` / `01` | language-list, unit and supported-language TLVs; not a universal one-byte language code |
| Model info | `2B 0A` | vendor requests tags 1 through 5 |

`CommandConfigEnum` also uses service `2B` with touch-sensitivity `14/15`, cue-volume `16/17`, and
is-beside `18/19` write/read pairs. These overlap numerically with other model contexts (long press,
noise cycling): they are not universally interchangeable semantics. Select by actual model/UI path.

`packageSupportCmd` sends `0A 0E 00`, which is the plugin's big-data support check. Despite its generic
method name, it is **not** a safe generic settings-capability query and should not be copied into a
read-only probe. Service `09` is firmware update and service `0A` contains logs/management.

Battery reply parser: tag 1 is a single global/minimum byte; tag 2 is an array of component percentages;
tag 3 an array of charge states. OpenFreebuds maps a three-byte tag 2 to left, right, case and treats
any charge-state byte equal to 1 as charging. Keep missing/invalid component values unknown; do not
turn a signed Java byte or an absent case report into a displayed negative percentage.

## 5. Newer ANC, EQ and controls (public reference evidence)

The supplied older plugin is not the source for these newer layouts. They are OpenFreebuds facts and
must stay labeled that way until a matching vendor plugin or recording is examined.

Newer ANC uses read `2B 2A`, tags 1 and 2, and write `2B 04`, tag 1. In the reference handler, read tag 1
is **level, mode**, while write tag 1 is **mode, level**. Modes are 0 normal, 1 cancellation, 2 awareness.
Cancellation levels are 0 normal, 1 comfort, 2 ultra, and optionally 3 dynamic; awareness can distinguish
voice boost. Selecting a mode can use `FF` for unchanged/firmware-selected level. Do not copy the old
plugin's one-byte ANC write into this newer two-byte layout. Notification `2B 03` can trigger a reread.

EQ uses read `2B 4A` and write `2B 49`. Built-in selection writes tag 1 = preset id. Custom EQ writes
TLVs 1 id, 2 band count, 3 signed-byte band values, 4 UTF-8 name, 5 action; the reference reader parses
custom records from tag 8, with current preset in tag 2 and available presets in tag 3. Band counts,
storage limits, value scaling and preset ids are model-specific. For example the Pro 3 handler maps
its default to 5, whereas the generic handler knows default 1. Do not universalize those values or
copy one product's EQ curves to another. OpenFreebuds' custom curves for Symphony/Hi-Fi Live are
software-applied curves, not newly discovered firmware preset ids.

Further reference commands: auto pause `2B 11/10`; swipe `2B 1F/1E`; low latency `2B 6C`; multipoint
enable `2B 2F/2E`; device enumeration/preferred/execute/event `2B 31/32/33/36`. The last group includes
other phones' identities and active connection ownership; it is separate from exposing a benign
multipoint switch. The reference's per-model handlers also cover voice language and double/triple/
long-press choices. Preserve the offered action sets per model.

## 6. Native code and server/plugin boundaries

The outer APK's native inventory includes security/crypto/loader libraries and iperf. The headphone
packet construction and settings parsers examined above are Java in the embedded plugin. The plugin
was opened directly; there is no need to claim that a downloadable plugin blocks all protocol research.
The outer app also has plugin discovery/download and cloud/account code. No vendor endpoint was
requested. Any later attempt to obtain a newer plugin must first inspect its actual request and stop
at signing, account, certificate or device-identifier requirements under CLAUDE.md's rules.

Newer-model evidence can already be developed from the public reference and recordings. A newer vendor
plugin is additional research, not a prerequisite for documenting the readable older control stack.

## 7. Exclusions and validation

Never send firmware service `09`, log collection/download, analytics/support reporting, factory reset,
renaming, connection-legality/authentication provisioning, or other-phone connect/remove operations
from an initial control implementation. Broad version/model responses can contain addresses and serials;
select fields carefully and redact reports. Reading a vendor class does not authorize its upload code.

Checked: complete outer APK; embedded Smart Audio plugin; transport, CRC/TLV builders, battery/gesture/
ANC/language/settings parsers and model ids; native inventory; OpenFreebuds framing, commands and
per-model handlers. No app-held server credentials or secrets files were read. No hardware test was
performed and no source-derived example is represented as a capture.

Remaining analysis: map every old plugin codename to its displayed model, follow each model's UI/action
sets and overlapping command contexts, obtain a newer vendor plugin if its access is permitted, and
confirm newer EQ units/notification layouts against that app or recordings. A future implementation
needs byte-for-byte codec cases, model-specific read sets, privacy filtering and actual connection
validation. These are open research/implementation tasks; no hard-rule blocker was established for
ordinary local headphone control.
