# F04 — Papéis e permissões

**Requisitos:** R4 (secção 7 da spec) · secção 5 (tabela de papéis: Visitante, Membro, Dono, Moderador, Admin; «um utilizador acumula papéis»)
**Depende de:** F01 (principal autenticado), F02 (agregado `Community`, `CommunityAccessRules`), F03 (`CommunityAccessService`, máquina de estados de membership)
**Planeado:** 2026-07-27 · Opus 5
**Milestone:** M1 (última feature)

---

## 1. Objetivo

Depois de F04 existe **uma única autoridade de permissões por comunidade**, verificada
sempre no servidor: um catálogo fechado de ações (`CommunityPermission`), uma matriz pura
papel × permissão (`CommunityPermissionRules`), e uma porta de serviço
(`CommunityPermissionService`) que traduz «este utilizador, nesta comunidade, pode esta
ação?» em `401`/`403` — mais uma anotação (`@RequiresCommunityPermission`) que aplica a
mesma porta na fronteira HTTP antes de o pedido chegar a qualquer código de negócio.

Em termos observáveis: o dono nomeia e remove moderadores de entre os membros ativos
(`POST`/`DELETE /api/communities/{slug}/moderators`), vê a lista de membros
(`GET .../members`), e mais ninguém consegue fazê-lo — um moderador que tente nomear
outro moderador ou alterar o preço recebe `403`, um membro recebe `403`, um ex-membro
recebe `403`, um pedido sem sessão recebe `401`, e um `ADMIN` global que não pertença à
comunidade recebe `403`. A API passa a devolver o papel efetivo e o conjunto de permissões
do utilizador em cada comunidade (`GET /api/communities/{slug}`,
`GET /api/communities/{slug}/access`, `GET /api/communities`, e o novo
`GET /api/me/community-roles`, que mostra a acumulação de papéis da secção 5 da spec:
dono de A, moderador de B, membro de C). O frontend ganha `/comunidades/:slug/moderadores`
e passa a desenhar apenas o que o servidor já autorizou.

Fecha-se ainda a dívida herdada `requestMatchers("/__test__/**").permitAll()` da cadeia de
segurança de produção (ver §4.3 e §2.6).

---

## 2. Contexto herdado

Handoffs lidos por inteiro, por esta ordem: `docs/features/F03-subscricoes/handoff.md`,
`docs/features/F02-comunidades/handoff.md`, `docs/features/F01-contas-autenticacao/handoff.md`,
`docs/features/F00-fundacoes/handoff.md`, mais `CLAUDE.md` e o código real de
`backend/src/main/java/pt/seerhub/community/`, `.../user/security/`,
`.../config/SecurityConfig.java`, `backend/src/test/java/pt/seerhub/support/` e o frontend
(`src/lib/auth.tsx`, `src/lib/communities.ts`, `src/lib/subscriptions.ts`,
`src/components/RequireAuth.tsx`).

### 2.1 O que já existe e F04 **não** reimplementa

| Superfície | Ficheiro | O que F04 faz com ela |
| --- | --- | --- |
| `CommunityAccessService` (5 métodos, porta de conteúdo premium) | `backend/src/main/java/pt/seerhub/community/service/CommunityAccessService.java` | **Consome.** `CommunityPermissionService` injeta-a e chama `acessoDe(...)`. F04 nunca redefine «tem acesso premium». |
| `MembershipAccessRules` (`eGestor`, `concedeAcessoPremium`, `deveExpirar`, `proximaExpiracao`, `DURACAO_SUBSCRICAO`) | `.../community/service/MembershipAccessRules.java` | **Consome.** Em particular, «membro ativo» do critério 1 é exatamente `concedeAcessoPremium(...)` — F04 não inventa uma segunda definição de membro ativo. |
| `CommunityAccess` (record, `gestor()`, `subscritor()`, `semMembership(...)`) | `.../community/service/CommunityAccess.java` | **Consome.** É a entrada da matriz: `(role, premium)`. |
| `CommunityAccessRules.podeSerLidaPor` / `exigirQueAceitaNovasSubscricoes` | `.../community/service/CommunityAccessRules.java` | **Consome.** A regra de visibilidade (404 para não-membro de comunidade suspensa) fica onde está. |
| `CommunityService.obterEntidadeParaLeitura(String slug, Long viewerIdOuNull)` | `.../community/service/CommunityService.java` | **Consome.** É o único sítio que decide o `404`; F04 chama-o sempre **antes** de avaliar permissões, para manter «404 antes de 403» (D-10 de F02). |
| `CommunityMembership.deDono/deSubscritor/cancelar/reativar/renovar` | `.../community/domain/CommunityMembership.java` | **Não toca no ciclo de vida da subscrição.** F04 acrescenta só duas transições de **papel** (§4.2). |
| Tabela `community_memberships` com `role IN (OWNER, MODERATOR, MEMBER)` | `V2__baseline_schema.sql` | **Suficiente. Nenhuma migração nova** (§4.4). |
| `AuthenticatedUser` (`id()`, `email()`, `role()` global) | `.../user/security/AuthenticatedUser.java` | **Consome.** O papel de comunidade **nunca viaja no JWT** (D-4 de F01) — é lido da base de dados a cada pedido. F04 respeita isto sem exceção. |
| `AuthTestSupport`, `CommunityTestSupport` | `backend/src/test/java/pt/seerhub/support/` | **Reutiliza sem alterar.** F04 não edita nenhum dos dois. |

### 2.2 O buraco que F04 preenche (dívida 5 do handoff de F03)

Hoje não existe papel efetivo centralizado: metade da resposta está no fallback do dono
(`community.getOwner().getId().equals(userId)`, privado dentro de `CommunityAccessService`)
e metade em `CommunityMembershipRepository.findByCommunityIdAndUserId(...).getRole()`.
Também não existe `PermissionEvaluator`, nem `@PreAuthorize` por comunidade: todo o
controlo de acesso de F02/F03 é `if`/`throw ApiException` manual dentro dos serviços
(`CommunityService.exigirDono`). E não existe endpoint de nomeação de moderador — hoje só
existe `CommunityTestSupport.inserirMembership(..., "MODERATOR", ...)`, SQL cru em teste.

### 2.3 Decisão estrutural: porquê **não** `@PreAuthorize` nem `PermissionEvaluator`

`SecurityConfig` já tem `@EnableMethodSecurity`, por isso `@PreAuthorize` parece o caminho
óbvio. **Não é**, e a razão é concreta e verificável no repositório:
`pt.seerhub.common.error.ApiExceptionHandler` (`@RestControllerAdvice`) declara
`@ExceptionHandler(Exception.class)` → `500 "Ocorreu um erro inesperado."`. Uma
`AuthorizationDeniedException` lançada pela method security **dentro** da invocação do
controlador é resolvida pelos `HandlerExceptionResolver` do DispatcherServlet, cai nesse
handler genérico e devolve **500 em vez de 403**. Isto nunca foi detetado porque o único
`@PreAuthorize` existente (`AdminUserController`) está atrás de
`requestMatchers("/api/admin/**").hasRole("ADMIN")` na cadeia de filtros, que recusa antes
de o método ser sequer invocado.

Consequências, ambas assumidas por este plano:
1. F04 usa `ApiException(HttpStatus.FORBIDDEN, ...)` — o mecanismo já provado por F02/F03,
   que produz `ProblemDetail` com `correlationId` e mensagem em português.
2. F04 fecha na mesma o buraco latente: acrescenta
   `@ExceptionHandler(AccessDeniedException.class)` ao `ApiExceptionHandler` (§4.3, D-11),
   para que qualquer `@PreAuthorize` futuro devolva `403` e não `500`.

### 2.4 Decisão estrutural: duas camadas, uma regra

```
                     CommunityPermissionRules  (puro, estático, sem Spring)
                                  ▲
                                  │
  @RequiresCommunityPermission ──► CommunityPermissionService ──► CommunityAccessService (F03)
   (interceptor MVC, fronteira)     (a porta, chamada pelos serviços)
```

- **`CommunityPermissionRules`** — a matriz, função pura de `(MembershipRole, boolean acessoPremium)` para `Set<CommunityPermission>`. Testável como `*Test`, sem contexto.
- **`CommunityPermissionService`** — **a superfície de autorização que todas as features seguintes têm de chamar**. Lê a base de dados via `CommunityAccessService` e lança `401`/`403`.
- **`@RequiresCommunityPermission` + `CommunityPermissionInterceptor`** — declaração na fronteira HTTP, com execução real (não é uma anotação decorativa): o interceptor resolve a comunidade, resolve o principal e chama a mesma porta **antes** de o controlador correr.

