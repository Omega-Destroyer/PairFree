# Soundcore 5.0.35-435: per-model feature evidence

**2026-11-15 expansion:** [Full restored-code route analysis](MODEL_PROTOCOL_ROUTES_5_0_35.md) follows all 165 keys beyond this earlier home-card matrix, including legacy inline serializers, direct SPP devices, component roles, exact control-factory dispatch, action IDs and additional EQ evidence. This matrix retains its original extraction scope; a cell marked unmapped below is not the latest route inventory.

This maps all **165 compiled-registry keys** (161 app registrations plus four SDK-only keys). Keys include aliases and separate cases; they are not a count of distinct earbud products. The online Things catalogue can add other products. This is research evidence for later implementation, not a claim of complete feature coverage or tested hardware support.

## How to read the evidence

- **Home cards** are factories reachable from the declared home view model’s `initListData`, following its recovered helper calls. A card may still depend on device state, firmware or configuration. `?` marks a conditional call path or a factory with branches. Names describe the source factory, not a verified wire function.
- **VM** is the activity getter’s declared return type. A non-final type is a bound; this pass does not execute native activity construction to prove the actual object returned. Known subclass ambiguity is recorded in the JSON.
- **Buttons/EQ** describe the explicitly selected or inherited command serializer. This does not prove a model exposes remapping, supports every command, or accepts any particular gesture/action ID.
- **Unmapped** means this extraction has not established the feature list. Many older screens use a different construction path. It never means the device lacks those features.
- The [machine-readable evidence](MODEL_FEATURE_EVIDENCE_5_0_35.json) preserves source classes, call paths, state/version dependencies, unreachable factories, capability predicates and unresolved method status. Vendor code and raw artifacts stay outside Git.

## Measured coverage

154 keys have a located home activity, 152 have a typed VM binding, and 61 have reachable home-card construction through this entry point. There are 73 VM types and 173 distinct reachable factory records. Explicit model initialization binds 91 keys to common command serializers; two additional legacy routes switch to Cmm2 conditionally, as detailed below. Seventeen older model keys have explicit More-panel visibility branches.

The original 2,984-class protocol selection has zero remaining instruction-store markers, but also **188 declared native methods in 17 classes**. Native activity/popup/lifecycle methods are not recovered Java merely because the marker count is zero. The selected VM classes have no declared native methods; that does not resolve native activity construction or all downstream settings.

## Compiled-registry matrix

`81` = `04/81`, side + gesture + packed non-TWS/TWS nibbles. `8C` = `04/8C`, side + gesture + separate TWS/non-TWS bytes. EQ labels name the recovered serializer branch; see [wire findings](LATEST_PROTOCOL_FINDINGS_5_0_35.md) and [builder flags](BUILDER_FLAGS_5_0_35.csv). `legacy` means the shared `s6`/short-DRC branch, not a fully resolved per-device EQ capability.

