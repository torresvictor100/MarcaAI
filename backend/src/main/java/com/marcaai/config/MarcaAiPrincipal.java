package com.marcaai.config;

import com.marcaai.auth.Papel;

/** Principal autenticado extraído do JWT, disponível via SecurityContext em qualquer módulo. */
public record MarcaAiPrincipal(Long usuarioId, String login, Papel papel) {
}
