package com.marcaai.relatoriofila;

import com.marcaai.atendimento.ClassificacaoRisco;
import com.marcaai.encaminhamento.StatusEncaminhamento;

import java.time.LocalDateTime;

/** O que o motor de sugestões precisa saber de cada paciente da fila. {@code dataAgendamento} só se agendado. */
public record ItemAnalise(
        Long encaminhamentoId,
        String pacienteNome,
        int posicao,
        StatusEncaminhamento status,
        ClassificacaoRisco risco,
        boolean urgente,
        LocalDateTime dataAtendimento,
        LocalDateTime dataAgendamento
) {
}
