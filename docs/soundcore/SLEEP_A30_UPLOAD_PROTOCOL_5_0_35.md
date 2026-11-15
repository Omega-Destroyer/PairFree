# Sleep A30: file writing and offline playback in Soundcore 5.0.35

Research date: 2026-11-15. Targets: **D1301 (Sleep A30)** and the separately identified **D1301S** route. This updates the older [6.4.0 feasibility assessment](SLEEP_A30_AUDIO_FEASIBILITY.md) using restored 5.0.35-435 code and the current native library. [Numeric evidence and exact synthetic cases](SLEEP_A30_UPLOAD_EVIDENCE_5_0_35.json) accompany this document.

The app's preparation, transport selection, several control messages and native container are now substantially understood. **No independently prepared audio has been uploaded to or played by a real A30 in this research.** The full transfer state machine has not been exercised against a virtual earbud either. This is a protocol specification with explicit remaining work, not a ready-to-run uploader.

## 1. Three different meanings of offline

| Operation | What the evidence establishes |
| --- | --- |
| Earbud Local mode | An already transferred sound is played locally; mode 1 selects it. This is separate from phone Bluetooth streaming. |
| Official app preparing/uploading a sound | Both the A30 popup and shared upload entry require network availability. New preparations obtain media identifiers through the server workflow, even if audio inputs are cached. |
| Proposed PairFree saved-file library and uploader | Imported originals and converted outputs can be stored on the phone and reused without a picker or network. This library is not implemented. Whether firmware accepts locally assigned media IDs and independently encoded files still needs confirmation. |

The vendor app's network checks do not prove that the Bluetooth transport or earbuds require internet. Conversely, a local container writer without a signature step does not prove that the complete device path accepts arbitrary metadata or audio. Do not advertise a working offline custom uploader yet.

## 2. End-to-end process and source trail

1. **Read current state and audio inventory.** Identify the actual model, firmware, TWS state, current mode, selected sound and existing uploaded entries. `MusicDownloadHandlerImpl.m` checks device eligibility and TWS state; some content paths additionally require the case connection. Do not apply a case-only branch from another model universally to D1301.
2. **Choose or replace content explicitly.** The UI describes one user-selected local file and replacement. `DataUtil.b` nevertheless returns candidate indices **0 and 1** for D1301/D1301S. Use reported state and trace allocation/replacement rather than equating those candidates with two independent arbitrary-track slots.
3. **Prepare source audio.** The popup removes zero-volume inputs. The shared view model locates/downloads the selected sources, obtains server-side transfer metadata for the vendor workflow, mixes/encodes them, and wraps the encoded bytes in a container. For a future PairFree library, retain the user's original independently of those vendor cache paths.
4. **Apply the app's size policy.** The prepared bin size is compared with the model threshold; an oversized result triggers a lower-bitrate preparation path. This is not a demonstrated firmware storage query or a complete final size-rejection contract.
5. **Allocate the target index and transport.** `TransferringSoundViewModel.I` builds `OtaMusicFileModel` entries with transfer ID, index and prepared path. Its `z()` returns vendor type **1** for D1301/D1301S. `MultiMusicOtaUpdate` selects the BES music-file path, not the WQ runners.
6. **Run the audio-specific BES handshake and transfer.** Keep its user flag, file index and transport configuration intact. The command family is shared with update tooling, but the inspected caller configures an audio transfer. Do not substitute firmware or reboot operations.
7. **Wait for final device success.** Sending the last data chunk is insufficient. The service verifies the file/result and final activation exchange before reporting success.
8. **Refresh and reconcile inventory.** The popup requests audio inventory or performs cleanup of leftover entries, refreshes state where its model branch permits, and updates the library UI. Verify the expected media ID/index and Local playback before calling a future independent upload successful.

Key source classes are `D1301NoiseTransferringSoundBottomPopup`, `TransferringSoundViewModel`, `AudioMixHelper`, `MultiMusicOtaUpdate`, `DefaultBesTaskFactory`, `BesOtaService`, `BesOtaCMD` and `D1301AnalysisService`. Some coroutine Java output is malformed; use restored DEX for its branches. For example, DEX for `TransferringSoundViewModel.N` explicitly returns before the WQ wrapper unless vendor type is 3. D1301/D1301S return 1, so their files must not acquire the WQ package merely because that code is nearby.

