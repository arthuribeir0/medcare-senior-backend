<#
.SYNOPSIS
  Mostra os últimos códigos de confirmação de e-mail e de troca de senha (só com o envio de e-mail desligado).

.DESCRIPTION
  Sem smtp.env na pasta de segredos, o backend da apresentação escreve no log o código que iria por e-mail. Este script
  mostra as últimas linhas desses códigos, para testar o cadastro e o "Esqueci minha senha" no app sem um servidor SMTP.
  Com o smtp.env, os códigos vão para o e-mail de verdade e não aparecem aqui.

.PARAMETER Quantos
  Quantos códigos mostrar, dos mais recentes (padrão 3).

.EXAMPLE
  .\ultimo-codigo-email.ps1
#>
param([int] $Quantos = 3)

. (Join-Path $PSScriptRoot 'comum.ps1')

try {
    Testar-Docker
    Definir-VariaveisDoCompose
    if ($env:EMAIL_ENABLED -eq 'true') {
        Escrever-Aviso 'o envio de e-mail está ligado (smtp.env): os códigos vão para o e-mail, não para o log.'
    }
    $preferencia = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $linhas = docker compose -f $script:ArquivoCompose -p medcare-apresentacao logs --no-log-prefix backend 2>$null |
            Select-String -SimpleMatch '[E-MAIL DESLIGADO]' | Select-Object -Last $Quantos
    } finally { $ErrorActionPreference = $preferencia }
    if (-not $linhas) {
        Escrever-Aviso 'nenhum código no log ainda (cadastre uma conta ou peça "Esqueci minha senha" no app).'
        exit 0
    }
    foreach ($linha in $linhas) {
        $texto = "$linha"
        $inicio = $texto.IndexOf('Código de')
        Write-Host ($(if ($inicio -ge 0) { $texto.Substring($inicio) } else { $texto }))
    }
} catch {
    Escrever-Erro $_.Exception.Message
    exit 1
}
