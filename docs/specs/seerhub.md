
# SeerHub

**Estado:** Rascunho — a aguardar aprovação
**Última atualização:** 2026-07-27
**Brief de origem:** `seerhub.md`

> **Revisão de 2026-07-27:** o parser de tips deixou de ser um modelo de linguagem e passou a ser uma gramática determinística com correspondência difusa de equipas (R6). Motivo: custo por parse (~€0,11 com a Claude API), latência (~30s vs <50ms) e testabilidade. A interface `TipTextParser` mantém a IA plugável se o formato se revelar rígido demais. Afetou R5, R6, R7, o modelo de dados, a abordagem técnica, os pressupostos e Q2.

> **Revisão de 2026-07-27 (durante a implementação, antes de F05):** o calendário de sincronização do R5 deixou de ser sondagem fixa e passou a ser **a pedido**. Motivo: o plano contratado da API-Football é o gratuito, com 100 chamadas/dia, e o calendário anterior (resultados de 15 em 15 minutos, jogos de 6 em 6 horas) consumia exatamente 100 chamadas/dia **com uma única liga** — sem folga para retentativas, para desenvolvimento, nem para as seis ligas escolhidas. A sincronização passa a chamar a API apenas quando existe uma seleção pendente por resolver, agrupando por liga e dia, com orçamento diário explícito. Q1 fica resolvida. Afetou R5 e, indiretamente, o que o R8 consegue resolver automaticamente.

## 1. Resumo

O SeerHub é uma plataforma web onde tipsters criam a sua própria comunidade de apostas, definem o preço da subscrição e publicam tips; e onde o apostador final subscreve várias comunidades e vê tudo — tips, estatísticas e conversa — num único sítio, em vez de saltar entre Telegram, Patreon e BuyMeACoffee.

O que distingue o produto de um canal de Telegram são duas coisas: **as tips são dados estruturados, não texto** (o tipster cola um bloco num formato conhecido e o sistema converte-o em seleções ligadas a jogos reais), e **as estatísticas são verificáveis** (as tips resolvem automaticamente contra os resultados reais sempre que possível, em vez de serem autodeclaradas).

## 2. Problema

Hoje um tipster distribui tips por Telegram, Patreon ou BuyMeACoffee. Isto tem três custos:

- **Para o tipster:** inserir tips uma a uma é trabalho manual repetitivo, e o histórico fica preso em mensagens que ninguém consegue agregar.
- **Para o apostador:** seguir cinco tipsters significa cinco plataformas, cinco subscrições e nenhuma forma de comparar quem realmente dá lucro.
- **Para ambos:** as estatísticas de performance são autodeclaradas e não auditáveis, o que faz com que a reputação valha pouco.

Deixar isto como está mantém o mercado opaco: quem é bom não tem como o provar, e quem paga não tem como saber.

## 3. Objetivos

- Um tipster publica um lote de tips colando um único bloco de texto, e vê-as estruturadas e ligadas aos jogos reais em segundos.
- Um apostador subscreve N comunidades e vê todas as tips abertas num feed único, ordenado pela hora de início dos jogos.
- Um apostador avalia um tipster antes de pagar, olhando para estatísticas que o tipster não controla.
- Uma tip fechada reflete o resultado real do jogo sem intervenção humana, sempre que o mercado o permita.

## 4. Não-objetivos

Explicitamente fora desta versão. Cada linha aqui é uma discussão de âmbito já decidida:

- **Pagamentos reais.** A subscrição existe como estado e controla o acesso, mas não há gateway, cobrança, payouts nem KYC. O modelo de dados é desenhado para receber Stripe Connect depois sem remodelação.
- **Mensagens diretas entre utilizadores.** Só chat de comunidade. DMs implicam bloqueio, denúncia e política anti-spam.
- **Desportos além de futebol.** Sem basquetebol, ténis ou eSports. O catálogo de mercados é fechado e específico de futebol.
- **Apps nativas iOS/Android.** Web responsiva apenas.
- **Notificações por email ou push do browser.** Só notificações dentro da aplicação.
- **Integração com casas de apostas.** Sem colocação automática de apostas, sem leitura de odds ao vivo, sem comparação de casas.
- **Gestão de banca pessoal do apostador.** As unidades são a métrica do tipster, não a carteira do subscritor.
- **Venda de tips avulso.** O modelo é subscrição à comunidade, não compra individual.

## 5. Utilizadores

| Papel | Âmbito | O que precisa |
| --- | --- | --- |
| **Visitante** | Plataforma | Descobrir comunidades e avaliar tipsters antes de criar conta |
| **Membro** | Comunidade | Ver tips das comunidades que subscreve, estatísticas, chat |
| **Dono** | Comunidade | Criar a comunidade, definir preço, publicar tips, nomear moderadores, moderar chat |
| **Moderador** | Comunidade | Publicar e resolver tips, moderar chat. Não mexe em preço nem em membros |
| **Admin** | Plataforma | Suspender comunidades e utilizadores, resolver denúncias |

Um utilizador acumula papéis: pode ser dono da comunidade A, moderador da B e membro da C.

