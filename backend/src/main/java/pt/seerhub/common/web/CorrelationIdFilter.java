package pt.seerhub.common.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Garante a correlação de pedidos de ponta a ponta (R15.6b/c).
 *
 * <p>Lê o cabeçalho {@link #HEADER} do pedido; se ausente ou com um formato
 * inválido, gera um novo {@link UUID}. O valor final é colocado no
 * {@link org.slf4j.MDC} (chave {@link #MDC_KEY}, para que apareça em todos
 * os logs estruturados do pedido) e escrito de volta no cabeçalho da
 * resposta, para que o cliente o possa registar e correlacionar com os logs
 * do servidor. O MDC é sempre limpo no {@code finally}, mesmo que o pedido
 * seguinte na cadeia lance uma exceção.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    /** Aceita apenas valores com aparência de UUID; qualquer outra coisa é substituída. */
    private static final Pattern FORMATO_VALIDO = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recebido = request.getHeader(HEADER);
        String correlationId = (recebido != null && FORMATO_VALIDO.matcher(recebido).matches())
                ? recebido
                : UUID.randomUUID().toString();

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            // Uma linha por pedido com o correlationId no MDC (propagado
            // automaticamente para o log estruturado), para que R15.6b seja
            // verificável também a olho nos logs do servidor, não só em teste.
            log.info("Pedido recebido: {} {}", request.getMethod(), request.getRequestURI());
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
