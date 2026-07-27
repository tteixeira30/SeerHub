# Changelog — SeerHub

Registo append-only do run de features. Mais recente em baixo.
Escrito pelo orquestrador antes de cada ação, para que uma interrupção deixe sempre uma
posição legível. Nenhuma entrada passada é editada ou apagada — corrige-se com uma nova.

---

## 2026-07-27 16:20 — RUN ABERTO

Spec `docs/specs/seerhub.md`. 17 features fatiadas a partir de R1–R15.

Ambiente: Java 21.0.7, Maven 3.9.10, Node 22.16.0, Docker 28.1.1 a correr.
Repositório versionado em `github.com/tteixeira30/SeerHub`, branch `main`, commit inicial
`ff3e4cc` (brief + spec). Um commit por feature a partir daqui.

Comando de testes: ainda não existe — F00 tem de o criar. Previsto `./mvnw test` e `npm test`.
**Baseline: 0 testes, 0 a passar, 0 a falhar** — repositório sem código.

Desvios face aos milestones da spec, com motivo:
- R15 dividido em F00 (esqueleto) e F15 (seed) — o seed não é verificável antes de existirem
  entidades para semear.
- R6 dividido em F06a (gramática, testável sem base de dados) e F06b (correspondência de
  equipas, precisa de `pg_trgm` e do catálogo de F05).

## 2026-07-27 16:24 — ÂMBITO DO RUN

Decisão do utilizador: o loop corre **F00–F04 (M1)** e para para revisão. F05–F15 ficam
`TODO` no backlog e retomam numa invocação seguinte.

Q1 da spec (plano da API-Football e ligas a cobrir): o utilizador tem chave e plano. Os
detalhes concretos são recolhidos no checkpoint do M1, antes de F05. Não bloqueia M1.

## 2026-07-27 16:25 — F00 Fundações e esqueleto · PLANNING

Planeador lançado (Opus 5), com deliberação lisa-loop de 3 minutos: F00 fixa stack,
estrutura de pacotes, harness de testes e migrações, dos quais dependem as 16 features
seguintes. Errar aqui é o erro mais caro do run.

## 2026-07-27 16:56 — F00 Fundações e esqueleto · PLANNED

`docs/features/F00-fundacoes/plan.md` — 581 linhas. 52 ficheiros a criar, 25 critérios
mapeados a testes nomeados, nenhum critério sem teste.

Três decisões estruturais que o plano fixa para todo o run:
1. **Esquema completo do §8 no baseline Flyway**, com a regra "está no §8 → já existe;
   não está → a feature acrescenta a sua própria migração", reforçada por
   `ddl-auto: validate`. Motivo: o grafo de FKs do §8 atravessa a ordem das features, e o
   risco dominante é 16 agentes sem contexto partilhado traduzirem o mesmo §8 de forma
   divergente.
2. **Testcontainers com Postgres real** (contentor estático partilhado), não H2:
   `pg_trgm`, `similarity()`, GIN, `JSONB`, `UNIQUE NULLS NOT DISTINCT` e bloqueio otimista
   não existem em H2 e dariam verde falso no R6, o requisito de maior risco.
3. **Backend organizado por feature** (`pt.seerhub.<feature>` com `api`/`domain`/`repo`/
   `service`), monorepo, frontend fora do build Maven.

Ocorrências do ciclo, registadas por afetarem os próximos:
- O agente `Plan` é read-only e **não tem ferramenta de escrita**: devolveu o plano na
  resposta e o orquestrador persistiu-o. A skill foi corrigida para descrever este fluxo.
- O lisa-loop gastou 721s contra um orçamento de 180s (4x). Nos próximos planeadores o
  orçamento é dado como limite, não como mínimo.
- O planeador leu o `.env` para descobrir o nome da variável da chave (`API_KEY`). Não
  expôs o valor, mas é uma leitura de segredo que não devia ter acontecido; a skill passou
  a proibir explicitamente o acesso ao `.env` em ambos os agentes.

Ação para o utilizador antes de F05: o `.env` usa `API_KEY`, o plano normaliza para
`API_FOOTBALL_KEY`. Renomear a linha quando chegarmos a M2.

## 2026-07-27 16:58 — F00 Fundações e esqueleto · IMPLEMENTING

Implementador lançado (Sonnet 5) a partir de `plan.md`.

## 2026-07-27 17:40 — F00 Fundações e esqueleto · DONE

