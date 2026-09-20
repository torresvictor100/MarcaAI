package com.marcaai.vagasagendamento;

import com.marcaai.encaminhamento.StatusEncaminhamento;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Vaga do especialista, livre ou ocupada; nas ocupadas vêm o encaminhamento e o paciente. */
@Schema(description = "Vaga do especialista logado; nas ocupadas vêm o encaminhamento e o paciente")
public record AgendaVagaResponse(
        @Schema(description = "Id da vaga", example = "40")
        Long vagaId,
        @Schema(description = "Data e hora", example = "2026-10-05T09:00:00")
        LocalDateTime dataHora,
        @Schema(description = "Especialidade (consulta) ou exame", example = "Cardiologia")
        String especialidadeOuExame,
        @Schema(description = "Unidade", example = "Centro Especializado Cardio", nullable = true)
        String unidadeNome,
        @Schema(description = "Situação da vaga")
        StatusVaga status,
        @Schema(description = "Id do agendamento (só em vaga ocupada)", example = "12", nullable = true)
        Long agendamentoId,
        @Schema(description = "Id do encaminhamento (só em vaga ocupada)", example = "5", nullable = true)
        Long encaminhamentoId,
        @Schema(description = "Paciente (só em vaga ocupada)", example = "João Normal", nullable = true)
        String pacienteNome,
        @Schema(description = "Situação do encaminhamento (só em vaga ocupada)", nullable = true)
        StatusEncaminhamento statusEncaminhamento,
        @Schema(description = "Quando o paciente confirmou presença", example = "2026-10-05T09:00:00", nullable = true)
        LocalDateTime presencaConfirmadaEm
) {
}
