# Soundcore: full vendor APK protocol coverage

Target: **5.0.35-435**, package `com.oceanwing.soundcore`. Branch: `research/soundcore-full-protocol`. Scope is all vendor-supported models, independently of PairFree's current device list. This research is **in progress**, with no application implementation changes.

## Recovered coverage

- [Recovery evidence and virtual checks](APK_RECOVERY_5_0_35.md): protected business DEX and selected method bodies recovered; 2,984 protocol/model/control classes, 27,002 methods, zero instruction-store markers remaining in that measured selection.
- [App registration inventory](REGISTERED_MODELS_5_0_35.md): 161 keys, including aliases and separate cases, with recovered model bodies and observed SPP UUIDs.
- [New SDK profiles](SDK_PROFILES_5_0_35.md): 14 product/component profiles, plus a SAMPLE scaffold excluded from totals. The union with the app table is 165 distinct keys after case normalization.
- [Things / OMC protocol path](THINGS_PROTOCOL_5_0_35.md): an additional 351 recovered classes / 2,353 methods and the named-function-to-QuickJS bridge; online product definitions still required.
- [Latest protocol findings](LATEST_PROTOCOL_FINDINGS_5_0_35.md): newer parametric EQ, firmware table gates, packet variants, and a virtually checked D1206 button serializer.
- Derived CSV catalogues contain 467 builder methods, 683 runtime constants, 106 constant boolean overrides and 408 EQ-related vectors from 44 tables.

## Evidence categories

| Category | What it establishes | Current result |
| --- | --- | --- |
| Resource candidate | A product/label string exists | Appendix below; not an active registration |
| Compiled registration/profile | This APK has an explicit model route | App and SDK inventory documents |
| Recovered protocol code | Original instruction bodies are available for research | Measured protocol selection recovered |
| Virtually checked data | Vendor getters or static tables were read offline | Registration UUIDs, scoped constants and EQ vectors |
| Virtually checked serialization | Actual vendor code produced compared bytes | Six wrapper cases and two D1206 button cases |
| Physical device verified | Bud accepted a command and changed/reported state | None in this exercise |

## Completeness work still required

The compiled app and SDK inventories are not the final online supported-device list: `BaseSeriesHelp.m()` can merge downloaded Things/OMC product definitions. Those definitions and schema-driven commands still need inventory and research. Resource-only strings must also be reconciled against the real registries rather than promoted to supported devices.

For every registered model, finish mapping the selected command/parser path, initial reads, ordered state layouts, EQ/preset selection, gesture/action choices, additional feature writes and firmware branches. Distinguish inherited methods from functions actually enabled for that model. Expand virtual serializer/parser checks with explicit per-case evidence. No 3.3/OpenSCQ30 layout has been assigned to a newly discovered model by name similarity.

## Resource candidates

The identifier column preserves the product/label resource suffix. It is not asserted to be a Bluetooth model number. Git presence is only a comparison column and supplies no vendor-protocol evidence.

