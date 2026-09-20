package com.marcaai.vagasagendamento;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * Abre vagas de um profissional numa unidade, repetindo toda semana nos dias escolhidos: para cada data do
 * período cujo dia da semana está em {@code diasDaSemana}, uma vaga a cada {@code duracaoMinutos} entre
 * {@code horaInicio} e {@code horaFim}. Uma vaga só = mesmo dia de início e fim, com um horário.
 */
@Schema(description = "Abertura de vagas em lote: para cada data do período cujo dia da semana foi escolhido, uma vaga a cada duracaoMinutos entre horaInicio e horaFim. Uma vaga só: mesmo dia de início e fim, com um horário.")
public record VagaLoteRequest(
        @Schema(description = "Id do profissional (GET /profissionais)", example = "2")
        @NotNull Long profissionalId,
        @Schema(description = "Id da unidade (GET /unidades)", example = "2")
        @NotNull Long unidadeId,
        @Schema(description = "Primeiro dia do período (não pode gerar vaga no passado)", example = "2026-10-05")
        @NotNull LocalDate dataInicio,
        @Schema(description = "Último dia do período, inclusive", example = "2026-10-16")
        @NotNull LocalDate dataFim,
        @Schema(description = "Dias da semana em que abrir vagas")
        @NotEmpty Set<DayOfWeek> diasDaSemana,
        @Schema(description = "Horário da primeira vaga do dia", example = "08:00:00")
        @NotNull LocalTime horaInicio,
        @Schema(description = "Fim do expediente: a última vaga termina até aqui", example = "12:00:00")
        @NotNull LocalTime horaFim,
        @Schema(description = "Duração de cada vaga, de 10 a 240 minutos", example = "30")
        @NotNull @Min(10) @Max(240) Integer duracaoMinutos
) {
}
