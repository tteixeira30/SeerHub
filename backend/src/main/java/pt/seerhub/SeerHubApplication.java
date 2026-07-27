package pt.seerhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import pt.seerhub.config.SeerHubProperties;

/**
 * Ponto de entrada da aplicação SeerHub.
 *
 * {@code @EnableScheduling} está aqui desde já porque F03 (expiração de
 * subscrições), F05 (sincronização de jogos) e F13 (notificações) precisam
 * de tarefas agendadas; F00 não regista nenhuma.
 */
@SpringBootApplication
@EnableConfigurationProperties(SeerHubProperties.class)
@EnableScheduling
public class SeerHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeerHubApplication.class, args);
    }
}
