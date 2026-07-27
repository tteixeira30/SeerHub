# F00 — handoff

**Status:** COMPLETE
**Implementado:** 2026-07-27 · Sonnet 5
**Test run:** `./mvnw test` → 22 passados, 0 falhas, 0 erros, 0 ignorados · `cd frontend && npm test` → 4 passados (2 ficheiros de teste), 0 falhas

## O que agora existe

Um monorepo com `backend/` (Spring Boot 3.5.0 / Java 21) e `frontend/`
(React 18 + Vite, servido por nginx), que arranca inteiro com
`docker compose up` — verificado de ponta a ponta nesta máquina: os três
serviços chegam a `healthy`, o health check responde `200 UP` diretamente
no backend e também através do proxy nginx do frontend, e os logs do
backend saem em JSON (ECS) com `correlationId` por pedido. O esquema
completo da secção 8 da spec existe em Postgres 16, aplicado por Flyway,
com `pg_trgm` a funcionar (`similarity()` testado, não só a extensão
presente). Não existe nenhuma entidade JPA, nenhum endpoint de negócio,
nenhuma autenticação — só o esqueleto e a prova de que funciona.

## Superfície pública para a próxima feature (F01)

### Estrutura de pacotes

```
backend/src/main/java/pt/seerhub/
  SeerHubApplication.java        @SpringBootApplication, @EnableScheduling
  config/
    SeerHubProperties.java       @ConfigurationProperties("seerhub"), records Security/Football/Uploads
    SecurityConfig.java          cadeia mínima — ver secção "Segurança" abaixo
  common/
    web/CorrelationIdFilter.java
    error/ApiException.java
    error/ApiExceptionHandler.java
```

Convenção obrigatória para F01 em diante: organização **por feature**, não
por camada. Cada feature cria `pt.seerhub.<feature>` com subpacotes `api`
(controllers + DTOs `record`), `domain` (entidades + enums), `repo`
(interfaces Spring Data), `service`. F01 deve criar `pt.seerhub.user`.

### Propriedades tipadas

`pt.seerhub.config.SeerHubProperties` (record, `@ConfigurationProperties("seerhub")`,
`@Validated`) já expõe:

```java
record Security(@NotBlank String jwtSecret, Duration accessTtl, Duration refreshTtl)
record Football(String apiKey, String baseUrl)
record Uploads(Path dir)
```

F01 injeta `SeerHubProperties.security()` para assinar/validar JWT —
`jwtSecret()`, `accessTtl()` (`PT15M` por omissão), `refreshTtl()` (`P30D`
por omissão). Já está validado que o arranque falha cedo, com mensagem que
nomeia a propriedade, se `jwt-secret` faltar (ver
`SeerHubPropertiesTest.faltaDeJwtSecretImpedeOArranqueComMensagemQueNomeiaAPropriedade`).

### Base de testes de integração

`backend/src/test/java/pt/seerhub/support/AbstractIntegrationTest.java` —
estender esta classe para qualquer `*IT`. Já traz `@SpringBootTest(webEnvironment = RANDOM_PORT)`,
`@ActiveProfiles("test")`, `@AutoConfigureMockMvc`, e um
`PostgreSQLContainer<>("postgres:16-alpine")` estático partilhado (arranca
uma vez por execução da suite; não parar explicitamente, o Ryuk trata da
limpeza). **Só** testes que precisam de simular a base de dados em baixo
declaram o seu próprio contentor descartável — ver
`HealthCheckDbDownIT` como exemplo de padrão.

`backend/src/test/java/pt/seerhub/support/RepoRoot.java` — `RepoRoot.find()`
devolve o `Path` da raiz do monorepo (sobe a partir de `user.dir` até achar
`docker-compose.yml` e `.env.example`). Útil para qualquer teste de
configuração que precise de ler ficheiros fora do classpath do módulo.

### Como lançar um erro de negócio

```java
throw new pt.seerhub.common.error.ApiException(HttpStatus.CONFLICT, "Mensagem em português de Portugal.");
```

