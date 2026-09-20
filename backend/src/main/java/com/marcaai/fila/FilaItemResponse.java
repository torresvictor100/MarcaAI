package com.marcaai.fila;

import com.marcaai.atendimento.ClassificacaoRisco;
import com.marcaai.encaminhamento.StatusEncaminhamento;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Item da fila. {@code pacienteNome}, {@code statusEncaminhamento}, {@code classificacaoRisco} e
 * {@code presencaConfirmadaEm} (quando o paciente agendado confirmou que vai comparecer) só vêm
 * preenchidos nas visões da secretaria (ver {@link FilaDetalhesService}); nas demais ficam {@code null}.
 */
@Schema(description = "Item da fila. pacienteNome, statusEncaminhamento, classificacaoRisco e presencaConfirmadaEm só vêm nas visões da secretaria (GET /fila?especialidade= e GET /fila/todas); nas demais, null.")
public record FilaItemResponse(
        @Schema(description = "Id do item de fila (use no ajuste manual)", example = "7")
        Long itemId,
        @Schema(description = "Id do encaminhamento", example = "5")
        Long encaminhamentoId,
        @Schema(description = "Especialidade (consulta) ou exame", example = "Cardiologia")
        String especialidadeOuExame,
        @Schema(description = "Posição atual na fila, começando em 1", example = "3")
        int posicao,
        @Schema(description = "Score de prioridade calculado pelo motor de regras (maior = mais prioritário)", example = "72.5")
        double scoreAtual,
        @Schema(description = "Se a posição foi ajustada manualmente pela secretaria", example = "false")
        boolean overrideManual,
        @Schema(description = "Justificativa do ajuste manual (auditoria)", example = "Piora clínica informada pela UBS.", nullable = true)
        String justificativaOverride,
        @Schema(description = "Nome do paciente (só na visão da secretaria)", example = "João Normal", nullable = true)
        String pacienteNome,
        @Schema(description = "Situação do encaminhamento (só na visão da secretaria)", nullable = true)
        StatusEncaminhamento statusEncaminhamento,
        @Schema(description = "Risco do atendimento de origem (só na visão da secretaria)", nullable = true)
        ClassificacaoRisco classificacaoRisco,
        @Schema(description = "Quando o paciente agendado confirmou presença (só na visão da secretaria)", example = "2026-10-05T09:00:00", nullable = true)
        LocalDateTime presencaConfirmadaEm
) {
    public static FilaItemResponse of(ItemFila item, int posicao) {
        return new FilaItemResponse(
                item.getId(), item.getEncaminhamentoId(), item.getEspecialidadeOuExame(),
                posicao, item.getScoreAtual(), item.isOverrideManual(), item.getJustificativaOverride(),
                null, null, null, null);
    }

    public FilaItemResponse comDetalhes(String pacienteNome, StatusEncaminhamento status, ClassificacaoRisco risco,
                                        LocalDateTime presencaConfirmadaEm) {
        return new FilaItemResponse(itemId, encaminhamentoId, especialidadeOuExame, posicao, scoreAtual,
                overrideManual, justificativaOverride, pacienteNome, status, risco, presencaConfirmadaEm);
    }
}
