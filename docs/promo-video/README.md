# EVSCT promo video

A 48-second, 1080p motion-graphics promo built entirely from the app
screenshots in [`../screenshots`](../screenshots) and the brand marks in
[`../branding`](../branding), scored with an original cinematic soundtrack
generated in code. The rendered result is [`evsct-promo.mp4`](evsct-promo.mp4).

| Time | Scene |
| --- | --- |
| 0–4.4 s | Logo build: bars, amber V, route line, charge stop, pin (a pluck or hit on each), riser |
| 4.4 s | Charging log; the drop, where the taiko and string ostinato start |
| 10.0 s | Live tracking: notification stopwatch, then the entry form scrolls |
| 15.6 s | Map, with the trip pins as a backdrop; a bell melody enters |
| 21.2 s | Stats, with the "vs gas" card called out; hats join |
| 26.8 s | Vehicles & trips; high strings join |
| 32.4 s | Year recap |
| 38.0 s | No account / no server / no analytics, one brass hit per word |
| 42.2 s | End card on an F-major chord: logo, stack, GitHub link, phone fan |

Each time is a scene cut and lands on a downbeat: the score runs at 86 BPM
with 2.8 s bars, so every scene is exactly two bars.

## How it works

`index.html` is the whole composition. Every frame is a pure function of
time (`seek(t)`), with no CSS animations or timers, so `render.py` can step
through it frame by frame in headless Chromium and pipe PNGs straight into
ffmpeg (H.264, `yuv420p`, `+faststart`).

It's also a regular web page that plays in a loop and scales to fit any
window. GitHub Pages serves `docs/`, so from the default branch it's live at
**https://r2205.github.io/EVSCT/promo-video/**, or open `index.html` locally.
Browsers don't autoplay sound, so it starts as a silent loop with a **Play
with sound** button; while sound is on, the music drives the animation clock,
so they can't drift apart. Space (or a tap) plays and pauses, ←/→ jump a
second, and `#t=21` on the URL starts at 21 s.

## Re-rendering

```sh
pip install playwright imageio-ffmpeg
playwright install chromium        # or: export CHROMIUM=/path/to/chrome
cd docs/promo-video
python3 render.py                  # -> evsct-promo.mp4
python3 render.py --fps 60         # smoother, twice the frames
python3 render.py --stills 7,24.5  # PNG stills for checking a layout
python3 render.py --no-audio       # a silent cut, e.g. for muted autoplay feeds
```

## The soundtrack

`music.py` synthesizes the score from scratch with numpy and scipy (additive
string pads, a galloping string ostinato, taiko, bells, brass hits, risers and
a synthetic reverb), so it carries no third-party licence. It writes
`music.m4a` (AAC, which `render.py` muxes into the MP4) and `music.mp3` (for
the web page, since not every browser decodes AAC), both normalized to
−14 LUFS. It's seeded, so re-running it gives the same track.

```sh
pip install numpy scipy imageio-ffmpeg
python3 music.py && python3 render.py
```

The score and `SCENES` in `index.html` share one timeline; if you move a scene
cut, move the matching bar in `music.py` (`T0`, `BAR`, `BREAK`, `FINAL`).

The video picks up new screenshots automatically. Callout crops are
pixel rectangles (`data-crop="x,y,w,h"` on each `.lens`), so re-check those
if a screen's layout changes. Timings live in the `SCENES` array and the
`CH` chapter list.

To use a different track instead, pass `--audio track.m4a` to `render.py`.

Fonts: [Space Grotesk](https://fonts.google.com/specimen/Space+Grotesk) and
[Inter](https://fonts.google.com/specimen/Inter), both under the SIL Open
Font License, bundled in `fonts/`.
