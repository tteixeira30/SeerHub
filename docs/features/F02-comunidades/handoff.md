# F02 — handoff

**Status:** COMPLETE
**Implementado:** 2026-07-27 · Sonnet 5
**Test run:** `./mvnw test` → 84 passados, 0 falhas, 0 erros, 0 ignorados (corrido duas vezes seguidas, ambas verdes) · `cd frontend && npm test` → 17 passados, 0 falhados · `npm run typecheck` limpo · `npm run build` limpo · verificado também em `docker compose --env-file .env.example up --build` real (ver secção "Verificação manual")

## O que agora existe

Um utilizador autenticado cria a sua própria comunidade num único pedido
(`POST /api/communities`), sem aprovação: escolhe um nome entre 3 e 60
caracteres, o sistema deriva um `slug` único (com sufixo numérico
incremental em colisão) e grava a comunidade em `ACTIVE`, criando na mesma
transação a linha `community_memberships` que o torna `OWNER` com
membership ativa e sem data de expiração. O dono edita nome, descrição,
avatar, banner e preço mensal (`PUT /api/communities/{slug}`), sempre em
cêntimos inteiros — negativos e frações (`12.50`) devolvem `400`, nunca são
truncados em silêncio. Ninguém tem mais de três comunidades `ACTIVE` em
simultâneo (as suspensas não contam); a quarta tentativa devolve `409`.
Uma comunidade suspensa desaparece de `GET /api/communities` (só
`ACTIVE`) e de `GET /api/communities/{slug}` para quem não é membro
(`404`, mesma resposta de um slug inexistente), mas quem já tem uma linha
de membership — qualquer `role`, qualquer `status`, o que inclui sempre o
dono — continua a lê-la por completo; o dono continua também a editá-la.
Existe o ponto de verificação público
`CommunityAccessRules.exigirQueAceitaNovasSubscricoes(...)` para F03
invocar no seu caminho de subscrição. No frontend existem `/comunidades`
(as minhas), `/comunidades/nova` (criação) e
`/comunidades/:slug/definicoes` (edição pelo dono), todas protegidas por
`RequireAuth`, todas usando `apiFetch`.

## Superfície pública para a próxima feature (F03)

### `Community` — entidade e repositório

`pt.seerhub.community.domain.Community`
(`backend/src/main/java/pt/seerhub/community/domain/Community.java`) —
mapeada contra `communities` (`V2`), `ddl-auto: validate`. Getters
públicos: `getId()`, `getOwner()` (`User`, `@ManyToOne(LAZY)`),
`getName()`, `getSlug()`, `getDescription()`, `getAvatarUrl()`,
`getBannerUrl()`, `getPriceMonthlyCents()` (`int`), `getStatus()`
(`CommunityStatus`), `getCreatedAt()`. Setters: `setName`,
`setDescription`, `setAvatarUrl`, `setBannerUrl`, `setPriceMonthlyCents`,
`setStatus`. Constante `Community.MOEDA = "EUR"` — **a coluna `currency`
não está mapeada** (D-2 do plano: `CHAR(3)` não é mapeável por `String`
com `ddl-auto: validate`); qualquer leitura de moeda deve vir desta
constante ou de `JdbcTemplate` direto, nunca de um getter. **Sem
`@Version`** (a tabela não tem essa coluna) e **sem nenhum `@OneToMany`**
para memberships — gravar uma comunidade nunca escreve em
`community_memberships`.

`pt.seerhub.community.repo.CommunityRepository extends JpaRepository<Community, Long>`
(`backend/src/main/java/pt/seerhub/community/repo/CommunityRepository.java`):
`findBySlug(String)`, `existsBySlug(String)`,
`countByOwnerIdAndStatus(Long, CommunityStatus)`,
`findTop100ByStatusOrderByCreatedAtDesc(CommunityStatus)`,
`findByOwnerIdOrderByCreatedAtDesc(Long)`. F03 pode usar `findById`/`findBySlug`
livremente para resolver a comunidade de uma subscrição; **não adivinhar
o `owner_id`** — vem sempre de `community.getOwner().getId()`.

