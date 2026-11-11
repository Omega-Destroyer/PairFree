# What the 2026-11-11 Soundcore unpack opened, and what it did not

Owner's question: does the new unpack of Soundcore 6.4.0-17 and 5.0.35 (handoff in `E:\Builds\research\soundcore-unpack-2026-11-11\README.md`, outside the repo) open
anything about Soundcore that could not be assessed before, not only the hearing and fit tests? Assessed the same day from the handoff's outputs (Blutter's Dart
assembly, its object pool, the extracted native libraries and their Ghidra output). Source analysis only.

## Still closed: the earbuds' wire protocol

The settings protocol (state, noise modes, EQ, touch actions, HearID block, every command in `data/soundcore/commands.json`) is in the iJiami-protected Java, which the
handoff says is not recovered (`protected_java_payload_recovered: false`). Checked: the Dart object pools of both versions hold no packet header or command bytes (no
`08EE00`, no per-model tables); the exposed Java is the loader plus Andserver and Alibaba SDK code (one Soundcore class); the readable native libraries hold none (below).
So the models only 6.4.0 names (`report_only`), their layouts, the A40's unread settings and every "to read" item that needs the byte layout stay as they were.
This is a real limit, recorded with what was checked.

## Opened: the Flutter modules (Dart assembly, named by library)

6.4.0 has `module_flutter` with `hearid` (37 files), `aichat`, `qqheaing` (QQ Music healing), `widgets` (breathing, care, credits); 5.0.35 adds `d3200` (304 files) and
`ankerday` (54). The Dart side drives native methods by name (`channel.dart`): `setHearIDStartOrCancel`, `setHearIdAudio`, `setHearIdTestResult`,
`startWearFitDetection`, `getSoundLevel`, `closeSoundLevel`, `getDeviceInfo`. What that gives:

- **HearID test result (Dart to native):** a map with `timestamp`, `gender`, `level`, `ageRangeIndex`, `noiseInterference`, `sealFail`, `largeHearingVariation`,
  `leftEarData` and `rightEarData` (lists). The tone test, its steps (`hearid_test_step.dart`, 198 KB of assembly), the environmental noise check, the seal and variation
  checks and the training pages are Dart; the conversion of the ear data into the earbuds' HearID block is native, so in the protected part. Not yet read: the step logic.
- **Fit test:** `startWearFitDetection` and `isLeftOk` / `isRightOk` in the result, which agrees with the `0901` reading in `FIT_AND_HEARING_TESTS.md`; which models get the
  screen is decided natively.
- **New products:** `d3200` is a recorder with AI notes (auto shutdown, knock mark, indicator light, recording prompt, device restore, battery, storage, cloud sync); not in PairFree's list.
  `ankerday` is an Anker account and rewards feature. Neither is an earbud setting PairFree can read from here.

## Opened: unpacked native code (5.0.35 `libscnativelib.so` and split libraries)

