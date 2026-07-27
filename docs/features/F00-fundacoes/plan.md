# F00 — Fundações e esqueleto

**Requisitos:** R15 (todos os critérios exceto o seed de desenvolvimento, que é F15)
**Depende de:** —
**Planeado:** 2026-07-27 · Opus 5

## 1. Objetivo

Depois desta feature existe um monorepo que arranca inteiro com `docker compose up` (Postgres 16 com `pg_trgm`, backend Spring Boot 3 / Java 21, frontend React 18 + Vite servido por nginx) e uma suite de testes verde que corre **offline, sem rede e sem chave de API**: `./mvnw test` na raiz e `npm test` em `frontend/`. O esquema completo do §8 da spec existe em Postgres, criado por Flyway, com os índices de trigramas prontos para o R6. Há um health check que confirma a base de dados, logs estruturados em JSON com `correlationId` por pedido, e um handler global que devolve RFC 7807 sem stack trace. Não existe nenhuma entidade de negócio, nenhum endpoint de negócio e nenhum ecrã de negócio — apenas o esqueleto e a prova de que funciona.

## 2. Contexto herdado

Nenhum. F00 é a primeira feature do run; não há handoffs para ler. O que existe no repositório antes de começar:

| Caminho | O que é |
| --- | --- |
| `docs/specs/seerhub.md` | A spec. Fonte de verdade. Só de leitura. |
| `seerhub.md` | O brief original. Só de leitura. |
| `docs/features/BACKLOG.md`, `docs/features/CHANGELOG.md` | Livro-razão do run. Só o orquestrador escreve. |
| `.gitignore` | Já cobre `.env`, `target/`, `node_modules/`, `dist/`, `/uploads/`, `postgres-data/`. |
| `.env` | **Existe e contém uma chave real da API-Football.** Está ignorado pelo git. Nunca ler, editar, copiar nem commitar. |
| `.claude/settings.local.json` | Configuração da ferramenta. Não tocar. |

Ambiente verificado nesta máquina: Java 21.0.7, Maven 3.9.10, Node 22.16.0, npm 10.9.2, Docker 28.1.1 a correr, imagem `postgres:16-alpine` já em cache local (o que garante que os testes de integração com Testcontainers correm sem rede).

Dívida herdada: o `.env` local usa `API_KEY` como nome da variável. Este plano normaliza para `API_FOOTBALL_KEY`. Ver secção 9.

## 3. Critérios de aceitação → testes

Legenda de tipo: `unit` = sem contexto Spring; `slice` = contexto parcial (`ApplicationContextRunner`); `int` = `@SpringBootTest` + Testcontainers; `front` = Vitest.

| # | Critério | Ficheiro de teste | Nome do teste | Tipo |
| --- | --- | --- | --- | --- |
| R15.1a | `docker compose up` arranca Postgres, backend e frontend | `backend/src/test/java/pt/seerhub/config/DockerComposeTest.java` | `declaraOsTresServicosDaSpec` | unit |
| R15.1b | Nenhum passo manual além de preencher o `.env`: ordem de arranque garantida | `backend/src/test/java/pt/seerhub/config/DockerComposeTest.java` | `backendEsperaPelaBaseDeDadosSaudavelEFrontendPeloBackend` | unit |
| R15.1c | Volumes persistentes de dados e de uploads declarados (§9 da spec) | `backend/src/test/java/pt/seerhub/config/DockerComposeTest.java` | `declaraVolumesPersistentesDeDadosEUploads` | unit |
| R15.2a | Esquema gerido por Flyway | `backend/src/test/java/pt/seerhub/migration/FlywayBaselineIT.java` | `migracoesAplicamEsquemaCompletoDoModeloDeDados` | int |
| R15.2b | Nenhuma alteração de esquema por auto-DDL do Hibernate fora de local | `backend/src/test/java/pt/seerhub/config/ConfigurationConventionsTest.java` | `nenhumPerfilUsaAutoDdlGeradorDeEsquema` | unit |
| R15.3a | `.env.example` tem **todas** as variáveis necessárias | `backend/src/test/java/pt/seerhub/config/EnvExampleTest.java` | `todaVariavelUsadaEmConfiguracaoOuComposeEstaNoEnvExample` | unit |
| R15.3b | `.env.example` não tem variáveis a mais (sem lixo por copiar) | `backend/src/test/java/pt/seerhub/config/EnvExampleTest.java` | `todaVariavelDoEnvExampleEUsadaEmConfiguracaoOuCompose` | unit |
| R15.3c | Nenhum segredo real no `.env.example` | `backend/src/test/java/pt/seerhub/config/EnvExampleTest.java` | `variaveisDeSegredoTemApenasPlaceholders` | unit |
| R15.4a | Chave da API-Football e segredo JWT vêm exclusivamente de variáveis de ambiente | `backend/src/test/java/pt/seerhub/config/ConfigurationConventionsTest.java` | `segredosSaoSempreReferenciasAVariaveisDeAmbiente` | unit |
| R15.4b | Nenhum segredo commitado em qualquer ficheiro versionado | `backend/src/test/java/pt/seerhub/config/ConfigurationConventionsTest.java` | `nenhumFicheiroVersionadoContemSegredoComAparenciaReal` | unit |
| R15.4c | `.env` continua ignorado pelo git | `backend/src/test/java/pt/seerhub/config/ConfigurationConventionsTest.java` | `gitignoreIgnoraOEnvEPermiteOEnvExample` | unit |
| R15.5a | Health check devolve 200 e verifica a base de dados | `backend/src/test/java/pt/seerhub/health/HealthCheckIT.java` | `healthDevolve200ComComponenteDbUp` | int |
| R15.5b | O frontend consegue ler o health check pelo mesmo host (proxy) | `frontend/src/App.test.tsx` | `mostraOEstadoDoServicoDevolvidoPelaApi` | front |
| R15.6a | Logs estruturados (JSON) configurados fora de local | `backend/src/test/java/pt/seerhub/config/ConfigurationConventionsTest.java` | `perfilPorOmissaoEmiteLogsEstruturados` | unit |
| R15.6b | Correlação de pedido: id gerado quando ausente, propagado no MDC e no cabeçalho | `backend/src/test/java/pt/seerhub/common/web/CorrelationIdFilterTest.java` | `geraCorrelationIdQuandoOPedidoNaoTrazENoLimpaOMdcNoFim` | unit |
| R15.6c | Correlação de pedido: id recebido do cliente é respeitado | `backend/src/test/java/pt/seerhub/common/web/CorrelationIdFilterTest.java` | `reutilizaOCorrelationIdRecebidoDoCliente` | unit |
| R15.6d | Erros não expõem stack traces ao cliente | `backend/src/test/java/pt/seerhub/common/error/ApiExceptionHandlerIT.java` | `excecaoNaoTratadaDevolve500SemStackTraceComCorrelationId` | int |
| R15.6e | O frontend envia sempre o correlation id | `frontend/src/lib/api.test.ts` | `enviaCabecalhoXCorrelationIdEmCadaPedido` | front |
| E1 | Harness de integração real: Postgres com `pg_trgm` sem rede nem chave | `backend/src/test/java/pt/seerhub/migration/FlywayBaselineIT.java` | `extensaoPgTrgmEIndicesDeTrigramasExistem` | int |
| E2 | Convenção de migrações verificável pelas 16 features seguintes | `backend/src/test/java/pt/seerhub/migration/MigrationNamingTest.java` | `todosOsFicheirosDeMigracaoSeguemAConvencaoEVersaoUnica` | unit |
| E3 | Propriedades tipadas da aplicação ligam corretamente | `backend/src/test/java/pt/seerhub/config/SeerHubPropertiesTest.java` | `ligaTodasAsPropriedadesQuandoOAmbienteEstaCompleto` | slice |
| E4 | Frontend renderiza e o runner de testes funciona | `frontend/src/App.test.tsx` | `renderizaOTituloDaAplicacao` | front |
| **X1** | **Falha: base de dados em baixo** → health degrada, aplicação não rebenta | `backend/src/test/java/pt/seerhub/health/HealthCheckDbDownIT.java` | `comBaseDeDadosParadaHealthDevolve503EComponenteDbDown` | int |
| **X2** | **Falha: variável de ambiente obrigatória em falta** → arranque falha depressa com mensagem que nomeia a propriedade | `backend/src/test/java/pt/seerhub/config/SeerHubPropertiesTest.java` | `faltaDeJwtSecretImpedeOArranqueComMensagemQueNomeiaAPropriedade` | slice |
| **X3** | **Falha: exceção não tratada chega ao cliente** → corpo tem correlation id, não tem stack trace nem mensagem interna | `backend/src/test/java/pt/seerhub/common/error/ApiExceptionHandlerIT.java` | `excecaoNaoTratadaDevolve500SemStackTraceComCorrelationId` | int |

