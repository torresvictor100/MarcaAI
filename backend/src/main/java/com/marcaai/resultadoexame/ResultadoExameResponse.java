package com.marcaai.resultadoexame;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Resultado registrado")
public record ResultadoExameResponse(
        @Schema(description = "Id do resultado", example = "1")
        Long id,
        @Schema(description = "Id do encaminhamento", example = "5")
        Long encaminhamentoId,
        @Schema(description = "Referência do laudo/relatório", example = "laudos/hemograma-5.pdf")
        String referenciaArquivo,
        @Schema(description = "Data do resultado", example = "2026-10-05")
        LocalDate dataResultado,
        @Schema(description = "Observações", example = "Sem alterações relevantes.", nullable = true)
        String observacoes
) {
    public static ResultadoExameResponse of(ResultadoExame r) {
        return new ResultadoExameResponse(r.getId(), r.getEncaminhamentoId(), r.getReferenciaArquivo(), r.getDataResultado(), r.getObservacoes());
    }
}
