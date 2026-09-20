package com.marcaai.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credenciais de acesso")
public record LoginRequest(
        @Schema(description = "Login do usuário", example = "bruno.ubs") @NotBlank String login,
        @Schema(description = "Senha", example = "Senha123!", format = "password") @NotBlank String senha
) {
}