## 3. Encoding, size and duration

| Property | Recovered app behavior | Limit of the conclusion |
| --- | --- | --- |
| Ordinary encoding branch | `audioType != 1`: FFmpeg SBC, mono (`-ac 1`) | Exact SBC bitpool/frame settings and independent firmware acceptance are not established. |
| Alternate branch | `audioType == 1`: FFmpeg AAC in ADTS, mono | Shared-code branch; not proof every A30 UI content type exposes it or every firmware accepts it. |
| Initial/fallback bitrate | D1301/D1301S config: 128 / 72 kb/s | Re-encoding policy, not a device-advertised bitrate range. |
| Sample rate | 16,000 Hz normally; `soundType == 3` uses 32,000 Hz | D1105 has a separate override; do not apply it to A30. |
| SBC output duration | `min(first selected input's playTime, 60 seconds)` | App-generated clip cap, not proven firmware maximum. |
| AAC output duration | `min(first selected input's playTime, 10 seconds)` | Shared alternate encoder cap, not a universal A30 duration limit. |
| Prepared-file size threshold | **1,024,000 bytes** for D1301/D1301S | Triggers the app's fallback encoding; not proof that any file below it is accepted or a larger one is rejected by firmware. |
| Header allocation on mix path | **256 bytes** | Earlier native tests used 128-byte headers and must not define the new caller's output. |

`RangesKt.f(float,float)` is an upper clamp, confirmed from its implementation. The command builders also contain looping/mixing filters (`aloop`, `amix`, `duration=first`), per-input volume normalization, and speed/EQ transforms. These are app-side preparation choices; the exact loop seam behavior of custom user files and firmware playback remains untested. Overnight playback does not imply an hours-long file is stored. The UI's approximately 40-second transfer estimate is unrelated to clip duration.

The source's oversize branch re-encodes after inspecting a prepared file. The coroutine output contains malformed duplicated regions, and this research does not claim a reliable hard final reject/retry rule for every batch or input. An independent implementation should measure the actual encoded/container bytes and present an explicit trim/size decision rather than infer duration from nominal bitrate alone.

## 4. Container written by the current native library

The ARM64 `transfer_sbc_t_bin` entry is at **0x59AE8** in the extracted 5.0.35 library, SHA-256 `fa32df302d5b717d723223dc7b6c94a1d6c504581a929c46c0a31ee357e5a8a0`. The inspected caller supplies eight arguments: encoded input path, output path, header allocation, media ID, name, sound type, selected sample rate and codec/audio type.

The writer copies the encoded bytes unchanged after the allocated header. It does not itself encode audio. Header integers below are little-endian.

| Offset | Bytes | Meaning / value |
| --- | --- | --- |
| 0 | 4 | Magic 0x36363131, bytes `31 31 36 36` |
| 4 | 1 | Caller codec/audio type: inspected branches 0 SBC / 1 AAC |
| 5 | 2 | Constant 0x0110 |
| 7 | 1 | Zero |
| 8 | 8 | Media/transfer ID |
| 16 | 1 | Caller sound type |
| 17 | 7 | Zero |
| 24 | 64 | Name, truncated/padded by native `strncpy` |
| 88 | 4 | Encoded payload byte length |
| 92 | 4 | **Literal 128**, retained even when allocating 256 bytes |
| 96 | 4 | Sample-rate argument traced from the mix caller |
| 100 | 4 | Zero |
| 104 | 4 | Actual header allocation argument: **256** on this caller path |
| 108 | 2 | CRC-16 of encoded payload |
| 110 | 2 | CRC-16 of header bytes 0..109 |
| 112..255 | 144 | Zero padding for a 256-byte allocation |
| 256 onward | payload length | Unchanged encoded audio for this caller |

Both CRCs use polynomial **0x1021**, initial **0**, with the native routine's non-reflected byte processing. Forty-eight synthetic native-execution comparisons matched an independent reconstruction byte for byte, varying header size 128/256, codec byte 0/1, payload size and ASCII name truncation. These tests cover container construction and CRCs, not valid audio decoding or device acceptance.

