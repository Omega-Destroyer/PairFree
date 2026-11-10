# Bose virtual testing

Research date: **2026-11-07**. Original Bose **13.3.4**, `com.bose.bosemusic`,
offline on an isolated Android 37 Google APIs userdebug emulator with ARM64 translation.
The APK and command encoders were unchanged. **No physical Bose device was tested.**

## QuietComfort Ultra Headphones (2nd Gen) / Wolverine fixture

This is an **instrumented startup-only capture**, not a settings test. The stock app's
Do It Later option skips account setup. A synthetic Classic audio/RFCOMM peer advertises
FEBE and Bose manufacturer data using the app's BMAP 1.2.0 advertisement layout. Product
code 112 selects Wolverine, variant 1; available/pairing flags permit discovery. The
app displays the synthetic product as Bose Ultra2. Names, addresses and replies are fixtures.

Android pairs and connects A2DP. The app's connection SDK also calls the protected
BluetoothHeadset connection API, which throws a `MODIFY_PHONE_STATE` SecurityException
in this Android 37 guest. Research instrumentation selects the synthetic scan result,
invokes the original SDK's connection method, and reports connected audio-profile state
only for that synthetic address. It does not change RFCOMM writes or BMAP serializers.

The original app opened a real RFCOMM channel and emitted three identification requests:

| Frame | Request |
|---|---|
| `00 01 01 00` | BMAP version |
| `00 02 01 00` | All function blocks |
| `00 03 01 00` | Product ID and variant |

These outgoing frames are retained in [emulator_vectors.json](../../data/bose/emulator_vectors.json),
with installed APK hashes. Synthetic replies supply ASCII version `1.2.0`, a function-block
list `00 01 02 05 1F`, and product/variant `40 82 01`. An earlier draft incorrectly put a
product name in the AllFblocks response; it was corrected after reading the original parser.
Neither fixture is evidence of a real product's function-block list.

The displayed Connect control launches `OtgPairWithProductActivity`, which immediately
finishes and returns to discovery. Tracing that local onboarding decision remains to do.
No EQ, modes, buttons, timers, readback or reconnection behavior has passed this test;
the other Bose model records inherit no coverage. No account service was contacted.

External scripts, original APKs, synthetic advertisements/replies and raw traces remain in
`E:\Builds\research\existing-vendor-virtual-2026-11-07`. Pairing material is not committed.
