# Sleep A30 custom audio: feasibility assessment

Research date: **2026-11-09**. Target: **Soundcore Sleep A30, D1301**. This is a research
assessment; it adds no upload implementation and reports no successful upload to real earbuds.

For an owner's next capture, use the [step-by-step A30 Bluetooth recording guide](SLEEP_A30_CAPTURE_GUIDE.md).
The recorder now exports safe transfer diagnostics through PairFree's normal public text report;
raw logs and full Android bug reports are not requested. See the recording scope in section 13
of [PROTOCOL.md](PROTOCOL.md).

## Decision

The requested **phone-side library is feasible**: import an audio file once, retain an app-owned
copy, and offer it for later uploads without reopening the phone's file picker. The library can
work entirely offline and does not depend on Soundcore's account or catalog.

**Custom on-earbud audio is promising, but not yet demonstrated for the A30.** The hardware
already receives audio for its Local mode. Its app also includes an independently reproducible
SBC container writer. However, the inspected writer has a Sleep A20-family magic value; its use
by the A30 has not been established. The A30 transfer commands, accepted encoding, storage limit,
completion checks and firmware acceptance of user-produced audio remain unresolved. Do not ship
an uploader using A20 assumptions or promise a maximum clip duration from this assessment.

## What the official product supports

- Soundcore's [A30 download instructions](https://service.soundcore.com/article-description/How-do-I-download-white-noise-to-my-Sleep-A30)
  say only one white-noise track can be stored in the earbuds and third-party audio import is
  unsupported by the official app. This is a product limitation of that app; it does not by itself
  establish a firmware prohibition on a differently prepared audio file.
