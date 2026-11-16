# Jabra Sound+ protocol research

Research date: 2026-11-09. Source: `com.jabra.moments` **5.22.0.0.11632.985318146**, including all four APK splits, the ARM64 SDK and the APK's cached device configurations. This is research for later implementation; PairFree's runtime has not been changed.

## Evidence and coverage

- [MODELS.md](MODELS.md): all 55 device definition files; 49 production entries with 34 distinct display names. Product variants are separate entries. Six entries are nonproduction, including a developer board that reuses a real product's PID.
- The APK contains `res/raw/device_info.zip`: 46 configuration snapshots covering **all 42 production GNP PIDs**. Its 138 encrypted configuration/text files were decoded locally using the native app's data-decoding behavior. They yield 20 protocol schema versions, 1,755 distinct schema definitions for 1,080 main/subcommand byte pairs, and 315 distinct settings variants referenced 1,208 times by the profiles. These counts describe the SDK catalog, including unrelated enterprise/test/video commands, not 1,080 supported earbud controls.
- The seven GAIA models use device feature discovery and Jabra's vendor plugins. Their 54 declared vendor commands and value tables are indexed separately.
- The original native GNP packet encoder and selected setting functions were executed under ARM64 emulation with a stub transport. Music EQ writes were also executed using a synthetic descriptor. [test_vectors.json](../../data/jabra/research/test_vectors.json) records those inputs and outputs. This is not a whole-app virtual earbud test or evidence that hardware accepts a write.
- No Jabra hardware was connected. No model is marked hardware-tested or whole-app virtually tested.

APKs, native binaries, decompiled code, decrypted XML, images and research scripts remain outside Git under `E:\Builds\research\jabra-2026-11-09`; jadx output is under `E:\Builds\research\decompiled\jabra-5.22.0`. Hashes and source versions are in [sources.json](../../data/jabra/research/sources.json). No vendor code, key, encrypted cache or credentials are included here.

## Reading the research data

| File under `data/jabra/` | Meaning |
|---|---|
| `devices.json` | Own model schema: PID, protocol, app feature flags, firmware snapshot references, assistants, wear detection, spatial and LE Audio gates. |
| `firmware_profiles.json` | Exact PID/variant/firmware snapshots, device capabilities and setting references. A snapshot is historical evidence, not the latest firmware. |
| `gnp_settings.json` | Model-bound settings: address, GUID, command references, fixed selector positions, shared value positions, dependencies and option codes. |
| `gnp_catalogs.json` | Which schema revisions belong to each profile, plus the address table. |
| `gnp_command_only.json` | ACK, ECHO, POLL, deprecated NOOP and excluded DFU schema groups without declared subcommands; snapshot references retained. |
| `gnp_commands.json` | Own factual representation of wire fields, attributes, enums and numeric constraints. Payload positions follow the vendor schema; they are not absolute frame offsets. |
| `native_commands.json` | Selected command payloads observed at the original native functions' transport boundary. |
| `gaia_commands.json` | Jabra vendor feature/command/notification IDs and GAIA value enums. |
| `eq_presets.json` | Six official app preset percentage curves; these are not fixed dB curves. |
| `app_settings.json` | 62 public setting GUIDs, including 18 button/context GUIDs, and 76 SDK option constants. GUIDs are schema identifiers, not wire commands. |
| `gnp_properties.json` | Supplementary facts from GN Audio's public properties package; not a per-model support list or executable pipeline. |

[SETTINGS.md](SETTINGS.md) presents every cached model/firmware settings row in a readable table. Resolve a setting through its firmware profile, not through an unrelated device's schema. The app also filters settings using its UI flags and runtime responses. `form_enabled=false` is retained explicitly and must not be treated as an enabled UI control. `configuration_mode_required` is recorded per read/write layout; obey it rather than assuming every setting writes directly.

## GNP transport and framing

`sf0` (BluetoothComm) opens RFCOMM SPP UUID `00001101-0000-1000-8000-00805f9b34fb`: insecure socket first, secure fallback, then a reflection fallback to channel 3. The retry loop tries up to five times. The service's `c.D` queues one read/write request at a time and writes the packet unchanged to the stream.

The consumer Sound+ frame is:

| Offset | Field |
|---|---|
| 0 | Destination address |
| 1 | Source address |
| 2 | Sequence byte |
| 3 | `(attribute << 6) | total_frame_length` |
| 4 | Main command |
| 5 onward | Payload, normally beginning with a subcommand |

