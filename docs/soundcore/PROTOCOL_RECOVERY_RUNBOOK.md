# Soundcore protocol recovery runbook

This records the working 5.0.35-435 investigation. Most mechanical steps are scripted outside Git. It is not an end-to-end unpacker guaranteed to work on every future release: packer layout, ABI helpers, application factory behavior and method protection must be checked again for a new input.

## Inputs and workspace

- Vendor input folder: `X:/Coding/PairFree/PairFree Reference APKs/Anker Soundcore`.
- Research root: `E:/Builds/research/soundcore-full-protocol-2026-11-13` (called `R` below).
- Isolated worktree: `R/worktree`, branch `research/soundcore-full-protocol`. Do not alter the user's active checkout.
- SDK: `C:/Users/Nima/AppData/Local/Android/Sdk`; Java: Android Studio's `jbr`.
- JADX: `E:/Tools/jadx-1.5.6`.
- Original extraction/research ABI APKs: `E:/Builds/research/soundcore-unpack-2026-11-11/5.0.35`.

Read the active project's `CLAUDE.md` first. Vendor APKs, SDK code, decrypted artifacts and signing material stay outside Git. Do not open secret assets or existing account/preferences data. Only derived protocol facts and synthetic test observations are committed.

## 1. Fingerprint the whole input

Record package/version/build and archive SHA-256. Enumerate every APKM/XAPK member, including ABI and feature splits. The cross-vendor scanner and its current-input report are on the separate `research/apk-protection-audit` branch (`bf9e1cd`); its external script is `C:/Users/Nima/AppData/Local/PairFreeResearch/apk-protection-audit-2026-11-13/scan_apks.py`.

For this input the protection is iJiami. Do not infer the same method from version numbering or apply iJiami's instruction markers to QCY's Jiagu-family shell. Keep version order and source provenance explicit.

## 2. Prepare an isolated offline guest

The lab AVD is `PairFree_Soundcore_Clean30` (API 30 x86_64), with ADB serial `emulator-5602`. Its `ANDROID_AVD_HOME` is `C:/Users/Nima/AppData/Local/PairFreeResearch/soundcore-full-protocol-2026-11-13/avds`. Start it headless with no snapshots, audio or external DNS; wait until `sys.boot_completed` is `1` and the activity service is available before issuing package/instrumentation commands. An ADB connection alone is not proof that Android services are ready.

Use root only on this disposable guest. Disable Wi-Fi and mobile data before launching the vendor process. The existing lab permits hidden API reflection and `/proc/self/mem` inspection. Do not apply these settings to user phones or unrelated emulators.

The lab already has a same-signer research installation. A fresh installation needs all splits re-signed consistently with the disposable research key. Original ARM-only input failed on x86; the research base made the packer's own x86_64 helpers available under the appropriate native ABI directory. An upstream MMKV ABI was investigated for ordinary startup, but the successful capture avoids application startup altogether. See the recovery evidence before changing an APK or ABI patch.

## 3. Establish that the dump is business DEX

`R/proc_dex_scan.c` and its compiled `proc-dex-scan` are the external read-only process-memory scanner. Its first useful output is in `runtime-x86-signed/`. Verify DEX structure, defined classes and actual vendor package coverage. The earlier loader/framework-only dumps were not sufficient.

`measure_placeholders.py` measures extracted method markers. For this APK all 297,210 unique indices exactly match the instruction-store header count. That proves coverage of the index space, not recovered instructions. Nonzero code-item lengths and return/zero padding are not a successful unpack.

## 4. Restore selected protocol bodies

The working harness is `R/instrumentation/src/io/pairfree/research/ProtocolInstrumentation.java`. It allows the packer to supply the business class loader through application instantiation, skips the normal application's `onCreate`, and resolves only a targeted class list. It then reads live DEX through the resolved classes' DEX metadata.

Build the harness with `javac` against the installed SDK platform JAR, then `d8` at min API 26. `instrumentation/run_probe.py` packages/signs the test APK, installs it and runs `am instrument`; it does **not** compile changed Java source itself. Rebuild both `.class` and `classes.dex` before using it after a source change. Its disposable signing key belongs to this research, not the vendor.

Push `instrumentation/targets.txt` to `/data/local/tmp/pairfree-class-targets.txt` before running the harness. The broad button sweep separately reads `button-service-targets.txt` from `/data/local/tmp/pairfree-button-targets.txt`. Use DEX descriptors to generate runtime names. JADX names such as `Outer.1` and renamed Kotlin coroutine classes are not necessarily valid reflection names.

