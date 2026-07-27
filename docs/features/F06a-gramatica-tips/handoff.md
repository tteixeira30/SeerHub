# F06a — handoff

**Status:** COMPLETE
**Implementado:** 2026-07-27 · Sonnet 5
**Test run:** `./mvnw test` → `Tests run: 340, Failures: 0, Errors: 0, Skipped: 0` (274 da
baseline + 66 novos), corrido três vezes seguidas (duas normais + uma com
`API_FOOTBALL_KEY= API_FOOTBALL_DAILY_BUDGET= API_FOOTBALL_SEASON=` vazias), sempre igual ·
`cd frontend && npm test` → `Test Files 12 passed (12)`, `Tests 26 passed (26)`, inalterados ·
zero ficheiros do frontend tocados.

## O que agora existe

Um parser determinístico, sem Spring, sem base de dados e sem rede, que converte um bloco de
texto colado numa lista de `ParsedTip`/`ParsedSelection` — data, par de equipas em texto cru,
mercado, seleção, odd e stake — e numa lista de `ParseError` com linha, coluna e mensagem em
português para tudo o que não bate certo. Reconhece o catálogo de cinco mercados da spec com
abreviaturas (`1X2`, dupla hipótese, mais/menos golos, ambas marcam, handicap), o bloco `MULT`
com N seleções e odd total como produto, e nunca lança exceção — todo o texto de entrada,
por pior que seja, devolve um `ParseResult`. Nenhuma equipa é resolvida, nenhum `fixture_id`
é ligado, nada é persistido: todas as seleções produzidas trazem `TeamResolution.porResolver()`
e `fixtureId == null`, prontas para F06b decorar.

## Público — `TipTextParser` e `ParseResult`, assinaturas reais

```java
// pt.seerhub.tips.parser — o contrato que F06b e F07 constroem em cima, implementado exatamente como desenhado.

public interface TipTextParser {
    ParseResult parse(String rawText, TipCatalog catalog);
}

// A implementação de F06a. F06b NÃO EDITA este ficheiro — decora-o (ver "Como F06b entra" abaixo).
@Service
public class GrammarTipTextParser implements TipTextParser { ... }

public interface TipCatalog {
    ZoneId fuso();
    LocalDate hoje();               // F06a lê isto — inferência do ano da data
    List<FixtureView> jogos();      // F06a IGNORA isto por completo — é F06b que lê
}

public record SimpleTipCatalog(ZoneId fuso, LocalDate hoje, List<FixtureView> jogos) implements TipCatalog {
    public static SimpleTipCatalog vazio(Clock clock);
    public static SimpleTipCatalog de(Clock clock, List<FixtureView> jogos);
}

public record ParseResult(
        String rawText,                 // verbatim, sempre — mesmo quando tudo falha
        String parserVersion,           // "grammar-1"
        List<ParsedTip> tips,
        List<ParseError> erros,
        boolean formatoNaoReconhecido) {
    public ImportStatus estado();       // OK | PARTIAL | FAILED
    public int totalSelecoes();
    public boolean temErros();
    public static ParseResult vazio(String rawText);
}

public record ParsedTip(
        int linha, TipKind tipo, BigDecimal stakeUnidades, BigDecimal oddTotal,
        List<ParsedSelection> selecoes, String textoOriginal) {}

public record ParsedSelection(
        int linha, LocalDate data, String textoDaData,
        TeamRef casa, TeamRef fora,
        Market mercado, String selecao, BigDecimal linhaMercado, BigDecimal odd,
        TeamResolution resolucao) {
    public ParsedSelection comResolucao(TeamResolution nova);   // única porta de escrita da resolução
    public ParsedSelection comData(LocalDate nova);             // única porta de escrita da data
}

public record TeamRef(String texto, int linha, int coluna) {}   // texto cru, sem trim interno, sem normalização

public record TeamResolution(Long fixtureId, ResolutionStatus estado, List<FixtureCandidate> candidatos) {
    public static TeamResolution porResolver();                // é o único que F06a emite
}
public enum ResolutionStatus { POR_RESOLVER, RESOLVIDA, AMBIGUA, SEM_CANDIDATOS }
public record FixtureCandidate(long fixtureId, double semelhanca) {}

public record ParseError(int linha, int coluna, String trecho, ParseErrorCode codigo, String esperado) {}
```

### Que campos F06b preenche, e quais já estão fechados

