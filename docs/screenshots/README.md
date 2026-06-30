# Screenshots

App screenshots shown in the project [README](../../README.md#screenshots).

## How to add or update

1. Capture screenshots on a device or emulator (a Pixel-class phone looks
   best). Cropping out the status bar / gesture bar makes the gallery look
   cleaner, but it's optional — display width is set in the README table.
2. Save them in this folder using the filenames below (PNG or WebP).

   **Grid thumbnails** (one single-screen shot each, shown at 200 px):

   | File           | Screen             |
   |----------------|--------------------|
   | `log.png`      | Charging log       |
   | `entry.png`    | Add / edit session |
   | `map.png`      | Map view           |
   | `stats.png`    | Stats              |
   | `vehicle.png`  | Vehicle detail     |
   | `trips.png`    | Trips              |
   | `recap.png`    | Year recap         |
   | `settings.png` | Settings           |

   **Full-length captures** (extended/long screenshots for screens that scroll
   past one screenful). These feed the collapsible "Full-length captures"
   expanders in the README and are shown at 320 px wide so detail stays
   legible. Only these five screens have a full version:

   | File                | Screen             |
   |---------------------|--------------------|
   | `entry-full.png`    | Add / edit session |
   | `stats-full.png`    | Stats              |
   | `vehicle-full.png`  | Vehicle detail     |
   | `recap-full.png`    | Year recap         |
   | `settings-full.png` | Settings           |

3. Keep them reasonably sized — full-res phone PNGs are ~1–2 MB each, and the
   long captures are larger still.
   Converting to WebP (`cwebp -q 80 log.png -o log.webp`) cuts that by
   ~70%. If you switch to `.webp`, update the file extensions in the root
   README's Screenshots table to match.

Remove a row from the README table (and skip that file) for any screen you
don't want to include — the table is plain HTML, so deleting a `<td>` is all
it takes.
