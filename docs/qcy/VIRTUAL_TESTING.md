# QCY virtual testing

Research date: **2026-11-07**. Original QCY app **4.0.7**, package `com.qcymall.googleearphonesetup`, on an isolated offline Android 37 emulator. No physical earbuds were tested. The original APK and Bluetooth encoder were unchanged.

## MeloBuds N70 / vendor ID 23877: startup only

The app crashed in its offline advertising-request error handler before onboarding. A research-only hook suppressed that startup advertisement request. Its offered Tourist Mode initiated a server request and did not complete offline. A separate local activity navigation opened the main screen without an account or vendor server authentication. Discovery normally requires a downloaded product list; a minimal **synthetic** row containing the N70 name, vendor ID and model ID was inserted into the scan activity's in-memory product list. That row is not a complete vendor control-panel configuration.

With those interventions, the original app recognized a synthetic N70 manufacturer advertisement. Android paired and connected its synthetic Classic audio profile, after which the app connected over BLE to service `A001` and subscribed to `1002`. The displayed battery values 85/80/100 came from the invented fixture. The app read EQ characteristic `000B` and gesture characteristic `000D`, both returning invented fixture values.

[emulator_vectors.json](../../data/qcy/emulator_vectors.json) retains two observed app writes on `1001`: `FF 03 FE 01 44` and `FF 03 FE 01 16`. The app source labels item `44` maximum EQ count; item `16` is balance. These are queries, not settings writes. The peer did not invent responses to them.

The Sound screen showed generic entries, but tapping Soft returned to login and produced no captured EQ write. No gesture assignment, preset, custom EQ or other setting was demonstrated. The synthetic discovery row lacks the model's control-panel settings, so the screen cannot be treated as a complete N70 configuration. Hydrating a verified panel, resolving local entry and testing settings remain open. This result does not establish that a physical N70 accepts a command, that its settings persist, or that other QCY models behave identically.
