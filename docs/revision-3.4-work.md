# V3.4 implementation working notes

Archived working notes. Music implementation is complete; manual arrangement is in V3.5.
The user paused fingertip verification. Network instrumentation/server/reverse are removed;
the strengthened live marker prediction assertion failed and is explicitly ignored.
See index-marker-verification-deferred.md for corrected evidence.

User: previous music sounds bad; replace with Future Bounce, aiming at professional
festival production. Actual index finger leads rendered marker sphere; optimize latency.
Chinese communication. Direct authorized build/install; no screenshot, approval or Meego.
Preserve 1.20 m, only index IDs 1/6, cream/pink/mint UI and bundled Chinese font.

## Music implementation direction

Original 128 BPM A minor Future Bounce: Neon Bounce / 霓虹弹跳.
Replace existing auto song and weak production sound bank. Preserve old pad indices;
append F1/G1 bass pads to support Am/F/C/G roots, leave existing melody and chord ordering.
Generate deterministic 48 kHz stereo one-shots offline with NumPy and bundle assets:
layered punchy kick, crisp clap/hats, resonant saw/sub bounce bass, detuned lead/pluck,
wide chord stabs. Band-limit harmonics, envelope/EQ, no sample aliasing.
Load assets once when VM prepares engine; never file I/O or synthesis inside audio callback.
Production and offline master must use the same generated bank.
Add sample-clock kick sidechain envelope and bounded/preallocated stereo delay/reverb
mixing so playback and WAV export share the improved actual sound, not just a preview.
AutoScore uses new original notes with velocity accents and sixteenth syncopation;
six-pass recording/overdub remains real pad() recording. Need full evolving performance
arrangement (drops, breakdown, buildup, final drop/outro) after recording rather than a
static four-bar loop. Keep manual .loop compatibility; sections are score metadata.
Produce standalone master WAV/MP3, original score and evidence in music/neon_bounce.
No promise/certification of DJ Mag ranking or equivalence; deliver best concrete music.

## Tracking direction

Pre-fix evidence in debug-index-marker-lag.md and .dbg NDJSON.
Use public HandTrackingProvider.addListener (off-main) for volatile latest snapshot and
monotonic receive time; no UI/entity mutation in callback. Read latest snapshot and update
marker once in ECS System update on main. Lifetime bind/unbind per LoopScene, no global
leaks or feedback into touch gate. Bounded visual-only prediction uses two fresh stage
positions, short horizon, displacement cap, reject stale/teleport/reversal.
Contact always uses raw measured position; do not let prediction trigger early pads.
May expose fingertip guidance toggle if useful; keep Chinese layout overflow check.
Debug instrumentation remains low-frequency while OPEN, no per-contact network logging.
