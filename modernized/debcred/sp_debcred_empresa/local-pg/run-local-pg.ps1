# Runs sp-debcred-empresa on http://localhost:8080 against the LOCAL PostgreSQL test environment
# (profile local-pg). Not production: production calls the COBIS procedures in Sybase ASE.
#
#   powershell -ExecutionPolicy Bypass -File local-pg\run-local-pg.ps1            # reset data, build if needed, run
#   powershell -ExecutionPolicy Bypass -File local-pg\run-local-pg.ps1 -KeepData  # do not reload debcred-local.sql
#   powershell -ExecutionPolicy Bypass -File local-pg\run-local-pg.ps1 -Rebuild   # rebuild the jar first
#
# Connection values come from %USERPROFILE%\.modernize\debcred\debcred-pg.env, outside the repository.
# This script never prints them. Stop the service with Ctrl+C.
param([switch]$Rebuild, [switch]$KeepData)

$ErrorActionPreference = 'Stop'
$module = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$envFile = Join-Path $env:USERPROFILE '.modernize\debcred\debcred-pg.env'
if (-not (Test-Path $envFile)) { throw "Missing $envFile" }
foreach ($line in Get-Content $envFile) {
    if ($line -match '^\s*([A-Z_][A-Z0-9_]*)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process') }
}

if (-not (docker ps --filter 'name=^seguridad-admin-pg$' --format '{{.Names}}')) {
    docker start seguridad-admin-pg | Out-Null; Start-Sleep -Seconds 3
}
if (-not $KeepData) {
    Write-Host 'Loading local-pg/debcred-local.sql (tables, simulated procedures, fictitious data)...'
    Get-Content (Join-Path $module 'local-pg\debcred-local.sql') -Raw |
        docker exec -i seguridad-admin-pg psql -q -v ON_ERROR_STOP=1 -U debcred_app -d debcred_local 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'Loading the local schema failed.' }
}

$jar = Get-ChildItem (Join-Path $module 'target') -Filter 'sp-debcred-empresa-*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
if ($Rebuild -or -not $jar) {
    $mvn = Join-Path $env:USERPROFILE 'tools\apache-maven-3.9.9\bin\mvn.cmd'
    if (-not (Test-Path $mvn)) { $mvn = 'mvn' }
    Push-Location $module
    try { & $mvn -o -q package -DskipTests; if ($LASTEXITCODE -ne 0) { throw 'Build failed.' } } finally { Pop-Location }
    $jar = Get-ChildItem (Join-Path $module 'target') -Filter 'sp-debcred-empresa-*.jar' |
            Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
}

Write-Host 'sp-debcred-empresa (local PostgreSQL) on http://localhost:8080/debitos-empresa'
java -jar $jar.FullName --spring.profiles.active=local-pg
