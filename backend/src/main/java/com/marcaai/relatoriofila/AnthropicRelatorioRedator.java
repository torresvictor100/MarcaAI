package com.marcaai.relatoriofila;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.marcaai.triagemia.AnthropicTransitorioException;
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
 * Redige o relatório via Messages API da Anthropic (mesma integração HTTP do módulo triagem-ia, TECH-SPEC).
 * Recebe as sugestões já decididas pelo motor de regras e só escreve o texto (ADR-008). Por LGPD, o prompt
 * leva só dados sem identificação: número do encaminhamento, risco, posição e o motivo da regra — nunca nome.
 *
 * <p>Mesma proteção da justificativa (Retry, Circuit Breaker e Bulkhead, ADR-011), em instâncias próprias: o
 * relatório é legitimamente mais lento (texto maior), então o limite de "chamada lenta" é outro. Circuito aberto
 * ou bulkhead cheio caem direto no texto-padrão.
 */
@Component
public class AnthropicRelatorioRedator implements RedatorRelatorio {

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnthropicRelatorioRedator(
            @Value("${marcaai.anthropic.api-key}") String apiKey,
            @Value("${marcaai.anthropic.model}") String model,
            @Value("${marcaai.anthropic.base-url}") String baseUrl,
            @Value("${marcaai.anthropic.relatorio-timeout-ms:15000}") int timeoutMs) {
        this.apiKey = apiKey;
        this.model = model;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    /** Nome das instâncias de Retry, Circuit Breaker e Bulkhead em {@code resilience4j.*.instances.*}. */
    public static final String RESILIENCIA = "anthropic-relatorio";

    @Override
    @Retry(name = RESILIENCIA)
    @CircuitBreaker(name = RESILIENCIA, fallbackMethod = "iaIndisponivel")
    @Bulkhead(name = RESILIENCIA)
    public String redigir(ContextoRelatorio contexto) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new RedacaoIndisponivelException("ANTHROPIC_API_KEY não configurada");
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("max_tokens", 1500);
            body.put("system", """
                    Você escreve relatórios para a secretaria de saúde no sistema MarcaAI, que organiza filas de \
                    encaminhamento do SUS. Um motor de regras já decidiu quais pontos merecem atenção. Explique esses \
                    pontos em português do Brasil, de forma objetiva, começando pelos de prioridade alta. Use somente \
                    os dados recebidos: não invente pacientes, datas, números nem sugestões novas, e não diga que a \
                    fila foi alterada — a decisão é sempre da secretaria. Use texto simples, com parágrafos curtos e \
                    listas com hífen; sem títulos em markdown.""");
            var userMessage = body.putArray("messages").addObject();
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
        } catch (RedacaoIndisponivelException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            if (AnthropicTransitorioException.statusTransitorio(ex.getStatusCode().value())) {
                throw new RedacaoTransitoriaException("API da Anthropic respondeu " + ex.getStatusCode().value(), ex);
            }
            throw new RedacaoIndisponivelException("API da Anthropic recusou a chamada: " + ex.getStatusCode().value(), ex);
        } catch (Exception ex) {
            throw new RedacaoIndisponivelException("Falha ao chamar a API da Anthropic: " + ex.getMessage(), ex);
        }
    }

    private String montarPrompt(ContextoRelatorio contexto) {
        String sugestoes = contexto.sugestoes().isEmpty()
                ? "Nenhuma."
                : contexto.sugestoes().stream()
                        .map(s -> "- [%s] %s%s%s: %s".formatted(
                                s.prioridade(),
                                s.tipo(),
                                s.encaminhamentoId() == null ? "" : " | encaminhamento #" + s.encaminhamentoId(),
                                s.classificacaoRisco() == null ? "" : " | risco " + s.classificacaoRisco(),
                                s.motivo()))
                        .collect(Collectors.joining("\n"));
        return """
                Escreva o relatório de sugestões da fila abaixo em até 250 palavras: um parágrafo de visão geral e, \
                em seguida, o que a secretaria deveria olhar primeiro. Refira-se aos pacientes pelo número do \
                encaminhamento.

                Especialidade/exame: %s
                Pacientes na fila: %d (%d aguardando agendamento, %d agendados)
                Vagas livres: %d
                Pontos de atenção encontrados pelo motor de regras:
                %s
                """.formatted(contexto.especialidadeOuExame(), contexto.totalNaFila(), contexto.aguardandoAgendamento(),
                contexto.agendados(), contexto.vagasLivres(), sugestoes);
    }

    private String extrairTexto(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        if ("refusal".equals(root.path("stop_reason").asText())) {
            throw new RedacaoIndisponivelException("A API da Anthropic recusou a solicitação");
        }
        StringBuilder texto = new StringBuilder();
        for (JsonNode bloco : root.path("content")) {
            if ("text".equals(bloco.path("type").asText())) {
                texto.append(bloco.path("text").asText(""));
            }
        }
        if (texto.toString().isBlank()) {
            throw new RedacaoIndisponivelException("Resposta da Anthropic sem texto utilizável");
        }
        return texto.toString().trim();
    }

    /** Fallback para circuito aberto; as demais falhas seguem como estão. */
    String iaIndisponivel(ContextoRelatorio contexto, CallNotPermittedException ex) {
        throw new RedacaoIndisponivelException("Circuit breaker da Anthropic aberto: " + ex.getMessage(), ex);
    }

    /** Fallback para o bulkhead cheio: já há relatórios demais sendo redigidos, vai direto para o texto-padrão. */
    String iaIndisponivel(ContextoRelatorio contexto, BulkheadFullException ex) {
        throw new RedacaoIndisponivelException("Bulkhead da Anthropic cheio: " + ex.getMessage(), ex);
    }
}
