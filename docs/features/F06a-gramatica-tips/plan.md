# F06a — Gramática de tips

**Requisitos:** R6 (metade da gramática — ver §3.6 para a lista explícita do que fica para F06b)
**Depende de:** F00 (esqueleto, convenções, `ApiException`), F05 (`FixtureView` como tipo do parâmetro `catalog`)
**Planeado:** 2026-07-27 · Opus 5

---

## 1. Objetivo

Depois desta feature existe um parser determinístico que converte um bloco de texto colado
pelo tipster numa estrutura de tips — data, par de equipas (texto cru), mercado, seleção,
odd e stake — sem tocar na base de dados, sem contexto Spring, sem rede e sem custo por
operação. O parser vive atrás da interface `TipTextParser(rawText, catalog) → ParseResult`
que o R6 exige, reconhece o catálogo de mercados com as abreviaturas da spec, reconhece
blocos `MULT` com N seleções e odd total calculada como produto, e devolve **por cada linha
inválida** o número da linha, a coluna e o que era esperado naquela posição — sem nunca
invalidar as linhas válidas do mesmo lote. Quando *nenhuma* linha valida, o resultado traz
uma bandeira dedicada (`formatoNaoReconhecido`) e o exemplo canónico do formato, para o
ecrã de revisão mostrar o exemplo lado a lado com o texto colado em vez de uma lista de
erros de sintaxe.

Observável no fim: `./mvnw test` verde, com uma suite nova em `pt.seerhub.tips` que parte do
exemplo literal da spec e produz **6 tips com 8 seleções**, todas com o par de equipas em
texto cru e estado de resolução `POR_RESOLVER`. Nenhuma linha de código de F06a resolve
equipas, liga `fixture_id` ou escreve em Postgres.

---

## 2. Contexto herdado

### 2.1 Handoffs lidos

- `docs/features/F00-fundacoes/handoff.md` — layout de pacotes por feature, `*Test`/`*IT`,
  `ApiException`/`ApiExceptionHandler`, regra dura "nenhum teste toca a rede", nomes de teste
  em português.
- `docs/features/F05-sincronizacao-futebol/handoff.md` — superfície pública
  (`FootballCatalogService`, `FixtureView`, `TeamView`, `TeamNameNormalizer`), pontos P1–P6,
  e o aviso explícito dirigido a **F06 e F06b** sobre normalização.
- `CLAUDE.md` na raiz — convenções de nomes, testes, migrações, erros, idioma.
- `docs/specs/seerhub.md` — R6 completo, §6.1 (fluxo de publicação), §8 (modelo de dados),
  §9 ("Parser de tips" e "Porquê não um modelo de linguagem"), §10 (casos de fronteira),
  §11 (pressuposto mais arriscado do produto).

### 2.2 O que já existe e F06a usa

| Superfície | Caminho | Uso em F06a |
| --- | --- | --- |
| `FixtureView` (record) | `backend/src/main/java/pt/seerhub/football/service/FixtureView.java` | **Apenas como tipo** do método `jogos()` de `TipCatalog`. F06a nunca lê a lista. |
| `FootballCatalogService.jogosNaJanela(de, ate)` | `.../football/service/FootballCatalogService.java` | **Não é chamado por F06a.** É a origem documentada do `catalog` para F06b/F07. |
| `TeamNameNormalizer.normalizar(...)` | `.../football/service/TeamNameNormalizer.java` | **Não é chamado por F06a.** Documentado aqui como obrigação de F06b (P3 do handoff de F05). |
| `ApiException` | `.../common/error/ApiException.java` | **Não é usado.** O parser nunca lança por texto inválido — erros são dados, não exceções. Ver §4.2, decisão D2. |

### 2.3 O que já existe no esquema e F06a **não** toca

`backend/src/main/resources/db/migration/V2__baseline_schema.sql` já traz `tip_imports`,
`tips` e `tip_selections`. F06a **não persiste nada** e **não cria entidade JPA nenhuma**.
Mas as formas que produz têm de caber lá sem conversão — é isso que fixa metade das decisões
de §4.2:

```sql
tips.stake_units  NUMERIC(4,2)  CHECK (stake_units > 0)
tips.total_odds   NUMERIC(6,3)  CHECK (total_odds >= 1)
tip_selections.market    VARCHAR(20) CHECK (market IN
        ('MATCH_RESULT','DOUBLE_CHANCE','OVER_UNDER','BTTS','HANDICAP'))
tip_selections.selection VARCHAR(20) NOT NULL
tip_selections.line      NUMERIC(4,2)          -- nullable
tip_selections.odds      NUMERIC(6,3) CHECK (odds >= 1)
tip_selections.fixture_id BIGINT NULL           -- NULL é legal (§10 da spec)
tip_imports.parser_version VARCHAR(20) NOT NULL
tip_imports.status         VARCHAR(20) CHECK (status IN ('OK','PARTIAL','FAILED'))
tip_imports.raw_text       TEXT NOT NULL
```

Consequências diretas, já decididas:
- `BigDecimal` com escala 3 para odds, escala 2 para stake e linha — nunca `double`.
- `oddTotal` tem de caber em `NUMERIC(6,3)` → teto de `999.999` validado no parser (§4.2, D9).
- `market` é um enum de exatamente cinco valores, espelho do `CHECK` — nem mais, nem menos.
- `selection` tem de ter ≤ 20 caracteres em todas as formas produzidas (§4.3).
- `parserVersion` tem de ter ≤ 20 caracteres → `"grammar-1"`.

### 2.4 Dívidas herdadas que afetam esta feature

Nenhuma. As três dívidas abertas de F05 (`API_FOOTBALL_KEY` no `.env` do utilizador,
`npm audit` do frontend, forma do JSON da API-Football) são todas de rede/ambiente; F06a não
toca em nenhum dos três caminhos. F06a é a primeira feature desta run que **não precisa de
Docker, de Postgres nem de variáveis de ambiente** para correr os seus próprios testes.

### 2.5 A linha entre F06a e F06b, dita uma vez com precisão

Sempre que a gramática encontra um par de equipas, produz **o texto cru de cada equipa e a
sua posição na linha original** — nada mais:

```java
record TeamRef(String texto, int linha, int coluna) {}
```

e anexa a cada seleção um `TeamResolution.porResolver()` — `(fixtureId = null,
estado = POR_RESOLVER, candidatos = [])`. **F06a nunca normaliza um nome de equipa, nunca
consulta o catálogo, nunca calcula semelhança e nunca preenche `fixtureId`.** É isso, e só
isso, que F06b acrescenta, e acrescenta-o **por decoração**, não por edição (§4.2, D1).

---

## 3. Critérios de aceitação → testes

Numeração: `R6-<n>` refere o n-ésimo *bullet* de critérios do R6 na spec, pela ordem em que
lá aparece. Sub-letras são os pontos verificáveis autónomos em que o dividi. `E<n>` são casos
de fronteira da §10 da spec que esta feature possui. `X<n>` são pontos de prosa da spec (§9,
§8) que exigem asserção própria.

Todos os testes são **unitários** (`*Test`, sem contexto Spring, sem Postgres, sem rede).
Não há um único `*IT` nesta feature — é o sinal de que a linha F06a/F06b foi bem traçada.

### 3.1 R6-1 — a gramática aceita data, par de equipas, mercado, seleção, odd e stake

| # | Critério | Ficheiro de teste | Nome do teste | Tipo |
| --- | --- | --- | --- | --- |
| 1a | Linha completa com data `dd/mm`, par, mercado, odd e stake produz uma tip simples com todos os campos | `TipGrammarSimpleLineTest` | `linhaCompletaProduzUmaTipSimplesComTodosOsCampos` | unit |
| 1b | A data é opcional: a linha sem `dd/mm` é válida e devolve `data == null` sem erro | `TipGrammarSimpleLineTest` | `dataAusenteEValidaEDeixaADataPorResolver` | unit |
| 1c | O separador do par pode ser `-`, `–`, `x`, `X`, `vs` ou `v`, sempre rodeado de espaços | `TipGrammarSimpleLineTest` | `aceitaTodosOsSeparadoresDeParDeEquipas` | unit |
| 1d | Um `-` **dentro** de um nome (`Paris Saint-Germain`) não é separador | `TipGrammarSimpleLineTest` | `hifenDentroDoNomeDaEquipaNaoESeparador` | unit |
| 1e | Odd decimal aceita `.` e `,` como separador (`1.85` e `1,85` dão o mesmo `BigDecimal`) | `TipGrammarSimpleLineTest` | `oddAceitaPontoEVirgulaComoSeparadorDecimal` | unit |
| 1f | Stake em unidades: `2u`, `1.5u`, `1,5u`, `2 u`, `2U` | `TipGrammarSimpleLineTest` | `stakeAceitaAsVariantesDeEscritaDeUnidades` | unit |
| 1g | O ano é inferido a partir de `catalog.hoje()` pela regra de distância mínima com desempate no futuro | `TipGrammarSimpleLineTest` | `oAnoEInferidoPelaDataDeReferenciaDoCatalogo` | unit |
| 1h | `29/02` num ano não bissexto salta para o ano bissexto candidato; sem candidato bissexto é `DATA_INVALIDA` | `TipGrammarSimpleLineTest` | `vinteENoveDeFevereiroEscolheOAnoBissextoCandidato` | unit |
| 1i | O texto cru de cada equipa e a sua coluna vêm em `TeamRef`, e a resolução fica `POR_RESOLVER` | `TipGrammarSimpleLineTest` | `oParDeEquipasVemEmTextoCruComColunaEPorResolver` | unit |

