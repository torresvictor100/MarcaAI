package com.marcaai.triagemia;

import com.marcaai.relatoriofila.AnthropicRelatorioRedator;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.bulkhead.autoconfigure.BulkheadAutoConfiguration;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Retry, Circuit Breaker e Bulkhead da chamada à Anthropic (ADR-011) de verdade — com as anotações do
 * Resilience4j ativas (AOP) — contra um servidor HTTP local que faz o papel da API. Contexto enxuto: só os
 * dois clientes da Anthropic, sem banco.
 */
@SpringBootTest(classes = {AnthropicJustificativaGenerator.class, AnthropicRelatorioRedator.class}, properties = {
        "marcaai.anthropic.api-key=chave-de-teste",
        "marcaai.anthropic.timeout-ms=2000",
        "marcaai.anthropic.relatorio-timeout-ms=2000",
        "resilience4j.retry.instances.anthropic-triagem.wait-duration=10ms",
        "resilience4j.bulkhead.instances.anthropic-triagem.max-concurrent-calls=1"
})
@ImportAutoConfiguration({AopAutoConfiguration.class, CircuitBreakerAutoConfiguration.class,
        RetryAutoConfiguration.class, BulkheadAutoConfiguration.class})
class ResilienciaAnthropicTest {

    /** Respostas que a "API" vai dar, na ordem; vazia = 200 com texto. */
    private static final ConcurrentLinkedQueue<Integer> PROXIMOS_STATUS = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger CHAMADAS = new AtomicInteger();
    private static volatile CountDownLatch segurarResposta;
    private static final HttpServer SERVIDOR = iniciar();

    private static HttpServer iniciar() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            servidor.createContext("/v1/messages", exchange -> {
                CHAMADAS.incrementAndGet();
                exchange.getRequestBody().readAllBytes();
                CountDownLatch segurar = segurarResposta;
                if (segurar != null) {
                    try {
                        segurar.await(3, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                Integer status = PROXIMOS_STATUS.poll();
                int codigo = status == null ? 200 : status;
                byte[] corpo = (codigo == 200
                        ? "{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"text\",\"text\":\"Texto da IA.\"}]}"
                        : "{\"type\":\"error\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(codigo, corpo.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(corpo);
                }
            });
            servidor.start();
            return servidor;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @DynamicPropertySource
    static void baseUrl(DynamicPropertyRegistry registry) {
        registry.add("marcaai.anthropic.base-url", () -> "http://localhost:" + SERVIDOR.getAddress().getPort());
    }

    @Autowired
    private AnthropicJustificativaGenerator triagem;

    @Autowired
    private AnthropicRelatorioRedator relatorio;

    @Autowired
    private CircuitBreakerRegistry circuitos;

    private final ContextoJustificativa contexto = new ContextoJustificativa(1L, "Cardiologia", 50.0, Map.of(), List.of(), false);

    @BeforeEach
    void limpar() {
        PROXIMOS_STATUS.clear();
        CHAMADAS.set(0);
        segurarResposta = null;
        circuitos.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @Test
    void falhaTransitoriaEhRepetidaUmaVezEDaCerto() {
        PROXIMOS_STATUS.add(529); // "overloaded" da Anthropic

        assertThat(triagem.gerar(contexto)).isEqualTo("Texto da IA.");
        assertThat(CHAMADAS.get()).as("1 falha + 1 nova tentativa").isEqualTo(2);
    }

    @Test
    void retryDesisteDepoisDaSegundaTentativa() {
        PROXIMOS_STATUS.addAll(List.of(503, 503, 503));

        assertThatThrownBy(() -> triagem.gerar(contexto)).isInstanceOf(AnthropicTransitorioException.class);
        assertThat(CHAMADAS.get()).isEqualTo(2);
    }

    @Test
    void erroDeQuemChamouNaoEhRepetido() {
        PROXIMOS_STATUS.add(400);

        assertThatThrownBy(() -> triagem.gerar(contexto)).isInstanceOf(AnthropicIndisponivelException.class)
                .isNotInstanceOf(AnthropicTransitorioException.class);
        assertThat(CHAMADAS.get()).as("4xx não se repete").isEqualTo(1);
    }

    @Test
    void relatorioTambemRepeteFalhaTransitoria() {
        PROXIMOS_STATUS.add(429);

        assertThat(relatorio.redigir(new com.marcaai.relatoriofila.ContextoRelatorio("Cardiologia", 1, 1, 0, 1, List.of())))
                .isEqualTo("Texto da IA.");
        assertThat(CHAMADAS.get()).isEqualTo(2);
    }

    @Test
    void bulkheadCheioVaiDiretoParaOFallbackSemChamarAApi() throws Exception {
        segurarResposta = new CountDownLatch(1);
        CompletableFuture<String> primeira = CompletableFuture.supplyAsync(() -> triagem.gerar(contexto));
        // Espera a primeira ocupar a única vaga do bulkhead (limite 1 neste teste).
        for (int i = 0; i < 100 && CHAMADAS.get() == 0; i++) {
            Thread.sleep(20);
        }

        assertThatThrownBy(() -> triagem.gerar(contexto)).isInstanceOf(AnthropicIndisponivelException.class)
                .hasMessageContaining("Bulkhead");
        assertThat(CHAMADAS.get()).as("a segunda nem chegou na API").isEqualTo(1);
        assertThat(circuitos.circuitBreaker(AnthropicJustificativaGenerator.RESILIENCIA).getMetrics().getNumberOfFailedCalls())
                .as("bulkhead cheio não conta como falha da API").isZero();

        segurarResposta.countDown();
        assertThat(primeira.get(5, TimeUnit.SECONDS)).isEqualTo("Texto da IA.");
    }

    @Test
    void cadaChamadaTemOSeuLimiteDeChamadaLenta() {
        assertThat(circuitos.circuitBreaker(AnthropicJustificativaGenerator.RESILIENCIA).getCircuitBreakerConfig()
                .getSlowCallDurationThreshold()).isEqualTo(Duration.ofSeconds(3));
        assertThat(circuitos.circuitBreaker(AnthropicRelatorioRedator.RESILIENCIA).getCircuitBreakerConfig()
                .getSlowCallDurationThreshold()).isEqualTo(Duration.ofSeconds(12));
    }
}
