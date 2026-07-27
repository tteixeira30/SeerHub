package pt.seerhub.tips.parser;

/**
 * Um candidato a jogo para uma equipa ambígua, mínimo de propósito: F07 já tem de chamar
 * {@code FootballCatalogService.jogo(fixtureId)} para obter emblemas, liga e hora de início,
 * portanto duplicar esses dados aqui seria redundância a manter sincronizada (§9.2, risco 5,
 * do plano de F06a). F06a nunca produz instâncias disto — é F06b quem preenche {@link
 * TeamResolution#candidatos()}.
 */
public record FixtureCandidate(long fixtureId, double semelhanca) {
}
