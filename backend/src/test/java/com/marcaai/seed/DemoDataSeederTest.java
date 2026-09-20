package com.marcaai.seed;

import com.marcaai.atendimento.Atendimento;
import com.marcaai.atendimento.AtendimentoService;
import com.marcaai.encaminhamento.Encaminhamento;
import com.marcaai.encaminhamento.EncaminhamentoService;
import com.marcaai.fila.FilaItemResponse;
import com.marcaai.fila.FilaService;
import com.marcaai.resultadoexame.ResultadoExameService;
import com.marcaai.vagasagendamento.AgendamentoRequest;
import com.marcaai.vagasagendamento.AgendamentoService;
import com.marcaai.vagasagendamento.VagaHorario;
import com.marcaai.config.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class DemoDataSeederTest {

    private FilaService filaService;
    private EncaminhamentoService encaminhamentoService;
    private AgendamentoService agendamentoService;
    private DemoDataSeeder seeder;

    @BeforeEach
    void setUp() {
        filaService = mock(FilaService.class);
        agendamentoService = mock(AgendamentoService.class);
        encaminhamentoService = mock(EncaminhamentoService.class);
        AtendimentoService atendimentoService = mock(AtendimentoService.class);
        seeder = new DemoDataSeeder(
                encaminhamentoService, filaService, agendamentoService, mock(ResultadoExameService.class), atendimentoService);
        // Encaminhamento N pertence ao atendimento N; ímpares são da massa V015, pares são de outro cenário.
        when(encaminhamentoService.buscarPorId(anyLong()))
                .thenAnswer(inv -> Encaminhamento.builder().id(inv.getArgument(0)).atendimentoId(inv.getArgument(0)).build());
        when(atendimentoService.buscarPorId(anyLong())).thenAnswer(inv -> {
            long id = inv.getArgument(0);
            String notas = id % 2 == 1 ? DemoDataSeeder.PREFIXO_MASSA_V015 + " #" + id : "Cenário de risco (massa de dados) #" + id;
            return Atendimento.builder().id(id).notas(notas).build();
        });
        when(agendamentoService.buscarPorEncaminhamento(any())).thenThrow(new RecursoNaoEncontradoException("sem agendamento"));
        // Fila e vagas livres de sobra em toda especialidade — o limite tem que vir só da cota.
        when(filaService.listarPorEspecialidade(anyString())).thenAnswer(inv -> fila(inv.getArgument(0), 20));
        when(agendamentoService.listarDisponiveisPorEspecialidade(anyString())).thenAnswer(inv -> vagas(20));
    }

    private static List<FilaItemResponse> fila(String especialidade, int tamanho) {
        return LongStream.rangeClosed(1, tamanho)
                .mapToObj(i -> new FilaItemResponse(i, 100 + i, especialidade, (int) i, 50.0, false, null, null, null, null, null))
                .toList();
    }

    private static List<VagaHorario> vagas(int quantidade) {
        return LongStream.rangeClosed(1, quantidade).mapToObj(i -> VagaHorario.builder().id(i).build()).toList();
    }

    @Test
    void naoAgendaNadaQuandoACotaDaEspecialidadeJaFoiAtingida() {
        when(agendamentoService.contarAgendamentosPorEspecialidade(anyString())).thenReturn(14L);

        seeder.run(null);

        verify(agendamentoService, never()).criar(any());
    }

    @Test
    void agendaSoOQueFaltaParaCompletarACotaTotal() {
        when(agendamentoService.contarAgendamentosPorEspecialidade(anyString())).thenReturn(14L);
        when(agendamentoService.contarAgendamentosPorEspecialidade("Ortopedia")).thenReturn(8L);

        seeder.run(null);

        verify(agendamentoService, times(2)).criar(any(AgendamentoRequest.class));
    }

    @Test
    void soAgendaAMassaDaV015() {
        when(agendamentoService.contarAgendamentosPorEspecialidade(anyString())).thenReturn(14L);
        when(agendamentoService.contarAgendamentosPorEspecialidade("Ortopedia")).thenReturn(0L);

        seeder.run(null);

        // Fila de Ortopedia: encaminhamentos 101..120; só os ímpares (massa V015) podem ser agendados.
        verify(agendamentoService, times(10)).criar(argThat(req -> req.encaminhamentoId() % 2 == 1));
        verify(agendamentoService, never()).criar(argThat(req -> req.encaminhamentoId() % 2 == 0));
    }

    private static com.marcaai.vagasagendamento.VagaMarcadaResponse marcada(long agendamentoId, long encaminhamentoId,
                                                                            java.time.LocalDateTime confirmadaEm) {
        return new com.marcaai.vagasagendamento.VagaMarcadaResponse(agendamentoId, java.time.LocalDateTime.now().plusDays(1),
                "Ortopedia", null, null, agendamentoId, com.marcaai.vagasagendamento.StatusAgendamento.CONFIRMADO, "sistema-seed",
                encaminhamentoId, "Paciente " + encaminhamentoId, confirmadaEm);
    }

    @Test
    void confirmaAPresencaDeUmSimUmNaoSoDaMassaV015() {
        when(agendamentoService.contarAgendamentosPorEspecialidade(anyString())).thenReturn(14L);
        when(encaminhamentoService.buscarPorId(anyLong())).thenAnswer(inv -> Encaminhamento.builder().id(inv.getArgument(0))
                .atendimentoId(inv.getArgument(0)).status(com.marcaai.encaminhamento.StatusEncaminhamento.AGENDADO).build());
        // Ímpares são da massa V015: sobram 1, 3, 5 e 7; confirma o 1º e o 3º deles (1 e 5).
        when(agendamentoService.listarMarcadasPorEspecialidade(eq("Ortopedia"), any(), isNull())).thenReturn(
                LongStream.rangeClosed(1, 8).mapToObj(i -> marcada(i, i, null)).toList());

        seeder.run(null);

        verify(agendamentoService).confirmarPresenca(1L);
        verify(agendamentoService).confirmarPresenca(5L);
        verify(agendamentoService, times(2)).confirmarPresenca(anyLong());
    }

    @Test
    void naoConfirmaDeNovoQuandoAEspecialidadeJaTemPresencaConfirmada() {
        when(agendamentoService.contarAgendamentosPorEspecialidade(anyString())).thenReturn(14L);
        when(encaminhamentoService.buscarPorId(anyLong())).thenAnswer(inv -> Encaminhamento.builder().id(inv.getArgument(0))
                .atendimentoId(inv.getArgument(0)).status(com.marcaai.encaminhamento.StatusEncaminhamento.AGENDADO).build());
        when(agendamentoService.listarMarcadasPorEspecialidade(eq("Ortopedia"), any(), isNull())).thenReturn(List.of(
                marcada(1, 1, java.time.LocalDateTime.now()), marcada(3, 3, null)));

        seeder.run(null);

        verify(agendamentoService, never()).confirmarPresenca(anyLong());
    }
}
