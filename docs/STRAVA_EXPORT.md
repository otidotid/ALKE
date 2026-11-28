# ALKE v663 — Strava FIT export

Workout Detail exposes an explicit "Export to Strava (FIT)" action. Android document picker saves a file; after successful save the owner may open https://www.strava.com/upload/select and select it. No automatic network sharing, account tokens or API secret. Strava accepts timed FIT records (GPS optional), <=25 MB: https://developers.strava.com/docs/uploads/ and https://support.strava.com/en-us/articles/15402066-how-do-i-get-my-activities-to-strava.

Recorded-only path keeps original HR and validated Huawei GPS timestamps. Huawei HR takes priority on observed minutes; WHOOP fills gaps. Legacy lat/lon-only route without timestamps is omitted, explicitly disclosed; no interpolated pace or fabricated GPS. At least two timestamped records required. FIT activity type uses known Garmin profile codes, hiking 17. Unknown sport uses generic. Strava processing and sport attribution require actual account smoke test. No change to existing legacy exporter, data store contents, source matrix or scoring.

Direct OAuth upload is a separate integration: requires registered Strava app, authorization with activity:write, protected server-side secret/token lifecycle, idempotent external_id, status polling and duplicate handling. No credential embedded in APK. Full CI required before signing.
