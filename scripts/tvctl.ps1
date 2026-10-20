$ErrorActionPreference = 'Stop'
$app = Join-Path (Split-Path -Parent $PSScriptRoot) 'apps\tvctl'
$bin = Join-Path $app 'bin\tvctl.exe'
$go = Get-Command go -ErrorAction SilentlyContinue
$stale = -not (Test-Path $bin)
if (-not $stale -and $go) {
    $built = (Get-Item $bin).LastWriteTime
    $stale = [bool](Get-ChildItem (Join-Path $app 'cmd'), (Join-Path $app 'internal'), (Join-Path $app 'go.mod') -Recurse -File |
        Where-Object { $_.LastWriteTime -gt $built } | Select-Object -First 1)
}
if ($stale) {
    if (-not $go) { throw 'tvctl: install Go (https://go.dev/dl) or put a release binary at apps\tvctl\bin\tvctl.exe' }
    Push-Location $app
    try { go build -trimpath -ldflags=-s -o $bin ./cmd/tvctl; if ($LASTEXITCODE -ne 0) { throw 'go build failed' } }
    finally { Pop-Location }
}
& $bin @args
exit $LASTEXITCODE
