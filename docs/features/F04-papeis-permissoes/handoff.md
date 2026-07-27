# F04 — handoff

**Status:** COMPLETE
**Implementado:** 2026-07-27 · Sonnet 5
**Test run:** `./mvnw test` → 200 passados, 0 falhas, 0 erros, 0 ignorados (corrido três vezes seguidas, todas verdes — 140 da baseline + 60 novos, ver "Desvios" sobre a diferença de 1 face ao alvo de 59 do plano) · `cd frontend && npm test` → `Test Files 12 passed (12)`, `Tests 26 passed (26)` (22 da baseline + 4 novos, exatamente o alvo do plano) · `npm run typecheck` limpo · `npm run build` limpo

## O que agora existe

Uma única autoridade de permissões por comunidade, verificada sempre no servidor. Um
catálogo fechado de 9 ações (`CommunityPermission`), uma matriz pura papel × permissão
(`CommunityPermissionRules`), uma porta de serviço (`CommunityPermissionService`) que
traduz «este utilizador, nesta comunidade, pode esta ação?» em `401`/`403`, e a anotação
`@RequiresCommunityPermission` que aplica a mesma porta na fronteira HTTP através de
`CommunityPermissionInterceptor`, antes de qualquer controlador correr. O dono nomeia e
remove moderadores de entre os membros ativos (`POST`/`DELETE /api/communities/{slug}/moderators`,
`GET .../members`); um moderador que tente nomear outro moderador ou alterar o preço
recebe `403`; um membro, um ex-membro e um `ADMIN` global sem relação com a comunidade
recebem `403`; um pedido sem sessão recebe `401`. A API devolve o papel efetivo e o
conjunto de permissões do utilizador em `GET /api/communities/{slug}`
(`viewerRole`/`viewerPermissions`), `GET /api/communities/{slug}/access` (`permissions`),
`GET /api/communities` (uma linha por comunidade) e o novo `GET /api/me/community-roles`
(a acumulação de papéis da secção 5 da spec: dono de A, moderador de B, membro de C). O
frontend ganhou `/comunidades/:slug/moderadores` e o link "Gerir moderadores" em
`CommunityPage`, condicionado inteiramente à permissão vinda do servidor.

A dívida herdada `requestMatchers("/__test__/**").permitAll()` da cadeia de segurança de
produção **está fechada**: removida de `SecurityConfig`, com dois testes novos a provar
que não volta (`SecurityChainIT`, comportamental; `SecurityConfigConventionsTest`,
mecânico) e um terceiro (`ApiExceptionHandlerTest`) a provar que uma `AccessDeniedException`
dentro de um controlador dá `403`, não `500`.

## Superfície pública para as próximas features (contrato obrigatório para F07, F08, F12, F14)

**Esta secção é copiada literalmente do §2.5 do plano de F04 — é o contrato que F07, F08,
F12 e F14 têm de seguir sem exceção.**

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
nunca um `if` avulso num controlador de admin. (Provado por `adminGlobalSemRelacaoNaoNomeiaModeradorDevolve403`.)

**(C8) `DELETE_COMMUNITY` existe no catálogo e é negada a toda a gente exceto ao dono, mas
não tem endpoint em F04** — apagar comunidade não está em R2 nem em R4 como endpoint, e R14
prefere suspender. Quem construir o endpoint usa esta permissão; não inventa outra.

### Uso exato da anotação (para F07/F08/F12 HTTP; F12 STOMP usa (C4) diretamente)

```java
@RestController
public class TipController {

    private final TipService tipService;

    @PostMapping("/api/communities/{slug}/tips")
    @RequiresCommunityPermission(CommunityPermission.PUBLISH_TIPS)   // slugVariable() default = "slug"
    public TipResponse publicar(
            @PathVariable String slug,
            @Valid @RequestBody CreateTipRequest request,
            @AuthenticationPrincipal AuthenticatedUser autenticado) {
        return tipService.publicar(slug, request, autenticado);
    }
}
```

