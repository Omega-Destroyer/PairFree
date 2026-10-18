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
  - `JLDeviceImpl`: the JieLi path.
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

- **JieLi:** PairFree does not speak JieLi's RCSP yet and says so for those models. QCY's app needs no
  authentication for their settings: JieLi's authentication (`RcspAuth`, native code) is only in
  JieLi's firmware-update and watch SDKs.
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

## 6. Not yet

- **JieLi:** JieLi's RCSP (79 models), and QCY's items inside its custom command (5 models).
- **EQ:** the presets' values came from QCY's server, so PairFree leaves the EQ out. The custom EQ is
  `0000000B` with the preset id and a signed gain per band, or parametric items `20` and `22`, which
  OpenQCY writes on the N70.
- **Other:** the fit test (`11`), auto power-off (`14`) and prompt volume (`1D`).
