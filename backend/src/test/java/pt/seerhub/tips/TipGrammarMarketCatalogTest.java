package pt.seerhub.tips;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import pt.seerhub.support.TipTextSamples;
import pt.seerhub.tips.domain.Market;
import pt.seerhub.tips.parser.GrammarTipTextParser;
import pt.seerhub.tips.parser.ParseError;
import pt.seerhub.tips.parser.ParseErrorCode;
import pt.seerhub.tips.parser.ParseResult;
import pt.seerhub.tips.parser.ParsedSelection;
import pt.seerhub.tips.parser.TipCatalog;

import static org.assertj.core.api.Assertions.assertThat;

/** R6-2 (2a–2h): catálogo de mercados com abreviaturas. */
class TipGrammarMarketCatalogTest {

    private final GrammarTipTextParser parser = new GrammarTipTextParser();
    private final TipCatalog catalog = TipTextSamples.catalogoDeTeste(LocalDate.of(2026, 7, 27));

    private ParsedSelection parseUmaSelecao(String segmentoDeMercado) {
        String linha = "28/07 Benfica - Porto | " + segmentoDeMercado + " | 1.85 | 2u";
        ParseResult resultado = parser.parse(linha, catalog);
        assertThat(resultado.temErros()).as("linha '%s' devia parsear sem erros", linha).isFalse();
        assertThat(resultado.tips()).hasSize(1);
        return resultado.tips().get(0).selecoes().get(0);
    }

    private ParseError parseComErro(String segmentoDeMercado) {
        String linha = "28/07 Benfica - Porto | " + segmentoDeMercado + " | 1.85 | 2u";
        ParseResult resultado = parser.parse(linha, catalog);
        assertThat(resultado.tips()).as("linha '%s' não devia produzir tip", linha).isEmpty();
        assertThat(resultado.erros()).as("linha '%s' devia produzir exatamente um erro", linha).hasSize(1);
        return resultado.erros().get(0);
    }

    @Test
    void resultadoFinalReconheceUmXDoisComESemTokenDeMercado() {
        assertThat(parseUmaSelecao("1X2 1").mercado()).isEqualTo(Market.MATCH_RESULT);
        assertThat(parseUmaSelecao("1X2 1").selecao()).isEqualTo("HOME");
        assertThat(parseUmaSelecao("1X2 X").selecao()).isEqualTo("DRAW");
        assertThat(parseUmaSelecao("1X2 2").selecao()).isEqualTo("AWAY");
        // Sem o token de mercado.
        assertThat(parseUmaSelecao("1").selecao()).isEqualTo("HOME");
        assertThat(parseUmaSelecao("X").selecao()).isEqualTo("DRAW");
        assertThat(parseUmaSelecao("2").selecao()).isEqualTo("AWAY");
    }

    @Test
    void duplaHipoteseReconheceAsTresCombinacoesEAsEscritasInvertidas() {
        record Caso(String token, String selecaoEsperada) {
        }
        List<Caso> casos = List.of(
                new Caso("1X", "HOME_DRAW"), new Caso("X1", "HOME_DRAW"),
                new Caso("12", "HOME_AWAY"), new Caso("21", "HOME_AWAY"),
                new Caso("X2", "DRAW_AWAY"), new Caso("2X", "DRAW_AWAY"));

        for (Caso caso : casos) {
            ParsedSelection selecao = parseUmaSelecao(caso.token());
            assertThat(selecao.mercado()).as("token '%s'", caso.token()).isEqualTo(Market.DOUBLE_CHANCE);
            assertThat(selecao.selecao()).as("token '%s'", caso.token()).isEqualTo(caso.selecaoEsperada());
        }
    }

