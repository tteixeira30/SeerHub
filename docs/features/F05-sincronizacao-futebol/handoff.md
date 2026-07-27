# F05 — handoff

**Status:** COMPLETE
**Implementado:** 2026-07-27 · Sonnet 5
**Test run:** `./mvnw test` → 274 passados, 0 falhas, 0 erros, 0 ignorados (200 da baseline +
74 novos — ver "Desvios" sobre a diferença face aos 277/77 do plano), corrido cinco vezes
seguidas, sempre verde (incluindo três vezes encadeadas `./mvnw test && ./mvnw test && ./mvnw
test`, e uma vez com `API_FOOTBALL_KEY= API_FOOTBALL_DAILY_BUDGET= API_FOOTBALL_SEASON=` vazias
no ambiente) · `cd frontend && npm test` → `Test Files 12 passed (12)`, `Tests 26 passed (26)`,
inalterados · zero ficheiros do frontend tocados.

## O que agora existe

Um catálogo próprio de ligas, equipas e jogos das seis ligas de arranque, guardado em Postgres
e mantido por duas tarefas agendadas que falam com a API-Football através da interface
`FootballDataProvider`, dentro de um orçamento diário contado em base de dados
(`api_call_budget`, migração `V4`) que nunca é excedido. O catálogo dos próximos 7 dias é
sincronizado de 12 em 12 horas (1 chamada por liga + no máximo 1 de backfill de nome curto);
os **resultados** deixaram de ser sondados por relógio e passam a ser pedidos **só quando
existe procura real** — uma seleção `PENDING` cujo jogo começou há mais de 2h e ainda não
terminou — agrupando por liga e dia numa única chamada (a asserção `ResultSyncGroupingIT`
prova isto: 5 seleções em 3 jogos da mesma liga e dia custam 1 chamada, não 3, não 5). Um dia
sem tips pendentes custa zero chamadas. Cada equipa fica gravada com nome, nome curto, país,
URL do emblema e um **nome normalizado gerado na sincronização** (`TeamNameNormalizer`), servido
pelo índice de trigramas já existente desde F00. Os emblemas são cacheados em disco
(`CrestCacheService`) e servidos por `GET /api/football/crests/{teams|leagues}/{id}` — nunca o
CDN do fornecedor diretamente. Quando o fornecedor falha ou a quota se esgota, o facto é
registado (`log.warn`) e a sincronização retoma no ciclo seguinte; nenhum pedido de utilizador
falha por causa disso (a base de dados continua a responder — provado em `BudgetCeilingIT` e
`FootballProviderFailureIT`). O consumo do dia está exposto em
`GET /actuator/metrics/seerhub.football.api.calls.today` (`ADMIN` apenas). Toda a suite corre
sem rede e sem chave de API, com `FixedFootballDataProvider` (dados fixos, vive em `main`).

## Superfície pública para as próximas features (contrato obrigatório para F06, F07 e F08)

**Esta secção é copiada do §2.5 do plano de F05 — é o contrato que F06, F07 e F08 têm de
seguir sem exceção.**

```java
// pt.seerhub.football.service — tudo em records imutáveis, tudo com o emblema já em URL local.

public record FixtureResultView(
        long fixtureId, FixtureStatus status,
        Integer homeScore, Integer awayScore,
        Instant kickoffAt, Instant lastSyncedAt) {
    public boolean terminado()    { return status == FixtureStatus.FINISHED
                                        && homeScore != null && awayScore != null; }
    public boolean naoSeRealizou(){ return status == FixtureStatus.POSTPONED
                                        || status == FixtureStatus.CANCELLED; }
}

// FootballCatalogService — classe concreta @Service, sem interface própria; a assinatura é o contrato.
Optional<FixtureResultView> resultadoDe(long fixtureId);                    // F08
List<FixtureResultView>     resultadosDe(Collection<Long> fixtureIds);      // F08, uma consulta
List<FixtureView>           jogosNaJanela(Instant de, Instant ate);         // F06/F07
Optional<FixtureView>       jogo(long fixtureId);
Optional<TeamView>          equipa(long teamId);

public record TeamView(long id, String name, String shortName, String country,
                        String normalizedName, String crestUrl) {}          // crestUrl é sempre local
public record FixtureView(long id, long leagueId, String leagueName, String leagueCrestUrl,
                           TeamView home, TeamView away, Instant kickoffAt,
                           FixtureStatus status, Integer homeScore, Integer awayScore) {}

// pt.seerhub.football.service.TeamNameNormalizer — a única autoridade sobre normalização, estática, sem estado.
public static String normalizar(String nome);              // idempotente, nunca devolve null
public static final Set<String> TOKENS_DE_CLUBE;            // público, para F06b documentar o mesmo contrato
```

