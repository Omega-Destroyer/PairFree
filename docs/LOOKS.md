# Looks

A look is how PairFree is drawn: its colours, its fonts, its lines and corners, and how the device drawings are
inked. Every look uses the same screens and the same features (`docs/DESIGN.md`, Looks). The app ships with:

| Look         | File                           | What it is                                                              |
|--------------|--------------------------------|-------------------------------------------------------------------------|
| Studio       | `data/looks/studio.json`       | Dark, amber, IBM Plex. The default.                                     |
| Studio Light | `data/looks/studio-light.json` | Studio on paper: the same type and lines, light.                        |
| Material You | `data/looks/material-you.json` | The phone's wallpaper colours and font, round corners; follows dark mode. |
| Sketchbook   | `data/looks/sketchbook.json`   | Light and hand-drawn: its own fonts, hard shadows, cartoon drawings.    |

The look is chosen on the connect screen and on the Device tab, next to the language. The screens, the home screen
widget and the status notification's accent follow it.

## Adding a look

A look is one file. Like a translation, it arrives as a pull request:

1. Copy `data/looks/studio.json` to `data/looks/<id>.json`. The file name is the look's id: small letters, digits
   and hyphens.
2. Change its values (below).
3. Add its name to `android/app/src/main/res/values/strings.xml`, beside the others:
   `<string name="look_<id>">My Look</string>` (hyphens in the id become underscores).
4. Run `python -m unittest tools.test_looks`. It checks that the look is complete and that its text can be read.
5. Build the app and look at every tab in it. `ui/Previews.kt` shows how to add a preview of a look.

No Kotlin is needed: the build reads the folder (`generateLooks` in `android/app/build.gradle.kts`) and the picker
lists what it finds. You are credited in `CONTRIBUTORS.md`.

## The file

```json
{
  "name": "Studio",
  "order": 1,
  "dark":  { "background": "#101214", "...": "..." },
  "light": { "...": "..." },
  "type":  { "text": "plex_sans", "numbers": "plex_mono", "label_caps": true },
  "shape": { "hairline": 1, "corner": 6, "small_corner": 4, "panel_border": true },
  "art":   { "style": "line", "stroke": 1.0 }
}
```

- `name`: for readers of the file. People see the name from `strings.xml`.
- `order`: where the picker lists it. Leave it out to come after the built-in looks.
- `dark`, `light`: the palettes. Give one, or both: a look with both follows the phone's dark mode.
- `type.text`, `type.numbers`, and `type.headings` where titles have a font of their own: fonts by name.
  `plex_sans`, `plex_mono`, `fredoka`, `nunito`, `system` (the phone's font) and `system_mono` exist. A look with
  another font adds the font files to `android/app/src/main/res/font/` (SIL Open Font License or as free), its
  licence to `android/app/src/main/assets/licenses/`, and one line to `fontFamily()` in `ui/theme/Look.kt`.
- `type.label_caps`: whether small labels are set in capitals, as Studio's are.
- `shape`: in dp. `hairline` is the width of outlines, `corner` the radius of panels and dialogs, `small_corner` of
  buttons and chips. `panel_border` false leaves panels without an outline, told from the background by colour.
  Three more, each optional: `uneven` (0 to 1) makes a shape's four corners differ by that part of the radius;
  `shadow` puts a hard shadow that many dp below and to the right of panels and buttons; `selected_fill` true fills
  a chosen secondary choice with the highlight colour, where Studio outlines it in the accent.
- `art.style`: how the device drawings are drawn. `line`: thin technical lines with dashed centre lines. `cartoon`:
  heavier lines, no centre lines, and each shape's flat colour set off from its line by `art.offset` (in the
  drawing's units; a drawing is 96 wide). `art.stroke` multiplies every line's width.

### Colours

Each palette has the same ten roles:

| Role             | Used for                                                          |
|------------------|-------------------------------------------------------------------|
| `background`     | The screen                                                        |
| `panel`          | Panels; the body of a device drawing                              |
| `raised`         | Dialogs, the widget's buttons                                     |
| `hairline`       | Outlines, dividers, empty tracks                                  |
| `text`           | Text; the lines of a device drawing                               |
| `secondary_text` | Explanations                                                      |
| `label`          | Small labels; the guide lines of a device drawing                 |
| `accent`         | What is chosen or can be pressed; the accents of a device drawing |
| `on_accent`      | Text on the accent                                                |
| `destructive`    | Factory reset and the like                                        |

Five more roles are optional; a look that leaves one out gets the colour of the role in brackets: `outline`, the
outline of panels and controls (`hairline`); `shadow`, the hard shadow (`hairline`); `highlight`, the fill of a
chosen secondary choice (`raised`); `art_fill`, the body of a device drawing (`panel`); `art_accent`, the accents of
a device drawing (`accent`).

A colour is `#RRGGBB`, or one of the colours Android makes from the wallpaper, by its resource name:
`system_accent1_200`, `system_neutral2_800` and so on (`accent1` to `accent3`, `neutral1` and `neutral2`, each from
`0` to `1000`). That is how Material You is written; the app does not use the Material library.

Rules (`docs/DESIGN.md`, Rules for every look): text, secondary text, labels, the accent and the destructive colour
need a contrast of at least 4.5:1 on the background, the panel and the raised colour, and `on_accent` on the accent.
The test checks every pair of fixed colours. It cannot check wallpaper colours: pick tones far apart (200 on 800 or
darker; 600 or darker on 100 or lighter).

## What follows the look, and how

- **Screens**: `ui/theme/Look.kt`. Every screen takes its colours, type and shapes from `Look.colors`, `Look.type`
  and `Look.shape`. There is no colour, font or corner written into a screen, and the test fails on a colour literal.
- **Device drawings**: the drawings are generated once (`tools/gen_device_art.py`) in Studio's colours, and those
  seven colours stand for roles. `deviceArtPainter` (`ui/DeviceDrawing.kt`) shows a drawing with the look's colour
  for each role and its stroke width. So every look gets every drawing, and a new drawing needs nothing per look.
- **Home screen widget**: `widget/WidgetLook.kt`. The widget's layout holds white shapes, tinted with the look's
  colours. A look with two palettes follows the phone's dark mode on the home screen by itself. Wallpaper colours
  are read when the widget is drawn; the app draws it again when the phone's configuration changes.
- **Status notification**: it keeps Android's own notification styles, which sit on Android's surface in any look;
  its icon takes the look's accent.
- **System bars**: light icons on a dark look, dark on a light one.

The launcher icon is Studio's in every look.

## A look that changes more than colours

The parts are there for it, and each is one place:

Sketchbook is the example: nothing in it is a special case in a screen.

- **Shapes of controls**: `ui/Components.kt` draws every button, switch, slider, panel and dialog, and every
  outlined shape goes through one modifier, `surface`, which draws the look's shadow, fill and outline. A look that
  needs something the tokens do not say adds a field to `shape` in the look files, to `LookShape`
  (`ui/theme/Look.kt`) and to the build's reader, and reads it in the component. Screens do not change.
- **Drawings in another style**: `art.style` names it, and `ui/DeviceDrawing.kt` draws it from the same generated
  drawings: a style is a function there that turns each path of a drawing into the paths to draw. So a new style
  reaches all the drawings at once, with nothing regenerated. A style that needs other shapes than the traced ones
  would be a second writer in `tools/gen_device_art.py`; none needs that so far.
