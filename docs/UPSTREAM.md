# Where PairFree differs from other open-source projects

PairFree works each brand's protocol out from the brand's own app first, and from recordings of that app
talking to real earbuds (CLAUDE.md, Sources). Open-source projects for the same brands are a check and fill
gaps. Where the vendor's app or a recording shows something an open-source project does differently, it is
listed here, with the evidence, so it can be reported to that project as an issue or a pull request.

Each entry says what PairFree found, where the evidence is, and whether it has been reported yet. A project
whose code PairFree may only use for facts (an AGPL or unlicensed project) can still get an issue.

Add an entry whenever a brand's work finds such a difference, in the same change that records it in the
brand's protocol doc. When it is reported, add the link; when the project fixes it, say so and leave the entry.

## OpenSCQ30 (Soundcore)

[github.com/Oppzippy/OpenSCQ30](https://github.com/Oppzippy/OpenSCQ30), GPL-3.0-or-later.

| What | PairFree's evidence | Reported |
|---|---|---|
| Space A40 (A3936): turning one side's single press off. OpenSCQ30 turns both sides' single, double and long presses off together; the Soundcore app sends `04 83` for the chosen side only, and the next state still has the other side on. | Owner's recording, `data/soundcore/reports/A3936_06.43.txt`; `docs/soundcore/PROTOCOL.md` section 6 | No |
| Space A40 EQ write: the Soundcore app sends gender `FF` where OpenSCQ30 sends `00`, and an empty HearID curve as `FF` bytes ending in `00` where OpenSCQ30 sends all `FF`. | Same recording; `docs/soundcore/PROTOCOL.md` section 8 | No |
| Q20i (A3004) EQ presets: the Soundcore app uses the A3005's preset set (Bass Booster as preset `7E 7E`, Rock ending +4, +5 dB), not the common set OpenSCQ30 gives the A3004. | User's recording, GitHub issue #11, `data/soundcore/reports/A3004_05.08.txt`; `docs/soundcore/PROTOCOL.md` section 12 | No |
| Q20i (A3004) noise mode: the Soundcore app writes `06 81` with six bytes, the four sound mode bytes then `01 FC`; OpenSCQ30 writes the four. The state holds the two extra bytes after the sound modes. What they mean is not known. | Same recording | No |
| Sleep A20 (A6611): OpenSCQ30's library does not support it. A user's recording confirms its EQ (bytes 30-51), touch controls (53-56) and sleep timer (67-70, `15 85` writes, `15 03` reads). | User's recording, GitHub issue #8, `data/soundcore/reports/A6611_03.22.txt`; `docs/soundcore/PROTOCOL.md` section 11 | No |
| Models the Soundcore app 2.9.9 reads without a feature OpenSCQ30 lists for them (`soundcore_app.lacks` in `data/soundcore/devices.json`). | Soundcore app 2.9.9's parsers; `docs/soundcore/PROTOCOL.md` section 10 | No |

## GalaxyBudsClient (Samsung)

[github.com/timschneeb/GalaxyBudsClient](https://github.com/timschneeb/GalaxyBudsClient), GPL-3.0.

| What | PairFree's evidence | Reported |
|---|---|---|
| Finding a renamed pair by the model id in its service list: GalaxyBudsClient has the lookup, but its parsing reads the UUID's prefix instead of the id, so it never matches. | Samsung's Galaxy Buds plugin 9.0.10 (`BluetoothUuidUtil.getDeviceIdFromUUID`); `docs/samsung/PROTOCOL.md` section 4 | No |
| Buds4 family state bytes 40 to 50: GalaxyBudsClient reads a hearing test value at 40, no auto adjust sound, and lighting at 50, so charging, extra clear calls and extra high ambient land on the wrong bytes. | Samsung's plugin; `docs/samsung/PROTOCOL.md` section 12 | No |
| Buds4 family writes that differ: 121 (touch and hold's modes), 130 (ambient per earbud, no tone), 144 (touch lock, 10 bytes), 146 (touch and hold codes and each side's assistant), 175 (seamless connection, 1 = on), and the custom curve as equalizer 6 before 134 and 137. | Samsung's plugin; `docs/samsung/PROTOCOL.md` section 12 | No |
| Models GalaxyBudsClient lacks: Buds On (SM-U600), H1 (SM-U700), T1 (SM-R545), with their model ids. | Samsung's plugin; `samsung_app` in `data/samsung/devices.json` | No |

## Gadgetbridge (Sony)

[codeberg.org/Freeyourgadget/Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge), AGPL-3.0.

| What | PairFree's evidence | Reported |
|---|---|---|
| Table set 2's noise control reply `17`: 7 bytes in Sony's app; Gadgetbridge reads 8 from the WF-1000XM4, laid out like `15`. Possibly both are right for different firmware; PairFree goes by the length. | Sony / Sound Connect 13.3.0; `docs/sony/PROTOCOL.md` section 5 | No |

## Gadgetbridge (EarFun)

[codeberg.org/Freeyourgadget/Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge), AGPL-3.0 (facts only). Read at commit 562ae7700189.

| What | PairFree's evidence | Reported |
|---|---|---|
| EQ master gain: EarFun's app writes `FE 98` (-6 dB) on the Qualcomm models (`Qualcomm10Equailzer`, `Qualcomm6Equailzer`); Gadgetbridge writes `FF 98` (-1.7 dB). | EarFun Audio 20.1.61; `docs/earfun/PROTOCOL.md` section 6 | No |
| EQ gain steps: EarFun's app maps its 21 slider positions on the 10-band models to -6, -5, -4, -3.5 .. 3.5, 4, 5, 6 dB (half-dB steps near 0), and on the 6-band models (Air S) sends the position itself in dB, up to 10 dB; Gadgetbridge sends 0.6 dB a step on both. | Same; `gainQcc` and `Qualcomm6Equailzer.getSendGain` | No |
| The Air Pro 4+ and 5 (not in Gadgetbridge) end each EQ band with `0D` (`TW700Equailzer`). | Same | No |

## bosectl (Bose)

[github.com/aaronsb/bosectl](https://github.com/aaronsb/bosectl), MIT.

| What | PairFree's evidence | Reported |
|---|---|---|
| Multipoint (1.10): bit 0 is on, bit 1 supported, bit 2 can be turned off. bosectl reads bit 1 as on. | Bose's app 13.3.4; `docs/bose/PROTOCOL.md` section 6 | No |
| The button's action masks (1.9): Bose's app reads bit 0 in the last byte; bosectl reads them the other way round. | Bose's app 13.3.4; `docs/bose/PROTOCOL.md` section 6 | No |

## nada and nadamas (Nothing and CMF)

[github.com/chukfinley/nada](https://github.com/chukfinley/nada), [github.com/ezvk/nadamas](https://github.com/ezvk/nadamas), MIT.

| What | PairFree's evidence | Reported |
|---|---|---|
| EQ preset numbers (`C01F`, `F010`): Nothing X uses 0 balanced, 1 voice, 2 more treble, 3 more bass, 4 Dirac, 5 custom, consistently in three places; nada and nadamas number them in screen order (1 more bass, 3 voice). Not settled on hardware yet. | Nothing X 3.4.15; `docs/nothing/PROTOCOL.md` section 5 | No |

## Earshot (Skullcandy / Bragi, research only)

[github.com/HellzYeahh/earshot](https://github.com/HellzYeahh/earshot), MIT. Read at revision
`a581f96efb5a91b0622a5be9600a02a72b4e1649` on 2026-11-06. Its Crusher 1080 ANC implementation is
explicitly untested on hardware; this finding is also static vendor-app evidence, not a capture.

| What | PairFree's evidence | Reported |
|---|---|---|
| BOS-RPC STATUS success: Earshot's BragiConnection accepts an absent or empty status body as success, and VirtualCrusher emits an empty STATUS. Skullcandy's SDK only recognizes a STATUS success when body byte 0 exists and equals zero; a missing byte follows its error path. Use the explicit success byte when building future PairFree support, pending contrary hardware evidence. | Skullcandy 5.0.6, `com/bragi/sdk/android/transport/rpc/l.invoke` (STATUS handling and getOrNull at payload offset 4); reference `app/src/main/kotlin/dev/earshot/bt/BragiConnection.kt` around 231-235 and `virtual/VirtualCrusher.kt` around 186-188; `docs/skullcandy/PROTOCOL.md` section 4 | No |
