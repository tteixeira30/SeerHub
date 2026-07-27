package pt.seerhub.football.api;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pt.seerhub.common.error.ApiException;
import pt.seerhub.football.domain.League;
import pt.seerhub.football.domain.Team;
import pt.seerhub.football.repo.LeagueRepository;
import pt.seerhub.football.repo.TeamRepository;
import pt.seerhub.football.service.CrestCacheService;

/**
 * Serve os emblemas de equipas e ligas a partir da cache local (R5,
 * critério 5). Rota pública (o emblema aparece no perfil público de uma
 * comunidade, R11) — ver a linha nova em {@code SecurityConfig}.
 *
 * <p>Casos de fronteira:
 * <ul>
 *   <li>equipa/liga desconhecida → {@code 404} em {@code ProblemDetail} (5h);</li>
 *   <li>conhecida mas ainda sem ficheiro em cache → {@code 302} para a
 *   origem, nunca {@code 404}/{@code 500} nem uma chamada bloqueante ao CDN
 *   dentro do pedido do utilizador (5g).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/football/crests")
public class CrestController {

    private static final Duration MAX_AGE = Duration.ofDays(7);

    private final TeamRepository teamRepository;
    private final LeagueRepository leagueRepository;
    private final CrestCacheService crestCacheService;

    public CrestController(TeamRepository teamRepository, LeagueRepository leagueRepository,
            CrestCacheService crestCacheService) {
        this.teamRepository = teamRepository;
        this.leagueRepository = leagueRepository;
        this.crestCacheService = crestCacheService;
    }

    @GetMapping("/teams/{teamId}")
    public ResponseEntity<Resource> emblemaDaEquipa(@PathVariable long teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Equipa não encontrada."));

        Optional<Path> ficheiro = crestCacheService.ficheiroDaEquipa(teamId);
        if (ficheiro.isPresent()) {
            return servir(ficheiro.get());
        }
        return redirecionarParaOrigem(team.getLogoUrl());
    }

    @GetMapping("/leagues/{leagueId}")
    public ResponseEntity<Resource> emblemaDaLiga(@PathVariable long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Liga não encontrada."));

        Optional<Path> ficheiro = crestCacheService.ficheiroDaLiga(leagueId);
        if (ficheiro.isPresent()) {
            return servir(ficheiro.get());
        }
        return redirecionarParaOrigem(league.getLogoUrl());
    }

    private ResponseEntity<Resource> redirecionarParaOrigem(String logoUrl) {
        if (logoUrl == null || logoUrl.isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Ainda não há emblema disponível.");
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(logoUrl)).build();
    }

    private ResponseEntity<Resource> servir(Path ficheiro) {
        Resource resource = new FileSystemResource(ficheiro);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(MAX_AGE).cachePublic())
                .contentType(mediaTypeDaExtensao(ficheiro))
                .body(resource);
    }

    private MediaType mediaTypeDaExtensao(Path ficheiro) {
        String nome = ficheiro.getFileName().toString().toLowerCase(Locale.ROOT);
        if (nome.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (nome.endsWith(".jpg") || nome.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (nome.endsWith(".svg")) {
            return MediaType.valueOf("image/svg+xml");
        }
        if (nome.endsWith(".webp")) {
            return MediaType.valueOf("image/webp");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
