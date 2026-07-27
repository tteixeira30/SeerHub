package pt.seerhub.football.service;

/** {@code crestUrl} é sempre um caminho local (nunca o do fornecedor externo) — §2.5 do plano de F05. */
public record TeamView(long id, String name, String shortName, String country, String normalizedName, String crestUrl) {
}
