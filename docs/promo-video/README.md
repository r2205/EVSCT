# EVSCT promo video

A 48-second, 1080p motion-graphics promo built entirely from the app
screenshots in [`../screenshots`](../screenshots) and the brand marks in
[`../branding`](../branding). The rendered result is
[`evsct-promo.mp4`](evsct-promo.mp4).

| Time | Scene |
| --- | --- |
| 0–4.6 s | Logo build: bars, amber V, route line, charge stop, pin |
| 4.2–10 s | Charging log, with the totals card and a session card popping out |
| 10–16 s | Live tracking: notification stopwatch, then the entry form scrolls |
| 16–22 s | Map, with the trip pins as a backdrop |
| 21–27 s | Stats, with the "vs gas" card called out |
| 27–33 s | Vehicles & trips |
| 32–38 s | Year recap |
| 38–42 s | No account / no server / no analytics |
| 42–48 s | End card: logo, stack, GitHub link, phone fan |

## How it works

`index.html` is the whole composition. Every frame is a pure function of
time (`seek(t)`), with no CSS animations or timers, so `render.py` can step
through it frame by frame in headless Chromium and pipe PNGs straight into
ffmpeg (H.264, `yuv420p`, `+faststart`).

It's also a regular web page that plays in a loop and scales to fit any
window. GitHub Pages serves `docs/`, so from the default branch it's live at
**https://r2205.github.io/EVSCT/promo-video/**, or open `index.html` locally.
Space (or a tap) plays and pauses, ←/→ jump a second, and `#t=21` on the URL
starts at 21 s.

## Re-rendering

```sh
pip install playwright imageio-ffmpeg
playwright install chromium        # or: export CHROMIUM=/path/to/chrome
cd docs/promo-video
python3 render.py                  # -> evsct-promo.mp4
python3 render.py --fps 60         # smoother, twice the frames
python3 render.py --stills 7,24.5  # PNG stills for checking a layout
```

The video picks up new screenshots automatically. Callout crops are
pixel rectangles (`data-crop="x,y,w,h"` on each `.lens`), so re-check those
if a screen's layout changes. Timings live in the `SCENES` array and the
`CH` chapter list.

The video is silent; drop a music track in with, for example,
`ffmpeg -i evsct-promo.mp4 -i track.mp3 -c:v copy -c:a aac -shortest out.mp4`.

Fonts: [Space Grotesk](https://fonts.google.com/specimen/Space+Grotesk) and
[Inter](https://fonts.google.com/specimen/Inter), both under the SIL Open
Font License, bundled in `fonts/`.
