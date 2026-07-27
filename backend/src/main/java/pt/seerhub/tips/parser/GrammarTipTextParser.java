package pt.seerhub.tips.parser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;

import pt.seerhub.tips.parser.internal.FieldDiagnostics;
import pt.seerhub.tips.parser.internal.LineSplitter;
import pt.seerhub.tips.parser.internal.MatchFieldScanner;
import pt.seerhub.tips.parser.internal.OddsScanner;
import pt.seerhub.tips.parser.internal.Segment;
import pt.seerhub.tips.parser.internal.StakeScanner;
import pt.seerhub.tips.parser.market.MarketCatalog;
import pt.seerhub.tips.parser.market.MarketMatch;

/**
 * A implementação de F06a de {@link TipTextParser}. Orquestra: pré-condições → {@link
 * LineSplitter} → classificação de linha → scanners → {@link MarketCatalog} → montagem de
 * {@link ParsedTip}. Ignora {@code catalog.jogos()} por inteiro — só lê {@code
 * catalog.hoje()}, para a inferência do ano da data. <b>F06b não edita este ficheiro</b>: a
 * resolução de equipas entra por decoração, através de um {@link TipTextParser} novo que
 * envolve este (§9.1/D1 do plano).
 */
@Service
public class GrammarTipTextParser implements TipTextParser {

    @Override
    public ParseResult parse(String rawTextEntrada, TipCatalog catalogEntrada) {
        String rawText = rawTextEntrada == null ? "" : rawTextEntrada;
        // Salvaguarda defensiva: catalog só devia ser null por erro de quem chama — nunca lança (13e).
        TipCatalog catalog = catalogEntrada == null ? SimpleTipCatalog.vazio(Clock.systemUTC()) : catalogEntrada;

        if (rawText.length() > TipFormat.LIMITE_DE_CARACTERES) {
            ParseError erro = new ParseError(1, 1, "", ParseErrorCode.BLOCO_DEMASIADO_GRANDE,
                    "O texto colado é demasiado grande (mais de " + TipFormat.LIMITE_DE_CARACTERES
                            + " carateres). Cole um lote mais pequeno.");
            return new ParseResult(rawText, TipFormat.VERSAO, List.of(), List.of(erro), false);
        }

        List<LineSplitter.Linha> linhas = LineSplitter.dividirEmLinhas(rawText);
        List<ParsedTip> tips = new ArrayList<>();
        List<ParseError> erros = new ArrayList<>();

        MultBlock multAberto = null;

        for (LineSplitter.Linha linha : linhas) {
            if (linha.vazia()) {
                // Ignorada em todo o lado — exceto que não fecha um bloco MULT aberto.
                continue;
            }
            List<Segment> segmentos = LineSplitter.segmentar(linha.textoOriginal());
            boolean ehCabecalhoMult = "MULT".equals(segmentos.get(0).texto().toUpperCase(Locale.ROOT));

            if (ehCabecalhoMult) {
                if (multAberto != null) {
                    finalizarMult(multAberto, tips, erros);
                }
                multAberto = abrirMult(linha, segmentos, erros);
                continue;
            }

            if (multAberto != null && linha.indentada()) {
                processarLinhaFilha(linha, segmentos, multAberto, catalog, erros);
                continue;
            }

            if (multAberto != null) {
                finalizarMult(multAberto, tips, erros);
                multAberto = null;
            }

            processarTipSimples(linha, segmentos, catalog, tips, erros);
        }

        if (multAberto != null) {
            finalizarMult(multAberto, tips, erros);
        }

        boolean formatoNaoReconhecido = tips.isEmpty() && linhas.stream().anyMatch(l -> !l.vazia());
        return new ParseResult(rawText, TipFormat.VERSAO, tips, erros, formatoNaoReconhecido);
    }

    // === Tip simples ===

