# Spatial Loop V3 implementation

User authorized implementation directly, including scene assembly without MCP-generated
models. This supersedes the old project's codegen/accepted-design contract for this
independent prototype. Do not edit the old project or invoke Editor generation workflows.

## Backup

Second generation is the existing `spatial-dj-demo`, latest V9.
Verified archive: `../workspace-backups/spatial-dj-second-version-2026-10-27/spatial-dj-second-version.tar.gz`.
All 2533 included files checked against SHA-256 manifest. Original source is unchanged.
Only regenerable dependency/cache/build directories were excluded.

## Product

Five independent tracks: Kick, Clap, Bass, Melody, Chord.
Select track, arm recording, play large pads with direct fingertip touch,
record one complete loop, automatically replay, select next track and add its part.
Shared BPM, 1/2/4 bars, optional sixteenth-note quantization, count-in, metronome,
individual volume/mute/undo/clear, save/load session and mix export.
Fixed A minor note/chord bank for easy harmonious layering. Fingertip pads and sliders
operate inside a native room with actual lights and four articulated dancing humans.

## Interaction and presentation

V3.1 replaces the failed native-pointer + independent fingertip gate with fingertip
sphere/plane contact on actual registered native Button/Slider faces. Official SDK design
guidance recommends HandTracking and collision for strict finger-only contact.
V3.3 converts only fresh, nonzero left/right INDEX_TIP joints from stage through the actual
attachment entity. The dispatch gate separately accepts only IDs 1/6, including hover.
Contact at 12 mm; lift threshold 20 mm; bounded swept crossing for fast taps. Capture per
finger, one strike per contact, one owner per slider. Native pointers are consumed and
native callbacks are empty, so rays/controllers/pinch cannot trigger or duplicate actions.
No palm/wrist/body joints are forwarded. Zero/invalid hand data, tracking loss, stale
samples, background/error, changed control faces and tab replacement release captures.
Only two index markers are created. Legacy per-contact HTTP/thread debug reporting is
removed from the touch path; callbacks run synchronously and the pressed flash is 45 ms.
Physical end-to-end fingertip/audio response still needs headset evidence.

One SpatialUI native panel mounted on a lightweight geometric console at reachable
distance. Wide continuous sliders replace knobs. V3.6 uses a shared offwhite/navy/teal/
amber/gray-blue palette, a waveform emblem, outlined raised keys and conventional speakers.
Large performance pads, compact lane tabs and an optional tool tray avoid crowding.
Visible fingertip dots and cyan/amber contact feedback. All interactive controls,
including transport/save/height, go through the fingertip contact path.
Use panel center alignment and measured onSizeChanged pixel-to-meter scale so validation
is in the same coordinate frame as current hands. Guard against stale tracking,
underside approach, suspended lifecycle, duplicate presses and contact loss.
Geometry stays behind the UI plane; no huge legend planes or white overlays.
Default console height is 1.30 m with fingertip +/-10cm buttons.

V3.2 uses a bundled, renamed OFL Noto Sans SC subset (Loop Sans SC), static weights
400/600 covering 28,124 Latin/CJK/punctuation codepoints per font. All panel Text goes
through LoopText with explicit family, weight, line metrics, zero letter spacing and no
Android font padding or synthetic bold. Native Chinese font metrics are taller than
Latin defaults; title, pad and tools layouts reserve their actual measured height.
Actions and track/drum labels are Chinese; standard note/chord names, BPM and WAV stay.
Transport icons use Canvas geometry. Main/detail button text has separate sizing,
centered alignment, and fixed padding; slider labels and right-aligned percentage fields
share baselines. Notices reserve two lines with ellipsis for long external errors.
Control IDs, track order, pad indices and binary .loop format remain compatible.
Debug builds record actual TextLayoutResult and coordinates; instrumentation checks
glyphs, font selection, clipping, panel/button containment, baselines and touch rectangles.

## Architecture

