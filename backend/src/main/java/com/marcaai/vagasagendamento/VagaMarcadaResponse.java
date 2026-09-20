package com.marcaai.vagasagendamento;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Vaga já ocupada, com quem está nela. Nomes vêm resolvidos para a tela não precisar de uma chamada por
 * id; ficam {@code null} se o cadastro não existir.
 */
@Schema(description = "Vaga já ocupada, com quem está nela (nomes null se o cadastro não existir)")
public record VagaMarcadaResponse(
        @Schema(description = "Id da vaga", example = "40")
        Long vagaId,
        @Schema(description = "Data e hora", example = "2026-10-05T09:00:00")
        LocalDateTime dataHora,
        @Schema(description = "Especialidade (consulta) ou exame", example = "Cardiologia")
        String especialidadeOuExame,
        @Schema(description = "Profissional", example = "Dra. Elisa (Cardiologia)", nullable = true)
        String profissionalNome,
        @Schema(description = "Unidade", example = "Centro Especializado Cardio", nullable = true)
        String unidadeNome,
        @Schema(description = "Id do agendamento", example = "12")
        Long agendamentoId,
        @Schema(description = "Situação do agendamento")
        StatusAgendamento statusAgendamento,
        @Schema(description = "Login de quem agendou", example = "secretaria")
        String agendadoPor,
        @Schema(description = "Id do encaminhamento", example = "5")
        Long encaminhamentoId,
        @Schema(description = "Paciente", example = "João Normal", nullable = true)
        String pacienteNome,
        @Schema(description = "Quando o paciente confirmou presença", example = "2026-10-05T09:00:00", nullable = true)
        LocalDateTime presencaConfirmadaEm
) {
}
