#!/usr/bin/env python3
"""Render index.html to an MP4, frame by frame.

The composition is a pure function of time (window.seek(t)), so each frame is
seeked and screenshotted in headless Chromium and piped straight into ffmpeg.

    pip install playwright imageio-ffmpeg
    python3 render.py                      # -> evsct-promo.mp4 (1080p30)
    python3 render.py --fps 60 --out x.mp4
    python3 render.py --stills 2,7,13      # PNG stills at those seconds, no video
    python3 render.py --no-audio           # silent (otherwise music.m4a is muxed in)

Uses the Chromium that Playwright finds (`playwright install chromium`), or
set CHROMIUM=/path/to/chrome to use a specific browser binary.
"""
import argparse
import os
import pathlib
import subprocess
import sys
import time

from playwright.sync_api import sync_playwright

HERE = pathlib.Path(__file__).resolve().parent


def ffmpeg_exe():
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except ImportError:
        return "ffmpeg"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--fps", type=int, default=30)
    ap.add_argument("--out", default=str(HERE / "evsct-promo.mp4"))
    ap.add_argument("--crf", type=int, default=18)
    ap.add_argument("--stills", help="comma-separated seconds; writes still-<t>.png instead of a video")
    ap.add_argument("--start", type=float, default=0.0)
    ap.add_argument("--end", type=float, default=None)
    ap.add_argument("--audio", default=str(HERE / "music.m4a"), help="soundtrack to mux in (see music.py)")
    ap.add_argument("--no-audio", action="store_true")
    args = ap.parse_args()

    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=os.environ.get("CHROMIUM") or None, args=["--allow-file-access-from-files", "--force-color-profile=srgb"])
        page = browser.new_page(viewport={"width": 1920, "height": 1080}, device_scale_factor=1)
        page.goto((HERE / "index.html").as_uri())
        page.evaluate("window.__ready")
        duration = page.evaluate("window.DURATION")

        if args.stills:
            for s in args.stills.split(","):
                t = float(s)
                page.evaluate(f"seek({t})")
                out = HERE / f"still-{t:05.2f}.png"
                page.screenshot(path=str(out))
                print(out)
            browser.close()
            return

        end = args.end if args.end is not None else duration
        n = int(round((end - args.start) * args.fps))
        cmd = [ffmpeg_exe(), "-y", "-loglevel", "error",
               "-f", "image2pipe", "-framerate", str(args.fps), "-c:v", "png", "-i", "-"]
        audio = not args.no_audio and os.path.exists(args.audio)
        if audio:  # the score is written against the same timeline, so offset it to --start
            cmd += ["-ss", str(args.start), "-i", args.audio, "-map", "0:v", "-map", "1:a", "-c:a", "copy", "-shortest"]
        cmd += ["-c:v", "libx264", "-preset", "slow", "-crf", str(args.crf),
                "-pix_fmt", "yuv420p", "-movflags", "+faststart", args.out]
        print("with soundtrack " + args.audio if audio else "silent")
        ff = subprocess.Popen(cmd, stdin=subprocess.PIPE)
        t0 = time.time()
        for i in range(n):
            t = args.start + i / args.fps
            page.evaluate(f"seek({t})")
            ff.stdin.write(page.screenshot(type="png"))
            if i % args.fps == 0:
                el = time.time() - t0
                sys.stdout.write(f"\r{i}/{n} frames  {el:5.0f}s elapsed")
                sys.stdout.flush()
        ff.stdin.close()
        rc = ff.wait()
        browser.close()
        print(f"\nwrote {args.out} ({os.path.getsize(args.out) / 1e6:.1f} MB)" if rc == 0 else f"\nffmpeg failed ({rc})")
        sys.exit(rc)


if __name__ == "__main__":
    main()
