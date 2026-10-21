<#
.SYNOPSIS
  Verificação de ponta a ponta da API publicada (VM com HTTPS ou túnel do plano B).

.DESCRIPTION
  1. Saúde: GET <Url>/actuator/health precisa responder {"status":"UP"} e só isso.
  2. HTTPS: http:// redireciona para https:// (pule com -SemRedirecionamento no túnel).
  3. Cabeçalhos de segurança do nginx e rotas internas fechadas (Swagger e actuator).
  4. Fluxo real pelo smoke do app (repositório do front): cadastro, login, idoso, remédio, dose, confirmação,
     histórico, mensagens e exclusão da conta. O smoke cria uma conta de teste e a exclui no fim.
  Não imprime nem pede segredo. Os logs dos jobs ficam na VM (ver docs/implantacao.md, seção 10).

.EXAMPLE
  .\verificar-producao.ps1 -Url https://medcare-senior.duckdns.org
.EXAMPLE
  .\verificar-producao.ps1 -Url https://abc.trycloudflare.com -SemRedirecionamento -SemCabecalhos
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
    [switch] $IgnorarCertificado
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
        $r = Get-Resposta (($base -replace '^https://', 'http://') + '/api/idosos')
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

Passo 'rotas internas fechadas (Swagger e demais endpoints do actuator)' {
    foreach ($rota in '/v3/api-docs', '/swagger-ui.html', '/actuator/env', '/actuator', '/actuator/info') {
        $s = (Get-Resposta "$base$rota").Status
        if ($s -lt 400) { throw "$rota respondeu $s" }
    }
}

Passo 'rota da API sem token responde 401' {
    $s = (Get-Resposta "$base/api/idosos").Status
    if ($s -ne 401) { throw "esperava 401, veio $s" }
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
