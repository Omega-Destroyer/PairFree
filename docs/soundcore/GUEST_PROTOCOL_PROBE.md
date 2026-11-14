# Soundcore 5.0.35 guest catalogue probe

The local probe is implemented and passes offline tests. **Latest owner-run result: guest bootstrap and separate transport exchange accepted, encrypted catalogue HTTP 401.** The `guest-mega-v1` report records both validated milestones before the catalogue refusal. The transport addition worked through the exchange; it did not resolve catalogue access. The current follow-up improves safe failure diagnostics only and does not change catalogue authentication or request an unchanged rerun. [Transport findings and evidence limits](MEGA_TRANSPORT_5_0_35.md) record the exact observation and remaining protected helpers. Per-model downloads remain a later step after catalogue success.

## Run locally

Double-click `E:/Builds/research/soundcore-full-protocol-2026-11-13/Fetch-Soundcore-Guest.cmd` for the global/US defaults. To select Canada, run in PowerShell:

```powershell
& 'E:\Builds\research\soundcore-full-protocol-2026-11-13\Fetch-Soundcore-Guest.cmd' --country CA
```

The launcher uses the existing local Python environment with `cryptography` installed. `--region china` selects the production China origin explicitly; the script never switches regions automatically. `--language en` is the default. Country/language are explicit inputs because the app's URL-dependent country helper remains protected; their defaults are not claimed to reproduce every installation's headers.

The script asks for no email/password and uses no existing phone/account data. It reads the fallback-token expression and the production Things preset from already recovered local sources; the preset-source file is hash-pinned. It does not extract the legacy native signing key. Its fresh UUIDs, guest and transport EC private keys, guest state and transport keys stay in memory. The agent has not run this live flow with production material. These launch instructions describe how to run it when a further justified change is ready; the observed refused request should not be repeated unchanged.

## Requests and evidence

1. `POST /passport/create_guest` on the selected production Things origin, with `client_secret_info.public_key`. Recovered `AccountApi.f`, `ThingVisitorManager$performRequestSuspend$2` and `RequestCommonInterceptor` establish the body, PHONE model type, app name, OpenUDID and signed-out common headers. The internal `BaseUrlChannel` routing header is not sent. Focused recovered `SecurityApisKt.c` selects `/v2/`; this guest path is outside that selection.
2. Require integer `code: 0`, a guest ID and a valid server EC public point. Derive a shared value in memory. P-256/raw uncompressed-point encoding and the 32-byte shared result match the recovered CommonKit helper and a synthetic SpongyCastle reference. The guest's distinct InterfaceSafe wrapper remains protected, so equivalence is still an inference to test.
3. After at least 3.1 seconds, make one `POST /openapi/oauth/key/exchange` with a separate preset-wrapped transport public key and HMAC signature. Require a successful response and valid wrapped server EC point. The endpoint, fields and production-preset selector are from Soundcore; the wire orchestration is supported by the related public Mega implementation and recovered local CommonKit algorithms.
4. After at least another 3.1 seconds, make one encrypted/HMAC-signed `POST /app/things/get_product_list`. `X-Auth-TS` is Unix time in seconds, `X-Auth-Token` is lower-case SHA-1 of timestamp concatenated with the guest key, and `X-Terminal-ID` is the guest ID. These guest headers are distinct from `X-Request-Ts`, `X-Request-Once`, `X-Key-Ident` and `X-Signature` in the transport layer. Decrypt successful encrypted data before validating and saving the catalogue. Refusal at either step ends the run without retry or plaintext fallback.

The previous bare anonymous catalogue request received HTTP 401. This probe changes the request by using the app's explicit guest bootstrap and recovered guest authentication. It does not silently retry a refusal, use a different host, or claim that guest signing bypasses backend access requirements.

## Results and limits

HTTP 200 alone is insufficient. Application envelopes and data shapes must pass validation. Guest-bootstrap/key-exchange replies, private/shared/preset keys, identifiers, headers and server messages are never saved or displayed. Fixed stage/status/structural diagnostics are displayed on failure. On HTTP refusal, at most 8 KiB of the existing response body is inspected transiently for allowlisted JSON shape/codes and fixed wording categories; raw error text and headers remain excluded. The tool stops immediately on refusal, unexpected data, unsupported EC encoding, decryption failure or non-JSON response.

Every started probe creates its output directory before loading material or making requests. A `run-report.json` records UTC start/finish times, profile, outcome/exit code, fixed failure category, validated milestones and the stage, HTTP status and allowlisted structural diagnostic for each request. It is updated after each request, so an interrupted process can leave a report marked `running`. The newest snapshot is also written to `server_dump/soundcore/latest-run-report.json`; previous timestamped reports remain intact. Failure produces a report even when no catalogue is saved. The account downloader uses the same reporting helper for future metadata/resource pulls. Reports contain no HTTP headers, response bodies, server messages, credentials or session values. A disk/permission failure can prevent report creation; the tool then stops before making requests.

Only a successful plaintext structured catalogue is redacted and saved to the ignored directory:

`worktree/server_dump/soundcore/guest-probe-<UTC timestamp>/catalogue.json`

No personal-account session is used or stored. The exchange creates a fresh transport identity; it does not change a personal account. There are no earbud, firmware, pairing, telemetry, or CDN requests. HTTPS certificate checking is enabled, redirects and environment proxies are disabled, and response size is bounded. Python cannot guarantee complete erasure of immutable temporary strings; the process exits after the probe.

For future review, inspect `latest-run-report.json` and the corresponding timestamped folder directly; terminal copying is no longer needed. No raw guest reply or authentication value is needed. The latest report has been inspected and documented. A further request needs a justified authentication change; the added refusal diagnostics alone are not a fix. If successful later, inspect the sanitized catalogue shape and extend the client to request per-model definitions. The original two-request run predates file reporting; its result is recorded from the owner's message rather than represented as a machine-generated report.

## Offline checks

40 offline tests pass across the transport, guest, security, account and reporting modules. They include the previous two-request refusal fixture, the complete three-request encrypted flow, exchange/catalogue refusal without fallback, sanitized output, bounded safe error diagnostics, source pinning/production selection, and AES/HMAC results checked independently with JDK25. All scripts and launchers stay outside Git under the research root; only factual documentation is committed.
