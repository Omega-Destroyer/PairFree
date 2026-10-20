# SoundPEATS control protocol (from PeatsAudio 1.9.45 for Android)

Source: static analysis of `com.xingkeqi.peats` 1.9.45. The app is not obfuscated, so
class and field names below are the app's own. Each section says what is read directly
from code and what is inferred. Section 11 lists what has been confirmed on real earbuds;
everything else is still untested.

A second source, read later: SOUNDPEATS 2.5.3-global (`com.thirtydays.headset`), PeatsAudio's
successor from the same company; its code is `com.xingkeqi.peats.v2`. Of the 87 commands it
names, 72 match section 3 exactly, in number and meaning. Facts taken from it say "2.5.3".

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
exactly one code path for those. Since confirmed: the H3 answers on this channel and
its firmware code is `S5` (section 11).

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

The H3 sends its frames with version `03`, while the phone (PeatsAudio and PairFree
alike) sends `04` (section 11). A reader should not insist on `04`.

- GET: command `03 xx`, empty payload.
- Reply to a GET: command `83 xx` (same number with the top bit set). Payload is usually
  `[status, value]`. Status `00` is success. The app ignores battery replies whose
  status is `01` or `02`.
- SET: command `03 yy` with the new value as payload. The GET and SET numbers are
  different; use the table.
- On the H3, a SET gets no reply of its own. About 2 s later the earbuds send the
  matching GET reply with the new value, unasked (seen for ANC mode, section 11).
  They do the same when the setting is changed by touch on the earbuds.
  Treat every GET reply as a state update, whenever it arrives.
- On the H3, the earbuds stop answering for 6 to 15 s after a touch control, then
  answer everything asked in the meantime at once (section 11).
- On the H3, a GET the firmware does not support gets no reply at all, not an error
  status.
- The app's reader treats each socket read as one frame. A new client should
  reassemble by the length byte instead.

Example, read firmware version: `FF 04 00 00 00 0A 03 09`
Example, transparency on: `FF 04 00 01 00 0A 03 11 02`

## 3. Commands (vendor `00 0A`)

All read from `FunctionEnum`, `FunctionStatusEnum` and `DefaultSppDeviceModel`, except
the rows marked 2.5.3, which come from SOUNDPEATS 2.5.3's `FeatureConstant`.
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
| Fit test                 |         | `03 63` | non-Qualcomm; start/stop, result as `83 63`, payloads not known. Qualcomm: section 11 |
| Reset state              | `03 67` | `03 68` | reply last two bytes == 1 means "was just factory reset"   |
| Connect classic          | `03 17` |         | Defined in the app with reply `83 17`, never sent by it. May trigger an action. Do not probe. |
| Earbud colour (2.5.3)    | `03 26` |         | one byte: the colour of this pair                          |
| LE Audio broadcast (2.5.3) | `03 37` | `03 36` | named, never sent; values not known                      |
| Hearing protection (2.5.3) | `03 4E` | `03 4F` | volume limit, 50 to 100                                  |
| Volume balance (2.5.3)   | `03 75` | `03 76` | 0 to 100                                                   |
| Phone codec (2.5.3)      | `03 77` |         | codec in use: 0 other, 4 LDAC, 9 Opus; see section 12      |
| Known phones (2.5.3)     | `03 52` |         | see below. Private: do not probe.                          |
| Connect a phone (2.5.3)  |         | `03 53` | its address (6 bytes), then 1 connect or 0 disconnect      |
| Send device address (2.5.3) |      | `03 51` | named, never sent                                          |
| Auracast (2.5.3)         |         | `03 78` | see below                                                  |
| Reboot                   |         | `03 16` | no payload                                                 |
| Factory reset            |         | `03 05` | no payload. Do not send casually.                          |

From SOUNDPEATS 2.5.3:

- Known phones (`03 52`): the phones the earbuds have connected to. The reply is an entry
  number, 1 if that phone is connected now, its Bluetooth address (6 bytes) and its name,
  ending in a 0 byte. On `S13` a 41-byte reply has one extra leading byte. It holds other
  people's device names and addresses, so PairFree never reads it unasked.
