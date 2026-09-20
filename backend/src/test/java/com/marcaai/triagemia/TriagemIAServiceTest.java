package com.marcaai.triagemia;

import com.marcaai.atendimento.Atendimento;
import com.marcaai.atendimento.AtendimentoService;
import com.marcaai.atendimento.ClassificacaoRisco;
import com.marcaai.encaminhamento.*;
import com.marcaai.fila.FilaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TriagemIAServiceTest {

    private EncaminhamentoService encaminhamentoService;
    private AtendimentoService atendimentoService;
    private FilaService filaService;
    private AnaliseIARepository analiseIARepository;
    private JustificativaGenerator justificativaGenerator;
    private TriagemIAService triagemIAService;

    @BeforeEach
    void setUp() {
        encaminhamentoService = mock(EncaminhamentoService.class);
        atendimentoService = mock(AtendimentoService.class);
        filaService = mock(FilaService.class);
        analiseIARepository = mock(AnaliseIARepository.class);
        justificativaGenerator = mock(JustificativaGenerator.class);
        triagemIAService = new TriagemIAService(
                encaminhamentoService, atendimentoService, filaService, analiseIARepository, justificativaGenerator);

        when(analiseIARepository.findByEncaminhamentoId(anyLong())).thenReturn(java.util.Optional.empty());
        when(analiseIARepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(justificativaGenerator.gerar(any())).thenReturn("justificativa de teste");
    }

    private Cid cidCardiologia() {
        return Cid.builder().id(1L).codigo("I20").descricao("Angina").especialidadesCompativeis(List.of("Cardiologia")).build();
    }

    private Atendimento atendimento(ClassificacaoRisco risco, LocalDateTime data) {
        return Atendimento.builder()
                .id(10L).pacienteId(100L).profissionalId(200L).unidadeId(1L)
                .data(data).classificacaoRisco(risco).build();
    }

    private Encaminhamento encaminhamento(String especialidade, Long cidId, boolean urgente, String justificativa) {
        return Encaminhamento.builder()
                .id(1L).atendimentoId(10L).tipo(TipoEncaminhamento.CONSULTA_ESPECIALISTA)
                .especialidadeOuExame(especialidade).cidId(cidId)
                .status(StatusEncaminhamento.EM_ANALISE).urgente(urgente).justificativaUrgencia(justificativa)
                .build();
    }

    @Test
    void casoNormalNaoEUrgenteEEntraNaFilaComScorePositivo() {
        Encaminhamento encaminhamento = encaminhamento("Cardiologia", 1L, false, null);
        when(encaminhamentoService.buscarPorId(1L)).thenReturn(encaminhamento);
        when(atendimentoService.buscarPorId(10L)).thenReturn(atendimento(ClassificacaoRisco.AMARELO, LocalDateTime.now()));
        when(encaminhamentoService.buscarCid(1L)).thenReturn(cidCardiologia());
        when(encaminhamentoService.listarDocumentos(1L)).thenReturn(List.of());
        when(encaminhamentoService.existeOutroEncaminhamentoAtivo(anyLong(), anyString(), anyLong())).thenReturn(false);
        when(encaminhamentoService.profissionalIdDoEncaminhamento(1L)).thenReturn(200L);

        AnaliseIA resultado = triagemIAService.analisar(1L);

        assertThat(resultado.isBloqueado()).isFalse();
        assertThat(resultado.getScorePrioridade()).isEqualTo(50.0); // peso AMARELO, sem urgência, sem espera
        verify(encaminhamentoService).atualizarStatus(1L, StatusEncaminhamento.NA_FILA);
        verify(filaService).adicionarNaFila(1L, "Cardiologia", 50.0, 200L);
    }

    @Test
    void casoUrgenteComRiscoAltoTemScoreMaiorQueCasoNormal() {
        Encaminhamento normal = encaminhamento("Cardiologia", 1L, false, null);
        Encaminhamento urgente = encaminhamento("Cardiologia", 1L, true, "Piora súbita do quadro clínico");
        urgente.setId(2L);

        when(encaminhamentoService.buscarPorId(1L)).thenReturn(normal);
        when(encaminhamentoService.buscarPorId(2L)).thenReturn(urgente);
        when(atendimentoService.buscarPorId(10L)).thenReturn(atendimento(ClassificacaoRisco.AMARELO, LocalDateTime.now()));
        when(encaminhamentoService.buscarCid(1L)).thenReturn(cidCardiologia());
        when(encaminhamentoService.listarDocumentos(anyLong())).thenReturn(List.of());
        when(encaminhamentoService.existeOutroEncaminhamentoAtivo(anyLong(), anyString(), anyLong())).thenReturn(false);
        when(encaminhamentoService.profissionalIdDoEncaminhamento(anyLong())).thenReturn(200L);

        double scoreNormal = triagemIAService.analisar(1L).getScorePrioridade();
        double scoreUrgente = triagemIAService.analisar(2L).getScorePrioridade();

        assertThat(scoreUrgente).isGreaterThan(scoreNormal);
    }

    @Test
    void cidIncompativelBloqueiaEEncaminhamentoNaoEntraNaFila() {
        Encaminhamento encaminhamento = encaminhamento("Ortopedia", 1L, false, null);
        when(encaminhamentoService.buscarPorId(1L)).thenReturn(encaminhamento);
        when(atendimentoService.buscarPorId(10L)).thenReturn(atendimento(ClassificacaoRisco.VERDE, LocalDateTime.now()));
        when(encaminhamentoService.buscarCid(1L)).thenReturn(cidCardiologia()); // só compatível com Cardiologia
        when(encaminhamentoService.listarDocumentos(1L)).thenReturn(List.of());
        when(encaminhamentoService.existeOutroEncaminhamentoAtivo(anyLong(), anyString(), anyLong())).thenReturn(false);

        AnaliseIA resultado = triagemIAService.analisar(1L);

        assertThat(resultado.isBloqueado()).isTrue();
        assertThat(resultado.getScorePrioridade()).isZero();
        verify(encaminhamentoService).atualizarStatus(1L, StatusEncaminhamento.BLOQUEADO_REVISAO);
        verify(filaService, never()).adicionarNaFila(anyLong(), anyString(), anyDouble(), anyLong());
    }

    @Test
    void documentoVencidoBloqueiaOEncaminhamento() {
        Encaminhamento encaminhamento = encaminhamento("Cardiologia", 1L, false, null);
        Documento vencido = Documento.builder()
                .id(1L).encaminhamentoId(1L).tipo("LAUDO").referenciaArquivo("laudo.pdf")
                .dataEmissao(LocalDate.now().minusYears(1)).validade(LocalDate.now().minusDays(1))
                .build();

        when(encaminhamentoService.buscarPorId(1L)).thenReturn(encaminhamento);
        when(atendimentoService.buscarPorId(10L)).thenReturn(atendimento(ClassificacaoRisco.VERDE, LocalDateTime.now()));
        when(encaminhamentoService.buscarCid(1L)).thenReturn(cidCardiologia());
        when(encaminhamentoService.listarDocumentos(1L)).thenReturn(List.of(vencido));
        when(encaminhamentoService.existeOutroEncaminhamentoAtivo(anyLong(), anyString(), anyLong())).thenReturn(false);

        AnaliseIA resultado = triagemIAService.analisar(1L);

        assertThat(resultado.isBloqueado()).isTrue();
        verify(encaminhamentoService).atualizarStatus(1L, StatusEncaminhamento.BLOQUEADO_REVISAO);
    }

    @Test
    void justificativaGeradaRecebeContextoComScoreEIrregularidadesCorretos() {
        Encaminhamento encaminhamento = encaminhamento("Cardiologia", 1L, false, null);
        when(encaminhamentoService.buscarPorId(1L)).thenReturn(encaminhamento);
        when(atendimentoService.buscarPorId(10L)).thenReturn(atendimento(ClassificacaoRisco.VERMELHO, LocalDateTime.now()));
        when(encaminhamentoService.buscarCid(1L)).thenReturn(cidCardiologia());
        when(encaminhamentoService.listarDocumentos(1L)).thenReturn(List.of());
        when(encaminhamentoService.existeOutroEncaminhamentoAtivo(anyLong(), anyString(), anyLong())).thenReturn(false);
        when(encaminhamentoService.profissionalIdDoEncaminhamento(1L)).thenReturn(200L);

        triagemIAService.analisar(1L);

        ArgumentCaptor<ContextoJustificativa> captor = ArgumentCaptor.forClass(ContextoJustificativa.class);
        verify(justificativaGenerator).gerar(captor.capture());
        assertThat(captor.getValue().score()).isEqualTo(100.0); // peso VERMELHO
        assertThat(captor.getValue().bloqueado()).isFalse();
        assertThat(captor.getValue().irregularidades()).isEmpty();
    }

    @Test
    void documentoFaltandoViraAlertaSemBloquearNemMudarOScore() {
        Encaminhamento encaminhamento = encaminhamento("Cardiologia", 1L, false, null);
        when(encaminhamentoService.buscarPorId(1L)).thenReturn(encaminhamento);
        when(atendimentoService.buscarPorId(10L)).thenReturn(atendimento(ClassificacaoRisco.VERMELHO, LocalDateTime.now()));
        when(encaminhamentoService.buscarCid(1L)).thenReturn(cidCardiologia());
        when(encaminhamentoService.listarDocumentos(1L)).thenReturn(List.of());
        when(encaminhamentoService.tiposDocumentoFaltantes(eq(encaminhamento), anyList()))
                .thenReturn(List.of("EXAME_ANTERIOR", "LAUDO"));
        when(encaminhamentoService.existeOutroEncaminhamentoAtivo(anyLong(), anyString(), anyLong())).thenReturn(false);
        when(encaminhamentoService.profissionalIdDoEncaminhamento(1L)).thenReturn(200L);

        AnaliseIA resultado = triagemIAService.analisar(1L);

        assertThat(resultado.isBloqueado()).isFalse();
        assertThat(resultado.getScorePrioridade()).isEqualTo(100.0);
        assertThat(resultado.getIrregularidades())
                .contains("Faltam documentos exigidos para Cardiologia: exame anterior, laudo médico")
                .contains("ALERTA");
        verify(encaminhamentoService).atualizarStatus(1L, StatusEncaminhamento.NA_FILA);
        verify(filaService).adicionarNaFila(1L, "Cardiologia", 100.0, 200L);
    }

    @Test
    void buscarAnaliseDeEncaminhamentoAindaNaoTriadoDa404() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> triagemIAService.buscarPorEncaminhamento(77L))
                .isInstanceOf(com.marcaai.config.RecursoNaoEncontradoException.class)
                .hasMessageContaining("77");
    }
}
