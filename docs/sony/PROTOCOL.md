# Sony protocol

How PairFree talks to Sony earbuds and headphones. Everything here comes from Sony's own app,
Sony | Sound Connect (`com.sony.songpal.mdr`) 13.3.0, read on 2026-10-14 outside the repo; none
of its code is in it. Its protocol package keeps Sony's names (`tandemfamily`, "MDR"), which
this document uses. Where [Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge)
(AGPL-3.0, facts only, commit `53e478e`) agrees, it is said: its Sony support has been used with
real headphones, so it confirms what the app's code says. Nothing here has been tried with
PairFree on real earbuds yet; a device report from any model helps
([device page](../devices/sony.md)).

The data is in `data/sony/`: `commands.json` (the four command tables), `functions.json` (what
the earbuds list they have), `devices.json` (the models PairFree recognises by name),
`eq_presets.json` (the EQ presets) and `test_vectors.json`.

## 1. Two table sets

Sony's app speaks one of two table sets, chosen by the RFCOMM service it opens:

| Set | Service | Sony's package |
|---|---|---|
| 1 | `96CC203E-5068-46ad-B32D-E316F5E069BA` | `message.mdr.v1` |
| 2 | `956C7B26-D49A-4BA8-B03F-B17D393CB6E2` | `message.mdr.v2` |

It tries set 2's service first, then set 1's. PairFree opens set 2's where the phone lists it
for the device, else set 1's; where the phone lists neither, the model's table set from
`devices.json`, else set 2. Gadgetbridge picks the service the same way.

Each set has two tables. Table 1 holds nearly everything; table 2 holds the voice guidance,
peripherals and, in set 2, more. The two sets number some commands alike (connect, noise
control, EQ, audio and system) and others differently (the battery is `10` in set 1 and `22` in
set 2). `commands.json` has every command of all four tables.

The earbuds' first reply also says the set (section 3): Sony's app reads it as 4 bytes in set 1
and 8 in set 2, and Gadgetbridge picks its protocol by that length. PairFree follows the reply.

## 2. Frames and acknowledgements

A frame is:

```
3E  data type  sequence  length (4 bytes, big-endian)  payload  checksum  3C
```

