# PairFree app design

This records the design decisions made so far. It covers the Android app first.
iOS and Windows follow the same structure.

Mockups for every screen live on a private design canvas held by the
project owner. Before the repository goes public, export screenshots into
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
  licences (Apache 2.0, MIT, BSD). Nothing from any brand's official app: no code, no
  images, no fonts, no sounds.
- **One app for every brand.** (Owner, 2026-10-13.) The aim is a universal control app
  for any earbuds or headphones whose protocol can be worked out, so people with
  several pairs need one app. SoundPEATS comes first, then Soundcore (Anker), then Samsung
  Galaxy Buds. The screens
  stay the same for every brand; each model shows only the features it has. Each
  brand's protocol, model table, reports and drawings live in folders named after the
  brand (`docs/<brand>/`, `data/<brand>/`, `assets/devices/<brand>/`). Models nobody on
  the project owns come from owners' recordings, as for SoundPEATS. A feature only one brand
  has (3D sound on Soundcore, the fit test on SoundPEATS) shows on the earbuds that have it.
- **Clean vector art.** Device drawings are vectors. Their outlines may be traced
  from product photos so the proportions are right, but no photograph, logo, text or
  surface pattern goes into a drawing. The photos themselves never go into the
  repository or the app.

## Stack

- Kotlin with Jetpack Compose.
- The protocol code sits in its own module with no Android dependencies, so iOS can
  reuse it through Kotlin Multiplatform or reimplement it against
  `data/soundpeats/test_vectors.json`.
- Permissions: `BLUETOOTH_CONNECT`. Add `POST_NOTIFICATIONS`, and what the companion
  device setup and the background connection need, when the notification ships. And for JBL
  alone, `BLUETOOTH_SCAN` with `neverForLocation` (owner, 2026-10-15): most JBL models take
  their settings over Bluetooth LE, and when their LE side does not answer on the paired
  address, the only way to find it is its advert (`docs/jbl/PROTOCOL.md` section 3). It is
  asked for only when the user opens a JBL model, the scan lasts at most 10 s and keeps
  nothing, and connecting goes ahead without it where the direct way works. Nothing else.

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
- Every paired device PairFree supports, of any brand (SoundPEATS, Soundcore, ...), each
  with a Connect button. The user picks a device, never a brand: PairFree works out the
  brand and model by itself (owner, 2026-10-13).
- A button that opens the system Bluetooth settings.
- One line: "No account. No internet. Nothing leaves this phone."

### Home

Everything here fits without scrolling on a 390 by 844 screen.

1. Model name and connection state. A button to switch earbuds.
2. Battery for left, case and right, each with a drawing, a percentage and a bar. Headphones
   and speakers report one battery: it shows once, named and drawn as the product, with a
   generic headphones or speaker drawing until the model has its own (owner, 2026-10-14).
   Earbuds that report their battery in steps rather than percent (fifths on the Space A40,
   tenths on some Soundcore models) show one bar per step, filled up to the level, and no
   number, as their own app does (owner, 2026-10-14). The model's data gives the number of
   steps, so any count works; a row that mixes bars and a percentage lines up.
3. **Active Noise Cancelling.** One control with three choices in this order:
   Off, Cancelling, Transparency.
   - Under Cancelling: the scene choices the earbuds report (Adaptive, Indoor,
     Outdoor, Traffic on the H3).
   - Under Transparency: the transparency levels the earbuds report.
4. Current sound profile with a small curve. Opens the equalizer.
5. Quick switches: game mode, two devices, in-ear detection, LDAC, 3D sound. Each shows only
   on earbuds that have it.

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
whatever the connected model needs. The labels above are the official app's
generic custom layout. The H3's is 50, 160, 350, 800, 1.6k, 2.8k, 4.6k, 8k, 12k and
17k, from -6 to +6 dB in whole dB.

