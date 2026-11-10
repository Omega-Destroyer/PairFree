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
| `customEqualizer` (39), `customEqFrequency` (10), `customEqUiVersion`, `customEqMax` | A custom curve exists (`0122` read, `0418` write); frequencies where listed | `eq.custom`, `eq.frequencies`; not built |
| `gameMode` (67) | Feature switch id 6 (`0403 06 01`); records with only `gameModeList` (2) count too | `features.gameMode`; built (Device tab) |
| `batteryInfo` (82) | The app shows a battery card; PairFree reads `0106` on every model and shows what the earbuds answer | always read |
| `control` (83), `callControl` (50), `controlList` (3) | Touch assignments: one entry per gesture with the functions its `support` mask allows | `touch` (see below); the stem taps (single, double, triple) are built, the long press, the call controls and `controlList` are not |

## Touch controls (`touch` in the data)

A key assignment is four bytes: device type (1 left, 2 right; 4 for call actions), button (1 for the stem, 6 for call actions, 2 and 3 for the
volume buttons of older models), button action and function; `0401` writes a count and these records, `0108` reads them
(capture: `0108` with `02 03 01`). The catalog's `action` becomes the button action byte (`N.e`): 16, 17, 18 and the older 1, 2, 3 are single,
double and triple press (byte 1 to 3); 19, 20, 11 and 4 are a long press (byte 4); call actions 28 and 32 (single), 29, 33 and 36 (double), 30
and 34 (triple) and 31 and 35 (byte 6). Bytes 5 and 6 (older models' `action` 5 and 6, 22 records and 68) and 7, 8, 12 to 15, 25 and 37 (10
records) have no name in the app's code that was found: kept as numbers. A `support` mask lists the allowed functions as bits; PairFree's
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
What each other switch does when set (a plain 0 or 1, or a level) was not read: not built.

## Mapped as a fact but not used yet

| Field (records) | What it is | Status |
|---|---|---|
| `multiDevicesConnect` (50), `multiConnectFunctions` (13) | Dual connection (feature id 17), version 1 or 2 | not built |
| `wearDetection` (38) | In-ear detection (own read `0109`) | not built |
| `spatialTypes` (47), `spatialDescriptionType`, `spatialHiResMutex` | Spatial audio with the allowed types (feature id 27; write `0422`) | not built |
| `hearingEnhancement` (34), `earScan` (32), `hearingJudgeDb` (8) | Hearing enhancement and the ear scan (needs phone-side tests) | not built |
| `fitDetection` (31) | Ear tip fit test | not built |
| `highToneQuality` (28), `highAudio` (3) | Codec/quality switch (feature id 24) | not built |
| `bassEngineSupport` (24) | Bass engine (id 29) | not built |
| `promptVolume` (26), `promptVolumeRange`, `promptVolumePercent` | Voice prompt volume | not built |
| `controlAutoVolumeSupport` (16) | Automatic volume for the controls (id 28) | not built |
| `personalNoise` (9), `personalNoiseCompat` (5) | Personalized noise cancelling (`011A` read, `0412` write, id 12) | not built |
| `zenMode` (6), `whiteNoise` (12) | Zen mode and its sounds (id 15) | not built |
| `strongNoiseReductionRealTime` (16), `noiseReductionUIVersion` (6) | Changes the noise level descriptions and screen, not the wire | no effect needed |
| `headMotion` (2), `spineHealth` (2), `sleepDetection` (2), `speechPerception` (2), `vocalEnhance` (1) | Head motion (id 59), spine health, sleep and speech features | not built |
| `heyFindDevice` (61), `findDevice` (10) | Find my earbuds (`0400`, audible: never part of a probe) | not built |

## Not for PairFree

App-side or server-side fields that need no earbud command: guides and tutorials (`controlGuideSupport` 66, `functionIntroductionGuide`,
`introductionGuideList`, `tutorialGuide`, `newFunctionGuide`, `cleanGuide`, `wearingVideoTutorial`), appearance (`customDress`,
`carouselDress`, `honorWall`), pairing and discovery (`fastDiscovery`, `swiftPair`, `openBoxPairing`, `holdInBoxPairing`, `inBoxStatus`,
`pairingModeTip`, `pairingInBoxCount`), firmware (`autoFirmwareUpdate` 76, `firmwareTrack`, `firmwareFileCheckFlags`, `firmwareCheckCommandId`,
`firmwareDiscovery`, `minOtaBattery`, `upgradeMtu`: never sent), logs and feedback (`collectLogs`, `feedback`, `related`), and display
(`hideFromUIList`, `dolbyMark`, `dialogTags`, game package lists). `minVersion` and the per-model `podsVersion`, `opsPodsVersion` and
`heyMelodyVersion` gate the app's own versions.

## Still unresolved

- The button action bytes 5 and 6 and the ones listed above; mask 2.
- Which firmware version selects `equalizerModeByVersion` and `controlList`, and how the app reads a firmware version (`0105`).
- The device types a `0108` read asks for (1, 2 and 3; the app adds 2 on some phones, `T.u`): what type 3 returns.
- How the long-press noise cycle is stored and written.
- The model-specific rounding of battery values (`batteryRadix`), which the virtual run showed for the right earbud.
