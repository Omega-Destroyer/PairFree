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
