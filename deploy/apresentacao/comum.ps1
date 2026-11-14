<#
  Funções comuns dos scripts da apresentação no PC (carregado com ". $PSScriptRoot\comum.ps1").
  Nenhuma função imprime segredo: dos arquivos de segredo só se lê o NOME do arquivo e, do ngrok, só a linha
  NGROK_DOMAIN (o domínio é público: vai embutido no app).
#>

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$script:PastaApresentacao = $PSScriptRoot
$script:ArquivoCompose = Join-Path $PSScriptRoot 'docker-compose.apresentacao.yml'
$script:ArquivoEnv = Join-Path $PSScriptRoot '.env.apresentacao'
$script:PastaSegredos = if ($env:MEDCARE_SEGREDOS) { $env:MEDCARE_SEGREDOS } else { Join-Path $env:USERPROFILE 'segredos' }
$script:UrlLocal = 'http://127.0.0.1:8085'

function Escrever-Ok([string] $texto) { Write-Host "ok     $texto" -ForegroundColor Green }
function Escrever-Aviso([string] $texto) { Write-Host "AVISO  $texto" -ForegroundColor Yellow }
function Escrever-Erro([string] $texto) { Write-Host "ERRO   $texto" -ForegroundColor Red }

function Testar-Docker {
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $docker) { throw 'Docker não encontrado. Instale o Docker Desktop (winget install -e --id Docker.DockerDesktop).' }
    $preferencia = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { docker info --format '{{.ServerVersion}}' *> $null; $rodando = ($LASTEXITCODE -eq 0) }
    finally { $ErrorActionPreference = $preferencia }
    if (-not $rodando) { throw 'O Docker Desktop não está em execução. Abra o Docker Desktop, espere o ícone ficar verde e rode de novo.' }
}

# Arquivo do ngrok: ngrok.env (ou ngrok.env.txt, quando o Bloco de Notas acrescenta a extensão)
function Obter-ArquivoNgrok {
    foreach ($nome in 'ngrok.env', 'ngrok.env.txt') {
        $caminho = Join-Path $script:PastaSegredos $nome
        if (Test-Path $caminho) { return $caminho }
    }
    throw "Arquivo do ngrok não encontrado em $($script:PastaSegredos) (ngrok.env com NGROK_AUTHTOKEN e NGROK_DOMAIN)."
}

# Só a linha NGROK_DOMAIN; confere que o token existe sem lê-lo para a tela
function Obter-DominioNgrok {
    $arquivo = Obter-ArquivoNgrok
    $linhas = Get-Content -LiteralPath $arquivo
    if (-not ($linhas | Where-Object { $_ -match '^\s*NGROK_AUTHTOKEN\s*=\s*\S' })) { throw "NGROK_AUTHTOKEN ausente em $arquivo." }
    $linha = $linhas | Where-Object { $_ -match '^\s*NGROK_DOMAIN\s*=' } | Select-Object -First 1
    if (-not $linha) { throw "NGROK_DOMAIN ausente em $arquivo." }
    $dominio = ($linha -replace '^\s*NGROK_DOMAIN\s*=\s*', '').Trim().Trim('"').TrimEnd('/') -replace '^https?://', ''
    if ($dominio -notmatch '^[a-z0-9.-]+\.[a-z]{2,}$') { throw "NGROK_DOMAIN em $arquivo não parece um domínio (ex.: algo.ngrok-free.app, sem https://)." }
    $dominio
}

# Credencial do Firebase Admin (*adminsdk*.json); $null se não houver
function Obter-CredencialFirebase {
    if (-not (Test-Path $script:PastaSegredos)) { return $null }
    $arquivo = Get-ChildItem -LiteralPath $script:PastaSegredos -Filter '*adminsdk*.json' -File | Select-Object -First 1
    if ($arquivo) { $arquivo.FullName } else { $null }
}

# Servidor SMTP do envio dos códigos (smtp.env ou smtp.env.txt em segredos); $null se não houver. O arquivo vai inteiro
# para o contêiner (env_file); daqui só se lê o NOME.
function Obter-ArquivoSmtp {
    foreach ($nome in 'smtp.env', 'smtp.env.txt') {
        $caminho = Join-Path $script:PastaSegredos $nome
        if (Test-Path -LiteralPath $caminho) { return $caminho }
    }
    return $null
}

function Gerar-Segredo([int] $tamanho) {
    $alfabeto = [char[]] 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
    $bytes = New-Object byte[] ($tamanho)
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    # 62 símbolos: o resto da divisão de um byte tem um viés desprezível para senha e chave de 40 a 64 caracteres
    -join ($bytes | ForEach-Object { $alfabeto[$_ % $alfabeto.Length] })
}

