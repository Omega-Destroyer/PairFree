# Beats virtual testing

Research date: **2026-11-07**. Original Beats Android app **2.12**, package `com.apple.bnd`, in an isolated offline Android 37 emulator. Reference bundle SHA-256: `64ed219cc7638045b722a00c2144e1b52becfbe7ea362c29e828cbc34d5fa54a`. No physical device was tested.

## Beats Fit Pro: attempted discovery

The original app completed onboarding without an account. A synthetic Classic peer named `Beats Fit Pro` offered the Apple accessory service UUID and an L2CAP listener on PSM `1001`; Android paired it and connected its A2DP audio profile. The app remained on its welcome screen and the peer observed no accessory-channel connection or application packets, including after the app was restarted. This fixture does not yet reproduce the product identity and discovery information expected by the app. Pairing and an audio connection alone are **not** a virtual protocol test.

The supplied app's decompiled code also contains a BLE discovery path that inspects Apple manufacturer ID 76 and an RFCOMM socket path using service `00001101-0000-1000-8000-00805F9B34FB`. These are source observations only: the particular model and conditions for each path have not yet been established. They do not establish a mismatch with PairFree's L2CAP implementation. Further work must determine the Fit Pro identity and transport and capture its original outgoing commands. The Beats app cannot serve as a vendor-app test of AirPods models.

## PairFree against virtual AirPods and Beats (2026-11-10)

Virtual testing, not hardware: PairFree's debug build (0.9.0 plus the AirPods and Beats work) ran on an Android 17 emulator against Bumble
peers whose replies were invented from the layouts in `docs/apple/B2P.md`. The peer scripts and raw captures stay in
`E:\Builds\research\beats-virtual-2026-11-10`.

| Virtual device | Result |
|---|---|
| AirPods Pro (Apple's service, L2CAP PSM 0x1001) | Listed by PairFree. The Android 17 stack opened the channel (`btsock_connect ... channel: 4097`), PairFree took the socket from the first hidden constructor on its list, and sent the handshake, the feature flags and the notification request, again after 5 s. The peer answered nothing, so the Home tab stayed on "Reading the earbuds". The hidden-API exemption was not needed on this emulator image: the constructor was reachable (the runtime logged an access warning only), so that path stays untested. A first run failed because the peer had not registered the PSM (the stack's reply was "PSM not supported"), which PairFree shows as "Connection failed for unknown reason". |
| Beats Fit Pro (B2P, over the serial channel) | Listed and recognised by name. PairFree sent the product id, battery, listening mode (1 then 2), in-ear detection, one-bud ANC and microphone reads, 200 ms apart; the peer's independent implementation accepted every frame's CRC and length. It showed the battery (85%), noise control Cancelling, the one-bud switch, ear detection and the microphone choice. Choosing Transparency sent `015A 02 64` and then read the mode again. |
| Beats Flex (BCD) | Listed. PairFree sent the product id and the auto answer read (the Flex lists no ANC), acknowledged each reply, and showed "Answer calls automatically"; switching it off and on sent `0248 02` and `0248 01`. |
| Powerbeats Pro 2, Beats Studio3 | Bonding failed in the harness (not a PairFree result); not tested. |

Observed: a device bonded without a Bluetooth inquiry has no device class, and PairFree's list leaves it out because it keeps only audio devices.
That is harness-only: real earbuds report a class. On the Flex, which has neither battery nor noise control, the Home tab says "Reading the
earbuds" for ever, and the Fit Pro's single battery level is labelled "Left". Both are to be improved.
