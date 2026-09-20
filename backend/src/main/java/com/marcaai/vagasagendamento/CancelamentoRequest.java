package com.marcaai.vagasagendamento;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Cancelamento de agendamento")
public record CancelamentoRequest(
        @Schema(description = "Motivo (obrigatório, fica no histórico para auditoria)", example = "Paciente pediu para desmarcar.")
        @NotBlank String motivo
) {
}
