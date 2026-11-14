# Soundcore Things transport follow-up

The owner-run guest probe accepted `passport/create_guest`, then received HTTP 401 at `app/things/get_product_list`. That observation validates guest response parsing and local EC-point agreement, but not acceptance of the guest signature by the catalogue service. The previous client omitted the SDK's separate transport identity, encryption and signature. The exact server reason for the 401 was not captured and must not be stated as proven.

The external probe now has a materially different **experimental** profile, `guest-mega-v1`: guest creation, a separate transport key exchange, then one encrypted/signed catalogue request. Its implementation has passed synthetic tests; the owner has not yet tested this profile against Soundcore. It never retries or substitutes another host after refusal.

## Evidence boundaries

| Component | Evidence in Soundcore 5.0.35 | Remaining limit |
| --- | --- | --- |
| Guest bootstrap | Recovered `ThingVisitorManager`, request/response models, common interceptor | Earlier owner run reached catalogue stage successfully |
| Guest headers | Recovered timestamp/SHA-1 callback, `SoundCoreIotKit.setInfoProvider$lambda$0`, `GlobalHeaderRouting` | Earlier catalogue request still refused |
| Separate exchange route | `OpenApiNetApi` has `POST openapi/oauth/key/exchange` | Effective routing implementation has protected helpers |
| Exchange fields | `ExchangeKeyRequest.client_public_key`, `ExchangeKeyResponse.server_public_key` | ESIot serializer bodies still contain instruction-store markers |
| Transport prerequisite | Recovered CryptoRouting request bytecode calls `getSecurityKey`, `isExchanging`, `awaitExchangeKey`, `ensureFirstKeyExchange` before proceeding | Several orchestrator helpers remain packed |
| Preset selection | `SoundCoreThingIot.f()` calls `WebSocketServiceManager.k()` | This is a distinct source from the legacy native signing getter |
| Production branch | First preset-return branch compares the environment returned by `BuildEnv.b()` when `BuildEnv.j()` selects production | Do not mistake the final unconditional return for production |
| AES construction | Recovered CommonKit `EncryptUtil`: CBC/PKCS7, random 16-byte IV prefixed to ciphertext, Base64; body key uses first 16 shared bytes | Direct ESIot `EncryptKeyFactory` bodies remain packed |
| Signing key and canonical input | Recovered CommonKit `EcdhKeyUtils.getSecurityKey()` truncates shared bytes to 16 and hex-encodes them; `EncryptUtil.encryptByHMAC()` signs timestamp, nonce and optional body joined by plus signs using UTF-8 key text | Equivalence with this ESIot version is supported by the related implementation below, not a new runtime observation |

Raw request-pipeline code was decompiled again with JADX `--show-bad-code --comments-level debug`; the result remains imperfect coroutine Java and must be checked against bytecode when branch ordering matters. No successful new emulator capture is claimed. Comparing recovered trees confirmed that `CryptoRequestFunctions`, `EncryptKeyFactory`, `KeySaverFactory` and `ExchangeKeyHandler` still have protected bodies. Searching 200 already-restored instruction sequences in the instruction-store asset found no plaintext matches; that check does not establish its cipher or compression format.

## Related SDK implementation used as a check

[bropat/eufy-security-client](https://github.com/bropat/eufy-security-client/tree/3bfef130bdcbff8f2f68286a5f63ec7e48177202) implements the related Mega transport under MIT (Patrick Broetto and contributors). Its [crypto module](https://github.com/bropat/eufy-security-client/blob/3bfef130bdcbff8f2f68286a5f63ec7e48177202/src/http/megaCrypto.ts) and [HTTP module](https://github.com/bropat/eufy-security-client/blob/3bfef130bdcbff8f2f68286a5f63ec7e48177202/src/http/megaApi.ts) distinguish a preset-wrapped exchange from subsequent ECDH traffic. A [first-hand reverse-engineering discussion](https://github.com/bropat/eufy-security-client/issues/933) led to that reference; its initial handshake claims were later corrected, so the earlier comment alone is not a specification.

The new Python implementation uses these wire choices where the Soundcore ESIot body is still protected:

- A fresh P-256 transport key pair, separate from the guest-auth key pair, and a client-generated key identifier.
- Exchange JSON containing the preset-encrypted public-point hex in `client_public_key`. The server's `server_public_key` is decrypted with the preset before deriving the transport shared secret.
- HMAC-SHA256 over timestamp, nonce and the wrapped public key for exchange; subsequent requests sign the encrypted body with the first 32 hex characters of the shared secret as ASCII, distinct from the 16 decoded bytes used for AES.
- Raw Base64 ciphertext for the normal request body, with key identifier, encryption, timestamp, nonce, replay and signature headers. The separate Soundcore guest-auth headers remain present.
- Successful encrypted response `data` is decrypted and parsed before saving a sanitized catalogue. Structured plaintext success data is also accepted; unsupported envelopes stop the run.

These are cross-app SDK inferences with matching local CommonKit algorithms and API shapes. They are not proof that every Eufy transport choice applies to Soundcore. No Eufy key, host, account flow, server static point or session is used. If the new exchange or catalogue is refused, inspect the saved stage and recover the corresponding Soundcore helper before another attempt.

## Local handling and verification

`soundcore_things_transport.py`, the updated `fetch_soundcore_guest.py`, and tests remain outside Git under `E:/Builds/research/soundcore-full-protocol-2026-11-13/`. The owner-run loader pins the recovered preset source file's SHA-256 and reads the verified production branch locally. Key values are not embedded in the scripts or docs, printed, accepted as arguments, serialized, or saved in reports. No production preset was used for a network request by the agent. Inspection returned only source hashes, branch comparisons and format booleans, never key literals.

38 offline tests pass across security, guest, transport, reporting and account tools. Nine new transport cases cover an independent JDK25 AES/HMAC reference, ASCII-versus-binary HMAC key representation, wrapped server EC points, raw encrypted request bodies, response decoding, source pinning/production selection, clearing/serialization refusal, the full three-request mock flow and immediate stop at exchange/catalogue refusal. Saved fixtures exclude session/key values. The historical two-request 401 fixture remains explicitly separate from the new profile.

The report records `guest-mega-v1` and milestones `guest_bootstrap_validated`, `things_transport_established` and `catalogue_saved`, where reached. Failures carry a fixed category and per-request HTTP/structural diagnostics. This is sufficient to review the next run directly from local files without terminal copying. See [run instructions](GUEST_PROTOCOL_PROBE.md).
