package pt.seerhub.tips;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import pt.seerhub.support.TipTextSamples;
import pt.seerhub.tips.parser.GrammarTipTextParser;
import pt.seerhub.tips.parser.ParseResult;
import pt.seerhub.tips.parser.ParsedSelection;
import pt.seerhub.tips.parser.ResolutionStatus;
import pt.seerhub.tips.parser.TipCatalog;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R6-10 (metade da gramática, 10a–10c): o corpus fixo de 5 tips simples + 1 acumulador de 3
 * jogos produz 6 tips com 8 seleções, sem qualquer chamada de rede. A segunda metade do
 * critério ("...todas ligadas a jogos reais") é de F06b — §3.6 do plano.
 */
class TipFixedCorpusTest {

    private final GrammarTipTextParser parser = new GrammarTipTextParser();
    private final TipCatalog catalog = TipTextSamples.catalogoDeTeste(LocalDate.of(2026, 7, 27));

    @Test
    void cincoSimplesMaisUmAcumuladorDeTresProduzemSeisTipsComOitoSelecoes() {
        ParseResult resultado = parser.parse(TipTextSamples.CINCO_SIMPLES_E_UM_ACUMULADOR, catalog);

        assertThat(resultado.temErros()).isFalse();
        assertThat(resultado.tips()).hasSize(6);
        assertThat(resultado.totalSelecoes()).isEqualTo(8);
    }

    @Test
    void noCorpusFixoTodasAsSelecoesFicamPorResolver() {
        ParseResult resultado = parser.parse(TipTextSamples.CINCO_SIMPLES_E_UM_ACUMULADOR, catalog);

        List<ParsedSelection> todasAsSelecoes = resultado.tips().stream()
                .flatMap(t -> t.selecoes().stream())
                .toList();
        assertThat(todasAsSelecoes).hasSize(8);
        assertThat(todasAsSelecoes).allSatisfy(s -> {
            assertThat(s.resolucao().estado()).isEqualTo(ResolutionStatus.POR_RESOLVER);
            assertThat(s.resolucao().fixtureId()).isNull();
            assertThat(s.resolucao().candidatos()).isEmpty();
        });
    }

    @Test
    void osTextosCrusDasEquipasDoCorpusSaoOsDoExemploDaSpec() {
        ParseResult resultado = parser.parse(TipTextSamples.CINCO_SIMPLES_E_UM_ACUMULADOR, catalog);

        List<String> equipas = new ArrayList<>();
        for (var tip : resultado.tips()) {
            for (var selecao : tip.selecoes()) {
                equipas.add(selecao.casa().texto());
                equipas.add(selecao.fora().texto());
            }
        }

        assertThat(equipas).containsExactlyElementsOf(TipTextSamples.EQUIPAS_DO_CORPUS_FIXO);
    }
}