## 6. Fluxos principais

### 6.1 Publicar um lote de tips (dono/moderador)

1. **Início:** o tipster abre *Nova publicação* na sua comunidade e cola um bloco no formato do SeerHub (uma linha por tip).
2. A gramática decompõe cada linha em data, equipas, mercado, seleção, odd e stake. Linhas que não encaixam na gramática são marcadas com a posição exata do erro.
3. Para cada linha válida, o resolvedor de equipas procura o jogo correspondente entre os já sincronizados, por alias conhecido primeiro e por semelhança de nome depois.
4. O tipster vê um ecrã de revisão: cada tip com emblemas, equipas, liga, hora de início, mercado, seleção, odd e stake. As linhas com erro de sintaxe e as equipas ambíguas aparecem destacadas no topo.
5. O tipster corrige o que for preciso. Cada correção de equipa é guardada como alias, por isso o mesmo erro não volta a acontecer.
6. O tipster confirma. As tips passam a `PENDENTE` e ficam visíveis no feed.
7. **Fim:** os subscritores recebem notificação e as tips aparecem no feed ordenadas por hora de início.

**Estado vazio:** comunidade sem tips mostra um exemplo colável do formato e um botão *Colar o meu primeiro lote*.
**Falha:** linhas inválidas nunca bloqueiam as válidas — o lote é publicado parcialmente e as linhas com erro ficam no editor com a mensagem do problema. O texto original nunca se perde.

### 6.2 Descobrir e subscrever (visitante → membro)

1. **Início:** o visitante chega ao Hub.
2. Vê cartões de comunidades com nome, descrição, preço, número de membros e **estatísticas dos últimos 30 dias** (yield, nº de tips, taxa de acerto).
3. Abre o perfil público de uma comunidade: histórico completo de estatísticas, tips já **resolvidas** com seleções visíveis, e tips **abertas** com as seleções tapadas.
4. Clica em *Subscrever*, cria conta se ainda não tiver, e a subscrição fica ativa imediatamente (sem cobrança na v1).
5. **Fim:** as tips abertas ficam visíveis e a comunidade aparece no feed pessoal.

**Estado vazio:** Hub sem comunidades mostra convite a criar a primeira.
**Falha:** subscrever uma comunidade suspensa mostra *Esta comunidade está suspensa* e não cria subscrição.

### 6.3 Resolução de uma tip

1. **Início:** um jogo referenciado por uma seleção pendente termina.
2. O sincronizador de resultados atualiza o `Fixture` com o resultado final.
3. O resolvedor automático avalia cada seleção pendente desse jogo cujo mercado seja auto-resolvível e marca-a `GANHA`, `PERDIDA` ou `ANULADA`.
4. Quando **todas** as seleções de uma tip estão resolvidas, a tip resolve: ganha se todas ganharam, perdida se alguma perdeu, anulada se todas anuladas.
5. Seleções em mercados não auto-resolvíveis ficam `PENDENTE_MANUAL` e aparecem numa fila para o tipster fechar.
6. **Fim:** as estatísticas da comunidade refletem o resultado; a tip mostra o resultado real e o badge da fonte da resolução (automática ou manual).

**Falha:** jogo adiado ou cancelado → seleções ficam `PENDENTE_MANUAL` com o motivo, nunca resolvem sozinhas para perdida.

## 7. Requisitos

### R1 — Contas e autenticação
**Prioridade:** Must
**Descrição:** Registo e sessão com email e password. Papel global `USER` ou `ADMIN`.
**Critérios de aceitação:**
- [ ] Registo com email único, password com mínimo 10 caracteres, guardada com BCrypt (força ≥ 10).
- [ ] Login devolve access token JWT (validade 15 min) e refresh token (validade 30 dias, rotativo, revogável).
- [ ] Email duplicado devolve 409 com mensagem que não revela se a conta existe noutro contexto.
- [ ] Endpoint protegido sem token válido devolve 401; com token válido mas papel insuficiente devolve 403.
- [ ] Logout invalida o refresh token usado.

### R2 — Criação e gestão de comunidades
**Prioridade:** Must
**Descrição:** Qualquer utilizador autenticado cria uma comunidade imediatamente, sem aprovação.
**Critérios de aceitação:**
- [ ] Criar comunidade exige nome (3–60 caracteres) e gera um `slug` único; colisão acrescenta sufixo numérico.
- [ ] O criador fica automaticamente com papel `OWNER` e membership ativa.
- [ ] O dono edita nome, descrição, avatar, banner e preço mensal; o preço é guardado em cêntimos inteiros.
- [ ] Alterar o preço não afeta subscrições já ativas.
- [ ] Um utilizador não pode ter mais de 3 comunidades ativas na v1.
- [ ] Comunidade suspensa por admin deixa de aparecer no Hub e recusa novas subscrições, mas os membros existentes mantêm acesso de leitura.