58 ficheiros commitados. **26 testes, 0 falhas** — 22 JUnit (`./mvnw test`) + 4 Vitest
(`cd frontend && npm test`), verificados pelo orquestrador e não apenas reportados pelo
implementador. Baseline do run passa de 0 para 26.

Os 25 critérios da tabela do plano têm teste com o nome exato especificado, confirmado um a
um. Lista "não tocar" respeitada: spec, backlog, changelog, plano e `.env` intactos;
`.gitignore` só cresceu 3 linhas, nenhuma apagada.

**Nova baseline para todas as features seguintes: 26 a passar, 0 a falhar.**

Desvios face ao plano, todos registados no handoff e nenhum contra a spec:
1. **`snakeyaml` retirado do `backend/pom.xml`.** O plano declarava-o como dependência de
   teste explícita; isso sobrepõe-se ao escopo `compile` que vem transitivamente do
   `spring-boot-starter` e o jar empacotado ficava sem ele — a aplicação não arrancava em
   contentor, mas os testes Maven passavam na mesma. Só apareceu por o implementador ter
   corrido mesmo o `docker compose up`. Regra que fica para as features seguintes: nunca
   redeclarar com escopo mais restrito uma dependência que o Spring Boot já traz.
2. **`@types/node` acrescentado ao frontend** — sem ele `tsc -b` falhava a compilar
   `vite.config.ts` e o build de produção do Dockerfile nunca teria sido testado.
3. Dois testes extra além da tabela, a cobrir prosa explícita do plano.
4. Porta 8080 ocupada por outro projeto nesta máquina; a verificação em contentor correu
   com portas remapeadas por variável de shell, sem tocar no `.env.example`.

Verificação em contentor **confirmada**, não "melhor esforço": os três serviços chegam a
`healthy`, health check responde 200 `UP` direto e via proxy nginx, logs em JSON ECS com
`correlationId`, `docker compose down -v` limpa tudo.

Dívidas herdadas por quem vier a seguir:
- `management.endpoint.health.show-details: always` — **dono nomeado: F01**, trocar para
  `when-authorized` e adaptar `HealthCheckIT` para autenticar.
- `SecurityConfig` com `anyRequest().permitAll()` — esqueleto que F01 substitui.
- `npm audit` reporta 7 vulnerabilidades (1 crítica) em dependências de build do
  Vite/Vitest. Não investigadas; são dev-only, não código servido.
- **Ação do utilizador antes de F05:** renomear `API_KEY` para `API_FOOTBALL_KEY` no `.env`.

Commit `bfe5b19`.

## 2026-07-27 17:41 — F01 Contas e autenticação · PLANNING

Planeador lançado (Opus 5), com deliberação lisa-loop de 2 minutos: F01 fixa o modelo de
autenticação (JWT, rotação de refresh tokens, revogação) de que dependem F02–F14, e é a
primeira feature a mapear entidades JPA contra o baseline Flyway — a prova de que a decisão
estrutural de F00 funciona.

## 2026-07-27 17:47 — F01 Contas e autenticação · PLANNED

`docs/features/F01-contas-autenticacao/plan.md` — 669 linhas, 49 ficheiros a criar ou
editar, **28 critérios mapeados a testes nomeados**: os 5 critérios de R1 decompostos em 17,
mais 5 caminhos de falha, 3 para fechar a dívida do health de F00 e 3 de esqueleto.

O lisa-loop parou aos 120s, dentro do orçamento — o limite passou a ser tratado como teto e
não como mínimo, ao contrário de F00 (721s contra 180s).

Decisões que ficam fixadas: access token JWT HS256 de 15 min, refresh token **opaco**
guardado com hash na base de dados, rotativo com deteção de reutilização que revoga a
família inteira, `V3__refresh_tokens.sql` como migração própria (a tabela não está no §8).

O critério mais fácil de testar mal — "409 que não revela se a conta existe" — tem asserção
precisa: 3b exige que email desconhecido e password errada produzam respostas
indistinguíveis, não apenas que o 409 exista.

Nota de qualidade do plano: a linha X5 da tabela traz uma auto-correção do nome do teste
(um espaço a mais, corrigido a negrito na mesma célula). O nome válido é o da versão a
negrito.

## 2026-07-27 17:48 — F01 Contas e autenticação · IMPLEMENTING

Implementador lançado (Sonnet 5) a partir de `plan.md`. Baseline a preservar: 26 testes.

## 2026-07-27 18:25 — F01 Contas e autenticação · DONE

