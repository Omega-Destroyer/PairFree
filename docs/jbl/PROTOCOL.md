# JBL protocol (Harman commands)

How PairFree talks to JBL headphones and earbuds. The commands come from JBL's own app, JBL
Headphones (`jbl.stc.com`) 5.37.10, read on 2026-10-15 outside the repo (its Airoha side, section
10, on 2026-10-30); none of its code is in it. Class names below say where in that app to look. Two
open-source projects checked it on real devices:

- [JblTray](https://github.com/emperorbug46/JblTray) by emperorbug46 (MIT, commit `5ccf929`)
  controls a Tune Flex 2 from Windows. Its notes and code give the noise control frames it
  recorded from the earbuds over Bluetooth LE, the battery read, and that the earbuds' LE side
  answers on their classic address.
- [JBL-Headphones-App](https://github.com/FajkPes/JBL-Headphones-App) by FajkPes (commit
  `3f383d1`, no licence: its recorded bytes are used as facts, none of its code) reads and
  writes a Tune 720BT over Web Bluetooth. Its notes give that headphone's replies to the
  status, battery and voice prompt reads.

Only the Airoha models' RACE commands have been tried with PairFree: a user's Live Buds 3 (section 10). The
Harman commands have not; a device report from any model helps ([device page](../devices/jbl.md)).

The data is in `data/jbl/`: `commands.json` (the commands PairFree uses, their values and what
is never sent), `devices.json` (the models PairFree knows, with how each is reached and what
JBL's app shows for it) and `test_vectors.json` (frames JblTray and FajkPes recorded, and
frames built the way JBL's app builds them).

## 1. Models and control families

JBL's app keeps a product list (`assets/product_list.json`) with each model's name, product
ids (pid), chip platform, connection (`BLE`, `BT` or `BR_EDR`) and the control class that
drives it, and a dashboard description per model (`assets/config/dash4/`) naming the cards it
shows. The control classes fall into families (`com.harman.cmdctrl.imp.control.u3`):

| Family | What it speaks | PairFree |
|--------|----------------|----------|
| `bes` (31 classes) | Harman commands (section 2) | Yes, the classes with a dash4 config |
| `protocol3` (7) | Harman commands, settings as feature records | Yes |
| `protocol4` (22) | JBL's newer protocol (section 6) | Yes, with each model's BLE security step (section 7) |
| `airoha` (10), and the older `com.harman.cmdctrl.imp.airoha` (3) | Airoha's RACE commands (section 10) | Yes |
| `lea`: the 3 on `LeaAhBaseControl` | Airoha's RACE, over LE Audio or serial (section 10) | Yes |
| `lea`: the BES ones, `hid` | LE Audio for BES models, USB | No |

The older `com.harman.cmdctrl.imp.bes` classes (Live 220BT to Tour Pro+) have no dash4 config,
so what each model has is not in the app; they are left out until reports say. `devices.json`
lists the models PairFree covers; section 5 says how each model's class sends its settings.

## 2. Frames

```
AA  command  length  payload
```

- **AA** starts every frame (`CmdBase.HEADER_COMMAND`, 170). There is no checksum.
- **length** is one byte, the payload's, except for command `06` (feature records, section
  4.6), whose length is two bytes, low byte first (`Command3`). The custom EQ write (`41`) adds
  a packet count after a one-byte length; PairFree does not send it.
- A reply comes back under its own number for some commands (`11` → `12`, `21` → `22`) and
  under the same number for the rest (`25`, `91`, `06`).
- A write is acknowledged with `AA 00 len <command> <status>` (`HandleParse`, `RET_DEV_ACK`).

JBL's app sends one command at a time and waits up to 1 s for anything from the earbuds before
the next (`HandleSend`, a wait of 1000 ms that any parsed reply ends). PairFree does the same.
Over Bluetooth LE each notification is one frame (`BleDevice.doCommands`).

## 3. Transport and finding the earbuds

**Bluetooth LE** (connection `BLE`, most models): GATT service
`65786365-6c70-6f69-6e74-2e636f6d0000` ("excelpoint.com" in ASCII). JBL's app asks for a
512-byte MTU, subscribes to notifications on `…0001` (descriptor `2902`) and writes each frame
with a response to `…0002` (`Constants`, `GenServiceDiscovery`). JblTray found the Tune Flex 2's
commands go over LE unencrypted, with no handshake needed.

**Serial** (connection `BT`: the Quantum 360, 360X and 360P): the standard SPP UUID
`00001101-0000-1000-8000-00805F9B34FB`, the same frames.

The Airoha models have a serial UUID and an LE service of their own (section 10).

**Finding the LE side.** The earbuds are paired for sound over classic Bluetooth. JBL's app
finds their LE side by scanning (`qi.n`), with two filters: manufacturer data under Harman's
company id 3787 (`0x0ECB`), or service data on `FDDF`. Its "classic" advert holds, little-endian:

| Bytes | What |
|-------|------|
| 0–1 | product id |
| 2 | colour |
| 3–4, 5–6 | CRC of the Bluetooth name of each phone the earbuds are connected to |
| 7–8 | a CRC JBL's app stores as the device's address CRC (`btAddressCrc`) |
| 9 | a flag |

The CRC is CRC-16/ARC (`com.harman.utils.c`), of the name's first 31 bytes, or of its first 50.
JBL's app takes a classic advert whose product id it knows and that carries this phone's name
CRC, so it connects to earbuds already connected to the phone. Its newer "p5" advert (protocol 4
models) holds the CRC of the classic address, which the app compares with the paired device's:
after the product id comes a two-byte word whose bits say which fields follow (bit 15 colour, 14
and 13 the phones' name CRCs, 12 the address CRC, 11 the address and more), then those fields in
that order (`qi.b`, BroadcastParse). PairFree reads both layouts.

PairFree first connects over LE to the classic address itself, since JblTray found the Tune
Flex 2 answers there, and needs no scan for that. Only if that fails within 10 s does it scan,
for at most 10 s, and take the advert with the model's product id that comes from the classic
address, carries the CRC of the classic address in bytes 7–8, or carries this phone's name
CRC; with only name CRCs it takes a device only when exactly one matched. Scanning needs
Android's BLUETOOTH_SCAN permission: PairFree declares it never used for location, asks for it
only when the user opens a JBL model, and keeps nothing it saw.

## 4. Commands

### 4.1 Device info: `11` → `12`

`AA 11 00`. The reply's payload: name (16 bytes), product id (2, low first), colour, battery
(bit 7 charging, bits 0–6 percent), the device's address (6 bytes, reversed), firmware (3
bytes, shown `a.b.c`), then voltages, the TWS state and a country code (`HandleParse.parseDeviceInfo`).
PairFree reads the product id and firmware; the device report blanks the name and address.

### 4.2 Status: `21` → `22`

`AA 21 01 <type>`; the reply starts with the type. Types (`EnumDeviceStatusType`): `30` all,
`31` noise cancelling, `32` ambient mode, `33` auto power-off, `34` EQ preset, `35` voice
assistant, `38` auto play/pause, `39` TWS, `41` in-ear test, `42` seal test.

The whole status (`30`, `parseDeviceAllStatus`), after the type: noise cancelling (00 off, 01
or 02 on), ambient mode (00 off, 01 TalkThru, 02 Ambient Aware), auto power-off in one byte
(bit 7 on, bits 0–6 minutes; FF absent), EQ preset, voice assistant, one byte, update state,
auto play/pause (00 off), TWS, then auto power-off in three bytes (on, minutes low first; FFFF
absent). FajkPes recorded it from a Tune 720BT:

```
AA 22 0D 30 00 00 9E E6 00 01 00 00 00 01 1E 00
```

noise cancelling off, ambient off, auto power-off on after 30 minutes (`9E`, and `01 1E 00`),
auto play/pause off. FajkPes found the 720BT ignores reads of single types, so PairFree reads
`30` on these models.

### 4.3 Battery: `25`

`AA 25 01 00`. The reply's payload: `01`, then whether the secondary and the primary unit are
charging, their levels, whether the case is charging and its level, then voltages. Over 100 is
unknown. JBL's app shows the secondary as the left earbud and the primary as the right one;
on headphones PairFree shows the primary. FajkPes's Tune 720BT:
`AA 25 0D 01 00 00 50 50 FF FF …` (both 80 %, no case).

### 4.4 Noise control: `91`

The first payload byte is a sub-command (`SubId`): `10` set, `11` read, `12` reply, `13` all
off; then pairs of mode and value, modes `01` noise cancelling, `02` Ambient Aware, `03`
TalkThru (`CmdGen.genSetANCModes`, `HandleParse` case 23). JblTray recorded, on the Tune Flex 2:

| | |
|---|---|
| read | `AA 91 01 11` |
| noise cancelling | `AA 91 07 10 01 01 02 00 03 00` |
| Ambient Aware | `AA 91 07 10 01 00 02 01 03 00` |
| TalkThru | `AA 91 07 10 01 00 02 00 03 01` |
| off | `AA 91 01 13` |

The reply (`12`, and JblTray saw `10` too) has the same pairs; the mode whose value is not 0 is
on, none means off. Sub-commands `20` to `22` set and read the noise cancelling level and
sub-modes `00` to `02` other modes; PairFree does not use them yet.

### 4.5 The older noise commands: `31`, `32`

`AA 31 01 <00 off, 01 on>` for noise cancelling and `AA 32 01 <00 off, 01 TalkThru, 02 Ambient
Aware>` for ambient sound; read with status types `31` and `32`, or the whole status.

### 4.6 Feature records: `06`

```
AA 06 <length, 2 bytes, low first> <type> <records>
```

Type `00` set, `01` read, `02` reply (`Command3`). A read lists feature ids, two bytes each,
high byte first; a set or reply gives per feature its id, a length and the value. JblTray
recorded `AA 06 03 00 01 00 0C` (read spatial sound) on the Tune Flex 2. The ids PairFree uses
(`DeviceInfoFeature`): `02` product id (low first), `05` firmware (3 bytes), `0B` auto
power-off (on, minutes low first), `11` auto play/pause (1 on), `19` headband battery, `1A`
and `1B` left and right earbud, `1C` case (each: charging, level).

### 4.7 Auto power-off and auto play/pause: `33`, `35`

`AA 33 01 <bit 7 on, bits 0–6 minutes>` (v1) or `AA 33 03 <on> <minutes, 2 bytes low first>`
(v2, `generateSetAutoOffCmdV2`); `AA 35 01 <00 off, 01 on>`.

### 4.8 The other settings

JBL's dashboard shows a card per setting, and the model's control class sends it; `settings` in
`devices.json` lists those a model has both of (the card in its dashboard config, and a method
that the class implements rather than leaving as JBL's "not support" default). Most are a Harman
command whose first byte is a sub-command: `00` write, `01` read, `02` reply (`CmdBase`). On the
feature family the sound effect, the audio limiter and Hi-Res are feature records instead.

| Setting | Card | Command or feature | Read | Reply or write |
|---|---|---|---|---|
| Smart Audio & Video | `SmartAudioVideoCard` | `80` | `01` | `02` (write `00`), then AAC, SBC and two latencies, two bytes each, low first |
| Voice prompts | `VoicePromptCard` | `93` | `04` | reply `05`, write `03`, then 0 tones or 1 spoken |
| Balance | `LeftRightBalanceCard` | `A8` | `01` | `02` (write `00`) `01` on `02` level: 84 left to 116 right, 100 the middle |
| VoiceAware | `VoiceAwareCard` | `98` | `01` | `02` (write `00`), the level (1 low, 2 mid, 3 high), on |
| SafeSound | `SafeSoundCard` | `A5` | `01` | `02` (write `00`) `01` status, 1 on |
| 3D spatial sound | `Spatial3DCard` | `9D` | `01` | `02` (write `00`), on, the type (1 music, 2 movie, 3 game) |
| Speak to chat | `SpeakToChatCard` | `9F` | `01` | `02` (write `00`), on, the seconds before the music comes back (5, 15, 20) |
| Sound effect | `SoundEffectCard` | feature `20` | | on, the effect (0 standard, 1 treble boost, 2 bass boost) |
| Audio limiter | `AudioLimiterCard` | feature `35` | | 1 on |
| Hi-Res audio | `HiResAudioCard` | feature `59` | | 1 on |
| Low volume EQ | `LowVolumeEq`, `LowEQCompensationCard` | `9E` (direct), feature `24` | `01` | `02` (write `00`), on |
| Find My Buds | `FindMyBudCard` | `36` | | one value per earbud: `11` start the left, `10` the right, `01` stop the left, `00` the right |

Smart Audio & Video's numbers come from the product list (`smartAudioVideo`: `normal`, `audio`,
`video`, in `devices.json` as `smart_audio_video`). JBL's card is a switch (off is `normal`) with
Audio or Video under it; a model with three numbers writes 65535 as the fourth, and the reply is
matched on as many numbers as the model gives. The sound effect's names come from the product
list's `mEffectList` (`effects`); every model so far has Natural, more treble and more bass. JBL's
configs put the sound effect and audio limiter with the call settings (`CallModeItems`), so
PairFree shows them there; whether they act on calls alone has not been checked. The balance's
middle is no balance at all, which PairFree writes as off. Find My Buds has no read; PairFree
starts both earbuds and lets the user mute either, as on Galaxy Buds.

Protocol 4 (section 6) has each as a feature, one byte with the status in bit 7 and the level in
bits 0 to 6 unless said: `4111` Smart Audio & Video (the four numbers, two bytes each, low first),
`3136` voice prompts (status spoken, level the volume), `21` balance (50 lower than the
Harman level: 34 left to 66 right, `gn.l0` and `General4Control.setLRBalanceStatus`), `35` sidetone (VoiceAware),
`37` max volume limit (SafeSound), `34` spatial scene (level 1 movie, 2 music, 3 game: not the
Harman order, `General4Control.set3DSpatialStatus`), `36` low volume EQ (level 127), `43` smart talk (speak to chat, level the
seconds), `41` sound effect, `39` audio limiter and `40` Hi-Res (level 127), and `4106` Find My
Buds, written only: bit 7 the case, 6 the right earbud, 5 the left, bits 0 to 4 set.

### 4.9 Touch controls: `77`

The models whose dashboard has `GestureCard` or `GestureLeAudioCard` (the second only greys it out
while LE Audio is on) choose a set of actions per earbud on one page (`sp.e`, `sp.j`, `CustomGesture`),
and their class writes all of a side's gestures in one command (`genSetGestureInBach77`):

```
AA 77 <length> 00 (<gesture> <action>)...     write
AA 77 02 01 FF                                 read every gesture
AA 77 <length> <01 or 02> (<gesture> <action>)...   reply
```

A gesture's byte is its `GestureType` through `CmdBase.values`: `06` left tap, `07` double tap, `08`
triple tap, `0C` hold; `09`, `0A`, `0B` and `0E` the same on the right. JBL's app numbers them 1, 3,
7 and 49 on the left and 2, 4, 8 and 50 on the right. An action's byte is JBL's own id
(`GestureActionType` through `CmdBase.values_Action`). The sets, as tap, double tap, triple tap and hold:

| Set | Actions |
|---|---|
| Playback and voice assistant | `0A` play/pause, `05` next, `06` previous, `A1` the phone's assistant |
| Ambient sound | the noise cycle, `04` TalkThru, `00`, `A1` |
| Volume | `01` up, `02` down, `00`, `A1` |
| None | `00` on all four |

The noise cycle is `0B` (noise cancelling and ambient sound) or the one the tap already has (`09`,
`0B`, `0C`, `0D`); headphones and the Endurance Peak 3 start with `0D` (Ambient Aware and off),
`gesture_noise` in `devices.json`. The hold is `A1` while the earbuds' voice assistant is the phone's
default (`AppUtils.isDefaultMultiAi`); with Google Assistant or Alexa chosen in JBL's app, the
playback set holds for that assistant instead. PairFree does not read that choice and always writes
`A1`. The read names a side's set by its tap action, else its hold (`CustomGesture.generateName`).
The other gesture pages are not covered: `GestureUi4Card` (11 models) builds its sets from
`customizedGesture`, which the bundled product list has for none of them (they use JBL's server
resources, `usingSeverRes`, which PairFree never fetches), and the headphone cards
(`HeadLive670GestureCard` and the like) open pages of their own. The 29 other p4 models have no
gesture card in JBL's app. Touch sensitivity (protocol 4 features `4033` and `4034`) sits on the
customized-button page, whose levels come from the same server resources, so it is left out too.

### 4.10 Adaptive noise cancelling and auto compensation: `91`

Two of the noise card's "customize" entries (`AdaptiveAncCard`, `AutoCompensationCard`), each a
switch. On the feature family they share noise control's command (`AdvancedAncCmd`):

```
AA 91 01 21                    read
AA 91 <length> 22 (<type> <value>)...    reply
AA 91 03 20 <type> <0 or 1>    write one
```

Types: `01` adaptive noise cancelling, `08` auto compensation (and, not offered by PairFree, `04`
the manual level, `05` leakage and `06` ear canal compensation, `A1` the Ambient Aware level). JBL's
app writes one type at a time. Protocol 4 has them as features `27` (AdaptiveANC) and `32`
(AutoCompensation), the status bit and level 127.

The voice assistant choice (`VoiceAssistantCard`, `AA 34`; status type `35`: 00 off, 01 Google
Assistant, 02 Alexa, `DF` the phone's own) is left out. JBL's app changes the touch controls and
the hot-word mode with it, and Alexa and Google Assistant need an account linked through JBL's app.
PairFree's touch sets hold for the phone's own assistant (`A1`), which is what the earbuds use with
00 or `DF`.

Also left out: the LE Audio switch (`LeAudioCard`, 23 models), which restarts the earbuds and
needs the phone to reconnect over LE Audio while JBL's app waits on dialogs; PersoniFi
(`PersoniFiCard`), a hearing test that plays tones through JBL's app; relax sounds, which the phone
plays; and the equalizer (section 9).

## 5. How each model sends its settings

`devices.json` gives each model's:

- **family:** `direct` models (the `bes` classes on `BesDeviceStatusControl`) read the device
  info (`11`) and the whole status (`30`) and write each setting with its own command;
  `feature` models (`protocol3`, and the Junior 320BT and 470NC, Live Beam 3, Live Flex 3 and
  Quantum 360 classes) read and write auto power-off and auto play/pause, and read the product
  id, firmware and battery, as feature records. All read the battery with `25` too, as JblTray
  does on the Tune Flex 2. `p4` models speak protocol 4 (section 6), `airoha` models Airoha's RACE
  (section 10, with noise `race` and auto_off `race`).
- **noise:** `modes` (command `91`, every class whose `setAncSetting` uses `VERSION_NEW`),
  `old` (`31` and `32`: Live 460NC and 660NC, Reflect Flow Pro, Endurance Race; off sends both),
  `club` (the Club One: off sends `31` alone, `VERSION_OLD_CLUB_950_ONE`) or `asc` (protocol 4,
  section 6).
- **modes:** which of noise cancelling, Ambient Aware and TalkThru the model's noise card in
  JBL's dashboard offers: `AscCard` and its variants all three, `AscCardNoAnc` the two ambient
  ones, `AscCardNoAaTt`, `AscOnlyANCCard`, `AncCard` and `AncClubOneCard` noise cancelling
  alone; protocol 4's `AscStatusCard` and its two variants all three. A model with no noise card (the Tune 520BT, for one) gets no noise control, even
  though its config lists the three modes.
- **auto_off:** `v1`, `v2` or `feature`, by the class's `setAutoPowerOffStatus`, where the
  dashboard has `AutoPowerOffCard` (never, 30 or 60 minutes) or `AutoOffCard` (on or off; JBL's
  app writes 10 minutes).
  Models with `PowerSavingCard` instead have it on JBL's power saving page (`gq.g`): 30, 60 or
  120 minutes, with 15 too on the product ids `gq.a.isSupport15Min` lists, or never. Their
  classes write it as v2 (`AutoOff` with `VERSION_NEW`), as the feature record or on protocol 4.
- **auto_play_pause:** where the dashboard has `AutoPlayPauseCard` and the class sends it.

PairFree shows noise cancelling and Ambient Aware on the shared noise control (as its off,
noise cancelling and ambient sound) when a model has both, and TalkThru as a switch beside it;
models with other sets get a switch per mode. After a change it reads the setting again 3 s
later, so the screen shows what the earbuds hold.

## 6. Protocol 4

JBL's newest models speak another frame (`com.harman.protocol4`: `AssembleCmd4`,
`DeviceInfoList4Cmd`, `CommonParse`), over the same GATT service and characteristics when the
product list gives the model, over the same GATT service and characteristics. Some models add a
connection step, which the product list marks as a BLE security mode (section 7). Tour Pro 4 (over
classic Bluetooth), Tour One M4 and the second Smart Tx are left out; JBL's app marks them
unsupported and gives them no control class.

```
identifier (2)  command (2)  packet count (1)  packet index (1)  length (2)  payload
```

Two-byte fields are low byte first. The identifier is `DD00`, or `DD01` for what concerns the
charging case; commands are `0001` read, `0002` write, `0003` a notification from the earbuds.
A read's payload lists feature ids (two bytes each); a write's gives per feature its id, the
value's length (two bytes each) and the value. An answer's or notification's first packet
starts with a status code (two bytes, 0 when all is well) and then the same records as a write.
JBL's app splits payloads over 497 bytes into several packets and joins answers by index.

Features PairFree uses (`DeviceInfoFeature5`): `4` product id (two bytes), `12` firmware (its
bytes, shown joined by dots), `13` and `14` the left and right earbud's battery, `7939` the
case's, `24` noise cancelling, `25` Ambient Aware, `26` TalkThru, `73` ambient sound control
and `47` host type. A battery or mode is one byte: bit 7 the status (charging, or the mode
selected), bits 0 to 6 the level; a level of 127 leaves it as it is.

Auto power-off (feature `18`) is a two-byte feature: bit 15 the status, bits 0 to 14 the time in
seconds (JBL writes minutes x 60; 32767 is unknown). Auto play/pause (feature `42`) is a one-byte
feature. The host name's checksum is feature `7` (section 7).

On connecting, JBL's app writes host type 1 and then reads. There are two noise-control writes:
- **asc** (the classes that override `setAncSetting`: `TuneFlex3Control`, `WaveFlex3Control`,
  `TuneBeam3Control`, `Live680NCControl`, `LiveTws4Control`) writes ambient sound control on with
  the mode's feature, status 1 and its level; off writes ambient sound control off with the
  current mode.
- **mode4** (the base `General4Control.setAncSetting`: `TourOneM3Control`, `Tune680Control`,
  `EndurancePeak4Control`) writes the single mode feature with status 1 and level 127; off writes
  noise cancelling with status 0.

Both read the mode back as the one of noise cancelling, Ambient Aware and TalkThru whose status is
1, and off when ambient sound control is off or every mode is off (`gn.l0`). PairFree does the
same.

## 7. Security modes

The product list gives each protocol 4 model a `bleSecurityMode` (1 to 3). These are transport
steps JBL's app runs when the GATT link opens (`BleDevice`); none needs a key or a secret, so
PairFree does them itself. They are a direct port of JBL's steps and have not been tried on a
device.

- **Mode 1** (Live 680NC and 780NC, Tour One M3, Smart Tx): the app also subscribes to the raw
  characteristic `...0003`; commands still go to `...0002`.
- **Mode 2** (Tune 530/535/730/735/520BT2, Tune 680/780/785NC, Sense Pro, Soundgear Clips, Junior
  Free, Endurance Pace, Run 3 and Peak 4): the app waits for the earbuds to report PairingStatus
  (feature `46`) = 1, which arrives on `...0001` as a normal frame, before it treats the link as
  up. It never bonds itself, so this reflects the ordinary Bluetooth pairing. PairFree does not
  block on it: its reads simply go unanswered until the earbuds are ready, as for any brand.
- **Mode 3** (Live Beam, Buds and Flex 4): the app subscribes `...0003`, writes the checksum of
  this phone's Bluetooth name (CRC-16/ARC, section 3) as feature `7` to `...0003`, waits up to 1 s
  for the earbuds to echo it, then carries on regardless. PairFree writes the checksum and carries
  on without waiting.

PairFree runs the extra steps where the model needs them, but never lets them fail the main
connection: if the raw characteristic is missing or a step is refused, it still opens `...0001`
and sends. Whether a model's commands are gated on these steps is not known
([device page](../devices/jbl.md)).

Beyond the security modes, JBL's app also runs a counterfeit check on protocol 4 models (an RSA
challenge whose key lives in a native library, `com.jbl.counterfeit`). It is not part of
control — it only shows an "is this genuine" popup — and PairFree cannot do it without extracting
JBL's embedded key, which the project's rules forbid. PairFree leaves it out.

