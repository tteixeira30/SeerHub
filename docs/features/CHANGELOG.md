# Changelog — SeerHub

Registo append-only do run de features. Mais recente em baixo.
Escrito pelo orquestrador antes de cada ação, para que uma interrupção deixe sempre uma
posição legível. Nenhuma entrada passada é editada ou apagada — corrige-se com uma nova.

---

## 2026-07-27 16:20 — RUN ABERTO

Spec `docs/specs/seerhub.md`. 17 features fatiadas a partir de R1–R15.

Ambiente: Java 21.0.7, Maven 3.9.10, Node 22.16.0, Docker 28.1.1 a correr.
Repositório versionado em `github.com/tteixeira30/SeerHub`, branch `main`, commit inicial
`ff3e4cc` (brief + spec). Um commit por feature a partir daqui.

Comando de testes: ainda não existe — F00 tem de o criar. Previsto `./mvnw test` e `npm test`.
**Baseline: 0 testes, 0 a passar, 0 a falhar** — repositório sem código.

Desvios face aos milestones da spec, com motivo:
- R15 dividido em F00 (esqueleto) e F15 (seed) — o seed não é verificável antes de existirem
  entidades para semear.
- R6 dividido em F06a (gramática, testável sem base de dados) e F06b (correspondência de
  equipas, precisa de `pg_trgm` e do catálogo de F05).

## 2026-07-27 16:24 — ÂMBITO DO RUN

Decisão do utilizador: o loop corre **F00–F04 (M1)** e para para revisão. F05–F15 ficam
`TODO` no backlog e retomam numa invocação seguinte.

Q1 da spec (plano da API-Football e ligas a cobrir): o utilizador tem chave e plano. Os
detalhes concretos são recolhidos no checkpoint do M1, antes de F05. Não bloqueia M1.

## 2026-07-27 16:25 — F00 Fundações e esqueleto · PLANNING

Planeador lançado (Opus 5), com deliberação lisa-loop de 3 minutos: F00 fixa stack,
estrutura de pacotes, harness de testes e migrações, dos quais dependem as 16 features
seguintes. Errar aqui é o erro mais caro do run.

## 2026-07-27 16:56 — F00 Fundações e esqueleto · PLANNED

`docs/features/F00-fundacoes/plan.md` — 581 linhas. 52 ficheiros a criar, 25 critérios
mapeados a testes nomeados, nenhum critério sem teste.

Três decisões estruturais que o plano fixa para todo o run:
1. **Esquema completo do §8 no baseline Flyway**, com a regra "está no §8 → já existe;
   não está → a feature acrescenta a sua própria migração", reforçada por
   `ddl-auto: validate`. Motivo: o grafo de FKs do §8 atravessa a ordem das features, e o
   risco dominante é 16 agentes sem contexto partilhado traduzirem o mesmo §8 de forma
   divergente.
2. **Testcontainers com Postgres real** (contentor estático partilhado), não H2:
   `pg_trgm`, `similarity()`, GIN, `JSONB`, `UNIQUE NULLS NOT DISTINCT` e bloqueio otimista
   não existem em H2 e dariam verde falso no R6, o requisito de maior risco.
3. **Backend organizado por feature** (`pt.seerhub.<feature>` com `api`/`domain`/`repo`/
   `service`), monorepo, frontend fora do build Maven.

Ocorrências do ciclo, registadas por afetarem os próximos:
- O agente `Plan` é read-only e **não tem ferramenta de escrita**: devolveu o plano na
  resposta e o orquestrador persistiu-o. A skill foi corrigida para descrever este fluxo.
- O lisa-loop gastou 721s contra um orçamento de 180s (4x). Nos próximos planeadores o
  orçamento é dado como limite, não como mínimo.
- O planeador leu o `.env` para descobrir o nome da variável da chave (`API_KEY`). Não
  expôs o valor, mas é uma leitura de segredo que não devia ter acontecido; a skill passou
  a proibir explicitamente o acesso ao `.env` em ambos os agentes.

Ação para o utilizador antes de F05: o `.env` usa `API_KEY`, o plano normaliza para
`API_FOOTBALL_KEY`. Renomear a linha quando chegarmos a M2.

## 2026-07-27 16:58 — F00 Fundações e esqueleto · IMPLEMENTING

Implementador lançado (Sonnet 5) a partir de `plan.md`.

## 2026-07-27 17:40 — F00 Fundações e esqueleto · DONE

