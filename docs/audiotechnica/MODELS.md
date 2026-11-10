# Audio-Technica model and transport facts

Research only, 2026-11-06. Source: Connect 2.8.0, Hermes bytecode v96 decompiled with hermes-dec (revision in [PROTOCOL.md](PROTOCOL.md)). These are model definitions, not PairFree support. Source line numbers refer to the external `audiotechnica/bundle.js` produced in this audit; original function/module names are stronger locators across tool versions.

| Model | ProtocolType | External decompilation line |
|---|---|---|
| ATH-AL5NC | `RTK` | 397142 |
| ATH-TWX7 | `RTK` | 712308 |
| ATH-CKR7TW | `GAIA_V2` | 713151 |
| ATH-SR50BT | `GAIA_V2` | 713305 |
| ATH-SQ1TW2NC | `RTK` | 815677 |
| ATMM-SHD1 | `RTK` | 816711 |
| ATH-TWX9MK2 | `GAIA_V3` | 817413 |
| ATH-CC500BT2 | `AIROHA` | 818019 |
| ATH-CKS50TW2 | `AIROHA` | 818847 |
| ATH-CKS30TW+ | `RTK` | 819931 |
| ATH-SQ1TW2 | `RTK` | 821074 |
| ATH-WB2022 | `RTK` | 821547 |
| ATH-CKS30TW | `RTK` | 822273 |
| ATH-CC500BT | `GAIA_V2` | 822748 |
| ATH-TWX9 | `GAIA_V3` | 823134 |
| ATH-HL7BT | `RTK` | 825293 |
| ATH-CKS50TW | `GAIA_V3` | 826047 |
| ATH-M50xBT2 | `RTK` | 827980 |
| ATH-CKR70TW | `GAIA_V2` | 828481 |
| ATH-ANC300TW | `GAIA_V2` | 828905 |
| ATH-ANC400BT | `GAIA_V2` | 829136 |
| ATH-CKR700BT | `GAIA_V2` | 829354 |
| ATH-CKS5TW | `GAIA_V2` | 829633 |
| ATH-ANC900BT | `GAIA_V2` | 829785 |
| ATH-M50xBT | `GAIA_V2` | 829945 |
| ATH-SPORT7TW | `GAIA_V2` | 830210 |
| AT-SBS50BT | `GAIA_V2` | 830348 |
| AT-SBS70BT | `GAIA_V2` | 830440 |

RTK is Realtek, AIROHA uses vendor-specific RACE framing, and GAIA_V2 and GAIA_V3 are distinct command sets. The AT-SBS products are speakers and stay future work; ATMM-SHD1 is a hearing-assist product and needs its own scope decision. `needSlaveForSetting`, `twMirroring`, `rejectSecondaryApp`, per-model `supportFeatures` functions, `deviceHomeContents` and firmware/build predicates must be carried into a future model table, not replaced with a single brand-wide feature list.

Examples pinned in the bytecode: TWX9 uses GAIA_V3 and requires the peer for codec, EQ and balance changes; CKS50TW2 uses AIROHA and lists peer requirements for attachment, name, call ANC, EQ, key assignment, voice prompts, balance and LE Audio. SQ1TW2NC uses RTK and lists codec, EQ, balance, key assignment, talk-through, voice packs and name among peer-sensitive settings. Some methods present in a protocol interface throw an unimplemented-method error; their mere presence is not support evidence.

## Per-model predicate and peer-setting index

These are named predicates present in each model definition, with simple constant returns distinguished from conditional logic. This is a source-navigation index, not a flattened runtime capability table: conditional returns must still be evaluated with the model firmware/build and application context. Names describing UI behavior are not necessarily on-device settings.

### ATH-AL5NC (RTK)

Peer-sensitive category list was not recovered as a simple local list; follow the original definition.

| Predicate/function | Result classification |
|---|---|
| `batteryRoundFunc` | conditional / inspect body |
| `autoConnectionForegroundIos` | true |
| `codecSetting` | true |
| `codecTypes` | conditional / inspect body |
| `lowLatencyUnsupportedCodec` | conditional / inspect body |
| `noiseCanceling` | true |
| `ambienceModeCycle` | true |
| `keyFullAssign` | true |
| `keyAssignType` | conditional / inspect body |
| `eqV2NeedReconnect` | false |
| `limitSoundLeakage` | conditional / inspect body |
| `multipointManagement` | true |
| `budsTimer` | true |
| `leAudio` | conditional / inspect body |