| Campo | Estado em F06a | O que F06b faz |
| --- | --- | --- |
| `ParsedSelection.casa`/`fora` (`TeamRef`) | **Final.** Texto cru e posição, nunca mudam. | Lê `texto`, nunca escreve aqui — a resolução vive só em `resolucao`. |
| `ParsedSelection.resolucao` | Sempre `TeamResolution.porResolver()`. | Chama `original.comResolucao(nova)` — devolve uma **cópia** com todos os outros 9 campos intactos (provado por `TipParserSeamTest.osMetodosDeCopiaPreservamTodosOsOutrosCampos`). |
| `ParsedSelection.data` | `LocalDate` já com o ano inferido, ou `null` se o tipster omitiu a data. | Só toca nisto para desambiguação por data (§10 da spec) quando há 2 candidatos igualmente prováveis — via `original.comData(nova)`. Normalmente não mexe. |
| `ParsedSelection.textoDaData`, `mercado`, `selecao`, `linhaMercado`, `odd`, `linha` | **Finais.** Não há porta de escrita — só existem `comResolucao`/`comData`. | Nunca altera. Se precisar, é sinal de que a linha F06a/F06b foi mal traçada — registar no handoff de F06b, não acrescentar campos a `ParsedSelection`. |
| `ParsedTip` (todos os campos) | **Final.** | Nunca decorado — a resolução é só ao nível da seleção. |
| `ParseResult.rawText`/`parserVersion`/`tips`/`erros`/`formatoNaoReconhecido` | **Finais.** | O decorador de F06b reconstrói um `ParseResult` novo com as `tips` substituídas (as suas `ParsedSelection` trocadas por `comResolucao(...)`), preservando `rawText`/`erros`/`formatoNaoReconhecido` tal como vieram da gramática. |

### Como F06b entra sem tocar em `GrammarTipTextParser` (decisão D1 do plano)

Por **decoração**, nunca por edição: F06b cria `ResolvingTipTextParser implements TipTextParser`
(`@Service @Primary` — não `@ConditionalOnMissingBean`, que é dependente de ordem de registo),
que recebe `GrammarTipTextParser` no construtor, chama `parse(...)`, e devolve o mesmo
`ParseResult` com as seleções reescritas por `comResolucao(...)`/`comData(...)`. F07 injeta
`TipTextParser` e recebe o `@Primary` que existir. A costura já está provada em F06a por um
decorador de teste — `TipParserSeamTest.umDecoradorEnriqueceOResultadoSemTocarNaGramatica` —
sem tocar em `GrammarTipTextParser`.

### Onde vive o texto cru da equipa e a sua posição

Em `ParsedSelection.casa()`/`fora()`, tipo `TeamRef(String texto, int linha, int coluna)`.
`texto` é **exatamente** o que o tipster escreveu entre a data e o separador (ou entre o
separador e o `|` seguinte) — sem trim interno, sem normalização, sem remoção de sufixos de
clube. `"Sporting"` e `"Sporting Lisbon"` são igualmente válidos aqui e nenhum teste de F06a os
distingue. `linha`/`coluna` são 1-based sobre o texto original tal como colado (nunca uma
versão reescrita — comprovado com tabulações misturadas em
`TipGrammarErrorTest.misturaDeTabulacoesEEspacosNaoDeslocaAsColunas`). É esse par
(`texto`, `linha`, `coluna`) que F06b usa como termo de pesquisa: primeiro passa `texto` por
`TeamNameNormalizer.normalizar(...)` (P3 do handoff de F05 — obrigatório, nunca reimplementar),
depois alias exato → normalizado exato → trigramas.

## Catálogo de mercados — estrutura e como acrescentar um mercado

Duas peças com responsabilidades disjuntas (decisão H3 do plano):

- **`Market`** (`pt.seerhub.tips.domain`) — `enum` de 5 valores, taxonomia persistida, espelho
  exato do `CHECK ck_selection_market` de `V2__baseline_schema.sql`. Burro, sem comportamento.
  É sobre ele que R8 despacha a resolução automática.
- **`MarketDefinition`** (`pt.seerhub.tips.parser.market`) — interface de comportamento:

```java
public interface MarketDefinition {
    Optional<MarketMatch> reconhecer(String tokenDeMercado, String tokenDeSelecao); // tokenDeMercado pode ser null (forma fundida)
    String nome();
    List<String> exemplos();                    // para a mensagem de MERCADO_DESCONHECIDO
    boolean reconheceTokenDeMercado(String token); // só diagnóstico: SELECAO_INVALIDA vs MERCADO_DESCONHECIDO
    List<String> selecoesAceites();              // só diagnóstico: lista as seleções válidas na mensagem de erro
}
public record MarketMatch(Market mercado, String selecao, BigDecimal linha) {} // linha só em OVER_UNDER/HANDICAP
```

`MarketCatalog.PADRAO` é a lista ordenada e imutável das cinco implementações
(`MatchResultMarket`, `DoubleChanceMarket`, `OverUnderMarket`, `BttsMarket`, `HandicapMarket`);
`MarketCatalog.reconhecer(...)` percorre-a e devolve o primeiro match.

**Para acrescentar um mercado novo:** uma classe nova que implemente `MarketDefinition` + uma
linha em `MarketCatalog.PADRAO` — mais uma migração nova para o `CHECK` de
`tip_selections.market` se for uma família de mercado nova (o enum `Market` também ganha um
valor). Nenhuma das duas peças obriga a mexer na outra; acrescentar resolução automática em R8
é um `switch` sobre `Market`, sem tocar no parser.

**As duas portas de diagnóstico** (`reconheceTokenDeMercado`/`selecoesAceites`) são um
acrescento meu ao desenho do plano (que só descrevia `reconhecer`/`nome`/`exemplos`) — necessário
para `FieldDiagnostics` conseguir distinguir `MERCADO_DESCONHECIDO` (nenhum mercado reconhece o
token) de `SELECAO_INVALIDA_PARA_O_MERCADO` (o mercado é conhecido, a seleção escrita não é),
que a spec e os testes 2g/2h exigem como códigos distintos. Ver "Desvios face ao plano".

## Desempenho medido

`TipParserPerformanceTest.p95DoParseDeDoisMilCaracteresFicaMuitoAbaixoDoOrcamento`: 300 amostras
com 200 iterações de aquecimento sobre um bloco de ~2000 carateres (`TipTextSamples.BLOCO_DE_2000_CARACTERES`).

**p95 medido: 0.1841 ms**, contra o orçamento de **20 ms** — cerca de **108× de margem**. Não
foi preciso tocar em nenhum limiar; o algoritmo é O(n) sobre o texto (um tokenizador por
carateres, sem `Pattern` com quantificadores aninhados). O guarda de retrocesso catastrófico
(`entradasAdversariaisNaoProvocamRetrocessoCatastrofico`, `assertTimeoutPreemptively` de 2 s
sobre 5 blocos adversariais de 2000 carateres cada) também passa, sem margem crítica.

Isto é o orçamento da **gramática** apenas. Os 200 ms de ponta a ponta que R6-12 exige na spec
incluem a correspondência de equipas — medição de F06b (ver lista adiada abaixo).

## Lista de F06b, adiada §3.6 do plano — transportada verbatim

Nada nesta lista foi tocado por F06a. O planeador de F06b herda-a tal como está:

| Critério do R6 (texto da spec) | Dono | Porquê não é de F06a |
| --- | --- | --- |
| "A correspondência de equipa é feita por três vias… alias exato → nome normalizado exato → semelhança por trigramas" | **F06b** | Precisa de Postgres com `pg_trgm` e do catálogo sincronizado. |
| "Uma linha cujo par de equipas corresponda a exatamente um jogo na janela sincronizada é ligada automaticamente a esse `fixture_id`" | **F06b** | F06a deixa `fixtureId == null` e `POR_RESOLVER` em todas as seleções. |
| "Zero candidatos acima do limiar, ou mais do que um, marca a linha como ambígua e apresenta os candidatos ordenados" | **F06b** | F06a define o *contentor* (`TeamResolution`, `FixtureCandidate`) e nunca o preenche. |
| "Escolher um candidato no ecrã de revisão guarda um `TeamAlias`" | **F06b** | Escrita em base de dados e ecrã de revisão. |
| "O conjunto de teste inclui variantes de escrita reais — `Sporting`, `Sporting CP`, `Sporting Lisbon`, `Man Utd`, `Manchester United`, `Inter`, `Internazionale`" | **F06b** | Corpus de F06b, não de F06a. Para a gramática, um nome de equipa é uma cadeia opaca — `Sporting` e `Sporting Lisbon` são exatamente igualmente válidos e nenhum teste de F06a distingue os dois. |
| "…todas ligadas a jogos reais" (segunda metade de R6-10) | **F06b** | F06a prova 6 tips / 8 seleções; F06b prova as 8 ligações. |
| "…**incluindo a correspondência de equipas**, responde abaixo de 200 ms no percentil 95" (segunda metade de R6-12) | **F06b** | F06a fixa o orçamento da gramática em 20 ms dos 200 ms e mede-o (0.1841 ms medido); a medição ponta-a-ponta é de F06b. |
| "O texto original é sempre guardado num registo `TipImport`" | **F07** | Persistência. F06a garante o *dado* (`rawText` verbatim + `estado()`), não a escrita. |

