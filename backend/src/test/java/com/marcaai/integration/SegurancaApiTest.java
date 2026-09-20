package com.marcaai.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marcaai.auth.ChavesJwtDeTeste;
import com.marcaai.auth.JwtService;
import com.marcaai.auth.Papel;
import com.marcaai.triagemia.AnthropicIndisponivelException;
import com.marcaai.triagemia.AnthropicJustificativaGenerator;
import com.marcaai.triagemia.ContextoJustificativa;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de segurança da API com MockMvc e Spring Security Test (curso de Segurança, aula 5; ADR-010):
 * autenticação (401), autorização por papel (403), validação do JWT, headers de segurança, CORS,
 * CPF cifrado no banco e o Circuit Breaker da Anthropic. Roda contra o banco real do Testcontainers.
 */
class SegurancaApiTest extends IntegracaoBase {

    private static final String SENHA = "Senha123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AnthropicJustificativaGenerator anthropic;

    @Autowired
    private CircuitBreakerRegistry circuitos;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String login(String login) throws Exception {
        String corpo = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("login", login, "senha", SENHA))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo).get("token").asText();
    }

    private static MockHttpServletRequestBuilder comToken(MockHttpServletRequestBuilder requisicao, String token) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    // --- Autenticação: sem login não entra -----------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"/fila/todas", "/painel/resumo", "/vagas?especialidade=Cardiologia", "/encaminhamentos/1",
            "/atendimentos/1", "/pacientes?nome=jo", "/agenda/vagas", "/cids?especialidade=Cardiologia",
            "/fila/relatorio-ia?especialidade=Cardiologia", "/encaminhamentos/1/resultado-exame"})
    @WithAnonymousUser
    void semLoginTodaRotaProtegidaResponde401ComEnvelopeJson(String rota) throws Exception {
        mockMvc.perform(get(rota))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.mensagem").exists());
    }

    @Test
    void loginERotasPublicasNaoExigemToken() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void loginErradoNaoDizSeOUsuarioExiste() throws Exception {
        String inexistente = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"nao.existe\",\"senha\":\"x\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String senhaErrada = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"admin\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(inexistente).get("mensagem"))
                .isEqualTo(objectMapper.readTree(senhaErrada).get("mensagem"));
    }

    // --- Autorização por papel (RBAC) ----------------------------------------------------------------

    @Test
    @WithMockUser(roles = "PACIENTE")
    void pacienteNaoAcessaRotasDaGestao() throws Exception {
        mockMvc.perform(get("/painel/resumo")).andExpect(status().isForbidden());
        mockMvc.perform(get("/vagas/marcadas?especialidade=Cardiologia")).andExpect(status().isForbidden());
        mockMvc.perform(get("/profissionais")).andExpect(status().isForbidden());
        mockMvc.perform(post("/agendamentos/1/cancelar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"x\"}")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SECRETARIA")
    void secretariaNaoCriaAtendimentoNemRegistraResultado() throws Exception {
        // Corpo válido: o @Valid do corpo roda antes do @PreAuthorize (corpo inválido daria 400, não 403).
        mockMvc.perform(post("/atendimentos").contentType(MediaType.APPLICATION_JSON).content("""
                        {"pacienteId":1,"unidadeId":1,"data":"2026-09-24T10:00:00","classificacaoRisco":"VERDE"}"""))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/encaminhamentos/1/resultado-exame").contentType(MediaType.APPLICATION_JSON).content("""
                        {"referenciaArquivo":"laudo.pdf","dataResultado":"2026-09-24"}"""))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/agenda/encaminhamentos")).andExpect(status().isForbidden());
    }

    @Test
    void cadaPapelComTokenRealSoEntraOndePode() throws Exception {
        String paciente = login("joao.paciente");
        String medico = login("bruno.ubs");
        String laboratorio = login("lab.central");
        String secretaria = login("secretaria");
        String admin = login("admin");

        for (String rota : List.of("/painel/resumo", "/fila/todas", "/profissionais")) {
            mockMvc.perform(comToken(get(rota), secretaria)).andExpect(status().isOk());
            mockMvc.perform(comToken(get(rota), admin)).andExpect(status().isOk());
            mockMvc.perform(comToken(get(rota), paciente)).andExpect(status().isForbidden());
            mockMvc.perform(comToken(get(rota), medico)).andExpect(status().isForbidden());
            mockMvc.perform(comToken(get(rota), laboratorio)).andExpect(status().isForbidden());
        }
        mockMvc.perform(comToken(get("/pacientes?nome=jo"), medico)).andExpect(status().isOk());
        mockMvc.perform(comToken(get("/pacientes?nome=jo"), paciente)).andExpect(status().isForbidden());
    }

    // --- Validação do JWT ----------------------------------------------------------------------------

    @Test
    void tokenForjadoExpiradoDeOutroEmissorOuSemAssinaturaDa401() throws Exception {
        String outraChave = ChavesJwtDeTeste.gerar().jwtService(60_000L).gerarToken(1L, "admin", Papel.ADMIN);
        String expirado = ChavesJwtDeTeste.PRINCIPAL.jwtService(-1_000L).gerarToken(1L, "admin", Papel.ADMIN);
        var chavePrivada = java.security.KeyFactory.getInstance("RSA").generatePrivate(
                new java.security.spec.PKCS8EncodedKeySpec(java.util.Base64.getDecoder().decode(ChavesJwtDeTeste.PRINCIPAL.privada)));
        String outroEmissor = Jwts.builder().issuer("outro-sistema").subject("admin").claim("usuarioId", "1")
                .claim("papel", "ADMIN").signWith(chavePrivada, Jwts.SIG.RS256).compact();
        String semAssinatura = Jwts.builder().issuer(JwtService.EMISSOR).subject("admin").claim("usuarioId", "1")
                .claim("papel", "ADMIN").compact();

        for (String token : List.of(outraChave, expirado, outroEmissor, semAssinatura, "lixo")) {
            mockMvc.perform(comToken(get("/painel/resumo"), token)).andExpect(status().isUnauthorized());
        }
    }

    // --- Headers de segurança e CORS -----------------------------------------------------------------

    @Test
    void respostasTrazemOsHeadersDeSeguranca() throws Exception {
        mockMvc.perform(comToken(get("/especialidades"), login("secretaria")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("default-src 'self'")))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", org.hamcrest.Matchers.containsString("camera=()")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void corsSoLiberaAOrigemConfigurada() throws Exception {
        mockMvc.perform(options("/fila/todas").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mockMvc.perform(options("/fila/todas").header("Origin", "http://site-malicioso.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // --- Senha e CPF protegidos no banco -------------------------------------------------------------

    @Test
    void loginRegravaSenhaDaSeedComBcryptCusto12() throws Exception {
        login("marta.paciente");

        String hash = jdbc.queryForObject("SELECT senha_hash FROM usuarios WHERE login = 'marta.paciente'", String.class);
        assertThat(hash).startsWith("$2a$12$");
        login("marta.paciente");
    }

    @Test
    void cpfFicaCifradoNoBancoComIndiceCegoEAApiDevolveMascarado() throws Exception {
        List<Map<String, Object>> linhas = jdbc.queryForList("SELECT cpf, cpf_hash FROM pacientes");

        assertThat(linhas).isNotEmpty().allSatisfy(linha -> {
            assertThat(linha.get("cpf").toString()).startsWith("v1:").doesNotContainPattern("^\\d{11}$");
            assertThat(linha.get("cpf_hash").toString()).hasSize(64);
        });
        assertThat(linhas).extracting(l -> l.get("cpf_hash")).doesNotHaveDuplicates();

        String resposta = mockMvc.perform(comToken(get("/pacientes?nome=jo"), login("secretaria")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(resposta).containsPattern("\\*\\*\\*\\.\\d{3}\\.\\d{3}-\\*\\*");
    }

    // --- Circuit Breaker da Anthropic ----------------------------------------------------------------

    @Test
    void circuitoAbreDepoisDeFalhasSeguidasEAChamadaNemSaiDaAplicacao() {
        CircuitBreaker circuito = circuitos.circuitBreaker(AnthropicJustificativaGenerator.RESILIENCIA);
        circuito.reset();
        ContextoJustificativa contexto = new ContextoJustificativa(1L, "Cardiologia", 50.0, Map.of(), List.of(), false);

        // Nos testes não há chave da Anthropic: toda chamada falha — 5 falhas é o mínimo para o circuito avaliar.
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> anthropic.gerar(contexto)).isInstanceOf(AnthropicIndisponivelException.class)
                    .hasMessageContaining("ANTHROPIC_API_KEY");
        }

        assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> anthropic.gerar(contexto)).isInstanceOf(AnthropicIndisponivelException.class)
                .hasMessageContaining("Circuit breaker");
        assertThat(circuito.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(1);
        circuito.reset();
    }
}