O interceptor resolve a comunidade e avalia a permissão **antes** de o controlador correr —
mas isto não substitui a chamada explícita a `communityPermissionService.exigir(...)` dentro
do serviço (padrão C1): o serviço tem de repetir a verificação porque (i) é a única garantia
que existe para caminhos não-HTTP (STOMP, tarefas agendadas), e (ii) mantém o contrato
verdadeiro mesmo que alguém invoque o serviço diretamente de outro sítio. As duas camadas são
deliberadas e redundantes — ver §2.4 do plano.

Se a variável de caminho declarada em `slugVariable()` não existir no pedido (endpoint mal
anotado, ex.: caminho sem `{slug}`), o interceptor lança `IllegalStateException` — erro de
programação, detetado no arranque do primeiro teste que exercitar esse endpoint, nunca
silencioso.

### Como estender o catálogo e a matriz (só se a spec realmente mudar)

1. Acrescentar o valor a `pt.seerhub.community.domain.CommunityPermission`, com a sua
   `mensagemDeRecusa()` em português de Portugal.
2. Acrescentar a(s) linha(s) correspondente(s) em `CommunityPermissionRules.permissoesDe`
   (branch `OWNER`/`MODERATOR`/`MEMBER`).
3. Acrescentar a célula (ou as 5 células, uma por estado de papel) a
   `CommunityPermissionRulesTest.aMatrizCompletaDePapelPorPermissaoEExatamenteAEsperada` —
   este teste falha sozinho se o número de permissões do catálogo mudar sem a tabela
   acompanhar.
4. Se a permissão nova tiver um endpoint novo, acrescentar `@RequiresCommunityPermission`
   ao handler e uma linha nova (um `*IT` por papel: anónimo `401`, visitante/membro/
   ex-membro/outro-papel-sem-a-permissão `403`, quem tem a permissão `200`/`201` de
   controlo) — sem isso, `CommunityAuthorizationContractIT.todoEndpointDeComunidadeDeclaraAPermissaoQueExige`
   fica vermelho.

### O que as próximas features **não devem fazer**

- Não redefinir «tem acesso premium» — isso é sempre `CommunityAccessService`/
  `MembershipAccessRules` (F03), nunca uma segunda pergunta dentro de F07/F08/F12.
- Não usar `@PreAuthorize`/`PermissionEvaluator` para autorização por comunidade — ver
  "Decisão estrutural" abaixo. `@PreAuthorize` continua ligado (`@EnableMethodSecurity`) e
  seguro de usar para outra coisa (o handler de `AccessDeniedException` cobre-o), mas R4
  deliberadamente não o usa.
- Não ler o papel de comunidade do JWT — nunca viajou lá (D-4 de F01) e continua a ser lido
  da base de dados a cada pedido, via `CommunityPermissionService`.
- Não construir uma segunda lista de "endpoints sem permissão" — há uma só, dentro de
  `CommunityAuthorizationContractIT`, e adicionar-lhe uma linha é uma decisão revista, não
  um atalho.
- Não estender `GET /api/communities/{slug}/member-area` nem `SubscriptionController` —
  cada feature cria os seus próprios endpoints e chama as portas que precisar
  (`CommunityAccessService` para premium, `CommunityPermissionService` para permissões).

### Decisão estrutural: porquê não `@PreAuthorize`/`PermissionEvaluator`

`pt.seerhub.common.error.ApiExceptionHandler` tinha (antes de F04) só
`@ExceptionHandler(Exception.class)` → `500`. Uma `AuthorizationDeniedException` lançada
pela method security **dentro** da invocação do controlador seria resolvida pelos
`HandlerExceptionResolver` do `DispatcherServlet`, cairia nesse handler genérico, e
devolveria `500` em vez de `403`. F04 usa `ApiException(403, ...)` (o mecanismo já provado
por F02/F03) e fecha o buraco latente na mesma, acrescentando
`@ExceptionHandler(AccessDeniedException.class)` a `ApiExceptionHandler` — para que
qualquer `@PreAuthorize` futuro (ex.: se F14 decidir usá-lo em `/api/admin/**`) devolva
`403` e não `500`. Ver `ApiExceptionHandlerTest.acessoNegadoDevolve403EmProblemDetail`.

## Ficheiros criados

**Backend — produção (13):**

