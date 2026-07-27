package pt.seerhub.football.provider;

/** Liga tal como o fornecedor a devolve, embutida na resposta de jogos (§2.4-D do plano de F05). */
public record ProviderLeague(long providerId, String name, String country, String logoUrl, int season) {
}
