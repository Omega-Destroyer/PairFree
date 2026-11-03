# EarFun protocol

How EarFun earbuds and headphones are controlled, as PairFree does it. No EarFun model has been tried with
PairFree yet.

## 1. Sources

- **EarFun's app**: EarFun Audio 20.1.61 (`com.corelink.earfun`), decompiled outside the repo on 2026-11-03.
  None of its code is in the repo. These parts were read:
  - `device/base/DeviceType`: the 35 models, each with its match name, product ids, configuration (what the
    app offers it), parser, equalizer and firmware updater.
  - `device/protocol/headset`: `QualcommProtocolParser` and its subclasses `TW600ProtocolParser`,
    `TW700ProtocolParser` (and `TW700AIProtocolParser`) and `HP1000ProtocolParser`; `EarfunProtocolParser` and
    `CW100ProtocolParser`, and the parsers `DeviceType` writes inline for the Air Life, the Air Pro 4i, the
    Clip 2 and the headphones (`EarfunHeadProtocolParser`).
  - `device/equailzer`: `BaseEquailzerProtocol`, `Qualcomm10Equailzer`, `TW205Equailzer` and the equalizers
    `DeviceType` writes inline (section 6).
  - `device/config`, `page/device/...` (`HeadsetOtherViewHolder`, `DeviceVoiceWizardFragment`,
    `HeadsetQualityFragment`): which screen a model shows and what each sends.
- **Gadgetbridge** ([codeberg.org/Freeyourgadget/Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge),
  AGPL-3.0: facts only, never its code), which supports the Air S, Air Pro 4 and Free Pro 3 over the Qualcomm
  frames, read on 2026-11-03 (commit 562ae7700189) as a check. Its command numbers agree with EarFun's app;
  where its EQ differs, EarFun's app is followed (section 6, `docs/UPSTREAM.md`).

## 2. Connection

Every model PairFree covers is controlled over the standard serial port (RFCOMM,
`00001101-0000-1000-8000-00805F9B34FB`; `BaseProtocolParser.getUUID`). `00007034-…` is EarFun's firmware
update channel, which PairFree never opens. Only one app can hold the serial channel.

EarFun's app picks the model from the product id in the earbuds' Bluetooth LE advert, after the classic
address (`BtEngine.scanResult`). PairFree matches the Bluetooth name instead, whole (`data/earfun/devices.json`),
so it needs no scan permission; every name in EarFun's table is distinct, except where two chips share a
name (section 5).

EarFun's app queues its commands and sends one at a time. PairFree does the same: a read waits up to 1 s for
its answer, then 50 ms pass before the next frame; a change is read again 3 s later.

Two protocols run on that channel, by the model's chip.

## 3. Frames

### Qualcomm models (`QualcommProtocolParser`)

GAIA, as SOUNDPEATS' Qualcomm models speak it (`docs/soundpeats/PROTOCOL.md` section 2):

```
FF | 04 | 00 | length | 00 0A | 03 command | data
```

The length counts the data. The answer comes back as `83` (or `03`, unasked) and the command, then a status
byte, then the value: EarFun's app reads the value at the answer's fourth byte (`getNormalData`), the
firmware version as text from there. The reader takes protocol versions `04` and `03`.

On connecting EarFun's app sends `00 0A 03 00` and opens GAIA v3 on vendor `001D`: the features (`0001`), the
protocol version (`000D` `07 00 00 00 04`) and notifications for features `0D`, `05`, `08`, `01`, `06` and `07`
(`0007`; `initCommand`). PairFree sends the same, without feature `06`, firmware update. The Air Pro 4's
parser sends none of it, and PairFree neither.

### EarFun's own frame (`EarfunProtocolParser`)

```
EF | command (2) | length | payload | (length + payload bytes) & FF | FE
```

Both ways alike. EarFun's app takes a frame at each `FE` whose checksum matches (`checkData`); PairFree splits
by the length byte and checks the end byte and the checksum. Switches are 2 on, 1 off.

## 4. Commands

`data/earfun/commands.json` lists both command sets with the values each setting takes; `EarfunQcc` and
`EarfunCommand` are generated from it. PairFree reads only what EarFun's app offers the model (section 5),
and shows a setting once its read has answered.