`pt.seerhub.community.domain.CommunityStatus` — `{ ACTIVE, SUSPENDED }`,
`@Enumerated(STRING)`.

### `CommunityMembership` — entidade e repositório, e a fronteira F02/F03

**Ler esta secção antes de tocar em `community_memberships` a partir de
F03 — é a decisão mais importante que este handoff transporta.**

`pt.seerhub.community.domain.CommunityMembership`
(`backend/src/main/java/pt/seerhub/community/domain/CommunityMembership.java`)
— mapeada contra `community_memberships` (`V2`). Getters públicos:
`getId()`, `getCommunity()`, `getUser()`, `getRole()` (`MembershipRole`),
`getStatus()` (`MembershipStatus`), `getJoinedAt()`, `getExpiresAt()`
(nulável), `getVersion()` (`Long`, mapeado com `@Version`). **O único
construtor público é a fábrica estática `CommunityMembership.deDono(Community, User, Clock)`**,
que fixa `role=OWNER`, `status=ACTIVE`, `expiresAt=null` — não existe
nenhum outro construtor público, deliberadamente, para que nada em F02
consiga criar por acidente uma linha `MEMBER`/`MODERATOR`.

`pt.seerhub.community.repo.CommunityMembershipRepository extends JpaRepository<CommunityMembership, Long>`
(`backend/src/main/java/pt/seerhub/community/repo/CommunityMembershipRepository.java`)
só declara `existsByCommunityIdAndUserId(Long, Long)`. Nenhum outro
método de leitura ou escrita foi acrescentado.

**Exatamente o que F02 escreveu em `community_memberships` (e nada mais):**
- Um único `INSERT`, dentro de `CommunityService.criar(...)`, na mesma
  transação que a comunidade: a linha do dono, `role='OWNER'`,
  `status='ACTIVE'`, `joined_at=now(clock)`, `expires_at=NULL`,
  `version=0`.
- Uma leitura de existência, `existsByCommunityIdAndUserId(...)`, usada
  em `CommunityService.obterParaLeitura` como a porta que decide se uma
  comunidade suspensa pode ser lida por quem pede (qualquer `role`,
  qualquer `status` contam para esta verificação — R3 é livre de restringir
  isto por `status='ACTIVE'`/`expires_at` se quiser uma porta mais fina de
  conteúdo premium; F02 não o fez porque isso é território de R3, não de
  R2).

**O que F02 nunca fez, deliberadamente, e fica inteiramente livre para F03/F04:**
- Nenhuma linha `role='MEMBER'` ou `role='MODERATOR'` foi criada por
  código de produção. Nos testes, essas linhas são inseridas por SQL cru
  em `CommunityTestSupport.inserirMembership(...)` — não existe nenhum
  endpoint de subscrição nem de nomeação de moderador.
- Nenhum `UPDATE` foi feito a nenhuma linha de `community_memberships`,
  nem sequer à linha `OWNER` que o próprio F02 criou. Não há transição de
  `status` (`ACTIVE→CANCELLED`/`EXPIRED`) em nenhum caminho de código de
  F02.
- Nenhuma escrita em `expires_at` fora do `NULL` fixo do dono. A tarefa
  diária de expiração (R3, critério 3) nunca foi tocada.
- `version` de qualquer linha que F02 não criou fica sempre em `0` —
  prova explícita no teste `CommunityEditIT.alterarOPrecoNaoMexeEmNenhumaMembershipJaAtiva`
  (critério 4), que faz uma fotografia da linha antes e depois de uma
  edição de preço e compara coluna a coluna, incluindo `version`.

