package pt.seerhub.community.api;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import pt.seerhub.community.service.CommunityService;
import pt.seerhub.user.security.AuthenticatedUser;

/**
 * Os cinco endpoints de comunidades (D-6 do plano de F02). Os dois
 * {@code GET} são públicos (ver {@code SecurityConfig}); neles,
 * {@code autenticado} pode vir a {@code null} porque um pedido anónimo tem
 * um {@code AnonymousAuthenticationToken} com principal {@code String}, não
 * {@link AuthenticatedUser} — nunca desreferenciar sem verificar.
 */
@RestController
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @PostMapping("/api/communities")
    public ResponseEntity<CommunityResponse> criar(
            @Valid @RequestBody CreateCommunityRequest request,
            @AuthenticationPrincipal AuthenticatedUser autenticado) {
        CommunityResponse resposta = communityService.criar(request, autenticado);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/communities/" + resposta.slug()))
                .body(resposta);
    }

    @GetMapping("/api/communities")
    public List<CommunityResponse> listar() {
        return communityService.listarAtivas();
    }

    @GetMapping("/api/communities/{slug}")
    public CommunityResponse obter(
            @PathVariable String slug,
            @AuthenticationPrincipal AuthenticatedUser autenticado) {
        Long viewerId = autenticado == null ? null : autenticado.id();
        return communityService.obterParaLeitura(slug, viewerId);
    }

    @PutMapping("/api/communities/{slug}")
    public CommunityResponse editar(
            @PathVariable String slug,
            @Valid @RequestBody UpdateCommunityRequest request,
            @AuthenticationPrincipal AuthenticatedUser autenticado) {
        return communityService.editar(slug, request, autenticado);
    }

    @GetMapping("/api/me/communities")
    public List<CommunityResponse> minhas(@AuthenticationPrincipal AuthenticatedUser autenticado) {
        return communityService.listarDoDono(autenticado.id());
    }
}
