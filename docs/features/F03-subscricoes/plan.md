# F03 — Subscrições e controlo de acesso

**Requirements:** R3
**Depends on:** F00, F01, F02
**Planned:** 2026-07-27 · Opus 5

## 1. Objetivo

Depois desta feature, um utilizador autenticado subscreve qualquer comunidade
com um único pedido (`POST /api/communities/{slug}/subscription`) e passa a
ter uma `CommunityMembership` `ACTIVE`, com papel `MEMBER` e `expires_at` a 30
dias; cancela com `DELETE /api/communities/{slug}/subscription`, o que coloca a
membership em `CANCELLED` **sem tocar em `expires_at`** — e, prova-se com um
pedido real, continua a ler o conteúdo premium até essa data; e vê em
`GET /api/me/subscriptions` todas as comunidades que subscreve, sem limite de
número. Uma tarefa agendada diária marca `EXPIRED` toda a membership `ACTIVE`
ou `CANCELLED` cuja `expires_at` já passou, e é invocável diretamente como
método de serviço, por isso testável sem esperar pelo relógio. Donos e
moderadores atravessam a porta sem qualquer subscrição — inclusive um dono a
quem falte por completo a linha de membership. Um pedido a conteúdo premium
sem sessão devolve `401` e com membership expirada devolve `403`, e o cliente
mostra o ecrã de re-subscrição, com botão que volta a subscrever no mesmo
sítio.

E, sobretudo: passa a existir **uma única porta de acesso a conteúdo premium**,
`pt.seerhub.community.service.CommunityAccessService`, testada diretamente e
documentada, que F07 (tips), F10 (feed agregado), F11 (teaser) e F12 (chat)
chamam em vez de reinventar a verificação. Sem ela, a garantia do R11 — «a
resposta da API a um não-subscritor **não contém** os campos ocultos» — seria
imposta por quatro implementações independentes, e portanto não seria imposta
de todo.

## 2. Contexto herdado

Handoffs lidos na íntegra: `docs/features/F00-fundacoes/handoff.md`,
`docs/features/F01-contas-autenticacao/handoff.md`,
`docs/features/F02-comunidades/handoff.md`, mais o `CLAUDE.md` da raiz e o
código real de `pt/seerhub/community/`, `pt/seerhub/user/`,
`backend/src/main/resources/db/migration/V2__baseline_schema.sql`,
`backend/src/test/java/pt/seerhub/support/` e o frontend indicado.

### O que já existe e F03 usa tal como está

| Superfície | Caminho | Como F03 a usa |
| --- | --- | --- |
| `AuthenticatedUser(Long id, String email, GlobalRole role)` | `backend/src/main/java/pt/seerhub/user/security/AuthenticatedUser.java` | `@AuthenticationPrincipal` nos controladores novos; `autenticado.id()` é o `userId` de toda a consulta de membership |
| `anyRequest().authenticated()` | `backend/src/main/java/pt/seerhub/config/SecurityConfig.java` | **F03 não edita este ficheiro** (ver §2.3) |
| `ApiException(HttpStatus, String)` + `ApiExceptionHandler` | `backend/src/main/java/pt/seerhub/common/error/` | todo o erro de negócio de F03; o handler já produz `ProblemDetail` com `correlationId` |
| `Clock` (bean) | `backend/src/main/java/pt/seerhub/config/ClockConfig.java` | injetado em `SubscriptionService` e em `MembershipAccessRules` (por parâmetro `Instant agora`); é o que torna a expiração testável sem esperar |
| `Community`, `CommunityStatus`, `CommunityRepository` | `backend/src/main/java/pt/seerhub/community/{domain,repo}/` | resolução por `slug`; `getOwner().getId()` para o fallback de dono |
| `CommunityAccessRules.exigirQueAceitaNovasSubscricoes(Community)` | `.../community/service/CommunityAccessRules.java` | chamada **no início** de `SubscriptionService.subscrever`; F03 **não reimplementa** a verificação de comunidade suspensa |
| `CommunityAccessRules.podeSerLidaPor(Community, boolean)` | idem | usada indiretamente, através de `CommunityService`, para manter o `404` de F02 em comunidades suspensas |
| `CommunityService.obterParaLeitura`, `.exigirDono` | `.../community/service/CommunityService.java` | `obterParaLeitura` é refatorado (extração de método, sem mudança de comportamento) para F03 obter a **entidade** com a mesma regra de visibilidade |
| `AbstractIntegrationTest`, `AuthTestSupport`, `CommunityTestSupport` | `backend/src/test/java/pt/seerhub/support/` | base de todos os `*IT` de F03; `CommunityTestSupport` ganha dois helpers (§4), não é substituído |
| `apiFetch`, `AuthProvider`/`useAuth`, `RequireAuth` | `frontend/src/lib/api.ts`, `frontend/src/lib/auth.tsx`, `frontend/src/components/RequireAuth.tsx` | todas as chamadas novas passam por `apiFetch`; as rotas novas ficam dentro de `<RequireAuth>` |

### 2.1 A fronteira F02/F03, tal como F02 a deixou

O handoff de F02 é explícito e F03 herda exatamente isto:

- `CommunityMembership` está mapeada contra `community_memberships` e **é de
  F02**, mas F02 só faz **um `INSERT`** (a linha `OWNER`: `role='OWNER'`,
  `status='ACTIVE'`, `expires_at=NULL`, `version=0`) e **uma leitura de
  existência** (`existsByCommunityIdAndUserId`). Nunca fez nenhum `UPDATE` a
  nenhuma linha, nem sequer à sua.
- O único construtor público é a fábrica `CommunityMembership.deDono(...)`,
  deliberadamente, para F02 não conseguir criar por acidente uma linha
  `MEMBER`/`MODERATOR`.
- `MembershipRole {OWNER, MODERATOR, MEMBER}` e
  `MembershipStatus {ACTIVE, CANCELLED, EXPIRED}` já cobrem os três valores do
  `CHECK` do baseline. F03 usa-os sem os alterar.
- `@Version` já está mapeado (`version BIGINT NOT NULL DEFAULT 0`).
- **Tudo o resto é de F03:** criar linhas `MEMBER`, toda a transição de
  `status`, todo o uso de `expires_at`, e a tarefa diária de expiração.

Consequência de desenho: F03 **acrescenta** à entidade uma fábrica
(`deSubscritor`) e três transições (`cancelar`, `reativar`, `renovar`), e
**acrescenta** métodos ao repositório. Não move, não renomeia e não remove nada
do que F02 escreveu.

### 2.2 Tabela: `community_memberships` já tem tudo

Do `V2__baseline_schema.sql` (nunca editado):

```sql
CREATE TABLE community_memberships (
    id           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    community_id BIGINT      NOT NULL REFERENCES communities(id),
    user_id      BIGINT      NOT NULL REFERENCES users(id),
    role         VARCHAR(20) NOT NULL,
    status       VARCHAR(20) NOT NULL,
    joined_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ,
    version      BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_membership_community_user UNIQUE (community_id, user_id),
    CONSTRAINT ck_membership_role   CHECK (role IN ('OWNER','MODERATOR','MEMBER')),
    CONSTRAINT ck_membership_status CHECK (status IN ('ACTIVE','CANCELLED','EXPIRED'))
);
CREATE INDEX ix_membership_user_status    ON community_memberships (user_id, status);
CREATE INDEX ix_membership_status_expires ON community_memberships (status, expires_at);
CREATE INDEX ix_membership_community      ON community_memberships (community_id);
```

Todas as colunas de que o R3 precisa existem, com os `CHECK` certos, com a
restrição de unicidade que a §10 da spec invoca (*«Utilizador subscreve duas
vezes a mesma comunidade → impedido pela restrição de unicidade»*) e até com o
índice ideal para a consulta da tarefa diária (`status, expires_at`) e para o
feed agregado do R10 (`user_id, status`).

**Decisão: F03 não cria nenhuma migração.** Não há `V4`. `V1`, `V2` e `V3`
não são tocados.

### 2.3 `SecurityConfig` não precisa de ser editado

A regra de F02 é `requestMatchers(HttpMethod.GET, "/api/communities",
"/api/communities/*").permitAll()`. O `*` do Spring casa **um único segmento**
de caminho, por isso nenhum dos endpoints novos de F03 —
`/api/communities/{slug}/subscription`, `/api/communities/{slug}/access`,
`/api/communities/{slug}/member-area`, `/api/me/subscriptions` — é apanhado por
ele: todos caem em `anyRequest().authenticated()` e exigem sessão por omissão,
que é exatamente o que R3 quer.

**F03 não edita `SecurityConfig.java`.** Isto é uma decisão, não um acaso: é o
que garante que os 6 testes de `AuthorizationIT` (F01) e os 6 de
`CommunityVisibilityIT` (F02) não correm risco nenhum.

### 2.4 Dívidas herdadas que tocam nesta feature

