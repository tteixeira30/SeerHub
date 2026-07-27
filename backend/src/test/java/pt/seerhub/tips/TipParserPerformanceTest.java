package pt.seerhub.tips;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import pt.seerhub.support.TipTextSamples;
import pt.seerhub.tips.parser.GrammarTipTextParser;
import pt.seerhub.tips.parser.ParseErrorCode;
import pt.seerhub.tips.parser.ParseResult;
import pt.seerhub.tips.parser.TipCatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * R6-12 (metade da gramática): p95 do parse de um bloco de 2000 carateres fica abaixo de
 * 20 ms — o orçamento da gramática, uma fração dos 200 ms de ponta a ponta que R6-12 exige
 * (a outra fração, a correspondência de equipas, é de F06b — §3.6 do plano).
 *
 * <p><b>Regra dura do plano:</b> se 12a falhar, o problema é algorítmico — corrigir o
 * algoritmo, nunca subir o limiar do teste.
 */
class TipParserPerformanceTest {

    private final GrammarTipTextParser parser = new GrammarTipTextParser();
    private final TipCatalog catalog = TipTextSamples.catalogoDeTeste(LocalDate.of(2026, 7, 27));

    @Test
    void p95DoParseDeDoisMilCaracteresFicaMuitoAbaixoDoOrcamento() {
        String bloco = TipTextSamples.BLOCO_DE_2000_CARACTERES;

        // Aquecimento: deixa a JIT compilar o caminho quente antes de medir.
        for (int i = 0; i < 200; i++) {
            parser.parse(bloco, catalog);
        }

        int amostras = 300;
        List<Long> duracoesNs = new ArrayList<>(amostras);
        ParseResult ultimoResultado = null;
        for (int i = 0; i < amostras; i++) {
            long inicio = System.nanoTime();
            ultimoResultado = parser.parse(bloco, catalog);
            duracoesNs.add(System.nanoTime() - inicio);
        }

        // O resultado está certo: o bloco é todo composto pela mesma linha válida repetida.
        assertThat(ultimoResultado.temErros()).isFalse();
        assertThat(ultimoResultado.tips()).isNotEmpty();

        Collections.sort(duracoesNs);
        int indiceP95 = (int) Math.ceil(0.95 * amostras) - 1;
        long p95Ns = duracoesNs.get(indiceP95);
        double p95Ms = p95Ns / 1_000_000.0;

        // Orçamento: 20 ms. Margem larga de propósito (§9.2, risco 3, do plano) — a ordem de
        // grandeza esperada é de dezenas/centenas de microssegundos em JVM aquecida.
        assertThat(p95Ms).isLessThan(20.0);
    }

    @Test
    void entradasAdversariaisNaoProvocamRetrocessoCatastrofico() {
        List<String> entradas = List.of(
                TipTextSamples.BLOCO_ADVERSARIAL_X,
                TipTextSamples.BLOCO_ADVERSARIAL_PIPE,
                TipTextSamples.BLOCO_ADVERSARIAL_HIFEN,
                TipTextSamples.BLOCO_ADVERSARIAL_ESPACOS,
                TipTextSamples.BLOCO_ADVERSARIAL_BARRA);

        for (String entrada : entradas) {
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> parser.parse(entrada, catalog));
        }
    }

    @Test
    void blocoAcimaDeDoisMilCaracteresContinuaAParsearNormalmente() {
        String blocoMaior = TipTextSamples.BLOCO_DE_2000_CARACTERES + TipTextSamples.BLOCO_DE_2000_CARACTERES;
        assertThat(blocoMaior.length()).isGreaterThan(2000);

        ParseResult resultado = parser.parse(blocoMaior, catalog);

        assertThat(resultado.temErros()).isFalse();
        assertThat(resultado.tips()).isNotEmpty();
        assertThat(resultado.erros()).noneMatch(e -> e.codigo() == ParseErrorCode.BLOCO_DEMASIADO_GRANDE);
    }

    @Test
    void blocoAcimaDoLimiteDuroDevolveUmSoErroSemVarrer() {
        String blocoEnorme = "x".repeat(25_000);

        ParseResult resultado = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> parser.parse(blocoEnorme, catalog));

        assertThat(resultado.tips()).isEmpty();
        assertThat(resultado.erros()).hasSize(1);
        assertThat(resultado.erros().get(0).codigo()).isEqualTo(ParseErrorCode.BLOCO_DEMASIADO_GRANDE);
    }
}
