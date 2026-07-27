# SeerHub — convenções do repositório

Este ficheiro descreve o que existe de facto no repositório depois de F00
(fundações e esqueleto). É o contrato que todas as features seguintes (F01
a F15) devem seguir.

## Como correr

- `./mvnw test` — na raiz, corre a suite completa do backend: testes
  unitários (`*Test`) e de integração (`*IT`) num único comando. Não é
  preciso rede nem chave de API; o Postgres de teste é um Testcontainer
  (`postgres:16-alpine`, já em cache local).
- `cd frontend && npm test` — corre a suite Vitest do frontend.
- `docker compose --env-file .env up --build` — arranca os três serviços
  (`db`, `backend`, `frontend`) depois de preencher o `.env` a partir de
  `.env.example`.
- `cd frontend && npm run dev` — desenvolvimento local do frontend, com o
  proxy do Vite a apontar `/api`, `/actuator` e `/ws` para
  `http://localhost:8080`.

## Layout

Monorepo com dois módulos independentes: `backend/` (Spring Boot 3 / Java
21) e `frontend/` (React 18 + Vite, servido por nginx em produção). O
frontend **não** é um módulo Maven — corre com o seu próprio `npm`, para
que `./mvnw test` nunca dependa de `node`/`npm` nem pague o custo do build
JS.

O backend organiza-se **por feature, não por camada**: pacote base
`pt.seerhub`, subpacotes `pt.seerhub.<feature>` cada um com:

- `api` — controllers e DTOs (`record`);
- `domain` — entidades JPA e enums;
- `repo` — interfaces Spring Data;
- `service` — regras de negócio.

Infraestrutura transversal fica em `pt.seerhub.common` (erros, web) e
`pt.seerhub.config` (propriedades, segurança).

Mapa de pacotes de feature ainda por criar, para referência de quem
planeia a seguir: `user` (F01), `community` (F02), `membership` (F03),
`football` (F05), `tips` (F06–F08), `stats` (F09), `hub` (F10), `chat`
(F12), `notification` (F13), `admin` (F14).

## Nomes

- Entidades no singular (`Community`); tabelas no plural em snake_case
  (`communities`).
- Repositórios `<Entidade>Repository`; serviços `<Feature>Service`;
  controladores `<Feature>Controller`; DTOs como `record` dentro do
  subpacote `api`.
- Enumerados persistidos como `VARCHAR` na base de dados, mapeados com
  `@Enumerated(EnumType.STRING)` do lado Java.

## Testes

- `*Test` — unitário, sem contexto Spring (ou, quando testa propriedades
  de configuração, com `ApplicationContextRunner`, que é um contexto
  mínimo isolado, não o da aplicação).
- `*IT` — integração. Estende
  `pt.seerhub.support.AbstractIntegrationTest`
  (`backend/src/test/java/pt/seerhub/support/AbstractIntegrationTest.java`),
  que já traz `@SpringBootTest(webEnvironment = RANDOM_PORT)`,
  `@ActiveProfiles("test")`, `@AutoConfigureMockMvc` e um
  `PostgreSQLContainer<>("postgres:16-alpine")` estático e partilhado
  (arranca uma vez por execução da suite, nunca é parado explicitamente —
  o Ryuk do Testcontainers trata da limpeza no fim da JVM). **Só** os
  testes que precisam de simular a base de dados em baixo declaram o seu
  próprio contentor descartável (ver
  `backend/src/test/java/pt/seerhub/health/HealthCheckDbDownIT.java` como
  exemplo).
- Um único comando (`./mvnw test`) corre os dois tipos.
- **Regra dura: nenhum teste pode aceder à rede nem exigir uma chave de
  API.** Dependências externas (a começar pela API-Football, R5) ficam
  atrás de uma interface, com implementação de teste em memória —
  nunca uma chamada real, nem sequer para localhost, fora do
  Testcontainer do Postgres.
- Nomes de teste em português (europeu), estilo `metodoFazXQuandoY`.
  Mensagens ao utilizador também em português de Portugal.
- Para localizar ficheiros na raiz do monorepo a partir de um teste
  (`docker-compose.yml`, `.env.example`), usar
  `pt.seerhub.support.RepoRoot.find()`
  (`backend/src/test/java/pt/seerhub/support/RepoRoot.java`), que sobe
  diretórios a partir de `user.dir` até encontrar os dois ficheiros.

