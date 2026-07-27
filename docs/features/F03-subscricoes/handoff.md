# F03 — handoff

**Status:** COMPLETE
**Implementado:** 2026-07-27 · Sonnet 5
**Test run:** `./mvnw test` → 140 passados, 0 falhas, 0 erros, 0 ignorados (corrido duas vezes seguidas, ambas verdes — 84 da baseline + 56 novos, exatamente o alvo do plano) · `cd frontend && npm test` → 22 passados, 0 falhados (17 da baseline + 5 novos, exatamente o alvo do plano) · `npm run typecheck` limpo · `npm run build` limpo

## O que agora existe

Um utilizador autenticado subscreve qualquer comunidade `ACTIVE` num único
pedido (`POST /api/communities/{slug}/subscription`): fica com uma
`CommunityMembership` `MEMBER`/`ACTIVE`, `expires_at` a 30 dias. Cancela
(`DELETE .../subscription`) e a linha passa a `CANCELLED` sem tocar em
`expires_at` — provado com um pedido real a `GET .../member-area`, que
continua a devolver `200` até essa data. Re-subscrever depois de expirar
renova a mesma linha (`200`); reativar uma `CANCELLED` ainda dentro do prazo
repõe `ACTIVE` sem oferecer 30 dias novos (`200`); nenhum dos dois caminhos
cria uma segunda linha. `GET /api/me/subscriptions` lista todas as
comunidades subscritas (role `MEMBER`), sem limite de número. Uma tarefa
diária (`@Scheduled`, 03:15 UTC por omissão, configurável, desligada no
perfil `test`) marca `EXPIRED` toda a membership `ACTIVE`/`CANCELLED` cuja
`expires_at` já passou — invocável diretamente como método de serviço, e por
isso testável sem esperar pelo relógio. Donos e moderadores atravessam a
porta sempre, inclusive um dono sem nenhuma linha de membership (fallback em
`communities.owner_id`). Conteúdo premium sem sessão devolve `401`; com
membership expirada (por `status` ou só pela data, antes de a tarefa correr)
devolve `403`. O cliente tem `/subscricoes` (lista) e `/comunidades/:slug`
(ficha + Subscrever/Cancelar + área de membro), esta última a mostrar o ecrã
de re-subscrição quando a área de membro devolve `403`.

E, sobretudo, existe agora **uma única porta de acesso a conteúdo premium**,
`pt.seerhub.community.service.CommunityAccessService`, que F07, F10, F11 e
F12 devem chamar em vez de reimplementar a verificação.

## Superfície pública para a próxima feature (F04)

### `CommunityAccessService` — a porta (ler primeiro)

`pt.seerhub.community.service.CommunityAccessService`
(`backend/src/main/java/pt/seerhub/community/service/CommunityAccessService.java`),
`@Service`, injeta `CommunityRepository`, `CommunityMembershipRepository` e
`Clock` (bean de `ClockConfig`). Cinco métodos, todos `@Transactional(readOnly = true)`:

```java
public CommunityAccess acessoDe(Long communityId, Long userIdOuNull);
public CommunityAccess acessoDe(Community comunidade, Long userIdOuNull);
public boolean temAcessoPremium(Long communityId, Long userIdOuNull);
public CommunityAccess exigirAcessoPremium(Community comunidade, Long userIdOuNull);
public List<Long> comunidadesComAcessoPremium(Long userId);
```

Garantias, uma a uma:

- **`acessoDe(...)` nunca lança por falta de acesso** — devolve sempre um
  `CommunityAccess` com `premium=false`; é a chamada certa para F11 (teaser),
  que precisa da resposta na mesma para decidir que campos omitir (R11).
- **`exigirAcessoPremium(Community, Long userIdOuNull)` é a porta dura**:
  `userIdOuNull == null` → `ApiException(401, MENSAGEM_AUTENTICACAO_NECESSARIA)`;
  sem acesso → `ApiException(403, MENSAGEM_SEM_ACESSO_PREMIUM)`. Constantes
  públicas: `CommunityAccessService.MENSAGEM_SEM_ACESSO_PREMIUM` e
  `.MENSAGEM_AUTENTICACAO_NECESSARIA` — usar estas, nunca repetir a mensagem
  literal num teste ou noutro serviço.