**Do not use field 92 alone as the payload start.** With header allocation 256, the executed native output starts audio at 256 while field 92 stays 128. The firmware interpretation of that legacy field has not been independently established. Preserve what the caller/writer produce and retain the distinction in any decoder.

## 5. Two Bluetooth conversations

### Ordinary Soundcore control: select Bluetooth or Local

`D1301MainVM.H1` calls `Cmm2BtDeviceManager.G`, which delegates to `CmmBtCmdService.b4`. The prefix is **`08 ee 00 00 00 01 a9`**, with a one-byte payload:

- **0**: Bluetooth mode.
- **1**: Local/sleep playback mode.

These are command prefix and payload, not a complete frame: the ordinary Soundcore length/checksum framing still applies. Two interpreted builder cases verified the payload values and prefix. The UI also stops its phone-side sound player on transition to local mode. `D1301AnalysisService.W1` reads the returned mode at full-packet offset 9, requires at least 11 bytes, and stops the media provider when the value is 1. Do not send mode-switch traffic over the BES file channel.

### Audio transfer: BES RFCOMM service

`MultiMusicOtaUpdate.S` configures:

- **SPP UUID `66666666-6666-6666-6666-666666666666`**, from `BesSdkConstants.f30049a`.
- `USER_FLAG = 1`, upgrade type **2**, user/index selector **10 + indexInDevice**.
- `totaConnect = false`: the inspected path does not apply the optional TOTA wrapper.
- `isWithoutResponse = true`, and the music caller supplies `skipGetVersion = true`.

The SDK's other UUIDs, standard serial UUID and WQ package settings are not interchangeable alternatives for this path. Actual reconnection/channel ownership on A30 hardware still needs validation.

In user-flag-1 mode, BES builders emit **opcode u8 + payload length u32 LE + payload**. The following are complete BES messages at the SDK boundary, before RFCOMM segmentation. They are not ordinary Soundcore nine-byte-header packets.

This is a table of available builders and conditional transitions, not a fixed list to concatenate and send. In particular, `BesOtaService.e0` starts at negotiation `s()` when the inspected music/skip-version flags are set; other branches perform version, side and user selection. Preserve the actual state machine rather than assuming every optional negotiation appears in every upload.

| Purpose / builder | Example bytes | Notes |
| --- | --- | --- |
| Version query `i()` | `8e 04 00 00 00 42 45 53 54` | Optional path; music skips initial version retrieval. |
| Select side `u(0)` | `9d 01 00 00 00 00` | Shared SDK path; other inputs map 1 -> 1, 2 -> 2, otherwise -> 0. |
| Select audio user/index `t(10)` | `97 01 00 00 00 0a` | Example for index 0; index 1 selects 11. |
| Begin negotiation `s()` | `90 01 00 00 00 00` | Fresh tested static state; payload is SDK `M[0]`, not an inferred universal constant. |
| Resume/start negotiation `e()` | `8c 2c 00 00 00` + 44-byte payload | `BEST`, 32-byte resume value, bytes `01 02 03 04`, then CRC-32 over those 36 resume/seed bytes. |
| File announcement `v(path)` | `80 0c 00 00 00` + 12-byte payload | `BEST`, file length u32 LE, CRC-32 of the complete prepared file u32 LE. |
| Transfer configuration `m(path,context)` | `86 5c 00 00 00` + 92-byte payload | Contains an inner length field, file-tail-derived bytes, option flags, optional configuration data and CRC-32. Complete field meanings/defaults still need validation; do not replace it with a guessed zero block. |
| Audio data `k/j` | `85` + length u32 LE + file bytes | Sizing follows negotiated/profile/segment state, not a fixed guessed chunk size. |
| Segment CRC | `82 08 00 00 00` + 8-byte payload | `BEST` plus segment CRC-32 u32 LE. |
| End file | `88 00 00 00 00` | Emitted after transfer state reaches file end; not final success. |
| Final activation `l()` | `92 04 00 00 00 42 45 53 54` | Requires final result acknowledgement. |