### 3.2 R6-2 — catálogo de mercados com abreviaturas

| # | Critério | Ficheiro de teste | Nome do teste | Tipo |
| --- | --- | --- | --- | --- |
| 2a | `1X2` com `1`/`X`/`2`, com e sem o token de mercado, produz `MATCH_RESULT` + `HOME`/`DRAW`/`AWAY` | `TipGrammarMarketCatalogTest` | `resultadoFinalReconheceUmXDoisComESemTokenDeMercado` | unit |
| 2b | Dupla hipótese `1X`/`12`/`X2` (e as escritas invertidas `X1`/`21`/`2X`) → `DOUBLE_CHANCE` | `TipGrammarMarketCatalogTest` | `duplaHipoteseReconheceAsTresCombinacoesEAsEscritasInvertidas` | unit |
| 2c | `O2.5`/`U3.5` (fundido) e `O 2.5`/`UNDER 3.5` (separado) → `OVER_UNDER` com `linha` preenchida | `TipGrammarMarketCatalogTest` | `maisMenosGolosReconheceFormaFundidaESeparadaEExtraiALinha` | unit |
| 2d | `BTTS S`/`BTTS N` (e `AM S`, `GG`, `NG`) → `BTTS` + `YES`/`NO`; `S` isolado é rejeitado | `TipGrammarMarketCatalogTest` | `ambasMarcamExigeTokenDeMercadoOuFormaFundida` | unit |
| 2e | `H-1`/`H+1.5` → `HANDICAP` com `HOME` e linha sinalizada; `H2+1.5` → `AWAY` | `TipGrammarMarketCatalogTest` | `handicapExtraiEquipaELinhaComSinal` | unit |
| 2f | Linha de quarto (`O2.25`, `H-0.75`) é gramática válida (R8 é que a manda para manual) | `TipGrammarMarketCatalogTest` | `linhaDeQuartoEGramaticaValidaMesmoNaoSendoAutoResolvivel` | unit |
| 2g | Token de mercado desconhecido devolve `MERCADO_DESCONHECIDO` com a lista de exemplos aceites | `TipGrammarMarketCatalogTest` | `mercadoDesconhecidoDevolveErroComExemplosAceites` | unit |
| 2h | Seleção inválida para um mercado conhecido (`1X2 3`, `BTTS talvez`) → `SELECAO_INVALIDA_PARA_O_MERCADO` | `TipGrammarMarketCatalogTest` | `selecaoInvalidaParaOMercadoNomeiaAsSelecoesAceites` | unit |
| 2i | Toda a seleção produzida cabe em `VARCHAR(20)` e todo o `Market` existe no `CHECK` do `V2` | `TipsConventionsTest` | `todaSelecaoProduzidaCabeNasColunasDoBaseline` | unit |

### 3.3 R6-3 — o bloco `MULT`

| # | Critério | Ficheiro de teste | Nome do teste | Tipo |
| --- | --- | --- | --- | --- |
| 3a | `MULT | 2u` seguido de N linhas indentadas produz **uma** tip com N seleções | `TipGrammarMultTest` | `blocoMultProduzUmaTipComNSelecoes` | unit |
| 3b | A odd total é o produto das odds das seleções, com escala 3 e `HALF_UP` | `TipGrammarMultTest` | `aOddTotalEOProdutoDasOddsDasSelecoes` | unit |
| 3c | O stake é declarado uma única vez na linha `MULT` e é o stake da tip | `TipGrammarMultTest` | `oStakeEDeclaradoUmaSoVezNaLinhaMult` | unit |
| 3d | O bloco fecha na primeira linha não indentada; a linha seguinte volta a ser tip simples | `TipGrammarMultTest` | `oBlocoFechaNaPrimeiraLinhaNaoIndentada` | unit |
| 3e | Linha em branco **dentro** do bloco não o fecha | `TipGrammarMultTest` | `linhaEmBrancoDentroDoBlocoNaoOFecha` | unit |
| 3f | Dois blocos `MULT` no mesmo texto produzem duas tips múltiplas independentes | `TipGrammarMultTest` | `doisBlocosMultProduzemDuasTipsIndependentes` | unit |
| 3g | `MULT` com uma só seleção → `MULT_COM_UMA_SELECAO` na linha do cabeçalho, tip descartada | `TipGrammarMultTest` | `multComUmaSoSelecaoEErroNoCabecalho` | unit |
| 3h | `MULT` sem seleções indentadas → `MULT_VAZIO`, com mensagem que exige indentação | `TipGrammarMultTest` | `multSemSelecoesIndentadasExplicaQueTemDeSerIndentado` | unit |
| 3i | `MULT` sem stake → `MULT_SEM_STAKE` na linha do cabeçalho, com coluna no fim da linha | `TipGrammarMultTest` | `multSemStakeEErroNoCabecalhoComColunaNoFim` | unit |
| 3j | Linha filha com um 4.º campo (stake dentro do bloco) → `STAKE_DENTRO_DE_MULT` nessa linha | `TipGrammarMultTest` | `stakeNumaLinhaFilhaEErroNessaLinha` | unit |
| 3k | Filha inválida descarta o acumulador **e** produz um segundo erro no cabeçalho a dizer porquê | `TipGrammarMultTest` | `filhaInvalidaDescartaOAcumuladorEExplicaNoCabecalho` | unit |
| 3l | Produto acima de `999.999` → `ODD_TOTAL_EXCESSIVA` no cabeçalho (não cabe em `NUMERIC(6,3)`) | `TipGrammarMultTest` | `oddTotalAcimaDoLimiteDaColunaERejeitadaNoCabecalho` | unit |

### 3.4 R6-4 — uma linha inválida nunca invalida o lote

| # | Critério | Ficheiro de teste | Nome do teste | Tipo |
| --- | --- | --- | --- | --- |
| 4a | Bloco com 3 linhas boas e 2 más devolve 3 tips **e** 2 erros; nenhuma exceção é lançada | `TipGrammarErrorTest` | `linhasValidasSeguemEAsInvalidasVoltamComErro` | unit |
| 4b | Cada erro traz linha (1-based, sobre o texto original), coluna (1-based) e `esperado` em português | `TipGrammarErrorTest` | `cadaErroTrazLinhaColunaEOQueEraEsperado` | unit |
| 4c | Linha sem odd → `CAMPOS_A_MENOS` com mensagem que nomeia o campo em falta, não "não corresponde" | `TipGrammarErrorTest` | `linhaSemOddNomeiaOCampoEmFaltaEmVezDeDizerQueNaoCorresponde` | unit |
| 4d | Data malformada (`32/01`, `28/13`, `2807`, `28-07`) → `DATA_INVALIDA` com coluna sobre o token da data | `TipGrammarErrorTest` | `dataMalformadaApontaAColunaDoTokenDaData` | unit |
| 4e | Stake sem `u` (`| 2`) → `STAKE_SEM_UNIDADE`, coluna no fim do número, exemplo `2u` na mensagem | `TipGrammarErrorTest` | `stakeSemUnidadeApontaOFimDoNumeroESugere2u` | unit |
| 4f | Stake fora de `[0.25, 10]` → `STAKE_FORA_DO_INTERVALO` (pressuposto de §11 da spec) | `TipGrammarErrorTest` | `stakeForaDoIntervaloDeUnidadesERejeitada` | unit |
| 4g | Odd `1.00`, `0.85` ou `abc` → `ODD_INVALIDA` com coluna sobre o token da odd | `TipGrammarErrorTest` | `oddNaoSuperiorAUmOuNaoNumericaEErroComColunaCerta` | unit |
| 4h | Campos a mais (5 segmentos) → `CAMPOS_A_MAIS` com coluna no início do segmento excedente | `TipGrammarErrorTest` | `camposAMaisApontamOInicioDoSegmentoExcedente` | unit |
| 4i | Par de equipas sem separador, ou com dois separadores → `PAR_DE_EQUIPAS_INVALIDO` / `_AMBIGUO` | `TipGrammarErrorTest` | `parDeEquipasSemSeparadorOuComDoisEErroDistinto` | unit |
| 4j | Mistura de tabulações e espaços parseia na mesma, e as colunas continuam a apontar o carácter certo da linha original | `TipGrammarErrorTest` | `misturaDeTabulacoesEEspacosNaoDeslocaAsColunas` | unit |
| 4k | Linhas vazias e só-espaços são ignoradas, não geram erro e não deslocam a numeração das linhas seguintes | `TipGrammarErrorTest` | `linhasVaziasSaoIgnoradasENaoDeslocamANumeracao` | unit |
| 4l | Terminações `\r\n`, `\n` e um BOM inicial não afetam a numeração nem as colunas | `TipGrammarErrorTest` | `terminacoesDeLinhaEBomNaoAfetamNumeracaoNemColunas` | unit |
| 4m | Um erro nunca é uma exceção: nenhuma entrada, por má que seja, faz `parse` lançar | `TipGrammarErrorTest` | `nenhumaEntradaFazOParserLancarExcecao` | unit |