- **O acesso é calculado por `expiresAt`, nunca por `status`** — uma linha
  `ACTIVE` cuja data já passou não dá acesso, mesmo antes de a tarefa diária
  correr (janela de até 24h fechada). A lógica pura vive em
  `pt.seerhub.community.service.MembershipAccessRules` (estática, sem
  Spring): `eGestor(role)`, `concedeAcessoPremium(role, status, expiresAt, agora)`,
  `deveExpirar(status, expiresAt, agora)`, `proximaExpiracao(agora)`,
  `DURACAO_SUBSCRICAO = Duration.ofDays(30)`.
- **Gestores (`OWNER`/`MODERATOR`) atravessam sempre**, independentemente de
  `status`/`expiresAt` — inclusive um `MODERATOR` com linha `EXPIRED`. Um
  `OWNER` **sem nenhuma linha de membership** também atravessa: a porta cai
  para `community.getOwner().getId().equals(userId)` e sintetiza um
  `CommunityAccess` com `role=OWNER`, `premium=true`, sem linha real por
  trás — cuidado a não assumir que existe sempre uma `CommunityMembership`
  por trás de um `CommunityAccess.gestor() == true`.
- **Regra fechada por omissão**: sem papel reconhecido → sem acesso; `MEMBER`
  sem `expiresAt` → sem acesso (dado corrompido, nunca acontece em produção
  porque `deSubscritor` exige sempre uma data).
- **`comunidadesComAcessoPremium(userId)` filtra em Java, não em SQL** (dívida
  registada abaixo, dono F10) — lê `findByUserId(userId)` e aplica
  `MembershipAccessRules.concedeAcessoPremium` a cada linha.
- **A porta ignora `CommunityStatus`** — uma comunidade `SUSPENDED` não retira
  acesso a quem já o tem; só recusa subscrições novas (isso é
  `CommunityAccessRules.exigirQueAceitaNovasSubscricoes`, de F02, chamado só
  em `SubscriptionService.subscrever`).

`pt.seerhub.community.service.CommunityAccess` — `record (Long communityId,
Long userId, MembershipRole role, MembershipStatus status, Instant joinedAt,
Instant expiresAt, boolean premium)`, com `gestor()` (delega em
`MembershipAccessRules.eGestor`), `subscritor()` (`role == MEMBER`) e a
fábrica estática `semMembership(communityId, userId)` (tudo `null`,
`premium=false`). **Nunca é uma entidade JPA** — é sempre construído pela
porta, nunca por um repositório.

### `CommunityMembership` — a entidade e as transições que F03 acrescentou

`pt.seerhub.community.domain.CommunityMembership`
(`backend/src/main/java/pt/seerhub/community/domain/CommunityMembership.java`).
F02 deixou só a fábrica `deDono(Community, User, Clock)` (papel `OWNER`,
`ACTIVE`, `expiresAt=null`) — **intacta, nada foi renomeado**. F03 acrescentou:

```java
public static CommunityMembership deSubscritor(Community community, User user, Instant expiresAt, Clock clock);
public void cancelar();   // status = CANCELLED; expiresAt intocada
public void reativar();   // status = ACTIVE; expiresAt intocada (D-7: não oferece 30 dias grátis)
public void renovar(Instant novoExpiresAt); // status = ACTIVE; expiresAt = novoExpiresAt
```

**Máquina de estados completa** (o que `SubscriptionService.subscrever`
decide, por ordem, depois de `CommunityAccessRules.exigirQueAceitaNovasSubscricoes`):

| Estado da linha existente | Ação | Resultado HTTP |
| --- | --- | --- |
| Não existe | `deSubscritor(...)`, `INSERT` | `201` |
| `role IN (OWNER, MODERATOR)` | nenhuma | `409` `MENSAGEM_JA_TEM_ACESSO_COMO_GESTOR` |
| `MEMBER`, `ACTIVE`, `expiresAt` no futuro | nenhuma | `409` `MENSAGEM_JA_SUBSCREVEU` |
| `MEMBER`, `CANCELLED`, `expiresAt` no futuro | `reativar()` | `200` |
| `MEMBER`, qualquer outro caso (`EXPIRED`, ou data já passada) | `renovar(agora+30d)` | `200` |

