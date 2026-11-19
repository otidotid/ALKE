param(
    [switch]$FullTests,
    [switch]$SkipBuild,
    [string]$Apk = ".\signed-output\ALKE-alpha20-insight-cleanup-signed.apk"
)
$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repo

if (git status --porcelain --untracked-files=no) {
    throw "Tracked working tree must be clean before release validation."
}

git diff --check
if ($LASTEXITCODE -ne 0) { throw "git diff --check failed." }

if (-not $SkipBuild) {
    Set-Location "$repo\android"
    $tests = @(
        ":app:testPersonalDebugUnitTest",
        "--tests", "com.otidotid.alke.release.ReleaseContractTest",
        "--tests", "com.otidotid.alke.ai.StepsAiValidatorClientTest",
        "--tests", "com.otidotid.alke.analytics.StepsFusionProductionTest",
        "--tests", "com.otidotid.alke.analytics.StepsEstimateEngineTest",
        "--tests", "com.otidotid.alke.analytics.StepsCounterTest",
        "--no-daemon"
    )
    & .\gradlew.bat -PstagingRelease @tests
    if ($LASTEXITCODE -ne 0) { throw "Targeted Personal tests failed." }
    if ($FullTests) {
        & .\gradlew.bat -PstagingRelease :app:testPersonalDebugUnitTest --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "Complete Personal tests failed." }
    }
    & .\gradlew.bat -PstagingRelease :app:compilePersonalDebugKotlin :app:assemblePersonalDebug --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Personal build failed." }
    Set-Location $repo
}

if (-not (Test-Path $Apk)) { throw "Signed APK not found: $Apk" }
$buildTools = Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" -Directory |
    Where-Object Name -match '^\d+(\.\d+)+$' |
    Sort-Object { [version]$_.Name } -Descending |
    Select-Object -First 1
if (-not $buildTools) { throw "Android build-tools not found." }
$aapt = Join-Path $buildTools.FullName "aapt.exe"
$apksigner = Join-Path $buildTools.FullName "apksigner.bat"
$packageLine = & $aapt dump badging $Apk | Select-String '^package:' | Select-Object -First 1
if ($packageLine -notmatch "name='com\.otidotid\.alke\.personal\.debug'") { throw "Unexpected package: $packageLine" }
$gradle = Get-Content ".\android\app\build.gradle.kts" -Raw
$versionCode = [regex]::Match($gradle, 'versionCode\s*=\s*(\d+)').Groups[1].Value
if (-not $versionCode -or $packageLine -notmatch "versionCode='$versionCode'") { throw "APK version differs from build.gradle.kts: $packageLine" }
$verify = & $apksigner verify --verbose --print-certs $Apk 2>&1
if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed." }
$verifyText = ($verify -join "`n").ToLowerInvariant()
if ($verifyText -notmatch 'verified using v2 scheme.*true' -or $verifyText -notmatch 'verified using v3 scheme.*true') { throw "APK must verify with v2 and v3." }
$hash = (Get-FileHash $Apk -Algorithm SHA256).Hash
$checksum = "$Apk.sha256"
"$hash  $([IO.Path]::GetFileName($Apk))" | Set-Content $checksum -Encoding ASCII
Write-Host "ALKE PERSONAL RELEASE VALIDATED" -ForegroundColor Green
Write-Host "VersionCode: $versionCode"
Write-Host "APK SHA-256: $hash"
Write-Host "Checksum: $checksum"
