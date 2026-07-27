package pt.seerhub.community.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import pt.seerhub.community.domain.CommunityPermission;
import pt.seerhub.community.security.RequiresCommunityPermission;
import pt.seerhub.community.service.ModerationService;
import pt.seerhub.user.security.AuthenticatedUser;

/**
 * Os três endpoints de moderação (R4, critério 1): nomear, remover e listar
 * membros. Os três exigem {@link CommunityPermission#MANAGE_MODERATORS}
 * (D-7 do plano de F04 — hoje só o dono).
 */
@RestController
public class ModerationController {

    private final ModerationService moderationService;

    public ModerationController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @PostMapping("/api/communities/{slug}/moderators")
    @RequiresCommunityPermission(CommunityPermission.MANAGE_MODERATORS)
    public ResponseEntity<CommunityMemberResponse> nomear(
            @PathVariable String slug,
            @Valid @RequestBody AppointModeratorRequest request,
            @AuthenticationPrincipal AuthenticatedUser autenticado) {
        CommunityMemberResponse resposta = moderationService.nomearModerador(slug, request.userId(), autenticado);
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }

    @DeleteMapping("/api/communities/{slug}/moderators/{userId}")
    @RequiresCommunityPermission(CommunityPermission.MANAGE_MODERATORS)
    public CommunityMemberResponse remover(
            @PathVariable String slug,
            @PathVariable Long userId,
            @AuthenticationPrincipal AuthenticatedUser autenticado) {
        return moderationService.removerModerador(slug, userId, autenticado);
    }

    @GetMapping("/api/communities/{slug}/members")
    @RequiresCommunityPermission(CommunityPermission.MANAGE_MODERATORS)
    public List<CommunityMemberResponse> membros(
            @PathVariable String slug,
            @AuthenticationPrincipal AuthenticatedUser autenticado) {
        return moderationService.listarMembros(slug, autenticado);
    }
}
