package pt.seerhub.tips.parser;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import pt.seerhub.football.service.FixtureView;

/** Implementação mínima de {@link TipCatalog}, imutável — a lista de jogos é copiada. */
public record SimpleTipCatalog(ZoneId fuso, LocalDate hoje, List<FixtureView> jogos) implements TipCatalog {

    public SimpleTipCatalog {
        jogos = List.copyOf(jogos);
    }

    /** Catálogo sem jogos nenhuns — o único que F06a precisa para os seus próprios testes. */
    public static SimpleTipCatalog vazio(Clock clock) {
        return new SimpleTipCatalog(clock.getZone(), LocalDate.now(clock), List.of());
    }

    /** Com jogos — usado por F06b, que lê {@link #jogos()}. */
    public static SimpleTipCatalog de(Clock clock, List<FixtureView> jogos) {
        return new SimpleTipCatalog(clock.getZone(), LocalDate.now(clock), jogos);
    }
}
