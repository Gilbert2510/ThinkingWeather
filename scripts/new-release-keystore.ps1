#requires -Version 7.0
[CmdletBinding()]
param([string]$JdkHome = "")
$ErrorActionPreference = 'Stop'
$projectDirectory = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$signingDirectory = [IO.Path]::GetFullPath((Join-Path $projectDirectory 'signing'))
$storePath = Join-Path $signingDirectory 'release-keystore.p12'
$propertiesPath = Join-Path $signingDirectory 'keystore.properties'
if (-not $signingDirectory.StartsWith($projectDirectory + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The signing directory must be inside this project.'
}
if ((Test-Path -LiteralPath $storePath) -or (Test-Path -LiteralPath $propertiesPath)) {
    throw 'A release key or configuration already exists. It will not be overwritten.'
}
if ([string]::IsNullOrWhiteSpace($JdkHome)) { $JdkHome = $env:JAVA_HOME }
if ([string]::IsNullOrWhiteSpace($JdkHome)) { throw 'Set JAVA_HOME or pass -JdkHome with your JDK directory.' }
$keytoolRelative = if ($IsWindows) { 'bin/keytool.exe' } else { 'bin/keytool' }
$keytoolPath = Join-Path $JdkHome $keytoolRelative
if (-not (Test-Path -LiteralPath $keytoolPath)) { throw 'keytool was not found in the supplied JDK.' }
New-Item -ItemType Directory -Path $signingDirectory -Force | Out-Null
if ($IsWindows) {
    # Only the current Windows account and SYSTEM may read generated signing credentials.
    $owner = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $systemSid = [Security.Principal.SecurityIdentifier]::new('S-1-5-18')
    $access = [Security.AccessControl.DirectorySecurity]::new()
    $access.SetOwner($owner)
    $access.SetAccessRuleProtection($true, $false)
    $inheritance = [Security.AccessControl.InheritanceFlags]'ContainerInherit,ObjectInherit'
    foreach ($principal in @($owner, $systemSid)) {
        $access.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new(
            $principal, [Security.AccessControl.FileSystemRights]::FullControl,
            $inheritance, [Security.AccessControl.PropagationFlags]::None,
            [Security.AccessControl.AccessControlType]::Allow
        ))
    }
    Set-Acl -LiteralPath $signingDirectory -AclObject $access
}
$passwordBytes = [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
$storePassword = [Convert]::ToBase64String($passwordBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
[Environment]::SetEnvironmentVariable('TW_SIGN_STORE_PASSWORD', $storePassword, 'Process')
try {
    # Pass passwords through the child process environment, never through command-line arguments.
    $keytoolArguments = @(
        '-J-Duser.language=en', '-genkeypair', '-noprompt', '-keystore', $storePath, '-storetype', 'PKCS12',
        '-storepass:env', 'TW_SIGN_STORE_PASSWORD', '-keypass:env', 'TW_SIGN_STORE_PASSWORD',
        '-alias', 'thinkingweather-release', '-keyalg', 'RSA', '-keysize', '3072',
        '-sigalg', 'SHA256withRSA', '-validity', '10000',
        '-dname', 'CN=ThinkingWeather, OU=Android App'
    )
    & $keytoolPath @keytoolArguments
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed. No existing key has been overwritten.' }
    $configuration = @(
        '# Local release signing credentials. Never commit or share this file.'
        'storeFile=signing/release-keystore.p12'
        'storeType=PKCS12'
        ('storePassword=' + $storePassword)
        'keyAlias=thinkingweather-release'
        ('keyPassword=' + $storePassword)
    ) -join [Environment]::NewLine
    [IO.File]::WriteAllText($propertiesPath, $configuration + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
    if (-not $IsWindows) {
        & chmod 700 $signingDirectory
        & chmod 600 $storePath $propertiesPath
        if ($LASTEXITCODE -ne 0) { throw 'Restrict permissions on the generated signing files before use.' }
    }
    Write-Output 'Created signing/release-keystore.p12 and signing/keystore.properties. Back them up securely.'
} finally {
    [Environment]::SetEnvironmentVariable('TW_SIGN_STORE_PASSWORD', $null, 'Process')
    [Array]::Clear($passwordBytes, 0, $passwordBytes.Length)
    $storePassword = $null
}
