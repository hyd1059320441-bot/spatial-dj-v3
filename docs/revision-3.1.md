# V3.1 corrective revision

User: “我手势根本控制不了啊，还有整个风格太丑了，偏动漫一些”.
Authorized fix and visual overhaul, no approval pause.
Keep second-generation backup and V3.0 archived APK unchanged. Same project/applicationId.

## Touch implementation

Official SDK reference:
`/Users/bytedance/Library/PICO/sdk/6.1/agent-vault/spatial/documentation/zh/6.1/spatial-design_输入与交互_体感交互.md:31`
recommends HandTracking and collision for strict finger contact.
Replace dual native pointer + independent gate with deterministic fingertip sphere/plane
contact on actual registered native control rectangles, transformed into panel meters.
Do not recognize poses, pinch, wrist rotation, palm or body contacts.
SDK tips are stage meters; convert through the actual attachment entity, including its
runtime parent transform. Do not assume the SpatialView root is stage identity.
Filter missing/zero/invalid full hand data; use data provider availability and sample updates.
Use narrow contact thickness, front-side approach, hysteresis, swept crossing for fast taps,
one capture per finger, one down per contact; continuous horizontal slider moves.
Clamp slider values; release when leaving row/front, on lost tracking, lifecycle stop,
UI tab replacement, busy/output error or scene destruction. Avoid a pointer route dependency.
Update AGENTS to the corrected user-approved interaction contract; the earlier native-only
rule was implementation choice, not an additional user requirement.

Display small pastel fingertips/cyan near-contact markers at their actual tracked positions,
amber on contact; no debug jargon in product UI. All five fingers of both hands can be
geometrically tracked; do not claim proven ten-finger hardware feel.

## Anime arcade design

Cream ivory #FFF8ED, sakura pink #FF8CB9, mint #76E3CA, periwinkle #8FADFF,
butter yellow #FFD66C, ink outlines #393349. Opaque colors with readable dark ink.
An original “LOOP CLUB / 音乐游乐场” identity and small vector cat/headphone/stars/stickers.
Rounded chunky keys with offset ink shadows and colored raised faces; larger performance
pads as the focal point. Reduce crowded utility controls via an optional tools tray if helpful,
while keeping play/record/track select/volume/pads directly accessible.
Prefer tasteful layout: five compact color-coded lane tabs, a prominent beat strip,
a large selected instrument area and clear recording/count-in states.
No dark enterprise dashboard look or dense tiny text.

3D console: pastel rounded casing with dark contour shell, small accent strips, chunky feet.
Cute rounded twin speakers with ears and simple geometric facial/grille details.
No dancers, shadows/lights, imported assets, MCP generation or heavy background geometry.
Keep geometry behind panel and the currently accepted default 1.50m height controls.
Resource meshes/materials shared/retained; release audio before geometry.

## Validation and delivery

Meaningful pure contact tests and a real-stage instrumentation contact replay using the
actual measured panel rectangles and entity conversion, not merely direct VM actions.
Retain existing 28 sequencer/storage tests as relevant; replace obsolete Poke gate tests.
Build/install, real speaker/loop/export/lifecycle regression and actual PxrMetric playing FPS.
Preserve and label evidence: synthetic geometry contact proves pipeline; final subjective
finger operation still needs headset user evidence. Do not take headset screenshots.
Final APK named SpatialLoop-v3.1.apk in a new project artifacts subfolder, delivery.md.
Debug collection should remain available for subsequent real finger confirmation per
TRAE-debugger; first business change was instrumentation only. Never stop at logging alone.
