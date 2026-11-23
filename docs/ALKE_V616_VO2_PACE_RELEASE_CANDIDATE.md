# ALKE v616 — VO₂ Max v2 + Workout Pace

## Delivered

- The app keeps its existing Nes/Uth VO₂ Max baseline when qualifying running
  pace and HR are absent. The baseline is **not calibrated to match Zepp**.
- Qualified outdoor running sessions (at least three) can apply a conservative,
  bounded correction of **at most ±0.5 ml/kg/min** to the existing VO₂ Max estimate.
  The user's observed comparison before release was ALKE 52.5 vs Zepp 51.5;
  this discrepancy alone is not evidence that either estimate is more accurate.
- Workout details show average pace from actual stored distance and recorded
  duration, regardless of ALKE native recording, Health Connect / Zepp, or
  other imported workout sources. No distance means no pace.
- Health Connect workout HR can be read only after its own user-granted
  permission. Previously imported workouts can be enriched non-destructively
  when missing HR or distance becomes available.
- No new database schema or removal of existing history, modules or Fitness Age.

## Reproducible build evidence

- Branch: `alke/v616-vo2-pace-v2`
- Compiled source / unit-test run commit:
  `9e6f84cb48ae8f45082c3e66a808128fc9c2434f`
- GitHub Actions quality run:
  https://github.com/otidotid/ALKE/actions/runs/45104501872
- JVM suite + Personal Debug/Release build + APK validation + unsigned
  original-package artifact upload all **passed**. The combined workflow itself
  ended failed at the optional GitHub Release creation step with
  `HTTP 403 Resource not accessible by integration`.
- Original-package installer: `com.otidotid.alke.personal.debug`
- Version code: 616
- Version name: `1.0.0-alpha.22.26-vo2-pace-personal-debug`
- Owner certificate SHA-256:
  `f34c6210ef774d588a426a784a09bb452f01b6c5f18bec64b03e209a0420e877`
- Android APK Signature Scheme v2: verified. v3: verified.
- Signed APK SHA-256: `c3f972bbe1fc7855fe56639bee241796e861e040bc0516f993dbb1aafc1d86d7`
- The original Personal P12 and password were used to sign the binary outside
  GitHub; they were **not committed to the repository**.

## Release status

The owner-certificate-signed Personal APK was built, verified and delivered in
the original ChatGPT conversation as a downloadable attachment.

**GitHub Release is not published yet** because the repository Actions token
received a 403 at `POST /repos/otidotid/ALKE/releases`. This is a publication
permission problem, not a Gradle build, unit-test or APK signing failure.
Do not claim the GitHub release or real-device installation has been verified.

To install, update the existing Personal app (do not uninstall). The same
package and signing certificate preserve Android's in-place update path.
Actual Health Connect import permissions, run pace and VO₂ Max behavior still
require real-device smoke testing.
