# F05 — Sincronização de dados de futebol

**Requisitos:** R5 (texto revisto em 2026-07-27, sincronização a pedido)
**Depende de:** F00 (esqueleto, `SeerHubProperties`, `ClockConfig`, baseline `V1`–`V3`), F01 (cadeia de segurança onde entra o caminho público dos emblemas). Não depende de F02–F04.
**Planeado:** 2026-07-27 · Opus 5

---

## 1. Objetivo

Depois desta feature o SeerHub tem, em Postgres, um catálogo próprio de ligas, equipas e jogos das seis ligas de arranque (Primeira Liga, Premier League, La Liga, Serie A, Bundesliga, Ligue 1), mantido por duas tarefas agendadas que falam com a API-Football **através de uma interface** e **dentro de um orçamento diário de chamadas que é contado em base de dados e não pode ser excedido**. O catálogo dos próximos sete dias é atualizado de 12 em 12 horas (6 chamadas por execução, uma por liga); os **resultados** deixam de ser sondados por relógio e passam a ser pedidos **só quando existe procura real** — uma seleção de tip pendente cujo jogo começou há mais de duas horas e ainda não terminou — agrupando todos os jogos em falta por liga e por dia numa única chamada. Um dia sem tips pendentes custa zero chamadas. Cada equipa fica gravada com nome, nome curto, país, URL do emblema e um **nome normalizado gerado na sincronização**, servido pelo índice de trigramas que o R6 vai consumir; os emblemas passam a ser servidos de uma cache local em disco e nunca do CDN externo em cada render. Quando o fornecedor está em baixo, devolve erro, ou a quota se esgota a meio do dia, o facto é registado, a sincronização retoma no ciclo seguinte (ou no dia seguinte) e **nenhum pedido de utilizador falha por causa disso** — a base de dados continua a responder. O consumo do dia fica exposto numa métrica. Toda a suite continua a correr sem rede e sem chave de API, com uma implementação de dados fixos do fornecedor.

---

## 2. Contexto herdado

### 2.1 Handoffs lidos

- `docs/features/F00-fundacoes/handoff.md` — estrutura de pacotes por feature, `AbstractIntegrationTest`, `RepoRoot`, regra das migrações, `ApiException`/`ApiExceptionHandler`, `SeerHubProperties.Football(apiKey, baseUrl)` já existente, `@EnableScheduling` já ligado em `SeerHubApplication`.
- `docs/features/F04-papeis-permissoes/handoff.md` — contrato de permissões (C1–C8). **F05 não cria nenhuma rota sob `/api/communities/{slug}/...`**, por isso não é abrangida por `CommunityAuthorizationContractIT` (verificado: `rotasDeComunidade(...)` filtra por `caminho.startsWith("/api/communities/{slug}")`, `backend/src/test/java/pt/seerhub/community/CommunityAuthorizationContractIT.java:145`). Nenhuma permissão nova é acrescentada ao catálogo `CommunityPermission`, conforme (C5).
- `CLAUDE.md` — o mapa de pacotes já reserva `football` para F05; convenções de nomes, testes em português, migrações, segredos.

### 2.2 Esquema já existente (não se recria)

`backend/src/main/resources/db/migration/V2__baseline_schema.sql` já traz, com a forma exata contra a qual as entidades vão ser mapeadas com `ddl-auto: validate`:

- `leagues(id, provider_id UNIQUE, name, country, logo_url, season NOT NULL, active)`
- `teams(id, provider_id UNIQUE, name, normalized_name NOT NULL, short_name, country, logo_url)` + `ix_teams_normalized_name` + **`ix_teams_normalized_name_trgm` (GIN, `gin_trgm_ops`)**
- `fixtures(id, provider_id UNIQUE, league_id, home_team_id, away_team_id, kickoff_at, status CHECK IN (SCHEDULED|LIVE|FINISHED|POSTPONED|CANCELLED), home_score, away_score, last_synced_at)` + `ix_fixtures_kickoff` + **`ix_fixtures_status_kickoff`** + índices por liga e por equipa
- `team_aliases(...)` com `uq_team_alias UNIQUE NULLS NOT DISTINCT (normalized_alias, community_id)` + `ix_team_aliases_trgm` — **F05 não escreve nesta tabela**; é de F06b
- `tip_selections(id, tip_id, fixture_id NULL, market, selection, line, odds, status, settlement_source, settled_by, settled_at, version)` + **`ix_selections_fixture_status (fixture_id, status)`**

Dois destes índices existem precisamente para esta feature: a §8 da spec anota `Fixture(status, kickoff_at)` como sendo "para o sincronizador de resultados" e `TipSelection(fixture_id, status)` "para o resolvedor". A consulta de procura desenhada em §2.4 assenta exatamente neles.

`fixtures.last_synced_at` já existe e é o que permite estrangular a repetição de chamadas sem coluna nova.
`tip_selections.fixture_id` é **anulável** por desenho ("uma tip pode ser publicada com o jogo por associar", comentário do baseline) — o que faz com que o caso de fronteira "liga menor sem jogos no fornecedor" custe zero chamadas de graça, porque essas seleções nunca entram na consulta de procura (o `JOIN` a `fixtures` elimina-as).

### 2.3 Código existente reutilizado

| Ficheiro | O que F05 usa |
| --- | --- |
| `backend/src/main/java/pt/seerhub/config/ClockConfig.java` | O bean `Clock` injetado por construtor em todos os serviços de F05 (padrão já usado por `SubscriptionService`, `backend/src/main/java/pt/seerhub/community/service/SubscriptionService.java:50`). Nenhum `Instant.now()` sem relógio. |
| `backend/src/main/java/pt/seerhub/community/service/MembershipExpiryTask.java` | O padrão exato das tarefas agendadas: `@Component`, constante pública `CRON_POR_OMISSAO`, `@Scheduled(cron = "${...:omissão}", zone = "UTC")`, corpo que delega no serviço e **engole qualquer exceção** com `log.warn`. As duas tarefas de F05 copiam-no linha a linha. |
| `backend/src/test/resources/application-test.yml` | O padrão de desligar tarefas na suite com `expiry-cron: "-"` (`Scheduled.CRON_DISABLED`). F05 desliga as suas duas. |
| `backend/src/main/java/pt/seerhub/config/SeerHubProperties.java` | O record `Football(apiKey, baseUrl)` é **estendido** por F05 (não substituído). `apiKey` continua sem `@NotBlank`: `application.yml` já traz `api-key: ${API_FOOTBALL_KEY:}` com o comentário "vazio é válido: F05 valida quando sincroniza" — F05 honra isso e **nunca faz o arranque falhar por falta de chave**. |
| `backend/src/test/java/pt/seerhub/support/CommunityTestSupport.java` | O precedente explícito de inserir linhas cruas por `JdbcTemplate` quando a API da feature dona ainda não existe. `FootballTestSupport` faz o mesmo com `tips`/`tip_selections`. |
| `backend/src/main/java/pt/seerhub/common/error/ApiException.java` | Único mecanismo de erro de negócio (o `404` do emblema desconhecido). |

`spring-boot-starter-web` já traz `RestClient` (Spring Framework 6.1) e `spring-boot-starter-actuator` já traz o Micrometer: **F05 não acrescenta nenhuma dependência ao `backend/pom.xml`** (e cumpre assim o aviso do desvio 1 de F00 sobre redeclarar dependências transitivas).

### 2.4 A decisão central: sincronização a pedido dentro de um orçamento

Esta subsecção fixa o desenho que o resto do plano implementa. As três escolhas contestadas foram resolvidas com a evidência do repositório (ver §9 para a resolução completa).

**(A) O contador de chamadas vive numa tabela, não em memória.**
`docker-compose.yml:47` declara `restart: unless-stopped` no serviço `backend`. Um contador em memória volta a zero em cada reinício, e o cenário em que o reinício é mais provável (fornecedor em baixo → exceções → reinício) é exatamente aquele em que a garantia importa: bastariam três reinícios num dia para triplicar o consumo real. A tabela `api_call_budget` (migração `V4`) tem uma linha por dia UTC, e a reserva é **um único `INSERT ... ON CONFLICT DO UPDATE ... WHERE`**, atómico, que devolve 1 linha se reservou e 0 se o teto foi atingido:

```sql
INSERT INTO api_call_budget (day, calls_used, updated_at)
VALUES (:dia, 1, now())
ON CONFLICT (day) DO UPDATE
   SET calls_used = api_call_budget.calls_used + 1, updated_at = now()
 WHERE api_call_budget.calls_used < :limite
```

Sem lock aplicacional, sem corrida, sem depender do relógio de nenhuma JVM em particular. E dá o gancho de teste que não existe de outra forma: um teste pode escrever `calls_used = 100` diretamente e simular um dia esgotado **sem bifurcar o contexto Spring** (que é o que `@TestPropertySource` obrigaria).

**(B) Reservar antes de chamar, nunca devolver a reserva.**
A reserva acontece **antes** do pedido HTTP. Se o pedido falhar, a unidade não é devolvida. Contar a mais é seguro; contar a menos não é. Além disso, quando o fornecedor devolve `429`, ou o cabeçalho `x-ratelimit-requests-remaining: 0`, o dia é **imediatamente esgotado** (`UPDATE ... SET calls_used = GREATEST(calls_used, :limite)`) e o ciclo é interrompido — o sinal do fornecedor é mais autoritativo do que a nossa contagem.

**(C) Dois tetos sobre um só contador.**
O catálogo pode consumir até `daily-budget` (100). Os resultados só podem consumir até `daily-budget - catalog-reserve` (100 − 20 = 80). Isto responde literalmente ao critério "nunca esgota a quota a meio de um ciclo": sem a reserva, uma jornada com muitas tips pendentes poderia consumir as 100 chamadas antes das 07:00 e deixar o catálogo por sincronizar durante um dia inteiro — o que faria as tips do dia seguinte não terem jogos a que se ligar. A reserva é a única coisa que impede a sincronização de resultados de matar à fome a sincronização do catálogo.

**Aritmética do orçamento (fixada aqui, verificável no teste 1e):**

| Consumidor | Chamadas por execução | Execuções/dia | Total/dia |
| --- | ---: | ---: | ---: |
| Catálogo (1 chamada por liga, `from`/`to` cobre os 7 dias de uma vez) | 6 | 2 (cron de 12h) | 12 |
| Backfill de nome curto/país (`/teams`, no máximo 1 liga por execução, extingue-se ao fim de ~3 dias) | ≤1 | 2 | ≤2 |
| **Subtotal catálogo** | | | **≤14** (reserva de 20) |
| Resultados (1 chamada por grupo liga×dia, no máximo 4 por execução, estrangulada a 1 por grupo por 30 min) | ≤4 | 96 (cron de 15 min) | teto rígido **80** |
| **Teto absoluto** | | | **100** |

O emblema **não consome orçamento**: vem de `media.api-sports.io`, que é um CDN de ficheiros e não a API de dados. Isto é uma decisão explícita e está isolada no código (o método `emblema(...)` do fornecedor é o único que não passa pela reserva).