1. **`requestMatchers("/__test__/**").permitAll()` em `SecurityConfig`.**
   **F03 adia, deliberadamente, e não a perde de vista.** Motivo: removê-la
   parte `ApiExceptionHandlerIT` (F00), que é um dos 101 testes da baseline, e
   partir um teste existente é critério de falha desta feature. Dono continua a
   ser **F15**; a correção mais barata já identificada por F01 continua válida
   (mover o controlador de teste para um pacote fora de `pt.seerhub`, ou
   registá-lo num `SecurityFilterChain` só do perfil `test`, e só então remover
   o matcher). F03 acrescenta duas obrigações concretas em cima disto:
   (i) **nenhum endpoint de F03 fica sob `/__test__/`** — todos vivem sob
   `/api/`, por isso o matcher não afrouxa nada do que esta feature constrói;
   (ii) o `handoff.md` de F03 tem de voltar a registar a dívida, com o mesmo
   dono, para ela não se dissolver por repetição. Verificação mecânica exigida
   no §7: `grep -R "__test__" backend/src/main/java` continua a devolver
   exatamente a linha de `SecurityConfig.java` e mais nenhuma.
2. **O papel por comunidade nunca viaja no JWT** (D-4 de F01). F03 lê sempre a
   membership da base de dados a cada pedido. É por isso que o `403` de uma
   membership expirada é imediato, e não até 15 minutos depois.
3. **`Community` não mapeia `currency`** (D-2 de F02) — os DTO de F03 que
   mostrem moeda usam `Community.MOEDA`.
4. **Contentor Postgres partilhado e nunca limpo entre classes** — todo o teste
   de F03 cria os seus utilizadores com `AuthTestSupport.emailUnico(...)` e as
   suas comunidades com `CommunityTestSupport.nomeUnico(...)`.
5. **`npm audit`** — 7 vulnerabilidades em dependências de build, herdadas de
   F00. F03 não acrescenta nenhuma dependência npm nem Maven, logo não agrava.

### 2.5 Pacote escolhido: `pt.seerhub.community`

O `CLAUDE.md` antecipava `pt.seerhub.membership` para F03. **Depois de ler o
código, a escolha é `pt.seerhub.community`.** Razões, por ordem de peso:

1. **A entidade e o repositório já lá estão.** `CommunityMembership` e
   `CommunityMembershipRepository` vivem em `pt.seerhub.community.domain` e
   `.repo` desde F02, por aplicação da regra «uma tabela → uma entidade, quem
   escreve primeiro mapeia-a». Criar `pt.seerhub.membership` obrigaria a
   *mover* essas duas classes, o que reescreve imports em `CommunityService`,
   `CommunityTestSupport` e nos 34 testes de F02 — um raio de explosão enorme
   para ganho nenhum, e a contradizer o espírito da lista «não tocar».
2. **Seria uma dependência circular entre pacotes.** Um
   `pt.seerhub.membership.service` precisa de `Community`, `CommunityStatus`,
   `CommunityRepository` e `CommunityAccessRules`; e `community` precisa da
   verificação de membership (já hoje, em `obterParaLeitura`, e mais ainda
   quando F10/F11 filtrarem). Duas metades do mesmo agregado a importarem-se
   mutuamente não é uma fronteira, é ruído.
3. **A porta de acesso pertence junto de `CommunityAccessRules`.** F02 deixou
   `CommunityAccessRules` em `community.service` como «o ponto de contacto que
   F03 consome». `MembershipAccessRules` e `CommunityAccessService` são a
   continuação natural dessa família, no mesmo sítio, com os mesmos nomes de
   família — e é isso que faz F07/F10/F11/F12 encontrarem a porta sem terem de
   procurar.
4. **A subscrição não é um agregado próprio.** Não tem ciclo de vida
   independente da comunidade: não existe subscrição sem comunidade, a chave
   natural é `(community_id, user_id)` e a regra de suspensão da comunidade
   decide se ela pode nascer. É estado do agregado `Community`, não um agregado
   vizinho.

O `CLAUDE.md` **não é editado** por F03 (está na lista «não tocar» do §6). O
`handoff.md` de F03 regista, numa linha, que o mapa de pacotes previa
`membership` para F03 e que a implementação ficou em `community`, com a
justificação acima, para que quem planeia F04 não procure no sítio errado.

## 3. Critérios de aceitação → testes

Os seis critérios do R3, decompostos. **Nenhuma linha fica sem teste.** Todos os
`*IT` estendem `pt.seerhub.support.AbstractIntegrationTest`; todos os `*Test`
correm sem contexto Spring. Caminhos completos das classes de teste:

- `backend/src/test/java/pt/seerhub/community/MembershipAccessRulesTest.java`
- `backend/src/test/java/pt/seerhub/community/MembershipExpiryTaskTest.java`
- `backend/src/test/java/pt/seerhub/community/SubscriptionIT.java`
- `backend/src/test/java/pt/seerhub/community/SubscriptionCancellationIT.java`
- `backend/src/test/java/pt/seerhub/community/SubscriptionExpiryIT.java`
- `backend/src/test/java/pt/seerhub/community/PremiumAccessIT.java`
- `frontend/src/pages/CommunityPage.test.tsx`
- `frontend/src/pages/MySubscriptionsPage.test.tsx`

### Critério 1 — «Subscrever cria uma `CommunityMembership` com estado `ATIVA`, papel `MEMBER` e `expires_at` a 30 dias»

| # | Critério | Ficheiro de teste | Nome do teste | Tipo |
| --- | --- | --- | --- | --- |
| 1a | `POST .../subscription` devolve `201` e cria a linha com `role='MEMBER'`, `status='ACTIVE'` | `SubscriptionIT` | `subscreverCriaMembershipAtivaComPapelMembro` | integração |
| 1b | `expires_at` fica a 30 dias do momento da subscrição (tolerância de 2 min sobre `now()`) | `SubscriptionIT` | `subscreverDefineExpiracaoATrintaDias` | integração |
| 1c | Aritmética exata dos 30 dias, com `Clock.fixed` | `MembershipAccessRulesTest` | `proximaExpiracaoEAgoraMaisTrintaDias` | unitário |
| 1d | A linha grava o par `(community_id, user_id)` correto, `joined_at` preenchido e `version = 0` | `SubscriptionIT` | `subscreverGravaOParComunidadeUtilizadorCorreto` | integração |
| 1e | A subscrição aparece em `GET /api/me/subscriptions` com `active: true` | `SubscriptionIT` | `subscricaoApareceEmAsMinhasSubscricoes` | integração |
| 1f | **Falha:** subscrever duas vezes a mesma comunidade devolve `409` e continua a existir **uma só** linha | `SubscriptionIT` | `subscreverDuasVezesDevolve409ENaoCriaSegundaLinha` | integração |
| 1g | **Falha (§10):** a restrição `uq_membership_community_user` recusa uma segunda linha para o mesmo par, ao nível da base de dados | `SubscriptionIT` | `restricaoDeUnicidadeRecusaSegundaLinhaParaOMesmoPar` | integração |
| 1h | **Falha (§6.2):** subscrever comunidade suspensa devolve `409` com `"Esta comunidade está suspensa."` e não cria linha nenhuma | `SubscriptionIT` | `subscreverComunidadeSuspensaDevolve409ENaoCriaMembership` | integração |
| 1i | **Falha:** dono e moderador a subscrever devolvem `409` (já têm acesso; não criam linha `MEMBER`) | `SubscriptionIT` | `donoEModeradorASubscreverDevolvem409` | integração |
| 1j | Re-subscrever depois de expirar **renova a linha existente** (`200`, `ACTIVE`, novo `expires_at`, sem segunda linha) | `SubscriptionIT` | `resubscreverRenovaALinhaExistenteSemCriarOutra` | integração |
| 1k | Reativar uma subscrição `CANCELLED` ainda dentro do prazo devolve `200`, repõe `ACTIVE` e **mantém** `expires_at` (não oferece 30 dias grátis) | `SubscriptionIT` | `reativarSubscricaoCanceladaMantemAExpiracao` | integração |
| 1l | **Falha:** `POST` sem token devolve `401` | `SubscriptionIT` | `subscreverSemTokenDevolve401` | integração |
| 1m | **Falha:** `POST` a slug inexistente devolve `404` | `SubscriptionIT` | `subscreverSlugInexistenteDevolve404` | integração |
| 1n | Cliente: subscrever mostra o estado ativo e a data de fim | `CommunityPage.test.tsx` | `subscreverMostraOEstadoAtivoEADataDeFim` | vitest |

### Critério 2 — «Cancelar coloca o estado em `CANCELADA` mas mantém o acesso até `expires_at`»

