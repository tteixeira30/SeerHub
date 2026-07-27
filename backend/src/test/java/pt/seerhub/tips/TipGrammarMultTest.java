package pt.seerhub.tips;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import pt.seerhub.support.TipTextSamples;
import pt.seerhub.tips.parser.GrammarTipTextParser;
import pt.seerhub.tips.parser.ParseError;
import pt.seerhub.tips.parser.ParseErrorCode;
import pt.seerhub.tips.parser.ParseResult;
import pt.seerhub.tips.parser.ParsedTip;
import pt.seerhub.tips.parser.TipCatalog;
import pt.seerhub.tips.parser.TipKind;

import static org.assertj.core.api.Assertions.assertThat;

/** R6-3 (3a–3l): o bloco MULT. */
class TipGrammarMultTest {

    private final GrammarTipTextParser parser = new GrammarTipTextParser();
    private final TipCatalog catalog = TipTextSamples.catalogoDeTeste(LocalDate.of(2026, 7, 27));

    @Test
    void blocoMultProduzUmaTipComNSelecoes() {
        String texto = """
                MULT | 2u
                  28/07 Benfica - Porto | 1 | 1.40
                  29/07 Arsenal - Chelsea | 1X | 1.55
                  30/07 Girona - Real Madrid | O2.5 | 1.90
                """;
        ParseResult resultado = parser.parse(texto, catalog);

        assertThat(resultado.temErros()).isFalse();
        assertThat(resultado.tips()).hasSize(1);
        ParsedTip tip = resultado.tips().get(0);
        assertThat(tip.tipo()).isEqualTo(TipKind.MULTIPLA);
        assertThat(tip.selecoes()).hasSize(3);
    }

    @Test
    void aOddTotalEOProdutoDasOddsDasSelecoes() {
        String texto = """
                MULT | 2u
                  28/07 Benfica - Porto | 1 | 1.40
                  29/07 Arsenal - Chelsea | 1X | 1.55
                  30/07 Girona - Real Madrid | O2.5 | 1.90
                """;
        ParseResult resultado = parser.parse(texto, catalog);

        assertThat(resultado.tips().get(0).oddTotal()).isEqualByComparingTo("4.123");
    }

    @Test
    void oStakeEDeclaradoUmaSoVezNaLinhaMult() {
        String texto = """
                MULT | 2u
                  28/07 Benfica - Porto | 1 | 1.40
                  29/07 Arsenal - Chelsea | 1X | 1.55
                """;
        ParseResult resultado = parser.parse(texto, catalog);

        assertThat(resultado.temErros()).isFalse();
        assertThat(resultado.tips().get(0).stakeUnidades()).isEqualByComparingTo("2.00");
    }

    @Test
    void oBlocoFechaNaPrimeiraLinhaNaoIndentada() {
        String texto = """
                MULT | 2u
                  28/07 Benfica - Porto | 1 | 1.40
                  29/07 Arsenal - Chelsea | 1X | 1.55
                30/07 Girona - Real Madrid | O2.5 | 1.90 | 1u
                """;
        ParseResult resultado = parser.parse(texto, catalog);

        assertThat(resultado.temErros()).isFalse();
        assertThat(resultado.tips()).hasSize(2);
        assertThat(resultado.tips().get(0).tipo()).isEqualTo(TipKind.MULTIPLA);
        assertThat(resultado.tips().get(0).selecoes()).hasSize(2);
        assertThat(resultado.tips().get(1).tipo()).isEqualTo(TipKind.SIMPLES);
    }

    @Test
    void linhaEmBrancoDentroDoBlocoNaoOFecha() {
        String texto = "MULT | 2u\n"
                + "  28/07 Benfica - Porto | 1 | 1.40\n"
                + "\n"
                + "  29/07 Arsenal - Chelsea | 1X | 1.55\n";
        ParseResult resultado = parser.parse(texto, catalog);

        assertThat(resultado.temErros()).isFalse();
        assertThat(resultado.tips()).hasSize(1);
        assertThat(resultado.tips().get(0).selecoes()).hasSize(2);
    }

    @Test
    void doisBlocosMultProduzemDuasTipsIndependentes() {
        String texto = """
                MULT | 2u
                  28/07 Benfica - Porto | 1 | 1.40
                  29/07 Arsenal - Chelsea | 1X | 1.55
                MULT | 1u
                  30/07 Girona - Real Madrid | O2.5 | 1.90
                  31/07 Bayern - Leipzig | 1 | 1.40
                """;
        ParseResult resultado = parser.parse(texto, catalog);

        assertThat(resultado.temErros()).isFalse();
        assertThat(resultado.tips()).hasSize(2);
        assertThat(resultado.tips()).allMatch(t -> t.tipo() == TipKind.MULTIPLA);
        assertThat(resultado.tips().get(0).stakeUnidades()).isEqualByComparingTo("2.00");
        assertThat(resultado.tips().get(1).stakeUnidades()).isEqualByComparingTo("1.00");
    }

