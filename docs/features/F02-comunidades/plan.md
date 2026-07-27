# F02 — Criação e gestão de comunidades

**Requisitos:** R2
**Depende de:** F00 (fundações), F01 (contas e autenticação)
**Planeado:** 2026-07-27 · Opus 5

## 1. Objetivo

Depois desta feature, um utilizador autenticado cria a sua própria comunidade num único
pedido, sem aprovação: escolhe um nome entre 3 e 60 caracteres, o sistema deriva um `slug`
único (com sufixo numérico quando o slug base já está tomado), grava a comunidade em
`ACTIVE` e, na mesma transação, cria a linha `community_memberships` que o torna `OWNER`
com membership ativa e sem data de expiração. O dono passa a poder editar nome, descrição,
avatar, banner e preço mensal — o preço sempre em cêntimos inteiros, validado contra
negativos e contra valores fracionários. Ninguém consegue ter mais de três comunidades
ativas em simultâneo. Uma comunidade suspensa desaparece da listagem pública e deixa de
ser alcançável por quem não é membro, enquanto quem já lá está continua a lê-la
normalmente; e existe um ponto de verificação público, `CommunityAccessRules
.exigirQueAceitaNovasSubscricoes(...)`, que F03 invoca para recusar subscrições novas.
No frontend existem três ecrãs novos — `/comunidades` (as minhas), `/comunidades/nova`
(criação) e `/comunidades/:slug/definicoes` (edição pelo dono) — todos protegidos por
`RequireAuth`.

## 2. Contexto herdado

Lido antes de planear: `docs/specs/seerhub.md` (R2, §5, §6.2, §8, §11), o handoff de F01
(`docs/features/F01-contas-autenticacao/handoff.md`, integralmente), o handoff de F00
(`docs/features/F00-fundacoes/handoff.md`), o `CLAUDE.md` da raiz e o código real listado
abaixo.

### O que já existe e F02 usa

| Superfície | Caminho | Como F02 a usa |
| --- | --- | --- |
| Tabela `communities` | `backend/src/main/resources/db/migration/V2__baseline_schema.sql` (linhas 22–40) | Entidade `Community` mapeada contra ela, `ddl-auto: validate`. **Sem migração nova.** |
| Tabela `community_memberships` | mesmo ficheiro (linhas 42–57) | Entidade `CommunityMembership`; F02 só insere a linha `OWNER` (ver §2.1). |
| `AuthenticatedUser` | `backend/src/main/java/pt/seerhub/user/security/AuthenticatedUser.java` | `@AuthenticationPrincipal AuthenticatedUser autenticado` → `autenticado.id()` para dono/membership. |
| `UserRepository` | `backend/src/main/java/pt/seerhub/user/repo/UserRepository.java` | `findById(autenticado.id())` para resolver o `owner_id`. |
| `User` | `backend/src/main/java/pt/seerhub/user/domain/User.java` | Alvo do `@ManyToOne` de `Community.owner`. |
| `SecurityConfig` | `backend/src/main/java/pt/seerhub/config/SecurityConfig.java` | `anyRequest().authenticated()` já protege tudo o que F02 cria; F02 só **acrescenta** dois `requestMatchers` públicos para os dois `GET` de leitura. |
| `ApiException` / `ApiExceptionHandler` | `backend/src/main/java/pt/seerhub/common/error/` | Todo o erro de negócio de F02 é um `ApiException(status, mensagem em pt-PT)`. |
| `ClockConfig` | `backend/src/main/java/pt/seerhub/config/ClockConfig.java` | Bean `Clock` injetado no serviço e passado às entidades para preencher `created_at`/`joined_at` (mesmo padrão de `User`). |
| `AbstractIntegrationTest` | `backend/src/test/java/pt/seerhub/support/AbstractIntegrationTest.java` | Base de todos os `*IT` de F02. Contentor Postgres partilhado, **nunca limpo entre classes**. |
| `AuthTestSupport` | `backend/src/test/java/pt/seerhub/support/AuthTestSupport.java` | `registarEAutenticar`, `login`, `emailUnico`. **Reutilizar; não reinventar autenticação em teste.** |
| `apiFetch` / sessão | `frontend/src/lib/api.ts` | Anexa `Authorization: Bearer` e renova em 401; F02 chama-o para todos os pedidos. |
| `useAuth` / `AuthProvider` | `frontend/src/lib/auth.tsx` | Estado de sessão; `App.tsx` já está envolvida. |
| `RequireAuth` | `frontend/src/components/RequireAuth.tsx` | Envolve as três rotas novas de F02. |

### 2.1 Fronteira F02 / F03 sobre `community_memberships`

Esta é a decisão mais importante do plano, porque a tabela é partilhada e F03 ainda não
existe.

**F02 é dono de:**
- A **entidade JPA** `CommunityMembership` e o repositório `CommunityMembershipRepository`
  (uma tabela → uma entidade; quem escreve primeiro mapeia-a). Ficam em
  `pt.seerhub.community.domain` / `pt.seerhub.community.repo`.
- A **inserção de exatamente uma linha por comunidade**, no momento da criação, com
  `role = OWNER`, `status = ACTIVE`, `joined_at = Instant.now(clock)`,
  `expires_at = NULL` (o dono nunca expira) e `version = 0`.
- Duas **leituras**: `existsByCommunityIdAndUserId(...)` (usada como porta de leitura de
  uma comunidade suspensa) e `countByCommunityIdAndStatus(...)` se for preciso um número
  de membros na resposta — nada mais.

**F02 tem de deixar em paz (é território de F03/F04):**
- Criar linhas com `role = MEMBER` ou `role = MODERATOR`. F02 **não** tem endpoint de
  subscrição nem de nomeação de moderador. Nos testes, as linhas `MEMBER` são inseridas
  por SQL direto em `CommunityTestSupport`, deliberadamente, para F02 não inventar a API
  de F03.
- Qualquer **escrita** em `status` de uma linha já existente (`ACTIVE → CANCELLED`,
  `→ EXPIRED`): não há transições de estado em F02.
- Qualquer escrita em **`expires_at`** fora do `NULL` do dono, e a tarefa diária de
  expiração (R3, critério 3).
- A semântica de `@Version`: F02 mapeia a coluna com `@Version` (ela existe no baseline
  e serve o bloqueio otimista de F03/F08), mas **nunca faz `UPDATE` a uma linha de
  membership** — logo, a `version` de qualquer linha que F02 não criou fica sempre em 0.
  É exatamente isto que o teste do critério 4 explora como prova.
- `Community` **não** tem `@OneToMany` para memberships. Não há cascata nenhuma entre as
  duas entidades: guardar uma comunidade nunca pode escrever numa membership.

**Numa frase:** *F02 insere e lê exclusivamente a linha `OWNER` da comunidade que cria
(`status=ACTIVE`, `expires_at=NULL`) e nunca escreve noutra linha de
`community_memberships` — toda a criação de linhas `MEMBER`/`MODERATOR`, toda a transição
de `status` e todo o uso de `expires_at` pertencem a F03/F04.*

### 2.2 Dívidas herdadas que afetam F02

1. **`requestMatchers("/__test__/**").permitAll()` em `SecurityConfig`** (dívida 8 do
   handoff de F01). **F02 adia, deliberadamente, e não a resolve.** Motivo: essa linha
   existe só porque o controlador aninhado de `ApiExceptionHandlerIT` (F00, ficheiro na
   lista de "não tocar") é apanhado pelo component-scan de qualquer contexto `*IT`;
   removê-la torna vermelho um dos 62 testes da baseline, o que é critério de falha desta
   feature. Fica **explicitamente reencaminhada**, com dono nomeado — **F15** (operação) —
   e com a correção mais barata já identificada: mover o controlador de teste para um
   pacote fora de `pt.seerhub` ou registá-lo num `SecurityFilterChain` só do perfil `test`,
   e só então remover o matcher. F02 regista isto de novo no seu handoff, para não se
   perder.
