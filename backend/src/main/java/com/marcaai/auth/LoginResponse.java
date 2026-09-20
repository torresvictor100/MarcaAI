package com.marcaai.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultado do login: o token e quem é o usuário")
public record LoginResponse(
        @Schema(description = "Token JWT para o header Authorization: Bearer <token>",
                example = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJicnVuby51YnMifQ.assinatura") String token,
        @Schema(description = "Id do usuário", example = "1") Long usuarioId,
        @Schema(description = "Nome de exibição", example = "Dr. Bruno (UBS)") String nome,
        @Schema(description = "Papel do usuário, que define as rotas liberadas") Papel papel,
        @Schema(description = "Id do cadastro de paciente; só vem para o papel PACIENTE", example = "1", nullable = true)
        Long pacienteId,
        @Schema(description = "Id do cadastro de profissional; vem para médico da UBS e especialista",
                example = "1", nullable = true)
        Long profissionalId
) {
}
