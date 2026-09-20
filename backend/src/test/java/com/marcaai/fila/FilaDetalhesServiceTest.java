package com.marcaai.fila;

import com.marcaai.atendimento.Atendimento;
import com.marcaai.atendimento.AtendimentoService;
import com.marcaai.atendimento.ClassificacaoRisco;
import com.marcaai.encaminhamento.Encaminhamento;
import com.marcaai.encaminhamento.EncaminhamentoService;
import com.marcaai.encaminhamento.StatusEncaminhamento;
import com.marcaai.shared.Paciente;
import com.marcaai.shared.PacienteRepository;
import com.marcaai.vagasagendamento.AgendamentoService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FilaDetalhesServiceTest {

    @Test
    void acrescentaPacienteSituacaoERiscoSemMexerNaPosicao() {
        EncaminhamentoService encaminhamentoService = mock(EncaminhamentoService.class);
        AtendimentoService atendimentoService = mock(AtendimentoService.class);
        PacienteRepository pacienteRepository = mock(PacienteRepository.class);
        AgendamentoService agendamentoService = mock(AgendamentoService.class);
        FilaDetalhesService service = new FilaDetalhesService(encaminhamentoService, atendimentoService, pacienteRepository,
                agendamentoService);
        LocalDateTime confirmouEm = LocalDateTime.of(2026, 9, 24, 10, 0);
        when(agendamentoService.presencaConfirmadaEm(16L)).thenReturn(Optional.of(confirmouEm));

        when(encaminhamentoService.buscarPorId(16L)).thenReturn(
                Encaminhamento.builder().id(16L).atendimentoId(5L).status(StatusEncaminhamento.AGENDADO).build());
        when(atendimentoService.buscarPorId(5L)).thenReturn(
                Atendimento.builder().id(5L).pacienteId(9L).classificacaoRisco(ClassificacaoRisco.VERMELHO).build());
        when(pacienteRepository.findById(9L)).thenReturn(Optional.of(Paciente.builder().id(9L).nome("Marta Aparecida").build()));
        FilaItemResponse item = new FilaItemResponse(12L, 16L, "Cardiologia", 3, 108.0, false, null, null, null, null, null);

        FilaItemResponse detalhado = service.detalhar(List.of(item)).get(0);

        assertThat(detalhado.posicao()).isEqualTo(3);
        assertThat(detalhado.pacienteNome()).isEqualTo("Marta Aparecida");
        assertThat(detalhado.statusEncaminhamento()).isEqualTo(StatusEncaminhamento.AGENDADO);
        assertThat(detalhado.classificacaoRisco()).isEqualTo(ClassificacaoRisco.VERMELHO);
        assertThat(detalhado.presencaConfirmadaEm()).as("paciente agendado que confirmou presença").isEqualTo(confirmouEm);
    }
}
