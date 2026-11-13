# Soundcore protocol follow-up: exposed sources and targeted validation

Research date: **2026-11-13**. This follows the owner's request to recover the
app-to-earbud protocol without unpacking unrelated application features. It adds
research evidence, not device support or an uploader. No Bluetooth traffic was sent.

The existing [3.3.0 command table](CMM2_COMMANDS.md) and Liberty 4 state layout in
[PROTOCOL.md](PROTOCOL.md) already document substantial work by the previous agent.
The findings below supplement that work; the command-table indexing is not claimed
as a newly discovered protocol.

## Sources and coverage

- Target 5.0.35-435 is unchanged from the November 11 input: APKM SHA-256
  `b7acfeee5c506879e5870a8215562b8b0cd896a93aaa54cd3eb28c24cdc6e734`.
  Its protected Java Bluetooth builders remain unrecovered. Native-loader recovery
  and VM experiments are under `E:\Builds\research\soundcore-unpack-2026-11-13`.
- Readable fallback 3.3.0-272: APKM SHA-256
  `3adc48dbdbebda898dfd80c56bf81316393871f24d0458b1a3c2d6df54586fa5`,
  manifest target SDK 32. Fresh inspection of this bundle found ten exposed DEX
  files in its base APK and no `assets/ijiami*` markers. This is the current input's
  count; the earlier assessment reported eleven. JADX 1.5.6 reported 78 errors
  overall, but none of the 129 indexed `devicecmd` source files contained a
  decompilation-failure marker. Selected builders were also executed as described below.
- All extracted/decompiled code, scripts and generated inventories remain outside
  the repository, under `E:\Builds\research\soundcore-protocol-2026-11-13`.
  Source paths below are relative to `fallback-3.3.0/jadx/sources/com/oceanwing/`
  within that directory. No secret assets, server credentials or authenticated
  vendor endpoints were used.

The generated index has 160 static header definitions across the indexed protocol
files, 146 command-builder methods, and 86 explicit product-code declarations.
These are **not** counts of unique commands, fully researched devices or verified
settings. Some products use inherited or legacy managers outside `devicecmd`.
The existing Cmm2 table's narrower count refers to its common command constants.

## Model bindings that the previous assessment missed

### Life Note C / A3943

`soundcore/model/deviceprodctinfo/A3943ProductModel.java` explicitly selects:

- Command service `devicecmd/manager/l/l/b.java` (`A3948CmdService`).
- Parser `devicecmd/manager/l/l/a.java` (`A3948AnalysisService`), constructed with
  the product code **A3943**.
- `A3944MainActivity` as its home screen.
- RFCOMM service `0CF12D31-FAC3-4553-BD80-D6832E7B3943`.

Thus the lack of a package named `a3943` or an `A3943AnalysisService` does not mean
the app lacks its protocol. Its explicit factory binding supplies the starting
point. Follow the shared parser's model conditions and UI feature gates before
enabling settings; reuse does not establish identical capabilities on every model.

The same direct service family appears in the product factories for A3927,
A3927EU, A3944/A3944EU, A3948/A3948A25 and A3949/A3949P25/A3949R50. The local
`fallback-model-bindings.json` records these allocations. It does not replace a
per-model feature audit.

### Sport X10 / A3961

`soundcore/model/deviceprodctinfo/A3961ProductModel.java` returns
`A3961MainActivity`, and initializes `soundcore/l/l/b.java`
(`A3933DeviceManager`) with its own product code and the service
`0CF12D31-FAC3-4553-BD80-D6832E7B3961`.

That manager explicitly includes **A3961** in the state-dispatch branch that calls
`B0`, then `G0`. This is a legacy protocol implementation outside the newer
`devicecmd/manager/l` directory, not an absent parser. Its view models also call
that manager for touch tone, voice prompts and wind-noise detection.

Selected state offsets, counted from the start of the complete reply:

| Offset | Source interpretation |
| --- | --- |
| 9–14 | Host side, TWS status, left/right battery, left/right charging |
| 15–24 | Two five-byte firmware strings |
| 25–40 | Serial-like identifier; keep out of public captures |
| 41–42 | EQ identifier; interpretation depends on its high byte |
| 43–111 | EQ and HearID-related sections, parsed by the common helpers; presence alone does not imply that the model offers HearID |
| 112 onwards | Button block, parsed by `I0` with a firmware-dependent single-press branch |
| 124 | Noise-mode cycle bits: ANC, transparency, normal |
| 125–128 | Noise-mode fields, normalized through the manager's helpers |
| 131–134 | Touch tone, wear detection, game mode, case battery |
| 136–137 | Colour and wind-noise detection |

`B0` requires at least 142 reply bytes. For **A3961**, normalized firmware at
least `179` and reply length at least 143, `I0` reads single-press enable/action
bytes at 138/139 and 140/141, using high/low nibbles for solo/TWS behavior.
The older branch uses the earlier positions in the button block. Preserve this
condition; copying a fixed A3933 button layout would miss it. These offsets were
read from source, not confirmed on hardware.

The former assessment's statement that A3943 and A3961 had only names and no
usable implementation was incorrect. Their remaining work is tracing these
actual managers and feature gates, then validating the resulting layouts.

## Noise-control writes: model-specific payloads

`devicecmd/manager/l/k/b.java` (`A3947CmdService`, Liberty 4 NC) and
`devicecmd/manager/l/n/b.java` (`A3953CmdService`, Liberty 4) both use command
**06 81**, but their `q1` payloads differ:

| Byte | A3947 | A3953 |
| --- | --- | --- |
| 0 | Main mode, with the base class's mode normalization | Same |
| 1 | Manual ANC option in high nibble; automatic option in low nibble | Same |
| 2 | Transparency option, clamped to 0–2 | Same |
| 3 | ANC automation mode | Same |
| 4 | Wind-noise suppression, 0/1 | Same |
| 5 | Environment detection, 0/1 | Automatic ANC sensitivity |
| 6 | Multi-scene mode | Absent |

Both override the manual option clamp to **1–6**. The elementary nibble helper is
`BytesUtil.F(high, low) = ((high & 255) << 4) | (low & 15)`, truncated to a byte.
The generic parent has a different manual clamp, so it must not be substituted
without following the actual model override.

**Offline validation:** executed the actual exposed A3947/A3953 builders,
`SendCmdDataBean`, `BaseBtDeviceCommand` and `CheckSumUtil` in a JVM harness.
The Android transport was stubbed to perform no I/O; elementary byte helpers,
the otherwise unused EQ container and parent methods were stubbed from inspected
source. Ten synthetic cases, including manual-option boundary cases, matched an
independent payload/frame reconstruction. This validates selected serialization
paths, not the admissibility of every synthetic setting or hardware acceptance.
Results: `builder-probe/results.json` in the external research directory.

## EQ preset discrepancy to retain for review

The preset arrays of `A3040CmdService`, `A3936CmdService`, `A3947CmdService`,
`A3952CmdService` and `A3953CmdService` each contain 22 rows with nine explicit
constructor fields. Their matrices are identical in this APK. This does not make
them nine visible bands: follow the EQ bean, hidden fields and serialization.

At table index **17**, corresponding to Rock in the recorded OpenSCQ30 preset
ordering, the visible eight fields differ from `type_2` in
`data/soundcore/eq_presets.json`:

| Source | Eight values in dB |
| --- | --- |
| Vendor 3.3.0 raw preset row | 3, 2, -1, -1, 1, 3, **3, 3** |
| Current OpenSCQ30-derived `type_2` row | 3, 2, -1, -1, 1, 3, **4, 5** |

