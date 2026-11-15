# Soundcore 5.0.35: evaluated control-choice rules

This evaluates the normal-action choice-list method `k(CustomControlItemBean)` for all **38 data sources** selected by the compiled control factory. **912 cases** cover six requested gesture types, TWS false/true and predicate-result false/true. Another **432 cases** execute the actual version helpers for four model codes and nine firmware strings. Exact results and abstraction boundaries are in [CONTROL_CHOICE_CASES_5_0_35.json](CONTROL_CHOICE_CASES_5_0_35.json).

**A requested gesture is a synthetic input, not evidence that a product offers that gesture.** Menu membership is checked here; navigation, gesture visibility, remapping packet encoding and hardware acceptance are separate. The 115-key default factory group remains a fallback, not a claim of remapping support.

## What was executed and modeled

The bounded interpreter executes the restored list-building branches, enum switches and Kotlin collection calls. Enum field identities, ordinals and command values are checked against the actual enum constructor inputs. The base `g0` initializer is represented by named action objects: this experiment tests which objects enter the menu, not their descriptions, submenus or transmitted numeric fields.

TWS and gesture are explicit synthetic state. In the 912-case sweep, the six encountered eligibility/version predicates are modeled at their result boundary; both outcomes are exercised. These booleans are not invented firmware values or measured device capabilities. In the 432-case sweep, version helpers and their StringUtils parsing execute through restored DEX, while unrelated eligibility predicates remain false. Only the nine listed firmware strings were tested; malformed firmware text and exception paths were not exercised. No preference contents, account data or vendor servers were accessed.

## Model and firmware distinctions

A3954 volume choices use a model check and two firmware branches. After `StringUtils.h` removes dots and parses the integer, an odd hundreds digit uses threshold 327, while an even hundreds digit uses threshold 427. Thus 3.99 passes but 4.00 does not. Slide-up and slide-down menu functions separately return volume up/none and volume down/none even when that volume-expansion gate is false; this does not prove the slides are exposed.

A3957 photo choices use the parsed last firmware component >=63 **or** first component >8, with a separate D1200 model exception in the helper. This must not be simplified to a single numeric minimum firmware. The D1200 exception does not itself prove that D1200 selects the photo menu.

| Firmware test | A3954 single-tap menu includes volume choices | A3957 single-tap menu includes photo |
| --- | --- | --- |
| 3.26 | False | False |
| 3.27 | True | False |
| 3.99 | True | True |
| 4.00 | False | False |
| 4.26 | False | False |
| 4.27 | True | False |
| 8.62 | True | False |
| 8.63 | True | True |
| 9.00 | True | True |

Other directly evaluated distinctions include: A3331 hold has volume/assistant/none choices; A3875 includes the ambient action only with TWS true; D1105 triple-tap choice-list returns only `btnWhiteNoise`; D1102 normal lists include AI/translation/recording choices for some gesture requests. These are source-menu results, not proof that the UI exposes each gesture.

Recording predicates differ: A3388 requires AI Recap support and nonempty cached feature-switch data; A3956/D1206 additionally consult the merged AudioNote device-support lists. Dynamic list membership remains an input that must be established for an actual device.

## Per-source menus

Action names below are source field labels, and order is preserved. “Predicate” means the specific call named under that source, not a universal feature flag. Sources with identical list results can still have different visibility or eligibility rules.

### A3330DataSource

Keys: A3330.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab, DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnVolumeAdd, btnVolumeMinus, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnNone |

### A3331DataSource

Keys: A3331.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab, DoubleTab, TripleTab, SlideUp, SlideDown | all tested states | btnVolumeAdd, btnVolumeMinus, btnMusicPlayPause, btnMusicNext, btnMusicPre, btnSiri, btnNone |
| LongPress | all tested states | btnVolumeAdd, btnVolumeMinus, btnSiri, btnNone |

### A3388DataSource

Keys: A3388, A3388P42.

Predicate calls: `Lcom/soundcore/control/datasource/A3388DataSource;->D0()Z`.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab, DoubleTab, LongPress, SlideUp, SlideDown | all tested states | btnMusicNext, btnMusicPre, btnMusicPlayPause, btnVolumeAdd, btnVolumeMinus, btnSiri, btnNone |
| TripleTab | predicate=false | btnMusicNext, btnMusicPre, btnMusicPlayPause, btnVolumeAdd, btnVolumeMinus, btnSiri, btnNone |
| TripleTab | predicate=true | btnMusicNext, btnMusicPre, btnMusicPlayPause, btnVolumeAdd, btnVolumeMinus, btnSiri, btnRecording, btnNone |