58 ficheiros commitados. **26 testes, 0 falhas** — 22 JUnit (`./mvnw test`) + 4 Vitest
(`cd frontend && npm test`), verificados pelo orquestrador e não apenas reportados pelo
implementador. Baseline do run passa de 0 para 26.

Os 25 critérios da tabela do plano têm teste com o nome exato especificado, confirmado um a
um. Lista "não tocar" respeitada: spec, backlog, changelog, plano e `.env` intactos;
`.gitignore` só cresceu 3 linhas, nenhuma apagada.

**Nova baseline para todas as features seguintes: 26 a passar, 0 a falhar.**

Desvios face ao plano, todos registados no handoff e nenhum contra a spec:
1. **`snakeyaml` retirado do `backend/pom.xml`.** O plano declarava-o como dependência de
   teste explícita; isso sobrepõe-se ao escopo `compile` que vem transitivamente do
   `spring-boot-starter` e o jar empacotado ficava sem ele — a aplicação não arrancava em
   contentor, mas os testes Maven passavam na mesma. Só apareceu por o implementador ter
   corrido mesmo o `docker compose up`. Regra que fica para as features seguintes: nunca
   redeclarar com escopo mais restrito uma dependência que o Spring Boot já traz.
2. **`@types/node` acrescentado ao frontend** — sem ele `tsc -b` falhava a compilar
   `vite.config.ts` e o build de produção do Dockerfile nunca teria sido testado.
3. Dois testes extra além da tabela, a cobrir prosa explícita do plano.
4. Porta 8080 ocupada por outro projeto nesta máquina; a verificação em contentor correu
   com portas remapeadas por variável de shell, sem tocar no `.env.example`.

Verificação em contentor **confirmada**, não "melhor esforço": os três serviços chegam a
`healthy`, health check responde 200 `UP` direto e via proxy nginx, logs em JSON ECS com
`correlationId`, `docker compose down -v` limpa tudo.

Dívidas herdadas por quem vier a seguir:
- `management.endpoint.health.show-details: always` — **dono nomeado: F01**, trocar para
  `when-authorized` e adaptar `HealthCheckIT` para autenticar.
- `SecurityConfig` com `anyRequest().permitAll()` — esqueleto que F01 substitui.
- `npm audit` reporta 7 vulnerabilidades (1 crítica) em dependências de build do
  Vite/Vitest. Não investigadas; são dev-only, não código servido.
- **Ação do utilizador antes de F05:** renomear `API_KEY` para `API_FOOTBALL_KEY` no `.env`.

Commit `bfe5b19`.

## 2026-07-27 17:41 — F01 Contas e autenticação · PLANNING

Planeador lançado (Opus 5), com deliberação lisa-loop de 2 minutos: F01 fixa o modelo de
autenticação (JWT, rotação de refresh tokens, revogação) de que dependem F02–F14, e é a
primeira feature a mapear entidades JPA contra o baseline Flyway — a prova de que a decisão
estrutural de F00 funciona.

## 2026-07-27 17:47 — F01 Contas e autenticação · PLANNED

`docs/features/F01-contas-autenticacao/plan.md` — 669 linhas, 49 ficheiros a criar ou
editar, **28 critérios mapeados a testes nomeados**: os 5 critérios de R1 decompostos em 17,
mais 5 caminhos de falha, 3 para fechar a dívida do health de F00 e 3 de esqueleto.

O lisa-loop parou aos 120s, dentro do orçamento — o limite passou a ser tratado como teto e
não como mínimo, ao contrário de F00 (721s contra 180s).

Decisões que ficam fixadas: access token JWT HS256 de 15 min, refresh token **opaco**
guardado com hash na base de dados, rotativo com deteção de reutilização que revoga a
família inteira, `V3__refresh_tokens.sql` como migração própria (a tabela não está no §8).

O critério mais fácil de testar mal — "409 que não revela se a conta existe" — tem asserção
precisa: 3b exige que email desconhecido e password errada produzam respostas
indistinguíveis, não apenas que o 409 exista.

Nota de qualidade do plano: a linha X5 da tabela traz uma auto-correção do nome do teste
(um espaço a mais, corrigido a negrito na mesma célula). O nome válido é o da versão a
negrito.

## 2026-07-27 17:48 — F01 Contas e autenticação · IMPLEMENTING

Implementador lançado (Sonnet 5) a partir de `plan.md`. Baseline a preservar: 26 testes.
