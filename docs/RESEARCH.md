# New vendor app research

Research pass dated 2026-11-06, requested as documentation only. No application/protocol source,
runtime device/command data, supported-device pages, generated tables, versions, or permissions were
changed. None of these vendors was tested with PairFree or connected during the research. Existing
EarFun work already has its own protocol document; the five vendors below lacked equivalent notes.

## 1. Reading order and coverage

| Vendor | App examined | Research documents | What was recovered | Important limit |
|---|---|---|---|---|
| OnePlus / OPPO | HeyMelody 117.6.6 | [Protocol](oneplus/PROTOCOL.md), [85 catalog records](oneplus/MODELS.md), [69 capability bits](oneplus/CAPABILITIES.md) | OPOv1 framing, negotiation, responses, battery/touch/noise/EQ writes, authenticated local catalog decoding | Finish semantic review of all response branches and per-feature enum/firmware predicates; hidden entries are leads |
| Audio-Technica | Connect 2.8.0 | [Protocol](audiotechnica/PROTOCOL.md), [models and predicates](audiotechnica/MODELS.md), [command namespaces](audiotechnica/COMMANDS.md) | Hermes v96 decompilation, 28 model definitions, four chip/protocol families, command tables, native EQ entry points | Complete all payload/notification/native-math behavior per model; 28 includes speakers/hearing assist |
| Skullcandy | Skullcandy 5.0.6 | [Protocol](skullcandy/PROTOCOL.md), [56 catalog records](skullcandy/MODELS.md), [Bragi services](skullcandy/SERVICES.md) | Product/firmware features, Bragi schemas/CRC/RPC/security paths, native/Flutter inspection | Dart per-model custom routes are not fully decoded; seven records are speakers and some are metadata-only |
| Cambridge Audio | Melomania 2.8.12 | [Protocol](cambridge/PROTOCOL.md) | 1+/Touch GAIA commands, EQ curves/scaling, control mask, model selection; separately attributed A100 reference | A100/M100/P100 redirect to Melomania Connect, which is absent from the supplied APKs |
| Huawei | AI Life 11.0.2.305 | [Protocol](huawei/PROTOCOL.md) | Embedded Smart Audio plugin, SPP/CRC/TLV protocol and older settings; newer-model reference evidence | Old vendor plugin is not a full modern model table; newer layouts are explicitly attributed |

Each protocol document identifies the source classes, APK fingerprint, transport, message format,
settings/EQ/controls, unsafe/private operations, validation and unfinished analysis. This is a research
handoff, not an implementation-ready guarantee for every model. No missing detail was filled with a
number borrowed from another brand. Source examples are labeled analytical, not recordings.

## 2. Reference APK provenance

All APKs came from the existing `PairFree Reference APKs` folder beside the repository. They were
extracted outside the repository. SHA-256 values below fingerprint the original downloaded bundle or
monolithic APK, not an extracted base APK.

| Vendor | Package | Version | Reference bundle/APK SHA-256 |
|---|---|---|---|
| OnePlus / OPPO | `com.heytap.headset` | 117.6.6 | `c646b6f6bf86947a5c637af4eae52861dc324b974e55075e118bd158fb0427b1` |
| Audio-Technica | `jp.co.audiotechnica.connect` | 2.8.0 | `0c12e95c43111a3253112581521e7df77994470861108dcf07985d776b83685e` |
| Skullcandy | `app.skullcandy` | 5.0.6 | `255153c95a3a12131af83350dc962a22b20eb49b6e73ce4a92800e82af254e9a` |
| Cambridge Audio | `com.cambridgeaudio.melomania` | 2.8.12 | `0e4e2ddb205bfb3af2a8b4a83add8180a7854407a6163eaa93f026d05cdf1ba6` |
| Huawei | `com.huaweioverseas.smarthome` | 11.0.2.305 | `f09948106562ea930055f657a72391b58e139b3b6c1d659b326d28b772e9f5f4` |

All splits were inventoried, including language/density and ABI splits. APK counts and native-library
entries across all ABIs are:

| Extracted vendor folder | APK files examined | Native .so entries |
|---|---|---|
| `oneplus` | 25 | 4 |
| `audiotechnica` | 20 | 27 |
| `skullcandy` | 4 | 26 |
| `cambridge` | 20 | 1 |
| `huawei` | 1 | 11 |

The supplied Audio-Technica native split is armeabi-v7a; Skullcandy's is arm64-v8a. Huawei is a single
APK with both ABI trees. Listing native entries does not mean every native routine has been semantically
decoded; each vendor note states the inspection depth and remaining native work.

