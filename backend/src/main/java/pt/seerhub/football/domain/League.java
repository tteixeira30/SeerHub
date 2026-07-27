package pt.seerhub.football.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidade mapeada contra a tabela {@code leagues} do baseline {@code V2}
 * (F00). Uma linha por liga do fornecedor, independentemente da época —
 * {@link #season} diz qual a época que estamos a acompanhar (ver §2.6.3 do
 * plano de F05 sobre porquê {@code provider_id} continua {@code UNIQUE}).
 *
 * <p>Sem {@code @Version} (a tabela não tem coluna) e sem nenhuma coleção
 * (mesma disciplina D-16 de F02 sobre {@code Community}): gravar uma liga
 * nunca escreve em {@code fixtures} nem {@code teams}.
 */
@Entity
@Table(name = "leagues")
public class League {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false, unique = true)
    private long providerId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 80)
    private String country;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(nullable = false)
    private int season;

    @Column(nullable = false)
    private boolean active = true;

    protected League() {
        // exigido pelo JPA
    }

    public League(long providerId, String name, String country, String logoUrl, int season) {
        this.providerId = providerId;
        this.name = name;
        this.country = country;
        this.logoUrl = logoUrl;
        this.season = season;
        this.active = true;
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

    public int getSeason() {
        return season;
    }

    public void setSeason(int season) {
        this.season = season;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