**(P1) `FixtureResultView` é o único caminho para F08 saber como acabou um jogo.** Nunca ler
`Fixture` diretamente com uma query própria de F08 — chamar
`footballCatalogService.resultadoDe(fixtureId)` (ou `resultadosDe` em lote, uma só consulta
`findAllById`). `terminado()` é a única condição correta para decidir "há resultado para
resolver"; `naoSeRealizou()` é o sinal para o mercado ir a `PENDENTE_MANUAL` com o motivo, nunca
resolver automaticamente para perdida (§10 da spec).

**(P2) OS GOLOS SÃO SEMPRE OS DO TEMPO REGULAMENTAR — nunca os do prolongamento.** `homeScore`/
`awayScore` vêm de `score.fulltime` da API-Football (`ApiFootballStatusMapper.golosRegulamentares`,
Javadoc em maiúsculas de propósito), nunca de `goals` (que inclui prolongamento). Os mercados
1X2, dupla hipótese, mais/menos golos e ambas marcam resolvem-se sempre ao tempo regulamentar.
**F08 não precisa de repetir este cuidado** — o valor que chega já está certo — mas não deve
nunca ir buscar golos a nenhum outro sítio que não `FixtureResultView`/`Fixture.homeScore`/
`Fixture.awayScore`, que são exatamente os mesmos.

**(P3) `TeamNameNormalizer.normalizar(...)` é obrigatório antes de qualquer comparação com
`teams.normalized_name` ou `team_aliases.normalized_alias`.** Regra dura para F06b: o termo de
pesquisa escrito pelo tipster tem de passar por esta função **antes** de ser comparado ou usado
em `similarity(...)`. Se F06b implementar uma segunda normalização (mesmo que pareça
equivalente), a correspondência exata deixa de bater certo e a difusa degrada. Regras exatas
(§4.2 do plano, fixadas em `TeamNameNormalizerTest`):
1. `null`/vazio → `""`.
2. NFD + remoção de marcas combinatórias (`Atlético`→`Atletico`, `München`→`Munchen`).
3. Minúsculas (`Locale.ROOT`).
4. Fora de `[a-z0-9]` vira espaço.
5. Remove `TOKENS_DE_CLUBE` só no início/fim (nunca no meio): `fc, cf, sc, cd, ca, ac, ad, as,
   us, sl, sd, ud, rc, rcd, cfc, afc, bsc, ssc, sv, tsv, vfb, vfl, fsv, rb, ogc, sco, fk, nk,
   if, ff, bk, cp, sad`.
6. Nunca fica vazio (`"FC"` → `"fc"`, não `""`).
7. Colapso de espaços, `trim`.
8. Truncado a 120 (limite de `teams.normalized_name`).

**Nota explícita para F06b:** `Sporting` e `Sporting Lisbon` **não** normalizam para a mesma
cadeia — é a semelhança por trigramas e os aliases que fecham essa distância.
`FootballCatalogSurfaceIT.aPesquisaPorTrigramasSobreNomesNormalizadosDevolveOCandidatoCerto`
prova, sobre os dados reais gravados pela sincronização (o catálogo fixo de §4.1 do plano), que
`similarity(normalized_name, normalizar('Sporting Lisbon'))` devolve **Sporting CP** como
melhor candidato entre as equipas da Primeira Liga — F05 garante o dado e o índice
(`ix_teams_normalized_name_trgm`, GIN, já existente desde `V2`); a decisão de limiar é de F06b.

**(P4) O nome normalizado é regenerado a cada sincronização, nunca escrito por outro sítio.**
Se o fornecedor mudar o nome de uma equipa, `FootballSyncService.upsertTeam` recalcula
`normalized_name` na mesma passagem (`FixtureCatalogSyncIT.mudancaDeNomeNoFornecedorRegeneraONomeNormalizado`).
F06b nunca deve escrever em `teams.normalized_name` diretamente.

