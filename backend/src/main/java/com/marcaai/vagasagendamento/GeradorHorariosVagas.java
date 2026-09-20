package com.marcaai.vagasagendamento;

import com.marcaai.config.RegraDeNegocioException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Regra pura de quais horários um lote gera — sem banco, fácil de testar. */
final class GeradorHorariosVagas {

    static final int MAX_DIAS_PERIODO = 183;
    static final int MAX_VAGAS_POR_LOTE = 500;

    private GeradorHorariosVagas() {
    }

    static List<LocalDateTime> gerar(VagaLoteRequest request, LocalDateTime agora) {
        if (request.dataFim().isBefore(request.dataInicio())) {
            throw new RegraDeNegocioException("A data final do período não pode ser anterior à data inicial");
        }
        if (ChronoUnit.DAYS.between(request.dataInicio(), request.dataFim()) > MAX_DIAS_PERIODO) {
            throw new RegraDeNegocioException("O período pode ter no máximo " + MAX_DIAS_PERIODO + " dias");
        }
        if (!request.horaFim().isAfter(request.horaInicio())) {
            throw new RegraDeNegocioException("O horário final precisa ser depois do inicial");
        }

        List<LocalDateTime> horarios = new ArrayList<>();
        for (LocalDate dia = request.dataInicio(); !dia.isAfter(request.dataFim()); dia = dia.plusDays(1)) {
            if (!request.diasDaSemana().contains(dia.getDayOfWeek())) {
                continue;
            }
            // Contado em minutos do dia (LocalTime daria a volta na meia-noite); cada vaga cabe inteira até o fim.
            int fim = request.horaFim().toSecondOfDay() / 60;
            for (int minuto = request.horaInicio().toSecondOfDay() / 60;
                 minuto + request.duracaoMinutos() <= fim;
                 minuto += request.duracaoMinutos()) {
                LocalDateTime dataHora = dia.atTime(LocalTime.ofSecondOfDay(minuto * 60L));
                if (dataHora.isAfter(agora)) {
                    horarios.add(dataHora);
                }
                if (horarios.size() > MAX_VAGAS_POR_LOTE) {
                    throw new RegraDeNegocioException("O lote passaria de " + MAX_VAGAS_POR_LOTE
                            + " vagas; reduza o período, os dias ou aumente a duração");
                }
            }
        }
        if (horarios.isEmpty()) {
            throw new RegraDeNegocioException("Nenhum horário futuro no período, dias e horário escolhidos");
        }
        return horarios;
    }
}
