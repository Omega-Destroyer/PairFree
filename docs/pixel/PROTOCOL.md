# Google Pixel Buds protocol

What PairFree knows of how Google's Pixel Buds are controlled, and where each fact comes from.

Sources:
- **Google's Pixel Buds app** 1.0.990706425 (`com.google.android.apps.wearables.maestro.companion`, versionCode
  10260901), decompiled outside the repo on 2026-10-30. It is obfuscated, and its protobuf-lite classes keep no field
  names; the schemas below were read from the message info each class holds, and the names from the app's writers,
  readers, log strings and preference keys. Where this document names a class, it is the app's obfuscated name in
  that build. Everything here is from the app unless it says otherwise.
- **pbpctrl** (github.com/qzed/pbpctrl, Maximilian Luz, MIT or Apache-2.0, commit 2620367), a Linux tool used with a
  Pixel Buds Pro: the Maestro service, its method names, the HDLC framing and the RPC channels. PairFree's HDLC code
  follows its libmaestro.
- **OpenControl for Pixel Buds Pro 2** (github.com/tedsluis/opencontrolpixelbudspro2, tedsluis, AGPL-3.0, commit
  29ca74d): recordings of a Pixel Buds Pro 2 (firmware release_5.203) and a reading of an older build of the same app.
  AGPL, so facts only: the service and method ids it saw on the wire, the EQ write, field names it confirmed.

No Pixel Buds have been tried with PairFree. The Pixel Buds Pro family's messages have been seen on real earbuds by
both projects above; the Pixel Buds (2020) and A-Series' transport by nobody.

## 1. Transports and finding Pixel Buds

The app opens a classic RFCOMM channel by service UUID (grz, gpk), choosing by the services the earbuds list:

| Service | Byte-reversed form | What | Models |
|---|---|---|---|
| `25e97ff7-24ce-4c4c-8951-f764a708f7b5` | `b5f708a7-64f7-5189-4c4c-ce24f77fe925` | Maestro: Pigweed RPC in HDLC frames ("pigweed internal rfcomm socket") | Pro, Pro 2, 2a |
| `3a046f6d-24d2-7655-6534-0d7ecb759709` | `099775cb-7e0d-3465-5576-d2246d6f043a` | plain frames ("default internal rfcomm socket") | Pixel Buds (2020), A-Series |

With neither listed, the app falls back to Maestro; PairFree uses the model the name gives, else Maestro. Both carry
the same messages (section 4). The earbuds also have Google's Fast Pair message stream (its own RFCOMM service): the
battery and finding the earbuds go there too, but Fast Pair's noise-control write needs a MAC from the account key
Google Play services holds, so PairFree uses Maestro's instead (OpenControl section 4.1).

PairFree lists a paired device as Pixel Buds by either service, else by "Pixel" in the name (data/identify.json);
"Pixel Watch" is not ours. pbpctrl also checks the Class of Device (0x240404 Pro, 0x244404 Pro 2).

## 2. Frames

### 2.1 Maestro (Pro, Pro 2, 2a)

Pigweed's pw_hdlc UI frames (pbpctrl):

`7E` · address · control `03` · data · CRC-32 · `7E`

- The address is a varint whose bytes carry seven bits each from the lowest, shifted left one, the last byte with bit
  0 set. It names the peers: source << 6 | target << 10, four bits each (host 1, case 2, left 3 and right 4 Bluetooth
  core, Maestro A 10, Maestro B 13).
- The CRC is CRC-32 (IEEE, reflected) over address, control and data, little-endian.
- `7D` escapes `7D` and `7E`: `7D` then the byte xor `20`. The service id holds a `7E`, so every frame has an escape.

The data is a pw_rpc packet (`pw.rpc.packet.RpcPacket`): 1 type (0 request, 1 response, 5 server error, 7 server
stream), 2 channel id, 3 service id (fixed32), 4 method id (fixed32), 5 payload, 6 status, 7 call id. Ids are pw_rpc's
65599 hash of the names: the length, plus each character times 65599 to the power of its position plus one (32-bit).
`maestro_pw.Maestro` hashes to `7EDE71EA` and `WriteSetting` to `9E8C9A1D`, the ids OpenControl saw a Pro 2 receive.

