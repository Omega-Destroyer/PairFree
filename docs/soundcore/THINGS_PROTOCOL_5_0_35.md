# Soundcore 5.0.35-435: Things / OMC protocol path

This is a third protocol/inventory path alongside the compiled app and SDK registries. It is required before claiming coverage of every device supported by the installed vendor app.

## Model discovery

`BaseSeriesHelp.m()` reads online product definitions through `ThingsModelUtil.f()`, creates `ThingsProductModel` instances, copies their SPP UUIDs and merges them into the normal model map. `ThingsModelUtil.d()` supplies a separate OMC product list. The list implementations are storage-backed; existing preferences and accounts were not read in this research.

`ProductListViewModel` combines online series/product-code data with Things product descriptions. The resulting model metadata includes product code, name, identifier, SPP/service UUIDs, UUID prefix/suffix and a Soundcore device-type flag. UUID defaults in that conversion are fallback values, not evidence that all online products share those identifiers.

The 165 compiled-registry keys are therefore a measured inventory of the APK's explicit registrations, not a ceiling on online support. The full online catalogue and per-product definitions have not yet been recovered. Resource names, cached list types and generic Things support cannot supply that missing count.

## Packet construction path

```mermaid
flowchart LR
    A[App setting] --> B[ThingsCmdService: named function and JSON]
    B --> C[IotFunctions / ESControlParam]
    C --> D[PN-specific QuickJS handler]
    D --> E[DeviceCmdInfoModel / CmdBle]
    E --> F[SendCmdDataBean]
    F --> G[Soundcore packet wrapper and transport]
```

`IotFunctions.jsonToControlResultModel` combines caller JSON with a device value, creates an `ESControlParam` containing the product identifier and named function, calls `ESIotDeviceControlManager.controlDevice`, then decodes the returned data JSON into `DeviceCmdInfoModel`. The inverse route uses `hexToStatusResultModel` for received status.

`ESIotDeviceControlManager` maintains a controller per product number. `ESIotDeviceAndroidHandlerQuickJs` runs the product's JavaScript and uses the `controlDevice` and `deviceStatus` entry points. It obtains script content through `TDRepository.getJSFileContent`, with a `downloadJSFileByPN` path when needed. `TDRepositoryImpl` also exposes local/cloud Thing Description retrieval. This explains why recovering generic Java classes alone cannot establish the exact function bytes for all online products: the per-product script and description are inputs to the encoding and decoding.

`CmdBle.getRealCmdHeader` concatenates `cmdHead` and `cmd`, converting integer values to bytes. `getRealCmdBody` converts the body list to bytes. `ThingsCmdService.E6` places the returned prefix into the command-without-additional-payload route; `F6` supplies prefix and array payload separately. Neither helper by itself establishes a complete transmitted frame. Resolve the selected transport/wrapper and returned command metadata as well.

## Named functions used by the app

The following are identifiers and JSON field names from the recovered app bridge. They are not inferred wire opcodes. Fallback names refer to the superclass implementation when no schema result is available.

| Bridge method | Function identifier | JSON fields | Legacy fallback method |
| --- | --- | --- | --- |
| G1 | SET_TOUCH_LOCK | switch | X3 |
| H0 | GET_AUDIO_PLAY_TYPE | No JSON property supplied | H0 |
| M0 | SET_ANC_STATE | sc_anc_mode, sc_anc_option_manual, sc_anc_option_auto, sc_trancparency_sub_mode, sc_anc_auto_mode, sc_is_wind_noise_suppression | M0 |
| S3 | CONNECT_TO_SPECIFIED_DEVICE | mac | S3 |
| X3 | SET_LOW_POWER_TONE | switch | X3 |
| Z1 | SET_KEY_TONE | switch | Z1 |
| Z3 | REMOVE_FROM_HISTORICAL_DATA | mac | Z3 |
| a4 | TEST_HEADSET_FIT | No JSON property supplied | a4 |
| j0 | RE_SET_CUSTOM_UI | No JSON property supplied | j0 |
| m1 | DISCONNECT_THE_SPECIFIED_DEVICE | mac | m1 |
| p4 | SET_CUSTOM_UI | earphone_type, un_tws_fun, tws_fun, btn_type | p4 |
| r0 | OPEN_FIND_DEVICE | switch_left, switch_right, switch_case | r0 |
| r3 | SET_ONE_TO_TWO_FUNCTION | switch | r3 |
| s1 | CONNECT_NEW_DEVICES | No JSON property supplied | s1 |

