# ALKE Patch 15 — Verified online-only storage / v611 acceptance gates

Status: **Safety foundation / draft. Actual source-data offload NOT enabled.**

Parent: merged ALKE v610 / PR #13.
This is a deploy/QA checklist, **not** a claim that these conditions
have passed on the owner's phone.

## Target behavior
- Ages 0–14 days: source rows stay local.
- Ages 15–21 days: compact snapshot presentation; original source preserved until safe.
- Ages 22+ days: encrypted Drive read-back; eventually eligible for reversible, user-approved offload.
- All scores, modules, BLE, source identities and food records continue to work.
- Offline, expired authorization, tampered archive or failed rollback must not fabricate empty history.
- No automatic destructive cleanup after an app update.

## Observed blockers in v610 source
1. Rolling monthly encrypted archive contains the HEALTH database only.
   The separate FOOD database exists in complete recovery backups, not monthly archives.
2. Online day previews are summaries, not full data hydration for all app modules.
3. Cloud archive manifests are local; independent clean-device retrieval and parity remain unproved.
4. The previous readiness report was advisory, using caller-supplied proof booleans;
   encrypted-file SHA or successful prefetch alone cannot authorize raw-row deletion.

## New Patch 15 fail-closed foundation
- Inventory must include consecutive months through rolling cutoff, without duplicates.
- Reject missing, partial, malformed, future-dated and conflicting archive metadata.
- Recovery proof must be no older than 24 hours and not older than the verified archive.
- Require independent encrypted full-backup read-back of BOTH health and food,
  clean-store restoration, scoring/multi-device/cross-module checks and rollback,
  including interruption recovery.
- These are extra safety checks, NOT completed on-device attestations.

## Mandatory work before any source row is removed
1. Download and authenticate full backup from cloud. Extract HEALTH and FOOD into
   isolated test stores; compare row counts and deterministic per-table content hashes
   against a frozen local snapshot. Byte-level ZIP equality is insufficient.
2. Check device/source IDs, relationships, score parity, snapshots, metrics and labs.
3. Provide complete online deep-history hydration to each module that needs it,
   or keep that module's history strictly local. Prove offline/failed Drive behavior.
4. Perform real-device recovery/rollback drills; bind the verified evidence
   to account, database version, source inventory and exact archive revision.
5. Add explicit default-OFF opt-in with reclaimable bytes, date cutoff,
   per-table effects, data-recovery guidance and a safety backup.
6. Use transactional staged offload: validate cloud, snapshot local state,
   journal pending state, delete bounded eligible rows only after independent
   parity, and roll back on cancellation, crash or loss of authorization.
   Never delete operational tables, tombstones, active days or Food without
   a verified complete and module-compatible substitute.
7. Device smoke tests: historical views in every module before/after,
   offline mode, multi-device, scores, Food, tampered cloud, interrupted
   offload and clean-device restore. Measure real storage reclaimed.
8. Full Personal CI on final SHA, unchanged signing certificate, signed review APK.
   Do not enable deletion or call it a final release before these tests pass.

## Release policy
This v611 safety branch introduces **NO source-data deletion**.
v610 cache and prefetch remain enabled; all health/food history stays local.
