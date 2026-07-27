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

import pt.seerhub.user.domain.User;

/**
 * Entidade mapeada contra a tabela {@code communities} do baseline
 * {@code V2} (F00).
 *
 * <p><b>D-2 do plano de F02:</b> a coluna {@code currency} é
 * {@code CHAR(3)}; o Hibernate com {@code ddl-auto: validate} recusa
 * mapear um campo {@code String} contra {@code bpchar} (o mesmo problema
 * que F01 já teve com {@code refresh_tokens.token_hash}). Como a v1 é
 * EUR-only por pressuposto de §11 da spec, esta entidade
 * <b>não declara o campo</b>: o {@code INSERT} omite a coluna e o Postgres
 * aplica o {@code DEFAULT 'EUR'}. A resposta pública expõe {@code currency}
 * a partir da constante {@link #MOEDA}.
 *
 * <p><b>D-3:</b> sem {@code @Version} — a tabela não tem coluna
 * {@code version} e não há concorrência de escrita em F02 (só o dono
 * edita).
 *
 * <p><b>D-16:</b> sem nenhum {@code @OneToMany} para
 * {@link CommunityMembership} — nenhuma coleção, nenhuma cascata. É o que
 * garante estruturalmente que gravar uma comunidade nunca escreve numa
 * linha de {@code community_memberships}.
 */
@Entity
@Table(name = "communities")
public class Community {

    /** V1 é EUR-only (§11 da spec); ver nota da classe sobre D-2. */
    public static final String MOEDA = "EUR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(length = 2000)
    private String description;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "banner_url", length = 500)
    private String bannerUrl;

    @Column(name = "price_monthly_cents", nullable = false)
    private int priceMonthlyCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommunityStatus status = CommunityStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Só para preencher {@link #createdAt} em {@link #prePersist()}; nunca persistido. */
    @Transient
    private Clock clock;

    protected Community() {
        // exigido pelo JPA
    }

    public Community(User owner, String name, String slug, Clock clock) {
        this.owner = owner;
        this.name = name;
        this.slug = slug;
        this.clock = clock;
    }

    @PrePersist
    void prePersist() {
        Clock efetivo = clock != null ? clock : Clock.systemUTC();
        this.createdAt = Instant.now(efetivo);
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getBannerUrl() {
        return bannerUrl;
    }

    public void setBannerUrl(String bannerUrl) {
        this.bannerUrl = bannerUrl;
    }

    public int getPriceMonthlyCents() {
        return priceMonthlyCents;
    }

    public void setPriceMonthlyCents(int priceMonthlyCents) {
        this.priceMonthlyCents = priceMonthlyCents;
    }

    public CommunityStatus getStatus() {
        return status;
    }

    public void setStatus(CommunityStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
