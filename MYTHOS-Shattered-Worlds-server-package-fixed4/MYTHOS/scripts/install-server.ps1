$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Root = Split-Path -Parent $PSScriptRoot
$Server = Join-Path $Root 'server'
$Tools = Join-Path $Root '.tools'
$ForgeVersion = '1.21.1-52.1.5'
$SpongeVersion = '1.21.1-52.1.5-12.0.3'
$ForgeInstallerName = "forge-$ForgeVersion-installer.jar"
$ForgeInstaller = Join-Path $Tools $ForgeInstallerName
$ForgeUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/$ForgeVersion/$ForgeInstallerName"
$SpongeJarName = "spongeforge-$SpongeVersion-universal.jar"
$SpongeJar = Join-Path (Join-Path $Server 'mods') $SpongeJarName
$SpongeUrl = "https://repo.spongepowered.org/repository/maven-releases/org/spongepowered/spongeforge/$SpongeVersion/$SpongeJarName"

New-Item -ItemType Directory -Force -Path $Server, (Join-Path $Server 'mods'), (Join-Path $Server 'config'), $Tools | Out-Null

Write-Host '== MYTHOS server installer ==' -ForegroundColor Cyan
Write-Host "Minecraft: 1.21.1 | Forge: $ForgeVersion | SpongeForge: $SpongeVersion"

$JavaCommand = Get-Command java -ErrorAction SilentlyContinue
if (-not $JavaCommand) { throw 'Java was not found on PATH. Install a 64-bit Java 21 runtime first.' }

# java -version writes to stderr. In Windows PowerShell 5.1, native stderr
# can become an ErrorRecord and terminate a script using ErrorActionPreference=Stop.
# Route it through cmd.exe so the version text is captured as ordinary stdout.
$JavaVersionOutput = (& $env:ComSpec /d /c 'java -version 2>&1' | Out-String)
$JavaVersionExitCode = $LASTEXITCODE
if ($JavaVersionExitCode -ne 0) { throw "java -version failed with exit code $JavaVersionExitCode. Check the Java installation and PATH." }
if ($JavaVersionOutput -notmatch 'version "(?<major>\d+)') { throw "Could not determine Java version: $JavaVersionOutput" }
$JavaMajor = [int]$Matches.major
if ($JavaMajor -ne 21) { throw "This Forge/Sponge target is pinned to Java 21. Detected Java $JavaMajor. Select a 64-bit Java 21 runtime and retry." }
Write-Host "Java detected: $JavaMajor" -ForegroundColor Green

if (-not (Test-Path $ForgeInstaller)) {
    Write-Host 'Downloading matching Forge installer...'
    Invoke-WebRequest -Uri $ForgeUrl -OutFile $ForgeInstaller
}
if ((Get-Item $ForgeInstaller).Length -lt 1000000) { throw 'Forge installer download is unexpectedly small; refusing to execute it.' }

$RunBat = Join-Path $Server 'run.bat'
if (-not (Test-Path $RunBat)) {
    Write-Host 'Installing dedicated Forge server files...'
    Push-Location $Server
    try { & java -jar $ForgeInstaller --installServer; if ($LASTEXITCODE -ne 0) { throw "Forge installer exited with code $LASTEXITCODE" } }
    finally { Pop-Location }
}
if (-not (Test-Path $RunBat)) { throw 'Forge installer completed but did not create run.bat. Check the Forge installer output.' }

if (-not (Test-Path $SpongeJar)) {
    Write-Host 'Downloading official recommended SpongeForge build...'
    Invoke-WebRequest -Uri $SpongeUrl -OutFile $SpongeJar
}
if ((Get-Item $SpongeJar).Length -lt 1000000) { throw 'SpongeForge download is unexpectedly small; refusing to install it.' }

$Mods = Join-Path $Server 'mods'
if (-not (Get-ChildItem -LiteralPath $Mods -Filter 'LuckPerms*.jar' -File)) {
    $LuckPermsName = 'LuckPerms-Sponge-5.5.91.jar'
    $LuckPermsDownload = Join-Path $Tools "$LuckPermsName.download"
    $LuckPermsUrl = "https://download.luckperms.net/1678/sponge/loader/$LuckPermsName"
    $ExpectedHash = 'E91A3BCB03C2171B9E17ECF6756FA940250C0D2DF12E6A58EB281FB3482FE5F2'
    Write-Host 'Downloading LuckPerms for Sponge...'
    try {
        Invoke-WebRequest -Uri $LuckPermsUrl -OutFile $LuckPermsDownload -UseBasicParsing
        if ((Get-FileHash -LiteralPath $LuckPermsDownload -Algorithm SHA256).Hash -ne $ExpectedHash) {
            throw 'The LuckPerms download checksum does not match.'
        }
        Move-Item -LiteralPath $LuckPermsDownload -Destination (Join-Path $Mods $LuckPermsName)
    } finally {
        if (Test-Path -LiteralPath $LuckPermsDownload) {
            Remove-Item -LiteralPath $LuckPermsDownload
        }
    }
} else {
    Write-Host 'Existing LuckPerms installation found in server\mods.'
}

$LuckPermsData = Join-Path $Server 'luckperms'
New-Item -ItemType Directory -Force -Path $LuckPermsData | Out-Null
$RankPreset = Join-Path $PSScriptRoot 'permissions/mythos-ranks.json'
$RankImport = Join-Path $LuckPermsData 'mythos-ranks.json.gz'
$RankTemp = "$RankImport.tmp"
try {
    $Output = [IO.File]::Create($RankTemp)
    try {
        $Gzip = [IO.Compression.GZipStream]::new($Output, [IO.Compression.CompressionMode]::Compress, $true)
        try {
            $InputFile = [IO.File]::OpenRead($RankPreset)
            try { $InputFile.CopyTo($Gzip) }
            finally { $InputFile.Dispose() }
        } finally { $Gzip.Dispose() }
    } finally { $Output.Dispose() }
    Move-Item -LiteralPath $RankTemp -Destination $RankImport -Force
} finally {
    if (Test-Path -LiteralPath $RankTemp) {
        Remove-Item -LiteralPath $RankTemp
    }
}

$Eula = Join-Path $Server 'eula.txt'
if (-not (Test-Path $Eula)) { Set-Content -Path $Eula -Value 'eula=false' -Encoding ASCII }

$Properties = Join-Path $Server 'server.properties'
if (-not (Test-Path $Properties)) {
    @(
      'motd=MYTHOS: Shattered Worlds'
      'server-port=25565'
      'online-mode=true'
      'enable-command-block=false'
      'spawn-protection=0'
      'view-distance=8'
      'simulation-distance=6'
      'max-players=50'
      'white-list=true'
      'enforce-whitelist=true'
    ) | Set-Content -Path $Properties -Encoding ASCII
}

Write-Host ''
Write-Host 'Forge, SpongeForge and LuckPerms setup complete.' -ForegroundColor Green
Write-Host 'To load the MYTHOS rank presets, run lp import "mythos-ranks.json.gz" in the running server console.'
Write-Host 'The first server start will generate the world and configuration.'
Write-Host 'The Minecraft EULA remains eula=false until you accept it yourself.'
Write-Host "Edit: $Eula"
Write-Host ''
Write-Host 'Building the MYTHOS plugin is a separate step. Run build.bat after this installer.'
