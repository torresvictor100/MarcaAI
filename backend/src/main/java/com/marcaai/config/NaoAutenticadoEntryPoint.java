package com.marcaai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Requisição sem token (ou com token inválido/expirado) recebe 401, não 403 — 403 fica só para quem está
 * autenticado mas não tem permissão. Mesmo envelope JSON do {@link GlobalExceptionHandler}.
 */
@Component
@RequiredArgsConstructor
public class NaoAutenticadoEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        LogSeguranca.naoAutenticado(request);
        RespostaErro.escrever(response, objectMapper, CodigoErro.NAO_AUTENTICADO,
                "Sessão ausente, inválida ou expirada. Faça login novamente.", request);
    }
}
