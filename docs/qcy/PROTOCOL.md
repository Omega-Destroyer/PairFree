# QCY protocol

How QCY earbuds and headphones are controlled, as PairFree does it. No QCY model has been tried with
PairFree yet.

## 1. Sources and protocols

- **QCY's app**: QCY 4.0.7 (`com.qcymall.googleearphonesetup`), decompiled outside the repo on
  2026-10-18. None of its code is in the repo. These parts were read:
  - `com.qcymall.qcylibrary`: `QCYHeadsetClient`, `DataBean` and its item beans. This is the same
    library PeatsAudio carries for the SOUNDPEATS Capsule3 Pro (`docs/soundpeats/PROTOCOL.md`
    section 10), and 4.0.7's copy is larger.
  - `Devicebind`: the advert, and how a model's chip is read.
  - `JLDeviceImpl`: the JieLi path, and the copy of JieLi's RCSP SDK (`com.jieli.jl_bt_rcsp`) it runs on.
- **QCY's product list and control panels**, which QCY's app downloads from QCY's server, as
  [Quicky](https://github.com/hui1601/Quicky) by hui1601 dumped them. Quicky has no licence, so it gives
  facts only. Its protocol notes, read from a QCY HT07's traffic, agree with QCY's app.
  - `data/qcy/devices.json` keeps the 171 entries Quicky files as earphones; watches, speakers and the
    one accessory are left out (the owner's choice, 2026-10-18).
  - Quicky left out the numbers QCY's control panels attach to touch actions and switch writes, and
    the EQ presets' values. So the touch actions are matched by name to Quicky's function table, and
    the switches use QCY's usual 01 on, 02 off (section 4).
- **[OpenQCY-Desktop](https://github.com/GiorgioRafael/OpenQCY-Desktop)** by GiorgioRafael (MIT): a
  Windows app tried on a MeloBuds N70 (vendor id 23877, firmware 3.0.13). Its transcript is in
  `data/qcy/test_vectors.json`.

QCY's earbuds use chips from several makers, and QCY's app picks the protocol by the chip, which the
vendor id names (`Devicebind`):

| Vendor id bits 4-7 | Chip | Protocol |
|--------------------|------|----------|
| `0x10` | Qualcomm | QCY's own (this document) |
| `0x40` | WUQI | QCY's own |
| `0x70` (or id 512) | JieLi | JieLi's RCSP (`JLDeviceImpl`); vendor ids with bits `0x8070` (`isNewJLDevice`) send QCY's own items inside RCSP's custom command |
| `0x90` | ZR | QCY's own, with its own key table and EQ format |
| `0xA0` | RD | QCY's own |
| `0xC0` | Realtek | QCY's own |
| `0xD0` | BES | QCY's own |

Bits 8-11 of `0x100` mark a product that is not true wireless (`isNotTWS`): one battery.

- **JieLi:** section 6. QCY's app needs no authentication for their settings: JieLi's authentication
  (`RcspAuth`, native code) is only in JieLi's firmware-update and watch SDKs, not in `jl_bt_rcsp`.
- **ZR models:** these get QCY's settings, but not their touch controls or EQ.

## 2. Finding the earbuds

QCY's earbuds take their settings over Bluetooth LE GATT, service `0000A001-…`. QCY's app finds the
earbuds by scanning (`BleScanManager`):

- **The advert:** manufacturer data under company id `0x521C` (21020), at least 20 bytes
  (`Devicebind.refreshInfoFromAVData`):

  | Bytes | Meaning |
  |-------|---------|
  | 0-1 | vendor id, high byte first: the model |
  | 3 | colour: `(byte & 0x0E) >> 1` |
  | 5, 6, 7 | battery: left, right, case; bit 7 charging, low 7 bits percent (0 or over 100 unknown) |
  | 12, 11, 13, 16, 15, 14 | one earbud's classic address, in that order |
  | 19, 18, 20, 23, 22, 21 | the other's; all zero means the same |

- **Connecting:** QCY's app connects over LE to the device that sent the advert, which is not the
  classic address. PairFree does the same: it scans for at most 10 s for an advert carrying the paired
  address (either earbud's), then connects to that device. Scanning needs `BLUETOOTH_SCAN`, which the
  owner allowed for QCY on 2026-10-18, as for JBL. It is asked for only when a QCY device is opened,
  declared `neverForLocation`, and the scan ends when the earbuds are found.
- **PeatsAudio's company id:** PeatsAudio's copy of the library notes the company id as `0x522C`,
  but QCY's app uses `0x521C` (`BuildConfig.CompanyID`).

## 3. Characteristics and packets

| Characteristic | Use |
|----------------|-----|
| `00001001` | Commands: write packets |
| `00001002` | Settings: read, or notify; packets with the earbuds' reports |
| `00000007` | Version: read; ASCII on some models, else 3 bytes (6: left, then right) |
| `00000008` | Battery: read or notify; three bytes as in the advert |
| `0000000B` | EQ: the preset id, then a signed gain per band, written raw |
| `0000000D` | Key table: pairs of key and action, written raw |

QCY's app subscribes to every characteristic of the service that notifies.

**Packets** on `1001` and `1002`: `FF`, the length of everything after it, then items, each its id,
its value's length and its value. One packet can hold several items. Item `FE` with one byte asks for
that item, and the earbuds then report it on `1002`. They also report changes made on the earbuds.

`data/qcy/commands.json` lists the items from QCY's `DataBean`. The ones PairFree uses:

| Item | Name | Value |
|------|------|-------|
| `06` | In-ear detection | 01 on, 02 off |
| `09` | Game mode | 01 on, 02 off |
| `0C` | Noise mode, older models | one byte (section 5) |
| `10` | Sleep mode (touch controls off) | 01 on, 02 off |
| `12` | Status light | 01 on, 02 off |
| `16` | Balance | 0 left to 100 right, 50 centre |
| `17` | Noise setting | mode, scene, value (section 5) |
| `23` | LDAC | 01 on, 02 off |
| `24` | Two devices | 01 on, 02 off |
| `27` | Dynamic EQ | 01 on, 02 off |
| `2A` | Wind noise detection | 01 on, 02 off |
| `2C` | Wearing detection | on (01) or off (02), the music action, the noise action, and on some models the tone (OpenQCY: the N70's in-ear detection) |
| `2D` | Spatial audio | 01 on, 02 off |
| `40` | Adaptive volume | 01 on, 02 off |
| `FE` | Request | the item to report |

**Switch values:** QCY's app sends each switch's on and off bytes as the model's control panel gives
them. Quicky did not keep those bytes. OpenQCY uses 01 on, 02 off for every switch, and so does
PairFree; PeatsAudio's library wrote LDAC and two devices as 01 and 00. A report of 01 is on, anything
else off. PairFree asks for the item again 3 s after each write, so the screen shows what the earbuds
hold.

**Never sent:** reset `01`, clear pairing `02`, factory reset `03`, test mode `0D`, rename `18`, photo
`1E`, standby `1F`, restart `25`, key reset `26`, TWS `34`, clock `3E`, alarm `3F`, and anything for
firmware updates.

## 4. Touch controls

The key table on `000D` is pairs of key and action (`KeyFuncDataBean`). Keys and actions follow
Quicky's tables:

- **Keys:** 1 left tap, 2 right tap, 3 and 4 double tap, 5 and 6 triple tap, 9 and 10 hold.
- **Actions:** 0 none, 1 play or pause, 2 previous, 3 next, 4 voice assistant, 5 volume up, 6 volume
  down, 7 game mode.

QCY's app takes each model's numbers from its control panel. PairFree offers the gestures the panel
lists, with the panel's actions whose names match that table; "ANC mode" has no number there and is
left out. A write is the whole table with the change, in key order, as OpenQCY writes it.

## 5. Noise control

QCY's control panels pack each noise state as one number: `mode << 16 | scene << 8 | value`.

- **Packed values above `0xFF`:** sent as item `17` with the three bytes.
  - Mode 1 is noise cancelling, with these scenes:

    | Scene | Name | Value |
    |-------|------|-------|
    | 1 | indoor | strength 0-2 |
    | 2 | commuting | strength 0-2 |
    | 3 | noisy | strength 0-2 |
    | 4 | anti-wind | — |
    | 5 | adaptive | — |

    The panels name scenes 1 and 3 the other way round on some models; PairFree goes by each model's
    panel.
  - Mode 2 is off.
  - Mode 3 (on two models 4) is transparency, with a level in the value (up to seven).
  - QCY's app sends transparency at scene 1, value 0 as scene 2 (`QCYHeadsetClient.setNoiseMode`).
- **Older models:** one byte, sent as item `0C`: `0x02` off, `0x41`-`0x43`, `0x51`-`0x53`, `0x61`-`0x63`
  and `0x71` the scenes, `0xA0`-`0xA6` transparency.

OpenQCY's N70 writes (`data/qcy/test_vectors.json`) are adaptive `01 05 00`, indoor `01 01 02`,
commuting `01 02 02`, noisy `01 03 02` and anti-wind `01 04 00`.

PairFree shows off, cancelling and transparency on the shared control where the model has all three,
a switch where it has two, then the scene, its strength and the transparency level.

## 6. JieLi models

84 models are on JieLi chips. They advertise as the others do (section 2), and PairFree finds them the
same way, but their settings go through JieLi's RCSP over another GATT service. Most of what QCY's app
sends there is QCY's own items (section 3), so PairFree reads and writes them as for the other models
and only changes how they travel (`QcyJieliSession`). The newer models (vendor ids with bits `0x8070`,
`isNewJLDevice`, 5 models: `jieli_custom` in `data/qcy/devices.json`) take more of them as QCY's items.

**Service** `0000A002-…`: write `00000001`, notify `00000002`.

**Frame** (`jl_bt_rcsp`'s `BasePacket`): `FE DC BA`, a flags byte, the opcode, the parameters' length
(two bytes, high first), the parameters, `EF`. Bit 7 of the flags marks a command, bit 6 that an answer
is wanted. A command's parameters start with its sequence number; an answer's with a status (0 success)
and the sequence number of the command it answers. A frame can span several notifications.

| Opcode | Name | Use |
|--------|------|-----|
| `03` | Target info | 4-byte mask of attributes, then the platform (0). PairFree asks only for attribute 5, the firmware version: two bytes, one digit per nibble (`1.2.3.4`). JieLi's SDK reads it on every connection. |
| `C0` | Set attributes | length-type-value attributes (the length counts the type) |
| `C1` | Get attributes | 4-byte mask, bit *n* for attribute *n*; the answer is length-type-value attributes |
| `C4` | Earbuds ask | the earbuds' own command; QCY's app reads the settings again on the older models |
| `FF` | Custom | QCY's items as in section 3, without the `FF` and length in front |

The earbuds' own commands that want an answer get one: status 0 and their sequence number.

**Attributes** (`C0`, `C1`) that QCY's app uses:

| Attribute | Name | Value |
|-----------|------|-------|
| 0 | Battery | three bytes as in the advert |
| 5 | Work mode | 2 game mode, 1 normal |
| 8 | In-ear detection | 01 on, 02 off |
| 10 | Sleep mode | 01 on, 02 off |
| 12 | Balance | 0 left to 100 right |

**Which goes where**, as `JLDeviceImpl` and `QCYConnectManager` send it:

| Setting | Older models | Newer models |
|---------|--------------|--------------|
| Battery | attribute 0 | item `2F`, three bytes as in the advert |
| Firmware | target info attribute 5 | item `30`, three bytes (six: left, then right) |
| Noise control | item `17` | item `17` |
| Game mode | attribute 5 | item `09` |
| Sleep mode | attribute 10 | item `10` |
| In-ear detection | attribute 8 | attribute 8 |
| Balance | attribute 12 | attribute 12 |
| Other switches | QCY's items | QCY's items |

Every JieLi model's noise values in QCY's control panels are packed ones (section 5), which QCY's app
sends only as item `17`.

**Never sent:** reboot `E7`, function `0E` and system info `08` writes, system operations `C5` (factory
reset among them), the attributes PairFree does not write (the name among them), and QCY's never-sent
items (section 3) inside the custom command.

**Left out:** the touch controls (no JieLi model's panel in Quicky lists them) and the EQ, which the
older models take through JieLi's own EQ setting and the newer ones as items `20` and `22`.

## 7. Not yet

- **EQ:** the presets' values came from QCY's server, so PairFree leaves the EQ out. The custom EQ is
  `0000000B` with the preset id and a signed gain per band, or parametric items `20` and `22`, which
  OpenQCY writes on the N70.
- **Other:** the fit test (`11`), auto power-off (`14`) and prompt volume (`1D`).
