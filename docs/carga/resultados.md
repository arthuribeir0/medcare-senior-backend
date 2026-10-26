# Resultados do teste de carga

> Estes números foram medidos na máquina local de desenvolvimento, **não** na VM da Oracle. Servem para achar
> gargalos de código e de configuração e para comparar versões. Na VM (1 a 4 OCPU ARM ou 1/8 de OCPU AMD) os números
> serão outros: repita o teste lá depois da implantação ([README.md](README.md), seção "Contra o servidor").

## Ambiente

| Item | Valor |
|---|---|
| CPU | Intel Core i5-14600K (14 núcleos, 20 threads) |
| Memória | 16 GB (8 GB para o Docker Desktop) |
| Sistema | Windows 11 Pro; PostgreSQL e JMeter em contêineres do Docker Desktop (WSL 2) |
| Java do backend | OpenJDK 17.0.20.1 (Microsoft), heap fixo de 512 MB (`-Xms512m -Xmx512m`), coletor G1 |
| Backend | JAR de produção, perfil padrão, FCM desligado, jobs ligados (varredura a cada minuto), Swagger desligado |
| Pool (HikariCP) | 8 conexões no máximo e 2 ociosas (padrão de produção) |
| Banco | PostgreSQL 16 (alpine), descartável, com os dados em memória (tmpfs) |
| JMeter | 5.6.3 (imagem `alpine/jmeter`), chegando ao backend por `host.docker.internal` |
| Massa | 75 cuidadores, 75 pacientes, 75 remédios e 75 tratamentos com 23 horários por dia (52.575 doses geradas) |

## Carga nominal: 50 usuários (25 cuidadores, 25 pacientes), rampa de 60 s, 5 minutos

| Cenário | Amostras | Média (ms) | p90 | p95 | p99 | Vazão (req/s) | Erro |
|---|---:|---:|---:|---:|---:|---:|---:|
| Escrita - confirma dose (cuidador) | 110 | 6.5 | 11 | 13 | 15 | 0.66 | 1.82% |
| Escrita - confirma dose (paciente) | 168 | 6.6 | 11 | 13 | 17 | 1.02 | 0.60% |
| Escrita - envia mensagem (cuidador) | 342 | 6.3 | 11 | 12 | 15 | 1.17 | 0.00% |
| Escrita - envia mensagem (paciente) | 322 | 5.9 | 10 | 11 | 12 | 1.12 | 0.00% |
| Escrita - gera código de vínculo | 154 | 7.1 | 12 | 13 | 16 | 0.52 | 0.00% |
| Escrita - login do cuidador | 136 | 51.2 | 57 | 58 | 60 | 0.47 | 0.00% |
| Escrita - login do cuidador (início) | 25 | 51.5 | 55 | 56 | 57 | 0.43 | 0.00% |
| Escrita - login do cuidador (para o código) | 25 | 51.9 | 55 | 56 | 77 | 0.44 | 0.00% |
| Escrita - login do paciente por código | 154 | 6.4 | 11 | 13 | 14 | 0.52 | 0.00% |
| Leitura - conversa (cuidador) | 547 | 5.8 | 10 | 11 | 13 | 1.88 | 0.00% |
| Leitura - conversa (paciente) | 647 | 5.1 | 9 | 10 | 13 | 2.22 | 0.00% |
| Leitura - dados do paciente | 193 | 4.5 | 8 | 9 | 12 | 0.67 | 0.00% |
| Leitura - doses do dia (cuidador) | 3085 | 5.7 | 10 | 11 | 13 | 10.40 | 0.00% |
| Leitura - doses do dia (paciente) | 4091 | 5.2 | 9 | 10 | 12 | 13.92 | 0.00% |
| Leitura - histórico em PDF | 68 | 12.6 | 17 | 18 | 171 | 0.24 | 0.00% |
| Leitura - histórico paginado | 683 | 6.4 | 11 | 12 | 14 | 2.32 | 0.00% |
| Leitura - mensagens não lidas (cuidador) | 1026 | 4.8 | 9 | 10 | 11 | 3.48 | 0.00% |
| Leitura - mensagens não lidas (paciente) | 972 | 4.9 | 9 | 10 | 11 | 3.32 | 0.00% |
| Leitura - relatório diário | 685 | 5.1 | 9 | 10 | 13 | 2.32 | 0.00% |
| Pacientes do cuidador | 25 | 6.8 | 11 | 11 | 13 | 0.44 | 0.00% |
| Pacientes do cuidador (para o código) | 25 | 5.5 | 9 | 9 | 13 | 0.44 | 0.00% |
| **Total** | 13483 | 6.1 | 10 | 11 | 49 | 45.11 | 0.02% |