Também **desambiguação por data quando há dois candidatos igualmente prováveis** (§10 da spec)
é de F06b: F06a entrega a `LocalDate` já resolvida (ou `null`), que é o insumo dessa
desambiguação.

## Ficheiros criados

**Backend — produção (31 ficheiros `.java`, todos sob `backend/src/main/java/pt/seerhub/tips/`), zero migrações:**

| Pacote | Ficheiros |
| --- | --- |
| `domain` (2) | `Market`, `ImportStatus` |
| `parser` (15) | `TipTextParser`, `GrammarTipTextParser`, `TipCatalog`, `SimpleTipCatalog`, `ParseResult`, `ParsedTip`, `TipKind`, `ParsedSelection`, `TeamRef`, `TeamResolution`, `ResolutionStatus`, `FixtureCandidate`, `ParseError`, `ParseErrorCode`, `TipFormat` |
| `parser.internal` (6) | `LineSplitter`, `Segment`, `MatchFieldScanner`, `OddsScanner`, `StakeScanner`, `FieldDiagnostics` |
| `parser.market` (8) | `MarketDefinition`, `MarketMatch`, `MarketCatalog`, `MatchResultMarket`, `DoubleChanceMarket`, `OverUnderMarket`, `BttsMarket`, `HandicapMarket` |

**Backend — testes (11 ficheiros), sob `backend/src/test/java/pt/seerhub/`:**

`support/TipTextSamples.java`, `tips/{TipGrammarSimpleLineTest, TipGrammarMarketCatalogTest,
TipGrammarMultTest, TipGrammarErrorTest, TipGrammarUnknownFormatTest, TipFixedCorpusTest,
TipFormatExampleTest, TipParserSeamTest, TipParserPerformanceTest, TipsConventionsTest}.java`

**Frontend: zero ficheiros criados ou editados.**

## Ficheiros editados

**Nenhum**, como o plano exigia. Nenhuma migração nova (`V1`–`V4` inalterados, sem `V5`),
nenhuma propriedade de configuração, nenhuma variável de ambiente, `SecurityConfig` intocado,
`application.yml`/`docker-compose.yml`/`.env.example`/`pom.xml` intocados.

## Testes

Backend: **66 testes novos** (274 → 340), suite corrida três vezes seguidas, sempre igual
(duas normais + uma com `API_FOOTBALL_KEY= API_FOOTBALL_DAILY_BUDGET= API_FOOTBALL_SEASON=`
vazias, confirmando que F06a não depende de nenhuma variável do fornecedor de futebol).

| Ficheiro | Testes | Critérios |
| --- | ---: | --- |
| `TipGrammarSimpleLineTest` | 9 | 1a–1i |
| `TipGrammarMarketCatalogTest` | 8 | 2a–2h |
| `TipGrammarMultTest` | 12 | 3a–3l |
| `TipGrammarErrorTest` | 15 | 4a–4m, X1, X2 |
| `TipGrammarUnknownFormatTest` | 4 | E1–E4 |
| `TipFixedCorpusTest` | 3 | 10a–10c |
| `TipFormatExampleTest` | 1 | E5 (números corrigidos — ver desvio 1) |
| `TipParserSeamTest` | 5 | 13a–13e |
| `TipParserPerformanceTest` | 4 | 12a–12d |
| `TipsConventionsTest` | 5 | 2i, X3–X6 |

Total: 66. O plano não define uma contagem agregada — cada critério da tabela §3 do plano tem
exatamente um método de teste, nomeado tal como a tabela pede (incluindo a correção de
`oTextoOriginalENuncaPerdidoMesmoQuandoTudoFalha` sem o espaço a mais que a nota do plano já
assinalava).

