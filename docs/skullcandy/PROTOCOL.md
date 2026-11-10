# Skullcandy protocol research

Research only, 2026-11-06. No Skullcandy implementation was added and no hardware was connected.
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
