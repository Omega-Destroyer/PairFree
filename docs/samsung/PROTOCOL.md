# Samsung Galaxy Buds protocol

**Status:** not tried on any earbuds yet; nobody on the project owns a pair. Everything below
comes from the source of [GalaxyBudsClient](https://github.com/timschneeb/GalaxyBudsClient) by
Tim Schneeberger (commit `754b6fd`, 2026-10-01), an open-source app for every Galaxy Buds model,
licensed GPLv3. Its author worked the protocol out from Samsung's own apps and the earbuds'
replies; its repo also has notes of its own (`GalaxyBudsRFCommProtocol.md`). PairFree's state
parser reproduces every state reply in GalaxyBudsClient's tests (7 models,
`data/samsung/test_vectors.json`). Each model's row on the [device page](../devices/samsung.md)
says whether it is among them.

For the Buds4 family PairFree follows Samsung's own Galaxy Buds plugin instead
(`com.samsung.accessory.budsunitemgr` 9.0.10, read on 2026-10-14 outside the repo; none of its
code is in it). It covers the Galaxy Buds4 and Buds4 Pro and three models GalaxyBudsClient does
not have, and differs from GalaxyBudsClient for them in places: section 12.

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

A paired device is matched from the service list Android keeps for it (its SDP UUIDs, which an
app reads with the connect permission alone, no scan) and its Bluetooth name
(`SamsungModels.identify`), in this order:

1. **The model id.** A Buds2 or later publishes a service
   `d908aab5-7a90-4cbe-8641-86a553db` followed by four hex digits: its model id, which is the
   colour code (device id) of its model and colour, such as `0146` (326) for a grey Buds2 Pro.
   Samsung's plugin reads it this way (`BluetoothUuidUtil.getDeviceIdFromUUID`), and its table
   (`nr.b`) maps each code to a model; it agrees with GalaxyBudsClient's `DeviceIds` (the
   `colors` of each model in `data/samsung/devices.json`) and adds the Buds On's 363 and 364.
   GalaxyBudsClient has the same lookup, but its parsing reads the prefix instead of the id, so
   it never matches. This finds a renamed pair.
2. **The name**, as GalaxyBudsClient matches it: it contains the model's `base_name` or one of the
   names Samsung's plugin matches, longer ones first, so "Buds3 Pro" wins over "Buds3". While
   Android has a service list for the device, the model's own service (section 1) must be on it:
   other brands' names hold base names too (the CMF Buds Pro 2 matched "Buds Pro"), and
   PairFree could not talk to such a device as Galaxy Buds anyway.
3. **Samsung's fallback** for a pair with the Buds2-and-later service but no id and no name:
   a Buds2 Pro when it also has hands-free (`111E`) and LE Audio (`184E`), otherwise a Buds2.

The device report says which way the model was found. Which models publish the id is not known
yet: none has been tried.

There is no handshake. After the channel opens:

1. The earbuds send their whole state (message 97, section 5) unasked.
2. PairFree answers with the manager info, message 136: payload `01`, then the phone (`01`
   Samsung, `02` other), then its Android SDK version. Samsung's app sends it the same way.
3. PairFree asks for the version, message 99 with no payload. Models before the Buds3
   answer with each earbud's hardware and software version; PairFree spells the software
   version as Samsung writes it, such as `R177XXU0AXF3`. The Buds3 and later do not answer.
   PairFree asks them for the long version (104) instead, which the Buds4 family answers with
   the software versions as text (section 12); whether the Buds3 models answer it is not known.

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

The state reports the mode the same way. Samsung does not publish the presets' curves. Message
105 asks the earbuds for them; PairFree sends it to the Buds4 family, as Samsung's plugin does,
and draws the presets from the reply (section 12). For other models it shows the preset's name
and draws no curve.

Models with the `custom_equalizer` feature (the Buds3 and later) also take a nine-band custom
curve:
- **Bands:** 63, 125, 250, 500, 1000, 2000, 4000, 8000 and 16000 Hz.
- **Message 137:** the band count (`09`), then each band's gain from −10 to 10, one signed byte
  each.