| Resource identifier | Vendor product/label text | Exact identifier already in Git | Resource-to-registry reconciliation |
| --- | --- | --- | --- |
| A1303_BOX | Sleep Earbuds 4-Box | No | Not reconciled |
| A3004 | soundcore Q20i / Q20i | Yes | Not reconciled |
| A3004311 | soundcore Q21i NC / Q21i NC | No | Not reconciled |
| A3004C | soundcore Q20i Plus / Q20i Plus | No | Not reconciled |
| A3005 | soundcore Q11i / Q11i | Yes | Not reconciled |
| A3012 | soundcore H30i / H30i | Yes | Not reconciled |
| A3025 | soundcore Life Q20 / Life Q20 | Yes | Not reconciled |
| A3025EU | soundcore Q20 / Q20 | No | Not reconciled |
| A3027 | Soundcore Life Q35 / Life Q35 | Yes | Not reconciled |
| A3027EU | Soundcore Q35 / Q35 | No | Not reconciled |
| A3028 | Soundcore Life Q30 / Life Q30 | Yes | Not reconciled |
| A3028CC | soundcore  Q30 | No | Not reconciled |
| A3028CC_CN | Q30 | No | Not reconciled |
| A3028CC_JP | soundcore Q30i / Q30i | No | Not reconciled |
| A3028EU | Soundcore Q30 / Q30 | No | Not reconciled |
| A3029 | Soundcore Life Tune / Life Tune | Yes | Not reconciled |
| A3029EU | Soundcore Tune / Tune | No | Not reconciled |
| A3029X | Soundcore Life Tune XR / Life Tune XR | No | Not reconciled |
| A3030 | Soundcore Life Tune Pro / Life Tune Pro | Yes | Not reconciled |
| A3033 | Soundcore Life 2 Neo / Life 2 Neo | Yes | Not reconciled |
| A3033EU | Soundcore Q10i / Q10i | No | Not reconciled |
| A3035 | soundcore Space One / Space One | Yes | Not reconciled |
| A3035CS | soundcore CS 30Q / CS 30Q | No | Not reconciled |
| A3040 | Soundcore Space Q45 / Space Q45 | Yes | Not reconciled |
| A3045 | Soundcore Life Q20+ / Life Q20+ | Yes | Not reconciled |
| A3045ZA1 | soundcore Life Q20+ SE / Life Q20+ SE | No | Not reconciled |
| A3062 | soundcore Space One Pro / Space One Pro | Yes | Not reconciled |
| A3101S_BOX | soundcore Sleep A30 Special Box / Sleep A30 Special Box | No | Not reconciled |
| A3101_BOX | soundcore Sleep A30 Box / Sleep A30 Box | No | Not reconciled |
| A3116 | Soundcore Motion+ / Motion+ | Yes | Not reconciled |
| A3117 | Soundcore 3 / soundcore 3 | No | Not reconciled |
| A3118 | Soundcore Motion Boom / Motion Boom | No | Not reconciled |
| A3119 | Soundcore Mini 3 / soundcore Mini 3 | No | Not reconciled |
| A3123 | Soundcore Icon+ / Icon+ | No | Not reconciled |
| A3125 | Soundcore Select 2 / soundcore Select 2 | No | Not reconciled |
| A3126 | Soundcore Select Pro / Select Pro | No | Not reconciled |
| A3127 | Soundcore Mini 3 Pro / soundcore Mini 3 Pro | No | Not reconciled |
| A3129 | Soundcore Motion Boom Plus / Motion Boom Plus | No | Not reconciled |
| A3130 | soundcore Motion X600 / Motion X600 | No | Not reconciled |
| A3131 | soundcore Motion X500 / Motion X500 | No | Not reconciled |
| A3133 | soundcore Motion 100 / Motion 100 | No | Not reconciled |
| A3134 | soundcore Boom 2 Plus / Boom 2 Plus | No | Not reconciled |
| A3134Z11 | soundcore Boom 2 Pro / Boom 2 Pro | No | Not reconciled |
| A3135 | soundcore Motion 300 / Motion 300 | No | Not reconciled |
| A3136 | soundcore Glow Mini / Glow Mini | No | Not reconciled |
| A3138 | soundcore Boom 2 / Boom 2 | No | Not reconciled |
| A3138J11 | soundcore Boom V2 / Boom V2 | No | Not reconciled |
| A3145 | Anker Soundcore Boost / Boost | No | Not reconciled |
| A3148 | soundcore Boom 2 SE / Boom 2 SE | No | Not reconciled |
| A3161 | Soundcore Flare | No | Not reconciled |
| A3161_UK | A3161 | No | Not reconciled |
| A3162 | Soundcore Flare + | No | Not reconciled |
| A3162_OLD | Soundcore Flare+ | No | Not reconciled |
| A3162_UK | A3162 | No | Not reconciled |
| A3163 | Soundcore Flare S+ / Flare S+ | No | Not reconciled |
| A3163_UK | A3163 | No | Not reconciled |
| A3165 | Soundcore Flare 2 / Flare 2 | No | Not reconciled |
| A3165_UK | A3165 | No | Not reconciled |
| A3166 | soundcore Glow / Glow | No | Not reconciled |
| A3167 | Soundcore Flare Mini / Flare Mini | No | Not reconciled |
| A3167_UK | A3167 | No | Not reconciled |
| A3171 | soundcore Select 2S / Select 2S | No | Not reconciled |
| A3172 | soundcore Select 3 / Select 3 | No | Not reconciled |
| A31A3 | soundcore Rave 3 / Rave 3 | No | Not reconciled |
| A31A3012 | soundcore Rave 3S / Rave 3S | No | Not reconciled |
| A3201 | Soundcore Life NC / Life NC | Yes | Not reconciled |
| A3300 | Soundcore Wakey / Wakey | No | Not reconciled |
| A3301 | Anker PowerConf / PowerConf | No | Not reconciled |
| A3302 | Anker PowerConf S3 / PowerConf S3 | No | Not reconciled |
| A3330 | soundcore C30i / C30i | Yes | Not reconciled |
| A3331 | soundcore C40i / C40i | Yes | Not reconciled |
| A3372 | Soundcore Infini Pro / Infini Pro | No | Not reconciled |
| A3388 | soundcore AeroClip / AeroClip | Yes | Not reconciled |
| A3388P42 | soundcore AeroClip Special / AeroClip Special | No | Not reconciled |
| A3388_OLD | soundcore C50i / C50i | No | Not reconciled |
| A3390 | Soundcore Rave mini | No | Not reconciled |
| A3390_E | Soundcore Rave PartyCast / Rave PartyCast | No | Not reconciled |
| A3390_E_NEW | Rave PartyCast | No | Not reconciled |
| A3390_J | Soundcore Rave Party SE / Rave Party SE | No | Not reconciled |
| A3391 | Soundcore Rave | No | Not reconciled |
| A3391_P | Soundcore Rave+ / Rave+ | No | Not reconciled |
| A3392 | Soundcore Mega / Mega | No | Not reconciled |
| A3393 | Soundcore Trance / Trance | No | Not reconciled |
| A3395 | Soundcore Rave Neo / Rave Neo | No | Not reconciled |
| A3396 | Soundcore Trance Go / Trance Go | No | Not reconciled |
| A3398 | Soundcore R30 / R30 | No | Not reconciled |
| A3399 | Soundcore Rave Party 2 / Rave Party 2 | No | Not reconciled |
| A33A0 | Soundcore Rave Neo SE / Rave Neo SE | No | Not reconciled |
| A33A1 | Soundcore Rave Neo 2 / Rave Neo 2 | No | Not reconciled |
| A33A1J11 | Soundcore Rave Neo 2 SE / Rave Neo 2 SE | No | Not reconciled |
| A3561 | BeatLight B32 | No | Not reconciled |
| A3600 | Soundcore Frames / soundcore Frames | No | Not reconciled |
| A3850 | Soundcore VR P10 / VR P10 | Yes | Not reconciled |
| A3871 | soundcore AeroFit Pro / AeroFit Pro | Yes | Not reconciled |
| A3872 | soundcore AeroFit / AeroFit | Yes | Not reconciled |
| A3873 | soundcore V30i / V30i | Yes | Not reconciled |
| A3874 | soundcore AeroFit 2 / AeroFit 2 | Yes | Not reconciled |
| A3874X | AeroFit 2 AI Assistant | No | Not reconciled |
| A3875 | soundcore AeroFit 2 Pro / AeroFit 2 Pro | No | Not reconciled |
| A3876 | soundcore V20i / V20i | Yes | Not reconciled |
| A3878 | soundcore V40i / V40i | Yes | Not reconciled |
| A3909 | Soundcore Liberty 2 Pro / Liberty 2 Pro | Yes | Not reconciled |
| A3910 | Soundcore Liberty Air2 / Liberty Air 2 | Yes | Not reconciled |
| A3910_NEW | Soundcore Liberty Air 2 | No | Not reconciled |
| A3913 | Soundcore Liberty 2 / Liberty 2 | Yes | Not reconciled |
| A3926 | Soundcore Liberty Neo 2 / Liberty Neo 2 | Yes | Not reconciled |
| A3926Z11 | Soundcore Life Dot 2S / Life Dot 2S | No | Not reconciled |
| A3927 | Soundcore Life A1 / Life A1 | Yes | Not reconciled |
| A3927EU | Soundcore A1 / A1 | No | Not reconciled |
| A3930 | Soundcore Liberty 2 Pro+ / Liberty 2 Pro+ | Yes | Not reconciled |
| A3930_OLD | Soundcore Liberty 2 Pro + | No | Not reconciled |
| A3931 | Soundcore Life Dot 2 NC / Life Dot 2 NC | Yes | Not reconciled |
| A3931X | Soundcore Life Dot 2 XR / Life Dot 2 XR | No | Not reconciled |
| A3933 | Soundcore Life Note 3 / Life Note 3 | Yes | Not reconciled |
| A3935 | Soundcore Life A2 NC / Life A2 NC | Yes | Not reconciled |
| A3935W | Soundcore Life A2 NC+ / Life A2 NC+ | No | Not reconciled |
| A3936 | Soundcore Space A40 / Space A40 | Yes | Not reconciled |
| A3937 | soundcore P41i / P41i | Yes | Not reconciled |
| A3939 | Soundcore Life P3 / Life P3 | Yes | Not reconciled |
| A3939EU | Soundcore P3 / P3 | No | Not reconciled |
| A3943 | soundcore Life Note C / Life Note C | Yes | Not reconciled |
| A3944 | soundcore Life P2 Mini / Life P2 Mini | Yes | Not reconciled |
| A3944EU | soundcore P2 Mini / P2 Mini | No | Not reconciled |
| A3945 | Soundcore Life Note 3S / Life Note 3S | Yes | Not reconciled |
| A3947 | soundcore Liberty 4 NC / Liberty 4 NC | Yes | Not reconciled |
| A3947_TEMP | soundcore Space P40 | No | Not reconciled |
| A3948 | soundcore A20i / A20i | Yes | Not reconciled |
| A3948A25 | soundcore A25i / A25i | No | Not reconciled |
| A3949 | soundcore P20i / P20i | Yes | Not reconciled |
| A3949P25 | soundcore P25i / P25i | No | Not reconciled |
| A3949R50 | soundcore R50i / R50i | No | Not reconciled |
| A3951 | Soundcore Liberty Air 2 Pro / Liberty Air 2 Pro | Yes | Not reconciled |
| A3951_NEW | Soundcore Liberty Air 2 NC | No | Not reconciled |
| A3952 | Soundcore Liberty 3 Pro / Liberty 3 Pro | Yes | Not reconciled |
| A3952_OLD | Soundcore Liberty Pro 3 | No | Not reconciled |
| A3953 | Soundcore Liberty 4 / Liberty 4 | Yes | Not reconciled |
| A3954 | soundcore Liberty 4 Pro / Liberty 4 Pro | Yes | Not reconciled |
| A3954_BOX | soundcore Liberty 4 Pro Box / Liberty 4 Pro Box | No | Not reconciled |
| A3955 | soundcore P40i / P40i | Yes | Not reconciled |
| A3956 | soundcore Liberty 5 Pro Max / Liberty 5 Pro Max | No | Not reconciled |
| A3956X | soundcore Liberty 5 Pro / Liberty 5 Pro | No | Not reconciled |
| A3956X_BOX | soundcore Liberty 5 Pro Box / Liberty 5 Pro Box | No | Not reconciled |
| A3956_BOX | soundcore Liberty 5 Pro Max Case / Liberty 5 Pro Max Box | No | Not reconciled |
| A3957 | soundcore Liberty 5 / Liberty 5 | Yes | Not reconciled |
| A3958 | soundcore A30i / A30i | Yes | Not reconciled |
| A3959 | soundcore P30i / P30i | Yes | Not reconciled |
| A3959R50 | soundcore R50i NC / R50i NC | No | Not reconciled |
| A3961 | Soundcore Sport X10 / Sport X10 | Yes | Not reconciled |
| A3968 | soundcore Sport X20 / Sport X20 | Yes | Not reconciled |
| A3969 | soundcore R50i VI / R50i VI | Yes | Not reconciled |
| A3982 | Soundcore Life Dot 3i / Life Dot 3i | Yes | Not reconciled |
| A3982EU | Soundcore Dot 3i / Dot 3i | No | Not reconciled |
| A3983 | Soundcore Life Note 3i / Life Note 3i | Yes | Not reconciled |
| A3983EU | Soundcore Note 3i / Note 3i | No | Not reconciled |
| A3992 | Soundcore Life A3i / Life A3i | Yes | Not reconciled |
| A3992EU | Soundcore A3i / A3i | No | Not reconciled |
| A3993 | Soundcore Life P3i / Life P3i | Yes | Not reconciled |
| A3993EU | Soundcore P3i / P3i | No | Not reconciled |
| A3994 | soundcore K20i / K20i | Yes | Not reconciled |
| A6610 | Soundcore Sleep A10 / Sleep A10 | Yes | Not reconciled |
| A6611 | soundcore Sleep A20 / Sleep A20 | Yes | Not reconciled |
| D1101 | soundcore C50i / C50i | Yes | Not reconciled |
| D1102 | soundcore AeroClip 2 Pro / AeroClip 2 Pro | No | Not reconciled |
| D1105 | Anker AeroClip 2 / AeroClip 2 | No | Not reconciled |
| D1200 | Liberty Buds | No | Not reconciled |
| D1202 | soundcore P31i / P31i | Yes | Not reconciled |
| D1202C | soundcore R60i NC / R60i NC | Yes | Not reconciled |
| D1205 | soundcore P42i / P42i | No | Not reconciled |
| D1206 | Liberty Buds 2 | No | Not reconciled |
| D1301 | soundcore Sleep A30 / Sleep A30 | Yes | Not reconciled |
| D1301S | soundcore Sleep A30 Special / Sleep A30 Special | No | Not reconciled |
| D1302 | Anker Sleep Earbuds 4 Pro / Sleep Earbuds 4 Pro | No | Not reconciled |
| D1302_BOX | Anker Sleep Earbuds 4 Pro-BOX / Sleep Earbuds 4 Pro-Box | No | Not reconciled |
| D1303 | Anker Sleep Earbuds 4 / Sleep Earbuds 4 | No | Not reconciled |
| D1303_BOX | ANKER Sleep Earbuds 4-Box | No | Not reconciled |
| D1402 | Space 2 | No | Not reconciled |
| D1402_NEW | Soundcore Space 2 | No | Not reconciled |
| D1404 | Anker soundcore Q31i / Q31i | No | Not reconciled |
| D1406 | Anker Space 2 Pro / Space 2 Pro | No | Not reconciled |
| D1407 | Anker Space 2 Pro Max / Space 2 Pro Max | No | Not reconciled |
| D3200 | soundcore Work | No | Not reconciled |
| D5100 | soundcore Boom 3i / Boom 3i | No | Not reconciled |
| D5103 | soundcore Boom Go 3i / Boom Go 3i | No | Not reconciled |

## Evidence and reproducibility

Original APKM SHA-256: `b7acfeee5c506879e5870a8215562b8b0cd896a93aaa54cd3eb28c24cdc6e734`. Resource XML SHA-256: `4494184d610b8bf8b82f8311989caf4169a13a76d7ed0a0b471be962d5cdb7a7`.

External research directory: `E:\Builds\research\soundcore-full-protocol-2026-11-13`. `model_census.py` rebuilds this document and the full local JSON from the extracted 5.0.35 resource XML. No secret assets are opened. Captured/decompiled vendor code and binary inputs stay outside Git.
