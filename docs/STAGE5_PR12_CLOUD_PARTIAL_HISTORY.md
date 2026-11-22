# PR #12 / ALKE v609 — Cloud date index + independently encrypted weekly previews

**Baseline:** PR #11 / v608 was smoke-tested on phone and merged. This PR
does not change scoring, WHOOP BLE, database schemas, Drive recovery format,
automatic backup timing, or local history retention.

## Existing limitation
Legacy `YYYY-MM.alkeenc` is a single authenticated encrypted archive. HTTP
byte ranges cannot extract a verifiable inner day from that ciphertext.
Changing only an HTTP Range header would not be a valid partial download.

## Versioned optional preview sidecars
- When **new** monthly archives are generated and verified, ALKE also creates
  independent 7-calendar-day preview packets from the exact copied monthly
  SQLite snapshot, using the **same bounded day-preview queries** as the
  original month reader (daily aggregates, workout and sleep session summary
  lines, metricSeries summary). No waveform BLOBs, routes or raw notes.
- Each JSON packet is encrypted using the existing ALKEENC-v1 passphrase
  encryption with independent random IV/salt. Only encrypted bytes are uploaded
  to the private Drive appDataFolder; each remote packet is downloaded again,
  checksum verified, decrypted and compared byte-for-byte with the original.
- The existing local monthly manifest is expanded with optional metadata
  (date bounds, Drive file ID, encrypted SHA-256, byte count, preview line
  count, parent monthly ciphertext SHA-256). No health values are stored in
  unencrypted manifest metadata.
- When opening an eligible archived day, ALKE uses the index to download the
  **one encrypted week packet** that covers that day, rather than the entire
  encrypted month. SHA-256/length, encryption, parent archive identity,
  schema/version/date range and row count are checked before previewing.
- If a packet is absent, invalid, missing or fails verification, ALKE falls
  back to the original independently verified monthly archive reader.
  Legacy monthly archives without any sidecars remain fully compatible.
- New sidecar uploads are optional **and never justify deletion of raw local
  data**. Full monthly encrypted archives and disaster recovery backups
  remain authoritative. Partial-preview failures do not fail an already
  verified monthly archive.

## Limitations and next steps
- **Partial download contains the existing bounded day preview only**, not
  every raw biometric sample or full workout payload. Deep details still
  require reading the complete encrypted monthly archive.
- Old existing monthly archives are not silently rewritten; they retain the
  previous full-month read path until a future explicit backfill strategy.
- Week packets are not persistently cached; their temporary downloaded and
  decrypted bytes are deleted after reading. Later cache/prefetch work may
  optimize this with independent caps and expiry policies.
- The source still retains all original local historical data. Encrypted
  online-only retention needs independently verified restore/rollback gates.

## Validation
- Daily window boundary, date coverage, malformed hash/size, changed monthly
  version, legacy format and encrypted-only lookup unit tests.
- Full Personal Android CI + original certificate and valid v2/v3 APK signing
  before user smoke test.
