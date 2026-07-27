package pt.seerhub.user.domain;

import java.time.Clock;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Entidade mapeada contra a tabela {@code users} do baseline {@code V2}
 * (F00). {@code username} e {@code display_name} são {@code NOT NULL} e
 * {@code username} é único na base de dados, mas são derivados pelo
 * {@code UsernameGenerator} — o registo (R1) só pede email e password (D-6
 * do plano de F01).
 *
 * <p>{@code createdAt} tem {@code DEFAULT now()} no DDL, mas o Hibernate
 * escreve sempre a coluna: por isso é preenchido explicitamente em
 * {@link #prePersist()} com o {@link Clock} injetado, nunca confiando no
 * default da base de dados.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "global_role", nullable = false, length = 20)
    private GlobalRole globalRole = GlobalRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Relógio usado só para preencher {@link #createdAt} em
     * {@link #prePersist()}; nunca persistido, existe apenas para o JPA
     * poder injetá-lo através do construtor usado pelas fábricas. Não é
     * gerido pelo Hibernate como coluna.
     */
    @Transient
    private Clock clock;

    protected User() {
        // exigido pelo JPA
    }

    public User(String email, String passwordHash, String username, String displayName, Clock clock) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.username = username;
        this.displayName = displayName;
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

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public GlobalRole getGlobalRole() {
        return globalRole;
    }

    public void setGlobalRole(GlobalRole globalRole) {
        this.globalRole = globalRole;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