**(D) O catálogo é uma chamada por liga por execução, não uma chamada por liga por dia.**
`GET /fixtures?league={id}&season={época}&from={hoje}&to={hoje+7}&timezone=UTC` devolve os sete dias de uma só vez. Descoberta decisiva: a resposta de `/fixtures` **traz embutidos a liga e as duas equipas** (id, nome, país, logo, época), pelo que ligas e equipas são inseridas/atualizadas a partir do mesmo payload — **zero chamadas adicionais** para catálogo de equipas. É isto que faz 12 chamadas/dia serem suficientes para manter seis ligas frescas. O único campo que `/fixtures` não traz é o `code` de três letras (nome curto) e o país da equipa, resolvidos pelo backfill limitado descrito acima.

**(E) "Há algo que valha a pena resolver?" é uma pergunta só de base de dados.**
A tarefa de resultados corre de 15 em 15 minutos e a **primeira coisa que faz é uma consulta**; se ela devolver zero linhas, a tarefa termina sem tocar na rede. A consulta (`PendingResultRepository`, `JdbcTemplate`, SQL nativo):

```sql
SELECT l.id, l.provider_id, l.season,
       CAST(f.kickoff_at AT TIME ZONE 'UTC' AS date) AS dia,
       COUNT(DISTINCT f.id) AS jogos
  FROM tip_selections s
  JOIN fixtures f ON f.id = s.fixture_id
  JOIN leagues  l ON l.id = f.league_id
 WHERE s.status = 'PENDING'
   AND f.status IN ('SCHEDULED','LIVE')
   AND f.kickoff_at <= ?          -- agora - result-delay (2h)
   AND f.kickoff_at >= ?          -- agora - result-window (7d)
   AND (f.last_synced_at IS NULL OR f.last_synced_at <= ?)   -- agora - min-result-interval (30m)
 GROUP BY l.id, l.provider_id, l.season, 4
 ORDER BY 4 DESC, l.id
 LIMIT ?                          -- max-groups-per-run (4)
```

Cinco propriedades desta consulta, cada uma deliberada:

1. **É lida por SQL nativo e F05 não cria nenhuma entidade `TipSelection`.** A tabela existe no baseline mas o seu desenho JPA pertence a F06/F07; criar aqui a entidade obrigaria F06 a herdar um mapeamento que não escolheu. F05 lê três colunas por SQL e mais nada.
2. **`f.status IN ('SCHEDULED','LIVE')`** exclui `FINISHED` (nada a fazer), `POSTPONED` e `CANCELLED`. Um jogo adiado ou cancelado sai da fila **para sempre** assim que o estado é gravado, em vez de ser sondado eternamente. Se for remarcado, a sincronização do catálogo repõe `kickoff_at` e `status = SCHEDULED` e ele volta à fila naturalmente.
3. **A janela inferior (`agora − 7 dias`)** impede o gotejar eterno de um jogo que o fornecedor nunca marca como terminado. Ao fim de sete dias o jogo é abandonado pelo sincronizador e fica para resolução manual (que é o que o R8 já prevê).
4. **O estrangulamento por `last_synced_at`** impede que um jogo em prolongamento consuma uma chamada de 15 em 15 minutos. Sem ele, um único jogo encravado gastaria 96 chamadas/dia.
5. **`GROUP BY liga, dia`** é o agrupamento exigido pelo critério 4: N seleções em M jogos da mesma liga e dia colapsam numa linha, logo numa chamada.

Complemento obrigatório: depois de uma chamada bem sucedida a um grupo, o serviço faz **um `UPDATE` em bloco a `last_synced_at` de todos os jogos daquela liga naquele dia**, e não só dos jogos que o fornecedor devolveu. Sem isto, um jogo que o fornecedor deixou de devolver mantinha `last_synced_at` antigo e re-disparava o grupo em todos os ciclos.

**(F) O que F05 faz e o que não faz na resolução.**
F05 grava `status`, `home_score`, `away_score` e `last_synced_at` no `Fixture`. **F05 nunca toca em `tip_selections` nem em `tips`** — não muda estados, não resolve nada. O passo 3 do fluxo §6.3 da spec é de F08, que lê o resultado pela superfície definida em §2.5.

**(G) Golos que contam: os dos 90 minutos.**
`score.fulltime` quando existir, com recurso a `goals` quando não existir. `goals` inclui prolongamento; os mercados 1X2, dupla hipótese, mais/menos e ambas marcam resolvem-se ao tempo regulamentar. Gravar o resultado do prolongamento faria F08 resolver mal todos os jogos de taça com prolongamento. Documentado em maiúsculas no Javadoc de `ApiFootballDataProvider` e no handoff, para F08 não voltar a esta questão.

### 2.5 Superfície pública que F05 entrega (contrato para F06, F07 e F08)

Tudo em `pt.seerhub.football.service`, tudo em `record`s imutáveis, tudo com o emblema já em URL local.

```java
// A resposta à pergunta de F08: "como acabou este jogo?"
public record FixtureResultView(
        long fixtureId, FixtureStatus status,
        Integer homeScore, Integer awayScore,
        Instant kickoffAt, Instant lastSyncedAt) {
    public boolean terminado()   { return status == FixtureStatus.FINISHED
                                       && homeScore != null && awayScore != null; }
    public boolean naoSeRealizou(){ return status == FixtureStatus.POSTPONED
                                       || status == FixtureStatus.CANCELLED; }
}

public interface FootballCatalogService {
    // === F08 (resolução automática) ===
    Optional<FixtureResultView> resultadoDe(long fixtureId);
    List<FixtureResultView>     resultadosDe(Collection<Long> fixtureIds);   // lote, uma consulta

    // === F06 (catálogo do parser) / F07 (ecrã de revisão) ===
    List<FixtureView> jogosNaJanela(Instant de, Instant ate);
    Optional<FixtureView> jogo(long fixtureId);
    Optional<TeamView>    equipa(long teamId);
}

public record TeamView(long id, String name, String shortName, String country,
                       String normalizedName, String crestUrl) {}   // crestUrl é sempre local
public record FixtureView(long id, long leagueId, String leagueName, String leagueCrestUrl,
                          TeamView home, TeamView away, Instant kickoffAt,
                          FixtureStatus status, Integer homeScore, Integer awayScore) {}
```

E, para F06b, o normalizador é **a única autoridade** sobre normalização de nomes, com assinatura estática e sem estado:

```java
public final class TeamNameNormalizer {
    public static String normalizar(String nome);   // idempotente, nunca devolve null
    public static final Set<String> TOKENS_DE_CLUBE; // público, para F06b documentar o mesmo contrato
}
```

**Regra dura documentada para F06b:** o termo de pesquisa escrito pelo tipster tem de passar por `TeamNameNormalizer.normalizar(...)` **antes** de ser comparado com `teams.normalized_name` ou com `team_aliases.normalized_alias`. Se F06b implementar uma segunda normalização, a correspondência exata deixa de funcionar e a difusa degrada. `FootballCatalogSurfaceIT` prova que `similarity(normalized_name, normalizar('Sporting Lisbon'))` devolve o candidato certo sobre os dados gravados pela sincronização.

### 2.6 Dívidas herdadas que afetam esta feature

1. **`API_KEY` → `API_FOOTBALL_KEY` no `.env` real do utilizador** — registada por F00 e reconfirmada por F01–F04, com F04 a marcá-la "obrigatória antes de F05". Continua a ser **ação do utilizador**. O plano não lê nem edita o `.env`. Se a variável continuar com o nome antigo, a aplicação arranca na mesma (chave vazia é válida) e a sincronização real regista `Chave da API-Football por configurar` sem rebentar; a suite passa de qualquer forma. **Não é bloqueante para a implementação nem para os testes; é bloqueante para o proveito real em produção.** F05 acrescenta duas variáveis novas ao `.env.example` que o utilizador também tem de copiar para o seu `.env` — ver §4.
2. `npm audit` (7 vulnerabilidades de build) — F05 não toca no frontend, não agrava.
3. **Risco levantado pelo planeador de F00: `leagues.provider_id UNIQUE` pode estar errado se o fornecedor reutilizar ids entre épocas.** F05 é a feature que decide. **Decisão: fica como está, e é o desenho correto.** O id de liga do fornecedor é estável entre épocas (a época é um parâmetro separado do pedido, não parte da identidade da liga); a coluna `season` da mesma linha diz qual a época que estamos a acompanhar. Uma linha por liga é também o que as estatísticas do R9 querem ("filtro por liga" significa "Premier League", não "Premier League 2025/26"), e os jogos guardam a data, que é o que separa épocas na prática. **Nenhuma migração é criada por este motivo**, e o teste `FixtureCatalogSyncIT.aMesmaLigaEmEpocaNovaAtualizaALinhaEmVezDeCriarOutra` fixa a decisão: sincronizar a liga 39 com época 2026 depois de existir com época 2025 faz `UPDATE` da linha, não `INSERT` (que violaria a restrição). Se um dia for preciso histórico por época, é uma tabela nova `league_seasons`, nunca uma alteração à identidade de `leagues`.

---

## 3. Critérios de aceitação → testes

Os nove critérios do R5 (texto revisto) decompostos em 55 asserções. Todos os testes correm **sem rede e sem chave de API**.