53 ficheiros commitados. **62 testes, 0 falhas** — 50 JUnit + 12 Vitest, corridos pelo
orquestrador. Baseline sobe de 26 para 62, sem uma única regressão nos testes de F00.

Os 28 critérios da tabela do plano têm teste com o nome exato especificado, confirmado um a
um (o de 2b tem acento no nome e escapou ao primeiro varrimento — existe). Lista "não
tocar" respeitada: spec, F00, `.claude`, `V1` e `V2` intactos; `V3__refresh_tokens.sql`
acrescentada corretamente como migração nova.

**Nova baseline: 62 a passar, 0 a falhar.**

Desvios face ao plano, todos no handoff:
1. **A aritmética do próprio plano estava errada** — anunciava 46 testes totais, mas a
   decomposição por ficheiro somava 28 novos. O implementador seguiu as linhas nomeadas, que
   é o que vale: 22 + 28 = 50.
2. **`token_hash` mapeado como `VARCHAR(64)` e não `CHAR(64)`** — `ddl-auto: validate`
   rejeitava. É exatamente o contrato de F00 a funcionar como pretendido.
3. **`@Transactional(noRollbackFor = ApiException.class)` em `AuthService.refresh()`** — sem
   isto, a revogação da família de tokens ao detetar reutilização era silenciosamente
   revertida pelo rollback. Era um buraco de segurança real, não um detalhe: um token roubado
   e reutilizado devolvia 401 mas não revogava nada.
4. **`trim()` no construtor compacto de `RegisterRequest`/`LoginRequest`** — a Bean Validation
   corre antes da normalização do serviço.
5. **Regra `/__test__/**` com `permitAll()` acrescentada à cadeia de segurança de produção**,
   para manter verde o `ApiExceptionHandlerIT` de F00. Ver dívidas.

Dívidas deixadas:
- **`/__test__/**` permitAll em `SecurityConfig.java:72`** — a rota só existe numa
  `@TestConfiguration` dentro de `ApiExceptionHandlerIT`, por isso em produção é um
  `permitAll` sobre um caminho inexistente: inofensivo hoje, mas é configuração de teste a
  vazar para a cadeia de produção. Correção mais limpa: declarar a regra em escopo de teste
  ou mover o endpoint de teste para uma rota pública já existente. Dono: quem tocar a seguir
  em `SecurityConfig` (F04, que reescreve autorização).
- `npm audit` continua com 7 vulnerabilidades dev-only, herdadas de F00.
- **Ação do utilizador antes de F05:** renomear `API_KEY` para `API_FOOTBALL_KEY` no `.env`.
  Continua por fazer — o implementador não leu nem tocou o ficheiro, como mandado.

Commit `0a7f35d`.

## 2026-07-27 18:26 — F02 Criação e gestão de comunidades · PLANNING

Planeador lançado (Opus 5), sem deliberação lisa-loop: F02 é CRUD sobre uma tabela que já
existe no baseline, com autorização já resolvida por F01. Não há decisão arquitetural
contestada que justifique o orçamento — o time gate é para comprar profundidade onde errar
é caro, não um imposto por feature.

## 2026-07-27 18:40 — F02 Criação e gestão de comunidades · PLANNED

`docs/features/F02-comunidades/plan.md` — 691 linhas, 9 secções.

O plano tem uma secção 2.1 dedicada só à **fronteira F02/F03 sobre `community_memberships`**:
F02 cria a linha `OWNER` do criador e nunca faz `UPDATE` a linhas de membership; toda a
transição de `status` e todo o uso de `expires_at` ficam para F03. Sem isto, F02 teria
decidido o desenho de F03 por acidente.

Decisões: avatar e banner só como URLs, upload adiado com dono nomeado (F15); um ponto de
verificação público `CommunityAccessRules.exigirQueAceitaNovasSubscricoes(...)` que F03 vai
invocar, testado já em F02.

A dívida `/__test__/**` foi reencaminhada em vez de perdida — o plano atribui-a a F15,
enquanto o meu registo de F01 apontava F04. Fica anotado; o dono efetivo é quem primeiro
reescrever `SecurityConfig` a sério.

## 2026-07-27 18:41 — F02 Criação e gestão de comunidades · IMPLEMENTING

Implementador lançado (Sonnet 5). Baseline a preservar: 62 testes.

## 2026-07-27 19:05 — F02 Criação e gestão de comunidades · DONE

35 ficheiros commitados. **101 testes, 0 falhas** — 84 JUnit + 17 Vitest, corridos pelo
orquestrador. Baseline sobe de 62 para 101, sem regressões.