| Critério | Limite | Medido | Resultado |
|---|---|---|---|
| p95 das leituras | ≤ 500 ms | 11 ms | passou |
| p95 das escritas | ≤ 800 ms | 51 ms | passou |
| Erros | < 1% | 0,02% (3 respostas 409) | passou |
| Respostas 5xx | 0 | 0 | passou |
| Pool | sem espera, sem vazamento | no máximo 1 conexão ativa; ao fim, as 8 ociosas | passou |

## Estresse: 150 usuários (75 cuidadores, 75 pacientes), rampa de 60 s, 5 minutos

| Cenário | Amostras | Média (ms) | p90 | p95 | p99 | Vazão (req/s) | Erro |
|---|---:|---:|---:|---:|---:|---:|---:|
| Escrita - confirma dose (cuidador) | 233 | 4.0 | 6 | 7 | 11 | 1.52 | 3.86% |
| Escrita - confirma dose (paciente) | 329 | 3.9 | 6 | 7 | 11 | 2.20 | 0.91% |
| Escrita - envia mensagem (cuidador) | 1024 | 4.0 | 6 | 7 | 10 | 3.48 | 0.00% |
| Escrita - envia mensagem (paciente) | 967 | 3.5 | 5 | 7 | 9 | 3.32 | 0.00% |
| Escrita - gera código de vínculo | 463 | 4.7 | 7 | 9 | 14 | 1.56 | 0.00% |
| Escrita - login do cuidador | 408 | 48.8 | 51 | 53 | 56 | 1.40 | 0.00% |
| Escrita - login do cuidador (início) | 75 | 50.3 | 55 | 57 | 74 | 1.28 | 0.00% |
| Escrita - login do cuidador (para o código) | 75 | 50.3 | 53 | 56 | 76 | 1.28 | 0.00% |
| Escrita - login do paciente por código | 462 | 3.8 | 6 | 8 | 11 | 1.57 | 0.00% |
| Leitura - conversa (cuidador) | 1635 | 3.8 | 6 | 7 | 10 | 5.58 | 0.00% |
| Leitura - conversa (paciente) | 1951 | 3.4 | 5 | 6 | 9 | 6.71 | 0.00% |
| Leitura - dados do paciente | 582 | 2.9 | 4 | 6 | 9 | 2.04 | 0.00% |
| Leitura - doses do dia (cuidador) | 9229 | 3.8 | 6 | 7 | 10 | 31.12 | 0.00% |
| Leitura - doses do dia (paciente) | 12302 | 3.3 | 5 | 6 | 9 | 41.83 | 0.00% |
| Leitura - histórico em PDF | 203 | 6.2 | 9 | 10 | 13 | 0.71 | 0.00% |
| Leitura - histórico paginado | 2046 | 4.1 | 6 | 7 | 10 | 6.93 | 0.00% |
| Leitura - mensagens não lidas (cuidador) | 3071 | 3.0 | 5 | 6 | 9 | 10.42 | 0.00% |
| Leitura - mensagens não lidas (paciente) | 2924 | 3.0 | 5 | 6 | 9 | 10.02 | 0.00% |
| Leitura - relatório diário | 2055 | 3.2 | 5 | 6 | 9 | 6.95 | 0.00% |
| Pacientes do cuidador | 75 | 3.4 | 7 | 9 | 12 | 1.27 | 0.00% |
| Pacientes do cuidador (para o código) | 75 | 3.3 | 6 | 7 | 8 | 1.28 | 0.00% |
| **Total** | 40184 | 4.1 | 6 | 7 | 48 | 134.60 | 0.03% |

| Critério | Limite | Medido | Resultado |
|---|---|---|---|
| p95 das leituras | ≤ 500 ms | 6 ms | passou |
| p95 das escritas | ≤ 800 ms | 49 ms | passou |
| Erros | < 1% | 0,03% (12 respostas 409) | passou |
| Respostas 5xx | 0 | 0 | passou |
| Pool | sem espera, sem vazamento | no máximo 1 conexão ativa; ao fim, 6 ociosas (as acima de 2 fecham depois de 5 minutos sem uso) | passou |

