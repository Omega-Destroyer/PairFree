# Samsung Galaxy Buds protocol

**Status:** not tried on any earbuds yet; nobody on the project owns a pair. Everything below
comes from the source of [GalaxyBudsClient](https://github.com/timschneeb/GalaxyBudsClient) by
Tim Schneeberger (commit `754b6fd`, 2026-10-01), an open-source app for every Galaxy Buds model,
licensed GPLv3. Its author worked the protocol out from Samsung's own apps and the earbuds'
replies; its repo also has notes of its own (`GalaxyBudsRFCommProtocol.md`). PairFree's state
parser reproduces every state reply in GalaxyBudsClient's tests (7 models,
`data/samsung/test_vectors.json`). Each model's row on the [device page](../devices/samsung.md)
says whether it is among them.

`data/samsung/messages.json` is the message table and `data/samsung/devices.json` the model
table; `generateSamsungTables` (in `android/protocol/build.gradle.kts`) turns them into
`SamsungMessage` and the model list. `tools/import_galaxybudsclient.py PATH` refreshes the model
table from a local checkout of GalaxyBudsClient.

## 1. Transport

Classic Bluetooth serial (RFCOMM), on a service that depends on the model:

| Models | Service |
|---|---|
| Galaxy Buds (2019) | `00001102-0000-1000-8000-00805f9b34fd` |
| Buds+, Buds Live, Buds Pro | `00001101-0000-1000-8000-00805f9b34fb` (standard SPP) |
| Buds2 and later | `2e73a4ad-332d-41fc-90e2-16bef06523f2` |

Each model's service is `service_uuid` in `data/samsung/devices.json`.

## 2. Frames

| Framing | Layout |
|---|---|
| Standard (every model but the 2019 Buds) | `FD`, length (2 bytes, little-endian), id, payload, CRC (2 bytes), `DD` |
| Legacy (Galaxy Buds 2019) | `FE`, type (0 request, 1 response), length (1 byte), id, payload, CRC (2 bytes), `EE` |

- **Length:** counts the id, the payload and the CRC. In the standard framing only bits 0 to 9
  are the length; bits 12 and 13 (`0x1000`, `0x2000`) are flags for a fragment and for a
  response. The earbuds do not set them reliably, so PairFree ignores them, as GalaxyBudsClient
  does.
- **CRC:** CRC-16/XMODEM (polynomial `1021`, start 0, no reflection) over the id and the
  payload, sent low byte first.
- **Message id:** one byte. GalaxyBudsClient and `messages.json` write it in decimal.

Examples:
- noise control on (message 120, payload `01`): `FD 04 00 78 01 D1 91 DD`;
- version request (message 99, no payload): `FD 03 00 63 C5 5C DD`.

The Gradle tests build both.

A read can hold part of a frame or several. PairFree keeps bytes until a whole frame is there.
It drops a frame whose CRC or end byte is wrong and looks again from the next start byte.

## 3. Messages

`data/samsung/messages.json` lists every message PairFree reads or sends, with its payload,
and the ones it never sends. The settings messages are mostly one byte, 1 on and 0 off; the
exceptions are in the table's notes. Two are inverted, 0 on and 1 off: seamless connection
(175) and call path control (110).

After most settings the earbuds answer with message 66 (universal acknowledgement): the id of
the message it answers, then the value as set. PairFree takes that value into the state. After
a change made by touch, the earbuds send an update instead:

| Message | Update |
|---|---|
| 119 | the noise control mode |
| 129 | ambient sound on or off (Buds, Buds+) |
| 155 | noise cancelling on or off (Buds Live) |
| 132 | the ambient sound volume |
| 145 | the touch lock |

## 4. Identifying the model and connecting

The earbuds do not say their model in a way PairFree uses. GalaxyBudsClient reads a model id
from the device's service list, but that code has a bug and is not used. PairFree matches the
paired device's Bluetooth name, as GalaxyBudsClient also does. A name matches when it contains
the model's `base_name`. Longer base names are tried first, so "Buds3 Pro" wins over "Buds3".
The Buds2 and later can be renamed in Samsung's app, and a renamed pair is not found.

There is no handshake. After the channel opens:

