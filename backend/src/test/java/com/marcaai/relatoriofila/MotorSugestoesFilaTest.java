package com.marcaai.relatoriofila;

import com.marcaai.atendimento.ClassificacaoRisco;
import com.marcaai.encaminhamento.StatusEncaminhamento;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MotorSugestoesFilaTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 9, 24, 12, 0);
    private final MotorSugestoesFila motor = new MotorSugestoesFila(7, 30);

    private static ItemAnalise aguardando(long enc, int posicao, ClassificacaoRisco risco) {
        return new ItemAnalise(enc, "Paciente " + enc, posicao, StatusEncaminhamento.NA_FILA, risco, false, AGORA.minusDays(3), null);
    }

    private static ItemAnalise agendado(long enc, int posicao, ClassificacaoRisco risco, LocalDateTime data) {
        return new ItemAnalise(enc, "Paciente " + enc, posicao, StatusEncaminhamento.AGENDADO, risco, false, AGORA.minusDays(3), data);
    }

    private static List<VagaLivre> vagas(int quantidade) {
        return java.util.stream.IntStream.rangeClosed(1, quantidade)
                .mapToObj(i -> new VagaLivre((long) i, AGORA.plusDays(i)))
                .toList();
    }

    @Test
    void riscoAltoAtrasDeRiscoMenorDeveSubir() {
        List<SugestaoFila> sugestoes = motor.sugerir(List.of(
                aguardando(10, 1, ClassificacaoRisco.VERDE),
                aguardando(20, 2, ClassificacaoRisco.AMARELO),
                aguardando(30, 3, ClassificacaoRisco.VERMELHO),
                aguardando(40, 4, ClassificacaoRisco.LARANJA)), vagas(10), AGORA);

        assertThat(sugestoes).filteredOn(s -> s.tipo() == TipoSugestao.SUBIR_NA_FILA)
                .extracting(SugestaoFila::encaminhamentoId, SugestaoFila::prioridade)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(30L, PrioridadeSugestao.ALTA),
                        org.assertj.core.groups.Tuple.tuple(40L, PrioridadeSugestao.MEDIA));
        assertThat(sugestoes.get(0).motivo()).contains("posição 3").contains("sugestão: posição 1");
    }

    @Test
    void riscoAltoNaFrenteOuSoAtrasDeRiscoIgualNaoGeraSugestao() {
        List<SugestaoFila> sugestoes = motor.sugerir(List.of(
                aguardando(10, 1, ClassificacaoRisco.VERMELHO),
                aguardando(20, 2, ClassificacaoRisco.VERMELHO),
                aguardando(30, 3, ClassificacaoRisco.VERDE)), vagas(10), AGORA);

        assertThat(sugestoes).isEmpty();
    }

    @Test
    void agendadoDeRiscoAltoLongeComVagaAntesDeveAntecipar() {
        LocalDateTime consulta = AGORA.plusDays(20);
        List<SugestaoFila> sugestoes = motor.sugerir(List.of(
                agendado(10, 1, ClassificacaoRisco.VERMELHO, consulta),
                agendado(20, 2, ClassificacaoRisco.VERMELHO, AGORA.plusDays(5)),   // perto: não antecipa
                agendado(30, 3, ClassificacaoRisco.VERDE, consulta)),              // risco baixo: não antecipa
                List.of(new VagaLivre(7L, AGORA.minusDays(1)), new VagaLivre(8L, AGORA.plusDays(2))), AGORA);

        assertThat(sugestoes).singleElement().satisfies(s -> {
            assertThat(s.tipo()).isEqualTo(TipoSugestao.ANTECIPAR);
            assertThat(s.encaminhamentoId()).isEqualTo(10L);
            assertThat(s.vagaSugeridaId()).as("vaga passada não conta").isEqualTo(8L);
        });
    }

    @Test
    void semVagaAntesDaConsultaNaoSugereAntecipar() {
        List<SugestaoFila> sugestoes = motor.sugerir(
                List.of(agendado(10, 1, ClassificacaoRisco.LARANJA, AGORA.plusDays(20))),
                List.of(new VagaLivre(8L, AGORA.plusDays(25))), AGORA);

        assertThat(sugestoes).isEmpty();
    }

    @Test
    void urgenteSemVagaEsperaLongaEFaltaDeVagas() {
        ItemAnalise urgente = new ItemAnalise(10L, "A", 1, StatusEncaminhamento.NA_FILA, ClassificacaoRisco.AMARELO, true,
                AGORA.minusDays(2), null);
        ItemAnalise esperandoMuito = new ItemAnalise(20L, "B", 2, StatusEncaminhamento.NA_FILA, ClassificacaoRisco.VERDE, false,
                AGORA.minusDays(45), null);
        ItemAnalise esperandoMuitoRiscoAlto = new ItemAnalise(30L, "C", 3, StatusEncaminhamento.NA_FILA,
                ClassificacaoRisco.LARANJA, false, AGORA.minusDays(40), null);

        List<SugestaoFila> sugestoes = motor.sugerir(List.of(urgente, esperandoMuito, esperandoMuitoRiscoAlto), vagas(1), AGORA);

        assertThat(sugestoes).extracting(SugestaoFila::tipo).contains(
                TipoSugestao.URGENTE_SEM_VAGA, TipoSugestao.ESPERA_LONGA, TipoSugestao.FALTA_DE_VAGAS);
        assertThat(sugestoes).filteredOn(s -> s.tipo() == TipoSugestao.ESPERA_LONGA)
                .extracting(SugestaoFila::encaminhamentoId, SugestaoFila::prioridade)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(20L, PrioridadeSugestao.MEDIA),
                        org.assertj.core.groups.Tuple.tuple(30L, PrioridadeSugestao.ALTA));
        SugestaoFila falta = sugestoes.stream().filter(s -> s.tipo() == TipoSugestao.FALTA_DE_VAGAS).findFirst().orElseThrow();
        assertThat(falta.encaminhamentoId()).isNull();
        assertThat(falta.motivo()).contains("3 paciente(s) aguardando").contains("1 vaga(s)");
        // Alta prioridade vem antes.
        assertThat(sugestoes.get(0).prioridade()).isEqualTo(PrioridadeSugestao.ALTA);
    }

    @Test
    void dadosIncompletosNaoGeramSugestaoNemQuebramOMotor() {
        // Sem risco classificado, sem data de atendimento e agendado sem data: nada a sugerir sobre eles.
        List<SugestaoFila> sugestoes = motor.sugerir(List.of(
                new ItemAnalise(10L, "Sem risco", 2, StatusEncaminhamento.NA_FILA, null, false, AGORA.minusDays(3), null),
                new ItemAnalise(20L, "Sem data", 3, StatusEncaminhamento.NA_FILA, ClassificacaoRisco.VERDE, false, null, null),
                new ItemAnalise(30L, "Agendado sem data", 1, StatusEncaminhamento.AGENDADO, ClassificacaoRisco.VERMELHO, false,
                        AGORA.minusDays(3), null)), vagas(10), AGORA);

        assertThat(sugestoes).isEmpty();
    }
}
