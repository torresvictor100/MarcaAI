package com.marcaai.encaminhamento;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "CID da lista fechada usada no encaminhamento")
public record CidResponse(
        @Schema(description = "Id do CID (use em cidId ao encaminhar)", example = "1")
        Long id,
        @Schema(description = "Código CID-10", example = "I20")
        String codigo,
        @Schema(description = "Descrição do CID", example = "Angina pectoris")
        String descricao,
        @Schema(description = "Especialidades/exames para as quais o CID é compatível; fora delas a triagem bloqueia")
        List<String> especialidadesCompativeis
) {
    public static CidResponse of(Cid cid) {
        return new CidResponse(cid.getId(), cid.getCodigo(), cid.getDescricao(), cid.getEspecialidadesCompativeis());
    }
}
