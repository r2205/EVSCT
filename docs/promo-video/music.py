#!/usr/bin/env python3
"""Synthesize the promo's cinematic score (music.m4a for the MP4, music.mp3 for the page).

Everything is generated from scratch (additive synths, noise, a synthetic
reverb), so the track carries no third-party licence. It's written against
the video's timeline in index.html:

    0.0–4.4   intro: dark drone, a pluck per logo piece, riser into the drop
    4.4       drop: impact, then taiko + a galloping string ostinato
              one chord per bar (86 BPM, 2.8 s bars, Dm Bb F C), so every
              5.6 s scene cut lands on a downbeat with a lighter impact
    15.6      a bell melody enters (map)
    21.2      hats join (stats); 26.8 a high string line (trips)
    38.0      break: three brass hits, one per privacy word, then a riser
    42.2      end card: F-major chord and a rising bell motif, ringing out

    pip install numpy scipy imageio-ffmpeg
    python3 music.py            # -> music.m4a (AAC, for the MP4) + music.mp3 (for the page)
"""
import json
import pathlib
import subprocess
import wave

import numpy as np
from scipy.signal import butter, fftconvolve, sosfilt

HERE = pathlib.Path(__file__).resolve().parent
SR = 48000
DUR = 48.0
N = int(SR * DUR)
rng = np.random.default_rng(2205)

# ---- timeline (must match SCENES in index.html) -------------------------
T0 = 4.4               # the drop; first downbeat
BEAT = 0.7             # 85.7 BPM
BAR = 4 * BEAT         # 2.8 s; a scene is two bars
BREAK = T0 + 12 * BAR  # 38.0, privacy words
WORDS = [BREAK, BREAK + BEAT, BREAK + 2 * BEAT]
FINAL = 42.2           # outro logo lands

dry = np.zeros((2, N))
wet = np.zeros((2, N))  # reverb send bus


def midi(m):
    return 440.0 * 2 ** ((m - 69) / 12)


def tt(dur):
    return np.arange(int(dur * SR)) / SR


def add(sig, t0, gain=1.0, pan=0.0, send=0.0):
    """Mix a mono or stereo signal in at time t0 (equal-power pan)."""
    sig = np.asarray(sig, dtype=float)
    if sig.ndim == 1:
        a = (pan + 1) * np.pi / 4
        sig = np.vstack([sig * np.cos(a), sig * np.sin(a)]) * np.sqrt(2)
    i0 = int(round(t0 * SR))
    if i0 < 0:
        sig, i0 = sig[:, -i0:], 0
    n = min(sig.shape[1], N - i0)
    if n <= 0:
        return
    dry[:, i0:i0 + n] += sig[:, :n] * gain
    wet[:, i0:i0 + n] += sig[:, :n] * gain * send


def env(n, a, r, hold=None):
    """Linear attack, sustain, cosine release over n samples."""
    e = np.ones(n)
    na, nr = int(a * SR), int(r * SR)
    if na:
        e[:na] = np.linspace(0, 1, na)
    if nr:
        e[-nr:] *= 0.5 * (1 + np.cos(np.linspace(0, np.pi, nr)))
    return e


def saw(f, dur, bright=12.0, detune=0.0, rolloff=1.0, vib=0.0):
    """Band-limited saw by additive synthesis. `bright` sets where the
    harmonics roll off (a gentle low-pass); it can be an array over time."""
    t = tt(dur)
    f = f * 2 ** (detune / 1200)
    ph = 2 * np.pi * f * t
    if vib:
        ph = ph + vib * np.sin(2 * np.pi * 5.2 * t) * (1 - np.exp(-t / 0.6))
    out = np.zeros_like(t)
    h = 1
    while h * f < SR * 0.45 and h <= 40:
        out += np.sin(h * ph + rng.uniform(0, 2 * np.pi)) / h ** rolloff * np.exp(-(h - 1) / bright)
        h += 1
    return out


def lp(x, fc, order=2):
    return sosfilt(butter(order, fc, 'low', fs=SR, output='sos'), x)


def hp(x, fc, order=2):
    return sosfilt(butter(order, fc, 'high', fs=SR, output='sos'), x)


def noise(dur):
    return rng.standard_normal(int(dur * SR))