| Key | Resource name / class | Declared home VM | Reachable home cards | Bound serializer: buttons / EQ |
| --- | --- | --- | --- | --- |
| A3004 | soundcore Q20i / Q20i | A3004MainVM | unmapped | A3004CmdService: 81 / q6 |
| A3004311 | soundcore Q21i NC / Q21i NC | A3004MainVM | unmapped | A3004CmdService: 81 / q6 |
| A3004C | soundcore Q20i Plus / Q20i Plus | A3004MainVM | unmapped | A3004cCmdService: 81 / q6 |
| A3005 | soundcore Q11i / Q11i | A3005MainVM | Device Info?, EQ?, Dual Connect? | A3005CmdService: 81 / q6 |
| A3012 | soundcore H30i / H30i | A3012MainVM | Device Info, EQ, Dual Connect, Low Battery Alert | A3012CmdService: 81 / q6 |
| A3025 | soundcore Life Q20 / Life Q20 | A3025MainVM | Device Info, Ambient, EQ, Dual Connect | A3025CmdService: 81 / q6 |
| A3027 | Soundcore Life Q35 / Life Q35 | A3951MainVM | unmapped | manager delegated; unmapped |
| A3028 | Soundcore Life Q30 / Life Q30 | A3951MainVM | unmapped | conditional legacy → Cmm2; below |
| A3028CC | soundcore  Q30 | A3951MainVM | unmapped | conditional legacy → Cmm2; below |
| A3029 | Soundcore Life Tune / Life Tune | A3951MainVM | unmapped | manager delegated; unmapped |
| A3029XR | A3029XProductModel | A3951MainVM | unmapped | manager delegated; unmapped |
| A3030 | Soundcore Life Tune Pro / Life Tune Pro | A3951MainVM | unmapped | manager delegated; unmapped |
| A3033 | Soundcore Life 2 Neo / Life 2 Neo | A3951MainVM | unmapped | manager delegated; unmapped |
| A3033EU | Soundcore Q10i / Q10i | A3951MainVM | unmapped | manager delegated; unmapped |
| A3035 | soundcore Space One / Space One | A3035MainVM | Device Info, Ambient, Smart Talk, EQ, Control, Safe Volume | A3035CmdService: 81 / t6 |
| A3035CS | soundcore CS 30Q / CS 30Q | A3035MainVM | Device Info, Ambient, Smart Talk, EQ, Control, Safe Volume | A3035CmdService: 81 / t6 |
| A3040 | Soundcore Space Q45 / Space Q45 | BtSlidingViewModel | unmapped | A3040CmdService: 81 / conditional-unresolved |
| A3045 | Soundcore Life Q20+ / Life Q20+ | A3951MainVM | unmapped | manager delegated; unmapped |
| A3045ZA1 | soundcore Life Q20+ SE / Life Q20+ SE | A3004MainVM | unmapped | A3004CmdService: 81 / q6 |
| A3062 | soundcore Space One Pro / Space One Pro | A3062MainVM | Device Info, Ambient, EQ, Smart Talk, Control, Safe Volume | A3062CmdService: 81 / t6 |
| A3116 | Soundcore Motion+ / Motion+ | A3116MainViewModel | unmapped | manager delegated; unmapped |
| A3117 | Soundcore 3 / soundcore 3 | A3117MainVM | unmapped | manager delegated; unmapped |
| A3118 | Soundcore Motion Boom / Motion Boom | A3118MainVM | unmapped | manager delegated; unmapped |
| A3119 | Soundcore Mini 3 / soundcore Mini 3 | A3117MainVM | unmapped | manager delegated; unmapped |
| A3123 | Soundcore Icon+ / Icon+ | A3116MainViewModel | unmapped | manager delegated; unmapped |
| A3125 | Soundcore Select 2 / soundcore Select 2 | A3117MainVM | unmapped | manager delegated; unmapped |
| A3126 | Soundcore Select Pro / Select Pro | A3117MainVM | unmapped | manager delegated; unmapped |
| A3127 | Soundcore Mini 3 Pro / soundcore Mini 3 Pro | A3117MainVM | unmapped | manager delegated; unmapped |
| A3129 | Soundcore Motion Boom Plus / Motion Boom Plus | A3118MainVM | unmapped | manager delegated; unmapped |
| A3130 | soundcore Motion X600 / Motion X600 | A3130MainVM | unmapped | A3130CmdService: 81 / legacy |
| A3131 | soundcore Motion X500 / Motion X500 | A3131MainVM | Device Info?, Volume, EQ | A3131CmdService: 81 / legacy |
| A3133 | soundcore Motion 100 / Motion 100 | A3133MainVM | Device Info, EQ | A3133CmdService: 81 / legacy |
| A3134 | soundcore Boom 2 Plus / Boom 2 Plus | A3134MainVM | Device Info?, View Pager | A3134CmdService: 81 / legacy |
| A3134Z11 | soundcore Boom 2 Pro / Boom 2 Pro | A3134MainVM | Device Info?, View Pager | A3134CmdService: 81 / legacy |
| A3135 | soundcore Motion 300 / Motion 300 | A3135MainVM | Device Info, Volume Seek Bar, EQ | A3135CmdService: 81 / legacy |
| A3136 | soundcore Glow Mini / Glow Mini | A3136MainVM | Device Info?, View Pager | A3136CmdService: 81 / legacy |
| A3138 | soundcore Boom 2 / Boom 2 | A3138MainVM | Device Info, View Pager | A3138CmdService: 81 / legacy |
| A3138J11 | soundcore Boom V2 / Boom V2 | A3138MainVM | Device Info, View Pager | A3138CmdService: 81 / legacy |
| A3145 | Anker Soundcore Boost / Boost | A3116MainViewModel | unmapped | manager delegated; unmapped |
| A3148 | soundcore Boom 2 SE / Boom 2 SE | A3148MainVM | Device Info?, Volume, Bass Up, EQ | A3148CmdService: 81 / legacy |
| A3161 | Soundcore Flare | unmapped | unmapped | manager delegated; unmapped |
| A3162 | Soundcore Flare + | unmapped | unmapped | manager delegated; unmapped |
| A3163 | Soundcore Flare S+ / Flare S+ | A3163MainViewModel | unmapped | manager delegated; unmapped |
| A3165 | Soundcore Flare 2 / Flare 2 | A3165MainViewModel | unmapped | manager delegated; unmapped |
| A3166 | soundcore Glow / Glow | A3136MainVM | Device Info?, View Pager | A3166CmdService: 81 / legacy |
| A3167 | Soundcore Flare Mini / Flare Mini | A3117MainVM | unmapped | manager delegated; unmapped |
| A3171 | soundcore Select 2S / Select 2S | A3172MainVM | Device Info?, Bass Up?, EQ? | A3172CmdService: 81 / legacy |
| A3172 | soundcore Select 3 / Select 3 | A3172MainVM | Device Info?, Bass Up?, EQ? | A3172CmdService: 81 / legacy |
| A31A3 | soundcore Rave 3 / Rave 3 | A31A3MainVM | Device Info?, View Pager | A31A3CmdService: 81 / legacy |
| A31A3012 | soundcore Rave 3S / Rave 3S | A31A3MainVM | Device Info?, View Pager | A31A3CmdService: 81 / legacy |
| A31X1 | A31X1ProductModel | A3172MainVM | Device Info?, Bass Up?, EQ? | A3172CmdService: 81 / legacy |
| A3201 | Soundcore Life NC / Life NC | A3201MainViewModel | unmapped | manager delegated; unmapped |
| A3300 | Soundcore Wakey / Wakey | A3300MainViewModel | unmapped | manager delegated; unmapped |
| A3301 | Anker PowerConf / PowerConf | A3301MainVM | unmapped | manager delegated; unmapped |
| A3302 | Anker PowerConf S3 / PowerConf S3 | A3301MainVM | unmapped | manager delegated; unmapped |
| A3330 | soundcore C30i / C30i | A3330MainVM | Device Info?, EQ?, Control?, Dual Connect? | A3330CmdService: 81 / q6 |
| A3331 | soundcore C40i / C40i | A3331MainVM | Device Info?, Home Tips?, EQ?, Control?, Dual Connect? | A3331CmdService: 81 / q6 |
| A3372 | Soundcore Infini Pro / Infini Pro | A3372MainViewModel | unmapped | manager delegated; unmapped |
| A3388 | soundcore AeroClip / AeroClip | A3388MainVM | Device Info?, EQ?, Control?, Dual Connect?, Find Device? | A3388CmdService: 81 / q6 |
| A3388P42 | soundcore AeroClip Special / AeroClip Special | A3388MainVM | Device Info?, EQ?, Control?, Dual Connect?, Find Device? | A3388CmdService: 81 / q6 |
| A3390 | Soundcore Rave mini | A3391MainViewModel | unmapped | manager delegated; unmapped |
| A3390E | A3390EProductModel | A3395MainViewModel | unmapped | manager delegated; unmapped |
| A3390J | A3390JProductModel | A3395MainViewModel | unmapped | manager delegated; unmapped |
| A3391 | Soundcore Rave | A3391MainViewModel | unmapped | manager delegated; unmapped |
| A3391E | A3391PProductModel | A3395MainViewModel | unmapped | manager delegated; unmapped |
| A3392 | Soundcore Mega / Mega | A3391MainViewModel | unmapped | manager delegated; unmapped |
| A3393 | Soundcore Trance / Trance | A3391MainViewModel | unmapped | manager delegated; unmapped |
| A3395 | Soundcore Rave Neo / Rave Neo | A3395MainViewModel | unmapped | manager delegated; unmapped |
| A3396 | Soundcore Trance Go / Trance Go | A3395MainViewModel | unmapped | manager delegated; unmapped |
| A3398 | Soundcore R30 / R30 | A3398MainViewModel | unmapped | A3398CmdService: 81 / legacy |
| A3399 | Soundcore Rave Party 2 / Rave Party 2 | A3398MainViewModel | unmapped | A3399CmdService: 81 / legacy |
| A33A0 | Soundcore Rave Neo SE / Rave Neo SE | A3395MainViewModel | unmapped | manager delegated; unmapped |
| A33A1 | Soundcore Rave Neo 2 / Rave Neo 2 | A3398MainViewModel | unmapped | A33A1CmdService: 81 / legacy |
| A33A1J11 | Soundcore Rave Neo 2 SE / Rave Neo 2 SE | A3398MainViewModel | unmapped | A33A1CmdService: 81 / legacy |
| A3600 | Soundcore Frames / soundcore Frames | A3600MainVM | unmapped | A3600CmdService: 81 / legacy |
| A3850 | Soundcore VR P10 / VR P10 | A3850MainVM | unmapped | A3850CmdService: 81 / legacy |
| A3871 | soundcore AeroFit Pro / AeroFit Pro | A3871MainVM | Device Info, EQ, Control | A3871CmdService: 81 / q6 |
| A3872 | soundcore AeroFit / AeroFit | A3871MainVM | Device Info, EQ, Control | A3871CmdService: 81 / q6 |
| A3873 | soundcore V30i / V30i | A3871MainVM | Device Info, EQ, Control | A3871CmdService: 81 / q6 |
| A3874 | soundcore AeroFit 2 / AeroFit 2 | A3874MainVM | Device Info?, Home Tips?, EQ?, Control?, Dual Device?, Game Mode?, AIQA? | A3874CmdService: 81 / q6 |
| A3874X | AeroFit 2 AI Assistant | A3874MainVM | Device Info?, Home Tips?, EQ?, Control?, Dual Device?, Game Mode?, AIQA? | A3874CmdService: 81 / q6 |
| A3875 | soundcore AeroFit 2 Pro / AeroFit 2 Pro | A3875MainVM | Device Info?, Ambient?, EQ?, Game Mode?, Dual Device?, Control? | A3875CmdService: 81 / r6 |
| A3876 | soundcore V20i / V20i | A3876MainVM | Device Info?, Home Tips?, EQ?, Control?, Home Switch?, Light Effect? | A3876CmdService: 81 / q6 |
| A3878 | soundcore V40i / V40i | A3878MainVM | Device Info, EQ, Control, Home Switch, Dual Device | A3876CmdService: 81 / q6 |
| A3909 | Soundcore Liberty 2 Pro / Liberty 2 Pro | A3909MainViewModel | unmapped | manager delegated; unmapped |
| A3910 | Soundcore Liberty Air2 / Liberty Air 2 | A3909MainViewModel | unmapped | manager delegated; unmapped |
| A3913 | Soundcore Liberty 2 / Liberty 2 | A3909MainViewModel | unmapped | manager delegated; unmapped |
| A3926 | Soundcore Liberty Neo 2 / Liberty Neo 2 | A3951MainVM | unmapped | manager delegated; unmapped |
| A3926Z11 | Soundcore Life Dot 2S / Life Dot 2S | A3951MainVM | unmapped | manager delegated; unmapped |
| A3927 | Soundcore Life A1 / Life A1 | A3927MainVM | Device Info, EQ, Control | A3948CmdService: 81 / q6 |
| A3927EU | Soundcore A1 / A1 | A3927MainVM | Device Info, EQ, Control | A3948CmdService: 81 / q6 |
| A3930 | Soundcore Liberty 2 Pro+ / Liberty 2 Pro+ | A3951MainVM | unmapped | manager delegated; unmapped |
| A3931 | Soundcore Life Dot 2 NC / Life Dot 2 NC | A3951MainVM | unmapped | manager delegated; unmapped |
| A3931XR | A3931XProductModel | A3951MainVM | unmapped | manager delegated; unmapped |
| A3933 | Soundcore Life Note 3 / Life Note 3 | A3933MainVM | unmapped | manager delegated; unmapped |
| A3935 | Soundcore Life A2 NC / Life A2 NC | A3951MainVM | unmapped | manager delegated; unmapped |
| A3935W | Soundcore Life A2 NC+ / Life A2 NC+ | A3951MainVM | unmapped | manager delegated; unmapped |
| A3936 | Soundcore Space A40 / Space A40 | BtSlidingViewModel | unmapped | A3936CmdService: 81 / t6 |
| A3937 | soundcore P41i / P41i | A3937MainViewModel | Device Info, Ambient, EQ?, Control, Safe Volume | A3955CmdService: 81 / t6 |
| A3939 | Soundcore Life P3 / Life P3 | A3933MainVM | unmapped | manager delegated; unmapped |
| A3939WEU | A3939EUProductModel | A3933MainVM | unmapped | manager delegated; unmapped |
| A3943 | soundcore Life Note C / Life Note C | A3944MainVM | Device Info?, EQ, Control, Find Device | A3948CmdService: 81 / q6 |
| A3944 | soundcore Life P2 Mini / Life P2 Mini | A3944MainVM | Device Info?, EQ, Control, Find Device | A3948CmdService: 81 / q6 |
| A3944EU | soundcore P2 Mini / P2 Mini | A3944MainVM | Device Info?, EQ, Control, Find Device | A3948CmdService: 81 / q6 |
| A3945 | Soundcore Life Note 3S / Life Note 3S | A3933MainVM | unmapped | manager delegated; unmapped |
| A3947 | soundcore Liberty 4 NC / Liberty 4 NC | A3947MainVM | unmapped | A3947CmdService: 81 / t6 |
| A3948 | soundcore A20i / A20i | A3948MainVM | unmapped | A3948CmdService: 81 / q6 |
| A3948A25 | soundcore A25i / A25i | A3948MainVM | unmapped | A3948CmdService: 81 / q6 |
| A3949 | soundcore P20i / P20i | A3948MainVM | unmapped | A3948CmdService: 81 / q6 |
| A3949P25 | soundcore P25i / P25i | A3948MainVM | unmapped | A3948CmdService: 81 / q6 |
| A3949R50 | soundcore R50i / R50i | A3948MainVM | unmapped | A3948CmdService: 81 / q6 |
| A3951 | Soundcore Liberty Air 2 Pro / Liberty Air 2 Pro | A3951MainVM | unmapped | manager delegated; unmapped |
| A3952 | Soundcore Liberty 3 Pro / Liberty 3 Pro | A3952MainVM | unmapped | A3952CmdService: 81 / t6 |
| A3953 | Soundcore Liberty 4 / Liberty 4 | A3953MainVM | unmapped | A3953CmdService: 81 / t6 |
| A3954 | soundcore Liberty 4 Pro / Liberty 4 Pro | A3954MainViewModel | unmapped | A3954CmdService: 81 / n6 |
| A3954-Box | A3954BoxProductModel | unmapped | unmapped | manager delegated; unmapped |
| A3955 | soundcore P40i / P40i | A3955MainViewModel | Device Info, Ambient, EQ?, Control, Safe Volume | A3955CmdService: 81 / t6 |
| A3957 | soundcore Liberty 5 / Liberty 5 | A3957MainVM | Device Info?, Ambient?, EQ?, Control? | A3957CmdService: 81 / n6 |
| A3958 | soundcore A30i / A30i | A3958MainVM | Device Info?, Ambient?, EQ?, Control?, Game Mode? | A3958CmdService: 81 / q6 |
| A3959 | soundcore P30i / P30i | A3958MainVM | Device Info?, Ambient?, EQ?, Control?, Game Mode? | A3958CmdService: 81 / q6 |
| A3959R50 | soundcore R50i NC / R50i NC | A3958MainVM | Device Info?, Ambient?, EQ?, Control?, Game Mode? | A3958CmdService: 81 / q6 |
| A3961 | Soundcore Sport X10 / Sport X10 | A3961MainVM | unmapped | manager delegated; unmapped |
| A3968 | soundcore Sport X20 / Sport X20 | A3968MainViewModel | Device Info, Ambient, EQ, Control | A3968CmdService: 81 / t6 |
| A3969 | soundcore R50i VI / R50i VI | A3948MainVM | unmapped | manager delegated; unmapped |
| A3982 | Soundcore Life Dot 3i / Life Dot 3i | A3933MainVM | unmapped | manager delegated; unmapped |
| A3982EU | Soundcore Dot 3i / Dot 3i | A3933MainVM | unmapped | manager delegated; unmapped |
| A3983 | Soundcore Life Note 3i / Life Note 3i | A3933MainVM | unmapped | manager delegated; unmapped |
| A3983EU | Soundcore Note 3i / Note 3i | A3933MainVM | unmapped | manager delegated; unmapped |
| A3992 | Soundcore Life A3i / Life A3i | A3933MainVM | unmapped | manager delegated; unmapped |
| A3992EU | Soundcore A3i / A3i | A3933MainVM | unmapped | manager delegated; unmapped |
| A3993 | Soundcore Life P3i / Life P3i | A3933MainVM | unmapped | manager delegated; unmapped |
| A3993EU | Soundcore P3i / P3i | A3933MainVM | unmapped | manager delegated; unmapped |
| A3994 | soundcore K20i / K20i | A3994MainViewModel | Device Info, EQ, Control, Game Mode | A3994CmdService: 81 / q6 |
| A6610 | Soundcore Sleep A10 / Sleep A10 | A6610MainVM | unmapped | A6610CmdService: 81 / legacy |
| A6611 | soundcore Sleep A20 / Sleep A20 | A6611MainVM | unmapped | A6611CmdService: 81 / q6 |
| D1101 | soundcore C50i / C50i | D1101MainVM | Device Info, EQ?, Control, Dual Connect | D1101CmdService: 81 / q6 |
| D1102 | soundcore AeroClip 2 Pro / AeroClip 2 Pro | unmapped | unmapped | SDK profile only |
| D1105 | Anker AeroClip 2 / AeroClip 2 | D1105MainVM | Device Info, Normalize EQ, Control, Dual Connect, Nap?, Game Mode | D1105CmdService: 8C / m6 parametric |
| D1200 | Liberty Buds | D1200MainVM | Device Info, Ambient, EQ?, Control | D1200CmdService: 81 / n6 |
| D1202 | soundcore P31i / P31i | D1202MainVM | Device Info, Ambient?, EQ?, Control | D1202CmdService: 81 / n6 |
| D1202C | soundcore R60i NC / R60i NC | D1202MainVM | Device Info, Ambient?, EQ?, Control | D1202CmdService: 81 / n6 |
| D1203 | A3956XProductModel | A3956MainVM | Device Info?, Ambient?, Normalize EQ?, Control? | A3956CmdService: 8C / m6 parametric |
| D1203-Box | A3956XBoxProductModel | unmapped | unmapped | A3956CmdService: 8C / m6 parametric |
| D1204 | A3956ProductModel | A3956MainVM | Device Info?, Ambient?, Normalize EQ?, Control? | A3956CmdService: 8C / m6 parametric |
| D1204-Box | A3956BoxProductModel | unmapped | unmapped | A3956CmdService: 8C / m6 parametric |
| D1205 | soundcore P42i / P42i | D1205MainVM | Device Info, Ambient, Normalize EQ?, Game Mode, Control | D1205CmdService: 81 / m6 parametric |
| D1206 | Liberty Buds 2 | D1206MainVM | Device Info, Ambient, Normalize EQ, Control | D1206CmdService: 8C / m6 parametric |
| D1301 | soundcore Sleep A30 / Sleep A30 | D1301MainVM | unmapped | D1301CmdService: 81 / q6 |
| D1301-Box | D1301BoxProductModel | unmapped | unmapped | manager delegated; unmapped |
| D1301S | soundcore Sleep A30 Special / Sleep A30 Special | D1301MainVM | unmapped | D1301CmdService: 81 / q6 |
| D1301S-Box | D1301SBoxProductModel | unmapped | unmapped | manager delegated; unmapped |
| D1302 | Anker Sleep Earbuds 4 Pro / Sleep Earbuds 4 Pro | unmapped | unmapped | SDK profile only |
| D1302-BOX | D1302BoxProductProfile | unmapped | unmapped | SDK profile only |
| D1303 | Anker Sleep Earbuds 4 / Sleep Earbuds 4 | D1303MainVM | unmapped | D1303CmdService: 81 / q6 |
| D1303-Box | D1303BoxProductModel | unmapped | unmapped | manager delegated; unmapped |
| D1402 | Space 2 | D1402MainVM | Device Info?, Ambient?, EQ?, Control?, Safe Volume?, Nap? | D1402CmdService: 81 / t6 |
| D1404 | Anker soundcore Q31i / Q31i | D1404MainVM | Device Info?, Ambient?, Normalize EQ?, Control?, Nap? | D1404CmdService: 81 / m6 parametric |
| D1406 | Anker Space 2 Pro / Space 2 Pro | D1406MainVM | Device Info, Ambient?, Normalize EQ, Control, Safe Volume, Nap? | D1406CmdService: 8C / m6 parametric |
| D1407 | Anker Space 2 Pro Max / Space 2 Pro Max | D1406MainVM | Device Info, Ambient?, Normalize EQ, Control, Safe Volume, Nap? | D1406CmdService: 8C / m6 parametric |
| D3200 | soundcore Work | BtSlidingViewModel | unmapped | D3200CmdService: 81 / q6 |
| D5100 | soundcore Boom 3i / Boom 3i | D5100MainVM | Device Info, View Pager | D5100CmdService: 81 / legacy |
| D5103 | soundcore Boom Go 3i / Boom Go 3i | D5103MainVM | Device Info, View Pager | D5103CmdService: 81 / legacy |
| D5160 | D5160ProductProfile | unmapped | unmapped | SDK profile only |
| Z5180 | Z5180ProductModel | CmmBtMainViewModel | unmapped | manager delegated; unmapped |
| Z6111 | Z6111ProductModel | unmapped | unmapped | manager delegated; unmapped |

