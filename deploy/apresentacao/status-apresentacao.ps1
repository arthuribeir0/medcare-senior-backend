<#
.SYNOPSIS
  Mostra a saúde da apresentação no PC numa tabela.

.DESCRIPTION
  Contêineres (estado e saúde), /actuator/health local e público, último evento de push gravado no banco de
  apresentação, relógio do PC comparado ao UTC da internet, espaço livre em disco e a versão da imagem do backend.
  Só lê: não muda nada. Nenhum segredo aparece.

.EXAMPLE
  .\status-apresentacao.ps1
#>
param()

. (Join-Path $PSScriptRoot 'comum.ps1')

$linhas = New-Object Collections.Generic.List[object]
function Linha([string] $item, [string] $situacao, [string] $detalhe) {
    $linhas.Add([pscustomobject]@{ Item = $item; Situacao = $situacao; Detalhe = $detalhe })
}

try {
    Testar-Docker
    Linha 'Docker Desktop' 'OK' ''
} catch {
    Linha 'Docker Desktop' 'FALHA' $_.Exception.Message
    $linhas | Format-Table -AutoSize -Wrap
    exit 1
}

$dominio = $null
try { $dominio = Obter-DominioNgrok; Definir-VariaveisDoCompose } catch { Linha 'Arquivo do ngrok' 'FALHA' $_.Exception.Message }

foreach ($servico in 'postgres', 'backend', 'ngrok') {
    $id = (docker ps -a -q --filter "label=com.docker.compose.project=medcare-apresentacao" --filter "label=com.docker.compose.service=$servico" | Select-Object -First 1)
    if (-not $id) { Linha "contêiner $servico" 'FALHA' 'não existe (rode iniciar-apresentacao.ps1)'; continue }
    $estado = docker inspect --format '{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}sem healthcheck{{end}}|{{.RestartCount}}' $id
    $partes = $estado -split '\|'
    $ok = $partes[0] -eq 'running' -and $partes[1] -in 'healthy', 'sem healthcheck'
    Linha "contêiner $servico" $(if ($ok) { 'OK' } else { 'FALHA' }) "$($partes[0]), $($partes[1]), reinícios: $($partes[2])"
}

Linha 'saúde local' $(if (Saude-Ok $script:UrlLocal) { 'OK' } else { 'FALHA' }) "$($script:UrlLocal)/actuator/health"
if ($dominio) {
    Linha 'saúde pública (túnel)' $(if (Saude-Ok "https://$dominio") { 'OK' } else { 'FALHA' }) "https://$dominio/actuator/health"
}

Linha 'push (FCM)' $(if ($env:FCM_ENABLED -eq 'true') { 'OK' } else { 'DESLIGADO' }) $(if ($env:FCM_ENABLED -eq 'true') { 'credencial do Firebase Admin montada' } else { 'sem *adminsdk*.json em segredos' })
try {
    $evento = Invocar-Sql "SELECT coalesce(to_char(ultima_tentativa, 'DD/MM HH24:MI'), 'sem tentativa') || ' ' || status || coalesce(' ' || motivo_falha, '') FROM evento_notificacao ORDER BY id DESC LIMIT 1;"
    Linha 'último evento de push' 'INFO' $(if ("$evento".Trim()) { "$evento".Trim() } else { 'nenhum ainda' })
} catch { Linha 'último evento de push' 'FALHA' 'banco de apresentação inacessível' }

try {
    $resposta = Invoke-WebRequest -Uri 'https://www.google.com' -Method Head -UseBasicParsing -TimeoutSec 10
    $utc = [DateTime]::Parse($resposta.Headers['Date']).ToUniversalTime()
    $diferenca = [int] [Math]::Abs(((Get-Date).ToUniversalTime() - $utc).TotalSeconds)
    Linha 'relógio do PC' $(if ($diferenca -le 60) { 'OK' } else { 'FALHA' }) "diferença de $diferenca s para o UTC da internet (hora local $(Get-Date -Format 'HH:mm:ss'))"
} catch { Linha 'relógio do PC' 'AVISO' 'sem internet para comparar' }

$disco = Get-PSDrive -Name ($env:SystemDrive.TrimEnd(':'))
$livreGb = [Math]::Round($disco.Free / 1GB, 1)
Linha 'disco livre' $(if ($livreGb -ge 5) { 'OK' } else { 'AVISO' }) "$livreGb GB em $($env:SystemDrive)"

# Template sem aspas: o PowerShell 5.1 as remove ao chamar programas externos
$ErrorActionPreference = 'Continue'
$imagem = docker image inspect medcare-senior-backend:apresentacao --format '{{json .}}' 2>$null | ConvertFrom-Json
if ($imagem) {
    $compilada = ([DateTime]::Parse($imagem.Created)).ToLocalTime().ToString('dd/MM HH:mm')
    Linha 'imagem do backend' 'INFO' "commit $($imagem.Config.Labels.'medcare.commit'), compilada em $compilada"
} else { Linha 'imagem do backend' 'FALHA' 'não compilada (rode iniciar-apresentacao.ps1)' }

$linhas | Format-Table -AutoSize -Wrap
if ($linhas | Where-Object { $_.Situacao -eq 'FALHA' }) { exit 1 }
