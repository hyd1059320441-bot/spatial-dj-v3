#!/usr/bin/env python3
"""Deterministic original Future Bounce score and band-limited stereo sound bank.
No third-party melodies, recordings or samples. Final master uses the Android renderer.
"""
import json
import math
import wave
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
ASSETS = ROOT / "app/src/main/assets"
RATE = 48000
RNG = np.random.default_rng(28734)


def highpass(x, cutoff):
    # Offline zero-phase FFT filtering, with padding to avoid wrapping the transient.
    length = 1 << (len(x) * 2 - 1).bit_length()
    freq = np.fft.rfftfreq(length, 1 / RATE)
    response = 1 - np.exp(-(freq / cutoff) ** 4)
    return np.fft.irfft(np.fft.rfft(x, length) * response, length)[:len(x)]


def noise(t, low=1800, high=15000):
    x = RNG.standard_normal(len(t))
    length = 1 << (len(t) * 2 - 1).bit_length()
    freq = np.fft.rfftfreq(length, 1 / RATE)
    response = (1 - np.exp(-(freq / low) ** 4)) * np.exp(-(freq / high) ** 6)
    x = np.fft.irfft(np.fft.rfft(x, length) * response, length)[:len(t)]
    return x / max(np.std(x), 1e-8)


def saw(t, f, cutoff, phase=0, detune=1):
    # Time-varying harmonic rolloff gives a pluck filter without aliasing oscillators.
    result = np.zeros(len(t))
    f *= detune
    for h in range(1, min(72, int(RATE * .45 / f)) + 1):
        result += np.sin(2 * np.pi * f * h * t + phase * h) / h * np.exp(-(f * h / cutoff) ** 2)
    return result * .64


def env(t, attack, decay, duration, release=.025):
    return np.minimum(t / attack, 1) * np.exp(-t * decay) * np.clip((duration - t) / release, 0, 1)


def stereo(l, r=None):
    return np.stack((l, l if r is None else r), axis=1)