| Caminho | Propósito |
| --- | --- |
| `community/domain/CommunityPermission.java` | Catálogo fechado de 9 permissões + `mensagemDeRecusa()` |
| `community/service/CommunityPermissionRules.java` | A matriz pura, `permissoesDe(role, premium)` / `permite(...)` |
| `community/service/CommunityAuthorization.java` | Record de valor: `communityId, userId, role, premium, permissions`, `pode(...)`, `gestor()`, `semPapel(...)` |
| `community/service/CommunityPermissionService.java` | A porta: `autorizacaoDe`, `autorizacoesDe` (batch), `pode`, `exigir`, `listarPapeisDoUtilizador` |
| `community/security/RequiresCommunityPermission.java` | A anotação (`value()`, `slugVariable() default "slug"`) |
| `community/security/CommunityPermissionInterceptor.java` | `HandlerInterceptor`: 404 → 401/403, antes do controlador |
| `config/WebMvcConfig.java` | Regista o interceptor em `/api/**`, sem `@EnableWebMvc` |
| `community/service/ModerationService.java` | `nomearModerador`, `removerModerador`, `listarMembros` |
| `community/api/ModerationController.java` | `POST`/`DELETE .../moderators[/{userId}]`, `GET .../members` |
| `community/api/AppointModeratorRequest.java` | `record (Long userId)` |
| `community/api/CommunityMemberResponse.java` | DTO de membro — nunca inclui email (D-10) |
| `community/api/CommunityRoleResponse.java` | Uma linha de `/api/me/community-roles` |
| `community/api/CommunityRoleController.java` | `GET /api/me/community-roles` |

**Backend — testes (8, 60 métodos):** `community/{CommunityPermissionRulesTest,CommunityPermissionMatrixIT,ModeratorAppointmentIT,EffectiveRoleIT,CommunityAuthorizationContractIT}.java`,
`config/{SecurityChainIT,SecurityConfigConventionsTest}.java`,
`common/error/ApiExceptionHandlerTest.java`.

**Frontend (3):** `lib/permissions.ts`, `pages/CommunityModeratorsPage.tsx` + `.test.tsx`.

## Ficheiros editados

| Caminho | Alteração |
| --- | --- |
| `community/domain/CommunityMembership.java` | `promoverAModerador()`/`despromoverParaMembro()` — só mudam `role`; `status`/`expiresAt` intactos |
| `community/repo/CommunityMembershipRepository.java` | `findByCommunityIdOrderByJoinedAtAsc` |
| `community/service/CommunityService.java` | `editar` usa `communityPermissionService.exigir(..., EDIT_COMMUNITY)`; `exigirDono` removido; `MENSAGEM_SEM_PERMISSAO` deriva da enum (valor literal idêntico); `criar`/`obterParaLeitura`/`listarAtivas`/`listarDoDono` constroem `CommunityResponse` com a autorização do viewer (`listarAtivas`/`listarDoDono` ganham parâmetro `viewerIdOuNull`) |
| `community/api/CommunityResponse.java` | `viewerRole`, `viewerPermissions` novos; fábrica `de(Community, CommunityAuthorization)`; `de(Community, Long)` mantida só para o caminho anónimo |
| `community/api/CommunityController.java` | `listar` recebe `@AuthenticationPrincipal` (pode ser `null`); `PUT` ganha `@RequiresCommunityPermission(EDIT_COMMUNITY)` |
| `community/api/CommunityAccessResponse.java` | `permissions` novo; fábrica ganha parâmetro `CommunityAuthorization` |
| `community/api/CommunityAccessController.java` | `acesso` passa também a autorização à fábrica |
| `config/SecurityConfig.java` | Removida `requestMatchers("/__test__/**").permitAll()`; Javadoc atualizado |
| `common/error/ApiExceptionHandler.java` | `@ExceptionHandler(AccessDeniedException.class)` → `403`, `MENSAGEM_ACESSO_NEGADO` |
| `common/error/ApiExceptionHandlerIT.java` | Única alteração: `.with(user("teste"))` no pedido a `/__test__/boom` |
| `frontend/src/lib/communities.ts` | `viewerRole?`, `viewerPermissions?` (opcionais) em `Comunidade` |
| `frontend/src/lib/subscriptions.ts` | `permissions: PermissaoComunidade[]` em `AcessoComunidade` |
| `frontend/src/pages/CommunityPage.tsx` | Link "Gerir moderadores" só quando `pode(acesso.permissions, "MANAGE_MODERATORS")` |
| `frontend/src/pages/CommunityPage.test.tsx` | `permissions: []`/`["READ_TIPS",...]` nos mocks de `/access`; teste FE4 novo |
| `frontend/src/App.tsx` | Rota `/comunidades/:slug/moderadores`, antes de `/comunidades/:slug` |