# Cria o .env.apresentacao a partir do exemplo, com senha do banco e JWT aleatórios (escritos direto no arquivo)
function Garantir-ArquivoEnv {
    if (Test-Path $script:ArquivoEnv) { return $false }
    $exemplo = Get-Content -LiteralPath (Join-Path $script:PastaApresentacao '.env.apresentacao.example')
    $conteudo = $exemplo | ForEach-Object {
        if ($_ -match '^POSTGRES_PASSWORD=$') { "POSTGRES_PASSWORD=$(Gerar-Segredo 40)" }
        elseif ($_ -match '^JWT_SECRET=$') { "JWT_SECRET=$(Gerar-Segredo 64)" }
        else { $_ }
    }
    # UTF-8 sem BOM e LF: o compose lê o arquivo como dotenv
    [IO.File]::WriteAllText($script:ArquivoEnv, (($conteudo -join "`n") + "`n"), (New-Object Text.UTF8Encoding($false)))
    $true
}

# Variáveis que o compose interpola (caminhos e flags, nunca segredos)
function Definir-VariaveisDoCompose {
    $env:NGROK_ENV_FILE = Obter-ArquivoNgrok
    $credencial = Obter-CredencialFirebase
    if ($credencial) {
        $env:FIREBASE_ADMIN_JSON = $credencial
        $env:FCM_ENABLED = 'true'
    } else {
        $env:FIREBASE_ADMIN_JSON = Join-Path $script:PastaApresentacao 'credencial-ausente.json'
        $env:FCM_ENABLED = 'false'
    }
    # E-mail: com smtp.env, os códigos vão por e-mail; sem ele, ficam no log do backend (ultimo-codigo-email.ps1)
    $smtp = Obter-ArquivoSmtp
    if ($smtp) {
        $env:SMTP_ENV_FILE = $smtp
        $env:EMAIL_ENABLED = 'true'
        $env:EMAIL_LOG_DO_CODIGO = 'false'
    } else {
        $env:SMTP_ENV_FILE = Join-Path $script:PastaApresentacao 'smtp-ausente.env'
        $env:EMAIL_ENABLED = 'false'
        $env:EMAIL_LOG_DO_CODIGO = 'true'
    }
    $preferencia = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $commit = git -C $script:PastaApresentacao rev-parse --short HEAD 2>$null } finally { $ErrorActionPreference = $preferencia }
    $env:MEDCARE_COMMIT = if ($commit) { "$commit".Trim() } else { 'desconhecido' }
}

# docker compose com o arquivo e o projeto da apresentação; devolve o código de saída
function Invocar-Compose {
    param([Parameter(ValueFromRemainingArguments = $true)] [string[]] $Argumentos)
    $preferencia = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & docker compose -f $script:ArquivoCompose @Argumentos 2>&1 | ForEach-Object { Write-Host "       $_" }
        $LASTEXITCODE
    } finally { $ErrorActionPreference = $preferencia }
}

# GET simples: status e corpo, também para 4xx/5xx; $null se não conectar
function Obter-Resposta([string] $endereco, [int] $timeoutMs = 10000) {
    $req = [Net.HttpWebRequest]::Create($endereco)
    $req.AllowAutoRedirect = $false
    $req.Timeout = $timeoutMs
    $req.UserAgent = 'medcare-apresentacao'
    try { $resp = $req.GetResponse() }
    catch [Net.WebException] { if ($_.Exception.Response) { $resp = $_.Exception.Response } else { return $null } }
    try {
        $corpo = (New-Object IO.StreamReader($resp.GetResponseStream())).ReadToEnd()
        [pscustomobject]@{ Status = [int] $resp.StatusCode; Corpo = $corpo }
    } finally { $resp.Close() }
}

function Saude-Ok([string] $base) {
    $r = Obter-Resposta "$base/actuator/health"
    $r -and $r.Status -eq 200 -and $r.Corpo.Trim() -eq '{"status":"UP"}'
}

function Aguardar-Saude([string] $base, [int] $segundos, [string] $descricao) {
    $limite = (Get-Date).AddSeconds($segundos)
    while ((Get-Date) -lt $limite) {
        if (Saude-Ok $base) { return $true }
        Start-Sleep -Seconds 3
    }
    Escrever-Erro "$descricao não respondeu UP em $segundos s ($base/actuator/health)."
    $false
}

# Comando SQL no banco da APRESENTAÇÃO (contêiner do projeto medcare-apresentacao; nunca o medcare_postgres)
function Invocar-Sql([string] $sql) {
    $preferencia = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $saida = $sql | docker compose -f $script:ArquivoCompose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -X -q -t -A -U "$POSTGRES_USER" -d "$POSTGRES_DB"' 2>&1
        if ($LASTEXITCODE -ne 0) { throw "falha no SQL do banco de apresentação: $saida" }
        $saida
    } finally { $ErrorActionPreference = $preferencia }
}
