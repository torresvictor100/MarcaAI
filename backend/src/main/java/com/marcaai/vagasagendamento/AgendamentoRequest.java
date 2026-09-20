package com.marcaai.vagasagendamento;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Marca um encaminhamento da fila numa vaga livre")
public record AgendamentoRequest(
        @Schema(description = "Id do encaminhamento (sem agendamento ativo)", example = "5")
        @NotNull Long encaminhamentoId,
        @Schema(description = "Id de uma vaga DISPONIVEL (GET /vagas?especialidade=)", example = "40")
        @NotNull Long vagaId
) {
}
