# Soundcore 5.0.35 guest catalogue probe

The local probe is implemented and passes offline tests. **Live server compatibility remains unverified.** It is intended to determine whether the guest route can supply the catalogue without a personal login. It does not yet fetch per-model definitions or implement the SDK's additional encryption.

## Run locally

Double-click `E:/Builds/research/soundcore-full-protocol-2026-11-13/Fetch-Soundcore-Guest.cmd` for the global/US defaults. To select Canada, run in PowerShell:

```powershell
& 'E:\Builds\research\soundcore-full-protocol-2026-11-13\Fetch-Soundcore-Guest.cmd' --country CA
```

The launcher uses the existing local Python environment with `cryptography` installed. `--region china` selects the production China origin explicitly; the script never switches regions automatically. `--language en` is the default. Country/language are explicit inputs because the app's URL-dependent country helper remains protected; their defaults are not claimed to reproduce every installation's headers.

The script asks for no email/password and uses no existing phone/account data. It reads only the fallback-token expression from an already recovered local common-header source. It does not extract the native app signing or preset keys. Its own fresh UUID and ephemeral EC private key stay in memory. The agent has not run this live flow or read its material.

## Requests and evidence

1. `POST /passport/create_guest` on the selected production Things origin, with `client_secret_info.public_key`. Recovered `AccountApi.f`, `ThingVisitorManager$performRequestSuspend$2` and `RequestCommonInterceptor` establish the body, PHONE model type, app name, OpenUDID and signed-out common headers. The internal `BaseUrlChannel` routing header is not sent. Focused recovered `SecurityApisKt.c` selects `/v2/`; this guest path is outside that selection.
2. Require integer `code: 0`, a guest ID and a valid server EC public point. Derive a shared value in memory. P-256/raw uncompressed-point encoding and the 32-byte shared result match the recovered CommonKit helper and a synthetic SpongyCastle reference. The guest's distinct InterfaceSafe wrapper remains protected, so equivalence is still an inference to test.
3. After at least 3.1 seconds, make one `POST /app/things/get_product_list` request. `X-Auth-TS` is Unix time in seconds, `X-Auth-Token` is lower-case SHA-1 of timestamp concatenated with the guest key, and `X-Terminal-ID` is the guest ID. The signature callback, header binding and guest-ID header are directly recovered. This request deliberately does not invent the unresolved SDK crypto envelope.

The previous bare anonymous catalogue request received HTTP 401. This probe changes the request by using the app's explicit guest bootstrap and recovered guest authentication. It does not silently retry a refusal, use a different host, or claim that guest signing bypasses backend access requirements.

## Results and limits

HTTP 200 alone is insufficient. Both application envelopes and their data shapes must pass validation. Guest-bootstrap replies, private/shared keys, identifiers, headers and server messages are never saved or displayed. Fixed stage/status/structural diagnostics are displayed on failure. The tool stops immediately on refusal, unexpected data, unsupported EC encoding or an encrypted/non-JSON response.

Only a successful plaintext structured catalogue is redacted and saved to the ignored directory:

`worktree/server_dump/soundcore/guest-probe-<UTC timestamp>/catalogue.json`

No personal-account session is used or stored. There are no earbud, firmware, pairing, account-change, telemetry, or CDN requests. HTTPS certificate checking is enabled, redirects and environment proxies are disabled, and response size is bounded. Python cannot guarantee complete erasure of immutable temporary strings; the process exits after the probe.

For review, report only the fixed `Stage:`, HTTP status and `Response diagnostic:` lines, or that a catalogue was saved. No raw guest reply or authentication value is needed. If successful, the next task is to inspect the sanitized catalogue shape and extend the guest client to request per-model definitions. If refused, recover the missing SDK crypto processing before another attempt.

## Offline checks

`test_guest_fetch_offline.py` has nine synthetic tests covering header names/time units, EC agreement, strict bootstrap envelopes, identifier rejection, body/header construction, pacing, allowlisted paths, immediate HTTP-refusal handling, fallback-only material loading and sanitized/no-output result paths. Together with the existing security/account tests, 27 tests pass. All scripts and launchers stay outside Git under the research root; only these factual instructions are committed.