Porquê as duas camadas e não só uma:
- Só o interceptor não chega, porque **F12 (chat) corre sobre STOMP/WebSocket**, onde um `HandlerInterceptor` de MVC não se aplica. A porta de serviço tem de ser obrigatória.
- Só a porta de serviço não chega, porque um endpoint futuro que se esqueça de a chamar fica **aberto em silêncio**. Com a anotação obrigatória mais o teste de contrato (§3.5), esquecer-se dela fica **vermelho**, não aberto.

**Direção das dependências (fixada, para não haver ciclo de beans):**
`CommunityPermissionService` → `CommunityAccessService` + `CommunityRepository` + `CommunityMembershipRepository`.
`CommunityService` → `CommunityPermissionService` (para `editar`).
`ModerationService` → `CommunityService` + `CommunityPermissionService` + repositórios.
`CommunityPermissionInterceptor` (camada web) → `CommunityService` + `CommunityPermissionService`.
**`CommunityPermissionService` nunca resolve uma comunidade por `slug`** — recebe sempre a
entidade `Community` já resolvida. É isto que mantém o `404` num sítio só
(`CommunityService.obterEntidadeParaLeitura`) e impede o ciclo.

### 2.5 Contrato para F07, F08, F12 e F14 — **obrigatório, não sugestão**

Escrito aqui porque F07/F08/F12/F14 serão implementadas por agentes que só leem o handoff
de F04. O handoff tem de repetir esta secção literalmente.

**(C1) Toda a ação dentro de uma comunidade passa por `CommunityPermissionService`.**
Nenhuma feature volta a escrever `community.getOwner().getId().equals(userId)`,
`membership.getRole() == MODERATOR`, ou qualquer `if` equivalente. O padrão é sempre:

```java
Community c = communityService.obterEntidadeParaLeitura(slug, userIdOuNull);      // 404
CommunityAuthorization auth =
        communityPermissionService.exigir(c, userIdOuNull, CommunityPermission.PUBLISH_TIPS); // 401 / 403
```

**(C2) Todo o handler MVC de uma rota `/api/communities/{slug}/...` declara a permissão
que exige**, com `@RequiresCommunityPermission(CommunityPermission.X)`. Não declarar é
falha de build lógico: `CommunityAuthorizationContractIT` percorre os handler mappings e
fica vermelho.

**(C3) Rotas que legitimamente não exigem permissão de comunidade** entram na lista
explícita e justificada `ENDPOINTS_SEM_PERMISSAO_DE_COMUNIDADE`, dentro de
`CommunityAuthorizationContractIT`. Acrescentar uma linha lá é uma decisão deliberada e
revista, não um esquecimento.

**(C4) F12 (chat, STOMP) não tem interceptor MVC.** O canal STOMP tem de chamar
`CommunityPermissionService.exigir(...)` com `READ_CHAT` na subscrição do tópico e
`WRITE_CHAT` no envio. Apagar mensagem alheia é `DELETE_ANY_CHAT_MESSAGE`; apagar a própria
mensagem **não é uma permissão de comunidade** (é autoria, verificada em `ChatMessage.author_id`)
— a regra de R12 é `DELETE_ANY_CHAT_MESSAGE || éOAutor`.

**(C5) O catálogo `CommunityPermission` já contém as permissões de R7, R8, R11 e R12**
(`PUBLISH_TIPS`, `SETTLE_TIPS`, `READ_TIPS`, `READ_CHAT`, `WRITE_CHAT`,
`DELETE_ANY_CHAT_MESSAGE`). **Nenhuma feature seguinte precisa de acrescentar valores.** Se
a spec mudar e for mesmo preciso um valor novo, quem o acrescenta é obrigado a: (a) acrescentar
a linha correspondente em `CommunityPermissionRules`, (b) acrescentar a célula ao teste
exaustivo `aMatrizCompletaDePapelPorPermissaoEExatamenteAEsperada` (que compara com uma
tabela literal e por isso rebenta sozinho), e (c) acrescentar a linha à matriz papel × endpoint
com um `*IT` por papel.

**(C6) `READ_TIPS`/`READ_CHAT`/`WRITE_CHAT` de um `MEMBER` dependem do acesso premium de F03.**
Quem quiser só a pergunta «tem subscrição válida?» continua a usar
`CommunityAccessService.temAcessoPremium(...)` — R11 (teaser) é essa pergunta, não esta.

**(C7) O papel global `ADMIN` não concede nenhuma permissão dentro de uma comunidade.**
F14 faz moderação de plataforma em `/api/admin/**` com `hasRole("ADMIN")`, nunca através de
`CommunityPermission`. Se F14 decidir que o admin tem de agir dentro de uma comunidade, isso é
uma alteração deliberada a `CommunityPermissionRules` mais linhas novas na matriz e nos testes,
nunca um `if` avulso num controlador de admin.

**(C8) `DELETE_COMMUNITY` existe no catálogo e é negada a toda a gente exceto ao dono, mas
não tem endpoint em F04** — apagar comunidade não está em R2 nem em R4 como endpoint, e R14
prefere suspender. Quem construir o endpoint usa esta permissão; não inventa outra.

### 2.6 Dívidas herdadas que este plano toca

1. **`requestMatchers("/__test__/**").permitAll()`** (F01, desvio 2; reconfirmada por F02 e F03,
   com dono nomeado F15). **F04 fecha-a** — justificação completa em §4.3/D-12.
2. **Sem `PermissionEvaluator`** (F01 D-8) — resolvida por §2.4, com a decisão deliberada de
   **não** usar o mecanismo de method security do Spring (§2.3).
3. **`CommunityService.exigirDono`** — passa a ser código morto assim que `editar` usa a porta;
   **é removido** (D-9). Nenhum teste o referencia (verificado: só existe uma chamada, em
   `CommunityService.editar`).
4. `comunidadesComAcessoPremium` filtra em Java (dono F10), `GET /api/communities` sem paginação
   (dono F10), `npm audit` (7 vulnerabilidades de build desde F00), upload de avatar/banner
   (dono F15) — **F04 não agrava nenhuma e não resolve nenhuma**.
5. Renomear `API_KEY` → `API_FOOTBALL_KEY` no `.env` real: continua pendente do utilizador,
   obrigatório antes de F05. F04 não lê nem edita o `.env`.

---

## 3. Critérios de aceitação → testes

### 3.1 Matriz papel × permissão (o catálogo fechado)

`CommunityPermission` (9 valores) × 5 estados de papel = **45 células**, todas asseridas
literalmente por `CommunityPermissionRulesTest.aMatrizCompletaDePapelPorPermissaoEExatamenteAEsperada`.

| Permissão | `OWNER` | `MODERATOR` | `MEMBER` com acesso premium | `MEMBER` sem acesso (expirado/vencido) | sem papel (visitante / `null`) |
| --- | :---: | :---: | :---: | :---: | :---: |
| `READ_TIPS` | ✔ | ✔ | ✔ | ✘ | ✘ |
| `READ_CHAT` | ✔ | ✔ | ✔ | ✘ | ✘ |
| `WRITE_CHAT` | ✔ | ✔ | ✔ | ✘ | ✘ |
| `PUBLISH_TIPS` | ✔ | ✔ | ✘ | ✘ | ✘ |
| `SETTLE_TIPS` | ✔ | ✔ | ✘ | ✘ | ✘ |
| `DELETE_ANY_CHAT_MESSAGE` | ✔ | ✔ | ✘ | ✘ | ✘ |
| `MANAGE_MODERATORS` | ✔ | ✘ | ✘ | ✘ | ✘ |
| `EDIT_COMMUNITY` | ✔ | ✘ | ✘ | ✘ | ✘ |
| `DELETE_COMMUNITY` | ✔ | ✘ | ✘ | ✘ | ✘ |

Regras que a tabela codifica, e que o teste puro fixa:
- **Gestores ignoram `acessoPremium`** (D-5 de F03): `permissoesDe(MODERATOR, false)` devolve na
  mesma as 6 permissões — um moderador com linha `EXPIRED` continua a moderar.
- **Fechado por omissão**: `role == null` → conjunto vazio. Qualquer valor de enum futuro sem
  linha na tabela → conjunto vazio, nunca acesso por omissão.
- **`EDIT_COMMUNITY` cobre o preço** (critério 2 da R4: «não pode alterar preço») porque o preço
  só se altera por `PUT /api/communities/{slug}`.

### 3.2 Matriz papel × endpoint (o que a R4, critério 4, exige provar)

Todos os endpoints existem depois de F04. Cada célula é **um teste de integração** em
`CommunityPermissionMatrixIT` (25 testes; a última linha só se aplica a um endpoint).

