$Draft = $false
$SkipPack = $false
$NoBump = $false
$BumpPart = "patch"
$NotesFile = ""
$NoPause = $false
for ($i = 0; $i -lt $args.Count; $i++) {
    $a = [string]$args[$i]
    switch -Regex ($a) {
        "^(?i)(/draft|-draft)$" { $Draft = $true }
        "^(?i)(/skip-pack|-SkipPack)$" { $SkipPack = $true }
        "^(?i)(/no-bump|-NoBump)$" { $NoBump = $true }
        "^(?i)(/major|-major)$" { $BumpPart = "major" }
        "^(?i)(/minor|-minor)$" { $BumpPart = "minor" }
        "^(?i)(/patch|-patch)$" { $BumpPart = "patch" }
        "^(?i)(-NoPause|/nopause)$" { $NoPause = $true }
        "^(?i)(/notes|-notes)$" {
            if ($i + 1 -ge $args.Count) { Write-Host "ERROR: /notes requires a file"; exit 1 }
            $NotesFile = [string]$args[$i + 1]
            $i++
        }
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

function Invoke-Gh([string[]] $GhArgs) {
    $old = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & gh @GhArgs
        return [int]$LASTEXITCODE
    } finally {
        $ErrorActionPreference = $old
    }
}

$gh = Get-Command gh -ErrorAction SilentlyContinue
if (-not $gh) { Fail "GitHub CLI not found. winget install --id GitHub.cli -e   then   gh auth login" }

$oldEap = $ErrorActionPreference
$ErrorActionPreference = "SilentlyContinue"
& gh auth status 2>$null
$authCode = [int]$LASTEXITCODE
$ErrorActionPreference = $oldEap
if ($authCode -ne 0) { Fail "GitHub CLI is not logged in. Run: gh auth login" }

function Read-AppVersion {
    $pkgText = Get-Content -Raw (Join-Path $PSScriptRoot "package.json")
    if ($pkgText -notmatch '"version"\s*:\s*"([^"]+)"') {
        Fail "cannot read version from package.json"
    }
    return $Matches[1]
}

if ($SkipPack) { $NoBump = $true }

$oldVersion = Read-AppVersion
if (-not $NoBump) {
    if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
        Fail "npm is not in PATH (needed to bump version)"
    }
    Write-Host "Bump version ($BumpPart): $oldVersion -> ..."
    & npm version $BumpPart --no-git-tag-version --no-commit-hooks
    if ($LASTEXITCODE -ne 0) { Fail "npm version $BumpPart failed" }
}

$version = Read-AppVersion
$tag = "v$version"
$pkgRoot = Join-Path $PSScriptRoot "packages"

Write-Host "========== AabInstalllHelp Release $tag =========="
if (-not $NoBump) {
    Write-Host "Version: $oldVersion -> $version (only publish_github_release bumps)"
} else {
    Write-Host "Version: $version (no bump)"
}
Write-Host ""

if (-not $SkipPack) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "pack_all.ps1") "-NoPause"
    if ($LASTEXITCODE -ne 0) { Fail "pack_all failed" }
}

if (-not (Test-Path $pkgRoot)) { Fail "missing packages\ ; run pack_all.bat first" }

$pkgFiles = @(
    Get-ChildItem -Path (Join-Path $pkgRoot "windows"), (Join-Path $pkgRoot "android"), (Join-Path $pkgRoot "macos") -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -ne ".gitkeep" }
)
$stale = @($pkgFiles | Where-Object { $_.Name -notlike "*$version*" })
if ($stale.Count -gt 0) {
    Write-Host "Skipping leftover files from other versions:" -ForegroundColor Yellow
    foreach ($s in $stale) { Write-Host "  $($s.Name)" -ForegroundColor Yellow }
}
$versioned = @($pkgFiles | Where-Object { $_.Name -like "*$version*" })
if ($versioned.Count -lt 1) { Fail "no $version artifacts in packages\windows|android|macos ; run pack_all.bat first" }

