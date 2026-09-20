package com.marcaai.vagasagendamento;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** Nomes de profissional/unidade da vaga (e o endereço da unidade, para o paciente) vão junto (ficam {@code null} se o cadastro não existir), com o histórico de mudanças. */
@Schema(description = "Agendamento, com nomes e endereço resolvidos (null se o cadastro não existir) e o histórico de mudanças")
public record AgendamentoResponse(
        @Schema(description = "Id do agendamento", example = "12")
        Long id,
        @Schema(description = "Id do encaminhamento", example = "5")
        Long encaminhamentoId,
        @Schema(description = "Id da vaga", example = "40")
        Long vagaId,
        @Schema(description = "Data e hora marcadas", example = "2026-10-05T09:00:00")
        LocalDateTime dataHora,
        @Schema(description = "Situação do agendamento")
        StatusAgendamento status,
        @Schema(description = "Login de quem agendou", example = "secretaria")
        String agendadoPor,
        @Schema(description = "Profissional da vaga", example = "Dra. Elisa (Cardiologia)", nullable = true)
        String profissionalNome,
        @Schema(description = "Unidade da vaga", example = "Centro Especializado Cardio", nullable = true)
        String unidadeNome,
        @Schema(description = "Endereço da unidade", example = "Av. dos Especialistas, 200", nullable = true)
        String unidadeEndereco,
        @Schema(description = "Quando o paciente confirmou presença (remarcar ou antecipar zera)", example = "2026-10-05T09:00:00", nullable = true)
        LocalDateTime presencaConfirmadaEm,
        @Schema(description = "Cancelamentos, remarcações e antecipações, com motivo e autor (auditoria)")
        List<HistoricoAgendamentoResponse> historico
) {
    public static AgendamentoResponse of(Agendamento a, String profissionalNome, String unidadeNome, String unidadeEndereco,
                                         List<HistoricoAgendamentoResponse> historico) {
        return new AgendamentoResponse(a.getId(), a.getEncaminhamentoId(), a.getVagaId(), a.getDataHora(), a.getStatus(),
                a.getAgendadoPor(), profissionalNome, unidadeNome, unidadeEndereco, a.getPresencaConfirmadaEm(), historico);
    }
}
