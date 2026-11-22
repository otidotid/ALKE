# ALKE PR #11 — v608 unified history reader and Daily Outlook balance

## Unified history read
- A one-day indexed `WhoopRepository.dayMerged` read uses exactly the
  canonical multi-strap imported/computed/user-edited-sleep/steps merge.
- A single read path prioritizes **local authoritative dailyMetric**,
  then a valid, source-tagged, unexpired **Smart Daily Snapshot**,
  then offers **verified encrypted Google Drive** only if both local
  options are missing and an eligible registered archive covers the date.
- The existing Google account history browser uses this shared reader, rather
  than demanding cloud auth for a day still available locally.
- For encrypted archives, the existing recovery passphrase prompt remains.
  Downloads, decryption, archive verification and cleanup remain handled by
  the existing GoogleDriveArchiveManager. No passphrase or OAuth token stored.
- A standalone local-day preview shows DailyMetric-derived scores and source.
  Full detailed Sleep/Workout/HR data are unchanged in their existing modules.
- Partial download, full cross-module routing, and local data offload are
  separate work and are NOT falsely claimed as complete here.

## UI: owner-requested Effort + Rest horizontal Daily Outlook tiles
- In the compact daily outlook hero, **EFFORT | value** and **REST | value**
  use a row: uppercase title and Activity/Sleep sublabel on the left,
  large number aligned to the right; keep 72dp tile minimum height.
- Both tiles still route to their original detail actions.
- Existing stored scores, conversion scale, color and charge gauge unchanged.

## Safety
No scoring, snapshot serialization, encrypted Drive archive format, backup,
database schema, historical retention, or deletion behaviour changes.
`UnifiedHistoryRead.CloudRequired` means "needs verified remote read" only,
never silently restores or deletes local data.

## Validation
Personal debug compile/targeted tests and full unit suite, same original
personal signing certificate as v607, APK v2/v3 signatures, in-place upgrade.
Smoke test both Daily Outlook cards and old/new history days.