The harness writes only its own outputs under the target application's `files/pairfree-instrumentation`. Pull that directory, not the whole application-data tree. The local output layout from `adb pull` is `instrumentation-output/pairfree-instrumentation/`; make sure analysis does not read stale files left at the parent level by an earlier pull.

## 5. Validate recovery and observations

Run these external scripts from `R` as appropriate:

```powershell
python fix_live_headers.py
python index_protocol.py
python measure_live.py
python verify_research.py
python verify_new_protocol_cases.py
```

`fix_live_headers.py` creates analysis copies with repaired DEX checksums; raw images remain in the pull directory. `index_protocol.py` indexes selected classes and method markers. `measure_live.py` compares instruction arrays to the initial capture. The two verification scripts check synthetic outputs independently of the vendor methods; the newer one also checks each button-format override against the recovered DEX hierarchy.

Inspect each output's errors and scope. Instrumentation returning success does not prove every class initialized, every method recovered, or every case matched. The unconnected A3300 singleton holder has an initialization error outside the successful packet checks. Zero remaining markers is scoped to the measured classes, not the whole APK. Some restored methods still require bytecode analysis when JADX cannot reconstruct Java.

When selecting classes, follow inheritance. A first builder-only selection missed classes that only override capability/preset methods. The complete shared-builder sweep currently has 64 concrete classes. Never equate a successful target count or inherited method with a supported feature for every device.

## 6. Decompile and research semantics

Feed checksum-repaired images from `live-fixed/` into JADX outside Git. `live-jadx/` is the first main protocol tree; `discovery-jadx/` is the later script/request-discovery tree, and `td-jadx/` is a focused repository decompile. Wait for decompilation to finish before treating an old output file as a new result.

The external generators `write_registry_docs.py` and `write_command_docs.py` produce registration/route inventories and derived catalogues. They are starting points, not a substitute for tracing feature screens, firmware gates, request sequencing and ordered reply layouts. Review before running prose generators: they can overwrite hand-edited notes from a later pass. Command constants should use runtime field names instead of JADX's unstable generated aliases.

For new releases, diff registration keys, explicit model routes, UUIDs, command constants, capability methods, parameter formats, EQ tables and parser schemas. Exercise changed serializers/parsers with synthetic buffers, including firmware boundaries and malformed input, before raising the evidence category. Trace Things/OMC product definitions separately; compiled registry counts are not the whole online catalogue.

## 7. Save a reviewable checkpoint

Commit only derived docs/data on the isolated research branch. Record the input hash, scope, checks and unresolved work. Do not push unless requested. Stop only the lab emulator you started and retain the external artifacts and continuation notes. Documentation must distinguish code recovery, virtual parsing/serialization and physical hardware verification; none of this work establishes physical device acceptance.


## Native declarations and per-model follow-up

**2026-11-15 recovery path:** [Offline instruction-store recovery](OFFLINE_UNPACK_5_0_35.md) now restores all 297,210 marked methods across 23 DEX files without completing the Android loader. It documents the native bootstrap, inflater oracle, complete record parsing, 49,460 withheld-method comparisons and reproducible external scripts. Use its `catalogue-security/native-offline-restored/` and `catalogue-security/offline-restored-jadx/` trees for further analysis. This supersedes dependence on additional runtime class captures for instruction-store bodies; it does not recover declared native methods or prove cloud authentication.

Zero instruction-store markers only measures restoration of that protection mechanism. The original 2,984-class selection also contains **188 declared native methods in 17 classes**, mainly activities, popups and lifecycle code. It must not be described as full recovery of those methods. The [per-model evidence](MODEL_FEATURES_5_0_35.md) records 165 compiled-registry keys, declared home-VM bounds, reachable home-card factories, explicit serializer routes, 17 legacy More-panel branches and A3028/A3028CC firmware-dependent manager migration. The latest source/mapping directory for this follow-up is `C:/Users/Nima/AppData/Local/PairFreeResearch/soundcore-feature-mapping-2026-11-13/`.


## Failed-current-run guard (2026-11-14)

The external runner now checks the instrumentation completion marker and result code before pulling outputs. `am instrument` can exit at the shell with code zero while reporting `Process crashed`; pulling an existing folder afterward yields stale observations. The latest security pass failed to reproduce a complete capture on both the old lab and a fresh guest, so it claims no new crypto definitions. See [security follow-up](SECURITY_FLOW_5_0_35.md) for the tested conditions. Its lab metadata/clock were restored and headless guests shut down; old successful host captures remain available.
