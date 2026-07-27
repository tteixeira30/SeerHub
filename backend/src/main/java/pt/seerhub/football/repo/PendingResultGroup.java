package pt.seerhub.football.repo;

import java.time.LocalDate;

/**
 * Um grupo liga+dia com pelo menos uma seleção pendente por resolver
 * (§2.4-E do plano de F05). {@code jogos} é só informativo (contagem de
 * jogos distintos no grupo); a sincronização faz sempre uma única chamada
 * por grupo, independentemente de quantos jogos ele tiver.
 */
public record PendingResultGroup(long ligaId, long ligaProviderId, int epoca, LocalDate dia, int jogos) {
}
