# SoundPEATS control protocol (from PeatsAudio 1.9.45 for Android)

Source: static analysis of `com.xingkeqi.peats` 1.9.45. The app is not obfuscated, so
class and field names below are the app's own. Nothing here has been tested against
real earbuds yet. Each section says what is read directly from code and what is inferred.

## 1. Transport

- Classic Bluetooth serial (RFCOMM) on the standard SPP UUID
  `00001101-0000-1000-8000-00805F9B34FB`.
- The earbuds must already be paired and connected at the system level. The app then
  opens the RFCOMM socket and starts sending. There is no handshake, key exchange or
  login on the wire.
- A BLE path is compiled in but switched off (`DeviceInfoFactoryKt.USE_BLE = false`);
  see section 6. Every model except the QCY-built Capsule3 Pro goes through
  `DefaultSppDeviceModel`, whatever its chip family. That class has no branch on
  model or chip: every model gets the same frames and the same command numbers.
  The Capsule3 Pro uses a different protocol over BLE, described in section 10.
- The app waits 50 ms after each write and does not wait for a reply before sending
  the next command.
- Only one client can hold the channel. Close PeatsAudio before connecting anything else.

Inferred for the H3: it is not named anywhere in the APK (its feature list comes from
SoundPEATS' server at runtime), but it is a Qualcomm QCC3091 device and the app has
exactly one code path for those.

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

- GET: command `03 xx`, empty payload.
- Reply to a GET: command `83 xx` (same number with the top bit set). Payload is usually
  `[status, value]`. Status `00` is success. The app ignores battery replies whose
  status is `01` or `02`.
- SET: command `03 yy` with the new value as payload. The GET and SET numbers are
  different; use the table.
- The app's reader treats each socket read as one frame. A new client should
  reassemble by the length byte instead.

Example, read firmware version: `FF 04 00 00 00 0A 03 09`
Example, transparency on: `FF 04 00 01 00 0A 03 11 02`

## 3. Commands (vendor `00 0A`)

All read from `FunctionEnum`, `FunctionStatusEnum` and `DefaultSppDeviceModel`.
The table is the same for every model on the common protocol. A model supports a
subset of it. Which subset comes from SoundPEATS' server (section 8), so for any
model it has to be probed.

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
| Dolby support hint       |         | `03 62` | 0 if the phone supports Dolby, 1 if not. Sent only to `S7` (Clip1). |
| Adaptive volume          | `03 46` | `03 47` | 0 off, 1 on                                                |
| Personalized volume      | `03 40` | `03 41` | reply bytes 1, 3, 5 carry the three values                 |
| Left / right ear volume  | `03 42` / `03 43` | `03 44` / `03 45` | 0 to 100 (marked deprecated in the app)  |
| Find earbud left / right | `03 4A` / `03 4B` | `03 3A` / `03 3B` | 0 stop, 1 beep                           |
| Light effect             | `03 0A` | `03 0B` | 0 off, 1 on                                                |
| Privacy mode             | `03 6B` | `03 6C` | 0 off, 1 on                                                |
| Fit test                 |         | `03 63` | start/stop; result arrives as `83 63`                      |
| Reset state              | `03 67` | `03 68` | reply last two bytes == 1 means "was just factory reset"   |
| Connect classic          | `03 17` |         | Defined in the app with reply `83 17`, never sent by it. May trigger an action. Do not probe. |
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

The H3's marketed modes (Indoor, Outdoor, Traffic, Adaptive) line up with `12` to `15`.
How `11`, `15` and the environment-adaptive switch (`03 64`/`03 65`) relate on the H3
needs a live test. What the app's code says about them:

- On firmware codes `S15`, `S21` and `S22` the app shows level `11` under the
  "adaptive" label. On every other model it shows it as "normal mode".
- The app shows the environment-adaptive switch only when the firmware code is `S22`.
- On `S22` firmware 0.4.6 the app sends outdoor (`13`) when the user picks indoor (`12`).

Which products `S15` and `S22` are is not in the APK.

### 3.2 Custom keys

SET payload: `[gesture, action]`, one gesture per frame.
GET reply: status byte, then `(gesture, action)` pairs.

Gestures: 1 tap L, 2 tap R, 3 double L, 4 double R, 5 triple L, 6 triple R,
7 hold L (1.5 s), 8 hold R.

Actions: 0 none, 1 volume up, 2 volume down, 3 play/pause, 4 game mode,
5 previous, 6 next, 7 ANC cycle, 8 voice assistant.

## 4. EQ (vendor `00 1D`)

Write-only. The app never reads the EQ back from the earbuds.

One frame per band. The command is `0E 01` or `0E 02`. With `0E 02` the first
payload byte becomes two bytes: count, index.

The app picks `0E 01` when any of these holds, and `0E 02` otherwise
(`BluetoothProductFirmwareEnum.useOldEqPact`):

- the family is `QUALCOMM`. This holds for every Qualcomm model, including the ones
  whose compiled-in flag is false (Air5, Air6 Pro);
- the model's compiled-in "old EQ" flag is true. Outside the Qualcomm family that
  is `WQT7033`, `T57` and `ATS3019DK12`;
- the code is `BS519W2D` and the firmware version is 0.4.5 or lower.

`data/devices.json` carries the result per model as `eq_command`.

Payload, 9 bytes:

| Bytes | Field                                                   |
|-------|---------------------------------------------------------|
| 0     | high nibble = number of bands, low nibble = band index (from 1) |
| 1-2   | master gain in dB x 60, signed                          |
| 3-4   | centre frequency in Hz x 3                              |
| 5-6   | band gain in dB x 60, signed                            |
| 7-8   | Q x 4096                                                |

Example, band 1 of 9, 63 Hz, +3.5 dB, Q 0.7: `91 00 00 00 BD 00 D2 0B 33`

Open points for the H3:

- Preset curves and the "classic" base curve are downloaded from SoundPEATS' server
  (`eq/preset/list?otaCode=...`), not stored in the APK. On Qualcomm devices the app
  snaps custom points onto the classic curve's bands and adds the gains
  (`EqUtilKt.mergeQualcommEQ`), so the band count and centre frequencies the firmware
  expects are not known from the APK alone. Get them from one phone capture, or by
  experiment.
- The hearing-test EQ is computed in the app and sent through the same band writes.

The custom EQ screen offers fixed bands, all with Q 1.8:

- Most families: 31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k Hz (10 bands)
- Bluetrum: 62, 125, 250, 500, 1k, 2k, 4k, 8k Hz (8 bands)

What gets sent depends on the model:

- Qualcomm, plus firmware codes `QCYTS04`, `SPTS04`, `TS03WINGS2`, `ZKLXSPACEPRO`,
  `ZKLXAIR5LITE`, `S13`, `S15`, `S22`, `S29`: the user's points are snapped to the
  nearest band of the
  model's "classic" curve and their gains added to it. The band count stays that of
  the classic curve.
- Everything else: the classic curve's bands are sent first, then the user's bands
  appended.

## 5. Chip families

The app sorts every model into a family ("chip scheme"). The family decides the
firmware-update path and a few control details. It does not change the frame format
or the command numbers.

The earbuds do not report their family. The app looks it up by Bluetooth name in a
model list it downloads from SoundPEATS' server (`ota/pairDataList`), and falls back
to the list compiled into the APK. A client that works offline needs its own table:
`data/devices.json`, keyed by the firmware code from the `03 09` reply.

| Family code | Vendor            | Control path                   | Differences in control                         |
|-------------|-------------------|--------------------------------|------------------------------------------------|
| `QUALCOMM`  | Qualcomm          | SPP frames (sections 1 to 4)   | EQ command `0E 01`; EQ merged onto the classic bands; custom-key reply has a leading status byte; BLE packets drop the 4-byte header |
| `WUQI`      | WUQI Micro        | SPP frames                     | EQ `0E 02`, except `WQT7033` and old `BS519W2D` firmware (0.4.5 and earlier), which use `0E 01` |
| `JIELI`     | Jieli             | SPP frames                     | EQ `0E 02`                                     |
| `FOTA`      | Bluetrum          | SPP frames                     | EQ `0E 02`; 8-band custom EQ; LDAC conflicts with some features |
| `BES`       | Bestechnic        | SPP frames                     | EQ `0E 02`. No models in the APK's table. Clip1 (`S7`) is reported to use a BES chip. |
| `ACTIONS`   | Actions           | SPP frames                     | EQ `0E 02`, except `ATS3019DK12` (`0E 01`)     |
| `SMARTLINK` | Smartlink         | SPP frames                     | EQ `0E 01` on `T57`                            |
| `AIROHA`    | Airoha            | listed, no models in the APK   |                                                |
| `QCY-WUQI`  | QCY-built (WUQI)  | QCY's own SDK over BLE         | separate protocol, see section 10; Capsule3 Pro only |

`data/devices.json` holds the model table compiled into the app: 45 firmware codes with
their Bluetooth names and family, plus the transport, EQ command and EQ merge rule
that follow from the app's code. Newer models are missing from it because the app
downloads their entries. Their codes look like `S7`, `S21`, `S27`.

Identify a model by the first field of the firmware-version reply (`03 09`).

### 5.1 What differs from model to model

Everything on the common protocol differs in these ways only:

| What                         | Decided by                    | Where it is recorded                      |
|------------------------------|-------------------------------|-------------------------------------------|
| Which features exist         | Server function list          | Probe the earbuds. See section 8.         |
| Which values a feature takes | Server function list, and for ANC levels the earbuds' own reply | Probe          |
| EQ command (`0E 01`/`0E 02`) | Family and firmware code      | `eq_command` in `data/devices.json`       |
| EQ merge rule                | Family and firmware code      | `eq_merge` in `data/devices.json`         |
| Custom EQ band count         | Family (8 on Bluetrum, else 10) | `custom_eq_bands` in `data/devices.json` |
| Classic EQ curve and presets | Server, per firmware code     | Not in the APK                            |
| Custom-key reply layout      | Family (Qualcomm has a status byte) | Section 3.2                         |
| BLE packet framing           | Family (Qualcomm drops the header) | Section 6                            |
| Firmware update              | Family                        | Section 7                                 |
| Quirks                       | Firmware code                 | Section 9                                 |

### 5.2 Newer models

Firmware codes the app's code mentions without listing the model:

| Code  | Product            | Source of the name     | Family                                     |
|-------|--------------------|------------------------|--------------------------------------------|
| `S7`  | Clip1              | log string             | BES by public report, not stated in the app |
| `S9`  | unknown            |                        | unknown                                    |
| `S13` | PearlClip Pro (LDAC) | model table          | Bluetrum                                   |
| `S15` | unknown            |                        | unknown, probably not Qualcomm             |
| `S19` | PopClip2           | constant name          | Bluetrum                                   |
| `S21` | Cove Pro           | class name             | unknown                                    |
| `S22` | unknown            |                        | unknown, probably not Qualcomm             |
| `S27`, `S32` | Air6 Pro    | model table            | Qualcomm                                   |
| `S29` | GoFree2S           | constant name          | Bluetrum                                   |

"Probably not Qualcomm" is an inference: the app names `S15` and `S22` in a list that
sits next to a separate check for the Qualcomm family, which would be redundant if
they were Qualcomm.

The H3 and the Air5 Pro+ appear nowhere in the APK. Public reviews name the Qualcomm
QCC3091 for the Air5 Pro, Air5 Pro+ and Air6 Pro. `data/devices.json` lists these
reports under `reported_chips` with their sources.

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

The APK ships no per-model feature list and no EQ curves. Its assets hold only
animations, hearing-test tones and SDK files.

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
- `S7` (Clip1) on firmware 0.0.15: the app forces an update prompt.
- `S7` (Clip1): 60 ms after reading the device state the app sends `03 62` with one
  byte saying whether the phone supports Dolby (0 yes, 1 no).
- `BS519W2D` (Air3 Deluxe HS) and `AIR3DELUXEHSW53D` (by its name, another Air3
  Deluxe HS build): the app treats the touch-disable switch as inverted. Whether the
  inversion applies to the reply, the write or both was not traced.
- `S9`: when a feature that conflicts with EQ is turned off, the app does not put
  the previous EQ back. On every other model it does.
- `S22`: see section 3.1.
- `JIELILIFE3` (Life3): the firmware updater runs in single-backup mode.
- `S27` and `S32` (Air6 Pro): the auto-shutdown sheet shows a different hint. Cosmetic.
- Turning LDAC on disables other features. The set depends on the family:
  - every family: game mode;
  - Jieli: also spatial audio, Dolby and movie mode;
  - BES: those, plus dynamic EQ and dynamic bass;
  - Bluetrum, and codes `ZKLXAIR5LITE`, `ZKLXCHASERS`, `S13`, `S19`, `S29` whatever
    family they report: game mode, spatial audio, Dolby, movie mode and dynamic
    bass. Dynamic EQ is added only on `ZKLXAIR5LITE`, `ZKLXCHASERS`, `S13` and `S29`.

## 10. Capsule3 Pro (QCY protocol)

The Capsule3 Pro (firmware codes `QCYTS04` and `SPTS04`) is built by QCY. The app
drives it through QCY's library over BLE, not through the frames in sections 1 to 4.
Read from that library and from `QcyBleDeviceModel`. Not confirmed on hardware.
`data/qcy_commands.json` holds the same information in machine-readable form.

### 10.1 Transport

- BLE GATT service `0000A001-0000-1000-8000-00805F9B34FB`.
- Characteristics, all with the same base UUID:

  | UUID prefix | Use                                                        |
  |-------------|------------------------------------------------------------|
  | `00001001`  | Commands. Write item packets here.                         |
  | `00001002`  | Settings. Read or notify: an item packet with current state. |
  | `00000007`  | Version. Read: ASCII `CODE_DATE_VERSION`.                  |
  | `00000008`  | Battery. Read: three bytes.                                |
  | `0000000B`  | EQ. Read or write.                                         |
  | `0000000D`  | Gestures. Read or write.                                   |

- The app enables notifications on every characteristic of the service that offers them.
- Finding the earbuds: they advertise manufacturer data under company ID `0x522C`,
  at least 20 bytes. Bytes 12, 11, 13, 16, 15, 14, in that order, are the classic
  Bluetooth address. The app scans and connects to the advertiser whose classic
  address matches the paired earbuds. Bytes 5, 6 and 7 carry left, right and case
  battery in the same format as the battery characteristic.

### 10.2 Item packets

`FF`, then the length of everything after that byte, then one or more items. An item
is: id, value length, value.

| Id   | Meaning          | Value                                              |
|------|------------------|----------------------------------------------------|
| `0C` | Noise mode       | `02` off, `63` ANC, `A5` transparency              |
| `09` | Game mode        | `01` on, `02` off                                  |
| `10` | Touch disabled   | `01` on, `02` off                                  |
| `23` | LDAC             | `01` on, `00` off                                  |
| `24` | Multipoint       | `01` on, `00` off                                  |
| `19` | Prompt language  | ASCII `WQ00` en, `WQ01` zh, `WQ02` ja, `WQ03` de   |
| `FE` | Request          | one item id; the earbuds then report that item     |
| `26` | Reset gestures   | empty                                              |
| `03` | Factory reset    | empty. Do not send casually.                       |

Example, ANC on: `FF 03 0C 01 63`
Example, ask for the multipoint state: `FF 03 FE 01 24`

Notes:

- The app treats multipoint and LDAC as exclusive. Turning multipoint on first sends
  LDAC off; turning it off first sends LDAC on. Its separate LDAC switch does nothing
  on this model.
- To read state the app reads the settings characteristic and parses the items in it.
  For multipoint it sends the request item instead.
- The library also defines reset settings (`01`), clear pairing (`02`), restart (`25`)
  and set Bluetooth name (`18`). The app never sends them.

### 10.3 Battery

Three bytes: left, right, case. Bit 7 is the charging flag. The low 7 bits are percent.

### 10.4 Gestures

Written and read on the gestures characteristic as bare `[gesture, function]` pairs,
with no `FF` header.

Gestures: 1 tap L, 2 tap R, 3 double L, 4 double R, 5 triple L, 6 triple R, 9 hold L,
10 hold R.

Functions: 0 none, 1 play/pause, 2 previous, 3 next, 4 voice assistant, 5 volume up,
6 volume down, 7 game mode, 11 ANC cycle.

Both number sets differ from the common protocol's (section 3.2).

### 10.5 EQ

One write to the EQ characteristic carries every band. No `FF` header. Little-endian.

| Bytes        | Field                                   |
|--------------|-----------------------------------------|
| 0            | `00`                                    |
| 1-2          | master gain in dB x 100, signed         |
| then per band | frequency in Hz (2), gain in dB x 100 signed (2), Q x 100 (2) |

Example, one band, 31 Hz, +3 dB, Q 1.8, master gain 0: `00 00 00 1F 00 2C 01 B4 00`

The official app truncates the master gain to a whole number of dB before scaling.
The user's points are merged onto the classic curve's bands first, as on Qualcomm
models (section 4).

### 10.6 What the app does not do on this model

ANC levels, transparency levels, dynamic EQ, dynamic bass, movie mode, spatial audio,
Dolby, prompt volume, in-ear detection, light effect, find earbud, auto shutdown,
LE Audio and the fit test are all empty in the app's Capsule3 Pro code.

The Capsule3 Pro+ is a different product. It is not in the APK and its protocol is
unknown.