    @Test
    void maisMenosGolosReconheceFormaFundidaESeparadaEExtraiALinha() {
        ParsedSelection fundidaOver = parseUmaSelecao("O2.5");
        assertThat(fundidaOver.mercado()).isEqualTo(Market.OVER_UNDER);
        assertThat(fundidaOver.selecao()).isEqualTo("OVER");
        assertThat(fundidaOver.linhaMercado()).isEqualByComparingTo("2.50");

        ParsedSelection fundidaUnder = parseUmaSelecao("U3.5");
        assertThat(fundidaUnder.selecao()).isEqualTo("UNDER");
        assertThat(fundidaUnder.linhaMercado()).isEqualByComparingTo("3.50");

        ParsedSelection separadaO = parseUmaSelecao("O 2.5");
        assertThat(separadaO.selecao()).isEqualTo("OVER");
        assertThat(separadaO.linhaMercado()).isEqualByComparingTo("2.50");

        ParsedSelection separadaUnder = parseUmaSelecao("UNDER 3.5");
        assertThat(separadaUnder.selecao()).isEqualTo("UNDER");
        assertThat(separadaUnder.linhaMercado()).isEqualByComparingTo("3.50");
    }

    @Test
    void ambasMarcamExigeTokenDeMercadoOuFormaFundida() {
        assertThat(parseUmaSelecao("BTTS S").selecao()).isEqualTo("YES");
        assertThat(parseUmaSelecao("BTTS N").selecao()).isEqualTo("NO");
        assertThat(parseUmaSelecao("AM S").selecao()).isEqualTo("YES");
        assertThat(parseUmaSelecao("GG").selecao()).isEqualTo("YES");
        assertThat(parseUmaSelecao("NG").selecao()).isEqualTo("NO");

        for (ParsedSelection s : List.of(parseUmaSelecao("BTTS S"), parseUmaSelecao("GG"))) {
            assertThat(s.mercado()).isEqualTo(Market.BTTS);
        }

        // "S" isolado, sem o token de mercado, é rejeitado.
        ParseError erro = parseComErro("S");
        assertThat(erro.codigo()).isEqualTo(ParseErrorCode.MERCADO_DESCONHECIDO);
    }

    @Test
    void handicapExtraiEquipaELinhaComSinal() {
        ParsedSelection menos1 = parseUmaSelecao("H-1");
        assertThat(menos1.mercado()).isEqualTo(Market.HANDICAP);
        assertThat(menos1.selecao()).isEqualTo("HOME");
        assertThat(menos1.linhaMercado()).isEqualByComparingTo("-1.00");

        ParsedSelection mais1e5 = parseUmaSelecao("H+1.5");
        assertThat(mais1e5.selecao()).isEqualTo("HOME");
        assertThat(mais1e5.linhaMercado()).isEqualByComparingTo("1.50");

        ParsedSelection fora = parseUmaSelecao("H2+1.5");
        assertThat(fora.selecao()).isEqualTo("AWAY");
        assertThat(fora.linhaMercado()).isEqualByComparingTo("1.50");
    }

    @Test
    void linhaDeQuartoEGramaticaValidaMesmoNaoSendoAutoResolvivel() {
        ParsedSelection overDeQuarto = parseUmaSelecao("O2.25");
        assertThat(overDeQuarto.mercado()).isEqualTo(Market.OVER_UNDER);
        assertThat(overDeQuarto.linhaMercado()).isEqualByComparingTo("2.25");

        ParsedSelection handicapDeQuarto = parseUmaSelecao("H-0.75");
        assertThat(handicapDeQuarto.mercado()).isEqualTo(Market.HANDICAP);
        assertThat(handicapDeQuarto.linhaMercado()).isEqualByComparingTo("-0.75");
    }

    @Test
    void mercadoDesconhecidoDevolveErroComExemplosAceites() {
        ParseError erro = parseComErro("ZZZ 1");
        assertThat(erro.codigo()).isEqualTo(ParseErrorCode.MERCADO_DESCONHECIDO);
        assertThat(erro.esperado()).contains("1X2");
    }

    @Test
    void selecaoInvalidaParaOMercadoNomeiaAsSelecoesAceites() {
        ParseError erro1x2 = parseComErro("1X2 3");
        assertThat(erro1x2.codigo()).isEqualTo(ParseErrorCode.SELECAO_INVALIDA_PARA_O_MERCADO);
        assertThat(erro1x2.esperado()).contains("1").contains("X").contains("2");

        ParseError erroBtts = parseComErro("BTTS talvez");
        assertThat(erroBtts.codigo()).isEqualTo(ParseErrorCode.SELECAO_INVALIDA_PARA_O_MERCADO);
        assertThat(erroBtts.esperado()).contains("S").contains("N");
    }
}