## 8. Never sent

`commands.json`'s `never_send`: `95` factory reset, `97` power off, `F0` firmware update, `AA`
preserve settings, `13` and `15` analytics. In protocol 4: `00FF` factory reset, `0101` to
`0104` firmware update, `0201` and `02FF` analytics. JBL's firmware updates go over separate
services PairFree never opens. The Airoha models' list is in section 10; PairFree checks every
RACE frame against it before sending.

## 9. Open questions

- Whether bytes 7–8 of the classic advert are the CRC of the classic address, as the name JBL's
  app gives them says; it does not compare them for these models.
- Which models' LE side answers on the classic address, as the Tune Flex 2's does.
- Whether the feature-family headphones (Junior, Quantum 360) answer the battery read `25`.
- Protocol 4 has no recorded frame yet: sections 6 and 7 are from JBL's app alone.
- Section 4.8's settings have no recorded frame: they are from JBL's app alone. Whether the sound
  effect and audio limiter act on calls alone, as JBL's configs group them.
- Whether a security-mode model answers commands before its step (mode 2's pairing report, mode
  3's host checksum) is unknown; PairFree runs the step but does not block on it.
- What the acknowledgement's status values mean; PairFree reads a setting back rather than
  trusting them.
- The equalizer: JBL's app writes a whole curve of filters as floating-point numbers with a
  calibration (`41` on older models, `A2` `EQCmd` on others, as FajkPes's notes lay them out), and its
  presets are curves the app holds (`assets/eq`). The calibration comes from a native library
  (`EqCalibration.calculateCalib` in `libcalibrationLib`), which works it out from the model's design EQ and
  the new curve; PairFree cannot port it (hard rules) and does not guess it, since a wrong value can make the
  sound much louder. Picking a preset by its index (`AA 40`, `MessageID.PRESET_EQ`) needs no calibration, but
  JBL's app sends it only on its BES models' older path (on protocol 3 `setEQPreset` is not implemented), its
  index list differs per family, and nothing of it has been checked on a device. So PairFree does not send
  the EQ yet.
- Section 10 (the Airoha models) has no recorded frame: it is from JBL's app and Airoha's SDK in it. Which chip
  each model has, and whether the older three (Club Pro+, Reflect Mini NC, Live Free NC+) have the settings
  their class sends, needs device reports; the report names the chip.
- Over LE Audio, whether the earbud that is not the leader answers PairFree's commands, or only the leader does
  as JBL's app assumes; and how Android lists LE Audio earbuds among the paired devices.

## 10. Airoha models

The models whose control class is in `u3.airoha` or the older `com.harman.cmdctrl.imp.airoha` (`devices.json`
family `airoha`): the Club Pro+, Reflect Mini NC, Live Free NC+, Live Free 2, Reflect Aero, Quantum TWS, Quantum
TWS Air, Live Buds 3, Endurance Race 2, Wave Beam 2, Vibe Beam 2, Tune 775NC and 777NC, and the Quantum 910,
910P and 910X headsets; and the LE Audio models whose class extends `u3.lea.LeaAhBaseControl`: the Tour Pro 3,
Tune 670NC and Tune 770NC. JBL's app drives them through Airoha's SDK (`com.airoha`) and its own adapter for it
(`com.airoha.adapter.harman`: `AirohaDeviceStatusControl` and its stages), reached from the control classes
through `gi.c` (`gi.a` for the LE Audio classes, the same SDK). Their commands are Airoha's RACE, not Harman's.

**Tried.** A user's Live Buds 3 (firmware v8.1.5, chip `AB1577S_Earbuds`, so the AB158x rules) connected over
Airoha's serial UUID in PairFree 0.3.2 and, by the user, worked throughout (`data/jbl/reports/20F1_v8.1.5.txt`,
GitHub issue #4). Every read PairFree sends on connecting answered with status 0: the chip, both earbuds' battery
and the case's, the firmware, noise control, Smart Audio & Video, balance, the config keys and JBL's extras. 0.3.2
did not yet read the model name (NV `1003`); whether it answers is still to see.

**LE Audio.** For the LE Audio models JBL's app picks the link by how the phone is connected (`gi.a.w`,
`hh.g`): with the earbuds on the phone's LE Audio profile it opens Airoha's LE service on the LE Audio device's
address (`PROTOCOL_LEA`, the same `GattLinkParam` as `BLE`), and on earbuds first asks the role (`0CC4`), moving
to the other earbud when that one is not the leader; otherwise it opens the serial channel as for the other Airoha
models. PairFree opens the LE service on any Airoha model paired as an LE-only device, and otherwise does as the
model's connection says; it does not ask the role or move to the other earbud.

**Frames** (`RacePacket`):

```
05  type  length(2)  id(2)  payload        two-byte fields low byte first; no checksum
```

The type is `5A` a command, `5B` a response, `5C` a command needing no response, `5D` an indication (some
reads answer with one too, and the earbuds send changes with them). A response's payload starts with a status
byte, 0 for success. Module reads and writes (`0901`, `0900`) put the module id (two bytes) first, then the
status; config reads and writes (`2C83`, `2C82`) answer the status, then the setting's id (two bytes), then its
data; NV key reads (`0A00`) answer the data's length (two bytes) and the data. The SDK sends one command at a
time and waits for its answer; PairFree waits up to 1 s, as for JBL's other models. `RaceParser` splits frames by
their length, skipping bytes before a start byte.

**Transport.** Connection `BT`: RFCOMM on `00000000-0000-0000-0099-AABBCCDDEEFF` (`UuidTable.AIROHA_SPP_UUID`).
Connection `BLE`: service `5052494D-2DAB-0341-6972-6F6861424C45`; PairFree writes each frame, with a response, to
`43484152-2DAB-3241-6972-6F6861424C45` and takes notifications from `43484152-2DAB-3141-6972-6F6861424C45`, asking
for a 517-byte MTU (`GattLinkParam`). The LE side is found as in section 3.

**Opening** (`AirohaConnector`, `AirohaDeviceConnector`, the control classes' `getAllCommands`):

| Read | Frame | Answer |
|---|---|---|
| Chip name | `0A00` NV key `1002`, 1000 bytes | the name in ASCII |
| Push of changes on | `0006` `01` | status |
| Which earbud PairFree talks to (the agent) | `0CD4` | status, 1 when it is the right one |
| Firmware | `1C07` `00` | status, role, length, the version in ASCII |
| Battery | `0CD6` `00` agent, `01` the other earbud | status, role, level (indication) |
| Case battery | `000B` | status, level; 0 or less unknown (indication) |
| Noise control | `0901` module `0005` | module, status, filter, gain (two bytes) |
| Model name, once the chip is known | NV key `F500` (AB155x, AB1568) or `1003` (AB1568 v3, AB158x), 100 bytes; the AB1562 `1C09` `01 FF` | 20 bytes, then the model name: on JBL's models the product id ("20F1"); the AB1562's answer holds it at byte 29 of its data, after the address |

The product id names the model, as on JBL's other families: earbuds found by a name another model shares, or by
an older name, get that model's settings (`JblSession.productIdIs`). PairFree keeps the names as text in the
device report, not the keys' bytes, and never the AB1562's answer, which holds the address.

JBL's app skips the push and the agent read on the Quantum 910 headsets (`push_status` false in `devices.json`).
Unasked, the earbuds send module `0005` (the noise filter, after a change on the earbuds), `0065` and `0066` (the
agent's and the other earbud's battery; the agent's may add 1 when it is the right one) and `0067` (the case's)
as `0901` indications.

**The chip.** Airoha's SDK reads the chip's name first and picks its code by it: `AB1562`, `AB1562E` or `AB1563`
the AB1562; `AB1565_DUAL_CHIP`, `AB1568_DUAL_CHIP`, `AB1565`, `AB1568` or `MT…` the AB1568; `AB158…`, `AB157…`
or `MT283…` the AB158x; anything else the AB155x. What differs:

| | AB155x | AB1562 | AB1568, AB158x |
|---|---|---|---|
| Noise on | `0E06` `00 0A` filter | `00 0A` filter, mode, `01` | `00 0A` filter, mode |
| Noise off | `0E06` `00 0B` | `00 0B 01` | `00 0B` |
| Ambient Aware, TalkThru filters | 5, 6 | 9, 10 | 9, 10 |
| Auto power-off | NV key `F2F1` (on, minutes); write `0007` on, `00`, minutes (two bytes) | as AB155x | config `0001`: on (two bytes), seconds (two bytes) |
| Pause when taken off | NV key `F2F0`; write `000A` on | as AB155x | config `0002`: on |
| The other earbud's battery | `0CD6` `01` | relayed (`0D01`), so PairFree shows the agent's alone | `0CD6` `01` |

Filters: 0 off, 1 noise cancelling (2 with the sports mode some cards offer), then the passthroughs (Ambient
Aware, TalkThru). The mode is 0 for noise cancelling and 4 for a passthrough (`SDKAncSettings`,
`AirohaDeviceStatusControl.convertToAirohaAncIndex`). Until the chip is known PairFree sends no noise,
auto power-off or pause write.

**Other settings.** Each a JBL command id of its own unless said, read with no payload; the values are the
ones Harman's commands carry (section 4.8), which JBL's adapter passes through:

| Setting | Read | Answer after the status | Write |
|---|---|---|---|
| Smart Audio & Video | key event `1101` `A9 00` | key (2), AAC (2), SBC (2), latency (4), second latency (4) | `1101` `A8 00` and the four numbers |
| Voice prompts | config `0009` | the prompt language: 255 tones, else spoken | config `0009`: 0 spoken, 255 tones |
| Balance | `0037` | on, level | `0036` on, level |
| VoiceAware | config `0007` and `0006` | on; level (two bytes) | config `0007` on, then `0006` level |
| SafeSound | `0014` | status (1 on) | `0015` status |
| 3D spatial sound | `0013` (indication) | on, type | `0012` on, type |
| Sound effect | config `0013` | microphone effect, sound effect, audio setting | config `0013` 5 standard, 6 more treble, 7 more bass |
| Audio limiter | `0051` | on | `0050` on |
| Hi-Res audio | `0075` | type (1 on) | `0076` type |
| Low volume EQ | `0058` | on | `0059` on |
| Speak to chat | `0060` | on, seconds | `0061` on, seconds |
| Find My Buds | | | `2C01` blink, ring, earbud (0 left, 1 right): `00 01` to ring, `00 00` to stop |

JBL's voice prompt switch picks spoken prompts or tones (`MmiStage_SetVpLanguageIndex`); it never turns prompts
off (`0900` module `0001`), so PairFree does not either. The sound effect has no off on these models. The
models without a dash4 config (the Club Pro+, Reflect Mini NC and Live Free NC+) get noise control from the
product list's ANC flag and Smart Audio & Video, voice prompts, VoiceAware and auto power-off from their class;
PairFree shows each only once the earbuds answer its read.

**Not covered yet:** the touch controls (an NV key per chip generation, rewritten and relayed to the other
earbud, then reloaded: `MmiStage_SetGesture_1568_v3` and its kin), the noise cancelling and Ambient Aware level
(`0E06` `00 1E` and the level, which JBL's app sends only with the mode on, and reads back only from
indications), adaptive noise cancelling and auto compensation (separate flows per product id through Airoha's
ANC manager), PSAP, PersoniFi, the Live Buds 3's smart case settings, Auracast, the voice assistant and the EQ
(section 9), and the Tour Pro 3's smart case (its own firmware and settings, `CaseOtaControl`).

**Never sent** (`commands.json` `airoha.never_send`): analytics `0010`, `0011`, `0016`; dongle pairing and
switching `0030`, `0031`; connecting, deleting and listing paired devices `0056`, `0057`, `0067`; the charging
case action `0083`; the clock `0090`; Harman commands passed through `0093`; device information `0094`; NV key
writes `0A01` and reloads `0A09`; relays to the other earbud `0D01`; key events on `1101` other than Smart Audio
& Video's (`0096` factory reset, `0095` power off, `0002` pairing among them); and the firmware update commands
`1C00` to `1CFF` other than the version read.
