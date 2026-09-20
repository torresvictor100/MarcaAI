package com.marcaai.relatoriofila;

import com.marcaai.atendimento.ClassificacaoRisco;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Uma sugestão do relatório. Campos do paciente ficam nulos na sugestão geral (falta de vagas).
 * {@code vagaSugeridaId}/{@code vagaSugeridaDataHora} só vêm na sugestão de antecipar.
 */
@Schema(description = "Uma sugestão do relatório. Campos do paciente ficam null na sugestão geral (falta de vagas).")
public record SugestaoFila(
        @Schema(description = "Tipo: SUBIR_NA_FILA, ANTECIPAR, ESPERA_LONGA, URGENTE_SEM_VAGA ou FALTA_DE_VAGAS")
        TipoSugestao tipo,
        @Schema(description = "Prioridade da sugestão")
        PrioridadeSugestao prioridade,
        @Schema(description = "Id do encaminhamento", example = "5", nullable = true)
        Long encaminhamentoId,
        @Schema(description = "Nome do paciente", example = "João Normal", nullable = true)
        String pacienteNome,
        @Schema(description = "Posição atual na fila", example = "9", nullable = true)
        Integer posicao,
        @Schema(description = "Risco do paciente", nullable = true)
        ClassificacaoRisco classificacaoRisco,
        @Schema(description = "Por que a regra sugeriu", example = "Risco vermelho atrás de 2 pacientes de risco menor")
        String motivo,
        @Schema(description = "Vaga sugerida (só na sugestão ANTECIPAR)", example = "40", nullable = true)
        Long vagaSugeridaId,
        @Schema(description = "Data e hora da vaga sugerida (só na sugestão ANTECIPAR)", example = "2026-10-05T09:00:00", nullable = true)
        LocalDateTime vagaSugeridaDataHora
) {
}
