# Sennheiser

Sennheiser's control protocols, read from the company's two apps (decompiled outside the repo on 2026-11-15) and built for the products on **GAIA v3**. No Sennheiser product has
been tried with PairFree.

## 1. Sources

- **Sennheiser Smart Control 4.9.4** (`com.sennheiser.control`, versionCode 175297). A NativeScript app whose protocol is plain JavaScript and JSON: every product has a product file
  (`assets/app/<model>.json`) listing its protocol, its shared feature files (`assets/app/features/`) and its *properties*: each one a thing the app can read, write or hear, with the commands
  that do it, their ids and typed parameters. `data/sennheiser/commands.json` and `devices.json` keep the facts PairFree builds from them (`tools/import_sennheiser_app.py`); the app's files are
  not in the repo.
- **Sennheiser Smart Control Plus 1.6.3** (`com.sonova.chb.control`). A Flutter app (Dart 3.10.8 AOT, obfuscated; its arm64 build was read with Blutter and a small annotating disassembler). It
  names the newer products and which platform each is on (section 2) and uses the same GAIA v3 implementation (its frame handler accepts exactly the vendors `0x001D` and `0x0495`, versions 3 and
  4, no checksum).
- The full research notes, the catalogue of every command of the 24 products in Smart Control and the Dart enums are outside the repo (`E:\Builds\research\sennheiser`).

## 2. Products and platforms

The two apps use four control protocols between them:

| Protocol | Products | PairFree |
|----------|----------|----------|
| **GAIA v3** (Qualcomm's framework; vendors `0x001D` Qualcomm's and `0x0495` Sennheiser's own) | Accentum, Accentum Plus, Accentum True Wireless, CX 200, CX 200 Sports, CX Plus True Wireless (CX400II), CX 500, Momentum 4, Momentum Sport, Momentum True Wireless 3 and 4 (Smart Control); Momentum 5, Momentum True Wireless 5, HDB 630, HDR 275 / 255 (Smart Control Plus) | built (sections 3 to 6) |
| GAIA v2 (vendors `0x0494` and Qualcomm's `0x000A`) | CX 350, CX 400, HD 250, HD 350, HD 450, HD 450SE, HD 458, IE 80, Momentum True Wireless 1 and 2 | blocked: nobody to try it on; checked: Smart Control's product files (`cx350.json`, `tw2.json`...) define its commands completely (the same property catalogue, other ids), so it is a table and a framing version away |
| Nautilus (Sennheiser's own frame, CRC-8) | Momentum 3, PXC 550, PXC 550-II | blocked: nobody to try it on; checked: `m3.json`, `pxc550.json`, `pxc550II.json` define its commands; the frame is in `NautilusCommand` (`EB FE 01 00 01 00 00 00`, length and CRC-8 poly `0x07` init `0xFF`, little-endian module and command) |
| Airoha RACE | Accentum Clip (AOWS1) | blocked: nobody to try it on; checked: Smart Control Plus's Dart code (`aows1` is the only product its platform table sends to Airoha) and PairFree's own RACE code from the JBL work |
| Soundbars (Elmar over BLE; Wi-Fi) and the HDW 960 | AMBEO Soundbar | speakers are held back (owner, 2026-10-30) |

How the platform is known: Smart Control Plus identifies a product from Sonova's advertisement data and a jump table in its Dart code sends every product to "herakles" (Qualcomm GAIA 3) except
the Accentum Clip and an unknown Airoha product ("airoha") and the HDW 960 and the soundbars ("sue"). So the **Momentum 5 and Momentum True Wireless 5 are GAIA v3**, as the Momentum 5 report
in issue #47 suggests (`00 04 06 07 0c 0d` is a GAIA 3 feature list). Smart Control sends the Momentum 5/6, True Wireless 5/6, HDB 630, HDR 275, BTA1, AOWS1 and others to the Plus app.

## 3. Transport and framing

- **Classic Bluetooth:** RFCOMM service `a2129ff3-081b-4c45-8afe-469d9c4842ec` (Sennheiser's own: it is what PairFree identifies a Sennheiser device by besides its name). Smart Control prefers it on
  Android for the Accentum, Momentum 4, Momentum Sport and Momentum True Wireless 3 and 4; the CX 200, CX Plus and CX 500 are BLE only (service `FCFE` Sonova, `FDCE` Sennheiser; characteristics `6333133B`
  write, `6333133C` read). PairFree opens RFCOMM only.
- **Frame:** `FF`, version `03`, flags (bit 0: an XOR byte follows; bit 1: two length bytes), the payload's length, the vendor (u16), the command word (u16), the payload; big-endian throughout.
  PairFree sends no checksum and one length byte (what `SPPGaiaFramer` writes), and accepts both from the earbuds.
- **Command word:** feature << 9 | type << 7 | command. Type 0 is a command, 1 a notification, 2 a reply (the command + `0100`), 3 an error (+ `0180`). The Sennheiser features are registered with `0495:0007`
  and a feature id: core 0, device 2, battery 3, generic audio 4, user EQ 8, versions 9, device management 10, MMI 11, transparent hearing 12, ANC 13.
- A reply and a notification of the same property carry the same bytes (the product file's `Event` is its `Response`). One command goes out at a time, each answered by its reply or an error; Smart Control
  waits 3 s and tries again.

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
run outside the repo, and the frames each generation sends. checked: the app's `EqKnobValuesCoder` was read for its bit layout; the Plus-only products (Momentum 5, Momentum True Wireless 5, HDB 630, HDR 275)
have no product file in either app, so PairFree asks them nothing about the equalizer.

## 5. Never sent

`SennheiserSession.allowed` is a closed list: only the reads and writes of section 4's properties and the notification registration go out. Everything else stays unsent, in particular the factory reset
(`0495:0040`), firmware update (the `Upgrade_*` / VMUP commands), the panic log erase and the raw device storage, and `SetMMIConfig`.

## 6. To build next

Each says what was read.

- The equalizer's user presets that live on the earbuds (`selectablePreset`, `userPresets` of `UserEQ_GetConfiguration`) and the Plus app's equalizer for its own products: checked: the first are counted in the
  configuration reply but no command in the product files names or applies them; the Plus-only products have no product file.
- Touch and button configuration (`GetMMIConfig`, `SetMMIConfig`, `SetDefaultMMIConfig`, `ApplyMMIConfig`): checked: the commands are in the product files and the action list in Smart Control Plus has 55
  values (`kEa` in the Dart dump, each with its number).
- Transparent hearing's level (`TransparentHearing` / `ANC_Transparency`, 0 to 100 on the Momentum 4 and Momentum Sport): checked: the commands (`1803`/`1802`, `1A03`/`1A02`) and the slider's range (`min: 0, max: 100`) are in the product files; not built until a product's behaviour is known.
- Sidetone (`SidetoneState`), find device (`features/find-device-1.json`), personalised sound (`features/sound-personalization-*.json`), audio mode (`features/audio-mode-*.json`), the fit test, multipoint
  (`PairedDevices*`), Auracast (`features/auracast-1.json`), voice assistant selection, voice prompt language and the Momentum Sport's sensors: checked: all are in the product files with their ids and types;
  none is built.

## 7. Open questions

- Which GAIA v3 properties the Momentum 5 and Momentum True Wireless 5 have: checked: neither has a product file in either app; PairFree asks for all of them and shows what answers, so a device report
  settles it.
- Whether the 3-way noise control matches the product's behaviour on the models with a combined slider (Momentum 4: `ANC_Transparency`): checked: the Momentum 4's product code uses `ANC` and the slider,
  not `ANC_Status`, in its noise screen; PairFree's three-way control writes `ANC_Status` and `TransparentHearing_Status`, which the Momentum 4's file also lists. A report from a Momentum 4 will say.