**Nenhum teste toca a rede nem base de dados.** Confirmado mecanicamente por
`TipsConventionsTest.oPacoteTipsNaoDependeDeBaseDeDadosNemDeRede` (grep a
`jakarta.persistence`/`springframework.data`/`java.net.`/`RestClient`/`WebClient`/
`pt.seerhub.football.repo` em `pt.seerhub.tips`) e
`TipsConventionsTest.nenhumTesteDaGramaticaPrecisaDeContextoSpring` (grep a `@SpringBootTest`/
`AbstractIntegrationTest`/`Testcontainers` nos testes de `pt.seerhub.tips`) — e confirmado à mão
com os comandos do §7 do plano.

## Desvios face ao plano

1. **`TipFormat.EXEMPLO` produz 4 tips / 5 seleções, não "6 tips e 8 seleções" como a linha E5
   da tabela §3.5 do plano dizia.** `TipFormat.EXEMPLO` é, por desenho explícito do próprio
   plano (§4.1: "o bloco literal da spec"), o texto literal da secção "Formato (v1)" do R6 —
   3 tips simples + um `MULT` de 2 seleções (Benfica–Porto, Arsenal–Chelsea, Girona–Real
   Madrid, Bayern–Leipzig, Inter–Napoli). Contado à mão e por teste, esse bloco dá **4 tips, 5
   seleções, zero erros** — nunca 6/8. O "6 tips, 8 seleções" é o número do critério R6-10 (o
   corpus **diferente e maior** de "5 tips simples + 1 acumulador de 3 jogos"), que a linha E5
   da tabela repetiu por lapso de cópia do contexto imediatamente acima (o mesmo género de
   deslize de aritmética já registado nos handoffs de F00/F04/F05 — prosa/tabela a divergir da
   soma real). Preservei a asserção que a spec realmente pede em E5 — "o exemplo mostrado ao
   utilizador é ele próprio um bloco válido, zero erros" — e corrigi só os números para os que
   o texto literal produz. `TipFixedCorpusTest` (10a) é quem prova o "6 tips, 8 seleções" real,
   com um corpus próprio (`TipTextSamples.CINCO_SIMPLES_E_UM_ACUMULADOR`) desenhado para isso.
2. **`MarketDefinition` ganhou dois métodos que o plano não listava**
   (`reconheceTokenDeMercado(String)`, `List<String> selecoesAceites()`), além dos três
   descritos em prosa no §4.1 (`reconhecer`, `nome`, `exemplos`). Necessário para
   `FieldDiagnostics` distinguir `MERCADO_DESCONHECIDO` de `SELECAO_INVALIDA_PARA_O_MERCADO`
   (2g vs. 2h) sem duplicar, fora do pacote `market`, o conhecimento de que tokens cada
   definição aceita. É aditivo — não removeu nem mudou nenhum dos três métodos descritos no
   plano — e fica confinado a `pt.seerhub.tips.parser.market`, um pacote inteiramente interno a
   F06a que nem F06b nem F07 tocam.
3. **`MatchFieldScanner` devolve `LocalDate` (já com o ano inferido) em vez do `MonthDay`
   textual que a prosa do §4.1 do plano mencionava.** A mesma frase do plano diz "Faz a
   inferência do ano" como responsabilidade da própria classe — devolver `MonthDay` obrigaria
   essa inferência a acontecer outra vez, redundantemente, num outro sítio. É um detalhe
   inteiramente interno (`internal.MatchFieldScanner.Resultado` nunca sai de F06a); a forma
   pública e final, `ParsedSelection.data(): LocalDate`, é exatamente a que o plano fixa em
   §4.3 e que os testes verificam.
4. **Coluna exata dos erros "no fim da linha"/"no início do segmento" foi decidida por mim
   onde o plano não a fixava a um carácter preciso** (ex.: `CAMPOS_A_MENOS` genérico aponta
   `colunaFinal()` do último segmento presente; `MULT_SEM_STAKE`/`MULT_VAZIO`/
   `MULT_COM_UMA_SELECAO`/`MULT_COM_SELECAO_INVALIDA`/`ODD_TOTAL_EXCESSIVA` apontam o fim do
   texto do cabeçalho). Todos os testes que dependem de uma coluna exata calculam-na a partir
   do próprio texto de entrada (`indexOf`/`length()`), nunca por um número mágico — para não
   ficarem frágeis a uma escolha de coluna que o plano deixou em aberto.