The ANC bridge supplies mode, manual/automatic levels, transparency submode, automation mode and wind-noise suppression separately. The custom-UI bridge supplies side, gesture, TWS function and non-TWS function as named properties; their serialization is delegated to the product script. These field names must not be converted to fixed byte positions without that script's evidence.

## Recovery and verification status

An additional **351 Things/schema/repository classes with 2,353 methods** were selected and recovered through the offline instrumentation harness. Their live DEX index has zero instruction-store markers remaining. This is in addition to the earlier 2,984-class protocol selection. All raw/vendor artifacts remain outside Git. `TDRepositoryImpl.downloadJSFileByPN` still has a Java decompiler failure despite restored instructions; examine its bytecode or a different decompiler before drawing conclusions about that branch.

The bundled `assets/resource.zip` was inspected by archive member names only: its 319 entries describe ambient sound resources, not an identified per-product protocol script bundle. No inference is made about other encrypted or downloaded resources from that check.

No Things command was sent or invoked through the encoding engine. Two unsigned anonymous API requests were subsequently attempted, as recorded below; no account login or app credential was used. Virtual checks cover the [expanded serializer/parser cases](VIRTUAL_PROTOCOL_CHECKS_5_0_35.md) and the pure firmware predicates in the [feature mapping](MODEL_FEATURES_5_0_35.md).

## Remaining requirements for full coverage

1. Recover the active online product catalogue, including region/version selection and entries absent from compiled registries.
2. Trace the repository/script manager to the per-product descriptions and JavaScript resources. Preserve product code, version and content hashes. Inspect bundled resources or use only permitted unauthenticated vendor resources; do not reuse account credentials or expose signing material.
3. Trace each named function to its wire command, ordered payload and status fields. Record unsupported functions explicitly and preserve any legacy fallback behavior.
4. Exercise those scripts/serializers offline with synthetic state, including firmware boundaries, before marking their commands virtually checked.

Local continuation artifacts: `things-protocol-index.json`, `things-recovery-summary.json`, `live-fixed/`, `live-jadx/sources/com/anker/esiotkit/` and the focused `td-jadx/` tree under the external research root listed in the recovery document.

## Script discovery follow-up

A further 95 selected script-discovery/request-routing classes (601 methods) were recovered with zero remaining instruction-store markers in those classes. The new source tree is `discovery-jadx/`, with measurements in `script-discovery-summary.json` and `script-discovery-class-index.json` outside Git.

`JSFileManagerImpl` computes a document directory per configured environment and a `JavaScript` child directory. The observed naming variants are `<PN>.js`, `<PN>.zip`, `<PN>New.js`, `<PN>New.zip` and `<PN>Handle.mix.js`. The `New` suffix is controlled by its `isNew` argument. These are source-derived paths, not files read from an existing user's installation. Its read path can call back into the repository to obtain a missing product resource.

All ten APK members were checked by archive entry names for product-script/archive patterns. The only three `.js` entries were Flutter toast/wakelock helpers and `WebViewJavascriptBridge.js`; none was a PN-specific Things handler. One broad PN/archive name match was `assets/lottie/a3953_run_data.zip`, an animation-path candidate, not an identified protocol bundle. This is a filename inventory, not proof that no differently named or encrypted bundle exists. Secret assets were not opened.

### Catalogue request path

`ThingNetApi` exposes these relative POST paths relevant to catalogue/configuration discovery:

| Relative path | Result role |
| --- | --- |
| `app/things/get_product_list` | Product descriptions; includes a Category header argument |
| `app/things/get_product_category_list` | Product categories |
| `app/things/get_product_config` | Product/Thing configuration; two typed overloads |
| `app/things/get_things_list` | Thing Description model results |

Other methods query pairing information or resolve a PN from an SN. The SN lookup is not a permitted shortcut for this catalogue research because it requires a device identifier. Only the product-list and Thing Description routes were attempted anonymously below. Pairing and SN lookup routes were not called.

Request construction is more involved than an optional account login: `GlobalHeaderRouting` adds account-token headers only when its provider returns a token; `CryptoRouting` defaults its encryption requirement to true, with an explicit internal opt-out; and Soundcore registers a timestamp-signing callback through `SoundCoreThingIot.j`/`k`. The callback delegates to its secret-dependent signing routine. That callback was not invoked and no secret value or credential was read or used.

