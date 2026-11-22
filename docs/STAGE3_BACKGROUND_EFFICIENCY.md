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

## Sunday CI regression — uphold the Saturday-only publication contract

The complete Personal suite on 22 Nov 2026 found eight
`MetabolicEstimatePersistenceTest` fixture failures: their test clock
followed real Sunday even though the deliberate Fitness Age contract publishes
only on Saturdays. A first attempted change to publish on weekdays was rejected
by `FitnessAgeWeeklyPublicationContractTest` and is **not** part of v605.

The final correction preserves `newestDay == weeklyDay` as the **only**
publication gate and the existing week/day input logic. The production clock
still defaults to current time; a clock parameter lets persistence tests use
their deterministic fixture Saturday and removes dependence on CI day-of-week.
When a weekday manual recompute cannot publish, its Boolean result now returns
`false`, instead of falsely reporting an update. It does **not** rewrite
last Saturday's stored score. No Fitness Age, Vitality or AI Labs formula changed.

Regression: all prior AI metabolic persistence cases on a simulated Saturday,
plus a new Sunday-no-publish/no-false-success case, and the existing
Saturday-only contract test. Prior red CI runs 44980031775 and 44980879395
must **not** be used for release; a third green full run is mandatory.

## CI revision: service test clock must match the weekly fixture

A follow-up full run found one remaining failure in
`disabledReviewReplacesAStaleAiScoreAndReportsTheActualLocalResult`: the
production `MetabolicReviewService` created its own current clock (Sunday),
while the fixture's expected persisted result was on Saturday.
The service now passes a default production clock to the engine and permits
a deterministic Saturday override in this specific persistence test.
All normal calls use the same real wall clock as before; the Saturday-only
publication gate and score formulas are unchanged. A fourth full Personal
CI success is required before release.
