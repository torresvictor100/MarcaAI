package com.marcaai.config;

import com.marcaai.auth.Papel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthContextTest {

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void retornaOPrincipalQuandoAutenticado() {
        MarcaAiPrincipal principal = new MarcaAiPrincipal(1L, "bruno.ubs", Papel.MEDICO_UBS);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of()));

        assertThat(AuthContext.atual()).isEqualTo(principal);
    }

    @Test
    void lancaExcecaoQuandoPrincipalNaoEhDoTipoEsperado() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("um-principal-qualquer", null, java.util.List.of()));

        assertThatThrownBy(AuthContext::atual).isInstanceOf(NaoAutenticadoException.class);
    }

    @Test
    void semAutenticacaoNenhumaDa401NaoNullPointer() {
        assertThatThrownBy(AuthContext::atual).isInstanceOf(NaoAutenticadoException.class);
    }
}
