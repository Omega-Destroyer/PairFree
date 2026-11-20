# 1MORE virtual testing

Research date: **2026-11-07**. Original 1MORE app **2.0.0**, package `com.onemore.connect`, on an isolated offline Android 37 emulator. No physical earbuds were tested. The original APK and its packet encoders were unchanged.

## Aero / ES903 fixture

Android paired and connected a synthetic Classic audio peer named `1MORE Aero`; the app opened its RFCOMM service. Initial startup sent handshake `004D 01` (`1101004d000100015d01`) and battery read `004E`. The app continued to the battery read when the handshake was unanswered; a later run returned a synthetic success. This does not establish whether a real device requires the handshake. PairFree currently suppresses it; that difference merits separate review. **PairFree's follow-up (2026-11-10):** the decompiled
app does send `004D 01` from five places and starts its reads on the answer, so the earlier note ("never sends it") was wrong;
PairFree now sends it first (not waiting for the answer) and `0069 0C` before a custom curve on models with preset sounds
(`OneMoreTest.theFramesTheAppSentToAVirtualAero` compares these vectors with PairFree's frames).

The offline app initially lacked feature controls because its product configuration was absent. A research-only Frida script deserialized the Aero row from a previously saved, owner-approved public product-list response into the app's own model and seeded its original `headsetConfigList` and `headsetConfig` LiveData in memory. It did not modify an encoder. The retained UI actions were performed in the original app after this intervention; no vendor server or account was contacted. Product capabilities, battery, gesture values and other status replies were fixture inputs, not observations of physical firmware.

For selected settings the peer acknowledged writes and returned the same value on subsequent reads. This is simulated state, not proof of device persistence. Gesture readback remained a fixed synthetic table. Firmware behavior, reconnect persistence and acoustic effects remain unverified.

## Outgoing commands observed

[emulator_vectors.json](../../data/1more/emulator_vectors.json) retains **36 distinct setting command/payload pairs** and the startup handshake, with app hashes and the associated UI actions. Each retained frame was checked for payload length and header XOR.

- All twelve displayed presets produced `0069` values `00` through `0B`, matching the documented names. Entering Custom EQ first sent `0069 0C`.
- Ten separate custom EQ slider edits produced `0053` seven-byte records. Their frequencies and Q values matched the Aero catalog. The **EQ-curve reset** produced one `0053` containing ten flat records; this was an equalizer operation, not a factory reset. The selected gain values and fixture-specific sequence numbers are in the vectors.
- Main-screen noise actions sent `005E 01` for ANC, `05` for transparency and `00` for off. Transparency reflects the app's remembered choice in this fixture; other transparency variants need independent tests.
- Auto-Pause Only, Auto-Pause/Play and No Smart Playback sent `0062 00`, `01` and `02`. Spatial audio and voice control each produced `0082` and `0080` on/off writes respectively.
- Left double-tap Next Track sent `0084 01 01`; right triple-tap Vol+ sent `0084 04 04`. Each was followed by a gesture-status read `0087`. Other mappings, hold behavior and real readback remain untested.

The app's UI actions and outgoing packets confirm these paths for this configuration only. The other 38 catalog records, different firmware and configuration variants, other gestures, remaining functions, and a comparison against PairFree's runtime behavior remain outstanding. This is not evidence of hardware acceptance or full vendor support.

## PairFree against a virtual PistonBuds PRO Q30 (2026-11-19)

PairFree 0.16.1 (debug, before the fixes below) on the Android 37 emulator against a synthetic `1MORE PistonBuds PRO Q30` (`EC305`, a Bumble peer serving the standard serial port; replies invented from the Aero fixture, with the Q30's longer battery reply). Not firmware: it shows what PairFree sends and how it reads replies, never that real earbuds accept them. The peer's first two bonds (made before an inquiry) kept the emulator's unclassified device class, so PairFree did not list them (its list keeps audio-class devices); the third, made after the Pair new device scan, was listed and connected.

- Opening: `004D 01`, `004E`, `005F`, `006C`, `0083`, `003C`, `0087`, `006A`, `0054`, `005B 01`; every read answered by the peer. Battery 85 / 100 / 80, right earbud out shown as a dash, firmware, adaptive noise cancelling, connection mode, sound mode and the gesture rows all came from those replies.
- Writes seen by the peer: `005E 00` and `005E 07` (off, cancelling), `0069 02` (Bass booster, read back with `006A`), `0084 01 01` (left double tap, next track, then `0087`), `006B 01` (game mode, then `006C`).
- Two faults found and fixed in PairFree: the Sound tab and the Home card stayed "Not known yet" after a connection because `OneMoreSession.receive` reported no change when only the preset or curve changed, and `EqOpener` skipped its report when the EQ had opened before the answers (`OneMoreTest.anEqAnswerCountsAsAChange`); and the Sound tab's note said "Samsung does not publish its curve" for every brand's presets. The older preset list's display names (`default`, `bass_reducer_1`, ...) were also missing from the names shown.
- This does not explain the real Q30 (issues #58, #63): the virtual earbuds answer. PairFree's frames and reads were as Life 5.0.3 sends them.