### ATH-TWX7 (RTK)

Peer-sensitive setting categories: `Equalizer`, `VolumeBalance`, `KeyAssign`, `TalkThrough`, `VpUpdate`, `ChangeName`.

| Predicate/function | Result classification |
|---|---|
| `batteryRoundFunc` | conditional / inspect body |
| `autoConnectionForegroundIos` | true |
| `codecSetting` | true |
| `codecTypes` | conditional / inspect body |
| `lowLatencyUnsupportedCodec` | conditional / inspect body |
| `noiseCanceling` | true |
| `keyFullAssign` | true |
| `keyAssignType` | conditional / inspect body |
| `ambienceModeCycle` | true |
| `touchRegister` | conditional / inspect body |
| `eqV2NeedReconnect` | conditional / inspect body |
| `multipointManagement` | true |
| `budsTimer` | conditional / inspect body |

### ATH-CKR7TW (GAIA_V2)

Peer-sensitive category list was not recovered as a simple local list; follow the original definition.

| Predicate/function | Result classification |
|---|---|
| `codecSetting` | true |
| `codecTypes` | conditional / inspect body |

### ATH-SR50BT (GAIA_V2)

Peer-sensitive category list was not recovered as a simple local list; follow the original definition.

| Predicate/function | Result classification |
|---|---|
| `codecSetting` | true |
| `codecTypes` | conditional / inspect body |
| `noiseCanceling` | true |

### ATH-SQ1TW2NC (RTK)

Peer-sensitive setting categories: `Codec`, `Equalizer`, `VolumeBalance`, `KeyAssign`, `TalkThrough`, `VpUpdate`, `ChangeName`.

| Predicate/function | Result classification |
|---|---|
| `batteryRoundFunc` | conditional / inspect body |
| `autoConnectionForegroundIos` | true |
| `codecSetting` | false |
| `codecTypes` | conditional / inspect body |
| `ambienceModeCycle` | true |
| `noiseCanceling` | true |
| `keyFullAssign` | true |
| `keyAssignType` | conditional / inspect body |
| `touchRegister` | conditional / inspect body |
| `multipointManagement` | true |
| `sidetone` | true |
| `budsTimer` | true |

### ATMM-SHD1 (RTK)

Peer-sensitive setting categories: `Equalizer`, `VolumeBalance`, `KeyAssign`, `EarDetection`, `ChangeName`, `HsScenes`, `HsSceneParams`, `HsNoiseCancelling`.

| Predicate/function | Result classification |
|---|---|
| `batteryRoundFunc` | conditional / inspect body |
| `autoConnectionForegroundIos` | true |
| `codecSetting` | false |
| `codecTypes` | conditional / inspect body |
| `keyFullAssign` | true |
| `keyAssignType` | conditional / inspect body |
| `eq` | conditional / inspect body |

### ATH-TWX9MK2 (GAIA_V3)

Peer-sensitive setting categories: `Codec`, `Equalizer`, `VolumeBalance`.

| Predicate/function | Result classification |
|---|---|
| `autoConnectionForegroundIos` | true |
| `codecSetting` | true |
| `codecSettingV2` | true |
| `codecTypes` | conditional / inspect body |
| `hiddenCodecSetting` | conditional / inspect body |
| `ambienceModeCycle` | true |
| `noiseCanceling` | true |
| `keyFullAssign` | true |
| `keyAssignType` | conditional / inspect body |
| `multipointManagement` | true |
| `sidetone` | true |

### ATH-CC500BT2 (AIROHA)

Peer-sensitive category list was not recovered as a simple local list; follow the original definition.

| Predicate/function | Result classification |
|---|---|
| `codecSetting` | conditional / inspect body |
| `codecSettingV2` | true |
| `codecTypes` | conditional / inspect body |
| `eq` | true |
| `leAudio` | conditional / inspect body |

### ATH-CKS50TW2 (AIROHA)

Peer-sensitive setting categories: `BudsAttach`, `ChangeName`, `CallNoiseCancelling`, `Equalizer`, `KeyAssign`, `VpSwitch`, `VolumeBalance`, `LeAudio`.

| Predicate/function | Result classification |
|---|---|
| `codecSetting` | conditional / inspect body |
| `codecSettingV2` | true |
| `codecTypes` | conditional / inspect body |
| `ambienceModeCycle` | true |
| `noiseCanceling` | true |
| `keyFullAssign` | true |
| `keyAssignType` | conditional / inspect body |
| `multipointManagement` | true |
| `leAudio` | conditional / inspect body |
| `budsTimer` | true |
| `budsTimerSoundLabels` | conditional / inspect body |