## Testes

Backend: **200 testes JUnit** (140 da baseline + 60 novos — ver "Desvios" sobre a
diferença de 1 face aos 59 previstos), suite corrida **três** vezes seguidas, sempre
verde.

| Ficheiro | Testes | Critérios |
| --- | ---: | --- |
| `CommunityPermissionRulesTest` | 11 | 2a, 2b, 2g, 3a–3c, 4ab–4ad, X1, X2 |
| `CommunityPermissionMatrixIT` | 25 | 2d–2f, 4a–4x |
| `ModeratorAppointmentIT` | 12 | 1a–1l |
| `EffectiveRoleIT` | 6 | 2c, 3d, 5a–5f |
| `CommunityAuthorizationContractIT` | 3 | 4y, 4z, 4aa |
| `SecurityChainIT` | 1 | X3 |
| `SecurityConfigConventionsTest` | 1 | X4 |
| `ApiExceptionHandlerTest` | 1 | X5 |

Frontend: **4 testes novos** — `CommunityModeratorsPage.test.tsx` (FE1–FE3),
`CommunityPage.test.tsx` (FE4, acrescentado ao ficheiro existente).

## Desvios face ao plano

1. **`CommunityPermissionRulesTest` tem 11 testes, não 10.** A tabela do §3.4 do plano
   lista 11 linhas distintas para este ficheiro (2a, 2b, 2g, 3a, 3b, 3c, 4ab, 4ac, 4ad,
   X1, X2), cada uma com nome de método próprio; a soma do §3.5 ("10") está errada, não o
   âmbito — mesmo tipo de deslize de aritmética que os handoffs de F01/F02/F03 já
   registaram nos seus próprios planos. Implementei as 11, sem fundir nem omitir nenhuma.
   Consequência: o total real é **140 + 60 = 200** testes JUnit, não os 199 previstos —
   a diferença de 1 é inteiramente esta linha.
2. **`CommunityAuthorizationContractIT` precisou de `@Qualifier("requestMappingHandlerMapping")`
   ao injetar `RequestMappingHandlerMapping`.** O contexto de teste tem dois beans desse
   tipo — `requestMappingHandlerMapping` (MVC) e `controllerEndpointHandlerMapping`
   (Actuator, já presente desde F00) — e o plano não previa a ambiguidade. Sem o
   qualificador, os três testes deste ficheiro falhavam no arranque do contexto
   (`NoUniqueBeanDefinitionException`), antes de chegar a qualquer asserção. Nenhum
   comportamento de produção foi alterado; é só uma correção de injeção no teste.
3. **`ModeratorAppointmentIT.moderadorNomeadoGanhaAsPermissoesNoMesmoInstante` (1l) usa
   `GET /api/communities/{slug}/access` em vez de `GET .../members`.** A primeira versão
   verificava que o moderador recém-nomeado passava a poder listar membros — mas
   `GET .../members` exige `MANAGE_MODERATORS` (D-7 do plano: só o dono, nunca o
   moderador), por isso a asserção original falhava sempre com `403`, mesmo com a
   promoção a funcionar corretamente. O teste corrigido verifica, com o **mesmo** access
   token, que `/access` muda de `role: MEMBER, manager: false` para
   `role: MODERATOR, manager: true` com `PUBLISH_TIPS`/`SETTLE_TIPS` no array
   `permissions` — a mesma prova (permissão efetiva sem reemitir o JWT), sobre um
   endpoint que o moderador realmente pode chamar. Nenhum critério de aceitação foi
   enfraquecido; o texto do critério 1l ("ganha as permissões de imediato") está
   igualmente coberto.
