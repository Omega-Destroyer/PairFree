# AirPods and Beats protocol (Apple's accessory protocol)

How PairFree talks to AirPods and to Beats on Apple's chips. Apple's apps are not involved:
everything here comes from two open-source projects that worked the protocol out:

- [LibrePods](https://github.com/kavishdevar/librepods) by LibrePods contributors
  (GPL-3.0-or-later, commit `4589176`), which controls AirPods from Android and Linux. Its notes
  (`docs/`, captured from AirPods Pro 2 (USB-C), firmware 7A305) and code (`AACPManager`,
  `BluetoothConnectionManager`, `data/AirPods.kt`, `utils/RootlessSupport.kt`) are the source
  of most of this, and PairFree's code is ported from them. LibrePods calls the protocol AACP.
- [MagicPodsCore](https://github.com/steam3d/MagicPodsCore) by Aleksandr Maslov and Andrei
  Litvintsev (GPL-3.0, commit `173245a`), which controls AirPods and Beats from Linux. It
  confirms the setting values, and lists the Beats and AirPods Max models (`AapModelIds`).

Nothing here has been tried with PairFree yet; a device report from any model helps
([device page](../devices/apple.md)).

The data is in `data/apple/`: `commands.json` (the channel, the fixed packets, the opcodes and
control commands PairFree uses, and what is never sent), `devices.json` (the models) and
`test_vectors.json` (LibrePods' documented packets, with serial numbers zeroed).

## 1. Channel, and which phones open it

Classic Bluetooth L2CAP on PSM `0x1001` (4097), authenticated and encrypted. Paired AirPods list
Apple's accessory service `74ec2172-0bad-4d01-8f77-997b2be0722a`, which is how LibrePods knows
them; PairFree does the same, and takes "AirPods" in the name too. Beats are known by the
service alone, since the ones on Beats' own chips (Studio Buds, Studio Buds +, Solo Buds) speak
another protocol with Beats' app, though MagicPodsCore lists them.

Android has no public way to open a classic L2CAP channel. LibrePods makes the socket through
`BluetoothSocket`'s hidden constructors, trying each Android version's signature, newest first;
PairFree does the same (`AppleLink`). Android's Bluetooth stack then refuses the AirPods' channel
(Google issue 371713238) except where it is fixed: Android 17 and later, Pixels on Android 16
QPR3 (build ids starting `CP1A`), and OnePlus, OPPO and realme phones on Android 16
(`AppleSupport`, LibrePods' `isSupported`). Elsewhere LibrePods needs root and Xposed; PairFree
never uses either. On a phone outside the list it does not try the channel, as LibrePods does by
default, and reports that Android 17 is needed; on the others it gives up after 5 s, as
LibrePods does.

## 2. Packets

```
04 00 04 00  opcode (2 bytes, low first)  data
```

Each L2CAP read is one packet. A **control command** is opcode `09` with an id and four value
bytes, unused ones 0; the AirPods answer a write with the same packet, and send one for every
setting after the notification request and whenever a setting changes.

| Opcode | What |
|--------|------|
| `04` | Battery: a count, then per battery its component (`01` single, `02` right, `04` left, `08` case), `01`, the level, the status (`01` charging, `02` not, `04` disconnected) and `01` |
| `06` | Ear detection: each AirPod `00` in ear, `01` out, `02` in the case |
| `09` | Control command |
| `1D` | Information: null-terminated strings: name, model number, manufacturer, serial number, two versions, more serial numbers and data |
| `4B` | Conversation Awareness turning the volume down and up |

LibrePods' example battery packet, `04 00 04 00 04 00 03 02 01 64 02 01 04 01 63 01 01 08 01 11
02 01`, is the right AirPod at 100 %, the left at 99 % and charging, and the case at 17 %
(component `02` is the right one in LibrePods' and MagicPodsCore's code; its notes' table says
left). A disconnected battery is shown as unknown.

PairFree reads the model number and the first version from the information packet, the way
LibrePods splits it, and keeps no serial number; it leaves the packet out of its debug log.

## 3. Opening

1. The handshake, `00 00 04 00 01 00 02 00 00 00 00 00 00 00 00 00`; the AirPods answer nothing
   before it.
2. Feature flags, `04 00 04 00 4D 00 D7 00 00 00 00 00 00 00`, as LibrePods' code sends them
   (its notes give `FF`, MagicPodsCore `0E`): they switch on what Apple keeps to its own devices,
   such as Adaptive Audio and Conversation Awareness while media plays.
3. The notification request, `04 00 04 00 0F 00 FF FF FF FF`.

LibrePods sends them 200 ms apart, and again 5 s later; PairFree does the same. It does not send
LibrePods' head tracking start and stop, nor its request for the AirPods' keys.

## 4. Settings

Control command ids and values (`commands.json`; LibrePods' `control_commands.md`, MagicPodsCore's
`AapCmdSettings`). Switches are `01` on and `02` off.

| Id | Setting | Values |
|----|---------|--------|
| `0D` | Listening mode | `01` off, `02` noise cancelling, `03` transparency, `04` adaptive |
| `0A` | Automatic ear detection | switch |
| `17` | Press speed | `00` default, `01` slower, `02` slowest |
| `18` | Press and hold duration | `00` default, `01` shorter, `02` shortest |
| `1B` | Noise control with one AirPod | switch |
| `23` | Volume swipe length | `00` default, `01` longer, `02` longest |
| `25` | Swipe to change volume | switch |
| `26` | Personalized Volume | switch |
| `28` | Conversation Awareness | switch |
| `2E` | Adaptive noise reduction | 0 to 100 |
| `34` | Off as a listening mode for the stem | switch |

PairFree shows a setting once the AirPods have reported it. Off, noise cancelling and
transparency go on the shared noise control; Adaptive is a switch beside it on models that have
it (`adaptive` in `devices.json`, from LibrePods' and MagicPodsCore's lists) or that report being
in it, and switching it off goes back to the mode before.

## 5. Models

AirPods name their model by its A number in the information packet (LibrePods' `AirPodsModels`).
The Beats and AirPods Max in `devices.json` have no A number in either project: PairFree shows
them by the name they advertise, with the settings they report. Their product ids, from
MagicPodsCore, are under Apple's Bluetooth vendor id; Android does not show them to apps.

## 6. Never sent

`never_send` in `commands.json`: opcodes `10` (smart routing, which takes the AirPods over from
other devices), `14` (sends a device's address), `17` (head tracking), `1A` (renaming) and `30`
(the AirPods' keys); control commands `06` (owning the connection), `20` and `36` (automatic
connection) and `3A` (temporary pairing).

## 7. Open questions

- Every Beats model: which settings it reports, and whether it opens the channel at all.
- Whether the feature flags packet differs by model (LibrePods sends `D7` to all; MagicPodsCore
  sends `0E` to Adaptive models only).
- Which phones besides LibrePods' list open the channel before Android 17.
