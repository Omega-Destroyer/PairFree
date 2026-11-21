# Skullcandy protocol research

Virtual follow-up, 2026-11-06: [model-specific scope and interventions](VIRTUAL_TESTING.md).
No physical hardware was tested.

Research 2026-11-06; a local protocol-core draft is described in [IMPLEMENTATION.md](IMPLEMENTATION.md).
No Skullcandy model is enabled in the app and no hardware was connected.
[MODELS.md](MODELS.md) preserves the 56 bundled catalog records and firmware-feature unions;
[SERVICES.md](SERVICES.md) preserves Bragi service/method identifiers as facts. Those inventories are
not a promise of 56 controllable products or a single protocol across them.

## 1. Primary source and evidence locations

Primary source: Skullcandy 5.0.6 (`app.skullcandy`), reference XAPK SHA-256
`255153c95a3a12131af83350dc962a22b20eb49b6e73ce4a92800e82af254e9a`.
All four APK splits were inventoried. Flutter/Dart AOT code is in the arm64 split's `libapp.so`;
the base carries Java/Kotlin SDKs, product/feature metadata, and readable protobuf schemas.
The app's `assets/flutter_assets/assets/secrets/secrets.json` was not read.

Source locators in `E:\Builds\research\decompiled\skullcandy`:

- `resources/assets/flutter_assets/assets/products/product-manifest.json`: catalog, Android transports,
  model ids, firmware-specific features, button actions and unsupported-name list.
- `resources/bos_*.proto`: Bragi protobuf message field numbers and method ids; the original files
  remain outside the repo, including their copyright notices.
- `sources/com/bragi/sdk/android/transport/common/d`: incremental PPP frame reader and checksum scopes.
- `sources/androidx/work/impl/s.p`: the obfuscated PPP frame writer (it is not Android WorkManager semantics).
- `transport/gaia/d`: the two RPC serializers (raw body versus PPP wrapped); `rpc/n`, `k`, `q`, `l`:
  packet flags, packet type, registry, and response/status handling.
- `transport/race/a`, `e`; `transport/gaia/`; `transport/rpc/crypto/`: chip tunneling, timeouts,
  secure-channel wrapping; `api/model/config/` and strategy configs select per-device transports.
- `protobuf/BosAuthenticatedAccess`, the authenticated-access schema and SDK crypto implementations:
  local trust and session negotiation.

