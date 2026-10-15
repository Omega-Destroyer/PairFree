# JBL protocol (Harman commands)

How PairFree talks to JBL headphones and earbuds. The commands come from JBL's own app, JBL
Headphones (`jbl.stc.com`) 5.37.10, read on 2026-10-15 outside the repo; none of its code is in
it. Class names below say where in that app to look. Two open-source projects checked it on
real devices:

- [JblTray](https://github.com/emperorbug46/JblTray) by emperorbug46 (MIT, commit `5ccf929`)
  controls a Tune Flex 2 from Windows. Its notes and code give the noise control frames it
  recorded from the earbuds over Bluetooth LE, the battery read, and that the earbuds' LE side
  answers on their classic address.
- [JBL-Headphones-App](https://github.com/FajkPes/JBL-Headphones-App) by FajkPes (commit
  `3f383d1`, no licence: its recorded bytes are used as facts, none of its code) reads and
  writes a Tune 720BT over Web Bluetooth. Its notes give that headphone's replies to the
  status, battery and voice prompt reads.

Nothing here has been tried with PairFree yet; a device report from any model helps
([device page](../devices/jbl.md)).

The data is in `data/jbl/`: `commands.json` (the commands PairFree uses, their values and what
is never sent), `devices.json` (the models PairFree knows, with how each is reached and what
JBL's app shows for it) and `test_vectors.json` (frames JblTray and FajkPes recorded, and
frames built the way JBL's app builds them).

## 1. Models and control families

JBL's app keeps a product list (`assets/product_list.json`) with each model's name, product
ids (pid), chip platform, connection (`BLE`, `BT` or `BR_EDR`) and the control class that
drives it, and a dashboard description per model (`assets/config/dash4/`) naming the cards it
shows. The control classes fall into families (`com.harman.cmdctrl.imp.control.u3`):

| Family | What it speaks | PairFree |
|--------|----------------|----------|
| `bes` (31 classes) | Harman commands (section 2) | Yes, the classes with a dash4 config |
| `protocol3` (7) | Harman commands, settings as feature records | Yes |
| `protocol4` (22) | JBL's newer protocol (section 6), mostly behind a BLE security mode | Yes, the models with no security mode |
| `airoha`, `lea`, `hid` | Airoha's SDK, LE Audio, USB | No |

The older `com.harman.cmdctrl.imp.bes` classes (Live 220BT to Tour Pro+) have no dash4 config,
so what each model has is not in the app; they are left out until reports say. `devices.json`
lists the 61 models PairFree covers; section 5 says how each model's class sends its settings.

## 2. Frames

```
AA  command  length  payload
```

- **AA** starts every frame (`CmdBase.HEADER_COMMAND`, 170). There is no checksum.
- **length** is one byte, the payload's, except for command `06` (feature records, section
  4.6), whose length is two bytes, low byte first (`Command3`). The custom EQ write (`41`) adds
  a packet count after a one-byte length; PairFree does not send it.
- A reply comes back under its own number for some commands (`11` → `12`, `21` → `22`) and
  under the same number for the rest (`25`, `91`, `06`).
- A write is acknowledged with `AA 00 len <command> <status>` (`HandleParse`, `RET_DEV_ACK`).

JBL's app sends one command at a time and waits up to 1 s for anything from the earbuds before
the next (`HandleSend`, a wait of 1000 ms that any parsed reply ends). PairFree does the same.
Over Bluetooth LE each notification is one frame (`BleDevice.doCommands`).

## 3. Transport and finding the earbuds

**Bluetooth LE** (connection `BLE`, most models): GATT service
`65786365-6c70-6f69-6e74-2e636f6d0000` ("excelpoint.com" in ASCII). JBL's app asks for a
512-byte MTU, subscribes to notifications on `…0001` (descriptor `2902`) and writes each frame
with a response to `…0002` (`Constants`, `GenServiceDiscovery`). JblTray found the Tune Flex 2's
commands go over LE unencrypted, with no handshake needed.

**Serial** (connection `BT`: the Quantum 360, 360X and 360P): the standard SPP UUID
`00001101-0000-1000-8000-00805F9B34FB`, the same frames.

**Finding the LE side.** The earbuds are paired for sound over classic Bluetooth. JBL's app
finds their LE side by scanning (`qi.n`), with two filters: manufacturer data under Harman's
company id 3787 (`0x0ECB`), or service data on `FDDF`. Its "classic" advert holds, little-endian:

| Bytes | What |
|-------|------|
| 0–1 | product id |
| 2 | colour |
| 3–4, 5–6 | CRC of the Bluetooth name of each phone the earbuds are connected to |
| 7–8 | a CRC JBL's app stores as the device's address CRC (`btAddressCrc`) |
| 9 | a flag |

The CRC is CRC-16/ARC (`com.harman.utils.c`), of the name's first 31 bytes, or of its first 50.
JBL's app takes a classic advert whose product id it knows and that carries this phone's name
CRC, so it connects to earbuds already connected to the phone. Its newer "p5" advert (protocol 4
models) holds the CRC of the classic address, which the app compares with the paired device's:
after the product id comes a two-byte word whose bits say which fields follow (bit 15 colour, 14
and 13 the phones' name CRCs, 12 the address CRC, 11 the address and more), then those fields in
that order (`qi.b`, BroadcastParse). PairFree reads both layouts.

PairFree first connects over LE to the classic address itself, since JblTray found the Tune
Flex 2 answers there, and needs no scan for that. Only if that fails within 10 s does it scan,
for at most 10 s, and take the advert with the model's product id that comes from the classic
address, carries the CRC of the classic address in bytes 7–8, or carries this phone's name
CRC; with only name CRCs it takes a device only when exactly one matched. Scanning needs
Android's BLUETOOTH_SCAN permission: PairFree declares it never used for location, asks for it
only when the user opens a JBL model, and keeps nothing it saw.

## 4. Commands

### 4.1 Device info: `11` → `12`

`AA 11 00`. The reply's payload: name (16 bytes), product id (2, low first), colour, battery
(bit 7 charging, bits 0–6 percent), the device's address (6 bytes, reversed), firmware (3
bytes, shown `a.b.c`), then voltages, the TWS state and a country code (`HandleParse.parseDeviceInfo`).
PairFree reads the product id and firmware; the device report blanks the name and address.

### 4.2 Status: `21` → `22`

`AA 21 01 <type>`; the reply starts with the type. Types (`EnumDeviceStatusType`): `30` all,
`31` noise cancelling, `32` ambient mode, `33` auto power-off, `34` EQ preset, `35` voice
assistant, `38` auto play/pause, `39` TWS, `41` in-ear test, `42` seal test.

The whole status (`30`, `parseDeviceAllStatus`), after the type: noise cancelling (00 off, 01
or 02 on), ambient mode (00 off, 01 TalkThru, 02 Ambient Aware), auto power-off in one byte
(bit 7 on, bits 0–6 minutes; FF absent), EQ preset, voice assistant, one byte, update state,
auto play/pause (00 off), TWS, then auto power-off in three bytes (on, minutes low first; FFFF
absent). FajkPes recorded it from a Tune 720BT:

```
AA 22 0D 30 00 00 9E E6 00 01 00 00 00 01 1E 00
```

noise cancelling off, ambient off, auto power-off on after 30 minutes (`9E`, and `01 1E 00`),
auto play/pause off. FajkPes found the 720BT ignores reads of single types, so PairFree reads
`30` on these models.

### 4.3 Battery: `25`

`AA 25 01 00`. The reply's payload: `01`, then whether the secondary and the primary unit are
charging, their levels, whether the case is charging and its level, then voltages. Over 100 is
unknown. JBL's app shows the secondary as the left earbud and the primary as the right one;
on headphones PairFree shows the primary. FajkPes's Tune 720BT:
`AA 25 0D 01 00 00 50 50 FF FF …` (both 80 %, no case).

### 4.4 Noise control: `91`

The first payload byte is a sub-command (`SubId`): `10` set, `11` read, `12` reply, `13` all
off; then pairs of mode and value, modes `01` noise cancelling, `02` Ambient Aware, `03`
TalkThru (`CmdGen.genSetANCModes`, `HandleParse` case 23). JblTray recorded, on the Tune Flex 2:

| | |
|---|---|
| read | `AA 91 01 11` |
| noise cancelling | `AA 91 07 10 01 01 02 00 03 00` |
| Ambient Aware | `AA 91 07 10 01 00 02 01 03 00` |
| TalkThru | `AA 91 07 10 01 00 02 00 03 01` |
| off | `AA 91 01 13` |

The reply (`12`, and JblTray saw `10` too) has the same pairs; the mode whose value is not 0 is
on, none means off. Sub-commands `20` to `22` set and read the noise cancelling level and
sub-modes `00` to `02` other modes; PairFree does not use them yet.

### 4.5 The older noise commands: `31`, `32`

`AA 31 01 <00 off, 01 on>` for noise cancelling and `AA 32 01 <00 off, 01 TalkThru, 02 Ambient
Aware>` for ambient sound; read with status types `31` and `32`, or the whole status.

### 4.6 Feature records: `06`

```
AA 06 <length, 2 bytes, low first> <type> <records>
```

Type `00` set, `01` read, `02` reply (`Command3`). A read lists feature ids, two bytes each,
high byte first; a set or reply gives per feature its id, a length and the value. JblTray
recorded `AA 06 03 00 01 00 0C` (read spatial sound) on the Tune Flex 2. The ids PairFree uses
(`DeviceInfoFeature`): `02` product id (low first), `05` firmware (3 bytes), `0B` auto
power-off (on, minutes low first), `11` auto play/pause (1 on), `19` headband battery, `1A`
and `1B` left and right earbud, `1C` case (each: charging, level).

### 4.7 Auto power-off and auto play/pause: `33`, `35`

`AA 33 01 <bit 7 on, bits 0–6 minutes>` (v1) or `AA 33 03 <on> <minutes, 2 bytes low first>`
(v2, `generateSetAutoOffCmdV2`); `AA 35 01 <00 off, 01 on>`.

## 5. How each model sends its settings

`devices.json` gives each model's:

- **family:** `direct` models (the `bes` classes on `BesDeviceStatusControl`) read the device
  info (`11`) and the whole status (`30`) and write each setting with its own command;
  `feature` models (`protocol3`, and the Junior 320BT and 470NC, Live Beam 3, Live Flex 3 and
  Quantum 360 classes) read and write auto power-off and auto play/pause, and read the product
  id, firmware and battery, as feature records. All read the battery with `25` too, as JblTray
  does on the Tune Flex 2. `p4` models speak protocol 4 (section 6).
- **noise:** `modes` (command `91`, every class whose `setAncSetting` uses `VERSION_NEW`),
  `old` (`31` and `32`: Live 460NC and 660NC, Reflect Flow Pro, Endurance Race; off sends both),
  `club` (the Club One: off sends `31` alone, `VERSION_OLD_CLUB_950_ONE`) or `asc` (protocol 4,
  section 6).
- **modes:** which of noise cancelling, Ambient Aware and TalkThru the model's noise card in
  JBL's dashboard offers: `AscCard` and its variants all three, `AscCardNoAnc` the two ambient
  ones, `AscCardNoAaTt`, `AscOnlyANCCard`, `AncCard` and `AncClubOneCard` noise cancelling
  alone; protocol 4's `AscStatusCard` and its two variants all three. A model with no noise card (the Tune 520BT, for one) gets no noise control, even
  though its config lists the three modes.
- **auto_off:** `v1`, `v2` or `feature`, by the class's `setAutoPowerOffStatus`, where the
  dashboard has `AutoPowerOffCard` (never, 30 or 60 minutes) or `AutoOffCard` (on or off; JBL's
  app writes 10 minutes).
- **auto_play_pause:** where the dashboard has `AutoPlayPauseCard` and the class sends it.

PairFree shows noise cancelling and Ambient Aware on the shared noise control (as its off,
noise cancelling and ambient sound) when a model has both, and TalkThru as a switch beside it;
models with other sets get a switch per mode. After a change it reads the setting again 3 s
later, so the screen shows what the earbuds hold.

## 6. Protocol 4

JBL's newest models speak another frame (`com.harman.protocol4`: `AssembleCmd4`,
`DeviceInfoList4Cmd`, `CommonParse`), over the same GATT service and characteristics when the
product list gives the model no BLE security mode, as for the 13 models in `devices.json`
(Tune Buds, Beam and Flex 3; Vibe and Wave Buds, Beam and Flex 3; Sense Lite; Endurance Zone;
Tune 135BT and 235BT). The others (Tour Pro 4, Live 4, Tune 680NC and more) first authenticate
over a separate characteristic (`bleSecurityMode` 1 to 3); PairFree leaves them out.

