# Copy to the Forge server directory beside run.bat.
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$forgeArgs = 'libraries/net/minecraftforge/forge/1.21.1-52.1.5/win_args.txt'
if (-not (Test-Path -LiteralPath $forgeArgs) -or -not (Test-Path -LiteralPath 'user_jvm_args.txt')) { throw 'Missing Forge 52.1.5 argument files.' }
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java.exe' }
$marker = Join-Path $PSScriptRoot '.server-restart-request'
try {
    do {
        if (Test-Path -LiteralPath $marker) { Remove-Item -LiteralPath $marker -Force }
        $env:KSP_RESTART_TOKEN = [Guid]::NewGuid().ToString()
        & $java '@user_jvm_args.txt' "@$forgeArgs" nogui
        $exitCode = $LASTEXITCODE
        $requested = $false
        if (Test-Path -LiteralPath $marker) {
            $requested = ((Get-Content -LiteralPath $marker -Raw).Trim() -ceq $env:KSP_RESTART_TOKEN)
            Remove-Item -LiteralPath $marker -Force
        }
        if ($exitCode -ne 0 -or -not $requested) { break }
        Write-Host 'Clean restart requested. Starting Forge again in 5 seconds.'
        Start-Sleep -Seconds 5
    } while ($true)
} finally {
    Remove-Item Env:KSP_RESTART_TOKEN -ErrorAction SilentlyContinue
}
