# ALKE v605 — Stage 3 background efficiency / data safety

**Baseline:** approved v604 smoke test, source `alke/alpha20-insight-cleanup`
after PR #7 was merged. The existing 14-day local detail, 15–21 day
compact-read and encrypted Drive archive policies remain unchanged.

## Confirmed I/O contention risk

Three independent flows accessed the same Room/WAL and temporary disk space:
`CompactDailySnapshots.refresh` (daily / catchup / manual),
`GoogleDriveArchiveManager.createAndVerifyArchives` (verified monthly encrypted
copy, auto + manual), and `GoogleDriveBackupCoordinator.backUpNow` (verified
full health/food backup, auto + manual). WorkManager's unique-work names did not
prevent these distinct kinds of work from running simultaneously.

Stage 3 introduces one **process-wide, cancellable Mutex**, acquired only while
each complete heavy operation runs. This serializes disk-heavy jobs and Drive
transport without changing user schedules, deleting local records, replaying
BLE data, or changing any scoring formula. Failures/cancellation always release
the permit. Restore/preview and foreground score reading remain unrestricted.

## Regression gates

- The original full Android Personal test suite remains mandatory.
- Tests verify no overlap, cancellation and failure permit release.
- The BLE drain additionally verifies lossless 50,001-entry synthetic FIFO
  and clean offload reset; this is **not** physical WHOOP or battery profiling.
- Personal signed APK must use the **same certificate** as v604 and be installable
  over it without uninstall, verified by APK Signature v2/v3 and matching SHA-256.
- No retention cleanup is enabled. Full local offload requires verified
  round-trip records, historic reads and a rollback strategy before deletion.

**Limitations:** WorkManager currently runs in one app process; a future
multi-process worker setup would require an inter-process filesystem lock.
A physical power/thermal benchmark and deep-backlog on-device testing remain
optional follow-up evidence, not claims made by this change.

## Weekly analytics — avoid rescanning the entire history per week

The historical weekly Fitness Age/Vitality backfill built a map of all daily
metrics, but scanned and sorted **every day again for every missing Saturday**,
in both the local-only and labs-enriched paths. The index now uses
`TreeMap` and one seven-day inclusive `subMap` per week. The original
`associateBy` last-wins behavior for duplicate days, ascending order,
Saturday anchors, captured lab inputs, values and scorer calls are preserved.
The boundary/equivalence regression tests cover gaps, duplicates and
the empty/reversed window. No score values or daily history rows are modified.

## CI regression found on Sunday — manual Fitness Age refresh

The full Personal test gate on 22 Nov 2026 (Sunday) found **8 failures**
in `MetabolicEstimatePersistenceTest`, all from an existing
`recomputeFitnessAgeOnly` path returning `true` when no `fitness_age`
was written. Previously the routine only upserted results when today was
Saturday; tests running the day after Saturday exposed the false-success
branch. On weekdays its seven-day inputs also included post-Saturday rows.

**Fix:** explicitly anchor the score inputs at the last completed Saturday,
using that Saturday's original seven-day filter/sort/takeLast logic. Persist
and attach provenance whenever a nonempty weekly result exists, including
Sunday–Friday. Do not use the newer weekday data to change a prior Saturday's
score. All Fitness Age/AI Labs formulas remain untouched.

Regression: Saturday boundary plus Sunday/Monday exclusion in
`WeeklyFitnessAgeWindowTest`, and all eight real Room + AI metabolic
persistence tests in the full gate. Old CI run 44980031775 is **not valid
for release**; a subsequent green full run on this corrective commit is required.
