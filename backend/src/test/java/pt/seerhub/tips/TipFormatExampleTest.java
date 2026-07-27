package pt.seerhub.tips;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import pt.seerhub.support.TipTextSamples;
import pt.seerhub.tips.parser.GrammarTipTextParser;
import pt.seerhub.tips.parser.ParseResult;
import pt.seerhub.tips.parser.TipCatalog;
import pt.seerhub.tips.parser.TipFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E5: o exemplo mostrado ao utilizador (o bloco literal da spec, {@link TipFormat#EXEMPLO}) é
 * ele próprio um bloco válido.
 *
 * <p><b>Nota sobre os números:</b> o bloco literal da spec (secção "Formato (v1)") tem 3 tips
 * simples mais um {@code MULT} de 2 seleções — 4 tips, 5 seleções, não "6 tips, 8 seleções"
 * (esse número é o do critério R6-10, um corpus diferente e maior, coberto por {@code
 * TipFixedCorpusTest}). Ver "Desvios face ao plano" no handoff.
 */
class TipFormatExampleTest {

    private final GrammarTipTextParser parser = new GrammarTipTextParser();
    private final TipCatalog catalog = TipTextSamples.catalogoDeTeste(LocalDate.of(2026, 7, 27));

    @Test
    void oExemploMostradoAoUtilizadorEEleProprioUmBlocoValido() {
        ParseResult resultado = parser.parse(TipFormat.EXEMPLO, catalog);

        assertThat(resultado.temErros()).isFalse();
        assertThat(resultado.tips()).hasSize(4);
        assertThat(resultado.totalSelecoes()).isEqualTo(5);
        assertThat(resultado.formatoNaoReconhecido()).isFalse();
    }
}
