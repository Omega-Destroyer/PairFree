# SoundPeter app design

This records the design decisions made so far. It covers the Android app first.
iOS and Windows follow the same structure.

Mockups for every screen live on a private design canvas held by the project owner. Before the repository goes public, export screenshots into
`docs/design/` so this document stands on its own.

## Requirements

These came from the owner and are not up for trade.

- **Works with no network.** The app does not request the internet permission.
  That rules out server presets, cloud profiles and in-app firmware downloads.
- **Collects nothing.** No account, no analytics, no crash reporting. Settings and
  EQ profiles are stored on the phone and leave it only when the user exports a file.
- **Light on resources.** Low memory and CPU use matters more than download size.
  Under 10 MB is a target to aim for, not a limit.
- **Clean code and assets.** Our own code plus libraries under GPL-compatible
  licences (Apache 2.0, MIT, BSD). Nothing from the official app: no code, no
  images, no fonts, no sounds.
- **Original vector art.** Device drawings are drawn from scratch as vectors.
  Product photos may be used as a reference for shape and proportion. The photos
  themselves never go into the repository or the app.

## Stack

- Kotlin with Jetpack Compose.
- The protocol code sits in its own module with no Android dependencies, so iOS can
  reuse it through Kotlin Multiplatform or reimplement it against
  `data/test_vectors.json`.
- Permissions: `BLUETOOTH_CONNECT`. Add `POST_NOTIFICATIONS` only when the
  notification controls ship. Nothing else.

## Looks

The app ships with one look for now: Studio. More looks can be added later as
skins. A look is a skin: a palette, a pair of fonts, a shape language and a set of
device drawings. Every look uses the same screens, the same layout and the same
features. A feature never exists in one look only.

### Studio

Dark, precise, made to feel like audio equipment.

| Role                | Value     |
|---------------------|-----------|
| Background          | `#101214` |
| Panel               | `#181B1F` |
| Raised panel        | `#1F2328` |
| Hairline            | `#2B3037` |
| Text                | `#E8E6E1` |
| Secondary text      | `#A0A5AD` |
| Label text          | `#8A9099` |
| Accent (amber)      | `#E6A23C` |
| Text on accent      | `#101214` |
| Destructive         | `#E88868` |

- Fonts: IBM Plex Sans for text, IBM Plex Mono for every number and for small
  capitalised labels. Both are under the SIL Open Font License and are bundled.
- Shape: 1 px hairlines, 4 to 6 px corner radius, no shadows.
- Selected state: amber fill with dark text for primary choices, amber outline and
  amber text for secondary ones.
- Device drawings: thin technical line art, 1.5 px stroke, with dashed centre lines.

### Sketchbook (parked)

Not being built for now. The owner chose to work on Studio only. The values are
kept here in case it comes back as a second skin.

Light, friendly, hand-drawn.

| Role                | Value     |
|---------------------|-----------|
| Background          | `#EEF3EF` |
| Card                | `#FBFCFA` |
| Text and ink        | `#33423F` |
| Outline             | `#4F625E` |
| Soft shadow         | `#C5D5CF` |
| Secondary text      | `#5B6B67` |
| Accent (teal)       | `#2B7670` |
| Highlight (peach)   | `#F2B8A0` |
| Teal tint           | `#D6E9E5` |
| Peach tint          | `#FBE6DA` |
| Destructive         | `#A4472E` |

- Fonts: Fredoka for headings, Nunito for text. Both SIL Open Font License, bundled.
- Shape: 2 px outlines, slightly uneven corner radii, hard offset shadows in the
  soft shadow colour.
- Selected state: teal fill with white text for primary choices, peach fill with a
  raised shadow for secondary ones.
- Device drawings: cartoon outlines with flat colour fills offset from the line.

### Rules for every look

- Touch targets are at least 44 px.
- Text contrast is at least 4.5:1.
- A selected state never relies on colour alone. It also changes fill, outline or weight.
- No gradients, blur or looping animation.

## Screens

Four tabs along the bottom: Home, Sound, Controls, Device. One more screen shows
when nothing is connected.

### Not connected

- A drawing of an open case.
- The earbuds already paired with the phone, each with a Connect button.
- A button that opens the system Bluetooth settings.
- One line: "No account. No internet. Nothing leaves this phone."

