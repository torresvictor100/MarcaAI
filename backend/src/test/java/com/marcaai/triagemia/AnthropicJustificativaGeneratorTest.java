package com.marcaai.triagemia;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa {@link AnthropicJustificativaGenerator} contra um servidor HTTP embutido (JDK puro, sem
 * dependência extra) simulando a API da Anthropic — cobre o caminho feliz e os caminhos de falha que
 * fazem o {@link CompositeJustificativaGenerator} cair no fallback (ADR-004).
 */
class AnthropicJustificativaGeneratorTest {

    private HttpServer servidor;
    private String corpoRecebido;

    @AfterEach
    void pararServidor() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    private String iniciarServidor(int status, String corpoResposta) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/v1/messages", exchange -> {
            corpoRecebido = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
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

    private ContextoJustificativa contextoDeExemplo() {
        return new ContextoJustificativa(
                1L, "Cardiologia", 75.0,
                Map.of("classificacaoRisco", "AMARELO", "pesoRisco", 50.0),
                List.of(), false);
    }

    @Test
    void semChaveConfiguradaLancaExcecaoImediatamenteSemChamarRede() {
        AnthropicJustificativaGenerator generator = new AnthropicJustificativaGenerator(
                "", "claude-haiku-4-5", "http://localhost:1", 1000);

        assertThatThrownBy(() -> generator.gerar(contextoDeExemplo()))
                .isInstanceOf(AnthropicIndisponivelException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }

    @Test
    void respostaValidaExtraiOTextoDoContent() throws IOException {
        String baseUrl = iniciarServidor(200, """
                {"content":[{"type":"text","text":"Score alto, priorize este caso."}]}
                """);
        AnthropicJustificativaGenerator generator = new AnthropicJustificativaGenerator(
                "chave-de-teste", "claude-haiku-4-5", baseUrl, 3000);

        String justificativa = generator.gerar(contextoDeExemplo());

        assertThat(justificativa).isEqualTo("Score alto, priorize este caso.");
    }

    @Test
    void respostaSemContentUtilizavelLancaExcecao() throws IOException {
        String baseUrl = iniciarServidor(200, """
                {"content":[]}
                """);
        AnthropicJustificativaGenerator generator = new AnthropicJustificativaGenerator(
                "chave-de-teste", "claude-haiku-4-5", baseUrl, 3000);

        assertThatThrownBy(() -> generator.gerar(contextoDeExemplo()))
                .isInstanceOf(AnthropicIndisponivelException.class);
    }

    @Test
    void respostaDeErroHttpLancaExcecao() throws IOException {
        String baseUrl = iniciarServidor(500, "{\"error\":\"boom\"}");
        AnthropicJustificativaGenerator generator = new AnthropicJustificativaGenerator(
                "chave-de-teste", "claude-haiku-4-5", baseUrl, 3000);

        assertThatThrownBy(() -> generator.gerar(contextoDeExemplo()))
                .isInstanceOf(AnthropicIndisponivelException.class);
    }

    @Test
    void servidorInalcancavelLancaExcecaoAposTimeout() {
        AnthropicJustificativaGenerator generator = new AnthropicJustificativaGenerator(
                "chave-de-teste", "claude-haiku-4-5", "http://localhost:1", 500);

        assertThatThrownBy(() -> generator.gerar(contextoDeExemplo()))
                .isInstanceOf(AnthropicIndisponivelException.class);
    }

    @Test
    void compositeCaiParaTemplateQuandoAnthropicFalha() {
        AnthropicJustificativaGenerator anthropic = new AnthropicJustificativaGenerator(
                "", "claude-haiku-4-5", "http://localhost:1", 500);
        TemplateJustificativaGenerator template = new TemplateJustificativaGenerator();
        CompositeJustificativaGenerator composite = new CompositeJustificativaGenerator(anthropic, template);

        String resultado = composite.gerar(contextoDeExemplo());

        assertThat(resultado).contains("Score de prioridade");
    }

    @Test
    void chaveNulaTambemLancaExcecaoSemChamarRede() {
        AnthropicJustificativaGenerator generator = new AnthropicJustificativaGenerator(
                null, "claude-haiku-4-5", "http://localhost:1", 1000);

        assertThatThrownBy(() -> generator.gerar(contextoDeExemplo()))
                .isInstanceOf(AnthropicIndisponivelException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }

    @Test
    void promptLevaIrregularidadesEBloqueioDecididosPeloMotorDeRegras() throws IOException {
        String baseUrl = iniciarServidor(200, """
                {"content":[{"type":"text","text":"  Bloqueado: falta exame.  "}]}
                """);
        AnthropicJustificativaGenerator generator = new AnthropicJustificativaGenerator(
                "chave-de-teste", "claude-haiku-4-5", baseUrl, 3000);
        ContextoJustificativa bloqueado = new ContextoJustificativa(
                2L, "Ortopedia", 10.0, Map.of("pesoRisco", 10.0),
                List.of(new Irregularidade("Exame obrigatório ausente", Severidade.BLOQUEANTE)), true);

        String justificativa = generator.gerar(bloqueado);

        assertThat(justificativa).as("texto aparado").isEqualTo("Bloqueado: falta exame.");
        assertThat(corpoRecebido)
                .contains("Bloqueado para revisão humana: sim")
                .contains("[BLOQUEANTE] Exame obrigatório ausente")
                .contains("NÃO deve mudar o");
    }

    @Test
    void semIrregularidadesOPromptDizNenhuma() throws IOException {
        String baseUrl = iniciarServidor(200, """
                {"content":[{"type":"text","text":"ok"}]}
                """);
        new AnthropicJustificativaGenerator("chave-de-teste", "claude-haiku-4-5", baseUrl, 3000).gerar(contextoDeExemplo());

        assertThat(corpoRecebido).contains("Nenhuma.").contains("Bloqueado para revisão humana: não");
    }

    @Test
    void respostaSemCampoContentOuComTextoEmBrancoLancaExcecao() throws IOException {
        String semContent = iniciarServidor(200, "{\"id\":\"msg_1\"}");
        AnthropicJustificativaGenerator generator = new AnthropicJustificativaGenerator(
                "chave-de-teste", "claude-haiku-4-5", semContent, 3000);
        assertThatThrownBy(() -> generator.gerar(contextoDeExemplo())).isInstanceOf(AnthropicIndisponivelException.class);
        servidor.stop(0);

        String textoEmBranco = iniciarServidor(200, """
                {"content":[{"type":"text","text":"   "}]}
                """);
        AnthropicJustificativaGenerator outro = new AnthropicJustificativaGenerator(
                "chave-de-teste", "claude-haiku-4-5", textoEmBranco, 3000);
        assertThatThrownBy(() -> outro.gerar(contextoDeExemplo())).isInstanceOf(AnthropicIndisponivelException.class);
    }

    @Test
    void compositeUsaOTextoDaAnthropicQuandoElaResponde() throws IOException {
        String baseUrl = iniciarServidor(200, """
                {"content":[{"type":"text","text":"Texto redigido pela IA."}]}
                """);
        CompositeJustificativaGenerator composite = new CompositeJustificativaGenerator(
                new AnthropicJustificativaGenerator("chave-de-teste", "claude-haiku-4-5", baseUrl, 3000),
                new TemplateJustificativaGenerator());

        assertThat(composite.gerar(contextoDeExemplo())).isEqualTo("Texto redigido pela IA.");
    }
}
