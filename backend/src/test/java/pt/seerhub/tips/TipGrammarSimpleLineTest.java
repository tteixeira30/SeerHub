package pt.seerhub.tips;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import pt.seerhub.support.TipTextSamples;
import pt.seerhub.tips.domain.Market;
import pt.seerhub.tips.parser.GrammarTipTextParser;
import pt.seerhub.tips.parser.ParseResult;
import pt.seerhub.tips.parser.ParsedSelection;
import pt.seerhub.tips.parser.ParsedTip;
import pt.seerhub.tips.parser.TeamResolution;
import pt.seerhub.tips.parser.TipCatalog;
import pt.seerhub.tips.parser.TipKind;

import static org.assertj.core.api.Assertions.assertThat;

/** R6-1 (1a–1i): a gramática aceita data, par de equipas, mercado, seleção, odd e stake. */
class TipGrammarSimpleLineTest {

    private final GrammarTipTextParser parser = new GrammarTipTextParser();

    @Test
    void linhaCompletaProduzUmaTipSimplesComTodosOsCampos() {
        TipCatalog catalog = TipTextSamples.catalogoDeTeste(LocalDate.of(2026, 7, 27));
        ParseResult resultado = parser.parse("28/07 Benfica - Porto | 1X2 1 | 1.85 | 2u", catalog);

        assertThat(resultado.temErros()).isFalse();
        assertThat(resultado.tips()).hasSize(1);
        ParsedTip tip = resultado.tips().get(0);
        assertThat(tip.tipo()).isEqualTo(TipKind.SIMPLES);
        assertThat(tip.stakeUnidades()).isEqualByComparingTo("2.00");
        assertThat(tip.oddTotal()).isEqualByComparingTo("1.850");
        assertThat(tip.selecoes()).hasSize(1);

        ParsedSelection selecao = tip.selecoes().get(0);
        assertThat(selecao.data()).isEqualTo(LocalDate.of(2026, 7, 28));
        assertThat(selecao.textoDaData()).isEqualTo("28/07");
        assertThat(selecao.casa().texto()).isEqualTo("Benfica");
        assertThat(selecao.fora().texto()).isEqualTo("Porto");
        assertThat(selecao.mercado()).isEqualTo(Market.MATCH_RESULT);
        assertThat(selecao.selecao()).isEqualTo("HOME");
        assertThat(selecao.odd()).isEqualByComparingTo("1.850");
        assertThat(selecao.resolucao()).isEqualTo(TeamResolution.porResolver());
    }

    @Test
    void dataAusenteEValidaEDeixaADataPorResolver() {
        TipCatalog catalog = TipTextSamples.catalogoDeTeste(LocalDate.of(2026, 7, 27));
        ParseResult resultado = parser.parse("Benfica - Porto | 1X2 1 | 1.85 | 2u", catalog);

        assertThat(resultado.temErros()).isFalse();
        assertThat(resultado.tips()).hasSize(1);
        ParsedSelection selecao = resultado.tips().get(0).selecoes().get(0);
        assertThat(selecao.data()).isNull();
        assertThat(selecao.textoDaData()).isNull();
        assertThat(selecao.casa().texto()).isEqualTo("Benfica");
        assertThat(selecao.fora().texto()).isEqualTo("Porto");
    }

    @Test
    void aceitaTodosOsSeparadoresDeParDeEquipas() {
        TipCatalog catalog = TipTextSamples.catalogoDeTeste(LocalDate.of(2026, 7, 27));
        List<String> separadores = List.of("-", "–", "x", "X", "vs", "v");

        for (String separador : separadores) {
            String linha = "28/07 Benfica " + separador + " Porto | 1X2 1 | 1.85 | 2u";
            ParseResult resultado = parser.parse(linha, catalog);

            assertThat(resultado.temErros()).as("separador '%s'", separador).isFalse();
            assertThat(resultado.tips()).as("separador '%s'", separador).hasSize(1);
            ParsedSelection selecao = resultado.tips().get(0).selecoes().get(0);
            assertThat(selecao.casa().texto()).as("separador '%s'", separador).isEqualTo("Benfica");
            assertThat(selecao.fora().texto()).as("separador '%s'", separador).isEqualTo("Porto");
        }
    }

    @Test
    void hifenDentroDoNomeDaEquipaNaoESeparador() {
        TipCatalog catalog = TipTextSamples.catalogoDeTeste(LocalDate.of(2026, 7, 27));
        ParseResult resultado = parser.parse(
                "28/07 Paris Saint-Germain - Olympique Marseille | 1X2 1 | 1.85 | 2u", catalog);

        assertThat(resultado.temErros()).isFalse();
        ParsedSelection selecao = resultado.tips().get(0).selecoes().get(0);
        assertThat(selecao.casa().texto()).isEqualTo("Paris Saint-Germain");
        assertThat(selecao.fora().texto()).isEqualTo("Olympique Marseille");
    }

