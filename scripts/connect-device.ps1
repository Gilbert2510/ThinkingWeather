#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$Serial = '',
    [ValidateRange(1024, 65535)][int]$Port = 8080,
    [string]$AdbPath = '',
    [switch]$Install
)
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($AdbPath)) {
    $sdkCandidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
    $localProperties = Join-Path $projectDirectory 'local.properties'
    if (Test-Path -LiteralPath $localProperties) {
        $sdkLine = Get-Content -LiteralPath $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($sdkLine) { $sdkCandidates += $sdkLine.Substring(8).Replace('\:', ':').Replace('\\', '\') }
    }
    if ($env:LOCALAPPDATA) { $sdkCandidates += Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    $adbName = if ($IsWindows) { 'platform-tools/adb.exe' } else { 'platform-tools/adb' }
    foreach ($sdk in $sdkCandidates) {
        if ([string]::IsNullOrWhiteSpace($sdk)) { continue }
        $candidate = Join-Path $sdk $adbName
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { $AdbPath = $candidate; break }
    }
}
if ([string]::IsNullOrWhiteSpace($AdbPath) -or -not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) {
    throw 'adb was not found. Install Android SDK Platform Tools or pass -AdbPath.'
}
$deviceOutput = & $AdbPath devices
if ($LASTEXITCODE -ne 0) { throw 'adb could not enumerate connected devices.' }
$devices = @($deviceOutput | ForEach-Object {
    if ($_ -match '^(\S+)\s+device(?:\s|$)') { $Matches[1] }
})
if ($devices.Count -eq 0) {
    throw 'No authorized device. Connect USB, enable USB debugging, and accept the phone authorization prompt.'
}
if ([string]::IsNullOrWhiteSpace($Serial)) {
    if ($devices.Count -ne 1) { throw 'Multiple devices are connected. Select one with -Serial (see adb devices).' }
    $Serial = $devices[0]
}
if ($Serial -notin $devices) { throw 'The selected device is not connected or is not authorized.' }
try {
    $health = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/health" -TimeoutSec 5
} catch {
    throw 'The computer gateway is not running. Start scripts/start-backend.ps1 in another terminal first.'
}
if ($health.status -ne 'ok' -or $health.configured -ne $true) {
    throw 'The gateway is not configured. Complete backend/.env.local before connecting the device.'
}
& $AdbPath -s $Serial reverse "tcp:$Port" "tcp:$Port"
if ($LASTEXITCODE -ne 0) { throw 'USB port forwarding failed. Reconnect the device and retry.' }
Write-Output "Gateway forwarding ready: device 127.0.0.1:$Port -> computer 127.0.0.1:$Port."
if ($Install) {
    $apk = Join-Path $projectDirectory 'app/build/outputs/apk/debug/app-debug.apk'
    if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { throw 'Build :app:assembleDebug first.' }
    & $AdbPath -s $Serial install -r $apk
    if ($LASTEXITCODE -ne 0) { throw 'Debug APK installation failed. No application was uninstalled.' }
    & $AdbPath -s $Serial shell am start -n 'com.thinkingweather.android.debug/com.thinkingweather.android.MainActivity'
    if ($LASTEXITCODE -ne 0) { throw 'The installed debug application could not be launched.' }
}
Write-Output "Build debug with WEATHER_DEBUG_BASE_URL=http://127.0.0.1:$Port"
Write-Output 'Keep the computer gateway and adb connection active. Repeat after reconnecting or restarting the phone.'
Write-Output 'Independent use without a computer requires a deployed HTTPS gateway in WEATHER_BASE_URL.'
