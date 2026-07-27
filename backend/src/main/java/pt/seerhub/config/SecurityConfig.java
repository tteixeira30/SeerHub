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
 * para as features seguintes (D-8 do plano de F01). A autorização por
 * comunidade (R4) não usa {@code @PreAuthorize}/{@code PermissionEvaluator}
 * deliberadamente — ver {@code pt.seerhub.community.service.CommunityPermissionService}
 * e {@code pt.seerhub.community.security.CommunityPermissionInterceptor}
 * (F04, §2.3 do seu plano): uma {@code AuthorizationDeniedException} lançada
 * pela method security dentro da invocação do controlador seria resolvida
 * pelos {@code HandlerExceptionResolver} do {@code DispatcherServlet} e cairia
 * no handler genérico de {@code ApiExceptionHandler}, devolvendo {@code 500}
 * em vez de {@code 403}. F04 usa {@code ApiException(403, ...)}, o mecanismo
 * já provado por F02/F03, e acrescenta {@code @ExceptionHandler(AccessDeniedException.class)}
 * como defesa em profundidade para qualquer {@code @PreAuthorize} futuro.
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
                        // F02: listagem pública e leitura de uma comunidade por slug (R2).
                        // Só GET — POST /api/communities e PUT /api/communities/{slug}
                        // continuam a exigir autenticação por anyRequest().authenticated().
                        .requestMatchers(HttpMethod.GET, "/api/communities", "/api/communities/*").permitAll()
                        // F05: os emblemas aparecem no perfil público de uma comunidade (R11).
                        .requestMatchers(HttpMethod.GET, "/api/football/crests/**").permitAll()
                        .requestMatchers("/actuator/metrics/**").hasRole("ADMIN")
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