### A3871DataSource

Keys: A3871.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnNone |
| DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnNone |

### A3872DataSource

Keys: A3872.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnNone |
| DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnNone |

### A3873DataSource

Keys: A3873.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnNone |
| DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnNone |

### A3874DataSource

Keys: A3874.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnNone |
| DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnNone |

### A3874XDataSource

Keys: A3874X.

Predicate calls: `Lcom/oceanwing/devicecmd/manager/product/a3874/A3874VersionUtils;->c()Z`.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnNone |
| DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | predicate=false | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnNone |
| DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | predicate=true | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnTranslate, btnAskAnswer, btnNone |

### A3875DataSource

Keys: A3875.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab, DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | TWS=false | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnNone |
| SingleTab, DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | TWS=true | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnAnc, btnNone |

### A3876DataSource

Keys: A3876.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnNone |
| DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnColorfulLight, btnNone |

### A3878DataSource

Keys: A3878.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnNone |
| DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnNone |

### A3927DataSource

Keys: A3927, A3927EU.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab, DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicPlayPause, btnMusicNext, btnMusicPre, btnSiri, btnNone |

### A3937DataSource

Keys: A3937.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnNone |
| DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnAnc, btnNone |

### A3943DataSource

Keys: A3943.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab, DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnNone |

### A3944DataSource

Keys: A3944, A3944EU.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab, DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnNone |

### A3947DataSource

Keys: A3947.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnNone |
| DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnAnc, btnNone |

### A3948DataSource

Keys: A3948, A3948A25.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab, DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicPlayPause, btnMusicNext, btnMusicPre, btnSiri, btnNone |

### A3949DataSource

Keys: A3949, A3949P25, A3949R50, A3969.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab, DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicPlayPause, btnMusicNext, btnMusicPre, btnSiri, btnNone |

### A3954DataSource

Keys: A3954.

Predicate calls: `Lcom/oceanwing/devicecmd/manager/product/a3954/A3954VersionUtils;->c()Z`.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab | predicate=false | btnMusicPlayPause, btnMusicNext, btnMusicPre, btnNone |
| SingleTab | predicate=true | btnMusicPlayPause, btnVolumeAdd, btnVolumeMinus, btnMusicNext, btnMusicPre, btnNone |
| DoubleTab, TripleTab, LongPress | predicate=false | btnMusicPlayPause, btnMusicNext, btnMusicPre, btnSiri, btnAnc, btnNone |
| DoubleTab, TripleTab, LongPress | predicate=true | btnMusicPlayPause, btnVolumeAdd, btnVolumeMinus, btnMusicNext, btnMusicPre, btnSiri, btnAnc, btnNone |
| SlideUp | all tested states | btnVolumeAdd, btnNone |
| SlideDown | all tested states | btnVolumeMinus, btnNone |

### A3955DataSource

Keys: A3955.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnNone |
| DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnAnc, btnNone |

### A3956DataSource

Keys: D1203, D1204.

Predicate calls: `Lcom/soundcore/control/datasource/A3956DataSource;->E0()Z`.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnNone |
| DoubleTab, LongPress, SlideUp, SlideDown | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnAskAnswer, btnSiri, btnTranslate, btnAnc, btnNone |
| TripleTab | predicate=false | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnAskAnswer, btnSiri, btnTranslate, btnAnc, btnNone |
| TripleTab | predicate=true | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnAskAnswer, btnSiri, btnTranslate, btnAnc, btnRecording, btnNone |

### A3957DataSource

Keys: A3957.

Predicate calls: `Lcom/oceanwing/devicecmd/manager/product/a3957/A3957VersionUtils;->c()Z`.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab | predicate=false | btnMusicPlayPause, btnMusicNext, btnMusicPre, btnVolumeAdd, btnVolumeMinus, btnNone |
| SingleTab | predicate=true | btnMusicPlayPause, btnMusicNext, btnMusicPre, btnVolumeAdd, btnVolumeMinus, btnTakePhoto, btnNone |
| DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnMusicPlayPause, btnMusicNext, btnMusicPre, btnSiri, btnAnc, btnVolumeAdd, btnVolumeMinus, btnNone |