Attributes: event 0, read 1, write 2, reply 3. Total frame length includes the five-byte header and is at most 63; a normal subcommand leaves at most 57 further bytes. There is no checksum or byte escaping in this GNP serializer. Buffer the RFCOMM stream and split by byte 3's low six bits, allowing fragmented and coalesced reads. The Java service parser `po4.u` requires at least six bytes for its subcommand-based messages.

The native SDK initially constructs packets with destination 1/source 0, but the Sound+ bridge `com.jabra.sdk.impl.jni.e.F2` rewrites them to **destination 4/source 9**. Model form groups also name other destinations: 8 is common for DSP sidetone. Send each setting to the address its form names, not to address 4 just because it is a headset.

For example, a synthetic selected-mode read is `04 09 2A 47 13 BE 01`; an ANC selection with the same illustrative sequence is `04 09 2A 88 13 BE 01 04`. Real sequence/reply handling belongs to the queue. The older Java `po4` constructor starts a counter at 1 and increments it; the native encoder accepts a supplied byte. These examples were not captured from earbuds.

Main groups useful to consumer controls include IDENT `02`, DEVICE `0D`, STATUS `12`, CONFIG `13`, MMI `1A`, BODY_MONITOR `20`; ACK/NACK handling must follow the selected schema and service handlers. Other groups in the SDK catalog are not evidence of a model feature. Read product ID (`02/11`), type (`02/02`), firmware version (`02/03`) and the relevant variant before selecting a profile. The PID reply is low byte then high byte; TYPE includes a length and type group/identifier, and VERSION includes a length and ASCII string. Battery STATUS `12/02` includes flags, local level, ADC bytes and an extra-unit count followed by addressed records; use the selected schema and secondary-role information rather than assigning left/right/case by position. The cache uses decimal PIDs in directory names; do not confuse those names with the hex bytes on the wire.

A separate collaboration BLE class, `ro4`, serializes a different header ordering (main command first). It is not the consumer RFCOMM packet codec. Likewise, `c25fe59d-5578-4e5a-a5ce-b539a527000b` appears in an audio-streaming helper; it is not the default settings service.

Evidence: `po4`, `sf0`, `j3`, `wj4`, `com.gnnetcom.jabraservice.c`, JNI bridge `e`; native `GnProtocol::GnpPacket::Serialize` at `0x3e2224`, constructors at `0x3e20e8` onward. Virtual checks cover all four attributes, sequence/source/destination fields, the 63-byte boundary and rejection at 64 bytes.

## GNP noise controls

Newer devices use CONFIG `13/BE` with a selector byte:

| Selector | Meaning | Value encoding |
|---|---|---|
| `00` | Supported data | Capabilities, level/balance limits and extended-setting support. |
| `01` | Selected mode | Off `01`, HearThrough `02`, ANC `04`, calibration `80`. |
| `02` | HearThrough level | One byte; zero is maximum HearThrough. |
| `03` | ANC level | One byte; zero is maximum ANC. |
| `04` | HearThrough extended settings | Bit 0: music mix; bits 7..5: submode. |
| `05` | ANC extended settings | Bit 0: personalization completed; bits 7..5: submode. |
| `06` | ANC/calibration balance | Signed two's-complement byte. |
| `FF` | All current settings | Selected mode through balance, in schema order. |

A read contains `BE selector`; a write adds the required value(s). Read support and preserve unrelated extended-setting bits when changing one setting. The native helpers parse maximum HT/ANC levels from supported-data vector indices 2/3, balance from index 6, mix/personalization support from bit 0 of indices 4/5, and maximum submode from their upper three bits. These are internal vector indices including the selector, not raw frame offsets.

CONFIG `13/BF` handles the button's mode loop: the native setter emits a count followed by **SDK enum ordinals**: off 0, HearThrough 1, ANC 2. This differs from the selected-mode bit values above. Synthetic `[off, HearThrough, ANC]` produces `BF 03 00 01 02`. STATUS `12/13` reads ambience noise level.

Older models expose legacy ANC/HearThrough settings through their forms instead. A false `adjustable_ambience_mode` app flag does not mean a device lacks ANC: Elite 85h is an example. Use that model's settings, not the newer BE family by assumption.

## Music EQ and presets

GNP CONFIG `13/7D` reads parameters with selector `00`; CONFIG `13/7E` reads/writes the EQ enable byte. A full parameter reply body after subcommand `7D` contains:

