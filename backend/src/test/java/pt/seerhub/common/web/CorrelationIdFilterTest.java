package pt.seerhub.common.web;

import org.junit.jupiter.api.Test;

import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R15.6b: gera um correlation id quando o pedido não traz um, propaga-o no
 * MDC durante o pedido e limpa-o no fim.
 * R15.6c: quando o cliente já envia um correlation id válido, é reutilizado.
 */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void geraCorrelationIdQuandoOPedidoNaoTrazENoLimpaOMdcNoFim() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/qualquer");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] correlationIdVistoDuranteOPedido = new String[1];
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                correlationIdVistoDuranteOPedido[0] = MDC.get(CorrelationIdFilter.MDC_KEY);
            }
        };

        filter.doFilter(request, response, chain);

        String cabecalhoDaResposta = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(cabecalhoDaResposta).isNotBlank();
        assertThat(correlationIdVistoDuranteOPedido[0]).isEqualTo(cabecalhoDaResposta);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void reutilizaOCorrelationIdRecebidoDoCliente() throws Exception {
        String correlationIdDoCliente = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/qualquer");
        request.addHeader(CorrelationIdFilter.HEADER, correlationIdDoCliente);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(correlationIdDoCliente);
    }
}
