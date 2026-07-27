package pt.seerhub.football.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidade mapeada contra a tabela {@code teams} do baseline {@code V2}
 * (F00). {@link #normalizedName} é sempre atribuído por
 * {@code TeamNameNormalizer} no serviço de sincronização — nunca pelo
 * chamador — e serve o índice de trigramas ({@code ix_teams_normalized_name_trgm})
 * que F06b vai consumir.
 */
@Entity
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false, unique = true)
    private long providerId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 120)
    private String normalizedName;

    @Column(name = "short_name", length = 60)
    private String shortName;

    @Column(length = 80)
    private String country;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    protected Team() {
        // exigido pelo JPA
    }

    public Team(long providerId, String name, String normalizedName) {
        this.providerId = providerId;
        this.name = name;
        this.normalizedName = normalizedName;
    }

    public Long getId() {
        return id;
    }

    public long getProviderId() {
        return providerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }
}