## Legacy More-panel visibility

Source: `A3951MoreVM.initDataByProduct`. These columns concern this legacy panel only. A hidden row here does not establish global feature absence. In particular, the migrated Cmm2 path has additional settings. Touch enable is an on/off control, not gesture remapping. Firmware values below are parsed integers produced by the app, not decimal version comparisons.

| Key | Sidetone | Wear detection | Touch tone | Auto power-off | Touch enable |
| --- | --- | --- | --- | --- | --- |
| A3027 | hidden | shown | hidden | hidden | FW ≥ 112 |
| A3028 | hidden | hidden | hidden | hidden | hidden |
| A3028CC | hidden | hidden | hidden | hidden | hidden |
| A3029 | hidden | hidden | hidden | hidden | hidden |
| A3029XR | hidden | hidden | hidden | hidden | hidden |
| A3030 | hidden | shown | hidden | hidden | FW ≥ 112 |
| A3033 | hidden | hidden | hidden | hidden | hidden |
| A3033EU | hidden | hidden | hidden | hidden | hidden |
| A3045 | hidden | hidden | hidden | hidden | hidden |
| A3926 | hidden | hidden | hidden | hidden | hidden |
| A3926Z11 | hidden | hidden | hidden | hidden | hidden |
| A3930 | hidden | hidden | hidden | hidden | hidden |
| A3931 | hidden | hidden | shown | shown | hidden |
| A3931XR | hidden | hidden | shown | shown | hidden |
| A3935 | hidden | hidden | shown | shown | hidden |
| A3935W | hidden | hidden | shown | shown | hidden |
| A3951 | hidden | shown | shown | hidden | hidden |