2. **Não existe `PermissionEvaluator` por comunidade** (F04). F02 implementa a sua
   autorização com consultas diretas (`autenticado.id()` + `community.getOwner().getId()`),
   como o handoff de F01 manda, e não cria nenhuma abstração de permissões que F04 tenha
   de desfazer.
3. **O papel por comunidade nunca viaja no JWT** (D-4 de F01). F02 lê sempre da base de
   dados. Nenhum claim novo é acrescentado ao token.
4. **Coluna `CHAR(n)` não é mapeável por um campo `String` com `ddl-auto: validate`**
   (desvio 1 de F01). `communities.currency` é `CHAR(3)` — ver decisão D-2.
5. **`spring.jackson.default-property-inclusion: non_null`** (`application.yml`): campos
   nulos são **omitidos** do JSON. As asserções sobre `description`/`avatarUrl` ausentes
   têm de usar `jsonPath(...).doesNotExist()`, não `.value(null)`.
6. **O contentor Postgres é partilhado e nunca limpo.** `slug` é `UNIQUE` global — todos
   os nomes de comunidade em teste têm de vir de `CommunityTestSupport.nomeUnico(prefixo)`.

## 3. Critérios de aceitação → testes

Todas as linhas de R2 estão decompostas. Nenhum critério fica sem teste. Tipo `unit` =
`*Test` sem contexto Spring; `integração` = `*IT` que estende `AbstractIntegrationTest`;
`frontend` = Vitest.

### R2, critério 1 — «Criar comunidade exige nome (3–60 caracteres) e gera um `slug` único; colisão acrescenta sufixo numérico»

| # | Critério | Ficheiro de teste | Nome do teste | Tipo |
| --- | --- | --- | --- | --- |
| 1a | Criar com nome válido devolve 201 e o `slug` derivado do nome | `CommunityCreationIT` | `criarComunidadeComNomeValidoDevolve201ComSlugDerivadoDoNome` | integração |
| 1b | Nome repetido gera `slug` com sufixo numérico incremental (`x`, `x-2`, `x-3`) e as três comunidades coexistem | `CommunityCreationIT` | `nomesRepetidosGeramSlugsComSufixoNumericoIncremental` | integração |
| 1c1 | A base do slug tira acentos, maiúsculas e pontuação | `SlugGeneratorTest` | `baseRemoveAcentosMaiusculasEPontuacao` | unit |
| 1c2 | Nome sem qualquer carácter alfanumérico cai para o slug por omissão | `SlugGeneratorTest` | `nomeSemCaracteresAlfanumericosCaiParaOSlugPorOmissao` | unit |
| 1c3 | Sem colisão, `gerarUnico` devolve a base intacta | `SlugGeneratorTest` | `gerarUnicoDevolveOSlugBaseQuandoNaoHaColisao` | unit |
| 1c4 | Com `x` e `x-2` já ocupados, `gerarUnico` devolve `x-3` | `SlugGeneratorTest` | `gerarUnicoAcrescentaSufixoNumericoAteEncontrarUmLivre` | unit |
| 1d | Nome com 2 caracteres devolve 400 e não persiste nada | `CommunityCreationIT` | `nomeComMenosDeTresCaracteresDevolve400ENaoPersiste` | integração |
| 1e | Nome com 61 caracteres devolve 400 e não persiste nada | `CommunityCreationIT` | `nomeComMaisDeSessentaCaracteresDevolve400ENaoPersiste` | integração |
| 1f | Criar sem token devolve 401 com `ProblemDetail` e `correlationId` | `CommunityCreationIT` | `criarComunidadeSemTokenDevolve401` | integração |
| 1g | Os limites 3 e 60 são inclusivos (ambos devolvem 201) | `CommunityCreationIT` | `nomesNosLimitesDeTresESessentaCaracteresSaoAceites` | integração |

### R2, critério 2 — «O criador fica automaticamente com papel `OWNER` e membership ativa»

| # | Critério | Ficheiro de teste | Nome do teste | Tipo |
| --- | --- | --- | --- | --- |
| 2a | A criação cria uma linha em `community_memberships` com `role='OWNER'`, `status='ACTIVE'`, `expires_at IS NULL`, `version=0`, e o par `(community_id, user_id)` correto — lido por `JdbcTemplate`, não pela API | `CommunityCreationIT` | `criadorFicaComMembershipOwnerAtivaSemDataDeExpiracao` | integração |
| 2b | A comunidade criada aparece em `GET /api/me/communities` com `ownedByViewer = true` | `CommunityCreationIT` | `comunidadeCriadaApareceEmAsMinhasComunidadesComoDono` | integração |

### R2, critério 3 — «O dono edita nome, descrição, avatar, banner e preço mensal; o preço é guardado em cêntimos inteiros»

| # | Critério | Ficheiro de teste | Nome do teste | Tipo |
| --- | --- | --- | --- | --- |
| 3a | O dono altera os cinco campos e a leitura seguinte devolve os valores novos | `CommunityEditIT` | `donoEditaNomeDescricaoAvatarBannerEPrecoEAsAlteracoesPersistem` | integração |
| 3b | O preço fica na coluna `price_monthly_cents` (INTEGER) como cêntimo inteiro, com `currency='EUR'` | `CommunityEditIT` | `precoEGuardadoEmCentimosInteirosNaColunaInteger` | integração |
| 3c | Preço negativo devolve 400 e o valor guardado não muda | `CommunityEditIT` | `precoNegativoDevolve400ENaoAlteraOValorGuardado` | integração |
| 3d | Preço fracionário (`12.50`) devolve 400 e **não é truncado** para 12 | `CommunityEditIT` | `precoNaoInteiroDevolve400ENaoETruncado` | integração |
| 3e | Utilizador que não é dono a editar devolve 403 e nada muda | `CommunityEditIT` | `utilizadorQueNaoEDonoAEditarDevolve403ENaoAlteraNada` | integração |
| 3f | Editar sem token devolve 401 | `CommunityEditIT` | `editarSemTokenDevolve401` | integração |
| 3g | Editar comunidade inexistente devolve 404 com «Comunidade não encontrada.» | `CommunityEditIT` | `editarComunidadeInexistenteDevolve404` | integração |
| 3h | Renomear não altera o `slug` (as ligações mantêm-se estáveis) | `CommunityEditIT` | `renomearNaoAlteraOSlug` | integração |

### R2, critério 4 — «Alterar o preço não afeta subscrições já ativas»

| # | Critério | Ficheiro de teste | Nome do teste | Tipo |
| --- | --- | --- | --- | --- |
| 4a | Alterar o preço não toca em nenhuma linha de membership já ativa | `CommunityEditIT` | `alterarOPrecoNaoMexeEmNenhumaMembershipJaAtiva` | integração |

**Asserções obrigatórias de 4a (o teste é inválido se se limitar a `status().isOk()`):**

1. **Arranjo.** Dono cria a comunidade com `priceMonthlyCents = 500`. Um segundo
   utilizador (`AuthTestSupport.registarEAutenticar`) recebe uma membership inserida por
   SQL direto (`CommunityTestSupport.inserirMembership`) com `role='MEMBER'`,
   `status='ACTIVE'`, `joined_at = now()`, `expires_at = now() + 30 dias`.
2. **Fotografia.** `Map<String,Object> antes = jdbcTemplate.queryForMap("SELECT id,
   community_id, user_id, role, status, joined_at, expires_at, version FROM
   community_memberships WHERE community_id = ? AND user_id = ?", ...)`.
3. **Ação.** O dono faz `PUT /api/communities/{slug}` com `priceMonthlyCents = 5000`.
4. **Prova de que a ação não foi vazia.** `SELECT price_monthly_cents FROM communities
   WHERE slug = ?` é agora `5000` (senão o teste passaria por o endpoint não fazer nada).