4. **O critério de verificação §7.7 do plano ("no máximo duas linhas" para
   `getOwner().getId().equals`) não se verificou à letra — há cinco, todas legítimas.**
   `grep -rn "getOwner().getId().equals" backend/src/main/java` devolve:
   `CommunityAccessService` (a esperada, F03), `CommunityPermissionService` (a esperada,
   F04), mais três que o plano não previu mas que não são o anti-padrão que o critério
   quer eliminar: (i) duas em `CommunityResponse.de` (as duas fábricas), a calcular
   `ownedByViewer` — um campo de UI que já existia antes de F04 (D-14 de F02) e que F04
   está explicitamente proibida de remover, nunca uma decisão de autorização; (ii) uma em
   `ModerationService.nomearModerador`, a regra de negócio "não pode nomear o próprio
   dono" (critério 1h) — uma validação de estado do alvo (`409`), não uma verificação de
   permissão (essa já aconteceu antes, via `communityPermissionService.exigir(...,
   MANAGE_MODERATORS)`). Nenhuma destas três linhas contorna `CommunityPermissionService`
   para decidir "pode ou não pode"; documentado aqui para que quem correr o mesmo `grep`
   no futuro não confunda isto com uma regressão.

Nenhum desvio contraria a spec nem o plano.

## Dívidas

**Fechada por F04:**
1. **`requestMatchers("/__test__/**").permitAll()`** — removida de `SecurityConfig`.
   `grep -rn "__test__" backend/src/main/java` devolve zero linhas. Provado por
   `SecurityChainIT.caminhoInternoDeTesteDeixouDeSerPublico` (comportamental: um pedido
   anónimo a `/__test__/boom` dá `401`) e `SecurityConfigConventionsTest.aCadeiaDeSegurancaNaoLibertaNenhumCaminhoInterno`
   (mecânico: o ficheiro-fonte nunca volta a conter a substring). O único ajuste na
   suite herdada foi `ApiExceptionHandlerIT`, que passou a autenticar o pedido com
   `.with(user("teste"))` — nome, asserções e o controlador de teste aninhado ficaram
   exatamente como estavam.

**Herdadas, ainda por resolver (nenhuma agravada por F04):**
2. **`npm audit`** — nenhuma dependência nova, backend ou frontend (D-14 do plano de F04:
   sem `spring-boot-starter-aop`, sem bibliotecas de teste novas). As vulnerabilidades já
   registadas por F00 (7, incluindo 1 crítica, em dependências de build) continuam por
   investigar, sem agravamento. São de desenvolvimento/build, não afetam produção.
3. **Renomear `API_KEY` → `API_FOOTBALL_KEY` no `.env` real** — continua pendente do
   utilizador, obrigatório antes de F05. F04 nunca leu nem editou o `.env` (regra
   absoluta do plano) e não chama a API-Football em nenhum caminho.
4. **`comunidadesComAcessoPremium` filtra em Java** (dono F10), `GET /api/communities`
   sem paginação (dono F10), upload de avatar/banner (dono F15) — F04 não agrava
   nenhuma e não resolve nenhuma.

**Novas, introduzidas por F04:**
5. **Duplo pedido de autorização por rota HTTP protegida** (interceptor + porta chamada
   de novo dentro do serviço) — deliberado (§2.4 do plano: a porta de serviço tem de ser
   chamada de qualquer forma para cobrir STOMP/tarefas agendadas), mas significa duas
   consultas à base de dados por pedido em vez de uma. Aceite para v1; se o perfil de
   carga em produção justificar, uma otimização futura pode passar a `CommunityAuthorization`
   já calculada pelo interceptor ao serviço via atributo do pedido — não é urgente e não
   está pedida por nenhum requisito.
6. **`GET /api/communities/{slug}/members` exige `MANAGE_MODERATORS` (só o dono)** — D-7
   do plano, decisão deliberada para v1. Se uma feature futura quiser que o moderador
   também veja a lista de membros, é uma linha nova na matriz (`CommunityPermissionRules`)
   mais testes novos — nunca alargar a regra silenciosamente.

