# Soundcore 5.0.35: model EQ selection and filter payloads

This pass connects compiled sound-effects provider routes to their inherited holders and EQ list selectors, then binds six normalized serializer families to actual filter type/gain/frequency/Q tables. [Machine-readable evidence](EQ_MODEL_SELECTION_5_0_35.json) includes the model inventory, numeric tables and checks. No app implementation or hardware support status changes.

## Coverage and evidence

- **40 annotated provider routes** checked directly in restored DEX; **38** match the 165 compiled registry keys. One additional model-shaped route is D1102, which is not in that registry inventory; the other is the dynamic Things provider. A route alone does not establish discovery, connection or feature visibility.
- **36 holder types** resolved through inheritance and checked against source. The type selector is not necessarily the final list: providers and screens can replace it.
- **36 normalized preset records**, including conditional variants, across six serializers. Each record has complete numeric filter points, bounded by the frequency table's validCount. These are not 36 distinct device capabilities.
- **432 source/DEX getter comparisons**, **20 actual predicate checks**, and **7 restored serializer checks** passed. Version outcomes in the 432-case table sweep are supplied explicitly; the separate 20-case experiment executes the actual predicates and firmware parser.

The remaining 127 compiled keys have no explicit provider match in this particular inventory. Their older/model-specific EQ screens, fallback behavior and model routes must be traced separately; this is not evidence that they lack EQ. Parent component routes are retained separately from independent earbud EQ support.

## Provider and holder selection

These rows show the provider holder selection before screen-level replacement, sorting or state-dependent visibility. Builder letters refer to [preset bindings](EQ_PRESET_BINDINGS_5_0_35.md). Inherited routes are resolved to their actual holder getter.

| Model route | Holder | Config type | Holder-selected list | Explicit list override |
| --- | --- | --- | --- | --- |
| A3005 | A3005MutexSoundDataHolder | 0 | p | See provider/screen rules below |
| A3025 | A3025MutexSoundDataHolder | 0 | p | See provider/screen rules below |
| A3062 | A3062MutexSoundDataHolder | 0 | p | See provider/screen rules below |
| A3330 | A3330MutexSoundDataHolder | 0 | p | See provider/screen rules below |
| A3331 | A3331MutexSoundDataHolder | 4 | d | See provider/screen rules below |
| A3388 | A3388MutexSoundDataHolder | 9 | k | See provider/screen rules below |
| A3388P42 | A3388MutexSoundDataHolder | 9 | k | See provider/screen rules below |
| A3871 | A3871MutexSoundDataHolder | 4 | d | See provider/screen rules below |
| A3872 | A3872MutexSoundDataHolder | 4 | d | See provider/screen rules below |
| A3873 | A3873MutexSoundDataHolder | 4 | d | See provider/screen rules below |
| A3874 | A3874MutexSoundDataHolder | 7 | e | See provider/screen rules below |
| A3874X | A3874XMutexSoundDataHolder | 7 | e | See provider/screen rules below |
| A3875 | A3875MutexSoundDataHolder | 7 | e | See provider/screen rules below |
| A3876 | A3876MutexSoundDataHolder | 8 | f | See provider/screen rules below |
| A3878 | A3878MutexSoundDataHolder | 10 | g | See provider/screen rules below |
| A3937 | A3937MutexSoundDataHolder | 0 | p | See provider/screen rules below |
| A3954 | A3954MutexSoundDataHolder | 0 | p | See provider/screen rules below |
| A3955 | A3955MutexSoundDataHolder | 0 | p | See provider/screen rules below |
| D1204 | D1204MutexSoundDataHolder | 13 | l | See provider/screen rules below |
| D1203 | D1203MutexSoundDataHolder | 13 | l | See provider/screen rules below |
| A3957 | A3957MutexSoundDataHolder | 0 | p | See provider/screen rules below |
| A3958 | A3958MutexSoundDataHolder | 0 | p | See provider/screen rules below |
| A3959R50 | A3958MutexSoundDataHolder | 0 | p | See provider/screen rules below |
| A3959 | A3958MutexSoundDataHolder | 0 | p | See provider/screen rules below |
| A3968 | A3968MutexSoundDataHolder | 0 | p | See provider/screen rules below |
| A3994 | A3994MutexSoundDataHolder | 5 | t | See provider/screen rules below |
| A6611 | A6611MutexSoundDataHolder | 0 | p | v |
| D1101 | D1101MutexSoundDataHolder | 11 | h | See provider/screen rules below |
| D1102 (outside compiled registry) | D1105MutexSoundDataHolder | 17 | i | See provider/screen rules below |
| D1105 | D1105MutexSoundDataHolder | 17 | i | See provider/screen rules below |
| D1200 | D1200MutexSoundDataHolder | 0 | p | See provider/screen rules below |
| D1202C | D1202MutexSoundDataHolder | 0 | p | See provider/screen rules below |
| D1202 | D1202MutexSoundDataHolder | 0 | p | See provider/screen rules below |
| D1205 | D1205NormalizeMutexSoundDataHolder | 14 | m | See provider/screen rules below |
| D1206 | D1206MutexSoundDataHolder | 16 | n | See provider/screen rules below |
| D1402 | D1402MutexSoundDataHolder | 0 | p | See provider/screen rules below |
| D1404 | D1404NormalizeMutexSoundDataHolder | 15 | o | See provider/screen rules below |
| D1406 | BaseD1406NormalizeMutexSoundDataHolder | 15 | o | See provider/screen rules below |
| D1407 | BaseD1406NormalizeMutexSoundDataHolder | 15 | o | See provider/screen rules below |
| Things (dynamic) | ThingsMutexSoundDataHolder | 6 | d | See provider/screen rules below |

