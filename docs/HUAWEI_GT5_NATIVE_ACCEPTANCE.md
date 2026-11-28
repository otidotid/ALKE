# Huawei GT 5 native integration — acceptance contract

Base: ALKE v630 (d496607e1c61fc27f3b62d2c2816e9f9fec0bdba).

Goal: native Huawei Bluetooth pairing and simultaneous existing WHOOP connectivity.
No Huawei Health, Health Connect, Gadgetbridge runtime, or watch APK in the native path.
WHOOP is worn 24/7; Huawei is a complementary daytime device. No scheduled/manual device switching.

| Metric | Primary | Fallback |
|---|---|---|
| Heart rate | Huawei | WHOOP |
| HRV | WHOOP | Huawei only if measurement compatible |
| RR intervals | WHOOP | Huawei only if genuine RR available |
| Sleep | WHOOP | Huawei |
| Steps | Huawei | WHOOP |
| GPS | Huawei | phone GPS |
| Workout detail | Huawei | WHOOP |
| SpO2 | Huawei | WHOOP |

Preserve existing header and WHOOP outline/percentage battery capsule, add Huawei outline/percentage in the same capsule separated by divider. Connection indicators independent, disconnected last known values dimmed, unknown battery not invented. Existing ALKE logo remains sync shortcut. No battery shortcut or general redesign. Respect v630 Me navigation. Preserve all existing algorithms, HR timeline and activity icons, AI workout detection, backup/restore, historical records and cloud contracts. No Fossil scope.

## Status
- The source-policy selector remains isolated from app ingestion and consolidation. The opt-in Huawei GT 5 flow attempts first binding, reconnect authentication, and encrypted product and battery readback. Physical acceptance requires a successful run on a real watch, followed by reconnect and WHOOP coexistence checks.
- Before integration, inventory actual BLE services, state/DAO/backup dependencies, existing Fused Record/source handling and v630 header. Prefer reuse rather than parallel engines.
- Huawei proprietary protocol requires independent implementation and verification of transport, session authentication/encryption, discovery, history framing, reconnect and actual GT5 firmware capabilities. ALKE LICENSE is PolyForm Noncommercial; do not copy incompatible AGPL source. Document protocol facts and independently implemented code. References: https://gadgetbridge.org/basics/pairing/huawei-honor-pairing/ and https://gadgetbridge.org/basics/topics/huawei-honor/ . No automatic factory reset.
- Persist raw records and source identifiers; align intervals/units, validate quality and wear status if available; keep RR sequences and sleep stages coherent; steps from aligned non-overlapping intervals, not summed counters or max. Select only compatible/valid primary/fallback. Reconcile late records incrementally and preserve provenance. No fabricated readings. Resolve overlapping native/manual/AI workout sessions into one canonical identity and HR icon. Existing data remains readable after migration and restore.
- Prove actual native pairing and sync, both devices simultaneously, WHOOP-only fallback, Huawei-only supported metrics, on/off wrist, reconnect, repeated sync and late data, steps and workout duplicates, historical retention and header layout. Never infer raw RR support from HR/HRV summaries.
- Require compilation, unit/regression checks and staging build before physical smoke test. Do not distribute a signed final release until evidence supports acceptance. No release version bump in this foundation.

Benchmark: Zepp multi-device experience and Garmin Primary Wearable/Primary Training Device; ALKE's explicit per-metric policy is not a claim about their proprietary internal algorithms.