- Band count, then signed 16-bit big-endian master gain, in 1/60 dB.
- For each band: maximum gain as a byte in quarter-dB units; frequency as unsigned 16-bit big-endian Hz times 3; signed 16-bit big-endian gain in 1/60 dB; unsigned 16-bit big-endian Q times 4096.

The full write payload is `7D count parameter master_gain [frequency gain Q]...`; parameter 0 selects all bands. The reply's maximum-gain byte is not copied into each write record. The app's native `SetEqualizerParameters` first reads the descriptor, validates count and each absolute gain against its reported maximum, preserves master gain, frequencies and Q, and replaces the band gains. Conversion uses float multiplication by 60 and truncation toward zero, not rounding to the nearest integer. An out-of-range synthetic gain produced no transport call.

The six app presets are five slider percentages:

| Preset | Percentages, low to high |
|---|---|
| Jabra default | `0, 0, 0, 0, 0` |
| Voice | `-60, 0, 0, 17, -20` |
| Bass boost | `60, -33, 0, 0, 0` |
| Treble boost | `-20, -33, 0, 33, 40` |
| Smooth | `0, 33, 0, -33, 0` |
| Energize | `40, 0, -33, -17, 30` |

The app converts each percentage to `reported_band_maximum_dB * percentage / 100`. Do not hardcode frequencies or assume one common maximum for every model. Its custom preset library is stored on the phone; selecting a preset applies gains rather than a universal preset number.

Evidence: `ProvidedEqualizerPreset`, `JabraDeviceEqualizerHandler`, `EqCalculator`, SDK JNI/EqualizerBand; native constructor constants at `0x39b7f4..0x39b82c`, `GetEqualizerData` at `0x3ae8d4`, `SetEqualizerParameters` at `0x3aedec`. The original setter's writes are checked with synthetic descriptor frequencies/limits, not real model measurements.

## Buttons and gestures

GNP CONFIG `13/9A` uses records `[context, button, gesture, action]`. Contexts: music/idle 0, incoming call 1, ongoing call 2. Buttons: left 0, right 1. The app's bundled forms specify tap 0, double 1, triple 2. A read with only a context requests all settings in that context; a read with context/button/gesture requests one. Writes can contain one or more four-byte records. Defaults use `13/AD` in the native SDK.

Action codes must come from the matching form. Typical GNP codes are none 0, play/pause 1, next 2, previous 3, assistant 4, sound modes 5, reject 6, accept/end 7, accept/hold 8, end/resume 9, hold/resume 10, mute 11, sidetone 12, reserved assistant 13, Spotify 14. Do not substitute the GAIA action enum: its call-action values differ. Do not overwrite an assistant-reserved assignment without accounting for assistant state. Context restrictions are in each profile's option list.

The public newer SDK describes hold/tap-and-hold and additional actions for some devices. That is supplementary capability evidence, not permission to expose those gestures on all Sound+ models. MyControls availability is separate from whether the app shows its onboarding.

## Other GNP controls and functions

The settings reference includes per-model sleep timers, voice prompts and language, sidetone and levels, wear detection, auto pause, automatic answering/muting, call audio, busy lights, vibration, call quality, rename, assistant and button choices, connection modes, wind reduction and automatic volume adjustment where present. A few pitfalls:

- An option's numeric key is not a universal boolean. Sidetone's app setting uses On=0/Off=1; other settings use On=1. The Elite 10 mute-reminder form uses On=`14` (hex 20), not 1.
- Inactivity interval `13/90` is in five-minute units. Elite 10's offered `03/06/0C/18/00` therefore means 15/30/60/120 minutes/off.
- Shared DSP sidetone fields target address 8 and different value positions in one command. Read/modify/write the full payload; changing the level must preserve enable state. Where a command layout requires configuration mode, enter with DEVICE `0D/11` and leave with `0D/12` according to the SDK sequence; these are settings-mode operations, not firmware operations.
- Spotify CONFIG `13/E5` uses a byte; spatial CONFIG `13/E4` carries call/media/Dolby fields. Preserve the other spatial settings when changing one. Media, communication, head tracking and HRTF toggles have separate model gates.
- MySound hearing personalization is distinct from music EQ. CONFIG `13/9C` describes its separate little-endian band data and chunking; the native SDK has dedicated support/range checks. Do not apply the ordinary big-endian five-band codec to it.
- Soundscapes, saved presets, Moments switching, location history and prompts also have phone-side behavior. The presence of app audio assets does not establish an earbud audio-file upload facility.
- Language selection must distinguish already-available language choices from downloading/changing firmware language packs. Firmware traffic remains excluded.
- LE Audio changes are gated by model and firmware. Elite 10/Elite 8 definitions include `firstLEAudioFirmware`; these fields do not establish a safe standalone codec switch.