def one_shot(lane, pad):
    duration = [(.40, .10, .34, .38, 1.25, 3.75)[pad] if lane == 0 else .32,
                .32, .30, .58, .85][lane]
    t = np.arange(round(RATE * duration)) / RATE
    if lane == 0:
        if pad == 4:
            x = noise(t, 3700, 14500) * env(t, .001, 4.6, duration, .12) * .18
            body = np.sin(2 * np.pi * np.cumsum(38 + 140 * np.exp(-t * 50)) / RATE) * np.exp(-t * 24) * .28
            return stereo(x + body, np.roll(x, 89) + body)
        if pad == 5:
            rising = (t / duration) ** 1.7
            x = noise(t, 1900, 16000) * rising * .12
            chirp = np.sin(2 * np.pi * (190 * t + 85 * t ** 2)) * rising * .035
            x = (x + chirp) * np.clip((duration - t) / .18, 0, 1)
            return stereo(x, np.roll(x, 113))
        if pad == 0:
            # Sub fundamental, brief pitch click, upper punch, no buzzy sustained top.
            freq = 49 + 135 * np.exp(-t * 62) + 24 * np.exp(-t * 15)
            phase = 2 * np.pi * np.cumsum(freq) / RATE
            x = np.sin(phase) * env(t, .0007, 10, duration, .05) * .84
            x += np.sin(phase * 2.02) * np.exp(-t * 42) * .15
            x += noise(t, 3500, 12000) * np.exp(-t * 600) * .065
            return stereo(np.tanh(x * 1.22) * .82)
        if pad in (1, 2):
            x = noise(t, 6000, 16000) * env(t, .0004, 48 if pad == 1 else 13, duration)
            # Multiple inharmonic metallic resonators under the noise.
            x += sum(np.sin(2 * np.pi * f * t) for f in (4831, 6217, 7891)) * .12 * np.exp(-t * 40)
            return stereo(x * .085, np.roll(x, 11) * .085)
        freq = 95 + 110 * np.exp(-t * 26)
        x = np.sin(2 * np.pi * np.cumsum(freq) / RATE) * env(t, .001, 15, duration) * .5
        return stereo(x)
    if lane == 1:
        bursts = sum(np.exp(-np.maximum(t - d, 0) * 110) * (t >= d) for d in (0, .012, .025, .040))
        body = np.sin(2 * np.pi * 182 * t) * np.exp(-t * 33) * .19
        x = noise(t, 1100, 12500) * (bursts * .14 + np.exp(-t * 18) * .17)
        if pad == 1:  # Tighter snare for the build.
            x = noise(t, 1800, 13000) * np.exp(-t * 25) * .26 + body
        else:
            x += body * .4
        x *= env(t, .0005, 0, duration)
        scale = (1, 1, .48, 1.12)[pad]
        return stereo(x * scale, np.roll(x, 31) * scale)
    if lane == 2:
        midi = (33, 36, 38, 40, 29, 31)[pad]
        f = 440 * 2 ** ((midi - 69) / 12)
        cutoff = 260 + 2500 * np.exp(-t * 20)
        mono = saw(t, f * 2, cutoff) * .37 + saw(t, f * 2, cutoff, .32, 1.004) * .12
        mono = np.tanh(mono * 1.5) * .45 + np.sin(2 * np.pi * f * t) * .42
        return stereo(mono * env(t, .0025, 6.8, duration, .04))
    if lane == 3:
        midi = (57, 60, 62, 64, 67, 69, 72, 76)[pad]
        f = 440 * 2 ** ((midi - 69) / 12)
        cutoff = 1500 + 8500 * np.exp(-t * 9)
        channels = []
        for side in (-1, 1):
            x = sum(saw(t, f, cutoff, p, 2 ** (c / 1200)) * level
                    for c, p, level in ((0, 0, .45), (side * 7, .19, .22),
                                        (-side * 13, .37, .17), (side * 19, .11, .12)))
            x += np.sin(2 * np.pi * f * 2 * t) * .08
            # A warm, controlled lead, with a small attack and shorter bounce release.
            channels.append(np.tanh(x * 1.45) * .44 * env(t, .003, 5.5, duration, .08))
        return stereo(*channels)
    chord = ((57, 60, 64, 69), (53, 57, 60, 65), (55, 60, 64, 67), (55, 59, 62, 67))[pad]
    channels = []
    for side in (-1, 1):
        x = np.zeros(len(t))
        for index, midi in enumerate(chord):
            f = 440 * 2 ** ((midi - 69) / 12)
            cutoff = 1200 + 4500 * np.exp(-t * 6)
            x += saw(t, f, cutoff, .1 * index, 2 ** (side * (5 + index) / 1200)) * .11
            x += saw(t, f, cutoff, .2, 2 ** (-side * 7 / 1200)) * .075
        channels.append(highpass(np.tanh(x), 180) * env(t, .008, 3.6, duration, .12) * .75)
    return stereo(*channels)


def note(at, pad, velocity=.8):
    return dict(at=at, pad=pad, velocity=velocity)


def patterns(variant=0):
    kick = [note(b, 0, .96 if b % 4 == 0 else .88) for b in range(16)]
    hats = [note(b + .5, 2 if b % 4 == 3 else 1, .72 if b % 2 else .60) for b in range(16)]
    hats += [note(b + .75, 1, .34) for b in (3, 7, 11, 15)]
    clap = [note(b, 0, .82 if b % 4 == 1 else .92) for b in range(1, 16, 2)]
    clap += [note(15.25, 2, .38), note(15.75, 1, .54)]
    bass, chords, melody = [], [], []
    roots = (0, 4, 1, 5)  # A1 / F1 / C2 / G1; previous pad IDs stay stable.
    # Original two-bar call/response, then a harmonic answer. No source melody.
    phrases = (
        ((.5, 5), (1.25, 3), (1.75, 5), (2.5, 6), (3.25, 5), (3.75, 3)),
        ((.5, 3), (1.25, 1), (1.75, 3), (2.5, 5), (3.25, 6), (3.75, 5)),
        ((.5, 6), (1.25, 4), (1.75, 3), (2.5, 4), (3.25, 6), (3.75, 7)),
        ((.5, 4), (1.25, 2), (1.75, 4), (2.5, 5), (3.25, 4), (3.75, 3)),
    )
    for bar, root in enumerate(roots):
        for offset, velocity in ((.5, .9), (1.25, .72), (1.75, .86), (2.5, .92), (3.25, .75), (3.75, .83)):
            bass.append(note(bar * 4 + offset, root, velocity))
        for offset in (.5, 2.5, 3.75):
            chords.append(note(bar * 4 + offset, bar, .65 if offset != 3.75 else .48))
        for index, (offset, pad) in enumerate(phrases[bar]):
            if variant and index in (1, 4):
                pad = (5, 3, 7, 5)[bar]
            melody.append(note(bar * 4 + offset, pad, (.90, .65, .78, .94, .72, .80)[index]))
    if variant:
        kick += [note(15.75, 3, .55)]
        hats += [note(14.75, 1, .4), note(15.25, 1, .5)]
        melody += [note(15.5, 2, .42)]
    return [kick + hats, clap, bass, melody, chords], kick, hats