| # | Critério (R5) | Ficheiro de teste | Nome do teste | Tipo |
| --- | --- | --- | --- | --- |
| 1a | Tarefa agendada, periodicidade configurável, 12h por omissão | `football/FootballScheduledTasksTest.java` | `oCronDoCatalogoPorOmissaoDisparaDeDozeEmDozeHoras` | unit |
| 1b | …anotada com cron configurável, em UTC | `football/FootballScheduledTasksTest.java` | `aTarefaDoCatalogoEstaAgendadaComCronConfiguravelEmUtc` | unit |
| 1c | Sincroniza os jogos das ligas **configuradas** (as seis) | `football/FixtureCatalogSyncIT.java` | `aSincronizacaoDoCatalogoGravaOsJogosDasSeisLigasConfiguradas` | integração |
| 1d | …para os **próximos 7 dias** (janela pedida e respeitada) | `football/FixtureCatalogSyncIT.java` | `aJanelaPedidaAoFornecedorEDeHojeAteSeteDiasDepois` | integração |
| 1e | Jogo fora da janela não é gravado | `football/FixtureCatalogSyncIT.java` | `jogosForaDaJanelaDeSeteDiasNaoSaoGravados` | integração |
| 1f | Custo do catálogo: 1 chamada por liga + no máximo 1 de backfill | `football/FixtureCatalogSyncIT.java` | `oCatalogoCustaUmaChamadaPorLigaMaisNoMaximoUmaDeBackfill` | integração |
| 1g | Idempotente: duas execuções não duplicam jogos nem equipas | `football/FixtureCatalogSyncIT.java` | `correrDuasVezesNaoDuplicaJogosNemEquipas` | integração |
| 1h | Segunda execução atualiza `kickoff_at` e estado alterados pelo fornecedor | `football/FixtureCatalogSyncIT.java` | `umaRemarcacaoDoFornecedorAtualizaAHoraEOEstadoDoJogo` | integração |
| 2a | Tarefa de resultados frequente, cron configurável (15 min), UTC | `football/FootballScheduledTasksTest.java` | `oCronDeResultadosPorOmissaoDisparaDeQuinzeEmQuinzeMinutosEmUtc` | unit |
| 2b | **Um dia sem tips pendentes custa zero chamadas** | `football/PendingResultSyncIT.java` | `umDiaSemSelecoesPendentesCustaZeroChamadas` | integração |
| 2c | Verificação é **em base de dados** antes de qualquer chamada | `football/PendingResultSyncIT.java` | `aProcuraEAvaliadaEmBaseDeDadosAntesDeQualquerChamada` | integração |
| 2d | Jogo começado há **menos** de 2h não gera chamada | `football/PendingResultSyncIT.java` | `jogoComenosDeDuasHorasDesdeOInicioNaoGeraChamada` | integração |
| 2e | Jogo começado há **mais** de 2h e não `FINISHED` gera chamada e grava o resultado | `football/PendingResultSyncIT.java` | `jogoComecadoHaMaisDeDuasHorasEPorTerminarGeraChamadaEGravaOResultado` | integração |
| 2f | Jogo já `FINISHED` não gera chamada | `football/PendingResultSyncIT.java` | `jogoJaTerminadoNaoGeraNovaChamada` | integração |
| 2g | Seleção já resolvida (não `PENDING`) não gera chamada | `football/PendingResultSyncIT.java` | `selecaoJaResolvidaNaoGeraChamada` | integração |
| 2h | Seleção sem jogo associado (`fixture_id NULL`) não gera chamada | `football/PendingResultSyncIT.java` | `selecaoSemJogoAssociadoNaoGeraChamada` | integração |
| 2i | Grupo sincronizado há menos de 30 min não repete a chamada | `football/PendingResultSyncIT.java` | `grupoSincronizadoRecentementeNaoERepetidoNoCicloSeguinte` | integração |
| 2j | Jogo fora da janela de 7 dias no passado deixa de gerar chamadas | `football/PendingResultSyncIT.java` | `jogoForaDaJanelaDeResultadosDeixaDeGerarChamadas` | integração |
| 2k | F05 **não** altera `tip_selections` nem `tips` | `football/PendingResultSyncIT.java` | `aSincronizacaoNuncaAlteraSelecoesNemTips` | integração |
| 3a | Orçamento vem de `API_FOOTBALL_DAILY_BUDGET`, 100 por omissão | `football/FootballPropertiesTest.java` | `oOrcamentoDiarioVemDaVariavelDeAmbienteComOmissaoDeCem` | unit |
| 3b | Valor vazio na variável não rebenta o arranque (cai na omissão) | `football/FootballPropertiesTest.java` | `orcamentoVazioCaiNaOmissaoSemImpedirOArranque` | unit |
| 3c | A variável está no `.env.example` e no `docker-compose.yml` | `football/FootballConventionsTest.java` | `asVariaveisNovasEstaoNoEnvExampleENoDockerCompose` | unit |
| 3d | Reserva atómica: com limite N, N reservas concedidas e a N+1 recusada | `football/ApiCallBudgetIT.java` | `comLimiteDefinidoSaoConcedidasExatamenteNReservasEASeguinteERecusada` | integração |
| 3e | O contador **sobrevive a reinício** (vive na base de dados) | `football/ApiCallBudgetIT.java` | `oContadorSobreviveAReinicioPorqueViveNaBaseDeDados` | integração |
| 3f | Contagem é por dia UTC: o consumo de ontem não conta para hoje | `football/ApiCallBudgetIT.java` | `oConsumoDeOntemNaoContaParaHoje` | integração |
| 3g | Reservas concorrentes nunca ultrapassam o teto | `football/ApiCallBudgetIT.java` | `reservasConcorrentesNuncaUltrapassamOTeto` | integração |
| 3h | A migração `V4` cria a tabela com chave primária por dia | `football/ApiCallBudgetIT.java` | `aTabelaDoOrcamentoTemUmaLinhaPorDiaComChavePrimaria` | integração |
| 3i | Ao atingir o teto, a sincronização **deixa de chamar** e **regista o facto** | `football/BudgetCeilingIT.java` | `aoAtingirOTetoDiarioASincronizacaoDeResultadosDeixaDeChamar` | integração |
| 3j | Não falha silenciosamente: o resultado reporta os grupos ignorados por orçamento | `football/BudgetCeilingIT.java` | `oResultadoDaSincronizacaoReportaOsGruposIgnoradosPorOrcamento` | integração |
| 3k | A reserva de catálogo impede que os resultados esgotem a quota do catálogo | `football/BudgetCeilingIT.java` | `aReservaDeCatalogoImpedeQueOsResultadosEsgotemAQuotaDoCatalogo` | integração |
| 3l | Limite de quota devolvido pelo fornecedor esgota o dia e interrompe o ciclo | `football/BudgetCeilingIT.java` | `oLimiteDeQuotaDoFornecedorEsgotaODiaEInterrompeOCiclo` | integração |
| 3m | **Retoma no dia seguinte** | `football/BudgetCeilingIT.java` | `noDiaSeguinteASincronizacaoVoltaAChamar` | integração |
| 4a | **N seleções em M jogos da mesma liga e dia → UMA chamada, não M** | `football/ResultSyncGroupingIT.java` | `cincoSelecoesPendentesEmTresJogosDaMesmaLigaEDiaCustamUmaSoChamada` | integração |
| 4b | Dias distintos ou ligas distintas custam uma chamada cada | `football/ResultSyncGroupingIT.java` | `diasDistintosOuLigasDistintasCustamUmaChamadaCadaUm` | integração |
| 4c | A chamada identifica liga+época+dia, nunca um jogo individual | `football/ResultSyncGroupingIT.java` | `aChamadaIdentificaLigaEpocaEDiaENuncaUmJogoIndividual` | integração |
| 5a | Ligas gravadas com nome, país, emblema e época | `football/FixtureCatalogSyncIT.java` | `ligasSaoGravadasComNomePaisEmblemaEEpoca` | integração |
| 5b | Equipas gravadas com nome, **nome curto**, país e URL do emblema | `football/FixtureCatalogSyncIT.java` | `equipasSaoGravadasComNomeNomeCurtoPaisEEmblema` | integração |
| 5c | O nome curto chega por backfill limitado quando `/fixtures` não o traz | `football/FixtureCatalogSyncIT.java` | `oNomeCurtoEPreenchidoPorBackfillLimitadoAUmaLigaPorExecucao` | integração |
| 5d | O emblema é descarregado para cache local **durante a sincronização** | `football/CrestCacheIT.java` | `aSincronizacaoDescarregaOsEmblemasParaACacheLocal` | integração |
| 5e | O emblema é servido do disco, com cabeçalhos de cache, sem tocar no CDN | `football/CrestCacheIT.java` | `oEmblemaEServidoDoDiscoComCabecalhosDeCache` | integração |
| 5f | O download de emblemas **não consome orçamento** | `football/CrestCacheIT.java` | `oDescarregamentoDeEmblemasNaoConsomeOrcamento` | integração |
| 5g | Emblema ainda sem cache degrada com redirecionamento, nunca com erro | `football/CrestCacheIT.java` | `emblemaAindaSemCacheRedirecionaParaAOrigemENuncaFalha` | integração |
| 5h | Equipa desconhecida devolve `404` em `ProblemDetail`, nunca `500` | `football/CrestCacheIT.java` | `emblemaDeEquipaDesconhecidaDevolve404EmProblemDetail` | integração |
| 5i | A superfície pública expõe sempre URL local, nunca o do fornecedor | `football/FootballCatalogSurfaceIT.java` | `osEmblemasExpostosPelaSuperficieSaoSempreLocais` | integração |
| 6a | Normalizado: minúsculas, sem acentos, sem prefixos/sufixos de clube | `football/TeamNameNormalizerTest.java` | `normalizaParaMinusculasSemAcentosESemPrefixosOuSufixosDeClube` | unit |
| 6b | Nome só com tokens de clube não fica vazio | `football/TeamNameNormalizerTest.java` | `nomeFormadoSoPorTokensDeClubeNaoFicaVazio` | unit |
| 6c | Idempotente e null-safe | `football/TeamNameNormalizerTest.java` | `normalizarEIdempotenteENuncaDevolveNull` | unit |
| 6d | Cobre as variantes de escrita que o R6 vai exigir | `football/TeamNameNormalizerTest.java` | `asVariantesDeEscritaDoRequisitoSeisNormalizamParaAFormaEsperada` | unit |
| 6e | O normalizado é **gerado na sincronização** e gravado na coluna | `football/FixtureCatalogSyncIT.java` | `oNomeNormalizadoEGeradoNaSincronizacaoEGravadoNaColuna` | integração |
| 6f | Mudança de nome no fornecedor regenera o normalizado | `football/FixtureCatalogSyncIT.java` | `mudancaDeNomeNoFornecedorRegeneraONomeNormalizado` | integração |
| 6g | O índice de trigramas serve a correspondência difusa do R6 | `football/FootballCatalogSurfaceIT.java` | `aPesquisaPorTrigramasSobreNomesNormalizadosDevolveOCandidatoCerto` | integração |
| 7a | Existe a interface `FootballDataProvider` com implementação real e de teste | `football/FootballProviderWiringIT.java` | `existemDuasImplementacoesDoFornecedorEOContextoDeTesteSoTemADeDadosFixos` | integração |
| 7b | Só o serviço de sincronização depende do fornecedor | `football/FootballConventionsTest.java` | `soOServicoDeSincronizacaoDependeDoFornecedor` | unit |
| 7c | O perfil de teste usa dados fixos; o perfil por omissão usa o real | `football/FootballConventionsTest.java` | `oPerfilDeTesteUsaDadosFixosEOPerfilPorOmissaoOFornecedorReal` | unit |
| 7d | Toda a suite corre **sem chave de API**: sem chave, nada rebenta | `football/FootballProviderFailureIT.java` | `semChaveDeApiASincronizacaoRegistaEDevolveSemRebentar` | integração |
| 7e | Nenhum recurso de teste declara chave ou URL do fornecedor real | `football/FootballConventionsTest.java` | `nenhumRecursoDeTesteDeclaraChaveOuUrlDoFornecedorReal` | unit |
| 8a | Falha do fornecedor é **registada** e não propaga exceção | `football/FootballProviderFailureIT.java` | `falhaDoFornecedorERegistadaENaoPropagaExcecao` | integração |
| 8b | Uma liga em falha não impede as restantes | `football/FootballProviderFailureIT.java` | `umaLigaEmFalhaNaoImpedeAsRestantes` | integração |
| 8c | **Retenta no ciclo seguinte e recupera** (API em baixo durante horas) | `football/FootballProviderFailureIT.java` | `comOFornecedorEmBaixoVariosCiclosOSeguinteRecuperaSozinho` | integração |
| 8d | **Nenhum pedido de utilizador falha** com o fornecedor em baixo | `football/FootballProviderFailureIT.java` | `comOFornecedorEmBaixoOsPedidosDeUtilizadorRespondemDaBaseDeDados` | integração |
| 8e | Liga sem jogos no fornecedor: não apaga o catálogo nem falha | `football/FootballProviderFailureIT.java` | `ligaSemJogosNoFornecedorNaoApagaOCatalogoNemFalha` | integração |
| 8f | Jogo adiado/cancelado: estado gravado, sai da fila, seleções intactas | `football/FootballProviderFailureIT.java` | `jogoAdiadoOuCanceladoGravaOEstadoSaiDaFilaENaoResolveSelecoes` | integração |
| 8g | Estado desconhecido do fornecedor não degrada o estado gravado | `football/ApiFootballStatusMapperTest.java` | `estadoDesconhecidoNaoDegradaOEstadoJaGravado` | unit |
| 8h | Mapeamento de estados do fornecedor para os cinco do baseline | `football/ApiFootballStatusMapperTest.java` | `osCodigosDoFornecedorMapeiamParaOsCincoEstadosDoBaseline` | unit |
| 8i | Golos gravados são os dos 90 minutos, não os do prolongamento | `football/ApiFootballStatusMapperTest.java` | `osGolosGravadosSaoOsDoTempoRegulamentarENaoOsDoProlongamento` | unit |
| 9a | O número de chamadas do dia está exposto numa métrica | `football/FootballMetricsIT.java` | `oNumeroDeChamadasDoDiaEExpostoNaMetrica` | integração |
| 9b | A métrica acompanha o consumo real depois de uma sincronização | `football/FootballMetricsIT.java` | `aMetricaAcompanhaOConsumoDepoisDeUmaSincronizacao` | integração |
| 9c | O endpoint de métricas responde a `ADMIN` e recusa a um utilizador normal | `football/FootballMetricsIT.java` | `oEndpointDeMetricasResponde AoAdminERecusaAosRestantes` | integração |
| X1 | `leagues.provider_id UNIQUE` está certo: nova época atualiza a linha | `football/FixtureCatalogSyncIT.java` | `aMesmaLigaEmEpocaNovaAtualizaALinhaEmVezDeCriarOutra` | integração |
| X2 | A época é derivada do relógio e sobreponível por configuração | `football/SeasonResolverTest.java` | `aEpocaEDerivadaDoRelogioESobreponivelPorConfiguracao` | unit |
| X3 | A tarefa engole exceções (um `@Scheduled` que rebenta mata o agendador) | `football/FootballScheduledTasksTest.java` | `asTarefasDelegamNoServicoEEngolemFalhas` | unit |
| X4 | Duas execuções concorrentes não duplicam consumo (guarda de sobreposição) | `football/FootballScheduledTasksTest.java` | `umaSegundaExecucaoConcorrenteEIgnoradaEmVezDeDuplicarConsumo` | unit |

