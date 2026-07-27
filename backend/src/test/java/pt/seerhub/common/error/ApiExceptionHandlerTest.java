package pt.seerhub.common.error;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * X5 do plano de F04: uma {@link AccessDeniedException} lançada dentro da
 * invocação de um controlador (ex.: um {@code @PreAuthorize} futuro) devolve
 * {@code 403} num {@link ProblemDetail}, nunca o {@code 500} genérico —
 * defesa em profundidade descrita no Javadoc de {@code SecurityConfig}.
 * Unitário, sem contexto Spring.
 */
class ApiExceptionHandlerTest {

    @Test
    void acessoNegadoDevolve403EmProblemDetail() {
        ApiExceptionHandler handler = new ApiExceptionHandler();

        ProblemDetail problemDetail = handler.tratarAcessoNegado(new AccessDeniedException("negado"));

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problemDetail.getDetail()).isEqualTo(ApiExceptionHandler.MENSAGEM_ACESSO_NEGADO);
    }
}