$sumFile = Join-Path $pkgRoot "SHA256SUMS.txt"
if (Test-Path $sumFile) { Remove-Item -Force $sumFile }
foreach ($f in $versioned) {
    $rel = $f.FullName.Substring($pkgRoot.Length).TrimStart("\", "/")
    $rel = $rel -replace "\\", "/"
    $hash = (Get-FileHash -Algorithm SHA256 $f.FullName).Hash.ToLowerInvariant()
    Add-Content -Path $sumFile -Value "$hash  $rel" -Encoding ascii
}

$assets = @($versioned | ForEach-Object { $_.FullName })
if (Test-Path $sumFile) { $assets += $sumFile }

$defaultNotes = @"
AabInstalllHelp $tag

测试签名工具：把 .aab 转成可安装包。安装结果使用 Android debug keystore，不能覆盖商店正式包，也不能用于上架。

下载：
- AabInstalllHelp-$version-windows-x64.exe  Windows
- AabInstalllHelp-$version-android.apk  Android 本机扫描并安装 aab
- AabInstalllHelp-$version-macos.dmg  macOS（由 GitHub Actions 补传）

校验：SHA256SUMS.txt
"@

if ($NotesFile -and (Test-Path $NotesFile)) {
    $notes = Get-Content -Raw $NotesFile
} else {
    $notes = $defaultNotes
}
$tmpNotes = Join-Path $env:TEMP "AabInstalllHelp-release-notes.md"
Set-Content -Path $tmpNotes -Value $notes -Encoding utf8

$target = ""
try { $target = (& git -C $PSScriptRoot rev-parse HEAD).Trim() } catch { Fail "cannot read git HEAD" }
if (-not $target) { Fail "empty git HEAD" }

if (-not $NoBump) {
    & git -C $PSScriptRoot add -- "package.json" "package-lock.json"
    $status = & git -C $PSScriptRoot status --porcelain -- "package.json" "package-lock.json"
    if ($status) {
        & git -C $PSScriptRoot commit -m "chore: release $tag" -- "package.json" "package-lock.json"
        if ($LASTEXITCODE -ne 0) { Fail "git commit version bump failed" }
        & git -C $PSScriptRoot push origin HEAD
        if ($LASTEXITCODE -ne 0) { Fail "git push version bump failed" }
        $target = (& git -C $PSScriptRoot rev-parse HEAD).Trim()
    }
}

$existingTag = & git -C $PSScriptRoot tag -l $tag
if (-not $existingTag) {
    & git -C $PSScriptRoot tag $tag
    if ($LASTEXITCODE -ne 0) { Fail "git tag failed" }
    & git -C $PSScriptRoot push origin $tag
    if ($LASTEXITCODE -ne 0) { Fail "git push tag failed" }
}

function Test-GhRelease([string] $ReleaseTag) {
    $old = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try {
        $null = & gh release view $ReleaseTag --json tagName 2>$null
        return ($LASTEXITCODE -eq 0)
    } finally {
        $ErrorActionPreference = $old
    }
}

if (Test-GhRelease $tag) {
    Write-Host "Release $tag exists. Uploading with --clobber..."
    $code = Invoke-Gh (@("release", "upload", $tag) + $assets + @("--clobber"))
    if ($code -ne 0) { Fail "gh release upload failed" }
} else {
    $createArgs = @(
        "release", "create", $tag,
        "--title", "AabInstalllHelp $tag",
        "--notes-file", $tmpNotes,
        "--target", $target
    )
    if ($Draft) { $createArgs += "--draft" } else { $createArgs += "--latest" }
    $createArgs += $assets
    $code = Invoke-Gh $createArgs
    if ($code -ne 0 -and -not (Test-GhRelease $tag)) { Fail "gh release create failed" }
}

function Remove-StaleReleaseAssets([string] $ReleaseTag, [string] $KeepVersion) {
    $old = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $jsonText = & gh release view $ReleaseTag --json assets 2>$null
        if ($LASTEXITCODE -ne 0 -or -not $jsonText) { return }
        $json = $jsonText | ConvertFrom-Json
        foreach ($asset in @($json.assets)) {
            $name = [string]$asset.name
            if (-not $name -or $name -eq "SHA256SUMS.txt") { continue }
            if ($name -notlike "AabInstalllHelp-*") { continue }
            if ($name -like "*$KeepVersion*") { continue }
            Write-Host "Deleting stale release asset $name" -ForegroundColor Yellow
            $code = Invoke-Gh @("release", "delete-asset", $ReleaseTag, $name, "--yes")
            if ($code -ne 0) {
                Write-Host "WARN: failed to delete $name" -ForegroundColor Yellow
            }
        }
    } finally {
        $ErrorActionPreference = $old
    }
}

Remove-StaleReleaseAssets $tag $version

Write-Host ""
Write-Host "Release published: $tag" -ForegroundColor Green
if (-not $NoPause) { Pause }
exit 0
