package com.marcaai.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freio contra força bruta no login: depois de {@value #MAX_FALHAS} senhas erradas seguidas para o mesmo
 * login, esse login fica bloqueado por {@link #BLOQUEIO}. Em memória (uma instância só, Docker Compose
 * local) — com mais de uma instância precisaria de um armazenamento compartilhado.
 */
@Service
public class TentativasLoginService {

    static final int MAX_FALHAS = 5;
    static final Duration BLOQUEIO = Duration.ofMinutes(5);
    private static final int LIMPAR_ACIMA_DE = 1_000;

    private record Falhas(int quantidade, Instant bloqueadoAte) {
    }

    private final Map<String, Falhas> falhasPorLogin = new ConcurrentHashMap<>();
    private final Clock clock;

    @Autowired
    public TentativasLoginService() {
        this(Clock.systemUTC());
    }

    TentativasLoginService(Clock clock) {
        this.clock = clock;
    }

    public void verificarBloqueio(String login) {
        Falhas falhas = falhasPorLogin.get(chave(login));
        if (falhas != null && falhas.bloqueadoAte() != null && clock.instant().isBefore(falhas.bloqueadoAte())) {
            throw new LoginBloqueadoException("Muitas tentativas de login sem sucesso. Tente novamente em alguns minutos.");
        }
    }

    public void registrarFalha(String login) {
        Instant agora = clock.instant();
        falhasPorLogin.compute(chave(login), (k, atual) -> {
            // Bloqueio vencido recomeça a contagem do zero.
            int anteriores = atual == null || (atual.bloqueadoAte() != null && !agora.isBefore(atual.bloqueadoAte()))
                    ? 0 : atual.quantidade();
            int quantidade = anteriores + 1;
            return new Falhas(quantidade, quantidade >= MAX_FALHAS ? agora.plus(BLOQUEIO) : null);
        });
        if (falhasPorLogin.size() > LIMPAR_ACIMA_DE) {
            // Evita crescer sem limite com logins inventados: descarta quem não está bloqueado agora.
            falhasPorLogin.entrySet().removeIf(e -> e.getValue().bloqueadoAte() == null || !agora.isBefore(e.getValue().bloqueadoAte()));
        }
    }

    public void registrarSucesso(String login) {
        falhasPorLogin.remove(chave(login));
    }

    private static String chave(String login) {
        return login == null ? "" : login.trim().toLowerCase(Locale.ROOT);
    }
}
