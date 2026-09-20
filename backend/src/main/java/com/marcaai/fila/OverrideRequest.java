package com.marcaai.fila;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Ajuste manual de posição na fila. A justificativa fica registrada para auditoria (LGPD).")
public record OverrideRequest(
        @Schema(description = "Nova posição desejada, começando em 1", example = "1")
        @NotNull @Min(1) Integer posicao,
        @Schema(description = "Motivo do ajuste (obrigatório)", example = "Piora clínica informada pela UBS.")
        @NotBlank String justificativa
) {
}