- Auracast (`03 78`): the earbuds answer with `83 78`. The payload starts with a
  sub-command (1 scan, 2 join a source, 4 listening control, 5 listening state) and carries
  fields tagged by type (1 name, 2 broadcast id, 3 features, 4 address, 5 listening state,
  6 broadcast code). The exact layout was not traced.
- `03 33` is named `FIND_EARPHONES_DEPRECATED` and used nowhere; whether it is a read or a
  write is not known. Finding the earbuds uses `03 3A`/`03 3B`.

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

What the app's code says about these levels:

- On firmware codes `S15`, `S21` and `S22` the app shows level `11` under the
  "adaptive" label. On every other model it shows it as "normal mode".
- The app shows the environment-adaptive switch only when the firmware code is `S22`.
- On `S22` firmware 0.4.6 the app sends outdoor (`13`) when the user picks indoor (`12`).

Which products `S15` and `S22` are is not in the APK.

On hardware the H3 (`S5`) reports `11`, `12`, `13` and `14`, with `11` active, and no
`15`. It does not answer the environment-adaptive GET (`03 64`). Its marketed modes are
Indoor, Outdoor, Traffic and Adaptive, so its Adaptive is most likely `11`. By the rule
above the app would label `11` "normal mode" on the H3, since `S5` is not one of the three
codes; what PeatsAudio shows while `11` is active is still to check. Picking scenes from
PairFree (SET `03 25`) changes them on the H3, by ear (section 11).

### 3.2 Custom keys

SET payload: `[gesture, action]`, one gesture per frame.
GET reply: status byte, then `(gesture, action)` pairs.

Gestures: 1 tap L, 2 tap R, 3 double L, 4 double R, 5 triple L, 6 triple R,
7 hold L (1.5 s), 8 hold R.

Actions: 0 none, 1 volume up, 2 volume down, 3 play/pause, 4 game mode,
5 previous, 6 next, 7 ANC cycle, 8 voice assistant.

## 4. EQ (vendor `00 1D`)

Write-only. The app never reads the EQ back from the earbuds. On the H3 each band write
is acknowledged with `0F 81 01`, which carries no state (section 11). The H3's own
Qualcomm EQ reads answer, but they do not follow these writes, so nothing found so far
can report this EQ (section 11, "GAIA v3 on vendor `00 1D`").

One frame per band. The command is `0E 01` or `0E 02`. With `0E 02` the first
payload byte becomes two bytes: count, index.

The app picks `0E 01` when any of these holds, and `0E 02` otherwise
(`BluetoothProductFirmwareEnum.useOldEqPact`):

- the family is `QUALCOMM`. This holds for every Qualcomm model, including the ones
  whose compiled-in flag is false (Air5, Air6 Pro);
- the model's compiled-in "old EQ" flag is true. Outside the Qualcomm family that
  is `WQT7033`, `T57` and `ATS3019DK12`;
- the code is `BS519W2D` and the firmware version is 0.4.5 or lower.

`data/soundpeats/devices.json` carries the result per model as `eq_command`.

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
capture (section 11) and are in `data/soundpeats/devices.json` as the model's `eq` entry.

The hearing-test EQ is computed in the app and sent through the same band writes.

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
`data/soundpeats/devices.json`, keyed by the firmware code from the `03 09` reply.

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
| `SANNUO`    | not known         | listed in 2.5.3 only           | no models known                                |
| `QCY-WUQI`  | QCY-built (WUQI)  | QCY's own SDK over BLE         | separate protocol, see section 10; Capsule3 Pro only |

`data/soundpeats/devices.json` holds the model table compiled into the app: 45 firmware codes with
their Bluetooth names and family, plus the transport, EQ command and EQ merge rule
that follow from the app's code. Newer models are missing from it because the app
downloads their entries. Their codes look like `S7`, `S21`, `S27`.

SOUNDPEATS 2.5.3 lists the families `FOTA`, `ACTIONS`, `JIELI`, `QUALCOMM`, `WUQI`, `QCY-WUQI`,
`BES`, `SANNUO` and `UNKNOWN`; it has no `SMARTLINK` or `AIROHA`.

