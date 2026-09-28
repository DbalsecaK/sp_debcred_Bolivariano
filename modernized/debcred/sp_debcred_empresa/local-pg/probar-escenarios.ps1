# Runs the local test scenarios against the service started with run-local-pg.ps1 (profile local-pg).
# Each scenario reloads debcred-local.sql (fictitious data), optionally makes one simulated procedure
# fail (sim.falla), posts a request and prints the reply and what stayed in the database.
# Usage, with the service running in another terminal:
#   powershell -ExecutionPolicy Bypass -File local-pg\probar-escenarios.ps1
param([string]$Url = 'http://localhost:8080/debitos-empresa')

$ErrorActionPreference = 'Continue'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$base = Get-Content (Join-Path $here 'request-transcli.json') -Raw

function Sql([string]$query) {
    docker exec -i seguridad-admin-pg psql -q -U debcred_app -d debcred_local -At -c $query
}

function Reload {
    Get-Content (Join-Path $here 'debcred-local.sql') -Raw |
        docker exec -i seguridad-admin-pg psql -q -v ON_ERROR_STOP=1 -U debcred_app -d debcred_local | Out-Null
}

function Call([string]$body) {
    try {
        $r = Invoke-WebRequest -Uri $Url -Method Post -ContentType 'application/json' -Body $body -UseBasicParsing
        return "HTTP $($r.StatusCode)  $($r.Content)"
    } catch {
        if ($_.Exception.Response) { return "HTTP $([int]$_.Exception.Response.StatusCode)  (rechazado, no se ejecuto nada)" }
        return "SIN RESPUESTA: el servicio no esta corriendo en $Url"
    }
}

function Scenario([string]$title, [string]$falla, [string]$body) {
    Reload
    if ($falla) { Sql "insert into sim.falla values $falla" | Out-Null }
    Write-Host ''
    Write-Host "=== $title" -ForegroundColor Cyan
    Write-Host ('Respuesta:  ' + (Call $body))
    Write-Host 'Llamadas que quedaron grabadas (lo revertido no aparece):'
    $rows = Sql ("select procedimiento || coalesce('  estado=' || (argumentos->>'iEstProceso'), '')" +
                 " || coalesce('  valor=' || (argumentos->>'iValorMov'), '') from sim.llamada order by id")
    if ($rows) { $rows | ForEach-Object { "   - $_" } } else { '   (ninguna)' }
    Write-Host ('Cabecera de la orden 123456: ' + (Sql 'select te_estado_proceso from cobis.bp_total_orden where te_orden_banco = 123456'))
}

Scenario '1. Debito exitoso (TRANSCLI con comision y notificacion)' '' $base
Scenario '2. Falla el debito en sp_ndc_ahcc (salida A)' "('sp_ndc_ahcc', 201045, 0)" $base
Scenario '3. Falla la comision en sp_grb_comision (salida B)' "('sp_grb_comision', 0, 122010)" $base
Scenario '4. Falla la notificacion en sp_eventos (revierte el debito)' "('sp_eventos', 30001, 0)" $base
Scenario '5. Campo JSON desconocido' '' ($base -replace '"iTipctaEmp": 3', '"iTipctaEmp": 3, "iCampoRaro": 1')
Scenario '6. Monto fuera de rango (1E999999999)' '' ($base -replace '"iValorComision": 0', '"iValorComision": 1E999999999')
Reload
Write-Host ''
Write-Host 'Listo. Datos recargados a su estado inicial.'