Nenhum critério em âmbito fica sem teste. Notas sobre os três critérios tradicionalmente dados como "verificados manualmente":

- **`docker compose up`** — o arranque real não é executado dentro da suite (arrancar três contentores e esperar por saúde dentro de `mvn test` tornaria a suite lenta e dependente de build de imagens em todas as 16 features seguintes). O que é automatizado é tudo o que pode falhar por engano: `DockerComposeTest` faz o *parse* do `docker-compose.yml` com SnakeYAML e afirma que existem os serviços `db`, `backend` e `frontend`, que `backend.depends_on.db.condition == service_healthy`, que `frontend.depends_on.backend.condition == service_healthy`, que `db` tem `healthcheck`, que existem os volumes nomeados `pgdata` e `uploads`, e que nenhum valor de `environment` é um literal — todos são referências `${VAR}`. O arranque de ponta a ponta é verificado uma vez, por comando explícito, na secção 7.
- **Completude do `.env.example`** — automatizada nos dois sentidos por `EnvExampleTest`, que extrai com regex todos os placeholders `${VAR}` / `${VAR:default}` / `${VAR:-default}` de `backend/src/main/resources/application*.yml` e de `docker-compose.yml` e compara com as chaves de `.env.example`.
- **Ausência de segredos** — automatizada por `ConfigurationConventionsTest`, que (a) afirma que as propriedades de segredo são placeholders e não literais, (b) varre `.env.example`, `docker-compose.yml` e `backend/src/main/resources/**` à procura de sequências com aparência de segredo real (`[0-9a-f]{32,}`, `[A-Za-z0-9_\-]{40,}` fora de comentários e URLs) e falha se encontrar alguma, e (c) afirma que `.gitignore` contém `.env` e a exceção `!.env.example`.

## 4. Alterações

### Ficheiros a criar

**Raiz (7)**

| Caminho | Propósito |
| --- | --- |
| `pom.xml` | POM agregador (`packaging=pom`, sem parent, `<modules><module>backend</module></modules>`). Existe para que `./mvnw test` na raiz corra a suite do backend, como prometido no backlog. |
| `mvnw` | Wrapper Maven (gerado por `mvn -N wrapper:wrapper -Dmaven=3.9.10`). |
| `mvnw.cmd` | Idem, Windows. |
| `.mvn/wrapper/maven-wrapper.properties` | Idem. |
| `docker-compose.yml` | Postgres 16, backend, frontend, volumes `pgdata` e `uploads`. |
| `.env.example` | Todas as variáveis, só placeholders. |
| `CLAUDE.md` | Convenções do repositório para todas as features seguintes. Conteúdo obrigatório na secção 5, passo 9. |

**Backend (13)**