**Nenhum critério fica sem teste.** Correção de nome no 9c: `oEndpointDeMetricasRespondeAoAdminERecusaAosRestantes` (sem espaço).

### 3.1 Os dois testes que a spec exige em texto, especificados ao pormenor

**Teste de agrupamento (critério 4, linha 4a) — `ResultSyncGroupingIT.cincoSelecoesPendentesEmTresJogosDaMesmaLigaEDiaCustamUmaSoChamada`**

*Como o fornecedor de dados fixos conta chamadas:* `FixedFootballDataProvider` mantém uma `List<ProviderCall>` sincronizada. Cada método de dados acrescenta **exatamente um** registo antes de devolver, com o que foi pedido:
`ProviderCall(Tipo tipo, Long ligaProviderId, Integer epoca, LocalDate de, LocalDate ate)`, com `Tipo ∈ {JOGOS_ENTRE_DATAS, JOGOS_DO_DIA, EQUIPAS_DA_LIGA}`. `emblema(url)` **não** regista nada (não é chamada de dados). Métodos de apoio: `chamadas()` (cópia imutável), `limparRegisto()`, `contarChamadas()`.

*Arranjo:* com o `Clock` da aplicação, `hoje = LocalDate.now(clock)`; `dia = hoje.minusDays(1)`; liga do fornecedor `39`, época `2026`, gravada por `FootballTestSupport.inserirLiga(39, "Premier League", 2026)`. Três jogos dessa liga nesse dia, `kickoff_at = dia@14:00Z / 16:30Z / 19:00Z`, todos com `agora − kickoff > 2h`, `status = 'SCHEDULED'`, `last_synced_at = NULL`. Uma comunidade e um utilizador criados com `CommunityTestSupport`/`AuthTestSupport`; uma `tips` e **cinco** linhas em `tip_selections` com `status = 'PENDING'` distribuídas 2/2/1 pelos três jogos, inseridas por `FootballTestSupport.inserirSelecaoPendente(tipId, fixtureId)` (JDBC direto, precedente de `CommunityTestSupport.inserirMembership`). `fake.limparRegisto()`; orçamento do dia posto a zero.

*Ação:* `SyncOutcome resultado = footballSyncService.sincronizarResultadosPendentes();`

*Asserções (todas, nesta ordem):*
1. `assertThat(fake.chamadas()).hasSize(1);` — **a asserção que o critério exige**: uma, não cinco, não três.
2. `assertThat(fake.chamadas().get(0)).isEqualTo(new ProviderCall(JOGOS_DO_DIA, 39L, 2026, dia, dia));` — a única chamada identifica liga+época+dia.
3. `assertThat(resultado.chamadasEfetuadas()).isEqualTo(1);`
4. `assertThat(footballTestSupport.consumoDoDia()).isEqualTo(1);` — o orçamento foi debitado exatamente uma vez.
5. Os três jogos ficam com o estado e resultado que o fornecedor fixo tinha definido, e `last_synced_at` não nulo nos três.
6. `assertThat(footballTestSupport.estadosDasSelecoes(tipId)).containsOnly("PENDING");` — F05 não resolve nada.

*Complemento (4b):* com um quarto jogo noutro dia e um quinto noutra liga no mesmo dia, `chamadas()` passa a ter **3** registos e não 5 — o agrupamento é por `(liga, dia)`.

**Teste do orçamento (critério 3, linhas 3d/3i/3k/3m) — `ApiCallBudgetIT` e `BudgetCeilingIT`**

*3d, `comLimiteDefinidoSaoConcedidasExatamenteNReservasEASeguinteERecusada`:* com o dia limpo, um ciclo de `budgetService.tentarReservar(limite)` com `limite = 5` devolve `true` cinco vezes e `false` à sexta; `budgetService.consumidoHoje() == 5`; a linha em `api_call_budget` tem `calls_used = 5`. Nenhuma exceção, nenhuma chamada ao fornecedor envolvida (é só o contador).

*3g, `reservasConcorrentesNuncaUltrapassamOTeto`:* 32 threads virtuais tentam reservar 200 vezes com `limite = 50`; contam-se os `true`. `assertThat(sucessos).isEqualTo(50)` e `calls_used == 50` — **exatamente**, nem 49 nem 51. É esta a prova de que a reserva é atómica.

*3e, `oContadorSobreviveAReinicioPorqueViveNaBaseDeDados`:* reservam-se 3 unidades; constrói-se **uma instância nova** de `ApiCallBudgetService` com o mesmo repositório (o equivalente testável de um reinício de processo); `consumidoHoje()` continua a ser 3 e a quarta reserva com `limite = 3` é recusada.

*3i/3j, `aoAtingirOTetoDiarioASincronizacaoDeResultadosDeixaDeChamar`:* arranjo igual ao teste de agrupamento (procura real existente), mas com `footballTestSupport.esgotarOrcamentoDoDia()` (um `INSERT`/`UPDATE` direto pondo `calls_used = daily-budget`) antes da ação. Asserções: `fake.chamadas()` **vazia**; `resultado.chamadasEfetuadas() == 0`; `resultado.gruposConsiderados() == 1`; `resultado.gruposIgnoradosPorOrcamento() == 1` (não falha silenciosamente); os jogos ficam intactos (`status` ainda `SCHEDULED`); e — a parte que o R5 exige em prosa — `GET /api/football/crests/teams/{id}` e a superfície de leitura continuam a responder `200`.

*3k, `aReservaDeCatalogoImpedeQueOsResultadosEsgotemAQuotaDoCatalogo`:* com `calls_used = daily-budget − catalog-reserve` (80 de 100), a sincronização de **resultados** faz zero chamadas, mas a sincronização do **catálogo** logo a seguir faz as suas 6. Prova que os dois tetos são distintos.

*3m, `noDiaSeguinteASincronizacaoVoltaAChamar`:* o teste usa um `Clock` fixo injetado no serviço (construção direta, sem contexto novo) a apontar para o dia seguinte; a mesma procura passa a produzir uma chamada, e a linha de ontem em `api_call_budget` fica intacta com `calls_used = 100`.

---

## 4. Alterações

### Ficheiros a criar

**Backend — produção (34 ficheiros + 1 migração), todos sob `backend/src/main/java/pt/seerhub/football/` salvo indicação:**

