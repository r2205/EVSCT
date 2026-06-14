# EVSCT brand assets

The EVSCT mark combines the **EVSCT** wordmark — the `E` drawn as three
charge-level / log bars and an amber `V` — with a **route + charge stop**
motif that nods to road-trip charging and the in-app map.

## Files

| File | Use |
| --- | --- |
| `evsct-icon.svg` / `.svgz` / `-1024.png` / `-512.png` | App icon / square mark (stacked `EV` · route · `SCT`) |
| `evsct-banner.svg` / `.svgz` / `.png` | Dark banner for the README header, splash, store listing |
| `evsct-lockup.svg` / `.svgz` / `.png` | Horizontal lockup (icon badge + wordmark) for light backgrounds |

All SVGs are self-contained — the letters are outlined to paths, so no font
needs to be installed to render them. The `.svgz` files are gzip-compressed
SVGs (identical content, ~60% smaller).

## Palette

| Token | Hex | Where |
| --- | --- | --- |
| Deep emerald | `#155538` → `#0C3A2A` | Badge background gradient |
| Icon background | `#0F4C3A` | Adaptive launcher background |
| Mint | `#A8F5BF` → `#5BC489` | Bars, `SCT`, route highlights |
| Amber | `#FFD27A` → `#FFA000` | `V`, charge-stop node |
| Route green | `#3FB372` | Route line + endpoint dots |

The Android adaptive launcher icon is generated from the same geometry:
`app/src/main/res/drawable/ic_launcher_foreground.xml` (full-colour) and
`ic_launcher_monochrome.xml` (themed-icon silhouette), scaled into the
adaptive-icon safe zone.