`pt.seerhub.community.domain.MembershipRole` — `{ OWNER, MODERATOR, MEMBER }`.
`pt.seerhub.community.domain.MembershipStatus` — `{ ACTIVE, CANCELLED, EXPIRED }`.
Ambos os enums já cobrem os três valores do `CHECK` do baseline; F02 só
usa `OWNER`/`ACTIVE`. F03 pode usar os mesmos enums sem os alterar.

### Verificar se um utilizador é dono de uma comunidade

Não existe abstração de permissões (nenhum `PermissionEvaluator` — isso é
F04). O padrão usado em toda a F02, e que F03/F04 devem repetir até
F04 chegar:

```java
if (!community.getOwner().getId().equals(autenticado.id())) {
    throw new ApiException(HttpStatus.FORBIDDEN, "...");
}
```

Isto está encapsulado em `CommunityService.exigirDono(Community, Long)`
(`backend/src/main/java/pt/seerhub/community/service/CommunityService.java`),
público, reutilizável por outras features do pacote `community` sem
duplicar a comparação.

### `CommunityAccessRules` — o ponto de contacto que F03 consome

`pt.seerhub.community.service.CommunityAccessRules`
(`backend/src/main/java/pt/seerhub/community/service/CommunityAccessRules.java`)
— classe final, funções estáticas puras, sem repositórios nem Spring:

```java
public static boolean aceitaNovasSubscricoes(Community comunidade); // true sse status == ACTIVE
public static void exigirQueAceitaNovasSubscricoes(Community comunidade); // ApiException(409, MENSAGEM_COMUNIDADE_SUSPENSA)
public static boolean podeSerLidaPor(Community comunidade, boolean temMembership);
public static final String MENSAGEM_COMUNIDADE_SUSPENSA = "Esta comunidade está suspensa.";
```

F03 chama `exigirQueAceitaNovasSubscricoes(comunidade)` logo no início do
seu caminho de subscrição, depois de resolver a `Community` por slug/id, e
deixa a `ApiException` propagar — o `ApiExceptionHandler` já existente
converte-a em `409` com a mensagem literal do §6.2 da spec. Testado em
`CommunityAccessRulesTest` (`comunidadeSuspensaRecusaNovasSubscricoes`,
`comunidadeAtivaAceitaNovasSubscricoes`), sem contexto Spring.

### Endpoints REST (D-6 do plano)

| Método | Caminho | Acesso | Notas |
| --- | --- | --- | --- |
| `POST` | `/api/communities` | autenticado | `201` + `Location` + `CommunityResponse` |
| `GET` | `/api/communities` | público | só `ACTIVE`, top 100 por `createdAt DESC` |
| `GET` | `/api/communities/{slug}` | público | `200` ou `404`; `404` idêntico para inexistente e suspensa-sem-membership |
| `PUT` | `/api/communities/{slug}` | autenticado + dono | `404` antes de `403` |
| `GET` | `/api/me/communities` | autenticado | comunidades de que o utilizador é **dono** (inclui suspensas) |

`pt.seerhub.community.api.CommunityResponse` — `record` com `id, slug,
name, description, avatarUrl, bannerUrl, priceMonthlyCents, currency,
status, ownerId, ownerDisplayName, createdAt, ownedByViewer`, fábrica
`CommunityResponse.de(Community, Long viewerIdOuNull)`. **`ownedByViewer`
não é o "papel efetivo" de R4** (D-14) — é só para a UI decidir se mostra
"Definições". F04 acrescenta `papelEfetivo` a este mesmo `record` sem
remover `ownedByViewer`.

`GET /api/me/subscriptions` (as comunidades **subscritas**, não as
possuídas) é território de F03 — não existe ainda, e não deve reaproveitar
`/api/me/communities`.

### Frontend

- `frontend/src/lib/communities.ts`: tipo `Comunidade` (espelha
  `CommunityResponse`), `criarComunidade`, `listarMinhasComunidades`,
  `obterComunidade(slug)`, `guardarComunidade(slug, dados)`, e a
  conversão `eurosParaCentimos(valor: string): number` /
  `centimosParaEuros(cents: number): string` — aceita tanto `,` como `.`
  como separador decimal (normaliza `,`→`.` antes de `Number(...)`).