# ---- instruments ---------------------------------------------------------
def pad(notes, t0, dur, gain, bright=8.0, attack=0.6, release=1.0, cutoff=2400, send=0.45):
    """Detuned string-ish chord: three voices per note, spread in stereo."""
    for m in notes:
        for det, pan in ((-9, -0.6), (0, 0.0), (9, 0.6)):
            s = saw(midi(m), dur + release, bright=bright, detune=det + rng.uniform(-2, 2), vib=0.0025)
            s = lp(s, cutoff) * env(len(s), attack, release)
            add(s, t0, gain / len(notes), pan, send)


def bass(m, t0, dur, gain):
    t = tt(dur + 0.4)
    f = midi(m)
    s = np.sin(2 * np.pi * f * t) + 0.35 * np.sin(4 * np.pi * f * t) + 0.12 * np.sin(6 * np.pi * f * t)
    s = np.tanh(1.4 * s) * env(len(t), 0.05, 0.4)
    add(s, t0, gain, 0.0, 0.05)


def pluck(m, t0, gain, pan=0.0, decay=0.18, bright=5.0, send=0.2):
    s = saw(midi(m), decay * 5, bright=bright, rolloff=1.2)
    t = tt(decay * 5)
    s *= np.exp(-t / decay) * np.minimum(1, t / 0.003)
    add(s, t0, gain, pan, send)


def bell(m, t0, gain, pan=0.0, send=0.55, length=3.5):
    t = tt(length)
    f = midi(m)
    s = np.zeros_like(t)
    for ratio, amp, dec in ((1, 1, 1.9), (2, .45, 1.1), (3, .22, .7), (4.17, .16, .45), (5.43, .08, .3)):
        s += amp * np.sin(2 * np.pi * f * ratio * t) * np.exp(-t / dec)
    s *= np.minimum(1, t / 0.002)
    add(s, t0, gain, pan, send)


def taiko(t0, gain, pan=0.0):
    t = tt(1.2)
    f = 58 + 115 * np.exp(-t / 0.028)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.3)
    skin = lp(hp(noise(1.2), 180), 5000) * np.exp(-t / 0.018) * 0.6
    s = np.tanh(1.6 * (body + skin)) * np.minimum(1, t / 0.001)
    add(s, t0, gain, pan, 0.22)


def hat(t0, gain, pan=0.25):
    t = tt(0.12)
    s = hp(noise(0.12), 7500, 4) * np.exp(-t / 0.028)
    add(s, t0, gain, pan, 0.08)


def impact(t0, gain, length=3.0):
    """Cinematic hit: a sub drop, a noise burst and a long reverb tail."""
    t = tt(length)
    f = 32 + 70 * np.exp(-t / 0.12)
    sub = 0.6 * np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 1.0)
    burst = lp(noise(length), 7000) * np.exp(-t / 0.08) * 0.6
    s = np.tanh(1.3 * (sub + burst)) * np.minimum(1, t / 0.002)
    add(s, t0, gain, 0.0, 0.45)


def braam(t0, gain, length):
    """Low brass hit: a saw stack whose harmonics open up then close."""
    t = tt(length + 0.6)
    bright = 2 + 14 * np.minimum(1, t / 0.08) * np.exp(-t / 0.5)
    e = env(len(t), 0.015, 0.6) * (0.55 + 0.45 * np.exp(-t / 0.35))
    for m in (26, 38, 45, 50):
        for det, pan in ((-11, -0.5), (11, 0.5)):
            f = midi(m) * 2 ** (det / 1200)
            s = np.zeros_like(t)
            h = 1
            while h * f < 6000 and h <= 30:
                s += np.sin(2 * np.pi * f * h * t) / h * np.exp(-(h - 1) / bright)
                h += 1
            add(np.tanh(1.8 * s) * e, t0, gain / 4, pan, 0.4)


def riser(t0, t1, gain):
    """Noise through a resonant band-pass sweeping up, swelling in."""
    n = int((t1 - t0) * SR)
    x = noise(t1 - t0)
    k = np.linspace(0, 1, n)
    fc = 250 * (40 ** (k ** 1.6))                # 250 Hz -> 10 kHz
    g = np.tan(np.pi * np.minimum(fc, SR * 0.45) / SR)
    q = 4.0
    ic1 = ic2 = 0.0
    out = np.empty(n)
    for i in range(n):                            # state-variable filter
        gi = g[i]
        a1 = 1 / (1 + gi * (gi + 1 / q))
        v1 = a1 * (ic1 + gi * (x[i] - ic2))
        v2 = ic2 + gi * v1
        ic1, ic2 = 2 * v1 - ic1, 2 * v2 - ic2
        out[i] = v1
    out *= k ** 2.2
    tone = saw(midi(62), t1 - t0, bright=4) * (k ** 3) * 0.25   # pitched swell under it
    add(out / (np.abs(out).max() + 1e-9) + tone, t0, gain, 0.0, 0.35)


