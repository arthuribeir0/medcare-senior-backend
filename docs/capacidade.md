# Capacidade no plano gratuito e plano B

> **Tudo aqui é ESTIMATIVA**, feita com contas e premissas explícitas. Não houve teste de carga (JMeter fica para
> um bloco seguinte). Os números servem para escolher a VM e saber onde está o limite, não como garantia.

## Premissas

| Item | Valor usado | De onde vem |
|---|---|---|
| Memória do contêiner do backend | 640 MB (VM AMD de 1 GB) / 2 GB (Ampere A1) | `BACKEND_MEM_LIMIT` no `.env.prod` |
| Heap da JVM | 75% do contêiner: ~480 MB / ~1,5 GB | `-XX:MaxRAMPercentage=75` no Dockerfile |
| Memória fora do heap (metaspace, threads, buffers) | ~150 a 200 MB | Spring Boot 4 + Hibernate + Tomcat, ordem de grandeza típica |
| Pool de conexões (HikariCP) | 10 conexões (padrão) | `application.yml` não muda o tamanho |
| PostgreSQL | `max_connections` 100, `shared_buffers` 128 MB (padrões da imagem) | `postgres:16-alpine` sem ajuste |
| Tempo de uma requisição típica | 10 a 30 ms de banco (consultas indexadas, uma por requisição) | consultas com projeção e índices das migrations |
| Threads do Tomcat | até 200 | padrão do Spring Boot |

### Quanto o app chama a API (usuário ativo, app aberto)

| Tela | Requisições | Por segundo |
|---|---|---|
| Painel do cuidador (1 idoso) | 2 GET por minuto (doses do dia + alertas; repetidas em 3 s viram uma) | ~0,03 |
| Painel do cuidador (3 idosos, "Todos") | ~4 GET por minuto | ~0,07 |
| Mensagens abertas (cuidador ou idoso) | 6 GET por minuto (atualização a cada 10 s) | 0,10 |
| Tela inicial do idoso | 1 GET por minuto | ~0,02 |
| Escritas (confirmar dose, enviar mensagem) | raras | desprezível |

Média adotada para um usuário **ativo**: **0,1 requisição por segundo** (pior caso comum: mensagens abertas).
Usuário com o app fechado não gera requisição (o aviso de dose chega por push).

## Conta da capacidade

**Banco**: 10 conexões × (1 / 0,02 s) = **~500 consultas por segundo** de teto teórico, com 20 ms por consulta.

**CPU**, que é o limite real:

- **Ampere A1, 1 OCPU (ARM)**: uma API Spring simples serve, em ordem de grandeza, **150 a 300 req/s** num núcleo
  dedicado (JSON pequeno, uma consulta por requisição).
  → 150 req/s ÷ 0,1 req/s por usuário = **~1.500 usuários ativos ao mesmo tempo** (estimativa conservadora).
  Com 2 OCPU, o dobro, até o teto do pool (~500 req/s ≈ 5.000 usuários ativos), quando o pool passa a limitar.
- **AMD micro (1/8 de OCPU, com picos curtos)**: **15 a 30 req/s** sustentados.
  → 15 ÷ 0,1 = **~150 usuários ativos** (estimativa conservadora). A memória (1 GB, com 640 MB para o backend e o
  restante para PostgreSQL, nginx e sistema) também é apertada: precisa do swap do roteiro.

**Jobs**: a varredura de doses roda a cada minuto e trata até 500 doses por fase; a retentativa, até 200 avisos a
cada 5 minutos. Com poucos milhares de doses por dia isso leva milissegundos e não disputa a CPU de forma relevante.

**Conclusão**: a VM **Ampere A1** comporta com folga um piloto (dezenas a centenas de usuários) e chega à ordem de
1.000 usuários ativos simultâneos. A meta do RNF-10 (10.000 usuários simultâneos) **não** cabe no plano gratuito sem
teste de carga e ajuste (pool, réplicas, banco gerenciado). A VM AMD micro serve só para demonstração.

## Limites do Oracle Cloud Always Free (confira a página oficial antes de criar)

- **Ampere A1 (ARM)**: até 4 OCPU e 24 GB de memória no total da conta (equivalente a 3.000 horas de OCPU e 18.000
  GB-hora por mês), divididos entre até 4 instâncias.
- **AMD micro**: até 2 instâncias VM.Standard.E2.1.Micro (1/8 de OCPU e 1 GB cada).
- **Disco**: 200 GB de block volume no total (o disco de boot conta).
- **Rede**: 10 TB de saída por mês; IP público fixo enquanto a instância existir.
- **Instâncias ociosas**: a Oracle pode recuperar instâncias Always Free com uso muito baixo por vários dias
  (CPU, rede e memória abaixo de ~20%). Os jobs a cada minuto geram algum uso, mas não garantem a regra: verifique a
  política vigente e mantenha cópia de segurança do banco (seção 12 do [roteiro](implantacao.md)).
- **Capacidade**: Ampere A1 costuma faltar em algumas regiões ("Out of capacity"). Tente outro domínio de
  disponibilidade, outro horário ou a AMD micro.

## Plano B: backend local com túnel HTTPS

Quando a VM não estiver disponível, o backend roda no PC e um túnel publica uma URL `https://` (o app de release só
aceita HTTPS). O PC precisa ficar ligado e conectado enquanto o app estiver em uso.

### Opção 1 — Cloudflare Tunnel (sem conta, URL temporária)

1. Suba o banco e o backend no PC, como no desenvolvimento (FCM ligado só com a credencial local):
   ```powershell
   cd C:\Users\arthu\Projetos\medcare-senior-backend
   docker compose up -d                 # PostgreSQL local (usa o .env do backend)
   .\mvnw.cmd spring-boot:run           # backend em http://localhost:8080
   ```
2. Instale o `cloudflared` (<https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/>)
   e abra o túnel:
   ```powershell
   cloudflared tunnel --url http://localhost:8080
   ```
   Ele mostra uma URL `https://<nome-aleatório>.trycloudflare.com`. Teste: `<URL>/actuator/health` → `{"status":"UP"}`.
3. Gere o app com `EXPO_PUBLIC_API_URL=<URL>`. A URL muda a cada execução do `cloudflared`: para uma URL fixa, crie
   um túnel nomeado com conta gratuita da Cloudflare e um domínio próprio.

### Opção 2 — ngrok (conta gratuita, um domínio fixo)

1. Crie a conta em <https://ngrok.com>, instale e registre o token da conta **só no seu PC**
   (`ngrok config add-authtoken <TOKEN>`; o token não vai para o repositório).
2. Reserve o domínio estático gratuito no painel do ngrok e abra o túnel:
   ```powershell
   ngrok http --url=<seu-dominio>.ngrok-free.app 8080
   ```
3. Use `https://<seu-dominio>.ngrok-free.app` em `EXPO_PUBLIC_API_URL`.

Nos dois casos o HTTPS termina no serviço do túnel; o tráfego até o PC vai pelo próprio túnel, criptografado.
Verificação de ponta a ponta contra a URL: `deploy/verificar-producao.ps1 -Url <URL>`.
