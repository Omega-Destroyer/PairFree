# Nothing X virtual testing

Research date: **2026-11-07**. Original **Nothing X 3.4.15**, `com.nothing.smartcenter`,
running offline on the isolated Android 37 Google APIs userdebug emulator, with ARM64 translation.
The APK and its encoders were not modified. This is **not hardware testing**.

## Ear (2), B155

The peer advertises the Nothing RFCOMM service and SBC audio. Synthetic replies identify B155,
firmware `1.0.1.74`, left/right/case batteries 80/85/100, and source-derived setting state.
Android pairing and A2DP connection use Android's APIs through the research helper. No vendor
account or server authentication was used. Root/Frida was available for passive observation and
the explicitly assisted callback below; it did not replace a packet builder or Bluetooth write.

Identification requires more than a plausible model reply. The running app maps `B15501` to
`DEE8C0` (white) and `B15502` to `ACC520` (black). The draft peer's color `00` parsed but did not
resolve to an Ear (2), leaving My devices empty. Changing the synthetic color reply to `01`
allowed the original app to open the product. This was a fixture error, not an earbud failure.

### Captured through the original UI

- Presets: More bass sends `F010 0300`, More treble `0200`, Voice `0100`, Balanced `0000`,
  Custom `0500`. This independently confirms the vendor mapping disputed by nada/nadamas.
- Noise: `F00F 01 <mode> 00`: high 1, mid 2, low 3, adaptive 4, off 5, transparency 7.
- Left double pinch: `F003 01 02 01 02 <action>`: previous 8, next 9, assistant 11.
  The app subsequently reads `C018`. Early replies incorrectly collapsed the whole gesture
  table to the changed entry; that harness issue was corrected before further readback work.
- Low lag: this Flutter screen sends **one byte**, `F040 01` on and `F040 02` off, rather than
  the two-byte payload described by the older Kotlin builder and currently documented for PairFree.
- In-ear detection: `F004 010100` off and `F004 010101` on.
- The codec screen offers AAC and LHDC. No codec write was exercised because applying it may
  reconnect/restart the earbuds. Opening a screen is not counted as a captured setting write.

### Custom EQ: assisted callback, not a successful slider test

The native activity actually opened is `ear.two.equalizer.EqualizerActivity`, whose view model
extends `BaseEqualizerViewModel`; it is not the newer `SimpleEqualizerViewModel` route used in
the earlier static analysis. Radar drags changed the displayed values but emitted **no `F041`**.
Passive inspection found `EQDragView.changeListener == null`; the supplied activity/binding also
does not assign that listener. Do not infer acoustic success from the moving sliders.

For a separate assisted test, Frida invoked the original live Ear (2) view model's
`setCustomEQ(0)` after the UI held mid +1, treble -3, bass +6. No encoder was patched. Its actual
Bluetooth output is retained separately as `original_callback_invoked`:

- Count 3, total gain -6 dB; peak `(1, +1, 980 Hz, Q .7)`, high shelf
  `(2, -3, 3400 Hz, Q .7)`, low shelf `(0, +6, 140 Hz, Q .8)`; nine trailing zero bytes.
- **High-shelf Q is .7 on this native Ear (2) route**, while the newer product/feature-table
  route describes 1.0. Preserve that distinction; do not silently generalize either across models.
- A research-only legacy model preference was set from `B155` (which did not resolve through
  `getProductByModelId`) to `DEE8C0` during diagnosis. The assisted curve observation includes
  that intervention. The earlier preset/noise/gesture captures did not require it.

The peer's success replies and readback state are synthetic. In particular, optimistic UI changes
and transient app messages are not proof that real firmware accepts a payload length or value.
Advanced EQ, personalized ANC/Mimi, dual connection, other gestures, model/firmware variants,
rejection behavior and a PairFree UI comparison remain to be exercised.

**PairFree's follow-up (2026-11-10):** low lag now goes as the one byte the app wrote, and the Ear (2)'s high shelf has Q 0.7
(`NothingTest.theFramesNothingXSentToAVirtualEar2` compares PairFree's frames with these vectors: presets, in-ear detection,
low lag and the custom curve). The preset numbers, noise modes and left double-pinch frames already agreed with PairFree.
The other products' own view models differ from the feature table in more places (docs/nothing/PROTOCOL.md section 5); they
are not changed without a run of their own.

Selected outgoing facts: [`emulator_vectors.json`](../../data/nothing/emulator_vectors.json).
Raw captures, screenshots, profiles, hashes and research tools remain outside the repository at
`E:\Builds\research\existing-vendor-virtual-2026-11-07`. Other Nothing/CMF models have no runtime
result from this pass yet; do not transfer the Ear (2) result to them.
