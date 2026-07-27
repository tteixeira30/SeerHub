# F01 — handoff

**Status:** COMPLETE
**Implementado:** 2026-07-27 · Sonnet 5
**Test run:** `./mvnw test` → 50 passados, 0 falhas, 0 erros, 0 ignorados · `cd frontend && npm test` → 12 passados, 0 falhados · `npm run typecheck` limpo · `npm run build` limpo

## O que agora existe

Autenticação real no SeerHub. Um visitante regista-se em `POST /api/auth/register`
(email + password ≥10 caracteres, BCrypt força 10) e recebe imediatamente um par de
tokens; `POST /api/auth/login` devolve o mesmo par a quem já tem conta; o access token
JWT (HS256, 15 min, claims `sub`/`email`/`role`/`iat`/`exp`/`jti`/`iss`) abre qualquer
endpoint protegido via `Authorization: Bearer`; o refresh token opaco (30 dias,
guardado como hash SHA-256, nunca em claro) é rotativo — `POST /api/auth/refresh` mata o
anterior e emite um par novo — e revogável, com deteção de reutilização que revoga toda a
família (roubo/replay). `POST /api/auth/logout` (autenticado) revoga o refresh usado, sem
afetar outras sessões do mesmo utilizador. Papel global `USER`/`ADMIN` verificado em
`GET /api/admin/users`, primeiro endpoint exclusivo de `ADMIN`. A cadeia de segurança de
F00 deixou de ser `permitAll()`: qualquer pedido fora da lista pública (`/actuator/health`,
os três `POST /api/auth/*` de entrada) exige autenticação, e `/api/admin/**` exige o papel.
A dívida de F00 (`show-details: always`) foi fechada para `when-authorized` — o
`healthcheck` anónimo do compose continua a receber `200`, só os `components` deixaram de
ser públicos. No frontend existem `/registo`, `/entrar` e `/conta` (protegida), com sessão
gerida por `AuthProvider`/`useAuth()` e `apiFetch` a anexar o Bearer e a renovar uma vez em
`401`. Verificado também em `docker compose up` real (três serviços `healthy`, registo →
`201`, registo duplicado → `409` com a mensagem genérica, health autenticado → `db: UP`).

## Superfície pública para a próxima feature (F02)

### Obter o utilizador autenticado num controlador

```java
@GetMapping("/api/communities/mine")
public List<CommunityResponse> minhas(@AuthenticationPrincipal AuthenticatedUser autenticado) {
    // autenticado.id()    -> Long, o userId para consultas de membership/ownership
    // autenticado.email() -> String
    // autenticado.role()  -> GlobalRole (USER|ADMIN) — papel GLOBAL, nunca por comunidade
}
```

`pt.seerhub.user.security.AuthenticatedUser` — `record (Long id, String email, GlobalRole role)`
(`backend/src/main/java/pt/seerhub/user/security/AuthenticatedUser.java`). É o `principal`
de um `UsernamePasswordAuthenticationToken` com autoridade `ROLE_<papel>`. **Não existe
`UserDetailsService`/`UserDetails`** — não tentar carregá-lo assim.

**Importante para F02–F04 (decisão estrutural D-4 do plano):** o token só transporta
identidade global. O papel efetivo numa comunidade (`OWNER`/`MODERATOR`/`MEMBER`) **nunca
viaja no JWT** — tem de ser lido da base de dados a cada pedido, a partir de
`autenticado.id()`. Um token de 15 minutos com papel de comunidade embutido daria acesso
indevido até 15 minutos depois de uma membership expirar (R3).

### Exigir autenticação num endpoint novo

Qualquer rota não listada explicitamente em
`backend/src/main/java/pt/seerhub/config/SecurityConfig.java` já exige autenticação por
omissão (`anyRequest().authenticated()`). Ou seja: **não é preciso tocar em
`SecurityConfig` só para proteger um endpoint novo.** Só editar esse ficheiro se F02
precisar de:
- um caminho **público** novo (acrescentar um `requestMatchers(...).permitAll()` **antes**
  de `anyRequest()`);
- uma regra de **papel global** adicional por URL (acrescentar
  `requestMatchers("/api/algo/**").hasRole("...")`, também antes de `anyRequest()`).

