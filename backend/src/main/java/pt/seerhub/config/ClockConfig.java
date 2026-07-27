package pt.seerhub.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio da aplicação, injetável e substituível em teste.
 *
 * <p>{@link JwtService} (F01) usa-o para calcular expirações de forma
 * testável com {@code Clock.fixed(...)}; F03 (tarefa diária de expiração de
 * memberships) e F08 vão precisar do mesmo bean.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