O estresse ficou mais rápido que a carga nominal porque a JVM já estava aquecida (JIT) quando ele começou.

### Os 409 na confirmação

As únicas falhas nas duas rodadas são 409 `DOSE_JA_REGISTRADA`: o cuidador e o paciente escolheram a mesma dose aberta
quase ao mesmo tempo, e a segunda confirmação foi recusada. É a regra funcionando: dose registrada nunca muda, e a
primeira confirmação vence. Não é erro do servidor, mas o critério conta qualquer resposta fora de 2xx, então essas
respostas entram na taxa de erro.

## Teto da máquina: 150 usuários sem pausa entre ações, 2 minutos (fora dos critérios)

Para achar o limite, rodei os mesmos 150 usuários sem nenhuma pausa:

| | Valor |
|---|---|
| Vazão | **cerca de 2.680 req/s** (333.849 requisições em 2 minutos) |
| Média / p95 / p99 | 49,5 ms / 87 ms / 108 ms |
| p95 das leituras / das escritas | 85 ms / 105 ms |
| Respostas 5xx | nenhuma, e nenhum erro no log do backend |
| Pool | no máximo 3 conexões ativas ao mesmo tempo |
| Falhas (0,12%) | conexões TCP recusadas entre o contêiner do JMeter e o host (`HttpHostConnectException`, rede do Docker Desktop) e, por consequência, 401 dos usuários cujo login inicial não chegou ao backend |

Nessa taxa, os limites são a CPU, dividida na mesma máquina entre backend, banco e JMeter, e a rede do Docker Desktop.
O banco fica longe do limite: o pool de 8 conexões sobra, porque cada requisição usa a conexão por poucos
milissegundos.

## Gargalo encontrado e corrigido

**O cadastro de tratamento esgotava o pool.** Na primeira preparação, 19 dos 75 cadastros de tratamento simultâneos
responderam **500** com `Connection is not available, request timed out after 5009ms (total=8, active=8)`.

- **Causa:** a geração das doses (cerca de 690 por tratamento: 23 horários × 30 dias) usava o `saveAll` do JPA. Como
  o id da dose é gerado pelo banco (IDENTITY), o Hibernate não agrupa os INSERTs: era uma ida ao banco por dose, com a
  conexão presa por segundos. Com 8 cadastros assim ao mesmo tempo, os outros esperavam mais que os 5 s do pool e
  falhavam.
- **Correção:** gravação em lote JDBC (`DoseRepositoryAdapter.inserirPendentes`, lotes de 500), com
  `reWriteBatchedInserts=true` no driver, que junta cada lote em poucos comandos. O `ON CONFLICT DO NOTHING` no índice
  único também deixa a geração idempotente quando duas gerações do mesmo tratamento correm juntas; antes, a segunda
  terminava em erro.
- **Nova medição**, com os mesmos 75 cadastros simultâneos:

| Cadastro de tratamento | Média | p95 | Máximo | Erros |
|---|---:|---:|---:|---:|
| Antes | 3.807 ms | 5.444 ms | 5.458 ms | 19 (25%) |
| Depois | **472 ms** | **685 ms** | **703 ms** | **0** |

Teste: `PersistenciaPostgresTest.indiceUnicoDeixaUmaSoDoseNoMesmoHorarioDoMesmoTratamento`.

## Outras observações

- **GC (G1, heap de 512 MB):** 339 pausas jovens, somando 714 ms em cerca de 15 minutos de carga; a maior, de 8,9 ms.
  Nenhum GC completo. Depois de cada coleta, o heap volta a cerca de 92 MB: nenhum sinal de vazamento de memória.
- **Login do cuidador (cerca de 50 ms):** é o custo do BCrypt (fator 10), de propósito, e depende da CPU. Na VM AMD
  micro deve ficar bem mais lento: é o primeiro número a conferir lá.
- **Nenhuma consulta lenta:** nas rodadas com pausa, todas as leituras ficaram com p99 abaixo de 15 ms, exceto um
  pico isolado de 171 ms no primeiro PDF (aquecimento), coerente com os planos de execução de
  [capacidade.md](../capacidade.md).
- **Nenhum limite foi desligado:** o teste fala direto com o backend, sem o nginx, e o limite do pareamento só conta
  códigos inválidos.
