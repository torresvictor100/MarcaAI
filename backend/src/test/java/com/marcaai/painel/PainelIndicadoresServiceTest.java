package com.marcaai.painel;

import com.marcaai.atendimento.Atendimento;
import com.marcaai.atendimento.AtendimentoService;
import com.marcaai.atendimento.ClassificacaoRisco;
import com.marcaai.encaminhamento.Encaminhamento;
import com.marcaai.encaminhamento.EncaminhamentoService;
import com.marcaai.encaminhamento.StatusEncaminhamento;
import com.marcaai.fila.FilaDetalhesService;
import com.marcaai.fila.FilaItemResponse;
import com.marcaai.fila.FilaService;
import com.marcaai.vagasagendamento.AgendamentoService;
import com.marcaai.vagasagendamento.StatusVaga;
import com.marcaai.vagasagendamento.VagaHorario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PainelIndicadoresServiceTest {

    private EncaminhamentoService encaminhamentoService;
    private AtendimentoService atendimentoService;
    private FilaService filaService;
    private FilaDetalhesService filaDetalhesService;
    private AgendamentoService agendamentoService;
    private PainelIndicadoresService service;

    @BeforeEach
    void setUp() {
        encaminhamentoService = mock(EncaminhamentoService.class);
        atendimentoService = mock(AtendimentoService.class);
        filaService = mock(FilaService.class);
        filaDetalhesService = mock(FilaDetalhesService.class);
        agendamentoService = mock(AgendamentoService.class);
        service = new PainelIndicadoresService(encaminhamentoService, atendimentoService, filaService, filaDetalhesService,
                agendamentoService);
        when(encaminhamentoService.listarEspecialidades()).thenReturn(List.of("Cardiologia", "Ortopedia"));
        when(filaDetalhesService.detalhar(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static FilaItemResponse item(long enc, String esp, StatusEncaminhamento status, ClassificacaoRisco risco) {
        return new FilaItemResponse(enc, enc, esp, 1, 50, false, null, "P" + enc, status, risco, null);
    }

    private void encaminhamentoComAtendimentoHa(long enc, int dias) {
        when(encaminhamentoService.buscarPorId(enc)).thenReturn(Encaminhamento.builder().id(enc).atendimentoId(enc).build());
        when(atendimentoService.buscarPorId(enc)).thenReturn(Atendimento.builder().id(enc).data(LocalDateTime.now().minusDays(dias)).build());
    }

    @Test
    void calculaNumerosGraficosEMovimento() {
        when(encaminhamentoService.contarPorStatus(any())).thenReturn(0L);
        when(encaminhamentoService.contarPorStatus(StatusEncaminhamento.NA_FILA)).thenReturn(2L);
        when(encaminhamentoService.contarPorStatus(StatusEncaminhamento.BLOQUEADO_REVISAO)).thenReturn(3L);
        when(filaService.listarPorEspecialidade("Cardiologia")).thenReturn(List.of(
                item(1, "Cardiologia", StatusEncaminhamento.NA_FILA, ClassificacaoRisco.VERMELHO),
                item(2, "Cardiologia", StatusEncaminhamento.NA_FILA, ClassificacaoRisco.VERDE),
                item(3, "Cardiologia", StatusEncaminhamento.AGENDADO, ClassificacaoRisco.VERMELHO)));
        when(filaService.listarPorEspecialidade("Ortopedia")).thenReturn(List.of());
        encaminhamentoComAtendimentoHa(1, 10);
        encaminhamentoComAtendimentoHa(2, 20);
        LocalDateTime amanha = LocalDateTime.now().plusDays(1);
        when(agendamentoService.listarVagasFuturas()).thenReturn(List.of(
                VagaHorario.builder().especialidadeOuExame("Cardiologia").status(StatusVaga.DISPONIVEL).dataHora(amanha).build(),
                VagaHorario.builder().especialidadeOuExame("Cardiologia").status(StatusVaga.OCUPADA).dataHora(amanha).build(),
                VagaHorario.builder().especialidadeOuExame("Cardiologia").status(StatusVaga.OCUPADA).dataHora(amanha).build(),
                VagaHorario.builder().especialidadeOuExame("Ortopedia").status(StatusVaga.DISPONIVEL).dataHora(amanha).build()));
        Atendimento hoje = Atendimento.builder().id(50L).data(LocalDateTime.now()).build();
        Atendimento ontem = Atendimento.builder().id(51L).data(LocalDateTime.now().minusDays(1)).build();
        when(atendimentoService.listarNoPeriodo(any(), any())).thenReturn(List.of(hoje, ontem));
        when(encaminhamentoService.listarPorAtendimentos(any())).thenReturn(List.of(
                Encaminhamento.builder().id(90L).atendimentoId(50L).build(),
                Encaminhamento.builder().id(91L).atendimentoId(50L).build()));

        PainelIndicadoresResponse r = service.indicadores();

        assertThat(r.numeros().naFila()).isEqualTo(3);
        assertThat(r.numeros().vermelhoAguardando()).isEqualTo(1);
        assertThat(r.numeros().esperaMediaDias()).isEqualTo(15.0);
        assertThat(r.numeros().ocupacaoVagasPercentual()).isEqualTo(50.0);
        assertThat(r.numeros().bloqueadosRevisao()).isEqualTo(3);

        assertThat(r.situacaoEncaminhamentos()).extracting(PainelIndicadoresResponse.QuantidadePorSituacao::status)
                .containsExactlyElementsOf(PainelIndicadoresService.ORDEM_SITUACOES);

        assertThat(r.filaPorRisco()).singleElement().satisfies(f -> {
            assertThat(f.especialidadeOuExame()).isEqualTo("Cardiologia");
            assertThat(f.porRisco().get(ClassificacaoRisco.VERMELHO)).isEqualTo(2);
            assertThat(f.porRisco().get(ClassificacaoRisco.VERDE)).isEqualTo(1);
            assertThat(f.total()).isEqualTo(3);
        });

        assertThat(r.demandaCapacidade()).extracting(PainelIndicadoresResponse.DemandaCapacidade::especialidadeOuExame,
                        PainelIndicadoresResponse.DemandaCapacidade::aguardandoAgendamento,
                        PainelIndicadoresResponse.DemandaCapacidade::vagasLivres)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Cardiologia", 2L, 1L),
                        org.assertj.core.groups.Tuple.tuple("Ortopedia", 0L, 1L));

        assertThat(r.movimento30Dias()).hasSize(PainelIndicadoresService.DIAS_MOVIMENTO);
        PainelIndicadoresResponse.MovimentoDia ultimo = r.movimento30Dias().get(PainelIndicadoresService.DIAS_MOVIMENTO - 1);
        assertThat(ultimo.data()).isEqualTo(LocalDate.now());
        assertThat(ultimo.atendimentos()).isEqualTo(1);
        assertThat(ultimo.encaminhamentos()).isEqualTo(2);
        assertThat(r.movimento30Dias().get(PainelIndicadoresService.DIAS_MOVIMENTO - 2).atendimentos()).isEqualTo(1);
    }

    @Test
    void semFilaNemVagasOsNumerosFicamVazios() {
        when(filaService.listarPorEspecialidade(anyString())).thenReturn(List.of());
        when(agendamentoService.listarVagasFuturas()).thenReturn(List.of());
        when(atendimentoService.listarNoPeriodo(any(), any())).thenReturn(List.of());

        PainelIndicadoresResponse r = service.indicadores();

        assertThat(r.numeros().esperaMediaDias()).isNull();
        assertThat(r.numeros().ocupacaoVagasPercentual()).isNull();
        assertThat(r.filaPorRisco()).isEmpty();
        assertThat(r.demandaCapacidade()).isEmpty();
        assertThat(r.movimento30Dias()).allMatch(d -> d.atendimentos() == 0 && d.encaminhamentos() == 0);
    }
}
