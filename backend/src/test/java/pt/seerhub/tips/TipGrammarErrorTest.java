package pt.seerhub.tips;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import pt.seerhub.support.TipTextSamples;
import pt.seerhub.tips.domain.ImportStatus;
import pt.seerhub.tips.parser.GrammarTipTextParser;
import pt.seerhub.tips.parser.ParseError;
import pt.seerhub.tips.parser.ParseErrorCode;
import pt.seerhub.tips.parser.ParseResult;
import pt.seerhub.tips.parser.TipCatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** R6-4 (4a–4m), X1, X2: uma linha inválida nunca invalida o lote. */
class TipGrammarErrorTest {

    private final GrammarTipTextParser parser = new GrammarTipTextParser();
    private final TipCatalog catalog = TipTextSamples.catalogoDeTeste(LocalDate.of(2026, 7, 27));

    @Test
    void linhasValidasSeguemEAsInvalidasVoltamComErro() {
        String texto = """
                28/07 Benfica - Porto | 1X2 1 | 1.85 | 2u
                linha totalmente errada sem pipes nem nada
                28/07 Arsenal - Chelsea | O2.5 | 1.90 | 1u
                29/07 Girona - Real Madrid | BTTS talvez | 1.72 | 1u
                30/07 Inter - Napoli | 1 | 1.40 | 1u
                """;

        assertThatCode(() -> parser.parse(texto, catalog)).doesNotThrowAnyException();

        ParseResult resultado = parser.parse(texto, catalog);
        assertThat(resultado.tips()).hasSize(3);
        assertThat(resultado.erros()).hasSize(2);
    }

    @Test
    void cadaErroTrazLinhaColunaEOQueEraEsperado() {
        ParseResult resultado = parser.parse("linha completamente estranha", catalog);

        assertThat(resultado.erros()).hasSize(1);
        ParseError erro = resultado.erros().get(0);
        assertThat(erro.linha()).isEqualTo(1);
        assertThat(erro.coluna()).isGreaterThan(0);
        assertThat(erro.esperado()).isNotBlank();
        assertThat(erro.codigo()).isNotNull();
    }

    @Test
    void linhaSemOddNomeiaOCampoEmFaltaEmVezDeDizerQueNaoCorresponde() {
        ParseResult resultado = parser.parse("28/07 Benfica - Porto | 1X2 1 | 2u", catalog);

        assertThat(resultado.tips()).isEmpty();
        assertThat(resultado.erros()).hasSize(1);
        ParseError erro = resultado.erros().get(0);
        assertThat(erro.codigo()).isEqualTo(ParseErrorCode.CAMPOS_A_MENOS);
        assertThat(erro.esperado()).contains("odd");
        assertThat(erro.esperado()).doesNotContain("não corresponde");
    }

    @Test
    void dataMalformadaApontaAColunaDoTokenDaData() {
        for (String dataMalformada : new String[]{"32/01", "28/13", "2807", "28-07"}) {
            String linha = dataMalformada + " Benfica - Porto | 1X2 1 | 1.85 | 2u";
            ParseResult resultado = parser.parse(linha, catalog);

            assertThat(resultado.tips()).as("data '%s'", dataMalformada).isEmpty();
            assertThat(resultado.erros()).as("data '%s'", dataMalformada).hasSize(1);
            ParseError erro = resultado.erros().get(0);
            assertThat(erro.codigo()).as("data '%s'", dataMalformada).isEqualTo(ParseErrorCode.DATA_INVALIDA);
            assertThat(erro.coluna()).as("data '%s'", dataMalformada).isEqualTo(1);
        }
    }

    @Test
    void stakeSemUnidadeApontaOFimDoNumeroESugere2u() {
        String linha = "28/07 Benfica - Porto | 1X2 1 | 1.85 | 2";
        ParseResult resultado = parser.parse(linha, catalog);

        assertThat(resultado.erros()).hasSize(1);
        ParseError erro = resultado.erros().get(0);
        assertThat(erro.codigo()).isEqualTo(ParseErrorCode.STAKE_SEM_UNIDADE);
        assertThat(erro.coluna()).isEqualTo(linha.length());
        assertThat(erro.esperado()).contains("2u");
    }

    @Test
    void stakeForaDoIntervaloDeUnidadesERejeitada() {
        ParseResult acima = parser.parse("28/07 Benfica - Porto | 1X2 1 | 1.85 | 15u", catalog);
        assertThat(acima.erros()).hasSize(1);
        assertThat(acima.erros().get(0).codigo()).isEqualTo(ParseErrorCode.STAKE_FORA_DO_INTERVALO);

        ParseResult abaixo = parser.parse("28/07 Benfica - Porto | 1X2 1 | 1.85 | 0.1u", catalog);
        assertThat(abaixo.erros()).hasSize(1);
        assertThat(abaixo.erros().get(0).codigo()).isEqualTo(ParseErrorCode.STAKE_FORA_DO_INTERVALO);
    }

    @Test
    void oddNaoSuperiorAUmOuNaoNumericaEErroComColunaCerta() {
        for (String oddInvalida : new String[]{"1.00", "0.85", "abc"}) {
            String linha = "28/07 Benfica - Porto | 1X2 1 | " + oddInvalida + " | 2u";
            ParseResult resultado = parser.parse(linha, catalog);

            assertThat(resultado.erros()).as("odd '%s'", oddInvalida).hasSize(1);
            ParseError erro = resultado.erros().get(0);
            assertThat(erro.codigo()).as("odd '%s'", oddInvalida).isEqualTo(ParseErrorCode.ODD_INVALIDA);
            assertThat(erro.coluna()).as("odd '%s'", oddInvalida).isEqualTo(linha.indexOf(oddInvalida) + 1);
        }
    }

