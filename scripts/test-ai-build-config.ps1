param()

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$localFile = Join-Path $root 'ai.local.properties'
if (Test-Path -LiteralPath $localFile) {
    throw 'ai.local.properties already exists. Run this test in a checkout without a personal AI configuration; the test will not overwrite it.'
}
$fields = @('ARK_API_KEY', 'ARK_TEXT_MODEL', 'ARK_VISION_MODEL', 'VOLCENGINE_SPEECH_APP_ID', 'VOLCENGINE_SPEECH_ACCESS_TOKEN', 'DEEPSEEK_API_KEY')
$environment = @{}
$fixture = @{}
foreach ($field in $fields) {
    $environment[$field] = [Environment]::GetEnvironmentVariable($field, 'Process')
    [Environment]::SetEnvironmentVariable($field, $null, 'Process')
    $fixture[$field] = 'fixture-' + $field.ToLowerInvariant()
}

function Write-Fixture([hashtable]$Values) {
    $lines = $fields | ForEach-Object { $_ + '=' + $Values[$_] }
    [IO.File]::WriteAllText($localFile, ($lines -join "`n"), [Text.UTF8Encoding]::new($false))
}

function Invoke-GradleCase([string]$Name, [string[]]$Arguments, [bool]$ShouldSucceed, [string]$ExpectedText) {
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = & (Join-Path $root 'gradlew.bat') @Arguments --console=plain 2>&1 | Out-String
    $code = $LASTEXITCODE
    $ErrorActionPreference = $previousPreference
    foreach ($value in @($fixture.Values) + @('example-override-value', 'example-environment-value')) {
        if ($output.Contains($value)) { throw "$Name printed a configuration value (redacted)." }
    }
    if (($code -eq 0) -ne $ShouldSucceed -or -not $output.Contains($ExpectedText)) {
        throw "$Name failed: exit $code; expected success=$ShouldSucceed and message '$ExpectedText'. Gradle output withheld to avoid exposing local credentials."
    }
    Write-Output "PASS: $Name"
}

function Read-BuildConfig([string]$Variant) {
    $directory = Join-Path $root "app/build/generated/source/buildConfig/$Variant"
    $file = Get-ChildItem -LiteralPath $directory -Filter BuildConfig.java -Recurse | Select-Object -First 1
    if (-not $file) { throw "Missing $Variant BuildConfig." }
    return Get-Content -LiteralPath $file.FullName -Raw
}

function Assert-Field([string]$Config, [string]$Name, [string]$Expected) {
    if (-not $Config.Contains('public static final String ' + $Name + ' = "' + $Expected + '";')) {
        throw "Unexpected generated $Name (values redacted)."
    }
}

function Test-PrivateFileGuard {
    $previousIndex = $env:GIT_INDEX_FILE
    $indexPath = Join-Path $root ('build/ai-config-guard-' + [guid]::NewGuid().ToString('N') + '.index')
    try {
        $env:GIT_INDEX_FILE = $indexPath
        git read-tree --empty
        if ($LASTEXITCODE -ne 0) { throw 'Cannot create the isolated test index.' }
        $blob = git hash-object -w -- ai.local.properties.example
        if ($LASTEXITCODE -ne 0) { throw 'Cannot hash the public template.' }
        git update-index --add --cacheinfo "100644,$blob,ai.local.properties.example"
        if ($LASTEXITCODE -ne 0) { throw 'Cannot stage the template in the test index.' }
        & (Join-Path $PSScriptRoot 'check-secrets.ps1') -Staged
        if ($LASTEXITCODE -ne 0) { throw 'Public template must pass credential scanning.' }
        git update-index --add --cacheinfo "100644,$blob,ai.local.properties"
        if ($LASTEXITCODE -ne 0) { throw 'Cannot stage the duplicate-blob fixture.' }
        $previousPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        $output = powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'check-secrets.ps1') -Staged 2>&1 | Out-String
        $code = $LASTEXITCODE
        $ErrorActionPreference = $previousPreference
        if ($code -eq 0 -or -not $output.Contains('ai.local.properties : local configuration must not be tracked')) {
            throw 'Private file escaped scanning because its content matched the example.'
        }
        Write-Output 'PASS: Forced private-file staging is rejected even with identical template content'
    } finally {
        $env:GIT_INDEX_FILE = $previousIndex
        if (Test-Path -LiteralPath $indexPath) { Remove-Item -LiteralPath $indexPath }
    }
}

