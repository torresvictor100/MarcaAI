package com.marcaai.vagasagendamento;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Usado para remarcar e para antecipar: a vaga nova e o motivo da mudança. */
@Schema(description = "Troca de vaga, para remarcar ou antecipar")
public record RemarcacaoRequest(
        @Schema(description = "Id da vaga nova: livre e da mesma especialidade", example = "41")
        @NotNull Long vagaId,
        @Schema(description = "Motivo (obrigatório, fica no histórico para auditoria)", example = "Vaga mais cedo liberada.")
        @NotBlank String motivo
) {
}