`SubscriptionService.cancelar` (`DELETE`, só a subscrição de quem pede —
não há id de utilizador no caminho): sem linha → `404`; gestor → `409`
`MENSAGEM_SEM_SUBSCRICAO_PARA_CANCELAR`; `EXPIRED` → `409`
`MENSAGEM_SUBSCRICAO_JA_EXPIRADA`; `CANCELLED` → `200` idempotente; `ACTIVE`
→ `cancelar()`, `200`.

`pt.seerhub.community.repo.CommunityMembershipRepository`
(`backend/src/main/java/pt/seerhub/community/repo/CommunityMembershipRepository.java`)
acrescentou a `existsByCommunityIdAndUserId` (intacta) de F02:
`findByCommunityIdAndUserId(Long, Long)`, `findByUserId(Long)`,
`findByUserIdAndRoleOrderByJoinedAtDesc(Long, MembershipRole)`, e o
`@Modifying @Query int expirarVencidas(MembershipStatus statusAtiva,
MembershipStatus statusCancelada, MembershipStatus statusExpirada, Instant agora)`
— `UPDATE` em bloco, `clearAutomatically=true, flushAutomatically=true`,
incrementa `version` explicitamente (um `@Modifying` não passa pelo bloqueio
otimista do Hibernate), filtra `expiresAt IS NOT NULL` (protege a linha do
dono). Devolve o número de linhas afetadas.

### Como resolver o papel efetivo de um utilizador numa comunidade (para F04)

**Não existe ainda nenhum "papel efetivo" único e centralizado — é
precisamente isso que F04 constrói.** O que existe hoje, dividido em duas
metades que F04 tem de unificar:

1. **Dono sem linha:** `community.getOwner().getId().equals(userId)` →
   `OWNER` sintético (sem `CommunityMembership` por trás). Ver
   `CommunityAccessService.paraUtilizadorSemMembership` (privado, mas o
   padrão é este).
2. **Toda a gente com linha:** `CommunityMembershipRepository.findByCommunityIdAndUserId(communityId, userId)`
   → `.getRole()` é o papel (`OWNER`/`MODERATOR`/`MEMBER`), `.getStatus()` +
   `.getExpiresAt()` decidem se esse papel está "ativo" para efeitos de
   acesso premium (via `MembershipAccessRules.concedeAcessoPremium`) — mas
   **um `MEMBER` `CANCELLED`/`EXPIRED` continua a ser `MEMBER`** para efeitos
   de identidade, só não tem acesso premium. F04 (matriz papel × permissão)
   provavelmente precisa de "qual é o papel desta pessoa, independentemente
   de ter acesso premium" — isso é `findByCommunityIdAndUserId(...).map(CommunityMembership::getRole)`,
   mais o fallback do dono do ponto 1. **Isto não está encapsulado numa
   função pública hoje** — só existe espalhado dentro de
   `CommunityAccessService` (privado) e em `SubscriptionService`. F04 deve
   decidir se extrai isto para uma função pública nova
   (`CommunityAccessService.papelEfetivo(Long communityId, Long userId): Optional<MembershipRole>`,
   por exemplo) ou se constrói a sua própria camada de permissões por cima
   de `CommunityAccess` (que já expõe `.role()` e `.gestor()`).

### O que F04 constrói por cima vs. o que substitui

**Constrói por cima, não reimplementa:**
- `CommunityAccessService` inteira — é a porta de acesso a conteúdo premium.
  F04 (papéis e permissões) é sobre **quem pode fazer o quê dentro da
  comunidade** (nomear/remover moderador, apagar conteúdo alheio, etc.), não
  sobre "tem subscrição ativa" — são perguntas diferentes. `temAcessoPremium`
  continua a ser a resposta a "pode ver conteúdo premium", nunca a "pode
  moderar".
