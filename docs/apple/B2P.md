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
| Button mode | 046A | raw bytes handed to the app unchanged; layout open |

The rest (one-bud ANC 047C/047E, name 012C/0134, colour 0116, capabilities 0144, in-case tones 050A/050C and 0570/0572,
end-call control, microphone mode 014E/0150, listening mode rotation 018A/018C) have an opcode but their payload layouts are
still to read.

## Confirmed actions

`RESET` (0008) and `FACTORY_RESET` (0154) may be offered as actions the user confirms on screen, in that moment, as EarFun's restart
and power off are (owner, 2026-11-10: reset codes may be sent behind a confirmation). They are never part of a probe or a
read-back, and nothing else on the list below is.

## Never sent

Firmware update and DFU (`DFU_*`, `FWUP_*`, `FLASH_*`, `uarp*`, `b2pTransferFirmware*`), `SHIPPING_ENTER`, GPIO and I2C access, `LOG_DATA_SEND`/`b2pCaptureLogs`, the certificate and challenge exchange
(`CERTIFICATE_*`, `GAPA_*`, `AUTH`, which Apple uses to check the Beats are genuine), `FACTORY`-type calls and
`BEACON`/location. The GATT service `4715650b-5e9d-4ac2-b898-a4fc0aa5df78` is Apple's UARP firmware-update transfer.

## Next

1. The listening mode's two reads (0158 with 1 and 2), the button mode, capabilities, and the bytes 0, 1 and 4 of the battery reply.
2. What the app sends on connecting (`b2pInit`, `b2pRequestInfo`, `b2pGetCapabilities`) and whether a handshake or the
   `AUTH`/`GAPA` exchange gates the settings (it is the genuine-product check; if it gates them, say so).
3. Run the Beats app against a virtual Beats that answers `FF B2` frames, as for the other brands, to confirm the framing.
4. BCD and BTP (older Beats), in the same way.
