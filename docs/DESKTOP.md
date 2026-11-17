# PairFree for Windows and macOS: the plan

Approved by the owner on 2026-11-16. Phase 0 (the spike) is done: `desktop/spike/RESULTS.md`. This file is the plan the later phases follow;
change it when a decision changes.

## Context

PairFree is Android only. The owner wants Windows and macOS versions: one portable EXE, very light, settings in a readable file beside it, tray
behaviour, the same looks, every Android function, self-update from GitHub, and a code base where a new brand or feature is written once.

Owner's answers (2026-11-16): a **native EXE** is preferred, and the desktop UI **need not copy the Android screens**: looks and colours carry over,
the layout is redesigned for a mouse. **No Mac** is available, so Windows comes first and the Mac build follows from a GitHub Actions Mac runner,
tested by users. Update check: **ask once on first run**.

### What the code looks like today (read 2026-11-16)

| Part | State |
|---|---|
| `android/protocol` | 28,300 lines, 15 brands, plain Kotlin, **no `java.*`/`android.*` imports**, built with the `kotlin.jvm` plugin. About 267 uses of JVM-only calls in 74 files (mostly `"%02x".format(...)`), which Kotlin/Native lacks. |
| `android/app` | 17,100 lines. 21 files have no Android import already (most of `brands/`, `eq/`, `capture/`, `bluetooth/ControlLink.kt`, `ui/EarbudsActions.kt`, the generated tables). Android Bluetooth is in 19 files (`bluetooth/*Link.kt`, the three finders, `EarbudsConnection`, four brand files, status, sleep upload). |
| UI | 26 Compose files, foundation only (no Material). Looks are data (`data/looks/*.json`, `generateLooks`); device art is 294 generated vector drawables from `tools/gen_device_art.py`. |
| Text | 1,683 strings in one `strings.xml`; 86 lines mention the phone or Android. |

## Recommendation (requirement 11): Kotlin, compiled natively

**Kotlin Multiplatform, with the desktop apps built by Kotlin/Native** (`mingwX64` for Windows, `macosArm64`/`macosX64` for Mac).

- It is the only choice that keeps the 28,000 lines of protocol code, the brand files, the tables and their tests as **one copy**. Rust, C# or C++
  would give a fine EXE but mean a second copy of every brand, kept in step by hand for ever: the opposite of requirement 10.
- Kotlin/Native makes a real executable with no runtime to install. Compose on Windows needs a bundled Java runtime (40-60 MB, 150-300 MB RAM),
  which fails requirements 1 and 2; and since the desktop UI is being redesigned anyway, sharing the Android screens is worth little.
- Measured in phase 0 with every brand linked in: a 10.8 MB EXE, its window after 0.27 s, 18 MB of private memory. The finished app will be somewhat larger.

**Risk to settle first:** Kotlin's Windows native target has the lowest support tier and no ARM64 Windows build (ARM PCs run the x64 EXE emulated).
Phase 0 is a spike that must pass before anything else is built. Fallback if it fails: the same shared modules on a bundled Java runtime with Compose.

## Design

### 1. Modules (requirement 10)

```
shared/protocol   (moved from android/protocol)  Kotlin Multiplatform: common code; targets jvm (Android uses it), mingwX64, macos
shared/core       new: everything about the app that is not a screen or an OS call
android/app       Compose screens + Android's Bluetooth, widget, notification
desktop/ui        new: the desktop screens and PairFree's own small drawing toolkit, common to Windows and Mac
desktop/windows   new: Win32 window, tray, Direct2D/DirectWrite, Bluetooth, audio, updater
desktop/macos     new: AppKit window, menu bar item, CoreGraphics/CoreText, IOBluetooth/CoreBluetooth
```

- `shared/core` takes, from `android/app`: `brands/` (specs and drivers; `BrandSpec.open` returns a description of the channel instead of opening an
  Android socket), `eq/`, `capture/`, `ControlLink`, `EarbudsConnection` and `Hub` minus their Android calls, profiles, settings keys, report
  privacy, look specs, the device art table, and the string table. It defines the few interfaces each OS fills in: `Transport` (serial channel by
  service UUID, GATT, L2CAP, scan, paired list), `SettingsStore`, `TonePlayer`, `Clipboard/Files`, `SystemColors`.
- The generators stay the single source: `generate*Tables` move with the protocol; `generateLooks` writes a common `LookSpec`; `tools/gen_device_art.py`
  gains a second output, a compact path table in `shared/core` that the desktop toolkit draws with the look's seven role colours (Android keeps
  its drawables); a new `generateStrings` turns `strings.xml` into a Kotlin table for desktop.