| Principal | `POST /api/communities/{slug}/moderators` | `DELETE /api/communities/{slug}/moderators/{userId}` | `GET /api/communities/{slug}/members` | `PUT /api/communities/{slug}` |
| --- | :---: | :---: | :---: | :---: |
| Anónimo (sem `Authorization`) | **401** | **401** | **401** | **401** |
| Autenticado sem relação (visitante) | **403** | **403** | **403** | **403** |
| Membro ativo | **403** | **403** | **403** | **403** |
| Ex-membro (`EXPIRED`, data no passado) | **403** | **403** | **403** | **403** |
| Moderador | **403** | **403** | **403** | **403** ← «moderador não altera o preço» |
| Dono | **201** | **200** | **200** | **200** |
| `ADMIN` global sem relação com a comunidade | **403** | — | — | — |

As linhas `200`/`201` do dono não são decorativas: **são o controlo que prova que um `403` vem
do papel e não de uma rota partida, de um corpo inválido ou de um `404` disfarçado.** Sem elas,
um endpoint mal registado devolveria `403` a toda a gente e a matriz passaria na mesma.

Os `401` vêm da cadeia de filtros (`anyRequest().authenticated()`), os `403` vêm do
interceptor/porta — duas camadas independentes, e a matriz prova as duas.

### 3.3 O que conta como prova do critério 4 (e o que **não** conta)

O critério é «nenhuma verificação de permissão depende apenas do cliente». A prova exigida
por este plano, célula a célula:

1. O pedido é emitido **diretamente ao servidor** com `MockMvc`, dentro de um `*IT` que
   estende `AbstractIntegrationTest` (Postgres real em Testcontainer), com o header
   `Authorization: Bearer <token do papel em causa>` e nada mais. Nenhum componente de
   frontend participa.
2. Para cada célula de recusa assere-se **(a)** o código de estado exato e **(b)** que o
   estado persistido **não mudou**: `CommunityTestSupport.lerMembership(communityId, userId)`
   mantém `role`/`status`/`version` para os endpoints de moderador,
   `CommunityTestSupport.lerColunaDaComunidade(slug, "name"|"price_monthly_cents")` mantém os
   valores para o `PUT`. Um `403` que já tivesse escrito não é um `403` verdadeiro.
3. Para cada endpoint existe pelo menos uma célula de sucesso, com **o mesmo corpo** que a
   célula de recusa correspondente usou.
4. **Nenhum teste Vitest conta como prova deste critério.** Um teste que verifica que o
   cliente esconde um botão não prova nada sobre autorização — prova apenas que a UI reflete
   o que o servidor já decidiu. Os testes de frontend deste plano (FE1–FE4) estão declarados
   explicitamente como prova de *renderização*, nunca de autorização.
5. `CommunityAuthorizationContractIT.aRecusaDoInterceptorNuncaChegaAoServico` fecha o argumento
   pelo outro lado: um `PUT` de um membro com um corpo que *mudaria* o nome e o preço devolve
   `403` **e** as colunas na base de dados ficam idênticas — o que só é verdade se a recusa
   tiver acontecido antes de qualquer código de negócio.

### 3.4 Tabela critérios → testes

Sub-critérios derivados dos 5 critérios literais da R4. **Nenhuma linha fica sem teste.**

