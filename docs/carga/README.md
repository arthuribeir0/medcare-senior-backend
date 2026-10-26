# Teste de carga (JMeter)

Tudo o que é preciso para repetir o teste de carga da API. Os resultados medidos estão em
[resultados.md](resultados.md).

| Arquivo | Para quê |
|---|---|
| `preparacao.jmx` | Cria a massa de teste **isolada**: uma conta de cuidador `carga+N@exemplo.invalid` por usuário virtual, com um paciente, um remédio e um tratamento com horários a cada 5 minutos, de 55 minutos antes a 55 minutos depois da hora da preparação. Assim as doses ficam confirmáveis durante o teste. |
| `carga.jmx` | O teste: cuidadores e pacientes em paralelo, cada um sorteando ações com o peso do uso real. |
| `limpeza.jmx` | Exclui as contas de teste pela própria API (a mesma exclusão de conta da LGPD). |
| `ResumoCarga.java` | Lê o resultado (`.jtl`), monta a tabela por cenário e confere os critérios abaixo. Não tem dependências. |

Nada disso toca dados reais: todas as contas usam o domínio reservado `exemplo.invalid`, que não recebe e-mail, com o
prefixo configurável (`-Jprefixo`, padrão `carga`). A limpeza apaga só essas contas.

## Critérios de aceitação

Definidos antes da medição, com os números de [capacidade.md](../capacidade.md) como referência.

| Critério | Carga nominal (50 usuários) | Estresse (150 usuários) |
|---|---|---|
| p95 das leituras (`Leitura - ...`) | ≤ 500 ms | ≤ 500 ms |
| p95 das escritas (`Escrita - ...`) | ≤ 800 ms | ≤ 800 ms |
| Erros (qualquer resposta fora de 2xx) | < 1% | < 1% |
| Respostas 5xx | nenhuma | nenhuma |
| Pool de conexões | sem espera por conexão, sem vazamento: as conexões voltam a ociosas ao fim | idem |

"Usuários" são as threads do JMeter, metade cuidadores e metade pacientes. Cada um faz uma ação a cada 0,5 a 1,5 s
(`pausa_min_ms` + sorteio até `pausa_var_ms`), ou seja, cerca de **1 requisição por segundo**. É dez vezes o uso
ativo estimado em capacidade.md (0,1 req/s com as mensagens abertas), de propósito.

### Peso de cada ação (sorteada a cada iteração)

| Cuidador | % | Paciente | % |
|---|---:|---|---:|
| Doses do dia do paciente | 40 | Doses do dia (`/me/doses`) | 55 |
| Mensagens não lidas | 15 | Mensagens não lidas | 15 |
| Relatório diário | 10 | Conversa (página de 20) | 10 |
| Histórico paginado | 10 | Confirmar a dose aberta mais antiga | 8 |
| Conversa (página de 20) | 8 | Enviar mensagem | 5 |
| Enviar mensagem | 5 | Dados do paciente (`/me`) | 3 |
| Confirmar uma dose aberta (sorteada) | 5 | Novo pareamento (cuidador gera o código e o paciente entra com ele) | 2 |
| Login | 2 | | |
| Histórico em PDF | 1 | | |

No início, cada cuidador faz login. Cada paciente pede ao seu cuidador um código de vínculo e entra com ele (login do
paciente por código).

## Propriedades (`-J...`)

| Propriedade | Padrão | |
|---|---|---|
| `protocolo`, `host`, `porta` | `http`, `localhost`, `8080` | Servidor alvo |
| `cuidadores`, `pacientes` | `25`, `25` | Usuários virtuais de cada tipo. A preparação e a limpeza usam só `cuidadores`: prepare com o maior valor que for usar |
| `rampa`, `duracao` | `60`, `300` | Segundos até todos os usuários estarem ativos, e duração total |
| `pausa_min_ms`, `pausa_var_ms` | `500`, `1000` | Pausa entre ações: mínimo + sorteio de 0 até o valor |
| `prefixo`, `senha` | `carga`, `CargaSomente1` | Contas de teste (`prefixo+N@exemplo.invalid`). A senha é só destas contas sintéticas |
| `fuso` (só preparação) | `America/Sao_Paulo` | Fuso do backend, para os horários do tratamento ficarem em volta de agora |

## Como rodar localmente (sem instalar o JMeter)

O JMeter roda pela imagem Docker `alpine/jmeter:5.6.3`. Nenhum binário entra no repositório; `tools/` e `*.jtl` estão
no `.gitignore`. Use **um banco descartável**, nunca o de desenvolvimento.

```powershell
# 1. Banco descartável (sem volume: some ao remover o contêiner) e backend apontando para ele, porta 8090
docker run -d --name medcare-pg-carga -e POSTGRES_USER=medcare_admin -e POSTGRES_PASSWORD=carga_descartavel `
  -e POSTGRES_DB=medcare_carga -p 127.0.0.1:55432:5432 --tmpfs /var/lib/postgresql/data postgres:16-alpine
