# Soundcore 5.0.35: offline instruction-store recovery

**Reproduction update, 2026-11-19:** Use the [successful-path guide and portable launcher](UNPACKING_GUIDE.md) to repeat this recovery from the original APK/APKM. The new launcher was tested in fresh directories, matches all 23 restored reference DEX hashes and no longer requires the earlier live-capture workspace during normal use. The historical commands below describe the original validation; they are not the recommended new-user entry point.

## Result (2026-11-15)

The native recovery path now restores **all 297,210 instruction-store methods in 23 DEX files**, including the previously missing **279 marked methods across 44 security/helper classes**. All restored DEX SHA-1 signatures and Adler-32 checksums verify; no instruction-store markers remain. These are analysis files outside Git, not an installed APK.

This supersedes the recovery blocker in [the earlier helper checkpoint](HELPER_RECOVERY_5_0_35.md). It does not establish a catalogue-authentication fix, completed cloud downloads, recovery of declared native methods, or device compatibility. This pass made no vendor request, phone inspection or emulator launch.

Input: Soundcore 5.0.35, version code 435, original APKM SHA-256 `b7acfeee5c506879e5870a8215562b8b0cd896a93aaa54cd3eb28c24cdc6e734`; extracted base APK SHA-256 `51bc439ee58d03011d92f995a78b352052915028edf4d10149f53c3ccdf04249`.

## Recovery and independent checks

1. Loaded reconstructed ARM64 `libexec.so` and `libexecmain.so` into Unicorn with ELF relocations, bounded synthetic memory, libc/JNI models and read-only access to the original base APK. Ran 73 native initializers. The virtualized `JNI_OnLoad` returned JNI 1.4 and registered 11 native methods.
2. Invoked the shell's `N.al` entry. It extracted `assets/ijiami.ajm`, transformed its compressed contents and expanded a **20,938,402-byte** instruction payload.
3. Checked the native inflater on synthetic stored, fixed-Huffman and dynamic-Huffman streams: all three reproduced the expected 45,240 bytes. Python zlib independently decoded the loader's actual transformed, zlib-wrapped input and matched the native payload exactly: SHA-256 `fff21d7214e22016f348fc3df2af0971e23be9dcc470d389afc0bf7e35f67338`. An earlier raw-DEFLATE attempt failed because this inner stream has a zlib wrapper; that was not another encryption layer.
4. Used a bounded zlib model at the native inflate entry to accelerate subsequent runs. It reproduced the original extracted `.ajm` and `.dat` asset hashes. The transformed `.dat` stream expanded to **121,755,896 bytes**, containing 23 bounded DEX images totaling 121,755,668 bytes. Model use is explicit in its report; this is not complete Android execution.
5. Parsed exactly **297,210 variable-length records**. Each has a 12-byte header (identifier, original debug-info offset, instruction-byte length), followed by encoded instruction bytes. Parsing consumes the entire payload with no remainder; identifiers form a contiguous range. Descending identifier order matches instruction lengths for all **51,460 independently changed methods** in earlier live captures.
6. Derived a complete, bijective 256-byte substitution from 2,000 previously captured methods. Validated it on **49,460 withheld methods / 3,282,964 instruction bytes**: every method matched byte-for-byte. This compares real bodies independently recovered from the same APK, rather than merely checking opcode plausibility.
7. Restored all 297,210 instruction arrays and original debug-info offsets, then repaired DEX checksums. All 44 indexed target classes were found; their 288 methods contain zero markers and none declares a native method.

The longer native-only run was progressing through decompression, rather than stuck: stream totals and output pointers established this after file-read counts had misleadingly stayed unchanged. Exact output comparison justified acceleration.

## Decompilation and limits

JADX 1.5.6 processed all 23 restored files and produced **54,474 Java files**. It exited with code 3: this is not an error-free Java reconstruction. None of the 14 main target source files contains a `Method not decompiled` placeholder, but `CryptoRequestFunctions.encryptBodyAndSign` and `ExchangeKeyHandler.exchange` have incorrect-decompilation warnings. Follow restored DEX control flow for their coroutine branches.

The whole extracted application and its dependencies declare **10,014 native methods**. This includes third-party/framework support; it is not a count of missing earbud protocol operations. Instruction-store restoration does not recover these native implementations.