5. **Asserção central.** `Map<String,Object> depois = <mesma consulta>` e
   `assertThat(depois).isEqualTo(antes)` — **coluna a coluna, incluindo `version`**. A
   `version` inalterada é a prova forte: se qualquer `UPDATE` tivesse tocado na linha, o
   bloqueio otimista tê-la-ia incrementado. `expires_at` e `joined_at` comparados como
   valores exatos, não «aproximadamente».
6. **Asserção de contagem.** `SELECT count(*) FROM community_memberships WHERE
   community_id = ?` é o mesmo antes e depois (2: o dono e o membro) — nada foi criado
   nem apagado.
7. **Asserção de acesso.** O membro continua a receber `200` em
   `GET /api/communities/{slug}` depois da alteração de preço.

Nota de desenho, para o implementador não inventar esquema: na v1 **não há gateway de
pagamento** (§4 da spec) e a tabela `community_memberships` do baseline **não tem coluna
de preço**. «Não afetar subscrições ativas» significa, portanto, que os termos gravados da
membership (`role`, `status`, `joined_at`, `expires_at`) e o acesso que dela decorre ficam
byte a byte intactos. **Não acrescentar nenhuma coluna `price_cents_at_subscription`** —
isso seria uma migração e uma decisão de modelo que não pertence a F02 (nem sequer à v1).

### R2, critério 5 — «Um utilizador não pode ter mais de 3 comunidades ativas na v1»

| # | Critério | Ficheiro de teste | Nome do teste | Tipo |
| --- | --- | --- | --- | --- |
| 5a | A quarta comunidade ativa devolve 409 com `MENSAGEM_LIMITE_ATINGIDO` e não persiste comunidade nem membership | `CommunityCreationIT` | `criarUmaQuartaComunidadeAtivaDevolve409ENaoPersisteNada` | integração |
| 5b | Com uma das três suspensa, criar outra é permitido — só contam as ativas | `CommunityCreationIT` | `comunidadeSuspensaNaoContaParaOLimiteDeTresAtivas` | integração |

### R2, critério 6 — «Comunidade suspensa deixa de aparecer no Hub e recusa novas subscrições, mas os membros existentes mantêm acesso de leitura»

| # | Critério | Ficheiro de teste | Nome do teste | Tipo |
| --- | --- | --- | --- | --- |
| 6a | Suspender tira a comunidade da listagem pública (presente antes, ausente depois — asserção sobre o próprio slug) | `CommunityVisibilityIT` | `comunidadeSuspensaDesapareceDaListagemPublica` | integração |
| 6b | **As duas metades no mesmo teste, sobre a mesma comunidade suspensa:** o membro existente continua a lê-la, e quem não é membro não a alcança | `CommunityVisibilityIT` | `comunidadeSuspensaMantemLeituraAoMembroERecusaQuemNaoEMembro` | integração |
| 6c | O ponto de verificação que F03 vai chamar recusa subscrições numa comunidade suspensa | `CommunityAccessRulesTest` | `comunidadeSuspensaRecusaNovasSubscricoes` | unit |
| 6d | O mesmo ponto de verificação deixa passar uma comunidade ativa (controlo) | `CommunityAccessRulesTest` | `comunidadeAtivaAceitaNovasSubscricoes` | unit |
| 6e | O dono continua a ver e a poder editar a sua comunidade suspensa | `CommunityVisibilityIT` | `donoContinuaAVerEAEditarAComunidadeSuspensa` | integração |

**Asserções obrigatórias de 6b (as duas metades, ambas exigidas):**

*Metade «mantém acesso de leitura»* — o utilizador com linha em `community_memberships`
para aquela comunidade faz `GET /api/communities/{slug}` e recebe **200** com o payload
**completo**: `jsonPath("$.name")`, `$.description`, `$.priceMonthlyCents`, `$.slug` e
`$.status` = `"SUSPENDED"` todos presentes e com os valores gravados. Não basta assertar
o código 200 — o conteúdo tem de estar lá, senão «acesso de leitura» não está provado.

*Metade «recusa novas subscrições»* — sobre **a mesma comunidade**, no mesmo teste:
(i) um segundo utilizador autenticado **sem** linha de membership recebe **404** com
`detail` = `CommunityService.MENSAGEM_COMUNIDADE_NAO_ENCONTRADA`; (ii) um pedido
**anónimo** ao mesmo `slug` recebe também **404**; (iii) o `slug` **não** consta do corpo
de `GET /api/communities`. Quem não é membro não tem sequer por onde iniciar uma
subscrição. A recusa formal do ato de subscrever — a exceção que F03 vai propagar — está
em 6c.

### Endurecimento adicional (não são critérios de R2, mas fecham caminhos de falha)

| # | O que cobre | Ficheiro de teste | Nome do teste | Tipo |
| --- | --- | --- | --- | --- |
| X1 | `GET /api/communities/{slug}` com slug inexistente devolve 404 | `CommunityVisibilityIT` | `slugInexistenteDevolve404` | integração |
| X2 | A listagem pública é acessível sem autenticação (visitante do §5) | `CommunityVisibilityIT` | `listagemPublicaEAcessivelSemAutenticacao` | integração |
| X3 | `GET /api/me/communities` sem token devolve 401 | `CommunityVisibilityIT` | `asMinhasComunidadesSemTokenDevolve401` | integração |
| X4 | `currency` fica `'EUR'` na base de dados apesar de não estar mapeada na entidade (prova de D-2) | `CommunityCreationIT` | `moedaFicaEurNaBaseDeDadosSemEstarMapeadaNaEntidade` | integração |
| X5 | Descrição acima de 2000 caracteres devolve 400 (não estoura na coluna) | `CommunityEditIT` | `descricaoAcimaDoLimiteDevolve400` | integração |
| X6 | Avatar/banner que não seja URL `http`/`https` devolve 400 | `CommunityEditIT` | `avatarOuBannerQueNaoSejaUrlHttpDevolve400` | integração |

### Frontend

| # | O que cobre | Ficheiro de teste | Nome do teste | Tipo |
| --- | --- | --- | --- | --- |
| FE1 | Criar comunidade com nome válido chama `POST /api/communities` e navega para as definições | `CreateCommunityPage.test.tsx` | `criarComunidadeComNomeValidoNavegaParaAsDefinicoes` | frontend |
| FE2 | O 409 do limite de 3 comunidades aparece ao utilizador com a mensagem do backend | `CreateCommunityPage.test.tsx` | `limiteDeComunidadesMostraAMensagemDevolvidaPeloBackend` | frontend |
| FE3 | O formulário de definições vem preenchido com os dados da comunidade | `CommunitySettingsPage.test.tsx` | `formularioDeDefinicoesVemPreenchidoComOsDadosDaComunidade` | frontend |
| FE4 | Guardar converte o preço em euros para cêntimos inteiros no corpo do pedido (`9,99 € → 999`) | `CommunitySettingsPage.test.tsx` | `guardarEnviaOPrecoConvertidoParaCentimosInteiros` | frontend |
| FE5 | A listagem mostra as comunidades de que sou dono com ligação para as definições | `MyCommunitiesPage.test.tsx` | `listaAsMinhasComunidadesComLigacaoParaAsDefinicoes` | frontend |

**Totais:** 28 linhas cobrem os 6 critérios de R2, mais 6 de endurecimento e 5 de
frontend = 39 linhas. Traduzem-se em **34 testes JUnit novos** (4 `SlugGeneratorTest` +
2 `CommunityAccessRulesTest` + 11 `CommunityCreationIT` + 11 `CommunityEditIT` +
6 `CommunityVisibilityIT`) e **5 testes Vitest novos**. Nenhum critério de R2 fica sem uma
linha.

## 4. Alterações

### Decisões que este plano fecha

**D-1 — Pacote.** `pt.seerhub.community`, com `api` / `domain` / `repo` / `service`,
conforme o mapa do `CLAUDE.md`.

