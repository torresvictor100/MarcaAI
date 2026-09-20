package com.marcaai.relatoriofila;

import com.marcaai.atendimento.ClassificacaoRisco;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contra um servidor HTTP embutido simulando a Messages API (mesmo padrão do teste da triagem). */
class AnthropicRelatorioRedatorTest {

    private HttpServer servidor;
    private final AtomicReference<String> corpoRecebido = new AtomicReference<>();

    @AfterEach
    void pararServidor() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    private String iniciarServidor(int status, String corpoResposta) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/v1/messages", exchange -> {
            corpoRecebido.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = corpoResposta.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        servidor.start();
        return "http://localhost:" + servidor.getAddress().getPort();
    }

    private ContextoRelatorio contexto() {
        SugestaoFila sugestao = new SugestaoFila(TipoSugestao.SUBIR_NA_FILA, PrioridadeSugestao.ALTA, 30L, "Marta Aparecida",
                3, ClassificacaoRisco.VERMELHO, "Risco vermelho na posição 3.", null, null);
        return new ContextoRelatorio("Cardiologia", 4, 3, 1, 1, List.of(sugestao));
    }

    @Test
    void devolveOTextoEnviaSoDadosSemIdentificacao() throws IOException {
        String url = iniciarServidor(200, """
                {"stop_reason":"end_turn","content":[{"type":"text","text":"Priorize o encaminhamento #30."}]}""");
        AnthropicRelatorioRedator redator = new AnthropicRelatorioRedator("chave", "claude-haiku-4-5", url, 2000);

        assertThat(redator.redigir(contexto())).isEqualTo("Priorize o encaminhamento #30.");
        assertThat(corpoRecebido.get()).contains("encaminhamento #30").contains("claude-haiku-4-5")
                .doesNotContain("Marta");
    }

    @Test
    void semChaveNaoChamaARede() {
        AnthropicRelatorioRedator redator = new AnthropicRelatorioRedator("", "claude-haiku-4-5", "http://localhost:1", 1000);

        assertThatThrownBy(() -> redator.redigir(contexto())).isInstanceOf(RedacaoIndisponivelException.class);
    }

    @Test
    void erroHttpRecusaOuRespostaSemTextoViramIndisponivel() throws IOException {
        String erro = iniciarServidor(500, "{\"type\":\"error\"}");
        assertThatThrownBy(() -> new AnthropicRelatorioRedator("chave", "m", erro, 2000).redigir(contexto()))
                .isInstanceOf(RedacaoIndisponivelException.class);
        servidor.stop(0);

        String recusa = iniciarServidor(200, "{\"stop_reason\":\"refusal\",\"content\":[]}");
        assertThatThrownBy(() -> new AnthropicRelatorioRedator("chave", "m", recusa, 2000).redigir(contexto()))
                .isInstanceOf(RedacaoIndisponivelException.class);
        servidor.stop(0);

        String vazio = iniciarServidor(200, "{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"text\",\"text\":\" \"}]}");
        assertThatThrownBy(() -> new AnthropicRelatorioRedator("chave", "m", vazio, 2000).redigir(contexto()))
                .isInstanceOf(RedacaoIndisponivelException.class);
    }

    @Test
    void chaveNulaNaoChamaARede() {
        AnthropicRelatorioRedator redator = new AnthropicRelatorioRedator(null, "m", "http://localhost:1", 1000);

        assertThatThrownBy(() -> redator.redigir(contexto())).isInstanceOf(RedacaoIndisponivelException.class);
    }

    @Test
    void semSugestoesOPromptDizNenhumaESugestaoGeralNaoLevaEncaminhamentoNemRisco() throws IOException {
        String url = iniciarServidor(200, """
                {"stop_reason":"end_turn","content":[{"type":"text","text":"Fila tranquila."}]}""");
        AnthropicRelatorioRedator redator = new AnthropicRelatorioRedator("chave", "m", url, 2000);

        redator.redigir(new ContextoRelatorio("Cardiologia", 0, 0, 0, 5, List.of()));
        assertThat(corpoRecebido.get()).contains("Nenhuma.");

        SugestaoFila geral = new SugestaoFila(TipoSugestao.FALTA_DE_VAGAS, PrioridadeSugestao.MEDIA, null, null,
                null, null, "Demanda maior que as vagas.", null, null);
        redator.redigir(new ContextoRelatorio("Cardiologia", 9, 9, 0, 0, List.of(geral)));
        assertThat(corpoRecebido.get()).contains("Demanda maior que as vagas.")
                .doesNotContain("encaminhamento #").doesNotContain("| risco");
    }

    @Test
    void juntaSoOsBlocosDeTextoDaResposta() throws IOException {
        String url = iniciarServidor(200, """
                {"stop_reason":"end_turn","content":[
                  {"type":"thinking","thinking":"rascunho interno"},
                  {"type":"text","text":"Primeira parte. "},
                  {"type":"text","text":"Segunda parte."}]}""");

        String texto = new AnthropicRelatorioRedator("chave", "m", url, 2000).redigir(contexto());

        assertThat(texto).isEqualTo("Primeira parte. Segunda parte.").doesNotContain("rascunho");
    }

    @Test
    void falhaTransitoriaEErroDeQuemChamouViramExcecoesDiferentes() throws IOException {
        String sobrecarregada = iniciarServidor(529, "{\"type\":\"error\"}");
        assertThatThrownBy(() -> new AnthropicRelatorioRedator("chave", "m", sobrecarregada, 2000).redigir(contexto()))
                .isInstanceOf(RedacaoTransitoriaException.class);
        servidor.stop(0);

        String recusada = iniciarServidor(400, "{\"type\":\"error\"}");
        assertThatThrownBy(() -> new AnthropicRelatorioRedator("chave", "m", recusada, 2000).redigir(contexto()))
                .isInstanceOf(RedacaoIndisponivelException.class)
                .isNotInstanceOf(RedacaoTransitoriaException.class);
    }

    @Test
    void fallbacksDeCircuitoAbertoEBulkheadCheioViramIndisponivel() {
        AnthropicRelatorioRedator redator = new AnthropicRelatorioRedator("chave", "m", "http://localhost:1", 1000);
        var circuito = io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("x");
        var bulkhead = io.github.resilience4j.bulkhead.Bulkhead.ofDefaults("x");

        assertThatThrownBy(() -> redator.iaIndisponivel(contexto(),
                io.github.resilience4j.circuitbreaker.CallNotPermittedException.createCallNotPermittedException(circuito)))
                .isInstanceOf(RedacaoIndisponivelException.class).hasMessageContaining("Circuit breaker");
        assertThatThrownBy(() -> redator.iaIndisponivel(contexto(),
                io.github.resilience4j.bulkhead.BulkheadFullException.createBulkheadFullException(bulkhead)))
                .isInstanceOf(RedacaoIndisponivelException.class).hasMessageContaining("Bulkhead");
    }
}
