# Cambridge Audio Melomania protocol research

Research 2026-11-06; local implementation draft and [virtual tests](VIRTUAL_TESTING.md) added subsequently.
No hardware was connected.
The supplied Melomania app directly controls **Melomania 1+ and Melomania Touch**. It recognizes newer
names to direct the user to another app; their protocol must be kept separate.

## 1. Sources and app boundary

Primary source: Melomania by Cambridge Audio 2.8.12 (`com.cambridgeaudio.melomania`), reference XAPK SHA-256
`0e4e2ddb205bfb3af2a8b4a83add8180a7854407a6163eaa93f026d05cdf1ba6`.
This replaces the unrelated StreamMagic app examined in the old external findings. All splits were
inventoried, including arm64; its sole native library is `libJni_wgs2gcj.so`. The headphone packet,
setting and EQ logic examined here is Java/Kotlin, not hidden in that native library.

Decompilation is outside the repo at
`E:\Builds\research\vendor-audit-2026-11-06\cambridge\jadx`. Source locators relative to `sources/`:

- `com/cambridgeaudio/melomania/b`: supported-name and newer-incompatible-name arrays.
- `activities/SearchingActivity`, `b3/b0` (NotCompatibleFragment): selection and redirect to
  `com.cambridgeaudio.melomaniaplus` (Melomania Connect).
- `services/a` (BREDRProvider), `GAIABREDRService`, `GAIAGATTBLEService` and `services/b`.
- `sb/a,b,c`: GAIA packets; `rb/c`: request/ACK manager; `q2/a,b`: vendor 000A setting manager.
- `q2/b.I`, `com/cambridgeaudio/melomania/f`, `activities/MelomaniaMainActivity.j`: state decoding.
- `q2/b.K,L`, `t3/b` (SetHeadphoneEqBandsImpl), `r3/b,c`, `p2/`: EQ serialization, limits and presets.
- `b3/e` codec, `b3/o` voice language, `b3/r0` sound-through, `b3/i0,n` control masks.