## Confirmação sobre `API_KEY` → `API_FOOTBALL_KEY`

**Ainda pendente, por confirmar pelo utilizador** — a mesma dívida registada por F00 e
reconfirmada por F01, F02 e F03. F04 nunca leu nem editou o `.env` real e não chama a
API-Football em nenhum caminho. **Obrigatório antes de F05 arrancar** — F05 é football
data synchronisation e vai precisar da chave real com o nome correto.

## Verificação manual

**Não repetida nesta feature** (mesmo padrão que F03 já tinha registado): as anteriores
(F00–F03) já confirmaram o arranque real dos três serviços via `docker compose`, e F04
não altera `docker-compose.yml`, `Dockerfile`, `.env.example` nem nenhuma migração — o
risco de regressão nesse eixo é considerado baixo. `./mvnw test` (três vezes seguidas) e
`npm test`/`npm run typecheck`/`npm run build` são a prova usada aqui. Se quem vier a
seguir quiser essa confirmação extra, o roteiro está no §7 do plano de F04: registar dois
utilizadores, o primeiro cria a comunidade, o segundo subscreve, o primeiro faz
`POST /api/communities/{slug}/moderators` com o `userId` do segundo (`201`), e o segundo
tenta `PUT /api/communities/{slug}` com um preço novo (`403`, preço inalterado em `psql`).

## Avisos para quem vier a seguir

- **F05 (dados de futebol) mal toca em autorização** — não cria comunidades nem
  permissões de comunidade; se vier a expor algum endpoint sob `/api/communities/{slug}/...`
  (pouco provável, dado o âmbito de R5), esse endpoint entra na mesma disciplina de (C2):
  `@RequiresCommunityPermission` ou uma linha justificada na lista de exceções.
- **404 antes de 403, sempre** (D-6) — `CommunityPermissionInterceptor` chama
  `communityService.obterEntidadeParaLeitura(slug, userId)` antes de
  `communityPermissionService.exigir(...)`. Uma comunidade suspensa continua `404` para
  quem não tem linha de membership, mesmo num endpoint protegido por
  `CommunityPermission` — a regra de visibilidade de F02 não foi redefinida.
- **`CommunityAuthorization.gestor() == true` não implica uma `CommunityMembership`
  real por trás** — herdado do mesmo aviso de F03 sobre `CommunityAccess`: o dono sem
  linha de membership (fallback sintético) sintetiza `role=OWNER`, `premium=true`, todas
  as permissões, sem nenhuma linha na tabela. `ModerationService.listarMembros` já trata
  este caso (`CommunityMemberResponse.paraDonoSemMembership`).
- **`CommunityResponse.de(Community, Long)` só deve ser usada no caminho anónimo.**
  Qualquer novo código que já tenha um `userId` não nulo deve chamar
  `communityPermissionService.autorizacaoDe(...)` e usar a fábrica
  `de(Community, CommunityAuthorization)` — nunca o overload antigo, que fixa
  `viewerRole=null`/`viewerPermissions=[]` incondicionalmente.
- **`viewerRole` é omitido do JSON quando `null`** (`spring.jackson.default-property-inclusion: non_null`)
  — testar com `jsonPath("$.viewerRole").doesNotExist()`, nunca `.value(null)`.
  `viewerPermissions`/`permissions` continuam presentes como `[]` (coleção vazia não é
  `null`).
- **`CommunityPermissionMatrixIT` e `ModeratorAppointmentIT` criam o seu próprio cenário
  por teste** (dono + comunidade dedicados) — nenhuma célula de sucesso muta um cenário
  partilhado. O contentor Postgres continua partilhado e nunca limpo entre classes
  (herdado de F00); usar sempre `communityTestSupport.nomeUnico(...)`/`authTestSupport.emailUnico(...)`.
- **`AuthTestSupport.registarAdmin` reautentica depois de promover** — o token do registo
  ainda diria `USER` (D-4 de F01: o papel global também é fixado à emissão do token, só
  não é o mesmo problema do papel de comunidade). Usado em `adminGlobalSemRelacaoNaoNomeiaModeradorDevolve403`.
