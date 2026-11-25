# Sennheiser

Sennheiser's control protocols, read from the company's two apps (decompiled outside the repo on 2026-11-15) and built for the products on **GAIA v3** and, over the serial port, the older **GAIA v2** and
**Nautilus** headphones. No Sennheiser product has been tried with PairFree.

## 1. Sources

- **Sennheiser Smart Control 4.9.4** (`com.sennheiser.control`, versionCode 175297). A NativeScript app whose protocol is plain JavaScript and JSON: every product has a product file
  (`assets/app/<model>.json`) listing its protocol, its shared feature files (`assets/app/features/`) and its *properties*: each one a thing the app can read, write or hear, with the commands
  that do it, their ids and typed parameters. `data/sennheiser/commands.json`, `devices.json`, `gaia2.json`, `nautilus.json`, `touch.json` and `eq.json` keep the facts PairFree builds from them
  (`tools/import_sennheiser_app.py`); the app's files are not in the repo. The app's JavaScript is read for what each product's class sets up (`createProductFeatures`, `mapToFeature`,
  `sendFeatureToDevice`), which is what decides the rows and the values written.
- **Sennheiser Smart Control Plus 1.6.3** (`com.sonova.chb.control`). A Flutter app (Dart 3.10.8 AOT, obfuscated; its arm64 build was read with Blutter and a small annotating disassembler). It
  names the newer products and which platform each is on (section 2) and uses the same GAIA v3 implementation (its frame handler accepts exactly the vendors `0x001D` and `0x0495`, versions 3 and
  4, no checksum).
- The full research notes, the catalogue of every command of the 24 products in Smart Control and the Dart enums are outside the repo (`E:\Builds\research\sennheiser`).

## 2. Products and platforms

The two apps use four control protocols between them:

| Protocol | Products | PairFree |
|----------|----------|----------|
| **GAIA v3** (Qualcomm's framework; vendors `0x001D` Qualcomm's and `0x0495` Sennheiser's own) | Accentum, Accentum Plus, Accentum True Wireless, CX 200, CX 200 Sports, CX Plus True Wireless (CX400II), CX 500, Momentum 4, Momentum Sport, Momentum True Wireless 3 and 4 (Smart Control); Momentum 5, Momentum True Wireless 5, HDB 630, HDR 275 / 255 (Smart Control Plus) | built (sections 3 to 4.8, 4.11); the CX 200, CX 200 Sports, CX Plus and CX 500 over Bluetooth LE (section 3.1) |
| **GAIA v2** (vendors `0x0494` and Qualcomm's `0x000A`) | CX 350BT, HD 250BT, HD 350BT, HD 450BT, HD 450SE, HD 458BT, IE 80S BT (the serial port); CX 400BT, Momentum True Wireless 1 and 2, CX 150 (Bluetooth LE only) | built (section 4.9); the CX 400BT and Momentum True Wireless 1 and 2 over Bluetooth LE (section 3.1) |
| **Nautilus** (Sennheiser's own frame, CRC-8) | Momentum 3, PXC 550, PXC 550-II | built (section 4.10) |
| Airoha RACE | Accentum Clip (AOWS1) | not built (section 6); checked: Smart Control Plus's Dart code sends only this product to its Airoha platform, and its RACE feature variants were found but their commands not traced |
| Soundbars (Elmar over BLE; Wi-Fi) and the HDW 960 | AMBEO Soundbar | speakers are held back (owner, 2026-10-30) |

How the platform is known: Smart Control Plus identifies a product from Sonova's advertisement data and a jump table in its Dart code sends every product to "herakles" (Qualcomm GAIA 3) except
the Accentum Clip and an unknown Airoha product ("airoha") and the HDW 960 and the soundbars ("sue"). So the **Momentum 5 and Momentum True Wireless 5 are GAIA v3**, as the Momentum 5 report
in issue #47 suggests (`00 04 06 07 0c 0d` is a GAIA 3 feature list). Smart Control sends the Momentum 5/6, True Wireless 5/6, HDB 630, HDR 275, BTA1, AOWS1 and others to the Plus app.

Which transport Android opens for each product is in Smart Control's `DeviceConfig`: the serial port for the Accentum family, Momentum 4, Momentum Sport, Momentum True Wireless 3 and 4 (GAIA v3), for the
GAIA v2 headphones named above and for the Nautilus ones; Bluetooth LE only for the CX 200, CX 200 Sports, CX Plus, CX 500, CX 400, CX 150 and Momentum True Wireless 1 and 2.

## 3. Transport and framing

- **GAIA v3, classic Bluetooth:** RFCOMM service `a2129ff3-081b-4c45-8afe-469d9c4842ec` (Sennheiser's own: it is what PairFree identifies a Sennheiser device by besides its name). The LE ones use service `FCFE`
  (Sonova) or `FDCE` (Sennheiser), characteristics `6333133B` write and `6333133C` read, with no frame (vendor, word and payload only).
- **Frame:** `FF`, version `03`, flags (bit 0: an XOR byte follows; bit 1: two length bytes), the payload's length, the vendor (u16), the command word (u16), the payload; big-endian throughout.
  PairFree sends no checksum and one length byte (what `SPPGaiaFramer` writes), and accepts both from the earbuds.
- **Command word:** feature << 9 | type << 7 | command. Type 0 is a command, 1 a notification, 2 a reply (the command + `0100`), 3 an error (+ `0180`). The Sennheiser features are registered with `0495:0007`
  and a feature id: core 0, device 2, battery 3, generic audio 4, user EQ 8, versions 9, device management 10, MMI 11, transparent hearing 12, ANC 13, fit test 15, personalised sound 16, Auracast 21,
  find device 22.
- A reply and a notification of the same property carry the same bytes (the product file's `Event` is its `Response`). One command goes out at a time, each answered by its reply or an error; Smart Control
  waits 3 s and tries again.
- **GAIA v2:** `FF`, version `01`, flags, the payload's length (no command header), the vendor (`0494`, or Qualcomm's `000A` for a few), the command and the payload; a response is the command with bit 15 set and
  its payload starts with a status byte (0 is success). The serial UUID is `a2129ff3-081b-4c45-8afe-469d9c4842eb` (the last letter differs from GAIA v3's).
- **Nautilus:** see section 4.10.

### 3.1 Bluetooth LE

Owner, 2026-11-15: Bluetooth LE may be used for any vendor whenever its app needs it. The products Smart Control's `DeviceConfig` opens only over LE (CX 200, CX 200 Sports, CX Plus, CX 500, CX 400BT, Momentum True Wireless 1 and 2)
use the service `FDCE` (the Momentum True Wireless 1: `63331337-23C1-11E5-B696-FEFF819CDC9F`) with the characteristics `63331338` write and `63331339` notify (the CX 200, CX 200 Sports, CX 400BT, Momentum True Wireless 1 and 2)
or `6333133B` and `6333133C` (CX Plus and CX 500), all `-23C1-11E5-B696-FEFF819CDC9F`; `data/sennheiser/devices.json` and `gaia2.json` keep each. A packet is the vendor, the command word and the payload with no `FF` frame
(`GattLink` carries it; the sessions add and take away the frame). PairFree connects to the classic address first and scans (`SennheiserFinder`: the services `FDCE`, `FCFE` and `63331337`, ten seconds, `BLUETOOTH_SCAN` asked when the user opens
such a model) when that fails. The app's own bonding steps run when the device is not bonded: `RESET_REQ` (0) then `USER_ACTION_REQUIRED_REQ` (1) to the pairing characteristic `63331379`, the user presses the earbuds' buttons
as the app's screen shows, and the encrypted data characteristic (`6333133D` or `6333133A`) is read every second until Android reports the bond, for a minute at most. checked: the order and the codes are the app's
(`BlePairingScreenService.startBonding`, `BondingBLEEffects`); the earbuds' own answers on `63331379` (`PAIRING_ENABLED_TIMEOUT`, `BLE_PAIRING_DISABLED`...) are not acted on, and none of it has met a product.

## 4. Built

Each is a property of the product files (`data/sennheiser/commands.json`), read once the channel opens and shown once its read answers; a model with a product file reads only what the file lists, one without
(the Momentum 5, Momentum True Wireless 5, HDB 630, HDR 275) reads them all and shows what answers:

| PairFree | Smart Control's property | Reads and writes (vendor `0495`) |
|----------|--------------------------|----------------------------------|
| battery (left, right, case) | `Battery_Percent` | `0603` / reply bytes: left, right, case; 255 unknown; one byte on a headband. A zero in the pair shows the larger on the left, as the app does (`mapBatteryValue`) |
| noise control: off, noise cancelling, transparency | `ANC_Status` (`1A05`, set `1A04`) and `TransparentHearing_Status` (`1805`, set `1804`) | turning one on turns the other off first, as the app's feature code does |
| anti-wind, adaptive noise cancelling | `ANC` (`1A01`, set `1A00` with mode and state): pairs of mode and state, 1 anti-wind, 2 comfort, 3 adaptive | |
| pause music in full transparency | `TransparentHearingMode` (`1801`, set `1800`) | |
| on-head detection, smart pause, answer calls automatically, comfort call, low latency mode | `Setting_OnHeadDetection` (`0401`/`0400`), `Setting_SmartPause` (`080D`/`080C`), `Setting_AutoCall` (`080B`/`080A`), `Setting_ComfortCall` (`0815`/`0814`), `LowLatencyMode` (`0818`/`0817`) | |
| lock the touch controls, eco mode, battery protection | `Setting_AutoLockMode` (`1607`/`1606`; the app: "to turn touch control on/off"), `Power_EcoMode` (`0609`/`0608`), `Power_BatteryProtectionMode` (`060B`/`060A`) | |
| voice prompts and tones | `Setting_AudioPromptMode` (`0802`/`0801`): 0 off, 1 tones, 2 tones and voice | |
| codec in use; firmware; model number; charging | `Sound_CodecUsed` (`0800`), `Service_SystemReleaseVersion` (`1201`: three u16), `Versions_ModelId` (`1206`), `Battery_ChargingStatus` (`0602`) | read only; the last two are in the device report |
| equalizer | three generations, picked by what the product answers (section 4.1) | `eq2_*`, `eq1_*`: vendor `0495`, feature 8 (`1000` to `1109`), and `audio_mode` (`0804`/`0803`) |

The status of every write is read back a moment later. `SennheiserTest` pins the frames and the rebuilt bytes of the writes.

### 4.1 The equalizer

Smart Control 4.9.4 has three, each ported from its JavaScript (`Eq5BandGen2`, `Eq5BandGen1`, `Eq3Band`, `EqKnobValuesCoder`; `SennheiserEq` keeps JavaScript's rounding and truncation) and its numbers from
`EqPresetsService` and `get5BandParametersForDevice` / `get3BandParametersForDevice` (`data/sennheiser/eq.json`, written by the importer). The Sound tab opens over the bands of whichever answers:

1. **user-eq-2** (a model with `gen2` in `devices.json`: Accentum, Accentum Plus, Accentum True Wireless, Momentum 4, Momentum Sport, Momentum True Wireless 4): `UserEQ_GetConfiguration` (`1000`: bands, minimum and maximum in
   tenths of a dB, preset counts) then one `UserEQ_GetSliderGain` (`1002`, band index) per band; a write is `UserEQ_SetSliderGain` (`1001`, band, gain in tenths as one signed byte). Bass boost is the
   sub-mode (`1009` read, `1008` write, 1 on; its notification `1089` is followed). The bands are 63, 250, 1000, 4000 and 8000 Hz, plus or minus 6 dB.
2. **user-eq-1, five bands** (from the firmware each product file names, `Eq5Band_Gen1_MinFwVersion`): the earbuds hold a shelf filter set the app calculates from the band gains: bass boost, loudness compensation,
   clipping prevention and the shelf gains give a parameter group (`EQ_UserParameterGroup_Set`, `1002`, signed 16-bit values), the level shift in tenths of a dB (`1006`) and the knob's flags (`1004`, index 1:
   new storage `0x4000`, bass boost `0x1000`). A read is the level shift (`1007`), the knob (`1005`) and the parameter group read (`1003`).
3. **user-eq-1, three knobs** (older firmware, and the CX models): bass, mid and treble, centred on their mean and cut to the product's gain limit, as a parameter group plus the two knob values (`1004` index 0 and 1).

Every curve is written after `AudioMode` USEREQ (`0803`, 1) when the product's last answer was another mode, as the app does when the equalizer is used. Smart Control's presets are curves over those bands, so
they go as curves; PairFree's gains move in half dB (the earbuds of the first kind hold tenths, the others the app's rounded numbers). The replies of the two user-eq generations share command words (`1002` is
gen 2's slider read and gen 1's parameter write), so the session decodes a feature 8 reply only against the read it sent. `SennheiserEqTest` pins the parameter groups against a port of the app's JavaScript
run outside the repo, and the frames each generation sends. The Plus-only products (Momentum 5, Momentum True Wireless 5) have no product file in either app, but Smart Control Plus has the feature layer for any GAIA v3 product (`Equalizer2Gaia3FeatureVariant`, `PEQEqualizer2Gaia3FeatureVariant`; its objects name graphic and parametric equalizers with high pass, low pass, shelf and bell stages), so PairFree asks them for the second-generation equalizer (`UserEQ_GetConfiguration`) and shows it when they answer, without presets (Sennheiser's per-product preset curves are server data). **Open (issue #47, a Momentum 5 on 0.14.0 showed no equalizer because PairFree asked nothing):** whether the Momentum 5 answers as the second generation or as the parametric one is settled by its next device report; checked: the Plus app's Dart objects (`E:\Builds\research\sennheiser\tools\dartx.py`) name both feature variants, but their command numbers sit in classes whose payloads were not traced.

### 4.2 Settings the product classes set up

Each product's class in the app's JavaScript (`createProductFeatures`) says which of these it offers; PairFree reads a property only for the products whose screen uses it.

| PairFree row | Property and words | Notes |
|--------------|--------------------|-------|
| sidetone (your own voice on calls) | `SidetoneState` (`0806`/`0805`, one byte): 0 is off | the highest level is `phoneCallsMaxValue`: 4 on the Accentum Plus and Momentum 4, 2 on the others; the CX models name 0, 1, 2 off, min, max |
| automatic power off | `Config_GetTimer` (`0601`, timer 0) / `Config_SetTimer` (`0600`, timer 0 and seconds) | the app's choices are never, 15, 30 and 60 minutes (`defaultAutoPowerOff`) |
| Bluetooth compatibility mode | `BluetoothCompatibilityMode` (`0406`/`0405`): 1 better compatibility, 0 better audio | Momentum 4, Momentum Sport, Momentum True Wireless 3 and 4 |
| transparency level, Accentum Plus and Momentum 4 | `ANC_Transparency` (`1A03`/`1A02`, 0 to 100): the app's `slider` | between full noise cancelling (0) and full transparency (100), as a Momentum 5 owner described it and his report shows (`ANC_Status` 1 with level 0, issue #47; the Momentum 4 and Accentum Plus use the same property and are assumed the same); shown beside the three-way noise control (section 7) |
| transparency level, the earbuds | `TransparentHearing` (`1803`/`1802`) | the app writes 1, 50 or 100 (`transparent_hearing_slider_low`, `mid`, `high`) |
| tap sensitivity | `TapDetection` (`2601`/`2600`): low 2, mid 1, high 0 | Momentum Sport |
| open ear tip support, hold to disable the touch area | `Sound_OpenEartip` (`0811`/`0810`), `Touch_Locked` (`1609`/`1608`) | CX Sport, Momentum Sport |
| aptX Lossless | `AptXLosslessSupport` (`0408`/`0407`) | Momentum True Wireless 4 |
| wear state, sample rate, voice prompt language | `PhysicalDevice_State` (`0402`), `Sound_Sample_Rate` (`081A`), `VoicePrompt_Language` (`0807`) | read only; checked: the language's Set names a command without any id in every product file, so the app cannot write it either |
| restart | `Reboot` (`060F`) | an action the screen confirms (owner, 2026-11-03); the app restarts the earbuds after an eco mode change |

### 4.3 Touch and button functions

Smart Control's class for each product lists the functions its screen offers (`availableActions`), with the gestures each is allowed on (`constrainedTo`, none listed meaning any). The app reads each earbud's four gestures
(`GetMMIConfig` `1601` with the button, 0 right and 1 left, and the gesture, 1 single, 2 double, 3 triple, 4 long press) and shows a gesture when the answer's function is one of the product's. A write is
`SetMMIConfig` (`1600`, button, gesture, function); `SetDefaultMMIConfig` (`1604`) restores the product's own, and `IsDefaultMMIConfig` (`1605`) says whether they are. The functions' numbers (`data/sennheiser/touch.json`):
play or pause 1, next 2, previous 3, volume up 4, volume down 5, transparent hearing 6, voice assistant 7, noise cancelling 9, lock or unlock 13, sound mode toggle 15, open ear tip 16, noise cancelling and
transparency toggle 18, bass boost 21. Volume functions are allowed on a long press only; the Momentum Sport's are double and triple taps. Functions a class adds for newer firmware (CX Sport: open ear tip and lock from 2.9.5;
Momentum Sport: bass boost and the sound mode from 3.17.3) wait for the firmware the earbuds report. The Accentum, Accentum Plus and Momentum 4 offer none: their `availableActions` is empty, so the app only shows their fixed
gestures, and so does PairFree (the lock switch of section 4 stays).

### 4.4 Find device

`FindDeviceRingSeparateDevice` (`2C02`, the side: 0 right, 1 left), `FindDeviceStopRinging` (`2C01`) and `FindDeviceIsDeviceRinging` (`2C03`, a side; the answer is the notification `2C83` naming whether it rings and which side).
Feature 22 is registered. Accentum True Wireless, Momentum Sport and Momentum True Wireless 4, from the firmware their product files name.

### 4.5 Personalised sound and podcast mode

`AudioMode` (`0804`/`0803`): 1 the user equalizer, 2 speech (the app's podcast mode, which locks the equalizer) and 3 the personalised sound. The personalised sound has two generations (`sound-personalization-1` and `-2`):
the first holds its state in `PersonalizedSound_Mode` (`2001`/`2000`; 0 not set up, 1 disabled, 2 standby, 3 enabled, 4 calibrating) and is switched on with mode 3 and off with the audio mode user equalizer; the second
is the audio mode 3 itself (mode: 0 not set up, 1 calibrating, 2 calibrated, 3 activation inhibited). PairFree picks the generation from the firmware against each file's version, shows the switch only once a personalisation
exists, and offers `PersonalizedSound_Reset` (`2009`) as an action the screen confirms. The personalisation itself (the listening test that works out the thresholds, ratios and gains) is not built (section 6);
checked: it runs in a native library of the app, `libmusic_fit_native-lib.so` (67 KB, Fraunhofer IDMT's MusicFit and SurfaceFit: its exports are the fitting presets `preset_N_M`, `adaptPresetsToResults`,
`fit_sequenced`, `compute_fader_offset`), which was listed but not disassembled.

### 4.6 Fit test

`FitTest_CheckPrecondition` (`1E01`) then `FitTest_Check` (`1E00`), each answered by a notification with the left and right earbud (feature 15 registered): the precondition is 1 for a ready earbud; the result is 0 to 29 bad, 30 to 59
acceptable, 60 to 100 good, 254 invalid, 255 none. The app plays its own 10 second music clip (`fit_test.ogg`, 44.1 kHz stereo, bass-heavy, loudest in the middle) in a loop above a target volume while the earbuds measure; PairFree cannot include the file, so the screen plays a
steady 120 Hz tone in both ears (owner, 2026-11-15: a tone, as other brands do) and asks for a medium phone volume. Whether the earbuds accept a tone as well as the clip is not known: a result of 254 would say so.
Accentum True Wireless, Momentum Sport, Momentum True Wireless 3 and 4.

### 4.7 Paired devices (multipoint)

`DeviceList_GetPairedDeviceListSize` (`1400`), `GetOwnDeviceIndex` (`1407`), `GetDeviceInfo` (`1401`, index; the answer is index, priority, status and name), `Connect` (`1402`), `Disconnect` (`1403`) and `GetConnectionStatus` (`1404`;
its notification `1484`). The status is 0 disconnected, 1 classic, 2 LE, 3 both, 128 a flag, 254 and 255 no device. The app reads from index 0 up to eight entries. PairFree offers connecting and disconnecting the devices that are
not the phone itself, so the connection it holds is never cut. Deleting an entry (`1405`) is offered behind a confirmation, never the phone's own; the whole list (`1406`) is not offered; checked: it removes every pairing from the earbuds' list, and no screen of the app needs it.

### 4.8 The Momentum Sport's sensors

`Sensor_SetSensorEnable` (`2201`, the sensor and 1 or 0), `Sensor_GetSensorEnable` (`2202`) and `Sensor_SensorData` (`2200`; the notification `2280`): sensor 0 is the heart rate (the second byte of the data, in beats per minute),
sensor 1 the body temperature (the data's bytes 2 and 3 as a number, times ten to the signed byte 4, celsius when the flags say so; `parseBodyTemperatureSensorData`). PairFree switches both on only when asked, reads them with
the battery and switches them off again.

### 4.9 GAIA v2 headphones

The product files of CX 350BT, HD 250BT, HD 350BT, HD 450BT, HD 450SE, HD 458BT and IE 80S BT (`data/sennheiser/gaia2.json`): battery (`0182`) and charging (`0185`), the audio mode (`0786`/`0706`: user equalizer 1, speech 2: the podcast
mode), sidetone (`0787`/`0707`: off, min, max), the three-band equalizer (the parameter group `EQ_UserParameterGroup_Set`, Qualcomm's vendor `000A` command `021B`, and the two knobs `0685`/`0605`, as the
newer products' three knobs of section 4.1, with the gain limit 4), and on the IE 80S BT the answer-call and smart-pause switches, the voice prompt mode (`0781`/`0701`), language (`000A:0292`/`0212`), codec (`0184`), the automatic power off
timer (`000A:0189`/`0109`) and the multi-function button (`0186`/`0106`: voice assistant or the equalizer modes). Everything is read when the channel opens and the battery again on a timer. The HD 458BT has the same three-band equalizer as the others (its class does not use the five-band one). The IE 80S BT has the app's
five-band equalizer (`Eq5BandIE80`, bands labelled 62.5, 250, 1000, 4000 and 16000 Hz with filters at 32, 125, 500, 2000 and 8000 Hz, plus or minus 6 dB): the first curve of a session sets each band's filter type (5 for the two
lowest, 11 for the others), Q (2896) and frequency (`Sound_SetCustomEQParam` `000A:021A`, parameter `0x1n0`, `0x1n3`, `0x1n1`), turns `Sound_EQControl` (`0214`) on if it is not, writes the pre-gain and each band's step above the one
below in sixtieths of a dB as one group (`Sound_SetEQParamGroup` `021B`: the count, twelve numbers, a zero) and stores it (`0494:0606`); the bands are read back one by one (`029A`, parameter `0x1n2`; the app turns a 16 bit negative
into a number with 65535 and PairFree does the same). Two differences from the app: its fade (gains move in steps of at most 2 dB every 300 ms) is left out, so a large change goes at once, and the pre-gain uses the firmware 0.9.0 value
(-6; the older firmware's is -2), because the product file lists no firmware command for this product. The earbuds' events and the HD 450SE's voice assistant (`Voice_Assistant_Mode`, `0583`/`0503`, Alexa) are not built; checked:
the events (`000A:4003`, answered `C003`) are not registered by any command of the product files, so PairFree re-reads instead, and the HD 450SE's switch needs the Alexa app's set-up state (`Voice_Assistant_Config_Status`, `018A`),
whose answer layout the product file gives only as two bytes.

### 4.10 Nautilus headphones

The Momentum 3, PXC 550 and PXC 550-II (`data/sennheiser/nautilus.json`). A packet is `EB FE 01 00 01 00 00 00`, the length of the header and payload (two bytes, little-endian), a CRC-8 over them (polynomial `07`, initial
`FF`, most significant bit first, two bytes little-endian) and the header: module, optional submodule and command, each two bytes little-endian, then the payload; a string is UTF-16. On the serial port
(`1ddce62a-ecb1-4455-8153-0743c87aec9f`) each packet goes between two `2A` and the bytes `2A` and `3F` are escaped as `3F 2F` and `3F 3F`. The answer carries the header of the answer's own definition. `NautilusTest` pins the packets
against a port of the app's `accumulateCrc8` run outside the repo. Shown: the battery (the Momentum 3 answers a percentage, `12:03`; the PXC 550s a voltage and a level of five steps, `12:01`), the Momentum 3's noise cancelling
mode (`ANC_ActiveMode` `17:08`/`17:09`: off 0, airplane 1, outdoor 3, home 4; only while the control type `17:10` says the switch is on), transparent hearing (`ANC_ToggleAmbientMode` `17:11`) and whether it pauses music
(`17:0A`/`17:0B`), the PXC 550s' noise cancelling state (`17:03`/`17:04`: off, on, adaptive), the PXC 550-II's middle position of the switch (`17:12`/`17:13`), smart pause (`Input_Proximity_Mode`, module `08` submodule `EF` command `01`:
a write of the mode, and a read is the same command with 2), the voice prompts (`07:01`/`07:05`), the prompt language (read), the codec (`0A:10`), call enhancement (`0A:03`/`0A:04`), the sound presets (`0A:07`/`0A:08`) and the
version. The Momentum 3's equalizer is `Eq3BandM3`: the gains are smoothed (gain limit 4), then written as three parameter groups (`0A:0D`: index, no commit, type, five 32-bit numbers): the high shelf at 1600 Hz, the low shelf at 280 Hz
and the master gain, each biquad coefficient times 2^30 (halved while above 1.9, the master doubling for each halving); with the high-end sound tuning on, two peak filters (50 Hz -4 dB, 500 Hz +3 dB) replace the shelves; then the knobs
(`0A:0E`/`0A:0F`: index 0 the first two gains, index 1 the third gain and the flags, the tuning being the bit `0x800`). `SennheiserEqTest` pins the groups against a port of the app's JavaScript. The PXC 550s have the adaptive strength (`17:05`/`17:06`,
0 to 8, used while the state is adaptive) and, under the director preset (4), boost, virtual speaker distance, reverb and dynamic loudness (`0A/FF:07` to `0A/FF:0A`, index 4 and the position in the app's list). The voice prompt language is
settable (`07:02`) and the voice assistant switch is Alexa or the standard one (`06:41`/`06:42`).

### 4.11 Auracast

The earbuds scan for broadcasts themselves (`Auracast_StartSourceScan` `2A23`, five seconds as the app asks; `StopSourceScan` `2A24`; the results are the notification `2AA0`; the status `2A25`) and are an assistant for the
broadcast (BASS): `Auracast_AddSource` (`2A02`: the address type and address, the advertising set, the broadcast id, synchronise without past info, an unknown interval, one subgroup taking every stream and no metadata, as
`BassAddSourceOperation` builds it), `RemoveSource` (`2A05`), `GetSourceIds` (`2A22`) and `GetReceiveState` (`2A21`, with the notification `2AA1`). Feature 21 is registered. Accentum True Wireless and Momentum True Wireless 4 from firmware
5.13.1. A source that asks for a code gets a text line (`SetBroadcastCode` `2A04`: the source id and sixteen bytes). `SennheiserMoreTest` builds each message from the app's own layout.

## 5. Never sent

`SennheiserSession.allowed` is a closed list: only the reads and writes of the tables' properties and the notification registration go out. Everything else stays unsent, in particular the factory reset
(`0495:0040`), firmware update (the `Upgrade_*` / VMUP commands and `Upgrade_EnterDfuMode`), the panic log erase, the raw device storage, the statistics, the serial number and the pairing list's deletions.
`Reboot` and `PersonalizedSound_Reset` go only from an action the user confirms on the screen. The GAIA v2 and Nautilus sessions send only the commands of their products' tables, each checked as the GAIA v3 session's are.

## 6. What remains

Each says what was read.

- **The Accentum Clip (Airoha RACE)**: not built: checked: Smart Control Plus sends it, and only it, to its Airoha platform; its sixteen RACE feature variants exist as classes (`AutoPowerOffRaceFeatureVariant`,
  `Equalizer2RaceFeatureVariant`, `TouchControlsRaceFeatureVariant`...) whose constructors hold only the feature name and an id (the auto power off is `0x12`). The Plus app holds the command numbers of Sennheiser's own commands
  for these products as enums: battery level `011A`, battery type `0120`, battery protection `0130`/`0131`, eco mode `0132`/`0133`, in-case state `011F`, codec `0117`, voice prompt `0136`/`0137`, auto answer `013B`/`013C`, bass boost
  `0141`/`0140`, touch lock `0119`/`0118`, gesture config `012C`/`012B`, default gestures `012F`/`012E`, available functions `012D`, play sound `0122`/`0123`/`0124`, paired devices `0110` to `0116` and `010D`, serial `0106`, hardware
  `0108`, firmware `011B`, reboot both earbuds `0152`, the Airoha agent query `0CD4`, factory reset `9500` (never sent). These are Airoha RACE command numbers, not GAIA words: as GAIA words their type bits would make `011A` a reply,
  and `0CD4` is RACE's own range, so they do not belong to the Momentum 5 or Momentum True Wireless 5, which stay on GAIA v3 (their feature list in issue #47 is GAIA 3's). What was read of their payloads: a response parser exists per
  command group (touch `D24EC8`, `D250F4`, `D25320`, `12C2904`, `12C2D1C`; peers `D231B8` to `D237FC`; firmware, hardware and serial `D22840`, `D22988`, `D22DCC`; battery, eco and protection `D25408` to `D259FC`), and the battery
  protection and eco mode parsers take a response of at least two bytes (the first is the command's echo or status, the second the value, 0 off and 1 on) and fail with "Unexpected payload length" otherwise; the request builders
  wrap the command in a generic object with a 0xFF response mask, and the RACE frame itself is built in a shared layer. Read further on 2026-11-16: every response goes through one check (`D22BF4`: the payload must be longer than the given index, its byte there must be 0, 1, `FE` or `FF`, and `FE` or any other non-zero value after that raises "Received error when trying to execute <command>"), then the group's parser takes the first byte, or the first and the second (battery level and the two-byte ones), as the value; whether that first byte is a RACE status or the value itself is not settled by the code, because the same byte is both checked and returned. Static reading of the remaining layers is ambiguous enough that the evidence to settle it is the app's real traffic, which the owner's virtual-device method (`docs/VIRTUAL_TESTING.md`) can capture for the requests; the replies it would need are invented there, so a recording from a real Accentum Clip is what pins the response bytes. The next step is to finish tracing those functions (the Dart code is obfuscated, Blutter left their bodies
  empty, and `E:\Builds
esearch\sennheiser	ools	race.py` and `uses.py` summarise a function's strings, constants and calls) until each command's request and response bytes are known; building from the numbers alone would invent layouts.
  Virtual attempt, 2026-11-16 (emulator, Plus app 1.6.3, a Bumble peer; virtual evidence only): the app lists a peer as the Clip when it advertises the service UUID `FDCE` (the app's scan filters are service UUIDs `FDCE` and `FCFE`)
  and manufacturer data of company `0BA3` whose type-3 block starts with product id `12` and colour `18` (cream); it then shows the Clip's picture. Tapping it gives "Something went wrong" at once, with no GATT client
  registered, no connection attempt, no bonding and no serial connection in the Bluetooth logs, with the configured byte 0 or 1; so the app refuses before any Bluetooth traffic and some advertisement field or an earlier
  check is still unmet (the app writes nothing to logcat). No request bytes were captured.
- **Personalised sound's listening test**: blocked: it plays the app's own music stems (`bass.ogg`, `brk.ogg`, `full.ogg`, `rhythm.ogg`, `treble.ogg` in `res/raw`, 0.5 to 2 MB each) that the library's fitting presets and
  fader maths are made for, and vendor audio cannot go in the repo (Hard rules); checked: section 4.5, the stems' names and sizes, and the library's exports.
- **Auracast broadcast codes**: built (a text line for the source that asks for one, `Auracast_SetBroadcastCode` `2A04`, up to 16 characters padded with zero bytes).
- **The equalizer presets stored on the earbuds** (`selectablePreset`, `userPresets` of `UserEQ_GetConfiguration`) and the Plus app's equalizer for its own products: checked: the first are counted in the
  configuration reply but no command in the product files names or applies them; the Plus-only products have no product file.
- **The voice assistant** (`VoiceUI_Selected_Assistant`) and **renaming** (`renameDeviceMinFwVersion`): checked: every product file sets `voiceAssistant_MinFwVersion` and `renameDeviceMinFwVersion` to 99.99.99, so the app
  itself never offers them.
- **The soundbars (Elmar, Wi-Fi)**: held back with the other speakers (owner, 2026-10-30); checked: the AMBEO Soundbar's product file (`sb01.json`, 77 properties, one-byte commands with a `0xFF` separator over `63331377/78`).

## 7. Open questions

- Which GAIA v3 properties the Momentum 5 and Momentum True Wireless 5 have: checked: neither has a product file in either app; PairFree asks for all of them and shows what answers, so a device report
  settles it.
- Whether the three-way noise control matches the Momentum 4's and Accentum Plus's behaviour: checked: the Momentum 4's class writes only `ANC_Transparency` (the slider), `ANC` and `TransparentHearingMode` in its noise
  screen and never `ANC_Status` or `TransparentHearing_Status`, which its product file lists; PairFree's three-way control writes those two, and the slider of section 4.2 is the app's own control. A report from a
  Momentum 4 will say which the headphones follow.
- The fit test's music (section 4.6), the Nautilus and GAIA v2 headphones' replies, and the whole of this document: no product was available; each is a reading of the apps.