**D-2 — `currency` não é mapeada na entidade.** A coluna é `CHAR(3) NOT NULL DEFAULT
'EUR'`. O desvio 1 de F01 provou que o Hibernate com `ddl-auto: validate` recusa mapear um
campo `String` contra `bpchar`, mesmo com `columnDefinition` explícito. Como a v1 é
EUR-only por pressuposto de §11 e nenhum critério de R2 depende de a moeda ser variável,
`Community` **não declara o campo**: o `INSERT` omite a coluna e o Postgres aplica o
`DEFAULT 'EUR'`. A resposta expõe `currency` a partir da constante pública
`Community.MOEDA = "EUR"`. O teste X4 lê a coluna por `JdbcTemplate` e confirma que fica
`'EUR'`, para a decisão ficar verificada em vez de assumida. Quando a plataforma tiver
mais do que uma moeda, a feature que a introduzir troca a coluna por `VARCHAR(3)` numa
migração nova e só então a mapeia.

**D-3 — Sem `@Version` em `Community`.** A tabela `communities` do baseline não tem coluna
`version` (ao contrário de `community_memberships`, `tips` e `tip_selections`). Não
acrescentar uma: seria uma migração fora do âmbito, e não há concorrência de escrita em
F02 (só o dono edita).

**D-4 — `PUT` e não `PATCH` para editar.** Um `PATCH` com campos anuláveis é ambíguo
(`null` significa «não mexer» ou «limpar»?) e cria um tri-estado impossível de testar sem
um tipo `Optional` no DTO. `PUT /api/communities/{slug}` recebe a representação completa:
`name` obrigatório (3–60), `priceMonthlyCents` obrigatório (≥ 0), e
`description`/`avatarUrl`/`bannerUrl` opcionais em que ausente ou `null` significa
**limpar**. Sem ambiguidade, sem ramos por testar.

**D-5 — O `slug` é imutável depois da criação.** Renomear não regenera o slug. R2 exige
que a criação gere um slug; nada exige que a edição o mude, e mudá-lo partiria ligações
partilhadas, o `Location` já entregue, e mais tarde as referências de F03 (subscrições) e
F10 (Hub). Coberto por 3h.

**D-6 — Endpoints.**

| Método | Caminho | Acesso | Resposta |
| --- | --- | --- | --- |
| `POST` | `/api/communities` | autenticado | `201` + `Location: /api/communities/{slug}` + `CommunityResponse` |
| `GET` | `/api/communities` | **público** | `200` + `List<CommunityResponse>`, só `ACTIVE`, `findTop100ByStatusOrderByCreatedAtDesc` |
| `GET` | `/api/communities/{slug}` | **público** | `200` + `CommunityResponse`, ou `404` |
| `PUT` | `/api/communities/{slug}` | autenticado + dono | `200` + `CommunityResponse` |
| `GET` | `/api/me/communities` | autenticado | `200` + `List<CommunityResponse>` das comunidades **de que é dono** (inclui as suspensas) |

O endpoint «as minhas» vive em `/api/me/communities`, **não** em `/api/communities/mine`,
para que nenhum `slug` legítimo (por exemplo, uma comunidade chamada «Mine») possa alguma
vez colidir com um segmento literal. Semântica fechada: devolve as comunidades de que o
utilizador é **dono**; F03 acrescenta `GET /api/me/subscriptions` para as subscritas, sem
tocar neste endpoint.

**D-7 — Regras de visibilidade de uma comunidade suspensa.**

- `GET /api/communities` → só `ACTIVE`. É a metade «deixa de aparecer no Hub» que F02
  consegue provar hoje; F10 substitui este endpoint por um com ordenação, pesquisa e
  métricas, mantendo o filtro de estado.
- `GET /api/communities/{slug}`, comunidade `ACTIVE` → `200` para qualquer pessoa,
  incluindo anónimos.
- `GET /api/communities/{slug}`, comunidade `SUSPENDED` → `200` **apenas** se o pedido
  vier autenticado e existir uma linha em `community_memberships` para
  `(comunidade, utilizador)` — qualquer `role`, qualquer `status`, o que inclui sempre o
  dono. Para todos os outros (anónimo ou autenticado sem linha) → `404` com
  `MENSAGEM_COMUNIDADE_NAO_ENCONTRADA`, exatamente a mesma resposta de um slug que não
  existe. Escolheu-se `404` e não `403`: «deixa de aparecer no Hub» implica invisibilidade,
  e um `403` com mensagem própria transformaria o endpoint num oráculo sobre comunidades
  suspensas (mesmo raciocínio do critério 3 de R1, já aplicado por F01 em `AuthService`).
  «Qualquer `status` de membership» é deliberado: a porta fina de conteúdo premium por
  membership expirada é de R3, não de R2.
- `PUT /api/communities/{slug}` numa comunidade `SUSPENDED`, feito pelo dono → **permitido**
  (`200`). A suspensão afeta descoberta e adesões novas; não confisca a gestão ao dono, e
  bloqueá-la criaria um ramo que R2 não pede. Coberto por 6e.

**D-8 — O ponto de contacto que F03 consome.**
`pt.seerhub.community.service.CommunityAccessRules`, classe final de funções puras sobre a
entidade (sem repositórios, sem Spring — por isso testável como `*Test`):

```java
public static boolean aceitaNovasSubscricoes(Community comunidade);
public static void exigirQueAceitaNovasSubscricoes(Community comunidade); // ApiException 409
public static boolean podeSerLidaPor(Community comunidade, boolean temMembership);
public static final String MENSAGEM_COMUNIDADE_SUSPENSA = "Esta comunidade está suspensa.";
```

F03 chama `exigirQueAceitaNovasSubscricoes(...)` no seu caminho de subscrição e propaga o
`ApiException` — a mensagem é exatamente a do §6.2 da spec. F02 não implementa mais nada
de subscrição.

**D-9 — Limite de 3.** `CommunityService.LIMITE_COMUNIDADES_ATIVAS = 3` (constante
pública). Verificado com `countByOwnerIdAndStatus(userId, ACTIVE)`; **as suspensas não
contam** (o critério diz «comunidades ativas»). Excedido → `409 CONFLICT` com
`MENSAGEM_LIMITE_ATINGIDO`. `409` e não `400`: é um conflito com o estado atual, não um
pedido malformado — a mesma escolha que F01 fez para o email duplicado.

**D-10 — Autorização de edição: só o dono.** Nem `MODERATOR` (R4 diz-lhe expressamente
que «não mexe em preço»), nem `ADMIN` global (as ferramentas de admin são R14/F14; dar-lhe
acesso agora seria pré-decidir o desenho de F14 sem log de auditoria). Ordem obrigatória
das verificações: **404 antes de 403** — uma comunidade inexistente devolve sempre 404,
mesmo a um não-dono, para não revelar existência.

**D-11 — Avatar e banner: só URLs em F02; upload adiado, com dono nomeado.**
F02 aceita `avatarUrl` e `bannerUrl` como strings `http(s)://…` até 500 caracteres, e não
implementa carregamento de ficheiros. Porquê: (i) o modelo de dados da §8 define os campos
como URLs e o critério de R2 é «o dono edita … avatar, banner», que fica satisfeito;
(ii) o upload é um subsistema inteiro — endpoint multipart, `seerhub.uploads.dir` (já
existe em `SeerHubProperties.Uploads`), rota de servir estáticos, passagem no nginx,
validação de MIME e tamanho, defesa contra travessia de caminhos, limpeza de órfãos e
verificação do volume Docker — sem um único critério de aceitação em R2, e introduziria
estado de sistema de ficheiros numa suite que hoje é só base de dados em Testcontainer;
(iii) as três metas de M1 (criar conta, criar comunidade com preço, subscrever, nomear
moderador) não dependem dele. **Dono do adiamento: F15** (a feature de operação/seed, que
já mexe em infraestrutura e dados de exemplo); se F10 (Hub) concluir que precisa de
imagens carregadas mais cedo para a página de descoberta, F10 pode reclamá-lo — mas o dono
por omissão, registado no handoff e a propor ao backlog, é F15. O que fica pronto para
quem o fizer: o campo já existe, a validação de URL já existe, e o único trabalho novo é
devolver um URL do próprio serviço em vez de um externo.

