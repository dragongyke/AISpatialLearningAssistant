param([switch]$Staged, [switch]$AllHistory, [switch]$SelfTest)

$ErrorActionPreference = 'Stop'
$knownSecrets = @()
$localConfigs = @(
    (Join-Path $env:USERPROFILE '.gradle/gradle.properties'),
    (Join-Path (Split-Path $PSScriptRoot -Parent) 'ai.local.properties')
)
foreach ($localConfig in $localConfigs) {
    if (-not (Test-Path -LiteralPath $localConfig)) { continue }
    foreach ($line in Get-Content -LiteralPath $localConfig) {
        if ($line -match '^\s*(?:DEEPSEEK_API_KEY|ARK_API_KEY|VOLCENGINE_SPEECH_ACCESS_TOKEN|VOLCENGINE_SPEECH_SECRET_KEY)\s*=\s*(.+)$') {
            if ($Matches[1].Trim().Length -ge 16) { $knownSecrets += $Matches[1].Trim() }
        }
    }
}

function Test-SecretText([string]$Text) {
    foreach ($secret in $knownSecrets) {
        if ($Text.Contains($secret)) { return $true }
    }
    if ($Text -match '(?<![A-Za-z0-9_])(?:sk-[A-Za-z0-9]{24,}|ark-[A-Za-z0-9-]{32,})') { return $true }
    if ($Text -match '-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----') { return $true }
    $assignments = [regex]::Matches($Text, '(?im)(?:api[_-]?key|access[_-]?token|secret[_-]?key)["'']?\s*[:=]\s*["'']?(?<value>[A-Za-z0-9_+/=-]{16,})')
    foreach ($match in $assignments) {
        $value = $match.Groups['value'].Value
        if ($value -notmatch '^(?:(?:sk-|ark-)?your[-_]|replace[-_]|example[-_]|placeholder|BuildConfig|configuration)') { return $true }
    }
    return $false
}

if ($SelfTest) {
    if (-not (Test-SecretText ('sk-' + ('a' * 32)))) { throw 'Token detection failed.' }
    if (-not (Test-SecretText ('ARK_API_KEY=' + ('X' * 32)))) { throw 'Assignment detection failed.' }
    if (Test-SecretText 'ARK_API_KEY=REPLACE_ME') { throw 'Placeholder false positive.' }
    if (Test-SecretText 'val key = BuildConfig.ARK_API_KEY') { throw 'Code reference false positive.' }
    if (Test-SecretText ('ARK_API_KEY=sk-' + 'your-deepseek-api-key')) { throw 'Legacy placeholder false positive.' }
    Write-Output 'Secret scanner self-test passed.'
    exit 0
}

