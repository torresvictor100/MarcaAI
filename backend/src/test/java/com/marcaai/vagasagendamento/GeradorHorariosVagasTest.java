package com.marcaai.vagasagendamento;

import com.marcaai.config.RegraDeNegocioException;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeradorHorariosVagasTest {

    // Quinta-feira, 24/09/2026, meio-dia.
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 9, 24, 12, 0);

    private static VagaLoteRequest lote(LocalDate inicio, LocalDate fim, Set<DayOfWeek> dias, String de, String ate, int duracao) {
        return new VagaLoteRequest(1L, 1L, inicio, fim, dias, LocalTime.parse(de), LocalTime.parse(ate), duracao);
    }

    @Test
    void repeteTodaSemanaNosDiasEscolhidos() {
        // 3 semanas (28/09 a 18/10), terças e quintas, 08:00-10:00 de 30 em 30 = 6 dias × 4 horários.
        List<LocalDateTime> horarios = GeradorHorariosVagas.gerar(lote(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 18),
                Set.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), "08:00", "10:00", 30), AGORA);

        assertThat(horarios).hasSize(24);
        assertThat(horarios).allMatch(h -> h.getDayOfWeek() == DayOfWeek.TUESDAY || h.getDayOfWeek() == DayOfWeek.THURSDAY);
        assertThat(horarios.get(0)).isEqualTo(LocalDateTime.of(2026, 9, 29, 8, 0));
        assertThat(horarios.get(3)).isEqualTo(LocalDateTime.of(2026, 9, 29, 9, 30));
    }

    @Test
    void vagaQueNaoCabeInteiraNoHorarioNaoEntraEPassadoEhIgnorado() {
        // Hoje (quinta) 08:00-13:00 de 50 min: 08:00, 08:50, 09:40, 10:30, 11:20 (12:10 terminaria 13:00 e cabe) — só 12:10 é futuro.
        List<LocalDateTime> horarios = GeradorHorariosVagas.gerar(lote(AGORA.toLocalDate(), AGORA.toLocalDate(),
                Set.of(DayOfWeek.THURSDAY), "08:00", "13:00", 50), AGORA);

        assertThat(horarios).containsExactly(LocalDateTime.of(2026, 9, 24, 12, 10));
    }

    @Test
    void naoDaAVoltaNaMeiaNoite() {
        List<LocalDateTime> horarios = GeradorHorariosVagas.gerar(lote(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 25),
                Set.of(DayOfWeek.FRIDAY), "22:00", "23:59", 60), AGORA);

        assertThat(horarios).containsExactly(LocalDateTime.of(2026, 9, 25, 22, 0));
    }

    @Test
    void recusaEntradasIncoerentesOuGrandesDemais() {
        LocalDate d = LocalDate.of(2026, 10, 1);
        assertThatThrownBy(() -> GeradorHorariosVagas.gerar(lote(d, d.minusDays(1), Set.of(DayOfWeek.MONDAY), "08:00", "12:00", 30), AGORA))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> GeradorHorariosVagas.gerar(lote(d, d.plusDays(200), Set.of(DayOfWeek.MONDAY), "08:00", "12:00", 30), AGORA))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> GeradorHorariosVagas.gerar(lote(d, d, Set.of(DayOfWeek.THURSDAY), "12:00", "08:00", 30), AGORA))
                .isInstanceOf(RegraDeNegocioException.class);
        // Nenhum dia do período cai numa segunda.
        assertThatThrownBy(() -> GeradorHorariosVagas.gerar(lote(d, d, Set.of(DayOfWeek.MONDAY), "08:00", "12:00", 30), AGORA))
                .isInstanceOf(RegraDeNegocioException.class);
        // 6 meses, todos os dias, 08-18 de 10 em 10 = milhares.
        assertThatThrownBy(() -> GeradorHorariosVagas.gerar(lote(d, d.plusDays(180), Set.of(DayOfWeek.values()), "08:00", "18:00", 10), AGORA))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("500");
    }
}