**(P5) O emblema devolvido por `TeamView.crestUrl`/`FixtureView.leagueCrestUrl` é sempre um
caminho local** (`/api/football/crests/teams/{id}` ou `/api/football/crests/leagues/{id}`),
nunca o URL do fornecedor. Esse endpoint é público (`GET`, `SecurityConfig`) — serve do disco
com `Cache-Control: public, max-age=604800` se já em cache, redireciona (`302`) para a origem
se ainda não, e nunca bloqueia o pedido do utilizador à espera do CDN externo.

**(P6) F05 nunca escreve em `tip_selections` nem em `tips`.** A pergunta "há seleções
pendentes por resolver?" é respondida por `PendingResultRepository.gruposPorResolver(...)` — SQL
nativo, sem entidade JPA para essas tabelas — e é **global**, sem âmbito de comunidade. F06/F07
podem criar as suas próprias entidades `Tip`/`TipSelection` livremente; F05 nunca as tocará.

## Como o orçamento se comporta (contrato para quem chamar `FootballSyncService`)

- `ApiCallBudgetService.tentarReservar(limite)` reserva **antes** de qualquer chamada de rede
  (nunca depois) — atómico via `INSERT ... ON CONFLICT ... WHERE calls_used < :limite` em
  `api_call_budget`, sem lock aplicacional. Devolve `false` sem lançar exceção quando o teto já
  foi atingido — quem chama tem de tratar isso como "sem orçamento agora", nunca como erro.
- **Dois tetos sobre o mesmo contador:** o catálogo pode ir até `daily-budget` (100 por
  omissão); os resultados só até `daily-budget - catalog-reserve` (80). A reserva de 20 é o que
  impede uma jornada com muitas tips pendentes de esgotar a quota antes do catálogo poder
  correr — `BudgetCeilingIT.aReservaDeCatalogoImpedeQueOsResultadosEsgotemAQuotaDoCatalogo`
  fixa isto.
- Quando o **fornecedor** sinaliza quota esgotada (429, ou seria `x-ratelimit-requests-remaining:
  0` no real), `esgotarDiaPorSinalDoFornecedor(daily-budget)` marca o dia inteiro como esgotado
  (`GREATEST`, nunca reduz um valor já mais alto) e o ciclo atual **interrompe-se** — não
  continua a tentar os grupos seguintes.
- O dia é sempre o dia UTC do `Clock` injetado. Nunca `LocalDate.now()` sem relógio.
- `SyncOutcome.gruposIgnoradosPorOrcamento()` é como uma chamada externa sabe que o orçamento
  bloqueou trabalho **sem falhar silenciosamente** — nunca lançar exceção por causa disto.

## Ficheiros criados

**Backend — produção (34 ficheiros .java + 1 migração), todos sob `backend/src/main/java/pt/seerhub/football/`:**

| Pacote | Ficheiros |
| --- | --- |
| `domain` | `FixtureStatus`, `League`, `Team`, `Fixture` |
| `repo` | `LeagueRepository`, `TeamRepository`, `FixtureRepository`, `ApiCallBudgetRepository`, `PendingResultRepository`, `PendingResultGroup` |
| `provider` | `FootballDataProvider`, `ProviderLeague`, `ProviderTeam`, `ProviderFixture`, `ProviderCall`, `FootballProviderException`, `ApiFootballStatusMapper`, `ApiFootballDataProvider`, `FixedFootballDataProvider` |
| `service` | `TeamNameNormalizer`, `SeasonResolver`, `ApiCallBudgetService`, `SyncOutcome`, `FootballSyncService`, `FootballCatalogService`, `FixtureResultView`, `TeamView`, `FixtureView`, `CrestCacheService`, `FootballMetrics`, `FixtureCatalogSyncTask`, `PendingResultSyncTask` |
| `api` | `CrestController` |
| `config` | `FootballProviderConfig` |
| migração | `backend/src/main/resources/db/migration/V4__api_call_budget.sql` |

**Backend — testes (16 ficheiros), sob `backend/src/test/java/pt/seerhub/`:**

