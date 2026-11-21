# 1MORE protocol

How 1MORE earbuds and headphones are controlled, as PairFree does it. No 1MORE model has been tried with
PairFree yet.

## 1. Sources

- **1MORE's app**: 1MORE 2.0.0 (installed as `com.onemore.connect`; its code lives in the namespace `com.onemore.music`), decompiled outside the repo on 2026-10-18. None of
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

- **Head:** `11 01` from the phone, `01 00` from the earbuds. Life 5.0.3 sends `11 00` to its older models and `11 01` to the
  newer ones, the Q30 among them (`com.bitaxon.n2.c.f`, the header builder). Neither app checks a reply's head: the Music app
  splits by the command at offset 3 (`BluetoothService`, `getFeedbackLength`), Life reads the command at offset 3 of whatever
  one read returns (`com.onemore.life.c.a.c`). So what the newer models answer with is not pinned, and since 2026-11-20 (issues
  #58, #63) PairFree takes any reply that starts `01`: with `01 00` or `01 01` as before, with another second byte only when its
  XOR matches.
- **Command, length, sequence:** two bytes each, high byte first. The length counts the payload. The
  sequence number counts up from 0 for every frame the phone sends and starts over after `FFFF`
  (`Cmd.kt`, `makeData`).
- **XOR:** the XOR of the eight bytes before it.
- **Handshake:** `004D` with payload `01` is the first frame 1MORE's app sends once the serial channel is up (five call sites,
  among them `HeadsetListActivity`, `BluetoothManager` and `BluetoothListenerReceiver`); a reply of `01` is success, and the
  app's headset screen starts its reads on it (`DispatcherKt`, `HeadsetActivity`: EQ mode, battery, listen mode, ear return,
  volume limit, preset sound, find device). The first version of this document, and PairFree up to 0.8.x, took it for
  something the app never sends. A virtual Aero run (`VIRTUAL_TESTING.md`) saw the app send `1101004D000100015D01` and carry
  on with the battery read when it got no answer. PairFree sends it first and does not wait for the answer; whether real
  firmware needs it is not known.

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
| `0077` | `0076` | Two devices | 0 off, 1 on; Life's Lab features page (`ExperimentActivity`, 5.0.3) shows the switch by model, not by the product list's `dobleConnection`: EH902, ES903, EC305 (the Q30), HC905, HC306, EF906 (firmware 10405 or later), S51 and S70 (`tools/import_1more_list.py`, `LAB_DUAL_DEVICE`); the app says the earbuds restart when it changes |
| `0083` | `0082` | Spatial audio | 0 off, 1 on |
| `0063` | `0062` | Taking them out | 0 pause only, 1 pause and play again, 2 nothing |
| `0070` | `006F` | Sleep detection | 0 off, 1 on |
| `0081` | `0080` | Voice commands (wake word) | 0 off, 1 on |
| `0087` | `0084` | Touch, per earbud | read: double left, double right, triple left, triple right; write: the gesture (1 double left, 2 double right, 3 triple left, 4 triple right), then the action |
| `0065` / `0067` | `0064` / `0066` | Touch, both alike | double tap / triple tap |
| `0091` | `0090` | Hearing your own voice | 0 off, 1 on |
| `0093` | `0092` | Its depth | 1 low, 2 medium, 3 high |
| `0095` | `0094` | Volume limit | 0 off, 1 low, 2 medium, 3 high |
| `008A` | `008B` | The NANO's light mode | 0 one colour, 1 breathing, 2 colour cycle (RGB), 3 off |
| `008C` | `008D` | The NANO's light colour | 0 ice blue, 1 white, 2 green, 3 red, 4 yellow |
| `005B` | — | Finding the earbuds | sent with `01`; earbuds that can ring answer it |
| — | `005A` | Ring | left, right: 1 ring, 0 stop; the earbuds report the same two bytes, and 0 when a side stops |

**What shows on Life's home page, by model (read 2026-11-21, 1MORE Life 5.0.3: `e/c.java`, `fragment/d.java`, `utils/ah.java`).** A product that comes from the product list (`com.bitaxon.n2.d.a.a.u()`, which is every model PairFree takes from the list, the Q30 among them) shows each row by the list's own flag, which PairFree carries
(`j` the listen modes, `l` the equalizer, `n` the connection mode, `r` spatial audio and the rest). The `ah.*` model predicates decide only for products without a list entry and for four rows: the Lab page (above); `ah.ad` (EH902, ES903, EC305, HC905, HC306: a row with no action behind it); `ah.j` (EH902 from firmware 10002, ES903: the Major EQ page, a choice of
Sound ID, the presets and the custom curve; Sound ID is cloud data and is hidden on the ES903, EC305, HC905 and HC306) and `ah.ac` (the sleep songs: the soothing music player, whose transfer commands are never sent, `data/unused_ids.json`). So no row of the Q30's home page is missing for want of a model rule. checked: `e/c.java` (the flag
setters), `utils/ah.java` (every predicate), `fragment/d.java` (the click handler behind each row: `d.j` to `d.m`), `MajorEQActivity`.

