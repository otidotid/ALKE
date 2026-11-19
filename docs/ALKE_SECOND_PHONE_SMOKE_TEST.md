# ALKE second-phone smoke test — stages 1–4

## Scope
- **No personal data**. Clean-install the standalone ALKE debug APK on the second Android phone.
- Do **not** restore THOOP backups, pair the user's WHOOP strap, import Health Connect data, use real lab entries, or sign in to existing cloud health accounts.
- THOOP on the first phone and all its personal records remain untouched. There is no cross-device synchronization in this smoke test.
- For UI screenshot checks, use empty states or separately generated synthetic fixtures only, clearly labeled as synthetic.

## Smoke checks (after a passing build)
1. Confirm Android installs ALKE as a separate package (`com.otidotid.alke.debug` for `fullDebug`) and no THOOP installation is overwritten.
2. Confirm launcher displays **ALKE**; verify approved flat **Option D** icon once the actual artwork is integrated.
3. Open the application from a clean state; confirm it does not crash and provides useful empty-data states.
4. Verify Today, Sleep, Workouts, Trends, Health, VO2/Body Age details, AI/Coach, Settings and available navigation routes.
5. Inspect typography, contrast, touch targets, card visual hierarchy and dark navy/cyan/gold aesthetic; avoid gloss/glow-heavy UI.
6. Verify existing local algorithms do not crash on missing inputs, and missing metrics are represented as missing/unknown, not zero or fabricated scores.
7. Confirm required permissions are optional and consented; test deny and allow flows without enabling data sync.
8. Check that no obsolete WHOOP employee questionnaire / offline-only introduction interrupts onboarding after that redesign patch has landed.
9. Verify compact, medium and large ALKE Android widgets once integrated, including empty-state display and resize behavior.
10. Capture crashes/logcat and screenshots without personal data.

## Deferred to Stage 5 — real migration
- THOOP encrypted backup / Google Drive appDataFolder access across Android application IDs.
- Import existing real health history, food/lab data, backup restore, device pairing and algorithm/score parity.
- Only after safe backup, explicit authorization and verification on the second phone.

## Release safety
A debug APK is not a validated production build. Do not install ALKE over THOOP, change the THOOP repository, or publish a production release from this checklist.