- `MembershipAccessRules` — as regras puras de acesso premium ficam como
  estão; F04 acrescenta as SUAS próprias regras puras (ex.:
  `PermissionRules` ou semelhante) para permissões de gestão, sem duplicar
  `eGestor`/`concedeAcessoPremium`.
- `CommunityMembership.deDono`/`deSubscritor`/`cancelar`/`reativar`/`renovar`
  — o ciclo de vida da subscrição está fechado; F04 não precisa de mais
  transições de `status`/`expiresAt`.
- A tabela `community_memberships` e os seus três `role`/três `status` —
  já cobrem `MODERATOR`. F04 não precisa de nenhuma coluna nova para saber
  "quem é moderador"; precisa de decidir o que um `MODERATOR` pode fazer.

**Deve substituir/estender, não deixar como está:**
- **Não existe `PermissionEvaluator` nem `@PreAuthorize` por papel de
  comunidade** — herdado de F01 (D-8), confirmado por F02, ainda verdade.
  Todo o controlo de acesso de F03 é `if`/`throw ApiException` manual dentro
  dos serviços. F04 é explicitamente quem resolve isto (R4).
- **Nomear/remover moderador não tem endpoint** — hoje só existe por
  `CommunityTestSupport.inserirMembership(..., "MODERATOR", ...)` em teste.
  F04 constrói o endpoint real (provavelmente em
  `pt.seerhub.community.api`, dado que `CommunityMembership` já vive em
  `pt.seerhub.community`).
- **`GET /api/communities/{slug}/access` devolve só `manager: boolean`**
  (gestor sim/não) — não devolve uma lista de permissões. Se F04 precisar
  de expor mais granularidade ao cliente, é um campo novo neste DTO, não uma
  substituição do que já lá está (F07/F10/F11/F12 podem já depender de
  `manager`/`premium`).

### Endpoints REST novos (D-7, D-9, D-10, D-11, D-12 do plano)

| Método | Caminho | Acesso | Notas |
| --- | --- | --- | --- |
| `POST` | `/api/communities/{slug}/subscription` | autenticado | `201` (linha nova) ou `200` (reativada/renovada) |
| `DELETE` | `/api/communities/{slug}/subscription` | autenticado | sempre `200` em sucesso; nunca `204` |
| `GET` | `/api/me/subscriptions` | autenticado | só `role=MEMBER`, `joined_at DESC`, cada uma com `active` calculado |
| `GET` | `/api/communities/{slug}/access` | autenticado | **nunca `403`** — estado para o cliente desenhar Subscrever/Cancelar/Re-subscrever |
| `GET` | `/api/communities/{slug}/member-area` | autenticado | a porta dura: `401`/`403`/`404` (suspensa+não-membro) possíveis; **não estender** — F07/F10/F11/F12 criam os seus próprios endpoints e chamam `CommunityAccessService` |

`pt.seerhub.community.api.SubscriptionResponse` — `record (Long communityId,
String slug, String communityName, int priceMonthlyCents, String currency,
MembershipRole role, MembershipStatus status, Instant joinedAt, Instant
expiresAt, boolean active)`, fábrica `de(CommunityMembership, boolean active)`.

`pt.seerhub.community.api.CommunityAccessResponse` — `record (Long
communityId, String slug, boolean premium, boolean manager, MembershipRole
role, MembershipStatus status, Instant joinedAt, Instant expiresAt, int
priceMonthlyCents, String currency)`, fábrica `de(CommunityAccess, Community)`.

`pt.seerhub.community.api.MemberAreaResponse` — `record (Long communityId,
String slug, String name, MembershipRole role, MembershipStatus status,
Instant joinedAt, Instant expiresAt)`, fábrica `de(Community, CommunityAccess)`.

### `CommunityService.obterEntidadeParaLeitura` (extração, sem mudança de comportamento)

