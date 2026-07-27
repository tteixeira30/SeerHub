package pt.seerhub.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base de todos os erros de negócio lançados pelas features do SeerHub.
 *
 * <p>{@link #detail} é uma mensagem segura para mostrar ao utilizador
 * (nunca informação interna) e é sempre devolvida no corpo do
 * {@code ProblemDetail} pelo {@link ApiExceptionHandler}, com o
 * {@link #status} indicado.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String detail;

    public ApiException(HttpStatus status, String detail) {
        super(detail);
        this.status = status;
        this.detail = detail;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }
}
