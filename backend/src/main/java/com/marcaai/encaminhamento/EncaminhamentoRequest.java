package com.marcaai.encaminhamento;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "Encaminhamento da UBS. Nasce com pelo menos um documento (ADR-009); se os obrigatórios já vierem completos, a triagem roda na hora.")
public record EncaminhamentoRequest(
        @Schema(description = "Id de um atendimento registrado pelo médico logado", example = "1")
        @NotNull Long atendimentoId,
        @Schema(description = "Consulta com especialista ou exame")
        @NotNull TipoEncaminhamento tipo,
        @Schema(description = "Especialidade (consulta) ou exame", example = "Cardiologia")
        @NotNull String especialidadeOuExame,
        @Schema(description = "Id do CID (GET /cids?especialidade=)", example = "1")
        @NotNull Long cidId,
        @Schema(description = "Marcado como urgente pelo médico; soma bônus no score", example = "false")
        boolean urgente,
        @Schema(description = "Obrigatória quando urgente = true", example = "Angina instável, dor em repouso.", nullable = true)
        String justificativaUrgencia,
        /** O encaminhamento só nasce com pelo menos um documento anexado (ADR-009). */
        @Schema(description = "Documentos anexados na criação (pelo menos um)")
        @NotEmpty @Valid List<DocumentoRequest> documentos
) {
}
