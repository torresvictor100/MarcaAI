package com.marcaai.resultadoexame;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "Resultado do exame ou registro da consulta realizada. Tira o paciente da fila.")
public record ResultadoExameRequest(
        @Schema(description = "Referência do laudo/relatório no repositório de documentos", example = "laudos/hemograma-5.pdf")
        @NotBlank String referenciaArquivo,
        @Schema(description = "Data do resultado ou da consulta", example = "2026-10-05")
        @NotNull LocalDate dataResultado,
        @Schema(description = "Observações do profissional", example = "Sem alterações relevantes.", nullable = true)
        String observacoes
) {
}
