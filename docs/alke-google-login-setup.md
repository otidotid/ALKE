# ALKE Google Login and Drive OAuth setup

## What the app uses

ALKE authenticates with Android Credential Manager using a **Google Web OAuth
client ID** as the server client ID. Google Cloud also requires an **Android
OAuth client** matching the exact APK package name **and signing SHA-1**.

ALKE does not require Firebase, does not embed client secrets, and does not
store Google ID/access tokens in account preferences. Drive authorization is
requested separately for `drive.appdata` (ALKE's private app-data folder).

## Android packages to register

- Public full debug: `com.otidotid.alke.debug`
- Privately signed personal debug: `com.otidotid.alke.personal.debug`
- Production full release (when distributed): `com.otidotid.alke`

The debug/public and personally re-signed variants may have **different signing
certificates**, even if the source code is identical. Register **each exact
package + SHA-1** in the *same* Google Cloud project as the Web OAuth client.

### Find SHA-1 without exposing keys

For the APK installed on your device, find the original APK file and run
Android SDK Build Tools `apksigner verify --print-certs path/to/ALKE.apk`.
Copy the displayed **Signer #1 certificate SHA-1 digest**, not the private key.

On Windows PowerShell, for a downloaded personal APK:

```powershell
$buildTools = Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" -Directory |
  Sort-Object Name -Descending | Select-Object -First 1
& (Join-Path $buildTools.FullName 'apksigner.bat') verify --print-certs 'D:\OneDrive\Downloads\ALKE-v1.0.0-alpha.15-PRIVATE-20261117.apk'
```

If multiple signing configurations are used, repeat for each real APK.

## Configure in Google Cloud Console

1. Select the intended Google Cloud project at <https://console.cloud.google.com/apis/credentials>.
2. Set up the OAuth consent screen/branding and test users as applicable.
3. Create one OAuth 2.0 client of type **Web application**. Copy its **Client ID**
   (ends in `apps.googleusercontent.com`). It is not an API key or secret.
4. Create an **Android** OAuth 2.0 client for each exact app package and signing SHA-1.
5. Make sure Google Play services is installed/enabled on the device.
6. For automatic configured GitHub builds, set repository Actions secret
   `ALKE_GOOGLE_WEB_CLIENT_ID` to the Web application client ID. For local
   builds, set `ALKE_GOOGLE_WEB_CLIENT_ID` in `android/local.properties` or
   as an environment variable.
7. For builds shipped **without** the ID, ALKE > Settings > Backup & History >
   Google Drive > Google OAuth setup accepts the Web Client ID locally.
   This only supplies the ID; it cannot register your APK SHA-1 in Google Cloud.

## Debug checklist

- Missing ID: sign-in stays disabled with a clear configuration prompt.
- ID configured but Google rejects the flow: verify **Web** ID belongs to the
  same project as the **Android** client for **this** APK's package + SHA-1.
- Sign-in succeeding does not automatically authorize Drive. Select
  **Reconnect Google Drive** to grant `drive.appdata`.
- Legacy Drive app data owned by another OAuth app/client may not be visible in
  ALKE. Never delete the legacy backups.
- Never publish a private backup, client secret, signing key, token or health
  history in GitHub CI artifacts, issues or release assets.
- App data and previously paired WHOOP must remain untouched during Google
  OAuth testing; avoid uninstall/reinstall and `adb shell pm clear`.

## Limits

A successful CI build confirms Kotlin/Android compilation and JVM tests, **not**
live Google OAuth; actual sign-in and Drive authorization require a configured
Google Cloud project and real-device smoke test. Personal APK updates must be
signed with the **same private certificate** or Android will reject the update;
do not uninstall to work around a signing mismatch.