The phone reaches the earbuds on one of six channels (pbpctrl): 18, 19, 21 (Maestro A to the case, left, right),
23, 24, 26 (Maestro B to the same). The frame's address is the channel's pair: channel 18 is `00 2B`. pbpctrl, like
the app, sends GetSoftwareInfo on each with call id `FFFFFFFF` and keeps the one that answers. Other calls use call id 0.

### 2.2 Plain frames (2020, A-Series)

The app's own reader and writer (grp): group (1 byte), code (1 byte), length (2 bytes, big-endian), payload. No
checksum, no start byte; a length over 1024 from the earbuds is out of step. Handlers are registered per group (gjh;
grd for the 2020 model, gsb for the A-Series):

| Group | What |
|---|---|
| 135 | device and settings (section 4) |
| 136 | usage statistics (KPI events, gsh): never needed |
| 130 | firmware-update debug (2020) |
| 5 | crash and diagnostic reports (A-Series) |
| 2 | firmware update (gny): codes 1, 5, 7, 8, 13 |

The app's callers name the group plus one (gjv.m sends `i - 1`); the wire has 135.

## 3. Connecting

Maestro (pbpctrl, the app's gnu and its Maestro handlers):
1. GetSoftwareInfo on each channel; the one that answers is used from then on.
2. GetHardwareInfo, SubscribeRuntimeInfo and SubscribeToSettingsChanges, then ReadSetting for each setting.

The battery, the placement and every change made on the earbuds or in Google's app then stream.

Plain frames (gpp, gpd, gpe): code 4, empty, when the channel opens; the earbuds send their software, hardware and
runtime info. Then a read of each setting (the 2020 model's 1 to 12, the A-Series' 1, 3, 5, 7, 8, 10 to 14), the
setup-finished one first. A write is code 9, followed at once by a read of the same field.

PairFree sends one message at a time, waiting up to 1 s for a reply where one comes, and 40 ms between them.

## 4. Messages

The same messages travel both transports; Maestro wraps them in RPC calls, the plain frames send them as group 135
codes.

| Maestro method | Plain code | Message | Fields |
|---|---|---|---|
| GetSoftwareInfo | 2 (from the earbuds) | software info (sgu) | 4 (or 3): 1 case, 2 right, 3 left, each {1, 2 version string}. Field order from pbpctrl. |
| GetHardwareInfo | 5 (from the earbuds) | hardware info (sgo) | 1 device type (section 7), 2 SKU, 4/7 serial numbers (never kept), 5/6 hardware versions |
| SubscribeRuntimeInfo | 6 (from the earbuds) | runtime info (sgr) | 6 (or 4) battery: 1 case, 2 left, 3 right, each {1 level %, 2 state: 1 not charging, 2 charging}; 7 (or 5) in the case: 1 right, 2 left. Order from pbpctrl. |
| ReadSetting | 8 (to the earbuds) | read (sgq) | the field's number, in the family's slot |
| WriteSetting | 9 (to the earbuds) | write (sgv) | the family's settings message, in its slot, with one field set |
| ReadSetting's reply, SubscribeToSettingsChanges | 10 (from the earbuds) | setting value (sgt) | as the write |
| SubscribeToOobeActions | 11 (from the earbuds) | setup gesture (sgp) | not used by PairFree |

The slots: 1 common (sfl: 1 setup mode), 2 the 2020 model's settings (shi), 3 the A-Series' (shn), 4 the Allegro
settings of the Pro, Pro 2 and 2a (sff), 5 another (shp). Maestro answers a read of a setting the earbuds lack with a
non-zero status (pbpctrl: 2); the plain frames, as far as the app shows, not at all.

## 5. Settings

`data/pixel/commands.json` holds each field, its type and the row PairFree shows; the app's settings writer
interface (gnv) has one method per setting, implemented for each family (gok for Allegro, gpd for the 2020 model, gpe
for the A-Series), which ties the three numberings together. never: PairFree never writes it.

### 5.1 Allegro: Pixel Buds Pro, Pro 2, 2a (sff, a 39-way oneof)

| Field | Type | Setting | PairFree |
|---|---|---|---|
| 1 | bool | automatic updates | never |
| 2 | bool | in-ear detection | Device |
| 3 | bool | setup finished | never |
| 4 | bool | gestures (touch controls on) | Controls |
| 5 | bool | diagnostics | never |
| 6 | bool | setup mode | never |
| 7 | message | press and hold: 1 left, 2 right, each {4: {1: action}} | Controls |
| 8, 9, 10 | bool, enum, enum | (9, 10: noise state per earbud count; the app never writes them) | — |
| 11 | bool | multipoint | Device |
| 12 | message | the modes press and hold steps through: 1 noise cancelling, 2 off, 3 transparency, 4 adaptive; the app keeps two on | Controls |
| 13 | enum | noise control: 1 off, 2 noise cancelling, 3 transparency, 4 adaptive | Home |
| 14 | bool | ear tip seal test | never (section 9) |
| 15 | bool | volume EQ | Sound |
| 16 | message | the EQ in use (section 6) | Sound |
| 17 | sint32 | balance, -100 (left) to 100 (right) | Sound |
| 18 | message | the user's saved EQ ("Last saved") | Sound |
| 19 | bool | mono audio | Sound |
| 21 | bool | volume level notifications | Device |
| 22 | bool | conversation detection (the app's "speech detection") | Sound |
| 27 | bool | case sounds: other alerts | Device |
| 28 | bool | case sounds: earbud returned | Device |
| 29 | enum | head gestures: 1 off, 2 on | Controls |
| 32 | bool | an automatic test (CATEGORY_RV_BLOCK_AUTO_TEST) | never |
| 34 | bool | the "phone booth" state, synced by the app | — |
| 36 | bool | sleep mode (follows a Pixel Watch) | — (section 9) |
| 37 | message | noise control during Live Translate: {1 on} | Device |
| 38 | bool | tap to mute during calls | Controls |
| 20, 23–26, 30, 31, 33, 35, 39 | | not named by the app's code | — |

The app's balance slider also turns mono on past ±50; PairFree writes the balance alone.

Press and hold's actions (gtp, mapped to the wire by gqd): 1 check notifications, 2 previous, 3 next, 4 play/pause
(pbpctrl), 5 noise control, 6 digital assistant, 7 Live Translate. The app offers noise control, the digital assistant
and Live Translate (iaw, hng, guy) and writes one side at a time. Translate, Duo and a playlist take a byte form in
the action's field 2 instead (`01 v 00`: v 1 Translate, 2 Duo, 4 a playlist); the app no longer offers them, and
PairFree shows them as set in Google's app.

### 5.2 Pixel Buds (2020) (shi)

| Field | Type | Setting | PairFree |
|---|---|---|---|
| 1 | bool | in-ear detection | Device |
| 2, 3, 7 | bool | read by the app, never written | — |
| 4 | message | touch actions, two slots per earbud (1 left, 2 right, each {1 slot one, 2 slot two}) | — (below) |
| 5 | bool | Adaptive Sound (the app's "noise detection") | Sound |
| 6 | bool | sharing detection ("shared mode"); needs in-ear detection | Device |
| 8 | bool | setup finished | never |
| 9 | message | attention alerts: 1 on, 2 a baby crying, 3 a dog barking, 4 sirens | Sound |
| 10 | bool | diagnostics | never |
| 11 | bool | gestures (touch controls on) | Controls |
| 12 | enum | the EQ: 0 default, 1 bass boost | Sound |

The app writes an alert sound with the alerts on ({1 on, n: value}) and turning them off as {1 off}. Its touch screen
writes only the Pro family's press and hold; the 2020 model's two slots are written only with what is already there,
so PairFree, which offers only what Google's app offers, leaves them alone.

### 5.3 A-Series (shn)

| Field | Type | Setting | PairFree |
|---|---|---|---|
| 1 | bool | in-ear detection | Device |
| 3, 7 | bool | read by the app, never written | — |
| 5 | bool | Adaptive Sound | Sound |
| 8 | bool | setup finished | never |
| 10 | bool | diagnostics | never |
| 11 | bool | gestures | Controls |
| 12 | enum | the EQ: 0 default, 1 bass boost | Sound |
| 13 | bool | loudness compensation | Sound |
| 14 | int32 | the bass level: the app's slider runs from -1 to 4 and sends its value plus 1 | Sound |

## 6. The EQ (Allegro fields 16 and 18)

Five floats, 1 low bass, 2 bass, 3 mid, 4 treble, 5 upper treble, in dB from -6 to 6 (the app's user_eq_range 12);
the app's sliders move in 0.5 dB steps (user_eq_increment). OpenControl saw the five-float write on a Pro 2 and
confirmed the band order. A preset is written as its curve (gol), so presets and the user's curve are the same
write. Google's presets (gol, and its EQ function's own description):

| Preset | Low bass | Bass | Mid | Treble | Upper treble |
|---|---|---|---|---|---|
| Default | 0 | 0 | 0 | 0 | 0 |
| Heavy bass | 5 | 3 | 0 | 0 | 0 |
| Light bass | -5 | -1.5 | 0 | 0 | 0 |
| Balanced | -3.5 | 0.5 | 1 | -1 | 2.5 |
| Vocal boost | -1 | 0 | 4 | 2 | 0 |
| Clarity | -2 | 0 | 2 | 3 | 5 |

Field 18 is the curve the user saved, Google's "Last saved". PairFree's EQ screen moves in whole dB, so a half-step
curve from Google's app shows rounded until it is changed in PairFree; presets go out exactly. The app names the bands,
not their frequencies.

## 7. Models and features

The device type (hardware info field 1) names the model (ifw, and OpenControl's reading of the code names):

| Type | Code name | Model | Family | Transport |
|---|---|---|---|---|
| 1 | Presto | Pixel Buds (the app's default name; by elimination the 2020 model) | Presto | plain |
| 2 | Stretto | Pixel Buds A-Series | Stretto | plain |
| 4, 5 | Allegro | Pixel Buds Pro | Allegro | Maestro |
| 6 | Largo | Pixel Buds Pro 2 | Allegro | Maestro |
| 9 | AG12AG22 | Pixel Buds 2a | Allegro | Maestro |

The app bundles a feature list per device type with each feature's minimum firmware, as the default of the flag
`MaestroDeviceConfig__all_devices_configs` (devices.json `features`; pmv, read with gxl). Its feature ids have no names
in the code; a few can be placed by where the app checks them (31 press and hold's Live Translate option, 41 and 32
Live Translate, 42 the digital assistant, 24 cleaning reminders), the rest not. PairFree does not need them: the
earbuds answer only for what they have. Adaptive noise control is offered on the Pro 2, and on any model that reports
the adaptive state.

## 8. Never sent

Group 2, 5, 130 and 136 frames on the plain transport (firmware update, crash reports, update debug, statistics);
firmware update on Maestro; and writes of automatic updates, setup finished, setup mode, diagnostics, the seal test
and the automatic test. `PixelSession.allowed` checks every message.

## 9. Left out, and why

- **Finding the earbuds** goes through Fast Pair's message stream, a second channel PairFree does not open.
- **The ear tip seal test** (Allegro 14, and the EartipFitTest service): how its result comes back was not traced.
- **Spatial audio and head tracking** are Android's own settings (the app calls `Spatializer`), which only system
  apps may change.
- **Sleep mode** (36) follows a Pixel Watch's sleep state, which the app syncs from the watch.
- **The experimental features** ("Feature A", which needs LE Audio) and the phone booth state (34): not named well
  enough to show.
- **The Dosimeter, HeadGesture, Multipoint and JitterBuffer services** (hearing exposure, head gesture events,
  forcing a multipoint switch, buffering): not settings.

## 10. Open questions

- Everything here, on real earbuds with PairFree; above all the plain transport of the 2020 model and the A-Series,
  which no recording shows: whether the earbuds send their info after code 4, and how they answer a read they lack.
- Which device type 5 is (the app names it Pixel Buds Pro and treats it as type 4).
- The battery's and firmware's order of left, right and case (pbpctrl's, which this follows, says the firmware's
  "might not be correct").
- Whether another app holding the Maestro channel (Google's own, or Play services) keeps PairFree out.
- The settings fields the app never names (section 5.1).
