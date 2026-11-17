# Desktop spike (phase 0): results, 2026-11-16

The question: can PairFree for Windows be one small native EXE built with Kotlin/Native from the code Android uses? Tried on the owner's PC
(Windows 11, Intel Bluetooth) with a SOUNDPEATS H3. Answer: yes.

| Question | Result |
|---|---|
| Does the protocol module compile for Windows (`mingwX64`)? | Yes, after about 80 JVM-only calls in 19 files were replaced by common Kotlin (`protocol/Common.kt`). Android's unit tests pass unchanged. |
| Size, start, memory with every brand linked in | 10.8 MB, one file; window after 0.27 s; 18 MB private memory (37 MB working set); no CPU use while idle. The real app adds screens, fonts, all drawings and strings. |
| Classic serial channel (most brands) | Works through Winsock (`AF_BTH`, RFCOMM, service UUID): the H3's three battery reads answered and the shared code decoded them. |
| Drawing | A Win32 window drawn with GDI+ (Kotlin/Native ships its bindings): text, a panel and the H3 drawing from the generated vector's paths, in Studio's colours. Direct2D has headers in the toolchain but no ready bindings; decide in phase 2. |
| Bluetooth LE (JBL, QCY, some Sennheiser) | Works: WinRT called as plain COM from Kotlin (`Gatt.kt`) listed the H3's 8 GATT services. No extra library. Notifications need a COM object written in Kotlin (a callback); not tried. |
| L2CAP (AirPods, PSM 0x1001) | Not available. Winsock creates an L2CAP stream socket but every connect fails with error 10050, to the H3's SDP channel too. So AirPods and Beats need a kernel driver on Windows. |
| The MagicAAP driver (magicpods.app, read 2026-11-16) | Official build: unsigned, needs Secure Boot off and Windows' test mode. Community build: imports a root certificate, needs Defender's real-time protection off and, since Windows' April 2026 update, the Windows driver policy removed. No licence, source or interface for other programs is stated. Its own page says to install it only if "prepared to reinstall Windows". |
| Current codec | Readable from the trace provider `Microsoft.Windows.Bluetooth.BthA2dp` (`ddb6da39-08a7-4579-8d0c-68011146e205`): the `A2dpStreaming` event gave codec id 2 (AAC) for the H3, and `A2dpPinSetDataFormat` 48000 Hz, 16 bit, 2 channels. The codec event is written when a stream stops, not when it starts. |
| Rights for that trace | It ran without elevation here because the owner's account is in the Performance Log Users group. An account that is not needs administrator rights (not tried on such an account). |
| Bit rate | Not reported: no event of that provider or of its telemetry provider carries one. |

Build notes: a full native link takes about 2.5 to 3 minutes and needs a 6 GB heap (`gradle.properties`); GDI+ declares its own `Unit`, so Kotlin's
must be written `kotlin.Unit` in a file that imports `platform.gdiplus.*`; Winsock's names are in `platform.posix`.

Not checked: a PC without Performance Log Users membership, Windows 10, ARM PCs, a GATT read, write or notification, and the Sennheiser equalizer's
test vectors on the native build (its sine and cosine now come from Kotlin's maths, not Java's StrictMath).

Decisions the owner took from these results (2026-11-16): no link to the MagicAAP driver, AirPods and Beats are listed as not supported on Windows;
no codec panel. Both stay possible later if a cleaner way appears (`docs/DESKTOP.md`, 4.1 and 4.2).
