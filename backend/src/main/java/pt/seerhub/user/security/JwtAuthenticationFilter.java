package pt.seerhub.user.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.jsonwebtoken.JwtException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import pt.seerhub.user.service.JwtService;

/**
 * Lê o {@code Authorization: Bearer} do pedido e, se válido, preenche o
 * {@link org.springframework.security.core.context.SecurityContext} com um
 * {@link AuthenticatedUser} como principal.
 *
 * <p>Só age se houver cabeçalho {@code Authorization: Bearer}; um token
 * inválido (malformado, expirado, assinatura errada) deixa o contexto
 * vazio — é o {@code ExceptionTranslationFilter}/{@code AuthenticationEntryPoint}
 * a produzir o 401 mais tarde na cadeia, não este filtro. Nunca limpa um
 * contexto já preenchido por outro mecanismo.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String PREFIXO_BEARER = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String cabecalho = request.getHeader("Authorization");

        if (cabecalho != null && cabecalho.startsWith(PREFIXO_BEARER)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = cabecalho.substring(PREFIXO_BEARER.length());
            try {
                AuthenticatedUser autenticado = jwtService.validarEDecodificar(token);
                var authority = new SimpleGrantedAuthority("ROLE_" + autenticado.role().name());
                var authentication = new UsernamePasswordAuthenticationToken(
                        autenticado, null, List.of(authority));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                // Token inválido: o contexto fica vazio de propósito. O log
                // é ao nível debug para não poluir com tentativas normais de
                // access tokens expirados (esperadas em uso normal).
                log.debug("Token JWT inválido no pedido {} {}: {}",
                        request.getMethod(), request.getRequestURI(), ex.getMessage());
            }
        }

        chain.doFilter(request, response);
    }
}