| # | Critério (da R4) | Ficheiro de teste | Nome do teste | Tipo |
| --- | --- | --- | --- | --- |
| 1a | O dono nomeia um moderador de entre os membros ativos | `ModeratorAppointmentIT` | `donoNomeiaModeradorDeEntreOsMembrosAtivos` | integração |
| 1b | Nomear muda só o papel: `status` e `expires_at` ficam intactos | `ModeratorAppointmentIT` | `nomearModeradorNaoAlteraStatusNemExpiresAt` | integração |
| 1c | O dono remove um moderador, que volta a `MEMBER` | `ModeratorAppointmentIT` | `donoRemoveModeradorQueVoltaAMembro` | integração |
| 1d | Remover devolve o utilizador ao estado real da subscrição (se já venceu, perde acesso) | `ModeratorAppointmentIT` | `despromocaoDevolveOUtilizadorAoEstadoDaSubscricaoSubjacente` | integração |
| 1e | Nomear quem não é membro é recusado | `ModeratorAppointmentIT` | `nomearAlguemQueNaoEMembroDevolve409` | integração |
| 1f | Nomear membro com subscrição expirada é recusado («de entre os membros **ativos**») | `ModeratorAppointmentIT` | `nomearMembroComSubscricaoExpiradaDevolve409` | integração |
| 1g | Nomear quem já é moderador é recusado | `ModeratorAppointmentIT` | `nomearAlguemQueJaEModeradorDevolve409` | integração |
| 1h | Nomear o próprio dono é recusado | `ModeratorAppointmentIT` | `nomearODonoDevolve409` | integração |
| 1i | Nomear um id de utilizador inexistente é recusado sem revelar se existe | `ModeratorAppointmentIT` | `nomearUtilizadorInexistenteDevolve409ComAMesmaMensagem` | integração |
| 1j | Remover quem não é moderador é recusado | `ModeratorAppointmentIT` | `removerAlguemQueNaoEModeradorDevolve409` | integração |
| 1k | A lista de membros inclui o dono mesmo sem linha de membership | `ModeratorAppointmentIT` | `listaDeMembrosIncluiODonoMesmoSemLinhaDeMembership` | integração |
| 1l | O moderador recém-nomeado ganha as permissões de imediato (sem esperar pelo JWT) | `ModeratorAppointmentIT` | `moderadorNomeadoGanhaAsPermissoesNoMesmoInstante` | integração |
| 2a | Moderador **pode** publicar e resolver tips | `CommunityPermissionRulesTest` | `moderadorPodePublicarEResolverTips` | unitário |
| 2b | Moderador **pode** apagar mensagens de chat de outros | `CommunityPermissionRulesTest` | `moderadorPodeApagarMensagensDeChatDeOutros` | unitário |
| 2c | Moderador **pode** publicar/resolver — visível na API, não só na tabela | `EffectiveRoleIT` | `acessoDeModeradorDevolvePublicarEResolverTips` | integração |
| 2d | Moderador **não** pode alterar o preço | `CommunityPermissionMatrixIT` | `moderadorNaoAlteraOPrecoDevolve403` | integração |
| 2e | Moderador **não** pode nomear moderadores | `CommunityPermissionMatrixIT` | `moderadorNaoNomeiaOutroModeradorDevolve403` | integração |
| 2f | Moderador **não** pode remover moderadores | `CommunityPermissionMatrixIT` | `moderadorNaoRemoveModeradorDevolve403` | integração |
| 2g | Moderador **não** pode apagar a comunidade | `CommunityPermissionRulesTest` | `moderadorNaoPodeApagarAComunidade` | unitário |
| 3a | Membro ativo **pode** ler tips e ler/escrever no chat | `CommunityPermissionRulesTest` | `membroAtivoPodeLerTipsELerEEscreverNoChat` | unitário |
| 3b | Membro ativo **não** pode publicar nem resolver tips | `CommunityPermissionRulesTest` | `membroAtivoNaoPodePublicarNemResolverTips` | unitário |
| 3c | Membro sem acesso premium perde leitura e escrita | `CommunityPermissionRulesTest` | `membroSemAcessoPremiumPerdeLeituraEEscrita` | unitário |
| 3d | O que o membro pode é o que a API devolve (não só a tabela) | `EffectiveRoleIT` | `perfilDaComunidadeDevolveOPapelEPermissoesDoMembro` | integração |
| 4a | Anónimo × `POST /moderators` | `CommunityPermissionMatrixIT` | `anonimoNaoNomeiaModeradorDevolve401` | integração |
| 4b | Visitante × `POST /moderators` | `CommunityPermissionMatrixIT` | `visitanteNaoNomeiaModeradorDevolve403` | integração |
| 4c | Membro ativo × `POST /moderators` | `CommunityPermissionMatrixIT` | `membroAtivoNaoNomeiaModeradorDevolve403` | integração |
| 4d | Ex-membro × `POST /moderators` | `CommunityPermissionMatrixIT` | `exMembroNaoNomeiaModeradorDevolve403` | integração |
| 4e | Moderador × `POST /moderators` | `CommunityPermissionMatrixIT` | `moderadorNaoNomeiaOutroModeradorDevolve403` | integração |
| 4f | Dono × `POST /moderators` (controlo `201`) | `CommunityPermissionMatrixIT` | `donoNomeiaModeradorDevolve201` | integração |
| 4g | `ADMIN` global sem relação × `POST /moderators` | `CommunityPermissionMatrixIT` | `adminGlobalSemRelacaoNaoNomeiaModeradorDevolve403` | integração |
| 4h | Anónimo × `DELETE /moderators/{userId}` | `CommunityPermissionMatrixIT` | `anonimoNaoRemoveModeradorDevolve401` | integração |
| 4i | Visitante × `DELETE /moderators/{userId}` | `CommunityPermissionMatrixIT` | `visitanteNaoRemoveModeradorDevolve403` | integração |
| 4j | Membro ativo × `DELETE /moderators/{userId}` | `CommunityPermissionMatrixIT` | `membroAtivoNaoRemoveModeradorDevolve403` | integração |
| 4k | Ex-membro × `DELETE /moderators/{userId}` | `CommunityPermissionMatrixIT` | `exMembroNaoRemoveModeradorDevolve403` | integração |
| 4l | Moderador × `DELETE /moderators/{userId}` | `CommunityPermissionMatrixIT` | `moderadorNaoRemoveModeradorDevolve403` | integração |
| 4m | Dono × `DELETE /moderators/{userId}` (controlo `200`) | `CommunityPermissionMatrixIT` | `donoRemoveModeradorDevolve200` | integração |
| 4n | Anónimo × `GET /members` | `CommunityPermissionMatrixIT` | `anonimoNaoListaMembrosDevolve401` | integração |
| 4o | Visitante × `GET /members` | `CommunityPermissionMatrixIT` | `visitanteNaoListaMembrosDevolve403` | integração |
| 4p | Membro ativo × `GET /members` | `CommunityPermissionMatrixIT` | `membroAtivoNaoListaMembrosDevolve403` | integração |
| 4q | Ex-membro × `GET /members` | `CommunityPermissionMatrixIT` | `exMembroNaoListaMembrosDevolve403` | integração |
| 4r | Moderador × `GET /members` | `CommunityPermissionMatrixIT` | `moderadorNaoListaMembrosDevolve403` | integração |
| 4s | Dono × `GET /members` (controlo `200`) | `CommunityPermissionMatrixIT` | `donoListaMembrosDevolve200` | integração |
| 4t | Anónimo × `PUT /api/communities/{slug}` | `CommunityPermissionMatrixIT` | `anonimoNaoEditaAComunidadeDevolve401` | integração |
| 4u | Visitante × `PUT /api/communities/{slug}` | `CommunityPermissionMatrixIT` | `visitanteNaoEditaAComunidadeDevolve403` | integração |
| 4v | Membro ativo × `PUT /api/communities/{slug}` | `CommunityPermissionMatrixIT` | `membroAtivoNaoEditaAComunidadeDevolve403` | integração |
| 4w | Ex-membro × `PUT /api/communities/{slug}` | `CommunityPermissionMatrixIT` | `exMembroNaoEditaAComunidadeDevolve403` | integração |
| 4x | Dono × `PUT /api/communities/{slug}` (controlo `200`) | `CommunityPermissionMatrixIT` | `donoEditaAComunidadeDevolve200` | integração |
| 4y | Todo o endpoint de comunidade declara a permissão que exige (impede bypass silencioso futuro) | `CommunityAuthorizationContractIT` | `todoEndpointDeComunidadeDeclaraAPermissaoQueExige` | integração |
| 4z | A lista de exceções justificadas não fica obsoleta | `CommunityAuthorizationContractIT` | `aListaDeExcecoesSoContemRotasQueAindaExistem` | integração |
| 4aa | A recusa acontece antes de qualquer escrita (não é o cliente a decidir nada) | `CommunityAuthorizationContractIT` | `aRecusaDoInterceptorNuncaChegaAoServico` | integração |
| 4ab | Fecho por omissão da matriz (papel nulo/desconhecido não tem nada) | `CommunityPermissionRulesTest` | `papelNuloOuDesconhecidoFechaPorOmissao` | unitário |
| 4ac | Visitante sem papel não tem nenhuma permissão | `CommunityPermissionRulesTest` | `visitanteSemPapelNaoTemNenhumaPermissao` | unitário |
| 4ad | A matriz completa (45 células) é exatamente a esperada | `CommunityPermissionRulesTest` | `aMatrizCompletaDePapelPorPermissaoEExatamenteAEsperada` | unitário |
| 5a | O perfil da comunidade devolve o papel efetivo e as permissões do membro | `EffectiveRoleIT` | `perfilDaComunidadeDevolveOPapelEPermissoesDoMembro` | integração |
| 5b | Para um visitante anónimo o perfil não traz papel e traz permissões vazias | `EffectiveRoleIT` | `perfilDaComunidadeParaAnonimoNaoTemPapelNemPermissoes` | integração |
| 5c | `GET .../access` devolve as permissões (campo novo, sem remover `premium`/`manager`) | `EffectiveRoleIT` | `acessoDeModeradorDevolvePublicarEResolverTips` | integração |
| 5d | Um utilizador acumula papéis: dono de A, moderador de B, membro de C (secção 5 da spec) | `EffectiveRoleIT` | `utilizadorAcumulaPapeisEmComunidadesDiferentes` | integração |
| 5e | Ex-membro continua a ser `MEMBER` como identidade, mas sem permissões | `EffectiveRoleIT` | `exMembroMantemOPapelMasPerdeAsPermissoes` | integração |
| 5f | A listagem de comunidades traz o papel efetivo em cada linha | `EffectiveRoleIT` | `listagemDeComunidadesDevolveOPapelEfetivoEmCadaLinha` | integração |
| X1 | Toda a permissão do catálogo tem mensagem de recusa em português (guarda contra valor novo mudo) | `CommunityPermissionRulesTest` | `todaAPermissaoTemMensagemDeRecusaEmPortugues` | unitário |
| X2 | O dono tem todas as permissões (nenhuma ação lhe é negada) | `CommunityPermissionRulesTest` | `donoTemTodasAsPermissoesDaComunidade` | unitário |
| X3 | O caminho interno `/__test__/**` deixou de ser público (dívida fechada) | `SecurityChainIT` | `caminhoInternoDeTesteDeixouDeSerPublico` | integração |
| X4 | A cadeia de segurança não liberta nenhum caminho interno (guarda contra reintrodução) | `SecurityConfigConventionsTest` | `aCadeiaDeSegurancaNaoLibertaNenhumCaminhoInterno` | unitário |
| X5 | Uma recusa do Spring Security dentro do controlador devolve `403`, não `500` | `ApiExceptionHandlerTest` | `acessoNegadoDevolve403EmProblemDetail` | unitário |
| FE1 | O dono vê os membros e nomeia um moderador (renderização, **não** autorização) | `CommunityModeratorsPage.test.tsx` | `donoVeOsMembrosENomeiaModerador` | Vitest |
| FE2 | Remover moderador volta a mostrá-lo como membro | `CommunityModeratorsPage.test.tsx` | `removerModeradorVoltaAMostrarComoMembro` | Vitest |
| FE3 | Um `403` do servidor mostra a mensagem do servidor e nenhum botão de ação | `CommunityModeratorsPage.test.tsx` | `respostaDe403MostraAMensagemDoServidorESemBotoes` | Vitest |
| FE4 | O link «Gerir moderadores» só aparece com a permissão vinda do servidor | `CommunityPage.test.tsx` | `linkDeGerirModeradoresSoApareceComAPermissaoDoServidor` | Vitest |

**Critérios sem endpoint próprio, e o que é verificado em vez disso (declarado por
honestidade, não por omissão):** as capacidades `PUBLISH_TIPS`, `SETTLE_TIPS`, `READ_TIPS`,
`READ_CHAT`, `WRITE_CHAT`, `DELETE_ANY_CHAT_MESSAGE` e `DELETE_COMMUNITY` não têm ainda
endpoint (R7, R8, R11, R12 são F07/F08/F11/F12). São verificadas em dois níveis reais:
(i) a decisão de autorização, exaustivamente, em `CommunityPermissionRulesTest` (45 células
com tabela literal); (ii) a sua exposição pela API a partir de dados reais na base de dados,
em `EffectiveRoleIT` (`/access` e `/api/communities/{slug}` de um moderador, de um membro e
de um ex-membro). O `403` HTTP dessas ações passa a existir automaticamente no momento em
que F07/F08/F12 anexarem `@RequiresCommunityPermission` aos seus handlers — e o contrato
(C2)/(C5) obriga-as a acrescentar as linhas da matriz papel × endpoint nessa altura.

### 3.5 Contagem de testes

| Ficheiro | Testes novos |
| --- | ---: |
| `CommunityPermissionRulesTest` | 10 |
| `CommunityPermissionMatrixIT` | 25 |
| `ModeratorAppointmentIT` | 12 |
| `EffectiveRoleIT` | 6 |
| `CommunityAuthorizationContractIT` | 3 |
| `SecurityChainIT` | 1 |
| `SecurityConfigConventionsTest` | 1 |
| `ApiExceptionHandlerTest` | 1 |
| **Total backend novo** | **59** |
| `CommunityModeratorsPage.test.tsx` | 3 |
| `CommunityPage.test.tsx` (acrescentado ao ficheiro existente) | 1 |
| **Total frontend novo** | **4** |

Alvo: **140 + 59 = 199 testes JUnit** e **22 + 4 = 26 testes Vitest** em **12 ficheiros Vitest**
(11 + 1). Total **225**, com **0 falhas** e **nenhum dos 162 existentes alterado no seu
resultado**.

---

## 4. Alterações

### 4.1 Ficheiros a criar