    private void processarTipSimples(LineSplitter.Linha linha, List<Segment> segmentos, TipCatalog catalog,
            List<ParsedTip> tips, List<ParseError> erros) {
        if (segmentos.size() != 4) {
            erros.add(FieldDiagnostics.diagnosticarContagemDeCamposSimples(segmentos, linha.numero()));
            return;
        }

        List<ParseError> errosDaLinha = new ArrayList<>();
        ParsedSelection selecao = lerSelecao(segmentos.get(0), segmentos.get(1), segmentos.get(2), linha.numero(),
                catalog, errosDaLinha);
        if (selecao == null) {
            erros.addAll(errosDaLinha);
            return;
        }

        StakeScanner.Resultado stake = StakeScanner.ler(segmentos.get(3), linha.numero());
        if (!stake.sucesso()) {
            erros.add(stake.erro());
            return;
        }

        ParsedTip tip = new ParsedTip(linha.numero(), TipKind.SIMPLES, stake.stake(), selecao.odd(),
                List.of(selecao), linha.textoOriginal());
        tips.add(tip);
    }

    /** Lê par de equipas + mercado + odd de uma linha (sem stake). {@code null} se algo falhar — o erro já fica em {@code erros}. */
    private ParsedSelection lerSelecao(Segment segMatch, Segment segMercado, Segment segOdd, int numeroLinha,
            TipCatalog catalog, List<ParseError> erros) {
        MatchFieldScanner.Resultado par = MatchFieldScanner.ler(segMatch, numeroLinha, catalog.hoje());
        if (!par.sucesso()) {
            erros.add(par.erro());
            return null;
        }

        Optional<MarketMatch> mercado = reconhecerSegmentoDeMercado(segMercado);
        if (mercado.isEmpty()) {
            erros.add(FieldDiagnostics.diagnosticarMercado(segMercado, numeroLinha));
            return null;
        }

        OddsScanner.Resultado odd = OddsScanner.ler(segOdd, numeroLinha);
        if (!odd.sucesso()) {
            erros.add(odd.erro());
            return null;
        }

        return new ParsedSelection(numeroLinha, par.data(), par.textoDaData(), par.casa(), par.fora(),
                mercado.get().mercado(), mercado.get().selecao(), mercado.get().linha(), odd.odd(),
                TeamResolution.porResolver());
    }

    private Optional<MarketMatch> reconhecerSegmentoDeMercado(Segment segmentoMercado) {
        List<FieldDiagnostics.Token> tokens = FieldDiagnostics.tokenizar(segmentoMercado.texto());
        if (tokens.size() == 1) {
            return MarketCatalog.reconhecer(null, tokens.get(0).texto());
        }
        if (tokens.size() == 2) {
            return MarketCatalog.reconhecer(tokens.get(0).texto(), tokens.get(1).texto());
        }
        return Optional.empty();
    }

    // === Bloco MULT ===

    /** Estado acumulado de um bloco MULT em construção — vive só durante a passagem pelas linhas. */
    private static final class MultBlock {
        final int linhaCabecalho;
        final String textoCabecalho;
        final List<String> linhasOriginais = new ArrayList<>();
        final List<ParsedSelection> selecoes = new ArrayList<>();
        BigDecimal stake;
        boolean erroDeCabecalhoJaRegistado;
        boolean algumaFilhaInvalida;

        MultBlock(int linhaCabecalho, String textoCabecalho) {
            this.linhaCabecalho = linhaCabecalho;
            this.textoCabecalho = textoCabecalho;
            this.linhasOriginais.add(textoCabecalho);
        }

        int colunaFimDaLinha() {
            return Math.max(1, textoCabecalho.length());
        }
    }

