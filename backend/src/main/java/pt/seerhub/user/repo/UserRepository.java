package pt.seerhub.user.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pt.seerhub.user.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);
}
