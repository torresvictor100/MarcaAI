package com.marcaai.atendimento;

import com.marcaai.shared.Paciente;
import com.marcaai.shared.PacienteRepository;
import com.marcaai.shared.Profissional;
import com.marcaai.shared.ProfissionalRepository;
import com.marcaai.shared.UnidadeSaude;
import com.marcaai.shared.UnidadeSaudeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AtendimentoServiceTest {

    private PacienteRepository pacienteRepository;
    private ProfissionalRepository profissionalRepository;
    private UnidadeSaudeRepository unidadeSaudeRepository;
    private AtendimentoService atendimentoService;

    @BeforeEach
    void setUp() {
        pacienteRepository = mock(PacienteRepository.class);
        profissionalRepository = mock(ProfissionalRepository.class);
        unidadeSaudeRepository = mock(UnidadeSaudeRepository.class);
        atendimentoService = new AtendimentoService(
                mock(AtendimentoRepository.class), pacienteRepository, profissionalRepository, unidadeSaudeRepository);
    }

    private Atendimento atendimento() {
        return Atendimento.builder()
                .id(56L).pacienteId(59L).profissionalId(1L).unidadeId(1L)
                .data(LocalDateTime.of(2026, 9, 10, 9, 30)).classificacaoRisco(ClassificacaoRisco.AMARELO)
                .build();
    }

    @Test
    void montarRespostaIncluiOsNomesDePacienteProfissionalEUnidade() {
        when(pacienteRepository.findById(59L)).thenReturn(Optional.of(Paciente.builder().id(59L).nome("Fábio Almeida").build()));
        when(profissionalRepository.findById(1L)).thenReturn(Optional.of(Profissional.builder().id(1L).nome("Dr. Bruno (UBS)").build()));
        when(unidadeSaudeRepository.findById(1L)).thenReturn(Optional.of(UnidadeSaude.builder().id(1L).nome("UBS Jardim das Flores").build()));

        AtendimentoResponse resposta = atendimentoService.montarResposta(atendimento());

        assertThat(resposta.pacienteId()).isEqualTo(59L);
        assertThat(resposta.pacienteNome()).isEqualTo("Fábio Almeida");
        assertThat(resposta.profissionalNome()).isEqualTo("Dr. Bruno (UBS)");
        assertThat(resposta.unidadeNome()).isEqualTo("UBS Jardim das Flores");
    }

    @Test
    void montarRespostaDeixaNomeNuloQuandoOCadastroNaoExiste() {
        when(pacienteRepository.findById(any())).thenReturn(Optional.empty());
        when(profissionalRepository.findById(any())).thenReturn(Optional.empty());
        when(unidadeSaudeRepository.findById(any())).thenReturn(Optional.empty());

        AtendimentoResponse resposta = atendimentoService.montarResposta(atendimento());

        assertThat(resposta.pacienteId()).isEqualTo(59L);
        assertThat(resposta.pacienteNome()).isNull();
        assertThat(resposta.profissionalNome()).isNull();
        assertThat(resposta.unidadeNome()).isNull();
    }
}