Para autorização mais fina (por comunidade, por papel de membership), usar
`@PreAuthorize(...)` no controlador — `@EnableMethodSecurity` já está ligado (D-8 do plano
de F01). **Não existe ainda nenhum `PermissionEvaluator` por comunidade** — isso é do R4,
explicitamente adiado para F04. F02/F03 que precisem de autorização por comunidade têm de
implementá-la a partir de consultas diretas (`autenticado.id()` + repositório de
membership), não à espera de uma abstração que ainda não existe.

### Entidade `User` e o seu repositório

`pt.seerhub.user.domain.User` (`backend/src/main/java/pt/seerhub/user/domain/User.java`) —
mapeada contra a tabela `users` do `V2` (F00), `ddl-auto: validate`. Getters públicos:
`getId()`, `getEmail()`, `getPasswordHash()`, `getUsername()`, `getDisplayName()`,
`getAvatarUrl()`, `getGlobalRole()`, `getStatus()`, `getCreatedAt()`. **Não tem construtor
público sem argumentos utilizável fora do JPA** — para criar um `User` novo usar
`new User(email, passwordHash, username, displayName, clock)`.

`pt.seerhub.user.repo.UserRepository extends JpaRepository<User, Long>`
(`backend/src/main/java/pt/seerhub/user/repo/UserRepository.java`): `findByEmail(String)`,
`existsByEmail(String)`, `existsByUsername(String)`. F02 vai precisar de `findById` (já
vem do `JpaRepository`) para resolver o `owner_id` de uma comunidade a partir de
`autenticado.id()`.

### Enums

- `pt.seerhub.user.domain.GlobalRole` — `{ USER, ADMIN }` (papel global).
- `pt.seerhub.user.domain.UserStatus` — `{ ACTIVE, SUSPENDED }`.

Ambos `@Enumerated(EnumType.STRING)`, persistidos como `VARCHAR` (convenção do CLAUDE.md).

### Cadeia de segurança — como está e como F02 lhe acrescenta regras

`backend/src/main/java/pt/seerhub/config/SecurityConfig.java`. Estrutura atual do
`authorizeHttpRequests`, por ordem:

```java
.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/refresh").permitAll()
.requestMatchers("/__test__/**").permitAll()   // ver "Desvios" — só existe em testes
.requestMatchers("/api/admin/**").hasRole("ADMIN")
.anyRequest().authenticated()
```

`STATELESS` e `csrf.disable()` mantidos (API stateless com Bearer). O
`JwtAuthenticationFilter` está registado antes de `UsernamePasswordAuthenticationFilter` e
os dois handlers (`ProblemDetailAuthenticationEntryPoint`/`ProblemDetailAccessDeniedHandler`)
produzem sempre `ProblemDetail` com `correlationId`, nunca a página de erro por omissão do
Spring Security. **F02 não deve reescrever este método** — só inserir novos
`requestMatchers(...)` antes de `anyRequest()`, ou usar `@PreAuthorize` nos seus
controladores (ver acima).

`BCryptPasswordEncoder` continua o bean `PasswordEncoder`, agora explicitamente com força
10: `SecurityConfig.BCRYPT_STRENGTH = 10` (constante pública).

### Emitir/validar tokens (só se F02 precisar de emitir um token fora do fluxo normal)

`pt.seerhub.user.service.JwtService` (`backend/src/main/java/pt/seerhub/user/service/JwtService.java`):
`gerarAccessToken(AuthenticatedUser)` → `String`; `validarEDecodificar(String token)` →
`AuthenticatedUser` (lança `io.jsonwebtoken.JwtException` não verificada se inválido).
Injetável como bean Spring (`SecurityConfig.jwtService(...)`) ou construtível diretamente
com `new JwtService(SeerHubProperties, Clock)` em teste. Constante pública
`JwtService.ISSUER = "seerhub"`.

### Mensagens de erro públicas de `AuthService`

`pt.seerhub.user.service.AuthService` expõe `MENSAGEM_REGISTO_RECUSADO`,
`MENSAGEM_LOGIN_INVALIDO`, `MENSAGEM_REFRESH_INVALIDO` como constantes públicas — usar
estas constantes em vez de repetir a mensagem literal em qualquer teste que precise de a
assertar.

### Autenticar em testes de integração de F02

