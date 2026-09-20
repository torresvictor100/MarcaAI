package com.marcaai.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TentativasLoginServiceTest {

    private final AtomicReference<Instant> agora = new AtomicReference<>(Instant.parse("2026-09-24T12:00:00Z"));
    private TentativasLoginService tentativas;

    @BeforeEach
    void setUp() {
        Clock relogio = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return agora.get(); }
        };
        tentativas = new TentativasLoginService(relogio);
    }

    private void falhar(String login, int vezes) {
        for (int i = 0; i < vezes; i++) {
            tentativas.registrarFalha(login);
        }
    }

    @Test
    void bloqueiaDepoisDoLimiteDeFalhasSemDiferenciarMaiusculas() {
        falhar("bruno.ubs", TentativasLoginService.MAX_FALHAS - 1);
        assertThatCode(() -> tentativas.verificarBloqueio("bruno.ubs")).doesNotThrowAnyException();

        tentativas.registrarFalha("Bruno.UBS ");
        assertThatThrownBy(() -> tentativas.verificarBloqueio("bruno.ubs")).isInstanceOf(LoginBloqueadoException.class);
        assertThatCode(() -> tentativas.verificarBloqueio("outro.login")).doesNotThrowAnyException();
    }

    @Test
    void bloqueioVenceEAContagemRecomeca() {
        falhar("bruno.ubs", TentativasLoginService.MAX_FALHAS);
        agora.set(agora.get().plus(TentativasLoginService.BLOQUEIO));
        assertThatCode(() -> tentativas.verificarBloqueio("bruno.ubs")).doesNotThrowAnyException();

        tentativas.registrarFalha("bruno.ubs");
        assertThatCode(() -> tentativas.verificarBloqueio("bruno.ubs")).doesNotThrowAnyException();
    }

    @Test
    void sucessoZeraAsFalhas() {
        falhar("bruno.ubs", TentativasLoginService.MAX_FALHAS - 1);
        tentativas.registrarSucesso("bruno.ubs");
        tentativas.registrarFalha("bruno.ubs");
        assertThatCode(() -> tentativas.verificarBloqueio("bruno.ubs")).doesNotThrowAnyException();
    }

    @Test
    void naoCresceSemLimiteComLoginsInventados() {
        for (int i = 0; i < 1_500; i++) {
            tentativas.registrarFalha("inventado-" + i);
        }
        falhar(null, TentativasLoginService.MAX_FALHAS);
        assertThatThrownBy(() -> tentativas.verificarBloqueio(null)).isInstanceOf(LoginBloqueadoException.class);
    }

    @Test
    void falharDuranteOBloqueioMantemOBloqueio() {
        falhar("bruno.ubs", TentativasLoginService.MAX_FALHAS);
        agora.set(agora.get().plusSeconds(60));

        tentativas.registrarFalha("bruno.ubs");

        assertThatThrownBy(() -> tentativas.verificarBloqueio("bruno.ubs")).isInstanceOf(LoginBloqueadoException.class);
    }

    @Test
    void limpezaDescartaBloqueioVencidoMasPreservaQuemAindaEstaBloqueado() {
        falhar("antigo", TentativasLoginService.MAX_FALHAS);
        agora.set(agora.get().plus(TentativasLoginService.BLOQUEIO));
        falhar("vitima", TentativasLoginService.MAX_FALHAS);

        for (int i = 0; i < 1_500; i++) {
            tentativas.registrarFalha("inventado-" + i);
        }

        assertThatThrownBy(() -> tentativas.verificarBloqueio("vitima")).isInstanceOf(LoginBloqueadoException.class);
        assertThatCode(() -> tentativas.verificarBloqueio("antigo")).doesNotThrowAnyException();
    }
}