- **Message 134:** then sent with `07`, which selects the custom curve. On the Buds4 family the
  custom curve is `06`, selected first, then sent (section 12).

The earbuds forget the custom curve when they turn off. PairFree keeps the last one it set and
sends it again once per connection, as GalaxyBudsClient does.

**Unknown:** what one step of the custom curve is in dB. GalaxyBudsClient and Samsung's plugin
both handle the steps as they are.

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
    lists, and its writer for these models leaves the ambient bit out. Samsung's plugin writes it
    for the Buds4 family as two bytes, left then right, in the same bits (section 12), so PairFree
    offers the choice there. For the Buds3 models it waits for a user's report.
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
  never sends it. Samsung's plugin shows why: on the Buds4 family it is not a setting but the
  phone's state, which the app reports by itself (section 12).
- **Spatial audio** (`SET_SPATIAL_AUDIO`): GalaxyBudsClient turns it on only while it streams
  the head-tracking sensor to its own page.
- **Also read only:** the hearing test values and the Buds3's newer settings (adaptive volume,
  sound adaptation, siren detection, voice commands, lighting control). GalaxyBudsClient reads
  these from the state but has no write for them.

## 11. Open questions

Each of these needs a user's device report:

- The layouts of the Buds Core, Buds3, Buds3 Pro and Buds3 FE (section 5); for the Buds4 family
  Samsung's plugin gives one, still to be checked against a real reply (section 12).
- The write for which modes touch and hold switches between on the Buds3 models (section 7).
- What one step of the custom EQ is in dB (section 6).
- Whether the Buds3 models answer the long version request (section 4).
- The Galaxy Buds On's features, which the plugin does not list.

## 12. The Buds4 family, from Samsung's own plugin

Samsung's Galaxy Buds plugin 9.0.10 (`com.samsung.accessory.budsunitemgr`) covers these models
(`samsung_app` in `data/samsung/devices.json`):

| Model | Number | Codename |
|---|---|---|
| Galaxy Buds4 | SM-R540 | handel |
| Galaxy Buds4 Pro | SM-R640 | bach |
| Galaxy Buds On | SM-U600 | able |
| Samsung H1 (a headset) | SM-U700 | h1 |
| Samsung T1 | SM-R545 | t1 |

It matches each by two names, such as "Galaxy H1" and "Samsung H1". All use the `2e73a4ad-...`
service and the standard frames. Where it differs from GalaxyBudsClient, PairFree follows it.

**State (message 97).** Bytes 0 to 39 are as in section 5, except that byte 19, seamless
connection, is 1 for on. From byte 40:

| Byte | Field | Byte | Field |
|---|---|---|---|
| 40 | noise reduction amplify | 50 | adaptive volume |
| 41 | auto adjust sound | 51 | quick launch (advanced) |
| 42 | head tracking | 52 | siren detection |
| 43 | charging: 0x10 left, 0x04 right, 0x01 case | 53 | adapt sound |
| 44 | extra clear calls | 54-56 | voice command version a.b.c |
| 45 | extra high ambient | 57 | voice boost |
| 46 | pause when an earbud is taken out | 58 | volume normalisation |
| 47 | voice commands | 59 | digital assistant |
| 48 | voice command language | | |
| 49 | adjust sound fit | | |

GalaxyBudsClient reads 40 to 50 differently (a hearing test value at 40, no auto adjust sound,
and lighting at 50), so its charging, extra clear calls and extra high ambient land on the wrong
bytes for these models. Then each model adds its own: the Buds4 Pro loud noise reduction and the
adaptive response level, and from revision 5 the noise monitor and the hearing aid's WDRC for
calls and music; the H1 and T1 an auto power-off timer; the Buds On gestures and the automatic
left/right switch.

**Writes that differ:**
- **121, which modes touch and hold switches between:** left, then right, each 1 ambient,
  2 adaptive, 4 off, 8 noise cancelling, as in the state's byte 21.
