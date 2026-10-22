<#
.SYNOPSIS
  Registra en Microsoft Entra ID lo mínimo para CloudOrders: app API (scopes orders.read/orders.write),
  app SPA (cliente público, PKCE), usuario de prueba, y escribe los IDs en .env y frontend/.env.

.DESCRIPTION
  Requisitos: Azure CLI y una sesión iniciada por TI (az login --allow-no-subscriptions) con un usuario que
  pueda registrar aplicaciones y crear usuarios en el tenant. El script NO inicia sesión ni evade MFA.
  Es idempotente: reutiliza las apps si ya existen. Los IDs de tenant/cliente NO son secretos; la contraseña del
  usuario de prueba se guarda en .secrets/ (ignorado por git) y nunca se imprime.

.EXAMPLE
  .\infra\entra\setup-entra.ps1 -SpaRedirectUris 'http://localhost:5173/'
  .\infra\entra\setup-entra.ps1 -SpaRedirectUris 'http://localhost:5173/','https://<frontend-cloud-run>/'
#>
param(
    [string[]]$SpaRedirectUris = @('http://localhost:5173/'),
    [string]$TestUserName = 'cloudorders.tester',
    [switch]$SkipTestUser
)
$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$graph = 'https://graph.microsoft.com/v1.0'

function Invoke-Graph([string]$Method, [string]$Path, $Body) {
    $azArgs = @('rest', '--method', $Method, '--url', "$graph$Path", '--headers', 'Content-Type=application/json')
    $tmp = $null
    if ($null -ne $Body) {
        $tmp = New-TemporaryFile
        ($Body | ConvertTo-Json -Depth 12 -Compress) | Set-Content -Path $tmp -Encoding utf8
        $azArgs += @('--body', "@$tmp")
    }
    try {
        $out = & az @azArgs 2>&1
        if ($LASTEXITCODE -ne 0) { throw "Graph $Method $Path falló: $out" }
        if ($out) { return ($out | Out-String | ConvertFrom-Json) }
    } finally { if ($tmp) { Remove-Item $tmp -Force -ErrorAction SilentlyContinue } }
}

$tenantId = az account show --query tenantId -o tsv 2>$null
if (-not $tenantId) { throw 'No hay sesión. Ejecuta primero: az login --allow-no-subscriptions' }
Write-Host "Tenant: $tenantId"

function Get-OrCreateApp([string]$Name, $Body) {
    $found = (Invoke-Graph GET "/applications?`$filter=displayName eq '$Name'").value | Select-Object -First 1
    if ($found) { Write-Host "App '$Name' ya existe (se reutiliza)."; return $found }
    Write-Host "Creando app '$Name'..."
    return Invoke-Graph POST '/applications' $Body
}
function Ensure-ServicePrincipal([string]$AppId) {
    $sp = (Invoke-Graph GET "/servicePrincipals?`$filter=appId eq '$AppId'").value | Select-Object -First 1
    if (-not $sp) { Invoke-Graph POST '/servicePrincipals' @{ appId = $AppId } | Out-Null }
}

# 1) API
$readId = [guid]::NewGuid().ToString(); $writeId = [guid]::NewGuid().ToString()
$api = Get-OrCreateApp 'CloudOrders API' @{ displayName = 'CloudOrders API'; signInAudience = 'AzureADMyOrg' }
$apiAppId = $api.appId
$existingScopes = @($api.api.oauth2PermissionScopes)
if ($existingScopes.Count -ge 2) {
    $readId = ($existingScopes | Where-Object value -eq 'orders.read').id
    $writeId = ($existingScopes | Where-Object value -eq 'orders.write').id
}
function New-Scope($Id, $Value, $Desc) {
    @{ id = $Id; value = $Value; type = 'User'; isEnabled = $true
       adminConsentDisplayName = $Desc; adminConsentDescription = $Desc
       userConsentDisplayName = $Desc; userConsentDescription = $Desc }
}

# 2) SPA
$resource = @(@{ resourceAppId = $apiAppId; resourceAccess = @(@{ id = $readId; type = 'Scope' }, @{ id = $writeId; type = 'Scope' }) })
$spa = Get-OrCreateApp 'CloudOrders SPA' @{
    displayName = 'CloudOrders SPA'; signInAudience = 'AzureADMyOrg'
    spa = @{ redirectUris = $SpaRedirectUris }; requiredResourceAccess = $resource }
