# ALKE Alpha 19.1 — Personal v583

**Release status:** Final published Personal APK for Alpha 19.1 smoke-test revisions (not an APK Demo).

## What's changed
- **Header:** Removed the redundant age text beside the profile avatar.
- **Me / Profile:** Combined profile information and profile photo into one section; the same photo remains shared across modules.
- **Google Drive and Backup:** Corrected corrupt characters and encoding in visible labels and status text.
- **Today and Insights:** Body Readiness now uses the same continuously animated luminous cyan/gold dial in both modules, with clearer moving highlights. The animation changes presentation only, never the Recovery value.
- **ALKE sync header:** A thick moving cyan/gold indicator, pulsing logo, and visible “SYNCING DEVICE” state make an active synchronization obvious across modules.
- **Battery saver / motion settings:** Idle animations respect reduced-motion and battery-saving settings; the actual synchronization state continues to be represented.
- Includes Alpha 19’s **unified weighted Fitness Age**, isolated evidence-based Insights summary, Me navigation cleanup, and Cloud Coach build configuration.

## Compatibility and verification
- **APK:** `ALKE-v1.0.0-alpha.19.1-PERSONAL-v583-signed.apk`
- **Application ID:** `com.otidotid.alke.personal.debug`
- **Version:** `1.0.0-alpha.19.1` (`versionCode 583`)
- **Certificate SHA-256:** `f34c6210ef774d588a426a784a09bb452f01b6c5f18bec64b03e209a0420e877` — same Personal update signer as previous releases.
- **APK SHA-256:** `c9ebb77178905f1a7cb49a77b4c610ab90134bbccfea3fb42029fba646b85d3b`
- **Android APK Signature Scheme v2 and v3:** verified.
- **CI:** [Alpha 19.1 Personal v583 successful build and unit tests](https://github.com/otidotid/ALKE/actions/runs/44249736377) (commit `c975ff9571522518bcc69c201fd1ee2a6d88607d`).

**Install as an update over v581/v582. Do not uninstall your current ALKE app:** preserving the installed package and original signing identity protects existing app data. A direct in-device smoke test, including active device sync, Google Drive interaction, and the visual animations, is still recommended.

**Security:** This binary is the byte-identical preverified Personal APK; it is reconstructed from the pinned successful build and a public APK v2/v3 signature block. The private signing key and password are not included in GitHub.