| # | Critério | Ficheiro de teste | Nome do teste | Tipo |
| --- | --- | --- | --- | --- |
| 2a | `DELETE .../subscription` devolve `200` e a linha fica em `CANCELLED` | `SubscriptionCancellationIT` | `cancelarColocaAMembershipEmCancelada` | integração |
| 2b | `expires_at` **não muda**: fotografia da coluna antes e depois, comparada `isEqualTo` | `SubscriptionCancellationIT` | `cancelarNaoAlteraAExpiracao` | integração |
| 2c | **A prova de acesso, não de coluna:** depois de cancelar, `GET .../member-area` continua a devolver `200` com o conteúdo premium e `status: "CANCELLED"` | `SubscriptionCancellationIT` | `depoisDeCancelarOAcessoPremiumContinuaAFuncionarAteAExpiracao` | integração |
| 2d | Empurrada a `expires_at` da mesma linha para o passado, o mesmo pedido passa a `403` — o acesso acabou exatamente na data, não antes | `SubscriptionCancellationIT` | `depoisDaDataDeExpiracaoOAcessoPremiumDeixaDeFuncionar` | integração |
| 2e | **Falha:** cancelar sem ter subscrição devolve `404` | `SubscriptionCancellationIT` | `cancelarSemSubscricaoDevolve404` | integração |
| 2f | **Falha:** cancelar não toca na subscrição de outro utilizador (a linha do outro fica `ACTIVE`, com `expires_at` e `version` idênticos) | `SubscriptionCancellationIT` | `cancelarNaoTocaNaSubscricaoDeOutroUtilizador` | integração |
| 2g | **Falha:** o dono não pode cancelar a sua própria membership → `409` | `SubscriptionCancellationIT` | `donoNaoPodeCancelarASuaPropriaMembership` | integração |
| 2h | Cancelar duas vezes é idempotente: `200`, continua `CANCELLED`, `expires_at` igual | `SubscriptionCancellationIT` | `cancelarDuasVezesEIdempotente` | integração |
| 2i | **Falha:** cancelar uma membership já `EXPIRED` devolve `409` | `SubscriptionCancellationIT` | `cancelarMembershipJaExpiradaDevolve409` | integração |
| 2j | **Falha:** `DELETE` sem token devolve `401` | `SubscriptionCancellationIT` | `cancelarSemTokenDevolve401` | integração |
| 2k | Cliente: depois de cancelar, a página mostra que o acesso se mantém e até quando | `CommunityPage.test.tsx` | `cancelarMantemOAcessoEMostraAteQuandoEValido` | vitest |
| 2l | Regra pura: `CANCELLED` dentro do prazo concede acesso | `MembershipAccessRulesTest` | `membroCanceladoDentroDoPrazoMantemAcesso` | unitário |

### Critério 3 — «Uma tarefa diária marca como `EXPIRADA` toda a membership `ATIVA` ou `CANCELADA` com `expires_at` no passado»

**Nenhum destes testes espera pelo relógio.** Os `*IT` inserem linhas com
`expires_at` já no passado (por `CommunityTestSupport`) e **invocam
`subscriptionService.expirarMembershipsVencidas()` diretamente**, que é o mesmo
método que o `@Scheduled` chama; os `*Test` usam `Clock.fixed` e
`CronExpression`.

| # | Critério | Ficheiro de teste | Nome do teste | Tipo |
| --- | --- | --- | --- | --- |
| 3a | `ACTIVE` com `expires_at` no passado passa a `EXPIRED` (e `version` incrementa) | `SubscriptionExpiryIT` | `tarefaExpiraMembershipAtivaComDataNoPassado` | integração |
| 3b | `CANCELLED` com `expires_at` no passado passa a `EXPIRED` | `SubscriptionExpiryIT` | `tarefaExpiraMembershipCanceladaComDataNoPassado` | integração |
| 3c | `ACTIVE` com `expires_at` no futuro fica intacta (estado, data e `version` iguais) | `SubscriptionExpiryIT` | `tarefaNaoTocaEmMembershipComDataNoFuturo` | integração |
| 3d | **A linha do dono (`expires_at IS NULL`) nunca expira** | `SubscriptionExpiryIT` | `tarefaNuncaExpiraALinhaDoDonoSemDataDeExpiracao` | integração |
| 3e | Uma linha já `EXPIRED` não é reprocessada (`version` inalterada) | `SubscriptionExpiryIT` | `tarefaNaoReprocessaMembershipJaExpirada` | integração |
| 3f | Idempotência: a segunda execução seguida devolve `0` linhas afetadas | `SubscriptionExpiryIT` | `segundaExecucaoSeguidaNaoAlteraNada` | integração |
| 3g | A tarefa é **diária**: duas disparadas consecutivas do cron por omissão distam exatamente 24 h (`CronExpression.parse`, sem esperar) | `MembershipExpiryTaskTest` | `oCronPorOmissaoDisparaUmaVezPorDia` | unitário |
| 3h | O método está anotado com `@Scheduled`, em `zone = "UTC"`, com o cron configurável por propriedade e com o valor por omissão certo | `MembershipExpiryTaskTest` | `oMetodoEstaAgendadoComCronConfiguravelEmUtc` | unitário |
| 3i | A tarefa delega no serviço e **não propaga** falhas (uma exceção do serviço não mata o agendador) | `MembershipExpiryTaskTest` | `correrDelegaNoServicoEEngoleFalhas` | unitário |
| 3j | Regra pura, incluindo a fronteira exata `expires_at == agora` (expira) | `MembershipAccessRulesTest` | `deveExpirarNaFronteiraExata` | unitário |
| 3k | Regra pura: `deveExpirar` ignora linhas sem data de expiração | `MembershipAccessRulesTest` | `deveExpirarIgnoraLinhasSemDataDeExpiracao` | unitário |

### Critério 4 — «Um utilizador pode ter membership ativa em N comunidades em simultâneo, sem limite»

| # | Critério | Ficheiro de teste | Nome do teste | Tipo |
| --- | --- | --- | --- | --- |
| 4a | O mesmo utilizador subscreve **5** comunidades de donos diferentes: 5 × `201`, 5 linhas `ACTIVE`, nenhum `409` de limite | `SubscriptionIT` | `utilizadorPodeSubscreverVariasComunidadesEmSimultaneoSemLimite` | integração |
| 4b | `GET /api/me/subscriptions` devolve as 5, todas com `active: true` | `SubscriptionIT` | `asMinhasSubscricoesDevolvemTodasAsComunidadesSubscritas` | integração |
| 4c | `CommunityAccessService.comunidadesComAcessoPremium(userId)` devolve os 5 ids (a superfície que F10 vai usar) | `PremiumAccessIT` | `comunidadesComAcessoPremiumDevolveTodasAsSubscricoesValidas` | integração |
| 4d | Cliente: `/subscricoes` lista todas as comunidades subscritas | `MySubscriptionsPage.test.tsx` | `listaTodasAsComunidadesSubscritas` | vitest |

### Critério 5 — «O dono e os moderadores têm acesso total sem subscrição»

| # | Critério | Ficheiro de teste | Nome do teste | Tipo |
| --- | --- | --- | --- | --- |
| 5a | Dono com a sua linha `OWNER` (`expires_at IS NULL`, sem qualquer subscrição): `GET .../member-area` → `200`, `role: "OWNER"` | `PremiumAccessIT` | `donoTemAcessoPremiumSemSubscricao` | integração |
| 5b | **Dono sem nenhuma linha de membership** (linha apagada por JDBC): `200` na mesma, por fallback em `communities.owner_id` | `PremiumAccessIT` | `donoSemLinhaDeMembershipContinuaComAcessoPremium` | integração |
| 5c | Moderador com linha `MODERATOR`/`ACTIVE` e `expires_at IS NULL`: `200` | `PremiumAccessIT` | `moderadorTemAcessoPremiumSemSubscricao` | integração |
| 5d | **Moderador com linha `EXPIRED` e `expires_at` no passado: `200` na mesma** — o papel de gestão atravessa a porta, o estado da subscrição é irrelevante para ele | `PremiumAccessIT` | `moderadorComLinhaExpiradaContinuaComAcessoPremium` | integração |
| 5e | Regra pura: `OWNER` e `MODERATOR` concedem acesso em todas as combinações de `status` × `expires_at` (incluindo `EXPIRED` + data no passado) | `MembershipAccessRulesTest` | `gestorTemAcessoIndependentementeDoEstadoEDaData` | unitário |
| 5f | Contraste explícito: um `MEMBER` nas mesmas combinações **não** tem acesso | `MembershipAccessRulesTest` | `membroExpiradoNaoTemAcesso` | unitário |

> Nota registada aqui de propósito: «moderador sem linha nenhuma» não é um caso
> — um moderador *é* a linha `MODERATOR`; sem linha não existe moderador. O
> único papel com identidade fora da tabela é o dono
> (`communities.owner_id`), e é exatamente esse que 5b cobre.

### Critério 6 — «Um pedido a conteúdo premium com membership expirada devolve 403 e o cliente mostra o ecrã de re-subscrição»

