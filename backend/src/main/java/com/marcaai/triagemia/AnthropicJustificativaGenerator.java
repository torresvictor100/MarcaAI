package com.marcaai.triagemia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.stream.Collectors;

/**
 * Chamada real à API da Anthropic (Messages API) só para redigir a justificativa em texto.
 * Nunca decide score ou bloqueio (ADR-004) — recebe o resultado já calculado pelo motor de regras
 * e só o transforma em linguagem natural.
 *
 * <p>Resiliência (Resilience4j, ADR-010/ADR-011), de fora para dentro:
 * <ol>
 *   <li><b>Retry</b>: repete uma vez, com backoff exponencial, só falha transitória (429/5xx/529);</li>
 *   <li><b>Circuit Breaker</b>: com muitas falhas <i>ou chamadas lentas</i> seguidas, abre e as próximas nem saem
 *   daqui por um tempo;</li>
 *   <li><b>Bulkhead</b>: no máximo N chamadas ao mesmo tempo — a triagem roda dentro da requisição do médico e da
 *   transação do banco, então IA lenta não pode prender todas as threads e conexões.</li>
 * </ol>
 * Circuito aberto ou bulkhead cheio viram {@link AnthropicIndisponivelException} na hora e o
 * {@link CompositeJustificativaGenerator} usa o template.
 */
@Component
public class AnthropicJustificativaGenerator implements JustificativaGenerator {

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnthropicJustificativaGenerator(
            @Value("${marcaai.anthropic.api-key}") String apiKey,
            @Value("${marcaai.anthropic.model}") String model,
            @Value("${marcaai.anthropic.base-url}") String baseUrl,
            @Value("${marcaai.anthropic.timeout-ms}") int timeoutMs) {
        this.apiKey = apiKey;
        this.model = model;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /** Nome das instâncias de Retry, Circuit Breaker e Bulkhead em {@code resilience4j.*.instances.*}. */
    public static final String RESILIENCIA = "anthropic-triagem";

    @Override
    @Retry(name = RESILIENCIA)
    @CircuitBreaker(name = RESILIENCIA, fallbackMethod = "iaIndisponivel")
    @Bulkhead(name = RESILIENCIA)
    public String gerar(ContextoJustificativa contexto) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AnthropicIndisponivelException("ANTHROPIC_API_KEY não configurada");
        }

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("max_tokens", 300);
            var messages = body.putArray("messages");
            var userMessage = messages.addObject();
            userMessage.put("role", "user");
            userMessage.put("content", montarPrompt(contexto));

            String responseBody = restClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return extrairTexto(responseBody);
        } catch (AnthropicIndisponivelException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            if (AnthropicTransitorioException.statusTransitorio(ex.getStatusCode().value())) {
                throw new AnthropicTransitorioException("API da Anthropic respondeu " + ex.getStatusCode().value(), ex);
            }
            throw new AnthropicIndisponivelException("API da Anthropic recusou a chamada: " + ex.getStatusCode().value(), ex);
        } catch (Exception ex) {
            throw new AnthropicIndisponivelException("Falha ao chamar a API da Anthropic: " + ex.getMessage(), ex);
        }
    }

    private String montarPrompt(ContextoJustificativa contexto) {
        String fatores = contexto.fatoresConsiderados().entrySet().stream()
                .map(e -> "- " + e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("\n"));
        String irregularidades = contexto.irregularidades().isEmpty()
                ? "Nenhuma."
                : contexto.irregularidades().stream()
                        .map(i -> "- [%s] %s".formatted(i.severidade(), i.descricao()))
                        .collect(Collectors.joining("\n"));

        return """
                Você é parte do sistema MarcaAI, que prioriza uma fila de encaminhamentos de saúde pública.
                Um motor de regras determinístico já calculou o resultado abaixo — você NÃO deve mudar o \
                score nem a decisão de bloqueio, apenas explicar em português, em até 3 frases curtas e \
                objetivas, por que este encaminhamento está nesta posição/situação. Escreva para o \
                profissional da secretaria de saúde que vai ler isso ao agendar.

                Especialidade/exame: %s
                Score de prioridade: %.1f
                Bloqueado para revisão humana: %s
                Fatores considerados pelo motor de regras:
                %s
                Irregularidades encontradas:
                %s
                """.formatted(
                contexto.especialidadeOuExame(),
                contexto.score(),
                contexto.bloqueado() ? "sim" : "não",
                fatores,
                irregularidades);
    }

    private String extrairTexto(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode content = root.path("content");
        if (content.isArray() && !content.isEmpty()) {
            String texto = content.get(0).path("text").asText("");
            if (!texto.isBlank()) {
                return texto.trim();
            }
        }
        throw new AnthropicIndisponivelException("Resposta da Anthropic sem texto utilizável: " + responseBody);
    }

    /** Fallback para circuito aberto; as demais falhas seguem como estão. */
    String iaIndisponivel(ContextoJustificativa contexto, CallNotPermittedException ex) {
        throw new AnthropicIndisponivelException("Circuit breaker da Anthropic aberto: " + ex.getMessage(), ex);
    }

    /** Fallback para o bulkhead cheio: já há chamadas demais em andamento, vai direto para o template. */
    String iaIndisponivel(ContextoJustificativa contexto, BulkheadFullException ex) {
        throw new AnthropicIndisponivelException("Bulkhead da Anthropic cheio: " + ex.getMessage(), ex);
    }
}