$root = git rev-parse --show-toplevel
if ($LASTEXITCODE -ne 0) { throw 'Run inside a Git repository.' }
Push-Location $root
$process = $null
try {
    $objects = @{}
    $paths = New-Object 'System.Collections.Generic.HashSet[string]'
    if ($AllHistory) {
        foreach ($line in (git rev-list --objects --all)) {
            if ($line -match '^([a-f0-9]+) (.+)$') {
                $objects[$Matches[1]] = $Matches[2]
                [void]$paths.Add($Matches[2])
            }
        }
    } else {
        foreach ($line in (git ls-files --stage)) {
            if ($line -match '^\d+ ([a-f0-9]+) \d\t(.+)$') {
                $objects[$Matches[1]] = $Matches[2]
                [void]$paths.Add($Matches[2])
            }
        }
    }

    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = 'git'
    $info.Arguments = 'cat-file --batch'
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $process = [System.Diagnostics.Process]::Start($info)
    $stream = $process.StandardOutput.BaseStream

    function Read-Blob([string]$ObjectId) {
        $process.StandardInput.WriteLine($ObjectId)
        $process.StandardInput.Flush()
        $header = New-Object System.Text.StringBuilder
        while (($byte = $stream.ReadByte()) -ne 10) {
            if ($byte -lt 0) { throw 'Unexpected end of Git object stream.' }
            [void]$header.Append([char]$byte)
        }
        $parts = $header.ToString().Split(' ')
        if ($parts.Length -ne 3) { throw 'Invalid Git object response.' }
        $bytes = New-Object byte[] ([int]$parts[2])
        $offset = 0
        while ($offset -lt $bytes.Length) {
            $read = $stream.Read($bytes, $offset, $bytes.Length - $offset)
            if ($read -le 0) { throw 'Incomplete Git object.' }
            $offset += $read
        }
        [void]$stream.ReadByte()
        return ,$bytes
    }

    $problems = New-Object 'System.Collections.Generic.List[string]'
    $checked = 0
    $commonGitDirectory = git rev-parse --git-common-dir
    Add-Type -AssemblyName System.IO.Compression
    # Check every filename before deduplicating blob content (a local file can match its template).
    foreach ($path in $paths) {
        if ($path -match '(?i)(^|/)(ai\.local\.properties|local.properties|secrets.properties|gradle-local.properties|\.env(?:\..+)?)$' -and $path -notmatch '\.(example|sample|template)$') {
            $problems.Add("$path : local configuration must not be tracked")
        }
        if ($path -match '(?i)\.(apk|aab|jks|keystore)$|(^|/)BuildConfig\.(java|class)$') {
            $problems.Add("$path : private configuration or build artifact must not be tracked")
        }
    }
    foreach ($entry in $objects.GetEnumerator()) {
        $path = $entry.Value
        if ($path -match '(?i)\.(apk|aab|jks|keystore)$|(^|/)BuildConfig\.(java|class)$') { continue }
        if ($path -match '(?i)\.(png|jpe?g|webp|tga|ktx|mp4|bundle|usdc|usdz|fbx|glb|bin)$') { continue }
        if ($AllHistory -or $Staged -or -not (Test-Path -LiteralPath $path)) {
            $bytes = Read-Blob $entry.Key
        } else { $bytes = [System.IO.File]::ReadAllBytes((Join-Path $root $path)) }
        if ($bytes.Length -lt 1024) {
            $pointer = [System.Text.Encoding]::UTF8.GetString($bytes)
            if ($pointer.StartsWith('version https://git-lfs.github.com/spec/v1') -and $pointer -match 'oid sha256:([a-f0-9]{64})') {
                $oid = $Matches[1]
                $lfsPath = Join-Path $commonGitDirectory ("lfs/objects/" + $oid.Substring(0, 2) + "/" + $oid.Substring(2, 2) + "/" + $oid)
                if (-not (Test-Path -LiteralPath $lfsPath)) { throw "Cannot scan missing LFS source object: $path ($oid). Fetch this object and retry." }
                $bytes = [System.IO.File]::ReadAllBytes($lfsPath)
            }
        }
        $checked++
        if ($path -match '(?i)\.(zip|jar|aar)$') {
            $memory = [System.IO.MemoryStream]::new($bytes, $false)
            $archive = [System.IO.Compression.ZipArchive]::new($memory)
            try {
                foreach ($item in $archive.Entries) {
                    if ($item.FullName -match '(?i)\.(zip|jar|aar|apk|aab)$') {
                        $problems.Add("$path!$($item.FullName) : nested archive requires an explicit security review")
                        continue
                    }
                    if ($item.FullName -notmatch '(?i)\.(kt|kts|java|class|dex|md|properties|json|ya?ml|xml|txt|ps1|py|js|sh|env)$') { continue }
                    if ($item.Length -gt 256MB) { throw "Archive entry exceeds scan limit: $path!$($item.FullName)" }
                    $reader = [System.IO.StreamReader]::new($item.Open())
                    try {
                        if (Test-SecretText ($reader.ReadToEnd())) { $problems.Add("$path!$($item.FullName) : credential detected [REDACTED]") }
                    } finally { $reader.Dispose() }
                }
            } finally { $archive.Dispose(); $memory.Dispose() }
        } elseif (Test-SecretText ([System.Text.Encoding]::UTF8.GetString($bytes))) {
            $problems.Add("$path : credential detected [REDACTED] (object $($entry.Key.Substring(0, 12)))")
        }
    }
    if ($problems.Count -gt 0) {
        $problems | Sort-Object -Unique | Write-Output
        throw 'Secret scan failed. Remove credentials and rotate exposed keys. No credential values were printed.'
    }
    Write-Output "Secret scan passed: $checked Git objects checked. Credential values were not printed."
} finally {
    if ($null -ne $process) {
        $process.StandardInput.Close()
        $process.WaitForExit()
        $process.Dispose()
    }
    Pop-Location
}
