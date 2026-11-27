# JBL virtual testing

Research date: **2026-11-07**. Original JBL Headphones **5.37.10**, `jbl.stc.com`,
offline on an isolated Android 37 Google APIs userdebug emulator with ARM64 translation.
The original APK and command encoders were unchanged. **No physical JBL device was tested.**

## Tune 230NC TWS / BES direct-control fixture

The original app recognizes a synthetic `JBL TUNE230NC TWS` peer with product ID `204B`,
paired Classic audio, the app's manufacturer advertisement and phone-name CRC, and the
documented `excelpoint.com` GATT service with notification and write characteristics.
Android connected A2DP and the app subscribed to notifications and opened its BLE control
connection in security mode 0.

The first connection reached the product list but did not reach settings. A research-only
invocation of the original `Tune230Control.getAllDashboardCommands()` demonstrated BLE
startup queries; it did not alter an encoder or any return value. Subsequently the peer was
configured to send a **synthetic unsolicited `AA 12` device-info notification** after
subscription. The app then completed its normal product navigation and emitted further
startup queries. This push is a fixture intervention, not evidence that real Tune 230NC
firmware sends it unsolicited. Passive Frida hooks observed connection/logging during the
earlier investigation; no hooks were needed for the retained UI setting writes.

Battery 85/80/100, firmware `1.0.0`, status, gesture and noise replies are invented fixture
values. A few known setting writes receive synthetic success ACKs. The gesture status remains
the seeded table, and no device persistence or physical effect follows from these tests.
Unknown requests receive no invented application-level success. The EQ design read `42 00`
was unanswered, so **calibration `-1` in the observed EQ writes is fixture-dependent**.

## Captured outgoing commands

[emulator_vectors.json](../../data/jbl/emulator_vectors.json) contains **11 distinct UI
setting frames**, **13 selected startup reads** after the synthetic identity notification,
and **six multipart EQ transfer sequences**. Installed APK hashes are recorded there. The
fixture's invented replies, Bluetooth address and phone-name CRC are not in the vectors.

- Ambient control `91`: ANC `10 01 01 02 00 03 00`, Ambient Aware
  `10 01 00 02 01 03 00`, TalkThru `10 01 00 02 00 03 01`, and off `13`.
- Choosing left-ear Volume Control sends `77 00 FD 00`, followed by
  `77 00 06 01 07 02 08 00 0C A1`. Choosing None on the right sends
  `77 00 FE 00`, followed by `77 00 09 00 0A 00 0B 00 0E 00`.
  The original `CmdGen.genResetGestureInBach77` names `FD` and `FE` as left/right
  gesture resets; these are gesture operations, not a factory reset. The subsequent
  frames set the displayed four-gesture categories. Other choices and readback remain open.
- Smart Audio & Video uses the **legacy BES path**: the running app reads `AA 82 00`
  and writes `AA 81 08` followed by four little-endian 16-bit values, with **no leading
  subcommand byte**. Captured Audio values are `00 01 2E 00 18 01 FF FF` and Normal
  values are `C5 00 2E 00 18 01 FF FF`. The original `CmdGen` confirms `82`/`81`, and
  `HandleParse` reads the response under `83`. `SmartSwitchCmd` also defines a separate
  `80` subcommand format for another path. PairFree currently uses the latter for its
  general direct path, so its Tune 230NC Smart Audio & Video handling needs review; this
  research commit does not change production code. Other models need their own path checked.
  **PairFree's follow-up (2026-11-10):** JBL's app has two status classes with identical methods and bytes except this
  one. The BES class (base of every control class that extends `BesBasicControl` or `BesSdkControl`) uses `82`/`81`/`83`,
  the protocol 3 class `80`. The 39 models on the BES base now use the BES commands (`smart_switch: bes` in
  `devices.json`, `JblTest.theBesModelsSendSmartAudioAndVideoOverTheirOwnCommands` replays these vectors); the others keep
  `80`. The left-Volume and right-None gesture frames PairFree writes equal the captured ones, apart from the app's
  `77 00 FD 00` / `FE 00` reset of the side first, which PairFree leaves out because its set writes all four gestures.
- Selecting VOCAL and BASS presets sent `AA 41 B3 03`, three separately written BLE
  parts indexed 0, 1, 2 containing **179 data bytes**, then `AA 05 41`. The BASS body has
  category 0 and ten filters at 32 Hz through 16 kHz. Creating/editing My EQ produced
  category 1 and a fractional, interpolated ten-band curve. Saving it and selecting it
  again after BASS retransmitted exactly the captured custom parts. This establishes local
  profile recall and outgoing bytes, not earbud storage or readback.

An early generic lab parser incorrectly concatenated the multipart `41` transfer and
recorded a truncated ordinary frame. The vectors retain the original individual GATT writes,
not that parser result; the parser was subsequently corrected and captured later transfers
with the same boundaries. UI action windows can end just before the final part, so the EQ
sequences use the peer's timestamped GATT records.

No other JBL model, BLE security mode, protocol 3/4, Airoha or LE Audio family inherits this
result. Remaining work includes the other presets, more gesture/action combinations,
negative replies and device-state readback, timers, codec behavior, and comparison with
PairFree against the same fixture. No firmware, factory-reset or power-off operation was sent.

Original APKs, scripts, logs, synthetic peer state, raw captures and pairing state remain
outside the repository under `E:\Builds\research\existing-vendor-virtual-2026-11-07`.