### A3958DataSource

Keys: A3958.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnNone |
| DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnAnc, btnNone |

### A3959DataSource

Keys: A3959, A3959R50.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnNone |
| DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnAnc, btnNone |

### A3968DataSource

Keys: A3968.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnNone |
| DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnAnc, btnNone |

### A3994DataSource

Keys: A3994.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnNone |
| DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnNone |

### A6611DataSource

Keys: A6611.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab, DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnSwitchMode, btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnNone |

### BaseDataSourceImpl

Factory fallback group; see the per-key matrix.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab, DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicPlayPause, btnMusicNext, btnMusicPre, btnSiri, btnAnc, btnNone |

### D1101DataSource

Keys: D1101.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab | all tested states | btnVolumeAdd, btnVolumeMinus, btnMusicPlayPause, btnMusicNext, btnMusicPre, btnSiri, btnNone |
| DoubleTab, TripleTab, SlideUp, SlideDown | all tested states | btnMusicPlayPause, btnMusicNext, btnMusicPre, btnSiri, btnNone |
| LongPress | all tested states | btnVolumeAdd, btnVolumeMinus, btnSiri, btnNone |

### D1102DataSource

Keys: D1102.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab | all tested states | btnVolumeAdd, btnVolumeMinus, btnMusicPlayPause, btnMusicNext, btnMusicPre, btnNone |
| DoubleTab, TripleTab | all tested states | btnVolumeAdd, btnVolumeMinus, btnMusicPlayPause, btnMusicNext, btnMusicPre, btnSiri, btnAskAnswer, btnTranslate, btnRecording, btnNone |
| LongPress | all tested states | btnVolumeAdd, btnVolumeMinus, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnAskAnswer, btnTranslate, btnRecording, btnNone |
| SlideUp, SlideDown | all tested states | btnVolumeAdd, btnVolumeMinus, btnMusicPlayPause, btnMusicNext, btnMusicPre, btnSiri, btnNone |

### D1105DataSource

Keys: D1105.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab | all tested states | btnVolumeAdd, btnVolumeMinus, btnMusicPlayPause, btnMusicNext, btnMusicPre, btnNone |
| DoubleTab, SlideUp, SlideDown | all tested states | btnVolumeAdd, btnVolumeMinus, btnMusicPlayPause, btnMusicNext, btnMusicPre, btnSiri, btnNone |
| TripleTab | all tested states | btnWhiteNoise |
| LongPress | all tested states | btnVolumeAdd, btnVolumeMinus, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnNone |

### D1200DataSource

Keys: D1200.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnNone |
| DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnAnc, btnNone |

### D1202DataSource

Keys: D1202, D1202C.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnNone |
| DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnAnc, btnNone |

### D1205DataSource

Keys: D1205.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnNone |
| DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnAnc, btnNone |

### D1206DataSource

Keys: D1206.

Predicate calls: `Lcom/soundcore/control/datasource/D1206DataSource;->E0()Z`.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnNone |
| DoubleTab, LongPress, SlideUp, SlideDown | all tested states | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnAnc, btnNone |
| TripleTab | predicate=false | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnAnc, btnNone |
| TripleTab | predicate=true | btnVolumeMinus, btnVolumeAdd, btnMusicNext, btnMusicPre, btnMusicPlayPause, btnSiri, btnAnc, btnRecording, btnNone |

### D1301DataSource

Keys: D1301, D1301S.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab, DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnAncSwitch, btnSwitchMode, btnVolumeAdd, btnVolumeMinus, btnMusicPlayPause, btnMusicNext, btnMusicPre, btnNone |

### D1302DataSource

Keys: D1302.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab, DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnAncSwitch, btnSwitchMode, btnVolumeAdd, btnVolumeMinus, btnMusicPlayPause, btnMusicNext, btnMusicPre, btnNone |

### D1303DataSource

Keys: D1303.

| Gesture request | Tested condition | Choice labels |
| --- | --- | --- |
| SingleTab, DoubleTab, TripleTab, LongPress, SlideUp, SlideDown | all tested states | btnAncSwitch, btnSwitchMode, btnVolumeAdd, btnVolumeMinus, btnMusicPlayPause, btnMusicNext, btnMusicPre, btnNone |
