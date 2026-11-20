# Reproduce the successful Soundcore unpacking workflow

This is the successful path from the original **Soundcore 5.0.35-435** package to restored DEX, decompiled Java and decoded resources. It omits exploratory attempts. The reusable workflow was tested from the original APKM in a new directory on **2026-11-19**. It needs no phone, Android emulator, account, signing key or vendor-server request.

**What “complete” means here:** all **297,210 instruction-store-protected methods** were restored in **23 DEX files**. Every restored DEX matches the independently validated reference hash. JADX produces **54,474 Java files**, with diagnostics. Declared native methods are still native, and some Java/coroutine reconstructions require reading restored DEX. This is not a claim that every native function became C/Java or that an installable unprotected APK was produced.

## 1. Obtain the toolkit and original input

The portable local package is **`soundcore-unpacker-5.0.35-435.zip`**, prepared outside Git under:

```text
E:/Builds/research/soundcore-unpacker-2026-11-19/
```

It contains the launcher, its Python stages, a validated build profile, dependency requirements and attribution/licenses. Share that complete ZIP with another researcher alongside this guide. The original APK is supplied separately. The toolkit is a local research artifact, not a published release or a download hosted by this repository.

The [clean-run verification report](UNPACKING_REPRODUCTION_2026_11_19.json) records the ZIP fingerprint, full APKM run, direct-base-APK run and launcher refusal checks. Its recorded ZIP SHA-256 is `abe95f9c77552c092d2c69a59b0b3157ef74e13b1f1084892926de7e3dfb9697`.

The profile includes a 256-byte instruction-decoding permutation derived and validated during the original research. It is not a login credential, API/server key, private key or user data. It stays outside Git. It lets a new user repeat this build's recovery without recreating the original live-capture calibration. The package contains no vendor APK, vendor native library, recovered DEX, decompiled vendor/SDK source or account material.

Supported input fingerprints:

| Input | SHA-256 |
| --- | --- |
| Reference APKM, 5.0.35-435 | `b7acfeee5c506879e5870a8215562b8b0cd896a93aaa54cd3eb28c24cdc6e734` |
| Base APK extracted from it | `51bc439ee58d03011d92f995a78b352052915028edf4d10149f53c3ccdf04249` |

The script accepts that base APK directly, or the same base inside an APKM/XAPK container. It checks the base hash before unpacking. A different build stops with an unsupported-build message. A container holding the same base can have a different outer hash; its actual hash is recorded rather than misrepresented as the reference archive.

## 2. Set up the tools once

The tested environment used **Python 3.10.6**, **Unicorn 2.1.4**, **pyelftools 0.31**, **JADX 1.5.6**, and Android Studio's **JBR 25** Java runtime. The launcher/dependency installation instructions below target Windows; other platforms have not been tested end to end.

Extract the toolkit ZIP to a convenient folder outside a Git checkout. In PowerShell, change to that folder and run:

