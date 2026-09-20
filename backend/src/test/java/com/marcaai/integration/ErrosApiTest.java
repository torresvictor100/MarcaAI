package com.marcaai.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Status e corpo de erro da API (ADR-011): erro de quem chamou nunca vira 500, cada caso tem o seu status e
 * o seu código em {@code erro}, e o corpo é o mesmo em todo lugar. Casos do scan do OWASP ZAP incluídos.
 */
class ErrosApiTest extends IntegracaoBase {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String token(String login) throws Exception {
        String corpo = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("login", login, "senha", "Senha123!"))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo).get("token").asText();
    }

    private ResultActions como(String login, MockHttpServletRequestBuilder requisicao) throws Exception {
        return mockMvc.perform(requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(login)));
    }

    private static void corpoDeErro(ResultActions resposta, int status, String codigo) throws Exception {
        resposta.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.erro").value(codigo))
                .andExpect(jsonPath("$.mensagem").isNotEmpty())
                .andExpect(jsonPath("$.caminho").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.mensagem", not(containsString("Exception"))));
    }

    // --- Antes davam 500 -----------------------------------------------------------------------------

    @Test
    void jsonMalformadoDa400() throws Exception {
        corpoDeErro(mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"login\":")),
                400, "JSON_INVALIDO");
    }

    @Test
    void valorDeTipoErradoNoCorpoDa400DizendoOCampo() throws Exception {
        ResultActions resposta = como("secretaria", patch("/fila/1/override").contentType(MediaType.APPLICATION_JSON)
                .content("{\"posicao\":\"um\",\"justificativa\":\"x\"}"));
        corpoDeErro(resposta, 400, "JSON_INVALIDO");
        resposta.andExpect(jsonPath("$.mensagem").value("Valor inválido para o campo 'posicao'"));
    }

    @Test
    void metodoErradoDa405ComAllow() throws Exception {
        ResultActions resposta = como("secretaria", delete("/fila/todas"));
        corpoDeErro(resposta, 405, "METODO_NAO_PERMITIDO");
        resposta.andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")));
    }

    @Test
    void rotaInexistenteDa404() throws Exception {
        corpoDeErro(como("secretaria", get("/nao-existe")), 404, "ROTA_NAO_ENCONTRADA");
    }

    @Test
    void parametroObrigatorioFaltandoDa400() throws Exception {
        corpoDeErro(como("secretaria", get("/vagas")), 400, "PARAMETRO_INVALIDO");
    }

    @Test
    void contentTypeErradoDa415() throws Exception {
        corpoDeErro(como("secretaria", post("/agendamentos").contentType(MediaType.TEXT_PLAIN).content("x")),
                415, "TIPO_DE_CONTEUDO_NAO_SUPORTADO");
    }

    // --- Cada caso com o seu status ------------------------------------------------------------------

    @Test
    void validacaoDa400ComAListaDeCampos() throws Exception {
        ResultActions resposta = como("secretaria", patch("/fila/1/override").contentType(MediaType.APPLICATION_JSON)
                .content("{\"posicao\":0,\"justificativa\":\"\"}"));
        corpoDeErro(resposta, 400, "DADOS_INVALIDOS");
        resposta.andExpect(jsonPath("$.campos.length()").value(2))
                .andExpect(jsonPath("$.campos[?(@.campo == 'justificativa')]").exists());
    }

    @Test
    void recursoInexistenteDa404() throws Exception {
        corpoDeErro(como("secretaria", get("/encaminhamentos/999999")), 404, "RECURSO_NAO_ENCONTRADO");
    }

    @Test
    void regraDeNegocioDa422EConflitoDa409() throws Exception {
        corpoDeErro(como("secretaria", post("/fila/relatorio-ia?especialidade=Astrologia")), 422, "REGRA_DE_NEGOCIO");

        String corpo = "{\"nome\":\"Dra. Repetida\",\"registroConselho\":\"CRM-SP 999001\",\"especialidade\":\"Dermatologia\"}";
        String secretaria = token("secretaria");
        mockMvc.perform(post("/profissionais").header(HttpHeaders.AUTHORIZATION, "Bearer " + secretaria)
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isCreated());
        corpoDeErro(mockMvc.perform(post("/profissionais").header(HttpHeaders.AUTHORIZATION, "Bearer " + secretaria)
                .contentType(MediaType.APPLICATION_JSON).content(corpo)), 409, "CONFLITO");
    }

    @Test
    void semLoginEPapelErradoUsamOMesmoCorpo() throws Exception {
        corpoDeErro(mockMvc.perform(get("/fila/todas")), 401, "NAO_AUTENTICADO");
        corpoDeErro(como("bruno.ubs", get("/fila/todas")), 403, "ACESSO_NEGADO");
        corpoDeErro(mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"admin\",\"senha\":\"errada\"}")), 401, "CREDENCIAIS_INVALIDAS");
    }

    // --- 403 antes do 400 (a regra de papel da rota vem antes de ler o corpo) ------------------------

    @Test
    void papelSemPermissaoRecebe403MesmoComCorpoInvalido() throws Exception {
        corpoDeErro(como("secretaria", post("/atendimentos").contentType(MediaType.APPLICATION_JSON).content("{}")),
                403, "ACESSO_NEGADO");
        corpoDeErro(como("secretaria", post("/encaminhamentos/1/resultado-exame").contentType(MediaType.APPLICATION_JSON)
                .content("{}")), 403, "ACESSO_NEGADO");
        corpoDeErro(como("bruno.ubs", post("/agendamentos").contentType(MediaType.APPLICATION_JSON).content("lixo")),
                403, "ACESSO_NEGADO");
        corpoDeErro(como("joao.paciente", patch("/fila/1/override").contentType(MediaType.APPLICATION_JSON).content("{}")),
                403, "ACESSO_NEGADO");
        // Quem tem o papel continua recebendo a validação normal.
        corpoDeErro(como("bruno.ubs", post("/atendimentos").contentType(MediaType.APPLICATION_JSON).content("{}")),
                400, "DADOS_INVALIDOS");
    }

    // --- Monitoramento: Actuator só para ADMIN; Prometheus com credencial própria --------------------

    @Test
    void metricasEEstadoDosCircuitosSoParaAdmin() throws Exception {
        como("admin", get("/actuator/metrics")).andExpect(status().isOk());
        como("admin", get("/actuator/circuitbreakers")).andExpect(status().isOk())
                .andExpect(jsonPath("$.circuitBreakers['anthropic-triagem']").exists())
                .andExpect(jsonPath("$.circuitBreakers['anthropic-relatorio']").exists());
        como("admin", get("/actuator/bulkheads")).andExpect(status().isOk());
        como("admin", get("/actuator/retries")).andExpect(status().isOk());
        corpoDeErro(como("secretaria", get("/actuator/metrics")), 403, "ACESSO_NEGADO");
        corpoDeErro(mockMvc.perform(get("/actuator/metrics")), 401, "NAO_AUTENTICADO");
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void prometheusColetaComACredencialPropriaENaoComOutra() throws Exception {
        String basic = Base64.getEncoder().encodeToString(("prometheus:" + TOKEN_METRICAS_TESTE).getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(get("/actuator/prometheus").header(HttpHeaders.AUTHORIZATION, "Basic " + basic))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("http_server_requests_seconds")))
                .andExpect(content().string(containsString("resilience4j_circuitbreaker_state")));

        String errado = Base64.getEncoder().encodeToString("prometheus:outro".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(get("/actuator/prometheus").header(HttpHeaders.AUTHORIZATION, "Basic " + errado))
                .andExpect(status().isUnauthorized());
        como("secretaria", get("/actuator/prometheus")).andExpect(status().isForbidden());
        como("admin", get("/actuator/prometheus")).andExpect(status().isOk());
    }

    @Test
    void headersDeIsolamentoEntreOrigens() throws Exception {
        como("secretaria", get("/especialidades"))
                .andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"))
                .andExpect(header().string("Cross-Origin-Opener-Policy", "same-origin"))
                .andExpect(header().string("Cross-Origin-Embedder-Policy", "require-corp"));
    }
}
