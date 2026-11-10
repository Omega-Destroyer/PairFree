# Sony virtual testing

Research date: **2026-11-07**. Original **Sound Connect 13.3.0**, `com.sony.songpal.mdr`,
offline on the isolated Android 37 Google APIs userdebug emulator with ARM64 translation.
The APK and protocol encoders were unchanged. **No physical Sony device was tested.**

## WH-1000XM3 / table set 1 fixture

The synthetic peer exposes the set-1 RFCOMM service and SBC audio. It reports protocol `4010`
(wire payload `01 00 40 10`), model WH-1000XM3, made-up firmware `4.5.2`, battery 85%, and
only three support functions: battery `11`, preset EQ `51`, and NC/ambient `62`. This is a
restricted capability fixture, **not a claim that these are the complete XM3 capabilities**.
Other model names, firmware, table sets and feature combinations have no result from this test.

The app's privacy-center WebView was blank offline. Research instrumentation invoked its
existing local completion callback (`PrivacyCenterActivity.M2`) to continue navigation; it
did not contact the privacy service, create an account, or alter a Bluetooth command builder.
The app's own Later and Continue without restoring options skipped account/backup setup.
Pairing and A2DP connection used Android's APIs, with only the active test peer retained.

### Startup and readback

The app exchanged real RFCOMM frames with the peer, including protocol, capability counter/ID,
model, firmware, series/color, instruction-guide and support-function reads, followed by the
advertised functions' capabilities/status/parameters. The EQ capability request was
`50 01 01`: the last byte is **display language**, absent from the abbreviated request shown
in the earlier protocol notes. The capability counter was varied from 1 to 2 while changing
the synthetic preset list. Names/IDs and all replies are fixtures, not device discoveries.

ACK sequence inversion and checksums/escaping were exercised by the live exchange. Unknown
requests received transport ACKs but no invented application success response. The app also
sent `94 01 00` after entering the dashboard; it was retained only in external raw traces.

### Settings captured from the original UI

- Preset selections: `58 01 <id> 00`, covering Off `00`, factory presets `10` through `17`,
  and user slots `A1`/`A2`. The later fixture also listed Manual `A0`. These are selection
  packets; the simulator does not establish the factory presets' actual acoustic curves or
  user-slot persistence in real firmware.
- Custom curves: `58 01 FF 06 <six values>`, Clear Bass first, then 400 Hz, 1 kHz,
  2.5 kHz, 6.3 kHz, 16 kHz. Values 0/10/20 correspond to -10/0/+10 dB in this 21-step
  fixture. Captured cumulative changes cover every value, including positive/negative limits.
  Final synthetic readback was `57 01 A0 06 00 00 14 14 00 14` and the editor reopened as
  Manual with that curve. This verifies app parsing/rendering under supplied readback, not
  acceptance or retention by physical firmware.
- After curve edits the app reads status `52 01`, parameters `56 01`, and band info `5A 01`.
- Ambient control switch: `68 02 00 02 02 01 00 00` off and
  `68 02 01 02 02 01 00 00` on, preserving the fixture's NC setting.
- Slider actions emitted adjustment-start `10` and adjustment-complete `11` effect states.
  Ambient level 10 ends with `68 02 11 02 00 01 00 0A`; level 20 ends in `14`.
  NC selection ends with `68 02 11 02 02 01 00 00`.
- Voice passthrough at ambient level 10: `68 02 11 02 00 01 01 0A`.

There are **43 selected distinct setting payloads** in
[`emulator_vectors.json`](../../data/sony/emulator_vectors.json), including intermediate
slider writes. This is a packet count, not a device/feature completeness percentage.

### Negative fixture findings

Initially, fixed flat EQ replies made sliders revert after a write. Later, naively echoing
the outgoing `FF` preset into active-state readback crashed the original app with
`IllegalArgumentException: There is no preset identified by UNSPECIFIED`. The source enum
also names `FF` UNSPECIFIED. The corrected simulator explicitly chooses advertised Manual
`A0` for readback; **that simulator choice does not establish real firmware's mapping**.
Do not count the failed attempts or subsequent blind UI gestures as successful curve tests.

One source-description refinement: effect `01` is used by the on/off switch, whereas slider
adjustment uses `10`/`11`. The earlier statement that every on-write uses `11` was too broad.

Gestures, timers, DSEE, connection mode, other functions, table set 2 and PairFree's own UI
comparison remain untested in this profile. Firmware, reset and power-off flows were not opened.
Raw traces, screenshots, synthetic state, research instrumentation and reference hashes stay at
`E:\Builds\research\existing-vendor-virtual-2026-11-07`, outside the repository.