- `frontend/src/pages/{MyCommunitiesPage,CreateCommunityPage,CommunitySettingsPage}.tsx`
  — rotas `/comunidades`, `/comunidades/nova`, `/comunidades/:slug/definicoes`,
  todas em `App.tsx` dentro de `<RequireAuth>`. Os campos de preço usam
  `type="text" inputMode="decimal"` (não `type="number"`) precisamente
  para aceitar o separador decimal `,` do PT-PT — um `<input type="number">`
  rejeita silenciosamente qualquer valor com vírgula, em qualquer
  navegador, e a conversão fica sem efeito (descoberto ao escrever o
  teste FE4).

## Ficheiros criados

**Backend — produção (13):**
`community/domain/{Community,CommunityStatus,CommunityMembership,MembershipRole,MembershipStatus}.java`,
`community/repo/{CommunityRepository,CommunityMembershipRepository}.java`,
`community/service/{SlugGenerator,CommunityAccessRules,CommunityService}.java`,
`community/api/{CommunityController,CreateCommunityRequest,UpdateCommunityRequest,CommunityResponse}.java`.

**Backend — testes (6):** `support/CommunityTestSupport.java`,
`community/SlugGeneratorTest.java`, `community/CommunityAccessRulesTest.java`,
`community/CommunityCreationIT.java`, `community/CommunityEditIT.java`,
`community/CommunityVisibilityIT.java`.

**Frontend (7):** `lib/communities.ts`, `pages/MyCommunitiesPage.tsx` +
`.test.tsx`, `pages/CreateCommunityPage.tsx` + `.test.tsx`,
`pages/CommunitySettingsPage.tsx` + `.test.tsx`.

## Ficheiros editados

| Caminho | Alteração |
| --- | --- |
| `backend/src/main/java/pt/seerhub/config/SecurityConfig.java` | Uma linha: `.requestMatchers(HttpMethod.GET, "/api/communities", "/api/communities/*").permitAll()`, inserida antes de `/api/admin/**`. Nada mais tocado. |
| `backend/src/main/java/pt/seerhub/common/error/ApiExceptionHandler.java` | Um `@ExceptionHandler(HttpMessageNotReadableException.class)` novo → `400` com a constante nova `MENSAGEM_PEDIDO_MAL_FORMADO`. Os três handlers de F00/F01 ficam literalmente como estavam. |
| `backend/src/main/resources/application.yml` | Acrescentado `spring.jackson.deserialization.accept-float-as-int: false`, irmão de `default-property-inclusion`. |
| `frontend/src/App.tsx` | Três `<Route>` novas dentro de `<RequireAuth>`: `/comunidades`, `/comunidades/nova`, `/comunidades/:slug/definicoes`. Rota `/` inalterada, sem cabeçalho/navegação global. |

## Testes

Backend: **84 testes JUnit** (50 de F00+F01 intactos + 34 novos), todos
passados, suite corrida duas vezes seguidas (armadilha do contentor
partilhado — nenhum teste depende de ordem de execução).

| Critério(s) | Teste | Resultado |
| --- | --- | --- |
| 1c1–1c4 | `SlugGeneratorTest` (4 testes) | passou |
| 6c, 6d | `CommunityAccessRulesTest` (2 testes) | passou |
| 1a, 1b, 1d, 1e, 1f, 1g, 2a, 2b, 5a, 5b, X4 | `CommunityCreationIT` (11 testes) | passou |
| 3a–3h, 4a, X5, X6 | `CommunityEditIT` (11 testes) | passou |
| 6a, 6b, 6e, X1, X2, X3 | `CommunityVisibilityIT` (6 testes) | passou |