    @Test
    void oddAceitaPontoEVirgulaComoSeparadorDecimal() {
        TipCatalog catalog = TipTextSamples.catalogoDeTeste(LocalDate.of(2026, 7, 27));
        ParseResult comPonto = parser.parse("28/07 Benfica - Porto | 1X2 1 | 1.85 | 2u", catalog);
        ParseResult comVirgula = parser.parse("28/07 Benfica - Porto | 1X2 1 | 1,85 | 2u", catalog);

        assertThat(comPonto.tips().get(0).selecoes().get(0).odd())
                .isEqualByComparingTo(comVirgula.tips().get(0).selecoes().get(0).odd());
        assertThat(comVirgula.tips().get(0).selecoes().get(0).odd()).isEqualByComparingTo("1.850");
    }

    @Test
    void stakeAceitaAsVariantesDeEscritaDeUnidades() {
        TipCatalog catalog = TipTextSamples.catalogoDeTeste(LocalDate.of(2026, 7, 27));
        record Caso(String stakeEscrito, String stakeEsperado) {
        }
        List<Caso> casos = List.of(
                new Caso("2u", "2.00"),
                new Caso("1.5u", "1.50"),
                new Caso("1,5u", "1.50"),
                new Caso("2 u", "2.00"),
                new Caso("2U", "2.00"));

        for (Caso caso : casos) {
            String linha = "28/07 Benfica - Porto | 1X2 1 | 1.85 | " + caso.stakeEscrito();
            ParseResult resultado = parser.parse(linha, catalog);
            assertThat(resultado.temErros()).as("stake '%s'", caso.stakeEscrito()).isFalse();
            assertThat(resultado.tips().get(0).stakeUnidades())
                    .as("stake '%s'", caso.stakeEscrito())
                    .isEqualByComparingTo(caso.stakeEsperado());
        }
    }

    @Test
    void oAnoEInferidoPelaDataDeReferenciaDoCatalogo() {
        TipCatalog catalog = TipTextSamples.catalogoDeTeste(LocalDate.of(2026, 12, 20));
        // "01/01" está mais perto de 2027-01-01 (12 dias à frente) do que de 2026-01-01 (354 dias atrás).
        ParseResult resultado = parser.parse("01/01 Benfica - Porto | 1X2 1 | 1.85 | 2u", catalog);

        assertThat(resultado.temErros()).isFalse();
        assertThat(resultado.tips().get(0).selecoes().get(0).data()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void vinteENoveDeFevereiroEscolheOAnoBissextoCandidato() {
        TipCatalog catalogComBissexto = TipTextSamples.catalogoDeTeste(LocalDate.of(2025, 3, 1));
        ParseResult comCandidato = parser.parse("29/02 Benfica - Porto | 1X2 1 | 1.85 | 2u", catalogComBissexto);
        assertThat(comCandidato.temErros()).isFalse();
        assertThat(comCandidato.tips().get(0).selecoes().get(0).data()).isEqualTo(LocalDate.of(2024, 2, 29));

        TipCatalog catalogSemBissexto = TipTextSamples.catalogoDeTeste(LocalDate.of(2026, 7, 27));
        ParseResult semCandidato = parser.parse("29/02 Benfica - Porto | 1X2 1 | 1.85 | 2u", catalogSemBissexto);
        assertThat(semCandidato.tips()).isEmpty();
        assertThat(semCandidato.erros()).hasSize(1);
        assertThat(semCandidato.erros().get(0).codigo()).isEqualTo(pt.seerhub.tips.parser.ParseErrorCode.DATA_INVALIDA);
    }

    @Test
    void oParDeEquipasVemEmTextoCruComColunaEPorResolver() {
        TipCatalog catalog = TipTextSamples.catalogoDeTeste(LocalDate.of(2026, 7, 27));
        String linha = "28/07 Benfica - Porto | 1X2 1 | 1.85 | 2u";
        ParseResult resultado = parser.parse(linha, catalog);

        ParsedSelection selecao = resultado.tips().get(0).selecoes().get(0);
        assertThat(selecao.casa().linha()).isEqualTo(1);
        assertThat(selecao.casa().coluna()).isEqualTo(linha.indexOf("Benfica") + 1);
        assertThat(selecao.fora().linha()).isEqualTo(1);
        assertThat(selecao.fora().coluna()).isEqualTo(linha.indexOf("Porto") + 1);
        assertThat(selecao.resolucao()).isEqualTo(TeamResolution.porResolver());
        assertThat(selecao.resolucao().fixtureId()).isNull();
    }
}
