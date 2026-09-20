package com.marcaai.config;

import com.marcaai.auth.ChavesJwtDeTeste;
import com.marcaai.auth.JwtService;
import com.marcaai.auth.Papel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

/** O filtro autentica só com token válido e nunca interrompe a cadeia (quem responde 401/403 é o Security). */
class JwtAuthenticationFilterTest {

    private final JwtService jwtService = ChavesJwtDeTeste.PRINCIPAL.jwtService(60_000L);
    private final JwtAuthenticationFilter filtro = new JwtAuthenticationFilter(jwtService);

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private MockFilterChain filtrar(String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/fila");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        MockFilterChain chain = new MockFilterChain();
        filtro.doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }

    @Test
    void tokenValidoAutenticaComOPapelDoToken() throws Exception {
        String token = jwtService.gerarToken(7L, "secretaria", Papel.SECRETARIA);

        MockFilterChain chain = filtrar("Bearer " + token);

        assertThat(chain.getRequest()).as("a cadeia continua").isNotNull();
        MarcaAiPrincipal principal = AuthContext.atual();
        assertThat(principal.usuarioId()).isEqualTo(7L);
        assertThat(principal.login()).isEqualTo("secretaria");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_SECRETARIA");
    }

    @Test
    void tokenAssinadoComOutraChaveNaoAutentica() throws Exception {
        String forjado = ChavesJwtDeTeste.gerar().jwtService(60_000L).gerarToken(1L, "admin", Papel.ADMIN);

        MockFilterChain chain = filtrar("Bearer " + forjado);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void tokenExpiradoOuLixoNaoAutentica() throws Exception {
        String expirado = ChavesJwtDeTeste.PRINCIPAL.jwtService(-1_000L).gerarToken(1L, "admin", Papel.ADMIN);

        filtrar("Bearer " + expirado);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        filtrar("Bearer nao-e-um-jwt");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void semCabecalhoOuSemPrefixoBearerSegueSemAutenticar() throws Exception {
        assertThat(filtrar(null).getRequest()).isNotNull();
        assertThat(filtrar("Basic dXNlcjpzZW5oYQ==").getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