1. The earbuds send their whole state (message 97, section 5) unasked.
2. PairFree answers with the manager info, message 136: payload `01`, then the phone (`01`
   Samsung, `02` other), then its Android SDK version. Samsung's app sends it the same way.
3. PairFree asks for the version, message 99 with no payload. Models before the Buds3
   answer with each earbud's hardware and software version; PairFree spells the software
   version as Samsung writes it, such as `R177XXU0AXF3`. The Buds3 and later do not answer, so
   PairFree shows no firmware for them.

## 5. State

The earbuds send the full state (message 97, extended status) on connecting and after some
changes, and a shorter battery update (message 96) when a battery or an earbud's place changes.

- **Revision:** the full state's first byte. A model's layout grows with it.
- **Features by revision:** GalaxyBudsClient lists, for each model, the features it has and
  the lowest revision that has each (`features` in `data/samsung/devices.json`).
- **Common start:** every layout starts with the revision, the ear type, the left and right
  batteries (percent), whether the earbuds are linked, and the main connection.
- **Where layouts part:** after those bytes the layout depends on the model. The 2019 Buds
  are one layout, the Buds+ another. The Buds Live, Pro, Buds2 and Buds2 Pro each add to a
  common middle part. The Buds FE and later read on in order after byte 33.

`SamsungStatus.kt` is the layout, ported line by line from GalaxyBudsClient's
`ExtendedStatusUpdateDecoder`, with its field names in snake case so its tests can be checked
one for one. A reply shorter than its layout keeps what it holds, as GalaxyBudsClient's guarded
reads do.

- **Battery:** an earbud whose place reads 0 is not connected, and PairFree shows no level for
  it. Newer models also report whether each earbud and the case are charging.
- **Unchecked layouts:** GalaxyBudsClient's tests hold no state for the Buds Core, Buds3,
  Buds3 Pro, Buds3 FE, Buds4 or Buds4 Pro. Their layouts are its code alone. It reads the Buds
  Core and Buds3 FE as the Buds FE, as the likeliest layout.

## 6. Equalizer

Every model has Normal (EQ off) and five presets the earbuds apply themselves: bass boost,
soft, dynamic, clear and treble boost. Message 134 selects one:

| Models | Payload |
|---|---|
| Galaxy Buds (2019) | on (1 or 0), then the preset + 5 |
| Every other model | `00` for Normal, else the preset + 1 |

The state reports the mode the same way. Samsung does not publish the presets' curves, and the
earbuds do not report them, so PairFree shows the preset's name and draws no curve.

Models with the `custom_equalizer` feature (the Buds3 and later) also take a nine-band custom
curve:
- **Bands:** 63, 125, 250, 500, 1000, 2000, 4000, 8000 and 16000 Hz.
- **Message 137:** the band count (`09`), then each band's gain from −10 to 10, one signed byte
  each.
- **Message 134:** then sent with `07`, which selects the custom curve.

The earbuds forget the custom curve when they turn off. PairFree keeps the last one it set and
sends it again once per connection, as GalaxyBudsClient does.

**Unknown:** what one step of the custom curve is in dB. GalaxyBudsClient shows the steps as
they are. Message 105 asks for every preset's bands and the custom bands; PairFree does not send
it yet.

## 7. Touch controls

- **Touch lock (message 144):**
  - older models: one byte, 1 locked.
  - models with `advanced_touch_lock`: touch on, then single tap, double tap, triple tap and
    touch and hold, each 1 on.
  - models with `advanced_touch_lock_for_calls`: double tap and touch and hold during calls
    follow.

  PairFree sends the whole set on every change.
- **What touch and hold does (message 146):** the left earbud's option byte, then the right's.
  The bytes differ by model (`touch_hold` in `data/samsung/devices.json`).
  - **Custom:** runs an action on the phone that Samsung's app sets. PairFree shows it when the
    earbuds hold it, but does not offer it.