| Caminho | Propósito |
| --- | --- |
| `domain/FixtureStatus.java` | Enum `SCHEDULED, LIVE, FINISHED, POSTPONED, CANCELLED` — exatamente os cinco valores do `CHECK` de `V2`. |
| `domain/League.java` | `@Entity @Table(name="leagues")`. Campos: `id`, `providerId`, `name`, `country`, `logoUrl`, `season`, `active`. Sem `@Version` (a tabela não tem coluna). Sem coleções nem `@OneToMany` (regra D-16 de F02). |
| `domain/Team.java` | `@Entity @Table(name="teams")`: `id, providerId, name, normalizedName, shortName, country, logoUrl`. `normalizedName` é sempre atribuído por `TeamNameNormalizer` no serviço, nunca pelo chamador. |
| `domain/Fixture.java` | `@Entity @Table(name="fixtures")`: `id, providerId, league (@ManyToOne LAZY), homeTeam, awayTeam, kickoffAt, status (@Enumerated(STRING)), homeScore, awayScore, lastSyncedAt`. |
| `repo/LeagueRepository.java` | `JpaRepository<League,Long>`: `Optional<League> findByProviderId(long)`, `List<League> findByActiveTrue()`. |
| `repo/TeamRepository.java` | `JpaRepository<Team,Long>`: `Optional<Team> findByProviderId(long)`, `List<Team> findByProviderIdIn(Collection<Long>)`, e a consulta nativa `List<Long> idsDeLigasComEquipasSemNomeCurto()` (junta `fixtures` para saber a que liga pertence a equipa). |
| `repo/FixtureRepository.java` | `JpaRepository<Fixture,Long>`: `Optional<Fixture> findByProviderId(long)`, `List<Fixture> findByProviderIdIn(...)`, `List<Fixture> findByKickoffAtBetweenOrderByKickoffAtAsc(...)`, e o `@Modifying` em bloco `int marcarSincronizados(long ligaId, Instant inicioDoDia, Instant fimDoDia, Instant quando)`. |
| `repo/ApiCallBudgetRepository.java` | `@Repository` com `JdbcTemplate` (**não** interface Spring Data — ver D-3). `boolean tentarReservar(LocalDate dia, int limite)` (o `INSERT ... ON CONFLICT ... WHERE` de §2.4), `int consumidasEm(LocalDate dia)`, `void esgotarDia(LocalDate dia, int limite)`. |
| `repo/PendingResultRepository.java` | `@Repository` com `JdbcTemplate`: `List<PendingResultGroup> gruposPorResolver(Instant inicioDaJanela, Instant limiteDeArranque, Instant limiteDeRepeticao, int maximo)` — o SQL nativo de §2.4. Não cria entidade para `tip_selections` (D-4). |
| `repo/PendingResultGroup.java` | `record (long ligaId, long ligaProviderId, int epoca, LocalDate dia, int jogos)`. |
| `provider/FootballDataProvider.java` | A porta: `List<ProviderFixture> jogosDaLigaEntre(long ligaProviderId, int epoca, LocalDate de, LocalDate ate)`, `List<ProviderFixture> jogosDaLigaNoDia(long, int, LocalDate)`, `List<ProviderTeam> equipasDaLiga(long, int)`, `Optional<byte[]> emblema(String url)`. Javadoc fixa: os três primeiros custam 1 unidade de orçamento cada, o quarto custa 0. |
| `provider/ProviderLeague.java` | `record (long providerId, String name, String country, String logoUrl, int season)`. |
| `provider/ProviderTeam.java` | `record (long providerId, String name, String shortName, String country, String logoUrl)`. |
| `provider/ProviderFixture.java` | `record (long providerId, ProviderLeague league, ProviderTeam home, ProviderTeam away, Instant kickoffAt, FixtureStatus status, Integer homeScore, Integer awayScore)`. `status == null` significa "estado desconhecido, não alterar o gravado". |
| `provider/ProviderCall.java` | `record (Tipo tipo, Long ligaProviderId, Integer epoca, LocalDate de, LocalDate ate)` + `enum Tipo`. O registo que o fornecedor de dados fixos acumula e os testes de agrupamento asseveram. |
| `provider/FootballProviderException.java` | `RuntimeException` com `boolean quotaExcedida()` e fábricas `falhaDeRede`, `respostaInvalida`, `quotaEsgotada`, `semChave`. |
| `provider/ApiFootballStatusMapper.java` | `Optional<FixtureStatus> mapear(String codigoCurto)` + `Integer[] golosRegulamentares(JsonNode)`. Isolado por ser a peça mais frágil e a mais barata de testar. |
| `provider/ApiFootballDataProvider.java` | A implementação real, com `RestClient`, cabeçalho `x-apisports-key`, `timeout` configurável, parsing por `JsonNode` (resiliente ao `errors` que ora é objeto ora é lista), leitura de `x-ratelimit-requests-remaining`. Sem chave configurada lança `FootballProviderException.semChave()` **na chamada**, nunca no arranque. |
| `provider/FixedFootballDataProvider.java` | A implementação de dados fixos: catálogo em memória gerado a partir do `Clock` (ver §4.1), registo de chamadas, e mutadores para os testes (`definirResultado`, `definirEstado`, `falharNaProximaChamada`, `devolverQuotaEsgotada`, `esvaziarLiga`, `renomearEquipa`, `limparRegisto`). Vive em `main` deliberadamente (D-5). |
| `service/TeamNameNormalizer.java` | O normalizador — regras em §4.2. `final`, construtor privado, método estático, sem estado. |
| `service/SeasonResolver.java` | `int epocaAtual()` — usa o override configurado se existir, senão deriva do `Clock` (mês ≥ 7 → ano; senão ano − 1). |
| `service/ApiCallBudgetService.java` | `boolean tentarReservar(int limite)`, `int consumidoHoje()`, `int restanteHoje(int limite)`, `void esgotarDiaPorSinalDoFornecedor(int limite)`. Usa `Clock` para o dia UTC. |
| `service/SyncOutcome.java` | `record (int gruposConsiderados, int chamadasEfetuadas, int gruposIgnoradosPorOrcamento, int jogosAtualizados, int falhas, boolean ignoradoPorConcorrencia)` + fábricas `semTrabalho()`, `ignorado()`. |
| `service/FootballSyncService.java` | O motor: `sincronizarCatalogo()`, `sincronizarResultadosPendentes()`, e os `upsert` privados de liga/equipa/jogo. **É a única classe que depende de `FootballDataProvider`** (imposto por teste). Guarda de sobreposição com `ReentrantLock.tryLock()`. |
| `service/FootballCatalogService.java` | A superfície de leitura de §2.5 (interface + implementação `FootballCatalogServiceImpl`? **não** — classe concreta `@Service`, para não multiplicar tipos; a assinatura em §2.5 é o contrato). |
| `service/FixtureResultView.java` | O record que F08 consome. |
| `service/TeamView.java` | Record com `crestUrl` já local. |
| `service/FixtureView.java` | Record do catálogo para F06/F07. |
| `service/CrestCacheService.java` | `void garantirEmblemaDaEquipa(Team)`, `void garantirEmblemaDaLiga(League)`, `Optional<Path> ficheiroDaEquipa(long)`, `Optional<Path> ficheiroDaLiga(long)`, `String urlPublicoDaEquipa(long)`. Limite de tamanho (512 KB) e limite de descarregamentos por execução. |
| `service/FootballMetrics.java` | `@Component` que regista, no `MeterRegistry`: gauge `seerhub.football.api.calls.today`, gauge `seerhub.football.api.budget.remaining`, counter `seerhub.football.api.calls.total{outcome=sucesso|falha|sem_orcamento}`. |
| `service/FixtureCatalogSyncTask.java` | `@Scheduled(cron = "${seerhub.football.catalog-cron:" + CRON_POR_OMISSAO + "}", zone = "UTC")`, `CRON_POR_OMISSAO = "0 7 */12 * * *"`. Delega e engole exceções. |
| `service/PendingResultSyncTask.java` | `@Scheduled(cron = "${seerhub.football.result-cron:" + CRON_POR_OMISSAO + "}", zone = "UTC")`, `CRON_POR_OMISSAO = "0 */15 * * * *"`. Idem. |
| `api/CrestController.java` | `GET /api/football/crests/teams/{teamId}` e `GET /api/football/crests/leagues/{leagueId}`. Devolve `ResponseEntity<Resource>` com `Cache-Control: public, max-age=604800` e `Content-Type` derivado da extensão. |
| `config/FootballProviderConfig.java` | `RestClient` dedicado (timeouts de ligação e leitura a partir de `request-timeout`), e a escolha da implementação por `@ConditionalOnProperty(prefix="seerhub.football", name="provider", havingValue="api-football"|"fake")`. |
| `backend/src/main/resources/db/migration/V4__api_call_budget.sql` | A tabela do orçamento (DDL em §4.3). |

**Backend — testes (16 ficheiros), sob `backend/src/test/java/pt/seerhub/`:**

| Caminho | Propósito |
| --- | --- |
| `support/FootballTestSupport.java` | Inserção JDBC direta de ligas, equipas, jogos, `tips` e `tip_selections`; leitura/escrita de `api_call_budget`; `consumoDoDia()`, `esgotarOrcamentoDoDia()`, `estadosDasSelecoes(tipId)`, `lerColunaDoJogo(...)`. |
| `football/TeamNameNormalizerTest.java` | 4 testes (6a–6d). |
| `football/ApiFootballStatusMapperTest.java` | 3 testes (8g–8i). |
| `football/SeasonResolverTest.java` | 1 teste (X2), com `Clock.fixed` em julho e em fevereiro. |
| `football/FootballScheduledTasksTest.java` | 5 testes (1a, 1b, 2a, X3, X4) — sem contexto Spring, `CronExpression` + `Mockito.mock` (já disponível, sem dependência nova). |
| `football/FootballPropertiesTest.java` | 2 testes (3a, 3b) com `ApplicationContextRunner`, no mesmo estilo de `SeerHubPropertiesTest`. |
| `football/FootballConventionsTest.java` | 5 testes (3c, 7b, 7c, 7e + `aVariavelDaEpocaEstaNoEnvExample`) — mecânicos, leem ficheiros via `RepoRoot.find()`. |
| `football/ApiCallBudgetIT.java` | 5 testes (3d–3h). |
| `football/FixtureCatalogSyncIT.java` | 12 testes (1c–1h, 5a–5c, 6e, 6f, X1). |
| `football/PendingResultSyncIT.java` | 10 testes (2b–2k). |
| `football/ResultSyncGroupingIT.java` | 3 testes (4a–4c). |
| `football/BudgetCeilingIT.java` | 5 testes (3i–3m). |
| `football/FootballProviderFailureIT.java` | 7 testes (7d, 8a–8f). |
| `football/CrestCacheIT.java` | 5 testes (5d–5h). |
| `football/FootballCatalogSurfaceIT.java` | 3 testes (5i, 6g + `aSuperficieDeResultadosDevolveOEstadoEOsGolosParaF08`). |
| `football/FootballMetricsIT.java` | 3 testes (9a–9c). |
| `football/FootballProviderWiringIT.java` | 1 teste (7a). |

**Total: 52 ficheiros a criar.** Testes novos previstos: **77** (200 → **277** JUnit). Frontend: **zero ficheiros**, 26 testes Vitest inalterados.

### Ficheiros a editar