`pt.seerhub.common.error.ApiExceptionHandler` converte isso (e qualquer
`MethodArgumentNotValidException`, e qualquer exceção não prevista) num
`ProblemDetail` (RFC 7807) com a propriedade extra `correlationId`. Nunca
devolver a mensagem original de uma exceção não prevista — o handler já
fixa `"Ocorreu um erro inesperado."` para o 500 genérico e regista a
exceção real (com stack trace) no log do servidor.

### Como acrescentar uma migração

Ficheiros em `backend/src/main/resources/db/migration/`, padrão
`V<n>__snake_case.sql` (verificado por `MigrationNamingTest`), **nunca**
editar uma migração já aplicada (o Flyway falha por checksum). O baseline
(`V1__enable_extensions.sql`, `V2__baseline_schema.sql`) cobre exatamente
as 13 tabelas da secção 8 da spec. Regra para as features seguintes,
citada no `CLAUDE.md` da raiz:

> Se a tabela está na secção 8 da spec, já existe: mapeia a entidade
> contra ela e confia em `ddl-auto: validate`. Se não está (`refresh_tokens`
> do R1, `notification_preferences` do R13, `community_reports` do R14),
> a feature que a desenha acrescenta a sua própria migração `V3`, `V4`, ...

**F01 deve criar `V3__refresh_tokens.sql`** (ou nome equivalente) para a
tabela `refresh_tokens` do R1, que não está na secção 8 do modelo de
dados — não a acrescentar ao `V2`.

### Onde vive a cadeia de segurança e o que F01 substitui

`backend/src/main/java/pt/seerhub/config/SecurityConfig.java`:

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
}

@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

**F01 troca só `anyRequest().permitAll()` pelas regras reais** (rotas
públicas de registo/login vs. protegidas por JWT). Mantém
`SessionCreationPolicy.STATELESS` e `csrf.disable()` — a API é stateless
com JWT, não com sessão de servidor. O bean `PasswordEncoder`
(`BCryptPasswordEncoder`) já está pronto para F01 usar no registo.