Public check: [Earshot Crusher 1080 notes](https://github.com/HellzYeahh/earshot/blob/main/docs/crusher-1080.md),
revision `a581f96efb5a91b0622a5be9600a02a72b4e1649`, MIT. Its Crusher work is explicitly untested on real
headphones. Its framing/checksum example agrees with the vendor reader and the independent calculation
below. A status-success assumption differs from the vendor SDK (section 4 and [UPSTREAM.md](../UPSTREAM.md)).
No public-project source code was copied into PairFree.

## 2. Product identity and multiple control paths

The catalog's `connectionTypeAndroid` can be BLE, Bluetooth Classic, or Classic plus LE Audio. These
are connection choices, not a chip/protocol identifier. There are legacy BLE/control paths, Qualcomm
GAIA, Airoha RACE, JieLi SDKs and Bragi BOS-RPC. Some products mix Bragi and platform-specific commands.
Do not build one GAIA or one RACE implementation and claim all catalog products work.

Examples of identity collisions matter: Crusher Evo appears as product ids `02` (BLE) and `30`
(Classic), with the same marketing/model name and different feature sets. Crusher 540 Active also has
separate BLE/Classic records (`2C` / `31`). Indy Evo/Fuel have legacy and Airoha-id entries. Model name
alone is insufficient for feature and protocol selection. Catalog firmware entries, not their union,
choose the actual feature set. A hidden/future product entry is not evidence of a released device.

Seven records are speakers (Terrain Mini/Terrain/Terrain XL, Session 360/540/720/900); speaker support
remains future work. Other records expose only FAQ, Tile or registration. A metadata record is not
proof that an app exposes a local headphone setting.

Crusher 1080 ANC (`S6CNW`, product `34`) is a particularly useful first Bragi target. The manifest lists
Bose QuietControl, Bose EQ/TrueSpatial, Crusher dial/profiles, ANC/ambient, sidetone, wear detection,
custom controls/EQ, multipoint and low latency. **Sound by Bose does not make it a BMAP device**.
Earshot identifies its Bragi codename as CHP300 and describes a secure RFCOMM data service
`54C5BF4C-C52F-4635-98DC-EEBF97C6726F`, with a distinct Airoha third-party service
`A4602B24-...`. These service-selection facts remain attributed to that reference until the matching
vendor device-config path is independently pinned for each product. Do not send PPP to a RACE service
just because an RFCOMM socket opens.

## 3. Bragi stream framing

The SDK's common reader recognizes:

```text
7E | FF | flags | protocol | payload length u16 BE | header CRC8 | payload | optional CRC16 u16 BE
```

The normal flags byte is `80` when the FCS is present. The protocol byte identifies the payload:
`90` user plane, `91` RTP, `92` secure RTP, `93` protobuf, `94` BOS-RPC, `95` secure BOS-RPC.
The header is seven bytes, including its CRC. The reader can consume partial headers, partial payloads,
multiple frames and FCS bytes across calls. It has a 4096-byte internal buffer: a future implementation
must reject oversized announced lengths before copying, not inherit an unchecked buffer assumption.

Header CRC: CRC-8/ROHC, reflected polynomial `E0`, initial `FF`, no final XOR, over bytes 0..5 including
`7E`. FCS: CRC-16/MCRF4XX, reflected polynomial `8408`, initial `FFFF`, no final XOR, over header bytes
1..6 and the payload, **excluding `7E` but including the header CRC**. The FCS is emitted high byte first.
This differs from both HDLC escaping and other protocols that use little-endian CRC storage.
Do not add an HDLC byte-stuffing rule without evidence from the selected Bragi transport.

Some SDK paths send the RPC body through another framing layer; others add PPP directly. The two
serializers in `transport/gaia/d` make that distinction explicit. Choose the configured stack once,
rather than wrapping every command in PPP and then blindly wrapping it again in GAIA/RACE.

## 4. RPC header, requests, errors and subscriptions

The unencrypted RPC payload is a four-byte header followed by the protobuf body:

```text
flags/type | service u8 | method u8 | tag u8 | protobuf
```

Type is bits 2..3: request 0, response 1, status 2, abort 3. Bit `10` is set by the request serializer
and checked on replies as the final flag. Preserve the per-request tag and method/service matching.
Subscriptions can produce multiple responses; do not treat the first successful status as the last
state update. An abort has the same RPC header without a protobuf body.

The SDK's `rpc/l` checks a STATUS body's first byte for zero before treating it as success. A missing
body is not an explicit success; status `64` hex means a subscription was replaced. Other decoded
errors include service/method unavailable, invalid arguments and access failures. Earshot's prose says
it also accepts an empty STATUS as success, so a future PairFree implementation should follow the
vendor's stricter behavior unless a real-device capture establishes another encoding.

Analytical frame, not a hardware capture: `device.dev_info`, service 2, method 1, tag 0, protobuf field 1
explicitly set to unspecified gender (`08 00`):

```text
7E FF 80 94 00 06 2D 10 02 01 00 08 00 54 80
```

The header CRC is `2D`; FCS is `5480`. Independently recalculated from the vendor reader's algorithms,
this agrees with Earshot's documented checksum example. It does not prove connection or authentication.

## 5. Readable service and setting behavior

Service/method ids below are decimal, matching the schemas. SERVICES.md has the full inventory.

| Area | Calls | Important details |
|---|---|---|
| Device | service 2: dev_info 1, sys_info 2, batt_info 3, mode_info 4 | device info includes serial/id and authenticated-access flag; battery entries include side, percent, charge status |
| Controls | service 3: get_info 1, get_action 2, set_action 3, subscribe 6, get_info_v2 7 | discover actions/slots per product; do not invoke/inject arbitrary actions |
| ANC / ambient | service 4: mode_read 1, configure 2, mode_info 3 | modes 1 off, 2 ANC, 3 transparency; ranges/preset counts come from mode_info |
| EQ | service 4: state 4, configuration 5, limits 6, configure 7, enable 8, disable 9 | parametric bands, hardware-advertised ranges and quantization |
| Sidetone | service 4: read 10, configure 11, limits 12 | separate enable/volume and limits |
| Voice control | service 5 | local feature and audio-streaming surfaces are distinct |
| Language | service 6: info 1, configure 2 | enumerate supported language/prompt choices |
| Low latency | service 8: get 1, set 2 | not the same as choosing a codec |
| Wear/insertion | service 12 | mode and trigger reads/writes are separate |
| Spatial | service 17: info 1, set 2 | 1 off, 2 fixed to head, 3 fixed to room; availability can change while single |
| Sounds | service 22: options_get 1, options_set 2 | feedback/prompt choices |
| Crusher | service 23: config 1, state 2, set 3 | maximum intensity, read-only flag and optional mode count come from config |

Hearing mode configuration uses protobuf fields mode 1, activate 2, optional preset 3, optional gain 4.
Off must be activated without gain/preset. Read limits before writing ANC/ambient parameters. The schema
warns that Airoha can delay acoustic application by 500 ms; an unsolicited state update may precede the
write reply. Readback can temporarily show a stale gain. Preserve those asynchronous updates.

PEQ uses band frequency (Hz), floating gain (dB) and Q. `peq_info` advertises max bands, frequency/gain/Q
ranges and number of quantized steps; step size is `(max - min)/(steps - 1)`. Validate steps before
calculating. Do not assume all Skullcandy EQs are a fixed five-band graphic EQ or reuse native PEQ
coefficients for a protobuf PEQ without evidence. Query configuration/state and preserve filter ids.

Crusher set fields are optional active flag, intensity, mode. If config says intensity is read-only,
a write need not change it. Where modes exist, the schema describes 0 high, 1 medium, 2 low. Apply
those labels only to devices reporting that mode capability. The returned state, not the requested
slider, is authoritative.

Feature provisioning (service 9), Mimi personalization (10), media (15), IMU calibration (18), Dolby
spatial controls (19), audio out (21), diagnostics (100) and streaming (14) are visible in the schemas.
Their presence does not mean every product offers them or that they are safe probe targets.

## 6. Local secure channel

`device.dev_info.authenticated_access` is protobuf field 12. If required, service 25 handles hello,
authenticate and local trust establishment (start/commit/nonce/confirm/abort). The schema describes
user-confirmed numeric comparison, stored peer public identities, signed ephemeral exchange and
negotiated AEAD. The SDK has ECDH and AES-GCM/AES-CCM/ChaCha20-Poly1305 paths. This is local accessory
security, separate from the app's server OAuth/account SDKs.

A future client must generate its own identity, keep its private key in phone-local secure storage,
verify the peer's signature/commitment, and require genuine user comparison/confirmation where the
protocol demands it. Do not copy an app-held private key or replace trust verification with automatic
acceptance. No secure-session implementation or pairing was attempted here. No evidence establishes
that every Skullcandy product requires the secure channel.

## 7. Native code and platform command paths

The arm64 split contains Bragi-adjacent chip SDK dependencies, JieLi auth/update libraries, Airoha
PEQ/ANC libraries, voice/AI libraries and Dart/Flutter. `libapp.so` was extracted; its exported symbols
are Dart VM/isolate snapshot data/instruction entry points, not named setting functions. Nonsecret
snapshot strings expose RACE paths for bass boost, adaptive ANC, wear detection, multipoint, gestures,
Crusher EQ volume, touch controls, spatial/head tracking, LEDs and speaker settings. Strings establish
leads, not command numbers or a model-to-chip map.

`libnative-peq.so` SHA-256 is
`ffa603fecd3e496a12c5b1f541b56b2c83566801f19200db9ed92cc27bb0ee68`.
Exported filter/coefficient symbols were enumerated and `NativePeq_getCofeParam` at `1C2A4` was
disassembled outside the repo. The library exposes Airoha coefficient/filter routines. It must be
checked for parameter/serialization differences before reusing PairFree's existing math. This was
entry-point inspection, not a completed native EQ port or proof of per-model chip selection.

The Java RACE helper uses `05 5A` requests, `05 5B` replies and `05 5D` notifications, length u16 LE
and command id. Its tunneling helper adds a private subheader to another command. The SDK's RACE
transport timeout is fixed at 8000 ms. Follow the Java helper and Dart call sites for a particular
feature; do not infer command ids from debug strings. The Dart snapshot still needs full method/object
recovery for the legacy/custom product routes. It is further local research, not an unreadable-native
blocker and not permission to use firmware/OTA auth code.

## 8. Boundaries and next research

Never probe resets, firmware/voice-pack update, calibration, diagnostics, arbitrary NV/register writes,
feature provisioning, streamed microphone data, time writes, serial/device-id reads, or other-phone
management. Device info needed for handshake contains serial/id fields; redact those before any report.
Tile, Spotify, Mimi accounts and voice/AI cloud features are not ordinary local earbud settings.

Checked: all splits; full manifest/feature inventory; readable Bragi schemas/registry; frame reader,
RPC serializers, status handler, crypto call paths, chip transport helpers; native exports and selected
PEQ disassembly; nonsecret Dart protocol strings; Earshot reference. No vendor endpoint was contacted,
no secrets file was read, no factory/OTA operation or Bluetooth traffic was sent.

Still to analyze: Dart per-model transport configs and custom RACE/GAIA/JieLi payloads; complete local
trust/secure-session transcript; per-firmware action/EQ/preset choices; native coefficient behavior.
Start with the model's complete configuration and safe reads, then validate with actual recordings or
hardware. Neither public reference virtual tests nor a valid checksum justifies marking a product as
tried. No hard-rule blocker was established for reading ordinary local settings.

## 9. The Bragi SDK's own device configurations and its AT dialect (read 2026-11-18)

Source: Skullcandy 5.0.6, `com/bragi/sdk/android/device/config/c.java` (decompiled outside the repo), imported by `tools/import_skullcandy_app.py` into
`data/skullcandy/sdk_devices.json`. **Only ten products have a configuration in the SDK**; the app's other catalogue entries go through other routes (section 10).
Each configuration names the model, its chipset, its serial services and, per feature, the transport that carries it (`FeatureServiceType`: `BOS_AT`, `BOS_RPC`,
`AIROHA_RACE`, `AIROHA_RACE_CUSTOM`, `PLUGIN`; the enum also lists `QCOMM_GAIA`, `BOS_RPC_GAIA` and `PROPRIETARY`, which no config of the ten uses).

| SDK key | Names the app matches | Chipset | Catalogue id (BLE model id) | Features come over |
|---|---|---|---|---|
| GRIND | Grind, Sesh Boost | AB1565 | 0x09 | AT (21 features), Airoha RACE (3: EQ, TWS, update) |
| GRIND_FUEL | Grind Fuel, Sesh Boost Costco | AB1565 | 16 (the catalogue lists 0x63) | the same |
| PUSH_ACTIVE | Push Active, Oppactive | AB1565 | 0x08 | the same |
| CHP200 | CHP200, S6CAW (Crusher ANC 2) | AB1568 | 0x16 | AT (19), RACE (6, with multipoint and the ANC gain) |
| T70 | T70, S2RLW-Q740 (Rail) | AB1562 | 0x21 | RACE (7) and RACE custom (14) |
| T80_PLUS | T80+, S2IPW-P740 (Rail ANC) | AB1562 | 0x15 | RACE (10) and RACE custom (14) |
| T99_PLUS | Method 360 ANC | AB1585 | 0x1D | BOS-RPC (protobuf, 20), RACE (3) |
| T120 | Method 540 ANC | AB1585 | 0x2D | BOS-RPC (21), RACE (3) |
| CHP300 | CHP300, Crusher 1080 ANC | AB1595 | 0x34 | BOS-RPC (25), RACE (4) |
| P6 | Skullcandy P6 (a speaker) | JL7012 | none | JieLi plugin (9) |

The serial services: Grind, Grind Fuel, Push Active and the Crusher ANC 2 use the SDK's `b.a` (`ed0e95a6-8ab5-4642-8654-6c744a37c99b`) with a second service
`b.b` (`351178d6-76dd-4ec3-b939-43f9a11e3617`); the Rail and Rail ANC use Airoha's own (`FFEEDDCC-BBAA-9988-7766-554433221100` on the Rail); the Method 360 and 540
and the Crusher 1080 ANC use `54C5BF4C-C52F-4635-98DC-EEBF97C6726F` (Bragi's RPC service), `EA4F0F8F-89AF-4914-9DFF-AA14E4185810` and, for Airoha RACE,
`A4602B24-F9F1-4AC3-988C-228D764B97A6`. Both ids were computed from the byte arrays in `b.java` (two bytes of each are constants of JieLi's `AttrAndFunCode`, 14 and 17, 21).
The importer leaves the Rail ANC's control service empty where the decompiler wrote it as a variable; that is to be read by hand.

**The AT dialect** (`transport/at`): plain text lines over the serial service, `AT.<GROUP>=<args>` to set and `AT.<GROUP>?` to read, ending with CR LF; a reply is read up to
its boundary and an error is `ERROR`. The app's 81 AT strings, grouped: `AT.UIAUDIO=` (`anc`, `anc_filter`, `transparency`, `atoncall`, `atonpause`, `trackinfo`),
`AT.UI=` (`info`, `get`, `set`, `default`, `used`, `inject`, `invoke`, `notify`, `single_side_mode`: the button/gesture key map), `AT.UIGESTURE`, `AT.UILANGUAGE`,
`AT.UIVOICE=` (`enable`, `disable`, `mode`, `start`, `stop`), `AT.VOLUME=`, `AT.VOLUMEGESTURE`, `AT.AUTOMUTE=` (`sound`, `mute_timer`), `AT.CRUSHER` (`level`), `AT.LATENCY=gaming_mode`,
`AT.MIMI=` (personalisation: `state`, `intensity`, `preset`, `userid`, `version`, `clear`), `AT.FEATURE=` (`count`, `info`, `enable`), `AT.SYSINFO=battery`, `AT.DEVINFO?`,
`AT.TIME`, `AT.LOCATION`, `AT.IMU=calibration`, `AT.BLUETOOTH=` (`advcachedname`, `advcustomname`, `pdlreset`), `AT.IDAM=` (`did`, `peer_did`) and `AT.FACTORY_RESET=default`.
Never send `AT.FACTORY_RESET`, `AT.BLUETOOTH=pdlreset` (clears the paired devices), `AT.IMU=calibration`, `AT.TIME`, `AT.LOCATION`, `AT.IDAM` (device ids) or `AT.UI=inject` and
`invoke` (they run arbitrary UI commands). The full literal list is in `E:\Builds\research\skullcandy-at-strings.txt` (outside the repo); the argument values per command are
still to be read from their callers (`api/ui/data/at`, `api/audio/data/at` and the `anc`, `ancFilter`, `transparency` mappers).

## 10. The other routes: the app's own chip table (read 2026-11-18)

The Flutter code (the Dart snapshot `libapp.so`, run through Blutter outside the repo; Dart 3.11.5, not obfuscated, so class and function names survive) holds the model-to-chip map in
`SKDYProductModelInfo.chip()`, a jump table over the model enum; `tools/import_skullcandy_dart.py` writes it to `data/skullcandy/chips.json`. The chip enum is `SKDYProductChip`
(qualcomm, airoha, bes, actions, jieli, notSupported). Of the 56 models:

- **Airoha, 41**: all the Dime, Sesh, Mod, Riff, PLYR, SLYR, Icon, Aviator, Hesh, Crusher PLYR, Push 540/720 Open, Crusher 720/1080/900, Session and Method models, the Rail, Grind and
  Push Active, Crusher ANC 2, and the BLE+Classic twins (`indyANC`, `indyFuelRC`, `indyEvoRC`, `crusherEvoRC`, `crusher540ActiveRC`). The app drives them through a native channel
  (`com.skullcandy.api/airoha`: `SKDYAirohaApi.runATCommand`, `runRaceCommandEncoded`, the Mimi calls) into the Bragi SDK's AT, RACE and, on the newest, BOS-RPC transports (section 9).
- **Qualcomm, 6**: `crusherANC`, `crusherEvo` (the BLE entries), `indyFuel`, `indyEvo`, `pushUltra`, `crusher540Active` (BLE): the older models, controlled over Bluetooth LE.
- **BES, 5**: `icon180`, `dime540ANC`, both Smokin' Buds 360 (hanger, stick) and `push180Open` (the app's `bes` package and the BES SDK).
- **JieLi, 1**: Smokin' Buds 3. **Actions, 3**: the Terrain speakers (speakers are held back).

So the work divides by chip, not by model: one Airoha implementation (AT dialect plus RACE for the equaliser, multipoint and similar, plus BOS-RPC for the newest) covers most of the
catalogue, once the AT arguments and the per-model feature lists are read; the manifest's per-model features (`MODELS.md`) say what each shows.

### 9.1 AT framing and the first commands read (2026-11-18)

Read from the SDK's shared AT helpers and the audio request providers (`api/audio/data/*/requestProvider`):

- A request is the text line plus CR LF (`AT.UIAUDIO=anc,on\r\n`), UTF-8, over the serial service. A reply is read up to its boundary, split at `\n` (a trailing `\r` is dropped) and
  accepted when it contains `OK`; `ERROR` is a failure, and `NO CARRIER` means the device is gone. A value is the text after the first `:` of a reply line. The exact reply line shapes for
  each group are still to be read (only the notification literals `.UIAUDIO:trackinfo`, `.UI:notify`, `.UIVOICE:` and `.AUTOMUTE:` were found as strings).
- Noise cancelling: read `AT.UIAUDIO=anc`, write `AT.UIAUDIO=anc,on|off` (the SDK's state enum is `ON`, `OFF`, written lower case). ANC filter strength: read `AT.UIAUDIO=anc_filter`, write
  `AT.UIAUDIO=anc_filter,<n>` (`off` for off). Transparency: read `AT.UIAUDIO=transparency`, write `AT.UIAUDIO=transparency,on|off`, and its level as a percent, `AT.UIAUDIO=transparency,,<0 to 100>`
  (the SDK multiplies its 0 to 1 gain by 100). Volume: `AT.VOLUME=up,<steps>` and `AT.VOLUME=down,<steps>`. The button map, voice, language, gaming mode, auto mute, Crusher and Mimi forms follow in 9.3 and below.
  mode, auto mute, Crusher and Mimi values.

### 9.2 Reply shapes read so far (2026-11-18)

Replies are lines of `<prefix>:<value>` (the prefixes the SDK recognises include `.DEVINFO`, `.UI`, `.SYSINFO`, `.SYSTEM`, `.EAUDIO`, `.MODE`, `.BLUETOOTH`, `.FEATURE`, `.LICENSE`, `.LOCATION`)
followed by `OK`:

- Noise cancelling (`AT.UIAUDIO=anc`): the text after the colon is `on` or `off`.
- ANC filter (`AT.UIAUDIO=anc_filter`): a line containing `off`, else the number after the colon.
- Transparency (`AT.UIAUDIO=transparency`): `on|off` and, after a comma, the level as an integer percent.
- Battery (`AT.SYSINFO=battery`): the first line after its colon is split at tab characters into `single:`, `local:`, `right:` and `left:` fields (an integer each); `single` is used, else `local`,
  for the case-less or one-earbud models (the SDK keeps left, right and single/local).
- Language (`AT.UILANGUAGE?` and `=language,<value>`), time (`AT.TIME`, never sent), gaming mode (`AT.LATENCY?`, `=gaming_mode,<on|off>`), Crusher (`AT.CRUSHER?`, `=<on|off>`,
  `=level,<n>`) and auto mute (`AT.AUTOMUTE?`, `=<on|off>`, `=mute_timer,<n>`, `=sound,<x>`) follow the same shapes; their exact value sets are still to be read.

`data/skullcandy/devices.json` (from `tools/import_skullcandy_manifest.py`) now holds all 56 products: id, model names, Android connection types, chip, SDK configuration where one exists, each firmware's
feature list and the button settings. Of the Airoha models, the features by count are the defined equaliser (34), custom equaliser (33), multipoint toggle (26), clear voice (24), custom buttons (21),
stay-aware level (20), personal sound (19), voice assistant (19), Tile (16), bass boost (16), low latency (15), noise-cancelling level (10) and sidetone (10).

## 11. The app's RACE command classes (read 2026-11-18)

The Flutter code holds 58 files of RACE command classes and helpers (`utils/race_commands/`; 46 of them write or check literal byte strings: ANC, battery saver, charge-out protection, Crusher bass boost and EQ volume, feedback sounds, button and gesture
settings, LED colour/brightness/mode, Mimi user id, multipoint, party mode, wear detect, serial number and SKU reads, SG200 EQ mapping, speaker bass boost, THX spatial audio and head
tracking, capacitive touch, voice control). `tools/import_skullcandy_race.py` writes the byte strings each writes or checks to `data/skullcandy/race_commands.json`: requests are RACE
`05 5A <length u16 LE> <command id u16 LE> <payload>`, replies `05 5B` with the same id and a status byte (`00` is success: `RaceCommandBasBoostSet` checks the byte at offset 12 against `00`),
notifications `05 5D`. Some commands are Airoha's own (`05 5A 05 00 06 0E 00 22` for the adaptive ANC, `05 5A 06 00 00 0A ...` for the SKU read) and some are Skullcandy's custom ones
in the `2C xx` range (`D0 2C` Crusher EQ volume set, `D1 2C` get, `D7 2C` bass boost set, `D8 2C` get, `81 2C` feedback sounds, `82 2C` multipoint) or NV key reads and writes (`0A 00` with a
key id such as `F4 03`, `F4 05`, `F4 02`). The values each command writes are built at run time; reading them class by class is the next step (the response offsets are in each
`decodeResponse`). JBL's Airoha RACE code (`RacePacket`, `RaceParser`, `JblRace`) already speaks the framing, so this brand reuses it; the NV writes follow the owner's rule for
vendor-exact NV writes (2026-11-04).

### 9.3 The AT button map and the other groups (read 2026-11-18)

- Button and gesture assignments (`api/ui/data/at/h.java`): `AT.UI=info` (what the earbuds offer), `AT.UI=get,<side>,<key>.<gesture>` and `AT.UI=set,<side>,<key>.<gesture>,<command>`,
  `AT.UI=default,<side>,<key>.<gesture>` (restore), `AT.UI=used,<side>,<key>.<gesture>` (is it assigned). Keys: `CONTROL`, `POWER`, `CENTR`, `UPPER`, `LOWER`, `ROCKR`, `ROTOR`, `BUTTON0` to
  `BUTTON5` (each model's configuration maps its own names, e.g. `BUTTON0` to `swt_c` on the Crusher 1080 ANC); gestures: `UP`, `CLICK`, `CLICK2`, `CLICK3`, `CLICK4`, `HOLD`, `HOLD2`, `HOLD3`,
  `CLICK_HOLD`, `CLICK2_HOLD`, `HOLDN`, `DOWN`, `HOLD_UP`, `HOLD2_UP`, `HOLD3_UP`, `CLICK_HOLD_UP`. The commands are the SDK's `Command` list (about 100: `ANCAT_TOGGLE`, `ANCAT_ANC_ON`,
  `MEDIA_PLAY_TOGGLE`, `VOLUME_UP`, `ASSISTANT_TOGGLE`, `SPOTIFY_ONE_TOUCH`, `BT_PAIRING`, `CRUSHER_TOGGLE`, `SIDE_TONE_TOGGLE`, `SPATIAL_TOGGLE`, `EQ_CYCLE` and others; never `DEVICE_FACTORY_RESET`,
  `BT_PDL_RESET`, `DEVICE_POWER_OFF` without a confirmation). The command-to-AT-name table is in a lambda switch (`api/ui/data/at/mapper/a.java`), still to be read; each model's own list of
  mappable actions is `mappable_buttons` in `data/skullcandy/devices.json`.
- Voice (`AT.UIVOICE`): `?`, `=enable`, `=disable`, `=mode[,<mode>]`, `=start,<type>` (`spp` for the phone's assistant) and `=stop`. Gaming mode: `AT.LATENCY?` and `AT.LATENCY=gaming_mode,<on|off>`.
  Language: `AT.UILANGUAGE?` and `AT.UILANGUAGE=language,<value>`. Feature table: `AT.FEATURE=count`, `=info,0,<n>`, `=enable,<n>[,<m>]`.
The write form of the button map (`api/ui/data/at/h.java`): `AT.UI=set,<side>,<key>.<gesture>[,<event>[,<event>...]]`, the events being the firmware's names (`HEvents.fwName`, else the enum name in lower case with its
first underscore turned into a dot: `ancat.toggle`, `media.play_toggle`, `volume.up`, `telephony.take_end`, `assistant.toggle`, `bt.pairing`, `device.power_off` ...). The earbuds answer a physical press with an
unasked `.UI:notify,<side>,<key>.<gesture>[,<event>]` line, and `AT.UI=notify,off|pair` switches those notices. The HEvents list: none, ancat toggle, remote notify/up/down/center, telephony take/reject/end/
reject-end/take-end/switch-call/mute-toggle, media play-toggle/next/prev/play/pause/reject, volume up/down/repeat-start, ancat anc-toggle and at-toggle, assistant play-sound/toggle/alexa-toggle, bt pairing and
passcode accept/reject, PTT toggle/start/stop, recording toggle/start/stop, device power on/off.

### 11.1 What the RACE commands are (2026-11-18)

With the byte strings in full (literals the code loads into any register, and the `buildCommand` of each class run for each of the 56 models by `tools/import_skullcandy_race.py`; the file's
`built_by_model` groups the models by the bytes they get), the commands fall into three kinds, in the `05 5A <len u16 LE> <id u16 LE> <payload>` framing:

- **Airoha's own RACE** commands: `05 5A 05 00 06 0E ...` (adaptive ANC), `05 5A 04 00 01 09 1F 00` (adaptive ANC read), `05 5A 02 00 41 00` and `43 00` (serial number and SKU, never reported).
- **NV key reads and keyed writes.** A read is the NV read command, `05 5A 06 00 00 0A <key u16 LE> 01 00` (command id `0A 00`, then the key and a length of one), and its answer comes back as a
  `05 5B` frame; the keys read in the files are `F4 02` (wear detect), `F4 03` (charge-out protection), `F4 05` (feedback sounds), `F4 09` (party mode), `F5 00` (multipoint, the
  `F5 00` form) and `F5 14` and `F5 23` (the capacitive touch controls). A write is a direct command whose id is the key: wear detect `05 5A 03 00 02 F4 <v>`, charge-out protection `03 F4 <v>`,
  feedback sounds `05 F4 <v>`, battery saver `05 5A 03 00 23 00 <v>`, speaker bass boost `22 00 <v>`, touch `07 00` and `08 00`, voice control `01 00`. These keyed commands are the vendor's own
  and fall under the owner's rule for vendor-exact NV writes (2026-11-04) once each is confirmed against its class.
- **Skullcandy's custom commands**: `2C 81` feedback sounds, `2C 82` multipoint (`05 5A 05 00 82 2C 10 00 <0|1>`), `2C 83` LED state, `2C 85` LED mode, `2C 86` LED brightness, `2C 88` LED customiser,
  `2C D0`/`D1` Crusher EQ volume, `2C D7`/`D8` bass boost; and Skullcandy's THX group at `F4 01`. Which form a model gets can depend on the model: the multipoint switch is the NV form
  `05 5A 03 00 01 14 <0|1>` on the Hesh 360, Crusher Evo and Crusher 540 Active classic twins and the Session 360 and 540; `05 5A 04 00 01 11 BB|BC 00` on the Dime Evo, Dime 360, Rail, Rail ANC and
  Session 720; and the custom `2C 82` form on the rest.

## 12. What the non-Airoha routes hold (2026-11-18)

From `data/skullcandy/devices.json` (the manifest's firmware feature lists) and `data/skullcandy/chips.json`:

- **Qualcomm, Bluetooth LE only (6): Crusher ANC, Crusher Evo (0x02), Indy Fuel, Indy Evo, Push Ultra, Crusher 540 Active (0x2C).** Their features are a preset equaliser and its toggle, ambient mode on the two Indy models, personal sound and
  Tile. The Crusher Evo (0x02) and Crusher 540 Active (0x2C) have classic twins on Airoha (0x30, 0x31: `airohaProductId` in the manifest). The BLE protocol is the oldest and the one nothing in the Bragi SDK covers.
- **BES, classic (5): Icon 180, Dime 540 ANC, Smokin' Buds 360 H and S, Push 180 Open.** The richest of the non-Airoha models: custom and preset equalisers, bass boost, multipoint, low latency, custom buttons,
  stay-aware and sidetone levels, and on the Dime 540 ANC adjustable noise cancelling and personal sound. The app's `bes` package carries BES's TOTA transport (SPP or BLE) with an optional encryption layer the app switches on
  with a `BES_TOTA_ENCRYPTION_KEY` flag (a Boolean read from the app's configuration, defaulting on; the cipher object itself is in `bessdk/service/base/b.java`): to be read in full before it counts as covered.
- **JieLi (1): Smokin' Buds 3, and Actions (3): the Terrain speakers.** The manifest gives them only a product FAQ, no setting, so there is nothing to control; the speakers are held back in any case.

So the settings a Skullcandy owner can change come from 41 Airoha models, 5 BES models and 6 older Qualcomm BLE ones (preset equaliser, ambient mode); the Airoha work comes first because it holds most of them.

### 12.1 BES: the TOTA command classes (2026-11-18)

The Flutter code has 20 BES TOTA command classes (`utils/tota_commands/`), written to `data/skullcandy/tota_commands.json` by `tools/import_skullcandy_race.py --tota`. Each request starts `09 63 <length u16 LE> <command u16 LE>` for a read
and `08 63 <length> <command> <value>` for a write; the command numbers: 1 battery, 2 equaliser mode, 3 custom equaliser (a write is `08 63 21 00 03 00` and 30 bytes of bands), 4 button settings, 5 multipoint, 6 low latency, 7 serial
number (private), 8 firmware version, 9 hearing mode, `10` sidetone, `11` touch control, `12` TWS status (the last three written in hexadecimal by the app: `10 00`, `11 00`, `12 00`). The framing and the encryption are in sections 12.2 and 12.3, the layouts of every command in section 12.4.

### 12.2 BES TOTA encryption: a handshake, no key held by the app (read 2026-11-18)

Checked: `app/skullcandy/bes/i.java` (the connection handler), `bessdk/service/base/d.java` (framing and cipher) and `bessdk/connect/`. The optional layer is not a key the app carries. When TOTA encryption is on
(a Boolean the app reads from its configuration, `BES_TOTA_ENCRYPTION_KEY`, default on; the configuration file itself, `secrets`, stays closed by the hard rule) the two sides derive a fresh key at connect:

- Each side sends a buffer of its own (the host's is `dVar.b`, the earbuds' arrives in a `02 10 <length u16 LE>` message). The two buffers are combined byte by byte, the operation chosen by the index modulo 6: add,
  multiply, AND, OR, XOR, and the sum of both squares (a sixth case gives 0), each as a byte.
- The key is the SHA-256 of the combined bytes; the 16 bytes at the even positions of the hash (0, 2, ... 30) are the AES-128 key. The cipher is AES/CBC without padding (data padded with zeros to 16 bytes) and the fixed
  initialisation vector `00 01 02 ... 0F`; the frames also carry a CRC-32 (little endian) and the markers `HEAD` and `TAIL`.
- The host confirms with `03 10 21 00 20` and the 32-byte hash; the earbuds answer `02`/`10` ... with the ASCII `suc` at offset 12 when it holds, after which encryption is on (the app sets its flag in `h()`).

So BES TOTA is implementable from public behaviour, with no vendor key or credential. The byte layouts of the handshake messages and of the frames are in section 12.3.

The TOTA v2 frame (`d.java` `a` writes, `b` reads), as the code builds it: `HEAD` (the ASCII marker), the payload length as four bytes little endian, the payload, the payload's CRC-32 as four bytes little endian, `TAIL`. A frame that fails
its CRC is dropped (the reader returns a one-byte `01`). With encryption on, the payload is the AES-CBC ciphertext of the zero-padded content. The 20 command classes (section 12.1) supply the payloads: `09 63 02 00 <cmd> 00` to read and
`08 63 <length> 00 <cmd> 00 <value>` to write.

### 9.4 How the app reaches the Airoha models without an SDK configuration (corrected again 2026-11-20, issue #66)

An earlier version of this section said the Bragi AT lines travel inside the RACE command `01 0D`; `0D01` is Airoha's RACE *relay* command, so that was wrong. The version of 2026-11-18 then said the app's own AT client
(`app/skullcandy/bragi/h.java`, the standard serial port `00001101-...`) reaches the 28 Airoha models without an SDK configuration. **That was wrong too.** A user's Dime Evo (issue #66) lists no standard serial port at all
(its services: `00000000-0000-0000-0099-aabbccddeeff`, Google Fast Pair's `df21fe2c-...` and the audio profiles), and every socket to `00001101-...` failed. What the app's code shows, read again:

- The AT client is built only when `SKDYProductModelExtensionsKt.getShouldUseATCommands` is true (`s2.java`, `buildATCommandAPi`: "this model does not support AT commands"), and that is true for four models only: the Crusher ANC 2, Push Active, Grind and
  Grind Fuel, the four whose SDK configurations are AT (section 9).
- Every other Airoha model is driven through Airoha's SDK (`com.airoha.sdk`): the Flutter channel's methods `getANCSettings`, `getEQSettings`, `getBatteryInfo`, `getLowLatencyMode`, `getGestureSettings` and the rest (`AirohaChannelMethod`).
  The SDK connects over Airoha's serial service `00000000-0000-0000-0099-AABBCCDDEEFF` (`com.airoha.liblinker.constant.c.b`; `s2.java` passes it as the `ConnectionUUID` for every model but the Method 360 and 540 ANC,
  which get `A4602B24-...`), and `z4.v` checks a device's services for that UUID.

So PairFree drives these 28 models as it drives the Rail (section 16): `SkullcandyRaceTalk` is their main conversation (`primary`), over the Airoha service the device lists (`AIROHA_SPP` first, as the app connects,
else `FFEEDDCC-...`), with the batteries, the hearing mode (the module read and `0E06`, the AB1562 forms), Skullcandy's custom RACE commands (multipoint, wear detection, bass boost) and the equaliser. Added for them:
**gaming mode**, as Airoha's SDK does it: read as module 6 of the module read (`0901`; `libmmi1562.stage.b`, "GetGameMode"), set with the key event `1101` and `A4 00` (on) or `A5 00` (off) (`libmmi1562.c.j`, "SetGameMode").
The answer's parser did not decompile, so that its first data byte is 1 for on and 0 for off is inferred. The Rail's own buttons, voice control and gain stay the Rail's (they come from its SDK configuration).
The buttons and the voice prompts are section 9.5. Nothing here has met a real product; the next report from issue #66 confirms or corrects it.

### 9.5 Buttons and voice prompts of the Airoha models without an SDK configuration (built 2026-11-20)

Twelve of the 28 list `customButtonActions` with the presses and actions of the manifest (`default_buttons`, `mappable_buttons`). The app's native side has two routes (`FlutterAirohaApi`, methods
`getGestureSettings`, `setGestureSettings`, `resetGestureSettings`), and `data/skullcandy/buttons.json` holds the tables of both:

- **Airoha's own gesture API**, for the Mod, Push ANC Active, Sesh ANC Active, Dime Evo, Dime 360, Push 720 Open, Push 540 Open and the Crusher 900 ANC: `getGestureStatus(255)` and
  `setGestureStatus(List<AirohaGestureSettings>)`. The app maps its presses to Airoha's gesture ids (single press 1 and 2, double 3 and 4, triple 7 and 8, press and hold the long press release 11 and 12,
  on the Mod the long press 5 and 6; odd left, even right) and its actions to Airoha's action ids (Spotify 212, the stay-aware switch 5, or 10 on the Sesh and Push ANC Active, play/pause 8, previous 7, next 6,
  volume down 2 and up 1, take photo 240, voice assistant 160, none 0; read back, 209 is the firmware's default, shown as the manifest's default for that press). Airoha's SDK then writes the key map as
  JBL's Airoha models do (`JblAirohaGesture`: NV key `F2D6` on the AB158x, `F2E7` on the older chips, the copy of the other earbud's in `F2EF`, the reload, all relayed to the other earbud; the AB1562's key
  map commands `0A0B` and `0A0A`), but from Airoha's own encoders, keyed by Airoha's action ids: `libmmi158x.Utility.a` (play/pause 83 and 85, Spotify 330, take photo `F001`, and so on), `libmmi.Utility.a`
  (the same without Spotify and take photo, for which it has no event, so on those chips the rows list only the actions it writes) and `libmmi1562.Utility.a` (its own events, the tap as key type 0, and a state bit mask
  per event from `libmmi1562.model.a.b`). The default key id is 24 (18 hex), then the one the read found. The Crusher 900 ANC's manifest also offers cycling the EQ and spatial audio, which have no Airoha
  action id: the app's own `setGestureSettings` rejects them for this product, so PairFree offers its voice assistant and none.
- **The app's own key map code**, for the four products `getHasVendorSpecificButtonId` names (Icon ANC, Aviator 900 ANC, Hesh 540 ANC, Crusher 720): the native side reads NV key `F2D6` and hands the
  bytes to Dart, which takes the entries of the product's button (`keyId` 82 on the Icon ANC, 7E on the others) by key type (single press 1, double 2, triple 3, press and hold 10;
  `SKDYButtonSettingsUtils.toKeyGestureStruct`) and reads the actions from their events (`SKDYActionInfo.fwActionId`: Spotify 330, the stay-aware switch 146, play/pause 83 and 85, volume 10 and 11,
  take photo `F001`, voice assistant 33 and 35, none 0, cycle the EQ 220 hex, head tracking `F402`, spatial audio `F401`). The write is the command `F2D6` with the entries (`SetGesturesRaceCommand`,
  `05 5A <length> D6 F2 <entries>`). PairFree reads the key with the SDK's own NV read (length 100; the app's literal `s2.f0` carries a length field one short of its bytes) and, on a change, replaces only
  that press's entries of that button and writes every other entry back as it read it. That the app writes these four this way is read from its encoder and the Dart helpers; the call that sends the write
  was not found in the Dart snapshot (checked: `skdyairohaapi.dart` `setButtonSettings`, which calls the native `setGestureSettings`, and the callers of `SetGesturesRaceCommand`).