$spaAppId = $spa.appId
$uris = @(@($spa.spa.redirectUris) + $SpaRedirectUris | Where-Object { $_ } | Select-Object -Unique)
Invoke-Graph PATCH "/applications/$($spa.id)" @{ spa = @{ redirectUris = $uris }; requiredResourceAccess = $resource } | Out-Null

# 3) API: URI de aplicación, tokens v2, scopes y SPA preautorizada (consentimiento ya concedido para la SPA)
$apiBase = @{ requestedAccessTokenVersion = 2
    oauth2PermissionScopes = @(
        (New-Scope $readId 'orders.read' 'Leer pedidos de CloudOrders'),
        (New-Scope $writeId 'orders.write' 'Crear pedidos en CloudOrders')) }
Invoke-Graph PATCH "/applications/$($api.id)" @{ identifierUris = @("api://$apiAppId"); api = $apiBase } | Out-Null
# Graph exige que los scopes ya existan antes de preautorizar la SPA: segundo PATCH.
$apiBase.preAuthorizedApplications = @(@{ appId = $spaAppId; delegatedPermissionIds = @($readId, $writeId) })
Invoke-Graph PATCH "/applications/$($api.id)" @{ api = $apiBase } | Out-Null
Ensure-ServicePrincipal $apiAppId
Ensure-ServicePrincipal $spaAppId

# 4) Usuario de prueba
if (-not $SkipTestUser) {
    $domain = (Invoke-Graph GET '/domains').value | Where-Object isDefault | Select-Object -First 1 -ExpandProperty id
    $upn = "$TestUserName@$domain"
    $user = (Invoke-Graph GET "/users?`$filter=userPrincipalName eq '$upn'").value | Select-Object -First 1
    if (-not $user) {
        $chars = 'abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789!#$%'
        $pwd = (-join (1..20 | ForEach-Object { $chars[(Get-Random -Maximum $chars.Length)] })) + 'aA1!'
        Invoke-Graph POST '/users' @{
            accountEnabled = $true; displayName = 'CloudOrders Tester'; mailNickname = $TestUserName
            userPrincipalName = $upn
            passwordProfile = @{ forceChangePasswordNextSignIn = $false; password = $pwd } } | Out-Null
        New-Item -ItemType Directory -Force (Join-Path $root '.secrets') | Out-Null
        Set-Content -Path (Join-Path $root '.secrets\entra-test-user.txt') -Value "usuario: $upn`ncontraseña: $pwd" -Encoding utf8
        Write-Host "Usuario de prueba creado: $upn (contraseña en .secrets\entra-test-user.txt, no se imprime)."
    } else { Write-Host "Usuario $upn ya existe." }
}

# 5) Variables locales (no son secretos)
function Set-EnvValue([string]$File, [string]$Key, [string]$Value) {
    $lines = if (Test-Path $File) { @(Get-Content $File) } else { @() }
    $pattern = "^\s*$Key\s*="
    if ($lines -match $pattern) { $lines = $lines | ForEach-Object { if ($_ -match $pattern) { "$Key=$Value" } else { $_ } } }
    else { $lines += "$Key=$Value" }
    Set-Content -Path $File -Value $lines -Encoding utf8
}
$scopes = "api://$apiAppId/orders.read,api://$apiAppId/orders.write"
Set-EnvValue (Join-Path $root '.env') 'ENTRA_TENANT_ID' $tenantId
Set-EnvValue (Join-Path $root '.env') 'ENTRA_API_AUDIENCE' $apiAppId
Set-EnvValue (Join-Path $root 'frontend\.env') 'VITE_ENTRA_CLIENT_ID' $spaAppId
Set-EnvValue (Join-Path $root 'frontend\.env') 'VITE_ENTRA_TENANT_ID' $tenantId
Set-EnvValue (Join-Path $root 'frontend\.env') 'VITE_ENTRA_API_SCOPES' $scopes

Write-Host "`nListo. Tenant=$tenantId | API appId=$apiAppId | SPA appId=$spaAppId"
Write-Host "Siguiente: docker compose up -d order-service   y   cd frontend; npm run dev"