The gains on screen are the user's, on top of the model's base curve, the way the
official app does it, so 0 dB on every band is the earbuds' own tuning (the Stock
profile). A model's presets come from `data/soundpeats/devices.json` (`eq.presets`). They are
complete curves, sent as they are and drawn read-only as their difference from stock;
editing band by band starts from Stock, New or a saved profile. The preamp only lowers
the master gain. The earbuds cannot report their EQ, so the screen shows what this phone
last sent, and the app sends that again each time the channel opens, as the official app
does, in case another app changed it. Earbuds this phone has never set show "Not known
yet" until the user picks a profile; nothing is sent to them on connect. A profile that
puts the same bytes on the earbuds as the current setting shows as selected, so New with
every band at 0 is Stock.

Soundcore earbuds report their EQ, so the same screen works differently for them (owner,
2026-10-14). The profiles are the model's presets, the user's saved profiles and New; there is
no Stock and no preamp. The screen shows what the earbuds report, whichever app set it, and
nothing is sent on connect. After a change the app reads the state back, so the earbuds confirm
it. The custom curve is the model's visible bands (8 on most) in whole dB within the model's
range (±6 dB on most). The screen offers the EQ only on models whose EQ write a recording or
OpenSCQ30's tests confirm (docs/soundcore/PROTOCOL.md section 8); the others say a device report
helps.

Galaxy Buds report which preset they use (docs/samsung/PROTOCOL.md section 6). The profiles are
Normal and Samsung's five presets; there is no Stock and no preamp. The earbuds apply the presets
themselves and nobody knows their curves, so a preset shows its name and a line saying so, and no
graph. On the Buds3 and later a nine-band custom curve (−10 to 10 steps) joins them, with New and
the user's saved profiles; on older models, which have no custom curve, New, import and export do
not show. The earbuds forget a custom curve when they turn off and report only that it is custom,
so the app keeps the last one it set and sends it again once per connection, as GalaxyBudsClient
does.

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

Soundcore earbuds (docs/soundcore/PROTOCOL.md section 9) use the same tab. The side switch shows
only when the model has buttons on both sides. Rows list what the model has, slides included (the
Liberty 4 Pro); a press that can be turned off has Off in its picker. A third group, During calls,
shows on the C30i, and presses the model fixes (single press on the three one-button headphones)
show without a picker. Soundcore earbuds keep separate actions for when one earbud is used alone:
a line under the groups says which set is on screen, following whether both earbuds are in. Then
touch controls on or off, touch lock and touch tone, which noise modes the noise-control action
cycles through (at least one stays on), and Reset gestures, each only where the model has it.

### Device

- Header: model and firmware version, read from the earbuds.
- Connection: two devices at once, LDAC. Turning LDAC on opens a short dialog: the phone picks the
  codec, and Samsung phones can need LDAC on for the earbuds in their own Bluetooth settings too, with a
  button to those settings; while LDAC is on, a note under the switch says the same. Android does not
  let PairFree read the codec in use, so it cannot tell whether the phone followed.
- Prompts and power: voice prompt volume, prompt language, turn off when idle.
- Find my earbuds: beep left, beep right.
- Connection: LE Audio too, on models PeatsAudio offers it on (`confirmed` in
  `data/soundpeats/devices.json`). The earbuds restart to switch; the app reconnects by itself.
- Ear tips: a fit test page, on models whose GAIA features include the fit test. It says
  how to wear the earbuds, runs the test (about 9 s, with a progress bar), then shows a
  result per earbud with a drawing ("Good fit" or "Poor fit"), and what to do about a
  poor seal. No result after 20 s says so. Noise control goes off for the test, as in the
  official app, and back on after it, which the official app does not do.
- Water drain, where the earbuds answer its read: one button, with the advice to take
  the earbuds out first. It shows "Draining" until the earbuds report it finished.
- Status notification: PairFree, Fast Pair (on earbuds with it) or None, with what each
  means and buttons for what Android still needs (Notification above). The note under
  PairFree says that while it shows, the official app cannot connect.
- Maintenance: copy device report, record the EQ from PeatsAudio, factory reset.

Copy device report puts the same information as `tools/probe.py` on the clipboard,
so anyone can contribute a new model from their phone. Factory reset asks for
confirmation every time.

### Settings only one brand has

