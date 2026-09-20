package com.marcaai.vagasagendamento;

import com.marcaai.config.ConflitoException;
import com.marcaai.atendimento.Atendimento;
import com.marcaai.atendimento.AtendimentoService;
import com.marcaai.config.RegraDeNegocioException;
import com.marcaai.encaminhamento.Encaminhamento;
import com.marcaai.encaminhamento.EncaminhamentoService;
import com.marcaai.shared.Paciente;
import com.marcaai.shared.PacienteRepository;
import com.marcaai.shared.Profissional;
import com.marcaai.shared.ProfissionalRepository;
import com.marcaai.shared.UnidadeSaude;
import com.marcaai.shared.UnidadeSaudeRepository;
import com.marcaai.auth.Papel;
import com.marcaai.config.MarcaAiPrincipal;
import com.marcaai.config.RecursoNaoEncontradoException;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AgendamentoServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 24);

    private AgendamentoRepository agendamentoRepository;
    private VagaHorarioRepository vagaHorarioRepository;
    private EncaminhamentoService encaminhamentoService;
    private AtendimentoService atendimentoService;
    private PacienteRepository pacienteRepository;
    private ProfissionalRepository profissionalRepository;
    private UnidadeSaudeRepository unidadeSaudeRepository;
    private HistoricoAgendamentoRepository historicoRepository;
    private AgendamentoService agendamentoService;

    @BeforeEach
    void setUp() {
        agendamentoRepository = mock(AgendamentoRepository.class);
        vagaHorarioRepository = mock(VagaHorarioRepository.class);
        encaminhamentoService = mock(EncaminhamentoService.class);
        atendimentoService = mock(AtendimentoService.class);
        pacienteRepository = mock(PacienteRepository.class);
        profissionalRepository = mock(ProfissionalRepository.class);
        unidadeSaudeRepository = mock(UnidadeSaudeRepository.class);
        historicoRepository = mock(HistoricoAgendamentoRepository.class);
        agendamentoService = new AgendamentoService(agendamentoRepository, vagaHorarioRepository, encaminhamentoService,
                atendimentoService, pacienteRepository, profissionalRepository, unidadeSaudeRepository, historicoRepository);
        when(agendamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        MarcaAiPrincipal secretaria = new MarcaAiPrincipal(5L, "secretaria", Papel.SECRETARIA);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(secretaria, null, List.of()));
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    // --- Cancelar / remarcar / antecipar -------------------------------------------------------------

    private static final LocalDateTime DATA_ATUAL = LocalDateTime.of(2026, 10, 1, 9, 0);

    private Agendamento confirmadoNaVaga7() {
        Agendamento agendamento = Agendamento.builder().id(30L).encaminhamentoId(56L).vagaId(7L).dataHora(DATA_ATUAL)
                .status(StatusAgendamento.CONFIRMADO).agendadoPor("secretaria")
                .presencaConfirmadaEm(DATA_ATUAL.minusDays(2)).build();
        when(agendamentoRepository.findById(30L)).thenReturn(Optional.of(agendamento));
        return agendamento;
    }

    private VagaHorario vaga(long id, String especialidade, LocalDateTime dataHora, StatusVaga status) {
        VagaHorario vaga = VagaHorario.builder().id(id).unidadeId(2L).profissionalId(2L)
                .especialidadeOuExame(especialidade).dataHora(dataHora).status(status).build();
        when(vagaHorarioRepository.findById(id)).thenReturn(Optional.of(vaga));
        return vaga;
    }

    @Test
    void cancelarLiberaAVagaDevolveOPacienteParaAFilaERegistraOMotivo() {
        Agendamento agendamento = confirmadoNaVaga7();
        VagaHorario vagaAtual = vaga(7L, "Cardiologia", DATA_ATUAL, StatusVaga.OCUPADA);

        agendamentoService.cancelar(30L, "Paciente pediu para desmarcar");

        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
        assertThat(vagaAtual.getStatus()).isEqualTo(StatusVaga.DISPONIVEL);
        verify(encaminhamentoService).atualizarStatus(56L, com.marcaai.encaminhamento.StatusEncaminhamento.NA_FILA);
        verify(historicoRepository).save(argThat(h -> h.getAcao() == AcaoAgendamento.CANCELADO
                && h.getMotivo().equals("Paciente pediu para desmarcar") && h.getFeitoPor().equals("secretaria")
                && h.getVagaNovaId() == null));
    }

    @Test
    void remarcarTrocaAVagaEmQualquerData() {
        Agendamento agendamento = confirmadoNaVaga7();
        VagaHorario vagaAtual = vaga(7L, "Cardiologia", DATA_ATUAL, StatusVaga.OCUPADA);
        VagaHorario vagaNova = vaga(8L, "cardiologia", DATA_ATUAL.plusDays(5), StatusVaga.DISPONIVEL);

        agendamentoService.remarcar(30L, 8L, "Médica em congresso");

        assertThat(agendamento.getVagaId()).isEqualTo(8L);
        assertThat(agendamento.getDataHora()).isEqualTo(DATA_ATUAL.plusDays(5));
        assertThat(agendamento.getPresencaConfirmadaEm()).as("nova data exige nova confirmação").isNull();
        assertThat(vagaAtual.getStatus()).isEqualTo(StatusVaga.DISPONIVEL);
        assertThat(vagaNova.getStatus()).isEqualTo(StatusVaga.OCUPADA);
        verify(historicoRepository).save(argThat(h -> h.getAcao() == AcaoAgendamento.REMARCADO
                && h.getVagaAnteriorId() == 7L && h.getVagaNovaId() == 8L && h.getDataHoraAnterior().equals(DATA_ATUAL)));
    }

    @Test
    void anteciparSoAceitaVagaAnterior() {
        confirmadoNaVaga7();
        vaga(7L, "Cardiologia", DATA_ATUAL, StatusVaga.OCUPADA);
        vaga(8L, "Cardiologia", DATA_ATUAL.plusDays(1), StatusVaga.DISPONIVEL);
        vaga(9L, "Cardiologia", DATA_ATUAL.minusDays(1), StatusVaga.DISPONIVEL);

        assertThatThrownBy(() -> agendamentoService.antecipar(30L, 8L, "Abriu vaga antes"))
                .isInstanceOf(RegraDeNegocioException.class);

        Agendamento antecipado = agendamentoService.antecipar(30L, 9L, "Abriu vaga antes");
        assertThat(antecipado.getVagaId()).isEqualTo(9L);
        verify(historicoRepository).save(argThat(h -> h.getAcao() == AcaoAgendamento.ANTECIPADO));
    }

    @Test
    void recusaVagaOcupadaDeOutraEspecialidadeAMesmaVagaOuSemMotivo() {
        confirmadoNaVaga7();
        vaga(7L, "Cardiologia", DATA_ATUAL, StatusVaga.OCUPADA);
        vaga(8L, "Cardiologia", DATA_ATUAL.plusDays(1), StatusVaga.OCUPADA);
        vaga(10L, "Ortopedia", DATA_ATUAL.plusDays(1), StatusVaga.DISPONIVEL);
        vaga(11L, "Cardiologia", DATA_ATUAL.plusDays(2), StatusVaga.DISPONIVEL);

        assertThatThrownBy(() -> agendamentoService.remarcar(30L, 8L, "motivo")).as("vaga ocupada: 409").isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> agendamentoService.remarcar(30L, 10L, "motivo")).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> agendamentoService.remarcar(30L, 7L, "motivo")).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> agendamentoService.remarcar(30L, 11L, "  ")).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> agendamentoService.cancelar(30L, null)).isInstanceOf(RegraDeNegocioException.class);
        verify(historicoRepository, never()).save(any());
    }

    @Test
    void naoAlteraAgendamentoQueNaoEstaConfirmadoOuNaoExiste() {
        Agendamento agendamento = confirmadoNaVaga7();
        agendamento.setStatus(StatusAgendamento.CANCELADO);
        when(agendamentoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agendamentoService.cancelar(30L, "motivo")).isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> agendamentoService.cancelar(99L, "motivo")).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void naoAgendaDuasVezesOMesmoEncaminhamento() {
        vaga(11L, "Cardiologia", DATA_ATUAL.plusDays(2), StatusVaga.DISPONIVEL);
        when(agendamentoRepository.findFirstByEncaminhamentoIdAndStatusNotOrderByIdDesc(56L, StatusAgendamento.CANCELADO))
                .thenReturn(Optional.of(Agendamento.builder().id(30L).build()));

        assertThatThrownBy(() -> agendamentoService.criar(new AgendamentoRequest(56L, 11L)))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void buscaOAgendamentoEmVigorEIncluiOHistorico() {
        Agendamento emVigor = Agendamento.builder().id(31L).encaminhamentoId(56L).vagaId(7L).dataHora(DATA_ATUAL)
                .status(StatusAgendamento.CONFIRMADO).agendadoPor("secretaria").build();
        when(agendamentoRepository.findFirstByEncaminhamentoIdAndStatusNotOrderByIdDesc(56L, StatusAgendamento.CANCELADO))
                .thenReturn(Optional.of(emVigor));
        when(historicoRepository.findByAgendamentoIdOrderByFeitoEmAscIdAsc(31L)).thenReturn(List.of(HistoricoAgendamento.builder()
                .acao(AcaoAgendamento.REMARCADO).dataHoraAnterior(DATA_ATUAL.minusDays(1)).dataHoraNova(DATA_ATUAL)
                .motivo("Médica em congresso").feitoPor("secretaria").feitoEm(DATA_ATUAL.minusDays(3)).build()));

        AgendamentoResponse resposta = agendamentoService.montarResposta(agendamentoService.buscarPorEncaminhamento(56L));

        assertThat(resposta.id()).isEqualTo(31L);
        assertThat(resposta.historico()).extracting(HistoricoAgendamentoResponse::motivo).containsExactly("Médica em congresso");
    }

    private VagaHorario vagaOcupada() {
        return VagaHorario.builder()
                .id(7L).unidadeId(2L).profissionalId(2L).especialidadeOuExame("Cardiologia")
                .dataHora(LocalDateTime.of(2026, 9, 25, 9, 0)).status(StatusVaga.OCUPADA)
                .build();
    }

    @Test
    void semDataFinalBuscaDoInicioDoDiaEmDianteEResolveOPaciente() {
        when(vagaHorarioRepository.findByEspecialidadeOuExameIgnoreCaseAndStatusAndDataHoraGreaterThanEqualOrderByDataHora(
                "Cardiologia", StatusVaga.OCUPADA, HOJE.atStartOfDay())).thenReturn(List.of(vagaOcupada()));
        when(agendamentoRepository.findByVagaIdOrderByIdDesc(7L)).thenReturn(List.of(Agendamento.builder()
                .id(30L).encaminhamentoId(56L).vagaId(7L).status(StatusAgendamento.CONFIRMADO).agendadoPor("secretaria").build()));
        when(encaminhamentoService.buscarPorId(56L)).thenReturn(Encaminhamento.builder().id(56L).atendimentoId(56L).build());
        when(atendimentoService.buscarPorId(56L)).thenReturn(Atendimento.builder().id(56L).pacienteId(59L).build());
        when(pacienteRepository.findById(59L)).thenReturn(Optional.of(Paciente.builder().id(59L).nome("Fábio Almeida").build()));
        when(profissionalRepository.findById(2L)).thenReturn(Optional.of(Profissional.builder().id(2L).nome("Dra. Elisa (Cardiologia)").build()));
        when(unidadeSaudeRepository.findById(2L)).thenReturn(Optional.of(UnidadeSaude.builder().id(2L).nome("Centro Especializado Cardio").build()));

        List<VagaMarcadaResponse> marcadas = agendamentoService.listarMarcadasPorEspecialidade("Cardiologia", HOJE, null);

        assertThat(marcadas).hasSize(1);
        VagaMarcadaResponse marcada = marcadas.get(0);
        assertThat(marcada.pacienteNome()).isEqualTo("Fábio Almeida");
        assertThat(marcada.encaminhamentoId()).isEqualTo(56L);
        assertThat(marcada.statusAgendamento()).isEqualTo(StatusAgendamento.CONFIRMADO);
        assertThat(marcada.profissionalNome()).isEqualTo("Dra. Elisa (Cardiologia)");
        assertThat(marcada.unidadeNome()).isEqualTo("Centro Especializado Cardio");
    }

    @Test
    void comDataFinalIncluiODiaInteiroDoFimDoPeriodo() {
        LocalDate fim = HOJE.plusDays(7);

        agendamentoService.listarMarcadasPorEspecialidade("Cardiologia", HOJE, fim);

        verify(vagaHorarioRepository)
                .findByEspecialidadeOuExameIgnoreCaseAndStatusAndDataHoraGreaterThanEqualAndDataHoraLessThanOrderByDataHora(
                        "Cardiologia", StatusVaga.OCUPADA, HOJE.atStartOfDay(), fim.plusDays(1).atStartOfDay());
    }

    @Test
    void listaDisponiveisDaMaisProximaParaAMaisDistanteComNomes() {
        VagaHorario depois = VagaHorario.builder().id(2L).unidadeId(2L).profissionalId(2L)
                .dataHora(LocalDateTime.of(2026, 9, 26, 8, 0)).status(StatusVaga.DISPONIVEL).build();
        VagaHorario antes = VagaHorario.builder().id(1L).unidadeId(2L).profissionalId(99L)
                .dataHora(LocalDateTime.of(2026, 9, 25, 8, 0)).status(StatusVaga.DISPONIVEL).build();
        when(vagaHorarioRepository.findByEspecialidadeOuExameIgnoreCaseAndStatus("Cardiologia", StatusVaga.DISPONIVEL))
                .thenReturn(List.of(depois, antes));
        when(profissionalRepository.findById(2L)).thenReturn(Optional.of(Profissional.builder().id(2L).nome("Dra. Elisa (Cardiologia)").build()));
        when(profissionalRepository.findById(99L)).thenReturn(Optional.empty());
        when(unidadeSaudeRepository.findById(2L)).thenReturn(Optional.of(UnidadeSaude.builder().id(2L).nome("Centro Especializado Cardio").build()));

        List<VagaHorarioResponse> vagas = agendamentoService.listarDisponiveisComNomes("Cardiologia");

        assertThat(vagas).extracting(VagaHorarioResponse::id).containsExactly(1L, 2L);
        assertThat(vagas.get(0).profissionalNome()).isNull();
        assertThat(vagas.get(1).profissionalNome()).isEqualTo("Dra. Elisa (Cardiologia)");
        assertThat(vagas.get(1).unidadeNome()).isEqualTo("Centro Especializado Cardio");
    }

    @Test
    void respostaDoAgendamentoTrazProfissionalEUnidadeDaVaga() {
        when(vagaHorarioRepository.findById(7L)).thenReturn(Optional.of(vagaOcupada()));
        when(profissionalRepository.findById(2L)).thenReturn(Optional.of(Profissional.builder().id(2L).nome("Dra. Elisa (Cardiologia)").build()));
        when(unidadeSaudeRepository.findById(2L)).thenReturn(Optional.of(UnidadeSaude.builder().id(2L).nome("Centro Especializado Cardio").build()));
        Agendamento agendamento = Agendamento.builder().id(30L).encaminhamentoId(56L).vagaId(7L).status(StatusAgendamento.CONFIRMADO).build();

        AgendamentoResponse resposta = agendamentoService.montarResposta(agendamento);

        assertThat(resposta.profissionalNome()).isEqualTo("Dra. Elisa (Cardiologia)");
        assertThat(resposta.unidadeNome()).isEqualTo("Centro Especializado Cardio");

        when(vagaHorarioRepository.findById(7L)).thenReturn(Optional.empty());
        assertThat(agendamentoService.montarResposta(agendamento).profissionalNome()).isNull();
    }

    @Test
    void criaVagasEmLoteNaEspecialidadeDoProfissionalSemDuplicar() {
        when(profissionalRepository.findById(2L)).thenReturn(Optional.of(
                Profissional.builder().id(2L).nome("Dra. Elisa (Cardiologia)").especialidade("Cardiologia").build()));
        when(unidadeSaudeRepository.findById(2L)).thenReturn(Optional.of(UnidadeSaude.builder().id(2L).nome("Cardio").build()));
        java.time.LocalDate segunda = java.time.LocalDate.now().plusWeeks(1).with(java.time.DayOfWeek.MONDAY);
        // Já existe vaga às 08:00 da segunda: deve ser ignorada.
        when(vagaHorarioRepository.findByProfissionalIdAndDataHoraBetween(eq(2L), any(), any()))
                .thenReturn(List.of(VagaHorario.builder().id(1L).dataHora(segunda.atTime(8, 0)).build()));
        VagaLoteRequest lote = new VagaLoteRequest(2L, 2L, segunda, segunda.plusDays(6),
                java.util.Set.of(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.WEDNESDAY),
                java.time.LocalTime.of(8, 0), java.time.LocalTime.of(9, 0), 30);

        VagaLoteResponse resposta = agendamentoService.criarVagasEmLote(lote);

        assertThat(resposta.criadas()).isEqualTo(3);
        assertThat(resposta.ignoradasDuplicadas()).isEqualTo(1);
        assertThat(resposta.especialidadeOuExame()).isEqualTo("Cardiologia");
        verify(vagaHorarioRepository).saveAll(argThat((List<VagaHorario> vagas) -> vagas.size() == 3
                && vagas.stream().allMatch(v -> v.getEspecialidadeOuExame().equals("Cardiologia")
                && v.getStatus() == StatusVaga.DISPONIVEL && v.getProfissionalId() == 2L)));
    }

    @Test
    void loteComProfissionalOuUnidadeInexistenteDa404() {
        when(profissionalRepository.findById(99L)).thenReturn(Optional.empty());
        VagaLoteRequest lote = new VagaLoteRequest(99L, 2L, java.time.LocalDate.now().plusDays(1), java.time.LocalDate.now().plusDays(1),
                java.util.Set.of(java.time.DayOfWeek.values()), java.time.LocalTime.of(8, 0), java.time.LocalTime.of(9, 0), 30);
        assertThatThrownBy(() -> agendamentoService.criarVagasEmLote(lote)).isInstanceOf(RecursoNaoEncontradoException.class);

        when(profissionalRepository.findById(2L)).thenReturn(Optional.of(Profissional.builder().id(2L).especialidade("Cardiologia").build()));
        when(unidadeSaudeRepository.findById(99L)).thenReturn(Optional.empty());
        VagaLoteRequest semUnidade = new VagaLoteRequest(2L, 99L, lote.dataInicio(), lote.dataFim(), lote.diasDaSemana(),
                lote.horaInicio(), lote.horaFim(), 30);
        assertThatThrownBy(() -> agendamentoService.criarVagasEmLote(semUnidade)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void recusaPeriodoComDataFinalAntesDaInicial() {
        assertThatThrownBy(() -> agendamentoService.listarMarcadasPorEspecialidade("Cardiologia", HOJE, HOJE.minusDays(1)))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    // --- Agenda do especialista ----------------------------------------------------------------------

    private void encaminhamentoDoPaciente(long encaminhamentoId, long atendimentoId, long pacienteId, String nome,
                                          com.marcaai.encaminhamento.StatusEncaminhamento status) {
        when(encaminhamentoService.buscarPorId(encaminhamentoId)).thenReturn(Encaminhamento.builder().id(encaminhamentoId)
                .atendimentoId(atendimentoId).especialidadeOuExame("Cardiologia").status(status).build());
        when(atendimentoService.buscarPorId(atendimentoId))
                .thenReturn(Atendimento.builder().id(atendimentoId).pacienteId(pacienteId).build());
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(Paciente.builder().id(pacienteId).nome(nome).build()));
    }

    @Test
    void agendaDoEspecialistaFiltraPorSituacaoNomeSemAcentoEPeriodo() {
        vaga(7L, "Cardiologia", DATA_ATUAL, StatusVaga.OCUPADA);
        vaga(8L, "Cardiologia", DATA_ATUAL.plusDays(3), StatusVaga.OCUPADA);
        Agendamento aAtender = Agendamento.builder().id(1L).encaminhamentoId(56L).vagaId(8L).dataHora(DATA_ATUAL.plusDays(3))
                .status(StatusAgendamento.CONFIRMADO).build();
        Agendamento atendido = Agendamento.builder().id(2L).encaminhamentoId(57L).vagaId(7L).dataHora(DATA_ATUAL)
                .status(StatusAgendamento.CONFIRMADO).build();
        when(agendamentoRepository.listarDoProfissional(2L, StatusAgendamento.CANCELADO)).thenReturn(List.of(atendido, aAtender));
        encaminhamentoDoPaciente(56L, 560L, 5600L, "João Araújo", com.marcaai.encaminhamento.StatusEncaminhamento.AGENDADO);
        encaminhamentoDoPaciente(57L, 570L, 5700L, "Maria Souza", com.marcaai.encaminhamento.StatusEncaminhamento.REALIZADO);

        assertThat(agendamentoService.listarAgendaDoProfissional(2L, null, null, null, null))
                .extracting(AgendaEncaminhamentoResponse::encaminhamentoId).containsExactly(57L, 56L);
        assertThat(agendamentoService.listarAgendaDoProfissional(2L, SituacaoAtendimento.A_ATENDER, null, null, null))
                .extracting(AgendaEncaminhamentoResponse::pacienteNome).containsExactly("João Araújo");
        assertThat(agendamentoService.listarAgendaDoProfissional(2L, SituacaoAtendimento.ATENDIDO, null, null, null))
                .extracting(AgendaEncaminhamentoResponse::encaminhamentoId).containsExactly(57L);
        assertThat(agendamentoService.listarAgendaDoProfissional(2L, null, "joao ARAUJO", null, null))
                .as("nome sem acento e sem diferenciar maiúsculas").extracting(AgendaEncaminhamentoResponse::encaminhamentoId)
                .containsExactly(56L);
        assertThat(agendamentoService.listarAgendaDoProfissional(2L, null, null,
                DATA_ATUAL.toLocalDate().plusDays(1), DATA_ATUAL.toLocalDate().plusDays(3)))
                .extracting(AgendaEncaminhamentoResponse::encaminhamentoId).containsExactly(56L);
        assertThatThrownBy(() -> agendamentoService.listarAgendaDoProfissional(2L, null, null, HOJE, HOJE.minusDays(1)))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void vagasDoEspecialistaMostramLivresEOcupadasComOPaciente() {
        VagaHorario livre = VagaHorario.builder().id(7L).unidadeId(2L).profissionalId(2L).especialidadeOuExame("Cardiologia")
                .dataHora(DATA_ATUAL).status(StatusVaga.DISPONIVEL).build();
        VagaHorario ocupada = VagaHorario.builder().id(8L).unidadeId(2L).profissionalId(2L).especialidadeOuExame("Cardiologia")
                .dataHora(DATA_ATUAL.plusDays(1)).status(StatusVaga.OCUPADA).build();
        when(vagaHorarioRepository.findByProfissionalIdOrderByDataHora(2L)).thenReturn(List.of(livre, ocupada));
        // Na vaga ocupada houve um agendamento cancelado antes; vale o que está em vigor.
        when(agendamentoRepository.findByVagaIdOrderByIdDesc(8L)).thenReturn(List.of(
                Agendamento.builder().id(4L).encaminhamentoId(56L).vagaId(8L).status(StatusAgendamento.CONFIRMADO).build(),
                Agendamento.builder().id(3L).encaminhamentoId(99L).vagaId(8L).status(StatusAgendamento.CANCELADO).build()));
        encaminhamentoDoPaciente(56L, 560L, 5600L, "João Araújo", com.marcaai.encaminhamento.StatusEncaminhamento.AGENDADO);

        List<AgendaVagaResponse> todas = agendamentoService.listarVagasDoProfissional(2L, null, null, null);
        assertThat(todas).extracting(AgendaVagaResponse::vagaId).containsExactly(7L, 8L);
        assertThat(todas.get(0).pacienteNome()).isNull();
        assertThat(todas.get(1).pacienteNome()).isEqualTo("João Araújo");
        assertThat(todas.get(1).agendamentoId()).isEqualTo(4L);

        assertThat(agendamentoService.listarVagasDoProfissional(2L, StatusVaga.DISPONIVEL, null, null))
                .extracting(AgendaVagaResponse::vagaId).containsExactly(7L);
        assertThat(agendamentoService.listarVagasDoProfissional(2L, StatusVaga.OCUPADA, null, null))
                .extracting(AgendaVagaResponse::vagaId).containsExactly(8L);
    }

    @Test
    void profissionalDaVagaEmVigorIgnoraCancelado() {
        vaga(7L, "Cardiologia", DATA_ATUAL, StatusVaga.OCUPADA);
        when(agendamentoRepository.findFirstByEncaminhamentoIdAndStatusNotOrderByIdDesc(56L, StatusAgendamento.CANCELADO))
                .thenReturn(Optional.of(Agendamento.builder().id(1L).encaminhamentoId(56L).vagaId(7L).build()));
        assertThat(agendamentoService.profissionalDaVagaEmVigor(56L)).contains(2L);
        assertThat(agendamentoService.profissionalDaVagaEmVigor(57L)).isEmpty();
    }

    // --- Confirmação de presença pelo paciente -------------------------------------------------------

    private Agendamento agendamentoFuturo(com.marcaai.encaminhamento.StatusEncaminhamento statusEncaminhamento) {
        Agendamento agendamento = Agendamento.builder().id(40L).encaminhamentoId(60L).vagaId(7L)
                .dataHora(LocalDateTime.now().plusDays(3)).status(StatusAgendamento.CONFIRMADO).agendadoPor("secretaria").build();
        when(agendamentoRepository.findById(40L)).thenReturn(Optional.of(agendamento));
        when(encaminhamentoService.buscarPorId(60L))
                .thenReturn(Encaminhamento.builder().id(60L).status(statusEncaminhamento).build());
        return agendamento;
    }

    @Test
    void pacienteConfirmaPresencaUmaVezSo() {
        Agendamento agendamento = agendamentoFuturo(com.marcaai.encaminhamento.StatusEncaminhamento.AGENDADO);

        agendamentoService.confirmarPresenca(40L);
        LocalDateTime primeira = agendamento.getPresencaConfirmadaEm();
        assertThat(primeira).isNotNull();

        agendamentoService.confirmarPresenca(40L);
        assertThat(agendamento.getPresencaConfirmadaEm()).as("confirmar de novo não muda o momento").isEqualTo(primeira);
        verify(agendamentoRepository, times(1)).save(agendamento);
    }

    @Test
    void naoConfirmaPresencaDeAtendidoCanceladoOuDataPassada() {
        agendamentoFuturo(com.marcaai.encaminhamento.StatusEncaminhamento.REALIZADO);
        assertThatThrownBy(() -> agendamentoService.confirmarPresenca(40L)).as("já atendido: 409").isInstanceOf(ConflitoException.class);

        Agendamento passado = agendamentoFuturo(com.marcaai.encaminhamento.StatusEncaminhamento.AGENDADO);
        passado.setDataHora(LocalDateTime.now().minusHours(1));
        assertThatThrownBy(() -> agendamentoService.confirmarPresenca(40L)).isInstanceOf(RegraDeNegocioException.class);

        Agendamento cancelado = agendamentoFuturo(com.marcaai.encaminhamento.StatusEncaminhamento.AGENDADO);
        cancelado.setStatus(StatusAgendamento.CANCELADO);
        assertThatThrownBy(() -> agendamentoService.confirmarPresenca(40L)).as("cancelado: 409").isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> agendamentoService.confirmarPresenca(99L)).isInstanceOf(RecursoNaoEncontradoException.class);
    }


    // --- Dados ausentes e bordas ---------------------------------------------------------------------

    @Test
    void naoAgendaEmVagaInexistenteOuJaOcupada() {
        assertThatThrownBy(() -> agendamentoService.criar(new AgendamentoRequest(56L, 404L)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        vaga(7L, "Cardiologia", DATA_ATUAL, StatusVaga.OCUPADA);
        assertThatThrownBy(() -> agendamentoService.criar(new AgendamentoRequest(56L, 7L)))
                .isInstanceOf(ConflitoException.class);
        verify(agendamentoRepository, never()).save(any());
    }

    @Test
    void remarcarParaVagaInexistenteDa404SemMexerNoAgendamento() {
        Agendamento agendamento = confirmadoNaVaga7();
        vaga(7L, "Cardiologia", DATA_ATUAL, StatusVaga.OCUPADA);

        assertThatThrownBy(() -> agendamentoService.remarcar(30L, 404L, "motivo"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(agendamento.getVagaId()).isEqualTo(7L);
        verify(historicoRepository, never()).save(any());
    }

    @Test
    void buscarAgendamentoInexistenteDa404() {
        assertThatThrownBy(() -> agendamentoService.buscarPorId(404L)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void respostaDeAgendamentoAindaNaoSalvoNaoConsultaHistorico() {
        Agendamento novo = Agendamento.builder().encaminhamentoId(56L).vagaId(7L).status(StatusAgendamento.CONFIRMADO).build();

        AgendamentoResponse resposta = agendamentoService.montarResposta(novo);

        assertThat(resposta.historico()).isEmpty();
        verifyNoInteractions(historicoRepository);
    }

    @Test
    void vagaMarcadaSemAgendamentoOuComEncaminhamentoApagadoNaoQuebraALista() {
        VagaHorario semAgendamento = vagaOcupada();
        VagaHorario semEncaminhamento = VagaHorario.builder().id(8L).unidadeId(2L).profissionalId(2L)
                .especialidadeOuExame("Cardiologia").dataHora(LocalDateTime.of(2026, 9, 26, 9, 0))
                .status(StatusVaga.OCUPADA).build();
        when(vagaHorarioRepository.findByEspecialidadeOuExameIgnoreCaseAndStatusAndDataHoraGreaterThanEqualOrderByDataHora(
                "Cardiologia", StatusVaga.OCUPADA, HOJE.atStartOfDay())).thenReturn(List.of(semAgendamento, semEncaminhamento));
        when(agendamentoRepository.findByVagaIdOrderByIdDesc(8L)).thenReturn(List.of(Agendamento.builder()
                .id(31L).encaminhamentoId(404L).vagaId(8L).status(StatusAgendamento.CONFIRMADO).build()));
        when(encaminhamentoService.buscarPorId(404L)).thenThrow(new RecursoNaoEncontradoException("não existe"));

        List<VagaMarcadaResponse> marcadas = agendamentoService.listarMarcadasPorEspecialidade("Cardiologia", HOJE, null);

        assertThat(marcadas).hasSize(2);
        assertThat(marcadas.get(0).agendamentoId()).isNull();
        assertThat(marcadas.get(0).pacienteNome()).isNull();
        assertThat(marcadas.get(1).agendamentoId()).isEqualTo(31L);
        assertThat(marcadas.get(1).pacienteNome()).isNull();
    }

    @Test
    void agendaDoEspecialistaIgnoraEncaminhamentoApagadoEAceitaVagaSemCadastro() {
        Agendamento orfao = Agendamento.builder().id(1L).encaminhamentoId(404L).vagaId(7L).dataHora(DATA_ATUAL)
                .status(StatusAgendamento.CONFIRMADO).build();
        Agendamento semVaga = Agendamento.builder().id(2L).encaminhamentoId(56L).vagaId(404L).dataHora(DATA_ATUAL)
                .status(StatusAgendamento.CONFIRMADO).build();
        when(agendamentoRepository.listarDoProfissional(2L, StatusAgendamento.CANCELADO)).thenReturn(List.of(orfao, semVaga));
        when(encaminhamentoService.buscarPorId(404L)).thenThrow(new RecursoNaoEncontradoException("não existe"));
        encaminhamentoDoPaciente(56L, 560L, 5600L, "João Araújo", com.marcaai.encaminhamento.StatusEncaminhamento.AGENDADO);

        List<AgendaEncaminhamentoResponse> agenda = agendamentoService.listarAgendaDoProfissional(2L, null, null, null, null);

        assertThat(agenda).extracting(AgendaEncaminhamentoResponse::encaminhamentoId).containsExactly(56L);
        assertThat(agenda.get(0).unidadeNome()).isNull();
    }

    @Test
    void vagaOcupadaDoEspecialistaSemAgendamentoEmVigorOuComEncaminhamentoApagado() {
        VagaHorario soCancelado = VagaHorario.builder().id(7L).unidadeId(2L).profissionalId(2L).especialidadeOuExame("Cardiologia")
                .dataHora(DATA_ATUAL).status(StatusVaga.OCUPADA).build();
        VagaHorario orfa = VagaHorario.builder().id(8L).unidadeId(2L).profissionalId(2L).especialidadeOuExame("Cardiologia")
                .dataHora(DATA_ATUAL.plusDays(1)).status(StatusVaga.OCUPADA).build();
        when(vagaHorarioRepository.findByProfissionalIdOrderByDataHora(2L)).thenReturn(List.of(soCancelado, orfa));
        when(agendamentoRepository.findByVagaIdOrderByIdDesc(7L)).thenReturn(List.of(
                Agendamento.builder().id(3L).encaminhamentoId(99L).vagaId(7L).status(StatusAgendamento.CANCELADO).build()));
        when(agendamentoRepository.findByVagaIdOrderByIdDesc(8L)).thenReturn(List.of(
                Agendamento.builder().id(4L).encaminhamentoId(404L).vagaId(8L).status(StatusAgendamento.CONFIRMADO).build()));
        when(encaminhamentoService.buscarPorId(404L)).thenThrow(new RecursoNaoEncontradoException("não existe"));

        List<AgendaVagaResponse> vagas = agendamentoService.listarVagasDoProfissional(2L, null, null, null);

        assertThat(vagas.get(0).agendamentoId()).isNull();
        assertThat(vagas.get(1).agendamentoId()).isEqualTo(4L);
        assertThat(vagas.get(1).statusEncaminhamento()).isNull();
        assertThat(vagas.get(1).pacienteNome()).isNull();
    }

    @Test
    void periodoDaAgendaComSoUmaDasDatasOuComDataIgual() {
        VagaHorario vaga = VagaHorario.builder().id(7L).unidadeId(2L).profissionalId(2L).especialidadeOuExame("Cardiologia")
                .dataHora(DATA_ATUAL).status(StatusVaga.DISPONIVEL).build();
        when(vagaHorarioRepository.findByProfissionalIdOrderByDataHora(2L)).thenReturn(List.of(vaga));
        LocalDate dia = DATA_ATUAL.toLocalDate();

        assertThat(agendamentoService.listarVagasDoProfissional(2L, null, dia, dia)).hasSize(1);
        assertThat(agendamentoService.listarVagasDoProfissional(2L, null, dia.plusDays(1), null)).isEmpty();
        assertThat(agendamentoService.listarVagasDoProfissional(2L, null, null, dia.minusDays(1))).isEmpty();
        assertThat(agendamentoService.listarVagasDoProfissional(2L, null, null, dia)).hasSize(1);
        assertThatThrownBy(() -> agendamentoService.listarVagasDoProfissional(2L, null, dia, dia.minusDays(1)))
                .isInstanceOf(RegraDeNegocioException.class);
    }
}
