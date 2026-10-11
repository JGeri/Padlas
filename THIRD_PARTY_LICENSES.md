# Third party assets

## Fonts

The typefaces are bundled in `core/design/src/main/res/font/` and are all licensed under
the **SIL Open Font License 1.1**, which permits bundling and redistribution inside an
application as long as the fonts themselves are not sold on their own.

| File | Family | Source |
|---|---|---|
| `instrument_sans_regular.ttf` | Instrument Sans 400 | https://fonts.google.com/specimen/Instrument+Sans |
| `instrument_sans_medium.ttf` | Instrument Sans 500 | https://fonts.google.com/specimen/Instrument+Sans |
| `instrument_sans_semibold.ttf` | Instrument Sans 600 | https://fonts.google.com/specimen/Instrument+Sans |

They are bundled rather than fetched through the downloadable-fonts provider so the first
screen never renders in a fallback face. Instrument Sans carries real tabular figures,
which is why it is the only family the app needs.

## Icons

Material Symbols (`androidx.compose.material:material-icons-extended`), Apache-2.0.

Full licence text: https://openfontlicense.org/open-font-license-official-text/

## Libraries

Every runtime dependency is declared in `gradle/libs.versions.toml`. They are Apache-2.0
licensed with the exception of the Google Play services and ML Kit artifacts, which are
covered by the Google APIs Terms of Service.
