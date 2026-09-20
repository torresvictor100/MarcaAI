package com.marcaai.relatoriofila;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Relatório de sugestões de uma fila. As sugestões são decididas por regras; a IA só redige o texto (ADR-008). Não altera a fila.")
public record RelatorioFilaResponse(
        @Schema(description = "Id do relatório", example = "3")
        Long id,
        @Schema(description = "Especialidade (consulta) ou exame", example = "Cardiologia")
        String especialidadeOuExame,
        @Schema(description = "Quando foi gerado", example = "2026-10-05T09:00:00")
        LocalDateTime geradoEm,
        @Schema(description = "Login de quem gerou", example = "secretaria")
        String geradoPor,
        @Schema(description = "Pacientes na fila no momento da geração", example = "16")
        int totalNaFila,
        @Schema(description = "Quem redigiu o texto: IA (Anthropic) ou MODELO (texto padrão, sem IA disponível)")
        OrigemTexto origemTexto,
        @Schema(description = "Texto do relatório para a secretaria", example = "A fila de Cardiologia tem 3 pacientes de risco vermelho...")
        String texto,
        @Schema(description = "Sugestões decididas pelo motor de regras")
        List<SugestaoFila> sugestoes
) {
}
