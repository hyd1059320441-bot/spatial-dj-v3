# Debug Session: index-marker-lag

- Status: [DEFERRED] — user explicitly paused fingertip verification on 2026-10-28.
- Issue: Real index fingers visibly lead the drawn fingertip spheres during movement.
- Scope: spatial-dj-v3 only; keep 1.20 m height, index-only activation, existing save files.
- Debug Server: stopped; timing probes and ADB reverse removed.
- Log File: .dbg/trae-debug-log-index-marker-lag.ndjson

## Hypotheses

| ID | Hypothesis | Observation |
| --- | --- | --- |
| A | HandTrackingProvider latestData is older than the visible native hand pose | Timestamp availability, update cadence, repeated sample age |
| B | Compose withFrameNanos dispatch reaches the 3D scene late | Compose frame timestamp vs callback time and ECS update cadence |
| C | Coordinate conversion/marker transforms add measurable processing latency | Poll/convert/marker update durations and transform round trips |

## Constraints

Evidence before fixing tracking logic. The first existing-code edit is instrumentation.
Collect bounded, low-frequency reports away from input/audio hot paths; do not resurrect
per-touch network/thread reporting. No headset screenshots or automatic bug filing.
The user already authorized implementation and device optimization, so continue independent
music work and automated evidence collection without another approval checkpoint.

## Pre-fix evidence

107 A reports: Compose poll interval median 11 ms; dispatch median 0.478348 ms,
range 0.376182–7.825382 ms. 108 B reports: ECS update on main at about 11 ms.
C initial conversion/contact/marker processing 0.579375 ms, zero tips.
All reported samples have timestamp=0, leftPresent=false, rightPresent=false.
Device is online but no tracked hands were present during this capture.
Therefore A remains unverified; cannot claim an absolute sensor capture-age measurement.
B shows updates are tied to the Compose frame clock independently of the scene tick;
C does not support a large coordinate conversion delay with the current zero-tip evidence.

SDK public DataProvider.addListener supplies fresh data on a worker callback; entities
must be updated on Main. SDK HandTrackingData.timestamp is internal: use reflection only
for debug evidence, never production input. Public hand target anchors/predicted timestamp
queries are absent from this BOM. Plan: listener snapshot with monotonic receive time,
scene-synchronous update, visual-only bounded prediction, raw coordinates for activation.

## Verification

V3.4 implementation: public provider listener copies two position values into a volatile
lifetime mailbox, then scene-bound ECS System updates contact and markers on main.
Visual prediction is capped to 18 ms / 12 mm, rejects jumps/reversal/discontinuity,
stops predicting after 25 ms and hides after 50 ms. Contact uses raw position.
The user paused validation; all temporary network probes and Compose timing fields were
removed. Functional listener/ECS/predictor changes remain. This is not a confirmed fix.

The earlier scene injection test had an insufficient displacement assertion: its passing
value (z=.018999944 for raw z=.019) was transform floating-point roundoff, not proof of
prediction. The strengthened >2 mm assertion FAILED. A one-shot injected sample aged
106 ms before ECS consumption in the failure evidence, exceeding the 50 ms freshness
window. No further fingertip device tests were run after the user's pause. The scene
test is explicitly ignored with this reason. Standalone predictor boundary tests are
algorithm checks only; physical tracking latency and live scene prediction remain unverified.

Ordinary app launch collected 120 reports, with 79 fresh received samples after 2 s warmup.
Scene interval median 11 ms, p95 13 ms, max 209 ms.
Receiver-to-scene age median 1.272084 ms, p95 11.008906 ms, max 21.820625 ms.
After tracking stopped, mailbox retained its last pose but age exceeded the 50 ms cutoff;
the implementation hides the sphere and releases contact. Stale mailbox presence is
not evidence that a hand is still physically tracked.

Evidence files: .dbg/v3.4-normal-timing.json, .dbg/v3.4-normal-timing-summary.json,
.dbg/v3.4-marker-device-test.log, .dbg/v3.4-marker-scene-report.json,
.dbg/v3.4-final-device-tests.log, .dbg/v3.4-automatic-report.json.

Physical finger-to-display latency still requires headset feedback; receiver timing,
scene injection and the absence of audio underruns do not prove subjective lag disappeared.
Do not claim native sensor latency measured, or frame hitches eliminated.
