package com.marcaai.cadastro;

import com.marcaai.shared.Profissional;
import com.marcaai.shared.TipoProfissional;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Profissional de saúde cadastrado")
public record ProfissionalResponse(
        @Schema(description = "Id do profissional", example = "2")
        Long id,
        @Schema(description = "Nome", example = "Dra. Elisa (Cardiologia)")
        String nome,
        @Schema(description = "Registro no conselho", example = "CRM-SP 222222")
        String registroConselho,
        @Schema(description = "Tipo de profissional")
        TipoProfissional tipo,
        @Schema(description = "Especialidade ou exame que atende (null para médico da UBS)", example = "Cardiologia", nullable = true)
        String especialidade
) {
    public static ProfissionalResponse of(Profissional p) {
        return new ProfissionalResponse(p.getId(), p.getNome(), p.getRegistroConselho(), p.getTipo(), p.getEspecialidade());
    }
}
