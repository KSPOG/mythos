$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$Root = Split-Path -Parent $PSScriptRoot
$Tools = Join-Path $Root '.tools'
$GradleVersion = '8.10.2'
$GradleHome = Join-Path $Tools "gradle-$GradleVersion"
$GradleZip = Join-Path $Tools "gradle-$GradleVersion-bin.zip"
$GradleExe = Join-Path $GradleHome 'bin\gradle.bat'
New-Item -ItemType Directory -Force -Path $Tools | Out-Null

# Java writes version information to stderr. Capture it via cmd.exe so
# Windows PowerShell 5.1 does not convert native stderr into a terminating error.
$JavaOutput = (& $env:ComSpec /d /c 'java -version 2>&1' | Out-String)
$JavaExitCode = $LASTEXITCODE
if ($JavaExitCode -ne 0 -or $JavaOutput -notmatch 'version "(?<major>\d+)') { throw 'Java is required. Install Java 21 and add it to PATH.' }
$JavaMajor = [int]$Matches.major
if ($JavaMajor -ne 21) { throw "Java 21 is required to build MYTHOS for this target. Detected Java $JavaMajor. Select a Java 21 runtime." }
Write-Host "Java detected: $JavaMajor" -ForegroundColor Green

if (-not (Test-Path $GradleExe)) {
    if (-not (Test-Path $GradleZip)) {
        Write-Host "Downloading Gradle $GradleVersion..."
        Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip" -OutFile $GradleZip
    }
    Write-Host 'Extracting Gradle...'
    Expand-Archive -Path $GradleZip -DestinationPath $Tools -Force
}

Push-Location $Root
try {
    & $GradleExe --no-daemon clean build
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE" }
    $Jar = Join-Path $Root 'mythos-sponge\build\libs\Mythos-0.1.0-SNAPSHOT.jar'
    if (-not (Test-Path $Jar)) { throw "Build succeeded but expected plugin JAR was not found: $Jar" }
    $Mods = Join-Path $Root 'server\mods'
    New-Item -ItemType Directory -Force -Path $Mods | Out-Null
    Copy-Item $Jar (Join-Path $Mods 'Mythos.jar') -Force
    Write-Host "Installed MYTHOS plugin: $(Join-Path $Mods 'Mythos.jar')" -ForegroundColor Green
    Write-Host "MYTHOS resource pack: $(Join-Path $Root 'build\resource-packs\MYTHOS-resource-pack.zip')" -ForegroundColor Green
} finally { Pop-Location }