- **Battery:** percent; 0 is an earbud that is not there. 1MORE's app rounds to the nearest ten.
- **Version:** each byte in hex, joined by dots, as 1MORE's app shows it.
- **Touch actions, per earbud:** 0 none, 1 next, 2 previous, 3 volume down, 4 volume up, 5 play or pause,
  6 voice assistant. The S-31, S51, S70, HQ20 and S25 do not offer the assistant (`ConstKt.clickMonauralActions`).
- **Touch actions, both alike:** 0 none, 1 play or pause, 2 previous or next, 3 volume, 4 voice assistant.
- **Hearing your own voice:** 1MORE's app turns it on (`0090 01`), then sets the depth; off is `0090 00`.
  It sends both only while both earbuds report a battery.
- **The light:** 1MORE's app shows it for the 1MORE NANO alone (`HeadsetActivity`), and reads the new value
  from the answer to a change as from a read.
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
  - Choosing Custom in 1MORE's app sends preset sound `0C` (`0069 0C`; `DefaultSoundActivity.getStyleCmd`, the list entry
    "custom_eqs"), and the editor then writes the curve. The app's own band slider writes one band's record per move, its
    reset writes every band at 0 dB in one `0053`. PairFree writes every band in one `0053` and, on a model that also has preset
    sounds, sends `0069 0C` before it unless the earbuds reported that they already hold it; it reads both again after a write.
- Only the models whose product list has the custom flag get the custom EQ; presets follow the preset flag.

**Never sent:** the AI licence read (`004F`), deleting music (`0049`) and the music
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

- **PistonBuds PRO Q30 (EC305), issues #58 and #63 (2026-11-19):** two users' earbuds connect over the serial channel and never answer a read. blocked: 1MORE Life 5.0.3 (`com.onemore.life`, read 2026-11-19) controls the Q30 over the same
  channel and frame as PairFree and shows no step PairFree lacks; checked: the app's serial connector (`com.bitaxon.n2.k.a`: `getUuids()` first, then a secure `createRfcommSocketToServiceRecord(00001101-…)`, a plain
  reader), its frame builder (`com.bitaxon.n2.c.f`: `11`, a flag byte that is 1 for the Q30, command, length, sequence, XOR, payload), its first requests on connecting (the battery read `004E`, then the model's list; the
  handshake `004D 01` comes later, on its own event), its command table (`com.onemore.life.c.a.a`, the same numbers as `commands.json`), its name mapping (`1MORE PistonBuds PRO`, `1MORE Pistonbuds PRO Q30` and `... PRO Q30`
  all map to EC305) and the EC305 handlers (the battery reply carries one more byte, `[11]`, which no other model of the app's reads; the custom EQ's reply is offset by a per-model table). Nothing there explains
  silence, so the cause is on the channel (a secure socket after `getUuids()`, which PairFree 0.16.1 now also tries, then insecure and channel 1) or in the earbuds' state; waiting for a connection report from 0.16.1.
  A device report from 0.17.0 (#58, 2026-11-20, Android 17) is still empty. A second look found one place where PairFree was stricter than both apps: it dropped every
  reply not headed `01 00`, while the apps never check the head and Life heads the Q30's requests `11 01` (section 2). If the Q30 answers `01 01`, PairFree threw
  every answer away, which would look exactly like silence. PairFree now takes such replies, and the device report says how many bytes arrived and their first four,
  so the next report tells silent earbuds from replies PairFree had to skip. Not confirmed on a Q30.

- **Models from 1MORE Life 5.0.3, added 2026-11-19** (`LIFE` in `tools/import_1more_list.py`; the Music app's product list lacks them): ComfoBuds 2 (ES303), ColorBuds 2 (ES602), PistonBuds Pro (EC302), ComfoBuds Pro (ES901),
  SonoFlow (HC905), SonoFlow SE (HC306), Fit (EF906), Fit SE (EF606) and S31. Source: Life's `DeviceFactoryImpl` (name to model), its per-model classes (`com.bitaxon.n2.d.a`: the requests it sends on connecting, which show
  autoplay `0063`, game mode `006C` and spatial audio `0083`), its home screen's listen mode buttons per model (values 0 off, 1 strong, 2 mild, 3 transparency, 4 wind; the ColorBuds 2's screen hides its off button, which PairFree
  keeps because the earbuds' own off is the first mode), its equalizer screens (`preset_eq_content`, the twelve presets, for ES303, EC302, S31, EF906 and the SonoFlows, with the custom curve on ES303 and EC302; `eq_content`, 22
  older presets sent as their index, for ES901, ES602 and EF606: `values.preset_sound_classic`) and its touch screens (`fragment/d` picks `OperationSettingActivity`, a tap per earbud with command `0084`, for the models of its list K, which hides
  the assistant on S31; `OperationCustomActivity` and `ES901OperationCustomActivity`, one double and one triple tap for both earbuds, `0064` and `0066`, for the others). New commands: `006D`/`006E`, what a touch and hold does per earbud
  on the ComfoBuds Pro (left and right bytes, 1 strong, 2 mild, 4 wind), and `0074`/`0075`, the switch that turns the touch controls off, which Life shows on the ComfoBuds Mini above firmware 1048 (1 is off). Device drawings are traced from
  Life's own product renders (`res/drawable/*_color_black`). None of these models has been tried on earbuds.