| # | Critério | Ficheiro de teste | Nome do teste | Tipo |
| --- | --- | --- | --- | --- |
| 6a | Membership `EXPIRED`: `GET .../member-area` → `403` com `detail` = `CommunityAccessService.MENSAGEM_SEM_ACESSO_PREMIUM` | `PremiumAccessIT` | `pedidoAConteudoPremiumComMembershipExpiradaDevolve403` | integração |
| 6b | Membership ainda `ACTIVE` mas com `expires_at` no passado (a tarefa diária ainda não correu) → `403` na mesma: a porta decide pela data, não pela coluna de estado | `PremiumAccessIT` | `membershipAtivaComDataNoPassadoJaNaoDaAcessoAntesDaTarefaCorrer` | integração |
| 6c | **Falha:** utilizador autenticado sem nenhuma membership → `403` | `PremiumAccessIT` | `utilizadorSemMembershipDevolve403` | integração |
| 6d | **Falha:** pedido a conteúdo premium **sem autenticação** → `401` | `PremiumAccessIT` | `conteudoPremiumSemTokenDevolve401` | integração |
| 6e | `GET .../access` (que nunca dá `403`) devolve `premium: false`, `status: "EXPIRED"` e `expiresAt`, que é o que o cliente usa para desenhar o ecrã de re-subscrição | `PremiumAccessIT` | `acessoExpostoAoClienteIdentificaSubscricaoExpirada` | integração |
| 6f | Regressão de F02: `member-area` de comunidade suspensa devolve `404` (não `403`) a quem não é membro — a suspensão continua a esconder a existência | `PremiumAccessIT` | `areaDeMembroDeComunidadeSuspensaDevolve404AQuemNaoEMembro` | integração |
| 6g | Membro com subscrição válida numa comunidade suspensa **mantém** acesso premium (a suspensão só recusa subscrições novas) | `PremiumAccessIT` | `membroDeComunidadeSuspensaMantemAcessoPremium` | integração |
| 6h | Cliente: um `403` na área de membro faz aparecer o ecrã de re-subscrição | `CommunityPage.test.tsx` | `erro403NaAreaDeMembroMostraOEcraDeResubscricao` | vitest |
| 6i | Cliente: o botão do ecrã de re-subscrição volta a subscrever e o conteúdo premium reaparece | `CommunityPage.test.tsx` | `botaoDoEcraDeResubscricaoVoltaASubscrever` | vitest |

### Linhas extra (não são critérios, mas fecham a porta)

| # | O que prova | Ficheiro de teste | Nome do teste | Tipo |
| --- | --- | --- | --- | --- |
| X1 | Regra fechada por omissão: um `MEMBER` com `expires_at IS NULL` **não** tem acesso (subscrição sem fim é erro de dados; nega-se) | `MembershipAccessRulesTest` | `membroSemDataDeExpiracaoNaoTemAcessoRegraFechada` | unitário |
| X2 | Sem linha de membership não há acesso premium (`role`/`status` nulos) | `MembershipAccessRulesTest` | `semLinhaDeMembershipNaoHaAcessoPremium` | unitário |
| X3 | `MEMBER`/`ACTIVE` dentro do prazo tem acesso (o caso feliz da regra pura) | `MembershipAccessRulesTest` | `membroAtivoDentroDoPrazoTemAcesso` | unitário |
| X4 | `GET /api/me/subscriptions` sem token → `401` | `SubscriptionIT` | `asMinhasSubscricoesSemTokenDevolve401` | integração |

**Totais.** 56 testes JUnit novos (10 `MembershipAccessRulesTest`, 3
`MembershipExpiryTaskTest`, 16 `SubscriptionIT`, 12 `SubscriptionCancellationIT`,
6 `SubscriptionExpiryIT`, 9... — a contagem exata por classe é a que a tabela
dita; o implementador não pode ter menos linhas do que a tabela tem) e 5 testes
Vitest novos. Alvo: **84 + 56 = 140 JUnit** e **17 + 5 = 22 Vitest**. Se a
contagem final divergir por o implementador ter fundido ou desdobrado um teste,
o `handoff.md` explica linha a linha; o que não é aceitável é uma linha da
tabela sem asserção correspondente.

## 4. Alterações

### Decisões de desenho (todas fechadas aqui, nenhuma deixada em aberto)

- **D-1 — Pacote:** `pt.seerhub.community` (justificação completa em §2.5).
- **D-2 — A porta de acesso chama-se `CommunityAccessService`**, é um `@Service`
  e é a **única** superfície que F07/F10/F11/F12 podem chamar para decidir
  acesso a conteúdo premium. As regras puras vivem separadas, em
  `MembershipAccessRules` (estático, sem Spring), para serem testáveis como
  `*Test` — o mesmo desenho que F02 usou em `CommunityAccessRules`.
- **D-3 — O acesso é calculado por `expires_at`, não por `status`.** Uma linha
  `ACTIVE` cuja `expires_at` já passou **não dá acesso**, mesmo antes de a
  tarefa diária correr. A tarefa é uma limpeza de estado, não a fonte de
  verdade da autorização. Sem isto haveria uma janela de até 24 h em que uma
  subscrição expirada continuava a servir conteúdo premium — e é precisamente
  a garantia do R11 que se perderia.
- **D-4 — Regra fechada por omissão.** Sem linha → sem acesso. `MEMBER` com
  `expires_at` nulo → sem acesso. Qualquer estado que a regra não reconheça →
  sem acesso. Uma porta de segurança falha para o lado de negar.
- **D-5 — Gestores atravessam sempre.** `role IN (OWNER, MODERATOR)` concede
  acesso independentemente de `status` e de `expires_at`. Mais: se não existir
  linha nenhuma, `communities.owner_id == userId` também concede. É o único
  caminho de acesso que não depende da tabela de memberships, e existe porque
  R3 diz «o dono tem acesso total sem subscrição» sem condicionar isso à
  existência de uma linha.
- **D-6 — A porta ignora o estado da comunidade.** Uma comunidade `SUSPENDED`
  não retira acesso a quem já o tem (R2: «os membros existentes mantêm acesso
  de leitura»); a suspensão só recusa subscrições *novas*, e essa verificação
  é a de F02, `CommunityAccessRules.exigirQueAceitaNovasSubscricoes`, chamada
  no início de `subscrever` — F03 não a reimplementa.
- **D-7 — `POST /api/communities/{slug}/subscription` é uma máquina de estados
  sobre a linha existente, não um `INSERT` cego.** Ordem obrigatória:
  1. resolver a comunidade por `slug` → `404` se não existe;
  2. `CommunityAccessRules.exigirQueAceitaNovasSubscricoes(comunidade)` →
     `409` `"Esta comunidade está suspensa."`;
  3. procurar a linha `(communityId, userId)`:
     - **não existe** → `INSERT` `MEMBER`/`ACTIVE`/`expires_at = agora + 30d` →
       **`201 Created`** com `Location`;
     - existe com `role IN (OWNER, MODERATOR)` → `409`
       `"Já tem acesso a esta comunidade como dono ou moderador."`;
     - existe `MEMBER` e **dá acesso agora** (`ACTIVE` dentro do prazo) →
       `409` `"Já subscreveu esta comunidade."`;
     - existe `MEMBER`, `CANCELLED` e **ainda dentro do prazo** → `reativar()`:
       `status = ACTIVE`, **`expires_at` intacta** → **`200 OK`**;
     - existe `MEMBER` e já não dá acesso (`EXPIRED`, ou data no passado) →
       `renovar(agora + 30d)`: `status = ACTIVE`, nova `expires_at`,
       `joined_at` intacta → **`200 OK`**.
  A reativação **não oferece 30 dias novos**: sem gateway de pagamento,
  cancelar-e-resubscrever em ciclo seria uma forma gratuita de renovar para
  sempre. `201` para linha nova, `200` para linha reaproveitada — o cliente
  trata os dois como sucesso, mas a distinção é assertável.
- **D-8 — A restrição de unicidade é a rede de segurança, não a regra.** O
  caminho normal já devolve `409` antes de tentar escrever. O `INSERT` fica
  na mesma dentro de um `try/catch (DataIntegrityViolationException)` que
  mapeia para o mesmo `409` — mesmo padrão que `CommunityService.criar` usa
  para a colisão de `slug` (D-15 de F02). É defesa em profundidade contra dois
  pedidos concorrentes; a §10 da spec é provada ao nível da base de dados pelo
  teste 1g.
- **D-9 — `DELETE /api/communities/{slug}/subscription` só sabe cancelar a
  subscrição *de quem pede*.** Não há id de utilizador no caminho nem no corpo:
  o alvo é sempre `autenticado.id()`. É assim que «cancelar a subscrição de
  outra pessoa» deixa de ser um caminho que precise de autorização — deixa de
  ser um caminho. O teste 2f prova-o observando a linha do outro utilizador,
  coluna a coluna, antes e depois.
  Estados: sem linha → `404`; `OWNER`/`MODERATOR` → `409` (não têm subscrição
  para cancelar); `CANCELLED` → `200` idempotente, sem tocar em `expires_at`;
  `EXPIRED` → `409` (cancelar o que já acabou não tem significado);
  `ACTIVE` → `200`, `status = CANCELLED`, **`expires_at` intocada**.