| Setting | Qualcomm (read / write) | EarFun frame (read / write) | Values |
|---|---|---|---|
| Firmware | `09` | `10 10` | text; or three numbers (text on the headphones and the Air Pro 4i) |
| Battery | `06`, `07`, case `17` | `10 14` (left, right), case `10 18` | percent; on the Air Pro 4+ and 5 the case's bit 7 is charging |
| Noise mode | `15` / `14` | `20 80` / `20 85` | per model (`noise.values`): Qualcomm 0 off, 1 cancelling, 2 ambient, 3 adaptive, 4 wind; EarFun 1 off, 2 ambient, 3 wind, 4 cancelling, 5 adaptive; the headphones 4 ANC 2, 5 ANC 1; the Air Pro 4i 3 cancelling |
| Cancelling mode | `3B` / `3A` | `23 10` / `23 15` | per parser (`values.noise_cancel_mode`) |
| Ambient mode | `3D` / `3C` | `23 00` / `23 05` | Qualcomm 0 default, 1 full; EarFun 1 default, 2 natural |
| Game mode | `13` / `12` | `20 30` / `20 35` | |
| Two devices | `26` / `27` | `21 40` / `21 45` | |
| Microphone | `31` / `30` | | 0 automatic, 1 left, 2 right |
| Voice prompt volume | `39` / `38` | `21 20` / `21 25` | Qualcomm 0 = 100 % .. 4 = 0 %; EarFun 1 = 0 % .. 5 = 100 % |
| Volume limit | `54` / `53` | `23 20` / `23 25` | 1 90 dB, 2 80, 3 70, 4 60, 5 50, 0 off |
| Auto power-off | `48` / `47` | `21 30` / `21 35` | per model (`values.auto_off`); never is `FF` |
| Spatial audio | | `21 90` / `21 95` | |
| In the ear | `4A` / `49` | | Air Pro 4+ and 5: bit 0 set turns pausing off, bit 1 answering calls; the other bits are written back as read |
| Find the earbuds | `33` / `32` | `20 50` / `20 55` | Qualcomm bit 0 left, bit 1 right; EarFun a byte each |
| Touch controls | `0B` / `0A` | `20 70` / `20 75` | below |
| Touch off | `35` / `34` | `20 60` / `20 65` | Qualcomm 1 both, 2 left, 3 right, 0 none; EarFun a byte each, 1 off |
| EQ | GAIA `001D` `0E 01` | `20 95` | section 6 |

**Touch controls.** The Qualcomm read holds the action bytes at every other byte from the third (after the
status): left then right for one tap, double tap, triple tap and press and hold, then the call gestures; a
write is `[gesture, action, 0, 0]` for the left earbud or `[0, 0, gesture, action]` for the right, the
gesture 1, 2, 3 or 17 (`KeyTypeEnum`). The EarFun frame holds eight action bytes, the left's four gestures
then the right's, and a write sends all eight. The action bytes differ by model (`values.key_actions`):
Qualcomm's `KeyOperateEnum`, and the EarFun frame's key maps (`EarfunProtocolParser`, `CW100ProtocolParser`, the
Air Pro 4i's and the Clip 2's). PairFree offers per gesture what EarFun's app offers, leaving out powering
off; the call gestures and two actions that need a firmware version PairFree does not check (call back on the
Air Pro 4 before version 28, quiet mode on the Clip before 20007) are left out.

**Never sent** (`NEVER_SEND`, `PRIVATE`): factory reset (Qualcomm `36`, EarFun `10 00 AF A5`), restart (`08`),
powering off (`SET_CONTROL_PLAY` 1: Qualcomm `51`, EarFun `20 25`, so no play controls either), renaming,
connecting, disconnecting and pairing other devices, the paired phones' list (names and addresses), the
serial number and any firmware update. `EarfunSession.allowed` checks every frame; on vendor `001D` only the
opening and the EQ pass.

## 5. Models

`data/earfun/devices.json` has one entry per Bluetooth name, from `DeviceType` and each model's
configuration: 26 entries for EarFun's 33 earbuds and headphones (the two speakers are left out until
speakers are taken up; owner, 2026-10-30). Two codes share an entry where EarFun's app treats them alike
(`TW500` and `TW500_3072`, `TWA01` and `TW201`, `TW220` and `TW220_VDL`, `OW100` and `OW100_NEW`, `TW700` and
`TW700AI`). The Air Pro 4+AI shares the Air Pro 4+'s entry: they differ by their colour code above 4, and in
EarFun's app only by AI features PairFree does not offer.

- **Qualcomm** (9): Free Pro 3, Air Pro 3, Air Pro 4, Air Pro 4+ (and 4+AI), Air Pro 5, Air S, ATVEL Tune S5
  ProX, Free 2S and Wave Pro X. The Free 2S also comes on a ZhongKe chip (`TW101C`) under the same name; those
  will not answer, and PairFree shows nothing for them.
- **EarFun's own frame** (9): Air 2 NC, Air Life, OpenJump, Clip, Clip 2, Air Pro 4i, Wave Life, Tune Pro and
  Wave Pro 2.
- **Not covered yet** (section 9): Air (Airoha), Air 2, Air Pro 2 and ATVEL Tune S5 Pro (WUQI), Air Pro SV,
  Free 1S (ZhongKe), Wave Pro and ATVEL Tune Pro Q7 (BES). PairFree does not list them among the paired
  earbuds.

## 6. EQ

The EQ cannot be read: EarFun's app keeps it on the phone and sends it when the user changes it, never on
connecting, and PairFree does the same (`EarfunEqTarget`, `restoresOnConnect`).

Each band goes as one frame (`BaseEquailzerProtocol.sendEq`):

```
index | master gain (2) | frequency x3 (2) | gain x60 (2) | Q x4096 (2)
```

