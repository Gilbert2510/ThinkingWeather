[CmdletBinding()]
param(
    [string]$ConfigFile = "",
    [string]$JdkHome = "",
    [switch]$UseInstalledDistribution
)
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ConfigFile)) {
    $ConfigFile = Join-Path $projectDirectory 'backend\.env.local'
}
if (-not (Test-Path -LiteralPath $ConfigFile -PathType Leaf)) {
    throw "Create backend/.env.local from backend/.env.example first. Keep it local."
}
$allowedNames = @('BIND_ADDRESS','PORT','QWEATHER_API_HOST','QWEATHER_PRIVATE_KEY_FILE',
    'QWEATHER_KEY_ID','QWEATHER_DEVELOPER_ID','QWEATHER_PROJECT_ID','QWEATHER_API_KEY',
    'QWEATHER_WEATHER_API','QWEATHER_ENABLE_WARNINGS','QWEATHER_ENABLE_AIR','QWEATHER_ENABLE_INDICES',
    'GATEWAY_PER_PEER_RPM','GATEWAY_GLOBAL_RPM','QWEATHER_UPSTREAM_RPM')
foreach ($entry in Get-Content -LiteralPath $ConfigFile -Encoding UTF8) {
    $line = $entry.Trim()
    if ($line.Length -eq 0 -or $line.StartsWith('#')) { continue }
    if ($line -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') { throw 'Invalid .env.local line. Use NAME=value.' }
    $entryName = $Matches[1]
    $entryValue = $Matches[2].Trim()
    if ($entryName -notin $allowedNames) { throw 'Unknown configuration name in .env.local.' }
    if ($entryValue.Length -gt 1 -and (($entryValue.StartsWith('"') -and $entryValue.EndsWith('"')) -or
        ($entryValue.StartsWith("'") -and $entryValue.EndsWith("'")))) {
        $entryValue = $entryValue.Substring(1, $entryValue.Length - 2)
    }
    [Environment]::SetEnvironmentVariable($entryName, $entryValue, 'Process')
}
if ([string]::IsNullOrWhiteSpace($JdkHome)) { $JdkHome = $env:JAVA_HOME }
if ([string]::IsNullOrWhiteSpace($JdkHome) -and (Test-Path -LiteralPath 'C:\software\AndroidStudio\jbr\bin\java.exe')) {
    $JdkHome = 'C:\software\AndroidStudio\jbr'
}
if ([string]::IsNullOrWhiteSpace($JdkHome) -or -not (Test-Path -LiteralPath (Join-Path $JdkHome 'bin\java.exe'))) {
    throw 'Pass -JdkHome with your Android Studio jbr directory (JDK 25).'
}
$env:JAVA_HOME = $JdkHome
# Config is read as plain data, never executed. Values and credentials are never printed.
Push-Location $projectDirectory
try {
    if ($UseInstalledDistribution) {
        $launcher = Join-Path $projectDirectory 'backend\build\install\backend\bin\backend.bat'
        if (-not (Test-Path -LiteralPath $launcher)) { throw 'Run :backend:installDist first.' }
        & $launcher
    } else {
        & (Join-Path $projectDirectory 'gradlew.bat') -p backend run --console=plain
    }
    if ($LASTEXITCODE -ne 0) { throw 'Backend startup failed. See the generic diagnostic above.' }
} finally { Pop-Location }