- Tests stay on the JVM target (JUnit as now); a handful of smoke tests also run natively.
- A new brand after this: its `protocol` package, one `brands/` file, its data and strings. No per-OS code unless it needs a new kind of transport.

### 2. Desktop UI

Redesigned for a window and a mouse, **one implementation in Kotlin for both systems**: a small retained toolkit in `desktop/ui` (panel, button,
choice, switch, slider, list, text field, dialog, EQ graph) drawn through a `Canvas` interface that Windows backs with Direct2D/DirectWrite and Mac
with CoreGraphics/CoreText. Drawing it ourselves is what the looks require anyway (hairlines, Sketchbook's shadows and uneven corners); the look's
`shape` and `type` tokens are honoured in the toolkit as they are in `ui/Components.kt`.

- Layout: device list and tabs in a left sidebar, content at the right; EQ with draggable points, mouse wheel and arrow keys; right-click menus;
  keyboard focus and tab order; tooltips; per-monitor DPI.
- Looks: Studio, Studio Light, Sketchbook as they are. Material You's file becomes "System" on desktop: Windows' accent colour and dark/light mode
  (macOS' accent likewise), through `SystemColors`.
- Persian and other right-to-left languages: DirectWrite and CoreText shape the text; the toolkit mirrors as `ui/Direction.kt` does.
- Screen readers: UI Automation on Windows needs its own provider code. Planned after the first release; said openly until then.
- A design section for the desktop goes into `docs/DESIGN.md` with mock-ups for the owner **before** the screens are built (end of phase 1).

### 3. Windows shell (requirements 1, 3, 4)

- One `PairFree.exe`: fonts, art and strings compiled in; no installer, no registry, single instance.
- `PairFree.ini`-style settings as **TOML** in `PairFree.toml` beside the EXE: readable, commented, and it holds lists (EQ profiles) where INI
  cannot. Sections: `[app]` (language, look, close behaviour, update check), `[window]`, `[device."AA:BB:CC:…"]` (last EQ sent, choices),
  `[[profile]]`. Written atomically; unknown keys kept. If the folder cannot be written, it says so and uses `%APPDATA%\PairFree`.
- Window: minimise goes to the taskbar; the close button does what `close = "tray" | "exit"` says (asked the first time); a "minimise to tray"
  setting; optional start with Windows (a Startup shortcut, removable) and start minimised.
- Tray icon: battery in the tooltip, and a menu with noise control, EQ profile, connect/disconnect, open, exit. This is the desktop's equivalent of
  the Android widget and status notification, with a low-battery toast.

### 4. Bluetooth on each system

| Channel | Windows | macOS |
|---|---|---|
| Classic serial (most brands) | Winsock Bluetooth sockets by service UUID | IOBluetooth RFCOMM |
| Bluetooth LE (JBL, QCY, some Sennheiser) | WinRT GATT, called through COM from Kotlin (fiddly; own phase) | CoreBluetooth |
| L2CAP (AirPods, PSM 0x1001) | Not available to programs (phase 0); see 4.1. | IOBluetooth has L2CAP, but macOS itself talks to AirPods; likely nothing to add |
| Paired devices, connect audio ("Use for sound") | Windows device enumeration and audio endpoint APIs | IOBluetooth |

Hearing test, fit test tone and the A30 upload need audio output and a second serial channel: WASAPI/waveOut and CoreAudio behind `TonePlayer`.

Android-only items and their desktop form: widget and notification → tray; "Record from the official app" → left out (owner: almost no vendor
has a Windows app); Fast Pair and Find Hub → left out; the Samsung LDAC reminder → a note that Windows chooses the codec; the hidden-API and Android 17
notes → not shown.

#### 4.1 AirPods and Beats on Windows: not supported (owner, 2026-11-16)

Phase 0 confirmed that Windows gives programs no L2CAP channel (every connect fails with error 10050), so AirPods and Apple-chip Beats cannot be
controlled without a kernel driver. The one known driver, MagicPods' MagicAAP, needs Secure Boot off and test mode, or a root certificate with
Defender's protection off and Windows' driver policy removed, and states no interface for other programs. The owner decided not to link to it.
The Windows app shows "not available on Windows" for these models with one line of why, and the supported-devices pages say AirPods have no
Windows support. A possible future feature if a clean way appears.

#### 4.2 Codec panel: left out (owner, 2026-11-16)

Phase 0 read the codec from Windows' A2DP trace provider (AAC, 48 kHz, 16 bit for the H3), but the event is only written when a stream stops,
carries no bit rate, and needs administrator rights or membership of Performance Log Users. Too little for a feature; left out, as is any
changing of codecs (replacement audio drivers are not something PairFree links to). A possible future feature if Windows offers a proper call.
The earbuds' own codec switches (LDAC, aptX Lossless, sound quality, LE Audio) stay as brand settings, with a note that Windows chooses the codec.

### 5. Text (requirement 8)

`strings.xml` stays the one file translators work from. A small `strings_desktop.xml` (and `values-<code>/strings_desktop.xml`) holds **only** the
entries whose wording differs on a computer, under the same keys ("your phone" → "your computer"); desktop reads the override first. An
`android_only="true"` mark on entries the desktop never shows. `tools/test_translations.py` changes: the override file is allowed, every override
key exists in the main file, and a string that names the phone or Android must have an override or the mark. `docs/TRANSLATING.md` explains it.

### 6. Updates (requirement 9)

- First run asks once; "Check now" is always in About. Only `api.github.com/repos/Omega-Destroyer/PairFree/releases/latest` and that release's
  asset are contacted, through the system's own HTTPS (WinHTTP, NSURLSession): no network library in the EXE. Android stays without internet.
- The download is checked against a signature made with a release key of the owner's (public half in the EXE) before anything is replaced.
- Swap: download `PairFree.exe.new`, rename the running EXE to `.old`, move the new one in, restart, delete `.old`. `PairFree.toml` is never touched.
- CLAUDE.md's hard rule is reworded: "the *Android* app never requests the internet permission; the desktop apps contact GitHub's release API only".
- No Authenticode certificate (owner): the EXE shows Windows' SmartScreen warning on first run, and `docs/WINDOWS.md` says how to get past it.
- 64-bit only (owner): Windows 10 1809 or later and Windows 11, x64; ARM PCs run it emulated.

### 7. Releases (requirement 7)

One version number for all three. The tag builds the APK as now (F-Droid unchanged), `PairFree-X.Y.Z.exe` on Windows, and the Mac app
(`PairFree.app`, zipped: macOS needs a bundle for the Bluetooth permission text; unsigned, so users right-click > Open) on a GitHub Actions Mac
runner. `tools/test_version.py` covers the desktop version constant. README, site and F-Droid text name the platforms; device pages say per brand
what a platform lacks (AirPods on Windows).

## Phases (each on its own short branch, merged when it works)

0. **Spike, the go/no-go**: done 2026-11-16, go (`desktop/spike/RESULTS.md`). Drawing was done with GDI+, whose bindings Kotlin/Native ships;
   Direct2D or GDI+ is decided in phase 2 after trying Persian text and the equalizer graph.
1. **Shared modules**: `protocol` becomes multiplatform; `shared/core` extracted from `android/app` with no change in the Android app's behaviour
   (all tests, lint, a 0.14.0 comparison on the emulator). Desktop design section and mock-ups for approval.
2. **Windows shell**: toolkit, looks, strings, TOML settings, tray, window behaviour, single instance.
3. **Windows, serial brands**: every screen for the brands on classic serial channels, tried on the H3, Air5 Pro+ and Space A40; EQ, profiles,
   controls, device report, hearing and fit tests.
4. **Windows, Bluetooth LE brands** (JBL, QCY, Sennheiser LE).
5. **Updater**, release pipeline, docs (README, site, `docs/DESIGN.md`, `CLAUDE.md`, a `docs/WINDOWS.md` for users), first Windows release.
6. **macOS**: backends in `desktop/macos`, built on a Mac runner (needs the owner's go-ahead to push the workflow), tested by users.

## Verification

- Every phase: `python -m unittest discover -s tools`; in the E: mirror `:protocol:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`,
  plus the native build and its smoke tests. Phase 1 additionally proves Android is unchanged (test vectors, emulator screenshots against 0.14.0).
- Windows: run the EXE against the owner's three earbuds; each setting compared with the Android app on the same earbuds; settings file edited by
  hand and reloaded; tray, close and minimise paths; update swap rehearsed against a test release on a fork; a clean Windows 10 and 11 machine or VM.
- Not checkable here: any Mac behaviour, ARM Windows, and every brand the owner has no earbuds for (as on Android: users' reports).

## Open points (owner)

- Pushing a GitHub Actions workflow for the Mac build when phase 6 comes.