### 3.5 Corpus fixo, desempenho, interface e casos de fronteira

| # | Critério | Ficheiro de teste | Nome do teste | Tipo |
| --- | --- | --- | --- | --- |
| 10a | **R6-10 (metade da gramática):** o corpus fixo de 5 tips simples + 1 acumulador de 3 jogos produz **6 tips com 8 seleções**, sem qualquer chamada de rede | `TipFixedCorpusTest` | `cincoSimplesMaisUmAcumuladorDeTresProduzemSeisTipsComOitoSelecoes` | unit |
| 10b | Nesse mesmo corpus, as 8 seleções vêm todas com `POR_RESOLVER` e `fixtureId == null` (a ligação é de F06b) | `TipFixedCorpusTest` | `noCorpusFixoTodasAsSelecoesFicamPorResolver` | unit |
| 10c | Os textos crus das 16 equipas do corpus são exatamente os do exemplo da spec, na ordem certa | `TipFixedCorpusTest` | `osTextosCrusDasEquipasDoCorpusSaoOsDoExemploDaSpec` | unit |
| 12a | **R6-12 (orçamento da gramática):** p95 do parse de um bloco de 2000 caracteres fica abaixo de 20 ms, com aquecimento e verificação de que o resultado está certo | `TipParserPerformanceTest` | `p95DoParseDeDoisMilCaracteresFicaMuitoAbaixoDoOrcamento` | unit |
| 12b | Entradas adversariais (2000 `x`, 2000 `|`, 2000 `-`, 2000 espaços, 2000 `/`) completam dentro de 2 s — guarda contra retrocesso catastrófico | `TipParserPerformanceTest` | `entradasAdversariaisNaoProvocamRetrocessoCatastrofico` | unit |
| 12c | Um bloco **acima** de 2000 caracteres continua a parsear normalmente (2000 é critério de desempenho, não limite) | `TipParserPerformanceTest` | `blocoAcimaDeDoisMilCaracteresContinuaAParsearNormalmente` | unit |
| 12d | Bloco acima de 20 000 caracteres devolve um único `BLOCO_DEMASIADO_GRANDE`, sem varrer e sem lançar | `TipParserPerformanceTest` | `blocoAcimaDoLimiteDuroDevolveUmSoErroSemVarrer` | unit |
| 13a | **R6-13:** existe `TipTextParser` com exatamente `ParseResult parse(String rawText, TipCatalog catalog)` | `TipParserSeamTest` | `aInterfaceTipTextParserTemAAssinaturaExigidaPelaSpec` | unit |
| 13b | Um decorador de teste que enriquece o `ParseResult` (a simular F06b) não precisa de tocar em `GrammarTipTextParser` | `TipParserSeamTest` | `umDecoradorEnriqueceOResultadoSemTocarNaGramatica` | unit |
| 13c | `ParsedSelection.comResolucao(...)` e `.comData(...)` devolvem cópias e preservam todos os outros campos | `TipParserSeamTest` | `osMetodosDeCopiaPreservamTodosOsOutrosCampos` | unit |
| 13d | A gramática ignora por completo o conteúdo de `catalog.jogos()`: catálogo vazio e catálogo cheio dão o mesmo `ParseResult` | `TipParserSeamTest` | `oResultadoDaGramaticaNaoDependeDosJogosDoCatalogo` | unit |
| 13e | `catalog == null` ou `rawText == null` não lançam: devolvem `ParseResult` vazio/erro, nunca `NullPointerException` | `TipParserSeamTest` | `entradasNulasDevolvemResultadoVazioEmVezDeLancar` | unit |
| E1 | **§10:** quando *nenhuma* linha valida, `formatoNaoReconhecido()` é `true` e `TipFormat.EXEMPLO` acompanha o resultado | `TipGrammarUnknownFormatTest` | `quandoNenhumaLinhaValidaOResultadoPedeOExemploDoFormato` | unit |
| E2 | Um bloco no formato de Telegram (`🔥 Benfica vence @1.85 ✅`) dispara essa bandeira e continua a listar os erros linha a linha, sem perder nada | `TipGrammarUnknownFormatTest` | `formatoDeTelegramDisparaABandeiraEMantemOsErrosLinhaALinha` | unit |
| E3 | Um lote **parcialmente** válido (≥ 1 tip) nunca dispara a bandeira, mesmo com muitos erros | `TipGrammarUnknownFormatTest` | `loteParcialmenteValidoNuncaDisparaABandeira` | unit |
| E4 | Texto vazio ou só com linhas em branco: zero tips, zero erros, bandeira **falsa** (não há formato estrangeiro, há texto vazio) | `TipGrammarUnknownFormatTest` | `textoVazioNaoEFormatoDesconhecidoEENulo` | unit |
| E5 | `TipFormat.EXEMPLO` é ele próprio um bloco válido: parseado, dá 6 tips e 8 seleções e zero erros | `TipFormatExampleTest` | `oExemploMostradoAoUtilizadorEEleProprioUmBlocoValido` | unit |
| X1 | **§8/§9:** `ParseResult.rawText()` devolve o texto original byte a byte, sempre, incluindo quando tudo falha (o `TipImport` de F07 depende disto) | `TipGrammarErrorTest` | `oTextoOriginalENunca PerdidoMesmoQuandoTudoFalha` | unit |
| X2 | `ParseResult.estado()` devolve `OK`/`PARTIAL`/`FAILED` conforme o `CHECK` de `tip_imports` | `TipGrammarErrorTest` | `oEstadoDoResultadoSegueOsTresValoresDeTipImports` | unit |
| X3 | `parserVersion()` é `"grammar-1"` e cabe em `VARCHAR(20)` | `TipsConventionsTest` | `aVersaoDoParserCabeNaColunaDoBaseline` | unit |
| X4 | **§9 ("sem dependência externa em runtime"):** nenhuma classe de `pt.seerhub.tips` importa JPA, Spring Data, `java.net`, `RestClient` ou `pt.seerhub.football.repo` | `TipsConventionsTest` | `oPacoteTipsNaoDependeDeBaseDeDadosNemDeRede` | unit |
| X5 | Nenhum teste de `pt.seerhub.tips` usa `@SpringBootTest` nem estende `AbstractIntegrationTest` — a gramática é lógica pura | `TipsConventionsTest` | `nenhumTesteDaGramaticaPrecisaDeContextoSpring` | unit |
| X6 | Todos os `Pattern` do parser são constantes `static final` compiladas uma vez (custo de compilação fora do caminho quente) | `TipsConventionsTest` | `todosOsPadroesDoParserSaoConstantesCompiladasUmaSoVez` | unit |

> **Nota sobre X1:** o nome do teste acima leva um espaço por lapso de escrita nesta tabela;
> o nome real a usar é `oTextoOriginalENuncaPerdidoMesmoQuandoTudoFalha`.

### 3.6 Critérios do R6 **adiados para F06b** — lista explícita e fechada

O planeador de F06b herda esta lista tal como está e não precisa de fazer diff ao requisito.

