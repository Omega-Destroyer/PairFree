# Beats virtual testing

Research date: **2026-11-07**. Original Beats Android app **2.12**, package `com.apple.bnd`, in an isolated offline Android 37 emulator. Reference bundle SHA-256: `64ed219cc7638045b722a00c2144e1b52becfbe7ea362c29e828cbc34d5fa54a`. No physical device was tested.

## Beats Fit Pro: attempted discovery

The original app completed onboarding without an account. A synthetic Classic peer named `Beats Fit Pro` offered the Apple accessory service UUID and an L2CAP listener on PSM `1001`; Android paired it and connected its A2DP audio profile. The app remained on its welcome screen and the peer observed no accessory-channel connection or application packets, including after the app was restarted. This fixture does not yet reproduce the product identity and discovery information expected by the app. Pairing and an audio connection alone are **not** a virtual protocol test.

The supplied app's decompiled code also contains a BLE discovery path that inspects Apple manufacturer ID 76 and an RFCOMM socket path using service `00001101-0000-1000-8000-00805F9B34FB`. These are source observations only: the particular model and conditions for each path have not yet been established. They do not establish a mismatch with PairFree's L2CAP implementation. Further work must determine the Fit Pro identity and transport and capture its original outgoing commands. The Beats app cannot serve as a vendor-app test of AirPods models.