- **Data type:** `0C` for table 1's messages, `0E` for table 2's, `01` for an acknowledgement
  (Sony's `DataType`: DATA_MDR, DATA_MDR_NO2, ACK).
- **Checksum:** the low byte of the sum of every byte from the data type to the payload's end.
- **Escaping:** between `3E` and `3C`, the bytes `3C`, `3D` and `3E` are sent as `3D` followed
  by the byte with bit 4 cleared: `3D 2C`, `3D 2D`, `3D 2E`. A receiver puts bit 4 back.
- **Payload:** the command first, then its parameters. Most commands take a type next, which
  says which of several things the command is about (Sony's "inquired type").

Every message of type `0C` or `0E` is acknowledged at once with an empty frame of type `01`
whose sequence number is the other one: 1 for a message sent with 0, 0 for one sent with 1.
Sony's app holds both acknowledgements as constants (`test_vectors.json`):
`3E 01 01 00 00 00 00 02 3C` and `3E 01 00 00 00 00 00 01 3C`.

A sender keeps one message in flight. It sends it with its current sequence number, waits for
the acknowledgement, and takes the number the acknowledgement carries for the next message.
Sony's app waits 750 ms and sends the same frame again, at most 10 times; PairFree does the
same. A message received with the same number as the one before is a resend: it is
acknowledged again but not handled twice.

## 3. Connecting

PairFree sends, in order, waiting for each acknowledgement:

1. `00 00`, the protocol request. Set 1 answers `01 00` and a 2-byte version; set 2 answers
   `01 00`, a 4-byte version and two enable/disable bytes. Gadgetbridge's notes give real
   replies (`test_vectors.json`), such as `01 00 40 10` from the WH-1000XM3 and
   `01 00 03 00 00 00 00 00` from the WH-1000XM5.
2. `04 01` and `04 02`, the model name and the firmware version. Each answer is `05`, the
   type, a length and that many bytes of UTF-8 text. PairFree shows them as `model_version`.
3. `06 00`, the support functions. The answer is `07 00`, a count, then the functions: one byte
   each in set 1, two in set 2 (the function and its version). `functions.json` names them.
   Set 2 sends `06 00` on table 2 as well, for the functions of its second table (voice guidance
   among them), and answers the same way there.

Then PairFree reads each function it offers (the sections below), and the earbuds send each
change as a notification (`..NTFY..`), whether PairFree, a button or Sony's app made it. The
battery is read again every 30 s.

Sony's app also asks each function's capability; PairFree asks the EQ's (section 6), and those
of automatic power-off and the keys (section 7), whose choices it offers.

## 4. Battery

| | Set 1 | Set 2 |
|---|---|---|
| Ask | `10 type` | `22 type` |
| Reply, notification | `11`, `13` | `23`, `25` |
| One battery | type `00` (function `11`) | `00` (`20`), or `08` with thresholds (`28`) |
| Left and right | `01` (`15`) | `01` (`21`), or `09` (`29`) |
| Case | `02` (`18`) | `02` (`22`), or `0A` (`2A`) |

The reply is the type, then the level (0 to 100) and the charging state for one battery, or
the left's level and charging state, then the right's. Set 2's threshold types add two bytes.
Gadgetbridge reads the left first and leaves out an earbud whose level is 0, which is how a
missing earbud shows; PairFree does the same.

## 5. Noise control (NCASM)

Read with `66 type`; the reply is `67`, a notification `69`, and a change is `68` with the same
layout. The type depends on the function the earbuds list:

| Set | Function | Type | Layout after the type |
|---|---|---|---|
| 1 | `62` NC and ambient sound | `02` | effect, NC setting type, NC value, ambient setting type, voice, level |
| 1 | `61` NC | `01` | NC setting type, NC on (1) or off (0) |
| 1 | `63` ambient sound | `03` | effect, ambient setting type, voice, level |
| 2 | `68` | `15` | changed, on, mode, NC value, voice, level |
| 2 | `6A` | `16` | as `15`; the NC value is dual (2) or single (1) |
| 2 | `6B` | `17` | changed, on, mode, voice, level |
| 2 | `6C` | `18` | as `17`; the mode can also be NcSS (2) |
| 2 | `6D` | `19` | as `17`, then noise adaptation on and its sensitivity |
| 2 | `64` | `13` | changed, on, NC on, voice, level |
| 2 | `67` ambient sound | `22` | changed, on, voice, level |

- **Set 1's effect:** 0 off, 1 on in a reply. A change writes `11` for on, Sony's "adjustment
  complete" (`10` is "adjusting"), as Gadgetbridge does.
- **Set 1's NC setting type:** 0 on/off, where the NC value 1 is noise cancelling and 0 ambient
  sound; or 2 dual/single/off, where 2 is noise cancelling, 1 the single setting (wind noise
  reduction in Sony's app, as Gadgetbridge names it) and 0 ambient sound. Gadgetbridge reads
  both alike.
- **Set 2:** "changed" is 0 while a slider moves and 1 once it settles; PairFree writes 1. The
  mode is 0 noise cancelling, 1 ambient sound. On and off are 1 and 0.
- **Voice** is 1 for focus on voice; **level** is the ambient sound level. Gadgetbridge reads
  levels 0 to 20, and PairFree offers that range.

PairFree writes a change in the layout of the earbuds' own last reply, with only the changed
bytes different. Set 2's `17` is 7 bytes in Sony's app, but Gadgetbridge reads it with 8 from
the WF-1000XM4, laid out like `15`, so PairFree goes by the length. When it turns noise
cancelling on in a layout with an NC value, it writes dual (2) where the value was 0, as
Gadgetbridge does.

Not read: set 2's types `01`, `11`, `12`, `14`, `21` and `30`, whose on/off bytes PairFree does
not know how to show. Earbuds with those get no noise control yet.

On the Home tab, earbuds with both modes get the three-way choice; those with one get a switch.

## 6. Equalizer (EQEBB)

The function `51` (set 1) or `50` (set 2) is an equalizer with presets and a curve of the
user's own; `53` (set 1) or `52` (set 2) one with presets only. PairFree reads it with type `01`
or `03` in set 1, `00` or `02` in set 2, with three requests:

- **`50 type`, the capability:** `51 type`, the number of values, the number of steps, the
  number of presets, then each preset as its id, a name length and the name (often empty).
  Sony's app reads the second byte as the band count and the third as the level steps.
- **`5A type`, the band information:** `5B type`, a count, then each value's kind and a
  2-byte number: `01` Hz, `02` kHz, `10` special, where the number 1 is Clear Bass.
- **`56 type`, the setting:** `57 type`, the preset, a count and the values; `59` is the
  notification.

Values run from 0 to steps - 1, flat in the middle (Sony's app: value - (steps - 1) / 2).
Gadgetbridge reads 21 steps, -10 to +10. Without band information, Sony's app draws six values
as Clear Bass and 400 Hz, 1, 2.5, 6.3 and 16 kHz, and ten as 31 Hz to 16 kHz; PairFree too.

A change is `58 type preset count values`:
- **A preset:** its id and no values (count 0), as Sony's app sends it.
- **The user's curve:** every value, Clear Bass included. Set 1 names no preset (`FF`) with it,
  as Sony's app does; set 2 names Custom (`A0`), as Gadgetbridge does.

The presets' ids are in `eq_presets.json`: `00` Off, `10` to `17` Bright to Speech, `A0`
Custom, `A1` to `A5` the user settings, and others. PairFree offers the presets the earbuds
list, except Custom and the user settings, which stand for the user's own curve. The earbuds
do not report a preset's curve, so none is drawn. Clear Bass is a setting of its own.

Not read: the EBB level (function `52` in set 1, `51` in set 2), the ULT mode and the other
sound effects.

## 7. Other settings

| Setting | Set 1 | Set 2 |
|---|---|---|
| DSEE upscaling | function `E2`; `E6 02`, reply `E7 02 00 value`, change `E8 02 00 value`; 1 auto, 0 off | function `E2`; `E6 01`, reply `E7 01 value`, change `E8 01 value`; 1 auto, 0 off |
| Pause when taken off | function `F3`; `F6 03`, reply `F7 03 00 value`, change `F8 03 00 value`; 1 on | function `F1`; `F6 01`, reply `F7 01 value`, change `F8 01 value`; **0 on**, 1 off |
| Speak-to-chat | function `F5`; `F6 05`, reply `F7 05 01 value`, change `F8 05 01 value`; 1 on | function `FC` (type `0C`) or `F2` (type `02`); reply `F7 type mode preview`, change the same; **0 on**, 1 off |
| Voice guidance | function `39`; table 2: `46 01 01`, reply `47 01 01 value`, change `48 01 01 value`; 1 on | table 2 functions `40` to `45`; table 2: `46 type`, reply `47 type value` (types 1 and 2 add the language), change `48 type value`, and type 2 sends the language back; **0 on** |
| Connection mode | function `E1`; `E6 01`, reply `E7 01 00 value`, change `E8 01 00 value`; 0 sound quality, 1 stable connection | function `E1` (type `00`) or `E3` (type `02`, which Sony's app uses with the LDAC status); `E6 type`, reply `E7 type value`, change `E8 type value`; 0 sound quality, 1 stable, 2 low latency on some |
| Automatic power-off | function `F4`; capability `F0 04`, reply `F1 04 count ids`; `F6 04`, reply `F7 04 01 active time`, change `F8 04 01 active time` | function `24` (POWER type `04`) or `25` (type `05`, with when taken off); capability `20 type`, reply `21 type count ids`; `26 type`, reply `27 type active time`, change `28 type active time` |
| Keys | function `F6`; capability `F0 06`; `F6 06`, reply `F7 06 count presets`, change `F8 06 count presets` | function `F3` (type `03`) or `FE` (type `0E`); capability `F0 type`; `F6 type`, reply `F7 type count presets`, change the same with `F8` |

In set 2 on is 0 and off is 1 for these switches (Sony's `OnOffSettingValue` and
`ProtocolOnOff`), which Gadgetbridge marks as reversed. The speak-to-chat change keeps the
preview byte as the earbuds last reported it.

Set 2's voice guidance has a type per function of table 2: `40` type 0, `41` and `42` type 1,
`44` type 2, `45` type 3 (Sony's `VoiceGuidanceInquiredType`). Types 1 and 2 reply with the
language after the switch; only type 2 sends it back in a change (Sony's app). `42` and `43` add a
volume, which PairFree does not offer. Gadgetbridge writes type 1 the same way.

**Automatic power-off.** The ids are times (0 five minutes, 4 fifteen, 1 thirty, 2 sixty, 3 180),
`10` when taken off and `11` never; set 1 has no fifteen minutes. The capability lists the ids the
model offers. The setting holds the active id and a time: a time is sent as both, and when taken
off or never keeps the time the earbuds last gave, as Sony's app does (Gadgetbridge writes never as
`11 00`).

**Keys.** The capability lists each key (0 left, 1 right, 2 custom, 3 C, 4 and 5 the NC/AMB
button), whether it is a touch sensor or a button, its default and the presets it can take, each
with the actions it then has: set 1 a count and action and function pairs; set 2 a count of pairs,
a count of actions with functions to switch between, the pairs, then each of those as action,
function, count and functions. Type `0E` has one more byte before the keys. The setting holds one
preset per key, in the capability's order. Presets: `00` noise control, `10` volume, `20` playback,
`21` track, `22` playback without the assistant, `30` the phone's voice assistant, `31` Google
Assistant, `32` Alexa, `36` Quick Access, `45` noise control and mic, `47` noise control and
listening mode, `FF` nothing (and more in Sony's `Preset`). PairFree offers those that need nothing
set up elsewhere; Gadgetbridge writes `FF`, `00`, `20` and `10` the same way.

## 8. Never sent

Firmware update (`UPDT_*`), the test command and the logs are marked `never_send` in
`commands.json`, and PairFree's Sony session refuses them. Nor does PairFree send a reset.

## 9. Open questions

- Everything here, on real earbuds: no Sony model has been tried with PairFree.
- The ambient level's real range per model; Sony's app reads it from each type's capability.
- Set 2's noise control types PairFree does not read (section 5).
- Multipoint, and the voice guidance volume.
- Whether every model takes a key, power-off or connection change as Sony's app writes it: no
  recording of the app confirms them yet.
