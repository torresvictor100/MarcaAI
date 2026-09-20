package com.marcaai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting da API (ADR-010): limite de requisições por minuto, contado por usuário logado ou, sem login,
 * por IP. Passou do limite → 429 com {@code Retry-After}. Janela fixa de um minuto, em memória (o backend
 * roda em uma instância só); complementa o bloqueio por login errado do {@code TentativasLoginService}.
 *
 * <p>Não é um bean de propósito: é criado pelo {@link SecurityConfig} e roda só dentro da cadeia do Spring
 * Security, logo depois do {@link JwtAuthenticationFilter} (já sabe quem é o usuário).
 */
public class LimiteRequisicoesFilter extends OncePerRequestFilter {

    private static final int LIMPAR_ACIMA_DE = 10_000;

    private record Janela(long minuto, int quantidade) {
    }

    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();
    private final int limiteAutenticado;
    private final int limiteAnonimo;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LimiteRequisicoesFilter(int limiteAutenticado, int limiteAnonimo, ObjectMapper objectMapper, Clock clock) {
        this.limiteAutenticado = limiteAutenticado;
        this.limiteAnonimo = limiteAnonimo;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        // Preflight do CORS e health check (usado por monitoramento) não contam.
        return "OPTIONS".equals(request.getMethod()) || request.getRequestURI().startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        boolean logado = autenticacao != null && autenticacao.getPrincipal() instanceof MarcaAiPrincipal;
        String chave = logado
                ? "usuario:" + ((MarcaAiPrincipal) autenticacao.getPrincipal()).usuarioId()
                : "ip:" + request.getRemoteAddr();
        int limite = logado ? limiteAutenticado : limiteAnonimo;

        long agora = clock.millis();
        long minuto = agora / 60_000;
        Janela janela = janelas.merge(chave, new Janela(minuto, 1),
                (atual, nova) -> atual.minuto() == minuto ? new Janela(minuto, atual.quantidade() + 1) : nova);
        if (janelas.size() > LIMPAR_ACIMA_DE) {
            janelas.entrySet().removeIf(e -> e.getValue().minuto() < minuto);
        }

        if (janela.quantidade() > limite) {
            LogSeguranca.limiteExcedido(request, chave);
            long segundosAteAProximaJanela = Math.max(1, ((minuto + 1) * 60_000 - agora + 999) / 1000);
            responderLimiteExcedido(request, response, segundosAteAProximaJanela);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void responderLimiteExcedido(HttpServletRequest request, HttpServletResponse response, long retryAfterSegundos)
            throws IOException {
        response.setHeader("Retry-After", String.valueOf(retryAfterSegundos));
        RespostaErro.escrever(response, objectMapper, CodigoErro.LIMITE_EXCEDIDO,
                "Muitas requisições em pouco tempo. Aguarde um instante e tente de novo.", request);
    }
}
