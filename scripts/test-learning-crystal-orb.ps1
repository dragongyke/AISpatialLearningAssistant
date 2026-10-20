param([switch]$CompileRenderer, [string]$PreviewDirectory)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$cache = Join-Path $HOME '.gradle/caches/modules-2/files-2.1'
$classes = (Join-Path $root 'app/build/tmp/kotlin-classes/debug').Replace('\', '/')
$output = Join-Path $root 'app/build/orb-refinement-tests'

function CachedJar([string]$relative, [string]$name) {
    $jar = Get-ChildItem -LiteralPath (Join-Path $cache $relative) -Recurse -Filter $name |
        Select-Object -First 1
    if (-not $jar) { throw "Missing cached dependency: $relative/$name" }
    $jar.FullName
}

if (-not (Test-Path (Join-Path $classes 'com/pico/spatial/sample/spatialvideo/ui/LearningVoiceControlState.class'))) {
    throw 'Requires an existing debug Kotlin compilation for the unchanged voice-state enum.'
}
$stdlib = CachedJar 'org.jetbrains.kotlin/kotlin-stdlib/2.1.20' 'kotlin-stdlib-2.1.20.jar'
$junit = CachedJar 'junit/junit/4.13.2' 'junit-4.13.2.jar'
$hamcrest = CachedJar 'org.hamcrest/hamcrest-core/1.3' 'hamcrest-core-1.3.jar'
$annotations = CachedJar 'org.jetbrains/annotations/13.0' 'annotations-13.0.jar'
$compiler = @(
    (CachedJar 'org.jetbrains.kotlin/kotlin-compiler-embeddable/2.1.20' 'kotlin-compiler-embeddable-2.1.20.jar')
    $stdlib
    $annotations
    (CachedJar 'org.jetbrains.intellij.deps/trove4j' 'trove4j-1.0.20200330.jar')
    (CachedJar 'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.8.0' 'kotlinx-coroutines-core-jvm-1.8.0.jar')
) -join ';'
$classpath = @($stdlib, $junit, $hamcrest, $annotations, $classes)
$sourceDir = Join-Path $root 'app/src/main/java/com/pico/spatial/sample/spatialvideo/ui'
$testDir = Join-Path $root 'app/src/test/java/com/pico/spatial/sample/spatialvideo/ui'
$sources = @(Get-ChildItem -LiteralPath $sourceDir -Filter 'LearningCrystalOrb*.kt' |
    Where-Object { $CompileRenderer -or $_.Name -ne 'LearningCrystalOrb.kt' } |
    Select-Object -ExpandProperty FullName)
$tests = @(Get-ChildItem -LiteralPath $testDir -Filter 'LearningCrystalOrb*Test.kt')

if ($PreviewDirectory) {
    $classpath += CachedJar 'org.json/json/20240303' 'json-20240303.jar'
    $sources += Join-Path $PSScriptRoot 'export-learning-crystal-orb.kt'
}

if ($CompileRenderer) {
    $classpath += CachedJar 'androidx.annotation/annotation-jvm/1.9.1' 'annotation-jvm-1.9.1.jar'
    $transforms = Join-Path $HOME '.gradle/caches/8.13/transforms'
    $classpath += @(Get-ChildItem -LiteralPath $transforms -Recurse -Filter '*-api.jar' |
        Where-Object { $_.Name -match '^(core-6|foundation-6|annotation)' } |
        Select-Object -ExpandProperty FullName)
}
New-Item -ItemType Directory -Path $output -Force | Out-Null
& java -cp $compiler org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect `
    -jvm-target 11 "-Xfriend-paths=$classes" -classpath ($classpath -join ';') `
    -d $output @sources @($tests.FullName)
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -cp ((@($output) + $classpath) -join ';') org.junit.runner.JUnitCore `
    @($tests | ForEach-Object { 'com.pico.spatial.sample.spatialvideo.ui.' + $_.BaseName })
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($PreviewDirectory) {
    & java -cp ((@($output) + $classpath) -join ';') `
        com.pico.spatial.sample.spatialvideo.ui.Export_learning_crystal_orbKt $PreviewDirectory
}
exit $LASTEXITCODE
