package com.marcaai.vagasagendamento;

import com.marcaai.atendimento.AtendimentoService;
import com.marcaai.config.AuthContext;
import com.marcaai.config.RegraDeNegocioException;
import com.marcaai.config.ConflitoException;
import com.marcaai.encaminhamento.Encaminhamento;
import com.marcaai.encaminhamento.EncaminhamentoService;
import com.marcaai.encaminhamento.StatusEncaminhamento;
import com.marcaai.shared.Paciente;
import com.marcaai.shared.PacienteRepository;
import com.marcaai.shared.Profissional;
import com.marcaai.shared.ProfissionalRepository;
import com.marcaai.shared.UnidadeSaude;
import com.marcaai.shared.UnidadeSaudeRepository;
import com.marcaai.config.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AgendamentoService {

    private final AgendamentoRepository agendamentoRepository;
    private final VagaHorarioRepository vagaHorarioRepository;
    private final EncaminhamentoService encaminhamentoService;
    private final AtendimentoService atendimentoService;
    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final UnidadeSaudeRepository unidadeSaudeRepository;
    private final HistoricoAgendamentoRepository historicoAgendamentoRepository;

    @Transactional
    public Agendamento criar(AgendamentoRequest request) {
        VagaHorario vaga = vagaHorarioRepository.findById(request.vagaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vaga não encontrada: " + request.vagaId()));
        if (vaga.getStatus() != StatusVaga.DISPONIVEL) {
            throw new ConflitoException("Vaga " + vaga.getId() + " não está disponível");
        }

        // Garante que o encaminhamento existe antes de confirmar o agendamento.
        encaminhamentoService.buscarPorId(request.encaminhamentoId());
        if (agendamentoEmVigor(request.encaminhamentoId()).isPresent()) {
            throw new ConflitoException("Encaminhamento " + request.encaminhamentoId()
                    + " já tem agendamento; para mudar a data, remarque ou antecipe");
        }

        vaga.setStatus(StatusVaga.OCUPADA);
        vagaHorarioRepository.save(vaga);

        Agendamento agendamento = Agendamento.builder()
                .encaminhamentoId(request.encaminhamentoId())
                .vagaId(vaga.getId())
                .dataHora(vaga.getDataHora())
                .agendadoPor(AuthContext.atual().login())
                .build();
        Agendamento salvo = agendamentoRepository.save(agendamento);

        encaminhamentoService.atualizarStatus(request.encaminhamentoId(), StatusEncaminhamento.AGENDADO);

        return salvo;
    }

    public List<VagaHorario> listarDisponiveisPorEspecialidade(String especialidadeOuExame) {
        return vagaHorarioRepository.findByEspecialidadeOuExameIgnoreCaseAndStatus(especialidadeOuExame, StatusVaga.DISPONIVEL);
    }

    /**
     * Vagas ocupadas de uma especialidade/exame no período [{@code de}, {@code ate}] (datas inclusivas),
     * da mais próxima para a mais distante. {@code ate} nulo = sem limite final.
     */
    public List<VagaMarcadaResponse> listarMarcadasPorEspecialidade(String especialidadeOuExame, LocalDate de, LocalDate ate) {
        if (ate != null && ate.isBefore(de)) {
            throw new RegraDeNegocioException("A data final do período não pode ser anterior à data inicial");
        }
        List<VagaHorario> vagas = ate == null
                ? vagaHorarioRepository.findByEspecialidadeOuExameIgnoreCaseAndStatusAndDataHoraGreaterThanEqualOrderByDataHora(
                        especialidadeOuExame, StatusVaga.OCUPADA, de.atStartOfDay())
                : vagaHorarioRepository.findByEspecialidadeOuExameIgnoreCaseAndStatusAndDataHoraGreaterThanEqualAndDataHoraLessThanOrderByDataHora(
                        especialidadeOuExame, StatusVaga.OCUPADA, de.atStartOfDay(), ate.plusDays(1).atStartOfDay());
        return vagas.stream().map(this::montarVagaMarcada).toList();
    }

    /** Vagas disponíveis de uma especialidade/exame, da mais próxima para a mais distante, com os nomes resolvidos. */
    public List<VagaHorarioResponse> listarDisponiveisComNomes(String especialidadeOuExame) {
        return listarDisponiveisPorEspecialidade(especialidadeOuExame).stream()
                .sorted(java.util.Comparator.comparing(VagaHorario::getDataHora))
                .map(vaga -> VagaHorarioResponse.of(vaga, nomeDaUnidade(vaga), nomeDoProfissional(vaga)))
                .toList();
    }

    /**
     * Abre vagas em lote (repetição semanal). A especialidade é sempre a do profissional; horário que o
     * profissional já tem vaga é ignorado (não duplica) e informado na resposta.
     */
    @Transactional
    public VagaLoteResponse criarVagasEmLote(VagaLoteRequest request) {
        Profissional profissional = profissionalRepository.findById(request.profissionalId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Profissional não encontrado: " + request.profissionalId()));
        unidadeSaudeRepository.findById(request.unidadeId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Unidade não encontrada: " + request.unidadeId()));

        List<LocalDateTime> horarios = GeradorHorariosVagas.gerar(request, LocalDateTime.now());
        java.util.Set<LocalDateTime> jaExistentes = vagaHorarioRepository
                .findByProfissionalIdAndDataHoraBetween(profissional.getId(), horarios.get(0), horarios.get(horarios.size() - 1))
                .stream().map(VagaHorario::getDataHora).collect(java.util.stream.Collectors.toSet());

        List<VagaHorario> novas = horarios.stream()
                .filter(h -> !jaExistentes.contains(h))
                .map(h -> VagaHorario.builder()
                        .unidadeId(request.unidadeId())
                        .profissionalId(profissional.getId())
                        .especialidadeOuExame(profissional.getEspecialidade())
                        .dataHora(h)
                        .status(StatusVaga.DISPONIVEL)
                        .build())
                .toList();
        vagaHorarioRepository.saveAll(novas);

        return new VagaLoteResponse(profissional.getEspecialidade(), profissional.getNome(), novas.size(),
                horarios.size() - novas.size(),
                novas.isEmpty() ? null : novas.get(0).getDataHora(),
                novas.isEmpty() ? null : novas.get(novas.size() - 1).getDataHora());
    }

    /** Resposta do agendamento com o profissional e a unidade da vaga resolvidos por nome. */
    public AgendamentoResponse montarResposta(Agendamento agendamento) {
        VagaHorario vaga = vagaHorarioRepository.findById(agendamento.getVagaId()).orElse(null);
        List<HistoricoAgendamentoResponse> historico = agendamento.getId() == null ? List.of()
                : historicoAgendamentoRepository.findByAgendamentoIdOrderByFeitoEmAscIdAsc(agendamento.getId()).stream()
                        .map(HistoricoAgendamentoResponse::of)
                        .toList();
        return AgendamentoResponse.of(agendamento,
                vaga == null ? null : nomeDoProfissional(vaga),
                vaga == null ? null : nomeDaUnidade(vaga),
                vaga == null ? null : unidadeSaudeRepository.findById(vaga.getUnidadeId()).map(UnidadeSaude::getEndereco).orElse(null),
                historico);
    }

    private String nomeDoProfissional(VagaHorario vaga) {
        return profissionalRepository.findById(vaga.getProfissionalId()).map(Profissional::getNome).orElse(null);
    }

    private String nomeDaUnidade(VagaHorario vaga) {
        return unidadeSaudeRepository.findById(vaga.getUnidadeId()).map(UnidadeSaude::getNome).orElse(null);
    }

    private VagaMarcadaResponse montarVagaMarcada(VagaHorario vaga) {
        // Se a vaga já foi cancelada e remarcada, vale o agendamento mais recente.
        Agendamento agendamento = agendamentoRepository.findByVagaIdOrderByIdDesc(vaga.getId()).stream()
                .findFirst().orElse(null);
        return new VagaMarcadaResponse(
                vaga.getId(),
                vaga.getDataHora(),
                vaga.getEspecialidadeOuExame(),
                nomeDoProfissional(vaga),
                nomeDaUnidade(vaga),
                agendamento == null ? null : agendamento.getId(),
                agendamento == null ? null : agendamento.getStatus(),
                agendamento == null ? null : agendamento.getAgendadoPor(),
                agendamento == null ? null : agendamento.getEncaminhamentoId(),
                agendamento == null ? null : nomeDoPaciente(agendamento.getEncaminhamentoId()),
                agendamento == null ? null : agendamento.getPresencaConfirmadaEm());
    }

    /** Encaminhamento → atendimento → paciente, pelos serviços dos módulos donos de cada dado. */
    private String nomeDoPaciente(Long encaminhamentoId) {
        try {
            Long atendimentoId = encaminhamentoService.buscarPorId(encaminhamentoId).getAtendimentoId();
            Long pacienteId = atendimentoService.buscarPorId(atendimentoId).getPacienteId();
            return pacienteRepository.findById(pacienteId).map(Paciente::getNome).orElse(null);
        } catch (RecursoNaoEncontradoException ex) {
            return null;
        }
    }

    /** Vagas a partir de agora (livres e ocupadas), para medir capacidade e ocupação. */
    public List<VagaHorario> listarVagasFuturas() {
        return vagaHorarioRepository.findByDataHoraAfter(LocalDateTime.now());
    }

    public long contarVagasDisponiveis() {
        return vagaHorarioRepository.countByStatus(StatusVaga.DISPONIVEL);
    }

    public long contarAgendamentos() {
        return agendamentoRepository.count();
    }

    /** Inclui cancelados: é a cota da demonstração, que não deve reagendar quem a secretaria cancelou. */
    public long contarAgendamentosPorEspecialidade(String especialidadeOuExame) {
        return agendamentoRepository.contarPorEspecialidade(especialidadeOuExame);
    }

    /**
     * Cancela um agendamento confirmado: a vaga volta a ficar livre e o paciente volta a aguardar
     * agendamento na fila (o item de fila nunca saiu, então a posição volta a valer pelo score/ajuste).
     */
    @Transactional
    public Agendamento cancelar(Long agendamentoId, String motivo) {
        Agendamento agendamento = buscarConfirmado(agendamentoId);
        exigirMotivo(motivo);
        VagaHorario vaga = buscarVaga(agendamento.getVagaId());

        registrarHistorico(agendamento, AcaoAgendamento.CANCELADO, null, motivo);
        vaga.setStatus(StatusVaga.DISPONIVEL);
        vagaHorarioRepository.save(vaga);
        agendamento.setStatus(StatusAgendamento.CANCELADO);
        Agendamento salvo = agendamentoRepository.save(agendamento);
        encaminhamentoService.atualizarStatus(agendamento.getEncaminhamentoId(), StatusEncaminhamento.NA_FILA);
        return salvo;
    }

    /** Troca a vaga de um agendamento confirmado por outra livre da mesma especialidade, em qualquer data. */
    @Transactional
    public Agendamento remarcar(Long agendamentoId, Long novaVagaId, String motivo) {
        return trocarVaga(agendamentoId, novaVagaId, motivo, AcaoAgendamento.REMARCADO);
    }

    /** Como remarcar, mas só para uma vaga anterior à data atual do agendamento. */
    @Transactional
    public Agendamento antecipar(Long agendamentoId, Long novaVagaId, String motivo) {
        return trocarVaga(agendamentoId, novaVagaId, motivo, AcaoAgendamento.ANTECIPADO);
    }

    private Agendamento trocarVaga(Long agendamentoId, Long novaVagaId, String motivo, AcaoAgendamento acao) {
        Agendamento agendamento = buscarConfirmado(agendamentoId);
        exigirMotivo(motivo);
        VagaHorario vagaAtual = buscarVaga(agendamento.getVagaId());
        VagaHorario vagaNova = buscarVaga(novaVagaId);

        if (vagaNova.getId().equals(vagaAtual.getId())) {
            throw new RegraDeNegocioException("A vaga escolhida já é a do agendamento");
        }
        if (vagaNova.getStatus() != StatusVaga.DISPONIVEL) {
            throw new ConflitoException("Vaga " + vagaNova.getId() + " não está disponível");
        }
        if (!vagaNova.getEspecialidadeOuExame().equalsIgnoreCase(vagaAtual.getEspecialidadeOuExame())) {
            throw new RegraDeNegocioException("A vaga nova precisa ser da mesma especialidade/exame ("
                    + vagaAtual.getEspecialidadeOuExame() + ")");
        }
        if (acao == AcaoAgendamento.ANTECIPADO && !vagaNova.getDataHora().isBefore(agendamento.getDataHora())) {
            throw new RegraDeNegocioException("Para antecipar, a vaga nova precisa ser antes da data atual do agendamento");
        }

        registrarHistorico(agendamento, acao, vagaNova, motivo);
        vagaAtual.setStatus(StatusVaga.DISPONIVEL);
        vagaHorarioRepository.save(vagaAtual);
        vagaNova.setStatus(StatusVaga.OCUPADA);
        vagaHorarioRepository.save(vagaNova);
        agendamento.setVagaId(vagaNova.getId());
        agendamento.setDataHora(vagaNova.getDataHora());
        // A confirmação valia para a data anterior: o paciente precisa confirmar a nova.
        agendamento.setPresencaConfirmadaEm(null);
        return agendamentoRepository.save(agendamento);
    }

    /**
     * O paciente confirma que vai comparecer. Só no agendamento confirmado, ainda não atendido e com data
     * futura; confirmar de novo não muda o momento registrado.
     */
    @Transactional
    public Agendamento confirmarPresenca(Long agendamentoId) {
        Agendamento agendamento = buscarConfirmado(agendamentoId);
        if (agendamento.getPresencaConfirmadaEm() != null) {
            return agendamento;
        }
        if (encaminhamentoService.buscarPorId(agendamento.getEncaminhamentoId()).getStatus() != StatusEncaminhamento.AGENDADO) {
            throw new ConflitoException("Só é possível confirmar presença de um encaminhamento agendado e ainda não atendido");
        }
        if (!agendamento.getDataHora().isAfter(LocalDateTime.now())) {
            throw new RegraDeNegocioException("A data deste agendamento já passou");
        }
        agendamento.setPresencaConfirmadaEm(LocalDateTime.now());
        return agendamentoRepository.save(agendamento);
    }

    public Agendamento buscarPorId(Long agendamentoId) {
        return agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Agendamento não encontrado: " + agendamentoId));
    }

    /** Momento em que o paciente confirmou presença no agendamento em vigor; vazio se não confirmou ou não está agendado. */
    public Optional<LocalDateTime> presencaConfirmadaEm(Long encaminhamentoId) {
        return agendamentoEmVigor(encaminhamentoId).map(Agendamento::getPresencaConfirmadaEm);
    }

    private Agendamento buscarConfirmado(Long agendamentoId) {
        Agendamento agendamento = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Agendamento não encontrado: " + agendamentoId));
        if (agendamento.getStatus() != StatusAgendamento.CONFIRMADO) {
            throw new ConflitoException("Só é possível alterar agendamento confirmado (situação atual: "
                    + agendamento.getStatus() + ")");
        }
        return agendamento;
    }

    private VagaHorario buscarVaga(Long vagaId) {
        return vagaHorarioRepository.findById(vagaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vaga não encontrada: " + vagaId));
    }

    private static void exigirMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new RegraDeNegocioException("Informe o motivo — a mudança fica registrada para auditoria");
        }
    }

    private void registrarHistorico(Agendamento agendamento, AcaoAgendamento acao, VagaHorario vagaNova, String motivo) {
        historicoAgendamentoRepository.save(HistoricoAgendamento.builder()
                .agendamentoId(agendamento.getId())
                .acao(acao)
                .vagaAnteriorId(agendamento.getVagaId())
                .dataHoraAnterior(agendamento.getDataHora())
                .vagaNovaId(vagaNova == null ? null : vagaNova.getId())
                .dataHoraNova(vagaNova == null ? null : vagaNova.getDataHora())
                .motivo(motivo.trim())
                .feitoPor(AuthContext.atual().login())
                .feitoEm(LocalDateTime.now())
                .build());
    }

    private Optional<Agendamento> agendamentoEmVigor(Long encaminhamentoId) {
        return agendamentoRepository.findFirstByEncaminhamentoIdAndStatusNotOrderByIdDesc(
                encaminhamentoId, StatusAgendamento.CANCELADO);
    }

    /** Profissional dono da vaga do agendamento em vigor do encaminhamento; vazio se não estiver agendado. */
    public Optional<Long> profissionalDaVagaEmVigor(Long encaminhamentoId) {
        return agendamentoEmVigor(encaminhamentoId)
                .flatMap(agendamento -> vagaHorarioRepository.findById(agendamento.getVagaId()))
                .map(VagaHorario::getProfissionalId);
    }

    /**
     * Agenda do profissional: encaminhamentos agendados (sem os cancelados) nas vagas dele, da data mais
     * próxima para a mais distante. Filtros opcionais: situação, trecho do nome do paciente (sem diferenciar
     * caixa nem acento) e período [{@code de}, {@code ate}] com as duas datas incluídas.
     */
    public List<AgendaEncaminhamentoResponse> listarAgendaDoProfissional(Long profissionalId, SituacaoAtendimento situacao,
                                                                        String paciente, LocalDate de, LocalDate ate) {
        validarPeriodo(de, ate);
        String termo = normalizar(paciente);
        return agendamentoRepository.listarDoProfissional(profissionalId, StatusAgendamento.CANCELADO).stream()
                .filter(agendamento -> dentroDoPeriodo(agendamento.getDataHora(), de, ate))
                .map(this::montarItemDaAgenda)
                .filter(item -> item != null && (situacao == null || situacao.inclui(item.statusEncaminhamento())))
                .filter(item -> termo.isEmpty() || normalizar(item.pacienteNome()).contains(termo))
                .toList();
    }

    /**
     * Todas as vagas do profissional no período, livres e ocupadas, com o paciente de cada ocupada.
     * {@code status} nulo = todas.
     */
    public List<AgendaVagaResponse> listarVagasDoProfissional(Long profissionalId, StatusVaga status, LocalDate de, LocalDate ate) {
        validarPeriodo(de, ate);
        return vagaHorarioRepository.findByProfissionalIdOrderByDataHora(profissionalId).stream()
                .filter(vaga -> dentroDoPeriodo(vaga.getDataHora(), de, ate))
                .filter(vaga -> status == null || vaga.getStatus() == status)
                .map(this::montarVagaDaAgenda)
                .toList();
    }

    private AgendaEncaminhamentoResponse montarItemDaAgenda(Agendamento agendamento) {
        Encaminhamento encaminhamento;
        try {
            encaminhamento = encaminhamentoService.buscarPorId(agendamento.getEncaminhamentoId());
        } catch (RecursoNaoEncontradoException ex) {
            return null;
        }
        VagaHorario vaga = vagaHorarioRepository.findById(agendamento.getVagaId()).orElse(null);
        return new AgendaEncaminhamentoResponse(
                agendamento.getId(),
                encaminhamento.getId(),
                agendamento.getDataHora(),
                nomeDoPaciente(encaminhamento.getId()),
                encaminhamento.getEspecialidadeOuExame(),
                vaga == null ? null : nomeDaUnidade(vaga),
                encaminhamento.getStatus(),
                encaminhamento.isUrgente(),
                agendamento.getPresencaConfirmadaEm());
    }

    private AgendaVagaResponse montarVagaDaAgenda(VagaHorario vaga) {
        Agendamento agendamento = vaga.getStatus() == StatusVaga.DISPONIVEL ? null
                : agendamentoRepository.findByVagaIdOrderByIdDesc(vaga.getId()).stream()
                        .filter(a -> a.getStatus() != StatusAgendamento.CANCELADO)
                        .findFirst().orElse(null);
        StatusEncaminhamento statusEncaminhamento = null;
        if (agendamento != null) {
            try {
                statusEncaminhamento = encaminhamentoService.buscarPorId(agendamento.getEncaminhamentoId()).getStatus();
            } catch (RecursoNaoEncontradoException ex) {
                statusEncaminhamento = null;
            }
        }
        return new AgendaVagaResponse(
                vaga.getId(),
                vaga.getDataHora(),
                vaga.getEspecialidadeOuExame(),
                nomeDaUnidade(vaga),
                vaga.getStatus(),
                agendamento == null ? null : agendamento.getId(),
                agendamento == null ? null : agendamento.getEncaminhamentoId(),
                agendamento == null ? null : nomeDoPaciente(agendamento.getEncaminhamentoId()),
                statusEncaminhamento,
                agendamento == null ? null : agendamento.getPresencaConfirmadaEm());
    }

    private static void validarPeriodo(LocalDate de, LocalDate ate) {
        if (de != null && ate != null && ate.isBefore(de)) {
            throw new RegraDeNegocioException("A data final do período não pode ser anterior à data inicial");
        }
    }

    private static boolean dentroDoPeriodo(LocalDateTime dataHora, LocalDate de, LocalDate ate) {
        return (de == null || !dataHora.toLocalDate().isBefore(de)) && (ate == null || !dataHora.toLocalDate().isAfter(ate));
    }

    private static String normalizar(String texto) {
        if (texto == null) return "";
        return java.text.Normalizer.normalize(texto.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
    }

    public Agendamento buscarPorEncaminhamento(Long encaminhamentoId) {
        return agendamentoEmVigor(encaminhamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Ainda não há agendamento confirmado para o encaminhamento " + encaminhamentoId));
    }
}