    private MultBlock abrirMult(LineSplitter.Linha linha, List<Segment> segmentos, List<ParseError> erros) {
        MultBlock bloco = new MultBlock(linha.numero(), linha.textoOriginal());
        StakeScanner.Resultado stake = segmentos.size() >= 2
                ? StakeScanner.ler(segmentos.get(1), linha.numero())
                : new StakeScanner.Resultado(null, null);
        if (segmentos.size() < 2 || !stake.sucesso()) {
            bloco.erroDeCabecalhoJaRegistado = true;
            erros.add(new ParseError(linha.numero(), bloco.colunaFimDaLinha(), linha.textoOriginal(),
                    ParseErrorCode.MULT_SEM_STAKE,
                    "Falta o stake do acumulador. Escreva-o na linha do cabeçalho: \"MULT | 2u\"."));
            return bloco;
        }
        bloco.stake = stake.stake();
        return bloco;
    }

    private void processarLinhaFilha(LineSplitter.Linha linha, List<Segment> segmentos, MultBlock bloco,
            TipCatalog catalog, List<ParseError> erros) {
        bloco.linhasOriginais.add(linha.textoOriginal());
        if (segmentos.size() != 3) {
            erros.add(FieldDiagnostics.diagnosticarContagemDeCamposMult(segmentos, linha.numero()));
            bloco.algumaFilhaInvalida = true;
            return;
        }
        List<ParseError> errosDaFilha = new ArrayList<>();
        ParsedSelection selecao = lerSelecao(segmentos.get(0), segmentos.get(1), segmentos.get(2), linha.numero(),
                catalog, errosDaFilha);
        if (selecao == null) {
            erros.addAll(errosDaFilha);
            bloco.algumaFilhaInvalida = true;
            return;
        }
        bloco.selecoes.add(selecao);
    }

    private void finalizarMult(MultBlock bloco, List<ParsedTip> tips, List<ParseError> erros) {
        if (bloco.erroDeCabecalhoJaRegistado) {
            return;
        }
        if (bloco.algumaFilhaInvalida) {
            erros.add(new ParseError(bloco.linhaCabecalho, bloco.colunaFimDaLinha(), bloco.textoCabecalho,
                    ParseErrorCode.MULT_COM_SELECAO_INVALIDA,
                    "Este acumulador tem pelo menos uma seleção inválida — corrija a linha assinalada "
                            + "e volte a colar o bloco."));
            return;
        }
        if (bloco.selecoes.isEmpty()) {
            erros.add(new ParseError(bloco.linhaCabecalho, bloco.colunaFimDaLinha(), bloco.textoCabecalho,
                    ParseErrorCode.MULT_VAZIO,
                    "Este MULT não tem seleções. As linhas do acumulador têm de ficar indentadas "
                            + "(começar com espaço ou tabulação) sob o \"MULT\"."));
            return;
        }
        if (bloco.selecoes.size() == 1) {
            erros.add(new ParseError(bloco.linhaCabecalho, bloco.colunaFimDaLinha(), bloco.textoCabecalho,
                    ParseErrorCode.MULT_COM_UMA_SELECAO,
                    "Um acumulador precisa de pelo menos duas seleções indentadas."));
            return;
        }

        BigDecimal produto = BigDecimal.ONE;
        for (ParsedSelection selecao : bloco.selecoes) {
            produto = produto.multiply(selecao.odd());
        }
        BigDecimal oddTotal = produto.setScale(3, RoundingMode.HALF_UP);
        if (oddTotal.compareTo(TipFormat.ODD_TOTAL_MAXIMA) > 0) {
            erros.add(new ParseError(bloco.linhaCabecalho, bloco.colunaFimDaLinha(), bloco.textoCabecalho,
                    ParseErrorCode.ODD_TOTAL_EXCESSIVA,
                    "A odd total deste acumulador excede o máximo permitido (999.999)."));
            return;
        }

        String textoOriginal = String.join("\n", bloco.linhasOriginais);
        tips.add(new ParsedTip(bloco.linhaCabecalho, TipKind.MULTIPLA, bloco.stake, oddTotal,
                List.copyOf(bloco.selecoes), textoOriginal));
    }
}
