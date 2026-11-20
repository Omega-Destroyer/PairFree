# Soundcore protocol recovery runbook

**For unpacking from the original APK/APKM, start with the [successful-path guide](UNPACKING_GUIDE.md).** It documents the portable build-checked launcher validated on 2026-11-19. The Android instrumentation sections below explain the original calibration and subsequent runtime protocol experiments; that Android setup is not needed to repeat the supported build's offline recovery.

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

**Restored-code model analysis:** [The expanded route matrix](MODEL_PROTOCOL_ROUTES_5_0_35.md) covers all 165 compiled keys, 84 route groups, exact control-factory dispatch and separate source/action/preset evidence. Its external `per-model-analysis/` directory contains the DEX call-index extractor, bounded factory/serializer interpreters, metadata generators and `verify_analysis.py`. Verification checks all 23 restored DEX hashes, model/factory agreement, original field identities, 139 runtime prefix overlaps, six serializer/framer cases and local document links. It does not evaluate every firmware/state branch or prove physical support.

**Refined condition/preset/reply pass:** `ControlChoiceProbe.java` now evaluates all selected normal choice-list sources, with predicates abstracted at their result boundary in 912 cases and actual version-helper DEX in 432 additional cases. `LegacyReplyProbe.java` runs the earlier six serializer checks plus 30 reply-parser checks. `bind_eq_presets.py` binds the source list builders; `EqSelectorProbe.java` independently verifies the integer dispatch in 35 cases. The corrected gain extractor distinguishes nine-float-plus-int validCount constructors from position-only overloads, crosschecked against all 192 corresponding DEX constructor calls.

For a fresh reproduction of this build, retain `control-base-fields.json` (20 original-field/source-label bindings), run `ControlFactoryProbe`, then `ControlChoiceProbe` with arguments: restored DEX directory, `control-base-fields.json`, `control-factory-routes.json`, and output `control-choice-scenarios.json`. Run it before `map_control_sources.py`, which now uses the checked enum field-name mapping. `LegacyReplyProbe` takes the same first three arguments as the serializer probe plus reply-output path. Run `extract_eq_vectors.py`, then `bind_eq_presets.py`; run `EqSelectorProbe` against restored `4e2fdef22111bc7e.dex`, `eq-preset-bindings.json`, and output `eq-selector-cases.json`. Finally run `write_analysis_docs.py`, `write_refined_analysis_docs.py`, `verify_analysis.py` and `verify_refined_analysis.py`. The generator order matters: the refined generator adds the new follow-up links after the base generator. Compile the six Java probe/index classes together on the same JADX JAR classpath. All tooling and vendor artifacts remain outside Git.

Reproduction uses the installed JBR and `E:/Tools/jadx-1.5.6/lib/jadx-1.5.6-all.jar` (which supplies dexlib2 and Gson). Compile `DexProtocolIndex.java`, `ControlFactoryProbe.java` and `LegacyPayloadProbe.java` together outside Git. Run the indexer against `catalogue-security/native-offline-restored/`, then `extract_static_prefixes.py`. The control probe takes restored `140c38477afd34c7.dex`, `compiled-model-inputs.json` and output `control-factory-routes.json`; its constructors are recorded rather than executed. The legacy probe takes the restored DEX directory, `static-prefixes.json` and output `legacy-payload-cases.json`. Then run `map_control_sources.py`, `map_protocol_routes.py`, `extract_eq_vectors.py`, `write_analysis_docs.py` and `verify_analysis.py`. The full call graph stays external; only derived evidence is published. Preserve the distinction between exact method identities in DEX, JADX-renamed source identifiers and source-constructor ordinals in the EQ vector data.

Zero instruction-store markers only measures restoration of that protection mechanism. The original 2,984-class selection also contains **188 declared native methods in 17 classes**, mainly activities, popups and lifecycle code. It must not be described as full recovery of those methods. The [per-model evidence](MODEL_FEATURES_5_0_35.md) records 165 compiled-registry keys, declared home-VM bounds, reachable home-card factories, explicit serializer routes, 17 legacy More-panel branches and A3028/A3028CC firmware-dependent manager migration. The latest source/mapping directory for this follow-up is `C:/Users/Nima/AppData/Local/PairFreeResearch/soundcore-feature-mapping-2026-11-13/`.


## Failed-current-run guard (2026-11-14)

The external runner now checks the instrumentation completion marker and result code before pulling outputs. `am instrument` can exit at the shell with code zero while reporting `Process crashed`; pulling an existing folder afterward yields stale observations. The latest security pass failed to reproduce a complete capture on both the old lab and a fresh guest, so it claims no new crypto definitions. See [security follow-up](SECURITY_FLOW_5_0_35.md) for the tested conditions. Its lab metadata/clock were restored and headless guests shut down; old successful host captures remain available.

