package com.marcaai.encaminhamento;

import java.util.Locale;
import java.util.Map;

/** Nome legível dos tipos de documento, para mensagens que chegam à tela (ex.: alerta de documento faltando). */
public final class TiposDocumento {

    private static final Map<String, String> ROTULOS = Map.of(
            "GUIA_ENCAMINHAMENTO", "guia de encaminhamento",
            "EXAME_ANTERIOR", "exame anterior",
            "LAUDO", "laudo médico");

    private TiposDocumento() {
    }

    public static String rotulo(String tipo) {
        return ROTULOS.getOrDefault(tipo.toUpperCase(Locale.ROOT), tipo.toLowerCase(Locale.ROOT).replace('_', ' '));
    }
}