| Critério do R6 (texto da spec) | Dono | Porquê não é de F06a |
| --- | --- | --- |
| "A correspondência de equipa é feita por três vias… alias exato → nome normalizado exato → semelhança por trigramas" | **F06b** | Precisa de Postgres com `pg_trgm` e do catálogo sincronizado. |
| "Uma linha cujo par de equipas corresponda a exatamente um jogo na janela sincronizada é ligada automaticamente a esse `fixture_id`" | **F06b** | F06a deixa `fixtureId == null` e `POR_RESOLVER` em todas as seleções. |
| "Zero candidatos acima do limiar, ou mais do que um, marca a linha como ambígua e apresenta os candidatos ordenados" | **F06b** | F06a define o *contentor* (`TeamResolution`, `FixtureCandidate`) e nunca o preenche. |
| "Escolher um candidato no ecrã de revisão guarda um `TeamAlias`" | **F06b** | Escrita em base de dados e ecrã de revisão. |
| "O conjunto de teste inclui variantes de escrita reais — `Sporting`, `Sporting CP`, `Sporting Lisbon`, `Man Utd`, `Manchester United`, `Inter`, `Internazionale`" | **F06b** | **Corpus de F06b, não de F06a.** Para a gramática, um nome de equipa é uma cadeia opaca entre a data e o separador — `Sporting` e `Sporting Lisbon` são exatamente igualmente válidos e nenhum teste de F06a distingue os dois. |
| "…todas ligadas a jogos reais" (segunda metade de R6-10) | **F06b** | F06a prova 6 tips / 8 seleções; F06b prova as 8 ligações. |
| "…**incluindo a correspondência de equipas**, responde abaixo de 200 ms no percentil 95" (segunda metade de R6-12) | **F06b** | F06a fixa o orçamento da gramática em 20 ms dos 200 ms e mede-o; a medição ponta-a-ponta é de F06b. Ver §4.2, D11. |
| "O texto original é sempre guardado num registo `TipImport`" | **F07** | Persistência. F06a garante o *dado* (`rawText` verbatim + `estado()`), não a escrita. |

Também **desambiguação por data quando há dois candidatos igualmente prováveis** (§10 da
spec) é de F06b: F06a entrega a `LocalDate` já resolvida (ou `null`), que é o insumo dessa
desambiguação.

---

## 4. Alterações

### 4.1 Ficheiros a criar

Todos sob `backend/src/main/java/pt/seerhub/tips/` (produção) e
`backend/src/test/java/pt/seerhub/` (testes). **Zero ficheiros de frontend.**

#### Produção — `pt.seerhub.tips.domain` (2)

| Caminho | Propósito |
| --- | --- |
| `backend/src/main/java/pt/seerhub/tips/domain/Market.java` | `enum` de 5 valores, espelho exato do `CHECK ck_selection_market` do `V2`: `MATCH_RESULT`, `DOUBLE_CHANCE`, `OVER_UNDER`, `BTTS`, `HANDICAP`. É a taxonomia persistida — sem comportamento de parsing. R8 despacha sobre este enum. |
| `backend/src/main/java/pt/seerhub/tips/domain/ImportStatus.java` | `enum` `OK`, `PARTIAL`, `FAILED`, espelho de `ck_tip_imports_status`. F07 persiste-o; F06a deriva-o do `ParseResult`. |

#### Produção — `pt.seerhub.tips.parser` (15)

| Caminho | Propósito |
| --- | --- |
| `.../tips/parser/TipTextParser.java` | **A interface que o R6 exige.** Um único método: `ParseResult parse(String rawText, TipCatalog catalog)`. Javadoc que diz que qualquer implementação futura (incluindo um passo de LLM sobre as linhas rejeitadas, §9 da spec) entra por aqui. |
| `.../tips/parser/GrammarTipTextParser.java` | `@Service`. A implementação de F06a. Orquestra: pré-condições → `LineSplitter` → classificação de linha → scanners → `MarketCatalog` → montagem de `ParsedTip`. Ignora `catalog.jogos()`. **F06b não edita este ficheiro.** |
| `.../tips/parser/TipCatalog.java` | O parâmetro `catalog` do R6: `ZoneId fuso()`, `LocalDate hoje()`, `List<FixtureView> jogos()`. F06a lê os dois primeiros (inferência do ano); F06b lê os três. |
| `.../tips/parser/SimpleTipCatalog.java` | `record SimpleTipCatalog(ZoneId fuso, LocalDate hoje, List<FixtureView> jogos) implements TipCatalog`, com `static vazio(Clock)` e `static de(Clock, List<FixtureView>)`. Lista copiada defensivamente com `List.copyOf`. |
| `.../tips/parser/ParseResult.java` | Ver §4.3. Métodos derivados: `estado()`, `formatoNaoReconhecido()`, `totalSelecoes()`, `temErros()`. |
| `.../tips/parser/ParsedTip.java` | Uma tip pronta para revisão, ainda sem `fixture_id`. |
| `.../tips/parser/TipKind.java` | `enum` `SIMPLES`, `MULTIPLA`. |
| `.../tips/parser/ParsedSelection.java` | Uma seleção. Inclui `comResolucao(TeamResolution)` e `comData(LocalDate)` — as duas únicas portas por onde F06b altera um resultado. |
| `.../tips/parser/TeamRef.java` | `record TeamRef(String texto, int linha, int coluna)`. O texto é **exatamente** o do bloco colado, sem trim interno, sem normalização. |
| `.../tips/parser/TeamResolution.java` | `record TeamResolution(Long fixtureId, ResolutionStatus estado, List<FixtureCandidate> candidatos)` + `static porResolver()`. |
| `.../tips/parser/ResolutionStatus.java` | `enum` `POR_RESOLVER`, `RESOLVIDA`, `AMBIGUA`, `SEM_CANDIDATOS`. F06a só emite o primeiro. |
| `.../tips/parser/FixtureCandidate.java` | `record FixtureCandidate(long fixtureId, double semelhanca)`. Deliberadamente mínimo — F07 obtém emblemas/liga/hora por `FootballCatalogService.jogo(fixtureId)`. |
| `.../tips/parser/ParseError.java` | `record ParseError(int linha, int coluna, String trecho, ParseErrorCode codigo, String esperado)`. |
| `.../tips/parser/ParseErrorCode.java` | O `enum` de códigos (§4.4). O frontend e os testes casam sobre o código, nunca sobre a prosa. |
| `.../tips/parser/TipFormat.java` | Constantes públicas: `EXEMPLO` (o bloco literal da spec), `VERSAO = "grammar-1"`, `LIMITE_DE_CARACTERES = 20_000`, `STAKE_MINIMA`/`STAKE_MAXIMA`, `ODD_MINIMA`/`ODD_MAXIMA`, `ODD_TOTAL_MAXIMA`. É daqui que F07 tira o exemplo a mostrar ao lado do texto colado. |

#### Produção — `pt.seerhub.tips.parser.internal` (6)

| Caminho | Propósito |
| --- | --- |
| `.../parser/internal/LineSplitter.java` | Parte o texto em linhas (1-based, `\r\n`/`\n`, BOM removido) e cada linha em segmentos por `|`, **guardando o deslocamento absoluto de cada segmento na linha original**. É esta classe que faz as colunas serem verdadeiras com tabulações à mistura. |
| `.../parser/internal/Segment.java` | `record Segment(String texto, int colunaInicial, int colunaFinal)` — texto já sem espaços das pontas, colunas sempre relativas à linha original, 1-based. |
| `.../parser/internal/MatchFieldScanner.java` | Lê o primeiro segmento: data opcional + par de equipas. Devolve `MonthDay` (ou `null`), dois `TeamRef` com colunas, ou um `ParseError`. Faz a inferência do ano. |
| `.../parser/internal/OddsScanner.java` | Lê uma odd decimal (`.` ou `,`), valida intervalo e escala, devolve `BigDecimal` escala 3. |
| `.../parser/internal/StakeScanner.java` | Lê uma stake em unidades, valida o sufixo `u`, o intervalo e a escala, devolve `BigDecimal` escala 2. |
| `.../parser/internal/FieldDiagnostics.java` | **A peça que produz erros úteis.** Quando a contagem de segmentos ou um scanner falha, olha para o que os tokens *parecem* e escolhe a mensagem mais específica (§4.5). |

#### Produção — `pt.seerhub.tips.parser.market` (8)

| Caminho | Propósito |
| --- | --- |
| `.../parser/market/MarketDefinition.java` | Interface: `Optional<MarketMatch> reconhecer(String tokenDeMercado, String tokenDeSelecao)`, `String nome()`, `List<String> exemplos()`. `tokenDeMercado` pode ser `null` (forma de um só token). |
| `.../parser/market/MarketMatch.java` | `record MarketMatch(Market mercado, String selecao, BigDecimal linha)` — `linha` nula fora de O/U e handicap. |
| `.../parser/market/MarketCatalog.java` | Lista ordenada e imutável das definições (`PADRAO`), com `reconhecer(...)` que percorre e devolve o primeiro *match*, e `exemplosDeTodosOsMercados()` para as mensagens de erro. |
| `.../parser/market/MatchResultMarket.java` | `1`/`X`/`2`; token de mercado opcional `1X2`, `MR`, `FT`, `RESULTADO`. |
| `.../parser/market/DoubleChanceMarket.java` | `1X`/`12`/`X2` e invertidos `X1`/`21`/`2X`; token opcional `DC`. |
| `.../parser/market/OverUnderMarket.java` | `O2.5`/`U3.5`/`OVER2.5`/`UNDER3.5` fundidos e as mesmas separadas em dois tokens. |
| `.../parser/market/BttsMarket.java` | `BTTS S`/`BTTS N`/`AM S`/`AM N` e os fundidos `GG`/`NG`. |
| `.../parser/market/HandicapMarket.java` | `H-1`/`H+1.5`/`H1-1`/`H2+1.5` e a forma separada (`H`\|`HA`\|`AH`) + linha. |