| Caminho | Alteração | Risco |
| --- | --- | --- |
| `backend/src/main/java/pt/seerhub/config/SeerHubProperties.java` | Estender o record aninhado `Football` com: `String provider`, `Integer dailyBudget`, `Integer catalogReserve`, `Integer season`, `Duration catalogHorizon`, `Duration resultDelay`, `Duration resultWindow`, `Duration minResultInterval`, `Integer maxGroupsPerRun`, `Duration requestTimeout`, `Integer crestDownloadsPerRun`, `List<LeagueConfig> leagues` (novo record aninhado `LeagueConfig(Long providerId, String name, String country)`), mais acessores com omissão (`orcamentoDiario()`, `reservaDeCatalogo()`, …) que devolvem o valor por omissão quando o componente é `null`. **Sem `@NotNull`/`@NotBlank` em nenhum componente novo.** | **Médio.** `SeerHubPropertiesTest.ligaTodasAsPropriedadesQuandoOAmbienteEstaCompleto` corre com `ApplicationContextRunner` e só define `api-key`/`base-url`: qualquer anotação de validação nova, ou um componente primitivo (`int` em vez de `Integer`), parte esse teste. Os acessores com omissão são a mitigação; o binder converte string vazia em `null` para tipos wrapper, nunca para primitivos. |
| `backend/src/main/java/pt/seerhub/config/SecurityConfig.java` | Duas linhas novas antes de `anyRequest()`: `.requestMatchers(HttpMethod.GET, "/api/football/crests/**").permitAll()` (os emblemas aparecem no perfil público de uma comunidade, R11) e `.requestMatchers("/actuator/metrics/**").hasRole("ADMIN")`. Nada mais é tocado. | **Baixo.** `SecurityConfigConventionsTest` só verifica que a substring `__test__` não regressa; `SecurityChainIT` testa `/__test__/boom` e `/actuator/health`, ambos inalterados. |
| `backend/src/main/resources/application.yml` | Bloco `seerhub.football.*` completo (§4.4) e `management.endpoints.web.exposure.include: health,metrics`. | **Médio.** `ConfigurationConventionsTest.nenhumFicheiroVersionadoContemSegredoComAparenciaReal` varre `backend/src/main/resources/**` à procura de sequências `[A-Za-z0-9_-]{40,}`: nenhuma linha nova pode ter um identificador contínuo com 40+ caracteres. `segredosSaoSempreReferenciasAVariaveisDeAmbiente` exige que `api-key: ${API_FOOTBALL_KEY` continue tal e qual — **não reordenar nem reformatar essa linha**. |
| `backend/src/test/resources/application-test.yml` | `seerhub.football.provider: fake`, `catalog-cron: "-"`, `result-cron: "-"`, `daily-budget: 100`, `catalog-reserve: 20`, `season: 2026`, e `seerhub.uploads.dir: ${java.io.tmpdir}/seerhub-test-uploads` (para os emblemas de teste não escreverem dentro do módulo). | **Baixo.** `ConfigurationConventionsTest` também lê este ficheiro, mas só à procura de `ddl-auto`, que não é tocado. |
| `.env.example` | Duas linhas novas na secção da API-Football: `API_FOOTBALL_DAILY_BUDGET=100` e `API_FOOTBALL_SEASON=` (vazio = derivada do relógio). | **Médio.** `EnvExampleTest` compara **nos dois sentidos**: as duas variáveis têm de aparecer em `application.yml` **e** em `docker-compose.yml`, senão um dos dois testes fica vermelho. |
| `docker-compose.yml` | Duas linhas no `environment` do serviço `backend`: `API_FOOTBALL_DAILY_BUDGET: ${API_FOOTBALL_DAILY_BUDGET}` e `API_FOOTBALL_SEASON: ${API_FOOTBALL_SEASON}`. | **Médio.** `DockerComposeTest.nenhumValorDeEnvironmentEUmLiteral` exige o padrão exato `^\$\{[A-Z_][A-Z0-9_]*}$` — **é proibido usar `${VAR:-omissão}`**. Consequência aceite e coberta: se o utilizador não definir a variável no `.env`, o contentor recebe string vazia, e é por isso que os componentes são `Integer` com acessor de omissão (teste 3b). |

**Total: 6 ficheiros a editar. 58 ficheiros ao todo.**

**Ação do utilizador (não é código):** acrescentar ao `.env` real, além do já pendente `API_FOOTBALL_KEY`, as linhas `API_FOOTBALL_DAILY_BUDGET=100` e `API_FOOTBALL_SEASON=`. Sem isso o `docker compose` avisa e passa string vazia — que a aplicação trata como omissão, mas o aviso é ruído desnecessário. **O implementador não lê nem edita o `.env`.**

### 4.1 O conjunto de dados fixos (contrato antecipado para F06)

`FixedFootballDataProvider` gera o catálogo a partir do `Clock` injetado, para estar sempre dentro da janela de 7 dias. Ids do fornecedor fictícios mas estáveis. **Este conjunto é escolhido para ser exatamente o que o R6 vai precisar** (as cinco linhas do exemplo da spec, mais as variantes de escrita do critério de aceitação do R6).

| Liga (id/época) | Jogo (id) | Casa | Fora | Início |
| --- | --- | --- | --- | --- |
| 94 Primeira Liga / Portugal | 9401 | SL Benfica (`BEN`) | FC Porto (`POR`) | hoje+1 19:45Z |
| 94 | 9402 | Sporting CP (`SCP`) | SC Braga (`SCB`) | hoje+1 21:15Z |
| 39 Premier League / England | 3901 | Arsenal (`ARS`) | Chelsea (`CHE`) | hoje+1 17:30Z |
| 39 | 3902 | Manchester United (`MUN`) | Liverpool (`LIV`) | hoje+2 16:00Z |
| 140 La Liga / Spain | 14001 | Girona (`GIR`) | Real Madrid (`RMA`) | hoje+2 20:00Z |
| 135 Serie A / Italy | 13501 | Internazionale (`INT`) | Napoli (`NAP`) | hoje+3 19:45Z |
| 78 Bundesliga / Germany | 7801 | Bayern München (`FCB`) | RB Leipzig (`RBL`) | hoje+2 18:30Z |
| 61 Ligue 1 / France | 6101 | Paris Saint-Germain (`PSG`) | Olympique Marseille (`OM`) | hoje+3 20:00Z |

Duas propriedades deliberadas: os jogos 9401/9402 e 3901 caem **no mesmo dia**, o que dá material natural ao teste de agrupamento; e os nomes cobrem os casos difíceis de normalização (`SL`, `FC`, `CP`, `SC`, `RB` como tokens de clube; `München` com trema; `Saint-Germain` com hífen; `Internazionale` vs `Inter`).

`emblema(url)` devolve sempre os mesmos 68 bytes de um PNG 1×1 — suficiente para provar a cache local, offline.

### 4.2 Regras do normalizador (contrato para F06b)

`TeamNameNormalizer.normalizar(String)`, por ordem:

1. `null` ou em branco → `""`.
2. Normalização Unicode `NFD` e remoção das marcas combinatórias (`\p{Mn}`) → `Atlético` → `Atletico`, `München` → `Munchen`.
3. Minúsculas com `Locale.ROOT`.
4. Todo o carácter fora de `[a-z0-9]` passa a espaço (trata `.`, `-`, `'`, `/`).
5. Remoção de tokens de clube quando aparecem como palavra inteira, no início ou no fim (nunca no meio): `fc, cf, sc, cd, ca, ac, ad, as, us, sl, sd, ud, rc, rcd, cfc, afc, bsc, ssc, sv, tsv, vfb, vfl, fsv, rb, ogc, sco, fk, nk, if, ff, bk, cp, sad`.
6. Se o passo 5 esvaziar a cadeia, **desfaz-se**: fica o resultado do passo 4 (`"FC"` → `fc`, nunca `""`).
7. Colapso de espaços múltiplos e `trim`.
8. Truncatura a 120 caracteres (limite da coluna `teams.normalized_name`).

Exemplos fixados no teste 6a/6d: `SL Benfica`→`benfica`; `FC Porto`→`porto`; `Sporting CP`→`sporting`; `Sporting Lisbon`→`sporting lisbon`; `Manchester United`→`manchester united`; `Man Utd`→`man utd`; `Internazionale`→`internazionale`; `Inter`→`inter`; `Atlético Madrid`→`atletico madrid`; `Bayern München`→`bayern munchen`; `Paris Saint-Germain`→`paris saint germain`; `RB Leipzig`→`leipzig`; `1899 Hoffenheim`→`1899 hoffenheim`.

**Nota explícita para F06b:** `Sporting` e `Sporting Lisbon` **não** normalizam para a mesma cadeia — é a semelhança por trigramas (`similarity('sporting','sporting lisbon') ≈ 0,53`) e os aliases que fecham essa distância, exatamente como o R6 descreve nas suas três vias. F05 garante o dado e o índice; a decisão de limiar é de F06b.

### 4.3 Modelo de dados / migração

Única migração nova, `backend/src/main/resources/db/migration/V4__api_call_budget.sql`. **Nenhuma alteração a `leagues`, `teams`, `fixtures` ou `team_aliases`** (ver §2.6.3 sobre `provider_id UNIQUE`).

```sql
-- Orçamento diário de chamadas à API-Football (R5, critério 3).
-- Uma linha por dia UTC. A reserva é feita com um único INSERT ... ON CONFLICT
-- ... DO UPDATE ... WHERE, atómico: devolve 1 linha afetada quando reservou e 0
-- quando o teto do dia já foi atingido. Vive em base de dados, e não em memória,
-- porque o backend corre com "restart: unless-stopped" — um contador em memória
-- voltaria a zero a cada reinício e a garantia de nunca exceder a quota deixaria
-- de existir precisamente no cenário em que ela importa.
-- Retenção: as linhas são pequenas e permanentes (uma por dia); servem de
-- histórico de consumo. Nomes abaixo de 40 caracteres por causa de
-- ConfigurationConventionsTest.nenhumFicheiroVersionadoContemSegredoComAparenciaReal.

CREATE TABLE api_call_budget (
    day        DATE        NOT NULL PRIMARY KEY,
    calls_used INTEGER     NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_acb_calls CHECK (calls_used >= 0)
);
```

Sem entidade JPA associada (D-3): a tabela só é tocada por SQL nativo em `ApiCallBudgetRepository`, o que evita ter de conciliar um `@Entity` com um `INSERT ... ON CONFLICT` e mantém `ddl-auto: validate` fora do caminho.

### 4.4 Configuração acrescentada a `application.yml`

```yaml
seerhub:
  football:
    api-key: ${API_FOOTBALL_KEY:}             # linha existente — NÃO tocar nem reformatar
    base-url: ${API_FOOTBALL_BASE_URL:https://v3.football.api-sports.io}   # existente
    provider: api-football                    # "fake" só no perfil de teste
    daily-budget: ${API_FOOTBALL_DAILY_BUDGET:100}
    catalog-reserve: 20                       # unidades que os resultados nunca gastam
    season: ${API_FOOTBALL_SEASON:}           # vazio = derivada do relógio
    catalog-cron: "0 7 */12 * * *"            # 00:07 e 12:07 UTC — 12h de periodicidade
    catalog-horizon: P7D
    result-cron: "0 */15 * * * *"
    result-delay: PT2H                        # só olha para jogos começados há 2h+
    result-window: P7D                        # e ainda não há mais de 7 dias
    min-result-interval: PT30M                # não repete o mesmo grupo antes disto
    max-groups-per-run: 4
    request-timeout: PT10S
    crest-downloads-per-run: 50
    leagues:
      - provider-id: 94
        name: Primeira Liga
        country: Portugal
      - provider-id: 39
        name: Premier League
        country: England
      - provider-id: 140
        name: La Liga
        country: Spain
      - provider-id: 135
        name: Serie A
        country: Italy
      - provider-id: 78
        name: Bundesliga
        country: Germany
      - provider-id: 61
        name: Ligue 1
        country: France
```

Se algum `provider-id` estiver errado, a sincronização dessa liga devolve zero jogos, regista `log.warn` com o id, e **as outras cinco continuam** (teste 8b/8e). Corrige-se editando esta lista, sem tocar em código.

---

## 5. Ordem de implementação

