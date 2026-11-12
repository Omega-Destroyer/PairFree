# Beats on Apple's chips: the B2P protocol (work in progress)

Read from the Beats app 2.12 (`com.apple.bnd`), whose three protocol libraries (`libB2pAdapter.so`, `libBcdAdapter.so`,
`libBtpAdapter.so`) keep their symbol names. Disassembled outside the repo on 2026-11-10 (`E:\Builds\research\beats-native`).
Nothing here has been sent to a Beats yet, and no recording or virtual run has confirmed it: it is source analysis only.

## Which Beats use it, and over what

`assets/products_offline.json` in the app gives each product a `protocol_id`: 4 is B2P (Powerbeats Pro, Powerbeats Pro 2,
Powerbeats, Powerbeats Fit, Solo Pro, Solo 4, Solo Buds, Studio Buds, Studio Buds+, Fit Pro, Studio Pro, Pill, Beats 360),
3 is BCD (Powerbeats3, Solo3, Beatsx, Flex, Studio3), 2 is BTP (Pill+). The app opens a plain RFCOMM socket
(`createInsecureRfcommSocketToServiceRecord`, UUID 00001101-0000-1000-8000-00805f9b34fb, up to four connection attempts)
and hands the stream to the adapter. A second path sends the same bytes over GATT (`b2pOnGattReceivedBytes`). There is
no L2CAP anywhere in the app, so no Android version matters.

## Frame (`b2p_link_send_or_queue_packet`)

```
FF  B2  length(2, low byte first)  sequence  opcode(2, low byte first)  payload...  crc32(4, low byte first)
```

- `length` counts the whole frame: payload length + 11.
- `sequence` counts 1 to 255 and wraps to 1 (never 0).
- The CRC covers every byte from the leading `FF` to the last payload byte. It is table-driven and MSB first, polynomial
  `04C11DB7`, starting value 0, and the result is inverted (`b2p_crc32_get`, `b2p_crc32_calc_done`; the table is
  `b2p_crc32tab`).
- A second framing, `FF BC` with a big-endian length (payload + 9), an 8-bit CRC (SAE J1850, polynomial 1D,
  `buddy_crc8sae_*`) and no opcode field in the same place, belongs to "buddy" packets; its use is not worked out.
- Opcodes: `data/apple/b2p_opcodes.json` (names from `get_b2p_opcode_name`). A command is an even number and its
  response the next odd one (`B2P_CMD_BATT_GET` 0152, `B2P_RSP_BATT_GET` 0153).

## Commands the app's setters build

Seen in the JNI wrappers (`Java_com_apple_beats_adapters_B2pAdapter_*`):

| Setting | Opcode | Payload |
|---|---|---|
| Listening mode (0 off, 1 noise cancelling, 2 transparency; larger values refused) | 015A `LISTENING_MODE_LEVEL_SET` | mode, then 64 (the level, 100) |

Payload layouts of the replies, from the JNI getters (`b2pGetBattery`, `b2pGetInEarDetection`, `b2pGetProductId`,
`b2pGetListeningMode`). `b2p_response_handler` copies a reply's payload into the connection state and records its length;
each getter then checks that length and reads fixed offsets (source analysis only, nothing confirmed on a Beats):

