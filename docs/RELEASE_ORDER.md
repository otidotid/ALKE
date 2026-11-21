# ALKE Releases — Newest First

This is the **canonical numeric release index** for ALKE's signed Android builds. GitHub's native Releases screen and REST listing sometimes display older releases out of sequence (for example, v599 before v601). That platform ordering cannot be manually overridden. Use the version number here to identify the newest build.

> Current signed release: **ALKE v603**. Source and APK passed CI; on-device v603 smoke-testing has not been independently confirmed. Asset `ALKE-v603-stage1-signed.apk`, SHA-256 `a02af876f7410e42cdbc02c6be76c0b347236dca1b4f7e81b3f8e85001e25682`.

| Version | GitHub Release | Date (UTC) |
| ---: | --- | --- |
| **v603** | [Stage 1 UI refinement](https://github.com/otidotid/ALKE/releases/tag/v1.0.0-alpha.22.13-stage1-ui-refinement.603) | 2026-11-21 |
| **v602** | [Verified total audit fixes](https://github.com/otidotid/ALKE/releases/tag/v1.0.0-alpha.22.12-total-audit-fixes.602) | 2026-11-21 |
| **v601** | [Weekly Fitness Age publication](https://github.com/otidotid/ALKE/releases/tag/v1.0.0-alpha.22.11-fitness-age-weekly.601) | 2026-11-21 |
| **v600** | [Correctness and data safety](https://github.com/otidotid/ALKE/releases/tag/v1.0.0-alpha.22.10-correctness.600) | 2026-11-21 |
| **v599** | [Premium realtime widgets](https://github.com/otidotid/ALKE/releases/tag/v1.0.0-alpha.22.9-premium-widgets.599) | 2026-11-21 |
| **v598** | [Live HR audit](https://github.com/otidotid/ALKE/releases/tag/v1.0.0-alpha.22.8-live-hr-audit.598) | 2026-11-20 |
| **v597** | [Audit correctness](https://github.com/otidotid/ALKE/releases/tag/v1.0.0-alpha.22.7-audit-correctness.597) | 2026-11-20 |
| **v596** | [Widget resume and Me cleanup](https://github.com/otidotid/ALKE/releases/tag/v1.0.0-alpha.22.6-widget-resume-me-cleanup.596) | 2026-11-20 |
| **v595** | [Me navigation cleanup](https://github.com/otidotid/ALKE/releases/tag/v1.0.0-alpha.22.5-me-cleanup.595) | 2026-11-20 |
| **v594** | [Me consolidation](https://github.com/otidotid/ALKE/releases/tag/v1.0.0-alpha.22.4-me-consolidation.594) | 2026-11-20 |
| **v593** | [Fitness Age safety](https://github.com/otidotid/ALKE/releases/tag/v1.0.0-alpha.22.3-fitness-age-safety.593) | 2026-11-20 |
| **v592** | [Premium HR timeline](https://github.com/otidotid/ALKE/releases/tag/v1.0.0-alpha.22.2-hr-timeline-premium.592) | 2026-11-19 |
| **v591** | [HR widget polish](https://github.com/otidotid/ALKE/releases/tag/v1.0.0-alpha.22.1-widget-hr-polish.591) | 2026-11-19 |
| **v590** | [Stress and Breathe](https://github.com/otidotid/ALKE/releases/tag/v1.0.0-alpha.22-stress-breathe.590) | 2026-11-19 |
| **v589** | [Steps AI validation](https://github.com/otidotid/ALKE/releases/tag/v1.0.0-alpha.21.1-steps-ai.589) | 2026-11-19 |
| **v588** | [Steps Fusion](https://github.com/otidotid/ALKE/releases/tag/v1.0.0-alpha.21-steps-production.588) | 2026-11-19 |
| **v587** | [Alpha 20.3](https://github.com/otidotid/ALKE/releases/tag/v1.0.0-alpha.20.3-personal.587) | 2026-11-18 |
| **v586** | [Alpha 20.2](https://github.com/otidotid/ALKE/releases/tag/v1.0.0-alpha.20.2-personal.586) | 2026-11-18 |
| **v585** | [Alpha 20.1](https://github.com/otidotid/ALKE/releases/tag/v1.0.0-alpha.20.1-personal.585) | 2026-11-18 |
| **v584** | [Alpha 20 final](https://github.com/otidotid/ALKE/releases/tag/v1.0.0-alpha.20-personal.584) | 2026-11-18 |
| **v583** | [Alpha 19.1](https://github.com/otidotid/ALKE/releases/tag/v1.0.0-alpha.19.1-personal.583) | 2026-11-18 |
| **v581** | [Alpha 18](https://github.com/otidotid/ALKE/releases/tag/v1.0.0-alpha.18-personal.581) | 2026-11-18 |

Earlier debug/design preview releases remain available under [GitHub Releases](https://github.com/otidotid/ALKE/releases). The duplicate historical experimental tag [v597-full-corrective](https://github.com/otidotid/ALKE/releases/tag/v597-full-corrective) is **not** a newer numbered build than v597.

## Release invariants

1. The Git tag's trailing build number must equal Android `versionCode`.
2. Release only an APK signed with the existing update certificate; verify the previous version's certificate fingerprint.
3. Check the signed APK's SHA-256, package ID, and APK Signature Schemes v2/v3 before publishing.
4. Publish the release on the appropriate source commit, not an unrelated `main` branch tip.
5. Run the complete test suite and record on-device smoke-test results. Never infer successful runtime behavior from compilation alone.
6. Update this index in descending numeric order when releasing a new version; do not delete older release assets merely to change GitHub's native sorting.
