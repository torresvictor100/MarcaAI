package com.marcaai.acesso;

import com.marcaai.atendimento.Atendimento;
import com.marcaai.atendimento.AtendimentoService;
import com.marcaai.config.AuthContext;
import com.marcaai.config.MarcaAiPrincipal;
import com.marcaai.encaminhamento.Encaminhamento;
import com.marcaai.encaminhamento.EncaminhamentoService;
import com.marcaai.shared.Paciente;
import com.marcaai.shared.PacienteRepository;
import com.marcaai.shared.Profissional;
import com.marcaai.shared.ProfissionalRepository;
import com.marcaai.vagasagendamento.AgendamentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Regra única de quem pode ver ou mexer em dado clínico de um encaminhamento/atendimento. Antes cada
 * controller só barrava o PACIENTE, e qualquer outro papel lia o dado de qualquer paciente (ver ADR-007).
 *
 * <ul>
 *   <li>SECRETARIA / ADMIN: tudo (gestão da fila e da rede).</li>
 *   <li>PACIENTE: só o que é dele.</li>
 *   <li>MEDICO_UBS: só os atendimentos que ele registrou e os encaminhamentos gerados a partir deles.</li>
 *   <li>ESPECIALISTA: só encaminhamentos (consulta ou exame) da especialidade dele agendados numa vaga
 *       dele — não vê a fila nem o que está na agenda de outro especialista. O laboratório também entra
 *       aqui: a "especialidade" dele é o exame que realiza.</li>
 * </ul>
 * A especialidade do ESPECIALISTA vem do cadastro de profissional vinculado ao usuário;
 * usuário sem esse vínculo não acessa nada.
 */
@Service
@RequiredArgsConstructor
public class ControleAcessoService {

    private final EncaminhamentoService encaminhamentoService;
    private final AtendimentoService atendimentoService;
    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final AgendamentoService agendamentoService;

    /** Leitura de um encaminhamento e dos seus sub-recursos (timeline, documentos, análise, fila, agendamento, resultado). */
    public void verificarLeituraDoEncaminhamento(Long encaminhamentoId) {
        Encaminhamento encaminhamento = encaminhamentoService.buscarPorId(encaminhamentoId);
        if (!podeLer(AuthContext.atual(), encaminhamento)) {
            throw new AccessDeniedException("Sem acesso ao encaminhamento " + encaminhamentoId);
        }
    }

    public void verificarLeituraDoAtendimento(Atendimento atendimento) {
        MarcaAiPrincipal principal = AuthContext.atual();
        boolean permitido = switch (principal.papel()) {
            case SECRETARIA, ADMIN -> true;
            case PACIENTE -> ehDoPaciente(principal, atendimento);
            case MEDICO_UBS -> ehDoMedico(principal, atendimento);
            // Especialista vê o atendimento de origem só se enxerga algum encaminhamento dele.
            case ESPECIALISTA -> encaminhamentoService.listarPorAtendimento(atendimento.getId()).stream()
                    .anyMatch(encaminhamento -> podeLer(principal, encaminhamento));
        };
        if (!permitido) {
            throw new AccessDeniedException("Sem acesso ao atendimento " + atendimento.getId());
        }
    }

    /** Médico da UBS só encaminha a partir de um atendimento que ele mesmo registrou. */
    public void verificarAtendimentoDoMedicoLogado(Long atendimentoId) {
        Atendimento atendimento = atendimentoService.buscarPorId(atendimentoId);
        if (!ehDoMedico(AuthContext.atual(), atendimento)) {
            throw new AccessDeniedException("Médico só pode encaminhar a partir dos próprios atendimentos");
        }
    }

    /** Médico da UBS só anexa documento em encaminhamento gerado por um atendimento dele. */
    public void verificarEncaminhamentoDoMedicoLogado(Long encaminhamentoId) {
        Encaminhamento encaminhamento = encaminhamentoService.buscarPorId(encaminhamentoId);
        Atendimento atendimento = atendimentoService.buscarPorId(encaminhamento.getAtendimentoId());
        if (!ehDoMedico(AuthContext.atual(), atendimento)) {
            throw new AccessDeniedException("Médico só pode anexar documentos aos próprios encaminhamentos");
        }
    }

    /**
     * Quem registra o desfecho: o especialista, para a consulta ou exame da especialidade dele agendado
     * numa vaga dele. As regras de estado (já agendado, sem resultado anterior) ficam
     * no {@code ResultadoExameService}.
     */
    public void verificarRegistroDeResultado(Long encaminhamentoId) {
        Encaminhamento encaminhamento = encaminhamentoService.buscarPorId(encaminhamentoId);
        MarcaAiPrincipal principal = AuthContext.atual();
        boolean permitido = switch (principal.papel()) {
            case ESPECIALISTA -> podeLer(principal, encaminhamento);
            default -> false;
        };
        if (!permitido) {
            throw new AccessDeniedException("Só o especialista responsável registra o resultado");
        }
    }

