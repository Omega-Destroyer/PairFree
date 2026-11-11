# Fit tests and hearing tests across the vendors' apps

Survey of 2026-11-10 (owner's request: make the fit test generic, check every brand for a fit test and a hearing test). Source analysis only: the
vendors' apps decompiled outside the repo, plus the docs and data already in the repo. Nothing here was tried on earbuds.

## Fit test (ear tip seal check)

The earbuds run it themselves; the phone starts it and gets a result per earbud. PairFree's side is the `FitTest` interface
(`protocol/FitTest.kt`): a session implements `startFitTest()` (frames) and `state`, sets `EarbudState.fitTest` for the models that have it, and writes
the result into `EarbudState.fit`. The shared `FitTestScreen`, the Device tab row and `EarbudsConnection.startFitTest` need nothing brand-specific; a
new brand is one line in `EarbudsConnection.fitTestSession`. SOUNDPEATS' Qualcomm models stay the one special case (GAIA feature `09`; noise control
goes off around the test, which the connection does).

| Brand | Vendor app has it? | Built | Evidence |
|---|---|---|---|
| SOUNDPEATS (Qualcomm) | yes (GAIA feature 09) | yes | PeatsAudio / SOUNDPEATS 2.5.3, the owner's H3 and Air5 Pro+ |
| Samsung | yes (message 157 start, 158 result), per model | yes, on the `FitTest` interface | budsunitemgr 9.0.10; no hardware |
| OnePlus / OPPO | yes (`fitDetection` flag; `0405 01`, event 4 in `0204`) | yes, on the `FitTest` interface | HeyMelody; invented replies only |
| EarFun | **no**: the Qualcomm GAIA client library inside the app carries `EarbudFitPlugin` / `SetEarbudFitTestRequest`, but nothing outside that library calls it, and the app has no fit test screen (`wear_01` is a help picture) | not offered | EarFun Audio 20.1.61, whole tree searched |
| QCY | **no**: the same library, never called outside it; QCY's panels list no fit test command (docs/qcy/PROTOCOL.md) | not offered | QCY 4.0.7 |
| Soundcore | partly: `0901` ("wear fit detection") exists in the A3933 and A3951 families (reply: left byte, right byte, code <= 1 is a fit, then pressure and over/under values), but Soundcore 2.9.9 only logs the result on them; the one fit test screen is the A3600's (`A3600FitTestActivity`, "wear adjust"), and the A3600 is not in PairFree's model list. 6.4.0 is packed and its resources hold no fit test text | not built | Soundcore 2.9.9 code, 6.4.0 resources |
| Nothing (Ear (2) and others with `fit_test` in `devices.json`) | yes (`isSupportEarTipFitTest`, a Flutter screen); start `F014` 01 and result `E00D` (left, right) from ear-web and attune (AGPL, facts) | **built 2026-11-10** on the models whose table says so; value meanings inferred. Nothing X's own start/values sit in Dart: the start message is in the Dart code of `libapp.so`, which is not read yet (not a blocker: `E:\Builds\research\nothing-tools\libapp` exists) | Nothing X 3.4.15 |
| Pixel Buds (Pro, Pro 2, 2a) | **built 2026-11-10** (thresholds found: `MaestroOobe__fit_test_good_criteria` 80, `fit_test_max_criteria` 200, noise limits 20/40; verdict logic read in jadx fallback mode). Originally:  Maestro service `maestro_pw.EartipFitTest` with `StartTest` (empty), `SubscribeToResults` (stream; each result is two floats: fit score, noise score) and `EndTest` (empty), per earbud. The app turns the scores into NONE, NEED_ADJUSTMENT, NEED_CHANGE_EARTIP, GOOD or NOISY with thresholds from Google flags (noise limit default 40.0 for three device kinds in `MaestroOtts__noise_test_max_criteria_*`, the fit score's own window in a device-capability interface not yet traced) | not built: the fit score window is the open point | Pixel Buds 1.0.990706425, `gxc.l`, `gxb`, `pnq` |
| JBL | feature flags `41` in-ear test and `42` seal test in the product list; no screen traced | not built: read JBL's seal test screen first | docs/jbl/PROTOCOL.md section 4.8 |
| Apple / Beats | `SealTest` in the Powerbeats Pro 2 / Beats feature lists. Read 2026-11-10 from the Beats app 2.12 (Java and `libB2pAdapter.so`): the phone sets `046E` with the byte 1 (stop: 3), the earbuds report on `046C` (state byte 3 = result) a block of floats from which the native code computes four scores (mean of 10 log10 over bins 2 to 4 of each of two 20-bin arrays, plus offsets at payload bytes 3 and 91); the app judges them against `seal_test_descriptor.json` (product 8210: confidence 5.1, seal 4.3; 8221: 1 and 5): either of the first two under the confidence is NOISE_LEVEL_HIGH, otherwise each earbud is good when its score is at least the seal value. **While the test runs the phone plays Apple's own 22 s stimulus (`seal_test_stimulus_audio.wav`, a broadband bass-heavy stereo programme) through the earbuds** | **not built, blocked**: the verdict depends on that stimulus (the thresholds are calibrated to it), Apple's file may not be shipped and a synthetic stand-in is uncalibrated without earbuds or a recording; which block is left or right is inferred. Checked: the Java, the native receive path, the descriptor, the stimulus. Open with a user's recording of the official test | Beats 2.12 |
| Sony, Bose, 1MORE, Cambridge | nothing found in the code or strings | - | strings and file names searched |
| Jabra (research only) | yes (GAIA fit test, `FitTestRepository`) | not merged as a brand | Jabra Sound+ 5.22.0 |

## Hearing tests

Two different things go by the name. PairFree's SOUNDPEATS test is the first kind.

1. **Phone-run staircase test** (tones played by the phone, the user answers heard / not heard, the result becomes an EQ curve). Generic once a brand has a
   tone table and a custom EQ: `HearingTestScreen` takes any `EqState` and a `HearingTable` and applies the result through the EQ (`BandEq`), so any
   brand with a custom curve could take one. Only SOUNDPEATS' table (`data/soundpeats/hearing_test.json`) is a vendor's own.
2. **Earbud-side or profile tests** (the vendor's app measures, then sends a profile block the earbuds apply with their own DSP).

| Brand | Test in the vendor's app | Kind | Status |
|---|---|---|---|
| SOUNDPEATS | Adaptive EQ (8 frequencies x 10 levels) | 1 | built |
| Samsung | Adapt Sound (`hearingadjust` activities; the logic is `HearingTestManager`, `lp/f.java` in the decompiled plugin 9.0.10) | 1 and 2 | **built 2026-11-11** (docs/samsung/PROTOCOL.md section 14: the earbuds make the tones, message 204's block, level rules; unit tests only). Traced: The phone plays the tones (media volume fixed, noise control forced off, Do Not Disturb, the noise monitor listens for a noisy room) and the user answers Yes or No: six frequencies per ear, 37 steps in a diagnosis order, levels in 5 dB steps 5 to 35 (`setTestResult` moves a frequency's level up or down by the answer). The result goes to the earbuds as Samsung message 204 (`-52`) with a 25-byte block: byte 0 a state (`o(0,1)` on start), bytes 5 to 10 the left thresholds, 15 to 20 the right, byte 4 set to 1 at the end; `mb.f.O` maps a stored level to the bytes. Adapt Sound is then turned on (a setting) and the profile kept on the phone. Frequencies 250 to 8000 Hz, slots, block bytes and the profile layout were read in the second pass; no tone files are needed |
| Soundcore | HearID (`BaseHearIdActivity`, `HearIdTestVM`): the phone plays tones (a `MediaPlayer`; the user holds a button while hearing one), after an age range is chosen and the earbuds are told to start the test (`cmdStartOrCancelHearIdTest`); the curve goes to the earbuds in the HearID block of the EQ write | 1 in kind | PairFree already passes the block on and the generic test covers the idea; the tone files, the curve computation and the start command were seen but not extracted |
| JBL | PersoniFi: a seal check first ("a tone is now playing on the left ear, adjust until you hear it", `check_seal_*` strings: phone-run, the user answers; it falls back to the hearing test), then a hearing test with tones the app plays; the result is an EQ the earbuds hold as category 201 (`EQ` command, docs/jbl/PROTOCOL.md section 11) | 1 in kind (phone-run tones, answers, a curve) | covered in substance by the generic test above; JBL's own tone table and its curve rule are not extracted (the table is in the app's code, not read) |
| Nothing | Mimi hearing test (`mimi_content_hearing_test*` layouts, `com.audiodo.aspen`, Mimi SDK) and "personal sound" | 2 (Mimi's SDK) | not read |
| 1MORE | Sound ID (`ISdkSoundIdRoute`) and Hearing Protect | 2 | not read |
| OnePlus / OPPO | hearing enhancement analysis (`hearing_enhancement_analysis.json`), ear scan | 2 | not read |
| Bose | hearing assistance (a hearing aid feature on some models; limits, loudness, directionality) | aid, not a test | not read |
| Apple | needs Apple's hearing test (iOS) | - | out of reach on Android |
| Pixel, Sony, EarFun, QCY, Cambridge | none found | - | - |

Nothing in the second kind is "not possible"; it is **not read yet** and listed so it is read next (owner, 2026-11-10: read everything first).

## Added above the vendors' apps (owner, 2026-11-10)

The phone-run hearing test is offered on **every brand whose earbuds take a custom curve of three bands or more** (`hearingTable` in `ConnectedScreen.kt`), with SOUNDPEATS' tone table and PairFree's own merge onto the model's bands (`HearingEq.interpolated`: straight lines over log frequency between the results, held flat beyond the ends). It is PairFree's feature, not a vendor's: no vendor app except SOUNDPEATS does this, and the result is only an EQ curve, saved as a profile like any other. The vendors' own tests (second table above) stay to be read and built as their own kind.