Identify a model by the first field of the firmware-version reply (`03 09`).

### 5.1 What differs from model to model

Everything on the common protocol differs in these ways only:

| What                         | Decided by                    | Where it is recorded                      |
|------------------------------|-------------------------------|-------------------------------------------|
| Which features exist         | Server function list          | Probe the earbuds. See section 8.         |
| Which values a feature takes | Server function list, and for ANC levels the earbuds' own reply | Probe          |
| EQ command (`0E 01`/`0E 02`) | Family and firmware code      | `eq_command` in `data/soundpeats/devices.json`       |
| EQ merge rule                | Family and firmware code      | `eq_merge` in `data/soundpeats/devices.json`         |
| Custom EQ band count         | Family (8 on Bluetrum, else 10) | `custom_eq_bands` in `data/soundpeats/devices.json` |
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
| `S8`  | Q40 HD (version 1) | constant name, 2.5.3   | unknown                                    |
| `S14` | Air6 HS            | constant name, 2.5.3   | unknown                                    |
| `S23` | Q40 HD (version 2) | constant name, 2.5.3   | unknown                                    |
| `S25` | "CC3 Pro"          | constant name, 2.5.3   | unknown                                    |
| `S26` | Capsule5 Pro       | constant name, 2.5.3   | unknown                                    |
| `S31` | "CC3 Pro AI"       | constant name, 2.5.3   | unknown                                    |
| `S33` | PopClip2 AI        | constant name, 2.5.3   | unknown                                    |
| `S34` | Jewel Clip         | constant name, 2.5.3   | unknown                                    |
| `S36` | Air6 Lite          | constant name, 2.5.3   | unknown                                    |

2.5.3 names `S27` Air6 Pro and `S32` Air6 Pro AI. Its demo data (section 8.1) adds model
list entries for codes that are not in the APK's table:

| Code               | Bluetooth name             | Family     |
|--------------------|----------------------------|------------|
| `AIR3DELUXEHSW53D` | SOUNDPEATS Air3 Deluxe HS  | `WUQI`     |
| `WUQIRFLite2`      | SOUNDPEATS RunFree Lite2   | `WUQI`     |
| `Q35HD`            | SOUNDPEATS Q35 HD+         | `WUQI`     |
| `7034MXGoFree2S`   | SOUNDPEATS GoFree 2        | `ACTIONS`  |
| `SPTS04PRO`        | SOUNDPEATS Capsule3 Pro+   | `QCY-WUQI` |

Every code it shares with the APK's table has the same family there.

"Probably not Qualcomm" is an inference: the app names `S15` and `S22` in a list that
sits next to a separate check for the Qualcomm family, which would be redundant if
they were Qualcomm.

The H3 and the Air5 Pro+ appear nowhere in the APK. Public reviews name the Qualcomm
QCC3091 for the Air5 Pro, Air5 Pro+ and Air6 Pro. `data/soundpeats/devices.json` lists these
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
The H3 does, and GETs work over it as described above (section 11).

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
release notes, a size and one or more download URLs. The request body of this call
is AES-encrypted with a key compiled into the app, and the reply may be too.

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

How these are served, as read from the app:

- All four are GET requests with no body. The app adds headers `os`, `appVersion`,
  `model`, `manufacturer`, `sdkVersion`, `appVersionCode`, `userId` (empty when not
  signed in) and `lang`.
- `ota/pairDataList` and `eq/preset/list` are parsed as plain JSON. The model list
  entries are `bluetoothName`, `otaCode` and `schemeCode` (the chip family).
- `ota/functionList` comes back as text. The app decrypts it first if it is
  AES-encrypted and reads it as plain JSON otherwise, so either may arrive. Its data
  has `schemeCode`, `languageList` and `funcList`. Each function has `code`
  (`FSN..`), `commandList` (accepted values), `parentCode`, `status` and display fields.
- An EQ preset has `eqCode`, `name`, `masterGain`, and three JSON arrays as strings:
  `fjson` (frequencies), `gainJson` and `qjson`.
- `product/list` gives `productName` per `otaCode`.

