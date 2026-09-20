package com.marcaai.encaminhamento;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Documento anexado ao encaminhamento")
public record DocumentoResponse(
        @Schema(description = "Id do documento", example = "10")
        Long id,
        @Schema(description = "Id do encaminhamento", example = "5")
        Long encaminhamentoId,
        @Schema(description = "Tipo do documento", example = "LAUDO")
        String tipo,
        @Schema(description = "Referência do arquivo", example = "docs/laudo-ecg-joao.pdf")
        String referenciaArquivo,
        @Schema(description = "Data de emissão", example = "2026-09-20")
        LocalDate dataEmissao,
        @Schema(description = "Validade", example = "2026-12-20", nullable = true)
        LocalDate validade
) {
    public static DocumentoResponse of(Documento d) {
        return new DocumentoResponse(d.getId(), d.getEncaminhamentoId(), d.getTipo(), d.getReferenciaArquivo(), d.getDataEmissao(), d.getValidade());
    }
}