#### Testes (11)

| Caminho | Propósito |
| --- | --- |
| `backend/src/test/java/pt/seerhub/support/TipTextSamples.java` | Corpus partilhado: `EXEMPLO_DA_SPEC`, `CINCO_SIMPLES_E_UM_ACUMULADOR`, `FORMATO_DE_TELEGRAM`, `TABS_E_ESPACOS`, `BLOCO_DE_2000_CARACTERES`, `BLOCO_ADVERSARIAL_*` e `catalogoDeTeste(LocalDate)`. Segue o padrão de `FootballTestSupport`/`AuthTestSupport`. |
| `backend/src/test/java/pt/seerhub/tips/TipGrammarSimpleLineTest.java` | R6-1 (1a–1i). |
| `backend/src/test/java/pt/seerhub/tips/TipGrammarMarketCatalogTest.java` | R6-2 (2a–2h). |
| `backend/src/test/java/pt/seerhub/tips/TipGrammarMultTest.java` | R6-3 (3a–3l). |
| `backend/src/test/java/pt/seerhub/tips/TipGrammarErrorTest.java` | R6-4 (4a–4m), X1, X2. |
| `backend/src/test/java/pt/seerhub/tips/TipGrammarUnknownFormatTest.java` | E1–E4 (o caso de fronteira do Telegram). |
| `backend/src/test/java/pt/seerhub/tips/TipFixedCorpusTest.java` | 10a–10c. |
| `backend/src/test/java/pt/seerhub/tips/TipFormatExampleTest.java` | E5. |
| `backend/src/test/java/pt/seerhub/tips/TipParserSeamTest.java` | 13a–13e. |
| `backend/src/test/java/pt/seerhub/tips/TipParserPerformanceTest.java` | 12a–12d. |
| `backend/src/test/java/pt/seerhub/tips/TipsConventionsTest.java` | 2i, X3–X6. |

**Total: 31 ficheiros de produção + 11 de teste = 42 ficheiros a criar.**

### 4.2 Ficheiros a editar

**Nenhum.** Zero.

Isto é deliberado e é um critério de verificação (§7, passo 5): F06a não acrescenta
propriedades de configuração (não há limiar, não há cron, não há chave), não acrescenta
migração, não acrescenta variável de ambiente, não toca em `SecurityConfig` (não há endpoint)
e não toca no frontend. Se o implementador se vir a editar `application.yml`, `.env.example`,
`docker-compose.yml`, `SeerHubProperties` ou `SecurityConfig`, tomou um desvio que tem de
justificar no handoff — quase de certeza puxou para F06a algo que é de F06b ou de F07.

### 4.3 Modelo de dados / migrações

**Nenhuma migração.** F06a não persiste nada, não cria entidade JPA nenhuma e não regista
nenhum repositório. `MigrationNamingTest` e `FlywayBaselineIT` ficam intocados e verdes.

As formas produzidas, fixadas aqui para não haver interpretação no código:

```java
public record ParseResult(
        String rawText,                 // verbatim, sempre, mesmo quando tudo falha
        String parserVersion,           // "grammar-1"
        List<ParsedTip> tips,           // por ordem de aparecimento no texto
        List<ParseError> erros,         // por ordem de linha, depois de coluna
        boolean formatoNaoReconhecido   // true sse houve ≥1 linha não vazia e 0 tips
) {
    public ImportStatus estado();       // OK | PARTIAL | FAILED
    public int totalSelecoes();
    public boolean temErros();
    public static ParseResult vazio(String rawText);
}

public record ParsedTip(
        int linha,                      // 1-based: a linha da tip simples, ou a do cabeçalho MULT
        TipKind tipo,                   // SIMPLES | MULTIPLA
        BigDecimal stakeUnidades,       // escala 2, ∈ [0.25, 10]
        BigDecimal oddTotal,            // escala 3, ∈ [1.01, 999.999]
        List<ParsedSelection> selecoes, // 1 para SIMPLES, ≥2 para MULTIPLA
        String textoOriginal            // as linhas de origem, verbatim, juntas por "\n"
) {}

public record ParsedSelection(
        int linha,                      // 1-based
        LocalDate data,                 // null quando o tipster a omitiu; ano já inferido
        String textoDaData,             // "28/07" ou null — para o ecrã de revisão mostrar o que foi escrito
        TeamRef casa,
        TeamRef fora,
        Market mercado,
        String selecao,                 // HOME | DRAW | AWAY | HOME_DRAW | ... | OVER | UNDER | YES | NO
        BigDecimal linhaMercado,        // escala 2, null fora de OVER_UNDER e HANDICAP
        BigDecimal odd,                 // escala 3
        TeamResolution resolucao        // SEMPRE TeamResolution.porResolver() em F06a
) {
    public ParsedSelection comResolucao(TeamResolution nova);  // porta de F06b
    public ParsedSelection comData(LocalDate nova);            // porta de F06b (data inferida do jogo)
}
```

Valores de `selecao` por mercado — todos ≤ 20 caracteres, conforme `tip_selections.selection`:

| `Market` | `selecao` possíveis | `linhaMercado` |
| --- | --- | --- |
| `MATCH_RESULT` | `HOME`, `DRAW`, `AWAY` | `null` |
| `DOUBLE_CHANCE` | `HOME_DRAW`, `HOME_AWAY`, `DRAW_AWAY` | `null` |
| `OVER_UNDER` | `OVER`, `UNDER` | ex.: `2.50` |
| `BTTS` | `YES`, `NO` | `null` |
| `HANDICAP` | `HOME`, `AWAY` | com sinal, ex.: `-1.00`, `1.50` |

### 4.4 A gramática, fixada sem ambiguidade

**Linha lógica.** O texto é partido por `\r?\n`. Um BOM inicial (`\uFEFF`) é descartado.
Linhas em branco ou só com espaços/tabulações são ignoradas em todo o lado — **exceto** que
não fecham um bloco `MULT` aberto. A numeração de linhas é sempre 1-based sobre o texto
original, contando as linhas em branco.

**Classificação de linha**, por esta ordem:

1. O segmento antes do primeiro `|`, sem espaços das pontas e em maiúsculas, é `MULT` →
   **cabeçalho de acumulador**.
2. Existe um bloco `MULT` aberto **e** a linha começa por espaço ou tabulação →
   **seleção de acumulador**.
3. Caso contrário → **tip simples**. (A indentação fora de um bloco aberto não tem
   significado: é tolerada e descartada.)

**Formas:**

```
tip simples   :  [dd/mm] <equipa A> <sep> <equipa B> | <mercado+seleção> | <odd> | <stake>u
cabeçalho MULT:  MULT | <stake>u
seleção MULT  :  ␣[dd/mm] <equipa A> <sep> <equipa B> | <mercado+seleção> | <odd>
```

**Separador do par de equipas:** uma sequência de espaços/tabulações, seguida de um de
`-`, `–`, `—`, `x`, `X`, `vs`, `VS`, `v`, `V`, seguida de espaços/tabulações. A exigência de
espaço dos dois lados é o que faz `Paris Saint-Germain` funcionar. Zero ocorrências →
`PAR_DE_EQUIPAS_INVALIDO`; duas ou mais → `PAR_DE_EQUIPAS_AMBIGUO` com a coluna da segunda.

**Data:** `d{1,2}/d{1,2}` no início do primeiro segmento, seguida de pelo menos um espaço.
Validada como dia real do mês. O ano é inferido a partir de `catalog.hoje()` assim:
candidatos `hoje.ano-1`, `hoje.ano`, `hoje.ano+1`; descartam-se os que não formem uma data
válida (`29/02`); dos restantes escolhe-se **o de menor distância absoluta em dias a
`hoje`, com desempate para o futuro**. Se nenhum candidato for válido →
`DATA_INVALIDA`. Regra determinística, sem relógio de sistema: só o `hoje()` do catálogo.

