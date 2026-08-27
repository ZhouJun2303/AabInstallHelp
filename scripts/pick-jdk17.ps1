$ErrorActionPreference = "Continue"

function Get-JavaMajor([string] $JavaExe) {
    if (-not (Test-Path -LiteralPath $JavaExe)) { return 0 }
    $text = & cmd.exe /c "`"$JavaExe`" -version 2>&1"
    $text = if ($text -is [array]) { $text -join "`n" } else { [string]$text }
    if ($text -match 'version "1\.(\d+)') { return [int]$Matches[1] }
    if ($text -match 'version "(\d+)') { return [int]$Matches[1] }
    return 0
}

function Resolve-Jdk17Home {
    $dirs = New-Object System.Collections.Generic.List[string]
    foreach ($d in @(
        $env:JAVA_HOME,
        "$env:ProgramFiles\Java\jdk-21",
        "$env:ProgramFiles\Java\jdk-17",
        "$env:ProgramFiles\Android\Android Studio\jbr"
    )) {
        if (-not [string]::IsNullOrWhiteSpace($d)) { $dirs.Add($d) }
    }
    $javaRoot = Join-Path $env:ProgramFiles "Java"
    if (Test-Path $javaRoot) {
        Get-ChildItem $javaRoot -Directory -ErrorAction SilentlyContinue | ForEach-Object { $dirs.Add($_.FullName) }
    }
    foreach ($jdkHome in $dirs) {
        $exe = Join-Path $jdkHome "bin\java.exe"
        if ((Get-JavaMajor $exe) -ge 17) {
            return [System.IO.Path]::GetFullPath($jdkHome)
        }
    }
    return $null
}

$jdk = Resolve-Jdk17Home
if (-not $jdk) {
    [Console]::Error.WriteLine("ERROR: Android Gradle Plugin 8.7 needs JDK 17 or newer.")
    [Console]::Error.WriteLine("JAVA_HOME is currently: $($env:JAVA_HOME)")
    [Console]::Error.WriteLine("Install JDK 17/21 or Android Studio, then rerun.")
    exit 1
}
Write-Output $jdk
exit 0