**Fronteira F02/F03 verificada no código, não só reportada:** o único `setStatus` no pacote
`community` é em `Community`; não existe um único `UPDATE` a `community_memberships`. F03
recebe a tabela com o desenho por fazer, que era o objetivo.

Lista "não tocar" respeitada: spec, F00, F01, `.claude`, `V1`–`V3` intactos. Nenhuma
migração nova foi precisa — as duas tabelas já vinham do baseline, e `ddl-auto: validate`
confirmou o mapeamento.

**Nova baseline: 101 a passar, 0 a falhar.**

Desvios face ao plano, nenhum contra a spec:
1. **Campos de preço passam a `type="text" inputMode="decimal"`** em vez de `type="number"`
   — um input numérico de HTML rejeita silenciosamente a vírgula decimal, que é a convenção
   portuguesa. Descoberto a escrever o teste, não depois.
2. Um teste usa `jsonPath("$[0]...")` em vez de expressão de filtro Jayway, que devolvia
   `null` nesta versão da biblioteca.

Verificação em contentor confirmada: registo → criação → nome repetido gera `slug-2`,
`GET /api/communities` anónimo devolve 200, `PUT` com preço `12.50` devolve 400 (não 500,
não truncado), quarta comunidade devolve 409.

Dívidas: inalteradas e não agravadas — `/__test__/**` permitAll, `npm audit` dev-only, e a
renomeação `API_KEY` → `API_FOOTBALL_KEY` no `.env` (ação do utilizador, antes de F05).

Commit `a24e005`.

## 2026-07-27 19:06 — F03 Subscrições e controlo de acesso · PLANNING

Planeador lançado (Opus 5), sem lisa-loop. F03 desenha o ciclo de vida da membership que F02
deliberadamente não tocou: subscrever, cancelar mantendo acesso até `expires_at`, expirar por
tarefa agendada, e 403 em conteúdo premium com membership expirada.

## 2026-07-27 19:20 — F03 Subscrições e controlo de acesso · PLANNED

`docs/features/F03-subscricoes/plan.md` — 819 linhas, 9 secções.

A decisão que vale mais do que o resto da feature: **`CommunityAccessService` como porta
única de acesso a conteúdo premium**, testada diretamente e documentada para F07, F10, F11 e
F12. Sem ela, a garantia do R11 — "a resposta da API a um não-subscritor não contém os campos
ocultos" — seria imposta por quatro implementações independentes e portanto não seria imposta
de todo.

A expiração agendada é testada com `Clock.fixed` e por invocação direta do método de serviço,
nunca esperando pelo relógio. O `ClockConfig` que F01 introduziu é reutilizado em vez de
duplicado.

As transições de estado da membership passam a viver na entidade (`cancelar()`, `reativar()`,
`renovar()`), sem remover nem renomear a fábrica `deDono` que F02 criou — o teste de F02 que
compara a linha coluna a coluna continua a ser o guarda dessa fronteira.

## 2026-07-27 19:21 — F03 Subscrições e controlo de acesso · IMPLEMENTING

Implementador lançado (Sonnet 5). Baseline a preservar: 101 testes.

## 2026-07-27 19:50 — F03 Subscrições e controlo de acesso · DONE

32 ficheiros commitados. **162 testes, 0 falhas** — 140 JUnit + 22 Vitest, corridos pelo
orquestrador. Baseline sobe de 101 para 162, sem regressões.

Verificado por mim para além dos números: **nenhum teste em todo o backend contém
`Thread.sleep` ou espera equivalente** — a expiração agendada é testada com `Clock.fixed` e
invocação direta, como o plano exigia. O teste-guarda da fronteira F02/F03
(`CommunityEditIT.alterarOPrecoNaoMexeEmNenhumaMembershipJaAtiva`) continua verde depois de
F03 ter passado a ser dona das transições de estado.

Entregue: `CommunityAccessService` como porta única de acesso premium, mais
`MembershipAccessRules` (regras puras), `SubscriptionService` e `MembershipExpiryTask`.

Desvio único: subscrever e cancelar foram implementados num só incremento de produção em vez
dos dois passos separados do plano; os ficheiros de teste mantêm-se divididos exatamente como
planeado.

O implementador encontrou e corrigiu uma armadilha própria: um Javadoc continha a literal
`__test__`, o que faria o comando de verificação da dívida devolver duas linhas em vez de
uma e mascarar o estado real da dívida. Reescrito antes de terminar.