`pt.seerhub.support.AuthTestSupport` (`backend/src/test/java/pt/seerhub/support/AuthTestSupport.java`)
— construir em `@BeforeEach` com `new AuthTestSupport(mockMvc, objectMapper, jdbcTemplate, properties)`
(todos `@Autowired` na classe de teste). Métodos:

| Método | Devolve | Uso |
| --- | --- | --- |
| `registarEAutenticar(email, password)` | `AuthResponse` | cria conta nova, já autenticada |
| `login(email, password)` | `AuthResponse` | login numa conta existente |
| `registarAdmin(email, password)` | `AuthResponse` (papel `ADMIN`) | promove por JDBC direto e reautentica (não há endpoint de promoção) |
| `accessTokenExpirado(id, email, role)` | `String` (JWT) | token assinado com o segredo real mas já expirado |
| `accessTokenComOutroSegredo(id, email, role)` | `String` (JWT) | token bem-formado, assinatura inválida |
| `emailUnico(prefixo)` | `String` | email único por teste (contentor Postgres partilhado, sem limpeza entre testes) |

`AuthResponse` (`pt.seerhub.user.api.AuthResponse`) — `record (String accessToken,
String refreshToken, String tokenType, long expiresIn, UserResponse user)`.
`UserResponse.de(User)` constrói a versão pública (sem password) de um `User`.

### Frontend

- `frontend/src/lib/api.ts`: `apiFetch<T>(path, init?)` (inalterado na assinatura),
  `setSession(accessToken, refreshToken)`, `clearSession()`, `getAccessToken()`,
  `getRefreshToken()`. Anexa `Authorization: Bearer` automaticamente quando há sessão;
  renova uma vez em `401` (exceto em `/api/auth/*`) e repete o pedido; limpa a sessão se a
  renovação falhar. `AuthResponseBody` exportado para quem precisar do formato de
  `AuthResponse` no frontend.
- `frontend/src/lib/auth.tsx`: `<AuthProvider>` (envolve toda a `<App/>`) e
  `useAuth()` → `{ utilizador, autenticado, aCarregar, registar, entrar, sair }`. `Utilizador`
  também exportado (espelha `UserResponse`).
- `frontend/src/components/RequireAuth.tsx`: `<RequireAuth>{children}</RequireAuth>` —
  redireciona para `/entrar` (com `state.from`) se não autenticado; mostra um estado de
  carregamento enquanto a tentativa de retomar sessão decorre. F02 usa isto para qualquer
  rota nova que exija sessão (ex.: `/comunidades/nova`).
- `frontend/src/App.tsx` já está envolvida em `<AuthProvider>`; a rota `/` continua
  `HealthPage`, sem cabeçalho/navegação global (D-9 do plano — não quebrar
  `App.test.tsx`).

## Ficheiros criados

**Backend — produção (24):** `V3__refresh_tokens.sql`,
`config/ClockConfig.java`, `user/domain/{User,GlobalRole,UserStatus,RefreshToken}.java`,
`user/repo/{UserRepository,RefreshTokenRepository}.java`,
`user/service/{JwtService,AuthService,UsernameGenerator}.java`,
`user/security/{AuthenticatedUser,JwtAuthenticationFilter,ProblemDetailAuthenticationEntryPoint,ProblemDetailAccessDeniedHandler}.java`,
`user/api/{AuthController,UserController,AdminUserController,RegisterRequest,LoginRequest,RefreshRequest,LogoutRequest,AuthResponse,UserResponse}.java`.

**Backend — testes (8):** `support/AuthTestSupport.java`,
`user/JwtServiceTest.java`, `user/UsernameGeneratorTest.java`, `user/RegistrationIT.java`,
`user/LoginIT.java`, `user/TokenRefreshIT.java`, `user/LogoutIT.java`,
`user/AuthorizationIT.java`.

**Frontend (9):** `lib/auth.tsx`, `lib/auth.test.tsx`, `pages/RegisterPage.tsx`,
`pages/RegisterPage.test.tsx`, `pages/LoginPage.tsx`, `pages/LoginPage.test.tsx`,
`pages/AccountPage.tsx`, `components/RequireAuth.tsx`, `components/RequireAuth.test.tsx`.

## Ficheiros editados