**D-12 — `HttpMessageNotReadableException` passa a devolver 400.** Hoje
`ApiExceptionHandler` não a trata e ela cai no `@ExceptionHandler(Exception.class)` → 500.
Sem isto, o critério 3d (preço fracionário) não pode ser satisfeito com um 400. F02
acrescenta um handler dedicado e, no `application.yml`, `spring.jackson.deserialization
.accept-float-as-int: false` — sem essa flag o Jackson **trunca silenciosamente** `12.50`
para `12` e gravava um preço errado sem nenhum erro. As duas alterações são aditivas e
globalmente mais seguras; nenhum DTO existente de F01 tem campos numéricos de entrada, e
`ApiExceptionHandlerIT` (F00) lança uma `RuntimeException` própria, pelo que continua a
receber 500.

**D-13 — Nomenclatura.** Campos de DTO e nomes JSON em inglês, espelhando o modelo de
dados da §8 (`name`, `slug`, `description`, `avatarUrl`, `bannerUrl`, `priceMonthlyCents`,
`currency`, `status`, `ownerId`, `ownerDisplayName`, `createdAt`, `ownedByViewer`);
métodos de serviço com verbos em português, como em F01 (`criar`, `editar`,
`listarAtivas`, `exigirDono`). O booleano chama-se `ownedByViewer` (e não `isOwner`) para
evitar a heurística do Jackson sobre prefixos `is`.

**D-14 — `ownedByViewer` não é o «papel efetivo» de R4.** É só o mínimo de que a UI de F02
precisa para decidir se mostra o botão «Definições». O campo `papelEfetivo`
(`OWNER`/`MODERATOR`/`MEMBER`/nenhum) exigido pelo critério 5 de R4 é de F04, que o
acrescenta a este mesmo `CommunityResponse` sem remover `ownedByViewer`.

**D-15 — Colisão de `slug` em corrida.** O ciclo de `gerarUnico` fecha a janela em
99,99% dos casos; o `UNIQUE` da base de dados fecha o resto. Se o `saveAndFlush` lançar
`DataIntegrityViolationException`, o serviço converte-a em
`ApiException(409, "Não foi possível criar a comunidade. Tente novamente.")` — **não** faz
retry dentro da mesma transação, porque uma violação de restrição marca a transação como
`rollback-only` e o retry falharia na mesma. Mesmo padrão do `register` de `AuthService`.

**D-16 — Sem `@OneToMany` em `Community`.** Nenhuma coleção de memberships mapeada,
nenhuma cascata. É o que garante estruturalmente que gravar uma comunidade nunca escreve
numa membership (critério 4).

### Ficheiros a criar

**Backend — produção (13)**

| Caminho | Objetivo |
| --- | --- |
| `backend/src/main/java/pt/seerhub/community/domain/Community.java` | Entidade da tabela `communities`. `@ManyToOne(fetch=LAZY)` para `User` em `owner_id`; `name`(60), `slug`(80, único), `description`(2000), `avatarUrl`(500), `bannerUrl`(500), `priceMonthlyCents` (`int`), `status` (`@Enumerated(STRING)`), `createdAt` preenchido em `@PrePersist` com `@Transient Clock` (padrão de `User`). **Sem `currency`, sem `@Version`, sem coleções.** Construtor público `Community(User owner, String name, String slug, Clock clock)`; setters para `name`, `description`, `avatarUrl`, `bannerUrl`, `priceMonthlyCents` e `status`. Constante `public static final String MOEDA = "EUR"`. |
| `backend/src/main/java/pt/seerhub/community/domain/CommunityStatus.java` | `{ ACTIVE, SUSPENDED }`. |
| `backend/src/main/java/pt/seerhub/community/domain/CommunityMembership.java` | Entidade de `community_memberships`. `@ManyToOne` para `Community` e `User`; `role`, `status`, `joinedAt` (`@PrePersist` com `Clock`), `expiresAt` (nulável), `@Version Long version`. Construtor público único: `CommunityMembership.deDono(Community, User, Clock)` — fábrica estática que fixa `OWNER`/`ACTIVE`/`expiresAt = null`, para F02 não conseguir criar por acidente uma linha que pertence a F03. |
| `backend/src/main/java/pt/seerhub/community/domain/MembershipRole.java` | `{ OWNER, MODERATOR, MEMBER }` (os três do `CHECK` do baseline; F02 só usa `OWNER`). |
| `backend/src/main/java/pt/seerhub/community/domain/MembershipStatus.java` | `{ ACTIVE, CANCELLED, EXPIRED }` (idem; F02 só usa `ACTIVE`). |
| `backend/src/main/java/pt/seerhub/community/repo/CommunityRepository.java` | `JpaRepository<Community, Long>`: `Optional<Community> findBySlug(String)`, `boolean existsBySlug(String)`, `long countByOwnerIdAndStatus(Long, CommunityStatus)`, `List<Community> findTop100ByStatusOrderByCreatedAtDesc(CommunityStatus)`, `List<Community> findByOwnerIdOrderByCreatedAtDesc(Long)`. |
| `backend/src/main/java/pt/seerhub/community/repo/CommunityMembershipRepository.java` | `JpaRepository<CommunityMembership, Long>`: `boolean existsByCommunityIdAndUserId(Long, Long)`. Nada mais — o resto é de F03. |
| `backend/src/main/java/pt/seerhub/community/service/SlugGenerator.java` | Funções puras, espelho de `UsernameGenerator`. `static String base(String nome)`: NFD → remove `\p{M}` → `toLowerCase(Locale.ROOT)` → `[^a-z0-9]+` → `-` → corta `-` nas pontas → corta a 60 → se ficar vazio, `"comunidade"`. `static String gerarUnico(String nome, Predicate<String> existe)`: base, depois `base-2`…`base-999`; esgotado, `IllegalStateException`. |
| `backend/src/main/java/pt/seerhub/community/service/CommunityAccessRules.java` | Ver D-8. Funções puras + `MENSAGEM_COMUNIDADE_SUSPENSA`. |
| `backend/src/main/java/pt/seerhub/community/service/CommunityService.java` | `@Transactional criar(CreateCommunityRequest, AuthenticatedUser)`, `@Transactional editar(String slug, UpdateCommunityRequest, AuthenticatedUser)`, `@Transactional(readOnly=true) obterParaLeitura(String slug, Long userIdOuNull)`, `listarAtivas()`, `listarDoDono(Long)`, `exigirDono(Community, Long)`. Constantes públicas `LIMITE_COMUNIDADES_ATIVAS = 3`, `MENSAGEM_LIMITE_ATINGIDO`, `MENSAGEM_COMUNIDADE_NAO_ENCONTRADA`, `MENSAGEM_SEM_PERMISSAO`, `MENSAGEM_SLUG_EM_CONFLITO`. |
| `backend/src/main/java/pt/seerhub/community/api/CommunityController.java` | Os cinco endpoints de D-6. Nos dois `GET` públicos, `@AuthenticationPrincipal AuthenticatedUser autenticado` **pode vir a `null`** (pedido anónimo tem `AnonymousAuthenticationToken` com principal `String`) — tratar `null` explicitamente, nunca desreferenciar. |
| `backend/src/main/java/pt/seerhub/community/api/CreateCommunityRequest.java` | `record (@NotBlank @Size(min=3,max=60) String name, @Size(max=2000) String description, @Size(max=500) @Pattern(regexp="^https?://.+") String avatarUrl, @Size(max=500) @Pattern(regexp="^https?://.+") String bannerUrl, @NotNull @Min(0) Integer priceMonthlyCents)`. Construtor compacto que faz `trim()` ao nome e converte strings em branco em `null` **antes** do Bean Validation (padrão do `RegisterRequest` de F01, desvio 4). |
| `backend/src/main/java/pt/seerhub/community/api/UpdateCommunityRequest.java` | Mesma forma; representação completa (D-4). |
| `backend/src/main/java/pt/seerhub/community/api/CommunityResponse.java` | `record (Long id, String slug, String name, String description, String avatarUrl, String bannerUrl, int priceMonthlyCents, String currency, CommunityStatus status, Long ownerId, String ownerDisplayName, Instant createdAt, boolean ownedByViewer)` + fábrica `de(Community, Long userIdOuNull)`. |