## Model EQ selection and normalized filter follow-up (2026-11-15)

[Model selections and filter payloads](EQ_MODEL_SELECTION_5_0_35.md) adds DEX/source agreement for 40 annotated provider routes and 36 inherited holder types, plus 432 getter crosschecks, 20 actual predicate cases and seven offline serializer cases. D1102 is an additional provider route outside the 165-key compiled registry; Things is a separate dynamic route. D1105's zero/negative table values are retained and flagged without assigning firmware semantics.

All new tools remain in the external `per-model-analysis/` directory. Compile `EqModelRouteProbe.java`, `EqFilterProbe.java` and `EqVersionProbe.java` on the existing JADX JAR classpath alongside `DexProtocolIndex.java`, `ControlFactoryProbe.java` and `ControlChoiceProbe.java`. Run:

1. `EqModelRouteProbe <restored-DEX-directory> ../registry-models.json eq-model-dex-routes.json`.
2. `EqFilterProbe <restored-DEX-directory> eq-filter-getter-cases.json static-prefixes.json eq-normalized-payload-cases.json`.
3. `EqVersionProbe <restored-DEX-directory> eq-normalized-version-cases.json`.
4. `python map_eq_model_selection.py`, then `python write_eq_selection_docs.py`, then `python verify_eq_selection.py`.

Use the prior generated `eq-preset-bindings.json`, `dex-protocol-index.json`, model route JSON and static prefix inventory. Run this writer after the earlier documentation writers, which can overwrite their follow-up links. Provider annotation suffixes and numeric tables are checked independently against source; no arbitrary route literals are exported. Point getters, device state, command-bean setters and diagnostics are modeled in serializer checks. These checks execute no Android lifecycle, outer packet framing, checksum or transport. Keep `verify_analysis.py` and `verify_refined_analysis.py` passing as regression checks. Do not confuse the 432 table-getter cases with the earlier 432 control-firmware cases.

## Sleep A30 file upload and offline-mode follow-up (2026-11-15)

The [A30 protocol specification](SLEEP_A30_UPLOAD_PROTOCOL_5_0_35.md) links the restored preparation/transfer path to the current native writer and distinguishes Local playback, the vendor's network-gated upload flow and a proposed offline PairFree library. Exact derived cases are in `SLEEP_A30_UPLOAD_EVIDENCE_5_0_35.json`. The 92-byte transfer configuration, custom IDs, slot semantics and physical acceptance remain further work; a packet-builder check is not a complete upload.

External tools remain under `per-model-analysis/`. Compile `SleepAudioProbe.java` with the existing `ControlChoiceProbe.java`, `EqFilterProbe.java`, `ControlFactoryProbe.java` and `DexProtocolIndex.java` dependencies on the JADX JAR classpath. Run `SleepAudioProbe <restored-DEX-directory> sleep-a30-probe-cases.json sleep-a30-prefix-inputs.json`. The prefix input is a one-record JSON array for original DEX field `Lcom/oceanwing/devicecmd/manager/cmmbt2/Cmm2CmdData;->g0:[B`, taken from the prior runtime constant CSV (class Cmm2CmdData, field g0: `08ee00000001a9`); retain that provenance rather than matching JADX's generated alias. The probe executes only selected getters and small builders with modeled state, no file transfer or device access.

Extract only `lib/arm64-v8a/libscnativelib.so` from the original APKM's base APK into external `per-model-analysis/sleep-a30-native/lib_arm64-v8a_libscnativelib.so`. Verify its SHA-256 is `fa32df302d5b717d723223dc7b6c94a1d6c504581a929c46c0a31ee357e5a8a0`. `python sleep_a30_native_probe.py` reuses the older external libc/file-I/O harness from `E:/Builds/research/soundcore-a30-2026-11-09/sbc_container_emu.py` and the JBL research emulator dependencies, but explicitly loads this new library and supplies all eight native arguments. It runs 48 synthetic comparisons with no codec encoding or Bluetooth. The current native routine begins at 0x59AE8; do not reuse the old 6.4.0 address.

Then run `python write_sleep_a30_evidence.py` and `python verify_sleep_a30.py`. Prose is hand-maintained. The redacting `SafeDexMethodDump.java` can inspect malformed coroutine Java without printing string literals; the preserved `sleep-a30-native/wrap-array-dex.txt` records the vendor-type-3 guard in method N. Keep previous analysis, refined-control and EQ-selection checks passing after interpreter extensions. Recording implementation changes are not part of this research: the documented 128/256-byte and separate-BES-channel gaps still require a follow-up before promising complete filtered transfer diagnostics.
