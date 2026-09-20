package com.marcaai.encaminhamento;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Encaminhamento e sua situação atual")
public record EncaminhamentoResponse(
        @Schema(description = "Id do encaminhamento", example = "5")
        Long id,
        @Schema(description = "Id do atendimento de origem", example = "1")
        Long atendimentoId,
        @Schema(description = "Consulta com especialista ou exame")
        TipoEncaminhamento tipo,
        @Schema(description = "Especialidade (consulta) ou exame", example = "Cardiologia")
        String especialidadeOuExame,
        @Schema(description = "Id do CID", example = "1")
        Long cidId,
        @Schema(description = "Situação: AGUARDANDO_DOCUMENTOS → EM_ANALISE → NA_FILA → AGENDADO → REALIZADO; ou BLOQUEADO_REVISAO (irregularidade bloqueante) ou CANCELADO")
        StatusEncaminhamento status,
        @Schema(description = "Marcado como urgente pelo médico", example = "false")
        boolean urgente,
        @Schema(description = "Justificativa da urgência", example = "Angina instável, dor em repouso.", nullable = true)
        String justificativaUrgencia
) {
    public static EncaminhamentoResponse of(Encaminhamento e) {
        return new EncaminhamentoResponse(
                e.getId(), e.getAtendimentoId(), e.getTipo(), e.getEspecialidadeOuExame(),
                e.getCidId(), e.getStatus(), e.isUrgente(), e.getJustificativaUrgencia());
    }
}
