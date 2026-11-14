<#
.SYNOPSIS
  Cria (ou atualiza) os dados de demonstração da apresentação e imprime os códigos de pareamento válidos agora.

.DESCRIPTION
  SOMENTE DEMONSTRAÇÃO, e só no banco da apresentação no PC (nunca no servidor da Oracle nem no banco de
  desenvolvimento). Pela API local (http://127.0.0.1:8085):
    - cuidador banca@exemplo.com (senha forte gerada na primeira vez e guardada em CREDENCIAIS_DEMO.txt, fora dos
      repositórios), com tolerância curta para o aviso de push aparecer ao vivo;
    - pacientes "Dona Maria" e "Seu José", com 3 remédios cada e horários espalhados no dia;
    - um remédio "ao vivo" da Dona Maria cuja dose cai daqui a -MinutosAteAPrimeiraDose minutos (padrão 4), para ver
      o lembrete tocar no celular do paciente;
    - um código de pareamento novo para cada paciente (rode de novo minutos antes da banca para códigos frescos).
  Por SQL, no contêiner do banco de apresentação: o histórico dos últimos 14 dias (a API só cria doses futuras), com
  confirmações no horário, com atraso e doses não tomadas, confirmadas pelo paciente ou pelo cuidador.
  Idempotente: rodar de novo não duplica nada; só renova o horário ao vivo e os códigos.

.PARAMETER MinutosAteAPrimeiraDose
  Daqui a quantos minutos cai a dose ao vivo (padrão 4; de 3 a 600). A API só aceita horário de hoje com 5 minutos de
  antecedência: abaixo de 6, o tratamento é criado pela API para daqui a 6 minutos e o horário é aproximado por SQL,
  só no banco da apresentação (o mesmo caminho do histórico).

.PARAMETER ToleranciaMinutos
  Tolerância do cuidador de demonstração (padrão 10): o push de dose não confirmada chega esse tempo depois do horário.

.EXAMPLE
  .\preparar-demonstracao.ps1
.EXAMPLE
  .\preparar-demonstracao.ps1 -MinutosAteAPrimeiraDose 12
#>
param(
    [ValidateRange(3, 600)] [int] $MinutosAteAPrimeiraDose = 4,
    [ValidateRange(5, 120)] [int] $ToleranciaMinutos = 10,
    [string] $ArquivoCredenciais = (Join-Path $PSScriptRoot '..\..\..\CREDENCIAIS_DEMO.txt')
)

. (Join-Path $PSScriptRoot 'comum.ps1')

$EmailDemo = 'banca@exemplo.com'
$Api = $script:UrlLocal
$Utf8 = New-Object Text.UTF8Encoding($false)
$TodosOsDias = @('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')

$Pacientes = @(
    @{ Nome = 'Dona Maria'; Nascimento = '1948-03-12'; Telefone = '11988887777'; Remedios = @(
        @{ Nome = 'Losartana'; Dosagem = '50 mg'; Tipo = 'COMPRIMIDO'; Formato = 'REDONDO'; Cor = '#FFFFFF'; Estoque = 60; Horarios = @('08:00', '20:00') },
        @{ Nome = 'Metformina'; Dosagem = '850 mg'; Tipo = 'COMPRIMIDO'; Formato = 'OBLONGO'; Cor = '#F3EBDD'; Estoque = 45; Horarios = @('12:30') },
        @{ Nome = 'Sinvastatina'; Dosagem = '20 mg'; Tipo = 'COMPRIMIDO'; Formato = 'OVAL'; Cor = '#F4A6B8'; Estoque = 30; Horarios = @('21:30') }
    ) },
    @{ Nome = 'Seu José'; Nascimento = '1945-08-21'; Telefone = '11977776666'; Remedios = @(
        @{ Nome = 'Omeprazol'; Dosagem = '20 mg'; Tipo = 'CAPSULA'; Formato = $null; Cor = '#9CC9F0'; Estoque = 28; Horarios = @('07:00') },
        @{ Nome = 'Atenolol'; Dosagem = '25 mg'; Tipo = 'COMPRIMIDO'; Formato = 'REDONDO'; Cor = '#F29E4C'; Estoque = 50; Horarios = @('09:00', '21:00') },
        @{ Nome = 'Vitamina D'; Dosagem = '5 gotas'; Tipo = 'GOTAS'; Formato = $null; Cor = '#F4D35E'; Estoque = 1; Horarios = @('13:00') }
    ) }
)
$RemedioAoVivo = @{ Nome = 'Dipirona'; Dosagem = '500 mg'; Tipo = 'COMPRIMIDO'; Formato = 'OBLONGO'; Cor = '#6CC08B'; Estoque = 20 }

# Chamada JSON à API local, em UTF-8 nos dois sentidos (o PowerShell 5.1 erra os acentos sem isto)
function Api([string] $metodo, [string] $rota, $corpo = $null, [string] $token = $null) {
    $req = [Net.HttpWebRequest]::Create("$Api$rota")
    $req.Method = $metodo
    $req.Timeout = 30000
    $req.Accept = 'application/json'
    if ($token) { $req.Headers.Add('Authorization', "Bearer $token") }
    if ($null -ne $corpo) {
        $bytes = $Utf8.GetBytes(($corpo | ConvertTo-Json -Depth 6 -Compress))
        $req.ContentType = 'application/json; charset=utf-8'
        $req.ContentLength = $bytes.Length
        $fluxo = $req.GetRequestStream()
        try { $fluxo.Write($bytes, 0, $bytes.Length) } finally { $fluxo.Close() }
    }
    try { $resp = $req.GetResponse() }
    catch [Net.WebException] { if ($_.Exception.Response) { $resp = $_.Exception.Response } else { throw "API local fora do ar ($Api): rode iniciar-apresentacao.ps1" } }
    try {
        $texto = (New-Object IO.StreamReader($resp.GetResponseStream(), $Utf8)).ReadToEnd()
        $status = [int] $resp.StatusCode
    } finally { $resp.Close() }
    $json = if ($texto) { $texto | ConvertFrom-Json } else { $null }
    [pscustomobject]@{ Status = $status; Json = $json; Texto = $texto }
}

function Exigir($resposta, [int[]] $esperados, [string] $acao) {
    if ($esperados -notcontains $resposta.Status) { throw "$acao respondeu $($resposta.Status): $($resposta.Texto)" }
    $resposta.Json
}

# Senha do cuidador de demonstração: lida de CREDENCIAIS_DEMO.txt ou gerada (maiúscula, minúscula e número)
function Obter-Senha {
    if (Test-Path $ArquivoCredenciais) {
        $linha = Get-Content -LiteralPath $ArquivoCredenciais | Where-Object { $_ -match '^Senha:\s*\S' } | Select-Object -First 1
        if ($linha) { return ($linha -replace '^Senha:\s*', '').Trim() }
    }
    "Banca$(Gerar-Segredo 14)7x"
}

function Salvar-Credenciais([string] $senha, [object[]] $codigos, [string] $dominio) {
    $linhas = @(
        'MedCare Sênior: credenciais da DEMONSTRAÇÃO (apresentação no PC). Não versione este arquivo.',
        "Atualizado em: $(Get-Date -Format 'dd/MM/yyyy HH:mm')",
        "Servidor: https://$dominio",
        '',
        'Cuidador (celular 1, entrar com e-mail e senha)',
        "E-mail: $EmailDemo",
        "Senha: $senha",
        '',
        'Códigos de pareamento (celular 2, "Sou paciente"; uso único, valem 24 horas)'
    ) + ($codigos | ForEach-Object { "$($_.Nome): $($_.Codigo) (até $($_.Validade))" })
    [IO.File]::WriteAllText([IO.Path]::GetFullPath($ArquivoCredenciais), ($linhas -join "`r`n") + "`r`n", $Utf8)
}

# Histórico dos últimos 14 dias e das doses de hoje que já passaram (só pela demonstração; a API não cria doses passadas).
# Status escolhido por uma "sorte" fixa do dia, horário e tratamento: ~70% no horário, ~20% com atraso, ~10% não tomadas.
function Gravar-Historico([long[]] $tratamentos, [int] $tolerancia) {
    $ids = $tratamentos -join ','
    $sql = @"
WITH agora AS (SELECT (now() AT TIME ZONE 'America/Sao_Paulo') AS momento),
candidatas AS (
  SELECT t.id AS tratamento_id, (d::date + h::time) AS horario,
         (extract(doy FROM d)::int * 31 + extract(hour FROM h::time)::int * 7 + extract(minute FROM h::time)::int + t.id::int * 13) % 20 AS sorte
  FROM tratamento t
  CROSS JOIN LATERAL jsonb_array_elements_text(t.horarios) AS h
  CROSS JOIN agora
  CROSS JOIN LATERAL generate_series(agora.momento::date - 14, agora.momento::date, interval '1 day') AS d
  WHERE t.id IN ($ids)
    AND t.dias_semana ? trim(to_char(d, 'FMDAY'))
    AND (d::date + h::time) < agora.momento - interval '$($tolerancia + 31) minutes'
)
INSERT INTO dose (tratamento_id, horario_programado, status, hora_confirmacao, confirmado_por)
SELECT tratamento_id, horario,
       CASE WHEN sorte < 14 THEN 'CONFIRMADA_NO_HORARIO' WHEN sorte < 18 THEN 'CONFIRMADA_COM_ATRASO' ELSE 'NAO_TOMADA' END,
       CASE WHEN sorte < 14 THEN horario + make_interval(mins => sorte % $tolerancia)
            WHEN sorte < 18 THEN horario + make_interval(mins => $tolerancia + 2 + sorte % 20) END,
       CASE WHEN sorte >= 18 THEN NULL WHEN sorte % 4 = 0 THEN 'CUIDADOR' ELSE 'PACIENTE' END
FROM candidatas
ON CONFLICT (tratamento_id, horario_programado) DO NOTHING;
SELECT count(*) FROM dose WHERE tratamento_id IN ($ids) AND status <> 'PENDENTE';
"@
    ("$(Invocar-Sql $sql)" -split "`n" | Where-Object { $_ -match '^\d+$' } | Select-Object -Last 1)
}

try {
    Testar-Docker
    $dominio = Obter-DominioNgrok
    Definir-VariaveisDoCompose
    if (-not (Saude-Ok $Api)) { throw "o backend local não está no ar ($Api); rode .\iniciar-apresentacao.ps1 antes." }

    # 1. Cuidador
    $senha = Obter-Senha
    $login = Api 'POST' '/api/auth/login' @{ email = $EmailDemo; senha = $senha }
    if ($login.Status -eq 401) {
        $cadastro = Api 'POST' '/api/cuidadores' @{ nome = 'Banca Avaliadora'; email = $EmailDemo; senha = $senha; telefone = '11999990000'; consentimentoLgpd = $true }
        if ($cadastro.Status -eq 409) { throw "$EmailDemo já existe com outra senha. Corrija a linha Senha: em $ArquivoCredenciais ou rode .\resetar-demonstracao.ps1." }
        Exigir $cadastro 201 'cadastro do cuidador' | Out-Null
        $login = Api 'POST' '/api/auth/login' @{ email = $EmailDemo; senha = $senha }
        Escrever-Ok "cuidador $EmailDemo criado"
    }
    # O e-mail da banca é de exemplo (ninguém recebe o código): a conta de demonstração já fica confirmada
    Invocar-Sql "UPDATE cuidador SET email_verificado = true WHERE email = '$EmailDemo' AND NOT email_verificado;" | Out-Null
    $token = (Exigir $login 200 'login do cuidador').token
    Exigir (Api 'PUT' '/api/configuracoes/tolerancia' @{ toleranciaMinutos = $ToleranciaMinutos } $token) 200 'tolerância' | Out-Null
    Escrever-Ok "cuidador $EmailDemo pronto (tolerância de $ToleranciaMinutos minutos)"

    # 2. Pacientes, remédios e tratamentos
    $hoje = Get-Date
    $inicioHistorico = $hoje.AddDays(-14).ToString('yyyy-MM-dd')
    $tratamentosDoHistorico = New-Object Collections.Generic.List[long]
    $existentes = @(Exigir (Api 'GET' '/api/pacientes' $null $token) 200 'lista de pacientes')
    $idsPacientes = @{}
    foreach ($p in $Pacientes) {
        $paciente = $existentes | Where-Object { $_.nome -eq $p.Nome } | Select-Object -First 1
        if (-not $paciente) {
            $paciente = Exigir (Api 'POST' '/api/pacientes' @{ nome = $p.Nome; dataNascimento = $p.Nascimento; telefone = $p.Telefone } $token) 201 "cadastro de $($p.Nome)"
            Escrever-Ok "paciente $($p.Nome) criado"
        }
        $idsPacientes[$p.Nome] = $paciente.id
        $remedios = @(Exigir (Api 'GET' "/api/medicamentos?pacienteId=$($paciente.id)" $null $token) 200 "remédios de $($p.Nome)")
        foreach ($r in $p.Remedios) {
            $remedio = $remedios | Where-Object { $_.nome -eq $r.Nome } | Select-Object -First 1
            if (-not $remedio) {
                $remedio = Exigir (Api 'POST' '/api/medicamentos' @{ nome = $r.Nome; dosagem = $r.Dosagem; tipo = $r.Tipo; formato = $r.Formato; cor = $r.Cor; quantidadeEstoque = $r.Estoque; pacienteId = $paciente.id } $token) 201 "cadastro de $($r.Nome)"
            }
            $tratamento = @(Exigir (Api 'GET' "/api/medicamentos/$($remedio.id)/tratamentos" $null $token) 200 "tratamentos de $($r.Nome)") | Select-Object -First 1
            if (-not $tratamento) {
                $tratamento = Exigir (Api 'POST' "/api/medicamentos/$($remedio.id)/tratamentos" @{ dataInicio = $inicioHistorico; horarios = $r.Horarios; diasSemana = $TodosOsDias } $token) 201 "tratamento de $($r.Nome)"
            }
            $tratamentosDoHistorico.Add([long] $tratamento.id)
        }
        Escrever-Ok "$($p.Nome): $($p.Remedios.Count) remédios com tratamento"
    }

    # 3. Remédio ao vivo: uma dose hoje daqui a N minutos (renovada a cada execução). A API exige 5 minutos de
    # antecedência: o tratamento vai pela API para daqui a pelo menos 6 minutos e, se N for menor, o horário é trazido
    # para mais perto por SQL (só no banco da apresentação)
    $horaAoVivo = (Get-Date).AddMinutes($MinutosAteAPrimeiraDose)
    $horaAoVivo = $horaAoVivo.AddSeconds(-$horaAoVivo.Second)
    $horaPelaApi = (Get-Date).AddMinutes([Math]::Max($MinutosAteAPrimeiraDose, 6))
    $horaPelaApi = $horaPelaApi.AddSeconds(-$horaPelaApi.Second)
    if ($horaPelaApi.Date -ne (Get-Date).Date) { throw 'a dose ao vivo cairia amanhã; rode antes das 23:50 ou diminua -MinutosAteAPrimeiraDose.' }
    $horario = $horaPelaApi.ToString('HH:mm')
    $idMaria = $idsPacientes['Dona Maria']
    $remediosMaria = @(Exigir (Api 'GET' "/api/medicamentos?pacienteId=$idMaria" $null $token) 200 'remédios da Dona Maria')
    $aoVivo = $remediosMaria | Where-Object { $_.nome -eq $RemedioAoVivo.Nome } | Select-Object -First 1
    if (-not $aoVivo) {
        $aoVivo = Exigir (Api 'POST' '/api/medicamentos' @{ nome = $RemedioAoVivo.Nome; dosagem = $RemedioAoVivo.Dosagem; tipo = $RemedioAoVivo.Tipo; formato = $RemedioAoVivo.Formato; cor = $RemedioAoVivo.Cor; quantidadeEstoque = $RemedioAoVivo.Estoque; pacienteId = $idMaria } $token) 201 'cadastro da Dipirona'
    }
    $hojeTexto = (Get-Date).ToString('yyyy-MM-dd')
    $tratamentoAoVivo = @(Exigir (Api 'GET' "/api/medicamentos/$($aoVivo.id)/tratamentos" $null $token) 200 'tratamento ao vivo') | Select-Object -First 1
    $corpoAoVivo = @{ dataInicio = $hojeTexto; dataFim = $hojeTexto; horarios = @($horario); diasSemana = $TodosOsDias }
    if ($tratamentoAoVivo) {
        $tratamentoAoVivo = Exigir (Api 'PUT' "/api/tratamentos/$($tratamentoAoVivo.id)" $corpoAoVivo $token) 200 'horário ao vivo'
    } else {
        $tratamentoAoVivo = Exigir (Api 'POST' "/api/medicamentos/$($aoVivo.id)/tratamentos" $corpoAoVivo $token) 201 'tratamento ao vivo'
    }
    if ($horaAoVivo -ne $horaPelaApi) {
        $horarioCurto = $horaAoVivo.ToString('HH:mm')
        $quando = $horaAoVivo.ToString('yyyy-MM-dd HH:mm')
        $sqlAoVivo = @"
UPDATE tratamento SET horarios = '["$horarioCurto"]'::jsonb WHERE id = $($tratamentoAoVivo.id);
UPDATE dose SET horario_programado = '$quando'
 WHERE tratamento_id = $($tratamentoAoVivo.id) AND status = 'PENDENTE' AND horario_programado::date = DATE '$hojeTexto';
SELECT count(*) FROM dose WHERE tratamento_id = $($tratamentoAoVivo.id) AND horario_programado = '$quando';
"@
        $movidas = ("$(Invocar-Sql $sqlAoVivo)" -split "`n" | Where-Object { $_ -match '^\d+$' } | Select-Object -Last 1)
        if ($movidas -ne '1') { throw "não foi possível aproximar a dose ao vivo para $horarioCurto." }
        $horario = $horarioCurto
    }
    Escrever-Ok "dose ao vivo: Dipirona da Dona Maria hoje às $horario"

    # 4. Histórico de 14 dias (SQL só no banco de apresentação)
    $confirmadas = Gravar-Historico $tratamentosDoHistorico.ToArray() $ToleranciaMinutos
    Escrever-Ok "histórico de 14 dias pronto ($confirmadas doses já encerradas)"

    # 5. Códigos de pareamento novos
    $codigos = foreach ($p in $Pacientes) {
        $codigo = Exigir (Api 'POST' "/api/pacientes/$($idsPacientes[$p.Nome])/gerar-codigo" $null $token) 201, 200 "código de $($p.Nome)"
        [pscustomobject]@{ Nome = $p.Nome; Codigo = $codigo.codigo; Validade = ([DateTime] $codigo.expiraEm).ToString('dd/MM HH:mm') }
    }
    Salvar-Credenciais $senha $codigos $dominio

    # 6. Resumo copiável
    $proximas = @()
    foreach ($p in $Pacientes) {
        foreach ($r in $p.Remedios) {
            foreach ($h in $r.Horarios) {
                $quando = [DateTime]::ParseExact($h, 'HH:mm', $null)
                if ($quando -lt (Get-Date)) { $quando = $quando.AddDays(1) }
                $proximas += [pscustomobject]@{ Quando = $quando; Texto = "$($quando.ToString('dd/MM HH:mm'))  $($p.Nome): $($r.Nome) $($r.Dosagem)" }
            }
        }
    }
    $proximas += [pscustomobject]@{ Quando = $horaAoVivo; Texto = "$($horaAoVivo.ToString('dd/MM HH:mm'))  Dona Maria: Dipirona 500 mg (AO VIVO)" }
    Write-Host ''
    Write-Host '================ DEMONSTRAÇÃO PRONTA ================' -ForegroundColor Green
    Write-Host "Servidor:  https://$dominio"
    Write-Host "Cuidador:  $EmailDemo"
    Write-Host "Senha:     $senha"
    Write-Host 'Pacientes e códigos de pareamento (uso único, 24 h):'
    $codigos | ForEach-Object { Write-Host "  $($_.Nome): $($_.Codigo)  (até $($_.Validade))" }
    Write-Host 'Próximas doses:'
    $proximas | Sort-Object Quando | Select-Object -First 6 | ForEach-Object { Write-Host "  $($_.Texto)" }
    Write-Host "Lembrete no celular do paciente: às $horario (abra o app do paciente uma vez antes, para ele agendar)."
    Write-Host "Reforço do servidor ao paciente: 2 min depois do horário, se a dose continuar pendente."
    Write-Host "Push ao cuidador: $ToleranciaMinutos min depois do horário, se a dose não for confirmada."
    Write-Host "Credenciais salvas em $([IO.Path]::GetFullPath($ArquivoCredenciais))"
    Write-Host '======================================================' -ForegroundColor Green
} catch {
    Escrever-Erro $_.Exception.Message
    exit 1
}