| Caminho | Propósito |
| --- | --- |
| `backend/pom.xml` | Projeto Spring Boot. Parent = `spring-boot-starter-parent:3.5.0`. `groupId=pt.seerhub`, `artifactId=seerhub-backend`, `java.version=21`. |
| `backend/Dockerfile` | Multi-stage: `maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre-alpine`. `HEALTHCHECK` com `wget -qO- http://localhost:8080/actuator/health`. |
| `backend/.dockerignore` | `target/`, `.git`, `*.md`. |
| `backend/src/main/java/pt/seerhub/SeerHubApplication.java` | `@SpringBootApplication`, `@EnableConfigurationProperties(SeerHubProperties.class)`, `@EnableScheduling` (F05/F03/F13 precisam; sem tarefas ainda). |
| `backend/src/main/java/pt/seerhub/config/SeerHubProperties.java` | `@ConfigurationProperties("seerhub")` + `@Validated`. Record aninhado: `Security(String jwtSecret /* @NotBlank */, Duration accessTtl, Duration refreshTtl)`, `Football(String apiKey, String baseUrl)`, `Uploads(Path dir)`. |
| `backend/src/main/java/pt/seerhub/config/SecurityConfig.java` | Cadeia mínima: `sessionCreationPolicy(STATELESS)`, `csrf.disable()`, `authorizeHttpRequests().anyRequest().permitAll()`, `BCryptPasswordEncoder` como bean. Comentário no topo: **F01 substitui as regras; não apagar a cadeia, editar as regras.** |
| `backend/src/main/java/pt/seerhub/common/web/CorrelationIdFilter.java` | `OncePerRequestFilter` com `@Order(HIGHEST_PRECEDENCE)`. Lê `X-Correlation-Id`; se ausente ou inválido gera `UUID.randomUUID()`. Põe em `MDC["correlationId"]`, escreve o cabeçalho na resposta, limpa o MDC em `finally`. Expõe `public static final String HEADER` e `MDC_KEY`. |
| `backend/src/main/java/pt/seerhub/common/error/ApiException.java` | `RuntimeException` com `HttpStatus status` e `String detail`. Base de todos os erros de negócio das features seguintes. |
| `backend/src/main/java/pt/seerhub/common/error/ApiExceptionHandler.java` | `@RestControllerAdvice`. `ApiException` → `ProblemDetail` com o status próprio; `MethodArgumentNotValidException` → 400 com lista de campos; `Exception` → 500 com `detail` fixo `"Ocorreu um erro inesperado."`. Todos acrescentam a propriedade `correlationId` lida do MDC. O 500 **regista** a exceção com stack trace no log do servidor e **nunca** a devolve. |
| `backend/src/main/resources/application.yml` | Perfil por omissão (contentor). Conteúdo exato abaixo. |
| `backend/src/main/resources/application-local.yml` | Perfil `local`: logs em texto legível, `show-sql` a falso, `DB_HOST` a `localhost`. |
| `backend/src/main/resources/db/migration/V1__enable_extensions.sql` | `pg_trgm` e `unaccent`. |
| `backend/src/main/resources/db/migration/V2__baseline_schema.sql` | Esquema completo do §8. DDL exato abaixo. |

**Testes do backend (13)**

| Caminho | Propósito |
| --- | --- |
| `backend/src/test/java/pt/seerhub/support/AbstractIntegrationTest.java` | Base de todos os `*IT`. Contentor `PostgreSQLContainer<>("postgres:16-alpine")` **estático e partilhado** (padrão singleton, `start()` num bloco estático, sem `stop()` — o Ryuk trata da limpeza), ligado com `@ServiceConnection`. `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@ActiveProfiles("test")`, `@AutoConfigureMockMvc`. |
| `backend/src/test/java/pt/seerhub/support/RepoRoot.java` | `static Path find()` — sobe a partir de `user.dir` até encontrar um diretório que contenha `docker-compose.yml` e `.env.example`. Usado pelos testes de configuração. |
| `backend/src/test/java/pt/seerhub/health/HealthCheckIT.java` | R15.5a. |
| `backend/src/test/java/pt/seerhub/health/HealthCheckDbDownIT.java` | X1. **Declara o seu próprio contentor**, não usa o partilhado. `@DirtiesContext(classMode = AFTER_CLASS)`. Arranca, para o contentor, invoca `/actuator/health`, espera 503 e `components.db.status == DOWN`. |
| `backend/src/test/java/pt/seerhub/migration/FlywayBaselineIT.java` | R15.2a e E1. |
| `backend/src/test/java/pt/seerhub/migration/MigrationNamingTest.java` | E2. |
| `backend/src/test/java/pt/seerhub/common/web/CorrelationIdFilterTest.java` | R15.6b/c, com `MockHttpServletRequest`/`Response` e `MockFilterChain`. |
| `backend/src/test/java/pt/seerhub/common/error/ApiExceptionHandlerIT.java` | R15.6d / X3. Inclui uma `@TestConfiguration` interna com um `@RestController` em `/__test__/boom` que lança `IllegalStateException("segredo interno")`; o teste afirma que o corpo **não contém** `"segredo interno"`, `"Exception"`, `"at pt.seerhub"` nem `"trace"`, e **contém** `correlationId`. |
| `backend/src/test/java/pt/seerhub/config/SeerHubPropertiesTest.java` | E3 e X2, com `ApplicationContextRunner`. |
| `backend/src/test/java/pt/seerhub/config/ConfigurationConventionsTest.java` | R15.2b, R15.4a/b/c, R15.6a. |
| `backend/src/test/java/pt/seerhub/config/EnvExampleTest.java` | R15.3a/b/c. |
| `backend/src/test/java/pt/seerhub/config/DockerComposeTest.java` | R15.1a/b/c. |
| `backend/src/test/resources/application-test.yml` | Perfil `test`: fornece `seerhub.security.jwt-secret` fixo, `logging.structured.format.console` desligado, `spring.jpa.hibernate.ddl-auto: validate`. |

**Frontend (19)**

| Caminho | Propósito |
| --- | --- |
| `frontend/package.json` | Scripts `dev`, `build`, `preview`, `test` (`vitest run`), `test:watch`, `lint`, `typecheck` (`tsc --noEmit`). |
| `frontend/tsconfig.json` | `strict: true`, `paths: { "@/*": ["./src/*"] }`. |
| `frontend/tsconfig.node.json` | Para `vite.config.ts`. |
| `frontend/vite.config.ts` | Plugin React, alias `@`, `server.proxy` de `/api`, `/actuator` e `/ws` para `http://localhost:8080`, e bloco `test` do Vitest (`environment: 'jsdom'`, `setupFiles: ['./vitest.setup.ts']`, `globals: true`). |
| `frontend/vitest.setup.ts` | `import '@testing-library/jest-dom/vitest'`. |
| `frontend/index.html` | Raiz da SPA, `lang="pt-PT"`. |
| `frontend/tailwind.config.js` | `content: ['./index.html','./src/**/*.{ts,tsx}']`. |
| `frontend/postcss.config.js` | tailwindcss + autoprefixer. |
| `frontend/nginx.conf` | `try_files $uri /index.html`; `location /api`, `/actuator` e `/ws` → `proxy_pass http://backend:8080` com `Upgrade`/`Connection` para o WebSocket do F12/F13. |
| `frontend/Dockerfile` | `node:22-alpine` (`npm ci && npm run build`) → `nginx:1.27-alpine` com o `nginx.conf`. |
| `frontend/.dockerignore` | `node_modules`, `dist`. |
| `frontend/src/main.tsx` | `QueryClientProvider` + `BrowserRouter` + `<App/>`. |
| `frontend/src/App.tsx` | Router com uma rota `/` → `HealthPage`. |
| `frontend/src/index.css` | Diretivas do Tailwind. |
| `frontend/src/vite-env.d.ts` | Tipos do Vite. |
| `frontend/src/lib/api.ts` | `apiFetch<T>(path, init?)`: prefixo `import.meta.env.VITE_API_BASE_URL ?? ''`, gera `crypto.randomUUID()` e envia-o em `X-Correlation-Id`, faz `Accept: application/json`, e converte respostas não-OK num `ApiError` que lê `detail` e `correlationId` do ProblemDetail. |
| `frontend/src/pages/HealthPage.tsx` | Usa `useQuery` para `GET /actuator/health` e mostra `UP`/`DOWN`. É a página trivial que prova a cadeia toda. |
| `frontend/src/App.test.tsx` | R15.5b e E4. `fetch` mocado com `vi.stubGlobal`. |
| `frontend/src/lib/api.test.ts` | R15.6e. |

