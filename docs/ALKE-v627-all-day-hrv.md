# v627 work in progress — mandatory all-day HRV

Approved owner change: remove separate HRV enable, overnight and battery-pause switches. Retain background connection control and explicit disconnection. Effective background capture depends on background connection only; Live screen keeps its existing on-demand streaming path. Old restored HRV flags cannot narrow capture; legacy keys and helper tests retained for compatibility. No change to RR quality gates, nightly/deep sleep HRV scoring, data schemas, backup, or Charge/Recovery. No new AI classifier in this commit.

24/7 is a capture intent, not proof of uninterrupted measurements: actual availability depends on wearable connectivity, wear, battery, device support and OS restrictions. Higher wearable battery drain is possible. Real-device data continuity and power impact require manual verification.


Release scope: v627 includes the already-tested Adaptive Activity Worker R1 from commit 247254369c8c8e05b7e8ae19f5f10cb6458f183a; no change in Android activity protocol. New nap insert checks all existing sleep intervals atomically and does not overwrite existing main sleep; non-AI/non-legacy-auto workouts also block insertion. It does not repair historical overlap, late main sleep reconciliation, nap provenance, or daily scoring. No new cloud sleep classifier yet: Walch et al. (2019), DOI 10.1093/sleep/zsz180 is an overnight Apple Watch validation, not a WHOOP daytime nap model. The 2024 wearable research recommendations (DOI 10.1093/sleep/zsad325) discuss limitations for daytime sleep. No invented HRV or sleep credit. Existing local nap detection and score policy remain in this release until the scientific design is validated. Worker is packaged for manual deploy; no changes to Groq secrets or endpoint auth.


## Supplemental sleep accounting work in progress

- Shared interval union/subtraction gives credit only to estimated asleep stages within recorded bounds; no duplicate time, no wake or unknown-stage credit. Ambiguous overlapping summary-only stages receive no inferred credit.
- Sleep screen and local debt use the new accounting, and separately entered non-overlapping sessions survive imported-day precedence. Existing rows are not deleted. Main sleep still owns its existing scoring baseline.
- The v627 release remains blocked pending complete cloud nap detection, explicit episode provenance, late main-sleep reconciliation, and coherent Rest/Charge integration. No arbitrary Nap/Daytime threshold or unvalidated AI physiological bonus has been added.
- Evidence: https://pubmed.ncbi.nlm.nih.gov/20626612/ ; https://pubmed.ncbi.nlm.nih.gov/38149978/ ; https://pubmed.ncbi.nlm.nih.gov/31579900/ . These support multisignal research and limitations, not an already-validated WHOOP nap classifier.


## Daily Rest and compact Sleep UI

Daily Rest credits valid supplemental asleep minutes through the existing duration component (50%, default 8h reference), keeping main-sleep quality and Charge unchanged. Imported sleep performance is retained when no supplemental credit exists, not arbitrarily incremented. This is ALKE-specific accounting, not a clinically validated recovery formula. Summary above architecture, date/edit/add controls above hero, preserved stage graph and original HR Timeline. Historical supplemental sessions without a reliable class are honestly shown as supplemental; no fake AI status or invented duration. Main sleep edits target the main group; supplemental edits target only the selected row, and overlapping edits are refused. The final three-category presentation is blocked until cloud classification is integrated. Sensor estimates remain approximate. CDC/NIOSH recognizes both short and long naps (https://www.cdc.gov/niosh/work-hour-training-for-nurses/longhours/mod7/05.html); no universal 30-minute nap/day-sleep cutoff. No release yet.


## Cloud Sleep protocol (draft, not enabled)

Worker /v1/sleep/detect and Android transport support a rolling 24-hour snapshot of 5-minute measured HR, motion, valid RMSSD and coverage; independent consent, activity bearer token, enable flag ENABLE_SLEEP_AI_DETECTION, separate D1 quota, strict result schema, missing data and exclusion gates, completed episodes only. The 70% floor reuses an engineering transport quality gate; no clinical threshold, no arbitrary nap/day sleep duration split, no generated stages or score. No phone-side classifier. This commit does not yet schedule or persist sleep inference and deliberately does not enable it or remove the legacy detector until cutover and late-data reconciliation are validated. Groq model is not clinically validated; the tests use mocked responses only.


## Regression gate updates

Reviewed TodayScreen against v626: only the Rest score/sparkline loader block changed. WorkoutsScreen and HrEventVisuals retain the previous blob hashes. Activity R1's handler is unchanged byte-for-byte; only the Sleep route, helper block and deployment build ID are new. Both Worker test suites now run in Personal Quality Gate and a standalone Node workflow. Sleep summary uses actual estimated asleep minutes, existing local sleep target and selected-day debt; no simulated scores. Cloud contract remains disabled until the Android scheduler, durable episode classification, single-detector cutover and late sync integration are finished.


## v627 design consolidation (owner-approved)

- One pinned daily Rest/total/target/debt summary; one main Sleep Architecture and Hypopnea assessment; one collapsed Sleep Details entry for physiology, balance ledger, trends, body clock and optional historical marks.
- Hide redundant Rest/debt/target tiles only in the compact Sleep grid. Shared Today-hosted grid and protected HR/Workouts visuals stay unchanged. Existing raw records, backup schema, preferences, section IDs and individual metrics are retained.
- Main-sleep Hours vs Target retains its original definition. Daily fulfillment explicitly combines non-overlapping credited sleep and the same target. The prior failed imported-figures regression retains its main-sleep assertions and adds a daily-fulfillment assertion. Imported source values are not overwritten.
- **Remaining release blockers:** a single cloud-driven detector with durable category provenance and late-data reconciliation, full episode stage/efficiency aggregation, and canonical persisted daily Rest feeding Charge and its contributor breakdown. The current duration-credit Rest is an interim implementation, not the final scientific model. No changes to the physiological baseline or fabricated post-nap HRV. Do not release or deploy production until these are complete and validated.


## Unified daily Rest → Charge scoring (draft)

- Preserve original main-sleep DailyMetric and sleep_performance. A new computed metric `alke_daily_rest` aggregates eligible supplemental duration, weighted efficiency and observed Deep/REM through existing RestScorer weights and personal need, only when primary session duration agrees with main metrics. Complete staged non-overlapping rows only; ambiguous overlaps/missing stages receive no quality credit. Main-only days retain legacy behavior. No physiological bonus, invented stages, or changed HRV baseline.
- The raw-data scoring pass supplies the same unified score to RecoveryScorer and its trace; persists it in the computed score transaction, clearing stale points on recompute. Today, Sleep and Rest history prefer the persisted score on computed days instead of calculating a separate UI bonus. Imported provider scores retain original precedence; imported-day recomputation and contributor display reconciliation remain open. The model is not clinically calibrated, and the integration still needs regression/device verification. No production release.


Cloud snapshot builder now reads one physical sensor source, aggregates measured HR/motion/RMSSD into 5-minute bins, rejects truncated streams, noisy/overcounted RR and gaps, and leaves missing evidence null. Engineering gates are not clinical sleep thresholds. No runtime scheduler or data transmission is enabled by this commit; the single-detector cutover and durable episode reconciliation are still release blockers.