- **D-10 — `GET /api/communities/{slug}/member-area` é o primeiro conteúdo
  premium do SeerHub e o consumidor de referência da porta.** Existe porque o
  critério 6 exige um pedido real que devolva `403`, e o R3 não traz nenhum
  outro conteúdo (tips são F06/F07, chat é F12). Devolve a vista privada da
  comunidade para quem tem acesso: `communityId`, `slug`, `name`, `role`,
  `status`, `joinedAt`, `expiresAt`. **F07/F10/F11/F12 não estendem este
  endpoint** — chamam `CommunityAccessService` a partir dos seus próprios
  endpoints. Antes da porta premium, resolve a comunidade com a **mesma regra
  de visibilidade de F02** (`404` para não-membro de comunidade suspensa), para
  não regredir o que `CommunityVisibilityIT` garante.
- **D-11 — `GET /api/communities/{slug}/access` nunca devolve `403`.** É o
  endpoint de estado que o cliente usa para decidir o que desenhar (Subscrever /
  Cancelar / Re-subscrever). Exige autenticação (cai em
  `anyRequest().authenticated()`), devolve sempre `200` para uma comunidade
  visível, com `premium`, `role`, `status`, `expiresAt`, `joinedAt`. Separar o
  estado (`/access`) da porta (`/member-area`) é o que permite ao cliente
  mostrar um ecrã útil em vez de um erro.
- **D-12 — `GET /api/me/subscriptions` devolve só as memberships com
  `role = MEMBER`**, todas elas (incluindo `CANCELLED` e `EXPIRED`), ordenadas
  por `joined_at DESC`, cada uma com o campo calculado `active` (= a porta diz
  sim, agora). Não reaproveita `/api/me/communities` (que é «as que possuo»),
  conforme o handoff de F02 exige explicitamente.
- **D-13 — A tarefa diária é um `UPDATE` em bloco, não um ciclo de entidades.**
  JPQL `@Modifying` com parâmetros (nunca literais de enum em HQL), filtrando
  `status IN (:ativa, :cancelada) AND expires_at IS NOT NULL AND expires_at <= :agora`,
  e incrementando `version` explicitamente (um `UPDATE` em bloco não passa pelo
  bloqueio otimista do Hibernate). O `expires_at IS NOT NULL` é o que protege
  as linhas `OWNER` — teste 3d. Devolve o número de linhas afetadas, que é
  registado no log e assertado nos testes.
- **D-14 — Cron configurável, com valor por omissão no próprio código e
  desligado no perfil de teste.** `@Scheduled(cron =
  "${seerhub.subscriptions.expiry-cron:" + CRON_POR_OMISSAO + "}", zone = "UTC")`
  com `CRON_POR_OMISSAO = "0 15 3 * * *"` (03:15 UTC, longe do pico e depois
  da meia-noite em Portugal continental). **Nenhuma variável de ambiente nova**
  — não se escreve `${VAR}` em `application*.yml`, por isso `EnvExampleTest`
  não é afetado e `.env.example` não é tocado. No perfil `test`, o valor passa
  a `"-"` (`Scheduled.CRON_DISABLED`), para que a tarefa **nunca** dispare
  sozinha durante a suite: todos os testes de expiração invocam o método de
  serviço diretamente. Zero espera de relógio, zero intermitência.
- **D-15 — A tarefa apanha e regista qualquer exceção.** Um `@Scheduled` que
  rebenta escreve stack trace e volta a correr no ciclo seguinte, mas o padrão
  seguro (e testável, 3i) é capturar, registar em `warn` e devolver.
- **D-16 — `comunidadesComAcessoPremium(userId)` lê as linhas do utilizador e
  filtra em Java com `MembershipAccessRules`**, em vez de duplicar a regra numa
  cláusula `WHERE`. Motivo: uma segunda cópia da regra em JPQL é a forma mais
  provável de a garantia do R11 divergir sem ninguém notar. À escala da v1
  (dezenas de subscrições por utilizador, índice `ix_membership_user_status` já
  existente) o custo é irrelevante. Fica registado como dívida com dono **F10**,
  que pode trocar por uma consulta paginada quando o feed agregado tiver perfil
  de carga real.
- **D-17 — Frontend: `/comunidades/:slug` fica dentro de `<RequireAuth>`.** O
  perfil público de comunidade para visitantes é R10/R11 (F10/F11), não R3.
  F03 entrega a página autenticada, que é a que os critérios 1, 2 e 6 exigem.
  Registado como fronteira, não como omissão.
- **D-18 — Frontend: nenhuma página de F02 é editada.** A navegação para
  `/comunidades/:slug` sai da página nova `/subscricoes`. Alterar
  `MyCommunitiesPage.tsx` para acrescentar um link poria em risco um dos 17
  testes Vitest da baseline por ganho nenhum.

### Ficheiros a criar

**Backend — produção (10)**

| Caminho | Propósito |
| --- | --- |
| `backend/src/main/java/pt/seerhub/community/service/MembershipAccessRules.java` | Regras puras e estáticas: `eGestor`, `concedeAcessoPremium(role, status, expiresAt, agora)`, `deveExpirar(status, expiresAt, agora)`, `proximaExpiracao(agora)`, constante `DURACAO_SUBSCRICAO = Duration.ofDays(30)`. Sem Spring, sem repositórios — testável como `*Test` |
| `backend/src/main/java/pt/seerhub/community/service/CommunityAccess.java` | `record` de valor devolvido pela porta: `(Long communityId, Long userId, MembershipRole role, MembershipStatus status, Instant joinedAt, Instant expiresAt, boolean premium)`, com `gestor()` e `subscritor()` derivados e fábrica `semMembership(...)` |
| `backend/src/main/java/pt/seerhub/community/service/CommunityAccessService.java` | **A porta.** `@Service`. É isto que F07/F10/F11/F12 chamam (assinaturas em §4 «Superfície pública») |
| `backend/src/main/java/pt/seerhub/community/service/SubscriptionService.java` | `subscrever`, `cancelar`, `listarDoUtilizador`, `expirarMembershipsVencidas`; `record Resultado(boolean criada, SubscriptionResponse subscricao)` aninhado, para o controlador distinguir `201` de `200`; constantes públicas de mensagem |
| `backend/src/main/java/pt/seerhub/community/service/MembershipExpiryTask.java` | `@Component` com o `@Scheduled` e `CRON_POR_OMISSAO`; delega em `SubscriptionService.expirarMembershipsVencidas()`, regista o total e engole exceções |
| `backend/src/main/java/pt/seerhub/community/api/SubscriptionController.java` | `POST`/`DELETE /api/communities/{slug}/subscription`, `GET /api/me/subscriptions` |
| `backend/src/main/java/pt/seerhub/community/api/CommunityAccessController.java` | `GET /api/communities/{slug}/access`, `GET /api/communities/{slug}/member-area` |
| `backend/src/main/java/pt/seerhub/community/api/SubscriptionResponse.java` | `record (Long communityId, String slug, String communityName, int priceMonthlyCents, String currency, MembershipRole role, MembershipStatus status, Instant joinedAt, Instant expiresAt, boolean active)` + fábrica `de(CommunityMembership, boolean active)` |
| `backend/src/main/java/pt/seerhub/community/api/CommunityAccessResponse.java` | `record (Long communityId, String slug, boolean premium, boolean manager, MembershipRole role, MembershipStatus status, Instant joinedAt, Instant expiresAt, int priceMonthlyCents, String currency)` + fábrica `de(CommunityAccess, Community)` |
| `backend/src/main/java/pt/seerhub/community/api/MemberAreaResponse.java` | `record (Long communityId, String slug, String name, MembershipRole role, MembershipStatus status, Instant joinedAt, Instant expiresAt)` — o conteúdo premium de F03 |

**Backend — testes (6)**

| Caminho | Propósito |
| --- | --- |
| `backend/src/test/java/pt/seerhub/community/MembershipAccessRulesTest.java` | 10 testes unitários da regra pura (linhas 1c, 2l, 3j, 3k, 5e, 5f, X1, X2, X3 + o caso feliz de `deveExpirar`) |
| `backend/src/test/java/pt/seerhub/community/MembershipExpiryTaskTest.java` | 3 testes unitários: cron diário, anotação correta, delegação + falha engolida |
| `backend/src/test/java/pt/seerhub/community/SubscriptionIT.java` | Critério 1 completo (1a, 1b, 1d–1m), critério 4 (4a, 4b) e X4 |
| `backend/src/test/java/pt/seerhub/community/SubscriptionCancellationIT.java` | Critério 2 completo (2a–2j) |
| `backend/src/test/java/pt/seerhub/community/SubscriptionExpiryIT.java` | Critério 3, parte de integração (3a–3f), sempre por invocação direta do serviço |
| `backend/src/test/java/pt/seerhub/community/PremiumAccessIT.java` | Critério 5 (5a–5d), critério 6 (6a–6g) e 4c |

