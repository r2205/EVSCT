# Screenshots

App screenshots shown in the project [README](../../README.md#screenshots).
Stored as **lossless WebP** (converted from the original PNG captures) to keep
the repo lean without any quality loss.

## How to add or update

1. Capture screenshots on a device or emulator (a Pixel-class phone looks
   best). Cropping out the status bar / gesture bar makes the gallery look
   cleaner, but it's optional — display width is set in the README table.
2. Convert each capture to WebP and save it in this folder using the filenames
   below. Lossless keeps text razor-sharp; lossy (`-q 82`) is smaller and looks
   identical at the displayed size — either is fine.

   ```sh
   # lossless (what the current set uses)
   cwebp -lossless log.png -o log.webp
   # or lossy
   cwebp -q 82 log.png -o log.webp
   ```

   **Grid thumbnails** (one single-screen shot each, shown at 200 px):

   | File            | Screen             |
   |-----------------|--------------------|
   | `log.webp`      | Charging log       |
   | `entry.webp`    | Add / edit session |
   | `map.webp`      | Map view           |
   | `stats.webp`    | Stats              |
   | `vehicle.webp`  | Vehicle detail     |
   | `trips.webp`    | Trips              |
   | `recap.webp`    | Year recap         |
   | `settings.webp` | Settings           |

   **Full-length captures** (extended/long screenshots for screens that scroll
   past one screenful). These feed the collapsible "Full-length captures"
   expanders in the README and are shown at 320 px wide so detail stays
   legible. Only these five screens have a full version:

   | File                 | Screen             |
   |----------------------|--------------------|
   | `entry-full.webp`    | Add / edit session |
   | `stats-full.webp`    | Stats              |
   | `vehicle-full.webp`  | Vehicle detail     |
   | `recap-full.webp`    | Year recap         |
   | `settings-full.webp` | Settings           |

3. No need to resize — display width is set in the root README's `<img>` tags
   (200 px grid, 320 px full-length). Just keep filenames matching the table.

If you prefer a different format/extension, update the matching `<img src>`
lines in the root README's Screenshots table. To drop a screen entirely,
delete its `<td>` (grid) or `<details>` block (full-length) there.
