# Soundcore 5.0.35: model routes after full instruction-store recovery

This pass covers all **165 compiled-registry keys**, including aliases and component keys. It is a protocol research inventory, not 165 distinct supported products or complete per-device feature specifications. The online Things/OMC catalogue remains unenumerated because the last catalogue request returned 401; no server request was made in this pass.

## New evidence

- Indexed 29,036 vendor classes, 203,458 methods and 792,086 call sites from the restored DEX. Arbitrary string constants are excluded from the index.
- Resolved 84 distinct combinations of manager, serializer, parser and component role; 79 contain outbound wire candidates and every one of those has command-constant evidence.
- Located 1166 unique command/manager method definitions and 954 unique UI-to-send call sites. These are class-level references, not proof that every model reaches or enables each function.
- Extracted 549 literal seven- or nine-byte command constants in 17 classes; all 139 overlapping earlier runtime values match exactly. Nine-byte constants retain their embedded length bytes and must not be treated as seven-byte prefixes.
- Evaluated the actual restored control factory for all 165 keys, obtaining 38 data-source selections. The unknown-code default and Things override were checked separately. Constructors and Android UI were not executed.
- Added [control/action evidence](CONTROL_MAPPING_5_0_35.md), 192 literal EQ vectors in 14 gain-provider classes, and [six interpreted legacy serializer/framer checks](LEGACY_PROTOCOL_DETAILS_5_0_35.md).

## Route distinctions

The 161 app registrations comprise 152 ordinary manager routes, two independently managed component routes, two direct SPP routes, four parent-owned components and one registration without a model manager. Four additional keys occur only in SDK profiles.

Flare A3161 and Flare+ A3162 have null manager getters and empty model initializers, but both select `A3161MainActivity`; its `initSpplink` calls `A3161SppLink`. That is a separate path, not absent protocol support. `A3163DeviceSppManager` is recognized through its superclass rather than its spelling.

A3954-Box, D1301-Box, D1301S-Box and D1303-Box expose component state through their parent devices; they do not initialize independent model managers. D1203-Box and D1204-Box declare their own managers and serializers and remain separate routes. Z6111 inherits the null manager/home defaults and has no model initializer: registration metadata alone does not supply an implementation route.

## Per-key index

Counts list static candidates. **Default** in the control column means the factory falls back to `BaseDataSourceImpl`; it says nothing about remapping support. Conditional legacy-to-Cmm2 migration for A3028/A3028CC remains recorded in the JSON.