**Frontend (6)**

| Caminho | Propósito |
| --- | --- |
| `frontend/src/lib/subscriptions.ts` | Tipos `Subscricao`, `AcessoComunidade`, `AreaDeMembro` e as chamadas `subscrever(slug)`, `cancelarSubscricao(slug)`, `obterAcesso(slug)`, `obterAreaDeMembro(slug)`, `listarMinhasSubscricoes()`, todas sobre `apiFetch` |
| `frontend/src/components/ResubscribeNotice.tsx` | O **ecrã de re-subscrição**: nome da comunidade, preço, «A sua subscrição expirou», botão «Subscrever de novo» que chama `subscrever(slug)` e revalida as queries |
| `frontend/src/pages/CommunityPage.tsx` | `/comunidades/:slug` — ficha da comunidade + estado da subscrição + botões Subscrever/Cancelar + área de membro; um `403` na área de membro renderiza `ResubscribeNotice` |
| `frontend/src/pages/CommunityPage.test.tsx` | 4 testes Vitest (1n, 2k, 6h, 6i) |
| `frontend/src/pages/MySubscriptionsPage.tsx` | `/subscricoes` — lista das comunidades subscritas, com estado e data de fim, e link para cada `/comunidades/:slug` |
| `frontend/src/pages/MySubscriptionsPage.test.tsx` | 1 teste Vitest (4d) |

### Ficheiros a editar

| Caminho | Alteração | Risco |
| --- | --- | --- |
| `backend/src/main/java/pt/seerhub/community/domain/CommunityMembership.java` | Acrescentar a fábrica `public static CommunityMembership deSubscritor(Community, User, Instant expiresAt, Clock)` (fixa `MEMBER`/`ACTIVE`) e três transições: `public void cancelar()` (só `status = CANCELLED`), `public void reativar()` (só `status = ACTIVE`), `public void renovar(Instant novoExpiresAt)` (`ACTIVE` + nova data). Atualizar o Javadoc de fronteira para dizer que F03 passou a ser dona das transições. **Nada é removido nem renomeado**; `deDono` fica intacta | Baixo — `CommunityEditIT.alterarOPrecoNaoMexeEmNenhumaMembershipJaAtiva` compara a linha coluna a coluna e continua verde porque nenhum caminho de F02 chama os métodos novos |
| `backend/src/main/java/pt/seerhub/community/repo/CommunityMembershipRepository.java` | Acrescentar `Optional<CommunityMembership> findByCommunityIdAndUserId(Long, Long)`, `List<CommunityMembership> findByUserId(Long)`, `List<CommunityMembership> findByUserIdAndRoleOrderByJoinedAtDesc(Long, MembershipRole)` e o `@Modifying @Query int expirarVencidas(...)` de D-13. `existsByCommunityIdAndUserId` fica | Baixo — só acrescenta métodos derivados |
| `backend/src/main/java/pt/seerhub/community/service/CommunityService.java` | **Extração de método, sem mudança de comportamento:** extrair de `obterParaLeitura` o corpo que resolve a entidade com a regra de visibilidade para um novo `public Community obterEntidadeParaLeitura(String slug, Long viewerIdOuNull)`, e fazer `obterParaLeitura` chamá-lo. `CommunityAccessController` usa-o para o `member-area`, mantendo o `404` de F02 num sítio só | Médio-baixo — é o ficheiro mais quente de F02; a exigência é que os 6 testes de `CommunityVisibilityIT` e os 11 de `CommunityEditIT` passem sem uma única alteração |
| `backend/src/test/java/pt/seerhub/support/CommunityTestSupport.java` | Acrescentar **dois** helpers: `public void definirExpiracao(long communityId, long userId, Instant expiresAt)` e `public void apagarMembership(long communityId, long userId)`. Nada existente é alterado — `inserirMembership`, `lerMembership`, `contarMemberships`, `suspender`, `nomeUnico`, `criar` ficam como estão | Baixo |
| `backend/src/test/resources/application-test.yml` | Acrescentar `seerhub.subscriptions.expiry-cron: "-"` (D-14), para a tarefa nunca disparar durante a suite | Baixo — é ficheiro de teste; `ConfigurationConventionsTest` e `EnvExampleTest` só varrem `backend/src/main/resources`, `.env.example` e `docker-compose.yml` |
| `frontend/src/App.tsx` | Duas `<Route>` novas dentro de `<AuthProvider>`, ambas envolvidas em `<RequireAuth>`: `/subscricoes` → `MySubscriptionsPage` e `/comunidades/:slug` → `CommunityPage`. **A rota `/comunidades/nova` fica antes na lista e continua a ganhar** (o React Router v6 classifica segmentos estáticos acima de dinâmicos, mas a ordem explícita evita qualquer dúvida). Rota `/` e as três de F02 inalteradas | Médio-baixo — `App.test.tsx` (F00) renderiza `/`; verificar que continua verde |

### Modelo de dados / migrações

**Nenhuma migração.** `community_memberships` do `V2` já tem `role`, `status`,
`joined_at`, `expires_at`, `version`, a restrição `uq_membership_community_user`
e os índices `ix_membership_status_expires` e `ix_membership_user_status` que a
tarefa diária e o feed agregado precisam. `ddl-auto: validate` continua a
validar a entidade contra a tabela sem alterações. `V1`, `V2` e `V3` não são
editados; nenhum `V4` é criado.

### Superfície pública que F03 entrega (a documentar no handoff, literalmente)

```java
package pt.seerhub.community.service;

/**
 * A ÚNICA porta de acesso a conteúdo premium de uma comunidade.
 * F07 (tips), F10 (feed agregado), F11 (teaser) e F12 (chat) chamam esta
 * classe; nenhuma delas reimplementa a verificação.
 */
@Service
public class CommunityAccessService {

    public static final String MENSAGEM_SEM_ACESSO_PREMIUM =
            "Precisa de uma subscrição ativa para aceder ao conteúdo desta comunidade.";
    public static final String MENSAGEM_AUTENTICACAO_NECESSARIA = "Autenticação necessária.";

    /** Fotografia do acesso. Nunca lança por falta de acesso; devolve premium=false. */
    @Transactional(readOnly = true)
    public CommunityAccess acessoDe(Long communityId, Long userIdOuNull);

    /** Igual, sem repetir a leitura da comunidade quando o chamador já a tem. */
    @Transactional(readOnly = true)
    public CommunityAccess acessoDe(Community comunidade, Long userIdOuNull);

    /** Atalho booleano — é o que F11 usa para decidir que campos omitir do payload. */
    @Transactional(readOnly = true)
    public boolean temAcessoPremium(Long communityId, Long userIdOuNull);

    /** Porta dura — é o que F07/F12 usam: 401 se anónimo, 403 se sem acesso. */
    @Transactional(readOnly = true)
    public CommunityAccess exigirAcessoPremium(Community comunidade, Long userIdOuNull);

    /** Ids das comunidades a que o utilizador tem acesso agora — é o que F10 usa no feed. */
    @Transactional(readOnly = true)
    public List<Long> comunidadesComAcessoPremium(Long userId);
}
```

Como cada feature seguinte a usa, para ficar escrito antes de alguém ter de
adivinhar:

| Feature | Chamada | Porquê essa e não outra |
| --- | --- | --- |
| F07 — publicação/consulta de tips | `exigirAcessoPremium(comunidade, userId)` | Quer `403` imediato; não há resposta parcial que faça sentido |
| F10 — feed agregado | `comunidadesComAcessoPremium(userId)` | Precisa do conjunto de ids para uma única consulta ao feed |
| F11 — teaser | `temAcessoPremium(communityId, userIdOuNull)` | **Não pode lançar**: a resposta existe, só lhe faltam campos. É esta chamada que torna a garantia do R11 («a resposta não contém os campos ocultos») imponível num sítio só |
| F12 — chat | `temAcessoPremium(communityId, userId)` na subscrição do tópico STOMP | Recusa a subscrição do tópico sem quebrar a ligação |

## 5. Ordem de implementação

Cada passo termina com uma execução de testes verde. Os testes vêm com o código
do passo, nunca no fim.

1. **Regras puras.** Criar `MembershipAccessRules` e
   `MembershipAccessRulesTest` (10 testes). Correr
   `./mvnw test -Dtest=MembershipAccessRulesTest`. Este passo fixa a semântica
   de D-3, D-4 e D-5 antes de existir qualquer endpoint — se a regra estiver
   errada aqui, está errada em toda a parte.
2. **Entidade, repositório e suporte de teste.** Acrescentar `deSubscritor`,
   `cancelar`, `reativar`, `renovar` a `CommunityMembership`; os quatro métodos
   a `CommunityMembershipRepository`; os dois helpers a `CommunityTestSupport`;
   `seerhub.subscriptions.expiry-cron: "-"` a `application-test.yml`. Correr a
   **suite completa** — tem de continuar exatamente nos 84 JUnit. Este é o
   ponto de controlo mais importante do plano: prova que tocar em código de F02
   não partiu F02.