`support/FootballTestSupport.java`, `football/{ApiCallBudgetIT, ApiFootballStatusMapperTest,
BudgetCeilingIT, CrestCacheIT, FixtureCatalogSyncIT, FootballCatalogSurfaceIT,
FootballConventionsTest, FootballMetricsIT, FootballPropertiesTest,
FootballProviderFailureIT, FootballProviderWiringIT, FootballScheduledTasksTest,
PendingResultSyncIT, ResultSyncGroupingIT, SeasonResolverTest, TeamNameNormalizerTest}.java`

**Frontend: zero ficheiros criados ou editados.**

## Ficheiros editados

| Caminho | Alteração |
| --- | --- |
| `backend/src/main/java/pt/seerhub/config/SeerHubProperties.java` | `Football` estendido com `provider, dailyBudget, catalogReserve, season, catalogHorizon, resultDelay, resultWindow, minResultInterval, maxGroupsPerRun, requestTimeout, crestDownloadsPerRun, leagues` (novo `LeagueConfig`) + acessores com omissão (`orcamentoDiario()`, `reservaDeCatalogo()`, …). Sem `@NotNull` em nenhum componente novo. |
| `backend/src/main/java/pt/seerhub/config/SecurityConfig.java` | Duas linhas: `GET /api/football/crests/**` público (R11), `/actuator/metrics/**` exige `ADMIN`. Nada mais tocado. |
| `backend/src/main/resources/application.yml` | Bloco `seerhub.football.*` completo + `management.endpoints.web.exposure.include: health,metrics`. |
| `backend/src/test/resources/application-test.yml` | `seerhub.football.provider: fake`, crons desligados (`"-"`), `daily-budget: 100`, `catalog-reserve: 20`, `season: 2026`, `seerhub.uploads.dir` a apontar para `${java.io.tmpdir}/seerhub-test-uploads`. |
| `.env.example` | `API_FOOTBALL_DAILY_BUDGET=100`, `API_FOOTBALL_SEASON=` (vazio = derivada do relógio). |
| `docker-compose.yml` | `API_FOOTBALL_DAILY_BUDGET`/`API_FOOTBALL_SEASON` no `environment` do `backend`, ambos por referência `${VAR}`. |
| `backend/src/test/java/pt/seerhub/user/JwtServiceTest.java` | Único ajuste: a chamada a `new SeerHubProperties.Football(null, null)` passou a ter os 14 parâmetros da assinatura estendida. Nenhuma asserção do teste foi alterada. |

## Testes

Backend: **274 testes JUnit** (200 da baseline + 74 novos), suite corrida cinco vezes seguidas,
sempre verde (incluindo três vezes encadeadas e uma vez com as três variáveis de ambiente de
F05 vazias).

| Ficheiro | Testes | Critérios |
| --- | ---: | --- |
| `ApiCallBudgetIT` | 5 | 3d–3h |
| `ApiFootballStatusMapperTest` | 3 | 8g–8i |
| `BudgetCeilingIT` | 5 | 3i–3m |
| `CrestCacheIT` | 5 | 5d–5h |
| `FixtureCatalogSyncIT` | 12 | 1c–1h, 5a–5c, 6e, 6f, X1 |
| `FootballCatalogSurfaceIT` | 3 | 5i, 6g, superfície de resultados |
| `FootballConventionsTest` | 5 | 3c, 7b, 7c, 7e, variável de época |
| `FootballMetricsIT` | 3 | 9a–9c |
| `FootballPropertiesTest` | 2 | 3a, 3b |
| `FootballProviderFailureIT` | 7 | 7d, 8a–8f |
| `FootballProviderWiringIT` | 1 | 7a |
| `FootballScheduledTasksTest` | 5 | 1a, 1b, 2a, X3, X4 |
| `PendingResultSyncIT` | 10 | 2b–2k |
| `ResultSyncGroupingIT` | 3 | 4a–4c |
| `SeasonResolverTest` | 1 | X2 |
| `TeamNameNormalizerTest` | 4 | 6a–6d |

Frontend: **26 testes Vitest inalterados** (12 ficheiros), zero ficheiros tocados.

**Nenhum teste toca a rede.** Confirmado mecanicamente por
`FootballConventionsTest.nenhumRecursoDeTesteDeclaraChaveOuUrlDoFornecedorReal` e pelo `grep`
manual (`grep -rn "api-sports.io" backend/src/test` → zero linhas; `grep -rn "http://"
backend/src/test --include=*.java | grep -v localhost` → zero linhas). A suite inteira passa
com `API_FOOTBALL_KEY= API_FOOTBALL_DAILY_BUDGET= API_FOOTBALL_SEASON= ./mvnw test`.