Cada passo compila e corre a suite inteira verde antes do seguinte. Os testes entram com o código do passo, nunca no fim.

1. **Migração e contador.** `V4__api_call_budget.sql`, `ApiCallBudgetRepository` (JDBC), `ApiCallBudgetService`, `FootballTestSupport` (parte do orçamento), `ApiCallBudgetIT` (5 testes: 3d–3h). Verificar `./mvnw test` verde antes de escrever mais uma linha — é o alicerce de tudo o resto e o único sítio onde uma corrida de concorrência pode existir.
2. **Normalizador e época.** `TeamNameNormalizer`, `SeasonResolver`, `TeamNameNormalizerTest`, `SeasonResolverTest`. Puros, sem base de dados. Fixam já o contrato que F06b vai consumir.
3. **Entidades e repositórios.** `FixtureStatus`, `League`, `Team`, `Fixture`, os três `JpaRepository`, `PendingResultGroup`, `PendingResultRepository`. **Ponto de risco de `ddl-auto: validate`:** correr a suite aqui prova que o mapeamento bate certo com `V2` antes de existir lógica.
4. **A porta e os `record`s neutros.** `FootballDataProvider`, `ProviderLeague/Team/Fixture`, `ProviderCall`, `FootballProviderException`.
5. **O fornecedor de dados fixos.** `FixedFootballDataProvider` com o conjunto de §4.1 e o registo de chamadas; `FootballProviderConfig` com a escolha por propriedade; `application-test.yml` a passar para `provider: fake`; `FootballProviderWiringIT` (7a) e `FootballConventionsTest` (7b, 7c, 7e). Só depois disto é que existe forma de testar o motor.
6. **Propriedades.** Estender `SeerHubProperties.Football`, escrever o bloco de `application.yml`, acrescentar as duas variáveis a `.env.example` e a `docker-compose.yml`, `FootballPropertiesTest` (3a, 3b) e a linha 3c de `FootballConventionsTest`. **Correr `./mvnw test -Dtest=EnvExampleTest,DockerComposeTest,ConfigurationConventionsTest,SeerHubPropertiesTest` aqui** — são os quatro testes herdados com maior probabilidade de partir, e é barato descobrir já.
7. **Sincronização do catálogo.** `FootballSyncService.sincronizarCatalogo()` com os `upsert` de liga/equipa/jogo, o backfill limitado de nome curto, e `FixtureCatalogSyncTask`; `FixtureCatalogSyncIT` (12 testes) e as partes de catálogo de `FootballScheduledTasksTest`.
8. **Sincronização de resultados a pedido.** `PendingResultRepository.gruposPorResolver`, `FootballSyncService.sincronizarResultadosPendentes()`, o `UPDATE` em bloco de `last_synced_at`, `PendingResultSyncTask`; `PendingResultSyncIT` (10) e `ResultSyncGroupingIT` (3). **É o coração da feature — o teste 4a é a prova que a spec pede por escrito.**
9. **Tetos e falhas.** Ligar a reserva de catálogo, o esgotamento por sinal do fornecedor, e os caminhos de falha; `BudgetCeilingIT` (5) e `FootballProviderFailureIT` (7).
10. **Fornecedor real.** `ApiFootballStatusMapper` + `ApiFootballDataProvider` + `ApiFootballStatusMapperTest` (3). Deliberadamente tarde: nada na suite depende dele, e escrevê-lo depois do motor garante que o motor não foi moldado à forma do fornecedor.
11. **Emblemas.** `CrestCacheService`, `CrestController`, a linha de `SecurityConfig`, `CrestCacheIT` (5).
12. **Superfície e métrica.** `FootballCatalogService` + `TeamView`/`FixtureView`/`FixtureResultView`, `FootballMetrics`, a linha de exposição no `application.yml`; `FootballCatalogSurfaceIT` (3) e `FootballMetricsIT` (3).
13. **Fecho.** Suite completa três vezes seguidas (padrão de F04), `npm test` para confirmar os 26 intactos, e escrita do `handoff.md` com a secção "Superfície pública" copiada de §2.5 deste plano, incluindo o aviso dos golos de tempo regulamentar e o contrato do normalizador para F06b.

---

## 6. Não tocar

**Ficheiros e diretórios proibidos, sem exceção:**

- `docs/specs/seerhub.md` — a spec é entrada, nunca saída.
- `docs/features/BACKLOG.md` e `docs/features/CHANGELOG.md` — só o orquestrador escreve.
- `docs/features/F00-fundacoes/`, `F01-contas-autenticacao/`, `F02-comunidades/`, `F03-subscricoes/`, `F04-papeis-permissoes/` — todos os `plan.md` e `handoff.md`, sem exceção.
- `seerhub.md` na raiz (o brief de origem).
- **`.env`** — nunca ler, nunca abrir, nunca copiar, nunca citar. Contém a chave real. Só o `.env.example` é editável.
- `.claude/` — inteiro.
- `backend/src/main/resources/db/migration/V1__enable_extensions.sql`, `V2__baseline_schema.sql`, `V3__refresh_tokens.sql` — migrações já aplicadas; o Flyway falha por checksum. Qualquer correção é `V4` ou posterior.

**Comportamento que não pode mudar:**

- **`tips` e `tip_selections` não são escritas por F05.** Nenhum `INSERT`/`UPDATE`/`DELETE` sobre estas tabelas em código de produção; a leitura é `SELECT` nativo e mais nada. Não criar entidade `TipSelection` nem `Tip` — são de F06/F07.
- `team_aliases` não é tocada (F06b).
- Nenhum ficheiro sob `frontend/` é criado ou editado. Os 26 testes Vitest têm de continuar a passar sem alteração.
- `backend/pom.xml` não ganha dependências (`RestClient` e Micrometer já vêm dos starters existentes).
- `pt.seerhub.community.**` e `pt.seerhub.user.**` ficam intactos. Nenhuma permissão nova em `CommunityPermission` ((C5) de F04).
- `SecurityConfig` só ganha as duas linhas de `requestMatchers` descritas; `STATELESS`, `csrf.disable()`, a ordem das regras existentes, os handlers de exceção e o `JwtAuthenticationFilter` ficam exatamente como estão.
- A linha `api-key: ${API_FOOTBALL_KEY:}` de `application.yml` não é reformatada nem movida (`ConfigurationConventionsTest` compara por padrão).
- `management.endpoint.health.show-details: when-authorized` mantém-se (dívida de F00 já fechada por F01).
- `AbstractIntegrationTest` não é alterada — o contentor Postgres continua estático, partilhado e nunca parado.
- Nenhum teste, novo ou existente, abre socket para fora do contentor Postgres. Nenhum teste exige chave de API.

---

## 7. Verificação

Comandos exatos, na raiz do repositório, por esta ordem.

1. **Suite backend completa (o critério principal):**
   ```
   ./mvnw test
   ```
   Sucesso: `Tests run: 277, Failures: 0, Errors: 0, Skipped: 0` — **os 200 herdados continuam todos verdes** e os 77 novos passam. Se o número final divergir de 277 por causa de uma decomposição legítima de um teste, o handoff regista o desvio e a aritmética exata; o que **não** é aceitável é qualquer falha, erro ou teste ignorado.

2. **Suite corrida três vezes seguidas** (padrão herdado de F04, apanha dependências de ordem e resíduo entre classes no contentor partilhado):
   ```
   ./mvnw test && ./mvnw test && ./mvnw test
   ```
   Sucesso: três execuções verdes, com a mesma contagem. Atenção especial ao `ResultSyncGroupingIT` e ao `PendingResultSyncIT`, que dependem de o orçamento do dia estar no estado que o próprio teste preparou — cada teste tem de limpar/definir a linha de `api_call_budget` no seu `@BeforeEach`, nunca herdar o estado de outra classe.

3. **Frontend inalterado:**
   ```
   cd frontend && npm test
   ```
   Sucesso: `Test Files 12 passed (12)`, `Tests 26 passed (26)`. Zero ficheiros do frontend foram tocados, logo qualquer alteração aqui é um erro.

4. **Prova de que a suite corre sem chave de API** (a regra não negociável):
   ```
   API_FOOTBALL_KEY= API_FOOTBALL_DAILY_BUDGET= API_FOOTBALL_SEASON= ./mvnw test
   ```
   Sucesso: mesmo resultado do passo 1. Prova simultaneamente o critério 7d e o tratamento de variáveis vazias (3b).

5. **Prova mecânica de que nada na suite fala para fora:**
   ```
   grep -rn "api-sports.io" backend/src/test  ;  grep -rn "http://" backend/src/test --include=*.java | grep -v localhost
   ```
   Sucesso: a primeira devolve zero linhas; a segunda devolve apenas ocorrências em `SeerHubPropertiesTest` (`http://localhost:9999`, já existente) e nada mais. Formalizado no teste `FootballConventionsTest.nenhumRecursoDeTesteDeclaraChaveOuUrlDoFornecedorReal`, para não depender de alguém se lembrar de correr o `grep`.

6. **Prova mecânica de que só o serviço de sincronização toca no fornecedor:**
   ```
   grep -rln "FootballDataProvider" backend/src/main/java
   ```
   Sucesso: apenas `football/provider/*` (a própria interface e as duas implementações), `football/service/FootballSyncService.java` e `football/config/FootballProviderConfig.java`. Formalizado em `FootballConventionsTest.soOServicoDeSincronizacaoDependeDoFornecedor`.

7. **Prova de que a migração é nova e não editada:**
   ```
   git status --porcelain backend/src/main/resources/db/migration
   ```
   Sucesso: uma única linha, `?? .../V4__api_call_budget.sql`. Nenhum `M` sobre `V1`, `V2` ou `V3`.

8. **Compose ainda coerente** (sem arrancar contentores; os testes de configuração já cobrem):
   ```
   ./mvnw test -Dtest=EnvExampleTest,DockerComposeTest,ConfigurationConventionsTest,SeerHubPropertiesTest,MigrationNamingTest
   ```
   Sucesso: 12 testes verdes. É o conjunto exato de guardas herdadas que as edições de `.env.example`, `docker-compose.yml`, `application.yml` e `SeerHubProperties.java` podem partir.

9. **Verificação manual, opcional e explicitamente não bloqueante.** Um `docker compose --env-file .env up --build` com chave real faria uma sincronização verdadeira, mas **gastaria chamadas do orçamento diário do utilizador** e depende de a dívida do `.env` estar resolvida. Não é exigida para dar a feature por concluída; se for feita, o esperado é: arranque saudável dos três serviços, uma execução do catálogo no primeiro cron a gravar ~6 ligas e ~120 equipas, `SELECT * FROM api_call_budget` com `calls_used` entre 6 e 7, e `GET /actuator/metrics/seerhub.football.api.calls.today` (autenticado como `ADMIN`) a devolver o mesmo valor.

---

## 8. Casos de fronteira cobertos

Da §10 da spec, apenas os que esta feature possui. Nenhum deles pode fazer falhar um pedido de utilizador.

