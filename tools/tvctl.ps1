$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$bin = Join-Path $root 'tools\bin\tvctl.exe'
$go = Get-Command go -ErrorAction SilentlyContinue
$stale = -not (Test-Path $bin)
if (-not $stale -and $go) {
    $built = (Get-Item $bin).LastWriteTime
    $stale = [bool](Get-ChildItem (Join-Path $root 'cmd'), (Join-Path $root 'internal'), (Join-Path $root 'go.mod') -Recurse -File |
        Where-Object { $_.LastWriteTime -gt $built } | Select-Object -First 1)
}
if ($stale) {
    if (-not $go) { throw 'tvctl: install Go (https://go.dev/dl) or put a release binary at tools\bin\tvctl.exe' }
    Push-Location $root
    try { go build -trimpath -ldflags=-s -o $bin ./cmd/tvctl; if ($LASTEXITCODE -ne 0) { throw 'go build failed' } }
    finally { Pop-Location }
}
& $bin @args
exit $LASTEXITCODE
