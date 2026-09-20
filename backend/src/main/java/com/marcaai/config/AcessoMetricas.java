package com.marcaai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.function.Supplier;

/**
 * Quem lê {@code /actuator/prometheus} (ADR-011): o ADMIN logado ou o Prometheus, que não tem como fazer login
 * e por isso usa HTTP Basic com usuário {@value #USUARIO} e a senha {@code MARCAAI_METRICAS_TOKEN}. Basic (e não
 * Bearer) para o filtro do JWT nem tentar ler essa credencial. Sem token configurado, só o ADMIN.
 */
@Component
public class AcessoMetricas implements AuthorizationManager<RequestAuthorizationContext> {

    static final String USUARIO = "prometheus";

    private final byte[] credencialEsperada;

    public AcessoMetricas(@Value("${marcaai.metricas.token:}") String token) {
        this.credencialEsperada = token == null || token.isBlank() ? null
                : ("Basic " + Base64.getEncoder().encodeToString((USUARIO + ":" + token).getBytes(StandardCharsets.UTF_8)))
                        .getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> autenticacao, RequestAuthorizationContext contexto) {
        String header = contexto.getRequest().getHeader("Authorization");
        if (credencialEsperada != null && header != null
                // Comparação em tempo constante: não dá para descobrir o token medindo o tempo de resposta.
                && MessageDigest.isEqual(credencialEsperada, header.getBytes(StandardCharsets.UTF_8))) {
            return new AuthorizationDecision(true);
        }
        Authentication atual = autenticacao.get();
        boolean admin = atual != null && atual.isAuthenticated()
                && atual.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return new AuthorizationDecision(admin);
    }
}