### Construction, replacement and sorting

`EQConfig(type, hasCustomEq, baseUpValue)` initially fills its list with builder `p` regardless of type. The synthetic constructor uses mask bits 1/2/4 to default type to 0, custom-EQ permission to true, and bass-up values to null. The default no-argument config therefore permits custom EQ; literal false arguments can be overridden by its Kotlin mask.

`DefaultEQActivity.init` replaces the list: type 1 -> `j`; type 2 -> `b(false)` through its default wrapper; type 3 -> `b(true)`; all other types -> `p`. This is specific to that older screen, and differs from `EQInfoListBuilder.r(type)` used by newer paths. Do not apply the generic type dispatcher to every activity.

The default preset-chart card obtains `EqSortUtils.c(type, 0, null, 6, null)`: its mask selects a five-entry preferred prefix and obtains cached command positions. `EqSortUtils.b` matches those positions against `EqInfo.cmdPosition`, removes matches from the remainder, and appends the remaining presets. Unknown saved positions are skipped; command positions are preserved. A3388, D1101 and the D1102 provider explicitly request a six-entry prefix. Actual preference contents were not read.

A6611 explicitly supplies builder `v`, which selects default-list source rows **0, 2, 1, 17, 15, 4** in that order. It therefore has a six-entry subset at this provider boundary, despite its holder returning type 0. Names, values and command positions of those six entries are in the JSON. A3874/A3874X/A3878 card overrides set `hasCustomEq` false; this is a screen/config property, not a universal claim that those devices cannot accept custom-EQ commands.

The inspected D1204 and base D1406 provider paths copy the selected list before handing it to `NormalizePresetEQConfig`. The D1203/D1204 and D1406/D1407 lists must use the same predicate as their corresponding serializer tables. A provider's sync/login cards do not establish whether every local feature requires account access.

## Normalized presets: command positions and filter tables

| Service | Explicit earbud/device model routes | List predicate | Command positions in UI order |
| --- | --- | --- | --- |
| A3956CmdService | D1203, D1204 | A3956VersionUtils.h false / true | 0,1,2,3 / 4,1,2,3,0 |
| D1105CmdService | D1105 | none in these tables | 0,1,2,3,30 |
| D1205CmdService | D1205 | none in these tables | 0,1,2,3 |
| D1206CmdService | D1206 | none in these tables | 0,1,2,3,4 |
| D1404CmdService | D1404 | D1406VersionUtils.d is false for D1404 | 0,1,2,3 |
| D1406CmdService | D1406, D1407 | D1406VersionUtils.d is true for those model strings | 0,4,1,2,3 |

D1203-Box and D1204-Box also reference A3956CmdService in their component routes. That does not establish independent case EQ controls. D1102 has a UI provider but no matching compiled model route here, so it is not assigned to D1105CmdService from the provider name alone.

`A3956VersionUtils.h` parses firmware after removing dots and is true only in **[348,399), [448,499), [548,599), [648,699)**. Boundary tests cover 47/48/98/99 endings in each branch. For example, 3.48 and 3.98 pass; 3.47 and 3.99 fail. No single minimum version captures this rule. Tests use valid firmware strings; malformed text and helper exception handling are outside their scope.

