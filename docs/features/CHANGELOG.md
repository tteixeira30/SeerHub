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
