# Audio-Technica: limited instrumented virtual startup

Date: 2026-11-06. Profile: **ATH-M50xBT, GAIA_V2**. The original Connect 2.8.0 app opened RFCOMM to
a simulated accessory, and its API-version request `FF010000000A0300` was observed at the app's
Bluetooth output and in the peer's HCI traffic. This is a **startup-only, instrumented result**.
Identification and the settings screen were not completed; no EQ, preset, gesture or extra setting
write has been virtually validated. No AT hardware or PairFree UI was tested.

## Compatibility work and interventions

- The supplied native split contains **armeabi-v7a**, not arm64. Android 37/33 x86_64-only guests
  cannot run it. A dedicated Android 30 Google APIs x86_64 guest provided 32-bit ARM translation.
- React Native initially failed to locate `libreactnative.so`: SoLoader selected an x86 APK path.
  On the rooted isolated guest, the original split's 27 native libraries were extracted into the
  app's own `nativeLibraryDir` and their context restored. The unchanged app then launched, accepted
  local onboarding and reached discovery. Neither APK nor native binary was patched or re-signed.
- Android 30's Bluetooth HAL uses an older in-guest RootCanal, separate from modern Netsim. Its
  `bt.rootcanal_test_console=true` exposes HCI TCP 6211, forwarded to host 16211. SELinux was made
  permissive **inside this dedicated guest** for those HAL test sockets. The host's settings and
  the owner's AVD were not changed.
- The old controller advertised optional HCI commands that returned Unknown HCI Command. The harness
  skipped Page 2 event-mask and suggested-data-length operations. It also used outbound ACL PB=2:
  this old RootCanal forwards PB bits unchanged, while PB=0 is reserved on the controller-to-host
  path. These are controller compatibility adjustments, not vendor-protocol changes.
- Android service discovery progressed, but A2DP discovery still failed in that guest. A Frida
  17.26.1 hook supplied a connected-device list/state for the one synthetic AT address, allowing the
  vendor app to open its real RFCOMM socket. A separate passive output hook logged bytes and called
  the original write. No command builder, parser, permission grant result or authentication check
  was replaced. Synthetic profile discovery must remain explicit in every claim from this run.

The recorder initially raised a keyword-argument error after receiving the query. The packet was
still present in the underlying HCI log and passive app-output log; it is retained as that limited
observation. The recorder error was corrected. The incomplete identification must not be presented
as an earbud rejecting the command or as proof of an AT protocol limitation.

## Remaining work and artifacts

Complete the fixture's API-version/AT identity responses, remove unnecessary discovery intervention
where possible, and capture the actual per-model startup/settings flows. The simulator's draft API
reply was not a hardware-derived schema validation. The standard GAIA manager expects status plus
three API-version components; check both that manager and the React Native consumer before using a
fixture reply as state evidence.

GAIA_V3, AIROHA and RTK were researched statically but **were not connected virtually**. The remaining
27 model definitions do not inherit M50xBT's startup result, including M50xBT2, which uses RTK.
The model index records this distinction in its virtual-testing column.

The single selected outgoing request is in `data/audiotechnica/emulator_vectors.json`. APKs, original
extracted libraries, emulator state, hook scripts and logs remain outside the repo under
`E:\Builds\research\melomania-virtual`. `at-frida.stdout.log` contains the passive `APP_TX` entry;
`virtual_at30clean.stderr.log` contains the RFCOMM/HCI packet and the recorder exception. Pairing
material and raw logs are not redistributed. Wi-Fi/mobile data remained disabled; no login, firmware,
reset, power-off or vendor server operation was exercised.
