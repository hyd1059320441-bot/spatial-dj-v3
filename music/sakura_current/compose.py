#!/usr/bin/env python3
"""Original 128 BPM house music, synthesized locally. Dependency: numpy.

python3 compose.py
python3 compose.py --bpm 132 --play
Outputs: WAV, optional MP3 (ffmpeg), MIDI and a four-bar Spatial Loop .loop.
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import shutil
import struct
import subprocess
import wave

import numpy as np

SR = 48_000
PPQ = 480
SEED = 128_2026
TITLE = "Sakura Current"
SECTIONS = [
    (0, 8, "Intro"),
    (8, 16, "Groove"),
    (16, 24, "Build"),
    (24, 40, "Drop"),
    (40, 48, "Break"),
    (48, 60, "Final drop"),
    (60, 64, "Outro"),
]
CHORDS = [(57, 60, 64), (53, 57, 60), (48, 52, 55), (55, 59, 62)]
ROOTS = [33, 29, 36, 31]
# Deliberately singable two-bar hook; melody stays in A minor.
HOOK = [
    (0.00, 69, .375), (.75, 72, .25), (1.25, 76, .375),
    (2.00, 72, .25), (2.50, 69, .25), (3.00, 67, .25),
    (3.50, 64, .375), (4.00, 69, .375), (4.75, 72, .25),
    (5.25, 76, .375), (6.00, 72, .25), (6.50, 67, .25),
    (7.00, 69, .5),
]


def frequency(midi: int) -> float:
    return 440.0 * 2 ** ((midi - 69) / 12)


def envelope(t: np.ndarray, duration: float, attack=.006, release=.035):
    return np.minimum(t / attack, 1) * np.clip((duration - t) / release, 0, 1)


class Synth:
    def __init__(self):
        self.cache = {}
        self.rng = np.random.default_rng(SEED)

    def sound(self, name: str, midi: int, duration: float) -> np.ndarray:
        key = (name, midi, round(duration, 6))
        if key not in self.cache:
            self.cache[key] = self.make(name, midi, duration).astype(np.float32)
        return self.cache[key]

    def make(self, name, midi, duration):
        t = np.arange(round(duration * SR), dtype=np.float64) / SR
        end = envelope(t, duration)
        if name == "kick":
            # Continuous phase on the pitch drop; sub body plus a tiny attack click.
            phase = 2 * np.pi * (48 * t + 145 / 42 * (1 - np.exp(-42 * t)))
            body = np.sin(phase) * np.exp(-9.5 * t)
            click = self.rng.uniform(-1, 1, len(t)) * np.exp(-350 * t) * .10
            return np.tanh(1.35 * (body + click)) * end
        if name in ("hat", "open_hat", "clap", "snare", "riser"):
            noise = self.rng.normal(0, .45, len(t))
            # Differencing removes low-frequency energy from noise percussion.
            bright = np.diff(noise, prepend=noise[0]) * .60
            if name in ("hat", "open_hat"):
                decay = 68 if name == "hat" else 14
                metal = sum(np.sin(2 * np.pi * f * t) for f in (5423, 6731, 8917)) / 15
                return (bright + metal) * np.exp(-decay * t) * end
            if name == "riser":
                phase = 2 * np.pi * (400 * t + 90 * t * t)
                return (bright * .3 + np.sin(phase) * .13) * (t / duration) ** 1.6 * end
            if name == "clap":
                pulses = sum(np.where(t >= offset, np.exp(-np.maximum(t-offset, 0) * 135), 0)
                             for offset in (0, .012, .025, .039))
                tail = np.where(t >= .045, np.exp(-np.maximum(t-.045, 0) * 22), 0)
                return bright * (pulses * .38 + tail * .62) * end
            return (bright * np.exp(-21 * t) +
                    np.sin(2 * np.pi * 185 * t) * np.exp(-35 * t) * .30) * end
        f = frequency(midi)
        if name == "bass":
            # Mono sub and decaying band-limited harmonics; no discontinuous saw wave.
            signal = np.sin(2 * np.pi * f * t) * .77
            for harmonic in range(2, 12):
                signal += np.sin(2 * np.pi * f * harmonic * t) * (
                    .55 / harmonic * np.exp(-t * (4 + harmonic)))
            return np.tanh(signal * 1.2) * np.exp(-t * 3.8) * end
        if name == "lead":
            # Three slightly detuned voices, opposite stereo spreads.
            channels = []
            for side in (-1, 1):
                signal = np.zeros_like(t)
                for detune in (-7, 0, 7):
                    phase = 2 * np.pi * f * 2 ** ((detune + side * 1.5) / 1200) * t
                    for harmonic in range(1, min(17, int(SR * .43 / f))):
                        signal += np.sin(phase * harmonic + detune * .05) / (
                            harmonic ** 1.22) * np.exp(-t * (2.5 + harmonic * .6))
                channels.append(signal / 4 * envelope(t, duration, .008, .05))
            return np.stack(channels, axis=1)
        if name == "chord":
            channels = []
            for side in (-1, 1):
                phase = 2 * np.pi * f * 2 ** (side * 4 / 1200) * t
                signal = sum(np.sin(phase * k + .15 * side) / k ** 1.5 *
                             np.exp(-t * (1.3 + k * .4)) for k in range(1, 9))
                channels.append(signal * envelope(t, duration, .012, .08) * .37)
            return np.stack(channels, axis=1)
        raise ValueError(name)


def score():
    """(beat, instrument, MIDI note, duration in beats, gain, pan)."""
    events = []

    def add(beat, sound, midi=0, duration=.5, gain=.5, pan=0):
        events.append((float(beat), sound, midi, duration, gain, pan))

    for bar in range(64):
        beat = bar * 4
        intro = bar < 8
        build = 16 <= bar < 24
        drop = 24 <= bar < 40 or 48 <= bar < 60
        pause = 40 <= bar < 48
        outro = bar >= 60
        # Last two build bars leave breathing room before the kick returns.
        kick_on = (not pause or bar >= 46) and bar not in (22, 23, 63)
        if kick_on:
            for n in range(4):
                add(beat+n, "kick", duration=.85, gain=.78 if drop else .68)
        if not intro or bar >= 4:
            if not pause or bar >= 44:
                for n in (1, 3):
                    add(beat+n, "clap", duration=.5, gain=.48 if drop else .37)
        if not pause or bar >= 44:
            for n in range(8):
                if intro and n % 2 == 0:
                    continue
                # Tiny alternating accents; offbeats remain precisely on the grid.
                add(beat+n*.5, "hat", duration=.20, gain=.13 if n % 2 == 0 else .23,
                    pan=(-.22 if n % 2 == 0 else .22))
            if drop or 8 <= bar < 16:
                for n in (.5, 1.5, 2.5, 3.5):
                    add(beat+n, "open_hat", duration=.50, gain=.16, pan=.28)
        chord_index = bar % 4
        if 8 <= bar < 60 and not pause:
            # Offbeat bass rhythm with sixteenth-note pick-ups, changing with harmony.
            for n in (.5, 1.5, 2.5, 3.25, 3.5):
                add(beat+n, "bass", ROOTS[chord_index], .38 if n == 3.25 else .62,
                    .36 if drop else .29)
        if bar >= 4 and bar < 62:
            positions = (0, 1.5, 2.75) if drop or 8 <= bar < 16 else (0,)
            for n in positions:
                for note in CHORDS[chord_index]:
                    add(beat+n, "chord", note, .75 if len(positions)>1 else 3.4,
                        .27 if drop else .21)
        if build:
            # Roll doubles towards the drop; slight gain ramp avoids a harsh jump.
            step = 1 if bar < 20 else .5 if bar < 22 else .25 if bar == 22 else .125
            for n in np.arange(0, 4, step):
                add(beat+n, "snare", duration=.28,
                    gain=.10 + .22 * ((bar-16)*4+n)/32, pan=math.sin(n*3)*.12)
        if bar in (20, 46):
            add(beat, "riser", duration=16 if bar == 20 else 8, gain=.45)
    # The same hook anchors both drops; the final phrase adds an octave response.
    for first, last in ((12, 16), (24, 40), (40, 44), (48, 60)):
        for bar in range(first, last, 2):
            for offset, note, length in HOOK:
                quiet = 40 <= bar < 44
                add(bar*4+offset, "lead", note, length+.22,
                    .22 if quiet else .32, pan=0)
                if 56 <= bar < 60 and offset in (1.25, 5.25, 7.0):
                    add(bar*4+offset, "lead", note+12, length+.18, .12)
    return sorted(events)


def mix(events, bpm):
    seconds_per_beat = 60 / bpm
    frames = round((256 * seconds_per_beat + 2.0) * SR)
    drums = np.zeros((frames, 2), dtype=np.float32)
    bass = np.zeros_like(drums)
    harmony = np.zeros_like(drums)
    leads = np.zeros_like(drums)
    synth = Synth()
    for beat, name, midi, length, gain, pan in events:
        duration = length * seconds_per_beat
        if name in ("kick", "clap", "hat", "open_hat", "snare"):
            duration = {"kick": .40, "clap": .25, "hat": .08,
                        "open_hat": .24, "snare": .17}[name]
        sound = synth.sound(name, midi, duration)
        start = round(beat * seconds_per_beat * SR)
        count = min(len(sound), frames-start)
        if sound.ndim == 1:
            left = math.cos((pan+1)*np.pi/4)
            right = math.sin((pan+1)*np.pi/4)
            sound = sound[:count, None] * np.array([left, right], dtype=np.float32)
        else:
            sound = sound[:count]
        target = bass if name == "bass" else harmony if name == "chord" else (
            leads if name == "lead" else drums)
        target[start:start+count] += sound * gain
    # Kick-triggered ducking: no pumping in the drumless break.
    duck = np.ones(frames, dtype=np.float32)
    recovery = int(SR * seconds_per_beat * .80)
    t = np.arange(recovery) / SR
    shape = (1 - .78 * np.exp(-t/ .092)).astype(np.float32)
    for beat, name, *_ in events:
        if name != "kick":
            continue
        start = round(beat * seconds_per_beat * SR)
        count = min(recovery, frames-start)
        duck[start:start+count] = np.minimum(duck[start:start+count], shape[:count])
    bass *= duck[:, None]
    harmony *= (.25+.75*duck[:, None])
    leads *= (.40+.60*duck[:, None])
    # Stereo eighth/dotted-eighth delays, attenuated repeats, with a deliberate tail.
    for source, delay_beats, gain in ((leads, .75, .20), (harmony, .5, .13)):
        for repeat in range(1, 4):
            delay = round(delay_beats * repeat * seconds_per_beat * SR)
            drums[delay:] += source[:-delay, ::-1] * gain ** repeat
    mixed = drums + bass + harmony + leads
    mixed -= np.mean(mixed, axis=0)  # Remove DC offset before mastering.
    # Gentle saturation, followed by a peak ceiling; no hard-clipped integer samples.
    mixed = np.tanh(mixed * 1.25)
    fade_in = round(.025*SR)
    mixed[:fade_in] *= np.linspace(0, 1, fade_in, dtype=np.float32)[:, None]
    fade_out = round(2.0*SR)
    mixed[-fade_out:] *= np.linspace(1, 0, fade_out, dtype=np.float32)[:, None]
    peak = float(np.max(np.abs(mixed)))
    if peak == 0:
        raise RuntimeError("Empty composition")
    mixed *= 10 ** (-1.5/20) / peak
    return mixed


def write_wav(path, samples):
    pcm = np.round(np.clip(samples, -.999, .999) * 32767).astype("<i2")
    with wave.open(str(path), "wb") as out:
        out.setnchannels(2)
        out.setsampwidth(2)
        out.setframerate(SR)
        out.writeframes(pcm.tobytes())


def variable_length(number):
    result = [number & 127]
    number >>= 7
    while number:
        result.insert(0, (number & 127) | 128)
        number >>= 7
    return bytes(result)


def write_midi(path, events, bpm):
    def chunk(items):
        payload = bytearray()
        previous = 0
        for tick, data in sorted(items, key=lambda x: (x[0], x[1][0])):
            payload += variable_length(tick-previous) + data
            previous = tick
        payload += b"\0\xff\x2f\0"
        return b"MTrk"+struct.pack(">I", len(payload))+payload

    tempo = round(60_000_000/bpm).to_bytes(3, "big")
    conductor = [(0, b"\xff\x51\x03"+tempo), (0, b"\xff\x58\x04\x04\x02\x18\x08")]
    for start, _, name in SECTIONS:
        label = name.encode("ascii")
        conductor.append((start*4*PPQ, b"\xff\x06"+variable_length(len(label))+label))
    tracks = [chunk(conductor)]
    percussion = {"kick": 36, "clap": 39, "hat": 42, "open_hat": 46, "snare": 38}
    for lane, channel, program in (("drums", 9, 0), ("bass", 0, 38),
                                    ("chord", 1, 89), ("lead", 2, 81)):
        items = [(0, bytes([0xC0|channel, program]))] if lane != "drums" else []
        for beat, name, note, length, gain, _ in events:
            if (lane == "drums" and name not in percussion) or (
                    lane != "drums" and name != lane):
                continue
            note = percussion.get(name, note)
            begin = round(beat*PPQ)
            end = max(begin+1, round((beat+length)*PPQ))
            items.append((begin, bytes([0x90|channel, note, max(1, min(127, round(gain*220)))])))
            items.append((end, bytes([0x80|channel, note, 0])))
        tracks.append(chunk(items))
    path.write_bytes(b"MThd"+struct.pack(">IHHH", 6, 1, len(tracks), PPQ)+b"".join(tracks))


def write_loop(path, bpm):
    """Exactly SessionFiles.write's big-endian contract; use only the app's public pads.
    Its compact sound bank differs from this script's richer synths/arrangement.
    """
    lanes = [[] for _ in range(5)]
    for bar in range(4):
        base = bar*4
        for beat in range(4):
            lanes[0].append((base+beat, 0, .9))
        for n in range(8):
            lanes[0].append((base+n*.5, 1, .30 if n % 2 == 0 else .52))
        for beat in (.5, 2.5):
            lanes[0].append((base+beat, 2, .35))
        for beat in (1, 3):
            lanes[1].append((base+beat, 0, .82))
        # A / C / C / D stay inside the app's four bass pads and fit Am / F / C / G.
        for beat in (.5, 1.5, 2.5, 3.25, 3.5):
            lanes[2].append((base+beat, [0, 1, 1, 2][bar], .80))
        for beat in (0, 1.5, 2.75):
            lanes[4].append((base+beat, bar, .70))
    melody_pads = {57: 0, 60: 1, 62: 2, 64: 3, 67: 4, 69: 5, 72: 6, 76: 7}
    for base in (0, 8):
        for beat, note, _ in HOOK:
            lanes[3].append((base+beat, melody_pads[note], .77))
    payload = bytearray(struct.pack(">IIIIBf", 0x534C5633, 1, bpm, 4, 1, .78))
    for volume, events in zip((.80, .65, .76, .57, .49), lanes):
        payload += struct.pack(">fBI", volume, 0, len(events))
        for beat, pad, velocity in sorted(events):
            payload += struct.pack(">dIf", beat, pad, velocity)
    path.write_bytes(payload)
    return [len(lane) for lane in lanes]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bpm", type=int, default=128, help="60–180, default 128")
    parser.add_argument("--out", type=Path, default=Path(__file__).resolve().parent/"output")
    parser.add_argument("--play", action="store_true", help="play the WAV locally after rendering")
    args = parser.parse_args()
    if not 60 <= args.bpm <= 180:
        parser.error("--bpm must be between 60 and 180")
    args.out.mkdir(parents=True, exist_ok=True)
    events = score()
    print(f"Composing {TITLE}: {args.bpm} BPM, A minor, 64 bars…", flush=True)
    samples = mix(events, args.bpm)
    wav = args.out/"sakura-current.wav"
    write_wav(wav, samples)
    write_midi(args.out/"sakura-current.mid", events, args.bpm)
    counts = write_loop(args.out/"sakura-current.loop", args.bpm)
    summary = {
        "title": "樱花电流 / Sakura Current", "bpm": args.bpm, "key": "A minor",
        "sample_rate": SR, "channels": 2, "duration_seconds": len(samples)/SR,
        "events": len(events), "peak_dbfs": round(float(20*np.log10(np.max(np.abs(samples)))), 2),
        "rms_dbfs": round(float(20*np.log10(np.sqrt(np.mean(samples**2)))), 2),
        "sections": [{"name": name, "bar_start": start+1, "bars": end-start,
                      "seconds": round(start*4*60/args.bpm, 2)}
                     for start, end, name in SECTIONS],
        "loop_lane_events": counts,
        "loop_note": "4-bar five-track arrangement for Spatial Loop's existing sound bank",
    }
    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg:
        subprocess.run([ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
                        "-i", str(wav), "-codec:a", "libmp3lame", "-b:a", "256k",
                        "-metadata", f"title={TITLE}", str(args.out/"sakura-current.mp3")],
                       check=True)
    (args.out/"composition.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2)+"\n")
    print(json.dumps(summary, ensure_ascii=False, indent=2), flush=True)
    print(f"Music ready: {wav}", flush=True)
    if args.play:
        player = shutil.which("afplay") or shutil.which("ffplay")
        if not player:
            parser.error("No local player found; open the generated WAV manually")
        subprocess.run([player, str(wav)], check=True)


if __name__ == "__main__":
    main()