Inspect each profile and the dedicated handlers before writing a control. Some forms include metadata rows (firmware, SKU), UI-only panels and SDK virtual command names ending `_SPECIAL_CMD_...`. Those virtual ambience/loop names have been linked to their underlying command schema but require the dedicated adapter, not a direct generic field write.

## GAIA devices

The APK selects GAIA for Elite 2 (9441), Elite 3 (9442), Elite 4 Active (9446), Elite 5 (9458), Elite 4 (11848), Elite 3 Active (9493) and Connect 5t (9501). Their settings travel through Qualcomm's transport/plugin layer, rather than the GNP settings service. The RFCOMM connection request supports SPP and the legacy GAIA service `00001107-d102-11e1-9b23-00025b00a5a5`; do not assume one UUID for every negotiated path.

The outer RFCOMM frame starts `FF version flags length`, then big-endian vendor and packed command (two bytes each), then the payload. Length excludes those four vendor/command bytes. A checksum flag adds an XOR byte; the app's sender normally disables it. Version 4 can use flag 2 and a two-byte length for extended payloads; versions 1..3 cap ordinary payloads at 254. Match the negotiated protocol rather than forcing version 3 from connection start.

For GAIA v3, the packed command is `(feature << 9) | (type << 7) | operation`; types are command 0, notification 1, response 2, error 3. Jabra vendor ID is **103 / 0067**. Discover feature 1/operation 1 before constructing feature plugins; register/unregister notifications through feature 2 operations 1/2 with the target feature byte. Do not infer support solely from a model name.

`gaia_commands.json` contains every Jabra plugin's declared command and notification enum. The feature groups are sidetone 3, HearThrough 4, battery 5, secondary status 6, maximum gains 7, voice language 8, button control 9, sound modes 10, graphic-EQ support 11, leveled ANC 12, MyControls 13, assistant 14 and in-ear detection 16, in addition to discovery/notification registration.

Important payloads:

- Sound mode feature 10: get/set operations 4/5, mode values **off 0, ANC 1, HearThrough 2**. Loop get/set 6/7 uses one to three distinct mode bytes without the GNP count prefix. FF ANC get/set 2/3 uses gain and left/right balance bytes; max values operation 1. Onboarding get/set 8/9 uses 1=completed, 2=not completed.
- HearThrough feature 4: state get/set 1/2, level get/set 3/4, mix get/set 5/6, maximum level 7. State and mix enums use 0=unsupported, 1=on/mix, 2=off/no-mix. Off is 2; 0 only reports that the feature is missing.
- Sidetone feature 3: state get/set 1/2, level set/get 3/4, maximum level 5. Its state is likewise 0=unsupported, 1=enabled, 2=disabled.
- Battery feature 5/operation 1 accepts two or three bytes; the optional third case level is valid only from 0 through 100. Secondary-role state is separate; do not equate primary with right without reading that role.
- Button-control feature 9: get/set 1/2, values assistant 0, disabled 1, Spotify one touch 2. This is separate from complete gesture remapping.
- MyControls feature 13: get current 1, set current 2, defaults 3, supported actions 4. A table is exactly seven bytes: `[context, single_left, single_right, double_left, double_right, triple_left, triple_right]`. Contexts are **incoming 0, active call 1, media 2**, unlike GNP. Supported actions are queried by context. The SDK parser constructs a range from the returned first/last IDs; retain the raw reply if validating that behavior.
- In-ear feature 16: current state get/set 1/2, auto-answer 3/4, auto-mute 5/6, auto-pause 7/8, mute reminder 9/10, current in-ear status 11. Most switches use 0=unsupported, 1=enabled, 2=disabled.

### GAIA EQ

Jabra feature 7 supplies maximum gains. Its set/get operations are 1/2, with start-band/end-band bytes followed by gains. The app multiplies these limits by slider percentages, just as on GNP, but sends gains through the **Qualcomm v3 music-processing plugin**, vendor **29 / 001D**, feature 5. `r9d` reads a band range with operation 5 and writes gains with operation 6: `[start, end, signed_gain16be...]`, where gain is dB times 60 truncated toward zero. Band configuration replies contain seven-byte records: frequency u16be, Q u16be /4096, filter-type byte, gain s16be /60. This frequency is direct Hz, unlike GNP's times-three representation.

