# Soundcore 5.0.35 guest catalogue probe

The local probe is implemented and passes offline tests. **Owner-run result: guest bootstrap accepted, catalogue HTTP 401.** The owner reported `Stage: get_product_list; HTTP status: 401` after running the probe. Reaching that stage requires a validated guest response and successful local EC agreement, so this verifies the bootstrap path, but does not establish that the server accepted the derived key or catalogue signature. The exact cause of the catalogue refusal remains unresolved; additional SDK crypto is one unimplemented part, not a proven diagnosis. Do not repeat the unchanged request. The tool does not yet fetch per-model definitions or implement the SDK's additional encryption.

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

Every started probe now creates its output directory before loading material or making requests. A `run-report.json` records UTC start/finish times, outcome/exit code, validated milestones and the stage, HTTP status and allowlisted structural diagnostic for each request. It is updated after each request, so an interrupted process can leave a report marked `running`. The newest snapshot is also written to `server_dump/soundcore/latest-run-report.json`; previous timestamped reports remain intact. Failure produces a report even when no catalogue is saved. The account downloader uses the same reporting helper for future metadata/resource pulls. Reports contain no HTTP headers, response bodies, server messages, credentials or session values. A disk/permission failure can prevent report creation; the tool then stops before making requests.

Only a successful plaintext structured catalogue is redacted and saved to the ignored directory:

`worktree/server_dump/soundcore/guest-probe-<UTC timestamp>/catalogue.json`

No personal-account session is used or stored. There are no earbud, firmware, pairing, account-change, telemetry, or CDN requests. HTTPS certificate checking is enabled, redirects and environment proxies are disabled, and response size is bounded. Python cannot guarantee complete erasure of immutable temporary strings; the process exits after the probe.

For future review, inspect `latest-run-report.json` and the corresponding timestamped folder directly; terminal copying is no longer needed. No raw guest reply or authentication value is needed. If successful, the next task is to inspect the sanitized catalogue shape and extend the guest client to request per-model definitions. After the observed refusal, recover the missing SDK processing and verify the catalogue requirements before another attempt. The earlier run predates file reporting; its result is recorded here from the owner's message, not represented as a machine-generated report.

## Offline checks

`test_guest_fetch_offline.py` has ten synthetic tests covering header names/time units, EC agreement, strict bootstrap envelopes, identifier rejection, body/header construction, pacing, allowlisted paths, immediate HTTP-refusal handling, fallback-only material loading and sanitized output/report paths. A full simulated bootstrap-200/catalogue-401 run checks that both stages and the validated-bootstrap milestone are saved without guest material. `test_run_report_offline.py` checks diagnostic allowlisting, latest-report replacement and preservation of earlier runs. Together with the existing security/account tests, 29 tests pass. All scripts and launchers stay outside Git under the research root; only these factual instructions are committed.
