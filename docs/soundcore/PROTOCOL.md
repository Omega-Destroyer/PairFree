# Soundcore (Anker) protocol

**Status:** confirmed on the owner's Space A40 on 2026-10-13
(`data/soundcore/reports/A3936_06.43.txt`): the transport and service, the frame format and
checksum, and the commands for the state, noise control, the EQ, touch actions and touch tone. The
rest is unconfirmed. Everything below comes from reading the source of
[OpenSCQ30](https://github.com/Oppzippy/OpenSCQ30) (commit `bc77a29`, 2026-09-26), an
open-source Soundcore app that supports about 40 models, under GPL-3.0-or-later like
PairFree. [Gadgetbridge](https://gadgetbridge.org/gadgets/headphones/anker/) supports eight
Soundcore models as well. The official Soundcore app cannot be read from 3.9 on (6.4.0, 5.0.24,
4.0.82 and 3.9.13 were tried): its code is encrypted by a commercial protector (ijiami), and the
unencrypted Flutter part holds no protocol. Version 2.9.9 is not encrypted, and its parsers were read
on 2026-10-29 (section 10). Each model's state layout (section 5) was read from OpenSCQ30's parsers
on 2026-10-13 and checked against the replies in its tests and, where 2.9.9 has the model, against the
app's own parser.

## 1. Transport

Classic Bluetooth serial (RFCOMM), like SoundPEATS. OpenSCQ30 connects to a vendor service
when the earbuds offer one: its UUID is `0cf12d31-fac3-4553-bd80-d6832e7?????`, where the last
20 bits vary. Otherwise it uses the standard SPP UUID `00001101-0000-1000-8000-00805F9B34FB`.

Two models get a fixed service instead:
- the A3116 (Motion+) uses `0cf12d31-fac3-4553-bd80-d6832e7b3116`, which ends in its model
  number;
- the A3930 (Liberty 2 Pro+) uses SPP.

The Space A40 (A3936) offers `0cf12d31-fac3-4553-bd80-d6832e7b3936` (RFCOMM channel 5), so
both known vendor UUIDs end in `b` and the model number: the earbuds name their model in their
service list (section 4). The Soundcore app (2.9.9) opens this form for most of its models, with
four hex digits after the `b` that are the model number or, for a variant, another id (`392B` for
a variant of the A3926, `393C` for the A3935W); for its older earbuds and headphones (A3909, A3910,
A3913, A3027 to A3030, A3930, A3931, A3935, A3951) it opens the standard serial port.

## 2. Frames

| Direction | Header | Then |
|---|---|---|
| To the earbuds | `08 EE 00 00 00` | command (2 bytes), total length (2 bytes, little-endian), body, checksum |
| From the earbuds | `09 FF 00 00 01` | the same |

- **Total length:** counts every byte of the frame, from the header to the checksum.
- **Checksum:** the sum of all the bytes before it, modulo 256.
- **Exception:** OpenSCQ30 sends and expects no checksum for the A3116.

Example, request state: `08 EE 00 00 00 01 01 0A 00 02`. That's the header, command `01 01`,
length 10, no body, and checksum `02`.

## 3. Commands OpenSCQ30 uses

Reads and updates have bit 7 of the second byte clear (`01 01`, `06 01`), and settings
have it set (`01 81`, `06 81`). This is only a pattern in the numbers below; the official app
has not confirmed it.

| Command | Use in OpenSCQ30 |
|---|---|
| `01 01` | Request state. The reply carries the whole state, laid out differently per model |
| `01 02` | TWS status: whether both earbuds are connected |
| `01 03` | Battery level |
| `01 04` | Battery charging |
| `01 05` | Serial number and firmware version |
| `01 0F` | Chinese voice prompt |
| `01 10` | Request voice prompt |
| `01 11` | Gaming mode update |
| `01 7F` | LDAC state |
| `01 81` | Set auto play/pause, or set wearing detection, depending on the model |
| `01 83` | Set touch tone |
| `01 86` | Set auto power off |
| `01 87` | Set gaming mode |
| `01 8C` | Set wearing tone |
| `02 81` | Set equalizer |
| `02 83` | Set equalizer with DRC |
| `02 86` | Set surround sound |
| `03 86`, `03 87` | Set equalizer and custom HearID |
| `04 81` | Set one touch action (section 9) |
| `04 82` | Reset the touch actions to the model's defaults |
| `04 83` | Turn one touch action on or off |
| `04 84` | Set every touch action at once |
| `04 86` | Set touch lock |
| `04 87` | Set one touch action used during calls (the A3330) |
| `06 01` | Sound modes (noise control) update |
| `06 81` | Set sound modes |
| `06 82` | Set which modes the noise-control button cycles through |
| `0B 01` | Dual connections: the connected devices |
| `0B 81`, `0B 82`, `0B 83`, `0B 84` | Dual connections: disconnect a device, connect a device, forget a device, turn the feature on or off |
| `10 82` | Set low battery prompt |
| `10 86` | Set sound leak compensation |
| `10 94` | Turn every touch control off (1) or on (0) |
| `20 81`, `20 82` | Limit high volume |

Seen on the A40 but not in OpenSCQ30: the Soundcore app reads usage statistics with `05 01`
right after the state (little-endian counters), then clears them with `05 81`, which has no
body; the next `05 01` reads all zero. PairFree has no use for either and sends neither. On the
Sleep A20 the reply starts with the model number, the serial number and the Bluetooth address, so
`05 01` is marked private and a recording leaves its body out. The Sleep A20 adds its own commands
(section 11).

On the A40 every setting is acknowledged with the same command and no body, and after `06 81`
the earbuds also send the new sound modes as `06 01`. They send the battery (`01 03`) and the
charging state (`01 04`: left, right, 1 charging) unasked.

**How this differs from SoundPEATS:** SoundPEATS earbuds have one read per feature, and a
feature counts as present once its read answers. Soundcore earbuds answer `01 01` with one block
holding the whole state. That block's layout depends on the model, so the app must know the
model before it can read anything. The EQ is part of that block, so unlike on SoundPEATS
earbuds it can be read back.

## 4. Identifying the model

OpenSCQ30 does not detect the model: the user picks it when adding the earbuds. PairFree finds
SoundPEATS models by itself (from the firmware code), and does the same for Soundcore.

On the A40 two things name the model: the vendor service UUID ends in `3936`, and the serial
number in the state reply starts with `3936`. The rest of the serial number is the earbuds'
Bluetooth address, so reports mask it. Its Bluetooth name is "Soundcore Space A40".

PairFree (`SoundcoreModels.identify`) takes the model from the vendor service UUID when it
names a model in `data/soundcore/devices.json` (`b` and the model number, as on the A3116 and
A3936, or an id a model lists in `vendor_ids`), and otherwise from the Bluetooth name, matched without case against the names
OpenSCQ30 gives each model. Only the A40's name is confirmed to be the one the earbuds use. A
pair renamed by its owner, on a model whose service does not name it, is not found.

The service to open is chosen as OpenSCQ30 chooses it (`soundcore/common/device.rs`): the vendor
service when the earbuds have one, else the standard serial port, except on the two models where
OpenSCQ30 fixes it (the A3116 always uses its vendor service, the A3930 the serial port) and on the
A3910 and A3913, not in OpenSCQ30, which use the serial port as the Soundcore app does.

## 5. Every model

`data/soundcore/devices.json` holds each model's reply to the state request (`01 01`) as
`[field, bytes]` in order, read from the model's parser in OpenSCQ30. `skip` marks bytes
PairFree does not use; `optional:` marks fields only some firmware sends. Each model also has
the scale of its battery levels: percent = (value + offset) * 100 / max, as OpenSCQ30 reads its
`level_offset` ("if 1, 0 will represent 1": an offset of 1 makes 0 the first of ten steps and 9 the
last; PairFree 0.2.0 subtracted it, and an AeroClip's report showed full earbuds as 8 of 10), so most report fifths
(max 5), some tenths or percent, and a level outside 0 to max (`FF`) means that earbud is away.
PairFree shows a scale of steps as bars, one per step, as the Soundcore app does (3 of 5 bars, not
60%), and a scale of 100 as a percentage; the A40's earbuds report fifths and its case percent.
`SoundcoreLayout` and `SoundcoreState` read any model's reply from that table.

Every noise control block starts with the noise mode (0 noise cancelling, 1 transparency,
2 normal; the Liberty 4 Pro adds 3 for airplane mode), and every model sets it with `06 81` and
the whole block, as the A40 does. LDAC (`01 FF`), two devices (`0B 84`) and game mode (`01 87`)
are one-byte switches on every model that has them, except game mode on the Liberty 4 NC (A3947)
and Liberty 5 (A3957), which use `10 85`.

Quirks from OpenSCQ30:
- The Motion+ (A3116) sends no checksum, and its length field leaves the checksum out.
- The P30i (A3959) sends the game mode byte on all firmware but has game mode only from
  firmware 01.60 on, on both earbuds (`min_firmware`).
- In a dual firmware field an earbud that is away sends bytes that are not a version, so the
  version is taken from the other earbud.
- The battery update (`01 03`) holds one level per earbud, then two bytes OpenSCQ30 does not
  name. On the A40 the third is the case in percent (section 6); on other models PairFree
  ignores them.

How far each layout is checked:
- **Tested:** the A40 (section 6).
- **Checked:** 21 models whose replies OpenSCQ30's tests hold, 37 replies in all, many from its
  users' bug reports. They are in `data/soundcore/test_vectors.json` (`openscq30_states`) with
  the serial numbers replaced by zeros, and PairFree's tests read every one: the firmware is a
  version, the batteries are 0 to 100 or away, the noise mode is known.
- **Checked against the Soundcore app:** 15 of OpenSCQ30's models whose layout the app's own
  parser (2.9.9) reads the same way, and 8 models OpenSCQ30 lacks that the app reads with another
  model's code (section 10). They are marked `soundcore_app` in `data/soundcore/devices.json`.
- **Not tested:** the rest, read from OpenSCQ30's parsers alone. A device report from one of them
  (the state reply as hex) checks its layout.

The Soundcore device page ([docs/devices/soundcore.md](../devices/soundcore.md)) shows which is which.

### Adding models nobody on the project owns

OpenSCQ30 asks owners of an unsupported model to copy the raw state reply from its
development mode. PairFree's Copy device report gives the same reply, with the serial number
masked, and says how long it was against the length the layout expects. A bug report's
Bluetooth log, made while the official Soundcore app is in use, gives more: the state reply,
every setting the app sends and its EQ curves.

## 6. Space A40 (A3936)

The owner has a pair, so this is the first Soundcore model to support. Everything here is from
OpenSCQ30's `a3936` module.

**Connecting.** OpenSCQ30 sends `01 01` and parses the reply as below. If multipoint is on, it
then reads the connected devices with `0B 01`.

**State reply, in order:**
1. TWS status (whether both earbuds are connected)
2. Battery of each earbud, 0 to 5 (5 while Fast Pair showed 91% and 92%), then whether each is charging
3. Firmware version of each earbud
4. Serial number
5. EQ: a preset number (`0xFEFE` for a custom curve), then 10 bands for each earbud
6. Age range, for HearID
7. Custom HearID, with the favourite music genre at the end
8. One byte OpenSCQ30 does not know
9. Touch actions: single, double and long press on each side
10. Which modes the noise-control gesture cycles through
11. Sound modes, 6 bytes: ambient mode (normal, noise cancelling, transparency), manual or
    adaptive noise cancelling, transparency mode, noise-cancelling mode, wind-noise reduction,
    adaptive sensitivity
12. Touch tone
13. Case battery: a percentage on the A40 (it read 15 while Google Fast Pair showed the case at 15%)
14. Colour
15. LDAC
16. Multipoint on or off
17. Auto power-off
18. Gaming mode

**What OpenSCQ30 offers on it:** noise control with its modes, the EQ with HearID, touch actions
(and resetting them), the noise-control cycle, multipoint and its device list, auto power-off
in half-hour steps, LDAC, touch tone, gaming mode, both earbuds' and the case's battery, and the
serial number and firmware versions.

**Confirmed on the owner's pair:** the vendor service and checksum (sections 1 and 2), how it
names its model (section 4), the start of the state reply (TWS status, battery, both firmware
versions, the serial number), the sound modes block (6 bytes, byte 0: 0 noise cancelling,
1 transparency, 2 normal), and the touch-action and touch-tone commands.

**Settings tried from PairFree (2026-10-13).** Game mode (`01 87`), LDAC (`01 FF`) and two
devices (`0B 84`) each take one byte, 1 on and 0 off, as OpenSCQ30 sends them. Every LDAC or
two-devices change makes the earbuds restart their connection, so the control channel closes
and opens again a few seconds later. LDAC on turns two devices off and LDAC off turns it back
on; with LDAC off, two devices can be switched by itself (`ldac_excludes` in
`data/soundcore/devices.json`). With two devices on, the earbuds send their connected-device
list (`0B 01`, 42 bytes per device) unasked; it holds other devices' names and addresses.

**EQ.** The Soundcore app writes the whole curve with `03 87` (92 bytes):

| Bytes | What |
|---|---|
| 2 | Preset number, little-endian; `FE FE` for a custom curve |
| 2 | Favourite music genre (HearID) |
| 20 | 10 bands for each earbud, left first: one byte each, `0x78` = 0 dB, 0.1 dB per step |
| 3 | Gender, age range, one unknown byte |
| 20 | HearID curve |
| 4 | HearID time |
| 1 | HearID type |
| 20 | Custom HearID curve |
| 20 | The 10 bands again for each earbud, after OpenSCQ30's `apply_drc()` (`volume_adjustments.rs`) |

The app's sliders went from `3C` (-6 dB) to `B4` (+6 dB). A preset write sends 9 bands and a
`00` for each earbud. The state reply carries the EQ too, so PairFree can show the curve
the earbuds hold.

**3D sound:** `02 86`, 1 on and 0 off. OpenSCQ30 knows the command (surround sound) but does not
offer it on the A40. Which state byte holds it is not known yet.

**Touch controls.** The recording holds four touch-action writes and two touch-tone writes, and
PairFree rebuilds each frame from the recorded state (section 9). One thing differs from OpenSCQ30:
turning the right single press off sent `04 83` for the right side only, and the next state still
had the left single press on. OpenSCQ30 turns both sides' single, double and long presses off
together on the A40; PairFree turns off only the side the user picked, as the Soundcore app does.

**Still to check:** the byte sizes of the remaining state fields (OpenSCQ30's shared structures
hold them), and a state read with 3D sound on and with a custom EQ.

## 7. Open questions

1. Whether each model's Bluetooth name is the one OpenSCQ30 gives it (section 4).
2. Models newer than OpenSCQ30's list, and whether each model's features are in the app or come
   from a server.
3. Whether the 26 models whose EQ write no test pins take what OpenSCQ30 sends (section 8), and
   whether the Soundcore app sends gender `FF` and empty HearID curves ending in `00` on models
   other than the A40, as it does there.
4. The state layout of the models with no reply to check it against and not in the readable
   Soundcore app (section 5), and of the report-only models (section 10).
5. Anything the app sends on connecting, before it asks for the state.
6. What the two bytes after the earbud levels in the battery update (`01 03`) are on models
   other than the A40, where the first is the case (section 5).
7. Whether other models' case levels are in percent too. OpenSCQ30 reads the A40's as 0 to 10,
   yet the A40 reports percent (section 6), so its other case scales may be wrong as well.
8. Whether the models where OpenSCQ30 turns both sides off together (section 9) really need it,
   or treat each side alone as the A40 does.
9. What touch lock (`04 86`, the Liberty 4 NC) does on the earbuds.
10. The Sleep A20's touch tone, low battery prompt, prompts, alarms and sleep mode, and what its
    `01 A9` does (section 11).

## 8. Equalizer

Every model's EQ is in `data/soundcore/devices.json` (`eq`) and its presets in
`data/soundcore/eq_presets.json`, read from OpenSCQ30's equalizer module, its shared EQ packets
and each model's own (commit `bc77a29`). `SoundcoreEqualizer` reads and writes them.

**Values.** One byte per band: the value minus the scale's minimum. Most models use tenths of a
dB from -12.0 to +13.4 dB (`0x78` = 0 dB). The Liberty 2 Pro (A3909) uses whole dB from -12 to
+12, the Motion+ (A3116) whole dB from -6 to +6 over 9 bands. A curve whose first byte is `FF` is
not there (an earbud away, or no HearID).

**What a choice sends.** A preset sends its number and its own curve to every earbud. A custom
curve sends the custom number (`FE FE` on most models) and the 8 visible bands, then fixed values
for the bands past them (`00 00` for bands 9 and 10). Both earbuds always get the same curve. The
Space A40 recordings confirm both (section 6). In OpenSCQ30 the P20i/P25i/R50i (A3949) takes no
custom curve, and the C30i (A3330) has a custom curve and one preset.

**Packets.**

| Format (`eq.write`) | Command | Body | Models |
|---|---|---|---|
| plain (and a3909) | `02 81` | preset, each earbud's curve | 11 |
| drc | `02 83` | preset, each curve, each curve after correction | 9 |
| hear_id | `03 87` (`03 86` when the age range is `FF`) | preset, music genre, curves, gender, age, `00`, HearID curve, time, type, custom HearID curve, corrected curves | 6 |
| a3035, a3040, a3062, a3947, a3955, interleaved | `03 87` | variations of hear_id; see `SoundcoreEqualizer` | 9 |
| a3388, a3876 | `02 83` | the A3388: preset and both curves, no correction; the A3876 and the Sleep A20 (A6611): preset, one curve and its correction | 3 |
| a3116 | `02 81` preset, `02 83` curve | the Motion+ sends only what changed | 1 |

"Correction" is OpenSCQ30's `apply_drc()`, a fixed 8 by 8 mix of the bands (`SoundcoreEq.drc`).
HearID models pass the HearID block from the state back with HearID turned off, as OpenSCQ30 does;
each model's block is described part by part (`eq.hear_id.parts`), since five layouts exist.

**Where the Space A40 differs from OpenSCQ30.** The Soundcore app sends gender `FF` where
OpenSCQ30 sends `00`, and an empty HearID curve as `FF` bytes ending in `00` where OpenSCQ30 sends
all `FF` (`eq.gender`, `eq.hear_id.empty_curve_ends_in_zero`). PairFree follows the recordings.

**How far each model is checked.** The A40: its 7 recorded writes. 11 models: OpenSCQ30's own EQ
tests, 14 cases of a state reply, a choice and the exact packet (`openscq30_eq` in
`data/soundcore/test_vectors.json`; the R50i case was matched against the Soundcore app). Its
tests of the shared packets (`openscq30_eq_formats`) cover the formats the other models use. The
other 26 models' writes come from OpenSCQ30's code alone.

**Reading it back.** The state holds the preset number and the curves (the A3909 only the preset
number; its curve comes from the preset). So the screen can show what the earbuds hold, unlike
SoundPEATS earbuds.

## 9. Touch controls

Every model's touch controls are in `data/soundcore/devices.json` (`buttons`) and their action
sets in `data/soundcore/button_actions.json`, read from OpenSCQ30's button configuration module
and each model's settings (commit `bc77a29`), and for the Sleep A20 from a recording of the Soundcore
app (section 11). 38 models have them. `SoundcoreButtons` reads and
writes them, and `SoundcoreTouch` adds the switches below.

**In the state.** The `buttons` field holds one entry per button (a side and a press: single,
double, triple, long, or on the A3954 a slide up or down), in an order each model fixes. An
entry is an enabled byte, which some models do not have, then an action byte. Either byte can be
one value, or two values in one byte: the low four bits for when both earbuds are in and the high
four for when one earbud is used alone. Which applies comes from the TWS status, the second byte
of the state (1: both in), which the earbuds also send unasked as `01 02` (host, both in). An
action of `F` means off on models without an enabled byte; an enabled value counts as on only
when it is 1. The A3954's slides come in a second field much further on (`slide_buttons`).

**Actions.** The common set: 0 volume up, 1 volume down, 2 previous, 3 next, 4 noise control,
5 voice assistant, 6 play or pause; some models add 9 game mode (the A40's double and long
press), 14 lights (the V20i, A3876), 7 bass up (the three headphones with one button, A3035,
A3040, A3062), or have their own sets (the Liberty 2 Pro, the C50i, the Sleep A30 with 8 noise
cancelling and 13 change mode, and the C30i during calls: 0 decline, 1 answer).

**What a change sends.** For one button: `04 81` (side, button number, the whole action byte)
when the action changed, then `04 83` (side, button number, the whole enabled byte) when the
enabled byte changed. Button numbers: 0 double, 1 long, 2 single, 5 triple, 6 slide up, 7 slide
down. Only the four bits for how the earbuds are worn now change. A change that touches several
buttons goes as one `04 84` with every button's bytes, on the models that take it (the
A3926, A3933 and A3939, A3945, A3951, and the models the Soundcore app reads like the A3926 or A3933). The C30i's (A3330) actions during calls use `04 87`
instead of `04 81`. `04 82` (no body) restores the defaults; OpenSCQ30 then asks for the state.

**Turning a button off.** Each press says whether it can be: never, one side at a time, or both
sides together (OpenSCQ30 turns the other side off too, and back on when one side gets an action
again). On the A40 the recordings show one side at a time (section 6), so PairFree uses that there.
Models whose `ignore_enabled` is set turn a button off with action `F` and set its enabled byte
on, as OpenSCQ30 does; on the others the enabled byte turns it off and the action stays.

**Switches.** Touch tone `01 83` (21 models), touch lock `04 86` (the Liberty 4 NC, A3947), and
every touch control off with `10 94`, 1 off and 0 on (the C30i, AeroClip and C50i; the Sleep A30
the same command for its tap controls). Each is one byte in the state. The noise-control action's cycle is `06 82` with one
byte: 1 noise cancelling, 2 transparency, 4 normal; on the Sport X20 (A3968) the low four bits
for both earbuds in and the high four for one. PairFree offers each only where OpenSCQ30 does and the state
holds it. The A3933 and A3939 send their touch tone, wearing detection, game mode and case battery
in an optional block at the end of the state, which only some firmware sends.

**How far each model is checked.** The A40: its recorded writes, rebuilt frame for frame
(`a3936_buttons` in `data/soundcore/test_vectors.json`). The A3330, A3926, A3948 and A3959:
OpenSCQ30's tests of their touch writes (`openscq30_buttons`), with the C50i's and Liberty 4
NC's switches. The rest come from OpenSCQ30's code alone; PairFree offers them anyway (owner,
2026-10-14), since a wrong touch setting can be changed back in the app.

## 10. The Soundcore app 2.9.9

Soundcore 2.9.9 (`com.oceanwing.soundcore`) is the last version whose code is not encrypted. It was
decompiled outside the repo on 2026-10-29 and its state parsers compared with `devices.json`; nothing
of its code is in the repo. It reads the earbuds with three parsers, each for a family of models,
and the Liberty 3 Pro with a fourth:

- **A3933 family** (A3933, A3939, A3945, and the A3982, A3983, A3992 and A3993 with their EU
  versions): the same layouts as OpenSCQ30's. Battery levels run from 0 to 5.
- **A3951 family** (A3951, A3931, A3935, A3027, A3028, A3029, A3030, A3025, A3930, A3033, A3926,
  A3927): the same layouts as OpenSCQ30's, except that the A3926, A3927 and the A3926's variant read
  their levels from 0 to 4 (4 is full, and anything higher reads as 4), where OpenSCQ30 reads them
  out of 5. Those three have no noise control in the app, and their touch actions leave out the
  noise mode. The Life Q20 (A3025) has the noise modes but none of their sub-modes.
- **A3909 family** (A3909, A3913, A3910): the A3909's layout. The A3909 and A3913 offer volume,
  track, voice assistant and nothing else as touch actions, with no way to turn a touch off. The
  Liberty Air 2 (A3910) has its own parser after the HearID: for each touch a switch and an action
  (left double, left long, right double, right long), a switch the app calls transparency, then on
  newer firmware wear detection and single taps (switch and action, left then right). It adds play
  and pause to the actions, and each touch is turned off by its own switch: `04 83` with side, touch
  (0 double, 1 long, 2 single) and 0 or 1, and set with `04 81` with side, touch and action.
- **Liberty 3 Pro** (A3952): the app finds the noise-mode bytes after the touch controls by the
  length in byte 103 of the reply. When it is 18, as in OpenSCQ30's test reply, its offsets are the
  ones in `devices.json`.

From this, the A3927, A3982, A3983, A3992, A3993, A3913, A3025 and A3910 have layouts (`soundcore_app`
with `same_as` names the model whose layout they share), and are no longer report only. Not
covered: the Life NC (A3201), which the app asks for each setting with a message of its own, an older
protocol than the one state reply PairFree reads, and the Life Q20+ (A3045), for which 2.9.9 has no
code. Both stay report only, with the models newer than 2.9.9.

## 11. Sleep A20 (A6611)

OpenSCQ30's library does not support the Sleep A20. Its device faker holds one state reply (131 bytes)
with what each byte is, as far as OpenSCQ30 knows, and a user's recording of the Soundcore app (GitHub
issue #8, firmware 03.22; `data/soundcore/reports/A6611_03.22.txt`) confirms these parts of it, every
write rebuilt byte for byte in `data/soundcore/test_vectors.json` (`a6611_recording`):

| Bytes | What | Write |
|---|---|---|
| 0-29 | host, both in, each earbud's battery (tenths, offset 1), firmware, serial | |
| 30-51 | EQ: preset (2 bytes), the left curve (10), `FF` for the right | `02 83`: preset, one curve and its correction, as the V20i (section 8) |
| 53-56 | taps: left double, right double, left triple, right triple | `04 81`: side, press (0 double, 5 triple), action (section 9) |
| 67-70 | sleep timer: 1 on or 2 off, the minutes (2 bytes, little-endian), a byte sent back as it is | `15 85`, the same 4 bytes; `15 03` reads them |
| 126 | the case's battery (tenths, offset 1) | |

- **EQ.** The presets are OpenSCQ30's second common set (`type_2`): its Rock ends +4 and +5 dB, which the
  recording shows, where the first set's ends +3 and +3. A preset sends bands 9 and 10 as `78 00`, the
  custom curve as `78 78`, as on the other models.
- **Taps.** No enabled byte; `F` turns a tap off. The app writes the action byte's high four bits as the
  state had them and the new action in the low four, as PairFree does with both earbuds in, and the earbuds
  then store the low four bits in both halves. Actions: 0 volume up, 1 volume down, 2 previous, 3 next,
  6 play or pause, 13 change mode (Bluetooth or sleep sounds), the Sleep A30's set without noise cancelling.
  The recording used 0, 2, 6, 13 and `F`; 1 is the left triple tap's default, and 3 is taken from the
  Sleep A30. A reset to the defaults (`04 82`) was not recorded, so PairFree does not offer one.
- **Sleep timer.** Off keeps the minutes. The recording set 120 and 30 minutes; PairFree offers 30
  minutes to 8 hours in half hours, from OpenSCQ30's note that the app goes up to 8 hours.

Also in the recording, and not used by PairFree: `01 A6`, ten bytes the app sends now and then, which
start with the phone's time (seconds since 1970, little-endian); `14 01`, the alarm list (empty here;
the faker gives five alarms of seven bytes); `05 03`, `05 83`, `15 01`, `15 88`, `15 06` and `15 07`,
the sleep logs and their transfer, which hold the user's sleep times; and `01 A9` with 1 and then 0,
which the earbuds answer with `01 14` and `13 03`, and which the user's list of changes would explain.

Not yet confirmed, so not offered: the alarm (58-61), sleep mode (65), touch tone (75), the low
battery prompt (76), and the prompts for auto power-off and for switching modes (124-125). Each needs
a recording in which the user changes it, with the change written down.
