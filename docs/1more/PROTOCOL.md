# 1MORE protocol

How 1MORE earbuds and headphones are controlled, as PairFree does it. No 1MORE model has been tried with
PairFree yet.

## 1. Sources

- **1MORE's app**: 1MORE 2.0.0 (`com.onemore.music`), decompiled outside the repo on 2026-10-18. None of
  its code is in the repo. It is a Tuya ("Thing") host app with React Native parts; the earbud control is
  1MORE's own Kotlin code. These parts were read:
  - `module/ui/cmd`: `Cmd.kt` (the frames and every command it sends), `Dispatcher.kt` (how it reads each
    answer) and `Const.kt` (the values).
  - `module/spp`: `BluetoothService` (the serial channel) and `BluetoothOrderConstents` (an older table of
    the same commands, with whole frames written out; `data/1more/test_vectors.json` holds them).
  - `HeadsetActivity`, `CustomSettingsMonauralActivity`, `EarReturnActivity`, `HearingProtectActivity`,
    `FindDeviceActivity` and `HeadsetConfigListApiData`: what each screen sends and which model sees it.
  - The chip makers' SDKs in the app (JieLi, Bluetrum, Actions) are for firmware updates only.
- **1MORE's product list**: `system/headphone/list` on 1MORE's server, which 1MORE's app downloads to
  decide what each model has (section 5). It was fetched once by hand on 2026-10-18 with the owner's OK
  (a plain GET, no sign-in, no device details); the raw reply stays in `server_dump/`, which git ignores,
  and `data/1more/devices.json` keeps only the facts the owner approved, which `tools/import_1more_list.py`
  takes from the saved reply. PairFree never contacts 1MORE.

No open-source project for 1MORE's earbuds was found.

## 2. Frames

```
head (2) | command (2) | length (2) | sequence (2) | xor (1) | payload
```

- **Head:** `11 01` from the phone, `01 00` from the earbuds.
- **Command, length, sequence:** two bytes each, high byte first. The length counts the payload. The
  sequence number counts up from 0 for every frame the phone sends and starts over after `FFFF`
  (`Cmd.kt`, `makeData`).
- **XOR:** the XOR of the eight bytes before it.
- **No handshake:** `004D` exists but 1MORE's app never sends it, and PairFree does not either.

1MORE's app splits what the earbuds send by a fixed length per command (`BluetoothOrderConstents.getFeedbackLength`)
and never checks the XOR or the length field. Its lengths match the length field for every answer it
reads (for example 19 bytes for `004E`, a 10-byte payload), so PairFree splits by the length field and
takes a frame whose XOR does not match only when its command is one it knows. 1MORE's app also expects
13-byte frames starting `FE`; what they are is not known, and PairFree skips them.

## 3. Connection

The control channel is the standard serial port (RFCOMM, `00001101-0000-1000-8000-00805F9B34FB`) of the
paired earbuds: 1MORE's app opens it with `isAndroid` false, which picks that UUID. Only one app can hold
it.

The earbuds answer a read with a frame of the same command, and a write with one byte on the write's
command: 1 success, 0 failure, 2 both earbuds needed. 1MORE's app reads every frame the same way, asked
for or not, and so does PairFree. What a model does with a read it lacks is not known, so PairFree sends
only the reads the product list says the model has (section 5).

1MORE's app sends without waiting for answers. PairFree sends one frame at a time and waits up to 1 s for
its answer, as it does for Nothing's earbuds, then reads each change again 3 s later. A refused write is
read again at once.

## 4. Commands

`data/1more/commands.json` lists them all. The ones PairFree uses:

| Read | Write | Setting | Value |
|------|-------|---------|-------|
| `004E` | — | Both earbuds | left is master (0); left version (3 bytes); left battery; right version (3 bytes); right battery; case battery |
| `005F` | `005E` | Listen mode | 0 off; cancelling 7 adaptive, 1 strong, 2 mild, 4 wind; transparency 3, 5 pass-through, 6 voice enhancement |
| `006C` | `006B` | Connection mode | 0 standard, 1 game ("entertainment"), 2 LDAC |
| `0077` | `0076` | Two devices | 0 off, 1 on |
| `0083` | `0082` | Spatial audio | 0 off, 1 on |
| `0063` | `0062` | Taking them out | 0 pause only, 1 pause and play again, 2 nothing |
| `0070` | `006F` | Sleep detection | 0 off, 1 on |
| `0081` | `0080` | Voice commands (wake word) | 0 off, 1 on |
| `0087` | `0084` | Touch, per earbud | read: double left, double right, triple left, triple right; write: the gesture (1 double left, 2 double right, 3 triple left, 4 triple right), then the action |
| `0065` / `0067` | `0064` / `0066` | Touch, both alike | double tap / triple tap |
| `0091` | `0090` | Hearing your own voice | 0 off, 1 on |
| `0093` | `0092` | Its depth | 1 low, 2 medium, 3 high |
| `0095` | `0094` | Volume limit | 0 off, 1 low, 2 medium, 3 high |
| `005B` | — | Finding the earbuds | sent with `01`; earbuds that can ring answer it |
| — | `005A` | Ring | left, right: 1 ring, 0 stop; the earbuds report the same two bytes, and 0 when a side stops |

