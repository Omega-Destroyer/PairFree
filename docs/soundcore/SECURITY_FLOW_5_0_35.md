# Soundcore 5.0.35: local security implementation status

**Status: incomplete. The encrypted login/download client is not ready.** This pass adds verified signing components and an EC reference implementation, not a working replacement for the vendor's full security stack. The launcher now stops before requesting credentials or making network calls while required pieces remain unverified. `--security-status` prints the exact state; `--legacy-unsigned` explicitly selects the old diagnostic that the server previously refused.

## Authorization and scope

On 2026-11-14 the owner expressly authorized this Soundcore local tool to use the embedded app signing secret, with the conditions that its value not be displayed, returned to the agent or committed. This is a specific exception to the earlier exclusion of app server credentials. It does not authorize reading the owner's password, account files or phone session, or committing any key/vendor code. No Soundcore network request or personal-account login was attempted by the agent in this implementation pass.

## Implemented outside Git

`E:/Builds/research/soundcore-full-protocol-2026-11-13/soundcore_app_security.py` contains:

- A local material provider pinned to the original APKM and its embedded native security payload hashes. It locates the production-default native getter data without embedding credential values in the Python source. The provider also reads the previously recovered common-header fallback expression locally. It does not open secret-asset files, print material or make network requests.
- Redacted object representations, refusal to pickle material/private keys, clearing of mutable key buffers, and fixed error messages that omit source values. Python cannot guarantee complete erasure of all temporary immutable copies; process exit remains part of the boundary.
- The legacy selected-header signature algorithm, common-header fallback-token algorithm and Things guest-auth callback calculation.
- A P-256 helper using uncompressed EC points and a synthetic reference checked against SpongyCastle's AES shared-secret result.

No actual vendor secret value is included in these scripts or this document. The material provider has not been used to authenticate to a server. Tests use synthetic material and synthetic EC private scalars.

## Legacy email/password login

`RequestApiSignInterceptor.b` constructs a map beginning with the app secret. It conditionally includes lower-case `x-request-ts`, `terminal-id`, `language`, `country`, `user-agent` and `x-request-once`; raw encoded query fields are included for GET. It sorts by key, joins the values with ampersands and hashes with the MD5 helper. The implementation rejects unsupported query shapes rather than silently altering them. POST body bytes are not part of this signature.

The interceptor adds request timestamp, a request-once UUID and `unique-sign`. `SecurityApisKt.c` selects `/v2/` paths for the security processing. `EncryptInterceptor` passes the request JSON to `AesSafeUtil`, using the request signature before login and stored AES state after login. Its response path recognizes CBC and CFB handling.

**Unresolved:** `AesSafeUtil` key transformation, exact IV/padding/ciphertext envelope, and `KeyExchangeInterceptor` request/response details remain protected. Standard AES primitives alone cannot establish those choices. No guessed encryption profile was enabled.

## Things guest flow: a separate path

`ThingVisitorManager` requests `passport/create_guest` with `client_secret_info.public_key`. Its API method includes PHONE model type and Soundcore app name, and uses an OpenUDID argument. The normal common interceptor also supplies terminal and other app headers. A future standalone client must use its own generated ephemeral identifier, not read the owner's phone identifier.

The response model contains `guest_id` and `server_secret_info.public_key`. The manager derives shared key material, stores guest identity/key state, and refreshes the Things configuration. This is a possible route to cloud definitions without personal login, but the end-to-end guest client has not been implemented or verified.

The recovered **CommonKit** ECDH helper uses curve P-256, provider SC, uncompressed EC point hex, and `KeyAgreement.generateSecret("AES")`. An independent synthetic reference using SpongyCastle 1.58.0.0 returned 32 shared bytes; the Python implementation matches it in both directions. The guest caller uses the separate protected InterfaceSafe wrapper, so equivalence of its full encoding/derivation must still be verified. The helper/reference evidence must not be promoted to an observed guest-server handshake.

`SoundCoreThingIot.c/m/n` establishes **SHA-1 of timestamp concatenated with the selected key, formatted as lower-case hex**. Its key selector returns the cached guest key when signed out and an empty string when signed in. In this path the selected guest key is negotiated state, not the embedded app signing secret. This corrects the earlier assumption that all timestamp signing used a static vendor credential.

**Unresolved:** the callback's actual request-header binding, the Things preset-key selection, the SDK's additional exchange and its encrypted request/response envelope. `SoundCoreIotKit.setInfoProvider` and relevant `CryptoRequestFunctions` implementations remain protected. Header names must not be guessed from the existence of constants such as X-Signature or X-Auth-Token.

## Recovery attempts and evidence limits

The previously successful emulator extraction no longer reaches the full harness in the tested setup. A fresh API30 research guest also failed before capture. Definition-only bootstrapping reached native-library preparation, then the protected classloader call terminated the process. No new crypto method-body recovery is claimed.

The pass tried the original and compatibility APK paths, both native host widths, streamed installation, a definition-only manifest/bootstrap, original public-certificate/ABI metadata in the disposable lab, and a reversible lab-clock check. None produced the missing definitions. The lab metadata and clock were restored, and the two headless research guests were stopped. The owner's phone and its account storage were untouched throughout this pass.

The external `instrumentation/run_probe.py` now requires both successful instrumentation result code and the expected completion marker before pulling outputs. A zero shell exit code or an old output folder is not proof of a new capture. Earlier committed successful observations remain historical evidence; failed current runs do not replace or extend them.

This remains a loader/semantic-recovery task, not a claim that native code is impossible to analyze. Continue by identifying the protected-loader exit path or recovering the missing helper definitions through another verified runtime/build. Do not send guessed encrypted login attempts while the format remains unknown.

## Validation and artifacts

18 offline tests pass: nine account-tool/privacy/gating cases and nine material/signing/EC cases. They cover sorted header signing, raw query preservation, credential representation/serialization, cleared-key refusal, bounded ELF mapping, invalid EC point handling, the synthetic SpongyCastle reference and stopping the normal CLI before any credential prompt or request.

- External scripts: `soundcore_app_security.py`, `test_security_profile_offline.py`, `test_account_fetch_offline.py`, and `fetch_soundcore_account.py` under the research root above.
- Crypto environment, native payload copies and recovery experiments: `C:/Users/Nima/AppData/Local/PairFreeResearch/soundcore-crypto-2026-11-14/`.
- Synthetic Java reference: `reference/EcdhReference.java`, with published SpongyCastle core/prov 1.58.0.0 dependencies kept outside Git.
- Existing recovered sources: the earlier `discovery-jadx/` tree and `soundcore-login-2026-11-13/jadx2/`. No method body is considered recovered solely because a decompiler wrote a Java file.

## Privacy incident

A source-inspection command accidentally returned embedded app-key literals in tool output, bringing those values into this conversation's tool context. The owner was informed. Their values are not repeated here, were not copied into the implementation or committed, and no personal password/account token was accessed. Consequently, this pass must not be described as having kept every embedded app key out of the conversation. Future source inspection must redact literals before returning output; directly printing a line range around a key-related method is unsuitable.