- **Which modes touch and hold switches between (message 121):** applies while it is set to
  noise controls. The state holds it in one byte.
  - **Before the Buds3:** bit 0 off, 1 ambient, 2 noise cancelling, and on models with
    `noise_control_mode_dual_side` bits 4 to 6 the same for the left earbud. Message 121
    sends three bytes per side (noise cancelling, ambient, off; left first on dual-side
    models). PairFree offers the three pairs Samsung's app offers: noise cancelling and
    ambient, noise cancelling and off, ambient and off.
  - **The Buds3 and later:** a newer bit layout with adaptive among the modes (bit 0 ambient,
    1 adaptive, 2 off, 3 noise cancelling, bits 4 to 7 the same for the left). PairFree reads
    it that way. GalaxyBudsClient's parser has this branch but gates it on a feature no model
    lists, and its writer for these models leaves the ambient bit out. PairFree does not offer
    the choice on these models until a user's report shows the right write.
- **Double tap the edge for volume (message 149):** one byte, 1 on.

## 8. Other settings, find and the fit test

| Setting | Message | Payload |
|---|---|---|
| Noise control mode | 120 | 0 off, 1 noise cancelling, 2 ambient sound, 3 adaptive |
| Ambient sound (Buds, Buds+) | 128 | 1 on, 0 off |
| Noise cancelling (Buds Live) | 152 | 1 on, 0 off |
| Relieve pressure (Buds Live) | 159 | 1 on, 0 off |
| Ambient volume | 132 | a step from 0 to the model's highest (`max_ambient_volume`) |
| Extra loud ambient | 150 | adds one volume step |
| Voice focus (2019 Buds) | 133 | 1 on, 0 off |
| Noise cancelling level | 131 | 1 high, 0 low |
| Noise controls with one earbud | 111 | 1 on, 0 off |
| Voice detect | 122 | 1 on, 0 off |
| Voice detect wait | 123 | 0 for 5 s, 1 for 10 s, 2 for 15 s |
| Ambient sound per earbud | 130 | on, left volume, right volume, tone (0 to 4) |
| Left-right balance | 143 | 0 to 32, 16 is the middle |
| Extra clear calls | 72 | 1 on, 0 off |
| Ambient sound during calls | 139 | 1 on, 0 off |
| In-ear detection for calls | 110 | 0 on, 1 off |
| Seamless connection | 175 | 0 on, 1 off |
| Wake the voice assistant by voice | 151 | 1 on, 0 off |

PairFree does not offer adaptive noise control. The state has no adaptive bit that
GalaxyBudsClient reads, and no model in GalaxyBudsClient lists the feature.

- **Extra loud:** when turned on, PairFree moves the ambient volume to the new highest step, as
  GalaxyBudsClient does.
- **Find:** message 160 starts the beeping and 161 stops it. Models with
  `fmg_ring_while_wearing` use 166 to start, which also beeps while worn. While finding,
  message 162 mutes the left or right earbud (1 muted), and the earbuds confirm with 163.
- **Fit test:** message 157 with `01` starts it and `00` stops it. Message 158 gives the
  result, left then right: 1 good, 0 poor, 2 the test failed. Noise control stays as it was.

## 9. Codecs

There is no codec message. On Galaxy Buds, as on other earbuds, the phone picks the codec;
Samsung phones also offer Samsung's own codec with them, and their Bluetooth settings decide.
GalaxyBudsClient has no codec setting, and nothing in the protocol can switch one, so PairFree
offers none.

## 10. What PairFree never sends

`never_send` in `messages.json` marks these, and `SamsungSession.frames` drops them:
- factory reset (80), reboot (82) and power off (83);
- the hidden and debug modes (18, 19, 32) and the self test (171);
- every firmware update message (176 to 178, 187 to 190).

Some settings PairFree reads but does not offer:
- **Game mode** (message 135): every model but the 2019 Buds lists it, but GalaxyBudsClient
  never sends it.
- **Spatial audio** (`SET_SPATIAL_AUDIO`): GalaxyBudsClient turns it on only while it streams
  the head-tracking sensor to its own page.
- **Also read only:** the hearing test values and the Buds3's newer settings (adaptive volume,
  sound adaptation, siren detection, voice commands, lighting control). GalaxyBudsClient reads
  these from the state but has no write for them.

## 11. Open questions

Each of these needs a user's device report:

- The layouts of the Buds Core, Buds3, Buds3 Pro, Buds3 FE, Buds4 and Buds4 Pro (section 5).
- The write for which modes touch and hold switches between on the Buds3 and later
  (section 7).
- What one step of the custom EQ is in dB (section 6).
- How to read the firmware of the Buds3 and later, which do not answer the version request
  (section 4).