    @Test
    void camposAMaisApontamOInicioDoSegmentoExcedente() {
        String linha = "28/07 Benfica - Porto | 1X2 1 | 1.85 | 2u | extra";
        ParseResult resultado = parser.parse(linha, catalog);

        assertThat(resultado.erros()).hasSize(1);
        ParseError erro = resultado.erros().get(0);
        assertThat(erro.codigo()).isEqualTo(ParseErrorCode.CAMPOS_A_MAIS);
        assertThat(erro.coluna()).isEqualTo(linha.indexOf("extra") + 1);
    }

    @Test
    void parDeEquipasSemSeparadorOuComDoisEErroDistinto() {
        ParseResult semSeparador = parser.parse("28/07 BenficaPorto | 1X2 1 | 1.85 | 2u", catalog);
        assertThat(semSeparador.erros()).hasSize(1);
        assertThat(semSeparador.erros().get(0).codigo()).isEqualTo(ParseErrorCode.PAR_DE_EQUIPAS_INVALIDO);

        ParseResult comDois = parser.parse("28/07 Benfica - Porto - Sporting | 1X2 1 | 1.85 | 2u", catalog);
        assertThat(comDois.erros()).hasSize(1);
        assertThat(comDois.erros().get(0).codigo()).isEqualTo(ParseErrorCode.PAR_DE_EQUIPAS_AMBIGUO);
    }

    @Test
    void misturaDeTabulacoesEEspacosNaoDeslocaAsColunas() {
        String texto = TipTextSamples.TABS_E_ESPACOS;
        ParseResult resultado = parser.parse(texto, catalog);

        assertThat(resultado.erros()).hasSize(1);
        ParseError erro = resultado.erros().get(0);
        assertThat(erro.codigo()).isEqualTo(ParseErrorCode.ODD_INVALIDA);
        assertThat(erro.coluna()).isEqualTo(texto.indexOf("abc") + 1);
    }

    @Test
    void linhasVaziasSaoIgnoradasENaoDeslocamANumeracao() {
        String texto = "\n28/07 Benfica - Porto | 1X2 1 | 1.85 | 2u\n\nlinha errada\n";
        ParseResult resultado = parser.parse(texto, catalog);

        assertThat(resultado.tips()).hasSize(1);
        assertThat(resultado.tips().get(0).linha()).isEqualTo(2);
        assertThat(resultado.erros()).hasSize(1);
        assertThat(resultado.erros().get(0).linha()).isEqualTo(4);
    }

    @Test
    void terminacoesDeLinhaEBomNaoAfetamNumeracaoNemColunas() {
        String texto = "﻿28/07 Benfica - Porto | 1X2 1 | 1.85 | 2u\r\nlinha errada\r\n";
        ParseResult resultado = parser.parse(texto, catalog);

        assertThat(resultado.tips()).hasSize(1);
        assertThat(resultado.tips().get(0).linha()).isEqualTo(1);
        assertThat(resultado.erros()).hasSize(1);
        assertThat(resultado.erros().get(0).linha()).isEqualTo(2);
    }

    @Test
    void nenhumaEntradaFazOParserLancarExcecao() {
        assertThatCode(() -> parser.parse(null, catalog)).doesNotThrowAnyException();
        assertThatCode(() -> parser.parse("28/07 Benfica - Porto | 1X2 1 | 1.85 | 2u", null)).doesNotThrowAnyException();
        assertThatCode(() -> parser.parse(null, null)).doesNotThrowAnyException();
        assertThatCode(() -> parser.parse("", catalog)).doesNotThrowAnyException();
        assertThatCode(() -> parser.parse("   \n\t\n   ", catalog)).doesNotThrowAnyException();
        assertThatCode(() -> parser.parse(TipTextSamples.BLOCO_ADVERSARIAL_X, catalog)).doesNotThrowAnyException();
        assertThatCode(() -> parser.parse(TipTextSamples.BLOCO_ADVERSARIAL_PIPE, catalog)).doesNotThrowAnyException();
        assertThatCode(() -> parser.parse(TipTextSamples.BLOCO_ADVERSARIAL_HIFEN, catalog)).doesNotThrowAnyException();
        assertThatCode(() -> parser.parse(TipTextSamples.BLOCO_ADVERSARIAL_BARRA, catalog)).doesNotThrowAnyException();
        assertThatCode(() -> parser.parse("MULT | 2u\n  ///|||---xxx", catalog)).doesNotThrowAnyException();
        assertThatCode(() -> parser.parse("a".repeat(30_000), catalog)).doesNotThrowAnyException();
    }

    @Test
    void oTextoOriginalENuncaPerdidoMesmoQuandoTudoFalha() {
        ParseResult resultado = parser.parse(TipTextSamples.FORMATO_DE_TELEGRAM, catalog);

        assertThat(resultado.tips()).isEmpty();
        assertThat(resultado.rawText()).isEqualTo(TipTextSamples.FORMATO_DE_TELEGRAM);
    }

    @Test
    void oEstadoDoResultadoSegueOsTresValoresDeTipImports() {
        ParseResult tudoValido = parser.parse("28/07 Benfica - Porto | 1X2 1 | 1.85 | 2u", catalog);
        assertThat(tudoValido.estado()).isEqualTo(ImportStatus.OK);

        ParseResult misto = parser.parse("""
                28/07 Benfica - Porto | 1X2 1 | 1.85 | 2u
                linha errada
                """, catalog);
        assertThat(misto.estado()).isEqualTo(ImportStatus.PARTIAL);

        ParseResult tudoInvalido = parser.parse("linha errada", catalog);
        assertThat(tudoInvalido.estado()).isEqualTo(ImportStatus.FAILED);
    }
}
