# Soundcore 5.0.35-435: protocol recovery evidence

Research date: 2026-11-13. Branch: `research/soundcore-full-protocol`, based on main `4809617d30c32fd2cb08377accc50eed601fe5e2`. Application implementation was not changed.

## Input and provenance

Package `com.oceanwing.soundcore`, versionName `5.0.35`, versionCode `435`. Input APKM SHA-256:

`b7acfeee5c506879e5870a8215562b8b0cd896a93aaa54cd3eb28c24cdc6e734`

All ten APKM members were retained, including the two feature splits. The original APKM and extracted vendor APKs were not altered. Disposable, separately signed research APKs added the packer's x86_64 ABI helpers; a later experiment added an upstream x86_64 MMKV library. Those APKs, their signing material, all DEX images and decompiled/vendor code remain outside Git.

## What finally worked

1. A clean, offline API 30 x86_64 emulator ran the disposable research installation. No account or physical Bluetooth peripheral was connected.
2. An external root memory reader captured structurally validated DEX images without injecting into the app. This yielded 25 DEX images with 82,576 class definitions, including actual vendor business classes. The earlier loader/framework-only dumps were insufficient and are not counted as business-code recovery.
3. Many instruction arrays in those DEX images were still return/zero padding. Their repurposed debug offsets identify an instruction store: `index = 0x04000000 - debug_info_off`. All 297,210 distinct indices from 0 through 297,209 were present, exactly matching the count in the `ijiami.ajm` header. This is structural evidence, not recovery of those method bodies.
4. A same-signer Android instrumentation harness allowed the packer to supply the business class loader, then skipped the normal application's `onCreate`. This avoided unrelated startup failures and worked where Frida attachments had failed.
5. Loading and resolving targeted protocol classes caused their live instruction arrays to be restored. The harness captured the corresponding live DEX images, rather than assuming that a nonzero code item contained meaningful instructions. Decompiled copies have repaired SHA-1/Adler-32 header checksums; raw captures are also retained.
6. The measured selection contains **2,984 protocol/model/control classes and 27,002 declared methods, with zero instruction-store markers remaining in that selection**. It includes legacy command and analysis services, model registration, transport classes, modern SDK profiles, control/EQ classes and builders declared outside the usual protocol packages. A further pass followed inline helper references, using DEX class names to avoid decompiler-only aliases.

The first protocol checkpoint measured **28,978 changed instruction arrays and replaced markers** across the captured images. That includes incidental dependencies. It is not a claim that all 297,210 protected methods in the whole application were recovered. Decompiler warnings and unrelated protected classes remain; the selected builder catalogue has no `Method not decompiled` failures.

## Offline observations

- Executing `SeriesHelp.getInitProductModel()` returned 161 app registration keys. All 161 model bodies are readable in the recovered source set.
- The separate SDK factory contains 14 product/component profiles and one SAMPLE scaffold. App and SDK registries have **165 distinct keys**, case-normalizing case-component suffixes and excluding SAMPLE.
- The recovered code also supports downloaded Things/OMC product metadata. Therefore 165 is the compiled-registry inventory, **not a proven ceiling on everything the installed app can support after downloading resources**. `BaseSeriesHelp.m()` merges cached online product definitions into the model map through `ThingsProductModel`. Those online definitions and their schema coverage still require separate inventory work. Existing preferences/accounts were not read.
- Model getters supplied SPP UUIDs without initializing or connecting device managers. Six state-dependent `isBoxConnected()` getters threw because no live device state existed; no box-presence result is asserted for them.
- Reflection captured 683 explicitly scoped command constants and 408 EQ-related vectors from 44 static tables. The vectors include gains, filter types, frequencies and Q values; they are not 408 interchangeable named presets.

## Virtual serialization checks

Eight cases were exercised against recovered vendor methods in the offline emulator. The wrapper calls only `BaseBtDeviceCommand.c(header,payload)`, which constructs bytes; transport-send methods were not called.

| Case | Input | Observed output |
| --- | --- | --- |
| Checksummed wrapper | prefix `08ee0000000681`, empty payload | `08ee00000006810a0087` |
| Checksummed wrapper | same prefix, payload `00` | `08ee00000006810b000088` |
| Checksummed wrapper | same prefix, payload `00020507` | `08ee00000006810e000002050799` |
| Unchecksummed wrapper | same prefix, empty byte array | `08ee00000006810900` |
| Unchecksummed wrapper | same prefix, payload `00` | `08ee00000006810a0000` |
| Unchecksummed wrapper | same prefix, payload `00020507` | `08ee00000006810d0000020507` |
| D1206 button builder | left, gesture 2, non-TWS function 7, TWS function 5 | prefix `08ee000000048c`, payload `00020507` |
| D1206 button builder | right, same values | prefix `08ee000000048c`, payload `01020507` |

These checks establish serialization for those inputs. They do not establish that gesture/function values are available on every device, that firmware accepts them, or that a bud changes state. Registration getters, class recovery and data-table extraction must not be described as complete virtual device emulation.

## Research artifacts outside Git

Root: `E:/Builds/research/soundcore-full-protocol-2026-11-13`.

- `runtime-x86-signed/`: initial captured DEX images.
- `instruction-placeholder-measurement.json`: exact instruction-store census.
- `instrumentation/`: harness source, target list and disposable test APK.
- `instrumentation-output/pairfree-instrumentation/`: live DEX, registry, constant, EQ and serialization observations.
- `live-fixed/` and `live-jadx/sources/`: checksum-repaired analysis copies and recovered Java.
- `protocol-class-index.json`, `live-recovery-measurement.json`: coverage measurements.
- `model-route-facts.json`, `sdk-profile-facts.json`: derived registration/profile facts.

A later Things pass recovered another 351 selected classes / 2,353 methods with no remaining instruction-store markers; see [Things protocol research](THINGS_PROTOCOL_5_0_35.md).

Use these artifacts to continue semantic protocol analysis. Keep vendor implementation and raw artifacts outside Git; commit only derived protocol facts and documentation.

## Follow-up observations

The [expanded virtual checks](VIRTUAL_PROTOCOL_CHECKS_5_0_35.md) add 128 shared button-writer cases and 13 parser/parametric-EQ cases. An additional 95 script-discovery/request-routing classes (601 methods) were recovered with zero instruction-store markers remaining in that measured selection. Their helpers and unrelated app code are not implied to be fully recovered. The latest artifact tree for this pass is `discovery-jadx/`; script lookup and request construction are documented in the Things protocol notes.


## Native declarations and per-model follow-up

Zero instruction-store markers only measures restoration of that protection mechanism. The original 2,984-class selection also contains **188 declared native methods in 17 classes**, mainly activities, popups and lifecycle code. It must not be described as full recovery of those methods. The [per-model evidence](MODEL_FEATURES_5_0_35.md) records 165 compiled-registry keys, declared home-VM bounds, reachable home-card factories, explicit serializer routes, 17 legacy More-panel branches and A3028/A3028CC firmware-dependent manager migration. The latest source/mapping directory for this follow-up is `C:/Users/Nima/AppData/Local/PairFreeResearch/soundcore-feature-mapping-2026-11-13/`.
