package com.marcaai.relatoriofila;

import java.util.List;

/** Entrada para redigir o texto do relatório: as sugestões já decididas pelo {@link MotorSugestoesFila}. */
public record ContextoRelatorio(
        String especialidadeOuExame,
        int totalNaFila,
        int aguardandoAgendamento,
        int agendados,
        int vagasLivres,
        List<SugestaoFila> sugestoes
) {
}
