package pt.seerhub.common.error;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pt.seerhub.support.AbstractIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R15.6d / X3: uma exceção não tratada que chega ao {@link ApiExceptionHandler}
 * devolve 500 com um {@code ProblemDetail} que traz {@code correlationId} e
 * NUNCA a mensagem original, o nome da classe da exceção ou o stack trace.
 */
class ApiExceptionHandlerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void excecaoNaoTratadaDevolve500SemStackTraceComCorrelationId() throws Exception {
        mockMvc.perform(get("/__test__/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("segredo interno"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("at pt.seerhub"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsStringIgnoringCase("trace"))));
    }

    // Nota: o controlador de teste é detetado por component-scan (está no
    // pacote base pt.seerhub, tal como qualquer controlador de produção),
    // por isso não é também registado explicitamente com @Bean — isso
    // duplicaria o mapeamento do endpoint.
    @TestConfiguration
    static class ControladorDeRebentamento {

        @RestController
        static class ControladorBoom {
            @GetMapping("/__test__/boom")
            public void rebenta() {
                throw new IllegalStateException("segredo interno");
            }
        }
    }
}