# ---- the score -----------------------------------------------------------
CHORDS = [  # (pad voicing, bass, ostinato root, melody [(beat, midi, beats)])
    ([50, 57, 62, 65, 69], 38, 50, [(0, 69, 2), (2, 74, 2)]),              # Dm
    ([46, 53, 62, 65, 70], 34, 46, [(0, 77, 3), (3, 74, 1)]),              # Bb
    ([53, 57, 60, 65, 69], 41, 53, [(0, 72, 2), (2, 69, 2)]),              # F
    ([48, 55, 60, 64, 67], 36, 48, [(0, 67, 2), (2, 64, 1), (3, 67, 1)]),  # C
]
GALLOP = [1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 1, 0]  # 3-3-3-3-2-2 accents

# intro: drone swelling out of silence
pad([38, 45, 50, 57], 0.0, T0 - 0.2, 0.30, bright=4, attack=3.2, release=0.5, cutoff=1400, send=0.6)
bass(26, 0.0, T0 - 0.3, 0.05)
for i, (m, at) in enumerate(((62, .30), (65, .39), (69, .48))):       # the three E bars
    pluck(m + 12, at, 0.16, pan=-0.3 + 0.3 * i, send=0.45)
pluck(81, 0.70, 0.10, pan=0.2, send=0.5)                               # the amber V
pluck(74, 1.25, 0.12, pan=-0.4, send=0.5)                              # route start ring
impact(1.92, 0.22, length=2.2)                                         # charge stop pops
bell(74, 1.92, 0.16, -0.2); bell(81, 1.92, 0.10, 0.2)
bell(86, 2.42, 0.10, 0.35)                                             # pin drops
riser(2.7, T0 - 0.03, 0.22)

# main section: 12 bars from the drop to the break
for k in range(12):
    t = T0 + k * BAR
    notes, b, root, mel = CHORDS[k % 4]
    lift = k / 11                                   # the arrangement builds
    pad(notes, t - 0.05, BAR, 0.24 + 0.14 * lift, bright=9 + 8 * lift, attack=0.25, release=0.9,
        cutoff=3200 + 3500 * lift)
    if k >= 6:                                      # an airy octave on top as it builds
        pad([n + 12 for n in notes[-3:]], t - 0.05, BAR, 0.06 + 0.05 * lift, bright=14, attack=0.4,
            release=0.9, cutoff=9000, send=0.7)
    bass(b, t, BAR - 0.05, 0.12)
    # string ostinato
    for s16 in range(16):
        acc = GALLOP[s16]
        pluck(root, t + s16 * BEAT / 4, 0.05 + 0.05 * acc, pan=-0.25, decay=0.07, bright=8 + 8 * acc, send=0.12)
        if acc:
            pluck(root + 12, t + s16 * BEAT / 4, 0.045, pan=0.3, decay=0.07, bright=12, send=0.15)
    # taiko: 1, the "and" of 2, 3, plus a crescendo roll into every scene cut
    for beat, g in ((0, .42), (1.5, .25), (2, .34)):
        taiko(t + beat * BEAT, g, pan=-0.1)
    if k % 2 == 1:
        for i, beat in enumerate((3, 3.25, 3.5, 3.75)):
            taiko(t + beat * BEAT, 0.16 + 0.07 * i, pan=0.15)
    else:
        taiko(t + 3.5 * BEAT, 0.18, pan=0.15)
    if k % 2 == 0:
        impact(t, 0.55 if k == 0 else 0.26)         # every scene cut lands on a hit
    if k >= 6:                                      # hats from the stats scene on
        for e8 in range(8):
            hat(t + e8 * BEAT / 2, 0.07 if e8 % 2 else 0.04)
    if k >= 4:                                      # bell melody from the map scene on
        for beat, m, _ in mel:
            bell(m, t + beat * BEAT, 0.11, pan=0.25, send=0.5)
    if k >= 8:                                      # high strings from trips on
        for beat, m, beats in mel:
            s = saw(midi(m + 12), beats * BEAT + 0.5, bright=10, rolloff=1.2, vib=0.004)
            s = lp(s, 8000) * env(len(s), 0.12, 0.5)
            add(s, t + beat * BEAT, 0.05 + 0.015 * (k - 8), 0.35, 0.6)

