<#
.SYNOPSIS
  Verificação de ponta a ponta da API publicada (VM com HTTPS ou túnel do plano B).

.DESCRIPTION
  1. Saúde: GET <Url>/actuator/health precisa responder {"status":"UP"} e só isso.
  2. HTTPS: http:// redireciona para https:// (pule com -SemRedirecionamento no túnel).
  3. Certificado válido, com mais de 14 dias até vencer.
  4. Cabeçalhos de segurança do nginx e rotas internas fechadas (Swagger com 404, demais endpoints do actuator).
  5. Portas do banco (5432) e do backend (8080) fechadas para fora: só o nginx (80/443) responde.
  6. (Opcional, -TestarLimite) Limite de requisições do nginx: uma rajada acima de 20/s recebe 429 LIMITE_TENTATIVAS.
  7. Fluxo real pelo smoke do app (repositório do front): cadastro, login, paciente, remédio, dose, confirmação,
     histórico, mensagens e exclusão da conta. O smoke cria uma conta de teste e a exclui no fim.
  Não imprime nem pede segredo. Os logs dos jobs ficam na VM (ver docs/implantacao.md, seção 10).

.EXAMPLE
  .\verificar-producao.ps1 -Url https://medcare-senior.duckdns.org
.EXAMPLE
  .\verificar-producao.ps1 -Url https://abc.trycloudflare.com -SemRedirecionamento -SemCabecalhos
.EXAMPLE
  .\verificar-producao.ps1 -Url https://medcare-senior.duckdns.org -TestarLimite
.EXAMPLE
  # SÓ para testar a pilha de produção no próprio PC com certificado autoassinado (nunca contra a URL pública)
  .\verificar-producao.ps1 -Url https://localhost -IgnorarCertificado
#>
param(
    [Parameter(Mandatory = $true)] [string] $Url,
    [string] $Front = (Join-Path $PSScriptRoot '..\..\medcare-senior-mobile'),
    [switch] $SemRedirecionamento,
    [switch] $SemCabecalhos,
    [switch] $SemSmoke,
    [switch] $IgnorarCertificado,
    # Com SWAGGER_ENABLED=true no .env.prod, a documentação responde 200 de propósito
    [switch] $SwaggerLigado,
    [switch] $TestarLimite
)

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
if ($IgnorarCertificado) {
    Write-Host 'ATENÇÃO: certificado não verificado (só para teste local)' -ForegroundColor Yellow
    # Política compilada: no PowerShell 5.1 um scriptblock como callback roda sem runspace e derruba a conexão
    if (-not ('AceitaQualquerCertificado' -as [type])) {
        Add-Type -TypeDefinition @'
public class AceitaQualquerCertificado : System.Net.ICertificatePolicy {
    public bool CheckValidationResult(System.Net.ServicePoint sp, System.Security.Cryptography.X509Certificates.X509Certificate cert,
                                      System.Net.WebRequest req, int problem) { return true; }
}
'@
    }
    [Net.ServicePointManager]::CertificatePolicy = New-Object AceitaQualquerCertificado
    $env:NODE_TLS_REJECT_UNAUTHORIZED = '0'
}
$base = $Url.Trim().TrimEnd('/')
if ($base -notmatch '^https://') { throw 'Use a URL pública com https:// (o app de release só aceita HTTPS).' }
$falhas = 0
function Passo([string] $nome, [scriptblock] $teste) {
    try { & $teste; Write-Host "ok   $nome" }
    catch { $script:falhas++; Write-Host "FALHOU $nome -> $($_.Exception.Message)" -ForegroundColor Red }
}

<#
  GET sem seguir redirecionamento: status, cabeçalhos e corpo, também para 3xx e 4xx (o mesmo comportamento no
  PowerShell 5.1 e no 7, ao contrário do Invoke-WebRequest).
#>
function Get-Resposta([string] $endereco) {
    $req = [Net.HttpWebRequest]::Create($endereco)
    $req.AllowAutoRedirect = $false
    $req.Timeout = 20000
    try { $resp = $req.GetResponse() }
    catch [Net.WebException] { if ($_.Exception.Response) { $resp = $_.Exception.Response } else { throw } }
    try {
        $corpo = (New-Object IO.StreamReader($resp.GetResponseStream())).ReadToEnd()
        [pscustomobject]@{ Status = [int] $resp.StatusCode; Headers = $resp.Headers; Corpo = $corpo }
    } finally { $resp.Close() }
}

Passo 'saúde UP sem detalhes (/actuator/health)' {
    $r = Get-Resposta "$base/actuator/health"
    if ($r.Status -ne 200 -or $r.Corpo.Trim() -ne '{"status":"UP"}') { throw "resposta inesperada: $($r.Status) $($r.Corpo)" }
}

if (-not $SemRedirecionamento) {
    Passo 'http:// redireciona para https://' {
        $r = Get-Resposta (($base -replace '^https://', 'http://') + '/api/pacientes')
        if ($r.Status -ne 301 -or $r.Headers['Location'] -notmatch '^https://') { throw "esperava 301 para https://, veio $($r.Status)" }
    }
}

