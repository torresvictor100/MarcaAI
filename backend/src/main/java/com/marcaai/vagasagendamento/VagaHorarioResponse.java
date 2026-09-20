package com.marcaai.vagasagendamento;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Nomes de profissional/unidade vão junto dos ids; ficam {@code null} se o cadastro não existir. */
@Schema(description = "Vaga de horário, com os nomes resolvidos (null se o cadastro não existir)")
public record VagaHorarioResponse(
        @Schema(description = "Id da vaga", example = "40")
        Long id,
        @Schema(description = "Id da unidade", example = "2")
        Long unidadeId,
        @Schema(description = "Nome da unidade", example = "Centro Especializado Cardio", nullable = true)
        String unidadeNome,
        @Schema(description = "Id do profissional", example = "2")
        Long profissionalId,
        @Schema(description = "Nome do profissional", example = "Dra. Elisa (Cardiologia)", nullable = true)
        String profissionalNome,
        @Schema(description = "Especialidade (consulta) ou exame", example = "Cardiologia")
        String especialidadeOuExame,
        @Schema(description = "Data e hora", example = "2026-10-05T09:00:00")
        LocalDateTime dataHora,
        @Schema(description = "Situação da vaga")
        StatusVaga status
) {
    public static VagaHorarioResponse of(VagaHorario v, String unidadeNome, String profissionalNome) {
        return new VagaHorarioResponse(v.getId(), v.getUnidadeId(), unidadeNome, v.getProfissionalId(), profissionalNome,
                v.getEspecialidadeOuExame(), v.getDataHora(), v.getStatus());
    }
}
