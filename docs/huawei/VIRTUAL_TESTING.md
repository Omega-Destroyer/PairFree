# Huawei: virtual setup attempted, no MBB capture

Date: 2026-11-06. Profile: **HUAWEI FreeBuds 3**. AI Life 11.0.2.305 was installed unchanged and run
offline on isolated Android 37 and Android 30 guests. No login was needed to open the bundled catalog.
The modern guest paired and connected audio to a synthetic FreeBuds 3, but the app did not open an
SPP control channel. **No MBB request, setting write, EQ or gesture was captured.** This is a setup
attempt, not a virtually tested settings/device-support claim.

## Original app and instrumented navigation

The manual catalog lists FreeBuds 3, FreeBuds 3i, FreeLace Pro and several other older products. The
ordinary manual route in `HandItemFragment` returns immediately when
`NetworkUtil.getConnectedType() == -1`, before starting its configuration guide. Repeated taps while
offline therefore do not diagnose the Bluetooth protocol.

On Android 30, Frida 17.26.1 replaced **only the local network-availability query** with a synthetic
Wi-Fi value and deoptimized app execution so that the hook was reached. Passive catalog/route hooks
logged the selected item; no account token, server response, trust check or command encoder was
replaced. Actual Wi-Fi and mobile data stayed disabled. This reached the configuration guide, its
completion checkbox and Bluetooth scan. The scan ended **No devices found** with the draft peer.

The running catalog route logged device id **`004O`** for the visible **FreeBuds 3** card. Keep that
observed catalog-id mapping distinct from a successful accessory PBI read: the simulator did not
return PBI `004O`, and no device identification command was captured. The source's `ModelPbiMap`
maps `004O` to `CM-H-SHARK`; the catalog observation is a useful lead, not a replacement for runtime
plugin identity handling.

## Next work

Reproduce the app's discovery filters, advertised identity and eligibility/paired-device handling;
then enter the existing embedded Smart Audio plugin and capture its SPP opening. The current
observation-only peer advertised SPP and an audio sink; that is insufficient evidence for the
Huawei-specific discovery profile. The bundled plugin and frame builders remain available for
further work. A network gate being skipped does not supply missing cloud content or authorization.

All other Huawei/Honor products in the [source/reference tables](PROTOCOL.md) remain **not attempted
virtually**. No newer FreeBuds Pro/SE/FreeClip model inherits this older app's setup result. No hardware
or PairFree UI was tested. No factory reset, firmware, power-off, account service or authenticated
provisioning was exercised. No APK was patched or re-signed.

Artifacts remain outside the repo at `E:\Builds\research\melomania-virtual`: `huawei-traffic.jsonl`,
`huawei-frida.stdout.log`, `huawei-offline.js` and the `huawei*` UI dumps/screenshots. The first trace
contains Bluetooth/audio readiness and connection events, **no `app_tx` packet**. There are no Huawei
emulator command vectors to add yet.
