<#
.SYNOPSIS
  Para a apresentação no PC (banco, backend e túnel) sem apagar dados.

.DESCRIPTION
  docker compose stop: os contêineres param e o volume do banco de apresentação continua intacto. Para voltar, rode
  .\iniciar-apresentacao.ps1. Para apagar os dados, use .\resetar-demonstracao.ps1.

.EXAMPLE
  .\parar-apresentacao.ps1
#>
param()

. (Join-Path $PSScriptRoot 'comum.ps1')

try {
    Testar-Docker
    Definir-VariaveisDoCompose
    if ((Invocar-Compose 'stop') -ne 0) { throw 'docker compose stop falhou.' }
    Escrever-Ok 'apresentação parada; os dados continuam no volume medcare_apresentacao_pgdata'
} catch {
    Escrever-Erro $_.Exception.Message
    exit 1
}