## Desvios face ao plano

1. **74 testes novos, não 77.** O corpo do plano (§4) diz "77 novos previstos (200 → 277)", mas
   a soma da sua **própria** tabela por ficheiro (§4, "Backend — testes") dá 74, exatamente o
   número que implementei — o mesmo tipo de deslize de aritmética já registado nos handoffs de
   F00 e F04 (prosa vs. tabela). Não faltou nenhum critério: todos os 55 pontos de aceitação de
   §3 (1a–9c, X1–X4) têm exatamente um método de teste, nomeado tal como a tabela do plano
   pede. Total real: **200 + 74 = 274** testes JUnit.
2. **`CrestCacheService` não recebe `FootballDataProvider` no construtor** (o plano listava
   `garantirEmblemaDaEquipa(Team)`/`garantirEmblemaDaLiga(League)` como se a classe fosse buscar
   os bytes sozinha). Isso violaria a própria regra 7b do plano ("só o serviço de sincronização
   depende do fornecedor") assim que `CrestCacheService` guardasse uma referência a
   `FootballDataProvider` — descoberto pelo teste mecânico
   `FootballConventionsTest.soOServicoDeSincronizacaoDependeDoFornecedor`, que falhou logo que
   escrevi a primeira versão. Corrigido: `CrestCacheService` agora só decide **se** vale a pena
   descarregar (`precisaDeDescarregarEquipa`/`precisaDeDescarregarLiga`, respeitando o limite
   por execução) e grava bytes já obtidos (`gravarEmblemaDaEquipa`/`gravarEmblemaDaLiga`);
   é `FootballSyncService` — a única classe que depende do fornecedor — quem chama
   `provider.emblema(url)` e entrega os bytes. Nenhum comportamento observável mudou (os
   testes de `CrestCacheIT` continuam a validar através de `sincronizarCatalogo()`, nunca
   chamando os métodos internos diretamente); é uma correção de dependência, não de
   funcionalidade.
3. **Contagem de ficheiros: 51 criados + 6 editados = 57, não os "52 a criar" / "58 ao todo"
   do plano.** Recontei duas vezes: 34 `.java` de produção + 1 migração + 16 de teste
   (incluindo `FootballTestSupport`) = 51. A diferença de 1 face ao "52" do plano é
   inconsequente e do mesmo género de deslize da nota 1 — nenhum ficheiro da lista do plano
   ficou por criar.
4. **As asserções de `FixtureCatalogSyncIT`/`BudgetCeilingIT` usam sempre `provider_id`
   específico, nunca contagens globais da tabela**, e `PendingResultSyncIT`/
   `ResultSyncGroupingIT`/`BudgetCeilingIT`/`FootballCatalogSurfaceIT` apagam em `@AfterEach`
   tudo o que criam (`FootballTestSupport.apagarTip/apagarJogo/apagarEquipa/apagarLiga`). O
   plano não antecipa isto explicitamente, mas decorre diretamente da sua própria regra do §7
   ("cada teste tem de limpar/definir... nunca herdar o estado de outra classe") — a consulta
   de procura de resultados (`PendingResultRepository.gruposPorResolver`) é deliberadamente
   **global**, sem âmbito de comunidade, e o contentor Postgres é partilhado por toda a suite
   (herdado de F00). Sem esta disciplina, um jogo pendente esquecido por um teste
   contaminaria "zero chamadas" doutro — descoberto ao correr `ApiCallBudgetIT`/
   `FixtureCatalogSyncIT`/`PendingResultSyncIT`/`ResultSyncGroupingIT` juntos pela primeira
   vez e ver asserções globais oscilarem consoante a ordem das classes.
5. **`FootballTestSupport.inserirLiga/inserirEquipa/inserirJogo` são "upsert"
   (`ON CONFLICT (provider_id) DO UPDATE`), não `INSERT` simples.** Necessário porque mais do
   que uma classe usa deliberadamente os mesmos `provider_id` de exemplo do plano (a liga 39,
   Premier League, é usada por `FixtureCatalogSyncIT` via sincronização real **e** por
   `ResultSyncGroupingIT` via inserção direta) — sem idempotência, a segunda classe a inserir
   essa liga violaria `UNIQUE(provider_id)`. Não enfraquece nenhuma asserção; só evita uma
   falha de infraestrutura de teste.
6. **`FixtureRepository.marcarSincronizados` precisou de `@Transactional` explícito** no
   método do repositório (`@Modifying @Query` do Spring Data não abre transação sozinho) — sem
   isto, `PendingResultSyncIT`/`ResultSyncGroupingIT` falhavam com
   `TransactionRequiredException` assim que havia um resultado para gravar. Detalhe de
   implementação, não uma alteração de comportamento.
7. **`FootballCatalogService` é `@Transactional(readOnly = true)` a nível de classe** — sem
   isto, `Fixture.league`/`homeTeam`/`awayTeam` (todos `FetchType.LAZY`, e
   `spring.jpa.open-in-view: false` desde F00) lançavam `LazyInitializationException` ao
   construir `FixtureView` fora de uma sessão Hibernate aberta. Descoberto por
   `FootballCatalogSurfaceIT`.

Nenhum desvio contraria a spec ou o R5.

## Dívidas

**Herdadas, ainda por resolver (nenhuma agravada por F05):**
1. **`API_KEY` → `API_FOOTBALL_KEY` no `.env` real do utilizador** — registada por F00,
   reconfirmada por F01–F04, e marcada "obrigatória antes de F05" por F04. **F05 nunca leu nem
   editou o `.env`.** A suite inteira passa sem qualquer chave (prova formal em
   `FootballProviderFailureIT.semChaveDeApiASincronizacaoRegistaEDevolveSemRebentar` e no passo
   4 da verificação), mas a sincronização **real** contra a API-Football só funciona depois de
   o utilizador corrigir isto no seu `.env`.
2. **`npm audit`** (7 vulnerabilidades, 1 crítica, em dependências de build/teste do frontend)
   — F05 não toca no frontend, não agrava.
3. **A forma exata do JSON da API-Football não é verificável offline** (risco já assumido no
   plano, §9.2). `ApiFootballDataProvider` usa `JsonNode` (resiliente a campos novos e ao
   `errors` que ora é objeto ora é lista) e nunca é exercitado pela suite — só uma execução real
   com chave (fora do âmbito desta feature) prova o parsing contra a API real.

**Novas, introduzidas por F05:**
4. **`FootballCatalogService` não tem nenhum controlador HTTP próprio.** É deliberado — a
   superfície de §2.5 é uma porta de serviço para F06/F07/F08 chamarem diretamente, não uma
   API pública nova. Quem precisar de a expor por HTTP (por exemplo, F07 para o ecrã de
   revisão) cria o seu próprio `@RestController` sobre ela.
5. **O emblema de uma liga/equipa recém-criada só fica disponível no disco depois da primeira
   sincronização do catálogo que a processe** — antes disso, `GET .../crests/...` redireciona
   (`302`) para a origem, o que é o comportamento desenhado (5g), mas significa que o primeiro
   pedido depois de uma sincronização a meio pode ainda apanhar o redirecionamento se o
   download tiver ficado para trás do limite de `crest-downloads-per-run` (50). Aceitável para
   v1; documentado para quem for investigar um "emblema a redirecionar" inesperado.
6. **`CrestCacheService` grava sempre no primeiro `Content-Type`/extensão inferido do URL de
   origem** — se o fornecedor mudar o formato do emblema sem mudar a extensão do URL, o
   ficheiro antigo em cache não é substituído automaticamente por um de formato diferente
   (mas é substituído com o mesmo nome/extensão, o conteúdo fica sempre atualizado). Não
   observado no fornecedor de dados fixos; risco teórico para o fornecedor real.

## Confirmação sobre variáveis novas no `.env`

**Ação do utilizador, não é código:** o `.env` real precisa de duas linhas novas, além do já
pendente `API_FOOTBALL_KEY` (dívida herdada, ver acima):

```
API_FOOTBALL_DAILY_BUDGET=100
API_FOOTBALL_SEASON=
```

Sem elas, o `docker compose` avisa e passa string vazia — que a aplicação trata como omissão
(100 e "deriva do relógio", respetivamente) sem rebentar, mas o aviso é ruído desnecessário.
**F05 não leu nem editou o `.env`.**

## Verificação manual

**Não repetida nesta feature.** F00–F04 já confirmaram o arranque real dos três serviços via
`docker compose`. F05 acrescenta duas variáveis de ambiente (sem valores por omissão perigosos)
e uma migração aditiva (`V4`, só uma tabela nova) — risco de regressão no arranque considerado
baixo. Se alguém quiser confirmar a sincronização real: preencher `API_FOOTBALL_KEY` (nome
correto) no `.env`, `docker compose --env-file .env up --build`, esperar pelo primeiro cron do
catálogo (00:07/12:07 UTC) ou reiniciar o container perto dessa hora, e verificar
`SELECT * FROM api_call_budget` (`calls_used` entre 6 e 7) e
`GET /actuator/metrics/seerhub.football.api.calls.today` autenticado como `ADMIN`. **Isto gasta
chamadas reais do orçamento diário do utilizador — não é exigido para dar a feature por
concluída.**

## Avisos para quem vier a seguir

- **A consulta de procura de resultados é global, sem âmbito de comunidade** — qualquer teste
  futuro que insira `tip_selections` ligadas a `fixtures` tem de as apagar no seu próprio
  `@AfterEach` (padrão em `PendingResultSyncIT`/`ResultSyncGroupingIT`/`BudgetCeilingIT`), ou
  vai contaminar as asserções "zero chamadas" de outra classe no mesmo contentor partilhado.
- **`leagues.provider_id`/`teams.provider_id`/`fixtures.provider_id` são `UNIQUE` globais** —
  usar sempre `FootballTestSupport.inserirLiga/inserirEquipa/inserirJogo` (upsert por
  `provider_id`) em vez de `INSERT` direto, e escolher ids que não colidam com o catálogo fixo
  de `FixedFootballDataProvider` (ligas 94/39/140/135/78/61; jogos 9401/9402/3901/3902/14001/
  13501/7801/6101; equipas com o padrão `<liga><1..4>01..04`) a não ser que seja
  deliberadamente esse o cenário a testar (como em `ResultSyncGroupingIT`, que reutiliza a liga
  39 de propósito).
- **A cache de emblemas em disco vive em `${java.io.tmpdir}/seerhub-test-uploads` (perfil de
  teste) e sobrevive entre execuções separadas de `mvn test`**, ao contrário do Postgres do
  Testcontainer (sempre fresco). Um teste que precise de garantir "ainda não há emblema em
  cache" para um id específico deve apagar esse ficheiro explicitamente antes de verificar (ver
  `CrestCacheIT.emblemaAindaSemCacheRedirecionaParaAOrigemENuncaFalha`), porque a sequência de
  identidade do Postgres reinicia em 1 a cada execução fresca mas o disco não.
