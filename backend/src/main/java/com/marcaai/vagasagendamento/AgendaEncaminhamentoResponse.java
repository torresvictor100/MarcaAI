package com.marcaai.vagasagendamento;

import com.marcaai.encaminhamento.StatusEncaminhamento;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Um encaminhamento na agenda do especialista (agendado numa vaga dele), com o nome do paciente resolvido. */
@Schema(description = "Encaminhamento agendado numa vaga do especialista logado")
public record AgendaEncaminhamentoResponse(
        @Schema(description = "Id do agendamento", example = "12")
        Long agendamentoId,
        @Schema(description = "Id do encaminhamento", example = "5")
        Long encaminhamentoId,
        @Schema(description = "Data e hora da consulta", example = "2026-10-05T09:00:00")
        LocalDateTime dataHora,
        @Schema(description = "Nome do paciente", example = "João Normal", nullable = true)
        String pacienteNome,
        @Schema(description = "Especialidade (consulta) ou exame", example = "Cardiologia")
        String especialidadeOuExame,
        @Schema(description = "Unidade da consulta", example = "Centro Especializado Cardio", nullable = true)
        String unidadeNome,
        @Schema(description = "AGENDADO (a atender) ou REALIZADO (atendido)")
        StatusEncaminhamento statusEncaminhamento,
        @Schema(description = "Marcado como urgente pelo médico da UBS", example = "false")
        boolean urgente,
        @Schema(description = "Quando o paciente confirmou presença (null se ainda não confirmou)", example = "2026-10-05T09:00:00", nullable = true)
        LocalDateTime presencaConfirmadaEm
) {
}