```powershell
py -3 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

The initial dependency installation can need internet; the actual unpacking does not. Java and the JADX 1.5.6 distribution are supplied separately. There is no automatic APK, Java or JADX download.

Copy `config.example.json` to `config.json` and change the tool paths. For example:

```json
{
  "java": "C:/Program Files/Android/Android Studio/jbr/bin/java.exe",
  "jadx_jar": "C:/Tools/jadx-1.5.6/lib/jadx-1.5.6-all.jar"
}
```

This config holds tool paths only. Command-line `--java` / `--jadx-jar` take precedence. `JAVA_HOME` / `JADX_HOME` are also supported. Keep `stages/`, `profiles/` and `LICENSES/` beside the Python launcher; a detached copy of the launcher is insufficient.

## 3. Run it

### Drag and drop

Drop the original `.apk`, `.apkm` or `.xapk` onto **`Unpack Soundcore.cmd`**. It uses the toolkit's `.venv` when present, otherwise the Windows `py -3` launcher. With no input argument, the Python entry point offers a file picker. The command window remains open so the result is readable.

Default output is a new timestamped folder beside the input. If the input is on a slow network share, specify a local output directory with the command-line form instead. The output directory must be new/empty and outside a Git workspace. The input is never modified; existing outputs are not overwritten.

### Command line

From the extracted toolkit folder:

```powershell
.\.venv\Scripts\python.exe unpack_soundcore.py "C:/APKs/soundcore.apkm" --output "C:/Research/soundcore-unpacked"
```

Without `config.json`, supply explicit tool paths:

```powershell
.\.venv\Scripts\python.exe unpack_soundcore.py "C:/APKs/soundcore.apkm" --output "C:/Research/soundcore-unpacked" --java "C:/Program Files/Android/Android Studio/jbr/bin/java.exe" --jadx-jar "C:/Tools/jadx-1.5.6/lib/jadx-1.5.6-all.jar"
```

Manifests and resources from the supplied APKs are decoded by default. `--no-resources` skips that step. To restore the DEX/native loaders without Java/JADX:

```powershell
.\.venv\Scripts\python.exe unpack_soundcore.py "C:/APKs/base.apk" --output "C:/Research/soundcore-dex-only" --dex-only
```

Progress names the active stage. Native extraction can take time; use the report/log files rather than assuming a quiet console means failure. On the tested machine the clean native extraction took about 12 seconds and JADX about 112 seconds; these are observations, not timing guarantees.

## 4. The successful stages, in order

### A. Validate and extract the original package

The launcher finds the matching base APK, records the original/container hashes and creates a private output tree. It reads the two required ARM64 packer assets:

```text
assets/ijm_lib/arm64-v8a/libexec.so
assets/ijm_lib/arm64-v8a/libexecmain.so
```

It also retains native `.so` libraries from the supplied APKs for separate analysis. APK assets are indexed by name/size; secret asset contents are not opened or copied. Resource-only input archives contain manifest, resource table and `res/`, excluding assets.

### B. Unpack and reconstruct the native loader

`stages/unpack_nrv.py` decodes the loader's NRV2E blocks. `stages/rebuild_native.py` restores ARM64 branch operands, reconstructs loadable segments, copies the packed image's writable segment data and restores the initializer address from the loader metadata. Unavailable non-loadable ELF section metadata is removed rather than fabricated.

The results are **analysis ELF images**, not installable replacements. Their ARM64 code and dynamic metadata can be loaded by the next stage. The NRV2E and branch-filter adaptations retain their UCL/UPX attribution and licenses in the toolkit.

### C. Execute the loader's payload extraction offline

`stages/emulate_loader_vm.py` maps the reconstructed ARM64 libraries into Unicorn. It provides bounded synthetic memory, relocations, selected libc functions and a minimal JNI/framework environment. The original base APK is exposed read-only to the guest. Host network and Android services are not exposed.

For this profile, the working bootstrap runs **73 native initializers**, enters the native loader, obtains JNI 1.4 and invokes its registered `s.h.e.l.l.N.al` entry. The loader transforms and decompresses its own packed payloads. A validated Python zlib model at the native inflater boundary accelerates that mechanical step while preserving its input/output accounting.

The extracted data includes:

- A **20,938,402-byte** instruction-store payload, SHA-256 `fff21d7214e22016f348fc3df2af0971e23be9dcc470d389afc0bf7e35f67338`.
- A **121,755,896-byte** decoded DEX-containing payload; its bounded images total **121,755,668 DEX bytes**.

The synthetic loader stops at the known unmodeled JNI **`NewObjectArray`** operation after extraction. The launcher checks that exact stop and extracted hashes. This is sufficient for recovery; it is not a successful Android app startup. A different stop, missing payload or wrong hash fails the run.

### D. Recover the real method bodies

The extracted DEX files already expose class/method structure, but their protected methods contain instruction-store markers and placeholder instructions. Decompiling them at that point would not recover the business logic.

The instruction payload contains **297,210 variable-length records**. Each starts with three little-endian u32 fields: record identifier, original debug-info offset, and instruction-byte length. Encoded instruction bytes follow. Record parsing must consume the entire payload, without duplicates or a remainder.

For this build, a marked method's index is:

```text
method_index = 0x04000000 - protected_debug_info_offset
record_id    = highest_record_id - method_index
```

The launcher checks the record's instruction length against that method's DEX code item, translates its bytes using the validated build profile, restores its original debug-info offset, and replaces the instruction array. It preserves the surrounding DEX structure.

It then repairs the DEX SHA-1 signature and Adler-32 checksum. It requires exactly **297,210 unique restored indices**, zero remaining markers and the complete expected set of **23 restored DEX hashes**. A plausible opcode stream or a JADX output directory alone is not success.

### E. Decompile the restored DEX and decode resources

The Java reconstruction uses the same successful JADX options:

```text
-r -j 6 --no-inline-methods --no-inline-anonymous --show-bad-code
```

All 23 restored DEX files are supplied together. A separate resource-only pass uses `-s` on each supplied APK's filtered manifest/resource archive. This avoids mixing shell DEX with restored classes and avoids copying secret assets.

The fresh reference run produced **54,474 Java files**. JADX exited **3** and reported **492 diagnostics**. The script records `complete_with_jadx_diagnostics`, preserving the log. Some methods require reading restored DEX rather than trusting malformed Java, especially coroutine/control-flow warnings. An unexpected JADX exit or no Java output fails the run.

## 5. Where to find the results

| Output | Contents |
| --- | --- |
| `decompiled/sources/` | Java/Kotlin reconstruction from restored DEX |
| `resources/<apk-number>/resources/` | Decoded manifest and resources for each supplied APK |
| `dex/restored/` | The 23 checksum-repaired, fully instruction-store-restored DEX files |
| `dex/protected/` | Extracted pre-restoration DEX, retained for comparison |
| `native/native-unpacked/arm64-v8a/` | Reconstructed ARM64 loader/helper loadable segments |
| `native/original/` | Supplied APK native libraries for separate disassembly |
| `restoration.json` | Counts, zero-marker check and every restored DEX hash |
| `run.json` | Input hashes, stage results, final status and JADX result |
| `loader/` | Private intermediate payloads, loader metadata and memory artifacts |
| `asset-index.json` | Asset names/sizes, not secret asset contents |
| `logs/` | Private per-stage diagnostics |

These folders can contain substantial vendor-derived material. Keep them outside Git and out of public issue attachments. Only the own-code toolkit and its non-credential profile are intended to be shared as the unpacking tool; decoded vendor output is not bundled with it.

## 6. What established the original breakthrough

The decisive separation was **DEX extraction versus instruction recovery**. Native-loader reconstruction exposed the DEX and a separate instruction store. The DEX method markers indexed that store; those markers were not ordinary valid method bodies. Once the record order, byte transformation and code-item lengths were matched, every protected method could be restored mechanically.

The byte transformation was calibrated once using successful offline Android instrumentation of the same APK. The instrumentation let the vendor supply its business class loader, skipped normal application `onCreate`, resolved selected classes, and read only its own live-DEX outputs. Matching those real restored bodies against the offline records established the reverse index order and the 256-byte permutation.

The original validation trained on **2,000 methods** and checked **49,460 withheld methods / 3,282,964 instruction bytes**. Every withheld method matched byte for byte. The inflater was independently checked on stored, fixed-Huffman and dynamic-Huffman synthetic streams, and its actual instruction payload matched native-only execution. The new launcher reuses these validated facts and verifies the complete output hashes. **Normal reproduction of this supported build does not repeat the live Android calibration.**

The [original recovery evidence](OFFLINE_UNPACK_5_0_35.md) retains those validation details; the [broader research runbook](PROTOCOL_RECOVERY_RUNBOOK.md) covers subsequent protocol analysis. Neither is a prerequisite to following the launcher steps above.

## 7. Boundaries for another version or another app

The launcher is deliberately build-specific. Its native bootstrap addresses, packer layout, record mapping, decoding profile and reference hashes belong to this base APK. Another Soundcore build or another iJiami-protected app may require a different profile. **Do not disable the hash checks, reuse the map blindly or interpret partial Java output as successful recovery.**

Supporting another build means establishing its loader/payload layout and method mapping, independently validating its byte transformation, then recording new complete input/output fingerprints. The reusable stage machinery helps, but that profile-development work is not automated by the current launcher.

Native declarations remain native. This app/dependency set declares **10,014 native methods**; restoring instruction-store bodies does not reconstruct those implementations. Native libraries are retained for separate disassembly. The toolkit also does not decrypt arbitrary app assets, obtain cloud device definitions, log into anything, rebuild/sign an installable APK or establish earbud compatibility.
