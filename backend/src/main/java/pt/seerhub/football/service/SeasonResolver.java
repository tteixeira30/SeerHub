package pt.seerhub.football.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;

import pt.seerhub.config.SeerHubProperties;

/**
 * Resolve a época corrente da API-Football (R5, casos de fronteira X2).
 *
 * <p>Usa o override configurado ({@code seerhub.football.season}, variável
 * {@code API_FOOTBALL_SEASON}) se existir; senão deriva do {@link Clock}
 * injetado: as épocas europeias começam em julho/agosto, por isso mês ≥ 7
 * significa que já estamos na época do ano corrente; antes disso, ainda
 * estamos na época que começou no ano anterior.
 */
@Service
public class SeasonResolver {

    private static final int MES_DE_INICIO_DE_EPOCA = 7;

    private final Clock clock;
    private final SeerHubProperties properties;

    public SeasonResolver(Clock clock, SeerHubProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public int epocaAtual() {
        Integer override = properties.football() != null ? properties.football().season() : null;
        if (override != null) {
            return override;
        }
        LocalDate hoje = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        return hoje.getMonthValue() >= MES_DE_INICIO_DE_EPOCA ? hoje.getYear() : hoje.getYear() - 1;
    }
}