**Backend — testes (6)**

| Caminho | Objetivo |
| --- | --- |
| `backend/src/test/java/pt/seerhub/support/CommunityTestSupport.java` | Classe simples sem anotações Spring, construída em `@BeforeEach` (padrão de `AuthTestSupport`). `nomeUnico(prefixo)` → `prefixo + "-" + UUID.substring(0,8)` (≤60 chars, obrigatório pelo contentor partilhado); `criar(token, nome)` e `criar(token, nome, precoCentimos)` → `CommunityResponse`; `suspender(slug)` / `reativar(slug)` por `UPDATE` JDBC (não há UI de admin antes de F14, tal como `AuthTestSupport.registarAdmin`); `inserirMembership(communityId, userId, role, status, expiresAt)` por `INSERT` JDBC **deliberadamente cru**, para F02 não inventar a API de F03; `lerMembership(communityId, userId)` → `Map<String,Object>` com `id, community_id, user_id, role, status, joined_at, expires_at, version`, usado na fotografia do critério 4; `contarMemberships(communityId)`; `lerColunaDaComunidade(slug, coluna)`. |
| `backend/src/test/java/pt/seerhub/community/SlugGeneratorTest.java` | 4 testes (1c1–1c4). |
| `backend/src/test/java/pt/seerhub/community/CommunityAccessRulesTest.java` | 2 testes (6c, 6d). Constrói `Community` e `User` em memória; nenhuma Spring, nenhum mock. |
| `backend/src/test/java/pt/seerhub/community/CommunityCreationIT.java` | 11 testes (1a, 1b, 1d, 1e, 1f, 1g, 2a, 2b, 5a, 5b, X4). |
| `backend/src/test/java/pt/seerhub/community/CommunityEditIT.java` | 11 testes (3a–3h, 4a, X5, X6). |
| `backend/src/test/java/pt/seerhub/community/CommunityVisibilityIT.java` | 6 testes (6a, 6b, 6e, X1, X2, X3). |

**Frontend (7)**

| Caminho | Objetivo |
| --- | --- |
| `frontend/src/lib/communities.ts` | Tipo `Comunidade` (espelha `CommunityResponse`) e as quatro funções sobre `apiFetch`: `criarComunidade`, `listarMinhasComunidades`, `obterComunidade(slug)`, `guardarComunidade(slug, dados)`. Também `eurosParaCentimos(valor: string): number` (`Math.round(Number(valor) * 100)`) e `centimosParaEuros(cents: number): string`, para a conversão viver num sítio só e ser testável. |
| `frontend/src/pages/MyCommunitiesPage.tsx` | `/comunidades`. `useQuery` sobre `GET /api/me/communities`. Lista nome, slug, estado e preço; ligação «Definições» por linha e botão «Criar comunidade». Estado vazio com convite a criar a primeira (§6.2 da spec). |
| `frontend/src/pages/MyCommunitiesPage.test.tsx` | FE5. |
| `frontend/src/pages/CreateCommunityPage.tsx` | `/comunidades/nova`. Formulário com nome (obrigatório, `minLength=3`/`maxLength=60`), descrição, avatar, banner e preço mensal em euros. Em sucesso, `navigate('/comunidades/{slug}/definicoes')`. Em erro, `<p role="alert">` com `ApiError.detail` (padrão de `LoginPage`). |
| `frontend/src/pages/CreateCommunityPage.test.tsx` | FE1, FE2. |
| `frontend/src/pages/CommunitySettingsPage.tsx` | `/comunidades/:slug/definicoes`. Carrega por `GET /api/communities/{slug}`, preenche o formulário, grava com `PUT`. Se `status === "SUSPENDED"`, mostra um aviso «Esta comunidade está suspensa.» acima do formulário, sem o desativar (D-7). |
| `frontend/src/pages/CommunitySettingsPage.test.tsx` | FE3, FE4. |

### Ficheiros a editar

| Caminho | Alteração | Risco |
| --- | --- | --- |
| `backend/src/main/java/pt/seerhub/config/SecurityConfig.java` | Inserir **uma linha**, imediatamente antes de `.requestMatchers("/api/admin/**")`: `.requestMatchers(HttpMethod.GET, "/api/communities", "/api/communities/*").permitAll()`. Nada mais é tocado — nem a ordem das regras existentes, nem `STATELESS`, nem `csrf.disable()`, nem os handlers, nem o filtro JWT. | **Baixo.** O matcher é só `GET` e `*` cobre um único segmento, portanto `POST /api/communities` e `PUT /api/communities/{slug}` continuam a exigir autenticação por `anyRequest().authenticated()`. `AuthorizationIT` usa `/api/users/me` e `/api/admin/users`, intocados. |
| `backend/src/main/java/pt/seerhub/common/error/ApiExceptionHandler.java` | Acrescentar `@ExceptionHandler(HttpMessageNotReadableException.class)` → `ProblemDetail` 400 com `detail` = nova constante pública `MENSAGEM_PEDIDO_MAL_FORMADO = "O corpo do pedido é inválido."` e `correlationId`. Aditivo; nenhum handler existente é alterado. | **Baixo.** `ApiExceptionHandlerIT` (F00) lança uma `RuntimeException` própria e continua a cair no handler genérico de 500. |
| `backend/src/main/resources/application.yml` | Sob `spring.jackson`, acrescentar `deserialization: accept-float-as-int: false` (irmão do `default-property-inclusion` já existente). | **Baixo.** Cuidado mecânico: `ConfigurationConventionsTest.nenhumFicheiroVersionadoContemSegredoComAparenciaReal` varre este ficheiro à procura de sequências de 40+ caracteres em `[A-Za-z0-9_-]`; a linha nova tem no máximo 19. Nenhuma variável de ambiente nova → `EnvExampleTest` não é afetado. |
| `frontend/src/App.tsx` | Três `<Route>` novas dentro do `<Routes>` já existente, todas envolvidas em `<RequireAuth>`: `/comunidades` → `MyCommunitiesPage`; `/comunidades/nova` → `CreateCommunityPage`; `/comunidades/:slug/definicoes` → `CommunitySettingsPage`. A rota `/` continua `HealthPage`; **não acrescentar cabeçalho nem navegação global** (D-9 do plano de F01 — partiria `App.test.tsx`). | **Baixo.** `App.test.tsx` renderiza `/` e continua a passar. O React Router v6 dá prioridade a `/comunidades/nova` (segmento estático) sobre padrões dinâmicos automaticamente. |

### Modelo de dados / migrações

**Nenhuma migração.** `communities` e `community_memberships` estão na §8 da spec e já
existem em `V2__baseline_schema.sql`; pela regra do `CLAUDE.md`, F02 mapeia as entidades
contra elas e confia em `ddl-auto: validate`. Não há nenhuma coluna em falta:
`price_monthly_cents` já é `INTEGER` com `CHECK (>= 0)`, `name` já tem
`CHECK (char_length BETWEEN 3 AND 60)`, `slug` já é `UNIQUE`, `status` já tem
`CHECK (IN ('ACTIVE','SUSPENDED'))` e `community_memberships` já tem `version` e
`UNIQUE (community_id, user_id)`. **`V1`, `V2` e `V3` não são editados em nenhuma
circunstância.** Se, durante a implementação, se descobrir mesmo assim uma necessidade de
esquema, é um `V4__<descrição>.sql` novo — e isso é um desvio a documentar no handoff,
não uma edição.

