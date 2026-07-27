package pt.seerhub.user.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import com.fasterxml.jackson.databind.ObjectMapper;

import pt.seerhub.common.web.CorrelationIdFilter;

/**
 * Escreve o 401 como {@link ProblemDetail} (mesma forma que
 * {@code ApiExceptionHandler}), porque o {@code ExceptionTranslationFilter}
 * do Spring Security corre antes do {@code @RestControllerAdvice} e este
 * nunca veria a exceção de autenticação.
 */
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String DETALHE = "Autenticação necessária.";

    private final ObjectMapper objectMapper;

    public ProblemDetailAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, DETALHE);
        problemDetail.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), problemDetail);
    }
}