    @Test
    void multComUmaSoSelecaoEErroNoCabecalho() {
        String texto = """
                MULT | 2u
                  28/07 Benfica - Porto | 1 | 1.40
                """;
        ParseResult resultado = parser.parse(texto, catalog);

        assertThat(resultado.tips()).isEmpty();
        assertThat(resultado.erros()).hasSize(1);
        ParseError erro = resultado.erros().get(0);
        assertThat(erro.codigo()).isEqualTo(ParseErrorCode.MULT_COM_UMA_SELECAO);
        assertThat(erro.linha()).isEqualTo(1);
    }

    @Test
    void multSemSelecoesIndentadasExplicaQueTemDeSerIndentado() {
        ParseResult resultado = parser.parse("MULT | 2u", catalog);

        assertThat(resultado.tips()).isEmpty();
        assertThat(resultado.erros()).hasSize(1);
        ParseError erro = resultado.erros().get(0);
        assertThat(erro.codigo()).isEqualTo(ParseErrorCode.MULT_VAZIO);
        assertThat(erro.esperado()).contains("indentad");
    }

    @Test
    void multSemStakeEErroNoCabecalhoComColunaNoFim() {
        String linha = "MULT";
        ParseResult resultado = parser.parse(linha, catalog);

        assertThat(resultado.tips()).isEmpty();
        assertThat(resultado.erros()).hasSize(1);
        ParseError erro = resultado.erros().get(0);
        assertThat(erro.codigo()).isEqualTo(ParseErrorCode.MULT_SEM_STAKE);
        assertThat(erro.linha()).isEqualTo(1);
        assertThat(erro.coluna()).isEqualTo(linha.length());
    }

    @Test
    void stakeNumaLinhaFilhaEErroNessaLinha() {
        String texto = """
                MULT | 2u
                  28/07 Benfica - Porto | 1 | 1.40 | 1u
                  29/07 Arsenal - Chelsea | 1X | 1.55
                """;
        ParseResult resultado = parser.parse(texto, catalog);

        assertThat(resultado.tips()).isEmpty();
        assertThat(resultado.erros()).hasSize(2);
        assertThat(resultado.erros()).anySatisfy(erro -> {
            assertThat(erro.codigo()).isEqualTo(ParseErrorCode.STAKE_DENTRO_DE_MULT);
            assertThat(erro.linha()).isEqualTo(2);
        });
        assertThat(resultado.erros()).anySatisfy(erro -> {
            assertThat(erro.codigo()).isEqualTo(ParseErrorCode.MULT_COM_SELECAO_INVALIDA);
            assertThat(erro.linha()).isEqualTo(1);
        });
    }

    @Test
    void filhaInvalidaDescartaOAcumuladorEExplicaNoCabecalho() {
        String texto = """
                MULT | 2u
                  28/07 Benfica - Porto | 1 | 1.40
                  29/07 Arsenal - Chelsea | ZZZ | 1.55
                """;
        ParseResult resultado = parser.parse(texto, catalog);

        assertThat(resultado.tips()).isEmpty();
        assertThat(resultado.erros()).hasSize(2);
        assertThat(resultado.erros()).anySatisfy(erro -> {
            assertThat(erro.codigo()).isEqualTo(ParseErrorCode.MERCADO_DESCONHECIDO);
            assertThat(erro.linha()).isEqualTo(3);
        });
        assertThat(resultado.erros()).anySatisfy(erro -> {
            assertThat(erro.codigo()).isEqualTo(ParseErrorCode.MULT_COM_SELECAO_INVALIDA);
            assertThat(erro.linha()).isEqualTo(1);
        });
    }

    @Test
    void oddTotalAcimaDoLimiteDaColunaERejeitadaNoCabecalho() {
        String texto = """
                MULT | 2u
                  28/07 Benfica - Porto | 1 | 20.00
                  29/07 Arsenal - Chelsea | 1X | 20.00
                  30/07 Girona - Real Madrid | O2.5 | 20.00
                """;
        ParseResult resultado = parser.parse(texto, catalog);

        assertThat(resultado.tips()).isEmpty();
        assertThat(resultado.erros()).hasSize(1);
        ParseError erro = resultado.erros().get(0);
        assertThat(erro.codigo()).isEqualTo(ParseErrorCode.ODD_TOTAL_EXCESSIVA);
        assertThat(erro.linha()).isEqualTo(1);
    }
}
