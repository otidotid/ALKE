# ALKE v625 — approved total premium redesign

Baseline: v624 / 4df12bc38f66338218bf564aeb1fe25bad11559d. User approved two releases only: v625 redesign, v626 final visual consistency/performance/regression audit.

## Non-negotiable
- Exact approved All Sessions card layout, typography, colors, filters, editing and HR/calorie presentation. Keep WorkoutsScreen.kt unchanged unless separately approved.
- Keep the existing HR graph rendering, markers, sleep bands and gestures. Do not replace TodayScreen.kt or HrEventVisuals.kt.
- Preserve existing algorithms, data, backups, import routes, signing identity F34C, and stored settings. Online Activity Intelligence through Cloudflare/Groq only; no local classification/approval.
- Keep continuous premium hero animations and reduced-motion support. Use existing measured values, not mockup values.
- One release APK for v625, not separate releases for each commit. No release until all approved sections implemented and Quality Gate passes.

## Acceptance matrix
| Area | Required layout/function | State at initial implementation |
| --- | --- | --- |
| Progress | Existing Fitness Age hero first, four factors, detail history, Lab Book, Trends Studio, weekly review | Parent links and retained hero implemented; exact visual review pending |
| Sleep | Canonical direct navigation, one hero, architecture, hypopnea, grouped sleep balance, night physiology, clock, history, header alarms | Direct route and balance disclosure implemented; exact visual layout pending |
| Stress & Breathe | Stress hero and timeline, guided breathing, history | Shared mode host implemented; full visual composition pending |
| Heart Rate Center | Existing graph unchanged, live HR, HRV snapshot, rhythm, zones | Shared mode host implemented; HRV through existing Live HR; visual review pending |
| Insight | Signals, Daily Journal (journal/mood/caffeine), Personal Analytics, AI Summary last | Groups and disclosures implemented; exact compact mockup pending |
| Trends Studio | Trends, Explore, Compare, shared selector | Shared mode host implemented; visual review pending |
| Me | Profile, connected sources, preferences, diagnostics | Grouped cards implemented; detail visual review pending |
| Train | Retain All Sessions, intervals and Activity Overview | Contextual intervals and lifetime overview label implemented; exact grouped layout pending |
| Navigation | Custom dock preserved, old routes remain valid, no duplicate discovery items | Initial consolidation implemented; test all entry points |
| Design system | Shared v624 navy/cyan/gold typography, spacing and component states | New shared surface/link/selector; full styling pending |

Source-based work does not establish on-device fidelity. Do not call the matrix complete until screenshot comparison and functional navigation tests succeed.

## Implementation checkpoints
- Added real weekly age sparkline, compact journal/mood/caffeine cards, selected-night sleep readouts and header alarms; no illustrative data used.
- Consolidated Train activity overview, added contextual HRV/RHR readouts and actual device connectivity, and reduced duplicate headings in tab hosts.
- Kept original sub-screen implementations for full functional depth. Saved pins are not rewritten.
- Remaining: verify visual fidelity on a real device; review actual Sleep architecture ordering, skin-temperature/charge contributor ownership and deep settings styling against the mockups. CI tests and signed APK release not yet verified.

## Latest correction pass
- Me contracts now assert approved Profile → Connected Sources → Preferences ordering, retained backup/settings callbacks and dock editing. More uses the original push callback with canonical Stress catalog entry, so Back retains the originating stack.
- Recovery contributors moved into Charge detail; skin-temperature/cycle/illness content moved into Insight with original controls. Legacy Health links remain available.
- Sleep architecture defaults first, hypopnea follows architecture, date-specific hero readouts use the selected day rather than latest-history tiles. Existing stored custom Sleep layout orders remain unchanged.
- Train intervals is grouped with start/add controls. Build and full regression must be rerun on this commit. Device visual verification still pending.

## Visual implementation follow-up
- Stress uses a real 0–3 semicircular instrument, timeline, guided breathing and expandable historical/advanced evidence. Breathing remains available when stress readings are absent.
- Sleep selected-night summary uses the existing debt calculation with an explicit day key. Night physiology and Body Clock are collapsible. Old saved orders and keys are unchanged.
- Insight adds the gold AI summary/Coach entry and retains evidence-only text and retry. Heart Rate Center tiles link their correct metric histories.
- Visual fidelity of all modules still requires actual screenshot/device verification. No release is authorized by these source-only checks.

## Pre-signing audit pass
- Audited exact passing head e8e6110 against v624: no deleted files; no algorithm, DB schema, signing, BLE protocol, cloud Worker or backup implementation changes. Shared UI palette changes still require device review.
- Fixed three navigation/state/data risks introduced by consolidation: tab switches now preserve Trends/Explore selections, widget and active-workout commands override restored HR tabs, Journal summary counts unique items and uses selected-date data (today-only mood/caffeine labeled separately).
- Added source contract tests. These are not instrumentation or device smoke tests. New head requires fresh full Quality Gate. Device visual/layout, back-stack, BLE, actual backup/restore, cloud detection and measured performance remain unverified in this environment. Do not publish as final.
