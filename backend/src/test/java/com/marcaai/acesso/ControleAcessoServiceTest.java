package com.marcaai.acesso;

import com.marcaai.atendimento.Atendimento;
import com.marcaai.atendimento.AtendimentoService;
import com.marcaai.auth.Papel;
import com.marcaai.config.MarcaAiPrincipal;
import com.marcaai.encaminhamento.Encaminhamento;
import com.marcaai.encaminhamento.EncaminhamentoService;
import com.marcaai.encaminhamento.TipoEncaminhamento;
import com.marcaai.shared.Paciente;
import com.marcaai.shared.PacienteRepository;
import com.marcaai.shared.Profissional;
import com.marcaai.shared.ProfissionalRepository;
import com.marcaai.vagasagendamento.AgendamentoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ControleAcessoServiceTest {

    // Usuários (ids de usuario) e seus vínculos.
    private static final long USUARIO_PACIENTE = 10L, PACIENTE_ID = 100L;
    private static final long USUARIO_MEDICO = 20L, MEDICO_ID = 200L;
    private static final long USUARIO_CARDIO = 30L, CARDIO_ID = 300L;
    private static final long USUARIO_LAB = 40L, LAB_ID = 400L;
    private static final long USUARIO_SEM_VINCULO = 50L;

    private static final long ATENDIMENTO_ID = 1000L;
    private static final long ENC_CONSULTA = 5000L, ENC_EXAME = 6000L;

    private EncaminhamentoService encaminhamentoService;
    private AtendimentoService atendimentoService;
    private AgendamentoService agendamentoService;
    private ControleAcessoService controle;
    private Atendimento atendimento;

    @BeforeEach
    void setUp() {
        encaminhamentoService = mock(EncaminhamentoService.class);
        atendimentoService = mock(AtendimentoService.class);
        PacienteRepository pacienteRepository = mock(PacienteRepository.class);
        ProfissionalRepository profissionalRepository = mock(ProfissionalRepository.class);
        agendamentoService = mock(AgendamentoService.class);
        controle = new ControleAcessoService(encaminhamentoService, atendimentoService, pacienteRepository, profissionalRepository,
                agendamentoService);
        when(agendamentoService.profissionalDaVagaEmVigor(any())).thenReturn(Optional.empty());
        // A consulta de Cardiologia está agendada numa vaga da especialista logada.
        when(agendamentoService.profissionalDaVagaEmVigor(ENC_CONSULTA)).thenReturn(Optional.of(CARDIO_ID));
        // O exame de Hemograma está agendado numa vaga do laboratório (que também é especialista).
        when(agendamentoService.profissionalDaVagaEmVigor(ENC_EXAME)).thenReturn(Optional.of(LAB_ID));

        when(pacienteRepository.findByUsuarioId(any())).thenReturn(Optional.empty());
        when(pacienteRepository.findByUsuarioId(USUARIO_PACIENTE)).thenReturn(Optional.of(Paciente.builder().id(PACIENTE_ID).build()));
        when(profissionalRepository.findByUsuarioId(any())).thenReturn(Optional.empty());
        when(profissionalRepository.findByUsuarioId(USUARIO_MEDICO))
                .thenReturn(Optional.of(Profissional.builder().id(MEDICO_ID).especialidade("Clínica Geral").build()));
        when(profissionalRepository.findByUsuarioId(USUARIO_CARDIO))
                .thenReturn(Optional.of(Profissional.builder().id(CARDIO_ID).especialidade("Cardiologia").build()));
        when(profissionalRepository.findByUsuarioId(USUARIO_LAB))
                .thenReturn(Optional.of(Profissional.builder().id(LAB_ID).especialidade("Hemograma Completo").build()));

        atendimento = Atendimento.builder().id(ATENDIMENTO_ID).pacienteId(PACIENTE_ID).profissionalId(MEDICO_ID).build();
        Encaminhamento consulta = Encaminhamento.builder().id(ENC_CONSULTA).atendimentoId(ATENDIMENTO_ID)
                .tipo(TipoEncaminhamento.CONSULTA_ESPECIALISTA).especialidadeOuExame("cardiologia").build();
        Encaminhamento exame = Encaminhamento.builder().id(ENC_EXAME).atendimentoId(ATENDIMENTO_ID)
                .tipo(TipoEncaminhamento.EXAME).especialidadeOuExame("Hemograma Completo").build();
        when(atendimentoService.buscarPorId(ATENDIMENTO_ID)).thenReturn(atendimento);
        when(encaminhamentoService.buscarPorId(ENC_CONSULTA)).thenReturn(consulta);
        when(encaminhamentoService.buscarPorId(ENC_EXAME)).thenReturn(exame);
        when(encaminhamentoService.listarPorAtendimento(ATENDIMENTO_ID)).thenReturn(List.of(consulta));
        when(encaminhamentoService.listarPorPaciente(PACIENTE_ID)).thenReturn(List.of(consulta, exame));
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private void logar(long usuarioId, Papel papel) {
        MarcaAiPrincipal principal = new MarcaAiPrincipal(usuarioId, "usuario-" + usuarioId, papel);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private void podeLer(long encaminhamentoId) {
        assertThatCode(() -> controle.verificarLeituraDoEncaminhamento(encaminhamentoId)).doesNotThrowAnyException();
    }

    private void naoPodeLer(long encaminhamentoId) {
        assertThatThrownBy(() -> controle.verificarLeituraDoEncaminhamento(encaminhamentoId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void gestaoLeTudo() {
        logar(1L, Papel.SECRETARIA);
        podeLer(ENC_CONSULTA);
        podeLer(ENC_EXAME);
        logar(2L, Papel.ADMIN);
        podeLer(ENC_CONSULTA);
    }

    @Test
    void pacienteSoLeOsProprios() {
        logar(USUARIO_PACIENTE, Papel.PACIENTE);
        podeLer(ENC_CONSULTA);
        logar(USUARIO_SEM_VINCULO, Papel.PACIENTE);
        naoPodeLer(ENC_CONSULTA);
    }

    @Test
    void medicoDaUbsSoLeOQueEleAtendeu() {
        logar(USUARIO_MEDICO, Papel.MEDICO_UBS);
        podeLer(ENC_CONSULTA);
        podeLer(ENC_EXAME);
        atendimento.setProfissionalId(999L);
        naoPodeLer(ENC_CONSULTA);
    }

    @Test
    void especialistaSoLeConsultaDaPropriaEspecialidade() {
        logar(USUARIO_CARDIO, Papel.ESPECIALISTA);
        podeLer(ENC_CONSULTA);   // "cardiologia" casa com "Cardiologia" sem diferenciar maiúsculas
        naoPodeLer(ENC_EXAME);
        logar(USUARIO_SEM_VINCULO, Papel.ESPECIALISTA);
        naoPodeLer(ENC_CONSULTA);
    }

    @Test
    void especialistaSoLeOQueEstaAgendadoNumaVagaDele() {
        logar(USUARIO_CARDIO, Papel.ESPECIALISTA);
        // Na vaga de outro especialista da mesma especialidade: não é dele.
        when(agendamentoService.profissionalDaVagaEmVigor(ENC_CONSULTA)).thenReturn(Optional.of(999L));
        naoPodeLer(ENC_CONSULTA);
        assertThatThrownBy(() -> controle.verificarRegistroDeResultado(ENC_CONSULTA)).isInstanceOf(AccessDeniedException.class);
        // Ainda sem agendamento (na fila): também não.
        when(agendamentoService.profissionalDaVagaEmVigor(ENC_CONSULTA)).thenReturn(Optional.empty());
        naoPodeLer(ENC_CONSULTA);
        assertThatThrownBy(() -> controle.verificarLeituraDoAtendimento(atendimento)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void laboratorioEhEspecialistaESoLeOExameAgendadoNaVagaDele() {
        logar(USUARIO_LAB, Papel.ESPECIALISTA);
        podeLer(ENC_EXAME);
        naoPodeLer(ENC_CONSULTA);
        // Ainda na fila, sem vaga dele: igual ao especialista, não enxerga.
        when(agendamentoService.profissionalDaVagaEmVigor(ENC_EXAME)).thenReturn(Optional.empty());
        naoPodeLer(ENC_EXAME);
    }

    @Test
    void atendimentoSegueAsMesmasRegras() {
        logar(1L, Papel.SECRETARIA);
        assertThatCode(() -> controle.verificarLeituraDoAtendimento(atendimento)).doesNotThrowAnyException();
        logar(USUARIO_PACIENTE, Papel.PACIENTE);
        assertThatCode(() -> controle.verificarLeituraDoAtendimento(atendimento)).doesNotThrowAnyException();
        logar(USUARIO_MEDICO, Papel.MEDICO_UBS);
        assertThatCode(() -> controle.verificarLeituraDoAtendimento(atendimento)).doesNotThrowAnyException();
        // Especialista vê porque o atendimento gerou uma consulta de Cardiologia; o laboratório não enxerga nenhum encaminhamento dele.
        logar(USUARIO_CARDIO, Papel.ESPECIALISTA);
        assertThatCode(() -> controle.verificarLeituraDoAtendimento(atendimento)).doesNotThrowAnyException();
        logar(USUARIO_LAB, Papel.ESPECIALISTA);
        assertThatThrownBy(() -> controle.verificarLeituraDoAtendimento(atendimento)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void medicoSoEncaminhaEAnexaNoQueEhDele() {
        logar(USUARIO_MEDICO, Papel.MEDICO_UBS);
        assertThatCode(() -> controle.verificarAtendimentoDoMedicoLogado(ATENDIMENTO_ID)).doesNotThrowAnyException();
        assertThatCode(() -> controle.verificarEncaminhamentoDoMedicoLogado(ENC_CONSULTA)).doesNotThrowAnyException();

        atendimento.setProfissionalId(999L);
        assertThatThrownBy(() -> controle.verificarAtendimentoDoMedicoLogado(ATENDIMENTO_ID)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controle.verificarEncaminhamentoDoMedicoLogado(ENC_CONSULTA)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void resultadoSoPeloEspecialistaResponsavel() {
        logar(USUARIO_CARDIO, Papel.ESPECIALISTA);
        assertThatCode(() -> controle.verificarRegistroDeResultado(ENC_CONSULTA)).doesNotThrowAnyException();
        assertThatThrownBy(() -> controle.verificarRegistroDeResultado(ENC_EXAME)).isInstanceOf(AccessDeniedException.class);

        logar(USUARIO_LAB, Papel.ESPECIALISTA);
        assertThatCode(() -> controle.verificarRegistroDeResultado(ENC_EXAME)).doesNotThrowAnyException();
        assertThatThrownBy(() -> controle.verificarRegistroDeResultado(ENC_CONSULTA)).isInstanceOf(AccessDeniedException.class);

        // Nem a secretaria registra desfecho clínico.
        logar(1L, Papel.SECRETARIA);
        assertThatThrownBy(() -> controle.verificarRegistroDeResultado(ENC_EXAME)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void listagemPorPacienteFiltraPorPapel() {
        logar(1L, Papel.SECRETARIA);
        assertThat(controle.encaminhamentosVisiveisDoPaciente(PACIENTE_ID)).hasSize(2);

        logar(USUARIO_PACIENTE, Papel.PACIENTE);
        assertThat(controle.encaminhamentosVisiveisDoPaciente(PACIENTE_ID)).hasSize(2);
        assertThatThrownBy(() -> controle.encaminhamentosVisiveisDoPaciente(999L)).isInstanceOf(AccessDeniedException.class);

        logar(USUARIO_MEDICO, Papel.MEDICO_UBS);
        assertThat(controle.encaminhamentosVisiveisDoPaciente(PACIENTE_ID)).hasSize(2);
        atendimento.setProfissionalId(999L);
        assertThat(controle.encaminhamentosVisiveisDoPaciente(PACIENTE_ID)).isEmpty();

        logar(USUARIO_CARDIO, Papel.ESPECIALISTA);
        assertThatThrownBy(() -> controle.encaminhamentosVisiveisDoPaciente(PACIENTE_ID)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void listagemDeAtendimentosPorPacienteFiltraPorPapelEOrdenaDoMaisRecente() {
        Atendimento antigo = Atendimento.builder().id(1L).pacienteId(PACIENTE_ID).profissionalId(MEDICO_ID)
                .data(LocalDateTime.of(2026, 1, 10, 9, 0)).build();
        Atendimento recente = Atendimento.builder().id(2L).pacienteId(PACIENTE_ID).profissionalId(MEDICO_ID)
                .data(LocalDateTime.of(2026, 9, 1, 9, 0)).build();
        Atendimento deOutroMedico = Atendimento.builder().id(3L).pacienteId(PACIENTE_ID).profissionalId(999L)
                .data(LocalDateTime.of(2026, 5, 1, 9, 0)).build();
        when(atendimentoService.listarPorPaciente(PACIENTE_ID)).thenReturn(List.of(antigo, deOutroMedico, recente));

        logar(1L, Papel.SECRETARIA);
        assertThat(controle.atendimentosVisiveisDoPaciente(PACIENTE_ID)).containsExactly(recente, deOutroMedico, antigo);

        logar(USUARIO_PACIENTE, Papel.PACIENTE);
        assertThat(controle.atendimentosVisiveisDoPaciente(PACIENTE_ID)).hasSize(3);
        assertThatThrownBy(() -> controle.atendimentosVisiveisDoPaciente(999L)).isInstanceOf(AccessDeniedException.class);

        logar(USUARIO_MEDICO, Papel.MEDICO_UBS);
        assertThat(controle.atendimentosVisiveisDoPaciente(PACIENTE_ID)).containsExactly(recente, antigo);

        logar(USUARIO_CARDIO, Papel.ESPECIALISTA);
        assertThatThrownBy(() -> controle.atendimentosVisiveisDoPaciente(PACIENTE_ID)).isInstanceOf(AccessDeniedException.class);
        logar(USUARIO_LAB, Papel.ESPECIALISTA);
        assertThatThrownBy(() -> controle.atendimentosVisiveisDoPaciente(PACIENTE_ID)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void profissionalDoAtendimentoVemDoLogin() {
        logar(USUARIO_MEDICO, Papel.MEDICO_UBS);
        assertThat(controle.profissionalIdDoUsuarioLogado()).isEqualTo(MEDICO_ID);

        logar(USUARIO_SEM_VINCULO, Papel.MEDICO_UBS);
        assertThatThrownBy(() -> controle.profissionalIdDoUsuarioLogado()).isInstanceOf(AccessDeniedException.class);
    }
}
