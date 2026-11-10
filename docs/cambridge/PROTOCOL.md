# Cambridge Audio Melomania protocol research

Research only, 2026-11-06. No Cambridge Audio implementation was added and no hardware was connected.
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
no Cambridge product is marked supported or tried by this research.