| Key | Vendor label / class | Route | Manager / direct link | Explicit writer | Control factory |
| --- | --- | --- | --- | --- | --- |
| A3004 | soundcore Q20i / Q20i | device_manager | Cmm2BtDeviceManager | A3004CmdService | Default |
| A3004311 | soundcore Q21i NC / Q21i NC | device_manager | Cmm2BtDeviceManager | A3004CmdService | Default |
| A3004C | soundcore Q20i Plus / Q20i Plus | device_manager | Cmm2BtDeviceManager | A3004cCmdService | Default |
| A3005 | soundcore Q11i / Q11i | device_manager | Cmm2BtDeviceManager | A3005CmdService | Default |
| A3012 | soundcore H30i / H30i | device_manager | Cmm2BtDeviceManager | A3012CmdService | Default |
| A3025 | soundcore Life Q20 / Life Q20 | device_manager | Cmm2BtDeviceManager | A3025CmdService | Default |
| A3027 | Soundcore Life Q35 / Life Q35 | device_manager | A3951DeviceManager | inline / delegated | Default |
| A3028 | Soundcore Life Q30 / Life Q30 | device_manager | A3951DeviceManager | inline / delegated | Default |
| A3028CC | soundcore  Q30 | device_manager | A3951DeviceManager | inline / delegated | Default |
| A3029 | Soundcore Life Tune / Life Tune | device_manager | A3951DeviceManager | inline / delegated | Default |
| A3029XR | A3029XProductModel | device_manager | A3951DeviceManager | inline / delegated | Default |
| A3030 | Soundcore Life Tune Pro / Life Tune Pro | device_manager | A3951DeviceManager | inline / delegated | Default |
| A3033 | Soundcore Life 2 Neo / Life 2 Neo | device_manager | A3951DeviceManager | inline / delegated | Default |
| A3033EU | Soundcore Q10i / Q10i | device_manager | A3951DeviceManager | inline / delegated | Default |
| A3035 | soundcore Space One / Space One | device_manager | Cmm2BtDeviceManager | A3035CmdService | Default |
| A3035CS | soundcore CS 30Q / CS 30Q | device_manager | Cmm2BtDeviceManager | A3035CmdService | Default |
| A3040 | Soundcore Space Q45 / Space Q45 | device_manager | Cmm2BtDeviceManager | A3040CmdService | Default |
| A3045 | Soundcore Life Q20+ / Life Q20+ | device_manager | A3951DeviceManager | inline / delegated | Default |
| A3045ZA1 | soundcore Life Q20+ SE / Life Q20+ SE | device_manager | Cmm2BtDeviceManager | A3004CmdService | Default |
| A3062 | soundcore Space One Pro / Space One Pro | device_manager | Cmm2BtDeviceManager | A3062CmdService | Default |
| A3116 | Soundcore Motion+ / Motion+ | device_manager | A3116BtDeviceManager | inline / delegated | Default |
| A3117 | Soundcore 3 / soundcore 3 | device_manager | A3145DeviceManager | inline / delegated | Default |
| A3118 | Soundcore Motion Boom / Motion Boom | device_manager | A3145DeviceManager | inline / delegated | Default |
| A3119 | Soundcore Mini 3 / soundcore Mini 3 | device_manager | A3145DeviceManager | inline / delegated | Default |
| A3123 | Soundcore Icon+ / Icon+ | device_manager | A3116BtDeviceManager | inline / delegated | Default |
| A3125 | Soundcore Select 2 / soundcore Select 2 | device_manager | A3145DeviceManager | inline / delegated | Default |
| A3126 | Soundcore Select Pro / Select Pro | device_manager | A3145DeviceManager | inline / delegated | Default |
| A3127 | Soundcore Mini 3 Pro / soundcore Mini 3 Pro | device_manager | A3145DeviceManager | inline / delegated | Default |
| A3129 | Soundcore Motion Boom Plus / Motion Boom Plus | device_manager | A3145DeviceManager | inline / delegated | Default |
| A3130 | soundcore Motion X600 / Motion X600 | device_manager | Cmm2BtDeviceManager | A3130CmdService | Default |
| A3131 | soundcore Motion X500 / Motion X500 | device_manager | Cmm2BtDeviceManager | A3131CmdService | Default |
| A3133 | soundcore Motion 100 / Motion 100 | device_manager | Cmm2BtDeviceManager | A3133CmdService | Default |
| A3134 | soundcore Boom 2 Plus / Boom 2 Plus | device_manager | Cmm2BtDeviceManager | A3134CmdService | Default |
| A3134Z11 | soundcore Boom 2 Pro / Boom 2 Pro | device_manager | Cmm2BtDeviceManager | A3134CmdService | Default |
| A3135 | soundcore Motion 300 / Motion 300 | device_manager | Cmm2BtDeviceManager | A3135CmdService | Default |
| A3136 | soundcore Glow Mini / Glow Mini | device_manager | Cmm2BtDeviceManager | A3136CmdService | Default |
| A3138 | soundcore Boom 2 / Boom 2 | device_manager | Cmm2BtDeviceManager | A3138CmdService | Default |
| A3138J11 | soundcore Boom V2 / Boom V2 | device_manager | Cmm2BtDeviceManager | A3138CmdService | Default |
| A3145 | Anker Soundcore Boost / Boost | device_manager | A3145DeviceManager | inline / delegated | Default |
| A3148 | soundcore Boom 2 SE / Boom 2 SE | device_manager | Cmm2BtDeviceManager | A3148CmdService | Default |
| A3161 | Soundcore Flare | direct_spp_link | A3161SppLink | inline / delegated | Default |
| A3162 | Soundcore Flare + | direct_spp_link | A3161SppLink | inline / delegated | Default |
| A3163 | Soundcore Flare S+ / Flare S+ | device_manager | A3163DeviceSppManager | inline / delegated | Default |
| A3165 | Soundcore Flare 2 / Flare 2 | device_manager | A3165DeviceManager | inline / delegated | Default |
| A3166 | soundcore Glow / Glow | device_manager | Cmm2BtDeviceManager | A3166CmdService | Default |
| A3167 | Soundcore Flare Mini / Flare Mini | device_manager | A3145DeviceManager | inline / delegated | Default |
| A3171 | soundcore Select 2S / Select 2S | device_manager | Cmm2BtDeviceManager | A3172CmdService | Default |
| A3172 | soundcore Select 3 / Select 3 | device_manager | Cmm2BtDeviceManager | A3172CmdService | Default |
| A31A3 | soundcore Rave 3 / Rave 3 | device_manager | Cmm2BtDeviceManager | A31A3CmdService | Default |
| A31A3012 | soundcore Rave 3S / Rave 3S | device_manager | Cmm2BtDeviceManager | A31A3CmdService | Default |
| A31X1 | A31X1ProductModel | device_manager | Cmm2BtDeviceManager | A3172CmdService | Default |
| A3201 | Soundcore Life NC / Life NC | device_manager | A3201DeviceManager | inline / delegated | Default |
| A3300 | Soundcore Wakey / Wakey | device_manager | A3300DeviceManager | inline / delegated | Default |
| A3301 | Anker PowerConf / PowerConf | device_manager | A3301DeviceManager | inline / delegated | Default |
| A3302 | Anker PowerConf S3 / PowerConf S3 | device_manager | A3301DeviceManager | inline / delegated | Default |
| A3330 | soundcore C30i / C30i | device_manager | Cmm2BtDeviceManager | A3330CmdService | A3330DataSource |
| A3331 | soundcore C40i / C40i | device_manager | Cmm2BtDeviceManager | A3331CmdService | A3331DataSource |
| A3372 | Soundcore Infini Pro / Infini Pro | device_manager | A3372BtDeviceManager | inline / delegated | Default |
| A3388 | soundcore AeroClip / AeroClip | device_manager | Cmm2BtDeviceManager | A3388CmdService | A3388DataSource |
| A3388P42 | soundcore AeroClip Special / AeroClip Special | device_manager | Cmm2BtDeviceManager | A3388CmdService | A3388DataSource |
| A3390 | Soundcore Rave mini | device_manager | RaveDeviceManager | inline / delegated | Default |
| A3390E | A3390EProductModel | device_manager | A3395DeviceManager | inline / delegated | Default |
| A3390J | A3390JProductModel | device_manager | A3395DeviceManager | inline / delegated | Default |
| A3391 | Soundcore Rave | device_manager | RaveDeviceManager | inline / delegated | Default |
| A3391E | A3391PProductModel | device_manager | A3395DeviceManager | inline / delegated | Default |
| A3392 | Soundcore Mega / Mega | device_manager | RaveDeviceManager | inline / delegated | Default |
| A3393 | Soundcore Trance / Trance | device_manager | RaveDeviceManager | inline / delegated | Default |
| A3395 | Soundcore Rave Neo / Rave Neo | device_manager | A3395DeviceManager | inline / delegated | Default |
| A3396 | Soundcore Trance Go / Trance Go | device_manager | A3395DeviceManager | inline / delegated | Default |
| A3398 | Soundcore R30 / R30 | device_manager | Cmm2BtDeviceManager | A3398CmdService | Default |
| A3399 | Soundcore Rave Party 2 / Rave Party 2 | device_manager | Cmm2BtDeviceManager | A3399CmdService | Default |
| A33A0 | Soundcore Rave Neo SE / Rave Neo SE | device_manager | A3395DeviceManager | inline / delegated | Default |
| A33A1 | Soundcore Rave Neo 2 / Rave Neo 2 | device_manager | Cmm2BtDeviceManager | A33A1CmdService | Default |
| A33A1J11 | Soundcore Rave Neo 2 SE / Rave Neo 2 SE | device_manager | Cmm2BtDeviceManager | A33A1CmdService | Default |
| A3600 | Soundcore Frames / soundcore Frames | device_manager | Cmm2BtDeviceManager | A3600CmdService | Default |
| A3850 | Soundcore VR P10 / VR P10 | device_manager | Cmm2BtDeviceManager | A3850CmdService | Default |
| A3871 | soundcore AeroFit Pro / AeroFit Pro | device_manager | Cmm2BtDeviceManager | A3871CmdService | A3871DataSource |
| A3872 | soundcore AeroFit / AeroFit | device_manager | Cmm2BtDeviceManager | A3871CmdService | A3872DataSource |
| A3873 | soundcore V30i / V30i | device_manager | Cmm2BtDeviceManager | A3871CmdService | A3873DataSource |
| A3874 | soundcore AeroFit 2 / AeroFit 2 | device_manager | Cmm2BtDeviceManager | A3874CmdService | A3874DataSource |
| A3874X | AeroFit 2 AI Assistant | device_manager | Cmm2BtDeviceManager | A3874CmdService | A3874XDataSource |
| A3875 | soundcore AeroFit 2 Pro / AeroFit 2 Pro | device_manager | Cmm2BtDeviceManager | A3875CmdService | A3875DataSource |
| A3876 | soundcore V20i / V20i | device_manager | Cmm2BtDeviceManager | A3876CmdService | A3876DataSource |
| A3878 | soundcore V40i / V40i | device_manager | Cmm2BtDeviceManager | A3876CmdService | A3878DataSource |
| A3909 | Soundcore Liberty 2 Pro / Liberty 2 Pro | device_manager | A3909DeviceManager | inline / delegated | Default |
| A3910 | Soundcore Liberty Air2 / Liberty Air 2 | device_manager | A3909DeviceManager | inline / delegated | Default |
| A3913 | Soundcore Liberty 2 / Liberty 2 | device_manager | A3909DeviceManager | inline / delegated | Default |
| A3926 | Soundcore Liberty Neo 2 / Liberty Neo 2 | device_manager | A3951DeviceManager | inline / delegated | Default |
| A3926Z11 | Soundcore Life Dot 2S / Life Dot 2S | device_manager | A3951DeviceManager | inline / delegated | Default |
| A3927 | Soundcore Life A1 / Life A1 | device_manager | Cmm2BtDeviceManager | A3948CmdService | A3927DataSource |
| A3927EU | Soundcore A1 / A1 | device_manager | Cmm2BtDeviceManager | A3948CmdService | A3927DataSource |
| A3930 | Soundcore Liberty 2 Pro+ / Liberty 2 Pro+ | device_manager | A3951DeviceManager | inline / delegated | Default |
| A3931 | Soundcore Life Dot 2 NC / Life Dot 2 NC | device_manager | A3951DeviceManager | inline / delegated | Default |
| A3931XR | A3931XProductModel | device_manager | A3951DeviceManager | inline / delegated | Default |
| A3933 | Soundcore Life Note 3 / Life Note 3 | device_manager | A3933DeviceManager | inline / delegated | Default |
| A3935 | Soundcore Life A2 NC / Life A2 NC | device_manager | A3951DeviceManager | inline / delegated | Default |
| A3935W | Soundcore Life A2 NC+ / Life A2 NC+ | device_manager | A3951DeviceManager | inline / delegated | Default |
| A3936 | Soundcore Space A40 / Space A40 | device_manager | Cmm2BtDeviceManager | A3936CmdService | Default |
| A3937 | soundcore P41i / P41i | device_manager | Cmm2BtDeviceManager | A3955CmdService | A3937DataSource |
| A3939 | Soundcore Life P3 / Life P3 | device_manager | A3933DeviceManager | inline / delegated | Default |
| A3939WEU | A3939EUProductModel | device_manager | A3933DeviceManager | inline / delegated | Default |
| A3943 | soundcore Life Note C / Life Note C | device_manager | Cmm2BtDeviceManager | A3948CmdService | A3943DataSource |
| A3944 | soundcore Life P2 Mini / Life P2 Mini | device_manager | Cmm2BtDeviceManager | A3948CmdService | A3944DataSource |
| A3944EU | soundcore P2 Mini / P2 Mini | device_manager | Cmm2BtDeviceManager | A3948CmdService | A3944DataSource |
| A3945 | Soundcore Life Note 3S / Life Note 3S | device_manager | A3933DeviceManager | inline / delegated | Default |
| A3947 | soundcore Liberty 4 NC / Liberty 4 NC | device_manager | Cmm2BtDeviceManager | A3947CmdService | A3947DataSource |
| A3948 | soundcore A20i / A20i | device_manager | Cmm2BtDeviceManager | A3948CmdService | A3948DataSource |
| A3948A25 | soundcore A25i / A25i | device_manager | Cmm2BtDeviceManager | A3948CmdService | A3948DataSource |
| A3949 | soundcore P20i / P20i | device_manager | Cmm2BtDeviceManager | A3948CmdService | A3949DataSource |
| A3949P25 | soundcore P25i / P25i | device_manager | Cmm2BtDeviceManager | A3948CmdService | A3949DataSource |
| A3949R50 | soundcore R50i / R50i | device_manager | Cmm2BtDeviceManager | A3948CmdService | A3949DataSource |
| A3951 | Soundcore Liberty Air 2 Pro / Liberty Air 2 Pro | device_manager | A3951DeviceManager | inline / delegated | Default |
| A3952 | Soundcore Liberty 3 Pro / Liberty 3 Pro | device_manager | Cmm2BtDeviceManager | A3952CmdService | Default |
| A3953 | Soundcore Liberty 4 / Liberty 4 | device_manager | Cmm2BtDeviceManager | A3953CmdService | Default |
| A3954 | soundcore Liberty 4 Pro / Liberty 4 Pro | device_manager | Cmm2BtDeviceManager | A3954CmdService | A3954DataSource |
| A3954-Box | A3954BoxProductModel | parent_component | parent A3954 | component / metadata | Default |
| A3955 | soundcore P40i / P40i | device_manager | Cmm2BtDeviceManager | A3955CmdService | A3955DataSource |
| A3957 | soundcore Liberty 5 / Liberty 5 | device_manager | Cmm2BtDeviceManager | A3957CmdService | A3957DataSource |
| A3958 | soundcore A30i / A30i | device_manager | Cmm2BtDeviceManager | A3958CmdService | A3958DataSource |
| A3959 | soundcore P30i / P30i | device_manager | Cmm2BtDeviceManager | A3958CmdService | A3959DataSource |
| A3959R50 | soundcore R50i NC / R50i NC | device_manager | Cmm2BtDeviceManager | A3958CmdService | A3959DataSource |
| A3961 | Soundcore Sport X10 / Sport X10 | device_manager | A3933DeviceManager | inline / delegated | Default |
| A3968 | soundcore Sport X20 / Sport X20 | device_manager | Cmm2BtDeviceManager | A3968CmdService | A3968DataSource |
| A3969 | soundcore R50i VI / R50i VI | device_manager | Cmm2BtDeviceManager | inline / delegated | A3949DataSource |
| A3982 | Soundcore Life Dot 3i / Life Dot 3i | device_manager | A3933DeviceManager | inline / delegated | Default |
| A3982EU | Soundcore Dot 3i / Dot 3i | device_manager | A3933DeviceManager | inline / delegated | Default |
| A3983 | Soundcore Life Note 3i / Life Note 3i | device_manager | A3933DeviceManager | inline / delegated | Default |
| A3983EU | Soundcore Note 3i / Note 3i | device_manager | A3933DeviceManager | inline / delegated | Default |
| A3992 | Soundcore Life A3i / Life A3i | device_manager | A3933DeviceManager | inline / delegated | Default |
| A3992EU | Soundcore A3i / A3i | device_manager | A3933DeviceManager | inline / delegated | Default |
| A3993 | Soundcore Life P3i / Life P3i | device_manager | A3933DeviceManager | inline / delegated | Default |
| A3993EU | Soundcore P3i / P3i | device_manager | A3933DeviceManager | inline / delegated | Default |
| A3994 | soundcore K20i / K20i | device_manager | Cmm2BtDeviceManager | A3994CmdService | A3994DataSource |
| A6610 | Soundcore Sleep A10 / Sleep A10 | device_manager | Cmm2BtDeviceManager | A6610CmdService | Default |
| A6611 | soundcore Sleep A20 / Sleep A20 | device_manager | Cmm2BtDeviceManager | A6611CmdService | A6611DataSource |
| D1101 | soundcore C50i / C50i | device_manager | Cmm2BtDeviceManager | D1101CmdService | D1101DataSource |
| D1105 | Anker AeroClip 2 / AeroClip 2 | device_manager | Cmm2BtDeviceManager | D1105CmdService | D1105DataSource |
| D1200 | Liberty Buds | device_manager | Cmm2BtDeviceManager | D1200CmdService | D1200DataSource |
| D1202 | soundcore P31i / P31i | device_manager | Cmm2BtDeviceManager | D1202CmdService | D1202DataSource |
| D1202C | soundcore R60i NC / R60i NC | device_manager | Cmm2BtDeviceManager | D1202CmdService | D1202DataSource |
| D1203 | A3956XProductModel | device_manager | Cmm2BtDeviceManager | A3956CmdService | A3956DataSource |
| D1203-Box | A3956XBoxProductModel | independent_component | BoxBleDeviceManager | A3956CmdService | Default |
| D1204 | A3956ProductModel | device_manager | Cmm2BtDeviceManager | A3956CmdService | A3956DataSource |
| D1204-Box | A3956BoxProductModel | independent_component | BoxSppDeviceManager | A3956CmdService | Default |
| D1205 | soundcore P42i / P42i | device_manager | Cmm2BtDeviceManager | D1205CmdService | D1205DataSource |
| D1206 | Liberty Buds 2 | device_manager | Cmm2BtDeviceManager | D1206CmdService | D1206DataSource |
| D1301 | soundcore Sleep A30 / Sleep A30 | device_manager | Cmm2BtDeviceManager | D1301CmdService | D1301DataSource |
| D1301-Box | D1301BoxProductModel | parent_component | parent D1301 | component / metadata | Default |
| D1301S | soundcore Sleep A30 Special / Sleep A30 Special | device_manager | Cmm2BtDeviceManager | D1301CmdService | D1301DataSource |
| D1301S-Box | D1301SBoxProductModel | parent_component | parent D1301S | component / metadata | Default |
| D1303 | Anker Sleep Earbuds 4 / Sleep Earbuds 4 | device_manager | Cmm2BtDeviceManager | D1303CmdService | D1303DataSource |
| D1303-Box | D1303BoxProductModel | parent_component | parent D1303 | component / metadata | Default |
| D1402 | Space 2 | device_manager | Cmm2BtDeviceManager | D1402CmdService | Default |
| D1404 | Anker soundcore Q31i / Q31i | device_manager | Cmm2BtDeviceManager | D1404CmdService | Default |
| D1406 | Anker Space 2 Pro / Space 2 Pro | device_manager | Cmm2BtDeviceManager | D1406CmdService | Default |
| D1407 | Anker Space 2 Pro Max / Space 2 Pro Max | device_manager | Cmm2BtDeviceManager | D1406CmdService | Default |
| D3200 | soundcore Work | device_manager | D3200DeviceManager | D3200CmdService | Default |
| D5100 | soundcore Boom 3i / Boom 3i | device_manager | Cmm2BtDeviceManager | D5100CmdService | Default |
| D5103 | soundcore Boom Go 3i / Boom Go 3i | device_manager | Cmm2BtDeviceManager | D5103CmdService | Default |
| Z5180 | Z5180ProductModel | device_manager | CmmBtDeviceManager | inline / delegated | Default |
| Z6111 | Z6111ProductModel | registration_without_model_manager | no model manager | component / metadata | Default |
| D1302 | Anker Sleep Earbuds 4 Pro / Sleep Earbuds 4 Pro | SDK only | SDK profile | SDK modules | D1302DataSource |
| D1302-BOX | D1302BoxProductProfile | SDK only | SDK profile | SDK modules | Default |
| D5160 | D5160ProductProfile | SDK only | SDK profile | SDK modules | Default |
| D1102 | soundcore AeroClip 2 Pro / AeroClip 2 Pro | SDK only | SDK profile | SDK modules | D1102DataSource |