A brand's settings that fit none of the screens above show as generic rows (Galaxy Buds first,
2026-10-14). The brand's session lists them in groups. Each group has a heading and names its
tab, and its panel goes on that tab after the tab's own controls:

- **Home:** after noise control.
- **Sound:** under the equalizer.
- **Controls:** the whole tab, for a brand without the gesture table.
- **Device:** before Maintenance.

A row is one of four kinds:

- **Switch:** a title, an optional note, and the switch.
- **Choice:** the title and the current option; tapping the row opens a picker.
- **Level:** up to five steps as a segmented row. More steps get a slider with minus and plus
  buttons, sent 300 ms after it settles. A level that runs from one side to the other, such as
  balance, centres its slider and names the middle.
- **Button:** for something that runs, such as Find my earbuds. While it runs it says Stop, and
  the rows that only make sense then (mute left, mute right) appear under it.

A row the earbuds cannot take right now shows disabled, such as a gesture switch while touch is
off. The labels are PairFree's own words, not the brand's.

On Galaxy Buds these hold:

- **Home:** the ambient sound level and its extras, voice detect and the noise cancelling level.
- **Sound:** balance.
- **Controls:** the gesture switches and what touch and hold does.
- **Device:** each earbud's ambient level and tone, the call settings, seamless connection and
  voice wake-up, and Find my earbuds.

The fit test uses the shared page, but noise control stays as it is.

### Record from the official app

