<#
.SYNOPSIS
  Sobe a apresentação no PC: PostgreSQL, backend e túnel ngrok com o domínio estático, e confere tudo.

.DESCRIPTION
  1. Confere o Docker Desktop e os arquivos em C:\Users\arthu\segredos (só os nomes; nenhum valor aparece).
  2. Na primeira vez, cria o .env.apresentacao com senha do banco e JWT aleatórios.
  3. docker compose up -d --build (compila o backend do código local; a primeira vez leva alguns minutos).
  4. Espera a saúde local (http://127.0.0.1:8085) e a pública (https://<NGROK_DOMAIN>).
  5. Imprime o resumo: PRONTO: https://<domínio> | backend | banco | push | túnel.
  Pode rodar quantas vezes quiser: o que já está no ar continua, e os dados ficam no volume.

.PARAMETER SemBuild
  Não recompila a imagem do backend (sobe a última compilada). Mais rápido quando o código não mudou.

.EXAMPLE
  .\iniciar-apresentacao.ps1
.EXAMPLE
  .\iniciar-apresentacao.ps1 -SemBuild
#>
param([switch] $SemBuild)

. (Join-Path $PSScriptRoot 'comum.ps1')

try {
    Testar-Docker
    Escrever-Ok 'Docker em execução'
    $dominio = Obter-DominioNgrok
    Escrever-Ok "túnel configurado para https://$dominio"
    Definir-VariaveisDoCompose
    if ($env:FCM_ENABLED -eq 'true') { Escrever-Ok 'credencial do Firebase Admin encontrada (push ligado)' }
    else { Escrever-Aviso "sem *adminsdk*.json em $($script:PastaSegredos): push DESLIGADO (os avisos ficam só no registro)" }
    if ($env:EMAIL_ENABLED -eq 'true') { Escrever-Ok 'smtp.env encontrado (e-mail com os códigos ligado)' }
    else { Escrever-Aviso "sem smtp.env em $($script:PastaSegredos): e-mail DESLIGADO (os códigos ficam no log; veja .\ultimo-codigo-email.ps1)" }
    if (Garantir-ArquivoEnv) { Escrever-Ok '.env.apresentacao criado com segredos aleatórios (não exibidos)' }
    else { Escrever-Ok '.env.apresentacao existente reaproveitado' }

    Write-Host 'Subindo os contêineres (a primeira compilação do backend leva alguns minutos)...'
    $argumentos = @('up', '-d', '--remove-orphans')
    if (-not $SemBuild) { $argumentos += '--build' }
    if ((Invocar-Compose @argumentos) -ne 0) { throw 'docker compose up falhou (veja as linhas acima).' }

    if (-not (Aguardar-Saude $script:UrlLocal 240 'O backend local')) { throw 'backend fora do ar; veja: docker compose -p medcare-apresentacao logs backend' }
    Escrever-Ok "backend e banco saudáveis em $($script:UrlLocal)"
    $publica = "https://$dominio"
    if (-not (Aguardar-Saude $publica 90 'A URL pública')) { throw 'túnel fora do ar; veja: docker compose -p medcare-apresentacao logs ngrok' }
    Escrever-Ok "URL pública respondendo em $publica"

    $push = if ($env:FCM_ENABLED -eq 'true') { 'push OK' } else { 'push DESLIGADO' }
    $email = if ($env:EMAIL_ENABLED -eq 'true') { 'e-mail OK' } else { 'e-mail NO LOG' }
    Write-Host ''
    Write-Host "PRONTO: $publica | backend OK | banco OK | $push | $email | túnel OK" -ForegroundColor Green
    Write-Host 'Próximo passo: .\preparar-demonstracao.ps1 (dados e códigos de pareamento).'
} catch {
    Escrever-Erro $_.Exception.Message
    exit 1
}
