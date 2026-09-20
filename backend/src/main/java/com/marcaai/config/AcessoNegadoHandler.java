package com.marcaai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 403 dado pela regra de papel da rota no {@code SecurityFilterChain} (ADR-011), antes de chegar no controller:
 * mesmo corpo de erro e mesmo log do 403 dado dentro do controller ({@link GlobalExceptionHandler}).
 */
@Component
@RequiredArgsConstructor
public class AcessoNegadoHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) throws IOException {
        LogSeguranca.acessoNegado(request);
        RespostaErro.escrever(response, objectMapper, CodigoErro.ACESSO_NEGADO, "Acesso negado para este papel/usuário", request);
    }
}