The synthetic environment does not run worker threads or provide ART. `N.al` currently stops at the unmodeled JNI `NewObjectArray` operation, after payload extraction. Signal registration is modeled only in memory; no host signal handler is installed. Heap allocation does not reuse freed buffers. This bootstrap does not demonstrate successful vendor-app execution.

## Newly readable transport facts

The following now come directly from restored Soundcore helpers:

- InterfaceSafe `ECDH.b` computes the guest shared secret from the local private key and returned server point, then hex-encodes it. `ECDHJava` uses named P-256 and uncompressed public points.
- `ThingVisitorManager.j` saves that secret through `AccountBaseUtil.D`; `SoundCoreThingIot.g` reads it through `AccountBaseUtil.j` when signed out. The catalogue callback hashes timestamp concatenated with that secret using SHA-1. The cache accessors separate app regions.
- `KeySaverFactory.getSecurityKey` takes the first 16 decoded transport-secret bytes and hex-encodes them. Request HMAC uses that string as text; AES uses decoded bytes.
- `EncryptKeyFactory` encrypts public-point text with the preset and normal bodies with the shared key. The envelope is Base64 of IV followed by AES-CBC ciphertext. IVs come from generated UUID bytes. HMAC input joins timestamp, nonce and optional encrypted body with plus signs.
- `RandomField` uses seconds and a generated UUID nonce. Encryption/replay header flags match the current local profile.
- `IotNetwork.getFinalPath` performs an exact lookup in its URL-mapper list, otherwise returning the input path. Its recovered `initNetwork` does not populate that list, and the decompiled application has no external `setUrlMapper`/`new UrlMapper` call site. This provides no basis for guessing another endpoint.

These facts strengthen earlier cross-app inferences but have not explained the catalogue 401. The guest script and its last live result are unchanged. No unchanged request was repeated.

## Local artifacts and reproduction

Paths below are relative to `E:/Builds/research/soundcore-full-protocol-2026-11-13/catalogue-security/`. Vendor binaries, sources, lookup tables, payloads and local security material remain outside Git.

| Artifact | Purpose |
| --- | --- |
| `emulate_loader_vm.py` | Bounded ARM64 experiment; explicit `--accelerate-inflate` option |
| `native_inflate_oracle.py`, `native-inflate-oracle.json` | Three synthetic native-inflate comparisons |
| `native-vm-experiment/native-only-1200m-result.json` | Preserved native-only run metadata |
| `native-inflate-output-0.bin` | Native-only instruction payload oracle |
| `accelerated-zlib-output-1.bin` | Matching accelerated payload |
| `native-vm-experiment/` | Latest safe metadata and opaque memory artifacts |
| `extract_offline_dex.py`, `native-offline-dex/` | DEX extraction from this experiment's own heap |
| `index_ajm_records.py`, `ajm-record-index.json` | Pinned payload validation and record index |
| `compare_ajm_records.py`, `validate_ajm_substitution.py` | Independent live-body comparison and withheld validation |
| `restore_offline_dex.py`, `native-offline-restored/` | Full restoration and checksum repair |
| `offline-restoration-report.json`, `offline-restoration-verification.json` | Hashes, counts, coverage and decompiler limits |
| `decompile_offline_restored.py`, `offline-restored-jadx/` | Full analysis tree and private diagnostic log |

With original inputs, prior live captures and reconstructed native libraries retained, run from this directory:

```powershell
python native_inflate_oracle.py
python emulate_loader_vm.py --accelerate-inflate
python extract_offline_dex.py
python index_ajm_records.py
python compare_ajm_records.py
python validate_ajm_substitution.py
python restore_offline_dex.py
python decompile_offline_restored.py
```

Inspect every report: emulation's process exit alone does not mean loader completion. The recorded unsupported JNI stop is expected. The preserved native-only payload is required for index validation. Scripts pin this build and rely on its validated live-body oracle; they are not yet a general unpacker for future releases.

Next: trace restored catalogue/global-header routing and credential representation against the downloader, using DEX for warned coroutine methods. Only a demonstrated request difference warrants another bounded guest probe. Then retrieve cloud protocol files and resume per-model mapping against the complete restored instruction-store source.
