# ALKE alpha.2 — UI review contract

## Identity and design (approved proposal)
- ALKE Option 2 logo, deep navy, cyan and gold; premium, understated UI.
- Default dock: Today · Train · Progress · Insight · Me. Today/Me are fixed.
- Me > Customize Dock: up to three configurable module pins, reorderable and persisted.
- Today remains a concise readiness-focused surface; detailed metrics stay accessible.
- Today > Heart Rate Timeline opens the original time-aligned HR chart. Sport icons remain on the HR graph (at workout peaks), with workout intervals and sleep markers. The daily Strain chart is a separate metric.
- No BLE, recovery algorithm, workout-data model, database schema or user settings key migrations are part of this UI patch.

## Backup and personal data
- New folder backups are `alke-backup-YYYYMMDD-HHmmss.alkebak`.
- The prior `.noopbak` ZIP/SQLite payload structure is read for backward compatibility.
- Historical `.noopbak` files remain selectable for restore and are excluded from ALKE automatic prune.
- Import overwrites the target ALKE app database only after the app's restore confirmation; export is a distinct action.
- The format remains unencrypted unless the user chooses an explicitly encrypted backup route. Do not place personal archives in GitHub releases, issues, or CI.
- This source change does not itself restore a personal backup: no personal backup file was supplied.

## Review APK variants
- Full debug APK: `com.otidotid.alke.debug`; no seeded data. Restore from your own backup locally via Me > Backup & Restore, or pair/sync a wearable.
- Demo debug APK: `com.otidotid.alke.demo.debug`; only synthetic records are seeded by the existing DemoSeeder. Clearly labeled `ALKE Demo`; do not mistake the numbers for measured health data.
- Both are review builds, not stable releases, and use CI debug signatures. Uninstalling either test package deletes its data. Do not uninstall THOOP to test ALKE.
- The original NOOP copyright and source license notices remain in the repository. The product UI is ALKE; class/package names and preference keys remain unchanged for compatibility.

## Validation gates
- GitHub Actions assembles both debug variants, runs full-flavor unit/parity tests using frozen read-only THOOP fixtures, checks artifact integrity, then publishes the alpha.2 prerelease with both APKs.
- Manual physical-device validation remains necessary for Bluetooth, local restore, and visual parity against the proposal.