Total: **52 ficheiros a criar** (mais `frontend/package-lock.json`, gerado por `npm install`).

### Ficheiros a editar

| Caminho | Alteração | Risco |
| --- | --- | --- |
| `.gitignore` | Acrescentar `.mvn/wrapper/maven-wrapper.jar` já está coberto; acrescentar `!frontend/package-lock.json` não é preciso; acrescentar `/backend/target/` é redundante com `target/`. **Editar apenas se o wrapper gerado produzir artefactos não cobertos.** Não apagar nenhuma linha existente. | Baixo |

### Modelo de dados / migrações

**Decisão: esquema completo do §8 no baseline, com uma regra explícita para as features seguintes.**

`V2__baseline_schema.sql` cria **exatamente as entidades que o §8 da spec congelou** — nem mais, nem menos. A regra que fica registada no `CLAUDE.md` e que todas as 16 features seguintes seguem é:

> Se a tabela está no §8 da spec, já existe: mapeia a entidade contra ela e confia em `ddl-auto: validate`. Se não está no §8 (por exemplo `refresh_tokens` do R1, `notification_preferences` do R13, `community_reports` do R14), a feature que a desenha acrescenta a sua própria migração `V<n>__<descrição>.sql`. Nunca se edita uma migração já existente.

Porquê, e o que custa: o grafo de chaves estrangeiras do §8 atravessa a ordem das features (`tip_selections.fixture_id` liga F07/F08 a F05; `team_aliases.community_id` liga F06b a F02; `tips.community_id` liga F07 a F02). Com migração-por-feature, cada planeador re-deriva DDL do §8 de forma independente, e a divergência de nomes, tipos e índices entre 16 agentes sem contexto partilhado é a falha mais provável do run — e a mais cara, porque só se manifesta quando duas features se encontram. Com o baseline completo há **uma** tradução canónica do §8, e `spring.jpa.hibernate.ddl-auto: validate` transforma-a num contrato: qualquer entidade mal mapeada rebenta o contexto no primeiro teste de integração dessa feature. O custo é ter tabelas vazias durante algumas semanas, que é zero em runtime. O risco é o baseline estar errado nalgum ponto; mitiga-se com `ALTER TABLE` numa migração da feature afetada, sobre uma base de dados sem produção.

Desvios conscientes face ao texto literal do §8, todos comentados no próprio ficheiro SQL:

1. **`version BIGINT NOT NULL DEFAULT 0`** em `tips`, `tip_selections` e `community_memberships` — o caso de fronteira "duas resoluções concorrentes da mesma seleção → bloqueio otimista" (§10) exige-o. Acrescentar depois obrigaria a `ALTER` com dados.
2. **`tip_selections.fixture_id` é NULL** — o caso de fronteira "API-Football sem os jogos de uma liga menor → o tipster pode publicar com o jogo por associar" (§10) exige-o.
3. **Enumerados como `VARCHAR` + `CHECK`**, não tipos `ENUM` nativos — alterar um `ENUM` nativo do Postgres é doloroso e vai acontecer (R8 fala em acrescentar mercados).
4. **`UNIQUE NULLS NOT DISTINCT (normalized_alias, community_id)`** em `team_aliases` — sem `NULLS NOT DISTINCT` (Postgres 15+) a restrição do §8 não deduplica os aliases globais, porque `NULL` é distinto de `NULL` num índice único. É um bug silencioso do modelo tal como escrito.
5. **Identidades `BIGINT GENERATED BY DEFAULT AS IDENTITY`** — o §8 não fixa o tipo do `id`. Escolhido por simplicidade e por os identificadores públicos serem `slug` (comunidades) e não o id.
6. **Timestamps `TIMESTAMPTZ`** — a spec exige UTC; `TIMESTAMPTZ` é a única forma de o Postgres o garantir.

`V1__enable_extensions.sql`:

```sql
-- As duas extensões são criadas na migração (e não num script de init do
-- contentor) para que o Testcontainers e o docker compose obtenham
-- exatamente o mesmo esquema sem duplicação de configuração.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;
```

`V2__baseline_schema.sql`:

```sql
-- Baseline do modelo de dados da secção 8 da spec.
-- Regra: esta migração nunca é editada. Alterações vêm em V3+.
-- Timestamps em UTC (TIMESTAMPTZ), dinheiro em cêntimos inteiros,
-- odds em decimal, enumerados como VARCHAR + CHECK.

CREATE TABLE users (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    username      VARCHAR(50)  NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    avatar_url    VARCHAR(500),
    global_role   VARCHAR(20)  NOT NULL DEFAULT 'USER',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email     UNIQUE (email),
    CONSTRAINT uq_users_username  UNIQUE (username),
    CONSTRAINT ck_users_role      CHECK (global_role IN ('USER','ADMIN')),
    CONSTRAINT ck_users_status    CHECK (status IN ('ACTIVE','SUSPENDED'))
);

CREATE TABLE communities (
    id                  BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    owner_id            BIGINT       NOT NULL REFERENCES users(id),
    name                VARCHAR(60)  NOT NULL,
    slug                VARCHAR(80)  NOT NULL,
    description         VARCHAR(2000),
    avatar_url          VARCHAR(500),
    banner_url          VARCHAR(500),
    price_monthly_cents INTEGER      NOT NULL DEFAULT 0,
    currency            CHAR(3)      NOT NULL DEFAULT 'EUR',
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_communities_slug   UNIQUE (slug),
    CONSTRAINT ck_communities_price  CHECK (price_monthly_cents >= 0),
    CONSTRAINT ck_communities_name   CHECK (char_length(name) BETWEEN 3 AND 60),
    CONSTRAINT ck_communities_status CHECK (status IN ('ACTIVE','SUSPENDED'))
);
CREATE INDEX ix_communities_owner  ON communities (owner_id);
CREATE INDEX ix_communities_status ON communities (status);

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

CREATE TABLE leagues (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    provider_id BIGINT       NOT NULL,
    name        VARCHAR(120) NOT NULL,
    country     VARCHAR(80),
    logo_url    VARCHAR(500),
    season      INTEGER      NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_leagues_provider UNIQUE (provider_id)
);

CREATE TABLE teams (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    provider_id     BIGINT       NOT NULL,
    name            VARCHAR(120) NOT NULL,
    normalized_name VARCHAR(120) NOT NULL,
    short_name      VARCHAR(60),
    country         VARCHAR(80),
    logo_url        VARCHAR(500),
    CONSTRAINT uq_teams_provider UNIQUE (provider_id)
);
CREATE INDEX ix_teams_normalized_name      ON teams (normalized_name);
CREATE INDEX ix_teams_normalized_name_trgm ON teams USING GIN (normalized_name gin_trgm_ops);

CREATE TABLE team_aliases (
    id               BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    team_id          BIGINT       NOT NULL REFERENCES teams(id),
    alias            VARCHAR(120) NOT NULL,
    normalized_alias VARCHAR(120) NOT NULL,
    community_id     BIGINT       REFERENCES communities(id),  -- NULL = alias global
    created_by       BIGINT       NOT NULL REFERENCES users(id),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- NULLS NOT DISTINCT é obrigatório: sem ele os aliases globais (community_id
    -- NULL) não seriam deduplicados, porque NULL <> NULL num índice único.
    CONSTRAINT uq_team_alias UNIQUE NULLS NOT DISTINCT (normalized_alias, community_id)
);
CREATE INDEX ix_team_aliases_team      ON team_aliases (team_id);
CREATE INDEX ix_team_aliases_trgm      ON team_aliases USING GIN (normalized_alias gin_trgm_ops);

CREATE TABLE fixtures (
    id             BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    provider_id    BIGINT      NOT NULL,
    league_id      BIGINT      NOT NULL REFERENCES leagues(id),
    home_team_id   BIGINT      NOT NULL REFERENCES teams(id),
    away_team_id   BIGINT      NOT NULL REFERENCES teams(id),
    kickoff_at     TIMESTAMPTZ NOT NULL,
    status         VARCHAR(20) NOT NULL,
    home_score     INTEGER,
    away_score     INTEGER,
    last_synced_at TIMESTAMPTZ,
    CONSTRAINT uq_fixtures_provider UNIQUE (provider_id),
    CONSTRAINT ck_fixtures_status   CHECK (status IN ('SCHEDULED','LIVE','FINISHED','POSTPONED','CANCELLED')),
    CONSTRAINT ck_fixtures_teams    CHECK (home_team_id <> away_team_id)
);
CREATE INDEX ix_fixtures_kickoff        ON fixtures (kickoff_at);
CREATE INDEX ix_fixtures_status_kickoff ON fixtures (status, kickoff_at);
CREATE INDEX ix_fixtures_league         ON fixtures (league_id);
CREATE INDEX ix_fixtures_home_team      ON fixtures (home_team_id);
CREATE INDEX ix_fixtures_away_team      ON fixtures (away_team_id);

CREATE TABLE tip_imports (
    id             BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    community_id   BIGINT      NOT NULL REFERENCES communities(id),
    author_id      BIGINT      NOT NULL REFERENCES users(id),
    raw_text       TEXT        NOT NULL,
    parser_version VARCHAR(20) NOT NULL,
    status         VARCHAR(20) NOT NULL,
    line_errors    JSONB       NOT NULL DEFAULT '[]'::jsonb,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_tip_imports_status CHECK (status IN ('OK','PARTIAL','FAILED'))
);
CREATE INDEX ix_tip_imports_community ON tip_imports (community_id, created_at DESC);

CREATE TABLE tips (
    id           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    community_id BIGINT        NOT NULL REFERENCES communities(id),
    author_id    BIGINT        NOT NULL REFERENCES users(id),
    import_id    BIGINT        REFERENCES tip_imports(id),
    note         VARCHAR(500),
    stake_units  NUMERIC(4,2)  NOT NULL,
    total_odds   NUMERIC(6,3)  NOT NULL,
    bookmaker    VARCHAR(60),
    status       VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    published_at TIMESTAMPTZ,
    settled_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version      BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_tips_stake  CHECK (stake_units > 0),
    CONSTRAINT ck_tips_odds   CHECK (total_odds >= 1),
    CONSTRAINT ck_tips_status CHECK (status IN ('PENDING','WON','LOST','VOID','PENDING_MANUAL'))
);
CREATE INDEX ix_tips_feed   ON tips (community_id, status, published_at DESC);
CREATE INDEX ix_tips_author ON tips (author_id);
CREATE INDEX ix_tips_import ON tips (import_id);

CREATE TABLE tip_selections (
    id                BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    tip_id            BIGINT       NOT NULL REFERENCES tips(id) ON DELETE CASCADE,
    -- NULL permitido: uma tip pode ser publicada com o jogo por associar
    -- quando o fornecedor não tem o jogo dessa liga (secção 10 da spec).
    fixture_id        BIGINT       REFERENCES fixtures(id),
    market            VARCHAR(20)  NOT NULL,
    selection         VARCHAR(20)  NOT NULL,
    line              NUMERIC(4,2),
    odds              NUMERIC(6,3) NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    settlement_source VARCHAR(10),
    settled_by        BIGINT       REFERENCES users(id),
    settled_at        TIMESTAMPTZ,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_selection_market CHECK (market IN ('MATCH_RESULT','DOUBLE_CHANCE','OVER_UNDER','BTTS','HANDICAP')),
    CONSTRAINT ck_selection_status CHECK (status IN ('PENDING','WON','LOST','VOID','PENDING_MANUAL')),
    CONSTRAINT ck_selection_source CHECK (settlement_source IS NULL OR settlement_source IN ('AUTO','MANUAL')),
    CONSTRAINT ck_selection_odds   CHECK (odds >= 1)
);
CREATE INDEX ix_selections_fixture_status ON tip_selections (fixture_id, status);
CREATE INDEX ix_selections_tip            ON tip_selections (tip_id);

CREATE TABLE chat_messages (
    id           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    community_id BIGINT        NOT NULL REFERENCES communities(id),
    author_id    BIGINT        NOT NULL REFERENCES users(id),
    content      VARCHAR(2000) NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ,
    deleted_by   BIGINT        REFERENCES users(id)
);
CREATE INDEX ix_chat_history ON chat_messages (community_id, created_at DESC);

CREATE TABLE notifications (
    id           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users(id),
    type         VARCHAR(40) NOT NULL,
    community_id BIGINT      REFERENCES communities(id),
    tip_id       BIGINT      REFERENCES tips(id),
    payload      JSONB       NOT NULL DEFAULT '{}'::jsonb,
    read_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_notifications_inbox   ON notifications (user_id, read_at, created_at DESC);
CREATE INDEX ix_notifications_created ON notifications (created_at);

CREATE TABLE admin_audit_logs (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    admin_id    BIGINT      NOT NULL REFERENCES users(id),
    action      VARCHAR(60) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id   BIGINT      NOT NULL,
    note        VARCHAR(1000),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_admin ON admin_audit_logs (admin_id, created_at DESC);
```