## Review artifacts

[MODEL_PROTOCOL_ROUTES_5_0_35.json](MODEL_PROTOCOL_ROUTES_5_0_35.json) holds per-key bindings, inherited method identities, UI call sites, specialized prefix paths, version-helper references and ByteBuffer writer calls. Writer calls are in DEX instruction order within a method; branches and loops remain candidates, not a completed ordered payload schema. [Static constants](STATIC_COMMAND_PREFIXES_5_0_35.csv) preserve original DEX field identities rather than unstable JADX aliases.

[EQ_GAIN_EVIDENCE_5_0_35.json](EQ_GAIN_EVIDENCE_5_0_35.json) records literal bean positions and exact EQ list-builder/provider calls. Constructor ordinal is not preset index. The existing runtime [EQ table CSV](EQ_TABLES_5_0_35.csv) remains separate; neither table alone assigns curves, frequencies or enabled presets to every model.

## Remaining analysis

The following is still to do, not an absence-of-support claim: evaluate each model/firmware/state gate; bind each preset index/name to its gain, frequency, type and Q vectors; finish gesture-specific action choices; reconstruct ordered reply schemas and unsupported-buffer handling; and extend offline payload checks to the remaining families. Shared/inherited commands must not become enabled features without those checks. Declared native activities/lifecycle methods remain outside the instruction-store recovery.

The online catalogue and per-product TD/JavaScript files are a separate completeness requirement. The restored catalogue helpers are available for further authentication/routing analysis. The existing guest profile was not changed or retried here.

External reproduction: `per-model-analysis/` under the research root holds `DexProtocolIndex.java`, `map_protocol_routes.py`, `extract_static_prefixes.py`, `ControlFactoryProbe.java`, `map_control_sources.py`, `LegacyPayloadProbe.java`, `extract_eq_vectors.py` and this document generator. All tools and vendor source stay outside Git.