- **Voice prompts** (`voicePrompts`, the two SLYR Pro Wireless): Skullcandy's command `2C 81` from the native side (`s2.W`, `X`, `Y`): `05 5A 04 00 81 2C 02 FF` reads, `... 00 FF` turns them on and
  `... 01 FF` off; the answer's last byte is 1 when they are off, and the earbuds report a change as `05 5D 03 00 81 2C <value>`.

### 9.6 Sidetone, the mixes, touch controls and the rest (built 2026-11-20)

More of the Airoha models' settings are Skullcandy's own RACE commands, carried by the Airoha SDK inside the app (`com.airoha.libmmi.b` and its `libmmi1568` stages) and by the Flutter code's race command classes:

- **The custom pair `2C82` (write) and `2C83` (read)**: a setting number (two bytes, low first) and the value. A read is `05 5A 04 00 83 2C <n> 00`, answered `05 5B <len> 83 2C 00 <n> 00 <value>` (a status byte, the number, the value; a level is a short, low byte first). A write is `05 5A <len> 82 2C <n> 00 <value>`.
  Settings: 7 sidetone on or off (1 is on; also an unasked notice `05 5D 04 00 83 2C 07 <v>`), 6 the sidetone level, 0B the game and chat mix (`MmiStageSetGameChatMixRatio`), 0C the microphone volume (`MmiStageSetGameMicVolume`), and 10 multipoint (section 11).
  The app writes the sidetone state and then the level on every change (`doSetSidetoneStatus`), and PairFree does the same. The level is the app's slider (`SKDYSidetoneSettings`): `truncate(percent / 100 * 57) - 40`, so the wire holds -40 to 17; read back, `(level + 40) * 100 / 57`.