## Migrações

Regra herdada de F00 (o esquema completo da secção 8 da spec já existe no
baseline):

> Se a tabela está na secção 8 da spec, já existe: mapeia a entidade
> contra ela e confia em `ddl-auto: validate`. Se não está na secção 8
> (por exemplo `refresh_tokens` do R1, `notification_preferences` do R13,
> `community_reports` do R14), a feature que a desenha acrescenta a sua
> própria migração `V<n>__<descrição>.sql`. Nunca se edita uma migração já
> existente.

- Ficheiros em `backend/src/main/resources/db/migration/`.
- Numeração sequencial `V<n>__<snake_case>.sql`, versão única, nunca
  reutilizada nem reordenada. Verificado mecanicamente por
  `pt.seerhub.migration.MigrationNamingTest`.
- `spring.jpa.hibernate.ddl-auto` é sempre `validate`, em todos os
  perfis. Verificado por
  `pt.seerhub.config.ConfigurationConventionsTest`.
- Migrações já aplicadas nunca são editadas — o Flyway falha por
  checksum se alguém o fizer sobre uma base de dados existente. Uma
  correção ao baseline é sempre um `ALTER TABLE` numa migração nova.

## Erros e logs

- Lançar `pt.seerhub.common.error.ApiException(HttpStatus status, String detail)`
  para qualquer erro de negócio
  (`backend/src/main/java/pt/seerhub/common/error/ApiException.java`).
  `detail` é sempre uma mensagem segura para mostrar ao utilizador, em
  português de Portugal.
- O `pt.seerhub.common.error.ApiExceptionHandler`
  (`@RestControllerAdvice`) converte isso, e qualquer exceção não tratada,
  num `ProblemDetail` (RFC 7807) com a propriedade extra `correlationId`.
  Exceções não previstas devolvem sempre o mesmo `detail` fixo
  (`"Ocorreu um erro inesperado."`); a exceção real fica só no log do
  servidor, nunca na resposta.
- Correlação de pedido: `pt.seerhub.common.web.CorrelationIdFilter`
  (`backend/src/main/java/pt/seerhub/common/web/CorrelationIdFilter.java`),
  regista o cabeçalho `X-Correlation-Id` (constante
  `CorrelationIdFilter.HEADER`) no MDC (chave
  `CorrelationIdFilter.MDC_KEY`) para todos os logs do pedido, e o
  devolve na resposta. Gera um novo UUID quando o cliente não envia um
  válido.
- Logs estruturados em JSON (formato ECS, com o `correlationId` do MDC)
  em todos os perfis exceto `local` e `test`, onde são desligados para
  facilitar a leitura humana.

## Segredos

- Só por variável de ambiente, nunca literais em `application*.yml` nem
  em `docker-compose.yml`.
- Toda a variável nova entra em `.env.example` **no mesmo commit** que a
  introduz — senão `pt.seerhub.config.EnvExampleTest` fica vermelho (ele
  compara, nos dois sentidos, os placeholders `${VAR}` usados em
  `application*.yml`/`docker-compose.yml` com as chaves de
  `.env.example`).
- `.env.example` só tem placeholders óbvios (`change-me...`), nunca um
  segredo real. `pt.seerhub.config.ConfigurationConventionsTest` varre
  `.env.example`, `docker-compose.yml` e
  `backend/src/main/resources/**` à procura de sequências com aparência
  de segredo real.

## Idioma

Interface, mensagens ao utilizador e nomes de teste em português de
Portugal. Identificadores de código (classes, métodos, variáveis) em
inglês. Comentários em português.

## Segurança (dívida deliberada para F01)

`backend/src/main/java/pt/seerhub/config/SecurityConfig.java` define uma
cadeia mínima: `STATELESS`, CSRF desligado, `anyRequest().permitAll()`, e
o bean `PasswordEncoder` (`BCryptPasswordEncoder`). **F01 substitui as
regras de autorização, não apaga a cadeia.** Mantém `STATELESS` e
`csrf.disable()` (API stateless com JWT), troca só
`anyRequest().permitAll()` pelas regras reais.
