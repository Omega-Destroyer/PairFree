# Bose protocol (BMAP)

How PairFree talks to Bose headphones and earbuds. The messages come from Bose's own app, Bose
(`com.bose.bosemusic`) 13.3.4, read on 2026-10-14 outside the repo; none of its code is in it.
Its BMAP library keeps Bose's names, which this document uses. What works without Bose's cloud
comes from [bosectl](https://github.com/aaronsb/bosectl) by aaronsb (MIT, commit `70570b2`),
which was tried on the QC Ultra Headphones (2nd Gen), QC Ultra Earbuds (2nd Gen), QuietComfort
Headphones, QC35, QC Earbuds, QC45 and Ultra Open Earbuds. Where the two differ, Bose's app
wins. The standby timer's choices and its "never" come from
[based-connect](https://github.com/Denton-L/based-connect) (Denton Liu, GPL-3.0: facts only),
which controls the QC35. Nothing here has been tried with PairFree yet; a device report from any model helps
([device page](../devices/bose.md)).

The data is in `data/bose/`: `commands.json` (every function block and function, the operators
and the error codes), `devices.json` (the models by product id) and `test_vectors.json`
(replies bosectl read from real devices, and requests from its notes).

## 1. Transport and finding Bose devices

Classic Bluetooth serial on the standard SPP UUID `00001101-0000-1000-8000-00805F9B34FB`, which
is what Bose's app opens (`SppConnectionManager`); the phone's service lookup leads it to the
right channel (bosectl saw channel 2 on newer models and 8 on older ones).

Bose's app knows a device as Bose's by its services (`BluetoothService.isBoseDevice`): a 16-bit
UUID `FEBE` or `FDD2`, or a UUID starting `F4C93A79-D04F-4565` ("Bragi"). PairFree uses the
same test on the services the phone lists for a paired device, else "Bose" in its name.

## 2. Packets

```
block  function  flags  length  payload
```

- **flags:** device (bits 6 and 7), port (bits 4 and 5) and operator (bits 0 to 3). A phone
  sends device and port 0.
- **operator:** 0 SET, 1 GET, 2 SETGET, 3 STATUS, 4 ERROR, 5 START, 6 RESULT, 7 PROCESSING.
- **length:** one byte, the payload's length.

There is no start byte and no checksum: packets follow each other, and a reader splits them by
length. An ERROR's payload is an error code (`commands.json`): 3 the block is not there, 4 the
function is not there, 5 the operator is not allowed (on newer models, it needs Bose's
sign-in), 6 the data is wrong, 8 a passing failure.

PairFree sends one request at a time and waits for the answer to it (any packet for the same
function) before the next, as bosectl does, at most 2 s. Devices also send STATUS packets
unasked when something changes.

## 3. What needs Bose's sign-in

Bose's app signs in to the device through Bose's cloud (block 18, Authentication). PairFree
never contacts Bose's servers. bosectl found what works without it: every GET, SETGET on the
Settings block and on AudioModes, and START on AudioModes. Plain SET needs the sign-in. Bose's
own app writes the settings below with SETGET too (each packet class defaults to it), so
PairFree uses SETGET and START only. A device may still refuse a write (bosectl saw the QC Ultra
Headphones refuse Settings' CNC); PairFree then reads the setting again and shows what the
device holds.

## 4. Connecting

PairFree reads, in order:

| Read | Function | Answer |
|---|---|---|
| BMAP version | GET 0.1 | text; bosectl sends it first, as the QC35 and QC45 need |
| Product | GET 0.3 | product id (2 bytes, big-endian), variant: `40 82 01` is the QC Ultra Headphones (2nd Gen) |
| Firmware | GET 0.5 | text, such as `8.2.20+g34cf029` |
| Battery | GET 2.2 | section 5 |
| Settings | GET 1.3, 1.4, 1.5, 1.6, 1.7, 1.9, 1.10, 1.11, 1.24, 1.27 | section 6 |
| Spatial audio | GET 5.15 | section 6 |
| Mode | GET 31.3, GET 31.10, START 31.1 | section 7 |

A function the device does not have answers with an ERROR, and PairFree offers nothing for it.
The battery is read again every 30 s.

## 5. Battery (Status 2.2)

Records of four bytes: the level (0 to 100), the minutes left (2 bytes, `FFFF` when unknown) and
the component: 0 the only battery, 1 the right earbud, 2 the left, 3 the case (Bose's
`ComponentId`). One battery may come in fewer than four bytes, with no component. bosectl saw
earbuds also send a component 4 for both earbuds together; PairFree leaves it aside.

## 6. Settings (block 1)

Each is read with GET and written with SETGET; the device answers a write with a STATUS of the
new value.

| Function | Answer | Write | Notes |
|---|---|---|---|
| 1.3 voice prompts | a byte: language (bits 0 to 4), on (bit 5), default (bit 6), can be switched (bit 7); then the languages it has (4 bytes) and two bytes for battery announcements | the byte with on and the language | PairFree offers the switch only where bit 7 is set |
| 1.4 standby timer | minutes, 0 never; Bose's app reads a reply of three bytes or more as the first byte (low) and the third (high) | minutes in one byte up to 255, else two, the low one first | PairFree offers based-connect's choices for the QC35 (5, 20, 40, 60 and 180 minutes, never) and the device's own value; Bose's app also names 10 minutes and 24 hours |
| 1.5 CNC | number of steps, the step, flags (bit 0 on) | step, on | the noise cancelling level |
| 1.6 ANR | the mode: 0 off, 1 high, 2 wind, 3 low | the mode | the QC35's noise cancelling |
| 1.7 range control | four bytes per band: minimum, maximum, value (signed), band (0 bass, 1 mid, 2 treble, 3 low mid, 4 high mid) | value, band | the tone control |
| 1.9 buttons | the button (Bose's `ConfigurableButtonId`), its gesture, its action (`ActionButtonMode`), a 4-byte mask of the actions it can take, then one of those it cannot take now and the reasons | button, gesture, action | the masks as Bose's app reads them: bit 0 is in the last byte. bosectl reads them the other way round; Bose's reading puts the device's own action among them on bosectl's QC Ultra 2 capture (`80 09 0E 00 09 40 02`: voice assistant, disabled, Spotify Tap, immersive audio). PairFree offers the actions that need no app or account set up |
| 1.10 multipoint | bit 0 on, bit 1 supported, bit 2 can be turned off | 1 or 0 | bosectl reads bit 1 as on; Bose's app does not |
| 1.11 sidetone | kept (bit 0), level (0 off, 1 high, 2 medium, 3 low), the levels it has (a bit each) | kept, level | how much of your own voice you hear in calls |
| 1.24 auto play/pause | 1 on, 0 off | the same | pause when taken off |
| 1.27 auto answer | 1 on, 0 off | the same | |

Spatial audio is AudioManagement 5.15 (Bose's `SpatialAudioModeTypes`): GET answers one byte,
0 off, 1 fixed to the room ("still" in Bose's app), 2 fixed to the head ("motion"); SETGET writes it.

The noise cancelling level: on models with AudioModes' settings (31.10, section 7) PairFree
writes it there, as bosectl does, and the CNC step and that value count the same way in
bosectl. Elsewhere it writes 1.5.

## 7. Listening modes (AudioModes, block 31)

- **31.3 current mode:** GET answers the mode's index. START with the index and a voice prompt
  byte (0 none) switches to it and answers RESULT with the index taken (some models answer
  PROCESSING first).
- **31.1 get all:** START makes the device send every mode's configuration (31.6) as STATUS.
- **31.6 mode configuration:** the index, the voice prompt (2 bytes: Quiet `00 01`, Aware
  `00 02`, Bose's `AudioModesPrompt`), user slot, set up, favourite, the name (32 bytes, zero
  padded), then the mode's settings (44, 47 or 48 bytes in all, by model).
- **31.10 settings:** the noise cancelling level, auto CNC, spatial audio (0 off, 1 room,
  2 head), wind block and ANC, a byte each; SETGET writes all five.

PairFree offers the firmware's modes and the user slots that are set up, by their names, and
switches with START 31.3. It does not change the modes themselves.

## 8. Never sent

Firmware update, factory default, ship mode, reset, power, debug and the Authentication block
are marked `never_send` in `commands.json`, and PairFree's Bose session refuses them.

## 9. Open questions

- Everything here, on real devices with PairFree.
- Which models refuse which SETGET without Bose's sign-in (bosectl: Settings' CNC on the QC
  Ultra Headphones; multipoint not yet tried).
- Whether every model takes the button, standby timer and spatial audio writes as Bose's app sends
  them, and which standby times each model accepts.
- Editing modes.
- A recording of Bose's app: with no start byte or checksum, Bose's packets cannot be picked out
  of a Bluetooth log with any certainty yet.