.\mvnw.cmd -q package -DskipTests
$env:SERVER_PORT="8090"; $env:POSTGRES_PORT="55432"; $env:POSTGRES_DB="medcare_carga"
$env:POSTGRES_PASSWORD="carga_descartavel"; $env:FCM_ENABLED="false"
java -Xmx512m "-Xlog:gc*:file=gc.log" -jar target\senior-0.0.1-SNAPSHOT.jar

# 2. Em outro terminal, na raiz do repositório: preparação, carga nominal, estresse e limpeza
$jm = "docker run --rm -e JVM_ARGS=-Duser.timezone=America/Sao_Paulo -v ${PWD}\docs\carga:/plano:ro -v ${PWD}\tools\carga:/saida alpine/jmeter:5.6.3"
Invoke-Expression "$jm -n -t /plano/preparacao.jmx -Jhost=host.docker.internal -Jporta=8090 -Jcuidadores=75 -l /saida/preparacao.jtl"
Invoke-Expression "$jm -n -t /plano/carga.jmx -Jhost=host.docker.internal -Jporta=8090 -Jcuidadores=25 -Jpacientes=25 -l /saida/nominal.jtl -e -o /saida/nominal"
Invoke-Expression "$jm -n -t /plano/carga.jmx -Jhost=host.docker.internal -Jporta=8090 -Jcuidadores=75 -Jpacientes=75 -l /saida/estresse.jtl -e -o /saida/estresse"
Invoke-Expression "$jm -n -t /plano/limpeza.jmx -Jhost=host.docker.internal -Jporta=8090 -Jcuidadores=75 -l /saida/limpeza.jtl"

# 3. Resumo por cenário e critérios (o relatório HTML completo fica em tools\carga\nominal\index.html)
java -Dfile.encoding=UTF-8 docs\carga\ResumoCarga.java tools\carga\nominal.jtl

# 4. Desligue o backend (Ctrl+C) e remova o banco descartável
docker rm -f medcare-pg-carga
```

Notas:
- O JMeter **acrescenta** ao `.jtl` existente: apague o arquivo (ou use outro nome) antes de repetir uma execução.
- Rode a preparação longe da meia-noite: os horários do tratamento não atravessam a virada do dia.
- A janela de confirmação das doses preparadas dura cerca de uma hora depois da preparação. Para um teste mais longo,
  rode a preparação de novo antes (ela só acrescenta outro remédio às mesmas contas).
- Para conferir o pool durante o teste:
  `docker exec medcare-pg-carga psql -U medcare_admin -d medcare_carga -c "SELECT state, count(*) FROM pg_stat_activity WHERE datname='medcare_carga' GROUP BY state"`.
- Depois da limpeza, as doses que chegaram a ser confirmadas continuam no banco **anonimizadas** (sem tratamento, sem
  ligação com ninguém), como em qualquer exclusão de conta. Isso não tem dado pessoal. Num banco descartável, isso
  não importa.
- O limite de tentativas do backend só conta códigos de vínculo **inválidos**; o teste usa só códigos válidos, então
  não esbarra nele.

## Contra o servidor, depois da implantação

Os números de resultados.md valem para a máquina local, não para a VM da Oracle. Para medir o servidor:

1. Na VM, isente temporariamente o IP público da máquina do JMeter do limite do nginx (20 req/s por IP). O passo a
   passo está em `deploy/nginx/carga-isenta.conf`. Sem isso, o próprio nginx responde 429.
2. Prepare, rode e limpe apontando para o domínio, com cargas menores primeiro:
   ```powershell
   Invoke-Expression "$jm -n -t /plano/preparacao.jmx -Jprotocolo=https -Jhost=SEU-SUBDOMINIO.duckdns.org -Jporta=443 -Jcuidadores=25 -l /saida/vm-preparacao.jtl"
   Invoke-Expression "$jm -n -t /plano/carga.jmx -Jprotocolo=https -Jhost=SEU-SUBDOMINIO.duckdns.org -Jporta=443 -Jcuidadores=25 -Jpacientes=25 -l /saida/vm-nominal.jtl -e -o /saida/vm-nominal"
   Invoke-Expression "$jm -n -t /plano/limpeza.jmx -Jprotocolo=https -Jhost=SEU-SUBDOMINIO.duckdns.org -Jporta=443 -Jcuidadores=25 -l /saida/vm-limpeza.jtl"
   java -Dfile.encoding=UTF-8 docs\carga\ResumoCarga.java tools\carga\vm-nominal.jtl
   ```
3. Tire a isenção do nginx logo depois (comente a linha e recarregue).
4. Rode a limpeza sempre, mesmo se o teste falhar no meio.