Dívidas: inalteradas — `/__test__/**` permitAll isolado a uma linha, `npm audit` dev-only, e
a renomeação `API_KEY` → `API_FOOTBALL_KEY` no `.env` (ação do utilizador, antes de F05).

Commit `225a7e7`.

## 2026-07-27 19:51 — F04 Papéis e permissões · PLANNING

Planeador lançado (Opus 5), sem lisa-loop. Última feature do M1. F04 fecha a autorização por
comunidade e é a feature que o R4 obriga a provar com uma matriz papel × endpoint.

## 2026-07-27 20:05 — F04 Papéis e permissões · PLANNED

`docs/features/F04-papeis-permissoes/plan.md` — 696 linhas, 9 secções.

Autoridade única de permissões por comunidade: catálogo fechado de ações
(`CommunityPermission`), matriz pura papel × permissão (`CommunityPermissionRules`), porta de
serviço (`CommunityPermissionService`) e anotação `@RequiresCommunityPermission` que aplica a
verificação na fronteira HTTP antes de o pedido chegar a código de negócio.

O contrato para as features que ainda não existem está escrito como contrato e não como
sugestão: F07, F08, F12 e F14 anotam os seus endpoints e acrescentam entradas ao catálogo, em
vez de reinventarem a verificação. `CommunityPermissionService` **consome**
`CommunityAccessService` de F03 — F04 nunca redefine "tem acesso premium".

**A dívida `/__test__/**` é fechada aqui**, com teste próprio (`SecurityChainIT
.caminhoInternoDeTesteDeixouDeSerPublico`) a provar que o caminho deixou de ser público. Era
o momento certo: F04 é a feature que reescreve autorização.

## 2026-07-27 20:06 — F04 Papéis e permissões · IMPLEMENTING

Implementador lançado (Sonnet 5). Baseline a preservar: 162 testes. Última feature do M1.

## 2026-07-27 20:35 — F04 Papéis e permissões · DONE

43 ficheiros commitados. **226 testes, 0 falhas** — 200 JUnit + 26 Vitest, corridos pelo
orquestrador. Baseline sobe de 162 para 226, sem regressões.

**Dívida `/__test__/**` fechada e verificada:** `grep -rn "__test__" backend/src/main/java`
devolve zero linhas. Dois testes impedem o regresso — `SecurityChainIT` prova que o caminho
interno devolve 401 a anónimos, e `SecurityConfigConventionsTest` é guarda mecânico contra a
reintrodução da regra.

Desvios face ao plano, nenhum contra a spec:
1. `CommunityPermissionRulesTest` tem 11 testes e não os 10 anunciados — a aritmética do
   plano estava outra vez desalinhada com a sua própria tabela. O implementador seguiu as
   linhas nomeadas, que é o critério certo. Total real: 200 JUnit.
2. `@Qualifier("requestMappingHandlerMapping")` necessário por o contexto de teste ter dois
   beans desse tipo (MVC e Actuator).
3. O teste 1l passou a verificar contra `/access` em vez de `/members` — `/members` exige
   `MANAGE_MODERATORS`, que é exclusivo do dono, logo o teste original não podia provar o que
   dizia provar.
4. O grep de verificação encontrou 5 usos de `getOwner().getId().equals` em vez dos 2
   esperados; os outros 3 são legítimos (cálculo de `ownedByViewer` no DTO e a regra de
   negócio "não se pode nomear o dono"), não desvios de autorização. Documentado no handoff
   para não ser confundido com regressão mais tarde.

Dívidas restantes: `npm audit` dev-only, e a renomeação `API_KEY` → `API_FOOTBALL_KEY` no
`.env` (ação do utilizador, obrigatória antes de F05).

Commit `ede3e14`.

## 2026-07-27 20:36 — MILESTONE M1 CONCLUÍDO · RUN EM PAUSA

F00, F01, F02, F03 e F04 `DONE` — R1, R2, R3, R4 e R15 (exceto seed) implementados e
testados. 226 testes verdes, 0 falhas, 0 ignorados. Tudo empurrado para
`github.com/tteixeira30/SeerHub`, commit `ede3e14`.

Paragem planeada conforme a decisão de âmbito de 2026-07-27 16:24. F05–F15 continuam `TODO`
no backlog e retomam numa invocação seguinte da skill, que lê este changelog e continua a
partir de F05.

