package pt.seerhub.football;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import pt.seerhub.config.SeerHubProperties;
import pt.seerhub.football.service.SeasonResolver;

import static org.assertj.core.api.Assertions.assertThat;

/** R5, caso de fronteira X2. */
class SeasonResolverTest {

    @Test
    void aEpocaEDerivadaDoRelogioESobreponivelPorConfiguracao() {
        Clock emJulhoDe2026 = Clock.fixed(Instant.parse("2026-07-15T10:00:00Z"), ZoneOffset.UTC);
        Clock emFevereiroDe2026 = Clock.fixed(Instant.parse("2026-02-15T10:00:00Z"), ZoneOffset.UTC);

        SeasonResolver semOverride = new SeasonResolver(emJulhoDe2026, propriedadesCom(null));
        assertThat(semOverride.epocaAtual()).isEqualTo(2026);

        SeasonResolver emFevereiro = new SeasonResolver(emFevereiroDe2026, propriedadesCom(null));
        assertThat(emFevereiro.epocaAtual()).isEqualTo(2025);

        SeasonResolver comOverride = new SeasonResolver(emFevereiroDe2026, propriedadesCom(2030));
        assertThat(comOverride.epocaAtual()).isEqualTo(2030);
    }

    private static SeerHubProperties propriedadesCom(Integer overrideEpoca) {
        return new SeerHubProperties(
                null,
                new SeerHubProperties.Football(
                        null, null, null, null, null, overrideEpoca,
                        null, null, null, null, null, null, null, null),
                null);
    }
}
