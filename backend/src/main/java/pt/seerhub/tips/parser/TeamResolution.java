package pt.seerhub.tips.parser;

import java.util.List;

/**
 * O estado de resolução de uma equipa contra o catálogo de jogos. F06a produz sempre {@link
 * #porResolver()} — nunca normaliza um nome, nunca consulta o catálogo, nunca calcula
 * semelhança e nunca preenche {@code fixtureId} (§2.5 do plano). F06b decora o {@code
 * ParseResult} de F06a substituindo esta forma por uma resolvida, através de {@link
 * ParsedSelection#comResolucao(TeamResolution)} — nunca edita {@code GrammarTipTextParser}.
 */
public record TeamResolution(Long fixtureId, ResolutionStatus estado, List<FixtureCandidate> candidatos) {

    public TeamResolution {
        candidatos = List.copyOf(candidatos);
    }

    public static TeamResolution porResolver() {
        return new TeamResolution(null, ResolutionStatus.POR_RESOLVER, List.of());
    }
}