### Configuração exata

`backend/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: seerhub
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${POSTGRES_DB:seerhub}
    username: ${POSTGRES_USER:seerhub}
    password: ${POSTGRES_PASSWORD:seerhub}
  jpa:
    hibernate:
      ddl-auto: validate      # NUNCA update/create/create-drop, em nenhum perfil
    open-in-view: false
    properties:
      hibernate.jdbc.time_zone: UTC
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false
  jackson:
    default-property-inclusion: non_null
  threads:
    virtual:
      enabled: true

server:
  port: 8080
  error:
    include-stacktrace: never
    include-message: never
    include-exception: false
    include-binding-errors: never

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always      # necessário para expor o componente "db"
      probes:
        enabled: true

logging:
  level:
    root: ${LOG_LEVEL:INFO}
  structured:
    format:
      console: ecs              # logs JSON com MDC (inclui correlationId)

seerhub:
  security:
    jwt-secret: ${JWT_SECRET}                 # sem omissão: obrigatório
    access-ttl: ${JWT_ACCESS_TTL:PT15M}
    refresh-ttl: ${JWT_REFRESH_TTL:P30D}
  football:
    api-key: ${API_FOOTBALL_KEY:}             # vazio é válido: F05 valida quando sincroniza
    base-url: ${API_FOOTBALL_BASE_URL:https://v3.football.api-sports.io}
  uploads:
    dir: ${UPLOADS_DIR:./uploads}
```

`.env.example` (todos os valores são placeholders):

```dotenv
# Base de dados
POSTGRES_DB=seerhub
POSTGRES_USER=seerhub
POSTGRES_PASSWORD=change-me
DB_HOST=db
DB_PORT=5432

# Segredos da aplicação — gerar valores próprios, nunca commitar
JWT_SECRET=change-me-com-pelo-menos-32-caracteres
JWT_ACCESS_TTL=PT15M
JWT_REFRESH_TTL=P30D

# API-Football (usada a partir da F05; vazio é válido até lá)
API_FOOTBALL_KEY=
API_FOOTBALL_BASE_URL=https://v3.football.api-sports.io

# Runtime
UPLOADS_DIR=/var/seerhub/uploads
LOG_LEVEL=INFO
BACKEND_PORT=8080
FRONTEND_PORT=5173
```

## 5. Ordem de implementação

Cada passo deixa a árvore num estado que compila e cujos testes passam. Não avançar com testes vermelhos.

1. **Esqueleto Maven.** Criar `pom.xml` agregador na raiz e `backend/pom.xml`. Gerar o wrapper com `mvn -N wrapper:wrapper -Dmaven=3.9.10` na raiz. Dependências de `backend/pom.xml`: `spring-boot-starter-web`, `-security`, `-data-jpa`, `-validation`, `-actuator`, `-websocket`, `org.flywaydb:flyway-core`, `org.flywaydb:flyway-database-postgresql`, `org.postgresql:postgresql` (runtime); em teste: `spring-boot-starter-test`, `spring-security-test`, `spring-boot-testcontainers`, `org.testcontainers:junit-jupiter`, `org.testcontainers:postgresql` (versões geridas pelo parent, não fixar). **Sem Lombok e sem MapStruct** — convenção do repositório. Configurar `maven-surefire-plugin` com `<includes>` explícito de `**/*Test.java` e `**/*IT.java`, para que `./mvnw test` corra unitários **e** integração num único comando. Verificar com `./mvnw -q compile`.
2. **Aplicação mínima + propriedades.** `SeerHubApplication`, `SeerHubProperties`, `application.yml`, `application-local.yml`, `src/test/resources/application-test.yml`. Escrever `SeerHubPropertiesTest` (E3 + X2) já aqui: com `ApplicationContextRunner`, sem `seerhub.security.jwt-secret` o contexto tem de falhar e a mensagem tem de conter `jwt-secret`. Verde antes de continuar.
3. **Migrações e harness de integração.** `V1`, `V2`, `support/AbstractIntegrationTest`, `support/RepoRoot`, `FlywayBaselineIT`, `MigrationNamingTest`. `FlywayBaselineIT` afirma, via `JdbcTemplate` sobre `information_schema` e `pg_indexes`: as 13 tabelas existem; existem os índices `ix_fixtures_kickoff`, `ix_fixtures_status_kickoff`, `ix_selections_fixture_status`, `ix_tips_feed`, `ix_membership_user_status`, `ix_chat_history`, `ix_teams_normalized_name_trgm`, `ix_team_aliases_trgm`; `SELECT 1 FROM pg_extension WHERE extname='pg_trgm'` devolve linha; e `SELECT similarity('sporting','sporting cp') > 0` executa sem erro (prova funcional da extensão, não só a sua presença). Este é o passo que valida a decisão mais cara do plano — fazê-lo cedo.
4. **Health check.** `HealthCheckIT` e `HealthCheckDbDownIT`. Nada a escrever em produção além da configuração do actuator já feita no passo 2.
5. **Correlação e erros.** `CorrelationIdFilter`, `ApiException`, `ApiExceptionHandler`, `CorrelationIdFilterTest`, `ApiExceptionHandlerIT`.
6. **Segurança mínima.** `SecurityConfig` permit-all e stateless. Reconfirmar que `HealthCheckIT` e `ApiExceptionHandlerIT` continuam verdes (é aqui que uma cadeia mal configurada aparece como 401 inesperado).
7. **Contentores.** `backend/Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`, `docker-compose.yml`, `.env.example`, `.dockerignore`. Escrever `DockerComposeTest`, `EnvExampleTest` e `ConfigurationConventionsTest`. Estes três testes falham enquanto o compose ou o `.env.example` estiverem incompletos — é essa a intenção.
8. **Frontend.** `npm create vite@latest` não é usado; criar os ficheiros à mão conforme a lista, correr `npm install` para gerar o lock. `App.test.tsx` e `api.test.ts`. `npm test` verde.
9. **`CLAUDE.md`.** Escrito no fim, a descrever o que existe de facto. Secções obrigatórias:
   - **Como correr:** `./mvnw test` (raiz, corre `*Test` e `*IT`), `cd frontend && npm test`, `docker compose --env-file .env up --build`, `npm run dev` para desenvolvimento com proxy.
   - **Layout:** monorepo `backend/` + `frontend/`, backend com pacote base `pt.seerhub`, **organização por feature e não por camada** — `pt.seerhub.<feature>` com subpacotes `api` (controllers + DTOs record), `domain` (entidades + enums), `repo` (interfaces Spring Data), `service`. Infraestrutura transversal em `pt.seerhub.common` e `pt.seerhub.config`. Mapa de pacotes futuros: `user` (F01), `community` (F02), `membership` (F03), `football` (F05), `tips` (F06–F08), `stats` (F09), `hub` (F10), `chat` (F12), `notification` (F13), `admin` (F14).
   - **Nomes:** entidades no singular (`Community`), tabelas no plural em snake_case (`communities`), repositórios `<Entidade>Repository`, serviços `<Feature>Service`, controladores `<Feature>Controller`, DTOs como `record` no subpacote `api`. Enumerados persistidos como `VARCHAR` com `@Enumerated(EnumType.STRING)`.
   - **Testes:** `*Test` = unitário sem contexto Spring; `*IT` = integração que estende `AbstractIntegrationTest` (Postgres real, partilhado). **Um único comando corre os dois.** Regra dura: **nenhum teste pode aceder à rede nem exigir uma chave de API**; dependências externas ficam atrás de interface com implementação de teste (é o que o R5 exige do `FootballDataProvider`). Nomes de teste em português, estilo `metodoFazXQuandoY`.
   - **Migrações:** a regra do §4 deste plano, citada literalmente. Numeração sequencial `V<n>__<snake_case>.sql`, nunca editar uma migração aplicada, `ddl-auto` é sempre `validate`.
   - **Erros e logs:** lançar `ApiException` para erros de negócio; nunca devolver mensagens internas; todas as respostas de erro são `ProblemDetail` com `correlationId`.
   - **Segredos:** só por variável de ambiente; toda a variável nova entra no `.env.example` no mesmo commit, senão `EnvExampleTest` fica vermelho.
   - **Idioma:** UI e mensagens ao utilizador em português de Portugal.
