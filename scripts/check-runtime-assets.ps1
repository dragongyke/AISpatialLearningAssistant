param([string]$Ref = 'HEAD')

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    $tree = git rev-parse --verify "${Ref}^{tree}"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve the requested Git tree.' }
    $entries = @(git ls-tree -r -l $tree -- app/src/main/res app/src/main/assets)
    if ($LASTEXITCODE -ne 0 -or $entries.Count -eq 0) { throw 'Cannot inspect application resources.' }
    $pointers = @()
    foreach ($line in $entries) {
        if ($line -notmatch '^\d+ blob ([a-f0-9]+)\s+(\d+)\t(.+)$') { continue }
        $objectId = $Matches[1]
        $size = [long]$Matches[2]
        $path = $Matches[3]
        if ($size -gt 1024) { continue }
        $content = @(git cat-file blob $objectId)
        if ($LASTEXITCODE -ne 0) { throw "Cannot inspect resource: $path" }
        if ($content.Count -gt 0 -and $content[0] -eq 'version https://git-lfs.github.com/spec/v1') {
            $pointers += $path
        }
    }
    if ($pointers.Count -gt 0) {
        $pointers | ForEach-Object { Write-Output "LFS pointer in application resources: $_" }
        throw 'Plain Git checkouts cannot build a complete APK. Store application runtime resources as ordinary Git files.'
    }
    Write-Output "PASS: $($entries.Count) application resource entries contain no Git LFS pointers."
} finally {
    Pop-Location
}