Keep Main.kt thin and PicoTheme/DefaultStage chain. Existing BOM6.1.9, JDK21.
New app id `com.example.spatialdj.loopv3`, keep Kotlin namespace/packages
`com.example.spatialdj.p170bd28c` inside separate APK for reused launch/audio scaffolding.
Rename app to Spatial Loop V3 and unique Stage id; both generations can stay installed.
Engine is pure Kotlin: bounded preallocated voices, sample-clock sequencer, immutable
snapshots, no per-sample allocations or file I/O inside render lock.
Model includes pending record/count-in/record/loop state, note bank, beat and event phase.
Loop boundaries and quantized event frame positions use the same master clock; handle
non-integral frame beat duration without accumulating rounded-buffer drift.
Track overdub uses working copy and atomic one-loop commit; cancel/undo preserve prior loop.
Manual pad audition must not double-play its recorded event in the same pass.
Stop/background/error releases notes and input. Preserve session on save/load; file I/O
off render lock. Validate session bounds, version, track/note/event counts, BPM and lengths.
AudioOutput interfaces + existing AndroidAudioOutputs spatial stereo split remain reusable.
Stop workers and release output bindings before destroying speaker entities.

Automatic performance reads `music/neon_bounce/score.json`, bundled as
`assets/music/neon-bounce.json`, once before readiness. AutoScore validates finite,
bounded notes, sixteenth-note grid, lane/pad order and aggregate 256-note lane limits.
Explicit Automatic action captures the prior project, then starts four-beat count-in
and six recording passes. LoopEngine schedules pad() on the existing sample clock and
commits each pass at the same loop boundary, including the second bottom-drum overdub.
UI follows the current lane, step and pad strike; the completed five-track loop then
performs the score's Drop / Breakdown / Build-up / Outro arrangement.
Stop/cancel and background restore the prior session; completed runs expose RestoreBeforeAuto
in tools. saved.loop is never written automatically. Prior project is also backed up to
before-auto.loop; all file I/O remains outside render lock.
Automatic timing and PCM are independent of render chunk size. Manual edits are gated
during the performance, while master/track volume, height, stop and exit remain available.
AudioTrack uses USAGE_GAME plus requested LOW_LATENCY, a 1536-frame active spatial buffer
(three 512-frame backend periods) and a 192-frame render batch per channel. A 384-frame
spatial buffer was rejected after measured continuous underruns. Spatial binding may prevent a FAST mode; inspect the
actual returned buffer size/performance mode and underruns, not just the requested flags.

V3.5 adds RecordingMode.DropOnly / FullSong. SongPart provides professional Intro,
Breakdown, Build-up, Drop and Outro labels. The original bars/lanes fields remain the Drop;
four independent SongSections hold the other parts. All sections have five tracks and
independent 1/2/4-bar lengths, volume/mute state and engine-local undo slots. The current
section is cached for rendering; no section lookup/allocation is added per audio sample.
Part/mode selection stops playback and discards only uncommitted overdubs. Part audition
loops locally; full playback visits each part once and stops after Outro.
Loop start is accumulated in beats and converted with ceil to sample time, so changing
part lengths and nonintegral BPM do not rebase the audio clock or accumulate rounding.