if (-not $SemCabecalhos) {
    Passo 'cabeçalhos de segurança (HSTS, nosniff, frame, CSP)' {
        $h = (Get-Resposta "$base/actuator/health").Headers
        foreach ($nome in 'Strict-Transport-Security', 'X-Content-Type-Options', 'X-Frame-Options', 'Content-Security-Policy') {
            if (-not $h[$nome]) { throw "sem $nome" }
        }
        if ($h['Server'] -match '\d') { throw 'o cabeçalho Server mostra a versão do nginx' }
    }
}

if (-not $IgnorarCertificado) {
    Passo 'certificado HTTPS válido e longe de vencer' {
        $req = [Net.HttpWebRequest]::Create("$base/actuator/health")
        $req.Timeout = 20000
        $req.GetResponse().Close()
        $cert = New-Object Security.Cryptography.X509Certificates.X509Certificate2($req.ServicePoint.Certificate)
        $dias = [int] ($cert.NotAfter - (Get-Date)).TotalDays
        if ($dias -lt 14) { throw "o certificado vence em $dias dias ($($cert.NotAfter)); confira a renovação automática" }
        Write-Host "     certificado de $($cert.Subject), vence em $dias dias"
    }
}

Passo 'Swagger desligado (404) e demais endpoints do actuator fechados' {
    if (-not $SwaggerLigado) {
        foreach ($rota in '/v3/api-docs', '/swagger-ui/index.html', '/swagger-ui.html') {
            $s = (Get-Resposta "$base$rota").Status
            if ($s -ne 404) { throw "$rota respondeu $s (esperava 404; se SWAGGER_ENABLED=true for de propósito, use -SwaggerLigado)" }
        }
    }
    foreach ($rota in '/actuator/env', '/actuator', '/actuator/info', '/actuator/metrics') {
        $s = (Get-Resposta "$base$rota").Status
        if ($s -lt 400) { throw "$rota respondeu $s" }
    }
}

Passo 'portas 5432 (banco) e 8080 (backend) fechadas para fora' {
    $servidor = ([Uri] $base).Host
    foreach ($porta in 5432, 8080) {
        $cliente = New-Object Net.Sockets.TcpClient
        try {
            $tentativa = $cliente.BeginConnect($servidor, $porta, $null, $null)
            if ($tentativa.AsyncWaitHandle.WaitOne(3000) -and $cliente.Connected) { throw "a porta $porta aceitou conexão de fora" }
        } finally { $cliente.Close() }
    }
}

Passo 'rota da API sem token responde 401' {
    $s = (Get-Resposta "$base/api/pacientes").Status
    if ($s -ne 401) { throw "esperava 401, veio $s" }
}

if ($TestarLimite) {
    Passo 'limite de requisições do nginx (rajada de 150 pedidos recebe 429)' {
        $limitados = 0
        $corpo429 = ''
        1..150 | ForEach-Object {
            $r = Get-Resposta "$base/api/pacientes"
            if ($r.Status -eq 429) { $limitados++; $corpo429 = $r.Corpo }
        }
        if ($limitados -eq 0) { throw 'nenhuma resposta 429: o limit_req do nginx não está ativo (ou o IP está isento em carga-isenta.conf)' }
        if ($corpo429 -notmatch 'LIMITE_TENTATIVAS') { throw "o 429 veio sem o codigo LIMITE_TENTATIVAS: $corpo429" }
        Write-Host "     $limitados de 150 pedidos limitados (aguarde alguns segundos antes do smoke)"
        Start-Sleep -Seconds 5
    }
}

if (-not $SemSmoke) {
    Passo 'fluxo completo pelo smoke do app (cria e exclui uma conta de teste)' {
        if (-not (Test-Path (Join-Path $Front 'package.json'))) { throw "repositório do front não encontrado em $Front (use -Front)" }
        Push-Location $Front
        $preferencia = $ErrorActionPreference
        try {
            $env:EXPO_PUBLIC_API_URL = $base
            # Avisos do Node no stderr não são falha: vale o código de saída do smoke
            $ErrorActionPreference = 'Continue'
            cmd /c "npm run smoke:api 2>&1" | ForEach-Object { Write-Host "     $_" }
            if ($LASTEXITCODE -ne 0) { throw "smoke saiu com $LASTEXITCODE" }
        } finally {
            $ErrorActionPreference = $preferencia
            Remove-Item Env:EXPO_PUBLIC_API_URL -ErrorAction SilentlyContinue
            Pop-Location
        }
    }
}

if ($IgnorarCertificado) { Remove-Item Env:NODE_TLS_REJECT_UNAUTHORIZED -ErrorAction SilentlyContinue }
if ($falhas -gt 0) { Write-Host "`n$falhas verificação(ões) falharam" -ForegroundColor Red; exit 1 }
Write-Host "`nProdução OK em $base"
