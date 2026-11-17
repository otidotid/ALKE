# ALKE design lock, module-removal ledger and selective restoration

## Safe snapshot (created BEFORE the removals)

- Immutable content reference: commit `4eb0d4f64a126d32893b55f200221ade2086b3c7`
- Convenient read-only recovery branch (do not force-push): `alke/backup-20261117-pre-design-lock`
- Working branch: `alke/alpha8-migration-brand-audit`
- Snapshot URL: https://github.com/otidotid/ALKE/tree/alke/backup-20261117-pre-design-lock
- This is a complete SOURCE snapshot, not a personal database, cloud account or hardware-device backup.
- Do not overwrite the source snapshot. Do not reset the entire app to roll back one cosmetic element.

## User-approved user-facing module removal inventory

Each row describes a **UI-only removal**. Underlying saved preferences, settings serializers, backup compatibility, and all data/analytics services remain in the source. This is intentional: removal of UI is reversible, whereas deletion of stable storage keys could corrupt migration.

| ID | UI module removed / consolidated | Original file and boundary | Selective recovery method | Preserved functionality |
|---|---|---|---|---|
| UI-01 | Appearance / theme customization panel | `android/app/src/main/java/com/noop/ui/SettingsScreen.kt`: `// --- Appearance (Theme) ---` through the closing `SettingsCard` preceding the AI Coach card | Copy only the original block from the backup commit back into the Settings screen, leaving General and AI settings intact | Existing language/clock settings moved into General; historical display preferences retained |
| UI-02 | Background image customization panel | `android/app/src/main/java/com/noop/ui/SettingsScreen.kt`: `// --- Background image` to `// --- App icon` | Restore only this original SettingsCard block and any launchers/state required | Previously uploaded local image files and `BackgroundImageStore` are not deleted |
| UI-03 | App icon customization panel | `android/app/src/main/java/com/noop/ui/SettingsScreen.kt`: `// --- App icon` to `// --- Strap` | Restore only that card | Existing launcher icon aliases and saved icon preference are retained; no manifest components removed |
| UI-04 | Cosmetic Appearance wording from Me | `android/app/src/main/java/com/noop/ui/AlkeExperience.kt`: Settings link subtitle | Restore the one line in `AlkeMeExperience` | Settings and all operational routes remain intact |
| UI-05 | Legacy Noop/Thoop-facing wording and historical What's New presentation | User-facing literal text in Kotlin UI, `AppChangelog.kt`/`WhatsNewSheet.kt`; no preferences/database keys renamed | Recover individual literal or historical note from original file via backup commit | Existing history and copyright notices remain recoverable in source, license remains |
| UI-06 | Fixed ALKE dark visual baseline (theme selection no longer offered) | `android/app/src/main/java/com/noop/ui/Theme.kt` `NoopTheme` | Restore just old `NoopTheme` body from backup to re-enable light/system modes | Stored theme preference is preserved; no data mutation |
| UI-07 | Duplicate/unused discovery entries if any are explicitly proven unused | `android/app/src/main/java/com/noop/ui/AppRoot.kt` `drawerGroups`/`configurableBottomBarDestinations` | Restore only removed destination entries from the recovery snapshot | Navigation routes and feature implementations remain untouched |

**Status convention:** Rows are planned until a matching working-branch commit implements them. No row asserts that a deletion was already completed. Record the resulting changes in the working branch's commit log.

## What must NOT be removed

- Android `com.noop` Kotlin namespace, packages, Room table/column names, SQL entities/migrations.
- Preference filenames/keys such as `noop_ai_secure_prefs`, `thoop_google_account`, `thoop_google_drive`, `noop.*`.
- On-disk backup ZIP entry name `noop-backup.sqlite`, legacy `.noopbak` reader, legacy Drive media type/kind metadata, encrypted archive compatibility.
- Consent, API-key encryption and provider-owner protections; Google Credential Manager and Drive scopes; BLE device integrations; test centre and debug exports; analytics/scoring engines.
- Legal licenses and third-party copyright attribution.
- Real user databases or local backup files. Never bundle API tokens or personal health data into synthetic demo APKs.

## Selective restore recipe (GitHub repo checkout)

1. Verify the source backup is reachable: `git rev-parse alke/backup-20261117-pre-design-lock` must equal the snapshot SHA above.
2. Work on a **new** fix branch based on the latest ALKE working branch, never on the backup branch itself.
3. View the exact old file with `git show 4eb0d4f64a126d32893b55f200221ade2086b3c7:android/app/src/main/java/com/noop/ui/SettingsScreen.kt`.
4. Restore **only the relevant UI composable block** by applying a small patch. Whole-file checkout is not recommended because it would overwrite unrelated fixes and migrations.
5. Add a regression test for the restored panel and rerun both full/demo builds and full unit tests.
6. Review the diff, commit with the row ID (e.g. `revert(UI-02)`) and publish an isolated APK for acceptance.

## Data restore is separate

A source rollback cannot restore a deleted/overwritten phone database. Before reinstalling or migrating to a new application ID, export and verify an ALKE/`.alkebak` (or legacy `.noopbak`) data backup in the app and retain a secure copy. Cloud Drive private `appDataFolder` is app-scoped, and reauthorizing a different Android OAuth client does not automatically expose a prior app's folder. Re-enter provider API keys in ALKE; encrypted key material should not be exported into a public repository or APK.
