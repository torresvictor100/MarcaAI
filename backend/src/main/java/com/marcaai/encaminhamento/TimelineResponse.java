package com.marcaai.encaminhamento;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Checklist de status do encaminhamento (ideia original — seção 8 do IDEIA1.1.MD). */
@Schema(description = "Checklist de etapas do encaminhamento. Bloqueado ou cancelado vira uma etapa única com essa situação.")
public record TimelineResponse(
        @Schema(description = "Id do encaminhamento", example = "5")
        Long encaminhamentoId,
        @Schema(description = "Etapas na ordem do fluxo")
        List<EtapaTimeline> etapas
) {

    @Schema(description = "Uma etapa da timeline")
    public record EtapaTimeline(
            @Schema(description = "Situação da etapa (nome de StatusEncaminhamento)", example = "NA_FILA")
            String nome,
            @Schema(description = "Se a etapa já foi alcançada", example = "true")
            boolean concluida
    ) {
    }

    private static final List<StatusEncaminhamento> ORDEM = List.of(
            StatusEncaminhamento.AGUARDANDO_DOCUMENTOS,
            StatusEncaminhamento.EM_ANALISE,
            StatusEncaminhamento.NA_FILA,
            StatusEncaminhamento.AGENDADO,
            StatusEncaminhamento.REALIZADO
    );

    public static TimelineResponse of(Encaminhamento encaminhamento) {
        if (encaminhamento.getStatus() == StatusEncaminhamento.BLOQUEADO_REVISAO
                || encaminhamento.getStatus() == StatusEncaminhamento.CANCELADO) {
            EtapaTimeline unica = new EtapaTimeline(encaminhamento.getStatus().name(), true);
            return new TimelineResponse(encaminhamento.getId(), List.of(unica));
        }

        int posicaoAtual = ORDEM.indexOf(encaminhamento.getStatus());
        List<EtapaTimeline> etapas = ORDEM.stream()
                .map(status -> new EtapaTimeline(status.name(), ORDEM.indexOf(status) <= posicaoAtual))
                .toList();
        return new TimelineResponse(encaminhamento.getId(), etapas);
    }
}