**Odd:** `\d{1,3}([.,]\d{1,3})?`, convertida com `.` , escala 3, e tem de estar em
`[1.01, 999.999]`. `1.00` é rejeitada (uma odd de 1.00 é um erro de escrita, não uma aposta).

**Stake:** `\d{1,2}([.,]\d{1,2})?` + espaços opcionais + `u` ou `U`. Escala 2. Intervalo
`[0.25, 10]`, conforme o pressuposto de §11 da spec. Falta o `u` → `STAKE_SEM_UNIDADE`
(nunca `STAKE_INVALIDA` — a distinção é o que torna o erro útil).

**Segmento de mercado:** um ou dois tokens separados por espaços. Com dois, o primeiro é o
token de mercado e o segundo a seleção; com um, o token é entregue a `MarketCatalog` com
`tokenDeMercado == null`. Três ou mais tokens → `MERCADO_DESCONHECIDO` com a coluna do
terceiro.

**Bloco `MULT`:** abre no cabeçalho, absorve todas as linhas indentadas seguintes (as em
branco não o fecham), fecha na primeira linha não indentada não vazia ou no fim do texto.
Exige ≥ 2 seleções. A odd total é o produto das odds das seleções em `BigDecimal`, com
`setScale(3, RoundingMode.HALF_UP)` **só no fim**, nunca a cada multiplicação. Se o produto
exceder `999.999` → `ODD_TOTAL_EXCESSIVA`.

**Códigos de erro (`ParseErrorCode`), fechados:**

`BLOCO_DEMASIADO_GRANDE`, `CAMPOS_A_MENOS`, `CAMPOS_A_MAIS`, `DATA_INVALIDA`,
`PAR_DE_EQUIPAS_INVALIDO`, `PAR_DE_EQUIPAS_AMBIGUO`, `EQUIPA_VAZIA`,
`MERCADO_DESCONHECIDO`, `SELECAO_INVALIDA_PARA_O_MERCADO`, `LINHA_DE_MERCADO_INVALIDA`,
`ODD_INVALIDA`, `STAKE_INVALIDA`, `STAKE_SEM_UNIDADE`, `STAKE_FORA_DO_INTERVALO`,
`MULT_SEM_STAKE`, `MULT_VAZIO`, `MULT_COM_UMA_SELECAO`, `MULT_COM_SELECAO_INVALIDA`,
`STAKE_DENTRO_DE_MULT`, `ODD_TOTAL_EXCESSIVA`.

### 4.5 O diagnóstico — onde nasce a diferença entre um erro útil e um inútil

Quando a contagem de segmentos não bate ou um scanner falha, `FieldDiagnostics` **não**
devolve "a linha não corresponde à gramática". Olha para os tokens e decide:

| Situação observada | Erro emitido |
| --- | --- |
| 3 segmentos numa linha não indentada, e o 3.º parece uma odd | `CAMPOS_A_MENOS` — *"Falta a stake no fim da linha. Exemplo: `\| 2u`."*, coluna no fim da linha |
| 3 segmentos numa linha não indentada, e o 3.º parece uma stake (`2u`) | `CAMPOS_A_MENOS` — *"Falta a odd antes da stake. Exemplo: `\| 1.85 \| 2u`."*, coluna no início do 3.º segmento |
| 4.º segmento é numérico sem `u` | `STAKE_SEM_UNIDADE`, coluna no fim do número |
| 4.º segmento numérico com `u` mas fora de `[0.25, 10]` | `STAKE_FORA_DO_INTERVALO`, com o intervalo na mensagem |
| 2 segmentos e o 1.º é `MULT` sem stake no 2.º | `MULT_SEM_STAKE` |
| 5+ segmentos | `CAMPOS_A_MAIS`, coluna no início do 5.º |
| 1 segmento e a linha contém `@`, emoji, ou `✅`/`🔥` | `CAMPOS_A_MENOS` com a mensagem que remete para o exemplo do formato |
| Token de mercado desconhecido | `MERCADO_DESCONHECIDO` com `MarketCatalog.exemplosDeTodosOsMercados()` na mensagem |
| Mercado conhecido, seleção não | `SELECAO_INVALIDA_PARA_O_MERCADO`, listando só as seleções desse mercado |

Todas as mensagens em português de Portugal, seguras para mostrar ao utilizador
(`CLAUDE.md`, secção "Erros e logs"), e **sempre com um exemplo concreto** — é o exemplo,
não a descrição do erro, que ensina o formato ao tipster (§11 da spec: o pressuposto de
produto é que ele aprende o formato; a mensagem de erro é o material didático).

---

## 5. Ordem de implementação

Cada passo é independentemente executável (`./mvnw test -Dtest='Tip*Test'`) e traz os seus
testes consigo — nunca testes no fim.

1. **Esqueleto de formas.** `Market`, `ImportStatus`, `TipKind`, `ResolutionStatus`,
   `FixtureCandidate`, `TeamResolution`, `TeamRef`, `ParseError`, `ParseErrorCode`,
   `ParsedSelection`, `ParsedTip`, `ParseResult`, `TipFormat`, `TipCatalog`,
   `SimpleTipCatalog`, `TipTextParser`. Compila sem implementação.
   Testes: `TipsConventionsTest` (2i, X3), `TipParserSeamTest` (13a, 13c).
2. **`LineSplitter` + `Segment`.** Corte por linhas e por `|` com deslocamentos absolutos.
   Testes: 4j, 4k, 4l dentro de `TipGrammarErrorTest`.
3. **`MarketCatalog` e as 5 `MarketDefinition`.** Só reconhecimento de tokens, sem contexto
   de linha. Testes: `TipGrammarMarketCatalogTest` inteiro (2a–2h).
4. **`OddsScanner` e `StakeScanner`.** Testes: 1e, 1f, 4e, 4f, 4g.
5. **`MatchFieldScanner`.** Data (com inferência do ano), par de equipas, colunas.
   Testes: 1b, 1c, 1d, 1g, 1h, 1i, 4d, 4i.
6. **`GrammarTipTextParser` — caminho da tip simples.** Junta 2–5.
   Testes: 1a, 4a, 4c, 4h, 4m, X1, X2.
7. **`FieldDiagnostics`.** Substitui as mensagens genéricas do passo 6 pelas específicas da
   tabela de §4.5. Os testes de 4c/4e/4h passam a asserir a mensagem específica.
8. **Bloco `MULT`.** Abertura, absorção, fecho, produto de odds, todos os erros de bloco.
   Testes: `TipGrammarMultTest` inteiro (3a–3l).
9. **Formato não reconhecido e limite de tamanho.** A bandeira, `TipFormat.EXEMPLO`, o teto
   de 20 000 caracteres. Testes: `TipGrammarUnknownFormatTest` (E1–E4), `TipFormatExampleTest`
   (E5), 12d.
10. **Corpus fixo.** `TipTextSamples` + `TipFixedCorpusTest` (10a–10c). É aqui que se prova
    o número da spec: 6 tips, 8 seleções.
11. **Costura para F06b.** `comResolucao`/`comData`, o decorador de teste, `catalog` ignorado,
    entradas nulas. Testes: `TipParserSeamTest` (13b, 13d, 13e).
12. **Desempenho.** `TipParserPerformanceTest` (12a–12c) e as convenções que o sustentam
    (X6). Se 12a falhar, o problema é algorítmico — corrigir o algoritmo, **nunca** subir o
    limiar do teste.
13. **Convenções finais.** `TipsConventionsTest` completo (X4, X5). Correr `./mvnw test`
    inteiro duas vezes seguidas e confirmar que os 274 JUnit e os 26 Vitest da baseline
    continuam todos verdes.

---

## 6. Não tocar

Proibido alterar, sob qualquer pretexto, nesta feature:

- `docs/specs/seerhub.md`
- `docs/features/BACKLOG.md`
- `docs/features/CHANGELOG.md`
- Todos os `plan.md` e `handoff.md` de F00 a F05
  (`docs/features/F00-fundacoes/`, `F01-contas-autenticacao/`, `F02-comunidades/`,
  `F03-subscricoes/`, `F04-papeis-permissoes/`, `F05-sincronizacao-futebol/`)
- `seerhub.md` (o brief na raiz)
- `.env` — **nem ler**
- `.claude/` e tudo o que lá dentro estiver
- `backend/src/main/resources/db/migration/V1__enable_extensions.sql`,
  `V2__baseline_schema.sql`, `V3__*.sql`, `V4__api_call_budget.sql` — e **não criar `V5`**
- Todo o pacote `pt.seerhub.football` (produção e testes) — F06a lê `FixtureView` e nada mais
- `pt.seerhub.user`, `pt.seerhub.community`, `pt.seerhub.common`, `pt.seerhub.config`
- `backend/src/main/resources/application.yml`, `application-local.yml`,
  `backend/src/test/resources/application-test.yml`