3. **A porta.** Criar `CommunityAccess` e `CommunityAccessService`; extrair
   `CommunityService.obterEntidadeParaLeitura`; criar `MemberAreaResponse`,
   `CommunityAccessResponse` e `CommunityAccessController`. Escrever
   `PremiumAccessIT` (critérios 5, 6 e 4c) usando `CommunityTestSupport.
   inserirMembership/definirExpiracao/apagarMembership` para montar cada
   cenário sem precisar dos endpoints de subscrição, que ainda não existem.
   Correr a suite completa.
4. **Subscrever.** `SubscriptionService.subscrever` + `SubscriptionResponse` +
   `SubscriptionController` (`POST` e `GET /api/me/subscriptions`).
   `SubscriptionIT` (critério 1, critério 4, X4). Correr a suite completa.
5. **Cancelar.** `SubscriptionService.cancelar` + `DELETE` no controlador.
   `SubscriptionCancellationIT` (critério 2). Atenção à ordem das asserções de
   2c/2d: o pedido a `member-area` **depois** do `DELETE` é a prova; a coluna
   `status` sozinha não prova nada. Correr a suite completa.
6. **Expiração agendada.** `SubscriptionService.expirarMembershipsVencidas` +
   `MembershipExpiryTask`. `SubscriptionExpiryIT` (invocação direta do serviço)
   e `MembershipExpiryTaskTest` (cron + anotação + delegação). Correr a suite
   completa **duas vezes seguidas** — o contentor Postgres é partilhado e não é
   limpo entre classes; qualquer dependência de ordem aparece aqui.
7. **Frontend.** `lib/subscriptions.ts` → `ResubscribeNotice` →
   `CommunityPage` + testes → `MySubscriptionsPage` + teste → duas rotas em
   `App.tsx`. Correr `npm test`, `npm run typecheck`, `npm run build`.
8. **Verificação final** (§7) e escrita do `handoff.md`, incluindo a nota de
   §2.5 sobre o pacote e a redeclaração da dívida `/__test__/**`.

## 6. Não tocar

Ficheiros e comportamentos que F03 está proibida de alterar. Isto é a cerca do
raio de explosão; o implementador é medido por ela na verificação.

**Documentos e configuração**
- `docs/specs/seerhub.md`
- `docs/features/BACKLOG.md`
- `docs/features/CHANGELOG.md`
- `docs/features/F00-fundacoes/plan.md` e `handoff.md`
- `docs/features/F01-contas-autenticacao/plan.md` e `handoff.md`
- `docs/features/F02-comunidades/plan.md` e `handoff.md`
- `seerhub.md` (o brief de origem, na raiz)
- `.env` — **nunca ler, nunca abrir, nunca copiar.** Tem uma chave de API real
- `.claude/` (todo o diretório)
- `CLAUDE.md` — a divergência de pacote é registada no `handoff.md` de F03, não
  aqui
- `.env.example`, `docker-compose.yml`, `.gitignore`
- `backend/pom.xml`, `pom.xml` da raiz, `frontend/package.json` — **F03 não
  acrescenta nenhuma dependência**, Maven ou npm

**Migrações**
- `backend/src/main/resources/db/migration/V1__enable_extensions.sql`
- `backend/src/main/resources/db/migration/V2__baseline_schema.sql`
- `backend/src/main/resources/db/migration/V3__refresh_tokens.sql`
- Não é criado nenhum `V4`

**Código de produção**
- `backend/src/main/java/pt/seerhub/config/SecurityConfig.java` — **nenhuma
  linha**, incluindo a de `/__test__/**` (ver §2.4)
- `backend/src/main/java/pt/seerhub/config/{SeerHubProperties,ClockConfig}.java`
- `backend/src/main/java/pt/seerhub/SeerHubApplication.java` —
  `@EnableScheduling` já lá está
- `backend/src/main/java/pt/seerhub/common/**` (erros, correlação)
- `backend/src/main/java/pt/seerhub/user/**` (toda a F01)
- `backend/src/main/resources/application.yml` e `application-local.yml`
- `backend/src/main/java/pt/seerhub/community/domain/{Community,CommunityStatus,MembershipRole,MembershipStatus}.java`
- `backend/src/main/java/pt/seerhub/community/service/{CommunityAccessRules,SlugGenerator}.java`
- `backend/src/main/java/pt/seerhub/community/api/{CommunityController,CommunityResponse,CreateCommunityRequest,UpdateCommunityRequest}.java`
- De `CommunityService.java`, só é permitida a extração de método descrita em
  §4; a assinatura e o comportamento observável de `criar`, `obterParaLeitura`,
  `listarAtivas`, `listarDoDono`, `editar` e `exigirDono` ficam idênticos

**Testes existentes (os 101 da baseline)**
- Os 84 JUnit de F00/F01/F02: nenhum ficheiro em
  `backend/src/test/java/pt/seerhub/{config,health,migration,common,user,community}`
  já existente é editado ou removido. Em particular
  `CommunityCreationIT`, `CommunityEditIT`, `CommunityVisibilityIT`,
  `CommunityAccessRulesTest`, `SlugGeneratorTest`, `AuthorizationIT`,
  `ApiExceptionHandlerIT`, `FlywayBaselineIT`, `MigrationNamingTest`,
  `ConfigurationConventionsTest`, `EnvExampleTest`
- `backend/src/test/java/pt/seerhub/support/{AbstractIntegrationTest,AuthTestSupport,RepoRoot}.java`
- Os 17 Vitest: `App.test.tsx`, `lib/api.test.ts`, `lib/auth.test.tsx`,
  `components/RequireAuth.test.tsx`, `pages/{RegisterPage,LoginPage,CreateCommunityPage,CommunitySettingsPage,MyCommunitiesPage}.test.tsx`

**Frontend de produção**
- `frontend/src/lib/api.ts`, `frontend/src/lib/auth.tsx`,
  `frontend/src/components/RequireAuth.tsx`
- `frontend/src/lib/communities.ts` — F03 importa `Comunidade`,
  `obterComunidade` e `centimosParaEuros` deste módulo; não lhe acrescenta nada
- `frontend/src/pages/{HealthPage,RegisterPage,LoginPage,AccountPage,MyCommunitiesPage,CreateCommunityPage,CommunitySettingsPage}.tsx`
- `frontend/vite.config.ts`, `vitest.setup.ts`, `tsconfig*.json`, `nginx.conf`

**Comportamentos que têm de continuar exatamente iguais**
- Comunidade suspensa: fora de `GET /api/communities`; `404` (não `403`) em
  `GET /api/communities/{slug}` para quem não tem linha de membership
- Alterar o preço não escreve nada em `community_memberships` — nem sequer o
  `version`
- `POST /api/auth/*` continua público; tudo o resto continua autenticado por
  omissão
- Um pedido sem token a qualquer endpoint protegido continua a devolver `401`
  em `ProblemDetail` com `correlationId`

## 7. Verificação

Comandos exatos, na ordem, a partir da raiz do repositório
(`C:\Users\tiago\Desktop\Projetos\SeerHub`).

```bash
# 1. Suite backend completa (unitários + integração num só comando)
./mvnw test

# 2. Outra vez, seguida — o contentor Postgres é partilhado e nunca limpo
./mvnw test

# 3. Frontend
cd frontend && npm test

# 4. Tipos e build de produção do frontend
cd frontend && npm run typecheck
cd frontend && npm run build

# 5. A dívida /__test__ não cresceu: uma linha, e só uma, em código de produção
grep -rn "__test__" backend/src/main/java

# 6. Nenhuma migração nova, nenhuma migração alterada
ls backend/src/main/resources/db/migration
git status --porcelain backend/src/main/resources/db/migration   # se o repo for versionado
```

**Contam como sucesso, e só isto:**

1. `./mvnw test` → `BUILD SUCCESS` com
   `Tests run: 140, Failures: 0, Errors: 0, Skipped: 0`
   (84 da baseline + 56 novos). **Se algum dos 84 anteriores falhar, a feature
   falhou** — não se «corrige» um teste de F00/F01/F02 para acomodar F03.
   Se a contagem final não for exatamente 140 por o implementador ter fundido
   ou desdobrado casos, o `handoff.md` justifica a diferença teste a teste e
   **todas as linhas da tabela do §3 continuam a ter asserção**.
2. A segunda execução de `./mvnw test` dá o mesmo resultado. Um teste que só
   passa na primeira execução depende de estado deixado por outro e não conta.
3. `npm test` → `Test Files 12 passed`, `Tests 22 passed`, 0 falhados
   (17 da baseline + 5 novos).
4. `npm run typecheck` e `npm run build` sem erros nem avisos novos.
5. O passo 5 devolve **exatamente uma** ocorrência, a linha já existente em
   `SecurityConfig.java`. Qualquer segunda ocorrência é uma regressão de
   segurança introduzida por F03.
6. O passo 6 mostra três ficheiros (`V1`, `V2`, `V3`), nenhum modificado.