- **`FootballSyncService` é construído com `Clock`/`ApiCallBudgetService` explícitos no
  construtor** deliberadamente (não só via injeção Spring) — testes que precisem de simular
  "o dia seguinte" ou "uma época diferente" constroem uma instância nova diretamente
  (`new FootballSyncService(...)`), reutilizando os outros beans reais via `@Autowired`. Ver
  `BudgetCeilingIT.noDiaSeguinteASincronizacaoVoltaAChamar` e
  `FixtureCatalogSyncIT.aMesmaLigaEmEpocaNovaAtualizaALinhaEmVezDeCriarOutra`.
- **F06 (gramática de tips) e F06b (correspondência de equipas)**: usar sempre
  `TeamNameNormalizer.normalizar(...)` antes de qualquer comparação com `normalized_name`
  (ver P3 acima) — é o único ponto do plano onde uma segunda implementação, mesmo que pareça
  equivalente, quebra silenciosamente a correspondência exata.
- **F08 (resolução)**: chamar sempre `FootballCatalogService.resultadoDe`/`resultadosDe`, nunca
  ler `Fixture` diretamente com uma repository query própria — ver P1/P2 acima sobre o porquê
  dos golos serem sempre os do tempo regulamentar.
- **`docs/features/BACKLOG.md` e `docs/features/CHANGELOG.md` já vinham marcados
  "IMPLEMENTING" para F05 antes desta sessão começar** (entrada do planeador Opus 5) — não
  foram tocados por este handoff; é o orquestrador quem os fecha para "DONE".
