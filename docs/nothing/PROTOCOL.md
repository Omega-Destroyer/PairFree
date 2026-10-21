# Nothing and CMF protocol

How Nothing and CMF earbuds and headphones are controlled, as PairFree does it. No Nothing or CMF
model has been tried with PairFree yet.

## 1. Sources

- **Nothing X 3.4.15** (`com.nothing.smartcenter`), decompiled outside the repo on 2026-10-18.
  The app's screens are Flutter, but the protocol is plain Kotlin:
  - `com.nothing.protocol.model.Message`: the frame.
  - `HeadsetSppConnector`: sending, timing, sequence numbers and response codes.
  - `com.nothing.base.protocol.constant.ProtocolConstant`: the command table.
  - `com.nothing.core.ext.TWSDeviceExtKt` and `com.nothing.earbase.os.DeviceProtocol`: the payloads.
  - `IOTProductDevice*`: the models.
  - `ear_white_list.json` in its Flutter assets: the feature table for each model.
  None of its code or files are in the repo. `data/nothing/commands.json` and
  `data/nothing/devices.json` hold the facts read from it.
- **[nada](https://github.com/chukfinley/nada)** by chukfinley (MIT): an Android app read from
  Nothing X 3.6.1 and tried on a CMF Buds 2 Plus (B184). Its noise reports are in
  `data/nothing/test_vectors.json`.
- **[nadamas](https://github.com/ezvk/nadamas)** by ezvk (MIT): a Linux app, a fork of
  SoaOaoS's [something-x](https://github.com/SoaOaoS/something-x) (MIT). It was tried on an Ear (2),
  an Ear (3a) and a CMF Buds Pro 2. The Ear (3a)'s product id, the codec values and the touch
  control finding (section 5) come from it.
- **[ear (web)](https://github.com/radiance-project/ear-web)** and
  [Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge) cover the older models. Their
  licence is AGPL (ear (web)'s LICENSE file says so, although its README says GPL-3.0), so they
  are used for facts only. nada lists the commands where ear (web) is wrong.

Where nada and Nothing X differ, Nothing X wins, unless a hardware test settles it. Section 5
names each such case.

## 2. Frame

Classic Bluetooth serial (RFCOMM). The service UUID is `AEAC4A03-DFF5-498F-843A-34487CF133EB`.

```
55 | control (2) | command (2) | length (2) | fsn | payload | crc16 (2)
```

Every number of two bytes is low byte first.

- **control:**
  - bits 0 to 4: the response code (0 success, 1 failure, 2 not activated, 3 not supported,
    4 invalid parameter, 5 verification failure, 6 validation failure, 7 time-out without ack,
    8 time-out).
  - 0x20: a CRC follows.
  - 0x40: multi-frame.
  - bits 8 to 11: the device type, 1 for earbuds and headphones (2 is a watch).
  - Nothing X sends 0x0160 (`55 60 01`); the Ear (1) takes no CRC, so 0x0140.
  - Replies seen carry 0x0120 or 0x0160 and the response code.
- **command:** reads are C0xx, writes F0xx, events the earbuds send unasked E0xx, and debug FCxx.
  A reply carries the command it answers with bit 15 cleared: `C007` → `4007`, `F00F` → `700F`.
  Events keep bit 15.
- **fsn:** counts 0 to 253, then starts over (`HeadsetSppConnector.createFsn`).
- **crc16:** CRC-16/MODBUS (reflected polynomial 0xA001, initial 0xFFFF) over every byte before
  it. Its check value for "123456789" is 0x4B37.

## 3. Session

- **Activation:** Nothing X reads the protocol version (`C001`) first on every model whose
  protocol class sets `isNeedActivate`, which is all but the Ear (2) and the Ear (a).
  - nada sends no activation at all and works on a B184.
  - nadamas follows `C001` with `F001` (protocol activated, no payload).
  - PairFree sends `C001` like Nothing X. It sends `F001` only if a reply comes back with
    response code 2 (not activated), and then reads everything again, once.
- **Timing:** Nothing X sends one command at a time and waits for its reply: 5 s for most,
  1 s for the version read. It leaves 40 ms between writes.
  - PairFree waits up to 1 s per command. A read the earbuds lack gets no reply at all on some
    models: nadamas found the Ear (3a) silent.
- **Reads on connect:** model `C01C`, firmware `C042`, battery `C007`, noise `C01E` (with
  payload `03`), EQ preset `C01F`, extra features `C00E`, low lag `C041`, bass boost `C04E`,
  spatial audio `C04F`, codec `C029`, touch controls `C018`. The battery is read again every
  30 s.
- **Unsupported reads:** a read the model lacks is answered with response code 3, with an empty
  payload (nadamas: the CMF Buds Pro 2 does this), or not at all. Any of these hides the
  setting.
- **Events:** the earbuds send battery (`E001`) and noise (`E003`) changes, in the same layouts
  as the reads.
- **After a write:** PairFree reads the setting again 3 s later.
- **Never read:** `C005`, `C006`, `C016` and `C028`. They hold the serial number, the
  manufacture data and the paired devices' addresses (`private` in `commands.json`).
- **Never sent:** factory reset `F03D`, the firmware update commands, calibration and the debug
  commands (`never_send`).

## 4. Commands

`data/nothing/commands.json` lists all 137 by Nothing X's names. The ones PairFree uses:

| Read | Write | What |
|------|-------|------|
| `C001` | `F001` | Protocol version / activated |
| `C01C` | | Device model (product id) |
| `C042` | | Firmware version, ASCII |
| `C007` | | Battery |
| `C01E` | `F00F` | Noise mode |
| `C01F` | `F010` | EQ preset |
| `C00E` | `F004` | Extra features (in-ear detection) |
| `C041` | `F040` | Low lag mode |
| `C04E` | `F051` | Bass boost and its level |
| `C04F` | `F052` | Spatial audio, head tracking |
| `C029` | `F01C` | Codec |
| `C018` | `F003` | Touch controls |
| | `F002` | Find the earbuds |

## 5. Payloads

- **Device model (`C01C` reply):** two bytes, printed in reverse as hex: `84 B1` is `B184`
  (`DeviceModelEntity`).
- **Battery (`C007`, `E001`):** a count, then a part and its level for each. The parts are
  1 a watch, 2 left, 3 right, 4 case and 6 both, which is how headphones report one battery.
  Bit 7 of the level marks charging.
- **Noise (`C01E`, `E003`):** groups of three bytes: type, value, 0.
  - Type 1 is the mode in effect. Type 2 is the cancelling strength to go back to, and it keeps
    its value while noise cancelling is off.
  - The modes (`DeviceNoiseReduction`, `DeviceConstant`): 1 high, 2 mid, 3 low, 4 adaptive,
    5 off, 6 comfortable, 7 transparency, 8 a second adaptive. PairFree shows 1 to 5 and 7.
  - To set: `F00F 01 <mode> 00`.
  - nada measured a B184: transparency gives `01 07 00 02 04 00`, high gives `01 01 00 02 01 00`.
- **EQ preset (`C01F`, `F010`):** the preset as a two-byte number: 0 balanced, 1 voice, 2 more
  treble, 3 more bass, 4 Dirac, 5 custom.
  - Nothing X agrees with itself in three places: `EQModeEntity`, `BaseNothingEarImpl.parseEqModel`
    and `BaseEqualizerViewModel`. Its screen lists them as balanced, more bass, more treble,
    voice.
  - nada and nadamas number them in that screen order (1 more bass, 3 voice). Nothing X wins
    until a report settles it.
  - Models whose feature table sets `eq` to 0 use Dirac Opteo presets, which PairFree does not
    know (section 6).
  - PairFree shows the presets on the Sound tab's equalizer for the models it knows; for a product
    id it does not know, as a row.
- **Custom EQ (`C044`, `F041`):** what Nothing X calls the simple custom EQ
  (`SimpleEqualizerViewModel.setCustomEQ`, `CustomEQ`). Picking it sets the EQ mode to 5 (`F010 05 00`);
  each change then sends the curve:
  - A count (3), the total gain as a little-endian float, then per filter its type (0 low shelf, 1 peak,
    2 high shelf), and its gain, frequency and Q as little-endian floats. Nothing X sends the peak, then
    the high shelf, then the low shelf (its radar's mid, treble and bass).
  - The gains are whole dB from -6 to 6 (`EQLabelItem.convertToGain`); the total gain is minus the
    highest gain, or 0 when none is above 0.
  - Each model's frequencies and Qs are in the feature table (`customEQ`: `freqLow`, `qLow`,
    `freqPeak`, `qPeak`, `freqHigh`, `qHigh`), `simple_eq` in `data/nothing/devices.json`.
  - `CustomEQ` sizes its buffer at 16 bytes a filter and fills 13, so 9 zero bytes follow the three
    filters; PairFree sends them too.
  - `C044` answers in the same layout without the zeros, so PairFree reads the curve back.
- **Extra features (`C00E`, `F004`):** a count, then a feature and its value for each. Feature 1
  is in-ear detection (`DeviceExtraFeatureStatus`). Set with `F004 01 01 <on>`.
- **Low lag (`C041`, `F040`):** 1 on, 2 off, written as two bytes.
- **Bass boost (`C04E`, `F051`):** on, then the level 1 to 5. Nothing X's plain switch writes
  level 5. nada found that with spatial audio on, a bass write is acknowledged and ignored.
- **Spatial audio (`C04F`, `F052`):** on, then on the models with head tracking (the
  headphones) head tracking.
- **Codec (`C029`, `F01C`):** two bytes: 0 standard (SBC and AAC), 1 LHDC, 2 LDAC.
  - Nothing X's builder writes only 0 or 1.
  - nadamas found that the Ear (3a) acknowledges and keeps 1 yet offers no LHDC, and that 2
    turns LDAC on. PairFree writes 2 for LDAC.
  - The phone picks the codec when the earbuds connect, so a change takes a reconnect.
- **Find (`F002`):** the part (2 left, 3 right) and on or off. Headphones take the on or off
  byte alone (`TWSDeviceExtKt.whereAmI`, side 5).
- **Touch controls (`C018`, `F003`):** the reply is a count, then device, button, gesture and
  action for each. A write is `F003 01 <device> <button> <gesture> <action>`, as Nothing X
  writes it (`DeviceProtocol.setGestureData`).
  - **Devices:** 2 left, 3 right, 4 the CMF Buds Pro 2's case dial (Nothing X's Smart Dial),
    6 the whole product.
  - **Buttons:** 1 the gesture itself, 9 what the same gesture does during a call. Nothing X lists
    the call actions as a gesture's second operation group (`default_call_controls`, with
    `callControlsButton` 9); the CMF Buds Pro 2's dial has them for double press (answer, or answer
    and mute during a call) and triple press (hang up or decline). Its default key table holds
    both entries (`04 09 02 01`, `04 09 03 01`).
  - **Gestures:** 1 single, 2 double, 3 triple, 7 hold, 9 double and hold, 10 turn (the dial; its
    action is 23, volume, or 1, none).
  - **Actions:** Nothing X's operation numbers, `ACTIONS` in `NothingSession`: 3 answer, 23 volume,
    25 answer or mute, 26 hang up or decline, 17 game mode, among others. The default hold action
    0x16 is a noise cycle the operation lists do not name. 31 is Nothing's news summary, offered on
    Nothing phones; PairFree leaves it out.
  - The dial locks and unlocks with a double press and hold, which Nothing X shows but does not let
    the user change.
  - nadamas wrote new double-press actions to an Ear (3a): it acknowledged them and read them
    back, but kept the old behaviour. Whether that model needs something more is open.

## 6. Models and the feature table

`data/nothing/devices.json` has a row for each model's product id. The name comes from Nothing X, or
from nada and nadamas where Nothing X 3.4.15 gives only a project number. The rest comes from
Nothing X's `ear_white_list.json`, with one entry per firmware range. The ranges name the last two
fields of the firmware (`*.*.1.74`).

Nothing X reads that table in its Dart code, which PairFree cannot read. Two fields are decoded
from what the models are known to have:

- **`ancLevel`:** a bit for each noise mode: 0 high, 1 mid, 2 low, 3 adaptive, 4 transparency,
  5 off.
  - The Ear (stick) settles bits 4 and 5. It has 33 (bits 0 and 5) on firmware x.x.2 and later,
    and Nothing X's own code (`IOTEarStickWhite`) says it has noise cancelling only from x.x.2
    and never transparency.
  - The other values fit what the models offer: the Ear (1)'s 53 is high, low, transparency
    and off ("maximum" and "light"), the CMF Buds' 49 is one strength.
- **`highQualityAudio`:** 2 LHDC, 4 LDAC. 7 is the Ear (2024), which has both.

`eq` set to 0 marks the CMF models with Dirac Opteo presets, which are sent as `F01D`/`C050`. Their
names are not in the Kotlin code, so PairFree leaves them out.

The Ear (3a) (B190) is newer than 3.4.15. Its row comes from nadamas, which read its product id
and tried its features. A product id PairFree does not know gets every setting its earbuds answer
for.

## 7. Not yet

- **Advanced EQ:** `F04F`/`C04C` for the mode, `F050`/`C04D` for eight bands with a profile
  index. nada uses `C06C`/`C06D`, which are not in 3.4.15.
- **Dual connection** (`F01A`/`C027`): what its value means is in Dart, so it is left out. Some
  models restart after a change (`dualConnectionReboot`).
- **Ear tip fit test:** the result arrives as `E00D`; how to start it is in Dart.
- **Other features:** personalised noise cancelling and sound (Mimi), the headphones' extra
  buttons.