- **Battery:** percent; 0 is an earbud that is not there. 1MORE's app rounds to the nearest ten.
- **Version:** each byte in hex, joined by dots, as 1MORE's app shows it.
- **Touch actions, per earbud:** 0 none, 1 next, 2 previous, 3 volume down, 4 volume up, 5 play or pause,
  6 voice assistant. The S-31, S51, S70, HQ20 and S25 do not offer the assistant (`ConstKt.clickMonauralActions`).
- **Touch actions, both alike:** 0 none, 1 play or pause, 2 previous or next, 3 volume, 4 voice assistant.
- **Hearing your own voice:** 1MORE's app turns it on (`0090 01`), then sets the depth; off is `0090 00`.
  It sends both only while both earbuds report a battery.
- **Noise cancelling without a choice made** is mode 1, and transparency mode 3 (`HeadsetActivity`,
  `prevNoiseReduction`, `prevTransparent`).

**EQ** (`CustomSoundActivity`, `DefaultSoundActivity`, `TestKt.custSoundData`):

- **Preset sounds (`006A`, `0069`):** one byte: 00 studio, 01 bass reducer, 02 bass booster, 03 acoustic
  (SOUND BY LUCA on the HQ60), 04 classical, 05 podcast, 06 deep, 07 electronic, 08 hip-hop, 09 lounge, 0A pop,
  0B loud (shown as Voice Enhancement), 0C custom, 0D THX. Each model lists six, four or twelve of them
  (`eq_presets` in `data/1more/devices.json`); the HQ60's THX depends on its firmware and is left out.
- **Custom EQ (`0054`, `0053`):** per band a record of 7 bytes: `01`, the raw gain, the frequency (Hz) and the Q
  (hundredths), two bytes each, high first. 1MORE's app writes every band at once when it resets a curve, and one
  band at a time while the user drags; PairFree writes every band at once.
  - The gain: 1MORE's app's slider goes from 0 to 120 around 60, and the raw gain is 5 times it plus the model's
    `gainBValue` (900, or 1200 on most headphones), so 0 dB is 1200 (or 1500) and the range is -3 to 3 dB in steps
    of 0.05 dB. PairFree uses whole dB. The HQ36 adds its own offset on some bands.
  - The Q12 and HQ60 take whole dB from -3 to 3 as (dB + 15) x 100 (`useProtocolGain`).
  - Each model's frequencies and Qs are in 1MORE's app code (`getEqBandConfig`), `eq_bands` in
    `data/1more/devices.json`.
  - The read (`0054`) names every band's frequency; 1MORE's app writes its length field as 20 whatever the band
    count, which the Q12 (12 bands) overruns, and PairFree sends it the same way. The answer is the same records.
  - 1MORE's app does not switch the preset sound before it writes a curve; PairFree reads both again after a write.
- Only the models whose product list has the custom flag get the custom EQ; presets follow the preset flag.

**Never sent:** the handshake (`004D`), the AI licence read (`004F`), deleting music (`0049`) and the music
upload frames (`0071` to `0073`).

## 5. Models

1MORE's app matches a paired device to its product list by the whole Bluetooth name (`headphoneName`) and
shows a setting only where the list's flag for it is 1 (`HeadsetConfig.is*Able`); without the list it shows
none. `data/1more/devices.json` keeps, per model, the name, the product model, these flags and the
pictures' paths (for tracing device art):

| Flag in the list | In devices.json |
|------------------|-----------------|
| `listeningMode`, `close`, `noiseReduction`, `dynamicNoise`, `deepnoiseReduction`, `comfortableNoise`, `antiwindNoise`, `penetrability`, `transparentEnvironment`, `vocalEnhancement` | `listen_modes` |
| `entertainmentMode`, `ldma` | `connection` |
| `dobleConnection`, `spatialAudio`, `autoplay`, `sleepDetection`, `hotwordMonitoring` | the switches of the same names |
| `monauralOperation`, `binauralOperation` | `touch`: `each` or `both` |
| `presetsoundEffects`, `stylesoundEffect`, `customizesoundEffects` | `eq` (not used yet) |

From 1MORE's app code, by name: which models show one battery (the HQ20, HQ31, HQ36, HQ51, HQ60, both
SonoFlows, the NANO and the G50: `kind` headphones), which have no case battery (S11, S12), the touch
actions without the assistant, and hearing your own voice and the volume limit (the HQ20 only).

**Tuya's AI earbuds:** 1MORE's app reaches the S52 and the S22Ultra through Tuya's platform
(`DataKt.aIDeviceList`, `HeadsetListActivity.connectTuYa`), not this protocol. PairFree does not list them.

## 6. Not yet

- **EQ:** the music and sleep styles (`003B`), the HQ60's THX preset, and 1MORE's finer gain steps.
- **The lights** of the NANO (`008A` to `008D`).
- **Music on the earbuds** (`0038` to `003A`, `0049`, `0071` to `0073`) and the sleep timer (`0068`).
- **The `FE` frames** (section 2), and whether the earbuds' XOR and length field always hold.
