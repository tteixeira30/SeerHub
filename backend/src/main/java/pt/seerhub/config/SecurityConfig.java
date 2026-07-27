package pt.seerhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cadeia de segurança mínima do esqueleto (F00).
 *
 * <p><b>F01 substitui as regras de autorização; não apagar esta cadeia,
 * editar as regras.</b> Esta configuração existe só para que a aplicação
 * arranque sem sessão de servidor e sem CSRF (API stateless com JWT, que
 * F01 introduz), com todos os pedidos permitidos por agora — não há
 * nenhum endpoint de negócio nesta feature para proteger. F01 troca
 * {@code anyRequest().permitAll()} pelas regras reais de autenticação e
 * autorização, mantendo {@code STATELESS} e {@code csrf.disable()}.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
