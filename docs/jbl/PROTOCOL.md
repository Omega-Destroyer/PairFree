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

Smart Audio & Video goes over two sets of commands. JBL's app has two status classes with the same methods and the same
bytes for everything but this: `HmProtocol3DeviceStatusControl` (`SmartSwitchCmd`, `80` as in the table) and
`BesDeviceStatusControl`, the base of every control class that extends `BesBasicControl` or `BesSdkControl`
(`CmdGen.generateReqSmartSwitch`, `generateSetSmartSwitch`): the read is `AA 82 00`, a write `AA 81 08` and the four numbers
with **no sub-command in front**, and the reply `AA 83 08` and the same eight bytes (`HandleParse`, `RetHeader` "aa83").
`devices.json` marks those 39 models `smart_switch: bes` (`JblModel.besSmartSwitch`); the protocol 3 models (Wave Buds 2,
Tune Buds 2 and the like) keep `80`. The two sets were found by comparing the two classes method by method, and a Tune 230NC
run in an emulator (JBL Headphones 5.37.10 against a synthetic peer, `docs/jbl/VIRTUAL_TESTING.md`) showed the app sending
`AA 82 00` and `AA 81 08 00 01 2E 00 18 01 FF FF` (Audio) and `AA 81 08 C5 00 2E 00 18 01 FF FF` (Normal); PairFree's old
`AA 80 09 00 ...` was not what that app sent. The emulator's replies are invented: whether real BES firmware also answers
`80` is not known, and a user's report would settle it.

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
The other gesture pages: `GestureUi4Card` builds its sets from the product list's `customizedGesture`, which only
the Endurance Race 2 among its models has (section 10.1; the others' page offers nothing); the customized button page
of 23 protocol 4 models is section 6.1. `HeadLive670GestureCard` and `HeadLive770GestureCard` (Live 670NC, 770NC,
675NC and 775NC) open this same page for the left channel alone, so those headphones get these sets, starting the
noise set at `0D` as headphones do. Not covered yet: `HeadTourOneGestureCard` (the Tour One's class reads gestures but
has no write; what it sets is tap and hold on the touch panel, `AA 79 03 00 01` and the function, from the customize
controls page) and `HeadQuantumGestureCard` (the Quantum 360 and 910 headsets' touch panel page, `wp.k`). Touch sensitivity (protocol 4 features `4033` and `4034`) sits on the
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

### 4.11 PSAP, the smart case, the clock, the fit checks and the ear tips: `A0`, `A3`, `A4`, `A7`, `99`, `9A`, `9C`

Read from JBL Headphones 5.37.10 (2026-11-23): the command classes `PSAPCmd`, `ChargingCaseDisplayStatusCmd`, `ChargingCaseFeatureCmd`, `SyncTimeToDeviceCmd`,
`EnvironmentNoiseCheckCmd`, `EarCanalTestingCmd` and `TipsTypeCmd` of `com.harman.bluetooth.imp.cmd`, and the dashboard cards that call them. Each is a Harman frame
(`AA`, the command, the payload's length, then the payload) whose first payload byte is the sub-command: 00 write, 01 read, 02 reply (`CmdBase.combine` adds the length).
A model has a setting when its dashboard card is in its config and its control class implements the write (`BesDeviceStatusControl` carries them for the BES models; the
protocol 4 models send the same settings as `DeviceInfoFeature5` records, section 4.12, and the Airoha models through their own adapter, section 4.13). So far:
Tour Pro 2, Live Beam 3 and Live Flex 3 (the smart case), the Tour One M2, Tour Pro 2, Live 670NC, 675NC, 770NC, 775NC and Live Beam 3 (PSAP), the Live Pro 2 TWS, Tour Pro 2 and
Live Beam 3 (the fit checks), the Tune Flex and Tune Flex 2 (the ear tips) and eight models for the Auracast receiver: `settings` in `devices.json`.

- **PSAP** (`A0`, `PSAP2Card`, `iq/g.java`: the page "Personal Sound Amplification"). Read `01`; the reply `02` then pairs of feature and value: 01 on, 02 the balance, 03 the gain
  (`PSAPCmd.parse`; a reply of fewer than ten bytes holds only the on byte and the balance). The page's balance bar is 95 to 105 (100 in the middle, `M0`), its gain bar has
  five steps (`GradientProgressBar` with `PartSplictLineCount` 5, so 0 to 5). A write is `00` and the three pairs together, with the values last reported (`toBytesSet`).
  JBL's text says that turning it on turns off spatial sound and the low volume EQ (`enabling_psap_will_diable_ss_and_lvd_eq`); PairFree shows that note and does not switch the
  other two itself, since the earbuds do. The page's reduce-background-noise and beamforming switches (`setPSAPReduceBackgroundNoise`, `setPSAPBeamforming`) are implemented only
  by the protocol 4 base class (`General4Control`), so a BES model has neither.
- **The smart case's screen** (`A3`, the cards `BrightnessCard`, `MsgNotificationCard`, `ScreenSaverCard`, `CaseLanguageCard`, `TapToWakeCard`, `CaseInfoCard`). Read `01`; the
  reply `02` then pairs of id and value (`parse`): 1 brightness (0 to 3: `BrightnessCard` has a three-step bar), 2 message notification, 3 the screen saver's index, 4 the screen
  saver's time in seconds (two bytes, low first; the card's slider has 31 positions of five seconds, 0 is "never"), 5 message preview, 6 whether the screen saver is the user's
  own, 7 that picture's CRC (four bytes), 8 the case's language (`CaseLangType`: 1 English, 2 Dutch, 3 French, 4 German, 5 Italian, 6 Japanese, 7 Korean, 8 Portuguese, 9 Russian,
  10 Simplified Chinese, 11 Spanish, 12 Thai, 13 Vietnamese), 9 the unlock indicator, 10 quick wake-up (the status in the bits above the seventh, the tap in the low seven: 1 single,
  2 double, 3 triple; the switch sends the status with action 0 and a tap its number with the status on, `TapToWakeCard`), 11 and 12 the earbuds' and the case's latest firmware
  as three bytes, 13 find my case. A write is `00` and the pairs, the screen saver time in two bytes (`toBytesSet`); `03 06 xx` deletes the user's own screen saver
  (`delCustomizeScreenSaver`).
- **The smart case's pages** (`A4`, `SmartCaseSettingCard` and `CaseCard`). Read `01`; the reply `02` and pairs of id and one byte (192 the case's version as three bytes, 193 its
  Bluetooth address as six bytes, 194 its language): ids 1 to 17 are the pages the case's screen can show (ambient sound control, equalizer, speak to chat, 3D spatial sound,
  low volume EQ, VoiceAware, auto play and pause, Silent Now, earbuds in the case, timer, lock screen wallpaper, notifications, find my buds, flashlight, music playback, volume,
  screen brightness), each 1 for shown and 0 for hidden (`ChargingCaseFeatureCmd.toBytesSet`, whose list form writes each page's id with `pageDisplay`). A write is `00` and the pairs.
  `03` launches the case's firmware update and is never sent.
- **The clock** (`A7`). JBL's app sends it on every connection to every model (`gn/p0.java` on `CONNECT_STATUS`), and the control class decides: the BES models write
  `00 01 hh mm ss weekday day month yearLo yearHi` with the phone's local time (weekday 0 is Sunday; `SyncTimeToDeviceCmd.toBytesSet(long)`), and so does PairFree, once, when the
  channel opens, on the models whose config has the `SyncTimeCard`. The display format (`02` and 1 for 12 hours, 2 for 24 hours, the variant with the format) belongs to the models that
  list `syncTimeFormat`, which these three do not.
- **The noise check** (`99`) and **the ear canal test** (`9A`). Read `01` starts each. The noise check's reply `02` holds the left and the right level, two bytes each, low first
  (`EnvironmentNoiseCheckCmd.parse`); the ear canal test's reply `02` holds a result byte, 3 for success, anything else a failure (`EarCanalTestingFragment`: `T0(result == 3)`). The
  test plays tones in the earbuds and compensates noise cancelling for the ear canal. PairFree shows the two levels and "done" or "failed" as they come.
- **The seal check** (`21` with the status type `42`, `CmdGen.genGetSealingStatus`): the reply `22` holds `42`, then the left and the right earbud, 1 for sealed and 0 for not
  (`HandleParse.parseDevStatus`, `SEAL_CHECK_STATUS`); JBL's fit page (`gp/j.java`, `k1`) asks the user to adjust the earbud whose byte is 0. JBL's page runs its steps in an order
  of its own (in-ear status `41`, the noise check, the seal check, the ear canal test) and gates them on the noise levels (both 50 or more); PairFree offers each as a button.
- **The Auracast receiver** (`B0`, `LeaAudioCmd`; the Auracast cards of the Tour One M2, Tour Pro 2, Live Beam 3, Live Flex 3, Tune Buds 2, Tune Flex 2, Tune Beam 2 and Tune 255NC; the
  LE Audio switch is a different card and a different command). The earbuds scan for broadcasts themselves; the sub-command is the first payload byte: `00` asks what they receive, `01` `01`
  and the seconds (30) starts a scan, `02` `00` stops it, `20` `01` the six address bytes and the password's characters receives a source, `21` `00` stops receiving, `22` and an index picks a
  sub-group. The replies are `10` (what they receive), `11` (one scan result per message), `12` (the scan stopped), `30`, `31` and `32` (the answers to `20`, `21` and `22`), each then a
  return status (for `30`: 0 done, 1 wrong password, 2 timed out; for `10` and `11`: 0 done, 1 not receiving, 2 the scan timed out) and, for `10` and `11`, records of a type, a length and a
  value: type 0 is the source and any other a sub-group of it, each holding records of an id, a length and a value (1 the address, 2 the name in UTF-8, 3 the receive status, 4 whether it
  needs a password, 32 the sub-group's index, 33 whether it is on, 35 its quality: 2 high). The address a reply gives goes back in the reverse order in `20` (`receiveLeAudioSource`). A
  source counts as received when one of its sub-groups is on (`AuracastP2Ui2ViewModel.I`).
- **The ear tips** (`9C`, `SoundFitCard`, only the Tune Flex family: `TIPS_OPEN` 0 and `TIPS_SEALING` 1). Read `01`, the reply `02` and the type, a write `00` and the type.

None of these is on the never-sent list. Open, in `data/completeness.json`: the screen saver's pictures and the user's own screen saver (a file transfer to the case,
`ScreenSaverAdapter`, ids 3, 6 and 7 of `A3`), find my case (`FindMyCaseCard`, id 13). The protocol 4 models send the rest as features (section 4.12) and the Airoha models as RACE commands (section 4.13).

### 4.12 The same settings on protocol 4

Read from `General4Control` (JBL Headphones 5.37.10, 2026-11-24) and the `DeviceInfoFeature5` table with its value classes (`com.harman.protocol4.features`). The protocol 4 models send the
cards of section 4.11 as feature records (section 6): an id (two bytes, low first), a length and a value, in a set (`0002`) or a read (`0001`); the earbuds answer or notify with the same
records. Value classes: `StatusLevelSize1Feature` is one byte, bit 7 the status and bits 0 to 6 the level (127 where the app has none); `Occupy1ByteEachIntFeature` a byte for each number;
`StatusLevelSize2Feature` two bytes, low first, bit 15 the status; `BitListFeature` one byte read bit by bit from the top; `CasePageConfigFeature` one byte, bit 7 whether the page shows, bit 6
whether the user may set it, bits 0 to 5 its place. A model has a setting under the same rule as before (its dashboard card in the config, the base class's write): `settings` in `devices.json`.

- **PSAP**: 3200 status (a one-byte status and 127), 3201 the balance (45 to 55: the page's 95 to 105 less 50, `setPSAPStatus`, `gn/l0`), 3202 the gain step (0 to 5), 3203 reduce
  background noise (status, level 0) and 3204 voice focus (`setPSAPBeamforming`: status on unless 0, the level 0, 1 or 2 for off, medium and high). `getPSAPStatus` reads the first three; PairFree reads
  all five.
- **Ear tips**: 4101, the status bit is the type (0 open, 1 sealing; `setTipsType` writes `setValue(type, 127)`).
- **Noise check** (4100) and **seal check** (4099, `getSealingStatus`): each is started by writing one byte, 0, and answered by a notification of one byte read bit by bit: for the seal check bit 7 is
  the left earbud and bit 6 the right, 1 for sealed (`SealingInfo`); for the noise check a set bit is quiet and a clear bit noisy (`gn/l0` turns them into the levels 0 and 100). Protocol 4 has no ear canal test
  (`General4Control` leaves `earCanalTesting` at the base class's default).
- **The clock** (49, `setDeviceTime`): nine bytes, seconds, minutes, hours, weekday (0 Sunday), day, month, the year low byte then high, and the display format (0 when the phone has none), sent when the
  channel opens. 7953 is the case's 24-hour switch.
- **The case's screen**: 7940 brightness (status 1, the level 0 to 3), 7941 message notification (bit 7 on, bit 6 the permission the case reported, kept when written; a case that reported none gets 127 in the level),
  7944 preview, 7943 the screen saver time (`StatusLevelSize2Feature`: the seconds in fifteen bits; the status bit is set for one to a hundred seconds, as `setChargingCaseDisplaySettingsStatus` sets it),
  7946 the language (the same numbers as `A3` id 8), 7948 quick wake-up (status, and the level 1 to 3 for the tap; the switch sends 127), 7937 and 7950 the case's version and the latest as three bytes,
  7942 the screen saver's index, 7945 and 7954 to 7959 the custom screen saver's CRC, status and look.
- **The case's pages**: 8064 to 8081, in the order ambient sound control, equalizer, speak to chat, 3D spatial sound, low volume EQ, VoiceAware, auto play and pause, Silent Now, earbuds in the case,
  timer, lock screen wallpaper, notifications, find my buds, flashlight, music playback, screen brightness, language and 24-hour time. PairFree switches bit 7 and keeps the other bits as the case reported them;
  a page the case does not let the user set (bit 6 clear) is shown greyed out.

Open, in `data/completeness.json`: the screen saver's index and pictures, find my case (7951) and the unlock indicator (7947, which only the Smart Tx page shows).

### 4.13 The same settings on the Airoha models

Read from JBL's Airoha adapter (`com.airoha.adapter.harman.AirohaDeviceStatusControl` and its `MmiStage_*` classes, JBL Headphones 5.37.10, 2026-11-24). The Live Buds 3 (`LiveBuds3Control`
over `gi/c`) and the Tour Pro 3 (`TourPro3LeControl` over `gi/a`, on LE) both end in the same flows, so both send RACE commands (section 10) of fixed layout: a read has no payload, the answer starts
with a status byte (0 is fine) and then the values, a write carries every field of its struct, and a field the app leaves alone is 0xFF (each info object starts at -1). The ids are the decimal
numbers of the stages' `mRaceId`:

- **PSAP**: `0077` reads, `0078` writes three bytes: on, the balance (95 to 105) and the gain step (0 to 5); the answer to either holds the same three.
- **The case's screen**: `0079` reads and `0080` writes a 26-byte struct: 0 brightness (0 to 3), 1 message notification, 2 preview, 3 the screen saver's index, 4 its time in seconds (one byte), 5 whether it is the
  user's own, 6 to 9 that picture's CRC, 10 the language (`CaseLangType` ordinal, 1 English to 13 Vietnamese), 11 the unlock indicator, 12 tap to wake, 13 to 15 the earbuds' latest version, 16 to 18 the case's, 19 find my case,
  20 to 23 the screen's width and height, 24 and 25 quick wake-up (the status, then the tap 1 to 3; the switch sends the status alone).
- **The case's pages**: `0081` reads and `0082` writes a 27-byte struct: bytes 0 to 8 whether the pages ambient sound control, equalizer, speak to chat, 3D spatial sound, low volume EQ, VoiceAware, auto play and pause,
  Silent Now and earbuds in the case show, 9 to 11 the case's version, 12 to 17 its address, 18 its language, 19 to 26 the timer, lock screen wallpaper, notifications, find my buds, flashlight, music playback, volume and
  screen brightness pages. PairFree sends the case's version and address back as the case reported them (the app sends zeros when it has none).
- **The clock**: the Harman frame `A7` of section 4.11 passed to the earbuds through RACE `0093` (`MmiStage_SendHmCmd`, the app's `writeHmCommand`), written once when the channel opens: both `LiveBuds3Control.setDeviceTime`
  and `TourPro3LeControl.setDeviceTime` write `SyncTimeToDeviceCmd`'s bytes that way. `0093` is on the never-sent list because it can carry any Harman command; PairFree lets through this one frame (`A7`) and nothing else.
  `0083` (`MmiStage_SetSmartCaseAction`, the case's firmware update) stays never sent. The adapter's own `SET_DEVICE_TIME` flow (`MmiStage_SetDeviceTime`, `0090`, seconds since 1970) is for other models and stays on the list.
- The Airoha adapter's `getTipsType` and `setTipsType` only log that nothing is there, so the ear tips row stays on the other families.

- **The noise check**: the app reads the environment detection's status (`0E17`, payload 3 and 2) and then its information (`0E15`, payload 3; `0E18` on the AB1562, `AncStageGetEnvironmentDetectionInfo1562`).
  The answer's data holds the type 3, the two gains, the level, then the stationary noise of the agent and of the partner earbud (two bytes each, low first, signed); the earbud PairFree talks to is the right one when
  the session's agent read says so, which swaps the two. A level from 0 to 100 comes from each noise by a line of the model's own (`getEnvironmentNoise`: (noise x 45 + a) / b, less or plus a constant; the Live Free 2,
  Reflect Aero, Quantum TWS, Live Buds 3 and Tour Pro 3 each have one), and a model with none shows 0.
- **The seal check**: `0E13` with the mode 0 and 1 (`MmiStageLeakageDetecttion`), answered up to 15 seconds later by an indication of 21 bytes whose bytes 19 and 20 are the first and second earbud, 0 for sealed
  (`OnAncAdaptiveStatus`); the right earbud is first when the agent is the right one.

The ear canal test (`EAR_CANAL_TESTING`; read 2026-11-24): `AirohaDeviceStatusControl` unzips the asset `EarCanalTesting.zip` and starts Airoha's ANC user trigger (`AncStageStartAncUserTrigger`: RACE `0901` with module 5, whose responses and the partner's relays go to `AirohaAncUserTriggerStateMachine`). The state machine measures the ear canal, `AncUserTriggerUtils` works out new feedback filter coefficients from the model's tuning tables and the coefficients are written to both earbuds (`AncStageUpdateAncUserTriggerCoef`). The zip holds tables for three models only, folders 2067 (Live Free 2), 207B (Reflect Aero) and 207D (Quantum TWS), about 3 MB each. Blocked: decision the owner has reserved (the tables are the vendor's tuning data and derived data goes into `data/` only with the owner's agreement; the test rewrites the earbuds' noise cancelling filters); checked: the flow, the stage, the utility's file list and the zip's folders, in the registry item. The Live Buds 3 and Tour Pro 3 have no folder, so their fit checks are the seal check and the noise check.

### 4.14 PersoniFi: the hearing test's curve

Read from JBL Headphones 5.37.10 (2026-11-24): the wizard `sk/q.java` and its helpers (`ek`, `fk`, `kk`, `zk`, `ih`), the Kotlin side of `com.jbl.personifi3`, and the native library `libpersonifiAlgorithm.so`
(16 KB). The resource pack the wizard downloads (`personifi3_resource.zip` from `appstorage.onecloud.harman.com`, a plain GET) holds only the test tones: `warble_<frequency>_<left|right>_14s.mp3` for 250, 500, 750 Hz,
1, 1.5, 2, 3, 4, 6, 8, 10 and 12 kHz. PairFree makes its own warbles and does not ship JBL's files.

**The test.** Nine tones for each ear, in this order: 1 kHz, 500 Hz, 250 Hz, 2 kHz, 4, 6, 8, 10 and 12 kHz, the left ear first (`ek.a`, `q.initData`). The user holds a finger down while a tone is audible; the tone
fades over 14 seconds, and the seconds between the finger going down and up give the level it was heard to (`m2`): `caldB(n)` is 43 - 3n dB for n from 1 to 14 (the library's own function, and the whole of the test's scale).
After 500 Hz, 2 kHz and 4 kHz the app adds a 750 Hz, 1.5 kHz or 3 kHz tone for that ear when the result is more than 20 dB from the preceding 1 kHz or 2 kHz result (`y1`); those extra results go to the library with the rest. While a tone plays the app tells the earbuds which one (`A1` ids 5 and 6: the side 1 or 2 and the frequency in Hz), and brackets the whole flow with the setup flow flag (`A1` id 3, 1 at the start and
0 at the end) and the hearing test mode (id 2, 0 when it is over).

**From levels to bands.** The app works out gains of its own from the levels (0.6 x the level less the tone's reference and the smallest such difference over both ears, between 0 and 9 dB; an 8 kHz high shelf of 2 dB
when nothing has a gain; `HearingTestBandComputation`, `A2`), but only for its report screen: the library takes each band's frequency and level and works the gains out again. The app drops the 250 Hz tone before the library
on the open-fit models (`sk/q.H1`, `zk.d.i`).

**The library** (`calCompensation`, mode 3, the only one any model passes): each tone is matched to a slot of the library's 12 frequencies (250, 500, 1000, 2000, 4000, 6000, 8000, 10000, 12000, 750, 1500, 3000 Hz), the
gain is worked out again as 0.6 x (the level - the library's own references 20, 10, 10, 15, 10, 20, 20, 10, 5, 10, 10, 10), kept between 0 and 9, the Qs are 1, 2, 2, 2, 3, 3, 3, 3, 1, 2, 2, 3 and the types 0 for 250 Hz, 2 for
12 kHz and 1 otherwise; then bands are merged where their gains differ by less than 1 dB (the three lowest into a low shelf, the highest three or four into a high shelf, everything into a shelf at 18 kHz when the whole
curve is flat), merged bands set to type 5 (off), and the master gain is minus the largest gain. `JblPersonifi` is a port, and `data/jbl/personifi_vectors.json` holds 400 cases of the library run in an emulator, which
`JblPersonifiTest` reproduces exactly.

**The messages around the test** (`PersoniFiModeHearingTextCmd`, `PersoniFi2Info`; `JblPersonifiMode`). On the Harman models they are command `A1`: `01` reads; the reply is `02` and pairs of id and value; a write is `00` and the
pairs in id order. The ids: `01` PersoniFi on, `02` the hearing test mode, `03` the setup flow, `04` the test file, `05` the side being tested (1 left, 2 right) and `06` the tone's frequency in Hz in four bytes, low first; the
others take one byte. On protocol 4 they are features 7680 to 7684 (`PersoniFiMode`, `HearingTestMode`, `PersoniFiSetupFlow`, `HearingTestFileSide`, `HearingTestFileFrequency`: the names the app gives the Airoha race ids
`RACE_GET_BOOT_REASON` to `RACE_GET_DUMP_REGION_INFO`), each a status bit and level byte written as the app writes it (`StatusLevelSize1Feature`: the value in bit 7, 127 as the level), the side as 0 or 1 and the
frequency in four bytes. The wizard sends the setup flow flag as it starts (`pk.e.startPersonifiFlow`) and, when a tone is due, the side and frequency (`sk.q.q2`, `A1` ids 5 and 6 in one write); it plays the tone only
when the finger is down and the earbuds have answered with that frequency (`sk.q.Y1`, polled every 500 ms: the app's status SUCCESS); the hearing test mode the earbuds report is 1 while it runs and 3 or 4 when they end it
(a call, or the earbuds lost the phone's sound), on protocol 4 read from levels 1 to 4 as 2 to 5 (`gn.l0`), and the wizard then asks whether to continue. At the end, or when the user leaves, it sends the hearing test mode 0 and
the setup flow 0 (`pk.e.stopPersonifiFlow`).

**The curve's write.** The wizard puts the library's output on the earbuds as `EQSettings2` (`zk.c.a`, `ih.b.c`, `hh.c.Y0` with no keeping): category 201, calibration 0 (it is not worked out against the design EQ, as the BES
models' custom curves are), 48 kHz, the master gain for both ears, 24 bands (the left ear's twelve, then the right's, a band set off sent as type 5), and the EQ's own path writes it: A2 (with Q in tenths on the models that
mark it), or protocol 4's EQ info with the keep byte at 0 and, on the Airoha-library models, no EQ curve feature (`General4Control.setEQSettings2`). The earbuds' PersoniFi switch (`A1` id 1, feature 7680) is turned on by the app's
own screen; PairFree turns it on after the curve and offers it as a row.

**The tone.** The resource pack's files decoded (a plain read of the mp3s outside the repo): 14 s long, one earbud only, the last second silent from 13 s; a pulse of about 185 ms every 400 ms with ramps of about 10 ms; the pitch
swings about 11 % either way about ten times a second; 3 dB quieter for each second (the 1-second RMS values fall 3.00 dB each); and each frequency starts at its own level, the peak of its first pulse in dB below full
scale: 250 Hz -0.5, 500 -14, 750 -17, 1000 -20, 1500 -19, 2000 -18.5, 3000 -17, 4000 -15.5, 6000 -20, 8000 -11.5, 10000 -7.5, 12000 -2.5. PairFree makes its own tone to that description (`JblWarble`) and ships none of the files.
Phone volume is set to the top for the test, as PairFree's other hearing tests do (`TonePlayer.begin`); how the app handles volume belongs to the wizard's other pages, listed as open below.

**What PairFree builds** (all 30 models with the card): `JblPersonifiRun` (the order of the tones, the extra ones, the scale), `JblWarble`, the screen
(`JblPersonifiScreen`: hold while the tone is heard, let go when it is gone; the seconds held give the level, `caldB`; a tone that plays out counts as heard to the end; a button back one tone), the write and the switch. Never tried on JBL earbuds.

The Club One (`ClubOneControl.setEQSettings2`) picks the user's slot with 40 and sends the curve as the older 41 transfer in category 3 (`EnumEqCategory.PERSONIFY_EQ`; `BesEQControl.setEQSetting`), built too.

The Airoha models (Live Buds 3, Tour Pro 3) send the same over RACE (`MmiStage_SetPersoniFiStatus` 0063 and its read 0062: the on byte, the test status, the file, the side and the frequency in two bytes, low first, 255 for what is not set; `MmiStage_SetPersoniFiSetupFlow` 0073: the flag; `MmiStage_SetPersoniFiEQ` 0071: the band count, the left gain in hundredths of a dB in four bytes, per band its type and frequency, gain and Q in hundredths, four bytes each, and with 24 bands the right gain), built too. The wizard sets the hearing test mode on in the pages before the tones; PairFree does as it begins.

Open, in `data/completeness.json`: the wizard's pages before the tones (the sealing page with the noise check, the sealing status and the in-ear status, the 75 Hz seal tone per ear with the file side flag, the can't-hear page and the report of the levels in bands), whose device traffic PairFree has in its own noise check and seal check but not as the wizard's sequence.

### 4.16 The smart case's pictures, the unlock indicator and find my case

Read from `ScreenSaverCard`, `LockScreenCard`, `FindMyCaseFragment` (`wq.k`, `wq.l`), `ChargingCaseDisplayStatusCmd`, `General4Control.setChargingCaseDisplaySettingsStatus`, `MmiStage_SetChargingCaseDisplaySetting` and `fr.m` (2026-11-24). Display ids (section 4.11): 3 the screen saver picture (0 to 4 the built-in ones, 193 a custom picture), 6 whether a custom picture is there, 9 the unlock indicator, 13 find my case (1 plays a sound on the case, 2 stops it). Every dashboard with the card lists five built-in pictures (`defaultScreenSaver`). On protocol 4 they are features 7942, 7954, 7947 and 7951 (find my case: 0x81 plays, 0x02 stops, as `General4Control` writes the status and level) and the custom picture is removed by writing 193 to 7955; on the Airoha models they are bytes 3, 5, 11 and 19 of the 26-byte display array and the removal is the command `0089`. Removing the custom picture is behind a confirmation. Putting a picture on the case is `LockScreenOtaViewModel`: it builds `image_ota.bin`, checks its CRC32 and sends it through the case's firmware updater (`startOta` with `OTADfuInfo.IS_CASE_OTA`), which the hard rule on firmware-update traffic forbids; blocked: firmware transport; checked: `fr/m.java` (image_ota.bin, CRC32 checks, `startOta` with `IS_CASE_OTA`) and `ScreenSaverCard`, listed in the registry item.

### 4.15 The LE Audio switch

Read from JBL Headphones 5.37.10 (2026-11-24): `LeAudioCard`, `GetSetFeatureCmd`, `General4Control.setFeatures`, `TourPro3LeControl`, `Tune770ncControl` and `AirohaDeviceStatusControl.writeHmCommand`. The card calls `BaseControl.setFeatures({1: on})` after a dialog that says the earbuds restart their link. On the Harman models that is command `B1` (`GetSetFeatureCmd`): a read is `00` and, for each key, the key and `00`; a write `01`, the key, the size 1 and the value; the answer (`aab1`) holds the sub-command and then key, size and value. Key 1 is the LE Audio switch (`LEAudioStatus`), 2 Auracast and 4 accidental touch prevention. On protocol 4 it is feature 2976 (`LEAudioUniCastStatus`), a status bit and level byte. On the Airoha models the `B1` frame goes inside the passthrough `0093` (`MmiStage_SendHmCmd`), as the clock does; PairFree lets through only a read or a write of key 1. The Tune 670NC has the card but its control class does nothing for `setFeatures`, so it gets no row. PairFree shows the row once the earbuds answer the read, instead of the app's per-model capability table (`qm.c`'s `le_audio_unicast`). Never tried on earbuds.

## 5. How each model sends its settings

The ten older BES models with no dashboard config (`com.harman.cmdctrl.imp.bes.*SdkControl`: the Live 220BT, 300TWS,
400BT, 500BT and 650BTNC, Club 700BT and 950NC, Live Pro+, Tour Pro+ and UA Project Rock) are described from their
control classes and the product list instead: noise control as `31` and `32` where the class writes it with
`ProtocolVersion.VERSION_OLD`, else `91`; the modes JBL's old screens offer (noise cancelling alone on the Live 650BTNC,
where `HomeActivity` asks for it; noise cancelling where the product has a `dashboardModel`, as `DefaultDashConfig`
shows it; Ambient Aware and TalkThru otherwise); auto power-off by `autoOffType` (0 one byte, 1 the timer); pause when
taken off where `SettingActivity` shows it (Live 300TWS, Live Pro, Tour Pro TWS, UA Project Rock); Smart Audio & Video
and Find My Buds by `smartAudioVideo` and `hasFindMyBuds`; the EQ as the Club One's (`setEQPreset` is command `40`,
`setEQSetting` the `41` transfer). PairFree shows each setting once the device answers its read. Their gestures,
written in the older variant of the gesture command, are not covered.

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

### 6.1 The customized button page

23 protocol 4 models have JBL's newer gesture page (`ControlCard`, `ui3.fragment.gesture.customizedbutton`). Each
gesture is a protocol 4 feature (`DeviceInfoFeature5`: `0F04` to `0F06` and `0F0A` the left earbud's tap, double tap,
triple tap and hold, `0F07` to `0F09` and `0F0C` the right's, `0F80` and on the buttons and touch panels), read with
the other features and written by `General4Control.setP5GestureStatus` as the feature with one byte: the function's
value. Which gestures a model has and the functions each offers come from two files JBL's app bundles and reads
without its server (`vm.d`): `assets/gesture/product_gesture_config.json` by product id (13 of PairFree's models),
else `common_gesture_config.json` (10); `data/jbl/gesture_config.json` keeps their gestures, functions and values.
Functions: noise control `0B` (or the cycle the gesture has: `09`, `0C`, `0D`), TalkThru `04` (`0F` on some), volume
up `01` and down `02`, play or pause `08`, next `05` and previous `06` track, EQ on or off `C8`, spatial sound `C2`,
VoiceAware `B5`, the phone's voice assistant `A1`, none `00`. PairFree shows a gesture once its feature answers.
Left out: the EQ preset cycle (`eQControl`, `B2`), which JBL's app writes together with the presets it cycles
through (`setP5GestureCmd`), and "tap, then tap and hold", which is pairing mode and cannot be changed.

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
RACE frame against it before sending. The one exception is an Airoha PEQ write PairFree builds itself
(section 11.2): its NV key writes (`0A01`) and relays to the other earbud (`0D01`) go out as Airoha's SDK
sends them in JBL's app (owner, 2026-11-04), and no other.

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
- The equalizer (section 11) has no recorded frame: whether the earbuds answer the reads as JBL's app expects,
  and whether a curve that only cuts gets the 1 dB calibration (below), needs a device report.
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

Voice prompt languages and custom prompts (issue #1, 2026-11-19): JBL's app offers no way to load a prompt of the user's own. Its language list is a set of voice packs: the
Airoha models take them as an update flow (`AirohaOTATask`, `MultiLangVoicePrompt`, `VpLangTypeEnum`) and the protocol 4 models report `VoicePromptLanguage` and
`VoiceFileVersion` (`DeviceInfoFeature5`). blocked: a language pack is a firmware-class transfer whose file comes from JBL's servers (hard rules: no server contact, no firmware traffic);
checked: JBL Headphones 5.37.10, `General4Control`, `HandleParse`, the Airoha adapter classes, and no screen that lets the user choose a file of their own.

**Touch controls** (section 10.1) are covered on the models whose page JBL's app has. **Not covered yet:** the noise cancelling and Ambient Aware level
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

### 10.1 Touch controls

Airoha's SDK in JBL's app keeps each earbud's touch controls as a key map of 8-byte entries: the key type, a key
id, the event (two bytes, low first) and a state (four bytes). JBL's gesture ids (1 and 2 tap, 3 and 4 double tap,
5 and 6 a second long press, 7 and 8 triple tap, 49 and 50 hold; odd on the left) and action ids are the same as
on JBL's other models (section 4.9). How they become entries depends on the chip, as `AirohaConnector` sets the
chip type:

| Variant | Chips | Key map | Key id |
|---|---|---|---|
| v3 | AB158x; AB1568 and AB1565 dual with an NVDM version above 1 | NV key `F2D6` | 126 at first, then the last entry read |
| 1568 | the other AB1568 and AB1565 dual | NV key `F2E7` | 24 |
| plain | AB155x | NV key `F2E7` | 126 |
| ab1562 | AB1562 | key map commands `0A0B` (read) and `0A0A` (write) | 0 at first, then the last entry read |

Every variant keeps a copy of the other earbud's map in `F2EF`. The read (`MmiStage_GetGesture*`) asks which earbud
PairFree talks to (`0CD4`), on an AB1568 the NVDM version (`1001`), then reads the earbud's own map (`0A00`, length
100) and, relayed (`0D01`), the other's; an empty map is every gesture at 0. A write (`setGestureInfo`) takes the
page's gestures for one side plus what the read found for the rest, then writes the earbud's own map (`0A01`), the
copy of the other's (`0A01` `F2EF`) and reloads its key (`0A09`), and on earbuds does the same relayed to the other
earbud; the AB1562 writes its map (`0A0A`), then relayed. Which entries an action writes, and which action a map's
entries mean, are tables in `data/jbl/commands.json` (`airoha.gesture`), derived from JBL's own methods run for every
gesture and action outside the repo; `test_vectors.json` (`airoha_gesture`) keeps 96 of those writes and reads.
Two quirks kept: the older chips have no entry for action 10 (play/pause that also dismisses the assistant), so it
writes nothing there, and the plain variant's read leaves out a gesture it cannot name instead of setting it to 0.

Which sets each model gets follows JBL's pages: the dashboard's gesture card (`sp.a`; Live Buds 3, Live Free 2,
Quantum TWS and TWS Air, Reflect Aero) has the sets of section 4.9 with the second long press at 0, and on the
Quantum TWS the low latency switch (14) on the triple tap of the noise control and volume sets; the newer gesture card
(`GestureUi4Card`, `yp.q`) on the Endurance Race 2 takes its sets from the product list's `customizedGesture`
(playback 8, 5, hold 161, 6; noise control 11 or the cycle the tap has, 4, 161, 0; volume 1, 2, 161, 0; none), in that
order of tap, double tap, hold and triple tap; the older settings
page (`em.o`, `AppUtils.setCommandAiRoHaGesture`; Reflect Mini NC, Live Free NC+) has playback (8, 5, 6, hold 161),
noise control (9, 4, 0, 161), volume (1, 2, 0, 161), the phone's voice assistant on the hold (one earbud at a time;
its tap, 160, writes nothing on these chips) and none. The Club Pro+ has no gesture page; the Tune 670NC, 770NC,
775NC and 777NC have no gesture card; the Tour Pro 3, Wave Beam 2 and Vibe Beam 2 have the newer card but no sets in
JBL's product list, so JBL's app offers nothing there either.

## 11. Equalizer

Read from JBL's app (`EQCmd`, `BesEQControl`, `CmdGen`, each model's control class, `AppUtils.getCalib`) and its
calibration library (`libcalibrationLib.so`, whose functions keep their names: BES's `app_audio_eq_*`, `freqz`,
`iirfilt_design`); no recording yet. `devices.json` gives each model's `eq`: how its class writes it (`path`), its
presets (a file of `assets/eq` or its series' standard ones), the custom curve's range (`eqGainRange`), whether the
class reads the design EQ, and whether JBL's app works out the calibration (`calibrate`).

**Paths.** `a2` and `a2_q10` (33 models) send command `A2`, below; `a2_q10` are the classes JBL's app marks
`QMultiply10EQ`, which send Q in tenths. `p4` (35 models) sends protocol 4's EQ info, below; on the seven whose EQ library
is Airoha's (`lib`), JBL's app sends the curve as Airoha's PEQ as well (section 11.3). `41` (10 models) and `club` (the Club One) are the older BES classes' transfer,
below. `segment` (the Tune 520BT, 525BT and 527BT) is Realtek's segment EQ (section 11.1) and `airoha` (19 models)
Airoha's PEQ over RACE (section 11.2).

**Command A2** (floats four bytes, low byte first):

| Bytes | Read | Reply / write |
|---|---|---|
| | `AA A2 02 01 <category>` (one-byte length) | `AA A2 <length, two bytes, low first>` |
| 0 | | `02` reply (`04` Q in tenths); `00` write (`03` Q in tenths) |
| 1 | | category |
| 2-5 | | calibration |
| 6 | | sample rate in kHz (48) |
| 7-10, 11-14 | | left and right gain in dB |
| 15 | | band count |
| then 10 a band | | type, gain (dB), frequency (Hz), Q (one byte) |

Categories: a preset's number (its `category` in `eq_presets.json`), `230` a curve of the user's own, `202` the design
EQ (read only), `201` PersoniFi, `255` to ask for the EQ in use. JBL's app reads `202` (where the class does) and `255`
on connecting; PairFree does the same and reads `255` again after a write.

**Protocol 4** (`General4Control`): the EQ in use is asked for by writing feature `0E01` (`EQInfoQuery`) with the
category, one byte (`FF`); the earbuds answer with feature `0E02` (`EQInfo`). A write is `0E02` too: the category (`C1`,
193, for a curve of the user's own, read back as 230), 1 to keep it (as the EQ card sends; live edits send 0), the band
count, the calibration, the sample rate (four bytes, 48), the left and right gain, then per band its type, frequency,
gain and Q (13 bytes); numbers four bytes, low first. JBL's app reads the design EQ as feature `0E7E`
(`GetDesignEQRawData`) but only stores the raw bytes, so on protocol 4 it never holds a design EQ and its BES models'
calibration is always -1; PairFree sends the same. `TourOneM3Control` (the Tour One M3, Sense Lite, Endurance Zone, Tune
135BT and 235BT) writes a curve of the user's own at half its gains on firmware up to 4.2.0, and doubles what the
earbuds report (`halve_custom_until`); unknown firmware counts as old.

**The older BES classes** (`BesEQControl`, `CmdGen`, `EqCmdData`, `HandleParse`; all over Bluetooth LE). A write is
`AA 41`, the payload's length (one byte) and the part count, sent as four bytes; then the payload in parts of 79 bytes,
each led by its index; then `AA 05 41`. The payload: the preset index (4, the user's, for every curve), the category (1
for a curve of the user's own; 0 for a preset, as `BesEQControl` maps preset categories), the calibration, the sample
rate (48), the left and right gain, the band count (four bytes), then per band its type (four bytes), gain, frequency
and Q, numbers four bytes, low first. A read is `AA 42 01` and the category (0 the design EQ, 1 the user's curve); the
earbuds answer `AA 43` with the part count, JBL's app acknowledges with `AA 01 02 43 00` (as many times as there are
parts, plus two), the parts follow as notifications of their own led by their index, and `AA 04 43` ends it. The reply's
payload is the write's without the calibration; it names no preset, so JBL's app keeps the choice on the phone, and so
does PairFree. JBL's app reads the design EQ (and the user's curve) on connecting; PairFree reads the design EQ alone,
for the calibration. The Club One picks a preset with `AA 40 01` and its category, and before a curve of the user's own
selects the user's slot (`40` with 4).

**Curves.** JBL's presets are ten bands at 32, 64, 125, 250, 500, 1k, 2k, 4k, 8k and 16k Hz, mostly peak filters
(type 1) at Q 1, with the gains in the file; a model's file may add a left and right gain. A curve of the user's own is
the same ten peak bands at Q 1 (type 2 on Airoha's chips) with whole-dB gains in the model's range, and gains 0. Band
types follow the Audio EQ Cookbook: 0 low shelf, 1 peak, 2 high shelf, 3 low pass, 4 high pass, 6 band pass, 7 band
pass at 0 dB, 8 notch, 9 all pass.

**Calibration.** On the models whose platform is BES (`calibrate`), JBL's app sends a calibration with every EQ
(`AppUtils.getCalib` in `gn.y.f`, which both dashboard EQ cards and the EQ pages write through); the other platforms
send 0, and with no design EQ stored it sends -1. The library (`calculateCalib`) designs each band of the design EQ and of the new curve as a biquad at 48 kHz
(the Cookbook's formulas, with sine and cosine in single precision; its band pass at 0 dB has `b2` positive), takes the
magnitude at 4096 points from 0 to 24 kHz for the design EQ alone and for both together, and when the combined peak is
higher, returns that peak in dB plus 1 dB, or 0 when the design EQ's own left gain is already at least that much.
PairFree's `JblCalibration` is a port of it. A curve that only cuts meets the design EQ at 0 Hz, where every peak
filter is exactly 1, so the library gives 0 or 1 dB there by rounding.

### 11.1 Realtek's segment EQ: `A6`

The Tune 520BT family (`Tune520BTControl`, platform Rtk, over Bluetooth LE) sends its EQ as `A6` (`SegmentEQCmd`,
`RtkEqUtils`, `RtkEqParameterConverter`, `RtkSrEntity`) with coefficients from Realtek's native `libDspConfig.so`
(`DspConfig.calculateEq`). Its functions keep their names (`PeakFilter`, `ShelvingLPFilter`, `FilterCoefFix`,
`SignedQuant`, `FreqResponse`, `calculateDiffMaxAndMinDb`, `AssembleModuleData`); PairFree's `RealtekDsp` is a port of
them, read from the arm64 code and checked against the library itself, run in an emulator outside the repo (1000
random curves' coefficients, and five whole outputs, byte for byte; `test_vectors.json` `segment_eq` keeps the cases).

**Reads.** `AA A6 02 01 <category>` (one-byte length): `202` the design EQ, `255` the EQ in use; JBL's app sends both
on connecting, and so does PairFree. The answer comes in parts, `AA A6 <length, two bytes, low first>`, then `04`, the
provider (`01`), the part count, the part's index and a slice of the data; JBL's app acknowledges each part but the
last with `AA 01 02 A6 00`. Joined, the data is the Harman curve: the category, the band count (low nibble) and the
sample-rate code (high: 3 44.1 kHz, 4 48, 6 96), a spare byte, the master gain in hundredths of a dB (two bytes,
signed), then five bytes a band, low bits first: the type (3 bits; Realtek's 0 is a peak, 1 a low shelf, 2 to 6 as
Harman's), Q in hundredths (10 bits), the gain in hundredths (12 bits, signed) and the frequency (15 bits). One byte
or none is JBL's default curve.

**Writes.** Without the design EQ JBL's app sends nothing; PairFree the same. The content is the category, the block
count (2), a coefficient block at 44.1 kHz (rate code 3) and one at 48 kHz (4), then the Harman curve as above. A block
is the rate code, the design EQ's band count, the largest gain the curve adds over the design EQ in 1/128 dB (two bytes,
low first), the data's length plus 4 (two bytes), `00 00 00 01`, then the library's stage data: a word with the stage
count (bits 0 to 4), `010` (bits 5 to 7) and the master gain in Q12 (bits 8 up), then per stage `b0`, `b1`, `b2`,
`a1` and `a2` as Q30 integers, four bytes each, low first. The stages are the curve's bands followed by the design EQ's
(at most 20); the master gain is the two curves' gains added (the library gives up beyond 12 dB, and PairFree then
sends nothing). The content goes in parts of the negotiated MTU less 18 bytes, each `AA A6`, a two-byte length, `03`,
the provider, the part count and the index. PairFree reads `255` again after a write.

**The library's filters** (all second order): peak (`PeakFilter`, `w0/(2Q)` and, for cuts, `w0/(2QA)` as its
bandwidth term), low and high shelf (`Shelving*Filter`, the Cookbook's with Q as the shelf slope and the square root of
the linear gain as its A), low and high pass (Butterworth, the frequency held to 20 Hz to 20 kHz, the low pass's gain
to -12 to 6), band pass, band reject, all pass (whose last denominator term the library leaves at 1). When a
numerator term reaches 2 the numerator is divided by its largest term, which goes into the master gain. `b1`, `b2`,
`a1` and `a2` are rounded to the Q30 grid before the response is taken; the largest gain is the highest ratio of
the whole response (master gain, curve and design EQ) to the design EQ's alone over 65536 points from 0 to the
Nyquist frequency. One quirk is kept: the library designs the design EQ's bands with the types of the curve's
first bands.

### 11.2 Airoha's PEQ: RACE

The Airoha models (`AirohaEQControl` in Harman's adapter, then Airoha's SDK: `AirohaPEQControl` on the AB155x,
`AirohaPEQControl1568` on every other chip, both through `AirohaPeqMgr` and its stages) write a curve as Airoha's
PEQ, with coefficients from Airoha's native `libnative-peq.so`. Its functions keep their names (`generate_cofe`,
`change_rescale_cofe`, `generate_freq_resp`, `calc_z`, `rpoly_ak1` and the rest); PairFree's `AirohaPeq` is a port of
the ones the SDK calls, read from the arm64 code and checked against the library itself, run in an emulator outside
the repo with Android 17's own libc and libm (300 random curves byte for byte; `test_vectors.json` `airoha_peq` keeps
30). The library sorts the bands by gain with a comparator that only looks at whole decibels, so the order of gains
less than 1 dB apart is whatever the phone's `qsort` makes of it; PairFree follows Android 17's (bionic's
unstable sort: median-of-three pivots, branchless partitions, heapsort past a depth limit). Another Android
version's `qsort` may order such bands differently, as JBL's app on it would too.

**The curve.** JBL's app sets the sample rate to 48 kHz and passes the category through Harman's adapter, which moves
JBL's preset numbers 0 to 99 up by one; a curve of the user's own goes as Harman's custom EQ, 103. Each band becomes
`PeqBandInfo`: its frequency, gain and bandwidth (frequency over Q, in single precision), each printed and read
back as a double (`Converter.getFloatAsDouble`); the master gain is the curve's left gain. The library makes, per
sample rate (44.1 and 48 kHz, and on the AB158x also 88.2 and 96), the band count, each stage's `b0`, `b1`, `b2`, `a1`
and `a2` as 32-bit numbers in two shorts (high first; Q23 and Q22 values scaled to 2^31 and 2^30) and the master
gain in Q26, after scaling each stage so the running response stays within range (a headroom of 1.19, and 18.0618 dB
of room for the master gain).

**Which slot.** The AB155x keeps JBL's presets on the earbuds: a preset is picked by its group (`0900` module 0 and
the group's position in the PEQ subset), and a curve of the user's own goes to custom slot 2 (103 less 101). The other
chips take every curve, presets included, into custom slot 0, and on category 1 (JBL's Off) only the first band is
enabled.

**The write** (`UpdateAndSavePeqData` with save on), each step waiting for the answer to the one before; any step
that fails ends it:
1. `0E2B` (sync of the UI data with the firmware): the UI data, then the peak in hundredths of a dB (four bytes). No
   answer awaited.
2. `0E03` (real-time PEQ): `00`, the block count (two bytes), `00 00`, then per rate its id (1 44.1 kHz, 2 48, 5 88.2,
   6 96; two bytes), the short count (two bytes) and the shorts, low byte first.
3. On earbuds `0D00` (available destinations): the answer's pairs of type and id; type 5 is the other earbud.
4. NV key writes (`0A01`, the key then the data), each relayed to the other earbud first on earbuds (`0D01`: the
   destination, then the whole frame; the answer comes as an indication of `0D01` holding the other earbud's frame):
   the UI data at `EF00` plus the slot (keep-rescale 1, four zero bytes, then ten bands of 18 bytes: enabled, type 2,
   then frequency, gain, bandwidth and Q in hundredths, four bytes each), the extra data at `EF10` plus the slot
   (category, payload id 0, sample rate 48000, four bytes each; the band count, two; the master gain in hundredths,
   four) and the peak at `EF20` plus the slot (hundredths of a dB, four bytes).
5. NV key reads (`0A00`, the key and 1000): the audio path (`F233`, or `E401` on the AB158x and on an AB1568 or
   AB1565 whose NVDM version, NV key `1001`, is above 1), whose first entry with a zero second field names the PEQ
   subset's key (else the group base, `F234` or `E402`); then the subset: a count, then per group its entries (a count,
   then four bytes each, the last two the coefficients' NV key). A key between the coefficient base (`F260`, or `E410`)
   plus 28 and plus 31 is custom slot 0 to 3.
6. The coefficients at the coefficient base plus 28 plus the slot (the blocks of step 2 without the leading `00`);
   the subset with one more group for that key unless its hex already appears in it (count up by one, then
   `01 00 00 00` and the key); `09FD` (save status): `00` and the key. Each relayed first on earbuds.
7. The audio path and subset read again, then `0900` module 0 with the group (the custom slot's position in the
   subset, or one past the end), and `0901` module 0 to read it back.

Headphones (Airoha's `HEADSET`) skip the relays and step 3. Airoha's SDK also has an NV reclaim stage for the AB1562;
its manager never queues it, and PairFree does not send it. PairFree does not read the curve back yet (the SDK reads
the group and the UI data), so it keeps the choice on the phone, as for the older BES classes.

### 11.3 Airoha's PEQ on protocol 4

The seven protocol 4 models whose product entry names Airoha's EQ library (`eqLibInfo`, platform airoha: the Tour One
M3, Endurance Zone, Tune 680NC, 780NC and 785NC, Live Buds 4 and Tune Buds 3) get, in the same set as the EQ info,
feature `0E7F` (`EQCurveFeature`): a two-byte length, then `AirohaEQSerialize.serializePEQ` (type 0) over Airoha's
second library, `libnative-peq-serialize.so`. JBL's app sends every band of these models as type 2 (Airoha's peak),
in the EQ info too. The serialized curve is the coefficients (type `00`, a two-byte length, the model's rate count
and three zero bytes, then per rate in the order 44.1, 48, 88.2, 96 kHz its id (1, 2, 5, 6), a zero, the short count
and the shorts, low byte first) and the peak (type `03`, length 8, the response's peak in hundredths of a dB, four
bytes, twice). The rates are the model's `eqLibInfo.sampleRates` (`rates` in `devices.json`). With JBL's settings
(`setParam` with calibration -1, so mode 1, and a last argument of 1) this library gives what the one in section 11.2
gives, but it takes Q directly, sorts the bands with a strict comparator (higher gain first), and keeps the peak in
single precision; `AirohaPeq.Library.SERIALIZE` does the same (150 random curves byte for byte against the library;
three whole curves in `test_vectors.json`). The set is longer than one packet, so it goes in parts of the MTU less 15
bytes, each with the part count and its index (`AssembleCmd4`, `JblP4Packet.frames`).