`pt.seerhub.community.service.CommunityService.obterEntidadeParaLeitura(String slug, Long viewerIdOuNull): Community`
— extraída de `obterParaLeitura` (que agora só chama esta e envolve em
`CommunityResponse.de`). Devolve a **entidade**, com a mesma regra de
visibilidade de F02 (`404` para não-membro de comunidade suspensa). É o que
`CommunityAccessController` usa antes de chamar a porta premium — mantém o
`404` de F02 num sítio só. Qualquer feature futura que precise da entidade
(não do DTO) deve chamar isto, não duplicar a query.

### Frontend — helpers de estado de subscrição

`frontend/src/lib/subscriptions.ts`:

```ts
export interface Subscricao { communityId, slug, communityName, priceMonthlyCents, currency, role, status, joinedAt, expiresAt, active }
export interface AcessoComunidade { communityId, slug, premium, manager, role, status, joinedAt, expiresAt, priceMonthlyCents, currency }
export interface AreaDeMembro { communityId, slug, name, role, status, joinedAt, expiresAt }

export function subscrever(slug: string): Promise<Subscricao>;             // POST .../subscription
export function cancelarSubscricao(slug: string): Promise<Subscricao>;     // DELETE .../subscription
export function obterAcesso(slug: string): Promise<AcessoComunidade>;      // GET .../access
export function obterAreaDeMembro(slug: string): Promise<AreaDeMembro>;    // GET .../member-area
export function listarMinhasSubscricoes(): Promise<Subscricao[]>;         // GET /api/me/subscriptions
```

Todas sobre `apiFetch` (F01) — nada de `fetch` direto.

`frontend/src/components/ResubscribeNotice.tsx` — `<ResubscribeNotice
slug communityName priceMonthlyCents currency />`: o ecrã de re-subscrição.
Botão "Subscrever de novo" chama `subscrever(slug)` e faz
`queryClient.invalidateQueries({ queryKey: ["communities", slug] })` — o
prefixo de chave que **todas** as queries de `CommunityPage` partilham
(perfil, `/access`, `/member-area`), por isso uma única invalidação revalida
as três.

`frontend/src/pages/CommunityPage.tsx` (`/comunidades/:slug`, dentro de
`RequireAuth`) — três queries com chaves `["communities", slug]`,
`["communities", slug, "access"]`, `["communities", slug, "member-area"]`;
um erro na terceira renderiza `ResubscribeNotice`.

`frontend/src/pages/MySubscriptionsPage.tsx` (`/subscricoes`, dentro de
`RequireAuth`) — lista `listarMinhasSubscricoes()`, com link para
`/comunidades/:slug` de cada uma.

## Ficheiros criados

**Backend — produção (10):**
`community/service/{MembershipAccessRules,CommunityAccess,CommunityAccessService,SubscriptionService,MembershipExpiryTask}.java`,
`community/api/{SubscriptionController,CommunityAccessController,SubscriptionResponse,CommunityAccessResponse,MemberAreaResponse}.java`.

**Backend — testes (6):**
`community/{MembershipAccessRulesTest,MembershipExpiryTaskTest,SubscriptionIT,SubscriptionCancellationIT,SubscriptionExpiryIT,PremiumAccessIT}.java`.

**Frontend (6):** `lib/subscriptions.ts`, `components/ResubscribeNotice.tsx`,
`pages/CommunityPage.tsx` + `.test.tsx`, `pages/MySubscriptionsPage.tsx` +
`.test.tsx`.

## Ficheiros editados