**Backend — produção (13):**

| Caminho | Propósito |
| --- | --- |
| `backend/src/main/java/pt/seerhub/community/domain/CommunityPermission.java` | `enum` com os 9 valores do catálogo (§3.1). Cada valor carrega `mensagemDeRecusa()` (português de Portugal). `EDIT_COMMUNITY` usa **literalmente** `"Não tem permissão para editar esta comunidade."` — o mesmo texto que `CommunityEditIT` já assere hoje. |
| `backend/src/main/java/pt/seerhub/community/service/CommunityPermissionRules.java` | Classe `final`, construtor privado, funções estáticas puras: `Set<CommunityPermission> permissoesDe(MembershipRole role, boolean acessoPremium)` e `boolean permite(MembershipRole, boolean, CommunityPermission)`. Sem Spring, sem repositórios. Mesma família de `CommunityAccessRules`/`MembershipAccessRules`. |
| `backend/src/main/java/pt/seerhub/community/service/CommunityAuthorization.java` | `record (Long communityId, Long userId, MembershipRole role, boolean premium, Set<CommunityPermission> permissions)` com `pode(CommunityPermission)`, `gestor()` e a fábrica `semPapel(Long communityId, Long userIdOuNull)`. Valor puro — **nunca** entidade JPA, nunca segura uma `Community`. |
| `backend/src/main/java/pt/seerhub/community/service/CommunityPermissionService.java` | **A superfície de autorização.** `@Service`. Métodos: `autorizacaoDe(Community, Long)`, `autorizacoesDe(Collection<Community>, Long)` (uma única query), `pode(Community, Long, CommunityPermission)`, `exigir(Community, Long, CommunityPermission)` (`401`/`403`), `listarPapeisDoUtilizador(Long)`. Constante pública `MENSAGEM_AUTENTICACAO_NECESSARIA` reutilizada de `CommunityAccessService`. |
| `backend/src/main/java/pt/seerhub/community/security/RequiresCommunityPermission.java` | `@Target(METHOD) @Retention(RUNTIME)`, atributos `CommunityPermission value()` e `String slugVariable() default "slug"`. |
| `backend/src/main/java/pt/seerhub/community/security/CommunityPermissionInterceptor.java` | `HandlerInterceptor.preHandle`: se o handler é `HandlerMethod` e tem a anotação, lê a variável de caminho de `HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE`, resolve o principal de `SecurityContextHolder` (`AuthenticatedUser` → `id()`, caso contrário `null`), chama `communityService.obterEntidadeParaLeitura(slug, userId)` (**404**) e depois `communityPermissionService.exigir(...)` (**401/403**). Se a variável de caminho não existir, lança `IllegalStateException` (erro de programação, ruidoso). |
| `backend/src/main/java/pt/seerhub/config/WebMvcConfig.java` | `@Configuration implements WebMvcConfigurer`, regista o interceptor em `/api/**`. **Sem `@EnableWebMvc`** (não substituir a auto-configuração de MVC do Boot). |
| `backend/src/main/java/pt/seerhub/community/service/ModerationService.java` | `nomearModerador(slug, alvoUserId, autenticado)`, `removerModerador(slug, alvoUserId, autenticado)`, `listarMembros(slug, autenticado)`. Todas: resolver comunidade → `exigir(..., MANAGE_MODERATORS)` → regra de negócio. |
| `backend/src/main/java/pt/seerhub/community/api/ModerationController.java` | `POST /api/communities/{slug}/moderators`, `DELETE /api/communities/{slug}/moderators/{userId}`, `GET /api/communities/{slug}/members`. Os três com `@RequiresCommunityPermission(CommunityPermission.MANAGE_MODERATORS)`. |
| `backend/src/main/java/pt/seerhub/community/api/AppointModeratorRequest.java` | `record (@NotNull Long userId)`. |
| `backend/src/main/java/pt/seerhub/community/api/CommunityMemberResponse.java` | `record (Long userId, String username, String displayName, MembershipRole role, MembershipStatus status, Instant joinedAt, Instant expiresAt, boolean active)` + fábricas `de(CommunityMembership, boolean)` e `paraDonoSemMembership(User)`. **Nunca inclui email** (decisão D-10). |
| `backend/src/main/java/pt/seerhub/community/api/CommunityRoleResponse.java` | `record (Long communityId, String slug, String name, MembershipRole role, boolean premium, Set<CommunityPermission> permissions)`. |
| `backend/src/main/java/pt/seerhub/community/api/CommunityRoleController.java` | `GET /api/me/community-roles` → `List<CommunityRoleResponse>` do utilizador autenticado (todos os papéis: dono, moderador, membro). |

**Backend — testes (8):**

| Caminho | Testes |
| --- | ---: |
| `backend/src/test/java/pt/seerhub/community/CommunityPermissionRulesTest.java` | 10 |
| `backend/src/test/java/pt/seerhub/community/CommunityPermissionMatrixIT.java` | 25 |
| `backend/src/test/java/pt/seerhub/community/ModeratorAppointmentIT.java` | 12 |
| `backend/src/test/java/pt/seerhub/community/EffectiveRoleIT.java` | 6 |
| `backend/src/test/java/pt/seerhub/community/CommunityAuthorizationContractIT.java` | 3 |
| `backend/src/test/java/pt/seerhub/config/SecurityChainIT.java` | 1 |
| `backend/src/test/java/pt/seerhub/config/SecurityConfigConventionsTest.java` | 1 |
| `backend/src/test/java/pt/seerhub/common/error/ApiExceptionHandlerTest.java` | 1 |

**Frontend (3):**

| Caminho | Propósito |
| --- | --- |
| `frontend/src/lib/permissions.ts` | Tipos `PapelComunidade`, `PermissaoComunidade`, `MembroComunidade`, `PapelEmComunidade`; funções `listarMembros(slug)`, `nomearModerador(slug, userId)`, `removerModerador(slug, userId)`, `listarOsMeusPapeis()`, e o helper `pode(permissoes, permissao)`. Tudo sobre `apiFetch` (F01) — nunca `fetch` direto. |
| `frontend/src/pages/CommunityModeratorsPage.tsx` | Rota `/comunidades/:slug/moderadores`, dentro de `RequireAuth`. Lista membros, botões «Nomear moderador» / «Remover moderador», e um `role="alert"` com o `detail` do servidor quando a resposta é `403`. |
| `frontend/src/pages/CommunityModeratorsPage.test.tsx` | FE1–FE3. Mock de `fetch` encaminhado por URL, que **lança** para URL não previsto (padrão obrigatório herdado de F03). |

### 4.2 Ficheiros a editar

