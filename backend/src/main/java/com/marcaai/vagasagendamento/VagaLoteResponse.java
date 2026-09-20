package com.marcaai.vagasagendamento;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Quantas vagas foram criadas e quantas já existiam (mesmo profissional e horário) e foram ignoradas. */
@Schema(description = "Resultado da abertura de vagas em lote")
public record VagaLoteResponse(
        @Schema(description = "Especialidade (consulta) ou exame", example = "Cardiologia")
        String especialidadeOuExame,
        @Schema(description = "Profissional", example = "Dra. Elisa (Cardiologia)")
        String profissionalNome,
        @Schema(description = "Vagas criadas", example = "16")
        int criadas,
        @Schema(description = "Horários que o profissional já tinha e foram ignorados", example = "0")
        int ignoradasDuplicadas,
        @Schema(description = "Data e hora da primeira vaga criada (null se nenhuma)", example = "2026-10-05T08:00:00", nullable = true)
        LocalDateTime primeira,
        @Schema(description = "Data e hora da última vaga criada (null se nenhuma)", example = "2026-10-14T11:30:00", nullable = true)
        LocalDateTime ultima
) {
}