The first nine stored fields of the other 21 rows agree after dividing the
recorded OpenSCQ30 values by ten. The tenth hidden position and final EQ/HearID
wire transformation were not validated here. The actual vendor `g(17)` getter
was executed for A3947 and A3953 and returned the raw vendor row above.

This is a **source-table discrepancy**, not a hardware-confirmed correction.
Presets in the app data were left unchanged; recordings and firmware-specific
behavior take precedence. The [upstream differences log](../UPSTREAM.md) records it.

## Sleep A10 audio transfer is a separate protocol path

The A6610 music-selection path gives a concrete transfer lead without unpacking
the latest app:

1. `A6610SleepMusicSelectActivity` obtains a selected model's `binUrl`, downloads
   a packaged file to a name ending in `.OTA`, and calls `startOta()`.
2. Its manager is `soundcore/i/c/a.java` (`A6610WqOtaUpgrade`), extending
   `ota/sdk/wq/WqOtaUpgrade`. That subclass returns **false** from `j0()`.
3. The base transfer manager opens RFCOMM service
   **00007034-0000-1000-8000-00805F9B34FB**. `BaseDeviceManager.t()` sends the
   supplied bytes directly through the SPP transport.
4. `WqOtaData` declares preamble **70 07 6E** and terminator **33**.
   The data-frame builder `WqCmdSendAndAnalyze.q` places `C0 E5`, a big-endian
   two-byte length, sequence byte, four offset bytes, file data and a four-byte
   CRC before the terminator. Its length field is data length plus nine.
   CRC covers the file-data bytes, not the whole frame. The helper implements
   reflected CRC-32 with initial/final inversion. These are source findings;
   no file or packet was transferred.
5. That builder splits a constructed frame into writes of at most **550 bytes**.
   This is an application write-chunk size, not proof of an RF or GATT MTU.
6. On transfer completion, the UI sends the selected music id through the normal
   command manager: `sendSetMusicIdCmd` → `w2` → `x1`, command **01 A4**, one-byte id.

The OTA-labelled transport is also used for device updates. Its base class
contains update-mode and reboot operations; `j0()` controls some completion
branches. **Do not implement a raw-audio uploader or replay this flow based on
the filename, callback names or subclass flag alone.** The downloaded package's
content/type validation and safe audio/update distinction still need investigation.

The normal Sleep A10 callbacks also map `01 10` to sleep-history retrieval,
`01 A6` to clock sync, `01 A7` to alarms, `01 A9` to current mode, `01 AA` to smart
play, `01 AB` to custom play time, `01 AC` to adaptive noise, `01 A4` to selected
music, `01 B1` to volume, and `01 B2`/`01 15` to playback set/read. Command
**01 B0 clears sleep history** and is not a harmless read. These meanings are
model-specific; for example `01 10` has another meaning on older non-sleep models.
Clock, alarm and sleep-history bodies require privacy handling.

**None of this establishes Sleep A20/A30 compatibility**, duration limits,
accepted user-produced files or an A30 upload service. It is a concrete place to
look in the newer app or a filtered capture. A recorder audit should distinguish
the separate service/stream and shared update transport before retaining fields;
it must continue excluding audio, identifiers and actual firmware data.

## Scope of the latest native/Flutter examination

The 5.0.35 Flutter bridge primarily calls native methods; that boundary does not
give the underlying Bluetooth bytes. The symbol inventory also found
`libprotocol_layer_api.so` and `libank-codec-lib.so`: their exposed APIs handle
audio packets/pages and codec wrappers. The former reports 16 kHz and 480 samples
per frame. No call path from those libraries to an earbud model was established,
so these values are **not** earbud-upload limits or a new control protocol.

The productive next targets are the device-command package, legacy managers
selected by product factories, and the audio-transfer path—not account, cloud,
AI-recorder or unrelated UI modules. For 5.0.35, targeted recovery of those paths
remains to do; findings from 3.3.0 must retain their version/model scope.