| Caminho | Alteração | Risco |
| --- | --- | --- |
| `backend/src/main/java/pt/seerhub/community/domain/CommunityMembership.java` | Acrescentar **só** `public void promoverAModerador()` (`role = MODERATOR`) e `public void despromoverParaMembro()` (`role = MEMBER`). **Nenhuma toca em `status` nem em `expiresAt`** — o ciclo de vida da subscrição de F03 fica fechado. `deDono`, `deSubscritor`, `cancelar`, `reativar`, `renovar` e o construtor protegido ficam literalmente intactos. | Baixo |
| `backend/src/main/java/pt/seerhub/community/repo/CommunityMembershipRepository.java` | Acrescentar `List<CommunityMembership> findByCommunityIdOrderByJoinedAtAsc(Long communityId)`. Nada existente é alterado. | Baixo |
| `backend/src/main/java/pt/seerhub/community/service/CommunityService.java` | (a) `editar(...)` troca `exigirDono(community, autenticado.id())` por `communityPermissionService.exigir(community, autenticado.id(), CommunityPermission.EDIT_COMMUNITY)`; (b) **remover `exigirDono`** (código morto depois de (a) — D-9); (c) `MENSAGEM_SEM_PERMISSAO` passa a `= CommunityPermission.EDIT_COMMUNITY.mensagemDeRecusa()`, **valor idêntico** ao literal atual, para `CommunityEditIT` continuar verde sem ser editado; (d) `criar`, `obterParaLeitura`, `listarAtivas`, `listarDoDono` passam a construir `CommunityResponse` com a autorização do viewer; (e) injetar `CommunityPermissionService`. `obterEntidadeParaLeitura` fica **intacta**. | Médio — é o ficheiro mais partilhado; `CommunityEditIT`/`CommunityVisibilityIT`/`CommunityCreationIT` (28 testes) têm de continuar verdes |
| `backend/src/main/java/pt/seerhub/community/api/CommunityResponse.java` | Acrescentar `MembershipRole viewerRole` e `Set<CommunityPermission> viewerPermissions` no fim do `record`; acrescentar a fábrica `de(Community, CommunityAuthorization)`; manter `de(Community, Long viewerIdOuNull)` com o comportamento atual (papel `null`, permissões vazias) apenas para o caminho anónimo, com Javadoc a dizer isso. `ownedByViewer` **não é removido** (D-14 de F02). | Baixo — `jsonPath("$.ownedByViewer")` de `CommunityCreationIT` mantém-se; `CommunityTestSupport.criar` desserializa o `record` com campos novos sem problema |
| `backend/src/main/java/pt/seerhub/community/api/CommunityController.java` | (a) `listar()` passa a receber `@AuthenticationPrincipal AuthenticatedUser autenticado` (pode ser `null`: a rota é pública) para preencher o papel efetivo em cada linha; (b) `PUT /api/communities/{slug}` ganha `@RequiresCommunityPermission(CommunityPermission.EDIT_COMMUNITY)`. | Baixo |
| `backend/src/main/java/pt/seerhub/community/api/CommunityAccessResponse.java` | Acrescentar `Set<CommunityPermission> permissions` no fim do `record` e um parâmetro na fábrica. `premium`, `manager`, `role`, `status`, `joinedAt`, `expiresAt` **ficam** (F07/F10/F11/F12 podem já depender deles). | Baixo |
| `backend/src/main/java/pt/seerhub/community/api/CommunityAccessController.java` | `acesso(...)` passa a pedir também `communityPermissionService.autorizacaoDe(community, viewerId)` e a passá-la à fábrica. `member-area` fica **intacto** (a porta premium de F03 não é redefinida). | Baixo |
| `backend/src/main/java/pt/seerhub/config/SecurityConfig.java` | **Remover** a linha `.requestMatchers("/__test__/**").permitAll()`; atualizar o Javadoc (a nota «o `PermissionEvaluator` por comunidade é de F04» passa a apontar para `CommunityPermissionService` e a explicar porque não se usa method security). Tudo o resto — `STATELESS`, `csrf.disable()`, ordem das regras, filtros, handlers, `BCRYPT_STRENGTH`, beans — fica literalmente igual. | Médio — ver §9, risco 4 |
| `backend/src/main/java/pt/seerhub/common/error/ApiExceptionHandler.java` | Acrescentar `@ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)` → `ProblemDetail` `403` com `correlationId` e mensagem fixa `"Não tem permissão para esta ação."` (constante pública nova `MENSAGEM_ACESSO_NEGADO`). Os quatro handlers existentes ficam intactos. | Baixo |
| `backend/src/test/java/pt/seerhub/common/error/ApiExceptionHandlerIT.java` | **Uma única alteração:** o pedido passa a `get("/__test__/boom").with(user("teste"))` (`SecurityMockMvcRequestPostProcessors.user`, já usado por `HealthCheckDbDownIT` desde F01). Nome do teste, asserções e controlador aninhado ficam **exatamente** como estão. | Médio — é um dos 162 testes da baseline; ver §9, risco 4 |
| `frontend/src/lib/communities.ts` | Acrescentar `viewerRole?: PapelComunidade \| null` e `viewerPermissions?: PermissaoComunidade[]` (**opcionais**, porque `spring.jackson.default-property-inclusion: non_null` omite `viewerRole` para o visitante anónimo) à interface `Comunidade`. Nada mais. | Baixo — sendo opcionais, as 4 fixtures de teste existentes não precisam de mudar |
| `frontend/src/lib/subscriptions.ts` | Acrescentar `permissions: PermissaoComunidade[]` a `AcessoComunidade`. | Baixo |
| `frontend/src/pages/CommunityPage.tsx` | Mostrar um `<Link to={/comunidades/${slug}/moderadores}>Gerir moderadores</Link>` **só quando** `acesso.permissions` inclui `MANAGE_MODERATORS`. Nenhum outro comportamento muda. | Baixo |
| `frontend/src/pages/CommunityPage.test.tsx` | Acrescentar `permissions: []` (e `permissions: ["MANAGE_MODERATORS"]` no caso do dono) às respostas `/access` do mock e o teste FE4. Os 4 testes existentes mantêm nome e asserções. | Baixo |
| `frontend/src/App.tsx` | Uma `<Route>` nova dentro de `<RequireAuth>`: `/comunidades/:slug/moderadores` → `CommunityModeratorsPage`, declarada **antes** de `/comunidades/:slug` (coerente com a ordem explícita que F02/F03 já usam). | Baixo |

### 4.3 Decisões fixadas neste plano

- **D-1 — `CommunityPermissionService` é a superfície única.** Nome final, não negociável pelas features seguintes: `pt.seerhub.community.service.CommunityPermissionService`.
- **D-2 — O catálogo `CommunityPermission` é fechado e nasce completo** com as permissões de R7, R8, R11 e R12 (§3.1). F07/F08/F12 usam-no; não o estendem.
- **D-3 — Nomear/remover moderador altera só `role`.** `status` e `expires_at` ficam intactos, o que faz com que despromover devolva o utilizador exatamente à subscrição que tinha por baixo (podendo ela já ter vencido — testado em 1d). Consequência aceite: um `MEMBER` pode ficar com `expires_at` no passado e sem acesso, e volta a subscrever pelo caminho normal de F03 (`renovar`, `200`).
- **D-4 — «Membro ativo» do critério 1 é `MembershipAccessRules.concedeAcessoPremium(...)`.** F04 não define uma segunda noção de atividade.
- **D-5 — Todas as recusas de estado do alvo são `409`,** com mensagens distintas: não é membro ativo / já é moderador / é o dono / não é moderador. Um `userId` inexistente devolve **o mesmo `409` e a mesma mensagem** que «não é membro ativo», para não servir de oráculo de existência de contas (coerente com `MENSAGEM_REGISTO_RECUSADO` de F01).
- **D-6 — `404` antes de `403`, sempre.** A comunidade é resolvida por `CommunityService.obterEntidadeParaLeitura` antes de qualquer avaliação de permissão; uma comunidade suspensa continua `404` para quem não tem linha de membership (regra de F02, não redefinida).
- **D-7 — `GET /api/communities/{slug}/members` exige `MANAGE_MODERATORS`** (ou seja, é do dono). Hoje a única razão para listar membros é escolher quem promover; alargar isto ao moderador é uma decisão de uma feature futura, com linha nova na matriz e testes novos.
- **D-8 — O papel global `ADMIN` não atravessa permissões de comunidade** (contrato C7), provado por 4g.
- **D-9 — `CommunityService.exigirDono` é removido.** Deixar um segundo caminho de verificação de propriedade contradiz a R4; nenhum teste o referencia.
- **D-10 — `CommunityMemberResponse` nunca expõe email.** Expõe `username`/`displayName`, que já são públicos noutras respostas.
- **D-11 — `ApiExceptionHandler` ganha um handler para `AccessDeniedException`.** É defesa em profundidade contra o `500`-em-vez-de-`403` descrito em §2.3, e cabe nesta feature por ser a feature de autorização.
- **D-12 — A dívida `/__test__/**` é fechada aqui.** Justificação: (i) F04 é a feature que reescreve a autorização — se não for agora, a linha fica na cadeia de produção até F15, num sistema que entretanto passa a ter autorização fina; (ii) o custo real é de duas linhas (remover o matcher; autenticar o pedido em `ApiExceptionHandlerIT` com `.with(user("teste"))`, um post-processor de `spring-security-test` já usado por `HealthCheckDbDownIT`), e não toca no nome nem nas asserções do teste da baseline; (iii) `JwtAuthenticationFilter` nunca limpa um contexto já preenchido por outro mecanismo (comentário explícito na classe), pelo que o post-processor funciona nesta cadeia; (iv) fica coberto por dois testes novos — comportamental (`SecurityChainIT`, anónimo → `401`) e mecânico (`SecurityConfigConventionsTest`, o ficheiro não volta a libertar caminhos internos). Se, contra o previsto, isto partir a suite, o plano manda **reverter só esta parte** (repor a linha, apagar X3/X4) e devolver a dívida a F15 com o motivo registado no handoff — nunca sacrificar um teste da baseline por ela.
- **D-13 — Nomes de método em português, nomes de classe em inglês.** Segue-se o precedente de facto de F02/F03 (`acessoDe`, `exigirAcessoPremium`, `podeSerLidaPor`), não a leitura literal do `CLAUDE.md`; consistência dentro do pacote vale mais.
- **D-14 — Nenhuma dependência Maven ou npm nova.** Sem `spring-boot-starter-aop` (o interceptor de MVC dispensa AOP), sem bibliotecas de teste novas.

### 4.4 Modelo de dados / migrações

**Nenhuma.** A tabela `community_memberships` do baseline `V2` já tem `role` com
`OWNER`/`MODERATOR`/`MEMBER` no `CHECK`, `status`, `joined_at`, `expires_at`, `version` e
`UNIQUE(community_id, user_id)` — tudo o que R4 precisa. Não é criada nenhuma tabela nova,
não é alterada nenhuma coluna, não é acrescentado nenhum ficheiro em
`backend/src/main/resources/db/migration/`. `V1`, `V2` e `V3` não são tocados.

