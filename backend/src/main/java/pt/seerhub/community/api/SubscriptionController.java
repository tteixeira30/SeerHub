package pt.seerhub.community.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import pt.seerhub.community.service.SubscriptionService;
import pt.seerhub.user.security.AuthenticatedUser;

/**
 * {@code POST}/{@code DELETE /api/communities/{slug}/subscription} e
 * {@code GET /api/me/subscriptions} (R3, critérios 1, 2 e 4). Nenhum destes
 * caminhos vive sob o prefixo de teste de {@code SecurityConfig}; caem
 * todos em {@code anyRequest().authenticated()} (§2.3 do plano de F03).
 */
@RestController
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping("/api/communities/{slug}/subscription")
    public ResponseEntity<SubscriptionResponse> subscrever(
            @PathVariable String slug,
            @AuthenticationPrincipal AuthenticatedUser autenticado) {
        SubscriptionService.Resultado resultado = subscriptionService.subscrever(slug, autenticado);
        if (resultado.criada()) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .location(URI.create("/api/communities/" + slug + "/subscription"))
                    .body(resultado.subscricao());
        }
        return ResponseEntity.ok(resultado.subscricao());
    }

    @DeleteMapping("/api/communities/{slug}/subscription")
    public SubscriptionResponse cancelar(
            @PathVariable String slug,
            @AuthenticationPrincipal AuthenticatedUser autenticado) {
        return subscriptionService.cancelar(slug, autenticado);
    }

    @GetMapping("/api/me/subscriptions")
    public List<SubscriptionResponse> minhasSubscricoes(@AuthenticationPrincipal AuthenticatedUser autenticado) {
        return subscriptionService.listarDoUtilizador(autenticado.id());
    }
}
