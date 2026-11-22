# ALKE v606 — Me accordion consistency + smart encrypted cloud-read cache

Source baseline: ALKE v605 signed prerelease, versionCode 605.
Single v606 update includes both UI cleanup and bounded Stage 4 cloud-read improvement.

## Me UI (no data, algorithm, or navigation changes)

- More Tools and Settings now use the SAME Me accordion header with icon badge,
  typography, card radius, padding, expansion chevron and accessibility state.
- More Tools includes All Modules + Notifications, keeping existing callbacks.
- Settings continues to reveal the original, complete SettingsScreen body in
  the same Me scaffold; no second scroll host or duplicate Settings destination.
- The other ALKE expandable panels remain unchanged.

## Online archive read (Stage 4 incremental)

Previously `previewMonthOnline` removed the entire month's archive cache after
every preview and `openMonthOnline` evicted all other months. This could force
repeated full-month Google Drive downloads even when revisiting the same data.

- After successful SHA-256/size/schema/record-count checks, cache only the
  authenticated encrypted `YYYY-MM.alkeenc` blob in app-private cacheDir.
- ALWAYS delete decrypted `.alkearc` and extracted `.sqlite` after closing
  the preview handle. Failed verification still deletes the month's cache.
- Keep at most three recent encrypted months / 512 MiB, LRU by validated use.
  Android may evict cacheDir independently. Larger blobs are never retained.
- A stale blob is reverified against the current manifest SHA-256 every time,
  and downloaded again if an archive is replaced.
- Existing encryption, monthly archive schema, historical tier policy,
  backup/restore and every scoring algorithm remain unchanged.
- Offline-first UI for *all* historic screens, partial per-day downloads and
  verified local-row offload require separate implementation and verification.
  This v606 is a real read-cache improvement, not those unbuilt capabilities.

## Test gates
- Full Personal CI, new UI structural tests, encrypted-cache lifecycle/LRU
  tests, original signed Personal certificate, unchanged application ID.
- Owner will smoke-test Me > More Tools / Settings and read the SAME cloud
  archive month twice; second read should not fetch the blob again unless
  the cache was evicted or archive content changed.