Nota: sem nenhuma regra de F01, o Spring Security geraria automaticamente
uma password aleatória de admin a cada arranque ("Using generated security
password...") se a cadeia não existisse — é por isso que este ficheiro
existe já em F00 e não pode ser apagado.

### Correlação de pedidos

`pt.seerhub.common.web.CorrelationIdFilter` — constantes públicas
`CorrelationIdFilter.HEADER` (`"X-Correlation-Id"`) e
`CorrelationIdFilter.MDC_KEY` (`"correlationId"`). Qualquer log emitido
durante o tratamento de um pedido HTTP já sai com `correlationId` no JSON
(o formato ECS do Spring Boot inclui o MDC automaticamente — confirmado a
correr o compose real e a inspecionar `docker compose logs backend`).

### Frontend

`frontend/src/lib/api.ts` expõe `apiFetch<T>(path, init?)` — já envia
`X-Correlation-Id` (gerado com `crypto.randomUUID()`) e `Accept:
application/json` em todos os pedidos, e lança `ApiError` (com `status`,
`detail`, `correlationId`) quando a resposta não é OK. F01 deve usá-lo
para os pedidos de registo/login em vez de `fetch` direto. `HealthPage`
(`frontend/src/pages/HealthPage.tsx`) é o único ecrã e serve só de prova;
F01 acrescenta as suas próprias páginas em `frontend/src/pages/` e as
suas rotas em `frontend/src/App.tsx`.

## Ficheiros criados

52 ficheiros conforme a secção 4 do plano, mais `frontend/package-lock.json`
(gerado por `npm install`). Lista completa por área:

| Área | Caminhos principais |
| --- | --- |
| Raiz | `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`, `docker-compose.yml`, `.env.example`, `CLAUDE.md` |
| Backend (produção) | `backend/pom.xml`, `backend/Dockerfile`, `backend/.dockerignore`, `backend/src/main/java/pt/seerhub/SeerHubApplication.java`, `.../config/SeerHubProperties.java`, `.../config/SecurityConfig.java`, `.../common/web/CorrelationIdFilter.java`, `.../common/error/ApiException.java`, `.../common/error/ApiExceptionHandler.java`, `backend/src/main/resources/application.yml`, `application-local.yml`, `db/migration/V1__enable_extensions.sql`, `V2__baseline_schema.sql` |
| Backend (testes) | `backend/src/test/java/pt/seerhub/support/AbstractIntegrationTest.java`, `RepoRoot.java`, `health/HealthCheckIT.java`, `health/HealthCheckDbDownIT.java`, `migration/FlywayBaselineIT.java`, `migration/MigrationNamingTest.java`, `common/web/CorrelationIdFilterTest.java`, `common/error/ApiExceptionHandlerIT.java`, `config/SeerHubPropertiesTest.java`, `config/ConfigurationConventionsTest.java`, `config/EnvExampleTest.java`, `config/DockerComposeTest.java`, `backend/src/test/resources/application-test.yml` |
| Frontend | `frontend/package.json`, `tsconfig.json`, `tsconfig.node.json`, `vite.config.ts`, `vitest.setup.ts`, `index.html`, `tailwind.config.js`, `postcss.config.js`, `nginx.conf`, `Dockerfile`, `.dockerignore`, `src/main.tsx`, `src/App.tsx`, `src/index.css`, `src/vite-env.d.ts`, `src/lib/api.ts`, `src/pages/HealthPage.tsx`, `src/App.test.tsx`, `src/lib/api.test.ts` |

## Ficheiros editados

| Caminho | Alteração |
| --- | --- |
| `.gitignore` | Acrescentadas 3 linhas (`*.tsbuildinfo`, `/frontend/vite.config.js`, `/frontend/vite.config.d.ts`) para cobrir artefactos gerados pelo `tsc -b` do frontend que não estavam previstos no plano. Nenhuma linha existente foi apagada. |

## Testes

| Teste | Cobre critério | Resultado |
| --- | --- | --- |
| `DockerComposeTest.declaraOsTresServicosDaSpec` | R15.1a | passou |
| `DockerComposeTest.backendEsperaPelaBaseDeDadosSaudavelEFrontendPeloBackend` | R15.1b | passou |
| `DockerComposeTest.declaraVolumesPersistentesDeDadosEUploads` | R15.1c | passou |
| `FlywayBaselineIT.migracoesAplicamEsquemaCompletoDoModeloDeDados` | R15.2a | passou |
| `ConfigurationConventionsTest.nenhumPerfilUsaAutoDdlGeradorDeEsquema` | R15.2b | passou |
| `EnvExampleTest.todaVariavelUsadaEmConfiguracaoOuComposeEstaNoEnvExample` | R15.3a | passou |
| `EnvExampleTest.todaVariavelDoEnvExampleEUsadaEmConfiguracaoOuCompose` | R15.3b | passou |
| `EnvExampleTest.variaveisDeSegredoTemApenasPlaceholders` | R15.3c | passou |
| `ConfigurationConventionsTest.segredosSaoSempreReferenciasAVariaveisDeAmbiente` | R15.4a | passou |
| `ConfigurationConventionsTest.nenhumFicheiroVersionadoContemSegredoComAparenciaReal` | R15.4b | passou |
| `ConfigurationConventionsTest.gitignoreIgnoraOEnvEPermiteOEnvExample` | R15.4c | passou |
| `HealthCheckIT.healthDevolve200ComComponenteDbUp` | R15.5a | passou |
| `App.test.tsx :: mostraOEstadoDoServicoDevolvidoPelaApi` | R15.5b | passou |
| `ConfigurationConventionsTest.perfilPorOmissaoEmiteLogsEstruturados` | R15.6a | passou |
| `CorrelationIdFilterTest.geraCorrelationIdQuandoOPedidoNaoTrazENoLimpaOMdcNoFim` | R15.6b | passou |
| `CorrelationIdFilterTest.reutilizaOCorrelationIdRecebidoDoCliente` | R15.6c | passou |
| `ApiExceptionHandlerIT.excecaoNaoTratadaDevolve500SemStackTraceComCorrelationId` | R15.6d / X3 | passou |
| `api.test.ts :: enviaCabecalhoXCorrelationIdEmCadaPedido` | R15.6e | passou |
| `FlywayBaselineIT.extensaoPgTrgmEIndicesDeTrigramasExistem` | E1 | passou |
| `MigrationNamingTest.todosOsFicheirosDeMigracaoSeguemAConvencaoEVersaoUnica` | E2 | passou |
| `SeerHubPropertiesTest.ligaTodasAsPropriedadesQuandoOAmbienteEstaCompleto` | E3 | passou |
| `App.test.tsx :: renderizaOTituloDaAplicacao` | E4 | passou |
| `HealthCheckDbDownIT.comBaseDeDadosParadaHealthDevolve503EComponenteDbDown` | X1 | passou |
| `SeerHubPropertiesTest.faltaDeJwtSecretImpedeOArranqueComMensagemQueNomeiaAPropriedade` | X2 | passou |

Total: 22 testes JUnit (backend) + 4 testes Vitest (frontend) = 26,
todos verdes. Dois testes adicionais, não pedidos pela tabela do plano
mas cobrindo prosa explícita da secção 3, foram também escritos e
passam: `DockerComposeTest.nenhumValorDeEnvironmentEUmLiteral` (parte da
verificação "todos são referências `${VAR}`") e
`api.test.ts :: lancaApiErrorComDetailECorrelationIdQuandoARespostaNaoEOk`.

## Desvios face ao plano

1. **`snakeyaml` retirado do `backend/pom.xml`.** O plano listava
   `org.yaml:snakeyaml` como dependência de teste explícita, para o
   `DockerComposeTest` fazer parse do `docker-compose.yml`. Declarar uma
   dependência diretamente com `<scope>test</scope>` faz a mediação do
   Maven **sobrepor-se** à versão/escopo que já vem transitivamente de
   `spring-boot-starter` (que traz `snakeyaml` em `compile`, necessário em
   runtime para o próprio Spring Boot ler `application.yml`). O sintoma só
   apareceu no arranque real em contentor (passo 4 da verificação): o jar
   empacotado excluía o `snakeyaml` por estar "apenas" em `test`, e a
   aplicação falhava a arrancar com `snakeyaml was not found on the
   classpath`. Os testes Maven passavam na mesma (o classpath de teste
   inclui tudo). Correção: remover a dependência explícita — o
   `snakeyaml` chega transitivamente, em `compile`, via
   `spring-boot-starter`, e continua disponível para o `DockerComposeTest`
   sem reintroduzir o problema. **Lição para quem for adicionar uma
   dependência que o Spring Boot já traz transitivamente: nunca a
   declarar de novo com um escopo mais restrito — verificar primeiro com
   `mvn dependency:tree -Dincludes=<groupId>:<artifactId>`.**
2. **`@types/node` acrescentado ao frontend.** O plano não o listava, mas
   `npm run build` (`tsc -b && vite build`) falhava a compilar
   `vite.config.ts` (`node:path`, `__dirname`) sem tipos de Node. Sem
   isto, o build de produção do frontend (usado pelo `Dockerfile`) nunca
   teria sido testado — só foi detetado ao correr o passo 4 da
   verificação (`docker compose up --build`), não pelo `npm test`
   isolado. Acrescentado como `devDependency` e em
   `tsconfig.node.json` (`"types": ["node"]`).
3. **Dois testes extra não nomeados pela tabela da secção 3**, ambos
   cobrindo prosa explícita do plano — ver secção "Testes" acima.
4. **Porta 8080 ocupada nesta máquina** por um contentor de outro
   projeto (`tracky-backend`). A verificação do passo 4 correu com
   `BACKEND_PORT=18080 FRONTEND_PORT=15173` só como variáveis de shell
   (não editei `.env.example`, que continua com `8080`/`5173` conforme o
   plano). É um conflito de ambiente local, não do plano.

Nenhum desvio contraria a spec.

## Dívidas deixadas

1. **`management.endpoint.health.show-details: always`** em
   `backend/src/main/resources/application.yml` — necessário para o
   `HealthCheckIT` ver o componente `db`, mas expõe publicamente o tipo de
   base de dados assim que houver acesso de fora. **Dívida deliberada do
   plano, com dono nomeado: F01 deve trocar para `when-authorized` e
   adaptar `HealthCheckIT` para autenticar.**
2. **Renomear `API_KEY` para `API_FOOTBALL_KEY` no `.env` real do
   utilizador antes de F05.** O `.env` local (não lido, não editado por
   mim) usa `API_KEY`; este plano e todo o código normalizam para
   `API_FOOTBALL_KEY`. F00–F04 nunca chamam a API-Football, por isso isto
   não bloqueia nada até F05. **Ação do utilizador:** editar `.env`
   (nunca `.env.example`) e trocar o nome da variável antes de F05
   arrancar.
3. **`SecurityConfig` com `anyRequest().permitAll()`.** Esperado — é
   esqueleto, F01 é a próxima feature e tem o R1 inteiro dedicado a
   substituir isto.
4. **Vulnerabilidades reportadas por `npm audit`** (7, incluindo 1
   crítica) nas dependências transitivas do toolchain de build do
   Vite/Vitest instaladas nesta feature. Não investigadas — são
   dependências de desenvolvimento (build/test), não código servido a
   utilizadores, mas ficam registadas para quem quiser correr
   `npm audit fix` numa feature futura.

## Avisos para quem vier a seguir

- **Nunca declarar de novo, com escopo restrito, uma dependência que já
  vem transitivamente do Spring Boot** (ver desvio 1 acima). Se precisar
  de confirmar que algo está disponível em teste, use
  `mvn dependency:tree -Dincludes=<groupId>:<artifactId>` antes de
  acrescentar a dependência.
- **`HealthCheckDbDownIT` declara o seu próprio `PostgreSQLContainer`** e
  usa `@DirtiesContext(classMode = AFTER_CLASS)` — não o mude para
  estender `AbstractIntegrationTest`, isso pararia o contentor partilhado
  por todas as outras `*IT` da suite.
- **`spring.datasource.hikari.connection-timeout: 2000` está fixado só no
  perfil `test`** (`backend/src/test/resources/application-test.yml`),
  para o `HealthCheckDbDownIT` falhar depressa (~4s em vez dos 30s por
  omissão do Hikari). Não copiar este valor para `application.yml` de
  produção sem pensar — em produção um timeout tão curto pode gerar falsos
  negativos sob carga.
- **`docker-compose.yml` usa `${DB_HOST}`/`${DB_PORT}` para o backend**,
  não os valores literais `db`/`5432`, mesmo sendo a topologia de rede
  fixa — é o `DockerComposeTest.nenhumValorDeEnvironmentEUmLiteral` a
  impor isto, e o `.env.example` já traz `DB_HOST=db` como valor por
  omissão coerente com o nome do serviço no compose.
- **O contexto de build de cada `Dockerfile` é o próprio módulo**
  (`./backend`, `./frontend`), não a raiz do monorepo — `backend/pom.xml`
  constrói sozinho, sem precisar do POM agregador da raiz.
- Testado nesta máquina com `docker compose --env-file .env.example up
  -d --build`: os três serviços chegam a `healthy`; curl a
  `http://localhost:<BACKEND_PORT>/actuator/health` e a
  `http://localhost:<FRONTEND_PORT>/actuator/health` (via proxy nginx)
  devolvem `200` com `"status":"UP"`; `docker compose logs backend`
  mostra JSON ECS válido com `correlationId` em cada linha de pedido;
  `docker compose down -v` remove tudo. Passo 4–8 da verificação do
  plano, portanto, **confirmados**, não apenas "melhor esforço" — só a
  porta teve de ser remapeada por conflito local, sem tocar em
  `.env.example`.