**Bloqueio conhecido para F05:** a renomeação `API_KEY` → `API_FOOTBALL_KEY` no `.env`, e os
detalhes de Q1 da spec (plano da API-Football contratado e ligas a cobrir), que o utilizador
disse ter mas ainda não especificou.

## 2026-07-27 21:05 — SPEC ALTERADA (R5) · Q1 RESOLVIDA

O utilizador confirmou: plano **gratuito** da API-Football, **100 chamadas/dia**, e as ligas
a cobrir são **Primeira Liga, Premier League, La Liga, Serie A, Bundesliga e Ligue 1** (seis).
Q1 da spec fica fechada.

**Conflito detetado antes de F05, não durante.** O calendário do R5 tal como escrito —
resultados de 15 em 15 minutos (96 execuções/dia) e jogos de 6 em 6 horas (4 execuções/dia) —
consome exatamente 100 chamadas/dia **com uma única liga**, assumindo um pedido por execução.
A API-Football consulta por liga e época, por isso com seis ligas seriam ~600 chamadas/dia:
seis vezes a quota. O R5 dizia que nenhum pedido de utilizador pode falhar por causa da API
externa, mas o seu próprio calendário garantia que a quota esgotava todos os dias.

**Alteração feita à spec** (`docs/specs/seerhub.md`), com nota de revisão no topo:
- Catálogo de jogos: periodicidade por omissão passa de 6h para 12h.
- Resultados: deixam de ser sondagem fixa e passam a ser **a pedido** — a tarefa verifica em
  base de dados se há alguma seleção pendente cujo jogo começou há mais de duas horas e ainda
  não terminou, e só então chama a API, agrupando por liga e dia num único pedido. Um dia sem
  tips pendentes custa zero chamadas.
- Novo critério: orçamento diário configurável (`API_FOOTBALL_DAILY_BUDGET`, omissão 100),
  respeitado — ao atingir o limite a sincronização pára e regista, em vez de falhar a meio.
- Novo critério: um teste prova que N seleções pendentes em M jogos da mesma liga e dia
  produzem **uma** chamada e não M.

Consumo estimado com as seis ligas: ~15–25 chamadas/dia, contra as ~600 do calendário
anterior.

Corrigido também um carácter solto (`O`) entre a nota de revisão e o primeiro título, que
vinha do commit inicial da spec.

Ação do utilizador confirmada: `.env` já tem `API_FOOTBALL_KEY` e continua ignorado pelo git.
A dívida herdada de F00 está fechada. **F05 deixa de estar bloqueada.**

## 2026-07-27 21:08 — F05 Sincronização de dados de futebol · PLANNING

Planeador lançado (Opus 5), com deliberação lisa-loop de 2 minutos (teto): F05 introduz o
primeiro subsistema com dependência externa, e o orçamento de quota é uma restrição de
desenho e não um detalhe de configuração. Primeira feature do M2.

## 2026-07-27 21:25 — F05 Sincronização de dados de futebol · PLANNED

`docs/features/F05-sincronizacao-futebol/plan.md` — 631 linhas, 9 secções.

Duas decisões que o plano justifica bem:
1. **O contador de orçamento vive em base de dados** (`api_call_budget`, migração `V4`), não
   em memória, com reserva atómica `INSERT ... ON CONFLICT DO UPDATE ... WHERE` que devolve 1
   linha se reservou e 0 se o teto foi atingido. Motivo concreto: `docker-compose.yml` declara
   `restart: unless-stopped`, e o cenário em que o reinício é mais provável — fornecedor em
   baixo, exceções, reinício — é exatamente aquele em que a garantia importa. Três reinícios
   num dia triplicariam o consumo real com um contador em memória.
2. **O resultado lido do fornecedor é `score.fulltime`, não `goals`.** O campo `goals` inclui
   prolongamento; os mercados 1X2, dupla hipótese, mais/menos e ambas marcam resolvem-se ao
   tempo regulamentar. Usar `goals` faria F08 resolver mal todos os jogos de taça com
   prolongamento — um erro que só apareceria em produção, num jogo a eliminar, meses depois.

Fronteira declarada: **F05 nunca toca em `tip_selections` nem em `tips`** — lê-as para saber
se há procura, e escreve apenas em `Fixture`. A resolução é inteiramente de F08, que consome a
superfície definida na secção 2.5 do plano.

## 2026-07-27 21:26 — F05 Sincronização de dados de futebol · IMPLEMENTING

Implementador lançado (Sonnet 5). Baseline a preservar: 226 testes.
