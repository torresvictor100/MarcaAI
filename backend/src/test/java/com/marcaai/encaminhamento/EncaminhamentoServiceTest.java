package com.marcaai.encaminhamento;

import com.marcaai.atendimento.Atendimento;
import com.marcaai.atendimento.AtendimentoService;
import com.marcaai.atendimento.ClassificacaoRisco;
import com.marcaai.config.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EncaminhamentoServiceTest {

    private EncaminhamentoRepository encaminhamentoRepository;
    private DocumentoRepository documentoRepository;
    private CidRepository cidRepository;
    private TipoDocumentoExigidoRepository tipoDocumentoExigidoRepository;
    private AtendimentoService atendimentoService;
    private ApplicationEventPublisher eventPublisher;
    private EncaminhamentoService service;

    @BeforeEach
    void setUp() {
        encaminhamentoRepository = mock(EncaminhamentoRepository.class);
        documentoRepository = mock(DocumentoRepository.class);
        cidRepository = mock(CidRepository.class);
        tipoDocumentoExigidoRepository = mock(TipoDocumentoExigidoRepository.class);
        atendimentoService = mock(AtendimentoService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new EncaminhamentoService(
                encaminhamentoRepository, documentoRepository, cidRepository,
                tipoDocumentoExigidoRepository, atendimentoService, eventPublisher);

        when(encaminhamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(atendimentoService.buscarPorId(10L)).thenReturn(
                Atendimento.builder().id(10L).pacienteId(100L).profissionalId(1L).unidadeId(1L)
                        .data(LocalDateTime.now()).classificacaoRisco(ClassificacaoRisco.VERDE).build());
    }

    private static final List<DocumentoRequest> DOCS = List.of(new DocumentoRequest("LAUDO", "a.pdf", LocalDate.now(), null));

    private Cid cid() {
        return Cid.builder().id(1L).codigo("I20").descricao("Angina").especialidadesCompativeis(List.of("Cardiologia")).build();
    }

    @Test
    void criarComCidExistenteVaiDiretoParaATriagem() {
        when(cidRepository.findById(1L)).thenReturn(Optional.of(cid()));

        Encaminhamento resultado = service.criar(new EncaminhamentoRequest(10L, TipoEncaminhamento.CONSULTA_ESPECIALISTA, "Cardiologia", 1L, false, null, DOCS));

        assertThat(resultado.getStatus()).isEqualTo(StatusEncaminhamento.EM_ANALISE);
        assertThat(resultado.getCidId()).isEqualTo(1L);
    }

    @Test
    void criarComCidInexistenteLancaRegraDeNegocio() {
        when(cidRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(new EncaminhamentoRequest(10L, TipoEncaminhamento.CONSULTA_ESPECIALISTA, "Cardiologia", 99L, false, null, DOCS)))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void criarUrgenteSemJustificativaLancaRegraDeNegocio() {
        when(cidRepository.findById(1L)).thenReturn(Optional.of(cid()));

        assertThatThrownBy(() -> service.criar(new EncaminhamentoRequest(10L, TipoEncaminhamento.CONSULTA_ESPECIALISTA, "Cardiologia", 1L, true, "   ", DOCS)))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void criarUrgenteComJustificativaFunciona() {
        when(cidRepository.findById(1L)).thenReturn(Optional.of(cid()));

        Encaminhamento resultado = service.criar(new EncaminhamentoRequest(10L, TipoEncaminhamento.CONSULTA_ESPECIALISTA, "Cardiologia", 1L, true, "Piora súbita", DOCS));

        assertThat(resultado.isUrgente()).isTrue();
        assertThat(resultado.getJustificativaUrgencia()).isEqualTo("Piora súbita");
    }

    @Test
    void criarSemDocumentoLancaRegraDeNegocio() {
        when(cidRepository.findById(1L)).thenReturn(Optional.of(cid()));

        assertThatThrownBy(() -> service.criar(new EncaminhamentoRequest(10L, TipoEncaminhamento.CONSULTA_ESPECIALISTA, "Cardiologia", 1L, false, null, List.of())))
                .isInstanceOf(RegraDeNegocioException.class);
        verify(encaminhamentoRepository, never()).save(any());
    }

    @Test
    void criarSalvaTodosOsDocumentosEDisparaATriagemNaHoraMesmoIncompleto() {
        when(cidRepository.findById(1L)).thenReturn(Optional.of(cid()));
        when(encaminhamentoRepository.save(any())).thenAnswer(inv -> {
            Encaminhamento e = inv.getArgument(0);
            if (e.getId() == null) e.setId(7L);
            return e;
        });
        when(documentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        List<DocumentoRequest> documentos = List.of(
                new DocumentoRequest("LAUDO", "laudo.pdf", LocalDate.now(), null),
                new DocumentoRequest("GUIA_ENCAMINHAMENTO", "guia.pdf", LocalDate.now(), null));

        Encaminhamento resultado = service.criar(
                new EncaminhamentoRequest(10L, TipoEncaminhamento.CONSULTA_ESPECIALISTA, "Cardiologia", 1L, false, null, documentos));

        verify(documentoRepository, times(2)).save(argThat(d -> d.getEncaminhamentoId().equals(7L)));
        assertThat(resultado.getStatus()).isEqualTo(StatusEncaminhamento.EM_ANALISE);
        verify(eventPublisher).publishEvent(new TriagemSolicitadaEvent(7L));
        verifyNoInteractions(tipoDocumentoExigidoRepository); // a falta de documento é checada na triagem, não aqui
    }

    private Encaminhamento encaminhamentoCom(StatusEncaminhamento status) {
        Encaminhamento encaminhamento = Encaminhamento.builder()
                .id(1L).atendimentoId(10L).tipo(TipoEncaminhamento.CONSULTA_ESPECIALISTA)
                .especialidadeOuExame("Cardiologia").cidId(1L).status(status).build();
        when(encaminhamentoRepository.findById(1L)).thenReturn(Optional.of(encaminhamento));
        when(documentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return encaminhamento;
    }

    @Test
    void adicionarDocumentoAntesDoAgendamentoRefazATriagem() {
        for (StatusEncaminhamento status : List.of(StatusEncaminhamento.AGUARDANDO_DOCUMENTOS, StatusEncaminhamento.NA_FILA,
                StatusEncaminhamento.BLOQUEADO_REVISAO)) {
            Encaminhamento encaminhamento = encaminhamentoCom(status);

            service.adicionarDocumento(1L, new DocumentoRequest("LAUDO", "a.pdf", LocalDate.now(), null));

            assertThat(encaminhamento.getStatus()).as("vindo de " + status).isEqualTo(StatusEncaminhamento.EM_ANALISE);
        }
        verify(eventPublisher, times(3)).publishEvent(new TriagemSolicitadaEvent(1L));
    }

    @Test
    void adicionarDocumentoDepoisDoAgendamentoSoGuardaODocumento() {
        for (StatusEncaminhamento status : List.of(StatusEncaminhamento.AGENDADO, StatusEncaminhamento.REALIZADO,
                StatusEncaminhamento.CANCELADO)) {
            Encaminhamento encaminhamento = encaminhamentoCom(status);

            service.adicionarDocumento(1L, new DocumentoRequest("LAUDO", "a.pdf", LocalDate.now(), null));

            assertThat(encaminhamento.getStatus()).isEqualTo(status);
        }
        verify(documentoRepository, times(3)).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void tiposFaltantesComparaOsExigidosComOsAnexadosSemDiferenciarCaixa() {
        when(tipoDocumentoExigidoRepository.findByEspecialidadeOuExameIgnoreCase("Cardiologia")).thenReturn(List.of(
                TipoDocumentoExigido.builder().especialidadeOuExame("Cardiologia").tipoDocumento("GUIA_ENCAMINHAMENTO").build(),
                TipoDocumentoExigido.builder().especialidadeOuExame("Cardiologia").tipoDocumento("EXAME_ANTERIOR").build(),
                TipoDocumentoExigido.builder().especialidadeOuExame("Cardiologia").tipoDocumento("laudo").build()));
        Encaminhamento encaminhamento = Encaminhamento.builder().id(1L).especialidadeOuExame("Cardiologia").build();
        List<Documento> anexados = List.of(
                Documento.builder().tipo("guia_encaminhamento").build(),
                Documento.builder().tipo("GUIA_ENCAMINHAMENTO").build());

        assertThat(service.listarDocumentosExigidos(" Cardiologia ")).containsExactly("GUIA_ENCAMINHAMENTO", "EXAME_ANTERIOR", "LAUDO");
        assertThat(service.tiposDocumentoFaltantes(encaminhamento, anexados)).containsExactly("EXAME_ANTERIOR", "LAUDO");
        assertThat(service.tiposDocumentoFaltantes(encaminhamento, List.of(
                Documento.builder().tipo("GUIA_ENCAMINHAMENTO").build(),
                Documento.builder().tipo("EXAME_ANTERIOR").build(),
                Documento.builder().tipo("LAUDO").build()))).isEmpty();
    }

    @Test
    void listarPorPacienteRetornaVazioQuandoPacienteNaoTemAtendimentos() {
        when(atendimentoService.listarPorPaciente(999L)).thenReturn(List.of());

        assertThat(service.listarPorPaciente(999L)).isEmpty();
    }

    @Test
    void existeOutroEncaminhamentoAtivoIgnoraOMesmoEncaminhamentoEStatusInativos() {
        when(atendimentoService.listarPorPaciente(100L)).thenReturn(List.of(
                Atendimento.builder().id(10L).pacienteId(100L).profissionalId(1L).unidadeId(1L)
                        .data(LocalDateTime.now()).classificacaoRisco(ClassificacaoRisco.VERDE).build()));
        when(encaminhamentoRepository.findByAtendimentoIdIn(List.of(10L))).thenReturn(List.of(
                Encaminhamento.builder().id(5L).atendimentoId(10L).especialidadeOuExame("Cardiologia")
                        .status(StatusEncaminhamento.REALIZADO).build(),
                Encaminhamento.builder().id(6L).atendimentoId(10L).especialidadeOuExame("Cardiologia")
                        .status(StatusEncaminhamento.NA_FILA).build()));

        boolean duplicadoIgnorandoEleMesmo = service.existeOutroEncaminhamentoAtivo(100L, "Cardiologia", 6L);
        boolean semAtivoQueNaoSejaEleMesmo = service.existeOutroEncaminhamentoAtivo(100L, "Cardiologia", 6L);

        assertThat(duplicadoIgnorandoEleMesmo).isFalse(); // o único ativo (6) é o próprio, então não conta
        assertThat(semAtivoQueNaoSejaEleMesmo).isFalse();

        boolean duplicadoDetectado = service.existeOutroEncaminhamentoAtivo(100L, "Cardiologia", 999L);
        assertThat(duplicadoDetectado).isTrue(); // 6 está ativo e não é o excluído
    }

    @Test
    void criarUrgenteComJustificativaNulaOuSemListaDeDocumentosNaoSalvaNada() {
        when(cidRepository.findById(1L)).thenReturn(Optional.of(cid()));

        assertThatThrownBy(() -> service.criar(new EncaminhamentoRequest(10L, TipoEncaminhamento.CONSULTA_ESPECIALISTA, "Cardiologia", 1L, true, null, DOCS)))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> service.criar(new EncaminhamentoRequest(10L, TipoEncaminhamento.CONSULTA_ESPECIALISTA, "Cardiologia", 1L, false, null, null)))
                .isInstanceOf(RegraDeNegocioException.class);
        verify(encaminhamentoRepository, never()).save(any());
    }

    @Test
    void buscarEncaminhamentoOuCidInexistenteDa404() {
        when(encaminhamentoRepository.findById(404L)).thenReturn(Optional.empty());
        when(cidRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(404L)).isInstanceOf(com.marcaai.config.RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.buscarCid(404L)).isInstanceOf(com.marcaai.config.RecursoNaoEncontradoException.class);
    }

    @Test
    void pacienteDoEncaminhamentoVemDoAtendimento() {
        when(encaminhamentoRepository.findById(50L)).thenReturn(Optional.of(Encaminhamento.builder().id(50L).atendimentoId(10L).build()));

        assertThat(service.pacienteIdDoEncaminhamento(50L)).isEqualTo(100L);
        assertThat(service.profissionalIdDoEncaminhamento(50L)).isEqualTo(1L);
    }

    @Test
    void listarPorAtendimentosSemIdsNaoConsultaOBanco() {
        assertThat(service.listarPorAtendimentos(List.of())).isEmpty();
        verify(encaminhamentoRepository, never()).findByAtendimentoIdIn(any());

        when(encaminhamentoRepository.findByAtendimentoIdIn(List.of(10L))).thenReturn(List.of(Encaminhamento.builder().id(50L).build()));
        assertThat(service.listarPorAtendimentos(List.of(10L))).hasSize(1);
    }
}