    /**
     * Encaminhamentos de um paciente que o usuário logado pode ver: tudo para o próprio paciente e para a
     * gestão; para o médico da UBS, só os que ele gerou. Outros papéis não usam esta listagem.
     */
    public List<Encaminhamento> encaminhamentosVisiveisDoPaciente(Long pacienteId) {
        MarcaAiPrincipal principal = AuthContext.atual();
        return switch (principal.papel()) {
            case SECRETARIA, ADMIN -> encaminhamentoService.listarPorPaciente(pacienteId);
            case PACIENTE -> {
                boolean ehOProprio = pacienteDoUsuario(principal).map(Paciente::getId).map(pacienteId::equals).orElse(false);
                if (!ehOProprio) {
                    throw new AccessDeniedException("Paciente só pode consultar os próprios encaminhamentos");
                }
                yield encaminhamentoService.listarPorPaciente(pacienteId);
            }
            case MEDICO_UBS -> encaminhamentoService.listarPorPaciente(pacienteId).stream()
                    .filter(encaminhamento -> podeLer(principal, encaminhamento))
                    .toList();
            case ESPECIALISTA ->
                    throw new AccessDeniedException("Listagem por paciente é só do paciente, do médico da UBS e da gestão");
        };
    }

    /**
     * Atendimentos de um paciente que o usuário logado pode ver, do mais recente para o mais antigo:
     * tudo para a gestão; para o paciente, só os próprios; para o médico da UBS, só os que ele registrou.
     */
    public List<Atendimento> atendimentosVisiveisDoPaciente(Long pacienteId) {
        MarcaAiPrincipal principal = AuthContext.atual();
        List<Atendimento> atendimentos = switch (principal.papel()) {
            case SECRETARIA, ADMIN -> atendimentoService.listarPorPaciente(pacienteId);
            case PACIENTE -> {
                boolean ehOProprio = pacienteDoUsuario(principal).map(Paciente::getId).map(pacienteId::equals).orElse(false);
                if (!ehOProprio) {
                    throw new AccessDeniedException("Paciente só pode consultar os próprios atendimentos");
                }
                yield atendimentoService.listarPorPaciente(pacienteId);
            }
            case MEDICO_UBS -> atendimentoService.listarPorPaciente(pacienteId).stream()
                    .filter(atendimento -> ehDoMedico(principal, atendimento))
                    .toList();
            case ESPECIALISTA ->
                    throw new AccessDeniedException("Listagem de atendimentos por paciente é só do paciente, do médico da UBS e da gestão");
        };
        return atendimentos.stream()
                .sorted(Comparator.comparing(Atendimento::getData, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /** Profissional vinculado ao usuário logado — é ele quem assina o atendimento, nunca um id vindo do corpo da requisição. */
    public Long profissionalIdDoUsuarioLogado() {
        return profissionalDoUsuario(AuthContext.atual())
                .map(Profissional::getId)
                .orElseThrow(() -> new AccessDeniedException("Usuário não está vinculado a um cadastro de profissional"));
    }

    private boolean podeLer(MarcaAiPrincipal principal, Encaminhamento encaminhamento) {
        return switch (principal.papel()) {
            case SECRETARIA, ADMIN -> true;
            case PACIENTE -> ehDoPaciente(principal, atendimentoDe(encaminhamento));
            case MEDICO_UBS -> ehDoMedico(principal, atendimentoDe(encaminhamento));
            case ESPECIALISTA -> ehDaEspecialidade(principal, encaminhamento)
                    && ehDaAgendaDoProfissional(principal, encaminhamento);
        };
    }

    private Atendimento atendimentoDe(Encaminhamento encaminhamento) {
        return atendimentoService.buscarPorId(encaminhamento.getAtendimentoId());
    }

    private boolean ehDoPaciente(MarcaAiPrincipal principal, Atendimento atendimento) {
        return pacienteDoUsuario(principal).map(Paciente::getId).map(atendimento.getPacienteId()::equals).orElse(false);
    }

    private boolean ehDoMedico(MarcaAiPrincipal principal, Atendimento atendimento) {
        return profissionalDoUsuario(principal).map(Profissional::getId).map(atendimento.getProfissionalId()::equals).orElse(false);
    }

    private boolean ehDaEspecialidade(MarcaAiPrincipal principal, Encaminhamento encaminhamento) {
        return profissionalDoUsuario(principal)
                .map(Profissional::getEspecialidade)
                .map(encaminhamento.getEspecialidadeOuExame()::equalsIgnoreCase)
                .orElse(false);
    }

    /** Agendamento em vigor (não cancelado) numa vaga do profissional logado. */
    private boolean ehDaAgendaDoProfissional(MarcaAiPrincipal principal, Encaminhamento encaminhamento) {
        Optional<Long> profissionalDaVaga = agendamentoService.profissionalDaVagaEmVigor(encaminhamento.getId());
        return profissionalDoUsuario(principal).map(Profissional::getId)
                .map(id -> profissionalDaVaga.map(id::equals).orElse(false))
                .orElse(false);
    }

    private Optional<Paciente> pacienteDoUsuario(MarcaAiPrincipal principal) {
        return pacienteRepository.findByUsuarioId(principal.usuarioId());
    }

    private Optional<Profissional> profissionalDoUsuario(MarcaAiPrincipal principal) {
        return profissionalRepository.findByUsuarioId(principal.usuarioId());
    }
}