`initLdac` shows the sound-quality section for A3930/A3951. Separately, this MainVM’s `hasLDAC` predicate returns true for A3930 and for A3951 with parsed firmware ≥400. Do not interpret its false result for another model as a global statement about that model’s codecs. The Chinese voice-prompt row is restricted to A3951, system language `zh`, and firmware in [364,400) or [564,600).

## A3028 / A3028CC: protocol migration and extended state

`A3951DeviceManager.v1` first parses legacy state, then can select a Cmm2 manager. The exact model comparison uses the current global product code, case sensitively. Both firmware eligibility and **reply length ≥77 bytes** are required. The byte positions below are indices in the array passed to `v1`; they must not be treated as offsets in an arbitrary stripped payload.

| Current product | Firmware predicate | Selected service pair |
| --- | --- | --- |
| A3028 | parsed firmware ≥400 | A3028CmdService / A3028AnalysisService |
| A3028CC | eligible independently of firmware | A3028CCCmdService / A3028CCAnalysisService |

The migration branch reads byte 69 touch tone, 70 touch control, 71 multipoint, 72 auto power-off switch, 73 auto power-off index through `F0`, 74 ambient-sound notification and 75 power-on battery notification. It carries product, firmware, serial and connection state into the new manager; no identifiers from a real device were used in this research.