Public check for newer models: [BudsLink Cambridge source](https://github.com/maniacx/BudsLink/tree/main/src/lib/devices/cambridgeBuds),
revision `0eb4d07dcd7eafabf425d98c607e02fc9519c94b`, GPL-3.0 (facts only, no source copied), credited by
its documentation to baarkerlounger for the protocol. Its [Cambridge page](https://maniacx.github.io/BudsLink/cambridge)
reports Melomania A100 hardware use; that does not establish PairFree testing or M100/P100 support.

## 2. Which model belongs to which evidence

| Model | Evidence in supplied app | Research consequence |
|---|---|---|
| Melomania 1+ | direct model selection, setting/EQ screens and wire builders | vendor-app basis for future implementation |
| Melomania Touch | separate selection, setting/EQ screens, feature differences | preserve model/firmware restrictions |
| A100, M100, P100 | names in a separate array; NotCompatibleFragment opens Melomania Connect | their names do not establish support by this APK |
| A100 | separate BudsLink source and hardware report | newer-family protocol lead, section 6 |
| Original Melomania 1, P100 SE, other variants | not direct model implementations established in this APK | do not add by name similarity |

SearchingActivity selects 1+ as model 1 and Touch as model 2, stores Classic transport type 1, and starts
the corresponding flow. The newer names cause the incompatible-app UI instead. This is a concrete
app boundary, not a conclusion that newer products cannot be implemented. Obtaining Melomania Connect
would supply the primary vendor evidence for those products; no such APK was in this reference folder.

## 3. Transport, frames and replies for 1+ / Touch

BREDRProvider accepts standard SPP `00001101-0000-1000-8000-00805F9B34FB` and GAIA RFCOMM
`00001107-D102-11E1-9B23-00025B00A5A5` from SDP. The searched/direct-control flow selects Classic;
BLE service/packet implementations also exist and must not be conflated with Classic frames.
Only one app can hold a control socket. BLE scanning would require a separate permission decision.

Classic frames are GAIA v1:

```text
FF | 01 | flags | payload length u8 | vendor u16 BE | command u16 BE | payload | optional XOR
```

`q2/a` fixes vendor to `000A`. `sb/c` appends XOR of all preceding bytes only when checksum flag bit 0
is set; the setting manager constructs packets with that option false. A future parser must still
accept the checksum flag and split by declared length. `sb/b` is the shorter BLE vendor/command packet,
not a complete Classic stream frame. The packet builder rejects payloads longer than 254 bytes.

A GAIA acknowledgement sets command bit 15 and begins its payload with status; zero means success.
Notification/request packets are not all ACKs. `rb/c` matches pending ACKs by masked command and keeps
per-command queues with a default 30-second timeout; this is not evidence of a globally serial 1-second
queue. The settings manager refreshes the composite state after three seconds in its polling path.

Analytical examples only, no hardware captures:

```text
Read composite state: FF 01 00 00 00 0A 07 80
Band 1 gain +2 dB:    FF 01 00 05 00 0A 02 1A 06 12 00 78 01
```

The second example follows q2/b.K's signed gain times 60 and its fixed parameter header; it does not
prove that a particular device has accepted a write.

## 4. Legacy commands and state

Command ids below are hex, vendor `000A`.

| Setting | Read / write | Builder / semantics |
|---|---|---|
| Composite state | `0780` | q2/b.E(2), q2/b.I; returned state includes both firmware versions, sound-through/control/codec-related values and addresses |
| EQ preset | `0294` / `0214` | q2/b.E(3), F, L; one preset id on write |
| EQ parameter | reply handled as `029A`; write `021A` | q2/b.G, K; do not invent a read request payload from the reply parser alone |
| Control mask | `0783` / `0782` | q2/b.E(4), H, M/N; two-byte mask, preserve unrelated bits |
| Sound-through enable | composite state / `0798` | q2/b.P; 1 enabled, 2 disabled |
| Sound-through level | composite state / `0799` | q2/b.O; one slider-level byte |
| Codec preference | composite state / `07B8` | q2/b.J; one selection id, validated against model UI |
| Voice language | composite state / `020B` | q2/b.Q; one UI-selected language index |

The state parser has length checks that are weaker than some subsequent indexed reads (for example,
a branch checks at least 12 bytes before referencing byte 13). A future codec must check each actual
required offset. Firmware triples begin at response-payload offsets 11 and 14 (including the ACK status
at offset 0); address fields occupy later bytes. Do not log whole state frames: they contain addresses.
Some fields vary by total reply length. Preserve the result as unknown when a shorter firmware omits it.

The vendor's screen sets sound-through level on slider release; the switch writes the enable enum.
The control screen toggles individual bits, then writes both mask bytes and retains its last value.
A future implementation should read/modify/write the specific setting, rather than send a guessed mask
or repeatedly XOR a stale value. Follow the model screen for bit meaning, not a brand-wide gesture table.
Codec changes can interrupt the channel; the codec screen waits and reconnects. This preference does not
force the phone's active audio codec.

## 5. Legacy EQ

There are five gain values, clamped to -6..+6 dB by SetHeadphoneEqBandsImpl. Slider writes are debounced
250 ms. q2/b.K writes a five-byte payload: fixed `06`, packed band/parameter id, gain times 60 as two
big-endian bytes, and final `01`. The packed id is `(band << 4) | parameter`; band zero uses parameter 1,
other bands parameter 2. Follow the call site's actual band numbering before offering a preamp.
The receive handler divides the corresponding parameter by 60. The frequency grid must be established
from the legacy model's EQ/UI evidence before displaying a curve; do not copy A100's seven frequencies.

The app's five-value preset curves (`r3/b`) are:

| Id | Preset | Five gains (dB, stored order) |
|---|---|---|
| 0 | Neutral | 0, 0, 0, 0, 0 |
| 1 | Immersive | 0, -2, 0, 1, 0 |
| 2 | RnB | 3, 3, 0, 3, 0 |
| 3 | Electronic | 0, 4, 0, 1, 5 |
| 4 | Rock | 0, 3, 0, 1, 0 |
| 5 | Voice | -6, -2, 0, 6, 0 |

The enum also has Custom1..3 ids 6..8 and CustomNotSaved 9. These names and curves belong to this app's
legacy model flow; they are not A100 presets. Follow preset versus custom application/restoration paths
before implementing persistence. Local preset storage is not proof of an earbud-side readable bank.

## 6. Newer A100 protocol facts (BudsLink, not this vendor APK)

BudsLink uses GAIA legacy/v3/v4 framing with vendors CSR `000A`, Qualcomm `001D`, Cambridge `0B9E`.
It registers notifications and uses Qualcomm feature commands plus a separate Cambridge vendor command
space. Keep these facts attributed until Melomania Connect or recordings confirm the desired model.

| Area | Newer-family command facts (hex) |
|---|---|
| Qualcomm battery | `1A01`, reply `1B01`; component ids 0 single, 1 left, 2 right, 3 case; FF unavailable |
| Qualcomm EQ | preset read/write `0A02/0A03`; band read/write `0A05/0A06`; user preset `3F` |
| Qualcomm gestures | read/write `1604/1605`; responses in feature `17xx` |
| Cambridge noise | read/write `000D/000E`; 0 off, 1 ANC, 2 transparency |
| Cambridge prompts | `0006/0007`; newer enum differs from the legacy app |
| Cambridge auto-off | write/read `0008/0009`; hours, minutes, seconds bytes |
| Cambridge dynamic EQ | write/read `0014/0015` |
| Cambridge game mode | write/read `0200/0201` |
| Cambridge wear detect | write/read `0206/0207` |
| Cambridge mono | write/read `0208/0209` |
| Cambridge sleep | write/read `020A/020B` |
| Cambridge codecs | write/read `0400/0401`; negotiated codec list |

The reference's A100 config has seven bands at 60,120,500,1000,2000,4000,10000 Hz, range +/-6 dB.
Its seven-value curves and gesture records must not be copied to 1+ or Touch. Gesture entries pack a
side/context/action and can encode several context actions for one gesture; they are not one arbitrary
button byte. Its auto-off choices are 0/30/60 minutes, but those are A100 config choices, not a promise
for M100/P100 or their variants.

The reference's initialization subscribes to Qualcomm feature `06` as well as other features. Under
PairFree's firmware boundary, that firmware-related subscription must be reviewed/excluded rather than
copied wholesale. The documented local settings do not require fetching firmware or impersonating
another app.

## 7. Exclusions, checked evidence and next steps

Never send firmware upgrade (`q2/c`, its upgrade channel/commands), resets, power-off actions, tests,
logs or cloud account/registration traffic from a probe. Composite state and paired-device history
contain addresses. Find-device/location screens include phone/app-side history; their existence does
not establish an audible ring command. Do not add location permission or Firebase access for them.
The app's server/account code was not contacted; the protocol facts above are local.

Checked: all splits, direct and incompatible model selection, RFCOMM and BLE packet classes,
ACK manager, setting/state parsers, EQ builder/presets/debounce, codec/language/control/ambient screens,
native inventory, and BudsLink's newer-family protocol/config. The whole-app decompilation had 31
reported jadx errors; critical decoded methods above were inspected individually, and errors are not
proof of encryption. Native code was not treated as a blocker for the readable control path.

Remaining analysis: full model/firmware screen-to-setting matrix, exact legacy EQ frequency grid and
control-bit meanings, complete composite state schema, and modern Melomania Connect vendor evidence.
Modern vendor APK absence limits the primary evidence, not the ability to make progress from the
public A100 work. Future implementation needs byte-for-byte vectors and real recordings/hardware;
no Cambridge product is marked tried by this research.

## 8. Local implementation draft for review

`data/cambridge/commands.json` supplies all wire constants, offsets, enum tables and preset gains to
`generateCambridgeTables`; `devices.json` restricts the implementation to 1+ and Touch.
`CambridgeParser` handles GAIA v1 stream boundaries, optional XOR, length limits and malformed frames.
`CambridgeSession` reads composite state, preset and control mask. Writes are allowlisted, require a
currently offered setting and are followed by the relevant read. The Android queue waits for matching
ACK command ids, including unsuccessful ACKs, with the vendor's 30-second deadline. It serializes requests
conservatively; it does not claim that the vendor globally serializes different commands.

Additional screen tracing for this implementation established:

- Composite ACK payload: 2 ambient flag, 3 language index, 8 left battery, 9 right battery, **10 ambient
  level**, 11..13 left firmware, 14..16 right firmware, 17..28 addresses, 31 active codec, 32 codec preference.
  The preset is read separately with 0294; byte 10 is not an EQ preset.
- `a3/h`, `b3/i0` and `earphone_controls_item_m1/touch` map mask bits 0..8 to play/pause, next,
  previous, volume up/down, answer/reject/end calls and voice assistant. Touch adds bit 9 for ambient.
  The two mask bytes are big-endian. The draft sets/clears a bit rather than blindly XORing it.
- `b3/n` hides ambient controls on 1+. `fragment_transparency.xml` sets the level range 0..100.
- `voice_guidance_item` maps indices 0..7 to Cantonese, Chinese, English, French, German, Italian,
  Japanese and Spanish. The language screen requires both earbuds present (battery > 0).
- `b3/e` maps codec preference 0 SBC, 1 AAC, 2 aptX, 3 AAC+aptX; active codec 3 locks its controls.
  The draft conservatively also requires both earbuds present before changing this preference.
- `l3/b`, `m3/b` and `r3/h` use bands **1..5**, not 0..4. `u3/b` switches to Custom1 (6) before
  `t3/b` sends gains. The draft writes all five explicit values, not an assumed existing custom bank.
  The UI labels are bass/mid-range/treble without numeric frequencies (`fragment_equalizer.xml`: five vertical sliders
  `vs_band1` to `vs_band5`, the labels under the left end, the middle and the right end, and a line chart through five equally spaced
  points); PairFree names bands 1, 3 and 5 as the app does and numbers the rest, and draws no frequency curve. Custom state from another app is unknown until the user chooses a known preset.
- **High performance audio is firmware installation**, not an ordinary setting: `b3/d` chooses raw
  resources `m1_d_class`, `m1_ab_class`, `touch_d_class`, `touch_ab_class`, writes a temporary file and
  enters the upgrade flow. Blocked: firmware restriction; checked: b3/d switching handlers, raw-resource
  selection and upgrade calls. No such resource or command is included in PairFree.

Device reports retain only the established fields of the three requested reads and zero offsets 17..28.
Raw composite frames are not logged. The implementation has no account, location, reset, upgrade or
power commands. Newer models remain separate. Before merging, review the gain-bank behavior and obtain
a hardware recording; tests exercise analytical frames, synthetic short/error replies, model boundaries,
privacy redaction, read-back scheduling and forbidden writes. Hardware validation remains unperformed.

Validation of the local draft: all six Cambridge protocol tests, the complete protocol test suite,
`:app:assembleDebug`, `:app:lintDebug`, and all 133 Python tests passed in the E: build mirror. Device-page
generation and the generic artwork generator were run; the models use the existing generic earbud art.
The queue also invalidates the editable gain state after a failed/missing bank or band ACK; the allowlist
then refuses remaining queued band writes until a known preset has been read again.

## 9. Vendor-app virtual testing

The original Melomania 2.8.12 APK has now connected to virtual Touch and 1+ instances over Classic Bluetooth.
The simulator's replies remain synthetic; outgoing app frames are runtime observations, not hardware evidence.
See [VIRTUAL_TESTING.md](VIRTUAL_TESTING.md) for the environment, exact checks and limitations, and
`data/cambridge/emulator_vectors.json` for 78 selected outgoing command cases and APK hashes.

Custom save/recall revealed Neutral 0 -> custom mode 6 -> gains. PairFree's full-curve write now follows
that sequence. The three named custom slots belong to the vendor app's phone-local storage; selecting
Custom 2 does not imply device preset 7. The UI wording was corrected accordingly. Runtime tests also
confirm that codec application can be enabled with both simulated earbuds present; the ambiguous
decompiler conditional in b3/e should not be interpreted as requiring an absent left earbud.

The complete protocol suite, debug APK build, Android lint and 133 Python checks pass after these changes.
Neither model is counted as hardware tested. A distinct "Vendor app tested virtually" category is generated
on the device page from each model's `virtual_tests` records, independently of hardware `reports`.

## 9. Melomania Connect 2.6.0: the vendor app for A100, M100 and P100 (2026-11-12)

The owner supplied Melomania Connect 2.6.0 (`com.cambridgeaudio.melomaniaplus`, APKPure XAPK): **unprotected**, four dex files, 15,096 classes (jadx, `E:\Builds\research\decompiled\melomania-connect-2.6.0`). It replaces BudsLink as the source for the newer family (section 6, which stays as the cross-check): the protocol is Qualcomm GAIA v3 with a Cambridge plugin. `CAMBRIDGE_AUDIO_VENDOR_ID` is 2974 (`0x0B9E`, as BudsLink's vendor number). The code is clean Kotlin with named commands:

- `devices/gaia3/customapi/CambridgeAudioPlugin`, `CambridgeAudioVendor` and `Gaia3CustomApi`; models `Commands`, `Responses`, `FeatureIds`, `ModesIds`, `GestureIds`, `CodecIds`, `MiscIds`, `AudioMode(s)`, `AmbientNoiseAction`, `Codec`, `Gesture`, `Language`, `LeftRightBalance`, `Timeout`, `VirtualizerMode`, `BatteryState`, `FirmwareVersion(s)`; encoding in `utils/PacketEncode` and `PacketDecode`.
- Repositories for audio curation, battery, custom info, device info and discovery in `devices/gaia3/`.

### Built (2026-11-12): the custom API

`CambridgeConnectSession` and `CambridgeConnectTalk` speak the plugin's custom API (`data/cambridge/commands.json`, section `connect`, read from `PacketEncode`/`PacketDecode`, `MiscIds`, `ModesIds`, `CodecIds`, `GestureIds`). A frame is `FF`, version 3, flags (1: XOR byte at the end, 2: two length bytes), the payload length, vendor `0B9E`, the command word `feature << 9 | type << 7 | command` (type 0 command, 1 notification, 2 response, 3 error) and the payload. Transport and Bluetooth service are the legacy models' (GAIA UUID or SPP); the app waits 100 ms between sends (`TX_DELAY_MS`).

What the rows read and write (payloads as `PacketDecode` reads them): API level (major, minor; gates the toggle options at 1.9, loudness at 1.14, the device list at 1.15, side tone at 1.17); battery (upgrades allowed; charging left, right, case; percent left, right, case; left and right in the case); two 3-byte firmware versions; ambient noise mode (one byte, `ANCMode`: off 0, ANC high 1, transparency low 2, ANC medium 3, transparency high 4, ANC low 5, voice focus 6, and 7 to 9 for the *_OFF variants, which PairFree does not offer); the toggle options (three flags: normal, noise cancelling, transparency); language; automatic power off (hours, minutes, seconds; the app offers 30 minutes, 60 minutes and never); gaming, proximity (wear detection), mono and sleep modes, loudness, side tone and find-headphones alert (one flag); spatial audio (flag and virtualizer mode 1 or 2, which the app takes from the phone's Dolby detector, so PairFree keeps the mode the earbuds report); enabled, available and current codecs (a list of codec ids); supported and enabled gestures (16-bit little-endian masks; bit i is gesture i; one gesture is switched with `SET_GESTURE_CONTROL_ON_OFF (id, flag)`).

Which controls a model gets is the app's own table (`LocalConfig`/`DeviceConfig`, decoded from the constructor masks and kept as `config` in `data/cambridge/devices.json`): touch controls, mono and sleep mode are on unless a model turns them off (the P100 and P100 SE, which are headphones, do); loudness on the A100, A100 SE and P100 SE; side tone on the P100 and P100 SE; spatial audio on the A100 SE; codec choice and the list of connected devices on the A100 and A100 SE (the P100 models also list devices); the X100 has no entry, so every optional control is off there.

Gestures: the earbuds list what they support in a 16-bit mask and PairFree switches those on or off, pairing mode and wear detection included (bits 12 and 13; an earlier version of this section left them out wrongly). Switching a gesture is a setting, not the action: PairFree never triggers a power-off, a reset or pairing. Three gestures lie outside the mask (power on/off 240, case pairing 241, case factory reset 242): the app lists them as switches in its user guide and cannot read their state, so PairFree shows them as on/off choices with no current value and sends exactly the app's `SET_GESTURE_CONTROL_ON_OFF (id, flag)`.

Balance: custom API `SET_LEFT_RIGHT_BALANCE (isLeft, percent)` and its read are sent as the app builds them (the app itself only reads it; its own screens never set it). PairFree shows one slider, negative for `isLeft`; which side the percentage attenuates is not stated anywhere in the app, so that reading is unconfirmed.

The equalizer is Qualcomm's music processing feature (vendor `001D`, feature 5, read from the gaiaclient library bundled in the app: `V3MusicProcessingPlugin`, `BandInfo`): reads `GET_BAND_COUNT` (4), `GET_SET` (2), `GET_BAND_CONFIG` (5, payload start and end band) answered with 7-byte bands (frequency u16, Q u16/4096, filter u8, gain s16/60 dB); writes `SET_SET` (3, payload 63 for the user set) and `SET_GAINS` (6, start, end, s16 gains in 1/60 dB). The app writes a preset by sending that preset's gains to the user set, so PairFree does the same; the presets are the model's lists in the app's bundled `RemoteConfigDefault.json` (the app also reads Firebase Remote Config, which may differ), in `devices.json` as `eq.presets`. The gain range is -9 to +3 dB (the app's slider is -3 to +3 in 0.1 steps, times 3 on the negative side). The P100 models carry their bands in the order 120, 35, 400, 1600, 3750, 8500, 8100 Hz in the app's preset lists; PairFree keeps the earbuds' own order. The X100's curve is not editable in the app (no range in its default config), so it has presets only.

Noise levels: the app also reads Qualcomm's audio curation feature (feature 8: mode, gain, balance, wind noise, auto transparency, howling, noise ID) but writes only the ANC mode through it, which PairFree sends through the custom API's `SET_AMBIENT_NOISE_MODE` as `Gaia3Device` does; the gain is read for display only.

Not sent, and why: the Bluetooth addresses (private); connect and disconnect of the device list (`GetDevices` answers one device per frame with no count in the app's code, so the list's layout is not settled; open, not blocked); flight mode (`SetFlightMode` is only on the app's API test page); the touch gesture configuration (Qualcomm feature 11: per-gesture actions for tap, double tap and long press, which the app's assign-action screen sets; to read next). Nothing here has been tried on earbuds: the layouts come from the app's code alone.