- **Touch controls** (`capTouchControl`, the Push 540 Open): the NV key `F514` (35 bytes) read as `05 5A 06 00 00 0A 14 F5 23 00`. Its first data byte is `00` when the touch controls are on (`RaceCommandCapTouchS2Get`) and the third is `01` when scenario 1 is on (`S1Get`). The app first makes sure scenario 1
  is on (`_ensureCapTouchScenario1IsEnabled`: `05 5A 03 00 07 00 01` when it is not), then writes scenario 2 as `05 5A 03 00 08 00 <00 on, 01 off>`. PairFree does the read without a write, and the two writes only when the user changes the row.
- **Adaptive noise cancelling** (`adaptiveANCMode`, the Aviator 900 ANC): the module read `001F` (`05 5A 04 00 01 09 1F 00`, the first data byte 1 is on) and the ANC command `05 5A 05 00 06 0E 00 22 <00|01>`.
- **The Crusher PLYR's intensity** (`crusherTechnology`): `05 5A 03 00 D0 2C <value>` writes it and `05 5A 02 00 D1 2C` reads it (`05 5B 04 00 D1 2C 00 <value>`). Skullcandy's own bass boost toggle (`D7 2C`) is section 11.

**Inferences, to be confirmed by a first report:** the ranges of the chat mix, the microphone volume and the Crusher intensity (the app's sliders run 0 to 100; PairFree offers tenths of that and writes the percent), which side of the sidetone's notice carries the value, and that `2C82` writes need no
reply to be taken (the app waits for the status byte).

**Open items for these models (to build; checked: the Airoha SDK's classes and the Flutter feature switch, as each item says):**
- *The noise mode's own writes on these models* (section 9.7): they still use the Rail's filter numbers (3 and 4), a guess for the others; the app's are ANC1 (1) for noise cancelling and, for transparency, `passThruFilterId` through the SDK's table (9 to 11 on every chip), with a mode byte that the app takes from a
  per-model configuration object (field 0x47 of the product, three flags) that is not traced. Blocked until that object is read: **blocked: the mode byte of `0A` is `SKDYAncMode.ancMode`'s result (0, 2, 8, 12 or 18 by model, feature and those flags); checked: `skdyhearingmodes.dart` (the function's branches by product index and feature index) and the SDK's stages (`libmmi1562.stage.c`, `libmmi.stage.c`), which write whatever byte the app gives.**
- *THX spatial audio, head tracking and intensity* (`thxSpatialAudio`, `thxHeadTracking`): Skullcandy's command classes `ReadThx*` and `SetThx*` (ids `000A` to `0011` in the race text format, built by `encode`); their payload layout is not traced.
- *Clear Voice smart mic, bass boost mode, the equalizer on/off switch:* nothing to build. **Checked 2026-11-20:** `clearVoiceSmartMic` appears only in its feature guide screen (no control) and the asset table; `bassBoostMode` is a capability flag in a `hasFeature` list of the equalizer screens (the bass boost preset is an equalizer category, section 13); `definedEqualizerToggle` is a firmware feature name that is not in the app's `SKDYFeature` enum, and no `activateFeature` case uses it.
- *Enhanced sound perception:* the Mimi personal sound service (`skdymimiservice.dart`) and Bragi's, so **blocked: it needs Mimi's server sign-in (hard rule); checked: `skdyairohaservice.dart`, `bragiservice.dart` and `skdymimiservice.dart` reference it, and the Airoha service only routes to Mimi.**
- *THX spatial audio and head tracking (still open):* `SetThxSpatialAudioRaceCommand` and its head tracking and intensity siblings list the race ids `00 0A` and `00 0B` (`supportedRaceIds`) and a `hasEnabledPayload` check on the answer; the frame's header and payload come from a shared base class not opened yet.
- *The LED customizer, Crusher technology's dial, wheel and profiles, the Crusher 900 ANC's Bose equalizer, the PLYR's gaming equalizer and its NV key `F510` order, the THX gaming equalizer, cap touch's Indy ANC ambient mode:* not traced yet from the app's feature switch (`activateFeature` in `skdyairohaservice.dart`, whose per-feature cases are decoded one at a time).

### 9.7 The strength of noise cancelling and transparency (built 2026-11-20)

The app's two sliders (`adjustableNoiseCancellation`, `adjustableStayAwareMode`) turn a percentage into dB by `convertPercentageToAirohaGain` (`percent * 20 / 100 - 20`, clamped to -20 to 0 dB; the same function for both modes) and pass it to the Airoha SDK's
`setAncSetting(filter, gain, mode, save)` (`FlutterAirohaApi.setANCSettings`, `doSave` defaulting to true). The SDK keeps a noise cancelling gain and a transparency gain and writes the active one:

- **The gain**: the ANC command `0E06` with `00 0F`, the dB times 100 as a short (low byte first) and, on the AB1562 only, a trailing `01` (`libmmi1562.stage.j`); on the AB1568 and later the stage (`libmmi.stage.k`) adds a sync byte only when the sync mode is not -1, which the app never sets.
  So `05 5A 06 00 06 0E 00 0F <lo> <hi>` (and `05 5A 07 00 06 0E 00 0F <lo> <hi> 01` on the AB1562). PairFree sends the gain alone, which the app's `f0` (filter) then `M` (gain) sequence ends with; it waits for the chip name, since the trailing byte depends on it.
- **The state**: NV key `E0E0`, 12 bytes (`05 5A 06 00 00 0A E0 E0 0C 00`): a flag for on, the SDK's filter, two bytes unused, the noise cancelling gain and the transparency gain (dB times 100, low byte first) and the mode as an int. The slider shows the gain of the mode in force.
- **The filters**: the SDK's raw filter numbers are, on every chip, ANC1 to ANC4 = 1 to 4 and pass-through 1 to 3 = 9 to 11; from the AB1568 on hybrid pass-through 1 to 3 = 5 to 7 (`s`, `i0` tables), and on the AB158x vivid pass-through 1 to 3 = 12 to 14.
  The module read of the hearing mode therefore also counts 5 to 7 and 12 to 14 as transparency on these models (the Rail's own are 3 and 4, section 16).

**Not done:** the app also saves the state to NV key `E0E0` after each change (its stages `f` and `o`, then a relay to the other earbud); PairFree sets the gain live only, so a gain set here is lost when the earbuds power off. **Open: the NV write goes through the SDK's `mmi.a` packet class,
whose frame was not opened.** The scale of the transparency slider is the same function as noise cancelling's (read in `adjustablestayawaremodesliderstate.dart`); that the earbuds apply a `0F` gain to the active filter, whichever it is, is inferred from the SDK's single gain stage, not recorded.

## 13. The equaliser on the Airoha models (read so far, 2026-11-18)

The Bragi SDK drives the equaliser through Airoha's own PEQ control (`api/equalizer/data/protocol/airoha`: `PEQControl.setEQSetting(categoryId, payload, ...)`, `getAllEQSettings`, `getRunningEQSetting`),
so the presets are categories the earbuds hold (the SDK names them per product: `{1 Music, 2 Podcast, 3 Movie}` on the Grind, Grind Fuel, Push Active, Crusher ANC 2 and Rail, and `{1 Music, 2 Bass Boost, 3 Podcast}` on the Rail ANC; each with a
gain range of -12 to +12 dB) and the custom curve is category 0 written with a payload. This is the same mechanism JBL's Airoha earbuds use (`protocol.airoha.AirohaPeq`, `JblAirohaEq`), whose coefficients PairFree computes from a port of Airoha's
`libnative-peq.so`; the Skullcandy app carries that library too (`libnative-peq.so`, SHA-256 `ffa603fe...`), so the same flow (NV keys, relay, subsets) is the candidate for Skullcandy's custom curve. **The library is a different build from JBL's, but it computes what the port's second variant computes** (checked 2026-11-18): Skullcandy's (SHA-256 `ffa603fe...`) takes the Q itself as the band's third value (JBL's takes a bandwidth in Hz) and its first comparator is strict (`compare1` is `-1`, `0`, `1` by an exact comparison of the gains, where JBL's truncates the difference to an int), which is `libnative-peq-serialize.so`'s behaviour, i.e. `AirohaPeq.Library.SERIALIZE`. Run under the same emulator (`E:\Builds
esearch\jbl-native`, `PEQ_LIB=libnative-peq-skullcandy.so`), `peqport.generate(..., q_given=True, strict=True)` equals the library's `getCofeParam` shorts on 60 of 60 random ten-band curves (44.1 and 48 kHz, random master gains, plain arithmetic). The coefficients of Skullcandy's custom curve therefore need no new port: `AirohaPeq.coefficients(fs, bands, masterDb, Library.SERIALIZE)`. What remains for the equaliser is the write flow per chip (NV keys, relay, subsets: JBL's `JblAirohaEq` is the model), the band layout per model and the preset categories.
counts and frequencies per model (the app's `SKDYProductModelInfo.ceqRange`, `customEqualizer4x` on the Crusher PLYR 720), the preset category numbers of the models the SDK has no configuration for.

### 13.1 The custom curve, as the app sends it (read 2026-11-18)

From `app/skullcandy/s2.java` (`setCustomEQBands`) and the Dart `SKDYProduct` (the custom equaliser index defaults to **101**): the Flutter side hands the native side a list of gains, the index (101), a save flag and a master gain; the native side builds Airoha's
`AirohaEQPayload` and calls `setEQSetting(index, payload, save, callback)`: **band type `BAND_PASS` (Airoha's peak), Q 0.91 for every band, sample rates 44100 and 48000, the master gain as given**. The bands' frequencies by model:

| Bands | Frequencies (Hz) | Models |
|---|---|---|
| 5 | 60, 250, 1000, 4000, 12000 | every other model |
| 7 | 60, 150, 400, 1000, 2500, 6000, 15000 | Session 720 and 900 (speakers) |
| 10 | 32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000 | PLYR, both SLYR Pro, Crusher PLYR 720 and its Xbox model |

The gain range (`SKDYProductModelInfo.ceqRange`) is -12 to +12 dB for most models and -6 to +6 for the rest (one model's is 0 to +20). The presets are the categories the earbuds hold (the SDK's names `Music`, `Podcast`, `Movie`, or `Music`, `Bass Boost`, `Podcast`, by number from 1).
The write flow is Airoha's own (JBL's, section 11.2 of its doc): the code of `JblAirohaEq` (internal to the protocol module) writes it for the chips AB155x, AB1562, AB1568 and AB158x once it takes the PEQ library's variant as a parameter (`AirohaPeq.Library.SERIALIZE` for Skullcandy:
the band's third value is the Q, and the comparator is strict) and the category 101, the first custom slot. The chip is read from the earbuds over RACE, as JBL's session does.

### 13.2 What PairFree builds of it (2026-11-18)

`SkullcandyRaceTalk` (the RACE channel's conversation: the chip read, Skullcandy's custom commands and the curve's write, with the steps of `JblAirohaEq` taking `AirohaPeq.Library.SERIALIZE`), `SkullcandyEq` (bands, range, Q 0.91, category 101) and `SkullcandyBandEq` (the Sound tab's
bands, five, seven or ten, with the app's gain range: 12 dB on the models the SDK configures and the gaming headsets, 6 dB on the rest). The curve is shown once the earbuds have named their chip. PairFree offers the custom curve and the earbuds' own presets (categories 1 to 3), and reads back what they hold (section 13.4). The Crusher 1080 ANC's equaliser (a Bose curve, range 0 to +20) goes with its RPC session, to do.

### 13.4 What the earbuds hold: the read-back (built 2026-11-19)

Airoha's SDK reads the running equaliser (`PEQControl.getRunningEQSetting`, `u0` and `AirohaPeqMgr`) with the stages JBL's write already knows: the audio path and the PEQ subset (NV keys, `PeqStageReadAudiPath`, `PeqStageReadPeqSubset`), the running group (`PeqStageGetPeqGrpIdx`: the module read `0901` for module 0, whose answer
is the 1-based index of the group in the subset; read from the app's smali, since the decompiler gives up on that method) and, when the group is a custom one (category above 100), the band data of its NV key (`PeqStageLoadUiData`: key `EF00` plus the slot; `PeqUiDataStru`, five header bytes, then ten bands of 18: enable, type, and the frequency,
gain, bandwidth and Q as hundredths). `JblAirohaEq.readBack` runs those steps once the chip is named, and `SkullcandyRaceTalk` turns the answer into the preset's name (the group's number is the app's mode number) or the curve's gains at PairFree's bands (a band the earbuds do not hold reads 0 dB), shown on the Sound tab and the Home card until PairFree writes
a curve of its own. Checked against synthetic answers in `SkullcandyRaceTalkTest`, not against earbuds.

### 13.3 The presets: the app's equaliser modes by number (read 2026-11-18)

The app's own table, `SKDYEqualizerModeUtils.valueForFeature(feature, product)` (the Dart function run for every product by `tools/import_skullcandy_eq_modes.py`, into `data/skullcandy/eq_modes.json`), gives each product the modes it shows and the number each is sent as; the earbuds hold the preset curves as groups
and the app picks one with `setEQSetting(number, null)`:

| Models | Modes (number) |
|---|---|
| Indy ANC, Dime 2, Dime XT2, Sesh ANC, Mod, Riff Wireless 2, Crusher 720, Push Active, Grind, Grind Fuel, Crusher ANC 2 and the classic twins | Music 1, Podcast 2, Movie 3, custom 101 |
| Dime Evo, Dime 360, Push ANC Active, Sesh ANC Active, Icon ANC, Hesh 360, Hesh 540 ANC, Push 720 and 540 Open and the BES models | Music 1, Podcast 2, Bass Boost 3, custom 101 |
| Rail, Rail ANC, Method 360 and 540 ANC | Music 1, Bass Boost 2, Podcast 3, custom 101 |
| Aviator 900 ANC | Music 1, Podcast 2, Movie 3, Bass Boost 4, custom 101 |
| PLYR, SLYR Pro (both) | Supreme Sound 1, Music 2, Podcast 3, Movie 4, FPS 5, MMO 6, RPG 7, custom 101 |
| Crusher PLYR 720 (both) | Supreme Sound 1, FPS 2, RPG 3, Music 4, four custom curves 101 to 104 |
| Session speakers | Signature 1, Outdoor 2, Indoor 3, custom 101 |
| Crusher 1080 ANC | Music 16, Podcast 17, Movie 18 (its Bose modes), custom 0 |

PairFree picks a preset with `JblAirohaEq`'s group step (`selectOnly`: the PEQ audio path and subset are read, then the group whose category is the number is selected) on any chip; the custom curve goes to 101. The Crusher PLYR 720's three extra custom slots stay unused: PairFree writes the first.

## 14. Noise cancelling and transparency strength (read 2026-11-18)

Two forms, by the SDK configuration of the product (`com/bragi/sdk/android/api/audio/data`):

- **AT**, for the transparency level of the Grind, Grind Fuel and Push Active (their configurations list `TRANSPARENCY_GAIN` over `BOS_AT`): `AT.UIAUDIO=transparency,,<percent>`, read back as `on,<percent>` (section 9.1).
- **Airoha RACE**, for the Crusher ANC 2's transparency gain and for the noise cancelling gain of every model that has one (`ANC_GAIN` and `TRANSPARENCY_GAIN` over `AIROHA_RACE`; the ANC filter itself is `AT.UIAUDIO=anc_filter` on the Crusher ANC 2): the command `0E06`, read as
  `05 5A 04 00 06 0E 00 0D` and written as `05 5A 0C 00 06 0E 00 0E <g> <g> <g> <g>`, the same gain four times, each two bytes big endian of the gain in hundredths of a dB (a short of dB x 100). One command serves the mode in force: the slider's 0 to 1
  maps to dB as `10 log10(x * (10^(max/10) - 10^(min/10)) + 10^(min/10))`, with the range -20 to 0 dB for noise cancelling and -90 to +6 dB for transparency (`GainRange`, `di/o.java` and `di/p.java`); the read answers with the status at byte 6, the sub-command `0D` at byte 7
  and the gain in bytes 8 and 9. The Flutter app's sliders convert percent to the same -20 to 0 dB (`convertPercentageToAirohaGain`). The models without an SDK configuration set the ANC state with AT (`SetAncStateAtCommand`) and the gain through Airoha's SDK (`setANCSettings`),
  whose RACE frames are Airoha's own ANC commands.

PairFree offers the AT level where the SDK lists it, and the RACE gain on the Crusher ANC 2 (`SkullcandyGain`, `SkullcandyRaceTalk`): a row for the strength of the mode in force, read again when the mode changes, in steps of a tenth of the slider. The other models' Airoha ANC frames (Airoha's SDK `setANCSettings`) come with their own sessions.

### 12.3 BES TOTA: the channel, the handshake and the replies (read 2026-11-18)

From `app/skullcandy/bes/` (`i.java`, `bessdk/service/base/b.java`, `d.java`, `bessdk/b.java`) and the Dart `utils/tota_commands/` classes.

- **Channel:** the serial service `66666666-6666-6666-6666-666666666666` (the BES SDK's default; classic Bluetooth, SPP). The SDK's BLE branch throws an `UnsupportedOperationException`, so the app uses SPP.
- **Handshake** (frames as section 12.2 describes, unencrypted until it ends): the host sends `01 10 <n u16 LE> <m> <m random bytes>` with m random between 16 and 127 and n = m + 1; the earbuds answer `02 10 <len u16 LE> <len byte> <their buffer>`; the host mixes the two buffers
  (the byte-wise operation by index modulo 6, section 12.2), takes the SHA-256, keeps the 16 even-position bytes as the AES key, and confirms with `03 10 21 00 20 <the 32-byte hash>`; the earbuds answer with `00 10 ...` holding the ASCII `suc` at offset 12 when it holds. The app waits 10 s for the answer and drops the link otherwise.
- **Data** after the handshake: each command is an AES-CBC (zero-padded, IV `00 01 .. 0F`) payload in a TOTA frame (`HEAD`, length, payload, CRC-32, `TAIL`), behind `9F <frame length u32 LE>`; replies come the same way (`9F` and four length bytes stripped first). The `BES_TOTA_ENCRYPTION_KEY` flag can turn the AES layer off; the app's default is on.
- **Commands:** a read is `09 63 02 00 <command> 00`, a write `08 63 03 00 <command> 00 <value>`; the replies start `0B 63` (to a read) or `0A 63` (to a write), then the length, the command and a status byte, with the data from the eighth byte on. Battery: two bytes, left then right. EQ mode, low latency,
  multipoint, sidetone, touch control and TWS: the last byte (`01` is on); the EQ mode's value goes through the same mode table as the Airoha models (section 13.3: Music 1, Podcast 2, Bass Boost 3, custom 101 on the BES models). The hearing mode, the button settings and the custom equaliser carry more; their layouts are section 12.4.

### 12.4 BES TOTA: the commands as PairFree builds them (read 2026-11-18)

Built as `SkullcandyBesTalk` (the conversation), `BesTota` (frame, handshake, key), `Aes128` (the cipher, checked against FIPS-197 and NIST SP 800-38A), `BesCommand`, `BesEq`, `BesButtons` and `SkullcandyBesBandEq` (the Sound tab). The Dart classes under
`utils/tota_commands/` and the app's own tables are the source; where a reading is an inference it is marked.

- **Handshake and frames** as in 12.3. The host's buffer is 16 to 127 random bytes (`01 10 <m+1 u16> <m> <buffer>`), the earbuds' arrives as `02 10 <length u16> <m> <buffer>` (the buffer is `length - 1` bytes from offset 5), the two are mixed byte by byte
  (the shorter padded with zeros; by the index modulo 6: sum, product, AND, OR, XOR, sum of squares, each as a byte), the SHA-256 of the result is sent as `03 10 21 00 20 <hash>`, and its even-position bytes are the AES-128 key. The key
  derivation is tested against a vector made with Python's `hashlib` from the same rules. *Inference:* the handshake travels in the same `9F <length>` wrapper and `HEAD ... TAIL` frame as the later messages, unencrypted; the app's receive code
  handles both a wrapped and a bare frame, and PairFree reads either. The CRC-32 is over the payload as it travels (the ciphertext, once the key is in force).
- **Reads and writes:** `09 63 02 00 <cmd> 00` and `08 63 <length u16> <cmd> 00 <value>`; a reply is `0B 63` (read) or `0A 63` (write), the length, the command and a status byte, then the data from offset 7. *Inference:* the reply's length counts
  from offset 4, which PairFree uses to cut the zero padding off (without it, `01` as the last byte of a switch could not be told from padding).
- **Battery** (1): two bytes, left then right, percent. **Equaliser mode** (2): one byte, the number of the app's mode table (section 13.3: Music 1, Podcast 2, Bass Boost 3, custom 101), written as `08 63 03 00 02 00 <mode>`.
- **Custom equaliser** (3): the reply is a band count and then six bytes per band (type, gain as two bytes little endian, frequency as two bytes little endian, Q); the write is `08 63 21 00 03 00 05` and per band `01 <gain> <frequency> 2A`, the Q 0.91 times 46.41
  rounded. A gain is a sign and a magnitude over minus 5 to plus 5 dB: the magnitude is `|round((g + 5) * 6553.5) - 32768|`, the sign bit `8000` is set below zero (minus 5 dB is `00 80`, plus 5 dB `FF 7F`), and PairFree offers whole dB. The frequencies are the earbuds'
  own, from the read, so the curve is offered once they have reported it. *Inference:* choosing the custom mode (`02 ... 65`) goes before the bands, as the app's `activateFeature` selects a mode and `setEQ` writes the bands; the app's code was not
  seen doing both in one gesture.
- **Hearing mode** (9): the reply ends with the mode and then the level (the app's decoder reads the second to last and the last byte); the write is `08 63 04 00 09 00 <mode> <level>` on the models with a level (Dime 540 ANC, `advancedHearingModes`: a slider of 0 to 100,
  starting at 50, whose value rounded is the level) and `08 63 03 00 09 00 <mode>` on the rest. The mode comes from the app's `SKDYBesHearingModeUtils.valueForFeature`: 2 for noise cancelling (`ancMode`, `adaptiveANCMode`, `adjustableNoiseCancellation`),
  1 for stay aware (`stayAwareMode`, `adjustableStayAwareMode`), 0 for off.
- **Switches:** multipoint (5), low latency (6), sidetone (`10`) and touch control (`11`) are `08 63 03 00 <cmd> 00 <01|00>`, read as `01` in the last byte; the firmware version (8) and the TWS status (`12`) are read for the device report. The serial number (7) is never read.
- **Buttons** (4, models with `customButtonActions`: Dime 540 ANC and Smokin' Buds 360 H and S): the reply is a whole number of four-byte entries `02 <gesture> <action u16 LE>`; the write is `08 63 12 00 04 00` and four entries, which the app writes whole
  (it refuses any other count). Gestures: `08` single press, `09` double, `0A` triple, `06` press and hold (the app's `SKDYGesture` indices 0 to 3). Actions: `03` play / pause, `01` previous track, `02` next track, `05` volume down, `04` volume up, `07` take a photo,
  `06` voice assistant (the `SKDYAction` indices 2 to 8). *Inference:* the manifest lists only `none` as mappable for these three models, so which actions the app's screen offers for each gesture could not be read from the data; PairFree offers the seven the writer
  can encode, for the gestures the earbuds report.
- **Never sent:** the serial number's read, any write but the ones above (`BesCommand.WRITABLE`); there is no firmware command in the app's TOTA classes.

## 15. Bragi protobuf RPC: the Method 360 and 540 ANC and the Crusher 1080 ANC (built 2026-11-18)

The SDK's own configurations for these three (`T99_PLUS`, `T120`, `CHP300`; `data/skullcandy/sdk_devices.json`) put their settings on `BOS_RPC`: RFCOMM service `54C5BF4C-...`, frames as sections 3 and 4, the schemas the app bundles (`bos_*.proto`, read outside the repo; the field
numbers and enum values below are theirs). Built as `SkullcandyRpcTalk` (the conversation), `BosRpc` (the ids and the list of what may be sent), `SkullcandyRpcBandEq` and the extra helpers in `BragiProto`; the driver is `SkullcandyRpcDriver`, which opens the RACE channel beside it for what is
Airoha's (multipoint on all three, the Method models' custom equaliser).

- **Secure session: not needed to start.** `device.dev_info` field 12 says whether the earbuds want it; the SDK's `secureSession()` failure path logs "degrading to an unsecure connection: services requiring authenticated_access will not work until this succeeds" and carries on. PairFree
  does the same: it never starts service 25, reads field 12 into the device report, and records any request answered with status 41 ("service access denied") there; a row whose request was refused does not appear. Which services need the secure session is not written in the schemas; the first
  device report of each model will show it.
- **Status codes** (the SDK's `rpc/l`): 0 success, 33 no such service, 34 no such procedure, 35 request decode failed, 41 access denied, 64 subscription replaced by a newer one.
- **Hearing** (service 4): `mode_info` (3) gives each mode's maximum preset and gain; `mode_read` (1, subscribed) streams the active mode (1 off, 2 noise cancelling, 3 transparency) with each mode's preset and gain; `mode_configure` (2) is mode, `activate`, and optionally preset and gain (off with neither).
  PairFree writes the mode alone for the mode buttons and the gain or the preset alone for the strength and profile rows of the mode in force. Sidetone: `sidetone_info` (12) the maximum volume, `sidetone_read` (10) and `sidetone_configure` (11, optional enabled and volume).
- **Low latency** (8): normal 1, permanent 3, temporary 4; the switch writes 1 or 3 (the SDK lists `LowLatencySupportsPermanentModeAttribute` for all three) and reads 3 or 4 as on.
- **Wear detection** (Crusher 1080 ANC, service 12): `trigger_get`/`trigger_set` with two flags, `play_pause` (the app's "pause when taken off") and `transparency` (switch to transparency on removal); the schema defaults both to true.
- **Crusher** (23): `config_get` (maximum intensity, whether it is read-only, an optional mode count), `state_get` (subscribed) and `state_set` (optional active, intensity, mode: 0 high, 1 medium, 2 low). The intensity row is greyed out when the earbuds say it is read-only.
- **Spatial audio** (17): `info_get` (subscribed: availability and the mode), `audio_source_mode_set` (1 off, 2 fixed to the head, 3 fixed to the room); the row appears when the earbuds say it is available.
- **Voice prompts** (22) and **language** (6): `options_get`/`options_set` (fewer prompts, the battery prompt at power-on) and `lang_info`/`lang_configure` (the locale codes the earbuds list); the language row shows the codes as the earbuds give them.
- **Equaliser:** the Method models' is the Airoha PEQ of section 13 (`EqPresetNamesAttribute` Music, Bass Boost, Podcast; the SDK notes `EqRequiresTwsConnected` for the 360). The Crusher 1080 ANC's is the earbuds' own parametric one (`peq_info` 6, `peq_read_state` 4, `peq_read_conf` 5, `peq_configure` 7): the
  presets are the filters that are not editable (picked by their id, activated and persisted), the curve is the editable filter's bands (each keeps its frequency and Q; the gain is a float). *Inference:* the SDK's gain range for it is 0 to +20 (`GainRangeAttribute`), which PairFree shows around the middle,
  minus 10 to plus 10, and sends as the value plus 10; checked: the SDK's configuration, the schema and the manifest, which give only that range; the app's Dart screens (2026-11-19: `SKDYEQEditorSliderWidgetState._minValue`, `_maxValue` and `_onDragUpdate`, `SKDYEQEditorWidgetState._convertGraphValuesToGains`, `BragiService`'s `BragiGetEQSettings`) take the slider's whole travel as the product's `ceqRange` (value = fraction x (max - min) + min, with no offset and no number shown on the sliders), so the wire gain is the slider's position over 0 to 20: PairFree's plus and minus 10 around the middle is the same travel, and where the earbuds' own flat point lies inside it is not shown by the app (it is the range's middle only if the earbuds' presets sit there).
- **Never sent:** the secure session (25), calibration (18), data streams (14), diagnostics (100), feature provisioning (9), time (2.6), device name (7), voice streaming (5), Mimi (10), the Dolby spatial controls (19), media (15), the audio-out mode (21: no SDK configuration lists it) and the action-button
  injection and invocation (3.4, 3.5). `BosRpc.isAllowed` lets through only the requests listed in the code.
- **Button actions** (service 3): the app's bridge (`n9.b0`, `c0`, `d0`, `Z`) turns each setting into `setCommands(side, key, gesture, [command])`: the side 0 left, 1 right (a headset: `HEADSET`), the key `POWER` for the earbuds' power button and `CENTR` for the Crusher's joystick, the gestures
  0 `CLICK`, 1 `CLICK2`, 2 `CLICK3`, 3 `HOLD_UP`, and the action id the Dart enum carries (0 Spotify one-touch, 1 transparency toggle, 2 play/pause, 3 previous, 4 next, 5 volume down, 6 volume up, 7 take a photo, 8 assistant, 9 none, 10 spatial toggle: the manifest's lists for the models are the
  ten actions of the Method models and play/pause, assistant, spatial toggle and none for the Crusher 1080 ANC). The RPC transport sends it as `set_action(config, "<key>.<gesture>", [action])`. PairFree asks the earbuds for their own list (`get_info_v2`: configurations, keys, gestures, actions), reads each wanted
  `key.gesture` with `get_action`, and offers only the actions of the manifest that the earbuds list. *Inference:* the action strings follow the SDK's naming (`media.play_pause`, `media.next`, `volume.up`, `ancat.toggle`, `spotify.onetouch`, `assistant.toggle`, `spatial.toggle`, `device.no_action`, `remote.hid_volume_down`), which the check against the earbuds' own list guards.
- **Auracast receiver, Tile, Share Audio, Mimi and the identity reads:** see section 18.

## 16. The Rail and Rail ANC: Airoha RACE alone (built 2026-11-18)

The SDK's configurations for these two (`T70`, `T80_PLUS`) put every feature on Airoha's RACE (`AIROHA_RACE`) or Skullcandy's custom RACE (`AIROHA_RACE_CUSTOM`), with no AT and no RPC. The Rail's serial service is Airoha's own, `FFEEDDCC-BBAA-9988-7766-554433221100` (the Rail ANC's entry holds a variable the decompiler lost; it is
taken to be the same). Built as the primary mode of `SkullcandyRaceTalk` (`primary`, `usesRail`) and `SkullcandyRailDriver`.

- **Hearing mode, read from the SDK** (`api/audio/data/anc/requestProvider/c.java`, `transparency/requestProvider/c.java`, the constants of `common/o.java`): the command is `06 0E`; noise cancelling on is `00 0A <filter> <type> 01` with filter 3 and type 0 (the hybrid default), transparency on is `00 0A 04 04 01` (filter 4, type 4), off is `00 0B 01`.
  These are the AB1562 forms of JBL's Airoha code (`RacePacket.of(ANC, 0, ANC_ON, filter, mode, 1)`). The state is the module read `01 09` of module 5: the first data byte 1 to 4 is noise cancelling on, 9 to 11 transparency on, anything else off. The Rail has transparency only (its configuration lists no `ANC`).
- **Strength:** the gain commands of section 14 (`ANC_GAIN` on the Rail ANC, `TRANSPARENCY_GAIN` on both), read again when the mode changes.
- **Batteries:** the Airoha SDK's own reads, as JBL's Airoha models do them: the push of changes on (`06 00 01`), which earbud the phone talks to, the agent's battery and the case's, and the other earbud's battery as an unasked indication; the screens show the two earbuds by which one the phone talks to.
  The Skullcandy configuration's `DEVICE_INFO` is a wrapper that hands the battery to the same Airoha provider.
- **Multipoint** (the NV forms of section 11), **the chip read and the equaliser** (section 13: Music, Bass Boost, Podcast and the custom curve) go through the same talk.
- **Buttons** (`customButtonActions`): the key map of each earbud, as `RaceCommandBragiButtonSettingsGet` and `Set` build it. A read is `05 5A 03 00 07 00 <side>` (01 left, 02 right); the answer is entries of eight bytes: the gesture's type (00 single press, 05 double, 06 triple, 02 press and hold), the key
  (0 is the power button), the event as two bytes little endian and four bytes of state. A write is `05 5A <length> 03 00 <side>` and the entries (the app writes the earbud's four gestures whole): the events of the app's ten actions (`_actionToBytes`) are 60 play/pause, 94 transparency toggle, 5A next, 5B previous,
  0A volume up, 0B volume down, BE Spotify one-touch, C0 take a photo, 33 voice assistant and 00 none; the state is 0E for the double press and 08 for the others; the earbuds acknowledge with `05 5B 03 00 03 00 00`. The rows are one per earbud and gesture.
- **Voice control** (`voiceControl`, `voiceLanguage`): `05 5A 03 00 01 00 10` turns it on and `... 00` off (the app has no read of the state, so PairFree offers two buttons, not a switch); `05 5A 03 00 09 00 01` reads the language as text, shown as information. Choosing another language installs a voice pack the app downloads from
  Skullcandy's servers, which PairFree never contacts (hard rule), so there is no language row. The audio stream command of the same class (`01 00 21`) starts voice streaming to the phone and is not a setting.
- **Tile, Mimi and Share Audio:** see section 18.

## 17. The six older Qualcomm models: C-Chip frames over Bluetooth LE (built 2026-11-18)

Crusher ANC, Crusher Evo, Indy Fuel, Indy Evo, Push Ultra and Crusher 540 Active (the entries the manifest lists as `ble`; section 12). The app's Dart code drives them without any native SDK: `api/bluetooth/skdy_bluetooth.dart` writes a frame to a GATT characteristic and reads the answers from
a notification, and `api/services/skdyearbudservice.dart` builds the frames. Built as `SkullcandyCChip`, `SkullcandyQualcommTalk` and `SkullcandyQualcommBandEq`, the driver `SkullcandyQualcommDriver`, the LE finders (`SkullcandyFinder` on Android, `LeScan.findSkullcandy` on Windows).

- **Channel:** the GATT service `00001100-D102-11E1-9B23-00025B00A5A5`; frames go to `00001101-...` and answers come as notifications of `00001102-...` (the strings the Dart code passes to `SKDYBT`). The app asks for no particular MTU; PairFree asks for 64, which the longest frame (five bytes) does not need.
- **Frame** (`SKDYCChipCommand.getData`, `fromData`): the command word `F8 FF` (63743), a type byte, a length byte (the four header bytes plus the payload) and the payload.
- **Types read from the app's calls:** 1 sets the ambient mode (payload 1 off, 2 on), 2 reads it, 7 sets the equaliser preset (payload 1 Music, 2 Podcast, 3 Movie: the `activateFeature` branches for `musicMode`, `podcastMode` and `movieMode`), 8 reads it, 9 and 10 read the left and right battery and 11 which
  earbud is connected (the closures `getBatteryLevelLeft`, `getBatteryLevelRight` and `getConnectedEarbudSide`). The ambient mode is the feature at index 20 (`ambientMode`), which the two Indy models have; the Crusher ANC has neither the equaliser nor the ambient mode (only Tile and personal sound, which are
  account and Mimi features).
- **Inference:** the app's decoder closures for the answers were not traced, so PairFree takes a reply to a read to mean what the matching write sends (the preset 1 to 3, the ambient 1 or 2, the battery as a percentage) and ignores any other value. The side read (11) is not used.
  The equaliser toggle (`definedEqualizerToggle`) has no command in the service's `activateFeature`, so it gets no row.
- **Finding the earbuds:** these earbuds control over LE while their audio is classic, and four of the six share a name with an Airoha twin (Crusher Evo, Crusher 540 Active, Indy Evo, Indy Fuel: section 2). The name cannot tell which hardware a user has, so PairFree tries the classic channel as before
  and, when it does not open, the LE one: GATT on the classic address, else the device a scan finds (the address itself, or the one device with the model's advertised name; `BLUETOOTH_SCAN`, which the owner allowed for any vendor on 2026-11-15 and which is asked only when the user opens such a model).
- **Never sent:** only the frames above go out (`SkullcandyQualcommTalk.allowed`).

## 18. The rest of the manifest's features: Auracast, Tile, Share Audio, Mimi and the identity reads (read 2026-11-19)

- **Auracast receiver (Crusher 1080 ANC, built).** The SDK's `AURACAST_RECEIVER` feature is on Airoha's RACE for the Crusher 1080 ANC alone (`sdk_devices.json`; `AuracastApi`: scan for broadcasts, scan for subgroups, start, stop, observe). The commands are Airoha's LE Audio
  commands of the AB158x, laid out in the app's `res/xml/command_race.xml` (`libmmi158x`): `2200 <mode>` scans (the SDK passes 3 to start and 0 to stop), `2201 <address as sent> <address type> <advertising set>` picks a source, `2202 <subgroup>` plays a subgroup, `2203` stops the stream, `2204` resets it, `2208` reads the state
  (status, then a state byte), `2209 <sixteen bytes>` sets the broadcast code and `220A <index>` plays by index. The earbuds answer with `05 5B` frames and send events as `05 5D` frames of the same ids: `2200` a source (status, address type, address, advertising set, signal, the advertising data length and the data, whose length-type-value entries hold the public
  broadcast announcement `1856` with the encrypted bit and the broadcast name, type `30` hex), `2202` the stream's sync, `2203` its end (0 normal, 1 a wrong code) and `2209` its information (non-zero: encrypted). The app's start is the pick, then the play of the first subgroup for an open broadcast, or the code for an encrypted one. Built as `SkullcandyAuracast`
  inside `SkullcandyRaceTalk` with Sennheiser's Auracast rows (scan, add, source state, code, remove); the message layouts are read from the SDK and the XML, checked against synthetic answers in `SkullcandyRaceTalkTest`, not against earbuds. The code text goes as the SDK sends it (ASCII padded to sixteen bytes; a longer text is read as hexadecimal).
- **Tile (22 products, built as a link).** `findWithTile` is Tile's own tracker in the earbuds; the app's guide (`SKDYFindWithTileFeatureGuide`, `SKDYNativeUtils`) only checks whether `com.thetileapp.tile` is installed and offers to open it. PairFree shows one row that opens Tile's page or app.
- **Share Audio (Push Active, Grind, Grind Fuel, Rail ANC): a user guide.** `shareAudio` is read in `skdyproductutils.dart` and shown only as `SKDYShareAudioFeatureGuide` (two texts, `share_audio_feature_guide_info` and `_make_sure`); no command or service belongs to it (checked: the guide, the manifest, the SDK configurations of these four, none of which lists a share or Auracast feature).
- **Mimi personal sound (20 products): blocked by a hard rule.** The SDK's `MIMI` feature and the Dart bridge (`authenticateMimiUser`, `startMimiTestFlow`, `getLatestMimiResult`, `setPersonalSoundMimiMode`) run Mimi's hearing test, which signs in to Mimi's cloud with the user's account before it works. checked: `AirohaChannelMethod`, the SDK
  `MIMI` features on AT, RACE-custom and RPC, the manifest. Not sent, and no account is used.
- **The two RACE reads of the Crusher 1080 ANC's whitelist (`41 00`, `43 00`):** no screen of the app sends them (checked: every caller of `RaceCommand` classes in the Dart code and the SDK's `custom command` list); their names, `serial number` and `SKU`, are the whitelist's own. PairFree does not read them.
- **Found only, no settings:** the three Terrain speakers (Actions chip) and Smokin' Buds 3 (JieLi) show no setting in the app (checked: the manifest and the chip table), and the four Session speakers are held back with all speakers.
- **The secure session (service 25) and BES encryption.** The SDK's flow (`api/auth/feature/dataSource/rpc/l.java`, `transport/rpc/crypto/a.java`, `transport/common/crypto/`, `protobuf/BosAuthenticatedAccess`): the client sends `authenticate` with an ephemeral NIST P-256 key, a random value and the channel ciphers it supports; the earbuds
  answer in chunks with their ephemeral key, their random value, the ciphers they pick (each: id, transport, cipher suite, key length, nonce length, tag length) and an ECDSA P-256 / SHA-512 signature over the client's key and random, the earbuds' key and random and the cipher list, which the client verifies against the earbuds' identity keys it has trusted. The identity keys come
  from the trust establishment (`trust_establishment_start`, `commit`, `nonce`, `confirm`, `abort`): a commitment, then nonces, then a number both sides show for the user to compare, kept in a trusted identity store; after `authenticate` every frame of every service except 25 is an AEAD record under the session's channel key (`transport.common.crypto.d`, the nonce from a
  per-channel counter). blocked: the owner's rule for this brand (2026-11-18) is to run the secure session only if a model's main controls turn out to need it, and no source shows one that does (the SDK's own failure path degrades to the unsecured connection and the app does the same); checked: `l.java`, `crypto/a.java`, `crypto/j.java`, the SDK's degrade log, the
  manifest (`authenticated_access` is a flag the earbuds report) and the models' RPC services (every request PairFree sends is answered or recorded as status 41 in the report). It would also need a screen for the number comparison, a store for the trusted identities and P-256, ECDSA and AEAD code that nothing here can check against earbuds. The BES TOTA encryption switch
  (`BES_TOTA_ENCRYPTION_KEY`) is in the app's `secrets` file, which stays closed (hard rule); PairFree runs the handshake as the app's default does (encryption on).