10. **Verificação final** conforme a secção 7.

## 6. Não tocar

O implementador está proibido de criar, editar ou apagar:

- `docs/specs/seerhub.md` — a spec. Só o utilizador a altera.
- `docs/features/BACKLOG.md` e `docs/features/CHANGELOG.md` — só o orquestrador escreve.
- `docs/features/F00-fundacoes/plan.md` — este documento. O implementador escreve `handoff.md` na mesma pasta.
- `seerhub.md` — o brief original.
- `.env` — contém uma chave real. Não ler, não copiar, não referenciar o seu conteúdo, não commitar. Se o arranque local exigir a variável `API_FOOTBALL_KEY`, isso é uma nota no handoff para o utilizador, não uma edição do ficheiro.
- `.claude/` — configuração da ferramenta.
- Qualquer pasta `docs/features/F01..F15` — não existem ainda e não são criadas aqui.

Proibições de comportamento:
- Não criar nenhuma entidade JPA, nenhum repositório, nenhum endpoint de negócio, nenhum ecrã além do `HealthPage`. O esquema existe em SQL; o mapeamento Java é de cada feature.
- Não implementar autenticação, JWT, registo nem login (é F01).
- Não implementar o seed de dados de exemplo (é F15).
- Não chamar a API-Football nem qualquer serviço externo, em código de produção ou de teste.
- Não apagar linhas do `.gitignore`.

## 7. Verificação

Executar, por esta ordem, a partir de `C:\Users\tiago\Desktop\Projetos\SeerHub`:

```
1)  ./mvnw -q test
2)  cd frontend && npm ci && npm run typecheck && npm test && cd ..
3)  docker compose --env-file .env.example config
4)  docker compose --env-file .env.example up -d --build
5)  curl -i http://localhost:8080/actuator/health
6)  curl -i http://localhost:5173/
7)  docker compose logs backend --tail 20
8)  docker compose down -v
```

O que conta como sucesso:

1. `BUILD SUCCESS` e uma linha `Tests run: N, Failures: 0, Errors: 0, Skipped: 0` com **N ≥ 20** (o número de testes nomeados na tabela da secção 3 para o backend). Zero testes ignorados. A execução tem de correr com a rede desligada — se houver dúvida, correr uma segunda vez com o adaptador de rede desativado; a única razão legítima para falhar então é o repositório Maven local não ter as dependências, o que não conta.
2. `tsc --noEmit` sem erros e o Vitest a reportar `Test Files  2 passed (2)` com pelo menos 4 testes passados.
3. Sai com código 0 e imprime a configuração resolvida com os três serviços. Qualquer `variable is not set` significa `.env.example` incompleto → R15.3a não está cumprido.
4. Os três contentores ficam `Up`; `db` e `backend` chegam a `healthy`.
5. `HTTP/1.1 200` e corpo com `"status":"UP"` e `"components":{"db":{"status":"UP"`.
6. `HTTP/1.1 200` e HTML da SPA.
7. Cada linha é um objeto JSON com os campos do formato ECS; pelo menos uma linha de pedido traz `correlationId`. Nenhuma linha contém a password do Postgres nem a chave da API.
8. Remove tudo, incluindo volumes.

Se o passo 4 falhar por falta de rede para descarregar imagens base, isso é ambiente e não plano: registar no handoff e considerar os passos 1–3 como a verificação vinculativa.

## 8. Casos de fronteira cobertos

Da secção 10 da spec, F00 não é dono de nenhum caso de negócio. É dono dos casos de infraestrutura que a spec implica e que o enunciado desta feature exige explicitamente:

