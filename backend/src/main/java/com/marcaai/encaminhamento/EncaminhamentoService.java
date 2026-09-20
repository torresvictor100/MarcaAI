package com.marcaai.encaminhamento;

import com.marcaai.atendimento.Atendimento;
import com.marcaai.atendimento.AtendimentoService;
import com.marcaai.config.RegraDeNegocioException;
import com.marcaai.config.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EncaminhamentoService {

    private static final Set<StatusEncaminhamento> STATUS_QUE_REFAZEM_TRIAGEM = EnumSet.of(
            StatusEncaminhamento.AGUARDANDO_DOCUMENTOS, StatusEncaminhamento.EM_ANALISE,
            StatusEncaminhamento.BLOQUEADO_REVISAO, StatusEncaminhamento.NA_FILA);

    private final EncaminhamentoRepository encaminhamentoRepository;
    private final DocumentoRepository documentoRepository;
    private final CidRepository cidRepository;
    private final TipoDocumentoExigidoRepository tipoDocumentoExigidoRepository;
    private final AtendimentoService atendimentoService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Encaminhamento criar(EncaminhamentoRequest request) {
        Atendimento atendimento = atendimentoService.buscarPorId(request.atendimentoId());

        Cid cid = cidRepository.findById(request.cidId())
                .orElseThrow(() -> new RegraDeNegocioException("CID inexistente na lista fechada: " + request.cidId()));

        if (request.urgente() && (request.justificativaUrgencia() == null || request.justificativaUrgencia().isBlank())) {
            throw new RegraDeNegocioException("Encaminhamento marcado como urgente precisa de justificativaUrgencia preenchida");
        }
        if (request.documentos() == null || request.documentos().isEmpty()) {
            throw new RegraDeNegocioException("Encaminhamento precisa de pelo menos um documento anexado");
        }

        Encaminhamento encaminhamento = Encaminhamento.builder()
                .atendimentoId(atendimento.getId())
                .tipo(request.tipo())
                .especialidadeOuExame(request.especialidadeOuExame())
                .cidId(cid.getId())
                .urgente(request.urgente())
                .justificativaUrgencia(request.justificativaUrgencia())
                .build();
        Encaminhamento salvo = encaminhamentoRepository.save(encaminhamento);
        // Mesma transação: se um documento falhar, o encaminhamento também não é criado.
        request.documentos().forEach(documento -> documentoRepository.save(novoDocumento(salvo.getId(), documento)));
        // Com documento anexado, a triagem roda na hora; o que faltar vira alerta na análise, não trava a fila (ADR-009).
        enviarParaTriagem(salvo);
        return salvo;
    }

    public Encaminhamento buscarPorId(Long id) {
        return encaminhamentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Encaminhamento não encontrado: " + id));
    }

    public List<Encaminhamento> listarPorStatus(StatusEncaminhamento status) {
        return encaminhamentoRepository.findByStatus(status);
    }

    public List<Documento> listarDocumentos(Long encaminhamentoId) {
        return documentoRepository.findByEncaminhamentoId(encaminhamentoId);
    }

    public Cid buscarCid(Long cidId) {
        return cidRepository.findById(cidId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("CID não encontrado: " + cidId));
    }

    public List<Cid> listarCidsPorEspecialidade(String especialidade) {
        return cidRepository.buscarPorEspecialidadeParcial(especialidade);
    }

    public List<String> listarEspecialidades() {
        return cidRepository.listarEspecialidadesDistintas();
    }

    /**
     * Anexa um documento e refaz a triagem enquanto o encaminhamento não foi agendado — a análise e o score
     * ficam em dia com os documentos. Agendado/atendido/cancelado só guarda o documento, para não tirar o
     * paciente da vaga.
     */
    @Transactional
    public Documento adicionarDocumento(Long encaminhamentoId, DocumentoRequest request) {
        Encaminhamento encaminhamento = buscarPorId(encaminhamentoId);
        Documento salvo = documentoRepository.save(novoDocumento(encaminhamento.getId(), request));
        if (STATUS_QUE_REFAZEM_TRIAGEM.contains(encaminhamento.getStatus())) {
            enviarParaTriagem(encaminhamento);
        }
        return salvo;
    }

    /** Tipos de documento exigidos para a especialidade/exame, na ordem do cadastro. */
    public List<String> listarDocumentosExigidos(String especialidadeOuExame) {
        return tipoDocumentoExigidoRepository.findByEspecialidadeOuExameIgnoreCase(especialidadeOuExame.trim()).stream()
                .map(t -> t.getTipoDocumento().toUpperCase())
                .distinct()
                .toList();
    }

    /** Tipos exigidos para a especialidade do encaminhamento que ainda não estão entre os documentos anexados. */
    public List<String> tiposDocumentoFaltantes(Encaminhamento encaminhamento, List<Documento> anexados) {
        Set<String> tiposAnexados = anexados.stream()
                .map(d -> d.getTipo().toUpperCase())
                .collect(Collectors.toSet());
        return listarDocumentosExigidos(encaminhamento.getEspecialidadeOuExame()).stream()
                .filter(tipo -> !tiposAnexados.contains(tipo))
                .toList();
    }

    private Documento novoDocumento(Long encaminhamentoId, DocumentoRequest request) {
        return Documento.builder()
                .encaminhamentoId(encaminhamentoId)
                .tipo(request.tipo())
                .referenciaArquivo(request.referenciaArquivo())
                .dataEmissao(request.dataEmissao())
                .validade(request.validade())
                .build();
    }

    private void enviarParaTriagem(Encaminhamento encaminhamento) {
        encaminhamento.setStatus(StatusEncaminhamento.EM_ANALISE);
        encaminhamentoRepository.save(encaminhamento);
        eventPublisher.publishEvent(new TriagemSolicitadaEvent(encaminhamento.getId()));
    }

    @Transactional
    public void atualizarStatus(Long encaminhamentoId, StatusEncaminhamento novoStatus) {
        Encaminhamento encaminhamento = buscarPorId(encaminhamentoId);
        encaminhamento.setStatus(novoStatus);
        encaminhamentoRepository.save(encaminhamento);
    }

    public List<Encaminhamento> listarPorAtendimento(Long atendimentoId) {
        return encaminhamentoRepository.findByAtendimentoId(atendimentoId);
    }

    public List<Encaminhamento> listarPorPaciente(Long pacienteId) {
        List<Long> atendimentoIds = atendimentoService.listarPorPaciente(pacienteId).stream()
                .map(Atendimento::getId)
                .toList();
        return atendimentoIds.isEmpty() ? List.of() : encaminhamentoRepository.findByAtendimentoIdIn(atendimentoIds);
    }

    public Long pacienteIdDoEncaminhamento(Long encaminhamentoId) {
        Encaminhamento encaminhamento = buscarPorId(encaminhamentoId);
        Atendimento atendimento = atendimentoService.buscarPorId(encaminhamento.getAtendimentoId());
        return atendimento.getPacienteId();
    }

    /** Usado pelo TriagemIAService para detectar duplicidade: mesmo paciente, mesma especialidade, ainda ativo. */
    public boolean existeOutroEncaminhamentoAtivo(Long pacienteId, String especialidadeOuExame, Long excluirEncaminhamentoId) {
        Set<StatusEncaminhamento> statusInativos = Set.of(StatusEncaminhamento.REALIZADO, StatusEncaminhamento.CANCELADO);
        return listarPorPaciente(pacienteId).stream()
                .filter(e -> !e.getId().equals(excluirEncaminhamentoId))
                .filter(e -> e.getEspecialidadeOuExame().equalsIgnoreCase(especialidadeOuExame))
                .anyMatch(e -> !statusInativos.contains(e.getStatus()));
    }

    public Long profissionalIdDoEncaminhamento(Long encaminhamentoId) {
        Encaminhamento encaminhamento = buscarPorId(encaminhamentoId);
        Atendimento atendimento = atendimentoService.buscarPorId(encaminhamento.getAtendimentoId());
        return atendimento.getProfissionalId();
    }

    public long contarTotal() {
        return encaminhamentoRepository.count();
    }

    public long contarPorStatus(StatusEncaminhamento status) {
        return encaminhamentoRepository.countByStatus(status);
    }

    public List<Encaminhamento> listarPorAtendimentos(List<Long> atendimentoIds) {
        return atendimentoIds.isEmpty() ? List.of() : encaminhamentoRepository.findByAtendimentoIdIn(atendimentoIds);
    }

    public long contarPorTipo(TipoEncaminhamento tipo) {
        return encaminhamentoRepository.countByTipo(tipo);
    }

    public java.util.Map<String, Long> demandaPorEspecialidade() {
        return encaminhamentoRepository.findAll().stream()
                .collect(Collectors.groupingBy(Encaminhamento::getEspecialidadeOuExame, Collectors.counting()));
    }
}
