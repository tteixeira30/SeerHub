package pt.seerhub.tips;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import pt.seerhub.support.TipTextSamples;
import pt.seerhub.tips.parser.GrammarTipTextParser;
import pt.seerhub.tips.parser.ParseResult;
import pt.seerhub.tips.parser.TipCatalog;
import pt.seerhub.tips.parser.TipFormat;

import static org.assertj.core.api.Assertions.assertThat;

/** §10 da spec: o caso do tipster colar o formato do Telegram dele em vez do formato do SeerHub. */
class TipGrammarUnknownFormatTest {

    private final GrammarTipTextParser parser = new GrammarTipTextParser();
    private final TipCatalog catalog = TipTextSamples.catalogoDeTeste(LocalDate.of(2026, 7, 27));

    @Test
    void quandoNenhumaLinhaValidaOResultadoPedeOExemploDoFormato() {
        ParseResult resultado = parser.parse("linha errada\noutra completamente errada\n", catalog);

        assertThat(resultado.tips()).isEmpty();
        assertThat(resultado.formatoNaoReconhecido()).isTrue();
        assertThat(TipFormat.EXEMPLO).isNotBlank();
    }

    @Test
    void formatoDeTelegramDisparaABandeiraEMantemOsErrosLinhaALinha() {
        ParseResult resultado = parser.parse(TipTextSamples.FORMATO_DE_TELEGRAM, catalog);

        assertThat(resultado.tips()).isEmpty();
        assertThat(resultado.formatoNaoReconhecido()).isTrue();
        assertThat(resultado.erros()).hasSize(3);
        assertThat(resultado.erros().get(0).linha()).isEqualTo(1);
        assertThat(resultado.erros().get(1).linha()).isEqualTo(2);
        assertThat(resultado.erros().get(2).linha()).isEqualTo(3);
    }

    @Test
    void loteParcialmenteValidoNuncaDisparaABandeira() {
        String texto = """
                28/07 Benfica - Porto | 1X2 1 | 1.85 | 2u
                linha errada 1
                linha errada 2
                linha errada 3
                """;
        ParseResult resultado = parser.parse(texto, catalog);

        assertThat(resultado.tips()).hasSize(1);
        assertThat(resultado.erros()).hasSize(3);
        assertThat(resultado.formatoNaoReconhecido()).isFalse();
    }

    @Test
    void textoVazioNaoEFormatoDesconhecidoEENulo() {
        ParseResult vazio = parser.parse("", catalog);
        assertThat(vazio.tips()).isEmpty();
        assertThat(vazio.erros()).isEmpty();
        assertThat(vazio.formatoNaoReconhecido()).isFalse();

        ParseResult soBranco = parser.parse("   \n\t\n   \n", catalog);
        assertThat(soBranco.tips()).isEmpty();
        assertThat(soBranco.erros()).isEmpty();
        assertThat(soBranco.formatoNaoReconhecido()).isFalse();
    }
}