```
identifier (2)  command (2)  packet count (1)  packet index (1)  length (2)  payload
```

Two-byte fields are low byte first. The identifier is `DD00`, or `DD01` for what concerns the
charging case; commands are `0001` read, `0002` write, `0003` a notification from the earbuds.
A read's payload lists feature ids (two bytes each); a write's gives per feature its id, the
value's length (two bytes each) and the value. An answer's or notification's first packet
starts with a status code (two bytes, 0 when all is well) and then the same records as a write.
JBL's app splits payloads over 497 bytes into several packets and joins answers by index.

Features PairFree uses (`DeviceInfoFeature5`): `4` product id (two bytes), `12` firmware (its
bytes, shown joined by dots), `13` and `14` the left and right earbud's battery, `7939` the
case's, `24` noise cancelling, `25` Ambient Aware, `26` TalkThru, `73` ambient sound control
and `47` host type. A battery or mode is one byte: bit 7 the status (charging, or the mode
selected), bits 0 to 6 the level; a level of 127 leaves it as it is.

On connecting, JBL's app writes host type 1 and then reads. On the models with noise control
(`TuneFlex3Control`, `WaveFlex3Control`, `TuneBeam3Control`), it sets a mode by writing ambient
sound control on together with the mode's feature, status 1 and the mode's level, and turns
noise control off by writing ambient sound control off with the current mode. It reads the
mode back as the one of noise cancelling, Ambient Aware and TalkThru whose status is 1, and off
when ambient sound control is off (`gn.l0`). PairFree does the same, with the level each mode
last reported, or 127 before it has reported one.

## 7. Never sent

`commands.json`'s `never_send`: `95` factory reset, `97` power off, `F0` firmware update, `AA`
preserve settings, `13` and `15` analytics. In protocol 4: `00FF` factory reset, `0101` to
`0104` firmware update, `0201` and `02FF` analytics. JBL's firmware updates go over separate
services PairFree never opens.

## 8. Open questions

- Whether bytes 7–8 of the classic advert are the CRC of the classic address, as the name JBL's
  app gives them says; it does not compare them for these models.
- Which models' LE side answers on the classic address, as the Tune Flex 2's does.
- Whether the feature-family headphones (Junior, Quantum 360) answer the battery read `25`.
- Protocol 4 has no recorded frame yet: everything in section 6 is from JBL's app alone.
- What the acknowledgement's status values mean; PairFree reads a setting back rather than
  trusting them.
- The equalizer: JBL's app writes a whole curve of filters as floating-point numbers with a
  calibration (`41`, `EQControl`), and its presets are curves the app holds (`assets/eq`).
  Nothing of it has been checked on a device, so PairFree does not send it.