Frontend: **17 testes Vitest** (12 de F00+F01 intactos + 5 novos): FE1/FE2
em `CreateCommunityPage.test.tsx`, FE3/FE4 em `CommunitySettingsPage.test.tsx`,
FE5 em `MyCommunitiesPage.test.tsx` — todos passados. `npm run typecheck`
e `npm run build` limpos.

### Verificação manual

`docker compose --env-file .env.example up -d --build` (portas
remapeadas `BACKEND_PORT=18080`/`FRONTEND_PORT=15173` por conflito local
com outro projeto — nunca editei `.env.example`): os três serviços
chegaram a `healthy`. Confirmado por `curl`:
- Registo → `201`; criar comunidade → `201` com `slug`; repetir o mesmo
  nome → `201` com `slug-2`.
- `GET /api/communities` sem `Authorization` → `200` com a lista completa
  (`ownedByViewer: false` para um visitante anónimo).
- `PUT /api/communities/{slug}` com `"priceMonthlyCents": 12.50` → `400`
  com `"O corpo do pedido é inválido."`, não `500` nem truncado.
- 3ª comunidade → `201`; 4ª comunidade do mesmo dono → `409` com
  `"Atingiu o limite de 3 comunidades ativas. ..."`.
`docker compose down -v` limpou tudo.

## Desvios face ao plano

1. **`CommunitySettingsPage`/`CreateCommunityPage`: campo de preço em
   `<input type="text" inputMode="decimal">`, não `type="number"`.** O
   plano não fixa o tipo de input. Ao escrever o teste FE4
   (`guardarEnviaOPrecoConvertidoParaCentimosInteiros`, que exercita
   literalmente "9,99 € → 999"), descobri que um `<input type="number">`
   rejeita qualquer valor com vírgula como separador decimal — o browser
   (e o jsdom usado pelo Vitest) trata-o como inválido e reduz o valor a
   `""`, que `Number(...)` converte para `0`. Como o PT-PT usa vírgula
   como separador decimal e `eurosParaCentimos` já normaliza `,`→`.`
   antes de `Number(...)`, o campo tinha de aceitar o valor em bruto sem
   a validação nativa de `type="number"` o rejeitar primeiro. Nenhum
   critério de aceitação é afetado — o valor continua validado no
   backend (`@Min(0)`, `accept-float-as-int: false`).
2. **Teste `2b` (`comunidadeCriadaApareceEmAsMinhasComunidadesComoDono`)
   usa `jsonPath("$[0]...")` em vez do filtro `$[?(@.slug == '...')]`
   sugerido implicitamente pela ideia de "procurar pelo slug".** O filtro
   Jayway devolvia `null` em vez de uma lista de um elemento nesta versão
   da biblioteca (`spring-test`/`json-path` tal como vêm no
   `spring-boot-starter-test`). Como o utilizador do teste é criado de
   propósito para o teste e não tem nenhuma outra comunidade, `$[0]` é
   equivalente em precisão e determinístico. Nenhuma perda de cobertura.

Nenhum desvio contraria a spec nem o §2.1 do plano.

## Dívidas deixadas

Herdadas do plano/de F01 (nenhuma resolvida nem agravada por F02):

1. **`requestMatchers("/__test__/**").permitAll()` em `SecurityConfig`**
   — ainda presente, ainda necessária: sem ela, o controlador de teste
   aninhado de `ApiExceptionHandlerIT` (F00) é apanhado pelo
   component-scan de qualquer contexto `*IT` e o teste fica vermelho.
   **F02 confirma, deliberadamente, que não a resolve** (removê-la agora
   partiria um dos 62 testes da baseline, critério de falha desta
   feature). Dono nomeado: **F15** (operação). Correção mais barata já
   identificada por F01: mover o controlador de teste para um pacote
   fora de `pt.seerhub`, ou registá-lo num `SecurityFilterChain` só do
   perfil `test`, e só então remover o matcher.
