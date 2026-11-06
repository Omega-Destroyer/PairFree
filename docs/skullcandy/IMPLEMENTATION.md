# Skullcandy local Bragi protocol-core draft

Branch `brand/skullcandy` implements the Bragi PPP/RPC wire layer independently from Skullcandy 5.0.6's
SDK behavior. `generateSkullcandyTables` supplies constants from `data/skullcandy/commands.json`.
It provides bounded stream parsing, header CRC-8/ROHC, big-endian stored CRC-16/MCRF4XX, RPC type/final
and service/method/tag matching, and a small protobuf field reader. No vendor schema source or generated
vendor class is copied. Earshot's MIT documentation provides an additional checksum comparison.

The only request helpers are device info (including its explicit unspecified-gender field) and battery.
Device info contains private identifiers; its raw payload must never enter device reports. The access
flag decoder distinguishes true, false and unknown; absent, duplicated or invalid flags are unknown,
never authorization to bypass trust establishment. Secure protocol 95 frames remain opaque and cannot
be decoded as plain RPC. No ECDH, identity storage, numeric confirmation or AEAD session is implemented.

The parser accepts SDK RPC protocol 94 and opaque secure RPC 95, with or without the FCS flag. Other
planes and unknown flag bits are outside this draft. The complete frame fits within 4096 bytes. CRC
header coverage includes 7E; FCS coverage excludes 7E and includes the header CRC. No HDLC escaping is
invented. RPC STATUS requires a present zero byte for success; empty status is not success, following
the vendor rather than Earshot's current behavior ([UPSTREAM.md](../UPSTREAM.md)).

No model detector, RFCOMM service choice, Android session or settings is enabled. Remaining work:
finish product-specific stack selection (Bragi versus RACE/custom routes), implement genuine local
trust establishment for products requiring it, consume service capability/configuration replies,
then map battery, ANC, PEQ, controls, Crusher, languages, latency, insertion and spatial settings with
their advertised ranges. Add redacted reports and device pages with that integration. These are tasks
still to implement, not a claim that local security or native code is an impossible protocol blocker.

Tests reproduce the analytical `7EFF809400062D1002010008005480` device-info frame, check every stream
split, coalescing, CRC corruption, bounds, optional FCS, opaque secure frames, strict status/correlation,
protobuf u64 values and malformed fields, and ambiguous/missing authentication flags. No hardware
success, product coverage or authenticated connection is claimed.