| Caminho | Alteração |
| --- | --- |
| `backend/src/main/java/pt/seerhub/community/domain/CommunityMembership.java` | Acrescentadas a fábrica `deSubscritor` e as transições `cancelar`/`reativar`/`renovar`; Javadoc de fronteira atualizado. `deDono` e o construtor protegido ficam intactos. |
| `backend/src/main/java/pt/seerhub/community/repo/CommunityMembershipRepository.java` | Acrescentados `findByCommunityIdAndUserId`, `findByUserId`, `findByUserIdAndRoleOrderByJoinedAtDesc`, `expirarVencidas` (`@Modifying`). `existsByCommunityIdAndUserId` fica. |
| `backend/src/main/java/pt/seerhub/community/service/CommunityService.java` | Extração de método: `obterEntidadeParaLeitura(String, Long)` sai de `obterParaLeitura`, que passa a chamá-lo. Nenhuma assinatura pública mudou; `criar`, `listarAtivas`, `listarDoDono`, `editar`, `exigirDono` intactos. |
| `backend/src/test/java/pt/seerhub/support/CommunityTestSupport.java` | Acrescentados `definirExpiracao(long, long, Instant)` e `apagarMembership(long, long)`. Nada existente alterado. |
| `backend/src/test/resources/application-test.yml` | Acrescentado `seerhub.subscriptions.expiry-cron: "-"` (a tarefa nunca dispara sozinha durante a suite). |
| `frontend/src/App.tsx` | Duas rotas novas dentro de `RequireAuth`: `/subscricoes` → `MySubscriptionsPage`, `/comunidades/:slug` → `CommunityPage` (depois de `/comunidades/nova` e de `/comunidades/:slug/definicoes`, ordem explícita). Rotas de F00–F02 inalteradas. |

## Testes

Backend: **140 testes JUnit** (84 da baseline + 56 novos), suite corrida duas
vezes seguidas, ambas verdes.

| Critério(s) | Teste | Resultado |
| --- | --- | --- |
| 1c, 2l, 3j, 3k, 5e, 5f, X1, X2, X3 + caso feliz de `deveExpirar` | `MembershipAccessRulesTest` (10 testes) | passou |
| 3g, 3h, 3i | `MembershipExpiryTaskTest` (3 testes) | passou |
| 1a, 1b, 1d–1m, 4a, 4b, X4 | `SubscriptionIT` (15 testes) | passou |
| 2a–2j | `SubscriptionCancellationIT` (10 testes) | passou |
| 3a–3f | `SubscriptionExpiryIT` (6 testes) | passou |
| 5a–5d, 6a–6g, 4c | `PremiumAccessIT` (12 testes) | passou |

Frontend: **22 testes Vitest** (17 da baseline + 5 novos): 1n/2k/6h/6i em
`CommunityPage.test.tsx` (4 testes), 4d em `MySubscriptionsPage.test.tsx`
(1 teste). `npm run typecheck` e `npm run build` limpos.

A contagem final é **exatamente** a que o plano previa (140 JUnit, 22
Vitest) — nenhuma linha da tabela do §3 ficou sem teste, nenhum teste foi
fundido ou desdobrado em relação ao plano.

## Desvios face ao plano

1. **`SubscriptionService`/`SubscriptionController` implementam
   `subscrever` e `cancelar` num único ficheiro cada, em vez de os passos 4
   e 5 da ordem de implementação produzirem dois incrementos separados de
   produção.** Os ficheiros de teste continuam exatamente como o plano
   pede (`SubscriptionIT` para o critério 1, `SubscriptionCancellationIT`
   para o critério 2, escritos e verificados como incrementos distintos).
   Motivo: as duas operações partilham a mesma máquina de estados sobre a
   mesma linha (D-7/D-9 são duas faces da mesma moeda) e separar o código
   de produção em dois ficheiros só duplicaria a leitura da comunidade e da
   membership. Nenhum critério de aceitação nem ficheiro de teste do plano
   foi afetado.
2. **§2.5 do plano, registado aqui como o plano pediu:** o `CLAUDE.md`
   previa o pacote `pt.seerhub.membership` para F03; a implementação ficou
   em `pt.seerhub.community` (justificação completa no plano, §2.5 — a
   entidade e o repositório já lá estavam desde F02, evitar dependência
   circular, e a porta pertence junto de `CommunityAccessRules`). Quem
   planeia F04 deve procurar `CommunityAccessService`/`MembershipAccessRules`
   em `pt.seerhub.community.service`, não num pacote `membership` que não
   existe.
