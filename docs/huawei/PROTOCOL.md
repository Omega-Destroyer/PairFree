# Huawei AI Life / FreeBuds protocol research

Virtual follow-up, 2026-11-06: [model-specific scope and interventions](VIRTUAL_TESTING.md).
No physical hardware was tested.

The [implementation](IMPLEMENTATION.md) runs through the Android and desktop shared driver.
The [model audit](MODELS.md) records the APK routes, supplementary captures and outstanding features.

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
its captured T0022/T0022C model is marketed as FreeBuds Pro 4. [Captured protocol](https://github.com/IAL32/freebuddies/blob/main/app/README.md) ? [Huawei T0022 guide](https://consumer.huawei.com/cn/support/content/zh-cn16025581/)

## 2. Connection and model boundaries

The vendor plugin opens a secure RFCOMM socket using standard SPP
`00001101-0000-1000-8000-00805F9B34FB` (`BluetoothThread`, socket creation around line 501).
No server sign-in or app-held server key occurs in this local frame builder/control path. That finding
is limited to these local commands; it does not authorize AI Life account or cloud operations.

OpenFreebuds independently uses model-specific RFCOMM ports: its base default is 16; several newer
models override to 1. These are public reference facts, not a reason to hardcode one port for Huawei.
PairFree uses the operating system's standard SPP service resolution and records the chosen service in its connection report.

The old plugin's ModelPbiMap follows. See [MODELS.md](MODELS.md) for the marketed identities and outer-app entries.

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

OpenFreebuds reference coverage, before the supplemental sources in [MODELS.md](MODELS.md):

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

The legacy parser rejects a nonzero control byte and checks CRC. PairFree reassembles the byte stream using a 4096-byte body limit. Fragmented control values are not
established by this plugin's parser and must not be invented.

OpenFreebuds' framing, CRC and TLV construction agree with the vendor plugin. Its reader supports
several requested battery tags whereas the old vendor battery request only asks for tag 1. That is a
request-coverage difference, not evidence that tags 2 and 3 have another wire meaning.

## 4. Commands from the vendor plugin

All command numbers here are hex and are paired with their service byte.

| Purpose | Read / write | Payload facts |
|---|---|---|
| Version/info | `01 07` | PairFree requests software version tag 7. The vendor's broader query can return private identities |
| Battery | `01 08`; notification `01 27` | read tag 1; response tag 1 global/minimum level, tag 2 component levels, tag 3 charging states |
| Double tap | `01 20` / `01 1F` | tag 1 left function, tag 2 right function; omit a side rather than writing a negative sentinel |
| Triple tap | `01 26` / `01 25` | same side TLVs, separate command pair |
| Voice-recognition switch | `2B 02` / `2B 01` | tag 1, scalar value on write |
| Legacy ANC switch | `2B 05` / `2B 04` | tag 1, one scalar byte in this builder |
| Legacy ANC mode/index | `2B 07` / `2B 06`; index write `2B 08` | tag 1; keep switch, mode and index distinct |
| Voice ANC | `2B 13` / `2B 12` | tag 1 |
| Long press | `2B 17` / `2B 16` | model-dependent side/action structure; follow packageLongPressSettingCmd |
| Long-press noise cycle | `2B 19` / `2B 18` | separate from choosing the current ANC mode |
| Touch enable | `01 2D` / `01 2C` | Studio's read body is 01 01 with no value byte. Read reply and write carry tag 1 with one byte, 0 locked and 1 enabled |
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


## 8. Model-specific command details

| Feature | Read / write | Payload |
| --- | --- | --- |
| Walrus double tap | 01 20 / 01 1F | Read tags 1, 2, 3. Tags 1 and 2 address the sides. Use tag 3's advertised list. Both 2 and 4 mean next track. Both 7 and 8 mean previous track. |
| Walrus long press | 2B 17 / 2B 16 | Read tags 1, 2, 3. The vendor writes tag 2. Values 3, 5, 6 and 9 select combinations of off, ANC and awareness. FF disables the gesture. Use the reported list. |
| Nile shared hold | 2B 17 / 2B 16 | Read tags 1, 2, 3. Prefer the shared setting in reply tag 2 as the APK does, with tag 1 as a fallback for later firmware. Write tag 1. Codes 3, 5, 6, 9 and FF match the Walrus meanings. |
| Nile ANC | 2B 05 or 2B 2A / 2B 04 | Both use mode IDs 0, 1, 2. The former uses the scalar write. The latter uses mode and level. |
| Eyewear battery | 01 08 | Request tags 1, 2, 3. Tag 2 holds the two temple levels and case. AI Life displays the smaller temple level. The first generation displays case battery. |
| Eyewear language | 0C 02 / 0C 01 | Read tags 1, 2, 3. Change to a locale from the reported list with tag 1 alone. |
| Eyewear and Eyewear II double tap | 01 20 / 01 1F | Request tag 3. Each side uses its tag 1/2. Actions 0 assistant, 1 play/pause, FF none. |
| Eyewear II swipe | 2B 1F / 2B 1E | Side tags 1/2. Actions 0 volume, 1 track, FF none. |
| Eyewear II short press | 2B 21 / 2B 20 | Tag 1. Actions 0 face-to-face translation, 1 voice memo, 2 pairing, FF none. |
| Eyewear II call hold | 2B 17 / 2B 16 | Request tags 1, 2 and 3. Left tag 1 uses 9 reject call and FF none. |
| Eyewear and Eyewear II switches | 2B 0F/0E, 11/10 | Boolean tag 1 for greeting and wear detection. |
| Eyewear II switches | 2B 1D/1C, 23/22 | Boolean tag 1 for power saving and intelligent volume. |
| Modern pinch | 2B 93 / 2B 92 | Tags 1, 2 identify gesture and scene. Side actions are tags 3, 4. Call and playback writes address both sides together. Hold addresses one side. |
| Modern keyed settings | 2B B4 | Tag 1 key, tag 2 state. Keys 2 adaptive volume, 5 single-ear ANC, 7 drop detection, 11 head control and 27 conversation awareness. |
| Adaptive awareness on Pro 5 | 2B 2A / 2B 04 | Mode 2, submode 4. Read the slider in tag 2. The slider write carries [2,4] in tag 1, value 1 in tag 3 and level 0?10 in tag 4. |
| Head gestures | 2B B4 | For key 11, tags 3 and 4 hold nod and shake actions. Values 0 none, 1 answer and 2 reject. |
| Sound quality | 2B A3 / 2B A2 | Request tag 1. Read tag 1 capability and tag 2 state. Write tag 1, 0 connectivity or 1 quality. |
| Ring earbuds | 2B 5E / 2B 5D | The read carries side 0 or 1 in tag 1 and the reply carries side and state in tag 2. Write side and 0 ring or 1 stop in tag 1. |

Studio's double-tap, hold and slide cards animate the built-in controls. Its quality screen reads and sets the phone's Bluetooth A2DP codec preference. In the FreeBuds Pro plugin, the light-press and swipe cards are tutorials, while the hold card writes 10 for noise control or 0 for voice assistant to the selected side. Modern firmware can expose additional controls.

The outer APK's Eyewear builders and parsers are in com/huawei/perrier/ota/spp. See ProtocolAPI,
C1484 and AppParseDataHandleHelper for battery, language and settings, and fiji/touchsettings
for gesture choices. Both generations also expose double tap, greeting and wear detection through
their respective DeviceSettingActivity routes. The device-backed wear path is explicit even
when the outer app also updates a Huawei phone preference. Eyewear II's HD recording uses the
phone's Bluetooth metadata API under key 10011.

PairFree requests only tag 2 of 2B 4A. Some firmware, including the captured FreeBuds 5,
requires the single-tag query. Replies can carry the current ID in tag 2, available IDs in tag 3,
and tag 8 custom records. Each record is 36 bytes with slot ID, band count, ten signed gains and
a 24-byte name field. The implemented EQ grid is 60, 125, 250, 500, 1000, 2000, 4000, 8000, 12000
and 16000 Hz, ?6 dB in signed tenths. The custom write order is 1, 2, 5, 3, 4, with operation 1
for the save profiles and 0 for FreeBuds 7i. Pro 3 preset IDs use the published 02 03 05 09 response.
[LibreBuds protocol and captures](https://github.com/librebuds/librebuds/tree/c7b0833) ? [HuaweiPods EQ codec](https://github.com/Nshpiter/HuaweiPods/blob/e2cf07e/app/src/main/java/moe/chenxy/huaweipods/pods/HuaweiEqualizerCodec.kt)

## 9. Supplied APK scope and gaps

Checked the outer APK's manifests, DEX files, local product profiles and the embedded Smart Audio
JAR. The active audio screens reach their settings through the Java MBB builders or the phone
APIs below. The ARM64 and ARMv7 dynamic symbol tables show no audio-control JNI exports. The two
nativeloader binaries lack dynamic symbol sections, and the audited settings paths call no native
loader methods. The reference folder supplies AI Life 11.0.2.305 as one complete APK.

| Area | What the APK establishes |
| --- | --- |
| FreeGO | Cm530DetailActivity, LuteDetailPresenter and lute_activity_main show battery and connection state. The action panel offers firmware and help. AccessoryDeviceSettingActivity is shared device management. The bundle provides no speaker EQ, power mode or sound-mode selector for CM530. |
| Wear detection on Walrus, Roc and Mermaid | The model wear activities call the Huawei phone class BtTwsWearDetectionEx. A portable MBB alternative for Walrus and Mermaid needs firmware evidence. The Studio profile has an independent wear-control reference. |
| Roc sound quality | RocQualityModeModel reads BluetoothA2dp.getCodecStatus and writes setCodecConfigPreference on the host. The app trace gives no MBB equivalent for Studio. |
| HD recording | MermaidHDRecordingActivity calls accessory.utils.ag. Both it and Fiji's HDRecordActivity use phone Bluetooth metadata 10011. A portable device write needs external evidence. |
| Finding | The Studio and FreeBuds Pro panels open the Huawei find-my-phone app. PairFree's independent ring support for newer products uses a separate local command. |
| Mermaid's archived smart-noise UI | The plugin manifest names three Mermaid noise activities whose class descriptors are absent from the outer and plugin DEX files. BaseNoiseControlActivity and BaseHeadsetSwitchActivity remain in the DEX without a reachable product-panel entry. The presenter forwards the smart strength and voice fields to BaseAudioDevice's empty three-argument method. Capture evidence is needed before giving those values a wire layout. |
| Eyewear preferences | A fixed voice language is an MBB write. Follow system is an AI Life preference that sends the phone's locale on reconnect. The main screen also sends its clock as device-management command 01 05 with epoch seconds and signed UTC offset. These are app-managed behaviors. |
| Later audio panels | The supplied bundle has no device route for the later fit-test, spatial audio, case-tone, multi-device centre or extended EQ panels. Their model matrices remain open for newer vendor artifacts and captures. |


See [the model audit](MODELS.md) for remaining features and [implementation](IMPLEMENTATION.md)
for PairFree validation.

## 10. The newer settings (2026-11-28)

From LibreBuds (c7b0833) and HuaweiPods (e2cf07e), both GPL-3.0-or-later, from their authors' captures and probes. None was tried with PairFree.
Numbers are in `data/huawei/commands.json`; frames in `data/huawei/test_vectors.json`.

| Setting | Read / write | Payload |
|---|---|---|
| Rest reminders | `2B 61` / `2B 60` | tag 1, 0 or 1 |
| HD calls | `2B 46` / `2B 45` | the read declares one byte for tag 1 and carries none; write tag 1 |
| Pickup mode | `2B 42` / `2B 41` | read as HD calls; 0 surroundings, 1 voices |
| Case prompt sounds | `2B B1` / `2B B1` | read tags 2 and 3, state in tag 2; write tag 1 |
| Volume boost in noise | `2B 88` / `2B 87` | tag 1 |
| Keyed settings | `2B B4` | tag 1 key: 3 voice control, 8 ear tip type (1 silicone, 2 memory foam), 24 spatial audio (tag 2: 0 off, 1 head tracking, 2 fixed; tag 3 the scene: 0 default, 1 theater, 2 cinema, 3 concert hall), 29 wind noise reduction |
| Delete a saved sound | `2B 49` | the slot's own record with operation 2 |

**Paired devices** (models with two devices at once): `2B 31` with an empty tag 1 answers one packet per host: tag 2 how many, tag 3 this one's index,
tag 4 the address (least significant byte first), tag 5 connected (9 while playing), tag 7 preferred, tag 8 connects automatically, tag 9 the name.
`2B 32` tag 1 = address sets the preferred device. `2B 33` acts on a device: the tag is the action (1 connect, 2 disconnect, 4 and 5 automatic
connection on and off), the value its address. `2B 36` says the list changed. Action 3 (unpair) is never sent. The list stays out of the device report.

**Software sounds.** Symphony, Hi-Fi Live and the HUAWEI SOUND Classical sound are curves the maker's app writes as a custom slot; PairFree does the same.

## 11. What the bundled app does not send

Its protocol class builds commands for voice recognition (`2B 01`/`02`), voice noise control (`2B 12`/`13`), touch sensitivity, cue volume and "is
beside" (`CommandConfigEnum`, which reuses `2B 14` to `2B 19`), the legacy mode write (`2B 06`) and the clock (`01 05`): none has a caller in the
plugin, so PairFree sends none (`data/unused_ids.json`). `2B 0A` reads the model, whose reply may hold serial numbers: not asked.

## 12. What is never sent

Factory reset (`01 0D`), connection legality (`01 13`), every command of the update service (`09`) and of the log service (`0A`), unpairing a device,
and anything not on the session's own closed list (`HuaweiTalk.allowed`).

## 13. The device report

Only the answers to PairFree's own reads, by command: of the version reply the version number alone, custom equalizer slots with their names blanked,
and never the paired devices' list. A catalogue model, and a newer FreeBuds, FreeClip, FreeLace or FreeArc the table lacks, is asked the settings'
reads only, for its report; nothing is shown or written.

## 14. Huawei's server (research, 2026-11-27)

The app's product list and device plugins come from Huawei's guide server (`smarthome.hicloud.com/device/guide/`), which serves them to a plain `GET`
with no token or signature. A research script outside the repo (`E:/Builds/research/huawei-tools/fetch_guide.py`) fetched the app's notification feed,
list version and `mainHelp.json` (five headsets and two glasses, none newer than the bundled list). It stopped for good when `config.json`, a path the
app requests, answered 403 (an earlier 403 was a path the app does not use). Raw replies are in `server_dump/huawei/`, not committed. The newer
plugins were not fetched (open: data/completeness.json, `huawei-current-product-scope`).

