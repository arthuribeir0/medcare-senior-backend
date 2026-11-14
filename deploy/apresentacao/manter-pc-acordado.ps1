<#
.SYNOPSIS
  Impede o PC de suspender ou hibernar quando ligado na tomada (o servidor da apresentação é o próprio PC).

.DESCRIPTION
  No plano de energia ATUAL, só para a tomada (AC): suspensão e hibernação em "nunca". O monitor continua apagando
  normalmente, e a bateria (DC) não muda. Mostra os valores anteriores e o comando exato que os restaura.

.PARAMETER Reverter
  Restaura os tempos informados (em minutos) com -SuspensaoMinutos e -HibernacaoMinutos.

.EXAMPLE
  .\manter-pc-acordado.ps1
.EXAMPLE
  .\manter-pc-acordado.ps1 -Reverter -SuspensaoMinutos 30 -HibernacaoMinutos 180
#>
param(
    [switch] $Reverter,
    [int] $SuspensaoMinutos = 30,
    [int] $HibernacaoMinutos = 0
)

$ErrorActionPreference = 'Stop'

# Valor AC atual (em minutos) de uma configuração do subgrupo de suspensão do plano ativo
function Obter-MinutosAC([string] $alias) {
    $saida = powercfg /query SCHEME_CURRENT SUB_SLEEP $alias
    $linha = $saida | Where-Object { $_ -match '0x[0-9a-fA-F]{8}' } | Select-Object -Last 2 | Select-Object -First 1
    if ($linha -match '(0x[0-9a-fA-F]{8})') { [int] ([Convert]::ToInt64($Matches[1], 16) / 60) } else { $null }
}

if ($Reverter) {
    powercfg /change standby-timeout-ac $SuspensaoMinutos
    powercfg /change hibernate-timeout-ac $HibernacaoMinutos
    Write-Host "Restaurado na tomada: suspensão $SuspensaoMinutos min, hibernação $HibernacaoMinutos min (0 = nunca)."
    exit 0
}

$suspensaoAntes = Obter-MinutosAC 'STANDBYIDLE'
$hibernacaoAntes = Obter-MinutosAC 'HIBERNATEIDLE'
powercfg /change standby-timeout-ac 0
powercfg /change hibernate-timeout-ac 0
Write-Host 'Na tomada, o PC não suspende nem hiberna mais (o monitor ainda pode apagar).' -ForegroundColor Green
Write-Host "Valores anteriores: suspensão $suspensaoAntes min, hibernação $hibernacaoAntes min (0 = nunca)."
Write-Host 'Para reverter exatamente:'
Write-Host "  .\manter-pc-acordado.ps1 -Reverter -SuspensaoMinutos $suspensaoAntes -HibernacaoMinutos $hibernacaoAntes"
