package com.marcaai.encaminhamento;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "Documento anexado ao encaminhamento. Só a referência do arquivo, sem o conteúdo.")
public record DocumentoRequest(
        @Schema(description = "Tipo do documento. Os exigidos por especialidade estão em GET /especialidades/{especialidade}/documentos-exigidos (ex.: GUIA_ENCAMINHAMENTO, EXAME_ANTERIOR, LAUDO)", example = "LAUDO")
        @NotBlank String tipo,
        @Schema(description = "Referência do arquivo no repositório de documentos", example = "docs/laudo-ecg-joao.pdf")
        @NotBlank String referenciaArquivo,
        @Schema(description = "Data de emissão", example = "2026-09-20")
        @NotNull LocalDate dataEmissao,
        @Schema(description = "Validade; documento vencido gera irregularidade na triagem", example = "2026-12-20", nullable = true)
        LocalDate validade
) {
}