### R3 — Subscrições e controlo de acesso
**Prioridade:** Must
**Descrição:** A subscrição é o estado que dá acesso ao conteúdo premium da comunidade. Sem gateway de pagamento na v1.
**Critérios de aceitação:**
- [ ] Subscrever cria uma `CommunityMembership` com estado `ATIVA`, papel `MEMBER` e `expires_at` a 30 dias.
- [ ] Cancelar coloca o estado em `CANCELADA` mas mantém o acesso até `expires_at`.
- [ ] Uma tarefa diária marca como `EXPIRADA` toda a membership `ATIVA` ou `CANCELADA` com `expires_at` no passado.
- [ ] Um utilizador pode ter membership ativa em N comunidades em simultâneo, sem limite.
- [ ] O dono e os moderadores têm acesso total sem subscrição.
- [ ] Um pedido a conteúdo premium com membership expirada devolve 403 e o cliente mostra o ecrã de re-subscrição.

### R4 — Papéis e permissões
**Prioridade:** Must
**Descrição:** Autorização por comunidade, verificada no servidor em todos os endpoints.
**Critérios de aceitação:**
- [ ] O dono nomeia e remove moderadores de entre os membros ativos.
- [ ] Um moderador pode publicar e resolver tips e apagar mensagens de chat; não pode alterar preço, nomear moderadores nem apagar a comunidade.
- [ ] Um membro pode ler tips e escrever no chat; não pode publicar tips.
- [ ] Nenhuma verificação de permissão depende apenas do cliente: existe um teste de integração por papel × endpoint protegido que confirma o 403.
- [ ] O papel efetivo do utilizador em cada comunidade é devolvido pela API para o frontend decidir o que renderizar.

### R5 — Sincronização de dados de futebol
**Prioridade:** Must
**Descrição:** Ligas, equipas, emblemas, jogos e resultados vêm da API-Football e são guardados em Postgres. A base de dados é a fonte de verdade em runtime; a API externa nunca é chamada no caminho do pedido do utilizador.
**Critérios de aceitação:**
- [ ] Uma tarefa agendada sincroniza os jogos das ligas configuradas para os próximos 7 dias, com periodicidade configurável (por omissão 12 horas).
- [ ] A sincronização de resultados é **a pedido, não por sondagem fixa**: uma tarefa agendada frequente verifica **em base de dados** se existe alguma seleção pendente cujo jogo já começou há mais de duas horas e ainda não está `FINISHED`; só nesse caso chama a API externa, e agrupa todos os jogos em falta por liga e dia num único pedido. Um dia sem tips pendentes custa zero chamadas.
- [ ] O orçamento diário de chamadas é configurável (`API_FOOTBALL_DAILY_BUDGET`, por omissão 100) e é respeitado: quando o consumo do dia atinge o limite, a sincronização deixa de chamar a API, regista o facto e retoma no dia seguinte — nunca esgota a quota a meio de um ciclo nem falha silenciosamente.
- [ ] Um teste prova que, com N seleções pendentes espalhadas por M jogos da mesma liga e do mesmo dia, é feita **uma** chamada e não M.
- [ ] Equipas e ligas são guardadas com nome, nome curto, país e URL do emblema; o emblema é servido através de cache local para não depender do CDN externo em cada render.
- [ ] Cada equipa guarda também um nome normalizado (minúsculas, sem acentos, sem prefixos e sufixos de clube), gerado na sincronização, com índice de trigramas para a correspondência difusa do R6.
- [ ] O fornecedor externo está atrás de uma interface `FootballDataProvider`; existe uma implementação de teste com dados fixos que permite correr toda a suite sem rede nem chave de API.
- [ ] Se a API externa falhar ou atingir o limite de quota, o erro é registado e a sincronização volta a tentar no ciclo seguinte; nenhum pedido de utilizador falha por causa disso.
- [ ] O número de chamadas por dia é contabilizado e exposto numa métrica, para caber no limite do plano contratado.

### R6 — Importação de tips por texto estruturado
**Prioridade:** Must
**Descrição:** Converter um bloco de texto num formato conhecido em tips estruturadas ligadas a jogos reais, com uma gramática determinística e correspondência difusa de nomes de equipas. Sem custo por operação e sem dependência externa em runtime.

**Formato (v1):** uma tip por linha; acumuladores num bloco indentado sob `MULT`.

```
28/07 Benfica - Porto | 1X2 1 | 1.85 | 2u
28/07 Arsenal - Chelsea | O2.5 | 1.90 | 1u
29/07 Girona - Real Madrid | BTTS S | 1.72 | 1u
MULT | 2u
  29/07 Bayern - Leipzig | 1 | 1.40
  30/07 Inter - Napoli | 1X | 1.55
```