`tools/fetch_server_data.py` asks for these at one request per 30 seconds. When the
owner ran it on 2026-10-10 the server answered both `ota/pairDataList` and
`product/list` with HTTP 200 carrying `{"code": 401, "success": false}` and a message
saying authentication failed: the server only answers the official app's
authenticated requests. Nothing was fetched, and the tool now stops at the first such
refusal. Per-model facts come from the earbuds instead: the probe for features, a
phone's Bluetooth log for EQ curves (section 11).

The APK ships no per-model feature list and no EQ curves. Its assets hold only
animations, hearing-test tones and SDK files.

### 8.1 SOUNDPEATS 2.5.3

PeatsAudio's successor gets the same per-model data from a new server,
`https://global.isoundpeats.com/api/app/v1/`. Its release build reads everything from there.
The JSON files in its assets (`products.json`, `support_device.json`, `features.json`,
`preset_eq.json` and others) feed a demo build only, and they are out of date: the H3 sample
gives firmware 0.1.8 and EQ `0E 02` at 40 Hz to 12 kHz, while the H3 uses `0E 01` at 50 Hz to
17 kHz (section 11). So neither app holds per-model features or EQ curves offline. The demo
model list is the one used in section 5.2.

## 9. Model-specific behaviour found in code

- Right-battery smoothing: if right reads 5 or more above left and is under 99, the
  app shows it 3 lower. Cosmetic.
- `S21` (Cove Pro): "dynamic bass" is done by sending an EQ curve, not the `03 3D` switch.
- Feature code `FSN34` ("focus mode"): reuses the ANC command `03 10`/`03 11` as a
  plain on/off switch.
- Fit test: Qualcomm models go through Qualcomm's own GAIA earbud-fit feature, with
  the app switching ANC itself during the test on every model except `S27` and `S32`
  (Air6 Pro; 2.5.3 keeps the same exception). Other families use `03 63`. The GAIA frames, as captured on the H3, are in
  section 11, with what it does to ANC.
- `ZKLXAIR5LITE` (Air5 Lite) and `ZKLXCHASERS` (Chasers): movie mode is only
  available while multipoint is on.
- `S7` (Clip1) on firmware 0.0.15: the app forces an update prompt.
- `S7` (Clip1): 60 ms after reading the device state the app sends `03 62` with one
  byte saying whether the phone supports Dolby (0 yes, 1 no).
- `BS519W2D` (Air3 Deluxe HS) and `AIR3DELUXEHSW53D` (another Air3 Deluxe HS build):
  the app treats the touch-disable switch as inverted. Whether the inversion applies to
  the reply, the write or both was not traced.
- 2.5.3 shows the touch-disable switch inverted on any firmware code that contains, ignoring
  case, `BT5107`, `BS519W2D`, `Air3Pro`, `BS390RB`, `WQT7033`, `QCC3040`, `BS343RB`,
  `BT5106-3040`, `T28Pro`, `T28`, `T57`, `ATS3019DK12`, `OPERA03`, `OPERA05`, `HILISTENING`,
  `FREELITE` or `JULIRUNFREE`. `AIR3DELUXEHSW53D` is no longer in the list. It inverts the
  state it shows; how it writes the switch was not traced. `touch_disable_inverted` in
  `data/soundpeats/devices.json` marks these models; PairFree does not act on it yet.
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
`data/soundpeats/qcy_commands.json` holds the same information in machine-readable form.

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

## 11. Confirmed on hardware

Raw output for each model is in `data/soundpeats/reports/`.

### SoundPEATS H3: firmware code `S5`, version 0.4.9

Tested 2026-10-10 from Windows over the SPP COM port. Report: `data/soundpeats/reports/S5_0.4.9.txt`.

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
  and LDAC was on. Recorded as `ldac_excludes` for `S5` in `data/soundpeats/devices.json`. Whether
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

Phone session, 2026-10-10, from PairFree's frame log and the phone's Bluetooth HCI
snoop log (raw frames at the end of the report). PairFree and then PeatsAudio did the
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
  are one either-or setting (`ldac_or_multipoint` in `data/soundpeats/devices.json`). PeatsAudio sends
  only the one SET and sees the same.
