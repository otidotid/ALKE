# ALKE combined PR #13 + planned PR #14 — v610

**One update:** smart verified encrypted week cache + opt-in prefetch +
non-destructive online-only readiness gate. Source is signed v609 baseline.

## Smart encrypted cache
- Reuse only ciphertext from independently encrypted weekly previews.
- 8 cached weeks / 64 MiB ceiling. Android may evict cacheDir.
- Verify exact file length and SHA-256 on *every* read against the current
  month-bound manifest. If corrupt or stale, delete and re-download.
- In-process coroutine mutex guards concurrent fetch/prune/read.
- Decrypted JSON is always temporary and deleted after read, even after
  failure; no password or Drive token is stored in the cache.

## User-driven, conservative prefetch
- After a user successfully opens an authenticated weekly day preview,
  optionally prefetch at most one adjacent week in the same month.
- Requires validated UNMETERED connection; skips data-saver and battery-saver.
- The prefetch task belongs to the foreground UI coroutine scope, not a
  persistent WorkManager job; it downloads ciphertext ONLY, never decrypts it.
- If network/capability information is unavailable, no prefetch occurs.
- Prefetch failure is ignored; a later manual history read uses normal
  month fallback after trying the verified encrypted week.
- No change to existing Drive enrollment, backup or full-month archives.

## Verified online-only readiness — NEVER DELETE HERE
- A read-only gate enumerates full encrypted monthly archive coverage plus
  mandatory future proofs: **independent table-level restored count/hash
  parity, all device/source IDs, score equivalence, deep cross-module online
  reads, health and food backup recovery, and rollback drill**.
- Manifest metadata and SHA-256 of a ciphertext are NOT restore-parity
  evidence. PR #13/#14 shows a "not enabled" status without offering deletion
  controls or assuming absent proofs succeeded.
- Patch #15 can add actual restore audits, explicit user opt-in, reversible
  local offload and delete only when proof is independently produced and
  passes on physical data. No scheduled deletion in v610.

## Regression and release
- Pure JVM tests cover no redundant network fetch, corruption refetch,
  rejected tampering, LRU cap, adjacent-week prefetch and network policies,
  all proof gates fail closed.
- APK signing remains the exact ALKE Personal certificate, versionCode 610.
- Signed GitHub prerelease only after full Android Personal CI.
- User smoke test: install over v609; read the same archived day twice,
  observe intact history and monthly fallback, confirm no data disappears.

## User-requested connection policy — no hardcoded unmetered-only prefetch

Under **Me > Google Account > Google Drive > Prefetch connection**, the user
selects one of four persistent modes:
1. **Off** — disable all automatic prefetch, manual history still works.
2. **Wi-Fi non-metered only** (default for existing installs).
3. **All Wi-Fi (including tethering)** — Wi-Fi transport is permitted even if
   Android marks it as metered.
4. **Wi-Fi + mobile data** — permits metered cellular and metered Wi-Fi.

Connection eligibility derives from actual Android
`NetworkCapabilities.TRANSPORT_WIFI / TRANSPORT_CELLULAR`, rather than
assuming Wi-Fi means unmetered. Internet validation is required. Android
Data Saver and Battery Saver continue to suppress optional prefetch.
The setting affects prefetch only; full backup schedule and manual history
download keep their existing separate user controls. Selection persists
in private SharedPreferences and is read anew for each prefetch request.
