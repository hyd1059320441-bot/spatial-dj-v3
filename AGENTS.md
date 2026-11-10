# Spatial Loop V3

Independent third-generation fingertip looper, assembled directly with public Spatial SDK
APIs. User explicitly authorized direct geometry assembly without MCP-generated models.
Do not edit `../spatial-dj-demo` or its accepted Editor/codegen contracts.

- Kotlin namespace `com.example.spatialdj.p170bd28c`; app id `com.example.spatialdj.loopv3`.
- Spatial SDK BOM 6.1.9; JDK 21; ARM64 Android 35+. Main.kt keeps PicoTheme/DefaultStage.
- Only fresh left/right INDEX_TIP joints (IDs 1/6) contacting measured panel control faces may perform UI
  actions. Consume native pointer events without triggering actions. No pinch, rotary
  knobs, body/palm/wrist contact or heuristic gesture recognition. Convert stage tips
  through the actual attachment entity; reject zero poses before conversion.
- Five tracks use a shared sample clock. No per-sample allocation, I/O or unbounded voices.
- Manual recording has Drop-only and FullSong modes. FullSong stores independent
  Intro/Breakdown/Build-up/Drop/Outro parts, per-part bars/lanes/undo, ordered playback
  and full WAV export. Switching modes must preserve committed parts. Read v1 .loop as
  Drop-only; v2 stores all parts, including parts hidden while in Drop-only mode.
- Default console height is 1.10 m. Built-in automatic performance is an explicit button action,
  schedules the original Neon Bounce recording passes and changing sections on the audio sample clock, and can restore
  the prior project. It must not overwrite saved.loop automatically.
- Use actual public SDK APIs; do not claim ten-finger concurrency or physical accuracy
  without headset evidence.
- Stop/join audio and release spatial audio bindings before destroying speaker entities.
- Keep source, tests, docs and delivery artifacts inside this project.
- No headset screenshot verification, automatic MeeGo filing or unrelated cleanup.
- User paused fingertip verification. Keep functional listener/ECS changes but do not run
  marker latency/prediction device validation or claim it passed. The strengthened
  scene injection assertion remains ignored, documented in docs/index-marker-verification-deferred.md.
- Latest user authorizes an enclosing room, actual lighting and dancing human characters.
  Use a coherent neutral arcade style shared with the pads; reduce pink/cat/blush styling.
  Native public SDK geometry/PBR/lights/articulated animation is authorized. Earlier
  product descriptions excluding rooms/lights/characters are superseded. Keep independent
  recording modes, default height and input rules. No screenshot or fingertip validation.
- V3.7 feedback: use four distinct restrained nightclub dances on the same beat (no
  character time offsets or overhead flailing), add female characters with light skin.
  Make the room visibly enclosed and dim, with a lower finished ceiling, an entrance,
  lounge seating, visible light shafts and correctly aimed PA stacks/desk monitors.
  Keep real spot lighting and a bounded mesh/light budget; V3.6 high-load prototypes
  caused GPU_NO_RETIRE. Do not run screenshot or fingertip validation.
- V3.8 feedback supersedes desk monitors and the very dark V3.7 lighting: add softer
  general light, preserve visible spots and cover front/rear dancers. Remove all desk
  speakers, brackets and rails; route audio to the existing room PA. Side ornaments
  must stay outside measured controls and their mounting bodies behind the panel.
- V3.9 feedback: corners must be readable. Increase general environment/fill light and
  lift room/furniture albedo, rather than only brightening the dance floor. All four
  skin materials are white; use neutral illumination on people to avoid strong skin tint.
- V3.10: use two shared moving spotlights sweeping the dance floor, not one light per
  person. General illumination keeps all people visible. Default console is raised
  another 10cm to 1.30m; both the scene and UI state must use the same initial height.
- V3.11: new projects start in FullSong with four bars in all five parts; preserve
  existing loaded file lengths/modes and the automatic score's explicit length.
  Improve tools navigation, including during file busy/error states. Targeted button
  regression is authorized; the separate marker prediction/physical latency check stays paused.
- V3.12: keep audio timing and per-control feedback out of the whole console's composition.
  A continuous 16mm shallow lift outside the original button may rearm another button;
  same-button repetition, loss/staleness, underside and sliders retain stronger protection.
  Native geometry is at least 40mm behind the attachment plane. Layout/geometry tests do
  not certify the absence of the user's gray-blue rectangle or missing glyph regions.
- V3.13 user confirms the interaction fix. Default height is lowered 20cm to 1.10m,
  new-project and automatic master volume are 100%, software gain is 1.6 before the
  existing soft limiter, ambient light is +3 EV and general fill is 480 lux.
  Temporary pad-contact debugging counters/history/collection are removed after confirmation.
- V3.14: use two opposing diagonal 800-lux directional fills (pitch -40, yaw 45/225),
  two moving spots and +4 EV IBL. Four actual lights, no shadow maps; lift wall/floor/
  ceiling albedo to 788793/596B78/627581. Expanded local-light configurations triggered
  GPU_NO_RETIRE on B3110; do not restore those without sustained device evidence.
  Automatic performance
  must remain FullSong from count-in onward. Record the original passes into Drop, then
  show the playing clip's typed SongPart and lane counts without mutating the committed
  project. Clip metadata must stay identical in the bundled score and source score.

Build and JVM tests:

```sh
env JAVA_HOME=/Users/bytedance/.pico/primer-cli/jdk/jdk-21.0.12.1+1/Contents/Home ./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Device integration: `:app:connectedDebugAndroidTest`; it installs/uninstalls the target
package as part of the test lifecycle. Reinstall the final APK afterward.
Instrumentation replays fingertip contact through measured live controls and checks audio;
it does not certify subjective human touch response.