The earbuds cannot report their EQ, and SoundPEATS' server is closed, so a model's base
curve and presets can only be learnt from what PeatsAudio sends. Which features the
official app offers on a model is also on that server; PeatsAudio's reads show it. For the
other brands (the Soundcore app, Galaxy Wearable), what their app sends for each setting is
what confirms a write on a model nobody on the project owns (owner, 2026-10-14: record from
any vendor's app, not only PeatsAudio). This screen lets anyone record that from the phone
alone, once per model, with no computer, and adds PairFree's own device report when it is
connected to the same earbuds. It opens from the Device tab for every brand, from the Sound
tab when a model has no EQ yet ("If you want to change EQ settings, you can contribute to
the project" and a button), and when a bug report is shared to the app.

- First, a panel says what PairFree reads: only the Bluetooth messages exchanged with
  earbuds of the brands it supports, on the phone, and what the report shows of them;
  nothing is sent until the user submits the GitHub issue in the browser; the app has no
  internet access. It links to the source code. People hesitate to hand a bug report to an
  app from an unknown developer.
- If the user misses the bug report notification, Choose a bug report opens the system
  picker, where Android lists bug reports under Bug reports in the menu.

- The steps: turn on Developer options, set Bluetooth HCI snoop log to Enabled and
  restart Bluetooth, open each screen of the official app once, then change each setting to
  learn, one at a time, and note the order; on SOUNDPEATS earbuds also run PeatsAudio's fit
  test and tap SOUNDPEATS Classic and then every preset; take a bug report, share it to
  PairFree, then turn the log off and delete the bug report.
- PairFree reads the bug report on the phone (`capture/BugReport.kt`), only the files
  named like the Bluetooth snoop log, and keeps only the control messages of the brands it
  supports (`SnoopScanner`, `EqCapture`, `LoggedReads`, `VendorRecording` in `:protocol`):
  SOUNDPEATS frames, and Soundcore and Samsung packets read from each RFCOMM channel. The
  log's connection events give each connection's address, which only matches the earbuds
  to a paired device and is masked. The file itself is never stored.
- PairFree notes when its own channel is open and leaves those messages out: its
  connect reads and its EQ put back are not PeatsAudio's. Sessions from before an update
  that added this are not known, so a log that old can still hold some.
- It lists each pair of earbuds in the log. SOUNDPEATS earbuds show how many settings
  PeatsAudio read, and whether PairFree's own device report goes in (it does when PairFree
  is connected to earbuds with the same firmware code). Other brands' earbuds show their
  model, from the paired device at their address, how many messages the app and the
  earbuds sent, and which messages the app sent, by name, so the user sees their changes
  were caught.
- For other brands the report holds a timeline per pair of earbuds: seconds from the first
  message, who sent it, its number and name, and its bytes, with a repeated message shown
  once with a count, then a place for the user to write what they changed, in order.
  It goes into a public issue, so the earbuds' address and anything that looks like a
  serial number are masked, bodies marked private (Soundcore's list of known devices and
  its serial number) and firmware-update messages are left out, and a message PairFree
  does not know shows at most four bytes, too few for an address, a name or a key.
- A phone keeps one Bluetooth log for hours, through Bluetooth being turned off and on,
  so the recording starts when the user opened this screen (remembered for up to three
  hours), or else just before an official app last connected (PeatsAudio's last firmware
  read, or when other earbuds last connected). Show all widens it to the whole
  log; Recording only narrows it again.
- It lists every complete EQ write in the recording, with its time and a small curve, and
  marks repeats. A curve that is byte for byte the base curve or a preset of a known model
  is named already ("known H3 preset"); the user's own slider curve is recognised and left
  out. Only a curve PairFree does not know shows "Needs a name". A recording with no EQ
  curves can still be sent for its settings. The user names each one from the preset names PairFree knows, types another,
  or leaves it out. The firmware reply in the log identifies the model.
- Send on GitHub copies the report and opens a new issue with it filled in (in a code
  block), through the browser: the app still has no internet permission. The link carries
  a compact form (no padding or decoded values, which the raw bytes carry, and a curve's
  payloads on one line) so a full recording fits GitHub's limit of about 8000 characters;
  only a longer one asks the user to paste. Share sends the full text anywhere else.
- The report holds the model, app and Android version, phone maker and model; what
  PeatsAudio read from each pair of earbuds, in device report lines; PairFree's own
  device report; and every named write's decoded bands and exact bytes. No names or
  addresses.
- A maintainer saves it under `data/soundpeats/reports/`, where `tools/gen_models_table.py` reads
  its settings, and turns its equalizer into the model's `eq` entry and test vectors with
  `tools/import_eq_capture.py`, which checks every value against the bytes.

## Behaviour

Proposed, not yet reviewed by the owner.

- **Show only what the earbuds support.** On connect the app sends the read
  commands and builds the screens from what answers. Unsupported features are
  hidden, not greyed out.
- **Say why a setting is blocked.** A supported setting that another setting turns
  off is shown disabled with the reason, not hidden. On the H3, game mode and two
  devices need LDAC off.
- **Optimistic, then confirmed.** A change shows at once and is read back from the
  earbuds afterwards.
- **One connection, shared.** The app holds one control connection to the earbuds, in the
  app process (`Hub`), and the screens and the notification both use it. While PairFree's
  notification is chosen for the earbuds, the connection stays open whenever they are
  connected, so the battery and the noise mode are live: the earbuds report changes
  themselves, and the battery is also read as before. Other apps, the official ones
  included, cannot connect meanwhile; choosing Fast Pair or None in the Device tab frees the
  earbuds, and then the app holds the connection only while it is on screen.

## Outside the app

### Notification (owner, 2026-10-13; the choice with Fast Pair, 2026-10-14)

- Shown while supported earbuds are connected (`status/StatusService.kt`, a connected-device
  foreground service holding the channel), with their name, battery for left, case and right
  (percent, or steps such as 3/5), and the noise mode.
- It ends when the earbuds disconnect: on Android's disconnect broadcast, when Bluetooth turns
  off, and, should the broadcast not arrive, once the earbuds are no longer connected for sound
  (A2DP, hands-free or LE Audio, through Android's profile proxies; `bluetooth/AudioLinks.kt`),
  checked every 2 s while the channel is not open, after 10 s for earbuds still connecting. It
  does not keep trying to open the channel to earbuds that are not connected (owner's report,
  2026-10-23: it stayed on Connecting after the earbuds went into their case).
- Buttons, each only on earbuds that have it: the noise mode, which steps to the next (off,
  cancelling, transparency, round again), and game mode. They are real buttons in the
  notification's own layout, so the noise mode works even while the notification is
  collapsed, where Android hides ordinary action buttons (owner's test on One UI 8,
  2026-10-14); expanded, game mode joins it. On models that offer game mode only with LDAC off
  (`ldac_excludes`), the game mode button is dimmed and says so while LDAC is on, as the Home
  tab's switch is, but turning game mode off always works. Tapping elsewhere opens PairFree. 3D sound joins
  them once the app sets it.
- Android 14 and later let the user swipe away any app's notification while the phone is
  unlocked, a foreground service's included. While the earbuds stay connected PairFree puts
  it straight back (owner, 2026-10-14); None on the Device tab is the way to be rid of it.
- A choice per pair of earbuds on the Device tab, Status notification: PairFree, Fast Pair
  or None. Fast Pair shows only on earbuds that have it; elsewhere there are two choices.
  PairFree replaces Google's Fast Pair notification as far as Android allows: no app can
  hide Google's, which Google Play services shows for every Fast Pair device. So PairFree's
  choice, and None, show a note and a button to Google Play services' notification
  settings, where the user turns Google's off.
- Default: PairFree's, except on earbuds with Fast Pair, which keep Google's until the user
  chooses, so nobody sees two at once (owner, 2026-10-14).
- Which earbuds have Fast Pair: `fast_pair` in the brand's devices.json where an owner saw
  it (the Space A40 has it; the H3 and Air5 Pro+ do not), else PairFree looks for Google's
  Fast Pair service (`FE2C`) on the earbuds with a short Bluetooth LE connection, once, and
  remembers the answer. Earbuds that use another address for LE give no answer and get two
  choices.
- What Android needs: the notification permission on Android 13 and later, and, to start
  the notification when the earbuds connect while PairFree is closed, PairFree linked to them
  as their companion app (a one-time system prompt per pair). The first time earbuds with
  PairFree's notification connect, the app asks for both, one after the other. Before the
  link it says in its own dialog what the link is for: Android's prompt says a linked app can
  sync information such as a caller's name, which PairFree does not do (owner's test,
  2026-10-14). Afterwards the Device tab has a button for whichever is missing. Without the link the notification
  starts only when PairFree is opened.
- While it shows, the official app cannot connect; the recording steps say to choose None
  first.

Planned, not yet designed:

- A quick settings tile that cycles the noise mode.
- A home screen widget.

## Device drawings

- Source files are SVG under `assets/devices/soundpeats/<model>/<look>/`; only `studio` exists
  for now. They are generated by `tools/gen_device_art.py`, which also writes the same
  drawings as Android vector drawables
  (`android/app/src/main/res/drawable/device_<model>_<part>.xml`) and the app's lookup
  table. Change the shapes in the script or its data, never in the generated files.
- Each model has `bud.svg` (the left earbud, mirrored in code for the right; the
  whole product for headphones) and, when it has a case, `case.svg`.
- Drawings use only paths, circles, ellipses and rectangles with solid fills and
  strokes in the Studio palette, so they convert cleanly to Android vector drawables.
- Outlines are traced from SoundPEATS' product photos: straight on where a photo
  shows the part that way (the H3 and Air5 Pro+ earbuds), otherwise from the angle the
  photo shows. Each model's data and the photo it came from are in
  `tools/device_art/soundpeats/<model>.json`; the H3 and Air5 Pro+ are in the script itself.
- Headphones are drawn whole; ear hooks and clips as one earbud.
- Models without art of their own, or a part no photo shows clearly, use a generic
  earbud or case. The app picks drawings by Bluetooth name, then firmware code.
- Drawings show the product's general shape. They do not reproduce logos.
- Reference photos are for looking at only. They are never committed.

## Left out on purpose

- Location-based sound zones.
- White-noise or relaxation sound libraries.
- Anything that needs an account or a server.

## Still open

- A light version of Studio.
- Which side the Air5 Pro+ drawing shows. It has not been checked against the L/R
  marking on a real pair.
- Widget and tile designs.
- The EQ of every model other than the H3 and Air5 Pro+, until someone records it
  (Record from the official app above).
- Whether a hearing-test profile is worth building. It can be done offline.
