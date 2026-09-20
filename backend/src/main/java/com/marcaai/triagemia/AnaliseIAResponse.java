package com.marcaai.triagemia;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Resultado da triagem. Score, bloqueio e irregularidades vêm do motor de regras determinístico; só justificativaTexto é redigida pela IA (ADR-004).")
public record AnaliseIAResponse(
        @Schema(description = "Id do encaminhamento", example = "5")
        Long encaminhamentoId,
        @Schema(description = "Score de prioridade (maior = mais prioritário): peso do risco + tempo de espera + urgência", example = "72.5")
        double scorePrioridade,
        @Schema(description = "Se há irregularidade bloqueante; bloqueado não entra na fila (BLOQUEADO_REVISAO)", example = "false")
        boolean bloqueado,
        @Schema(description = "JSON com os fatores do score", example = "{\"classificacaoRisco\":\"AMARELO\",\"pesoRisco\":50.0,\"diasDesdeAtendimento\":3,\"bonusTempoEspera\":1.5,\"urgenteMarcadoPeloMedico\":false,\"bonusUrgencia\":0.0}")
        String fatoresConsiderados,
        @Schema(description = "JSON com a lista de irregularidades (descricao, severidade BLOQUEANTE ou ALERTA)", example = "[{\"descricao\":\"CID M54 incompatível com Cardiologia\",\"severidade\":\"BLOQUEANTE\"}]")
        String irregularidades,
        @Schema(description = "Justificativa em texto para humanos (IA ou texto padrão)", example = "Paciente de risco amarelo, sem irregularidades; entra na fila com prioridade média.")
        String justificativaTexto,
        @Schema(description = "Quando a triagem rodou", example = "2026-10-05T09:00:00")
        LocalDateTime dataAnalise
) {
    public static AnaliseIAResponse of(AnaliseIA a) {
        return new AnaliseIAResponse(
                a.getEncaminhamentoId(), a.getScorePrioridade(), a.isBloqueado(),
                a.getFatoresConsiderados(), a.getIrregularidades(), a.getJustificativaTexto(), a.getDataAnalise());
    }
}
