<#
.SYNOPSIS
  Apaga o banco de APRESENTAÇÃO e recria os dados de demonstração do zero.

.DESCRIPTION
  Remove só o volume medcare_apresentacao_pgdata (nunca o medcare_postgres do desenvolvimento), sobe a pilha de novo
  e roda preparar-demonstracao.ps1. Pede confirmação: digite APAGAR (ou use -Confirmar em automação).
  Os aparelhos já pareados precisam parear de novo com os códigos novos.

.PARAMETER Confirmar
  Pula a pergunta de confirmação.

.PARAMETER MinutosAteAPrimeiraDose
  Repassado ao preparar-demonstracao.ps1 (padrão 8, mínimo 6).

.EXAMPLE
  .\resetar-demonstracao.ps1
#>
param(
    [switch] $Confirmar,
    [ValidateRange(6, 600)] [int] $MinutosAteAPrimeiraDose = 8
)

. (Join-Path $PSScriptRoot 'comum.ps1')

try {
    Testar-Docker
    Definir-VariaveisDoCompose
    if (-not $Confirmar) {
        Write-Host 'Isto APAGA todos os dados do banco de apresentação (contas, pacientes, histórico).' -ForegroundColor Yellow
        $resposta = Read-Host 'Digite APAGAR para continuar'
        if ($resposta -cne 'APAGAR') { Write-Host 'Nada foi apagado.'; exit 0 }
    }
    if ((Invocar-Compose 'down') -ne 0) { throw 'docker compose down falhou.' }
    $preferencia = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { docker volume rm medcare_apresentacao_pgdata 2>&1 | Out-Null } finally { $ErrorActionPreference = $preferencia }
    Escrever-Ok 'volume do banco de apresentação apagado'

    & (Join-Path $PSScriptRoot 'iniciar-apresentacao.ps1') -SemBuild
    if ($LASTEXITCODE -ne 0) { throw 'a pilha não voltou (veja acima).' }
    & (Join-Path $PSScriptRoot 'preparar-demonstracao.ps1') -MinutosAteAPrimeiraDose $MinutosAteAPrimeiraDose
    if ($LASTEXITCODE -ne 0) { throw 'os dados de demonstração não foram criados (veja acima).' }
} catch {
    Escrever-Erro $_.Exception.Message
    exit 1
}
