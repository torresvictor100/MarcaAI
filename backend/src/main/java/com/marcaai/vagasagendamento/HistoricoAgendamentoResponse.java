package com.marcaai.vagasagendamento;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Uma mudança no agendamento (auditoria)")
public record HistoricoAgendamentoResponse(
        @Schema(description = "O que foi feito")
        AcaoAgendamento acao,
        @Schema(description = "Data e hora antes da mudança", example = "2026-10-05T09:00:00")
        LocalDateTime dataHoraAnterior,
        @Schema(description = "Data e hora depois da mudança (null no cancelamento)", example = "2026-10-02T14:00:00", nullable = true)
        LocalDateTime dataHoraNova,
        @Schema(description = "Motivo informado", example = "Vaga mais cedo liberada.")
        String motivo,
        @Schema(description = "Login de quem fez", example = "secretaria")
        String feitoPor,
        @Schema(description = "Quando foi feito", example = "2026-09-24T10:15:00")
        LocalDateTime feitoEm
) {
    public static HistoricoAgendamentoResponse of(HistoricoAgendamento h) {
        return new HistoricoAgendamentoResponse(h.getAcao(), h.getDataHoraAnterior(), h.getDataHoraNova(), h.getMotivo(),
                h.getFeitoPor(), h.getFeitoEm());
    }
}
