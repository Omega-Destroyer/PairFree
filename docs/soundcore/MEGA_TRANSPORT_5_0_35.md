# Soundcore Things transport follow-up

**Update, 2026-11-15:** The [offline unpack](OFFLINE_UNPACK_5_0_35.md) has recovered the previously protected security helpers. Direct Soundcore code now confirms several transport choices described below as cross-app inferences. The historical evidence table retains its earlier scope; the guest script and catalogue 401 are unchanged, and no new live request was made.

The owner-run guest probe accepted `passport/create_guest`, then received HTTP 401 at `app/things/get_product_list`. That observation validates guest response parsing and local EC-point agreement, but not acceptance of the guest signature by the catalogue service. The previous client omitted the SDK's separate transport identity, encryption and signature. The exact server reason for the 401 was not captured and must not be stated as proven.

The external probe has a materially different **experimental** profile, `guest-mega-v1`: guest creation, a separate transport key exchange, then one encrypted/signed catalogue request. The owner subsequently tested this profile. Its saved report establishes the results below. It never retries or substitutes another host after refusal.

## Owner-run encrypted-profile result

The report started at `2026-11-14T22:12:19Z` and recorded:

| Stage | HTTP status | Local validation |
| --- | --- | --- |
| `create_guest` | 200 | Guest response and EC agreement accepted |
| `exchange` | 200 | Success envelope and preset-wrapped server EC point accepted; transport shared value derived |
| `get_product_list` | 401 | Refused; no catalogue saved |

Milestones `guest_bootstrap_validated` and `things_transport_established` are present. The saved `latest-run-report.json` contains only safe diagnostics, not bootstrap replies or keys. This is live evidence for the exchange encoding and preset/server-point handling, but it does not prove acceptance of the later guest-auth signature, HMAC, ciphertext or permissions. Do not describe the remaining failure as a failed exchange or assert a particular server reason.

The refusal body was discarded by that script version, leaving an empty refusal diagnostic. Future runs inspect at most 8 KiB of the already-returned error body locally and retain only allowlisted response shape, numeric codes and fixed wording categories. Raw bodies, message text and headers are never saved or displayed. This does not trigger another request or retry. **No authentication change was made in this follow-up, and no unchanged rerun is requested merely to obtain the new diagnostic.**

Additional offline checks confirmed that `SoundCoreThingIot$getProductList$2` explicitly calls `getProductList("soundcore")`. The SDK's generic `CATEGORY_SOUND_CORE` value is `eufy_soundcore`, but that different constant is not evidence for changing this request's explicit category. No speculative category substitution was enabled.

The actual guest helpers live in the earlier `dex-9882140` capture under `com.acc.interfacesafe`; focused decompilation confirms `ECDH.b` and `ECDHJava` still have instruction-store stubs. `AccountBaseUtil.j`/`D` are also protected in the inspected tree. The guest-specific agreement/formatting and storage-accessor behavior therefore remain recovery targets; the recovered CommonKit reference alone cannot establish every detail of those distinct helpers. This focused decompilation is not a newly successful runtime capture.

**Blocked for the current catalogue profile:** server access refusal; checked: recorded owner-run guest/exchange successes followed by catalogue 401, direct catalogue caller/category, guest callback/header binding, transport reference and remaining protected guest/helper bodies. Recover those semantics and/or obtain a safe refusal classification during a justified changed run before making another access attempt. This is not proof that the catalogue always requires a personal login.

### Agent-run diagnostic follow-up

The owner subsequently clarified that the agent may run scripts itself when no personal login credentials are needed. This authorizes the local guest probe with security material handled internally and excluded from output; it does not authorize reading a personal account/session or running the separate personal-login tool with real credentials.

Two bounded diagnostic runs were made under that clarification. Both validated guest creation and transport exchange, then stopped at catalogue HTTP 401. The first exposed an overly broad diagnostic redaction rule: a numeric code was withheld when its digits occurred inside a random longer key. The filter now suppresses a numeric code when it equals a supplied credential value, rather than for incidental substrings. Its exact-credential privacy case still passes. The corrected run, started at `2026-11-14T22:23:18Z`, records application `code: 401`, missing `data`, and no classified message hint. It supplies no specific server explanation. No requests followed the refusal within either run, and no format/host/authentication fallback was tried.

The script's guest authentication and encrypted catalogue request are unchanged by this diagnostic follow-up. No further unchanged live probe is warranted. The current recovery target remains the actual InterfaceSafe guest agreement/formatting and cache accessors, rather than guessing alternate key formats. The second run and all safe reports remain under the ignored vendor-data directory.

The subsequent owner-selected [focused helper recovery pass](HELPER_RECOVERY_5_0_35.md) indexed the exact targets and tested the loader offline. It recovered no new method bodies and made no vendor-server requests; catalogue authentication remains unchanged.

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

41 offline tests pass across security, guest, transport, reporting and account tools. Nine transport cases cover an independent JDK25 AES/HMAC reference, ASCII-versus-binary HMAC key representation, wrapped server EC points, raw encrypted request bodies, response decoding, source pinning/production selection, clearing/serialization refusal, the full three-request mock flow and immediate stop at exchange/catalogue refusal. Diagnostic cases verify bounded error-body inspection, exclusion of raw server messages/credentials and correct handling of incidental code substrings in random keys. Saved fixtures exclude session/key values. The historical two-request 401 fixture remains explicitly separate from the new profile.

The report records `guest-mega-v1` and milestones `guest_bootstrap_validated`, `things_transport_established` and `catalogue_saved`, where reached. Failures carry a fixed category and per-request HTTP/structural diagnostics. This is sufficient to review the next run directly from local files without terminal copying. See [run instructions](GUEST_PROTOCOL_PROBE.md).