**Critérios de aceitação:**
- [ ] A gramática aceita: data (`dd/mm`, opcional se o par de equipas for inequívoco na janela sincronizada), par de equipas separado por `-` ou `x`, mercado, seleção, odd decimal e stake em unidades (`2u`, `1.5u`).
- [ ] Mercados reconhecidos com abreviaturas: `1X2` (`1`/`X`/`2`), dupla hipótese (`1X`/`12`/`X2`), mais/menos golos (`O2.5`/`U3.5`), ambas marcam (`BTTS S`/`BTTS N`) e handicap (`H-1`/`H+1.5`).
- [ ] O bloco `MULT` cria uma tip com N seleções; a odd total é o produto das odds das seleções e o stake é declarado uma única vez na linha `MULT`.
- [ ] Uma linha inválida não invalida o lote: as válidas seguem para revisão e as inválidas são devolvidas com número da linha, coluna e o que era esperado nessa posição.
- [ ] A correspondência de equipa é feita por três vias, nesta ordem: alias exato conhecido → nome normalizado exato (minúsculas, sem acentos, sem prefixos e sufixos de clube como `FC`, `SL`, `CF`, `AC`) → semelhança por trigramas com limiar mínimo configurável.
- [ ] Uma linha cujo par de equipas corresponda a exatamente um jogo na janela sincronizada é ligada automaticamente a esse `fixture_id`.
- [ ] Zero candidatos acima do limiar, ou mais do que um, marca a linha como ambígua e apresenta no ecrã de revisão os candidatos ordenados por semelhança, para escolha com um clique.
- [ ] Escolher um candidato no ecrã de revisão guarda um `TeamAlias` (texto escrito → equipa), de forma que a mesma escrita passe a resolver automaticamente nos lotes seguintes da mesma comunidade.
- [ ] O texto original é sempre guardado num registo `TipImport`, associado ao lote, mesmo quando nenhuma linha é válida.
- [ ] Um conjunto de teste fixo com 5 tips simples e 1 acumulador de 3 jogos produz 6 tips com 8 seleções, todas ligadas a jogos reais, sem qualquer chamada de rede.
- [ ] O conjunto de teste inclui variantes de escrita reais — `Sporting`, `Sporting CP`, `Sporting Lisbon`, `Man Utd`, `Manchester United`, `Inter`, `Internazionale` — e todas resolvem para a equipa correta.
- [ ] O parse de um bloco de até 2000 caracteres, incluindo a correspondência de equipas, responde abaixo de 200 ms no percentil 95.
- [ ] O parser está atrás da interface `TipTextParser(rawText, catalog) → ParseResult`, para permitir substituir ou complementar a implementação sem tocar no ecrã de revisão nem no modelo de dados.

### R7 — Revisão e publicação de tips
**Prioridade:** Must
**Descrição:** Nada resultante do parser é publicado sem confirmação humana.
**Critérios de aceitação:**
- [ ] O ecrã de revisão mostra cada tip com emblemas, equipas, liga, hora de início, mercado, seleção, odd, stake e odd total.
- [ ] Todos os campos são editáveis antes de publicar, incluindo trocar o jogo associado por outro do catálogo.
- [ ] Tips não podem ser publicadas para jogos cuja hora de início já passou; a tentativa é bloqueada com mensagem explícita.
- [ ] Publicar coloca as tips em `PENDENTE` e regista `published_at`.
- [ ] Uma tip publicada não pode ter seleções, odds ou stake alterados; só pode ser anulada com motivo registado.
- [ ] Existe um caminho para adicionar uma tip manualmente sem passar pelo parser.
- [ ] Linhas com erro de sintaxe e equipas ambíguas aparecem no topo do ecrã de revisão, antes das tips já resolvidas, para o tipster não publicar sem reparar nelas.

### R8 — Resolução de tips
**Prioridade:** Must
**Descrição:** Automática quando o mercado o permite, manual como recurso, com a fonte sempre registada.
**Critérios de aceitação:**
- [ ] Mercados auto-resolvíveis: resultado final (1X2), dupla hipótese, mais/menos golos, ambas marcam, handicap europeu e handicap asiático de linha inteira ou meia.
- [ ] Handicap asiático de linha de quarto (.25/.75) e qualquer mercado fora do catálogo ficam `PENDENTE_MANUAL`.
- [ ] Um jogo com estado adiado, cancelado ou abandonado nunca resolve automaticamente para perdida; as seleções ficam `PENDENTE_MANUAL` com o motivo.
- [ ] Uma tip só resolve quando todas as suas seleções estiverem resolvidas.
- [ ] Regra da tip múltipla: ganha se todas as seleções ganharem; perdida se pelo menos uma perder; seleções anuladas são tratadas como odd 1.00 e não invalidam a tip.
- [ ] Cada seleção regista a fonte da resolução (`AUTOMATICA` ou `MANUAL`) e, se manual, quem a fechou e quando.
- [ ] O feed e o perfil da comunidade mostram visivelmente a proporção de tips resolvidas manualmente, porque essa proporção é um sinal de confiança.
- [ ] Uma resolução manual sobre uma seleção já resolvida automaticamente é bloqueada.
- [ ] Um teste cobre os 6 mercados auto-resolvíveis com resultados que produzem ganho, perda e anulação em cada um.

