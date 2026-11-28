# Translating PairFree

PairFree's text is in one file. A translation is a copy of that file with the texts changed. Once it is in the app, the
language shows up in the app's language list by itself: nothing else has to be written.

## The short version

1. Copy [`android/app/src/main/res/values/strings.xml`](../android/app/src/main/res/values/strings.xml) to
   `android/app/src/main/res/values-<code>/strings.xml`, where `<code>` is your language's code: `ko`, `de`, `fr`,
   `pt-rBR` for Brazilian Portuguese (a region is written `-r` and the region's letters).
2. Translate the text between the tags. Leave the `name="..."` of each entry as it is.
3. Run `python tools/translation_status.py`. It lists what is wrong, if anything, and how much is translated.
4. Open a pull request with your file. You do not need to build the app.

A translation does not have to be complete. An entry your file leaves out shows in English, so you can send what you have
and add to it later.

## The rules the check enforces

- **Keep the placeholders.** `%1$s`, `%2$d` and the like are filled in by the app (a name, a number). They may move
  within the sentence, but each must stay, with its number and its letter. `%%` is a percent sign.
- **Keep the escapes.** An apostrophe is written `\'`, a quotation mark `\"`, a line break `\n`.
- **Leave out entries marked `translatable="false"`.** They are names (PairFree, the languages of voice prompts in
  their own script) that stay the same in every language.
- **Do not add entries** the English file does not have.
- **Plurals** (`<plurals>`) need at least the `other` form; add the forms your language uses (`one`, `few`, `many`).

## What is in the file

The file runs roughly in the order of the screens: the first screens, Home, a brand's own settings (by brand), the fit
test, recording from the official app, the widget. Comments above an entry explain what its placeholders are where that
is not obvious.

The last part is generated and has two sections:

- **Names PairFree gives the vendors' presets** (`preset_name_...`): equalizer presets that the earbuds know by a number
  and PairFree gives a word. Translate these.
- **Vendor wording** (`vendor_...`): equalizer preset names as the vendor's own app or table words them ("Bass Booster",
  "Jazz", "Reno Featured (Dawn)"), grouped by vendor. Translate them if your language's users would expect it, for
  example where the vendor's own app is translated; leave them out to show the vendor's English. Both are fine.

## What is not translated

- **Reports.** The device report, the connection report and the recording report stay in English in every language. They
  are pasted into issues, where the maintainers read them and the project's tools take them apart. The buttons and the
  explanations around them are translated.
- **Model and brand names**, and what the earbuds themselves send as text (a Sony preset's name from the earbuds, for
  example).
- The documentation, the web site and the store description, for now.

## Right-to-left languages (Persian, Arabic, Hebrew, Urdu)

The app mirrors itself for a right-to-left language by itself: rows, tabs, back and forward arrows. What stays as it is
on purpose (`ui/Direction.kt`, `Physical`): the **left earbud on the left** and the right one on the right (battery card,
fit test, the controls' picker), and anything that runs from low to high going right: the sliders with their minus and plus
buttons, the equalizer's curve and band buttons. If you find something that should flip and does not, or the other way
round, say so in an issue with a screenshot (the Persian translation's first review, issue #44, found all of these).

What a translator can do in the file, since the app cannot guess it:

- **A number with a sign** (`+17`): the sign must come first when it is read from the left. Put `&#8206;` (the invisible
  left-to-right mark) right before the plus or minus, as in `setting_balance_left` of `values-fa`. The signs the app builds
  itself already carry it.
- **A percentage**: `percent`, `setting_percent` and `sleep_upload_sending` hold `%%%1$d` in Persian so that the sign
  shows to the left of the digits; use whatever your language writes.
- **Units in charts**: `db`, `hz` and `khz` are on the equalizer's graph labels and buttons. A language that does not
  write the unit there (Persian leaves it out, since the digits sit on a chart) can use `%1$s` alone.

## Choosing the language in the app

With more than one language in the app, a **Language** button shows on the first screens (asking for Bluetooth access,
and the list of earbuds) and on the Device tab. Each language is listed by its own name. "Same as the phone" follows the
phone's language again. On Android 13 and later the choice also shows under the phone's own per-app language setting.

## Languages so far

<!-- BEGIN LANGUAGES -->
| Language | File | PairFree's text | Vendor wording |
|---|---|---|---|
| English | `values/strings.xml` | 2130 entries | 138 entries |
| `fa` | `values-fa/strings.xml` | 62% (1333) | 92% (128) |
<!-- END LANGUAGES -->

Translators are credited in [CONTRIBUTORS.md](../CONTRIBUTORS.md).

## For maintainers

- The English file is the only text file: a brand's strings go in a section of it, not in a file of their own
  (`tools/test_translations.py` checks).
- The generated sections come from the app's own tables: `gradlew :app:testDebugUnitTest` gathers every preset name the
  app can show and fails while the file lacks one; `python tools/gen_vendor_strings.py` then rewrites the sections and
  `ui/VendorNames.kt`. Run both after adding a brand, a model or a preset.
- `python tools/translation_status.py --write` refreshes the table above.
- The app's list of languages is made at build time from the `values-*` folders that hold a `strings.xml`
  (`BuildConfig.LANGUAGES`, `Languages.kt`); Android's per-app language list from the same folders.
- Renaming or removing an English entry orphans its translations: the check then fails on those files, which is the
  moment to rename the entry in each of them too.
