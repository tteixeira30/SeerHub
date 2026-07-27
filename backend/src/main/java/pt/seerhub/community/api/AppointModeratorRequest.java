package pt.seerhub.community.api;

import jakarta.validation.constraints.NotNull;

/** Corpo de {@code POST /api/communities/{slug}/moderators} (R4, critério 1). */
public record AppointModeratorRequest(@NotNull Long userId) {
}