## 5. Ordem de implementação

Cada passo compila e corre sozinho; os testes vêm com o código do passo, não no fim.

1. **Domínio.** `CommunityStatus`, `MembershipRole`, `MembershipStatus`, `Community`
   (sem `currency`, sem `@Version`, sem coleções), `CommunityMembership` (com `@Version`,
   com a fábrica `deDono`). Correr `./mvnw test` **já aqui**: `ddl-auto: validate` falha o
   arranque de qualquer `*IT` se algum mapeamento não bater certo com o baseline. É o
   ponto mais barato para apanhar o problema de `CHAR(3)`.
2. **Repositórios.** `CommunityRepository`, `CommunityMembershipRepository`. Correr a
   suite: nomes de métodos derivados errados também rebentam no arranque do contexto.
3. **`SlugGenerator` + `SlugGeneratorTest`** (1c1–1c4). Lógica pura; verde antes de
   qualquer HTTP.
4. **`CommunityAccessRules` + `CommunityAccessRulesTest`** (6c, 6d). Também puro. Fica
   assim pronto, e testado, o ponto de contacto que F03 vai consumir.
5. **`CommunityService.criar` + DTOs de criação + `CommunityController` (`POST`).**
   Numa única transação: verificar o limite de 3 → gerar o slug único → gravar a
   comunidade → gravar a membership `OWNER`. Criar `CommunityTestSupport` e escrever
   `CommunityCreationIT` (1a, 1b, 1d, 1e, 1f, 1g, 2a, 5a, X4). Verde.
6. **Leituras + regra pública em `SecurityConfig`.** `GET /api/communities`,
   `GET /api/communities/{slug}`, `GET /api/me/communities`, `CommunityResponse`, a linha
   nova em `SecurityConfig`. Fechar 2b em `CommunityCreationIT` e escrever
   `CommunityVisibilityIT` inteiro (6a, 6b, 6e, X1, X2, X3) — 6e usa o `PUT` do passo 7,
   por isso pode ficar para o fim deste bloco. Correr a suite **completa** aqui: é o passo
   com maior risco de partir os 50 testes existentes, por causa da alteração à cadeia de
   segurança.
7. **`CommunityService.editar` + `UpdateCommunityRequest` + `PUT`.** Ordem obrigatória
   dentro do método: procurar por slug → 404 se não existe → `exigirDono` → 403 se não é →
   aplicar campos → gravar. Escrever `CommunityEditIT` sem 3d ainda (3a, 3b, 3c, 3e, 3f,
   3g, 3h, 4a, X5, X6). **O 4a é o teste a que se dedica mais atenção** — seguir as sete
   asserções da §3 à letra.
8. **Estrito no preço fracionário.** `application.yml` (`accept-float-as-int: false`) +
   o handler novo em `ApiExceptionHandler`. Acrescentar 3d a `CommunityEditIT`. Correr a
   suite completa: 84 JUnit verdes.
9. **Frontend: cliente e listagem.** `lib/communities.ts`, `MyCommunitiesPage` +
   `MyCommunitiesPage.test.tsx` (FE5), rota em `App.tsx`. `npm test` verde.
10. **Frontend: criação.** `CreateCommunityPage` + testes (FE1, FE2), rota.
11. **Frontend: definições.** `CommunitySettingsPage` + testes (FE3, FE4), rota.
    `npm test` → 17 verdes; `npm run typecheck` e `npm run build` limpos.
12. **Verificação final** (§7), e escrever `docs/features/F02-comunidades/handoff.md` com,
    obrigatoriamente: a secção da fronteira F02/F03 sobre `community_memberships` (§2.1
    deste plano, copiada e confirmada contra o que ficou de facto escrito), a dívida de
    `/__test__/**` reencaminhada para F15, e a dívida de upload de imagens com dono F15.

## 6. Não tocar

Fronteira de raio de explosão. Qualquer alteração a um destes ficheiros é um desvio a
justificar explicitamente no handoff.

**Documentos e configuração do run**
- `docs/specs/seerhub.md`
- `docs/features/BACKLOG.md`
- `docs/features/CHANGELOG.md`
- `docs/features/F00-fundacoes/plan.md` e `docs/features/F00-fundacoes/handoff.md`
- `docs/features/F01-contas-autenticacao/plan.md` e `.../handoff.md`
- `seerhub.md` (o brief de origem, na raiz)
- `CLAUDE.md`
- **`.env`** — nunca lido, nunca escrito, nunca citado. Contém uma chave de API real.
- `.env.example` (F02 não introduz nenhuma variável de ambiente nova)
- `.claude/` e todo o seu conteúdo

**Migrações aplicadas**
- `backend/src/main/resources/db/migration/V1__enable_extensions.sql`
- `backend/src/main/resources/db/migration/V2__baseline_schema.sql`
- `backend/src/main/resources/db/migration/V3__refresh_tokens.sql`

**Código de produção de F00/F01 (exceto as três edições nomeadas na §4)**
- Tudo em `backend/src/main/java/pt/seerhub/user/**` (domínio, repositórios, serviços,
  segurança, API) — F02 é apenas consumidora.
- `backend/src/main/java/pt/seerhub/common/web/CorrelationIdFilter.java`
- `backend/src/main/java/pt/seerhub/common/error/ApiException.java`
- `backend/src/main/java/pt/seerhub/config/SeerHubProperties.java`,
  `ClockConfig.java`, `SeerHubApplication.java`
- Em `SecurityConfig.java`: **só se acrescenta uma linha**. Não reescrever o método, não
  reordenar regras, não remover `requestMatchers("/__test__/**").permitAll()`, não mexer
  em `STATELESS`, `csrf.disable()`, nos handlers de `ProblemDetail`, no
  `JwtAuthenticationFilter`, no `BCRYPT_STRENGTH` nem no bean `jwtService`.
- Em `ApiExceptionHandler.java`: **só se acrescenta um handler**. Os três existentes ficam
  literalmente como estão.
- Em `application.yml`: **só se acrescenta a chave do Jackson**. `ddl-auto` continua
  `validate`, `show-details` continua `when-authorized`, `structured.format.console`
  continua `ecs`, os placeholders `${...}` ficam todos como estão.

**Testes existentes (os 62 da baseline)**
- Todos os ficheiros em `backend/src/test/java/pt/seerhub/{config,health,migration,common,user}/**`
- `backend/src/test/java/pt/seerhub/support/AbstractIntegrationTest.java`
- `backend/src/test/java/pt/seerhub/support/AuthTestSupport.java` — **reutilizar, não
  alterar.** Se F02 precisar de algo novo em teste, vai para `CommunityTestSupport`.
- `backend/src/test/java/pt/seerhub/support/RepoRoot.java`
- `backend/src/test/resources/application-test.yml`
- `frontend/src/App.test.tsx`, `frontend/src/lib/api.test.ts`,
  `frontend/src/lib/auth.test.tsx`, `frontend/src/components/RequireAuth.test.tsx`,
  `frontend/src/pages/LoginPage.test.tsx`, `frontend/src/pages/RegisterPage.test.tsx`

**Frontend de F00/F01**
- `frontend/src/lib/api.ts` — F02 usa `apiFetch` tal como está; não alterar a assinatura
  nem a lógica de renovação.
- `frontend/src/lib/auth.tsx`, `frontend/src/components/RequireAuth.tsx`,
  `frontend/src/pages/{HealthPage,LoginPage,RegisterPage,AccountPage}.tsx`,
  `frontend/src/main.tsx`, `frontend/src/index.css`
- Em `App.tsx`: **só se acrescentam três `<Route>`**. Sem cabeçalho global, sem navegação,
  sem alterar a rota `/`.