# break: silence the groove, one brass hit per privacy word
for i, w in enumerate(WORDS):
    braam(w, 0.34, 1.6 if i == 2 else 0.55)
    taiko(w, 0.45)
impact(WORDS[0], 0.35)
pad([38, 45, 50], WORDS[2] + 0.4, FINAL - WORDS[2] - 0.6, 0.18, bright=3, attack=1.2, release=0.3, cutoff=700)
riser(40.1, FINAL - 0.05, 0.26)

# end card: resolve to F major, rising bells, ring out
impact(FINAL, 0.6, length=5.0)
taiko(FINAL, 0.5)
pad([41, 53, 60, 67, 69, 72, 77], FINAL, DUR - FINAL, 0.55, bright=14, attack=0.04, release=2.5, cutoff=7000, send=0.6)
bass(29, FINAL, DUR - FINAL - 0.5, 0.12)
for i, m in enumerate((72, 77, 81)):
    bell(m, FINAL + i * BEAT, 0.14, pan=-0.2 + 0.2 * i, length=4.5)
bell(84, FINAL + 3 * BEAT + 0.7, 0.09, pan=0.3, length=4.0)

# ---- reverb, master, encode ---------------------------------------------
ir_t = tt(3.2)
ir = np.vstack([lp(noise(3.2), 6000) * np.exp(-ir_t / 0.75) for _ in range(2)])
ir[:, :int(0.012 * SR)] = 0                         # a short pre-delay
ir /= np.sqrt((ir ** 2).sum(axis=1, keepdims=True))
rev = np.vstack([fftconvolve(wet[c], ir[c])[:N] for c in range(2)])
mix = dry + 0.55 * rev
mix = hp(mix, 28)                                   # clear sub-sonic rumble
mix = mix - 0.3 * lp(mix, 90) + 0.6 * hp(mix, 3000)  # master EQ: ~-3 dB lows, ~+4 dB highs for small speakers

fade = np.ones(N)
fi, fo = int(0.03 * SR), int(2.0 * SR)
fade[:fi] = np.linspace(0, 1, fi)
fade[-fo:] = 0.5 * (1 + np.cos(np.linspace(0, np.pi, fo)))
mix *= fade
mix = np.tanh(1.5 * mix / np.abs(mix).max()) / np.tanh(1.5)  # gentle glue

wav = HERE / 'music.wav'
pcm = (np.clip(mix, -1, 1).T * 32767).astype('<i2')
with wave.open(str(wav), 'wb') as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes(pcm.tobytes())

try:
    import imageio_ffmpeg
    ff = imageio_ffmpeg.get_ffmpeg_exe()
except ImportError:
    ff = 'ffmpeg'
# two-pass loudnorm in linear mode: one gain for the whole track, so the
# quiet intro and the break stay quieter than the groove
LN = 'loudnorm=I=-14:TP=-1.5:LRA=20'
probe = subprocess.run([ff, '-hide_banner', '-i', str(wav), '-af', LN + ':print_format=json', '-f', 'null', '-'],
                       capture_output=True, text=True).stderr
meas = json.loads(probe[probe.rindex('{'):probe.rindex('}') + 1])
LN += (f":measured_I={meas['input_i']}:measured_TP={meas['input_tp']}:measured_LRA={meas['input_lra']}"
       f":measured_thresh={meas['input_thresh']}:offset={meas['target_offset']}:linear=true")
# AAC for muxing into the MP4; MP3 for the web page, since not every browser
# decodes AAC (Chromium builds without proprietary codecs, some Firefox setups)
for name, codec in (('music.m4a', ['-c:a', 'aac', '-b:a', '192k']), ('music.mp3', ['-c:a', 'libmp3lame', '-b:a', '160k'])):
    subprocess.run([ff, '-y', '-loglevel', 'error', '-i', str(wav), '-af', LN, '-ar', str(SR), *codec,
                    str(HERE / name)], check=True)
    print('wrote', HERE / name)
wav.unlink()
