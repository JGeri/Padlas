# Third party assets

## Fonts

The typefaces are bundled in `core/design/src/main/res/font/` and are all licensed under
the **SIL Open Font License 1.1**, which permits bundling and redistribution inside an
application as long as the fonts themselves are not sold on their own.

| File | Family | Source |
|---|---|---|
| `instrument_serif_regular.ttf` | Instrument Serif | https://fonts.google.com/specimen/Instrument+Serif |
| `instrument_sans_regular.ttf` | Instrument Sans 400 | https://fonts.google.com/specimen/Instrument+Sans |
| `instrument_sans_medium.ttf` | Instrument Sans 500 | https://fonts.google.com/specimen/Instrument+Sans |
| `instrument_sans_semibold.ttf` | Instrument Sans 600 | https://fonts.google.com/specimen/Instrument+Sans |
| `geist_mono_regular.ttf` | Geist Mono 400 | https://fonts.google.com/specimen/Geist+Mono |
| `geist_mono_medium.ttf` | Geist Mono 500 | https://fonts.google.com/specimen/Geist+Mono |

They are bundled rather than fetched through the downloadable-fonts provider because the
first screen the app ever shows is the one with the 56sp hero number on it; a face that
arrives a second late would land exactly there.

Full licence text: https://openfontlicense.org/open-font-license-official-text/

## Libraries

Every runtime dependency is declared in `gradle/libs.versions.toml`. They are Apache-2.0
licensed with the exception of the Google Play services and ML Kit artifacts, which are
covered by the Google APIs Terms of Service.
