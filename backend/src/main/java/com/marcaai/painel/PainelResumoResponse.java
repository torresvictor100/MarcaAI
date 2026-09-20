package com.marcaai.painel;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Números gerais da rede")
public record PainelResumoResponse(
        @Schema(description = "Atendimentos registrados hoje nas UBS", example = "12")
        long atendidosHoje,
        @Schema(description = "Total de encaminhamentos", example = "96")
        long encaminhamentos,
        @Schema(description = "Encaminhamentos do tipo exame", example = "25")
        long examesSolicitados,
        @Schema(description = "Agendamentos confirmados", example = "40")
        long consultasAgendadas,
        @Schema(description = "Vagas livres", example = "32")
        long vagasDisponiveis
) {
}