- **Index**: on the Qualcomm models one byte, `A1` and up (`Qualcomm10Equailzer.index`: ten bands, the band
  from 1), inside GAIA vendor `001D` command `0E 01`, as SOUNDPEATS' `0E 01` (`docs/soundpeats/PROTOCOL.md`
  section 4). On the Air Pro 4+, 5 and Wave Pro X each band ends with `0D` (`TW700Equailzer`). On the EarFun
  frame two bytes, the band count and the band from 1, in `20 95`.
- **Bands**: 31.5 (sent as 94, the app truncating 31.5 x 3), 63, 125, 250, 500, 1k, 2k, 4k, 8k and 16k Hz, Q
  0.7; the Air Life's first is 32 Hz. The 6-band models (Air S, ATVEL Tune S5 ProX, Free 2S;
  `Qualcomm6Equailzer`) have 63 Hz (Q 0.7), 180, 500, 1k, 8k and 15k Hz (Q 0.6), then four fixed bands always
  sent: 33 Hz +4.5 dB (Q 0.5), 200 Hz -4.5 dB (Q 0.6), 2 kHz +1.5 dB (Q 0.9), 15 kHz +3.5 dB (Q 0.6).
- **Gains**: EarFun's sliders have 21 positions, -10 to 10. On the 10-band models a position stands for
  -6, -5, -4, -3.5, -3, -2.5, -2, -1.5, -1, -0.5, 0, 0.5 .. 4, 5, 6 dB (`gainQcc`); PairFree's whole dB from -6
  to 6 each land on one. On the 6-band models the position is the gain in dB, -10 to 10.
- **Master gain**: -6 dB (`FE 98`) on most models. The headphones and some others work it out from the
  highest gain: the Wave Life -2 dB minus the highest gain above 0; the Tune Pro and Wave Pro 2 -8 dB minus it,
  cut to whole dB; the Clip -5 dB minus it; the Clip 2 0 minus it; the OpenJump and Air Pro 4i from a table by
  the highest position (`master` in `devices.json`).
- **Presets**: EarFun's 30 named curves for the 10-band models and four for the 6-band ones
  (`data/earfun/eq_presets.json`), as slider positions; they go out like a curve of the user's own. The
  partners' curves (OLUV, Fernanda) and those EarFun's server sends are left out.

Gadgetbridge writes the master gain as `FF 98` (-1.7 dB) and a slider step as 0.6 dB on both band counts;
EarFun's app writes `FE 98` and the steps above. PairFree follows EarFun's app (`docs/UPSTREAM.md`).

## 7. What the screens show

- **Home:** the battery, noise control (off, noise cancelling, ambient) where the model has it, the kind of
  noise cancelling (standard, adaptive, wind, ANC 1 and 2, or the sub-modes: balanced, adapts to your ears,
  levels 1 to 4) and the kind of ambient sound (full or natural).
- **Sound:** the EQ (section 6); spatial audio.
- **Controls:** the four gestures on each earbud, and turning the touch controls off.
- **Device:** two devices, game mode, the microphone, the voice prompt volume, the volume limit, auto
  power-off, pausing and answering calls in the ear, and finding the earbuds.

## 8. Not yet

- **Sound quality** (aptX, aptX Adaptive, aptX Lossless, LDAC; Qualcomm `2F` / `2E` bits 1, 2, 8 and 16, EarFun
  `21 00` / `21 05` bits 1, 2 and 4): EarFun's app turns these on and off as switches whose rules between them
  are on its screen, and the earbuds restart after a change (`HeadsetQualityFragment`, 20 s). Gadgetbridge
  writes single values (0 stable, 1 aptX, 3 aptX Adaptive, `13` aptX Lossless, 8 LDAC). Left for a recording.
- **Voice prompt language** (Qualcomm `71` / `70`, EarFun `21 10` / `21 15`): each model has its own list, and
  some reset after a change (`changeVoiceNeedReset`).
- The call gestures, the Wave Pro X's and the headphones' buttons (other read layouts), USB charging on the
  headphones, the Wave Pro X's spatial audio and second power-off timer, the left-right balance, bass boost,
  quiet mode, the DAC switch and game EQ (no screen of EarFun's app shows them for the models above, or their
  read is not known), LE Audio and Fast Pair (lab), Auracast, AI translation, and the codec in use (`4D`).
- The Qualcomm voice prompt language read: EarFun's app reads it from the frame's length byte
  (`getNormalData(src)` instead of the payload), a slip that does not matter to PairFree, which does not read it.

## 9. Other chips

EarFun's app speaks other chips' own protocols to the remaining models: Airoha's RACE (the Air,
`AirohaProtocolParser`; the Air Pro SV's own commands, `TW306`), WUQI's (`WuqiProtocolParser`: Air 2, Air Pro 2,
ATVEL Tune S5 Pro), ZhongKe's (Free 1S, and some Free 2S) and BES's (Wave Pro, ATVEL Tune Pro Q7). Covering
them is the next step for EarFun; their parsers are in the same app.