- Right after the channel reopened following LDAC off, an unasked `83 17 00 01` arrived.
  `83 17` is the reply to "connect classic" (`03 17`, section 3), which the app defines
  but never sends; here the earbuds sent it without being asked. What `00 01` means is
  not known. PairFree only listens for it and never sends `03 17`.
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
  `0F 81` with payload `01`, for PeatsAudio and PairFree alike. The same value every
  time; what it means beyond "received" is not known.
- PeatsAudio sends its saved curve about 2.5 s after it connects, so it overwrites an
  EQ another app set.
- Presets, from a second capture: picking a preset sends a complete curve of 10 bands
  at the same -2 dB master gain. SOUNDPEATS Classic is the base curve. Bass Boost, Bass
  Reduce, Treble Boost, Pop and Rock keep the base curve's frequencies and Q but not its
  gains (and not in whole-dB steps from it). Electronic, Classical, Folk, Book Whisper,
  HIFI and ACG also move some bands (for example 20 Hz, 5 kHz, or 6.8, 9.8 and 14.5 kHz)
  or change their Q. All eleven are in `data/soundpeats/devices.json` (`eq.presets`) and are the
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

### SoundPEATS H3: GAIA v3 on vendor `00 1D`

Probed 2026-10-11 from a PC over the serial port, looking for a way to read the EQ.
Every frame was approved by the owner before it went out. Report:
`data/soundpeats/reports/S5_gaia_v3.txt`.

- Every reply on vendor `00 1D` carries the request's command number plus `01 00`. The
  EQ write `0E 01` is answered by `0F 81`, its number plus `01 80`. That fits Qualcomm's
  GAIA v3, where a command number is a 7-bit feature, a 2-bit packet type (command,
  notification, response, error) and a 7-bit command. If so, `0F 81 01` is an error
  reply to feature 7, and the H3 applies the EQ anyway. GAIA v3's specification is not
  public, so the names below are interpretation; the bytes are in the report.
- `00 00` answered `03 01` (API version 3.1). `00 01` answered a list of nine
  (feature, version) pairs: `00`/5, `01`/3, `05`/1, `06`/2, `07`/1, `08`/7, `09`/1,
  `0C`/1, `0D`/1.
