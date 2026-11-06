# Huawei local protocol-core draft

Branch `brand/huawei` is a reviewable first layer, not a supported-device claim. It adds a pure Kotlin
MBB frame/TLV encoder, strict CRC-16/XMODEM decoder, bounded stream reassembly, a battery read builder
and a battery value decoder. Command, wire and CRC constants are generated from `data/huawei/commands.json`.
No vendor or GPL source was copied. Vendor-plugin facts are primary; component ordering is additionally
checked against OpenFreebuds, as credited in [PROTOCOL.md](PROTOCOL.md).

The only provided request helper asks for battery tag 1 (`5A00050001080100F53D`). It does not read broad
identity information, the misleadingly named big-data support command, firmware, accounts or logs.
The generic codec is not attached to any socket, model detector or UI. The parser's 4096-byte body limit
is a local settings-traffic bound, not a claimed vendor MTU. Duplicate TLVs remain ordered; ambiguous
duplicate battery tags and absent/invalid component percentages remain unknown.

Before app integration, select models explicitly, finish their feature/action tables, choose their
RFCOMM transport from actual model evidence, and add sessions, settings, EQ, privacy-safe reports,
device pages and official-app text. Modern ANC read/write field order differs and must not reuse the
legacy scalar builder. These are remaining implementation tasks, not claims that the protocol is blocked.

Tests use the analytical vendor battery frame, the independent XMODEM check value for `123456789`,
every split boundary, coalesced frames, noise, oversized lengths, CRC errors, truncated TLVs and
synthetic battery replies. No hardware test or device report exists.
