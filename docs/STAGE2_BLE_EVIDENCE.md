# ALKE v604 Stage 2 — evidence and safety contract

## Source-based competitive benchmark (22 November 2026)

- [WHOOP official requirements and sync advice](https://support.whoop.com/s/article/WHOOP-App-Minimum-Software-Requirements): background operation helps keep data current; after closing the app, a full sync can be needed and temporary delays are normal. This is a *user-visible recovery behavior*, not a description of internal queue limits.
- [Garmin official manual upload help](https://support.garmin.com/en-US/?faq=Ht3ZP52Kju075uKvqTqu99&productID=707174&tab=topics): another recovery path exists when automatic sync fails.
- [Amazfit/Zepp official support](https://support.amazfit.com/en/amazfit_balance/docs/HXlBdGQFcoTKAmx7dSSc8KCcn8e): manual resync is supported after a connection failure.
- [COROS official recovery warning](https://support.coros.com/hc/en-us/articles/53446467114388-Update-Your-COROS-App-to-Restore-Connectivity): do not uninstall/reset when unsynced activity remains.
- [Android BLE notifications](https://developer.android.com/develop/connectivity/bluetooth/ble/transfer-ble-data): characteristic updates are delivered asynchronously through GATT callbacks.

**Evidence limit:** none of these sources specifies an internal BLE queue length, WHOOP offload sender-pause command, or the ACK protocol of competitor devices. We MUST NOT infer that Garmin, Zepp or COROS uses any exact threshold.

## Code audit: v603 baseline

- `Backfiller.finishChunk`: durable decoded insert → durable rejected-frame archive (when applicable) → trim cursor persist → ACK.
- `WhoopBleClient.routeBackfillFrame`: previously caught frame processing errors, logged `skipping frame, offload continues`, and kept draining. A later HISTORY_END could acknowledge past a skipped frame. This is a correctness risk supported by the source, not a claim of observed user data loss.
- `BackfillDrainGate`: sequential queue and connection-generation lease already exist. Source had no measured queue high-water.
- `WindowedStreamPlan`, `AnalyticsCachePolicy`, `ReconnectBackoff`: already optimize normal history reads and reconnect; change only after measurement, not imitation without proof.

## v604 scoped changes (Stage 2 Fase A + B instrumentation)

1. On a frame ingestion exception: poison that generation, reject all subsequent queued frames, do not advance ACK past skipped frames, and present an error instead of claiming successful completion.
2. Guard deferred HISTORY_COMPLETE and deferred error callbacks against a superseding generation.
3. Report `queuePeakFrames` once per offload alongside existing throughput diagnostics; no per-frame logging or new scoring work.
4. **Remove arbitrary 8,192-frame hard stop.** In the absence of a verified sender-side flow-control contract and hardware measurements, imposing an arbitrary queue cap may harm otherwise healthy transfers.
5. Preserve the original WHOOP storage/ACK implementation, retry/reconnect policy, scores, Room schema and cloud settings.

## Validation and stop criteria

- Required: pure-JVM tests prove FIFO preservation on large normal bursts, poison behavior after exception, old-generation isolation, retry after reset and explicit teardown behavior.
- Required: full Personal CI; verify only BLE drain, tests, version and this documentation changed.
- Required before a final release: signed APK matches v603 update certificate, and on-device BLE smoke test confirms healthy sync, interruption and reconnect. Unit tests alone cannot prove over-the-air transfer safety.
- Measure before further throughput optimization: peak queued frames, transfer frame rate, actual database-write latency, mean and worst transfer duration, peak memory and battery draw. Establish baseline and compare to a candidate using the *same* device, firmware and backlog.
- **No scoring algorithm edits** in this change. Subsequent Fase C is out of scope until numeric parity is proven.