2. **Upload de avatar/banner adiado** (D-11 do plano) — F02 só aceita
   URLs `http(s)://` até 500 caracteres para `avatarUrl`/`bannerUrl`; não
   há endpoint multipart, nem `seerhub.uploads.dir` a ser servido. Dono
   por omissão, registado no plano: **F15**; F10 pode reclamá-lo antes se
   a página de descoberta precisar de imagens carregadas mais cedo. O
   campo e a validação de URL já estão prontos — o único trabalho novo é
   devolver um URL do próprio serviço em vez de um externo.
3. **`GET /api/communities` é `findTop100...`, sem paginação, ordenação
   por relevância nem pesquisa.** Suficiente para provar "sai da
   listagem quando suspensa" (o que F02 precisa); dívida com dono **F10**,
   que substitui o endpoint mantendo o filtro de estado.
4. **`npm audit`** — nenhuma dependência nova acrescentada por F02;
   as vulnerabilidades já registadas por F00 (7, incluindo 1 crítica, em
   dependências de build) continuam por investigar, sem agravamento.

Novas, introduzidas por F02:

5. **Nenhuma.** F02 não acrescentou nenhuma dependência, nenhuma
   variável de ambiente, nenhuma migração.

## Confirmação sobre `API_KEY` → `API_FOOTBALL_KEY`

**Ainda pendente, por confirmar pelo utilizador** — a mesma dívida
registada por F00 e reconfirmada por F01. F02 nunca leu nem editou o
`.env` real (regra absoluta do plano) e não chama a API-Football em
nenhum caminho, por isso a rename continua a não bloquear nada até F05.
Continua a ser uma ação do utilizador, obrigatória antes de F05 arrancar.

## Avisos para quem vier a seguir

- **Ler a secção "`CommunityMembership` — entidade e repositório, e a
  fronteira F02/F03" acima antes de escrever qualquer linha de F03 que
  toque em `community_memberships`.** É a decisão estrutural mais
  importante deste handoff: F02 só insere a linha `OWNER` e só lê
  existência; toda a criação de linhas `MEMBER`/`MODERATOR`, toda a
  transição de `status` e todo o uso de `expires_at` (incluindo a tarefa
  diária de expiração, R3 critério 3) pertencem inteiramente a F03/F04.
- **`Community` não mapeia `currency`** (D-2) — nunca tentar adicionar o
  campo por hábito; usar `Community.MOEDA` ou ler a coluna por
  `JdbcTemplate` em teste. Se um dia a v1 deixar de ser EUR-only, a
  correção é trocar a coluna para `VARCHAR(3)` numa migração nova e só
  então mapeá-la — nunca `columnDefinition` (já provado, duas vezes
  agora, que não resolve com `ddl-auto: validate`).
- **`slug` é imutável depois da criação** (D-5) — renomear uma comunidade
  nunca regenera o `slug`. Qualquer feature que precise de mudar o slug
  é uma decisão nova, não uma correção.
- **`CommunityAccessRules.podeSerLidaPor` conta qualquer `role` e
  qualquer `status` de membership como acesso de leitura a uma
  comunidade suspensa.** Se F03 precisar de uma porta mais fina — por
  exemplo, recusar leitura de conteúdo premium a uma membership já
  `EXPIRED` — isso é uma regra nova em cima desta, não uma alteração a
  esta função (que continua a servir "é membro, ponto final" para a
  finalidade de F02: mostrar a ficha da comunidade).
- **O contentor Postgres é partilhado e nunca limpo entre classes** —
  todos os nomes de comunidade em teste devem vir de
  `CommunityTestSupport.nomeUnico(prefixo)` (garante ≤60 caracteres);
  `slug` é `UNIQUE` global.
- **`accept-float-as-int: false` é global** (D-12) — torna estrito todo o
  corpo JSON de entrada da aplicação, não só o de comunidades. Campos
  `BigDecimal`/`NUMERIC` de features futuras (odds, stakes) continuam a
  funcionar normalmente; a flag só proíbe *coagir* um decimal para um
  campo inteiro.