The plugin also parses operations 0=EQ state, 1=available preset list, 2=selected preset, 4=band count. Do not confuse the Qualcomm preset IDs with Sound+'s six app-defined percentage curves. The graphics-EQ Jabra feature only reports support; it is not itself the gain-write command.

Evidence: Jabra `V3*Plugin` classes and data enums; `JabraFeatureFetcher`; `MaxGains`; `r9d`, `g80`, `w9d`, `hi9`, `k9d`, `u9d`, `wi4`, `t9d`; outer transport `zi4`, `d3a`, `bb4`, `a54`, and endian helper `eq0`.

### Vendor behavior to verify rather than copy blindly

`V3InEarDetectionPlugin.setMuteReminderState` calls the declared `SET_AUTO_MUTE_STATE` operation 6 although a distinct mute-reminder operation 10 is declared. Treat this as an apparent app bug until an original bytecode trace or device recording resolves it. Do not deliberately toggle auto-mute when asked to change mute reminders.

Several response length checks are also loose: Qualcomm band parsing checks `(end-start)*7+2` before iterating inclusively, and the max-gain parser's count rule assumes the expected start band. A new implementation should validate the full record count before indexing, while retaining protocol values rather than silently changing them.

## Cloud and source checks

The native CloudManager uses `https://devicecapabilities.jabra.com/v4/deviceconfiguration` and constructs manifest-version paths with vendor `B0E`, product/type, variant and firmware. Its request builder adds `ClientId` and `Accept` headers. A credential-free request using a synthetic variant returned HTTP 404; that result does not establish authentication or model availability. No client identifier, app server credential or authenticated request was used. A second credential-free request using the actual bundled Elite 10 variant/firmware (`2E46/01-92/1.2.4`) also returned 404. The bundled cache supplied historical settings for these 42 GNP PIDs. Native `DecryptCloudFile`, its AES-CBC invocation, cached form parser and command parser were examined; encryption was not treated as missing information.

GN Audio publishes a [device metadata API](https://developer.jabra.com/sdks-and-tools/metadataapi/) and a public `@gnaudio/jabra-properties-definition` package. Version **14.7.1**, declared **Apache-2.0**, was checked locally for supplementary property encodings. Its 434 properties cover more devices/transports than Sound+; availability is not inherited by this model list. Facts were transformed into our own index, not copied as a vendor pipeline interpreter.

The [MIT plasma-jabra-headphone-support project](https://github.com/nvaert1986/plasma-jabra-headphone-support) at commit `467309a93a55d317ec47de94ec2d1090dc22472b` was checked for framing and EQ agreement. Its tested transport is USB/dongle; Sound+'s Bluetooth routing remains the authority here. No project code was copied. No confirmed disagreement for the same transport was found. The README-linked protocol document was absent from that checkout, so executable protocol/tests were inspected instead.

## Implementation and verification still required

This research supplies encodings and model-bound controls, not a completed Jabra integration. A later change needs two transports/codecs, strict stream parsers, serialized requests, timeout/reply/error handling, runtime capability intersection, shared-payload read/modify/write, model-aware settings UI and safe recording/report filtering. Use the matching historical snapshot conservatively when firmware differs, and preserve unknown fields. Never enable every SDK command because it appears in the catalog.

Factory reset, reboot, immediate power-off and firmware/update paths must not be sent without the owner's current explicit confirmation. Production tests, debug/memory writes, credential handling and pairing-list deletion are outside ordinary settings support. Names, Bluetooth addresses, serials, pairing lists and contact data must not enter public reports unfiltered. Hearing tests and tone playback also need deliberate user interaction.

Some app flags describe features newer than a bundled form snapshot: for example, wind/automatic volume/mono-HearThrough flags do not by themselves supply a new firmware form. Keep that distinction when intersecting the sources; the public properties index provides additional layouts but does not prove model availability.

Next useful validation is a whole-app virtual peer for representative GNP and GAIA devices, followed by filtered vendor-app recordings from real devices. Virtual acceptance only proves the fake peer and app agree; it cannot prove firmware applies EQ, persistence, ANC behavior, routing or button actions. Extend the research with those findings rather than relabeling all models tested after a shared encoder check.

## PairFree's GNP session (2026-11-16)

`JabraSession` (`protocol/jabra`, one `Talk`, no adapter) is written from this document and checked against the native encoder's vectors (`data/jabra/research/test_vectors.json`) and replies built
from the app's own layouts. No Jabra product has been tried. `tools/import_jabra_app.py` turns the research files into the three tables the build reads (`data/jabra/commands.json`, `settings.json`,
`devices.json`); `tools/gen_jabra_strings.py` writes the words of the rows.

**Opening.** A read of the product id (`02/11`, low byte first), of the firmware (`02/03`, a length byte and ASCII) and of the battery (`12/02`). The id picks the model's variant, the version the firmware
snapshot (the exact one, else the nearest older, else the oldest); the snapshot's form lists the settings, each read once (the sidetone's two settings share one read) and shown once it answered. The
equalizer, ambience, loop, Spotify, spatial sound and language reads follow from the snapshot's capabilities (`music_equalizer`, `ambiencemodes`, `spotifytap`, `spatialsound*`).

