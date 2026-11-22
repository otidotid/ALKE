# ALKE PR #10 — v607 Smart Daily Snapshot

Based on merged PR #9/v606 (approved by owner on 2026-11-22).

## Actual change
- Keep original 15–21-day compact count snapshot + the 22+ day
  encrypted Google Drive archive policy intact.
- New independent, GZIP/CRC protected **full fidelity DailyMetric
  presentation snapshot** in app-private storage. Persist **all fields**,
  device/source identity, nulls and exact Double bits, and the complete
  merged multi-year history (up to a defended 20,000 days). Atomic file
  replacement and 8 MiB cap; unreadable, source-mismatched and 12h-old
  caches fail closed to the canonical Room read.
- The daily background snapshot worker refreshes the cache using exactly
  `WhoopRepository.daysMerged(activeId)` (existing import/computed/edit
  precedence) and the same non-overlapping background I/O gate.
- Trends and Trends Explore now show cached history quickly while loading
  the authoritative repository result. The authoritative DB result always
  replaces the cache; refreshed cache is written on IO dispatcher.
  Recent trend points still come from existing reactive flows.
- No `DailyMetric`, scorer, work profile, backup, cloud read or database
  schema changed. No raw BLE row eviction, no forced Drive migration.
- This is **not** yet the Stage 5 unified remote data router; online-only
  history purge remains forbidden.

## Test gates
- Entire 3,000-day imported history roundtrip; no hidden truncation.
- Exact 21 nullable DailyMetric scalars including exact Double bits and
  optional user/WHOOP fields.
- Source isolation, 12h expiry and corruption fail closed.
- All existing Android Personal unit tests and APK assembly.
- Original Personal certificate with v607 APK update in-place over v606.

## Risk/limitations
- Re-reading the authoritative history still costs database IO; the
  cache improves first render responsiveness, not a benchmarked total
  CPU reduction. Never consume the cached rows as scores or backup data.
