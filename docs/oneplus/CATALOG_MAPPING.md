# HeyMelody catalog: what each field means, and what PairFree does with it

Source analysis of HeyMelody 117.6.6 (2026-11-10): the 85 catalog records' `function` fields (counts below are records, of 85), the app's
tables that read them (`Ha.a` for control and noise names, `p072eb.w` for equalizer names, `N.e` for gestures, `PollCommandManager` for the
feature-switch ids) and the app's English strings. `data/oneplus/devices.json` keeps what PairFree uses of them; the importer is
`tools/import_heymelody_catalog.py`. Nothing here was tried on earbuds.

## Mapped and used by PairFree

| Field (records) | Meaning | Where it goes |
|---|---|---|
| `noiseReductionMode` (43) | The noise modes by protocol index, each with a type and sub-modes. Types: 1 off, 2 transparency, 3 light, 4 strong, 5 noise cancelling, 6 voice focus (transparency), 7 smart, 8 moderate, 10 auto, 11 less noise, 12 standard, 13 more noise. The other 42 records are earbuds with no noise control (Air, Clip, Free, W and M series). | `noise` (Home tab, and a level row). Written as `0404 01 01 <bitmap>` with the bit of the mode's or sub-mode's index |
| `equalizerMode` (73) | The preset list: protocol index and a type that names it. 12 records have none (Free, Air, Air Lite, W51, W31, Play, Buds, M31, Buds Z, Buds). `equalizerModeByVersion` (5) and `equalizerModeCompat` (8) hold other lists for other firmware: not used yet | `eq.presets` (Sound tab); written as `0406 <index>` |
| `customEqualizer` (39), `customEqFrequency` (10), `customEqUiVersion`, `customEqMax` | A custom curve exists (`0122` read, `0418` write); frequencies where listed | `eq.custom`, `eq.frequencies`, `eq.slots`; built: read `0122` (and the unasked `0506`), write `0418` with operation 1 (add) or 2 (save and use); delete (3) and rename are not offered |
| `gameMode` (67) | Feature switch id 6 (`0403 06 01`); records with only `gameModeList` (2) count too | `features.gameMode`; built (Device tab) |
| `batteryInfo` (82) | The app shows a battery card; PairFree reads `0106` on every model and shows what the earbuds answer | always read |
| `control` (83), `callControl` (50), `controlList` (3) | Touch assignments: one entry per gesture with the functions its `support` mask allows | `touch` (see below); built for the stem taps, hold, slide, long press and the call controls (the last three from the app's layouts only); `controlList` (3) is not |

## Touch controls (`touch` in the data)

A key assignment is four bytes: device type (1 left, 2 right; 4 for call actions), button (1 for the stem, 6 for call actions, 2 and 3 for the
volume buttons of older models), button action and function; `0401` writes a count and these records, `0108` reads them
(capture: `0108` with `02 03 01`). The catalog's `action` becomes the button action byte (`N.e`): 16, 17, 18 and the older 1, 2, 3 are single,
double and triple press (byte 1 to 3); 19, 20, 11 and 4 are a long press (byte 4); call actions 28 and 32 (single), 29, 33 and 36 (double), 30
and 34 (triple) and 31 and 35 (byte 6). Bytes 5 and 6 (older models' `action` 5 and 6) have no name in the app's code; they are named here from the earbuds' manuals and reviews (web search, 2026-11-10), which is evidence about the products, not about the wire: byte 5 is the slide gesture (its functions are none, adjust volume and switch song; the Enco X's, X2's and Buds 3 / Pro 3's stems are slid up or down for volume: [OPPO Enco X2 page](https://www.oppo.com/nz/accessories/oppo-enco-x2/), [GSMArena on the Buds 3](https://www.gsmarena.com/oneplus_buds_3_review-news-61480.php)), and byte 6 is a long press (its functions are none and switch device on the Buds Z2, Enco X, Nord Buds and Buds N, where a 3 second hold reconnects to the previous device: [Buds Z2 guide](https://manuals.plus/m/c4c529950e96905eb9832a1c4940532679ffdc2a4173a427e63216fc7d81ae75); volume up and down on the Enco Air series). The names are inferences: PairFree builds none of these rows. Bytes 7, 8, 12 to 15, 25 and 37 (10
records) have no name anywhere found: kept as numbers. A `support` mask lists the allowed functions as bits; PairFree's
importer turns each into a key and the number the app writes (`Ha.a.b`): none 0, play/pause 1, voice assistant 3, previous 5 (4 on the OnePlus
Buds and Buds Z), next 6 (5 there), adjust volume 7, noise control switch 8, play collection 9, switch song 10, volume up 11, volume down 12,
switch device 13, game mode 17, zen mode 18, collect music 22, AI summary 25, AI translation 26, spy tap 32, Gemini play/pause 34, trigger 35,
reject call 28, answer or hang up 29. Masks 2, 16384 (enter pairing) and 4194304 (clear pairing history) have no write number in the app's
table, and no record uses them. The 48 controls with no mask are the long-press noise-mode cycle and similar, which the app sets through its
own screens (not read yet).

## Feature switches (`010D`) and the catalog flags that ask for them

`PollCommandManager.m` builds the list of feature ids it reads from the flags: 5 and 4 always; 11 hearing enhancement; 12 personalized noise; 13
click to take a picture; 15 zen mode; 17 multi-device connect; 9 vocal enhance; 19 headset sound record; 24 high tone quality; 23 long power
mode; 21 smart call; 22 device lost reminder; 20 voice wake; 25 voice command; 6 game mode; 27 spatial audio types; 29 bass engine; 28 control
auto volume; 30 collect logs (never offered); 33 game EQ packages; 34, 35, 36 and 60 spine health; 39 and 40 game sound; 48 adaptive volume; 49
adaptive ear; 50 speech perception; 52 meeting assistant; 53 long-press volume; 55 swift pair; 56 hearing optimize; 57 incoming call control; 58
sleep detection; 59 head motion; 61 Gemini voice assist. The reply is a count then (id, state) pairs. Checked against the virtual Buds Pro 2:
its read asked for exactly ids 5, 4, 11, 12, 15, 17, 24, 6, 29, 28, which are the flags that record has. PairFree reads and writes id 6 only.
Every plain switch is written the same way, `0403` with the feature id and 0 or 1 (HeyMelody's `setSwitchFeature`, repository method `H0(address, id, on)`; an earlier note here counted two callers because it looked for another method's name: `H0` has about 40 call sites, one per feature screen). PairFree reads the ids the model's catalog flags give (`SWITCH_FLAGS` in the importer) and shows a switch only for an id the earbuds answered. Built: 4 in-ear detection (`wearDetection`), 9 vocal enhancement, 11 hearing enhancement, 17 dual connection, 19 recording through the earbuds, 24 high-quality audio, 28 automatic volume, 29 bass engine, 50 speech perception, 53 volume by long press, 55 Swift Pair, 58 sleep detection, 59 head nod and shake control. Not built on purpose: 12 personalized noise (a calibration, the app only switches it off), 15 zen mode (own audio-file transfer), 13 click to take a photo and 61 Gemini (phone-side), 20, 25 and 57 (voice assistant set-up flows), 30 log collection (never offered), 31 LE audio (the phone's OPLUS Bluetooth stack writes it from a system broadcast, `LeAudioDeviceManager`; checked: it is not a user setting and the app has no screen for it), 33, 39 and 40 (game equalizer and game sound: not read), 52 meeting assistant.

## Mapped as a fact but not used yet

| Field (records) | What it is | Status |
|---|---|---|
| `multiDevicesConnect` (50), `multiConnectFunctions` (13) | Dual connection: the switch is feature id 17; the other devices are read with `0112` (`status, count`, per device the address, a length, the state, a flag byte, the name) and connected (`0429 01 address`) or disconnected (`0429 02 address`); the address is written in the order HeyMelody's text shows it, the reverse of the read. State 1 is connecting, 2 connected, 4 disconnecting; flag bit 0 is this phone. | built (Device tab: the switch, a choice to connect and one to disconnect; this phone is never offered) |
| `wearDetection` (38) | In-ear detection: feature id 4 | built as a switch |
| `spatialTypes` (47), `spatialDescriptionType`, `spatialHiResMutex` | Spatial audio with the allowed types: 0 off, 1 fixed, 2 head tracking (read `012A`, write `0422`) | built (Sound tab) |
| `hearingEnhancement` (34), `earScan` (32), `hearingJudgeDb` (8) | Hearing enhancement: feature id 11 (a switch); the ear scan and its profile are phone-side tests | switch built; the scan not built |
| `fitDetection` (31) | Ear tip fit test: `0405 01` starts it (`t1`); the result is a notification event, command `0204` with `4, type, status, type, status` (type 1 left, 2 right; status 1 good, anything else poor). The earbuds send events once the phone registers for them: `0200` lists the events, `0201` registers one or `0205` several (HeyMelody picks by the model's command support; PairFree sends both for event 4). Whether `0201` or `0205` is the one a model takes is unconfirmed. | built (the shared fit test screen) |
| `highToneQuality` (28), `highAudio` (3) | High-quality audio: feature id 24 | built as a switch |
| `bassEngineSupport` (24) | Bass engine: feature id 29 (its level is written with `041B`, three bytes, which were not read) | switch built; the level not built |
| `promptVolume` (26), `promptVolumeRange`, `promptVolumePercent` | Voice prompt volume: read `0130` (`status, value`), write `0427` with the value; the bar runs from the catalog's minimum (else 1) to its maximum (else 10). The read was found in `PollCommandManager.M`, which the decompiler skipped (re-run with `-m fallback`) | built (Device tab) |
| `controlAutoVolumeSupport` (16) | Automatic volume: feature id 28 | built as a switch |
| `personalNoise` (9), `personalNoiseCompat` (5) | Personalized noise cancelling (`011A` read, `0412` write, id 12) | not built |
| `zenMode` (6), `whiteNoise` (12) | Zen mode and its sounds (id 15) | not built |
| `strongNoiseReductionRealTime` (16), `noiseReductionUIVersion` (6) | Changes the noise level descriptions and screen, not the wire | no effect needed |
| `headMotion` (2), `sleepDetection` (2), `speechPerception` (2), `vocalEnhance` (1) | Head motion (id 59, and `0431` with 0 for nod answers or 1 for shake answers, read `0134`), sleep detection (58), speech perception (50), vocal enhancement (9) | built |
| `spineHealth` (2) | Spine health: ids 34, 35, 36 and 60 with the phone's health app | not built (phone-side) |
| `tapLevelSetting` (1) | Tap sensitivity, 1 to 5: read `0133` (`status, value, default`), write `042D` | built (Controls tab) |
| `longPressVolume` (1), `swiftPair` (12), `headSetSoundRecord` (5) | Feature ids 53, 55 and 19 | built as switches |
| `heyFindDevice` (61), `findDevice` (10) | Find my earbuds (`0400` with 1 or 0, audible: never part of a probe) | built as a button on the Device tab |

## Not for PairFree

App-side or server-side fields that need no earbud command: guides and tutorials (`controlGuideSupport` 66, `functionIntroductionGuide`,
`introductionGuideList`, `tutorialGuide`, `newFunctionGuide`, `cleanGuide`, `wearingVideoTutorial`), appearance (`customDress`,
`carouselDress`, `honorWall`), pairing and discovery (`fastDiscovery`, `swiftPair`, `openBoxPairing`, `holdInBoxPairing`, `inBoxStatus`,
`pairingModeTip`, `pairingInBoxCount`), firmware (`autoFirmwareUpdate` 76, `firmwareTrack`, `firmwareFileCheckFlags`, `firmwareCheckCommandId`,
`firmwareDiscovery`, `minOtaBattery`, `upgradeMtu`: never sent), logs and feedback (`collectLogs`, `feedback`, `related`), and display
(`hideFromUIList`, `dolbyMark`, `dialogTags`, game package lists). `minVersion` and the per-model `podsVersion`, `opsPodsVersion` and
`heyMelodyVersion` gate the app's own versions.

## Still unresolved

- The button action bytes 7, 8, 12 to 15, 25 and 37 (and, as an inference only, 5 and 6); mask 2.
- Which firmware version selects `equalizerModeByVersion` and `controlList`, and how the app reads a firmware version (`0105`).
- The device types a `0108` read asks for (1, 2 and 3; the app adds 2 on some phones, `T.u`): what type 3 returns.
- How the long-press noise cycle is stored and written.
- The model-specific rounding of battery values (`batteryRadix`), which the virtual run showed for the right earbud.
- Feature switches: see the section above; all of them are only exercised against invented replies (virtual and unit tests), none on earbuds.
- Per-earbud long-press cycles (the 10 models whose `longPressType` is set): the read for them is `010C 02 03` (left) and `02 04` (right), written the same way;
  PairFree does not read them.
- Call controls, key reads and slide or long-press rows are built from the app's layouts but only exercised against invented replies: the key read
  PairFree sends asks for buttons 3 and 1, so whether the earbuds list device type 4 or button 6 records in it is not established.
- The bass engine's level, the ear scan and per-ear long-press cycles are not built; LE audio and the Gemini, zen and game-sound features are not user settings (see the switch list above, with what was checked).
