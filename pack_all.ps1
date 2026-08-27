$DoWin = $true
$DoAndroid = $true
$SkipNpm = $false
$NoPause = $false
foreach ($a in $args) {
    switch -Regex ($a) {
        "^(?i)(/win|-win)$" { $DoWin = $true; $DoAndroid = $false }
        "^(?i)(/android|-android)$" { $DoAndroid = $true; $DoWin = $false }
        "^(?i)(/skip-npm|-SkipNpm)$" { $SkipNpm = $true }
        "^(?i)(-NoPause|/nopause)$" { $NoPause = $true }
    }
}

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

function Fail([string] $Message) {
    Write-Host ""
    Write-Host "ERROR: $Message" -ForegroundColor Red
    if (-not $NoPause) { Pause }
    exit 1
}

$pkgText = Get-Content -Raw (Join-Path $PSScriptRoot "package.json")
if ($pkgText -notmatch '"version"\s*:\s*"([^"]+)"') {
    Fail "cannot read version from package.json"
}
$version = $Matches[1]
$pkgRoot = Join-Path $PSScriptRoot "packages"
$winDir = Join-Path $pkgRoot "windows"
$androidDir = Join-Path $pkgRoot "android"
$macDir = Join-Path $pkgRoot "macos"
foreach ($d in @($pkgRoot, $winDir, $androidDir, $macDir)) {
    New-Item -ItemType Directory -Force -Path $d | Out-Null
}

Write-Host "========== AabInstalllHelp pack_all $version =========="
Write-Host "Final output: $pkgRoot"
Write-Host ""

if ($DoWin) {
    foreach ($cmd in @("node", "npm")) {
        if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
            Fail "$cmd is not in PATH"
        }
    }
    if (-not $SkipNpm) {
        Write-Host "[win] npm install"
        & npm install
        if ($LASTEXITCODE -ne 0) { Fail "npm install failed" }
    }
    Write-Host "[win] npm run elebuild_win"
    & npm run elebuild_win
    if ($LASTEXITCODE -ne 0) { Fail "elebuild_win failed" }

    $nsis = Get-ChildItem -Path (Join-Path $PSScriptRoot "release") -Recurse -Filter "*.exe" -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -notmatch "\\dist\\" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $nsis) { Fail "Windows installer exe not found under release/" }
    $winName = "AabInstalllHelp-$version-windows-x64.exe"
    Copy-Item -Force $nsis.FullName (Join-Path $winDir $winName)
    Write-Host "copied packages\windows\$winName"
}

if ($DoAndroid) {
    $sdk = $env:ANDROID_HOME
    if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
    if (-not $sdk -and (Test-Path (Join-Path $PSScriptRoot "android\local.properties"))) {
        $lp = Get-Content (Join-Path $PSScriptRoot "android\local.properties")
        foreach ($line in $lp) {
            if ($line -match '^\s*sdk\.dir=(.+)$') {
                $sdk = $Matches[1].Trim().Replace("\\", "\").Replace("/", "\")
            }
        }
    }
    if (-not $sdk -or -not (Test-Path $sdk)) {
        Fail "Android SDK not found. Set ANDROID_HOME or android/local.properties sdk.dir"
    }
    $env:ANDROID_HOME = $sdk
    $env:ANDROID_SDK_ROOT = $sdk

    $jdk = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "scripts\pick-jdk17.ps1")
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($jdk)) {
        Fail "Android build needs JDK 17+. Install JDK 17/21 or Android Studio. JAVA_HOME=$($env:JAVA_HOME)"
    }
    $jdk = $jdk.Trim()
    $env:JAVA_HOME = $jdk
    $env:Path = "$(Join-Path $jdk 'bin');$env:Path"
    Write-Host "[android] JAVA_HOME=$jdk"

    $gw = Join-Path $PSScriptRoot "android\gradlew.bat"
    if (-not (Test-Path $gw)) { Fail "android\gradlew.bat missing. Generate the Gradle wrapper first." }
    Write-Host "[android] assembleRelease"
    Push-Location (Join-Path $PSScriptRoot "android")
    try {
        & .\gradlew.bat --stop | Out-Null
        & .\gradlew.bat :app:assembleRelease --no-daemon
        if ($LASTEXITCODE -ne 0) { Fail "assembleRelease failed" }
    } finally {
        Pop-Location
    }
    $apk = Get-ChildItem -Path (Join-Path $PSScriptRoot "android\app\build\outputs\apk") -Recurse -Filter "*.apk" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $apk) { Fail "Android APK not found" }
    $apkName = "AabInstalllHelp-$version-android.apk"
    Copy-Item -Force $apk.FullName (Join-Path $androidDir $apkName)
    Write-Host "copied packages\android\$apkName"
}

$dmg = Get-ChildItem -Path (Join-Path $PSScriptRoot "release") -Filter "*.dmg" -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($dmg) {
    $macName = "AabInstalllHelp-$version-macos.dmg"
    Copy-Item -Force $dmg.FullName (Join-Path $macDir $macName)
    Write-Host "copied packages\macos\$macName"
}

$sums = Join-Path $pkgRoot "SHA256SUMS.txt"
if (Test-Path $sums) { Remove-Item -Force $sums }
Get-ChildItem -Path $winDir, $androidDir, $macDir -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -ne ".gitkeep" } |
    ForEach-Object {
        $rel = $_.FullName.Substring($pkgRoot.Length).TrimStart("\", "/")
        $rel = $rel -replace "\\", "/"
        $hash = (Get-FileHash -Algorithm SHA256 $_.FullName).Hash.ToLowerInvariant()
        Add-Content -Path $sums -Value "$hash  $rel" -Encoding ascii
    }

Write-Host ""
Write-Host "========== Final packages =========="
Get-ChildItem -Path $winDir, $androidDir, $macDir -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -ne ".gitkeep" } |
    ForEach-Object { Write-Host ("  " + $_.FullName) }
if (Test-Path $sums) { Write-Host "  $sums" }
Write-Host "Done." -ForegroundColor Green
if (-not $NoPause) { Pause }
exit 0
