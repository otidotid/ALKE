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