| Caminho | Alteração |
| --- | --- |
| `backend/pom.xml` | `<jjwt.version>0.12.6</jjwt.version>`; `jjwt-api` (compile), `jjwt-impl`/`jjwt-jackson` (runtime) |
| `backend/src/main/java/pt/seerhub/config/SecurityConfig.java` | `@EnableMethodSecurity`; regras reais; `JwtAuthenticationFilter` registado; handlers de `ProblemDetail` para 401/403; `BCryptPasswordEncoder(10)`; bean `JwtService` |
| `backend/src/main/resources/application.yml` | `show-details: always` → `when-authorized` |
| `backend/src/test/java/pt/seerhub/health/HealthCheckIT.java` | `healthDevolve200ComComponenteDbUp` autentica com token real; acrescentado `healthAnonimoDevolve200SemExporOsComponentes` |
| `backend/src/test/java/pt/seerhub/health/HealthCheckDbDownIT.java` | pedido passa a `.with(user("saude"))` |
| `frontend/src/lib/api.ts` | `setSession`/`clearSession`/`getAccessToken`/`getRefreshToken`; Bearer automático; renovação single-flight em 401 |
| `frontend/src/lib/api.test.ts` | acrescentados FE3–FE5 e `beforeEach` de limpeza de sessão; os 2 testes de F00 inalterados |
| `frontend/src/App.tsx` | envolvido em `<AuthProvider>`; rotas `/registo`, `/entrar`, `/conta` (protegida) |

## Testes

Todas as 28 linhas da tabela de critérios do plano (secção 3) mapeadas 1:1 para um teste
real, mais os 2 testes de saúde adaptados (D2/D3) e o novo D1. Backend: **50 testes JUnit**
(22 de F00 intactos + 28 novos: 3 `JwtServiceTest`, 1 `UsernameGeneratorTest`, 5
`RegistrationIT`, 5 `LoginIT`, 5 `TokenRefreshIT`, 2 `LogoutIT`, 6 `AuthorizationIT`, 1
acrescentado a `HealthCheckIT`), todos passados. Frontend: **12 testes Vitest** (4 de F00
intactos + 2 `api.test.ts` novos + 2 `auth.test.tsx` + 1 `RequireAuth.test.tsx` + 1
`LoginPage.test.tsx` + 1 `RegisterPage.test.tsx`... nota: `api.test.ts` ganhou 3 novos
(FE3–FE5), não 2 — ver tabela abaixo), todos passados.

| Critério | Teste | Resultado |
| --- | --- | --- |
| 1a–1d, 3a | `RegistrationIT` (5 testes) | passou |
| 2a, 3b, X2, X3, X5 | `LoginIT` (5 testes) | passou |
| 2c, 2d, 2e, X1, X4 | `TokenRefreshIT` (5 testes) | passou |
| 5a, 5b | `LogoutIT` (2 testes) | passou |
| 4a–4f | `AuthorizationIT` (6 testes) | passou |
| 2b, E2, E3 | `JwtServiceTest` (3 testes) | passou |
| E1 | `UsernameGeneratorTest` (1 teste) | passou |
| D1, D2 | `HealthCheckIT` (2 testes) | passou |
| D3 | `HealthCheckDbDownIT` (1 teste, nome mantido) | passou |
| FE1 | `RegisterPage.test.tsx` | passou |
| FE2 | `LoginPage.test.tsx` | passou |
| FE3, FE4, FE5 | `api.test.ts` (3 novos, 5 no total) | passou |
| FE6 | `RequireAuth.test.tsx` | passou |
| (extra, do plano) | `auth.test.tsx` (2 testes: arranque com/sem refresh token) | passou |

Também verificado manualmente com `docker compose --env-file .env.example up -d --build`
(portas remapeadas `BACKEND_PORT=18080`/`FRONTEND_PORT=15173` por conflito local, sem tocar
`.env.example`): os três serviços chegaram a `healthy`; `curl` anónimo a
`/actuator/health` devolveu `{"status":"UP","groups":[...]}` sem `components`; registo
devolveu `201` com tokens; o mesmo registo repetido devolveu `409` com a mensagem genérica;
`/actuator/health` com o `accessToken` devolvido mostrou `components.db.status: "UP"`.
`docker compose down -v` limpou tudo.

## Desvios face ao plano