SessionFiles reads legacy v1 as Drop-only. v2 appends mode, selected part and the four
other sections to the original base payload, validates unique parts and note bounds,
and supports a maximum 131072-byte file (full 25-track capacity exceeds v1's 32768).
Saving Drop-only v2 also preserves hidden parts. Full WAV export includes every part once,
including silence for unrecorded parts; Drop-only export remains four loops. Before-auto
backups snapshot the whole project for every new run, and restore all sections and settings.
Manual modes are independent of the automatic score's changing playback arrangement.
The automatic score now uses Drop / Breakdown / Build-up / Outro names.

V3.6's ClubRoom owns four shared primitive meshes and cached PBR/unlit materials.
Six enclosing surfaces, a raised dance floor, acoustic panels, benches, light fixtures
and wall waveform leave the foreground console clear. Four stylized humans have faces,
hair/caps, jackets, shoes, and independent shoulder/elbow/leg/head articulation.
ClubMotion is pure Kotlin: alternating one-beat swing/one-beat support, forward-bending
two-bone leg IK, reachable targets and constant limb lengths. Legs stay in rig space;
torso sway does not rotate the support feet. ClubBeatClock accepts new audio snapshots
once and extrapolates at most 50ms; absolute elapsedBeats remains continuous across
parts of different lengths. The room shares the live scene's public ECS callback and
stops on background. Close releases entities before their global resources.
One directional fill and two moving spots illuminate actual PBR room/clothing surfaces.
Controls and accent strips remain unlit for readability. ClubMesh supplies a 44-triangle
beveled cube and 168-triangle smooth sphere with explicit outward winding/normals; all
characters and decorations share these bounded meshes. Materials explicitly enable back
culling/depth and disable default emissive output. Broad point washes and dynamic shadows
were removed after GPU_NO_RETIRE interruptions; actual lighting remains enabled. This is
a rendering cost reduction, not proof of the GPU driver's cause.
Manifest StageStyle.Full (3) replaces Mixed (1).

V3.7 replaces phase-offset flailing with four independent restrained eight-beat dance
styles. All use the same music beat: knee dip peaks on integer beats; foot travel occurs
in the preceding half-beat and feet land on the beat. Foot lifts are limited to 32mm,
shoulder spread remains below 30 degrees, and torso roll/twist remain below 5/8 degrees.
Two female characters have narrower jackets, ponytail/long hair and earrings.
Room wall/floor materials are dark; ceiling is a fully covering slab at 2.85m with
crossbeams and inset lighting. Entry door, exit pictogram, upholstered booths and bar
complete the interior. Directional fill is 32 lux; the SDK's supported neutral RGB11F
IBL with SH/prefiltered mips is used at -3 EV. Generic RGBA16F KTX was rejected by B3110;
the preparation script copies the known supported SDK texture and KTX is uncompressed.
Two 72-triangle open light shafts use additive opacity (2.5–5%), vertex fade, depth test
without depth writes, and end at the actual spotlight target on the dance floor.
PA stacks stand on their subwoofers near the rear side walls and face inward; direct-child
audio source entities now model desk monitors supported by a console rail, face the user,
and move vertically with the console height. Audio binding/lifetime remain unchanged.

V3.8 removes desk monitors and every monitor bracket/shelf/back rail. AudioOutputFactory
now binds the existing room PAMain-1 / PAMain1 entities; speaker ancestry is nested under
PAStack and ClubRoom, so instrumentation walks to LoopV3Root instead of assuming an
immediate parent. Audio stop/join/release still precedes the room's resource destruction.
Console ornaments (a small vinyl disc and waveform) have backing plates behind the UI
and their complete bounds stay at least 25mm outside the measured panel width.
ClubLighting centralizes dancer/light coordinates and coverage: one overhead spot per
dancer, inner/outer angles 14/22°, radius 3.8m, target sway limited to 25/30mm.
Tests cover head envelopes, torso and feet across all four dances; live tests
inspect actual transformed head corners and body/foot points in each spotlight cone.
Warm frontal fill is 130 lux, neutral environment IBL is -1 EV,
and three ceiling diffuser panels identify the general lighting source. The four visible
shafts remain depth-tested with no depth writes.

V3.9 raises global IBL from -1 to +1 EV and neutral fill from 130 to 260 lux.
Room/furniture albedo is lifted to mid charcoal blue, keeping corners readable without
adding more light components. ClubCast.WHITE_SKIN (#F9F6F0) is shared by all four people;
their actual spotlights use neutral white. Colored lenses/shafts remain accent geometry.
Live checks read all four skin material RGB values and verify neutral light color,
room finish values, environment/fill settings, existing cone coverage and lifecycle.

V3.10 uses two shared moving heads on the ceiling, sweeping opposing 16-beat elliptical
paths over the dance floor rather than assigning one spotlight to each person.
Targets remain inside the floor and the 4.6m light reach. Global IBL/fill and white skin
remain at the V3.9 settings. LoopUiState and LoopScene both start at 1.30m.
The old per-person cone assertions are replaced with sweep range/continuity, native
beam endpoint motion and measured initial height checks.

V3.11's newProject factory creates validated v2 FullSong projects with four bars in each
part and Intro selected. LoopEngine starts and resets to that factory; the legacy v1
constructor/read path and explicit automatic score length remain unchanged.
ControlOwners protects same-ID replacement instances during Compose disposal/registration.
FingertipGate.reset blocks held or near-contact tips, preserving a second already-retracted
hand. Page changes still require the touching hand to lift. During file busy/error states
PanelInput filters editing surfaces and releases captures, leaving tools/exit available;
HomeStage's input lifecycle no longer suspends navigation with the audio edit gate.
Automatic-button page reset is synchronous; later status updates cannot close the tools
page the user has opened. Global IBL is +2 EV and fill is 330 lux.

The user paused marker verification for V3.5. Temporary listener/scene timing reports,
Compose debug fields, collector and ADB reverse are removed. The functional ECS/listener
and visual predictor remain, but the strengthened live scene test is ignored after failure.
Standalone predictor unit tests do not verify physical or scene-level latency.

## Verification and delivery

V3.14 uses four unshadowed real lights: two opposing diagonal 800-lux fills and two
shared moving spots. The fills point down 40 degrees at yaw 45/225, reaching every wall
and the floor. IBL is +4 EV; wall/floor/ceiling albedo is 788793/596B78/627581.
The attempted four broad ceiling lights caused GPU_NO_RETIRE during playback on B3110
and are omitted from the final configuration. Room tests inspect actual fill directions.
AutoScore starts FullSong on Drop. Each playlist clip has a typed SongPart; snapshot
projects the playing part and lane counts while exportSession retains the committed
recording. Source and bundled score metadata must match. See revision-3.14.md.

Meaningful unit tests: five-layer recording/replay, quantization wrap, sample-clock drift,
tempo change restrictions, count-in, overdub/undo/mute/clear, save/load validation,
finger contact front/back/invalid/stale/loss tests, capture/hysteresis, fast repeated and
swept taps, multi-tip concurrency, slider ownership/endpoints, reset and replacement.
Real B3110 `PB331XPGL6040019G`: build/install independent app, scene/UI geometry loads,
sample output, spatial speakers, recording all layers and lifecycle; test-only access
must stay instrumentation scoped. Report actual Runtime/PxrMetric FPS while playing,
not application frame callback rate. No headset screenshots required.
Final APK and concise delivery record in this project's artifacts. Do not fabricate
subjective fingertip accuracy/music listening tests.

## Local references already checked

SDK vault `/Users/bytedance/Library/PICO/sdk/6.1/agent-vault/spatial`.
API `api-reference/com.pico.spatial.ui.foundation.gesture.md` (SpatialPointerInfo:
kind, pressed, x/y/z, uptimeMillis, pointerId, position3D, inputDevicePose).
API `api-reference/com.pico.spatial.ui.foundation.gesture.data.md`:
InteractionKind: Unknown, DirectPinch, Poke, GazePinch, RayBasedPinch, Pointer.
Docs `documentation/zh/6.1/spatial-sdk_交互_自定义空间交互事件.md` and
`documentation/zh/6.1/spatial-design_输入与交互_体感交互.md`.
Docs `documentation/en/6.1/spatial-sdk_content-layout-and-presentation_mount-a-2d-panel-onto-a-3d-entity.md`.
Native controls `com.pico.spatial.ui.design.Button/Slider/Text`; no Material3.
Use `AttachmentPanel(... alignment=CENTER)` under SpatialView. Entity has
TransformComponent by default. ModelEntity(MeshResource.createBox(size), UnlitMaterial).
Shared meshes/materials retained via Resource.toGlobal and closed after entity destruction.
Converter LocalPhysicalLengthConverter.dpToLength(dp, LengthUnit.Meters).
Existing old scene uses planes EulerAngles(-90f,0f,0f) on a horizontal desk.
Old TrackedHands polls HandTrackingProvider.latestData, skips identical samples for120ms,
then marks loss; all five fingertip joints converted via owner.convertPositionFrom(p,null).

CLI now globally installed 0.6.1 (previous broken-source assumptions are stale);
no need to upgrade/reset. Use existing Gradle/ADB baseline for this Kotlin prototype.
JDK `/Users/bytedance/.pico/primer-cli/jdk/jdk-21.0.12.1+1/Contents/Home`.
21GiB disk free before backup. No tool commands running at checkpoint.