### ATH-CKS30TW+ (RTK)

Peer-sensitive setting categories: `Codec`, `Equalizer`, `VolumeBalance`, `KeyAssign`, `TalkThrough`, `VpUpdate`, `ChangeName`.

| Predicate/function | Result classification |
|---|---|
| `autoConnectionForegroundIos` | true |
| `codecSetting` | conditional / inspect body |
| `codecTypes` | conditional / inspect body |
| `ambienceModeCycle` | true |
| `noiseCanceling` | true |
| `keyFullAssign` | true |
| `keyAssignType` | conditional / inspect body |
| `touchRegister` | conditional / inspect body |
| `eq` | true |
| `multipointManagement` | true |
| `sidetone` | true |

### ATH-SQ1TW2 (RTK)

Peer-sensitive setting categories: `Codec`, `Equalizer`, `VolumeBalance`, `KeyAssign`, `TalkThrough`, `VpUpdate`, `ChangeName`.

| Predicate/function | Result classification |
|---|---|
| `batteryRoundFunc` | conditional / inspect body |
| `autoConnectionForegroundIos` | true |
| `codecSetting` | false |
| `codecTypes` | conditional / inspect body |
| `keyFullAssign` | true |
| `keyAssignType` | conditional / inspect body |
| `touchRegister` | conditional / inspect body |
| `multipointManagement` | true |
| `sidetone` | true |
| `budsTimer` | conditional / inspect body |

### ATH-WB2022 (RTK)

Peer-sensitive category list was not recovered as a simple local list; follow the original definition.

| Predicate/function | Result classification |
|---|---|
| `batteryRoundFunc` | conditional / inspect body |
| `codecSetting` | true |
| `codecTypes` | conditional / inspect body |
| `hiddenCodecSetting` | conditional / inspect body |
| `lowLatencyUnsupportedCodec` | conditional / inspect body |
| `eqV2NeedReconnect` | true |

### ATH-CKS30TW (RTK)

Peer-sensitive setting categories: `Codec`, `Equalizer`, `VolumeBalance`, `KeyAssign`, `TalkThrough`, `VpUpdate`, `ChangeName`.

| Predicate/function | Result classification |
|---|---|
| `autoConnectionForegroundIos` | true |
| `codecSetting` | conditional / inspect body |
| `codecTypes` | conditional / inspect body |
| `noiseCanceling` | false |
| `keyFullAssign` | true |
| `keyAssignType` | conditional / inspect body |
| `touchRegister` | conditional / inspect body |
| `eq` | conditional / inspect body |
| `multipointManagement` | true |
| `sidetone` | true |

### ATH-CC500BT (GAIA_V2)

Peer-sensitive category list was not recovered as a simple local list; follow the original definition.

| Predicate/function | Result classification |
|---|---|
| `autoConnectionForegroundIos` | true |
| `codecSetting` | conditional / inspect body |
| `codecTypes` | conditional / inspect body |

### ATH-TWX9 (GAIA_V3)

Peer-sensitive setting categories: `Codec`, `Equalizer`, `VolumeBalance`.

| Predicate/function | Result classification |
|---|---|
| `autoConnectionForegroundIos` | true |
| `codecSetting` | true |
| `codecSettingV2` | conditional / inspect body |
| `codecTypes` | conditional / inspect body |
| `hiddenCodecSetting` | conditional / inspect body |
| `ambienceModeCycle` | conditional / inspect body |
| `noiseCanceling` | true |
| `keyFullAssign` | conditional / inspect body |
| `keyAssignPattern` | conditional / inspect body |
| `keyAssignType` | conditional / inspect body |
| `voiceAssistantKeySetting` | conditional / inspect body |
| `voiceAssistantSettingOptions` | conditional / inspect body |
| `voiceAssistantDefaultImage` | conditional / inspect body |
| `voiceAssistantDefaultString` | conditional / inspect body |
| `multipointManagement` | conditional / inspect body |
| `sidetone` | conditional / inspect body |

### ATH-HL7BT (RTK)

Peer-sensitive category list was not recovered as a simple local list; follow the original definition.

| Predicate/function | Result classification |
|---|---|
| `codecSetting` | conditional / inspect body |
| `codecTypes` | conditional / inspect body |
| `lowLatencyUnsupportedCodec` | conditional / inspect body |
| `eqV2NeedReconnect` | conditional / inspect body |