def section(title, lanes, gains, loops=1):
    part = ("Breakdown" if title.startswith("Breakdown") else "BuildUp" if title.startswith("Build-up")
            else "Outro" if title.startswith("Outro") else "Drop")
    return dict(title=title, part=part, loops=loops, gains=gains, lanes=lanes)


def main():
    bank_dir = ASSETS / "audio/neon"
    bank_dir.mkdir(parents=True, exist_ok=True)
    measurements = []
    for lane, count in enumerate((6, 4, 6, 8, 4)):
        for pad in range(count):
            x = one_shot(lane, pad)
            x -= np.mean(x, axis=0)  # Tiny DC offset, then taper sample endpoints.
            x[:32] *= np.linspace(0, 1, 32)[:, None]
            x[-240:] *= np.linspace(1, 0, 240)[:, None]
            peak = np.max(np.abs(x))
            if peak > .97:
                x *= .97 / peak
            pcm = np.round(np.clip(x, -.99, .99) * 32767).astype("<i2")
            with wave.open(str(bank_dir / f"{lane}-{pad}.wav"), "wb") as wav:
                wav.setparams((2, 2, RATE, len(x), "NONE", "not compressed"))
                wav.writeframes(pcm.tobytes())
            measurements.append(dict(lane=lane, pad=pad, frames=len(x), peak=float(np.max(np.abs(x))),
                                     rms=float(np.sqrt(np.mean(x * x)))))
    base, kick, hats = patterns()
    changed, _, _ = patterns(1)
    breakdown = [[], [], [], [note(b * 4 + o, p, .48) for b, p in enumerate((5, 3, 6, 4))
                              for o in (0, 1.5, 3)], [note(b * 4, b, .75) for b in range(4)]]
    build = [hats + [note(8, 5, .9)], [note(i / 2, 1, .36 + .025 * i) for i in range(24)] +
                   [note(12 + i / 4, 1, .50 + .035 * i) for i in range(12)],
             base[2][:18], base[3][:18], base[4]]
    # Last beat has a deliberate gap; first kick of the next section lands cleanly.
    sections = [
        section("Drop 1 · A", base, [1, 1, 1, 1, .85]),
        section("Drop 1 · B", changed, [1, 1, 1, 1, .85]),
        section("Breakdown · A", breakdown, [0, 0, 0, .65, 1]),
        section("Breakdown · B", breakdown, [0, 0, 0, .8, 1]),
        section("Build-up", build, [.5, .85, .7, .8, .7]),
        section("Drop 2 · A", changed, [1, 1, 1, 1, .9]),
        section("Drop 2 · B", base, [1, 1, 1, .92, .9]),
        section("Drop 2 · C", changed, [1, 1, 1, 1, .9]),
        section("Drop 2 · D", base, [1, 1, 1, .92, .9]),
        section("Outro", [base[0], base[1], base[2], [], []], [1, .8, .8, 0, 0]),
    ]
    for index in (0, 5):
        sections[index]["lanes"] = [list(notes) for notes in sections[index]["lanes"]]
        sections[index]["lanes"][0] += [note(0, 4, .8)]
    score = dict(title="霓虹弹跳", subtitle="原创 Future Bounce", bpm=128, bars=4,
                 master=1.0, volumes=[.88, .70, .86, .76, .60],
                 steps=[dict(lane=0, notes=kick), dict(lane=0, notes=hats)] +
                       [dict(lane=i, notes=base[i]) for i in range(1, 5)],
                 sections=sections)
    HERE.mkdir(parents=True, exist_ok=True)
    text = json.dumps(score, ensure_ascii=False, indent=2) + "\n"
    (HERE / "score.json").write_text(text)
    (ASSETS / "music/neon-bounce.json").write_text(text)
    (HERE / "sound-bank-report.json").write_text(json.dumps(measurements, indent=2) + "\n")
    print("Generated original 128 BPM Future Bounce;", sum(len(s["notes"]) for s in score["steps"]),
          "recorded notes;", len(sections) * 4, "arrangement bars; 28 stereo one-shots")


if __name__ == "__main__":
    main()