### Home

Everything here fits without scrolling on a 390 by 844 screen.

1. Model name and connection state. A button to switch earbuds.
2. Battery for left, case and right, each with a drawing, a percentage and a bar.
3. **Active Noise Cancelling.** One control with three choices in this order:
   Off, Cancelling, Transparency.
   - Under Cancelling: the scene choices the earbuds report (Adaptive, Indoor,
     Outdoor, Traffic on the H3).
   - Under Transparency: the transparency levels the earbuds report.
4. Current sound profile with a small curve. Opens the equalizer.
5. Quick switches: game mode, two devices, in-ear detection, LDAC.

### Sound (equalizer)

- Profile chips: built-in presets, the user's saved profiles, and New.
- A response graph:
  - dB scale on the left.
  - Band frequencies along the bottom, abbreviated (31, 62, 125, 250, 500, 1k,
    2k, 4k, 8k, 16k). The selected band's label is highlighted.
  - The selected point is larger and can be dragged up and down.
- A row of band buttons to pick a band, each showing its current gain.
- For the selected band: its name and gain, a slider, and minus and plus buttons.
  Dragging the point, moving the slider and tapping the buttons all do the same thing.
- Preamp with minus and plus buttons. The protocol carries a master gain with every
  band write.
- Save profile on its own full-width line.
- Import file and Export file side by side under it.

The band count and frequencies are data, not layout. The screen must work with
whatever the connected model needs.

### Controls

- Left and Right switch, with a drawing of the pair and the chosen side highlighted.
- Gestures in two groups:
  - **Taps:** tap, double tap, triple tap, press and hold.
  - **Slides:** slide up, slide down. Shown only on earbuds that have them.
- Tapping a row opens a picker for the action.
- A switch to turn touch controls off.
- Reset gestures to defaults.

The official app's gesture table has no slide gestures. Their command codes are
unknown until someone finds them on hardware.

### Device

- Header: model and firmware version, read from the earbuds.
- Connection: two devices at once, LDAC.
- Prompts and power: voice prompt volume, prompt language, turn off when idle.
- Find my earbuds: beep left, beep right.
- Maintenance: copy device report, factory reset.

Copy device report puts the same information as `tools/probe.py` on the clipboard,
so anyone can contribute a new model from their phone. Factory reset asks for
confirmation every time.

## Behaviour

Proposed, not yet reviewed by the owner.

- **Show only what the earbuds support.** On connect the app sends the read
  commands and builds the screens from what answers. Unsupported features are
  hidden, not greyed out.
- **Optimistic, then confirmed.** A change shows at once and is read back from the
  earbuds afterwards.
- **No background work by default.** The app talks to the earbuds while it is on
  screen. A background connection exists only if the user turns on the
  notification controls.

## Outside the app

Planned, not yet designed:

- A quick settings tile that cycles the noise mode.
- Notification controls with battery and noise mode.
- A home screen widget.

## Device drawings

- Source files are SVG under `assets/devices/<model>/<look>/`. Only `studio` exists
  for now.
- The files are generated by `tools/gen_device_art.py`. Change shapes there and
  rerun it. Do not edit the SVG files by hand.
- Drawn so far: H3 and Air5 Pro+.
- Each model has `bud.svg` (the left earbud; the right is mirrored in code) and
  `case.svg`.
- Drawings use only paths, circles, ellipses and rectangles with solid fills and
  strokes, so they convert cleanly to Android vector drawables.
- Models without their own art fall back to a generic drawing by shape: in-ear,
  stem, open-ear clip, neckband.
- Drawings show the product's general shape. They do not reproduce logos.
- Reference photos are for looking at only. They are never committed.

## Left out on purpose

- Location-based sound zones.
- White-noise or relaxation sound libraries.
- Anything that needs an account or a server.

## Still open

- A light version of Studio.
- Device drawings for every model other than the H3 and Air5 Pro+.
- Which side the Air5 Pro+ drawing shows. It has not been checked against the L/R
  marking on a real pair.
- Widget, tile and notification designs.
- The EQ band layout Qualcomm models expect.
- Whether a hearing-test profile is worth building. It can be done offline.