Justificação de que não falta nada: um papel por comunidade é exatamente uma linha de
membership; a acumulação de papéis (secção 5 da spec) é a existência de N linhas do mesmo
`user_id` em comunidades diferentes, já garantida pela chave única composta; e as permissões
são derivadas do papel, nunca guardadas (guardá-las criaria dois pontos de verdade e a
possibilidade de divergirem).

---

## 5. Ordem de implementação

Cada passo é executável e verificável sozinho. Os testes vêm com o código do passo, nunca no fim.
Correr `./mvnw test` no fim de **cada** passo do backend.

1. **Catálogo e matriz pura.** Criar `CommunityPermission` e `CommunityPermissionRules`;
   escrever `CommunityPermissionRulesTest` (10 testes, incluindo a tabela literal de 45
   células). Sem Spring, sem base de dados. Verde antes de avançar. *(Cobre 2a, 2b, 2g, 3a–3c,
   4ab–4ad, X1, X2.)*
2. **A porta + o interceptor, provados cedo.** Criar `CommunityAuthorization`,
   `CommunityPermissionService`, `RequiresCommunityPermission`,
   `CommunityPermissionInterceptor`, `WebMvcConfig`. Anotar **apenas**
   `PUT /api/communities/{slug}` e trocar `exigirDono` pela porta em `CommunityService.editar`
   (removendo `exigirDono` e derivando `MENSAGEM_SEM_PERMISSAO` da enum). Escrever já
   `CommunityAuthorizationContractIT.aRecusaDoInterceptorNuncaChegaAoServico` e as 5 células
   de `PUT` da matriz (4t–4x). **Este é o passo de maior risco técnico** (§9, risco 1): se uma
   `ApiException` lançada em `preHandle` não sair como `ProblemDetail`, aplicar aqui o plano B
   (§9) antes de construir o resto em cima. `CommunityEditIT` tem de continuar verde sem ser
   editado.
3. **Nomeação e remoção de moderadores.** `ModerationService`, `ModerationController`,
   `AppointModeratorRequest`, `CommunityMemberResponse`, as duas transições em
   `CommunityMembership`, o método novo do repositório. Escrever `ModeratorAppointmentIT`
   (12 testes). *(Cobre 1a–1l.)*
4. **A matriz completa.** Escrever `CommunityPermissionMatrixIT` (25 testes) sobre os quatro
   endpoints, incluindo as asserções de «a base de dados não mudou» e as células de controlo
   `200`/`201`. *(Cobre 4a–4x, 2d–2f.)*
5. **Papel efetivo na API.** `CommunityAuthorization` exposto: campos novos em
   `CommunityResponse` e `CommunityAccessResponse`, `CommunityController.listar` com o
   principal, `CommunityRoleResponse` + `CommunityRoleController`
   (`GET /api/me/community-roles`), `CommunityPermissionService.listarPapeisDoUtilizador`.
   Escrever `EffectiveRoleIT` (6 testes). *(Cobre 5a–5f, 2c, 3d.)*
6. **Contrato mecânico.** Completar `CommunityAuthorizationContractIT` com
   `todoEndpointDeComunidadeDeclaraAPermissaoQueExige` e
   `aListaDeExcecoesSoContemRotasQueAindaExistem`, com a lista justificada
   `ENDPOINTS_SEM_PERMISSAO_DE_COMUNIDADE` a conter exatamente: `GET /api/communities/{slug}`
   (perfil público, teaser de R11), `GET /api/communities/{slug}/access` (estado, nunca `403`),
   `GET /api/communities/{slug}/member-area` (porta premium de F03, `CommunityAccessService`),
   `POST /api/communities/{slug}/subscription` e `DELETE /api/communities/{slug}/subscription`
   (auto-serviço: o utilizador age sobre a sua própria subscrição). *(Cobre 4y, 4z.)*
7. **Dívida `/__test__` + `AccessDeniedException`.** Remover o matcher de `SecurityConfig`,
   ajustar `ApiExceptionHandlerIT` com `.with(user("teste"))`, criar `SecurityChainIT`,
   `SecurityConfigConventionsTest` e `ApiExceptionHandlerTest`, acrescentar o handler de
   `AccessDeniedException`. Correr a suite completa **duas vezes seguidas**. *(Cobre X3–X5.)*
8. **Frontend.** `lib/permissions.ts`, campos novos em `lib/communities.ts` e
   `lib/subscriptions.ts`, `CommunityModeratorsPage.tsx` + teste (FE1–FE3), link condicional em
   `CommunityPage.tsx` + FE4, rota em `App.tsx`. Correr `npm test`, `npm run typecheck`,
   `npm run build`.
9. **Verificação final** (§7): suite completa duas vezes, contagens exatas, `grep` de
   `__test__`, e escrita do `handoff.md` com a secção §2.5 (contrato) copiada literalmente.

---

## 6. Não tocar

Ficheiros e comportamentos que F04 está proibida de alterar. É a cerca do raio de explosão.

**Nunca abrir, nunca editar:**
- `docs/specs/seerhub.md`
- `docs/features/BACKLOG.md`, `docs/features/CHANGELOG.md` (só o orquestrador escreve)
- `docs/features/F00-fundacoes/*`, `docs/features/F01-contas-autenticacao/*`,
  `docs/features/F02-comunidades/*`, `docs/features/F03-subscricoes/*` (planos e handoffs)
- `seerhub.md` (o brief na raiz)
- `.env` — **nunca ler, nunca abrir, nunca citar; contém uma chave de API real**
- `.claude/`
- `backend/src/main/resources/db/migration/V1__enable_extensions.sql`,
  `V2__baseline_schema.sql`, `V3__refresh_tokens.sql`

**Código de produção que fica intacto:**
- `pt.seerhub.community.service.CommunityAccessService` — **estender, nunca redefinir**.
  Nenhum método muda de assinatura ou de semântica; F04 só a consome.
- `pt.seerhub.community.service.MembershipAccessRules`, `CommunityAccessRules`,
  `SubscriptionService`, `MembershipExpiryTask`, `SlugGenerator`.
- `CommunityMembership.deDono/deSubscritor/cancelar/reativar/renovar` e o construtor privado.
- `CommunityService.obterEntidadeParaLeitura`, `obterParaLeitura`, `criar`, `listarAtivas`,
  `listarDoDono` (só muda a construção do DTO, não a regra).
- `GET /api/communities/{slug}/member-area` e `SubscriptionController` — F07/F10/F11/F12 criam
  os seus próprios endpoints, não estendem estes.
- Tudo em `pt.seerhub.user.**` exceto a leitura de `AuthenticatedUser`/`UserRepository`.
- `pt.seerhub.common.web.CorrelationIdFilter`, `ApiException`.
- `application.yml`, `application-local.yml`, `application-test.yml`, `docker-compose.yml`,
  `.env.example`, `pom.xml` (raiz e backend), `frontend/package.json`.

**Testes da baseline que ficam intactos** (os 162): todos, **com a única exceção
declarada** de `ApiExceptionHandlerIT`, onde é permitida exatamente uma alteração — juntar
`.with(user("teste"))` ao pedido — mantendo nome, asserções e o controlador aninhado. Nenhum
outro ficheiro de teste existente pode ser editado, renomeado ou apagado; em particular
`backend/src/test/java/pt/seerhub/support/AuthTestSupport.java` e
`CommunityTestSupport.java` **não são alterados** (F04 não precisa de helpers novos).

**Comportamentos que não podem mudar:**
- Um `MEMBER` `CANCELLED` dentro do prazo continua a ter acesso premium.
- O dono sem linha de membership continua a atravessar a porta premium.
- `GET /api/communities` continua a devolver só `ACTIVE`; `GET /api/communities/{slug}`
  continua a devolver `404` (não `403`) a um não-membro de comunidade suspensa.
- `POST/DELETE /api/communities/{slug}/subscription` mantêm os códigos de estado de F03.
- O texto de `CommunityService.MENSAGEM_SEM_PERMISSAO` é literalmente o mesmo.

---

## 7. Verificação

```bash
# 1. Suite completa do backend, na raiz do monorepo, duas vezes seguidas
#    (o contentor Postgres é partilhado e nunca limpo — nenhum teste pode depender de ordem)
./mvnw test
./mvnw test

# 2. Frontend
cd frontend && npm test
cd frontend && npm run typecheck
cd frontend && npm run build

# 3. A dívida /__test__ está mesmo fechada (tem de devolver zero linhas)
grep -rn "__test__" backend/src/main/java

# 4. Nenhuma verificação de permissão avulsa sobreviveu no código de produção
#    (as únicas ocorrências legítimas de comparação com o dono são
#     CommunityAccessService.paraUtilizadorSemMembership e CommunityPermissionService)
grep -rn "getOwner().getId().equals" backend/src/main/java
grep -rn "exigirDono" backend/src/main/java backend/src/test/java
```