- **Life's own EQ bands (2026-11-19):** Life's `CustomEQActivity` and `utils/f.a` give three models their own custom curve, which PairFree had filled with the Aero's bands for the Q30: the PistonBuds PRO Q30 (bands 20, 65, 160, 360, 800, 2800, 4600, 6000, 8500 and 16000 Hz, Qs 50 to 250, the raw gain's offsets 1100, 700, 100, 300, 650, 1300, 200, -400, 300 and 800 on top of 1200), the SonoFlow and the SonoFlow SE (their own bands and offsets in `BANDS`, `tools/import_1more_list.py`). The Fit SE opens the custom EQ alone (no presets). Life matches Bluetooth names exactly, case included, so `1MORE PistonBuds Pro` is the EC302 and `1MORE PistonBuds PRO` the Q30 (`aliases` in `devices.json`; PairFree tries the exact case first). The Q30's battery reply has one more byte, `[11]`, which Life reads only for its firmware update.
- **Life's commands no screen of PairFree uses:** `0060`/`0061` (a wind noise switch) and `0078`/`0079` (assisted listening) are in Life's command table and never sent by any of its screens (checked: every caller of `utils/f` and the `BTCmd` enum); `0088`/`0089` start and carry the head motion data for the head-tracked 3D sound of Life's own music player (`Onemore3dAudioActivity`), which plays on the phone and is not a setting of the earbuds.
- **Life's Bluetooth LE families and QuietLace:** blocked: the E1001, E1004, E1020, E1022, E1026, S1001, ESS3001T and ECS3001T families (the iBFree, Triple Driver and older ANC products) speak other, older protocols over Bluetooth LE
  (Nordic UART `6e400001-…`, `e6781ec0-…`, `00006666-…`, a BES service `01000100-…`), and Life learns the product number from the device and the product's name from 1MORE's server, so no Bluetooth name of theirs is in the app; QuietLace
  (ES605) is only a drawing in Life, with no device class, command or feature of its own. checked: `IBleManager` (every service and characteristic), `ah` (the model predicates), `DeviceFactoryImpl`, `ImageUtils`, the app's strings and assets
  (no product list), `d/a/*` (a class for each model that has one). Server (2026-11-19, asked by the owner): Life learns each device type's name, pictures and guide images from the answer to its login (`LoginResp.deviceTypeMsg`, `HttpUtil.b`; requests to `app1.1more.com`, protobuf over HTTP with an MD5 of the body and a session key), and the login takes a phone number, an email address or a third-party code with a verification code (`NumberLoginActivity`, `HttpRequestUtil.a`), so blocked by the hard rule on server authentication: no request was made. The unauthenticated requests Life makes (`verify_fw?DeviceType=`, the Kugou music API) carry no names. The Music app's product list (`system/headphone/list`, already fetched) holds none of these families. Needs the owner to run a research script with their own account (typed at its prompt, never stored), or a recording of each family and its Bluetooth name.

- **EQ:** the music and sleep styles (`003B`/`003C`), the HQ60's THX preset, and 1MORE's finer gain steps. 1MORE's
  app 2.0.0 declares the styles (`BluetoothOrderConstents`) but never sends them, so nothing shows their payloads. **Corrected 2026-11-12: 1MORE 2.1.3 does send them** (`HeadsetActivity`: `sendSppSetEQMode(0)` music and `(1)` sleep; `SetTimeActivity`: `sendSppSetPlayTime((i + 1) * 2)`, or 0 for no timer; `ShortcutActionsActivity`: `sendSppSetVocalEnhancement`), so the layouts can be built from 2.1.3.
- **Built 2026-11-13 from 2.1.3:** the music and sleep modes (`003B` write, `003C` read, one byte, 0 music and 1 sleep; on the four models whose product entry has
  `comfortableMelody`: ComfoBuds Mini, Z30, Q50, PistonBuds PRO Q30) and the Q50's voice enhancement switch (`0096` write, `0097` read; 1 on, 0 off; 1MORE's
  app hides it on any other name). The sleep timer (`0068`, byte `(i + 1) * 2` for the wheel's 15 minute steps, 30 on the Z30, 0 unlimited), the music
  status, list and play commands and the volume (`004C`) belong to the soothing music player, which plays what the app uploads: blocked while uploading is never
  sent (see `data/unused_ids.json`). `vocalEnhancement` in the product list is the listening mode 6 (voice enhancement) of the EVO and S90, a different thing.

- **Music on the earbuds** (`0038` to `003A`, `0049`, `0071` to `0073`) and the play time or sleep timer (`0068`),
  which the app also declares but never sends.
- **The `FE` frames** (section 2), and whether the earbuds' XOR and length field always hold.
