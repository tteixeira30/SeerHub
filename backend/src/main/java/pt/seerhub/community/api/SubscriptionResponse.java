package pt.seerhub.community.api;

import java.time.Instant;

import pt.seerhub.community.domain.Community;
import pt.seerhub.community.domain.CommunityMembership;
import pt.seerhub.community.domain.MembershipRole;
import pt.seerhub.community.domain.MembershipStatus;

/** Representação pública de uma subscrição (R3, critérios 1, 2 e 4). */
public record SubscriptionResponse(
        Long communityId,
        String slug,
        String communityName,
        int priceMonthlyCents,
        String currency,
        MembershipRole role,
        MembershipStatus status,
        Instant joinedAt,
        Instant expiresAt,
        boolean active) {

    /** {@code active}: a porta ({@code MembershipAccessRules.concedeAcessoPremium}) diz sim, agora. */
    public static SubscriptionResponse de(CommunityMembership membership, boolean active) {
        Community community = membership.getCommunity();
        return new SubscriptionResponse(
                community.getId(),
                community.getSlug(),
                community.getName(),
                community.getPriceMonthlyCents(),
                Community.MOEDA,
                membership.getRole(),
                membership.getStatus(),
                membership.getJoinedAt(),
                membership.getExpiresAt(),
                active);
    }
}