### R9 — Estatísticas
**Prioridade:** Must
**Descrição:** Métricas calculadas exclusivamente a partir de tips resolvidas, com stake em unidades e odds decimais.
**Critérios de aceitação:**
- [ ] Métricas por comunidade: nº de tips resolvidas, taxa de acerto, unidades apostadas, lucro em unidades, yield, odd média.
- [ ] Fórmula do lucro: por tip ganha `stake × (odd_total − 1)`; por tip perdida `−stake`; por tip anulada `0`.
- [ ] Fórmula do yield: `lucro_total / unidades_apostadas`, apresentado em percentagem com uma casa decimal.
- [ ] Filtros por período (7/30/90 dias, sempre), por mercado e por liga.
- [ ] Gráfico de evolução do lucro acumulado ao longo do tempo.
- [ ] Tips pendentes nunca entram nos cálculos.
- [ ] As estatísticas do perfil público são idênticas às internas; não existe caminho que permita ao tipster ocultar ou filtrar resultados negativos.
- [ ] A página de estatísticas de uma comunidade com 1000 tips resolvidas responde em menos de 500 ms no percentil 95.

### R10 — Hub
**Prioridade:** Must
**Descrição:** A página de descoberta e agregação — a razão de o produto existir.
**Critérios de aceitação:**
- [ ] Visitante não autenticado vê a lista de comunidades ativas com cartão: nome, avatar, descrição curta, preço, nº de membros e métricas de 30 dias.
- [ ] Ordenação por yield de 30 dias, nº de membros e data de criação; pesquisa por nome.
- [ ] Utilizador autenticado vê adicionalmente um feed agregado com as tips abertas de **todas** as comunidades que subscreve, ordenadas por hora de início do jogo.
- [ ] Cada tip no feed agregado identifica a comunidade de origem.
- [ ] Estado vazio para quem não subscreve nada: sugestões de comunidades em vez de feed vazio.
- [ ] Comunidades com menos de 10 tips resolvidas mostram a etiqueta *Amostra reduzida* em vez de um yield potencialmente enganador.

### R11 — Visibilidade e teaser
**Prioridade:** Must
**Descrição:** Quem não subscreve vê o desempenho, não as seleções abertas.
**Critérios de aceitação:**
- [ ] Não-subscritor vê: todas as estatísticas, e as tips já **resolvidas** com seleções e resultado completos.
- [ ] Não-subscritor não vê: seleções, odds ou stake de tips `PENDENTE`. A existência da tip e a hora do jogo são visíveis; o conteúdo é substituído por um bloqueio com apelo à subscrição.
- [ ] A filtragem é feita no servidor: a resposta da API a um não-subscritor **não contém** os campos ocultos. Um teste de integração confirma a ausência dos campos no payload, não apenas a sua não-renderização.
- [ ] Uma tip transita automaticamente para visibilidade pública assim que resolve.
- [ ] O chat da comunidade é totalmente invisível para não-subscritores.

### R12 — Chat de comunidade
**Prioridade:** Should
**Descrição:** Uma sala por comunidade, em tempo real, só para membros ativos.
**Critérios de aceitação:**
- [ ] Ligação WebSocket autenticada; a subscrição do tópico da comunidade é recusada a quem não tenha membership ativa.
- [ ] Mensagens persistidas; ao entrar carrega as últimas 50 com paginação para trás.
- [ ] Uma mensagem enviada aparece nos outros clientes ligados em menos de 1 segundo em condições normais.
- [ ] Dono e moderadores apagam qualquer mensagem; o autor apaga a sua própria. Apagar é lógico (`deleted_at`), com o conteúdo substituído por *Mensagem removida*.
- [ ] Limite de 5 mensagens por 10 segundos por utilizador; excedente devolve erro sem quebrar a ligação.
- [ ] Limite de 2000 caracteres por mensagem.
- [ ] Perda de ligação reconecta automaticamente e recupera as mensagens do intervalo.

### R13 — Notificações
**Prioridade:** Should
**Descrição:** Dentro da aplicação, em tempo real, persistidas.
**Critérios de aceitação:**
- [ ] Eventos que geram notificação: nova tip publicada numa comunidade subscrita; tip resolvida; subscrição a expirar em 3 dias; promoção a moderador.
- [ ] Notificações são persistidas e entregues por WebSocket a quem estiver ligado; quem não estiver vê-as ao entrar.
- [ ] Sino com contador de não lidas; marcar uma como lida e marcar todas como lidas.
- [ ] Publicar um lote de 10 tips gera **uma** notificação agregada por subscritor, não dez.
- [ ] Cada utilizador pode desligar cada tipo de notificação por comunidade.
- [ ] Notificações com mais de 90 dias são eliminadas por tarefa agendada.

### R14 — Painel de administração
**Prioridade:** Should
**Descrição:** Moderação reativa da plataforma.
**Critérios de aceitação:**
- [ ] Listagem de comunidades e utilizadores com pesquisa e filtro por estado.
- [ ] Suspender e reativar uma comunidade; suspender e reativar um utilizador.
- [ ] Fila de denúncias de comunidades com transição de estado e nota do admin.
- [ ] Toda a ação de admin fica registada num log de auditoria imutável com autor, alvo, ação e timestamp.
- [ ] O painel é inacessível a quem não tenha papel global `ADMIN`, verificado no servidor.