- Feature `05` (Qualcomm's EQ): `0A 00` answered `00`; `0A 01` listed 8 sets (`00 01 02
  03 06 08 09 3F`); `0A 02` answered `00`; `0A 04` answered 5 bands; `0A 05 00 04`
  returned five bands of 7 bytes, every gain 0.
- After PeatsAudio's Reset curve and then its Bass Boost were sent as `0E 01` writes,
  `0A 00`, `0A 02` and `0A 05` answered exactly as before. SoundPEATS' EQ does not go
  through Qualcomm's EQ feature, so it cannot be read there. PairFree puts its own EQ
  back on every connect instead, as PeatsAudio does.
- Not tried: anything else on features `01`, `07`, `08`, `0C` and `0D`. Feature
  `06` is the firmware update feature on Qualcomm devices and must not be probed.

### SoundPEATS H3: fit test (GAIA v3 feature `09`)

Captured 2026-10-11 from the phone's Bluetooth log while the owner ran PeatsAudio's fit
test twice on the H3, ANC off. Report: `data/soundpeats/reports/S5_S2_fit_drain_leaudio.txt`; the
frames are the `gaia` cases in `data/soundpeats/test_vectors.json`.

- On opening the fit test, PeatsAudio sets up GAIA: it asks for the feature list (`00 01`),
  sets two transport parameters (`00 0D`), registers for notifications from features `00`,
  `09`, `05`, `08`, `01`, `06` and `07` (`00 07` with the feature id; each answered `01 07`),
  and reads three transport values (`00 0C`). The earbuds then send their current state
  on several features unasked. Before setting protocol version 4 (`00 0D 07 00 00 00 04`)
  PeatsAudio sends these frames with version byte `03`; afterwards `04`.
- Start: `12 00 01` (feature `09`, command 0, payload `01`), answered at once by `13 00`.
  There is no stop: the test ends by itself.
- Result: about 9 s later, the notification `12 80` with two bytes, sent twice about 0.2 s
  apart with the same payload. Both earbuds in the ears gave `01 01`, which PeatsAudio
  showed as a good fit; both on a desk gave `02 02`, shown as "Not fit". So `01` is a good
  fit and `02` is not, one byte per earbud. The first byte is the left earbud: on
  2026-10-12, with only the left earbud in the ear, PairFree's run got `01 02`.
- PairFree reads `00 01` on connect for models in the QUALCOMM family, offers the fit
  test when feature `09` is listed, registers for `09` and sends the start. It sends
  version `04` throughout and skips the transport parameters; the H3 answers GAIA reads
  that way.
- The Air5 Pro+ lists feature `09` too. Its feature list is `0D`, `09`, `08`, `01`, `06`,
  `07` and `00`: no `05` (Qualcomm's EQ) and no `0C`. The same start gets the same
  `13 00` and `12 80` result about 9 s later.
- With ANC on (captured 2026-10-12), PeatsAudio sends `03 11 00` (ANC off) and starts the
  GAIA setup 1.7 s later, then runs the test as above. It does not turn ANC back on: 13 s
  after the result, its reads still found ANC off. The pause matters: PairFree once
  started the test 0.3 s after the H3 confirmed ANC off (2 s after the SET), and the H3
  answered `12 80 02 02` (poor fit on both) 1.6 s later instead of after about 9 s.
  PairFree now waits for the confirmation (about 1.7 s) and then 1.5 s more, and puts
  the noise mode back once the result is in. Per section 9 the
  app leaves ANC alone on `S27` and `S32` (Air6 Pro): `fit_keeps_anc` in
  `data/soundpeats/devices.json`.

### SoundPEATS Air5 Pro+: firmware code `S2`, version 1.2

Captured 2026-10-11 from the phone's Bluetooth log while the owner used PeatsAudio (same
method as the H3's EQ). Report: `data/soundpeats/reports/S2_1.2.txt`.

- Firmware version reply: `S2_20250816_V1.2`. The probe has not been run on it yet.
- On connecting, PeatsAudio read battery, firmware, multipoint, ANC level, touch disable,
  environment adaptive, transparency level, game mode, custom keys, ANC mode, LDAC and
  reset state. The replies match the H3's: scenes `11` to `14` with `11` active,
  transparency `01` and `02` with `02` active, LDAC on, and no reply to `03 64` or `03 67`.
- EQ, as PeatsAudio writes it: command `0E 01` on vendor `00 1D`, ten bands in every write,
  each acknowledged with `0F 81 01`. Master gain is -12.0 dB in every frame (the H3's is
  -2.0 dB). Base curve, sent by Reset:

  | Band | Hz    | Q   | Base gain dB |
  |------|-------|-----|--------------|
  | 1    | 32    | 1.0 | +2.5         |
  | 2    | 65    | 1.5 | +3.0         |
  | 3    | 180   | 1.2 | -6.0         |
  | 4    | 500   | 1.5 | +0.5         |
  | 5    | 800   | 1.5 | +1.0         |
  | 6    | 1800  | 1.2 | +8.0         |
  | 7    | 3600  | 3.0 | +3.5         |
  | 8    | 5500  | 2.0 | -5.0         |
  | 9    | 8000  | 3.5 | -3.0         |
  | 10   | 12000 | 1.5 | +8.0         |

  The user's sliders add -6 to +6 dB in 1 dB steps to these, as on the H3 (checked at both
  ends and by single steps on two bands).
- Presets: the same twelve names as on the H3, in the same grid, but with this model's own
  curves; every one differs from the H3's. SOUNDPEATS Classic is the base curve; Pop and HIFI
  move some bands. All are in `data/soundpeats/devices.json` (`S2`, `eq.presets`) and are the `S2`
  cases of `eq_layout` and `eq_preset` in `data/soundpeats/test_vectors.json`.
- LDAC works once the phone's own LDAC switch for these earbuds is on; see section 12.
- Drainage and LE Audio (captured 2026-10-11, same report as the H3's fit test).
  PeatsAudio offers both on the Air5 Pro+ and neither on the H3, with LDAC on or off.
  The H3 does not answer the drainage read `03 50`; it answers the LE Audio read `03 35`
  with `00`, but PeatsAudio still does not offer it there.
  - Drainage: the Air5 Pro+ answers `03 50` with `00`. `03 3E 01` starts it with no
    reply; the earbuds play a series of sounds, and about 30 s later report `83 50 00 00`
    unasked, and PeatsAudio's toggle turns itself off.
  - LE Audio: `03 34 01` turns it on. The earbuds restart and reconnect by themselves;
    PeatsAudio's control channel dropped and reopened about 12 s later over the same
    classic serial channel, and `03 35` then read `01`. LDAC still read on. `03 34 00`
    turns it off the same way.

## 12. Codecs (A2DP), and LDAC on Samsung phones

Audio codec selection (SBC, AAC, the aptX family, LDAC, LC3) is A2DP, negotiated between
the phone and the earbuds. It is not on the SPP control channel, and no app picks the
active codec; the phone does. The only codec-related controls on the control channel are
the `ldac` switch (`03 38`/`03 39`) and the LE Audio switch (`03 35`/`03 34`). Toggling
`ldac` changes which codecs the earbuds advertise over A2DP.

SOUNDPEATS 2.5.3 also reads `03 77`, in which the earbuds report the codec they are playing:
4 LDAC, 9 Opus, 0 other (the app accepts 0 to 9 and names only these). Not yet tried on the
H3 or the Air5 Pro+. On the Air5 Pro+ it would show whether the earbuds ever see LDAC.

Tested 2026-10-11 on a Samsung Galaxy S24 FE (SM-S721W, Android 16, One UI 8, build
`S721WVLSEDZH9`); both earbuds are Qualcomm QCC3091. The phone's negotiation was read from
`dumpsys audio`/`dumpsys bluetooth_manager` and `logcat` (`bluetooth-a2dp`, `A2dpService`).
Evidence: `data/soundpeats/reports/air5-pro-plus_ldac.txt`.

### H3 (works)

- With `ldac` on, the H3 advertises LDAC, aptX, AAC and SBC together.
- The phone auto-negotiates LDAC at 96 kHz / 32-bit on connection, and LDAC can also be
  force-selected in Android Developer options; it switches cleanly (a real A2DP
  reconfigure, heard as a brief pause).

### Air5 Pro+ (works once the phone's LDAC switch is on)

- With `ldac` on, the Air5 Pro+ advertises AAC, SBC and LDAC, and stops advertising aptX.
- On 2026-10-11 the phone did not use LDAC: it played AAC, and a manual force to LDAC was
  rejected. The log shows `A2dpService: enableOptionalCodecs ldac disabled` at connection and
  `A2dpService: setCodecConfigPreference ldac disabled` on a manual switch; the codec
  stayed AAC, with no reconfigure.
- The cause (owner, 2026-10-19): Samsung phones keep an LDAC switch per paired device, under
  Settings > Connections > Bluetooth > the device's gear > Codecs. It was off for the Air5 Pro+
  and on for the H3. With it on, the phone plays LDAC on the Air5 Pro+. The log's "ldac
  disabled" is that switch (Android's optional codecs setting for the device, which Pixel phones
  show as HD audio). It is not an earbud fault, as first suspected.
- Not known yet: why the switch was on for the H3 and off for the Air5 Pro+ on the same phone, and
  whether every LDAC earbud needs it on Samsung phones or only some. Reports from other phones and
  earbuds will tell.
- With the switch off, turning `ldac` on lowers quality on the Air5 Pro+: it removes aptX and
  the phone lands on AAC.

### What PairFree does

Android's public API does not let an app read the codec in use (`BluetoothA2dp.getCodecStatus` and
the codec-changed broadcast are system APIs), so PairFree cannot tell whether the phone followed
the earbuds. When the user turns LDAC on, it explains the phone's switch and offers a button to the
phone's Bluetooth settings, and while LDAC is on the Device tab says so under the switch.