Nenhum desvio contraria a spec, o R6 ou a linha F06a/F06b do §2.5 do plano.

## Dívidas

**Herdadas, nenhuma agravada por F06a:**
1. `API_FOOTBALL_KEY` no `.env` real do utilizador, `npm audit` do frontend, forma exata do
   JSON da API-Football — todas de F05, todas de rede/ambiente. F06a não toca em nenhum dos
   três caminhos e não precisa de Docker, Postgres nem variáveis de ambiente para correr os
   seus próprios testes.

**Novas, introduzidas por F06a:**
2. **`ParseErrorCode.LINHA_DE_MERCADO_INVALIDA` e `ParseErrorCode.EQUIPA_VAZIA` estão
   implementados e alcançáveis mas não têm um teste dedicado que force cada um pelo nome.**
   Ambos fazem parte da lista fechada de códigos do §4.4 do plano e nenhum critério
   enumerado (2a–2i, 4a–4m, etc.) os nomeia explicitamente — não há linha na tabela §3 que os
   exija. `LINHA_DE_MERCADO_INVALIDA` dispara quando um token de forma fundida (`O`/`U`/`H` +
   número) tem o prefixo certo mas o número é malformado (ex.: `"O2.5.5"`); `EQUIPA_VAZIA`
   dispara quando o separador do par de equipas é encontrado mas um dos lados fica vazio (ex.:
   `"- Porto"`). Comportamento defensivo são sensato, não exercitado por nenhum teste de F06a.
   Se F06b ou F07 encontrarem uma linha real que produza um destes códigos, vale a pena
   acrescentar o teste nessa altura.
3. **`GrammarTipTextParser.parse` com `catalog == null` usa `Clock.systemUTC()` como
   salvaguarda apenas para não lançar `NullPointerException` (13e).** Isto é a única exceção à
   regra "nunca usar o relógio de sistema, só `catalog.hoje()`" (§4.4 do plano) — e só se ativa
   quando quem chama passa `null` por erro, nunca no caminho normal (Spring injeta sempre um
   `TipCatalog` real). Documentado no javadoc de `GrammarTipTextParser.parse`.

## Avisos para quem vier a seguir (F06b)

- **Não editar `GrammarTipTextParser.java`.** A costura é decoração (`ResolvingTipTextParser`
  novo, `@Primary`), não edição — ver "Como F06b entra" acima e
  `TipParserSeamTest.umDecoradorEnriqueceOResultadoSemTocarNaGramatica`.
- **`comResolucao`/`comData` são as duas únicas portas de escrita em `ParsedSelection`.** Se
  precisares de mais um campo que não caiba em `TeamResolution`, é sinal de que a linha
  F06a/F06b foi mal traçada — regista isso no teu handoff em vez de acrescentar componentes a
  `ParsedSelection` (risco 4 do plano de F06a).
- **`FixtureCandidate(fixtureId, semelhanca)` é deliberadamente mínimo** — não duplica emblema/
  liga/hora, que F07 já obtém de `FootballCatalogService.jogo(fixtureId)`. Podes acrescentar
  componentes (aditivo), mas não removas nem renomeies os dois que já existem — F07 vai
  depender deles.
- **Chama sempre `TeamNameNormalizer.normalizar(...)` antes de comparar com
  `teams.normalized_name`/`team_aliases.normalized_alias`** (P3 do handoff de F05) — o termo
  de pesquisa é `TeamRef.texto()`, cru, tal como veio da gramática.
- **`pt.seerhub.football` continua fora dos limites de escrita.** F06a só lê o tipo
  `FixtureView` (via `TipCatalog.jogos()`, que a gramática ignora); é F06b quem chama
  `FootballCatalogService.jogosNaJanela(de, ate)` pela primeira vez neste fio de features.
- **`ParseResult`/`ParsedTip` não têm porta de decoração — só `ParsedSelection` tem.** Se
  precisares de anotar algo ao nível da tip inteira (não da seleção), esse é outro sinal de
  linha mal traçada a registar, não a resolver por conta própria.
- **`docs/features/BACKLOG.md` e `docs/features/CHANGELOG.md` não foram tocados por este
  handoff** — é o orquestrador quem os fecha para "DONE".