- The [D1301 user guide](https://support.anker.com/s/articlemanual?pn=D1301) describes transferring
  sleep soundscapes and white noise for playback without the phone in Local mode.
- App 6.4.0's `d1301_builtinaudio_dynamicsnoringmasking4` string describes **one local file**,
  replaced by a soundscape or a custom white-noise mix. Its transfer confirmation says existing
  playback is interrupted and gives approximately 40 seconds as the transfer estimate. That is
  **transfer time, not the duration of the stored audio**.
- Its `d1301_builtinaudio_dynamicsnoringmasking2` string limits Dynamic Snore Masking to a
  snore-masking soundscape. Arbitrary imported audio must not be advertised as supporting that
  behavior. Ordinary local playback and adaptive masking are separate questions.

The A20's four-track library must not be copied to the A30. Likewise, SBC/AAC listed for Bluetooth
streaming does not establish the codec or container accepted by the local-file transfer protocol.
Long overnight playback is not evidence that an hours-long recording fits in the earbuds.

## App and native-code investigation

The supplied reference set has Soundcore **6.4.0** and **2.9.9**. The complete 6.4.0 APK contains
both ARM64 and ARMv7 libraries; this was not an analysis of the base Java code alone. Its Java
application code is packed by ijiami. The readable 2.9.9 app predates the A30 and supplies no
verified A30 upload path. APK assets and resources were inspected without reading secret assets
or server credentials. `resource.zip` contains audio-generation resources; its presence does not
establish an A30 transfer format.

Relevant 6.4.0 native entry points include:

| Library | Entry point | Finding |
|---|---|---|
| `libscnativelib.so` | `Java_com_anker_sbc_SbcToBinUtil_sbcToBin` | JNI wrapper forwarding paths and metadata to the container writer |
| `libscnativelib.so` | `transfer_sbc_t_bin`, address `0x4A318` | Reads an input file, creates a metadata/checksum header, writes header plus the input bytes |
| `libscnativelib.so` | `do_crc`, address `0x4A10C` | CRC-16 with polynomial `0x1021`, initial value zero |
| `libscnativelib.so` | SBC decoder entry points | SBC decoding exists in this app; does not identify the A30's transfer codec |
| `libopus-lib.so` | `OpusUtils.createEncoder` / `encode` | Opus encoding exists too; its A30 involvement is unverified |

The container routine and CRC were disassembled and then executed under ARM64 emulation, outside
the repository. Only file I/O, allocation and C memory functions were stubbed; the vendor's
container and CRC instructions were executed. An independent reconstruction matched the native
output **byte for byte in 40 synthetic cases**, varying payload length, identifier, name and two
metadata arguments. These are container tests, **not valid audio, Bluetooth or firmware tests**.

For the tested header-size argument of 128, the output was a 128-byte header followed by the
unchanged input bytes. The header contains numeric magic `0x36363131` (little-endian bytes
`31 31 36 36`), a 64-bit identifier, a type byte, a 64-byte name, input length, data offset,
additional metadata and separate payload/header CRCs. The purpose of every metadata argument is
not yet known. In particular, no unidentified integer has been labeled as a duration.

This wrapper accepted arbitrary synthetic byte payloads and performed no signature or encryption
step in the executed routine. That establishes the wrapper's behavior only. The packed caller
or earbud firmware could impose further format, identity or content restrictions. Its A20-family
magic is a reason to trace the A30 caller before using it, rather than evidence of compatibility.

The writer's 32-bit input-length guard is a container implementation detail, not an A30 capacity
or a usable maximum recording length. No device storage or duration limit was established.

## Runtime, other sources and remaining research

- The original 6.4.0 app was tried offline on isolated Android 37 and Android 30 guests. Its
  packing/runtime layer terminated before reaching A30 UI. Research-only attempts to inspect
  loaded DEX and select the ARM loader did not recover the A30 transfer classes. There was no
  A30 virtual command capture in this assessment.
- The local OpenSCQ30 checkout describes D1301 settings and state but contained no sleep-audio
  uploader in the inspected source. PairFree likewise has no audio-file transfer state machine
  or saved-audio library. Its existing D1301 settings do not establish file-upload support.
- Public Soundcore help supplies the product behavior above, but not a transfer specification.
  No unauthenticated audio-catalog/transfer API endpoint was recovered from the packed caller,
  and no vendor API request or account authentication was attempted. Endpoint discovery and its
  authentication requirements remain research work; this is not a claim that server data is
  inaccessible or necessary for user-supplied audio.

The next protocol work should establish:

1. The A30 screen's actual preparation path: codec, sample rate, channels, bitrate/frame settings,
   metadata, loop boundaries and whether it uses the inspected container or another one.
2. Transfer startup, transport/channel, any storage/capacity response, chunk sizing and offsets,
   flow control, ACK/error handling, cancellation and final activation. Avoid commands merely
   because their numbers resemble another model's transfer or firmware commands.
3. A normal A30 transfer from the vendor app, then an independently prepared file accepted and
   played by a real A30. Exercise interruption and replacement behavior as well as success.
   A recording of an ordinary transfer from the requesting owner would help validate the code
   findings, particularly model/firmware differences; further app analysis can proceed first.
4. The genuine file-size or frame limit. Convert that to duration only after the actual encoder
   is known. Do not use the app's 40-second progress estimate as a clip limit.

These items are unresolved investigation, not a declaration that the feature is impossible.
No factory reset, reboot, power-off or firmware traffic was sent to earbuds.

## Proposed PairFree behavior once the upload protocol is established

Use the Android document picker for the initial import and copy the selected content into
PairFree's private storage. Keeping only a filename or document URI would not satisfy reliable
later re-upload if the original is moved or access is lost. A saved copy survives those changes;
clearing PairFree's data or uninstalling it removes that library unless the user has exported it.

Keep the original file and a small local index: stable ID, display name, content hash, source
duration, file size, imported date, trim/loop choices and last uploaded model. Converted output
can be cached with its exact target format and conversion version. Retain the original so it can
be converted again when a model's format changes. No Soundcore sound library or internet service
is needed for the user's own files.

The UI should let the user import, name, preview, trim, delete and re-upload saved entries. Show
the prepared clip's actual duration and its target-model compatibility. If a file is too long,
offer a deliberate trim rather than silently cutting it. A loop editor/crossfade is useful for
ambient audio if the established device format supports it; do not assume looping semantics.
The app library can have many entries while the **A30 holds one local file**. Selecting Upload
should make that replacement clear and show progress, completion or a recoverable failure.

Audio decoding/resampling/encoding belongs in Android-side processing; framing and transfer state
belong in the plain protocol module. The transfer needs exclusive command scheduling and a
connection held until completion/cancellation, including while the screen changes. Retries and
resume must follow observed device behavior. Preserve the saved original even when an upload
fails, and never report a successful upload from merely having sent the last chunk.

Suggested delivery order: establish A30 format and transfers; prove a short custom clip on real
hardware; integrate conversion plus a minimal saved library; then add trimming, loop refinement
and wider format support. The library architecture is straightforward; confirming A30 acceptance
and interruption behavior is the material uncertainty.

## Reproduction artifacts

All original APKs, native binaries, disassembly, emulator logs and research scripts remain outside
the repository under `E:\Builds\research\soundcore-a30-2026-11-09`.

- Original local 6.4.0 APK SHA-256: `b9107394df7b5d04b233f9d71c229a731db8cac45f524cf5edb207c0b6583ae2`.
- ARM64 `libscnativelib.so` SHA-256: `6f9791c927279072fa9b393d9dd384bb70deb1143d912042a0ccd8da08ae5c71`.
- `sbc_to_bin_disasm.txt`: container routine disassembly.
- `sbc_container_emu.py`: isolated native execution with synthetic inputs.
- `check_wrapper.py` and `native-wrapper-validation.json`: 40-case independent comparison.

No vendor binary/source or audio asset is added to PairFree by this assessment.
