package com.marcaai.integration;

import com.marcaai.auth.ChavesJwtDeTeste;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Base64;

/**
 * Base dos testes de integração: um Postgres real (Testcontainers) e a aplicação inteira. O container é
 * único para a execução toda (padrão singleton, sem {@code @Container}) e a configuração é a mesma em todas
 * as subclasses, então o Spring reaproveita o mesmo contexto — sobe uma vez só.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
// Em teste o Spring Boot desliga a exportação de métricas; aqui ela é parte do que se testa (/actuator/prometheus).
@AutoConfigureObservability
abstract class IntegracaoBase {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("marcaai")
            .withUsername("postgres")
            .withPassword("postgres");

    static {
        POSTGRES.start();
    }

    /** Chave de criptografia do CPF só dos testes (32 bytes fixos). */
    static final String CHAVE_CRIPTO_TESTE = Base64.getEncoder().encodeToString("chave-de-teste-com-32-bytes!!!!!".getBytes());

    /** Credencial do Prometheus só dos testes. */
    static final String TOKEN_METRICAS_TESTE = "token-de-metricas-dos-testes";

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Sem chave configurada: força o fallback determinístico (TemplateJustificativaGenerator), sem chamada de rede em teste.
        registry.add("marcaai.anthropic.api-key", () -> "");
        // O application.properties não tem chaves padrão (ADR-010): o teste traz as dele, geradas em memória.
        registry.add("marcaai.jwt.private-key", () -> ChavesJwtDeTeste.PRINCIPAL.privada);
        registry.add("marcaai.jwt.public-key", () -> ChavesJwtDeTeste.PRINCIPAL.publica);
        registry.add("marcaai.cripto.chave", () -> CHAVE_CRIPTO_TESTE);
        // Todas as requisições dos testes saem do mesmo IP; o limite em si é testado no LimiteRequisicoesFilterTest.
        registry.add("marcaai.rate-limit.anonimo-por-minuto", () -> "100000");
        registry.add("marcaai.rate-limit.autenticado-por-minuto", () -> "100000");
        registry.add("marcaai.metricas.token", () -> TOKEN_METRICAS_TESTE);
    }
}