### R15 — Infraestrutura e operação
**Prioridade:** Must
**Descrição:** Ambiente reproduzível em contentores, migrações versionadas, observabilidade mínima.
**Critérios de aceitação:**
- [ ] `docker compose up` arranca Postgres, backend e frontend e a aplicação fica utilizável sem passos manuais além de preencher o `.env`.
- [ ] Esquema da base de dados gerido por Flyway; nenhuma alteração de esquema é feita por auto-DDL do Hibernate em ambiente não-local.
- [ ] Existe um `.env.example` com todas as variáveis necessárias e nenhum segredo real.
- [ ] A chave da API-Football e o segredo JWT vêm exclusivamente de variáveis de ambiente; nenhum segredo é commitado. A v1 não tem qualquer outra dependência externa paga.
- [ ] Endpoint de health check que verifica a base de dados.
- [ ] Logs estruturados com correlação de pedido; erros não expõem stack traces ao cliente.
- [ ] Seed de desenvolvimento que cria utilizadores, comunidades e tips de exemplo para o ambiente local arrancar com dados.

## 8. Modelo de dados

Entidades principais. Todos os timestamps em UTC; todos os montantes monetários em cêntimos inteiros; todas as odds em formato decimal.

```
User(id, email UNIQUE, password_hash, username UNIQUE, display_name,
     avatar_url, global_role[USER|ADMIN], status[ACTIVE|SUSPENDED], created_at)

Community(id, owner_id→User, name, slug UNIQUE, description, avatar_url,
          banner_url, price_monthly_cents, currency='EUR',
          status[ACTIVE|SUSPENDED], created_at)

CommunityMembership(id, community_id→Community, user_id→User,
                    role[OWNER|MODERATOR|MEMBER],
                    status[ACTIVE|CANCELLED|EXPIRED],
                    joined_at, expires_at)
                    UNIQUE(community_id, user_id)

TipImport(id, community_id, author_id, raw_text, parser_version,
          status[OK|PARTIAL|FAILED], line_errors JSONB, created_at)

Tip(id, community_id→Community, author_id→User, import_id→TipImport NULL,
    note, stake_units NUMERIC(4,2), total_odds NUMERIC(6,3), bookmaker NULL,
    status[PENDING|WON|LOST|VOID|PENDING_MANUAL],
    published_at, settled_at, created_at)

TipSelection(id, tip_id→Tip, fixture_id→Fixture,
             market[MATCH_RESULT|DOUBLE_CHANCE|OVER_UNDER|BTTS|HANDICAP],
             selection, line NUMERIC(4,2) NULL, odds NUMERIC(6,3),
             status[PENDING|WON|LOST|VOID|PENDING_MANUAL],
             settlement_source[AUTO|MANUAL] NULL, settled_by→User NULL, settled_at)

League(id, provider_id UNIQUE, name, country, logo_url, season, active)
Team(id, provider_id UNIQUE, name, normalized_name, short_name, country, logo_url)

TeamAlias(id, team_id→Team, alias, normalized_alias,
          community_id→Community NULL, created_by→User, created_at)
          UNIQUE(normalized_alias, community_id)
          -- community_id NULL = alias global; preenchido = alias só daquela comunidade
Fixture(id, provider_id UNIQUE, league_id→League, home_team_id→Team,
        away_team_id→Team, kickoff_at,
        status[SCHEDULED|LIVE|FINISHED|POSTPONED|CANCELLED],
        home_score NULL, away_score NULL, last_synced_at)

ChatMessage(id, community_id→Community, author_id→User, content,
            created_at, deleted_at NULL, deleted_by→User NULL)

Notification(id, user_id→User, type, community_id NULL, tip_id NULL,
             payload JSONB, read_at NULL, created_at)

AdminAuditLog(id, admin_id→User, action, target_type, target_id, note, created_at)
```

**Índices necessários pela carga esperada:** `Fixture(kickoff_at)` para o catálogo do parser; `Fixture(status, kickoff_at)` para o sincronizador de resultados; `TipSelection(fixture_id, status)` para o resolvedor; `Tip(community_id, status, published_at)` para o feed; `CommunityMembership(user_id, status)` para o feed agregado; `ChatMessage(community_id, created_at DESC)` para o histórico; **índice GIN de trigramas** sobre `Team(normalized_name)` e `TeamAlias(normalized_alias)` para a correspondência difusa.

**Retenção:** notificações apagadas aos 90 dias. Tips, seleções, jogos e mensagens de chat são permanentes — o histórico é o produto.

## 9. Abordagem técnica

**Backend:** Spring Boot 3 (Java 21). Spring Web, Spring Security, Spring Data JPA, Spring WebSocket com STOMP, Spring Scheduling para os sincronizadores. Flyway para migrações.

**Frontend:** React 18 + TypeScript + Vite. TanStack Query para estado de servidor, React Router para navegação, Tailwind para estilos. Cliente STOMP sobre SockJS para chat e notificações.

**Base de dados:** PostgreSQL 16 com a extensão `pg_trgm` ativa.

**Parser de tips:** gramática determinística escrita à mão (tokenizer por linha + regras por campo), sem dependências externas. A correspondência de equipa usa `pg_trgm` — alias exato, depois nome normalizado exato, depois `similarity()` com limiar configurável. Custo por operação: zero. Tempo de resposta: milissegundos. Toda a suite de testes corre sem rede.

