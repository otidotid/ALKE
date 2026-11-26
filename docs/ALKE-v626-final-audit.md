# ALKE v626 — finalization of approved v625

Baseline: released v625, main 0c65fd81bf7b17e701b5bdd289b5c9e201624dbc. Scope: evidence-led maintenance, no feature expansion or visual redesign.

## Source findings and fixes
- Me: remove unused activity-result registration and redundant profile revision. Avatar remains snapshot-backed and the real Profile route owns the photo picker.
- Me wearable link: observe only connected/pairing/not-connected transitions instead of each BLE telemetry update. Preserve labels and existing icon.
- Premium sparkline: cache normalized values outside the draw loop; same geometry, color and stroke.
- Profile: use existing Settings pattern for background photo decode/IO.
- Avatar decode: sample using longest edge, including panoramas and tall pictures; preserve EXIF and final 256px cap.
- Add focused regression tests. Bump versionCode to 626.

## Verification gates
Preserve v625 protected HR/All Sessions/Today surfaces, algorithms, database, BLE, backup and Worker. Run complete existing Personal Quality Gate before release. Verify package, F34C signature v2/v3 and checksums.

## Explicit limitations
No instrumented device baseline or comparable frame/memory/battery capture in this source pass. No measured performance claim or leak-free claim. Real-device BLE, Health Connect, Google Drive and production Cloudflare/Groq verification remain manual. Retain v625 user-approved demo smoke as design baseline; emulator skipped per owner preference.