- `.env.example`, `docker-compose.yml`, `backend/pom.xml`, `pom.xml` da raiz
- `backend/src/main/java/pt/seerhub/config/SecurityConfig.java` (F06a não tem endpoint)
- `backend/src/test/java/pt/seerhub/support/AbstractIntegrationTest.java`, `RepoRoot.java`,
  `FootballTestSupport.java`, `AuthTestSupport.java`, `CommunityTestSupport.java`
- **Toda a pasta `frontend/`** — zero ficheiros criados ou editados; os 26 testes Vitest têm
  de continuar exatamente iguais
- `CLAUDE.md` — o handoff descreve o que mudou; é o orquestrador que decide se o `CLAUDE.md`
  passa a mencionar `pt.seerhub.tips`

Comportamentos que não podem mudar: nenhuma migração nova, nenhuma entidade JPA nova,
nenhum bean que abra ligação a Postgres, nenhum endpoint HTTP, nenhuma propriedade de
configuração nova, nenhuma variável de ambiente nova.

---

## 7. Verificação

Comandos exatos, na ordem, a partir da raiz do repositório.

**1. Ciclo rápido durante a implementação**
```
./mvnw test -Dtest='Tip*Test,TipsConventionsTest' -pl backend
```
Sucesso: `BUILD SUCCESS`, `Failures: 0, Errors: 0`. Cada passo de §5 deve deixar este
comando verde antes do passo seguinte.

**2. Suite de backend completa**
```
./mvnw test
```
Sucesso: `BUILD SUCCESS`; `Tests run: <274 + os novos>, Failures: 0, Errors: 0, Skipped: 0`.
**Os 274 testes JUnit da baseline têm de continuar todos a passar** — nenhum ficheiro
existente é editado, portanto qualquer regressão neles é sinal de que se tocou onde não se
devia (ver §6).

**3. Suite de backend duas vezes seguidas, para apanhar dependência de ordem**
```
./mvnw test && ./mvnw test
```
Sucesso: as duas execuções com a mesma contagem. Especialmente relevante para
`TipParserPerformanceTest` (12a): se o p95 oscilar perto do limiar, o limiar está mal
escolhido — reportar no handoff, não o subir em silêncio.

**4. Frontend intocado**
```
cd frontend && npm test
```
Sucesso: `Test Files 12 passed (12)`, `Tests 26 passed (26)` — **exatamente estes números**,
inalterados face à baseline.

**5. Raio de alteração**
```
ls backend/src/main/java/pt/seerhub/tips/*/ backend/src/main/java/pt/seerhub/tips/parser/*/
ls backend/src/main/resources/db/migration/
```
Sucesso: 31 ficheiros `.java` sob `pt/seerhub/tips/`; **exatamente quatro** migrações
(`V1`, `V2`, `V3`, `V4`) — nenhuma `V5`.

**6. A gramática não depende de base de dados nem de rede** (também asserido por `X4`/`X5`,
mas confirmar à mão dá sinal imediato)
```
grep -rn "jakarta.persistence\|springframework.data\|java.net\|RestClient\|WebClient\|JdbcTemplate" backend/src/main/java/pt/seerhub/tips/ ; echo "esperado: nenhuma linha"
grep -rn "SpringBootTest\|AbstractIntegrationTest\|Testcontainers\|http://" backend/src/test/java/pt/seerhub/tips/ ; echo "esperado: nenhuma linha"
```

**7. A suite corre sem qualquer variável de ambiente da API-Football**
```
API_FOOTBALL_KEY= API_FOOTBALL_DAILY_BUDGET= API_FOOTBALL_SEASON= ./mvnw test
```
Sucesso: idêntico ao passo 2. F06a não pode ter tornado nada dependente do fornecedor.

**8. Verificação manual:** nenhuma. F06a não expõe endpoint, não altera o esquema, não
altera configuração e não toca no frontend — não há nada de observável fora dos testes.
`docker compose` não precisa de ser corrido nesta feature.

---

## 8. Casos de fronteira cobertos

Da §10 da spec, os que **esta** feature possui:

| Caso de fronteira (§10) | Como F06a o trata | Teste |
| --- | --- | --- |
| **"Tipster cola o formato do Telegram dele em vez do formato do SeerHub"** | Zero linhas válidas ⇒ `formatoNaoReconhecido() == true` e `TipFormat.EXEMPLO` disponível. A lista de erros continua completa (nada se perde), mas a bandeira diz ao ecrã de revisão para mostrar o exemplo lado a lado com o texto colado, não a lista de erros. É a mitigação literal que a spec pede. | E1, E2 |
| **"Nenhuma linha do bloco é válida"** | O texto original volta intacto em `rawText`, com o erro anotado linha a linha em `erros`, e `estado() == FAILED`. | E1, X1, X2 |
| **Linhas inválidas nunca bloqueiam as válidas** (§6.1, "Falha") | O parser acumula erros em vez de lançar; um lote misto devolve tips **e** erros. | 4a, 4m |
| **Uma tip pode ficar sem jogo associado** (§10, "API-Football sem os jogos de uma liga menor") | `fixtureId` é sempre `null` em F06a e `tip_selections.fixture_id` é nullable no baseline — o caminho já existe, F06b só o preenche quando pode. | 10b |
| **Desambiguação por data quando há dois candidatos** (§10) | F06a entrega a `LocalDate` já com o ano inferido, ou `null` quando o tipster a omitiu. É o insumo da desambiguação de F06b, e é por isso que a inferência do ano é feita aqui e não lá. | 1g, 1h |

Casos de fronteira próprios desta gramática, não listados na spec mas decididos aqui:

- Bloco acima de 20 000 caracteres → um único `BLOCO_DEMASIADO_GRANDE` na linha 1, sem varrer
  o texto (12d).
- Bloco entre 2000 e 20 000 caracteres → parseia normalmente; 2000 é um critério de
  desempenho, não um limite (12c).
- Tabulações misturadas com espaços → parseia, e as colunas continuam a apontar o carácter
  certo da linha **original** (4j).
- `29/02` escrito num ano não bissexto → salta para o ano bissexto candidato (1h).
- Odd escrita com vírgula (`1,85`) → aceite; é a escrita portuguesa (1e).
- Duas `MULT` seguidas, e `MULT` como última linha do texto → duas tips independentes /
  `MULT_VAZIO` (3f, 3h).
- Entrada `null` em `rawText` ou em `catalog` → `ParseResult` vazio, nunca `NullPointerException`
  (13e).

---

## 9. Riscos em aberto

### 9.1 Resolução da deliberação (LISA, 3 min — evidência: o exemplo de formato do R6, os casos de fronteira da §10, o `CHECK` do `V2`, o catálogo fixo de F05)

**Pergunta:** que desenho de gramática dá os erros mais úteis quando um tipster real cola
algo ligeiramente errado, e como se estrutura o parser para que F06b acrescente resolução de
equipas e F07 acrescente revisão sem que nenhum dos dois toque na gramática?

**H1 — estratégia de análise.** *Descida recursiva à mão sobre caracteres* vs. *regex por
campo* vs. *estilo combinador de parsers*.
**PARCIALMENTE CONFIRMADA, com o vencedor a ser um híbrido.** A evidência decisiva é o
próprio exemplo do R6: o formato é **delimitado por `|` ao nível superior** e livre dentro de
cada campo. Uma descida recursiva ao carácter trata o `|` como um token qualquer e paga
complexidade que a estrutura não exige. Uma regex única por linha é pior no que interessa:
falha em bloco e a única coisa que sabe dizer é "não corresponde" — precisamente o erro
inútil que a §10 identifica como indistinguível de um bug para quem usa pela primeira vez.
Combinadores dariam bons erros mas exigiriam infraestrutura própria em Java 21 sem
dependências (a spec exige "sem dependências externas", §9).
**Resolução:** *tokenizador de campos escrito à mão* — `LineSplitter` parte por `|` e guarda
o deslocamento absoluto de cada segmento; cada campo tem um scanner com regex **ancorada e
sem quantificadores aninhados**; e um passo de diagnóstico (`FieldDiagnostics`) escolhe a
mensagem quando algo falha. A regex fica confinada ao interior de um campo, onde não pode
esconder a posição do erro, porque a posição vem do splitter e não da regex. É literalmente
o que a §9 da spec descreve ("tokenizer por linha + regras por campo") — mas o valor está no
terceiro passo, o diagnóstico, que a spec não nomeia e que é onde nasce a diferença entre
*"linha 3, coluna 34: falta a stake no fim da linha. Exemplo: `| 2u`"* e *"linha 3: sintaxe
inválida"*.

