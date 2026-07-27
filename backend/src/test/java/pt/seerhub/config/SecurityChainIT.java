package pt.seerhub.config;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import pt.seerhub.support.AbstractIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * X3 do plano de F04: a dívida herdada {@code requestMatchers("/__test__/**").permitAll()}
 * está fechada — um pedido anónimo ao caminho interno de teste (o
 * controlador de rebentamento de {@code ApiExceptionHandlerIT}, apanhado
 * pelo component-scan de qualquer contexto {@code *IT}) já não é público:
 * cai em {@code anyRequest().authenticated()} e devolve {@code 401}.
 */
class SecurityChainIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void caminhoInternoDeTesteDeixouDeSerPublico() throws Exception {
        mockMvc.perform(get("/__test__/boom"))
                .andExpect(status().isUnauthorized());
    }
}
