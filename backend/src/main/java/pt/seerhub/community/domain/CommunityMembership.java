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
 * o único construtor público de F02 ser a fábrica estática {@link #deDono}.
 * F02 <b>nunca</b> fez {@code UPDATE} a uma linha de membership (nem à sua
 * própria linha {@code OWNER}, nem a nenhuma outra).
 *
 * <p><b>F03 é dona de toda a transição de estado (§2.1 do plano de F03):</b>
 * a fábrica {@link #deSubscritor} cria a linha {@code MEMBER}/{@code ACTIVE}
 * de uma subscrição nova, e {@link #cancelar()}, {@link #reativar()} e
 * {@link #renovar(Instant)} são as três transições que a
 * {@code SubscriptionService} usa. {@code deDono} fica intacta — nada é
 * removido nem renomeado. O campo {@link #version} está mapeado (bloqueio
 * otimista); a tarefa diária de expiração incrementa-o explicitamente por
 * ser um {@code UPDATE} em bloco (D-13 do plano de F03), que não passa pelo
 * bloqueio otimista do Hibernate.
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

    /**
     * A membership {@code MEMBER} criada por uma subscrição nova (F03,
     * D-7): sempre {@code ACTIVE}, sempre com {@code expiresAt} a 30 dias
     * (calculado por {@code MembershipAccessRules.proximaExpiracao}, nunca
     * aqui — esta fábrica só fixa o papel e o estado).
     */
    public static CommunityMembership deSubscritor(Community community, User user, Instant expiresAt, Clock clock) {
        return new CommunityMembership(community, user, MembershipRole.MEMBER, MembershipStatus.ACTIVE, expiresAt,
                clock);
    }

    /** Cancela a subscrição (D-9): só muda o estado, {@link #expiresAt} fica intacta — o acesso dura até lá. */
    public void cancelar() {
        this.status = MembershipStatus.CANCELLED;
    }

    /** Repõe {@code ACTIVE} sem tocar em {@link #expiresAt} (D-7): reativar não oferece 30 dias grátis. */
    public void reativar() {
        this.status = MembershipStatus.ACTIVE;
    }

    /** Renova: {@code ACTIVE} com uma nova data de expiração (D-7, resubscrição depois de expirar). */
    public void renovar(Instant novoExpiresAt) {
        this.status = MembershipStatus.ACTIVE;
        this.expiresAt = novoExpiresAt;
    }

    /**
     * Promove a {@code MODERATOR} (R4, F04, D-3): só o papel muda —
     * {@link #status} e {@link #expiresAt} ficam intactos, o que faz um
     * moderador nomeado sobre uma linha já vencida continuar sem acesso
     * até renovar (critério 1d).
     */
    public void promoverAModerador() {
        this.role = MembershipRole.MODERATOR;
    }

    /** Despromove para {@code MEMBER} (R4, F04, D-3): devolve o utilizador ao estado real da subscrição subjacente. */
    public void despromoverParaMembro() {
        this.role = MembershipRole.MEMBER;
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
