package com.marcaai.atendimento;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** Sem profissionalId: quem assina o atendimento é sempre o médico logado (ver ADR-007). */
@Schema(description = "Dados do atendimento na UBS. O médico é o usuário logado.")
public record AtendimentoRequest(
        @Schema(description = "Id do paciente atendido (busque pelo nome em GET /pacientes)", example = "2")
        @NotNull Long pacienteId,
        @Schema(description = "Id da UBS do atendimento (GET /unidades)", example = "1")
        @NotNull Long unidadeId,
        @Schema(description = "Data e hora do atendimento (horário de Brasília)", example = "2026-09-24T09:30:00")
        @NotNull LocalDateTime data,
        @Schema(description = "Anotações clínicas do médico", example = "Dor torácica aos esforços há 2 semanas.")
        String notas,
        @Schema(description = "Classificação de risco (protocolo de Manchester), do menor (AZUL) ao maior (VERMELHO)")
        @NotNull ClassificacaoRisco classificacaoRisco
) {
}
