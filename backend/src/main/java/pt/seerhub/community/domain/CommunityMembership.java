package pt.seerhub.community.domain;

import java.time.Clock;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

import pt.seerhub.user.domain.User;

/**
 * Entidade mapeada contra a tabela {@code community_memberships} do
 * baseline {@code V2} (F00).
 *
 * <p><b>Fronteira F02/F03 (§2.1 do plano de F02) — ler antes de tocar nesta
 * classe:</b> F02 é dono desta entidade e do repositório (uma tabela → uma
 * entidade, quem escreve primeiro mapeia-a), mas só insere <b>exatamente
 * uma linha por comunidade</b>, no momento da criação, com
 * {@code role=OWNER}, {@code status=ACTIVE}, {@code expiresAt=null} — daí
 * o único construtor público ser a fábrica estática {@link #deDono}, que
 * fixa esses valores para F02 não conseguir criar por acidente uma linha
 * que pertence a F03. F02 <b>nunca</b> faz {@code UPDATE} a uma linha de
 * membership (nem à sua própria linha {@code OWNER}, nem a nenhuma outra):
 * toda a criação de linhas {@code MEMBER}/{@code MODERATOR}, toda a
 * transição de {@code status} (ACTIVE→CANCELLED/EXPIRED) e todo o uso de
 * {@code expiresAt} fora do {@code null} do dono pertencem a F03/F04. O
 * campo {@link #version} existe e está mapeado (bloqueio otimista de
 * F03/F08), mas nunca é escrito por F02 depois do {@code INSERT} inicial.
 */
@Entity
@Table(name = "community_memberships")
public class CommunityMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipStatus status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Version
    @Column(nullable = false)
    private Long version;

    /** Só para preencher {@link #joinedAt} em {@link #prePersist()}; nunca persistido. */
    @Transient
    private Clock clock;

    protected CommunityMembership() {
        // exigido pelo JPA
    }

    private CommunityMembership(Community community, User user, MembershipRole role, MembershipStatus status,
            Instant expiresAt, Clock clock) {
        this.community = community;
        this.user = user;
        this.role = role;
        this.status = status;
        this.expiresAt = expiresAt;
        this.clock = clock;
    }

    /**
     * Único construtor público: a membership {@code OWNER} criada na mesma
     * transação da comunidade. Ativa, sem data de expiração — o dono nunca
     * expira (§2.1 do plano de F02).
     */
    public static CommunityMembership deDono(Community community, User owner, Clock clock) {
        return new CommunityMembership(community, owner, MembershipRole.OWNER, MembershipStatus.ACTIVE, null, clock);
    }

    @PrePersist
    void prePersist() {
        Clock efetivo = clock != null ? clock : Clock.systemUTC();
        this.joinedAt = Instant.now(efetivo);
    }

    public Long getId() {
        return id;
    }

    public Community getCommunity() {
        return community;
    }

    public User getUser() {
        return user;
    }

    public MembershipRole getRole() {
        return role;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Long getVersion() {
        return version;
    }
}
