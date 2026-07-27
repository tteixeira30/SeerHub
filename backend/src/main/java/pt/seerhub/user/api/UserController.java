package pt.seerhub.user.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pt.seerhub.common.error.ApiException;
import pt.seerhub.user.domain.User;
import pt.seerhub.user.repo.UserRepository;
import pt.seerhub.user.security.AuthenticatedUser;

/**
 * {@code GET /api/users/me} — o endpoint protegido de referência de F01,
 * usado por {@code AuthorizationIT} para provar 401/403/200.
 */
@RestController
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/api/users/me")
    public UserResponse eu(@AuthenticationPrincipal AuthenticatedUser autenticado) {
        User user = userRepository.findById(autenticado.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Autenticação necessária."));
        return UserResponse.de(user);
    }
}
