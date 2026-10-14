# Soundcore (Anker) protocol

**Status:** confirmed on the owner's Space A40 on 2026-10-13
(`data/soundcore/reports/A3936_06.43.txt`): the transport and service, the frame format and
checksum, and the commands for the state, noise control, touch actions and touch tone. The rest
is unconfirmed. Everything below comes from reading the source of
[OpenSCQ30](https://github.com/Oppzippy/OpenSCQ30) (commit `bc77a29`, 2026-09-26), an
open-source Soundcore app that supports about 40 models, under GPL-3.0-or-later like
SoundPeter. [Gadgetbridge](https://gadgetbridge.org/gadgets/headphones/anker/) supports eight
Soundcore models as well. The official Soundcore app (6.4.0) cannot be read: its code is
encrypted by a commercial protector (ijiami), and the unencrypted Flutter part holds no protocol.
So the next steps are trying this on the owner's Space A40 (section 6) and recording what the
Soundcore app sends to it.

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
service list (section 4).

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
| `04 81` to `04 84` | Set button (touch) actions |
| `04 86` | Set touch lock |
| `06 01` | Sound modes (noise control) update |
| `06 81` | Set sound modes |
| `06 82` | Set which modes the noise-control button cycles through |
| `0B 01` | Dual connections: the connected devices |
| `0B 81`, `0B 82`, `0B 83`, `0B 84` | Dual connections: disconnect a device, connect a device, forget a device, turn the feature on or off |
| `10 82` | Set low battery prompt |
| `10 86` | Set sound leak compensation |
| `20 81`, `20 82` | Limit high volume |

Seen on the A40 but not in OpenSCQ30: the Soundcore app reads usage statistics with `05 01`
right after the state (little-endian counters), then clears them with `05 81`, which has no
body; the next `05 01` reads all zero. SoundPeter has no use for either and sends neither.

On the A40 every setting is acknowledged with the same command and no body, and after `06 81`
the earbuds also send the new sound modes as `06 01`. They send the battery (`01 03`) and the
charging state (`01 04`: left, right, 1 charging) unasked.

**How this differs from SoundPEATS:** SoundPEATS earbuds have one read per feature, and a
feature counts as present once its read answers. Soundcore earbuds answer `01 01` with one block
holding the whole state. That block's layout depends on the model, so the app must know the
model before it can read anything. The EQ is part of that block, so unlike on SoundPEATS
earbuds it can be read back.

## 4. Identifying the model

OpenSCQ30 does not detect the model: the user picks it when adding the earbuds. SoundPeter finds
SoundPEATS models by itself (from the firmware code), and should do the same for Soundcore.

On the A40 two things name the model: the vendor service UUID ends in `3936`, and the serial
number in the state reply starts with `3936`. The rest of the serial number is the earbuds'
Bluetooth address, so reports mask it. Its Bluetooth name is "Soundcore Space A40". Candidates
in general:
- the vendor service UUID (section 1);
- the serial number (`01 05`);
- the Bluetooth name;
- a BLE advertisement.

The official app's code is encrypted, so the answer has to come from the earbuds and from a
recording of the Soundcore app.

## 5. Adding models nobody on the project owns

OpenSCQ30 asks owners of an unsupported model to copy the raw state reply from its
development mode. SoundPeter can do better with the same recording it uses for SoundPEATS: a
bug report's Bluetooth log, made while the official Soundcore app is in use, holds the state
reply, every setting the app sends and its EQ curves.

## 6. Space A40 (A3936)

The owner has a pair, so this is the first Soundcore model to support. Everything here is from
OpenSCQ30's `a3936` module.

**Connecting.** OpenSCQ30 sends `01 01` and parses the reply as below. If multipoint is on, it
then reads the connected devices with `0B 01`.

**State reply, in order:**
1. TWS status (whether both earbuds are connected)
2. Battery of each earbud
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
13. Case battery
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
`00` for each earbud. The state reply carries the EQ too, so SoundPeter can show the curve
the earbuds hold.

**3D sound:** `02 86`, 1 on and 0 off. OpenSCQ30 knows the command (surround sound) but does not
offer it on the A40. Which state byte holds it is not known yet.

**Still to check:** the byte sizes of the remaining state fields (OpenSCQ30's shared structures
hold them), and a state read with 3D sound on and with a custom EQ.

## 7. Open questions

1. How the app identifies the model (section 4).
2. The full model list, and whether each model's features are in the app or come from a server.
3. The EQ format of each model, and its presets.
4. The state layout of each model.
5. Anything the app sends on connecting, before it asks for the state.
