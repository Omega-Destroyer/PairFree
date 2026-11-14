# Soundcore 5.0.35: focused helper recovery checkpoint

The owner selected recovery of the remaining helpers in this APK, rather than inspecting another app version. This pass stayed within that scope. **No missing authentication method body was recovered, no catalogue-authentication fix is claimed, and no vendor-server request was made.** The guest downloader remains at the previously recorded catalogue 401.

## Exact recovery targets

An external metadata index now maps each selected method to its original captured DEX hash, descriptor, code offset and instruction-store index. It contains 14 main target classes, 44 including their continuation/inner classes, 288 methods and **279 instruction-store-marked methods**. The nine unmarked code-bearing methods are static initializers; this is not recovery of the functional helpers.

Targets include InterfaceSafe `ECDH`, `ECDHJava` and `ECDHUtil`; `AccountBaseUtil`; ESIot `CryptoRequestFunctions` and its Kotlin extensions; `UrlKtxKt`, `IotNetwork`, `KeySaverFactory`, `EncryptKeyFactory`, `ExchangeKeyHandler`, `ExchangeCryptoUtil`, `RandomField` and `InitializerKt`. The index is `catalogue-security/helper-recovery-index.json` under the external research root. It contains method metadata, not key values or preferences.

The visible `IotNetwork` static initialization creates an empty URL-mapper list. Its constructor, `initNetwork`, `getFinalPath` and setter bodies remain protected, so this observation alone cannot prove that mapping stays empty after initialization. No endpoint substitution follows from it.

## Offline experiments and results

All runtime work used only the disposable API30 lab `emulator-5602`. No phone or logged-in vendor session was inspected. App network was disabled; the memory-scan and final clean-boot runs additionally installed a temporary UID-specific outbound rejection rule. Those temporary rules were removed after their respective runs.

| Experiment | Observation | Evidence limit |
| --- | --- | --- |
| Early native-load hook on the original ARM research installation | Hook setup reached; both packer libraries were later found mapped with native translation | No helper snapshot or completed loader observation |
| Hook on the x86 research installation | `System.load` entry for `libexec.so` observed, without a matching completion event | Does not establish anti-Frida behavior or the termination cause |
| Minimal helper-only instrumentation without Frida | Android reported `Process crashed` before expected completion | Refused to pull any old output as a fresh capture |
| Lab-only propagation of the shell's swallowed load exception | Patched one bounded catch-handler short-goto into a throw in an analysis APK; probe still failed without a classified Java/linker exception | No explanation of the native failure was obtained; baseline APK restored afterward |
| Empty-Application substitution retaining the supplied loader | Same pre-completion process failure | Avoiding the vendor Application constructor was insufficient in this setup |
| External read-only instruction scan | 39 passes, 426,486,080 bytes read, zero matching maps for 200 known recovered 64-byte instruction prefixes | Does not prove no plaintext exists: only selected readable/writable, non-executable maps and bounded map sizes were scanned |
| Clean reboot after removing Frida server | Minimal helper instrumentation still reported `Process crashed` | Stale instrumentation state was not sufficient to explain this result; no new capture |

The signature-based scan read process memory through `/proc` without attaching/injecting. It searched for already recovered code sequences and would save only matching maps outside Git. It produced no map artifact. This was not an account-file or arbitrary preference export.

Native constant searches were bounded to exact literals rather than prefix matches: most apparent `0x4000000` references in the loader were instruction-relocation or signal/unwind code, not established references to the Java instruction store. The native dispatcher and its virtual-machine semantics still require analysis; numerical coincidence is not a decoder.

## Artifacts and lab state

External scripts/artifacts remain in `E:/Builds/research/soundcore-full-protocol-2026-11-13/catalogue-security/`:

- `loader_diagnostic.js`, `run_loader_diagnostic.py`: scoped native-load categories and helper snapshots, with no raw source literals or loader errors printed.
- `helper-probe/`, `run_helper_probe.py`: minimal same-signer instrumentation; successful result code and completion marker required before pulling outputs.
- `reveal_loader_error.py`: analysis-only shell handler patch with asserted opcode/layout checks. Original vendor APK unchanged; this patch is not retained in the installed baseline.
- `instruction_store_scan.c`, `instruction-store-scan`, `instruction-patterns.bin`, `run_instruction_store_scan.py`: bounded external memory experiment, with its result log and empty output directory.
- `helper-recovery-index.json`: exact original method targets for further instruction-store/native analysis.

The lab now has the re-signed x86 research baseline `base-mmkv.apk` with its matching splits. This supersedes the prior note that its installed app was the original ARM build. Frida server was removed, its temporary host forwarding was removed, and the lab emulator was stopped. The separate user-visible login emulator and the owner's phone were left alone.

**Current recovery blocker:** protected-loader termination before the minimal definition capture completes; checked: scoped load hooks, x86 baseline, injector-free minimal instrumentation, exception propagation, constructor avoidance, bounded external memory scan and a clean reboot. These experiments do not establish the missing cryptographic semantics or prove that static/native recovery is impossible. The next recovery work is the native loader/instruction-store path, using the indexed method targets. Repeating catalogue requests or changing key formats without those semantics is not supported by this pass.
