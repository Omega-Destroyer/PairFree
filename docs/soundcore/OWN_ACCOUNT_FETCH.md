# Soundcore: local own-account protocol fetch

The owner requested this local tool on 2026-11-13. It follows the explicit CLAUDE.md exception for a script run by the owner with credentials typed at its prompt. The agent must not run it with a real account, request pasted credentials/tokens, inspect its login response, or extract an app secret.

## Run locally

Double-click `E:/Builds/research/soundcore-full-protocol-2026-11-13/Fetch-Soundcore-Account.cmd`, or run this in your own PowerShell terminal:

```powershell
python E:\Builds\research\soundcore-full-protocol-2026-11-13\fetch_soundcore_account.py
```

Python 3.10 or newer is required; no packages need installing. Both email and password prompts show input as you type, at the owner's request. Use an email/password Soundcore account; Google/Apple login, CAPTCHA and MFA flows are not implemented. For a mainland-China account add `--region china`. To limit metadata to particular public model codes, add e.g. `--models D1302 D1206`. The default merges the returned catalogue with the 165 locally documented registry keys.

## Important access limitation

This is an **experimental credentials-only request**, not a verified working Soundcore login client. The recovered app uses `POST /api/v2/user/login_safely`, JSON `data.email` and `data.password`. Production account origins are `speaker.eufylife.com` globally and `speaker-api.anker-in.com` for China, from `EnvConfig` and `ChangeBaseUrlInterceptor`.

`SecurityApisKt.c` selects `/v2/` requests for security processing. `RequestApiSignInterceptor` obtains an **app secret** from the vendor security library to create its signature; `EncryptInterceptor` also transforms the login body and response. The tool deliberately contains none of those secrets and does not reproduce that signed/encrypted client. The server may therefore refuse correct account details or return an encrypted response. It stops without retrying or adding vendor credentials. A personal password alone does not necessarily satisfy these separate server requirements.

If a plaintext account login succeeds, `LoginSafelyResult.data` contains the `AccountCommonModel` token/uid. The tool keeps those only in process memory. The Things request header behavior follows `GlobalHeaderRouting`: the account token in `Authorization` and `X-Auth-Token`, plus `gtoken` as the MD5 of the account UID. The catalogue and per-model metadata paths are those documented in [Things research](THINGS_PROTOCOL_5_0_35.md). No device serial, MAC, guest identifier or terminal identifier is supplied.

## Output and privacy

Only protocol catalogue/metadata and public JS/ZIP resources are saved, under:

`E:/Builds/research/soundcore-full-protocol-2026-11-13/worktree/server_dump/soundcore/own-account-<UTC timestamp>/`

This is a gitignored vendor-data directory. The terminal displays email and password as you type. The script does not log or save them, and does not print or save the login response, session token or account UID. Metadata is redacted before writing, including account/session fields and supplied credential values. Signed URLs are omitted; the downloader skips resources requiring a signature or an unverified host. Download manifests contain filenames, byte counts and SHA-256 hashes. Credentials are not accepted through arguments, environment variables or files. Python cannot guarantee immediate zeroing of immutable strings in memory; exiting the process ends its session.

The tool has no telemetry or callback to the agent, does not inspect existing app accounts, and never forwards account headers to CDN downloads. It validates HTTPS certificates, refuses redirects, disables environment proxy configuration, and spaces requests by at least 3.1 seconds. HTTP or application-level refusal ends the run. Exceptions and server message bodies are not printed, because they could contain account details. There are no registration, password-change, pairing, firmware, device-control or logout requests.

Keep account details out of chat. A failure can be described using only the displayed stage and HTTP status. Do not share raw login responses or session material.

## Verification and maintenance

`test_account_fetch_offline.py` beside the tool has eight passing offline tests: simulated login/catalogue/metadata/script retrieval, account-data exclusion from output, no account headers on CDN requests, stop on HTTP 401 without leaking error content, rejection of encrypted login data, URL/redirect/path restrictions, and interactive-input refusal for pipes. Synthetic test output stays under the ignored vendor-data tree and is removed after each test. No account or live login request was used for validation.

The recovered account/security sources used for these facts are outside Git in `C:/Users/Nima/AppData/Local/PairFreeResearch/soundcore-login-2026-11-13/` (`jadx/`, `jadx2/`, focused `SecurityApisKt.java`). They were recovered in the disposable offline emulator by resolving method definitions, without calling login, security signing, key exchange or account-state helpers. The emulator was shut down afterward. The Python tools and launcher remain outside Git, as required for vendor-server fetch code.


## HTTP 200 troubleshooting (2026-11-14)

The owner observed HTTP 200 followed by the tool's generic rejection message. This establishes that the HTTP exchange completed, not successful login, a password error, or a requirement for additional verification. The previous message grouped those possibilities too broadly.

The updated tool prints a `Response diagnostic:` line on JSON failures: fixed response-type labels, bounded numeric `res_code`/`code`/`error_code` fields, data-object shape and a boolean indicating whether a token field is present. Optional message hints are fixed English keyword categories, not server text and not a diagnosis. Unknown keys, messages, account data, token values and code values matching supplied credential material are withheld. Nothing from this diagnostic is saved to disk.

Rerun the same launcher locally. Share only the `Stage:` and `Response diagnostic:` lines if further help is needed. Do not share a login body, password, email, session token or raw response. Two additional mocked tests cover HTTP-200 application failure and diagnostic privacy; no real account was accessed by the agent.


At the owner's request, the prompts now use visible terminal input for both email and password. The launcher is unchanged. Piped input remains disabled; credential/session storage and response diagnostics are unchanged.
