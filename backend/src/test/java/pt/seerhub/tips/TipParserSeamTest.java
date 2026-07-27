package pt.seerhub.tips;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import pt.seerhub.football.domain.FixtureStatus;
import pt.seerhub.football.service.FixtureView;
import pt.seerhub.football.service.TeamView;
import pt.seerhub.support.TipTextSamples;
import pt.seerhub.tips.parser.FixtureCandidate;
import pt.seerhub.tips.parser.GrammarTipTextParser;
import pt.seerhub.tips.parser.ParseResult;
import pt.seerhub.tips.parser.ParsedSelection;
import pt.seerhub.tips.parser.ResolutionStatus;
import pt.seerhub.tips.parser.SimpleTipCatalog;
import pt.seerhub.tips.parser.TeamResolution;
import pt.seerhub.tips.parser.TipCatalog;
import pt.seerhub.tips.parser.TipTextParser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** R6-13: existe TipTextParser(rawText, catalog) → ParseResult, e a costura para F06b já está provada. */
class TipParserSeamTest {

    private final GrammarTipTextParser parser = new GrammarTipTextParser();
    private final TipCatalog catalog = TipTextSamples.catalogoDeTeste(LocalDate.of(2026, 7, 27));
    private static final String LINHA_VALIDA = "28/07 Benfica - Porto | 1X2 1 | 1.85 | 2u";

    @Test
    void aInterfaceTipTextParserTemAAssinaturaExigidaPelaSpec() {
        assertThat(TipTextParser.class.isInterface()).isTrue();
        Method[] metodos = TipTextParser.class.getDeclaredMethods();
        assertThat(metodos).hasSize(1);
        Method metodo = metodos[0];
        assertThat(metodo.getName()).isEqualTo("parse");
        assertThat(metodo.getReturnType()).isEqualTo(ParseResult.class);
        assertThat(metodo.getParameterTypes()).containsExactly(String.class, pt.seerhub.tips.parser.TipCatalog.class);
    }

    @Test
    void umDecoradorEnriqueceOResultadoSemTocarNaGramatica() {
        TipTextParser decorador = new TipTextParser() {
            @Override
            public ParseResult parse(String rawText, TipCatalog catalog) {
                ParseResult base = parser.parse(rawText, catalog);
                List<pt.seerhub.tips.parser.ParsedTip> tipsEnriquecidas = base.tips().stream()
                        .map(tip -> new pt.seerhub.tips.parser.ParsedTip(tip.linha(), tip.tipo(), tip.stakeUnidades(),
                                tip.oddTotal(),
                                tip.selecoes().stream()
                                        .map(s -> s.comResolucao(new TeamResolution(999L, ResolutionStatus.RESOLVIDA, List.of())))
                                        .toList(),
                                tip.textoOriginal()))
                        .toList();
                return new ParseResult(base.rawText(), base.parserVersion(), tipsEnriquecidas, base.erros(),
                        base.formatoNaoReconhecido());
            }
        };

        ParseResult resultado = decorador.parse(LINHA_VALIDA, catalog);

        assertThat(resultado.tips()).hasSize(1);
        assertThat(resultado.tips().get(0).selecoes().get(0).resolucao().fixtureId()).isEqualTo(999L);
        assertThat(resultado.tips().get(0).selecoes().get(0).resolucao().estado()).isEqualTo(ResolutionStatus.RESOLVIDA);
    }

    @Test
    void osMetodosDeCopiaPreservamTodosOsOutrosCampos() {
        ParseResult resultado = parser.parse(LINHA_VALIDA, catalog);
        ParsedSelection original = resultado.tips().get(0).selecoes().get(0);

        TeamResolution novaResolucao = new TeamResolution(42L, ResolutionStatus.RESOLVIDA,
                List.of(new FixtureCandidate(42L, 0.9)));
        ParsedSelection comResolucao = original.comResolucao(novaResolucao);

        assertThat(comResolucao.resolucao()).isEqualTo(novaResolucao);
        assertThat(comResolucao.linha()).isEqualTo(original.linha());
        assertThat(comResolucao.data()).isEqualTo(original.data());
        assertThat(comResolucao.textoDaData()).isEqualTo(original.textoDaData());
        assertThat(comResolucao.casa()).isEqualTo(original.casa());
        assertThat(comResolucao.fora()).isEqualTo(original.fora());
        assertThat(comResolucao.mercado()).isEqualTo(original.mercado());
        assertThat(comResolucao.selecao()).isEqualTo(original.selecao());
        assertThat(comResolucao.linhaMercado()).isEqualTo(original.linhaMercado());
        assertThat(comResolucao.odd()).isEqualTo(original.odd());

        LocalDate novaData = LocalDate.of(2026, 8, 1);
        ParsedSelection comData = original.comData(novaData);
        assertThat(comData.data()).isEqualTo(novaData);
        assertThat(comData.textoDaData()).isEqualTo(original.textoDaData());
        assertThat(comData.casa()).isEqualTo(original.casa());
        assertThat(comData.fora()).isEqualTo(original.fora());
        assertThat(comData.mercado()).isEqualTo(original.mercado());
        assertThat(comData.selecao()).isEqualTo(original.selecao());
        assertThat(comData.linhaMercado()).isEqualTo(original.linhaMercado());
        assertThat(comData.odd()).isEqualTo(original.odd());
        assertThat(comData.resolucao()).isEqualTo(original.resolucao());
    }

    @Test
    void oResultadoDaGramaticaNaoDependeDosJogosDoCatalogo() {
        TipCatalog catalogoVazio = new SimpleTipCatalog(catalog.fuso(), catalog.hoje(), List.of());

        TeamView casa = new TeamView(1L, "SL Benfica", "BEN", "Portugal", "benfica", "/crests/teams/1");
        TeamView fora = new TeamView(2L, "FC Porto", "POR", "Portugal", "porto", "/crests/teams/2");
        FixtureView jogo = new FixtureView(100L, 94L, "Primeira Liga", "/crests/leagues/94", casa, fora,
                Instant.parse("2026-07-28T19:45:00Z"), FixtureStatus.SCHEDULED, null, null);
        TipCatalog catalogoCheio = new SimpleTipCatalog(catalog.fuso(), catalog.hoje(), List.of(jogo));

        ParseResult comVazio = parser.parse(LINHA_VALIDA, catalogoVazio);
        ParseResult comCheio = parser.parse(LINHA_VALIDA, catalogoCheio);

        assertThat(comVazio).isEqualTo(comCheio);
    }

    @Test
    void entradasNulasDevolvemResultadoVazioEmVezDeLancar() {
        assertThatCode(() -> parser.parse(null, catalog)).doesNotThrowAnyException();
        ParseResult resultado = parser.parse(null, catalog);
        assertThat(resultado.tips()).isEmpty();
        assertThat(resultado.erros()).isEmpty();
        assertThat(resultado.rawText()).isEqualTo("");

        assertThatCode(() -> parser.parse(LINHA_VALIDA, null)).doesNotThrowAnyException();
        ParseResult comCatalogoNulo = parser.parse(LINHA_VALIDA, null);
        assertThat(comCatalogoNulo.tips()).hasSize(1);

        assertThatCode(() -> parser.parse(null, null)).doesNotThrowAnyException();
    }
}