**Verificação manual recomendada** (não bloqueante, mas o `handoff.md` diz se
foi feita e o que deu):

```bash
docker compose --env-file .env.example up -d --build
# registar dois utilizadores; A cria uma comunidade; B subscreve-a
#   POST /api/communities/{slug}/subscription           -> 201, expiresAt a 30 dias
#   POST /api/communities/{slug}/subscription (de novo) -> 409 "Já subscreveu esta comunidade."
#   GET  /api/communities/{slug}/member-area  (B)       -> 200
#   DELETE /api/communities/{slug}/subscription (B)     -> 200, status CANCELLED
#   GET  /api/communities/{slug}/member-area  (B)       -> 200 (ainda dentro do prazo)
#   UPDATE community_memberships SET expires_at = now() - interval '1 day' ... (psql)
#   GET  /api/communities/{slug}/member-area  (B)       -> 403
#   GET  /api/communities/{slug}/member-area  (A, dono) -> 200
#   GET  /api/communities/{slug}/member-area  (anónimo) -> 401
docker compose down -v
```

Portas: se `8080`/`5173` estiverem ocupadas nesta máquina, remapear com
variáveis de shell (`BACKEND_PORT=18080 FRONTEND_PORT=15173`), **nunca**
editando `.env.example`, tal como F00/F01/F02 fizeram.

## 8. Casos de fronteira cobertos

Da §10 da spec, os que pertencem a F03:

| Caso de fronteira (§10) | Como F03 o resolve | Onde se prova |
| --- | --- | --- |
| **«Utilizador subscreve duas vezes a mesma comunidade → impedido pela restrição de unicidade `(community_id, user_id)`»** | Duas camadas: o caminho normal encontra a linha e devolve `409` sem escrever; e a restrição da base de dados apanha a corrida entre pedidos concorrentes, mapeada para o mesmo `409` (D-8) | 1f (`409`, uma só linha) e 1g (a restrição recusa ao nível do Postgres) |
| **«Subscrição expira com tips pendentes → o utilizador deixa de ver as seleções abertas mas mantém acesso ao histórico já resolvido (o histórico é público)»** | A metade que F03 pode provar: expirar **nunca apaga nada** — só muda `status`; e o perfil público da comunidade (`GET /api/communities/{slug}`) continua a devolver `200` ao ex-membro, enquanto `member-area` passa a `403`. A metade dos campos de tip é do R11/F11, que chama esta mesma porta | 6a + a asserção complementar em `PremiumAccessIT.pedidoAConteudoPremiumComMembershipExpiradaDevolve403`, que verifica no mesmo teste que o perfil público continua a `200` |
| **«Subscrever uma comunidade suspensa mostra *Esta comunidade está suspensa* e não cria subscrição» (§6.2, falha)** | `CommunityAccessRules.exigirQueAceitaNovasSubscricoes` no primeiro passo de `subscrever`, com a mensagem literal | 1h (`409`, mensagem literal, `contarMemberships` inalterado) |
| **«Comunidade suspensa: os membros existentes mantêm acesso de leitura» (R2, herdado)** | A porta ignora `CommunityStatus` (D-6); a suspensão só bloqueia subscrições novas | 6g |
| **Janela entre expirar e a tarefa correr** (não está na §10, mas é o buraco óbvio deste desenho) | O acesso é calculado por `expires_at`, não por `status` (D-3) | 6b, e a regra pura em `membroAtivoComDataNoPassadoNaoTemAcesso` |
| **Cancelar a subscrição de outra pessoa** | Não existe caminho: o endpoint não aceita id de utilizador (D-9) | 2f |
| **Dono a quem falta a linha de membership** | Fallback em `communities.owner_id` (D-5) | 5b |
| **Fronteira exata `expires_at == agora`** | Considera-se expirada (`!expiresAt.isAfter(agora)`), coerente entre a porta e a tarefa | 3j |

Fora de âmbito, explicitamente e por decisão da spec (§4): gateway de
pagamento, cobrança, payouts, KYC, período experimental gratuito (Q4 da §12, em
aberto e a decidir pelo utilizador antes de M1 terminar — F03 não o antecipa),
nomeação e remoção de moderadores (F04/R4), matriz completa papel × endpoint
(F04), Hub e feed agregado (F10), regras de teaser sobre os campos das tips
(F11).

## 9. Riscos em aberto

1. **A propriedade `seerhub.subscriptions.expiry-cron` fica sob o prefixo
   `seerhub`, que `SeerHubProperties` liga com constructor binding.**
   `ignoreUnknownFields` é `true` por omissão, por isso uma chave não declarada
   no `record` é simplesmente ignorada — mas é uma suposição, e falha ruidosa
   (todo o contexto `*IT` deixa de arrancar). *Forma mais barata de descobrir
   cedo:* é o passo 2 da ordem de implementação, que corre a suite completa logo
   a seguir a acrescentar a linha ao `application-test.yml`. *Correção se
   falhar:* mover a chave para o prefixo `seerhub-subscriptions.expiry-cron`
   (fora do binder) e ajustar o placeholder na anotação. Nenhuma outra parte do
   plano depende do nome da chave.
2. **A extração de `CommunityService.obterEntidadeParaLeitura` toca no ficheiro
   mais coberto de F02.** *Descobrir cedo:* correr `./mvnw test -Dtest=Community*`
   imediatamente a seguir à extração, antes de escrever qualquer código novo em
   cima dela. *Se der problemas:* duplicar a resolução dentro de
   `CommunityAccessController` (três linhas: `findBySlug` + `existsByCommunityIdAndUserId`
   + `CommunityAccessRules.podeSerLidaPor`) e deixar `CommunityService`
   completamente intocado. Custa uma pequena duplicação e resolve o risco por
   inteiro.
3. **O `UPDATE` em bloco da tarefa e o `@Version`.** Um `@Modifying` em JPQL não
   passa pelo bloqueio otimista do Hibernate e não incrementa `version`
   sozinho; se o `set m.version = m.version + 1` for esquecido, uma entidade já
   carregada na sessão fica com uma versão obsoleta e um `save` posterior
   rebenta com `OptimisticLockException` num sítio distante. *Descobrir cedo:*
   os testes 3a e 3e assertam `version` explicitamente, antes e depois. O
   `clearAutomatically = true`/`flushAutomatically = true` no `@Modifying` é
   obrigatório, não decorativo.
4. **`MembershipExpiryTaskTest.correrDelegaNoServicoEEngoleFalhas` precisa de um
   duplo de `SubscriptionService`.** Usa `org.mockito.Mockito.mock(...)`, que já
   vem em `spring-boot-starter-test` — **não se acrescenta nenhuma dependência
   nova** (a lição do desvio 1 de F00: nunca redeclarar com escopo restrito algo
   que o Spring Boot já traz). É a primeira vez que o repositório usa Mockito;
   se isso for indesejado, a alternativa sem custo é declarar a colaboração da
   tarefa como uma `java.util.function.IntSupplier` e passar uma lambda no
   teste. Decisão tomada: **usar Mockito nesse único teste**, e registá-lo no
   handoff.
5. **`CommunityPage` faz três pedidos** (`/api/communities/{slug}`,
   `/access`, `/member-area`), o que obriga os testes Vitest a encaminhar o
   `fetch` esbatido por URL em vez de devolverem sempre a mesma resposta —
   padrão diferente do que F01/F02 usaram, e uma fonte fácil de teste
   acidentalmente verde. *Mitigação obrigatória:* o `vi.stubGlobal("fetch", ...)`
   de `CommunityPage.test.tsx` faz `switch` sobre o URL e **lança** se receber
   um URL não previsto, de modo que um pedido esquecido rebenta o teste em vez
   de passar despercebido.
6. **`GET .../member-area` é um endpoint que existe sobretudo porque o critério
   6 precisa de conteúdo premium real para devolver `403`.** O risco é F07/F11
   tratarem-no como o sítio onde metem tips. *Mitigação:* está escrito em D-10,
   no Javadoc da classe e tem de voltar a estar no `handoff.md`: as features
   seguintes chamam `CommunityAccessService` a partir dos **seus** endpoints;
   `member-area` não cresce.
7. **A tarefa diária não notifica ninguém.** O R13 quer «subscrição a expirar em
   3 dias» como notificação — isso é F13 e depende desta tarefa existir. F03
   deixa `expirarMembershipsVencidas()` a devolver o número de linhas afetadas
   e a registá-lo; F13 acrescenta o aviso prévio como uma segunda tarefa, não
   alterando esta. Registado no handoff como dependência conhecida.
8. **`comunidadesComAcessoPremium` filtra em Java (D-16).** É a decisão certa
   para a correção e a errada para volumes grandes. Dívida com dono **F10**,
   registada no handoff com a saída já identificada (consulta paginada com a
   mesma regra expressa em SQL, mais um teste que compare os dois caminhos
   sobre o mesmo conjunto de dados, para a regra não divergir em silêncio).