- **130, ambient sound per earbud:** on, left level, right level, conversation boost, noise
  reduction amplify, its level. There is no tone.
- **144, touch lock:** touch on, single, double, triple, hold, double tap and hold during calls,
  swipe, then 1 and 1.
- **146, touch and hold:** the left and right codes, then each side's assistant: 1 Google,
  2 another, 0 when the option is not the assistant. Codes: 1 Bixby, 2 noise control, 3 the
  digital assistant, 6 volume, 5 anything else.
- **175, seamless connection:** 1 for on.
- **134 and 137, the custom curve:** it is equalizer number 6 (Balanced 0, then bass boost,
  smooth, dynamic, clear, treble boost, custom). The plugin selects 6, then sends the bands.

**Connecting.** After the first state the plugin answers message 97 and sends 34, 41 (the serial
numbers), 104 (the long version), 38, 118, the manager info (136, as in section 4), 105 (the EQ
curves) and 167 (the time). PairFree sends the manager info, 104 and 105.

- **104, long version:** the reply holds each earbud's hardware version (a byte each), each one's
  software version as 20 bytes of text, left first, then two bytes.
- **105, EQ curves:** the reply holds the preset count, the band count, then each preset's bands
  as signed steps. With six presets it leaves out the flat Balanced curve, which goes first.

**New settings PairFree offers where the model has them:** pause when an earbud is taken out
(108), adaptive volume (217), siren detection (222), each 1 on; and conversation boost, inside
message 130. Their acknowledgements (66) carry the value as set.

Also, from the plugin's per-model lists (`SUPPORT_HEAD_TRACKING`, `ADJUST_SOUND_FIT`, `BOOST_DIALOGUE`,
`NECK_STRETCH_REMINDER`, `AUTO_POWER_OFF_TIMER`) and its writes (`kk/f`):

| Setting | State byte | Write | Models |
|---|---|---|---|
| 360 Audio and head tracking | 35, 42 | 124: 360 audio, head tracking | Buds4, Buds4 Pro, H1 |
| Adjust sound to fit | 49 (on when not 0) | 218: 1 on, 0 off | Buds4, Buds4 Pro |
| Voice boost and volume normalisation | 57, 58 | 106: voice boost, volume normalisation | Buds4, Buds4 Pro, H1 |
| Neck stretch reminder | 38 | 201: 1 on | Buds4, Buds4 Pro |
| Loud noise reduction | the tail's first byte | 73: the adaptive response level as reported, loud noise reduction, 0 | Buds4 Pro |
| Auto power-off timer | the tail's byte, minutes | 107: 30, 60 or 120 | H1 |

Byte 38 is the reminder's switch (the plugin's neck stretch field), not a calibration. The T1's state
has a timer byte too, but its feature list has no timer, so PairFree does not offer it. Head
tracking shows only while 360 Audio is on. The plugin also changes the phone's own volume
normalisation (SoundAlive) on Samsung phones; PairFree sets only the earbuds'.

Left out: the voice command language and set-up (100, 153), Bixby's wake word (102), the adapt sound
and hearing aid settings (a hearing test, 184), the Buds On's gestures and its left/right switch (no
write found), and game mode (135), which reports the phone's state rather than setting anything.

**Features.** The plugin keeps a feature list per model. It gives the Buds4 no fit test,
ambient volume, noise cancelling level, voice detect or ambient sound per earbud, which
GalaxyBudsClient's list has. PairFree follows the plugin's lists, mapped to its own feature names.

**Game mode (135)** is the phone reporting its state, a short: bit 0 the phone in use, bit 4 a
game running. PairFree does not send it.

Also in the plugin, not used by PairFree: adaptive response level (0x49), the Bixby keyword
(0x66), voice boost and volume normalisation (0x6A), the auto power-off timer (0x6B), spatial
audio and head tracking (0x7C), the hearing aid's WDRC (0xB8), neck stretch (0xC9), adjust sound
fit (0xDA) and self diagnostics (0xEA, never sent).
