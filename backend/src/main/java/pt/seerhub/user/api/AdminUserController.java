package pt.seerhub.user.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pt.seerhub.user.repo.UserRepository;

/**
 * {@code GET /api/admin/users} — primeiro endpoint exclusivo de
 * {@code ADMIN} (D-11 do plano de F01): lista simples, sem pesquisa, sem
 * filtro e sem paginação. F14 estende isto; não construir sobre este
 * endpoint como se fosse final.
 */
@RestController
public class AdminUserController {

    private final UserRepository userRepository;

    public AdminUserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/users")
    public List<UserResponse> listar() {
        return userRepository.findAll().stream().map(UserResponse::de).toList();
    }
}