| Caso de fronteira | Comportamento desenhado | Teste |
| --- | --- | --- |
| **API-Football em baixo durante horas** | Cada ciclo apanha a exceção, regista `log.warn` com a liga e o correlativo, incrementa `falhas` no `SyncOutcome` e devolve normalmente. A unidade de orçamento reservada não é devolvida (conservador). O ciclo seguinte tenta de novo, sem estado extra a limpar. As tips continuam publicáveis contra o catálogo já em cache, e toda a leitura de utilizador vem da base de dados. | 8a, 8c, 8d |
| **Quota esgotada a meio do dia** | A reserva devolve `false`; a sincronização para de chamar, conta `gruposIgnoradosPorOrcamento`, regista o facto ao nível `warn` uma vez por ciclo, e retoma quando o dia UTC mudar (linha nova em `api_call_budget`). Se o próprio fornecedor sinalizar `429` ou `remaining: 0`, o dia é esgotado imediatamente e o ciclo interrompido. A reserva de catálogo garante que os resultados nunca comem as chamadas de que o catálogo precisa. | 3i, 3j, 3k, 3l, 3m |
| **Liga cujos jogos o fornecedor não tem** | Resposta vazia é tratada como resposta válida: **nada é apagado** (nunca há `DELETE` de jogos), a liga fica registada com `log.info` a dizer quantos jogos vieram, e as outras ligas do ciclo continuam. As seleções de tips sobre jogos por associar têm `fixture_id NULL` e por isso **nunca entram** na consulta de procura — custo zero, permanentemente, tal como a §10 da spec prevê ("essas tips só resolvem manualmente"). | 8b, 8e, 2h |
| **Jogo adiado ou cancelado** | O estado `POSTPONED`/`CANCELLED` é gravado no `Fixture` e o jogo **sai da fila de resultados** (a consulta só considera `SCHEDULED`/`LIVE`), deixando de consumir orçamento para sempre. **F05 não toca nas seleções** — a passagem a `PENDENTE_MANUAL` com motivo é de F08, que lê `FixtureResultView.naoSeRealizou()`. Se o jogo for remarcado, o catálogo repõe `kickoff_at` e `SCHEDULED` e ele regressa à fila naturalmente. | 8f, 1h, 2k |
| **Jogo que o fornecedor nunca marca como terminado** | Duas defesas: o estrangulamento por `last_synced_at` (no máximo uma chamada por grupo por 30 min) e a janela inferior de 7 dias, ao fim da qual o jogo deixa de gerar chamadas e fica para resolução manual. | 2i, 2j |
| **Fornecedor devolve um código de estado desconhecido** | `ApiFootballStatusMapper` devolve `Optional.empty()`, o `ProviderFixture.status` vem `null`, e o `upsert` **preserva o estado já gravado** em vez de o degradar para `SCHEDULED`. Regista `log.warn` com o código, para ser acrescentado ao mapa numa feature futura. | 8g |
| **Jogo decidido no prolongamento** | São gravados os golos do **tempo regulamentar** (`score.fulltime`), nunca os do prolongamento — é o que os mercados do R8 resolvem. | 8i |
| **Emblema ainda não descarregado quando o cliente o pede** | `302` para o URL de origem, com log a `debug`, e o ficheiro é apanhado na sincronização seguinte. Nunca `404`, nunca `500`, nunca uma chamada bloqueante ao CDN dentro do pedido do utilizador. | 5g |
| **Emblema de equipa inexistente** | `404` em `ProblemDetail` com `correlationId`, mensagem em português — semântica correta, não uma falha de infraestrutura. O `teamId` é `long` na assinatura do controlador, o que torna travessia de caminhos impossível por construção. | 5h |
| **Duas execuções da mesma tarefa em sobreposição** | Com `spring.threads.virtual.enabled: true` o agendador pode sobrepor execuções. `tryLock()` faz a segunda devolver `SyncOutcome.ignorado()` de imediato, sem consumir orçamento. (A garantia dura continua a ser a reserva atómica; isto só evita desperdício.) | X4 |

---

## 9. Riscos em aberto

### 9.1 Resolução da deliberação (LISA, 2 min — evidência: spec, código, baseline)

**H-a — "o contador de chamadas pode viver em memória". REJEITADA.**
`docker-compose.yml:47` declara `restart: unless-stopped` para o backend. O cenário que mais provavelmente provoca reinícios (fornecedor em baixo, exceções repetidas) é exatamente aquele em que o teto tem de ser respeitado; um contador em memória reporia zero a cada arranque e o consumo real seria um múltiplo desconhecido do orçamento. A tabela `api_call_budget` com reserva por `INSERT ... ON CONFLICT DO UPDATE ... WHERE calls_used < :limite` dá atomicidade sem lock aplicacional **e** dá o gancho de teste (pré-escrever a linha para simular um dia esgotado sem bifurcar o contexto Spring). Uma cache em memória por cima seria otimização prematura: são duas dezenas de escritas por dia. **Confiança: alta.**

**H-b — "a sincronização do catálogo devia ser agrupada numa só chamada em vez de uma por liga". PARCIALMENTE VERDADEIRA, e já é o desenho ótimo disponível.**
O agrupamento útil já está feito **dentro** de cada liga: `from`/`to` cobre os sete dias inteiros numa única chamada, pelo que o custo é 1 por liga por execução (6 no total), não 42. Agrupar **através** de ligas exigiria abdicar do filtro de liga (`/fixtures?date=`), que devolveria os jogos do mundo inteiro e teria de ser filtrado do nosso lado — e a spec revista fixa explicitamente o agrupamento "por liga e dia". A descoberta que torna a aritmética confortável não é o agrupamento, é outra: **a resposta de `/fixtures` traz a liga e as duas equipas embutidas**, o que elimina por completo as chamadas de catálogo de equipas e ligas. Sem isso, seis ligas × ~20 equipas seriam impagáveis; com isso, o catálogo custa 12–14 chamadas por dia e sobram ~86. **Confiança: alta para o desenho; média para a suposição de que `/fixtures?date=` sem liga seria sequer permitido, que não foi verificada e também não é necessária.**

**H-c — "detetar 'há algo para resolver' exige estado extra ou uma tabela de fila". REJEITADA — o baseline já foi desenhado para a consulta direta.**
`ix_selections_fixture_status (fixture_id, status)` e `ix_fixtures_status_kickoff (status, kickoff_at)` existem em `V2` e a §8 da spec anota o segundo como sendo "para o sincronizador de resultados". A junção `tip_selections(PENDING) → fixtures(status, kickoff_at) → leagues` é servida por esses dois índices e agrega no próprio Postgres. Nenhuma fila, nenhum evento, nenhuma coluna nova. Duas descobertas de segunda ordem, ambas incorporadas: (i) sem estrangulamento, um jogo encravado em `LIVE` re-dispararia o grupo de 15 em 15 minutos e gastaria 96 chamadas/dia — resolvido com `fixtures.last_synced_at`, **que já existe no baseline**, sem coluna nova; (ii) `tip_selections.fixture_id` ser anulável faz com que o caso "liga menor sem jogos" custe zero chamadas sem código dedicado. **Confiança: alta.**

**Descoberta não prevista pelas hipóteses, e a mais consequente:** o teto único não chega. Sem uma reserva dedicada, a sincronização de resultados pode legitimamente gastar as 100 chamadas numa jornada de sábado e deixar o catálogo por atualizar durante 24 horas — o que faria o parser de F06 não encontrar jogos para as tips do dia seguinte, transformando um problema de quota num problema de produto. Daí os **dois tetos sobre um só contador** (§2.4-C), que nenhuma das três hipóteses tinha antecipado.

### 9.2 Riscos que podem tornar este plano errado

1. **Os ids de liga do fornecedor podem não ser os configurados.** *Sintoma:* uma liga devolve sempre zero jogos. *Custo:* baixo — as outras cinco funcionam, e a correção é uma linha em `application.yml`. *Descoberta mais barata:* a primeira sincronização real regista, por liga, quantos jogos vieram; uma liga a zero em duas execuções seguidas é o sinal.
2. **O plano gratuito pode restringir as épocas acessíveis.** Se a API recusar a época corrente, todas as chamadas devolvem vazio ou erro. *Mitigação já no plano:* `API_FOOTBALL_SEASON` sobrepõe a época derivada sem tocar em código, e o comportamento com resposta vazia já é gracioso e testado (8e). *Descoberta mais barata:* a mesma da linha anterior — zero jogos em todas as seis ligas aponta para a época, não para os ids.
3. **A forma exata do JSON da API-Football não é verificável offline.** O parsing é o único ponto do plano que não pode ser provado pela suite. *Mitigação:* `JsonNode` em vez de mapeamento tipado (imune a campos novos e ao `errors` que ora é objeto ora é lista), o mapeamento de estados isolado em `ApiFootballStatusMapper` com testes próprios, e uma falha de parsing degradar para `FootballProviderException` tratada como qualquer outra falha (log + retentativa no ciclo seguinte). *Descoberta mais barata:* uma única execução real com chave, a olhar para o log da primeira sincronização.
4. **`spring.threads.virtual.enabled: true` torna o agendador concorrente.** O `tryLock()` e a reserva atómica cobrem-no, mas o teste X4 é o único da suite com duas threads e um latch — é o candidato natural a intermitência. *Mitigação:* o latch é libertado pelo teste **depois** de a segunda chamada já ter devolvido, o que remove a corrida; se ainda assim oscilar, o teste é substituído por uma verificação direta de que `sincronizarCatalogo()` devolve `ignorado()` quando o lock está tomado por outra thread parada, sem depender de temporização.
5. **Escrita em disco durante a suite (emblemas).** `application-test.yml` aponta `uploads.dir` para `${java.io.tmpdir}/seerhub-test-uploads`. Em Windows, se o diretório não puder ser criado, `CrestCacheService` tem de registar e continuar, nunca rebentar — o que é, aliás, o mesmo comportamento exigido em produção. *Descoberta mais barata:* o passo 2 da verificação (três execuções seguidas) na máquina do utilizador.
6. **`ddl-auto: validate` contra o baseline.** Mapear quatro entidades contra colunas escritas por outra pessoa é onde F01 e F02 já tropeçaram (o caso `CHAR(3)`/`bpchar` documentado em `Community.java:26`). Nenhuma coluna de F05 é `CHAR`, mas `leagues.season INTEGER NOT NULL` e `teams.normalized_name NOT NULL` obrigam a que o serviço nunca construa uma entidade sem esses valores. *Mitigação:* o passo 3 da ordem de implementação corre a suite inteira logo a seguir às entidades, antes de existir lógica que mascare o erro.
7. **A contagem de 277 testes é uma estimativa.** A experiência de F01–F04 é que a aritmética do plano derrapa por uma ou duas unidades quando um critério se decompõe naturalmente em mais métodos. Isso não é um desvio à spec; o que conta é **zero falhas e zero critérios sem teste**. O handoff regista a contagem real e a explicação da diferença, como F04 fez.