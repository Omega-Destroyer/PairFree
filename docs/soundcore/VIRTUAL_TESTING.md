# Soundcore virtual testing

Research date: **2026-11-07**. **No physical Soundcore device was tested in this audit.**
Existing hardware recordings retain their separate provenance.

## Life Q30 / A3028, original app 2.9.9

The unmodified original `com.oceanwing.soundcore` **2.9.9** runs offline on the isolated
Android 37 Google APIs userdebug guest with ARM64 translation. Back from the account screen
reaches the guest device list. The normal tutorial Skip and custom-profile dialog Cancel
options allow device setup and local EQ use without an account. No Frida/navigation/encoder
hooks were used. The research accessibility helper performs ordinary UI/Android Bluetooth actions.

The peer advertises standard serial RFCOMM and SBC audio, using the name Soundcore Life Q30.
Android pairs and connects A2DP; the app recognizes it by name, displays Device Found, opens
RFCOMM and requests state with `08 EE 00 00 00 01 01 0A 00 02`.

The **synthetic** 60-byte state body follows the original `A3951DeviceManager.W0` Q30 parser:
battery 5/5, not charging, Signature EQ and flat band values, no HearID, normal ambient mode,
made-up firmware `01.00`, and a 16-character all-zero placeholder identifier. This is a fixture,
not a capture from real Q30 firmware. It reaches the Life Q30 dashboard. Unknown queries,
including `0501`, are left unanswered rather than receiving invented success responses.

### Retained UI settings

[emulator_vectors.json](../../data/soundcore/emulator_vectors.json) holds **36 distinct
setting frames**, original APK hash and local profile recall actions. All full-frame lengths
and additive checksums were checked independently.

- **All 22 factory presets**, IDs `0000` through `1500` in little-endian order, use command
  `0281` with a two-byte preset ID and eight band bytes. Every captured ID and curve matches
  the existing OpenSCQ30-derived common table in
  [eq_presets.json](../../data/soundcore/eq_presets.json). The initially selected Signature
  produced no new write; explicitly reselecting it after another preset captured its frame.
- **Custom EQ** uses ID `FE FE` (65278), followed by all eight values in the same frame.
  Editing each band produced cumulative independent writes. Alternating +6/-6 dB at
  100/200/400/800/1600/3200/6400/12800 Hz gives
  `FE FE B4 3C B4 3C B4 3C B4 3C`: gain in tenths of dB plus 120.
  This tests the official UI's +/-6 dB range, not the entire possible byte range.
- Creating a new local profile starts flat. Selecting the earlier Custom profile sends
  its saved alternating curve; selecting the new profile sends flat; selecting Custom
  again retransmits its prior curve. This verifies **phone-side** profile storage/recall.
- **Ambient control**, command `0681`, carries four bytes. Captured ANC Transport is
  `00 00 00 00`, Outdoor `00 01 00 00`, Indoor `00 02 00 00`; transparency after Transport
  is `01 00 00 00`, normal is `02 00 00 00`. These preserve this fixture's remaining
  settings and do not establish every possible four-byte combination.

The peer returns a minimal positive ACK for `0281` and `0681` using the original parser's
success flag at frame byte 4. The first Acoustic selection was deliberately unanswered
before those ACKs were configured; it showed a waiting UI. Ordinary state remains static.
No write is claimed to have changed real firmware, and device readback/persistence has
not been verified. No PairFree comparison or adverse-reply/reconnection matrix is complete.

The More screen under this fixture displays firmware information and Quick Start Guide;
it offers no timer, gestures or button remapping. The firmware row was not entered.
Superior Sleep/audio playback was not tested. Other Soundcore models, layout families,
firmware and capability combinations inherit no result from Q30.

## Current app 6.4.0 and guest compatibility

The original packed **6.4.0** APK installs on both Android 37 research guests but terminates
before showing a screen. On the Google Play guest, `ApplicationExitInfo` records a signal-9
termination with no Java crash trace. The native-library inventory includes both ARM64 and
ARM32 libraries, the ijiami loader/emulator components and separate application libraries.
Tracing the startup termination/unpacking remains research work, not a protocol blocker.

The older 2.9.9 app launches on both guests. On the Google Play guest it recognizes the
paired Q30 name, but the synthetic A2DP connection remains Connecting and no serial protocol
is captured. On the Google APIs userdebug guest the same peer completes A2DP and the settings
exchange above. This environment difference must not be attributed to real Q30 behavior.

Original APKs, profiles, raw traffic, logs, UI captures and scripts remain outside the repo:
`E:\Builds\research\existing-vendor-virtual-2026-11-07`. Neither original APK was modified
or re-signed. No account service, firmware, factory reset or power-off operation was used.