**Porquê não um modelo de linguagem:** a versão anterior desta spec usava a Claude API com o catálogo de jogos no pedido. Custava ~€0,11 por parse (dominado pelos ~6.000 tokens do catálogo), demorava ~30 segundos e não era determinístico, logo não era testável. A gramática troca isso por fricção de onboarding: o tipster tem de aprender um formato. O ecrã de revisão do R7 absorve as falhas do parser, o que torna a troca vantajosa — não é preciso perfeição, só é preciso ser melhor que inserir tip a tip.

**Se o formato se revelar rígido demais:** a interface `TipTextParser` permite acrescentar um segundo passo que envia à Claude API **apenas as linhas que a gramática rejeitou**, com os candidatos daquela linha em vez do catálogo inteiro. Custo por linha na ordem de décimas de cêntimo, e a gramática continua a resolver a esmagadora maioria dos casos de graça.

**Dados de futebol:** API-Football, atrás de uma interface `FootballDataProvider` com uma implementação real e uma de teste com dados fixos. O plano gratuito tem limite diário baixo, o que torna a cache em Postgres obrigatória — que é o que queremos de qualquer forma, porque a base de dados é a fonte de verdade em runtime.

**Contentores:** `docker-compose.yml` com Postgres, backend e frontend. Volume persistente para a base de dados e outro para uploads de imagens.

**Decisões que valem a pena justificar:**
- *Fornecedor de futebol atrás de interface* — o plano gratuito pode não chegar; trocar de fornecedor não deve tocar em lógica de negócio.
- *Aliases aprendidos a partir das correções* — o vocabulário de cada tipster é estável e pequeno; ao fim de duas semanas de uso o parser conhece a forma como ele escreve os nomes das equipas, e a taxa de ambiguidade tende para zero sem trabalho de manutenção.
- *Modelo de tip com N seleções desde o início* — acumuladores são pão-nosso dos tipsters; acrescentar a tabela depois seria uma migração dolorosa com dados em produção.
- *Filtragem de visibilidade no servidor* — o teaser é o motor de conversão do produto; se fosse filtrado no cliente, bastaria abrir o painel de rede para contornar a subscrição.

## 10. Casos de fronteira

- **Jogo adiado ou cancelado** após a tip ser publicada → seleções para `PENDENTE_MANUAL` com motivo, nunca resolução automática para perdida.
- **Tip publicada para um jogo já a decorrer** → bloqueada na publicação por validação de `kickoff_at`.
- **Nome de equipa sem correspondência** → linha marcada como ambígua com os candidatos mais parecidos; nunca ligada a um jogo por adivinhação.
- **Nome de equipa com dois candidatos igualmente prováveis** (ex.: dois jogos do mesmo clube na mesma semana) → desambiguação por data se a linha a incluir; caso contrário, escolha explícita no ecrã de revisão.
- **Nenhuma linha do bloco é válida** → o editor devolve o texto original com o erro anotado linha a linha, mais o caminho manual.
- **Tipster cola o formato do Telegram dele em vez do formato do SeerHub** → todas as linhas falham a gramática, o que é indistinguível de um bug para quem está a usar pela primeira vez. Mitigação: quando *nenhuma* linha valida, o erro mostra o exemplo do formato lado a lado com o texto colado, em vez da lista de erros de sintaxe.
- **Alias aprendido errado** (o tipster escolheu a equipa errada e guardou o alias) → o ecrã de revisão permite corrigir e reescrever o alias; aliases são por comunidade, por isso um erro não contamina outras.
- **API-Football em baixo durante horas** → tips continuam a ser publicáveis contra os jogos já em cache; resolução automática atrasa e recupera sozinha.
- **API-Football sem os jogos de uma liga menor** → as linhas dessas tips ficam sem jogo associado; o tipster pode publicar na mesma com o jogo por associar, mas essas tips só resolvem manualmente.
- **Subscrição expira com tips pendentes** → o utilizador deixa de ver as seleções abertas mas mantém acesso às tips que já viu resolvidas (o histórico é público).
- **Dono apaga a conta** → a comunidade não é apagada em cascata; fica suspensa e o admin decide. As estatísticas históricas sobrevivem.
- **Duas resoluções concorrentes** da mesma seleção (automática e manual) → resolvido por bloqueio otimista na seleção; a segunda falha com conflito.
- **Utilizador subscreve duas vezes a mesma comunidade** → impedido pela restrição de unicidade `(community_id, user_id)`.

## 11. Pressupostos

Decisões tomadas por mim, não pelo utilizador. É aqui que deves olhar com atenção.