3. **Testes de `SubscriptionExpiryIT` comparam o total de linhas afetadas
   pela tarefa diária com `>= 1`, nunca com um valor exato, exceto onde a
   prova é ao nível da linha própria.** O plano não fixa este detalhe, mas
   o contentor Postgres partilhado (dívida herdada de F00/F02) faz com que
   `PremiumAccessIT.membershipAtivaComDataNoPassadoJaNaoDaAcessoAntesDaTarefaCorrer`
   e `SubscriptionCancellationIT.depoisDaDataDeExpiracaoOAcessoPremiumDeixaDeFuncionar`
   deixem propositadamente linhas `ACTIVE`/`CANCELLED` já vencidas por trás
   (para provar D-3 — a porta decide pela data antes de a tarefa correr).
   Se `SubscriptionExpiryIT` executasse depois dessas classes (a ordem de
   execução de `*IT` não é fixada pelo JUnit 5 por omissão) e comparasse o
   total global com um valor exato, o teste seria intermitente por motivos
   alheios ao que está a provar. A prova determinística ficou sempre ao
   nível da linha (`comunidade, utilizador)` própria de cada teste, nunca
   do total global — ver "Avisos" abaixo.

4. **A aritmética do §7 do plano diz "`Test Files 12 passed`"; o valor real
   é 11.** Os ficheiros de teste Vitest existentes antes de F03 são 9
   (`App.test.tsx`, `lib/api.test.ts`, `lib/auth.test.tsx`,
   `components/RequireAuth.test.tsx`,
   `pages/{RegisterPage,LoginPage,CreateCommunityPage,CommunitySettingsPage,MyCommunitiesPage}.test.tsx`),
   e F03 acrescenta exatamente 2 (`CommunityPage.test.tsx`,
   `MySubscriptionsPage.test.tsx`) — 9 + 2 = 11, confirmado por
   `npm test` (`Test Files 11 passed (11)`). O número de **testes** (22) e a
   contagem por classe JUnit (140) estão corretos e batem certo com o
   plano; é só a contagem de ficheiros Vitest no §7 que estava um a mais,
   o mesmo tipo de deslize de aritmética que o handoff de F01 já tinha
   corrigido.

Nenhum desvio contraria a spec nem o plano.

## Dívidas deixadas

Herdadas (nenhuma resolvida nem agravada por F03):

1. **`requestMatchers("/__test__/**").permitAll()` em `SecurityConfig`** —
   ainda presente, ainda necessária (remover parte `ApiExceptionHandlerIT`,
   um dos 84 testes da baseline). Dono continua **F15**. Verificação
   mecânica feita: `grep -rn "__test__" backend/src/main/java` devolve
   **exatamente** a linha de `SecurityConfig.java` — nenhum endpoint de F03
   vive sob este prefixo, e tive o cuidado de reescrever um Javadoc que
   continha acidentalmente a substring `__test__` para não inflacionar essa
   contagem.
2. **`npm audit`** — F03 não acrescentou nenhuma dependência npm nem Maven
   (Mockito já vinha transitivamente em `spring-boot-starter-test`, usado
   pela primeira vez em `MembershipExpiryTaskTest.correrDelegaNoServicoEEngoleFalhas`,
   conforme o risco 4 do plano previa). As vulnerabilidades já registadas
   por F00 (7, incluindo 1 crítica, em dependências de build) continuam por
   investigar, sem agravamento.
3. **Upload de avatar/banner adiado** (D-11 do plano de F02) — inalterado.

Novas, introduzidas por F03:

4. **`comunidadesComAcessoPremium` filtra em Java, não em SQL** (D-16 do
   plano) — decisão deliberada para não duplicar a regra de acesso numa
   cláusula `WHERE`. Dívida com dono **F10**: se o feed agregado tiver
   perfil de carga real, trocar por uma consulta paginada com a mesma regra
   expressa em SQL, mais um teste que compare os dois caminhos sobre o
   mesmo conjunto de dados.
5. **Não existe um "papel efetivo" único e centralizado** — ver a secção
   "Como resolver o papel efetivo" acima. F04 é quem deve decidir se
   extrai uma função pública para isto ou se constrói a sua própria camada
   por cima de `CommunityAccess`.
6. **`GET /api/me/subscriptions` sem paginação** — suficiente para a v1
   (dezenas de subscrições por utilizador); dívida com dono **F10**, mesmo
   padrão que `GET /api/communities` já tinha em aberto desde F02.
7. **A tarefa diária não notifica ninguém** (R13 é F13, depende desta
   tarefa existir) — `expirarMembershipsVencidas()` devolve e regista o
   número de linhas afetadas; F13 acrescenta o aviso prévio como uma
   segunda tarefa, sem alterar esta.

## Confirmação sobre `API_KEY` → `API_FOOTBALL_KEY`

**Ainda pendente, por confirmar pelo utilizador** — a mesma dívida
registada por F00, reconfirmada por F01 e por F02. F03 nunca leu nem editou
o `.env` real (regra absoluta do plano) e não chama a API-Football em
nenhum caminho, por isso a rename continua a não bloquear nada até F05.
Continua a ser uma ação do utilizador, obrigatória antes de F05 arrancar.

## Avisos para quem vier a seguir

- **O contentor Postgres é partilhado e nunca limpo entre classes** (herdado
  de F00/F02) — e F03 agora tem uma tarefa que faz `UPDATE`s em bloco sobre
  **toda** a tabela `community_memberships`, não só sobre as linhas de um
  teste. Qualquer teste futuro que invoque
  `SubscriptionService.expirarMembershipsVencidas()` diretamente **não pode
  assumir que o número de linhas afetadas é exatamente o que esse teste
  inseriu** — outras classes (`PremiumAccessIT`, `SubscriptionCancellationIT`)
  deixam propositadamente linhas vencidas por trás para provar D-3. Assertar
  sempre ao nível da linha própria (`lerMembership(communityId, userId)`),
  nunca do total global, a menos que seja com `>=`.
- **`CommunityAccess.gestor() == true` não implica que exista uma
  `CommunityMembership` por trás.** O fallback do dono sem linha (5b)
  sintetiza um `CommunityAccess` com `role=OWNER` sem nenhuma linha na
  tabela. Qualquer código que faça `communityAccessService.acessoDe(...)`
  e depois tente ir buscar a `CommunityMembership` outra vez à base de
  dados para "confirmar" vai encontrar `Optional.empty()` neste caso —
  usar sempre os campos do próprio `CommunityAccess`, nunca reconsultar.
- **`MembershipExpiryTask.correr()` engole qualquer exceção** (D-15) — se
  `expirarMembershipsVencidas()` começar a lançar por um motivo novo (ex.:
  uma constraint nova numa migração futura), a tarefa falha em silêncio,
  registando só um `warn`. Se uma feature futura precisar de alertar sobre
  falhas recorrentes desta tarefa, isso é trabalho de observabilidade (F15),
  não uma mudança a este método.
- **`seerhub.subscriptions.expiry-cron` está sob o prefixo `seerhub`**, que
  `SeerHubProperties` liga com constructor binding e
  `ignoreUnknownFields=true` — confirmado que **não** faz o arranque falhar
  (o risco 1 do plano não se materializou; a suite completa correu logo a
  seguir a acrescentar a propriedade, no passo 2 da ordem de implementação,
  e ficou verde).
- **`CommunityPage` faz três pedidos HTTP** (perfil, `/access`,
  `/member-area`) — qualquer teste Vitest que a exercite tem de encaminhar o
  mock de `fetch` por URL (nunca devolver sempre a mesma resposta) e deve
  lançar para um URL não previsto, para um pedido esquecido rebentar o
  teste em vez de passar despercebido (padrão já aplicado em
  `CommunityPage.test.tsx`).
- **Verificação manual com `docker compose` não foi repetida nesta feature**
  — as anteriores (F00–F02) já confirmaram o arranque real dos três
  serviços; F03 não altera `docker-compose.yml`, `Dockerfile`,
  `.env.example` nem nenhuma migração, por isso o risco de regressão nesse
  eixo é considerado baixo. Se quem vier a seguir quiser essa confirmação
  extra, o roteiro está no §7 do plano (registar dois utilizadores, um cria
  a comunidade, o outro subscreve, cancela, e confirma o `403` depois de
  empurrar `expires_at` para o passado via `psql`).