**Dependências e infraestrutura**
- `pom.xml` (raiz), `backend/pom.xml`, `frontend/package.json`,
  `frontend/package-lock.json` — F02 **não precisa de nenhuma dependência nova**, em
  nenhum dos módulos.
- `docker-compose.yml`, `backend/Dockerfile`, `frontend/Dockerfile`,
  `frontend/nginx.conf`, `frontend/vite.config.ts`, `.gitignore`

**Comportamentos que não podem mudar**
- Os 5 endpoints de F01 (`/api/auth/*`, `/api/users/me`, `/api/admin/users`) e os seus
  códigos e mensagens.
- Formato de `ProblemDetail` com `correlationId` em toda a resposta de erro.
- O papel por comunidade nunca entra no JWT.

## 7. Verificação

Executar, por esta ordem, a partir da raiz do repositório
(`C:\Users\tiago\Desktop\Projetos\SeerHub`):

```
./mvnw test
```

**Sucesso:** `Tests run: 84, Failures: 0, Errors: 0, Skipped: 0` e `BUILD SUCCESS`.
Os **50 testes de F00+F01 continuam todos verdes** — nenhum apagado, nenhum renomeado,
nenhum `@Disabled`. Os 34 novos são: 4 `SlugGeneratorTest`, 2 `CommunityAccessRulesTest`,
11 `CommunityCreationIT`, 11 `CommunityEditIT`, 6 `CommunityVisibilityIT`. Sem rede e sem
chave de API: o único contentor é o Postgres do Testcontainers.

```
cd frontend && npm test
```

**Sucesso:** `17 passed` (12 de F00+F01 intactos + 5 novos), 0 falhados.

```
cd frontend && npm run typecheck
cd frontend && npm run build
```

**Sucesso:** ambos sem erros nem avisos de TypeScript.

Verificação manual (melhor esforço; documentar no handoff o que se conseguiu correr):

```
docker compose --env-file .env.example up -d --build
```

**Sucesso:** os três serviços chegam a `healthy`; registar uma conta em
`POST /api/auth/register`, criar uma comunidade em `POST /api/communities` (`201` com
`slug`), repetir o mesmo nome (`201` com `slug-2`), pedir `GET /api/communities` **sem
qualquer cabeçalho `Authorization`** (`200` com a lista), pedir
`PUT /api/communities/{slug}` com `"priceMonthlyCents": 12.50` (`400`, não `500` nem
truncado), e criar uma quarta comunidade (`409`). Terminar com
`docker compose down -v`. Se a porta 8080 estiver ocupada nesta máquina (foi o caso em
F00 e F01), remapear com variáveis de shell — **nunca editando `.env.example`**.

**Critério global de falha:** qualquer um dos 62 testes da baseline a ficar vermelho,
qualquer ficheiro da lista da §6 alterado sem justificação escrita, ou qualquer teste
novo que precise de rede ou de chave de API.

## 8. Casos de fronteira cobertos

Da §10 da spec, os que pertencem a F02:

- **Comunidade suspensa e membros existentes** — D-7; testes 6a, 6b, 6c, 6d, 6e. É o caso
  de fronteira central desta feature.
- **Subscrever uma comunidade suspensa** (§6.2, «Falha») — a mensagem literal
  «Esta comunidade está suspensa.» já existe, testada, em
  `CommunityAccessRules.MENSAGEM_COMUNIDADE_SUSPENSA`; F03 só tem de a propagar.
- **Colisão de nome/slug** — o ciclo determinístico de `gerarUnico` (1b, 1c4) e, para a
  corrida de dois pedidos simultâneos, a restrição `UNIQUE` traduzida em 409 (D-15).
- **Nome sem qualquer carácter utilizável** (emoji, pontuação, alfabeto não latino) — o
  slug cai para `"comunidade"` e passa a receber sufixos numéricos (1c2); a comunidade é
  criada na mesma, porque `name` guarda o texto original intacto.
- **Preço escrito como fração** — 3d; nunca truncado em silêncio.

Da §10, os que **não** pertencem a F02 e ficam nomeados aqui para não se perderem:

- **«Dono apaga a conta → a comunidade fica suspensa e o admin decide.»** F02 não tem
  apagamento de conta (F01 também não o implementou) nem ferramenta de admin (F14). F02
  deixa o terreno pronto: `communities.status` é honrado em todas as consultas de F02, por
  isso pôr uma comunidade em `SUSPENDED` produz já o comportamento certo. **Dono: F14.**
- **Subscrição expira com tips pendentes** — R3/R11.
- **Utilizador subscreve duas vezes a mesma comunidade** — já impedido pelo
  `UNIQUE (community_id, user_id)` do baseline; o caminho que o exercita é de F03.

## 9. Riscos em aberto

1. **`currency CHAR(3)` pode fazer o contexto não arrancar** se o implementador mapear o
   campo, por hábito, apesar de D-2. **Sinal:** *todos* os `*IT` falham de uma vez com um
   erro de validação de esquema `bpchar` vs `varchar`. **Descoberta mais barata:** correr
   `./mvnw test` logo no fim do passo 1, com o domínio e nada mais. **Se acontecer:** a
   correção é apagar o campo, não é `columnDefinition` (F01 já provou que não resolve).
2. **A regra nova em `SecurityConfig` pode abrir demasiado.** `/api/communities/*` com
   `HttpMethod.GET` é estreito por construção, mas um erro de digitação (`/**` em vez de
   `*`, ou esquecer o `HttpMethod.GET`) tornaria `PUT` público. **Descoberta:** o teste 3f
   (`editarSemTokenDevolve401`) fica vermelho de imediato. Está deliberadamente na tabela
   por isso.
3. **O critério 4 é o mais fácil de fingir.** Um teste que se limite a `status().isOk()`
   passa sempre e não prova nada. **Mitigação:** as sete asserções da §3 são obrigatórias,
   em especial a comparação da linha inteira incluindo `version`, e a asserção de que o
   preço da comunidade *mudou* mesmo (senão o teste passaria com um endpoint que não faz
   nada). Quem rever a implementação deve ler este teste primeiro.
4. **Contentor Postgres partilhado e nunca limpo.** Um nome de comunidade constante em vez
   de `nomeUnico(...)` faz os testes dependerem da ordem de execução e falharem só na
   segunda corrida da suite. **Descoberta mais barata:** correr `./mvnw test` **duas vezes
   seguidas** antes de dar a feature por fechada — a segunda corrida é o teste real desta
   armadilha.
5. **A listagem pública é `findTop100...`.** Se um dia a suite acumular mais de 100
   comunidades `ACTIVE` antes de `CommunityVisibilityIT` correr, as asserções continuam a
   funcionar (a ordem é `createdAt DESC` e a comunidade do teste é a mais recente), mas o
   endpoint fica a mentir por omissão em produção. Fica como **dívida com dono: F10**, que
   substitui este endpoint por um com paginação, ordenação e pesquisa.
6. **`spring.jackson.deserialization.accept-float-as-int: false` é global.** Torna estrito
   todo o corpo JSON de entrada da aplicação. Hoje não há nenhum DTO numérico de entrada
   além dos de F02, mas features futuras (odds, stakes) recebem números decimais em campos
   `BigDecimal`/`NUMERIC` — o que continua a funcionar, porque a flag só proíbe *coagir*
   um decimal para um inteiro. **Descoberta:** a suite inteira, agora e em cada feature
   seguinte.
7. **A conversão euros→cêntimos no frontend usa vírgula flutuante.**
   `Math.round(Number("9.99") * 100)` dá 999, mas é a única linha do frontend onde um erro
   de arredondamento se traduz em dinheiro errado. Está isolada em
   `lib/communities.ts::eurosParaCentimos` e coberta por FE4 precisamente por isso; se
   alguma vez der sinais de imprecisão, o passo seguinte é receber o valor como string e
   converter sem passar por `Number`.