- **PRESSUPOSTO:** Autenticação própria com Spring Security + JWT, sem OAuth social nem Keycloak — *decidido porque* é a opção mais simples que satisfaz os requisitos e não acrescenta um contentor; *se estiver errado,* acrescentar OAuth social depois é trabalho localizado no módulo de autenticação.
- **PRESSUPOSTO:** O tipster aceita aprender um formato de texto em troca de não inserir tips uma a uma — *decidido porque* o formato é legível e colável no Telegram dele sem parecer código, e porque elimina custo por operação, latência de 30s e indeterminismo; *se estiver errado,* perdes tipsters no onboarding. É o pressuposto de produto mais arriscado desta revisão, e o mais barato de testar: mostra o formato a um tipster real antes de escrever código.
- **PRESSUPOSTO:** Stake medido em unidades (0.25 a 10), não em dinheiro — *decidido porque* é a convenção do mercado de tipsters e torna as estatísticas comparáveis entre comunidades; *se estiver errado,* obriga a migrar dados de stake e a recalcular todas as estatísticas históricas. **É o pressuposto mais caro de reverter.**
- **PRESSUPOSTO:** Odds em formato decimal europeu — *decidido porque* é o formato usado em Portugal e o único que a fórmula de lucro assume; *se estiver errado,* é conversão de apresentação, sem migração.
- **PRESSUPOSTO:** Preço em EUR, guardado em cêntimos inteiros — *decidido porque* evita erros de vírgula flutuante em dinheiro; *se estiver errado,* acrescentar moedas exige uma coluna e conversão na apresentação.
- **PRESSUPOSTO:** Sincronização agendada de jogos das próximas 168 horas — *decidido porque* cobre o horizonte típico de publicação de tips e cabe no limite de quota do plano gratuito; *se estiver errado,* é mudar uma constante de configuração.
- **PRESSUPOSTO:** Chat e notificações sobre a mesma ligação WebSocket/STOMP — *decidido porque* evita duas ligações por cliente e reaproveita a infraestrutura; *se estiver errado,* separar os tópicos é refactor de baixo risco.
- **PRESSUPOSTO:** Uploads de imagens guardados num volume Docker local, sem S3 — *decidido porque* a v1 não tem escala que o justifique; *se estiver errado,* migrar para S3 exige uma abstração de storage e mover ficheiros existentes.
- **PRESSUPOSTO:** Interface e conteúdo apenas em português de Portugal — *decidido porque* é o mercado inicial; *se estiver errado,* internacionalizar depois toca em toda a UI.
- **PRESSUPOSTO:** Escala v1 na ordem das dezenas de comunidades, milhares de utilizadores e dezenas de milhares de tips — *decidido porque* nada no brief sugere mais; *se estiver errado,* o modelo aguenta, mas o feed agregado e as estatísticas precisam de vistas materializadas.
- **PRESSUPOSTO:** Limite de 3 comunidades ativas por utilizador — *decidido porque* trava criação abusiva sem fila de aprovação; *se estiver errado,* é uma constante.

## 12. Questões em aberto

| # | Questão | Bloqueia | Responsável | Quando |
| --- | --- | --- | --- | --- |
| ~~Q1~~ | **RESOLVIDA em 2026-07-27.** Plano gratuito da API-Football, 100 chamadas/dia. Ligas cobertas no arranque: Primeira Liga, Premier League, La Liga, Serie A, Bundesliga e Ligue 1. A quota obrigou a redesenhar o calendário do R5 para sincronização a pedido — ver a nota de revisão no topo. | R5, R8 | — | Fechada |
| Q2 | O formato do R6 aguenta o que um tipster real escreve? Mostrar o exemplo a um ou dois tipsters e pedir que escrevam um lote típico nesse formato, antes de existir código. | R6, R7 | utilizador | Antes de M2 |
| Q3 | Quando entrarem pagamentos reais: o que acontece a uma subscrição ativa se a comunidade for suspensa a meio do período? | Pagamentos (pós-v1) | utilizador | Antes de integrar Stripe |
| Q4 | Precisa de um período experimental gratuito por comunidade? Afeta o modelo de membership. | R3 | utilizador | Antes de M1 terminar |

## 13. Milestones

| Milestone | Contém | Resultado demonstrável |
| --- | --- | --- |
| **M1 — Fundações** | R1, R2, R3, R4, R15 | Criar conta, criar comunidade com preço, subscrever, nomear moderador. `docker compose up` arranca tudo. |
| **M2 — Tips** | R5, R6, R7 | Colar um bloco de texto e ver 6 tips estruturadas com emblemas e horas de início reais, revê-las e publicá-las. |
| **M3 — Verificação** | R8, R9 | As tips fecham sozinhas quando os jogos acabam; a página de estatísticas mostra yield real, com a proporção de resolução manual visível. |
| **M4 — Hub** | R10, R11 | Um visitante compara comunidades por yield, vê tips resolvidas mas não as abertas, e subscreve. |
| **M5 — Social** | R12, R13 | Chat em tempo real na comunidade e sino de notificações a disparar quando saem tips novas. |
| **M6 — Operação** | R14 | Painel de admin com suspensão de comunidades e log de auditoria. |

M1 a M4 constituem o produto mínimo defensável: sem M3 e M4 o SeerHub é um Telegram com login.

## 14. Fora do âmbito deste documento

Deixado ao critério da implementação: estrutura de pacotes e camadas do backend, escolha de biblioteca de gráficos no frontend, layout e paleta visual concretos, estratégia de testes end-to-end, e configuração de CI.