- **Base de dados em baixo ao arranque / durante a operação.** Coberto por `HealthCheckDbDownIT`: o health devolve 503 com `db: DOWN`, e a aplicação continua de pé (não há `System.exit`). No compose, `depends_on: condition: service_healthy` impede que o backend arranque contra uma base de dados que ainda não aceita ligações, que é o modo de falha real de um primeiro `docker compose up`.
- **Variável de ambiente obrigatória em falta.** Coberto por `SeerHubPropertiesTest`: o arranque falha imediatamente e a mensagem nomeia a propriedade em falta. A alternativa — arrancar com um segredo vazio e falhar mais tarde na autenticação — seria muito pior e é explicitamente rejeitada.
- **Exceção não tratada a chegar ao cliente.** Coberto por `ApiExceptionHandlerIT`: 500 com `ProblemDetail`, `correlationId` presente, e ausência verificada de `stack`, `trace`, nome de classe de exceção e da mensagem original. O stack trace vai para o log do servidor, com o mesmo `correlationId`, o que torna o par cliente↔log rastreável.
- **Segredo a escapar para o repositório.** Coberto por `ConfigurationConventionsTest`: varrimento por padrão de segredo real e verificação de que `.gitignore` ignora `.env`.
- **Migração aplicada e depois editada.** Coberto por `MigrationNamingTest` (convenção e unicidade de versão) e pela validação do Flyway em cada `*IT`, que falha por checksum se alguém editar uma migração já aplicada num volume existente.

## 9. Riscos em aberto

**Resolução das hipóteses deliberadas antes de escrever este plano.**

- **H1 — esquema completo no baseline vs migração por feature: CONFIRMADA (baseline completo), com regra anexa.** Evidência: o grafo de chaves estrangeiras do §8 atravessa a ordem do backlog em pelo menos três pontos (`tip_selections.fixture_id` liga F07/F08 a F05; `team_aliases.community_id` liga F06b a F02; `tips.community_id` liga F07 a F02), e o §8 está fechado ao detalhe do tipo e da restrição. O risco dominante deste run não é o esquema estar errado — é 16 agentes sem contexto partilhado traduzirem o mesmo §8 de forma diferente. A regra "está no §8 → já existe; não está → a feature acrescenta a sua migração" resolve isso com uma frase. **O que a pode tornar errada:** se alguma feature precisar de alterar uma coluna do baseline, paga um `ALTER` — barato, porque não há produção. **Como descobrir cedo:** o passo 3 da ordem de implementação corre `FlywayBaselineIT` antes de existir qualquer outro código.
- **H2 — Testcontainers vs H2/embutido: CONFIRMADA (Testcontainers), sem alternativa viável.** `pg_trgm` com `similarity()`, índices GIN de trigramas, `JSONB`, `UNIQUE NULLS NOT DISTINCT`, `TIMESTAMPTZ` e bloqueio otimista não existem ou comportam-se de forma diferente em H2. Um harness embutido daria verde falso exatamente no requisito de maior risco da spec (R6). O receio legítimo — Testcontainers precisa de rede — está afastado: `postgres:16-alpine` já está em cache local nesta máquina, verificado com `docker images`. **Mitigação registada:** fixar a tag `postgres:16-alpine` (nunca `latest`) e usar um contentor estático partilhado em `AbstractIntegrationTest`, para que as 16 features seguintes paguem o arranque uma vez por execução da suite e não uma vez por classe.
- **H3 — forma do monorepo e frontend no build Maven: CONFIRMADA (monorepo, frontend fora do Maven).** Nove das dezassete features tocam no frontend; acoplar `npm` ao ciclo de vida do Maven com `frontend-maven-plugin` faria toda a execução de `./mvnw test` pagar o custo do npm e introduziria uma segunda fonte de falhas offline. O POM agregador na raiz custa um ficheiro e entrega literalmente o comando `./mvnw test` prometido no backlog, enquanto `backend/pom.xml`, parentado em `spring-boot-starter-parent`, continua a construir sozinho dentro do `docker build ./backend`.

**Riscos que sobrevivem à deliberação.**

1. **Nome da variável da chave da API-Football.** O `.env` local do utilizador usa `API_KEY`; este plano normaliza para `API_FOOTBALL_KEY`. O implementador **não altera o `.env`**. Consequência: até o utilizador renomear a linha, `API_FOOTBALL_KEY` fica vazia — o que é válido, porque F00 e F01–F04 não chamam a API. Registar no handoff como ação para o utilizador antes de F05. Custo de não o fazer: F05 falha no primeiro arranque com uma mensagem clara.
2. **`management.endpoint.health.show-details: always`.** É preciso para que o teste veja o componente `db`, mas expõe publicamente o tipo de base de dados a partir de F01, quando houver internet à frente. Dívida deliberada: F01 deve restringir o detalhe a `ADMIN` (`show-details: when-authorized`) e adaptar `HealthCheckIT` para autenticar. Registar no handoff como dívida com dono nomeado.
3. **Versões fixadas.** Spring Boot `3.5.0` e Maven `3.9.10` são os alvos. Se a resolução falhar, subir para a versão 3.5.x mais recente disponível localmente e registar o desvio no handoff — não descer para 3.3.x, porque o `logging.structured.format` (usado no critério R15.6a sem dependência extra) só existe a partir de 3.4. Tailwind fixado em `^3.4` e não em v4, porque a v4 muda o modelo de configuração e nenhuma feature seguinte ganha nada com isso.
4. **`SecurityConfig` permit-all.** É um esqueleto que, se F01 se esquecer dele, deixa a plataforma aberta. Mitigação barata: comentário no topo do ficheiro a nomear F01 como responsável, e uma nota no `CLAUDE.md`. Não vale a pena mais do que isso — F01 tem o R1 inteiro dedicado a substituí-lo, e é a primeira feature a seguir.
5. **`leagues.provider_id UNIQUE` conforme o §8 literal.** Se a API-Football reutilizar o mesmo id de liga em épocas diferentes, F05 precisará de trocar para `UNIQUE(provider_id, season)`. Não foi antecipado porque contrariar o §8 por suposição é pior do que uma migração de uma linha. Forma mais barata de descobrir: F05 sincroniza duas épocas da mesma liga no seu conjunto de dados de teste.
6. **O arranque real de `docker compose up` não corre dentro da suite.** É a única verificação parcialmente manual do plano, e está justificada na secção 3. Se se revelar insuficiente (por exemplo, o `Dockerfile` do backend a partir num contexto em que o POM agregador não existe), o sinal aparece no passo 4 da verificação e a correção é local ao `Dockerfile`.