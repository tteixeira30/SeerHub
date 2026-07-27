# Backlog — SeerHub

**Spec:** `docs/specs/seerhub.md`
**Run iniciado:** 2026-07-27
**Comando de testes:** a definir por F00 — previsto `./mvnw test` (backend) e `npm test` (frontend)
**Baseline:** 0 testes — repositório sem código

**Ambiente verificado:** Java 21.0.7 · Maven 3.9.10 · Node 22.16.0 · Docker 28.1.1 (a correr, Testcontainers viável)

## Legenda de estados

`TODO` → `PLANNING` → `PLANNED` → `IMPLEMENTING` → `VERIFYING` → `DONE` | `BLOCKED`

| ID | Feature | Requisitos | Depende de | Milestone | Estado | Atualizado |
| --- | --- | --- | --- | --- | --- | --- |
| F00 | Fundações e esqueleto | R15 (exceto seed) | — | M1 | IMPLEMENTING | 2026-07-27 |
| F01 | Contas e autenticação | R1 | F00 | M1 | TODO | — |
| F02 | Criação e gestão de comunidades | R2 | F01 | M1 | TODO | — |
| F03 | Subscrições e controlo de acesso | R3 | F02 | M1 | TODO | — |
| F04 | Papéis e permissões | R4 | F03 | M1 | TODO | — |
| F05 | Sincronização de dados de futebol | R5 | F00 | M2 | TODO | — |
| F06a | Gramática de tips | R6 (gramática) | F00 | M2 | TODO | — |
| F06b | Correspondência de equipas e aliases | R6 (matching) | F05, F06a, F02 | M2 | TODO | — |
| F07 | Revisão e publicação de tips | R7 | F06b, F04 | M2 | TODO | — |
| F08 | Resolução de tips | R8 | F07, F05 | M3 | TODO | — |
| F09 | Estatísticas | R9 | F08 | M3 | TODO | — |
| F10 | Hub | R10 | F09, F03 | M4 | TODO | — |
| F11 | Visibilidade e teaser | R11 | F10, F03 | M4 | TODO | — |
| F12 | Chat de comunidade | R12 | F04 | M5 | TODO | — |
| F13 | Notificações | R13 | F07, F08 | M5 | TODO | — |
| F14 | Painel de administração | R14 | F02, F01 | M6 | TODO | — |
| F15 | Seed de desenvolvimento | R15 (seed) | F08 | M6 | TODO | — |

## Notas de fatiamento

- **R15 dividido em F00 e F15.** O critério do seed de desenvolvimento ("cria utilizadores,
  comunidades e tips de exemplo") não é verificável antes de essas entidades existirem. F00
  entrega esqueleto, Postgres, Flyway, `.env.example`, health check, logs estruturados e
  `docker compose up`; F15 fecha o seed no fim, quando há o que semear.
- **R6 dividido em F06a e F06b.** São dois deliverables com verificação independente: a
  gramática é lógica pura e testa-se sem base de dados; a correspondência de equipas precisa
  de Postgres com `pg_trgm` e do catálogo sincronizado por F05. É o requisito de maior risco
  da spec e dividi-lo reduz o raio de falha.
- **Ordem alterada face aos milestones da spec.** F05 (R5, M2) só depende de F00 e não da
  cadeia de autenticação, mas mantém-se depois de F04 para não intercalar milestones. F06a
  também só depende de F00 — pode ser antecipado se F02–F04 ficarem bloqueados.
- **F12 (Chat) não depende de F07–F11.** Se a cadeia de tips bloquear, F12 e F14 continuam
  executáveis.
- Features com superfície de frontend além do backend: F02, F03, F07, F09, F10, F11, F12, F13, F14.
