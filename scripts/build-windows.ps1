param(
    [Parameter(Mandatory=$true)][string]$Sdk,
    [Parameter(Mandatory=$true)][string]$Java,
    [string]$BuildRoot = (Join-Path $env:TEMP 'CrewCamHybridBuild'),
    [string[]]$Tasks = @(':app:testDebugUnitTest', ':app:assembleDebug', ':app:lintDebug')
)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$checkout = Join-Path $BuildRoot 'source'
[IO.Directory]::CreateDirectory($checkout) | Out-Null
# Stage precisely the files Gradle reads. Use a fresh snapshot if any input has been removed.
$inputs = @('build.gradle.kts','settings.gradle.kts','gradle.properties','gradlew','gradlew.bat',
    'scripts/build-local.sh','gradle/wrapper/gradle-wrapper.jar','gradle/wrapper/gradle-wrapper.properties',
    'app/build.gradle.kts','core/build.gradle.kts')
foreach ($module in @('app','core')) {
    $inputs += Get-ChildItem -LiteralPath (Join-Path $project "$module/src") -File -Recurse |
        ForEach-Object { [IO.Path]::GetRelativePath($project, $_.FullName).Replace('\','/') }
}
foreach ($file in @('app/google-services.json','app/gradle.lockfile','core/gradle.lockfile')) {
    if (Test-Path -LiteralPath (Join-Path $project $file)) { $inputs += $file }
}
$index = Join-Path $checkout 'source-inputs.txt'
if (Test-Path -LiteralPath $index) {
    foreach ($old in (Get-Content -LiteralPath $index)) {
        if ($old -notin $inputs) { throw "A staged input was removed. Use a fresh BuildRoot: $old" }
    }
}
foreach ($rel in $inputs) {
    $src = Join-Path $project $rel; $dst = Join-Path $checkout $rel
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($dst)) | Out-Null
    if (!(Test-Path -LiteralPath $dst) -or ((Get-FileHash -LiteralPath $src).Hash -ne (Get-FileHash -LiteralPath $dst).Hash)) {
        [IO.File]::Copy($src, $dst, $true)
    }
}
$inputs | Set-Content -LiteralPath $index -Encoding utf8
('sdk.dir=' + $Sdk.Replace('\','/')) | Set-Content -LiteralPath (Join-Path $checkout 'local.properties') -Encoding utf8
$env:JAVA_HOME = $Java.Replace('\','/'); $env:ANDROID_HOME = $Sdk
Push-Location $checkout
try {
    & 'C:/Program Files/Git/bin/bash.exe' scripts/build-local.sh --console=plain --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' @Tasks
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Output "Verified build directory: $checkout"
} finally { Pop-Location }