Push-Location $root
try {
    $blank = @{}
    foreach ($field in $fields) { $blank[$field] = '' }
    Write-Fixture $blank
    Invoke-GradleCase 'Missing AI configuration is actionable' @('checkAiConfiguration') $false 'AI configuration is incomplete'
    Invoke-GradleCase 'Keyless builds remain supported' @(':app:generateDebugBuildConfig') $true 'BUILD SUCCESSFUL'
    $config = Read-BuildConfig 'debug'
    foreach ($field in $fields) { Assert-Field $config $field '' }

    $placeholders = @{}
    foreach ($field in $fields) { $placeholders[$field] = 'your-' + $field.ToLowerInvariant() }
    Write-Fixture $placeholders
    Invoke-GradleCase 'Template placeholders are rejected' @('checkAiConfiguration') $false 'AI configuration is incomplete'

    Write-Fixture $fixture
    Invoke-GradleCase 'Project-local configuration is loaded' @('checkAiConfiguration', ':app:generateDebugBuildConfig', ':app:generateReleaseBuildConfig') $true 'AI configuration fields are complete'
    $debug = Read-BuildConfig 'debug'
    foreach ($field in $fields) { Assert-Field $debug $field $fixture[$field] }
    $release = Read-BuildConfig 'release'
    foreach ($field in @('ARK_API_KEY', 'VOLCENGINE_SPEECH_APP_ID', 'VOLCENGINE_SPEECH_ACCESS_TOKEN', 'DEEPSEEK_API_KEY')) {
        Assert-Field $release $field ''
    }
    Write-Output 'PASS: Release strips credentials even with a complete local configuration'

    [Environment]::SetEnvironmentVariable('ARK_API_KEY', 'example-environment-value', 'Process')
    Invoke-GradleCase 'Environment overrides the local file' @(':app:generateDebugBuildConfig') $true 'BUILD SUCCESSFUL'
    Assert-Field (Read-BuildConfig 'debug') 'ARK_API_KEY' 'example-environment-value'
    Invoke-GradleCase 'Explicit Gradle properties override the environment' @(':app:generateDebugBuildConfig', '-PARK_API_KEY=example-override-value') $true 'BUILD SUCCESSFUL'
    Assert-Field (Read-BuildConfig 'debug') 'ARK_API_KEY' 'example-override-value'
    Invoke-GradleCase 'Explicit blank overrides populated environment and file' @(':app:generateDebugBuildConfig', '-PARK_API_KEY=') $true 'BUILD SUCCESSFUL'
    Assert-Field (Read-BuildConfig 'debug') 'ARK_API_KEY' ''
    [Environment]::SetEnvironmentVariable('ARK_API_KEY', $null, 'Process')

    $fallback = $fixture.Clone()
    $fallback['ARK_TEXT_MODEL'] = ''
    Write-Fixture $fallback
    Invoke-GradleCase 'Text model can reuse the vision model' @('checkAiConfiguration', ':app:generateDebugBuildConfig') $true 'AI configuration fields are complete'
    Assert-Field (Read-BuildConfig 'debug') 'ARK_TEXT_MODEL' $fixture['ARK_VISION_MODEL']

    Remove-Item -LiteralPath $localFile
    $propertyArguments = @($fields | ForEach-Object { '-Dorg.gradle.project.' + $_ + '=' + $fixture[$_] })
    Invoke-GradleCase 'Absent local file preserves Gradle-property fallback' (@('checkAiConfiguration', ':app:generateDebugBuildConfig') + $propertyArguments) $true 'AI configuration fields are complete'
    $config = Read-BuildConfig 'debug'
    foreach ($field in $fields) { Assert-Field $config $field $fixture[$field] }

    Write-Fixture $fixture
    $bomText = [char]0xFEFF + '# ' + [char]0x5B66 + "`n" + [IO.File]::ReadAllText($localFile)
    [IO.File]::WriteAllText($localFile, $bomText, [Text.UTF8Encoding]::new($false))
    Invoke-GradleCase 'UTF-8 BOM and comments are supported' @('checkAiConfiguration', ':app:generateDebugBuildConfig') $true 'AI configuration fields are complete'
    Assert-Field (Read-BuildConfig 'debug') 'ARK_API_KEY' $fixture['ARK_API_KEY']
    [IO.File]::WriteAllText($localFile, ('ARK_API_KEY=' + $fixture['ARK_API_KEY'] + '\uZZZZ'), [Text.UTF8Encoding]::new($false))
    Invoke-GradleCase 'Malformed escapes fail without exposing values' @('checkAiConfiguration') $false 'Invalid ai.local.properties syntax'

    foreach ($quoted in @('""', '"your-api-key"', "'your-api-key'")) {
        $invalid = $fixture.Clone()
        $invalid['ARK_API_KEY'] = $quoted
        Write-Fixture $invalid
        Invoke-GradleCase 'Quoted blanks and placeholders are rejected' @('checkAiConfiguration') $false 'AI configuration is incomplete'
    }
    Test-PrivateFileGuard
    Write-Output 'AI build configuration regression tests passed. No cloud services were called.'
} finally {
    foreach ($field in $fields) { [Environment]::SetEnvironmentVariable($field, $environment[$field], 'Process') }
    if (Test-Path -LiteralPath $localFile) { Remove-Item -LiteralPath $localFile }
    Pop-Location
}
