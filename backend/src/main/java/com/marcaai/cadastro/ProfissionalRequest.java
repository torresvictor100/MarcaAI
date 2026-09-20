package com.marcaai.cadastro;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cadastro de especialista: a especialidade precisa estar no catálogo (é o que permite encaminhar para ela). */
@Schema(description = "Cadastro de especialista. A especialidade precisa estar no catálogo (GET /especialidades).")
public record ProfissionalRequest(
        @Schema(description = "Nome do especialista", example = "Dra. Helena Duarte")
        @NotBlank @Size(max = 200) String nome,
        @Schema(description = "Registro no conselho (CRM etc.), único no sistema; maiúsculas e minúsculas não diferenciam", example = "CRM-SP 123456")
        @NotBlank @Size(max = 50) String registroConselho,
        @Schema(description = "Especialidade do catálogo", example = "Dermatologia")
        @NotBlank String especialidade
) {
}