**H2 — como os erros transportam a posição.**
**CONFIRMADA na forma forte.** Erros são **dados, nunca exceções** (`ParseError` acumulado,
`parse` não lança em nenhuma entrada — asserido em 4m). Cada erro traz `linha` e `coluna`
1-based **sobre o texto original**, mais o `trecho` ofensivo, mais um `codigo` de enum, mais
um `esperado` em prosa portuguesa com exemplo concreto. O código de enum existe porque o
frontend de F07 e os testes têm de casar sobre algo estável; a prosa existe porque é o
material didático do pressuposto de produto da §11. Decisão associada, tomada aqui: **a linha
original nunca é reescrita** (nem para normalizar tabulações) — os scanners saltam espaços,
mas as colunas vêm sempre do texto tal como foi colado. Sem esta regra, "mistura de tabulações
e espaços" desloca silenciosamente todas as colunas do lote e o erro passa a apontar para o
sítio errado, que é pior do que não apontar.

**H3 — catálogo de mercados: enum, tabela ou interface de estratégia.**
**REJEITADA na forma pura; resolvida em duas peças.** A evidência que fecha a questão está no
`V2__baseline_schema.sql`: `CHECK (market IN ('MATCH_RESULT','DOUBLE_CHANCE','OVER_UNDER',
'BTTS','HANDICAP'))`. Isto significa que **acrescentar um mercado exige migração de qualquer
forma** — logo o enum não é o estrangulamento de extensibilidade que parecia, e uma tabela em
base de dados custaria o mesmo em migração mais I/O em runtime dentro de um parser que a §9
quer sem dependências. Mas um enum sozinho também não chega: `O2.5` e `H-1.5` carregam uma
**linha** extraída do token, o que é comportamento, não taxonomia; e R8 vai pendurar
comportamento de resolução no mesmo conceito.
**Resolução:** duas peças com responsabilidades disjuntas. `Market` (enum em
`pt.seerhub.tips.domain`) é a taxonomia persistida, burra, espelho exato do `CHECK` — é sobre
ela que R8 despacha. `MarketDefinition` (interface em `pt.seerhub.tips.parser.market`, com
`MarketCatalog` a registar as cinco implementações por ordem) é o comportamento de
reconhecimento. Acrescentar um mercado = uma classe nova + uma linha no catálogo (+ migração
para o `CHECK`, se for uma família nova). Acrescentar resolução automática = um `switch` sobre
o enum em F08, sem tocar no parser. Nenhum dos dois obriga a mexer no outro.

**Como F06b e F07 entram sem tocar na gramática — a decisão estrutural (D1).**
Duas hipóteses foram consideradas: injetar um colaborador `TeamResolver` no
`GrammarTipTextParser` (F06b substitui o bean), ou **decorar** o parser. Venceu a decoração,
e a razão é verificável: com injeção, F06b tem de editar o construtor e o corpo de
`GrammarTipTextParser` — exatamente o ficheiro que o R6 quer estável. Com decoração, F06b
cria `ResolvingTipTextParser implements TipTextParser`, anotado `@Service @Primary`, que
recebe `GrammarTipTextParser` no construtor, chama-o, e devolve o mesmo `ParseResult` com as
seleções reescritas por `comResolucao(...)`/`comData(...)`. **F06b não edita uma única linha
de F06a**; F07 injeta `TipTextParser` e recebe o `@Primary` que existir. A costura é provada
já em F06a por um decorador de teste (13b) — não fica por verificar até F06b chegar.
Decisão explícita para o planeador de F06b: **usar `@Primary`, não `@ConditionalOnMissingBean`**
(a avaliação condicional entre duas `@Configuration` da própria aplicação é dependente de
ordem de registo; `@Primary` é determinístico).

**Consequência para §11 da spec (o pressuposto mais arriscado do produto).** O pressuposto é
que o tipster aceita aprender o formato. A gramática não pode reduzir esse risco — mas pode
não o agravar. Foi isso que decidiu três escolhas de leniência que de outra forma seriam
arbitrárias: aceitar `,` como separador decimal (escrita portuguesa), aceitar `x`/`vs`/`–`
além de `-` no par de equipas, e aceitar as escritas invertidas da dupla hipótese. Nenhuma
introduz ambiguidade, todas eliminam uma rejeição que o tipster leria como bug.

### 9.2 Riscos que podem tornar este plano errado

1. **A regra de indentação do `MULT` é frágil na cola real.** Um tipster que copie do
   Telegram pode perder a indentação e ver `MULT_VAZIO` mais N linhas com `CAMPOS_A_MENOS` —
   confuso. Mitigação já no plano: a mensagem de `MULT_VAZIO` diz explicitamente que as
   seleções têm de ficar indentadas sob o `MULT`, e a mensagem das filhas remete para o
   exemplo. **Rejeitei deliberadamente a alternativa "absorver linhas não indentadas de 3
   campos a seguir a um `MULT`"** — é magia que transforma um erro de escrita numa aposta
   diferente da pretendida, e uma aposta errada custa dinheiro real ao subscritor.
   *Como descobrir cedo:* Q2 da spec (mostrar o formato a um tipster real). Barato e ainda
   por fazer.
2. **`H-1` sem indicação de equipa.** A spec não diz a quem se aplica o handicap. Decidi
   `HOME` por convenção de mercado, com `H1`/`H2` explícitos como escape. Se estiver errado,
   é uma tabela de conversão em `HandicapMarket` e nada mais — nenhum dado persistido muda de
   forma, porque `selecao` já distingue `HOME` de `AWAY`.
   *Como descobrir cedo:* a mesma conversa da Q2.
3. **O limiar de 20 ms do teste de desempenho.** É ~100× acima do custo real medido esperado
   (a ordem de grandeza de um parse linear de 2000 caracteres em JVM aquecida é de dezenas de
   microssegundos) e 10× abaixo do critério da spec. Se o teste oscilar em torno do limiar na
   máquina do implementador, isso **não** é ruído a tolerar — é sinal de que o parser tem um
   custo não linear escondido. **Regra dura: se 12a falhar, corrigir o algoritmo; subir o
   limiar exige justificação explícita no handoff com o p95 medido.** O guarda determinístico
   contra retrocesso catastrófico (12b, `assertTimeoutPreemptively` de 2 s sobre entradas
   adversariais) é o que apanha a falha *real* de forma imune à carga da máquina; 12a é a
   rede de segurança de regressão, não a prova principal.
4. **`ParsedSelection` tem 10 componentes.** É muito para um `record`, e a tentação de
   F06b/F07 será acrescentar mais. Mitigação: `TeamResolution` já agrupa tudo o que F06b
   produz, e `comResolucao`/`comData` são as duas únicas portas. Se F06b precisar de um campo
   que não caiba em `TeamResolution`, é sinal de que a linha F06a/F06b foi mal traçada — deve
   ficar registado no handoff de F06b, não resolvido a acrescentar componentes a
   `ParsedSelection`.
5. **`FixtureCandidate(fixtureId, semelhanca)` pode ser insuficiente para F06b.** Escolhi o
   mínimo de propósito: F07 já tem de chamar `FootballCatalogService.jogo(fixtureId)` para
   obter emblemas, liga e hora de início, portanto duplicar esses dados no candidato seria
   redundância a manter sincronizada. Se F06b precisar mesmo de mais, acrescentar componentes
   a um `record` é aditivo para quem só lê os existentes — mas **renomear ou remover os dois
   atuais está proibido**, porque F07 já os terá.
6. **`pt.seerhub.tips` passa a depender de `pt.seerhub.football.service.FixtureView` sem o
   usar.** É uma dependência de compilação por causa da assinatura de `TipCatalog.jogos()`.
   A alternativa — F06b alargar `TipCatalog` mais tarde — foi rejeitada porque alterar a
   interface depois de F07 a consumir é exatamente o que o R6 proíbe ("sem tocar no ecrã de
   revisão nem no modelo de dados"). O custo de a assumir agora é zero em runtime e uma linha
   de `import`.
7. **Nada nesta feature é verificável fora dos testes.** Não há ecrã, não há endpoint, não há
   linha em base de dados. O único sinal de qualidade é a qualidade dos testes — e, em
   particular, a fidelidade do corpus de `TipTextSamples` ao que um tipster escreve. O corpus
   parte do exemplo literal da spec (que, verificado, usa exatamente as equipas do catálogo
   fixo de F05: Benfica–Porto, Arsenal–Chelsea, Girona–Real Madrid, Bayern–Leipzig,
   Inter–Napoli), o que faz com que o mesmo corpus sirva depois a F06b para provar as ligações
   reais sem inventar dados novos. Se o implementador escrever um corpus sintético em vez
   deste, F06b perde essa continuidade.