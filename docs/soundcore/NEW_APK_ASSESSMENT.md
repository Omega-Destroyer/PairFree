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