- **`libhid_app.so` (2.3 MB, plain code, ARM64 and ARMv7):** Soundcore's HearID engine, called through `com.anker.hearid.HidAppWrapper` (`init`, `getNextEqParams`,
  `updateUserFeedback`, `getUserProfile`, `getVersion`, `close`) over C++ `HID::get_next_EQ_params`, `update_user_feedback`, `get_user_profile` and `PEQ_State` (parametric EQ
  states). It is an interactive preference engine (the app proposes EQ parameter sets, takes the user's feedback and builds a profile), a newer HearID than 2.9.9's tone
  staircase. Disassembly only (no C yet); portable the way JBL's libraries were (emulated against the library). It is the only new thing here that could carry a feature
  PairFree cannot get otherwise; whether the A40 uses it, and how its output reaches the HearID block, is in the protected part.
- **`libscnativelib.so`:** speaker light effects (66 functions: Soundcore light speakers, PairFree has none), `DailyCareAlgorithm` (heart rate zones, calories, VO2, stress: sport
  earbuds with sensors), voice enhancement, QQ Music healing (key decryption), SBC and Opus decoding, a `lzf` compressor. None of it is a settings protocol.
- **Feature split:** `libaudio_readscan_jni.so` (`audio_select_eq(path)`, `audio_set_user_version(hard, soft)`, FFT code, log tag `UNZIP_AND_ENCRYPT`: it processes an audio
  file and packs it for the device, a lead for the Sleep A30 audio upload questions in `SLEEP_A30_AUDIO_FEASIBILITY.md`), `libecc-encryption.so` (`eccEncrypt`, `eccDecrypt`,
  `generateEccKey`: device authentication of the recorder and sleep products), `libopus-lib.so`, `libsecuritytool.so` (security tooling: keys stay out of the repo).
- Also present and unrelated to settings: snore detection, deep AEC, microphone detection, denoise demo, heart-rate variability.

## What to do next (all reading, none blocked)

1. Read `hearid_test_step.dart` and `hearing_result.dart` for the test's tone levels, frequencies and thresholds, and what `leftEarData` holds.
2. Disassemble `libhid_app.so`'s three functions; run it in an emulator as the JBL ports were, to see whether its profile output is the A40's HearID curve.
3. Read `libaudio_readscan_jni.so`'s `audio_select_eq` (70 functions, decompiled) for the Sleep A30 audio packaging.

## Read afterwards (2026-11-11, second pass)

- **The Flutter HearID test** (`hearing_test_vm.dart` in 6.4.0): nine tones per ear in the order 2000, 4000, 8000, 6000, 16000, 12000, 2000 (a repeat as a check), 1000 and
  500 Hz (`testSequence`); frequencies that start from a default level: 2000, 6000 and 12000 Hz (`needDefaultDBList`); re-checks of 6000 against 8000 and 4000, and of 12000
  against 16000 and 8000 (`recheckList`); the staircase is `canHear` / `canNotHear` with `_addStep`, `_reduceStep`, `_addResult`, `_getResult` and a prompt to retest at a level of
  15 or more. The tones are played by the native side (`setHearIdAudio`) and the Dart `hearid` plugin only exposes the microphone's sound level for the noise check. The result goes
  to native (`setHearIdTestResult`), where it becomes the HearID block: **protected**, so the test cannot be completed into a block from this source. `HearingLevelModel.dart`
  (162 KB) holds level tables not yet read.
- **`libhid_app.so`** is a dueling-bandit preference tuner: C++ class `HID::savage_dueling` (the SAVAGE algorithm: a preference matrix, Borda scores and winner, arm selection,
  `update(i, j, won)`, a stop criterion, state saved and loaded as JSON with nlohmann), plus `dueling` and Thompson-sampling arm selection (`select_arms_dts`). In plain terms:
  the app plays two EQ candidates, the user picks one, and the engine narrows to the one they prefer; `get_user_profile` returns the winner as parametric EQ states. Nothing in
  the Flutter modules calls it, so its consumer is in the protected Java. It is a published algorithm, so PairFree can offer an A/B EQ finder for any brand with a custom curve
  without porting Soundcore's tables (above the vendors' apps, owner's rule of 2026-11-10); Soundcore's own candidate sets and output format are not readable.

## Read afterwards (2026-11-11, third pass): `libaudio_readscan_jni.so`

`audio_select_eq(path)` (Ghidra C of the 5.0.35 arm64 library, `native-feature-c/`) is **not audio packaging for the Sleep A30**. It opens a WAV file (RIFF, a fixed mode of 44.1 kHz, one channel, 16-bit, set by `audio_select_eq_init` / `audio_select_eq_set_value`, which refuse any other values with -1 to -3), finds the loud stretch by block energy, runs an FFT and builds 17 spectral features that it compares with three built-in reference tables (`y_avg_b`, `y_max_d`, `y_min_d`) by squared distance, and returns a class number (negative values are errors: -1 no path, -2 cannot open, -3 mmap, -4 malloc, -5 unsupported sample layout, -6 no segment). It writes a debug line of values to `res/out` when run from a test harness. It encrypts nothing and writes no container; the log tag `UNZIP_AND_ENCRYPT` belongs to the JNI wrapper's logging of the path and the version strings (`audio_set_user_version` stores a hard and a soft version string and prints them). So it is a spectral classifier for a test recording (consistent with a factory or read-scan check of an earbud's microphone or speaker), and it gives nothing for the A30 audio upload questions in `SLEEP_A30_AUDIO_FEASIBILITY.md`. Checked: every function in the library (70, all decompiled), the JNI entry points, the data it writes.

## Read afterwards: JBL PersoniFi's audiogram-to-EQ rule (JBL Headphones 5.37.10)

Read from `nt.b` (`HearingTestBandProvider`), `nt.a`, `mt.j` and `xu.g`: the audiogram's thresholds per ear are floored at 0 dB; the loss at each of the nine frequencies is the lower of the two ears (`mt.j.d`); for every frequency except 250 and 500 Hz a loss of 30 dB or less gives a boost of 0.34 x loss (a loss over 30 dB gives none), as a peaking filter (type 1, Q 1.0); the four largest boosts are kept (sorted by boost, then by the filter's second value) and passed through a last adjustment step (`p.a`, not read). `mt.b` holds amplitude multipliers per frequency for JBL's warble tone files (`warble_250` to `warble_12k`), one table per model family (Club 700, Club One, Club 950, a default, each with an ANC-on variant): they calibrate JBL's own headphones' output to dB HL and are not transferable. PairFree's generic hearing test already turns relative results into per-band gains, so JBL's rule is recorded here but not ported: the thresholds it needs are absolute, and PairFree's phone-run tones are not calibrated to JBL's headphones.