**Critérios de sucesso, todos obrigatórios:**

1. `./mvnw test` → `Tests run: 199, Failures: 0, Errors: 0, Skipped: 0`, nas **duas** execuções.
   (140 da baseline + 59 novos. Se o total divergir, a diferença tem de ser explicada
   ficheiro a ficheiro no handoff — nunca «arredondada».)
2. Os 140 testes JUnit da baseline continuam todos a passar, com os mesmos nomes. Nenhum foi
   apagado, renomeado, fundido ou marcado `@Disabled`.
3. `cd frontend && npm test` → `Test Files 12 passed (12)`, `Tests 26 passed (26)`.
4. `npm run typecheck` e `npm run build` sem erros.
5. `grep -rn "__test__" backend/src/main/java` → **nenhuma linha**.
6. `grep -rn "exigirDono" backend/src` → **nenhuma linha**.
7. `grep -rn "getOwner().getId().equals" backend/src/main/java` → no máximo duas linhas, ambas
   dentro de `CommunityAccessService`/`CommunityPermissionService`.
8. Nenhum ficheiro novo em `backend/src/main/resources/db/migration/`; `V1`–`V3` com os
   checksums intactos (`FlywayBaselineIT` verde prova-o).
9. `MigrationNamingTest`, `ConfigurationConventionsTest`, `EnvExampleTest` e
   `DockerComposeTest` verdes (nenhuma variável de ambiente nova, nenhum segredo, nenhuma
   alteração de configuração).
10. `git status` (se versionado) não mostra alterações em nenhum ficheiro da lista §6.

**Verificação manual opcional** (não bloqueia, mas fecha o ciclo de M1 com o resultado
demonstrável do milestone «criar conta, criar comunidade com preço, subscrever, nomear
moderador»): com `docker compose --env-file .env.example up -d --build`, registar dois
utilizadores, o primeiro cria a comunidade, o segundo subscreve, o primeiro faz
`POST /api/communities/{slug}/moderators` com o `userId` do segundo (`201`), e o segundo
tenta `PUT /api/communities/{slug}` com um preço novo (`403`, com o preço inalterado em
`psql`). Registar o resultado no handoff.

---

## 8. Casos de fronteira cobertos por esta feature

Da secção 10 da spec e do raciocínio sobre a interação com F02/F03 — só os que F04 possui:

- **Nomear alguém que não é membro** → `409`, nenhuma linha criada (1e).
- **Nomear um membro cuja subscrição já expirou** → `409`: «de entre os membros **ativos**»
  usa a mesma definição de atividade de F03 (1f).
- **Nomear alguém que já é moderador** → `409` explícito, não idempotente silencioso (1g).
- **Nomear o dono** → `409`: o dono já tem tudo; promovê-lo despromoveria a sua própria linha (1h).
- **Nomear um `userId` inexistente** → `409` com a mesma mensagem de «não é membro ativo», para
  não revelar existência de contas (1i).
- **Remover quem não é moderador** (membro, dono, ou sem linha) → `409` (1j).
- **Dono sem linha de membership** (fallback sintético de F03) → aparece na lista de membros e
  mantém todas as permissões (1k).
- **Despromoção de um moderador cuja subscrição venceu durante o mandato** → volta a `MEMBER`
  sem acesso; re-subscrever é o caminho normal de F03 (1d).
- **Moderador acabado de nomear** → permissões imediatas, sem esperar pelos 15 minutos do access
  token, porque o papel de comunidade nunca viaja no JWT (1l; D-4 de F01).
- **Ex-membro** (`EXPIRED`, ou `ACTIVE` com data já passada antes de a tarefa diária correr) →
  continua a ser `MEMBER` como identidade, mas com conjunto de permissões vazio (5e, 4d, 4k, 4q, 4w).
- **Pedido sem sessão** a qualquer endpoint protegido → `401` da cadeia de filtros, nunca `403`
  (4a, 4h, 4n, 4t).
- **Comunidade suspensa** → a regra de F02 continua a mandar: `404` para quem não tem linha,
  antes de qualquer avaliação de permissão; o dono continua a poder editar e a poder nomear.
- **`ADMIN` global sem relação com a comunidade** → `403`; a moderação de plataforma é R14 (4g).

---

## 9. Riscos em aberto

1. **Uma `ApiException` lançada em `HandlerInterceptor.preHandle` pode não ser convertida em
   `ProblemDetail`.** O `DispatcherServlet` encaminha exceções de `applyPreHandle` para os
   `HandlerExceptionResolver` (e portanto para o `@RestControllerAdvice`), mas isto não está
   provado neste repositório. **Como descobrir cedo, e barato:** é o passo 2 da ordem de
   implementação — o primeiro teste escrito depois do interceptor é
   `moderadorNaoAlteraOPrecoDevolve403`, que assere `403` **e** o `detail` exato. Se falhar,
   **plano B fixado**: o interceptor apanha a própria `ApiException` e escreve a resposta
   diretamente, com o `ObjectMapper`, no mesmo formato `ProblemDetail`+`correlationId` — é
   exatamente o padrão que `ProblemDetailAccessDeniedHandler`/`ProblemDetailAuthenticationEntryPoint`
   já usam desde F01. Nenhuma outra parte do plano muda.
2. **`@TestInstance(Lifecycle.PER_CLASS)` com `@BeforeAll` não estático em
   `CommunityPermissionMatrixIT`** (usado para montar uma vez o cenário de 6 principais e não
   pagar ~144 registos com BCrypt força 10). O Spring TestContext suporta-o, mas se a injeção
   de dependências não estiver feita a tempo, o fallback é um campo `static` inicializado
   preguiçosamente no `@BeforeEach` com verificação de nulo. **Regra dura, independentemente do
   mecanismo:** as células de sucesso (`donoNomeiaModeradorDevolve201`,
   `donoRemoveModeradorDevolve200`, `donoEditaAComunidadeDevolve200`) **criam os seus próprios
   utilizadores e a sua própria comunidade dentro do teste** — nunca mutam o cenário
   partilhado, senão as células de recusa passam a depender da ordem de execução (armadilha já
   documentada por F02/F03: o contentor Postgres é partilhado e nunca limpo).
3. **`CommunityService` é o ficheiro mais partilhado do repositório** e F04 mexe-lhe em cinco
   sítios; 28 testes de F02 dependem dele. Mitigação: a alteração de `editar` é a primeira
   (passo 2), isolada, com a suite a correr logo a seguir, e `MENSAGEM_SEM_PERMISSAO` mantém o
   **valor literal** exato para `CommunityEditIT.tentativaDeEdicaoPorOutroUtilizador...` não
   precisar de ser tocado.
4. **Remover `/__test__/**` pode partir `ApiExceptionHandlerIT`** se o post-processor `user()`
   não sobreviver à cadeia (por exemplo, se algum filtro limpar o contexto). A leitura do
   código diz que não limpa (`JwtAuthenticationFilter` só age com header `Bearer` e nunca
   limpa um contexto já preenchido), e `HealthCheckDbDownIT` já usa o mesmo post-processor
   desde F01. **Se mesmo assim falhar:** reverter só o passo 7 (repor a linha em
   `SecurityConfig`, apagar `SecurityChainIT` e `SecurityConfigConventionsTest`), devolver a
   dívida a F15 com o motivo exato registado no handoff, e continuar. Um teste da baseline
   vermelho é falha da feature; a dívida fechada não vale esse preço.
5. **Ordem de execução do interceptor vs. validação de corpo.** Com a anotação, um `PUT` com
   corpo inválido feito por quem não tem permissão passa a devolver `403` em vez de `400` (a
   permissão é avaliada antes da desserialização/validação). Verificado que **nenhum** teste
   existente combina corpo inválido com utilizador sem permissão: os `400` de `CommunityEditIT`
   são todos emitidos pelo dono, e o `403` é emitido com um corpo válido. Ainda assim, a
   inversão é deliberada e correta (não revelar o resultado da validação a quem não pode agir);
   fica registada no handoff.
6. **Acrescentar campos a `CommunityResponse`** afeta `CommunityTestSupport.criar`, que o
   desserializa. Um `record` com campos novos desserializa sem problema (propriedades ausentes
   → `null`/vazio), mas com `default-property-inclusion: non_null` o campo `viewerRole` é
   **omitido** do JSON quando é nulo: as asserções de 5b têm de usar
   `jsonPath("$.viewerRole").doesNotExist()`, nunca `.value(null)`.
7. **Volume de testes** (59 novos, 25 deles numa só classe com HTTP + Postgres). Se o tempo da
   suite crescer de forma incomodativa, a mitigação é a do risco 2 (um cenário por classe), não
   cortar células da matriz — a matriz completa **é** o critério 4 e não é negociável.