Despite its class name, `D1406VersionUtils.d` is a **model predicate**, true for D1406 or D1407, with no firmware comparison in that path. Its false table branch was exercised synthetically, but should not be presented as an older-firmware D1406 list. D1404 uses the same UI list builder with the predicate false and its own four-row serializer tables.

For normalized presets, `CmmBtCmdService.j5` obtains gains through `T5`, types through `V5`, center frequencies through `m`, and Q through `U5`. The frequency bean's **validCount** limits emitted points. The types remain numeric vendor values; no filter-design enum meaning is guessed. These role tables are separate from displayed chart positions.

`NormalizeEQMutexSoundHandler.V3` takes the preset object's command position, looks up its values through the holder type, and calls manager `Q5`. Manager `Q5` delegates to service `j5`. In the normalized `c6()` branch, preset role tables override those passed-in bean positions. Custom index **0xFEFE** instead builds up to eight points with type 1, frequencies 100, 200, 400, 800, 1600, 3200, 6400 and 12800 Hz, and Q 0.7. The full custom-edit/transfer path remains separate from the preset table checks.

Example: A3956CmdService signature in the false predicate branch (command 0) produces three points: `(type 1, gain 3, 30 Hz, Q 0.3)`, `(1, 1, 50 Hz, 1)`, `(2, 1.5, 7500 Hz, 0.7)`. In the true branch, signature is command 4 and balanced is command 0. All 36 records and their per-point values are in the JSON.

### Getter edge behavior

The getter sweep covers command inputs -1, 0, 1, 2, 3, 4, 5, 30 and 0xFEFE for each of four getters, six services and both supplied predicate outcomes. A3956 `T5` checks the translated row; its other three getters check the original command position before indexing the translated row. The tested valid preset positions agree, while unknown commands usually return null. Preserve this distinction when auditing additional inputs rather than assuming the helpers have identical bounds checks.

D1105 translates the sparse positions `[0,1,2,3,30]` into contiguous table rows. Its fifth preset is volume booster at command **30**, not command 4. D1406 translates commands through its own table order, which differs from UI row order when balanced is inserted.

Two D1105 records contain nonstandard role values that must be preserved as evidence: signature emits a fourth all-zero point because validCount is 4; volume booster has first-point raw type -3, gain -3, frequency -3 and Q -3. The serializer converts that type to byte **0xFD**, while retaining the negative float values. The JSON exposes both raw type and unsigned type byte and flags these records. Their firmware interpretation remains unresolved; do not infer ordinary filter semantics or silently sanitize the recovered tables. Two additional table-derived serializer cases verify these exact emissions.

## Normalized EQ payload

`CmmBtCmdService.m6` produces command prefix **08 ee 00 00 00 03 8a** with a payload of **9 + 13*N bytes**. The outer command frame/checksum is not part of this payload.

| Payload offset | Encoding | Meaning |
| --- | --- | --- |
| 0..1 | u16 little-endian | EQ command position |
| 2 | byte 0/1 | HearID enable argument |
| 3 | byte | HearID state argument |
| 4..7 | u32 little-endian bytes | HearID time argument |
| 8 | byte | Point count N |
| 9 + 13*i | byte | Filter type |
| 10 + 13*i | f32 little-endian | Gain |
| 14 + 13*i | f32 little-endian | Center frequency |
| 18 + 13*i | f32 little-endian | Q |

Seven checks execute restored `m6` and `BytesUtil.S`: five use 0, 1, 3, 4 and 8 synthetic points, and two use the D1105 signature/volume-booster getter tables. Every payload byte is compared with an independent packing implementation. They include negative and fractional gains and confirm little-endian time/float fields. Device state, point getters, command-bean setters and diagnostics are modeled. A zero-point result shows what the builder emits, not that firmware accepts it. These offline checks overlap the previously documented runtime writer checks; do not sum overlapping cases as unique protocol coverage.

## Reproduction and remaining work

External tools: `EqModelRouteProbe.java`, `EqFilterProbe.java`, `EqVersionProbe.java`, `map_eq_model_selection.py`, `write_eq_selection_docs.py`, and `verify_eq_selection.py`. Inputs are the same restored 23 DEX files, source tree, compiled registry, DEX index, prior preset bindings and constant inventory. See the [runbook](PROTOCOL_RECOVERY_RUNBOOK.md). No server, account or hardware was accessed.

Remaining work includes older/model-specific screens for the other compiled keys, complete UI visibility and capability gates, additional fixed-format and DRC serializers, custom-EQ edit/serialization rules, ordered reply and connection sequencing, and the unresolved online Things/OMC catalogue. Numeric table recovery and synthetic serialization do not establish physical compatibility.
