package pt.seerhub.config;

import java.time.Clock;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import pt.seerhub.user.security.JwtAuthenticationFilter;
import pt.seerhub.user.security.ProblemDetailAccessDeniedHandler;
import pt.seerhub.user.security.ProblemDetailAuthenticationEntryPoint;
import pt.seerhub.user.service.JwtService;

/**
 * Cadeia de segurança do SeerHub.
 *
 * <p>F01 substituiu {@code anyRequest().permitAll()} pelas regras reais de
 * autenticação/autorização (R1), mantendo {@code STATELESS} e
 * {@code csrf.disable()} herdados de F00 — API stateless com Bearer JWT,
 * não com sessão de servidor.
 *
 * <p>Regras, por ordem de especificidade: {@code /actuator/health} (e
 * sub-caminhos, para as probes) é público (senão o {@code healthcheck}
 * anónimo do {@code docker-compose.yml} nunca fica saudável); os três
 * endpoints de entrada de {@code POST /api/auth/*} (registo, login,
 * refresh) são públicos; {@code /api/admin/**} exige o papel {@code ADMIN}
 * (defesa em profundidade, a mesma regra que {@code AdminUserController}
 * já impõe com {@code @PreAuthorize}); qualquer outro pedido exige
 * autenticação.
 *
 * <p>{@code @EnableMethodSecurity} liga {@code @PreAuthorize} para F01 e
 * para as features seguintes (D-8 do plano de F01) — o
 * {@code PermissionEvaluator} por comunidade é de F04, não desta feature.
 *
 * <p><b>Para F02 e seguintes:</b> para proteger um endpoint novo, basta
 * chegar a ele autenticado (a regra {@code anyRequest().authenticated()}
 * já cobre qualquer caminho não listado aqui); para uma regra de papel
 * global adicional, acrescentar um {@code requestMatchers(...)} antes de
 * {@code anyRequest()} nesta cadeia, ou usar
 * {@code @PreAuthorize("hasRole('...')")} no controlador — nunca reescrever
 * este método do zero.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Força do BCrypt exigida pelo R1 (mínimo 10), pública para ser assertável em teste. */
    public static final int BCRYPT_STRENGTH = 10;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtService jwtService, ObjectMapper objectMapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
                                "/api/auth/refresh").permitAll()
                        // Controlador de teste de ApiExceptionHandlerIT (F00), detetado pelo
                        // component-scan em qualquer contexto *IT porque o classpath de teste
                        // fica dentro do pacote base pt.seerhub. Nunca existe fora de testes.
                        .requestMatchers("/__test__/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new ProblemDetailAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(new ProblemDetailAccessDeniedHandler(objectMapper)))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    public JwtService jwtService(SeerHubProperties properties, Clock clock) {
        return new JwtService(properties, clock);
    }
}