### ATH-CKS50TW (GAIA_V3)

Peer-sensitive setting categories: `Codec`, `Equalizer`, `VolumeBalance`.

| Predicate/function | Result classification |
|---|---|
| `autoConnectionForegroundIos` | true |
| `codecSetting` | true |
| `codecSettingV2` | conditional / inspect body |
| `codecTypes` | conditional / inspect body |
| `hiddenCodecSetting` | conditional / inspect body |
| `ambienceModeCycle` | conditional / inspect body |
| `noiseCanceling` | true |
| `keyFullAssign` | conditional / inspect body |
| `keyAssignType` | conditional / inspect body |
| `voiceAssistantKeySetting` | conditional / inspect body |
| `multipointManagement` | true |
| `sidetone` | conditional / inspect body |

### ATH-M50xBT2 (RTK)

Peer-sensitive category list was not recovered as a simple local list; follow the original definition.

| Predicate/function | Result classification |
|---|---|
| `codecSetting` | true |
| `codecTypes` | conditional / inspect body |
| `lowLatencyUnsupportedCodec` | conditional / inspect body |
| `voiceAssistantSettingOptions` | conditional / inspect body |
| `voiceAssistantDefaultString` | conditional / inspect body |
| `voiceAssistantDefaultImage` | conditional / inspect body |
| `eq` | conditional / inspect body |
| `eqV2NeedReconnect` | conditional / inspect body |
| `sidetone` | conditional / inspect body |

### ATH-CKR70TW (GAIA_V2)

Peer-sensitive setting categories: `Ambience`, `Codec`, `Equalizer`, `VolumeBalance`.

| Predicate/function | Result classification |
|---|---|
| `keyAssignType` | conditional / inspect body |
| `codecSetting` | true |
| `codecTypes` | conditional / inspect body |
| `noiseCanceling` | true |

### ATH-ANC300TW (GAIA_V2)

Peer-sensitive setting categories: `Ambience`, `Codec`, `KeyAssign`, `VpType`, `AutoPowerOff`, `ChangeName`.

| Predicate/function | Result classification |
|---|---|
| `codecSetting` | true |
| `codecTypes` | conditional / inspect body |
| `noiseCanceling` | true |

### ATH-ANC400BT (GAIA_V2)

Peer-sensitive category list was not recovered as a simple local list; follow the original definition.

| Predicate/function | Result classification |
|---|---|
| `voiceAssistantSettingOptions` | conditional / inspect body |
| `voiceAssistantDefaultString` | conditional / inspect body |
| `voiceAssistantDefaultImage` | conditional / inspect body |
| `codecSetting` | true |
| `codecTypes` | conditional / inspect body |
| `noiseCanceling` | true |

### ATH-CKR700BT (GAIA_V2)

Peer-sensitive category list was not recovered as a simple local list; follow the original definition.

| Predicate/function | Result classification |
|---|---|
| `codecSetting` | true |
| `codecTypes` | conditional / inspect body |

### ATH-CKS5TW (GAIA_V2)

Peer-sensitive category list was not recovered as a simple local list; follow the original definition.

| Predicate/function | Result classification |
|---|---|
| `codecSetting` | true |
| `codecTypes` | conditional / inspect body |

### ATH-ANC900BT (GAIA_V2)

Peer-sensitive category list was not recovered as a simple local list; follow the original definition.

| Predicate/function | Result classification |
|---|---|
| `codecSetting` | true |
| `codecTypes` | conditional / inspect body |
| `noiseCanceling` | true |

### ATH-M50xBT (GAIA_V2)

Peer-sensitive category list was not recovered as a simple local list; follow the original definition.

| Predicate/function | Result classification |
|---|---|
| `codecSetting` | true |
| `codecTypes` | conditional / inspect body |

### ATH-SPORT7TW (GAIA_V2)

Peer-sensitive category list was not recovered as a simple local list; follow the original definition.

| Predicate/function | Result classification |
|---|---|
| `codecSetting` | true |
| `codecTypes` | conditional / inspect body |

### AT-SBS50BT (GAIA_V2)

Peer-sensitive category list was not recovered as a simple local list; follow the original definition.

No relevant simple model-local predicate functions recovered by this index.

### AT-SBS70BT (GAIA_V2)

Peer-sensitive category list was not recovered as a simple local list; follow the original definition.

No relevant simple model-local predicate functions recovered by this index.