**Option codes.** The forms leave some codes blank or give both options of a switch the same code (`Off=00, On=00`). The command's layout names the real ones (`GNP_CONFIG_HEARTHROUGH_ON` 01,
`Enabled` 00 and `Disabled` 01 for the sidetone, which the form lists as "On=00, Off=01"); the importer takes them by name. The older noise form's four options map by name to `13/87`'s codes (off 0,
ANC 1, HearThrough 2, HearThrough with music 3). One setting (the boom arm's rotation) has no code in either place and is left out.

**Battery** (`12/02`: flags, local level, two ADC bytes, the extra-unit count, then address, reserved byte and level for each). Read from `GNBatteryStatus::LoadPayloadInfo` (native, 0x3db4a8): bit 0 of the
flags is charging, bit 1 low; an extra unit with address 0x15 is the case, 4 the right earbud and 5 the left. The Java side (`BatteryStatusSimple`) puts the local level on the side the phone's link
event names and the first extra unit on the other. blocked: which earbud holds the link, so PairFree places the local level opposite the extra unit's address; checked: the native parser, the SDK's
`BatteryStatusSimple` and `EarbudConnectionState`, whose input is the `LinkConnectionEvent` (`JabraDevice::OnLinkConnectionEvent`, native 0x3a83f0) which PairFree leaves out. Confirm with a report.

**Noise control** has three forms by snapshot: the ambience command `13/BE` (`ambiencemodes`), the older `13/87`, or only HearThrough's switch in `13/76` (on and off, with a mode byte for the music mix, kept
when the switch is written). The ambience command's levels run strongest first on the wire (0 is the maximum); the screen shows 1 as the weakest. The loop is `13/BF` in the SDK's ordinals.

**Equalizer.** Written whole as the native setter writes it (the vectors in `test_vectors.json`, cases two and three, are in `JabraSessionTest`): count, parameter 0, the master gain kept, and each band's
frequency, gain (float times 60, truncated toward zero) and Q kept; a gain beyond a band's maximum sends nothing. The enable byte (`13/7E`) is sent first, 1 for a curve and 0 for a flat one: inferred from the
Sound+ handler, which names a neutral-curve check and `setEqualizerEnabled` but whose body did not decompile (`JabraDeviceEqualizerHandler`).

**Language.** The voice language is written inside the configuration mode: a read of `0D/11` (answered 1 when entered), the write of `13/08` with the id as two bytes, low first, and the write of `0D/12`.
Only languages the earbuds list (`02/08`) are offered. No firmware language pack is touched.

**Left out, with the reason.** The pairing list (`GET_DB_RECORD`), renaming (`BLUETOOTH_NAME`, `AUDIO_NAME`), the speed-dial number and the firmware lock are not built: they hold other devices' addresses,
a name or contact data, or are firmware traffic (hard rules, and the owner's no-renaming decision of 2026-11-04); checked: `data/jabra/settings.json` `left_out`.
blocked: the three-byte spatial sound reply (`SPATIAL_SOUND`: call, media and Dolby bytes) and the call tone setting (`CONFIG_EQUALIZER`, handled by the app itself); checked: both layouts in
`research/gnp_commands.json`, which name the fields but give no code table. The one-byte reply (VSS mode, codes 0 to 4) is built.

## Checks for this research commit

The repository Python suite passed: **141 tests** (`python -m unittest discover -s tools`). A separate read-only research validator checked all JSON, every profile/catalog/setting reference against the decoded forms, model coverage, command ranges, the 12 native framing fixtures and four native EQ cases, and documentation links. Its script is `E:\Builds\research\jabra-2026-11-09\validate_research.py`. These checks validate the documentation/data and encoder observations; they are not device acceptance tests. No Android production code changed, so no Android build is claimed.