The routing code alone does not establish anonymous access. Actual unsigned requests to two production routes subsequently returned HTTP 401; see the request-level evidence below. This does not establish that every vendor resource or public CDN is authenticated. Other routes remain untested; app signing material must not be replayed to access them.


## Anonymous fetch results ? 2026-11-13

The current production Things origin was traced through `ThingsEnvUtil.a` to `AIOTDomain.e`: `https://aiot-sc-api-pr.soundcore.com`. A separate China origin is configured in code, but was not tried after the global refusal. Anka origins belong to a different configuration path and were not substituted as authentication workarounds.

| Request | JSON body | Result |
| --- | --- | --- |
| POST `/app/things/get_product_list` | `{}` | HTTP 401; 32-byte reply; stopped |
| POST `/app/things/get_things_list` | `{"product_codes":["D1302"],"code_time_map":{}}` | HTTP 401; 32-byte reply; stopped |

Both requests used the public `Category: soundcore` header and JSON Accept/Content-Type. They used no authorization, app signing, preset key, cookies, account data or device identifier. D1302 is a public model code. There was no alternate-host retry or authentication attempt. **Zero protocol scripts were downloaded.**

Exact DTO element names were inspected through the vendor's Kotlin serialization descriptors in the offline emulator:

| DTO | Serialized properties |
| --- | --- |
| GetProductListRequest | `product_codes` (empty default omitted by the app's normal serialization) |
| GetThingListRequest | `product_codes`, `code_time_map` |
| GetProductDataRequest | `product_codes` |

The repository's method named `getTDBySnList` uses `GetThingListRequest`; the inspected non-existing-product caller supplies product numbers, not individual serial numbers. This distinguishes the direct model-metadata attempt from the separate prohibited SN lookup route.

`TDProfile` supplies `plugin_path` / `pluginPath` and `plugin_path_zip` / `pluginPathZip`, plus script IDs, product code and version. The script manager prefers the ZIP variant when present and has a plain-JS alternative. CDN locations must come from verified metadata; local `<PN>.js` filenames are not evidence of a remote URL.

The external script is `E:/Builds/research/soundcore-full-protocol-2026-11-13/fetch_cloud_protocols.py`. Its modes are `--discover --download`, `--protocol-metadata D1302 --download`, and `--metadata <saved-reply> --download`. The first two have already been attempted and refused; do not repeat those unchanged requests. The downloader:

- spaces requests by at least 3.1 seconds and retains hashes/status/provenance;
- accepts only verified vendor HTTPS hosts and protocol `.js`/`.zip` resources;
- refuses signed/authentication query parameters, device identifiers, firmware paths and redirects;
- stops on HTTP 401/403 or application-level authentication/non-success results;
- stores all raw replies only under the ignored `server_dump/soundcore/cloud-protocol-2026-11-13/` directory.

The original catalogue observation remains in `catalogue.json.meta.json` and `run-status.json`; the independent metadata observation is in `protocol-metadata-D1302.json.meta.json` and `protocol-metadata-D1302-status.json`. Raw replies and downloaded vendor content stay out of Git.

**Blocked for these unsigned routes:** authentication/access refusal; checked: production origin and request DTO/caller tracing, then one anonymous request to each endpoint, both HTTP 401. Other public resource paths are still research work; these results are not a universal login requirement. The full online inventory and product scripts remain unresolved.


## Owner-run account tool

At the owner?s request, a separate [local own-account tool](OWN_ACCOUNT_FETCH.md) now prompts for hidden credentials and can attempt the documented login/catalogue/metadata path. Only mocked offline tests have run. The app also signs and encrypts login with a vendor app secret, so account credentials alone may be refused; the tool does not extract or reuse that secret. This does not change the observed anonymous HTTP 401 results above.


## Security follow-up: guest key versus app secret

The [security follow-up](SECURITY_FLOW_5_0_35.md) distinguishes legacy app-secret signing from the Things guest callback. `SoundCoreThingIot.c/m/n` hashes timestamp plus selected key with SHA-1 and emits lower-case hex; its signed-out key is cached guest-negotiated state, while its signed-in key is empty. Guest creation carries `client_secret_info.public_key` and returns guest identity/server-public-key data. This is not yet a working guest downloader: header binding, the protected legacy wrapper and the Things crypto envelope still require verification. No new server request or protocol download occurred in that follow-up.