Sixteen interpreted cases verify the small stateless/control builders, including `i`, `o`, `l`, `s`, `t`, `u` and `n`; exact bytes are in the JSON. File announcement, resume-token construction, 92-byte configuration, chunking and full callback sequence above are **source-traced**, not executed together in those cases. No saved token, phone identifier or preference content was read. Resume values are per-session/device transfer state, not server login credentials, and must not be exported in recordings.

## 6. ACKs, progress, retries and completion

For the inspected BES mode, response status bytes generally start after opcode and four-byte length, at offset 5. Some messages also include `BEST` before their fields. Validate declared length and command-specific spans before accessing them; the vendor parser is not a general malformed-input validator.

| Response opcode | Recovered behavior |
| --- | --- |
| 0x91 | Status 1 accepts negotiation; service advances to 0x8C. |
| 0x8D | Resume offset u32 LE at offset 5. Fresh offset 0 additionally carries a 32-byte resume value; the caller then announces the file. Nonzero offset follows the resume/negotiated-state branch. |
| 0x81 | File/transport parameters after `BEST`; includes the negotiated packet/profile value used for chunk sizing. |
| 0x87 | Status 1 accepts configuration and starts data scheduling. |
| 0x83 | Segment CRC status: 1 advances the verified boundary; other status triggers segment failure/retry handling. |
| 0x84 | Status 1 accepts the completed file/result; service sends final 0x92 activation. |
| 0x93 | Status 1 completes activation; service marks success and notifies the music-file layer. |

`BesOtaCMD.B(1, true, true)` selects a **32,768-byte segment** for this SPP/no-response configuration. Individual data messages fit the negotiated profile and stop at the next segment/file boundary. For example, a fresh profile value 512 produces a 507-byte data payload after the builder's overhead adjustment; it is not a hard maximum for all devices or replies. The shared profile code has other caps and a special first-segment branch.

The SDK tracks current offset, last verified offset and segment index separately. On segment failure, `BesOtaCMD.b` restores the last verified offset; the service has a retry branch while its retry counter is below 3. Do not turn that into blind whole-file retransmission or assume resume works for a different file. Local write-completion scheduling and segment acknowledgements are distinct: do not invent a required on-wire ACK for every data chunk.

Source timer calls include 3-second negotiation waits and 5-second user-selection/segment-CRC waits. The service also contains a pacing-update branch mapping a zero report to 20 ms; this pass has not validated that branch's trigger/reachability or hardware timing. These are source scheduling details, not recommended universal timing for a new client. Cancellation releases the local task/runner; this pass does not establish an A30-specific on-wire abort transaction or rollback guarantee.

**Success is the final acknowledged transaction, followed by inventory/playback confirmation.** A progress value of 100 or a successful local socket write is insufficient.

## 7. Inventory, cleanup and local playback confirmation

`D1301AnalysisService.C3`, called from the full state parser at a model-dependent offset, reads:

| Relative offset | Meaning |
| --- | --- |
| 0 | Local music volume u8 |
| 1 | Current sound index u8 |
| 2 | Playing flag, byte equals 1 |
| 3 | Uploaded-entry count u8 |
| 4 onward | Ten bytes per entry: index u8, media ID u64 LE, sound type u8 |

A received entry type 0 is remapped to 2 in this parser. The helper expects room after the entries as well. Its starting offset is `iN + 74` in `e1`, not a fixed offset interchangeable with older A20 snapshots.

After upload, the popup calls `d3 -> g2` when no leftover entries need removal: ordinary Soundcore prefix **`08 ee 00 00 00 13 01`** requests audio inventory. Otherwise `e8 -> U3` uses prefix **`08 ee 00 00 00 13 04`** to reconcile/delete old entries. Its payload starts with count u8 and that many index bytes; an optional nonempty metadata list adds a count and `(index u8, media ID u64 LE)` records. The observed popup passes null for that optional list. Never apply cleanup to a guessed index or blindly remove the new entry.

The popup can then refresh full device state, with model-specific exclusions. Its `j8(true/false)` calls update a **local transferring flag/event**; they are not additional Bluetooth start/stop commands.