1. **`token_hash` mudou de `CHAR(64)` para `VARCHAR(64)` em `V3__refresh_tokens.sql`.**
   O DDL exato do plano (`CHAR(64)`) faz o Postgres reportar o tipo da coluna como
   `bpchar`; o `ddl-auto: validate` do Hibernate rejeita o mapeamento de um campo `String`
   contra essa coluna mesmo com `columnDefinition = "CHAR(64)"` explícito no `@Column`
   (o erro persiste, só muda de "`bpchar` vs `varchar(64)`" para "`bpchar` vs `char(64)`
   classificado como `Types#VARCHAR`" — o Hibernate insiste em tratar `String` como
   `VARCHAR`). Como o conteúdo é sempre um hash SHA-256 hexadecimal de exatamente 64
   caracteres, `VARCHAR(64)` é funcionalmente idêntico e resolve o conflito sem introduzir
   nenhuma capacidade nova. Nenhum critério de aceitação depende do tipo exato da coluna.
2. **Regra extra em `SecurityConfig`: `requestMatchers("/__test__/**").permitAll()`.**
   Não estava no excerto do plano. Sem ela, `ApiExceptionHandlerIT` (teste de F00, na lista
   de "não tocar") passou a falhar: o seu controlador de teste (`/__test__/boom`, nested
   `@RestController` dentro de um `@TestConfiguration`) é apanhado pelo component-scan de
   **qualquer** contexto `*IT` (o classpath de teste fica dentro do pacote base
   `pt.seerhub`), e sem a regra `anyRequest().authenticated()` devolvia `401` antes de o
   pedido chegar ao controlador que lança a exceção — o teste esperava `500`. Este caminho
   nunca existe fora de testes; a regra é inofensiva em produção.
3. **`AuthService.refresh()` precisou de `@Transactional(noRollbackFor = ApiException.class)`.**
   Sem isto, `refreshTokenRepository.revogarFamilia(...)` (o `UPDATE` em bloco que revoga
   toda a família ao detetar reutilização, X1) era desfeito pelo rollback automático do
   Spring quando o método lança `ApiException` logo a seguir — o comportamento por
   omissão do Spring é reverter a transação inteira em qualquer `RuntimeException` não
   marcada. Descoberto porque `TokenRefreshIT.reutilizarUmRefreshTokenJaRodadoDevolve401ERevogaAFamiliaInteira`
   falhava na segunda metade (o sucessor legítimo continuava a servir depois da deteção de
   reuso). Sem esta anotação, a "revogação em cascata" de D-3 seria só decorativa.
4. **`RegisterRequest`/`LoginRequest` ganharam um construtor compacto que faz `trim()` ao
   email.** Bean Validation (`@Email`) corre sobre o `record` já construído, antes de
   `AuthService` sequer ver o pedido; um email com espaços à volta (usado deliberadamente
   pelo teste 1d, `emailENormalizadoAntesDeGuardarEDeVerificarUnicidade`) falhava `@Email`
   com `400` antes de chegar à normalização de D-7. O `trim()` no construtor do `record`
   corre antes da validação (o Jackson invoca o construtor canónico ao desserializar), o
   que resolve sem enfraquecer nenhuma verificação.
5. **A aritmética do plano em §7 está incorreta, não o âmbito.** O plano diz
   "22 + 24 = 46 testes JUnit", mas a sua própria repartição (3+1+5+5+5+2+6+1) soma 28, não
   24; a tabela de critérios da secção 3 (30 linhas de backend, das quais D2 e D3 reutilizam
   nomes já existentes em vez de serem testes novos) implica exatamente 28 testes novos.
   O total real e correto é **22 + 28 = 50**, confirmado pela suite. Nenhuma linha da
   tabela de critérios ficou por implementar; é só a soma do §7 que estava errada.

## Dívidas deixadas

Herdadas do plano (nenhuma resolvida nem agravada por F01):

1. **Sem recuperação de password** — fora do âmbito da v1 (sem envio de email).
2. **Sem limite de tentativas de login** — custo aceite na escala v1.
3. **Access token continua a servir até 15 min depois de a conta ser suspensa** —
   consequência direta de D-5 (sem leitura à base de dados por pedido). O refresh morre de
   imediato; o teto real é de 15 minutos.
4. **Refresh tokens expirados nunca são apagados** — `ix_rt_expires` já existe para uma
   futura tarefa de limpeza (candidata natural: F15 ou a tarefa diária do R3).