## 3. External research artifacts and tools

All new raw artifacts, downloaded public reference code, decoded vendor data and disassembly remain in
`E:\Builds\research\vendor-audit-2026-11-06`. Earlier source decompilations are under
`E:\Builds\research\decompiled`. Nothing from those source trees was copied into this repository.
The local NAS reference APKs were not modified. No source control commit or push was made.

- jadx 1.5.6 was already installed. Cambridge's full pass reported 31 errors; Huawei's outer app 66;
  the embedded Huawei Smart Audio plugin completed with no errors. Decoded critical methods were
  inspected directly. A decompiler error is not proof of packing or protocol inaccessibility.
- HeyMelody's large dispatcher initially failed structured decompilation. Single-class fallback mode
  recovered its instructions successfully, pinning negotiation ids and response parsing. The external
  path is `oneplus/PollCommandManager-fallback.txt/l.java`.
- hermes-dec revision `2e796663e1fedb93dca51060797ebf3327b3fedb` successfully decompiled the
  Audio-Technica v96 bundle into `audiotechnica/bundle.js`, exposing model definitions, predicates,
  commands and builders. Line references in its model index refer to that external output.
- HeyMelody's bundled model file was authenticated/decrypted locally with the app's public certificate
  hash and decompressed. The key stayed in memory and the decoded vendor JSON stayed outside the repo.
  `whitelistSecretKey` belongs to the separate random-key cache and was not needed.
- capstone/pyelftools from the existing external tool folder enumerated native exports and opened EQ
  JNI entry points for Audio-Technica and Skullcandy. Addresses/hashes are in the vendor notes.
- A local crypto package was installed only under the audit folder for bundled-file decryption;
  no project/global dependency was added. No vendor `secrets` file was read.

The old `findings-2026-11-03-new-vendors.md` is an external preliminary note, not current project truth.
Its wrong-app entries for Huawei/Cambridge, tool limitation for Audio-Technica, and HeyMelody
cache/negotiation assumptions are superseded by the primary-source checks documented here.

## 4. Public reference checks

| Project/tool | Revision checked locally | Use |
|---|---|---|
| [OpenFreebuds](https://github.com/melianmiko/OpenFreebuds) | `0b4f14c0b500d491f0fb4ea38f2984ec334e3649` | Huawei framing, newer commands and model handlers; GPL-3.0, facts only |
| [BudsLink](https://github.com/maniacx/BudsLink) | `0eb4d07dcd7eafabf425d98c607e02fc9519c94b` | Modern Cambridge A100 protocol and model config; GPL-3.0, facts only |
| [Earshot](https://github.com/HellzYeahh/earshot) | `a581f96efb5a91b0622a5be9600a02a72b4e1649` | Bragi/Crusher reference, explicitly untested hardware; MIT, no code copied |
| [hermes-dec](https://github.com/P1sec/hermes-dec) | `2e796663e1fedb93dca51060797ebf3327b3fedb` | external analysis tool, no project runtime dependency |

Gadgetbridge OPPO documentation and TouchLock were checked as public OPOv1 leads; FreeBuddies was
identified as a further Huawei reference. Their limited evidentiary role is stated in the vendor notes.
No AGPL or unlicensed source code was ported. The Bragi STATUS difference is recorded in
[UPSTREAM.md](UPSTREAM.md); no message was posted to an upstream project.

## 5. Validation and next implementation work

Analytical checksum examples were independently recalculated: Huawei's battery-tag-1 read is
`5A 00 05 00 01 08 01 00 F5 3D` (CRC-16/XMODEM; standard check string gives `31C3`), and Bragi's
`device.dev_info` example has header CRC `2D` and FCS `5480`, matching the public reference example.
These are static derivations, not traffic sent to a device or hardware test vectors. No application
build/tests were needed for this documentation-only change.

No vendor server was contacted. Public source repositories/tooling were downloaded for research;
that is separate from making an authenticated vendor request. No firmware was fetched, no pairing,
reset, update, power, calibration, audio-playback or Bluetooth command was executed, and no Android
permission or device ownership changed.

For future work, start from the chosen model's complete configuration and source predicates; preserve
protocol-family and firmware differences. Derive safe command/model data and parser cases, integrate
through PairFree's existing identification/session/report architecture, then validate actual hardware
or recordings. Never treat an unconditionally permitted SDK command, a bundled schema, a manifest
feature union, or a virtual test as a safe command or a confirmed supported device. Each vendor document
lists concrete analysis tasks still open; none has been relabeled impossible merely because it is
native code, bytecode, a nested plugin, or a model-specific path.
