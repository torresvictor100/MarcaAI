package com.marcaai.relatoriofila;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marcaai.atendimento.Atendimento;
import com.marcaai.atendimento.AtendimentoService;
import com.marcaai.atendimento.ClassificacaoRisco;
import com.marcaai.auth.Papel;
import com.marcaai.config.MarcaAiPrincipal;
import com.marcaai.config.RegraDeNegocioException;
import com.marcaai.encaminhamento.Encaminhamento;
import com.marcaai.encaminhamento.EncaminhamentoService;
import com.marcaai.encaminhamento.StatusEncaminhamento;
import com.marcaai.fila.FilaDetalhesService;
import com.marcaai.fila.FilaItemResponse;
import com.marcaai.fila.FilaService;
import com.marcaai.vagasagendamento.Agendamento;
import com.marcaai.vagasagendamento.AgendamentoService;
import com.marcaai.vagasagendamento.VagaHorario;
import com.marcaai.config.RecursoNaoEncontradoException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RelatorioFilaServiceTest {

    private FilaService filaService;
    private FilaDetalhesService filaDetalhesService;
    private EncaminhamentoService encaminhamentoService;
    private AtendimentoService atendimentoService;
    private AgendamentoService agendamentoService;
    private AnthropicRelatorioRedator redatorIa;
    private RelatorioFilaRepository repository;
    private RelatorioFilaService service;

    @BeforeEach
    void setUp() {
        filaService = mock(FilaService.class);
        filaDetalhesService = mock(FilaDetalhesService.class);
        encaminhamentoService = mock(EncaminhamentoService.class);
        atendimentoService = mock(AtendimentoService.class);
        agendamentoService = mock(AgendamentoService.class);
        redatorIa = mock(AnthropicRelatorioRedator.class);
        repository = mock(RelatorioFilaRepository.class);
        service = new RelatorioFilaService(filaService, filaDetalhesService, encaminhamentoService, atendimentoService,
                agendamentoService, new MotorSugestoesFila(7, 30), redatorIa, new TemplateRelatorioRedator(), repository,
                new ObjectMapper().findAndRegisterModules());

        when(encaminhamentoService.listarEspecialidades()).thenReturn(List.of("Cardiologia", "Ortopedia"));
        when(repository.save(any())).thenAnswer(inv -> {
            RelatorioFila r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });
        // Fila: #10 verde na frente, #20 vermelho atrás (deve subir), #30 agendado.
        List<FilaItemResponse> fila = List.of(
                new FilaItemResponse(1L, 10L, "Cardiologia", 1, 90, false, null, "Ana", StatusEncaminhamento.NA_FILA, ClassificacaoRisco.VERDE, null),
                new FilaItemResponse(2L, 20L, "Cardiologia", 2, 80, false, null, "Bruno", StatusEncaminhamento.NA_FILA, ClassificacaoRisco.VERMELHO, null),
                new FilaItemResponse(3L, 30L, "Cardiologia", 3, 70, false, null, "Carla", StatusEncaminhamento.AGENDADO, ClassificacaoRisco.VERDE, null));
        when(filaService.listarPorEspecialidade("Cardiologia")).thenReturn(fila);
        when(filaDetalhesService.detalhar(fila)).thenReturn(fila);
        encaminhamento(10L, StatusEncaminhamento.NA_FILA, ClassificacaoRisco.VERDE);
        encaminhamento(20L, StatusEncaminhamento.NA_FILA, ClassificacaoRisco.VERMELHO);
        encaminhamento(30L, StatusEncaminhamento.AGENDADO, ClassificacaoRisco.VERDE);
        when(agendamentoService.buscarPorEncaminhamento(30L))
                .thenReturn(Agendamento.builder().id(9L).dataHora(LocalDateTime.now().plusDays(3)).build());
        when(agendamentoService.listarDisponiveisPorEspecialidade("Cardiologia")).thenReturn(List.of(
                VagaHorario.builder().id(5L).dataHora(LocalDateTime.now().plusDays(1)).build(),
                VagaHorario.builder().id(6L).dataHora(LocalDateTime.now().plusDays(2)).build()));

        MarcaAiPrincipal secretaria = new MarcaAiPrincipal(5L, "secretaria", Papel.SECRETARIA);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(secretaria, null, List.of()));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private void encaminhamento(long id, StatusEncaminhamento status, ClassificacaoRisco risco) {
        when(encaminhamentoService.buscarPorId(id)).thenReturn(
                Encaminhamento.builder().id(id).atendimentoId(id + 100).status(status).build());
        when(atendimentoService.buscarPorId(id + 100)).thenReturn(
                Atendimento.builder().id(id + 100).classificacaoRisco(risco).data(LocalDateTime.now().minusDays(2)).build());
    }

    @Test
    void geraComTextoDaIaEGuardaAsSugestoesDoMotor() {
        when(redatorIa.redigir(any())).thenReturn("Texto da IA");

        RelatorioFilaResponse relatorio = service.gerar("cardiologia");

        assertThat(relatorio.especialidadeOuExame()).isEqualTo("Cardiologia");
        assertThat(relatorio.origemTexto()).isEqualTo(OrigemTexto.IA);
        assertThat(relatorio.texto()).isEqualTo("Texto da IA");
        assertThat(relatorio.totalNaFila()).isEqualTo(3);
        assertThat(relatorio.geradoPor()).isEqualTo("secretaria");
        assertThat(relatorio.sugestoes()).extracting(SugestaoFila::tipo, SugestaoFila::encaminhamentoId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(TipoSugestao.SUBIR_NA_FILA, 20L));
        verify(repository).save(argThat(r -> r.getSugestoesJson().contains("SUBIR_NA_FILA")));
    }

    @Test
    void semIaCaiNoTextoPadrao() {
        when(redatorIa.redigir(any())).thenThrow(new RedacaoIndisponivelException("sem chave"));

        RelatorioFilaResponse relatorio = service.gerar("Cardiologia");

        assertThat(relatorio.origemTexto()).isEqualTo(OrigemTexto.MODELO);
        assertThat(relatorio.texto()).contains("Fila de Cardiologia: 3 paciente(s) — 2 aguardando agendamento, 1 agendado(s)");
    }

    @Test
    void recusaEspecialidadeForaDoCatalogo() {
        assertThatThrownBy(() -> service.gerar("Astrologia")).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> service.buscarUltimo(null)).isInstanceOf(RegraDeNegocioException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void buscaOUltimoOu404() {
        when(repository.findFirstByEspecialidadeOuExameIgnoreCaseOrderByGeradoEmDescIdDesc("Ortopedia")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.buscarUltimo("Ortopedia")).isInstanceOf(RecursoNaoEncontradoException.class);

        when(repository.findFirstByEspecialidadeOuExameIgnoreCaseOrderByGeradoEmDescIdDesc("Cardiologia")).thenReturn(Optional.of(
                RelatorioFila.builder().id(3L).especialidadeOuExame("Cardiologia").geradoEm(LocalDateTime.now()).geradoPor("admin")
                        .totalNaFila(0).sugestoesJson("[]").texto("t").origemTexto(OrigemTexto.MODELO).build()));
        assertThat(service.buscarUltimo("Cardiologia").id()).isEqualTo(3L);
    }

    @Test
    void agendadoSemAgendamentoEmVigorNaoDerrubaORelatorio() {
        when(agendamentoService.buscarPorEncaminhamento(30L)).thenThrow(new RecursoNaoEncontradoException("sem agendamento"));
        when(redatorIa.redigir(any())).thenReturn("Texto da IA");

        RelatorioFilaResponse relatorio = service.gerar("Cardiologia");

        assertThat(relatorio.totalNaFila()).isEqualTo(3);
        assertThat(relatorio.sugestoes()).extracting(SugestaoFila::tipo).doesNotContain(TipoSugestao.ANTECIPAR);
    }

    @Test
    void relatorioGravadoComSugestoesIlegiveisDaErroClaro() {
        when(repository.findFirstByEspecialidadeOuExameIgnoreCaseOrderByGeradoEmDescIdDesc("Cardiologia")).thenReturn(Optional.of(
                RelatorioFila.builder().id(4L).especialidadeOuExame("Cardiologia").geradoEm(LocalDateTime.now()).geradoPor("admin")
                        .totalNaFila(0).sugestoesJson("{não é json").texto("t").origemTexto(OrigemTexto.MODELO).build()));

        assertThatThrownBy(() -> service.buscarUltimo("Cardiologia"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Relatório 4");
    }
}