5. **Detalhes do health visíveis a qualquer autenticado**, não só `ADMIN` (D-10) —
   `management.endpoint.health.roles` fica por definir; apertar exige um caminho legítimo
   de promoção a `ADMIN` primeiro.
6. **`GET /api/admin/users` sem paginação, pesquisa nem filtro** — F14 é quem tem o
   requisito (R14) e deve substituir este endpoint, não construir sobre ele.
7. **Promoção a `ADMIN` só por SQL direto** (`AuthTestSupport.registarAdmin`) — não há
   endpoint nem seed. F14/F15 devem criar um caminho legítimo.

Novas, introduzidas por F01:

8. **Regra `/__test__/**` em `SecurityConfig`** (ver desvio 2) é conhecimento de um detalhe
   de teste vazando para código de produção. Inofensivo (o caminho nunca existe fora de
   testes), mas se `ApiExceptionHandlerIT` for um dia removido ou renomeado, esta linha
   fica morta e pode ser removida nessa altura.
9. **`npm audit`** — F01 não acrescentou nenhuma dependência npm nova; as vulnerabilidades
   já registadas por F00 (7, incluindo 1 crítica, em dependências de build) continuam por
   investigar, sem agravamento.

## Confirmação sobre `API_KEY` → `API_FOOTBALL_KEY`

**Ainda pendente, por confirmar pelo utilizador.** Esta feature nunca leu nem editou o
`.env` real (regra absoluta do plano — só se raciocina sobre `.env.example`, que já usa
`API_FOOTBALL_KEY` desde F00). F01 não chama a API-Football em nenhum caminho, por isso a
rename não bloqueou nada aqui, tal como não bloqueou F00. **Continua a ser uma ação do
utilizador, obrigatória antes de F05 arrancar**, tal como o handoff de F00 já registava.
Nenhuma feature até agora verificou nem pode verificar se o `.env` real já foi corrigido.

## Avisos para quem vier a seguir

- **O papel por comunidade nunca deve viajar no JWT.** Ver D-4 do plano e a secção
  "Superfície pública" acima — é a restrição estrutural mais importante que F02–F04 herdam.
- **`AuthTestSupport` não tem anotações Spring**: construir sempre explicitamente em
  `@BeforeEach` com as quatro colaborações `@Autowired` da própria classe de teste. Não
  tentar `@Autowired AuthTestSupport` diretamente — não é um bean.
- **Cada teste de integração gera o seu próprio email com `emailUnico(prefixo)`.** O
  contentor Postgres é partilhado e nunca limpo entre classes (herdado de F00); usar
  constantes de email faz os testes dependerem da ordem de execução. A única exceção
  deliberada é `RegistrationIT.registoComEmailJaExistenteDevolve409...`, que usa o email
  literal `ana.silva@exemplo.pt` porque a asserção do critério 3 exige testar exatamente
  esse local-part/domínio (e faz um `DELETE` defensivo antes de registar, para ser
  idempotente entre execuções).
- **`HealthCheckDbDownIT` continua a não estender `AbstractIntegrationTest`** (aviso já
  deixado por F00) — declara o seu próprio contentor Postgres descartável porque o vai
  parar deliberadamente.
- **O endpoint `/api/auth/logout` é idempotente e silencioso por desenho** (D-12): revogar
  um refresh token desconhecido ou de outro utilizador devolve `204` na mesma e não revoga
  nada. Não tratar um `204` de logout como prova de que o token pertencia ao utilizador.
- **`RefreshToken.tokenHash` é `VARCHAR(64)`, não `CHAR(64)`** — ver desvio 1. Se algures
  se vier a precisar de outra coluna de tamanho fixo mapeada por `String`, confirmar
  primeiro com um teste de arranque de contexto (`ddl-auto: validate` falha imediatamente,
  não silenciosamente).
- **Qualquer `@Transactional` que faça um `UPDATE`/operação de escrita e a seguir lance uma
  exceção de negócio precisa de `noRollbackFor` explícito** se a escrita tiver de
  sobreviver ao lançamento — o comportamento por omissão do Spring reverte tudo. Ver
  desvio 3; é fácil escrever este bug de novo em F02+ (ex.: um "banir e recusar" que grava
  um registo de auditoria antes de devolver 403).