| Read | Opcode | Reply payload |
|---|---|---|
| Battery (also V2, 056E) | 0152 (056E) | 5 bytes. Byte 2 is the level, 0 to 100. Byte 3 is the charging flag: 1 adds 0x80 to the level the app reports. Bytes 0, 1 and 4 are not read by the getter |
| Product id | 013A | 2 bytes, a little-endian number |
| In-ear detection | 014C | 1 byte: 0 off, 2 on. Seeing 0 the app writes 014A with 2 |
| Listening mode | 0158 | asked twice, with a one-byte payload of 1 and then 2; each reply is 1 byte. The app compares the two (and its own table of supported modes) to decide off, noise cancelling or transparency, so the decoding of those two bytes is still open |
| Information (`b2pRequestInfo`, also the battery's first read) | 0430 | exactly 20 bytes, handed to the app as one block (it may hold serial numbers: keep it out of logs and reports) |
| Button mode | 046A | raw bytes handed to the app unchanged; layout open |

The rest (one-bud ANC 047C/047E, name 012C/0134, colour 0116, capabilities 0144, in-case tones 050A/050C and 0570/0572,
end-call control, microphone mode 014E/0150, listening mode rotation 018A/018C) have an opcode but their payload layouts are
still to read.

## Which Beats offer what

Apple's app takes each product's features from `assets/products_descriptor.json` (read 2026-11-10; `features` in
`data/apple/devices.json` is the union over its firmware versions, derived facts only), and its native getters refuse a read the
product does not list. PairFree follows the same list: a read is sent only for a feature the model has.

| Feature | Read / write | Reply payload |
|---|---|---|
| `ANC`, `Transparency` | 0158 (with 1, then 2) / 015A | see above |
| `InEarDetection` | 014C / 014A | 1 byte: 1 off, 2 on |
| `OneBudAnc` | 047C / 047E | 1 byte: 1 on; write 0 or 1 |
| `Microphone` | 0150 / 014E | 1 byte, 0 to 2 (taken as the AirPods' automatic, right, left; the app only passes it on) |
| `InCaseTones` | 050C / 050A | 1 byte: 1 on; write 0 or 1 |
| `ListeningModeRotation` | 018A / 018C | 1 byte, raw (the bit meanings are not read; not offered) |
| `ButtonMode*`, `EndCallControl*`, `ToneVolume`, `HeartRateSensor`, `Sharing` | 0468/046A, 01AE/01AC (01C8/01C6), 031C/031E, 0364/036A, 03E6 | layouts not read; not offered |

## In PairFree

`BeatsSession` and `B2pFrame` (`protocol/apple/`), opened over the plain serial channel (`BeatsTalk`) for the Beats
whose `data/apple/devices.json` entry has `b2p` (known by name, `AppleModels.b2p`). On connecting it reads the product id and the
battery (0152), then one read for each feature in the table above that the model lists, 200 ms apart. It shows the battery, the
listening mode (off, noise cancelling, transparency), one-bud noise cancelling, ear detection, the microphone and the in-case tones, each
once its read was answered. The mode is written as 015A with the mode and 64 (hex) and read again after the Beats answer or announce it.
Only these commands are sent (`BeatsSession.ALLOWED`). Inferred and to be confirmed by a Beats owner's device report: the reading of the
two listening mode answers (a nonzero first answer with a zero second one means noise cancelling, the reverse transparency), the battery
reply taken as one level for the whole product, and the microphone's value names.

Reset and factory reset (0008, 0154) are not offered. blocked: Apple's app never sends them and no payload is known; checked: the
app's Java (no caller of either, `b2pRequest` is only a generic passthrough), the JNI exports (no reset setter), and the opcode table.
The owner allowed them as confirmed actions (2026-11-10), so they can be added once a source shows the payload.

## Confirmed actions

`RESET` (0008) and `FACTORY_RESET` (0154) may be offered as actions the user confirms on screen, in that moment, as EarFun's restart
and power off are (owner, 2026-11-10: reset codes may be sent behind a confirmation). They are never part of a probe or a
read-back, and nothing else on the list below is.

## Checked against the library

The CRC table in `libB2pAdapter.so` (`b2p_crc32tab`, 256 words) equals the table PairFree's `B2pFrame.crc32` computes
(MSB first, polynomial 04C11DB7), and the code in `BeatsTest` pins the algorithm to POSIX cksum's check value (`123456789` gives
765E7680). A frame built from the library's own routine (`b2p_link_send_or_queue_packet`) was read, not run: the earlier virtual run
of Beats' app (Fit Pro, `apple/VIRTUAL_TESTING.md`) never reached an accessory channel, so the framing is not yet confirmed
by an answer from a Beats or a virtual one.

## BCD (Powerbeats3, Solo3, BeatsX, Flex, Studio3)

`libBcdAdapter.so` (disassembled 2026-11-10; opcode names in `data/apple/bcd_opcodes.json`, 283 of them). Frame: `AA`, a length (the
command's two bytes plus the payload, so payload + 2; the sequence number is not counted), a sequence number (1 to 255, never 0), the
command (two bytes, high first), the payload, and a CRC-8 (SAE J1850 table, polynomial 1D, start 0, no final inversion, over every
byte before it, the `AA` included; the table's first entries are 00 1D 3A 27). A packet with length 0 is an acknowledgement
(`AA 00 seq crc`): the sender keeps one packet outstanding until it is acknowledged, and acknowledges each packet it receives. A
command is even and its response the next odd number (`BCD_CMD_GET_BATTERY` 014A, `BCD_RSP_GET_BATTERY` 014B).

What Apple's app reads and writes through its Java calls:

| Setting | Command | Payload / reply |
|---|---|---|
| Product id | 0000 | reply 2 bytes, low byte first |
| Listening mode (Studio3's noise cancelling) | read 01A4, write 0226 | reply 1 byte: 2 is on. Write 2 for on, 1 for off |
| Auto answer (only the Flex, product id 2010) | read 024A, write 0248 | reply 1 byte: 1 is on. Write 1 for on, 2 for off |
| Name, colour, serial number, firmware version | 010C/010A, 0018, 000A, 0002 | not used by PairFree (the serial number stays private) |

In PairFree: `BcdSession` and `BcdFrame`, opened over the same serial channel. Apple's feature list gives the Studio3 `ANC` and the Flex
`AutoAnswer` (the Powerbeats3, Solo3 and BeatsX list nothing), so it reads the product id and then just those, and shows what they
answer: noise cancelling on or off, and the Flex's auto answer switch. Source analysis only. Left out: the battery (`BCD_CMD_GET_BATTERY` 014A exists but Apple's Java never calls it and its
layout is unread; Android shows these models' battery through the standard Bluetooth reports), and the factory reset
(0158) which would need a confirmed action like B2P's.

## BTP (Beats Pill+), not built

Beats Pill+ is a speaker (`libBtpAdapter.so`: sessions, partner mode, authentication, volume, cue sounds). PairFree holds speakers as
future work (CLAUDE.md, Speakers), so BTP is not built; blocked: the owner's rule on speakers; checked: the adapter's exports show a
session and partner layer that no earbud or headphone uses.

## Never sent

Firmware update and DFU (`DFU_*`, `FWUP_*`, `FLASH_*`, `uarp*`, `b2pTransferFirmware*`), `SHIPPING_ENTER`, GPIO and I2C access, `LOG_DATA_SEND`/`b2pCaptureLogs`, the certificate and challenge exchange
(`CERTIFICATE_*`, `GAPA_*`, `AUTH`, which Apple uses to check the Beats are genuine), `FACTORY`-type calls and
`BEACON`/location. The GATT service `4715650b-5e9d-4ac2-b898-a4fc0aa5df78` is Apple's UARP firmware-update transfer.

## Next

1. The listening mode's two reads (0158 with 1 and 2), the button mode, capabilities, and the bytes 0, 1 and 4 of the battery reply.
2. Connecting: `b2pInit` only allocates the state and calls `b2p_init`/`b2p_enable`; the getters and setters each send their own
   command through one shared function (0x1a500) with no visible handshake or `AUTH`/`GAPA` step before them. So nothing in the
   library gates the settings; a virtual or real Beats must still confirm that.
3. Run the Beats app against a virtual Beats that answers `FF B2` frames, as for the other brands, to confirm the framing.
4. BCD and BTP (older Beats), in the same way.
