package com.marcaai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marcaai.auth.Papel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LimiteRequisicoesFilterTest {

    private final AtomicReference<Instant> agora = new AtomicReference<>(Instant.parse("2026-09-24T12:00:10Z"));
    private final Clock relogio = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return agora.get(); }
    };
    private final LimiteRequisicoesFilter filtro = new LimiteRequisicoesFilter(3, 2, new ObjectMapper(), relogio);

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse chamar(String ip, String metodo, String rota) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, rota);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filtro.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static void logadoComo(long usuarioId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new MarcaAiPrincipal(usuarioId, "u" + usuarioId, Papel.MEDICO_UBS), null, List.of()));
    }

    @Test
    void semLoginContaPorIpEPassouDoLimiteDa429ComRetryAfter() throws Exception {
        assertThat(chamar("10.0.0.1", "POST", "/auth/login").getStatus()).isEqualTo(200);
        assertThat(chamar("10.0.0.1", "POST", "/auth/login").getStatus()).isEqualTo(200);

        MockHttpServletResponse bloqueada = chamar("10.0.0.1", "POST", "/auth/login");

        assertThat(bloqueada.getStatus()).isEqualTo(429);
        assertThat(bloqueada.getHeader("Retry-After")).isEqualTo("50");
        assertThat(bloqueada.getContentAsString()).contains("\"status\":429");
        assertThat(chamar("10.0.0.2", "POST", "/auth/login").getStatus()).as("outro IP tem a própria cota").isEqualTo(200);
    }

    @Test
    void logadoContaPorUsuarioComLimiteProprio() throws Exception {
        logadoComo(7L);
        for (int i = 0; i < 3; i++) {
            assertThat(chamar("10.0.0.1", "GET", "/fila/todas").getStatus()).isEqualTo(200);
        }
        assertThat(chamar("10.0.0.1", "GET", "/fila/todas").getStatus()).isEqualTo(429);

        logadoComo(8L);
        assertThat(chamar("10.0.0.1", "GET", "/fila/todas").getStatus()).as("outro usuário, mesmo IP").isEqualTo(200);
    }

    @Test
    void cotaVoltaNoMinutoSeguinte() throws Exception {
        chamar("10.0.0.1", "GET", "/x");
        chamar("10.0.0.1", "GET", "/x");
        assertThat(chamar("10.0.0.1", "GET", "/x").getStatus()).isEqualTo(429);

        agora.set(agora.get().plusSeconds(60));

        assertThat(chamar("10.0.0.1", "GET", "/x").getStatus()).isEqualTo(200);
    }

    @Test
    void preflightEHealthCheckNaoContam() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(chamar("10.0.0.1", "OPTIONS", "/fila/todas").getStatus()).isEqualTo(200);
            assertThat(chamar("10.0.0.1", "GET", "/actuator/health").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void naoCresceSemLimiteComMuitosIps() throws Exception {
        for (int i = 0; i < 10_050; i++) {
            chamar("ip-" + i, "GET", "/x");
        }
        agora.set(agora.get().plusSeconds(60));
        chamar("ip-novo", "GET", "/x");
        for (int i = 0; i < 10; i++) {
            chamar("ip-novo-" + i, "GET", "/x");
        }
        assertThat(chamar("ip-0", "GET", "/x").getStatus()).isEqualTo(200);
    }
}
