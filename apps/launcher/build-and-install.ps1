param(
    [string]$TvHost = $env:TV_HOST,
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$InstallFlags = @()
)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
Set-Location $PSScriptRoot

function Find-AndroidStudioJdk {
    $studios = Get-ChildItem 'C:\Program Files\Android' -Directory -ErrorAction SilentlyContinue
    $studios |
        ForEach-Object { Join-Path $_.FullName 'jbr' } |
        Where-Object { Test-Path "$_\bin\java.exe" } |
        Select-Object -First 1
}

if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $jdk = Find-AndroidStudioJdk
    if (-not $jdk) { throw 'Set JAVA_HOME to a JDK 17+ (Android Studio ships one in jbr)' }
    $env:JAVA_HOME = $jdk
}

$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
if (-not (Test-Path local.properties)) {
    "sdk.dir=$($sdk -replace '\\', '\\')" | Out-File -Encoding ascii local.properties
}

.\gradlew.bat assembleRelease -q --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed' }

$tvctl = Join-Path $PSScriptRoot '..\..\scripts\tvctl.ps1'
$apk = 'app\build\outputs\apk\release\km-launcher.apk'
& $tvctl install @InstallFlags $apk
exit $LASTEXITCODE
