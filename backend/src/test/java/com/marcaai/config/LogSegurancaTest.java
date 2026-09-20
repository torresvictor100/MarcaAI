package com.marcaai.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSegurancaTest {

    @Test
    void quebraDeLinhaNoValorNaoForjaLinhaNoLog() {
        assertThat(LogSeguranca.limpar("admin\nevento=LOGIN_OK login=admin"))
                .doesNotContain("\n").startsWith("admin_evento");
        assertThat(LogSeguranca.limpar("a\r\tb")).isEqualTo("a__b");
    }

    @Test
    void cortaValorLongoETrataNulo() {
        assertThat(LogSeguranca.limpar("x".repeat(500))).hasSize(81).endsWith("…");
        assertThat(LogSeguranca.limpar(null)).isEqualTo("-");
    }
}
