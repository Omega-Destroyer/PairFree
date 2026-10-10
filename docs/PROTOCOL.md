# SoundPEATS control protocol (from PeatsAudio 1.9.45 for Android)

Source: static analysis of `com.xingkeqi.peats` 1.9.45. The app is not obfuscated, so
class and field names below are the app's own. Each section says what is read directly
from code and what is inferred. Section 10 lists what has been confirmed on real earbuds;
everything else is still untested.

## 1. Transport

- Classic Bluetooth serial (RFCOMM) on the standard SPP UUID
  `00001101-0000-1000-8000-00805F9B34FB`.
- The earbuds must already be paired and connected at the system level. The app then
  opens the RFCOMM socket and starts sending. There is no handshake, key exchange or
  login on the wire.
- A BLE path is compiled in but switched off (`DeviceInfoFactoryKt.USE_BLE = false`);
  see section 6. Every model except the QCY-built Capsule3 Pro goes through
  `DefaultSppDeviceModel`, whatever its chip family.
- The app waits 50 ms after each write and does not wait for a reply before sending
  the next command.
- Only one client can hold the channel. Close PeatsAudio before connecting anything else.

Inferred for the H3: it is not named anywhere in the APK (its feature list comes from
SoundPEATS' server at runtime), but it is a Qualcomm QCC3091 device and the app has
exactly one code path for those. Since confirmed: the H3 answers on this channel and
its firmware code is `S5` (section 10).

## 2. Frame format

Same layout in both directions. No checksum. Big-endian.

| Offset | Size | Field    | Value                                         |
|--------|------|----------|-----------------------------------------------|
| 0      | 1    | SOF      | `FF`                                          |
| 1      | 1    | version  | `04`                                          |
| 2      | 1    | flags    | `00`                                          |
| 3      | 1    | length   | payload length in bytes                       |
| 4      | 2    | vendor   | `00 0A` (everything) or `00 1D` (EQ writes)   |
| 6      | 2    | command  | see tables                                    |
| 8      | n    | payload  |                                               |

This is Qualcomm's GAIA serial framing carrying SoundPEATS' own command set.

The H3 sends its frames with version `03`, while the phone (PeatsAudio and SoundPeter
alike) sends `04` (section 10). A reader should not insist on `04`.

- GET: command `03 xx`, empty payload.
- Reply to a GET: command `83 xx` (same number with the top bit set). Payload is usually
  `[status, value]`. Status `00` is success. The app ignores battery replies whose
  status is `01` or `02`.
- SET: command `03 yy` with the new value as payload. The GET and SET numbers are
  different; use the table.
- On the H3, a SET gets no reply of its own. About 2 s later the earbuds send the
  matching GET reply with the new value, unasked (seen for ANC mode, section 10).
  They do the same when the setting is changed by touch on the earbuds.
  Treat every GET reply as a state update, whenever it arrives.
- On the H3, the earbuds stop answering for 6 to 15 s after a touch control, then
  answer everything asked in the meantime at once (section 10).
- On the H3, a GET the firmware does not support gets no reply at all, not an error
  status.
- The app's reader treats each socket read as one frame. A new client should
  reassemble by the length byte instead.

Example, read firmware version: `FF 04 00 00 00 0A 03 09`
Example, transparency on: `FF 04 00 01 00 0A 03 11 02`

## 3. Commands (vendor `00 0A`)

All read from `FunctionEnum`, `FunctionStatusEnum` and `DefaultSppDeviceModel`.
Which of these the H3 firmware answers has to be probed.

| Feature                  | GET     | SET     | Payload / reply value                                      |
|--------------------------|---------|---------|------------------------------------------------------------|
| Firmware version         | `03 09` |         | ASCII `CODE_x_VERSION`; field 0 is the model code          |
| Battery left             | `03 06` |         | percent                                                    |
| Battery right            | `03 07` |         | percent                                                    |
| Battery case             | `03 23` |         | percent                                                    |
| ANC mode                 | `03 10` | `03 11` | 0 off, 1 ANC, 2 transparency, 3 adaptive, 4 weak           |
| ANC level (scene)        | `03 24` | `03 25` | see 3.1                                                    |
| Transparency level       | `03 4C` | `03 4D` | 1 voice enhance, 2 standard                                |
| ANC environment adaptive | `03 64` | `03 65` | 0 off, 1 on                                                |
| Game mode                | `03 0E` | `03 0F` | 0 off, 1 on                                                |
| Multipoint (dual device) | `03 14` | `03 15` | 0 off, 1 on                                                |
| LDAC                     | `03 38` | `03 39` | 0 off, 1 on                                                |
| LE Audio                 | `03 35` | `03 34` | 0 off, 1 on                                                |
| In-ear detection         | `03 0C` | `03 0D` | 0 off, 1 on                                                |
| Disable touch            | `03 12` | `03 13` | 0 off, 1 on                                                |
| Custom keys              | `03 AA` | `03 AB` | see 3.2                                                    |
| Reset custom keys        |         | `03 AC` | no payload                                                 |
| Prompt volume            | `03 19` | `03 20` | 0 to 100                                                   |
| Auto shutdown            | `03 6E` | `03 6F` | minutes: 5, 10, 30, 60, 120, or 0 for never                |
| Voice prompt language    | `03 21` | `03 22` | 0 en, 1 zh, 2 de, 3 ja, 4 fr, 5 es, 6 it                   |
| Dynamic EQ               | `03 31` | `03 32` | 0 off, 1 on                                                |
| Dynamic bass             | `03 3C` | `03 3D` | 0 off, 1 on                                                |
| Movie mode               | `03 27` | `03 28` | 0 off, 1 on                                                |
| Spatial audio            | `03 57` | `03 58` | 0 off, 1 on; mode via `03 59` (1 byte)                     |
| Panoramic sound          | `03 29` | `03 30` | 0 normal, 1 music, 2 movie                                 |
| Dolby                    | `03 48` | `03 49` | 0 off, 1 on; mode via `03 54` (1 byte)                     |
| Adaptive volume          | `03 46` | `03 47` | 0 off, 1 on                                                |
| Personalized volume      | `03 40` | `03 41` | reply bytes 1, 3, 5 carry the three values                 |
| Left / right ear volume  | `03 42` / `03 43` | `03 44` / `03 45` | 0 to 100 (marked deprecated in the app)  |
| Find earbud left / right | `03 4A` / `03 4B` | `03 3A` / `03 3B` | 0 stop, 1 beep                           |
| Light effect             | `03 0A` | `03 0B` | 0 off, 1 on                                                |
| Privacy mode             | `03 6B` | `03 6C` | 0 off, 1 on                                                |
| Fit test                 |         | `03 63` | start/stop; result arrives as `83 63`                      |
| Reset state              | `03 67` | `03 68` | reply last two bytes == 1 means "was just factory reset"   |
| Reboot                   |         | `03 16` | no payload                                                 |
| Factory reset            |         | `03 05` | no payload. Do not send casually.                          |

### 3.1 ANC levels

SET payload is one byte:

| Byte | App label                        |
|------|----------------------------------|
| `11` | "normal mode" (shown with the adaptive label) |
| `12` | indoor                           |
| `13` | outdoor                          |
| `14` | traffic                          |
| `15` | adaptive                         |
| `16` | anti-wind                        |
| `01`-`05` | numbered levels on other models |

The GET reply (`83 24`) is a list of `(level, active)` pairs, where active is `01` for
the current one. If the payload length is odd and the first byte is `00`, drop that
byte first. This means the earbuds report which levels they support.

The H3's marketed modes are Indoor, Outdoor, Traffic and Adaptive. On hardware it
reports `11`, `12`, `13` and `14`, with `11` active, and no `15`. It does not answer the
environment-adaptive GET (`03 64`). So the H3's Adaptive is most likely `11`, which the
app shows with the adaptive label. Still to check: that PeatsAudio shows Adaptive while
`11` is active, and what SET `03 25` does on the H3.

### 3.2 Custom keys

SET payload: `[gesture, action]`, one gesture per frame.
GET reply: status byte, then `(gesture, action)` pairs.

Gestures: 1 tap L, 2 tap R, 3 double L, 4 double R, 5 triple L, 6 triple R,
7 hold L (1.5 s), 8 hold R.

Actions: 0 none, 1 volume up, 2 volume down, 3 play/pause, 4 game mode,
5 previous, 6 next, 7 ANC cycle, 8 voice assistant.

## 4. EQ (vendor `00 1D`)

Write-only. The app never reads the EQ back from the earbuds. On the H3 each band write
is acknowledged with `0F 81 01`, which carries no state (section 10).

One frame per band, command `0E 01` on Qualcomm devices (`0E 02` on others, where the
first payload byte becomes two bytes: count, index).

Payload, 9 bytes:

| Bytes | Field                                                   |
|-------|---------------------------------------------------------|
| 0     | high nibble = number of bands, low nibble = band index (from 1) |
| 1-2   | master gain in dB x 60, signed                          |
| 3-4   | centre frequency in Hz x 3                              |
| 5-6   | band gain in dB x 60, signed                            |
| 7-8   | Q x 4096                                                |

Example, band 1 of 9, 63 Hz, +3.5 dB, Q 0.7: `91 00 00 00 BD 00 D2 0B 33`

Preset curves and the "classic" base curve are downloaded from SoundPEATS' server
(`eq/preset/list?otaCode=...`), not stored in the APK. On Qualcomm devices the app
snaps custom points onto the classic curve's bands and adds the gains
(`EqUtilKt.mergeQualcommEQ`), so the band count and centre frequencies the firmware
expects are not known from the APK alone. For the H3 they were read from a phone
capture (section 10) and are in `data/devices.json` as the model's `eq` entry.

The hearing-test EQ is computed in the app and sent through the same band writes.

The custom EQ screen offers fixed bands, all with Q 1.8:

- Most families: 31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k Hz (10 bands)
- Bluetrum: 62, 125, 250, 500, 1k, 2k, 4k, 8k Hz (8 bands)

What gets sent depends on the model:

- Qualcomm, plus firmware codes `QCYTS04`, `SPTS04`, `TS03WINGS2`, `ZKLXSPACEPRO`,
  `ZKLXAIR5LITE`, `S13`: the user's points are snapped to the nearest band of the
  model's "classic" curve and their gains added to it. The band count stays that of
  the classic curve.
- Everything else: the classic curve's bands are sent first, then the user's bands
  appended.

## 5. Chip families

The app sorts every model into a family ("chip scheme"). The family decides the
firmware-update path and a few control details. It does not change the frame format
or the command numbers.

| Family code | Vendor            | Control path                   | Differences in control                         |
|-------------|-------------------|--------------------------------|------------------------------------------------|
| `QUALCOMM`  | Qualcomm          | SPP frames (sections 1 to 4)   | EQ command `0E 01`; custom-key reply has a leading status byte |
| `WUQI`      | WUQI Micro        | SPP frames                     | EQ `0E 02`, except `WQT7033` and old `BS519W2D` firmware (0.4.5 and earlier), which use `0E 01` |
| `JIELI`     | Jieli             | SPP frames                     | EQ `0E 02`                                     |
| `FOTA`      | Bluetrum          | SPP frames                     | EQ `0E 02`; 8-band custom EQ; LDAC conflicts with some features |
| `BES`       | Bestechnic        | SPP frames                     | EQ `0E 02`                                     |
| `ACTIONS`   | Actions           | SPP frames                     | EQ `0E 02`, except `ATS3019DK12` (`0E 01`)     |
| `SMARTLINK` | Smartlink         | SPP frames                     | EQ `0E 01` on `T57`                            |
| `AIROHA`    | Airoha            | listed, no models in the APK   |                                                |
| `QCY-WUQI`  | QCY-built (WUQI)  | QCY's own SDK over BLE         | separate protocol, not analysed; Capsule3 Pro only |

`data/devices.json` holds the model table compiled into the app: 45 firmware codes with
their Bluetooth names and family. Newer models are missing from it because the app
downloads their entries. Their codes look like `S7`, `S21`, `S27`.

Identify a model by the first field of the firmware-version reply (`03 09`).

## 6. BLE transport

The Android app carries a complete BLE path that is switched off. It is very likely
what the iPhone app uses, since iOS cannot open SPP to these earbuds.

- Service `00001100-D102-11E1-9B23-00025B00A5A5` (Qualcomm's GAIA BLE service)
- Write characteristic `00001101-D102-11E1-9B23-00025B00A5A5`, write with response
- Notify characteristic `00001102-D102-11E1-9B23-00025B00A5A5`
- On Qualcomm models the first four bytes of the frame (SOF, version, flags, length)
  are dropped in both directions: a BLE packet is vendor, command, payload.
  Other families send the full frame.
- The app ignores the notification `FF 04 01 80 01`.
- The earbuds' BLE advertisement carries manufacturer data containing the classic
  Bluetooth address, which the app uses to match the BLE device to the paired one.
  In the longer form it also carries left, right and case battery and a version string.

Whether a given model's firmware exposes this service needs a scan to confirm.
The H3 does, and GETs work over it as described above (section 10).

## 7. Firmware update

Each family uses its chip vendor's own update protocol, through that vendor's stock
SDK. None of them is SoundPEATS-specific. All run over classic Bluetooth serial.

| Family    | SDK in the app                              | Transport                                | Notes |
|-----------|---------------------------------------------|------------------------------------------|-------|
| Qualcomm  | Qualcomm GAIA client and upgrade library    | GAIA over RFCOMM                         | Standard GAIA "Upgrade" feature. The library is not obfuscated in the APK: opcodes, state machine and resume logic are all readable. The app auto-confirms every prompt (transfer complete, commit, in progress). |
| WUQI      | `org.wuqi.android.wuqibluetooth.upgrade`    | SPP on UUID `00007034-0000-1000-8000-00805F9B34FB` | Readable state machine, single-OS and dual-OS variants. |
| Jieli     | `com.jieli.jl_bt_ota` (RCSP)                | SPP                                      | Includes a device authentication step whose maths lives in a native library (`libjl_ota_auth.so`), not analysed. |
| Bluetrum  | `com.bluetrum.fota` (ABOta)                 | SPP, with a BLE service `FF12` (`FF14` in, `FF15` out) also present | Small library, 12 classes. |
| BES       | `com.bes.sdk` OTA service                   | SPP, service UUID `66666666-6666-6666-6666-666666666666` | |
| Actions   | `com.actions.bluetooth.ota`                 | SPP                                      | |
| Smartlink, Airoha | none                                |                                          | The app throws "not implemented". |

Qualcomm upgrade opcodes, for reference: `START_REQ 1`, `START_CFM 2`,
`DATA_BYTES_REQ 3`, `DATA 4`, `ABORT_REQ 7`, `ABORT_CFM 8`,
`TRANSFER_COMPLETE_IND 11`, `TRANSFER_COMPLETE_RES 12`, `PROCEED_TO_COMMIT 14`,
`COMMIT_REQ 15`, `COMMIT_CFM 16`, `ERROR_IND 17`, `COMPLETE_IND 18`, `SYNC_REQ 19`,
`SYNC_CFM 20`, `START_DATA_REQ 21`, `IS_VALIDATION_DONE_REQ 22`,
`IS_VALIDATION_DONE_CFM 23`, `ERROR_RES 31`. In GAIA v3 they travel inside the
Upgrade feature's commands: connect 0, disconnect 1, control 2, set data endpoint mode 4.

Firmware images are not in the APK. The app asks SoundPEATS' server
(`ota/version/check`, sending `otaCode` and `otaVersion`) and gets back a version,
release notes, a size and one or more download URLs. Request and response bodies are
AES-encrypted with a key compiled into the app.

## 8. What the app gets from the server

Base URL `https://peats.xingkeqi.com/prod/`. None of this is needed to control the
earbuds, but it is where per-model knowledge lives.

| Endpoint                                   | Returns                                                    |
|--------------------------------------------|------------------------------------------------------------|
| `ota/pairDataList`                         | Supported models: Bluetooth name, firmware code, family    |
| `ota/functionList?otaVersion=&otaCode=`    | Which features a model has and which values each accepts. Feature codes only (`FSN08` and so on); the command bytes always come from the table in section 3. |
| `eq/preset/list?otaCode=&type=`            | Preset curves: gain, frequency and Q arrays plus master gain |
| `ota/version/check`                        | Firmware version and download URLs                         |

A replacement client can skip the function list by sending every GET and seeing which
ones the earbuds answer.

## 9. Model-specific behaviour found in code

- Right-battery smoothing: if right reads 5 or more above left and is under 99, the
  app shows it 3 lower. Cosmetic.
- `S21` (Cove Pro): "dynamic bass" is done by sending an EQ curve, not the `03 3D` switch.
- Feature code `FSN34` ("focus mode"): reuses the ANC command `03 10`/`03 11` as a
  plain on/off switch.
- Fit test: Qualcomm models go through Qualcomm's own GAIA earbud-fit feature, with
  the app switching ANC itself during the test on every model except `S27` and `S32`
  (Air6 Pro). Other families use `03 63`.
- `ZKLXAIR5LITE` (Air5 Lite) and `ZKLXCHASERS` (Chasers): movie mode is only
  available while multipoint is on.
- `S7` on firmware 0.0.15: the app forces an update prompt.
- Bluetrum, BES and Jieli models: turning LDAC on disables some other features
  (dynamic EQ among them). The exact set differs by family.

## 10. Confirmed on hardware

Raw output for each model is in `data/reports/`.

### SoundPEATS H3: firmware code `S5`, version 0.4.9

Tested 2026-10-10 from Windows over the SPP COM port. Report: `data/reports/S5_0.4.9.txt`.

- Transport and framing are as in sections 1 and 2. Replies to GETs arrive within
  about 0.3 s. Every reply had status `00`.
- Firmware version reply: `S5_20250605_V0.4.9`, which is firmware code, build date, version.
- GETs answered: firmware version, battery left, right and case, ANC mode, ANC level,
  transparency level, game mode, multipoint, LDAC, LE Audio, disable touch, custom keys,
  prompt volume, voice language.
- GETs with no reply, even after 2.5 s: environment adaptive, in-ear detection, auto
  shutdown, dynamic EQ, dynamic bass, movie mode, spatial audio, panoramic sound, Dolby,
  adaptive volume, personalized volume, light effect, privacy mode, reset state.
- Game mode answers `0`, and PeatsAudio showed it as not available during this run. The
  owner reports that PeatsAudio offers game mode and dual device only while LDAC is off,
  and LDAC was on. Recorded as `ldac_excludes` for `S5` in `data/devices.json`. Whether
  the firmware refuses these with LDAC on, or only the app hides them, is not known.
- Values matched the settings PeatsAudio showed: ANC off, multipoint off, touch
  enabled, LDAC on.
- ANC levels: see 3.1. Transparency levels: `01` and `02` listed, `02` active.
- Custom keys: reply as in 3.2, with the leading status byte. Hold left reads action
  `07` and does cycle the ANC modes. The other gestures were not checked against the app.
- The case battery is reported while both earbuds are out of the case.
- Battery `FF` (status `00`) came back from both earbuds once, for a moment, while the
  left earbud was held. Treat `FF` as unknown, not as a percentage.
- SET ANC mode (`03 11`) with `01`, `02`, `00`: each took effect, and the voice prompts
  said "ANC on", "Passthrough", "ANC off". No `83 11` came back. About 2 s after each SET
  the earbuds sent `83 10` with the new mode, unasked; after the first SET it came twice.
- The ANC level stayed `11` through all three mode changes.
- ANC changed by touch (hold left, three times): each change was announced with an
  unasked `83 10` carrying the new mode, the same as after a SET. The first came twice.
- After each touch the earbuds stopped answering for about 13, 15 and 6 s. On Windows
  the writes blocked during the pause. Then every request sent in the meantime was
  answered at once. The link recovered on its own each time; reopening the port was
  not needed. The SETs above caused no pause.
- BLE advertisement: name `SOUNDPEATS H3`, services Battery `180F` and Device
  Information `180A`, manufacturer data under company ID `0x000E`:
  `53 05 64 FF 2F 98 80 BB XX XX XX 00 30 2E 34 2E 39`. It carries the classic
  address (`98 80 BB XX XX XX`) and the version string `0.4.9`, as section 6 says.
  Probably `64` is left battery 100, `FF` right battery unknown and `2F` case battery
  47 (the case read 47 over SPP), and `53 05` may stand for firmware code `S5`. Not
  confirmed.
- BLE GATT: the GAIA service `00001100-D102-11E1-9B23-00025B00A5A5` has `1101` (write),
  `1102` (notify, read) and `1103` (write without response, notify, read). The other
  services are Generic Access, Generic Attribute, Device Information, Battery twice,
  `0000EB10-D102-11E1-9B23-00025B00A5A5` (characteristics `EB11` to `EB14`) and `FD92`
  (characteristics `EB20` to `EB22`, same base UUID as GAIA).
- Device Information reads manufacturer `Qualcomm`, model `QCC3091`. The serial
  number, firmware and software revision strings are placeholders
  (`ABCDEF0123456789`, `1.0.0."(unreleased)"`, `20.3`).
- The two Battery Level characteristics both read 100. Which earbud each one belongs
  to is not known.
- GETs over GAIA BLE work as section 6 says. Writing `00 0A 03 09` to `1101` (write
  with response) brought `00 0A 83 09 00` + `S5_20250605_V0.4.9` as a notification on
  `1102` within 0.4 s. Battery left worked the same way. The 4-byte header is dropped
  in both directions.

Phone session, 2026-10-10, from SoundPeter's frame log and the phone's Bluetooth HCI
snoop log (raw frames at the end of the report). SoundPeter and then PeatsAudio did the
same steps.

- The earbuds send their frames with version byte `03`; both apps send `04`.
- `find_left` and `find_right` (`03 4A`, `03 4B`) answer `00 00`.
- SET transparency level (`03 4D`) is answered at once, within 0.2 s, by `83 4C` with
  the new level marked active, unlike ANC mode. PeatsAudio sends the same values as the
  table (`01` voice, `02` standard). By ear, in both apps, `01` takes effect but going
  back to `02` does not change the sound, although the earbuds report `02` as active.
  Looks like a firmware fault; nothing different to send was found.
- SET LDAC (`03 39`) and SET multipoint (`03 15`) get no reply. About 3 to 4 s later the
  earbuds close the serial channel; it can be opened again about 2 s after that. After
  LDAC off, multipoint reads `1`; after multipoint off, LDAC reads `1`. So on the H3 they
  are one either-or setting (`ldac_or_multipoint` in `data/devices.json`). PeatsAudio sends
  only the one SET and sees the same.
- Right after the channel reopened following LDAC off, an unasked `83 17 00 01` arrived.
  `03 17` is not in the command table; its meaning is not known.
- EQ, as PeatsAudio writes it to the H3: command `0E 01` on vendor `00 1D`, ten bands,
  all ten in every write, about 50 ms apart, no reply. Master gain is -2.0 dB in every
  frame. Bands, with the Q and base gain the app sends when every slider is at 0 (its
  Reset):

  | Band | Hz    | Q   | Base gain dB |
  |------|-------|-----|--------------|
  | 1    | 50    | 0.5 | +3.5         |
  | 2    | 160   | 1.6 | -6.0         |
  | 3    | 350   | 0.7 | -2.6         |
  | 4    | 800   | 1.0 | +3.7         |
  | 5    | 1600  | 1.0 | +5.5         |
  | 6    | 2800  | 1.5 | +6.5         |
  | 7    | 4600  | 1.8 | -2.5         |
  | 8    | 8000  | 6.0 | -3.5         |
  | 9    | 12000 | 1.5 | +1.2         |
  | 10   | 17000 | 1.5 | -0.2         |

  Each band's gain is the base gain plus the user's slider, as section 4 says. The
  sliders go from -6 to +6 in whole dB; the screen labels 1600, 2800 and 4600 Hz as 2k,
  3k and 5k. The owner's saved curve (+2, +1, 0, 0, 0, 0, 0, +1, +2, +2) and three edits
  match this exactly; they are the `eq_layout` test vectors. Opening the Customize EQ
  screen sends the current curve. The base curve is the model's stock tuning: Reset
  sends it alone.
- Every `0E 01` band write is answered within about 50 ms by vendor `00 1D` command
  `0F 81` with payload `01`, for PeatsAudio and SoundPeter alike. The same value every
  time; what it means beyond "received" is not known.
- PeatsAudio sends its saved curve about 2.5 s after it connects, so it overwrites an
  EQ another app set.
- Presets, from a second capture: picking a preset sends a complete curve of 10 bands
  at the same -2 dB master gain. SOUNDPEATS Classic is the base curve. Bass Boost, Bass
  Reduce, Treble Boost, Pop and Rock keep the base curve's frequencies and Q but not its
  gains (and not in whole-dB steps from it). Electronic, Classical, Folk, Book Whisper,
  HIFI and ACG also move some bands (for example 20 Hz, 5 kHz, or 6.8, 9.8 and 14.5 kHz)
  or change their Q. All eleven are in `data/devices.json` (`eq.presets`) and are the
  `eq_preset` test vectors.
- The unasked `83 17 00 01` also arrives about 6 s after a plain connect, with no LDAC
  change before it.
- Prompt volume read 18 in this session (11 in the first); it had been changed in between.

Still open on the H3:

- The scale of prompt volume. It read 11, then 18 after a change.
- Why transparency `02` does not sound different after `01` (above).
- What `83 17` means.
- Whether a master gain other than -2 dB has an effect. The app's preamp only lowers it.
- Over BLE: SETs, unasked state updates, and what `1103`, `EB10` and `FD92` are for.