For a future independent uploader: verify inventory contains the expected ID/index, select Local mode with `01 A9 / 01`, and confirm audio playback. Track physical listening separately from parsed inventory or a virtual ACK. The relation between the two allocator candidates, different sound types and the one-file UI needs device evidence before exposing multiple resident tracks.

## 8. Phone-side saved library and offline re-upload design

This is proposed PairFree behavior, not recovered vendor protocol:

- On initial import, copy the original into app-owned storage. Retaining only a picker URI is insufficient when the source moves or permission is lost.
- Keep a local index with a stable local ID, display name, content hash, duration, original size, trim/loop settings and last target. Keep converted output with its exact codec, sample rate, bitrate, container revision and target constraints.
- Re-upload from the saved original/prepared cache without the picker. A cache prepared for another model/codec must be regenerated or explicitly validated.
- Preserve the original when conversion or transfer fails. Deleting an item from the phone library and replacing a resident earbud file are distinct actions.
- Show the prepared duration/size and explicit replacement target. Keep conversion, progress and cancellation usable when changing screens. Clear app data/uninstall removes private copies unless exported/backed up.
- New custom-media ID assignment, the 92-byte transfer configuration and firmware acceptance must be resolved before promising account-free re-upload. The vendor server's `getIdFromServer` call is not a requirement to reproduce its account system in PairFree.

## 9. Recording-filter audit and capture implications

The existing [capture guide](SLEEP_A30_CAPTURE_GUIDE.md) correctly requests **only PairFree's filtered text report**. Its implementation predates this recovery and needs a follow-up before it can reliably diagnose this transfer path:

1. `SoundcoreRecording` recognizes only a 128-byte header and requires both fields 92 and 104 to be 128. The new native output has 92 = 128 and 104 = 256, so that recognizer does not accept it. Its magic matcher also fixes codec byte 4 to 0.
2. `SnoopLog` only exports unframed Soundcore RFCOMM chunks after a normal Soundcore frame positively identifies that same channel. A separate BES service channel cannot be assumed to receive that classification.
3. Future recording support should associate the BES service/channel reliably, reassemble opcode/u32-length records per connection/direction, handle split 256-byte headers and SBC/AAC codec bytes, and export bounded status/length/offset/CRC diagnostics. Omit audio, filenames, media IDs, resume values and optional configuration bodies. Preserve existing private sleep/alarm exclusions.

This commit changes documentation only. It does not add that filter/parser or claim a current PairFree capture includes every BES packet. A missing transfer in a filtered report is therefore not evidence that no transfer occurred. Do not work around it by asking the owner to post a raw snoop log or Android ZIP.

## 10. Evidence, reproduction and next checks

- Original APKM SHA-256: `b7acfeee5c506879e5870a8215562b8b0cd896a93aaa54cd3eb28c24cdc6e734`.
- Restored DEX/source inputs are the existing 23-file offline recovery; the native library was freshly extracted from that bundle's base APK.
- **14** interpreted model/codec routing/configuration cases; **16** BES control-builder cases; **2** ordinary mode-builder cases; **48** native container comparisons. Counts are scoped experiments, not complete virtual-device tests.
- External tools under `E:/Builds/research/soundcore-full-protocol-2026-11-13/per-model-analysis/`: `SleepAudioProbe.java`, `sleep_a30_native_probe.py`, `write_sleep_a30_evidence.py`, `verify_sleep_a30.py`, and redacting `SafeDexMethodDump.java`. Native binaries, disassembly and synthetic intermediate files stay outside Git. See the [recovery runbook](PROTOCOL_RECOVERY_RUNBOOK.md) for commands.

Next work: independently reproduce the complete preparation and 92-byte configuration, resolve custom ID and slot semantics, add the filtered BES recording diagnostics, then confirm a short independently prepared file on real A30 hardware. Validate interruption/resume/replacement and final playback separately. Adaptive snore masking additionally uploads characteristics through its own path; ordinary imported audio must not be labeled adaptive masking merely because it plays locally. No factory reset, reboot, power-off, firmware update or other device traffic was sent in this research.
