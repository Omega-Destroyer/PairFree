# Jabra virtual testing

Research date: **2026-11-18**. This is **PairFree itself** (a debug build of `brand/jabra`) run on an isolated Android 37 emulator against two virtual Bluetooth
earbuds written for the purpose (Google Bumble, Apache-2.0). It is a separate evidence category from the research in [PROTOCOL.md](PROTOCOL.md), from
the vendor app's own behaviour and from real earbuds: **no Jabra product and no Jabra app took part**. The peers answer as the research tables say a device
answers (a read gets the value the setting's layout holds; a write is echoed and kept). Their replies are invented, so an outgoing frame shows what PairFree
sends, never that firmware accepts it. Nothing here counts a model as tried.

## What was run

| Peer | Model, firmware | Session | Built from |
|---|---|---|---|
| Jabra Elite 8 Active | product id `2E47`, snapshot `11847_01-91_1.2.4`, GNP over RFCOMM SPP | `JabraSession` | `data/jabra/settings.json` and `devices.json` for that snapshot (every setting of its form answers with its first option) plus the hand-set battery, noise control, music equalizer (five bands, 12 dB) and loop |
| Jabra Elite 5 | product id `24F2`, GAIA v3 (`FF 03 00` frames, vendor `0067` and Qualcomm's `001D`) | `JabraGaiaSession` | the session's own feature list and the shape its code expects (battery, sound modes, HearThrough, sidetone, in-ear detection, button, leveled ANC, five-band equalizer) |

The peers' scripts are outside the repo (`E:\Builds\research\jabra-virtual-2026-11-18`).

## What it showed

**GNP (Elite 8 Active).** PairFree found the model by its Bluetooth name, opened the SPP channel and sent the opening reads (`02/11`, `02/03`, `12/02`, then the
equalizer and each setting of the snapshot, 43 reads in about 7 s) in the order `JabraSession.configure` builds. Home showed the battery (left 82, case 95, right
77: the extra unit at address 4 made the local level the left's, as the code infers), the noise mode, both level rows, the music mix and the button's loop; Sound
the five-band equalizer; Controls the 18 button rows; Device the timer, voice prompts, sidetone and the sound settings. Writes seen at the peer:

| Action | Frame sent (hex, destination 04 or 08, source 09) | Read back |
|---|---|---|
| Transparency / Off / Cancelling | `13/BE` selector `01` value `02` / `01` / `04` | `13/BE 01` |
| HearThrough level 2 (of 4 steps) | `13/BE 02 03` | `13/BE 02` |
| Noise cancelling level 5 | `13/BE 03 00` | `13/BE 03` |
| Hear music with HearThrough | `13/BE 04` value `01` / `00` | `13/BE 04` |
| Bass boost preset | `13/7E 01` (equalizer on), then `13/7D` with 5 bands, 60 Hz at +7.2 dB | none; the curve shows |
| Music, left earbud, tap: Play / pause | `13/9A 00 00 00 01` | `13/9A 00 00 00` |

**GAIA (Elite 5).** PairFree sent `FF 03 00 00 00 67 02 01` (the features), registered the notifications (`04/01` with each feature), read each feature once and
the music equalizer's bands (`001D` feature 5 command 5), then polled the battery every 30 s. Home showed the battery (80 / 90 / 75), the noise mode, HearThrough's
level and mix, leveled noise cancelling (0 to 4) and the loop. Writes: mode `14/05` (code 2 HearThrough, 0 off, 1 ANC), noise cancelling level `18/03 04`, the
music mix `08/06 01`, and Bass boost as `001D 0A 06 00 04` followed by five signed gains in 1/60 dB.

## Found by it, and fixed

- The seal-check note on the Device tab named JBL's app ("as JBL's app does") on a Jabra model. The shared string no longer names a vendor.
- The Home tab showed two cards titled "Noise control" (the shared one and Jabra's levels and loop). Jabra's is now "Noise levels and modes".

## Found by it, left as they are

- GAIA models show "Firmware: Jabra Elite 5" on the Device tab: no GAIA firmware read has been found, and the session puts the model name where the GNP session puts
  `model_firmware`. Cosmetic.
- GAIA's noise cancelling level counts 0 to 4 (the plugin's range); the GNP models count 1 to 5. The direction of the GAIA level is one of the inferences the
  protocol doc lists, so the labels are the wire values.
- The Elite 8 Active's form lists a plain **HearThrough** switch (`13/76`) next to its noise control; PairFree shows it because the vendor form lists it, and cannot say
  whether Sound+ hides it on models with ambience modes.

## Sound+ itself against the peer (2026-11-18, stopped at the handshake)

Sound+ 5.22.0 (all four splits, unmodified) was installed on the same emulator and given the Elite 8 Active peer, offline. It found the paired peer, opened the SPP
channel and sent its opening reads, which is the first look at what the vendor app does on connecting. In order (destination 4, source 9, all reads unless noted):

`02/08` available languages, `02/11` product id, `02/03` firmware version, `02/02` type, `22/00` SYSMON supported-get, `22/05` SYSMON supported-subscribe, then `02/11`
and `02/03` again, `22/01 00` SYSMON get, `02/00` name, `02/01` id, `02/03`, `0D/4C` DEVICE events (read), and a **write** of `0D/4C` with a four-byte mask: with the peer
answering `FFFFFFFF` the app wrote `00000003`, with `00000000` it wrote `00000000`, so it writes the supported events it wants (bits 0 and 1) back.

PairFree's own opening is shorter (`02/11`, `02/03`, `12/02`, then the settings) and never asks for the type, the name, the SYSMON group (a never-send family in
PairFree) or the event mask. Whether the earbuds push changes only after that mask is written is not known; the profile snapshots list a `settingschangenotification`
capability, which would fit. No frame beyond that was seen: after the mask write Sound+ stayed on its "Select your device" list and sent no battery or setting read, so
the exact reads of its Home and settings screens were **not captured**. A second attempt answered the name read with the product's name and pushed the events a device would send after the mask write (audio ready, a configuration change and
a battery status, all invented); Sound+ then left the picker for its connection guide ("Checking for connection: the app is checking if the headset is connected to your
phone") and sent nothing more in two minutes. Its Android-side check probably wants a headset profile connection (the peer offers only an audio sink), which was not
built; that is a guess, not something read from the app. What the peer answered was invented (`22/00` and the zero-filled replies built from the schema
for `22/05`, `22/01`, `02/01`), and the app may be waiting for an answer it does not accept. Not a result about any firmware.

## About the peers

Two Bumble peers at once on one emulator, or a peer that answers a connection too quickly, left the phone's Bluetooth stuck after the link came up (no audio or
RFCOMM connection, so PairFree showed "Connecting"); one peer with debug logging switched on connected every time. This is the research setup, not PairFree.

Not covered: any older GNP layout (the 65t/65e `13/76` and `13/87` noise control), speakerphones and headsets, the voice language setting, spatial sound,
Spotify tap, and everything the doc lists as not built.
