# Soundcore 5.0.35: preset labels, command positions and bean values

The recovered EQ list builders now bind **205 preset records across 16 builders**, including both branches of two normalized lists and an optional fourth entry in another list. The [machine-readable bindings](EQ_PRESET_BINDINGS_5_0_35.json) retain label resource identifiers, `EqInfo.cmdPosition`, literal position values, explicit valid counts and branch predicates. **35 independent DEX selector checks** agree with the source-derived configuration-type dispatch. These checks validate dispatch only, not execution of the list-builder bodies.

`cmdPosition` is the command position assigned by the app list. Its final per-model serialization still requires the caller/serializer path; it is not interchangeable with list row number. These values do not supply frequency, Q or filter-type tables, nor prove which presets a given firmware exposes. Callers can replace `EQConfig.eqInfoList` after constructing a config.

## Correction to the earlier literal-vector inventory

The `EQWaveValueBean` overload with nine floats followed by an integer sets nine position fields and then `validCount`. The earlier extraction grouped all numeric arguments as positions. **31 constructor records** have now been corrected to keep that integer separate. The total remains 192 literal vector records; this is a metadata correction, not a newly observed earbud behavior. The nine-float and ten-float overloads are distinct and remain supported.

## Dispatch by default EQ type

| Config type | List builder | Entries |
| --- | --- | --- |
| 1 | j | 21 |
| 4 | d | 22 |
| 5 | t | 22 |
| 6 | d | 22 |
| 7 | e | 22 |
| 8 | f | 22 |
| 9 | k | 6 |
| 10 | g | 22 |
| 11 | h | 6 |
| 12 | a | 4 |
| 13 | l | 5 when predicate true; 4 otherwise |
| 14 | m | 4 |
| 15 | o | 5 when predicate true; 4 otherwise |
| 16 | n | 5 |
| 17 | i | 5 |
| default | p | 22 |

Builder `j` starts from the default 22-entry list and removes source row 2. The remaining objects retain command positions `0, 1, 3, …, 21`; they must not be renumbered to `0…20`. Builder `b(isNewVersion)` is separate from this integer dispatch: its app command positions are 0, 19 and 20, plus 30 when the argument is true. Some callers explicitly replace the default list with this smaller list.

## Normalized list branches

| Builder / predicate | Outcome | Labels in list order | App command positions |
| --- | --- | --- | --- |
| l / A3956VersionUtils.h | true | anker_signature, clear_vocals_mode, powerful_bass, soothing_calm, eq_soundcore_balanced | 4, 1, 2, 3, 0 |
| l / A3956VersionUtils.h | false | anker_signature, clear_vocals_mode, powerful_bass, soothing_calm | 0, 1, 2, 3 |
| o / D1406VersionUtils.d | true | anker_signature, eq_soundcore_balanced, clear_vocals_mode, powerful_bass, soothing_calm | 0, 4, 1, 2, 3 |
| o / D1406VersionUtils.d | false | anker_signature, clear_vocals_mode, powerful_bass, soothing_calm | 0, 1, 2, 3 |

For builder `l`, signature is command position **4** when `A3956VersionUtils.h` is true, while balanced is position **0**. In the false branch, signature is position 0 and balanced is absent. Builder `o` inserts balanced after signature when `D1406VersionUtils.d` is true. Gain lists, label filtering and command-position arrays must follow the same predicate. A global “signature is always zero” assumption would be wrong.

The binding generator checks that gain, name and command-position counts agree in each branch. It preserves resource identifiers rather than guessing retail/translated preset names. Per-model default-type selection, explicit list replacement, sort order and the conversion of these bean values to device filters remain further work.

[Model EQ selections and normalized filter payloads](EQ_MODEL_SELECTION_5_0_35.md) adds provider/holder selection, table-role bindings, predicate checks and offline payload cases.
