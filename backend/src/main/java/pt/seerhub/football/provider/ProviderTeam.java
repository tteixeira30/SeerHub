package pt.seerhub.football.provider;

/**
 * Equipa tal como o fornecedor a devolve. {@code shortName}/{@code country}
 * podem vir {@code null} quando o payload é o de {@code /fixtures} (que não
 * traz o código de 3 letras nem o país da equipa) — resolvidos pelo
 * backfill limitado (5c do plano de F05).
 */
public record ProviderTeam(long providerId, String name, String shortName, String country, String logoUrl) {
}
