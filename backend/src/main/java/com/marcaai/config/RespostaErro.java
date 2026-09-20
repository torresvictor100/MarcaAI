package com.marcaai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Corpo único de erro da API (ADR-011), igual em todo lugar — handler de exceções, 401 do Spring Security,
 * 403 de rota, 429 do rate limiting:
 * <pre>{"timestamp", "status", "erro", "mensagem", "caminho", "campos"?}</pre>
 * {@code timestamp}, {@code status} e {@code mensagem} já existiam (o front lê {@code mensagem});
 * {@code erro} é o {@link CodigoErro}; {@code campos} só aparece em erro de validação.
 */
public final class RespostaErro {

    public record Campo(String campo, String mensagem) {
    }

    private RespostaErro() {
    }

    public static Map<String, Object> corpo(CodigoErro codigo, String mensagem, HttpServletRequest request) {
        return corpo(codigo, mensagem, request, null);
    }

    public static Map<String, Object> corpo(CodigoErro codigo, String mensagem, HttpServletRequest request, List<Campo> campos) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", codigo.status().value());
        body.put("erro", codigo.name());
        body.put("mensagem", mensagem);
        body.put("caminho", request == null ? null : request.getRequestURI());
        if (campos != null && !campos.isEmpty()) {
            body.put("campos", campos);
        }
        return body;
    }

    /** Para quem responde direto no servlet (filtros e handlers do Spring Security), fora do MVC. */
    public static void escrever(HttpServletResponse response, ObjectMapper objectMapper, CodigoErro codigo, String mensagem,
                                HttpServletRequest request) throws IOException {
        response.setStatus(codigo.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), corpo(codigo, mensagem, request));
    }
}