For **A3028CC only**, parsed firmware ≥518 enables the extra custom-control parsing: byte 76 is `customLength`, control data starts at 77, and ANC selection is read at `customLength + 75` (ANC/transparency/normal bits 0/1/2). The initial 77-byte guard does not prove that the variable extension fits. Any later implementation needs complete bounds checks.

Twenty calls to the vendor’s pure `AbOtaVersionCheckUtils.a/c` predicates were executed offline for A3028, A3028CC, A3027, A3029 and lowercase `a3028`, at firmware 399/400/517/518. All matched the conditions above. This verifies the predicates only; the stateful manager migration and reply-length boundary were inspected in code, not invoked. No OTA or transport operation was called.

## Reproduce and continue

External work directory: `C:/Users/Nima/AppData/Local/PairFreeResearch/soundcore-feature-mapping-2026-11-13`. `map_model_features.py` rebuilds the evidence JSON; its DEX metadata cache is invalidated by the current live-image fingerprint. `extract_legacy_visibility.py` resolves all 17 branches and their numeric switch constants. `write_feature_docs.py` writes this derived report. The newest recovered sources are in its `jadx/sources/`, not just the first recovery tree.

Runtime predicate observations and exact request serializer field names are under `E:/Builds/research/soundcore-full-protocol-2026-11-13/instrumentation-output/pairfree-instrumentation/`, in `firmware-predicates.tsv` and `request-schemas.tsv`. [Anonymous cloud results](THINGS_PROTOCOL_5_0_35.md) describe the attempted downloads.

Next work is semantic: map each remaining screen/settings model to its read/write calls, resolve supported gesture/action enumerations and firmware gates, trace delegated legacy and SDK profiles, and recover native feature methods where needed. D1406’s voice-setting factory is present but not reachable from its inspected home list, so it is deliberately not promoted to a home-card capability. Cloud-only functions still need their actual Thing Description and script; local catalogue rows cannot replace those.
