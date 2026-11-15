# Soundcore 5.0.35: control factories and action identifiers

**Follow-up:** [Evaluated gesture/state rules](CONTROL_GESTURE_RULES_5_0_35.md) now distinguish menu membership by gesture, TWS and predicate outcome, with additional concrete firmware checks. Gesture visibility and hardware acceptance remain separate.

The restored `DataSourceUtil.c` string-switch was interpreted directly because its Java decompilation has broken branches. All 165 compiled keys were evaluated with `ThingsModelUtil.h(productCode) = false`. A separate check with that predicate true selected `ThingsDataSource`; an unknown code selected `BaseDataSourceImpl`. This checks factory dispatch only, with no Android constructor/UI or Bluetooth execution.

The 38 resulting sources and their conditional action/gesture references are in [CONTROL_EVIDENCE_5_0_35.json](CONTROL_EVIDENCE_5_0_35.json). A default source is not proof that its screen is reachable or supported. Candidate action sets retain all branches and still require gesture, firmware, model and device-state evaluation.

## Gesture IDs

`ClickType` explicitly defines: single tap 2, double tap 0, triple tap 5, long press 1, slide up 6 and slide down 7. Call mute uses `MuteTab = 2`, sharing the single-tap number in a different context. These enum values do not establish which gestures each device exposes.

## Normal custom-control function IDs

These are `DeviceInfoUtil.t` results for explicitly present protocol identifiers. Identifiers that merely return the default 15 are excluded.

| Protocol identifier | Value |
| --- | --- |
| CUSTOM_ANC_SWITCH | 8 |
| CUSTOM_BTN_ASK_ANSWER | 11 |
| CUSTOM_BTN_NEXT | 3 |
| CUSTOM_BTN_NONE | 15 |
| CUSTOM_BTN_PLAY_PAUSE | 6 |
| CUSTOM_BTN_PREVIOUS | 2 |
| CUSTOM_BTN_RECORDING | 7 |
| CUSTOM_BTN_SIRI | 5 |
| CUSTOM_BTN_START_SLEEP | 14 |
| CUSTOM_BTN_TRANS | 4 |
| CUSTOM_BTN_TRANSLATE | 10 |
| CUSTOM_BTN_VOLUME_ADD | 0 |
| CUSTOM_BTN_VOLUME_REDUCE | 1 |
| CUSTOM_COLOR_FUL_LIGHT | 14 |
| CUSTOM_SWITCH_MODE | 13 |
| CUSTOM_TAKE_PHOTO | 10 |
| CUSTOM_WHITE_NOISE_SWITCH | 19 |

Translation and taking a photo both map to 10; colorful light and starting sleep both map to 14. Interpretation depends on the selected control source and context. A single global action enum would lose this distinction. `CUSTOM_BTN_GAME` and the two case quick-action identifiers are not explicit branches in this normal-control mapper; its fallback 15 must not be documented as their supported encoding.

## Call-control function IDs

| Protocol identifier | Value |
| --- | --- |
| CUSTOM_CALL_BTN_ANSWER | 1 |
| CUSTOM_CALL_BTN_DECLINE | 0 |
| CUSTOM_CALL_BTN_MUTE_NONE | 15 |
| CUSTOM_CALL_BTN_MUTE_SINGLE_TAP | 2 |

## Compiled source selections

Aliases are listed together only when the actual factory returns the same class. These are not aliases inferred from retail names. The large default group is listed in the JSON and the per-key route matrix.

| Selected source | Keys | Normal-choice candidate fields |
| --- | --- | --- |
| A3330DataSource | A3330 | btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus |
| A3331DataSource | A3331 | btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus |
| A3388DataSource | A3388, A3388P42 | btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnRecording, btnSiri, btnVolumeAdd, btnVolumeMinus |
| A3871DataSource | A3871 | btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus |
| A3872DataSource | A3872 | btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus |
| A3873DataSource | A3873 | btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus |
| A3874DataSource | A3874 | btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus |
| A3874XDataSource | A3874X | btnAskAnswer, btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnTranslate, btnVolumeAdd, btnVolumeMinus |
| A3875DataSource | A3875 | btnAnc, btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus |
| A3876DataSource | A3876 | btnColorfulLight, btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus |
| A3878DataSource | A3878 | btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus |
| A3927DataSource | A3927, A3927EU | btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus |
| A3937DataSource | A3937 | btnAnc, btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus |
| A3943DataSource | A3943 | btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus |
| A3944DataSource | A3944, A3944EU | btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus |
| A3947DataSource | A3947 | btnAnc, btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus |
| A3948DataSource | A3948, A3948A25 | btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus |
| A3949DataSource | A3949, A3949P25, A3949R50, A3969 | btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus |
| A3954DataSource | A3954 | btnAnc, btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus |
| A3955DataSource | A3955 | btnAnc, btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus |
| A3956DataSource | D1203, D1204 | btnAnc, btnAskAnswer, btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnRecording, btnSiri, btnTranslate, btnVolumeAdd, btnVolumeMinus |
| A3957DataSource | A3957 | btnAnc, btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnTakePhoto, btnVolumeAdd, btnVolumeMinus |
| A3958DataSource | A3958 | btnAnc, btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus |
| A3959DataSource | A3959, A3959R50 | btnAnc, btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus |
| A3968DataSource | A3968 | btnAnc, btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus |
| A3994DataSource | A3994 | btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus |
| A6611DataSource | A6611 | btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSwitchMode, btnVolumeAdd, btnVolumeMinus |
| D1101DataSource | D1101 | btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus |
| D1102DataSource | D1102 | btnAskAnswer, btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnRecording, btnSiri, btnTranslate, btnVolumeAdd, btnVolumeMinus |
| D1105DataSource | D1105 | btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus, btnWhiteNoise |
| D1200DataSource | D1200 | btnAnc, btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus |
| D1202DataSource | D1202, D1202C | btnAnc, btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus |
| D1205DataSource | D1205 | btnAnc, btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSiri, btnVolumeAdd, btnVolumeMinus |
| D1206DataSource | D1206 | btnAnc, btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnRecording, btnSiri, btnVolumeAdd, btnVolumeMinus |
| D1301DataSource | D1301, D1301S | btnAncSwitch, btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSwitchMode, btnVolumeAdd, btnVolumeMinus |
| D1302DataSource | D1302 | btnAncSwitch, btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSwitchMode, btnVolumeAdd, btnVolumeMinus |
| D1303DataSource | D1303 | btnAncSwitch, btnMusicNext, btnMusicPlayPause, btnMusicPre, btnNone, btnSwitchMode, btnVolumeAdd, btnVolumeMinus |

Field names in the last column are Kotlin/source labels matched back to original DEX field references. They are candidate choices across branches, not a verified action list for every gesture. Inline choice constructors and version-helper calls are separately recorded in JSON; their full conditions still require analysis. In particular, recording-related choices can be gated by audio-note support and must not be enabled just because `btnRecording` occurs.

The new factory/action evaluation is offline bytecode interpretation. It does not change any device to a physically tested or packet-tested category.
