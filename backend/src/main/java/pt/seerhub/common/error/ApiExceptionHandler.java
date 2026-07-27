package pt.seerhub.common.error;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pt.seerhub.common.web.CorrelationIdFilter;

/**
 * Handler global de erros (R15.6d / X3).
 *
 * <p>Toda resposta de erro é um {@link ProblemDetail} com a propriedade
 * extra {@code correlationId}, lida do MDC. Exceções não tratadas nunca
 * devolvem a sua mensagem original nem stack trace ao cliente — o 500
 * genérico tem sempre o mesmo {@code detail} fixo; a exceção real é
 * registada no log do servidor, com o mesmo correlationId, para
 * investigação.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final String MENSAGEM_ERRO_INESPERADO = "Ocorreu um erro inesperado.";

    @ExceptionHandler(ApiException.class)
    public ProblemDetail tratarApiException(ApiException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getDetail());
        adicionarCorrelationId(problemDetail);
        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail tratarValidacao(MethodArgumentNotValidException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Existem campos inválidos no pedido.");

        List<String> erros = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatarErroDeCampo)
                .toList();
        problemDetail.setProperty("erros", erros);
        adicionarCorrelationId(problemDetail);
        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail tratarExcecaoNaoPrevista(Exception ex) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        log.error("Exceção não tratada [correlationId={}]", correlationId, ex);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, MENSAGEM_ERRO_INESPERADO);
        adicionarCorrelationId(problemDetail);
        return problemDetail;
    }

    private String formatarErroDeCampo(FieldError erro) {
        return erro.getField() + ": " + erro.getDefaultMessage();
    }

    private void adicionarCorrelationId(ProblemDetail problemDetail) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        problemDetail.setProperty("correlationId", correlationId);
    }
}
