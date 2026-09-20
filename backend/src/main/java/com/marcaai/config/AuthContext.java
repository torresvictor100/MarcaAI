package com.marcaai.config;

import org.springframework.security.core.context.SecurityContextHolder;

/** Acesso ao usuário autenticado da requisição atual, para uso nos Services de qualquer módulo. */
public final class AuthContext {

    private AuthContext() {
    }

    public static MarcaAiPrincipal atual() {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof MarcaAiPrincipal marcaAiPrincipal) {
            return marcaAiPrincipal;
        }
        // 401, não 500 (ADR-011): chegou numa operação que exige usuário logado sem haver um.
        throw new NaoAutenticadoException("Nenhum usuário autenticado no contexto atual");
    }
}
